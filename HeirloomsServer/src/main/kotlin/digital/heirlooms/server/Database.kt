package digital.heirlooms.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import digital.heirlooms.server.domain.auth.InviteRecord
import digital.heirlooms.server.domain.auth.UserRecord
import digital.heirlooms.server.domain.auth.UserSessionRecord
import digital.heirlooms.server.domain.capsule.CapsuleDetail
import digital.heirlooms.server.domain.capsule.CapsuleShape
import digital.heirlooms.server.domain.capsule.CapsuleState
import digital.heirlooms.server.domain.capsule.CapsuleSummary
import digital.heirlooms.server.domain.keys.AccountSharingKeyRecord
import digital.heirlooms.server.domain.keys.FriendRecord
import digital.heirlooms.server.domain.keys.PendingDeviceLinkRecord
import digital.heirlooms.server.domain.keys.RecoveryPassphraseRecord
import digital.heirlooms.server.domain.keys.WrappedKeyRecord
import digital.heirlooms.server.domain.plot.FlowRecord
import digital.heirlooms.server.domain.plot.PlotInviteRecord
import digital.heirlooms.server.domain.plot.PlotItemWithUpload
import digital.heirlooms.server.domain.plot.PlotMemberRecord
import digital.heirlooms.server.domain.plot.PlotRecord
import digital.heirlooms.server.domain.plot.SharedMembershipRecord
import digital.heirlooms.server.domain.upload.UploadPage
import digital.heirlooms.server.domain.upload.UploadRecord
import digital.heirlooms.server.domain.upload.UploadSort
import digital.heirlooms.server.repository.auth.AuthRepository
import digital.heirlooms.server.repository.capsule.CapsuleRepository
import digital.heirlooms.server.repository.diag.DiagRepository
import digital.heirlooms.server.repository.keys.KeyRepository
import digital.heirlooms.server.repository.plot.FlowRepository
import digital.heirlooms.server.repository.plot.PlotItemRepository
import digital.heirlooms.server.repository.plot.PlotMemberRepository
import digital.heirlooms.server.repository.plot.PlotRepository
import digital.heirlooms.server.repository.social.SocialRepository
import digital.heirlooms.server.repository.storage.BlobRepository
import digital.heirlooms.server.repository.upload.UploadRepository
import org.flywaydb.core.Flyway
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource

// Re-export FOUNDING_USER_ID from domain.auth for backwards compatibility
val FOUNDING_USER_ID: UUID get() = digital.heirlooms.server.domain.auth.FOUNDING_USER_ID

// ── Top-level type aliases so handlers keep the same Database.XxxResult references ──

typealias CompostResult = digital.heirlooms.server.repository.upload.UploadRepository.CompostResult
typealias RestoreResult = digital.heirlooms.server.repository.upload.UploadRepository.RestoreResult

typealias UpdateResult = digital.heirlooms.server.repository.capsule.CapsuleRepository.UpdateResult
typealias SealResult = digital.heirlooms.server.repository.capsule.CapsuleRepository.SealResult
typealias CancelResult = digital.heirlooms.server.repository.capsule.CapsuleRepository.CancelResult

typealias PlotUpdateResult = digital.heirlooms.server.repository.plot.PlotRepository.PlotUpdateResult
typealias PlotDeleteResult = digital.heirlooms.server.repository.plot.PlotRepository.PlotDeleteResult
typealias BatchReorderResult = digital.heirlooms.server.repository.plot.PlotRepository.BatchReorderResult

typealias LeavePlotResult = digital.heirlooms.server.repository.plot.PlotMemberRepository.LeavePlotResult

typealias FlowCreateResult = digital.heirlooms.server.repository.plot.FlowRepository.FlowCreateResult
typealias FlowUpdateResult = digital.heirlooms.server.repository.plot.FlowRepository.FlowUpdateResult

