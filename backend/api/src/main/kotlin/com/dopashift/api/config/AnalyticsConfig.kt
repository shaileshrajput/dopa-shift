package com.dopashift.api.config

import com.dopashift.domain.usecase.ComputeEfficiencyScoreUseCase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Wires the ComputeEfficiencyScoreUseCase as a Spring bean.
 */
@Configuration
class AnalyticsConfig {

    @Bean
    fun computeEfficiencyScoreUseCase(): ComputeEfficiencyScoreUseCase {
        return ComputeEfficiencyScoreUseCase()
    }
}
