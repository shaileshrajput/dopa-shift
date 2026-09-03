package com.dopashift.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.dopashift.ui.R
import com.dopashift.ui.components.KineticCard
import com.dopashift.ui.components.KineticChip
import com.dopashift.ui.components.MomentumCheckbox
import com.dopashift.ui.components.SegmentedPill
import com.dopashift.ui.state.AppLimitsUiState
import com.dopashift.ui.state.InstalledAppUi
import com.dopashift.ui.state.IntervalPreset
import com.dopashift.ui.state.LimitType
import com.dopashift.ui.state.RuleRowUi
import com.dopashift.ui.theme.DopaShiftPillShape
import com.dopashift.ui.theme.DopaShiftTheme
import com.dopashift.ui.theme.DopaShiftTokens
import com.dopashift.ui.theme.ThemeMode

/**
 * Callbacks dispatched by [AppLimitsScreen] to the owning `screen-time-interception-engine`
 * view-model (spec `android-ui-upgrade`, AUI-3). This screen is presentation-only: it renders the
 * supplied [AppLimitsUiState] and forwards user intent through these callbacks, holding no
 * business logic of its own (design → "presentation-only, no parallel data path").
 *
 * @property onBack invoked when the top-app-bar back arrow is tapped, popping back to the screen
 *   that opened App Limits.
 * @property onQueryChange invoked as the user types in the search bar; the view-model is expected
 *   to re-emit [AppLimitsUiState.installedApps] filtered in real time (AUI-3.1).
 * @property onClearQuery invoked when the clear (`X`) control is tapped (AUI-3.1).
 * @property onAppSelected invoked with an installed app's package name and its new checked state
 *   when its selection checkbox is toggled (AUI-3.2).
 * @property onLimitTypeChange invoked with the newly selected authoring mode when the segmented
 *   pill changes (AUI-3.3).
 * @property onIntervalPresetSelected invoked with the chosen interval preset (AUI-3.4).
 * @property onDailyLimitChange invoked with a package name and the raw daily-limit text as the user
 *   edits a selected app's daily-limit field (AUI-3.2); the view-model validates/retains it.
 * @property onCustomIntervalChange invoked with the raw text of the free-selection "Custom
 *   interval" field in Repetitive mode (AUI-3.4); the view-model validates/retains it.
 * @property onSave invoked when the Save control is tapped; persists the authored rule(s) for every
 *   selected app (AUI-3.2, Requirement 2.9).
 * @property onRuleEdit invoked with a rule id when its Edit control is tapped (AUI-3.5).
 * @property onRulePauseToggle invoked with a rule id when its Pause/Resume control is tapped
 *   (AUI-3.5).
 * @property onRuleDelete invoked with a rule id when its Delete control is tapped (AUI-3.5).
 */
data class AppLimitsActions(
    val onBack: () -> Unit,
    val onQueryChange: (String) -> Unit,
    val onClearQuery: () -> Unit,
    val onAppSelected: (packageName: String, selected: Boolean) -> Unit,
    val onLimitTypeChange: (LimitType) -> Unit,
    val onIntervalPresetSelected: (IntervalPreset) -> Unit,
    val onDailyLimitChange: (packageName: String, rawMinutes: String) -> Unit,
    val onCustomIntervalChange: (rawMinutes: String) -> Unit,
    val onSave: () -> Unit,
    val onRuleEdit: (id: String) -> Unit,
    val onRulePauseToggle: (id: String) -> Unit,
    val onRuleDelete: (id: String) -> Unit,
)

/**
 * The two-segment ordering for the limit-type [SegmentedPill] (AUI-3.3). Index 0 is
 * [LimitType.ONCE] ("Once (Daily Limit)"), index 1 is [LimitType.REPETITIVE] ("Repetitive
 * (Interval)"). Kept as a single source of truth so index↔enum mapping stays symmetric.
 */
private val LIMIT_TYPE_ORDER = listOf(LimitType.ONCE, LimitType.REPETITIVE)

