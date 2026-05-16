package digital.heirlooms.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * SEC-015: Unit tests for EndpointStore.getRequireBiometric / setRequireBiometric.
 */
class BiometricStoreTest {

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
    fun `getRequireBiometric returns false when nothing has been saved`() {
        assertFalse(store.getRequireBiometric())
    }

    @Test
    fun `setRequireBiometric true is persisted and returned`() {
        store.setRequireBiometric(true)
        assertTrue(store.getRequireBiometric())
    }

    @Test
    fun `setRequireBiometric false is persisted and returned`() {
        store.setRequireBiometric(true)
        store.setRequireBiometric(false)
        assertFalse(store.getRequireBiometric())
    }

    @Test
    fun `two stores sharing the same PreferenceStore see the same biometric setting`() {
        val sharedPrefs = InMemoryPreferenceStore()
        val store1 = EndpointStore(sharedPrefs)
        val store2 = EndpointStore(sharedPrefs)

        store1.setRequireBiometric(true)
        assertTrue(store2.getRequireBiometric())
    }
}
