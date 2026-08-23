package com.dopashift.data.repository

import com.dopashift.data.local.dao.ReminderDao
import com.dopashift.data.local.entity.LocalReminder
import com.dopashift.domain.entity.ConditionType
import com.dopashift.domain.entity.RecurrenceRule
import com.dopashift.domain.entity.RecurrenceType
import com.dopashift.domain.entity.Reminder
import com.dopashift.domain.entity.ReminderCondition
import com.dopashift.domain.entity.ReminderEntityType
import com.dopashift.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [ReminderRepository].
 * Maps between [LocalReminder] (Room entity) and [Reminder] (domain entity).
 */
@Singleton
class ReminderRepositoryImpl @Inject constructor(
    private val reminderDao: ReminderDao
) : ReminderRepository {

    override suspend fun findById(id: UUID): Reminder? {
        return reminderDao.findById(id.toString())?.toDomain()
    }

    override suspend fun findActiveByUserId(userId: UUID): List<Reminder> {
        return reminderDao.findActiveByUserId(userId.toString()).map { it.toDomain() }
    }

    override suspend fun findByEntityId(entityId: UUID): List<Reminder> {
        return reminderDao.findByEntityId(entityId.toString()).map { it.toDomain() }
    }

    override suspend fun save(reminder: Reminder): Reminder {
        reminderDao.upsert(reminder.toLocal())
        return reminder
    }

    override suspend fun delete(id: UUID) {
        reminderDao.deleteById(id.toString())
    }

    override fun observeActiveByUserId(userId: UUID): Flow<List<Reminder>> {
        return reminderDao.observeActiveByUserId(userId.toString())
            .map { locals -> locals.map { it.toDomain() } }
    }

    // === Mapping Functions ===

    private fun LocalReminder.toDomain(): Reminder {
        return Reminder(
            id = UUID.fromString(id),
            userId = UUID.fromString(userId),
            entityType = ReminderEntityType.valueOf(entityType),
            entityId = UUID.fromString(entityId),
            scheduledTime = LocalTime.parse(scheduledTime),
            scheduledDate = scheduledDate?.let { LocalDate.parse(it) },
            recurrence = recurrenceJson?.let { parseRecurrenceJson(it) },
            condition = conditionType?.let { ReminderCondition(type = ConditionType.valueOf(it)) },
            escalationInterval = Duration.ofMinutes(escalationIntervalMinutes.toLong()),
            maxEscalations = maxEscalations,
            escalationCount = currentEscalationCount,
            isActive = isActive
        )
    }

    private fun Reminder.toLocal(): LocalReminder {
        val now = Instant.now().toEpochMilli()
        return LocalReminder(
            id = id.toString(),
            userId = userId.toString(),
            entityType = entityType.name,
            entityId = entityId.toString(),
            scheduledTime = scheduledTime.toString(),
            scheduledDate = scheduledDate?.toString(),
            recurrenceJson = recurrence?.let { toRecurrenceJson(it) },
            conditionType = condition?.type?.name,
            escalationIntervalMinutes = escalationInterval.toMinutes().toInt(),
            maxEscalations = maxEscalations,
            currentEscalationCount = escalationCount,
            isActive = isActive,
            createdAt = now,
            updatedAt = now
        )
    }

    private fun parseRecurrenceJson(json: String): RecurrenceRule {
        val obj = JSONObject(json)
        val type = RecurrenceType.valueOf(obj.getString("type"))
        val weekdays = if (obj.has("weekdays") && !obj.isNull("weekdays")) {
            val arr = obj.getJSONArray("weekdays")
            (0 until arr.length()).map { DayOfWeek.valueOf(arr.getString(it)) }.toSet()
        } else {
            null
        }
        val intervalDays = if (obj.has("intervalDays") && !obj.isNull("intervalDays")) {
            obj.getInt("intervalDays")
        } else {
            null
        }
        return RecurrenceRule(type = type, weekdays = weekdays, intervalDays = intervalDays)
    }

    private fun toRecurrenceJson(rule: RecurrenceRule): String {
        val obj = JSONObject()
        obj.put("type", rule.type.name)
        rule.weekdays?.let { days ->
            val arr = JSONArray()
            days.forEach { arr.put(it.name) }
            obj.put("weekdays", arr)
        }
        rule.intervalDays?.let { obj.put("intervalDays", it) }
        return obj.toString()
    }
}
