package com.dopashift.data.di

import com.dopashift.data.repository.ChangeLogRepositoryImpl
import com.dopashift.data.repository.DailyTodoRepositoryImpl
import com.dopashift.data.repository.EfficiencyScoreRepositoryImpl
import com.dopashift.data.repository.GoalChecklistItemRepositoryImpl
import com.dopashift.data.repository.GoalRepositoryImpl
import com.dopashift.data.repository.HabitTrackRepositoryImpl
import com.dopashift.data.repository.ReminderRepositoryImpl
import com.dopashift.data.repository.UserPreferencesRepositoryImpl
import com.dopashift.domain.repository.ChangeLogRepository
import com.dopashift.domain.repository.DailyTodoRepository
import com.dopashift.domain.repository.EfficiencyScoreRepository
import com.dopashift.domain.repository.GoalChecklistItemRepository
import com.dopashift.domain.repository.GoalRepository
import com.dopashift.domain.repository.HabitTrackRepository
import com.dopashift.domain.repository.ReminderRepository
import com.dopashift.domain.repository.UserPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds domain repository interfaces to their concrete implementations.
 * This is the Hexagonal Architecture boundary: domain depends only on
 * interfaces, and the data module provides implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindGoalRepository(impl: GoalRepositoryImpl): GoalRepository

    @Binds
    abstract fun bindDailyTodoRepository(impl: DailyTodoRepositoryImpl): DailyTodoRepository

    @Binds
    abstract fun bindHabitTrackRepository(impl: HabitTrackRepositoryImpl): HabitTrackRepository

    @Binds
    abstract fun bindUserPreferencesRepository(
        impl: UserPreferencesRepositoryImpl
    ): UserPreferencesRepository

    @Binds
    abstract fun bindReminderRepository(impl: ReminderRepositoryImpl): ReminderRepository

    @Binds
    abstract fun bindEfficiencyScoreRepository(
        impl: EfficiencyScoreRepositoryImpl
    ): EfficiencyScoreRepository

    @Binds
    abstract fun bindChangeLogRepository(impl: ChangeLogRepositoryImpl): ChangeLogRepository

    @Binds
    abstract fun bindGoalChecklistItemRepository(
        impl: GoalChecklistItemRepositoryImpl
    ): GoalChecklistItemRepository
}
