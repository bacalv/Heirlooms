package digital.heirlooms.crypto

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap

object VaultSession {

    @Volatile private var _masterKey: ByteArray? = null
    @Volatile private var _sharingPrivkey: ByteArray? = null

    val isUnlocked: Boolean get() = _masterKey != null

    // Throws if vault is not unlocked.
    val masterKey: ByteArray
        get() = _masterKey?.copyOf() ?: error("Vault is not unlocked")

    // PKCS8 DER bytes of the account-level sharing private key.
    // Null until loaded from server after vault unlock.
    val sharingPrivkey: ByteArray?
        get() = _sharingPrivkey?.copyOf()

    // In-memory cache of decrypted thumbnails, keyed by upload ID.
    // Cleared when the vault is locked (process restart).
    val thumbnailCache: ConcurrentHashMap<String, ImageBitmap> = ConcurrentHashMap()

    // Per-plot raw AES-256-GCM plot keys, keyed by plot ID.
    // Populated lazily on first shared-plot access. Cleared on vault lock.
    val plotKeys: ConcurrentHashMap<String, ByteArray> = ConcurrentHashMap()

    // Increments each time a plot key is added. Observed by HeirloomsImage to retry
    // thumbnail decryption for items whose key wasn't cached on first render.
    private val _plotKeysVersion = MutableStateFlow(0)
    val plotKeysVersion: StateFlow<Int> get() = _plotKeysVersion

    fun setPlotKey(plotId: String, rawKeyBytes: ByteArray) {
        plotKeys[plotId] = rawKeyBytes.copyOf()
        _plotKeysVersion.value++
    }

    fun getPlotKey(plotId: String): ByteArray? = plotKeys[plotId]?.copyOf()

    fun unlock(masterKey: ByteArray) {
        _masterKey = masterKey.copyOf()
    }

    fun setSharingPrivkey(pkcs8Bytes: ByteArray) {
        _sharingPrivkey = pkcs8Bytes.copyOf()
    }

    fun lock() {
        _masterKey?.fill(0)
        _masterKey = null
        _sharingPrivkey?.fill(0)
        _sharingPrivkey = null
        thumbnailCache.clear()
        plotKeys.values.forEach { it.fill(0) }
        plotKeys.clear()
        _plotKeysVersion.value = 0
    }
}
