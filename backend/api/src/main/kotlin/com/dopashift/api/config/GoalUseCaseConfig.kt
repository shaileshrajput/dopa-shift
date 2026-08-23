package com.dopashift.api.config

import com.dopashift.domain.repository.GoalChecklistItemRepository
import com.dopashift.domain.repository.GoalRepository
import com.dopashift.domain.repository.HabitTrackRepository
import com.dopashift.domain.repository.InterceptionRuleRepository
import com.dopashift.domain.usecase.goal.CreateGoalUseCase
import com.dopashift.domain.usecase.goal.DeleteGoalUseCase
import com.dopashift.domain.usecase.goal.UpdateGoalUseCase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Spring configuration for goal management use case beans.
 * Wires domain use cases with their repository dependencies.
 */
@Configuration
class GoalUseCaseConfig {

    @Bean
    fun createGoalUseCase(goalRepository: GoalRepository): CreateGoalUseCase {
        return CreateGoalUseCase(goalRepository)
    }

    @Bean
    fun updateGoalUseCase(goalRepository: GoalRepository): UpdateGoalUseCase {
        return UpdateGoalUseCase(goalRepository)
    }

    @Bean
    fun deleteGoalUseCase(
        goalRepository: GoalRepository,
        goalChecklistItemRepository: GoalChecklistItemRepository,
        habitTrackRepository: HabitTrackRepository,
        interceptionRuleRepository: InterceptionRuleRepository
    ): DeleteGoalUseCase {
        return DeleteGoalUseCase(
            goalRepository = goalRepository,
            goalChecklistItemRepository = goalChecklistItemRepository,
            habitTrackRepository = habitTrackRepository,
            interceptionRuleRepository = interceptionRuleRepository
        )
    }
}
