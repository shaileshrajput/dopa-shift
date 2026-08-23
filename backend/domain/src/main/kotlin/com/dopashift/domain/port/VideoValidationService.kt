package com.dopashift.domain.port

import com.dopashift.domain.model.UserLocale
import com.dopashift.domain.model.VideoResult
import com.dopashift.domain.model.VideoValidationResult

/**
 * Domain port for video URL validation and keyword-based search.
 * Used in the video recommendation pipeline to verify LLM-suggested URLs
 * and fall back to keyword search when necessary.
 */
interface VideoValidationService {
    /**
     * Validate a video URL (e.g. check YouTube accessibility and embeddability).
     */
    suspend fun validateVideoUrl(url: String): VideoValidationResult

    /**
     * Search for videos by keywords, respecting the user's locale for
     * relevanceLanguage and regionCode.
     */
    suspend fun searchByKeywords(keywords: List<String>, locale: UserLocale): List<VideoResult>
}
