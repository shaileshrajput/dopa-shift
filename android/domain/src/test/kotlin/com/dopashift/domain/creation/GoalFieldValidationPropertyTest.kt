package com.dopashift.domain.creation

// Feature: dashboard-quick-create, Property 2: Goal Field Validation and Case-Insensitive Uniqueness

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.az
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Property 2: Goal Field Validation and Case-Insensitive Uniqueness.
 *
 * **Feature: dashboard-quick-create, Property 2**
 * **Validates: Requirements 1.11, 2.1, 2.6** (also satisfies DQC-7.6)
 *
 * For any goal creation input, [GoalValidator.validate] accepts it (returns an empty error
 * map) if and only if:
 *  - the trimmed name is 1..[GoalValidator.NAME_MAX] characters and is not a case-insensitive
 *    duplicate of an existing name,
 *  - the trimmed category is 1..[GoalValidator.CATEGORY_MAX] characters, and
 *  - there are 1..[GoalValidator.KEYWORDS_MAX] keywords, each of trimmed length
 *    1..[GoalValidator.KEYWORD_MAX_LEN].
 *
 * Any input violating a bound, or duplicating an existing name in any letter case, is rejected
 * with the corresponding per-field error ([GoalValidator.FIELD_NAME],
 * [GoalValidator.FIELD_CATEGORY], [GoalValidator.FIELD_KEYWORDS]).
 *
 * Kotest property testing with a minimum of 100 iterations, per the design Testing Strategy.
 * The suite mixes a general model-based oracle over arbitrary inputs with explicit
 * boundary-value cases (name length 0/1/100/101, category 0/1/50/51, keyword count 0/1/20/21,
 * keyword length 0/1/50/51) and case-insensitive duplicate-name detection (DQC-7.6).
 */
