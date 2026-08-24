package com.dopashift.infrastructure.persistence

import com.dopashift.domain.entity.ConditionType
import com.dopashift.domain.entity.RecurrenceRule
import com.dopashift.domain.entity.RecurrenceType
import com.dopashift.domain.entity.Reminder
import com.dopashift.domain.entity.ReminderCondition
import com.dopashift.domain.entity.ReminderEntityType
import com.dopashift.domain.repository.ReminderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Date
import java.sql.ResultSet
import java.sql.Time
import java.time.DayOfWeek
import java.time.Duration
import java.util.UUID

@Repository
class JdbcReminderRepository(
    private val jdbcTemplate: JdbcTemplate
) : ReminderRepository {

    override suspend fun findById(id: UUID): Reminder? = withContext(Dispatchers.IO) {
        jdbcTemplate.query(
            "SELECT * FROM reminders WHERE id = ?",
            { rs, _ -> mapRow(rs) },
            id
        ).firstOrNull()
    }

    override suspend fun findActiveByUserId(userId: UUID): List<Reminder> = withContext(Dispatchers.IO) {
        jdbcTemplate.query(
            "SELECT * FROM reminders WHERE user_id = ? AND is_active = true ORDER BY scheduled_time",
            { rs, _ -> mapRow(rs) },
            userId
        )
    }

    override suspend fun findByEntityId(entityId: UUID): List<Reminder> = withContext(Dispatchers.IO) {
        jdbcTemplate.query(
            "SELECT * FROM reminders WHERE entity_id = ?",
            { rs, _ -> mapRow(rs) },
            entityId
        )
    }

    override suspend fun save(reminder: Reminder): Reminder = withContext(Dispatchers.IO) {
        val exists = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM reminders WHERE id = ?",
            Int::class.java, reminder.id
        ) ?: 0

        val weekdaysArray = reminder.recurrence?.weekdays?.map { it.value }?.toIntArray()

        if (exists > 0) {
            jdbcTemplate.update(
                """UPDATE reminders SET scheduled_time = ?, scheduled_date = ?, recurrence_type = ?,
                   recurrence_weekdays = ?, recurrence_interval_days = ?, condition_type = ?,
                   escalation_interval_minutes = ?, max_escalations = ?, current_escalation_count = ?,
                   is_active = ?, updated_at = NOW()
                   WHERE id = ?""",
                Time.valueOf(reminder.scheduledTime),
                reminder.scheduledDate?.let { Date.valueOf(it) },
                reminder.recurrence?.type?.name,
                weekdaysArray,
                reminder.recurrence?.intervalDays,
                reminder.condition?.type?.name,
                reminder.escalationInterval.toMinutes().toInt(),
                reminder.maxEscalations,
                reminder.escalationCount,
                reminder.isActive,
                reminder.id
            )
        } else {
            jdbcTemplate.update(
                """INSERT INTO reminders (id, user_id, entity_type, entity_id, scheduled_time, scheduled_date,
                   recurrence_type, recurrence_weekdays, recurrence_interval_days, condition_type,
                   escalation_interval_minutes, max_escalations, current_escalation_count, is_active, created_at, updated_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())""",
                reminder.id, reminder.userId, reminder.entityType.name, reminder.entityId,
                Time.valueOf(reminder.scheduledTime),
                reminder.scheduledDate?.let { Date.valueOf(it) },
                reminder.recurrence?.type?.name,
                weekdaysArray,
                reminder.recurrence?.intervalDays,
                reminder.condition?.type?.name,
                reminder.escalationInterval.toMinutes().toInt(),
                reminder.maxEscalations,
                reminder.escalationCount,
                reminder.isActive
            )
        }
        reminder
    }

    override suspend fun delete(id: UUID): Unit = withContext(Dispatchers.IO) {
        jdbcTemplate.update("DELETE FROM reminders WHERE id = ?", id)
    }

    private fun mapRow(rs: ResultSet): Reminder {
        val recurrenceType = rs.getString("recurrence_type")
        val recurrence = if (recurrenceType != null) {
            val weekdaysRaw = rs.getArray("recurrence_weekdays")
            val weekdays = weekdaysRaw?.let {
                @Suppress("UNCHECKED_CAST")
                (it.array as Array<Int>).map { day -> DayOfWeek.of(day) }.toSet()
            }
            RecurrenceRule(
                type = RecurrenceType.valueOf(recurrenceType),
                weekdays = weekdays,
                intervalDays = rs.getObject("recurrence_interval_days")?.let { (it as Number).toInt() }
            )
        } else null

        val conditionType = rs.getString("condition_type")
        val condition = if (conditionType != null) {
            ReminderCondition(type = ConditionType.valueOf(conditionType))
        } else null

        return Reminder(
            id = rs.getObject("id", UUID::class.java),
            userId = rs.getObject("user_id", UUID::class.java),
            entityType = ReminderEntityType.valueOf(rs.getString("entity_type")),
            entityId = rs.getObject("entity_id", UUID::class.java),
            scheduledTime = rs.getTime("scheduled_time").toLocalTime(),
            scheduledDate = rs.getDate("scheduled_date")?.toLocalDate(),
            recurrence = recurrence,
            condition = condition,
            escalationInterval = Duration.ofMinutes(rs.getInt("escalation_interval_minutes").toLong()),
            maxEscalations = rs.getInt("max_escalations"),
            escalationCount = rs.getInt("current_escalation_count"),
            isActive = rs.getBoolean("is_active")
        )
    }
}
