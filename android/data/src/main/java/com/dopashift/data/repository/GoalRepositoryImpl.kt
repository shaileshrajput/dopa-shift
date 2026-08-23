package com.dopashift.data.repository

import com.dopashift.data.local.dao.GoalDao
import com.dopashift.data.local.entity.LocalGoal
import com.dopashift.domain.entity.GoalProfile
import com.dopashift.domain.repository.GoalRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [GoalRepository].
 * Maps between [LocalGoal] (Room entity) and [GoalProfile] (domain entity),
 * handling UUID↔String, Instant↔Long, and JSON array↔List<String> conversions.
 */
@Singleton
class GoalRepositoryImpl @Inject constructor(
    private val goalDao: GoalDao
) : GoalRepository {

    private val moshi: Moshi = Moshi.Builder().build()
    private val stringListAdapter = moshi.adapter<List<String>>(
        Types.newParameterizedType(List::class.java, String::class.java)
    )

    override suspend fun findById(id: UUID): GoalProfile? {
        return goalDao.findById(id.toString())?.toDomain()
    }

    override suspend fun findByUserId(userId: UUID): List<GoalProfile> {
        return goalDao.findByUserId(userId.toString()).map { it.toDomain() }
    }

    override suspend fun findByUserIdAndName(userId: UUID, name: String): GoalProfile? {
        return goalDao.findByUserIdAndName(userId.toString(), name)?.toDomain()
    }

    override suspend fun save(goal: GoalProfile): GoalProfile {
        goalDao.upsert(goal.toLocal())
        return goal
    }

    override suspend fun delete(id: UUID) {
        goalDao.deleteById(id.toString())
    }

    override suspend fun countActiveByUserId(userId: UUID): Int {
        return goalDao.countActiveByUserId(userId.toString())
    }

    override fun observeByUserId(userId: UUID): Flow<List<GoalProfile>> {
        return goalDao.observeByUserId(userId.toString())
            .map { locals -> locals.map { it.toDomain() } }
    }

    private fun LocalGoal.toDomain(): GoalProfile {
        return GoalProfile(
            id = UUID.fromString(id),
            userId = UUID.fromString(userId),
            name = name,
            category = category,
            keywords = parseKeywordsJson(keywords),
            createdAt = Instant.ofEpochMilli(createdAt),
            updatedAt = Instant.ofEpochMilli(updatedAt),
            isActive = isActive
        )
    }

    private fun GoalProfile.toLocal(): LocalGoal {
        return LocalGoal(
            id = id.toString(),
            userId = userId.toString(),
            name = name,
            category = category,
            keywords = stringListAdapter.toJson(keywords),
            isActive = isActive,
            createdAt = createdAt.toEpochMilli(),
            updatedAt = updatedAt.toEpochMilli()
        )
    }

    private fun parseKeywordsJson(json: String): List<String> {
        if (json.isBlank()) return emptyList()
        return stringListAdapter.fromJson(json) ?: emptyList()
    }
}
