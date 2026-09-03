package com.dopashift.ui.screens

// Feature: android-ui-upgrade, Task 7.2

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.dopashift.ui.R
import com.dopashift.ui.state.AppLimitsUiState
import com.dopashift.ui.state.InstalledAppUi
import com.dopashift.ui.state.IntervalPreset
import com.dopashift.ui.state.LimitType
import com.dopashift.ui.state.RuleRowUi
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
 * Compose UI tests for the **App Limits & Rules** screen (spec `android-ui-upgrade`, task 7.2,
 * AUI-3.1–3.5; satisfies AUI-9.5).
 *
 * These are Compose UI tests written as **Robolectric JVM unit tests** so they run in this
 * environment (no emulator/device required), mirroring
 * `com.dopashift.ui.interception.RuleAuthoringScreenTest`. `createComposeRule` works under
 * [RobolectricTestRunner], and the `:ui` module sets
 * `testOptions.unitTests.isIncludeAndroidResources = true` so `stringResource` lookups resolve.
 *
 * The screen is presentation-only: it renders the [AppLimitsUiState] it is given (the view-model
 * owns filtering) and forwards intent through [AppLimitsActions]. Each test therefore drives
 * [AppLimitsScreen] with a controlled state plus lambda spies and asserts the render + the
 * dispatched callback. All content is wrapped in `DopaShiftTheme(themeMode = ThemeMode.DARK)`.
 */
