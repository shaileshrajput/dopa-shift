package com.dopashift.domain.creation

// Feature: dashboard-quick-create
// Tags: DQC-2.1, DQC-4.1

import com.dopashift.domain.entity.CheckpointStatus
import com.dopashift.domain.entity.GoalProfile
import com.dopashift.domain.entity.HabitCheckpoint
import com.dopashift.domain.entity.HabitTrack
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * Unit tests for Dashboard quick-create command and [CreationResult] construction and
 * defaults (tasks.md 1.3).
 *
 * Verifies that:
 * - `origin` defaults to [CreationOrigin.DASHBOARD] on every command that carries it
 *   (DQC-2.1 for goals, DQC-4.1 for to-dos, plus habits and inline goal+habit), and
 *   that an explicit non-default origin is retained.
 * - Each [CreationResult] variant carries the expected payload and enforces its
 *   documented invariants (non-empty field errors, non-blank rejection reason,
 *   Success carries a valid [UndoToken]).
 *
 * These are pure construction/default checks, so no repositories or use cases are
 * involved — the domain module stays pure Kotlin.
 *
 * **Validates: Requirements 2.1, 4.1**
 */
class CreationCommandDefaultsTest : StringSpec({

    // --- Fixtures ---

    val userId: UserId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    val goalId: GoalId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    val correlationId = CorrelationId.random()

    fun sampleGoal(): GoalProfile = GoalProfile(
        id = goalId,
        userId = userId,
        name = "Run daily",
        category = "Fitness",
        keywords = listOf("running"),
        createdAt = Instant.parse("2026-09-01T08:00:00Z"),
        updatedAt = Instant.parse("2026-09-01T08:00:00Z")
    )

    fun sampleHabit(): HabitTrack = HabitTrack(
        id = UUID.fromString("00000000-0000-0000-0000-000000000003"),
        goalId = goalId,
        userId = userId,
        startDate = LocalDate.of(2026, 9, 1),
        currentDay = 1,
        checkpoints = listOf(
            HabitCheckpoint(
                id = UUID.fromString("00000000-0000-0000-0000-000000000004"),
                habitTrackId = UUID.fromString("00000000-0000-0000-0000-000000000003"),
                dayNumber = 1,
                description = "Lace up and walk 5 minutes",
                status = CheckpointStatus.PENDING
            )
        )
    )

    fun goalCommand(origin: CreationOrigin = CreationOrigin.DASHBOARD): CreateGoalCommand =
        CreateGoalCommand(
            userId = userId,
            name = "Run daily",
            category = "Fitness",
            keywords = listOf("running", "cardio"),
            correlationId = correlationId,
            origin = origin
        )

    // --- origin defaults to DASHBOARD (DQC-2.1 / DQC-4.1) ---

    "CreateGoalCommand origin defaults to DASHBOARD" {
        val command = CreateGoalCommand(
            userId = userId,
            name = "Run daily",
            category = "Fitness",
            keywords = listOf("running"),
            correlationId = correlationId
        )

        command.origin shouldBe CreationOrigin.DASHBOARD
        command.description shouldBe null
    }

    "CreateTodoCommand origin defaults to DASHBOARD" {
        val command = CreateTodoCommand(
            userId = userId,
            text = "Buy groceries",
            dayDate = LocalDate.of(2026, 9, 1),
            correlationId = correlationId
        )

        command.origin shouldBe CreationOrigin.DASHBOARD
        command.dueDateTime shouldBe null
        command.reminder shouldBe null
    }

    "CreateHabitCommand origin defaults to DASHBOARD and withReminder defaults to false" {
        val command = CreateHabitCommand(
            userId = userId,
            goalId = goalId,
            authoring = HabitAuthoring.Template("fitness-default"),
            correlationId = correlationId
        )

        command.origin shouldBe CreationOrigin.DASHBOARD
        command.withReminder shouldBe false
    }

    "InlineGoalWithHabitCommand withReminder defaults to false and retains nested goal command" {
        val nestedGoal = goalCommand()
        val command = InlineGoalWithHabitCommand(
            goal = nestedGoal,
            authoring = HabitAuthoring.Manual(listOf("Day one")),
            correlationId = correlationId
        )

        command.withReminder shouldBe false
        command.goal shouldBe nestedGoal
        // Nested goal command keeps its own DASHBOARD default.
        command.goal.origin shouldBe CreationOrigin.DASHBOARD
    }

    // --- explicit non-default origin is retained ---

    "CreateGoalCommand retains an explicitly supplied non-default origin" {
        goalCommand(origin = CreationOrigin.DEDICATED_SCREEN).origin shouldBe
            CreationOrigin.DEDICATED_SCREEN
        goalCommand(origin = CreationOrigin.INTERCEPT_OVERLAY).origin shouldBe
            CreationOrigin.INTERCEPT_OVERLAY
    }

    "CreateTodoCommand retains optional fields when supplied" {
        val due = Instant.parse("2026-09-01T18:00:00Z")
        val reminder = ReminderDraft(time = LocalTime.of(9, 0), date = LocalDate.of(2026, 9, 1))
        val command = CreateTodoCommand(
            userId = userId,
            text = "Submit report",
            dayDate = LocalDate.of(2026, 9, 1),
            dueDateTime = due,
            reminder = reminder,
            correlationId = correlationId,
            origin = CreationOrigin.DEDICATED_SCREEN
        )

        command.dueDateTime shouldBe due
        command.reminder shouldBe reminder
        command.origin shouldBe CreationOrigin.DEDICATED_SCREEN
    }

    // --- CreationResult.Success carries value + UndoToken (DQC-2.1) ---

    "Success carries the created value and an UndoToken referencing it" {
        val goal = sampleGoal()
        val undo = UndoToken(
            entityRefs = listOf(EntityRef(type = "goal", id = goal.id)),
            correlationId = correlationId
        )

        val result: CreationResult<GoalProfile> = CreationResult.Success(value = goal, undo = undo)

        val success = result.shouldBeInstanceOf<CreationResult.Success<GoalProfile>>()
        success.value shouldBe goal
        success.undo shouldBe undo
        success.undo.correlationId shouldBe correlationId
        success.undo.entityRefs.map { it.id } shouldContainExactly listOf(goal.id)
    }

    "Success for Inline_Goal_Capture carries both goal and habit refs in the UndoToken" {
        val goal = sampleGoal()
        val habit = sampleHabit()
        val undo = UndoToken(
            entityRefs = listOf(
                EntityRef(type = "goal", id = goal.id),
                EntityRef(type = "habit_track", id = habit.id)
            ),
            correlationId = correlationId
        )

        val result: CreationResult<GoalWithHabit> =
            CreationResult.Success(value = GoalWithHabit(goal, habit), undo = undo)

        val success = result.shouldBeInstanceOf<CreationResult.Success<GoalWithHabit>>()
        success.value.goal shouldBe goal
        success.value.habit shouldBe habit
        success.undo.entityRefs.map { it.type } shouldContainExactly listOf("goal", "habit_track")
    }

    // --- CreationResult.ValidationError carries a non-empty field-error map (DQC-2.1) ---

    "ValidationError carries the supplied field errors" {
        val errors = mapOf("name" to "Name is required", "keywords" to "At least one keyword required")

        val result = CreationResult.ValidationError(errors)

        result.fieldErrors shouldBe errors
    }

    "ValidationError rejects an empty field-error map" {
        shouldThrow<IllegalArgumentException> {
            CreationResult.ValidationError(emptyMap())
        }
    }

    // --- CreationResult.Rejected carries a non-blank reason + retained input (DQC-2.1) ---

    "Rejected carries a non-blank reason and the retained input" {
        val retained = goalCommand()

        val result = CreationResult.Rejected(reason = "Name already taken", retainedInput = retained)

        result.reason shouldBe "Name already taken"
        result.retainedInput shouldBe retained
    }

    "Rejected allows a null retained input but still requires a non-blank reason" {
        val result = CreationResult.Rejected(reason = "Conflict on sync", retainedInput = null)

        result.reason shouldBe "Conflict on sync"
        result.retainedInput shouldBe null
    }

    "Rejected rejects a blank reason" {
        shouldThrow<IllegalArgumentException> {
            CreationResult.Rejected(reason = "   ", retainedInput = null)
        }
    }

    // --- CreationResult.Failure carries the cause ---

    "Failure carries the underlying cause" {
        val cause = IllegalStateException("habit save failed, transaction rolled back")

        val result = CreationResult.Failure(cause)

        result.cause shouldBe cause
    }
})
