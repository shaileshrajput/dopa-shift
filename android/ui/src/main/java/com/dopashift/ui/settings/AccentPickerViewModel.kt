package com.dopashift.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dopashift.domain.presentation.ContrastCalculator
import com.dopashift.domain.repository.UserPreferencesRepository
import com.dopashift.ui.theme.DEFAULT_ACCENT_SEED
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Coordinates the accent-seed selection for [AccentColorPicker] (DUX-4.6, DUX-4.7, DUX-5.4).
 *
 * The current seed is observed from the existing `data` DataStore via
 * [UserPreferencesRepository.observeAccentSeed]; a missing value maps to [DEFAULT_ACCENT_SEED]
 * so the design-system primary is used (DUX-4.6). Applying a seed runs the WCAG AA contrast
 * guard against the active theme surface (DUX-5.4) before persisting via
 * [UserPreferencesRepository.setAccentSeed] — persisting the *effective* (nearest-compliant)
 * shade so the theme recolors live without an app restart (DUX-4.7).
 */
@HiltViewModel
class AccentPickerViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    /**
     * The currently persisted accent seed as an ARGB color long, or [DEFAULT_ACCENT_SEED] when
     * no override is stored (design-system primary).
     */
    val currentSeed: StateFlow<Long> = userPreferencesRepository.observeAccentSeed()
        .map { it ?: DEFAULT_ACCENT_SEED }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DEFAULT_ACCENT_SEED,
        )

    /**
     * Applies the chosen accent [seed] after running the contrast guard against [surfaceArgb]
     * (the ARGB long of the active theme surface). If [seed] fails WCAG AA against the surface
     * it is nudged to the nearest compliant shade (DUX-5.4) and that effective shade is
     * persisted; the [DEFAULT_ACCENT_SEED] sentinel is persisted as-is (it means "use the
     * palette primary", not a literal color to validate).
     *
     * @param seed the raw accent seed the user picked (ARGB color long).
     * @param surfaceArgb the active [androidx.compose.material3.ColorScheme.surface] as an ARGB long.
     * @param largeText whether AA is evaluated against the large-text/non-text threshold (3:1)
     *   rather than the body threshold (4.5:1). Accent seeds drive meaningful non-text elements
     *   (progress rings, toggles), so callers typically pass `true`.
     */
    fun apply(seed: Long, surfaceArgb: Long, largeText: Boolean = true) {
        val effective = effectiveSeed(seed, surfaceArgb, largeText)
        viewModelScope.launch {
            userPreferencesRepository.setAccentSeed(effective)
        }
    }

    /**
     * Computes the effective accent seed for [seed] against [surfaceArgb] without persisting it,
     * so callers can preview the guarded result. Returns [DEFAULT_ACCENT_SEED] unchanged; for a
     * concrete color, returns the nearest AA-compliant shade against the surface (DUX-5.4).
     */
    fun effectiveSeed(seed: Long, surfaceArgb: Long, largeText: Boolean = true): Long =
        if (seed == DEFAULT_ACCENT_SEED) {
            DEFAULT_ACCENT_SEED
        } else {
            ContrastCalculator.nearestCompliant(seed, surfaceArgb, largeText)
        }

    /**
     * Whether the raw [seed] would be nudged by the contrast guard against [surfaceArgb]
     * (i.e. it fails AA and a different, compliant shade is applied). Used to surface the
     * "adjusted for readability" indicator (DUX-5.4). The default sentinel is never adjusted.
     */
    fun isContrastAdjusted(seed: Long, surfaceArgb: Long, largeText: Boolean = true): Boolean =
        seed != DEFAULT_ACCENT_SEED &&
            !ContrastCalculator.meetsAA(seed, surfaceArgb, largeText)
}
