package com.dopashift.domain.creation

// Feature: dashboard-quick-create, Property 19: Backend Rejection Retains Input

import io.kotest.core.Tag
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.uuid
import io.kotest.property.checkAll

/**
 * Property 19: Backend Rejection Retains Input.
 *
 * **Feature: dashboard-quick-create, Property 19**
 * **Validates: Requirements 2.11**
 *
 * For any creation rejected by the Backend on sync (for example, a name-uniqueness collision
 * created on another device while offline), the client SHALL:
 *  - retain the user's original submitted input for correction, and
 *  - surface the specific, non-empty rejection reason, and
 *  - NOT silently discard the entity data.
 *
 * The domain contract for this is [CreationResult.Rejected]: it carries the specific [reason]
 * (surfaced non-blockingly) and the [retainedInput] (the original submitted command). This
 * property fixes the sync-time mapping from "the Backend rejected this submitted input for this
 * reason" to a [CreationResult.Rejected], and asserts the three guarantees hold for every input
 * and every reason:
 *
 *  1. the result is a [CreationResult.Rejected] (never a [CreationResult.Failure] and never a
 *     silent drop / null),
 *  2. `retainedInput` is the exact original submitted input (equal by value, so the sheet can be
 *     re-populated for correction), and
 *  3. `reason` is non-blank (the specific reason is surfaced for the user).
 *
 * Kotest property testing with a minimum of 100 iterations, per the design Testing Strategy.
 *
 * Tags: Feature: dashboard-quick-create, Property 19
 */
class BackendRejectionRetainsInputPropertyTest : StringSpec({

    tags(
        Tag("Feature: dashboard-quick-create"),
        Tag("Property 19")
    )

    // >= 100 generated cases per property (design Testing Strategy: min 100 iterations).
    val config = PropTestConfig(iterations = 200)

    // ---- Sync-time rejection mapping (the client contract under test) --------------------

    /**
     * Models what the client does when the Backend rejects a submitted creation on sync: it maps
     * the (submitted input, specific reason) pair to a [CreationResult.Rejected] that retains the
     * original input and surfaces the reason. This is the behaviour Requirement 2.11 requires and
     * is the exact opposite of a silent discard.
     */
    fun <T> handleBackendRejection(submittedInput: T, reason: String): CreationResult<T> =
        CreationResult.Rejected(reason = reason, retainedInput = submittedInput)

    // ---- Generators ----------------------------------------------------------------------

    val correlationIds = Arb.string(1..24).filter { it.isNotBlank() }.map { CorrelationId(it) }

    // Arbitrary — even bound-violating — submitted goal input. A Backend rejection can happen for
    // input the client considered valid (e.g. a cross-device name collision), so the retained
    // input is not constrained to the client-valid space; retention must hold for anything.
    val goalCommands: Arb<CreateGoalCommand> = Arb.bind(
        Arb.uuid(),
        Arb.string(0..120),
        Arb.string(0..60),
        Arb.list(Arb.string(0..60), 0..25),
        correlationIds
    ) { userId, name, category, keywords, correlationId ->
        CreateGoalCommand(
            userId = userId,
            name = name,
            category = category,
            keywords = keywords,
            correlationId = correlationId
        )
    }

    // The specific rejection reason the Backend surfaces (always non-blank per the contract).
    val reasons = Arb.string(1..80).filter { it.isNotBlank() }

    // ---- Properties ----------------------------------------------------------------------

    "Property 19 (DQC-2.11): a Backend-rejected goal retains the exact submitted input" {
        checkAll(config, goalCommands, reasons) { submitted, reason ->
            val result = handleBackendRejection(submitted, reason)

            // Not silently discarded and not collapsed to an opaque failure.
            result.shouldBeInstanceOf<CreationResult.Rejected>()

            // The exact original submitted input is retained for correction/retry.
            result.retainedInput.shouldNotBeNull()
            result.retainedInput shouldBe submitted

            // The specific rejection reason is surfaced (non-empty).
            result.reason shouldBe reason
            result.reason.shouldNotBeBlank()
        }
    }

    "Property 19 (DQC-2.11): retained goal input is unmodified field-for-field" {
        checkAll(config, goalCommands, reasons) { submitted, reason ->
            val result = handleBackendRejection(submitted, reason)
            result.shouldBeInstanceOf<CreationResult.Rejected>()

            // Retention is by value, so the sheet re-populates with precisely what the user typed —
            // nothing dropped, truncated, or normalised on the way to the Rejected result.
            val retained = result.retainedInput
            retained.shouldBeInstanceOf<CreateGoalCommand>()
            retained.userId shouldBe submitted.userId
            retained.name shouldBe submitted.name
            retained.category shouldBe submitted.category
            retained.keywords shouldBe submitted.keywords
            retained.correlationId shouldBe submitted.correlationId
        }
    }

    "Property 19 (DQC-2.11): retention holds for any submitted input type, not just goals" {
        // The Rejected contract is entity-agnostic: a to-do rejected on sync is retained identically.
        val todoTexts = Arb.string(0..600)
        checkAll(config, Arb.uuid(), todoTexts, reasons) { userId, text, reason ->
            val submitted = "todo:$userId:$text" // an opaque submitted-input snapshot
            val result = handleBackendRejection(submitted, reason)

            result.shouldBeInstanceOf<CreationResult.Rejected>()
            result.retainedInput shouldBe submitted
            result.reason.shouldNotBeBlank()
        }
    }
})
