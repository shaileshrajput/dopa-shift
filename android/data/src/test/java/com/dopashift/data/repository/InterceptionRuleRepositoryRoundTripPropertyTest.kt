package com.dopashift.data.repository

import com.dopashift.data.local.dao.InterceptionRuleDao
import com.dopashift.data.local.entity.LocalInterceptionRule
import com.dopashift.domain.entity.InterceptionRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.random.Random

/**
 * Feature: screen-time-interception-engine, Property 8: Rule persistence round-trip.
 *
 * For any valid [InterceptionRule], saving it via [InterceptionRuleRepositoryImpl]
 * and then reloading it (via list/find) returns an equivalent rule that preserves
 * package, daily limit, enabled/paused state, and created_at through the domain ↔
 * Room mapping. Editing the daily limit persists the new value.
 *
 * Validates: Requirements 3.1, 3.2, 3.3.
 *
 * Uses JUnit4 + kotlin.random.Random + kotlinx.coroutines.test.runTest, matching
 * the data module's test conventions (Kotest is not on the data module classpath).
 */
class InterceptionRuleRepositoryRoundTripPropertyTest {

    private companion object {
        const val ITERATIONS = 200
        val PACKAGE_SEGMENTS = listOf(
            "com", "org", "net", "example", "app", "social", "video",
            "game", "news", "chat", "foo", "bar", "baz", "player", "feed"
        )
    }

    /**
     * Generates a valid domain [InterceptionRule] with random field values inside
     * their permitted ranges. [createdAt] is truncated to millisecond precision
     * because the Room mapping round-trips it through epoch millis.
     */
    private fun randomRule(rnd: Random, userId: UUID): InterceptionRule {
        val segmentCount = rnd.nextInt(2, 5)
        val pkg = (0 until segmentCount)
            .joinToString(".") { PACKAGE_SEGMENTS[rnd.nextInt(PACKAGE_SEGMENTS.size)] }
        val pausedForDate: LocalDate? =
            if (rnd.nextBoolean()) {
                LocalDate.ofEpochDay(rnd.nextLong(0, 30_000)) // ~1970..2052
            } else {
                null
            }
        val createdAtMillis = rnd.nextLong(0, System.currentTimeMillis() + 1)
        return InterceptionRule(
            id = UUID.randomUUID(),
            userId = userId,
            appPackageName = pkg,
            dailyLimitMinutes = rnd.nextInt(1, 481), // 1..480 inclusive
            enabled = rnd.nextBoolean(),
            pausedForDate = pausedForDate,
            createdAt = Instant.ofEpochMilli(createdAtMillis)
        )
    }

    private fun assertRulesEqual(expected: InterceptionRule, actual: InterceptionRule) {
        assertEquals("id preserved", expected.id, actual.id)
        assertEquals("userId preserved", expected.userId, actual.userId)
        assertEquals("appPackageName preserved", expected.appPackageName, actual.appPackageName)
        assertEquals(
            "dailyLimitMinutes preserved",
            expected.dailyLimitMinutes,
            actual.dailyLimitMinutes
        )
        assertEquals("enabled preserved", expected.enabled, actual.enabled)
        assertEquals("pausedForDate preserved", expected.pausedForDate, actual.pausedForDate)
        assertEquals("createdAt preserved", expected.createdAt, actual.createdAt)
    }

    @Test
    fun `saved rule round-trips through listForUser preserving all fields`() = runTest {
        val rnd = Random(0xDADA_5417)
        repeat(ITERATIONS) {
            val dao = FakeInterceptionRuleDao()
            val repository = InterceptionRuleRepositoryImpl(dao)
            val userId = UUID.randomUUID()
            val rule = randomRule(rnd, userId)

            val saveResult = repository.save(userId, rule)
            assertEquals("save succeeds", true, saveResult.isSuccess)

            val loaded = repository.listForUser(userId)
            assertEquals("exactly one rule persisted", 1, loaded.size)
            assertRulesEqual(rule, loaded.single())
        }
    }

