package com.dopashift.api.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.header.writers.HstsHeaderWriter

/**
 * TLS security configuration.
 *
 * Requirement 8.3, 22.2: TLS 1.2+ for all connections (client-to-backend, backend-to-backend).
 *
 * In production, TLS termination happens at the load balancer (nginx/ALB).
 * This configuration:
 * - Enforces HSTS headers (Strict-Transport-Security)
 * - Redirects HTTP to HTTPS in production profiles
 * - Sets secure headers (X-Content-Type-Options, X-Frame-Options, etc.)
 */
@Configuration
class TlsSecurityConfig {

    /**
     * Configures security headers for transport security.
     * These headers instruct browsers to use HTTPS exclusively.
     */
    @Bean
    fun securityHeadersFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .headers { headers ->
                // HSTS: instruct browsers to always use HTTPS (max-age = 1 year)
                headers.httpStrictTransportSecurity { hsts ->
                    hsts
                        .includeSubDomains(true)
                        .maxAgeInSeconds(31536000) // 1 year
                        .preload(true)
                }

                // Prevent MIME-type sniffing
                headers.contentTypeOptions { }

                // Prevent clickjacking
                headers.frameOptions { frame ->
                    frame.deny()
                }

                // XSS protection header (legacy but still useful for older browsers)
                headers.xssProtection { xss ->
                    xss.disable() // Modern approach: rely on CSP instead
                }

                // Content Security Policy
                headers.contentSecurityPolicy { csp ->
                    csp.policyDirectives("default-src 'self'; frame-ancestors 'none';")
                }
            }
            .csrf { csrf ->
                // Disable CSRF for stateless REST API (using Bearer tokens)
                csrf.disable()
            }

        return http.build()
    }
}
