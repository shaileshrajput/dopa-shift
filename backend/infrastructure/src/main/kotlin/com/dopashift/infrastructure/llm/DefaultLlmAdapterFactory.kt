package com.dopashift.infrastructure.llm

import com.dopashift.domain.model.FinishReason
import com.dopashift.domain.model.LlmCompletionResult
import com.dopashift.domain.model.LlmProviderType
import com.dopashift.domain.port.LlmAdapter
import com.dopashift.domain.port.LlmAdapterFactory
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Default implementation of LlmAdapterFactory.
 * Creates provider-specific LLM adapters based on the configured provider type.
 */
@Component
class DefaultLlmAdapterFactory : LlmAdapterFactory {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun create(provider: LlmProviderType, apiKey: String): LlmAdapter {
        logger.debug("Creating LLM adapter for provider={}", provider)
        return when (provider) {
            LlmProviderType.OPENAI -> PlaceholderLlmAdapter(provider, supportsWeb = false)
            LlmProviderType.GEMINI -> PlaceholderLlmAdapter(provider, supportsWeb = false)
            LlmProviderType.CLAUDE -> PlaceholderLlmAdapter(provider, supportsWeb = false)
        }
    }
}

/**
 * Placeholder LLM adapter that returns empty results.
 * Will be replaced with actual HTTP-based implementations per provider.
 */
internal class PlaceholderLlmAdapter(
    private val provider: LlmProviderType,
    private val supportsWeb: Boolean
) : LlmAdapter {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun complete(prompt: String, maxTokens: Int): LlmCompletionResult {
        logger.warn("PlaceholderLlmAdapter.complete() called for provider={}. No real LLM call made.", provider)
        return LlmCompletionResult(text = "", tokensUsed = 0, finishReason = FinishReason.ERROR)
    }

    override suspend fun healthCheck(): Boolean {
        logger.warn("PlaceholderLlmAdapter.healthCheck() called for provider={}. Returning false.", provider)
        return false
    }

    override val supportsWebBrowsing: Boolean = supportsWeb
}
