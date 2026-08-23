package com.dopashift.domain.repository

import com.dopashift.domain.entity.EfficiencyScore
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.UUID

/**
 * Repository port interface for efficiency score persistence.
 */
interface EfficiencyScoreRepository {
    suspend fun save(score: EfficiencyScore): EfficiencyScore
    suspend fun findByUserIdAndDateRange(userId: UUID, from: LocalDate, to: LocalDate): List<EfficiencyScore>

    /**
     * Observes efficiency scores for a user within a date range as a reactive Flow.
     * Room emits a new list whenever the underlying data changes.
     */
    fun observeByUserIdAndDateRange(userId: UUID, from: LocalDate, to: LocalDate): Flow<List<EfficiencyScore>>
}
