package com.dopashift.ui.dashboard

// Feature: dynamic-ui-experience, Property 6: Rejected optimistic update reverts to the authoritative value

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest

/**
 * Property-based test for the pure optimistic-update + revert core (task 10.3, [OptimisticUpdate]).
 *
 * Feature: dynamic-ui-experience, Property 6: Rejected optimistic update reverts to the authoritative value
 *
 * Validates: Requirements DUX-2.8 (and the accepted-commit half of DUX-2.7).
 *
 * The design property states: *for any* initial authoritative field value and any optimistic change
 * applied to it, if the subsequent write/sync is rejected then the resulting field equals the
 * original authoritative value and a `Reverted` event is emitted; a successful commit keeps the new
 * value. This exercises the framework-free core ([optimisticUpdate] and [OptimisticController]) so
 * the keep-or-revert decision is verified independent of Compose.
 *
 * Each property runs a minimum of 100 generated cases (design Testing Strategy: min 100 iterations).
 * Suspend calls run under kotlinx-coroutines-test [runTest].
 */
@OptIn(ExperimentalKotest::class)
class Property6OptimisticRevertTest : StringSpec({

    // >= 100 generated cases per property.
    val config = PropTestConfig(iterations = 200)

    // A deterministic revert message so assertions can focus on the transition, not the copy.
    fun <T> message(attempted: T, restored: T): String = "reverted $attempted -> $restored"

    "Property 6 (DUX-2.8): a commit returning false reverts to the authoritative value with a Reverted event" {
        checkAll(config, Arb.int(), Arb.int()) { current, next ->
            runTest {
                val result = optimisticUpdate(
                    current = current,
                    next = next,
                    revertMessage = ::message,
                    commit = { false }
                )

                val reverted = result.shouldBeInstanceOf<OptimisticResult.Reverted<Int>>()
                reverted.authoritative shouldBe current
                reverted.event shouldBe OptimisticUiEvent.Reverted(message(next, current))
                reverted.cause shouldBe null
            }
        }
    }

    "Property 6 (DUX-2.8): a commit that throws (non-cancellation) reverts and attaches the cause" {
        checkAll(config, Arb.int(), Arb.int()) { current, next ->
            runTest {
                val boom = IllegalStateException("sync conflict")
                val result = optimisticUpdate(
                    current = current,
                    next = next,
                    revertMessage = ::message,
                    commit = { throw boom }
                )

                val reverted = result.shouldBeInstanceOf<OptimisticResult.Reverted<Int>>()
                reverted.authoritative shouldBe current
                reverted.event shouldBe OptimisticUiEvent.Reverted(message(next, current))
                reverted.cause shouldBe boom
            }
        }
    }

    "Property 6 (DUX-2.7): a commit returning true keeps the optimistic value" {
        checkAll(config, Arb.int(), Arb.int()) { current, next ->
            runTest {
                val result = optimisticUpdate(
                    current = current,
                    next = next,
                    revertMessage = ::message,
                    commit = { true }
                )

                result.shouldBeInstanceOf<OptimisticResult.Kept<Int>>().value shouldBe next
            }
        }
    }

    "Property 6 (DUX-2.8): OptimisticController rejection fully reverts the backing field and emits Reverted" {
        checkAll(config, Arb.boolean(), Arb.boolean()) { current, next ->
            runTest {
                // Back the controller with a plain var acting as the read/write accessors.
                var field = current
                val events = mutableListOf<OptimisticUiEvent>()

                val controller = OptimisticController(
                    read = { field },
                    write = { field = it },
                    revertMessage = ::message,
                    onEvent = { events += it }
                )

                val result = controller.update(next) { false }

                // The field is restored to the original authoritative value (fully reverted).
                field shouldBe current
                controller.value shouldBe current
                result.shouldBeInstanceOf<OptimisticResult.Reverted<Boolean>>()
                // Exactly one non-blocking Reverted event was surfaced.
                events shouldBe listOf(OptimisticUiEvent.Reverted(message(next, current)))
            }
        }
    }

    "Property 6 (DUX-2.7): OptimisticController acceptance keeps the new value and emits no revert event" {
        checkAll(config, Arb.boolean(), Arb.boolean()) { current, next ->
            runTest {
                var field = current
                val events = mutableListOf<OptimisticUiEvent>()

                val controller = OptimisticController(
                    read = { field },
                    write = { field = it },
                    revertMessage = ::message,
                    onEvent = { events += it }
                )

                val result = controller.update(next) { true }

                field shouldBe next
                controller.value shouldBe next
                result.shouldBeInstanceOf<OptimisticResult.Kept<Boolean>>().value shouldBe next
                // No revert event on the happy path.
                events shouldBe emptyList()
            }
        }
    }
})