/**
 * The **App Limits & Rules** authoring screen (AUI-3).
 *
 * Renders — top to bottom — a search bar filtering installed apps in real time (AUI-3.1), the
 * (already-filtered) installed-app list with a high-contrast 48dp selection checkbox per row
 * (AUI-3.2), a two-option [SegmentedPill] toggling [LimitType] (AUI-3.3), interval preset chips
 * plus an explanatory tip shown **only** in [LimitType.REPETITIVE] (AUI-3.4, Property 2), and an
 * Active Rules list whose rows expose 48dp Edit / Pause-today / Delete controls (AUI-3.5).
 *
 * Every user-facing string comes from a localized resource and every color / size from
 * [DopaShiftTokens] (AUI-8.1, AUI-8.6). All intent is forwarded through [on] to the
 * `screen-time-interception-engine` view-model.
 *
 * @param state the rendered authoring state.
 * @param on the intent callbacks.
 * @param modifier optional layout modifier for the screen container.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLimitsScreen(
    state: AppLimitsUiState,
    on: AppLimitsActions,
    modifier: Modifier = Modifier,
) {
    val selectedTypeIndex = LIMIT_TYPE_ORDER.indexOf(state.limitType).coerceAtLeast(0)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DopaShiftTokens.Colors.background),
    ) {
        // --- Top app bar: back arrow + screen title (AUI-3) ---
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.aui_limits_screen_title),
                    style = DopaShiftTokens.TextRoles.titleMedium,
                    color = DopaShiftTokens.Colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = on.onBack,
                    modifier = Modifier.size(DopaShiftTokens.TouchTargets.minTouchTarget),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.aui_limits_back_content_description),
                        tint = DopaShiftTokens.Colors.textPrimary,
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = DopaShiftTokens.Colors.background,
                titleContentColor = DopaShiftTokens.Colors.textPrimary,
                navigationIconContentColor = DopaShiftTokens.Colors.textPrimary,
            ),
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(DopaShiftTokens.Spacing.marginMobile),
            verticalArrangement = Arrangement.spacedBy(DopaShiftTokens.Spacing.space16),
        ) {
            // --- Search bar (AUI-3.1) ---
        item(key = "search") {
            SearchBar(
                query = state.query,
                onQueryChange = on.onQueryChange,
                onClearQuery = on.onClearQuery,
            )
        }

        // --- Installed-app selection list (AUI-3.2) ---
        item(key = "apps_header") {
            SectionHeader(text = stringResource(R.string.aui_limits_select_apps_header))
        }
        if (state.installedApps.isEmpty()) {
            item(key = "apps_empty") {
                EmptyHint(text = stringResource(R.string.aui_limits_no_apps))
            }
        } else {
            items(state.installedApps, key = { "app_${it.packageName}" }) { app ->
                InstalledAppRow(
                    app = app,
                    onSelected = on.onAppSelected,
                    // The per-app daily limit is only authored in Once mode; in Repetitive mode the
                    // limit applies but the recurring interval is the primary control (AUI-3.2/3.4).
                    showDailyLimit = app.selected && state.limitType == LimitType.ONCE,
                    limitMinutes = state.perAppLimits[app.packageName],
                    limitError = state.limitErrorPackage == app.packageName,
                    onDailyLimitChange = { raw -> on.onDailyLimitChange(app.packageName, raw) },
                )
            }
        }

        // --- Limit-type segmented pill (AUI-3.3) ---
        item(key = "limit_type") {
            val onceLabel = stringResource(R.string.aui_limits_type_once)
            val repetitiveLabel = stringResource(R.string.aui_limits_type_repetitive)
            SegmentedPill(
                options = listOf(onceLabel, repetitiveLabel),
                selectedIndex = selectedTypeIndex,
                onSelect = { index -> on.onLimitTypeChange(LIMIT_TYPE_ORDER[index]) },
            )
        }

        // --- Interval presets + tip — ONLY in Repetitive mode (AUI-3.4, Property 2) ---
        if (state.limitType == LimitType.REPETITIVE) {
            item(key = "interval") {
                IntervalPresetSection(
                    presets = state.intervalPresets,
                    onPresetSelected = on.onIntervalPresetSelected,
                    customIntervalMinutes = state.customIntervalMinutes,
                    intervalError = state.intervalError,
                    onCustomIntervalChange = on.onCustomIntervalChange,
                )
            }
        }

        // --- Save control (AUI-3.2, Requirement 2.9) ---
        item(key = "save") {
            SaveButton(onClick = on.onSave)
        }

        // --- Active rules list (AUI-3.5) ---
        item(key = "rules_header") {
            SectionHeader(text = stringResource(R.string.aui_limits_active_rules_header))
        }
        if (state.activeRules.isEmpty()) {
            item(key = "rules_empty") {
                EmptyHint(text = stringResource(R.string.aui_limits_no_active_rules))
            }
        } else {
            items(state.activeRules, key = { "rule_${it.id}" }) { rule ->
                RuleRow(
                    rule = rule,
                    onEdit = on.onRuleEdit,
                    onPauseToggle = on.onRulePauseToggle,
                    onDelete = on.onRuleDelete,
                )
            }
        }
    }
    }
}

/**
 * The top search bar (AUI-3.1): a leading search glyph, a real-time-filtering text field, and a
 * trailing clear (`X`) control shown only when [query] is non-empty. Typing forwards to
 * [onQueryChange]; the clear control forwards to [onClearQuery].
 */
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = {
            Text(
                text = stringResource(R.string.aui_limits_search_placeholder),
                style = DopaShiftTokens.TextRoles.bodyMedium,
            )
        },
        textStyle = DopaShiftTokens.TextRoles.bodyMedium,
        shape = RoundedCornerShape(DopaShiftTokens.Radii.control),
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = stringResource(R.string.aui_limits_search_icon_content_description),
                tint = DopaShiftTokens.Colors.textSecondary,
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = onClearQuery,
                    modifier = Modifier.size(DopaShiftTokens.TouchTargets.minTouchTarget),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Clear,
                        contentDescription = stringResource(R.string.aui_limits_search_clear_content_description),
                        tint = DopaShiftTokens.Colors.textSecondary,
                    )
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = DopaShiftTokens.Colors.textPrimary,
            unfocusedTextColor = DopaShiftTokens.Colors.textPrimary,
            focusedContainerColor = DopaShiftTokens.Colors.surfaceElevated,
            unfocusedContainerColor = DopaShiftTokens.Colors.surfaceElevated,
            focusedBorderColor = DopaShiftTokens.Colors.momentumTeal,
            unfocusedBorderColor = DopaShiftTokens.Colors.surfaceBorder,
            cursorColor = DopaShiftTokens.Colors.momentumTeal,
            focusedPlaceholderColor = DopaShiftTokens.Colors.textSecondary,
            unfocusedPlaceholderColor = DopaShiftTokens.Colors.textSecondary,
        ),
    )
}

