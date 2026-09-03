package com.dopashift.ui.interception

// Feature: android-app-limit-repitative, Property 2

import androidx.test.core.app.ApplicationProvider
import com.dopashift.domain.entity.InterceptionRule
import com.dopashift.domain.entity.LimitType
import com.dopashift.domain.repository.InterceptionRuleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.UUID
import kotlin.random.Random

/**
 * Property test for **Property 2: Repetitive-interval validation (accept in
 * range, reject-and-retain)**.
 *
 * // Feature: android-app-limit-repitative, Property 2
 *
 * Validates: Requirements 1.5, 1.6
 *
 * The property (design.md): for any prior valid interval and any candidate
 * value, the value is accepted only if it is a whole number in 1–120 minutes
 * inclusive; otherwise it is rejected and the stored interval remains equal to
 * the prior valid value, with the range message exposed
 * ([RuleAuthoringUiState.intervalError] raised).
 *
 * The create-path validation gates on the single source of truth
 * [RuleAuthoringViewModel.isValidInterval]. This test exercises two
 * complementary levels:
 *
 *  1. The pure [RuleAuthoringViewModel.isValidInterval] decision directly (a
 *     candidate is valid iff the Int is in 1..120), across boundaries,
 *     negatives, and large values (`isValidInterval_*`).
 *  2. The stateful accept/reject-and-retain contract driven through the *real*
 *     [RuleAuthoringViewModel]: after establishing a known prior valid interval
 *     (via [RuleAuthoringViewModel.setLimitType] + [RuleAuthoringViewModel.setRepetitiveInterval]),
 *     applying an arbitrary candidate either updates
 *     `repetitiveIntervalMinutes` and clears `intervalError` (valid), or retains
 *     the prior interval and raises `intervalError` (invalid). Both the Int and
 *     the raw-String overloads are covered.
 *
 * The ViewModel is driven under Robolectric because its constructor builds an
 * [InstalledAppsProvider] backed by a [PackageManager]; the installed-app list
 * is irrelevant to this property. `viewModelScope` is pinned to an
 * [UnconfinedTestDispatcher] so any launched coroutine runs eagerly (the
 * interval setters themselves are synchronous `_uiState.update` calls).
 *
 * Distinctly named to avoid collision with sibling tasks (9.3, 9.5) writing in
 * the same module concurrently.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class RepetitiveIntervalValidationPropertyTest {

    private companion object {
        const val ITERATIONS = 200
        const val MIN = RuleAuthoringUiState.MIN_INTERVAL_MINUTES // 1
        const val MAX = RuleAuthoringUiState.MAX_INTERVAL_MINUTES // 120
    }

    /**
     * Minimal in-memory [InterceptionRuleRepository]. The interval property does
     * not touch persistence, so only the members the ViewModel constructor/init
     * touch need real behavior; the rest return trivial success/empty.
     */
    private class FakeRuleRepository : InterceptionRuleRepository {
        private val rules = MutableStateFlow<Map<UUID, InterceptionRule>>(emptyMap())

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
        ): Result<Unit> = Result.success(Unit)

        override suspend fun pauseForToday(
            userId: UUID,
            ruleId: UUID,
            today: LocalDate,
        ): Result<Unit> = Result.success(Unit)

        override suspend fun delete(userId: UUID, ruleId: UUID): Result<Unit> = Result.success(Unit)
    }

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
        // Switch into Repetitive mode so the interval picker applies; the last
        // valid interval is retained across setRepetitiveInterval calls.
        viewModel.setLimitType(LimitType.Repetitive)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- (1) Pure gate: the single source of truth --------------------------

    /**
     * Property: [RuleAuthoringViewModel.isValidInterval] is true iff the
     * candidate is a whole number in 1..120 (Int is inherently whole). Covers
     * the in-range and out-of-range spaces with randomized values.
     */
    @Test
    fun isValidInterval_trueIffWithinInclusiveRange() {
        repeat(ITERATIONS) { iteration ->
            val rnd = Random(iteration.toLong() * 31 + 1)
            val candidate = if (iteration % 2 == 0) {
                rnd.nextInt(MIN, MAX + 1) // 1..120 inclusive
            } else {
                if (rnd.nextBoolean()) rnd.nextInt(Int.MIN_VALUE / 2, MIN) // <= 0
                else rnd.nextInt(MAX + 1, Int.MAX_VALUE / 2) // >= 121
            }
            val expected = candidate in MIN..MAX
            assertEquals(
                "iteration $iteration: isValidInterval($candidate)",
                expected,
                RuleAuthoringViewModel.isValidInterval(candidate),
            )
        }
    }

    /** Boundary cases: 0 rejected, 1 accepted, 120 accepted, 121 rejected. */
    @Test
    fun isValidInterval_boundaries() {
        assertFalse(RuleAuthoringViewModel.isValidInterval(0))
        assertTrue(RuleAuthoringViewModel.isValidInterval(1))
        assertTrue(RuleAuthoringViewModel.isValidInterval(120))
        assertFalse(RuleAuthoringViewModel.isValidInterval(121))
        assertFalse(RuleAuthoringViewModel.isValidInterval(-1))
        assertFalse(RuleAuthoringViewModel.isValidInterval(Int.MIN_VALUE))
        assertFalse(RuleAuthoringViewModel.isValidInterval(Int.MAX_VALUE))
    }

    // --- (2) Stateful accept-in-range / reject-and-retain (Int overload) -----

    /**
     * Property: starting from a prior valid interval, applying a candidate via
     * [RuleAuthoringViewModel.setRepetitiveInterval] leaves
     * `repetitiveIntervalMinutes` equal to the candidate when it is valid (and
     * clears `intervalError`), and equal to the PRIOR valid value when it is
     * invalid (and raises `intervalError`, exposing the range message).
     */
    @Test
    fun setInterval_acceptInRange_rejectAndRetainOtherwise() {
        repeat(ITERATIONS) { iteration ->
            val rnd = Random(iteration.toLong() * 97 + 3)

            // Establish a known prior VALID interval.
            val prior = rnd.nextInt(MIN, MAX + 1)
            viewModel.setRepetitiveInterval(prior)
            assertEquals(
                "iteration $iteration: prior valid interval applied",
                prior,
                viewModel.uiState.value.repetitiveIntervalMinutes,
            )
            assertFalse(
                "iteration $iteration: valid prior clears intervalError",
                viewModel.uiState.value.intervalError,
            )

            // Now apply a candidate: valid or invalid.
            val valid = rnd.nextBoolean()
            val candidate = if (valid) {
                rnd.nextInt(MIN, MAX + 1)
            } else {
                if (rnd.nextBoolean()) rnd.nextInt(-1000, MIN) else rnd.nextInt(MAX + 1, 100_000)
            }
            viewModel.setRepetitiveInterval(candidate)

            val state = viewModel.uiState.value
            if (candidate in MIN..MAX) {
                assertEquals(
                    "iteration $iteration: valid candidate $candidate accepted",
                    candidate, state.repetitiveIntervalMinutes,
                )
                assertFalse(
                    "iteration $iteration: valid candidate clears intervalError",
                    state.intervalError,
                )
            } else {
                assertEquals(
                    "iteration $iteration: invalid candidate $candidate retains prior $prior",
                    prior, state.repetitiveIntervalMinutes,
                )
                assertTrue(
                    "iteration $iteration: invalid candidate raises intervalError (range message)",
                    state.intervalError,
                )
            }
        }
    }

    // --- (3) Stateful reject-and-retain via raw-String overload -------------

    /**
     * Property (raw-string overload): non-integer / non-whole-minute entries are
     * rejected exactly like out-of-range values — the prior valid interval is
     * retained and `intervalError` is raised.
     */
    @Test
    fun setInterval_rawString_nonIntegerRejectedAndRetained() {
        val nonIntegers = listOf("", " ", "abc", "3.5", "12x", "1e2", "-", "min", "12,5", "60.0")
        repeat(ITERATIONS) { iteration ->
            val rnd = Random(iteration.toLong() * 53 + 11)

            val prior = rnd.nextInt(MIN, MAX + 1)
            viewModel.setRepetitiveInterval(prior)
            assertEquals(prior, viewModel.uiState.value.repetitiveIntervalMinutes)

            val raw = nonIntegers[rnd.nextInt(nonIntegers.size)]
            viewModel.setRepetitiveInterval(raw)

            val state = viewModel.uiState.value
            assertEquals(
                "iteration $iteration: raw '$raw' retains prior $prior",
                prior, state.repetitiveIntervalMinutes,
            )
            assertTrue(
                "iteration $iteration: raw '$raw' raises intervalError",
                state.intervalError,
            )
        }
    }

    /**
     * Property (raw-string overload, integer strings): a raw integer string is
     * accepted iff it parses to a whole number in 1..120; otherwise the prior
     * valid interval is retained and `intervalError` is raised.
     */
    @Test
    fun setInterval_rawString_integerAcceptedInRangeElseRetained() {
        repeat(ITERATIONS) { iteration ->
            val rnd = Random(iteration.toLong() * 71 + 7)

            val prior = rnd.nextInt(MIN, MAX + 1)
            viewModel.setRepetitiveInterval(prior)

            val valid = rnd.nextBoolean()
            val candidate = if (valid) {
                rnd.nextInt(MIN, MAX + 1)
            } else {
                if (rnd.nextBoolean()) rnd.nextInt(-1000, MIN) else rnd.nextInt(MAX + 1, 100_000)
            }
            // Feed as a raw string, optionally with surrounding whitespace the
            // overload is expected to trim.
            val raw = if (rnd.nextBoolean()) candidate.toString() else "  $candidate  "
            viewModel.setRepetitiveInterval(raw)

            val state = viewModel.uiState.value
            if (candidate in MIN..MAX) {
                assertEquals(
                    "iteration $iteration: raw '$raw' accepted",
                    candidate, state.repetitiveIntervalMinutes,
                )
                assertFalse(
                    "iteration $iteration: raw '$raw' clears intervalError",
                    state.intervalError,
                )
            } else {
                assertEquals(
                    "iteration $iteration: raw '$raw' retains prior $prior",
                    prior, state.repetitiveIntervalMinutes,
                )
                assertTrue(
                    "iteration $iteration: raw '$raw' raises intervalError",
                    state.intervalError,
                )
            }
        }
    }
}
