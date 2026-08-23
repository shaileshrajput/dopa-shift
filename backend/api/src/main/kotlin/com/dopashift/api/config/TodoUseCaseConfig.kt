package com.dopashift.api.config

import com.dopashift.domain.repository.DailyTodoRepository
import com.dopashift.domain.usecase.todo.CreateTodoUseCase
import com.dopashift.domain.usecase.todo.DeleteTodoUseCase
import com.dopashift.domain.usecase.todo.MarkTodoCompleteUseCase
import com.dopashift.domain.usecase.todo.UpdateTodoUseCase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Spring configuration for daily todo use case beans.
 * Wires domain use cases with their repository dependencies.
 */
@Configuration
class TodoUseCaseConfig {

    @Bean
    fun createTodoUseCase(dailyTodoRepository: DailyTodoRepository): CreateTodoUseCase {
        return CreateTodoUseCase(dailyTodoRepository)
    }

    @Bean
    fun updateTodoUseCase(dailyTodoRepository: DailyTodoRepository): UpdateTodoUseCase {
        return UpdateTodoUseCase(dailyTodoRepository)
    }

    @Bean
    fun deleteTodoUseCase(dailyTodoRepository: DailyTodoRepository): DeleteTodoUseCase {
        return DeleteTodoUseCase(dailyTodoRepository)
    }

    @Bean
    fun markTodoCompleteUseCase(dailyTodoRepository: DailyTodoRepository): MarkTodoCompleteUseCase {
        return MarkTodoCompleteUseCase(dailyTodoRepository)
    }
}
