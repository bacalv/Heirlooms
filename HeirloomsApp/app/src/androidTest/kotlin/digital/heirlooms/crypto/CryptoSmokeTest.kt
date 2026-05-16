package digital.heirlooms.crypto

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * CryptoSmokeTest — Espresso instrumented smoke test for the VaultCrypto primitives.
 *
 * Run on every build as a canary: if any of the three core envelope operations
 * (symmetric encrypt, DEK wrap/unwrap, passphrase key derivation) produce structurally
 * invalid output, this test fails immediately and blocks the build.
 *
 * These tests run in-process on the device under test, which means they exercise the
 * same BouncyCastle + JCE stack that production code uses. They do NOT require network
 * access or a vault passphrase to be set up.
 *
 * Run with:
 *   ./gradlew :HeirloomsApp:connectedAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=digital.heirlooms.crypto.CryptoSmokeTest
 *
 * Or via the Makefile:
 *   make test-farm-smoke   (runs Maestro flows AND this test suite)
 */
@RunWith(AndroidJUnit4::class)
class CryptoSmokeTest {

    // ---- 1. DEK encrypt produces a valid symmetric envelope -----------------

    /**
     * encryptSymmetric with ALG_AES256GCM_V1 must return a byte array whose:
     *   [0]  == 1  (ENVELOPE_VERSION)
     *   [1]  == length of the algorithm ID string (for aes256gcm-v1: 12)
     *   [2..2+algLen-1] == the UTF-8 algorithm ID string
     * And decryptSymmetric must recover the original plaintext.
     */
    @Test
    fun dekEncryptProducesValidEnvelope() {
        val plaintext = "smoke-test".toByteArray(Charsets.UTF_8)
        val dek = VaultCrypto.generateDek()

        val envelope = VaultCrypto.encryptSymmetric(VaultCrypto.ALG_AES256GCM_V1, dek, plaintext)

        // Envelope version byte must be 1.
        assertThat(envelope[0].toInt() and 0xFF).isEqualTo(1)

        // Second byte is the algorithm ID length.
        val algLen = envelope[1].toInt() and 0xFF
        val algId = String(envelope, 2, algLen, Charsets.UTF_8)
        assertThat(algId).isEqualTo(VaultCrypto.ALG_AES256GCM_V1)

        // Envelope must be long enough to contain version + algLen byte + algId + nonce(12) + at least 16-byte GCM tag.
        assertThat(envelope.size).isAtLeast(2 + algLen + 12 + 16)

        // Decryption must recover the original plaintext.
        val decrypted = VaultCrypto.decryptSymmetric(envelope, dek)
        assertThat(decrypted).isEqualTo(plaintext)
    }

    // ---- 2. DEK generation is random ----------------------------------------

    /**
     * generateDek must return 32 bytes, and two consecutive calls must differ
     * (i.e. the RNG is not producing a constant value).
     */
    @Test
    fun dekGenerationIsRandom() {
        val dek1 = VaultCrypto.generateDek()
        val dek2 = VaultCrypto.generateDek()
        assertThat(dek1).hasLength(32)
        assertThat(dek1).isNotEqualTo(dek2)
    }

    // ---- 3. Master-key derivation is deterministic --------------------------

    /**
     * deriveAuthAndMasterKeys must be deterministic for the same passphrase + salt:
     * two calls with the same inputs must produce identical output pairs.
     * The auth key and master key seed must be distinct 32-byte values.
     */
    @Test
    fun masterKeyDeriveIsConsistent() {
        val passphrase = "test-passphrase-smoke"
        val salt = VaultCrypto.generateSalt(16)

        val (authKey1, masterKeySeed1) = VaultCrypto.deriveAuthAndMasterKeys(passphrase, salt)
        val (authKey2, masterKeySeed2) = VaultCrypto.deriveAuthAndMasterKeys(passphrase, salt)

        // Deterministic
        assertThat(authKey1).isEqualTo(authKey2)
        assertThat(masterKeySeed1).isEqualTo(masterKeySeed2)

        // Auth key and master key seed must be 32 bytes each and must differ from each other.
        assertThat(authKey1).hasLength(32)
        assertThat(masterKeySeed1).hasLength(32)
        assertThat(authKey1).isNotEqualTo(masterKeySeed1)
    }

    /**
     * A different passphrase must produce different derived keys (sanity check
     * that Argon2id is actually using the passphrase input).
     */
    @Test
    fun masterKeyDeriveIsSensitiveToPassphrase() {
        val salt = VaultCrypto.generateSalt(16)
        val (authKey1, _) = VaultCrypto.deriveAuthAndMasterKeys("passphrase-a", salt)
        val (authKey2, _) = VaultCrypto.deriveAuthAndMasterKeys("passphrase-b", salt)
        assertThat(authKey1).isNotEqualTo(authKey2)
    }

    // ---- 4. DEK wrap / unwrap round-trip ------------------------------------

