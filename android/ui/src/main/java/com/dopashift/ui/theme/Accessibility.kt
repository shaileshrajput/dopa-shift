package com.dopashift.ui.theme

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Reusable accessibility (a11y) modifiers for the dynamic-ui-experience feature (DUX-5).
 *
 * These small, composable-free-of-string-literals helpers centralise the two ergonomics rules the
 * feature applies across its composables so callers don't repeat the raw values inline:
 *
 * - **Minimum touch target** — DUX-5.1 requires every interactive element to expose a touch area of
 *   at least 48dp × 48dp, and task list rows to be at least 56dp tall. [Modifier.minTouchTarget]
 *   and [Modifier.minTaskRowHeight] express those minimums by name.
 * - **Live regions** — DUX-5.8 requires user-initiated dynamic content changes (a new to-do, a
 *   progress-ring update, a sync-pending / revert message) to be announced by screen readers.
 *   [Modifier.politeLiveRegion] marks a surface as a *polite* live region so assistive tech
 *   announces its content when it changes, without interrupting the user mid-utterance.
 *
 * Material 3 components (`NavigationBarItem`, `NavigationRailItem`, `Checkbox`, `IconButton`,
 * buttons) already reserve a ≥48dp interactive size via `minimumInteractiveComponentSize()`
 * internally, so those do not need [minTouchTarget]. Use these helpers for custom clickable
 * surfaces (clickable `Row`s, `Box`es, `Canvas`-drawn controls) that do not inherit that minimum.
 */

/** DUX-5 touch-ergonomics dimensions, referenced by name rather than as inline literals. */
object A11yDimens {
    /** Minimum interactive touch target, both axes (DUX-5.1): 48dp × 48dp. */
    val MinTouchTarget: Dp = 48.dp

    /** Minimum task-list row height (DUX-5.1): 56dp. */
    val MinTaskRowHeight: Dp = 56.dp
}

/**
 * Guarantees the composable occupies at least the [A11yDimens.MinTouchTarget] (48dp × 48dp)
 * interactive area required by DUX-5.1, without forcing its visible content any larger.
 *
 * Apply to custom clickable surfaces that don't already inherit Material 3's built-in minimum
 * interactive size (e.g. a `Modifier.clickable` `Row`/`Box`, a `Canvas`-drawn control). It expands
 * only the minimum bounds via [defaultMinSize], so larger content is unaffected.
 */
fun Modifier.minTouchTarget(): Modifier =
    this.defaultMinSize(
        minWidth = A11yDimens.MinTouchTarget,
        minHeight = A11yDimens.MinTouchTarget,
    )

/**
 * Guarantees a task-list row is at least [A11yDimens.MinTaskRowHeight] (56dp) tall per DUX-5.1,
 * leaving width to the caller (rows are typically full-width).
 */
fun Modifier.minTaskRowHeight(): Modifier =
    this.heightIn(min = A11yDimens.MinTaskRowHeight)

/**
 * Marks this surface as a **polite** accessibility live region (DUX-5.8), so screen readers
 * announce changes to its content once the user is idle rather than interrupting them.
 *
 * Apply to dynamic status surfaces whose content changes as a result of a user action on the same
 * screen — e.g. a sync-pending / refresh indicator, an optimistic-revert message, or an efficiency
 * value that updates in place. The announced text is derived from the surface's own text/content
 * description, so pair this with a meaningful label (or a `contentDescription`).
 */
fun Modifier.politeLiveRegion(): Modifier =
    this.semantics { liveRegion = LiveRegionMode.Polite }

/**
 * Convenience form of [politeLiveRegion] for status surfaces that carry no intrinsic text of their
 * own (e.g. an icon-only indicator): it both marks the polite live region and sets the
 * [description] that screen readers announce when the region appears or changes.
 *
 * @param description the localized text screen readers announce for this status change.
 */
fun Modifier.politeLiveRegion(description: String): Modifier =
    this.semantics {
        liveRegion = LiveRegionMode.Polite
        contentDescription = description
    }
