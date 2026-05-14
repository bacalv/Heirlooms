package digital.heirlooms.server.representation.capsule

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import digital.heirlooms.server.domain.capsule.CapsuleDetail
import digital.heirlooms.server.domain.capsule.CapsuleSummary
import digital.heirlooms.server.representation.responseMapper
import digital.heirlooms.server.representation.upload.UploadRecordResponse
import digital.heirlooms.server.representation.upload.toResponse
import java.time.Instant
import java.time.OffsetDateTime

private data class CapsuleDetailResponse(
    val id: String,
    val shape: String,
    val state: String,
    @JsonProperty("created_at") val createdAt: Instant,
    @JsonProperty("updated_at") val updatedAt: Instant,
    @JsonProperty("unlock_at") val unlockAt: OffsetDateTime,
    val recipients: List<String>,
    val uploads: List<UploadRecordResponse>,
    val message: String,
    @JsonProperty("cancelled_at") @JsonInclude(JsonInclude.Include.ALWAYS) val cancelledAt: Instant?,
    @JsonProperty("delivered_at") @JsonInclude(JsonInclude.Include.ALWAYS) val deliveredAt: Instant?,
)

private data class CapsuleSummaryResponse(
    val id: String,
    val shape: String,
    val state: String,
    @JsonProperty("created_at") val createdAt: Instant,
    @JsonProperty("updated_at") val updatedAt: Instant,
    @JsonProperty("unlock_at") val unlockAt: OffsetDateTime,
    val recipients: List<String>,
    @JsonProperty("upload_count") val uploadCount: Int,
    @JsonProperty("has_message") val hasMessage: Boolean,
    @JsonProperty("cancelled_at") @JsonInclude(JsonInclude.Include.ALWAYS) val cancelledAt: Instant?,
    @JsonProperty("delivered_at") @JsonInclude(JsonInclude.Include.ALWAYS) val deliveredAt: Instant?,
)

private data class CapsuleReverseLookupResponse(
    val id: String,
    val shape: String,
    val state: String,
    @JsonProperty("unlock_at") val unlockAt: OffsetDateTime,
    val recipients: List<String>,
)

fun CapsuleDetail.toDetailJson(): String {
    val r = record
    return responseMapper.writeValueAsString(
        CapsuleDetailResponse(
            id = r.id.toString(),
            shape = r.shape.name.lowercase(),
            state = r.state.name.lowercase(),
            createdAt = r.createdAt,
            updatedAt = r.updatedAt,
            unlockAt = r.unlockAt,
            recipients = recipients,
            uploads = uploads.map { it.toResponse() },
            message = message,
            cancelledAt = r.cancelledAt,
            deliveredAt = r.deliveredAt,
        )
    )
}

fun CapsuleSummary.toSummaryJson(): String {
    val r = record
    return responseMapper.writeValueAsString(
        CapsuleSummaryResponse(
            id = r.id.toString(),
            shape = r.shape.name.lowercase(),
            state = r.state.name.lowercase(),
            createdAt = r.createdAt,
            updatedAt = r.updatedAt,
            unlockAt = r.unlockAt,
            recipients = recipients,
            uploadCount = uploadCount,
            hasMessage = hasMessage,
            cancelledAt = r.cancelledAt,
            deliveredAt = r.deliveredAt,
        )
    )
}

fun CapsuleSummary.toReverseLookupJson(): String {
    val r = record
    return responseMapper.writeValueAsString(
        CapsuleReverseLookupResponse(
            id = r.id.toString(),
            shape = r.shape.name.lowercase(),
            state = r.state.name.lowercase(),
            unlockAt = r.unlockAt,
            recipients = recipients,
        )
    )
}
