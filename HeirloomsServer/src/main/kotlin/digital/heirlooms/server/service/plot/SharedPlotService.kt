package digital.heirlooms.server.service.plot

import digital.heirlooms.server.Database
import digital.heirlooms.server.domain.plot.PlotInviteRecord
import digital.heirlooms.server.domain.plot.PlotMemberRecord
import digital.heirlooms.server.domain.plot.SharedMembershipRecord
import digital.heirlooms.server.repository.plot.PlotMemberRepository
import java.util.UUID

/**
 * Encapsulates shared-plot membership lifecycle: key retrieval, member
 * management, invite link flow, leave / rejoin / restore, ownership
 * transfer, and status changes.
 */
class SharedPlotService(
    private val database: Database,
) {
    // ---- Plot key --------------------------------------------------------------

    /** Returns (wrappedPlotKey bytes, plotKeyFormat) or null if not found / not shared. */
    fun getPlotKey(plotId: UUID, userId: UUID): Pair<ByteArray, String>? {
        val plot = database.getPlotById(plotId) ?: return null
        if (plot.visibility != "shared") return null
        return database.getPlotKey(plotId, userId)
    }

    // ---- Members ---------------------------------------------------------------

    fun listMembers(plotId: UUID, userId: UUID): List<PlotMemberRecord>? =
        database.listMembers(plotId, userId)

    fun addMember(
        plotId: UUID,
        newUserId: UUID,
        wrappedPlotKey: ByteArray,
        plotKeyFormat: String,
        inviterUserId: UUID,
    ): PlotMemberRepository.AddMemberResult =
        database.addMember(plotId, newUserId, wrappedPlotKey, plotKeyFormat, inviterUserId)

    // ---- Invite link flow ------------------------------------------------------

    fun createInvite(plotId: UUID, userId: UUID): PlotInviteRecord? =
        database.createInvite(plotId, userId)

    fun getInviteInfo(token: String): PlotMemberRepository.InviteInfo? =
        database.getInviteInfo(token)

    fun redeemInvite(
        token: String,
        recipientUserId: UUID,
        recipientPubkey: String,
    ): PlotMemberRepository.RedeemInviteResult =
        database.redeemInvite(token, recipientUserId, recipientPubkey)

    fun listPendingInvites(plotId: UUID, userId: UUID): List<Map<String, String>> =
        database.listPendingInvites(plotId, userId)

    fun confirmInvite(
        inviteId: UUID,
        plotId: UUID,
        wrappedPlotKey: ByteArray,
        plotKeyFormat: String,
        confirmerUserId: UUID,
    ): Boolean = database.confirmInvite(inviteId, plotId, wrappedPlotKey, plotKeyFormat, confirmerUserId)

    // ---- Leave / accept / rejoin / restore ------------------------------------

    fun leavePlot(plotId: UUID, userId: UUID): PlotMemberRepository.LeavePlotResult =
        database.leavePlot(plotId, userId)

    fun acceptInvite(plotId: UUID, userId: UUID, localName: String): PlotMemberRepository.AcceptInviteResult =
        database.acceptInvite(plotId, userId, localName)

    fun rejoinPlot(plotId: UUID, userId: UUID, localName: String?): PlotMemberRepository.RejoinResult =
        database.rejoinPlot(plotId, userId, localName)

    fun restorePlot(plotId: UUID, userId: UUID): PlotMemberRepository.RestorePlotResult =
        database.restorePlot(plotId, userId)

    // ---- Transfer / status ----------------------------------------------------

    fun transferOwnership(
        plotId: UUID,
        newOwnerId: UUID,
        currentOwnerId: UUID,
    ): PlotMemberRepository.TransferOwnershipResult =
        database.transferOwnership(plotId, newOwnerId, currentOwnerId)

    fun setPlotStatus(plotId: UUID, status: String, userId: UUID): PlotMemberRepository.SetPlotStatusResult =
        database.setPlotStatus(plotId, status, userId)

    // ---- Memberships ----------------------------------------------------------

    fun listSharedMemberships(userId: UUID): List<SharedMembershipRecord> =
        database.listSharedMemberships(userId)
}
