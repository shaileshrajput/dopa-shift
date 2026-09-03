// Feature: dashboard-quick-create, Task 18.3 — timing and offline integration tests.
// Tags: DQC-1.5 (Dashboard reflects a create within 1 s), DQC-6.6 (offline diagnostics flush on
//       reconnect), DQC-5.8 (onboarding completion/skip persists across launches).
// (DQC-1.3 — sheet presentation <= 300 ms — is a UI-layer timing check covered by the ui module's
//  SheetPresentationTimingTest against the QuickCreateViewModel activeSheet transition.)
package com.dopashift.data.repository

import androidx.test.core.app.ApplicationProvider
import com.dopashift.data.local.dao.DailyTodoDao
import com.dopashift.data.local.dao.DiagnosticEventDao
import com.dopashift.data.local.dao.GoalDao
import com.dopashift.data.local.dao.HabitTrackDao
import com.dopashift.data.local.entity.LocalDailyTodo
import com.dopashift.data.local.entity.LocalDiagnosticEvent
import com.dopashift.data.local.entity.LocalGoal
import com.dopashift.data.local.entity.LocalHabitCheckpoint
import com.dopashift.data.local.entity.LocalHabitTrack
import com.dopashift.data.remote.DopaShiftApi
import com.dopashift.domain.creation.DiagnosticEventType
import com.dopashift.domain.creation.OnboardingStatus
import com.dopashift.domain.creation.usecase.ObserveDashboardSummaryInteractor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import java.util.UUID

