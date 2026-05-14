package digital.heirlooms.server

import digital.heirlooms.server.config.AppConfig
import digital.heirlooms.server.config.StorageBackend
import digital.heirlooms.server.filters.corsFilter
import digital.heirlooms.server.filters.sessionAuthFilter
import digital.heirlooms.server.repository.auth.PostgresAuthRepository
import digital.heirlooms.server.repository.keys.PostgresKeyRepository
import digital.heirlooms.server.repository.storage.PostgresBlobRepository
import digital.heirlooms.server.repository.upload.PostgresUploadRepository
import digital.heirlooms.server.routes.buildApp
import digital.heirlooms.server.service.cleanup.PendingBlobsCleanupService
import digital.heirlooms.server.service.upload.ExifExtractionService
import digital.heirlooms.server.storage.FileStore
import digital.heirlooms.server.storage.GcsFileStore
import digital.heirlooms.server.storage.LocalFileStore
import digital.heirlooms.server.storage.S3FileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.http4k.core.then
import org.http4k.server.Netty
import org.http4k.server.asServer
import org.slf4j.LoggerFactory
import java.util.Base64

private val logger = LoggerFactory.getLogger("digital.heirlooms.server.Main")

fun main() {
    // Prefer environment variables (used in Docker) over application.properties
    val config = try {
        AppConfig.fromEnv().takeIf { it.dbUrl.isNotEmpty() } ?: AppConfig.load()
    } catch (e: Exception) {
        AppConfig.load()
    }

    val database = Database.create(config)
    database.runMigrations()
    logger.info("Database migrations applied")

    val storage: FileStore = when (config.storageBackend) {
        StorageBackend.LOCAL -> {
            logger.info("Storage: local directory '{}'", config.storageDir)
            LocalFileStore.create(config.storageDir)
        }
        StorageBackend.S3 -> {
            require(config.s3Bucket.isNotEmpty())    { "s3.bucket must be set" }
            require(config.s3Region.isNotEmpty())    { "s3.region must be set" }
            require(config.s3AccessKey.isNotEmpty()) { "s3.access-key must be set" }
            require(config.s3SecretKey.isNotEmpty()) { "s3.secret-key must be set" }
            val endpoint = config.s3EndpointOverride.ifEmpty { "AWS" }
            logger.info("Storage: S3 bucket '{}' via {}", config.s3Bucket, endpoint)
            S3FileStore.create(
                config.s3Bucket, config.s3Region,
                config.s3AccessKey, config.s3SecretKey,
                config.s3EndpointOverride,
            )
        }
        StorageBackend.GCS -> {
            require(config.gcsBucket.isNotEmpty())          { "GCS_BUCKET must be set" }
            require(config.gcsCredentialsJson.isNotEmpty()) { "GCS_CREDENTIALS_JSON must be set" }
            logger.info("Storage: GCS bucket '{}'", config.gcsBucket)
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
    logger.info("PendingBlobsCleanupService started")

    val authSecret = if (config.authSecret.isNotEmpty()) {
        runCatching { Base64.getUrlDecoder().decode(config.authSecret) }.getOrElse {
            logger.error("SECURITY: AUTH_SECRET env var is set but is not valid Base64 — falling back to all-zeros key. Set AUTH_SECRET to a securely-generated 32-byte base64url value.")
            ByteArray(32)
        }
    } else {
        logger.error("SECURITY: AUTH_SECRET env var is not set. The fake-salt HMAC key is all zeros. This is a HIGH security finding — generate a random secret and set AUTH_SECRET before production use.")
        ByteArray(32)
    }

    // Parse allowed CORS origins from CORS_ALLOWED_ORIGINS (comma-separated).
    // If empty/unset, the filter falls back to Access-Control-Allow-Origin: * (dev only).
    val corsAllowedOrigins: Set<String> = System.getenv("CORS_ALLOWED_ORIGINS")
        ?.takeIf { it.isNotBlank() }
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?.toSet()
        ?: emptySet()
    if (corsAllowedOrigins.isEmpty()) {
        logger.warn("SECURITY: CORS_ALLOWED_ORIGINS is not set — using Access-Control-Allow-Origin: * (development mode). Set this env var to lock CORS in production.")
    } else {
        logger.info("CORS allowed origins: {}", corsAllowedOrigins)
    }

    val app = buildApp(storage, database, previewDurationSeconds = config.previewDurationSeconds, authSecret = authSecret)
    if (config.apiKey.isNotEmpty()) logger.info("Static API key auth enabled (development/test mode)")
    val server = corsFilter(corsAllowedOrigins).then(
        sessionAuthFilter(PostgresAuthRepository(database.dataSource), config.apiKey).then(app)
    ).asServer(Netty(config.serverPort))
    server.start()
    logger.info("HeirloomsServer running on port {}", config.serverPort)

    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("Shutting down...")
        exifScope.cancel()
        server.stop()
    })
}