class GoalFieldValidationPropertyTest : StringSpec({

    // >= 100 generated cases per property (design Testing Strategy: min 100 iterations).
    val config = PropTestConfig(iterations = 300)

    // ---- Reference oracle: an independent re-statement of the validation rules ----------

    /**
     * Independent model of the expected per-field error keys, derived straight from the
     * acceptance criteria (not from the implementation), used to assert equivalence.
     */
    fun expectedErrorFields(
        name: String,
        category: String,
        keywords: List<String>,
        existingNamesLower: Set<String>
    ): Set<String> {
        val fields = mutableSetOf<String>()

        val trimmedName = name.trim()
        if (trimmedName.isEmpty() ||
            trimmedName.length > GoalValidator.NAME_MAX ||
            existingNamesLower.contains(trimmedName.lowercase())
        ) {
            fields += GoalValidator.FIELD_NAME
        }

        val trimmedCategory = category.trim()
        if (trimmedCategory.isEmpty() || trimmedCategory.length > GoalValidator.CATEGORY_MAX) {
            fields += GoalValidator.FIELD_CATEGORY
        }

        val keywordInvalid = keywords.isEmpty() ||
            keywords.size > GoalValidator.KEYWORDS_MAX ||
            keywords.any { it.trim().isEmpty() || it.trim().length > GoalValidator.KEYWORD_MAX_LEN }
        if (keywordInvalid) {
            fields += GoalValidator.FIELD_KEYWORDS
        }

        return fields
    }

    // ---- Generators -----------------------------------------------------------------------

    // Names spanning empty, sub-limit, at-limit, and over-limit lengths so both the lower
    // (>=1) and upper (<=NAME_MAX) name bounds are exercised across iterations.
    val nameArb: Arb<String> = Arb.string(minSize = 0, maxSize = GoalValidator.NAME_MAX + 5, Codepoint.az())
    val categoryArb: Arb<String> =
        Arb.string(minSize = 0, maxSize = GoalValidator.CATEGORY_MAX + 5, Codepoint.az())

    // Individual keywords range from empty through over-length so keyword-length bounds fire.
    val keywordArb: Arb<String> =
        Arb.string(minSize = 0, maxSize = GoalValidator.KEYWORD_MAX_LEN + 5, Codepoint.az())

    // Keyword lists span empty, in-range, and over-count (0..KEYWORDS_MAX+3).
    val keywordsArb: Arb<List<String>> =
        Arb.list(keywordArb, range = 0..(GoalValidator.KEYWORDS_MAX + 3))

    // A small set of pre-existing (already lower-cased) names for uniqueness checks.
    val existingArb: Arb<Set<String>> =
        Arb.list(Arb.string(minSize = 1, maxSize = 12, Codepoint.az()).map { it.lowercase() }, range = 0..5)
            .map { it.toSet() }

    // ---- General equivalence property -----------------------------------------------------

    "Property 2: validate's error fields exactly match the acceptance-criteria oracle" {
        checkAll(config, nameArb, categoryArb, keywordsArb, existingArb) { name, category, keywords, existing ->
            val actual = GoalValidator.validate(name, category, keywords, existing).keys
            val expected = expectedErrorFields(name, category, keywords, existing)
            actual shouldBe expected
        }
    }

    "Property 2: empty error map iff every field is within bounds and name is unique" {
        checkAll(config, nameArb, categoryArb, keywordsArb, existingArb) { name, category, keywords, existing ->
            val errors = GoalValidator.validate(name, category, keywords, existing)
            val expectedValid = expectedErrorFields(name, category, keywords, existing).isEmpty()
            errors.isEmpty() shouldBe expectedValid
        }
    }

    // ---- Boundary-value cases: NAME length (DQC-7.6) --------------------------------------

    // A valid category and single valid keyword hold the other fields constant so the name
    // bound is isolated. No existing names => uniqueness never trips here.
    val validCategory = "Fitness"
    val validKeyword = listOf("focus")

    "Property 2 (boundary): name length 0 is rejected on the name field" {
        val errors = GoalValidator.validate("", validCategory, validKeyword, emptySet())
        errors shouldContainKey GoalValidator.FIELD_NAME
    }

    "Property 2 (boundary): name length 1 is accepted" {
        val errors = GoalValidator.validate("a", validCategory, validKeyword, emptySet())
        errors.shouldBeEmpty()
    }

    "Property 2 (boundary): name length NAME_MAX (100) is accepted" {
        val name = "a".repeat(GoalValidator.NAME_MAX)
        val errors = GoalValidator.validate(name, validCategory, validKeyword, emptySet())
        errors.shouldBeEmpty()
    }

    "Property 2 (boundary): name length NAME_MAX+1 (101) is rejected on the name field" {
        val name = "a".repeat(GoalValidator.NAME_MAX + 1)
        val errors = GoalValidator.validate(name, validCategory, validKeyword, emptySet())
        errors shouldContainKey GoalValidator.FIELD_NAME
    }

    // ---- Boundary-value cases: CATEGORY length (DQC-7.6) ----------------------------------

    val validName = "Run a marathon"

    "Property 2 (boundary): category length 0 is rejected on the category field" {
        val errors = GoalValidator.validate(validName, "", validKeyword, emptySet())
        errors shouldContainKey GoalValidator.FIELD_CATEGORY
    }

    "Property 2 (boundary): category length 1 is accepted" {
        val errors = GoalValidator.validate(validName, "a", validKeyword, emptySet())
        errors.shouldBeEmpty()
    }

    "Property 2 (boundary): category length CATEGORY_MAX (50) is accepted" {
        val category = "a".repeat(GoalValidator.CATEGORY_MAX)
        val errors = GoalValidator.validate(validName, category, validKeyword, emptySet())
        errors.shouldBeEmpty()
    }

    "Property 2 (boundary): category length CATEGORY_MAX+1 (51) is rejected on the category field" {
        val category = "a".repeat(GoalValidator.CATEGORY_MAX + 1)
        val errors = GoalValidator.validate(validName, category, validKeyword, emptySet())
        errors shouldContainKey GoalValidator.FIELD_CATEGORY
    }

    // ---- Boundary-value cases: KEYWORD COUNT (DQC-7.6) ------------------------------------

    "Property 2 (boundary): keyword count 0 is rejected on the keywords field" {
        val errors = GoalValidator.validate(validName, validCategory, emptyList(), emptySet())
        errors shouldContainKey GoalValidator.FIELD_KEYWORDS
    }

    "Property 2 (boundary): keyword count 1 is accepted" {
        val errors = GoalValidator.validate(validName, validCategory, listOf("k"), emptySet())
        errors.shouldBeEmpty()
    }

    "Property 2 (boundary): keyword count KEYWORDS_MAX (20) is accepted" {
        val keywords = List(GoalValidator.KEYWORDS_MAX) { "k$it" }
        val errors = GoalValidator.validate(validName, validCategory, keywords, emptySet())
        errors.shouldBeEmpty()
    }

    "Property 2 (boundary): keyword count KEYWORDS_MAX+1 (21) is rejected on the keywords field" {
        val keywords = List(GoalValidator.KEYWORDS_MAX + 1) { "k$it" }
        val errors = GoalValidator.validate(validName, validCategory, keywords, emptySet())
        errors shouldContainKey GoalValidator.FIELD_KEYWORDS
    }

    // ---- Boundary-value cases: KEYWORD LENGTH (DQC-7.6) -----------------------------------

    "Property 2 (boundary): a keyword of length 0 is rejected on the keywords field" {
        val errors = GoalValidator.validate(validName, validCategory, listOf(""), emptySet())
        errors shouldContainKey GoalValidator.FIELD_KEYWORDS
    }

    "Property 2 (boundary): a keyword of length 1 is accepted" {
        val errors = GoalValidator.validate(validName, validCategory, listOf("a"), emptySet())
        errors.shouldBeEmpty()
    }

    "Property 2 (boundary): a keyword of length KEYWORD_MAX_LEN (50) is accepted" {
        val keyword = "a".repeat(GoalValidator.KEYWORD_MAX_LEN)
        val errors = GoalValidator.validate(validName, validCategory, listOf(keyword), emptySet())
        errors.shouldBeEmpty()
    }

    "Property 2 (boundary): a keyword of length KEYWORD_MAX_LEN+1 (51) is rejected on the keywords field" {
        val keyword = "a".repeat(GoalValidator.KEYWORD_MAX_LEN + 1)
        val errors = GoalValidator.validate(validName, validCategory, listOf(keyword), emptySet())
        errors shouldContainKey GoalValidator.FIELD_KEYWORDS
    }

    // ---- Case-insensitive uniqueness (DQC-2.6, DQC-7.6) ----------------------------------

    "Property 2: a name duplicating an existing name in ANY letter case is rejected" {
        // Generate a base name, register its lower-cased form as existing, then submit an
        // arbitrary re-casing of the same name. It must always collide, regardless of case.
        val baseName = Arb.string(minSize = 1, maxSize = 30, Codepoint.az())
        val casingMask = Arb.list(Arb.int(0..1), range = 1..30)
        checkAll(config, baseName, casingMask) { base, mask ->
            val existing = setOf(base.lowercase())
            // Re-case each character according to the mask (upper where mask bit is 1).
            val recased = base.mapIndexed { i, c ->
                if (mask.getOrElse(i) { 0 } == 1) c.uppercaseChar() else c.lowercaseChar()
            }.joinToString("")

            val errors = GoalValidator.validate(recased, validCategory, validKeyword, existing)
            errors shouldContainKey GoalValidator.FIELD_NAME
        }
    }

    "Property 2: a unique name (not present in any case) passes the name field" {
        checkAll(config, existingArb) { existing ->
            // Build a name guaranteed absent from `existing` by construction.
            val candidate = "unique-" + (existing.size + 1)
            // Precondition guard: skip the astronomically unlikely accidental collision.
            if (!existing.contains(candidate.lowercase())) {
                val errors = GoalValidator.validate(candidate, validCategory, validKeyword, existing)
                errors shouldNotContainKey GoalValidator.FIELD_NAME
            }
        }
    }

    "Property 2: uniqueness compares the trimmed name, ignoring surrounding whitespace" {
        val existing = setOf("marathon")
        val errors = GoalValidator.validate("  MaraThon  ", validCategory, validKeyword, existing)
        errors shouldContainKey GoalValidator.FIELD_NAME
    }
})
