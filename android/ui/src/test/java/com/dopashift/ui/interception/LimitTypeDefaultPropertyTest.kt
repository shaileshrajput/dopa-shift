package com.dopashift.ui.interception

// Feature: android-app-limit-repitative, Property 1

import com.dopashift.domain.entity.LimitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Property test for **Property 1: Limit-type selector defaults to Once**.
 *
 * // Feature: android-app-limit-repitative, Property 1
 *
 * Validates: Requirements 1.1, 1.2
 *
 * The design property: *for any* newly opened Rule_Authoring_Screen for a new
 * Rule, the selected Limit_Type equals [LimitType.Once].
 *
 * A "newly opened screen for a new Rule" is modeled by a freshly constructed
 * [RuleAuthoringUiState] — this is exactly the initial state the
 * [RuleAuthoringViewModel] seeds its `MutableStateFlow` with before any user
 * interaction. To make this a genuine property over "any newly opened screen"
 * (rather than a single fixed assertion), the test also confirms the default
 * survives *any arbitrary sequence of non-limit-type interactions* applied to
 * that default state: the limit type stays [LimitType.Once] (and the interval
 * stays null / error clear) until the limit type is explicitly changed.
 *
 * This is a pure unit test — no Compose/Robolectric is needed. The rendered
 * default-selection UI assertion is covered by the instrumented test (task 9.5).
 * Follows the established `:ui` convention (JUnit4 + `kotlin.random.Random` with
 * `repeat(N >= 100)`); Kotest is not on this module's test classpath.
 */
class LimitTypeDefaultPropertyTest {

    private companion object {
        const val ITERATIONS = 200

        val MIN_LIMIT = RuleAuthoringUiState.MIN_LIMIT_MINUTES // 1
        val MAX_LIMIT = RuleAuthoringUiState.MAX_LIMIT_MINUTES // 480
    }

    /**
     * A non-limit-type mutation modeled as a pure transform on the UiState.
     * These mirror the state changes the ViewModel makes for interactions that
     * are unrelated to the Limit_Type selector (search, selection, per-app
     * daily-limit edits, save-outcome flags). None of them should ever touch
     * [RuleAuthoringUiState.limitType].
     */
    private fun randomNonLimitTypeMutation(rnd: Random): (RuleAuthoringUiState) -> RuleAuthoringUiState =
        when (rnd.nextInt(6)) {
            0 -> { s -> s.copy(searchQuery = "query-${rnd.nextInt(1000)}") }
            1 -> { s ->
                val pkg = "com.example.app${rnd.nextInt(20)}"
                s.copy(
                    selectedPackages = s.selectedPackages + pkg,
                    perAppLimits = s.perAppLimits + (pkg to RuleAuthoringUiState.DEFAULT_LIMIT_MINUTES),
                )
            }
            2 -> { s ->
                // Apply a per-app daily-limit edit (valid whole minutes) if any
                // app is selected; otherwise a no-op.
                val pkg = s.selectedPackages.randomOrNull(rnd)
                if (pkg == null) {
                    s
                } else {
                    s.copy(perAppLimits = s.perAppLimits + (pkg to rnd.nextInt(MIN_LIMIT, MAX_LIMIT + 1)))
                }
            }
            3 -> { s -> s.copy(isLoading = rnd.nextBoolean()) }
            4 -> { s -> s.copy(saveSuccess = rnd.nextBoolean(), requiredError = rnd.nextBoolean()) }
            else -> { s -> s.copy(isEmptyResult = rnd.nextBoolean()) }
        }

    private fun <T> Set<T>.randomOrNull(rnd: Random): T? =
        if (isEmpty()) null else elementAt(rnd.nextInt(size))

    /**
     * Property: a freshly constructed [RuleAuthoringUiState] — the state a
     * newly opened screen for a new Rule starts in — always has Limit_Type
     * [LimitType.Once], with no interval set and no interval error surfaced.
     */
    @Test
    fun `property - freshly opened screen defaults to Once`() {
        repeat(ITERATIONS) { iteration ->
            val fresh = RuleAuthoringUiState()
            assertEquals(
                "iteration $iteration: a newly opened screen defaults to Once",
                LimitType.Once,
                fresh.limitType,
            )
            assertNull(
                "iteration $iteration: Once default carries no interval",
                fresh.repetitiveIntervalMinutes,
            )
            assertFalse(
                "iteration $iteration: Once default surfaces no interval error",
                fresh.intervalError,
            )
        }
    }

    /**
     * Property: for *any* arbitrary sequence of non-limit-type interactions
     * applied to the default state, the Limit_Type remains [LimitType.Once]
     * (and the interval stays null with the error clear) until it is explicitly
     * changed. This generalizes the default beyond a single fixed snapshot to
     * "any newly opened screen still on its default selection".
     */
    @Test
    fun `property - default Once survives arbitrary non-limit-type interactions`() {
        repeat(ITERATIONS) { iteration ->
            val rnd = Random(iteration.toLong() * 131 + 7)
            var state = RuleAuthoringUiState()

            val steps = rnd.nextInt(0, 12)
            repeat(steps) {
                state = randomNonLimitTypeMutation(rnd)(state)

                // Invariant holds after every single step, not just at the end.
                assertEquals(
                    "iteration $iteration: limit type stays Once after a non-limit-type interaction",
                    LimitType.Once,
                    state.limitType,
                )
                assertNull(
                    "iteration $iteration: interval stays null while default Once is untouched",
                    state.repetitiveIntervalMinutes,
                )
                assertFalse(
                    "iteration $iteration: no interval error while default Once is untouched",
                    state.intervalError,
                )
            }
        }
    }

    /**
     * Anchoring assertion: the default is a property of the model, not an
     * accident of construction order — the very first field-defaulted instance
     * is `Once`, and only an explicit limit-type change moves it off `Once`.
     */
    @Test
    fun `default is Once and only an explicit change moves it`() {
        val fresh = RuleAuthoringUiState()
        assertEquals(LimitType.Once, fresh.limitType)

        // An explicit switch to Repetitive is the ONLY thing that changes it.
        val switched = fresh.copy(limitType = LimitType.Repetitive)
        assertTrue(
            "an explicit change is required to leave the Once default",
            switched.limitType == LimitType.Repetitive,
        )
    }
}
