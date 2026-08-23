package com.dopashift.api.logging

/**
 * Centralized sensitive data redaction logic used by both the pattern converter
 * (dev profile) and the JSON decorator (prod profile).
 *
 * Redacts:
 * - OpenAI API keys (sk-...)
 * - Google API keys (AIza...)
 * - Anthropic API keys (sk-ant-...)
 * - Bearer tokens
 * - Email addresses
 *
 * Requirements: 18.2, 22.4 (Structured logging with redaction at framework level)
 */
object SensitiveDataRedactor {

    private data class RedactionRule(val pattern: Regex, val replacement: String)

    private val rules: List<RedactionRule> = listOf(
        // OpenAI API keys: sk- followed by alphanumeric/dash characters (48+ chars typical)
        RedactionRule(
            Regex("""sk-[A-Za-z0-9_-]{20,}"""),
            "sk-***REDACTED***"
        ),
        // Anthropic API keys: sk-ant- followed by alphanumeric/dash characters
        RedactionRule(
            Regex("""sk-ant-[A-Za-z0-9_-]{20,}"""),
            "sk-ant-***REDACTED***"
        ),
        // Google API keys: AIza followed by alphanumeric characters
        RedactionRule(
            Regex("""AIza[A-Za-z0-9_-]{30,}"""),
            "AIza***REDACTED***"
        ),
        // Bearer tokens in log messages
        RedactionRule(
            Regex("""Bearer\s+[A-Za-z0-9._~+/=-]{10,}"""),
            "Bearer ***REDACTED***"
        ),
        // Email addresses
        RedactionRule(
            Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}"""),
            "***EMAIL_REDACTED***"
        )
    )

    /**
     * Apply all redaction rules to the input string.
     * Returns the input with all sensitive patterns masked.
     */
    fun redact(input: String): String {
        var result = input
        for (rule in rules) {
            result = rule.pattern.replace(result, rule.replacement)
        }
        return result
    }
}
