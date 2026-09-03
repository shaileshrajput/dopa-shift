package com.dopashift.domain.creation

import java.util.UUID

/**
 * A stable identifier generated at the point of origin of a creation action and
 * propagated through every downstream use-case call, local write, and diagnostic
 * event for that action (DQC-6.3).
 *
 * For an Inline_Goal_Capture transaction, a single [CorrelationId] spans the whole
 * unit (the goal and its dependent habit share it).
 */
@JvmInline
value class CorrelationId(val value: String) {
    init {
        require(value.isNotBlank()) { "CorrelationId must not be blank" }
    }

    companion object {
        /** Generates a new random [CorrelationId] at an action's origin. */
        fun random(): CorrelationId = CorrelationId(UUID.randomUUID().toString())
    }
}
