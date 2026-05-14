package digital.heirlooms.server.representation.keys

import com.fasterxml.jackson.databind.JsonNode
import digital.heirlooms.server.domain.keys.RecoveryPassphraseRecord
import digital.heirlooms.server.domain.keys.WrappedKeyRecord
import digital.heirlooms.server.representation.responseMapper
import java.time.Instant
import java.util.Base64

private val enc = Base64.getEncoder()

private data class WrappedKeyResponse(
    val id: String,
    val deviceId: String,
    val deviceLabel: String,
    val deviceKind: String,
    val pubkeyFormat: String,
    val pubkey: String,
    val wrappedMasterKey: String,
    val wrapFormat: String,
    val createdAt: Instant,
    val lastUsedAt: Instant,
    val retiredAt: Instant?,
)

private data class RecoveryPassphraseResponse(
    val wrappedMasterKey: String,
    val wrapFormat: String,
    val argon2Params: JsonNode,
    val salt: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun WrappedKeyRecord.toJson(): String =
    responseMapper.writeValueAsString(
        WrappedKeyResponse(
            id = id.toString(),
            deviceId = deviceId,
            deviceLabel = deviceLabel,
            deviceKind = deviceKind,
            pubkeyFormat = pubkeyFormat,
            pubkey = enc.encodeToString(pubkey),
            wrappedMasterKey = enc.encodeToString(wrappedMasterKey),
            wrapFormat = wrapFormat,
            createdAt = createdAt,
            lastUsedAt = lastUsedAt,
            retiredAt = retiredAt,
        )
    )

fun RecoveryPassphraseRecord.toJson(): String =
    responseMapper.writeValueAsString(
        RecoveryPassphraseResponse(
            wrappedMasterKey = enc.encodeToString(wrappedMasterKey),
            wrapFormat = wrapFormat,
            argon2Params = responseMapper.readTree(argon2Params),
            salt = enc.encodeToString(salt),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    )
