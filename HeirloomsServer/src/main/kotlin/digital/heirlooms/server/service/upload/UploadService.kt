package digital.heirlooms.server.service.upload

import digital.heirlooms.server.DirectUploadSupport
import digital.heirlooms.server.EnvelopeFormat
import digital.heirlooms.server.EnvelopeFormatException
import digital.heirlooms.server.FileStore
import digital.heirlooms.server.MediaMetadata
import digital.heirlooms.server.MetadataExtractor
import digital.heirlooms.server.StorageKey
import digital.heirlooms.server.METADATA_IMAGE_MIME_TYPES
import digital.heirlooms.server.METADATA_SUPPORTED_MIME_TYPES
import digital.heirlooms.server.THUMBNAIL_SUPPORTED_MIME_TYPES
import digital.heirlooms.server.domain.upload.UploadPage
import digital.heirlooms.server.domain.upload.UploadRecord
import digital.heirlooms.server.domain.upload.UploadSort
import digital.heirlooms.server.extractVideoDuration
import digital.heirlooms.server.generateThumbnail
import digital.heirlooms.server.repository.plot.FlowRepository
import digital.heirlooms.server.repository.plot.PlotRepository
import digital.heirlooms.server.repository.social.SocialRepository
import digital.heirlooms.server.repository.storage.BlobRepository
import digital.heirlooms.server.repository.upload.UploadRepository
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.UUID

private val PROCESSING_SUPPORTED_MIME_TYPES = THUMBNAIL_SUPPORTED_MIME_TYPES + METADATA_SUPPORTED_MIME_TYPES
private const val EXIF_HEADER_BYTES = 65_536

/**
 * Encapsulates business logic for upload operations. The handler is responsible
 * for parsing HTTP requests and formatting responses; this service owns the
 * orchestration decisions (dedup check, thumbnail generation, metadata
 * extraction, blob tracking, migration, sharing).
 */
