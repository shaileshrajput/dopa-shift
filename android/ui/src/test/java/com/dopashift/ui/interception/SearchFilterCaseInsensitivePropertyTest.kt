package com.dopashift.ui.interception

// Feature: screen-time-interception-engine, Property 5: Search filters to case-insensitive label matches

import com.dopashift.ui.interception.RuleAuthoringViewModel.Companion.filterByQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Property 5: Search filters to case-insensitive label matches.
 *
 * For any installed-app list and any search query, [RuleAuthoringViewModel.filterByQuery]:
 * 1. Soundness — every app in the result has a label that *contains* the query
 *    case-insensitively.
 * 2. Completeness — every app NOT in the result does NOT contain the query
 *    case-insensitively (the filter includes exactly the matching apps, order preserved).
 * 3. Case-insensitivity — filtering with the query rendered in different cases
 *    (lowercase / UPPERCASE / MiXeD) yields the same result set.
 * 4. Blank/empty query — a blank or empty query returns the full list unchanged
 *    (identity, order preserved).
 *
 * **Validates: Requirements 2.2**
 *
 * Property-based test following existing module conventions (JUnit4 + `kotlin.random.Random`
 * with `repeat(N >= 100)`; Kotest is not on this module's test classpath). The pure companion
 * function [filterByQuery] is driven directly — no ViewModel instance or Android is needed.
 */
class SearchFilterCaseInsensitivePropertyTest {

    private val labelAlphabet =
        ('a'..'z') + ('A'..'Z') + ('0'..'9') + listOf(' ', '-', '_', '.')

    /** Builds an [InstalledApp] with a random-cased, varying-length label. */
    private fun randomApp(random: Random, index: Int): InstalledApp {
        val len = random.nextInt(1, 16)
        val label = buildString {
            repeat(len) { append(labelAlphabet[random.nextInt(labelAlphabet.size)]) }
        }
        return InstalledApp(packageName = "com.pkg.app$index", label = label)
    }

    private fun randomAppList(random: Random): List<InstalledApp> {
        val size = random.nextInt(0, 20)
        return (0 until size).map { randomApp(random, it) }
    }

    /** Randomly re-cases each character of [s] to a mixed-case variant. */
    private fun mixedCase(random: Random, s: String): String = buildString {
        for (c in s) {
            append(if (random.nextBoolean()) c.uppercaseChar() else c.lowercaseChar())
        }
    }

    /**
     * Produces a query: about half the time a substring drawn from some app's label
     * (a guaranteed-ish match source), otherwise a likely non-matching random string.
     */
    private fun randomQuery(random: Random, apps: List<InstalledApp>): String {
        val useSubstring = apps.isNotEmpty() && random.nextBoolean()
        return if (useSubstring) {
            val label = apps[random.nextInt(apps.size)].label
            if (label.isEmpty()) {
                "x"
            } else {
                val start = random.nextInt(label.length)
                val end = random.nextInt(start + 1, label.length + 1)
                val sub = label.substring(start, end)
                // Randomly re-case the substring so matching cannot rely on exact case.
                mixedCase(random, sub)
            }
        } else {
            // A random string highly unlikely to be a substring of any label.
            // Uses punctuation outside the label alphabet so matches are near-impossible.
            val nonMatchingChars = ('!'..'/').toList()
            val len = random.nextInt(1, 6)
            buildString {
                repeat(len) { append(nonMatchingChars[random.nextInt(nonMatchingChars.size)]) }
            }
        }
    }

    /**
     * Property 1 (soundness): every app in the result has a label containing the query
     * case-insensitively.
     */
    @Test
    fun `property - every result label contains the query case-insensitively`() {
        val random = Random(seed = 5001)
        repeat(300) {
            val apps = randomAppList(random)
            val query = randomQuery(random, apps)
            val result = filterByQuery(apps, query)
            val trimmed = query.trim()

            if (trimmed.isNotEmpty()) {
                result.forEach { app ->
                    assertTrue(
                        "Result label '${app.label}' must contain query '$query' " +
                            "(case-insensitive)",
                        app.label.contains(trimmed, ignoreCase = true)
                    )
                }
            }
        }
    }

    /**
     * Property 2 (completeness + order): every app excluded from the result does NOT match
     * the query case-insensitively, and the result preserves the input order.
     */
    @Test
    fun `property - excluded apps do not match and order is preserved`() {
        val random = Random(seed = 5002)
        repeat(300) {
            val apps = randomAppList(random)
            val query = randomQuery(random, apps)
            val trimmed = query.trim()
            val result = filterByQuery(apps, query)

            if (trimmed.isNotEmpty()) {
                val resultSet = result.toSet()
                apps.filter { it !in resultSet }.forEach { app ->
                    assertTrue(
                        "Excluded label '${app.label}' must NOT contain query '$query'",
                        !app.label.contains(trimmed, ignoreCase = true)
                    )
                }

                // Order preserved: the result is the input filtered in place.
                val expected = apps.filter { it.label.contains(trimmed, ignoreCase = true) }
                assertEquals(
                    "Result must equal the in-order filtered input",
                    expected,
                    result
                )
            }
        }
    }

    /**
     * Property 3 (case-insensitivity): filtering with the query in lowercase, uppercase, and
     * a random mixed case all yield the same result set.
     */
    @Test
    fun `property - filtering is invariant to query casing`() {
        val random = Random(seed = 5003)
        repeat(300) {
            val apps = randomAppList(random)
            val query = randomQuery(random, apps)

            val lower = filterByQuery(apps, query.lowercase())
            val upper = filterByQuery(apps, query.uppercase())
            val mixed = filterByQuery(apps, mixedCase(random, query))

            assertEquals(
                "Lowercase and UPPERCASE queries must yield the same result",
                lower,
                upper
            )
            assertEquals(
                "Lowercase and mixed-case queries must yield the same result",
                lower,
                mixed
            )
        }
    }

    /**
     * Property 4 (identity): a blank or empty query returns the full list unchanged, with
     * order preserved.
     */
    @Test
    fun `property - blank or empty query returns the full list unchanged`() {
        val random = Random(seed = 5004)
        val blankQueries = listOf("", " ", "   ", "\t", "\n", " \t \n ")
        repeat(150) {
            val apps = randomAppList(random)
            val query = blankQueries[random.nextInt(blankQueries.size)]

            val result = filterByQuery(apps, query)

            assertEquals(
                "Blank query '${query.replace("\n", "\\n").replace("\t", "\\t")}' " +
                    "must return the full list unchanged (order preserved)",
                apps,
                result
            )
        }
    }
}
