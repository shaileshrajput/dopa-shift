package com.dopashift.data.repository

// Feature: android-app-limit-repitative, Property 3

import com.dopashift.data.local.dao.InterceptionRuleDao
import com.dopashift.data.local.entity.LocalInterceptionRule
import com.dopashift.domain.entity.InterceptionRule
import com.dopashift.domain.entity.LimitType
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.uuid
import io.kotest.property.checkAll
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

/**
 * Property-based test for [InterceptionRuleRepositoryImpl] limit-type persistence.
 *
 * **Feature: android-app-limit-repitative, Property 3: Rule persistence round-trip with limit type**
 *
 * **Validates: Requirements 1.7, 1.8**
 *
 * For any valid Rule (`Once` or `Repetitive`), saving then reloading it preserves
 * `limitType` and `repetitiveIntervalMinutes` (null for `Once`, 1..120 for
 * `Repetitive`); and a save whose store write fails leaves the prior persisted
 * state unchanged.
 *
 * This is a plain JVM unit test (data module `src/test`) using hand-written
 * in-memory [InterceptionRuleDao] fakes, so no Android emulator is required.
 * Properties are exercised with Kotest's [checkAll] engine at 200 generated cases
 * each (>= 100 per the design Testing Strategy). It is hosted in a JUnit4 test
 * class — matching the data module's discovery convention — because AGP's Android
 * unit-test task discovers JUnit tests while Kotest spec styles are not
 * auto-discovered here; `checkAll` still provides the full property-based
 * generation and shrinking.
 */
@OptIn(ExperimentalKotest::class)
class InterceptionRuleLimitTypeRoundTripPropertyTest {

    // >= 100 generated cases per property (design Testing Strategy: min 100 iterations).
    private val config = PropTestConfig(iterations = 200)

    private val packageSegments = Arb.of(
        "com", "org", "net", "example", "app", "social", "video",
        "game", "news", "chat", "foo", "bar", "baz", "player", "feed"
    )

    // 2..4 dotted package segments, e.g. "com.example.app".
    private val packageNames: Arb<String> =
        Arb.list(packageSegments, 2..4).map { it.joinToString(".") }

    // Optional pausedForDate; null half the time (ISO LocalDate round-trips as a string).
    private val pausedDates: Arb<LocalDate?> = Arb.bind(
        Arb.boolean(),
        Arb.int(0, 30_000)
    ) { present, epochDay -> if (present) LocalDate.ofEpochDay(epochDay.toLong()) else null }

    /**
     * Holder for a generated Rule paired with its owning user id (a save must have
     * `rule.userId == userId`, so the two are generated together).
     */
    private data class OwnedRule(val userId: UUID, val rule: InterceptionRule)

    /**
     * A valid domain Rule of either limit type, paired with its owner. `Once`
     * rules leave the interval null; `Repetitive` rules carry an interval in
     * 1..120. The entity `init` enforces both constraints, so every generated
     * value is a legal Rule. `createdAt` is fixed to a millisecond-aligned instant
     * because this property only asserts the limit-type fields (the parent
     * round-trip test already covers the other fields).
     */
    private val ownedRules: Arb<OwnedRule> = Arb.bind(
        Arb.uuid(),                 // userId
        Arb.uuid(),                 // rule id
        packageNames,
        Arb.int(1, 480),            // dailyLimitMinutes
        Arb.boolean()               // enabled
    ) { userId, id, pkg, dailyLimit, enabled ->
        OwnedRuleSeed(userId, id, pkg, dailyLimit, enabled)
    }.let { seeds ->
        Arb.bind(seeds, pausedDates, Arb.boolean(), Arb.int(1, 120)) { seed, paused, repetitive, interval ->
            val rule = InterceptionRule(
                id = seed.id,
                userId = seed.userId,
                appPackageName = seed.pkg,
                dailyLimitMinutes = seed.dailyLimit,
                limitType = if (repetitive) LimitType.Repetitive else LimitType.Once,
                repetitiveIntervalMinutes = if (repetitive) interval else null,
                enabled = seed.enabled,
                pausedForDate = paused,
                createdAt = Instant.ofEpochMilli(0)
            )
            OwnedRule(seed.userId, rule)
        }
    }

    private data class OwnedRuleSeed(
        val userId: UUID,
        val id: UUID,
        val pkg: String,
        val dailyLimit: Int,
        val enabled: Boolean
    )

    private fun assertLimitFieldsPreserved(expected: InterceptionRule, actual: InterceptionRule) {
        assertEquals("limitType preserved", expected.limitType, actual.limitType)
        assertEquals(
            "repetitiveIntervalMinutes preserved",
            expected.repetitiveIntervalMinutes,
            actual.repetitiveIntervalMinutes
        )
        // Sanity: the invariant that ties the two fields together must survive the round-trip.
        when (actual.limitType) {
            LimitType.Once -> assertEquals(
                "Once rule must have null interval after reload",
                null,
                actual.repetitiveIntervalMinutes
            )
            LimitType.Repetitive -> assertTrue(
                "Repetitive rule interval must be in 1..120 after reload",
                actual.repetitiveIntervalMinutes in 1..120
            )
        }
    }

