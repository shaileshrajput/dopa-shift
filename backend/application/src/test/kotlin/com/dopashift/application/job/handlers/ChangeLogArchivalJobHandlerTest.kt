package com.dopashift.application.job.handlers

import com.dopashift.application.job.JobType
import com.dopashift.domain.entity.ChangeLogEntry
import com.dopashift.domain.repository.ChangeLogRepository
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChangeLogArchivalJobHandlerTest {

    private val fixedInstant = Instant.parse("2024-06-15T10:00:00Z")
    private val fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    private fun createHandler(
        repository: FakeChangeLogRepository = FakeChangeLogRepository()
    ): Pair<ChangeLogArchivalJobHandler, FakeChangeLogRepository> {
        val handler = ChangeLogArchivalJobHandler(repository, fixedClock)
        return handler to repository
    }

    @Test
    fun `jobType is CHANGE_LOG_ARCHIVAL`() {
        val (handler, _) = createHandler()
        assertEquals(JobType.CHANGE_LOG_ARCHIVAL, handler.jobType)
    }

    @Test
    fun `parses payload with both values`() {
        val (handler, _) = createHandler()
        val config = handler.parsePayload("""{"thresholdRows": 500000, "retentionDays": 60}""")
        assertEquals(500_000L, config.thresholdRows)
        assertEquals(60L, config.retentionDays)
    }

    @Test
    fun `uses default values when payload is empty object`() {
        val (handler, _) = createHandler()
        val config = handler.parsePayload("{}")
        assertEquals(ChangeLogArchivalJobHandler.DEFAULT_THRESHOLD_ROWS, config.thresholdRows)
        assertEquals(ChangeLogArchivalJobHandler.DEFAULT_RETENTION_DAYS, config.retentionDays)
    }

    @Test
    fun `uses default values when payload fields are missing`() {
        val (handler, _) = createHandler()
        val config = handler.parsePayload("""{"thresholdRows": 2000000}""")
        assertEquals(2_000_000L, config.thresholdRows)
        assertEquals(ChangeLogArchivalJobHandler.DEFAULT_RETENTION_DAYS, config.retentionDays)
    }

    @Test
    fun `rejects zero thresholdRows`() {
        val (handler, _) = createHandler()
        assertFailsWith<IllegalArgumentException> {
            handler.parsePayload("""{"thresholdRows": 0, "retentionDays": 90}""")
        }
    }

    @Test
    fun `rejects zero retentionDays`() {
        val (handler, _) = createHandler()
        assertFailsWith<IllegalArgumentException> {
            handler.parsePayload("""{"thresholdRows": 1000000, "retentionDays": 0}""")
        }
    }

    @Test
    fun `skips archival when row count does not exceed threshold`() = runTest {
        val repository = FakeChangeLogRepository(totalCount = 500_000L)
        val handler = ChangeLogArchivalJobHandler(repository, fixedClock)

        handler.handle("""{"thresholdRows": 1000000, "retentionDays": 90}""")

        assertNull(repository.lastArchiveCutoff)
    }

    @Test
    fun `skips archival when row count equals threshold`() = runTest {
        val repository = FakeChangeLogRepository(totalCount = 1_000_000L)
        val handler = ChangeLogArchivalJobHandler(repository, fixedClock)

        handler.handle("""{"thresholdRows": 1000000, "retentionDays": 90}""")

        assertNull(repository.lastArchiveCutoff)
    }

    @Test
    fun `archives when row count exceeds threshold`() = runTest {
        val repository = FakeChangeLogRepository(totalCount = 1_000_001L)
        val handler = ChangeLogArchivalJobHandler(repository, fixedClock)

        handler.handle("""{"thresholdRows": 1000000, "retentionDays": 90}""")

        val expectedCutoff = fixedInstant.minusSeconds(90L * 24 * 60 * 60)
        assertEquals(expectedCutoff, repository.lastArchiveCutoff)
    }

    @Test
    fun `uses custom retention days for cutoff calculation`() = runTest {
        val repository = FakeChangeLogRepository(totalCount = 2_000_000L)
        val handler = ChangeLogArchivalJobHandler(repository, fixedClock)

        handler.handle("""{"thresholdRows": 1000000, "retentionDays": 30}""")

        val expectedCutoff = fixedInstant.minusSeconds(30L * 24 * 60 * 60)
        assertEquals(expectedCutoff, repository.lastArchiveCutoff)
    }

    @Test
    fun `uses custom threshold value`() = runTest {
        val repository = FakeChangeLogRepository(totalCount = 100_001L)
        val handler = ChangeLogArchivalJobHandler(repository, fixedClock)

        handler.handle("""{"thresholdRows": 100000, "retentionDays": 90}""")

        val expectedCutoff = fixedInstant.minusSeconds(90L * 24 * 60 * 60)
        assertEquals(expectedCutoff, repository.lastArchiveCutoff)
    }

    @Test
    fun `handle with empty payload uses defaults and archives if over 1M`() = runTest {
        val repository = FakeChangeLogRepository(totalCount = 1_500_000L)
        val handler = ChangeLogArchivalJobHandler(repository, fixedClock)

        handler.handle("{}")

        val expectedCutoff = fixedInstant.minusSeconds(90L * 24 * 60 * 60)
        assertEquals(expectedCutoff, repository.lastArchiveCutoff)
    }

    @Test
    fun `handle propagates exception on repository failure`() = runTest {
        val repository = FakeChangeLogRepository(totalCount = 2_000_000L, failOnArchive = true)
        val handler = ChangeLogArchivalJobHandler(repository, fixedClock)

        assertFailsWith<RuntimeException> {
            handler.handle("""{"thresholdRows": 1000000, "retentionDays": 90}""")
        }
    }
}

/**
 * In-memory fake ChangeLogRepository for testing the archival handler.
 */
class FakeChangeLogRepository(
    private val totalCount: Long = 0L,
    private val failOnArchive: Boolean = false
) : ChangeLogRepository {

    var lastArchiveCutoff: Instant? = null
        private set

    private val entries = mutableListOf<ChangeLogEntry>()

    override suspend fun append(entry: ChangeLogEntry) {
        entries.add(entry)
    }

    override suspend fun findSince(userId: UUID, since: Instant): List<ChangeLogEntry> {
        return entries.filter { it.userId == userId && it.timestamp > since }
    }

    override suspend fun findLatestByEntityIdAndField(entityId: UUID, field: String): ChangeLogEntry? {
        return entries
            .filter { it.entityId == entityId && it.field == field }
            .maxByOrNull { it.timestamp }
    }

    override suspend fun countByUserId(userId: UUID): Long {
        return entries.count { it.userId == userId }.toLong()
    }

    override suspend fun countTotal(): Long {
        return totalCount
    }

    override suspend fun archiveOlderThan(cutoff: Instant) {
        if (failOnArchive) throw RuntimeException("Storage unavailable")
        lastArchiveCutoff = cutoff
        entries.removeAll { it.timestamp < cutoff }
    }
}
