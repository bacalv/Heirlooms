package digital.heirlooms.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Minimal key/value persistence interface — the only operations EndpointStore needs.
 * The production implementation delegates to SharedPreferences; the test implementation
 * uses a plain in-memory map, removing any need for Robolectric or an Android runtime.
 */
interface PreferenceStore {
    fun getString(key: String, default: String): String
    fun putString(key: String, value: String)
}

/**
 * Production [PreferenceStore] backed by Android SharedPreferences.
 *
 * NOTE: This store is retained only for the one-time plaintext-to-encrypted migration
 * in [EndpointStore.create]. All new writes go through [EncryptedSharedPreferenceStore].
 */
class SharedPreferenceStore(context: Context) : PreferenceStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun getString(key: String, default: String): String =
        prefs.getString(key, default) ?: default

    override fun putString(key: String, value: String) {
        // commit() is synchronous so the write is guaranteed to reach disk before
        // the caller returns — important for the API key, which must survive process death.
        prefs.edit().putString(key, value).commit()
    }

    /** Removes [key] from the plaintext store. Used during migration. */
    fun remove(key: String) {
        prefs.edit().remove(key).commit()
    }

    /** True if the underlying prefs file contains [key]. Used during migration. */
    fun contains(key: String): Boolean = prefs.contains(key)

    companion object {
        const val PREFS_NAME = "heirloom_prefs"
    }
}

/**
 * Production [PreferenceStore] backed by [EncryptedSharedPreferences] (Android Keystore
 * AES-256-GCM). Values are opaque to any filesystem reader, including rooted/forensic access.
 */
class EncryptedSharedPreferenceStore(context: Context) : PreferenceStore {
    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun getString(key: String, default: String): String =
        prefs.getString(key, default) ?: default

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).commit()
    }

    companion object {
        private const val ENCRYPTED_PREFS_NAME = "heirloom_prefs_enc"
    }
}

/**
 * Persists the API key so it survives app restarts.
 *
 * Accepts a [PreferenceStore] so the Android runtime is never required in tests.
 * Use the [EndpointStore.create] factory in production code.
 */
class EndpointStore(private val store: PreferenceStore) {

    fun getApiKey(): String = store.getString(KEY_API_KEY, "")
    fun setApiKey(apiKey: String) = store.putString(KEY_API_KEY, apiKey.trim())

    // ---- M8 session token ---------------------------------------------------

    fun getSessionToken(): String = store.getString(KEY_SESSION_TOKEN, "")
    fun setSessionToken(token: String) = store.putString(KEY_SESSION_TOKEN, token.trim())
    fun clearSessionToken() = store.putString(KEY_SESSION_TOKEN, "")

    fun getUsername(): String = store.getString(KEY_USERNAME, "")
    fun setUsername(username: String) = store.putString(KEY_USERNAME, username.trim())

    fun getDisplayName(): String = store.getString(KEY_DISPLAY_NAME, "")
    fun setDisplayName(name: String) = store.putString(KEY_DISPLAY_NAME, name.trim())

    fun getAuthSalt(): String = store.getString(KEY_AUTH_SALT, "")
    fun setAuthSalt(salt: String) = store.putString(KEY_AUTH_SALT, salt)

    // ---- Other settings ------------------------------------------------------

    fun getWifiOnly(): Boolean = store.getString(KEY_WIFI_ONLY, "false") == "true"
    fun setWifiOnly(enabled: Boolean) = store.putString(KEY_WIFI_ONLY, if (enabled) "true" else "false")

    // Tracks whether the first-launch welcome screen has been acknowledged (once per install).
    fun getWelcomed(): Boolean = store.getString(KEY_WELCOMED, "false") == "true"
    fun setWelcomed(value: Boolean) = store.putString(KEY_WELCOMED, if (value) "true" else "false")

    // Maximum video duration (seconds) to play in full; longer videos use the preview clip.
    // Int.MAX_VALUE = "No limit" — always play the full video.
    fun getVideoPlaybackThreshold(): Int =
        store.getString(KEY_VIDEO_THRESHOLD, DEFAULT_VIDEO_THRESHOLD.toString()).toIntOrNull()
            ?: DEFAULT_VIDEO_THRESHOLD
    fun setVideoPlaybackThreshold(seconds: Int) =
        store.putString(KEY_VIDEO_THRESHOLD, seconds.toString())

    companion object {
        const val KEY_API_KEY = "api_key"          // retained for migration detection
        private const val KEY_SESSION_TOKEN = "session_token"
        private const val KEY_USERNAME = "username"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_AUTH_SALT = "auth_salt"
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_WELCOMED = "welcomed"
        private const val KEY_VIDEO_THRESHOLD = "video_playback_threshold"
        const val DEFAULT_VIDEO_THRESHOLD = 300  // 5 minutes

        /**
         * Convenience factory for use in Activities and Services.
         *
         * On first call after the SEC-007 upgrade the session token (and any other values)
         * present in the old plaintext [SharedPreferenceStore] are migrated into the new
         * [EncryptedSharedPreferenceStore] and then deleted from the plaintext file so that
         * no sensitive data remains accessible via root or forensic extraction.
         */
        fun create(context: Context): EndpointStore {
            val plainStore = SharedPreferenceStore(context)
            val encStore = EncryptedSharedPreferenceStore(context)

            // One-time migration: copy every key that still lives in the plaintext prefs
            // into the encrypted prefs, then remove it from plaintext storage.
            val migrationKeys = listOf(
                KEY_SESSION_TOKEN,
                KEY_API_KEY,
                KEY_USERNAME,
                KEY_DISPLAY_NAME,
                KEY_AUTH_SALT,
                KEY_WIFI_ONLY,
                KEY_WELCOMED,
                KEY_VIDEO_THRESHOLD,
            )
            for (key in migrationKeys) {
                if (plainStore.contains(key)) {
                    val value = plainStore.getString(key, "")
                    encStore.putString(key, value)
                    plainStore.remove(key)
                }
            }

            return EndpointStore(encStore)
        }
    }
}
