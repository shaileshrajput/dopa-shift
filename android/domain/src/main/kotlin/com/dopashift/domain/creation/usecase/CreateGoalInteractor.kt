package com.dopashift.domain.creation.usecase

import com.dopashift.domain.creation.CreateGoalCommand
import com.dopashift.domain.creation.CreationResult
import com.dopashift.domain.creation.DeviceIdProvider
import com.dopashift.domain.creation.DomainClock
import com.dopashift.domain.creation.EntityRef
import com.dopashift.domain.creation.GoalValidator
import com.dopashift.domain.creation.UndoToken
import com.dopashift.domain.entity.GoalProfile
import com.dopashift.domain.repository.ChangeLogRepository
import com.dopashift.domain.repository.GoalRepository
import java.util.UUID
import javax.inject.Inject

/**
 * The single implementation of [CreateGoalUseCase], shared verbatim by the Dashboard and
 * the dedicated Goals screen (DQC-1.10). Pure Kotlin: it depends only on domain ports via
 * constructor injection and performs no network call — the repository writes local Room
 * first and the create Change_Log events are enqueued for the Sync_Engine (DQC-2.8, DQC-6.1).
 *
 * Flow:
 * 1. Validate via the shared [GoalValidator], building `existingNamesLower` from the user's
 *    own goals ([GoalRepository.findByUserId]) so uniqueness is case-insensitive and scoped
 *    to the authenticated user (DQC-2.6).
 * 2. On any field error, return [CreationResult.ValidationError] and persist nothing.
 * 3. Otherwise build a [GoalProfile], [GoalRepository.save] it (local-first), append its
 *    create Change_Log events, and return [CreationResult.Success] with an [UndoToken]
 *    referencing the goal.
 */
class CreateGoalInteractor @Inject constructor(
    private val goalRepository: GoalRepository,
    private val changeLogRepository: ChangeLogRepository,
    private val deviceIdProvider: DeviceIdProvider,
    private val clock: DomainClock
) : CreateGoalUseCase {

    private val changeLog = ChangeLogEmitter(changeLogRepository, deviceIdProvider, clock)

    override suspend fun invoke(command: CreateGoalCommand): CreationResult<GoalProfile> {
        val trimmedName = command.name.trim()
        val trimmedCategory = command.category.trim()
        val trimmedKeywords = command.keywords.map { it.trim() }

        val existingNamesLower = goalRepository.findByUserId(command.userId)
            .asSequence()
            .map { it.name.trim().lowercase() }
            .toSet()

        val errors = GoalValidator.validate(
            name = command.name,
            category = command.category,
            keywords = command.keywords,
            existingNamesLower = existingNamesLower
        )
        if (errors.isNotEmpty()) {
            return CreationResult.ValidationError(errors)
        }

        return try {
            val now = clock.now()
            val goal = GoalProfile(
                id = UUID.randomUUID(),
                userId = command.userId,
                name = trimmedName,
                category = trimmedCategory,
                keywords = trimmedKeywords,
                createdAt = now,
                updatedAt = now,
                isActive = true
            )
            val saved = goalRepository.save(goal)

            changeLog.appendCreate(
                entityId = saved.id,
                entityType = ENTITY_TYPE,
                userId = saved.userId,
                fields = buildList {
                    add("name" to saved.name)
                    add("category" to saved.category)
                    add("keywords" to saved.keywords.joinToString(","))
                    add("isActive" to saved.isActive.toString())
                }
            )

            CreationResult.Success(
                value = saved,
                undo = UndoToken(
                    entityRefs = listOf(EntityRef(type = ENTITY_REF_TYPE, id = saved.id)),
                    correlationId = command.correlationId
                )
            )
        } catch (t: Throwable) {
            CreationResult.Failure(t)
        }
    }

    private companion object {
        /** Change_Log `entityType`, matching the domain entity's class name convention. */
        const val ENTITY_TYPE = "GoalProfile"

        /** [EntityRef] logical type for Undo, per the design's token vocabulary. */
        const val ENTITY_REF_TYPE = "goal"
    }
}
