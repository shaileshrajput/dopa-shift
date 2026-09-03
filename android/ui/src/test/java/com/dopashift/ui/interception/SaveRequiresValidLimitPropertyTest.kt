package com.dopashift.ui.interception

// Feature: screen-time-interception-engine, Property 7: Save requires at least one app with a valid limit

import com.dopashift.ui.interception.RuleAuthoringViewModel.Companion.canSave
import com.dopashift.ui.interception.RuleAuthoringViewModel.Companion.isValidLimit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Property 7: Save requires at least one app with a valid limit.
 *
 * For any selection set and any per-app limit map,
 * [RuleAuthoringViewModel.canSave] is TRUE **iff**:
 * 1. the selection is non-empty, AND
 * 2. every selected package has an entry in the limit map whose value is a
 *    valid daily limit (whole minutes in 1..480).
 *
 * The test drives the pure companion [canSave] directly against a reference
 * predicate over randomized scenarios:
 * - empty selection → false (regardless of the limit map);
 * - non-empty selection with every selected app validly limited → true;
 * - non-empty selection with ≥1 selected app missing a limit → false;
 * - non-empty selection with ≥1 selected app whose limit is out of range
 *   (0, negative, >480) → false.
 *
 * **Validates: Requirements 2.9, 2.10**
 *
 * Property-based test following existing module conventions (JUnit4 +
 * `kotlin.random.Random` with `repeat(N >= 100)`; Kotest is not on this
 * module's test classpath). No ViewModel instance / Android is needed.
 */
class SaveRequiresValidLimitPropertyTest {

    private val minLimit = RuleAuthoringUiState.MIN_LIMIT_MINUTES // 1
    private val maxLimit = RuleAuthoringUiState.MAX_LIMIT_MINUTES // 480

    /** Reference predicate mirroring the intended save-eligibility semantics. */
    private fun reference(selected: Set<String>, limits: Map<String, Int>): Boolean =
        selected.isNotEmpty() && selected.all { limits[it]?.let { m -> m in minLimit..maxLimit } == true }

    private fun pkg(i: Int): String = "com.pkg.app$i"

    private fun randomSelection(random: Random, allowEmpty: Boolean): Set<String> {
        val min = if (allowEmpty) 0 else 1
        val size = random.nextInt(min, 8)
        return (0 until size).map { pkg(random.nextInt(0, 20)) }.toSet()
    }

    private fun validLimit(random: Random): Int = random.nextInt(minLimit, maxLimit + 1)

    private fun invalidLimit(random: Random): Int = when (random.nextInt(4)) {
        0 -> 0
        1 -> random.nextInt(-500, 0)          // negative
        2 -> random.nextInt(maxLimit + 1, 1000) // > 480
        else -> maxLimit + 1                   // exactly 481
    }

    /**
     * General property: for arbitrary selections and limit maps (mixing valid,
     * invalid, and missing entries), [canSave] agrees with the reference
     * predicate.
     */
    @Test
    fun `property - canSave matches reference predicate over random scenarios`() {
        val random = Random(seed = 7001)
        repeat(500) {
            val selected = randomSelection(random, allowEmpty = true)
            // Build a limit map over a superset of package names; each entry is
            // randomly valid, invalid, or absent.
            val limits = buildMap {
                val universe = (0 until 20).map { pkg(it) }
                for (p in universe) {
                    when (random.nextInt(3)) {
                        0 -> put(p, validLimit(random))
                        1 -> put(p, invalidLimit(random))
                        else -> { /* leave absent */ }
                    }
                }
            }

            assertEquals(
                "canSave must match the reference predicate for selection=$selected limits=$limits",
                reference(selected, limits),
                canSave(selected, limits)
            )
        }
    }

    /**
     * Empty selection is never saveable, regardless of the limit map contents.
     */
    @Test
    fun `property - empty selection is never saveable`() {
        val random = Random(seed = 7002)
        repeat(150) {
            val limits = buildMap {
                repeat(random.nextInt(0, 10)) {
                    val p = pkg(random.nextInt(0, 20))
                    put(p, if (random.nextBoolean()) validLimit(random) else invalidLimit(random))
                }
            }
            assertFalse(
                "Empty selection must not be saveable (limits=$limits)",
                canSave(emptySet(), limits)
            )
        }
    }

    /**
     * A non-empty selection where every selected app has a valid limit is
     * saveable.
     */
    @Test
    fun `property - all selected apps validly limited is saveable`() {
        val random = Random(seed = 7003)
        repeat(300) {
            val selected = randomSelection(random, allowEmpty = false)
            val limits = selected.associateWith { validLimit(random) }
            assertTrue(
                "Selection with all valid limits must be saveable: selected=$selected limits=$limits",
                canSave(selected, limits)
            )
        }
    }

    /**
     * A non-empty selection with at least one selected app missing a limit is
     * not saveable.
     */
    @Test
    fun `property - a selected app missing a limit blocks save`() {
        val random = Random(seed = 7004)
        repeat(300) {
            val selected = randomSelection(random, allowEmpty = false)
            // Seed all with valid limits, then drop one selected app's limit.
            val limits = selected.associateWith { validLimit(random) }.toMutableMap()
            val dropped = selected.elementAt(random.nextInt(selected.size))
            limits.remove(dropped)

            assertFalse(
                "Missing limit for '$dropped' must block save: selected=$selected limits=$limits",
                canSave(selected, limits)
            )
        }
    }

    /**
     * A non-empty selection with at least one selected app whose limit is out of
     * range (0, negative, or >480) is not saveable.
     */
    @Test
    fun `property - a selected app with out-of-range limit blocks save`() {
        val random = Random(seed = 7005)
        repeat(300) {
            val selected = randomSelection(random, allowEmpty = false)
            val limits = selected.associateWith { validLimit(random) }.toMutableMap()
            val corrupted = selected.elementAt(random.nextInt(selected.size))
            limits[corrupted] = invalidLimit(random)

            assertFalse(
                "Out-of-range limit for '$corrupted' must block save: selected=$selected limits=$limits",
                canSave(selected, limits)
            )
        }
    }

    /**
     * Direct boundary assertions on [isValidLimit] (0 → false, 1 → true,
     * 480 → true, 481 → false) that anchor the range used by [canSave].
     */
    @Test
    fun `isValidLimit boundaries`() {
        assertFalse("0 minutes is invalid", isValidLimit(0))
        assertTrue("1 minute is valid", isValidLimit(1))
        assertTrue("480 minutes is valid", isValidLimit(480))
        assertFalse("481 minutes is invalid", isValidLimit(481))
    }
}
