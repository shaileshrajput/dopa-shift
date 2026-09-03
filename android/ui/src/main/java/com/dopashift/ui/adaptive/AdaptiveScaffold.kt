package com.dopashift.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.window.layout.FoldingFeature
import com.dopashift.ui.theme.DopaShiftTheme

/**
 * The single adaptive layout entry point shared by every top-level screen (DUX-1).
 *
 * It reads the current [windowSizeClass] — recomputed by the host on each configuration change via
 * `calculateWindowSizeClass(activity)` — and selects the navigation affordance, content column
 * count, and screen margins for the running window:
 *
 * | Width size class | Navigation | Columns | Margins |
 * |------------------|------------|---------|---------|
 * | Compact          | bottom [NavigationBar] (4 destinations) | 1 | 24dp (DUX-1.5) |
 * | Medium           | [NavigationRail] (4 destinations)       | 2 | scaled up (DUX-1.5) |
 * | Expanded         | [NavigationRail] (4 destinations)       | 3–4 | scaled up (DUX-1.5) |
 *
 * Centralising the breakpoint logic here means Goals, Analytics, and Settings obey the same rules
 * as the Dashboard by construction rather than by repetition (DUX-1.8).
 *
 * The [content] slot receives the resolved `columns` count and a [PaddingValues] that already folds
 * in the screen margins, the system-bar insets (edge-to-edge, DUX-4.9), and any foldable hinge
 * occlusion (DUX-1.7). Callers drive a `LazyVerticalGrid`/`FlowRow` from `columns` with a minimum
 * column width so content reflows down to a 320dp window without clipping (DUX-1.6).
 *
 * State retention across reconfiguration (scroll position, in-progress input — DUX-1.3) is the
 * caller's responsibility via `rememberSaveable` and navigation-scoped ViewModels; this scaffold
 * only re-lays-out and never itself discards that state.
 *
 * @param windowSizeClass the current window size class (re-evaluated on configuration change).
 * @param selected the currently active [TopDestination].
 * @param onSelect invoked when the user chooses a different [TopDestination].
 * @param foldingFeature the active [FoldingFeature] from `WindowInfoTracker`, or `null` when the
 *   device is not folded; its occlusion bounds are reserved as padding so primary controls avoid
 *   the hinge (DUX-1.7).
 * @param modifier modifier applied to the root scaffold.
 * @param content the screen content, receiving the resolved column count and content padding.
 */
@Composable
fun AdaptiveScaffold(
    windowSizeClass: WindowSizeClass,
    selected: TopDestination,
    onSelect: (TopDestination) -> Unit,
    foldingFeature: FoldingFeature?,
    modifier: Modifier = Modifier,
    content: @Composable (columns: Int, contentPadding: PaddingValues) -> Unit,
) {
    val widthClass = windowSizeClass.widthSizeClass
    val isCompact = widthClass == WindowWidthSizeClass.Compact
    val columns = columnsFor(widthClass)
    val margin = marginFor(widthClass)
    val hingePadding = hingeOcclusionWidth(foldingFeature)

    // Wrap the content invocation in a single, remembered movable composition so its
    // remembered/rememberSaveable state survives a width-class change (Compact <-> Medium/Expanded).
    // Without this, the `content(...)` call sits in two different positions across two different
    // subtrees; flipping the width class would tear down one subtree and rebuild the other,
    // discarding the content's state and violating DUX-1.3. `movableContentOf` moves the SAME
    // content node between the two layout branches instead of recreating it. The resolved column
    // count and per-branch content padding are passed as parameters so identity stays stable while
    // the values adapt per size class.
    val movableContent = remember(content) {
        movableContentOf { columns: Int, contentPadding: PaddingValues ->
            content(columns, contentPadding)
        }
    }

    if (isCompact) {
        // Compact: bottom navigation bar with the four destinations (DUX-1.4).
        Scaffold(
            modifier = modifier.fillMaxSize(),
            // Edge-to-edge: let the bar draw behind system bars and consume its own insets (DUX-4.9).
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                NavigationBar {
                    TopDestination.entries.forEach { destination ->
                        adaptiveBarItem(destination, selected, onSelect)
                    }
                }
            },
        ) { innerPadding ->
            movableContent(
                columns,
                mergePadding(innerPadding, margin, hingePadding),
            )
        }
    } else {
        // Medium / Expanded: navigation rail carrying the same four destinations (DUX-1.4).
        Scaffold(
            modifier = modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
        ) { innerPadding ->
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(
                            top = innerPadding.calculateTopPadding(),
                            bottom = innerPadding.calculateBottomPadding(),
                        ),
                ) {
                    TopDestination.entries.forEach { destination ->
                        adaptiveRailItem(destination, selected, onSelect)
                    }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    movableContent(
                        columns,
                        mergePadding(innerPadding, margin, hingePadding, includeStart = false),
                    )
                }
            }
        }
    }
}

