package digital.heirlooms.server.routes.capsule

import digital.heirlooms.server.filters.authUserId
import digital.heirlooms.server.service.capsule.TlockKeyService
import org.http4k.contract.ContractRoute
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.ACCEPTED
import org.http4k.core.Status.Companion.FORBIDDEN
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.SERVICE_UNAVAILABLE
import org.http4k.lens.Path
import org.http4k.lens.uuid
import java.util.UUID

/**
 * GET /api/capsules/:id/tlock-key
 *
 * Returns DEK_tlock to an authenticated recipient after both time gates are open.
 * Gate logic follows ARCH-010 §5.3 exactly (7 steps).
 *
 * LOGGING PROHIBITION: The response body (dek_tlock) MUST NEVER appear in any log,
 * trace, or error report. Only capsule ID, caller user ID, HTTP status, and latency
 * are logged. This handler adds no additional logging beyond what TlockKeyService logs.
 */
fun tlockKeyRoute(tlockKeyService: TlockKeyService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/capsules" / id / "tlock-key" meta {
        summary = "Retrieve DEK_tlock for a tlock capsule"
        description = """
            Returns the time-lock key (DEK_tlock) after both gates are open:
            (1) now() >= unlock_at and (2) the drand round has published.
            Caller must be an authenticated recipient of this capsule.
        """.trimIndent()
    } bindContract GET to { capsuleId: UUID, _: String ->
        { request: Request -> tlockKeyHandler(tlockKeyService, capsuleId, request) }
    }
}

private fun tlockKeyHandler(
    tlockKeyService: TlockKeyService,
    capsuleId: UUID,
    request: Request,
): Response {
    val callerUserId = request.authUserId()

    return when (val result = tlockKeyService.getTlockKey(capsuleId, callerUserId)) {
        is TlockKeyService.TlockKeyResult.Success -> {
            // SECURITY: response body contains dek_tlock — do NOT log it
            Response(OK)
                .header("Content-Type", "application/json")
                .body(
                    // Build manually to avoid accidental serializer logging
                    """{"dek_tlock":"${result.dekTlockB64}","chain_id":"${result.chainId}","round":${result.round}}"""
                )
        }

        TlockKeyService.TlockKeyResult.NotARecipient ->
            Response(FORBIDDEN)
                .header("Content-Type", "application/json")
                .body("""{"error":"not_a_recipient"}""")

        TlockKeyService.TlockKeyResult.NotFound ->
            Response(NOT_FOUND)
                .header("Content-Type", "application/json")
                .body("""{"error":"not_found"}""")

        TlockKeyService.TlockKeyResult.TlockNotEnabled ->
            Response(SERVICE_UNAVAILABLE)
                .header("Content-Type", "application/json")
                .body("""{"error":"tlock_not_enabled"}""")

        is TlockKeyService.TlockKeyResult.GateNotOpen ->
            Response(ACCEPTED)
                .header("Content-Type", "application/json")
                .body(
                    """{"error":"tlock_gate_not_open","detail":"${result.detail}","retry_after_seconds":${result.retryAfterSeconds}}"""
                )

        TlockKeyService.TlockKeyResult.TamperDetected ->
            Response(INTERNAL_SERVER_ERROR)
                .header("Content-Type", "application/json")
                .body("""{"error":"internal_error"}""")
    }
}
