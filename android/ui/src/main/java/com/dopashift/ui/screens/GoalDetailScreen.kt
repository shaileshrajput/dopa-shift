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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dopashift.ui.R
import com.dopashift.ui.components.KineticCard
import com.dopashift.ui.components.KineticChip
import com.dopashift.ui.components.MomentumCheckbox
import com.dopashift.ui.render.roadmapFraction
import com.dopashift.ui.state.GoalDetailUiState
import com.dopashift.ui.state.TodoRowUi
import com.dopashift.ui.theme.DopaShiftPillShape
import com.dopashift.ui.theme.DopaShiftTheme
import com.dopashift.ui.theme.DopaShiftTokens
import com.dopashift.ui.theme.ThemeMode
import kotlin.math.roundToInt

/**
 * Callbacks dispatched by [GoalDetailScreen] to its owning view-model (spec `android-ui-upgrade`,
 * AUI-5). Like every screen in this spec, Goal Detail is **presentation-only**: it renders the
 * supplied [GoalDetailUiState] and forwards user intent through these callbacks, holding no
 * business logic and no parallel data path of its own (design → "presentation-only, no parallel
 * data path").
 *
 * @property onBack invoked when the top-app-bar back arrow is tapped (AUI-5.1).
 * @property onEdit invoked when the top-app-bar edit control is tapped (AUI-5.1).
 * @property onDelete invoked when the top-app-bar delete control is tapped (AUI-5.1).
 * @property onChecklistItemToggled invoked with a checklist item's id and its new completion state
 *   when its [MomentumCheckbox] is toggled (AUI-5.3).
 * @property onChecklistItemDeleted invoked with a checklist item's id when its inline delete
 *   control is tapped (AUI-5.3).
 * @property onAddChecklistItem invoked with the trimmed text of a new checklist item captured
 *   inline, without leaving the screen (AUI-5.4).
 */
data class GoalDetailActions(
    val onBack: () -> Unit,
    val onEdit: () -> Unit,
    val onDelete: () -> Unit,
    val onChecklistItemToggled: (id: String, done: Boolean) -> Unit,
    val onChecklistItemDeleted: (id: String) -> Unit,
    val onAddChecklistItem: (text: String) -> Unit,
)

