package digital.heirlooms.tools.apiclient

import java.io.File
import java.util.Properties

/**
 * Configuration for the Heirlooms API client.
 *
 * Loaded from (in priority order):
 *  1. System properties (-Dprop=value on the JVM command line)
 *  2. Environment variables
 *  3. A config.properties file in the working directory
 *
 * Required properties:
 *  - heirlooms.username    (env: HEIRLOOMS_USERNAME)
 *  - heirlooms.auth_key    (env: HEIRLOOMS_AUTH_KEY) — 32-byte hex string; SHA-256(auth_key) == auth_verifier stored on server
 *
 * Optional properties:
 *  - heirlooms.base_url    (env: HEIRLOOMS_BASE_URL)   default: https://test.api.heirlooms.digital
 *  - heirlooms.api_key     (env: HEIRLOOMS_API_KEY)    default: ""  (not needed for password login)
 */
data class ClientConfig(
    val baseUrl: String,
    val username: String,
    val authKeyHex: String,
) {
    companion object {
        private const val DEFAULT_BASE_URL = "https://test.api.heirlooms.digital"

        fun load(@Suppress("UNUSED_PARAMETER") args: Array<String>): ClientConfig {
            val fileProps = loadFileProperties()

            fun resolve(sysProp: String, envVar: String, filePropKey: String): String? =
                System.getProperty(sysProp)
                    ?: System.getenv(envVar)
                    ?: fileProps.getProperty(filePropKey)

            val baseUrl = resolve("heirlooms.base_url", "HEIRLOOMS_BASE_URL", "heirlooms.base_url")
                ?: DEFAULT_BASE_URL

            val username = resolve("heirlooms.username", "HEIRLOOMS_USERNAME", "heirlooms.username")
                ?: error(
                    "heirlooms.username is required. " +
                    "Set it via -Dheirlooms.username=X, env HEIRLOOMS_USERNAME=X, or config.properties."
                )

            val authKeyHex = resolve("heirlooms.auth_key", "HEIRLOOMS_AUTH_KEY", "heirlooms.auth_key")
                ?: error(
                    "heirlooms.auth_key is required. " +
                    "Set it via -Dheirlooms.auth_key=X, env HEIRLOOMS_AUTH_KEY=X, or config.properties."
                )

            return ClientConfig(
                baseUrl = baseUrl.trimEnd('/'),
                username = username,
                authKeyHex = authKeyHex,
            )
        }

        private fun loadFileProperties(): Properties {
            val props = Properties()
            val file = File("config.properties")
            if (file.exists()) {
                file.inputStream().use { props.load(it) }
            }
            return props
        }
    }

    /** Decodes authKeyHex to raw bytes. */
    fun authKeyBytes(): ByteArray {
        val hex = authKeyHex.trim()
        require(hex.length == 64) {
            "auth_key must be a 64-character hex string (32 bytes). Got ${hex.length} chars."
        }
        return ByteArray(32) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
