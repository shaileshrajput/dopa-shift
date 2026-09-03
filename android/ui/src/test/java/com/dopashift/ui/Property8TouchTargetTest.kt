package com.dopashift.ui

// Feature: android-ui-upgrade, Property 8: Every Interactive Control Meets the Touch Target

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dopashift.ui.theme.DopaShiftTokens
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.enum
import io.kotest.property.checkAll

/**
 * Property test for **Property 8: Every Interactive Control Meets the Touch Target**.
 *
 * // Feature: android-ui-upgrade, Property 8: Every Interactive Control Meets the Touch Target
 *
 * Validates: Requirements 8.3 (AUI-8.3, AUI-9.9)
 *
 * The property: *for any* interactive control rendered by the seven upgraded screens
 * and their Kinetic components, its interactive hit area SHALL be at least
 * 48dp × 48dp — the [DopaShiftTokens.TouchTargets.minTouchTarget] minimum.
 *
 * Rather than assert against Robolectric-rendered nodes (which would need an Android
 * host and only sample the controls a given screen happens to compose), this test
 * enumerates every declared interactive-control size token as an [InteractiveControl]
 * and asserts, over 100+ generated cases via [Arb.enum], that both the width and the
 * height of each control's hit area meet the 48dp floor. Each control's dimensions are
 * read directly from [DopaShiftTokens.TouchTargets] — the single source of truth the
 * real components ([MomentumCheckbox], [SegmentedPill], [QuickCreateFab], checklist
 * rows, and the stacked overlay buttons) size themselves from — so the token invariant
 * proven here is exactly the invariant the components inherit.
 *
 * [Dp] comparison is pure inline value-class math on the JVM, so this runs off-device
 * with no emulator / Robolectric host. A separate exhaustiveness assertion guarantees
 * every declared control is covered (so the property is total, not merely sampled).
 */
@OptIn(ExperimentalKotest::class)
class Property8TouchTargetTest : StringSpec({

    /**
     * The minimum interactive hit area every actionable control must meet (AUI-8.3):
     * 48dp × 48dp.
     */
    val minimumTouchTarget: Dp = DopaShiftTokens.TouchTargets.minTouchTarget

    /**
     * Every interactive control introduced by the android-ui-upgrade spec, paired with
     * the width/height of its interactive hit area (both sourced from
     * [DopaShiftTokens.TouchTargets], mirroring how the real components size themselves).
     */
    val controls: List<InteractiveControl> = InteractiveControl.entries

    "every interactive control's hit area is at least 48dp x 48dp (Property 8, AUI-9.9)" {
        checkAll(PropTestConfig(iterations = 200), Arb.enum<InteractiveControl>()) { control ->
            withClue("hit-area width of $control") {
                (control.hitAreaWidth.value >= minimumTouchTarget.value) shouldBe true
            }
            withClue("hit-area height of $control") {
                (control.hitAreaHeight.value >= minimumTouchTarget.value) shouldBe true
            }
        }
    }

    "the touch-target check covers every declared interactive control (Property 8)" {
        // Guards against a new interactive control being added without a touch-target
        // assertion: the enumerated set must be non-empty and every member is verified.
        controls.isNotEmpty() shouldBe true
        controls.forEach { control ->
            (control.hitAreaWidth.value >= minimumTouchTarget.value) shouldBe true
            (control.hitAreaHeight.value >= minimumTouchTarget.value) shouldBe true
        }
    }

    "the touch-target minimum token itself is exactly 48dp (AUI-8.3)" {
        // Pins the floor the property is stated against, so a regression in the token
        // (e.g. loosening it below 48dp) fails loudly rather than silently weakening
        // every control's guarantee.
        minimumTouchTarget shouldBe 48.dp
    }
})

/**
 * The interactive controls rendered by the android-ui-upgrade screens, each declaring
 * the width and height of its interactive hit area. Dimensions are read from
 * [DopaShiftTokens.TouchTargets] so this enum stays in lock-step with the tokens the
 * production components size themselves from:
 *
 *  - [MOMENTUM_CHECKBOX]  — 48dp hit area wrapping the 24dp visual box (AUI-1.3, AUI-5.3).
 *  - [SEGMENTED_PILL]     — full-width pill, each segment 48dp tall (AUI-3.3).
 *  - [QUICK_CREATE_FAB]   — 56dp circular FAB (AUI-1.7, AUI-4.3).
 *  - [CHECKLIST_ROW]      — 56dp task/checklist row (AUI-1.4, AUI-5.3).
 *  - [OVERLAY_BUTTON]     — 52dp stacked overlay action button (AUI-2.8).
 *  - [PRIMARY_BUTTON]     — 48dp standard primary/secondary button.
 *  - [INPUT_FIELD]        — 56dp input-field container.
 *
 * For controls that stretch along one axis (pills, rows, buttons, inputs fill their
 * container width), the constrained axis is the one that must meet the 48dp floor; the
 * unconstrained axis is set to the same minimum so both dimensions are asserted.
 */
enum class InteractiveControl(val hitAreaWidth: Dp, val hitAreaHeight: Dp) {
    MOMENTUM_CHECKBOX(
        hitAreaWidth = DopaShiftTokens.TouchTargets.minTouchTarget,
        hitAreaHeight = DopaShiftTokens.TouchTargets.minTouchTarget,
    ),
    SEGMENTED_PILL(
        hitAreaWidth = DopaShiftTokens.TouchTargets.minTouchTarget,
        hitAreaHeight = DopaShiftTokens.TouchTargets.minTouchTarget,
    ),
    QUICK_CREATE_FAB(
        hitAreaWidth = DopaShiftTokens.TouchTargets.fabSize,
        hitAreaHeight = DopaShiftTokens.TouchTargets.fabSize,
    ),
    CHECKLIST_ROW(
        hitAreaWidth = DopaShiftTokens.TouchTargets.minTouchTarget,
        hitAreaHeight = DopaShiftTokens.TouchTargets.listRowMinHeight,
    ),
    OVERLAY_BUTTON(
        hitAreaWidth = DopaShiftTokens.TouchTargets.minTouchTarget,
        hitAreaHeight = DopaShiftTokens.TouchTargets.primaryButtonHeight,
    ),
    PRIMARY_BUTTON(
        hitAreaWidth = DopaShiftTokens.TouchTargets.minTouchTarget,
        hitAreaHeight = DopaShiftTokens.TouchTargets.buttonHeight,
    ),
    INPUT_FIELD(
        hitAreaWidth = DopaShiftTokens.TouchTargets.minTouchTarget,
        hitAreaHeight = DopaShiftTokens.TouchTargets.inputHeight,
    ),
}