/**
 * The **Goal Detail & 30-Day Roadmap** screen (AUI-5).
 *
 * Renders — top to bottom — a Material 3 [TopAppBar] with a back arrow, the goal title, and edit /
 * delete controls each meeting the 48dp Touch_Target (AUI-5.1); the goal category and keyword chips
 * on a `surfaceElevated` card (AUI-5.2); an interactive task checklist of 56dp rows using
 * [MomentumCheckbox] (teal check + strikethrough when done) with an inline delete control in
 * `dangerCoral` per row (AUI-5.3); an inline "Add checklist item" field with a plus control that
 * captures an item without leaving the screen (AUI-5.4); and a Habit_Track progress card showing a
 * `Day N/30` counter, an `Active` badge, a `completed / 30` progress bar, and a
 * `M of 30 days completed` sub-caption (AUI-5.5).
 *
 * Every user-facing string comes from a localized resource and every color / size from
 * [DopaShiftTokens] (AUI-8.1, AUI-8.6); every icon carries a content description (AUI-8.5). All
 * intent is forwarded through [on] to the owning view-model.
 *
 * @param state the rendered goal-detail state.
 * @param on the intent callbacks.
 * @param modifier optional layout modifier for the screen container.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalDetailScreen(
    state: GoalDetailUiState,
    on: GoalDetailActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DopaShiftTokens.Colors.background),
    ) {
        // --- Top app bar (AUI-5.1): back / title / edit / delete, each a 48dp target ---
        TopAppBar(
            title = {
                Text(
                    text = state.title,
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
                        contentDescription = stringResource(R.string.aui_goal_detail_back_content_description),
                        tint = DopaShiftTokens.Colors.textPrimary,
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = on.onEdit,
                    modifier = Modifier.size(DopaShiftTokens.TouchTargets.minTouchTarget),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.aui_goal_detail_edit_content_description),
                        tint = DopaShiftTokens.Colors.textSecondary,
                    )
                }
                IconButton(
                    onClick = on.onDelete,
                    modifier = Modifier.size(DopaShiftTokens.TouchTargets.minTouchTarget),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.aui_goal_detail_delete_content_description),
                        tint = DopaShiftTokens.Colors.dangerCoral,
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = DopaShiftTokens.Colors.background,
                titleContentColor = DopaShiftTokens.Colors.textPrimary,
                navigationIconContentColor = DopaShiftTokens.Colors.textPrimary,
                actionIconContentColor = DopaShiftTokens.Colors.textSecondary,
            ),
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(DopaShiftTokens.Spacing.marginMobile),
            verticalArrangement = Arrangement.spacedBy(DopaShiftTokens.Spacing.space16),
        ) {
            // --- Category + keyword chips (AUI-5.2) ---
            item(key = "keywords") {
                CategoryAndKeywords(category = state.category, keywords = state.keywords)
            }

            // --- Checklist header (AUI-5.3) ---
            item(key = "checklist_header") {
                SectionHeader(text = stringResource(R.string.aui_goal_detail_checklist_header))
            }

            // --- Interactive checklist (AUI-5.3) ---
            if (state.checklist.isEmpty()) {
                item(key = "checklist_empty") {
                    EmptyHint(text = stringResource(R.string.aui_goal_detail_checklist_empty))
                }
            } else {
                items(state.checklist, key = { "check_${it.id}" }) { row ->
                    ChecklistRow(
                        row = row,
                        onToggled = on.onChecklistItemToggled,
                        onDeleted = on.onChecklistItemDeleted,
                    )
                }
            }

            // --- Inline "Add checklist item" field (AUI-5.4) ---
            item(key = "add_item") {
                AddChecklistItemField(onAdd = on.onAddChecklistItem)
            }

            // --- Habit_Track roadmap progress card (AUI-5.5) ---
            item(key = "roadmap") {
                RoadmapProgressCard(state = state)
            }
        }
    }
}

/**
 * The category + keyword chips block on a `surfaceElevated` background (AUI-5.2). The goal category
 * is rendered as the first (teal) chip; each keyword follows as a chip, wrapping across rows.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryAndKeywords(category: String, keywords: List<String>) {
    KineticCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(text = stringResource(R.string.aui_goal_detail_keywords_header))
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = DopaShiftTokens.Spacing.space8),
            horizontalArrangement = Arrangement.spacedBy(DopaShiftTokens.Spacing.space8),
            verticalArrangement = Arrangement.spacedBy(DopaShiftTokens.Spacing.space8),
        ) {
            KineticChip(
                text = category,
                accent = DopaShiftTokens.Colors.momentumTeal,
            )
            keywords.forEach { keyword ->
                KineticChip(
                    text = keyword,
                    accent = DopaShiftTokens.Colors.clarityCyan,
                )
            }
        }
    }
}

/**
 * A single interactive checklist row (AUI-5.3): a 56dp-tall [KineticCard] hosting a
 * [MomentumCheckbox] (which shows a teal check + strikethrough when done) that dispatches
 * [onToggled], and — when the row is [TodoRowUi.deletable] — an inline delete [IconButton] in
 * `dangerCoral` meeting the 48dp Touch_Target that dispatches [onDeleted].
 */
@Composable
private fun ChecklistRow(
    row: TodoRowUi,
    onToggled: (id: String, done: Boolean) -> Unit,
    onDeleted: (id: String) -> Unit,
) {
    KineticCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = DopaShiftTokens.TouchTargets.listRowMinHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MomentumCheckbox(
                checked = row.done,
                onCheckedChange = { checked -> onToggled(row.id, checked) },
                label = row.text,
                struckThroughWhenChecked = true,
                modifier = Modifier.weight(1f),
            )
            if (row.deletable) {
                IconButton(
                    onClick = { onDeleted(row.id) },
                    modifier = Modifier.size(DopaShiftTokens.TouchTargets.minTouchTarget),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(
                            R.string.aui_goal_detail_checklist_delete_content_description,
                            row.text,
                        ),
                        tint = DopaShiftTokens.Colors.dangerCoral,
                    )
                }
            }
        }
    }
}

/**
 * The inline "Add checklist item" field (AUI-5.4): an [OutlinedTextField] plus a trailing plus
 * [IconButton] that, on submit, forwards the **trimmed** text to [onAdd] and clears the field — all
 * without leaving the screen. Blank / whitespace-only input is ignored.
 */
