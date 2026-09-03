package com.dopashift.domain.creation

import com.dopashift.domain.entity.GoalProfile
import com.dopashift.domain.entity.HabitTrack
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Command to create a [GoalProfile] from any [CreationOrigin] (DQC-2.1).
 *
 * The Dashboard collects the same fields the dedicated Goals screen does and drives
 * the same use case, so validation and Change_Log emission are identical (DQC-1.10).
 * Field bounds mirror [GoalProfile]'s own invariants and are re-checked server-side;
 * client validation is only a usability layer (DQC-1.11).
 *
 * @property userId the authenticated owner; every read/write is scoped by it.
 * @property name goal name, 1-100 chars, unique per user (case-insensitive).
 * @property category goal category, 1-50 chars.
 * @property keywords 1-20 keywords, each 1-50 chars.
 * @property description optional free-text description.
 * @property correlationId the [CorrelationId] generated at the action's origin (DQC-6.3).
 * @property origin where the action was initiated; defaults to [CreationOrigin.DASHBOARD].
 */
data class CreateGoalCommand(
    val userId: UserId,
    val name: String,
    val category: String,
    val keywords: List<String>,
    val description: String? = null,
    val correlationId: CorrelationId,
    val origin: CreationOrigin = CreationOrigin.DASHBOARD
)

/**
 * Command to create a standalone [com.dopashift.domain.entity.DailyTodoItem] (DQC-4.1).
 *
 * A to-do is never associated with a goal; its `dayDate` is the current date in the
 * user's configured time zone (DQC-4.3). The full sheet may additionally collect an
 * optional due date/time and an optional [ReminderDraft] (DQC-4.4).
 *
 * @property userId the authenticated owner.
 * @property text the to-do text, non-blank and at most 500 chars.
 * @property dayDate the day the item belongs to (current date in the user's tz).
 * @property dueDateTime optional due date/time.
 * @property reminder optional reminder configuration collected in the full sheet.
 * @property correlationId the [CorrelationId] generated at the action's origin (DQC-6.3).
 * @property origin where the action was initiated; defaults to [CreationOrigin.DASHBOARD].
 */
data class CreateTodoCommand(
    val userId: UserId,
    val text: String,
    val dayDate: LocalDate,
    val dueDateTime: Instant? = null,
    val reminder: ReminderDraft? = null,
    val correlationId: CorrelationId,
    val origin: CreationOrigin = CreationOrigin.DASHBOARD
)

/**
 * Command to create a [HabitTrack] for an existing, saved goal (DQC-3.5).
 *
 * The [goalId] MUST reference a goal that is already persisted; when a zero-goal user
 * starts a habit, use [InlineGoalWithHabitCommand] instead so goal and habit are
 * created as one atomic unit (DQC-3.2, DQC-3.4). The [authoring] mode is resolved to
 * exactly 30 checkpoints before persistence.
 *
 * @property userId the authenticated owner.
 * @property goalId the existing, saved goal this track belongs to.
 * @property authoring how the 30 checkpoints are sourced (Template | Llm | Manual).
 * @property withReminder whether a daily checkpoint Reminder is requested (offered, not required — DQC-3.12).
 * @property correlationId the [CorrelationId] generated at the action's origin (DQC-6.3).
 * @property origin where the action was initiated; defaults to [CreationOrigin.DASHBOARD].
 */
data class CreateHabitCommand(
    val userId: UserId,
    val goalId: GoalId,
    val authoring: HabitAuthoring,
    val withReminder: Boolean = false,
    val correlationId: CorrelationId,
    val origin: CreationOrigin = CreationOrigin.DASHBOARD
)

/**
 * Command for Inline_Goal_Capture: create a [GoalProfile] and a dependent [HabitTrack]
 * as one atomic transaction (DQC-3.2, DQC-3.3, DQC-6.2).
 *
 * The goal fields are collected inline so a zero-goal user can start a habit without
 * navigating away or hitting a blocking error. A single [correlationId] spans the whole
 * transaction and is shared with the nested [goal] command so all downstream writes and
 * diagnostics are correlated (DQC-6.3).
 *
 * @property goal the goal fields collected inline.
 * @property authoring how the habit's 30 checkpoints are sourced.
 * @property withReminder whether a daily checkpoint Reminder is requested.
 * @property correlationId the [CorrelationId] shared across the whole transaction.
 */
data class InlineGoalWithHabitCommand(
    val goal: CreateGoalCommand,
    val authoring: HabitAuthoring,
    val withReminder: Boolean = false,
    val correlationId: CorrelationId
)

/**
 * The result of a successful Inline_Goal_Capture: the created goal and its dependent
 * habit, returned together so the UI can reflect both and offer a whole-unit Undo.
 *
 * @property goal the created goal.
 * @property habit the created habit, referencing [goal]'s id.
 */
data class GoalWithHabit(
    val goal: GoalProfile,
    val habit: HabitTrack
)

/**
 * How a [HabitTrack]'s 30 daily checkpoints are sourced when creating a habit (DQC-3.5).
 *
 * All three modes resolve to exactly 30 checkpoints before persistence; the resolution
 * itself lives in a later use case. Template and Manual work fully offline; Llm is
 * optional and degrades gracefully to Template when unconfigured or on failure (DQC-3.7, DQC-3.8).
 */
sealed interface HabitAuthoring {
    /**
     * Use a bundled 30-checkpoint template for the goal's category (offline, DQC-3.6).
     *
     * @property templateId the bundled template identifier to load.
     */
    data class Template(val templateId: String) : HabitAuthoring {
        init {
            require(templateId.isNotBlank()) { "templateId must not be blank" }
        }
    }

    /**
     * Use LLM-generated checkpoint descriptions (DQC-3.7). Expected to carry 30 entries;
     * on any failure the caller falls back to [Template] without losing input (DQC-3.8).
     *
     * @property generated the generated checkpoint descriptions.
     */
    data class Llm(val generated: List<String>) : HabitAuthoring

    /**
     * Use user-authored checkpoint descriptions (offline, DQC-3.9). May contain fewer
     * than 30 entries; remaining days are auto-filled with the last authored description
     * (disclosed before saving) during resolution.
     *
     * @property authored the user-authored checkpoint descriptions (may be < 30).
     */
    data class Manual(val authored: List<String>) : HabitAuthoring
}

/**
 * An optional, pre-persistence reminder configuration collected in the full to-do
 * Creation_Sheet (DQC-4.4), mapped to the shared Reminder model on save.
 *
 * This is a lightweight draft rather than a full
 * [com.dopashift.domain.entity.Reminder] because the reminder's entity id does not
 * exist until the owning to-do is persisted.
 *
 * @property time the local time of day the reminder fires.
 * @property date optional specific date; null implies a recurring/daily reminder.
 */
data class ReminderDraft(
    val time: LocalTime,
    val date: LocalDate? = null
)