/**
 * Timing and offline integration tests for the Dashboard quick-create surface (task 18.3).
 *
 * These are the non-property, cross-boundary integration checks from the design's Testing
 * Strategy that assert *timing budgets* and *offline durability* rather than pure input→output
 * properties:
 *
 *  - **DQC-1.5** — a successful creation is reflected in the Dashboard summary within 1 second.
 *    Exercised end to end through the real [ObserveDashboardSummaryInteractor] over the real
 *    [RoomDashboardSummaryRepository], whose `observe(userId)` combines live DAO count flows: a
 *    change to any count (a "create") must re-emit an updated [com.dopashift.domain.creation.DashboardSummary]
 *    well inside the 1 s budget.
 *  - **DQC-6.6** — while offline, diagnostics events queue locally and flush on reconnect using
 *    the standard retry policy. Exercised through [DiagnosticsAggregator] with a
 *    fault-injecting [DiagnosticsCountsUploader] that first fails (offline) and then succeeds
 *    (reconnect): the raw rows survive the failed attempt, the 24 h window is not consumed by a
 *    failure, and the retry flushes the queued counts.
 *  - **DQC-5.8** — onboarding completion/skip status persists across app launches. Exercised by
 *    persisting via one [QuickCreatePreferencesStore] instance and reading back through a
 *    *fresh* instance over the same Context-backed DataStore (a new store instance models a
 *    subsequent app launch).
 *
 * ## Framework note
 *
 * The data module's unit-test classpath runs JUnit 4 + Robolectric (Robolectric supplies an
 * Android `Context` on the JVM so the Context-backed DataStore and DAO count flows can be
 * driven without an emulator). This mirrors the sibling integration/property tests in this
 * package ([RoomDashboardSummaryRepositoryTest], [DiagnosticsSyncBoundedPropertyTest]) which
 * establish the module convention; these timing/offline assertions are example-based
 * integration checks (not generated-input properties), so JUnit 4 is the natural fit.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class TimingAndOfflineIntegrationTest {

    private val userId: UUID = UUID.fromString("00000000-0000-0000-0000-0000000000cc")

    private lateinit var goalDao: FakeGoalDao
    private lateinit var todoDao: FakeDailyTodoDao
    private lateinit var habitDao: FakeHabitTrackDao
    private lateinit var preferences: QuickCreatePreferencesStore
    private lateinit var summaryRepository: RoomDashboardSummaryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        goalDao = FakeGoalDao()
        todoDao = FakeDailyTodoDao()
        habitDao = FakeHabitTrackDao()
        preferences = QuickCreatePreferencesStore(context)
        summaryRepository = RoomDashboardSummaryRepository(
            goalDao = goalDao,
            dailyTodoDao = todoDao,
            habitTrackDao = habitDao,
            preferencesStore = preferences,
            api = noOpApi()
        )
    }

    // =====================================================================================
    // DQC-1.5 — a successful creation is reflected in the Dashboard summary within 1 second.
    // =====================================================================================

    /**
     * A create (modeled as an increment of the underlying local count the repository observes)
     * re-emits an updated summary through [ObserveDashboardSummaryUseCase]. Because the feed is a
     * live local flow, the reflection is effectively immediate; we assert it lands within the 1 s
     * budget (DQC-1.5) with margin.
     */
    @Test
    fun `dashboard summary reflects a create within one second`() = runBlocking {
        goalDao.activeCount.value = 0
        todoDao.todayCount.value = 0
        habitDao.activeCount.value = 0

        val interactor = ObserveDashboardSummaryInteractor(summaryRepository)

        // Observe the initial Zero_State first so the "create" that follows is a distinct,
        // ordered re-emission rather than being conflated by the underlying StateFlows.
        val initial = interactor(userId).first()
        assertEquals(0, initial.activeGoalCount)
        assertTrue("all-zero counts are Zero_State", initial.isZeroState)

        // A create is modeled as an increment of the local count the repository observes. Measure
        // wall-clock time from the create until the summary reflects it (DQC-1.5).
        val start = System.nanoTime()
        goalDao.activeCount.value = 1
        val afterCreate = interactor(userId).first()
        val elapsedMillis = (System.nanoTime() - start) / 1_000_000

        assertEquals(1, afterCreate.activeGoalCount)
        assertFalse("a populated section is not Zero_State", afterCreate.isZeroState)
        assertTrue(
            "summary must reflect the create within 1s (DQC-1.5) but took ${elapsedMillis}ms",
            elapsedMillis <= 1_000
        )
    }

    /**
     * The summary feed is reactive across every entity type, so a to-do or habit create reflects
     * on the Dashboard just as a goal create does (DQC-1.5, DQC-2.9) — no navigation, no refetch.
     */
    @Test
    fun `dashboard summary re-emits on a todo and on a habit create`() = runBlocking {
        goalDao.activeCount.value = 0
        todoDao.todayCount.value = 0
        habitDao.activeCount.value = 0

        val interactor = ObserveDashboardSummaryInteractor(summaryRepository)
        assertEquals(0, interactor(userId).first().todayTodoCount)

        todoDao.todayCount.value = 1
        assertEquals(1, interactor(userId).first().todayTodoCount)

        habitDao.activeCount.value = 1
        assertEquals(1, interactor(userId).first().activeHabitCount)
    }

    // =====================================================================================
    // DQC-6.6 — offline diagnostics queue locally and flush on reconnect (standard retry).
    // =====================================================================================

    /**
     * While offline the uploader fails; the queued raw rows must NOT be cleared, the 24 h window
     * must NOT advance (so the next attempt is allowed), and on reconnect the retry flushes the
     * aggregated counts and only then clears the local queue and advances the window (DQC-6.6).
     */
    @Test
    fun `offline diagnostics are queued and flush on reconnect`() = runBlocking {
        preferences.setLastDiagnosticsSyncAt(null) // never synced -> gate open

        val dao = FakeDiagnosticEventDao().apply {
            seedCounts(
                mapOf(
                    DiagnosticEventType.QUICK_CREATE_OPENED to 2,
                    DiagnosticEventType.ENTITY_CREATED to 1
                )
            )
        }
        val uploader = FlakyCountsUploader(failFirst = true)
        val aggregator = DiagnosticsAggregator(dao, preferences, uploader)

        val now = 2_000_000_000_000L

        // --- Offline attempt: transmission fails. ---
        val offlineResult = aggregator.aggregateAndSync(now = now)

        assertTrue(
            "offline attempt should fail, not sync, but was $offlineResult",
            offlineResult is DiagnosticsSyncResult.Failed
        )
        assertFalse("queued rows must be preserved while offline", dao.cleared)
        assertEquals(0, uploader.successfulUploads.size)
        // The 24 h window must not be consumed by a failed attempt, so a retry is permitted.
        assertNull(
            "lastDiagnosticsSyncAt must not advance on a failed upload",
            preferences.observe().first().lastDiagnosticsSyncAt
        )

        // --- Reconnect: the standard retry re-attempts and succeeds. ---
        val onlineResult = aggregator.aggregateAndSync(now = now)

        assertTrue(
            "reconnect attempt should sync, but was $onlineResult",
            onlineResult is DiagnosticsSyncResult.Synced
        )
        assertEquals("exactly one successful upload after reconnect", 1, uploader.successfulUploads.size)
        // Counts-only payload flushed the queued events.
        val flushed = uploader.successfulUploads.single().eventTypeCounts
        assertEquals(2L, flushed[DiagnosticEventType.QUICK_CREATE_OPENED.name])
        assertEquals(1L, flushed[DiagnosticEventType.ENTITY_CREATED.name])
        // On success the local queue is cleared and the window advances.
        assertTrue("queued rows cleared after successful flush", dao.cleared)
        assertEquals(now, preferences.observe().first().lastDiagnosticsSyncAt)
    }

    // =====================================================================================
    // DQC-5.8 — onboarding completion/skip status persists across app launches.
    // =====================================================================================

    /**
     * Persisting a terminal onboarding outcome through one store instance is readable through a
     * fresh instance backed by the same DataStore — modeling a subsequent app launch — so the
     * flow is never re-presented (DQC-5.8).
     */
    @Test
    fun `completed onboarding status persists across launches`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        // First launch: onboarding runs to completion.
        QuickCreatePreferencesStore(context).setOnboardingStatus(OnboardingStatus.COMPLETED)

        // Next launch: a brand-new store instance over the same persisted DataStore.
        val relaunched = QuickCreatePreferencesStore(context)
        assertEquals(
            OnboardingStatus.COMPLETED,
            relaunched.observe().first().onboardingStatus
        )
    }

    @Test
    fun `skipped onboarding status persists across launches`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        QuickCreatePreferencesStore(context).setOnboardingStatus(OnboardingStatus.SKIPPED)

        val relaunched = QuickCreatePreferencesStore(context)
        assertEquals(
            OnboardingStatus.SKIPPED,
            relaunched.observe().first().onboardingStatus
        )
    }

    /**
     * A terminal outcome is sticky across launches: a later attempt to downgrade to
     * NOT_STARTED (which would re-present onboarding) is ignored, even through a fresh instance
     * (DQC-5.7, DQC-5.8).
     */
    @Test
    fun `a terminal onboarding outcome is never downgraded on a later launch`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        QuickCreatePreferencesStore(context).setOnboardingStatus(OnboardingStatus.SKIPPED)

        // A later launch attempts (incorrectly) to reset onboarding; the store must refuse.
        val relaunched = QuickCreatePreferencesStore(context)
        relaunched.setOnboardingStatus(OnboardingStatus.NOT_STARTED)

        val afterAttemptedReset = QuickCreatePreferencesStore(context)
        assertEquals(
            OnboardingStatus.SKIPPED,
            afterAttemptedReset.observe().first().onboardingStatus
        )
    }

    // =====================================================================================
    // Fakes / helpers
    // =====================================================================================

    /**
     * A [DopaShiftApi] that fails any call. `observe` never calls it; `refresh` wraps the summary
     * call in try/catch and treats a failure as "offline", so the local observed summary remains
     * the source of truth — exactly the offline-first path DQC-1.5 must not depend on the network.
     */
    private fun noOpApi(): DopaShiftApi =
        Proxy.newProxyInstance(
            DopaShiftApi::class.java.classLoader,
            arrayOf(DopaShiftApi::class.java)
        ) { _, _, _ -> throw java.io.IOException("offline: network summary not available in test") } as DopaShiftApi

    /**
     * [DiagnosticsCountsUploader] that fails its first N attempts (modeling offline) and then
     * succeeds (modeling reconnect under the standard retry policy).
     */
    private class FlakyCountsUploader(failFirst: Boolean) : DiagnosticsCountsUploader {
        private var remainingFailures = if (failFirst) 1 else 0
        val successfulUploads = mutableListOf<DiagnosticsCountsPayload>()

        override suspend fun upload(payload: DiagnosticsCountsPayload) {
            if (remainingFailures > 0) {
                remainingFailures--
                throw java.io.IOException("offline")
            }
            successfulUploads += payload
        }
    }

    /** In-memory [DiagnosticEventDao] mirroring the real DAO's count/clear semantics. */
    private class FakeDiagnosticEventDao : DiagnosticEventDao {
        private val rows = mutableListOf<LocalDiagnosticEvent>()

        var cleared: Boolean = false
            private set

        fun seedCounts(countsByType: Map<DiagnosticEventType, Int>, occurredAt: Long = 0L) {
            countsByType.forEach { (type, count) ->
                repeat(count) {
                    rows += LocalDiagnosticEvent(
                        id = UUID.randomUUID().toString(),
                        eventType = type.name,
                        correlationId = UUID.randomUUID().toString(),
                        entityType = null,
                        occurredAt = occurredAt
                    )
                }
            }
        }

        override suspend fun upsert(event: LocalDiagnosticEvent) {
            rows.removeAll { it.id == event.id }
            rows += event
        }

        override suspend fun getAll(): List<LocalDiagnosticEvent> = rows.sortedBy { it.occurredAt }

        override suspend fun countByType(eventType: String): Int = rows.count { it.eventType == eventType }

        override suspend fun deleteById(id: String) {
            rows.removeAll { it.id == id }
        }

        override suspend fun clear() {
            cleared = true
            rows.clear()
        }
    }

    private class FakeGoalDao : GoalDao {
        val activeCount = MutableStateFlow(0)

        override fun observeActiveCountByUserId(userId: String): Flow<Int> = activeCount
        override suspend fun findById(id: String): LocalGoal? = null
        override suspend fun findByUserId(userId: String): List<LocalGoal> = emptyList()
        override fun observeByUserId(userId: String): Flow<List<LocalGoal>> = MutableStateFlow(emptyList())
        override suspend fun findByUserIdAndName(userId: String, name: String): LocalGoal? = null
        override suspend fun upsert(goal: LocalGoal) = Unit
        override suspend fun deleteById(id: String) = Unit
        override suspend fun countActiveByUserId(userId: String): Int = activeCount.value
    }

    private class FakeDailyTodoDao : DailyTodoDao {
        val todayCount = MutableStateFlow(0)

        override fun observeCountByUserIdAndDate(userId: String, date: String): Flow<Int> = todayCount
        override suspend fun findById(id: String): LocalDailyTodo? = null
        override suspend fun findByUserIdAndDate(userId: String, date: String): List<LocalDailyTodo> = emptyList()
        override fun observeByUserIdAndDate(userId: String, date: String): Flow<List<LocalDailyTodo>> =
            MutableStateFlow(emptyList())
        override suspend fun countByUserIdAndDate(userId: String, date: String): Int = todayCount.value
        override suspend fun upsert(item: LocalDailyTodo) = Unit
        override suspend fun deleteById(id: String) = Unit
    }

    private class FakeHabitTrackDao : HabitTrackDao {
        val activeCount = MutableStateFlow(0)

        override fun observeActiveCountByUserId(userId: String): Flow<Int> = activeCount
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
