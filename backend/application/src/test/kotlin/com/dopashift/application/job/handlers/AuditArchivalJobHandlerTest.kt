package com.dopashift.application.job.handlers

import com.dopashift.application.job.JobType
import com.dopashift.domain.entity.AuditLogEntry
import com.dopashift.domain.repository.AuditLogRepository
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AuditArchivalJobHandlerTest {

    private val fixedInstant = Instant.parse("2024-06-15T10:00:00Z")
    private val fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    private fun createHandler(
        repository: FakeAuditLogRepositoryWithPurge = FakeAuditLogRepositoryWithPurge()
    ): Pair<AuditArchivalJobHandler, FakeAuditLogRepositoryWithPurge> {
        val handler = AuditArchivalJobHandler(repository, fixedClock)
        return handler to repository
    }

    @Test
    fun `jobType is AUDIT_ARCHIVAL`() {
        val (handler, _) = createHandler()
        assertEquals(JobType.AUDIT_ARCHIVAL, handler.jobType)
    }

    @Test
    fun `parses payload with both retention values`() {
        val (handler, _) = createHandler()
        val config = handler.parsePayload("""{"auditRetentionDays": 365, "appLogRetentionDays": 30}""")
        assertEquals(365L, config.auditRetentionDays)
        assertEquals(30L, config.appLogRetentionDays)
    }

    @Test
    fun `uses default values when payload fields are missing`() {
        val (handler, _) = createHandler()
        val config = handler.parsePayload("{}")
        assertEquals(AuditArchivalJobHandler.DEFAULT_AUDIT_RETENTION_DAYS, config.auditRetentionDays)
        assertEquals(AuditArchivalJobHandler.DEFAULT_APP_LOG_RETENTION_DAYS, config.appLogRetentionDays)
    }

    @Test
    fun `rejects zero auditRetentionDays`() {
        val (handler, _) = createHandler()
        assertFailsWith<IllegalArgumentException> {
            handler.parsePayload("""{"auditRetentionDays": 0, "appLogRetentionDays": 30}""")
        }
    }

    @Test
    fun `rejects zero appLogRetentionDays`() {
        val (handler, _) = createHandler()
        assertFailsWith<IllegalArgumentException> {
            handler.parsePayload("""{"auditRetentionDays": 365, "appLogRetentionDays": 0}""")
        }
    }

    @Test
    fun `handle purges app logs older than 30 days`() = runTest {
        val repository = FakeAuditLogRepositoryWithPurge()
        val handler = AuditArchivalJobHandler(repository, fixedClock)

        // Add app log entries — one old (60 days), one recent (10 days)
        val oldAppLog = createEntry(
            operationType = "APP_LOG_REQUEST",
            timestamp = fixedInstant.minusSeconds(60L * 24 * 60 * 60) // 60 days ago
        )
        val recentAppLog = createEntry(
            operationType = "APP_LOG_ERROR",
            timestamp = fixedInstant.minusSeconds(10L * 24 * 60 * 60) // 10 days ago
        )
        repository.entries.add(oldAppLog)
        repository.entries.add(recentAppLog)

        handler.handle("""{"auditRetentionDays": 365, "appLogRetentionDays": 30}""")

        // Should have purged the old app log
        assertEquals(2, repository.purgeInvocations.size)

        // First invocation: app log purge (30-day cutoff)
        val appLogPurge = repository.purgeInvocations[0]
        val expectedAppLogCutoff = fixedInstant.minusSeconds(30L * 24 * 60 * 60)
        assertEquals(expectedAppLogCutoff, appLogPurge.cutoff)
        assertEquals(setOf(AuditArchivalJobHandler.APP_LOG_OPERATION_PREFIX), appLogPurge.operationTypes)
    }

    @Test
    fun `handle purges audit entries older than 1 year`() = runTest {
        val repository = FakeAuditLogRepositoryWithPurge()
        val handler = AuditArchivalJobHandler(repository, fixedClock)

        // Add an audit entry that's 400 days old
        val oldAuditEntry = createEntry(
            operationType = "GOAL_DELETION",
            timestamp = fixedInstant.minusSeconds(400L * 24 * 60 * 60)
        )
        repository.entries.add(oldAuditEntry)

        handler.handle("""{"auditRetentionDays": 365, "appLogRetentionDays": 30}""")

        // Second invocation: audit purge (365-day cutoff)
        val auditPurge = repository.purgeInvocations[1]
        val expectedAuditCutoff = fixedInstant.minusSeconds(365L * 24 * 60 * 60)
        assertEquals(expectedAuditCutoff, auditPurge.cutoff)
        assertTrue(auditPurge.operationTypes.isEmpty())
    }

    @Test
    fun `handle with custom retention values uses those values`() = runTest {
        val repository = FakeAuditLogRepositoryWithPurge()
        val handler = AuditArchivalJobHandler(repository, fixedClock)

        handler.handle("""{"auditRetentionDays": 730, "appLogRetentionDays": 7}""")

        // App log purge: 7 days
        val appLogPurge = repository.purgeInvocations[0]
        val expectedAppLogCutoff = fixedInstant.minusSeconds(7L * 24 * 60 * 60)
        assertEquals(expectedAppLogCutoff, appLogPurge.cutoff)

        // Audit purge: 730 days
        val auditPurge = repository.purgeInvocations[1]
        val expectedAuditCutoff = fixedInstant.minusSeconds(730L * 24 * 60 * 60)
        assertEquals(expectedAuditCutoff, auditPurge.cutoff)
    }

    @Test
    fun `handle propagates exception on repository failure`() = runTest {
        val repository = FakeAuditLogRepositoryWithPurge(failOnPurge = true)
        val handler = AuditArchivalJobHandler(repository, fixedClock)

        assertFailsWith<RuntimeException> {
            handler.handle("""{"auditRetentionDays": 365, "appLogRetentionDays": 30}""")
        }
    }

    @Test
    fun `handle with empty payload uses defaults`() = runTest {
        val repository = FakeAuditLogRepositoryWithPurge()
        val handler = AuditArchivalJobHandler(repository, fixedClock)

        handler.handle("{}")

        val appLogPurge = repository.purgeInvocations[0]
        val expectedAppLogCutoff = fixedInstant.minusSeconds(30L * 24 * 60 * 60)
        assertEquals(expectedAppLogCutoff, appLogPurge.cutoff)

        val auditPurge = repository.purgeInvocations[1]
        val expectedAuditCutoff = fixedInstant.minusSeconds(365L * 24 * 60 * 60)
        assertEquals(expectedAuditCutoff, auditPurge.cutoff)
    }

    private fun createEntry(
        operationType: String,
        timestamp: Instant
    ): AuditLogEntry = AuditLogEntry(
        id = UUID.randomUUID(),
        correlationId = "corr-${UUID.randomUUID()}",
        userId = UUID.randomUUID(),
        operationType = operationType,
        entityId = null,
        beforeState = null,
        afterState = null,
        timestamp = timestamp,
        previousHash = "previous-hash",
        entryHash = "entry-hash-${UUID.randomUUID()}"
    )
}

