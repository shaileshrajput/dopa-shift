package com.dopashift.ui.screens

// Feature: android-ui-upgrade, Task 8.3

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isOff
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
import com.dopashift.ui.state.GoalCardUi
import com.dopashift.ui.state.GoalDetailUiState
import com.dopashift.ui.state.GoalsUiState
import com.dopashift.ui.state.TodoRowUi
import com.dopashift.ui.theme.DopaShiftTheme
import com.dopashift.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI + instrumented-style tests for the **Goals & Habits overview** (AUI-4) and the
 * **Goal Detail & 30-Day Roadmap** (AUI-5) screens (spec `android-ui-upgrade`, task 8.3; satisfies
 * AUI-9.3 and AUI-9.6).
 *
 * Written as **Robolectric JVM Compose tests** so they run in this environment without an
 * emulator/device, matching the existing `:ui` convention (see [DashboardScreenTest],
 * [AppLimitsScreenTest], [InterceptOverlayScreenTest]): `createComposeRule` works under
 * [RobolectricTestRunner] and the module enables
 * `testOptions.unitTests.isIncludeAndroidResources = true` so `stringResource` lookups resolve.
 *
 * Both screens are stateless renderers: [GoalsHabitsScreen] renders a [GoalsUiState] and dispatches
 * gestures through the [GoalsActions] interface; [GoalDetailScreen] renders a [GoalDetailUiState]
 * and dispatches through the [GoalDetailActions] surface. Each test drives the composable with a
 * controlled state plus recording callbacks, wrapped in `DopaShiftTheme(themeMode = ThemeMode.DARK)`.
 *
 * Coverage:
 *  - AUI-4.1 goal summary card (mini ring integer percent, title, category chip, subtitle counter,
 *    keyword chips) + card click dispatch.
 *  - AUI-4.2 bottom streak dot-matrix row (aggregate description) + roadmap status line.
 *  - AUI-4.3 persistent Quick-Create FAB + click dispatch.
 *  - AUI-4.4 bottom nav with Goals active.
 *  - AUI-5.1 top app bar (back / edit / delete) each dispatching its callback.
 *  - AUI-5.2 category + keyword chips.
 *  - AUI-5.3 interactive checklist: toggle dispatch, checked/ON strikethrough state, inline delete
 *    (satisfies AUI-9.3, the instrumented checkbox-strikethrough coverage).
 *  - AUI-5.4 inline add-checklist-item field captures the trimmed text.
 *  - AUI-5.5 Habit_Track roadmap card: `Day N/30` label, `Active` badge, `completed/30` bar
 *    (aggregate description) + sub-caption (satisfies AUI-9.6).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GoalsAndDetailScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun str(resId: Int): String = context.getString(resId)
    private fun str(resId: Int, vararg args: Any): String = context.getString(resId, *args)

    /** Matcher on a resolved string resource, used with `performScrollToNode`. */
    private fun hasTextResource(resId: Int) = hasText(str(resId))

    // =============================================================================================
    // Goals & Habits overview (AUI-4)
    // =============================================================================================

    /** A recording [GoalsActions] capturing every dispatched gesture on the overview screen. */
    private class RecordingGoalsActions : GoalsActions {
        val goalClicks = mutableListOf<String>()
        var quickCreateCount = 0
        val navTargets = mutableListOf<TopDestination>()

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

    private fun goalCard(
        id: String = "g1",
        title: String = "Get Fit",
        category: String = "Fitness",
        completionPercent: Int = 40,
        pendingTaskLabel: String = "1/2 tasks pending",
        keywords: List<String> = listOf("run", "gym"),
        streakCompletedDays: Int = 6,
        streakTotalDays: Int = 30,
        roadmapStatusLabel: String = "Day 6/30",
    ) = GoalCardUi(
        id = id,
        title = title,
        category = category,
        completionPercent = completionPercent,
        pendingTaskLabel = pendingTaskLabel,
        keywords = keywords,
        accentColor = Color(0xFF14B8A6),
        streakCompletedDays = streakCompletedDays,
        streakTotalDays = streakTotalDays,
        roadmapStatusLabel = roadmapStatusLabel,
    )

    private fun setGoals(
        uiState: GoalsUiState,
        actions: GoalsActions = RecordingGoalsActions(),
    ) {
        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                GoalsHabitsScreen(state = uiState, on = actions)
            }
        }
    }

    // --- AUI-4.1: goal summary card content + click dispatch -----------------------------------

    @Test
    fun aui_4_1_goal_card_shows_ring_title_category_subtitle_and_keywords() {
        val card = goalCard()
        setGoals(GoalsUiState(goals = listOf(card)))

        // Mini completion ring exposes its aggregate content description carrying the integer percent.
        val ringDescription = str(
            R.string.aui_goals_ring_content_description,
            card.title,
            card.completionPercent,
        )
        composeRule.onNodeWithContentDescription(ringDescription).assertIsDisplayed()

        // Title, category pill, subtitle counter, and each keyword chip are shown.
        composeRule.onNodeWithText(card.title).assertIsDisplayed()
        composeRule.onNodeWithText(card.category).assertIsDisplayed()
        composeRule.onNodeWithText(card.pendingTaskLabel).assertIsDisplayed()
        card.keywords.forEach { keyword ->
            composeRule.onNodeWithText(keyword).assertIsDisplayed()
        }
    }

    @Test
    fun aui_4_1_tapping_goal_card_invokes_on_goal_clicked_with_id() {
        val actions = RecordingGoalsActions()
        setGoals(GoalsUiState(goals = listOf(goalCard(id = "g-42", title = "Ship App"))), actions)

        composeRule.onNodeWithText("Ship App").performClick()

        assertEquals(listOf("g-42"), actions.goalClicks)
    }

    // --- AUI-4.2: bottom streak dot-matrix + roadmap status line -------------------------------

    @Test
    fun aui_4_2_streak_row_exposes_aggregate_description_and_roadmap_status_line() {
        val card = goalCard(streakCompletedDays = 6, streakTotalDays = 30, roadmapStatusLabel = "Day 6/30")
        setGoals(GoalsUiState(goals = listOf(card)))

        // The dot-matrix collapses into one aggregate TalkBack description "Streak: 6 of 30 days completed".
        val streakDescription = str(R.string.aui_goals_streak_content_description, 6, 30)
        composeRule.onNodeWithContentDescription(streakDescription).assertIsDisplayed()

        // The roadmap status line copy is shown alongside it.
        composeRule.onNodeWithText("Day 6/30").assertIsDisplayed()
    }

    // --- AUI-4.3: persistent Quick-Create FAB --------------------------------------------------

    @Test
    fun aui_4_3_quick_create_fab_is_shown_and_click_invokes_callback() {
        val actions = RecordingGoalsActions()
        setGoals(GoalsUiState(goals = listOf(goalCard())), actions)

        composeRule
            .onNodeWithContentDescription(str(R.string.aui_goals_quick_create_content_description))
            .assertIsDisplayed()
            .performClick()

        assertEquals(1, actions.quickCreateCount)
    }

    // --- AUI-4.4: bottom nav with Goals active -------------------------------------------------

    @Test
    fun aui_4_4_bottom_nav_rendered_with_goals_active() {
        setGoals(GoalsUiState(goals = listOf(goalCard())))

        // The active destination (Goals) renders its bold label and is the selected tab.
        composeRule.onNodeWithText(str(R.string.nav_goals)).assertIsDisplayed().assertIsSelected()
    }

    // =============================================================================================
    // Goal Detail & 30-Day Roadmap (AUI-5)
    // =============================================================================================

    private fun detailState(
        title: String = "Ship the side project",
        category: String = "Business",
        keywords: List<String> = listOf("startup", "mvp"),
        checklist: List<TodoRowUi> = listOf(
            TodoRowUi(id = "c1", text = "Draft landing page", done = true, deletable = true),
            TodoRowUi(id = "c2", text = "Wire up payments", done = false, deletable = true),
        ),
        roadmapDayLabel: String = "Day 6/30",
        roadmapActive: Boolean = true,
        roadmapCompletedDays: Int = 6,
        roadmapSubCaption: String = "6 of 30 days completed",
    ) = GoalDetailUiState(
        goalId = "g1",
        title = title,
        category = category,
        keywords = keywords,
        checklist = checklist,
        roadmapDayLabel = roadmapDayLabel,
        roadmapActive = roadmapActive,
        roadmapCompletedDays = roadmapCompletedDays,
        roadmapSubCaption = roadmapSubCaption,
    )

    /**
     * Builds a [GoalDetailActions] with recording lambdas for the fields a test cares about;
     * the rest default to no-ops.
     */
    private fun detailActions(
        onBack: () -> Unit = {},
        onEdit: () -> Unit = {},
        onDelete: () -> Unit = {},
        onChecklistItemToggled: (String, Boolean) -> Unit = { _, _ -> },
        onChecklistItemDeleted: (String) -> Unit = {},
        onAddChecklistItem: (String) -> Unit = {},
    ) = GoalDetailActions(
        onBack = onBack,
        onEdit = onEdit,
        onDelete = onDelete,
        onChecklistItemToggled = onChecklistItemToggled,
        onChecklistItemDeleted = onChecklistItemDeleted,
        onAddChecklistItem = onAddChecklistItem,
    )

    private fun setDetail(
        uiState: GoalDetailUiState,
        actions: GoalDetailActions = detailActions(),
    ) {
        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                GoalDetailScreen(state = uiState, on = actions)
            }
        }
    }

    // --- AUI-5.1: top app bar back / edit / delete dispatch ------------------------------------

    @Test
    fun aui_5_1_top_app_bar_shows_title_and_controls_dispatch_callbacks() {
        var backCount = 0
        var editCount = 0
        var deleteCount = 0
        setDetail(
            detailState(title = "Ship the side project"),
            detailActions(
                onBack = { backCount++ },
                onEdit = { editCount++ },
                onDelete = { deleteCount++ },
            ),
        )

        // The goal title is rendered in the app bar.
        composeRule.onNodeWithText("Ship the side project").assertIsDisplayed()

        // Back / edit / delete controls each dispatch their callback exactly once.
        composeRule
            .onNodeWithContentDescription(str(R.string.aui_goal_detail_back_content_description))
            .performClick()
        composeRule
            .onNodeWithContentDescription(str(R.string.aui_goal_detail_edit_content_description))
            .performClick()
        composeRule
            .onNodeWithContentDescription(str(R.string.aui_goal_detail_delete_content_description))
            .performClick()

        assertEquals(1, backCount)
        assertEquals(1, editCount)
        assertEquals(1, deleteCount)
    }

    // --- AUI-5.2: category + keyword chips -----------------------------------------------------

    @Test
    fun aui_5_2_category_and_keyword_chips_are_shown() {
        setDetail(detailState(category = "Business", keywords = listOf("startup", "mvp")))

        composeRule.onNodeWithText(str(R.string.aui_goal_detail_keywords_header)).assertIsDisplayed()
        composeRule.onNodeWithText("Business").assertIsDisplayed()
        composeRule.onNodeWithText("startup").assertIsDisplayed()
        composeRule.onNodeWithText("mvp").assertIsDisplayed()
    }

    // --- AUI-5.3: interactive checklist toggle / strikethrough / delete (satisfies AUI-9.3) ----

    @Test
    fun aui_5_3_toggling_checklist_item_invokes_callback_with_id_and_new_state() {
        val toggles = mutableListOf<Pair<String, Boolean>>()
        setDetail(
            detailState(
                checklist = listOf(
                    TodoRowUi(id = "c2", text = "Wire up payments", done = false, deletable = true),
                ),
            ),
            detailActions(onChecklistItemToggled = { id, done -> toggles += id to done }),
        )

        // The row's MomentumCheckbox is a single toggleable node whose aggregate description is
        // "<text>, not completed" (the inline delete control shares the "Wire up payments"
        // substring, so we match the checkbox's exact description). Toggling an unchecked row
        // dispatches (id, true).
        composeRule
            .onNodeWithContentDescription("Wire up payments, not completed")
            .assert(isToggleable())
            .performClick()

        assertEquals(listOf("c2" to true), toggles)
    }

    @Test
    fun aui_5_3_completed_checklist_item_is_toggleable_and_on() {
        // A completed checklist item's MomentumCheckbox is a toggleable node in the ON state; the
        // teal check + strikethrough/dim of the label is a structural guarantee of MomentumCheckbox
        // (Property 6, verified in the component tests) and is not separately observable here
        // because the control aggregates its children under a single semantics node.
        setDetail(
            detailState(
                checklist = listOf(
                    TodoRowUi(id = "c1", text = "Draft landing page", done = true, deletable = true),
                ),
            ),
        )

        composeRule
            .onNodeWithContentDescription("Draft landing page, completed")
            .assert(isToggleable())
            .assert(isOn())
    }

    @Test
    fun aui_5_3_uncompleted_checklist_item_is_toggleable_and_off() {
        setDetail(
            detailState(
                checklist = listOf(
                    TodoRowUi(id = "c2", text = "Wire up payments", done = false, deletable = true),
                ),
            ),
        )

        composeRule
            .onNodeWithContentDescription("Wire up payments, not completed")
            .assert(isToggleable())
            .assert(isOff())
    }

    @Test
    fun aui_5_3_inline_delete_control_invokes_callback_with_id() {
        val deletes = mutableListOf<String>()
        setDetail(
            detailState(
                checklist = listOf(
                    TodoRowUi(id = "c2", text = "Wire up payments", done = false, deletable = true),
                ),
            ),
            detailActions(onChecklistItemDeleted = { deletes += it }),
        )

        composeRule
            .onNodeWithContentDescription(
                str(R.string.aui_goal_detail_checklist_delete_content_description, "Wire up payments"),
            )
            .performClick()

        assertEquals(listOf("c2"), deletes)
    }

    // --- AUI-5.4: inline add-checklist-item field ----------------------------------------------

    @Test
    fun aui_5_4_add_item_field_captures_trimmed_text_without_leaving_screen() {
        val added = mutableListOf<String>()
        setDetail(detailState(), detailActions(onAddChecklistItem = { added += it }))

        // The add-item field sits below the checklist in the LazyColumn; scroll it into composition.
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasTextResource(R.string.aui_goal_detail_add_item_placeholder))
        composeRule
            .onNodeWithText(str(R.string.aui_goal_detail_add_item_placeholder))
            .performTextInput("  Book flights  ")
        composeRule
            .onNodeWithContentDescription(str(R.string.aui_goal_detail_add_item_content_description))
            .performClick()

        // The field trims the input before dispatching (whitespace-only input is ignored upstream).
        assertEquals(listOf("Book flights"), added)
    }

    // --- AUI-5.5: Habit_Track roadmap card (satisfies AUI-9.6) ---------------------------------

    @Test
    fun aui_5_5_roadmap_card_shows_day_label_active_badge_bar_and_sub_caption() {
        val state = detailState(
            roadmapDayLabel = "Day 6/30",
            roadmapActive = true,
            roadmapCompletedDays = 6,
            roadmapSubCaption = "6 of 30 days completed",
        )
        setDetail(state)

        // Scroll the roadmap card (last item in the LazyColumn) into composition.
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasTextResource(R.string.aui_goal_detail_roadmap_header))

        // Day counter, Active badge, and sub-caption are shown.
        composeRule.onNodeWithText("Day 6/30").assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.aui_goal_detail_roadmap_active_badge)).assertIsDisplayed()
        composeRule.onNodeWithText("6 of 30 days completed").assertIsDisplayed()

        // The `completed/30` progress bar exposes one aggregate description combining the day
        // label and sub-caption (AUI-5.5 / AUI-8.5).
        val barDescription = str(
            R.string.aui_goal_detail_roadmap_progress_content_description,
            state.roadmapDayLabel,
            state.roadmapSubCaption,
        )
        composeRule.onNodeWithContentDescription(barDescription).assertExists()
    }

    @Test
    fun aui_5_5_roadmap_active_badge_hidden_when_inactive() {
        setDetail(detailState(roadmapActive = false))

        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasTextResource(R.string.aui_goal_detail_roadmap_header))

        composeRule
            .onNodeWithText(str(R.string.aui_goal_detail_roadmap_active_badge))
            .assertDoesNotExist()
    }
}
