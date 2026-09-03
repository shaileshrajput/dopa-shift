// Feature: android-ui-upgrade, Task 5.2
// Compose UI tests for the Home Dashboard screen (AUI-1). Written as Robolectric JVM Compose tests
// (createComposeRule works under RobolectricTestRunner; the :ui module enables
// testOptions.unitTests.isIncludeAndroidResources so stringResource lookups resolve), matching the
// existing RuleAuthoringScreenTest / DashboardRenderingsUiTest conventions in this module.
//
// The Dashboard is a stateless renderer of DashboardUiState; every gesture is dispatched through
// the DashboardActions interface. These tests drive DashboardScreen(state, on) with a controlled
// state and a recording DashboardActions, wrapped in DopaShiftTheme(themeMode = ThemeMode.DARK).
//
// Validates:
//   AUI-1.1 — header efficiency ring content description reflects the integer percent; the streak
//             pill / glyph is displayed (satisfies AUI-9.2).
//   AUI-1.2 — anchor card shows the habit title when present; the start-a-habit prompt when null.
//   AUI-1.3 — toggling the anchor MomentumCheckbox invokes onAnchorHabitToggled(true); the checked
//             row is a toggleable node in the ON state (satisfies AUI-9.3). The MomentumCheckbox
//             structurally fires HapticFeedbackConstants.CONFIRM + a <=100ms strikethrough on
//             toggle; the haptic itself is not assertable under Robolectric and the strikethrough
//             is not separately observable because the control aggregates its children under a
//             single semantics node, so we assert the toggle callback fires and the control is a
//             toggleable node whose checked state tracks completion.
//   AUI-1.4 — Today's Focus title + each focus row text is displayed; quick-add invokes
//             onQuickAddTodo when text is entered and Add is tapped.
//   AUI-1.6 — each active goal's title + category is displayed; tapping a goal card invokes
//             onGoalClicked(id).
//   AUI-1.7 — the QuickCreateFab is displayed and clicking invokes onQuickCreate.
//   AUI-1.8 — the bottom nav is rendered with Home active.
package com.dopashift.ui.screens

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.dopashift.ui.R
import com.dopashift.ui.adaptive.TopDestination
import com.dopashift.ui.state.AnchorHabitUi
import com.dopashift.ui.state.DashboardUiState
import com.dopashift.ui.state.GoalCardUi
import com.dopashift.ui.state.TodoRowUi
import com.dopashift.ui.theme.DopaShiftTheme
import com.dopashift.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// A tall viewport qualifier so the whole Dashboard LazyVerticalGrid (header card, anchor card,
// Today's Focus card, the goals heading, and the 2-column goal grid) lays out with room to scroll;
// the default Robolectric device viewport is too short, so below-the-fold items (Today's Focus and
// the goal cards) are never composed and cannot be found/clicked. Below-the-fold nodes are scrolled
// into composition with performScrollToNode before asserting/interacting, mirroring
// SettingsScreenTest.
@Config(sdk = [33], qualifiers = "w411dp-h2000dp")
class DashboardScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun str(resId: Int): String = context.getString(resId)

    private fun str(resId: Int, vararg args: Any): String = context.getString(resId, *args)

    /**
     * A recording [DashboardActions] that captures every dispatched gesture so assertions can
     * verify the correct callback fired with the correct payload (and no other).
     */
    private class RecordingActions : DashboardActions {
        var anchorToggledTo: Boolean? = null
        val quickAddTexts = mutableListOf<String>()
        val todoToggles = mutableListOf<Pair<String, Boolean>>()
        val goalClicks = mutableListOf<String>()
        var quickCreateCount = 0
        val navTargets = mutableListOf<TopDestination>()

        override fun onAnchorHabitToggled(checked: Boolean) {
            anchorToggledTo = checked
        }

        override fun onQuickAddTodo(text: String) {
            quickAddTexts += text
        }

        override fun onTodoToggled(id: String, done: Boolean) {
            todoToggles += id to done
        }

        override fun onGoalClicked(id: String) {
            goalClicks += id
        }

        override fun onQuickCreate() {
            quickCreateCount++
        }

        override fun onNavigate(destination: TopDestination) {
            navTargets += destination
        }
    }

    private fun anchor(completed: Boolean = false) = AnchorHabitUi(
        id = "anchor-1",
        title = "Read 10 pages",
        subtitle = "Learning • morning",
        estimatedTimeLabel = "10m",
        dayLabel = "Day 6/30",
        completed = completed,
    )

    private fun goal(id: String, title: String, category: String) = GoalCardUi(
        id = id,
        title = title,
        category = category,
        completionPercent = 40,
        pendingTaskLabel = "1/2 tasks pending",
        keywords = listOf("kw"),
        accentColor = Color(0xFF14B8A6),
        streakCompletedDays = 6,
        streakTotalDays = 30,
        roadmapStatusLabel = "Day 6/30",
    )

    private fun state(
        efficiencyPercent: Int = 72,
        streakDays: Int = 5,
        anchorHabit: AnchorHabitUi? = anchor(),
        focusTodos: List<TodoRowUi> = listOf(
            TodoRowUi(id = "t1", text = "Draft outline", done = false),
            TodoRowUi(id = "t2", text = "Reply to email", done = true),
        ),
        activeGoals: List<GoalCardUi> = listOf(
            goal("g1", "Get Fit", "Fitness"),
            goal("g2", "Ship App", "Career"),
        ),
    ) = DashboardUiState(
        greeting = "Good morning, Sam",
        subCaption = "One small step at a time",
        streakDays = streakDays,
        efficiencyPercent = efficiencyPercent,
        anchorHabit = anchorHabit,
        focusTodos = focusTodos,
        activeGoals = activeGoals,
    )

    private fun setDashboard(
        uiState: DashboardUiState,
        actions: DashboardActions = RecordingActions(),
    ) {
        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                DashboardScreen(state = uiState, on = actions)
            }
        }
    }

    // --- AUI-1.1: header efficiency ring + streak pill/glyph (satisfies AUI-9.2) --------------

    @Test
    fun aui_1_1_header_ring_reflects_integer_percent_and_streak_pill_is_shown() {
        val percent = 72
        val streak = 5
        setDashboard(state(efficiencyPercent = percent, streakDays = streak))

        // The efficiency ring exposes a single aggregate content description carrying the integer
        // percent (AUI-8.4 / Property 5 — an integer, no fractional digits).
        val ringDescription = str(R.string.aui_dashboard_efficiency_ring_content_description, percent)
        composeRule.onNodeWithContentDescription(ringDescription).assertIsDisplayed()

        // The amber streak pill renders the glyph + day count (e.g. "🔥 5 DAYS STREAK").
        val streakPill = str(
            R.string.aui_dashboard_streak_pill,
            str(R.string.aui_dashboard_streak_glyph),
            streak,
        )
        composeRule.onNodeWithText(streakPill).assertIsDisplayed()
    }

    // --- AUI-1.2: anchor card present-vs-empty -------------------------------------------------

    @Test
    fun aui_1_2_anchor_card_shows_habit_title_when_present() {
        val habit = anchor()
        setDashboard(state(anchorHabit = habit))

        // The anchor heading is always shown; the habit title is exposed by the MomentumCheckbox's
        // aggregate content description ("<title>, not completed").
        composeRule.onNodeWithText(str(R.string.aui_dashboard_anchor_heading)).assertIsDisplayed()
        composeRule.onNode(hasContentDescription(habit.title, substring = true)).assertIsDisplayed()

        // The start-a-habit prompt is absent while an anchor exists.
        composeRule.onNodeWithText(str(R.string.aui_dashboard_anchor_empty_title)).assertDoesNotExist()
    }

    @Test
    fun aui_1_2_anchor_card_shows_start_a_habit_prompt_when_null() {
        setDashboard(state(anchorHabit = null))

        composeRule.onNodeWithText(str(R.string.aui_dashboard_anchor_empty_title)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.aui_dashboard_anchor_empty_body)).assertIsDisplayed()
    }

    // --- AUI-1.3: anchor checkbox toggle callback + checked state (satisfies AUI-9.3) ----------

    @Test
    fun aui_1_3_toggling_anchor_checkbox_invokes_callback_and_is_toggleable() {
        val actions = RecordingActions()
        val habit = anchor(completed = false)
        setDashboard(state(anchorHabit = habit), actions)

        // The anchor habit's MomentumCheckbox is a single toggleable node named by the habit title.
        val checkbox = composeRule.onNode(hasContentDescription(habit.title, substring = true))
        checkbox.assert(isToggleable())

        // Toggling an unchecked anchor dispatches onAnchorHabitToggled(true) — the <=100ms
        // strikethrough + confirmation haptic are structural guarantees of MomentumCheckbox.
        checkbox.performClick()
        assertEquals(true, actions.anchorToggledTo)
    }

    @Test
    fun aui_1_3_completed_anchor_checkbox_reports_checked_state() {
        val habit = anchor(completed = true)
        setDashboard(state(anchorHabit = habit))

        // A completed MomentumCheckbox is a toggleable node in the ON state; the accompanying
        // <=100ms strikethrough + dim of the label is a structural guarantee of MomentumCheckbox
        // (Property 6, verified in the MomentumCheckbox component tests) and is not separately
        // observable here because the control aggregates its children under a single semantics node.
        composeRule.onNode(hasContentDescription(habit.title, substring = true))
            .assert(isToggleable())
            .assert(isOn())
    }

    // --- AUI-1.4: Today's Focus list + quick-add ----------------------------------------------

    @Test
    fun aui_1_4_focus_title_and_each_row_text_is_shown() {
        val todos = listOf(
            TodoRowUi(id = "t1", text = "Draft outline", done = false),
            TodoRowUi(id = "t2", text = "Reply to email", done = true),
        )
        setDashboard(state(focusTodos = todos))

        // Today's Focus sits below the header + anchor cards; scroll it into composition first.
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText(str(R.string.aui_dashboard_focus_title)))

        composeRule.onNodeWithText(str(R.string.aui_dashboard_focus_title)).assertIsDisplayed()
        // Each focus row is a MomentumCheckbox; its label is exposed via the aggregate description.
        composeRule.onNode(hasContentDescription("Draft outline", substring = true)).assertIsDisplayed()
        composeRule.onNode(hasContentDescription("Reply to email", substring = true)).assertIsDisplayed()
    }

    @Test
    fun aui_1_4_quick_add_invokes_callback_with_entered_text() {
        val actions = RecordingActions()
        setDashboard(state(), actions)

        // Scroll the quick-add input into composition (it lives in the Today's Focus card, below the
        // header + anchor cards).
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText(str(R.string.aui_dashboard_focus_add_placeholder)))

        val entered = "Meditate 5 min"
        composeRule.onNodeWithText(str(R.string.aui_dashboard_focus_add_placeholder))
            .performTextInput(entered)
        composeRule.onNodeWithContentDescription(str(R.string.aui_dashboard_focus_add_content_description))
            .performClick()

        assertEquals(listOf(entered), actions.quickAddTexts)
    }

    // --- AUI-1.6: active-goals grid ------------------------------------------------------------

    @Test
    fun aui_1_6_each_goal_title_and_category_is_shown() {
        val goals = listOf(
            goal("g1", "Get Fit", "Fitness"),
            goal("g2", "Ship App", "Career"),
        )
        setDashboard(state(activeGoals = goals))

        // The 2-column goals grid is the bottom section; scroll each card into composition before
        // asserting its title/category (lazy grids only compose visible items).
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Get Fit"))
        composeRule.onNodeWithText("Get Fit").assertIsDisplayed()
        composeRule.onNodeWithText("Fitness").assertIsDisplayed()

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Ship App"))
        composeRule.onNodeWithText("Ship App").assertIsDisplayed()
        composeRule.onNodeWithText("Career").assertIsDisplayed()
    }

    @Test
    fun aui_1_6_tapping_goal_card_invokes_on_goal_clicked_with_id() {
        val actions = RecordingActions()
        setDashboard(state(activeGoals = listOf(goal("g1", "Get Fit", "Fitness"))), actions)

        // Scroll the goal card into composition before tapping it (bottom grid section).
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Get Fit"))
        composeRule.onNodeWithText("Get Fit").performClick()

        assertEquals(listOf("g1"), actions.goalClicks)
    }

    // --- AUI-1.7: Quick-Create FAB -------------------------------------------------------------

    @Test
    fun aui_1_7_quick_create_fab_is_shown_and_click_invokes_callback() {
        val actions = RecordingActions()
        setDashboard(state(), actions)

        val fabDescription = str(R.string.aui_dashboard_quick_create_content_description)
        composeRule.onNodeWithContentDescription(fabDescription).assertIsDisplayed().performClick()

        assertEquals(1, actions.quickCreateCount)
    }

    // --- AUI-1.8: bottom nav with Home active --------------------------------------------------

    @Test
    fun aui_1_8_bottom_nav_rendered_with_home_active() {
        val actions = RecordingActions()
        setDashboard(state(), actions)

        // The active destination (Home) renders its bold label and is the selected tab.
        composeRule.onNodeWithText(str(R.string.nav_home)).assertIsDisplayed().assertIsSelected()
    }
}
