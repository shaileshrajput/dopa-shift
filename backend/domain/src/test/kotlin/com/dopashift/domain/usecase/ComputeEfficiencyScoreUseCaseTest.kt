package com.dopashift.domain.usecase

import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ComputeEfficiencyScoreUseCaseTest {

    private val useCase = ComputeEfficiencyScoreUseCase()
    private val userId = UUID.randomUUID()
    private val date = LocalDate.of(2024, 6, 15)

    @Test
    fun `returns null scorePercent when total tracked seconds is below threshold`() {
        val result = useCase.compute(
            productiveSeconds = 30,
            totalTrackedSeconds = 59,
            userId = userId,
            date = date
        )

        assertNull(result.scorePercent)
        assertEquals(30L, result.productiveSeconds)
        assertEquals(59L, result.totalTrackedSeconds)
    }

    @Test
    fun `returns null scorePercent when total tracked seconds is zero`() {
        val result = useCase.compute(
            productiveSeconds = 0,
            totalTrackedSeconds = 0,
            userId = userId,
            date = date
        )

        assertNull(result.scorePercent)
    }

    @Test
    fun `computes score at exactly 60 seconds threshold`() {
        val result = useCase.compute(
            productiveSeconds = 30,
            totalTrackedSeconds = 60,
            userId = userId,
            date = date
        )

        assertEquals(50, result.scorePercent)
    }

    @Test
    fun `computes 100 percent when all time is productive`() {
        val result = useCase.compute(
            productiveSeconds = 3600,
            totalTrackedSeconds = 3600,
            userId = userId,
            date = date
        )

        assertEquals(100, result.scorePercent)
    }

    @Test
    fun `computes 0 percent when no time is productive`() {
        val result = useCase.compute(
            productiveSeconds = 0,
            totalTrackedSeconds = 3600,
            userId = userId,
            date = date
        )

        assertEquals(0, result.scorePercent)
    }

    @Test
    fun `rounds to nearest integer`() {
        // 33 / 100 = 33% (exactly)
        val result33 = useCase.compute(
            productiveSeconds = 33,
            totalTrackedSeconds = 100,
            userId = userId,
            date = date
        )
        assertEquals(33, result33.scorePercent)

        // 1 / 3 = 33.33...% -> rounds to 33
        val result33b = useCase.compute(
            productiveSeconds = 100,
            totalTrackedSeconds = 300,
            userId = userId,
            date = date
        )
        assertEquals(33, result33b.scorePercent)

        // 2 / 3 = 66.66...% -> rounds to 67
        val result67 = useCase.compute(
            productiveSeconds = 200,
            totalTrackedSeconds = 300,
            userId = userId,
            date = date
        )
        assertEquals(67, result67.scorePercent)
    }

    @Test
    fun `clamps score to 100 when productive exceeds total`() {
        // Edge case: productiveSeconds > totalTrackedSeconds
        // This shouldn't normally happen but the use case handles it gracefully
        val result = useCase.compute(
            productiveSeconds = 200,
            totalTrackedSeconds = 100,
            userId = userId,
            date = date
        )

        assertEquals(100, result.scorePercent)
    }

    @Test
    fun `preserves userId and date in returned entity`() {
        val result = useCase.compute(
            productiveSeconds = 500,
            totalTrackedSeconds = 1000,
            userId = userId,
            date = date
        )

        assertEquals(userId, result.userId)
        assertEquals(date, result.date)
    }

    @Test
    fun `computation is deterministic - same inputs produce same scorePercent`() {
        val result1 = useCase.compute(
            productiveSeconds = 1234,
            totalTrackedSeconds = 5678,
            userId = userId,
            date = date
        )
        val result2 = useCase.compute(
            productiveSeconds = 1234,
            totalTrackedSeconds = 5678,
            userId = userId,
            date = date
        )

        assertEquals(result1.scorePercent, result2.scorePercent)
        assertEquals(result1.productiveSeconds, result2.productiveSeconds)
        assertEquals(result1.totalTrackedSeconds, result2.totalTrackedSeconds)
    }

    @Test
    fun `throws on negative productiveSeconds`() {
        assertFailsWith<IllegalArgumentException> {
            useCase.compute(
                productiveSeconds = -1,
                totalTrackedSeconds = 100,
                userId = userId,
                date = date
            )
        }
    }

    @Test
    fun `throws on negative totalTrackedSeconds`() {
        assertFailsWith<IllegalArgumentException> {
            useCase.compute(
                productiveSeconds = 0,
                totalTrackedSeconds = -1,
                userId = userId,
                date = date
            )
        }
    }
}
