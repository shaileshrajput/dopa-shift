package com.dopashift.ui.interception

// Feature: screen-time-interception-engine, Property 4

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

private fun allocateProviderWithoutContext(): InstalledAppsProvider {
    val unsafeClass = Class.forName("sun.misc.Unsafe")
    val field = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }
    val unsafe = field.get(null)
    val allocate = unsafeClass.getMethod("allocateInstance", Class::class.java)
    return allocate.invoke(unsafe, InstalledAppsProvider::class.java) as InstalledAppsProvider
}

/**
 * Property 4: Installed-app list is sorted and complete, excluding critical apps.
 *
 * Drives the pure [InstalledAppsProvider.filterAndSort] with randomly generated
 * candidate lists (mixed-case labels, duplicate package names) and randomly chosen
 * excluded-package sets (standing in for the system-critical apps: this app, launcher,
 * dialer, settings). Verifies:
 *
 *  1. SORTED   — the result is sorted case-insensitively alphabetically by label
 *                (String.CASE_INSENSITIVE_ORDER) for every consecutive pair.
 *  2. COMPLETE — every candidate whose package is NOT excluded appears in the result,
 *                and no excluded package appears.
 *  3. EXCLUDES CRITICAL — none of the excluded (critical) packages leak into the output.
 *  4. DEDUPE   — duplicate package names collapse to a single entry (keeping the first).
 *
 * **Validates: Requirements 2.1, 2.8**
 *
 * Module convention: JUnit4 + kotlin.random.Random + repeat(N>=100). No Kotest,
 * no coroutines, no Robolectric — filterAndSort is pure Kotlin.
 */
class InstalledAppsListPropertyTest {

    // filterAndSort is pure and never dereferences the injected Context. Rather than pull
    // in Robolectric or a mocking library just to satisfy the constructor's non-null
    // Context parameter, we allocate an instance without running the constructor. The
    // uninitialized `context` field stays null, which is harmless because filterAndSort
    // never touches it.
    private val provider: InstalledAppsProvider = allocateProviderWithoutContext()

    private val comparator = String.CASE_INSENSITIVE_ORDER

    /** Generates a label with random mixed-case letters and occasional spaces/digits. */
    private fun randomLabel(random: Random): String {
        val length = random.nextInt(1, 12)
        val alphabet = "abcdefghijABCDEFGHIJ 0123"
        return buildString {
            repeat(length) { append(alphabet[random.nextInt(alphabet.length)]) }
        }
    }

    private fun randomApp(random: Random): InstalledApp {
        // Small package-name pool so duplicates arise naturally.
        val pkg = "com.pkg.${random.nextInt(0, 8)}"
        return InstalledApp(packageName = pkg, label = randomLabel(random))
    }

    @Test
    fun `property - result is sorted case-insensitively, complete, deduped, and excludes critical apps`() {
        val random = Random(seed = 20240613)

        repeat(300) { iteration ->
            // Build a candidate list including some "critical" apps we intend to exclude.
            val criticalPackages = listOf(
                "com.dopashift.app",
                "com.android.launcher",
                "com.android.dialer",
                "com.android.settings",
            )

            val candidates = buildList {
                val count = random.nextInt(0, 25)
                repeat(count) { add(randomApp(random)) }
                // Mix in critical apps as candidates so exclusion has something to strip.
                criticalPackages.forEach { crit ->
                    if (random.nextBoolean()) {
                        add(InstalledApp(packageName = crit, label = randomLabel(random)))
                    }
                }
            }.shuffled(random)

            // Excluded set: always the critical packages, plus occasionally a random
            // package drawn from the candidate pool.
            val excluded = buildSet {
                addAll(criticalPackages)
                if (candidates.isNotEmpty() && random.nextBoolean()) {
                    add(candidates[random.nextInt(candidates.size)].packageName)
                }
            }

            val result = provider.filterAndSort(candidates, excluded)

            // (1) SORTED: every consecutive pair ordered case-insensitively by label.
            for (i in 0 until result.size - 1) {
                assertTrue(
                    "Iteration $iteration: result not sorted at index $i " +
                        "('${result[i].label}' > '${result[i + 1].label}')",
                    comparator.compare(result[i].label, result[i + 1].label) <= 0,
                )
            }

            // (3) EXCLUDES CRITICAL + no excluded package appears at all.
            val resultPackages = result.map { it.packageName }
            excluded.forEach { ex ->
                assertFalse(
                    "Iteration $iteration: excluded package '$ex' leaked into result",
                    resultPackages.contains(ex),
                )
            }

            // (2) COMPLETE: every non-excluded candidate package is present in the result.
            val expectedPackages = candidates
                .map { it.packageName }
                .filter { it !in excluded }
                .toSet()
            assertEquals(
                "Iteration $iteration: result package set does not match non-excluded candidates",
                expectedPackages,
                resultPackages.toSet(),
            )

            // (4) DEDUPE: no package appears more than once.
            assertEquals(
                "Iteration $iteration: result contains duplicate package names",
                resultPackages.size,
                resultPackages.toSet().size,
            )
        }
    }

    @Test
    fun `property - dedupe keeps the first occurrence of each package name`() {
        val random = Random(seed = 99)

        repeat(150) { iteration ->
            // Build candidates where each package name may repeat with distinct labels;
            // the kept label must equal the label of the first candidate for that package.
            val candidates = buildList {
                val count = random.nextInt(1, 20)
                repeat(count) { i ->
                    val pkg = "com.dup.${random.nextInt(0, 5)}"
                    // Encode order into label uniqueness so we can identify the first.
                    add(InstalledApp(packageName = pkg, label = "${randomLabel(random)}#$i"))
                }
            }

            val result = provider.filterAndSort(candidates, emptySet())

            // Expected first-seen label per package.
            val firstLabelByPkg = LinkedHashMap<String, String>()
            candidates.forEach { app ->
                firstLabelByPkg.putIfAbsent(app.packageName, app.label)
            }

            result.forEach { app ->
                assertEquals(
                    "Iteration $iteration: dedupe did not keep first label for '${app.packageName}'",
                    firstLabelByPkg[app.packageName],
                    app.label,
                )
            }
        }
    }
}
