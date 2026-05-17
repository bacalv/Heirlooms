package digital.heirlooms.server.config

import java.util.Properties

enum class StorageBackend { LOCAL, S3, GCS }

enum class TlockProvider { STUB, SIDECAR, DISABLED }

data class AppConfig(
    val serverPort: Int,
    val storageBackend: StorageBackend,
    val storageDir: String,
    val s3Bucket: String,
    val s3Region: String,
    val s3AccessKey: String,
    val s3SecretKey: String,
    val s3EndpointOverride: String,
    val gcsBucket: String,
    val gcsCredentialsJson: String,
    val dbUrl: String,
    val dbUser: String,
    val dbPassword: String,
    val apiKey: String,
    val previewDurationSeconds: Int = 15,
    val authSecret: String = "",
    val tlockProvider: TlockProvider = TlockProvider.DISABLED,
    /** Base64url-encoded 32-byte secret. Required when tlockProvider=STUB. */
    val tlockStubSecret: String = "",
    /** HTTP URL for the sidecar. Required when tlockProvider=SIDECAR. */
    val tlockSidecarUrl: String = "",
) {
    companion object {

        fun load(): AppConfig {
            // If Docker env vars are present, use them directly
            if (System.getenv("DB_URL") != null || System.getenv("STORAGE_BACKEND") != null) {
                return fromEnv()
            }
            return loadFrom("application.properties")
        }

        private fun loadFrom(resource: String): AppConfig {
            val props = Properties()
            val stream = AppConfig::class.java.classLoader.getResourceAsStream(resource)
                ?: error("$resource not found on classpath")
            stream.use { props.load(it) }
            return fromProperties(props)
        }

        fun fromEnv(): AppConfig {
            fun env(key: String) = System.getenv(key)?.trim()

            val backend = when (env("STORAGE_BACKEND")?.uppercase()) {
                "S3"    -> StorageBackend.S3
                "GCS"   -> StorageBackend.GCS
                "LOCAL" -> StorageBackend.LOCAL
                else    -> StorageBackend.LOCAL
            }

            return AppConfig(
                serverPort         = env("SERVER_PORT")?.toIntOrNull() ?: 8080,
                storageBackend     = backend,
                storageDir         = env("STORAGE_DIR") ?: "uploads",
                s3Bucket           = env("S3_BUCKET")            ?: "",
                s3Region           = env("S3_REGION")            ?: "us-east-1",
                s3AccessKey        = env("S3_ACCESS_KEY")        ?: "",
                s3SecretKey        = env("S3_SECRET_KEY")        ?: "",
                s3EndpointOverride = env("S3_ENDPOINT_OVERRIDE") ?: "",
                gcsBucket          = env("GCS_BUCKET")           ?: "",
                gcsCredentialsJson = env("GCS_CREDENTIALS_JSON") ?: "",
                dbUrl              = env("DB_URL")               ?: "",
                dbUser             = env("DB_USER")              ?: "",
                dbPassword         = env("DB_PASSWORD")          ?: "",
                apiKey                 = env("API_KEY")                       ?: "",
                previewDurationSeconds = env("PREVIEW_DURATION_SECONDS")?.toIntOrNull() ?: 15,
                authSecret             = env("AUTH_SECRET")                   ?: "",
                tlockProvider          = parseTlockProvider(env("TLOCK_PROVIDER")),
                tlockStubSecret        = env("TLOCK_STUB_SECRET")             ?: "",
                tlockSidecarUrl        = env("TLOCK_SIDECAR_URL")             ?: "",
            )
        }

        private fun fromProperties(props: Properties): AppConfig {
            fun prop(key: String) = props.getProperty(key)?.trim()

            val backend = when (prop("storage.backend")?.uppercase()) {
                "S3"    -> StorageBackend.S3
                "GCS"   -> StorageBackend.GCS
                "LOCAL" -> StorageBackend.LOCAL
                else    -> StorageBackend.LOCAL
            }

            return AppConfig(
                serverPort         = prop("server.port")?.toIntOrNull() ?: 8080,
                storageBackend     = backend,
                storageDir         = prop("storage.dir")              ?: "uploads",
                s3Bucket           = prop("s3.bucket")                ?: "",
                s3Region           = prop("s3.region")                ?: "us-east-1",
                s3AccessKey        = prop("s3.access-key")            ?: "",
                s3SecretKey        = prop("s3.secret-key")            ?: "",
                s3EndpointOverride = prop("s3.endpoint-override")     ?: "",
                gcsBucket          = prop("gcs.bucket")               ?: "",
                gcsCredentialsJson = prop("gcs.credentials-json")     ?: "",
                dbUrl              = prop("db.url")                   ?: "",
                dbUser             = prop("db.user")                  ?: "",
                dbPassword         = prop("db.password")              ?: "",
                apiKey                 = prop("api.key")                            ?: "",
                previewDurationSeconds = prop("preview.duration.seconds")?.toIntOrNull() ?: 15,
                authSecret             = prop("auth.secret")                         ?: "",
                tlockProvider          = parseTlockProvider(prop("tlock.provider")),
                tlockStubSecret        = prop("tlock.stub.secret")                   ?: "",
                tlockSidecarUrl        = prop("tlock.sidecar.url")                   ?: "",
            )
        }

        private fun parseTlockProvider(value: String?): TlockProvider = when (value?.uppercase()) {
            "STUB"     -> TlockProvider.STUB
            "SIDECAR"  -> TlockProvider.SIDECAR
            else       -> TlockProvider.DISABLED
        }
    }
}
