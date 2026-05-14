package digital.heirlooms.server.routes.keys

import digital.heirlooms.server.filters.authUserId
import digital.heirlooms.server.representation.keys.toJson
import digital.heirlooms.server.service.keys.KeyService
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
import java.util.Base64
import java.util.UUID

private val keysEnc = Base64.getEncoder()
private val keysMapper = ObjectMapper()

fun keysRoutes(keyService: KeyService): List<ContractRoute> = listOf(
    registerDeviceRoute(keyService),
    listDevicesRoute(keyService),
    retireDeviceRoute(keyService),
    touchDeviceRoute(keyService),
    getPassphraseRoute(keyService),
    putPassphraseRoute(keyService),
    deletePassphraseRoute(keyService),
    initiateLinkRoute(keyService),
    registerLinkRoute(keyService),
    linkStatusRoute(keyService),
    wrapLinkRoute(keyService),
)

// ---- Device CRUD -----------------------------------------------------------

private fun registerDeviceRoute(keyService: KeyService): ContractRoute =
    "/devices" meta {
        summary = "Register a device"
        description = "Stores a device public key and wrapped master key."
    } bindContract POST to { request: Request ->
        try {
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
                return@to Response(BAD_REQUEST).body("Missing required fields")
            when (val result = keyService.registerDevice(
                deviceId, deviceLabel, deviceKind, pubkeyFormat, pubkeyB64, wrappedMasterKeyB64, wrapFormat,
                request.authUserId()
            )) {
                is KeyService.RegisterResult.Created ->
                    Response(CREATED).header("Content-Type", "application/json").body(result.record.toJson())
                KeyService.RegisterResult.AlreadyRegistered ->
                    Response(CONFLICT).header("Content-Type", "application/json")
                        .body("""{"error":"deviceId already registered"}""")
                is KeyService.RegisterResult.Invalid ->
                    Response(BAD_REQUEST).header("Content-Type", "application/json")
                        .body("""{"error":"${result.message}"}""")
            }
        } catch (e: Exception) {
            Response(INTERNAL_SERVER_ERROR).body("Failed to register device: ${e.message}")
        }
    }

private fun listDevicesRoute(keyService: KeyService): ContractRoute =
    "/devices" meta {
        summary = "List active devices"
        description = "Returns all non-retired wrapped_keys rows."
    } bindContract GET to { request: Request ->
        try {
            val keys = keyService.listDevices(request.authUserId())
            val node = JsonNodeFactory.instance.arrayNode()
            keys.forEach { node.add(keysMapper.readTree(it.toJson())) }
            Response(OK).header("Content-Type", "application/json").body(node.toString())
        } catch (e: Exception) {
            Response(INTERNAL_SERVER_ERROR).body("Failed to list devices: ${e.message}")
        }
    }

private fun retireDeviceRoute(keyService: KeyService): ContractRoute {
    val deviceId = Path.of("deviceId")
    return "/devices" / deviceId meta {
        summary = "Retire a device"
        description = "Soft-retires a device by setting retired_at."
    } bindContract DELETE to { dId: String ->
        { request: Request ->
            try {
                val record = keyService.getDeviceForUser(dId, request.authUserId())
                when {
                    record == null -> Response(NOT_FOUND)
                    record.retiredAt != null ->
                        Response(CONFLICT).header("Content-Type", "application/json")
                            .body("""{"error":"device is already retired"}""")
                    else -> {
                        keyService.retireDevice(dId, request.authUserId())
                        Response(NO_CONTENT)
                    }
                }
            } catch (e: Exception) {
                Response(INTERNAL_SERVER_ERROR).body("Failed to retire device: ${e.message}")
            }
        }
    }
}

private fun touchDeviceRoute(keyService: KeyService): ContractRoute {
    val deviceId = Path.of("deviceId")
    return "/devices" / deviceId / "used" meta {
        summary = "Update last_used_at"
        description = "Sets last_used_at = NOW() for the given device."
    } bindContract PATCH to { dId: String, _: String ->
        { request: Request ->
            try {
                if (keyService.touchDevice(dId, request.authUserId())) Response(NO_CONTENT)
                else Response(NOT_FOUND)
            } catch (e: Exception) {
                Response(INTERNAL_SERVER_ERROR).body("Failed to touch device: ${e.message}")
            }
        }
    }
}

// ---- Passphrase CRUD -------------------------------------------------------

