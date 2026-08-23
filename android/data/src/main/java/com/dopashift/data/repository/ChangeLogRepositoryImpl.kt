package com.dopashift.data.repository

import com.dopashift.data.local.dao.ChangeLogDao
import com.dopashift.data.local.entity.LocalChangeLogEntry
import com.dopashift.domain.entity.ChangeLogEntry
import com.dopashift.domain.repository.ChangeLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [ChangeLogRepository].
 * Maps between [LocalChangeLogEntry] (Room entity) and [ChangeLogEntry] (domain entity).
 */
@Singleton
class ChangeLogRepositoryImpl @Inject constructor(
    private val dao: ChangeLogDao
) : ChangeLogRepository {

    override suspend fun append(entry: ChangeLogEntry) {
        dao.insert(entry.toLocal())
    }

    override suspend fun findSince(userId: UUID, since: Instant): List<ChangeLogEntry> {
        return dao.findSince(userId.toString(), since.toEpochMilli())
            .map { it.toDomain() }
    }

    override suspend fun countByUserId(userId: UUID): Long {
        return dao.countByUserId(userId.toString())
    }

    override suspend fun archiveOlderThan(cutoff: Instant) {
        dao.deleteOlderThan(cutoff.toEpochMilli())
    }

    override fun observeRecent(userId: UUID, limit: Int, offset: Int): Flow<List<ChangeLogEntry>> {
        return dao.observeRecent(userId.toString(), limit, offset)
            .map { locals -> locals.map { it.toDomain() } }
    }

    private fun LocalChangeLogEntry.toDomain(): ChangeLogEntry {
        return ChangeLogEntry(
            id = UUID.fromString(id),
            entityId = UUID.fromString(entityId),
            entityType = entityType,
            field = field,
            value = value,
            timestamp = Instant.ofEpochMilli(timestamp),
            deviceId = deviceId,
            userId = UUID.fromString(userId)
        )
    }

    private fun ChangeLogEntry.toLocal(): LocalChangeLogEntry {
        return LocalChangeLogEntry(
            id = id.toString(),
            entityId = entityId.toString(),
            entityType = entityType,
            field = field,
            value = value,
            timestamp = timestamp.toEpochMilli(),
            deviceId = deviceId,
            userId = userId.toString(),
            isSynced = false
        )
    }
}
