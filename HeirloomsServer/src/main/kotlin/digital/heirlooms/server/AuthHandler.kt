package digital.heirlooms.server

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import org.http4k.contract.ContractRoute
import org.http4k.contract.meta
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.CONFLICT
import org.http4k.core.Status.Companion.CREATED
import org.http4k.core.Status.Companion.GONE
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.NO_CONTENT
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.UNAUTHORIZED
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private val authMapper = ObjectMapper()
private val urlEnc: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
private val urlDec: Base64.Decoder = Base64.getUrlDecoder()
private val stdDec: Base64.Decoder = Base64.getDecoder()
private val authRng = SecureRandom()

private fun sha256(bytes: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(bytes)

private fun generateRawToken(): ByteArray = ByteArray(32).also { authRng.nextBytes(it) }

private fun issueToken(): Triple<String, ByteArray, ByteArray> {
    val raw = generateRawToken()
    val token = urlEnc.encodeToString(raw)
    val hash = sha256(raw)
    return Triple(token, raw, hash)
}

private fun fakeSalt(username: String, serverSecret: ByteArray): ByteArray {
    val secret = if (serverSecret.isEmpty()) ByteArray(32) else serverSecret
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret, "HmacSHA256"))
    return mac.doFinal(username.toByteArray(Charsets.UTF_8)).copyOf(16)
}

private fun decodeBase64Url(value: String?): ByteArray? =
    if (value.isNullOrBlank()) null
    else runCatching { urlDec.decode(value) }.getOrNull()
        ?: runCatching { stdDec.decode(value) }.getOrNull()

private fun resolveSession(request: Request, database: Database): UserSessionRecord? {
    val raw = request.header("X-Api-Key") ?: return null
    val rawBytes = runCatching { urlDec.decode(raw) }.getOrElse {
        runCatching { stdDec.decode(raw) }.getOrNull()
    } ?: return null
    val hash = sha256(rawBytes)
    val session = database.findSessionByTokenHash(hash) ?: return null
    if (session.expiresAt.isBefore(Instant.now())) return null
    return session
}

private fun sessionTokenJson(token: String, userId: UUID, expiresAt: Instant): String {
    val node = JsonNodeFactory.instance.objectNode()
    node.put("session_token", token)
    node.put("user_id", userId.toString())
    node.put("expires_at", expiresAt.toString())
    return node.toString()
}

private fun generateNumericCode(): String =
    (10_000_000 + authRng.nextInt(90_000_000)).toString()

fun authRoutes(database: Database, serverSecret: ByteArray): List<ContractRoute> = listOf(
    challengeRoute(database, serverSecret),
    loginRoute(database),
    setupExistingRoute(database),
    logoutRoute(database),
    getInviteRoute(database),
    registerRoute(database),
    pairingInitiateRoute(database),
    pairingQrRoute(database),
    pairingCompleteRoute(database),
    pairingStatusRoute(database),
)

// ---- POST /challenge -------------------------------------------------------

private fun challengeRoute(database: Database, serverSecret: ByteArray): ContractRoute =
    "/challenge" meta {
        summary = "Fetch auth salt for a username"
    } bindContract POST to { request: Request ->
        try {
            val node = authMapper.readTree(request.bodyString())
            val username = node?.get("username")?.asText()
            if (username.isNullOrBlank())
                return@to Response(BAD_REQUEST).body("Missing username")

            val user = database.findUserByUsername(username)
            val salt = if (user?.authSalt != null) {
                user.authSalt
            } else {
                fakeSalt(username, serverSecret)
            }
            Response(OK).header("Content-Type", "application/json")
                .body("""{"auth_salt":"${urlEnc.encodeToString(salt)}"}""")
        } catch (e: Exception) {
            Response(INTERNAL_SERVER_ERROR).body("challenge failed: ${e.message}")
        }
    }

// ---- POST /login -----------------------------------------------------------

