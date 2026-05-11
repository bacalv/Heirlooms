package digital.heirlooms.server

import org.http4k.core.Filter
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.UNAUTHORIZED
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.UUID

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

fun sessionAuthFilter(database: Database): Filter = Filter { next ->
    { request ->
        if (isUnauthenticated(request.uri.path)) {
            next(request)
        } else {
            val raw = request.header("X-Api-Key")
            val session = raw?.let { token ->
                val bytes = runCatching { Base64.getUrlDecoder().decode(token) }
                    .getOrElse { runCatching { Base64.getDecoder().decode(token) }.getOrNull() }
                bytes?.let {
                    val hash = MessageDigest.getInstance("SHA-256").digest(it)
                    database.findSessionByTokenHash(hash)
                }
            }
            if (session == null || session.expiresAt.isBefore(Instant.now())) {
                Response(UNAUTHORIZED).body("Unauthorized")
            } else {
                database.refreshSession(session.id)
                next(request.header("X-Auth-User-Id", session.userId.toString())
                              .header("X-Auth-Device-Kind", session.deviceKind))
            }
        }
    }
}

fun Request.authUserId(): UUID =
    header("X-Auth-User-Id")?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        ?: FOUNDING_USER_ID