    @Test
    fun `saved rule round-trips through listForUser preserving limit type and interval`() =
        runTest {
            checkAll(config, ownedRules) { (userId, rule) ->
                val dao = FakeLimitTypeInterceptionRuleDao()
                val repository = InterceptionRuleRepositoryImpl(dao)

                val saveResult = repository.save(userId, rule)
                assertTrue("save succeeds", saveResult.isSuccess)

                val loaded = repository.listForUser(userId)
                assertEquals("exactly one rule persisted", 1, loaded.size)
                assertLimitFieldsPreserved(rule, loaded.single())
            }
        }

    @Test
    fun `enabled rule round-trips through findActiveByPackage preserving limit type and interval`() =
        runTest {
            // findActiveByPackage only returns enabled rules, so force enabled.
            checkAll(config, ownedRules.map { it.copy(rule = it.rule.copy(enabled = true)) }) {
                (userId, rule) ->
                val dao = FakeLimitTypeInterceptionRuleDao()
                val repository = InterceptionRuleRepositoryImpl(dao)

                repository.save(userId, rule).getOrThrow()

                val found = repository.findActiveByPackage(userId, rule.appPackageName)
                assertNotNull("active rule is found by package", found)
                assertLimitFieldsPreserved(rule, found!!)
            }
        }

    @Test
    fun `a save whose store write fails leaves the prior persisted state unchanged`() = runTest {
        // Persist a first rule, then attempt a mutating second save that fails; assert the
        // failure surfaces AND the first rule's limit fields survive untouched (Req 1.8).
        checkAll(config, ownedRules) { (userId, firstRule) ->
            val dao = FaultInjectingLimitTypeDao()
            val repository = InterceptionRuleRepositoryImpl(dao)

            // First save succeeds and establishes the prior persisted state.
            repository.save(userId, firstRule).getOrThrow()
            val before = dao.snapshot()

            // A second save (same id, flipped limit type) is attempted while the store write
            // is configured to fail. The DAO throws before mutating, so prior state is intact.
            val mutated = if (firstRule.limitType == LimitType.Once) {
                firstRule.copy(limitType = LimitType.Repetitive, repetitiveIntervalMinutes = 42)
            } else {
                firstRule.copy(limitType = LimitType.Once, repetitiveIntervalMinutes = null)
            }

            dao.failWrites = true
            val result = repository.save(userId, mutated)
            dao.failWrites = false

            // (a) The operation surfaces a failure result.
            assertTrue("failing save returns Result.failure", result.isFailure)

            // (b) The previously persisted store is byte-for-byte unchanged.
            assertEquals("persisted store mutated by failed write", before, dao.snapshot())

            // (c) Reloading returns the ORIGINAL rule's limit fields, not the mutation.
            val reloaded = repository.listForUser(userId).single()
            assertLimitFieldsPreserved(firstRule, reloaded)
        }
    }
}

/**
 * In-memory fake of [InterceptionRuleDao] backed by a map, implementing every DAO
 * method with the same semantics the real Room queries provide (user scoping,
 * active filtering, upsert-replace-by-id). Used to exercise the repository's
 * limit-type mapping and round-trip behavior without a real database.
 */
private class FakeLimitTypeInterceptionRuleDao : InterceptionRuleDao {

    private val rules = mutableMapOf<String, LocalInterceptionRule>()

    override suspend fun findActiveByPackageName(
        userId: String,
        packageName: String
    ): LocalInterceptionRule? = rules.values.firstOrNull {
        it.userId == userId && it.appPackageName == packageName && it.isActive
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
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
        val existing = rules[id] ?: return 0
        if (existing.userId != userId) return 0
        rules[id] = existing.copy(pausedForDate = pausedForDate)
        return 1
    }

    override suspend fun upsert(rule: LocalInterceptionRule) {
        rules[rule.id] = rule
    }

    override suspend fun deleteById(id: String) {
        rules.remove(id)
    }
}

/**
 * In-memory fault-injecting fake of [InterceptionRuleDao].
 *
 * When [failWrites] is true, every write operation throws BEFORE mutating the
 * in-memory store, so the prior state is preserved exactly. Read operations always
 * succeed, allowing a test to verify the store is unchanged after a failed write.
 */
private class FaultInjectingLimitTypeDao : InterceptionRuleDao {

    private val rules = mutableMapOf<String, LocalInterceptionRule>()

    /** When true, all write operations throw before mutating state. */
    var failWrites: Boolean = false

    /** Immutable snapshot of the current persisted rows. */
    fun snapshot(): Map<String, LocalInterceptionRule> = rules.toMap()

    override suspend fun findActiveByPackageName(
        userId: String,
        packageName: String
    ): LocalInterceptionRule? = rules.values.firstOrNull {
        it.userId == userId && it.appPackageName == packageName && it.isActive
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
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
