package digital.heirlooms.server.routes.capsule

import com.fasterxml.jackson.databind.ObjectMapper
import digital.heirlooms.server.filters.authUserId
import digital.heirlooms.server.service.capsule.SealCapsuleService
import org.http4k.contract.ContractRoute
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.core.Method.POST
import org.http4k.core.Method.PUT
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.CONFLICT
import org.http4k.core.Status.Companion.FORBIDDEN
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.UNPROCESSABLE_ENTITY
import org.http4k.lens.Path
import org.http4k.lens.uuid
import java.time.format.DateTimeFormatter
import java.util.UUID

private val sealMapper = ObjectMapper()

/**
 * Routes for PUT /api/capsules/:id/seal and POST /api/capsules/:id/seal.
 * Both verbs are required: PUT is the canonical M11 path; POST is required for
 * backwards compatibility with frozen app clients (ARCH-015).
 *
 * LOGGING PROHIBITION:
 * The tlock.dek_tlock and tlock.wrapped_key fields from the request body MUST NOT
 * be logged by this handler or any middleware. Only capsule ID, caller user ID,
 * HTTP status, and latency are logged.
 */
fun sealCapsuleRoutes(sealService: SealCapsuleService): List<ContractRoute> = listOf(
    sealCapsuleRouteVerb(sealService, isPut = true),
    sealCapsuleRouteVerb(sealService, isPut = false),
)

private fun sealCapsuleRouteVerb(sealService: SealCapsuleService, isPut: Boolean): ContractRoute {
    val id = Path.uuid().of("id")
    val method = if (isPut) PUT else POST
    return "/capsules" / id / "seal" meta {
        summary = if (isPut) "Seal a capsule (PUT)" else "Seal a capsule (POST — backwards compat)"
        description = """
            Seals a capsule: validates all recipient keys, tlock fields (if present),
            Shamir config (if present), and the multi-path fallback rule; then writes
            all crypto columns atomically and advances capsule shape to 'sealed'.
            Both PUT and POST verbs are accepted.
        """.trimIndent()
    } bindContract method to { capsuleId: UUID, _: String ->
        { request: Request -> sealCapsuleHandler(sealService, capsuleId, request) }
    }
}

private fun sealCapsuleHandler(
    sealService: SealCapsuleService,
    capsuleId: UUID,
    request: Request,
): Response {
    val userId = request.authUserId()

    // Parse body — tolerate malformed or empty JSON by using an empty node.
    // We always proceed to the service call so that ownership checks (steps [1]-[2])
    // fire before validation errors (step [3]+). This ensures the correct HTTP 404
    // when a caller tries to seal a capsule they don't own.
    val bodyStr = request.bodyString()
    val node = if (bodyStr.isBlank()) null else {
        try { sealMapper.readTree(bodyStr) } catch (_: Exception) { null }
    }

    // Parse recipient_keys — empty list if missing or malformed (service returns 422 at step [3])
    val recipientKeysNode = node?.get("recipient_keys")
    val recipientKeys: List<SealCapsuleService.RecipientKeyRequest> = if (recipientKeysNode != null && recipientKeysNode.isArray) {
        try {
            (0 until recipientKeysNode.size()).map { i ->
                val rk = recipientKeysNode[i]
                SealCapsuleService.RecipientKeyRequest(
                    connectionId      = UUID.fromString(rk.get("connection_id")?.asText() ?: ""),
                    wrappedCapsuleKey = rk.get("wrapped_capsule_key")?.asText() ?: "",
                    capsuleKeyFormat  = rk.get("capsule_key_format")?.asText()
                        ?: "capsule-ecdh-aes256gcm-v1",
                    wrappedBlindingMask = rk.get("wrapped_blinding_mask")?.asText()
                        ?.takeIf { it.isNotEmpty() },
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    } else {
        emptyList()
    }

    // Parse optional tlock block
    val tlockNode = node?.get("tlock")
    val tlockReq = if (tlockNode != null && !tlockNode.isNull) {
        try {
            SealCapsuleService.TlockRequest(
                round           = tlockNode.get("round")?.asLong() ?: 0L,
                chainId         = tlockNode.get("chain_id")?.asText() ?: "",
                wrappedKey      = tlockNode.get("wrapped_key")?.asText() ?: "",
                dekTlock        = tlockNode.get("dek_tlock")?.asText() ?: "",
                tlockKeyDigest  = tlockNode.get("tlock_key_digest")?.asText() ?: "",
            )
        } catch (_: Exception) {
            return Response(UNPROCESSABLE_ENTITY)
                .header("Content-Type", "application/json")
                .body("""{"error":"invalid_tlock_fields"}""")
        }
    } else null

    // Parse optional shamir block
    val shamirNode = node?.get("shamir")
    val shamirReq = if (shamirNode != null && !shamirNode.isNull) {
        try {
            SealCapsuleService.ShamirRequest(
                threshold   = shamirNode.get("threshold")?.asInt() ?: 0,
                totalShares = shamirNode.get("total_shares")?.asInt() ?: 0,
            )
        } catch (_: Exception) {
            return Response(UNPROCESSABLE_ENTITY)
                .header("Content-Type", "application/json")
                .body("""{"error":"invalid_shamir_fields"}""")
        }
    } else null

    val req = SealCapsuleService.SealRequest(
        recipientKeys = recipientKeys,
        tlock         = tlockReq,
        shamir        = shamirReq,
    )

    return when (val result = sealService.sealCapsule(capsuleId, userId, req)) {
        is SealCapsuleService.SealResult.Success -> {
            val sealedAtStr = DateTimeFormatter.ISO_INSTANT.format(result.sealedAt)
            Response(OK)
                .header("Content-Type", "application/json")
                .body(
                    """{"capsule_id":"${result.capsuleId}","shape":"sealed","state":"sealed","sealed_at":"$sealedAtStr"}"""
                )
        }
        is SealCapsuleService.SealResult.Forbidden ->
            Response(FORBIDDEN)
                .header("Content-Type", "application/json")
                .body("""{"error":"forbidden"}""")
        is SealCapsuleService.SealResult.NotFound ->
            Response(NOT_FOUND)
                .header("Content-Type", "application/json")
                .body("""{"error":"not_found"}""")
        is SealCapsuleService.SealResult.CapsuleNotSealable ->
            Response(CONFLICT)
                .header("Content-Type", "application/json")
                .body("""{"error":"capsule_not_sealable","detail":"${result.detail}"}""")
        is SealCapsuleService.SealResult.ValidationError -> {
            val detailPart = if (result.detail != null)
                ""","detail":"${result.detail.replace("\"", "\\\"")}""""
            else ""
            Response(UNPROCESSABLE_ENTITY)
                .header("Content-Type", "application/json")
                .body("""{"error":"${result.error}"$detailPart}""")
        }
        is SealCapsuleService.SealResult.InternalError ->
            Response(INTERNAL_SERVER_ERROR)
                .header("Content-Type", "application/json")
                .body("""{"error":"internal_error"}""")
    }
}