private fun getPassphraseRoute(keyService: KeyService): ContractRoute =
    "/passphrase" meta {
        summary = "Get recovery passphrase record"
        description = "Returns the passphrase-wrapped master key backup, or 404 if none exists."
    } bindContract GET to { request: Request ->
        try {
            val record = keyService.getPassphrase(request.authUserId())
            if (record == null) Response(NOT_FOUND)
            else Response(OK).header("Content-Type", "application/json").body(record.toJson())
        } catch (e: Exception) {
            Response(INTERNAL_SERVER_ERROR).body("Failed to get passphrase: ${e.message}")
        }
    }

private fun putPassphraseRoute(keyService: KeyService): ContractRoute =
    "/passphrase" meta {
        summary = "Create or replace recovery passphrase"
        description = "Upserts the passphrase-wrapped master key."
    } bindContract PUT to { request: Request ->
        try {
            val node = keysMapper.readTree(request.bodyString())
            val wrappedMasterKeyB64 = node?.get("wrappedMasterKey")?.asText()
            val wrapFormat = node?.get("wrapFormat")?.asText()
            val argon2Params = node?.get("argon2Params")
            val saltB64 = node?.get("salt")?.asText()
            if (wrappedMasterKeyB64.isNullOrBlank() || wrapFormat.isNullOrBlank() ||
                argon2Params == null || saltB64.isNullOrBlank())
                return@to Response(BAD_REQUEST).body("Missing required fields")
            when (val result = keyService.putPassphrase(
                wrappedMasterKeyB64, wrapFormat, argon2Params.toString(), saltB64, request.authUserId()
            )) {
                is KeyService.PutPassphraseResult.Updated ->
                    Response(OK).header("Content-Type", "application/json").body(result.record.toJson())
                is KeyService.PutPassphraseResult.Invalid ->
                    Response(BAD_REQUEST).body(result.message)
            }
        } catch (e: Exception) {
            Response(INTERNAL_SERVER_ERROR).body("Failed to put passphrase: ${e.message}")
        }
    }

private fun deletePassphraseRoute(keyService: KeyService): ContractRoute =
    "/passphrase" meta {
        summary = "Delete recovery passphrase"
        description = "Removes the passphrase-wrapped master key."
    } bindContract DELETE to { request: Request ->
        try {
            val deleted = keyService.deletePassphrase(request.authUserId())
            if (deleted) Response(NO_CONTENT) else Response(NOT_FOUND)
        } catch (e: Exception) {
            Response(INTERNAL_SERVER_ERROR).body("Failed to delete passphrase: ${e.message}")
        }
    }

// ---- Device link flow -------------------------------------------------------

private fun initiateLinkRoute(keyService: KeyService): ContractRoute =
    "/link/initiate" meta {
        summary = "Initiate a device link"
        description = "Trusted device starts a link session. Returns a one-time code and linkId."
    } bindContract POST to { request: Request ->
        try {
            val link = keyService.initiateLink(request.authUserId())
            Response(OK).header("Content-Type", "application/json")
                .body("""{"linkId":"${link.linkId}","code":"${link.code}"}""")
        } catch (e: Exception) {
            Response(INTERNAL_SERVER_ERROR).body("Failed to initiate link: ${e.message}")
        }
    }

private fun registerLinkRoute(keyService: KeyService): ContractRoute {
    val linkId = Path.uuid().of("linkId")
    return "/link" / linkId / "register" meta {
        summary = "Register new device on link"
        description = "New device submits its pubkey after typing the code."
    } bindContract POST to { lId: UUID, _: String ->
        { request: Request -> handleRegisterOnLink(lId, request, keyService) }
    }
}

private fun handleRegisterOnLink(linkId: UUID, request: Request, keyService: KeyService): Response {
    return try {
        val node = keysMapper.readTree(request.bodyString())
        val code = node?.get("code")?.asText()
        val deviceId = node?.get("deviceId")?.asText()
        val deviceLabel = node?.get("deviceLabel")?.asText()
        val deviceKind = node?.get("deviceKind")?.asText()
        val pubkeyFormat = node?.get("pubkeyFormat")?.asText()
        val pubkeyB64 = node?.get("pubkey")?.asText()
        if (code.isNullOrBlank() || deviceId.isNullOrBlank() || deviceLabel.isNullOrBlank() ||
            deviceKind.isNullOrBlank() || pubkeyFormat.isNullOrBlank() || pubkeyB64.isNullOrBlank())
            return Response(BAD_REQUEST).body("Missing required fields")
        when (val result = keyService.registerOnLink(linkId, code, deviceId, deviceLabel, deviceKind, pubkeyFormat, pubkeyB64)) {
            KeyService.RegisterOnLinkResult.Accepted -> Response(ACCEPTED)
            KeyService.RegisterOnLinkResult.NotFound -> Response(NOT_FOUND)
            KeyService.RegisterOnLinkResult.Expired ->
                Response(GONE).header("Content-Type", "application/json").body("""{"error":"link has expired"}""")
            KeyService.RegisterOnLinkResult.WrongState ->
                Response(CONFLICT).header("Content-Type", "application/json")
                    .body("""{"error":"link is not in initiated state"}""")
            is KeyService.RegisterOnLinkResult.Invalid ->
                Response(BAD_REQUEST).header("Content-Type", "application/json")
                    .body("""{"error":"${result.message}"}""")
        }
    } catch (e: Exception) {
        Response(INTERNAL_SERVER_ERROR).body("Failed to register device on link: ${e.message}")
    }
}

