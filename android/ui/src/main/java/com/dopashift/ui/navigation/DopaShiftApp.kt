package com.dopashift.ui.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.dopashift.ui.adaptive.AdaptiveScaffold
import com.dopashift.ui.adaptive.TopDestination
import com.dopashift.ui.theme.DopaShiftTheme
import com.dopashift.ui.theme.ThemeSettings
import com.dopashift.ui.theme.ThemeStateViewModel
import kotlinx.coroutines.flow.map

/**
 * Root composable for DopaShift.
 *
 * Applies the DopaShift theme (including user accent color) and observes user preferences
 * to determine whether onboarding has been completed:
 * - New users (onboarding not completed): starts at the onboarding flow, no adaptive chrome.
 * - Returning users: starts at the dashboard hosted inside the shared [AdaptiveScaffold].
 *
 * Requirement 17.4: Onboarding is a guided, minimal-friction sequence (goal selection,
 * first habit setup) — never a blank-state dashboard on first launch.
 * Requirement 19.15: User-selected accent color is applied globally.
 */
@Composable
fun DopaShiftApp(
    viewModel: DopaShiftAppViewModel = hiltViewModel(),
    themeStateViewModel: ThemeStateViewModel = hiltViewModel(),
) {
    val appState by viewModel.appState.collectAsState()
    val themeSettings: ThemeSettings by themeStateViewModel.themeSettings.collectAsState()

    // Theme mode + accent are read reactively from DataStore, so a change recolors the visible
    // screen without an app restart and without losing state (DUX-4.7).
    DopaShiftTheme(
        themeMode = themeSettings.themeMode,
        accentSeed = themeSettings.accentSeed,
    ) {
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

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
private fun DopaShiftAppContent(
    startDestination: String
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Onboarding stays outside the adaptive chrome (no nav bar / rail), as before.
    val showAdaptiveChrome = currentRoute != null && currentRoute != DopaShiftRoutes.ONBOARDING

    if (showAdaptiveChrome) {
        // calculateWindowSizeClass requires an Activity; obtain it from the composition context.
        val activity = LocalContext.current.findActivity()
        val windowSizeClass = calculateWindowSizeClass(activity)
        val foldingFeature = rememberFoldingFeature(activity)

        // The active TopDestination is derived from the current nav route; a rail/bar selection
        // navigates to that destination's route.
        val selected = topDestinationForRoute(currentRoute)

        AdaptiveScaffold(
            windowSizeClass = windowSizeClass,
            selected = selected,
            onSelect = { destination ->
                navController.navigateToTopDestination(destination)
            },
            foldingFeature = foldingFeature,
        ) { _, contentPadding ->
            DopaShiftNavGraph(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.padding(contentPadding),
            )
        }
    } else {
        // Onboarding (and any other pre-chrome route): render the graph edge-to-edge.
        DopaShiftNavGraph(
            navController = navController,
            startDestination = startDestination,
        )
    }
}

/**
 * Maps the current navigation [route] to the [TopDestination] that should appear selected in the
 * bottom bar / navigation rail. Routes outside the four top-level destinations default to [HOME]
 * so a detail screen reached from Home keeps Home highlighted.
 */
private fun topDestinationForRoute(route: String?): TopDestination = when (route) {
    DopaShiftRoutes.GOALS -> TopDestination.GOALS
    DopaShiftRoutes.ANALYTICS -> TopDestination.ANALYTICS
    DopaShiftRoutes.SETTINGS -> TopDestination.SETTINGS
    else -> TopDestination.HOME
}

/** Resolves the nav route for a given [TopDestination]. */
private fun TopDestination.route(): String = when (this) {
    TopDestination.HOME -> DopaShiftRoutes.DASHBOARD
    TopDestination.GOALS -> DopaShiftRoutes.GOALS
    TopDestination.ANALYTICS -> DopaShiftRoutes.ANALYTICS
    TopDestination.SETTINGS -> DopaShiftRoutes.SETTINGS
}

/**
 * Navigates to the given top-level [destination], preserving the standard bottom-nav behaviour:
 * pop up to the start destination saving state, single-top, and restore previously saved state.
 */
private fun NavHostController.navigateToTopDestination(destination: TopDestination) {
    navigate(destination.route()) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Observes the active [FoldingFeature] via [WindowInfoTracker] so [AdaptiveScaffold] can reserve
 * hinge occlusion bounds as padding (DUX-1.7). Emits `null` when the device is flat / not folded.
 */
@Composable
private fun rememberFoldingFeature(activity: Activity): FoldingFeature? {
    val foldingFeature by remember(activity) {
        WindowInfoTracker.getOrCreate(activity)
            .windowLayoutInfo(activity)
            .map { info -> info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull() }
    }.collectAsStateWithLifecycle(initialValue = null)
    return foldingFeature
}

/**
 * Unwraps a [ContextWrapper] chain to find the hosting [Activity], required by
 * [calculateWindowSizeClass]. The composition is always hosted by an Activity in this app.
 */
private fun Context.findActivity(): Activity {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    error("DopaShiftApp must be hosted by an Activity to compute the WindowSizeClass.")
}
