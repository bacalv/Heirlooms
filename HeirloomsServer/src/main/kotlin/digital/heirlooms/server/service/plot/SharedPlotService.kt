package digital.heirlooms.server.service.plot

import digital.heirlooms.server.domain.plot.PlotInviteRecord
import digital.heirlooms.server.domain.plot.PlotMemberRecord
import digital.heirlooms.server.domain.plot.SharedMembershipRecord
import digital.heirlooms.server.repository.plot.PlotMemberRepository
import digital.heirlooms.server.repository.plot.PlotRepository
import java.util.UUID

/**
 * Encapsulates shared-plot membership lifecycle: key retrieval, member
 * management, invite link flow, leave / rejoin / restore, ownership
 * transfer, and status changes.
 */
class SharedPlotService(
    private val plotRepo: PlotRepository,
    private val memberRepo: PlotMemberRepository,
) {
    // ---- Plot key --------------------------------------------------------------

    sealed class GetPlotKeyResult {
        data class Success(val wrappedKey: ByteArray, val format: String) : GetPlotKeyResult()
        object NotShared : GetPlotKeyResult()
        object NotFound : GetPlotKeyResult()
    }

    fun getPlotKey(plotId: UUID, userId: UUID): GetPlotKeyResult {
        val plot = plotRepo.getPlotById(plotId) ?: return GetPlotKeyResult.NotFound
        if (plot.visibility != "shared") return GetPlotKeyResult.NotShared
        val pair = memberRepo.getPlotKey(plotId, userId) ?: return GetPlotKeyResult.NotFound
        return GetPlotKeyResult.Success(pair.first, pair.second)
    }

    // ---- Members ---------------------------------------------------------------

    fun listMembers(plotId: UUID, userId: UUID): List<PlotMemberRecord>? =
        memberRepo.listMembers(plotId, userId)

    fun addMember(
        plotId: UUID,
        newUserId: UUID,
        wrappedPlotKey: ByteArray,
        plotKeyFormat: String,
        inviterUserId: UUID,
    ): PlotMemberRepository.AddMemberResult =
        memberRepo.addMember(plotId, newUserId, wrappedPlotKey, plotKeyFormat, inviterUserId)

    // ---- Invite link flow ------------------------------------------------------

    fun createInvite(plotId: UUID, userId: UUID): PlotInviteRecord? =
        memberRepo.createInvite(plotId, userId)

    fun getInviteInfo(token: String): PlotMemberRepository.InviteInfo? =
        memberRepo.getInviteInfo(token)

    fun redeemInvite(
        token: String,
        recipientUserId: UUID,
        recipientPubkey: String,
    ): PlotMemberRepository.RedeemInviteResult =
        memberRepo.redeemInvite(token, recipientUserId, recipientPubkey)

    fun listPendingInvites(plotId: UUID, userId: UUID): List<Map<String, String>> =
        memberRepo.listPendingInvites(plotId, userId)

    fun confirmInvite(
        inviteId: UUID,
        plotId: UUID,
        wrappedPlotKey: ByteArray,
        plotKeyFormat: String,
        confirmerUserId: UUID,
    ): Boolean = memberRepo.confirmInvite(inviteId, plotId, wrappedPlotKey, plotKeyFormat, confirmerUserId)

    // ---- Leave / accept / rejoin / restore ------------------------------------

    fun leavePlot(plotId: UUID, userId: UUID): PlotMemberRepository.LeavePlotResult =
        memberRepo.leavePlot(plotId, userId)

    fun acceptInvite(plotId: UUID, userId: UUID, localName: String): PlotMemberRepository.AcceptInviteResult =
        memberRepo.acceptInvite(plotId, userId, localName)

    fun rejoinPlot(plotId: UUID, userId: UUID, localName: String?): PlotMemberRepository.RejoinResult =
        memberRepo.rejoinPlot(plotId, userId, localName)

    fun restorePlot(plotId: UUID, userId: UUID): PlotMemberRepository.RestorePlotResult =
        memberRepo.restorePlot(plotId, userId)

    // ---- Transfer / status ----------------------------------------------------

    fun transferOwnership(
        plotId: UUID,
        newOwnerId: UUID,
        currentOwnerId: UUID,
    ): PlotMemberRepository.TransferOwnershipResult =
        memberRepo.transferOwnership(plotId, newOwnerId, currentOwnerId)

    fun setPlotStatus(plotId: UUID, status: String, userId: UUID): PlotMemberRepository.SetPlotStatusResult =
        memberRepo.setPlotStatus(plotId, status, userId)

    // ---- Memberships ----------------------------------------------------------

    fun listSharedMemberships(userId: UUID): List<SharedMembershipRecord> =
        memberRepo.listSharedMemberships(userId)
}
