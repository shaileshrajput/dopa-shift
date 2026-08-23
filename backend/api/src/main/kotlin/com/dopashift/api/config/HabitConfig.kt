package com.dopashift.api.config

import com.dopashift.domain.repository.GoalRepository
import com.dopashift.domain.repository.HabitTrackRepository
import com.dopashift.domain.usecase.habit.ActivateHabitTrackUseCase
import com.dopashift.domain.usecase.habit.AdvanceDayUseCase
import com.dopashift.domain.usecase.habit.MarkCheckpointUseCase
import com.dopashift.domain.usecase.habit.RecordMissedDayUseCase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Spring configuration for habit track use case beans.
 * Wires domain use cases with their repository dependencies.
 */
@Configuration
class HabitConfig {

    @Bean
    fun activateHabitTrackUseCase(
        goalRepository: GoalRepository,
        habitTrackRepository: HabitTrackRepository
    ): ActivateHabitTrackUseCase {
        return ActivateHabitTrackUseCase(
            goalRepository = goalRepository,
            habitTrackRepository = habitTrackRepository
        )
    }

    @Bean
    fun markCheckpointUseCase(
        habitTrackRepository: HabitTrackRepository
    ): MarkCheckpointUseCase {
        return MarkCheckpointUseCase(
            habitTrackRepository = habitTrackRepository
        )
    }

    @Bean
    fun advanceDayUseCase(
        habitTrackRepository: HabitTrackRepository
    ): AdvanceDayUseCase {
        return AdvanceDayUseCase(
            habitTrackRepository = habitTrackRepository
        )
    }

    @Bean
    fun recordMissedDayUseCase(
        habitTrackRepository: HabitTrackRepository
    ): RecordMissedDayUseCase {
        return RecordMissedDayUseCase(
            habitTrackRepository = habitTrackRepository
        )
    }
}
