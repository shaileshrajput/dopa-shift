package com.dopashift.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.dopashift.data.local.dao.ChangeLogDao
import com.dopashift.data.local.dao.DailyTodoDao
import com.dopashift.data.local.dao.DiagnosticEventDao
import com.dopashift.data.local.dao.EfficiencyScoreDao
import com.dopashift.data.local.dao.GoalChecklistDao
import com.dopashift.data.local.dao.GoalDao
import com.dopashift.data.local.dao.HabitTrackDao
import com.dopashift.data.local.dao.InterceptActionAuditDao
import com.dopashift.data.local.dao.InterceptionRuleDao
import com.dopashift.data.local.dao.ReminderDao
import com.dopashift.data.local.dao.SyncStateDao
import com.dopashift.data.local.dao.TelemetryDao
import com.dopashift.data.local.entity.LocalChangeLogEntry
import com.dopashift.data.local.entity.LocalDailyTodo
import com.dopashift.data.local.entity.LocalDiagnosticEvent
import com.dopashift.data.local.entity.LocalEfficiencyScore
import com.dopashift.data.local.entity.LocalGoal
import com.dopashift.data.local.entity.LocalGoalChecklist
import com.dopashift.data.local.entity.LocalHabitCheckpoint
import com.dopashift.data.local.entity.LocalHabitTrack
import com.dopashift.data.local.entity.LocalInterceptActionAudit
import com.dopashift.data.local.entity.LocalInterceptionRule
import com.dopashift.data.local.entity.LocalReminder
import com.dopashift.data.local.entity.LocalSyncState
import com.dopashift.data.local.entity.LocalTelemetryEvent

@Database(
    entities = [
        LocalGoal::class,
        LocalDailyTodo::class,
        LocalGoalChecklist::class,
        LocalHabitTrack::class,
        LocalHabitCheckpoint::class,
        LocalReminder::class,
        LocalChangeLogEntry::class,
        LocalEfficiencyScore::class,
        LocalInterceptionRule::class,
        LocalTelemetryEvent::class,
        LocalSyncState::class,
        LocalDiagnosticEvent::class,
        LocalInterceptActionAudit::class
    ],
    version = 4,
    exportSchema = true
)
abstract class DopaShiftDatabase : RoomDatabase() {
    abstract fun goalDao(): GoalDao
    abstract fun dailyTodoDao(): DailyTodoDao
    abstract fun habitTrackDao(): HabitTrackDao
    abstract fun reminderDao(): ReminderDao
    abstract fun changeLogDao(): ChangeLogDao
    abstract fun efficiencyScoreDao(): EfficiencyScoreDao
    abstract fun telemetryDao(): TelemetryDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun interceptionRuleDao(): InterceptionRuleDao
    abstract fun goalChecklistDao(): GoalChecklistDao
    abstract fun diagnosticEventDao(): DiagnosticEventDao
    abstract fun interceptActionAuditDao(): InterceptActionAuditDao
}
