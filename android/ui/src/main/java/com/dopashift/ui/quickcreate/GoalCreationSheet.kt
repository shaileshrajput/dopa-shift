package com.dopashift.ui.quickcreate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.dopashift.domain.creation.GoalValidator
import com.dopashift.ui.R
import com.dopashift.ui.theme.DopaShiftTheme

/**
 * The goal Creation_Sheet (DQC-2), presented as a Material 3 [ModalBottomSheet] over the Dashboard
 * so the user never leaves the screen (DQC-1.5).
 *
 * It collects the three required goal fields (DQC-2.1):
 * - **name** (1–[GoalValidator.NAME_MAX], unique per user),
 * - **category** (1–[GoalValidator.CATEGORY_MAX]) via a preset picker *and* free text (DQC-2.2), and
 * - **keywords** (1–[GoalValidator.KEYWORDS_MAX], each 1–[GoalValidator.KEYWORD_MAX_LEN]),
 *   pre-populated from the bundled mapping for a chosen preset category and individually
 *   editable/removable (DQC-2.3).
 *
 * ### Validation & counters (DQC-2.6, DQC-2.7)
 * All rules come from the shared [GoalValidator], so the sheet is a usability mirror of the same
 * checks the dedicated Goals screen and the Backend run (DQC-1.10, DQC-1.11). Per-field errors are
 * shown inline as the user types, including a case-insensitive duplicate-name error (DQC-2.6) that
 * disables saving. A per-field character counter appears only when the field is within
 * [COUNTER_WITHIN] characters of its limit (DQC-2.7).
 *
 * ### Suggest with AI (DQC-2.4, DQC-2.5)
 * When [GoalSheetState.llmConfigured] is `true`, an optional "Suggest with AI" action requests
 * keyword/description suggestions, sending only name/description/keywords as context (the request
 * is issued by the caller — DQC-2.4). When no provider is configured the action is shown **disabled**
 * with an explanatory label rather than one that errors on tap (DQC-2.5).
 *
 * ### After save (DQC-2.9, DQC-2.10)
 * The sheet only *routes* the save via [GoalSheetCallbacks.onSave] → `QuickCreateViewModel.saveGoal`
 * → the shared `CreateGoalUseCase`. Transitioning the Dashboard out of Zero_State and prompting to
 * start a habit for the first goal is driven reactively off the refreshed dashboard summary, not by
 * this composable. No field beyond the three required ones is mandatory (DQC-2.10).
 *
 * All user-facing copy comes from string resources (DUX-4.11); colors and spacing come from
 * [DopaShiftTheme].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalCreationSheet(
    state: GoalSheetState,
    callbacks: GoalSheetCallbacks,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // --- Transient form state owned by the sheet -------------------------------------
    var name by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    val keywords = remember { mutableStateListOf<String>() }
    var keywordDraft by rememberSaveable { mutableStateOf("") }
    // Optional description; may be populated by an AI suggestion (kept out of the required fields).
    var description by rememberSaveable { mutableStateOf("") }

    // Live, per-field validation via the shared validator (DQC-2.6, DQC-2.7, DQC-1.11).
    val errors = GoalValidator.validate(
        name = name,
        category = category,
        keywords = keywords,
        existingNamesLower = state.existingGoalNamesLower,
    )
    val canSave = errors.isEmpty() && !state.isSaving

    val hasUnsavedInput = name.isNotBlank() ||
        category.isNotBlank() ||
        keywords.isNotEmpty() ||
        keywordDraft.isNotBlank() ||
        description.isNotBlank()

    val dismiss: () -> Unit = {
        if (hasUnsavedInput) callbacks.onDismissWithUnsavedInput() else callbacks.onDismiss()
    }

    fun addKeyword() {
        val trimmed = keywordDraft.trim()
        if (trimmed.isNotEmpty() &&
            trimmed.length <= GoalValidator.KEYWORD_MAX_LEN &&
            keywords.size < GoalValidator.KEYWORDS_MAX &&
            !keywords.contains(trimmed)
        ) {
            keywords.add(trimmed)
            keywordDraft = ""
        }
    }

    ModalBottomSheet(
        onDismissRequest = dismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DopaShiftTheme.spacing.margin)
                .navigationBarsPadding(),
        ) {
            GoalSheetHeader(onClose = dismiss)

            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.gutter))

            // ---- Name (DQC-2.1, 2.6, 2.7) -----------------------------------------
            val nameError = errors[GoalValidator.FIELD_NAME]
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(GoalValidator.NAME_MAX) },
                label = { Text(stringResource(R.string.qc_goal_name_label)) },
                placeholder = { Text(stringResource(R.string.qc_goal_name_placeholder)) },
                singleLine = true,
                isError = name.isNotEmpty() && nameError != null,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
                supportingText = fieldSupportingText(
                    error = if (name.isNotEmpty()) nameError else null,
                    length = name.length,
                    max = GoalValidator.NAME_MAX,
                ),
            )

            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.gutter))

            // ---- Category: free text + preset picker (DQC-2.2) --------------------
            CategoryField(
                state = state,
                category = category,
                error = if (category.isNotEmpty()) errors[GoalValidator.FIELD_CATEGORY] else null,
                onCategoryChange = {
                    val clamped = it.take(GoalValidator.CATEGORY_MAX)
                    category = clamped
                    callbacks.onCategoryChanged(clamped)
                },
                onPresetSelected = { preset ->
                    category = preset
                    callbacks.onCategoryChanged(preset)
                },
            )

            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.gutter))

            // ---- Keywords: suggested + working list (DQC-2.3) ---------------------
            KeywordsField(
                state = state,
                keywords = keywords,
                keywordDraft = keywordDraft,
                error = errors[GoalValidator.FIELD_KEYWORDS],
                onKeywordDraftChange = { keywordDraft = it.take(GoalValidator.KEYWORD_MAX_LEN) },
                onAddKeyword = { addKeyword() },
                onAddSuggested = { suggestion ->
                    if (keywords.size < GoalValidator.KEYWORDS_MAX && !keywords.contains(suggestion)) {
                        keywords.add(suggestion)
                    }
                },
                onEditKeyword = { keyword ->
                    // "Edit" = pull the chip back into the draft field so it can be amended, then
                    // re-added; combined with individual removal this satisfies DQC-2.3.
                    keywords.remove(keyword)
                    keywordDraft = keyword
                },
                onRemoveKeyword = { keywords.remove(it) },
            )

            // ---- Suggest with AI (DQC-2.4, 2.5) -----------------------------------
            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.gutter))
            SuggestWithAiRow(
                state = state,
                onSuggest = {
                    callbacks.onSuggestWithAi(
                        name.trim(),
                        description.trim().ifBlank { null },
                        keywords.toList(),
                    )
                },
            )

            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.margin))

            // ---- Save / Cancel -----------------------------------------------------
            GoalSheetActions(
                canSave = canSave,
                onCancel = dismiss,
                onSave = {
                    callbacks.onSave(
                        name.trim(),
                        category.trim(),
                        keywords.toList(),
                        description.trim().ifBlank { null },
                    )
                },
            )

            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.margin))
        }
    }
}

// =====================================================================================
// Header
// =====================================================================================

@Composable
private fun GoalSheetHeader(onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.qc_goal_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.qc_goal_sheet_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.qc_goal_sheet_close_content_description),
            )
        }
    }
}

// =====================================================================================
// Category field (DQC-2.2)
// =====================================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryField(
    state: GoalSheetState,
    category: String,
    error: String?,
    onCategoryChange: (String) -> Unit,
    onPresetSelected: (String) -> Unit,
) {
    OutlinedTextField(
        value = category,
        onValueChange = onCategoryChange,
        label = { Text(stringResource(R.string.qc_goal_category_label)) },
        placeholder = { Text(stringResource(R.string.qc_goal_category_placeholder)) },
        singleLine = true,
        isError = category.isNotEmpty() && error != null,
        enabled = !state.isSaving,
        modifier = Modifier.fillMaxWidth(),
        supportingText = fieldSupportingText(
            error = error,
            length = category.length,
            max = GoalValidator.CATEGORY_MAX,
        ),
    )

    if (state.presetCategories.isNotEmpty()) {
        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
        Text(
            text = stringResource(R.string.qc_goal_category_presets_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.compact))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.base),
            verticalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.compact),
        ) {
            state.presetCategories.forEach { preset ->
                val selected = category == preset
                FilterChip(
                    selected = selected,
                    onClick = { onPresetSelected(preset) },
                    enabled = !state.isSaving,
                    label = { Text(preset) },
                )
            }
        }
    }
}

// =====================================================================================
// Keywords field (DQC-2.3)
// =====================================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeywordsField(
    state: GoalSheetState,
    keywords: List<String>,
    keywordDraft: String,
    error: String?,
    onKeywordDraftChange: (String) -> Unit,
    onAddKeyword: () -> Unit,
    onAddSuggested: (String) -> Unit,
    onEditKeyword: (String) -> Unit,
    onRemoveKeyword: (String) -> Unit,
) {
    Text(
        text = stringResource(R.string.qc_goal_keywords_label),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))

    val atLimit = keywords.size >= GoalValidator.KEYWORDS_MAX
    OutlinedTextField(
        value = keywordDraft,
        onValueChange = onKeywordDraftChange,
        label = { Text(stringResource(R.string.qc_goal_keyword_input_placeholder)) },
        singleLine = true,
        enabled = !state.isSaving && !atLimit,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onAddKeyword() }),
        supportingText = {
            Text(
                text = stringResource(
                    R.string.qc_goal_keywords_count,
                    keywords.size,
                    GoalValidator.KEYWORDS_MAX,
                ),
                style = MaterialTheme.typography.labelSmall,
            )
        },
        trailingIcon = {
            IconButton(onClick = onAddKeyword, enabled = !atLimit && keywordDraft.isNotBlank()) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.qc_goal_keyword_add),
                )
            }
        },
    )

    // Bundled suggestions for the chosen category, individually addable (DQC-2.3).
    val unusedSuggestions = state.suggestedKeywords.filter { it !in keywords }
    if (unusedSuggestions.isNotEmpty()) {
        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.base),
            verticalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.compact),
        ) {
            unusedSuggestions.forEach { suggestion ->
                AssistChip(
                    onClick = { onAddSuggested(suggestion) },
                    enabled = !atLimit,
                    label = { Text(suggestion) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
        }
    }

    // The working keyword list: tap a chip to edit it, tap the X to remove it (DQC-2.3).
    if (keywords.isNotEmpty()) {
        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.base),
            verticalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.compact),
        ) {
            keywords.forEach { keyword ->
                InputChip(
                    selected = false,
                    onClick = { onEditKeyword(keyword) },
                    label = { Text(keyword) },
                    trailingIcon = {
                        IconButton(
                            onClick = { onRemoveKeyword(keyword) },
                            modifier = Modifier.size(18.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(
                                    R.string.qc_goal_keyword_remove_content_description,
                                    keyword,
                                ),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    },
                )
            }
        }
    }

    // Inline keyword error (empty list, too many, or a bad length) (DQC-2.6).
    if (error != null) {
        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.compact))
        Text(
            text = error,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

// =====================================================================================
// Suggest with AI (DQC-2.4, 2.5)
// =====================================================================================

@Composable
private fun SuggestWithAiRow(
    state: GoalSheetState,
    onSuggest: () -> Unit,
) {
    if (state.llmConfigured) {
        TextButton(
            onClick = onSuggest,
            enabled = !state.isSuggesting && !state.isSaving,
        ) {
            if (state.isSuggesting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(DopaShiftTheme.spacing.base))
                Text(text = stringResource(R.string.qc_goal_suggest_ai_in_progress))
            } else {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(DopaShiftTheme.spacing.base))
                Text(text = stringResource(R.string.qc_goal_suggest_ai))
            }
        }
    } else {
        // Shown DISABLED with an explanatory label — never an action that errors on tap (DQC-2.5).
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {}, enabled = false) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(DopaShiftTheme.spacing.base))
                Text(text = stringResource(R.string.qc_goal_suggest_ai))
            }
        }
        Text(
            text = stringResource(R.string.qc_goal_suggest_ai_disabled_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// =====================================================================================
// Actions
// =====================================================================================

@Composable
private fun GoalSheetActions(
    canSave: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.base),
    ) {
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.weight(1f),
        ) {
            Text(text = stringResource(R.string.qc_goal_cancel))
        }
        Button(
            onClick = onSave,
            enabled = canSave,
            modifier = Modifier.weight(1f),
        ) {
            Text(text = stringResource(R.string.qc_goal_save))
        }
    }
}

// =====================================================================================
// Helpers
// =====================================================================================

/**
 * Builds the supporting-text slot for a field: shows the inline [error] when present, otherwise a
 * character counter only when the field is within [COUNTER_WITHIN] characters of [max] (DQC-2.7).
 * Returns `null` when there is nothing to show, so the field stays compact.
 */
private fun fieldSupportingText(
    error: String?,
    length: Int,
    max: Int,
): (@Composable () -> Unit)? = when {
    error != null -> {
        { Text(text = error, style = MaterialTheme.typography.bodySmall) }
    }
    length >= max - COUNTER_WITHIN -> {
        {
            Text(
                text = stringResource(R.string.qc_goal_char_counter, length, max),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
    else -> null
}

/** Show the per-field character counter within this many characters of the field's limit (DQC-2.7). */
private const val COUNTER_WITHIN = 20