typealias ApproveResult = digital.heirlooms.server.repository.plot.PlotItemRepository.ApproveResult
typealias RejectResult = digital.heirlooms.server.repository.plot.PlotItemRepository.RejectResult
typealias AddItemResult = digital.heirlooms.server.repository.plot.PlotItemRepository.AddItemResult
typealias RemoveItemResult = digital.heirlooms.server.repository.plot.PlotItemRepository.RemoveItemResult

typealias AddMemberResult = digital.heirlooms.server.repository.plot.PlotMemberRepository.AddMemberResult
typealias RedeemInviteResult = digital.heirlooms.server.repository.plot.PlotMemberRepository.RedeemInviteResult
typealias AcceptInviteResult = digital.heirlooms.server.repository.plot.PlotMemberRepository.AcceptInviteResult
typealias RejoinResult = digital.heirlooms.server.repository.plot.PlotMemberRepository.RejoinResult
typealias RestorePlotResult = digital.heirlooms.server.repository.plot.PlotMemberRepository.RestorePlotResult
typealias TransferOwnershipResult = digital.heirlooms.server.repository.plot.PlotMemberRepository.TransferOwnershipResult
typealias SetPlotStatusResult = digital.heirlooms.server.repository.plot.PlotMemberRepository.SetPlotStatusResult
typealias InviteInfo = digital.heirlooms.server.repository.plot.PlotMemberRepository.InviteInfo

/**
 * Facade that wires all repository classes together and provides the single
 * entry-point used by all handlers.  Each method delegates to the appropriate
 * repository — this is the shim layer for phases 2-4; handlers are updated
 * to call repositories directly in phase 5+.
 */
class Database(private val dataSource: DataSource) {

    // ── Repository instances ──────────────────────────────────────────────────

    private val uploads = UploadRepository(dataSource)
    private val plots   = PlotRepository(dataSource)
    private val flows   = FlowRepository(dataSource)
    private val items   = PlotItemRepository(dataSource)
    private val members = PlotMemberRepository(dataSource)
    private val capsules = CapsuleRepository(dataSource)
    private val auth    = AuthRepository(dataSource)
    private val keys    = KeyRepository(dataSource)
    private val social  = SocialRepository(dataSource)
    private val blobs   = BlobRepository(dataSource)
    private val diag    = DiagRepository(dataSource)

    // ── Migrations ────────────────────────────────────────────────────────────

    fun runMigrations() {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }

    // ── Upload operations ─────────────────────────────────────────────────────

    fun recordUpload(record: UploadRecord, userId: UUID = FOUNDING_USER_ID) =
        uploads.recordUpload(record, userId)

    fun findByContentHash(hash: String, userId: UUID = FOUNDING_USER_ID): UploadRecord? =
        uploads.findByContentHash(hash, userId)

    fun existsByContentHash(hash: String, userId: UUID): Boolean =
        uploads.existsByContentHash(hash, userId)

    fun getUploadById(id: UUID): UploadRecord? =
        uploads.getUploadById(id)

    fun findUploadByIdForUser(id: UUID, userId: UUID = FOUNDING_USER_ID): UploadRecord? =
        uploads.findUploadByIdForUser(id, userId)

    fun findUploadByIdForSharedMember(id: UUID, userId: UUID): UploadRecord? =
        uploads.findUploadByIdForSharedMember(id, userId)

    fun recordView(id: UUID, userId: UUID = FOUNDING_USER_ID): Boolean =
        uploads.recordView(id, userId)

    fun listUploads(tag: String? = null, excludeTag: String? = null, userId: UUID = FOUNDING_USER_ID): List<UploadRecord> =
        uploads.listUploads(tag, excludeTag, userId)

    fun listCompostedUploads(userId: UUID = FOUNDING_USER_ID): List<UploadRecord> =
        uploads.listCompostedUploads(userId)

    fun fetchExpiredCompostedUploads(): List<UploadRecord> =
        uploads.fetchExpiredCompostedUploads()

    fun hardDeleteUpload(id: UUID) = uploads.hardDeleteUpload(id)