/**
 * A single installed-app row (AUI-3.2): a high-contrast selection checkbox meeting the 48dp
 * touch target (via [MomentumCheckbox], no strikethrough), showing the app label and its package
 * sub-label. Toggling forwards the package name and new state to [onSelected].
 */
@Composable
private fun InstalledAppRow(
    app: InstalledAppUi,
    onSelected: (packageName: String, selected: Boolean) -> Unit,
    showDailyLimit: Boolean,
    limitMinutes: Int?,
    limitError: Boolean,
    onDailyLimitChange: (rawMinutes: String) -> Unit,
) {
    KineticCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The MomentumCheckbox hosts the label; a 48dp hit area satisfies AUI-8.3 / Property 8.
            MomentumCheckbox(
                checked = app.selected,
                onCheckedChange = { checked -> onSelected(app.packageName, checked) },
                label = app.label,
                struckThroughWhenChecked = false,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = app.packageName,
            style = DopaShiftTokens.TextRoles.labelMono,
            color = DopaShiftTokens.Colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = DopaShiftTokens.TouchTargets.minTouchTarget),
        )

        // Per-app daily-limit input, shown for a selected app in Once mode (AUI-3.2). Editing
        // forwards the raw text so the view-model can validate/retain the last valid value.
        if (showDailyLimit) {
            DailyLimitInput(
                value = limitMinutes,
                isError = limitError,
                onValueChanged = onDailyLimitChange,
                modifier = Modifier.padding(
                    start = DopaShiftTokens.TouchTargets.minTouchTarget,
                    top = DopaShiftTokens.Spacing.space8,
                ),
            )
        }
    }
}

