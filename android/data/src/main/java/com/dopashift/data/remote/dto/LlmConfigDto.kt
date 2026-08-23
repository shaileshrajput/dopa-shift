package com.dopashift.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ConfigureLlmRequest(
    val providerType: String, // OPENAI, GEMINI, CLAUDE
    val apiKey: String
)

@JsonClass(generateAdapter = true)
data class LlmConfigResponse(
    val id: String,
    val providerType: String,
    val apiKeyMasked: String,
    val isValidated: Boolean,
    val validatedAt: String?,
    val createdAt: String,
    val updatedAt: String
)

@JsonClass(generateAdapter = true)
data class LlmValidationResponse(
    val isValid: Boolean,
    val message: String?
)
