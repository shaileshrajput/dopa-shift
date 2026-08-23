package com.dopashift.domain.model

/**
 * Supported LLM provider types for BYO-LLM integration.
 * Users supply their own API key for one of these providers.
 */
enum class LlmProviderType {
    OPENAI,
    GEMINI,
    CLAUDE
}
