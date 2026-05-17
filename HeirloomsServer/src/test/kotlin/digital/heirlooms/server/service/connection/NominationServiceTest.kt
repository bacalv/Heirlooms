package digital.heirlooms.server.service.connection

import digital.heirlooms.server.domain.connection.NominationRecord
import digital.heirlooms.server.repository.connection.NominationRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * Unit tests for [NominationService].
 *
 * Tests the nomination state machine (ARCH-004 §4):
 *   - pending → accepted   (nominee)
 *   - pending → declined   (nominee)
 *   - pending → revoked    (owner)
 *   - accepted → revoked   (owner)
 *   - Invalid transitions → StateConflict (HTTP 409)
 *   - Wrong caller role → Forbidden (HTTP 403)
 */
class NominationServiceTest {

    private val repo = mockk<NominationRepository>()
    private lateinit var svc: NominationService

    private val ownerId = UUID.fromString("10000000-0000-0000-0000-000000000001")
    private val nomineeId = UUID.fromString("10000000-0000-0000-0000-000000000002")
    private val connId = UUID.fromString("10000000-0000-0000-0000-000000000003")
    private val nomId = UUID.fromString("10000000-0000-0000-0000-000000000004")

    @BeforeEach
    fun setUp() {
        svc = NominationService(repo)
    }

