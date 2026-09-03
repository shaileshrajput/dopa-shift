package com.dopashift.domain.repository

import com.dopashift.domain.entity.InterceptActionAudit
import java.util.UUID

/**
 * Repository port for the local-only intercept-action audit log.
 *
 * Every operation is scoped to the authenticated [userId], consistent with all
 * other Rule operations. Implementations MUST cross-check the supplied [userId]
 * against each entry's stored owner and reject cross-user access.
 *
 * The audit log is on-device only: implementations have no network dependency
 * and the entries are never emitted to any sync path (Requirements 3.4, 3.5).
 * [append] returns [Result] so a failed local-store write can be handled without
 * throwing; a failed audit write must never block the overlay action itself.
 */
interface InterceptActionAuditRepository {

    /**
     * Appends a single audit entry for the authenticated user. On failure the
     * prior persisted state is preserved and a failure result is returned.
     */
    suspend fun append(userId: UUID, audit: InterceptActionAudit): Result<Unit>

    /**
     * Returns all audit entries owned by the authenticated user.
     */
    suspend fun listForUser(userId: UUID): List<InterceptActionAudit>
}