    fun userAlreadyHasStorageKey(userId: UUID, storageKey: String): Boolean =
        uploads.userAlreadyHasStorageKey(userId, storageKey)

    fun hasLiveSharedReference(storageKey: String, excludeUploadId: UUID): Boolean =
        uploads.hasLiveSharedReference(storageKey, excludeUploadId)

    fun createSharedUpload(
        fromRecord: UploadRecord, fromUserId: UUID, toUserId: UUID,
        wrappedDek: ByteArray, wrappedThumbnailDek: ByteArray?, dekFormat: String,
        rotationOverride: Int? = null,
    ): UploadRecord = uploads.createSharedUpload(fromRecord, fromUserId, toUserId, wrappedDek, wrappedThumbnailDek, dekFormat, rotationOverride)

    fun updateRotation(id: UUID, rotation: Int, userId: UUID = FOUNDING_USER_ID): Boolean =
        uploads.updateRotation(id, rotation, userId)

    fun updateTags(id: UUID, tags: List<String>, userId: UUID = FOUNDING_USER_ID): Boolean =
        uploads.updateTags(id, tags, userId) { conn, uploadId, uid ->
            flows.runUnstagedFlowsForUpload(conn, uploadId, uid)
        }

    fun uploadExists(id: UUID, userId: UUID = FOUNDING_USER_ID): Boolean =
        uploads.uploadExists(id, userId)

    fun listPendingExifIds(): List<UUID> =
        uploads.listPendingExifIds()

    fun updateExif(
        id: UUID, takenAt: Instant?, latitude: Double?, longitude: Double?,
        altitude: Double?, deviceMake: String?, deviceModel: String?,
    ) = uploads.updateExif(id, takenAt, latitude, longitude, altitude, deviceMake, deviceModel)

    fun migrateUploadToEncrypted(
        id: UUID, newStorageKey: String, newContentHash: String?, envelopeVersion: Int?,
        wrappedDek: ByteArray, dekFormat: String, encryptedMetadata: ByteArray?,
        encryptedMetadataFormat: String?, thumbnailStorageKey: String?,
        wrappedThumbnailDek: ByteArray?, thumbnailDekFormat: String?,
    ): Boolean = uploads.migrateUploadToEncrypted(
        id, newStorageKey, newContentHash, envelopeVersion, wrappedDek, dekFormat,
        encryptedMetadata, encryptedMetadataFormat, thumbnailStorageKey, wrappedThumbnailDek, thumbnailDekFormat,
    )

    // ── Upload pagination ──────────────────────────────────────────────────────

    fun listUploadsPaginated(
        cursor: String? = null, limit: Int = 50, tags: List<String> = emptyList(),
        excludeTag: String? = null, fromDate: Instant? = null, toDate: Instant? = null,
        inCapsule: Boolean? = null, includeComposted: Boolean = false, hasLocation: Boolean? = null,
        sort: UploadSort = UploadSort.UPLOAD_NEWEST, justArrived: Boolean = false,
        mediaType: String? = null, isReceived: Boolean? = null, plotId: UUID? = null,
        userId: UUID = FOUNDING_USER_ID,
    ): UploadPage = uploads.listUploadsPaginated(
        cursor, limit, tags, excludeTag, fromDate, toDate, inCapsule, includeComposted,
        hasLocation, sort, justArrived, mediaType, isReceived, plotId, userId, plots,
    )

    fun listCompostedUploadsPaginated(cursor: String? = null, limit: Int = 50, userId: UUID = FOUNDING_USER_ID): UploadPage =
        uploads.listCompostedUploadsPaginated(cursor, limit, userId)

    fun listAllTags(userId: UUID = FOUNDING_USER_ID): List<String> =
        uploads.listAllTags(userId)

    // ── Compost / restore ─────────────────────────────────────────────────────

