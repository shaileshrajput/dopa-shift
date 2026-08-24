package com.dopashift.ui.navigation

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dopashift.domain.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Represents the app's initial navigation state.
 */
sealed interface AppStartState {
    /** DataStore hasn't emitted yet. */
    data object Loading : AppStartState

    /** Ready — use [startDestination] for the NavHost. */
    data class Ready(val startDestination: String) : AppStartState
}

/**
 * ViewModel for [DopaShiftApp] that determines the start destination
 * based on whether onboarding has been completed, and exposes the
 * user's accent color for theming.
 *
 * Requirement 17.4: First launch shows guided onboarding — never a blank dashboard.
 * Requirement 19.15: User-selected accent color applied across the app.
 */
@HiltViewModel
class DopaShiftAppViewModel @Inject constructor(
    userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val appState: StateFlow<AppStartState> = userPreferencesRepository
        .observeOnboardingCompleted()
        .map { completed ->
            if (completed) {
                AppStartState.Ready(startDestination = DopaShiftRoutes.DASHBOARD)
            } else {
                AppStartState.Ready(startDestination = DopaShiftRoutes.ONBOARDING)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppStartState.Loading
        )

    /**
     * User's selected accent color as a Compose [Color], or null for default theme.
     * Reactively updates when the user changes their preference in Settings.
     */
    val userAccentColor: StateFlow<Color?> = userPreferencesRepository
        .observeAccentColor()
        .map { hex -> hex?.let { parseHexColor(it) } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    private fun parseHexColor(hex: String): Color? {
        return try {
            val cleaned = hex.removePrefix("#")
            val argb = cleaned.toLong(16) or 0xFF000000
            Color(argb.toInt())
        } catch (_: Exception) {
            null
        }
    }
}
