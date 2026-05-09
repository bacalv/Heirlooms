package digital.heirlooms.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.os.Build
import android.util.Base64
import android.util.Size
import digital.heirlooms.crypto.VaultCrypto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class Uploader(
    private val httpClient: OkHttpClient,
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    private val initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
) {

    sealed class UploadResult {
        abstract val message: String

        data class Success(
            override val message: String,
            val httpCode: Int,
            val attempts: Int,
        ) : UploadResult()

        data class Failure(
            override val message: String,
            val httpCode: Int = NO_HTTP_CODE,
            val attempts: Int = 0,
        ) : UploadResult()
    }

    companion object {
        const val NO_HTTP_CODE = -1
        const val DEFAULT_MAX_ATTEMPTS = 3
        const val DEFAULT_INITIAL_DELAY_MS = 1_000L
        private const val FALLBACK_MIME_TYPE = "application/octet-stream"

        fun isValidEndpoint(endpoint: String?): Boolean {
            val trimmed = endpoint?.trim() ?: return false
            return trimmed.startsWith("http://") || trimmed.startsWith("https://")
        }

        fun resolveMimeType(mimeType: String?): String =
            mimeType?.trim()?.takeIf { it.isNotEmpty() } ?: FALLBACK_MIME_TYPE

        fun isRetryable(result: UploadResult): Boolean = when (result) {
            is UploadResult.Failure -> result.httpCode == NO_HTTP_CODE || result.httpCode >= 500
            is UploadResult.Success -> false
        }

        private fun backoffSequence(initialDelayMs: Long): Sequence<Long> =
            generateSequence(initialDelayMs) { it * 2 }

        fun parseJsonStringField(json: String, key: String): String? =
            Regex(""""$key"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1)

        fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }

        fun sha256Hex(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { stream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (stream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }

    fun upload(endpoint: String?, fileBytes: ByteArray?, mimeType: String, apiKey: String? = null): UploadResult {
        if (!isValidEndpoint(endpoint)) return UploadResult.Failure("No endpoint configured in config.properties")
        if (fileBytes == null || fileBytes.isEmpty()) return UploadResult.Failure("File is empty")

        val attempts = (1..maxAttempts)
        val delays = sequenceOf(0L) + backoffSequence(initialDelayMs)

        return attempts.asSequence().zip(delays).fold<Pair<Int, Long>, UploadResult>(
            initial = UploadResult.Failure("Upload not attempted"),
        ) { lastResult, (attempt, delayMs) ->
            if (attempt > 1 && !isRetryable(lastResult)) return lastResult
            if (delayMs > 0) Thread.sleep(delayMs)
            val result = attemptUpload(endpoint!!, fileBytes, mimeType, attempt, apiKey)
            if (!isRetryable(result)) return result
            result
        }
    }

    private fun attemptUpload(
        endpoint: String,
        fileBytes: ByteArray,
        mimeType: String,
        attempt: Int,
        apiKey: String?,
    ): UploadResult {
        val request = Request.Builder()
            .url(endpoint)
            .post(fileBytes.toRequestBody(mimeType.toMediaType()))
            .header("Content-Type", mimeType)
            .apply { if (!apiKey.isNullOrBlank()) header("X-Api-Key", apiKey) }
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                val code = response.code
                if (response.isSuccessful) UploadResult.Success("Uploaded successfully ($code)", code, attempt)
                else UploadResult.Failure("Upload failed: HTTP $code", code, attempt)
            }
        } catch (e: IOException) {
            UploadResult.Failure("Upload error: ${e.message}", NO_HTTP_CODE, attempt)
        }
    }

    /**
     * Three-step signed URL upload:
     * 1. POST /uploads/prepare  → signed GCS PUT URL
     * 2. PUT {signedUrl}        → bytes direct to GCS (onProgress fires here)
     * 3. POST /uploads/confirm  → record in DB (onConfirming fires just before)
     */
    fun uploadViaSigned(
        baseUrl: String?,
        fileBytes: ByteArray?,
        mimeType: String,
        apiKey: String? = null,
        onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)? = null,
        onConfirming: (() -> Unit)? = null,
    ): UploadResult {
        if (!isValidEndpoint(baseUrl)) return UploadResult.Failure("No endpoint configured")
        if (fileBytes == null || fileBytes.isEmpty()) return UploadResult.Failure("File is empty")

        val base = baseUrl!!.trimEnd('/')

        // Step 1: prepare
        val prepareRequest = Request.Builder()
            .url("$base/api/content/uploads/prepare")
            .post("""{"mimeType":"$mimeType"}""".toRequestBody("application/json".toMediaType()))
            .apply { if (!apiKey.isNullOrBlank()) header("X-Api-Key", apiKey) }
            .build()

        val prepareResponse = try {
            httpClient.newCall(prepareRequest).execute().use { response ->
                if (!response.isSuccessful) return UploadResult.Failure("Prepare failed: HTTP ${response.code}", response.code)
                response.body?.string() ?: return UploadResult.Failure("Empty prepare response")
            }
        } catch (e: IOException) {
            return UploadResult.Failure("Prepare error: ${e.message}")
        }

        val storageKey = parseJsonStringField(prepareResponse, "storageKey")
            ?: return UploadResult.Failure("Missing storageKey in prepare response")
        val uploadUrl = parseJsonStringField(prepareResponse, "uploadUrl")
            ?: return UploadResult.Failure("Missing uploadUrl in prepare response")

        // Step 2: PUT directly to GCS
        val putBody: RequestBody = if (onProgress != null)
            ProgressRequestBody(fileBytes, mimeType) { w, t -> onProgress(w, t) }
        else
            fileBytes.toRequestBody(mimeType.toMediaType())

        val putRequest = Request.Builder()
            .url(uploadUrl)
            .put(putBody)
            .header("Content-Type", mimeType)
            .build()

        try {
            httpClient.newCall(putRequest).execute().use { response ->
                if (!response.isSuccessful) return UploadResult.Failure("GCS upload failed: HTTP ${response.code}", response.code)
            }
        } catch (e: IOException) {
            return UploadResult.Failure("GCS upload error: ${e.message}")
        }

        // Step 3: confirm
        onConfirming?.invoke()
        val contentHash = sha256Hex(fileBytes)
        val confirmRequest = Request.Builder()
            .url("$base/api/content/uploads/confirm")
            .post("""{"storageKey":"$storageKey","mimeType":"$mimeType","fileSize":${fileBytes.size},"contentHash":"$contentHash"}"""
                .toRequestBody("application/json".toMediaType()))
            .apply { if (!apiKey.isNullOrBlank()) header("X-Api-Key", apiKey) }
            .build()

        return try {
            httpClient.newCall(confirmRequest).execute().use { response ->
                val code = response.code
                when {
                    response.isSuccessful -> UploadResult.Success("Uploaded successfully", code, 1)
                    code == 409 -> UploadResult.Success("Already uploaded (duplicate)", code, 1)
                    else -> UploadResult.Failure("Confirm failed: HTTP $code", code)
                }
            }
        } catch (e: IOException) {
            UploadResult.Failure("Confirm error: ${e.message}")
        }
    }

    /**
     * File-streaming variant of [uploadViaSigned]. Reads the file in chunks rather than
     * loading it into a ByteArray, so large videos don't exhaust the heap.
     * Do not call file.readBytes() before this — it causes OOM on small-heap devices (e.g. Samsung Galaxy A02s, 201 MB heap).
     */
    fun uploadViaSigned(
        baseUrl: String?,
        file: File,
        mimeType: String,
        apiKey: String? = null,
        tags: List<String> = emptyList(),
        onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)? = null,
        onConfirming: (() -> Unit)? = null,
    ): UploadResult {
        if (!isValidEndpoint(baseUrl)) return UploadResult.Failure("No endpoint configured")
        if (!file.exists() || file.length() == 0L) return UploadResult.Failure("File is empty")

        val base = baseUrl!!.trimEnd('/')

        val prepareRequest = Request.Builder()
            .url("$base/api/content/uploads/prepare")
            .post("""{"mimeType":"$mimeType"}""".toRequestBody("application/json".toMediaType()))
            .apply { if (!apiKey.isNullOrBlank()) header("X-Api-Key", apiKey) }
            .build()

        val prepareResponse = try {
            httpClient.newCall(prepareRequest).execute().use { response ->
                if (!response.isSuccessful) return UploadResult.Failure("Prepare failed: HTTP ${response.code}", response.code)
                response.body?.string() ?: return UploadResult.Failure("Empty prepare response")
            }
        } catch (e: IOException) {
            return UploadResult.Failure("Prepare error: ${e.message}")
        }

        val storageKey = parseJsonStringField(prepareResponse, "storageKey")
            ?: return UploadResult.Failure("Missing storageKey in prepare response")
        val uploadUrl = parseJsonStringField(prepareResponse, "uploadUrl")
            ?: return UploadResult.Failure("Missing uploadUrl in prepare response")

        val putBody: RequestBody = if (onProgress != null)
            ProgressFileRequestBody(file, mimeType, onProgress)
        else
            file.asRequestBody(mimeType.toMediaType())

        val putRequest = Request.Builder()
            .url(uploadUrl)
            .put(putBody)
            .header("Content-Type", mimeType)
            .build()

        try {
            httpClient.newCall(putRequest).execute().use { response ->
                if (!response.isSuccessful) return UploadResult.Failure("GCS upload failed: HTTP ${response.code}", response.code)
            }
        } catch (e: IOException) {
            return UploadResult.Failure("GCS upload error: ${e.message}")
        }

        onConfirming?.invoke()
        val contentHash = sha256Hex(file)
        val tagsJson = if (tags.isEmpty()) "" else ""","tags":[${tags.joinToString(",") { "\"$it\"" }}]"""
        val confirmRequest = Request.Builder()
            .url("$base/api/content/uploads/confirm")
            .post("""{"storageKey":"$storageKey","mimeType":"$mimeType","fileSize":${file.length()},"contentHash":"$contentHash"$tagsJson}"""
                .toRequestBody("application/json".toMediaType()))
            .apply { if (!apiKey.isNullOrBlank()) header("X-Api-Key", apiKey) }
            .build()

        return try {
            httpClient.newCall(confirmRequest).execute().use { response ->
                val code = response.code
                when {
                    response.isSuccessful -> UploadResult.Success("Uploaded successfully", code, 1)
                    code == 409 -> UploadResult.Success("Already uploaded (duplicate)", code, 1)
                    else -> UploadResult.Failure("Confirm failed: HTTP $code", code)
                }
            }
        } catch (e: IOException) {
            UploadResult.Failure("Confirm error: ${e.message}")
        }
    }

    /**
     * Encrypted upload flow:
     * 1. Generate content DEK + thumbnail DEK
     * 2. Encrypt file bytes (AES-256-GCM) → content envelope
     * 3. Generate + encrypt thumbnail → thumbnail envelope
     * 4. Wrap both DEKs under masterKey
     * 5. POST /uploads/initiate (storage_class: "encrypted") → two signed URLs
     * 6. PUT encrypted content (with progress callback)
     * 7. PUT encrypted thumbnail
     * 8. POST /uploads/confirm with all E2EE fields
     */
    fun uploadEncryptedViaSigned(
        baseUrl: String?,
        file: File,
        mimeType: String,
        masterKey: ByteArray,
        apiKey: String? = null,
        tags: List<String> = emptyList(),
        onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)? = null,
        onConfirming: (() -> Unit)? = null,
    ): UploadResult {
        if (!isValidEndpoint(baseUrl)) return UploadResult.Failure("No endpoint configured")
        if (!file.exists() || file.length() == 0L) return UploadResult.Failure("File is empty")

        val base = baseUrl!!.trimEnd('/')

        // Step 1: encrypt file
        val fileBytes = try {
            file.readBytes()
        } catch (e: Exception) {
            return UploadResult.Failure("Failed to read file: ${e.message}")
        }

        val contentDek = VaultCrypto.generateDek()
        val contentNonce = VaultCrypto.generateNonce()
        val encryptedContent = try {
            val ct = VaultCrypto.aesGcmEncrypt(contentDek, contentNonce, fileBytes)
            VaultCrypto.buildSymmetricEnvelope(VaultCrypto.ALG_AES256GCM_V1, contentNonce, ct)
        } catch (e: Exception) {
            return UploadResult.Failure("Content encryption failed: ${e.message}")
        }

        // Step 2: generate + encrypt thumbnail
        val thumbnailDek = VaultCrypto.generateDek()
        val (encryptedThumbnail, thumbMimeType) = try {
            val thumbBytes = generateThumbnail(file, mimeType)
                ?: makeFallbackThumbnail()
            val thumbNonce = VaultCrypto.generateNonce()
            val ct = VaultCrypto.aesGcmEncrypt(thumbnailDek, thumbNonce, thumbBytes)
            VaultCrypto.buildSymmetricEnvelope(VaultCrypto.ALG_AES256GCM_V1, thumbNonce, ct) to "image/jpeg"
        } catch (e: Exception) {
            return UploadResult.Failure("Thumbnail encryption failed: ${e.message}")
        }

        // Step 3: wrap DEKs under master key
        val wrappedContentDek = VaultCrypto.wrapDekUnderMasterKey(contentDek, masterKey)
        val wrappedThumbnailDek = VaultCrypto.wrapDekUnderMasterKey(thumbnailDek, masterKey)

        // Step 4: initiate
        val initiateRequest = Request.Builder()
            .url("$base/api/content/uploads/initiate")
            .post("""{"mimeType":"$mimeType","storage_class":"encrypted"}""".toRequestBody("application/json".toMediaType()))
            .apply { if (!apiKey.isNullOrBlank()) header("X-Api-Key", apiKey) }
            .build()

        val initiateResponse = try {
            httpClient.newCall(initiateRequest).execute().use { response ->
                if (!response.isSuccessful) return UploadResult.Failure("Initiate failed: HTTP ${response.code}", response.code)
                response.body?.string() ?: return UploadResult.Failure("Empty initiate response")
            }
        } catch (e: IOException) {
            return UploadResult.Failure("Initiate error: ${e.message}")
        }

        val contentStorageKey = parseJsonStringField(initiateResponse, "storageKey")
            ?: return UploadResult.Failure("Missing storageKey in initiate response")
        val contentUploadUrl = parseJsonStringField(initiateResponse, "uploadUrl")
            ?: return UploadResult.Failure("Missing uploadUrl in initiate response")
        val thumbStorageKey = parseJsonStringField(initiateResponse, "thumbnailStorageKey")
            ?: return UploadResult.Failure("Missing thumbnailStorageKey in initiate response")
        val thumbUploadUrl = parseJsonStringField(initiateResponse, "thumbnailUploadUrl")
            ?: return UploadResult.Failure("Missing thumbnailUploadUrl in initiate response")

        // Step 5: PUT encrypted content
        val contentBody: RequestBody = if (onProgress != null)
            ProgressRequestBody(encryptedContent, "application/octet-stream") { bw, total -> onProgress(bw, total) }
        else
            encryptedContent.toRequestBody("application/octet-stream".toMediaType())

        try {
            httpClient.newCall(
                Request.Builder().url(contentUploadUrl).put(contentBody).build()
            ).execute().use { response ->
                if (!response.isSuccessful) return UploadResult.Failure("Content PUT failed: HTTP ${response.code}", response.code)
            }
        } catch (e: IOException) {
            return UploadResult.Failure("Content PUT error: ${e.message}")
        }

        // Step 6: PUT encrypted thumbnail
        try {
            httpClient.newCall(
                Request.Builder()
                    .url(thumbUploadUrl)
                    .put(encryptedThumbnail.toRequestBody("application/octet-stream".toMediaType()))
                    .build()
            ).execute().use { response ->
                if (!response.isSuccessful) return UploadResult.Failure("Thumbnail PUT failed: HTTP ${response.code}", response.code)
            }
        } catch (e: IOException) {
            return UploadResult.Failure("Thumbnail PUT error: ${e.message}")
        }

        // Step 7: confirm
        onConfirming?.invoke()
        val wrappedDekB64 = Base64.encodeToString(wrappedContentDek, Base64.NO_WRAP)
        val wrappedThumbDekB64 = Base64.encodeToString(wrappedThumbnailDek, Base64.NO_WRAP)
        val tagsJson = if (tags.isEmpty()) "" else ""","tags":[${tags.joinToString(",") { "\"$it\"" }}]"""
        val confirmBody = """{"storageKey":"$contentStorageKey","mimeType":"$mimeType","fileSize":${encryptedContent.size},"storage_class":"encrypted","envelopeVersion":1,"wrappedDek":"$wrappedDekB64","dekFormat":"${VaultCrypto.ALG_MASTER_AES256GCM_V1}","thumbnailStorageKey":"$thumbStorageKey","wrappedThumbnailDek":"$wrappedThumbDekB64","thumbnailDekFormat":"${VaultCrypto.ALG_MASTER_AES256GCM_V1}"$tagsJson}"""

        return try {
            httpClient.newCall(
                Request.Builder()
                    .url("$base/api/content/uploads/confirm")
                    .post(confirmBody.toRequestBody("application/json".toMediaType()))
                    .apply { if (!apiKey.isNullOrBlank()) header("X-Api-Key", apiKey) }
                    .build()
            ).execute().use { response ->
                val code = response.code
                when {
                    response.isSuccessful -> UploadResult.Success("Encrypted upload complete", code, 1)
                    else -> UploadResult.Failure("Confirm failed: HTTP $code", code)
                }
            }
        } catch (e: IOException) {
            UploadResult.Failure("Confirm error: ${e.message}")
        }
    }

    private fun generateThumbnail(file: File, mimeType: String): ByteArray? {
        val bitmap: Bitmap? = when {
            mimeType.startsWith("image/") -> {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, opts)
                val maxDim = 400
                val scale = maxOf(opts.outWidth, opts.outHeight) / maxDim
                opts.inJustDecodeBounds = false
                opts.inSampleSize = maxOf(1, scale)
                BitmapFactory.decodeFile(file.path, opts)
            }
            mimeType.startsWith("video/") -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ThumbnailUtils.createVideoThumbnail(file, Size(400, 400), null)
                } else {
                    @Suppress("DEPRECATION")
                    ThumbnailUtils.createVideoThumbnail(file.path, android.provider.MediaStore.Video.Thumbnails.MINI_KIND)
                }
            }
            else -> null
        } ?: return null

        val maxDim = 400
        val bmp = bitmap ?: return null
        val scaled = if (bmp.width > maxDim || bmp.height > maxDim) {
            val ratio = minOf(maxDim.toFloat() / bmp.width, maxDim.toFloat() / bmp.height)
            Bitmap.createScaledBitmap(bmp, (bmp.width * ratio).toInt(), (bmp.height * ratio).toInt(), true)
        } else bmp

        return ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
    }

    private fun makeFallbackThumbnail(): ByteArray {
        val bmp = Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565)
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
    }
}

// Progress callbacks report (bytesWritten, totalBytes) so callers can aggregate
// multiple files into an overall percentage without re-computing from percent values.

private class ProgressRequestBody(
    private val bytes: ByteArray,
    private val mimeType: String,
    private val onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit,
) : RequestBody() {
    override fun contentType() = mimeType.toMediaType()
    override fun contentLength() = bytes.size.toLong()
    override fun writeTo(sink: BufferedSink) {
        val total = bytes.size.toLong()
        if (total == 0L) return
        var written = 0
        val chunk = 8 * 1024
        while (written < total) {
            val end = minOf(written + chunk, total.toInt())
            sink.write(bytes, written, end - written)
            written = end
            onProgress(written.toLong(), total)
        }
    }
}

private class ProgressFileRequestBody(
    private val file: File,
    private val mimeType: String,
    private val onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit,
) : RequestBody() {
    override fun contentType() = mimeType.toMediaType()
    override fun contentLength() = file.length()
    override fun writeTo(sink: BufferedSink) {
        val total = file.length()
        if (total == 0L) return
        var written = 0L
        val buffer = ByteArray(8 * 1024)
        file.inputStream().use { input ->
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                sink.write(buffer, 0, bytesRead)
                written += bytesRead
                onProgress(written, total)
            }
        }
    }
}