    fun compostUpload(id: UUID, userId: UUID = FOUNDING_USER_ID): digital.heirlooms.server.repository.upload.UploadRepository.CompostResult =
        uploads.compostUpload(id, userId)

    fun restoreUpload(id: UUID, userId: UUID = FOUNDING_USER_ID): digital.heirlooms.server.repository.upload.UploadRepository.RestoreResult =
        uploads.restoreUpload(id, userId)

    // ── Sharing / social ──────────────────────────────────────────────────────

    fun upsertSharingKey(userId: UUID, pubkey: ByteArray, wrappedPrivkey: ByteArray, wrapFormat: String) =
        social.upsertSharingKey(userId, pubkey, wrappedPrivkey, wrapFormat)

    fun getSharingKey(userId: UUID): AccountSharingKeyRecord? =
        social.getSharingKey(userId)

    fun listFriends(userId: UUID): List<FriendRecord> =
        social.listFriends(userId)

    fun createFriendship(a: UUID, b: UUID) =
        social.createFriendship(a, b)

    fun areFriends(a: UUID, b: UUID): Boolean =
        social.areFriends(a, b)

    // ── Capsule operations ────────────────────────────────────────────────────

    fun createCapsule(
        id: UUID, createdByUser: String, shape: CapsuleShape, state: CapsuleState,
        unlockAt: OffsetDateTime, recipients: List<String>, uploadIds: List<UUID>,
        message: String, userId: UUID = FOUNDING_USER_ID,
    ): CapsuleDetail = capsules.createCapsule(id, createdByUser, shape, state, unlockAt, recipients, uploadIds, message, userId)

    fun getCapsuleById(id: UUID, userId: UUID = FOUNDING_USER_ID): CapsuleDetail? =
        capsules.getCapsuleById(id, userId)

    fun listCapsules(states: List<CapsuleState>, orderBy: String, userId: UUID = FOUNDING_USER_ID): List<CapsuleSummary> =
        capsules.listCapsules(states, orderBy, userId)

    fun getCapsulesForUpload(uploadId: UUID, userId: UUID = FOUNDING_USER_ID): List<CapsuleSummary>? =
        capsules.getCapsulesForUpload(uploadId, userId)

    fun updateCapsule(
        id: UUID, userId: UUID = FOUNDING_USER_ID, unlockAt: OffsetDateTime?,
        recipients: List<String>?, uploadIds: List<UUID>?, message: String?,
    ): UpdateResult = capsules.updateCapsule(id, userId, unlockAt, recipients, uploadIds, message)

    fun sealCapsule(id: UUID, userId: UUID = FOUNDING_USER_ID): SealResult =
        capsules.sealCapsule(id, userId)

    fun cancelCapsule(id: UUID, userId: UUID = FOUNDING_USER_ID): CancelResult =
        capsules.cancelCapsule(id, userId)

    // ── Plot operations ───────────────────────────────────────────────────────

    fun listPlots(userId: UUID = FOUNDING_USER_ID): List<PlotRecord> =
        plots.listPlots(userId)

    fun getPlotById(id: UUID): PlotRecord? =
        plots.getPlotById(id)

    fun createPlot(
        name: String, criteria: String?, showInGarden: Boolean, visibility: String,
        wrappedPlotKeyB64: String? = null, plotKeyFormat: String? = null, userId: UUID = FOUNDING_USER_ID,
    ): PlotRecord = plots.createPlot(name, criteria, showInGarden, visibility, wrappedPlotKeyB64, plotKeyFormat, userId)

    fun updatePlot(id: UUID, name: String?, sortOrder: Int?, criteria: String?, showInGarden: Boolean?, userId: UUID = FOUNDING_USER_ID): PlotUpdateResult =
        plots.updatePlot(id, name, sortOrder, criteria, showInGarden, userId)

    fun deletePlot(id: UUID, userId: UUID = FOUNDING_USER_ID): PlotDeleteResult =
        plots.deletePlot(id, userId)

