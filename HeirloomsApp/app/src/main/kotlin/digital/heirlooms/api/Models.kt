package digital.heirlooms.api

data class Upload(
    val id: String,
    val storageKey: String,
    val mimeType: String,
    val fileSize: Long,
    val uploadedAt: String,
    val rotation: Int,
    val thumbnailKey: String?,
    val tags: List<String>,
    val compostedAt: String?,
    val takenAt: String?,
    val latitude: Double?,
    val longitude: Double?,
    val lastViewedAt: String?,
    // E2EE fields — present only when storageClass == "encrypted"
    val storageClass: String = "public",
    val envelopeVersion: Int? = null,
    val wrappedDek: ByteArray? = null,
    val dekFormat: String? = null,
    val wrappedThumbnailDek: ByteArray? = null,
    val thumbnailDekFormat: String? = null,
    val previewStorageKey: String? = null,
    val wrappedPreviewDek: ByteArray? = null,
    val previewDekFormat: String? = null,
    val plainChunkSize: Int? = null,
    val durationSeconds: Int? = null,
    // Sharing provenance — non-null when this upload was shared from another user
    val sharedFromUserId: String? = null,
    // Display name resolved client-side from the friends list
    val sharedFromDisplayName: String? = null,
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
    val isEncrypted: Boolean get() = storageClass == "encrypted"
    val isShared: Boolean get() = sharedFromUserId != null
}

data class UploadPage(
    val uploads: List<Upload>,
    val nextCursor: String?,
)

data class Plot(
    val id: String,
    val name: String,
    val criteria: String?,          // null = collection plot; JSON string for query plots
    val showInGarden: Boolean,
    val visibility: String,         // "private", "shared", "public"
    val sortOrder: Int,
    val isSystemDefined: Boolean,
    val isOwner: Boolean = true,    // false when user is a member but not the owner
    val plotStatus: String = "open",     // "open" or "closed"
    val localName: String? = null,       // member's chosen display name (non-null for non-owner members)
)

val Plot.isCollection: Boolean get() = criteria == null
val Plot.isShared: Boolean get() = visibility == "shared"

data class SharedMembership(
    val plotId: String,
    val plotName: String,
    val ownerUserId: String?,
    val ownerDisplayName: String?,
    val role: String,           // "owner" or "member"
    val status: String,         // "invited", "joined", "left"
    val localName: String?,
    val joinedAt: String,
    val leftAt: String?,
    val plotStatus: String,
    val tombstonedAt: String?,
    val tombstonedBy: String?,
)

data class PlotMember(
    val userId: String,
    val displayName: String,
    val username: String,
    val role: String,
    val status: String = "joined",
    val localName: String? = null,
)

data class Flow(
    val id: String,
    val name: String,
    val criteria: String,           // JSON string
    val targetPlotId: String,
    val requiresStaging: Boolean,
)

data class StagingItem(
    val upload: Upload,
)

data class PlotItem(
    val upload: Upload,
    val addedBy: String,
    val wrappedItemDek: String?,
    val itemDekFormat: String?,
    val wrappedThumbnailDek: String?,
    val thumbnailDekFormat: String?,
)

data class CapsuleSummary(
    val id: String,
    val shape: String,
    val state: String,
    val createdAt: String,
    val updatedAt: String,
    val unlockAt: String,
    val recipients: List<String>,
    val uploadCount: Int,
    val hasMessage: Boolean,
    val cancelledAt: String?,
    val deliveredAt: String?,
)

data class CapsuleDetail(
    val id: String,
    val shape: String,
    val state: String,
    val createdAt: String,
    val updatedAt: String,
    val unlockAt: String,
    val recipients: List<String>,
    val uploads: List<Upload>,
    val message: String,
    val cancelledAt: String?,
    val deliveredAt: String?,
)

data class CapsuleRef(
    val id: String,
    val shape: String,
    val state: String,
    val unlockAt: String,
    val recipients: List<String>,
)

data class AppSettings(
    val previewDurationSeconds: Int = 15,
)

sealed class LoadState<out T> {
    object Loading : LoadState<Nothing>()
    data class Success<T>(val data: T) : LoadState<T>()
    data class Error(val message: String) : LoadState<Nothing>()
}
