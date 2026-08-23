package com.dopashift.domain.property

import com.dopashift.domain.usecase.ComputeEfficiencyScoreUseCase
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import java.time.LocalDate
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Property 10: Efficiency Score Computation Consistency
 *
 * For any set of foreground-time data (productive seconds and total tracked seconds),
 * the efficiency score computed SHALL be identical regardless of which client
 * (Android or Web) performs the computation — the formula
 * `(productive / total) * 100` rounded to nearest integer is deterministic.
 *
 * **Validates: Requirements 7.5**
 *
 * Tag: Feature: dopa-shift, Property 10: Efficiency Score Computation Consistency
 */
class EfficiencyScoreConsistencyPropertyTest : FunSpec({

    tags(
        io.kotest.core.Tag("Feature: dopa-shift"),
        io.kotest.core.Tag("Property 10: Efficiency Score Computation Consistency")
    )

    val useCaseA = ComputeEfficiencyScoreUseCase()
    val useCaseB = ComputeEfficiencyScoreUseCase()

    test("Property 10: Determinism - computing the score twice yields identical results") {
        checkAll(100, Arb.long(0L..86400L), Arb.long(0L..86400L)) { productive, total ->
            // Ensure productive <= total to represent realistic data
            val adjustedProductive = if (total == 0L) 0L else productive.coerceAtMost(total)

            val userId = UUID.randomUUID()
            val date = LocalDate.of(2024, 6, 15)

            val result1 = useCaseA.compute(adjustedProductive, total, userId, date)
            val result2 = useCaseA.compute(adjustedProductive, total, userId, date)

            result1.scorePercent shouldBe result2.scorePercent
            result1.productiveSeconds shouldBe result2.productiveSeconds
            result1.totalTrackedSeconds shouldBe result2.totalTrackedSeconds
        }
    }

    test("Property 10: Formula verification - if total >= 60 then score = round((productive/total)*100) clamped 0-100") {
        checkAll(100, Arb.long(0L..86400L), Arb.long(60L..86400L)) { productive, total ->
            val adjustedProductive = productive.coerceAtMost(total)

            val userId = UUID.randomUUID()
            val date = LocalDate.of(2024, 6, 15)

            val result = useCaseA.compute(adjustedProductive, total, userId, date)

            val expected = ((adjustedProductive.toDouble() / total.toDouble()) * 100.0)
                .roundToInt()
                .coerceIn(0, 100)

            result.scorePercent shouldBe expected
            result.scorePercent!! shouldBeInRange 0..100
        }
    }

    test("Property 10: Insufficient data - if total < 60 then scorePercent is null") {
        checkAll(100, Arb.long(0L..86400L), Arb.long(0L..59L)) { productive, total ->
            val adjustedProductive = if (total == 0L) 0L else productive.coerceAtMost(total)

            val userId = UUID.randomUUID()
            val date = LocalDate.of(2024, 6, 15)

            val result = useCaseA.compute(adjustedProductive, total, userId, date)

            result.scorePercent.shouldBeNull()
        }
    }

    test("Property 10: Cross-client consistency - independent instances produce identical scores") {
        checkAll(100, Arb.long(0L..86400L), Arb.long(0L..86400L)) { productive, total ->
            val adjustedProductive = if (total == 0L) 0L else productive.coerceAtMost(total)

            val userId = UUID.randomUUID()
            val date = LocalDate.of(2024, 6, 15)

            // Simulate "different client" by computing with independent use case instances
            val androidResult = useCaseA.compute(adjustedProductive, total, userId, date)
            val webResult = useCaseB.compute(adjustedProductive, total, userId, date)

            androidResult.scorePercent shouldBe webResult.scorePercent
            androidResult.productiveSeconds shouldBe webResult.productiveSeconds
            androidResult.totalTrackedSeconds shouldBe webResult.totalTrackedSeconds
        }
    }
})
