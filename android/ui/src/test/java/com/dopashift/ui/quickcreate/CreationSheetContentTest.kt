package com.dopashift.ui.quickcreate

// Feature: dashboard-quick-create, Task 18.2

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.dopashift.domain.creation.HabitAuthoring
import com.dopashift.domain.creation.ReminderDraft
import com.dopashift.domain.entity.GoalProfile
import com.dopashift.ui.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * Example / Compose UI tests for the three Creation_Sheet composables (task 18.2).
 *
 * These are written as Robolectric JVM unit tests, matching the existing `:ui` test convention
 * (see `RuleAuthoringScreenTest`): `createComposeRule` works under [RobolectricTestRunner], and the
 * module already enables `testOptions.unitTests.isIncludeAndroidResources = true` so
 * `stringResource` lookups resolve. Each sheet composable is driven directly with a representative
 * state and its callbacks so the visible content and affordances can be asserted.
 *
 * The sheet bodies are hosted in a scrollable Material 3 `ModalBottomSheet`, so content below the
 * initial viewport *exists* in the semantics tree but is not necessarily "displayed" until scrolled
 * into view. Presence checks therefore use `assertExists`, and interactions with off-screen
 * controls `performScrollTo` first — the same distinction the existing UI tests rely on.
 *
 * Criteria covered (tagged per test):
 * - Goal sheet — preset categories (DQC-2.2), Suggest-with-AI disabled state (DQC-2.5),
 *   character counter within 20 of limit (DQC-2.7), required-field minimum with no other mandatory
 *   field (DQC-2.10, and the first-goal next-step of DQC-2.9 being driven off summary not the sheet).
 * - Habit sheet — Inline_Goal_Capture step with no blocking error (DQC-3.2 / DQC-5.4), three
 *   authoring modes offered (DQC-3.5), LLM mode disabled + defaults to Template when unconfigured
 *   (DQC-3.7), precedence disclosure when an active track already exists (DQC-3.11), reminder
 *   offered-not-required (DQC-3.12).
 * - To-do sheet — full sheet collects optional due date/time + reminder (DQC-4.4).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CreationSheetContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun str(resId: Int): String =
        ApplicationProvider.getApplicationContext<android.content.Context>().getString(resId)

    private fun str(resId: Int, vararg args: Any): String =
        ApplicationProvider.getApplicationContext<android.content.Context>().getString(resId, *args)

    private val presetCategories = listOf(
        "Career Growth", "Fitness", "Build Business", "Clear Exam", "Learning", "Wellbeing",
    )

    // =================================================================================
    // Goal Creation_Sheet
    // =================================================================================

    /** DQC-2.2: the goal sheet offers a preset category picker with the bundled presets. */
    @Test
    fun goalSheet_showsPresetCategories_DQC_2_2() {
        composeRule.setContent {
            GoalCreationSheet(
                state = GoalSheetState(presetCategories = presetCategories),
                callbacks = noopGoalCallbacks(),
            )
        }

        // The "Suggested categories" heading and every preset chip render.
        composeRule.onNodeWithText(str(R.string.qc_goal_category_presets_title)).assertExists()
        presetCategories.forEach { preset ->
            composeRule.onNodeWithText(preset).assertExists()
        }
    }

    /**
     * DQC-2.5: when no LLM provider is configured the "Suggest with AI" action is shown DISABLED
     * with an explanatory label rather than an action that errors on tap.
     */
    @Test
    fun goalSheet_suggestWithAi_disabledWithHint_whenLlmNotConfigured_DQC_2_5() {
        var suggestInvoked = false
        composeRule.setContent {
            GoalCreationSheet(
                state = GoalSheetState(llmConfigured = false),
                callbacks = noopGoalCallbacks(
                    onSuggestWithAi = { _, _, _ -> suggestInvoked = true },
                ),
            )
        }

        // The action is present but disabled, and the explanatory hint is shown.
        composeRule.onNodeWithText(str(R.string.qc_goal_suggest_ai)).assertIsNotEnabled()
        composeRule.onNodeWithText(str(R.string.qc_goal_suggest_ai_disabled_hint)).assertExists()
        assertFalse("disabled action must not have fired a suggestion", suggestInvoked)
    }

    /** DQC-2.5 (positive): when configured, the action is enabled (no disabled hint). */
    @Test
    fun goalSheet_suggestWithAi_enabled_whenLlmConfigured_DQC_2_5() {
        composeRule.setContent {
            GoalCreationSheet(
                state = GoalSheetState(llmConfigured = true),
                callbacks = noopGoalCallbacks(),
            )
        }

        composeRule.onNodeWithText(str(R.string.qc_goal_suggest_ai)).assertIsEnabled()
        composeRule.onNodeWithText(str(R.string.qc_goal_suggest_ai_disabled_hint)).assertDoesNotExist()
    }

    /**
     * DQC-2.7: a per-field character counter appears only when the field is within 20 characters of
     * its limit. The name limit is 100, so 81 characters (within 20) shows the counter while a
     * short value does not.
     */
    @Test
    fun goalSheet_nameCharacterCounter_appearsWithin20OfLimit_DQC_2_7() {
        composeRule.setContent {
            GoalCreationSheet(
                state = GoalSheetState(),
                callbacks = noopGoalCallbacks(),
            )
        }

        val nameField = composeRule.onNodeWithText(str(R.string.qc_goal_name_label))

        // A short name (well below the threshold) shows no counter.
        nameField.performTextInput("Short goal")
        composeRule.onAllNodesWithText(str(R.string.qc_goal_char_counter, 10, 100))
            .assertCountEquals(0)

        // Extending the name to 81 chars (within 20 of the 100 limit) surfaces the counter.
        nameField.performTextInput("x".repeat(71)) // 10 + 71 = 81
        composeRule.onNodeWithText(str(R.string.qc_goal_char_counter, 81, 100)).assertExists()
    }

    /**
     * DQC-2.10 / DQC-2.9: the sheet is completable with only the three required inputs (name,
     * category, >= 1 keyword) — no additional mandatory field. Save is disabled until those three
     * are present and becomes enabled once they are, with nothing else required. The first-goal
     * next-step prompt is driven off the refreshed summary, not this sheet, so nothing here blocks.
     */
    @Test
    fun goalSheet_requiredFieldMinimum_enablesSaveWithoutOtherFields_DQC_2_10() {
        composeRule.setContent {
            GoalCreationSheet(
                state = GoalSheetState(),
                callbacks = noopGoalCallbacks(),
            )
        }

        val saveLabel = str(R.string.qc_goal_save)

        // With no input the save action is disabled.
        composeRule.onNodeWithText(saveLabel).performScrollTo().assertIsNotEnabled()

        // Provide exactly the three required inputs.
        composeRule.onNodeWithText(str(R.string.qc_goal_name_label)).performScrollTo()
            .performTextInput("Get fit")
        composeRule.onNodeWithText(str(R.string.qc_goal_category_label)).performScrollTo()
            .performTextInput("Fitness")
        composeRule.onNodeWithText(str(R.string.qc_goal_keyword_input_placeholder)).performScrollTo()
            .performTextInput("running")
        composeRule.onNodeWithContentDescription(str(R.string.qc_goal_keyword_add)).performClick()

        // Save is now enabled — no description or any other field was required (DQC-2.10).
        composeRule.onNodeWithText(saveLabel).performScrollTo().assertIsEnabled()
    }

    // =================================================================================
    // Habit Creation_Sheet
    // =================================================================================

    /**
     * DQC-3.2 / DQC-5.4: with zero active goals the habit sheet opens on Inline_Goal_Capture as its
     * first step — collecting goal fields inline with no blocking error and no navigation away.
     */
    @Test
    fun habitSheet_zeroGoals_showsInlineGoalCapture_noBlockingError_DQC_3_2() {
        composeRule.setContent {
            HabitCreationSheet(
                state = HabitSheetState(
                    goals = emptyList(),
                    presetCategories = presetCategories,
                ),
                callbacks = noopHabitCallbacks(),
            )
        }

        // Inline_Goal_Capture title/labels are shown; the goal-selection step is not.
        composeRule.onNodeWithText(str(R.string.qc_habit_inline_goal_title)).assertExists()
        composeRule.onNodeWithText(str(R.string.qc_habit_inline_goal_name_label)).assertExists()
        composeRule.onNodeWithText(str(R.string.qc_habit_goal_step_title)).assertDoesNotExist()

        // The inline keyword-required hint is a non-blocking prompt, not a blocking error, and the
        // sheet still renders its content (authoring modes below) so the user is not stranded.
        composeRule.onNodeWithText(str(R.string.qc_habit_inline_goal_keywords_required)).assertExists()
        composeRule.onNodeWithText(str(R.string.qc_habit_authoring_title)).assertExists()
    }

    /** DQC-3.5: the habit sheet offers all three authoring modes (Template, AI plan, Manual). */
    @Test
    fun habitSheet_offersThreeAuthoringModes_DQC_3_5() {
        composeRule.setContent {
            HabitCreationSheet(
                state = HabitSheetState(goals = emptyList(), llmConfigured = true),
                callbacks = noopHabitCallbacks(),
            )
        }

        composeRule.onNodeWithText(str(R.string.qc_habit_mode_template)).assertExists()
        composeRule.onNodeWithText(str(R.string.qc_habit_mode_llm)).assertExists()
        composeRule.onNodeWithText(str(R.string.qc_habit_mode_manual)).assertExists()
    }

    /**
     * DQC-3.7: when no LLM provider is configured the AI-plan mode is disabled with an explanatory
     * label; the selection defaults to Template (never LLM). We assert the disabled label is shown
     * and that saving with the default selection produces a Template authoring payload.
     */
    @Test
    fun habitSheet_llmMode_disabledAndDefaultsToTemplate_whenUnconfigured_DQC_3_7() {
        val goal = sampleGoal("Career Growth", "Professional")
        var savedAuthoring: HabitAuthoring? = null
        composeRule.setContent {
            HabitCreationSheet(
                state = HabitSheetState(
                    goals = listOf(goal),
                    defaultGoalId = goal.id,
                    templateCategory = goal.category,
                    llmConfigured = false,
                ),
                callbacks = noopHabitCallbacks(
                    onSave = { _, authoring, _, _ -> savedAuthoring = authoring },
                ),
            )
        }

        // The AI-plan mode carries its "unavailable / using Template" explanatory label (DQC-3.7).
        composeRule.onNodeWithText(str(R.string.qc_habit_mode_llm_disabled)).assertExists()

        // Saving without changing the mode uses the default (Template), never LLM.
        composeRule.onNodeWithText(str(R.string.qc_habit_save)).performScrollTo().performClick()
        assertTrue(
            "default authoring must be Template when LLM is unconfigured",
            savedAuthoring is HabitAuthoring.Template,
        )
    }

    /**
     * DQC-3.11: when the selected goal already has an active habit track, the sheet informs the
     * user which track takes precedence in the Intercept_Overlay before saving.
     */
    @Test
    fun habitSheet_precedenceDisclosure_whenActiveTrackExists_DQC_3_11() {
        val goal = sampleGoal("Fitness", "Fitness")
        composeRule.setContent {
            HabitCreationSheet(
                state = HabitSheetState(
                    goals = listOf(goal),
                    defaultGoalId = goal.id,
                    templateCategory = goal.category,
                    existingActiveTrackForSelectedGoal = true,
                ),
                callbacks = noopHabitCallbacks(),
            )
        }

        composeRule.onNodeWithText(str(R.string.qc_habit_precedence_disclosure)).assertExists()
    }

    /** DQC-3.11 (negative): with no existing active track the precedence disclosure is absent. */
    @Test
    fun habitSheet_noPrecedenceDisclosure_whenNoActiveTrack_DQC_3_11() {
        val goal = sampleGoal("Fitness", "Fitness")
        composeRule.setContent {
            HabitCreationSheet(
                state = HabitSheetState(
                    goals = listOf(goal),
                    defaultGoalId = goal.id,
                    templateCategory = goal.category,
                    existingActiveTrackForSelectedGoal = false,
                ),
                callbacks = noopHabitCallbacks(),
            )
        }

        composeRule.onNodeWithText(str(R.string.qc_habit_precedence_disclosure)).assertDoesNotExist()
    }

    /**
     * DQC-3.12: a daily checkpoint reminder is OFFERED, not required — the sheet shows the reminder
     * toggle, and a habit can be saved without enabling it (default off).
     */
    @Test
    fun habitSheet_reminderOfferedNotRequired_DQC_3_12() {
        val goal = sampleGoal("Learning", "Learning")
        var savedWithReminder: Boolean? = null
        composeRule.setContent {
            HabitCreationSheet(
                state = HabitSheetState(
                    goals = listOf(goal),
                    defaultGoalId = goal.id,
                    templateCategory = goal.category,
                ),
                callbacks = noopHabitCallbacks(
                    onSave = { _, _, withReminder, _ -> savedWithReminder = withReminder },
                ),
            )
        }

        // The reminder toggle is offered.
        composeRule.onNodeWithText(str(R.string.qc_habit_reminder_label)).assertExists()

        // Saving without touching it succeeds with the reminder not required (default off).
        composeRule.onNodeWithText(str(R.string.qc_habit_save)).performScrollTo().performClick()
        assertTrue("reminder must not be required to save", savedWithReminder == false)
    }

    // =================================================================================
    // To-Do Creation_Sheet
    // =================================================================================

    /**
     * DQC-4.4: the full "New To-Do" sheet collects the required text PLUS an optional due date/time
     * and an optional reminder configuration. We assert both optional affordances render alongside
     * the required text field.
     */
    @Test
    fun todoSheet_collectsOptionalDueDateAndReminder_DQC_4_4() {
        composeRule.setContent {
            TodoCreationSheet(
                state = TodoSheetState(zoneId = ZoneId.of("UTC")),
                callbacks = noopTodoCallbacks(),
                text = "",
                onTextChange = {},
                dueDateTime = null,
                reminder = null,
                onPickDueDateTime = {},
                onPickReminder = {},
            )
        }

        // Required text field plus both optional field affordances are present (DQC-4.4).
        composeRule.onNodeWithText(str(R.string.qc_todo_text_placeholder)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.qc_todo_due_add)).assertExists()
        composeRule.onNodeWithText(str(R.string.qc_todo_reminder_add)).assertExists()
    }

    /** DQC-4.4: an already-set optional due date/time and reminder are reflected in the sheet. */
    @Test
    fun todoSheet_reflectsSetDueDateAndReminder_DQC_4_4() {
        val due = Instant.parse("2026-09-01T09:00:00Z")
        val reminder = ReminderDraft(time = LocalTime.of(8, 0))

        composeRule.setContent {
            TodoCreationSheet(
                state = TodoSheetState(zoneId = ZoneId.of("UTC")),
                callbacks = noopTodoCallbacks(),
                text = "Prep slides",
                onTextChange = {},
                dueDateTime = due,
                reminder = reminder,
                onPickDueDateTime = {},
                onPickReminder = {},
            )
        }

        // When a due date/time is set, the "add" prompt is replaced by the "set" summary label.
        composeRule.onNodeWithText(str(R.string.qc_todo_due_add)).assertDoesNotExist()
        // A reminder summary is shown (not the "add reminder" prompt).
        composeRule.onNodeWithText(str(R.string.qc_todo_reminder_add)).assertDoesNotExist()
        // Save is enabled since the required text is present and within limits.
        composeRule.onNodeWithText(str(R.string.qc_todo_save)).assertIsEnabled()
    }

    /** DQC-4.4: the optional pickers are hidden when the host provides no picker lambdas. */
    @Test
    fun todoSheet_optionalFieldsHidden_whenNoPickersProvided_DQC_4_4() {
        composeRule.setContent {
            TodoCreationSheet(
                state = TodoSheetState(zoneId = ZoneId.of("UTC")),
                callbacks = noopTodoCallbacks(),
                text = "",
                onTextChange = {},
                dueDateTime = null,
                reminder = null,
                onPickDueDateTime = null,
                onPickReminder = null,
            )
        }

        composeRule.onNodeWithText(str(R.string.qc_todo_due_add)).assertDoesNotExist()
        composeRule.onNodeWithText(str(R.string.qc_todo_reminder_add)).assertDoesNotExist()
        // The required text field remains, so the full sheet still functions.
        composeRule.onNodeWithText(str(R.string.qc_todo_text_placeholder)).assertIsDisplayed()
    }

    // =================================================================================
    // Helpers
    // =================================================================================

    private fun sampleGoal(name: String, category: String): GoalProfile = GoalProfile(
        id = UUID.randomUUID(),
        userId = UUID.randomUUID(),
        name = name,
        category = category,
        keywords = listOf("keyword"),
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        isActive = true,
    )

    private fun noopGoalCallbacks(
        onSave: (String, String, List<String>, String?) -> Unit = { _, _, _, _ -> },
        onSuggestWithAi: (String, String?, List<String>) -> Unit = { _, _, _ -> },
    ) = GoalSheetCallbacks(
        onSave = onSave,
        onDismiss = {},
        onDismissWithUnsavedInput = {},
        onCategoryChanged = {},
        onSuggestWithAi = onSuggestWithAi,
    )

    private fun noopHabitCallbacks(
        onSave: (UUID?, HabitAuthoring, Boolean, InlineGoalFields?) -> Unit = { _, _, _, _ -> },
    ) = HabitSheetCallbacks(
        onSave = onSave,
        onDismiss = {},
        onDismissWithUnsavedInput = {},
        onCategoryChanged = {},
        onGoalSelected = {},
    )

    private fun noopTodoCallbacks() = TodoSheetCallbacks(
        onSave = { _, _, _ -> },
        onDismiss = {},
    )
}
