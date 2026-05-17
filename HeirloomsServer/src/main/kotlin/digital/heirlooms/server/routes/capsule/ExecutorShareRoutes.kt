package digital.heirlooms.server.routes.capsule

import com.fasterxml.jackson.databind.ObjectMapper
import digital.heirlooms.server.filters.authUserId
import digital.heirlooms.server.service.capsule.ExecutorShareService
import org.http4k.contract.ContractRoute
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.FORBIDDEN
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.UNPROCESSABLE_ENTITY
import org.http4k.lens.Path
import org.http4k.lens.uuid
import java.util.UUID

private val shareMapper = ObjectMapper()

/**
 * Endpoints 13–15: Executor share distribution (DEV-004 / M11 Wave 4).
 *
 * 13. POST   /capsules/:id/executor-shares          — owner uploads all Shamir shares
 * 14. GET    /capsules/:id/executor-shares/mine     — executor fetches own share
 * 15. GET    /capsules/:id/executor-shares/collect  — author collects all shares (M11)
 */
fun executorShareRoutes(svc: ExecutorShareService): List<ContractRoute> = listOf(
    submitSharesRoute(svc),
    getMineShareRoute(svc),
    collectSharesRoute(svc),
)

// ─── Endpoint 13: POST /capsules/:id/executor-shares ─────────────────────────

private fun submitSharesRoute(svc: ExecutorShareService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/capsules" / id / "executor-shares" meta {
        summary = "Upload Shamir shares"
        description = "Owner uploads all Shamir shares for a sealed Shamir capsule. 8-step validation (ARCH-010 §5.2)."
    } bindContract POST to { capsuleId: UUID, _: String ->
        submitSharesHandler(svc, capsuleId)
    }
}

private fun submitSharesHandler(svc: ExecutorShareService, capsuleId: UUID): HttpHandler = handler@{ request ->
    val node = try { shareMapper.readTree(request.bodyString()) } catch (_: Exception) { null }
        ?: return@handler Response(BAD_REQUEST).body("Malformed JSON")

    val sharesNode = node.get("shares")
        ?: return@handler Response(BAD_REQUEST).body("Missing required field: shares")
    if (!sharesNode.isArray) return@handler Response(BAD_REQUEST).body("'shares' must be an array")

    val shares = mutableListOf<ExecutorShareService.ShareInput>()
    for (i in 0 until sharesNode.size()) {
        val s = sharesNode[i]

        val nominationIdStr = s.get("nomination_id")?.takeUnless { it.isNull }?.asText()
            ?: return@handler Response(BAD_REQUEST).body("shares[$i].nomination_id is required")
        val nominationId = runCatching { UUID.fromString(nominationIdStr) }.getOrElse {
            return@handler Response(BAD_REQUEST).body("shares[$i].nomination_id must be a valid UUID")
        }

        val shareIndex = s.get("share_index")?.takeUnless { it.isNull }?.asInt()
            ?: return@handler Response(BAD_REQUEST).body("shares[$i].share_index is required")

        val wrappedShare = s.get("wrapped_share")?.takeUnless { it.isNull }?.asText()
            ?: return@handler Response(BAD_REQUEST).body("shares[$i].wrapped_share is required")

        val shareFormat = s.get("share_format")?.takeUnless { it.isNull }?.asText()
            ?: return@handler Response(BAD_REQUEST).body("shares[$i].share_format is required")

        shares.add(ExecutorShareService.ShareInput(nominationId, shareIndex, wrappedShare, shareFormat))
    }

    return@handler when (val result = svc.submitShares(request.authUserId(), capsuleId, shares)) {
        ExecutorShareService.SubmitResult.Ok ->
            Response(OK)
                .header("Content-Type", "application/json")
                .body("{}")

        ExecutorShareService.SubmitResult.Forbidden ->
            Response(FORBIDDEN)
                .header("Content-Type", "application/json")
                .body("""{"error":"forbidden","detail":"capsule does not exist or caller is not the owner"}""")

        ExecutorShareService.SubmitResult.NotSealedShamir ->
            Response(UNPROCESSABLE_ENTITY)
                .header("Content-Type", "application/json")
                .body("""{"error":"capsule_not_sealed_shamir","detail":"capsule must be sealed with shamir_total_shares set"}""")

        is ExecutorShareService.SubmitResult.WrongShareCount ->
            Response(UNPROCESSABLE_ENTITY)
                .header("Content-Type", "application/json")
                .body("""{"error":"wrong_share_count","detail":"expected ${result.expected}, got ${result.got}"}""")

        ExecutorShareService.SubmitResult.InvalidShareIndices ->
            Response(UNPROCESSABLE_ENTITY)
                .header("Content-Type", "application/json")
                .body("""{"error":"invalid_share_indices","detail":"share_index values must be unique, 1-based, and cover 1..N without gaps"}""")

        is ExecutorShareService.SubmitResult.InvalidNominationId ->
            Response(UNPROCESSABLE_ENTITY)
                .header("Content-Type", "application/json")
                .body("""{"error":"invalid_nomination_id","detail":"${result.id}"}""")

        is ExecutorShareService.SubmitResult.InvalidWrappedShare ->
            Response(UNPROCESSABLE_ENTITY)
                .header("Content-Type", "application/json")
                .body("""{"error":"invalid_wrapped_share","detail":"share ${result.shareIndex}"}""")

        ExecutorShareService.SubmitResult.InvalidShareFormat ->
            Response(UNPROCESSABLE_ENTITY)
                .header("Content-Type", "application/json")
                .body("""{"error":"invalid_share_format","detail":"share_format must be \"shamir-share-v1\""}""")
    }
}

