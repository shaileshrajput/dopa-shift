package com.dopashift.domain.property

import com.dopashift.domain.entity.ChangeLogEntry
import com.dopashift.domain.strategy.LastWriteWinsStrategy
import com.dopashift.domain.strategy.MergeResult
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import java.time.Instant
import java.util.UUID

/**
 * Property 4: Same-Field Conflict Resolution Is Deterministic
 *
 * For any entity and any two offline edits to the same field from different devices,
 * after sync completes, both devices SHALL converge to the same value (the one with the
 * higher server-assigned logical timestamp), and the losing edit SHALL appear in the
 * conflict history.
 *
 * **Validates: Requirements 4.7**
 *
 * Feature: dopa-shift, Property 4: Same-Field Conflict Resolution Is Deterministic
 */
class SameFieldConflictResolutionDeterminismTest : StringSpec({

    val strategy = LastWriteWinsStrategy()

    // Generator for non-blank strings suitable for entityType, field, deviceId
    val nonBlankStringArb = Arb.string(minSize = 1, maxSize = 30, codepoints = Arb.of(('a'..'z').map { Codepoint(it.code) }))

    // Generator for Instant timestamps within a reasonable range
    val instantArb = Arb.long(
        min = Instant.parse("2020-01-01T00:00:00Z").epochSecond,
        max = Instant.parse("2030-12-31T23:59:59Z").epochSecond
    ).map { Instant.ofEpochSecond(it) }

    // Generator for a pair of ChangeLogEntries with same entityId and same field,
    // but different devices and distinct timestamps and different values.
    // In practice, the server assigns unique logical timestamps, so ties don't occur.
    val conflictingEntryPairArb = arbitrary {
        val entityId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val entityType = nonBlankStringArb.bind()
        val field = nonBlankStringArb.bind()
        val deviceA = "device-A-${nonBlankStringArb.bind()}"
        val deviceB = "device-B-${nonBlankStringArb.bind()}"
        val timestampA = instantArb.bind()
        // Ensure distinct timestamps by offsetting B by at least 1 second in either direction
        val offset = Arb.long(min = 1, max = 3600).bind()
        val direction = Arb.boolean().bind()
        val timestampB = if (direction) timestampA.plusSeconds(offset) else timestampA.minusSeconds(offset)
        val valueA = Arb.string(minSize = 1, maxSize = 50).bind()
        val valueB = Arb.string(minSize = 1, maxSize = 50).bind()

        val entryA = ChangeLogEntry(
            id = UUID.randomUUID(),
            entityId = entityId,
            entityType = entityType,
            field = field,
            value = valueA,
            timestamp = timestampA,
            deviceId = deviceA,
            userId = userId
        )

        val entryB = ChangeLogEntry(
            id = UUID.randomUUID(),
            entityId = entityId,
            entityType = entityType,
            field = field,
            value = valueB,
            timestamp = timestampB,
            deviceId = deviceB,
            userId = userId
        )

        Pair(entryA, entryB)
    }

    "same value wins regardless of which device processes the conflict (deterministic convergence)" {
        checkAll(PropTestConfig(iterations = 100), conflictingEntryPairArb) { (entryA, entryB) ->
            // Device A processes: entryA is local, entryB is remote
            val resultOnDeviceA = strategy.resolve(local = entryA, remote = entryB)

            // Device B processes: entryB is local, entryA is remote
            val resultOnDeviceB = strategy.resolve(local = entryB, remote = entryA)

            // Extract the winning value from each device's perspective
            val winningValueOnA = when (resultOnDeviceA) {
                is MergeResult.RemoteWins -> entryB.value
                is MergeResult.LocalWins -> entryA.value
                is MergeResult.FieldMerge -> error("FieldMerge not expected for same-field conflict")
            }

            val winningValueOnB = when (resultOnDeviceB) {
                is MergeResult.RemoteWins -> entryA.value
                is MergeResult.LocalWins -> entryB.value
                is MergeResult.FieldMerge -> error("FieldMerge not expected for same-field conflict")
            }

            // Both devices must converge to the same value
            winningValueOnA shouldBe winningValueOnB
        }
    }

    "the entry with the later timestamp always wins; ties go to remote" {
        checkAll(PropTestConfig(iterations = 100), conflictingEntryPairArb) { (entryA, entryB) ->
            val result = strategy.resolve(local = entryA, remote = entryB)

            when {
                entryB.timestamp > entryA.timestamp -> {
                    // Remote has later timestamp -> remote wins
                    result.shouldBeInstanceOf<MergeResult.RemoteWins>()
                }
                entryB.timestamp == entryA.timestamp -> {
                    // Tie -> remote wins (documented behavior)
                    result.shouldBeInstanceOf<MergeResult.RemoteWins>()
                }
                else -> {
                    // Local has later timestamp -> local wins
                    result.shouldBeInstanceOf<MergeResult.LocalWins>()
                }
            }
        }
    }

    "the losing entry appears as supersededEntry in the result" {
        checkAll(PropTestConfig(iterations = 100), conflictingEntryPairArb) { (entryA, entryB) ->
            val result = strategy.resolve(local = entryA, remote = entryB)

            when (result) {
                is MergeResult.RemoteWins -> {
                    // The local entry was superseded
                    result.supersededEntry shouldBe entryA
                }
                is MergeResult.LocalWins -> {
                    // The remote entry was superseded
                    result.supersededEntry shouldBe entryB
                }
                is MergeResult.FieldMerge -> {
                    error("FieldMerge not expected for same-field conflict")
                }
            }
        }
    }

    "both directions produce a superseded entry representing the losing edit in conflict history" {
        checkAll(PropTestConfig(iterations = 100), conflictingEntryPairArb) { (entryA, entryB) ->
            // Resolve from Device A perspective
            val resultOnDeviceA = strategy.resolve(local = entryA, remote = entryB)
            // Resolve from Device B perspective
            val resultOnDeviceB = strategy.resolve(local = entryB, remote = entryA)

            // Both resolutions must produce a superseded entry (proving loser goes to conflict history)
            val supersededOnA = when (resultOnDeviceA) {
                is MergeResult.RemoteWins -> resultOnDeviceA.supersededEntry
                is MergeResult.LocalWins -> resultOnDeviceA.supersededEntry
                is MergeResult.FieldMerge -> error("FieldMerge not expected")
            }

            val supersededOnB = when (resultOnDeviceB) {
                is MergeResult.RemoteWins -> resultOnDeviceB.supersededEntry
                is MergeResult.LocalWins -> resultOnDeviceB.supersededEntry
                is MergeResult.FieldMerge -> error("FieldMerge not expected")
            }

            // With distinct timestamps, both devices agree on the same loser
            // The superseded entries from both perspectives reference the same logical entry
            supersededOnA.value shouldBe supersededOnB.value
            supersededOnA.timestamp shouldBe supersededOnB.timestamp
        }
    }
})