/**
 * The per-app daily-limit text field (AUI-3.2). Tracks the raw text locally so an invalid entry
 * stays visible for correction while the view-model retains the last valid value; a rejected value
 * surfaces the localized "1–480 whole minutes" range message.
 */
@Composable
private fun DailyLimitInput(
    value: Int?,
    isError: Boolean,
    onValueChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable(value) { mutableStateOf(value?.toString() ?: "") }

    OutlinedTextField(
        value = text,
        onValueChange = { new ->
            text = new
            onValueChanged(new)
        },
        modifier = modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.aui_limits_daily_limit_label)) },
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = if (isError) {
            { Text(stringResource(R.string.aui_limits_daily_limit_range_message)) }
        } else {
            null
        },
        textStyle = DopaShiftTokens.TextRoles.bodyMedium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = DopaShiftTokens.Colors.textPrimary,
            unfocusedTextColor = DopaShiftTokens.Colors.textPrimary,
            focusedContainerColor = DopaShiftTokens.Colors.surfaceElevated,
            unfocusedContainerColor = DopaShiftTokens.Colors.surfaceElevated,
            focusedBorderColor = DopaShiftTokens.Colors.momentumTeal,
            unfocusedBorderColor = DopaShiftTokens.Colors.surfaceBorder,
            cursorColor = DopaShiftTokens.Colors.momentumTeal,
        ),
    )
}

/**
 * The primary Save control (AUI-3.2, Requirement 2.9). A full-width Momentum-Teal button that
 * forwards to the view-model's save, which validates that at least one app is selected with a valid
 * limit (and a valid interval in Repetitive mode) before persisting.
 */
@Composable
private fun SaveButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = DopaShiftTokens.TouchTargets.minTouchTarget),
        shape = DopaShiftPillShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = DopaShiftTokens.Colors.momentumTeal,
            contentColor = DopaShiftTokens.Colors.onMomentumTeal,
        ),
    ) {
        Text(
            text = stringResource(R.string.aui_limits_save),
            style = DopaShiftTokens.TextRoles.labelLarge,
        )
    }
}

/**
 * The interval preset section rendered only in [LimitType.REPETITIVE] (AUI-3.4): a header, the
 * preset chips (`15/30/45/60 min`, `Custom...`) each a 48dp-tall selectable, and an explanatory
 * tip describing repetitive interception.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IntervalPresetSection(
    presets: List<IntervalPreset>,
    onPresetSelected: (IntervalPreset) -> Unit,
    customIntervalMinutes: Int?,
    intervalError: Boolean,
    onCustomIntervalChange: (rawMinutes: String) -> Unit,
) {
    KineticCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(text = stringResource(R.string.aui_limits_interval_header))
        // Quick presets (AUI-3.4): a wrapping row so 1/3/5/10/15/30/45/60 min chips all fit.
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = DopaShiftTokens.Spacing.space8),
            horizontalArrangement = Arrangement.spacedBy(DopaShiftTokens.Spacing.space8),
            verticalArrangement = Arrangement.spacedBy(DopaShiftTokens.Spacing.space8),
        ) {
            presets.forEach { preset ->
                IntervalPresetChip(
                    preset = preset,
                    onSelected = { onPresetSelected(preset) },
                )
            }
        }

        // Free-selection custom interval (AUI-3.4): type any whole-minute value in 1..120; a
        // rejected value surfaces the localized "1–120 whole minutes" range message. Typing here
        // and tapping a preset are both valid ways to set the interval.
        CustomIntervalInput(
            value = customIntervalMinutes,
            isError = intervalError,
            onValueChanged = onCustomIntervalChange,
            modifier = Modifier.padding(top = DopaShiftTokens.Spacing.space12),
        )

        Text(
            text = stringResource(R.string.aui_limits_interval_tip),
            style = DopaShiftTokens.TextRoles.bodySmall,
            color = DopaShiftTokens.Colors.textSecondary,
            modifier = Modifier.padding(top = DopaShiftTokens.Spacing.space12),
        )
    }
}

/**
 * The free-selection custom-interval text field shown alongside the preset chips in Repetitive
 * mode (AUI-3.4). Mirrors [DailyLimitInput]: raw text is tracked locally so an invalid entry stays
 * visible for correction, and a rejected value surfaces the localized "1–120 whole minutes" range
 * message while the view-model retains the last valid interval.
 */
