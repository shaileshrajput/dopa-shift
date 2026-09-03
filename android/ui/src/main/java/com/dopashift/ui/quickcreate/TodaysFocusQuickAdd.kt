package com.dopashift.ui.quickcreate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import com.dopashift.domain.creation.TodoValidator
import com.dopashift.domain.entity.DailyTodoItem
import com.dopashift.ui.R
import com.dopashift.ui.theme.DopaShiftTheme

/**
 * State for the inline "Today's Focus" quick-add input (DQC-1.4, DQC-4.2).
 *
 * The quick-add creates a to-do from text only — no goal, no due date, no reminder (DQC-4.1). Text
 * bounds mirror the shared [TodoValidator] so the visible rules match the domain: at most
 * [TodoValidator.TEXT_MAX] characters (DQC-4.5) and a per-day cap of [TodoValidator.DAILY_CAP]
 * (DQC-4.6). The input remains functional offline; the only observable difference is the
 * sync-pending indicator (DQC-4.9).
 *
 * @property todayCount how many to-dos already exist for today in the user's time zone; disables
 *   quick-add once the cap is reached (DQC-4.6).
 * @property isOffline whether the device is offline, driving the sync-pending hint (DQC-4.9).
 */
@Immutable
data class QuickAddState(
    val todayCount: Int = 0,
    val isOffline: Boolean = false,
) {
    /** True once the day already holds [TodoValidator.DAILY_CAP] items (DQC-4.6). */
    val isDailyCapReached: Boolean get() = todayCount >= TodoValidator.DAILY_CAP
}

/**
 * Inline quick-add row for the Today's Focus section (DQC-1.4, DQC-4.2).
 *
 * Behavior on submit (via the IME "Done" action or the trailing add button): the entered text is
 * handed to [onSubmit] (which routes to `QuickCreateViewModel.saveTodo(text)`), the input is
 * cleared, and keyboard focus is retained so the user can enter another to-do immediately. The
 * host persists via the shared to-do use case (local write within 200 ms) and prepends the new
 * item to the Today's Focus list (DQC-4.2). Empty or whitespace-only text is ignored and creates
 * nothing (DQC-4.7); text over [TodoValidator.TEXT_MAX] is blocked with an inline error (DQC-4.5);
 * once [QuickAddState.isDailyCapReached] the input is disabled with the daily-limit message
 * (DQC-4.6). While offline, a sync-pending hint is shown but behavior is otherwise unchanged
 * (DQC-4.9).
 *
 * Optimistic prepend, the 200 ms persistence budget, and the completion styling of created items
 * live in the host list; this composable owns only the entry field and its retain-focus loop.
 */
@Composable
fun TodaysFocusQuickAdd(
    state: QuickAddState,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val spacing = DopaShiftTheme.spacing

    val charCount = text.length
    val isOverLimit = charCount > TodoValidator.TEXT_MAX
    // Empty/whitespace-only input is ignored (DQC-4.7); enabled only when a valid item can be made.
    val canSubmit = text.isNotBlank() && !isOverLimit && !state.isDailyCapReached

    // Submits, clears the field, and keeps focus for a consecutive entry (DQC-4.2). Blank/over-limit
    // input is a no-op so the retain-focus loop never emits an invalid item (DQC-4.5, DQC-4.7).
    val submit = {
        if (canSubmit) {
            onSubmit(text.trim())
            text = ""
            focusRequester.requestFocus()
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.compact),
    ) {
        if (state.isOffline) {
            QuickAddSyncPendingHint()
        }

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            enabled = !state.isDailyCapReached,
            placeholder = { Text(stringResource(R.string.qc_todo_quick_add_placeholder)) },
            isError = isOverLimit,
            supportingText = when {
                state.isDailyCapReached -> {
                    {
                        Text(
                            text = stringResource(
                                R.string.qc_todo_quick_add_daily_limit_reached,
                                TodoValidator.DAILY_CAP,
                            ),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                isOverLimit -> {
                    {
                        Text(
                            text = stringResource(
                                R.string.qc_todo_char_over_limit,
                                TodoValidator.TEXT_MAX,
                            ),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                else -> null
            },
            trailingIcon = {
                IconButton(onClick = submit, enabled = canSubmit) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.qc_todo_quick_add_content_description),
                    )
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )
    }
}

/**
 * Compact sync-pending hint for the quick-add row (DQC-4.9). Announced politely so assistive tech
 * surfaces the offline status without interrupting typing (DUX-5.8).
 */
@Composable
private fun QuickAddSyncPendingHint(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(horizontal = DopaShiftTheme.spacing.compact)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.CloudQueue,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(DopaShiftTheme.spacing.base))
        Text(
            text = stringResource(R.string.qc_todo_offline_sync_pending),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A single Today's Focus to-do row with completion styling (DQC-4.8).
 *
 * When completed the row applies the `label-strikethrough` text style and drops to 50% opacity,
 * and the item stays in the list so the user can un-check it. Toggling routes to [onToggleComplete]
 * (the host completes/un-completes via the shared update path). This is the Dashboard's Today's
 * Focus rendering of a created to-do; the inline quick-add above prepends new items to this list.
 *
 * @param item the to-do to render.
 * @param onToggleComplete invoked when the checkbox or row is tapped to flip completion.
 */
@Composable
fun TodaysFocusTodoRow(
    item: DailyTodoItem,
    onToggleComplete: (DailyTodoItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = DopaShiftTheme.spacing
    // Completed rows drop to 50% opacity but remain in the list and re-checkable (DQC-4.8).
    val rowAlpha = if (item.isCompleted) 0.5f else 1f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onToggleComplete(item) }
            .padding(vertical = spacing.base)
            .alpha(rowAlpha),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = item.isCompleted,
            onCheckedChange = { onToggleComplete(item) },
        )
        Spacer(modifier = Modifier.width(spacing.base))
        Text(
            text = item.text,
            style = MaterialTheme.typography.bodyMedium,
            // label-strikethrough on completion (DQC-4.8).
            textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