private fun linkStatusRoute(keyService: KeyService): ContractRoute {
    val linkId = Path.uuid().of("linkId")
    return "/link" / linkId / "status" meta {
        summary = "Poll link status"
        description = "Both trusted and new device poll this."
    } bindContract GET to { lId: UUID, _: String ->
        { _: Request ->
            try {
                val link = keyService.getLinkStatus(lId)
                if (link == null) {
                    Response(NOT_FOUND)
                } else if (link.state != "wrap_complete" && java.time.Instant.now().isAfter(link.expiresAt)) {
                    Response(GONE).header("Content-Type", "application/json").body("""{"error":"link has expired"}""")
                } else {
                    val node = JsonNodeFactory.instance.objectNode()
                    node.put("state", link.state)
                    if (link.state in setOf("device_registered", "wrap_complete")) {
                        if (link.newDeviceKind != null) node.put("newDeviceKind", link.newDeviceKind) else node.putNull("newDeviceKind")
                        if (link.newPubkeyFormat != null) node.put("newPubkeyFormat", link.newPubkeyFormat) else node.putNull("newPubkeyFormat")
                        if (link.newPubkey != null) node.put("newPubkey", keysEnc.encodeToString(link.newPubkey)) else node.putNull("newPubkey")
                    } else {
                        node.putNull("newDeviceKind"); node.putNull("newPubkeyFormat"); node.putNull("newPubkey")
                    }
                    if (link.state == "wrap_complete" && link.wrappedMasterKey != null) {
                        node.put("wrappedMasterKey", keysEnc.encodeToString(link.wrappedMasterKey))
                        if (link.wrapFormat != null) node.put("wrapFormat", link.wrapFormat) else node.putNull("wrapFormat")
                    } else {
                        node.putNull("wrappedMasterKey"); node.putNull("wrapFormat")
                    }
                    Response(OK).header("Content-Type", "application/json").body(node.toString())
                }
            } catch (e: Exception) {
                Response(INTERNAL_SERVER_ERROR).body("Failed to get link status: ${e.message}")
            }
        }
    }
}

private fun wrapLinkRoute(keyService: KeyService): ContractRoute {
    val linkId = Path.uuid().of("linkId")
    return "/link" / linkId / "wrap" meta {
        summary = "Complete device link with wrapped key"
        description = "Trusted device posts wrapped master key."
    } bindContract POST to { lId: UUID, _: String ->
        { request: Request -> handleWrapLink(lId, request, keyService) }
    }
}

private fun handleWrapLink(linkId: UUID, request: Request, keyService: KeyService): Response {
    return try {
        val node = keysMapper.readTree(request.bodyString())
        val wrappedMasterKeyB64 = node?.get("wrappedMasterKey")?.asText()
        val wrapFormat = node?.get("wrapFormat")?.asText()
        if (wrappedMasterKeyB64.isNullOrBlank() || wrapFormat.isNullOrBlank())
            return Response(BAD_REQUEST).body("Missing wrappedMasterKey or wrapFormat")
        when (val result = keyService.wrapLink(linkId, wrappedMasterKeyB64, wrapFormat, request.authUserId())) {
            is KeyService.WrapLinkResult.Wrapped ->
                Response(CREATED).header("Content-Type", "application/json").body(result.newDevice.toJson())
            KeyService.WrapLinkResult.NotFound -> Response(NOT_FOUND)
            KeyService.WrapLinkResult.Expired ->
                Response(GONE).header("Content-Type", "application/json").body("""{"error":"link has expired"}""")
            KeyService.WrapLinkResult.WrongState ->
                Response(CONFLICT).header("Content-Type", "application/json")
                    .body("""{"error":"link is not in device_registered state"}""")
            is KeyService.WrapLinkResult.Invalid ->
                Response(BAD_REQUEST).body(result.message)
        }
    } catch (e: Exception) {
        Response(INTERNAL_SERVER_ERROR).body("Failed to complete link: ${e.message}")
    }
}
