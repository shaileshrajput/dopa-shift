package com.dopashift.domain.repository

import com.dopashift.domain.entity.Reminder
import java.util.UUID

/**
 * Port interface for reminder persistence.
 * Implementations live in the infrastructure layer.
 */
interface ReminderRepository {
    suspend fun findById(id: UUID): Reminder?
    suspend fun findActiveByUserId(userId: UUID): List<Reminder>
    suspend fun findByEntityId(entityId: UUID): List<Reminder>
    suspend fun save(reminder: Reminder): Reminder
    suspend fun delete(id: UUID)
}
