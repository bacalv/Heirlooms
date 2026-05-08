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

sealed class LoadState<out T> {
    object Loading : LoadState<Nothing>()
    data class Success<T>(val data: T) : LoadState<T>()
    data class Error(val message: String) : LoadState<Nothing>()
}
