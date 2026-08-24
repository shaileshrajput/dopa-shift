package com.dopashift.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dopashift.ui.theme.DopaShiftTheme

/**
 * Root composable for DopaShift.
 *
 * Applies the DopaShift theme (including user accent color) and observes user preferences
 * to determine whether onboarding has been completed:
 * - New users (onboarding not completed): starts at the onboarding flow, bottom nav hidden.
 * - Returning users: starts at the dashboard with full bottom navigation.
 *
 * Requirement 17.4: Onboarding is a guided, minimal-friction sequence (goal selection,
 * first habit setup) — never a blank-state dashboard on first launch.
 * Requirement 19.15: User-selected accent color is applied globally.
 */
@Composable
fun DopaShiftApp(
    viewModel: DopaShiftAppViewModel = hiltViewModel()
) {
    val appState by viewModel.appState.collectAsState()
    val userAccentColor by viewModel.userAccentColor.collectAsState()

    DopaShiftTheme(userAccentColor = userAccentColor) {
        when (val state = appState) {
            is AppStartState.Loading -> {
                // Brief loading while DataStore is read
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is AppStartState.Ready -> {
                DopaShiftAppContent(
                    startDestination = state.startDestination
                )
            }
        }
    }
}

@Composable
private fun DopaShiftAppContent(
    startDestination: String
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Hide bottom nav during onboarding
    val showBottomNav = currentRoute != null && currentRoute != DopaShiftRoutes.ONBOARDING

    Scaffold(
        bottomBar = {
            if (showBottomNav) {
                DopaShiftBottomNavigation(
                    currentRoute = currentRoute,
                    onNavigate = { destination ->
                        navController.navigate(destination.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        DopaShiftNavGraph(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding),
        )
    }
}
