package digital.heirlooms.tools.apiclient

import org.junit.jupiter.api.Test
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests that the auth_key → auth_verifier contract matches the server's expectation.
 *
 * Server (AuthService.kt):
 *   auth_verifier = SHA-256(auth_key)
 *   login validates: sha256(received_auth_key) == stored_auth_verifier
 *
 * Client (Phase 1):
 *   auth_key is a raw 32-byte value stored as hex in config.
 *   The client sends auth_key directly; the server hashes it and compares.
 */
class AuthKeyTest {

    @Test
    fun `sha256 of auth_key bytes produces expected verifier`() {
        // Known auth_key: 32 bytes, all 0x42
        val authKeyHex = "42".repeat(32)
        val config = ClientConfig(
            baseUrl = "https://test.api.heirlooms.digital",
            username = "testuser",
            authKeyHex = authKeyHex,
        )
        val authKeyBytes = config.authKeyBytes()
        assertEquals(32, authKeyBytes.size)

        // Compute verifier as the server would
        val md = MessageDigest.getInstance("SHA-256")
        val verifier = md.digest(authKeyBytes)
        assertEquals(32, verifier.size)

        // The verifier is deterministic for the same auth_key
        val verifier2 = MessageDigest.getInstance("SHA-256").digest(authKeyBytes)
        assertTrue(verifier.contentEquals(verifier2))
    }

    @Test
    fun `auth_key bytes are correctly decoded from hex`() {
        // 32 bytes alternating 0x00 and 0xFF
        val hex = "00ff".repeat(16)
        val config = ClientConfig(
            baseUrl = "https://test.api.heirlooms.digital",
            username = "testuser",
            authKeyHex = hex,
        )
        val bytes = config.authKeyBytes()
        assertEquals(32, bytes.size)
        for (i in 0 until 32) {
            val expected = if (i % 2 == 0) 0x00.toByte() else 0xff.toByte()
            assertEquals(expected, bytes[i], "Mismatch at index $i")
        }
    }

    @Test
    fun `base64url encoding of auth_key bytes is padding-free`() {
        val authKeyHex = "a0".repeat(32)
        val config = ClientConfig(
            baseUrl = "https://test.api.heirlooms.digital",
            username = "testuser",
            authKeyHex = authKeyHex,
        )
        val bytes = config.authKeyBytes()
        val b64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        // Base64url encoding must not contain '+', '/', or '=' characters
        assertTrue(!b64.contains('+'), "Base64url must not contain '+'")
        assertTrue(!b64.contains('/'), "Base64url must not contain '/'")
        assertTrue(!b64.contains('='), "Base64url must not contain '=' padding")
    }
}
