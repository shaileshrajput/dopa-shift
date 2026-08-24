package com.dopashift.infrastructure.persistence

import com.dopashift.domain.entity.LlmConfiguration
import com.dopashift.domain.model.LlmProviderType
import com.dopashift.domain.repository.LlmConfigRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

@Repository
class JdbcLlmConfigRepository(
    private val jdbcTemplate: JdbcTemplate
) : LlmConfigRepository {

    override suspend fun findByUserId(userId: UUID): LlmConfiguration? = withContext(Dispatchers.IO) {
        jdbcTemplate.query(
            "SELECT * FROM llm_configurations WHERE user_id = ?",
            { rs, _ -> mapRow(rs) },
            userId
        ).firstOrNull()
    }

    override suspend fun save(config: LlmConfiguration): LlmConfiguration = withContext(Dispatchers.IO) {
        val exists = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM llm_configurations WHERE id = ?",
            Int::class.java, config.id
        ) ?: 0

        if (exists > 0) {
            jdbcTemplate.update(
                """UPDATE llm_configurations SET provider_type = ?, api_key_encrypted = ?,
                   api_key_iv = ?, is_validated = ?, validated_at = ?, updated_at = ?
                   WHERE id = ?""",
                config.providerType.name, config.apiKeyEncrypted, config.apiKeyIv,
                config.isValidated, config.validatedAt?.let { Timestamp.from(it) },
                Timestamp.from(config.updatedAt), config.id
            )
        } else {
            jdbcTemplate.update(
                """INSERT INTO llm_configurations (id, user_id, provider_type, api_key_encrypted, api_key_iv, is_validated, validated_at, created_at, updated_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                config.id, config.userId, config.providerType.name,
                config.apiKeyEncrypted, config.apiKeyIv,
                config.isValidated, config.validatedAt?.let { Timestamp.from(it) },
                Timestamp.from(config.createdAt), Timestamp.from(config.updatedAt)
            )
        }
        config
    }

    override suspend fun deleteByUserId(userId: UUID): Unit = withContext(Dispatchers.IO) {
        jdbcTemplate.update("DELETE FROM llm_configurations WHERE user_id = ?", userId)
    }

    private fun mapRow(rs: ResultSet): LlmConfiguration = LlmConfiguration(
        id = rs.getObject("id", UUID::class.java),
        userId = rs.getObject("user_id", UUID::class.java),
        providerType = LlmProviderType.valueOf(rs.getString("provider_type")),
        apiKeyEncrypted = rs.getBytes("api_key_encrypted"),
        apiKeyIv = rs.getBytes("api_key_iv"),
        apiKeyLast4 = "****", // Not stored in DB, derived on write
        isValidated = rs.getBoolean("is_validated"),
        validatedAt = rs.getTimestamp("validated_at")?.toInstant(),
        createdAt = rs.getTimestamp("created_at").toInstant(),
        updatedAt = rs.getTimestamp("updated_at").toInstant()
    )
}
