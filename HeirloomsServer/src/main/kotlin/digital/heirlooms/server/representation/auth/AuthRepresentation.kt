package digital.heirlooms.server.representation.auth

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import digital.heirlooms.server.representation.responseMapper
import java.time.Instant
import java.util.Base64
import java.util.UUID

private val urlEnc = Base64.getUrlEncoder().withoutPadding()

private data class SessionTokenResponse(
    @JsonProperty("session_token") val sessionToken: String,
    @JsonProperty("user_id") val userId: String,
    @JsonProperty("expires_at") val expiresAt: Instant,
)

private data class ChallengeResponse(
    @JsonProperty("auth_salt") val authSalt: String,
)

private data class InviteResponse(
    val token: String,
    @JsonProperty("expires_at") val expiresAt: Instant,
)

private data class PairingInitiateResponse(
    val code: String,
    @JsonProperty("expires_at") val expiresAt: Instant,
)

private data class PairingStatusCompleteResponse(
    val state: String,
    @JsonProperty("session_token") val sessionToken: String,
    @JsonProperty("wrapped_master_key") val wrappedMasterKey: String,
    @JsonProperty("wrap_format") val wrapFormat: String,
    @JsonProperty("expires_at") @JsonInclude(JsonInclude.Include.ALWAYS) val expiresAt: Instant?,
)

fun sessionTokenJson(token: String, userId: UUID, expiresAt: Instant): String =
    responseMapper.writeValueAsString(
        SessionTokenResponse(
            sessionToken = token,
            userId = userId.toString(),
            expiresAt = expiresAt,
        )
    )

fun challengeResponseJson(salt: ByteArray): String =
    responseMapper.writeValueAsString(ChallengeResponse(authSalt = urlEnc.encodeToString(salt)))

fun inviteResponseJson(token: String, expiresAt: Instant): String =
    responseMapper.writeValueAsString(InviteResponse(token = token, expiresAt = expiresAt))

fun pairingInitiateResponseJson(code: String, expiresAt: Instant): String =
    responseMapper.writeValueAsString(PairingInitiateResponse(code = code, expiresAt = expiresAt))

fun pairingStatusCompleteJson(
    sessionToken: String,
    wrappedMasterKey: ByteArray,
    wrapFormat: String,
    expiresAt: Instant?,
): String =
    responseMapper.writeValueAsString(
        PairingStatusCompleteResponse(
            state = "complete",
            sessionToken = sessionToken,
            wrappedMasterKey = urlEnc.encodeToString(wrappedMasterKey),
            wrapFormat = wrapFormat,
            expiresAt = expiresAt,
        )
    )
