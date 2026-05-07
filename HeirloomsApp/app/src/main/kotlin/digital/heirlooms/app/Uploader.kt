package digital.heirlooms.app

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
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
        onProgress: ((Int) -> Unit)? = null,
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
            ProgressRequestBody(fileBytes, mimeType, onProgress)
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
        onProgress: ((Int) -> Unit)? = null,
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
}

private class ProgressRequestBody(
    private val bytes: ByteArray,
    private val mimeType: String,
    private val onProgress: (Int) -> Unit,
) : RequestBody() {
    override fun contentType() = mimeType.toMediaType()
    override fun contentLength() = bytes.size.toLong()
    override fun writeTo(sink: BufferedSink) {
        val total = bytes.size
        if (total == 0) return
        var written = 0
        var lastPercent = -1
        val chunk = 8 * 1024
        while (written < total) {
            val end = minOf(written + chunk, total)
            sink.write(bytes, written, end - written)
            written = end
            val percent = (written * 100L / total).toInt()
            if (percent != lastPercent) {
                lastPercent = percent
                onProgress(percent)
            }
        }
    }
}

private class ProgressFileRequestBody(
    private val file: File,
    private val mimeType: String,
    private val onProgress: (Int) -> Unit,
) : RequestBody() {
    override fun contentType() = mimeType.toMediaType()
    override fun contentLength() = file.length()
    override fun writeTo(sink: BufferedSink) {
        val total = file.length()
        if (total == 0L) return
        var written = 0L
        var lastPercent = -1
        val buffer = ByteArray(8 * 1024)
        file.inputStream().use { input ->
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                sink.write(buffer, 0, bytesRead)
                written += bytesRead
                val percent = (written * 100L / total).toInt()
                if (percent != lastPercent) {
                    lastPercent = percent
                    onProgress(percent)
                }
            }
        }
    }
}
