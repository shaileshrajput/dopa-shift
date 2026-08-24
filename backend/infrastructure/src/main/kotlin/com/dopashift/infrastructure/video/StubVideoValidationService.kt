package com.dopashift.infrastructure.video

import com.dopashift.domain.model.UserLocale
import com.dopashift.domain.model.VideoResult
import com.dopashift.domain.model.VideoSource
import com.dopashift.domain.model.VideoValidationResult
import com.dopashift.domain.port.VideoValidationService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Stub implementation of VideoValidationService.
 * Will be replaced with a YouTube Data API integration.
 */
@Service
class StubVideoValidationService : VideoValidationService {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun validateVideoUrl(url: String): VideoValidationResult {
        logger.debug("Stub video validation for url={}", url)
        // Stub: validate basic URL format
        val isValid = url.contains("youtube.com") || url.contains("youtu.be")
        return VideoValidationResult(url = url, isValid = isValid, errorReason = if (isValid) null else "URL not recognized")
    }

    override suspend fun searchByKeywords(keywords: List<String>, locale: UserLocale): List<VideoResult> {
        logger.debug("Stub keyword search for keywords={}, locale={}", keywords, locale)
        // TODO: Integrate with YouTube Data API
        return emptyList()
    }
}
