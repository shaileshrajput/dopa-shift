package com.dopashift.ui.screens

// Feature: android-ui-upgrade, Task 6.2

import android.content.Context
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.dopashift.ui.R
import com.dopashift.ui.state.AnchorHabitUi
import com.dopashift.ui.state.OverlayUiState
import com.dopashift.ui.state.TodoRowUi
import com.dopashift.ui.theme.DopaShiftTheme
import com.dopashift.ui.theme.ThemeMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for the Intercept Overlay screen (AUI-2, task 6.2).
 *
 * Written as **Robolectric JVM unit tests** so they run in this environment without an
 * emulator/device, matching the existing `:ui` convention (see `RuleAuthoringScreenTest` and
 * `CreationSheetContentTest`): `createComposeRule` works under [RobolectricTestRunner], and the
 * module enables `testOptions.unitTests.isIncludeAndroidResources = true` so `stringResource`
 * lookups resolve.
 *
 * Each test drives the stateless [InterceptOverlayScreen] composable directly with a controlled
 * [OverlayUiState] and an [OverlayActions] surface (using flags to observe dispatched gestures),
 * verifying the AUI-2 behavioral contracts:
 *
 *  - AUI-2.1 scrim backdrop is present.
 *  - AUI-2.2 header content (intercept pill, brain glyph description, headline, app-named body).
 *  - AUI-2.3 monospace cooldown badge shows the remaining seconds.
 *  - AUI-2.4 behavioral rescue card (day label, habit checkbox, reward prompt) when present.
 *  - AUI-2.5 up to three pending focus tasks rendered.
 *  - AUI-2.6 both action button labels present.
 *  - AUI-2.7 continue button gated strictly by cooldown (disabled until zero) — satisfies AUI-9.4.
 *  - AUI-2.8 switch button click dispatches `onSwitchToDopaShift`.
 *
 * Strings are resolved from the Robolectric application context so assertions compare against the
 * same text the composable renders.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class InterceptOverlayScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun str(resId: Int): String = context.getString(resId)

    private fun str(resId: Int, vararg args: Any): String = context.getString(resId, *args)

    private fun rescueHabit(
        completed: Boolean = false,
        dayLabel: String = "Day 6/30",
        title: String = "Read one page",
        rewardPrompt: String? = "You've got this — momentum builds.",
    ) = AnchorHabitUi(
        id = "habit-1",
        title = title,
        subtitle = "Learning",
        estimatedTimeLabel = "2m",
        dayLabel = dayLabel,
        completed = completed,
        rewardPrompt = rewardPrompt,
    )

    private fun task(id: String, text: String, done: Boolean = false) =
        TodoRowUi(id = id, text = text, done = done)

    private fun state(
        targetAppName: String = "Instagram",
        cooldownSeconds: Int = 5,
        rescueHabit: AnchorHabitUi? = null,
        pendingTasks: List<TodoRowUi> = emptyList(),
    ) = OverlayUiState(
        targetAppName = targetAppName,
        cooldownSeconds = cooldownSeconds,
        continueEnabled = cooldownSeconds == 0,
        rescueHabit = rescueHabit,
        pendingTasks = pendingTasks,
    )

    /** Renders [InterceptOverlayScreen] inside the dark [DopaShiftTheme]. */
    private fun setContent(uiState: OverlayUiState, actions: OverlayActions = OverlayActions()) {
        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                InterceptOverlayScreen(state = uiState, on = actions)
            }
        }
    }

    // --- AUI-2.1 scrim backdrop --------------------------------------------

    @Test
    fun aui_2_1_scrim_backdropIsPresent() {
        setContent(state())

        composeRule
            .onNodeWithContentDescription(str(R.string.aui_overlay_scrim_content_description))
            .assertExists()
    }

    // --- AUI-2.2 header content --------------------------------------------

    @Test
    fun aui_2_2_header_showsPillGlyphHeadlineAndAppNamedBody() {
        val appName = "Instagram"
        setContent(state(targetAppName = appName))

        // Intercept pill copy.
        composeRule.onNodeWithText(str(R.string.aui_overlay_intercept_pill)).assertExists()
        // Brain glyph content description (TalkBack).
        composeRule
            .onNodeWithContentDescription(str(R.string.aui_overlay_brain_glyph_content_description))
            .assertExists()
        // Headline.
        composeRule.onNodeWithText(str(R.string.aui_overlay_headline)).assertExists()
        // Body copy naming the target app (formatted with the app name).
        composeRule.onNodeWithText(str(R.string.aui_overlay_body, appName)).assertExists()
    }

    // --- AUI-2.3 cooldown badge --------------------------------------------

    @Test
    fun aui_2_3_cooldownBadge_showsRemainingSeconds() {
        val cooldown = 7
        setContent(state(cooldownSeconds = cooldown))

        composeRule
            .onNodeWithText(str(R.string.aui_overlay_cooldown_badge, cooldown))
            .assertExists()
    }

    // --- AUI-2.4 behavioral rescue card ------------------------------------

    @Test
    fun aui_2_4_rescueCard_showsLabelHabitCheckboxAndRewardPrompt_whenPresent() {
        val habit = rescueHabit()
        setContent(state(rescueHabit = habit))

        // Rescue label (Day N/30).
        composeRule.onNodeWithText(str(R.string.aui_overlay_rescue_label, habit.dayLabel)).assertExists()
        // The habit title (rendered on the MomentumCheckbox, which exposes a single aggregate
        // content description "<label>, not completed" and clears its inner text semantics).
        composeRule.onNodeWithContentDescription("${habit.title}, not completed").assertExists()
        // The reward-prompt line.
        composeRule.onNodeWithText(habit.rewardPrompt!!).assertExists()
    }

    @Test
    fun aui_2_4_rescueCard_absent_whenNoRescueHabit() {
        setContent(state(rescueHabit = null))

        // With no rescue habit, no rescue label is rendered.
        composeRule.onNodeWithText(str(R.string.aui_overlay_rescue_label, "Day 6/30")).assertDoesNotExist()
    }

    // --- AUI-2.5 pending focus tasks ---------------------------------------

    @Test
    fun aui_2_5_pendingTasks_rendersAtMostThree() {
        // Pass more than three tasks; only the first three may be rendered.
        val tasks = listOf(
            task("t1", "Draft outline"),
            task("t2", "Reply to email"),
            task("t3", "Stretch break"),
            task("t4", "Should not render"),
        )
        setContent(state(pendingTasks = tasks))

        // Section title present.
        composeRule.onNodeWithText(str(R.string.aui_overlay_pending_title)).assertExists()
        // Each task renders on a MomentumCheckbox, which exposes an aggregate content description
        // "<text>, not completed" (its inner text semantics are cleared). The first three render.
        composeRule.onNodeWithContentDescription("Draft outline, not completed").assertExists()
        composeRule.onNodeWithContentDescription("Reply to email, not completed").assertExists()
        composeRule.onNodeWithContentDescription("Stretch break, not completed").assertExists()
        // The fourth task is not rendered (capped at three).
        composeRule.onNodeWithContentDescription("Should not render, not completed").assertDoesNotExist()
    }

    // --- AUI-2.6 action button labels --------------------------------------

    @Test
    fun aui_2_6_bothActionButtonLabelsPresent() {
        // Non-zero cooldown so the continue button shows its countdown label.
        val cooldown = 4
        setContent(state(cooldownSeconds = cooldown))

        composeRule.onNodeWithText(str(R.string.aui_overlay_switch)).assertExists()
        composeRule.onNodeWithText(str(R.string.aui_overlay_continue, cooldown)).assertExists()
    }

    // --- AUI-2.7 continue gating (satisfies AUI-9.4) -----------------------

    @Test
    fun aui_2_7_continueButton_disabled_whenCooldownRemaining() {
        setContent(state(cooldownSeconds = 5))

        // While the cooldown is non-zero the continue button is NOT enabled.
        composeRule.onNodeWithText(str(R.string.aui_overlay_continue, 5)).assertIsNotEnabled()
    }

    @Test
    fun aui_2_7_continueButton_enabled_whenCooldownZero() {
        setContent(state(cooldownSeconds = 0))

        // With the cooldown elapsed the button drops the counter and is enabled.
        composeRule.onNodeWithText(str(R.string.aui_overlay_continue_ready)).assertIsEnabled()
    }

    // --- AUI-2.8 switch action ---------------------------------------------

    @Test
    fun aui_2_8_switchButton_click_invokesOnSwitchToDopaShift() {
        var switched = false
        var continued = false
        setContent(
            state(cooldownSeconds = 5),
            OverlayActions(
                onSwitchToDopaShift = { switched = true },
                onContinueToApp = { continued = true },
            ),
        )

        composeRule.onNodeWithText(str(R.string.aui_overlay_switch)).performClick()

        assertTrue("tapping Switch to DopaShift must invoke onSwitchToDopaShift", switched)
        assertFalse("the switch button must not invoke the continue action", continued)
    }
}
