package com.dopashift.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dopashift.interception.InterceptionPermissionHelper
import com.dopashift.ui.theme.DopaShiftTheme
import com.dopashift.ui.theme.ThemeSettings
import com.dopashift.ui.theme.ThemeStateViewModel

/**
 * Root composable for the DopaShift Kinetic experience, wired in the `app` module
 * (spec `android-ui-upgrade`, task 11.1).
 *
 * Applies the [DopaShiftTheme] with the user's reactive theme mode + accent seed (so a Settings
 * accent selection recolors every screen without a restart — AUI-7.4) and hosts the [KineticNavHost]
 * that registers the four bottom-nav destinations plus the pushed Goal Detail and App Limits
 * screens. The accent seed is read from the existing [ThemeStateViewModel], keeping persistence in
 * the `data` module (no parallel data path).
 *
 * The whole experience is wrapped in [RequiredPermissionsGate], which re-checks the special-access
 * permissions (usage stats + overlay) on every app open / focus and surfaces a blocking permission
 * dialog whenever they are not granted.
 *
 * @param permissionHelper shared permission checker/intent-builder, supplied by [MainActivity]'s
 *   Hilt-injected instance so the gate can re-check status and open the relevant Settings screens.
 */
@Composable
fun DopaShiftKineticApp(
    permissionHelper: InterceptionPermissionHelper,
    themeStateViewModel: ThemeStateViewModel = hiltViewModel(),
    startViewModel: KineticStartViewModel = hiltViewModel(),
) {
    val themeSettings: ThemeSettings by themeStateViewModel.themeSettings.collectAsStateWithLifecycle()
    val startState: KineticStartState by startViewModel.startState.collectAsStateWithLifecycle()

    DopaShiftTheme(
        themeMode = themeSettings.themeMode,
        accentSeed = themeSettings.accentSeed,
    ) {
        // Wait for the onboarding flag to resolve before choosing a start destination so first-run
        // users never briefly see Home before onboarding (Requirement 17.4).
        when (val state = startState) {
            is KineticStartState.Loading -> Unit
            is KineticStartState.Ready -> {
                val navController = rememberNavController()
                val currentEntry by navController.currentBackStackEntryAsState()
                // The blocking permission gate is suppressed while onboarding is on screen, since
                // onboarding drives its own permission handoff step. It re-engages once the user
                // leaves onboarding for the main app.
                val onOnboarding = currentEntry?.destination?.route == KineticRoutes.ONBOARDING ||
                    (currentEntry == null && state.startDestination == KineticRoutes.ONBOARDING)

                RequiredPermissionsGate(
                    permissionHelper = permissionHelper,
                    enabled = !onOnboarding,
                ) {
                    KineticNavHost(
                        navController = navController,
                        startDestination = state.startDestination,
                    )
                }
            }
        }
    }
}
