package com.dopashift.domain.port

import java.util.UUID

/**
 * Port interface for profile photo storage operations (S3-compatible).
 * Implementations live in the infrastructure layer.
 *
 * Requirements: 9.2 (S3-compatible object storage)
 */
interface ProfileStorageService {
    /**
     * Uploads a profile photo for the given user.
     *
     * @param userId the user's ID used to derive the storage key
     * @param data the raw image bytes
     * @param contentType the MIME type (image/jpeg or image/png)
     * @return the public URL of the stored object
     */
    suspend fun uploadPhoto(userId: UUID, data: ByteArray, contentType: String): String

    /**
     * Deletes a previously uploaded profile photo.
     *
     * @param userId the user's ID
     * @param objectUrl the URL of the object to delete
     */
    suspend fun deletePhoto(userId: UUID, objectUrl: String)
}
