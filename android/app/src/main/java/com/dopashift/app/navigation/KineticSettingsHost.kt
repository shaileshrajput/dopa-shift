package com.dopashift.app.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dopashift.app.R
import com.dopashift.ui.adaptive.TopDestination
import com.dopashift.ui.settings.AccentPickerViewModel
import com.dopashift.ui.settings.PredefinedAccentColors
import com.dopashift.ui.settings.SettingsViewModel
import com.dopashift.ui.screens.SettingsActions
import com.dopashift.ui.screens.SettingsScreen
import com.dopashift.ui.state.AccentSwatch
import com.dopashift.ui.state.AvatarUi
import com.dopashift.ui.state.SettingsUiState

/**
 * Presentation bridge hosting the Kinetic [SettingsScreen] (AUI-7) on the [KineticRoutes.SETTINGS]
 * route (spec `android-ui-upgrade`, task 11.1).
 *
 * It observes the existing [SettingsViewModel] (profile, display name, password) and
 * [AccentPickerViewModel] (accent seed) and maps them into the presentation-only [SettingsUiState]
 * the Kinetic screen renders. The 12-swatch picker is built from the shared [PredefinedAccentColors];
 * selecting a swatch applies its seed through the accent picker so the accent propagates app-wide
 * (AUI-7.4). The "Manage App Limits" card pushes App Limits via [onOpenAppLimits] (AUI-7.2); the
 * password card delegates to the existing Keycloak-backed flow (AUI-7.5); the bottom nav navigates
 * via [onNavigate] with Settings active (AUI-7.6). No parallel data path is introduced.
 *
 * @param onNavigate invoked with the chosen bottom-nav [TopDestination] (AUI-7.6).
 * @param onOpenAppLimits pushes the App Limits & Rules screen (AUI-7.2 → AUI-3).
 * @param settingsViewModel the existing settings view-model (Hilt-provided).
 * @param accentViewModel the existing accent-picker view-model (Hilt-provided).
 */
@Composable
fun KineticSettingsHost(
    onNavigate: (TopDestination) -> Unit,
    onOpenAppLimits: () -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    accentViewModel: AccentPickerViewModel = hiltViewModel(),
) {
    val settings by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val currentSeed by accentViewModel.currentSeed.collectAsStateWithLifecycle()
    val surfaceArgb = MaterialTheme.colorScheme.surface.toArgb().toLong() and 0xFFFFFFFFL
    val context = LocalContext.current

    val swatches = PredefinedAccentColors.mapIndexed { index, argb ->
        AccentSwatch(
            id = "accent_$index",
            label = "#%06X".format(0xFFFFFF and argb.toInt()),
            color = Color(argb),
            accentSeed = argb.toInt(),
        )
    }
    val selected = swatches.firstOrNull { (it.accentSeed.toLong() and 0xFFFFFFFFL) == currentSeed }
        ?: swatches.first()

    val monogram = settings.displayName.trim().take(2).ifBlank {
        stringResource(R.string.kinetic_default_avatar_monogram)
    }.uppercase()

    val state = SettingsUiState(
        avatar = AvatarUi(imageUri = settings.profilePhotoUrl, monogram = monogram),
        fullName = settings.fullName,
        displayName = settings.displayName,
        accentSwatches = swatches,
        selectedAccent = selected,
    )

    val actions = SettingsActions(
        onUploadPhoto = { /* host wires the JPEG/PNG <=5MB picker; no-op in this bridge */ },
        onDisplayNameChange = settingsViewModel::updateDisplayName,
        onSaveDisplayName = settingsViewModel::saveDisplayName,
        onManageAppLimits = onOpenAppLimits,
        onAccentSelected = { swatch ->
            accentViewModel.apply(swatch.accentSeed.toLong() and 0xFFFFFFFFL, surfaceArgb)
        },
        onChangePassword = { current, new, confirm ->
            settingsViewModel.updateCurrentPassword(current)
            settingsViewModel.updateNewPassword(new)
            settingsViewModel.updateConfirmPassword(confirm)
            settingsViewModel.changePassword()
        },
        onNavigate = onNavigate,
    )

    SettingsScreen(state = state, on = actions)
}
