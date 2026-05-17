package digital.heirlooms.server.service.connection

import digital.heirlooms.server.domain.connection.ConnectionRecord
import digital.heirlooms.server.repository.connection.ConnectionRepository
import java.util.UUID

/**
 * Business logic for connection CRUD.
 *
 * Rules enforced here:
 * - At least one of contactUserId or email must be non-null (creation).
 * - sharing_pubkey is populated server-side via account_sharing_keys on bound creation.
 * - DELETE must check for active nominations before removing.
 */
class ConnectionService(private val connectionRepo: ConnectionRepository) {

    // ---- Create ----------------------------------------------------------------

    sealed class CreateResult {
        data class Created(val connection: ConnectionRecord) : CreateResult()
        data class Invalid(val message: String) : CreateResult()
        object Conflict : CreateResult()
    }

    fun createConnection(
        ownerUserId: UUID,
        contactUserId: UUID?,
        displayName: String,
        email: String?,
        roles: List<String>,
    ): CreateResult {
        if (contactUserId == null && email == null) {
            return CreateResult.Invalid("at least one of contact_user_id or email must be provided")
        }
        if (displayName.isBlank()) {
            return CreateResult.Invalid("display_name must not be blank")
        }

        // Backfill sharing_pubkey from account_sharing_keys for bound connections.
        val sharingPubkey = if (contactUserId != null) {
            connectionRepo.lookupSharingPubkey(contactUserId)
        } else null

        return try {
            val connection = connectionRepo.createConnection(
                ownerUserId = ownerUserId,
                contactUserId = contactUserId,
                displayName = displayName,
                email = email,
                sharingPubkey = sharingPubkey,
                roles = roles,
            )
            CreateResult.Created(connection)
        } catch (e: Exception) {
            // UNIQUE constraint violations from the DB layer signal a conflict.
            val msg = e.message ?: ""
            if (msg.contains("unique", ignoreCase = true) ||
                msg.contains("duplicate", ignoreCase = true) ||
                msg.contains("23505")) {
                CreateResult.Conflict
            } else {
                throw e
            }
        }
    }

    // ---- Read ------------------------------------------------------------------

    fun listConnections(ownerUserId: UUID): List<ConnectionRecord> =
        connectionRepo.listConnections(ownerUserId)

    fun getConnection(id: UUID, ownerUserId: UUID): ConnectionRecord? =
        connectionRepo.getConnection(id, ownerUserId)

    // ---- Update ----------------------------------------------------------------

    sealed class UpdateResult {
        data class Updated(val connection: ConnectionRecord) : UpdateResult()
        object NotFound : UpdateResult()
    }

    fun updateConnection(
        id: UUID,
        ownerUserId: UUID,
        displayName: String?,
        roles: List<String>?,
        sharingPubkey: String?,
        clearSharingPubkey: Boolean,
    ): UpdateResult {
        val updated = connectionRepo.updateConnection(
            id = id,
            ownerUserId = ownerUserId,
            displayName = displayName,
            roles = roles,
            sharingPubkey = sharingPubkey,
            clearSharingPubkey = clearSharingPubkey,
        )
        return if (updated != null) UpdateResult.Updated(updated) else UpdateResult.NotFound
    }

    // ---- Delete ----------------------------------------------------------------

    sealed class DeleteResult {
        object Deleted : DeleteResult()
        object NotFound : DeleteResult()
        object ActiveNominationsExist : DeleteResult()
    }

    fun deleteConnection(id: UUID, ownerUserId: UUID): DeleteResult {
        return when (connectionRepo.deleteConnection(id, ownerUserId)) {
            ConnectionRepository.DeleteResult.Deleted -> DeleteResult.Deleted
            ConnectionRepository.DeleteResult.NotFound -> DeleteResult.NotFound
            ConnectionRepository.DeleteResult.ActiveNominationsExist -> DeleteResult.ActiveNominationsExist
        }
    }
}
