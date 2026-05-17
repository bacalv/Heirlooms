package digital.heirlooms.server.routes.connection

import com.fasterxml.jackson.databind.ObjectMapper
import digital.heirlooms.server.domain.connection.NominationRecord
import digital.heirlooms.server.repository.capsule.RecipientLinkRepository
import digital.heirlooms.server.repository.connection.ConnectionRepository
import digital.heirlooms.server.repository.connection.NominationRepository
import digital.heirlooms.server.routes.buildApp
import digital.heirlooms.server.service.connection.NominationService
import digital.heirlooms.server.storage.LocalFileStore
import io.mockk.every
import io.mockk.mockk
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status.Companion.CONFLICT
import org.http4k.core.Status.Companion.CREATED
import org.http4k.core.Status.Companion.FORBIDDEN
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.OK
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.time.Instant
import java.util.UUID

/**
 * Unit tests for NominationRoutes — all service calls are mocked.
 *
 * Covers happy path, 403, 404, and 409 for each of the 6 nomination endpoints.
 */
class NominationRoutesTest {

    private val mapper = ObjectMapper()

    private val nominationRepo = mockk<NominationRepository>()
    private val connectionRepo = mockk<ConnectionRepository>(relaxed = true)
    private val recipientLinkRepo = mockk<RecipientLinkRepository>(relaxed = true)
    private val storage = LocalFileStore(Files.createTempDirectory("nom-routes-test"))

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
        nominationRepo = nominationRepo,
        recipientLinkRepo = recipientLinkRepo,
    )

    private val ownerId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val nomineeId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val connId = UUID.fromString("00000000-0000-0000-0000-000000000003")
    private val nomId = UUID.fromString("00000000-0000-0000-0000-000000000004")

    private fun authedRequest(method: org.http4k.core.Method, path: String, userId: UUID = ownerId) =
        Request(method, path).header("X-Auth-User-Id", userId.toString())

    private fun makeNomination(
        id: UUID = nomId,
        connectionId: UUID = connId,
        ownerUserId: UUID = ownerId,
        status: String = "pending",
        message: String? = "Please be my executor",
    ) = NominationRecord(
        id = id,
        connectionId = connectionId,
        ownerUserId = ownerUserId,
        status = status,
        offeredAt = Instant.parse("2026-05-01T00:00:00Z"),
        respondedAt = null,
        revokedAt = null,
        message = message,
    )

    @BeforeEach
    fun resetMocks() {
        io.mockk.clearMocks(nominationRepo, connectionRepo, recipientLinkRepo)
        // Default stubs needed to avoid NPE in ConnectionService
        every { connectionRepo.listConnections(any()) } returns emptyList()
    }

    // ─── POST /api/executor-nominations (Endpoint 6) ──────────────────────────

    @Test
    fun `POST executor-nominations creates pending nomination and returns 201`() {
        val nom = makeNomination()
        every { nominationRepo.getConnectionOwnerUserId(connId) } returns ownerId
        every { nominationRepo.hasActiveNomination(connId) } returns false
        every { nominationRepo.createNomination(ownerId, connId, "Please be my executor") } returns nom
        every { nominationRepo.getById(nomId) } returns nom

        val body = """{"connection_id":"$connId","message":"Please be my executor"}"""
        val resp = app(authedRequest(POST, "/api/executor-nominations").body(body))
        assertEquals(CREATED, resp.status, "Expected 201: ${resp.bodyString()}")

        val node = mapper.readTree(resp.bodyString())
        assertTrue(node.has("nomination"))
        assertEquals("pending", node.get("nomination").get("status").asText())
        assertEquals(nomId.toString(), node.get("nomination").get("id").asText())
    }

    @Test
    fun `POST executor-nominations returns 404 when connection does not belong to caller`() {
        every { nominationRepo.getConnectionOwnerUserId(connId) } returns null

        val body = """{"connection_id":"$connId"}"""
        val resp = app(authedRequest(POST, "/api/executor-nominations").body(body))
        assertEquals(NOT_FOUND, resp.status)
        val node = mapper.readTree(resp.bodyString())
        assertEquals("connection_not_found", node.get("error").asText())
    }

    @Test
    fun `POST executor-nominations returns 409 when active nomination already exists`() {
        every { nominationRepo.getConnectionOwnerUserId(connId) } returns ownerId
        every { nominationRepo.hasActiveNomination(connId) } returns true

        val body = """{"connection_id":"$connId"}"""
        val resp = app(authedRequest(POST, "/api/executor-nominations").body(body))
        assertEquals(CONFLICT, resp.status)
        val node = mapper.readTree(resp.bodyString())
        assertEquals("active_nomination_exists", node.get("error").asText())
    }

    // ─── GET /api/executor-nominations (Endpoint 7) ───────────────────────────

    @Test
    fun `GET executor-nominations returns list of issued nominations`() {
        val nom = makeNomination()
        every { nominationRepo.listByOwner(ownerId) } returns listOf(nom)

        val resp = app(authedRequest(GET, "/api/executor-nominations"))
        assertEquals(OK, resp.status)
        val node = mapper.readTree(resp.bodyString())
        assertTrue(node.has("nominations"))
        assertEquals(1, node.get("nominations").size())
        assertEquals(nomId.toString(), node.get("nominations")[0].get("id").asText())
    }

    @Test
    fun `GET executor-nominations returns empty list when none issued`() {
        every { nominationRepo.listByOwner(ownerId) } returns emptyList()

        val resp = app(authedRequest(GET, "/api/executor-nominations"))
        assertEquals(OK, resp.status)
        val node = mapper.readTree(resp.bodyString())
        assertEquals(0, node.get("nominations").size())
    }

    // ─── GET /api/executor-nominations/received (Endpoint 8) ─────────────────

    @Test
    fun `GET executor-nominations-received returns nominations for nominee`() {
        val nom = makeNomination(ownerUserId = ownerId)
        every { nominationRepo.listReceived(nomineeId) } returns listOf(nom)

        val resp = app(authedRequest(GET, "/api/executor-nominations/received", nomineeId))
        assertEquals(OK, resp.status)
        val node = mapper.readTree(resp.bodyString())
        assertTrue(node.has("nominations"))
        assertEquals(1, node.get("nominations").size())
    }

    @Test
    fun `GET executor-nominations-received returns empty list when no nominations received`() {
        every { nominationRepo.listReceived(nomineeId) } returns emptyList()

        val resp = app(authedRequest(GET, "/api/executor-nominations/received", nomineeId))
        assertEquals(OK, resp.status)
        assertEquals(0, mapper.readTree(resp.bodyString()).get("nominations").size())
    }

    // ─── POST /api/executor-nominations/:id/accept (Endpoint 9) ──────────────

    @Test
    fun `POST accept returns 200 with accepted nomination`() {
        val nom = makeNomination(status = "pending")
        val accepted = nom.copy(status = "accepted", respondedAt = Instant.now())
        every { nominationRepo.getById(nomId) } returns nom
        every { nominationRepo.getContactUserId(connId) } returns nomineeId
        every { nominationRepo.setRespondedStatus(nomId, "accepted") } returns accepted

        val resp = app(authedRequest(POST, "/api/executor-nominations/$nomId/accept", nomineeId))
        assertEquals(OK, resp.status)
        val node = mapper.readTree(resp.bodyString())
        assertEquals("accepted", node.get("nomination").get("status").asText())
    }

    @Test
    fun `POST accept returns 403 when caller is not the nominee`() {
        val nom = makeNomination(status = "pending")
        every { nominationRepo.getById(nomId) } returns nom
        every { nominationRepo.getContactUserId(connId) } returns UUID.randomUUID() // different user

        val resp = app(authedRequest(POST, "/api/executor-nominations/$nomId/accept", nomineeId))
        assertEquals(FORBIDDEN, resp.status)
        assertEquals("forbidden", mapper.readTree(resp.bodyString()).get("error").asText())
    }

    @Test
    fun `POST accept returns 404 when nomination not found`() {
        every { nominationRepo.getById(nomId) } returns null

        val resp = app(authedRequest(POST, "/api/executor-nominations/$nomId/accept", nomineeId))
        assertEquals(NOT_FOUND, resp.status)
    }

    @Test
    fun `POST accept returns 409 when nomination is not pending`() {
        val nom = makeNomination(status = "accepted") // already accepted
        every { nominationRepo.getById(nomId) } returns nom
        every { nominationRepo.getContactUserId(connId) } returns nomineeId

        val resp = app(authedRequest(POST, "/api/executor-nominations/$nomId/accept", nomineeId))
        assertEquals(CONFLICT, resp.status)
        assertEquals("invalid_state_transition", mapper.readTree(resp.bodyString()).get("error").asText())
    }

    // ─── POST /api/executor-nominations/:id/decline (Endpoint 10) ────────────

    @Test
    fun `POST decline returns 200 with declined nomination`() {
        val nom = makeNomination(status = "pending")
        val declined = nom.copy(status = "declined", respondedAt = Instant.now())
        every { nominationRepo.getById(nomId) } returns nom
        every { nominationRepo.getContactUserId(connId) } returns nomineeId
        every { nominationRepo.setRespondedStatus(nomId, "declined") } returns declined

        val resp = app(authedRequest(POST, "/api/executor-nominations/$nomId/decline", nomineeId))
        assertEquals(OK, resp.status)
        assertEquals("declined", mapper.readTree(resp.bodyString()).get("nomination").get("status").asText())
    }

    @Test
    fun `POST decline returns 403 when caller is not the nominee`() {
        val nom = makeNomination(status = "pending")
        every { nominationRepo.getById(nomId) } returns nom
        every { nominationRepo.getContactUserId(connId) } returns UUID.randomUUID()

        val resp = app(authedRequest(POST, "/api/executor-nominations/$nomId/decline", nomineeId))
        assertEquals(FORBIDDEN, resp.status)
    }

    @Test
    fun `POST decline returns 409 when nomination is not pending`() {
        val nom = makeNomination(status = "declined")
        every { nominationRepo.getById(nomId) } returns nom
        every { nominationRepo.getContactUserId(connId) } returns nomineeId

        val resp = app(authedRequest(POST, "/api/executor-nominations/$nomId/decline", nomineeId))
        assertEquals(CONFLICT, resp.status)
    }

    // ─── POST /api/executor-nominations/:id/revoke (Endpoint 11) ─────────────

    @Test
    fun `POST revoke returns 200 with revoked nomination`() {
        val nom = makeNomination(status = "accepted")
        val revoked = nom.copy(status = "revoked", revokedAt = Instant.now())
        every { nominationRepo.getById(nomId) } returns nom
        every { nominationRepo.setRevoked(nomId) } returns revoked

        val resp = app(authedRequest(POST, "/api/executor-nominations/$nomId/revoke"))
        assertEquals(OK, resp.status)
        assertEquals("revoked", mapper.readTree(resp.bodyString()).get("nomination").get("status").asText())
    }

    @Test
    fun `POST revoke returns 200 when revoking a pending nomination`() {
        val nom = makeNomination(status = "pending")
        val revoked = nom.copy(status = "revoked", revokedAt = Instant.now())
        every { nominationRepo.getById(nomId) } returns nom
        every { nominationRepo.setRevoked(nomId) } returns revoked

        val resp = app(authedRequest(POST, "/api/executor-nominations/$nomId/revoke"))
        assertEquals(OK, resp.status)
        assertEquals("revoked", mapper.readTree(resp.bodyString()).get("nomination").get("status").asText())
    }

    @Test
    fun `POST revoke returns 403 when caller is not the owner`() {
        val nom = makeNomination(status = "pending", ownerUserId = UUID.randomUUID())
        every { nominationRepo.getById(nomId) } returns nom

        val resp = app(authedRequest(POST, "/api/executor-nominations/$nomId/revoke", ownerId))
        assertEquals(FORBIDDEN, resp.status)
    }

    @Test
    fun `POST revoke returns 404 when nomination not found`() {
        every { nominationRepo.getById(nomId) } returns null

        val resp = app(authedRequest(POST, "/api/executor-nominations/$nomId/revoke"))
        assertEquals(NOT_FOUND, resp.status)
    }

    @Test
    fun `POST revoke returns 409 when nomination is already declined`() {
        val nom = makeNomination(status = "declined")
        every { nominationRepo.getById(nomId) } returns nom

        val resp = app(authedRequest(POST, "/api/executor-nominations/$nomId/revoke"))
        assertEquals(CONFLICT, resp.status)
        assertEquals("invalid_state_transition", mapper.readTree(resp.bodyString()).get("error").asText())
    }

    @Test
    fun `POST revoke returns 409 when nomination is already revoked`() {
        val nom = makeNomination(status = "revoked")
        every { nominationRepo.getById(nomId) } returns nom

        val resp = app(authedRequest(POST, "/api/executor-nominations/$nomId/revoke"))
        assertEquals(CONFLICT, resp.status)
    }
}
