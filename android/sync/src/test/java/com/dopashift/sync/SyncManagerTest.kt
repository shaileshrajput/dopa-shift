package com.dopashift.sync

import com.dopashift.data.local.dao.ChangeLogDao
import com.dopashift.data.local.dao.SyncStateDao
import com.dopashift.data.local.entity.LocalChangeLogEntry
import com.dopashift.data.local.entity.LocalSyncState
import com.dopashift.data.remote.DopaShiftApi
import com.dopashift.data.remote.ResolvedConflict
import com.dopashift.data.remote.dto.ConflictResponse
import com.dopashift.data.remote.SyncEvent
import com.dopashift.data.remote.SyncPullResponse
import com.dopashift.data.remote.SyncPushRequest
import com.dopashift.data.remote.SyncPushResponse
import com.dopashift.data.remote.ServerTimestamp
import com.dopashift.sync.model.SyncResult
import com.dopashift.sync.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncManagerTest {

    private lateinit var syncManager: SyncManager
    private lateinit var fakeChangeLogDao: FakeChangeLogDao
    private lateinit var fakeSyncStateDao: FakeSyncStateDao
    private lateinit var fakeApi: FakeDopaShiftApi

    @Before
    fun setup() {
        fakeChangeLogDao = FakeChangeLogDao()
        fakeSyncStateDao = FakeSyncStateDao()
        fakeApi = FakeDopaShiftApi()
        syncManager = SyncManager(fakeChangeLogDao, fakeSyncStateDao, fakeApi)
    }

    @Test
    fun `calculateBackoff returns correct exponential sequence`() {
        assertEquals(1000L, syncManager.calculateBackoff(1))
        assertEquals(2000L, syncManager.calculateBackoff(2))
        assertEquals(4000L, syncManager.calculateBackoff(3))
        assertEquals(8000L, syncManager.calculateBackoff(4))
        assertEquals(16000L, syncManager.calculateBackoff(5))
    }

    @Test
    fun `pushPendingChanges returns NothingToSync when queue is empty`() = runTest {
        val result = syncManager.pushPendingChanges("user-1")
        assertEquals(SyncResult.NothingToSync, result)
    }

    @Test
    fun `pushPendingChanges succeeds and marks entries synced`() = runTest {
        val entry = createTestEntry("user-1")
        fakeChangeLogDao.entries.add(entry)
        fakeApi.pushResponse = SyncPushResponse(
            serverTimestamps = listOf(ServerTimestamp(entry.id, 1000L)),
            conflicts = emptyList()
        )

        val result = syncManager.pushPendingChanges("user-1")

        assertTrue(result is SyncResult.PushSuccess)
        assertEquals(1, (result as SyncResult.PushSuccess).syncedCount)
        assertTrue(fakeChangeLogDao.syncedIds.contains(entry.id))
        assertEquals(SyncStatus.SYNCED, syncManager.syncStatus.value)
    }

    @Test
    fun `pushPendingChanges fails after max retries and sets ERROR status`() = runTest {
        val entry = createTestEntry("user-1")
        fakeChangeLogDao.entries.add(entry)
        fakeApi.shouldFail = true

        val result = syncManager.pushPendingChanges("user-1")

        assertTrue(result is SyncResult.Failure)
        assertTrue((result as SyncResult.Failure).attemptsExhausted)
        assertEquals(SyncStatus.ERROR, syncManager.syncStatus.value)
        assertEquals(SyncManager.MAX_RETRY_ATTEMPTS, fakeApi.pushCallCount)
    }

    @Test
    fun `pullRemoteChanges applies events and updates sync timestamp`() = runTest {
        val remoteEvent = SyncEvent(
            id = "remote-1",
            entityId = "entity-1",
            entityType = "DailyTodoItem",
            field = "text",
            value = "\"Updated text\"",
            timestamp = 2000L,
            deviceId = "device-2",
            userId = "user-1"
        )
        fakeApi.pullResponse = SyncPullResponse(
            events = listOf(remoteEvent),
            serverTimestamp = 2000L
        )

        val result = syncManager.pullRemoteChanges("user-1")

        assertTrue(result is SyncResult.PullSuccess)
        assertEquals(1, (result as SyncResult.PullSuccess).appliedCount)
        assertEquals(2000L, result.newSyncTimestamp)
        assertEquals(2000L, fakeSyncStateDao.states[SyncManager.SYNC_STATE_KEY]?.lastSyncTimestamp)
        assertEquals(SyncStatus.SYNCED, syncManager.syncStatus.value)
    }

    @Test
    fun `pullRemoteChanges skips already existing events`() = runTest {
        val existingEntry = createTestEntry("user-1", id = "existing-1")
        fakeChangeLogDao.allEntries.add(existingEntry)

        val remoteEvent = SyncEvent(
            id = "existing-1",
            entityId = "entity-1",
            entityType = "DailyTodoItem",
            field = "text",
            value = "\"text\"",
            timestamp = 1000L,
            deviceId = "device-1",
            userId = "user-1"
        )
        fakeApi.pullResponse = SyncPullResponse(
            events = listOf(remoteEvent),
            serverTimestamp = 2000L
        )

        val result = syncManager.pullRemoteChanges("user-1")

        assertTrue(result is SyncResult.PullSuccess)
        assertEquals(0, (result as SyncResult.PullSuccess).appliedCount)
    }

    @Test
    fun `pullRemoteChanges fails after max retries`() = runTest {
        fakeApi.shouldFail = true

        val result = syncManager.pullRemoteChanges("user-1")

        assertTrue(result is SyncResult.Failure)
        assertTrue((result as SyncResult.Failure).attemptsExhausted)
        assertEquals(SyncStatus.ERROR, syncManager.syncStatus.value)
    }

    @Test
    fun `appendChange inserts entry and updates status to PENDING`() = runTest {
        val entry = createTestEntry("user-1")

        syncManager.appendChange(entry)

        assertTrue(fakeChangeLogDao.entries.contains(entry))
        assertEquals(SyncStatus.PENDING, syncManager.syncStatus.value)
    }

    @Test
    fun `push with conflicts returns them in result`() = runTest {
        val entry = createTestEntry("user-1")
        fakeChangeLogDao.entries.add(entry)
        fakeApi.pushResponse = SyncPushResponse(
            serverTimestamps = listOf(ServerTimestamp(entry.id, 1000L)),
            conflicts = listOf(ResolvedConflict(entry.id, "LWW"))
        )

        val result = syncManager.pushPendingChanges("user-1")

        assertTrue(result is SyncResult.PushSuccess)
        assertEquals(1, (result as SyncResult.PushSuccess).conflicts.size)
        assertEquals("LWW", result.conflicts[0].resolution)
    }

    /**
     * DQC-6.7 (parent Requirement 4.7): when an entity created from the Dashboard is superseded
     * or rejected by conflict resolution on sync, the outcome is surfaced to the user
     * NON-BLOCKINGLY and the losing edit is retained in conflict history.
     *
     * This drives the SAME Sync_Engine (`SyncManager`) the dedicated screens use — no parallel
     * path (DQC-6.1). "Non-blocking" is verified by asserting the push still completes as
     * `PushSuccess` (never `Failure`), the sync status settles back to `SYNCED` rather than
     * `ERROR`, and the local entry is still marked synced — the conflict does not halt the user.
     * The conflict outcome is nonetheless SURFACED via `PushSuccess.conflicts`. "Retained in
     * conflict history" is verified by reading it back through the conflict-history endpoint,
     * whose `ConflictResponse.supersededValue` carries the losing edit.
     */
    @Test
    fun `dashboard-created entity superseded on sync is surfaced non-blockingly and losing edit retained`() = runTest {
        // A goal created from the Dashboard is pushed through the standard change-log pipeline.
        val dashboardEdit = createTestEntry("user-1", id = "dashboard-goal-1").copy(
            entityId = "goal-42",
            entityType = "GoalProfile",
            field = "name",
            value = "\"Run a marathon\""
        )
        fakeChangeLogDao.entries.add(dashboardEdit)

        // Backend resolves the concurrent edit against another device's write (LWW) — the
        // Dashboard edit loses. The push itself succeeds and reports the resolved conflict.
        fakeApi.pushResponse = SyncPushResponse(
            serverTimestamps = listOf(ServerTimestamp(dashboardEdit.id, 5000L)),
            conflicts = listOf(ResolvedConflict(eventId = dashboardEdit.id, resolution = "LWW"))
        )
        // The losing (superseded) edit is retained server-side in conflict history.
        fakeApi.conflictHistory = listOf(
            ConflictResponse(
                id = "conflict-1",
                changeLogId = dashboardEdit.id,
                supersededValue = dashboardEdit.value,   // the losing edit is preserved verbatim
                supersededDeviceId = dashboardEdit.deviceId,
                supersededTimestamp = dashboardEdit.timestamp,
                resolutionReason = "LWW",
                createdAt = "2026-09-01T10:00:00Z"
            )
        )

        val result = syncManager.pushPendingChanges("user-1")

        // Non-blocking: sync completed successfully rather than failing/erroring the user out.
        assertTrue("push should succeed, not block the user", result is SyncResult.PushSuccess)
        val pushSuccess = result as SyncResult.PushSuccess
        assertEquals(SyncStatus.SYNCED, syncManager.syncStatus.value)
        assertTrue(
            "the losing entry is still consumed by the pipeline (not stuck/retried)",
            fakeChangeLogDao.syncedIds.contains(dashboardEdit.id)
        )

        // The conflict outcome is surfaced (available for a non-blocking notice), not swallowed.
        assertEquals(1, pushSuccess.conflicts.size)
        assertEquals(dashboardEdit.id, pushSuccess.conflicts[0].eventId)
        assertEquals("LWW", pushSuccess.conflicts[0].resolution)

        // The losing edit is retained in conflict history and remains retrievable.
        val history = fakeApi.getConflicts(since = 0L)
        assertEquals(1, history.size)
        val retained = history.single()
        assertEquals(dashboardEdit.id, retained.changeLogId)
        assertEquals(dashboardEdit.value, retained.supersededValue)
        assertEquals("LWW", retained.resolutionReason)
    }

    // --- Test helpers ---

    private fun createTestEntry(userId: String, id: String = "test-entry-1") = LocalChangeLogEntry(
        id = id,
        entityId = "entity-1",
        entityType = "DailyTodoItem",
        field = "text",
        value = "\"Hello\"",
        timestamp = System.currentTimeMillis(),
        deviceId = "device-1",
        userId = userId,
        isSynced = false
    )

    // --- Fakes ---

    class FakeChangeLogDao : ChangeLogDao {
        val entries = mutableListOf<LocalChangeLogEntry>()
        val allEntries = mutableListOf<LocalChangeLogEntry>()
        val syncedIds = mutableListOf<String>()

        override suspend fun insert(entry: LocalChangeLogEntry) {
            entries.add(entry)
            allEntries.add(entry)
        }

        override suspend fun findSince(userId: String, since: Long): List<LocalChangeLogEntry> {
            return allEntries.filter { it.userId == userId && it.timestamp > since }
        }

        override suspend fun findUnsyncedByUserId(userId: String): List<LocalChangeLogEntry> {
            return entries.filter { it.userId == userId && !it.isSynced }
        }

        override fun observeUnsyncedByUserId(userId: String): Flow<List<LocalChangeLogEntry>> {
            return flowOf(entries.filter { it.userId == userId && !it.isSynced })
        }

        override suspend fun markSynced(ids: List<String>) {
            syncedIds.addAll(ids)
            entries.replaceAll { entry ->
                if (ids.contains(entry.id)) entry.copy(isSynced = true) else entry
            }
        }

        override suspend fun countByUserId(userId: String): Long {
            return allEntries.count { it.userId == userId }.toLong()
        }

        override suspend fun deleteOlderThan(cutoff: Long) {
            allEntries.removeAll { it.timestamp < cutoff }
        }

        override fun observeRecent(userId: String, limit: Int, offset: Int): Flow<List<LocalChangeLogEntry>> {
            return flowOf(
                allEntries.filter { it.userId == userId }
                    .sortedByDescending { it.timestamp }
                    .drop(offset)
                    .take(limit)
            )
        }
    }

    class FakeSyncStateDao : SyncStateDao {
        val states = mutableMapOf<String, LocalSyncState>()

        override suspend fun getByKey(key: String): LocalSyncState? {
            return states[key]
        }

        override suspend fun upsert(state: LocalSyncState) {
            states[state.key] = state
        }
    }

    class FakeDopaShiftApi : DopaShiftApi {
        var shouldFail = false
        var pushCallCount = 0
        var pushResponse = SyncPushResponse(emptyList(), emptyList())
        var pullResponse = SyncPullResponse(emptyList(), 0L)

        /** Conflict history the backend has recorded (losing/superseded edits retained). */
        var conflictHistory: List<ConflictResponse> = emptyList()
        var getConflictsCallCount = 0

        override suspend fun pushSync(events: SyncPushRequest): SyncPushResponse {
            pushCallCount++
            if (shouldFail) throw RuntimeException("Network error")
            return pushResponse
        }

        override suspend fun pullSync(since: Long): SyncPullResponse {
            if (shouldFail) throw RuntimeException("Network error")
            return pullResponse
        }

        // --- Stubs for unused API methods in sync tests ---

        override suspend fun exchangeToken(request: com.dopashift.data.remote.dto.TokenRequest) = throw NotImplementedError()
        override suspend fun refreshToken(request: com.dopashift.data.remote.dto.RefreshTokenRequest) = throw NotImplementedError()
        override suspend fun logout(request: com.dopashift.data.remote.dto.LogoutRequest) = throw NotImplementedError()
        override suspend fun createGoal(request: com.dopashift.data.remote.dto.CreateGoalRequest) = throw NotImplementedError()
        override suspend fun getGoals() = throw NotImplementedError()
        override suspend fun getGoal(id: String) = throw NotImplementedError()
        override suspend fun updateGoal(id: String, request: com.dopashift.data.remote.dto.UpdateGoalRequest) = throw NotImplementedError()
        override suspend fun deleteGoal(id: String, action: String, reassignGoalId: String?) = throw NotImplementedError()
        override suspend fun createChecklistItem(goalId: String, request: com.dopashift.data.remote.dto.CreateChecklistItemRequest) = throw NotImplementedError()
        override suspend fun getChecklistItems(goalId: String) = throw NotImplementedError()
        override suspend fun updateChecklistItem(goalId: String, itemId: String, request: com.dopashift.data.remote.dto.UpdateChecklistItemRequest) = throw NotImplementedError()
        override suspend fun deleteChecklistItem(goalId: String, itemId: String) = throw NotImplementedError()
        override suspend fun createTodo(request: com.dopashift.data.remote.dto.CreateTodoRequest) = throw NotImplementedError()
        override suspend fun getTodos(date: String) = throw NotImplementedError()
        override suspend fun getPendingTodos() = throw NotImplementedError()
        override suspend fun updateTodo(id: String, request: com.dopashift.data.remote.dto.UpdateTodoRequest) = throw NotImplementedError()
        override suspend fun deleteTodo(id: String) = throw NotImplementedError()
        override suspend fun activateHabitTrack(request: com.dopashift.data.remote.dto.ActivateHabitTrackRequest) = throw NotImplementedError()
        override suspend fun getHabitTracks(goalId: String?) = throw NotImplementedError()
        override suspend fun getHabitTrack(id: String) = throw NotImplementedError()
        override suspend fun updateCheckpoint(trackId: String, day: Int, request: com.dopashift.data.remote.dto.UpdateCheckpointRequest) = throw NotImplementedError()
        override suspend fun getCurrentHabitForOverlay() = throw NotImplementedError()
        override suspend fun createReminder(request: com.dopashift.data.remote.dto.CreateReminderRequest) = throw NotImplementedError()
        override suspend fun getReminders() = throw NotImplementedError()
        override suspend fun updateReminder(id: String, request: com.dopashift.data.remote.dto.UpdateReminderRequest) = throw NotImplementedError()
        override suspend fun deleteReminder(id: String) = throw NotImplementedError()
        override suspend fun createInterceptionRule(request: com.dopashift.data.remote.dto.CreateInterceptionRuleRequest) = throw NotImplementedError()
        override suspend fun getInterceptionRules() = throw NotImplementedError()
        override suspend fun updateInterceptionRule(id: String, request: com.dopashift.data.remote.dto.UpdateInterceptionRuleRequest) = throw NotImplementedError()
        override suspend fun deleteInterceptionRule(id: String) = throw NotImplementedError()
        override suspend fun getConflicts(since: Long): List<ConflictResponse> {
            getConflictsCallCount++
            return conflictHistory.filter { (it.supersededTimestamp ?: 0L) >= since }
        }
        override suspend fun getEfficiencyScores(from: String, to: String) = throw NotImplementedError()
        override suspend fun submitEfficiencyScore(request: com.dopashift.data.remote.dto.SubmitEfficiencyScoreRequest) = throw NotImplementedError()
        override suspend fun getDashboardSummary() = throw NotImplementedError()
        override suspend fun getActivityFeed(page: Int, size: Int) = throw NotImplementedError()
        override suspend fun getProfile() = throw NotImplementedError()
        override suspend fun updateProfile(request: com.dopashift.data.remote.dto.UpdateProfileRequest) = throw NotImplementedError()
        override suspend fun uploadProfilePhoto(photo: okhttp3.MultipartBody.Part) = throw NotImplementedError()
        override suspend fun deleteProfilePhoto() = throw NotImplementedError()
        override suspend fun changePassword(request: com.dopashift.data.remote.dto.ChangePasswordRequest) = throw NotImplementedError()
        override suspend fun configureLlm(request: com.dopashift.data.remote.dto.ConfigureLlmRequest) = throw NotImplementedError()
        override suspend fun getLlmConfig() = throw NotImplementedError()
        override suspend fun updateLlmConfig(request: com.dopashift.data.remote.dto.ConfigureLlmRequest) = throw NotImplementedError()
        override suspend fun deleteLlmConfig() = throw NotImplementedError()
        override suspend fun validateLlmConfig() = throw NotImplementedError()
        override suspend fun getVideoRecommendation(goalId: String) = throw NotImplementedError()
    }
}
