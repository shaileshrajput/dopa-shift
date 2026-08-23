package com.dopashift.application.audit

import com.dopashift.domain.entity.AuditLogEntry
import com.dopashift.domain.repository.AuditLogRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * Property 16: Audit Log Hash Chain Integrity
 *
 * For any sequence of audit log entries, each entry's `previous_hash` field SHALL equal
 * the `entry_hash` of the immediately preceding entry, forming a tamper-evident chain —
 * any modification to a stored entry is detectable.
 *
 * **Validates: Requirements 18.13**
 *
 * Tag: Feature: dopa-shift, Property 16: Audit Log Hash Chain Integrity
 */
class AuditLogHashChainPropertyTest : FunSpec({

    tags(
        io.kotest.core.Tag("Feature: dopa-shift"),
        io.kotest.core.Tag("Property 16: Audit Log Hash Chain Integrity")
    )

    // ===== Generators for audit log record inputs =====

    val arbOperationType = Arb.element(
        "GOAL_DELETION",
        "API_KEY_ROTATION",
        "PERMISSION_CHANGE",
        "ACCOUNT_DELETION",
        "REMINDER_CONFIG_CHANGE"
    )

    val arbJsonState = Arb.choice(
        Arb.constant(null as String?),
        Arb.string(1..200, Codepoint.alphanumeric()).map { """{"field":"$it"}""" }
    )

    val arbCorrelationId = Arb.string(5..30, Codepoint.alphanumeric()).map { "corr-$it" }

    /** Represents inputs for a single audit log record call */
    data class AuditRecordInput(
        val correlationId: String,
        val userId: UUID,
        val operationType: String,
        val entityId: UUID?,
        val beforeState: String?,
        val afterState: String?
    )

    val arbAuditRecordInput = Arb.bind(
        arbCorrelationId,
        Arb.uuid(),
        arbOperationType,
        Arb.choice(Arb.uuid().map<UUID, UUID?> { it }, Arb.constant(null as UUID?)),
        arbJsonState,
        arbJsonState
    ) { corrId, userId, opType, entityId, before, after ->
        AuditRecordInput(corrId, userId, opType, entityId, before, after)
    }

    /** Generates a list of 1–20 audit record inputs */
    val arbAuditRecordSequence = Arb.list(arbAuditRecordInput, 1..20)

    // ===== Property Tests =====

    test("Property 16: Hash chain integrity - first entry has GENESIS previousHash and subsequent entries chain correctly") {
        checkAll(100, arbAuditRecordSequence) { inputs ->
            val repository = FakeAuditLogRepository()
            val clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC)
            val service = AuditLogService(repository, clock)

            val entries = mutableListOf<AuditLogEntry>()
            for (input in inputs) {
                val entry = service.record(
                    correlationId = input.correlationId,
                    userId = input.userId,
                    operationType = input.operationType,
                    entityId = input.entityId,
                    beforeState = input.beforeState,
                    afterState = input.afterState
                )
                entries.add(entry)
            }

            // Verify first entry uses GENESIS as previousHash
            entries[0].previousHash shouldBe AuditLogService.GENESIS_HASH

            // Verify chain: entry[i].previousHash == entry[i-1].entryHash for all i > 0
            for (i in 1 until entries.size) {
                entries[i].previousHash shouldBe entries[i - 1].entryHash
            }
        }
    }

    test("Property 16: Hash chain tamper detection - modifying any entry's beforeState makes chain invalid") {
        checkAll(100, arbAuditRecordSequence) { inputs ->
            // Need at least 1 entry to tamper with
            val repository = FakeAuditLogRepository()
            val clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC)
            val service = AuditLogService(repository, clock)

            val entries = mutableListOf<AuditLogEntry>()
            for (input in inputs) {
                val entry = service.record(
                    correlationId = input.correlationId,
                    userId = input.userId,
                    operationType = input.operationType,
                    entityId = input.entityId,
                    beforeState = input.beforeState,
                    afterState = input.afterState
                )
                entries.add(entry)
            }

            // Pick a random entry to tamper with
            val tamperIndex = (0 until entries.size).random()
            val tamperedEntry = entries[tamperIndex]

            // Modify the beforeState (simulating tampering)
            val newBeforeState = """{"tampered":"true"}"""
            val recomputedHash = service.computeHash(
                previousHash = tamperedEntry.previousHash,
                operationType = tamperedEntry.operationType,
                entityId = tamperedEntry.entityId,
                timestamp = tamperedEntry.timestamp,
                beforeState = newBeforeState,
                afterState = tamperedEntry.afterState
            )

            // The recomputed hash with modified state should differ from the original entry hash
            recomputedHash shouldNotBe tamperedEntry.entryHash
        }
    }

    test("Property 16: Hash chain tamper detection - modifying any entry's afterState makes chain invalid") {
        checkAll(100, arbAuditRecordSequence) { inputs ->
            val repository = FakeAuditLogRepository()
            val clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC)
            val service = AuditLogService(repository, clock)

            val entries = mutableListOf<AuditLogEntry>()
            for (input in inputs) {
                val entry = service.record(
                    correlationId = input.correlationId,
                    userId = input.userId,
                    operationType = input.operationType,
                    entityId = input.entityId,
                    beforeState = input.beforeState,
                    afterState = input.afterState
                )
                entries.add(entry)
            }

            // Pick a random entry to tamper with
            val tamperIndex = (0 until entries.size).random()
            val tamperedEntry = entries[tamperIndex]

            // Modify the afterState (simulating tampering)
            val newAfterState = """{"tampered":"after-value"}"""
            val recomputedHash = service.computeHash(
                previousHash = tamperedEntry.previousHash,
                operationType = tamperedEntry.operationType,
                entityId = tamperedEntry.entityId,
                timestamp = tamperedEntry.timestamp,
                beforeState = tamperedEntry.beforeState,
                afterState = newAfterState
            )

            // The recomputed hash with modified state should differ from the original entry hash
            recomputedHash shouldNotBe tamperedEntry.entryHash
        }
    }
})
