package com.dopashift.ui.interception

// Feature: screen-time-interception-engine, Property 6

import androidx.test.core.app.ApplicationProvider
import com.dopashift.domain.entity.InterceptionRule
import com.dopashift.domain.repository.InterceptionRuleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.random.Random

/**
 * Property test for **Property 6: Daily-limit validation (accept in range,
 * reject-and-retain otherwise)** — covering BOTH the create path
 * ([RuleAuthoringViewModel.setDailyLimit]) and the edit path
 * ([RuleAuthoringViewModel.editRuleLimit]).
 *
 * // Feature: screen-time-interception-engine, Property 6
 *
 * Validates: Requirements 2.6, 2.7, 3.4
 *
 * The property: for any prior valid daily limit and any candidate value, on
 * either path the value is accepted only if it is a whole number in 1..480
 * minutes; otherwise it is rejected and the stored limit remains equal to the
 * prior valid value (reject-and-retain).
 *
 * Both the create path (`setDailyLimit`) and the edit path (`editRuleLimit`)
 * gate on the single source of truth [RuleAuthoringViewModel.isValidLimit].
 * This test exercises three complementary levels:
 *
 *  1. The pure [RuleAuthoringViewModel.isValidLimit] decision directly — the
 *     shared gate both paths use — across boundaries, negatives, and large
 *     values (`isValidLimit_*`).
 *  2. The create-path state contract driven through the *real*
 *     [RuleAuthoringViewModel]: after selecting an app (limit seeded to 30), a
 *     valid `setDailyLimit` updates `perAppLimits[pkg]` and clears the error;
 *     an invalid value RETAINS the prior `perAppLimits[pkg]` and raises
 *     `limitError` for that package.
 *  3. The edit-path state contract driven through the *real* ViewModel with an
 *     in-memory fake [InterceptionRuleRepository]: a valid `editRuleLimit`
 *     persists via `updateDailyLimit` (repo value changes); an invalid value is
 *     rejected without calling the repo (prior persisted value retained) and
 *     raises `editLimitError` for that rule id.
 *
 * The ViewModel is driven under Robolectric because its constructor builds an
 * [InstalledAppsProvider] backed by a [PackageManager]; the app list itself is
 * irrelevant to this property (selection is driven explicitly via
 * `toggleAppSelection`). `viewModelScope` is pinned to an
 * [UnconfinedTestDispatcher] so the edit-path coroutine runs eagerly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DailyLimitValidationPropertyTest {

    private companion object {
        const val ITERATIONS = 200
        const val MIN = RuleAuthoringUiState.MIN_LIMIT_MINUTES // 1
        const val MAX = RuleAuthoringUiState.MAX_LIMIT_MINUTES // 480
    }

    /**
     * Minimal in-memory [InterceptionRuleRepository]. Keeps a single-user store
     * of rules keyed by id; [updateDailyLimit] mutates the stored limit and the
     * observed Flow re-emits. Only the members the ViewModel touches are wired.
     */
    private class FakeRuleRepository : InterceptionRuleRepository {
        private val rules = MutableStateFlow<Map<UUID, InterceptionRule>>(emptyMap())

        /** Number of times [updateDailyLimit] was actually invoked. */
        var updateCallCount: Int = 0
            private set

        fun seed(rule: InterceptionRule) {
            rules.update { it + (rule.id to rule) }
        }

        fun current(ruleId: UUID): InterceptionRule? = rules.value[ruleId]

        override suspend fun save(userId: UUID, rule: InterceptionRule): Result<Unit> {
            rules.update { it + (rule.id to rule) }
            return Result.success(Unit)
        }

        override suspend fun findActiveByPackage(userId: UUID, pkg: String): InterceptionRule? =
            rules.value.values.firstOrNull { it.appPackageName == pkg && it.enabled }

        override suspend fun listForUser(userId: UUID): List<InterceptionRule> =
            rules.value.values.filter { it.userId == userId }

        override fun observeForUser(userId: UUID): Flow<List<InterceptionRule>> =
            rules.map { map -> map.values.filter { r -> r.userId == userId } }

        override suspend fun updateDailyLimit(
            userId: UUID,
            ruleId: UUID,
            minutes: Int,
        ): Result<Unit> {
            updateCallCount++
            val existing = rules.value[ruleId] ?: return Result.failure(
                IllegalStateException("no such rule")
            )
            if (existing.userId != userId) return Result.failure(IllegalStateException("cross-user"))
            rules.update { it + (ruleId to existing.copy(dailyLimitMinutes = minutes)) }
            return Result.success(Unit)
        }

        override suspend fun pauseForToday(
            userId: UUID,
            ruleId: UUID,
            today: LocalDate,
        ): Result<Unit> = Result.success(Unit)

        override suspend fun delete(userId: UUID, ruleId: UUID): Result<Unit> {
            rules.update { it - ruleId }
            return Result.success(Unit)
        }
    }

    // The placeholder user id the ViewModel uses internally.
    private val userId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")

    private lateinit var repo: FakeRuleRepository
    private lateinit var provider: InstalledAppsProvider
    private lateinit var viewModel: RuleAuthoringViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        provider = InstalledAppsProvider(context)
        repo = FakeRuleRepository()
        viewModel = RuleAuthoringViewModel(provider, repo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- (1) Pure gate: the single source of truth both paths use -----------

    /**
     * Property: [RuleAuthoringViewModel.isValidLimit] is true iff the candidate
     * is a whole number in 1..480 (Int is inherently whole). Covers the
     * in-range space and the out-of-range space with randomized values.
     */
    @Test
    fun isValidLimit_trueIffWithinInclusiveRange() {
        repeat(ITERATIONS) { iteration ->
            val rnd = Random(iteration.toLong() * 31 + 1)
            // Half in-range, half out-of-range for balanced coverage.
            val candidate = if (iteration % 2 == 0) {
                rnd.nextInt(MIN, MAX + 1) // 1..480 inclusive
            } else {
                // Out of range on either side (or far away).
                if (rnd.nextBoolean()) rnd.nextInt(Int.MIN_VALUE / 2, MIN) // <= 0
                else rnd.nextInt(MAX + 1, Int.MAX_VALUE / 2) // >= 481
            }
            val expected = candidate in MIN..MAX
            assertEquals(
                "iteration $iteration: isValidLimit($candidate)",
                expected,
                RuleAuthoringViewModel.isValidLimit(candidate),
            )
        }
    }

    /** Boundary cases: 0 rejected, 1 accepted, 480 accepted, 481 rejected. */
    @Test
    fun isValidLimit_boundaries() {
        assertFalse(RuleAuthoringViewModel.isValidLimit(0))
        assertTrue(RuleAuthoringViewModel.isValidLimit(1))
        assertTrue(RuleAuthoringViewModel.isValidLimit(480))
        assertFalse(RuleAuthoringViewModel.isValidLimit(481))
        assertFalse(RuleAuthoringViewModel.isValidLimit(-1))
        assertFalse(RuleAuthoringViewModel.isValidLimit(Int.MIN_VALUE))
        assertFalse(RuleAuthoringViewModel.isValidLimit(Int.MAX_VALUE))
    }

    // --- (2) CREATE path: accept-in-range / reject-and-retain ---------------

    /**
     * Property (create path): starting from a prior valid limit, applying a
     * candidate via [RuleAuthoringViewModel.setDailyLimit] leaves
     * `perAppLimits[pkg]` equal to the candidate when it is valid (and clears
     * the error), and equal to the PRIOR valid value when it is invalid (and
     * raises `limitError` for the package).
     */
    @Test
    fun createPath_acceptInRange_rejectAndRetainOtherwise() {
        val pkg = "com.example.distraction"
        repeat(ITERATIONS) { iteration ->
            val rnd = Random(iteration.toLong() * 97 + 3)

            // Fresh selection each iteration => seeds the default (30) limit.
            viewModel.setSelection(emptySet())
            viewModel.toggleAppSelection(pkg)
            assertEquals(
                "iteration $iteration: selection seeds default limit",
                RuleAuthoringUiState.DEFAULT_LIMIT_MINUTES,
                viewModel.uiState.value.perAppLimits[pkg],
            )

            // Establish a known prior VALID value.
            val prior = rnd.nextInt(MIN, MAX + 1)
            viewModel.setDailyLimit(pkg, prior)
            assertEquals(
                "iteration $iteration: prior valid value applied",
                prior,
                viewModel.uiState.value.perAppLimits[pkg],
            )
            assertFalse(viewModel.uiState.value.limitError)

            // Now apply a candidate: valid or invalid.
            val valid = rnd.nextBoolean()
            val candidate = if (valid) {
                rnd.nextInt(MIN, MAX + 1)
            } else {
                if (rnd.nextBoolean()) rnd.nextInt(-1000, MIN) else rnd.nextInt(MAX + 1, 100_000)
            }
            viewModel.setDailyLimit(pkg, candidate)

            val state = viewModel.uiState.value
            if (candidate in MIN..MAX) {
                assertEquals(
                    "iteration $iteration: valid candidate $candidate accepted",
                    candidate, state.perAppLimits[pkg],
                )
                assertFalse(
                    "iteration $iteration: valid candidate clears limitError",
                    state.limitError,
                )
                assertNull(state.limitErrorPackage)
            } else {
                assertEquals(
                    "iteration $iteration: invalid candidate $candidate retains prior $prior",
                    prior, state.perAppLimits[pkg],
                )
                assertTrue(
                    "iteration $iteration: invalid candidate raises limitError",
                    state.limitError,
                )
                assertEquals(pkg, state.limitErrorPackage)
            }
        }
    }

    /**
     * Property (create path, raw-string overload): non-integer / non-whole-minute
     * entries are rejected exactly like out-of-range values — prior value
     * retained, `limitError` raised.
     */
    @Test
    fun createPath_rawString_nonIntegerRejectedAndRetained() {
        val pkg = "com.example.social"
        val nonIntegers = listOf("", " ", "abc", "3.5", "12x", "1e2", "-", "min", "12,5")
        repeat(ITERATIONS) { iteration ->
            val rnd = Random(iteration.toLong() * 53 + 11)
            viewModel.setSelection(emptySet())
            viewModel.toggleAppSelection(pkg)
            val prior = rnd.nextInt(MIN, MAX + 1)
            viewModel.setDailyLimit(pkg, prior)

            val raw = nonIntegers[rnd.nextInt(nonIntegers.size)]
            viewModel.setDailyLimit(pkg, raw)

            val state = viewModel.uiState.value
            assertEquals(
                "iteration $iteration: raw '$raw' retains prior $prior",
                prior, state.perAppLimits[pkg],
            )
            assertTrue(
                "iteration $iteration: raw '$raw' raises limitError",
                state.limitError,
            )
            assertEquals(pkg, state.limitErrorPackage)
        }
    }

    // --- (3) EDIT path: accept-in-range / reject-and-retain -----------------

    /**
     * Property (edit path): starting from a persisted valid limit, editing via
     * [RuleAuthoringViewModel.editRuleLimit] persists the new value through the
     * repo when the candidate is valid; when invalid, the repo is NEVER called,
     * the persisted value is retained, and `editLimitError` is raised for the
     * rule id.
     */
    @Test
    fun editPath_acceptInRange_rejectAndRetainOtherwise() {
        repeat(ITERATIONS) { iteration ->
            val rnd = Random(iteration.toLong() * 89 + 17)

            // Seed a persisted rule with a known valid prior limit.
            val prior = rnd.nextInt(MIN, MAX + 1)
            val ruleId = UUID.randomUUID()
            val rule = InterceptionRule(
                id = ruleId,
                userId = userId,
                appPackageName = "com.example.pkg$iteration",
                dailyLimitMinutes = prior,
                enabled = true,
                pausedForDate = null,
                createdAt = Instant.EPOCH,
            )
            repo.seed(rule)
            val callsBefore = repo.updateCallCount

            val valid = rnd.nextBoolean()
            val candidate = if (valid) {
                rnd.nextInt(MIN, MAX + 1)
            } else {
                if (rnd.nextBoolean()) rnd.nextInt(-1000, MIN) else rnd.nextInt(MAX + 1, 100_000)
            }

            viewModel.editRuleLimit(ruleId, candidate)

            val state = viewModel.uiState.value
            if (candidate in MIN..MAX) {
                assertEquals(
                    "iteration $iteration: valid edit $candidate persists",
                    candidate, repo.current(ruleId)?.dailyLimitMinutes,
                )
                assertEquals(
                    "iteration $iteration: valid edit calls repo exactly once",
                    callsBefore + 1, repo.updateCallCount,
                )
                assertFalse(
                    "iteration $iteration: valid edit clears editLimitError",
                    state.editLimitError,
                )
            } else {
                assertEquals(
                    "iteration $iteration: invalid edit $candidate retains prior $prior",
                    prior, repo.current(ruleId)?.dailyLimitMinutes,
                )
                assertEquals(
                    "iteration $iteration: invalid edit never calls repo",
                    callsBefore, repo.updateCallCount,
                )
                assertTrue(
                    "iteration $iteration: invalid edit raises editLimitError",
                    state.editLimitError,
                )
                assertEquals(ruleId, state.editErrorRuleId)
            }
        }
    }

    /**
     * Property (edit path, raw-string overload): non-integer entries are
     * rejected without calling the repo — persisted value retained,
     * `editLimitError` raised for the rule id.
     */
    @Test
    fun editPath_rawString_nonIntegerRejectedAndRetained() {
        val nonIntegers = listOf("", "   ", "xyz", "7.25", "9m", "0x10", "+", "12 34")
        repeat(ITERATIONS) { iteration ->
            val rnd = Random(iteration.toLong() * 41 + 23)
            val prior = rnd.nextInt(MIN, MAX + 1)
            val ruleId = UUID.randomUUID()
            repo.seed(
                InterceptionRule(
                    id = ruleId,
                    userId = userId,
                    appPackageName = "com.example.raw$iteration",
                    dailyLimitMinutes = prior,
                    enabled = true,
                    pausedForDate = null,
                    createdAt = Instant.EPOCH,
                ),
            )
            val callsBefore = repo.updateCallCount

            val raw = nonIntegers[rnd.nextInt(nonIntegers.size)]
            viewModel.editRuleLimit(ruleId, raw)

            val state = viewModel.uiState.value
            assertEquals(
                "iteration $iteration: raw '$raw' retains persisted prior $prior",
                prior, repo.current(ruleId)?.dailyLimitMinutes,
            )
            assertEquals(
                "iteration $iteration: raw '$raw' never calls repo",
                callsBefore, repo.updateCallCount,
            )
            assertTrue(
                "iteration $iteration: raw '$raw' raises editLimitError",
                state.editLimitError,
            )
            assertEquals(ruleId, state.editErrorRuleId)
        }
    }
}
