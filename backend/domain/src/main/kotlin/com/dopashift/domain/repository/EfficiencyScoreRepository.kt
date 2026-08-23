package com.dopashift.domain.repository

import com.dopashift.domain.entity.EfficiencyScore
import java.time.LocalDate
import java.util.UUID

/**
 * Port interface for efficiency score persistence and retrieval.
 * Implementations live in the infrastructure layer.
 */
interface EfficiencyScoreRepository {
    suspend fun save(score: EfficiencyScore): EfficiencyScore
    suspend fun findByUserIdAndDateRange(userId: UUID, from: LocalDate, to: LocalDate): List<EfficiencyScore>
}
