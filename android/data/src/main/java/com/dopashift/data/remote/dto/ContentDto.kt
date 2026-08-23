package com.dopashift.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class VideoRecommendationResponse(
    val videoUrl: String,
    val videoTitle: String?,
    val source: String, // LLM or YOUTUBE
    val goalId: String
)