@Composable
private fun CustomIntervalInput(
    value: Int?,
    isError: Boolean,
    onValueChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable(value) { mutableStateOf(value?.toString() ?: "") }

    OutlinedTextField(
        value = text,
        onValueChange = { new ->
            text = new
            onValueChanged(new)
        },
        modifier = modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.aui_limits_interval_custom_label)) },
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = if (isError) {
            { Text(stringResource(R.string.aui_limits_interval_range_message)) }
        } else {
            null
        },
        textStyle = DopaShiftTokens.TextRoles.bodyMedium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = DopaShiftTokens.Colors.textPrimary,
            unfocusedTextColor = DopaShiftTokens.Colors.textPrimary,
            focusedContainerColor = DopaShiftTokens.Colors.surfaceElevated,
            unfocusedContainerColor = DopaShiftTokens.Colors.surfaceElevated,
            focusedBorderColor = DopaShiftTokens.Colors.momentumTeal,
            unfocusedBorderColor = DopaShiftTokens.Colors.surfaceBorder,
            cursorColor = DopaShiftTokens.Colors.momentumTeal,
        ),
    )
}

/**
 * A single selectable interval preset chip meeting the 48dp touch target (AUI-3.4, AUI-8.3). The
 * selected preset is filled with Momentum Teal and dark text; unselected chips sit on the elevated
 * surface with secondary text.
 */
@Composable
private fun IntervalPresetChip(
    preset: IntervalPreset,
    onSelected: () -> Unit,
) {
    val background =
        if (preset.selected) DopaShiftTokens.Colors.momentumTeal else DopaShiftTokens.Colors.surfaceElevated
    val textColor =
        if (preset.selected) DopaShiftTokens.Colors.onMomentumTeal else DopaShiftTokens.Colors.textSecondary

    Box(
        modifier = Modifier
            .heightIn(min = DopaShiftTokens.TouchTargets.minTouchTarget)
            .clip(DopaShiftPillShape)
            .background(background)
            .selectable(
                selected = preset.selected,
                role = Role.RadioButton,
                onClick = onSelected,
            )
            .padding(
                horizontal = DopaShiftTokens.Spacing.space16,
                vertical = DopaShiftTokens.Spacing.space12,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = preset.label,
            style = DopaShiftTokens.TextRoles.labelLarge,
            color = textColor,
        )
    }
}

/**
 * A single active-rule row (AUI-3.5): an app icon placeholder, the app label and package sub-label,
 * the rule-type indicator, an optional "paused today" badge, and 48dp Edit / Pause-today (or
 * Resume) / Delete controls dispatching to the supplied callbacks.
 */
