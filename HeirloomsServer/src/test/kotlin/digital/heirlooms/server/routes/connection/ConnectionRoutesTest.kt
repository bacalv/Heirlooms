package digital.heirlooms.server.routes.connection

import digital.heirlooms.server.domain.connection.ConnectionRecord
import digital.heirlooms.server.routes.buildApp
import digital.heirlooms.server.repository.connection.ConnectionRepository
import digital.heirlooms.server.service.connection.ConnectionService
import digital.heirlooms.server.storage.LocalFileStore
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.http4k.core.Method.DELETE
import org.http4k.core.Method.GET
import org.http4k.core.Method.PATCH
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.CONFLICT
import org.http4k.core.Status.Companion.CREATED
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.NO_CONTENT
import org.http4k.core.Status.Companion.OK
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.time.Instant
import java.util.UUID

/**
 * Unit tests for ConnectionRoutes — all service calls are mocked.
 * Covers happy paths, 404, and 409 cases for each of the 5 endpoints.
 */
class ConnectionRoutesTest {

    private val mapper = ObjectMapper()

    private val connectionService = mockk<ConnectionService>()

    // We test the routes by building a minimal app using the internal buildApp.
    // The mock connectionRepo is wired in so we can control service behaviour.
    private val connectionRepo = mockk<ConnectionRepository>()
    private val storage = LocalFileStore(Files.createTempDirectory("conn-routes-test"))
    private val app = buildApp(
        storage = storage,
        uploadRepo = mockk(relaxed = true),
        authRepo = mockk(relaxed = true),
        capsuleRepo = mockk(relaxed = true),
        plotRepo = mockk(relaxed = true),
        flowRepo = mockk(relaxed = true),
        itemRepo = mockk(relaxed = true),
        memberRepo = mockk(relaxed = true),
        keyRepo = mockk(relaxed = true),
        socialRepo = mockk(relaxed = true),
        blobRepo = mockk(relaxed = true),
        diagRepo = mockk(relaxed = true),
        connectionRepo = connectionRepo,
    )

    private val ownerId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val connId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val contactId = UUID.fromString("00000000-0000-0000-0000-000000000003")

    private fun authedRequest(method: org.http4k.core.Method, path: String) =
        Request(method, path).header("X-Auth-User-Id", ownerId.toString())

