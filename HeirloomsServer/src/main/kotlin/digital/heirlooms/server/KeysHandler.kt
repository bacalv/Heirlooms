package digital.heirlooms.server

import digital.heirlooms.server.domain.keys.PendingDeviceLinkRecord
import digital.heirlooms.server.domain.keys.RecoveryPassphraseRecord
import digital.heirlooms.server.domain.keys.WrappedKeyRecord
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import org.http4k.contract.ContractRoute
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.core.Method.DELETE
import org.http4k.core.Method.GET
import org.http4k.core.Method.PATCH
import org.http4k.core.Method.POST
import org.http4k.core.Method.PUT
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.ACCEPTED
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.CONFLICT
import org.http4k.core.Status.Companion.CREATED
import org.http4k.core.Status.Companion.GONE
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.NO_CONTENT
import org.http4k.core.Status.Companion.OK
import org.http4k.lens.Path
import org.http4k.lens.uuid
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID

private val keysEnc = Base64.getEncoder()
private val keysDec = Base64.getDecoder()
private val keysMapper = ObjectMapper()

private val VALID_DEVICE_KINDS = setOf("android", "web", "ios")
private const val CODE_CHARSET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

private fun generateLinkCode(): String {
    val sb = StringBuilder(9)
    repeat(4) { sb.append(CODE_CHARSET.random()) }
    sb.append('-')
    repeat(4) { sb.append(CODE_CHARSET.random()) }
    return sb.toString()
}

fun keysRoutes(database: Database): List<ContractRoute> = listOf(
    registerDeviceRoute(database),
    listDevicesRoute(database),
    retireDeviceRoute(database),
    touchDeviceRoute(database),
    getPassphraseRoute(database),
    putPassphraseRoute(database),
    deletePassphraseRoute(database),
    initiateLinkRoute(database),
    registerLinkRoute(database),
    linkStatusRoute(database),
    wrapLinkRoute(database),
)

// ---- Device CRUD -----------------------------------------------------------

private fun registerDeviceRoute(database: Database): ContractRoute =
    "/devices" meta {
        summary = "Register a device"
        description = "Stores a device public key and wrapped master key. Returns 409 if deviceId already registered."
    } bindContract POST to { request: Request ->
        try {
            registerDevice(request, database)
        } catch (e: Exception) {
            Response(INTERNAL_SERVER_ERROR).body("Failed to register device: ${e.message}")
        }
    }

private fun registerDevice(request: Request, database: Database): Response {
    val node = keysMapper.readTree(request.bodyString())
    val deviceId = node?.get("deviceId")?.asText()
    val deviceLabel = node?.get("deviceLabel")?.asText()
    val deviceKind = node?.get("deviceKind")?.asText()
    val pubkeyFormat = node?.get("pubkeyFormat")?.asText()
    val pubkeyB64 = node?.get("pubkey")?.asText()
    val wrappedMasterKeyB64 = node?.get("wrappedMasterKey")?.asText()
    val wrapFormat = node?.get("wrapFormat")?.asText()

    if (deviceId.isNullOrBlank() || deviceLabel.isNullOrBlank() || deviceKind.isNullOrBlank() ||
        pubkeyFormat.isNullOrBlank() || pubkeyB64.isNullOrBlank() ||
        wrappedMasterKeyB64.isNullOrBlank() || wrapFormat.isNullOrBlank())
        return Response(BAD_REQUEST).body("Missing required fields")
    if (deviceKind !in VALID_DEVICE_KINDS)
        return Response(BAD_REQUEST).header("Content-Type", "application/json")
            .body("""{"error":"deviceKind must be one of: android, web, ios"}""")
    val userId = request.authUserId()
    if (database.getWrappedKeyByDeviceIdForUser(deviceId, userId) != null)
        return Response(CONFLICT).header("Content-Type", "application/json")
            .body("""{"error":"deviceId already registered"}""")

    val pubkey = runCatching { keysDec.decode(pubkeyB64) }.getOrNull()
        ?: return Response(BAD_REQUEST).body("pubkey is not valid Base64")
    val wrappedMasterKey = runCatching { keysDec.decode(wrappedMasterKeyB64) }.getOrNull()
        ?: return Response(BAD_REQUEST).body("wrappedMasterKey is not valid Base64")
    val now = Instant.now()
    val record = WrappedKeyRecord(
        id = UUID.randomUUID(), deviceId = deviceId, deviceLabel = deviceLabel, deviceKind = deviceKind,
        pubkeyFormat = pubkeyFormat, pubkey = pubkey, wrappedMasterKey = wrappedMasterKey,
        wrapFormat = wrapFormat, createdAt = now, lastUsedAt = now, retiredAt = null,
    )
    database.insertWrappedKey(record, userId)
    return Response(CREATED).header("Content-Type", "application/json").body(record.toJson())
}