    fun batchReorderPlots(updates: List<Pair<UUID, Int>>, userId: UUID = FOUNDING_USER_ID): BatchReorderResult =
        plots.batchReorderPlots(updates, userId)

    fun withCriteriaValidation(node: com.fasterxml.jackson.databind.JsonNode, userId: UUID) =
        plots.withCriteriaValidation(node, userId)

    fun isMember(plotId: UUID, userId: UUID): Boolean =
        plots.isMember(plotId, userId)

    fun fetchExpiredTombstonedPlots(): List<UUID> =
        plots.fetchExpiredTombstonedPlots()

    fun hardDeletePlot(plotId: UUID) =
        plots.hardDeletePlot(plotId)

    fun createSystemPlot(userId: UUID) =
        plots.createSystemPlot(userId)

    // ── Leave plot ────────────────────────────────────────────────────────────

    fun leavePlot(plotId: UUID, userId: UUID): LeavePlotResult =
        members.leavePlot(plotId, userId)

    // ── Flow operations ───────────────────────────────────────────────────────

    fun listFlows(userId: UUID = FOUNDING_USER_ID): List<FlowRecord> =
        flows.listFlows(userId)

    fun getFlowById(id: UUID, userId: UUID = FOUNDING_USER_ID): FlowRecord? =
        flows.getFlowById(id, userId)

    fun createFlow(name: String, criteriaJson: String, targetPlotId: UUID, requiresStaging: Boolean, userId: UUID = FOUNDING_USER_ID): FlowCreateResult {
        val targetPlot = plots.getPlotById(targetPlotId) ?: return FlowRepository.FlowCreateResult.Error("Target plot not found")
        if (targetPlot.ownerUserId != userId) return FlowRepository.FlowCreateResult.Error("Target plot not found")
        return flows.createFlow(name, criteriaJson, targetPlotId, requiresStaging, targetPlot, userId)
    }

    fun updateFlow(id: UUID, name: String?, criteriaJson: String?, requiresStaging: Boolean?, userId: UUID = FOUNDING_USER_ID): FlowUpdateResult {
        val existingFlow = flows.getFlowById(id, userId) ?: return FlowRepository.FlowUpdateResult.NotFound
        val targetPlot = plots.getPlotById(existingFlow.targetPlotId)
        return flows.updateFlow(id, name, criteriaJson, requiresStaging, targetPlot, userId)
    }

    fun deleteFlow(id: UUID, userId: UUID = FOUNDING_USER_ID): Boolean =
        flows.deleteFlow(id, userId)

    // ── Staging operations ────────────────────────────────────────────────────

    fun getStagingItems(flowId: UUID, userId: UUID = FOUNDING_USER_ID): List<UploadRecord> {
        val flow = flows.getFlowById(flowId, userId) ?: return emptyList()
        return items.getStagingItems(flowId, flow, userId)
    }

    fun getStagingItemsForPlot(plotId: UUID, userId: UUID = FOUNDING_USER_ID): List<UploadRecord> {
        val stagingFlows = flows.listFlows(userId).filter { it.targetPlotId == plotId && it.requiresStaging }
        return items.getStagingItemsForPlot(plotId, stagingFlows, userId)
    }

    fun approveStagingItem(
        plotId: UUID, uploadId: UUID, sourceFlowId: UUID?, userId: UUID = FOUNDING_USER_ID,
        wrappedItemDekBytes: ByteArray? = null, itemDekFormat: String? = null,
        wrappedThumbnailDekBytes: ByteArray? = null, thumbnailDekFormat: String? = null,
    ): ApproveResult {
        val plot = plots.getPlotById(plotId) ?: return PlotItemRepository.ApproveResult.NotFound
        return items.approveStagingItem(plot, uploadId, sourceFlowId, userId, wrappedItemDekBytes, itemDekFormat, wrappedThumbnailDekBytes, thumbnailDekFormat)
    }