// ─── Endpoint 14: GET /capsules/:id/executor-shares/mine ─────────────────────

private fun getMineShareRoute(svc: ExecutorShareService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/capsules" / id / "executor-shares" / "mine" meta {
        summary = "Fetch own executor share"
        description = "Accepted executor fetches their own wrapped Shamir share for this capsule."
    } bindContract GET to { capsuleId: UUID, _: String, _: String ->
        getMineShareHandler(svc, capsuleId)
    }
}

private fun getMineShareHandler(svc: ExecutorShareService, capsuleId: UUID): HttpHandler = { request ->
    when (val result = svc.getMineShare(capsuleId, request.authUserId())) {
        is ExecutorShareService.MineResult.Found -> {
            val share = result.share
            Response(OK)
                .header("Content-Type", "application/json")
                .body(
                    """{"share":{"wrapped_share":"${share.wrappedShare}","share_format":"${share.shareFormat}","share_index":${share.shareIndex},"capsule_id":"${share.capsuleId}"}}"""
                )
        }
        ExecutorShareService.MineResult.NotYetDistributed ->
            Response(NOT_FOUND)
                .header("Content-Type", "application/json")
                .body("""{"error":"share_not_found","detail":"shares have not yet been distributed for this capsule"}""")

        ExecutorShareService.MineResult.Forbidden ->
            Response(FORBIDDEN)
                .header("Content-Type", "application/json")
                .body("""{"error":"forbidden","detail":"caller is not an accepted executor for this capsule"}""")
    }
}

// ─── Endpoint 15: GET /capsules/:id/executor-shares/collect ──────────────────

private fun collectSharesRoute(svc: ExecutorShareService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/capsules" / id / "executor-shares" / "collect" meta {
        summary = "Collect all executor shares"
        description = """
            Author-authenticated: collect all executor shares for this capsule.
            In M11, the caller must be the capsule author.
            Death-verification-gated collection (executor-initiated quorum) is M13 scope.
        """.trimIndent()
    } bindContract GET to { capsuleId: UUID, _: String, _: String ->
        collectSharesHandler(svc, capsuleId)
    }
}

private fun collectSharesHandler(svc: ExecutorShareService, capsuleId: UUID): HttpHandler = { request ->
    // TODO(M13): Add death-verification-gated collection via executor-initiated quorum.
    // In M13, executors will initiate a quorum by providing proof of death; until that
    // verification passes, the /collect endpoint should gate on death-verification status.
    // For M11 the endpoint is author-only and no death verification is required.
    when (val result = svc.collectShares(capsuleId, request.authUserId())) {
        is ExecutorShareService.CollectResult.Found -> {
            val sharesJson = result.shares.joinToString(",") { s ->
                """{"share_index":${s.shareIndex},"wrapped_share":"${s.wrappedShare}","nomination_id":"${s.nominationId}"}"""
            }
            val threshold = result.threshold?.toString() ?: "null"
            val total = result.total?.toString() ?: "null"
            Response(OK)
                .header("Content-Type", "application/json")
                .body("""{"shares":[$sharesJson],"threshold":$threshold,"total":$total}""")
        }
        ExecutorShareService.CollectResult.Forbidden ->
            Response(FORBIDDEN)
                .header("Content-Type", "application/json")
                .body("""{"error":"forbidden","detail":"caller is not the capsule author"}""")
    }
}
