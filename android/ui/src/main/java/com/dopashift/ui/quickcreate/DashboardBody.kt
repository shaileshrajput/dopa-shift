package com.dopashift.ui.quickcreate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dopashift.domain.creation.DashboardSection
import com.dopashift.domain.creation.DashboardSummary
import com.dopashift.domain.creation.OnboardingStatus
import com.dopashift.domain.creation.SectionRenderState
import com.dopashift.ui.R
import com.dopashift.ui.theme.DopaShiftTheme

/**
 * A contextual creation prompt surfaced by [DashboardBody], each of which directly opens the
 * corresponding [ActiveSheet] Creation_Sheet (DQC-5.2). The three prompts map one-to-one to the
 * three Dashboard entity types and the three [QuickCreateAction]s.
 */
enum class CreationPrompt {
    /** Prompts creating a Goal_Profile; opens the goal Creation_Sheet (DQC-2). */
    CREATE_GOAL,

    /** Prompts adding a DailyTodoItem; opens the to-do Creation_Sheet (DQC-4.4). */
    ADD_TODO,

    /** Prompts starting a Habit_Track; opens the habit Creation_Sheet (DQC-3). */
    START_HABIT,
}

/** Which [ActiveSheet] a [CreationPrompt] opens. */
fun CreationPrompt.targetSheet(): ActiveSheet = when (this) {
    CreationPrompt.CREATE_GOAL -> ActiveSheet.Goal
    CreationPrompt.ADD_TODO -> ActiveSheet.Todo
    CreationPrompt.START_HABIT -> ActiveSheet.Habit
}

/**
 * The Dashboard's Zero_State / Partial_State creation surface (DQC-5.2, DQC-5.3, DQC-5.6).
 *
 * Rendering is driven entirely by a single [DashboardSummary] so the state path issues no more
 * than one query (Backend Delta 2):
 *
 *  - **Zero_State** — [DashboardSummary.isZeroState] is true (all three counts zero): a
 *    whole-screen empty state showing exactly three contextual creation prompts — create a goal,
 *    add a to-do, start a habit — each directly opening its Creation_Sheet via [onPrompt]
 *    (DQC-5.2). A whole-screen empty state is rendered *only* here (DQC-5.3).
 *  - **Partial_State** — at least one count is non-zero and at least one is zero: [populatedContent]
 *    renders the sections that have data normally, while each empty section is rendered as an
 *    inline prompt (DQC-5.3). Goal-dependent sections stay visible as inline prompts so the
 *    capability remains discoverable (DQC-5.6).
 *  - **Populated** — every section has data: only [populatedContent] is shown, with no prompts.
 *
 * A Zero_State prompt is suppressed for an entity type created during onboarding
 * ([DashboardSummary.suppressesZeroStatePrompt], DQC-5.7); when all three prompts are suppressed
 * the whole-screen empty state falls back to [populatedContent].
 *
 * The host owns the populated section rendering and passes it as [populatedContent] so this
 * composable stays focused on state resolution and prompt placement and remains testable in
 * isolation. The [populatedContent] slot receives a `sectionInlinePrompt` builder that renders an
 * inline prompt for an empty section, which the host interleaves with its populated sections.
 *
 * @param summary the single state feed distinguishing Zero/Partial/populated (DQC-5.3).
 * @param onPrompt invoked when the user activates any creation prompt; the host opens
 *   `prompt.targetSheet()` (DQC-5.2).
 * @param onSkipOnboarding invoked by the "Skip" affordance, which lands the user on Zero_State
 *   (DQC-5.1). Shown only while onboarding has not yet been completed or skipped.
 * @param modifier layout modifier for the body container.
 * @param populatedContent the host's normal Dashboard content for Partial_State / populated. It is
 *   given a `sectionInlinePrompt` lambda so the host renders each empty section as an inline prompt
 *   in place (DQC-5.3, DQC-5.6).
 */
