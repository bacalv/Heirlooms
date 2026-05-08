package digital.heirlooms.app

import android.content.Context

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
 */
class SharedPreferenceStore(context: Context) : PreferenceStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun getString(key: String, default: String): String =
        prefs.getString(key, default) ?: default

    override fun putString(key: String, value: String) =
        prefs.edit().putString(key, value).apply()

    companion object {
        private const val PREFS_NAME = "heirloom_prefs"
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

    fun getWifiOnly(): Boolean = store.getString(KEY_WIFI_ONLY, "false") == "true"
    fun setWifiOnly(enabled: Boolean) = store.putString(KEY_WIFI_ONLY, if (enabled) "true" else "false")

    // Tracks whether the first-launch welcome screen has been acknowledged (once per install).
    fun getWelcomed(): Boolean = store.getString(KEY_WELCOMED, "false") == "true"
    fun setWelcomed(value: Boolean) = store.putString(KEY_WELCOMED, if (value) "true" else "false")

    companion object {
        private const val KEY_API_KEY = "api_key"
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_WELCOMED = "welcomed"

        /** Convenience factory for use in Activities and Services. */
        fun create(context: Context): EndpointStore =
            EndpointStore(SharedPreferenceStore(context))
    }
}
