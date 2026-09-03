package com.dopashift.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dopashift.domain.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Immutable snapshot of the theme inputs the UI reacts to.
 *
 * @property themeMode DARK (default) | LIGHT | SYSTEM (DUX-4.5).
 * @property accentSeed accent seed as an ARGB color long; [DEFAULT_ACCENT_SEED] for the default (DUX-4.6).
 */
data class ThemeSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accentSeed: Long = DEFAULT_ACCENT_SEED,
)

/**
 * Bridges the existing `data` DataStore preferences (`theme_mode`, `accent_seed`) into a
 * [StateFlow] the composition observes, so changing either preference recolors the visible screen
 * live — without an app restart and without losing screen state (DUX-4.7).
 *
 * The theme mode and accent seed are subscribed to (not owned) here; persistence stays in the
 * `data` module's [UserPreferencesRepository]. `theme_mode` defaults to DARK (DUX-4.5); a missing
 * `accent_seed` maps to [DEFAULT_ACCENT_SEED] so the design-system primary is used (DUX-4.6).
 */
@HiltViewModel
class ThemeStateViewModel @Inject constructor(
    userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val themeSettings: StateFlow<ThemeSettings> = combine(
        userPreferencesRepository.observeThemeMode(),
        userPreferencesRepository.observeAccentSeed(),
    ) { modeToken, seed ->
        ThemeSettings(
            themeMode = parseThemeMode(modeToken),
            accentSeed = seed ?: DEFAULT_ACCENT_SEED,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ThemeSettings(),
    )
}

/** Parses a persisted theme-mode token into [ThemeMode], defaulting to DARK on any mismatch. */
internal fun parseThemeMode(token: String?): ThemeMode = when (token?.uppercase()) {
    "LIGHT" -> ThemeMode.LIGHT
    "SYSTEM" -> ThemeMode.SYSTEM
    else -> ThemeMode.DARK
}
