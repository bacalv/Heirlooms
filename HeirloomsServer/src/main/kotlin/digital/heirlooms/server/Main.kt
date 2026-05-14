package digital.heirlooms.server

import digital.heirlooms.server.repository.auth.PostgresAuthRepository
import digital.heirlooms.server.repository.keys.PostgresKeyRepository
import digital.heirlooms.server.repository.storage.PostgresBlobRepository
import digital.heirlooms.server.repository.upload.PostgresUploadRepository
import digital.heirlooms.server.routes.buildApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.http4k.core.then
import org.http4k.server.Netty
import org.http4k.server.asServer
import java.util.Base64

fun main() {
    // Prefer environment variables (used in Docker) over application.properties
    val config = try {
        AppConfig.fromEnv().takeIf { it.dbUrl.isNotEmpty() } ?: AppConfig.load()
    } catch (e: Exception) {
        AppConfig.load()
    }

    val database = Database.create(config)
    database.runMigrations()
    println("Database migrations applied")

    val storage: FileStore = when (config.storageBackend) {
        StorageBackend.LOCAL -> {
            println("Storage: local directory '${config.storageDir}'")
            LocalFileStore.create(config.storageDir)
        }
        StorageBackend.S3 -> {
            require(config.s3Bucket.isNotEmpty())    { "s3.bucket must be set" }
            require(config.s3Region.isNotEmpty())    { "s3.region must be set" }
            require(config.s3AccessKey.isNotEmpty()) { "s3.access-key must be set" }
            require(config.s3SecretKey.isNotEmpty()) { "s3.secret-key must be set" }
            val endpoint = config.s3EndpointOverride.ifEmpty { "AWS" }
            println("Storage: S3 bucket '${config.s3Bucket}' via $endpoint")
            S3FileStore.create(
                config.s3Bucket, config.s3Region,
                config.s3AccessKey, config.s3SecretKey,
                config.s3EndpointOverride,
            )
        }
        StorageBackend.GCS -> {
            require(config.gcsBucket.isNotEmpty())          { "GCS_BUCKET must be set" }
            require(config.gcsCredentialsJson.isNotEmpty()) { "GCS_CREDENTIALS_JSON must be set" }
            println("Storage: GCS bucket '${config.gcsBucket}'")
            GcsFileStore.create(config.gcsBucket, config.gcsCredentialsJson)
        }
    }

    val exifScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    val exifService = ExifExtractionService(PostgresUploadRepository(database.dataSource), storage, exifScope)
    exifService.recoverPending()

    val cleanupService = PendingBlobsCleanupService(
        blobRepository = PostgresBlobRepository(database.dataSource),
        authRepository = PostgresAuthRepository(database.dataSource),
        keyRepository = PostgresKeyRepository(database.dataSource),
        storage = storage,
    )
    cleanupService.startPeriodicCleanup()
    println("PendingBlobsCleanupService started")

    val authSecret = if (config.authSecret.isNotEmpty())
        runCatching { Base64.getUrlDecoder().decode(config.authSecret) }.getOrElse { ByteArray(32) }
    else
        ByteArray(32)
    val app = buildApp(storage, database, previewDurationSeconds = config.previewDurationSeconds, authSecret = authSecret)
    if (config.apiKey.isNotEmpty()) println("Static API key auth enabled (development/test mode)")
    val server = corsFilter().then(
        sessionAuthFilter(PostgresAuthRepository(database.dataSource), config.apiKey).then(app)
    ).asServer(Netty(config.serverPort))
    server.start()
    println("HeirloomsServer running on port ${config.serverPort}")

    Runtime.getRuntime().addShutdownHook(Thread {
        println("Shutting down...")
        exifScope.cancel()
        server.stop()
    })
}
