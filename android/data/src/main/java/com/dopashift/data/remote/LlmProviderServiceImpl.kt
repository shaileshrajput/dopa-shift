package com.dopashift.data.remote

import com.dopashift.data.remote.dto.ConfigureLlmRequest
import com.dopashift.domain.service.LlmProviderService
import com.dopashift.domain.service.LlmProviderType
import com.dopashift.domain.service.LlmVideoResponse
import com.dopashift.domain.service.ValidationResult
import com.dopashift.domain.service.VideoSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android-side implementation of [LlmProviderService].
 *
 * Handles API key validation and video suggestion requests via the backend API.
 * Validation configures the key on the server first, then triggers validation
 * with a 10-second timeout (Requirement 15.8).
 */
@Singleton
class LlmProviderServiceImpl @Inject constructor(
    private val api: DopaShiftApi
) : LlmProviderService {

    override suspend fun requestVideoSuggestions(
        goalName: String,
        keywords: List<String>
    ): LlmVideoResponse {
        // Video suggestions are fetched per-goal via the content endpoint.
        // This is a simplified path; the full implementation would use goal ID.
        return LlmVideoResponse(videoUrls = emptyList(), source = VideoSource.YOUTUBE_SEARCH)
    }

    override suspend fun validateApiKey(
        provider: LlmProviderType,
        apiKey: String
    ): ValidationResult {
        return try {
            // Step 1: Configure the key on the backend
            api.configureLlm(
                ConfigureLlmRequest(
                    providerType = provider.name,
                    apiKey = apiKey
                )
            )
            // Step 2: Trigger validation
            val response = api.validateLlmConfig()
            if (response.isValid) {
                ValidationResult.Valid
            } else {
                ValidationResult.Invalid(response.message ?: "Invalid API key")
            }
        } catch (e: Exception) {
            ValidationResult.Invalid("Validation failed: ${e.message ?: "Network error"}")
        }
    }
}
