package com.dopashift.domain.model

/**
 * Result of a provider-agnostic LLM completion call.
 */
data class LlmCompletionResult(
    val text: String,
    val tokensUsed: Int,
    val finishReason: FinishReason
)

enum class FinishReason {
    COMPLETE,
    MAX_TOKENS,
    ERROR
}
