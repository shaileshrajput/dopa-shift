package com.dopashift.ui.interception

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dopashift.domain.entity.LimitType
import com.dopashift.ui.R
import com.dopashift.ui.theme.DopaShiftTheme
import androidx.compose.ui.unit.Dp
import java.util.UUID

/**
 * Max height of the installed-app selection list before it scrolls internally. Sized to show
 * roughly three app rows (each an ~72dp card plus row spacing) so the search field stays pinned
 * above and the existing-rules section below remains reachable without long outer scrolling.
 */
private val APP_LIST_MAX_HEIGHT: Dp = 240.dp

/**
 * Rule_Authoring_Screen (task 18.2).
 *
 * A standalone Compose (Material 3) screen for authoring per-app screen-time
 * limits. It binds to [RuleAuthoringViewModel] and renders:
 * - An installed-app list with a search field and empty-result state
 *   (Requirements 2.2, 2.3).
 * - A per-app daily-limit input defaulting to 30 whole minutes, showing the
 *   localized "1–480 whole minutes" range message on rejection (Requirement 2.5).
 * - A Save control with save-success and required-app feedback (Requirements
 *   2.9, 2.10).
 * - The user's existing Rules with edit/pause/delete controls (Requirement 3.2);
 *   deletion is gated behind a confirmation [AlertDialog] (Requirement 3.6).
 * - A usage-access permission rationale shown BEFORE the PACKAGE_USAGE_STATS
 *   grant screen is opened (Requirements 4.2, 4.3). The rationale dialog is the
 *   only path to `Settings.ACTION_USAGE_ACCESS_SETTINGS`, guaranteeing the
 *   rationale precedes the grant screen.
 *
 * The usage-access status check and Settings intent reuse the same mechanism as
 * onboarding's `PermissionSetupViewModel` (AppOpsManager OPSTR_GET_USAGE_STATS +
 * `Settings.ACTION_USAGE_ACCESS_SETTINGS`) for consistency.
 *
 * The screen is intentionally standalone (no navigation entry added) so it can
 * be wired into the nav graph in a later change without risking existing routes.
 */
