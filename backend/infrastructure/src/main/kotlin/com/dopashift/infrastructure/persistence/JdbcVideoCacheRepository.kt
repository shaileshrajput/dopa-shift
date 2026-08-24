package com.dopashift.infrastructure.persistence

import com.dopashift.domain.model.VideoResult
import com.dopashift.domain.model.VideoSource
import com.dopashift.domain.repository.VideoCacheRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
class JdbcVideoCacheRepository(
    private val jdbcTemplate: JdbcTemplate
) : VideoCacheRepository {

    override suspend fun findByKeywordSetHash(keywordSetHash: String): List<VideoResult>? =
        withContext(Dispatchers.IO) {
            val results = jdbcTemplate.query(
                "SELECT * FROM video_cache WHERE keyword_set_hash = ? AND expires_at > NOW()",
                { rs, _ ->
                    VideoResult(
                        url = rs.getString("video_url"),
                        title = rs.getString("video_title") ?: "",
                        source = VideoSource.valueOf(rs.getString("source"))
                    )
                },
                keywordSetHash
            )
            results.ifEmpty { null }
        }

    override suspend fun save(
        keywordSetHash: String,
        goalId: UUID,
        videos: List<VideoResult>,
        expiresAt: Instant
    ): Unit = withContext(Dispatchers.IO) {
        // Remove existing entries for this hash
        jdbcTemplate.update(
            "DELETE FROM video_cache WHERE keyword_set_hash = ?",
            keywordSetHash
        )

        videos.forEach { video ->
            jdbcTemplate.update(
                """INSERT INTO video_cache (id, keyword_set_hash, video_url, video_title, source, goal_id, cached_at, expires_at)
                   VALUES (?, ?, ?, ?, ?, ?, NOW(), ?)""",
                UUID.randomUUID(), keywordSetHash, video.url, video.title,
                video.source.name, goalId, Timestamp.from(expiresAt)
            )
        }
    }

    override suspend fun evictExpired(): Unit = withContext(Dispatchers.IO) {
        jdbcTemplate.update("DELETE FROM video_cache WHERE expires_at <= NOW()")
    }
}
