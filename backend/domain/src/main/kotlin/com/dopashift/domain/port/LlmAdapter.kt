package com.dopashift.domain.port

import com.dopashift.domain.model.LlmCompletionResult

/**
 * Provider-agnostic adapter interface for LLM completion.
 * Implementations for specific providers (OpenAI, Gemini, Claude) live in the infrastructure layer.
 *
 * Requirement 15, AC3: provider-agnostic adapter pattern.
 */
interface LlmAdapter {
    /**
     * Send a prompt to the LLM and receive a completion result.
     */
    suspend fun complete(prompt: String, maxTokens: Int = 1000): LlmCompletionResult

    /**
     * Verify the adapter can reach its provider and the API key is valid.
     */
    suspend fun healthCheck(): Boolean

    /**
     * Whether this provider supports web browsing / URL access within prompts.
     */
    val supportsWebBrowsing: Boolean
}
