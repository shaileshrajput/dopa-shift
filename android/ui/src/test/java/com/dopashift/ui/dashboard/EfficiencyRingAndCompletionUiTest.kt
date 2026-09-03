package com.dopashift.ui.dashboard

// Feature: dynamic-ui-experience, Task 11.5
// Validates: Requirements DUX-3.5, DUX-3.6, DUX-2.3, DUX-2.5, DUX-2.11

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ApplicationProvider
import com.dopashift.ui.R
import com.dopashift.ui.theme.DopaShiftTheme
import com.dopashift.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for the Dashboard's [EfficiencyRing] and [CompletionCheckbox]
 * completion-feedback control (DUX-3.5, DUX-3.6, DUX-2.3, DUX-2.5, DUX-2.11).
 *
 * These run as Compose UI tests under [RobolectricTestRunner] so they execute on the
 * JVM without an emulator (`createComposeRule` works under Robolectric), matching the
 * pattern established by `SkeletonAndSyncIndicatorUiTest` and the adaptive-layout tests.
 * The `:ui` module enables `testOptions.unitTests.isIncludeAndroidResources = true`, so
 * `stringResource` lookups inside the composables resolve against the packaged
 * `values/strings.xml`, and the tests read the same resolved strings from the Robolectric
 * [Context] to assert against the exposed content descriptions.
 *
 * Composables under test are wrapped in `DopaShiftTheme(themeMode = ThemeMode.DARK)`
 * because both reference `MaterialTheme.colorScheme` / typography tokens.
 *
 * Efficiency ring coverage:
 *  - `score = null` exposes the "Efficiency: no data" content description and never a
 *    scored/0% description (DUX-3.6).
 *  - a non-null score exposes "Efficiency N percent" (DUX-3.5, DUX-2.5), including after
 *    the one-shot animate-once sweep settles.
 *
 * Completion-feedback coverage (DUX-2.3):
 *  - completed vs not-completed expose distinct "..., completed" / "..., not completed"
 *    content descriptions (non-color state, state stated in words).
 *  - toggling `isComplete` false -> true preserves the completed semantics.
 *  - Haptics cannot be observed under Robolectric, so the state/semantics outcome of the
 *    completion transition is asserted rather than the haptic call itself (DUX-2.11).
 *
 * The ring collapses its children into a single semantics node via `clearAndSetSemantics`,
 * so its state is asserted through the exposed content description (the visible "No data" /
 * "N%" label text is intentionally hidden from the a11y tree). `assertExists` is used for
 * the ring's merged node since its only visible child is a `Canvas`, which Robolectric's
 * layout pass does not report as "displayed"; the completion checkbox has real icon bounds
 * and is asserted with `assertIsDisplayed`.
 *
 * Robolectric constraint: at most ONE `composeRule.setContent { ... }` per `@Test`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EfficiencyRingAndCompletionUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun str(resId: Int): String = context.getString(resId)
    private fun str(resId: Int, vararg args: Any): String = context.getString(resId, *args)

    private companion object {
        const val SAMPLE_SCORE = 73
        const val TASK_LABEL = "Finish reading chapter"
    }

    // =================================================================================
    // EfficiencyRing — "No data" state (DUX-3.6)
    // =================================================================================

    @Test
    fun efficiencyRing_nullScore_exposesNoDataDescription() {
        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                EfficiencyRing(score = null, animateOnce = false)
            }
        }

        // A null score renders an explicit "No data" state, never a misleading 0% (DUX-3.6).
        val noDataDescription = str(R.string.dux_efficiency_no_data_content_description)
        composeRule.onNodeWithContentDescription(noDataDescription).assertExists()

        // And it must NOT expose a scored "Efficiency N percent" description for any value
        // (in particular, never a misleading 0%).
        val zeroScoreDescription = str(R.string.dux_efficiency_content_description, 0)
        composeRule.onNodeWithContentDescription(zeroScoreDescription).assertDoesNotExist()
    }

    // =================================================================================
    // EfficiencyRing — scored state (DUX-3.5, DUX-2.5)
    // =================================================================================

    @Test
    fun efficiencyRing_score73_exposesPercentDescription() {
        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                // animateOnce = false so the ring snaps to the final value; the final
                // rendered state (73%) is what we assert, not the animation itself.
                EfficiencyRing(score = SAMPLE_SCORE, animateOnce = false)
            }
        }

        // A scored ring exposes the "Efficiency 73 percent" content description (DUX-3.5,
        // DUX-2.5).
        val scoredDescription = str(R.string.dux_efficiency_content_description, SAMPLE_SCORE)
        composeRule.onNodeWithContentDescription(scoredDescription).assertExists()

        // The scored ring must not fall back to the "No data" description.
        val noDataDescription = str(R.string.dux_efficiency_no_data_content_description)
        composeRule.onNodeWithContentDescription(noDataDescription).assertDoesNotExist()
    }

    @Test
    fun efficiencyRing_animateOnce_stillReportsFinalValue() {
        // Even when the one-shot 0->value sweep is enabled (DUX-2.5), once the animation
        // settles the ring must report the true final percentage, not an intermediate or 0%.
        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                EfficiencyRing(score = SAMPLE_SCORE, animateOnce = true)
            }
        }

        composeRule.waitForIdle()

        val scoredDescription = str(R.string.dux_efficiency_content_description, SAMPLE_SCORE)
        composeRule.onNodeWithContentDescription(scoredDescription).assertExists()

        // Never the "No data" description once a real score is present.
        val noDataDescription = str(R.string.dux_efficiency_no_data_content_description)
        composeRule.onNodeWithContentDescription(noDataDescription).assertDoesNotExist()
    }

    // =================================================================================
    // CompletionCheckbox — distinct completed / not-completed semantics (DUX-2.3)
    // =================================================================================

    @Test
    fun completionCheckbox_notComplete_exposesNotCompletedDescription() {
        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                CompletionCheckbox(isComplete = false, contentDescription = TASK_LABEL)
            }
        }

        // An incomplete item states its state in words (non-color state, DUX-2.3).
        composeRule.onNodeWithContentDescription("$TASK_LABEL, not completed").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("$TASK_LABEL, completed").assertDoesNotExist()
    }

    @Test
    fun completionCheckbox_complete_exposesCompletedDescription() {
        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                CompletionCheckbox(isComplete = true, contentDescription = TASK_LABEL)
            }
        }

        composeRule.onNodeWithContentDescription("$TASK_LABEL, completed").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("$TASK_LABEL, not completed").assertDoesNotExist()
    }

    @Test
    fun completionCheckbox_togglingToComplete_preservesCompletedSemantics() {
        // Drive the completion from a mutable state within a single setContent so the
        // false -> true completion transition (which fires the animation + haptic on a
        // real device) is exercised. Haptics can't be observed under Robolectric, so we
        // assert the resulting completed state/semantics is preserved (DUX-2.3, DUX-2.11).
        val complete = mutableStateOf(false)
        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                CompletionCheckbox(isComplete = complete.value, contentDescription = TASK_LABEL)
            }
        }

        // Initially not completed.
        composeRule.onNodeWithContentDescription("$TASK_LABEL, not completed").assertIsDisplayed()

        // Item is marked complete.
        complete.value = true
        composeRule.waitForIdle()

        // The completed semantics is exposed and preserved after the transition settles.
        composeRule.onNodeWithContentDescription("$TASK_LABEL, completed").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("$TASK_LABEL, not completed").assertDoesNotExist()
    }
}
