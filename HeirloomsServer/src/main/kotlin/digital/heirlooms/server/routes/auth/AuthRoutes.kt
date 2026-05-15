package digital.heirlooms.server.routes.auth

import digital.heirlooms.server.filters.authUserId
import digital.heirlooms.server.representation.auth.challengeResponseJson
import digital.heirlooms.server.representation.auth.inviteResponseJson
import digital.heirlooms.server.representation.auth.pairingInitiateResponseJson
import digital.heirlooms.server.representation.auth.pairingStatusCompleteJson
import digital.heirlooms.server.representation.auth.sessionTokenJson
import digital.heirlooms.server.service.auth.AuthService
import org.http4k.contract.ContractRoute
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.lens.Path
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.CONFLICT
import org.http4k.core.Status.Companion.CREATED
import org.http4k.core.Status.Companion.FORBIDDEN
import org.http4k.core.Status.Companion.GONE
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.NO_CONTENT
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.UNAUTHORIZED
import com.fasterxml.jackson.databind.ObjectMapper

private val authMapper = ObjectMapper()
private val authLogger = org.slf4j.LoggerFactory.getLogger("digital.heirlooms.server.routes.auth")

fun authRoutes(authService: AuthService): List<ContractRoute> = listOf(
    challengeRoute(authService),
    loginRoute(authService),
    setupExistingRoute(authService),
    logoutRoute(authService),
    meRoute(authService),
    getInviteRoute(authService),
    registerRoute(authService),
    inviteConnectRoute(authService),
    pairingInitiateRoute(authService),
    pairingQrRoute(authService),
    pairingCompleteRoute(authService),
    pairingStatusRoute(authService),
)

// ---- POST /challenge -------------------------------------------------------

private fun challengeRoute(authService: AuthService): ContractRoute =
    "/challenge" meta {
        summary = "Fetch auth salt for a username"
    } bindContract POST to { request: Request ->
        try {
            val node = authMapper.readTree(request.bodyString())
            val username = node?.get("username")?.asText()
            if (username.isNullOrBlank())
                return@to Response(BAD_REQUEST).body("Missing username")
            val salt = authService.getSaltForChallenge(username)
            Response(OK).header("Content-Type", "application/json")
                .body(challengeResponseJson(salt))
        } catch (e: Exception) {
            authLogger.error("challenge error", e)
            Response(INTERNAL_SERVER_ERROR).body("Internal server error")
        }
    }

// ---- POST /login -----------------------------------------------------------

