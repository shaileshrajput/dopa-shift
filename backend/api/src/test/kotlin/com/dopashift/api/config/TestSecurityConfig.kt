package com.dopashift.api.config

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.web.SecurityFilterChain

/**
 * Test security configuration for contract tests.
 *
 * Provides a mock JwtDecoder that rejects all tokens (secured endpoints → 401)
 * and a minimal security filter chain that mirrors the production auth rules
 * without requiring a real Keycloak instance.
 */
@TestConfiguration
class TestSecurityConfig {

    @Bean("jwtDecoder")
    @Primary
    fun testJwtDecoder(): JwtDecoder {
        return JwtDecoder { _ ->
            throw org.springframework.security.oauth2.jwt.JwtException("Test - no valid token")
        }
    }

    @Bean("securityFilterChain")
    @Primary
    fun testSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                    .requestMatchers("/v1/auth/**").permitAll()
                    .requestMatchers("/v1/**").authenticated()
                    .anyRequest().denyAll()
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt ->
                    jwt.decoder(testJwtDecoder())
                }
            }

        return http.build()
    }
}
