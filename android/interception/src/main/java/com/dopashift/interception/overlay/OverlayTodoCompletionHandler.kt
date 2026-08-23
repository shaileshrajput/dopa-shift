package com.dopashift.interception.overlay

import com.dopashift.domain.entity.ChangeLogEntry
import com.dopashift.domain.entity.DailyTodoItem
import com.dopashift.domain.repository.ChangeLogRepository
import com.dopashift.domain.repository.DailyTodoRepository
import timber.log.Timber
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles todo completion from the intercept overlay, using the same
 * Change_Log pipeline as normal todo updates.
 *
 * Requirements:
 * - 2.10: Mark DailyTodoItem complete from within overlay.
 * - 2.11: Sync via standard Change_Log pipeline.
 * - 4.5: All entity mutations produce Change_Log entries for sync.
 *
 * This handler ensures that completing a todo from the overlay produces
 * an identical Change_Log entry as completing it from the Daily Tasks screen.
 */
@Singleton
class OverlayTodoCompletionHandler @Inject constructor(
    private val dailyTodoRepository: DailyTodoRepository,
    private val changeLogRepository: ChangeLogRepository
) {

    companion object {
        /** Device ID for identifying changes originating from this device. */
        private val DEVICE_ID = UUID.randomUUID().toString()
    }

    /**
     * Completes a todo item and records the change in the Change_Log.
     *
     * @param todoId The ID of the todo to mark as complete.
     * @return The updated [DailyTodoItem], or null if not found.
     */
    suspend fun completeTodo(todoId: UUID): DailyTodoItem? {
        val existing = dailyTodoRepository.findById(todoId)
        if (existing == null) {
            Timber.w("OverlayTodoCompletionHandler: Todo $todoId not found")
            return null
        }

        if (existing.isCompleted) {
            Timber.d("OverlayTodoCompletionHandler: Todo $todoId already completed")
            return existing
        }

        val now = Instant.now()
        val updated = existing.copy(
            isCompleted = true,
            updatedAt = now
        )

        // Persist the updated todo
        dailyTodoRepository.save(updated)

        // Record the change in the Change_Log for sync (same pipeline as normal updates)
        val changeEntry = ChangeLogEntry(
            id = UUID.randomUUID(),
            entityId = todoId,
            entityType = "DailyTodoItem",
            field = "isCompleted",
            value = "true",
            timestamp = now,
            deviceId = DEVICE_ID,
            userId = existing.userId
        )
        changeLogRepository.append(changeEntry)

        Timber.i("OverlayTodoCompletionHandler: Completed todo $todoId via overlay, Change_Log entry appended")
        return updated
    }

    /**
     * Triggers a todo reminder notification when the overlay is displayed.
     * Shows the top 5 pending (incomplete) tasks for today.
     *
     * Requirement 2.11: Trigger todo reminder notification on overlay display.
     *
     * @param userId The user whose pending todos to retrieve.
     * @return List of up to 5 highest-priority pending todos for today.
     */
    suspend fun getTopPendingTodos(userId: UUID, limit: Int = 5): List<DailyTodoItem> {
        val today = java.time.LocalDate.now()
        val allTodos = dailyTodoRepository.findByUserIdAndDate(userId, today)
        return allTodos
            .filter { !it.isCompleted }
            .sortedBy { it.createdAt } // Oldest first = highest priority
            .take(limit)
    }
}