    @Test
    fun `enabled rule round-trips through findActiveByPackage preserving all fields`() = runTest {
        val rnd = Random(0xF00D_B33F)
        repeat(ITERATIONS) {
            val dao = FakeInterceptionRuleDao()
            val repository = InterceptionRuleRepositoryImpl(dao)
            val userId = UUID.randomUUID()
            // findActiveByPackage only returns enabled (isActive) rules, so force enabled.
            val rule = randomRule(rnd, userId).copy(enabled = true)

            repository.save(userId, rule).getOrThrow()

            val found = repository.findActiveByPackage(userId, rule.appPackageName)
            assertNotNull("active rule is found by package", found)
            assertRulesEqual(rule, found!!)
        }
    }

    @Test
    fun `updateDailyLimit persists the new value on round-trip`() = runTest {
        val rnd = Random(0xBEEF_CAFE)
        repeat(ITERATIONS) {
            val dao = FakeInterceptionRuleDao()
            val repository = InterceptionRuleRepositoryImpl(dao)
            val userId = UUID.randomUUID()
            val rule = randomRule(rnd, userId)
            repository.save(userId, rule).getOrThrow()

            val newLimit = rnd.nextInt(1, 481)
            val updateResult = repository.updateDailyLimit(userId, rule.id, newLimit)
            assertEquals("updateDailyLimit succeeds", true, updateResult.isSuccess)

            val reloaded = repository.listForUser(userId).single()
            assertEquals("new daily limit persisted", newLimit, reloaded.dailyLimitMinutes)
            // Fields other than the limit must be untouched by the edit.
            assertEquals("id unchanged by edit", rule.id, reloaded.id)
            assertEquals("package unchanged by edit", rule.appPackageName, reloaded.appPackageName)
            assertEquals("enabled unchanged by edit", rule.enabled, reloaded.enabled)
            assertEquals(
                "pausedForDate unchanged by edit",
                rule.pausedForDate,
                reloaded.pausedForDate
            )
            assertEquals("createdAt unchanged by edit", rule.createdAt, reloaded.createdAt)
        }
    }
}

/**
 * In-memory fake of [InterceptionRuleDao] backed by a [MutableList], implementing
 * every DAO method with the same semantics the real Room queries provide (user
 * scoping, active filtering, upsert-replace-by-id). Used to exercise the
 * repository's mapping and round-trip behavior without a real database.
 */
private class FakeInterceptionRuleDao : InterceptionRuleDao {

    private val rules = MutableStateFlow<List<LocalInterceptionRule>>(emptyList())

    private fun snapshot(): MutableList<LocalInterceptionRule> = rules.value.toMutableList()

    override suspend fun findActiveByPackageName(
        userId: String,
        packageName: String
    ): LocalInterceptionRule? {
        return rules.value.firstOrNull {
            it.userId == userId && it.appPackageName == packageName && it.isActive
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override suspend fun findActiveByPackageName(packageName: String): LocalInterceptionRule? {
        return rules.value.firstOrNull { it.appPackageName == packageName && it.isActive }
    }

    override suspend fun findActiveByUserId(userId: String): List<LocalInterceptionRule> {
        return rules.value.filter { it.userId == userId && it.isActive }
    }

    override suspend fun listForUser(userId: String): List<LocalInterceptionRule> {
        return rules.value.filter { it.userId == userId }
    }

    override fun observeForUser(userId: String): Flow<List<LocalInterceptionRule>> {
        return rules.map { list -> list.filter { it.userId == userId } }
    }

    override suspend fun findById(id: String): LocalInterceptionRule? {
        return rules.value.firstOrNull { it.id == id }
    }

    override suspend fun setPausedForDate(
        userId: String,
        id: String,
        pausedForDate: String?
    ): Int {
        val current = snapshot()
        var affected = 0
        for (i in current.indices) {
            val row = current[i]
            if (row.id == id && row.userId == userId) {
                current[i] = row.copy(pausedForDate = pausedForDate)
                affected++
            }
        }
        rules.value = current
        return affected
    }

    override suspend fun upsert(rule: LocalInterceptionRule) {
        val current = snapshot()
        current.removeAll { it.id == rule.id }
        current.add(rule)
        rules.value = current
    }

    override suspend fun deleteById(id: String) {
        rules.value = rules.value.filterNot { it.id == id }
    }
}