/**
 * In-memory fake that records purge invocations for assertion.
 */
class FakeAuditLogRepositoryWithPurge(
    private val failOnPurge: Boolean = false
) : AuditLogRepository {
    val entries = mutableListOf<AuditLogEntry>()
    val purgeInvocations = mutableListOf<PurgeInvocation>()

    override suspend fun append(entry: AuditLogEntry) {
        entries.add(entry)
    }

    override suspend fun findByCorrelationId(correlationId: String): List<AuditLogEntry> {
        return entries.filter { it.correlationId == correlationId }
    }

    override suspend fun getLastEntry(): AuditLogEntry? {
        return entries.lastOrNull()
    }

    override suspend fun purgeOlderThan(cutoff: Instant, operationTypes: Set<String>): Long {
        if (failOnPurge) throw RuntimeException("Storage unavailable")

        val toRemove = entries.filter { entry ->
            entry.timestamp < cutoff &&
                (operationTypes.isEmpty() || operationTypes.any { prefix -> entry.operationType.startsWith(prefix) })
        }
        entries.removeAll(toRemove)
        purgeInvocations.add(PurgeInvocation(cutoff, operationTypes))
        return toRemove.size.toLong()
    }

    data class PurgeInvocation(val cutoff: Instant, val operationTypes: Set<String>)
}
