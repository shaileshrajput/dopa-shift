package com.dopashift.data.repository

import com.dopashift.data.local.dao.EfficiencyScoreDao
import com.dopashift.data.local.entity.LocalEfficiencyScore
import com.dopashift.domain.entity.EfficiencyScore
import com.dopashift.domain.repository.EfficiencyScoreRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [EfficiencyScoreRepository].
 * Maps between [LocalEfficiencyScore] (Room entity) and [EfficiencyScore] (domain entity).
 */
@Singleton
class EfficiencyScoreRepositoryImpl @Inject constructor(
    private val dao: EfficiencyScoreDao
) : EfficiencyScoreRepository {

    override suspend fun save(score: EfficiencyScore): EfficiencyScore {
        dao.upsert(score.toLocal())
        return score
    }

    override suspend fun findByUserIdAndDateRange(
        userId: UUID,
        from: LocalDate,
        to: LocalDate
    ): List<EfficiencyScore> {
        return dao.findByUserIdAndDateRange(userId.toString(), from.toString(), to.toString())
            .map { it.toDomain() }
    }

    override fun observeByUserIdAndDateRange(
        userId: UUID,
        from: LocalDate,
        to: LocalDate
    ): Flow<List<EfficiencyScore>> {
        return dao.observeByUserIdAndDateRange(userId.toString(), from.toString(), to.toString())
            .map { locals -> locals.map { it.toDomain() } }
    }

    private fun LocalEfficiencyScore.toDomain(): EfficiencyScore {
        return EfficiencyScore(
            id = UUID.fromString(id),
            userId = UUID.fromString(userId),
            date = LocalDate.parse(scoreDate),
            productiveSeconds = productiveSeconds,
            totalTrackedSeconds = totalTrackedSeconds,
            scorePercent = scorePercent,
            computedAt = Instant.ofEpochMilli(computedAt)
        )
    }

    private fun EfficiencyScore.toLocal(): LocalEfficiencyScore {
        return LocalEfficiencyScore(
            id = id.toString(),
            userId = userId.toString(),
            scoreDate = date.toString(),
            productiveSeconds = productiveSeconds,
            totalTrackedSeconds = totalTrackedSeconds,
            scorePercent = scorePercent,
            computedAt = computedAt.toEpochMilli()
        )
    }
}