    fun rejectStagingItem(plotId: UUID, uploadId: UUID, sourceFlowId: UUID?, userId: UUID = FOUNDING_USER_ID): RejectResult {
        val plot = plots.getPlotById(plotId) ?: return PlotItemRepository.RejectResult.NotFound
        return items.rejectStagingItem(plot, uploadId, sourceFlowId, userId)
    }

    fun deleteDecision(plotId: UUID, uploadId: UUID, userId: UUID = FOUNDING_USER_ID): Boolean {
        val plot = plots.getPlotById(plotId) ?: return false
        return items.deleteDecision(plot, uploadId, userId)
    }

    fun getRejectedItems(plotId: UUID, userId: UUID = FOUNDING_USER_ID): List<UploadRecord> {
        val plot = plots.getPlotById(plotId) ?: return emptyList()
        return items.getRejectedItems(plot, userId)
    }

    // ── Collection plot item operations ───────────────────────────────────────

    fun getPlotItems(plotId: UUID, userId: UUID = FOUNDING_USER_ID): List<PlotItemWithUpload> {
        val conn = dataSource.connection
        val plot = plots.getPlotByIdForUser(conn, plotId, userId)
        conn.close()
        return items.getPlotItems(plotId, userId, plot)
    }

    fun addPlotItem(
        plotId: UUID, uploadId: UUID, userId: UUID = FOUNDING_USER_ID,
        wrappedItemDekBytes: ByteArray? = null, itemDekFormat: String? = null,
        wrappedThumbnailDekBytes: ByteArray? = null, thumbnailDekFormat: String? = null,
    ): AddItemResult {
        val plot = plots.getPlotById(plotId)
        val uploadExists = uploads.uploadExists(uploadId, userId)
        return items.addPlotItem(plot, uploadId, userId, uploadExists, wrappedItemDekBytes, itemDekFormat, wrappedThumbnailDekBytes, thumbnailDekFormat)
    }

    fun removePlotItem(plotId: UUID, uploadId: UUID, userId: UUID = FOUNDING_USER_ID): RemoveItemResult =
        items.removePlotItem(plotId, uploadId, userId)

    // ── Plot members / invites ────────────────────────────────────────────────

    fun getPlotKey(plotId: UUID, userId: UUID = FOUNDING_USER_ID): Pair<ByteArray, String>? =
        members.getPlotKey(plotId, userId)

    fun listMembers(plotId: UUID, userId: UUID = FOUNDING_USER_ID): List<PlotMemberRecord>? =
        members.listMembers(plotId, userId)

    fun addMember(plotId: UUID, newUserId: UUID, wrappedPlotKey: ByteArray, plotKeyFormat: String, inviterUserId: UUID = FOUNDING_USER_ID): AddMemberResult =
        members.addMember(plotId, newUserId, wrappedPlotKey, plotKeyFormat, inviterUserId)

    fun createInvite(plotId: UUID, userId: UUID = FOUNDING_USER_ID): PlotInviteRecord? =
        members.createInvite(plotId, userId)

    fun getInviteInfo(token: String): InviteInfo? =
        members.getInviteInfo(token)

    fun redeemInvite(token: String, recipientUserId: UUID, recipientPubkey: String): RedeemInviteResult =
        members.redeemInvite(token, recipientUserId, recipientPubkey)

    fun listPendingInvites(plotId: UUID, userId: UUID = FOUNDING_USER_ID): List<Map<String, String>> =
        members.listPendingInvites(plotId, userId)

    fun confirmInvite(inviteId: UUID, plotId: UUID, wrappedPlotKey: ByteArray, plotKeyFormat: String, confirmerUserId: UUID = FOUNDING_USER_ID): Boolean =
        members.confirmInvite(inviteId, plotId, wrappedPlotKey, plotKeyFormat, confirmerUserId)

    fun listSharedMemberships(userId: UUID): List<SharedMembershipRecord> =
        members.listSharedMemberships(userId)

