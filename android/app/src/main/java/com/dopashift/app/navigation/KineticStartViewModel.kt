package com.dopashift.app.navigation

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
 * The Kinetic app's initial navigation state, derived from whether onboarding has completed.
 */
sealed interface KineticStartState {
    /** DataStore hasn't emitted yet — hold the splash / show nothing to avoid a route flicker. */
    data object Loading : KineticStartState

    /** Ready — [startDestination] is the route [KineticNavHost] should start on. */
    data class Ready(val startDestination: String) : KineticStartState
}

/**
 * Determines the start destination for [KineticNavHost] based on the persisted onboarding flag
 * (Requirement 17.4: first launch shows guided onboarding, never a blank dashboard).
 *
 * First launch (flag unset/false) → [KineticRoutes.ONBOARDING]; thereafter → [KineticRoutes.HOME].
 */
@HiltViewModel
class KineticStartViewModel @Inject constructor(
    userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val startState: StateFlow<KineticStartState> = userPreferencesRepository
        .observeOnboardingCompleted()
        .map { completed ->
            KineticStartState.Ready(
                startDestination = if (completed) KineticRoutes.HOME else KineticRoutes.ONBOARDING,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = KineticStartState.Loading,
        )
}