private fun loginRoute(authService: AuthService): ContractRoute =
    "/login" meta {
        summary = "Authenticate with auth_key and receive a session token"
    } bindContract POST to { request: Request ->
        try {
            val node = authMapper.readTree(request.bodyString())
            val username = node?.get("username")?.asText()
            val authKeyB64 = node?.get("auth_key")?.asText()
            if (username.isNullOrBlank() || authKeyB64.isNullOrBlank())
                return@to Response(BAD_REQUEST).body("Missing username or auth_key")
            val authKey = authService.decodeBase64Url(authKeyB64)
                ?: return@to Response(BAD_REQUEST).body("auth_key is not valid Base64")
            when (val result = authService.login(username, authKey)) {
                is AuthService.LoginResult.Success ->
                    Response(OK).header("Content-Type", "application/json")
                        .body(sessionTokenJson(result.token, result.userId, result.expiresAt))
                AuthService.LoginResult.InvalidCredentials -> {
                    val clientIp = request.header("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
                        ?: request.header("X-Real-IP")?.trim() ?: "unknown"
                    authLogger.warn("Failed login attempt for username='{}' from IP={}", username, clientIp)
                    Response(UNAUTHORIZED).header("Content-Type", "application/json")
                        .body("""{"error":"Invalid credentials"}""")
                }
            }
        } catch (e: Exception) {
            authLogger.error("login error", e)
            Response(INTERNAL_SERVER_ERROR).body("Internal server error")
        }
    }

// ---- POST /setup-existing --------------------------------------------------

private fun setupExistingRoute(authService: AuthService): ContractRoute =
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
            val authSalt = authService.decodeBase64Url(authSaltB64)
                ?: return@to Response(BAD_REQUEST).body("auth_salt is not valid Base64")
            val authVerifier = authService.decodeBase64Url(authVerifierB64)
                ?: return@to Response(BAD_REQUEST).body("auth_verifier is not valid Base64")
            val wrappedMasterKeyRecoveryB64 = node?.get("wrapped_master_key_recovery")?.asText()
            val wrapFormatRecovery = node?.get("wrap_format_recovery")?.asText()
            val wrappedMasterKeyRecovery = if (!wrappedMasterKeyRecoveryB64.isNullOrBlank())
                authService.decodeBase64Url(wrappedMasterKeyRecoveryB64) else null
            when (val result = authService.setupExisting(username, deviceId, authVerifier, authSalt, wrappedMasterKeyRecovery, wrapFormatRecovery)) {
                is AuthService.SetupExistingResult.Success ->
                    Response(OK).header("Content-Type", "application/json")
                        .body(sessionTokenJson(result.token, result.userId, result.expiresAt))
                AuthService.SetupExistingResult.InvalidCredentials -> {
                    val clientIp = request.header("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
                        ?: request.header("X-Real-IP")?.trim() ?: "unknown"
                    authLogger.warn("Failed setup-existing attempt for username='{}' from IP={}", username, clientIp)
                    Response(UNAUTHORIZED).header("Content-Type", "application/json")
                        .body("""{"error":"Invalid credentials"}""")
                }
                AuthService.SetupExistingResult.PassphraseAlreadySet ->
                    Response(CONFLICT).header("Content-Type", "application/json")
                        .body("""{"error":"Passphrase already set"}""")
                AuthService.SetupExistingResult.NoDeviceKey -> {
                    val clientIp = request.header("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
                        ?: request.header("X-Real-IP")?.trim() ?: "unknown"
                    authLogger.warn("Failed setup-existing (no device key) for username='{}' from IP={}", username, clientIp)
                    Response(UNAUTHORIZED).header("Content-Type", "application/json")
                        .body("""{"error":"Invalid credentials"}""")
                }
            }
        } catch (e: Exception) {
            authLogger.error("setup-existing error", e)
            Response(INTERNAL_SERVER_ERROR).body("Internal server error")
        }
    }

// ---- POST /logout ----------------------------------------------------------

private fun logoutRoute(authService: AuthService): ContractRoute =
    "/logout" meta {
        summary = "Invalidate the calling session"
    } bindContract POST to { request: Request ->
        try {
            val session = authService.resolveSession(request.header("X-Api-Key"))
                ?: return@to Response(UNAUTHORIZED).body("Unauthorized")
            authService.logout(request.header("X-Api-Key"))
            Response(NO_CONTENT)
        } catch (e: Exception) {
            authLogger.error("logout error", e)
            Response(INTERNAL_SERVER_ERROR).body("Internal server error")
        }
    }

// ---- GET /me ---------------------------------------------------------------

private fun meRoute(authService: AuthService): ContractRoute =
    "/me" meta {
        summary = "Return the authenticated user's profile"
    } bindContract GET to { request: Request ->
        try {
            val userInfo = authService.getMe(request.authUserId())
                ?: return@to Response(UNAUTHORIZED)
            Response(OK).header("Content-Type", "application/json")
                .body("""{"user_id":"${userInfo.id}","username":"${userInfo.username}","display_name":"${userInfo.displayName}"}""")
        } catch (e: Exception) {
            authLogger.error("me error", e)
            Response(INTERNAL_SERVER_ERROR).body("Internal server error")
        }
    }

// ---- GET /invites ----------------------------------------------------------

private fun getInviteRoute(authService: AuthService): ContractRoute =
    "/invites" meta {
        summary = "Generate an invite token for the authenticated user"
    } bindContract GET to { request: Request ->
        try {
            val invite = authService.generateInvite(request.authUserId())
            Response(OK).header("Content-Type", "application/json")
                .body(inviteResponseJson(invite.token, invite.expiresAt))
        } catch (e: Exception) {
            authLogger.error("invites error", e)
            Response(INTERNAL_SERVER_ERROR).body("Internal server error")
        }
    }

// ---- POST /register --------------------------------------------------------

private fun registerRoute(authService: AuthService): ContractRoute =
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

            val authSalt = authService.decodeBase64Url(authSaltB64)
                ?: return@to Response(BAD_REQUEST).body("auth_salt is not valid Base64")
            val authVerifier = authService.decodeBase64Url(authVerifierB64)
                ?: return@to Response(BAD_REQUEST).body("auth_verifier is not valid Base64")
            val wrappedMasterKey = authService.decodeBase64Url(wrappedMasterKeyB64)
                ?: return@to Response(BAD_REQUEST).body("wrapped_master_key is not valid Base64")
            val pubkey = authService.decodeBase64Url(pubkeyB64)
                ?: return@to Response(BAD_REQUEST).body("pubkey is not valid Base64")

            val wrappedMasterKeyRecoveryB64 = node?.get("wrapped_master_key_recovery")?.asText()
            val wrapFormatRecovery = node?.get("wrap_format_recovery")?.asText()
            val wrappedMasterKeyRecovery = if (!wrappedMasterKeyRecoveryB64.isNullOrBlank())
                authService.decodeBase64Url(wrappedMasterKeyRecoveryB64) else null

            when (val result = authService.register(
                inviteToken, username, displayName, authVerifier, authSalt,
                wrappedMasterKey, wrapFormat, pubkeyFormat, pubkey,
                deviceId, deviceLabel, deviceKind,
                wrappedMasterKeyRecovery, wrapFormatRecovery,
            )) {
                is AuthService.RegisterResult.Success ->
                    Response(CREATED).header("Content-Type", "application/json")
                        .body(sessionTokenJson(result.token, result.userId, result.expiresAt))
                AuthService.RegisterResult.InvalidInvite ->
                    Response(GONE).header("Content-Type", "application/json")
                        .body("""{"error":"Invite token is invalid, expired, or already used"}""")
                AuthService.RegisterResult.UsernameTaken ->
                    Response(CONFLICT).header("Content-Type", "application/json")
                        .body("""{"error":"Username already taken"}""")
            }
        } catch (e: Exception) {
            authLogger.error("register error", e)
            Response(INTERNAL_SERVER_ERROR).body("Internal server error")
        }
    }