    private fun makeRecord(
        id: UUID = connId,
        ownerUserId: UUID = ownerId,
        contactUserId: UUID? = contactId,
        displayName: String = "Alice",
        email: String? = null,
        sharingPubkey: String? = null,
        roles: List<String> = listOf("recipient"),
        createdAt: Instant = Instant.parse("2026-05-01T00:00:00Z"),
        updatedAt: Instant = Instant.parse("2026-05-01T00:00:00Z"),
    ) = ConnectionRecord(
        id = id,
        ownerUserId = ownerUserId,
        contactUserId = contactUserId,
        displayName = displayName,
        email = email,
        sharingPubkey = sharingPubkey,
        roles = roles,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    @BeforeEach
    fun resetMocks() {
        io.mockk.clearMocks(connectionRepo)
    }

    // ─── GET /api/connections ─────────────────────────────────────────────────

    @Test
    fun `GET connections returns 200 with list`() {
        every { connectionRepo.listConnections(ownerId) } returns listOf(makeRecord())

        val resp = app(authedRequest(GET, "/api/connections"))
        assertEquals(OK, resp.status)
        val node = mapper.readTree(resp.bodyString())
        assertTrue(node.has("connections"))
        assertEquals(1, node.get("connections").size())
        assertEquals("Alice", node.get("connections")[0].get("display_name").asText())
    }

    @Test
    fun `GET connections returns empty list when none exist`() {
        every { connectionRepo.listConnections(ownerId) } returns emptyList()

        val resp = app(authedRequest(GET, "/api/connections"))
        assertEquals(OK, resp.status)
        val node = mapper.readTree(resp.bodyString())
        assertEquals(0, node.get("connections").size())
    }

    // ─── POST /api/connections ────────────────────────────────────────────────

    @Test
    fun `POST connections creates bound connection and returns 201`() {
        val record = makeRecord(sharingPubkey = "pubkeyABC")
        every { connectionRepo.lookupSharingPubkey(contactId) } returns "pubkeyABC"
        every { connectionRepo.createConnection(
            ownerUserId = ownerId,
            contactUserId = contactId,
            displayName = "Alice",
            email = null,
            sharingPubkey = "pubkeyABC",
            roles = listOf("recipient"),
        ) } returns record

        val body = """{"display_name":"Alice","contact_user_id":"$contactId","roles":["recipient"]}"""
        val resp = app(authedRequest(POST, "/api/connections").body(body))
        assertEquals(CREATED, resp.status)
        val node = mapper.readTree(resp.bodyString())
        assertTrue(node.has("connection"))
        assertEquals("Alice", node.get("connection").get("display_name").asText())
        assertEquals("pubkeyABC", node.get("connection").get("sharing_pubkey").asText())
    }

    @Test
    fun `POST connections creates placeholder connection with email`() {
        val record = makeRecord(contactUserId = null, email = "alice@example.com", sharingPubkey = null)
        every { connectionRepo.createConnection(
            ownerUserId = ownerId,
            contactUserId = null,
            displayName = "Alice",
            email = "alice@example.com",
            sharingPubkey = null,
            roles = emptyList(),
        ) } returns record

        val body = """{"display_name":"Alice","email":"alice@example.com"}"""
        val resp = app(authedRequest(POST, "/api/connections").body(body))
        assertEquals(CREATED, resp.status)
        val node = mapper.readTree(resp.bodyString())
        assertEquals("alice@example.com", node.get("connection").get("email").asText())
    }

    @Test
    fun `POST connections returns 400 when display_name missing`() {
        val body = """{"contact_user_id":"$contactId"}"""
        val resp = app(authedRequest(POST, "/api/connections").body(body))
        assertEquals(BAD_REQUEST, resp.status)
    }

    @Test
    fun `POST connections returns 400 when neither contact_user_id nor email provided`() {
        every { connectionRepo.createConnection(any(), null, any(), null, any(), any()) } throws
            IllegalArgumentException("should not reach here")

        val body = """{"display_name":"Alice"}"""
        val resp = app(authedRequest(POST, "/api/connections").body(body))
        // Service validates this — 400 returned
        assertEquals(BAD_REQUEST, resp.status)
    }

    @Test
    fun `POST connections returns 409 on duplicate`() {
        every { connectionRepo.lookupSharingPubkey(contactId) } returns null
        every { connectionRepo.createConnection(any(), any(), any(), any(), any(), any()) } throws
            RuntimeException("ERROR: duplicate key value violates unique constraint \"connections_owner_contact_unique\"")

        val body = """{"display_name":"Alice","contact_user_id":"$contactId","roles":[]}"""
        val resp = app(authedRequest(POST, "/api/connections").body(body))
        assertEquals(CONFLICT, resp.status)
    }

    // ─── GET /api/connections/:id ─────────────────────────────────────────────

    @Test
    fun `GET connection by id returns 200 with connection`() {
        val record = makeRecord()
        every { connectionRepo.getConnection(connId, ownerId) } returns record

        val resp = app(authedRequest(GET, "/api/connections/$connId"))
        assertEquals(OK, resp.status)
        val node = mapper.readTree(resp.bodyString())
        assertTrue(node.has("connection"))
        assertEquals(connId.toString(), node.get("connection").get("id").asText())
    }

    @Test
    fun `GET connection by id returns 404 when not found`() {
        every { connectionRepo.getConnection(connId, ownerId) } returns null

        val resp = app(authedRequest(GET, "/api/connections/$connId"))
        assertEquals(NOT_FOUND, resp.status)
    }

    // ─── PATCH /api/connections/:id ───────────────────────────────────────────

    @Test
    fun `PATCH connection updates display_name and returns 200`() {
        val updated = makeRecord(displayName = "Alice Smith")
        every { connectionRepo.getConnection(connId, ownerId) } returns makeRecord()
        every { connectionRepo.updateConnection(connId, ownerId, "Alice Smith", null, null, false) } returns updated

        val body = """{"display_name":"Alice Smith"}"""
        val resp = app(authedRequest(PATCH, "/api/connections/$connId").body(body))
        assertEquals(OK, resp.status)
        val node = mapper.readTree(resp.bodyString())
        assertEquals("Alice Smith", node.get("connection").get("display_name").asText())
    }

    @Test
    fun `PATCH connection returns 404 when not found`() {
        every { connectionRepo.getConnection(connId, ownerId) } returns null
        every { connectionRepo.updateConnection(connId, ownerId, any(), any(), any(), any()) } returns null

        val body = """{"display_name":"Nobody"}"""
        val resp = app(authedRequest(PATCH, "/api/connections/$connId").body(body))
        assertEquals(NOT_FOUND, resp.status)
    }

    // ─── DELETE /api/connections/:id ──────────────────────────────────────────

    @Test
    fun `DELETE connection returns 204 on success`() {
        every { connectionRepo.getConnection(connId, ownerId) } returns makeRecord()
        every { connectionRepo.deleteConnection(connId, ownerId) } returns ConnectionRepository.DeleteResult.Deleted

        val resp = app(authedRequest(DELETE, "/api/connections/$connId"))
        assertEquals(NO_CONTENT, resp.status)
    }

    @Test
    fun `DELETE connection returns 404 when not found`() {
        every { connectionRepo.deleteConnection(connId, ownerId) } returns ConnectionRepository.DeleteResult.NotFound

        val resp = app(authedRequest(DELETE, "/api/connections/$connId"))
        assertEquals(NOT_FOUND, resp.status)
    }

    @Test
    fun `DELETE connection returns 409 when active nominations exist`() {
        every { connectionRepo.deleteConnection(connId, ownerId) } returns
            ConnectionRepository.DeleteResult.ActiveNominationsExist

        val resp = app(authedRequest(DELETE, "/api/connections/$connId"))
        assertEquals(CONFLICT, resp.status)
        val node = mapper.readTree(resp.bodyString())
        assertEquals("active_nominations_exist", node.get("error").asText())
    }
}
