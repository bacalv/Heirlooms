package digital.heirlooms.server

import digital.heirlooms.server.crypto.AlgorithmIds
import digital.heirlooms.server.crypto.EnvelopeFormat
import digital.heirlooms.server.crypto.EnvelopeFormatException
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.security.SecureRandom

class EnvelopeFormatTest {

    private val rng = SecureRandom()
    private fun randomBytes(n: Int) = ByteArray(n).also { rng.nextBytes(it) }

    // Build a well-formed symmetric envelope from parts.
    private fun symEnvelope(algId: String, nonce: ByteArray, ciphertext: ByteArray, authTag: ByteArray): ByteArray {
        val algBytes = algId.toByteArray(Charsets.UTF_8)
        return byteArrayOf(0x01, algBytes.size.toByte()) + algBytes + nonce + ciphertext + authTag
    }

    // Build a well-formed asymmetric envelope from parts.
    private fun asymEnvelope(algId: String, ephPubkey: ByteArray, nonce: ByteArray, ciphertext: ByteArray, authTag: ByteArray): ByteArray {
        val algBytes = algId.toByteArray(Charsets.UTF_8)
        return byteArrayOf(0x01, algBytes.size.toByte()) + algBytes + ephPubkey + nonce + ciphertext + authTag
    }

    // Real AES-256-GCM encryption via BouncyCastle; returns (ciphertext, authTag).
    private fun aesGcmEncrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = GCMBlockCipher.newInstance(AESEngine.newInstance())
        cipher.init(true, AEADParameters(KeyParameter(key), 128, nonce))
        val output = ByteArray(cipher.getOutputSize(plaintext.size))
        var len = cipher.processBytes(plaintext, 0, plaintext.size, output, 0)
        len += cipher.doFinal(output, len)
        val ct = output.copyOfRange(0, len - 16)
        val tag = output.copyOfRange(len - 16, len)
        return ct to tag
    }

    // Build a 65-byte SEC1 uncompressed point (0x04 prefix + 32-byte x + 32-byte y).
    // The validator checks length only — curve-point validity is the client's concern.
    private fun p256EphemeralPubkey(): ByteArray = byteArrayOf(0x04) + randomBytes(64)

    // ---- Happy-path round-trip tests (BouncyCastle constructs; EnvelopeFormat parses) ----

    @Test
    fun `symmetric aes256gcm-v1 envelope parses correctly`() {
        val key = randomBytes(32)
        val nonce = randomBytes(12)
        val (ciphertext, authTag) = aesGcmEncrypt(key, nonce, "hello".toByteArray())
        val blob = symEnvelope(AlgorithmIds.AES256GCM_V1, nonce, ciphertext, authTag)

        val parsed = EnvelopeFormat.validateSymmetric(blob)

        assertEquals(1, parsed.version)
        assertEquals(AlgorithmIds.AES256GCM_V1, parsed.algorithmId)
        assertArrayEquals(nonce, parsed.nonce)
        assertArrayEquals(ciphertext, parsed.ciphertext)
        assertArrayEquals(authTag, parsed.authTag)
    }

    @Test
    fun `symmetric master-aes256gcm-v1 envelope parses correctly`() {
        val key = randomBytes(32)
        val nonce = randomBytes(12)
        val (ciphertext, authTag) = aesGcmEncrypt(key, nonce, randomBytes(32))
        val blob = symEnvelope(AlgorithmIds.MASTER_AES256GCM_V1, nonce, ciphertext, authTag)

        val parsed = EnvelopeFormat.validateSymmetric(blob)

        assertEquals(AlgorithmIds.MASTER_AES256GCM_V1, parsed.algorithmId)
        assertArrayEquals(nonce, parsed.nonce)
    }

    @Test
    fun `asymmetric p256-ecdh-hkdf-aes256gcm-v1 envelope parses correctly`() {
        val ephPubkey = p256EphemeralPubkey()
        val nonce = randomBytes(12)
        val key = randomBytes(32)
        val (ciphertext, authTag) = aesGcmEncrypt(key, nonce, randomBytes(32))
        val blob = asymEnvelope(AlgorithmIds.P256_ECDH_HKDF_AES256GCM_V1, ephPubkey, nonce, ciphertext, authTag)

        val parsed = EnvelopeFormat.validateAsymmetric(blob)

        assertEquals(AlgorithmIds.P256_ECDH_HKDF_AES256GCM_V1, parsed.algorithmId)
        assertArrayEquals(ephPubkey, parsed.ephemeralPubkey)
        assertEquals(65, parsed.ephemeralPubkey.size)
        assertArrayEquals(nonce, parsed.nonce)
    }

    @Test
    fun `expectedAlgorithmId matching passes validation`() {
        val blob = symEnvelope(AlgorithmIds.AES256GCM_V1, randomBytes(12), randomBytes(8), randomBytes(16))
        EnvelopeFormat.validateSymmetric(blob, AlgorithmIds.AES256GCM_V1)  // no exception
    }

    @Test
    fun `expectedAlgorithmId mismatch throws EnvelopeFormatException`() {
        val blob = symEnvelope(AlgorithmIds.AES256GCM_V1, randomBytes(12), randomBytes(8), randomBytes(16))
        assertThrows<EnvelopeFormatException> {
            EnvelopeFormat.validateSymmetric(blob, AlgorithmIds.MASTER_AES256GCM_V1)
        }
    }

    // ---- Version errors ----

    @Test
    fun `version byte 0x00 is rejected`() {
        val blob = symEnvelope(AlgorithmIds.AES256GCM_V1, randomBytes(12), randomBytes(8), randomBytes(16))
        blob[0] = 0x00
        assertThrows<EnvelopeFormatException> { EnvelopeFormat.validateSymmetric(blob) }
    }

    @Test
    fun `version byte 0x02 is rejected`() {
        val blob = symEnvelope(AlgorithmIds.AES256GCM_V1, randomBytes(12), randomBytes(8), randomBytes(16))
        blob[0] = 0x02
        assertThrows<EnvelopeFormatException> { EnvelopeFormat.validateSymmetric(blob) }
    }

    // ---- Algorithm ID errors ----

    @Test
    fun `unknown algorithm ID throws EnvelopeFormatException`() {
        val blob = symEnvelope("foo-v9", randomBytes(12), randomBytes(8), randomBytes(16))
        assertThrows<EnvelopeFormatException> { EnvelopeFormat.validateSymmetric(blob) }
    }

    @Test
    fun `algorithm ID string longer than 64 bytes is rejected`() {
        val longId = "a".repeat(65)
        val algBytes = longId.toByteArray(Charsets.UTF_8)
        val blob = byteArrayOf(0x01, algBytes.size.toByte()) + algBytes + randomBytes(12) + randomBytes(8) + randomBytes(16)
        assertThrows<EnvelopeFormatException> { EnvelopeFormat.validateSymmetric(blob) }
    }

    @Test
    fun `algorithm ID length byte exceeding blob size is rejected`() {
        val algBytes = AlgorithmIds.AES256GCM_V1.toByteArray(Charsets.UTF_8)
        // Claim the alg ID is much longer than it actually is
        val blob = byteArrayOf(0x01, 0x7F) + algBytes + randomBytes(12) + randomBytes(8) + randomBytes(16)
        assertThrows<EnvelopeFormatException> { EnvelopeFormat.validateSymmetric(blob) }
    }

    @Test
    fun `validateSymmetric rejects asymmetric algorithm ID`() {
        val blob = symEnvelope(AlgorithmIds.P256_ECDH_HKDF_AES256GCM_V1, randomBytes(12), randomBytes(8), randomBytes(16))
        assertThrows<EnvelopeFormatException> { EnvelopeFormat.validateSymmetric(blob) }
    }

    @Test
    fun `validateAsymmetric rejects symmetric algorithm ID`() {
        val ephPubkey = p256EphemeralPubkey()
        val blob = asymEnvelope(AlgorithmIds.AES256GCM_V1, ephPubkey, randomBytes(12), randomBytes(8), randomBytes(16))
        assertThrows<EnvelopeFormatException> { EnvelopeFormat.validateAsymmetric(blob) }
    }

    // ---- Structural size errors ----

    @Test
    fun `blob with only 8 bytes after header is rejected`() {
        val algBytes = AlgorithmIds.AES256GCM_V1.toByteArray(Charsets.UTF_8)
        // 8 bytes < NONCE_LENGTH(12) + AUTH_TAG_LENGTH(16) = 28 minimum after header
        val blob = byteArrayOf(0x01, algBytes.size.toByte()) + algBytes + randomBytes(8)
        assertThrows<EnvelopeFormatException> { EnvelopeFormat.validateSymmetric(blob) }
    }

    @Test
    fun `blob truncated to just header is rejected`() {
        val algBytes = AlgorithmIds.AES256GCM_V1.toByteArray(Charsets.UTF_8)
        val blob = byteArrayOf(0x01, algBytes.size.toByte()) + algBytes  // no nonce, no tag
        assertThrows<EnvelopeFormatException> { EnvelopeFormat.validateSymmetric(blob) }
    }

    @Test
    fun `completely empty blob is rejected`() {
        assertThrows<EnvelopeFormatException> { EnvelopeFormat.validateSymmetric(byteArrayOf()) }
    }

    @Test
    fun `asymmetric blob too short for 65-byte P-256 pubkey is rejected`() {
        val algBytes = AlgorithmIds.P256_ECDH_HKDF_AES256GCM_V1.toByteArray(Charsets.UTF_8)
        // 64 bytes after the header = one byte short of pubkey(65) alone, let alone nonce+tag
        val blob = byteArrayOf(0x01, algBytes.size.toByte()) + algBytes + randomBytes(64)
        assertThrows<EnvelopeFormatException> { EnvelopeFormat.validateAsymmetric(blob) }
    }

    // ---- Non-throwing convenience wrappers ----

    @Test
    fun `isValidSymmetric returns false for a structurally bad blob`() {
        assertFalse(EnvelopeFormat.isValidSymmetric(byteArrayOf(0x00, 0x00)))
    }

    @Test
    fun `isValidSymmetric returns true for a well-formed blob`() {
        val blob = symEnvelope(AlgorithmIds.AES256GCM_V1, randomBytes(12), randomBytes(8), randomBytes(16))
        assertTrue(EnvelopeFormat.isValidSymmetric(blob))
    }

    @Test
    fun `isValidAsymmetric returns true for a well-formed asymmetric blob`() {
        val blob = asymEnvelope(AlgorithmIds.P256_ECDH_HKDF_AES256GCM_V1, p256EphemeralPubkey(), randomBytes(12), randomBytes(8), randomBytes(16))
        assertTrue(EnvelopeFormat.isValidAsymmetric(blob))
    }
}
