package com.dopashift.data.repository

import androidx.test.core.app.ApplicationProvider
import com.dopashift.data.local.dao.DailyTodoDao
import com.dopashift.data.local.dao.GoalDao
import com.dopashift.data.local.dao.HabitTrackDao
import com.dopashift.data.local.entity.LocalDailyTodo
import com.dopashift.data.local.entity.LocalGoal
import com.dopashift.data.local.entity.LocalHabitCheckpoint
import com.dopashift.data.local.entity.LocalHabitTrack
import com.dopashift.data.remote.DopaShiftApi
import com.dopashift.domain.creation.OnboardingStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Unit tests for [RoomDashboardSummaryRepository]'s count aggregation (DQC-5.3).
 *
 * `observe(userId)` combines three reactive DAO count flows (active goals, today's
 * to-dos, active habits) with the persisted onboarding status into a single
 * [com.dopashift.domain.creation.DashboardSummary]. These tests fake the three DAOs so
 * the count flows are fully controllable, and use the real [QuickCreatePreferencesStore]
 * (Context-backed DataStore) supplied by Robolectric. `observe` never touches the network,
 * so the injected [DopaShiftApi] is a no-op dynamic proxy.
 *
 * This keeps the aggregation logic under test on the JVM without an emulator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class RoomDashboardSummaryRepositoryTest {

    private val userId: UUID = UUID.randomUUID()

    private lateinit var goalDao: FakeGoalDao
    private lateinit var todoDao: FakeDailyTodoDao
    private lateinit var habitDao: FakeHabitTrackDao
    private lateinit var prefsStore: QuickCreatePreferencesStore
    private lateinit var repository: RoomDashboardSummaryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        goalDao = FakeGoalDao()
        todoDao = FakeDailyTodoDao()
        habitDao = FakeHabitTrackDao()
        prefsStore = QuickCreatePreferencesStore(context)
        repository = RoomDashboardSummaryRepository(
            goalDao = goalDao,
            dailyTodoDao = todoDao,
            habitTrackDao = habitDao,
            preferencesStore = prefsStore,
            api = noOpApi()
        )
    }

    @Test
    fun `observe aggregates the three counts and onboarding status into the summary`() = runBlocking {
        goalDao.activeCount.value = 3
        todoDao.todayCount.value = 5
        habitDao.activeCount.value = 2
        prefsStore.setOnboardingStatus(OnboardingStatus.COMPLETED)

        val summary = repository.observe(userId).first()

        assertEquals(userId, summary.userId)
        assertEquals(3, summary.activeGoalCount)
        assertEquals(5, summary.todayTodoCount)
        assertEquals(2, summary.activeHabitCount)
        assertEquals(OnboardingStatus.COMPLETED, summary.onboardingStatus)
    }

    @Test
    fun `observe reports zero-state when all counts are zero`() = runBlocking {
        goalDao.activeCount.value = 0
        todoDao.todayCount.value = 0
        habitDao.activeCount.value = 0

        val summary = repository.observe(userId).first()

        assertEquals(0, summary.activeGoalCount)
        assertEquals(0, summary.todayTodoCount)
        assertEquals(0, summary.activeHabitCount)
        // Derived state: all-zero is the sole Zero_State condition (DQC-5.3).
        assertEquals(true, summary.isZeroState)
    }

    @Test
    fun `observe reports partial-state when only some counts are non-zero`() = runBlocking {
        goalDao.activeCount.value = 1
        todoDao.todayCount.value = 0
        habitDao.activeCount.value = 0

        val summary = repository.observe(userId).first()

        assertEquals(false, summary.isZeroState)
    }

    @Test
    fun `observe scopes every DAO query by the authenticated user id`() = runBlocking {
        repository.observe(userId).first()

        val expected = userId.toString()
        assertEquals(expected, goalDao.observedUserId)
        assertEquals(expected, todoDao.observedUserId)
        assertEquals(expected, habitDao.observedUserId)
    }

    @Test
    fun `observe queries todos for today in the device time zone`() = runBlocking {
        repository.observe(userId).first()

        val today = LocalDate.now(ZoneId.systemDefault()).toString()
        assertEquals(today, todoDao.observedDate)
    }

    /**
     * `observe` reads live count flows: a change in any DAO count re-emits an updated
     * summary so the Dashboard reflects a new entity within a second (DQC-1.5, DQC-2.9).
     */
    @Test
    fun `observe re-emits when an underlying count changes`() = runBlocking {
        goalDao.activeCount.value = 0
        todoDao.todayCount.value = 0
        habitDao.activeCount.value = 0

        assertEquals(0, repository.observe(userId).first().activeGoalCount)

        goalDao.activeCount.value = 1
        assertEquals(1, repository.observe(userId).first().activeGoalCount)
    }

    /** A no-op [DopaShiftApi]; `observe` never calls it, so a dynamic proxy suffices. */
    private fun noOpApi(): DopaShiftApi =
        Proxy.newProxyInstance(
            DopaShiftApi::class.java.classLoader,
            arrayOf(DopaShiftApi::class.java)
        ) { _, _, _ -> error("DopaShiftApi must not be called from observe()") } as DopaShiftApi

    // === Fakes: expose the observe* count flows used by the repository ===

    private class FakeGoalDao : GoalDao {
        val activeCount = MutableStateFlow(0)
        var observedUserId: String? = null

        override fun observeActiveCountByUserId(userId: String): Flow<Int> {
            observedUserId = userId
            return activeCount
        }

        override suspend fun findById(id: String): LocalGoal? = null
        override suspend fun findByUserId(userId: String): List<LocalGoal> = emptyList()
        override fun observeByUserId(userId: String): Flow<List<LocalGoal>> =
            MutableStateFlow(emptyList())
        override suspend fun findByUserIdAndName(userId: String, name: String): LocalGoal? = null
        override suspend fun upsert(goal: LocalGoal) = Unit
        override suspend fun deleteById(id: String) = Unit
        override suspend fun countActiveByUserId(userId: String): Int = activeCount.value
    }

    private class FakeDailyTodoDao : DailyTodoDao {
        val todayCount = MutableStateFlow(0)
        var observedUserId: String? = null
        var observedDate: String? = null

        override fun observeCountByUserIdAndDate(userId: String, date: String): Flow<Int> {
            observedUserId = userId
            observedDate = date
            return todayCount
        }

        override suspend fun findById(id: String): LocalDailyTodo? = null
        override suspend fun findByUserIdAndDate(userId: String, date: String): List<LocalDailyTodo> =
            emptyList()
        override fun observeByUserIdAndDate(userId: String, date: String): Flow<List<LocalDailyTodo>> =
            MutableStateFlow(emptyList())
        override suspend fun countByUserIdAndDate(userId: String, date: String): Int =
            todayCount.value
        override suspend fun upsert(item: LocalDailyTodo) = Unit
        override suspend fun deleteById(id: String) = Unit
    }

    private class FakeHabitTrackDao : HabitTrackDao {
        val activeCount = MutableStateFlow(0)
        var observedUserId: String? = null

        override fun observeActiveCountByUserId(userId: String): Flow<Int> {
            observedUserId = userId
            return activeCount
        }

        override suspend fun findById(id: String): LocalHabitTrack? = null
        override suspend fun findActiveByGoalId(goalId: String): List<LocalHabitTrack> = emptyList()
        override suspend fun findActiveByUserId(userId: String): List<LocalHabitTrack> = emptyList()
        override fun observeActiveByUserId(userId: String): Flow<List<LocalHabitTrack>> =
            MutableStateFlow(emptyList())
        override suspend fun upsert(track: LocalHabitTrack) = Unit
        override suspend fun getCheckpoints(trackId: String): List<LocalHabitCheckpoint> = emptyList()
        override suspend fun findCheckpointById(checkpointId: String): LocalHabitCheckpoint? = null
        override suspend fun upsertCheckpoint(checkpoint: LocalHabitCheckpoint) = Unit
        override suspend fun deleteCheckpointsForTrack(trackId: String) = Unit
        override suspend fun deleteTrack(id: String) = Unit
    }
}
