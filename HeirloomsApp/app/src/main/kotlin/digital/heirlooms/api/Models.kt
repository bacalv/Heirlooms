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
    val tagCriteria: List<String>,
    val sortOrder: Int,
    val isSystemDefined: Boolean,
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