    /**
     * wrapDekUnderMasterKey / unwrapDekWithMasterKey must recover the original DEK bytes.
     * The wrapped envelope must use the master-aes256gcm-v1 algorithm tag.
     */
    @Test
    fun dekWrapUnwrapRoundTrip() {
        val dek = VaultCrypto.generateDek()
        val masterKey = VaultCrypto.generateMasterKey()

        val wrapped = VaultCrypto.wrapDekUnderMasterKey(dek, masterKey)

        // Structural check: version byte and algorithm tag
        assertThat(wrapped[0].toInt() and 0xFF).isEqualTo(1)
        val algLen = wrapped[1].toInt() and 0xFF
        val algId = String(wrapped, 2, algLen, Charsets.UTF_8)
        assertThat(algId).isEqualTo(VaultCrypto.ALG_MASTER_AES256GCM_V1)

        val unwrapped = VaultCrypto.unwrapDekWithMasterKey(wrapped, masterKey)
        assertThat(unwrapped).isEqualTo(dek)
    }

    // ---- 5. Plot-key wrap / unwrap round-trip -------------------------------

    /**
     * wrapDekWithPlotKey / unwrapDekWithPlotKey must round-trip correctly and
     * use the plot-aes256gcm-v1 algorithm tag.
     */
    @Test
    fun plotKeyDekWrapUnwrapRoundTrip() {
        val dek = VaultCrypto.generateDek()
        val plotKey = VaultCrypto.generatePlotKey()

        val wrapped = VaultCrypto.wrapDekWithPlotKey(dek, plotKey)

        assertThat(wrapped[0].toInt() and 0xFF).isEqualTo(1)
        val algLen = wrapped[1].toInt() and 0xFF
        val algId = String(wrapped, 2, algLen, Charsets.UTF_8)
        assertThat(algId).isEqualTo(VaultCrypto.ALG_PLOT_AES256GCM_V1)

        val unwrapped = VaultCrypto.unwrapDekWithPlotKey(wrapped, plotKey)
        assertThat(unwrapped).isEqualTo(dek)
    }

    // ---- 6. Passphrase wrap round-trip --------------------------------------

    /**
     * wrapMasterKeyWithPassphrase / unwrapMasterKeyWithPassphrase must recover the
     * original master key, and the outer envelope must carry the argon2id algorithm tag.
     */
    @Test
    fun passphraseWrapUnwrapRoundTrip() {
        val masterKey = VaultCrypto.generateMasterKey()
        val passphrase = "smoke-passphrase-vault".toCharArray()

        val wrap = VaultCrypto.wrapMasterKeyWithPassphrase(masterKey, passphrase)

        // Version byte
        assertThat(wrap.envelope[0].toInt() and 0xFF).isEqualTo(1)
        // Algorithm tag
        val algLen = wrap.envelope[1].toInt() and 0xFF
        val algId = String(wrap.envelope, 2, algLen, Charsets.UTF_8)
        assertThat(algId).isEqualTo(VaultCrypto.ALG_ARGON2ID_AES256GCM_V1)
        // Salt is 16 bytes
        assertThat(wrap.salt).hasLength(16)

        val recovered = VaultCrypto.unwrapMasterKeyWithPassphrase(
            wrap.envelope, passphrase, wrap.salt, wrap.params
        )
        assertThat(recovered).isEqualTo(masterKey)
    }

    // ---- 7. HKDF is deterministic ------------------------------------------

    /**
     * hkdf must be deterministic for the same IKM + info, and different IKMs
     * must produce different outputs.
     */
    @Test
    fun hkdfIsDeterministic() {
        val ikm = ByteArray(32) { 0xAB.toByte() }
        val info = "heirlooms-v1".toByteArray(Charsets.UTF_8)

        val out1 = VaultCrypto.hkdf(ikm, info = info)
        val out2 = VaultCrypto.hkdf(ikm, info = info)

        assertThat(out1).hasLength(32)
        assertThat(out1).isEqualTo(out2)

        val diffIkm = ByteArray(32) { 0xCD.toByte() }
        val out3 = VaultCrypto.hkdf(diffIkm, info = info)
        assertThat(out1).isNotEqualTo(out3)
    }

    // ---- 8. Streaming decrypt round-trip ------------------------------------

    /**
     * A single-chunk streaming encryption + decryptStreamingContent must recover
     * the original plaintext. This exercises the nonce-as-AAD path used by
     * encryptAndUploadStreaming.
     */
    @Test
    fun streamingDecryptRoundTrip() {
        val dek = VaultCrypto.generateDek()
        val plaintext = ByteArray(1024) { it.toByte() }
        val nonce = VaultCrypto.generateNonce()

        // Encrypt one chunk with nonce as AAD (matches streaming production path).
        val ctWithTag = VaultCrypto.aesGcmEncryptWithAad(dek, nonce, nonce, plaintext)
        val chunk = nonce + ctWithTag

        val recovered = VaultCrypto.decryptStreamingContent(chunk, dek)
        assertThat(recovered).isEqualTo(plaintext)
    }
}