private fun loginRoute(database: Database): ContractRoute =
    "/login" meta {
        summary = "Authenticate with auth_key and receive a session token"
    } bindContract POST to { request: Request ->
        try {
            val node = authMapper.readTree(request.bodyString())
            val username = node?.get("username")?.asText()
            val authKeyB64 = node?.get("auth_key")?.asText()

            if (username.isNullOrBlank() || authKeyB64.isNullOrBlank())
                return@to Response(BAD_REQUEST).body("Missing username or auth_key")

            val authKey = decodeBase64Url(authKeyB64)
                ?: return@to Response(BAD_REQUEST).body("auth_key is not valid Base64")

            val user = database.findUserByUsername(username)
            if (user == null || user.authVerifier == null ||
                !sha256(authKey).contentEquals(user.authVerifier)
            ) {
                return@to Response(UNAUTHORIZED)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Invalid credentials"}""")
            }

            val (token, _, hash) = issueToken()
            val session = database.createSession(user.id, hash, "android")
            Response(OK).header("Content-Type", "application/json")
                .body(sessionTokenJson(token, user.id, session.expiresAt))
        } catch (e: Exception) {
            Response(INTERNAL_SERVER_ERROR).body("login failed: ${e.message}")
        }
    }

// ---- POST /setup-existing --------------------------------------------------

private fun setupExistingRoute(database: Database): ContractRoute =
    "/setup-existing" meta {
        summary = "One-time passphrase setup for the founding user"
    } bindContract POST to { request: Request ->
        try {
            val node = authMapper.readTree(request.bodyString())
            val username = node?.get("username")?.asText()
            val deviceId = node?.get("device_id")?.asText()
            val authSaltB64 = node?.get("auth_salt")?.asText()
            val authVerifierB64 = node?.get("auth_verifier")?.asText()

            if (username.isNullOrBlank() || deviceId.isNullOrBlank() ||
                authSaltB64.isNullOrBlank() || authVerifierB64.isNullOrBlank()
            ) return@to Response(BAD_REQUEST).body("Missing required fields")

            val authSalt = decodeBase64Url(authSaltB64)
                ?: return@to Response(BAD_REQUEST).body("auth_salt is not valid Base64")
            val authVerifier = decodeBase64Url(authVerifierB64)
                ?: return@to Response(BAD_REQUEST).body("auth_verifier is not valid Base64")

            val user = database.findUserByUsername(username)
                ?: return@to Response(UNAUTHORIZED)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Invalid credentials"}""")

            if (user.authVerifier != null) {
                return@to Response(CONFLICT)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Passphrase already set"}""")
            }

            val keyRecord = database.getWrappedKeyByDeviceIdAndUser(deviceId, user.id)
            if (keyRecord == null) {
                return@to Response(UNAUTHORIZED)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Invalid credentials"}""")
            }

            database.setUserAuth(user.id, authVerifier, authSalt)

            val (token, _, hash) = issueToken()
            val session = database.createSession(user.id, hash, keyRecord.deviceKind)
            Response(OK).header("Content-Type", "application/json")
                .body(sessionTokenJson(token, user.id, session.expiresAt))
        } catch (e: Exception) {
            Response(INTERNAL_SERVER_ERROR).body("setup-existing failed: ${e.message}")
        }
    }

// ---- POST /logout ----------------------------------------------------------

private fun logoutRoute(database: Database): ContractRoute =
    "/logout" meta {
        summary = "Invalidate the calling session"
    } bindContract POST to { request: Request ->
        try {
            val session = resolveSession(request, database)
                ?: return@to Response(UNAUTHORIZED).body("Unauthorized")
            database.deleteSession(session.id)
            Response(NO_CONTENT)
        } catch (e: Exception) {
            Response(INTERNAL_SERVER_ERROR).body("logout failed: ${e.message}")
        }
    }

// ---- GET /invites ----------------------------------------------------------

private fun getInviteRoute(database: Database): ContractRoute =
    "/invites" meta {
        summary = "Generate an invite token for the authenticated user"
    } bindContract GET to { request: Request ->
        try {
            val session = resolveSession(request, database)
                ?: return@to Response(UNAUTHORIZED).body("Unauthorized")

            val rawToken = urlEnc.encodeToString(generateRawToken())
            val invite = database.createInvite(session.userId, rawToken)

            val node = JsonNodeFactory.instance.objectNode()
            node.put("token", invite.token)
            node.put("expires_at", invite.expiresAt.toString())
            Response(OK).header("Content-Type", "application/json").body(node.toString())
        } catch (e: Exception) {
            Response(INTERNAL_SERVER_ERROR).body("invites failed: ${e.message}")
        }
    }

// ---- POST /register --------------------------------------------------------