@Composable
fun RuleAuthoringScreen(
    viewModel: RuleAuthoringViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    RuleAuthoringScreenContent(
        uiState = uiState,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onToggleAppSelection = viewModel::toggleAppSelection,
        onSetDailyLimit = viewModel::setDailyLimit,
        onClearLimitError = viewModel::clearLimitError,
        onSetLimitType = viewModel::setLimitType,
        onSetRepetitiveInterval = viewModel::setRepetitiveInterval,
        onClearIntervalError = viewModel::clearIntervalError,
        onSave = viewModel::save,
        onClearSaveSuccess = viewModel::clearSaveSuccess,
        onClearRequiredError = viewModel::clearRequiredError,
        onEditRuleLimit = viewModel::editRuleLimit,
        onClearEditLimitError = viewModel::clearEditLimitError,
        onPauseRule = viewModel::pauseRuleForToday,
        onRequestDelete = viewModel::requestDelete,
        onCancelDelete = viewModel::cancelDelete,
        onConfirmDelete = viewModel::confirmDelete,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RuleAuthoringScreenContent(
    uiState: RuleAuthoringUiState,
    onSearchQueryChanged: (String) -> Unit,
    onToggleAppSelection: (String) -> Unit,
    onSetDailyLimit: (String, String) -> Unit,
    onClearLimitError: () -> Unit,
    onSetLimitType: (LimitType) -> Unit,
    onSetRepetitiveInterval: (String) -> Unit,
    onClearIntervalError: () -> Unit,
    onSave: () -> Unit,
    onClearSaveSuccess: () -> Unit,
    onClearRequiredError: () -> Unit,
    onEditRuleLimit: (UUID, String) -> Unit,
    onClearEditLimitError: () -> Unit,
    onPauseRule: (UUID) -> Unit,
    onRequestDelete: (UUID) -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val saveSuccessMessage = stringResource(R.string.rule_authoring_save_success)
    val requiredMessage = stringResource(R.string.rule_authoring_required_app_message)

    // Rationale dialog must be shown BEFORE opening the usage-access grant screen.
    var showRationale by rememberSaveable { mutableStateOf(false) }
    var hasUsageAccess by rememberSaveable { mutableStateOf(hasUsageStatsPermission(context)) }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            snackbarHostState.showSnackbar(saveSuccessMessage)
            onClearSaveSuccess()
        }
    }

    LaunchedEffect(uiState.requiredError) {
        if (uiState.requiredError) {
            snackbarHostState.showSnackbar(requiredMessage)
            onClearRequiredError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.rule_authoring_title)) })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Usage-access permission banner — only entry point to the grant flow.
            if (!hasUsageAccess) {
                PermissionBanner(onClick = { showRationale = true })
            }

            SearchField(
                query = uiState.searchQuery,
                onQueryChanged = onSearchQueryChanged,
            )

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = DopaShiftTheme.spacing.gutter),
                    verticalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.base),
                ) {
                    // --- Limit-type selector + repetitive interval picker ---
                    // These apply to the Rule being authored (one Rule per app,
                    // OQ-3), so they sit above the app list rather than inside a
                    // single app row (Requirements 1.1, 1.2, 1.4, 1.6).
                    item(key = "limit_type_selector") {
                        LimitTypeSelector(
                            selected = uiState.limitType,
                            onSelect = onSetLimitType,
                        )
                    }

                    if (uiState.limitType == LimitType.Repetitive) {
                        item(key = "interval_picker") {
                            IntervalInput(
                                value = uiState.repetitiveIntervalMinutes,
                                isError = uiState.intervalError,
                                onValueChanged = onSetRepetitiveInterval,
                                onClearError = onClearIntervalError,
                            )
                        }
                    }

                    // --- App selection section ---
                    item(key = "select_header") {
                        SectionHeader(stringResource(R.string.rule_authoring_select_apps_header))
                    }

                    if (uiState.isEmptyResult) {
                        item(key = "empty_result") {
                            EmptyResult()
                        }
                    } else {
                        // Cap the installed-app list to ~3 rows in a self-scrolling region so the
                        // search bar stays pinned above and the existing-rules section below stays
                        // reachable. A nested LazyColumn inside a LazyColumn is disallowed, so the
                        // inner list is a bounded verticalScroll Column of AppSelectionRow.
                        item(key = "app_list") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = APP_LIST_MAX_HEIGHT)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(
                                    DopaShiftTheme.spacing.base,
                                ),
                            ) {
                                uiState.displayedApps.forEach { app ->
                                    AppSelectionRow(
                                        app = app,
                                        selected = app.packageName in uiState.selectedPackages,
                                        showDailyLimit = uiState.limitType == LimitType.Once,
                                        limit = uiState.perAppLimits[app.packageName],
                                        limitError = uiState.limitError &&
                                            uiState.limitErrorPackage == app.packageName,
                                        onToggle = { onToggleAppSelection(app.packageName) },
                                        onLimitChanged = { raw ->
                                            onSetDailyLimit(app.packageName, raw)
                                        },
                                        onClearLimitError = onClearLimitError,
                                    )
                                }
                            }
                        }
                    }

                    item(key = "save_button") {
                        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
                        Button(
                            onClick = onSave,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.rule_authoring_save))
                        }
                    }

                    // --- Existing-rule section (Requirement 3.2) ---
                    item(key = "existing_header") {
                        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.gutter))
                        SectionHeader(stringResource(R.string.rule_authoring_existing_header))
                    }

                    if (uiState.existingRules.isEmpty()) {
                        item(key = "no_existing") {
                            Text(
                                text = stringResource(R.string.rule_authoring_no_existing),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(uiState.existingRules, key = { it.id }) { rule ->
                            ExistingRuleRow(
                                rule = rule,
                                editError = uiState.editLimitError &&
                                    uiState.editErrorRuleId == rule.id,
                                onEditLimit = { raw -> onEditRuleLimit(rule.id, raw) },
                                onClearEditError = onClearEditLimitError,
                                onPause = { onPauseRule(rule.id) },
                                onDelete = { onRequestDelete(rule.id) },
                            )
                        }
                    }

                    item(key = "bottom_spacer") {
                        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.stackLg))
                    }
                }
            }
        }
    }

    // Delete confirmation dialog (Requirement 3.6).
    if (uiState.pendingDeleteRuleId != null) {
        AlertDialog(
            onDismissRequest = onCancelDelete,
            title = { Text(stringResource(R.string.rule_authoring_delete_confirm_title)) },
            text = { Text(stringResource(R.string.rule_authoring_delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = onConfirmDelete) {
                    Text(stringResource(R.string.rule_authoring_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelDelete) {
                    Text(stringResource(R.string.rule_authoring_delete_confirm_cancel))
                }
            },
        )
    }

    // Permission rationale dialog — MUST be shown before the grant screen opens
    // (Requirements 4.2, 4.3).
    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = null,
                )
            },
            title = { Text(stringResource(R.string.rule_authoring_permission_rationale_title)) },
            text = { Text(stringResource(R.string.rule_authoring_permission_rationale)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRationale = false
                        context.startActivity(createUsageAccessSettingsIntent())
                    },
                ) {
                    Text(stringResource(R.string.rule_authoring_permission_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }) {
                    Text(stringResource(R.string.rule_authoring_permission_cancel))
                }
            },
        )
    }
}

