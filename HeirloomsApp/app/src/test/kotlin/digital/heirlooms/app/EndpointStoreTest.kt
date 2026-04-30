package digital.heirlooms.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pure JVM tests for EndpointStore — no Android runtime, no Robolectric.
 *
 * InMemoryPreferenceStore stands in for SharedPreferences so we can test
 * all the EndpointStore logic without any Android dependencies.
 */
class EndpointStoreTest {

    /**
     * Test double: a plain map that implements PreferenceStore.
     * No mocking framework needed — simple enough to write inline.
     */
    private class InMemoryPreferenceStore : PreferenceStore {
        private val map = mutableMapOf<String, String>()
        override fun getString(key: String, default: String): String = map.getOrDefault(key, default)
        override fun putString(key: String, value: String) { map[key] = value }
        override fun contains(key: String): Boolean = map.containsKey(key)
    }

    private lateinit var store: EndpointStore

    @Before
    fun setUp() {
        store = EndpointStore(InMemoryPreferenceStore())
    }

    @Test
    fun `get returns default endpoint when nothing has been saved`() {
        assertEquals(EndpointStore.DEFAULT_ENDPOINT, store.get())
    }

    @Test
    fun `get returns value that was previously set`() {
        store.set("http://192.168.1.100:8080/api/content/upload")
        assertEquals("http://192.168.1.100:8080/api/content/upload", store.get())
    }

    @Test
    fun `set trims whitespace from the stored value`() {
        store.set("  http://myserver.com/upload  ")
        assertEquals("http://myserver.com/upload", store.get())
    }

    @Test
    fun `set overwrites a previously saved value`() {
        store.set("http://first.example.com/upload")
        store.set("http://second.example.com/upload")
        assertEquals("http://second.example.com/upload", store.get())
    }

    @Test
    fun `isConfigured returns false when nothing has been saved`() {
        assertFalse(store.isConfigured())
    }

    @Test
    fun `isConfigured returns true after a value has been saved`() {
        store.set("http://example.com/upload")
        assertTrue(store.isConfigured())
    }

    @Test
    fun `two stores sharing the same PreferenceStore see the same value`() {
        val sharedPrefs = InMemoryPreferenceStore()
        val store1 = EndpointStore(sharedPrefs)
        val store2 = EndpointStore(sharedPrefs)

        store1.set("http://shared.example.com/upload")

        assertEquals("http://shared.example.com/upload", store2.get())
    }

    @Test
    fun `default endpoint is a valid URL`() {
        assertTrue(Uploader.isValidEndpoint(EndpointStore.DEFAULT_ENDPOINT))
    }
}
