package digital.heirlooms.api

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [HeirloomsApi.createFriendInvite].
 *
 * Uses [MockWebServer] so no real network connection is made.  The test verifies that:
 * - The client issues a GET to /api/auth/invites (server only has GET; POST was a bug fixed in TST-010)
 * - The response body is parsed correctly into [HeirloomsApi.InviteResponse]
 */
class CreateFriendInviteTest {

    private lateinit var server: MockWebServer
    private lateinit var api: HeirloomsApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = HeirloomsApi(baseUrl = server.url("/").toString().trimEnd('/'), apiKey = "test-key")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `createFriendInvite sends GET to correct path`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"token":"tok123","expires_at":"2026-05-18T14:30:00Z"}"""),
        )

        api.createFriendInvite()

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/auth/invites", request.path)
    }

    @Test
    fun `createFriendInvite parses token and expiresAt from response`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"token":"mytoken","expires_at":"2026-05-18T14:30:00Z"}"""),
        )

        val result = api.createFriendInvite()

        assertEquals("mytoken", result.token)
        assertEquals("2026-05-18T14:30:00Z", result.expiresAt)
    }

    @Test(expected = java.io.IOException::class)
    fun `createFriendInvite throws IOException on server error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        api.createFriendInvite()
    }

    @Test
    fun `createFriendInvite sends the api key in the X-Api-Key header`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"token":"tok","expires_at":"2026-05-18T14:30:00Z"}"""),
        )

        api.createFriendInvite()

        val request = server.takeRequest()
        assertEquals("test-key", request.getHeader("X-Api-Key"))
    }
}
