package com.dopashift.domain.model

/**
 * Response from the LLM provider containing video suggestions for a goal.
 */
data class LlmVideoResponse(
    val videoUrls: List<String>,
    val rawResponse: String
)