/**
 * Builds the [Icon] for [destination], choosing the selected/unselected variant and using [label]
 * as the content description. Shared by [adaptiveBarItem] and [adaptiveRailItem] so the icon/label
 * construction is not duplicated across the bottom-bar and rail scopes.
 */
@Composable
private fun navItemIcon(destination: TopDestination, isSelected: Boolean, label: String) {
    Icon(
        imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
        contentDescription = label,
    )
}

/**
 * Renders a single bottom-[NavigationBar] destination. Declared as a [RowScope] extension so it can
 * only be called from inside a `NavigationBar { ... }` lambda, where [NavigationBarItem] requires
 * its [RowScope] receiver.
 */
@Composable
private fun RowScope.adaptiveBarItem(
    destination: TopDestination,
    selected: TopDestination,
    onSelect: (TopDestination) -> Unit,
) {
    val isSelected = destination == selected
    val label = stringResource(id = destination.labelResId)
    NavigationBarItem(
        selected = isSelected,
        onClick = { onSelect(destination) },
        icon = { navItemIcon(destination, isSelected, label) },
        label = { Text(text = label) },
    )
}

/**
 * Renders a single [NavigationRail] destination. Declared as a [ColumnScope] extension so it can
 * only be called from inside a `NavigationRail { ... }` lambda, where [NavigationRailItem] requires
 * its [ColumnScope] receiver.
 */
@Composable
private fun ColumnScope.adaptiveRailItem(
    destination: TopDestination,
    selected: TopDestination,
    onSelect: (TopDestination) -> Unit,
) {
    val isSelected = destination == selected
    val label = stringResource(id = destination.labelResId)
    NavigationRailItem(
        selected = isSelected,
        onClick = { onSelect(destination) },
        icon = { navItemIcon(destination, isSelected, label) },
        label = { Text(text = label) },
    )
}

/**
 * Resolves the content column count for the given [widthClass] (DUX-1.2):
 * 1 on Compact, 2 on Medium, and 4 on Expanded (the upper bound of the 3–4 range).
 */
internal fun columnsFor(widthClass: WindowWidthSizeClass): Int = when (widthClass) {
    WindowWidthSizeClass.Compact -> 1
    WindowWidthSizeClass.Medium -> 2
    else -> 4
}

/**
 * Resolves the screen margin for the given [widthClass] (DUX-1.5): 24dp on Compact, scaled up on
 * Medium and Expanded so components never feel cramped against the screen edge. Margins scale
 * proportionally rather than only widening the content columns.
 */
internal fun marginFor(widthClass: WindowWidthSizeClass): Dp = when (widthClass) {
    WindowWidthSizeClass.Compact -> COMPACT_MARGIN
    WindowWidthSizeClass.Medium -> COMPACT_MARGIN * 1.5f
    else -> COMPACT_MARGIN * 2f
}

/**
 * The width of the region occluded by a vertical (book-posture) hinge, reserved as horizontal
 * padding so primary controls never land under the hinge (DUX-1.7). Returns `0.dp` when there is
 * no separating fold or the fold is horizontal.
 */
internal fun hingeOcclusionWidth(foldingFeature: FoldingFeature?): Dp {
    val feature = foldingFeature ?: return 0.dp
    if (feature.occlusionType != FoldingFeature.OcclusionType.FULL) return 0.dp
    return if (feature.orientation == FoldingFeature.Orientation.VERTICAL) {
        feature.bounds.width().dp
    } else {
        0.dp
    }
}

/**
 * Combines the scaffold's inset [innerPadding] with the resolved screen [margin] and any hinge
 * occlusion, keeping the larger of inset-vs-margin on each edge so both the system bars and the
 * design-system margin are respected. When [includeStart] is false the leading edge is left to the
 * navigation rail (which already occupies it).
 */
private fun mergePadding(
    innerPadding: PaddingValues,
    margin: Dp,
    hinge: Dp,
    includeStart: Boolean = true,
    layoutDirection: LayoutDirection = LayoutDirection.Ltr,
): PaddingValues = PaddingValues(
    start = if (includeStart) {
        max(innerPadding.calculateStartPadding(layoutDirection), margin) + hinge
    } else {
        margin + hinge
    },
    top = max(innerPadding.calculateTopPadding(), margin),
    end = max(innerPadding.calculateEndPadding(layoutDirection), margin) + hinge,
    bottom = max(innerPadding.calculateBottomPadding(), margin),
)

/** Base Compact screen margin (DUX-1.5). 24dp is the requirement value itself. */
private val COMPACT_MARGIN: Dp = 24.dp
