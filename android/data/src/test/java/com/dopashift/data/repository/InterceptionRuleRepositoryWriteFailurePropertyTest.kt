package com.dopashift.data.repository

import com.dopashift.data.local.dao.InterceptionRuleDao
import com.dopashift.data.local.entity.LocalInterceptionRule
import com.dopashift.domain.entity.InterceptionRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.random.Random

/**
 * Property 12: Local-store write failures preserve prior state.
 *
 * Feature: screen-time-interception-engine, Property 12
 *
 * For any Rule write whose underlying store operation fails, the prior persisted
 * state is preserved and a failure result (surfacing an error indication) is
 * returned.
 *
 * Validates: Requirements 3.9, 6.1
 *
 * Strategy: a fault-injecting fake [InterceptionRuleDao] is seeded with a single
 * pre-existing persisted rule. On each iteration a randomly chosen write
 * operation (save / updateDailyLimit / pauseForToday / delete) is exercised while
 * the corresponding DAO write is configured to throw. The fake preserves its
 * in-memory state as it was before the failed call (it throws before mutating).
 * We then assert (a) the repository returned [Result.failure], and (b) reading
 * the previously persisted rule back returns the original rule with every field
 * intact.
 */
class InterceptionRuleRepositoryWriteFailurePropertyTest {

    private enum class WriteOp { SAVE, UPDATE_LIMIT, PAUSE_TODAY, DELETE }

    @Test
    fun `write failures preserve prior persisted state`() = runTest {
        val random = Random(0xC0FFEE)

        repeat(200) { iteration ->
            // --- Generate a random pre-existing persisted rule for a single user. ---
            val userId = UUID.randomUUID()
            val ruleId = UUID.randomUUID()
            val originalLocal = randomLocalRule(random, ruleId, userId)

            val dao = FaultInjectingInterceptionRuleDao()
            dao.seed(originalLocal)
            val repository = InterceptionRuleRepositoryImpl(dao)

            // Snapshot the persisted state before the failing write.
            val before = dao.snapshot()

            // --- Randomly choose which write operation fails this iteration. ---
            val op = WriteOp.values()[random.nextInt(WriteOp.values().size)]
            dao.failWrites = true

            val result: Result<Unit> = when (op) {
                WriteOp.SAVE -> {
                    // A save that mutates an existing rule (new limit) — the domain
                    // rule stays owned by the same user so the write is reached.
                    val mutated = originalLocal.toDomain().copy(
                        dailyLimitMinutes = boundedLimit(originalLocal.dailyAllowanceMinutes + 7),
                        enabled = !originalLocal.isActive
                    )
                    repository.save(userId, mutated)
                }
                WriteOp.UPDATE_LIMIT ->
                    repository.updateDailyLimit(userId, ruleId, boundedLimit(originalLocal.dailyAllowanceMinutes + 13))
                WriteOp.PAUSE_TODAY ->
                    repository.pauseForToday(userId, ruleId, LocalDate.of(2024, 6, 15))
                WriteOp.DELETE ->
                    repository.delete(userId, ruleId)
            }

            // (a) The operation surfaces a failure result.
            assertTrue(
                "iteration=$iteration op=$op expected Result.failure",
                result.isFailure
            )

            // (b) The previously persisted state is unchanged.
            dao.failWrites = false
            val after = dao.snapshot()
            assertEquals(
                "iteration=$iteration op=$op persisted store size changed",
                before.size,
                after.size
            )

            val reloaded = repository.findActiveByPackage(userId, originalLocal.appPackageName!!)
            if (originalLocal.isActive) {
                assertNotNull(
                    "iteration=$iteration op=$op original active rule should still be readable",
                    reloaded
                )
                assertEquals(ruleId, reloaded!!.id)
                assertEquals(userId, reloaded.userId)
                assertEquals(originalLocal.appPackageName, reloaded.appPackageName)
                assertEquals(originalLocal.dailyAllowanceMinutes, reloaded.dailyLimitMinutes)
                assertEquals(originalLocal.isActive, reloaded.enabled)
                assertEquals(
                    originalLocal.pausedForDate?.let { LocalDate.parse(it) },
                    reloaded.pausedForDate
                )
                assertEquals(Instant.ofEpochMilli(originalLocal.createdAt), reloaded.createdAt)
            }

            // Full-fidelity check against the raw stored row regardless of active state.
            val storedRow = dao.findById(ruleId.toString())
            assertEquals(
                "iteration=$iteration op=$op persisted row mutated",
                originalLocal,
                storedRow
            )
        }
    }

