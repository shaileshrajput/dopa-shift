package com.dopashift.domain.port

import com.dopashift.domain.model.LlmProviderType

/**
 * Factory interface for creating provider-specific [LlmAdapter] instances.
 * Dispatches on [LlmProviderType] to produce the correct adapter implementation.
 *
 * The actual factory implementation (with concrete OpenAI/Gemini/Claude adapters)
 * lives in the infrastructure layer.
 */
interface LlmAdapterFactory {
    /**
     * Create an [LlmAdapter] for the given provider type and API key.
     */
    fun create(provider: LlmProviderType, apiKey: String): LlmAdapter
}
