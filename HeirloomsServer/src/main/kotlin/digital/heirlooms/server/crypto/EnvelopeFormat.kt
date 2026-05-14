package digital.heirlooms.server.crypto

import java.nio.charset.StandardCharsets

object AlgorithmIds {
    const val AES256GCM_V1                 = "aes256gcm-v1"
    const val MASTER_AES256GCM_V1          = "master-aes256gcm-v1"
    const val PLOT_AES256GCM_V1            = "plot-aes256gcm-v1"
    const val P256_ECDH_HKDF_AES256GCM_V1 = "p256-ecdh-hkdf-aes256gcm-v1"
    const val ARGON2ID_AES256GCM_V1        = "argon2id-aes256gcm-v1"

    val SYMMETRIC  = setOf(AES256GCM_V1, MASTER_AES256GCM_V1, PLOT_AES256GCM_V1, ARGON2ID_AES256GCM_V1)
    val ASYMMETRIC = setOf(P256_ECDH_HKDF_AES256GCM_V1)
    val ALL        = SYMMETRIC + ASYMMETRIC
}

class EnvelopeFormatException(message: String) : Exception(message)

data class ParsedEnvelope(
    val version: Int,
    val algorithmId: String,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
    val authTag: ByteArray,
)

data class ParsedAsymmetricEnvelope(
    val version: Int,
    val algorithmId: String,
    val ephemeralPubkey: ByteArray,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
    val authTag: ByteArray,
)

object EnvelopeFormat {

    private const val SUPPORTED_VERSION: Byte = 1
    private const val MAX_ALG_ID_BYTES = 64
    private const val NONCE_LENGTH = 12
    private const val AUTH_TAG_LENGTH = 16
    private const val P256_PUBKEY_LENGTH = 65  // SEC1 uncompressed (0x04 prefix + 32 + 32)

    // Symmetric envelope structure:
    //   [1]  version
    //   [1]  alg_id_len
    //   [N]  alg_id  (UTF-8, max 64 bytes)
    //   [12] nonce
    //   [V]  ciphertext
    //   [16] auth_tag
    private const val MIN_SYMMETRIC_SIZE = 1 + 1 + 1 + NONCE_LENGTH + AUTH_TAG_LENGTH  // alg_id_len >= 1

    // Asymmetric envelope: same as symmetric but with 65-byte ephemeral pubkey before the nonce.
    private const val MIN_ASYMMETRIC_SIZE = MIN_SYMMETRIC_SIZE + P256_PUBKEY_LENGTH

    fun validateSymmetric(blob: ByteArray, expectedAlgorithmId: String? = null): ParsedEnvelope {
        if (blob.size < MIN_SYMMETRIC_SIZE)
            throw EnvelopeFormatException("blob too short: ${blob.size} bytes, minimum $MIN_SYMMETRIC_SIZE")

        var offset = 0

        val version = blob[offset++].toInt() and 0xFF
        if (version != SUPPORTED_VERSION.toInt())
            throw EnvelopeFormatException("unsupported envelope version: $version")

        val algIdLen = blob[offset++].toInt() and 0xFF
        if (algIdLen == 0 || algIdLen > MAX_ALG_ID_BYTES)
            throw EnvelopeFormatException("algorithm ID length out of range: $algIdLen")
        if (offset + algIdLen > blob.size)
            throw EnvelopeFormatException("algorithm ID length $algIdLen exceeds remaining blob size")

        val algorithmId = String(blob, offset, algIdLen, StandardCharsets.UTF_8)
        offset += algIdLen

        if (algorithmId !in AlgorithmIds.SYMMETRIC)
            throw EnvelopeFormatException("unknown or non-symmetric algorithm ID: $algorithmId")
        if (expectedAlgorithmId != null && algorithmId != expectedAlgorithmId)
            throw EnvelopeFormatException("algorithm ID mismatch: expected $expectedAlgorithmId, got $algorithmId")

        val remaining = blob.size - offset
        if (remaining < NONCE_LENGTH + AUTH_TAG_LENGTH)
            throw EnvelopeFormatException("blob too short after algorithm ID: $remaining bytes remain, need at least ${NONCE_LENGTH + AUTH_TAG_LENGTH}")

        val nonce = blob.copyOfRange(offset, offset + NONCE_LENGTH)
        offset += NONCE_LENGTH

        val ciphertextLen = blob.size - offset - AUTH_TAG_LENGTH
        val ciphertext = blob.copyOfRange(offset, offset + ciphertextLen)
        offset += ciphertextLen

        val authTag = blob.copyOfRange(offset, offset + AUTH_TAG_LENGTH)

        return ParsedEnvelope(version, algorithmId, nonce, ciphertext, authTag)
    }

    fun validateAsymmetric(blob: ByteArray, expectedAlgorithmId: String? = null): ParsedAsymmetricEnvelope {
        if (blob.size < MIN_ASYMMETRIC_SIZE)
            throw EnvelopeFormatException("blob too short: ${blob.size} bytes, minimum $MIN_ASYMMETRIC_SIZE")

        var offset = 0

        val version = blob[offset++].toInt() and 0xFF
        if (version != SUPPORTED_VERSION.toInt())
            throw EnvelopeFormatException("unsupported envelope version: $version")

        val algIdLen = blob[offset++].toInt() and 0xFF
        if (algIdLen == 0 || algIdLen > MAX_ALG_ID_BYTES)
            throw EnvelopeFormatException("algorithm ID length out of range: $algIdLen")
        if (offset + algIdLen > blob.size)
            throw EnvelopeFormatException("algorithm ID length $algIdLen exceeds remaining blob size")

        val algorithmId = String(blob, offset, algIdLen, StandardCharsets.UTF_8)
        offset += algIdLen

        if (algorithmId !in AlgorithmIds.ASYMMETRIC)
            throw EnvelopeFormatException("unknown or non-asymmetric algorithm ID: $algorithmId")
        if (expectedAlgorithmId != null && algorithmId != expectedAlgorithmId)
            throw EnvelopeFormatException("algorithm ID mismatch: expected $expectedAlgorithmId, got $algorithmId")

        val remaining = blob.size - offset
        if (remaining < P256_PUBKEY_LENGTH + NONCE_LENGTH + AUTH_TAG_LENGTH)
            throw EnvelopeFormatException("blob too short after algorithm ID: $remaining bytes remain")

        val ephemeralPubkey = blob.copyOfRange(offset, offset + P256_PUBKEY_LENGTH)
        offset += P256_PUBKEY_LENGTH

        val nonce = blob.copyOfRange(offset, offset + NONCE_LENGTH)
        offset += NONCE_LENGTH

        val ciphertextLen = blob.size - offset - AUTH_TAG_LENGTH
        val ciphertext = blob.copyOfRange(offset, offset + ciphertextLen)
        offset += ciphertextLen

        val authTag = blob.copyOfRange(offset, offset + AUTH_TAG_LENGTH)

        return ParsedAsymmetricEnvelope(version, algorithmId, ephemeralPubkey, nonce, ciphertext, authTag)
    }

    fun isValidSymmetric(blob: ByteArray, expectedAlgorithmId: String? = null): Boolean =
        try { validateSymmetric(blob, expectedAlgorithmId); true } catch (_: EnvelopeFormatException) { false }

    fun isValidAsymmetric(blob: ByteArray, expectedAlgorithmId: String? = null): Boolean =
        try { validateAsymmetric(blob, expectedAlgorithmId); true } catch (_: EnvelopeFormatException) { false }
}
