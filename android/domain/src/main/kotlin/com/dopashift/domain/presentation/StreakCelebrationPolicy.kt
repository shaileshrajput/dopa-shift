package com.dopashift.domain.presentation

/**
 * Pure decision logic for the 7-day streak celebration (DUX-2 AC4).
 *
 * A celebration is a brief, non-blocking, dismissible reward shown when a habit/goal
 * streak reaches a positive multiple of 7 days for the first time. This object decides
 * *whether* a given streak value should trigger a celebration; it renders nothing and has
 * no Android/Compose dependency, so it is fully unit- and property-testable under the JVM
 * over arbitrary sequences of streak values (Property 5).
 *
 * Two guarantees hold by construction:
 *  - **7-day rule:** a celebration fires only for a positive multiple of 7.
 *  - **Once-per-milestone:** each distinct milestone value fires at most once for a given
 *    tracked sequence — a value already celebrated never fires again, even if the streak
 *    dips and returns to it. Callers thread this via [MilestoneMemory] (see [evaluate]).
 */
object StreakCelebrationPolicy {

    /**
     * Streak length that defines one celebration milestone: celebrations fire on positive
     * multiples of this value (7, 14, 21, ...) — DUX-2 AC4.
     */
    const val MILESTONE_INTERVAL: Int = 7

    /**
     * Upper bound on a streak celebration's duration: it must never exceed 1500 ms so the
     * reward stays brief and non-blocking (DUX-2 AC4).
     */
    const val MAX_CELEBRATION_DURATION_MILLIS: Int = 1500

    /**
     * `true` when [streakValue] is a positive multiple of [MILESTONE_INTERVAL] — i.e. it is a
     * celebration milestone on its own, ignoring history. Non-positive values (including 0)
     * are never milestones.
     */
    fun isMilestone(streakValue: Int): Boolean =
        streakValue > 0 && streakValue % MILESTONE_INTERVAL == 0

    /**
     * Decides whether reaching [streakValue] should trigger a celebration, given the set of
     * milestone values already celebrated in this sequence ([alreadyCelebrated]).
     *
     * Returns `true` iff [streakValue] is a positive multiple of [MILESTONE_INTERVAL] and is
     * not already present in [alreadyCelebrated]. Pure and deterministic: the same
     * arguments always yield the same result. It does not mutate [alreadyCelebrated]; use
     * [MilestoneMemory] (or [evaluate]) to advance history as a sequence is processed.
     */
    fun shouldCelebrate(streakValue: Int, alreadyCelebrated: Set<Int>): Boolean =
        isMilestone(streakValue) && streakValue !in alreadyCelebrated

    /**
     * Evaluates a single new [streakValue] against prior [memory], returning both the
     * celebration decision and the updated memory to carry forward.
     *
     * This is the fold step for processing a sequence of streak values: seed with
     * [MilestoneMemory.EMPTY] and thread the returned [StreakCelebrationDecision.updatedMemory]
     * into the next call. The once-per-milestone guarantee (DUX-2 AC4) is preserved because a
     * milestone value is recorded in the memory the first time it fires and is then suppressed
     * on every subsequent occurrence.
     */
    fun evaluate(streakValue: Int, memory: MilestoneMemory): StreakCelebrationDecision {
        val celebrate = shouldCelebrate(streakValue, memory.celebratedValues)
        val nextMemory = if (celebrate) memory.withCelebrated(streakValue) else memory
        return StreakCelebrationDecision(
            triggered = celebrate,
            durationMillis = if (celebrate) MAX_CELEBRATION_DURATION_MILLIS else 0,
            updatedMemory = nextMemory
        )
    }
}

/**
 * Immutable record of which streak milestone values have already been celebrated in a
 * sequence, enabling the once-per-milestone guarantee (DUX-2 AC4) without any mutable state.
 *
 * @property celebratedValues the milestone streak values already celebrated so far
 */
data class MilestoneMemory(
    val celebratedValues: Set<Int> = emptySet()
) {
    /** Returns a new memory with [value] recorded as celebrated. */
    fun withCelebrated(value: Int): MilestoneMemory =
        if (value in celebratedValues) this
        else copy(celebratedValues = celebratedValues + value)

    companion object {
        /** Starting memory for a fresh sequence: nothing celebrated yet. */
        val EMPTY: MilestoneMemory = MilestoneMemory()
    }
}

/**
 * Outcome of evaluating a single streak value against prior milestone history.
 *
 * @property triggered `true` when this value fires a celebration (a not-yet-seen positive
 *   multiple of [StreakCelebrationPolicy.MILESTONE_INTERVAL])
 * @property durationMillis the celebration's duration in milliseconds when [triggered],
 *   always in `0..`[StreakCelebrationPolicy.MAX_CELEBRATION_DURATION_MILLIS]; `0` when not
 *   triggered
 * @property updatedMemory the milestone memory to carry into the next evaluation
 */
data class StreakCelebrationDecision(
    val triggered: Boolean,
    val durationMillis: Int,
    val updatedMemory: MilestoneMemory
) {
    init {
        require(durationMillis in 0..StreakCelebrationPolicy.MAX_CELEBRATION_DURATION_MILLIS) {
            "durationMillis must be within 0..${StreakCelebrationPolicy.MAX_CELEBRATION_DURATION_MILLIS}, " +
                "was $durationMillis"
        }
        require(triggered || durationMillis == 0) {
            "durationMillis must be 0 when no celebration is triggered, was $durationMillis"
        }
    }
}
