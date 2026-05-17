package digital.heirlooms.server.routes.capsule

import com.fasterxml.jackson.databind.ObjectMapper
import digital.heirlooms.server.repository.capsule.RecipientLinkRepository
import digital.heirlooms.server.repository.connection.ConnectionRepository
import digital.heirlooms.server.repository.connection.NominationRepository
import digital.heirlooms.server.routes.buildApp
import digital.heirlooms.server.storage.LocalFileStore
import io.mockk.every
import io.mockk.mockk
import org.http4k.core.Method.PATCH
import org.http4k.core.Request
import org.http4k.core.Status.Companion.CONFLICT
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.OK
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.util.UUID

/**
 * Unit tests for the PATCH /capsules/:id/recipients/:recipientId/link endpoint.
 * Covers happy path, 404 variants, and 409 duplicate connection.
 */
class RecipientLinkRouteTest {

    private val mapper = ObjectMapper()

    private val recipientLinkRepo = mockk<RecipientLinkRepository>()
    private val connectionRepo = mockk<ConnectionRepository>(relaxed = true)
    private val nominationRepo = mockk<NominationRepository>(relaxed = true)
    private val storage = LocalFileStore(Files.createTempDirectory("recip-link-test"))

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

    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val capsuleId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val recipientId = UUID.fromString("00000000-0000-0000-0000-000000000003")
    private val connId = UUID.fromString("00000000-0000-0000-0000-000000000004")

    private fun authedPatch(path: String, body: String) =
        Request(PATCH, path)
            .header("X-Auth-User-Id", userId.toString())
            .header("Content-Type", "application/json")
            .body(body)

    @BeforeEach
    fun resetMocks() {
        io.mockk.clearMocks(recipientLinkRepo, connectionRepo, nominationRepo)
        every { connectionRepo.listConnections(any()) } returns emptyList()
        every { nominationRepo.listByOwner(any()) } returns emptyList()
    }

    // ─── Happy path ────────────────────────────────────────────────────────────

    @Test
    fun `PATCH link returns 200 with linked recipient`() {
        every {
            recipientLinkRepo.linkRecipient(capsuleId, recipientId, connId, userId)
        } returns RecipientLinkRepository.LinkResult.Linked(
            recipientId = recipientId,
            capsuleId = capsuleId,
            recipient = "Alice",
            connectionId = connId,
        )

        val resp = app(authedPatch(
            "/api/capsules/$capsuleId/recipients/$recipientId/link",
            """{"connection_id":"$connId"}"""
        ))
        assertEquals(OK, resp.status, "Expected 200: ${resp.bodyString()}")
        val node = mapper.readTree(resp.bodyString())
        assertEquals(connId.toString(), node.get("recipient").get("connection_id").asText())
        assertEquals(recipientId.toString(), node.get("recipient").get("id").asText())
    }

    // ─── 404 variants ─────────────────────────────────────────────────────────

    @Test
    fun `PATCH link returns 404 when capsule not found`() {
        every {
            recipientLinkRepo.linkRecipient(capsuleId, recipientId, connId, userId)
        } returns RecipientLinkRepository.LinkResult.CapsuleNotFound

        val resp = app(authedPatch(
            "/api/capsules/$capsuleId/recipients/$recipientId/link",
            """{"connection_id":"$connId"}"""
        ))
        assertEquals(NOT_FOUND, resp.status)
        assertEquals("capsule_not_found", mapper.readTree(resp.bodyString()).get("error").asText())
    }

    @Test
    fun `PATCH link returns 404 when recipient not found`() {
        every {
            recipientLinkRepo.linkRecipient(capsuleId, recipientId, connId, userId)
        } returns RecipientLinkRepository.LinkResult.RecipientNotFound

        val resp = app(authedPatch(
            "/api/capsules/$capsuleId/recipients/$recipientId/link",
            """{"connection_id":"$connId"}"""
        ))
        assertEquals(NOT_FOUND, resp.status)
        assertEquals("recipient_not_found", mapper.readTree(resp.bodyString()).get("error").asText())
    }

    @Test
    fun `PATCH link returns 404 when connection not found or not owned by caller`() {
        every {
            recipientLinkRepo.linkRecipient(capsuleId, recipientId, connId, userId)
        } returns RecipientLinkRepository.LinkResult.ConnectionNotFound

        val resp = app(authedPatch(
            "/api/capsules/$capsuleId/recipients/$recipientId/link",
            """{"connection_id":"$connId"}"""
        ))
        assertEquals(NOT_FOUND, resp.status)
        assertEquals("connection_not_found", mapper.readTree(resp.bodyString()).get("error").asText())
    }

    // ─── 409 duplicate connection ─────────────────────────────────────────────

    @Test
    fun `PATCH link returns 409 when connection already linked to another recipient on same capsule`() {
        every {
            recipientLinkRepo.linkRecipient(capsuleId, recipientId, connId, userId)
        } returns RecipientLinkRepository.LinkResult.DuplicateConnection

        val resp = app(authedPatch(
            "/api/capsules/$capsuleId/recipients/$recipientId/link",
            """{"connection_id":"$connId"}"""
        ))
        assertEquals(CONFLICT, resp.status)
        assertEquals("connection_already_linked", mapper.readTree(resp.bodyString()).get("error").asText())
    }

    // ─── Bad request ──────────────────────────────────────────────────────────

    @Test
    fun `PATCH link returns 400 when connection_id missing from body`() {
        val resp = app(authedPatch(
            "/api/capsules/$capsuleId/recipients/$recipientId/link",
            """{}"""
        ))
        // The route handler returns 400 for missing connection_id
        assertEquals(400, resp.status.code)
    }
}
