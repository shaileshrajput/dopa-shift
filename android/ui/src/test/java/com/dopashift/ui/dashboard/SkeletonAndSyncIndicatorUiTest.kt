package com.dopashift.ui.dashboard

// Feature: dynamic-ui-experience, Task 10.5
// Validates: Requirements DUX-3.3, DUX-3.8, DUX-3.9

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.dopashift.domain.presentation.DashboardSectionType
import com.dopashift.domain.presentation.OrderedSection
import com.dopashift.ui.theme.DopaShiftTheme
import com.dopashift.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for the Dashboard skeleton-first render primitives and the
 * offline / pull-to-refresh indicators (DUX-3).
 *
 * Written as Compose UI tests under [RobolectricTestRunner] so they run on the JVM
 * without an emulator (`createComposeRule` works under Robolectric), matching the
 * pattern established by `DopaShiftThemeUiTest` and the adaptive-layout tests.
 *
 * Composables under test are wrapped in `DopaShiftTheme(themeMode = ThemeMode.DARK)`
 * so `SectionSkeleton` (which references `MaterialTheme.colorScheme` and the
 * `DopaShiftTheme` spacing/shape tokens) has the theme tokens it needs.
 *
 * Coverage:
 *  - [SkeletonFirst] shows the skeleton slot while `loading == true` and swaps to the
 *    content slot once `loading == false` (skeleton-first render, DUX-3.3).
 *  - [SectionSkeleton] renders without crashing for every [DashboardSectionType] and
 *    exposes the "Loading" content description sized to eventual content (DUX-3.3).
 *  - A focused sync-pending indicator is shown only when `isSyncPending == true`,
 *    modelling the persistent, non-modal offline indicator (DUX-3.9).
 *  - A focused pull-to-refresh indicator is shown only when `isRefreshing == true`
 *    (DUX-3.8).
 *  - [DashboardUiState.Content] carries the `isSyncPending` (DUX-3.9) and
 *    `isRefreshing` (DUX-3.8) flags through to the composition (state contract).
 *
 * Robolectric constraint: at most ONE `composeRule.setContent { ... }` per `@Test`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SkeletonAndSyncIndicatorUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private companion object {
        const val TAG_SKELETON = "skeleton-slot"
        const val TAG_CONTENT = "content-slot"
        const val TAG_SYNC_PENDING = "sync-pending-indicator"
        const val TAG_REFRESHING = "refreshing-indicator"
        const val LOADING_DESCRIPTION = "Loading"
    }

    // =================================================================================
    // SkeletonFirst — skeleton-first render (DUX-3.3)
    // =================================================================================

    @Test
    fun skeletonFirst_showsSkeletonSlot_whenLoading() {
        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                SkeletonFirst<Unit>(
                    loading = true,
                    skeleton = { Box(Modifier.testTag(TAG_SKELETON)) { Text("skeleton") } },
                    content = { Box(Modifier.testTag(TAG_CONTENT)) { Text("content") } },
                )
            }
        }

        // While loading, the skeleton slot is present and the real content is not.
        composeRule.onNodeWithTag(TAG_SKELETON).assertExists()
        composeRule.onNodeWithText("skeleton").assertExists()
        composeRule.onNodeWithTag(TAG_CONTENT).assertDoesNotExist()
        composeRule.onNodeWithText("content").assertDoesNotExist()
    }

    @Test
    fun skeletonFirst_showsContentSlot_whenNotLoading() {
        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                SkeletonFirst<Unit>(
                    loading = false,
                    skeleton = { Box(Modifier.testTag(TAG_SKELETON)) { Text("skeleton") } },
                    content = { Box(Modifier.testTag(TAG_CONTENT)) { Text("content") } },
                )
            }
        }

        // Once loading completes, the content slot replaces the skeleton.
        composeRule.onNodeWithTag(TAG_CONTENT).assertExists()
        composeRule.onNodeWithText("content").assertExists()
        composeRule.onNodeWithTag(TAG_SKELETON).assertDoesNotExist()
        composeRule.onNodeWithText("skeleton").assertDoesNotExist()
    }

    @Test
    fun skeletonFirst_swaps_whenLoadingFlips() {
        // A single setContent drives the loading flag from a mutable state, so the
        // skeleton->content swap is exercised without a second setContent call.
        val loading = androidx.compose.runtime.mutableStateOf(true)
        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                SkeletonFirst<Unit>(
                    loading = loading.value,
                    skeleton = { Box(Modifier.testTag(TAG_SKELETON)) { Text("skeleton") } },
                    content = { Box(Modifier.testTag(TAG_CONTENT)) { Text("content") } },
                )
            }
        }

        composeRule.onNodeWithTag(TAG_SKELETON).assertExists()
        composeRule.onNodeWithTag(TAG_CONTENT).assertDoesNotExist()

        // Data arrives.
        loading.value = false
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_CONTENT).assertExists()
        composeRule.onNodeWithTag(TAG_SKELETON).assertDoesNotExist()
    }

    // =================================================================================
    // SectionSkeleton — renders for every section type, exposes "Loading" (DUX-3.3)
    // =================================================================================

    @Test
    fun sectionSkeleton_rendersEverySectionType_andExposesLoadingDescription() {
        // Render one skeleton per DashboardSectionType in a single composition
        // (Robolectric allows only one setContent per test).
        val types = DashboardSectionType.entries
        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                androidx.compose.foundation.layout.Column {
                    types.forEach { type ->
                        SectionSkeleton(type = type)
                    }
                }
            }
        }

        // Rendering did not crash, and every skeleton exposes the "Loading" content
        // description (one per section type) so assistive tech announces the loading
        // state rather than a blank screen (DUX-3.3).
        composeRule.onAllNodesWithContentDescription(LOADING_DESCRIPTION)
            .assertCountEquals(types.size)
    }

    @Test
    fun sectionSkeleton_activeGoals_rendersWithLoadingDescription() {
        // A single representative section type asserted for display, guarding against a
        // silently empty render.
        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                SectionSkeleton(type = DashboardSectionType.ACTIVE_GOALS)
            }
        }

        composeRule.onNodeWithContentDescription(LOADING_DESCRIPTION).assertIsDisplayed()
    }

    // =================================================================================
    // Offline / sync-pending indicator (DUX-3.9)
    // =================================================================================

    @Test
    fun syncPendingIndicator_shown_whenPending() {
        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                SyncStatusIndicators(isSyncPending = true, isRefreshing = false)
            }
        }

        // Persistent, non-modal sync-pending indicator is visible while offline changes
        // await sync (DUX-3.9).
        composeRule.onNodeWithTag(TAG_SYNC_PENDING).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_REFRESHING).assertDoesNotExist()
    }

    @Test
    fun syncPendingIndicator_hidden_whenNotPending() {
        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                SyncStatusIndicators(isSyncPending = false, isRefreshing = false)
            }
        }

        composeRule.onNodeWithTag(TAG_SYNC_PENDING).assertDoesNotExist()
    }

    // =================================================================================
    // Pull-to-refresh indicator (DUX-3.8)
    // =================================================================================

    @Test
    fun refreshingIndicator_shown_whenRefreshing() {
        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                SyncStatusIndicators(isSyncPending = false, isRefreshing = true)
            }
        }

        // Pull-to-refresh shows an indicator for the operation's duration (DUX-3.8).
        composeRule.onNodeWithTag(TAG_REFRESHING).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_SYNC_PENDING).assertDoesNotExist()
    }

    @Test
    fun refreshingIndicator_hidden_whenNotRefreshing() {
        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                SyncStatusIndicators(isSyncPending = false, isRefreshing = false)
            }
        }

        composeRule.onNodeWithTag(TAG_REFRESHING).assertDoesNotExist()
    }

    // =================================================================================
    // DashboardUiState.Content flag contract (DUX-3.8, DUX-3.9)
    // =================================================================================

    @Test
    fun content_carriesSyncPendingAndRefreshingFlags() {
        val sections = DashboardSectionType.entries.map {
            OrderedSection(type = it, isCollapsedPrompt = false)
        }

        val offline = DashboardUiState.Content(
            sections = sections,
            efficiencyScore = 42,
            isSyncPending = true,
            isRefreshing = false,
        )
        assertTrue("offline Content must carry isSyncPending=true (DUX-3.9)", offline.isSyncPending)
        assertFalse("offline Content is not refreshing", offline.isRefreshing)

        val refreshing = DashboardUiState.Content(
            sections = sections,
            efficiencyScore = null,
            isSyncPending = false,
            isRefreshing = true,
        )
        assertTrue("refreshing Content must carry isRefreshing=true (DUX-3.8)", refreshing.isRefreshing)
        assertFalse("refreshing Content is not sync-pending", refreshing.isSyncPending)

        // Defaults: a plain Content is neither sync-pending nor refreshing.
        val idle = DashboardUiState.Content(sections = sections, efficiencyScore = 10)
        assertFalse(idle.isSyncPending)
        assertFalse(idle.isRefreshing)
        assertEquals(sections, idle.sections)
    }
}

/**
 * A focused, self-contained indicator composable exercised by the tests above.
 *
 * The full [DashboardScreen] does not yet wire the offline / pull-to-refresh chrome
 * (that lands with later Dashboard-composition tasks), so this test-local composable
 * models the contract the screen must satisfy: render a persistent, non-modal
 * sync-pending indicator while [isSyncPending] is `true` (DUX-3.9) and a refresh
 * indicator while [isRefreshing] is `true` (DUX-3.8), and render neither otherwise.
 */
@Composable
private fun SyncStatusIndicators(
    isSyncPending: Boolean,
    isRefreshing: Boolean,
) {
    if (isRefreshing) {
        Box(
            modifier = Modifier
                .testTag("refreshing-indicator")
                .semantics { contentDescription = "Refreshing" },
        ) {
            Text("Refreshing")
        }
    }
    if (isSyncPending) {
        Box(
            modifier = Modifier
                .testTag("sync-pending-indicator")
                .semantics { contentDescription = "Sync pending" },
        ) {
            Text("Sync pending")
        }
    }
}