@Composable
private fun AddChecklistItemField(onAdd: (text: String) -> Unit) {
    var text by remember { mutableStateOf("") }

    fun submit() {
        val trimmed = text.trim()
        if (trimmed.isNotEmpty()) {
            onAdd(trimmed)
            text = ""
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = {
            Text(
                text = stringResource(R.string.aui_goal_detail_add_item_placeholder),
                style = DopaShiftTokens.TextRoles.bodyMedium,
            )
        },
        textStyle = DopaShiftTokens.TextRoles.bodyMedium,
        shape = RoundedCornerShape(DopaShiftTokens.Radii.control),
        trailingIcon = {
            IconButton(
                onClick = { submit() },
                modifier = Modifier.size(DopaShiftTokens.TouchTargets.minTouchTarget),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.aui_goal_detail_add_item_content_description),
                    tint = DopaShiftTokens.Colors.momentumTeal,
                )
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
 * The Habit_Track roadmap progress card (AUI-5.5): the `Day N/30` counter in the mono metric role,
 * an `Active` status badge ([KineticChip]) shown only when [GoalDetailUiState.roadmapActive], a
 * horizontal progress bar whose fraction is [roadmapFraction] of
 * [GoalDetailUiState.roadmapCompletedDays] (`completed / 30`), and the
 * `M of 30 days completed` sub-caption.
 */
@Composable
private fun RoadmapProgressCard(state: GoalDetailUiState) {
    val fraction = roadmapFraction(state.roadmapCompletedDays)
    val progressDescription = stringResource(
        R.string.aui_goal_detail_roadmap_progress_content_description,
        state.roadmapDayLabel,
        state.roadmapSubCaption,
    )

    KineticCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(text = stringResource(R.string.aui_goal_detail_roadmap_header))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = DopaShiftTokens.Spacing.space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.roadmapDayLabel,
                style = DopaShiftTokens.TextRoles.metricCounter,
                color = DopaShiftTokens.Colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            if (state.roadmapActive) {
                KineticChip(
                    text = stringResource(R.string.aui_goal_detail_roadmap_active_badge),
                    accent = DopaShiftTokens.Colors.momentumTeal,
                )
            }
        }

        // Horizontal `completed / 30` progress bar (AUI-5.5): a rounded teal fill spanning
        // `fraction` of an elevated track. The whole bar is one TalkBack node announcing the day
        // counter + sub-caption together (AUI-8.5).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = DopaShiftTokens.Spacing.space12)
                .height(RoadmapBarHeight)
                .clip(DopaShiftPillShape)
                .background(DopaShiftTokens.Colors.surfaceElevated)
                .semantics { contentDescription = progressDescription },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RoadmapBarHeight)
                    .layout { measurable, constraints ->
                        val width = (constraints.maxWidth * fraction).roundToInt()
                        val placeable = measurable.measure(
                            constraints.copy(minWidth = width, maxWidth = width),
                        )
                        layout(placeable.width, placeable.height) {
                            placeable.place(0, 0)
                        }
                    }
                    .clip(DopaShiftPillShape)
                    .background(DopaShiftTokens.Colors.momentumTeal),
            ) {}
        }

        Text(
            text = state.roadmapSubCaption,
            style = DopaShiftTokens.TextRoles.bodySmall,
            color = DopaShiftTokens.Colors.textSecondary,
            modifier = Modifier.padding(top = DopaShiftTokens.Spacing.space8),
        )
    }
}

/** Height of the roadmap `completed / 30` progress track; a component dimension, not a token. */
private val RoadmapBarHeight: Dp = 8.dp

/** A small section header used above the keyword, checklist, and roadmap sections. */
@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = DopaShiftTokens.TextRoles.titleMedium,
        color = DopaShiftTokens.Colors.textPrimary,
    )
}

/** An inline empty-state hint for the checklist. */
@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = DopaShiftTokens.TextRoles.bodyMedium,
        color = DopaShiftTokens.Colors.textSecondary,
    )
}

@Preview
@Composable
private fun GoalDetailScreenPreview() {
    DopaShiftTheme(themeMode = ThemeMode.DARK) {
        GoalDetailScreen(
            state = GoalDetailUiState(
                goalId = "g1",
                title = "Ship the side project",
                category = "Business",
                keywords = listOf("startup", "mvp", "launch"),
                checklist = listOf(
                    TodoRowUi(id = "1", text = "Draft landing page", done = true, deletable = true),
                    TodoRowUi(id = "2", text = "Wire up payments", done = false, deletable = true),
                ),
                roadmapDayLabel = "Day 6/30",
                roadmapActive = true,
                roadmapCompletedDays = 6,
                roadmapSubCaption = "6 of 30 days completed",
            ),
            on = GoalDetailActions(
                onBack = {},
                onEdit = {},
                onDelete = {},
                onChecklistItemToggled = { _, _ -> },
                onChecklistItemDeleted = {},
                onAddChecklistItem = {},
            ),
        )
    }
}
