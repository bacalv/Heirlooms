package digital.heirlooms.crypto

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.KeyAgreement

class DeviceKeyManager(private val context: Context) {

    companion object {
        private const val KEYSTORE_ALIAS = "HeirloomsVaultKey"
        private const val PREFS_NAME = "heirlooms_vault"
        private const val KEY_WRAPPED_MASTER = "wm"
        private const val KEY_WRAPPED_DEVICE_PRIV = "dp"
        private const val KEY_DEVICE_PUB_SPKI = "dpub"
        private const val KEY_DEVICE_ID = "did"
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    val deviceId: String
        get() = prefs.getString(KEY_DEVICE_ID, null)
            ?: UUID.randomUUID().toString().also { id ->
                prefs.edit().putString(KEY_DEVICE_ID, id).apply()
            }

    val deviceLabel: String get() = "${Build.MANUFACTURER} ${Build.MODEL}"

    fun isVaultSetUp(): Boolean = prefs.contains(KEY_WRAPPED_MASTER)

    // ---- Keystore AES-256 local wrap ----------------------------------------

    private fun getOrCreateKeystoreKey(): javax.crypto.SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }
        return if (ks.containsAlias(KEYSTORE_ALIAS)) {
            ks.getKey(KEYSTORE_ALIAS, null) as javax.crypto.SecretKey
        } else {
            val spec = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build()
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                .apply { init(spec) }
                .generateKey()
        }
    }

    private fun keystoreEncrypt(plaintext: ByteArray): ByteArray {
        val key = getOrCreateKeystoreKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val nonce = cipher.iv  // 12-byte GCM IV chosen by the Keystore
        return nonce + cipher.doFinal(plaintext)
    }

    private fun keystoreDecrypt(nonceAndCt: ByteArray): ByteArray {
        val key = getOrCreateKeystoreKey()
        val nonce = nonceAndCt.copyOfRange(0, 12)
        val ct = nonceAndCt.copyOfRange(12, nonceAndCt.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce))
        return cipher.doFinal(ct)
    }

    // ---- Vault setup --------------------------------------------------------

    // One-time setup. Stores the master key wrapped by the Keystore AES key.
    // Also generates and stores a software P-256 device keypair (private wrapped).
    // Returns device P-256 public key bytes (SEC1 uncompressed, 65 bytes).
    fun setupVault(masterKey: ByteArray): ByteArray {
        val wrappedMaster = keystoreEncrypt(masterKey)
        prefs.edit().putString(KEY_WRAPPED_MASTER, wrappedMaster.toB64()).apply()

        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val kp = kpg.generateKeyPair()

        // Store private key encrypted
        val wrappedPriv = keystoreEncrypt(kp.private.encoded)
        prefs.edit().putString(KEY_WRAPPED_DEVICE_PRIV, wrappedPriv.toB64()).apply()

        // Store public key (SPKI DER) in plaintext — it is not a secret
        prefs.edit().putString(KEY_DEVICE_PUB_SPKI, kp.public.encoded.toB64()).apply()

        return toSec1(kp.public as ECPublicKey)
    }

    // Load master key from SharedPreferences and unwrap via Keystore AES key.
    fun loadMasterKey(): ByteArray? {
        val b64 = prefs.getString(KEY_WRAPPED_MASTER, null) ?: return null
        return keystoreDecrypt(b64.fromB64())
    }

    // X.509 SPKI DER bytes (for server registration `pubkey` field).
    fun getDevicePublicKeySpki(): ByteArray? =
        prefs.getString(KEY_DEVICE_PUB_SPKI, null)?.fromB64()

    // SEC1 uncompressed P-256 (65 bytes: 0x04 || x || y).
    fun getDevicePublicKeySec1(): ByteArray? {
        val spki = getDevicePublicKeySpki() ?: return null
        val pub = KeyFactory.getInstance("EC").generatePublic(
            java.security.spec.X509EncodedKeySpec(spki)
        ) as ECPublicKey
        return toSec1(pub)
    }

    // ---- Server registration wrapping ---------------------------------------

    // Wrap the master key under the device static public key.
    // Algorithm: p256-ecdh-hkdf-aes256gcm-v1
    // Uses ECDH(ephemeral_private, device_static_public) in software.
    fun wrapMasterKeyForServer(masterKey: ByteArray): ByteArray {
        val spki = getDevicePublicKeySpki() ?: error("Vault not set up")
        val deviceStaticPub = KeyFactory.getInstance("EC").generatePublic(
            java.security.spec.X509EncodedKeySpec(spki)
        )

        // Ephemeral keypair in software
        val ephKpg = KeyPairGenerator.getInstance("EC")
        ephKpg.initialize(ECGenParameterSpec("secp256r1"))
        val ephKp = ephKpg.generateKeyPair()
        val ephPubSec1 = toSec1(ephKp.public as ECPublicKey)

        // ECDH: ephemeral_private × device_static_public → shared secret
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(ephKp.private)
        ka.doPhase(deviceStaticPub, true)
        val sharedSecret = ka.generateSecret()

        // HKDF → KEK
        val kek = VaultCrypto.hkdf(sharedSecret, info = "heirlooms-v1".toByteArray(Charsets.UTF_8))

        // AES-256-GCM encrypt master key
        val nonce = VaultCrypto.generateNonce()
        val ct = VaultCrypto.aesGcmEncrypt(kek, nonce, masterKey)

        return VaultCrypto.buildAsymmetricEnvelope(VaultCrypto.ALG_P256_ECDH_HKDF_V1, ephPubSec1, nonce, ct)
    }

    // ---- Helpers ------------------------------------------------------------

    private fun toSec1(pub: ECPublicKey): ByteArray {
        val x = pub.w.affineX.toByteArray().stripSignByte().padTo(32)
        val y = pub.w.affineY.toByteArray().stripSignByte().padTo(32)
        return byteArrayOf(0x04.toByte()) + x + y
    }

    // BigInteger.toByteArray() may prepend a 0x00 sign byte for positive numbers.
    private fun ByteArray.stripSignByte(): ByteArray =
        if (this.size == 33 && this[0] == 0.toByte()) copyOfRange(1, size) else this

    private fun ByteArray.padTo(size: Int): ByteArray =
        if (this.size >= size) this else ByteArray(size - this.size) + this

    private fun ByteArray.toB64(): String = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.fromB64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)
}
