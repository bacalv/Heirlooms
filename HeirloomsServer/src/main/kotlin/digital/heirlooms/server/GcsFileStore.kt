package digital.heirlooms.server

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.StorageOptions
import java.io.ByteArrayInputStream
import java.util.UUID

/**
 * [FileStore] implementation that stores uploaded files in a Google Cloud Storage bucket.
 *
 * Credentials are loaded from a service account JSON string (typically supplied via
 * the GCS_CREDENTIALS_JSON environment variable) so the key never needs to be written
 * to disk inside the container.
 */
class GcsFileStore(
    private val bucket: String,
    private val storage: com.google.cloud.storage.Storage,
    private val uuidProvider: () -> UUID = UUID::randomUUID,
) : FileStore {

    override fun save(bytes: ByteArray, mimeType: String): StorageKey {
        val key = "${uuidProvider()}.${mimeTypeToExtension(mimeType)}"
        val blobId = BlobId.of(bucket, key)
        val blobInfo = BlobInfo.newBuilder(blobId).setContentType(mimeType).build()
        storage.create(blobInfo, bytes)
        return StorageKey(key)
    }

    companion object {
        fun create(bucket: String, credentialsJson: String): GcsFileStore {
            val credentials = GoogleCredentials
                .fromStream(ByteArrayInputStream(credentialsJson.toByteArray(Charsets.UTF_8)))
                .createScoped("https://www.googleapis.com/auth/cloud-platform")
            val storage = StorageOptions.newBuilder()
                .setCredentials(credentials)
                .build()
                .service
            return GcsFileStore(bucket, storage)
        }
    }
}