package digital.heirlooms.server.filters

import digital.heirlooms.server.FOUNDING_USER_ID
import digital.heirlooms.server.repository.auth.AuthRepository
import org.http4k.core.Filter
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.UNAUTHORIZED
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.UUID

private val authFilterLogger = LoggerFactory.getLogger("digital.heirlooms.server.filters.SessionAuth")

private val UNAUTHENTICATED_PATHS = setOf(
    "/api/auth/challenge",
    "/api/auth/login",
    "/api/auth/setup-existing",
    "/api/auth/register",
    "/api/auth/pairing/qr",
)

private fun isUnauthenticated(path: String): Boolean =
    path == "/health" ||
    path.startsWith("/docs") ||
    path.startsWith("/api/auth/pairing/status") ||
    path in UNAUTHENTICATED_PATHS

fun sessionAuthFilter(authRepository: AuthRepository, staticApiKey: String = ""): Filter = Filter { next ->
    { request ->
        if (isUnauthenticated(request.uri.path)) {
            next(request)
        } else {
            val raw = request.header("X-Api-Key")
            // Static API key bypass: used for integration tests and local development.
            // Only active when the server is configured with a non-empty API_KEY.
            if (staticApiKey.isNotEmpty() && raw == staticApiKey) {
                next(request.header("X-Auth-User-Id", FOUNDING_USER_ID.toString())
                             .header("X-Auth-Device-Kind", "static"))
            } else {
                val session = raw?.let { token ->
                    val bytes = runCatching { Base64.getUrlDecoder().decode(token) }
                        .getOrElse { runCatching { Base64.getDecoder().decode(token) }.getOrNull() }
                    bytes?.let {
                        val hash = MessageDigest.getInstance("SHA-256").digest(it)
                        authRepository.findSessionByTokenHash(hash)
                    }
                }
                if (session == null || session.expiresAt.isBefore(Instant.now())) {
                    Response(UNAUTHORIZED).body("Unauthorized")
                } else {
                    authRepository.refreshSession(session.id)
                    next(request.header("X-Auth-User-Id", session.userId.toString())
                                  .header("X-Auth-Device-Kind", session.deviceKind))
                }
            }
        }
    }
}

/**
 * Returns the authenticated user's UUID from the internal X-Auth-User-Id header that
 * [sessionAuthFilter] stamps onto every verified request.
 *
 * The header is always set by the filter for any authenticated path. If the header is
 * absent or malformed it means the request bypassed the filter (a configuration bug in
 * tests or a new unauthenticated path that accidentally calls this). We log a security
 * warning and fall back to FOUNDING_USER_ID rather than throwing, so existing handler
 * tests that build [buildApp] directly (without the filter) continue to work.
 *
 * Note: the actual security gate is [sessionAuthFilter], which returns 401 before
 * reaching any route handler when authentication is required. This fallback is a
 * convenience for test infrastructure only.
 */
fun Request.authUserId(): UUID {
    val raw = header("X-Auth-User-Id")
    val parsed = raw?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    if (parsed == null) {
        authFilterLogger.warn(
            "SECURITY: X-Auth-User-Id header missing or invalid on request to '{}' — falling back to FOUNDING_USER_ID. " +
            "This must not occur in production; ensure all authenticated routes pass through sessionAuthFilter.",
            uri.path
        )
        return FOUNDING_USER_ID
    }
    return parsed
}
