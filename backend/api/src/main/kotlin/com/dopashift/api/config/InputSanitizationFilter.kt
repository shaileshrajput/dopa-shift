package com.dopashift.api.config

import jakarta.servlet.FilterChain
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader

/**
 * Servlet filter that sanitizes all incoming request body content by stripping
 * null bytes (\u0000) and ASCII control characters (0x01–0x1F, except common
 * whitespace: tab \t, newline \n, carriage return \r).
 *
 * This provides defense-in-depth against injection attacks that rely on embedded
 * control characters or null bytes to bypass downstream validation. It runs before
 * Spring's DispatcherServlet processes the request body.
 *
 * Ordered after CorrelationIdFilter (HIGHEST_PRECEDENCE) but before other filters.
 *
 * Requirements: 22.7 (Input validation and sanitization)
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class InputSanitizationFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val contentType = request.contentType
        if (contentType != null && contentType.contains("application/json", ignoreCase = true)) {
            filterChain.doFilter(SanitizedRequestWrapper(request), response)
        } else {
            filterChain.doFilter(request, response)
        }
    }

    /**
     * Wrapper around HttpServletRequest that reads the body, sanitizes it,
     * and provides the sanitized bytes through getInputStream() and getReader().
     */
    private class SanitizedRequestWrapper(request: HttpServletRequest) : HttpServletRequestWrapper(request) {

        private val sanitizedBody: ByteArray

        init {
            val rawBytes = request.inputStream.readAllBytes()
            val rawString = String(rawBytes, Charsets.UTF_8)
            val sanitized = sanitize(rawString)
            sanitizedBody = sanitized.toByteArray(Charsets.UTF_8)
        }

        override fun getInputStream(): ServletInputStream {
            val byteStream = ByteArrayInputStream(sanitizedBody)
            return object : ServletInputStream() {
                override fun read(): Int = byteStream.read()
                override fun isFinished(): Boolean = byteStream.available() == 0
                override fun isReady(): Boolean = true
                override fun setReadListener(listener: ReadListener?) {
                    // No-op for synchronous processing
                }
            }
        }

        override fun getReader(): BufferedReader {
            return BufferedReader(InputStreamReader(ByteArrayInputStream(sanitizedBody), Charsets.UTF_8))
        }

        override fun getContentLength(): Int = sanitizedBody.size

        override fun getContentLengthLong(): Long = sanitizedBody.size.toLong()

        companion object {
            /**
             * Strips null bytes and control characters from the input string.
             * Preserves tab (\t), newline (\n), and carriage return (\r) as they
             * are valid whitespace in JSON.
             */
            fun sanitize(input: String): String {
                val sb = StringBuilder(input.length)
                for (ch in input) {
                    when {
                        ch == '\u0000' -> {} // Strip null bytes
                        ch.code in 0x01..0x1F && ch != '\t' && ch != '\n' && ch != '\r' -> {} // Strip control chars
                        ch == '\u007F' -> {} // Strip DEL character
                        else -> sb.append(ch)
                    }
                }
                return sb.toString()
            }
        }
    }
}