private fun listDevicesRoute(database: Database): ContractRoute =
    "/devices" meta {
        summary = "List active devices"
        description = "Returns all non-retired wrapped_keys rows."
    } bindContract GET to { request: Request ->
        try {
            val keys = database.listWrappedKeys(request.authUserId())
            val node = JsonNodeFactory.instance.arrayNode()
            keys.forEach { node.add(keysMapper.readTree(it.toJson())) }
            Response(OK).header("Content-Type", "application/json").body(node.toString())
        } catch (e: Exception) {
            Response(INTERNAL_SERVER_ERROR).body("Failed to list devices: ${e.message}")
        }
    }

private fun retireDeviceRoute(database: Database): ContractRoute {
    val deviceId = Path.of("deviceId")
    return "/devices" / deviceId meta {
        summary = "Retire a device"
        description = "Soft-retires a device by setting retired_at. Returns 404 if not found, 409 if already retired."
    } bindContract DELETE to { dId: String ->
        { request: Request ->
            try {
                val record = database.getWrappedKeyByDeviceIdForUser(dId, request.authUserId())
                when {
                    record == null -> Response(NOT_FOUND)
                    record.retiredAt != null ->
                        Response(CONFLICT).header("Content-Type", "application/json")
                            .body("""{"error":"device is already retired"}""")
                    else -> {
                        database.retireWrappedKey(record.id, Instant.now())
                        Response(NO_CONTENT)
                    }
                }
            } catch (e: Exception) {
                Response(INTERNAL_SERVER_ERROR).body("Failed to retire device: ${e.message}")
            }
        }
    }
}

private fun touchDeviceRoute(database: Database): ContractRoute {
    val deviceId = Path.of("deviceId")
    return "/devices" / deviceId / "used" meta {
        summary = "Update last_used_at"
        description = "Sets last_used_at = NOW() for the given device. Returns 404 if not found or retired."
    } bindContract PATCH to { dId: String, _: String ->
        { request: Request ->
            try {
                val record = database.getWrappedKeyByDeviceIdForUser(dId, request.authUserId())
                when {
                    record == null || record.retiredAt != null -> Response(NOT_FOUND)
                    else -> {
                        database.touchWrappedKey(record.id)
                        Response(NO_CONTENT)
                    }
                }
            } catch (e: Exception) {
                Response(INTERNAL_SERVER_ERROR).body("Failed to touch device: ${e.message}")
            }
        }
    }
}

// ---- Passphrase CRUD -------------------------------------------------------

private fun getPassphraseRoute(database: Database): ContractRoute =
    "/passphrase" meta {
        summary = "Get recovery passphrase record"
        description = "Returns the passphrase-wrapped master key backup, or 404 if none exists."
    } bindContract GET to { request: Request ->
        try {
            val record = database.getRecoveryPassphrase(request.authUserId())
            if (record == null) Response(NOT_FOUND)
            else Response(OK).header("Content-Type", "application/json").body(record.toJson())
        } catch (e: Exception) {
            Response(INTERNAL_SERVER_ERROR).body("Failed to get passphrase: ${e.message}")
        }
    }