@RunWith(RobolectricTestRunner::class)
// A tall viewport qualifier so the whole App Limits LazyColumn (search bar, app list, segmented
// pill, interval section, and the Active Rules list whose rows stack their details above a second
// row of Edit/Pause/Delete controls) lays out with room to scroll. The default Robolectric device
// is too short for a rule row's action controls to settle fully in the viewport, so a click at the
// control's center would not reliably reach onClick; the taller viewport keeps them fully in view.
@Config(sdk = [33], qualifiers = "w411dp-h2000dp")
class AppLimitsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun str(resId: Int): String = context.getString(resId)
    private fun str(resId: Int, vararg args: Any): String = context.getString(resId, *args)

    /** A minimal state with sensible defaults; individual tests override the fields they exercise. */
    private fun state(
        query: String = "",
        installedApps: List<InstalledAppUi> = emptyList(),
        limitType: LimitType = LimitType.ONCE,
        intervalPreset: IntervalPreset? = null,
        intervalPresets: List<IntervalPreset> = emptyList(),
        activeRules: List<RuleRowUi> = emptyList(),
    ) = AppLimitsUiState(
        query = query,
        installedApps = installedApps,
        limitType = limitType,
        intervalPreset = intervalPreset,
        intervalPresets = intervalPresets,
        activeRules = activeRules,
    )

    /** Renders [AppLimitsScreen] with all callbacks defaulting to no-ops. */
    private fun setContent(
        uiState: AppLimitsUiState,
        onQueryChange: (String) -> Unit = {},
        onClearQuery: () -> Unit = {},
        onAppSelected: (String, Boolean) -> Unit = { _, _ -> },
        onLimitTypeChange: (LimitType) -> Unit = {},
        onIntervalPresetSelected: (IntervalPreset) -> Unit = {},
        onDailyLimitChange: (String, String) -> Unit = { _, _ -> },
        onCustomIntervalChange: (String) -> Unit = {},
        onSave: () -> Unit = {},
        onRuleEdit: (String) -> Unit = {},
        onRulePauseToggle: (String) -> Unit = {},
        onRuleDelete: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                AppLimitsScreen(
                    state = uiState,
                    on = AppLimitsActions(
                        onBack = {},
                        onQueryChange = onQueryChange,
                        onClearQuery = onClearQuery,
                        onAppSelected = onAppSelected,
                        onLimitTypeChange = onLimitTypeChange,
                        onIntervalPresetSelected = onIntervalPresetSelected,
                        onDailyLimitChange = onDailyLimitChange,
                        onCustomIntervalChange = onCustomIntervalChange,
                        onSave = onSave,
                        onRuleEdit = onRuleEdit,
                        onRulePauseToggle = onRulePauseToggle,
                        onRuleDelete = onRuleDelete,
                    ),
                )
            }
        }
    }

    private fun sampleApp(pkg: String, label: String, selected: Boolean = false) =
        InstalledAppUi(packageName = pkg, label = label, selected = selected)

    // --- AUI-3.1: search — renders given (filtered) state; typing + clear route to callbacks ---

    @Test
    fun aui_3_1_search_rendersGivenFilteredAppsAndTypingInvokesOnQueryChange() {
        // The screen renders exactly the (already-filtered) installedApps it is given.
        val state = state(
            query = "so",
            installedApps = listOf(sampleApp("com.example.social", "Social")),
        )
        var typed: String? = null

        setContent(state, onQueryChange = { typed = it })

        // The displayed list matches the state: the one filtered app is shown. The app row's
        // label is hosted by a MomentumCheckbox that exposes an aggregate content description
        // ("<label>, not completed") and its package as a plain text sub-label, so we assert on
        // the (rendered) package text.
        composeRule.onNodeWithText("com.example.social").assertExists()
        // An app that is NOT in the filtered state is absent.
        composeRule.onNodeWithText("com.example.video").assertDoesNotExist()

        // Typing in the search field forwards to onQueryChange (filtering is view-model owned).
        // The search field renders its current query "so" as editable text; append to it.
        composeRule.onNodeWithText("so").performTextInput("c")
        assertTrue("typing in the search field must invoke onQueryChange", typed != null)
    }

    @Test
    fun aui_3_1_search_clearControlShownForNonEmptyQueryInvokesOnClearQuery() {
        var cleared = false
        // A non-empty query surfaces the clear (X) control.
        setContent(state(query = "social"), onClearQuery = { cleared = true })

        composeRule
            .onNodeWithContentDescription(str(R.string.aui_limits_search_clear_content_description))
            .performClick()

        assertTrue("tapping the clear control must invoke onClearQuery", cleared)
    }

    @Test
    fun aui_3_1_search_clearControlHiddenWhenQueryEmpty() {
        // With an empty query the clear (X) control is not rendered.
        setContent(state(query = ""))

        composeRule
            .onNodeWithContentDescription(str(R.string.aui_limits_search_clear_content_description))
            .assertDoesNotExist()
    }

    // --- AUI-3.2: selection checkbox toggles via onAppSelected(pkg, newState) ---

    @Test
    fun aui_3_2_selectionCheckbox_togglingInvokesOnAppSelectedWithNewState() {
        val pkg = "com.example.social"
        val app = sampleApp(pkg, "Social", selected = false)
        var selectedPkg: String? = null
        var newState: Boolean? = null

        setContent(
            state(installedApps = listOf(app)),
            onAppSelected = { p, s -> selectedPkg = p; newState = s },
        )

        // The MomentumCheckbox exposes an aggregate content description "<label>, not completed"
        // for an unchecked row (see MomentumCheckbox). Tapping it toggles to checked (true).
        composeRule.onNodeWithContentDescription("Social, not completed").performClick()

        assertEquals("checkbox must report the row's package", pkg, selectedPkg)
        assertEquals("toggling an unchecked row must report the new state (checked)", true, newState)
    }

    // --- AUI-3.3: segmented toggle presents both options; tapping Repetitive routes REPETITIVE ---

    @Test
    fun aui_3_3_segmentedToggle_bothOptionsPresentAndTappingRepetitiveInvokesOnLimitTypeChange() {
        var requested: LimitType? = null
        // Start from ONCE so tapping Repetitive is a real transition.
        setContent(state(limitType = LimitType.ONCE), onLimitTypeChange = { requested = it })

        val onceLabel = str(R.string.aui_limits_type_once)
        val repetitiveLabel = str(R.string.aui_limits_type_repetitive)

        // Both segment options are present.
        composeRule.onNodeWithText(onceLabel).assertIsDisplayed()
        composeRule.onNodeWithText(repetitiveLabel).assertIsDisplayed()

        // Tapping Repetitive dispatches LimitType.REPETITIVE.
        composeRule.onNodeWithText(repetitiveLabel).performClick()
        assertEquals(
            "tapping the Repetitive segment must request LimitType.REPETITIVE",
            LimitType.REPETITIVE,
            requested,
        )
    }

    // --- AUI-3.4 (key AUI-9.5): interval presets + tip visible ONLY in REPETITIVE ---

    @Test
    fun aui_3_4_presetsAndTip_notDisplayedInOnceMode() {
        val presets = listOf(
            IntervalPreset("15 min", 15),
            IntervalPreset("30 min", 30, selected = true),
        )
        // In ONCE mode the interval header, chips, and tip must all be absent.
        setContent(state(limitType = LimitType.ONCE, intervalPresets = presets))

        composeRule.onNodeWithText(str(R.string.aui_limits_interval_tip)).assertDoesNotExist()
        composeRule.onNodeWithText(str(R.string.aui_limits_interval_header)).assertDoesNotExist()
        composeRule.onNodeWithText("15 min").assertDoesNotExist()
        composeRule.onNodeWithText("30 min").assertDoesNotExist()
    }

    @Test
    fun aui_3_4_presetsAndTip_displayedInRepetitiveMode() {
        val presets = listOf(
            IntervalPreset("15 min", 15),
            IntervalPreset("30 min", 30, selected = true),
        )
        // In REPETITIVE mode the interval tip and preset chips are shown.
        setContent(
            state(
                limitType = LimitType.REPETITIVE,
                intervalPreset = presets[1],
                intervalPresets = presets,
            ),
        )

        // The interval section sits below the segmented pill in the LazyColumn; scroll to it.
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasTextResource(R.string.aui_limits_interval_tip))

        composeRule.onNodeWithText(str(R.string.aui_limits_interval_tip)).assertIsDisplayed()
        composeRule.onNodeWithText("15 min").assertIsDisplayed()
        composeRule.onNodeWithText("30 min").assertIsDisplayed()
    }

    @Test
    fun aui_3_4_presetChip_tapInvokesOnIntervalPresetSelected() {
        val fifteen = IntervalPreset("15 min", 15)
        val presets = listOf(fifteen, IntervalPreset("30 min", 30, selected = true))
        var selected: IntervalPreset? = null

        setContent(
            state(
                limitType = LimitType.REPETITIVE,
                intervalPreset = presets[1],
                intervalPresets = presets,
            ),
            onIntervalPresetSelected = { selected = it },
        )

        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasTextResource(R.string.aui_limits_interval_tip))
        composeRule.onNodeWithText("15 min").performClick()

        assertEquals("tapping a preset chip must dispatch that preset", fifteen, selected)
    }

    // --- AUI-3.5: rule rows show details; Edit/Pause/Delete dispatch to callbacks ---

    @Test
    fun aui_3_5_ruleRow_displaysLabelAndTypeAndControlsDispatch() {
        val ruleId = "rule-1"
        val appLabel = "Social"
        val ruleTypeLabel = "Repetitive: Every 30m"
        val rule = RuleRowUi(
            id = ruleId,
            appLabel = appLabel,
            packageName = "com.example.social",
            ruleTypeLabel = ruleTypeLabel,
            paused = false,
        )
        var editedId: String? = null
        var pausedId: String? = null
        var deletedId: String? = null

        setContent(
            state(activeRules = listOf(rule)),
            onRuleEdit = { editedId = it },
            onRulePauseToggle = { pausedId = it },
            onRuleDelete = { deletedId = it },
        )

        // The active rule renders its app label and rule-type indicator. Scroll the row into
        // composition, then assert the nodes exist (they may sit below the fold in the LazyColumn).
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasTextResource(R.string.aui_limits_active_rules_header))
        composeRule.onNodeWithText(appLabel).assertExists()
        composeRule.onNodeWithText(ruleTypeLabel).assertExists()

        // Edit control dispatches to onRuleEdit with the rule id.
        composeRule.onNodeWithContentDescription(
            str(R.string.aui_limits_rule_edit_content_description, appLabel),
        ).performClick()
        assertEquals("Edit must dispatch the row's rule id", ruleId, editedId)

        // Pause control dispatches to onRulePauseToggle (unpaused rule shows the Pause action).
        composeRule.onNodeWithContentDescription(
            str(R.string.aui_limits_rule_pause_content_description, appLabel),
        ).performClick()
        assertEquals("Pause must dispatch the row's rule id", ruleId, pausedId)

        // Delete control dispatches to onRuleDelete with the rule id.
        composeRule.onNodeWithContentDescription(
            str(R.string.aui_limits_rule_delete_content_description, appLabel),
        ).performClick()
        assertEquals("Delete must dispatch the row's rule id", ruleId, deletedId)

        // No callback fires the wrong id / none was accidentally left unset for the others.
        assertFalse("edit id must be set", editedId == null)
    }

    /** Matcher on a resolved string resource, used with `performScrollToNode`. */
    private fun hasTextResource(resId: Int) =
        androidx.compose.ui.test.hasText(str(resId))
}
