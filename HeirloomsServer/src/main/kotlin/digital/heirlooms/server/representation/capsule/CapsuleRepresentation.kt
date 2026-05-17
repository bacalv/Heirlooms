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
import java.util.Base64

private val b64urlEncCR = Base64.getUrlEncoder().withoutPadding()

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
    // M11 fields — null when the capsule was not sealed via M11 /seal
    @JsonProperty("wrapped_capsule_key") @JsonInclude(JsonInclude.Include.NON_NULL) val wrappedCapsuleKey: String?,
    @JsonProperty("capsule_key_format") @JsonInclude(JsonInclude.Include.NON_NULL) val capsuleKeyFormat: String?,
    @JsonProperty("tlock_round") @JsonInclude(JsonInclude.Include.NON_NULL) val tlockRound: Long?,
    @JsonProperty("tlock_chain_id") @JsonInclude(JsonInclude.Include.NON_NULL) val tlockChainId: String?,
    /** IBE ciphertext — safe to return; only decryptable after the drand round publishes. */
    @JsonProperty("tlock_wrapped_key") @JsonInclude(JsonInclude.Include.NON_NULL) val tlockWrappedKey: String?,
    /** SHA-256(DEK_tlock) — the digest is safe to return; DEK_tlock itself is not. */
    @JsonProperty("tlock_key_digest") @JsonInclude(JsonInclude.Include.NON_NULL) val tlockKeyDigest: String?,
    @JsonProperty("shamir_threshold") @JsonInclude(JsonInclude.Include.NON_NULL) val shamirThreshold: Int?,
    @JsonProperty("shamir_total_shares") @JsonInclude(JsonInclude.Include.NON_NULL) val shamirTotalShares: Int?,
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
            // M11 fields: base64url-encode byte arrays, pass primitives as-is
            wrappedCapsuleKey = r.wrappedCapsuleKey?.let { b64urlEncCR.encodeToString(it) },
            capsuleKeyFormat  = r.capsuleKeyFormat,
            tlockRound        = r.tlockRound,
            tlockChainId      = r.tlockChainId,
            // IBE ciphertext — safe to return; only unlocks after round publishes
            tlockWrappedKey   = r.tlockWrappedKey?.let { b64urlEncCR.encodeToString(it) },
            // Digest of DEK_tlock — safe to return (preimage is not)
            tlockKeyDigest    = r.tlockKeyDigest?.let { b64urlEncCR.encodeToString(it) },
            shamirThreshold   = r.shamirThreshold,
            shamirTotalShares = r.shamirTotalShares,
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