private fun putPassphraseRoute(database: Database): ContractRoute =
    "/passphrase" meta {
        summary = "Create or replace recovery passphrase"
        description = "Upserts the passphrase-wrapped master key."
    } bindContract PUT to { request: Request ->
        try {
            putPassphrase(request, database, request.authUserId())
        } catch (e: Exception) {
            Response(INTERNAL_SERVER_ERROR).body("Failed to put passphrase: ${e.message}")
        }
    }

private fun putPassphrase(request: Request, database: Database, userId: java.util.UUID): Response {
    val node = keysMapper.readTree(request.bodyString())
    val wrappedMasterKeyB64 = node?.get("wrappedMasterKey")?.asText()
    val wrapFormat = node?.get("wrapFormat")?.asText()
    val argon2Params = node?.get("argon2Params")
    val saltB64 = node?.get("salt")?.asText()

    if (wrappedMasterKeyB64.isNullOrBlank() || wrapFormat.isNullOrBlank() ||
        argon2Params == null || saltB64.isNullOrBlank())
        return Response(BAD_REQUEST).body("Missing required fields")

    val wrappedMasterKey = runCatching { keysDec.decode(wrappedMasterKeyB64) }.getOrNull()
        ?: return Response(BAD_REQUEST).body("wrappedMasterKey is not valid Base64")
    val salt = runCatching { keysDec.decode(saltB64) }.getOrNull()
        ?: return Response(BAD_REQUEST).body("salt is not valid Base64")

    database.upsertRecoveryPassphrase(
        RecoveryPassphraseRecord(
            wrappedMasterKey = wrappedMasterKey, wrapFormat = wrapFormat,
            argon2Params = argon2Params.toString(), salt = salt,
            createdAt = Instant.now(), updatedAt = Instant.now(),
        ),
        userId,
    )
    val updated = database.getRecoveryPassphrase(userId)!!
    return Response(OK).header("Content-Type", "application/json").body(updated.toJson())
}

private fun deletePassphraseRoute(database: Database): ContractRoute =
    "/passphrase" meta {
        summary = "Delete recovery passphrase"
        description = "Removes the passphrase-wrapped master key. Returns 404 if none exists."
    } bindContract DELETE to { request: Request ->
        try {
            val deleted = database.deleteRecoveryPassphrase(request.authUserId())
            if (deleted) Response(NO_CONTENT) else Response(NOT_FOUND)
        } catch (e: Exception) {
            Response(INTERNAL_SERVER_ERROR).body("Failed to delete passphrase: ${e.message}")
        }
    }

// ---- Device link flow -------------------------------------------------------

private fun initiateLinkRoute(database: Database): ContractRoute =
    "/link/initiate" meta {
        summary = "Initiate a device link"
        description = "Trusted device starts a link session. Returns a one-time code and linkId. Expires in 15 minutes."
    } bindContract POST to { request: Request ->
        try {
            val id = UUID.randomUUID()
            val code = generateLinkCode()
            val expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES)
            database.insertPendingDeviceLink(
                PendingDeviceLinkRecord(
                    id = id, oneTimeCode = code, expiresAt = expiresAt, state = "initiated",
                    newDeviceId = null, newDeviceLabel = null, newDeviceKind = null,
                    newPubkeyFormat = null, newPubkey = null, wrappedMasterKey = null, wrapFormat = null,
                    userId = request.authUserId(),
                )
            )
            Response(OK).header("Content-Type", "application/json").body("""{"linkId":"$id","code":"$code"}""")
        } catch (e: Exception) {
            Response(INTERNAL_SERVER_ERROR).body("Failed to initiate link: ${e.message}")
        }
    }

