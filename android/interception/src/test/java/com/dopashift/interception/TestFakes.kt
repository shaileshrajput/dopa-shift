package com.dopashift.interception

import com.dopashift.data.local.dao.InterceptionRuleDao
import com.dopashift.data.local.dao.TelemetryDao
import com.dopashift.data.local.entity.LocalInterceptionRule
import com.dopashift.data.local.entity.LocalTelemetryEvent
import com.dopashift.domain.entity.InterceptionRule
import com.dopashift.domain.repository.InterceptionRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Shared test fakes for interception module tests.
 * Internal visibility so they're accessible across test files in this package.
 */

/**
 * In-memory fake of the domain [InterceptionRuleRepository] port used by
 * [AllowanceTracker] tests. Backed by a mutable list of domain [InterceptionRule]s
 * and scoped by `userId` exactly like the real implementation.
 *
 * A convenience [addRule] overload accepts a [LocalInterceptionRule] so existing
 * tests that build Room-shaped rules keep working with minimal edits.
 */
internal class FakeInterceptionRuleRepository : InterceptionRuleRepository {

    private val rules = MutableStateFlow<List<InterceptionRule>>(emptyList())

    /** Adds a domain rule. */
    fun addRule(rule: InterceptionRule) {
        rules.value = rules.value.filterNot { it.id == rule.id } + rule
    }

    /** Adds a rule expressed as a Room entity, mapping it to the domain shape. */
    fun addRule(local: LocalInterceptionRule) {
        addRule(local.toDomain())
    }

    /** Removes a rule by id, simulating a delete. */
    fun removeRule(id: UUID) {
        rules.value = rules.value.filterNot { it.id == id }
    }

    override suspend fun save(userId: UUID, rule: InterceptionRule): Result<Unit> {
        if (rule.userId != userId) {
            return Result.failure(IllegalArgumentException("cross-user save"))
        }
        addRule(rule)
        return Result.success(Unit)
    }

    override suspend fun findActiveByPackage(userId: UUID, pkg: String): InterceptionRule? {
        return rules.value.find {
            it.userId == userId && it.appPackageName == pkg && it.enabled
        }
    }

    override suspend fun listForUser(userId: UUID): List<InterceptionRule> {
        return rules.value.filter { it.userId == userId }
    }

    override fun observeForUser(userId: UUID): Flow<List<InterceptionRule>> {
        return rules.map { list -> list.filter { it.userId == userId } }
    }

    override suspend fun updateDailyLimit(userId: UUID, ruleId: UUID, minutes: Int): Result<Unit> {
        val existing = rules.value.find { it.id == ruleId && it.userId == userId }
            ?: return Result.failure(NoSuchElementException("rule not found"))
        return runCatching {
            addRule(existing.copy(dailyLimitMinutes = minutes))
        }
    }

    override suspend fun pauseForToday(userId: UUID, ruleId: UUID, today: LocalDate): Result<Unit> {
        val existing = rules.value.find { it.id == ruleId && it.userId == userId }
            ?: return Result.failure(NoSuchElementException("rule not found"))
        addRule(existing.copy(pausedForDate = today))
        return Result.success(Unit)
    }

    override suspend fun delete(userId: UUID, ruleId: UUID): Result<Unit> {
        val existing = rules.value.find { it.id == ruleId && it.userId == userId }
            ?: return Result.failure(NoSuchElementException("rule not found"))
        removeRule(existing.id)
        return Result.success(Unit)
    }

    private fun LocalInterceptionRule.toDomain(): InterceptionRule {
        return InterceptionRule(
            id = UUID.fromString(id),
            userId = UUID.fromString(userId),
            appPackageName = appPackageName.orEmpty(),
            dailyLimitMinutes = dailyAllowanceMinutes,
            enabled = isActive,
            pausedForDate = pausedForDate?.let { LocalDate.parse(it) },
            createdAt = Instant.ofEpochMilli(createdAt)
        )
    }
}

/**
 * In-memory fake of the domain [com.dopashift.domain.repository.InterceptActionAuditRepository]
 * port used by the overlay-action routing wiring tests. Records every appended
 * [com.dopashift.domain.entity.InterceptActionAudit] so tests can assert exactly one local entry
 * is written per executed action (Requirement 3.4) and that nothing is transmitted (there is no
 * network path — Requirement 3.5). A [failNext] flag simulates a local-store write failure so the
 * "audit failure must not block the action" behavior can be exercised.
 */
