package com.dopashift.data.repository

import com.dopashift.data.local.dao.GoalChecklistDao
import com.dopashift.data.local.entity.LocalGoalChecklist
import com.dopashift.domain.entity.GoalChecklistItem
import com.dopashift.domain.repository.GoalChecklistItemRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [GoalChecklistItemRepository].
 * Maps between [LocalGoalChecklist] (Room entity) and [GoalChecklistItem] (domain entity).
 */
@Singleton
class GoalChecklistItemRepositoryImpl @Inject constructor(
    private val dao: GoalChecklistDao
) : GoalChecklistItemRepository {

    override suspend fun findById(id: UUID): GoalChecklistItem? {
        return dao.findById(id.toString())?.toDomain()
    }

    override suspend fun findByGoalId(goalId: UUID): List<GoalChecklistItem> {
        return dao.findByGoalId(goalId.toString()).map { it.toDomain() }
    }

    override suspend fun findByUserId(userId: UUID): List<GoalChecklistItem> {
        return dao.findByUserId(userId.toString()).map { it.toDomain() }
    }

    override suspend fun save(item: GoalChecklistItem): GoalChecklistItem {
        dao.upsert(item.toLocal())
        return item
    }

    override suspend fun delete(id: UUID) {
        dao.deleteById(id.toString())
    }

    override suspend fun deleteByGoalId(goalId: UUID) {
        dao.deleteByGoalId(goalId.toString())
    }

    override suspend fun reassignToGoal(fromGoalId: UUID, toGoalId: UUID) {
        dao.reassignToGoal(fromGoalId.toString(), toGoalId.toString())
    }

    override fun observeByGoalId(goalId: UUID): Flow<List<GoalChecklistItem>> {
        return dao.observeByGoalId(goalId.toString())
            .map { locals -> locals.map { it.toDomain() } }
    }

    override fun observeByUserId(userId: UUID): Flow<List<GoalChecklistItem>> {
        return dao.observeByUserId(userId.toString())
            .map { locals -> locals.map { it.toDomain() } }
    }

    private fun LocalGoalChecklist.toDomain(): GoalChecklistItem {
        return GoalChecklistItem(
            id = UUID.fromString(id),
            goalId = UUID.fromString(goalId),
            userId = UUID.fromString(userId),
            text = text,
            isCompleted = isCompleted,
            createdAt = Instant.ofEpochMilli(createdAt),
            updatedAt = Instant.ofEpochMilli(updatedAt)
        )
    }

    private fun GoalChecklistItem.toLocal(): LocalGoalChecklist {
        return LocalGoalChecklist(
            id = id.toString(),
            goalId = goalId.toString(),
            userId = userId.toString(),
            text = text,
            isCompleted = isCompleted,
            createdAt = createdAt.toEpochMilli(),
            updatedAt = updatedAt.toEpochMilli()
        )
    }
}