private fun registerLinkRoute(database: Database): ContractRoute {
    val linkId = Path.uuid().of("linkId")
    return "/link" / linkId / "register" meta {
        summary = "Register new device on link"
        description = "New device submits its pubkey after typing the code."
    } bindContract POST to { lId: UUID, _: String ->
        { request: Request ->
            try {
                registerOnLink(lId, request, database)
            } catch (e: Exception) {
                Response(INTERNAL_SERVER_ERROR).body("Failed to register device on link: ${e.message}")
            }
        }
    }
}

private fun registerOnLink(linkId: UUID, request: Request, database: Database): Response {
    val link = database.getPendingDeviceLink(linkId)
        ?: return Response(NOT_FOUND)
    if (Instant.now().isAfter(link.expiresAt))
        return Response(GONE).header("Content-Type", "application/json").body("""{"error":"link has expired"}""")
    if (link.state != "initiated")
        return Response(CONFLICT).header("Content-Type", "application/json")
            .body("""{"error":"link is not in initiated state"}""")

    val node = keysMapper.readTree(request.bodyString())
    val code = node?.get("code")?.asText()
    val deviceId = node?.get("deviceId")?.asText()
    val deviceLabel = node?.get("deviceLabel")?.asText()
    val deviceKind = node?.get("deviceKind")?.asText()
    val pubkeyFormat = node?.get("pubkeyFormat")?.asText()
    val pubkeyB64 = node?.get("pubkey")?.asText()

    if (code != link.oneTimeCode)
        return Response(BAD_REQUEST).header("Content-Type", "application/json").body("""{"error":"code does not match"}""")
    if (deviceId.isNullOrBlank() || deviceLabel.isNullOrBlank() || deviceKind.isNullOrBlank() ||
        pubkeyFormat.isNullOrBlank() || pubkeyB64.isNullOrBlank())
        return Response(BAD_REQUEST).body("Missing required fields")
    if (deviceKind !in VALID_DEVICE_KINDS)
        return Response(BAD_REQUEST).header("Content-Type", "application/json")
            .body("""{"error":"deviceKind must be one of: android, web, ios"}""")

    val pubkey = runCatching { keysDec.decode(pubkeyB64) }.getOrNull()
        ?: return Response(BAD_REQUEST).body("pubkey is not valid Base64")

    database.registerNewDevice(linkId, deviceId, deviceLabel, deviceKind, pubkeyFormat, pubkey)
    return Response(ACCEPTED)
}

private fun linkStatusRoute(database: Database): ContractRoute {
    val linkId = Path.uuid().of("linkId")
    return "/link" / linkId / "status" meta {
        summary = "Poll link status"
        description = "Both trusted and new device poll this. Returns state, pubkey when device_registered, wrappedMasterKey when wrap_complete."
    } bindContract GET to { lId: UUID, _: String ->
        { _: Request ->
            try {
                val link = database.getPendingDeviceLink(lId)
                if (link == null) {
                    Response(NOT_FOUND)
                } else if (link.state != "wrap_complete" && Instant.now().isAfter(link.expiresAt)) {
                    Response(GONE).header("Content-Type", "application/json").body("""{"error":"link has expired"}""")
                } else {
                    val node = JsonNodeFactory.instance.objectNode()
                    node.put("state", link.state)
                    if (link.state in setOf("device_registered", "wrap_complete")) {
                        if (link.newDeviceKind != null) node.put("newDeviceKind", link.newDeviceKind) else node.putNull("newDeviceKind")
                        if (link.newPubkeyFormat != null) node.put("newPubkeyFormat", link.newPubkeyFormat) else node.putNull("newPubkeyFormat")
                        if (link.newPubkey != null) node.put("newPubkey", keysEnc.encodeToString(link.newPubkey)) else node.putNull("newPubkey")
                    } else {
                        node.putNull("newDeviceKind")
                        node.putNull("newPubkeyFormat")
                        node.putNull("newPubkey")
                    }
                    if (link.state == "wrap_complete" && link.wrappedMasterKey != null) {
                        node.put("wrappedMasterKey", keysEnc.encodeToString(link.wrappedMasterKey))
                        if (link.wrapFormat != null) node.put("wrapFormat", link.wrapFormat) else node.putNull("wrapFormat")
                    } else {
                        node.putNull("wrappedMasterKey")
                        node.putNull("wrapFormat")
                    }
                    Response(OK).header("Content-Type", "application/json").body(node.toString())
                }
            } catch (e: Exception) {
                Response(INTERNAL_SERVER_ERROR).body("Failed to get link status: ${e.message}")
            }
        }
    }
}

