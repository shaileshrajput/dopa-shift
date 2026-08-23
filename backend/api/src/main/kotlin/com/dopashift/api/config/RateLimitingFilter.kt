package com.dopashift.api.config

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.core.annotation.Order
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration
import java.time.Instant

/**
 * Rate limiting filter that applies request throttling to all API endpoints using
 * Redis-backed sliding window counters for horizontal scalability.
 *
 * Auth endpoints: 10 requests per minute per client IP (stricter to mitigate
 * credential-stuffing and brute-force attacks).
 * Data endpoints: 100 requests per minute per authenticated user.
 *
 * Returns 429 Too Many Requests with a Retry-After header when the limit is exceeded.
 *
 * Requirements: 22.8
 */
@Component
@Order(10) // After CorrelationIdFilter (HIGHEST_PRECEDENCE) but before other filters
@EnableConfigurationProperties(RateLimitingProperties::class)
class RateLimitingFilter(
    private val properties: RateLimitingProperties,
    private val objectMapper: ObjectMapper,
    private val redisTemplate: StringRedisTemplate
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(RateLimitingFilter::class.java)

    companion object {
        private val WINDOW_DURATION: Duration = Duration.ofMinutes(1)
        private const val AUTH_KEY_PREFIX = "rate:auth:"
        private const val DATA_KEY_PREFIX = "rate:data:"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (!properties.enabled) {
            filterChain.doFilter(request, response)
            return
        }

        val path = request.requestURI

        // Skip non-API paths (actuator, etc.)
        if (!path.startsWith("/v1/")) {
            filterChain.doFilter(request, response)
            return
        }

        val isAuthEndpoint = path.startsWith("/v1/auth/")

        val key: String
        val limit: Long

        if (isAuthEndpoint) {
            // Auth endpoints: rate limit by client IP (stricter)
            key = "$AUTH_KEY_PREFIX${resolveClientIp(request)}"
            limit = properties.authRequestsPerMinute
        } else {
            // Data endpoints: rate limit by authenticated user ID
            val userId = resolveUserId(request)
            if (userId == null) {
                // No user context yet - let Spring Security handle the 401
                filterChain.doFilter(request, response)
                return
            }
            key = "$DATA_KEY_PREFIX$userId"
            limit = properties.dataRequestsPerMinute
        }

        val allowed = tryConsume(key, limit)

        if (allowed) {
            val remaining = getRemainingTokens(key, limit)
            response.setHeader("X-Rate-Limit-Remaining", remaining.toString())
            filterChain.doFilter(request, response)
        } else {
            val retryAfterSeconds = computeRetryAfterSeconds(key)
            rejectWithTooManyRequests(request, response, retryAfterSeconds)
        }
    }

    /**
     * Attempts to consume a request token from the sliding window counter.
     * Uses Redis INCR with TTL to implement a fixed-window counter.
     *
     * Returns true if the request is within the rate limit, false otherwise.
     */
    private fun tryConsume(key: String, limit: Long): Boolean {
        val ops = redisTemplate.opsForValue()
        val currentCount = ops.increment(key) ?: 1L

        // Set expiry on first request in the window
        if (currentCount == 1L) {
            redisTemplate.expire(key, WINDOW_DURATION)
        }

        return currentCount <= limit
    }

    /**
     * Computes remaining tokens for the current window.
     */
    private fun getRemainingTokens(key: String, limit: Long): Long {
        val currentCount = redisTemplate.opsForValue().get(key)?.toLongOrNull() ?: 0L
        return maxOf(0L, limit - currentCount)
    }

    /**
     * Computes the Retry-After value in seconds based on the key's TTL.
     */
    private fun computeRetryAfterSeconds(key: String): Long {
        val ttl = redisTemplate.getExpire(key)
        return if (ttl > 0) ttl else WINDOW_DURATION.toSeconds()
    }

    /**
     * Resolves the client IP address, respecting X-Forwarded-For for reverse proxy setups.
     */
    private fun resolveClientIp(request: HttpServletRequest): String {
        val forwarded = request.getHeader("X-Forwarded-For")
        return if (!forwarded.isNullOrBlank()) {
            forwarded.split(",").first().trim()
        } else {
            request.remoteAddr
        }
    }

    /**
     * Resolves the authenticated user ID from the Spring Security context.
     * Returns null if no authenticated user is present (pre-authentication phase).
     */
    @Suppress("UNUSED_PARAMETER")
    private fun resolveUserId(request: HttpServletRequest): String? {
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication is JwtAuthenticationToken) {
            return authentication.token.subject
        }
        return null
    }

    /**
     * Writes a 429 Too Many Requests response with Retry-After header and
     * a standardized JSON error body matching the project's ErrorResponse format.
     */
    private fun rejectWithTooManyRequests(
        request: HttpServletRequest,
        response: HttpServletResponse,
        retryAfterSeconds: Long
    ) {
        val correlationId = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE) as? String
            ?: "unknown"

        log.warn(
            "Rate limit exceeded [correlationId={}, path={}, ip={}]",
            correlationId, request.requestURI, resolveClientIp(request)
        )

        response.status = HttpStatus.TOO_MANY_REQUESTS.value()
        response.setHeader("Retry-After", retryAfterSeconds.toString())
        response.contentType = MediaType.APPLICATION_JSON_VALUE

        val errorBody = mapOf(
            "error" to mapOf(
                "code" to "RATE_LIMIT_EXCEEDED",
                "message" to "Too many requests. Please retry after $retryAfterSeconds seconds.",
                "details" to mapOf("retryAfterSeconds" to retryAfterSeconds),
                "correlationId" to correlationId,
                "timestamp" to Instant.now().toString()
            )
        )

        response.writer.write(objectMapper.writeValueAsString(errorBody))
    }
}