// ---- POST /invites/{token}/connect -----------------------------------------

private fun inviteConnectRoute(authService: AuthService): ContractRoute {
    val tokenPath = Path.of("token")
    return "/invites" / tokenPath / "connect" meta {
        summary = "Redeem an invite token as a friend connection for an already-authenticated user"
    } bindContract POST to { token: String, _: String ->
        { request: Request -> handleInviteConnect(token, request, authService) }
    }
}

private fun handleInviteConnect(token: String, request: Request, authService: AuthService): Response {
    return try {
        val requesterId = request.authUserId()
        when (val result = authService.connectViaInvite(requesterId, token)) {
            is AuthService.ConnectViaInviteResult.Success ->
                Response(OK).header("Content-Type", "application/json")
                    .body("""{"inviter_display_name":"${result.inviterDisplayName}"}""")
            AuthService.ConnectViaInviteResult.InvalidInvite ->
                Response(GONE).header("Content-Type", "application/json")
                    .body("""{"error":"Invite token is invalid, expired, or already used"}""")
            AuthService.ConnectViaInviteResult.AlreadyFriends ->
                Response(CONFLICT).header("Content-Type", "application/json")
                    .body("""{"error":"Already friends"}""")
            AuthService.ConnectViaInviteResult.SelfConnect ->
                Response(FORBIDDEN).header("Content-Type", "application/json")
                    .body("""{"error":"Cannot connect with yourself"}""")
        }
    } catch (e: Exception) {
        authLogger.error("invites/connect error", e)
        Response(INTERNAL_SERVER_ERROR).body("Internal server error")
    }
}

// ---- POST /pairing/initiate ------------------------------------------------

private fun pairingInitiateRoute(authService: AuthService): ContractRoute =
    "/pairing/initiate" meta {
        summary = "Generate a numeric pairing code (Android → web)"
    } bindContract POST to { request: Request ->
        try {
            val link = authService.initiatePairing(request.authUserId())
            Response(OK).header("Content-Type", "application/json")
                .body(pairingInitiateResponseJson(link.oneTimeCode, link.expiresAt))
        } catch (e: Exception) {
            authLogger.error("pairing/initiate error", e)
            Response(INTERNAL_SERVER_ERROR).body("Internal server error")
        }
    }

// ---- POST /pairing/qr ------------------------------------------------------

