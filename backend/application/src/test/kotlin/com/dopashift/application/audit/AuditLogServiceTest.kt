package com.dopashift.application.audit

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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AuditLogServiceTest {

    private val fixedInstant = Instant.parse("2024-06-15T10:30:00Z")
    private val fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    private fun createService(repository: FakeAuditLogRepository = FakeAuditLogRepository()): Pair<AuditLogService, FakeAuditLogRepository> {
        val service = AuditLogService(repository, fixedClock)
        return service to repository
    }

    @Test
    fun `first entry uses GENESIS as previousHash`() = runTest {
        val (service, repository) = createService()

        val entry = service.record(
            correlationId = "corr-001",
            userId = UUID.randomUUID(),
            operationType = "GOAL_DELETION",
            entityId = UUID.randomUUID(),
            beforeState = """{"name":"old"}""",
            afterState = null
        )

        assertEquals(AuditLogService.GENESIS_HASH, entry.previousHash)
        assertTrue(entry.entryHash.isNotBlank())
        assertEquals(1, repository.entries.size)
    }

    @Test
    fun `second entry chains to first entry hash`() = runTest {
        val (service, repository) = createService()

        val first = service.record(
            correlationId = "corr-001",
            userId = UUID.randomUUID(),
            operationType = "GOAL_DELETION",
            entityId = UUID.randomUUID()
        )

        val second = service.record(
            correlationId = "corr-002",
            userId = UUID.randomUUID(),
            operationType = "API_KEY_ROTATION",
            entityId = UUID.randomUUID()
        )

        assertEquals(first.entryHash, second.previousHash)
        assertNotEquals(first.entryHash, second.entryHash)
        assertEquals(2, repository.entries.size)
    }

    @Test
    fun `hash chain is deterministic for same inputs`() = runTest {
        val (service, _) = createService()

        val userId = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val entityId = UUID.fromString("22222222-2222-2222-2222-222222222222")

        val entry = service.record(
            correlationId = "corr-001",
            userId = userId,
            operationType = "GOAL_DELETION",
            entityId = entityId,
            beforeState = """{"name":"old"}""",
            afterState = null
        )

        // Compute expected hash manually
        val expectedHash = service.computeHash(
            previousHash = AuditLogService.GENESIS_HASH,
            operationType = "GOAL_DELETION",
            entityId = entityId,
            timestamp = fixedInstant,
            beforeState = """{"name":"old"}""",
            afterState = null
        )

        assertEquals(expectedHash, entry.entryHash)
    }

    @Test
    fun `records before and after state`() = runTest {
        val (service, repository) = createService()

        val entry = service.record(
            correlationId = "corr-001",
            userId = UUID.randomUUID(),
            operationType = "API_KEY_ROTATION",
            entityId = UUID.randomUUID(),
            beforeState = """{"key":"old-key-hash"}""",
            afterState = """{"key":"new-key-hash"}"""
        )

        assertEquals("""{"key":"old-key-hash"}""", entry.beforeState)
        assertEquals("""{"key":"new-key-hash"}""", entry.afterState)
        assertEquals(entry, repository.entries.last())
    }

    @Test
    fun `propagates correlation ID from request origin`() = runTest {
        val (service, _) = createService()

        val entry = service.record(
            correlationId = "req-abc-123",
            userId = UUID.randomUUID(),
            operationType = "ACCOUNT_DELETION"
        )

        assertEquals("req-abc-123", entry.correlationId)
    }

    @Test
    fun `findByCorrelationId delegates to repository`() = runTest {
        val (service, repository) = createService()

        // Record entries with different correlation IDs
        service.record(
            correlationId = "corr-A",
            userId = UUID.randomUUID(),
            operationType = "GOAL_DELETION"
        )
        service.record(
            correlationId = "corr-B",
            userId = UUID.randomUUID(),
            operationType = "API_KEY_ROTATION"
        )
        service.record(
            correlationId = "corr-A",
            userId = UUID.randomUUID(),
            operationType = "PERMISSION_CHANGE"
        )

        val results = service.findByCorrelationId("corr-A")
        assertEquals(2, results.size)
        assertTrue(results.all { it.correlationId == "corr-A" })
    }

    @Test
    fun `rejects blank correlation ID`() = runTest {
        val (service, _) = createService()

        assertFailsWith<IllegalArgumentException> {
            service.record(
                correlationId = "  ",
                userId = UUID.randomUUID(),
                operationType = "GOAL_DELETION"
            )
        }
    }

    @Test
    fun `rejects blank operation type`() = runTest {
        val (service, _) = createService()

        assertFailsWith<IllegalArgumentException> {
            service.record(
                correlationId = "corr-001",
                userId = UUID.randomUUID(),
                operationType = ""
            )
        }
    }

    @Test
    fun `hash includes all fields - different entity produces different hash`() = runTest {
        val service = AuditLogService(FakeAuditLogRepository(), fixedClock)

        val hash1 = service.computeHash(
            previousHash = AuditLogService.GENESIS_HASH,
            operationType = "GOAL_DELETION",
            entityId = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            timestamp = fixedInstant,
            beforeState = null,
            afterState = null
        )

        val hash2 = service.computeHash(
            previousHash = AuditLogService.GENESIS_HASH,
            operationType = "GOAL_DELETION",
            entityId = UUID.fromString("22222222-2222-2222-2222-222222222222"),
            timestamp = fixedInstant,
            beforeState = null,
            afterState = null
        )

        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `hash is 64 character hex string (SHA-256)`() = runTest {
        val service = AuditLogService(FakeAuditLogRepository(), fixedClock)

        val hash = service.computeHash(
            previousHash = AuditLogService.GENESIS_HASH,
            operationType = "TEST",
            entityId = null,
            timestamp = fixedInstant,
            beforeState = null,
            afterState = null
        )

        assertEquals(64, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `multiple entries form a valid chain`() = runTest {
        val (service, repo) = createService()

        repeat(5) { i ->
            service.record(
                correlationId = "corr-$i",
                userId = UUID.randomUUID(),
                operationType = "OPERATION_$i",
                entityId = UUID.randomUUID()
            )
        }

        val entries = repo.entries
        assertEquals(5, entries.size)

        // Verify chain integrity
        assertEquals(AuditLogService.GENESIS_HASH, entries[0].previousHash)
        for (i in 1 until entries.size) {
            assertEquals(entries[i - 1].entryHash, entries[i].previousHash)
        }
    }
}

/**
 * In-memory fake for testing the append-only audit log.
 */
class FakeAuditLogRepository : AuditLogRepository {
    val entries = mutableListOf<AuditLogEntry>()

    override suspend fun append(entry: AuditLogEntry) {
        entries.add(entry)
    }

    override suspend fun findByCorrelationId(correlationId: String): List<AuditLogEntry> {
        return entries.filter { it.correlationId == correlationId }
    }

    override suspend fun getLastEntry(): AuditLogEntry? {
        return entries.lastOrNull()
    }

    override suspend fun purgeOlderThan(cutoff: java.time.Instant, operationTypes: Set<String>): Long {
        val before = entries.size
        entries.removeAll { entry ->
            entry.timestamp.isBefore(cutoff) &&
                (operationTypes.isEmpty() || entry.operationType in operationTypes)
        }
        return (before - entries.size).toLong()
    }
}