@Composable
fun DashboardBody(
    summary: DashboardSummary,
    onPrompt: (CreationPrompt) -> Unit,
    onSkipOnboarding: () -> Unit,
    modifier: Modifier = Modifier,
    populatedContent: @Composable (sectionInlinePrompt: @Composable (DashboardSection) -> Unit) -> Unit,
) {
    // The prompts that would show in Zero_State, minus any suppressed for an onboarding-created
    // entity type (DQC-5.7). If onboarding produced a goal, the goal prompt is not re-shown.
    val visibleZeroStatePrompts = CreationPrompt.entries.filterNot {
        summary.suppressesZeroStatePrompt(it.section())
    }

    // Whole-screen empty state ONLY in Zero_State (DQC-5.3), and only if at least one prompt is
    // still visible after onboarding suppression — otherwise fall through to the host content.
    if (summary.isZeroState && visibleZeroStatePrompts.isNotEmpty()) {
        ZeroState(
            prompts = visibleZeroStatePrompts,
            onboardingActive = summary.onboardingStatus == OnboardingStatus.NOT_STARTED,
            onPrompt = onPrompt,
            onSkipOnboarding = onSkipOnboarding,
            modifier = modifier,
        )
        return
    }

    // Partial_State / populated: host renders populated sections normally and each empty section as
    // an inline prompt in place (DQC-5.3, DQC-5.6).
    populatedContent { section ->
        if (summary.sectionState(section) == SectionRenderState.INLINE_PROMPT) {
            SectionInlinePrompt(
                section = section,
                onPrompt = { onPrompt(section.prompt()) },
            )
        }
    }
}

/**
 * The whole-screen Zero_State: a heading plus exactly the three (unsuppressed) creation prompts,
 * each a full-width card that opens its Creation_Sheet (DQC-5.2). While onboarding has not yet been
 * completed/skipped, a "Skip" affordance is offered that lands the user here (DQC-5.1).
 */
@Composable
private fun ZeroState(
    prompts: List<CreationPrompt>,
    onboardingActive: Boolean,
    onPrompt: (CreationPrompt) -> Unit,
    onSkipOnboarding: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = DopaShiftTheme.spacing

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(spacing.margin)
            .testTag(TestTags.DASHBOARD_ZERO_STATE),
        verticalArrangement = Arrangement.spacedBy(spacing.gutter),
    ) {
        // Skip affordance lands the user on this Zero_State from onboarding (DQC-5.1).
        if (onboardingActive) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onSkipOnboarding,
                    modifier = Modifier.testTag(TestTags.DASHBOARD_SKIP_ONBOARDING),
                ) {
                    Text(stringResource(R.string.qc_dashboard_skip_onboarding))
                }
            }
        }

        Spacer(modifier = Modifier.height(spacing.base))

        Text(
            text = stringResource(R.string.qc_dashboard_zero_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.qc_dashboard_zero_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(spacing.base))

        prompts.forEach { prompt ->
            ZeroStatePromptCard(
                prompt = prompt,
                onClick = { onPrompt(prompt) },
            )
        }
    }
}

/**
 * A single full-width Zero_State prompt card. Tapping it (or its button) opens the matching
 * Creation_Sheet (DQC-5.2). The whole card exposes its title as its accessible action.
 */
@Composable
private fun ZeroStatePromptCard(
    prompt: CreationPrompt,
    onClick: () -> Unit,
) {
    val spacing = DopaShiftTheme.spacing
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(prompt.zeroStateTestTag()),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.gutter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.gutter),
        ) {
            Icon(
                imageVector = prompt.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(prompt.titleRes()),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(prompt.bodyRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onClick) {
                Text(stringResource(prompt.actionRes()))
            }
        }
    }
}

/**
 * An inline prompt for a single empty section in Partial_State (DQC-5.3). Goal-dependent sections
 * render here rather than being hidden so the capability stays discoverable (DQC-5.6).
 */
@Composable
private fun SectionInlinePrompt(
    section: DashboardSection,
    onPrompt: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = DopaShiftTheme.spacing
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(section.inlinePromptTestTag()),
        verticalArrangement = Arrangement.spacedBy(spacing.base),
    ) {
        Text(
            text = stringResource(section.titleRes()),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            shape = RoundedCornerShape(16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.gutter),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.gutter),
            ) {
                Icon(
                    imageVector = section.prompt().icon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(section.prompt().bodyRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = onPrompt) {
                    Text(stringResource(R.string.qc_dashboard_inline_prompt_action))
                }
            }
        }
    }
}

