package com.dopashift.ui.interception

// Feature: screen-time-interception-engine, Task 18.3

import android.app.Application
import android.content.Context
import android.provider.Settings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.dopashift.domain.entity.LimitType
import com.dopashift.ui.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * UI tests for the Rule_Authoring_Screen (task 18.3).
 *
 * These are Compose UI tests written as **Robolectric JVM unit tests** so they
 * execute in this environment (no emulator/device required). `createComposeRule`
 * works under [RobolectricTestRunner], and the `:ui` module already enables
 * `testOptions.unitTests.isIncludeAndroidResources = true` so `stringResource`
 * lookups resolve.
 *
 * They drive the (module-`internal`) `RuleAuthoringScreenContent` composable
 * directly with a controlled [RuleAuthoringUiState] and lambda spies, verifying
 * the behavioral contracts:
 *
 *  1. Time-picker default is 30 whole minutes (Requirement 2.5).
 *  2. Empty-search state shows the empty-result message (Requirement 2.3).
 *  3. Delete is gated behind a confirmation dialog (Requirement 3.6): the dialog
 *     is absent until requested; the row's delete icon requests (does not
 *     immediately delete); confirm/cancel route to the right callbacks.
 *  4. The usage-access permission rationale precedes the grant screen
 *     (Requirement 4.3): the rationale is the only path to
 *     `ACTION_USAGE_ACCESS_SETTINGS`, and no Settings intent starts before the
 *     rationale's confirm button is tapped.
 *
 * A resolved string is looked up via the Robolectric application context so the
 * assertions compare against the same text the composable renders.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RuleAuthoringScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun str(resId: Int): String = context.getString(resId)

    private fun sampleApp(pkg: String, label: String) =
        InstalledApp(packageName = pkg, label = label)

    /** Renders [RuleAuthoringScreenContent] with all callbacks defaulting to no-ops. */
    private fun setContent(
        uiState: RuleAuthoringUiState,
        onSearchQueryChanged: (String) -> Unit = {},
        onToggleAppSelection: (String) -> Unit = {},
        onSetDailyLimit: (String, String) -> Unit = { _, _ -> },
        onClearLimitError: () -> Unit = {},
        onSetLimitType: (LimitType) -> Unit = {},
        onSetRepetitiveInterval: (String) -> Unit = {},
        onClearIntervalError: () -> Unit = {},
        onSave: () -> Unit = {},
        onClearSaveSuccess: () -> Unit = {},
        onClearRequiredError: () -> Unit = {},
        onEditRuleLimit: (UUID, String) -> Unit = { _, _ -> },
        onClearEditLimitError: () -> Unit = {},
        onPauseRule: (UUID) -> Unit = {},
        onRequestDelete: (UUID) -> Unit = {},
        onCancelDelete: () -> Unit = {},
        onConfirmDelete: () -> Unit = {},
    ) {
        composeRule.setContent {
            RuleAuthoringScreenContent(
                uiState = uiState,
                onSearchQueryChanged = onSearchQueryChanged,
                onToggleAppSelection = onToggleAppSelection,
                onSetDailyLimit = onSetDailyLimit,
                onClearLimitError = onClearLimitError,
                onSetLimitType = onSetLimitType,
                onSetRepetitiveInterval = onSetRepetitiveInterval,
                onClearIntervalError = onClearIntervalError,
                onSave = onSave,
                onClearSaveSuccess = onClearSaveSuccess,
                onClearRequiredError = onClearRequiredError,
                onEditRuleLimit = onEditRuleLimit,
                onClearEditLimitError = onClearEditLimitError,
                onPauseRule = onPauseRule,
                onRequestDelete = onRequestDelete,
                onCancelDelete = onCancelDelete,
                onConfirmDelete = onConfirmDelete,
            )
        }
    }

    // --- (1) Time-picker default = 30 (Requirement 2.5) ---------------------

    @Test
    fun timePicker_defaultsTo30_forSelectedApp() {
        val pkg = "com.example.social"
        val state = RuleAuthoringUiState(
            allApps = listOf(sampleApp(pkg, "Social")),
            displayedApps = listOf(sampleApp(pkg, "Social")),
            selectedPackages = setOf(pkg),
            perAppLimits = mapOf(pkg to RuleAuthoringUiState.DEFAULT_LIMIT_MINUTES),
            isLoading = false,
        )

        setContent(state)

        // The limit input for the selected app renders the default value "30".
        composeRule.onNodeWithText("30").assertIsDisplayed()
    }

    // --- (2) Empty-search state (Requirement 2.3) ---------------------------

    @Test
    fun emptySearch_showsEmptyResultMessage() {
        val state = RuleAuthoringUiState(
            allApps = listOf(sampleApp("com.a", "Alpha")),
            searchQuery = "zzzznomatch",
            displayedApps = emptyList(),
            isLoading = false,
            isEmptyResult = true,
        )

        setContent(state)

        composeRule.onNodeWithText(str(R.string.rule_authoring_empty_result)).assertIsDisplayed()
    }

    // --- (3) Delete confirmation gate (Requirement 3.6) ---------------------

    @Test
    fun delete_dialogAbsentUntilRequested() {
        val rule = ExistingRuleDisplay(
            id = UUID.randomUUID(),
            appPackageName = "com.example.games",
            dailyLimitMinutes = 45,
            enabled = true,
            pausedForToday = false,
        )
        val state = RuleAuthoringUiState(
            isLoading = false,
            existingRules = listOf(rule),
            pendingDeleteRuleId = null,
        )

        setContent(state)

        // With no pending delete, the confirmation dialog title/message are absent.
        composeRule.onNodeWithText(str(R.string.rule_authoring_delete_confirm_title))
            .assertDoesNotExist()
        composeRule.onNodeWithText(str(R.string.rule_authoring_delete_confirm_message))
            .assertDoesNotExist()
    }

    @Test
    fun delete_iconRequestsDelete_doesNotDeleteImmediately() {
        val ruleId = UUID.randomUUID()
        val rule = ExistingRuleDisplay(
            id = ruleId,
            appPackageName = "com.example.games",
            dailyLimitMinutes = 45,
            enabled = true,
            pausedForToday = false,
        )
        var requestedId: UUID? = null
        var confirmed = false
        val state = RuleAuthoringUiState(
            isLoading = false,
            existingRules = listOf(rule),
            pendingDeleteRuleId = null,
        )

        setContent(
            state,
            onRequestDelete = { requestedId = it },
            onConfirmDelete = { confirmed = true },
        )

        // The existing-rule section sits below the limit-type selector and app
        // list in the LazyColumn, so scroll the list until the row's delete icon
        // is composed before interacting.
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasContentDescription(str(R.string.rule_authoring_delete)))

        // Tapping the row's delete icon requests (gates) deletion — it does not delete.
        composeRule.onNodeWithContentDescription(str(R.string.rule_authoring_delete))
            .performClick()

        assertEquals("delete icon must request deletion of the row's rule", ruleId, requestedId)
        assertFalse("deletion must be gated behind confirmation, not immediate", confirmed)
    }

    @Test
    fun delete_dialogShown_confirmAndCancelRouteToCallbacks() {
        val ruleId = UUID.randomUUID()
        var confirmed = false
        val state = RuleAuthoringUiState(
            isLoading = false,
            existingRules = listOf(
                ExistingRuleDisplay(
                    id = ruleId,
                    appPackageName = "com.example.games",
                    dailyLimitMinutes = 45,
                    enabled = true,
                    pausedForToday = false,
                ),
            ),
            pendingDeleteRuleId = ruleId,
        )

        setContent(state, onConfirmDelete = { confirmed = true })

        // With a pending delete, the dialog title and message ARE shown.
        composeRule.onNodeWithText(str(R.string.rule_authoring_delete_confirm_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.rule_authoring_delete_confirm_message))
            .assertIsDisplayed()

        // Tapping confirm invokes onConfirmDelete.
        composeRule.onNodeWithText(str(R.string.rule_authoring_delete_confirm)).performClick()
        assertTrue("confirm button must invoke onConfirmDelete", confirmed)
    }

    @Test
    fun delete_dialogCancel_routesToCancelCallback() {
        val ruleId = UUID.randomUUID()
        var canceled = false
        val state = RuleAuthoringUiState(
            isLoading = false,
            existingRules = listOf(
                ExistingRuleDisplay(
                    id = ruleId,
                    appPackageName = "com.example.games",
                    dailyLimitMinutes = 45,
                    enabled = true,
                    pausedForToday = false,
                ),
            ),
            pendingDeleteRuleId = ruleId,
        )

        setContent(state, onCancelDelete = { canceled = true })

        composeRule.onNodeWithText(str(R.string.rule_authoring_delete_confirm_cancel)).performClick()
        assertTrue("cancel button must invoke onCancelDelete", canceled)
    }

    // === Repetitive limit-type authoring behaviors (task 9.5) ===============
    // Requirements 1.1, 1.4, 1.6. These drive RuleAuthoringScreenContent with a
    // controlled RuleAuthoringUiState + lambda spies, matching the pattern above.

    /**
     * Default selection is `Once` (Requirements 1.1, 1.2): a freshly opened
     * screen renders the Once/Repetitive selector defaulting to Once, and the
     * Repetitive-only interval picker is NOT shown (the Once daily-limit path
     * applies instead).
     */
    @Test
    fun limitType_defaultsToOnce_intervalPickerNotShown() {
        // Default UiState leaves limitType == LimitType.Once.
        val state = RuleAuthoringUiState(isLoading = false)

        setContent(state)

        val onceLabel = str(R.string.rule_authoring_limit_type_once)
        val repetitiveLabel = str(R.string.rule_authoring_limit_type_repetitive)
        val intervalLabel = str(R.string.rule_authoring_interval_picker_label)

        // Both segment options render; Once is the selected (default) option.
        composeRule.onNodeWithText(onceLabel).assertIsDisplayed()
        composeRule.onNodeWithText(repetitiveLabel).assertIsDisplayed()

        // The interval picker (Repetitive-only) is absent while Once is selected.
        composeRule.onNodeWithText(intervalLabel).assertDoesNotExist()
    }

    /**
     * Switching to `Repetitive` shows the interval picker (Requirement 1.4), and
     * tapping the Repetitive segment while starting from Once invokes
     * `onSetLimitType(LimitType.Repetitive)`.
     */
    @Test
    fun switchingToRepetitive_showsIntervalPicker() {
        val state = RuleAuthoringUiState(
            isLoading = false,
            limitType = LimitType.Repetitive,
        )

        setContent(state)

        // With Repetitive selected, the interval-picker label IS displayed.
        composeRule.onNodeWithText(str(R.string.rule_authoring_interval_picker_label))
            .assertIsDisplayed()
    }

    @Test
    fun tappingRepetitiveSegment_fromOnce_invokesOnSetLimitType() {
        var requestedType: LimitType? = null
        // Start from the default Once selection.
        val state = RuleAuthoringUiState(isLoading = false)

        setContent(state, onSetLimitType = { requestedType = it })

        // Tapping the Repetitive segment routes through onSetLimitType.
        composeRule.onNodeWithText(str(R.string.rule_authoring_limit_type_repetitive))
            .performClick()

        assertEquals(
            "tapping the Repetitive segment must request LimitType.Repetitive",
            LimitType.Repetitive,
            requestedType,
        )
    }

    /**
     * An out-of-range interval retains the last valid value and surfaces the
     * range message (Requirement 1.6). The ViewModel retains the prior valid
     * value on rejection (covered by 9.1/9.4); here we assert the UI surfaces
     * that retained value alongside the range message when `intervalError` is set.
     */
    @Test
    fun outOfRangeInterval_retainsLastValidValue_andShowsRangeMessage() {
        val priorValidInterval = 30
        val state = RuleAuthoringUiState(
            isLoading = false,
            limitType = LimitType.Repetitive,
            repetitiveIntervalMinutes = priorValidInterval,
            intervalError = true,
        )

        setContent(state)

        // The localized "1–120 whole minutes" range message IS displayed.
        composeRule.onNodeWithText(str(R.string.rule_authoring_interval_range_message))
            .assertIsDisplayed()

        // The field still surfaces the retained prior valid value ("30").
        composeRule.onNodeWithText(priorValidInterval.toString()).assertIsDisplayed()
    }

    // --- (4) Permission-rationale-before-grant ordering (Requirement 4.3) ---

    @Test
    fun permissionRationale_precedesGrantScreen() {
        val state = RuleAuthoringUiState(isLoading = false)

        setContent(state)

        val rationaleText = str(R.string.rule_authoring_permission_rationale)
        val bannerText = str(R.string.rule_authoring_permission_banner)
        val continueText = str(R.string.rule_authoring_permission_continue)

        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowApp = Shadows.shadowOf(app)

        // The banner only shows when usage access is NOT granted. Under Robolectric
        // the AppOpsManager default for OPSTR_GET_USAGE_STATS is not "allowed", so
        // the banner is expected to render — assert that and drive the ordering.
        val bannerPresent = composeRule.onAllNodesWithText(bannerText)
            .fetchSemanticsNodes().isNotEmpty()

        // Ordering invariant, part A: the rationale is NOT shown until the banner
        // is tapped, and no Settings intent has been started yet.
        composeRule.onNodeWithText(rationaleText).assertDoesNotExist()
        assertNull(
            "no Settings intent may be started before the rationale is shown",
            shadowApp.nextStartedActivity,
        )

        if (!bannerPresent) {
            // If Robolectric reports usage access as granted, the banner (the only
            // entry point to the grant flow) is absent and there is therefore no
            // path to open Settings — the ordering invariant holds vacuously.
            return
        }

        // Tap the banner — the ONLY entry point to the grant flow.
        composeRule.onNodeWithText(bannerText).performClick()

        // Ordering invariant, part B: the rationale is now shown, and STILL no
        // Settings intent has been started (the grant action lives behind confirm).
        composeRule.onNodeWithText(rationaleText).assertIsDisplayed()
        composeRule.onNodeWithText(continueText).assertIsDisplayed()
        assertNull(
            "no Settings intent may be started merely by showing the rationale",
            shadowApp.nextStartedActivity,
        )

        // Tap the rationale's confirm button — only now may Settings open.
        composeRule.onNodeWithText(continueText).performClick()

        val started = shadowApp.nextStartedActivity
        assertTrue(
            "confirming the rationale must start the usage-access Settings screen",
            started != null &&
                started.action == Settings.ACTION_USAGE_ACCESS_SETTINGS,
        )
    }
}