// === Sub-composables ===

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionBanner(onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = DopaShiftTheme.spacing.gutter,
                vertical = DopaShiftTheme.spacing.stackSm,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(DopaShiftTheme.spacing.base))
            Text(
                text = stringResource(R.string.rule_authoring_permission_banner),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = DopaShiftTheme.spacing.gutter,
                vertical = DopaShiftTheme.spacing.base,
            ),
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        placeholder = { Text(stringResource(R.string.rule_authoring_search_hint)) },
        singleLine = true,
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun EmptyResult() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = DopaShiftTheme.spacing.stackLg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.rule_authoring_empty_result),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AppSelectionRow(
    app: InstalledApp,
    selected: Boolean,
    showDailyLimit: Boolean,
    limit: Int?,
    limitError: Boolean,
    onToggle: () -> Unit,
    onLimitChanged: (String) -> Unit,
    onClearLimitError: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(modifier = Modifier.padding(DopaShiftTheme.spacing.stackSm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggle() },
                )
                Spacer(modifier = Modifier.width(DopaShiftTheme.spacing.base))
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            if (selected && showDailyLimit) {
                Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
                LimitInput(
                    value = limit,
                    isError = limitError,
                    onValueChanged = onLimitChanged,
                    onClearError = onClearLimitError,
                )
            }
        }
    }
}

@Composable
private fun LimitInput(
    value: Int?,
    isError: Boolean,
    onValueChanged: (String) -> Unit,
    onClearError: () -> Unit,
) {
    // Track the raw text locally so an invalid entry stays visible for correction
    // while the ViewModel retains the last valid value.
    var text by rememberSaveable(value) {
        mutableStateOf((value ?: RuleAuthoringUiState.DEFAULT_LIMIT_MINUTES).toString())
    }

    OutlinedTextField(
        value = text,
        onValueChange = { new ->
            text = new
            if (isError) onClearError()
            onValueChanged(new)
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.rule_authoring_time_picker_label)) },
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = if (isError) {
            { Text(stringResource(R.string.rule_authoring_limit_range_message)) }
        } else {
            null
        },
    )
}

/**
 * Mandatory Limit_Type selector offering the two mutually exclusive options
 * `Once` and `Repetitive`, defaulting to `Once` via the UiState default
 * (Requirements 1.1, 1.2). Rendered as a Material 3 single-choice segmented
 * button row so exactly one option is always selected. The selector applies to
 * the Rule being authored (one Rule per app per OQ-3), so it sits above the
 * per-app selection rather than inside a single app row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LimitTypeSelector(
    selected: LimitType,
    onSelect: (LimitType) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.rule_authoring_limit_type_label))
        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
        val options = listOf(
            LimitType.Once to R.string.rule_authoring_limit_type_once,
            LimitType.Repetitive to R.string.rule_authoring_limit_type_repetitive,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (type, labelRes) ->
                SegmentedButton(
                    selected = selected == type,
                    onClick = { onSelect(type) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = options.size,
                    ),
                ) {
                    Text(stringResource(labelRes))
                }
            }
        }
    }
}

/**
 * Repetitive_Interval picker shown only when [LimitType.Repetitive] is selected
 * (Requirement 1.4). Accepts a whole number of minutes in 1..120; on rejection
 * the field is marked as an error and the localized "Interval must be between 1
 * and 120 whole minutes" message is shown (Requirement 1.6). Mirrors
 * [LimitInput] for consistency (Number keyboard, error-clearing on edit).
 */