class UploadService(
    private val uploadRepo: UploadRepository,
    private val blobRepo: BlobRepository,
    private val socialRepo: SocialRepository,
    private val plotRepo: PlotRepository,
    private val flowRepo: FlowRepository,
    private val storage: FileStore,
    private val thumbnailGenerator: (ByteArray, String) -> ByteArray? = ::generateThumbnail,
    private val metadataExtractor: (ByteArray, String) -> MediaMetadata = MetadataExtractor()::extract,
) {
    private val directUpload = storage as? DirectUploadSupport

    // ---- Direct upload preparation -----------------------------------------

    data class PreparedUploadResult(val storageKey: String, val uploadUrl: String)
    data class InitiatedUpload(
        val storageKey: String,
        val uploadUrl: String,
        val thumbnailStorageKey: String? = null,
        val thumbnailUploadUrl: String? = null,
    )
    data class ResumableUploadResult(val resumableUri: String)

    sealed class PrepareResult {
        data class Ok(val storageKey: String, val uploadUrl: String) : PrepareResult()
        object NotSupported : PrepareResult()
    }

    sealed class InitiateResult {
        data class Encrypted(
            val storageKey: String, val uploadUrl: String,
            val thumbnailStorageKey: String, val thumbnailUploadUrl: String,
        ) : InitiateResult()
        data class Public(val storageKey: String, val uploadUrl: String) : InitiateResult()
        object NotSupported : InitiateResult()
        data class Invalid(val message: String) : InitiateResult()
    }

    sealed class ResumableResult {
        data class Ok(val resumableUri: String) : ResumableResult()
        object NotSupported : ResumableResult()
        data class Invalid(val message: String) : ResumableResult()
    }

    fun prepareUpload(mimeType: String): PrepareResult {
        val du = directUpload ?: return PrepareResult.NotSupported
        val prepared = du.prepareUpload(mimeType)
        return PrepareResult.Ok(prepared.storageKey.value, prepared.uploadUrl)
    }

    fun initiateUpload(mimeType: String, storageClassRaw: String?): InitiateResult {
        val du = directUpload ?: return InitiateResult.NotSupported
        if (mimeType.isBlank()) return InitiateResult.Invalid("Missing or invalid mimeType in request body")
        if (storageClassRaw == "public") return InitiateResult.Invalid("Cannot use public storage class — omit storage_class or use encrypted")
        return if (storageClassRaw == "encrypted") {
            val content = du.prepareUpload(mimeType)
            val thumb = du.prepareUpload("application/octet-stream")
            blobRepo.insertPendingBlob(content.storageKey.value)
            blobRepo.insertPendingBlob(thumb.storageKey.value)
            InitiateResult.Encrypted(
                content.storageKey.value, content.uploadUrl,
                thumb.storageKey.value, thumb.uploadUrl,
            )
        } else {
            val prepared = du.prepareUpload(mimeType)
            blobRepo.insertPendingBlob(prepared.storageKey.value)
            InitiateResult.Public(prepared.storageKey.value, prepared.uploadUrl)
        }
    }

    fun initiateResumableUpload(storageKey: String, totalBytes: Long, contentType: String): ResumableResult {
        val du = directUpload ?: return ResumableResult.NotSupported
        if (totalBytes <= 0) return ResumableResult.Invalid("totalBytes must be positive")
        val uri = du.initiateResumableUpload(StorageKey(storageKey), totalBytes, contentType)
        return ResumableResult.Ok(uri)
    }

    // ---- Legacy (plaintext) upload -----------------------------------------

    sealed class UploadResult {
        data class Created(val record: UploadRecord) : UploadResult()
        data class Duplicate(val storageKey: String) : UploadResult()
        data class Invalid(val message: String) : UploadResult()
        data class Failed(val message: String) : UploadResult()
    }

    fun handleUpload(body: ByteArray, mimeType: String, userId: UUID): UploadResult {
        if (body.isEmpty()) return UploadResult.Invalid("Request body is empty")
        val hash = sha256Hex(body)
        val existing = uploadRepo.findByContentHash(hash, userId)
        if (existing != null) return UploadResult.Duplicate(existing.storageKey)
        return try {
            val id = UUID.randomUUID()
            val uploadedAt = Instant.now()
            val key = storage.save(body, mimeType)
            val thumbKey = tryStoreThumbnail(body, mimeType, key, storage, thumbnailGenerator)
            val metadata = try { metadataExtractor(body, mimeType) } catch (_: Exception) { MediaMetadata() }
            val record = UploadRecord(
                id = id,
                storageKey = key.value,
                mimeType = mimeType,
                fileSize = body.size.toLong(),
                uploadedAt = uploadedAt,
                contentHash = hash,
                thumbnailKey = thumbKey?.value,
                takenAt = metadata.takenAt,
                latitude = metadata.latitude,
                longitude = metadata.longitude,
                altitude = metadata.altitude,
                deviceMake = metadata.deviceMake,
                deviceModel = metadata.deviceModel,
            )
            uploadRepo.recordUpload(record, userId)
            UploadResult.Created(record)
        } catch (e: Exception) {
            UploadResult.Failed("Failed to store file: ${e.message}")
        }
    }

    // ---- Confirm upload (E2EE) ----------------------------------------------

    sealed class ConfirmResult {
        object Created : ConfirmResult()
        data class Duplicate(val storageKey: String) : ConfirmResult()
        data class Invalid(val message: String) : ConfirmResult()
    }

    fun confirmEncryptedUpload(
        storageKey: String,
        mimeType: String,
        fileSize: Long,
        contentHash: String?,
        tags: List<String>,
        envelopeVersion: Int?,
        wrappedDekB64: String?,
        dekFormat: String?,
        encryptedMetaB64: String?,
        encryptedMetaFormat: String?,
        thumbStorageKey: String?,
        wrappedThumbDekB64: String?,
        thumbDekFormat: String?,
        previewStorageKey: String?,
        wrappedPreviewDekB64: String?,
        previewDekFormat: String?,
        plainChunkSize: Int?,
        durationSeconds: Int?,
        takenAt: Instant?,
        userId: UUID,
    ): ConfirmResult {
        val dec = Base64.getDecoder()

        if (envelopeVersion == null || wrappedDekB64.isNullOrBlank() || dekFormat.isNullOrBlank())
            return ConfirmResult.Invalid("Encrypted confirm requires envelopeVersion, wrappedDek, and dekFormat")

        val wrappedDek = runCatching { dec.decode(wrappedDekB64) }.getOrNull()
            ?: return ConfirmResult.Invalid("wrappedDek is not valid Base64")
        val encryptedMeta = encryptedMetaB64?.let {
            runCatching { dec.decode(it) }.getOrNull()
                ?: return ConfirmResult.Invalid("encryptedMetadata is not valid Base64")
        }
        val wrappedThumbDek = wrappedThumbDekB64?.let {
            runCatching { dec.decode(it) }.getOrNull()
                ?: return ConfirmResult.Invalid("wrappedThumbnailDek is not valid Base64")
        }
        val wrappedPreviewDek = wrappedPreviewDekB64?.let {
            runCatching { dec.decode(it) }.getOrNull()
                ?: return ConfirmResult.Invalid("wrappedPreviewDek is not valid Base64")
        }

        runCatching { EnvelopeFormat.validateSymmetric(wrappedDek, dekFormat) }.onFailure { e ->
            return ConfirmResult.Invalid("wrappedDek envelope invalid: ${e.message}")
        }
        if (encryptedMeta != null && encryptedMetaFormat != null) {
            runCatching { EnvelopeFormat.validateSymmetric(encryptedMeta, encryptedMetaFormat) }.onFailure { e ->
                return ConfirmResult.Invalid("encryptedMetadata envelope invalid: ${e.message}")
            }
        }
        if (wrappedThumbDek != null && thumbDekFormat != null) {
            runCatching { EnvelopeFormat.validateSymmetric(wrappedThumbDek, thumbDekFormat) }.onFailure { e ->
                return ConfirmResult.Invalid("wrappedThumbnailDek envelope invalid: ${e.message}")
            }
        }
        if (wrappedPreviewDek != null && previewDekFormat != null) {
            runCatching { EnvelopeFormat.validateSymmetric(wrappedPreviewDek, previewDekFormat) }.onFailure { e ->
                return ConfirmResult.Invalid("wrappedPreviewDek envelope invalid: ${e.message}")
            }
        }

        val id = UUID.randomUUID()
        uploadRepo.recordUpload(
            UploadRecord(
                id = id,
                storageKey = storageKey,
                mimeType = mimeType,
                fileSize = fileSize,
                contentHash = contentHash,
                takenAt = takenAt,
                storageClass = "encrypted",
                envelopeVersion = envelopeVersion,
                wrappedDek = wrappedDek,
                dekFormat = dekFormat,
                encryptedMetadata = encryptedMeta,
                encryptedMetadataFormat = encryptedMetaFormat,
                thumbnailStorageKey = thumbStorageKey,
                wrappedThumbnailDek = wrappedThumbDek,
                thumbnailDekFormat = thumbDekFormat,
                previewStorageKey = previewStorageKey,
                wrappedPreviewDek = wrappedPreviewDek,
                previewDekFormat = previewDekFormat,
                plainChunkSize = plainChunkSize,
                durationSeconds = durationSeconds,
            ),
            userId,
        )
        if (tags.isNotEmpty()) uploadRepo.updateTags(id, tags, userId) { conn, uploadId, uid -> flowRepo.runUnstagedFlowsForUpload(conn, uploadId, uid) }
        runCatching { blobRepo.deletePendingBlob(storageKey) }
        if (thumbStorageKey != null) runCatching { blobRepo.deletePendingBlob(thumbStorageKey) }
        if (previewStorageKey != null) runCatching { blobRepo.deletePendingBlob(previewStorageKey) }
        return ConfirmResult.Created
    }

    fun confirmLegacyUpload(
        storageKey: String,
        mimeType: String,
        fileSize: Long,
        contentHash: String?,
        tags: List<String>,
        userId: UUID,
    ): ConfirmResult {
        val existing = if (contentHash != null) uploadRepo.findByContentHash(contentHash, userId) else null
        if (existing != null) return ConfirmResult.Duplicate(existing.storageKey)

        val bytes = fetchBytesIfNeeded(storageKey, mimeType)
        val thumbKey = if (bytes != null) tryStoreThumbnail(bytes, mimeType, StorageKey(storageKey), storage, thumbnailGenerator) else null
        val metaBytes = fetchHeaderForMetadata(storageKey, mimeType)
        val metadata = if (metaBytes != null) {
            runCatching { metadataExtractor(metaBytes, mimeType) }.getOrDefault(MediaMetadata())
        } else MediaMetadata()
        val normalizedMime = mimeType.substringBefore(";").trim().lowercase()
        val durationSeconds = if (bytes != null && normalizedMime.startsWith("video/"))
            runCatching { extractVideoDuration(bytes, mimeType) }.getOrNull()
        else null
        val id = UUID.randomUUID()
        uploadRepo.recordUpload(
            UploadRecord(
                id = id,
                storageKey = storageKey,
                mimeType = mimeType,
                fileSize = fileSize,
                contentHash = contentHash,
                thumbnailKey = thumbKey?.value,
                takenAt = metadata.takenAt,
                latitude = metadata.latitude,
                longitude = metadata.longitude,
                altitude = metadata.altitude,
                deviceMake = metadata.deviceMake,
                deviceModel = metadata.deviceModel,
                durationSeconds = durationSeconds,
            ),
            userId,
        )
        if (tags.isNotEmpty()) uploadRepo.updateTags(id, tags, userId) { conn, uploadId, uid -> flowRepo.runUnstagedFlowsForUpload(conn, uploadId, uid) }
        runCatching { blobRepo.deletePendingBlob(storageKey) }
        return ConfirmResult.Created
    }

    // ---- Migration ---------------------------------------------------------

    sealed class MigrateResult {
        data class Migrated(val record: UploadRecord) : MigrateResult()
        object NotFound : MigrateResult()
        data class WrongStorageClass(val message: String) : MigrateResult()
        data class Invalid(val message: String) : MigrateResult()
        object AlreadyMigrated : MigrateResult()
    }

    fun migrateToEncrypted(
        uploadId: UUID,
        userId: UUID,
        newStorageKey: String,
        contentHash: String?,
        envelopeVersion: Int?,
        wrappedDekB64: String,
        dekFormat: String,
        encryptedMetaB64: String?,
        encryptedMetaFormat: String?,
        thumbStorageKey: String?,
        wrappedThumbDekB64: String?,
        thumbDekFormat: String?,
    ): MigrateResult {
        val existing = uploadRepo.findUploadByIdForUser(uploadId, userId) ?: return MigrateResult.NotFound
        if (existing.storageClass != "public")
            return MigrateResult.WrongStorageClass("upload is already migrated or wrong storage class")

        if (envelopeVersion == null)
            return MigrateResult.Invalid("Missing required fields: newStorageKey, envelopeVersion, wrappedDek, dekFormat")

        val dec = Base64.getDecoder()
        val wrappedDek = try { dec.decode(wrappedDekB64) } catch (_: Exception) {
            return MigrateResult.Invalid("wrappedDek is not valid Base64")
        }
        val encryptedMeta = encryptedMetaB64?.let {
            try { dec.decode(it) } catch (_: Exception) {
                return MigrateResult.Invalid("encryptedMetadata is not valid Base64")
            }
        }
        val wrappedThumbDek = wrappedThumbDekB64?.let {
            try { dec.decode(it) } catch (_: Exception) {
                return MigrateResult.Invalid("wrappedThumbnailDek is not valid Base64")
            }
        }

        try { EnvelopeFormat.validateSymmetric(wrappedDek, dekFormat) } catch (e: EnvelopeFormatException) {
            return MigrateResult.Invalid("wrappedDek envelope invalid: ${e.message}")
        }
        if (encryptedMeta != null && encryptedMetaFormat != null) {
            try { EnvelopeFormat.validateSymmetric(encryptedMeta, encryptedMetaFormat) } catch (e: EnvelopeFormatException) {
                return MigrateResult.Invalid("encryptedMetadata envelope invalid: ${e.message}")
            }
        }
        if (wrappedThumbDek != null && thumbDekFormat != null) {
            try { EnvelopeFormat.validateSymmetric(wrappedThumbDek, thumbDekFormat) } catch (e: EnvelopeFormatException) {
                return MigrateResult.Invalid("wrappedThumbnailDek envelope invalid: ${e.message}")
            }
        }

        val oldStorageKey = existing.storageKey
        val oldThumbnailKey = existing.thumbnailKey

        val migrated = uploadRepo.migrateUploadToEncrypted(
            id = uploadId,
            newStorageKey = newStorageKey,
            newContentHash = contentHash,
            envelopeVersion = envelopeVersion,
            wrappedDek = wrappedDek,
            dekFormat = dekFormat,
            encryptedMetadata = encryptedMeta,
            encryptedMetadataFormat = encryptedMetaFormat,
            thumbnailStorageKey = thumbStorageKey,
            wrappedThumbnailDek = wrappedThumbDek,
            thumbnailDekFormat = thumbDekFormat,
        )
        if (!migrated) return MigrateResult.AlreadyMigrated

        // Delete old plaintext blobs (best effort)
        try { storage.delete(StorageKey(oldStorageKey)) } catch (e: Exception) {
            println("[migrate] WARNING: failed to delete old blob $oldStorageKey: ${e.message}")
        }
        if (oldThumbnailKey != null) {
            try { storage.delete(StorageKey(oldThumbnailKey)) } catch (e: Exception) {
                println("[migrate] WARNING: failed to delete old thumbnail $oldThumbnailKey: ${e.message}")
            }
        }

        // Clean up pending_blobs entries for the new ciphertext keys
        try { blobRepo.deletePendingBlob(newStorageKey) } catch (_: Exception) {}
        if (thumbStorageKey != null) {
            try { blobRepo.deletePendingBlob(thumbStorageKey) } catch (_: Exception) {}
        }

        val updated = uploadRepo.findUploadByIdForUser(uploadId, userId) ?: return MigrateResult.NotFound
        return MigrateResult.Migrated(updated)
    }

    // ---- Share upload -------------------------------------------------------

    sealed class ShareResult {
        data class Shared(val record: UploadRecord) : ShareResult()
        object NotFound : ShareResult()
        object NotFriends : ShareResult()
        object AlreadyHasItem : ShareResult()
        data class Invalid(val message: String) : ShareResult()
    }

    fun shareUpload(
        uploadId: UUID,
        requesterId: UUID,
        toUserIdStr: String?,
        wrappedDekB64: String?,
        wrappedThumbnailDekB64: String?,
        dekFormat: String?,
        rotationOverride: Int?,
    ): ShareResult {
        val record = uploadRepo.findUploadByIdForUser(uploadId, requesterId) ?: return ShareResult.NotFound
        if (toUserIdStr.isNullOrBlank() || wrappedDekB64.isNullOrBlank() || dekFormat.isNullOrBlank())
            return ShareResult.Invalid("Missing required fields")
        val toUserId = runCatching { UUID.fromString(toUserIdStr) }.getOrNull()
            ?: return ShareResult.Invalid("Invalid toUserId")
        if (!socialRepo.areFriends(requesterId, toUserId)) return ShareResult.NotFriends
        if (uploadRepo.userAlreadyHasStorageKey(toUserId, record.storageKey)) return ShareResult.AlreadyHasItem
        val dec = Base64.getDecoder()
        val wrappedDek = runCatching { dec.decode(wrappedDekB64) }.getOrNull()
            ?: return ShareResult.Invalid("wrappedDek is not valid Base64")
        val wrappedThumbnailDek = if (!wrappedThumbnailDekB64.isNullOrBlank())
            runCatching { dec.decode(wrappedThumbnailDekB64) }.getOrNull() else null
        val shared = uploadRepo.createSharedUpload(record, requesterId, toUserId, wrappedDek, wrappedThumbnailDek, dekFormat, rotationOverride)
        return ShareResult.Shared(shared)
    }

    // ---- Upload query delegation (thin wrappers for route use) ---------------

    fun listAllTags(userId: java.util.UUID) = uploadRepo.listAllTags(userId)

    fun findUploadForUser(id: java.util.UUID, userId: java.util.UUID) =
        uploadRepo.findUploadByIdForUser(id, userId) ?: uploadRepo.findUploadByIdForSharedMember(id, userId)

    fun existsByContentHash(hash: String, userId: java.util.UUID) = uploadRepo.existsByContentHash(hash, userId)

    fun listUploadsPaginated(
        cursor: String? = null, limit: Int = 50, tags: List<String> = emptyList(),
        excludeTag: String? = null, fromDate: Instant? = null, toDate: Instant? = null,
        inCapsule: Boolean? = null, includeComposted: Boolean = false, hasLocation: Boolean? = null,
        sort: digital.heirlooms.server.domain.upload.UploadSort = digital.heirlooms.server.domain.upload.UploadSort.UPLOAD_NEWEST,
        justArrived: Boolean = false, mediaType: String? = null, isReceived: Boolean? = null,
        plotId: java.util.UUID? = null, userId: java.util.UUID,
    ) = uploadRepo.listUploadsPaginated(
        cursor, limit, tags, excludeTag, fromDate, toDate, inCapsule, includeComposted,
        hasLocation, sort, justArrived, mediaType, isReceived, plotId, userId, plotRepo,
    )

    fun listCompostedUploadsPaginated(cursor: String? = null, limit: Int = 50, userId: java.util.UUID) =
        uploadRepo.listCompostedUploadsPaginated(cursor, limit, userId)

    fun compostUpload(id: java.util.UUID, userId: java.util.UUID) = uploadRepo.compostUpload(id, userId)

    fun restoreUpload(id: java.util.UUID, userId: java.util.UUID) = uploadRepo.restoreUpload(id, userId)

    fun updateRotation(id: java.util.UUID, rotation: Int, userId: java.util.UUID) =
        uploadRepo.updateRotation(id, rotation, userId)

    fun recordView(id: java.util.UUID, userId: java.util.UUID) = uploadRepo.recordView(id, userId)

    fun updateTags(id: java.util.UUID, tags: List<String>, userId: java.util.UUID) =
        uploadRepo.updateTags(id, tags, userId) { conn, uploadId, uid -> flowRepo.runUnstagedFlowsForUpload(conn, uploadId, uid) }

    // ---- Compost cleanup (background) --------------------------------------

    fun launchCompostCleanup() {
        Thread {
            try {
                val expired = uploadRepo.fetchExpiredCompostedUploads()
                for (record in expired) {
                    try {
                        val hasLiveRef = uploadRepo.hasLiveSharedReference(record.storageKey, record.id)
                        if (!hasLiveRef) {
                            storage.delete(StorageKey(record.storageKey))
                            if (record.thumbnailKey != null) {
                                try { storage.delete(StorageKey(record.thumbnailKey)) } catch (e: Exception) {
                                    println("[compost-cleanup] WARNING: failed to delete thumbnail ${record.thumbnailKey}: ${e.message}")
                                }
                            }
                            if (record.thumbnailStorageKey != null) {
                                try { storage.delete(StorageKey(record.thumbnailStorageKey)) } catch (e: Exception) {
                                    println("[compost-cleanup] WARNING: failed to delete encrypted thumbnail ${record.thumbnailStorageKey}: ${e.message}")
                                }
                            }
                        } else {
                            println("[compost-cleanup] INFO: skipping GCS delete for upload ${record.id} — live shared reference exists")
                        }
                        uploadRepo.hardDeleteUpload(record.id)
                        println("[compost-cleanup] INFO: hard-deleted upload ${record.id} (composted ${record.compostedAt})")
                    } catch (e: Exception) {
                        println("[compost-cleanup] WARNING: failed to hard-delete upload ${record.id}: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                println("[compost-cleanup] ERROR: cleanup failed: ${e.message}")
            }
            try {
                val expiredPlots = plotRepo.fetchExpiredTombstonedPlots()
                for (plotId in expiredPlots) {
                    try {
                        plotRepo.hardDeletePlot(plotId)
                        println("[compost-cleanup] INFO: hard-deleted tombstoned plot $plotId")
                    } catch (e: Exception) {
                        println("[compost-cleanup] WARNING: failed to hard-delete plot $plotId: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                println("[compost-cleanup] ERROR: tombstone plot cleanup failed: ${e.message}")
            }
        }.also { it.isDaemon = true }.start()
    }

    // ---- Private helpers ---------------------------------------------------

    private fun fetchBytesIfNeeded(storageKey: String, mimeType: String): ByteArray? {
        val normalized = mimeType.substringBefore(";").trim().lowercase()
        if (normalized !in PROCESSING_SUPPORTED_MIME_TYPES) return null
        return try { storage.get(StorageKey(storageKey)) } catch (_: Exception) { null }
    }

    private fun fetchHeaderForMetadata(storageKey: String, mimeType: String): ByteArray? {
        val normalized = mimeType.substringBefore(";").trim().lowercase()
        if (normalized !in METADATA_SUPPORTED_MIME_TYPES) return null
        return try {
            if (normalized in METADATA_IMAGE_MIME_TYPES) {
                storage.getFirst(StorageKey(storageKey), EXIF_HEADER_BYTES)
            } else {
                storage.get(StorageKey(storageKey))
            }
        } catch (_: Exception) { null }
    }

    companion object {
        fun sha256Hex(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(bytes).joinToString("") { "%02x".format(it) }
        }

        fun tryStoreThumbnail(
            bytes: ByteArray,
            mimeType: String,
            originalKey: StorageKey,
            storage: FileStore,
            thumbnailGenerator: (ByteArray, String) -> ByteArray?,
        ): StorageKey? {
            return try {
                val thumbBytes = thumbnailGenerator(bytes, mimeType) ?: return null
                val thumbKeyValue = "${originalKey.value.substringBeforeLast(".")}-thumb.jpg"
                storage.saveWithKey(thumbBytes, StorageKey(thumbKeyValue), "image/jpeg")
                StorageKey(thumbKeyValue)
            } catch (_: Exception) { null }
        }
    }
}
