package digital.heirlooms.crypto

import androidx.compose.ui.graphics.ImageBitmap
import java.util.concurrent.ConcurrentHashMap

object VaultSession {

    @Volatile private var _masterKey: ByteArray? = null

    val isUnlocked: Boolean get() = _masterKey != null

    // Throws if vault is not unlocked.
    val masterKey: ByteArray
        get() = _masterKey?.copyOf() ?: error("Vault is not unlocked")

    // In-memory cache of decrypted thumbnails, keyed by upload ID.
    // Cleared when the vault is locked (process restart).
    val thumbnailCache: ConcurrentHashMap<String, ImageBitmap> = ConcurrentHashMap()

    fun unlock(masterKey: ByteArray) {
        _masterKey = masterKey.copyOf()
    }

    fun lock() {
        _masterKey?.fill(0)
        _masterKey = null
        thumbnailCache.clear()
    }
}
