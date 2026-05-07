package digital.heirlooms.server

/**
 * Represents the key under which a file was stored.
 * For local storage this is the filename; for S3 this is the object key.
 */
@JvmInline
value class StorageKey(val value: String) {
    override fun toString(): String = value
}

/**
 * Abstraction over file storage backends.
 *
 * Implementations:
 * - [LocalFileStore] — writes files to a local directory
 * - [S3FileStore]    — puts objects into an Amazon S3 bucket
 */
interface FileStore {
    /**
     * Persists [bytes] with the given [mimeType].
     * The implementation chooses the key/filename using a UUID + derived extension.
     * @return the [StorageKey] under which the file was stored.
     * @throws Exception if the file could not be stored.
     */
    fun save(bytes: ByteArray, mimeType: String): StorageKey

    /**
     * Persists [bytes] under the explicit [key] with the given [mimeType].
     * Used for storing thumbnails alongside the original file.
     * @throws Exception if the file could not be stored.
     */
    fun saveWithKey(bytes: ByteArray, key: StorageKey, mimeType: String)

    /**
     * Retrieves the raw bytes stored under [key].
     * @throws Exception if the file could not be retrieved.
     */
    fun get(key: StorageKey): ByteArray

    /**
     * Retrieves at most [maxBytes] bytes from the start of the object stored under [key].
     * Used for reading EXIF headers without downloading the full file.
     * Default implementation falls back to [get] — override for efficiency.
     */
    fun getFirst(key: StorageKey, maxBytes: Int): ByteArray = get(key)

    /**
     * Deletes the object stored under [key]. No-op if the object does not exist.
     * Used by the compost lazy-cleanup path.
     */
    fun delete(key: StorageKey)
}
