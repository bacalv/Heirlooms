package digital.heirlooms.server.representation.plot

import digital.heirlooms.server.domain.plot.PlotMemberRecord
import digital.heirlooms.server.domain.plot.SharedMembershipRecord
import digital.heirlooms.server.representation.responseMapper
import java.time.Instant
import java.util.Base64
import java.util.UUID

private val enc = Base64.getEncoder()

private data class PlotMemberResponse(
    val userId: String,
    val displayName: String,
    val username: String,
    val role: String,
    val status: String,
    val localName: String?,
    val joinedAt: Instant,
)

private data class SharedMembershipResponse(
    val plotId: String,
    val plotName: String,
    val ownerUserId: String?,
    val ownerDisplayName: String?,
    val role: String,
    val status: String,
    val localName: String?,
    val joinedAt: Instant,
    val leftAt: Instant?,
    val plotStatus: String,
    val tombstonedAt: Instant?,
    val tombstonedBy: String?,
)

private data class PlotKeyResponse(
    val wrappedPlotKey: String,
    val plotKeyFormat: String,
)

private data class InviteResponse(
    val token: String,
    val expiresAt: Instant,
)

private data class JoinInfoResponse(
    val plotId: String,
    val plotName: String,
    val inviterDisplayName: String,
    val inviterUserId: String,
)

private data class PendingJoinResponse(
    val status: String,
    val inviteId: String,
    val inviterDisplayName: String,
)

fun PlotMemberRecord.toJson(): String =
    responseMapper.writeValueAsString(
        PlotMemberResponse(
            userId = userId.toString(),
            displayName = displayName,
            username = username,
            role = role,
            status = status,
            localName = localName,
            joinedAt = joinedAt,
        )
    )

fun SharedMembershipRecord.toJson(): String =
    responseMapper.writeValueAsString(
        SharedMembershipResponse(
            plotId = plotId.toString(),
            plotName = plotName,
            ownerUserId = ownerUserId?.toString(),
            ownerDisplayName = ownerDisplayName,
            role = role,
            status = status,
            localName = localName,
            joinedAt = joinedAt,
            leftAt = leftAt,
            plotStatus = plotStatus,
            tombstonedAt = tombstonedAt,
            tombstonedBy = tombstonedBy?.toString(),
        )
    )

fun plotKeyResponseJson(wrappedKey: ByteArray, format: String): String =
    responseMapper.writeValueAsString(
        PlotKeyResponse(
            wrappedPlotKey = enc.encodeToString(wrappedKey),
            plotKeyFormat = format,
        )
    )

fun inviteResponseJson(token: String, expiresAt: java.time.Instant): String =
    responseMapper.writeValueAsString(InviteResponse(token = token, expiresAt = expiresAt))

fun joinInfoResponseJson(plotId: UUID, plotName: String, inviterDisplayName: String, inviterUserId: UUID): String =
    responseMapper.writeValueAsString(
        JoinInfoResponse(
            plotId = plotId.toString(),
            plotName = plotName,
            inviterDisplayName = inviterDisplayName,
            inviterUserId = inviterUserId.toString(),
        )
    )

fun pendingJoinResponseJson(status: String, inviteId: UUID, inviterDisplayName: String): String =
    responseMapper.writeValueAsString(
        PendingJoinResponse(
            status = status,
            inviteId = inviteId.toString(),
            inviterDisplayName = inviterDisplayName,
        )
    )
