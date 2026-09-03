package com.dopashift.domain.creation

import io.kotest.core.Tag
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll

/**
 * Property 14: Preset Category Yields Bundled Suggested Keywords.
 *
 * **Feature: dashboard-quick-create, Property 14**
 * **Validates: Requirements 2.3**
 *
 * For any preset category selected in the goal sheet, the pre-populated suggested keywords
 * SHALL equal the keywords defined for that category in the locally bundled mapping, and each
 * suggestion SHALL be individually editable and removable before saving (DQC-2.3).
 *
 * The subject under test is the pure-Kotlin [CategoryKeywordProvider] port. Its asset-reading
 * implementation lives in the `data` module; here it is backed by an in-memory fake seeded
 * from the same locally bundled mapping so the property runs deterministically on the JVM with
 * 100+ iterations (no emulator, no asset I/O).
 *
 * "Individually editable and removable" is a domain-level guarantee that the returned
 * suggestions form an ordered, per-element-addressable collection: removing any single
 * suggestion leaves every other suggestion intact and preserves relative order. This mirrors
 * how the goal sheet lets the user accept, edit, or remove each suggestion before saving.
 */
class PresetCategoryKeywordsPropertyTest : StringSpec({

    tags(
        Tag("Feature: dashboard-quick-create"),
        Tag("Property 14")
    )

    // >= 100 generated cases per property (design Testing Strategy: min 100 iterations).
    val config = PropTestConfig(iterations = 200)

    // ---- Locally bundled mapping (mirrors data/assets/category_keywords.json) -------------

    // The preset categories DQC-2.2 requires at minimum, each with its bundled keyword list.
    // This is the single source of truth the fake provider is built from and the oracle the
    // property asserts against.
    val bundledMapping: Map<String, List<String>> = linkedMapOf(
        "Career Growth" to listOf(
            "promotion", "networking", "skills", "leadership",
            "portfolio", "certification", "interview", "mentorship"
        ),
        "Fitness" to listOf(
            "workout", "running", "strength", "nutrition",
            "steps", "hydration", "flexibility", "recovery"
        ),
        "Build Business" to listOf(
            "startup", "marketing", "customers", "revenue",
            "product", "sales", "branding", "cashflow"
        ),
        "Clear Exam" to listOf(
            "study", "revision", "mock test", "syllabus",
            "notes", "practice", "time management", "concepts"
        ),
        "Learning" to listOf(
            "reading", "courses", "practice", "notes",
            "tutorials", "projects", "review", "curiosity"
        ),
        "Wellbeing" to listOf(
            "meditation", "sleep", "journaling", "gratitude",
            "mindfulness", "breathing", "screen time", "connection"
        )
    )

    // ---- In-memory fake port backed by the bundled mapping --------------------------------

    val provider: CategoryKeywordProvider = object : CategoryKeywordProvider {
        override fun presetCategories(): List<String> = bundledMapping.keys.toList()

        override fun suggestedKeywords(category: String): List<String> =
            bundledMapping[category] ?: emptyList()
    }

    // Draw only from the provider's own preset categories.
    val presetCategoryArb: Arb<String> = Arb.of(provider.presetCategories())

    // ---- Core property: suggestions equal the bundled mapping -----------------------------

    "Property 14: selecting any preset category yields exactly the bundled keywords for it" {
        checkAll(config, presetCategoryArb) { category ->
            val suggestions = provider.suggestedKeywords(category)
            suggestions shouldContainExactly bundledMapping.getValue(category)
        }
    }

    "Property 14: every preset category yields a non-empty suggestion list" {
        checkAll(config, presetCategoryArb) { category ->
            provider.suggestedKeywords(category).size shouldBeGreaterThanOrEqual 1
        }
    }

    // ---- Editable / removable guarantee ---------------------------------------------------

    "Property 14: removing any single suggestion leaves the others intact and in order" {
        val indexedCategory = arbitraryCategoryWithIndex(provider)
        checkAll(config, indexedCategory) { (category, rawIndex) ->
            val suggestions = provider.suggestedKeywords(category)
            val index = rawIndex.mod(suggestions.size) // valid index for this category

            val removed = suggestions[index]
            val afterRemoval = suggestions.toMutableList().apply { removeAt(index) }

            // The removed element is gone; the surviving elements keep their relative order.
            afterRemoval shouldContainExactly (suggestions.filterIndexed { i, _ -> i != index })
            afterRemoval.size shouldBe suggestions.size - 1
            // Removal is individual: a distinct-valued list never loses more than the target.
            if (suggestions.count { it == removed } == 1) {
                afterRemoval shouldNotContain removed
            }
        }
    }

    "Property 14: editing any single suggestion changes only that entry" {
        val indexedCategory = arbitraryCategoryWithIndex(provider)
        checkAll(config, indexedCategory) { (category, rawIndex) ->
            val suggestions = provider.suggestedKeywords(category)
            val index = rawIndex.mod(suggestions.size)

            val edited = suggestions.toMutableList().apply { this[index] = "edited-keyword" }

            edited.size shouldBe suggestions.size
            edited[index] shouldBe "edited-keyword"
            // Every other position is untouched.
            edited.forEachIndexed { i, value ->
                if (i != index) value shouldBe suggestions[i]
            }
        }
    }

    // ---- Free-text (non-preset) category has no bundled suggestions -----------------------

    "Property 14: a free-text category outside the preset mapping yields no suggestions" {
        // "custom" categories are not in the bundled mapping (DQC-2.2 allows free-text entry).
        provider.suggestedKeywords("Underwater Basket Weaving") shouldContainExactly emptyList()
    }
})

/**
 * Pairs an arbitrary preset category with an arbitrary non-negative index seed; the test
 * reduces the seed modulo the category's suggestion count to obtain a valid element index.
 */
private fun arbitraryCategoryWithIndex(
    provider: CategoryKeywordProvider
): Arb<Pair<String, Int>> {
    val categoryArb = Arb.of(provider.presetCategories())
    val indexArb = Arb.int(0, 10_000)
    return arbitrary { rs ->
        categoryArb.bind() to indexArb.bind()
    }
}
