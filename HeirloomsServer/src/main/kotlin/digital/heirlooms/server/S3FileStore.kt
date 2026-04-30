package digital.heirlooms.server

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.net.URI
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
    private val uuidProvider: () -> UUID = UUID::randomUUID,
) : FileStore {

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

    companion object {
        fun create(
            bucket: String,
            region: String,
            accessKey: String,
            secretKey: String,
            endpointOverride: String = "",
        ): S3FileStore {
            val credentials = AwsBasicCredentials.create(accessKey, secretKey)
            val builder = S3AsyncClient.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .forcePathStyle(true) // required for MinIO

            if (endpointOverride.isNotEmpty()) {
                builder.endpointOverride(URI.create(endpointOverride))
            }

            return S3FileStore(bucket, builder.build())
        }
    }
}
