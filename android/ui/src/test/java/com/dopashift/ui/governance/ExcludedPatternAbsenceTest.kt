package com.dopashift.ui.governance

// Feature: dynamic-ui-experience, Task 20.2
// Validates: Requirements DUX-2.13 (excluded dark patterns), DUX-4.10 (competitor-asset avoidance)

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Absence-test enforcement for the two DUX governance criteria that are stated as
 * prohibitions rather than behaviours, and therefore cannot be exercised by a
 * conventional "does X happen" test — the only way to gate them automatically is to
 * assert the forbidden constructs never appear in the feature source:
 *
 *  - **DUX-2.13** — the app SHALL NOT implement autoplay-on-scroll video, infinite-scroll
 *    feeds without an end state, or any animation timed to re-engage the user after a period
 *    of inactivity. These attention-maximizing ("dark") patterns are explicitly excluded as
 *    contrary to the product's purpose.
 *  - **DUX-4.10** — the app SHALL NOT reproduce visual assets or copyrighted UI elements from
 *    Habitify, Streaks/Calistree, or Fabulous; those products are an interaction-quality
 *    reference only.
 *
 * This is a pure-JVM file scan (no Compose / Robolectric / emulator): it walks the Kotlin
 * source of the `ui` and `interception` modules and fails the build if a forbidden token
 * appears in feature code. It runs as part of `:ui:testDebugUnitTest`, so — together with the
 * accessibility suite and `:ui:verifyDesignTokens` — it is gated on every CI run (DUX-7.4,
 * DUX-7.8) and is the automated arm of the "review-checklist + absence test" enforcement the
 * design prescribes for these two criteria.
 *
 * The scan is intentionally conservative: it matches the specific dark-pattern APIs/identifiers
 * and the specific competitor names, ignores comment lines (so this test file's own
 * documentation and any explanatory comments don't self-trip it), and reports every violation
 * with file:line context.
 */
class ExcludedPatternAbsenceTest {

    private data class Rule(
        val criterion: String,
        val description: String,
        val regex: Regex,
    )

    private companion object {

        /**
         * DUX-2.13 — forbidden attention-maximizing patterns. Matched as whole-word / API
         * identifiers so ordinary code (e.g. a variable literally named `end`) is not flagged.
         */
        private val EXCLUDED_PATTERN_RULES = listOf(
            Rule(
                criterion = "DUX-2.13",
                description = "autoplay-on-scroll video is excluded",
                regex = Regex("""\baut[Oo]?[Pp]lay\b|autoPlayOnScroll|autoplayOnScroll""", RegexOption.IGNORE_CASE),
            ),
            Rule(
                criterion = "DUX-2.13",
                description = "infinite-scroll feed without an end state is excluded",
                regex = Regex("""infinite[_\s]?[Ss]croll|endlessScroll|infiniteScroll""", RegexOption.IGNORE_CASE),
            ),
            Rule(
                criterion = "DUX-2.13",
                description = "re-engagement / inactivity-timed animation is excluded",
                regex = Regex("""re[-_]?engage(ment)?|inactivityTimer|reEngageTimer|winBackTimer""", RegexOption.IGNORE_CASE),
            ),
        )

        /**
         * DUX-4.10 — competitor product names must not appear as reproduced assets/identifiers
         * in feature source. The names are distinctive enough that a match indicates a copied
         * asset reference (drawable name, string, or class) rather than incidental prose.
         */
        private val COMPETITOR_ASSET_RULES = listOf(
            Rule("DUX-4.10", "no reproduced Habitify asset", Regex("""habitify""", RegexOption.IGNORE_CASE)),
            Rule("DUX-4.10", "no reproduced Streaks asset", Regex("""\bstreaksApp\b|streaks_app|calistree""", RegexOption.IGNORE_CASE)),
            Rule("DUX-4.10", "no reproduced Fabulous asset", Regex("""\bfabulousApp\b|fabulous_app""", RegexOption.IGNORE_CASE)),
        )

        private val ALL_RULES = EXCLUDED_PATTERN_RULES + COMPETITOR_ASSET_RULES

        /** Modules whose Kotlin feature source is subject to the prohibition scan. */
        private val SCANNED_MODULES = listOf("ui", "interception")

        /** This test itself defines the forbidden tokens, so exclude it from the scan. */
        private const val SELF_FILE_NAME = "ExcludedPatternAbsenceTest.kt"
    }

    private data class Violation(val rule: Rule, val file: File, val line: Int, val text: String)

    @Test
    fun featureSourceContainsNoExcludedDarkPatternsOrCompetitorAssets() {
        val roots = moduleSourceRoots()
        assertTrue(
            "Expected to locate at least one module source root to scan; found none. " +
                "Ensure the test runs from the android module tree.",
            roots.isNotEmpty(),
        )

        val violations = mutableListOf<Violation>()
        roots.forEach { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" && it.name != SELF_FILE_NAME }
                .forEach { file ->
                    file.readLines().forEachIndexed { idx, raw ->
                        val trimmed = raw.trimStart()
                        // Ignore comment lines so documentation cannot self-trip the scan.
                        if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                            return@forEachIndexed
                        }
                        ALL_RULES.forEach { rule ->
                            if (rule.regex.containsMatchIn(raw)) {
                                violations += Violation(rule, file, idx + 1, raw.trim())
                            }
                        }
                    }
                }
        }

        assertTrue(
            buildString {
                append("Found ${violations.size} DUX governance violation(s) (DUX-2.13 / DUX-4.10):\n")
                violations.forEach { v ->
                    append("  [${v.rule.criterion}] ${v.rule.description}\n")
                    append("    ${v.file.absolutePath}:${v.line}\n")
                    append("      ${v.text}\n")
                }
                append(
                    "\nThese patterns are explicitly excluded by the dynamic-ui-experience spec. " +
                        "Remove the offending construct rather than suppressing the check.",
                )
            },
            violations.isEmpty(),
        )
    }

    /**
     * Resolves the Kotlin source roots for the scanned modules regardless of the working
     * directory Gradle launches from. The `ui` module test cwd is `android/ui`, so the sibling
     * `interception` module is reached by walking up to the `android` root.
     */
    private fun moduleSourceRoots(): List<File> {
        val androidRoot = locateAndroidRoot()
        val roots = mutableListOf<File>()
        SCANNED_MODULES.forEach { module ->
            listOf("src/main/java", "src/main/kotlin").forEach { sub ->
                val candidate = File(androidRoot, "$module/$sub")
                if (candidate.isDirectory) roots += candidate.absoluteFile
            }
        }
        return roots
    }

    /**
     * Walks up from the current working directory to find the `android` module root — the
     * directory that contains the `ui` and `interception` module folders (identified by their
     * `build.gradle.kts`).
     */
    private fun locateAndroidRoot(): File {
        var cursor: File? = File("").absoluteFile
        repeat(6) {
            val here = cursor ?: return@repeat
            // The android root directly contains the module directories.
            if (File(here, "ui/build.gradle.kts").isFile &&
                File(here, "interception/build.gradle.kts").isFile
            ) {
                return here
            }
            // When cwd is the module dir (android/ui), the parent is the android root.
            val parent = here.parentFile
            if (parent != null &&
                File(parent, "ui/build.gradle.kts").isFile &&
                File(parent, "interception/build.gradle.kts").isFile
            ) {
                return parent
            }
            cursor = parent
        }
        throw AssertionError(
            "Could not locate the android module root (the dir containing ui/ and interception/) " +
                "from cwd '${File("").absolutePath}'.",
        )
    }
}
