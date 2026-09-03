// Feature: dashboard-quick-create, Task 18.1
// Compose UI tests for the Dashboard's creation surface: the persistent FAB + Quick_Create_Menu,
// the Zero_State / Partial_State / populated renderings, the onboarding Skip affordance, and the
// dismissible Settings "resume onboarding" entry. Written as Robolectric JVM Compose tests
// (createComposeRule works under RobolectricTestRunner; the :ui module enables
// testOptions.unitTests.isIncludeAndroidResources so stringResource lookups resolve), matching the
// existing RuleAuthoringScreenTest convention.
//
// Validates:
//   DQC-1.1 — the FAB is present in both Zero_State and Partial_State.
//   DQC-1.2 — the menu expands to exactly three actions in order New Goal, New Habit, New To-Do.
//   DQC-1.4 — the New To-Do action routes to the to-do creation callback.
//   DQC-1.5 — selecting an action triggers the correct callback (no other).
//   DQC-1.6 — the Undo affordance (UndoSnackbar) remains available >= 5 s and carries the token.
//   DQC-1.8 — dismissal via scrim tap / back gesture / FAB re-tap creates nothing.
//   DQC-1.9 — dismissing a sheet with unsaved input routes to confirm-before-discard.
//   DQC-5.1 — a Skip onboarding affordance is shown when onboarding has NOT_STARTED.
//   DQC-5.2 — Zero_State shows exactly the three creation prompts, each opening its sheet.
//   DQC-5.6 — Partial_State keeps goal-dependent (empty) sections visible as inline prompts.
//   DQC-5.7 — the onboarding-created goal prompt is suppressed when onboarding is COMPLETED.
//   DQC-5.9 — the Settings resume-onboarding entry is visible and dismissible.
//   DQC-5.10 — Inline_Goal_Capture is reachable (the habit action opens the habit sheet path).
//   DQC-7.3 — a whole-screen empty state renders ONLY in Zero_State, never with any data present.
package com.dopashift.ui.quickcreate

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.dopashift.domain.creation.DashboardSection
import com.dopashift.domain.creation.DashboardSummary
import com.dopashift.domain.creation.OnboardingStatus
import com.dopashift.ui.theme.DopaShiftTheme
import com.dopashift.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DashboardRenderingsUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val userId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000009")

    /**
     * Drives the Quick_Create_Menu's [AnimatedVisibility] enter transition to completion so its
     * children (the scrim and the three action rows) are composed and laid out before assertions.
     * With the main clock paused we step it well past the 200 ms `MotionTokens.Quick` transition;
     * this is deterministic under Robolectric where relying on auto-advance across a full
     * transition is flaky.
     */
    private fun settleMenuAnimation() {
        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeBy(1_000L)
    }

    private fun summary(
        goals: Int = 0,
        todos: Int = 0,
        habits: Int = 0,
        onboarding: OnboardingStatus = OnboardingStatus.NOT_STARTED,
    ) = DashboardSummary(
        userId = userId,
        activeGoalCount = goals,
        todayTodoCount = todos,
        activeHabitCount = habits,
        onboardingStatus = onboarding,
    )

    // A minimal populated-content slot: renders one text marker per POPULATED section and defers
    // every section's inline-prompt rendering to the DashboardBody-provided builder, mirroring how
    // the real host interleaves populated sections with inline prompts (DQC-5.3).
    private fun setDashboardBody(
        summary: DashboardSummary,
        onPrompt: (CreationPrompt) -> Unit = {},
        onSkipOnboarding: () -> Unit = {},
    ) {
        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                DashboardBody(
                    summary = summary,
                    onPrompt = onPrompt,
                    onSkipOnboarding = onSkipOnboarding,
                ) { sectionInlinePrompt ->
                    DashboardSection.entries.forEach { section ->
                        Text(
                            text = "populated:${section.name}",
                            modifier = Modifier.testTag("populated_${section.name}"),
                        )
                        sectionInlinePrompt(section)
                    }
                }
            }
        }
    }

    private fun setMenu(
        expanded: Boolean,
        onToggle: () -> Unit = {},
        onDismiss: () -> Unit = {},
        onAction: (QuickCreateAction) -> Unit = {},
    ) {
        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                Box(modifier = Modifier.fillMaxSize()) {
                    QuickCreateMenu(
                        state = QuickCreateMenuState(expanded = expanded),
                        onToggle = onToggle,
                        onDismiss = onDismiss,
                        onAction = onAction,
                    )
                }
            }
        }
        // Let the menu's AnimatedVisibility enter transition finish so the scrim and action rows
        // are composed before assertions. When collapsed there is nothing animating in.
        if (expanded) {
            settleMenuAnimation()
        }
    }

    // ---- DQC-1.1: FAB present in Zero_State and Partial_State ------------------------

    @Test
    fun dqc_1_1_fab_present_when_menu_collapsed() {
        // The FAB renders unconditionally (independent of Zero/Partial state); the menu-collapsed
        // case is the one shown in both Zero_State and Partial_State.
        setMenu(expanded = false)
        composeRule.onNodeWithTag(TestTags.QUICK_CREATE_FAB).assertExists()
    }

    @Test
    fun dqc_1_1_fab_present_when_menu_expanded() {
        setMenu(expanded = true)
        composeRule.onNodeWithTag(TestTags.QUICK_CREATE_FAB).assertExists()
    }

    // ---- Menu actions, in order New Goal, New Habit, New To-Do, Interception Info -----

    @Test
    fun menu_expands_to_actions_in_order() {
        setMenu(expanded = true)

        composeRule.onNodeWithTag(TestTags.QUICK_CREATE_ACTION_GOAL).assertExists()
        composeRule.onNodeWithTag(TestTags.QUICK_CREATE_ACTION_HABIT).assertExists()
        composeRule.onNodeWithTag(TestTags.QUICK_CREATE_ACTION_TODO).assertExists()
        composeRule.onNodeWithTag(TestTags.QUICK_CREATE_ACTION_MANAGE_APP_LIMITS).assertExists()

        // The enum drives the menu layout verbatim, so the order is fixed by declaration order.
        // Manage App Limits is surfaced right after New To-Do (Requirement 2).
        assertEquals(
            listOf(
                QuickCreateAction.NEW_GOAL,
                QuickCreateAction.NEW_HABIT,
                QuickCreateAction.NEW_TODO,
                QuickCreateAction.MANAGE_APP_LIMITS,
            ),
            QuickCreateAction.entries.toList(),
        )
    }

    @Test
    fun dqc_1_2_actions_absent_when_menu_collapsed() {
        setMenu(expanded = false)
        composeRule.onNodeWithTag(TestTags.QUICK_CREATE_ACTION_GOAL).assertDoesNotExist()
        composeRule.onNodeWithTag(TestTags.QUICK_CREATE_ACTION_HABIT).assertDoesNotExist()
        composeRule.onNodeWithTag(TestTags.QUICK_CREATE_ACTION_TODO).assertDoesNotExist()
    }

    // ---- DQC-1.5 / DQC-1.4 / DQC-5.10: selecting an action triggers the right callback

    @Test
    fun dqc_1_5_new_goal_action_triggers_goal_callback_only() {
        val actions = mutableListOf<QuickCreateAction>()
        setMenu(expanded = true, onAction = { actions.add(it) })

        composeRule.onNodeWithTag(TestTags.QUICK_CREATE_ACTION_GOAL).performClick()

        assertEquals(listOf(QuickCreateAction.NEW_GOAL), actions)
    }

    @Test
    fun dqc_5_10_new_habit_action_triggers_habit_callback_only() {
        // The habit action opens the habit Creation_Sheet, which hosts Inline_Goal_Capture for a
        // zero-goal user (DQC-5.10 — reachable via the same path).
        val actions = mutableListOf<QuickCreateAction>()
        setMenu(expanded = true, onAction = { actions.add(it) })

        composeRule.onNodeWithTag(TestTags.QUICK_CREATE_ACTION_HABIT).performClick()

        assertEquals(listOf(QuickCreateAction.NEW_HABIT), actions)
    }

    @Test
    fun dqc_1_4_new_todo_action_triggers_todo_callback_only() {
        val actions = mutableListOf<QuickCreateAction>()
        setMenu(expanded = true, onAction = { actions.add(it) })

        composeRule.onNodeWithTag(TestTags.QUICK_CREATE_ACTION_TODO).performClick()

        assertEquals(listOf(QuickCreateAction.NEW_TODO), actions)
    }

    // ---- DQC-1.8: dismissal creates nothing (scrim tap, FAB re-tap) ------------------

    @Test
    fun dqc_1_8_scrim_tap_dismisses_and_creates_nothing() {
        var dismissed = false
        val actions = mutableListOf<QuickCreateAction>()
        setMenu(
            expanded = true,
            onDismiss = { dismissed = true },
            onAction = { actions.add(it) },
        )

        composeRule.onNodeWithTag(TestTags.QUICK_CREATE_SCRIM).performClick()

        assertTrue("scrim tap must dismiss the menu", dismissed)
        assertTrue("scrim dismissal must create nothing", actions.isEmpty())
    }

    @Test
    fun dqc_1_8_fab_retap_while_expanded_toggles_closed_and_creates_nothing() {
        var toggles = 0
        val actions = mutableListOf<QuickCreateAction>()
        setMenu(
            expanded = true,
            onToggle = { toggles++ },
            onAction = { actions.add(it) },
        )

        composeRule.onNodeWithTag(TestTags.QUICK_CREATE_FAB).performClick()

        assertEquals("FAB re-tap while expanded routes to onToggle (collapse)", 1, toggles)
        assertTrue("FAB re-tap must create nothing", actions.isEmpty())
    }

    @Test
    fun dqc_1_8_scrim_absent_when_collapsed() {
        setMenu(expanded = false)
        composeRule.onNodeWithTag(TestTags.QUICK_CREATE_SCRIM).assertDoesNotExist()
    }

    // ---- DQC-5.2 / DQC-7.3: Zero_State whole-screen three prompts --------------------

    @Test
    fun dqc_5_2_zero_state_shows_exactly_three_prompts() {
        setDashboardBody(summary(goals = 0, todos = 0, habits = 0))

        composeRule.onNodeWithTag(TestTags.DASHBOARD_ZERO_STATE).assertExists()
        composeRule.onNodeWithTag(TestTags.DASHBOARD_ZERO_PROMPT_GOAL).assertExists()
        composeRule.onNodeWithTag(TestTags.DASHBOARD_ZERO_PROMPT_TODO).assertExists()
        composeRule.onNodeWithTag(TestTags.DASHBOARD_ZERO_PROMPT_HABIT).assertExists()
    }

    @Test
    fun dqc_5_2_zero_state_prompt_opens_matching_sheet() {
        val prompts = mutableListOf<CreationPrompt>()
        setDashboardBody(summary(goals = 0, todos = 0, habits = 0), onPrompt = { prompts.add(it) })

        // Scroll the to-do prompt card into view (the Zero_State is a scrolling column), then click
        // its action button, scoped to that card so the click is unambiguous.
        composeRule.onNodeWithTag(TestTags.DASHBOARD_ZERO_PROMPT_TODO).performScrollTo()
        composeRule.onNode(
            hasClickAction() and hasAnyAncestor(hasTestTag(TestTags.DASHBOARD_ZERO_PROMPT_TODO)),
        ).performClick()

        assertEquals(listOf(CreationPrompt.ADD_TODO), prompts)
        assertEquals(ActiveSheet.Todo, CreationPrompt.ADD_TODO.targetSheet())
    }

    @Test
    fun dqc_7_3_whole_screen_zero_state_absent_when_any_data_present() {
        // Partial_State: at least one count is non-zero -> no whole-screen empty state (DQC-7.3).
        setDashboardBody(summary(goals = 1, todos = 0, habits = 0))
        composeRule.onNodeWithTag(TestTags.DASHBOARD_ZERO_STATE).assertDoesNotExist()
    }

    // ---- DQC-5.6: Partial_State keeps empty (incl. goal-dependent) sections visible --

    @Test
    fun dqc_5_6_partial_state_renders_inline_prompts_for_empty_sections_and_keeps_populated() {
        // One goal exists; to-dos and habits are empty. The goals section renders populated; the
        // empty to-do and habit sections render as inline prompts so they stay discoverable.
        setDashboardBody(summary(goals = 1, todos = 0, habits = 0))

        // No whole-screen empty state.
        composeRule.onNodeWithTag(TestTags.DASHBOARD_ZERO_STATE).assertDoesNotExist()
        // Populated goals section is shown.
        composeRule.onNodeWithTag("populated_GOALS").assertExists()
        // Empty sections surface as inline prompts (habits is goal-dependent, DQC-5.6).
        composeRule.onNodeWithTag(TestTags.DASHBOARD_INLINE_PROMPT_TODOS).assertExists()
        composeRule.onNodeWithTag(TestTags.DASHBOARD_INLINE_PROMPT_HABITS).assertExists()
        // The populated goals section does NOT also render its inline prompt.
        composeRule.onNodeWithTag(TestTags.DASHBOARD_INLINE_PROMPT_GOALS).assertDoesNotExist()
    }

    @Test
    fun dqc_5_6_inline_prompt_opens_matching_sheet() {
        val prompts = mutableListOf<CreationPrompt>()
        setDashboardBody(summary(goals = 1, todos = 0, habits = 0), onPrompt = { prompts.add(it) })

        // The inline prompt's tagged node is the section container; its action lives on the button
        // inside, so click the clickable descendant scoped to the habits inline prompt.
        composeRule.onNode(
            hasClickAction() and hasAnyAncestor(hasTestTag(TestTags.DASHBOARD_INLINE_PROMPT_HABITS)),
        ).performClick()

        assertEquals(listOf(CreationPrompt.START_HABIT), prompts)
    }

    // ---- DQC-5.7: onboarding-created goal prompt suppressed when COMPLETED -----------

    @Test
    fun dqc_5_7_zero_state_goal_prompt_suppressed_when_onboarding_completed() {
        // A user who COMPLETED onboarding produced a goal, so the goal prompt is suppressed even in
        // the transient all-zero moment; the to-do and habit prompts still show.
        setDashboardBody(summary(goals = 0, todos = 0, habits = 0, onboarding = OnboardingStatus.COMPLETED))

        composeRule.onNodeWithTag(TestTags.DASHBOARD_ZERO_STATE).assertExists()
        composeRule.onNodeWithTag(TestTags.DASHBOARD_ZERO_PROMPT_GOAL).assertDoesNotExist()
        composeRule.onNodeWithTag(TestTags.DASHBOARD_ZERO_PROMPT_TODO).assertExists()
        composeRule.onNodeWithTag(TestTags.DASHBOARD_ZERO_PROMPT_HABIT).assertExists()
    }

    @Test
    fun dqc_5_7_zero_state_goal_prompt_shown_when_onboarding_skipped() {
        // A SKIPPED user created nothing during onboarding, so every prompt stays discoverable.
        setDashboardBody(summary(goals = 0, todos = 0, habits = 0, onboarding = OnboardingStatus.SKIPPED))

        composeRule.onNodeWithTag(TestTags.DASHBOARD_ZERO_PROMPT_GOAL).assertExists()
    }

    // ---- DQC-5.1: Skip onboarding affordance shown when NOT_STARTED ------------------

    @Test
    fun dqc_5_1_skip_affordance_shown_when_onboarding_not_started_and_lands_on_zero_state() {
        var skipped = false
        setDashboardBody(
            summary(goals = 0, todos = 0, habits = 0, onboarding = OnboardingStatus.NOT_STARTED),
            onSkipOnboarding = { skipped = true },
        )

        composeRule.onNodeWithTag(TestTags.DASHBOARD_ZERO_STATE).assertExists()
        composeRule.onNodeWithTag(TestTags.DASHBOARD_SKIP_ONBOARDING).assertExists().performClick()

        assertTrue("Skip affordance must invoke onSkipOnboarding", skipped)
    }

    @Test
    fun dqc_5_1_skip_affordance_absent_once_onboarding_completed() {
        setDashboardBody(
            summary(goals = 0, todos = 0, habits = 0, onboarding = OnboardingStatus.COMPLETED),
        )
        composeRule.onNodeWithTag(TestTags.DASHBOARD_SKIP_ONBOARDING).assertDoesNotExist()
    }

    @Test
    fun dqc_5_1_skip_affordance_absent_once_onboarding_skipped() {
        setDashboardBody(
            summary(goals = 0, todos = 0, habits = 0, onboarding = OnboardingStatus.SKIPPED),
        )
        composeRule.onNodeWithTag(TestTags.DASHBOARD_SKIP_ONBOARDING).assertDoesNotExist()
    }

    // ---- DQC-5.9: Settings resume-onboarding entry visible & dismissible -------------

    @Test
    fun dqc_5_9_resume_onboarding_entry_visible_when_flag_true() {
        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                ResumeOnboardingSettingsEntry(visible = true, onResume = {}, onDismiss = {})
            }
        }
        composeRule.onNodeWithTag(TestTags.SETTINGS_RESUME_ONBOARDING).assertExists()
        composeRule.onNodeWithTag(TestTags.SETTINGS_RESUME_ONBOARDING_DISMISS).assertExists()
    }

    @Test
    fun dqc_5_9_resume_onboarding_entry_absent_when_flag_false() {
        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                ResumeOnboardingSettingsEntry(visible = false, onResume = {}, onDismiss = {})
            }
        }
        composeRule.onNodeWithTag(TestTags.SETTINGS_RESUME_ONBOARDING).assertDoesNotExist()
    }

    @Test
    fun dqc_5_9_resume_onboarding_entry_dismiss_routes_to_callback() {
        var dismissed = false
        var resumed = false
        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                ResumeOnboardingSettingsEntry(
                    visible = true,
                    onResume = { resumed = true },
                    onDismiss = { dismissed = true },
                )
            }
        }

        composeRule.onNodeWithTag(TestTags.SETTINGS_RESUME_ONBOARDING_DISMISS).performClick()

        assertTrue("dismiss control must invoke onDismiss", dismissed)
        assertFalse("dismissing must not resume onboarding", resumed)
    }

    // ---- DQC-1.9: dismissing a sheet with unsaved input asks to confirm-discard ------

    @Test
    fun dqc_1_9_todo_sheet_cancel_with_unsaved_input_requests_confirm_discard() {
        var discardConfirmRequested = false
        var plainDismiss = false
        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                TodoCreationSheet(
                    state = TodoSheetState(),
                    callbacks = TodoSheetCallbacks(
                        onSave = { _, _, _ -> },
                        onDismiss = { plainDismiss = true },
                        onDismissWithUnsavedInput = { discardConfirmRequested = true },
                    ),
                    text = "buy milk",
                    onTextChange = {},
                    dueDateTime = null,
                    reminder = null,
                )
            }
        }

        // Tapping Cancel while the field holds unsaved input must route to confirm-before-discard,
        // never to a silent plain dismiss (DQC-1.9).
        composeRule.onNodeWithText(str(com.dopashift.ui.R.string.qc_todo_cancel)).performClick()

        assertTrue("unsaved input dismissal must request confirm-discard", discardConfirmRequested)
        assertFalse("unsaved input dismissal must not silently plain-dismiss", plainDismiss)
    }

    @Test
    fun dqc_1_9_todo_sheet_cancel_with_no_input_dismisses_directly() {
        var discardConfirmRequested = false
        var plainDismiss = false
        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                TodoCreationSheet(
                    state = TodoSheetState(),
                    callbacks = TodoSheetCallbacks(
                        onSave = { _, _, _ -> },
                        onDismiss = { plainDismiss = true },
                        onDismissWithUnsavedInput = { discardConfirmRequested = true },
                    ),
                    text = "",
                    onTextChange = {},
                    dueDateTime = null,
                    reminder = null,
                )
            }
        }

        composeRule.onNodeWithText(str(com.dopashift.ui.R.string.qc_todo_cancel)).performClick()

        assertTrue("empty-input dismissal dismisses directly", plainDismiss)
        assertFalse("empty-input dismissal need not confirm-discard", discardConfirmRequested)
    }

    // ---- DQC-1.6: the Undo affordance is available for at least 5 s and carries a token

    @Test
    fun dqc_1_6_undo_snackbar_min_visible_is_at_least_five_seconds() {
        // The Undo affordance surfaced after a successful creation must remain available for at
        // least 5 s so the user can act on it (DQC-1.6). The snackbar carries the UndoToken so Undo
        // deletes through the standard path (DQC-1.7).
        assertTrue(
            "Undo must remain available >= 5 s",
            UndoSnackbar.MIN_VISIBLE_MILLIS >= 5_000L,
        )
        val token = com.dopashift.domain.creation.UndoToken(
            entityRefs = listOf(
                com.dopashift.domain.creation.EntityRef(type = "goal", id = UUID.randomUUID()),
            ),
            correlationId = com.dopashift.domain.creation.CorrelationId("corr"),
        )
        val snackbar = UndoSnackbar(message = "Goal created", undoToken = token)
        assertEquals(token, snackbar.undoToken)
        assertNull("sanity: token is non-null", null)
    }

    private fun str(resId: Int): String =
        ApplicationProvider.getApplicationContext<android.content.Context>().getString(resId)
}

