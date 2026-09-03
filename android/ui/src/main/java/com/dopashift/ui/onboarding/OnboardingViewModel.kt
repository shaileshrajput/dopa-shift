package com.dopashift.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dopashift.domain.entity.GoalProfile
import com.dopashift.domain.entity.HabitTrack
import com.dopashift.domain.repository.GoalRepository
import com.dopashift.domain.repository.HabitTrackRepository
import com.dopashift.domain.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/**
 * Onboarding step index constants.
 */
object OnboardingSteps {
    const val WELCOME = 0
    const val PROFILE = 1
    const val CREATE_GOAL = 2
    const val SETUP_HABIT = 3
    const val PERMISSIONS = 4
    const val DONE = 5
    const val TOTAL = 6
}

/**
 * UI state for the onboarding flow.
 */
data class OnboardingUiState(
    val currentStep: Int = OnboardingSteps.WELCOME,
    val fullName: String = "",
    val fullNameError: String? = null,
    val goalName: String = "",
    val goalCategory: String = "",
    val goalKeyword: String = "",
    val habitDescription: String = "",
    val isCreatingGoal: Boolean = false,
    val isCreatingHabit: Boolean = false,
    val createdGoalId: UUID? = null,
    val goalError: String? = null,
    val habitError: String? = null,
    val isCompleted: Boolean = false,
)

