package com.dopashift.ui.render

// Feature: android-ui-upgrade, Property 4: Daily Bar Color Matches Category

import androidx.compose.ui.graphics.Color
import com.dopashift.ui.theme.DopaShiftTokens
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.enum
import io.kotest.property.checkAll

/**
 * Property test for **Property 4: Daily Bar Color Matches Category**.
 *
 * // Feature: android-ui-upgrade, Property 4: Daily Bar Color Matches Category
 *
 * Validates: Requirements 6.4 (AUI-6.4, AUI-9.7)
 *
 * The property: for every [DailyBarCategory], [barColor] returns exactly the
 * [DopaShiftTokens] color that category is defined to map to, and no other
 * mapping ever occurs:
 *  - [DailyBarCategory.PRODUCTIVE]  → [DopaShiftTokens.Colors.momentumTeal]
 *  - [DailyBarCategory.DISTRACTING] → [DopaShiftTokens.Colors.dangerCoral]
 *  - [DailyBarCategory.NO_DATA]     → [DopaShiftTokens.Colors.chartNoData]
 *
 * [barColor] is a pure JVM function ([Color] is an inline value class over a
 * `ULong`), so this runs off-device with no emulator / Robolectric host.
 *
 * The property runs a minimum of 100 generated cases over all enum values via
 * [Arb.enum]. A separate exhaustiveness assertion guarantees every declared
 * enum value is covered by the expected mapping (so the property is total, not
 * just sampled).
 */
class Property4BarColorTest : StringSpec({

    val minIterations = PropTestConfig(iterations = 200)

    // The single source of truth for what each category must render as.
    val expected: Map<DailyBarCategory, Color> = mapOf(
        DailyBarCategory.PRODUCTIVE to DopaShiftTokens.Colors.momentumTeal,
        DailyBarCategory.DISTRACTING to DopaShiftTokens.Colors.dangerCoral,
        DailyBarCategory.NO_DATA to DopaShiftTokens.Colors.chartNoData,
    )

    "barColor maps every category to its exact token color and no other (Property 4)" {
        checkAll(minIterations, Arb.enum<DailyBarCategory>()) { category ->
            barColor(category) shouldBe expected.getValue(category)
        }
    }

    "the category → color mapping is exhaustive over every DailyBarCategory value (Property 4)" {
        // Guards against a new enum value slipping in without an explicit color:
        // the expected map must cover exactly the declared set of categories.
        expected.keys shouldBe DailyBarCategory.entries.toSet()
        DailyBarCategory.entries.forEach { category ->
            barColor(category) shouldBe expected.getValue(category)
        }
    }
})