/**
 * A dismissible "resume onboarding" entry for the Settings screen, shown only to a user who
 * skipped onboarding and has not yet dismissed the entry (DQC-5.9).
 *
 * The dismissal must be remembered across launches; the host persists it (via `QuickCreatePrefs`)
 * so this composable stays a pure function of [visible]. Nothing is rendered when [visible] is
 * false, so the caller can drive it directly from the persisted flag.
 *
 * @param visible whether to show the entry: true only when onboarding was skipped and the entry's
 *   dismissal has not been recorded.
 * @param onResume invoked when the user chooses to resume onboarding.
 * @param onDismiss invoked when the user dismisses the entry; the host records the dismissal so it
 *   is not shown again (DQC-5.9).
 */
@Composable
fun ResumeOnboardingSettingsEntry(
    visible: Boolean,
    onResume: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    val spacing = DopaShiftTheme.spacing

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TestTags.SETTINGS_RESUME_ONBOARDING),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.gutter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.base),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.qc_settings_resume_onboarding_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.qc_settings_resume_onboarding_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onResume) {
                Text(stringResource(R.string.qc_settings_resume_onboarding_title))
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(TestTags.SETTINGS_RESUME_ONBOARDING_DISMISS),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(
                        R.string.qc_settings_resume_onboarding_dismiss_content_description,
                    ),
                )
            }
        }
    }
}

// ---- Prompt <-> section <-> resource mappings ---------------------------------------

/** The section a prompt creates into. */
private fun CreationPrompt.section(): DashboardSection = when (this) {
    CreationPrompt.CREATE_GOAL -> DashboardSection.GOALS
    CreationPrompt.ADD_TODO -> DashboardSection.TODOS
    CreationPrompt.START_HABIT -> DashboardSection.HABITS
}

/** The prompt that fills a given empty section. */
private fun DashboardSection.prompt(): CreationPrompt = when (this) {
    DashboardSection.GOALS -> CreationPrompt.CREATE_GOAL
    DashboardSection.TODOS -> CreationPrompt.ADD_TODO
    DashboardSection.HABITS -> CreationPrompt.START_HABIT
}

private fun CreationPrompt.icon(): ImageVector = when (this) {
    CreationPrompt.CREATE_GOAL -> Icons.Outlined.Flag
    CreationPrompt.ADD_TODO -> Icons.Outlined.CheckCircle
    CreationPrompt.START_HABIT -> Icons.Outlined.Repeat
}

private fun CreationPrompt.titleRes(): Int = when (this) {
    CreationPrompt.CREATE_GOAL -> R.string.qc_dashboard_prompt_goal_title
    CreationPrompt.ADD_TODO -> R.string.qc_dashboard_prompt_todo_title
    CreationPrompt.START_HABIT -> R.string.qc_dashboard_prompt_habit_title
}

private fun CreationPrompt.bodyRes(): Int = when (this) {
    CreationPrompt.CREATE_GOAL -> R.string.qc_dashboard_prompt_goal_body
    CreationPrompt.ADD_TODO -> R.string.qc_dashboard_prompt_todo_body
    CreationPrompt.START_HABIT -> R.string.qc_dashboard_prompt_habit_body
}

private fun CreationPrompt.actionRes(): Int = when (this) {
    CreationPrompt.CREATE_GOAL -> R.string.qc_dashboard_prompt_goal_title
    CreationPrompt.ADD_TODO -> R.string.qc_dashboard_prompt_todo_title
    CreationPrompt.START_HABIT -> R.string.qc_dashboard_prompt_habit_title
}

private fun DashboardSection.titleRes(): Int = when (this) {
    DashboardSection.GOALS -> R.string.qc_dashboard_section_goals_title
    DashboardSection.TODOS -> R.string.qc_dashboard_section_todos_title
    DashboardSection.HABITS -> R.string.qc_dashboard_section_habits_title
}

private fun CreationPrompt.zeroStateTestTag(): String = when (this) {
    CreationPrompt.CREATE_GOAL -> TestTags.DASHBOARD_ZERO_PROMPT_GOAL
    CreationPrompt.ADD_TODO -> TestTags.DASHBOARD_ZERO_PROMPT_TODO
    CreationPrompt.START_HABIT -> TestTags.DASHBOARD_ZERO_PROMPT_HABIT
}

private fun DashboardSection.inlinePromptTestTag(): String = when (this) {
    DashboardSection.GOALS -> TestTags.DASHBOARD_INLINE_PROMPT_GOALS
    DashboardSection.TODOS -> TestTags.DASHBOARD_INLINE_PROMPT_TODOS
    DashboardSection.HABITS -> TestTags.DASHBOARD_INLINE_PROMPT_HABITS
}
