package com.dopashift.api.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtDecoders
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.web.SecurityFilterChain

/**
 * Spring Security configuration for DopaShift API.
 *
 * - Validates OAuth2 bearer tokens against Keycloak's JWKS endpoint
 * - Rejects missing, malformed, or expired tokens with 401 Unauthorized
 * - Extracts realm and client roles from Keycloak JWT claims for RBAC
 * - Enables method-level security (@PreAuthorize) for fine-grained access control
 * - Stateless session management (no server-side sessions)
 *
 * Requirements: 10.3, 10.4, 22.1, 22.10, 22.11
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
class SecurityConfig(
    @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private val issuerUri: String,

    @Value("\${dopashift.security.keycloak-client-id:dopashift-api}")
    private val keycloakClientId: String
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
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
                    jwt.decoder(jwtDecoder())
                    jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
                }
            }

        return http.build()
    }

    /**
     * Configures a JWT decoder that validates tokens against Keycloak's JWKS endpoint.
     * Validates issuer, expiration, and token signature.
     *
     * Rejects:
     * - Missing tokens → 401 (handled by Spring Security filter chain)
     * - Malformed tokens → 401 (JWT parsing failure)
     * - Expired tokens → 401 (exp claim validation)
     * - Tokens from wrong issuer → 401 (iss claim validation)
     */
    @Bean
    fun jwtDecoder(): JwtDecoder {
        val jwtDecoder = JwtDecoders.fromIssuerLocation(issuerUri) as NimbusJwtDecoder

        // Compose validators: default (issuer + expiration) from Spring Security
        val defaultValidator: OAuth2TokenValidator<Jwt> = JwtValidators.createDefaultWithIssuer(issuerUri)
        val validators = DelegatingOAuth2TokenValidator(defaultValidator)

        jwtDecoder.setJwtValidator(validators)
        return jwtDecoder
    }

    /**
     * Converts Keycloak JWT claims into Spring Security authorities.
     * Maps realm_access.roles and resource_access.<client>.roles to GrantedAuthority instances.
     *
     * This enables @PreAuthorize("hasRole('ADMIN')") or @PreAuthorize("hasRole('USER')") annotations
     * on controllers and service methods.
     */
    @Bean
    fun jwtAuthenticationConverter(): JwtAuthenticationConverter {
        val converter = JwtAuthenticationConverter()
        converter.setJwtGrantedAuthoritiesConverter(KeycloakJwtRoleConverter(keycloakClientId))
        return converter
    }
}
