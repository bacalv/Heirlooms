package digital.heirlooms.server

import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.HttpMethod
import com.google.cloud.storage.Storage.SignUrlOption
import com.google.cloud.storage.StorageOptions
import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * [FileStore] implementation that stores uploaded files in a Google Cloud Storage bucket.
 *
 * Credentials are loaded from a service account JSON string (typically supplied via
 * the GCS_CREDENTIALS_JSON environment variable) so the key never needs to be written
 * to disk inside the container.
 *
 * Also implements [DirectUploadSupport] so the mobile app can upload large files
 * directly to GCS via a signed URL, bypassing the Cloud Run 32 MB request limit.
 */
class GcsFileStore(
    private val bucket: String,
    private val storage: com.google.cloud.storage.Storage,
    private val credentials: ServiceAccountCredentials,
    private val uuidProvider: () -> UUID = UUID::randomUUID,
) : FileStore, DirectUploadSupport {

    override fun save(bytes: ByteArray, mimeType: String): StorageKey {
        val key = "${uuidProvider()}.${mimeTypeToExtension(mimeType)}"
        val blobId = BlobId.of(bucket, key)
        val blobInfo = BlobInfo.newBuilder(blobId).setContentType(mimeType).build()
        storage.create(blobInfo, bytes)
        return StorageKey(key)
    }

    override fun saveWithKey(bytes: ByteArray, key: StorageKey, mimeType: String) {
        val blobId = BlobId.of(bucket, key.value)
        val blobInfo = BlobInfo.newBuilder(blobId).setContentType(mimeType).build()
        storage.create(blobInfo, bytes)
    }

    override fun get(key: StorageKey): ByteArray = storage.readAllBytes(BlobId.of(bucket, key.value))

    override fun generateReadUrl(key: StorageKey): String {
        val blobInfo = BlobInfo.newBuilder(BlobId.of(bucket, key.value)).build()
        return storage.signUrl(
            blobInfo,
            60L, TimeUnit.MINUTES,
            SignUrlOption.withV4Signature(),
            SignUrlOption.signWith(credentials),
        ).toString()
    }

    override fun prepareUpload(mimeType: String): PreparedUpload {
        val key = "${uuidProvider()}.${mimeTypeToExtension(mimeType)}"
        val blobInfo = BlobInfo.newBuilder(BlobId.of(bucket, key))
            .setContentType(mimeType)
            .build()
        val url = storage.signUrl(
            blobInfo,
            15L, TimeUnit.MINUTES,
            SignUrlOption.httpMethod(HttpMethod.PUT),
            SignUrlOption.withContentType(),
            SignUrlOption.withV4Signature(),
            SignUrlOption.signWith(credentials),
        )
        return PreparedUpload(StorageKey(key), url.toString())
    }

    companion object {
        fun create(bucket: String, credentialsJson: String): GcsFileStore {
            val credentials = ServiceAccountCredentials
                .fromStream(ByteArrayInputStream(credentialsJson.toByteArray(Charsets.UTF_8)))
            val storage = StorageOptions.newBuilder()
                .setCredentials(credentials.createScoped("https://www.googleapis.com/auth/cloud-platform"))
                .build()
                .service
            return GcsFileStore(bucket, storage, credentials)
        }
    }
}
