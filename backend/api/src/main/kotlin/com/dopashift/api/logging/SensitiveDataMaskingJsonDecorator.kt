package com.dopashift.api.logging

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.SerializableString
import com.fasterxml.jackson.core.util.JsonGeneratorDelegate
import net.logstash.logback.decorate.JsonGeneratorDecorator

/**
 * LogstashEncoder JSON generator decorator that applies sensitive data
 * redaction to all string values written to log output.
 *
 * This ensures that regardless of where sensitive data appears in a JSON log entry
 * (message, MDC fields, structured arguments), it gets redacted at the
 * logging-framework level.
 *
 * Requirements: 18.2, 22.4 (Framework-level log redaction)
 */
class SensitiveDataMaskingJsonDecorator : JsonGeneratorDecorator {

    override fun decorate(generator: JsonGenerator): JsonGenerator {
        return MaskingJsonGenerator(generator)
    }

    private class MaskingJsonGenerator(
        delegate: JsonGenerator
    ) : JsonGeneratorDelegate(delegate) {

        override fun writeString(text: String?) {
            if (text != null) {
                super.writeString(SensitiveDataRedactor.redact(text))
            } else {
                super.writeNull()
            }
        }

        override fun writeString(text: CharArray, offset: Int, len: Int) {
            val str = String(text, offset, len)
            super.writeString(SensitiveDataRedactor.redact(str))
        }

        override fun writeString(text: SerializableString) {
            val redacted = SensitiveDataRedactor.redact(text.value)
            super.writeString(redacted)
        }
    }
}