    fun acceptInvite(plotId: UUID, userId: UUID, localName: String): AcceptInviteResult =
        members.acceptInvite(plotId, userId, localName)

    fun rejoinPlot(plotId: UUID, userId: UUID, localName: String?): RejoinResult =
        members.rejoinPlot(plotId, userId, localName)

    fun restorePlot(plotId: UUID, userId: UUID): RestorePlotResult =
        members.restorePlot(plotId, userId)

    fun transferOwnership(plotId: UUID, newOwnerId: UUID, currentOwnerId: UUID): TransferOwnershipResult =
        members.transferOwnership(plotId, newOwnerId, currentOwnerId)

    fun setPlotStatus(plotId: UUID, status: String, userId: UUID): SetPlotStatusResult =
        members.setPlotStatus(plotId, status, userId)

    // ── Auth (users, sessions, invites, pairing) ──────────────────────────────

    fun createUser(id: UUID = UUID.randomUUID(), username: String, displayName: String, authVerifier: ByteArray? = null, authSalt: ByteArray? = null): UserRecord =
        auth.createUser(id, username, displayName, authVerifier, authSalt)

    fun findUserByUsername(username: String): UserRecord? =
        auth.findUserByUsername(username)

    fun findUserById(id: UUID): UserRecord? =
        auth.findUserById(id)

    fun setUserAuth(userId: UUID, authVerifier: ByteArray, authSalt: ByteArray) =
        auth.setUserAuth(userId, authVerifier, authSalt)

    fun resetUserAuth(userId: UUID) =
        auth.resetUserAuth(userId)

    fun createSession(userId: UUID, tokenHash: ByteArray, deviceKind: String): UserSessionRecord =
        auth.createSession(userId, tokenHash, deviceKind)

    fun findSessionByTokenHash(tokenHash: ByteArray): UserSessionRecord? =
        auth.findSessionByTokenHash(tokenHash)

    fun deleteSession(id: UUID) =
        auth.deleteSession(id)

    fun refreshSession(id: UUID) =
        auth.refreshSession(id)

    fun deleteExpiredSessions() =
        auth.deleteExpiredSessions()

    fun createInvite(createdBy: UUID, rawToken: String): InviteRecord =
        auth.createInvite(createdBy, rawToken)

    fun findInviteByToken(token: String): InviteRecord? =
        auth.findInviteByToken(token)

    fun markInviteUsed(id: UUID, usedBy: UUID) =
        auth.markInviteUsed(id, usedBy)

    fun createPairingLink(userId: UUID, code: String): PendingDeviceLinkRecord =
        auth.createPairingLink(userId, code)

    fun setPairingWebSession(id: UUID, webSessionId: String) =
        auth.setPairingWebSession(id, webSessionId)

    fun completePairingLink(id: UUID, wrappedMasterKey: ByteArray, wrapFormat: String, rawSessionToken: String, sessionRecord: UserSessionRecord) =
        auth.completePairingLink(id, wrappedMasterKey, wrapFormat, rawSessionToken, sessionRecord)

    fun getPendingDeviceLinkByCode(code: String): PendingDeviceLinkRecord? =
        auth.getPendingDeviceLinkByCode(code)

    fun getPendingDeviceLinkByWebSessionId(webSessionId: String): PendingDeviceLinkRecord? =
        auth.getPendingDeviceLinkByWebSessionId(webSessionId)

    // ── Wrapped keys / device links ───────────────────────────────────────────

    fun insertWrappedKey(record: WrappedKeyRecord, userId: UUID = FOUNDING_USER_ID) =
        keys.insertWrappedKey(record, userId)

    fun listWrappedKeys(userId: UUID = FOUNDING_USER_ID, includeRetired: Boolean = false): List<WrappedKeyRecord> =
        keys.listWrappedKeys(userId, includeRetired)

    fun getWrappedKeyByDeviceId(deviceId: String): WrappedKeyRecord? =
        keys.getWrappedKeyByDeviceId(deviceId)

