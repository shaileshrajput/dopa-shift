package com.dopashift.api.dto

import java.time.Instant

/**
 * Response body for GET /v1/content/video — video recommendations for a goal.
 *
 * Requirements: 3.1, 3.2, 3.3, 3.4, 3.7, 3.8
 */
data class VideoRecommendationResponse(
    val videos: List<VideoItem>,
    val source: String,  // "LLM" or "YOUTUBE"
    val goalId: String,
    val cached: Boolean
)

/**
 * A single video recommendation item.
 */
data class VideoItem(
    val url: String,
    val title: String?,
    val source: String  // "LLM" or "YOUTUBE"
)