internal class FakeInterceptActionAuditRepository :
    com.dopashift.domain.repository.InterceptActionAuditRepository {

    val appended = mutableListOf<com.dopashift.domain.entity.InterceptActionAudit>()

    /** When true, the next [append] returns a failure without recording the entry. */
    var failNext: Boolean = false

    override suspend fun append(
        userId: UUID,
        audit: com.dopashift.domain.entity.InterceptActionAudit
    ): Result<Unit> {
        if (audit.userId != userId) {
            return Result.failure(IllegalArgumentException("cross-user audit append"))
        }
        if (failNext) {
            failNext = false
            return Result.failure(RuntimeException("simulated local-store write failure"))
        }
        appended.add(audit)
        return Result.success(Unit)
    }

    override suspend fun listForUser(
        userId: UUID
    ): List<com.dopashift.domain.entity.InterceptActionAudit> {
        return appended.filter { it.userId == userId }
    }
}

internal class FakeInterceptionRuleDao : InterceptionRuleDao {
    private val rules = mutableListOf<LocalInterceptionRule>()

    fun addRule(rule: LocalInterceptionRule) {
        rules.add(rule)
    }

    override suspend fun findActiveByPackageName(
        userId: String,
        packageName: String
    ): LocalInterceptionRule? {
        return rules.find {
            it.userId == userId && it.appPackageName == packageName && it.isActive
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override suspend fun findActiveByPackageName(packageName: String): LocalInterceptionRule? {
        return rules.find { it.appPackageName == packageName && it.isActive }
    }

    override suspend fun findActiveByUserId(userId: String): List<LocalInterceptionRule> {
        return rules.filter { it.userId == userId && it.isActive }
    }

    override suspend fun listForUser(userId: String): List<LocalInterceptionRule> {
        return rules.filter { it.userId == userId }
    }

    override fun observeForUser(
        userId: String
    ): kotlinx.coroutines.flow.Flow<List<LocalInterceptionRule>> {
        return kotlinx.coroutines.flow.flowOf(rules.filter { it.userId == userId })
    }

    override suspend fun findById(id: String): LocalInterceptionRule? {
        return rules.find { it.id == id }
    }

    override suspend fun setPausedForDate(
        userId: String,
        id: String,
        pausedForDate: String?
    ): Int {
        val index = rules.indexOfFirst { it.id == id && it.userId == userId }
        if (index < 0) return 0
        rules[index] = rules[index].copy(pausedForDate = pausedForDate)
        return 1
    }

    override suspend fun upsert(rule: LocalInterceptionRule) {
        rules.removeAll { it.id == rule.id }
        rules.add(rule)
    }

    override suspend fun deleteById(id: String) {
        rules.removeAll { it.id == id }
    }
}

/** Deterministic UUID from an arbitrary string label for readable test fixtures. */
internal fun testUuid(label: String): UUID =
    UUID.nameUUIDFromBytes(label.toByteArray())

/** Builds a domain [InterceptionRule] for tracker tests. */
internal fun domainRule(
    userId: UUID,
    packageName: String,
    dailyLimitMinutes: Int,
    id: UUID = testUuid(packageName),
    enabled: Boolean = true,
    pausedForDate: LocalDate? = null
): InterceptionRule = InterceptionRule(
    id = id,
    userId = userId,
    appPackageName = packageName,
    dailyLimitMinutes = dailyLimitMinutes,
    enabled = enabled,
    pausedForDate = pausedForDate,
    createdAt = Instant.ofEpochMilli(1_700_000_000_000L)
)

internal class FakeTelemetryDao : TelemetryDao {
    private val events = mutableListOf<LocalTelemetryEvent>()

    override suspend fun upsert(event: LocalTelemetryEvent) {
        events.removeAll { it.id == event.id }
        events.add(event)
    }

    override suspend fun findByDate(date: String): List<LocalTelemetryEvent> {
        return events.filter { it.date == date }
    }

    override suspend fun getTotalForegroundSeconds(date: String, packageName: String): Long? {
        val total = events
            .filter { it.date == date && it.appPackageName == packageName }
            .sumOf { it.foregroundSeconds }
        return if (total == 0L) null else total
    }

    override suspend fun deleteOlderThan(cutoffDate: String) {
        events.removeAll { it.date < cutoffDate }
    }
}
