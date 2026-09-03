// Feature: Home/App-Limits UI fixes — Task 1 verification.
// Validates DashboardViewModel.completeHabitCheckpoint advances currentDay to the next PENDING
// day (skipping MISSED days), marks the current-day checkpoint COMPLETED, and sets isFinished
// when no PENDING days remain — matching HabitRoadmapViewModel.completeCheckpoint.
package com.dopashift.ui.dashboard

import com.dopashift.domain.entity.ChangeLogEntry
import com.dopashift.domain.entity.CheckpointStatus
import com.dopashift.domain.entity.DailyTodoItem
import com.dopashift.domain.entity.EfficiencyScore
import com.dopashift.domain.entity.GoalChecklistItem
import com.dopashift.domain.entity.GoalProfile
import com.dopashift.domain.entity.HabitCheckpoint
import com.dopashift.domain.entity.HabitTrack
import com.dopashift.domain.repository.ChangeLogRepository
import com.dopashift.domain.repository.DailyTodoRepository
import com.dopashift.domain.repository.EfficiencyScoreRepository
import com.dopashift.domain.repository.GoalChecklistItemRepository
import com.dopashift.domain.repository.GoalRepository
import com.dopashift.domain.repository.HabitTrackRepository
import com.dopashift.domain.repository.SyncStatusProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardHabitCompletionTest {

    private val userId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")
    private val goalId: UUID = UUID.randomUUID()

    private fun checkpoint(day: Int, status: CheckpointStatus): HabitCheckpoint =
        HabitCheckpoint(
            id = UUID.randomUUID(),
            habitTrackId = UUID.randomUUID(),
            dayNumber = day,
            description = "Day $day habit",
            status = status,
        )

    private fun track(currentDay: Int, checkpoints: List<HabitCheckpoint>): HabitTrack =
        HabitTrack(
            id = UUID.randomUUID(),
            goalId = goalId,
            userId = userId,
            startDate = LocalDate.of(2026, 1, 1),
            currentDay = currentDay,
            isFinished = false,
            checkpoints = checkpoints,
        )

    private fun viewModel(repo: HabitTrackRepository) = DashboardViewModel(
        goalRepository = NoopGoalRepository,
        dailyTodoRepository = NoopDailyTodoRepository,
        habitTrackRepository = repo,
        efficiencyScoreRepository = NoopEfficiencyScoreRepository,
        changeLogRepository = NoopChangeLogRepository,
        goalChecklistItemRepository = NoopGoalChecklistItemRepository,
        syncStatusProvider = NoopSyncStatusProvider,
    )

    @Test
    fun completeHabitCheckpoint_skipsMissedDay_advancesToNextPendingDay() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            // Day 1 PENDING (current), day 2 MISSED, day 3 PENDING.
            val initial = track(
                currentDay = 1,
                checkpoints = listOf(
                    checkpoint(1, CheckpointStatus.PENDING),
                    checkpoint(2, CheckpointStatus.MISSED),
                    checkpoint(3, CheckpointStatus.PENDING),
                ),
            )
            val repo = RecordingHabitTrackRepository(initial)
            val vm = viewModel(repo)

            vm.completeHabitCheckpoint(initial.id)
            advanceUntilIdle()

            val saved = repo.lastSaved
            assertNotNull("track should have been saved", saved)
            saved!!

            // Current day advanced past the MISSED day 2 to the next PENDING day 3.
            assertEquals("currentDay should skip the missed day and land on day 3", 3, saved.currentDay)
            assertEquals(
                "day 1 checkpoint should be COMPLETED",
                CheckpointStatus.COMPLETED,
                saved.checkpoints.first { it.dayNumber == 1 }.status,
            )
            assertEquals(
                "the missed day 2 must remain MISSED",
                CheckpointStatus.MISSED,
                saved.checkpoints.first { it.dayNumber == 2 }.status,
            )
            assertTrue("track is not finished — day 3 is still pending", !saved.isFinished)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun completeHabitCheckpoint_lastPendingDay_marksTrackFinished() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            // Day 1 COMPLETED, day 2 PENDING (current) — completing it leaves no PENDING day.
            val initial = track(
                currentDay = 2,
                checkpoints = listOf(
                    checkpoint(1, CheckpointStatus.COMPLETED),
                    checkpoint(2, CheckpointStatus.PENDING),
                ),
            )
            val repo = RecordingHabitTrackRepository(initial)
            val vm = viewModel(repo)

            vm.completeHabitCheckpoint(initial.id)
            advanceUntilIdle()

            val saved = repo.lastSaved
            assertNotNull("track should have been saved", saved)
            saved!!

            assertTrue("track should be finished once all checkpoints are done", saved.isFinished)
            assertEquals(
                "day 2 checkpoint should be COMPLETED",
                CheckpointStatus.COMPLETED,
                saved.checkpoints.first { it.dayNumber == 2 }.status,
            )
            assertEquals("currentDay is held when finished", 2, saved.currentDay)
        } finally {
            Dispatchers.resetMain()
        }
    }

    // ---- Fakes -----------------------------------------------------------------------

    private class RecordingHabitTrackRepository(initial: HabitTrack) : HabitTrackRepository {
        private var stored: HabitTrack = initial
        var lastSaved: HabitTrack? = null
            private set

        override suspend fun findById(id: UUID): HabitTrack? = if (id == stored.id) stored else null
        override suspend fun findActiveByGoalId(goalId: UUID): List<HabitTrack> = listOf(stored)
        override suspend fun findActiveByUserId(userId: UUID): List<HabitTrack> = listOf(stored)
        override suspend fun save(track: HabitTrack): HabitTrack {
            stored = track
            lastSaved = track
            return track
        }
        override suspend fun delete(id: UUID) = Unit
        override fun observeActiveByUserId(userId: UUID): Flow<List<HabitTrack>> = flowOf(listOf(stored))
    }

    private object NoopGoalRepository : GoalRepository {
        override suspend fun findById(id: UUID): GoalProfile? = null
        override suspend fun findByUserId(userId: UUID): List<GoalProfile> = emptyList()
        override suspend fun findByUserIdAndName(userId: UUID, name: String): GoalProfile? = null
        override suspend fun save(goal: GoalProfile): GoalProfile = goal
        override suspend fun delete(id: UUID) = Unit
        override suspend fun countActiveByUserId(userId: UUID): Int = 0
        override fun observeByUserId(userId: UUID): Flow<List<GoalProfile>> = flowOf(emptyList())
    }

    private object NoopDailyTodoRepository : DailyTodoRepository {
        override suspend fun findById(id: UUID): DailyTodoItem? = null
        override suspend fun findByUserIdAndDate(userId: UUID, date: LocalDate): List<DailyTodoItem> = emptyList()
        override suspend fun countByUserIdAndDate(userId: UUID, date: LocalDate): Int = 0
        override suspend fun save(item: DailyTodoItem): DailyTodoItem = item
        override suspend fun delete(id: UUID) = Unit
        override fun observeByUserIdAndDate(userId: UUID, date: LocalDate): Flow<List<DailyTodoItem>> =
            flowOf(emptyList())
    }

    private object NoopEfficiencyScoreRepository : EfficiencyScoreRepository {
        override suspend fun save(score: EfficiencyScore): EfficiencyScore = score
        override suspend fun findByUserIdAndDateRange(userId: UUID, from: LocalDate, to: LocalDate): List<EfficiencyScore> = emptyList()
        override fun observeByUserIdAndDateRange(userId: UUID, from: LocalDate, to: LocalDate): Flow<List<EfficiencyScore>> = flowOf(emptyList())
    }

    private object NoopGoalChecklistItemRepository : GoalChecklistItemRepository {
        override suspend fun findById(id: UUID): GoalChecklistItem? = null
        override suspend fun findByGoalId(goalId: UUID): List<GoalChecklistItem> = emptyList()
        override suspend fun findByUserId(userId: UUID): List<GoalChecklistItem> = emptyList()
        override suspend fun save(item: GoalChecklistItem): GoalChecklistItem = item
        override suspend fun delete(id: UUID) = Unit
        override suspend fun deleteByGoalId(goalId: UUID) = Unit
        override suspend fun reassignToGoal(fromGoalId: UUID, toGoalId: UUID) = Unit
        override fun observeByGoalId(goalId: UUID): Flow<List<GoalChecklistItem>> = flowOf(emptyList())
        override fun observeByUserId(userId: UUID): Flow<List<GoalChecklistItem>> = flowOf(emptyList())
    }

    private object NoopChangeLogRepository : ChangeLogRepository {
        override suspend fun append(entry: ChangeLogEntry) = Unit
        override suspend fun findSince(userId: UUID, since: java.time.Instant): List<ChangeLogEntry> = emptyList()
        override suspend fun countByUserId(userId: UUID): Long = 0L
        override suspend fun archiveOlderThan(cutoff: java.time.Instant) = Unit
        override fun observeRecent(userId: UUID, limit: Int, offset: Int): Flow<List<ChangeLogEntry>> = flowOf(emptyList())
    }

    private object NoopSyncStatusProvider : SyncStatusProvider {
        override fun observeSyncPending(): Flow<Boolean> = flowOf(false)
    }
}
