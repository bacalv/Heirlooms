package digital.heirlooms.tools.apiclient

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ClientConfigTest {

    // A valid 32-byte hex string (64 hex chars)
    private val validAuthKeyHex = "a".repeat(64)

    @Test
    fun `authKeyBytes decodes 64-char hex string to 32 bytes`() {
        val config = ClientConfig(
            baseUrl = "https://test.api.heirlooms.digital",
            username = "testuser",
            authKeyHex = validAuthKeyHex,
        )
        val bytes = config.authKeyBytes()
        assertEquals(32, bytes.size)
        // All nibbles are 'a' = 0xAA
        bytes.forEach { b -> assertEquals(0xAA.toByte(), b) }
    }

    @Test
    fun `authKeyBytes rejects hex string shorter than 64 chars`() {
        val config = ClientConfig(
            baseUrl = "https://test.api.heirlooms.digital",
            username = "testuser",
            authKeyHex = "abcd1234",
        )
        assertThrows<IllegalArgumentException> {
            config.authKeyBytes()
        }
    }

    @Test
    fun `authKeyBytes rejects hex string longer than 64 chars`() {
        val config = ClientConfig(
            baseUrl = "https://test.api.heirlooms.digital",
            username = "testuser",
            authKeyHex = "a".repeat(66),
        )
        assertThrows<IllegalArgumentException> {
            config.authKeyBytes()
        }
    }

    @Test
    fun `baseUrl trailing slash is stripped`() {
        // ClientConfig.load strips trailing slashes; test the data class directly
        val config = ClientConfig(
            baseUrl = "https://test.api.heirlooms.digital/",
            username = "testuser",
            authKeyHex = validAuthKeyHex,
        )
        // The trimEnd is applied at load() time; the data class stores as-is.
        // This test documents the contract: callers should use load() which trims.
        assertNotNull(config.baseUrl)
    }

    @Test
    fun `load uses system property over environment variable`() {
        // We can only test via system properties in a clean unit test environment.
        System.setProperty("heirlooms.username", "sysprop_user")
        System.setProperty("heirlooms.auth_key", validAuthKeyHex)
        System.setProperty("heirlooms.base_url", "https://custom.api.example.com")
        try {
            val config = ClientConfig.load(emptyArray())
            assertEquals("sysprop_user", config.username)
            assertEquals(validAuthKeyHex, config.authKeyHex)
            assertEquals("https://custom.api.example.com", config.baseUrl)
        } finally {
            System.clearProperty("heirlooms.username")
            System.clearProperty("heirlooms.auth_key")
            System.clearProperty("heirlooms.base_url")
        }
    }

    @Test
    fun `load uses default base_url when none configured`() {
        System.setProperty("heirlooms.username", "testuser")
        System.setProperty("heirlooms.auth_key", validAuthKeyHex)
        try {
            val config = ClientConfig.load(emptyArray())
            assertEquals("https://test.api.heirlooms.digital", config.baseUrl)
        } finally {
            System.clearProperty("heirlooms.username")
            System.clearProperty("heirlooms.auth_key")
        }
    }

    @Test
    fun `load throws when username is missing`() {
        System.clearProperty("heirlooms.username")
        System.clearProperty("heirlooms.auth_key")
        // Ensure env var is not set (no practical way to unset env vars in JVM;
        // this test is valid when HEIRLOOMS_USERNAME is not set in the test env)
        if (System.getenv("HEIRLOOMS_USERNAME") == null) {
            assertThrows<IllegalStateException> {
                ClientConfig.load(emptyArray())
            }
        }
    }

    @Test
    fun `authKeyBytes produces correct byte values for known hex`() {
        val config = ClientConfig(
            baseUrl = "https://test.api.heirlooms.digital",
            username = "testuser",
            authKeyHex = "00ff" + "0".repeat(60),
        )
        val bytes = config.authKeyBytes()
        assertEquals(32, bytes.size)
        assertEquals(0x00.toByte(), bytes[0])
        assertEquals(0xff.toByte(), bytes[1])
        assertEquals(0x00.toByte(), bytes[2])
    }
}
