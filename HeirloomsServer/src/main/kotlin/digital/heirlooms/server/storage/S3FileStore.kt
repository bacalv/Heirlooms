package digital.heirlooms.server.storage

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.core.async.AsyncResponseTransformer
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.net.URI
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ExecutionException

/**
 * [FileStore] implementation that stores uploaded files in an Amazon S3 bucket
 * (or any S3-compatible service such as MinIO) using the AWS SDK v2 async client.
 *
 * Accepts an optional [endpointOverride] to redirect requests to a local MinIO
 * instance during testing — set `s3.endpoint-override` in application.properties.
 */
class S3FileStore(
    private val bucket: String,
    private val client: S3AsyncClient,
    private val presigner: S3Presigner,
    private val uuidProvider: () -> UUID = UUID::randomUUID,
) : FileStore, DirectUploadSupport {

    override fun save(bytes: ByteArray, mimeType: String): StorageKey {
        val key = "${uuidProvider()}.${mimeTypeToExtension(mimeType)}"

        val request = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType(mimeType)
            .contentLength(bytes.size.toLong())
            .build()

        try {
            client.putObject(request, AsyncRequestBody.fromBytes(bytes)).get()
        } catch (e: ExecutionException) {
            throw RuntimeException("S3 upload failed for key '$key': ${e.cause?.message}", e.cause)
        }

        return StorageKey(key)
    }

    override fun saveWithKey(bytes: ByteArray, key: StorageKey, mimeType: String) {
        val request = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key.value)
            .contentType(mimeType)
            .contentLength(bytes.size.toLong())
            .build()
        try {
            client.putObject(request, AsyncRequestBody.fromBytes(bytes)).get()
        } catch (e: ExecutionException) {
            throw RuntimeException("S3 upload failed for key '${key.value}': ${e.cause?.message}", e.cause)
        }
    }

    override fun delete(key: StorageKey) {
        val request = DeleteObjectRequest.builder().bucket(bucket).key(key.value).build()
        try {
            client.deleteObject(request).get()
        } catch (e: ExecutionException) {
            throw RuntimeException("S3 delete failed for key '${key.value}': ${e.cause?.message}", e.cause)
        }
    }

    override fun get(key: StorageKey): ByteArray {
        val request = GetObjectRequest.builder()
            .bucket(bucket)
            .key(key.value)
            .build()
        return try {
            client.getObject(request, AsyncResponseTransformer.toBytes()).get().asByteArray()
        } catch (e: ExecutionException) {
            throw RuntimeException("S3 download failed for key '${key.value}': ${e.cause?.message}", e.cause)
        }
    }

    override fun prepareUpload(mimeType: String): PreparedUpload {
        val key = "uploads/${uuidProvider()}.${mimeTypeToExtension(mimeType)}"
        val presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(Duration.ofHours(1))
            .putObjectRequest { it.bucket(bucket).key(key) }
            .build()
        val url = presigner.presignPutObject(presignRequest).url().toString()
        return PreparedUpload(StorageKey(key), url)
    }

    override fun generateReadUrl(key: StorageKey): String {
        val presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofHours(1))
            .getObjectRequest { it.bucket(bucket).key(key.value) }
            .build()
        return presigner.presignGetObject(presignRequest).url().toString()
    }

    companion object {
        fun create(
            bucket: String,
            region: String,
            accessKey: String,
            secretKey: String,
            endpointOverride: String = "",
        ): S3FileStore {
            val credentials = AwsBasicCredentials.create(accessKey, secretKey)
            val credProvider = StaticCredentialsProvider.create(credentials)
            val awsRegion = Region.of(region)

            val clientBuilder = S3AsyncClient.builder()
                .region(awsRegion)
                .credentialsProvider(credProvider)
                .forcePathStyle(true)
            val s3Config = S3Configuration.builder().pathStyleAccessEnabled(true).build()
            val presignerBuilder = S3Presigner.builder()
                .region(awsRegion)
                .credentialsProvider(credProvider)
                .serviceConfiguration(s3Config)

            if (endpointOverride.isNotEmpty()) {
                val uri = URI.create(endpointOverride)
                clientBuilder.endpointOverride(uri)
                presignerBuilder.endpointOverride(uri)
            }

            return S3FileStore(bucket, clientBuilder.build(), presignerBuilder.build())
        }
    }
}
