package com.dopashift.domain.model

/**
 * Result of validating a video URL (e.g. checking if a YouTube link is accessible and embeddable).
 */
data class VideoValidationResult(
    val url: String,
    val isValid: Boolean,
    val title: String? = null,
    val errorReason: String? = null
)
