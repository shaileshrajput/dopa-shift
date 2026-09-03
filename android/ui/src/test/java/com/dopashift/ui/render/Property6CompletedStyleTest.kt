package com.dopashift.ui.render

// Feature: android-ui-upgrade, Property 6: Completed items are struck through and dimmed

import androidx.compose.ui.text.style.TextDecoration
import com.dopashift.ui.theme.DopaShiftTokens
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.boolean
import io.kotest.property.checkAll

/**
 * Property test for **Property 6: Completed Items Are Struck Through and Dimmed**.
 *
 * // Feature: android-ui-upgrade, Property 6: Completed items are struck through and dimmed
 *
 * Validates: Requirements 1.5, 5.3 (AUI-1.5, AUI-5.3)
 *
 * The property: for any generated completion state, [completedRowStyle] produces a
 * render decision that is fully determined by `done`:
 *  - `done == true`  → 50% opacity, strikethrough, `LineThrough` decoration, `textCompleted` color
 *  - `done == false` → 100% opacity, no strikethrough, `None` decoration, `textPrimary` color
 *
 * [completedRowStyle] and [CompletedRowStyle] are pure JVM helpers (design → task 2.3): no Composable,
 * no Android runtime. `androidx.compose.ui.graphics.Color` is an inline value class over a `ULong` and
 * `TextDecoration` compares by value, so the assertions run off-device without an emulator or
 * Robolectric host.
 *
 * Each property runs a minimum of 100 generated cases.
 */
class Property6CompletedStyleTest : StringSpec({

    val minIterations = PropTestConfig(iterations = 200)

    "Feature: android-ui-upgrade, Property 6: completed rows are dimmed and struck through, active rows are not" {
        checkAll(minIterations, Arb.boolean()) { done ->
            val style = completedRowStyle(done)

            if (done) {
                style.opacity shouldBe 0.5f
                style.strikethrough shouldBe true
                style.textDecoration shouldBe TextDecoration.LineThrough
                style.textColor shouldBe DopaShiftTokens.Colors.textCompleted
            } else {
                style.opacity shouldBe 1.0f
                style.strikethrough shouldBe false
                style.textDecoration shouldBe TextDecoration.None
                style.textColor shouldBe DopaShiftTokens.Colors.textPrimary
            }
        }
    }
})
