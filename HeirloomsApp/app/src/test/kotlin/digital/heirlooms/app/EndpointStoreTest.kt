package digital.heirlooms.app

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class EndpointStoreTest {

    private class InMemoryPreferenceStore : PreferenceStore {
        private val map = mutableMapOf<String, String>()
        override fun getString(key: String, default: String): String = map.getOrDefault(key, default)
        override fun putString(key: String, value: String) { map[key] = value }
    }

    private lateinit var store: EndpointStore

    @Before
    fun setUp() {
        store = EndpointStore(InMemoryPreferenceStore())
    }

    @Test
    fun `getApiKey returns empty string when nothing has been saved`() {
        assertEquals("", store.getApiKey())
    }

    @Test
    fun `getApiKey returns value that was previously set`() {
        store.setApiKey("secret-key-123")
        assertEquals("secret-key-123", store.getApiKey())
    }

    @Test
    fun `setApiKey trims whitespace from the stored value`() {
        store.setApiKey("  my-key  ")
        assertEquals("my-key", store.getApiKey())
    }

    @Test
    fun `setApiKey overwrites a previously saved value`() {
        store.setApiKey("old-key")
        store.setApiKey("new-key")
        assertEquals("new-key", store.getApiKey())
    }

    @Test
    fun `two stores sharing the same PreferenceStore see the same api key`() {
        val sharedPrefs = InMemoryPreferenceStore()
        val store1 = EndpointStore(sharedPrefs)
        val store2 = EndpointStore(sharedPrefs)

        store1.setApiKey("shared-key")

        assertEquals("shared-key", store2.getApiKey())
    }
}