private fun pairingQrRoute(authService: AuthService): ContractRoute =
    "/pairing/qr" meta {
        summary = "Validate pairing code and return a session_id for QR display"
    } bindContract POST to { request: Request ->
        try {
            val node = authMapper.readTree(request.bodyString())
            val code = node?.get("code")?.asText()
            if (code.isNullOrBlank())
                return@to Response(BAD_REQUEST).body("Missing code")
            when (val result = authService.pairingQr(code)) {
                is AuthService.PairingQrResult.Ok ->
                    Response(OK).header("Content-Type", "application/json")
                        .body("""{"session_id":"${result.sessionId}"}""")
                AuthService.PairingQrResult.NotFound ->
                    Response(NOT_FOUND).header("Content-Type", "application/json")
                        .body("""{"error":"Code not found or expired"}""")
            }
        } catch (e: Exception) {
            authLogger.error("pairing/qr error", e)
            Response(INTERNAL_SERVER_ERROR).body("Internal server error")
        }
    }

// ---- POST /pairing/complete ------------------------------------------------

private fun pairingCompleteRoute(authService: AuthService): ContractRoute =
    "/pairing/complete" meta {
        summary = "Android posts wrapped key for web session (completes pairing)"
    } bindContract POST to { request: Request ->
        try {
            val node = authMapper.readTree(request.bodyString())
            val sessionId = node?.get("session_id")?.asText()
            val wrappedMasterKeyB64 = node?.get("wrapped_master_key")?.asText()
            val wrapFormat = node?.get("wrap_format")?.asText()
            if (sessionId.isNullOrBlank() || wrappedMasterKeyB64.isNullOrBlank() || wrapFormat.isNullOrBlank())
                return@to Response(BAD_REQUEST).body("Missing required fields")
            val wrappedMasterKey = authService.decodeBase64Url(wrappedMasterKeyB64)
                ?: return@to Response(BAD_REQUEST).body("wrapped_master_key is not valid Base64")
            when (authService.completePairing(request.authUserId(), sessionId, wrappedMasterKey, wrapFormat)) {
                AuthService.PairingCompleteResult.Ok ->
                    Response(OK).header("Content-Type", "application/json").body("""{"ok":true}""")
                AuthService.PairingCompleteResult.Unauthorized ->
                    Response(UNAUTHORIZED).body("Unauthorized")
                AuthService.PairingCompleteResult.NotFound ->
                    Response(NOT_FOUND).header("Content-Type", "application/json")
                        .body("""{"error":"Session not found or expired"}""")
                AuthService.PairingCompleteResult.WrongState ->
                    Response(CONFLICT).header("Content-Type", "application/json")
                        .body("""{"error":"Pairing not in expected state"}""")
            }
        } catch (e: Exception) {
            authLogger.error("pairing/complete error", e)
            Response(INTERNAL_SERVER_ERROR).body("Internal server error")
        }
    }

// ---- GET /pairing/status ---------------------------------------------------

private fun pairingStatusRoute(authService: AuthService): ContractRoute =
    "/pairing/status" meta {
        summary = "Web polls for pairing completion"
    } bindContract GET to { request: Request ->
        try {
            val sessionId = request.query("session_id")
            if (sessionId.isNullOrBlank())
                return@to Response(BAD_REQUEST).body("Missing session_id")
            when (val result = authService.pairingStatus(sessionId)) {
                AuthService.PairingStatusResult.Pending ->
                    Response(OK).header("Content-Type", "application/json").body("""{"state":"pending"}""")
                is AuthService.PairingStatusResult.Complete -> {
                    Response(OK).header("Content-Type", "application/json")
                        .body(pairingStatusCompleteJson(result.sessionToken, result.wrappedMasterKey, result.wrapFormat, result.expiresAt))
                }
                AuthService.PairingStatusResult.NotFound ->
                    Response(NOT_FOUND).header("Content-Type", "application/json")
                        .body("""{"error":"Session not found"}""")
                AuthService.PairingStatusResult.Expired ->
                    Response(NOT_FOUND).header("Content-Type", "application/json")
                        .body("""{"error":"Session expired"}""")
            }
        } catch (e: Exception) {
            authLogger.error("pairing/status error", e)
            Response(INTERNAL_SERVER_ERROR).body("Internal server error")
        }
    }
