package com.dopashift.domain.property

import com.dopashift.domain.entity.ChangeLogEntry
import com.dopashift.domain.strategy.FieldLevelMergeService
import com.dopashift.domain.strategy.LastWriteWinsStrategy
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.instant
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.uuid
import io.kotest.property.checkAll
import java.time.Instant
import java.util.UUID

/**
 * Feature: dopa-shift, Property 3: Field-Level Merge Preserves Non-Conflicting Edits
 *
 * Validates: Requirements 4.6
 *
 * For any entity and any two offline edits to different fields of that entity from different
 * devices, after sync completes on both devices, both field values SHALL be present —
 * neither edit is lost.
 */
class FieldLevelMergePropertyTest : FunSpec({

    val mergeService = FieldLevelMergeService(LastWriteWinsStrategy())

    /**
     * Generator for a pair of ChangeLogEntries with the same entityId but DIFFERENT field names,
     * from different devices.
     */
    val nonConflictingEntryPairArb: Arb<Pair<ChangeLogEntry, ChangeLogEntry>> = arbitrary {
        val entityId = Arb.uuid().bind()
        val userId = Arb.uuid().bind()
        val entityType = Arb.string(minSize = 1, maxSize = 20).bind()

        // Generate two distinct field names
        val field1 = Arb.string(minSize = 1, maxSize = 30).bind()
        var field2 = Arb.string(minSize = 1, maxSize = 30).bind()
        // Ensure fields are different
        while (field2 == field1) {
            field2 = Arb.string(minSize = 1, maxSize = 30).bind()
        }

        val value1 = Arb.string(minSize = 1, maxSize = 50).bind()
        val value2 = Arb.string(minSize = 1, maxSize = 50).bind()

        val timestamp1 = Arb.instant(
            minValue = Instant.parse("2024-01-01T00:00:00Z"),
            maxValue = Instant.parse("2025-12-31T23:59:59Z")
        ).bind()
        val timestamp2 = Arb.instant(
            minValue = Instant.parse("2024-01-01T00:00:00Z"),
            maxValue = Instant.parse("2025-12-31T23:59:59Z")
        ).bind()

        val deviceId1 = "device-A-${Arb.string(minSize = 1, maxSize = 10).bind()}"
        val deviceId2 = "device-B-${Arb.string(minSize = 1, maxSize = 10).bind()}"

        val entry1 = ChangeLogEntry(
            id = Arb.uuid().bind(),
            entityId = entityId,
            entityType = entityType,
            field = field1,
            value = value1,
            timestamp = timestamp1,
            deviceId = deviceId1,
            userId = userId
        )

        val entry2 = ChangeLogEntry(
            id = Arb.uuid().bind(),
            entityId = entityId,
            entityType = entityType,
            field = field2,
            value = value2,
            timestamp = timestamp2,
            deviceId = deviceId2,
            userId = userId
        )

        Pair(entry1, entry2)
    }

    test("Property 3: Field-level merge preserves both edits when fields differ") {
        checkAll(100, nonConflictingEntryPairArb) { (localEntry, remoteEntry) ->
            // Pre-condition: same entity, different fields, different devices
            localEntry.entityId shouldBe remoteEntry.entityId
            assert(localEntry.field != remoteEntry.field) {
                "Generated entries must target different fields"
            }
            assert(localEntry.deviceId != remoteEntry.deviceId) {
                "Generated entries must come from different devices"
            }

            // Act: merge the two entries
            val result = mergeService.merge(
                localEntries = listOf(localEntry),
                remoteEntries = listOf(remoteEntry)
            )

            // Assert: both entries are preserved — neither is lost
            result.preserved.size shouldBe 2
            result.preserved shouldContain localEntry
            result.preserved shouldContain remoteEntry

            // Assert: no conflicts were raised (different fields don't conflict)
            result.conflicts.shouldBeEmpty()
        }
    }
})
