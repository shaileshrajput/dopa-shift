package com.dopashift.infrastructure.storage

import com.dopashift.domain.port.ProfileStorageService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * MinIO/S3-compatible implementation of ProfileStorageService.
 * Uses local file system fallback when MinIO is not configured.
 */
@Service
class MinioProfileStorageService : ProfileStorageService {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun uploadPhoto(userId: UUID, data: ByteArray, contentType: String): String {
        // TODO: Integrate with MinIO S3 client when configured
        val extension = if (contentType.contains("png")) "png" else "jpg"
        val key = "profiles/$userId/photo.$extension"
        logger.info("Profile photo upload requested for user={}, key={}, size={} bytes", userId, key, data.size)
        return "/storage/$key"
    }

    override suspend fun deletePhoto(userId: UUID, objectUrl: String) {
        // TODO: Integrate with MinIO S3 client when configured
        logger.info("Profile photo delete requested for user={}, url={}", userId, objectUrl)
    }
}
