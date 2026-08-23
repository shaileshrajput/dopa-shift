package com.dopashift.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.dopashift.ui.R

/**
 * Navigation destinations for the bottom navigation bar.
 * Per Requirement 17.8: bottom navigation bar on mobile (Home, Goals, Analytics, Settings).
 * Labels are localized via string resources (en, hi, mr).
 */
enum class BottomNavDestination(
    val route: String,
    @StringRes val labelResId: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    Home(
        route = DopaShiftRoutes.DASHBOARD,
        labelResId = R.string.nav_home,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
    ),
    Goals(
        route = DopaShiftRoutes.GOALS,
        labelResId = R.string.nav_goals,
        selectedIcon = Icons.Filled.TrackChanges,
        unselectedIcon = Icons.Outlined.TrackChanges,
    ),
    Analytics(
        route = DopaShiftRoutes.ANALYTICS,
        labelResId = R.string.nav_analytics,
        selectedIcon = Icons.Filled.BarChart,
        unselectedIcon = Icons.Outlined.BarChart,
    ),
    Settings(
        route = DopaShiftRoutes.SETTINGS,
        labelResId = R.string.nav_settings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
    ),
}

/**
 * Material3 NavigationBar for DopaShift mobile app.
 * Displays four tabs: Home, Goals, Analytics, Settings.
 * Each item shows an icon (Material Icons) and a localized label from string resources.
 *
 * @param currentRoute The currently active navigation route.
 * @param onNavigate Callback invoked when the user taps a navigation item.
 */
@Composable
fun DopaShiftBottomNavigation(
    currentRoute: String?,
    onNavigate: (BottomNavDestination) -> Unit,
) {
    NavigationBar {
        BottomNavDestination.entries.forEach { destination ->
            val selected = currentRoute == destination.route
            val label = stringResource(id = destination.labelResId)
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = label,
                    )
                },
                label = { Text(text = label) },
            )
        }
    }
}
