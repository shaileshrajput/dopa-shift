package com.dopashift.app.navigation

import androidx.compose.runtime.Composable
import com.dopashift.ui.quickcreate.QuickCreateDashboardScreen

/**
 * Presentation bridge hosting the `dashboard-quick-create` surface on the
 * [KineticRoutes.QUICK_CREATE] route (spec `android-ui-upgrade`, task 11.1).
 *
 * The persistent Quick-Create FAB on the Home ([KineticHomeHost]) and Goals ([KineticGoalsHost])
 * screens routes here (AUI-1.7, AUI-4.3). The quick-create flow — the FAB menu, the three creation
 * sheets, undo, and confirm-discard — is entirely owned by the sibling `dashboard-quick-create`
 * spec via [QuickCreateDashboardScreen]; this bridge only mounts that surface and forwards the
 * "Manage App Limits" menu action to the App Limits destination.
 *
 * @param onClose pops back to the screen that opened Quick-Create.
 * @param onOpenAppLimits navigates to the App Limits & Rules screen when the menu requests it.
 */
@Composable
fun KineticQuickCreateHost(
    onClose: () -> Unit,
    onOpenAppLimits: () -> Unit,
) {
    QuickCreateDashboardScreen(
        onSkipOnboarding = onClose,
        onNavigateToInterceptionRules = onOpenAppLimits,
        onBack = onClose,
    )
}