@Composable
private fun RuleRow(
    rule: RuleRowUi,
    onEdit: (id: String) -> Unit,
    onPauseToggle: (id: String) -> Unit,
    onDelete: (id: String) -> Unit,
) {
    KineticCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // App icon placeholder.
            Box(
                modifier = Modifier
                    .size(DopaShiftTokens.TouchTargets.minTouchTarget)
                    .clip(CircleShape)
                    .background(DopaShiftTokens.Colors.surfaceElevated),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Apps,
                    contentDescription = stringResource(
                        R.string.aui_limits_rule_icon_content_description,
                        rule.appLabel,
                    ),
                    tint = DopaShiftTokens.Colors.textSecondary,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = DopaShiftTokens.Spacing.space12),
            ) {
                Text(
                    text = rule.appLabel,
                    style = DopaShiftTokens.TextRoles.titleMedium,
                    color = DopaShiftTokens.Colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = rule.packageName,
                    style = DopaShiftTokens.TextRoles.labelMono,
                    color = DopaShiftTokens.Colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = rule.ruleTypeLabel,
                    style = DopaShiftTokens.TextRoles.bodySmall,
                    color = DopaShiftTokens.Colors.textSecondary,
                    modifier = Modifier.padding(top = DopaShiftTokens.Spacing.space4),
                )
                if (rule.paused) {
                    Box(modifier = Modifier.padding(top = DopaShiftTokens.Spacing.space4)) {
                        KineticChip(
                            text = stringResource(R.string.aui_limits_rule_paused_badge),
                            accent = DopaShiftTokens.Colors.warningAmber,
                        )
                    }
                }
            }
        }

        // 48dp action controls (AUI-3.5): Edit / Pause today (or Resume) / Delete.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = DopaShiftTokens.Spacing.space8),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(
                onClick = { onEdit(rule.id) },
                modifier = Modifier.size(DopaShiftTokens.TouchTargets.minTouchTarget),
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(
                        R.string.aui_limits_rule_edit_content_description,
                        rule.appLabel,
                    ),
                    tint = DopaShiftTokens.Colors.textSecondary,
                )
            }
            IconButton(
                onClick = { onPauseToggle(rule.id) },
                modifier = Modifier.size(DopaShiftTokens.TouchTargets.minTouchTarget),
            ) {
                if (rule.paused) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = stringResource(
                            R.string.aui_limits_rule_resume_content_description,
                            rule.appLabel,
                        ),
                        tint = DopaShiftTokens.Colors.momentumTeal,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Pause,
                        contentDescription = stringResource(
                            R.string.aui_limits_rule_pause_content_description,
                            rule.appLabel,
                        ),
                        tint = DopaShiftTokens.Colors.warningAmber,
                    )
                }
            }
            IconButton(
                onClick = { onDelete(rule.id) },
                modifier = Modifier.size(DopaShiftTokens.TouchTargets.minTouchTarget),
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(
                        R.string.aui_limits_rule_delete_content_description,
                        rule.appLabel,
                    ),
                    tint = DopaShiftTokens.Colors.dangerCoral,
                )
            }
        }
    }
}

/** A small section header used above the app list, interval presets, and rules list. */
@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = DopaShiftTokens.TextRoles.titleMedium,
        color = DopaShiftTokens.Colors.textPrimary,
    )
}

/** An inline empty-state hint for the app list and rules list. */
@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = DopaShiftTokens.TextRoles.bodyMedium,
        color = DopaShiftTokens.Colors.textSecondary,
        modifier = Modifier.clearAndSetSemantics { contentDescription = text },
    )
}

@Preview
@Composable
private fun AppLimitsScreenPreview() {
    DopaShiftTheme(themeMode = ThemeMode.DARK) {
        AppLimitsScreen(
            state = AppLimitsUiState(
                query = "",
                installedApps = listOf(
                    InstalledAppUi("com.example.social", "Social", selected = true),
                    InstalledAppUi("com.example.video", "Video", selected = false),
                ),
                limitType = LimitType.REPETITIVE,
                intervalPreset = IntervalPreset("30 min", 30, selected = true),
                intervalPresets = listOf(
                    IntervalPreset("1 min", 1),
                    IntervalPreset("3 min", 3),
                    IntervalPreset("5 min", 5),
                    IntervalPreset("10 min", 10),
                    IntervalPreset("15 min", 15),
                    IntervalPreset("30 min", 30, selected = true),
                    IntervalPreset("45 min", 45),
                    IntervalPreset("60 min", 60),
                ),
                perAppLimits = mapOf("com.example.social" to 30),
                limitErrorPackage = null,
                customIntervalMinutes = 30,
                intervalError = false,
                activeRules = listOf(
                    RuleRowUi(
                        id = "1",
                        appLabel = "Social",
                        packageName = "com.example.social",
                        ruleTypeLabel = "Repetitive: Every 30m • 3 loops",
                        paused = false,
                    ),
                ),
            ),
            on = AppLimitsActions(
                onBack = {},
                onQueryChange = {},
                onClearQuery = {},
                onAppSelected = { _, _ -> },
                onLimitTypeChange = {},
                onIntervalPresetSelected = {},
                onDailyLimitChange = { _, _ -> },
                onCustomIntervalChange = {},
                onSave = {},
                onRuleEdit = {},
                onRulePauseToggle = {},
                onRuleDelete = {},
            ),
        )
    }
}
