package digital.heirlooms.tools.reimport

import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import java.io.ByteArrayInputStream
import java.time.Instant

class GcsBucketReader(
    private val storage: Storage,
    private val bucket: String,
) : BucketReader {

    override fun listObjects(): Sequence<GcsObject> = sequence {
        var page: com.google.api.gax.paging.Page<com.google.cloud.storage.Blob>? = storage.list(bucket)
        while (page != null) {
            for (blob in page.values) {
                yield(
                    GcsObject(
                        key = blob.name,
                        contentType = blob.contentType,
                        sizeBytes = blob.size ?: 0L,
                        createdAt = blob.createTime?.let { Instant.ofEpochMilli(it) } ?: Instant.now(),
                        downloadContent = { blob.getContent() },
                    )
                )
            }
            page = if (page.hasNextPage()) page.nextPage else null
        }
    }

    override fun objectExists(key: String): Boolean =
        storage.get(BlobId.of(bucket, key)) != null

    companion object {
        fun create(config: ReimportConfig): GcsBucketReader {
            val storage = if (config.gcsCredentialsJson != null) {
                val credentials = ServiceAccountCredentials
                    .fromStream(ByteArrayInputStream(config.gcsCredentialsJson.toByteArray(Charsets.UTF_8)))
                    .createScoped("https://www.googleapis.com/auth/cloud-platform")
                StorageOptions.newBuilder().setCredentials(credentials).build().service
            } else {
                StorageOptions.getDefaultInstance().service
            }
            return GcsBucketReader(storage, config.gcsBucket)
        }
    }
}
