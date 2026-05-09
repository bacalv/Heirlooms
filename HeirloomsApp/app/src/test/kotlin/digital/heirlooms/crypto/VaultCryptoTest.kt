package digital.heirlooms.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.security.interfaces.ECPublicKey
import javax.crypto.KeyAgreement

class VaultCryptoTest {

    @Test
    fun `generateMasterKey returns 32 bytes and two calls differ`() {
        val a = VaultCrypto.generateMasterKey()
        val b = VaultCrypto.generateMasterKey()
        assertTrue(a.size == 32)
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun `generateDek returns 32 bytes distinct from master key`() {
        val mk = VaultCrypto.generateMasterKey()
        val dek = VaultCrypto.generateDek()
        assertTrue(dek.size == 32)
        assertFalse(mk.contentEquals(dek))
    }

    @Test
    fun `aesGcmEncrypt decrypt round-trip`() {
        val key = VaultCrypto.generateMasterKey()
        val nonce = VaultCrypto.generateNonce()
        val plaintext = "Hello, Heirlooms vault!".toByteArray()
        val ct = VaultCrypto.aesGcmEncrypt(key, nonce, plaintext)
        val recovered = VaultCrypto.aesGcmDecrypt(key, nonce, ct)
        assertArrayEquals(plaintext, recovered)
    }

    @Test(expected = javax.crypto.AEADBadTagException::class)
    fun `aesGcmDecrypt with wrong key throws`() {
        val key = VaultCrypto.generateMasterKey()
        val wrongKey = VaultCrypto.generateMasterKey()
        val nonce = VaultCrypto.generateNonce()
        val ct = VaultCrypto.aesGcmEncrypt(key, nonce, byteArrayOf(1, 2, 3))
        VaultCrypto.aesGcmDecrypt(wrongKey, nonce, ct)
    }

    @Test
    fun `buildSymmetricEnvelope has correct structure`() {
        val algId = VaultCrypto.ALG_AES256GCM_V1
        val nonce = ByteArray(12) { it.toByte() }
        val ct = ByteArray(32) { (it + 100).toByte() }
        val env = VaultCrypto.buildSymmetricEnvelope(algId, nonce, ct)

        val version = env[0].toInt() and 0xFF
        val algLen = env[1].toInt() and 0xFF
        val algBytes = env.copyOfRange(2, 2 + algLen)

        assertTrue(version == 1)
        assertTrue(String(algBytes) == algId)
    }

    @Test
    fun `encryptSymmetric then decryptSymmetric round-trip`() {
        val key = VaultCrypto.generateMasterKey()
        val plaintext = "secret data".toByteArray()
        val envelope = VaultCrypto.encryptSymmetric(VaultCrypto.ALG_AES256GCM_V1, key, plaintext)
        val recovered = VaultCrypto.decryptSymmetric(envelope, key)
        assertArrayEquals(plaintext, recovered)
    }

    @Test
    fun `wrapDekUnderMasterKey unwrapDekWithMasterKey round-trip`() {
        val masterKey = VaultCrypto.generateMasterKey()
        val dek = VaultCrypto.generateDek()
        val wrapped = VaultCrypto.wrapDekUnderMasterKey(dek, masterKey)
        val recovered = VaultCrypto.unwrapDekWithMasterKey(wrapped, masterKey)
        assertArrayEquals(dek, recovered)
    }

    @Test(expected = Exception::class)
    fun `decryptSymmetric with wrong key throws`() {
        val key = VaultCrypto.generateMasterKey()
        val wrongKey = VaultCrypto.generateMasterKey()
        val envelope = VaultCrypto.encryptSymmetric(VaultCrypto.ALG_AES256GCM_V1, key, byteArrayOf(1, 2, 3))
        VaultCrypto.decryptSymmetric(envelope, wrongKey)
    }

    @Test
    fun `hkdf is deterministic for same inputs and differs for different IKMs`() {
        val ikm1 = ByteArray(32) { 0xAB.toByte() }
        val ikm2 = ByteArray(32) { 0xCD.toByte() }
        val info = "heirlooms-v1".toByteArray()
        val out1a = VaultCrypto.hkdf(ikm1, info = info)
        val out1b = VaultCrypto.hkdf(ikm1, info = info)
        val out2 = VaultCrypto.hkdf(ikm2, info = info)
        assertTrue(out1a.size == 32)
        assertArrayEquals(out1a, out1b)
        assertFalse(out1a.contentEquals(out2))
    }

    @Test
    fun `wrapMasterKeyWithPassphrase returns valid envelope`() {
        val masterKey = VaultCrypto.generateMasterKey()
        val passphrase = "correct-horse-battery-staple".toCharArray()
        val wrap = VaultCrypto.wrapMasterKeyWithPassphrase(masterKey, passphrase)
        assertTrue(wrap.envelope.isNotEmpty())
        assertTrue(wrap.salt.size == 16)
        assertTrue(wrap.envelope[0].toInt() and 0xFF == 1)  // version byte
    }

    @Test
    fun `wrapMasterKeyWithPassphrase unwrapMasterKeyWithPassphrase round-trip`() {
        val masterKey = VaultCrypto.generateMasterKey()
        val passphrase = "correct-horse-battery-staple".toCharArray()
        val wrap = VaultCrypto.wrapMasterKeyWithPassphrase(masterKey, passphrase)
        val recovered = VaultCrypto.unwrapMasterKeyWithPassphrase(
            wrap.envelope, passphrase, wrap.salt, wrap.params
        )
        assertArrayEquals(masterKey, recovered)
    }

    @Test(expected = Exception::class)
    fun `unwrapMasterKeyWithPassphrase wrong passphrase throws`() {
        val masterKey = VaultCrypto.generateMasterKey()
        val passphrase = "correct-horse-battery-staple".toCharArray()
        val wrongPassphrase = "wrong-passphrase".toCharArray()
        val wrap = VaultCrypto.wrapMasterKeyWithPassphrase(masterKey, passphrase)
        VaultCrypto.unwrapMasterKeyWithPassphrase(wrap.envelope, wrongPassphrase, wrap.salt, wrap.params)
    }

    @Test
    fun `buildAsymmetricEnvelope parseAsymmetricEnvelope round-trip`() {
        val algId = VaultCrypto.ALG_P256_ECDH_HKDF_V1
        val ephPub = ByteArray(65) { (it + 4).toByte() }.also { it[0] = 0x04 }
        val nonce = ByteArray(12) { it.toByte() }
        val ct = ByteArray(48) { it.toByte() }
        val env = VaultCrypto.buildAsymmetricEnvelope(algId, ephPub, nonce, ct)
        val parsed = VaultCrypto.parseAsymmetricEnvelope(env)
        assertTrue(parsed.algorithmId == algId)
        assertArrayEquals(ephPub, parsed.ephemeralPubkeyBytes)
        assertArrayEquals(nonce, parsed.nonce)
        assertArrayEquals(ct, parsed.ciphertextWithTag)
    }

    @Test
    fun `asymmetric envelope ECDH round-trip encrypts and decrypts master key`() {
        // Simulate device keypair (static) in software
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val deviceKp = kpg.generateKeyPair()
        val devicePub = deviceKp.public as ECPublicKey

        // Encode device public key as SEC1 uncompressed
        val x = devicePub.w.affineX.toByteArray().let { if (it.size == 33) it.copyOfRange(1, 33) else it.padTo(32) }
        val y = devicePub.w.affineY.toByteArray().let { if (it.size == 33) it.copyOfRange(1, 33) else it.padTo(32) }
        val devicePubSec1 = byteArrayOf(0x04) + x + y

        // Wrap master key under device public key (ephemeral)
        val masterKey = VaultCrypto.generateMasterKey()
        val ephKpg = KeyPairGenerator.getInstance("EC")
        ephKpg.initialize(ECGenParameterSpec("secp256r1"))
        val ephKp = ephKpg.generateKeyPair()
        val ephPub = ephKp.public as ECPublicKey
        val ephPubX = ephPub.w.affineX.toByteArray().let { if (it.size == 33) it.copyOfRange(1, 33) else it.padTo(32) }
        val ephPubY = ephPub.w.affineY.toByteArray().let { if (it.size == 33) it.copyOfRange(1, 33) else it.padTo(32) }
        val ephPubSec1 = byteArrayOf(0x04) + ephPubX + ephPubY

        // ECDH: eph_private × device_public → shared_secret (same as device_private × eph_public)
        val ka1 = KeyAgreement.getInstance("ECDH")
        ka1.init(ephKp.private)
        ka1.doPhase(deviceKp.public, true)
        val sharedSecret1 = ka1.generateSecret()

        val kek = VaultCrypto.hkdf(sharedSecret1, info = "heirlooms-v1".toByteArray())
        val nonce = VaultCrypto.generateNonce()
        val ct = VaultCrypto.aesGcmEncrypt(kek, nonce, masterKey)
        val envelope = VaultCrypto.buildAsymmetricEnvelope(VaultCrypto.ALG_P256_ECDH_HKDF_V1, ephPubSec1, nonce, ct)

        // Unwrap using device private key + ephemeral public from envelope
        val parsed = VaultCrypto.parseAsymmetricEnvelope(envelope)
        val ephPubFromEnvelope = decodeEcSec1(parsed.ephemeralPubkeyBytes)
        val ka2 = KeyAgreement.getInstance("ECDH")
        ka2.init(deviceKp.private)
        ka2.doPhase(ephPubFromEnvelope, true)
        val sharedSecret2 = ka2.generateSecret()
        val kek2 = VaultCrypto.hkdf(sharedSecret2, info = "heirlooms-v1".toByteArray())
        val recovered = VaultCrypto.aesGcmDecrypt(kek2, parsed.nonce, parsed.ciphertextWithTag)

        assertArrayEquals(masterKey, recovered)
    }

    private fun decodeEcSec1(bytes: ByteArray): java.security.PublicKey {
        val x = java.math.BigInteger(1, bytes.copyOfRange(1, 33))
        val y = java.math.BigInteger(1, bytes.copyOfRange(33, 65))
        val params = java.security.AlgorithmParameters.getInstance("EC")
        params.init(ECGenParameterSpec("secp256r1"))
        val curveParams = params.getParameterSpec(java.security.spec.ECParameterSpec::class.java)
        val spec = java.security.spec.ECPublicKeySpec(java.security.spec.ECPoint(x, y), curveParams)
        return java.security.KeyFactory.getInstance("EC").generatePublic(spec)
    }

    private fun ByteArray.padTo(size: Int): ByteArray =
        if (this.size >= size) this else ByteArray(size - this.size) + this
}
