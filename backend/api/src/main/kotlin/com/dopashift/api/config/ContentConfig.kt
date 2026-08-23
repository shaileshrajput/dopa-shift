package com.dopashift.api.config

import com.dopashift.domain.port.LlmProviderService
import com.dopashift.domain.port.VideoValidationService
import com.dopashift.domain.repository.GoalRepository
import com.dopashift.domain.repository.VideoCacheRepository
import com.dopashift.domain.usecase.VideoRecommendationUseCase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Spring configuration for content recommendation use case beans.
 * Wires the VideoRecommendationUseCase with its dependencies.
 *
 * LlmProviderService is optional (nullable) — if no bean exists,
 * the use case will always fallback to YouTube keyword search.
 */
@Configuration
class ContentConfig {

    @Bean
    fun videoRecommendationUseCase(
        goalRepository: GoalRepository,
        llmProviderService: LlmProviderService?,
        videoValidationService: VideoValidationService,
        videoCacheRepository: VideoCacheRepository
    ): VideoRecommendationUseCase {
        return VideoRecommendationUseCase(
            goalRepository = goalRepository,
            llmProviderService = llmProviderService,
            videoValidationService = videoValidationService,
            videoCacheRepository = videoCacheRepository
        )
    }
}
