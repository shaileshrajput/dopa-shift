package com.dopashift.api.config

import org.springframework.core.convert.converter.Converter
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt

/**
 * Converts Keycloak JWT claims (realm_access.roles and resource_access.<client>.roles)
 * into Spring Security GrantedAuthority instances.
 *
 * Keycloak tokens contain roles in:
 * - realm_access.roles: realm-level roles (e.g., "user", "admin")
 * - resource_access.<client_id>.roles: client-level roles
 *
 * This converter maps them to authorities with prefixes:
 * - ROLE_<realm_role> for realm roles
 * - ROLE_<client_id>_<client_role> for client roles
 *
 * Requirements: 22.10 (RBAC at API layer)
 */
class KeycloakJwtRoleConverter(
    private val clientId: String = "dopashift-api"
) : Converter<Jwt, Collection<GrantedAuthority>> {

    override fun convert(jwt: Jwt): Collection<GrantedAuthority> {
        val authorities = mutableSetOf<GrantedAuthority>()

        // Extract realm roles from realm_access.roles
        val realmAccess = jwt.getClaim<Map<String, Any>>("realm_access")
        if (realmAccess != null) {
            @Suppress("UNCHECKED_CAST")
            val realmRoles = realmAccess["roles"] as? Collection<String> ?: emptyList()
            realmRoles.forEach { role ->
                authorities.add(SimpleGrantedAuthority("ROLE_${role.uppercase()}"))
            }
        }

        // Extract client roles from resource_access.<clientId>.roles
        val resourceAccess = jwt.getClaim<Map<String, Any>>("resource_access")
        if (resourceAccess != null) {
            @Suppress("UNCHECKED_CAST")
            val clientAccess = resourceAccess[clientId] as? Map<String, Any>
            if (clientAccess != null) {
                @Suppress("UNCHECKED_CAST")
                val clientRoles = clientAccess["roles"] as? Collection<String> ?: emptyList()
                clientRoles.forEach { role ->
                    authorities.add(SimpleGrantedAuthority("ROLE_${clientId.uppercase()}_${role.uppercase()}"))
                }
            }
        }

        return authorities
    }
}
