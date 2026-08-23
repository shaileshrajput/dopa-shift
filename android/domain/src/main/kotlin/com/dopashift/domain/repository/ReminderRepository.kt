package com.dopashift.domain.repository

import com.dopashift.domain.entity.Reminder
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Repository port interface for reminder persistence.
 */
interface ReminderRepository {
    suspend fun findById(id: UUID): Reminder?
    suspend fun findActiveByUserId(userId: UUID): List<Reminder>
    suspend fun findByEntityId(entityId: UUID): List<Reminder>
    suspend fun save(reminder: Reminder): Reminder
    suspend fun delete(id: UUID)

    /**
     * Observes active reminders for a user as a reactive Flow.
     * Room emits a new list whenever the underlying data changes.
     */
    fun observeActiveByUserId(userId: UUID): Flow<List<Reminder>>
}
