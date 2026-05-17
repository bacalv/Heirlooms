package digital.heirlooms.tools.apiclient

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Standalone Heirlooms API client for the full capsule lifecycle.
 *
 * Structured for extensibility: Phase 2 (tlock + Shamir + client-side crypto)
 * can be added by overriding or extending the [sealCapsule] step and injecting
 * a crypto delegate without touching authentication or upload logic.
 *
 * Authentication:
 *   Heirlooms uses a two-step challenge/login flow derived from SRP:
 *   1. POST /api/auth/challenge { username } → { auth_salt }
 *   2. POST /api/auth/login { username, auth_key } → { session_token }
 *   The auth_key is the raw 32-byte key whose SHA-256 equals the auth_verifier
 *   stored in the database. For Phase 1 we send the raw auth_key bytes directly.
 *
 * Upload flow (E2EE-aware, Phase 1 uses public storage class):
 *   1. POST /api/content/uploads/initiate { mimeType, storage_class: "public" } → { storageKey, uploadUrl }
 *   2. PUT  <uploadUrl>  (bytes directly to GCS signed URL)
 *   3. POST /api/content/uploads/confirm { storageKey, mimeType, fileSize } → 201
 *   Then GET /api/content/uploads to resolve the upload ID by storageKey.
 *
 * Capsule flow:
 *   1. POST /api/capsules { shape, unlock_at, recipients, upload_ids, message } → 201 { id }
 *   2. POST /api/capsules/{id}/seal → 200 { id, shape: "sealed", state: "sealed" }
 *   3. GET  /api/capsules → 200 { capsules: [...] }
 *   4. GET  /api/capsules/{id} → 200 { full detail }
 */
