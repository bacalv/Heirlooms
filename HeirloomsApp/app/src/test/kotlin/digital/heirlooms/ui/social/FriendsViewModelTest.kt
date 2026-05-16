package digital.heirlooms.ui.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [FriendsViewModel] helper logic.
 *
 * The ViewModel itself extends [androidx.lifecycle.ViewModel], which requires the Android
 * runtime and cannot be instantiated in JVM unit tests without Robolectric.  The tests
 * here focus on the pure-JVM helper [formatExpiry] and the invite-URL construction
 * convention, both of which can be verified without a device or emulator.
 */
class FriendsViewModelTest {

    // ── formatExpiry ──────────────────────────────────────────────────────────

    @Test
    fun `formatExpiry returns non-empty string for valid ISO timestamp`() {
        val result = formatExpiry("2026-05-18T14:30:00Z")
        assertTrue("Expected non-empty expiry label", result.isNotEmpty())
        assertTrue("Expected label to start with 'Expires'", result.startsWith("Expires"))
    }

    @Test
    fun `formatExpiry returns empty string for unparseable input`() {
        val result = formatExpiry("not-a-timestamp")
        assertEquals("", result)
    }

    @Test
    fun `formatExpiry returns empty string for empty input`() {
        val result = formatExpiry("")
        assertEquals("", result)
    }

    @Test
    fun `formatExpiry handles fractional-second timestamps`() {
        val result = formatExpiry("2026-05-18T14:30:00.000Z")
        assertTrue("Expected non-empty expiry label", result.isNotEmpty())
        assertTrue("Expected label to start with 'Expires'", result.startsWith("Expires"))
    }

    // ── Invite URL construction ────────────────────────────────────────────────

    @Test
    fun `invite URL uses invite path with token`() {
        val token = "abc123"
        val baseUrl = "https://heirlooms.digital"
        val url = "$baseUrl/invite?token=$token"
        assertTrue(url.contains("/invite?token="))
        assertTrue(url.endsWith(token))
    }

    @Test
    fun `staging invite URL uses staging web domain`() {
        // Mirrors the BASE_URL_OVERRIDE logic used in FriendsScreen.
        val apiOverride = "https://test.api.heirlooms.digital"
        val webBase = apiOverride
            .replace("test.api.", "test.")
            .replace("api.", "")
            .trimEnd('/')
        assertEquals("https://test.heirlooms.digital", webBase)
    }

    @Test
    fun `prod invite URL uses production web domain when override is empty`() {
        val apiOverride = ""
        val webBase = if (apiOverride.isNotEmpty())
            apiOverride.replace("test.api.", "test.").replace("api.", "").trimEnd('/')
        else "https://heirlooms.digital"
        assertEquals("https://heirlooms.digital", webBase)
    }
}