private fun registerRoute(database: Database): ContractRoute =
    "/register" meta {
        summary = "Redeem an invite token and create a new user account"
    } bindContract POST to { request: Request ->
        try {
            val node = authMapper.readTree(request.bodyString())
            val inviteToken = node?.get("invite_token")?.asText()
            val username = node?.get("username")?.asText()
            val displayName = node?.get("display_name")?.asText()
            val authSaltB64 = node?.get("auth_salt")?.asText()
            val authVerifierB64 = node?.get("auth_verifier")?.asText()
            val wrappedMasterKeyB64 = node?.get("wrapped_master_key")?.asText()
            val wrapFormat = node?.get("wrap_format")?.asText()
            val pubkeyFormat = node?.get("pubkey_format")?.asText()
            val pubkeyB64 = node?.get("pubkey")?.asText()
            val deviceId = node?.get("device_id")?.asText()
            val deviceLabel = node?.get("device_label")?.asText()
            val deviceKind = node?.get("device_kind")?.asText()

            if (inviteToken.isNullOrBlank() || username.isNullOrBlank() || displayName.isNullOrBlank() ||
                authSaltB64.isNullOrBlank() || authVerifierB64.isNullOrBlank() ||
                wrappedMasterKeyB64.isNullOrBlank() || wrapFormat.isNullOrBlank() ||
                pubkeyFormat.isNullOrBlank() || pubkeyB64.isNullOrBlank() ||
                deviceId.isNullOrBlank() || deviceLabel.isNullOrBlank() || deviceKind.isNullOrBlank()
            ) return@to Response(BAD_REQUEST).body("Missing required fields")

            val authSalt = decodeBase64Url(authSaltB64)
                ?: return@to Response(BAD_REQUEST).body("auth_salt is not valid Base64")
            val authVerifier = decodeBase64Url(authVerifierB64)
                ?: return@to Response(BAD_REQUEST).body("auth_verifier is not valid Base64")
            val wrappedMasterKey = decodeBase64Url(wrappedMasterKeyB64)
                ?: return@to Response(BAD_REQUEST).body("wrapped_master_key is not valid Base64")
            val pubkey = decodeBase64Url(pubkeyB64)
                ?: return@to Response(BAD_REQUEST).body("pubkey is not valid Base64")

            val invite = database.findInviteByToken(inviteToken)
            if (invite == null || invite.usedAt != null || invite.expiresAt.isBefore(Instant.now())) {
                return@to Response(GONE)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Invite token is invalid, expired, or already used"}""")
            }

            if (database.findUserByUsername(username) != null) {
                return@to Response(CONFLICT)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Username already taken"}""")
            }

            val newUser = database.createUser(
                username = username,
                displayName = displayName,
                authVerifier = authVerifier,
                authSalt = authSalt,
            )

            val now = Instant.now()
            database.insertWrappedKey(
                WrappedKeyRecord(
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
                ),
                userId = newUser.id,
            )

            database.markInviteUsed(invite.id, newUser.id)

            val (token, _, hash) = issueToken()
            val session = database.createSession(newUser.id, hash, deviceKind)

            Response(CREATED).header("Content-Type", "application/json")
                .body(sessionTokenJson(token, newUser.id, session.expiresAt))
        } catch (e: Exception) {
            Response(INTERNAL_SERVER_ERROR).body("register failed: ${e.message}")
        }
    }

// ---- POST /pairing/initiate ------------------------------------------------

private fun pairingInitiateRoute(database: Database): ContractRoute =
    "/pairing/initiate" meta {
        summary = "Generate a numeric pairing code (Android → web)"
    } bindContract POST to { request: Request ->
        try {
            val session = resolveSession(request, database)
                ?: return@to Response(UNAUTHORIZED).body("Unauthorized")

            val code = generateNumericCode()
            val link = database.createPairingLink(session.userId, code)

            val node = JsonNodeFactory.instance.objectNode()
            node.put("code", link.oneTimeCode)
            node.put("expires_at", link.expiresAt.toString())
            Response(OK).header("Content-Type", "application/json").body(node.toString())
        } catch (e: Exception) {
            Response(INTERNAL_SERVER_ERROR).body("pairing/initiate failed: ${e.message}")
        }
    }

// ---- POST /pairing/qr ------------------------------------------------------

