package com.dopashift.domain.repository

import com.dopashift.domain.model.VideoResult
import java.time.Instant
import java.util.UUID

/**
 * Port interface for video cache persistence.
 * Stores validated video recommendations keyed by keyword-set hash with a 24h TTL.
 *
 * Requirements: 3.7 (cache LLM-suggested video links per goal-keyword-set, default 24h TTL)
 */
interface VideoCacheRepository {

    /**
     * Find cached video results by keyword-set hash that have not expired.
     */
    suspend fun findByKeywordSetHash(keywordSetHash: String): List<VideoResult>?

    /**
     * Store video results with the given keyword-set hash and expiration time.
     */
    suspend fun save(
        keywordSetHash: String,
        goalId: UUID,
        videos: List<VideoResult>,
        expiresAt: Instant
    )

    /**
     * Remove expired cache entries.
     */
    suspend fun evictExpired()
}
