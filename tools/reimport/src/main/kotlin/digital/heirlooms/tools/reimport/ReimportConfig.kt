package digital.heirlooms.tools.reimport

data class ReimportConfig(
    val dbUrl: String,
    val dbUser: String,
    val dbPassword: String,
    val gcsBucket: String,
    val gcsCredentialsJson: String?,
) {
    companion object {
        fun fromEnv(): ReimportConfig {
            fun env(key: String) = System.getenv(key)?.trim()
            return ReimportConfig(
                dbUrl              = env("DB_URL")               ?: error("DB_URL is required"),
                dbUser             = env("DB_USER")              ?: error("DB_USER is required"),
                dbPassword         = env("DB_PASSWORD")          ?: error("DB_PASSWORD is required"),
                gcsBucket          = env("GCS_BUCKET")           ?: error("GCS_BUCKET is required"),
                gcsCredentialsJson = env("GCS_CREDENTIALS_JSON"),
            )
        }
    }
}
