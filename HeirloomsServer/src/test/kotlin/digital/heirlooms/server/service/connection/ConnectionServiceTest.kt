package digital.heirlooms.server.service.connection

import digital.heirlooms.server.domain.connection.ConnectionRecord
import digital.heirlooms.server.repository.connection.ConnectionRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * Unit tests for [ConnectionService].
 *
 * Covers:
 * - sharing_pubkey backfill on bound connection creation
 * - Validation: neither contact_user_id nor email provided → Invalid
 * - DELETE blocking: active nominations → ActiveNominationsExist
 * - Happy paths for all CRUD operations
 */
class ConnectionServiceTest {

    private val repo = mockk<ConnectionRepository>()
    private lateinit var svc: ConnectionService

    private val ownerId = UUID.randomUUID()
    private val contactId = UUID.randomUUID()
    private val connId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        svc = ConnectionService(repo)
    }

    private fun makeRecord(
        displayName: String = "Alice",
        sharingPubkey: String? = null,
        roles: List<String> = listOf("recipient"),
    ) = ConnectionRecord(
        id = connId,
        ownerUserId = ownerId,
        contactUserId = contactId,
        displayName = displayName,
        email = null,
        sharingPubkey = sharingPubkey,
        roles = roles,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    // ─── createConnection ─────────────────────────────────────────────────────

    @Test
    fun `createConnection with contactUserId backfills sharing_pubkey from account_sharing_keys`() {
        val pubkey = "base64urlPubkey=="
        every { repo.lookupSharingPubkey(contactId) } returns pubkey
        val record = makeRecord(sharingPubkey = pubkey)
        every { repo.createConnection(ownerId, contactId, "Alice", null, pubkey, listOf("recipient")) } returns record

        val result = svc.createConnection(ownerId, contactId, "Alice", null, listOf("recipient"))

        assertTrue(result is ConnectionService.CreateResult.Created)
        assertEquals(pubkey, (result as ConnectionService.CreateResult.Created).connection.sharingPubkey)
        verify { repo.lookupSharingPubkey(contactId) }
    }

    @Test
    fun `createConnection with contactUserId sets null sharing_pubkey when none found`() {
        every { repo.lookupSharingPubkey(contactId) } returns null
        val record = makeRecord(sharingPubkey = null)
        every { repo.createConnection(ownerId, contactId, "Alice", null, null, emptyList()) } returns record

        val result = svc.createConnection(ownerId, contactId, "Alice", null, emptyList())

        assertTrue(result is ConnectionService.CreateResult.Created)
        verify { repo.lookupSharingPubkey(contactId) }
    }

    @Test
    fun `createConnection with email only does not look up sharing_pubkey`() {
        val record = ConnectionRecord(
            id = connId, ownerUserId = ownerId, contactUserId = null,
            displayName = "Bob", email = "bob@example.com", sharingPubkey = null,
            roles = emptyList(), createdAt = Instant.now(), updatedAt = Instant.now(),
        )
        every { repo.createConnection(ownerId, null, "Bob", "bob@example.com", null, emptyList()) } returns record

        val result = svc.createConnection(ownerId, null, "Bob", "bob@example.com", emptyList())

        assertTrue(result is ConnectionService.CreateResult.Created)
        verify(exactly = 0) { repo.lookupSharingPubkey(any()) }
    }

    @Test
    fun `createConnection returns Invalid when neither contactUserId nor email provided`() {
        val result = svc.createConnection(ownerId, null, "Alice", null, emptyList())
        assertTrue(result is ConnectionService.CreateResult.Invalid)
    }

    @Test
    fun `createConnection returns Invalid when displayName is blank`() {
        val result = svc.createConnection(ownerId, contactId, "   ", null, emptyList())
        assertTrue(result is ConnectionService.CreateResult.Invalid)
    }

    @Test
    fun `createConnection returns Conflict on unique constraint violation`() {
        every { repo.lookupSharingPubkey(contactId) } returns null
        every { repo.createConnection(any(), any(), any(), any(), any(), any()) } throws
            RuntimeException("ERROR: duplicate key value violates unique constraint")

        val result = svc.createConnection(ownerId, contactId, "Alice", null, emptyList())
        assertEquals(ConnectionService.CreateResult.Conflict, result)
    }

    // ─── deleteConnection ─────────────────────────────────────────────────────

    @Test
    fun `deleteConnection returns Deleted on success`() {
        every { repo.deleteConnection(connId, ownerId) } returns ConnectionRepository.DeleteResult.Deleted

        val result = svc.deleteConnection(connId, ownerId)
        assertEquals(ConnectionService.DeleteResult.Deleted, result)
    }

    @Test
    fun `deleteConnection returns NotFound when connection does not belong to caller`() {
        every { repo.deleteConnection(connId, ownerId) } returns ConnectionRepository.DeleteResult.NotFound

        val result = svc.deleteConnection(connId, ownerId)
        assertEquals(ConnectionService.DeleteResult.NotFound, result)
    }

    @Test
    fun `deleteConnection returns ActiveNominationsExist when active nominations block deletion`() {
        every { repo.deleteConnection(connId, ownerId) } returns
            ConnectionRepository.DeleteResult.ActiveNominationsExist

        val result = svc.deleteConnection(connId, ownerId)
        assertEquals(ConnectionService.DeleteResult.ActiveNominationsExist, result)
    }

    // ─── listConnections / getConnection ─────────────────────────────────────

    @Test
    fun `listConnections delegates to repository`() {
        val records = listOf(makeRecord())
        every { repo.listConnections(ownerId) } returns records

        assertEquals(records, svc.listConnections(ownerId))
    }

    @Test
    fun `getConnection returns null when not found`() {
        every { repo.getConnection(connId, ownerId) } returns null
        assertEquals(null, svc.getConnection(connId, ownerId))
    }

    @Test
    fun `getConnection returns record when found`() {
        val record = makeRecord()
        every { repo.getConnection(connId, ownerId) } returns record
        assertEquals(record, svc.getConnection(connId, ownerId))
    }

    // ─── updateConnection ─────────────────────────────────────────────────────

    @Test
    fun `updateConnection returns Updated with new record on success`() {
        val updated = makeRecord(displayName = "Alice Smith")
        every { repo.updateConnection(connId, ownerId, "Alice Smith", null, null, false) } returns updated

        val result = svc.updateConnection(connId, ownerId, "Alice Smith", null, null, false)
        assertTrue(result is ConnectionService.UpdateResult.Updated)
        assertEquals("Alice Smith", (result as ConnectionService.UpdateResult.Updated).connection.displayName)
    }

    @Test
    fun `updateConnection returns NotFound when repository returns null`() {
        every { repo.updateConnection(connId, ownerId, any(), any(), any(), any()) } returns null

        val result = svc.updateConnection(connId, ownerId, "Alice Smith", null, null, false)
        assertEquals(ConnectionService.UpdateResult.NotFound, result)
    }
}
