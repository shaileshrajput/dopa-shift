package com.dopashift.domain.creation.usecase

import com.dopashift.domain.creation.DeviceIdProvider
import com.dopashift.domain.creation.DomainClock
import com.dopashift.domain.entity.ChangeLogEntry
import com.dopashift.domain.repository.ChangeLogRepository
import java.util.UUID

/**
 * Shared, framework-free helper the creation interactors use to append field-level create
 * events to the append-only [ChangeLogRepository], so every created entity flows through
 * the existing Sync_Engine regardless of connectivity (offline-first: local Room is written
 * first by the repository, then these events are enqueued — no network call here) (DQC-6.1).
 *
 * Change_Log shape follows the existing convention (see the interception overlay writer):
 * one [ChangeLogEntry] per changed field, `entityType` = the domain entity's class simple
 * name (e.g. `"GoalProfile"`, `"DailyTodoItem"`, `"HabitTrack"`, `"HabitCheckpoint"`),
 * `value` = the field's stringified value (null denotes a deletion), `deviceId` from the
 * injected [DeviceIdProvider], and `timestamp` from the injected [DomainClock].
 *
 * Events are appended in list order so a caller can guarantee ordering (e.g. goal fields
 * before dependent habit fields for Inline_Goal_Capture, DQC-6.2).
 */
internal class ChangeLogEmitter(
    private val changeLogRepository: ChangeLogRepository,
    private val deviceIdProvider: DeviceIdProvider,
    private val clock: DomainClock
) {

    /**
     * Append one create [ChangeLogEntry] per (field, value) pair for the given entity,
     * preserving the order of [fields].
     *
     * @param entityId the created entity's id.
     * @param entityType the entity's logical Change_Log type (its class simple name).
     * @param userId the authenticated owner; every event is scoped by it.
     * @param fields ordered (field name -> stringified value) pairs describing the creation.
     */
    suspend fun appendCreate(
        entityId: UUID,
        entityType: String,
        userId: UUID,
        fields: List<Pair<String, String?>>
    ) {
        val deviceId = deviceIdProvider.deviceId()
        val timestamp = clock.now()
        for ((field, value) in fields) {
            changeLogRepository.append(
                ChangeLogEntry(
                    id = UUID.randomUUID(),
                    entityId = entityId,
                    entityType = entityType,
                    field = field,
                    value = value,
                    timestamp = timestamp,
                    deviceId = deviceId,
                    userId = userId
                )
            )
        }
    }

    /**
     * Append a single deletion [ChangeLogEntry] for the given entity, so an Undo (or any
     * standard delete) flows through the Sync_Engine as a real deletion event rather than a
     * local-only rollback (DQC-1.7). Following the shape convention documented above, a null
     * [ChangeLogEntry.value] denotes a deletion; the [DELETED_FIELD] marker names the event.
     *
     * @param entityId the deleted entity's id.
     * @param entityType the entity's logical Change_Log type (its class simple name).
     * @param userId the authenticated owner; the event is scoped by it.
     */
    suspend fun appendDelete(
        entityId: UUID,
        entityType: String,
        userId: UUID
    ) {
        changeLogRepository.append(
            ChangeLogEntry(
                id = UUID.randomUUID(),
                entityId = entityId,
                entityType = entityType,
                field = DELETED_FIELD,
                value = null,
                timestamp = clock.now(),
                deviceId = deviceIdProvider.deviceId(),
                userId = userId
            )
        )
    }

    private companion object {
        /** Marker field naming a deletion event; its null value denotes the deletion. */
        const val DELETED_FIELD = "deleted"
    }
}
