package com.dopashift.api.logging

import ch.qos.logback.classic.pattern.MessageConverter
import ch.qos.logback.classic.spi.ILoggingEvent

/**
 * Logback pattern layout converter that redacts sensitive data from log messages.
 *
 * Redacts:
 * - LLM API keys (OpenAI sk-*, Google AIza*, Anthropic sk-ant-*)
 * - Bearer tokens
 * - Email addresses
 *
 * Used in dev/local profile via the %maskedMsg conversion pattern.
 *
 * Requirements: 22.4 (Log redaction at logging-framework level)
 */
class SensitiveDataMaskingConverter : MessageConverter() {

    override fun convert(event: ILoggingEvent): String {
        val message = super.convert(event)
        return SensitiveDataRedactor.redact(message)
    }
}
