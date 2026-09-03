package com.dopashift.data.repository

import com.dopashift.data.local.dao.InterceptionRuleDao
import com.dopashift.data.local.entity.LocalInterceptionRule
import com.dopashift.domain.entity.InterceptionRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.random.Random

// Feature: screen-time-interception-engine, Property 11
/**
 * Property 11: User scoping isolation.
 *
 * Validates: Requirements 3.8
 *
 * For two distinct users A and B, a Rule owned by A is never exposed to reads
 * scoped to B (findActiveByPackage/listForUser/observeForUser exclude it), and
 * B's per-id write attempts (updateDailyLimit/pauseForToday/delete) against A's
 * Rule fail without mutating state — verified by reading A's Rule back unchanged.
 *
 * Uses an in-memory fake [InterceptionRuleDao] over a MutableList, mirroring the
 * Room query semantics (user-scoped reads, id-scoped setPausedForDate, unscoped
 * findById/deleteById on which the repository layers its ownership cross-check).
 * JUnit4 + kotlin.random.Random + kotlinx.coroutines.test.runTest per the data
 * module's conventions (Kotest is not on the classpath).
 */
class InterceptionRuleUserScopingPropertyTest {

    /**
     * In-memory fake of [InterceptionRuleDao] backed by a MutableList. Query
     * behavior matches the Room DAO: user-scoped reads filter by userId, per-id
     * pause is scoped by (id, userId), and findById/deleteById operate purely on
     * id (the repository is responsible for the ownership cross-check).
     */
    private class FakeInterceptionRuleDao : InterceptionRuleDao {
        private val rules = mutableListOf<LocalInterceptionRule>()

        override suspend fun findActiveByPackageName(
            userId: String,
            packageName: String
        ): LocalInterceptionRule? = rules.find {
            it.userId == userId && it.appPackageName == packageName && it.isActive
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override suspend fun findActiveByPackageName(packageName: String): LocalInterceptionRule? =
            rules.find { it.appPackageName == packageName && it.isActive }

        override suspend fun findActiveByUserId(userId: String): List<LocalInterceptionRule> =
            rules.filter { it.userId == userId && it.isActive }

        override suspend fun listForUser(userId: String): List<LocalInterceptionRule> =
            rules.filter { it.userId == userId }

        override fun observeForUser(userId: String): Flow<List<LocalInterceptionRule>> =
            flowOf(rules.filter { it.userId == userId })

        override suspend fun findById(id: String): LocalInterceptionRule? =
            rules.find { it.id == id }

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

    @Test
    fun `property - a Rule owned by A is never exposed to reads scoped to B`() = runTest {
        val random = Random(seed = 3080)

        repeat(120) {
            val dao = FakeInterceptionRuleDao()
            val repo = InterceptionRuleRepositoryImpl(dao)

            val userA = UUID.randomUUID()
            val userB = distinctUserFrom(userA)
            val rule = randomRule(random, owner = userA)

            assertTrue("A must be able to save its own Rule", repo.save(userA, rule).isSuccess)

            // findActiveByPackage scoped to B must not see A's Rule.
            assertNull(
                "B must not read A's Rule via package lookup",
                repo.findActiveByPackage(userB, rule.appPackageName)
            )
            // A can still read it (only relevant when the Rule is enabled).
            if (rule.enabled) {
                assertEquals(
                    "A must be able to read its own active Rule",
                    rule.id,
                    repo.findActiveByPackage(userA, rule.appPackageName)?.id
                )
            }

            // listForUser scoped to B must exclude A's Rule; A's list contains it.
            assertTrue(
                "B's list must not contain A's Rule",
                repo.listForUser(userB).none { it.id == rule.id }
            )
            assertTrue(
                "A's list must contain A's Rule",
                repo.listForUser(userA).any { it.id == rule.id }
            )

            // observeForUser scoped to B must exclude A's Rule; A's stream contains it.
            assertTrue(
                "B's stream must not contain A's Rule",
                repo.observeForUser(userB).first().none { it.id == rule.id }
            )
            assertTrue(
                "A's stream must contain A's Rule",
                repo.observeForUser(userA).first().any { it.id == rule.id }
            )
        }
    }

    @Test
    fun `property - B cannot mutate or delete A's Rule and state is preserved`() = runTest {
        val random = Random(seed = 1108)

        repeat(120) {
            val dao = FakeInterceptionRuleDao()
            val repo = InterceptionRuleRepositoryImpl(dao)

            val userA = UUID.randomUUID()
            val userB = distinctUserFrom(userA)
            val rule = randomRule(random, owner = userA)

            assertTrue(repo.save(userA, rule).isSuccess)
            val before = repo.listForUser(userA).single { it.id == rule.id }

            // B attempts updateDailyLimit with a valid-but-different limit.
            val newLimit = differentLimitFrom(rule.dailyLimitMinutes, random)
            val updateResult = repo.updateDailyLimit(userB, rule.id, newLimit)
            assertTrue("B's updateDailyLimit on A's Rule must fail", updateResult.isFailure)

            // B attempts pauseForToday.
            val pauseResult = repo.pauseForToday(userB, rule.id, LocalDate.of(2024, 6, 15))
            assertTrue("B's pauseForToday on A's Rule must fail", pauseResult.isFailure)

            // B attempts delete.
            val deleteResult = repo.delete(userB, rule.id)
            assertTrue("B's delete on A's Rule must fail", deleteResult.isFailure)

            // A's Rule must be entirely unchanged when read back as A.
            val after = repo.listForUser(userA).single { it.id == rule.id }
            assertEquals("A's Rule must be unchanged after B's failed attempts", before, after)

            // And still present / readable by A after B's failed delete.
            assertTrue(
                "A's Rule must still exist after B's failed delete",
                repo.listForUser(userA).any { it.id == rule.id }
            )

            // Sanity: A can perform the same operations successfully on its own Rule.
            assertTrue(repo.updateDailyLimit(userA, rule.id, newLimit).isSuccess)
            assertTrue(repo.pauseForToday(userA, rule.id, LocalDate.of(2024, 6, 15)).isSuccess)
            assertTrue(repo.delete(userA, rule.id).isSuccess)
            assertFalse(
                "A's Rule must be gone after A deletes it",
                repo.listForUser(userA).any { it.id == rule.id }
            )
        }
    }

    // ---- Generators -------------------------------------------------------

    private fun distinctUserFrom(other: UUID): UUID {
        var candidate = UUID.randomUUID()
        while (candidate == other) candidate = UUID.randomUUID()
        return candidate
    }

    private fun differentLimitFrom(current: Int, random: Random): Int {
        var candidate = random.nextInt(1, 481)
        while (candidate == current) candidate = random.nextInt(1, 481)
        return candidate
    }

    private fun randomRule(random: Random, owner: UUID): InterceptionRule = InterceptionRule(
        id = UUID.randomUUID(),
        userId = owner,
        appPackageName = "com.example.app${random.nextInt(0, 1000)}",
        dailyLimitMinutes = random.nextInt(1, 481),
        enabled = random.nextBoolean(),
        pausedForDate = if (random.nextBoolean()) {
            LocalDate.of(2024, 1, 1).plusDays(random.nextLong(0, 365))
        } else {
            null
        },
        createdAt = Instant.ofEpochMilli(random.nextLong(0, 1_700_000_000_000L))
    )
}