    fun getWrappedKeyByDeviceIdForUser(deviceId: String, userId: UUID): WrappedKeyRecord? =
        keys.getWrappedKeyByDeviceIdForUser(deviceId, userId)

    fun getWrappedKeyByDeviceIdAndUser(deviceId: String, userId: UUID): WrappedKeyRecord? =
        keys.getWrappedKeyByDeviceIdAndUser(deviceId, userId)

    fun retireWrappedKey(id: UUID, retiredAt: java.time.Instant = java.time.Instant.now()) =
        keys.retireWrappedKey(id, retiredAt)

    fun touchWrappedKey(id: UUID) =
        keys.touchWrappedKey(id)

    fun retireDormantWrappedKeys(dormantBefore: java.time.Instant): Int =
        keys.retireDormantWrappedKeys(dormantBefore)

    fun getRecoveryPassphrase(userId: UUID = FOUNDING_USER_ID): RecoveryPassphraseRecord? =
        keys.getRecoveryPassphrase(userId)

    fun upsertRecoveryPassphrase(record: RecoveryPassphraseRecord, userId: UUID = FOUNDING_USER_ID) =
        keys.upsertRecoveryPassphrase(record, userId)

    fun deleteRecoveryPassphrase(userId: UUID = FOUNDING_USER_ID): Boolean =
        keys.deleteRecoveryPassphrase(userId)

    fun insertPendingDeviceLink(record: PendingDeviceLinkRecord) =
        keys.insertPendingDeviceLink(record)

    fun getPendingDeviceLink(id: UUID): PendingDeviceLinkRecord? =
        keys.getPendingDeviceLink(id)


    fun registerNewDevice(id: UUID, deviceId: String, deviceLabel: String, deviceKind: String, pubkeyFormat: String, pubkey: ByteArray) =
        keys.registerNewDevice(id, deviceId, deviceLabel, deviceKind, pubkeyFormat, pubkey)

    fun completeDeviceLink(id: UUID, wrappedMasterKey: ByteArray, wrapFormat: String, deviceId: String, deviceLabel: String, deviceKind: String, pubkeyFormat: String, pubkey: ByteArray, userId: UUID = FOUNDING_USER_ID) =
        keys.completeDeviceLink(id, wrappedMasterKey, wrapFormat, deviceId, deviceLabel, deviceKind, pubkeyFormat, pubkey, userId)

    fun deleteExpiredDeviceLinks(before: java.time.Instant): Int =
        keys.deleteExpiredDeviceLinks(before)

    // ── Pending blobs ──────────────────────────────────────────────────────────

    fun insertPendingBlob(storageKey: String): UUID =
        blobs.insertPendingBlob(storageKey)

    fun deletePendingBlob(storageKey: String) =
        blobs.deletePendingBlob(storageKey)

    fun deleteStalePendingBlobs(olderThan: java.time.Instant): List<String> =
        blobs.deleteStalePendingBlobs(olderThan)

    // ── Diagnostics ───────────────────────────────────────────────────────────

    fun insertDiagEvent(deviceLabel: String, tag: String, message: String, detail: String, userId: UUID = FOUNDING_USER_ID) =
        diag.insertDiagEvent(deviceLabel, tag, message, detail, userId)

    fun listDiagEvents(userId: UUID = FOUNDING_USER_ID, limit: Int = 200): List<Map<String, String>> =
        diag.listDiagEvents(userId, limit)

    // ── Companion ─────────────────────────────────────────────────────────────

    companion object {
        fun create(config: AppConfig): Database {
            val hikari = HikariConfig().apply {
                jdbcUrl = config.dbUrl
                username = config.dbUser
                password = config.dbPassword
                maximumPoolSize = 10
                minimumIdle = 2
                connectionTimeout = 30_000
                idleTimeout = 600_000
            }
            return Database(HikariDataSource(hikari))
        }
    }
}

// UploadRecord.toJson() has moved to representation/upload/UploadRepresentation.kt
