package com.dopashift.domain.property

import com.dopashift.domain.entity.ChangeLogEntry
import com.dopashift.domain.strategy.DeleteWinsStrategy
import com.dopashift.domain.strategy.MergeResult
import io.kotest.common.ExperimentalKotest
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
 * Property 5: Delete Wins Over Edit
 *
 * *For any* entity that is deleted on one device and edited on another while both
 * are offline, after sync completes, the entity SHALL be deleted on both devices,
 * and the attempted edit SHALL appear in the conflict history.
 *
 * **Validates: Requirements 4.11**
 *
 * Feature: dopa-shift, Property 5: Delete Wins Over Edit
 */
@OptIn(ExperimentalKotest::class)
class DeleteWinsOverEditPropertyTest : StringSpec({

    val strategy = DeleteWinsStrategy()

    val arbEntityType = Arb.of(listOf("GoalProfile", "DailyTodoItem", "GoalChecklistItem", "HabitTrack", "HabitCheckpoint"))
    val arbField = Arb.of(listOf("text", "name", "category", "isCompleted", "status", "description"))
    val arbDeviceId = Arb.string(minSize = 1, maxSize = 20, codepoints = Arb.of(('a'..'z').map { Codepoint(it.code) }))
    val arbNonNullValue = Arb.string(minSize = 1, maxSize = 100, codepoints = Arb.of(('a'..'z').map { Codepoint(it.code) }))
    val arbTimestamp = Arb.long(min = 1_000_000_000L, max = 2_000_000_000L).map { Instant.ofEpochSecond(it) }

    fun arbChangeLogEntry(valueArb: Arb<String?>, timestampArb: Arb<Instant> = arbTimestamp): Arb<ChangeLogEntry> =
        Arb.bind(
            Arb.uuid(),
            Arb.uuid(),
            arbEntityType,
            arbField,
            valueArb,
            timestampArb,
            arbDeviceId,
            Arb.uuid()
        ) { id, entityId, entityType, field, value, timestamp, deviceId, userId ->
            ChangeLogEntry(
                id = id,
                entityId = entityId,
                entityType = entityType,
                field = field,
                value = value,
                timestamp = timestamp,
                deviceId = deviceId,
                userId = userId
            )
        }

    /**
     * **Validates: Requirements 4.11**
     *
     * When the delete is on the remote device and the edit is on the local device,
     * the delete (remote) always wins regardless of timestamp ordering.
     */
    "remote delete always wins over local edit regardless of timestamps" {
        checkAll(PropTestConfig(iterations = 100), arbNonNullValue, arbTimestamp, arbTimestamp, arbEntityType, arbField, arbDeviceId, arbDeviceId, Arb.uuid(), Arb.uuid()) {
            editValue, localTimestamp, remoteTimestamp, entityType, field, localDeviceId, remoteDeviceId, entityId, userId ->

            val localEdit = ChangeLogEntry(
                id = UUID.randomUUID(),
                entityId = entityId,
                entityType = entityType,
                field = field,
                value = editValue,
                timestamp = localTimestamp,
                deviceId = localDeviceId,
                userId = userId
            )

            val remoteDelete = ChangeLogEntry(
                id = UUID.randomUUID(),
                entityId = entityId,
                entityType = entityType,
                field = field,
                value = null,
                timestamp = remoteTimestamp,
                deviceId = remoteDeviceId,
                userId = userId
            )

            val result = strategy.resolve(localEdit, remoteDelete)

            result.shouldBeInstanceOf<MergeResult.RemoteWins>()
            result.supersededEntry shouldBe localEdit
        }
    }

    /**
     * **Validates: Requirements 4.11**
     *
     * When the delete is on the local device and the edit is on the remote device,
     * the delete (local) always wins regardless of timestamp ordering.
     */
    "local delete always wins over remote edit regardless of timestamps" {
        checkAll(PropTestConfig(iterations = 100), arbNonNullValue, arbTimestamp, arbTimestamp, arbEntityType, arbField, arbDeviceId, arbDeviceId, Arb.uuid(), Arb.uuid()) {
            editValue, localTimestamp, remoteTimestamp, entityType, field, localDeviceId, remoteDeviceId, entityId, userId ->

            val localDelete = ChangeLogEntry(
                id = UUID.randomUUID(),
                entityId = entityId,
                entityType = entityType,
                field = field,
                value = null,
                timestamp = localTimestamp,
                deviceId = localDeviceId,
                userId = userId
            )

            val remoteEdit = ChangeLogEntry(
                id = UUID.randomUUID(),
                entityId = entityId,
                entityType = entityType,
                field = field,
                value = editValue,
                timestamp = remoteTimestamp,
                deviceId = remoteDeviceId,
                userId = userId
            )

            val result = strategy.resolve(localDelete, remoteEdit)

            result.shouldBeInstanceOf<MergeResult.LocalWins>()
            result.supersededEntry shouldBe remoteEdit
        }
    }

    /**
     * **Validates: Requirements 4.11**
     *
     * The edit entry (the non-delete) always appears as the supersededEntry in the
     * MergeResult, proving it will be recorded in conflict history.
     */
    "the edit entry always appears as supersededEntry in the merge result" {
        checkAll(PropTestConfig(iterations = 100), arbNonNullValue, arbTimestamp, arbTimestamp, arbEntityType, arbField, arbDeviceId, arbDeviceId, Arb.uuid(), Arb.uuid(), Arb.boolean()) {
            editValue, timestamp1, timestamp2, entityType, field, deviceId1, deviceId2, entityId, userId, deleteIsLocal ->

            val deleteEntry = ChangeLogEntry(
                id = UUID.randomUUID(),
                entityId = entityId,
                entityType = entityType,
                field = field,
                value = null,
                timestamp = timestamp1,
                deviceId = deviceId1,
                userId = userId
            )

            val editEntry = ChangeLogEntry(
                id = UUID.randomUUID(),
                entityId = entityId,
                entityType = entityType,
                field = field,
                value = editValue,
                timestamp = timestamp2,
                deviceId = deviceId2,
                userId = userId
            )

            val result = if (deleteIsLocal) {
                strategy.resolve(deleteEntry, editEntry)
            } else {
                strategy.resolve(editEntry, deleteEntry)
            }

            // The edit entry should always be the superseded one
            val superseded = when (result) {
                is MergeResult.LocalWins -> result.supersededEntry
                is MergeResult.RemoteWins -> result.supersededEntry
                is MergeResult.FieldMerge -> throw AssertionError("FieldMerge not expected for delete-vs-edit")
            }
            superseded.value shouldBe editValue
            superseded shouldBe editEntry
        }
    }
})
