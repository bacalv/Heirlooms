package digital.heirlooms.server.representation.connection

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import digital.heirlooms.server.domain.connection.NominationRecord
import digital.heirlooms.server.representation.responseMapper
import java.time.Instant
import java.util.UUID

@JsonInclude(JsonInclude.Include.ALWAYS)
private data class NominationResponse(
    val id: String,
    @JsonProperty("connection_id") val connectionId: String,
    @JsonProperty("owner_user_id") val ownerUserId: String,
    val status: String,
    @JsonProperty("offered_at") val offeredAt: Instant,
    @JsonProperty("responded_at") val respondedAt: Instant?,
    @JsonProperty("revoked_at") val revokedAt: Instant?,
    val message: String?,
)

private fun NominationRecord.toResponse() = NominationResponse(
    id = id.toString(),
    connectionId = connectionId.toString(),
    ownerUserId = ownerUserId.toString(),
    status = status,
    offeredAt = offeredAt,
    respondedAt = respondedAt,
    revokedAt = revokedAt,
    message = message,
)

fun NominationRecord.toJson(): String = responseMapper.writeValueAsString(toResponse())

fun NominationRecord.toSingleJson(): String =
    """{"nomination":${toJson()}}"""

fun List<NominationRecord>.toNominationListJson(): String {
    val items = joinToString(",") { it.toJson() }
    return """{"nominations":[$items]}"""
}