private fun pairingQrRoute(database: Database): ContractRoute =
    "/pairing/qr" meta {
        summary = "Validate pairing code and return a session_id for QR display"
    } bindContract POST to { request: Request ->
        try {
            val node = authMapper.readTree(request.bodyString())
            val code = node?.get("code")?.asText()
            if (code.isNullOrBlank())
                return@to Response(BAD_REQUEST).body("Missing code")

            val link = database.getPendingDeviceLinkByCode(code)
            if (link == null || link.state != "initiated" || link.expiresAt.isBefore(Instant.now())) {
                return@to Response(NOT_FOUND)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Code not found or expired"}""")
            }

            val sessionId = UUID.randomUUID().toString()
            database.setPairingWebSession(link.id, sessionId)

            Response(OK).header("Content-Type", "application/json")
                .body("""{"session_id":"$sessionId"}""")
        } catch (e: Exception) {
            Response(INTERNAL_SERVER_ERROR).body("pairing/qr failed: ${e.message}")
        }
    }

// ---- POST /pairing/complete ------------------------------------------------

private fun pairingCompleteRoute(database: Database): ContractRoute =
    "/pairing/complete" meta {
        summary = "Android posts wrapped key for web session (completes pairing)"
    } bindContract POST to { request: Request ->
        try {
            val androidSession = resolveSession(request, database)
                ?: return@to Response(UNAUTHORIZED).body("Unauthorized")

            val node = authMapper.readTree(request.bodyString())
            val sessionId = node?.get("session_id")?.asText()
            val wrappedMasterKeyB64 = node?.get("wrapped_master_key")?.asText()
            val wrapFormat = node?.get("wrap_format")?.asText()

            if (sessionId.isNullOrBlank() || wrappedMasterKeyB64.isNullOrBlank() || wrapFormat.isNullOrBlank())
                return@to Response(BAD_REQUEST).body("Missing required fields")

            val wrappedMasterKey = decodeBase64Url(wrappedMasterKeyB64)
                ?: return@to Response(BAD_REQUEST).body("wrapped_master_key is not valid Base64")

            val link = database.getPendingDeviceLinkByWebSessionId(sessionId)
            if (link == null || link.expiresAt.isBefore(Instant.now())) {
                return@to Response(NOT_FOUND)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Session not found or expired"}""")
            }
            if (link.state != "device_registered") {
                return@to Response(CONFLICT)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Pairing not in expected state"}""")
            }
            if (link.userId != androidSession.userId) {
                return@to Response(NOT_FOUND)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Session not found or expired"}""")
            }

            val (rawToken, _, hash) = issueToken()
            val webSession = database.createSession(androidSession.userId, hash, "web")
            database.completePairingLink(link.id, wrappedMasterKey, wrapFormat, rawToken, webSession)

            Response(OK).header("Content-Type", "application/json").body("""{"ok":true}""")
        } catch (e: Exception) {
            Response(INTERNAL_SERVER_ERROR).body("pairing/complete failed: ${e.message}")
        }
    }

// ---- GET /pairing/status ---------------------------------------------------

private fun pairingStatusRoute(database: Database): ContractRoute =
    "/pairing/status" meta {
        summary = "Web polls for pairing completion"
    } bindContract GET to { request: Request ->
        try {
            val sessionId = request.query("session_id")
            if (sessionId.isNullOrBlank())
                return@to Response(BAD_REQUEST).body("Missing session_id")

            val link = database.getPendingDeviceLinkByWebSessionId(sessionId)
            if (link == null) {
                return@to Response(NOT_FOUND)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Session not found"}""")
            }

            if (link.state != "wrap_complete" && link.expiresAt.isBefore(Instant.now())) {
                return@to Response(NOT_FOUND)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Session expired"}""")
            }

            if (link.state != "wrap_complete") {
                return@to Response(OK)
                    .header("Content-Type", "application/json")
                    .body("""{"state":"pending"}""")
            }

            val node = JsonNodeFactory.instance.objectNode()
            node.put("state", "complete")
            node.put("session_token", link.rawSessionToken)
            node.put("wrapped_master_key", urlEnc.encodeToString(link.wrappedMasterKey))
            node.put("wrap_format", link.wrapFormat)
            node.put("expires_at", link.sessionExpiresAt?.toString())
            Response(OK).header("Content-Type", "application/json").body(node.toString())
        } catch (e: Exception) {
            Response(INTERNAL_SERVER_ERROR).body("pairing/status failed: ${e.message}")
        }
    }
