package com.dopashift.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

/**
 * Root composable for DopaShift.
 *
 * Provides a Material3 [Scaffold] with:
 * - A [DopaShiftBottomNavigation] bar (Requirement 17.8)
 * - A [DopaShiftNavGraph] content area driven by the NavHost
 *
 * Navigation behavior:
 * - Selecting a tab navigates to the corresponding route
 * - Re-selecting the current tab pops back to the start of that tab's back stack
 * - The back stack is restored when switching between tabs (saveState/restoreState)
 */
@Composable
fun DopaShiftApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            DopaShiftBottomNavigation(
                currentRoute = currentRoute,
                onNavigate = { destination ->
                    navController.navigate(destination.route) {
                        // Pop up to the start destination of the graph to avoid
                        // building up a large stack of destinations on the back stack
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        // Avoid multiple copies of the same destination
                        launchSingleTop = true
                        // Restore state when re-selecting a previously selected tab
                        restoreState = true
                    }
                },
            )
        },
    ) { innerPadding ->
        DopaShiftNavGraph(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
        )
    }
}
