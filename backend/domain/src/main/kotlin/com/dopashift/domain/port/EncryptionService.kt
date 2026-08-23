package com.dopashift.domain.port

/**
 * Port interface for encrypting and decrypting sensitive data (API keys).
 * Implementations use AES-256-GCM or equivalent in the infrastructure layer.
 *
 * Requirements: 15.2, 22.3
 */
interface EncryptionService {
    /**
     * Encrypt plaintext data. Returns a pair of (ciphertext, IV).
     */
    fun encrypt(plaintext: String): EncryptionResult

    /**
     * Decrypt ciphertext using the given IV.
     */
    fun decrypt(ciphertext: ByteArray, iv: ByteArray): String
}

/**
 * Result of an encryption operation containing the ciphertext and IV.
 */
data class EncryptionResult(
    val ciphertext: ByteArray,
    val iv: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptionResult) return false
        return ciphertext.contentEquals(other.ciphertext) && iv.contentEquals(other.iv)
    }

    override fun hashCode(): Int {
        var result = ciphertext.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        return result
    }
}
