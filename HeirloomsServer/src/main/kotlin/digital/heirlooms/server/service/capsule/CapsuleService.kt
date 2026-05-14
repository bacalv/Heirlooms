package digital.heirlooms.server.service.capsule

import digital.heirlooms.server.Database
import digital.heirlooms.server.domain.capsule.CapsuleDetail
import digital.heirlooms.server.domain.capsule.CapsuleShape
import digital.heirlooms.server.domain.capsule.CapsuleState
import digital.heirlooms.server.domain.capsule.CapsuleSummary
import digital.heirlooms.server.repository.capsule.CapsuleRepository
import java.time.OffsetDateTime
import java.util.UUID

private const val MESSAGE_MAX_BYTES = 50_000
private const val RECIPIENT_MAX_LENGTH = 200

/**
 * Encapsulates business logic for capsule lifecycle: creation validation,
 * update validation, state transitions (seal, cancel). The handler owns
 * HTTP parsing and response formatting; this service owns all decisions.
 */
class CapsuleService(
    private val database: Database,
) {
    // ---- Create ----------------------------------------------------------------

    sealed class CreateResult {
        data class Created(val detail: CapsuleDetail) : CreateResult()
        data class Invalid(val message: String) : CreateResult()
        data class UnknownUpload(val id: UUID) : CreateResult()
    }

    fun createCapsule(
        shape: CapsuleShape,
        unlockAt: OffsetDateTime,
        recipients: List<String>,
        uploadIds: List<UUID>,
        message: String,
        userId: UUID,
    ): CreateResult {
        val validationError = validateRecipients(recipients)
            ?: validateMessage(message)
            ?: if (shape == CapsuleShape.SEALED && uploadIds.isEmpty())
                "sealed capsules must have at least one upload"
            else null
        if (validationError != null) return CreateResult.Invalid(validationError)

        val unknownId = uploadIds.firstOrNull { !database.uploadExists(it, userId) }
        if (unknownId != null) return CreateResult.UnknownUpload(unknownId)

        val initialState = if (shape == CapsuleShape.SEALED) CapsuleState.SEALED else CapsuleState.OPEN
        val detail = database.createCapsule(
            id = UUID.randomUUID(),
            createdByUser = "api-user",
            shape = shape,
            state = initialState,
            unlockAt = unlockAt,
            recipients = recipients,
            uploadIds = uploadIds,
            message = message,
            userId = userId,
        )
        return CreateResult.Created(detail)
    }

    // ---- Update ----------------------------------------------------------------

    fun updateCapsule(
        capsuleId: UUID,
        userId: UUID,
        unlockAt: OffsetDateTime?,
        recipients: List<String>?,
        uploadIds: List<UUID>?,
        message: String?,
    ): CapsuleRepository.UpdateResult {
        if (recipients != null) {
            val err = validateRecipients(recipients)
            if (err != null) return CapsuleRepository.UpdateResult.InvalidRecipients(err)
        }
        if (message != null) {
            val err = validateMessage(message)
            if (err != null) return CapsuleRepository.UpdateResult.MessageTooLong(MESSAGE_MAX_BYTES)
        }
        return database.updateCapsule(capsuleId, userId, unlockAt, recipients, uploadIds, message)
    }

    // ---- Read ------------------------------------------------------------------

    fun getCapsule(capsuleId: UUID, userId: UUID): CapsuleDetail? =
        database.getCapsuleById(capsuleId, userId)

    fun listCapsules(states: List<CapsuleState>, orderBy: String, userId: UUID): List<CapsuleSummary> =
        database.listCapsules(states, orderBy, userId)

    fun getCapsulesForUpload(uploadId: UUID, userId: UUID): List<CapsuleSummary>? =
        database.getCapsulesForUpload(uploadId, userId)

    // ---- Seal / cancel ---------------------------------------------------------

    fun sealCapsule(capsuleId: UUID, userId: UUID): CapsuleRepository.SealResult =
        database.sealCapsule(capsuleId, userId)

    fun cancelCapsule(capsuleId: UUID, userId: UUID): CapsuleRepository.CancelResult =
        database.cancelCapsule(capsuleId, userId)

    // ---- Validation helpers ----------------------------------------------------

    private fun validateRecipients(recipients: List<String>): String? = when {
        recipients.isEmpty() -> "recipients must not be empty"
        recipients.any { it.isBlank() } -> "recipients must not contain blank strings"
        recipients.any { it.length > RECIPIENT_MAX_LENGTH } ->
            "each recipient must be $RECIPIENT_MAX_LENGTH characters or fewer"
        else -> null
    }

    private fun validateMessage(message: String): String? =
        if (message.toByteArray().size > MESSAGE_MAX_BYTES)
            "message exceeds maximum size of $MESSAGE_MAX_BYTES bytes"
        else null
}
