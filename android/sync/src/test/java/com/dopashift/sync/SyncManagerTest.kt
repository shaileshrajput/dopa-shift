package com.dopashift.sync

import com.dopashift.data.local.dao.ChangeLogDao
import com.dopashift.data.local.dao.SyncStateDao
import com.dopashift.data.local.entity.LocalChangeLogEntry
import com.dopashift.data.local.entity.LocalSyncState
import com.dopashift.data.remote.DopaShiftApi
import com.dopashift.data.remote.ResolvedConflict
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

        override suspend fun pushSync(events: SyncPushRequest): SyncPushResponse {
            pushCallCount++
            if (shouldFail) throw RuntimeException("Network error")
            return pushResponse
        }

        override suspend fun pullSync(since: Long): SyncPullResponse {
            if (shouldFail) throw RuntimeException("Network error")
            return pullResponse
        }
    }
}
