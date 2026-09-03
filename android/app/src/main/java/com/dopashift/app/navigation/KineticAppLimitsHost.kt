package com.dopashift.app.navigation

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dopashift.app.R
import com.dopashift.domain.entity.LimitType as DomainLimitType
import com.dopashift.ui.interception.ExistingRuleDisplay
import com.dopashift.ui.interception.RuleAuthoringViewModel
import com.dopashift.ui.screens.AppLimitsActions
import com.dopashift.ui.screens.AppLimitsScreen
import com.dopashift.ui.state.AppLimitsUiState
import com.dopashift.ui.state.InstalledAppUi
import com.dopashift.ui.state.IntervalPreset
import com.dopashift.ui.state.LimitType
import com.dopashift.ui.state.RuleRowUi
import java.util.UUID

/**
 * The repetitive-interval preset minutes offered in the Kinetic App Limits screen (AUI-3.4),
 * including the short 1/3/5/10-minute presets alongside the longer 15/30/45/60-minute options. All
 * fall within the domain's valid 1..120-minute interval range.
 */
private val INTERVAL_PRESET_MINUTES = listOf(1, 3, 5, 10, 15, 30, 45, 60)

/**
 * Presentation bridge hosting the Kinetic [AppLimitsScreen] (AUI-3) as a pushed destination on the
 * [KineticRoutes.APP_LIMITS] route (spec `android-ui-upgrade`, task 11.1).
 *
 * It observes the existing [RuleAuthoringViewModel] (owned by the interception engine) and maps its
 * [com.dopashift.ui.interception.RuleAuthoringUiState] into the presentation-only [AppLimitsUiState]
 * the Kinetic screen renders: the search-filtered installed apps with their selection state
 * (AUI-3.1, AUI-3.2), the limit-type segmented toggle (AUI-3.3), the repetitive interval presets
 * (shown only in Repetitive mode by the screen — AUI-3.4), and the active rules list (AUI-3.5). All
 * gestures dispatch straight back to the view-model. No parallel data path is introduced.
 *
 * @param onBack pops back to the previous destination.
 * @param viewModel the existing rule-authoring view-model (Hilt-provided).
 */
@Composable
fun KineticAppLimitsHost(
    onBack: () -> Unit,
    viewModel: RuleAuthoringViewModel = hiltViewModel(),
) {
    val vmState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Surface save outcomes as Toasts (the Kinetic AppLimitsScreen has no snackbar host) and reset
    // the view-model flags so the message shows once per outcome.
    val saveSuccessMessage = context.getString(R.string.kinetic_rule_save_success)
    val requiredMessage = context.getString(R.string.kinetic_rule_save_required)
    LaunchedEffect(vmState.saveSuccess) {
        if (vmState.saveSuccess) {
            Toast.makeText(context, saveSuccessMessage, Toast.LENGTH_SHORT).show()
            viewModel.clearSaveSuccess()
        }
    }
    LaunchedEffect(vmState.requiredError) {
        if (vmState.requiredError) {
            Toast.makeText(context, requiredMessage, Toast.LENGTH_SHORT).show()
            viewModel.clearRequiredError()
        }
    }

    val uiLimitType = when (vmState.limitType) {
        DomainLimitType.Once -> LimitType.ONCE
        DomainLimitType.Repetitive -> LimitType.REPETITIVE
    }

    val selectedInterval = vmState.repetitiveIntervalMinutes
    val presets = INTERVAL_PRESET_MINUTES.map { minutes ->
        IntervalPreset(
            label = "$minutes min",
            minutes = minutes,
            selected = minutes == selectedInterval,
        )
    }

    val state = AppLimitsUiState(
        query = vmState.searchQuery,
        installedApps = vmState.displayedApps.map { app ->
            InstalledAppUi(
                packageName = app.packageName,
                label = app.label,
                selected = app.packageName in vmState.selectedPackages,
            )
        },
        limitType = uiLimitType,
        intervalPreset = presets.firstOrNull { it.selected },
        intervalPresets = presets,
        perAppLimits = vmState.perAppLimits,
        limitErrorPackage = if (vmState.limitError) vmState.limitErrorPackage else null,
        // The custom-interval field mirrors the retained interval; a preset that isn't in the
        // preset list (e.g. a typed 7 min) still shows here as the free-selection value.
        customIntervalMinutes = vmState.repetitiveIntervalMinutes,
        intervalError = vmState.intervalError,
        activeRules = vmState.existingRules.map { rule -> rule.toRuleRowUi(context) },
    )

    val actions = AppLimitsActions(
        onBack = onBack,
        onQueryChange = viewModel::onSearchQueryChanged,
        onClearQuery = { viewModel.onSearchQueryChanged("") },
        onAppSelected = { packageName, _ -> viewModel.toggleAppSelection(packageName) },
        onLimitTypeChange = { type ->
            viewModel.setLimitType(
                when (type) {
                    LimitType.ONCE -> DomainLimitType.Once
                    LimitType.REPETITIVE -> DomainLimitType.Repetitive
                }
            )
        },
        onIntervalPresetSelected = { preset ->
            preset.minutes?.let { viewModel.setRepetitiveInterval(it) }
        },
        onDailyLimitChange = { packageName, raw -> viewModel.setDailyLimit(packageName, raw) },
        onCustomIntervalChange = { raw -> viewModel.setRepetitiveInterval(raw) },
        onSave = viewModel::save,
        onRuleEdit = { /* edit opens the limit editor in the engine screen; no-op in bridge */ },
        onRulePauseToggle = { id ->
            id.toUuidOrNull()?.let(viewModel::pauseRuleForToday)
        },
        onRuleDelete = { id ->
            id.toUuidOrNull()?.let(viewModel::deleteRule)
        },
    )

    AppLimitsScreen(state = state, on = actions)
}

/**
 * Maps an [ExistingRuleDisplay] into a Kinetic [RuleRowUi] (AUI-3.5). The rule-type indicator copy
 * is resolved from localized app-module strings so no user-facing text is hardcoded in the bridge.
 */
private fun ExistingRuleDisplay.toRuleRowUi(
    context: android.content.Context,
): RuleRowUi = RuleRowUi(
    id = id.toString(),
    appLabel = appPackageName,
    packageName = appPackageName,
    ruleTypeLabel = context.getString(R.string.kinetic_rule_daily_limit) +
        ": ${dailyLimitMinutes}m",
    paused = pausedForToday,
)

/** Parses a string id into a [UUID], returning `null` when malformed. */
private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()
