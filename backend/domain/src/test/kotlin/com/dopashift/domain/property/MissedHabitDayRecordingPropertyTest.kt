package com.dopashift.domain.property

import com.dopashift.domain.entity.CheckpointStatus
import com.dopashift.domain.entity.HabitCheckpoint
import com.dopashift.domain.entity.HabitTrack
import com.dopashift.domain.repository.HabitTrackRepository
import com.dopashift.domain.usecase.habit.RecordMissedDayUseCase
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.localDate
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.uuid
import io.kotest.property.checkAll
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Property 14: Missed Habit Day Recording
 *
 * For any Habit_Track checkpoint where the user did not check off the micro-habit
 * before midnight in their configured timezone, the system SHALL record that day
 * as MISSED with the date - never skip silently.
 *
 * **Validates: Requirements 6.4**
 *
 * Tags: Feature: dopa-shift, Property 14: Missed Habit Day Recording
 */
class MissedHabitDayRecordingPropertyTest : FunSpec({

    tags(
        io.kotest.core.Tag("Feature: dopa-shift"),
        io.kotest.core.Tag("Property 14: Missed Habit Day Recording")
    )

    // --- In-Memory Repository ---

    class InMemoryHabitTrackRepository : HabitTrackRepository {
        val store = mutableMapOf<UUID, HabitTrack>()

        override suspend fun findById(id: UUID): HabitTrack? = store[id]
        override suspend fun findActiveByGoalId(goalId: UUID): List<HabitTrack> =
            store.values.filter { it.goalId == goalId && !it.isFinished }

        override suspend fun findActiveByUserId(userId: UUID): List<HabitTrack> =
            store.values.filter { it.userId == userId && !it.isFinished }

        override suspend fun save(track: HabitTrack): HabitTrack {
            store[track.id] = track
            return track
        }

        override suspend fun deleteByGoalId(goalId: UUID) {
            store.entries.removeIf { it.value.goalId == goalId }
        }

        override suspend fun reassignToGoal(fromGoalId: UUID, toGoalId: UUID) {
            store.replaceAll { _, track ->
                if (track.goalId == fromGoalId) track.copy(goalId = toGoalId) else track
            }
        }

        override suspend fun countActiveByUserId(userId: UUID): Int =
            store.values.count { it.userId == userId && !it.isFinished }
    }

    // --- Generators ---

    fun arbCheckpoint(habitTrackId: UUID, dayNumber: Int, status: CheckpointStatus): HabitCheckpoint =
        HabitCheckpoint(
            id = UUID.randomUUID(),
            habitTrackId = habitTrackId,
            dayNumber = dayNumber,
            description = "Micro-habit day $dayNumber",
            status = status,
            completedAt = if (status == CheckpointStatus.COMPLETED) Instant.now() else null
        )

    /**
     * Generates a HabitTrack with a mix of checkpoint statuses.
     * At least one checkpoint will have the specified [targetStatus] at the given [targetDay].
     */
    fun arbHabitTrackWithCheckpoints(
        targetDay: Int,
        targetStatus: CheckpointStatus
    ): Arb<HabitTrack> = Arb.bind(
        Arb.uuid(),
        Arb.uuid(),
        Arb.uuid()
    ) { trackId, goalId, userId ->
        val checkpoints = (1..30).map { day ->
            val status = if (day == targetDay) targetStatus
            else listOf(CheckpointStatus.PENDING, CheckpointStatus.COMPLETED, CheckpointStatus.MISSED).random()
            arbCheckpoint(trackId, day, status)
        }
        HabitTrack(
            id = trackId,
            goalId = goalId,
            userId = userId,
            startDate = LocalDate.now().minusDays(targetDay.toLong()),
            currentDay = minOf(targetDay + 1, 30),
            isFinished = false,
            checkpoints = checkpoints
        )
    }

    // --- Property Tests ---

    test("PENDING checkpoints are marked as MISSED, never silently skipped") {
        checkAll(100, Arb.int(1..30), arbHabitTrackWithCheckpoints(1, CheckpointStatus.PENDING)) { targetDay, baseTrack ->
            // Generate a track where targetDay is PENDING
            val trackId = baseTrack.id
            val checkpoints = baseTrack.checkpoints.map { cp ->
                if (cp.dayNumber == targetDay) cp.copy(status = CheckpointStatus.PENDING, completedAt = null)
                else cp
            }
            val track = baseTrack.copy(
                checkpoints = checkpoints,
                currentDay = minOf(targetDay + 1, 30)
            )

            val repo = InMemoryHabitTrackRepository()
            repo.store[trackId] = track

            val useCase = RecordMissedDayUseCase(repo)
            val result = useCase.execute(trackId, targetDay)

            // Assert: checkpoint is now MISSED
            val updatedCheckpoint = result.checkpoints.find { it.dayNumber == targetDay }!!
            updatedCheckpoint.status shouldBe CheckpointStatus.MISSED
        }
    }

    test("calling RecordMissedDayUseCase on an already-MISSED checkpoint throws IllegalStateException") {
        checkAll(100, Arb.int(1..30), arbHabitTrackWithCheckpoints(1, CheckpointStatus.MISSED)) { targetDay, baseTrack ->
            // Generate a track where targetDay is already MISSED
            val trackId = baseTrack.id
            val checkpoints = baseTrack.checkpoints.map { cp ->
                if (cp.dayNumber == targetDay) cp.copy(status = CheckpointStatus.MISSED, completedAt = null)
                else cp
            }
            val track = baseTrack.copy(
                checkpoints = checkpoints,
                currentDay = minOf(targetDay + 1, 30)
            )

            val repo = InMemoryHabitTrackRepository()
            repo.store[trackId] = track

            val useCase = RecordMissedDayUseCase(repo)

            // Assert: calling again on already-MISSED day throws (already resolved)
            shouldThrow<IllegalStateException> {
                useCase.execute(trackId, targetDay)
            }
        }
    }

    test("COMPLETED checkpoints cannot be overwritten to MISSED") {
        checkAll(100, Arb.int(1..30), arbHabitTrackWithCheckpoints(1, CheckpointStatus.COMPLETED)) { targetDay, baseTrack ->
            // Generate a track where targetDay is COMPLETED
            val trackId = baseTrack.id
            val checkpoints = baseTrack.checkpoints.map { cp ->
                if (cp.dayNumber == targetDay) cp.copy(status = CheckpointStatus.COMPLETED, completedAt = Instant.now())
                else cp
            }
            val track = baseTrack.copy(
                checkpoints = checkpoints,
                currentDay = minOf(targetDay + 1, 30)
            )

            val repo = InMemoryHabitTrackRepository()
            repo.store[trackId] = track

            val useCase = RecordMissedDayUseCase(repo)

            // Assert: trying to mark a COMPLETED checkpoint as MISSED throws
            shouldThrow<IllegalStateException> {
                useCase.execute(trackId, targetDay)
            }
        }
    }
})
