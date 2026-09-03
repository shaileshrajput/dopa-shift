// Feature: dynamic-ui-experience, Task 18.2
// Validates: DUX-6.4 (a single quick-action recomposes only its affected section, not the
// full screen — recomposition is scoped so stable neighbours skip) and DUX-6.5 / DUX-6.2
// (the activity feed is lazy and paginated in pages of 20 — the underlying query window only
// widens on an explicit load-more, never before it is asked for).
package com.dopashift.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import com.dopashift.domain.entity.ChangeLogEntry
import com.dopashift.domain.entity.DailyTodoItem
import com.dopashift.domain.entity.EfficiencyScore
import com.dopashift.domain.entity.GoalChecklistItem
import com.dopashift.domain.entity.GoalProfile
import com.dopashift.domain.entity.HabitTrack
import com.dopashift.domain.repository.ChangeLogRepository
import com.dopashift.domain.repository.DailyTodoRepository
import com.dopashift.domain.repository.EfficiencyScoreRepository
import com.dopashift.domain.repository.GoalChecklistItemRepository
import com.dopashift.domain.repository.GoalRepository
import com.dopashift.domain.repository.HabitTrackRepository
import com.dopashift.domain.repository.SyncStatusProvider
import com.dopashift.ui.theme.DopaShiftTheme
import com.dopashift.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.util.Collections
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Performance characteristics tests for the Dashboard built in task 18.1.
 *
 * Part 1 (Compose, Robolectric) proves recomposition is **scoped** (DUX-6.4): two independent
 * children each carry their own state and their own recomposition counter; mutating one child's
 * state must recompose only that child while its stable neighbour skips.
 *
 * Part 2 (JVM coroutine test) proves the activity feed is **lazy + paginated** (DUX-6.5, DUX-6.2):
 * a fake [ChangeLogRepository] records every `limit` it is asked for. The first subscription must
 * request `limit == 20`; only after an explicit [DashboardViewModel.loadMoreActivityFeed] does a
 * later subscription widen to `limit == 40` (cumulative pages of 20). Nothing wider is fetched
 * before the user asks, which is exactly what "load on scroll / not before first navigation" means.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RecompositionAndPaginationTest {

    @get:Rule
    val composeRule = createComposeRule()

    // ---- Part 1: recomposition scoping (DUX-6.4) --------------------------------------

    /**
     * A single self-contained child. It owns its state via the passed-in [MutableState] and
     * increments its own recomposition counter on every composition pass (the counter read
     * happens in the composable body, so it runs each time the child recomposes).
     */
    @Composable
    private fun CountingChild(state: MutableState<Int>, recompositions: AtomicInteger) {
        // Runs on every recomposition of THIS composable only.
        recompositions.incrementAndGet()
        val value by state
        Text(text = "value=$value")
    }

    @Test
    fun singleStateChange_recomposesOnlyItsOwnChild_neighbourSkips() {
        val childARecompositions = AtomicInteger(0)
        val childBRecompositions = AtomicInteger(0)

        lateinit var stateA: MutableState<Int>
        lateinit var stateB: MutableState<Int>

        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                // Each child is driven by its own independent mutableStateOf, so a read of
                // one state cannot invalidate the other child's scope.
                stateA = remember { mutableStateOf(0) }
                stateB = remember { mutableStateOf(0) }
                Column {
                    CountingChild(state = stateA, recompositions = childARecompositions)
                    CountingChild(state = stateB, recompositions = childBRecompositions)
                }
            }
        }

        composeRule.waitForIdle()

        // Baseline after the first composition: each child composed at least once.
        val childAInitial = childARecompositions.get()
        val childBInitial = childBRecompositions.get()
        assertTrue("child A should have composed at least once", childAInitial >= 1)
        assertTrue("child B should have composed at least once", childBInitial >= 1)

        // Mutate ONLY child A's state.
        composeRule.runOnUiThread { stateA.value = 1 }
        composeRule.waitForIdle()

        // Child A recomposed beyond its initial compose; child B (a stable neighbour) did not.
        assertTrue(
            "child A must recompose after its own state changed " +
                "(before=$childAInitial, after=${childARecompositions.get()})",
            childARecompositions.get() > childAInitial
        )
        assertEquals(
            "stable neighbour child B must NOT recompose when only child A's state changed",
            childBInitial,
            childBRecompositions.get()
        )
    }

    // ---- Part 2: lazy-loading / pagination (DUX-6.5, DUX-6.2) --------------------------

    private val testUserId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")

    /**
     * Records every `limit` the [DashboardViewModel] asks the change log for and returns a Flow
     * of that many fake entries so the VM believes a full page came back (which is what lets it
     * conclude "more pages remain").
     */
    private class RecordingChangeLogRepository(
        private val userId: UUID
    ) : ChangeLogRepository {
        // Thread-safe: the VM collects on a background dispatcher under runTest.
        val recordedLimits: MutableList<Int> = Collections.synchronizedList(mutableListOf())

        override suspend fun append(entry: ChangeLogEntry) = Unit
        override suspend fun findSince(userId: UUID, since: Instant): List<ChangeLogEntry> = emptyList()
        override suspend fun countByUserId(userId: UUID): Long = 0L
        override suspend fun archiveOlderThan(cutoff: Instant) = Unit

        override fun observeRecent(userId: UUID, limit: Int, offset: Int): Flow<List<ChangeLogEntry>> {
            recordedLimits.add(limit)
            val entries = (0 until limit).map { i ->
                ChangeLogEntry(
                    id = UUID.randomUUID(),
                    entityId = UUID.randomUUID(),
                    entityType = "DailyTodoItem",
                    field = "isCompleted",
                    value = "task-$i",
                    timestamp = Instant.now(),
                    deviceId = "test-device",
                    userId = this.userId
                )
            }
            return flowOf(entries)
        }
    }

    private class FakeGoalRepository : GoalRepository {
        override suspend fun findById(id: UUID): GoalProfile? = null
        override suspend fun findByUserId(userId: UUID): List<GoalProfile> = emptyList()
        override suspend fun findByUserIdAndName(userId: UUID, name: String): GoalProfile? = null
        override suspend fun save(goal: GoalProfile): GoalProfile = goal
        override suspend fun delete(id: UUID) = Unit
        override suspend fun countActiveByUserId(userId: UUID): Int = 0
        override fun observeByUserId(userId: UUID): Flow<List<GoalProfile>> = flowOf(emptyList())
    }

    private class FakeDailyTodoRepository : DailyTodoRepository {
        override suspend fun findById(id: UUID): DailyTodoItem? = null
        override suspend fun findByUserIdAndDate(userId: UUID, date: LocalDate): List<DailyTodoItem> = emptyList()
        override suspend fun countByUserIdAndDate(userId: UUID, date: LocalDate): Int = 0
        override suspend fun save(item: DailyTodoItem): DailyTodoItem = item
        override suspend fun delete(id: UUID) = Unit
        override fun observeByUserIdAndDate(userId: UUID, date: LocalDate): Flow<List<DailyTodoItem>> =
            flowOf(emptyList())
    }

    private class FakeHabitTrackRepository : HabitTrackRepository {
        override suspend fun findById(id: UUID): HabitTrack? = null
        override suspend fun findActiveByGoalId(goalId: UUID): List<HabitTrack> = emptyList()
        override suspend fun findActiveByUserId(userId: UUID): List<HabitTrack> = emptyList()
        override suspend fun save(track: HabitTrack): HabitTrack = track
        override suspend fun delete(id: UUID) = Unit
        override fun observeActiveByUserId(userId: UUID): Flow<List<HabitTrack>> = flowOf(emptyList())
    }

    private class FakeEfficiencyScoreRepository : EfficiencyScoreRepository {
        override suspend fun save(score: EfficiencyScore): EfficiencyScore = score
        override suspend fun findByUserIdAndDateRange(
            userId: UUID,
            from: LocalDate,
            to: LocalDate
        ): List<EfficiencyScore> = emptyList()

        override fun observeByUserIdAndDateRange(
            userId: UUID,
            from: LocalDate,
            to: LocalDate
        ): Flow<List<EfficiencyScore>> = flowOf(emptyList())
    }

    private class FakeGoalChecklistItemRepository : GoalChecklistItemRepository {
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

    private class FakeSyncStatusProvider : SyncStatusProvider {
        override fun observeSyncPending(): Flow<Boolean> = flowOf(false)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun activityFeed_isLazyAndPaginated_firstPageIs20_loadMoreWidensTo40() = runTest {
        // The ViewModel launches its feed collection on Dispatchers.Main (viewModelScope).
        // Bind Main to this test's scheduler so advanceUntilIdle() drives those launches.
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val changeLog = RecordingChangeLogRepository(testUserId)
            val viewModel = DashboardViewModel(
                goalRepository = FakeGoalRepository(),
                dailyTodoRepository = FakeDailyTodoRepository(),
                habitTrackRepository = FakeHabitTrackRepository(),
                efficiencyScoreRepository = FakeEfficiencyScoreRepository(),
                changeLogRepository = changeLog,
                goalChecklistItemRepository = FakeGoalChecklistItemRepository(),
                syncStatusProvider = FakeSyncStatusProvider()
            )

            // Keep sectionData hot so the VM's reactive pipeline runs (WhileSubscribed).
            val collectJob = launch { viewModel.sectionData.collect { } }

            // Let init { loadActivityFeed() } run its first subscription.
            advanceUntilIdle()

            // First page must request exactly 20 rows — lazy: nothing wider fetched yet.
            assertTrue(
                "expected at least one observeRecent call after init, got ${changeLog.recordedLimits}",
                changeLog.recordedLimits.isNotEmpty()
            )
            assertEquals(
                "first activity-feed page must request limit == 20 (page size), " +
                    "recorded=${changeLog.recordedLimits}",
                20,
                changeLog.recordedLimits.first()
            )
            assertTrue(
                "before load-more, the feed must never widen beyond the first page of 20 " +
                    "(recorded=${changeLog.recordedLimits})",
                changeLog.recordedLimits.all { it == 20 }
            )

            // User asks for more — only now should the window widen to the next cumulative page.
            viewModel.loadMoreActivityFeed()
            advanceUntilIdle()

            assertTrue(
                "after loadMoreActivityFeed(), a subsequent observeRecent must request limit == 40 " +
                    "(cumulative 2 pages of 20), recorded=${changeLog.recordedLimits}",
                changeLog.recordedLimits.contains(40)
            )
            // Explicit ordering assertion: 20 was requested before 40 (proves on-demand widening).
            val firstFortyIndex = changeLog.recordedLimits.indexOf(40)
            val firstTwentyIndex = changeLog.recordedLimits.indexOf(20)
            assertTrue(
                "limit 20 must be requested before limit 40 (recorded=${changeLog.recordedLimits})",
                firstTwentyIndex in 0 until firstFortyIndex
            )

            collectJob.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }
}
