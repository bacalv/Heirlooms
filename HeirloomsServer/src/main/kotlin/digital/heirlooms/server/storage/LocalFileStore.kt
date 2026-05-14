package digital.heirlooms.server.storage

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID

/**
 * [FileStore] implementation that writes uploaded files to a local directory.
 *
 * Each file is saved as <UUID>.<extension>, where the extension is derived
 * from the MIME type. The storage directory is created on startup if it does
 * not already exist.
 *
 * Accepts a [uuidProvider] so tests can inject deterministic IDs.
 */
class LocalFileStore(
    private val storageDir: Path,
    private val uuidProvider: () -> UUID = UUID::randomUUID,
) : FileStore {

    init {
        Files.createDirectories(storageDir)
    }

    override fun save(bytes: ByteArray, mimeType: String): StorageKey {
        val filename = "${uuidProvider()}.${mimeTypeToExtension(mimeType)}"
        Files.write(storageDir.resolve(filename), bytes)
        return StorageKey(filename)
    }

    override fun saveWithKey(bytes: ByteArray, key: StorageKey, mimeType: String) {
        Files.write(storageDir.resolve(key.value), bytes)
    }

    override fun get(key: StorageKey): ByteArray = Files.readAllBytes(storageDir.resolve(key.value))

    override fun delete(key: StorageKey) {
        Files.deleteIfExists(storageDir.resolve(key.value))
    }

    companion object {
        fun create(storageDir: String): LocalFileStore =
            LocalFileStore(Paths.get(storageDir))
    }
}
