package digital.heirlooms.tools.reimport

import java.time.Instant

data class GcsObject(
    val key: String,
    val contentType: String?,
    val sizeBytes: Long,
    val createdAt: Instant,
    val downloadContent: () -> ByteArray,
)

interface BucketReader {
    fun listObjects(): Sequence<GcsObject>
    fun objectExists(key: String): Boolean
}
