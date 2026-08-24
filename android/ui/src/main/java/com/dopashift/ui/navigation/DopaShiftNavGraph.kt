package com.dopashift.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.dopashift.ui.analytics.AnalyticsScreen
import com.dopashift.ui.dashboard.DashboardScreen
import com.dopashift.ui.goals.GoalsScreen
import com.dopashift.ui.onboarding.OnboardingScreen
import com.dopashift.ui.settings.SettingsScreen

/**
 * Main navigation graph for DopaShift.
 * Routes will be added as UI screens are implemented.
 */
object DopaShiftRoutes {
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
    const val GOALS = "goals"
    const val ANALYTICS = "analytics"
    const val SETTINGS = "settings"
}

@Composable
fun DopaShiftNavGraph(
    navController: NavHostController,
    startDestination: String = DopaShiftRoutes.DASHBOARD,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(DopaShiftRoutes.ONBOARDING) {
            OnboardingScreen(
                onOnboardingComplete = {
                    navController.navigate(DopaShiftRoutes.DASHBOARD) {
                        popUpTo(DopaShiftRoutes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }
        composable(DopaShiftRoutes.DASHBOARD) {
            DashboardScreen()
        }
        composable(DopaShiftRoutes.GOALS) {
            GoalsScreen()
        }
        composable(DopaShiftRoutes.ANALYTICS) {
            AnalyticsScreen()
        }
        composable(DopaShiftRoutes.SETTINGS) {
            SettingsScreen()
        }
    }
}