    private fun boundedLimit(candidate: Int): Int = candidate.coerceIn(1, 480)

    private fun randomLocalRule(
        random: Random,
        ruleId: UUID,
        userId: UUID
    ): LocalInterceptionRule {
        val paused = if (random.nextBoolean()) {
            LocalDate.of(2024, 1, 1).plusDays(random.nextInt(0, 365).toLong()).toString()
        } else {
            null
        }
        return LocalInterceptionRule(
            id = ruleId.toString(),
            userId = userId.toString(),
            goalId = if (random.nextBoolean()) UUID.randomUUID().toString() else null,
            appPackageName = "com.example.app${random.nextInt(0, 10_000)}",
            siteDomain = if (random.nextBoolean()) "example${random.nextInt(0, 100)}.com" else null,
            dailyAllowanceMinutes = random.nextInt(1, 481),
            isActive = random.nextBoolean(),
            createdAt = random.nextLong(0, 4_000_000_000_000L),
            pausedForDate = paused
        )
    }

    private fun LocalInterceptionRule.toDomain(): InterceptionRule = InterceptionRule(
        id = UUID.fromString(id),
        userId = UUID.fromString(userId),
        appPackageName = appPackageName.orEmpty(),
        dailyLimitMinutes = dailyAllowanceMinutes,
        enabled = isActive,
        pausedForDate = pausedForDate?.let { LocalDate.parse(it) },
        createdAt = Instant.ofEpochMilli(createdAt)
    )
}

/**
 * In-memory fault-injecting fake of [InterceptionRuleDao].
 *
 * When [failWrites] is true, every write operation (upsert / setPausedForDate /
 * deleteById) throws BEFORE mutating the in-memory store, so the prior state is
 * preserved exactly. Read operations always succeed, allowing a test to verify
 * the store is unchanged after a failed write.
 */
private class FaultInjectingInterceptionRuleDao : InterceptionRuleDao {

    private val rules = mutableMapOf<String, LocalInterceptionRule>()

    /** When true, all write operations throw before mutating state. */
    var failWrites: Boolean = false

    fun seed(rule: LocalInterceptionRule) {
        rules[rule.id] = rule
    }

    /** Immutable snapshot of the current persisted rows. */
    fun snapshot(): Map<String, LocalInterceptionRule> = rules.toMap()

    override suspend fun findActiveByPackageName(
        userId: String,
        packageName: String
    ): LocalInterceptionRule? = rules.values.firstOrNull {
        it.userId == userId && it.appPackageName == packageName && it.isActive
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override suspend fun findActiveByPackageName(packageName: String): LocalInterceptionRule? =
        rules.values.firstOrNull { it.appPackageName == packageName && it.isActive }

    override suspend fun findActiveByUserId(userId: String): List<LocalInterceptionRule> =
        rules.values.filter { it.userId == userId && it.isActive }

    override suspend fun listForUser(userId: String): List<LocalInterceptionRule> =
        rules.values.filter { it.userId == userId }

    override fun observeForUser(userId: String): Flow<List<LocalInterceptionRule>> =
        flowOf(rules.values.filter { it.userId == userId })

    override suspend fun findById(id: String): LocalInterceptionRule? = rules[id]

    override suspend fun setPausedForDate(userId: String, id: String, pausedForDate: String?): Int {
        if (failWrites) throw RuntimeException("injected setPausedForDate failure")
        val existing = rules[id] ?: return 0
        if (existing.userId != userId) return 0
        rules[id] = existing.copy(pausedForDate = pausedForDate)
        return 1
    }

    override suspend fun upsert(rule: LocalInterceptionRule) {
        if (failWrites) throw RuntimeException("injected upsert failure")
        rules[rule.id] = rule
    }

    override suspend fun deleteById(id: String) {
        if (failWrites) throw RuntimeException("injected deleteById failure")
        rules.remove(id)
    }
}
