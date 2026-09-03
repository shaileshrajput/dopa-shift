package com.dopashift.domain.property

import com.dopashift.domain.entity.CheckpointStatus
import com.dopashift.domain.entity.HabitCheckpoint
import com.dopashift.domain.entity.HabitTrack
import com.dopashift.domain.repository.HabitTrackRepository
import com.dopashift.domain.usecase.habit.AdvanceDayUseCase
import com.dopashift.domain.usecase.habit.MarkCheckpointUseCase
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.localDate
import io.kotest.property.arbitrary.uuid
import io.kotest.property.checkAll
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Property 13: Habit Track Day Advancement Correctness
 *
 * For any Habit_Track, when a checkpoint is marked completed, the current-day pointer
 * SHALL advance to the next uncompleted day, and the completed checkpoint SHALL have
 * a UTC timestamp recorded.
 *
 * **Validates: Requirements 6.3**
 *
 * Tags: Feature: dopa-shift, Property 13: Habit Track Day Advancement Correctness
 */
class HabitTrackDayAdvancementPropertyTest : FunSpec({

    tags(
        io.kotest.core.Tag("Feature: dopa-shift"),
        io.kotest.core.Tag("Property 13: Habit Track Day Advancement Correctness")
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

    // --- Generator: random HabitTrack with 30 checkpoints (mix of PENDING, COMPLETED, MISSED) ---

    /**
     * Generates a HabitTrack with 30 checkpoints where each checkpoint has a random status.
     * The currentDay is set to the first PENDING checkpoint (ensuring the track is actionable).
     * At least one checkpoint is guaranteed to be PENDING (the currentDay).
     */
    fun arbHabitTrack(): Arb<HabitTrack> = Arb.bind(
        Arb.uuid(),
        Arb.uuid(),
        Arb.uuid(),
        Arb.localDate(minDate = LocalDate.of(2024, 1, 1), maxDate = LocalDate.of(2025, 12, 31)),
        // Generate 30 statuses: 0=PENDING, 1=COMPLETED, 2=MISSED
        Arb.list(Arb.int(0..2), range = 30..30)
    ) { trackId, goalId, userId, startDate, statusCodes ->
        val checkpoints = (1..30).map { dayNumber ->
            val statusCode = statusCodes[dayNumber - 1]
            val status = when (statusCode) {
                1 -> CheckpointStatus.COMPLETED
                2 -> CheckpointStatus.MISSED
                else -> CheckpointStatus.PENDING
            }
            HabitCheckpoint(
                id = UUID.randomUUID(),
                habitTrackId = trackId,
                dayNumber = dayNumber,
                description = "Day $dayNumber habit task",
                status = status,
                completedAt = if (status == CheckpointStatus.COMPLETED) Instant.now() else null
            )
        }

        // Ensure at least one PENDING checkpoint exists by forcing one if needed
        val pendingCheckpoints = checkpoints.filter { it.status == CheckpointStatus.PENDING }
        val finalCheckpoints = if (pendingCheckpoints.isEmpty()) {
            // Force the last checkpoint to PENDING
            checkpoints.mapIndexed { idx, cp ->
                if (idx == 29) cp.copy(status = CheckpointStatus.PENDING, completedAt = null)
                else cp
            }
        } else {
            checkpoints
        }

        // Set currentDay to the first PENDING checkpoint
        val firstPendingDay = finalCheckpoints.first { it.status == CheckpointStatus.PENDING }.dayNumber

        HabitTrack(
            id = trackId,
            goalId = goalId,
            userId = userId,
            startDate = startDate,
            currentDay = firstPendingDay,
            isFinished = false,
            checkpoints = finalCheckpoints
        )
    }

    // --- Property Tests ---

    test("marking current day as completed advances pointer to next PENDING day and records UTC timestamp") {
        checkAll(100, arbHabitTrack()) { track ->
            val repo = InMemoryHabitTrackRepository()
            repo.save(track)

            val markUseCase = MarkCheckpointUseCase(repo)
            val advanceUseCase = AdvanceDayUseCase(repo)

            val currentDay = track.currentDay
            val completedAt = Instant.now()

            // Mark the current day as completed
            markUseCase.execute(track.id, currentDay, completedAt)

            // Advance day
            val advancedTrack = advanceUseCase.execute(track.id)

            // Assert 1: The completed checkpoint has completedAt != null (UTC timestamp recorded)
            val completedCheckpoint = advancedTrack.checkpoints.first { it.dayNumber == currentDay }
            completedCheckpoint.status shouldBe CheckpointStatus.COMPLETED
            completedCheckpoint.completedAt shouldNotBe null

            // Assert 2: currentDay now points to the next PENDING day after the completed one,
            // or if no more PENDING days exist, the track is finished
            val remainingPending = advancedTrack.checkpoints
                .filter { it.status == CheckpointStatus.PENDING }

            if (remainingPending.isEmpty()) {
                // Assert 3: If no more PENDING days exist, track is marked as finished
                advancedTrack.isFinished shouldBe true
            } else {
                // currentDay should point to the next PENDING day
                advancedTrack.isFinished shouldBe false
                val nextPendingAfterCurrent = advancedTrack.checkpoints
                    .filter { it.dayNumber > currentDay && it.status == CheckpointStatus.PENDING }
                    .minByOrNull { it.dayNumber }

                if (nextPendingAfterCurrent != null) {
                    // Next PENDING day after the completed one
                    advancedTrack.currentDay shouldBe nextPendingAfterCurrent.dayNumber
                } else {
                    // No PENDING after current day, should wrap to earliest PENDING
                    val earliestPending = remainingPending.minByOrNull { it.dayNumber }!!
                    advancedTrack.currentDay shouldBe earliestPending.dayNumber
                }
            }
        }
    }
})
