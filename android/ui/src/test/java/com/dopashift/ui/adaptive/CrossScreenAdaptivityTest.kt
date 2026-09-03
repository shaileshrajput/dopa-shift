package com.dopashift.ui.adaptive

// Feature: dynamic-ui-experience, Task 12.3
// Validates: Requirements DUX-1.8 (and the DUX-2.10 back-navigation contract)

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
 * Cross-screen adaptivity and back-navigation tests for the shared [AdaptiveScaffold] (DUX-1.8).
 *
 * DUX-1.8 requires that the adaptive rules (nav affordance, column count, margins) defined for the
 * Dashboard apply *uniformly* to the Goals, Analytics, and Settings screens too — the behaviour is
 * a property of the window size class, not of which screen is hosted. These tests prove that
 * uniformity directly: for a fixed [WindowSizeClass], every [TopDestination] renders the *same*
 * column count ([columnsFor]) and the *same* four navigation labels, regardless of which
 * destination is currently selected.
 *
 * Written as Compose UI tests under [RobolectricTestRunner] (JVM, no emulator) — the same pattern as
 * [AdaptiveScaffoldSizeClassTest]. Each test wraps content in `DopaShiftTheme(ThemeMode.DARK)` and
 * uses exactly one `setContent` per `@Test`.
 *
 * The back-navigation test asserts the *contract* only: navigating dashboard -> goals then
 * `popBackStack()` returns to the start destination. `TestNavHostController` is not on this
 * module's test classpath, so a minimal two-route `NavHost` harness driven by a real
 * `rememberNavController` is used instead. The predictive-back *animation* (non-animated / seamless
 * back on Android 14+) is validated at the instrumented level; here we assert the back-navigation
 * contract that predictive back builds upon.
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CrossScreenAdaptivityTest {

    @get:Rule
    val composeRule = createComposeRule()

    // --- Window size classes built from DpSize (DUX-1.1) --------------------

    private val compactWindow: WindowSizeClass =
        WindowSizeClass.calculateFromSize(DpSize(width = 400.dp, height = 800.dp))

    private val expandedWindow: WindowSizeClass =
        WindowSizeClass.calculateFromSize(DpSize(width = 1000.dp, height = 900.dp))

    // --- Cross-screen adaptivity (DUX-1.8) ----------------------------------

    /**
     * At a Compact window every destination lays out a single content column and shows all four
     * nav labels — the adaptive behaviour is destination-independent (DUX-1.8).
     */
    @Test
    fun compactWindow_isUniformAcrossAllDestinations_oneColumnAndFourLabels() {
        assertEquals(WindowWidthSizeClass.Compact, compactWindow.widthSizeClass)
        assertAdaptiveBehaviorUniformAcrossDestinations(
            windowSizeClass = compactWindow,
            expectedColumns = columnsFor(WindowWidthSizeClass.Compact), // 1
        )
    }

    /**
     * At an Expanded window every destination lays out four content columns and still shows all
     * four nav labels — identical adaptive behaviour regardless of the selected screen (DUX-1.8).
     */
    @Test
    fun expandedWindow_isUniformAcrossAllDestinations_fourColumnsAndFourLabels() {
        assertEquals(WindowWidthSizeClass.Expanded, expandedWindow.widthSizeClass)
        assertAdaptiveBehaviorUniformAcrossDestinations(
            windowSizeClass = expandedWindow,
            expectedColumns = columnsFor(WindowWidthSizeClass.Expanded), // 4
        )
    }

    /**
     * Iterates the four selected [TopDestination]s at the given [windowSizeClass] and asserts that,
     * for each, the content lambda receives [expectedColumns] and all four destination nav labels
     * are present exactly once. Proves the adaptive rules are the same across screens (DUX-1.8).
     *
     * A single `setContent` hosts a mutable `selected` state; the test flips it through all four
     * destinations, re-asserting after each change, keeping to one `setContent` per `@Test`.
     */
    private fun assertAdaptiveBehaviorUniformAcrossDestinations(
        windowSizeClass: WindowSizeClass,
        expectedColumns: Int,
    ) {
        var reportedColumns = -1
        var selected by mutableStateOf(TopDestination.HOME)

        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                AdaptiveScaffold(
                    windowSizeClass = windowSizeClass,
                    selected = selected,
                    onSelect = { selected = it },
                    foldingFeature = null,
                ) { columns, _ ->
                    reportedColumns = columns
                    Box(Modifier.fillMaxSize().testTag(CONTENT_TAG)) { Text("content") }
                }
            }
        }

        TopDestination.entries.forEach { destination ->
            composeRule.runOnUiThread { selected = destination }
            composeRule.waitForIdle()

            // The content lambda ran and reported the size-class column count, unchanged by which
            // destination is selected (DUX-1.8).
            composeRule.onNodeWithTag(CONTENT_TAG).assertExists()
            assertEquals(
                "Column count must equal columnsFor(widthClass) for destination $destination " +
                    "(DUX-1.8 uniformity)",
                expectedColumns,
                reportedColumns,
            )

            // All four destination labels are present exactly once, identically for every selected
            // destination (DUX-1.8).
            assertAllFourDestinationsPresent()
        }
    }

    // --- Back-navigation contract (predictive-back foundation, DUX-2.10) ----

    /**
     * Navigating from the start route to a second route and then calling `popBackStack()` returns
     * the NavController to the start destination. This is the back-navigation contract that
     * predictive back animates on Android 14+; the animation itself is validated at the instrumented
     * level. Uses a minimal two-route `NavHost` harness (TestNavHostController is not on the test
     * classpath).
     */
    @Test
    fun popBackStack_fromSecondRoute_returnsToStartDestination() {
        lateinit var navController: NavHostController

        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                navController = rememberNavController()
                NavHost(navController = navController, startDestination = ROUTE_DASHBOARD) {
                    composable(ROUTE_DASHBOARD) {
                        Box(Modifier.fillMaxSize().testTag(TAG_DASHBOARD)) { Text("dashboard") }
                    }
                    composable(ROUTE_GOALS) {
                        Box(Modifier.fillMaxSize().testTag(TAG_GOALS)) { Text("goals") }
                    }
                }
            }
        }
        composeRule.waitForIdle()

        // Start destination is dashboard.
        assertEquals(
            ROUTE_DASHBOARD,
            navController.currentBackStackEntry?.destination?.route,
        )

        // Navigate to the second route (GOALS).
        composeRule.runOnUiThread { navController.navigate(ROUTE_GOALS) }
        composeRule.waitForIdle()
        assertEquals(
            ROUTE_GOALS,
            navController.currentBackStackEntry?.destination?.route,
        )

        // Back navigation returns to the start destination (the predictive-back contract).
        var popped = false
        composeRule.runOnUiThread { popped = navController.popBackStack() }
        composeRule.waitForIdle()

        assertEquals("popBackStack must pop a frame off the back stack", true, popped)
        assertEquals(
            "Back navigation must return to the start destination (DUX-2.10 back contract)",
            ROUTE_DASHBOARD,
            navController.currentBackStackEntry?.destination?.route,
        )
        composeRule.onNodeWithTag(TAG_DASHBOARD).assertExists()
    }

    /**
     * Asserts each of the four [TopDestination] labels is present exactly once in the composed nav
     * affordance (bottom bar on Compact, rail on Expanded — both carry all four, DUX-1.8). Labels
     * are resolved from the same string resources the affordance uses, via the Robolectric
     * application [android.content.Context].
     */
    private fun assertAllFourDestinationsPresent() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
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
        const val CONTENT_TAG = "cross-screen-adaptive-content"
        const val ROUTE_DASHBOARD = "dashboard"
        const val ROUTE_GOALS = "goals"
        const val TAG_DASHBOARD = "harness-dashboard"
        const val TAG_GOALS = "harness-goals"
    }
}
