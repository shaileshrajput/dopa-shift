package com.dopashift.ui.quickcreate

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dopashift.ui.R
import com.dopashift.ui.dashboard.DashboardScreen

/**
 * The navigable Dashboard host that layers the Dashboard Quick-Create surface (DQC-1) over the
 * existing populated Dashboard content.
 *
 * This is the composable the navigation graph routes the `DASHBOARD` destination to. It is the
 * single place that assembles the pieces built across the feature:
 *
 *  - [DashboardBody] resolves Zero_State / Partial_State / populated rendering from the one
 *    [com.dopashift.domain.creation.DashboardSummary] feed (DQC-5.3); in the populated/partial
 *    case it hosts the existing [DashboardScreen] content.
 *  - [QuickCreateMenu] renders the persistent FAB and its three-action menu, anchored bottom-end
 *    over the scrolling content so it is reachable without scrolling (DQC-1.1).
 *  - The three Creation_Sheets ([GoalCreationSheet], [HabitCreationSheet], [TodoCreationSheet]) are
 *    presented based on [QuickCreateViewModel.activeSheet]; each routes its save back through the
 *    shared use cases (DQC-1.10, no parallel path).
 *  - The post-creation Undo snackbar (DQC-1.6, DQC-1.7) and the one-shot confirm-discard / notice
 *    events (DQC-1.9, DQC-2.11) are surfaced here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickCreateDashboardScreen(
    onSkipOnboarding: () -> Unit = {},
    onNavigateToInterceptionRules: () -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: QuickCreateViewModel = hiltViewModel(),
) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val activeSheet by viewModel.activeSheet.collectAsStateWithLifecycle()
    val goalSheetState by viewModel.goalSheetState.collectAsStateWithLifecycle()
    val habitSheetState by viewModel.habitSheetState.collectAsStateWithLifecycle()
    val todoSheetState by viewModel.todoSheetState.collectAsStateWithLifecycle()

    var menuState by remember { mutableStateOf(QuickCreateMenuState()) }
    var pendingDiscard by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    val undoLabel = stringResource(R.string.qc_undo_action)

    // Surface the post-creation Undo snackbar (DQC-1.6, DQC-1.7). The Undo action stays available
    // for the snackbar's Long duration; activating it routes the token back to the ViewModel, which
    // deletes through the standard sync-safe path.
    LaunchedEffect(Unit) {
        viewModel.snackbar.collect { snackbar ->
            menuState = QuickCreateMenuState(expanded = false)
            val result = snackbarHostState.showSnackbar(
                message = snackbar.message,
                actionLabel = undoLabel,
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.onUndo(snackbar.undoToken)
            }
        }
    }

    // One-shot effects: confirm-before-discard (DQC-1.9) and non-blocking notices (DQC-2.11).
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is QuickCreateEvent.ConfirmDiscard -> pendingDiscard = true
                is QuickCreateEvent.Notice ->
                    snackbarHostState.showSnackbar(event.message)
                is QuickCreateEvent.FieldErrors ->
                    snackbarHostState.showSnackbar(
                        event.fieldErrors.values.firstOrNull() ?: ""
                    )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.qc_dashboard_zero_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(
                                R.string.qc_dashboard_back_content_description,
                            ),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Zero_State whole-screen prompts, or the populated Dashboard content otherwise.
            DashboardBody(
                summary = summary,
                onPrompt = { prompt -> viewModel.openQuickCreate(prompt.targetSheet()) },
                onSkipOnboarding = onSkipOnboarding,
                modifier = Modifier.fillMaxSize(),
            ) { _ ->
                // Populated / Partial_State: render the existing Dashboard content. The old screen
                // owns its own sections and inline quick-add; the quick-create FAB is layered on top.
                DashboardScreen(onNavigateToInterceptionRules = onNavigateToInterceptionRules)
            }

            // Persistent FAB + Quick_Create_Menu, anchored bottom-end over the content (DQC-1.1).
            QuickCreateMenu(
                state = menuState,
                onToggle = { menuState = QuickCreateMenuState(expanded = !menuState.expanded) },
                onDismiss = { menuState = QuickCreateMenuState(expanded = false) },
                onAction = { action ->
                    menuState = QuickCreateMenuState(expanded = false)
                    // Manage App Limits navigates to the Rule Authoring screen; the creation
                    // actions open their matching Creation_Sheet.
                    if (action == QuickCreateAction.MANAGE_APP_LIMITS) {
                        onNavigateToInterceptionRules()
                    } else {
                        viewModel.openQuickCreate(action.toActiveSheet())
                    }
                },
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
    }

    // Present exactly one Creation_Sheet based on the ViewModel's active-sheet state.
    when (activeSheet) {
        is ActiveSheet.None -> Unit

        is ActiveSheet.Goal -> GoalCreationSheet(
            state = goalSheetState,
            callbacks = GoalSheetCallbacks(
                onSave = { name, category, keywords, description ->
                    viewModel.saveGoal(name, category, keywords, description)
                },
                onDismiss = viewModel::dismissSheet,
                onDismissWithUnsavedInput = viewModel::dismissSheetWithUnsavedInput,
                onCategoryChanged = viewModel::onCategoryChanged,
            ),
        )

        is ActiveSheet.Habit -> HabitCreationSheet(
            state = habitSheetState,
            callbacks = HabitSheetCallbacks(
                onSave = { goalId, authoring, withReminder, inlineGoal ->
                    viewModel.saveHabit(
                        goalId = goalId,
                        authoring = authoring,
                        withReminder = withReminder,
                        inlineGoal = inlineGoal,
                    )
                },
                onDismiss = viewModel::dismissSheet,
                onDismissWithUnsavedInput = viewModel::dismissSheetWithUnsavedInput,
                onCategoryChanged = viewModel::onCategoryChanged,
            ),
        )

        is ActiveSheet.Todo -> {
            var text by remember { mutableStateOf("") }
            TodoCreationSheet(
                state = todoSheetState,
                callbacks = TodoSheetCallbacks(
                    onSave = { todoText, dueDateTime, reminder ->
                        viewModel.saveTodo(todoText, dueDateTime, reminder)
                    },
                    onDismiss = viewModel::dismissSheet,
                    onDismissWithUnsavedInput = viewModel::dismissSheetWithUnsavedInput,
                ),
                text = text,
                onTextChange = { text = it },
                dueDateTime = null,
                reminder = null,
            )
        }

    }

    // Confirm-before-discard dialog for a sheet holding unsaved input (DQC-1.9).
    if (pendingDiscard) {
        ConfirmDiscardDialog(
            onConfirm = {
                pendingDiscard = false
                viewModel.confirmDiscard()
            },
            onDismiss = { pendingDiscard = false },
        )
    }
}

/**
 * Maps a menu action to the Creation_Sheet it opens (DQC-1.2). [QuickCreateAction.MANAGE_APP_LIMITS]
 * navigates instead of opening a sheet, so it is handled before this mapping is ever consulted.
 */
private fun QuickCreateAction.toActiveSheet(): ActiveSheet = when (this) {
    QuickCreateAction.NEW_GOAL -> ActiveSheet.Goal
    QuickCreateAction.NEW_HABIT -> ActiveSheet.Habit
    QuickCreateAction.NEW_TODO -> ActiveSheet.Todo
    QuickCreateAction.MANAGE_APP_LIMITS -> ActiveSheet.None
}

/**
 * Confirm-before-discard dialog shown when the user dismisses a Creation_Sheet that holds unsaved
 * input, rather than dropping it silently (DQC-1.9).
 */
@Composable
private fun ConfirmDiscardDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.qc_discard_title)) },
        text = { Text(stringResource(R.string.qc_discard_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.qc_discard_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.qc_discard_cancel))
            }
        },
    )
}

