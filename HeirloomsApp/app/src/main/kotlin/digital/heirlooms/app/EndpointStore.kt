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

    /** Returns the stored API key, or an empty string if none has been saved. */
    fun getApiKey(): String = store.getString(KEY_API_KEY, "")

    /** Persists [apiKey] so it survives app restarts. */
    fun setApiKey(apiKey: String) = store.putString(KEY_API_KEY, apiKey.trim())

    companion object {
        private const val KEY_API_KEY = "api_key"

        /** Convenience factory for use in Activities and Services. */
        fun create(context: Context): EndpointStore =
            EndpointStore(SharedPreferenceStore(context))
    }
}
