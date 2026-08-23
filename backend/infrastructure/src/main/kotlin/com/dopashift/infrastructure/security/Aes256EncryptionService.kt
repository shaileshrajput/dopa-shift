package com.dopashift.infrastructure.security

import com.dopashift.domain.port.EncryptionResult
import com.dopashift.domain.port.EncryptionService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM implementation of [EncryptionService] for encrypting LLM API keys
 * and other sensitive data at rest.
 *
 * Requirements: 13.7, 15.2, 22.3
 * - AES-256 encryption for stored LLM API keys.
 * - No credentials in source control or plaintext config.
 * - Encryption key sourced from environment variable or Vault.
 *
 * The encryption key is injected from configuration (environment variable or Vault secret).
 * Key format: 32-byte hex string (64 hex characters).
 */
@Service
class Aes256EncryptionService(
    @Value("\${dopashift.security.encryption-key}") private val encryptionKeyHex: String
) : EncryptionService {

    companion object {
        private const val ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
    }

    private val secretKey: SecretKey by lazy {
        val keyBytes = hexToBytes(encryptionKeyHex)
        require(keyBytes.size == 32) {
            "Encryption key must be exactly 32 bytes (256 bits). Got ${keyBytes.size} bytes."
        }
        SecretKeySpec(keyBytes, ALGORITHM)
    }

    private val secureRandom = SecureRandom()

    override fun encrypt(plaintext: String): EncryptionResult {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        return EncryptionResult(
            ciphertext = ciphertext,
            iv = iv
        )
    }

    override fun decrypt(ciphertext: ByteArray, iv: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

        val plaintext = cipher.doFinal(ciphertext)
        return String(plaintext, Charsets.UTF_8)
    }

    private fun hexToBytes(hex: String): ByteArray {
        val cleanHex = hex.removePrefix("0x").removePrefix("0X")
        require(cleanHex.length % 2 == 0) { "Hex string must have even length" }
        return ByteArray(cleanHex.length / 2) { i ->
            cleanHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
