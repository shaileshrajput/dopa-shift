package com.dopashift.domain.model

/**
 * A single video search result from keyword-based lookup (YouTube Data API fallback).
 */
data class VideoResult(
    val url: String,
    val title: String,
    val source: VideoSource
)

enum class VideoSource {
    LLM,
    YOUTUBE
}
