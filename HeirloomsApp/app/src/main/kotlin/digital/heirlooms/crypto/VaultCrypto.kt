package digital.heirlooms.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object VaultCrypto {

    const val ALG_AES256GCM_V1          = "aes256gcm-v1"
    const val ALG_MASTER_AES256GCM_V1   = "master-aes256gcm-v1"
    const val ALG_ARGON2ID_AES256GCM_V1 = "argon2id-aes256gcm-v1"
    const val ALG_P256_ECDH_HKDF_V1     = "p256-ecdh-hkdf-aes256gcm-v1"

    private const val ENVELOPE_VERSION: Byte = 1
    private const val NONCE_SIZE = 12
    private const val AUTH_TAG_BITS = 128
    private const val EPHEMERAL_PUBKEY_SIZE = 65  // SEC1 uncompressed P-256

    private val rng = SecureRandom()

    fun generateMasterKey(): ByteArray = ByteArray(32).also { rng.nextBytes(it) }
    fun generateDek(): ByteArray = ByteArray(32).also { rng.nextBytes(it) }
    fun generateNonce(): ByteArray = ByteArray(NONCE_SIZE).also { rng.nextBytes(it) }
    fun generateSalt(size: Int = 16): ByteArray = ByteArray(size).also { rng.nextBytes(it) }

    // AES-256-GCM encrypt. Returns ciphertext || auth_tag (JCE appends tag automatically).
    fun aesGcmEncrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(AUTH_TAG_BITS, nonce))
        return cipher.doFinal(plaintext)
    }

    // AES-256-GCM encrypt with AAD. Returns ciphertext || auth_tag.
    // length allows encrypting a prefix of plaintext without copying (used for the last streaming chunk).
    fun aesGcmEncryptWithAad(
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray,
        plaintext: ByteArray,
        length: Int = plaintext.size,
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(AUTH_TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(plaintext, 0, length)
    }

    // AES-256-GCM decrypt. Input is ciphertext || auth_tag as returned by aesGcmEncrypt.
    fun aesGcmDecrypt(key: ByteArray, nonce: ByteArray, ciphertextWithTag: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(AUTH_TAG_BITS, nonce))
        return cipher.doFinal(ciphertextWithTag)
    }

    // Build symmetric envelope: [version][alg_id_len][alg_id][nonce][ciphertext || auth_tag]
    fun buildSymmetricEnvelope(algorithmId: String, nonce: ByteArray, ciphertextWithTag: ByteArray): ByteArray {
        val algBytes = algorithmId.toByteArray(Charsets.UTF_8)
        require(algBytes.size in 1..64) { "Algorithm ID length out of range: ${algBytes.size}" }
        val out = ByteArray(2 + algBytes.size + nonce.size + ciphertextWithTag.size)
        var off = 0
        out[off++] = ENVELOPE_VERSION
        out[off++] = algBytes.size.toByte()
        algBytes.copyInto(out, off); off += algBytes.size
        nonce.copyInto(out, off); off += nonce.size
        ciphertextWithTag.copyInto(out, off)
        return out
    }

    // Encrypt plaintext and return a symmetric envelope.
    fun encryptSymmetric(algorithmId: String, key: ByteArray, plaintext: ByteArray): ByteArray {
        val nonce = generateNonce()
        val ct = aesGcmEncrypt(key, nonce, plaintext)
        return buildSymmetricEnvelope(algorithmId, nonce, ct)
    }

    // Parse and decrypt a symmetric envelope. Throws on bad version, unknown alg, or auth failure.
    fun decryptSymmetric(envelope: ByteArray, key: ByteArray): ByteArray {
        var off = 0
        val version = envelope[off++].toInt() and 0xFF
        require(version == 1) { "Unsupported envelope version: $version" }
        val algLen = envelope[off++].toInt() and 0xFF
        require(algLen in 1..64 && off + algLen <= envelope.size) { "Invalid algorithm ID length: $algLen" }
        off += algLen  // skip algorithm ID (validated by server; client trusts it)
        require(envelope.size >= off + NONCE_SIZE + 16) { "Envelope too short" }
        val nonce = envelope.copyOfRange(off, off + NONCE_SIZE)
        off += NONCE_SIZE
        val ctWithTag = envelope.copyOfRange(off, envelope.size)
        return aesGcmDecrypt(key, nonce, ctWithTag)
    }

    // Wrap a DEK under the master key using master-aes256gcm-v1.
    fun wrapDekUnderMasterKey(dek: ByteArray, masterKey: ByteArray): ByteArray =
        encryptSymmetric(ALG_MASTER_AES256GCM_V1, masterKey, dek)

    // Unwrap a DEK from its master-key envelope.
    fun unwrapDekWithMasterKey(envelope: ByteArray, masterKey: ByteArray): ByteArray =
        decryptSymmetric(envelope, masterKey)

    // ---- Passphrase wrapping ------------------------------------------------

    data class Argon2Params(val m: Int = 65536, val t: Int = 3, val p: Int = 1)

    data class PassphraseWrap(
        val envelope: ByteArray,
        val salt: ByteArray,
        val params: Argon2Params,
    )

    fun wrapMasterKeyWithPassphrase(
        masterKey: ByteArray,
        passphrase: CharArray,
        params: Argon2Params = Argon2Params(),
    ): PassphraseWrap {
        val salt = generateSalt(16)
        val kek = deriveArgon2id(passphrase, salt, params)
        val envelope = encryptSymmetric(ALG_ARGON2ID_AES256GCM_V1, kek, masterKey)
        return PassphraseWrap(envelope, salt, params)
    }

    fun unwrapMasterKeyWithPassphrase(
        envelope: ByteArray,
        passphrase: CharArray,
        salt: ByteArray,
        params: Argon2Params,
    ): ByteArray {
        val kek = deriveArgon2id(passphrase, salt, params)
        return decryptSymmetric(envelope, kek)
    }

    private fun deriveArgon2id(passphrase: CharArray, salt: ByteArray, params: Argon2Params): ByteArray {
        val passphraseBytes = charArrayToUtf8(passphrase)
        return try {
            val argon2Params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withSalt(salt)
                .withMemoryAsKB(params.m)
                .withIterations(params.t)
                .withParallelism(params.p)
                .build()
            val generator = Argon2BytesGenerator()
            generator.init(argon2Params)
            ByteArray(32).also { generator.generateBytes(passphraseBytes, it) }
        } finally {
            passphraseBytes.fill(0)
        }
    }

    private fun charArrayToUtf8(chars: CharArray): ByteArray {
        val buf = java.nio.ByteBuffer.allocate(chars.size * 4)
        val encoder = Charsets.UTF_8.newEncoder()
        encoder.encode(java.nio.CharBuffer.wrap(chars), buf, true)
        return buf.array().copyOf(buf.position())
    }

    // ---- HKDF-SHA256 (RFC 5869) — 32-byte output ---------------------------

    fun hkdf(ikm: ByteArray, salt: ByteArray? = null, info: ByteArray = ByteArray(0)): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        // Extract: PRK = HMAC-Hash(salt, IKM)
        val effectiveSalt = if (salt == null || salt.isEmpty()) ByteArray(32) else salt
        mac.init(SecretKeySpec(effectiveSalt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)
        // Expand: T(1) = HMAC-Hash(PRK, info || 0x01) — one round for 32 bytes
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        mac.update(info)
        mac.update(0x01.toByte())
        return mac.doFinal()
    }

    // ---- Asymmetric envelope (P-256 ECDH HKDF AES-256-GCM) ----------------

    // Format: [version][alg_id_len][alg_id][65-byte ephemeral_pubkey SEC1][nonce][ct+tag]
    // This matches the server's ParsedAsymmetricEnvelope.

    data class ParsedAsymmetricEnvelope(
        val algorithmId: String,
        val ephemeralPubkeyBytes: ByteArray,
        val nonce: ByteArray,
        val ciphertextWithTag: ByteArray,
    )

    fun buildAsymmetricEnvelope(
        algorithmId: String,
        ephemeralPubkeyBytes: ByteArray,
        nonce: ByteArray,
        ciphertextWithTag: ByteArray,
    ): ByteArray {
        require(ephemeralPubkeyBytes.size == EPHEMERAL_PUBKEY_SIZE) {
            "Expected 65-byte SEC1 pubkey, got ${ephemeralPubkeyBytes.size}"
        }
        val algBytes = algorithmId.toByteArray(Charsets.UTF_8)
        require(algBytes.size in 1..64) { "Algorithm ID length out of range" }
        val out = ByteArray(2 + algBytes.size + EPHEMERAL_PUBKEY_SIZE + nonce.size + ciphertextWithTag.size)
        var off = 0
        out[off++] = ENVELOPE_VERSION
        out[off++] = algBytes.size.toByte()
        algBytes.copyInto(out, off); off += algBytes.size
        ephemeralPubkeyBytes.copyInto(out, off); off += EPHEMERAL_PUBKEY_SIZE
        nonce.copyInto(out, off); off += nonce.size
        ciphertextWithTag.copyInto(out, off)
        return out
    }

    fun parseAsymmetricEnvelope(envelope: ByteArray): ParsedAsymmetricEnvelope {
        var off = 0
        val version = envelope[off++].toInt() and 0xFF
        require(version == 1) { "Unsupported envelope version: $version" }
        val algLen = envelope[off++].toInt() and 0xFF
        require(algLen in 1..64 && off + algLen <= envelope.size) { "Invalid algorithm ID length" }
        val algorithmId = String(envelope, off, algLen, Charsets.UTF_8)
        off += algLen
        require(envelope.size >= off + EPHEMERAL_PUBKEY_SIZE + NONCE_SIZE + 16) { "Envelope too short" }
        val ephemeralPubkey = envelope.copyOfRange(off, off + EPHEMERAL_PUBKEY_SIZE)
        off += EPHEMERAL_PUBKEY_SIZE
        val nonce = envelope.copyOfRange(off, off + NONCE_SIZE)
        off += NONCE_SIZE
        val ctWithTag = envelope.copyOfRange(off, envelope.size)
        return ParsedAsymmetricEnvelope(algorithmId, ephemeralPubkey, nonce, ctWithTag)
    }
}
