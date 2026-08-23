package com.dopashift.interception.reminder.di

import com.dopashift.data.repository.LocalQuietHoursProvider
import com.dopashift.data.repository.RoomEntityCompletionChecker
import com.dopashift.domain.port.EntityCompletionChecker
import com.dopashift.domain.port.QuietHoursProvider
import com.dopashift.domain.service.ReminderEvaluationService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/**
 * Hilt module providing reminder engine dependencies.
 *
 * Provides:
 * - [Clock] — system clock for production, injectable for tests
 * - [ReminderEvaluationService] — the domain evaluation logic
 * - Bindings for [EntityCompletionChecker] and [QuietHoursProvider]
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ReminderModule {

    @Binds
    @Singleton
    abstract fun bindEntityCompletionChecker(
        impl: RoomEntityCompletionChecker
    ): EntityCompletionChecker

    @Binds
    @Singleton
    abstract fun bindQuietHoursProvider(
        impl: LocalQuietHoursProvider
    ): QuietHoursProvider

    companion object {

        @Provides
        @Singleton
        fun provideClock(): Clock = Clock.systemDefaultZone()

        @Provides
        @Singleton
        fun provideReminderEvaluationService(
            clock: Clock,
            entityCompletionChecker: EntityCompletionChecker
        ): ReminderEvaluationService {
            return ReminderEvaluationService(clock, entityCompletionChecker)
        }
    }
}
