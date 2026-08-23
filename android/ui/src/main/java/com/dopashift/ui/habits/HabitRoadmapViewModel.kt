package com.dopashift.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dopashift.domain.entity.CheckpointStatus
import com.dopashift.domain.entity.GoalProfile
import com.dopashift.domain.entity.HabitCheckpoint
import com.dopashift.domain.entity.HabitTrack
import com.dopashift.domain.repository.GoalRepository
import com.dopashift.domain.repository.HabitTrackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject

/**
 * A single habit track combined with its associated goal name for display.
 */
data class HabitTrackWithGoal(
    val track: HabitTrack,
    val goalName: String
)

/**
 * UI state for the Habit Roadmap screen.
 *
 * Displays all active habit tracks with 30-day visualization,
 * current day indicator, and checkpoint completion controls.
 *
 * Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6
 */
data class HabitRoadmapUiState(
    val habitTracksWithGoals: List<HabitTrackWithGoal> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val isLoading: Boolean = true,
    val isEmpty: Boolean = false
) {
    /**
     * Req 6.5: The track with the earliest start date is shown prominently.
     * Returns null if no tracks are active.
     */
    val prominentTrack: HabitTrackWithGoal?
        get() = habitTracksWithGoals.minByOrNull { it.track.startDate }
}

/**
 * ViewModel for the Habit Roadmap screen.
 *
 * Combines reactive Flows from HabitTrackRepository and GoalRepository
 * to produce a unified UI state. Room's invalidation tracker ensures
 * sub-second propagation (Requirement 14.4).
 *
 * Responsibilities:
 * - Display all active habit tracks with 30-day status grid (Req 6.1)
 * - Show earliest-start-date track prominently (Req 6.5)
 * - Mark current day's checkpoint as complete (Req 6.3)
 * - Visual status for each day: PENDING, COMPLETED, MISSED (Req 6.4)
 * - Current day indicator
 */
@HiltViewModel
class HabitRoadmapViewModel @Inject constructor(
    private val habitTrackRepository: HabitTrackRepository,
    private val goalRepository: GoalRepository
) : ViewModel() {

    // TODO: Replace with actual user ID from auth/session layer
    private val userId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")

    /**
     * Combined UI state from habit tracks and goals.
     *
     * [combine] merges latest emissions from each Flow: whenever habit tracks
     * or goals change in Room, this StateFlow updates and the subscribed
     * Compose UI recomposes.
     */
    val uiState: StateFlow<HabitRoadmapUiState> = combine(
        habitTrackRepository.observeActiveByUserId(userId),
        goalRepository.observeByUserId(userId)
    ) { tracks, goals ->
        buildUiState(tracks, goals)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = HabitRoadmapUiState()
    )

    private fun buildUiState(
        tracks: List<HabitTrack>,
        goals: List<GoalProfile>
    ): HabitRoadmapUiState {
        val goalMap = goals.associateBy { it.id }

        val tracksWithGoals = tracks.map { track ->
            HabitTrackWithGoal(
                track = track,
                goalName = goalMap[track.goalId]?.name ?: "Unknown Goal"
            )
        }.sortedBy { it.track.startDate } // Earliest start date first (Req 6.5)

        return HabitRoadmapUiState(
            habitTracksWithGoals = tracksWithGoals,
            today = LocalDate.now(),
            isLoading = false,
            isEmpty = tracksWithGoals.isEmpty()
        )
    }

    /**
     * Req 6.3: Mark the current day's checkpoint as complete.
     * Records completion with UTC timestamp, marks checkpoint as COMPLETED,
     * and advances the current-day pointer to the next uncompleted day.
     */
    fun completeCheckpoint(trackId: UUID) {
        viewModelScope.launch {
            val track = habitTrackRepository.findById(trackId) ?: return@launch

            val updatedCheckpoints = track.checkpoints.map { checkpoint ->
                if (checkpoint.dayNumber == track.currentDay && checkpoint.status == CheckpointStatus.PENDING) {
                    checkpoint.copy(
                        status = CheckpointStatus.COMPLETED,
                        completedAt = Instant.now()
                    )
                } else {
                    checkpoint
                }
            }

            // Advance current day pointer to next uncompleted day (Req 6.3)
            val nextDay = updatedCheckpoints
                .filter { it.dayNumber > track.currentDay && it.status == CheckpointStatus.PENDING }
                .minByOrNull { it.dayNumber }?.dayNumber
                ?: (track.currentDay + 1).coerceAtMost(30)

            // Req 6.6: Mark as finished when all 30 days are completed or missed
            val isFinished = updatedCheckpoints.all {
                it.status == CheckpointStatus.COMPLETED || it.status == CheckpointStatus.MISSED
            }

            val updatedTrack = track.copy(
                currentDay = if (isFinished) track.currentDay else nextDay,
                isFinished = isFinished,
                checkpoints = updatedCheckpoints
            )
            habitTrackRepository.save(updatedTrack)
        }
    }

    /**
     * Calculate which day number corresponds to today for a given track.
     * Returns a day number (1-30) based on the track's start date and today's date,
     * or null if today falls outside the 30-day window.
     */
    fun getTodayDayNumber(track: HabitTrack): Int? {
        val daysSinceStart = ChronoUnit.DAYS.between(track.startDate, LocalDate.now()).toInt()
        val todayDayNumber = daysSinceStart + 1
        return if (todayDayNumber in 1..30) todayDayNumber else null
    }
}
