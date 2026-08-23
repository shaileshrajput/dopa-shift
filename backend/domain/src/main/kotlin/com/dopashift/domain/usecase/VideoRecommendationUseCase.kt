package com.dopashift.domain.usecase

import com.dopashift.domain.exception.GoalNotFoundException
import com.dopashift.domain.model.UserLocale
import com.dopashift.domain.model.VideoResult
import com.dopashift.domain.model.VideoSource
import com.dopashift.domain.port.LlmProviderService
import com.dopashift.domain.port.VideoValidationService
import com.dopashift.domain.repository.GoalRepository
import com.dopashift.domain.repository.VideoCacheRepository
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Orchestrates the video recommendation pipeline:
 * 1. Check cache by keyword-set hash (24h TTL)
 * 2. If miss: call user's LLM with goal name + keywords (privacy: only these fields sent)
 * 3. Extract YouTube URLs from LLM response, validate via YouTube Data API
 * 4. If no valid LLM links: fallback to YouTube keyword search
 * 5. If no LLM configured: YouTube keyword search as sole source
 * 6. Cache results per keyword-set hash
 * 7. Pass relevanceLanguage/regionCode from user locale
 *
 * Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.7, 3.8
 */
class VideoRecommendationUseCase(
    private val goalRepository: GoalRepository,
    private val llmProviderService: LlmProviderService?,
    private val videoValidationService: VideoValidationService,
    private val videoCacheRepository: VideoCacheRepository
) {

    companion object {
        private val CACHE_TTL: Duration = Duration.ofHours(24)
        private const val MAX_LLM_URLS = 3
        private val YOUTUBE_URL_PATTERN = Regex(
            """(?:https?://)?(?:www\.)?(?:youtube\.com/watch\?v=|youtu\.be/|youtube\.com/embed/)([a-zA-Z0-9_-]{11})"""
        )
    }

    /**
     * Get video recommendations for the given goal.
     *
     * @param goalId the goal to get recommendations for
     * @param userId the authenticated user (for ownership validation)
     * @param locale the user's locale for YouTube relevanceLanguage/regionCode
     * @param llmConfigured whether the user has an LLM provider configured
     * @return VideoRecommendationResult containing videos, source info, and cache status
     */
    suspend fun execute(
        goalId: UUID,
        userId: UUID,
        locale: UserLocale,
        llmConfigured: Boolean
    ): VideoRecommendationResult {
        // 1. Fetch goal and validate ownership
        val goal = goalRepository.findById(goalId)
            ?: throw GoalNotFoundException(goalId.toString())
        if (goal.userId != userId) {
            throw GoalNotFoundException(goalId.toString())
        }

        val keywords = goal.keywords
        val keywordSetHash = computeKeywordSetHash(keywords)

        // 2. Check cache
        val cachedVideos = videoCacheRepository.findByKeywordSetHash(keywordSetHash)
        if (cachedVideos != null && cachedVideos.isNotEmpty()) {
            val primarySource = cachedVideos.first().source
            return VideoRecommendationResult(
                videos = cachedVideos,
                source = primarySource,
                cached = true
            )
        }

        // 3. Pipeline: LLM → validate → fallback → YouTube search
        val videos: List<VideoResult>
        val source: VideoSource

        if (llmConfigured && llmProviderService != null) {
            // Req 3.1: Call LLM with goal name + keywords only (privacy boundary)
            val llmResponse = try {
                llmProviderService.requestVideoSuggestions(goal.name, keywords)
            } catch (_: Exception) {
                null
            }

            if (llmResponse != null) {
                // Req 3.2: Extract up to 3 YouTube URLs from LLM response
                val extractedUrls = extractYouTubeUrls(llmResponse.rawResponse)
                    .take(MAX_LLM_URLS)

                if (extractedUrls.isNotEmpty()) {
                    // Validate extracted URLs via YouTube Data API
                    val validatedVideos = validateUrls(extractedUrls)
                    if (validatedVideos.isNotEmpty()) {
                        videos = validatedVideos
                        source = VideoSource.LLM
                    } else {
                        // Req 3.3: All extracted links fail validation → fallback
                        videos = youtubeKeywordSearch(keywords, locale)
                        source = VideoSource.YOUTUBE
                    }
                } else {
                    // Req 3.3: No parseable video links in LLM response → fallback
                    videos = youtubeKeywordSearch(keywords, locale)
                    source = VideoSource.YOUTUBE
                }
            } else {
                // LLM call failed → fallback to YouTube search
                videos = youtubeKeywordSearch(keywords, locale)
                source = VideoSource.YOUTUBE
            }
        } else {
            // Req 3.4: No LLM configured → YouTube keyword search as sole source
            videos = youtubeKeywordSearch(keywords, locale)
            source = VideoSource.YOUTUBE
        }

        // 4. Cache results (Req 3.7: 24h TTL)
        if (videos.isNotEmpty()) {
            videoCacheRepository.save(
                keywordSetHash = keywordSetHash,
                goalId = goalId,
                videos = videos,
                expiresAt = Instant.now().plus(CACHE_TTL)
            )
        }

        return VideoRecommendationResult(
            videos = videos,
            source = source,
            cached = false
        )
    }

    /**
     * Extract YouTube URLs from raw LLM response text.
     */
    private fun extractYouTubeUrls(text: String): List<String> {
        return YOUTUBE_URL_PATTERN.findAll(text)
            .map { matchResult ->
                val videoId = matchResult.groupValues[1]
                "https://www.youtube.com/watch?v=$videoId"
            }
            .distinct()
            .toList()
    }

    /**
     * Validate a list of YouTube URLs via the YouTube Data API.
     * Returns only valid, embeddable videos.
     */
    private suspend fun validateUrls(urls: List<String>): List<VideoResult> {
        return urls.mapNotNull { url ->
            val result = try {
                videoValidationService.validateVideoUrl(url)
            } catch (_: Exception) {
                null
            }
            if (result != null && result.isValid) {
                VideoResult(
                    url = result.url,
                    title = result.title ?: "",
                    source = VideoSource.LLM
                )
            } else {
                null
            }
        }
    }

    /**
     * Fallback: YouTube keyword search with user locale for relevanceLanguage/regionCode.
     * Req 3.5: Pass relevanceLanguage and regionCode from user locale.
     */
    private suspend fun youtubeKeywordSearch(
        keywords: List<String>,
        locale: UserLocale
    ): List<VideoResult> {
        return try {
            videoValidationService.searchByKeywords(keywords, locale)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Compute SHA-256 hash of sorted keywords for cache key.
     */
    private fun computeKeywordSetHash(keywords: List<String>): String {
        val sorted = keywords.map { it.lowercase().trim() }.sorted()
        val joined = sorted.joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(joined.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}

/**
 * Result from the video recommendation pipeline.
 */
data class VideoRecommendationResult(
    val videos: List<VideoResult>,
    val source: VideoSource,
    val cached: Boolean
)
