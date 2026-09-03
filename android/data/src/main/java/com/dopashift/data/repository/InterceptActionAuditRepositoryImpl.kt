package com.dopashift.data.repository

import com.dopashift.data.local.dao.InterceptActionAuditDao
import com.dopashift.data.local.entity.LocalInterceptActionAudit
import com.dopashift.domain.entity.InterceptActionAudit
import com.dopashift.domain.entity.InterceptActionType
import com.dopashift.domain.repository.InterceptActionAuditRepository
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [InterceptActionAuditRepository].
 *
 * Maps between [LocalInterceptActionAudit] (Room entity) and
 * [InterceptActionAudit] (domain entity): UUID ↔ String,
 * [InterceptActionType] ↔ its `.name` String (`CONTINUE` /
 * `SWITCH_TO_DOPASHIFT`), and `recordedAt` ([Instant] ↔ epoch millis).
 *
 * The audit log is LOCAL ONLY (Requirements 3.4, 3.5): this implementation has
 * no network dependency and the entries are never emitted to any sync path.
 * Every operation is scoped to the authenticated `userId`. [append] cross-checks
 * the domain entry's owner against the caller so an entry can never be written
 * under another user's identity; the write is wrapped in [runCatching] so a
 * failed local-store write returns [Result.failure] without throwing (a failed
 * audit write must never block the overlay action itself).
 *
 * Note: the Hilt binding for this implementation is provided in the data
 * module's [com.dopashift.data.di.RepositoryModule].
 */
@Singleton
class InterceptActionAuditRepositoryImpl @Inject constructor(
    private val dao: InterceptActionAuditDao
) : InterceptActionAuditRepository {

    override suspend fun append(userId: UUID, audit: InterceptActionAudit): Result<Unit> {
        return runCatching {
            // Cross-check ownership: the entry must belong to the caller so no
            // audit record is written under another user's identity.
            require(audit.userId == userId) {
                "Audit entry does not belong to the authenticated user"
            }
            dao.insert(audit.toLocal())
        }
    }

    override suspend fun listForUser(userId: UUID): List<InterceptActionAudit> {
        return dao.listByUser(userId.toString()).map { it.toDomain() }
    }

    private fun LocalInterceptActionAudit.toDomain(): InterceptActionAudit {
        return InterceptActionAudit(
            id = UUID.fromString(id),
            userId = UUID.fromString(userId),
            appPackageName = appPackageName,
            actionType = InterceptActionType.valueOf(actionType),
            recordedAt = Instant.ofEpochMilli(recordedAt)
        )
    }

    private fun InterceptActionAudit.toLocal(): LocalInterceptActionAudit {
        return LocalInterceptActionAudit(
            id = id.toString(),
            userId = userId.toString(),
            appPackageName = appPackageName,
            actionType = actionType.name,
            recordedAt = recordedAt.toEpochMilli()
        )
    }
}
