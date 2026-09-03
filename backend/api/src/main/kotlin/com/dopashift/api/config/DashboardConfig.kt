package com.dopashift.api.config

import com.dopashift.application.dashboard.DashboardSummaryQueryService
import com.dopashift.domain.repository.DailyTodoRepository
import com.dopashift.domain.repository.GoalRepository
import com.dopashift.domain.repository.HabitTrackRepository
import com.dopashift.domain.repository.UserProfileRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * Wires the application-layer Dashboard query services as Spring beans.
 */
@Configuration
class DashboardConfig {

    @Bean
    fun dashboardSummaryQueryService(
        goalRepository: GoalRepository,
        dailyTodoRepository: DailyTodoRepository,
        habitTrackRepository: HabitTrackRepository,
        userProfileRepository: UserProfileRepository,
        clock: Clock
    ): DashboardSummaryQueryService =
        DashboardSummaryQueryService(
            goalRepository = goalRepository,
            dailyTodoRepository = dailyTodoRepository,
            habitTrackRepository = habitTrackRepository,
            userProfileRepository = userProfileRepository,
            clock = clock
        )
}
