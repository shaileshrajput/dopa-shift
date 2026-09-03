package com.dopashift.domain.creation.usecase

import com.dopashift.domain.creation.CreateTodoCommand
import com.dopashift.domain.creation.CreationResult
import com.dopashift.domain.creation.DeviceIdProvider
import com.dopashift.domain.creation.DomainClock
import com.dopashift.domain.creation.EntityRef
import com.dopashift.domain.creation.TodoValidator
import com.dopashift.domain.creation.UndoToken
import com.dopashift.domain.entity.DailyTodoItem
import com.dopashift.domain.repository.ChangeLogRepository
import com.dopashift.domain.repository.DailyTodoRepository
import java.util.UUID
import javax.inject.Inject

/**
 * The single implementation of [CreateDailyTodoUseCase], shared verbatim by the Dashboard
 * and the dedicated Daily Tasks screen (DQC-1.10). Pure Kotlin, no network call: the
 * repository writes local Room first and the create Change_Log events are enqueued for the
 * Sync_Engine (DQC-4.2, DQC-4.9).
 *
 * Flow:
 * 1. Count today's items for the target day via [DailyTodoRepository.countByUserIdAndDate]
 *    (scoped to the authenticated user) and validate via the shared [TodoValidator] — blank
 *    rejection, 500-char cap, and the 100/day cap (DQC-4.5, DQC-4.6, DQC-4.7).
 * 2. On any error, return [CreationResult.ValidationError] and persist nothing.
 * 3. Otherwise build a [DailyTodoItem] with no goal association and `dayDate` from the
 *    command, [DailyTodoRepository.save] it, append its create Change_Log events, and return
 *    [CreationResult.Success] with an [UndoToken] referencing the to-do.
 */
class CreateDailyTodoInteractor @Inject constructor(
    private val dailyTodoRepository: DailyTodoRepository,
    private val changeLogRepository: ChangeLogRepository,
    private val deviceIdProvider: DeviceIdProvider,
    private val clock: DomainClock
) : CreateDailyTodoUseCase {

    private val changeLog = ChangeLogEmitter(changeLogRepository, deviceIdProvider, clock)

    override suspend fun invoke(command: CreateTodoCommand): CreationResult<DailyTodoItem> {
        val todayCount = dailyTodoRepository.countByUserIdAndDate(command.userId, command.dayDate)

        val errors = TodoValidator.validate(text = command.text, todayCount = todayCount)
        if (errors.isNotEmpty()) {
            return CreationResult.ValidationError(errors)
        }

        return try {
            val now = clock.now()
            val todo = DailyTodoItem(
                id = UUID.randomUUID(),
                userId = command.userId,
                text = command.text.trim(),
                dueDateTime = command.dueDateTime,
                isCompleted = false,
                createdAt = now,
                updatedAt = now,
                dayDate = command.dayDate
            )
            val saved = dailyTodoRepository.save(todo)

            changeLog.appendCreate(
                entityId = saved.id,
                entityType = ENTITY_TYPE,
                userId = saved.userId,
                fields = buildList {
                    add("text" to saved.text)
                    add("dayDate" to saved.dayDate.toString())
                    add("dueDateTime" to saved.dueDateTime?.toString())
                    add("isCompleted" to saved.isCompleted.toString())
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
        const val ENTITY_TYPE = "DailyTodoItem"

        /** [EntityRef] logical type for Undo, per the design's token vocabulary. */
        const val ENTITY_REF_TYPE = "todo"
    }
}
