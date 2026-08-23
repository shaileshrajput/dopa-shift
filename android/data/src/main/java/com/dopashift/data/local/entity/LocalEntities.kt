package com.dopashift.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "goals",
    indices = [Index("userId")]
)
data class LocalGoal(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val category: String,
    val keywords: String, // JSON array
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "daily_todo_items",
    indices = [Index("userId", "dayDate")]
)
data class LocalDailyTodo(
    @PrimaryKey val id: String,
    val userId: String,
    val text: String,
    val dueDateTime: Long?,
    val isCompleted: Boolean = false,
    val dayDate: String, // ISO LocalDate
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "goal_checklist_items",
    indices = [Index("goalId"), Index("userId")]
)
data class LocalGoalChecklist(
    @PrimaryKey val id: String,
    val goalId: String,
    val userId: String,
    val text: String,
    val isCompleted: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "habit_tracks",
    indices = [Index("goalId"), Index("userId")]
)
data class LocalHabitTrack(
    @PrimaryKey val id: String,
    val goalId: String,
    val userId: String,
    val startDate: String, // ISO LocalDate
    val currentDay: Int = 1,
    val isFinished: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "habit_checkpoints",
    indices = [Index("habitTrackId")]
)
data class LocalHabitCheckpoint(
    @PrimaryKey val id: String,
    val habitTrackId: String,
    val dayNumber: Int,
    val description: String,
    val status: String, // PENDING, COMPLETED, MISSED
    val completedAt: Long?
)

@Entity(
    tableName = "reminders",
    indices = [Index("userId"), Index("entityId")]
)
data class LocalReminder(
    @PrimaryKey val id: String,
    val userId: String,
    val entityType: String,
    val entityId: String,
    val scheduledTime: String, // ISO LocalTime
    val scheduledDate: String?, // ISO LocalDate, nullable for repeating
    val recurrenceJson: String?, // JSON of RecurrenceRule
    val conditionType: String?,
    val escalationIntervalMinutes: Int,
    val maxEscalations: Int,
    val currentEscalationCount: Int = 0,
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "change_log",
    indices = [Index("userId", "timestamp"), Index("entityId")]
)
data class LocalChangeLogEntry(
    @PrimaryKey val id: String,
    val entityId: String,
    val entityType: String,
    val field: String,
    val value: String?, // JSON-encoded field value, null = deletion
    val timestamp: Long,
    val deviceId: String,
    val userId: String,
    val isSynced: Boolean = false
)

@Entity(
    tableName = "efficiency_scores",
    indices = [Index("userId", "scoreDate")]
)
data class LocalEfficiencyScore(
    @PrimaryKey val id: String,
    val userId: String,
    val scoreDate: String, // ISO LocalDate
    val productiveSeconds: Long,
    val totalTrackedSeconds: Long,
    val scorePercent: Int?, // null = unavailable
    val computedAt: Long
)

@Entity(
    tableName = "interception_rules",
    indices = [Index("userId")]
)
data class LocalInterceptionRule(
    @PrimaryKey val id: String,
    val userId: String,
    val goalId: String?,
    val appPackageName: String?,
    val siteDomain: String?,
    val dailyAllowanceMinutes: Int,
    val isActive: Boolean = true,
    val createdAt: Long
)

@Entity(tableName = "telemetry_events")
data class LocalTelemetryEvent(
    @PrimaryKey val id: String,
    val appPackageName: String,
    val foregroundSeconds: Long,
    val date: String, // ISO LocalDate
    val recordedAt: Long
)

@Entity(tableName = "sync_state")
data class LocalSyncState(
    @PrimaryKey val key: String,
    val lastSyncTimestamp: Long
)
