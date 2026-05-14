package digital.heirlooms.server.service.keys

import digital.heirlooms.server.domain.keys.PendingDeviceLinkRecord
import digital.heirlooms.server.domain.keys.RecoveryPassphraseRecord
import digital.heirlooms.server.domain.keys.WrappedKeyRecord
import digital.heirlooms.server.repository.keys.KeyRepository
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID

private val VALID_DEVICE_KINDS = setOf("android", "web", "ios")
private const val CODE_CHARSET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

/**
 * Encapsulates device key management: registration, link-flow state machine,
 * recovery passphrase CRUD. Code generation lives here so the handler owns
 * only HTTP parsing and response formatting.
 */
class KeyService(
    private val keyRepo: KeyRepository,
) {
    private val enc = Base64.getEncoder()
    private val dec = Base64.getDecoder()

    // ---- Code generation -------------------------------------------------------

    fun generateLinkCode(): String {
        val sb = StringBuilder(9)
        repeat(4) { sb.append(CODE_CHARSET.random()) }
        sb.append('-')
        repeat(4) { sb.append(CODE_CHARSET.random()) }
        return sb.toString()
    }

    // ---- Device CRUD -----------------------------------------------------------

    sealed class RegisterResult {
        data class Created(val record: WrappedKeyRecord) : RegisterResult()
        object AlreadyRegistered : RegisterResult()
        data class Invalid(val message: String) : RegisterResult()
    }

    fun registerDevice(
        deviceId: String,
        deviceLabel: String,
        deviceKind: String,
        pubkeyFormat: String,
        pubkeyB64: String,
        wrappedMasterKeyB64: String,
        wrapFormat: String,
        userId: UUID,
    ): RegisterResult {
        if (deviceKind !in VALID_DEVICE_KINDS)
            return RegisterResult.Invalid("deviceKind must be one of: android, web, ios")
        if (keyRepo.getWrappedKeyByDeviceIdForUser(deviceId, userId) != null)
            return RegisterResult.AlreadyRegistered
        val pubkey = runCatching { dec.decode(pubkeyB64) }.getOrNull()
            ?: return RegisterResult.Invalid("pubkey is not valid Base64")
        val wrappedMasterKey = runCatching { dec.decode(wrappedMasterKeyB64) }.getOrNull()
            ?: return RegisterResult.Invalid("wrappedMasterKey is not valid Base64")
        val now = Instant.now()
        val record = WrappedKeyRecord(
            id = UUID.randomUUID(),
            deviceId = deviceId,
            deviceLabel = deviceLabel,
            deviceKind = deviceKind,
            pubkeyFormat = pubkeyFormat,
            pubkey = pubkey,
            wrappedMasterKey = wrappedMasterKey,
            wrapFormat = wrapFormat,
            createdAt = now,
            lastUsedAt = now,
            retiredAt = null,
        )
        keyRepo.insertWrappedKey(record, userId)
        return RegisterResult.Created(record)
    }

    fun listDevices(userId: UUID): List<WrappedKeyRecord> = keyRepo.listWrappedKeys(userId)

    fun getDeviceForUser(deviceId: String, userId: UUID): WrappedKeyRecord? =
        keyRepo.getWrappedKeyByDeviceIdForUser(deviceId, userId)

    fun retireDevice(deviceId: String, userId: UUID): Boolean {
        val record = keyRepo.getWrappedKeyByDeviceIdForUser(deviceId, userId) ?: return false
        if (record.retiredAt != null) return false
        keyRepo.retireWrappedKey(record.id, Instant.now())
        return true
    }

    fun touchDevice(deviceId: String, userId: UUID): Boolean {
        val record = keyRepo.getWrappedKeyByDeviceIdForUser(deviceId, userId)
        if (record == null || record.retiredAt != null) return false
        keyRepo.touchWrappedKey(record.id)
        return true
    }

    // ---- Passphrase ------------------------------------------------------------

    fun getPassphrase(userId: UUID): RecoveryPassphraseRecord? = keyRepo.getRecoveryPassphrase(userId)

    sealed class PutPassphraseResult {
        data class Updated(val record: RecoveryPassphraseRecord) : PutPassphraseResult()
        data class Invalid(val message: String) : PutPassphraseResult()
    }

    fun putPassphrase(
        wrappedMasterKeyB64: String,
        wrapFormat: String,
        argon2ParamsJson: String,
        saltB64: String,
        userId: UUID,
    ): PutPassphraseResult {
        val wrappedMasterKey = runCatching { dec.decode(wrappedMasterKeyB64) }.getOrNull()
            ?: return PutPassphraseResult.Invalid("wrappedMasterKey is not valid Base64")
        val salt = runCatching { dec.decode(saltB64) }.getOrNull()
            ?: return PutPassphraseResult.Invalid("salt is not valid Base64")
        keyRepo.upsertRecoveryPassphrase(
            RecoveryPassphraseRecord(
                wrappedMasterKey = wrappedMasterKey,
                wrapFormat = wrapFormat,
                argon2Params = argon2ParamsJson,
                salt = salt,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            ),
            userId,
        )
        val updated = keyRepo.getRecoveryPassphrase(userId)!!
        return PutPassphraseResult.Updated(updated)
    }

    fun deletePassphrase(userId: UUID): Boolean = keyRepo.deleteRecoveryPassphrase(userId)

    // ---- Device link flow ------------------------------------------------------

    data class LinkInitiated(val linkId: UUID, val code: String)

    fun initiateLink(userId: UUID): LinkInitiated {
        val id = UUID.randomUUID()
        val code = generateLinkCode()
        val expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES)
        keyRepo.insertPendingDeviceLink(
            PendingDeviceLinkRecord(
                id = id,
                oneTimeCode = code,
                expiresAt = expiresAt,
                state = "initiated",
                newDeviceId = null, newDeviceLabel = null, newDeviceKind = null,
                newPubkeyFormat = null, newPubkey = null,
                wrappedMasterKey = null, wrapFormat = null,
                userId = userId,
            )
        )
        return LinkInitiated(id, code)
    }

    sealed class RegisterOnLinkResult {
        object Accepted : RegisterOnLinkResult()
        object NotFound : RegisterOnLinkResult()
        object Expired : RegisterOnLinkResult()
        object WrongState : RegisterOnLinkResult()
        data class Invalid(val message: String) : RegisterOnLinkResult()
    }

    fun registerOnLink(
        linkId: UUID,
        code: String,
        deviceId: String,
        deviceLabel: String,
        deviceKind: String,
        pubkeyFormat: String,
        pubkeyB64: String,
    ): RegisterOnLinkResult {
        val link = keyRepo.getPendingDeviceLink(linkId) ?: return RegisterOnLinkResult.NotFound
        if (Instant.now().isAfter(link.expiresAt)) return RegisterOnLinkResult.Expired
        if (link.state != "initiated") return RegisterOnLinkResult.WrongState
        if (code != link.oneTimeCode) return RegisterOnLinkResult.Invalid("code does not match")
        if (deviceKind !in VALID_DEVICE_KINDS) return RegisterOnLinkResult.Invalid("deviceKind must be one of: android, web, ios")
        val pubkey = runCatching { dec.decode(pubkeyB64) }.getOrNull()
            ?: return RegisterOnLinkResult.Invalid("pubkey is not valid Base64")
        keyRepo.registerNewDevice(linkId, deviceId, deviceLabel, deviceKind, pubkeyFormat, pubkey)
        return RegisterOnLinkResult.Accepted
    }

    fun getLinkStatus(linkId: UUID): PendingDeviceLinkRecord? = keyRepo.getPendingDeviceLink(linkId)

    sealed class WrapLinkResult {
        data class Wrapped(val newDevice: WrappedKeyRecord) : WrapLinkResult()
        object NotFound : WrapLinkResult()
        object Expired : WrapLinkResult()
        object WrongState : WrapLinkResult()
        data class Invalid(val message: String) : WrapLinkResult()
    }

    fun wrapLink(
        linkId: UUID,
        wrappedMasterKeyB64: String,
        wrapFormat: String,
        userId: UUID,
    ): WrapLinkResult {
        val link = keyRepo.getPendingDeviceLink(linkId) ?: return WrapLinkResult.NotFound
        if (Instant.now().isAfter(link.expiresAt)) return WrapLinkResult.Expired
        if (link.state != "device_registered") return WrapLinkResult.WrongState
        val wrappedMasterKey = runCatching { dec.decode(wrappedMasterKeyB64) }.getOrNull()
            ?: return WrapLinkResult.Invalid("wrappedMasterKey is not valid Base64")
        keyRepo.completeDeviceLink(
            id = linkId,
            wrappedMasterKey = wrappedMasterKey,
            wrapFormat = wrapFormat,
            deviceId = link.newDeviceId!!,
            deviceLabel = link.newDeviceLabel!!,
            deviceKind = link.newDeviceKind!!,
            pubkeyFormat = link.newPubkeyFormat!!,
            pubkey = link.newPubkey!!,
            userId = userId,
        )
        val newDevice = keyRepo.getWrappedKeyByDeviceIdForUser(link.newDeviceId, userId)!!
        return WrapLinkResult.Wrapped(newDevice)
    }
}
