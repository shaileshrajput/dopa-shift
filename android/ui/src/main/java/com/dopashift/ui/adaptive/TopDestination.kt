package com.dopashift.ui.adaptive

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
import androidx.compose.ui.graphics.vector.ImageVector
import com.dopashift.ui.R

/**
 * The top-level navigation destinations of the app (DUX-1.4).
 *
 * There are exactly four values — [HOME], [GOALS], [ANALYTICS], [SETTINGS] — matching parent
 * Requirement 17, AC8. [AdaptiveScaffold] renders these as a bottom [androidx.compose.material3.NavigationBar]
 * on Compact windows and as a [androidx.compose.material3.NavigationRail] on Medium and Expanded
 * windows, so the same four destinations are available at every window size class.
 *
 * Each destination carries its selected/unselected [ImageVector] and a localized label
 * ([labelResId]) so the affordance is accessible and translatable (en, hi, mr).
 *
 * @property labelResId string resource for the destination's localized label.
 * @property selectedIcon icon shown when the destination is the active [TopDestination].
 * @property unselectedIcon icon shown when the destination is not active.
 */
enum class TopDestination(
    @StringRes val labelResId: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME(
        labelResId = R.string.nav_home,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
    ),
    GOALS(
        labelResId = R.string.nav_goals,
        selectedIcon = Icons.Filled.TrackChanges,
        unselectedIcon = Icons.Outlined.TrackChanges,
    ),
    ANALYTICS(
        labelResId = R.string.nav_analytics,
        selectedIcon = Icons.Filled.BarChart,
        unselectedIcon = Icons.Outlined.BarChart,
    ),
    SETTINGS(
        labelResId = R.string.nav_settings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
    ),
}
