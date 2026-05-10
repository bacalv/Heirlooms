package digital.heirlooms.app

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import digital.heirlooms.crypto.VaultCrypto
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import okio.Buffer
import java.io.IOException

/**
 * Media3 DataSource that decrypts streaming-encrypted content (produced by
 * encryptAndUploadStreaming) chunk by chunk as ExoPlayer reads it, so video
 * playback can begin without downloading the entire file first.
 *
 * Each ciphertext chunk is [nonce(12)][ciphertext+GCM-tag] = exactly 4 MiB.
 * The nonce doubles as the AAD (per buildChunkAad == buildChunkNonce).
 * Seeks are translated from plaintext coordinates to ciphertext byte ranges
 * via HTTP Range requests (the /file endpoint returns 206 for range requests).
 */
class DecryptingDataSource(
    private val okHttpClient: OkHttpClient,
    private val dek: ByteArray,
    private val apiKey: String?,
) : DataSource {

    private companion object {
        const val CIPHERTEXT_CHUNK = 4 * 1024 * 1024       // 4 MiB
        const val NONCE_SIZE = 12
        const val PLAINTEXT_CHUNK = CIPHERTEXT_CHUNK - NONCE_SIZE - 16  // = Uploader.CHUNK_SIZE
    }

    private var responseBody: ResponseBody? = null
    private var openedUri: Uri? = null
    private var plaintextChunk: ByteArray? = null
    private var chunkReadOffset = 0
    // Cached total plaintext length derived from the first response's Content-Length.
    private var cachedPlaintextLength = C.LENGTH_UNSET.toLong()

    override fun open(dataSpec: DataSpec): Long {
        close()
        openedUri = dataSpec.uri

        val plaintextPos = dataSpec.position
        val chunkIndex = plaintextPos / PLAINTEXT_CHUNK
        val offsetInChunk = (plaintextPos % PLAINTEXT_CHUNK).toInt()
        val ciphertextPos = chunkIndex * CIPHERTEXT_CHUNK

        val request = Request.Builder()
            .url(dataSpec.uri.toString())
            .apply { if (!apiKey.isNullOrBlank()) header("X-Api-Key", apiKey) }
            .apply { if (ciphertextPos > 0) header("Range", "bytes=$ciphertextPos-") }
            .build()

        val response = try {
            okHttpClient.newCall(request).execute()
        } catch (e: IOException) {
            throw e
        }
        if (!response.isSuccessful && response.code != 206) {
            response.close()
            throw IOException("HTTP ${response.code} opening ${dataSpec.uri}")
        }
        responseBody = response.body ?: throw IOException("Empty response body")

        // Derive total plaintext length from ciphertext total size (available on first open
        // from Content-Length, and on range responses from the Content-Range /total suffix).
        if (cachedPlaintextLength == C.LENGTH_UNSET.toLong()) {
            val ciphertextTotal: Long = when (response.code) {
                206 -> response.header("Content-Range")
                    ?.substringAfterLast('/')?.trim()?.toLongOrNull() ?: -1L
                else -> response.body?.contentLength() ?: -1L
            }
            if (ciphertextTotal > 0) {
                val numChunks = (ciphertextTotal + CIPHERTEXT_CHUNK - 1) / CIPHERTEXT_CHUNK
                cachedPlaintextLength = ciphertextTotal - numChunks * (NONCE_SIZE + 16)
            }
        }

        if (offsetInChunk > 0) {
            plaintextChunk = decryptNextChunk() ?: throw IOException("EOF seeking into chunk at $plaintextPos")
            chunkReadOffset = offsetInChunk
        }

        return when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
            cachedPlaintextLength != C.LENGTH_UNSET.toLong() -> cachedPlaintextLength - plaintextPos
            else -> C.LENGTH_UNSET.toLong()
        }
    }

    private fun decryptNextChunk(): ByteArray? {
        val source = responseBody?.source() ?: return null
        val sink = Buffer()
        var remaining = CIPHERTEXT_CHUNK.toLong()
        while (remaining > 0) {
            val n = source.read(sink, remaining)
            if (n == -1L) break
            remaining -= n
        }
        if (sink.size < NONCE_SIZE + 16) return null  // EOF or corrupt
        return try {
            val nonce = sink.readByteArray(NONCE_SIZE.toLong())
            val ctWithTag = sink.readByteArray()
            VaultCrypto.aesGcmDecryptWithAad(dek, nonce, nonce, ctWithTag)
        } catch (_: Exception) { null }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        var totalRead = 0
        while (totalRead < length) {
            val chunk = plaintextChunk
            if (chunk == null || chunkReadOffset >= chunk.size) {
                val next = decryptNextChunk()
                if (next == null || next.isEmpty()) {
                    return if (totalRead == 0) C.RESULT_END_OF_INPUT else totalRead
                }
                plaintextChunk = next
                chunkReadOffset = 0
            }
            val current = plaintextChunk ?: break
            val available = current.size - chunkReadOffset
            val toRead = minOf(length - totalRead, available)
            current.copyInto(buffer, offset + totalRead, chunkReadOffset, chunkReadOffset + toRead)
            chunkReadOffset += toRead
            totalRead += toRead
        }
        return if (totalRead == 0) C.RESULT_END_OF_INPUT else totalRead
    }

    override fun getUri(): Uri? = openedUri

    override fun close() {
        responseBody?.close()
        responseBody = null
        plaintextChunk = null
        chunkReadOffset = 0
        // cachedPlaintextLength is intentionally not reset — it persists across seeks.
    }

    override fun addTransferListener(transferListener: TransferListener) {}

    class Factory(
        private val okHttpClient: OkHttpClient,
        private val dek: ByteArray,
        private val apiKey: String?,
    ) : DataSource.Factory {
        override fun createDataSource() = DecryptingDataSource(okHttpClient, dek, apiKey)
    }
}
