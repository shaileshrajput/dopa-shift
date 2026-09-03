package com.dopashift.app.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import com.dopashift.ui.adaptive.TopDestination
import com.dopashift.ui.onboarding.OnboardingScreen

/**
 * Route constants for the DopaShift Kinetic navigation graph wired in the `app` module
 * (spec `android-ui-upgrade`, task 11.1).
 *
 * The four [top-level destinations][TopDestination] — Home, Goals, Analytics, Settings — are the
 * bottom-nav members (AUI-1.8, AUI-4.4, AUI-6.6, AUI-7.6). [GOAL_DETAIL] and [APP_LIMITS] are
 * pushed (non-top-level) destinations reached from Goals / Settings respectively (AUI-5, AUI-3,
 * AUI-7.2). The quick-create surface owned by `dashboard-quick-create` is opened from the
 * persistent FAB on the Home and Goals screens (AUI-1.7, AUI-4.3).
 */
object KineticRoutes {
    const val ONBOARDING = "kinetic_onboarding"
    const val HOME = "kinetic_home"
    const val GOALS = "kinetic_goals"
    const val ANALYTICS = "kinetic_analytics"
    const val SETTINGS = "kinetic_settings"
    const val GOAL_DETAIL = "kinetic_goal_detail/{goalId}"
    const val APP_LIMITS = "kinetic_app_limits"
    const val QUICK_CREATE = "kinetic_quick_create"

    /** Builds the concrete Goal Detail route for a given goal id. */
    fun goalDetail(goalId: String): String = "kinetic_goal_detail/$goalId"

    /** The nav-argument key carrying the selected goal id into [GOAL_DETAIL]. */
    const val ARG_GOAL_ID = "goalId"

    /** Resolves the [route] of a top-level [TopDestination]. */
    fun routeOf(destination: TopDestination): String = when (destination) {
        TopDestination.HOME -> HOME
        TopDestination.GOALS -> GOALS
        TopDestination.ANALYTICS -> ANALYTICS
        TopDestination.SETTINGS -> SETTINGS
    }
}

/**
 * The DopaShift Kinetic [NavHost] (spec `android-ui-upgrade`, task 11.1).
 *
 * Registers the four [TopDestination] routes hosting the four Kinetic screens
 * ([KineticHomeHost], [KineticGoalsHost], [KineticAnalyticsHost], [KineticSettingsHost]) and the
 * two pushed destinations ([KineticGoalDetailHost] for AUI-5, [KineticAppLimitsHost] for AUI-3).
 * The persistent Quick-Create FAB on Home and Goals routes to [QUICK_CREATE][KineticRoutes.QUICK_CREATE],
 * which hosts the `dashboard-quick-create` surface (AUI-1.7, AUI-4.3).
 *
 * Each screen host is a thin presentation bridge: it obtains the existing Hilt view-model, maps its
 * domain-backed state into the screen's `*UiState` contract, and dispatches gestures back — no
 * parallel data path is introduced (design → "presentation-only, no parallel data path").
 *
 * Top-level navigation uses the standard bottom-nav back-stack behavior (pop up to the start
 * destination saving state, single-top, restore state) so re-selecting a tab does not stack
 * duplicate copies.
 *
 * @param navController the host controller; defaults to a remembered controller.
 * @param startDestination the initial route; defaults to [KineticRoutes.HOME].
 * @param modifier optional layout modifier for the host container.
 */
@Composable
fun KineticNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: String = KineticRoutes.HOME,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable(KineticRoutes.ONBOARDING) {
                // First-launch guided onboarding (Requirement 17.4). On finish/skip it routes to
                // Home and pops itself off the back stack so Back does not return to onboarding.
                OnboardingScreen(
                    onOnboardingComplete = {
                        navController.navigate(
                            KineticRoutes.HOME,
                            navOptions {
                                popUpTo(KineticRoutes.ONBOARDING) { inclusive = true }
                                launchSingleTop = true
                            },
                        )
                    },
                )
            }

            composable(KineticRoutes.HOME) {
                KineticHomeHost(
                    onNavigate = navController::navigateToTopDestination,
                    onOpenQuickCreate = { navController.navigate(KineticRoutes.QUICK_CREATE) },
                    onOpenGoalDetail = { goalId ->
                        navController.navigate(KineticRoutes.goalDetail(goalId))
                    },
                )
            }

            composable(KineticRoutes.GOALS) {
                KineticGoalsHost(
                    onNavigate = navController::navigateToTopDestination,
                    onOpenQuickCreate = { navController.navigate(KineticRoutes.QUICK_CREATE) },
                    onOpenGoalDetail = { goalId ->
                        navController.navigate(KineticRoutes.goalDetail(goalId))
                    },
                )
            }

            composable(KineticRoutes.ANALYTICS) {
                KineticAnalyticsHost(
                    onNavigate = navController::navigateToTopDestination,
                )
            }

            composable(KineticRoutes.SETTINGS) {
                KineticSettingsHost(
                    onNavigate = navController::navigateToTopDestination,
                    onOpenAppLimits = { navController.navigate(KineticRoutes.APP_LIMITS) },
                )
            }

            // --- Pushed (non-top-level) destinations ---

            composable(KineticRoutes.GOAL_DETAIL) { backStackEntry ->
                val goalId = backStackEntry.arguments?.getString(KineticRoutes.ARG_GOAL_ID)
                KineticGoalDetailHost(
                    goalId = goalId,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(KineticRoutes.APP_LIMITS) {
                KineticAppLimitsHost(
                    onBack = { navController.popBackStack() },
                )
            }

            composable(KineticRoutes.QUICK_CREATE) {
                // The dashboard-quick-create surface (owned by that sibling spec). The FAB on Home
                // and Goals routes here (AUI-1.7, AUI-4.3); it clears itself on completion via back.
                KineticQuickCreateHost(
                    onClose = { navController.popBackStack() },
                    onOpenAppLimits = {
                        navController.navigate(KineticRoutes.APP_LIMITS)
                    },
                )
            }
        }
    }
}

/**
 * Navigates to the given top-level [destination] with the standard bottom-nav behavior: pop up to
 * the graph start destination saving state, single-top, and restore any previously saved state so
 * tab switches do not stack duplicate destinations.
 */
private fun NavHostController.navigateToTopDestination(destination: TopDestination) {
    navigate(KineticRoutes.routeOf(destination)) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
