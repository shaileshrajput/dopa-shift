package com.dopashift.ui.theme

// Feature: dynamic-ui-experience, Task 17.2
// Validates: Requirements DUX-4.11

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Resource-completeness / locale-parity test for the `ui` module string resources.
 *
 * Per DUX-4.11, every user-facing string is externalized to
 * `res/values/strings.xml` with translations provided in `values-hi` (Hindi)
 * and `values-mr` (Marathi). This test parses the three `strings.xml` resource
 * files directly (pure file parsing — no Compose / Robolectric / emulator) and
 * asserts full key parity across the locales:
 *
 *  - every `<string>` and `<plurals>` `name` defined in the default `values`
 *    locale has a matching entry in `values-hi` and `values-mr`, and
 *  - there are no orphan translations (a key present in `values-hi` /
 *    `values-mr` but absent from the default `values`).
 *
 * The assertion is on the full set of keys, which is stronger than the minimum
 * `dux_`-prefixed requirement — the existing non-DUX keys already ship hi/mr
 * translations, so full parity is the correct, more robust invariant here.
 *
 * The test's working directory when run via Gradle is the module dir
 * (`android/ui`); [resDir] resolves `src/main/res` robustly, walking up parent
 * directories and falling back to a bounded filesystem search so the test is
 * insensitive to the exact launch cwd.
 */
class StringResourceCompletenessTest {

    private companion object {
        const val DEFAULT_LOCALE = "values"
        const val HINDI_LOCALE = "values-hi"
        const val MARATHI_LOCALE = "values-mr"
        val TRANSLATION_LOCALES = listOf(HINDI_LOCALE, MARATHI_LOCALE)
    }

    /**
     * Extracts the set of resource key names (`<string name=...>` and
     * `<plurals name=...>`) from a locale's `strings.xml`.
     */
    private fun keysFor(locale: String): Set<String> {
        val file = File(resDir(), "$locale/strings.xml")
        assertTrue(
            "Expected strings.xml to exist for locale '$locale' at ${file.absolutePath}",
            file.isFile,
        )

        val doc = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = false }
            .newDocumentBuilder()
            .parse(file)
        doc.documentElement.normalize()

        val keys = linkedSetOf<String>()
        for (tag in listOf("string", "plurals")) {
            val nodes = doc.getElementsByTagName(tag)
            for (i in 0 until nodes.length) {
                val element = nodes.item(i) as Element
                // Skip translatable="false" entries — they are intentionally
                // default-only and are not expected in translation locales.
                if (element.getAttribute("translatable").equals("false", ignoreCase = true)) {
                    continue
                }
                val name = element.getAttribute("name")
                if (name.isNotBlank()) {
                    keys += name
                }
            }
        }
        return keys
    }

    /**
     * Locates the module's `src/main/res` directory regardless of the exact
     * working directory Gradle launches the test from.
     */
    private fun resDir(): File {
        // 1. Relative to the module cwd (the common Gradle case).
        val direct = File("src/main/res")
        if (direct.isDirectory) return direct.absoluteFile

        // 2. Walk up from cwd looking for a ui/src/main/res or */src/main/res.
        var cursor: File? = File("").absoluteFile
        repeat(6) {
            val here = cursor ?: return@repeat
            val candidate = File(here, "src/main/res")
            if (File(candidate, "$DEFAULT_LOCALE/strings.xml").isFile) {
                return candidate
            }
            val uiCandidate = File(here, "ui/src/main/res")
            if (File(uiCandidate, "$DEFAULT_LOCALE/strings.xml").isFile) {
                return uiCandidate
            }
            cursor = here.parentFile
        }

        // 3. Bounded search downward from cwd as a last resort.
        val root = File("").absoluteFile
        root.walkTopDown()
            .maxDepth(6)
            .firstOrNull {
                it.isDirectory &&
                    it.name == "res" &&
                    File(it, "$DEFAULT_LOCALE/strings.xml").isFile &&
                    it.path.replace('\\', '/').contains("/ui/")
            }
            ?.let { return it }

        throw AssertionError(
            "Could not locate the ui module's src/main/res directory from cwd " +
                "'${root.absolutePath}'. Ensure the test runs from the module dir.",
        )
    }

    @Test
    fun defaultLocaleDefinesStringKeys() {
        val defaultKeys = keysFor(DEFAULT_LOCALE)
        assertTrue(
            "Default '$DEFAULT_LOCALE/strings.xml' must define at least one string key",
            defaultKeys.isNotEmpty(),
        )
        // Sanity: the dynamic-ui-experience feature keys are present.
        val duxKeys = defaultKeys.filter { it.startsWith("dux_") }
        assertTrue(
            "Expected dynamic-ui-experience 'dux_' keys in the default locale",
            duxKeys.isNotEmpty(),
        )
    }

    @Test
    fun translationLocalesContainEveryDefaultKey() {
        val defaultKeys = keysFor(DEFAULT_LOCALE)

        for (locale in TRANSLATION_LOCALES) {
            val localeKeys = keysFor(locale)
            val missing = (defaultKeys - localeKeys).sorted()
            assertTrue(
                "Locale '$locale' is missing ${missing.size} key(s) present in " +
                    "'$DEFAULT_LOCALE': $missing",
                missing.isEmpty(),
            )
        }
    }

    @Test
    fun translationLocalesHaveNoOrphanKeys() {
        val defaultKeys = keysFor(DEFAULT_LOCALE)

        for (locale in TRANSLATION_LOCALES) {
            val localeKeys = keysFor(locale)
            val orphans = (localeKeys - defaultKeys).sorted()
            assertTrue(
                "Locale '$locale' has ${orphans.size} orphan key(s) with no default " +
                    "'$DEFAULT_LOCALE' entry: $orphans",
                orphans.isEmpty(),
            )
        }
    }

    @Test
    fun everyLocaleHasIdenticalKeySets() {
        val defaultKeys = keysFor(DEFAULT_LOCALE)
        for (locale in TRANSLATION_LOCALES) {
            assertEquals(
                "Key set for locale '$locale' must exactly match the default locale " +
                    "'$DEFAULT_LOCALE' (no missing, no orphan keys)",
                defaultKeys,
                keysFor(locale),
            )
        }
    }

    @Test
    fun duxFeatureKeysAreFullyTranslatedInEveryLocale() {
        val duxDefaultKeys = keysFor(DEFAULT_LOCALE).filter { it.startsWith("dux_") }.toSet()
        assertTrue("Expected dux_ keys in the default locale", duxDefaultKeys.isNotEmpty())

        for (locale in TRANSLATION_LOCALES) {
            val localeDuxKeys = keysFor(locale).filter { it.startsWith("dux_") }.toSet()
            assertEquals(
                "dynamic-ui-experience 'dux_' keys must have full parity in locale '$locale'",
                duxDefaultKeys,
                localeDuxKeys,
            )
        }
    }
}
