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
    fun contains(key: String): Boolean
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

    override fun contains(key: String): Boolean = prefs.contains(key)

    companion object {
        private const val PREFS_NAME = "heirloom_prefs"
    }
}

/**
 * Persists the upload endpoint URL so it survives app restarts.
 *
 * Accepts a [PreferenceStore] so the Android runtime is never required in tests.
 * Use the [EndpointStore.create] factory in production code.
 */
class EndpointStore(private val store: PreferenceStore) {

    /** Returns the stored endpoint, or [DEFAULT_ENDPOINT] if none has been saved yet. */
    fun get(): String = store.getString(KEY_ENDPOINT, DEFAULT_ENDPOINT)

    /** Persists [endpoint] so it survives app restarts. */
    fun set(endpoint: String) = store.putString(KEY_ENDPOINT, endpoint.trim())

    /** True if the user has explicitly saved an endpoint at least once. */
    fun isConfigured(): Boolean = store.contains(KEY_ENDPOINT)

    companion object {
        const val DEFAULT_ENDPOINT = "http://12.34.56.78:8080/api/content/upload"
        private const val KEY_ENDPOINT = "endpoint"

        /** Convenience factory for use in Activities and Services. */
        fun create(context: Context): EndpointStore =
            EndpointStore(SharedPreferenceStore(context))
    }
}
