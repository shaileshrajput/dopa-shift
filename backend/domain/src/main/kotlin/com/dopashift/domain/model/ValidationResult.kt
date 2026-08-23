package com.dopashift.domain.model

/**
 * Result of an API key validation attempt against an LLM provider.
 */
data class ValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
)
