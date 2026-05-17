package digital.heirlooms.server.service.connection

import digital.heirlooms.server.domain.connection.NominationRecord
import digital.heirlooms.server.repository.connection.NominationRepository
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Business logic for the executor nomination lifecycle.
 *
 * State machine (ARCH-004 §4):
 *   pending → accepted   (nominee)
 *   pending → declined   (nominee)
 *   pending → revoked    (owner)
 *   accepted → revoked   (owner)
 *
 * Invalid transitions produce HTTP 409 (StateConflict).
 * In M11 revocation does NOT trigger automatic share rotation — a WARN is logged.
 */
class NominationService(private val nominationRepo: NominationRepository) {

    private val log = LoggerFactory.getLogger(NominationService::class.java)

    // ---- Create ----------------------------------------------------------------

    sealed class CreateResult {
        data class Created(val nomination: NominationRecord) : CreateResult()
        /** connection_id does not exist or does not belong to the caller. */
        object ConnectionNotFound : CreateResult()
        /** A pending or accepted nomination already exists for this connection. */
        object Conflict : CreateResult()
    }

    fun createNomination(
        ownerUserId: UUID,
        connectionId: UUID,
        message: String?,
    ): CreateResult {
        // Verify the connection exists and is owned by the caller
        val connectionOwner = nominationRepo.getConnectionOwnerUserId(connectionId)
        if (connectionOwner == null || connectionOwner != ownerUserId) {
            return CreateResult.ConnectionNotFound
        }

        // Prevent duplicate active nominations for the same connection
        if (nominationRepo.hasActiveNomination(connectionId)) {
            return CreateResult.Conflict
        }

        val nomination = nominationRepo.createNomination(
            ownerUserId = ownerUserId,
            connectionId = connectionId,
            message = message,
        )
        return CreateResult.Created(nomination)
    }

    // ---- Read ------------------------------------------------------------------

    fun listByOwner(ownerUserId: UUID): List<NominationRecord> =
        nominationRepo.listByOwner(ownerUserId)

    fun listReceived(nomineeUserId: UUID): List<NominationRecord> =
        nominationRepo.listReceived(nomineeUserId)

    // ---- State transitions ------------------------------------------------------

    sealed class TransitionResult {
        data class Updated(val nomination: NominationRecord) : TransitionResult()
        /** Nomination not found. */
        object NotFound : TransitionResult()
        /** Caller is not the required role (nominee or owner). */
        object Forbidden : TransitionResult()
        /** Nomination is not in the required state for this transition. */
        object StateConflict : TransitionResult()
    }

    /** Nominee accepts a pending nomination. */
    fun accept(nominationId: UUID, nomineeUserId: UUID): TransitionResult =
        respondAsNominee(nominationId, nomineeUserId, "accepted")

    /** Nominee declines a pending nomination. */
    fun decline(nominationId: UUID, nomineeUserId: UUID): TransitionResult =
        respondAsNominee(nominationId, nomineeUserId, "declined")

    private fun respondAsNominee(nominationId: UUID, nomineeUserId: UUID, newStatus: String): TransitionResult {
        val nomination = nominationRepo.getById(nominationId) ?: return TransitionResult.NotFound

        // Verify caller is the nominee via the connection's contact_user_id
        val contactUserId = nominationRepo.getContactUserId(nomination.connectionId)
        if (contactUserId == null || contactUserId != nomineeUserId) {
            return TransitionResult.Forbidden
        }

        // Only pending nominations can be accepted or declined
        if (nomination.status != "pending") {
            return TransitionResult.StateConflict
        }

        val updated = nominationRepo.setRespondedStatus(nominationId, newStatus)
            ?: return TransitionResult.NotFound
        return TransitionResult.Updated(updated)
    }

    /** Owner revokes a pending or accepted nomination. No automatic share rotation in M11. */
    fun revoke(nominationId: UUID, ownerUserId: UUID): TransitionResult {
        val nomination = nominationRepo.getById(nominationId) ?: return TransitionResult.NotFound

        // Verify caller is the owner
        if (nomination.ownerUserId != ownerUserId) {
            return TransitionResult.Forbidden
        }

        // Cannot revoke already declined or revoked nominations
        if (nomination.status == "declined" || nomination.status == "revoked") {
            return TransitionResult.StateConflict
        }

        log.warn(
            "WARN: nomination {} revoked — owner must manually redistribute shares if applicable",
            nominationId,
        )

        val updated = nominationRepo.setRevoked(nominationId)
            ?: return TransitionResult.NotFound
        return TransitionResult.Updated(updated)
    }
}
