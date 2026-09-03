package com.dopashift.domain.presentation

/**
 * Pure decision logic for the staggered entry animation applied when a list is rendered
 * for the first time in a session (DUX-2.6). Framework-free so it runs under JVM tests.
 *
 * Each item's entry animation is delayed relative to the start of the render so items
 * appear to cascade in. To keep the effect satisfying rather than sluggish, the stagger
 * is capped: only the first [STAGGER_CAP] items receive an incremental delay; every item
 * at or beyond the cap enters immediately (0 ms). This bounds the total stagger sequence
 * to the first eight items no matter how long the list is.
 *
 * See Correctness Property 4 ("Staggered entry delay follows the capped rule").
 */
object StaggerPolicy {

    /** Per-item incremental delay for staggered entry, in milliseconds. */
    const val STAGGER_STEP_MILLIS: Int = 40

    /**
     * Number of leading items that receive an incremental stagger delay. Items with an index
     * at or beyond this cap enter with no delay (0 ms), so the stagger never spans more than
     * the first [STAGGER_CAP] items.
     */
    const val STAGGER_CAP: Int = 8

    /**
     * Returns the entry delay, in whole milliseconds, for the item at [index] in a
     * first-render list.
     *
     * The delay is `index * `[STAGGER_STEP_MILLIS] for `index < `[STAGGER_CAP], and `0` for
     * `index >= `[STAGGER_CAP] (DUX-2.6).
     *
     * @param index zero-based position of the item in the list; must be non-negative
     * @return the entry delay in milliseconds for that item
     * @throws IllegalArgumentException if [index] is negative
     */
    fun entryDelayMillis(index: Int): Int {
        require(index >= 0) { "index must be non-negative, was $index" }
        return if (index < STAGGER_CAP) index * STAGGER_STEP_MILLIS else 0
    }
}
