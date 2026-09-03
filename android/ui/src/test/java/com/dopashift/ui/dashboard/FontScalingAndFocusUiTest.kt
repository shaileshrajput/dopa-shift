package com.dopashift.ui.dashboard

// Feature: dynamic-ui-experience, Task 16.3
// Validates: Requirements DUX-5.5, DUX-5.6

import android.content.Context
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.dopashift.domain.entity.GoalProfile
import com.dopashift.ui.R
import com.dopashift.ui.theme.DopaShiftTheme
import com.dopashift.ui.theme.ThemeMode
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

/**
 * Compose UI tests for large-font-scale robustness and keyboard/D-pad focus of the
 * dynamic-ui-experience dashboard composables (DUX-5.5, DUX-5.6).
 *
 * These run as Compose UI tests under [RobolectricTestRunner] so they execute on the JVM
 * without an emulator (`createComposeRule` works under Robolectric), matching the pattern
 * established by [EfficiencyRingAndCompletionUiTest] and [Property8And11GoalCardSemanticsTest].
 * All composables are wrapped in `DopaShiftTheme(themeMode = ThemeMode.DARK)`; there is at most
 * one `composeRule.setContent { ... }` per `@Test` (Robolectric constraint).
 *
 * Font scaling (DUX-5.5): a representative dashboard composable ([GoalCard] with a
 * [GoalProgressSummary], and the [EfficiencyRing]) is composed inside a
 * `CompositionLocalProvider(LocalDensity provides Density(density, fontScale = ...))` so the
 * layout pass runs with an enlarged font scale. At both `fontScale = 1.0f` and `fontScale = 2.0f`
 * the composable still composes (no crash) and its key text / semantics remain exposed — the
 * goal-card content description and the ring's "73%" / "No data" descriptions. To show text
 * grows/wraps rather than clipping, the goal card's measured height at 2.0x is asserted to be at
 * least its 1.0x height (captured via `onGloballyPositioned`).
 *
 * Keyboard / D-pad focus (DUX-5.6): a couple of focusable interactive controls (two Buttons and
 * a clickable [GoalCard]) are tagged with testTags; the tests assert a control can receive focus
 * and that focus can move to another control, which also exercises focus traversal between
 * focusable elements. Under Robolectric real key-event injection is unreliable, so focus is
 * requested via the node's `SemanticsActions.RequestFocus` action and asserted on the tagged
 * nodes rather than dispatching D-pad key events.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FontScalingAndFocusUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun str(resId: Int): String = context.getString(resId)
    private fun str(resId: Int, vararg args: Any): String = context.getString(resId, *args)

    private companion object {
        const val SAMPLE_SCORE = 73
        const val LARGE_FONT_SCALE = 2.0f
        const val NORMAL_FONT_SCALE = 1.0f
        const val BASE_DENSITY = 2.75f // typical xhdpi-ish density; the value itself is not asserted

        const val TAG_BUTTON_A = "focus-button-a"
        const val TAG_BUTTON_B = "focus-button-b"
        const val TAG_GOAL_CARD = "focus-goal-card"
    }

    private fun sampleSummary(name: String = "Run a 10k", percent: Int = 75): GoalProgressSummary {
        // completed/total chosen so progressPercent == the requested percent for readability.
        val total = 4
        val completed = (percent * total) / 100
        return GoalProgressSummary(
            goal = GoalProfile(
                id = UUID.randomUUID(),
                userId = UUID.randomUUID(),
                name = name,
                category = "Fitness",
                keywords = emptyList(),
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                isActive = true,
            ),
            completedItems = completed,
            totalItems = total,
            streakDays = 5,
        )
    }

    /** Matches any semantics node that carries a (non-null) content description. */
    private fun hasContentDescription(): SemanticsMatcher =
        SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)

    /**
     * Requests focus on [node] via its `RequestFocus` semantics action. The
     * `SemanticsNodeInteraction.requestFocus()` convenience extension is not available in the
     * Compose version pinned by this module, so the underlying semantics action is invoked
     * directly (the same mechanism it delegates to).
     */
    private fun requestFocusOn(node: SemanticsNodeInteraction) {
        node.performSemanticsAction(SemanticsActions.RequestFocus)
    }

    // =================================================================================
    // Font scaling (DUX-5.5)
    // =================================================================================

    @Test
    fun goalCard_atNormalFontScale_composesAndExposesContentDescription() {
        val summary = sampleSummary(name = "Run a 10k", percent = 75)

        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                CompositionLocalProvider(
                    LocalDensity provides Density(density = BASE_DENSITY, fontScale = NORMAL_FONT_SCALE),
                ) {
                    GoalCard(summary = summary)
                }
            }
        }

        // Content is still exposed at the baseline font scale (no crash, semantics present).
        val expected = str(R.string.dux_goal_content_description, summary.goal.name, summary.progressPercent)
        val description = composeRule
            .onAllDescriptions()
            .firstOrNull { it.contains(summary.goal.name) }
        assertNotNull(
            "Goal card must expose a content description naming the goal at 1.0x font scale (DUX-5.5)",
            description,
        )
        assertTrue(
            "Goal card content description must include the goal name at 1.0x (DUX-5.5). Was: $description",
            description!!.contains(summary.goal.name),
        )
        assertTrue(
            "Goal card content description must include progress percent at 1.0x (DUX-5.5). " +
                "Expected token '${summary.progressPercent}%'. Was: $description",
            description.contains("${summary.progressPercent}%"),
        )
        // Sanity: the fully-formatted description resolves without throwing.
        assertTrue(expected.contains(summary.goal.name))
    }

    @Test
    fun goalCard_atLargeFontScale_stillComposesAndExposesContentDescription() {
        val summary = sampleSummary(name = "Run a 10k", percent = 75)

        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                CompositionLocalProvider(
                    LocalDensity provides Density(density = BASE_DENSITY, fontScale = LARGE_FONT_SCALE),
                ) {
                    GoalCard(summary = summary)
                }
            }
        }

        // At 2.0x the card must still compose and keep exposing its goal name + progress
        // semantics — content is not lost or clipped out of the a11y tree (DUX-5.5).
        val description = composeRule
            .onAllDescriptions()
            .firstOrNull { it.contains(summary.goal.name) }
        assertNotNull(
            "Goal card must still expose a content description naming the goal at 2.0x font scale (DUX-5.5)",
            description,
        )
        assertTrue(
            "Goal card content description must still include the numeric progress at 2.0x (DUX-5.5). " +
                "Was: $description",
            description!!.contains("${summary.progressPercent}%"),
        )
    }

    @Test
    fun goalCard_heightGrowsOrHolds_fromNormalToLargeFontScale_noClipping() {
        // Render the SAME card at 1.0x then flip LocalDensity to 2.0x within a single
        // composition (Robolectric: one setContent per test). Capture the card's measured
        // height at each scale via onGloballyPositioned. With larger text the card must grow
        // (text wraps / lines get taller) rather than clip — so height(2.0x) >= height(1.0x).
        val summary = sampleSummary(name = "Marathon training plan for autumn season", percent = 50)
        val fontScale = mutableStateOf(NORMAL_FONT_SCALE)
        var normalHeight = 0
        var largeHeight = 0

        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                CompositionLocalProvider(
                    LocalDensity provides Density(density = BASE_DENSITY, fontScale = fontScale.value),
                ) {
                    GoalCard(
                        summary = summary,
                        modifier = Modifier.onGloballyPositioned { coords ->
                            if (fontScale.value == NORMAL_FONT_SCALE) {
                                normalHeight = coords.size.height
                            } else {
                                largeHeight = coords.size.height
                            }
                        },
                    )
                }
            }
        }

        composeRule.waitForIdle()
        val capturedNormal = normalHeight
        assertTrue("Card must have a measured height at 1.0x font scale", capturedNormal > 0)

        // Increase the font scale to 2.0x and re-measure.
        fontScale.value = LARGE_FONT_SCALE
        composeRule.waitForIdle()

        assertTrue("Card must have a measured height at 2.0x font scale", largeHeight > 0)
        // Larger text should never shrink the card's layout height (would imply clipping).
        assertTrue(
            "Goal card height must grow or hold from 1.0x ($capturedNormal px) to 2.0x " +
                "($largeHeight px) font scale — text should wrap, not clip (DUX-5.5)",
            largeHeight >= capturedNormal,
        )
    }

    @Test
    fun efficiencyRing_scored_atLargeFontScale_stillExposesPercentDescription() {
        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                CompositionLocalProvider(
                    LocalDensity provides Density(density = BASE_DENSITY, fontScale = LARGE_FONT_SCALE),
                ) {
                    EfficiencyRing(score = SAMPLE_SCORE, animateOnce = false)
                }
            }
        }

        // The "73%" ring must still report its scored content description at 2.0x (DUX-5.5).
        val scoredDescription = str(R.string.dux_efficiency_content_description, SAMPLE_SCORE)
        composeRule.onNodeWithContentDescription(scoredDescription).assertExists()
    }

    @Test
    fun efficiencyRing_noData_atLargeFontScale_stillExposesNoDataDescription() {
        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                CompositionLocalProvider(
                    LocalDensity provides Density(density = BASE_DENSITY, fontScale = LARGE_FONT_SCALE),
                ) {
                    EfficiencyRing(score = null, animateOnce = false)
                }
            }
        }

        // The "No data" ring must still report its content description at 2.0x (DUX-5.5).
        val noDataDescription = str(R.string.dux_efficiency_no_data_content_description)
        composeRule.onNodeWithContentDescription(noDataDescription).assertExists()
    }

    // =================================================================================
    // Keyboard / D-pad focus (DUX-5.6)
    // =================================================================================

    @Test
    fun interactiveButton_canReceiveFocus() {
        val requester = FocusRequester()
        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                Button(
                    onClick = {},
                    modifier = Modifier
                        .testTag(TAG_BUTTON_A)
                        .focusRequester(requester),
                ) {
                    Text("Action A")
                }
            }
        }

        // An interactive control must be reachable via focus traversal (keyboard / D-pad).
        val node = composeRule.onNodeWithTag(TAG_BUTTON_A)
        node.assertIsDisplayed()
        requestFocusOn(node)
        node.assertIsFocused()
    }

    @Test
    fun focusCanMoveBetweenTwoInteractiveControls() {
        val requesterA = FocusRequester()
        val requesterB = FocusRequester()
        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                Column {
                    Button(
                        onClick = {},
                        modifier = Modifier
                            .testTag(TAG_BUTTON_A)
                            .focusRequester(requesterA),
                    ) {
                        Text("Action A")
                    }
                    Button(
                        onClick = {},
                        modifier = Modifier
                            .testTag(TAG_BUTTON_B)
                            .focusRequester(requesterB),
                    ) {
                        Text("Action B")
                    }
                }
            }
        }

        val a = composeRule.onNodeWithTag(TAG_BUTTON_A)
        val b = composeRule.onNodeWithTag(TAG_BUTTON_B)

        // Focus the first control.
        requestFocusOn(a)
        a.assertIsFocused()
        b.assertIsNotFocused()

        // Move focus to the second control (focus traversal between focusable elements).
        requestFocusOn(b)
        b.assertIsFocused()
        a.assertIsNotFocused()
    }

    @Test
    fun clickableGoalCard_andButton_areBothFocusable_andCardCanBeFocused() {
        val summary = sampleSummary(name = "Run a 10k", percent = 75)
        val cardRequester = FocusRequester()
        val buttonRequester = FocusRequester()

        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                Column {
                    GoalCard(
                        summary = summary,
                        onClick = {},
                        modifier = Modifier
                            .testTag(TAG_GOAL_CARD)
                            .focusRequester(cardRequester),
                    )
                    Button(
                        onClick = {},
                        modifier = Modifier
                            .testTag(TAG_BUTTON_A)
                            .focusRequester(buttonRequester),
                    ) {
                        Text("Action A")
                    }
                }
            }
        }

        val card = composeRule.onNodeWithTag(TAG_GOAL_CARD)
        val button = composeRule.onNodeWithTag(TAG_BUTTON_A)

        // Both interactive controls must exist and be laid out (reachable in the tree).
        card.assertIsDisplayed()
        button.assertIsDisplayed()

        // The clickable goal card collapses its content into a single content-description
        // semantics node via clearAndSetSemantics, so the merged node does not surface the
        // Focused state (or a RequestFocus action). Focusability is instead proven by requesting
        // focus through the card's FocusRequester: the request succeeds only because a focus
        // target exists for the card, and it is confirmed by observing focus LEAVE the button.
        composeRule.runOnUiThread { buttonRequester.requestFocus() }
        composeRule.waitForIdle()
        button.assertIsFocused()

        // Moving focus to the clickable card must pull it off the button — this only happens if
        // the card is itself a reachable focus target (D-pad / keyboard traversal, DUX-5.6).
        composeRule.runOnUiThread { cardRequester.requestFocus() }
        composeRule.waitForIdle()
        button.assertIsNotFocused()

        // And focus can move back to the button — traversal across interactive controls (DUX-5.6).
        composeRule.runOnUiThread { buttonRequester.requestFocus() }
        composeRule.waitForIdle()
        button.assertIsFocused()
    }

    @Test
    fun explicitlyFocusableNonClickableElement_canReceiveFocus() {
        // DUX-5.6 requires every focusable element be reachable; verify a plain element made
        // focusable(Modifier.focusable) participates in focus traversal.
        val requester = FocusRequester()
        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                Text(
                    text = "Focusable label",
                    modifier = Modifier
                        .testTag(TAG_BUTTON_B)
                        .focusRequester(requester)
                        .focusable(),
                )
            }
        }

        val node = composeRule.onNodeWithTag(TAG_BUTTON_B)
        requestFocusOn(node)
        node.assertIsFocused()
    }

    // ---------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------

    /** Collects every content description currently exposed in the semantics tree. */
    private fun ComposeContentTestRule.onAllDescriptions(): List<String> =
        onAllNodes(hasContentDescription())
            .fetchSemanticsNodes()
            .mapNotNull { it.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ") }
}
