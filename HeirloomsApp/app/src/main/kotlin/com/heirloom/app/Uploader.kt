package com.heirloom.app

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Handles HTTP upload logic independently of Android framework classes,
 * so it can be unit-tested on the JVM without an emulator.
 *
 * Supports automatic retry with exponential backoff for transient failures
 * (network errors and 5xx responses). 4xx errors are not retried as they
 * indicate a permanent client-side problem.
 */
class Uploader(
    private val httpClient: OkHttpClient,
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    private val initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
) {

    /**
     * Sealed class representing the outcome of an upload attempt.
     */
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

        /** Returns true only for http:// or https:// endpoints. */
        fun isValidEndpoint(endpoint: String?): Boolean {
            val trimmed = endpoint?.trim() ?: return false
            return trimmed.startsWith("http://") || trimmed.startsWith("https://")
        }

        /** Returns the trimmed MIME type, or [FALLBACK_MIME_TYPE] if blank. */
        fun resolveMimeType(mimeType: String?): String =
            mimeType?.trim()?.takeIf { it.isNotEmpty() } ?: FALLBACK_MIME_TYPE

        /** Returns true for errors that are worth retrying. */
        fun isRetryable(result: UploadResult): Boolean = when (result) {
            is UploadResult.Failure -> result.httpCode == NO_HTTP_CODE || result.httpCode >= 500
            is UploadResult.Success -> false
        }

        /**
         * Produces an infinite sequence of exponential backoff delays starting at
         * [initialDelayMs], doubling each step: initialDelayMs, initialDelayMs*2, initialDelayMs*4, …
         */
        private fun backoffSequence(initialDelayMs: Long): Sequence<Long> =
            generateSequence(initialDelayMs) { it * 2 }
    }

    /**
     * Performs the HTTP POST with automatic retry and exponential backoff.
     * Returns an [UploadResult] — never throws.
     *
     * Retry policy:
     * - Network errors (IOException): always retried up to [maxAttempts]
     * - HTTP 5xx: retried (server-side transient error)
     * - HTTP 4xx: not retried (permanent client error)
     * - Delay between attempts doubles each time: 1s, 2s, 4s, …
     */
    fun upload(endpoint: String?, fileBytes: ByteArray?, mimeType: String): UploadResult {
        if (!isValidEndpoint(endpoint)) {
            return UploadResult.Failure("No endpoint configured in config.properties")
        }
        if (fileBytes == null || fileBytes.isEmpty()) {
            return UploadResult.Failure("File is empty")
        }

        // Pair each attempt number with its pre-attempt delay (0 for the first attempt).
        // zip stops at the shorter sequence, giving exactly maxAttempts pairs.
        val attempts = (1..maxAttempts)
        val delays = sequenceOf(0L) + backoffSequence(initialDelayMs)

        return attempts.asSequence().zip(delays).fold<Pair<Int, Long>, UploadResult>(
            initial = UploadResult.Failure("Upload not attempted"),
        ) { lastResult, (attempt, delayMs) ->
            // Stop folding early if the last result is not retryable
            if (attempt > 1 && !isRetryable(lastResult)) return lastResult

            if (delayMs > 0) Thread.sleep(delayMs)

            val result = attemptUpload(endpoint!!, fileBytes, mimeType, attempt)

            if (!isRetryable(result)) return result

            result
        }
    }

    private fun attemptUpload(
        endpoint: String,
        fileBytes: ByteArray,
        mimeType: String,
        attempt: Int,
    ): UploadResult {
        val body = fileBytes.toRequestBody(mimeType.toMediaType())
        val request = Request.Builder()
            .url(endpoint)
            .post(body)
            .header("Content-Type", mimeType)
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                val code = response.code
                if (response.isSuccessful) {
                    UploadResult.Success("Uploaded successfully ($code)", code, attempt)
                } else {
                    UploadResult.Failure("Upload failed: HTTP $code", code, attempt)
                }
            }
        } catch (e: IOException) {
            UploadResult.Failure("Upload error: ${e.message}", NO_HTTP_CODE, attempt)
        }
    }
}
