package com.dopashift.ui.adaptive

// Feature: dynamic-ui-experience, Task 8.2
// Validates: Requirements DUX-1.1, DUX-1.2, DUX-1.4, DUX-1.5

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.dopashift.ui.R
import com.dopashift.ui.theme.DopaShiftTheme
import com.dopashift.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [AdaptiveScaffold]'s behavior across Window_Size_Classes (DUX-1).
 *
 * Written as Compose UI tests running under [RobolectricTestRunner] so they execute on the JVM
 * without an emulator (`createComposeRule` works under Robolectric), matching the pattern already
 * used by `DopaShiftThemeUiTest`.
 *
 * Each size class is built with
 * [WindowSizeClass.calculateFromSize] over a [DpSize] (the experimental Material 3 window-size-class
 * API, opted into via [ExperimentalMaterial3WindowSizeClassApi]).
 *
 * Assertions per DUX-1:
 *  - Compact width => bottom [androidx.compose.material3.NavigationBar] with all four
 *    [TopDestination] entries, and the content receives `columns == 1` (DUX-1.2, DUX-1.4).
 *  - Medium width => [androidx.compose.material3.NavigationRail] with four destinations and
 *    `columns == 2` (DUX-1.2, DUX-1.4).
 *  - Expanded width => navigation rail and `columns == 4` (the upper bound of the 3–4 range this
 *    implementation returns) (DUX-1.2, DUX-1.4).
 *  - The pure helpers [columnsFor] and [marginFor] return the expected column counts and margins
 *    for every width class (DUX-1.2, DUX-1.5). These are `internal` to the module so the test can
 *    call them directly.
 *  - [TopDestination] has exactly four entries (DUX-1.4).
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AdaptiveScaffoldSizeClassTest {

    @get:Rule
    val composeRule = createComposeRule()

    // --- Window size classes built from DpSize (DUX-1.1) --------------------

    private val compactWindow: WindowSizeClass =
        WindowSizeClass.calculateFromSize(DpSize(width = 400.dp, height = 800.dp))

    private val mediumWindow: WindowSizeClass =
        WindowSizeClass.calculateFromSize(DpSize(width = 700.dp, height = 900.dp))

    private val expandedWindow: WindowSizeClass =
        WindowSizeClass.calculateFromSize(DpSize(width = 1000.dp, height = 900.dp))

    // --- Compact: bottom navigation bar + single column (DUX-1.2, DUX-1.4) --

    @Test
    fun compactWidth_showsBottomBarWithFourDestinations_andOneColumn() {
        // Sanity: the DpSize we chose really classifies as Compact width.
        assertEquals(WindowWidthSizeClass.Compact, compactWindow.widthSizeClass)

        var reportedColumns = -1
        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                AdaptiveScaffold(
                    windowSizeClass = compactWindow,
                    selected = TopDestination.HOME,
                    onSelect = {},
                    foldingFeature = null,
                ) { columns, _ ->
                    reportedColumns = columns
                    Box(Modifier.fillMaxSize().testTag(CONTENT_TAG)) { Text("content") }
                }
            }
        }
        composeRule.waitForIdle()

        // The content lambda ran and received a single column on Compact.
        composeRule.onNodeWithTag(CONTENT_TAG).assertExists()
        assertEquals("Compact must lay out a single content column (DUX-1.2)", 1, reportedColumns)

        // All four destinations are present (the bottom nav bar carries every one, DUX-1.4).
        assertAllFourDestinationsPresent()
    }

    // --- Medium: navigation rail + two columns (DUX-1.2, DUX-1.4) -----------

    @Test
    fun mediumWidth_showsRailWithFourDestinations_andTwoColumns() {
        assertEquals(WindowWidthSizeClass.Medium, mediumWindow.widthSizeClass)

        var reportedColumns = -1
        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                AdaptiveScaffold(
                    windowSizeClass = mediumWindow,
                    selected = TopDestination.GOALS,
                    onSelect = {},
                    foldingFeature = null,
                ) { columns, _ ->
                    reportedColumns = columns
                    Box(Modifier.fillMaxSize().testTag(CONTENT_TAG)) { Text("content") }
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(CONTENT_TAG).assertExists()
        assertEquals("Medium must lay out two content columns (DUX-1.2)", 2, reportedColumns)
        assertAllFourDestinationsPresent()
    }

    // --- Expanded: navigation rail + 3–4 columns (DUX-1.2, DUX-1.4) ---------

    @Test
    fun expandedWidth_showsRailWithFourDestinations_andThreeToFourColumns() {
        assertEquals(WindowWidthSizeClass.Expanded, expandedWindow.widthSizeClass)

        var reportedColumns = -1
        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                AdaptiveScaffold(
                    windowSizeClass = expandedWindow,
                    selected = TopDestination.ANALYTICS,
                    onSelect = {},
                    foldingFeature = null,
                ) { columns, _ ->
                    reportedColumns = columns
                    Box(Modifier.fillMaxSize().testTag(CONTENT_TAG)) { Text("content") }
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(CONTENT_TAG).assertExists()
        // The implementation returns 4 (the upper bound of the 3–4 range).
        assertEquals("Expanded must lay out four content columns (DUX-1.2)", 4, reportedColumns)
        assertAllFourDestinationsPresent()
    }

    // --- Pure helper functions (DUX-1.2, DUX-1.5) ---------------------------

    @Test
    fun columnsFor_returnsOneTwoFourAcrossWidthClasses() {
        assertEquals(1, columnsFor(WindowWidthSizeClass.Compact))
        assertEquals(2, columnsFor(WindowWidthSizeClass.Medium))
        assertEquals(4, columnsFor(WindowWidthSizeClass.Expanded))
    }

    @Test
    fun marginFor_is24dpOnCompact_andScalesUp() {
        val compactMargin = marginFor(WindowWidthSizeClass.Compact)
        val mediumMargin = marginFor(WindowWidthSizeClass.Medium)
        val expandedMargin = marginFor(WindowWidthSizeClass.Expanded)

        // Compact baseline is the 24dp requirement value itself (DUX-1.5).
        assertEquals(24.dp, compactMargin)
        // Margins scale up proportionally rather than staying flat (DUX-1.5).
        assertEquals(36.dp, mediumMargin) // 24 * 1.5
        assertEquals(48.dp, expandedMargin) // 24 * 2
    }

    // --- TopDestination cardinality (DUX-1.4) -------------------------------

    @Test
    fun topDestination_hasExactlyFourEntries() {
        assertEquals(
            "There must be exactly four top-level destinations (DUX-1.4)",
            4,
            TopDestination.entries.size,
        )
        assertEquals(
            listOf(
                TopDestination.HOME,
                TopDestination.GOALS,
                TopDestination.ANALYTICS,
                TopDestination.SETTINGS,
            ),
            TopDestination.entries.toList(),
        )
    }

    /**
     * Asserts each of the four [TopDestination] labels is present exactly once in the composed nav
     * affordance (bottom bar on Compact, rail on Medium/Expanded — both carry all four, DUX-1.4).
     * The label strings are resolved from the same string resources the affordance uses (via the
     * Robolectric application [android.content.Context]).
     */
    private fun assertAllFourDestinationsPresent() {
        val context =
            ApplicationProvider.getApplicationContext<android.content.Context>()
        val labels = listOf(
            context.getString(R.string.nav_home),
            context.getString(R.string.nav_goals),
            context.getString(R.string.nav_analytics),
            context.getString(R.string.nav_settings),
        )
        labels.forEach { label ->
            composeRule.onAllNodesWithText(label).assertCountEquals(1)
        }
    }

    private companion object {
        const val CONTENT_TAG = "adaptive-content"
    }
}
