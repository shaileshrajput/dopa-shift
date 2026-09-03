package com.dopashift.data.repository

import com.dopashift.data.local.dao.InterceptionRuleDao
import com.dopashift.data.local.entity.LocalInterceptionRule
import com.dopashift.domain.entity.InterceptionRule
import com.dopashift.domain.entity.LimitType
import com.dopashift.domain.repository.InterceptionRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [InterceptionRuleRepository].
 *
 * Maps between [LocalInterceptionRule] (Room entity) and [InterceptionRule]
 * (domain entity): `dailyAllowanceMinutes ↔ dailyLimitMinutes`,
 * `isActive ↔ enabled`, `pausedForDate` (ISO `LocalDate` string ↔ [LocalDate]),
 * UUID ↔ String, and `createdAt` (epoch millis ↔ [Instant]).
 *
 * Every read and write is scoped to the authenticated `userId`. Per-id
 * operations (update/pause/delete) cross-check the stored owner against the
 * caller and reject mismatches without mutating state (Requirement 3.8). Writes
 * go to the Local_Store first (offline-first); a failed write returns
 * [Result.failure] with the prior persisted state preserved (Requirements 3.9,
 * 6.1).
 *
 * Note: the Hilt binding for this implementation is provided in the data
 * module's [com.dopashift.data.di.RepositoryModule].
 */
@Singleton
class InterceptionRuleRepositoryImpl @Inject constructor(
    private val dao: InterceptionRuleDao
) : InterceptionRuleRepository {

    override suspend fun save(userId: UUID, rule: InterceptionRule): Result<Unit> {
        return runCatching {
            // Cross-check ownership: the domain rule must belong to the caller,
            // and if a row with this id already exists it must be owned by the
            // caller too (no cross-user overwrite).
            require(rule.userId == userId) { "Rule does not belong to the authenticated user" }
            val existing = dao.findById(rule.id.toString())
            if (existing != null) {
                require(existing.userId == userId.toString()) {
                    "Cannot overwrite a Rule owned by another user"
                }
            }
            dao.upsert(rule.toLocal(existing))
        }
    }

    override suspend fun findActiveByPackage(userId: UUID, pkg: String): InterceptionRule? {
        return dao.findActiveByPackageName(userId.toString(), pkg)?.toDomain()
    }

    override suspend fun listForUser(userId: UUID): List<InterceptionRule> {
        return dao.listForUser(userId.toString()).map { it.toDomain() }
    }

    override fun observeForUser(userId: UUID): Flow<List<InterceptionRule>> {
        return dao.observeForUser(userId.toString())
            .map { locals -> locals.map { it.toDomain() } }
    }

    override suspend fun updateDailyLimit(
        userId: UUID,
        ruleId: UUID,
        minutes: Int
    ): Result<Unit> {
        return runCatching {
            require(minutes in 1..480) { "Daily limit must be between 1 and 480 whole minutes" }
            val existing = requireOwned(userId, ruleId)
            dao.upsert(existing.copy(dailyAllowanceMinutes = minutes))
        }
    }

    override suspend fun pauseForToday(
        userId: UUID,
        ruleId: UUID,
        today: LocalDate
    ): Result<Unit> {
        return runCatching {
            // Cross-check ownership before mutating. setPausedForDate is itself
            // user-scoped; a zero rows-affected result means the id/user pair did
            // not match, so we surface a failure without silently succeeding.
            requireOwned(userId, ruleId)
            val rowsAffected = dao.setPausedForDate(
                userId.toString(),
                ruleId.toString(),
                today.toString()
            )
            require(rowsAffected > 0) { "No Rule updated for the authenticated user" }
        }
    }

    override suspend fun delete(userId: UUID, ruleId: UUID): Result<Unit> {
        return runCatching {
            requireOwned(userId, ruleId)
            dao.deleteById(ruleId.toString())
        }
    }

    /**
     * Reads the row for [ruleId] and verifies it is owned by [userId], returning
     * the stored row. Throws if the Rule does not exist or belongs to another
     * user so the caller reports [Result.failure] without mutating state.
     */
    private suspend fun requireOwned(userId: UUID, ruleId: UUID): LocalInterceptionRule {
        val existing = dao.findById(ruleId.toString())
            ?: throw NoSuchElementException("Rule not found")
        require(existing.userId == userId.toString()) {
            "Rule does not belong to the authenticated user"
        }
        return existing
    }

    private fun LocalInterceptionRule.toDomain(): InterceptionRule {
        val mappedLimitType = limitType.toLimitType()
        return InterceptionRule(
            id = UUID.fromString(id),
            userId = UUID.fromString(userId),
            appPackageName = appPackageName.orEmpty(),
            dailyLimitMinutes = dailyAllowanceMinutes,
            limitType = mappedLimitType,
            repetitiveIntervalMinutes = when (mappedLimitType) {
                LimitType.Once -> null
                LimitType.Repetitive -> repetitiveIntervalMinutes
            },
            enabled = isActive,
            pausedForDate = pausedForDate?.let { LocalDate.parse(it) },
            createdAt = Instant.ofEpochMilli(createdAt)
        )
    }

    /**
     * Maps the domain Rule to its Room representation, preserving fields not
     * modeled by the domain entity ([LocalInterceptionRule.goalId] and
     * [LocalInterceptionRule.siteDomain]) from the [existing] row when present.
     */
    private fun InterceptionRule.toLocal(existing: LocalInterceptionRule?): LocalInterceptionRule {
        return LocalInterceptionRule(
            id = id.toString(),
            userId = userId.toString(),
            goalId = existing?.goalId,
            appPackageName = appPackageName,
            siteDomain = existing?.siteDomain,
            dailyAllowanceMinutes = dailyLimitMinutes,
            isActive = enabled,
            createdAt = createdAt.toEpochMilli(),
            pausedForDate = pausedForDate?.toString(),
            limitType = limitType.toStorageValue(),
            repetitiveIntervalMinutes = repetitiveIntervalMinutes
        )
    }

    /**
     * Maps the domain [LimitType] to its stored string. The domain enum uses
     * mixed-case constants ([LimitType.Once]/[LimitType.Repetitive]) while the
     * Local_Store persists uppercase tokens (`ONCE`/`REPETITIVE`), so the
     * mapping is explicit rather than relying on `name`/`valueOf`.
     */
    private fun LimitType.toStorageValue(): String = when (this) {
        LimitType.Once -> "ONCE"
        LimitType.Repetitive -> "REPETITIVE"
    }

    /**
     * Maps a stored limit-type token back to the domain [LimitType]. Unknown or
     * legacy values default to [LimitType.Once] (the pre-migration behavior and
     * the column default), keeping reads resilient.
     */
    private fun String.toLimitType(): LimitType = when (uppercase()) {
        "REPETITIVE" -> LimitType.Repetitive
        else -> LimitType.Once
    }
}
