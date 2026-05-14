package digital.heirlooms.server.domain.plot

import digital.heirlooms.server.domain.upload.UploadRecord
import java.time.Instant
import java.util.UUID

data class PlotRecord(
    val id: UUID,
    val ownerUserId: UUID?,
    val name: String,
    val sortOrder: Int,
    val isSystemDefined: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val criteria: String?,
    val showInGarden: Boolean,
    val visibility: String,
    val plotStatus: String = "open",
    val tombstonedAt: Instant? = null,
    val tombstonedBy: UUID? = null,
    val createdBy: UUID? = null,
    val localName: String? = null,
)

data class FlowRecord(
    val id: UUID,
    val userId: UUID,
    val name: String,
    val criteria: String,
    val targetPlotId: UUID,
    val requiresStaging: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class PlotItemRecord(
    val id: UUID,
    val plotId: UUID,
    val uploadId: UUID,
    val addedBy: UUID,
    val sourceFlowId: UUID?,
    val addedAt: Instant,
)

data class PlotItemWithUpload(
    val upload: UploadRecord,
    val addedBy: UUID,
    val wrappedItemDek: ByteArray?,
    val itemDekFormat: String?,
    val wrappedThumbnailDek: ByteArray?,
    val thumbnailDekFormat: String?,
)

data class PlotMemberRecord(
    val plotId: UUID,
    val userId: UUID,
    val displayName: String,
    val username: String,
    val role: String,
    val wrappedPlotKey: ByteArray?,
    val plotKeyFormat: String?,
    val joinedAt: Instant,
    val status: String = "joined",
    val localName: String? = null,
    val leftAt: Instant? = null,
)

data class SharedMembershipRecord(
    val plotId: UUID,
    val plotName: String,
    val ownerUserId: UUID?,
    val ownerDisplayName: String?,
    val role: String,
    val status: String,
    val localName: String?,
    val joinedAt: Instant,
    val leftAt: Instant?,
    val plotStatus: String,
    val tombstonedAt: Instant?,
    val tombstonedBy: UUID?,
)

data class PlotInviteRecord(
    val id: UUID,
    val plotId: UUID,
    val createdBy: UUID,
    val token: String,
    val recipientUserId: UUID?,
    val recipientPubkey: String?,
    val usedBy: UUID?,
    val expiresAt: Instant,
    val createdAt: Instant,
)