class HeirloomsClient(
    private val config: ClientConfig,
    private val http: OkHttpClient = defaultHttpClient(),
) {
    private val json = ObjectMapper()
        .registerModule(JavaTimeModule())

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    private val OCTET_MEDIA = "application/octet-stream".toMediaType()

    private var sessionToken: String? = null

    // -------------------------------------------------------------------------
    // Step 1: Authenticate
    // -------------------------------------------------------------------------

    /**
     * Authenticates using the two-step challenge/login flow.
     * Stores the session token for all subsequent requests.
     * Returns the session token string.
     */
    fun authenticate(): String {
        log("authenticate", "username=${config.username} base_url=${config.baseUrl}")

        // Step 1a: challenge — fetch auth_salt (we don't actually use it in Phase 1
        // since the client supplies the raw auth_key, not a derived verifier).
        // We still call it to follow the correct protocol sequence and verify the
        // server responds correctly.
        val challengeResp = post(
            path = "/api/auth/challenge",
            body = mapOf("username" to config.username),
            authenticated = false,
        )
        require(challengeResp.code == 200) {
            "challenge failed: HTTP ${challengeResp.code} — ${challengeResp.bodyString()}"
        }
        val challengeNode = parseJson(challengeResp.bodyString())
        val authSalt = challengeNode.get("auth_salt")?.asText()
        log("authenticate", "challenge OK — auth_salt=${authSalt?.take(8)}…")

        // Step 1b: login — send auth_key (raw bytes, base64url-encoded)
        val authKeyBytes = config.authKeyBytes()
        val authKeyB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(authKeyBytes)
        val loginResp = post(
            path = "/api/auth/login",
            body = mapOf("username" to config.username, "auth_key" to authKeyB64),
            authenticated = false,
        )
        require(loginResp.code == 200) {
            "login failed: HTTP ${loginResp.code} — ${loginResp.bodyString()}"
        }
        val loginNode = parseJson(loginResp.bodyString())
        val token = loginNode.get("session_token")?.asText()
            ?: error("login response missing session_token")
        sessionToken = token
        val userId = loginNode.get("user_id")?.asText()
        log("authenticate", "login OK — user_id=$userId session_token=${token.take(8)}…")
        return token
    }

    // -------------------------------------------------------------------------
    // Step 2: Upload a file
    // -------------------------------------------------------------------------

    /**
     * Uploads [fileBytes] with [mimeType] using the initiate → PUT → confirm flow.
     *
     * Phase 1 uses storage_class "public" (plaintext bytes sent to GCS).
     * Phase 2 will inject an encryption step between initiate and PUT.
     *
     * Returns the upload ID (UUID string) from the upload record.
     *
     * Uses POST /api/content/upload (direct upload) so the flow works with
     * local storage as well as GCS. The initiate/PUT/confirm flow is GCS-only.
     */
    fun uploadFile(fileBytes: ByteArray, mimeType: String = "image/jpeg"): String {
        log("uploadFile", "size=${fileBytes.size} mimeType=$mimeType")

        val uploadResp = postBinary(
            path = "/api/content/upload",
            bytes = fileBytes,
            contentType = mimeType,
        )
        require(uploadResp.code == 201) {
            "upload failed: HTTP ${uploadResp.code} — ${uploadResp.bodyString()}"
        }
        val uploadNode = parseJson(uploadResp.bodyString())
        val uploadId = uploadNode.get("id")?.asText()
            ?: error("upload response missing id")
        val storageKey = uploadNode.get("storageKey")?.asText() ?: "?"
        log("uploadFile", "OK — upload_id=$uploadId storageKey=${storageKey.take(16)}…")
        return uploadId
    }

    // -------------------------------------------------------------------------
    // Step 3: Create a capsule
    // -------------------------------------------------------------------------

    /**
     * Creates an open capsule with the given parameters.
     * Returns the capsule ID (UUID string).
     *
     * [recipients] — list of display names (freeform strings in the Heirlooms model)
     * [uploadIds] — list of upload UUIDs to include
     * [message] — plaintext message (Phase 1: sent as-is; Phase 2: client-encrypted)
     * [unlockAtIso] — ISO-8601 timestamp with timezone, e.g. "2030-01-01T00:00:00+00:00"
     */
    fun createCapsule(
        recipients: List<String>,
        uploadIds: List<String>,
        message: String,
        unlockAtIso: String,
    ): String {
        log("createCapsule", "recipients=$recipients uploadIds=$uploadIds unlockAt=$unlockAtIso")

        val createResp = post(
            path = "/api/capsules",
            body = mapOf(
                "shape" to "open",
                "unlock_at" to unlockAtIso,
                "recipients" to recipients,
                "upload_ids" to uploadIds,
                "message" to message,
            ),
        )
        require(createResp.code == 201) {
            "create capsule failed: HTTP ${createResp.code} — ${createResp.bodyString()}"
        }
        val createNode = parseJson(createResp.bodyString())
        val capsuleId = createNode.get("id")?.asText()
            ?: error("create capsule response missing id")
        log("createCapsule", "OK — capsule_id=$capsuleId")
        return capsuleId
    }

    // -------------------------------------------------------------------------
    // Step 4: Add upload to capsule (PATCH capsule with updated upload_ids)
    // -------------------------------------------------------------------------

    /**
     * Adds [uploadId] to the capsule [capsuleId] by patching upload_ids.
     *
     * Note: the v0.56 API adds uploads at creation time (POST /api/capsules body
     * includes upload_ids). This step demonstrates adding additional uploads via
     * PATCH /api/capsules/{id} — useful for Phase 2 where capsule items may be
     * added incrementally. If the upload was already included at creation, the
     * PATCH is a no-op (same list).
     *
     * ARCH-015 does not define a separate POST /api/capsules/{id}/items endpoint —
     * uploads are managed via PATCH /api/capsules/{id} with the full upload_ids list.
     */
    fun addUploadToCapsule(capsuleId: String, uploadId: String) {
        log("addUploadToCapsule", "capsule_id=$capsuleId upload_id=$uploadId")

        // Fetch current state so we can include all existing upload_ids
        val current = getCapsule(capsuleId)
        val existingIds = current.get("uploads")
            ?.map { it.get("id")?.asText() }
            ?.filterNotNull()
            ?: emptyList()

        val allIds = (existingIds + uploadId).distinct()

        val patchResp = patch(
            path = "/api/capsules/$capsuleId",
            body = mapOf("upload_ids" to allIds),
        )
        require(patchResp.code == 200) {
            "patch capsule failed: HTTP ${patchResp.code} — ${patchResp.bodyString()}"
        }
        log("addUploadToCapsule", "OK — upload_ids=$allIds")
    }

    // -------------------------------------------------------------------------
    // Step 5: Seal a capsule
    // -------------------------------------------------------------------------

    /**
     * Seals the capsule [capsuleId].
     *
     * If [connectionId] and [wrappedCapsuleKey] are provided (M11 path), sends a
     * recipient_keys block so the server can store the per-recipient ECDH envelope.
     * Otherwise falls back to a no-body POST (pre-M11 backwards-compat path).
     *
     * Returns the full capsule detail JSON node after sealing.
     */
    fun sealCapsule(
        capsuleId: String,
        connectionId: String? = null,
        wrappedCapsuleKey: String? = null,
    ): JsonNode {
        log("sealCapsule", "capsule_id=$capsuleId m11=${connectionId != null}")

        val body: Any? = if (connectionId != null && wrappedCapsuleKey != null) {
            mapOf(
                "recipient_keys" to listOf(
                    mapOf(
                        "connection_id" to connectionId,
                        "wrapped_capsule_key" to wrappedCapsuleKey,
                        "capsule_key_format" to "capsule-ecdh-aes256gcm-v1",
                    )
                )
            )
        } else null

        val sealResp = post(path = "/api/capsules/$capsuleId/seal", body = body)
        require(sealResp.code == 200) {
            "seal capsule failed: HTTP ${sealResp.code} — ${sealResp.bodyString()}"
        }
        val detail = parseJson(sealResp.bodyString())
        val state = detail.get("state")?.asText()
        log("sealCapsule", "OK — state=$state")
        return detail
    }

    // -------------------------------------------------------------------------
    // Step 6: List capsules
    // -------------------------------------------------------------------------

    /**
     * Lists capsules for the authenticated user.
     * Returns the parsed JSON node with the "capsules" array.
     */
    fun listCapsules(state: String = "open,sealed"): JsonNode {
        log("listCapsules", "state=$state")

        val listResp = get("/api/capsules?state=${state}")
        require(listResp.code == 200) {
            "list capsules failed: HTTP ${listResp.code} — ${listResp.bodyString()}"
        }
        val node = parseJson(listResp.bodyString())
        val count = node.get("capsules")?.size() ?: 0
        log("listCapsules", "OK — $count capsule(s) returned")
        return node
    }

    // -------------------------------------------------------------------------
    // Step 7: Retrieve a capsule
    // -------------------------------------------------------------------------

    /**
     * Retrieves the full detail for a single capsule by ID.
     * Returns the parsed JSON detail node.
     */
    fun getCapsule(capsuleId: String): JsonNode {
        log("getCapsule", "capsule_id=$capsuleId")

        val resp = get("/api/capsules/$capsuleId")
        require(resp.code == 200) {
            "get capsule failed: HTTP ${resp.code} — ${resp.bodyString()}"
        }
        val detail = parseJson(resp.bodyString())
        val state = detail.get("state")?.asText()
        val uploadCount = detail.get("uploads")?.size() ?: 0
        log("getCapsule", "OK — state=$state uploads=$uploadCount")
        return detail
    }

    // -------------------------------------------------------------------------
    // HTTP helpers
    // -------------------------------------------------------------------------

    private fun post(path: String, body: Any?, authenticated: Boolean = true): Response {
        val reqBody = if (body != null) {
            json.writeValueAsString(body).toRequestBody(JSON_MEDIA)
        } else {
            "".toRequestBody(JSON_MEDIA)
        }
        val req = Request.Builder()
            .url("${config.baseUrl}$path")
            .post(reqBody)
            .apply { if (authenticated) addHeader("X-Api-Key", requireToken()) }
            .build()
        return http.newCall(req).execute()
    }

    private fun patch(path: String, body: Any): Response {
        val reqBody = json.writeValueAsString(body).toRequestBody(JSON_MEDIA)
        val req = Request.Builder()
            .url("${config.baseUrl}$path")
            .patch(reqBody)
            .addHeader("X-Api-Key", requireToken())
            .build()
        return http.newCall(req).execute()
    }

    private fun get(path: String): Response {
        val req = Request.Builder()
            .url("${config.baseUrl}$path")
            .get()
            .addHeader("X-Api-Key", requireToken())
            .build()
        return http.newCall(req).execute()
    }

    private fun postBinary(path: String, bytes: ByteArray, contentType: String): Response {
        val reqBody = bytes.toRequestBody(contentType.toMediaType())
        val req = Request.Builder()
            .url("${config.baseUrl}$path")
            .post(reqBody)
            .addHeader("X-Api-Key", requireToken())
            .build()
        return http.newCall(req).execute()
    }

    private fun requireToken(): String =
        sessionToken ?: error("Not authenticated — call authenticate() first")

    private fun parseJson(s: String): JsonNode =
        json.readTree(s) ?: error("Empty JSON response")

    private fun Response.bodyString(): String = body?.string() ?: ""

    private fun log(step: String, msg: String) {
        println("[api-client] [$step] $msg")
    }

    companion object {
        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
