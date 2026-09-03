package com.dopashift.domain.repository

import com.dopashift.domain.entity.InterceptionRule
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.UUID

/**
 * Repository port interface for interception Rule persistence.
 *
 * Every operation is scoped to the authenticated [userId]. Implementations MUST
 * cross-check the supplied [userId] against each Rule's stored owner and reject
 * any cross-user access: reads return null/empty and writes return
 * [Result.failure] without mutating another user's data (Requirement 3.8).
 *
 * CRUD operations return [Result] so that a failed local-store write preserves
 * the prior persisted state and surfaces an error to the caller rather than
 * throwing (Requirements 3.9, 6.1). Writes are offline-first: they go to the
 * Local_Store first.
 */
interface InterceptionRuleRepository {

    /**
     * Creates or updates a Rule for the authenticated user. On failure the prior
     * persisted state is preserved and a failure result is returned.
     */
    suspend fun save(userId: UUID, rule: InterceptionRule): Result<Unit>

    /**
     * Returns the active (enabled) Rule for the given app package owned by the
     * authenticated user, or null if no such Rule exists.
     */
    suspend fun findActiveByPackage(userId: UUID, pkg: String): InterceptionRule?

    /**
     * Returns all Rules owned by the authenticated user, including paused Rules.
     */
    suspend fun listForUser(userId: UUID): List<InterceptionRule>

    /**
     * Observes all Rules owned by the authenticated user as a reactive Flow.
     * A new list is emitted whenever the underlying data changes.
     */
    fun observeForUser(userId: UUID): Flow<List<InterceptionRule>>

    /**
     * Updates the daily limit (in whole minutes) of a Rule owned by the
     * authenticated user. On failure the prior persisted value is preserved and
     * a failure result is returned.
     */
    suspend fun updateDailyLimit(userId: UUID, ruleId: UUID, minutes: Int): Result<Unit>

    /**
     * Pauses a Rule owned by the authenticated user for the given local calendar
     * day; the Rule auto-resumes on the next day boundary. On failure the prior
     * persisted state is preserved and a failure result is returned.
     */
    suspend fun pauseForToday(userId: UUID, ruleId: UUID, today: LocalDate): Result<Unit>

    /**
     * Deletes a Rule owned by the authenticated user. On failure the prior
     * persisted state is preserved and a failure result is returned.
     */
    suspend fun delete(userId: UUID, ruleId: UUID): Result<Unit>
}
