package com.dopashift.api.config

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Utility component that provides convenient access to the authenticated user's identity
 * from the Spring Security context. Controllers and services can inject this to extract
 * the user_id without manually parsing JWT claims.
 *
 * The user_id is sourced from the JWT 'sub' claim (Keycloak subject identifier).
 *
 * Requirements: 22.11 (user_id scoping at API/query layer)
 */
@Component
class AuthenticatedUser {

    /**
     * Returns the current authenticated user's ID (UUID) extracted from the JWT 'sub' claim.
     *
     * @throws IllegalStateException if no authentication is present or the token is not a JWT
     * @throws IllegalArgumentException if the 'sub' claim is not a valid UUID
     */
    fun getUserId(): UUID {
        val authentication = SecurityContextHolder.getContext().authentication
            ?: throw IllegalStateException("No authentication found in security context")

        val jwt = when (authentication) {
            is JwtAuthenticationToken -> authentication.token
            else -> throw IllegalStateException("Expected JWT authentication but found: ${authentication::class.simpleName}")
        }

        val subject = jwt.subject
            ?: throw IllegalStateException("JWT 'sub' claim is missing")

        return UUID.fromString(subject)
    }

    /**
     * Returns the JWT token for the current authenticated user.
     *
     * @throws IllegalStateException if no authentication is present or the token is not a JWT
     */
    fun getJwt(): Jwt {
        val authentication = SecurityContextHolder.getContext().authentication
            ?: throw IllegalStateException("No authentication found in security context")

        return when (authentication) {
            is JwtAuthenticationToken -> authentication.token
            else -> throw IllegalStateException("Expected JWT authentication but found: ${authentication::class.simpleName}")
        }
    }

    /**
     * Returns the user's email from the JWT 'email' claim, or null if not present.
     */
    fun getEmail(): String? {
        return getJwt().getClaimAsString("email")
    }

    /**
     * Returns the user's preferred username from the JWT, or null if not present.
     */
    fun getPreferredUsername(): String? {
        return getJwt().getClaimAsString("preferred_username")
    }

    /**
     * Checks if the current user has the specified realm role.
     *
     * @param role the role name (without ROLE_ prefix, case-insensitive)
     */
    fun hasRealmRole(role: String): Boolean {
        val authentication = SecurityContextHolder.getContext().authentication ?: return false
        return authentication.authorities.any {
            it.authority.equals("ROLE_${role.uppercase()}", ignoreCase = true)
        }
    }
}
