package com.dopashift.ui.quickcreate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.dopashift.domain.creation.ReminderDraft
import com.dopashift.domain.creation.TodoValidator
import com.dopashift.ui.R
import com.dopashift.ui.theme.DopaShiftTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * State for the full "New To-Do" Creation_Sheet (DQC-4.4).
 *
 * The full sheet extends the inline quick-add by collecting an optional due date/time and an
 * optional [ReminderDraft] in addition to the required text. Text validation mirrors the shared
 * [TodoValidator] bounds so the user sees the same rules the domain enforces: non-blank text
 * (DQC-4.7), at most [TodoValidator.TEXT_MAX] characters (DQC-4.5), and a per-day cap of
 * [TodoValidator.DAILY_CAP] items (DQC-4.6).
 *
 * @property todayCount how many to-dos already exist for today in the user's time zone; used to
 *   enforce the daily cap and disable creation once it is reached (DQC-4.6).
 * @property zoneId the user's configured time zone, used to render/interpret due date-times.
 * @property isOffline whether the device is currently offline; drives the sync-pending indicator
 *   so the sheet's behavior is unchanged offline apart from that hint (DQC-4.9).
 */
@Immutable
data class TodoSheetState(
    val todayCount: Int = 0,
    val zoneId: ZoneId = ZoneId.systemDefault(),
    val isOffline: Boolean = false,
) {
    /** True once the day already holds [TodoValidator.DAILY_CAP] items (DQC-4.6). */
    val isDailyCapReached: Boolean get() = todayCount >= TodoValidator.DAILY_CAP
}

/**
 * Callbacks the full to-do Creation_Sheet hands back to the host (DQC-4.4).
 *
 * @property onSave routes to `QuickCreateViewModel.saveTodo(...)` with the required text and the
 *   optional due date-time and reminder draft. The host is responsible for validating and
 *   persisting through the shared to-do use case (no parallel path, DQC-1.10).
 * @property onDismiss dismisses the sheet without creating anything (back gesture, scrim tap).
 * @property onDismissWithUnsavedInput dismiss requested while the text field holds unsaved input;
 *   the host may surface confirm-before-discard (DQC-1.9).
 */
@Stable
data class TodoSheetCallbacks(
    val onSave: (text: String, dueDateTime: Instant?, reminder: ReminderDraft?) -> Unit,
    val onDismiss: () -> Unit,
    val onDismissWithUnsavedInput: () -> Unit = onDismiss,
)

