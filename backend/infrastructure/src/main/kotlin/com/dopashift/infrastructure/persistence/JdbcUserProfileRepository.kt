package com.dopashift.infrastructure.persistence

import com.dopashift.domain.entity.UserProfile
import com.dopashift.domain.repository.UserProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

@Repository
class JdbcUserProfileRepository(
    private val jdbcTemplate: JdbcTemplate
) : UserProfileRepository {

    override suspend fun findById(id: UUID): UserProfile? = withContext(Dispatchers.IO) {
        jdbcTemplate.query(
            "SELECT * FROM users WHERE id = ?",
            { rs, _ -> mapRow(rs) },
            id
        ).firstOrNull()
    }

    override suspend fun findByKeycloakId(keycloakId: String): UserProfile? = withContext(Dispatchers.IO) {
        jdbcTemplate.query(
            "SELECT * FROM users WHERE keycloak_id = ?",
            { rs, _ -> mapRow(rs) },
            keycloakId
        ).firstOrNull()
    }

    override suspend fun save(profile: UserProfile): UserProfile = withContext(Dispatchers.IO) {
        val exists = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users WHERE id = ?",
            Int::class.java, profile.id
        ) ?: 0

        if (exists > 0) {
            jdbcTemplate.update(
                """UPDATE users SET display_name = ?, email = ?, preferred_locale = ?,
                   accent_color = ?, profile_photo_url = ?, timezone = ?,
                   quiet_hours_start = ?, quiet_hours_end = ?, updated_at = ?
                   WHERE id = ?""",
                profile.displayName, profile.email, profile.preferredLocale,
                profile.accentColor, profile.profilePhotoUrl, profile.timezone,
                profile.quietHoursStart?.let { java.sql.Time.valueOf(it) },
                profile.quietHoursEnd?.let { java.sql.Time.valueOf(it) },
                Timestamp.from(profile.updatedAt), profile.id
            )
        } else {
            jdbcTemplate.update(
                """INSERT INTO users (id, keycloak_id, display_name, email, preferred_locale, accent_color,
                   profile_photo_url, timezone, quiet_hours_start, quiet_hours_end, created_at, updated_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                profile.id, profile.keycloakId, profile.displayName, profile.email,
                profile.preferredLocale, profile.accentColor, profile.profilePhotoUrl,
                profile.timezone,
                profile.quietHoursStart?.let { java.sql.Time.valueOf(it) },
                profile.quietHoursEnd?.let { java.sql.Time.valueOf(it) },
                Timestamp.from(profile.createdAt), Timestamp.from(profile.updatedAt)
            )
        }
        profile
    }

    private fun mapRow(rs: ResultSet): UserProfile = UserProfile(
        id = rs.getObject("id", UUID::class.java),
        keycloakId = rs.getString("keycloak_id"),
        displayName = rs.getString("display_name"),
        email = rs.getString("email"),
        preferredLocale = rs.getString("preferred_locale"),
        accentColor = rs.getString("accent_color"),
        profilePhotoUrl = rs.getString("profile_photo_url"),
        timezone = rs.getString("timezone"),
        quietHoursStart = rs.getTime("quiet_hours_start")?.toLocalTime(),
        quietHoursEnd = rs.getTime("quiet_hours_end")?.toLocalTime(),
        createdAt = rs.getTimestamp("created_at").toInstant(),
        updatedAt = rs.getTimestamp("updated_at").toInstant()
    )
}