@Composable
private fun IntervalInput(
    value: Int?,
    isError: Boolean,
    onValueChanged: (String) -> Unit,
    onClearError: () -> Unit,
) {
    // Track raw text locally so an invalid entry stays visible for correction
    // while the ViewModel retains the last valid interval.
    var text by rememberSaveable(value) {
        mutableStateOf(value?.toString() ?: "")
    }

    OutlinedTextField(
        value = text,
        onValueChange = { new ->
            text = new
            if (isError) onClearError()
            onValueChanged(new)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = DopaShiftTheme.spacing.base),
        label = { Text(stringResource(R.string.rule_authoring_interval_picker_label)) },
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = if (isError) {
            { Text(stringResource(R.string.rule_authoring_interval_range_message)) }
        } else {
            null
        },
    )
}

@Composable
private fun ExistingRuleRow(
    rule: ExistingRuleDisplay,
    editError: Boolean,
    onEditLimit: (String) -> Unit,
    onClearEditError: () -> Unit,
    onPause: () -> Unit,
    onDelete: () -> Unit,
) {
    var editText by rememberSaveable(rule.id, rule.dailyLimitMinutes) {
        mutableStateOf(rule.dailyLimitMinutes.toString())
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(DopaShiftTheme.spacing.stackSm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.appPackageName,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val statusRes = when {
                        rule.pausedForToday -> R.string.rule_authoring_paused_label
                        rule.enabled -> R.string.rule_authoring_enabled_label
                        else -> R.string.rule_authoring_disabled_label
                    }
                    Text(
                        text = stringResource(statusRes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.rule_authoring_delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))

            OutlinedTextField(
                value = editText,
                onValueChange = { new ->
                    editText = new
                    if (editError) onClearEditError()
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.rule_authoring_time_picker_label)) },
                isError = editError,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = if (editError) {
                    { Text(stringResource(R.string.rule_authoring_limit_range_message)) }
                } else {
                    null
                },
            )

            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.base),
            ) {
                Button(
                    onClick = { onEditLimit(editText) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.rule_authoring_edit))
                }
                OutlinedButton(
                    onClick = onPause,
                    enabled = !rule.pausedForToday,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.rule_authoring_pause))
                }
            }
        }
    }
}

// === Usage-access permission helpers ===

/**
 * Builds the intent that opens the system Usage Access settings screen. Mirrors
 * onboarding's `PermissionSetupViewModel.createUsageStatsSettingsIntent()` so
 * the grant path is consistent across the app (Requirement 4.2).
 */
private fun createUsageAccessSettingsIntent(): Intent =
    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

/**
 * Checks whether PACKAGE_USAGE_STATS is currently granted, using the same
 * AppOpsManager mechanism as onboarding's `PermissionSetupViewModel`.
 *
 * On several OEM ROMs (Vivo Funtouch/OriginOS, Xiaomi MIUI, Oppo ColorOS) the op returns
 * [AppOpsManager.MODE_DEFAULT] even when Usage Access is granted; in that ambiguous case we
 * probe [UsageStatsManager] directly so the banner does not falsely keep showing "grant usage
 * access" after the user has already granted it.
 */
private fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
    }
    return when (mode) {
        AppOpsManager.MODE_ALLOWED -> true
        AppOpsManager.MODE_DEFAULT -> canQueryUsageStats(context)
        else -> false
    }
}

/**
 * Disambiguates an [AppOpsManager.MODE_DEFAULT] usage-stats result by querying the last hour of
 * usage stats. A non-empty result (and no [SecurityException]) means Usage Access is granted.
 */
private fun canQueryUsageStats(context: Context): Boolean {
    return try {
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            now - 60L * 60L * 1000L,
            now,
        )
        !stats.isNullOrEmpty()
    } catch (e: SecurityException) {
        false
    } catch (e: Exception) {
        false
    }
}
