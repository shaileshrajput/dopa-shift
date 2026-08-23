package com.dopashift.api.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for API rate limiting.
 *
 * Auth endpoints get stricter limits (credential-stuffing/brute-force mitigation).
 * Data endpoints get standard limits per authenticated user.
 *
 * Requirements: 22.8
 */
@ConfigurationProperties(prefix = "dopashift.rate-limiting")
data class RateLimitingProperties(
    /** Whether rate limiting is enabled (useful to disable in tests) */
    val enabled: Boolean = true,
    /** Rate limit for auth endpoints: requests per window per IP */
    val authRequestsPerMinute: Long = 10,
    /** Rate limit for data endpoints: requests per window per user */
    val dataRequestsPerMinute: Long = 100
)
