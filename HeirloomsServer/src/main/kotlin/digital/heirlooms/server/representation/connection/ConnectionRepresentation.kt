package digital.heirlooms.server.representation.connection

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import digital.heirlooms.server.domain.connection.ConnectionRecord
import digital.heirlooms.server.representation.responseMapper
import java.time.Instant
import java.util.UUID

@JsonInclude(JsonInclude.Include.ALWAYS)
private data class ConnectionResponse(
    val id: String,
    @JsonProperty("display_name") val displayName: String,
    @JsonProperty("contact_user_id") val contactUserId: String?,
    val email: String?,
    @JsonProperty("sharing_pubkey") val sharingPubkey: String?,
    val roles: List<String>,
    @JsonProperty("created_at") val createdAt: Instant,
    @JsonProperty("updated_at") val updatedAt: Instant,
)

private fun ConnectionRecord.toResponse() = ConnectionResponse(
    id = id.toString(),
    displayName = displayName,
    contactUserId = contactUserId?.toString(),
    email = email,
    sharingPubkey = sharingPubkey,
    roles = roles,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun ConnectionRecord.toJson(): String = responseMapper.writeValueAsString(toResponse())

fun ConnectionRecord.toSingleJson(): String =
    """{"connection":${toJson()}}"""

fun List<ConnectionRecord>.toListJson(): String {
    val items = joinToString(",") { it.toJson() }
    return """{"connections":[$items]}"""
}