/**
 * ViewModel for the onboarding flow.
 *
 * Manages:
 * - Step navigation (forward, back, skip)
 * - First goal creation (Requirement 1)
 * - First habit track setup (Requirement 6)
 * - Onboarding completion persistence
 *
 * Implements Requirement 17.4: max 5 screens, goal selection, first habit setup.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val goalRepository: GoalRepository,
    private val habitTrackRepository: HabitTrackRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    // A fixed userId for local-only onboarding (single-user device context).
    // In production, this would come from the auth session.
    private val localUserId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")

    fun onFullNameChanged(name: String) {
        _uiState.update { it.copy(fullName = name, fullNameError = null) }
    }

    fun onGoalNameChanged(name: String) {
        _uiState.update { it.copy(goalName = name, goalError = null) }
    }

    fun onGoalCategoryChanged(category: String) {
        _uiState.update { it.copy(goalCategory = category, goalError = null) }
    }

    fun onGoalKeywordChanged(keyword: String) {
        _uiState.update { it.copy(goalKeyword = keyword, goalError = null) }
    }

    fun onHabitDescriptionChanged(description: String) {
        _uiState.update { it.copy(habitDescription = description, habitError = null) }
    }

    /**
     * Navigates to the next step.
     * On the goal creation step, creates the goal before advancing.
     * On the habit setup step, creates the habit track before advancing.
     */
    fun nextStep() {
        val state = _uiState.value
        when (state.currentStep) {
            OnboardingSteps.PROFILE -> validateProfileAndAdvance()
            OnboardingSteps.CREATE_GOAL -> createGoalAndAdvance()
            OnboardingSteps.SETUP_HABIT -> createHabitAndAdvance()
            OnboardingSteps.DONE -> completeOnboarding()
            else -> _uiState.update { it.copy(currentStep = it.currentStep + 1) }
        }
    }

    /**
     * Navigates to the previous step.
     */
    fun previousStep() {
        _uiState.update {
            if (it.currentStep > 0) it.copy(currentStep = it.currentStep - 1) else it
        }
    }

    /**
     * Skips the entire onboarding flow.
     */
    fun skipOnboarding() {
        completeOnboarding()
    }

    /**
     * Skips the current step (e.g., goal creation or habit setup).
     * Allows users to proceed without filling in optional content.
     */
    fun skipStep() {
        _uiState.update { it.copy(currentStep = it.currentStep + 1) }
    }

    /**
     * Validates the full-name profile step (Requirement 17.4). The name is required, trimmed, and
     * length-bounded before advancing. Persistence happens on completion so a back-navigation edit
     * is not written prematurely.
     */
    private fun validateProfileAndAdvance() {
        val name = _uiState.value.fullName.trim()
        if (name.isEmpty()) {
            _uiState.update { it.copy(fullNameError = "Please enter your full name") }
            return
        }
        if (name.length > 100) {
            _uiState.update { it.copy(fullNameError = "Name must be at most 100 characters") }
            return
        }
        _uiState.update { it.copy(fullName = name, fullNameError = null, currentStep = it.currentStep + 1) }
    }

    private fun createGoalAndAdvance() {
        val state = _uiState.value
        val name = state.goalName.trim()
        val category = state.goalCategory.trim()
        val keyword = state.goalKeyword.trim()

        if (name.isEmpty()) {
            _uiState.update { it.copy(goalError = "Please enter a goal name") }
            return
        }
        if (name.length > 100) {
            _uiState.update { it.copy(goalError = "Goal name must be at most 100 characters") }
            return
        }
        if (category.isEmpty()) {
            _uiState.update { it.copy(goalError = "Please enter a category") }
            return
        }
        if (category.length > 50) {
            _uiState.update { it.copy(goalError = "Category must be at most 50 characters") }
            return
        }
        if (keyword.isEmpty()) {
            _uiState.update { it.copy(goalError = "Please enter at least one keyword") }
            return
        }
        if (keyword.length > 50) {
            _uiState.update { it.copy(goalError = "Keyword must be at most 50 characters") }
            return
        }

        _uiState.update { it.copy(isCreatingGoal = true, goalError = null) }
        viewModelScope.launch {
            try {
                val existing = goalRepository.findByUserIdAndName(localUserId, name)
                if (existing != null) {
                    _uiState.update {
                        it.copy(
                            isCreatingGoal = false,
                            goalError = "A goal with this name already exists"
                        )
                    }
                    return@launch
                }

                val now = Instant.now()
                val goal = GoalProfile(
                    id = UUID.randomUUID(),
                    userId = localUserId,
                    name = name,
                    category = category,
                    keywords = listOf(keyword),
                    createdAt = now,
                    updatedAt = now,
                    isActive = true,
                )
                val saved = goalRepository.save(goal)
                _uiState.update {
                    it.copy(
                        isCreatingGoal = false,
                        createdGoalId = saved.id,
                        currentStep = OnboardingSteps.SETUP_HABIT,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isCreatingGoal = false,
                        goalError = e.message ?: "Failed to create goal"
                    )
                }
            }
        }
    }

    private fun createHabitAndAdvance() {
        val state = _uiState.value
        val description = state.habitDescription.trim()

        if (description.isEmpty()) {
            _uiState.update { it.copy(habitError = "Please enter a habit description") }
            return
        }
        if (description.length > 200) {
            _uiState.update {
                it.copy(habitError = "Habit description must be at most 200 characters")
            }
            return
        }

        val goalId = state.createdGoalId
        if (goalId == null) {
            // If no goal was created (skipped), skip habit creation too
            _uiState.update { it.copy(currentStep = OnboardingSteps.PERMISSIONS) }
            return
        }

        _uiState.update { it.copy(isCreatingHabit = true, habitError = null) }
        viewModelScope.launch {
            try {
                val trackId = UUID.randomUUID()
                val track = HabitTrack(
                    id = trackId,
                    goalId = goalId,
                    userId = localUserId,
                    startDate = LocalDate.now(),
                    currentDay = 1,
                    isFinished = false,
                    checkpoints = (1..30).map { day ->
                        com.dopashift.domain.entity.HabitCheckpoint(
                            id = UUID.randomUUID(),
                            habitTrackId = trackId,
                            dayNumber = day,
                            description = if (day == 1) description else "Day $day: $description",
                            status = com.dopashift.domain.entity.CheckpointStatus.PENDING,
                            completedAt = null,
                        )
                    }
                )
                habitTrackRepository.save(track)
                _uiState.update {
                    it.copy(
                        isCreatingHabit = false,
                        currentStep = OnboardingSteps.PERMISSIONS,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isCreatingHabit = false,
                        habitError = e.message ?: "Failed to create habit track"
                    )
                }
            }
        }
    }

    private fun completeOnboarding() {
        viewModelScope.launch {
            val fullName = _uiState.value.fullName.trim()
            if (fullName.isNotEmpty()) {
                userPreferencesRepository.setFullName(fullName)
                // Seed the editable display name from the full name if the user has none yet, so the
                // Settings profile is populated on first launch.
                if (userPreferencesRepository.observeDisplayName().first().isBlank()) {
                    userPreferencesRepository.setDisplayName(fullName)
                }
            }
            userPreferencesRepository.setOnboardingCompleted(true)
            _uiState.update { it.copy(isCompleted = true) }
        }
    }
}
