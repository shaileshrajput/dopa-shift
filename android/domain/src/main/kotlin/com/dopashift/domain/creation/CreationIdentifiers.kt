package com.dopashift.domain.creation

import java.util.UUID

/**
 * Semantic aliases for the raw [UUID] identifiers used by the parent domain
 * entities ([com.dopashift.domain.entity.GoalProfile], etc.).
 *
 * These aliases document intent at Dashboard quick-create call sites without
 * redefining or wrapping the existing identifier type: a [UserId] and a [GoalId]
 * are both plain [UUID]s, so they remain fully interoperable with the parent
 * repositories and entities that already accept [UUID] (DQC-1.10).
 */
typealias UserId = UUID

/** Semantic alias for a [com.dopashift.domain.entity.GoalProfile] identifier. */
typealias GoalId = UUID