private fun wrapLinkRoute(database: Database): ContractRoute {
    val linkId = Path.uuid().of("linkId")
    return "/link" / linkId / "wrap" meta {
        summary = "Complete device link with wrapped key"
        description = "Trusted device posts wrapped master key. Atomically sets wrap_complete and creates a wrapped_keys row."
    } bindContract POST to { lId: UUID, _: String ->
        { request: Request ->
            try {
                wrapLink(lId, request, database)
            } catch (e: Exception) {
                Response(INTERNAL_SERVER_ERROR).body("Failed to complete link: ${e.message}")
            }
        }
    }
}

private fun wrapLink(linkId: UUID, request: Request, database: Database): Response {
    val link = database.getPendingDeviceLink(linkId)
        ?: return Response(NOT_FOUND)
    if (Instant.now().isAfter(link.expiresAt))
        return Response(GONE).header("Content-Type", "application/json").body("""{"error":"link has expired"}""")
    if (link.state != "device_registered")
        return Response(CONFLICT).header("Content-Type", "application/json")
            .body("""{"error":"link is not in device_registered state"}""")

    val node = keysMapper.readTree(request.bodyString())
    val wrappedMasterKeyB64 = node?.get("wrappedMasterKey")?.asText()
    val wrapFormat = node?.get("wrapFormat")?.asText()

    if (wrappedMasterKeyB64.isNullOrBlank() || wrapFormat.isNullOrBlank())
        return Response(BAD_REQUEST).body("Missing wrappedMasterKey or wrapFormat")

    val wrappedMasterKey = runCatching { keysDec.decode(wrappedMasterKeyB64) }.getOrNull()
        ?: return Response(BAD_REQUEST).body("wrappedMasterKey is not valid Base64")

    val userId = request.authUserId()
    database.completeDeviceLink(
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
    val newDevice = database.getWrappedKeyByDeviceIdForUser(link.newDeviceId, userId)!!
    return Response(CREATED).header("Content-Type", "application/json").body(newDevice.toJson())
}

// ---- JSON serialization ----------------------------------------------------

internal fun WrappedKeyRecord.toJson(): String {
    val node = JsonNodeFactory.instance.objectNode()
    node.put("id", id.toString())
    node.put("deviceId", deviceId)
    node.put("deviceLabel", deviceLabel)
    node.put("deviceKind", deviceKind)
    node.put("pubkeyFormat", pubkeyFormat)
    node.put("pubkey", keysEnc.encodeToString(pubkey))
    node.put("wrappedMasterKey", keysEnc.encodeToString(wrappedMasterKey))
    node.put("wrapFormat", wrapFormat)
    node.put("createdAt", createdAt.toString())
    node.put("lastUsedAt", lastUsedAt.toString())
    if (retiredAt != null) node.put("retiredAt", retiredAt.toString()) else node.putNull("retiredAt")
    return node.toString()
}

internal fun RecoveryPassphraseRecord.toJson(): String {
    val node = JsonNodeFactory.instance.objectNode()
    node.put("wrappedMasterKey", keysEnc.encodeToString(wrappedMasterKey))
    node.put("wrapFormat", wrapFormat)
    node.set<com.fasterxml.jackson.databind.JsonNode>("argon2Params", keysMapper.readTree(argon2Params))
    node.put("salt", keysEnc.encodeToString(salt))
    node.put("createdAt", createdAt.toString())
    node.put("updatedAt", updatedAt.toString())
    return node.toString()
}
