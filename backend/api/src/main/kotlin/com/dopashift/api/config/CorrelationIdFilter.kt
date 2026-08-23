package com.dopashift.api.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * Spring filter that generates or propagates a correlation ID for each request.
 *
 * - If the incoming request includes an X-Correlation-Id header, that value is used.
 * - Otherwise, a new UUID is generated.
 *
 * The correlation ID is:
 * 1. Stored in the MDC (for structured logging with logback/log4j2)
 * 2. Set as a request attribute (for use by controllers/exception handlers)
 * 3. Returned in the response X-Correlation-Id header
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CorrelationIdFilter : OncePerRequestFilter() {

    companion object {
        const val HEADER_NAME = "X-Correlation-Id"
        const val MDC_KEY = "correlationId"
        const val REQUEST_ATTRIBUTE = "correlationId"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val correlationId = request.getHeader(HEADER_NAME)?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString()

        try {
            MDC.put(MDC_KEY, correlationId)
            request.setAttribute(REQUEST_ATTRIBUTE, correlationId)
            response.setHeader(HEADER_NAME, correlationId)

            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(MDC_KEY)
        }
    }
}
