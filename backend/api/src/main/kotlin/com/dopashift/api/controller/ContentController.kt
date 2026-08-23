package com.dopashift.api.controller

import com.dopashift.api.config.AuthenticatedUser
import com.dopashift.api.dto.VideoItem
import com.dopashift.api.dto.VideoRecommendationResponse
import com.dopashift.domain.model.UserLocale
import com.dopashift.domain.repository.LlmConfigRepository
import com.dopashift.domain.usecase.VideoRecommendationUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * REST controller for content/video recommendations.
 *
 * Implements the LLM + YouTube fallback pipeline:
 * - If user has LLM configured: call LLM → extract URLs → validate → serve
 * - If LLM response has no valid links: fallback to YouTube keyword search
 * - If no LLM configured: YouTube keyword search as sole source
 * - Results cached per keyword-set hash (24h TTL)
 * - relevanceLanguage/regionCode derived from user locale
 *
 * Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.7, 3.8
 */
@RestController
@RequestMapping("/v1/content")
class ContentController(
    private val videoRecommendationUseCase: VideoRecommendationUseCase,
    private val llmConfigRepository: LlmConfigRepository,
    private val authenticatedUser: AuthenticatedUser
) {

    /**
     * GET /v1/content/video?goalId={id}
     *
     * Returns video recommendations for the specified goal using the
     * LLM + YouTube fallback pipeline.
     *
     * - Checks if user has an LLM provider configured
     * - Derives user locale from JWT 'locale' claim (defaults to EN)
     * - Delegates to VideoRecommendationUseCase for pipeline execution
     *
     * Returns 200 OK with video recommendations.
     * Returns 404 if goal not found or belongs to another user.
     */
    @GetMapping("/video")
    suspend fun getVideoRecommendations(
        @RequestParam goalId: UUID
    ): ResponseEntity<VideoRecommendationResponse> {
        val userId = authenticatedUser.getUserId()

        // Determine if user has LLM configured
        val llmConfig = llmConfigRepository.findByUserId(userId)
        val llmConfigured = llmConfig != null && llmConfig.isValidated

        // Derive locale from JWT claims, fallback to EN
        val locale = resolveUserLocale()

        val result = videoRecommendationUseCase.execute(
            goalId = goalId,
            userId = userId,
            locale = locale,
            llmConfigured = llmConfigured
        )

        val response = VideoRecommendationResponse(
            videos = result.videos.map { video ->
                VideoItem(
                    url = video.url,
                    title = video.title,
                    source = video.source.name
                )
            },
            source = result.source.name,
            goalId = goalId.toString(),
            cached = result.cached
        )

        return ResponseEntity.ok(response)
    }

    /**
     * Resolve user locale from JWT claims.
     * Falls back to EN if locale claim is missing or unsupported.
     *
     * Requirement 3.5: Pass relevanceLanguage/regionCode from user locale.
     * If locale not supported by YouTube API, default to 'en' and omit regionCode.
     */
    private fun resolveUserLocale(): UserLocale {
        val jwt = authenticatedUser.getJwt()
        val localeClaim = jwt.getClaimAsString("locale")
            ?: return UserLocale.EN

        return when (localeClaim.lowercase().take(2)) {
            "hi" -> UserLocale.HI
            "mr" -> UserLocale.MR
            "en" -> UserLocale.EN
            else -> UserLocale.EN
        }
    }
}
