package digital.heirlooms.server.storage

data class PreparedUpload(
    val storageKey: StorageKey,
    val uploadUrl: String,
)

interface DirectUploadSupport {
    fun prepareUpload(mimeType: String): PreparedUpload
    fun generateReadUrl(key: StorageKey): String

    /**
     * Initiates a GCS resumable upload session for [storageKey] and returns the session URI.
     * The caller PUTs ciphertext chunks directly to that URI using Content-Range headers.
     * Default implementation throws — only GcsFileStore supports resumable uploads.
     */
    fun initiateResumableUpload(storageKey: StorageKey, totalBytes: Long, contentType: String): String {
        throw NotImplementedError("Resumable uploads not supported by this storage backend")
    }
}