/**
 * Full Material 3 [ModalBottomSheet] for creating a daily to-do (DQC-4.4).
 *
 * Collects the required text plus an optional due date/time and an optional daily Reminder. Text
 * is bounded by the shared [TodoValidator] rules with an inline character counter and error; the
 * save action is disabled while the text is blank, over the limit, or the daily cap is reached
 * (DQC-4.5, DQC-4.6, DQC-4.7). When [TodoSheetState.isOffline] a non-blocking sync-pending hint is
 * shown so the only observable difference offline is that indicator (DQC-4.9). On save the sheet
 * routes to [TodoSheetCallbacks.onSave]; the host persists via the shared to-do use case.
 *
 * Date/time and reminder pickers are surfaced through the host-provided [onPickDueDateTime] and
 * [onPickReminder] lambdas so this composable stays free of platform dialog wiring and testable in
 * isolation; both are optional and, when omitted, those affordances are hidden.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoCreationSheet(
    state: TodoSheetState,
    callbacks: TodoSheetCallbacks,
    text: String,
    onTextChange: (String) -> Unit,
    dueDateTime: Instant?,
    reminder: ReminderDraft?,
    modifier: Modifier = Modifier,
    onPickDueDateTime: (() -> Unit)? = null,
    onClearDueDateTime: () -> Unit = {},
    onPickReminder: (() -> Unit)? = null,
    onClearReminder: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val spacing = DopaShiftTheme.spacing

    val charCount = text.length
    val isOverLimit = charCount > TodoValidator.TEXT_MAX
    val isBlank = text.isBlank()
    val canSave = !isBlank && !isOverLimit && !state.isDailyCapReached

    ModalBottomSheet(
        onDismissRequest = {
            if (text.isNotBlank()) callbacks.onDismissWithUnsavedInput() else callbacks.onDismiss()
        },
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = spacing.margin)
                .padding(bottom = spacing.margin),
            verticalArrangement = Arrangement.spacedBy(spacing.base),
        ) {
            Text(
                text = stringResource(R.string.qc_todo_sheet_title),
                style = MaterialTheme.typography.titleLarge,
            )

            if (state.isOffline) {
                SyncPendingIndicator()
            }

            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.qc_todo_text_placeholder)) },
                isError = isOverLimit,
                supportingText = {
                    val message = when {
                        isOverLimit -> stringResource(
                            R.string.qc_todo_char_over_limit,
                            TodoValidator.TEXT_MAX,
                        )
                        state.isDailyCapReached -> stringResource(
                            R.string.qc_todo_daily_limit_reached,
                            TodoValidator.DAILY_CAP,
                        )
                        else -> stringResource(
                            R.string.qc_todo_char_counter,
                            charCount,
                            TodoValidator.TEXT_MAX,
                        )
                    }
                    Text(
                        text = message,
                        color = if (isOverLimit || state.isDailyCapReached) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                maxLines = 4,
            )

            // Optional due date/time (DQC-4.4).
            if (onPickDueDateTime != null) {
                OptionalFieldChip(
                    icon = Icons.Default.Event,
                    label = dueDateTime?.let {
                        stringResource(R.string.qc_todo_due_set, formatDueDateTime(it, state.zoneId))
                    } ?: stringResource(R.string.qc_todo_due_add),
                    isSet = dueDateTime != null,
                    onClick = onPickDueDateTime,
                    onClear = onClearDueDateTime,
                    clearContentDescription = stringResource(R.string.qc_todo_due_clear_content_description),
                )
            }

            // Optional Reminder configuration (DQC-4.4).
            if (onPickReminder != null) {
                OptionalFieldChip(
                    icon = Icons.Default.Alarm,
                    label = reminder?.let {
                        val time = it.time.format(reminderTimeFormatter)
                        val summary = it.date?.let { date ->
                            stringResource(
                                R.string.qc_todo_reminder_date_at,
                                date.format(reminderDateFormatter),
                                time,
                            )
                        } ?: stringResource(R.string.qc_todo_reminder_daily_at, time)
                        stringResource(R.string.qc_todo_reminder_set, summary)
                    } ?: stringResource(R.string.qc_todo_reminder_add),
                    isSet = reminder != null,
                    onClick = onPickReminder,
                    onClear = onClearReminder,
                    clearContentDescription = stringResource(R.string.qc_todo_reminder_clear_content_description),
                )
            }

            Spacer(modifier = Modifier.height(spacing.compact))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        if (text.isNotBlank()) {
                            callbacks.onDismissWithUnsavedInput()
                        } else {
                            callbacks.onDismiss()
                        }
                    },
                ) {
                    Text(stringResource(R.string.qc_todo_cancel))
                }
                Spacer(modifier = Modifier.width(spacing.base))
                Button(
                    onClick = {
                        if (canSave) callbacks.onSave(text.trim(), dueDateTime, reminder)
                    },
                    enabled = canSave,
                ) {
                    Text(stringResource(R.string.qc_todo_save))
                }
            }
        }
    }
}

/**
 * A tappable chip representing an optional field (due date/time or reminder). Shows a "clear"
 * affordance once a value is set so the user can remove it without reopening the picker.
 */
@Composable
private fun OptionalFieldChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSet: Boolean,
    onClick: () -> Unit,
    onClear: () -> Unit,
    clearContentDescription: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AssistChip(
            onClick = onClick,
            label = { Text(label) },
            leadingIcon = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.width(AssistChipDefaults.IconSize),
                )
            },
        )
        if (isSet) {
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Clear, contentDescription = clearContentDescription)
            }
        }
    }
}

/**
 * A non-blocking sync-pending hint shown while offline. The only observable difference from the
 * online flow is this indicator; the to-do is still created and persisted locally (DQC-4.9).
 * Marked as a polite live region so assistive tech announces it when it appears (DUX-5.8).
 */
@Composable
private fun SyncPendingIndicator(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.base),
    ) {
        Icon(
            imageVector = Icons.Default.CloudQueue,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.qc_todo_offline_sync_pending),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val dueDateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

private val reminderTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

private val reminderDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

private fun formatDueDateTime(instant: Instant, zoneId: ZoneId): String =
    instant.atZone(zoneId).format(dueDateTimeFormatter)
