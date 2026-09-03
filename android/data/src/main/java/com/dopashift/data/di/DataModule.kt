package com.dopashift.data.di

import android.content.Context
import androidx.room.Room
import com.dopashift.data.local.DopaShiftDatabase
import com.dopashift.data.local.MIGRATION_1_2
import com.dopashift.data.local.MIGRATION_2_3
import com.dopashift.data.local.MIGRATION_3_4
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
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DopaShiftDatabase {
        return Room.databaseBuilder(
            context,
            DopaShiftDatabase::class.java,
            "dopashift.db"
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()
    }

    @Provides
    fun provideGoalDao(db: DopaShiftDatabase): GoalDao = db.goalDao()

    @Provides
    fun provideDailyTodoDao(db: DopaShiftDatabase): DailyTodoDao = db.dailyTodoDao()

    @Provides
    fun provideHabitTrackDao(db: DopaShiftDatabase): HabitTrackDao = db.habitTrackDao()

    @Provides
    fun provideReminderDao(db: DopaShiftDatabase): ReminderDao = db.reminderDao()

    @Provides
    fun provideChangeLogDao(db: DopaShiftDatabase): ChangeLogDao = db.changeLogDao()

    @Provides
    fun provideEfficiencyScoreDao(db: DopaShiftDatabase): EfficiencyScoreDao = db.efficiencyScoreDao()

    @Provides
    fun provideTelemetryDao(db: DopaShiftDatabase): TelemetryDao = db.telemetryDao()

    @Provides
    fun provideSyncStateDao(db: DopaShiftDatabase): SyncStateDao = db.syncStateDao()

    @Provides
    fun provideInterceptionRuleDao(db: DopaShiftDatabase): InterceptionRuleDao = db.interceptionRuleDao()

    @Provides
    fun provideGoalChecklistDao(db: DopaShiftDatabase): GoalChecklistDao = db.goalChecklistDao()

    @Provides
    fun provideDiagnosticEventDao(db: DopaShiftDatabase): DiagnosticEventDao = db.diagnosticEventDao()

    @Provides
    fun provideInterceptActionAuditDao(db: DopaShiftDatabase): InterceptActionAuditDao =
        db.interceptActionAuditDao()
}