    private fun makeNomination(
        id: UUID = nomId,
        connectionId: UUID = connId,
        ownerUserId: UUID = ownerId,
        status: String = "pending",
        message: String? = null,
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

    // ─── createNomination ─────────────────────────────────────────────────────

    @Test
    fun `createNomination returns Created when connection is owned and no active nomination`() {
        val nom = makeNomination()
        every { repo.getConnectionOwnerUserId(connId) } returns ownerId
        every { repo.hasActiveNomination(connId) } returns false
        every { repo.createNomination(ownerId, connId, null) } returns nom

        val result = svc.createNomination(ownerId, connId, null)
        assertTrue(result is NominationService.CreateResult.Created)
        assertEquals(nom, (result as NominationService.CreateResult.Created).nomination)
    }

    @Test
    fun `createNomination returns ConnectionNotFound when connection does not exist`() {
        every { repo.getConnectionOwnerUserId(connId) } returns null

        val result = svc.createNomination(ownerId, connId, null)
        assertEquals(NominationService.CreateResult.ConnectionNotFound, result)
        verify(exactly = 0) { repo.createNomination(any(), any(), any()) }
    }

    @Test
    fun `createNomination returns ConnectionNotFound when connection belongs to different user`() {
        every { repo.getConnectionOwnerUserId(connId) } returns UUID.randomUUID()

        val result = svc.createNomination(ownerId, connId, null)
        assertEquals(NominationService.CreateResult.ConnectionNotFound, result)
    }

    @Test
    fun `createNomination returns Conflict when active nomination already exists`() {
        every { repo.getConnectionOwnerUserId(connId) } returns ownerId
        every { repo.hasActiveNomination(connId) } returns true

        val result = svc.createNomination(ownerId, connId, null)
        assertEquals(NominationService.CreateResult.Conflict, result)
        verify(exactly = 0) { repo.createNomination(any(), any(), any()) }
    }

    @Test
    fun `createNomination passes message through to repository`() {
        val nom = makeNomination(message = "Please help")
        every { repo.getConnectionOwnerUserId(connId) } returns ownerId
        every { repo.hasActiveNomination(connId) } returns false
        every { repo.createNomination(ownerId, connId, "Please help") } returns nom

        val result = svc.createNomination(ownerId, connId, "Please help")
        assertTrue(result is NominationService.CreateResult.Created)
        assertEquals("Please help", (result as NominationService.CreateResult.Created).nomination.message)
    }

    // ─── accept state machine ─────────────────────────────────────────────────

    @Test
    fun `accept transitions pending to accepted`() {
        val pending = makeNomination(status = "pending")
        val accepted = pending.copy(status = "accepted", respondedAt = Instant.now())
        every { repo.getById(nomId) } returns pending
        every { repo.getContactUserId(connId) } returns nomineeId
        every { repo.setRespondedStatus(nomId, "accepted") } returns accepted

        val result = svc.accept(nomId, nomineeId)
        assertTrue(result is NominationService.TransitionResult.Updated)
        assertEquals("accepted", (result as NominationService.TransitionResult.Updated).nomination.status)
    }

    @Test
    fun `accept returns NotFound when nomination does not exist`() {
        every { repo.getById(nomId) } returns null
        assertEquals(NominationService.TransitionResult.NotFound, svc.accept(nomId, nomineeId))
    }

    @Test
    fun `accept returns Forbidden when caller is not the nominee`() {
        val pending = makeNomination(status = "pending")
        every { repo.getById(nomId) } returns pending
        every { repo.getContactUserId(connId) } returns UUID.randomUUID() // different user

        assertEquals(NominationService.TransitionResult.Forbidden, svc.accept(nomId, nomineeId))
    }

    @Test
    fun `accept returns Forbidden when connection has no contact_user_id`() {
        val pending = makeNomination(status = "pending")
        every { repo.getById(nomId) } returns pending
        every { repo.getContactUserId(connId) } returns null

        assertEquals(NominationService.TransitionResult.Forbidden, svc.accept(nomId, nomineeId))
    }

    @Test
    fun `accept returns StateConflict when nomination is already accepted`() {
        val alreadyAccepted = makeNomination(status = "accepted")
        every { repo.getById(nomId) } returns alreadyAccepted
        every { repo.getContactUserId(connId) } returns nomineeId

        assertEquals(NominationService.TransitionResult.StateConflict, svc.accept(nomId, nomineeId))
    }

    @Test
    fun `accept returns StateConflict when nomination is declined`() {
        val declined = makeNomination(status = "declined")
        every { repo.getById(nomId) } returns declined
        every { repo.getContactUserId(connId) } returns nomineeId

        assertEquals(NominationService.TransitionResult.StateConflict, svc.accept(nomId, nomineeId))
    }

    @Test
    fun `accept returns StateConflict when nomination is revoked`() {
        val revoked = makeNomination(status = "revoked")
        every { repo.getById(nomId) } returns revoked
        every { repo.getContactUserId(connId) } returns nomineeId

        assertEquals(NominationService.TransitionResult.StateConflict, svc.accept(nomId, nomineeId))
    }

    // ─── decline state machine ────────────────────────────────────────────────

    @Test
    fun `decline transitions pending to declined`() {
        val pending = makeNomination(status = "pending")
        val declined = pending.copy(status = "declined", respondedAt = Instant.now())
        every { repo.getById(nomId) } returns pending
        every { repo.getContactUserId(connId) } returns nomineeId
        every { repo.setRespondedStatus(nomId, "declined") } returns declined

        val result = svc.decline(nomId, nomineeId)
        assertTrue(result is NominationService.TransitionResult.Updated)
        assertEquals("declined", (result as NominationService.TransitionResult.Updated).nomination.status)
    }

    @Test
    fun `decline returns StateConflict when nomination is not pending`() {
        val accepted = makeNomination(status = "accepted")
        every { repo.getById(nomId) } returns accepted
        every { repo.getContactUserId(connId) } returns nomineeId

        assertEquals(NominationService.TransitionResult.StateConflict, svc.decline(nomId, nomineeId))
    }

    @Test
    fun `decline returns Forbidden when caller is not the nominee`() {
        val pending = makeNomination(status = "pending")
        every { repo.getById(nomId) } returns pending
        every { repo.getContactUserId(connId) } returns UUID.randomUUID()

        assertEquals(NominationService.TransitionResult.Forbidden, svc.decline(nomId, nomineeId))
    }

    // ─── revoke state machine ─────────────────────────────────────────────────

    @Test
    fun `revoke transitions pending to revoked`() {
        val pending = makeNomination(status = "pending")
        val revoked = pending.copy(status = "revoked", revokedAt = Instant.now())
        every { repo.getById(nomId) } returns pending
        every { repo.setRevoked(nomId) } returns revoked

        val result = svc.revoke(nomId, ownerId)
        assertTrue(result is NominationService.TransitionResult.Updated)
        assertEquals("revoked", (result as NominationService.TransitionResult.Updated).nomination.status)
    }

    @Test
    fun `revoke transitions accepted to revoked`() {
        val accepted = makeNomination(status = "accepted")
        val revoked = accepted.copy(status = "revoked", revokedAt = Instant.now())
        every { repo.getById(nomId) } returns accepted
        every { repo.setRevoked(nomId) } returns revoked

        val result = svc.revoke(nomId, ownerId)
        assertTrue(result is NominationService.TransitionResult.Updated)
        assertEquals("revoked", (result as NominationService.TransitionResult.Updated).nomination.status)
    }

    @Test
    fun `revoke returns NotFound when nomination does not exist`() {
        every { repo.getById(nomId) } returns null
        assertEquals(NominationService.TransitionResult.NotFound, svc.revoke(nomId, ownerId))
    }

    @Test
    fun `revoke returns Forbidden when caller is not the owner`() {
        val pending = makeNomination(status = "pending", ownerUserId = UUID.randomUUID())
        every { repo.getById(nomId) } returns pending

        assertEquals(NominationService.TransitionResult.Forbidden, svc.revoke(nomId, ownerId))
    }

    @Test
    fun `revoke returns StateConflict when nomination is already declined`() {
        val declined = makeNomination(status = "declined")
        every { repo.getById(nomId) } returns declined

        assertEquals(NominationService.TransitionResult.StateConflict, svc.revoke(nomId, ownerId))
    }

    @Test
    fun `revoke returns StateConflict when nomination is already revoked`() {
        val revoked = makeNomination(status = "revoked")
        every { repo.getById(nomId) } returns revoked

        assertEquals(NominationService.TransitionResult.StateConflict, svc.revoke(nomId, ownerId))
    }

    // ─── list operations ──────────────────────────────────────────────────────

    @Test
    fun `listByOwner delegates to repository`() {
        val records = listOf(makeNomination())
        every { repo.listByOwner(ownerId) } returns records

        assertEquals(records, svc.listByOwner(ownerId))
    }

    @Test
    fun `listReceived delegates to repository`() {
        val records = listOf(makeNomination())
        every { repo.listReceived(nomineeId) } returns records

        assertEquals(records, svc.listReceived(nomineeId))
    }
}
