package digital.heirlooms.server.routes.upload

import digital.heirlooms.server.domain.upload.TagValidationResult
import digital.heirlooms.server.domain.upload.UploadSort
import digital.heirlooms.server.domain.upload.validateTags
import digital.heirlooms.server.filters.authUserId
import digital.heirlooms.server.repository.upload.UploadRepository
import digital.heirlooms.server.representation.upload.toJson
import digital.heirlooms.server.service.upload.UploadService
import digital.heirlooms.server.storage.DirectUploadSupport
import digital.heirlooms.server.storage.FileStore
import digital.heirlooms.server.storage.StorageKey
import com.fasterxml.jackson.databind.ObjectMapper
import org.http4k.contract.ContractRoute
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.PATCH
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.CONFLICT
import org.http4k.core.Status.Companion.CREATED
import org.http4k.core.Status.Companion.FORBIDDEN
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.NOT_IMPLEMENTED
import org.http4k.core.Status.Companion.NO_CONTENT
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.PARTIAL_CONTENT
import org.http4k.core.Status.Companion.UNPROCESSABLE_ENTITY
import org.http4k.format.Jackson.auto
import org.http4k.lens.Path
import org.http4k.lens.binary
import org.http4k.lens.uuid
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

fun uploadContractRoute(uploadService: UploadService): ContractRoute =
    "/upload" meta {
        summary = "Upload a file"
        description = "Upload an image or video. Content-Type header should reflect the file's MIME type (e.g. image/jpeg, video/mp4)."
        receiving(Body.binary(ContentType("image/jpeg")).toLens())
        receiving(Body.binary(ContentType("image/png")).toLens())
        receiving(Body.binary(ContentType("image/gif")).toLens())
        receiving(Body.binary(ContentType("image/webp")).toLens())
        receiving(Body.binary(ContentType("image/heic")).toLens())
        receiving(Body.binary(ContentType("image/heif")).toLens())
        receiving(Body.binary(ContentType("image/bmp")).toLens())
        receiving(Body.binary(ContentType("image/tiff")).toLens())
        receiving(Body.binary(ContentType("video/mp4")).toLens())
        receiving(Body.binary(ContentType("video/quicktime")).toLens())
        receiving(Body.binary(ContentType("video/x-msvideo")).toLens())
        receiving(Body.binary(ContentType("video/webm")).toLens())
        receiving(Body.binary(ContentType("video/3gpp")).toLens())
        receiving(Body.binary(ContentType("video/mpeg")).toLens())
        receiving(Body.binary(ContentType("application/octet-stream")).toLens())
    } bindContract POST to { request: Request ->
        val body = request.body.payload.array()
        val mimeType = request.header("Content-Type")
            ?.substringBefore(";")?.trim()?.takeIf { it.isNotEmpty() }
            ?: "application/octet-stream"
        val userId = request.authUserId()
        when (val result = uploadService.handleUpload(body, mimeType, userId)) {
            is UploadService.UploadResult.Created ->
                Response(CREATED).header("Content-Type", "application/json").body(result.record.toJson())
            is UploadService.UploadResult.Duplicate ->
                Response(CONFLICT).header("Content-Type", "application/json")
                    .body("""{"storageKey":"${result.storageKey}"}""")
            is UploadService.UploadResult.Invalid ->
                Response(BAD_REQUEST).body(result.message)
            is UploadService.UploadResult.Failed ->
                Response(INTERNAL_SERVER_ERROR).body(result.message)
        }
    }

fun listUploadsContractRoute(uploadService: UploadService): ContractRoute =
    "/uploads" meta {
        summary = "List uploads"
        description = "Returns uploads as a cursor-paginated JSON object."
    } bindContract GET to listUploadsHandler(uploadService)

fun listTagsContractRoute(uploadService: UploadService): ContractRoute =
    "/uploads/tags" meta {
        summary = "List all tags"
        description = "Returns all distinct tags used across non-composted uploads, sorted alphabetically."
    } bindContract GET to { request: Request ->
        try {
            val userId = request.authUserId()
            val tags = uploadService.listAllTags(userId)
            val json = com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(tags)
            Response(OK).header("Content-Type", "application/json").body(json)
        } catch (e: Exception) {
            Response(INTERNAL_SERVER_ERROR).body("Failed to list tags: ${e.message}")
        }
    }

fun getUploadByIdContractRoute(uploadService: UploadService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/uploads" / id meta {
        summary = "Get upload by ID"
        description = "Returns a single upload by ID regardless of composted state. Returns 404 if not found."
    } bindContract GET to { uploadId: UUID ->
        { request: Request ->
            val userId = request.authUserId()
            val record = uploadService.findUploadForUser(uploadId, userId)
            if (record == null) Response(NOT_FOUND)
            else Response(OK).header("Content-Type", "application/json").body(record.toJson())
        }
    }
}

fun checkContentHashContractRoute(uploadService: UploadService): ContractRoute {
    val hash = Path.of("hash")
    return "/uploads/hash" / hash meta {
        summary = "Check if a content hash exists"
        description = "Returns 200 if a non-composted upload with this SHA-256 hex digest exists for the authenticated user, 404 otherwise."
    } bindContract GET to { h: String ->
        { request: Request ->
            val userId = request.authUserId()
            if (uploadService.existsByContentHash(h, userId))
                Response(OK).header("Content-Type", "application/json").body("""{"exists":true}""")
            else
                Response(NOT_FOUND)
        }
    }
}

fun listCompostedUploadsContractRoute(uploadService: UploadService): ContractRoute =
    "/uploads/composted" meta {
        summary = "List composted uploads"
        description = "Returns composted uploads as a cursor-paginated JSON object."
    } bindContract GET to listCompostedUploadsHandler(uploadService)

fun listUploadsHandler(uploadService: UploadService): HttpHandler = handler@{ request ->
    try {
        val cursor = request.query("cursor")?.takeIf { it.isNotBlank() }
        val limit = request.query("limit")?.toIntOrNull()?.coerceIn(1, 200) ?: 50
        val tagParam = request.query("tag")?.takeIf { it.isNotBlank() }
        val tags = tagParam?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
        val excludeTag = request.query("exclude_tag")?.takeIf { it.isNotBlank() }
        val fromDate = request.query("from_date")?.let { tryParseDate(it) }
        val toDate = request.query("to_date")?.let { tryParseDate(it, endOfDay = true) }
        val inCapsule = request.query("in_capsule")?.let {
            when (it) { "true" -> true; "false" -> false; else -> null }
        }
        val includeComposted = request.query("include_composted") == "true"
        val hasLocation = request.query("has_location")?.let {
            when (it) { "true" -> true; "false" -> false; else -> null }
        }
        val sort = when (request.query("sort")) {
            "upload_oldest" -> UploadSort.UPLOAD_OLDEST
            "taken_newest"  -> UploadSort.TAKEN_NEWEST
            "taken_oldest"  -> UploadSort.TAKEN_OLDEST
            else            -> UploadSort.UPLOAD_NEWEST
        }
        val justArrived = request.query("just_arrived") == "true"
        val mediaType = request.query("media_type")?.takeIf { it == "image" || it == "video" }
        val isReceived = request.query("is_received")?.let {
            when (it) { "true" -> true; "false" -> false; else -> null }
        }
        val plotId = request.query("plot_id")?.let {
            try { java.util.UUID.fromString(it) } catch (_: Exception) { null }
        }

        val page = try {
            uploadService.listUploadsPaginated(
                cursor = cursor, limit = limit, tags = tags, excludeTag = excludeTag,
                fromDate = fromDate, toDate = toDate, inCapsule = inCapsule,
                includeComposted = includeComposted, hasLocation = hasLocation,
                sort = sort, justArrived = justArrived,
                mediaType = mediaType, isReceived = isReceived, plotId = plotId,
                userId = request.authUserId(),
            )
        } catch (e: IllegalArgumentException) {
            return@handler Response(NOT_FOUND).body(e.message ?: "Plot not found")
        }
        val response = Response(OK).header("Content-Type", "application/json").body(page.toJson())
        uploadService.launchCompostCleanup()
        response
    } catch (e: Exception) {
        Response(INTERNAL_SERVER_ERROR).body("Failed to list uploads: ${e.message}")
    }
}

private fun tryParseDate(s: String, endOfDay: Boolean = false): Instant? = try {
    Instant.parse(s)
} catch (_: Exception) {
    try {
        val date = LocalDate.parse(s)
        val base = if (endOfDay) date.plusDays(1) else date
        base.atStartOfDay(ZoneOffset.UTC).toInstant()
    } catch (_: Exception) { null }
}

private fun listCompostedUploadsHandler(uploadService: UploadService): HttpHandler = { request ->
    try {
        val cursor = request.query("cursor")?.takeIf { it.isNotBlank() }
        val limit = request.query("limit")?.toIntOrNull()?.coerceIn(1, 200) ?: 50
        val page = uploadService.listCompostedUploadsPaginated(cursor = cursor, limit = limit, userId = request.authUserId())
        Response(OK).header("Content-Type", "application/json").body(page.toJson())
    } catch (e: Exception) {
        Response(INTERNAL_SERVER_ERROR).body("Failed to list composted uploads: ${e.message}")
    }
}

fun compostUploadContractRoute(uploadService: UploadService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/uploads" / id / "compost" meta {
        summary = "Compost an upload"
        description = "Soft-deletes an upload. Requires no tags and no active capsule memberships."
    } bindContract POST to { uploadId: UUID, _: String ->
        { request: Request ->
            try {
                when (val result = uploadService.compostUpload(uploadId, request.authUserId())) {
                    is UploadRepository.CompostResult.Success ->
                        Response(OK).header("Content-Type", "application/json").body(result.record.toJson())
                    is UploadRepository.CompostResult.NotFound ->
                        Response(NOT_FOUND)
                    is UploadRepository.CompostResult.AlreadyComposted ->
                        Response(CONFLICT).header("Content-Type", "application/json")
                            .body("""{"error":"Upload is already composted"}""")
                    is UploadRepository.CompostResult.PreconditionFailed ->
                        Response(UNPROCESSABLE_ENTITY).header("Content-Type", "application/json")
                            .body("""{"error":"Cannot compost: upload has tags or is in active capsules"}""")
                }
            } catch (e: Exception) {
                Response(INTERNAL_SERVER_ERROR).body("Failed to compost upload: ${e.message}")
            }
        }
    }
}

fun restoreUploadContractRoute(uploadService: UploadService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/uploads" / id / "restore" meta {
        summary = "Restore a composted upload"
        description = "Removes composted_at from an upload, returning it to the active garden."
    } bindContract POST to { uploadId: UUID, _: String ->
        { request: Request ->
            try {
                when (val result = uploadService.restoreUpload(uploadId, request.authUserId())) {
                    is UploadRepository.RestoreResult.Success ->
                        Response(OK).header("Content-Type", "application/json").body(result.record.toJson())
                    is UploadRepository.RestoreResult.NotFound ->
                        Response(NOT_FOUND)
                    is UploadRepository.RestoreResult.NotComposted ->
                        Response(CONFLICT).header("Content-Type", "application/json")
                            .body("""{"error":"Upload is not composted"}""")
                }
            } catch (e: Exception) {
                Response(INTERNAL_SERVER_ERROR).body("Failed to restore upload: ${e.message}")
            }
        }
    }
}

fun shareUploadContractRoute(uploadService: UploadService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/uploads" / id / "share" meta {
        summary = "Share an upload with a friend"
        description = "Creates a recipient upload record with a re-wrapped DEK."
    } bindContract POST to { uploadId: UUID, _: String ->
        { request: Request -> handleShareUpload(uploadId, request, uploadService) }
    }
}

private fun handleShareUpload(uploadId: UUID, request: Request, uploadService: UploadService): Response {
    val requesterId = request.authUserId() ?: return Response(FORBIDDEN)
    val node = ObjectMapper().readTree(request.bodyString())
    return when (val result = uploadService.shareUpload(
        uploadId = uploadId,
        requesterId = requesterId,
        toUserIdStr = node?.get("toUserId")?.asText(),
        wrappedDekB64 = node?.get("wrappedDek")?.asText(),
        wrappedThumbnailDekB64 = node?.get("wrappedThumbnailDek")?.asText(),
        dekFormat = node?.get("dekFormat")?.asText(),
        rotationOverride = node?.get("rotation")?.asInt(),
    )) {
        is UploadService.ShareResult.Shared ->
            Response(CREATED).header("Content-Type", "application/json").body(result.record.toJson())
        is UploadService.ShareResult.NotFound -> Response(NOT_FOUND)
        is UploadService.ShareResult.NotFriends ->
            Response(FORBIDDEN).body("Not friends")
        is UploadService.ShareResult.AlreadyHasItem ->
            Response(CONFLICT).body("Recipient already has this item")
        is UploadService.ShareResult.Invalid ->
            Response(BAD_REQUEST).body(result.message)
    }
}

fun fileProxyContractRoute(storage: FileStore, uploadService: UploadService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/uploads" / id / "file" meta {
        summary = "Get file"
        description = "Streams the raw file bytes for the given upload ID with the correct Content-Type."
    } bindContract GET to { uploadId: UUID, _: String ->
        { request: Request ->
            val userId = request.authUserId()
            val record = uploadService.findUploadForUser(uploadId, userId)
            if (record == null) {
                Response(NOT_FOUND)
            } else {
                try {
                    val bytes = storage.get(StorageKey(record.storageKey))
                    val total = bytes.size.toLong()
                    val rangeHeader = request.header("Range")
                    if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                        val parts = rangeHeader.removePrefix("bytes=").split("-")
                        val start = parts.getOrNull(0)?.toLongOrNull() ?: 0L
                        val end = parts.getOrNull(1)?.toLongOrNull()?.coerceAtMost(total - 1) ?: (total - 1)
                        if (start > end || start >= total) {
                            Response(org.http4k.core.Status(416, "Range Not Satisfiable"))
                                .header("Content-Range", "bytes */$total")
                        } else {
                            val slice = bytes.copyOfRange(start.toInt(), (end + 1).toInt())
                            Response(PARTIAL_CONTENT)
                                .header("Content-Type", record.mimeType)
                                .header("Content-Range", "bytes $start-$end/$total")
                                .header("Accept-Ranges", "bytes")
                                .body(ByteArrayInputStream(slice), slice.size.toLong())
                        }
                    } else {
                        Response(OK)
                            .header("Content-Type", record.mimeType)
                            .header("Accept-Ranges", "bytes")
                            .body(ByteArrayInputStream(bytes), total)
                    }
                } catch (e: Exception) {
                    Response(INTERNAL_SERVER_ERROR).body("Failed to fetch file: ${e.message}")
                }
            }
        }
    }
}

fun thumbProxyContractRoute(storage: FileStore, uploadService: UploadService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/uploads" / id / "thumb" meta {
        summary = "Get thumbnail"
        description = "Returns the JPEG thumbnail for the given upload ID if one exists, otherwise falls back to the full file."
    } bindContract GET to { uploadId: UUID, _: String ->
        { request: Request ->
            val userId = request.authUserId()
            val record = uploadService.findUploadForUser(uploadId, userId)
            if (record == null) {
                Response(NOT_FOUND)
            } else {
                val keyToFetch = when {
                    record.storageClass == "encrypted" && record.thumbnailStorageKey != null ->
                        StorageKey(record.thumbnailStorageKey)
                    record.thumbnailKey != null -> StorageKey(record.thumbnailKey)
                    else -> StorageKey(record.storageKey)
                }
                val mimeType = when {
                    record.storageClass == "encrypted" && record.thumbnailStorageKey != null ->
                        "application/octet-stream"
                    record.thumbnailKey != null -> "image/jpeg"
                    else -> record.mimeType
                }
                try {
                    val bytes = storage.get(keyToFetch)
                    Response(OK)
                        .header("Content-Type", mimeType)
                        .body(ByteArrayInputStream(bytes), bytes.size.toLong())
                } catch (e: Exception) {
                    Response(INTERNAL_SERVER_ERROR).body("Failed to fetch file: ${e.message}")
                }
            }
        }
    }
}

fun previewProxyContractRoute(storage: FileStore, uploadService: UploadService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/uploads" / id / "preview" meta {
        summary = "Get encrypted preview clip"
        description = "Returns the encrypted preview clip for the given upload ID if one exists, otherwise 404."
    } bindContract GET to { uploadId: UUID, _: String ->
        { request: Request ->
            val userId = request.authUserId()
            val record = uploadService.findUploadForUser(uploadId, userId)
            when {
                record == null -> Response(NOT_FOUND)
                record.previewStorageKey == null -> Response(NOT_FOUND)
                else -> try {
                    val bytes = storage.get(StorageKey(record.previewStorageKey))
                    Response(OK)
                        .header("Content-Type", "application/octet-stream")
                        .body(ByteArrayInputStream(bytes), bytes.size.toLong())
                } catch (e: Exception) {
                    Response(INTERNAL_SERVER_ERROR).body("Failed to fetch preview: ${e.message}")
                }
            }
        }
    }
}

fun readUrlContractRoute(directUpload: DirectUploadSupport?, uploadService: UploadService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/uploads" / id / "url" meta {
        summary = "Get signed read URL"
        description = "Returns a 1-hour signed GCS URL for the given upload ID."
    } bindContract GET to { uploadId: UUID, _: String ->
        { request: Request ->
            if (directUpload == null) {
                Response(NOT_IMPLEMENTED).body("Signed URLs not supported by the current storage backend")
            } else {
                val record = uploadService.findUploadForUser(uploadId, request.authUserId())
                if (record == null) {
                    Response(NOT_FOUND)
                } else {
                    try {
                        val url = directUpload.generateReadUrl(StorageKey(record.storageKey))
                        Response(OK).header("Content-Type", "application/json").body("""{"url":"$url"}""")
                    } catch (e: Exception) {
                        Response(INTERNAL_SERVER_ERROR).body("Failed to generate URL: ${e.message}")
                    }
                }
            }
        }
    }
}

fun prepareUploadContractRoute(uploadService: UploadService): ContractRoute =
    "/uploads/prepare" meta {
        summary = "Prepare a direct upload"
        description = "Returns a signed GCS URL the client can PUT the file to directly."
    } bindContract POST to { request: Request ->
        val mimeType = try {
            ObjectMapper().readTree(request.bodyString())?.get("mimeType")?.asText()
        } catch (_: Exception) { null }
        if (mimeType.isNullOrBlank()) {
            Response(BAD_REQUEST).body("Missing or invalid mimeType in request body")
        } else {
            when (val result = uploadService.prepareUpload(mimeType)) {
                is UploadService.PrepareResult.Ok ->
                    Response(OK).header("Content-Type", "application/json")
                        .body("""{"storageKey":"${result.storageKey}","uploadUrl":"${result.uploadUrl}"}""")
                UploadService.PrepareResult.NotSupported ->
                    Response(NOT_IMPLEMENTED).body("Direct upload not supported by the current storage backend")
            }
        }
    }

fun initiateUploadContractRoute(uploadService: UploadService): ContractRoute =
    "/uploads/initiate" meta {
        summary = "Initiate an upload (E2EE-aware)"
        description = "Returns signed upload URL(s)."
    } bindContract POST to { request: Request ->
        try {
            val node = ObjectMapper().readTree(request.bodyString())
            val mimeType = node?.get("mimeType")?.asText() ?: ""
            val storageClassRaw = node?.get("storage_class")?.asText()
            if (mimeType.isNotBlank() && !isAllowedMimeType(mimeType)) {
                return@to Response(BAD_REQUEST).body("Unsupported media type")
            }
            when (val result = uploadService.initiateUpload(mimeType, storageClassRaw)) {
                is UploadService.InitiateResult.Encrypted ->
                    Response(OK).header("Content-Type", "application/json")
                        .body("""{"storageKey":"${result.storageKey}","uploadUrl":"${result.uploadUrl}","thumbnailStorageKey":"${result.thumbnailStorageKey}","thumbnailUploadUrl":"${result.thumbnailUploadUrl}"}""")
                is UploadService.InitiateResult.Public ->
                    Response(OK).header("Content-Type", "application/json")
                        .body("""{"storageKey":"${result.storageKey}","uploadUrl":"${result.uploadUrl}"}""")
                is UploadService.InitiateResult.Invalid ->
                    Response(BAD_REQUEST).body(result.message)
                UploadService.InitiateResult.NotSupported ->
                    Response(NOT_IMPLEMENTED).body("Direct upload not supported by the current storage backend")
            }
        } catch (e: Exception) {
            Response(INTERNAL_SERVER_ERROR).body("Failed to initiate upload: ${e.message}")
        }
    }

fun resumableUploadContractRoute(uploadService: UploadService): ContractRoute =
    "/uploads/resumable" meta {
        summary = "Initiate a resumable (chunked) upload"
        description = "Creates a GCS resumable upload session and returns the session URI."
    } bindContract POST to { request: Request ->
        try {
            val node = ObjectMapper().readTree(request.bodyString())
            val storageKey = node?.get("storageKey")?.asText()
            val totalBytes = node?.get("totalBytes")?.asLong()
            val contentType = node?.get("contentType")?.asText()
            when {
                storageKey.isNullOrBlank() || totalBytes == null || contentType.isNullOrBlank() ->
                    Response(BAD_REQUEST).body("Missing storageKey, totalBytes, or contentType in request body")
                else -> when (val result = uploadService.initiateResumableUpload(storageKey, totalBytes, contentType)) {
                    is UploadService.ResumableResult.Ok ->
                        Response(OK).header("Content-Type", "application/json")
                            .body("""{"resumableUri":"${result.resumableUri}"}""")
                    is UploadService.ResumableResult.Invalid ->
                        Response(BAD_REQUEST).body(result.message)
                    UploadService.ResumableResult.NotSupported ->
                        Response(NOT_IMPLEMENTED).body("Resumable uploads not supported by the current storage backend")
                }
            }
        } catch (e: Exception) {
            Response(INTERNAL_SERVER_ERROR).body("Failed to initiate resumable upload: ${e.message}")
        }
    }

fun migrateUploadContractRoute(uploadService: UploadService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/uploads" / id / "migrate" meta {
        summary = "Migrate a legacy upload to encrypted"
        description = "Atomically replaces a public upload's bytes with an encrypted envelope."
    } bindContract POST to { uploadId: UUID, _: String ->
        { request: Request ->
            val node = try { ObjectMapper().readTree(request.bodyString()) } catch (_: Exception) { null }
            val newStorageKey = node?.get("newStorageKey")?.asText()
            val envelopeVersion = node?.get("envelopeVersion")?.asInt()
            val wrappedDekB64 = node?.get("wrappedDek")?.asText()
            val dekFormat = node?.get("dekFormat")?.asText()
            if (newStorageKey.isNullOrBlank() || wrappedDekB64.isNullOrBlank() || dekFormat.isNullOrBlank() || envelopeVersion == null) {
                Response(BAD_REQUEST).body("Missing required fields: newStorageKey, envelopeVersion, wrappedDek, dekFormat")
            } else {
                when (val result = uploadService.migrateToEncrypted(
                    uploadId = uploadId,
                    userId = request.authUserId(),
                    newStorageKey = newStorageKey,
                    contentHash = node?.get("contentHash")?.asText()?.takeIf { it.isNotBlank() },
                    envelopeVersion = envelopeVersion,
                    wrappedDekB64 = wrappedDekB64,
                    dekFormat = dekFormat,
                    encryptedMetaB64 = node?.get("encryptedMetadata")?.asText(),
                    encryptedMetaFormat = node?.get("encryptedMetadataFormat")?.asText(),
                    thumbStorageKey = node?.get("thumbnailStorageKey")?.asText()?.takeIf { it.isNotBlank() },
                    wrappedThumbDekB64 = node?.get("wrappedThumbnailDek")?.asText()?.takeIf { it.isNotBlank() },
                    thumbDekFormat = node?.get("thumbnailDekFormat")?.asText()?.takeIf { it.isNotBlank() },
                )) {
                    is UploadService.MigrateResult.Migrated ->
                        Response(OK).header("Content-Type", "application/json").body(result.record.toJson())
                    UploadService.MigrateResult.NotFound -> Response(NOT_FOUND)
                    is UploadService.MigrateResult.WrongStorageClass ->
                        Response(CONFLICT).header("Content-Type", "application/json")
                            .body("""{"error":"${result.message}"}""")
                    is UploadService.MigrateResult.Invalid ->
                        Response(BAD_REQUEST).body(result.message)
                    UploadService.MigrateResult.AlreadyMigrated ->
                        Response(CONFLICT).header("Content-Type", "application/json")
                            .body("""{"error":"upload was already migrated concurrently"}""")
                }
            }
        }
    }
}

fun confirmUploadContractRoute(uploadService: UploadService): ContractRoute =
    "/uploads/confirm" meta {
        summary = "Confirm a direct upload"
        description = "Records upload metadata after the client has PUT the file directly to GCS."
    } bindContract POST to { request: Request ->
        try {
            handleConfirmUpload(request, request.authUserId(), uploadService)
        } catch (e: Exception) {
            Response(INTERNAL_SERVER_ERROR).body("Failed to confirm upload: ${e.message}")
        }
    }

private fun handleConfirmUpload(
    request: Request,
    userId: UUID,
    uploadService: UploadService,
): Response {
    val node = ObjectMapper().readTree(request.bodyString())
    val storageKey = node?.get("storageKey")?.asText()
    val mimeType = node?.get("mimeType")?.asText()
    val fileSize = node?.get("fileSize")?.asLong()
    val contentHash = node?.get("contentHash")?.asText()?.takeIf { it.isNotBlank() }
    val storageClassRaw = node?.get("storage_class")?.asText()
    val tags = node?.get("tags")?.takeIf { it.isArray }
        ?.map { it.asText() }?.filter { it.isNotBlank() } ?: emptyList()

    val tagValidation = validateTags(tags)
    if (storageKey.isNullOrBlank() || mimeType.isNullOrBlank() || fileSize == null)
        return Response(BAD_REQUEST).body("Missing storageKey, mimeType, or fileSize in request body")
    if (tagValidation is TagValidationResult.Invalid)
        return Response(BAD_REQUEST).header("Content-Type", "application/json")
            .body("""{"error":"invalid tag","tag":"${tagValidation.tag}","reason":"${tagValidation.reason}"}""")

    return if (storageClassRaw == "encrypted") {
        val envelopeVersion = node?.get("envelopeVersion")?.asInt()
        val wrappedDekB64 = node?.get("wrappedDek")?.asText()
        val dekFormat = node?.get("dekFormat")?.asText()
        val encryptedMetaB64 = node?.get("encryptedMetadata")?.takeIf { !it.isNull }?.asText()?.takeIf { it.isNotBlank() }
        val encryptedMetaFormat = node?.get("encryptedMetadataFormat")?.takeIf { !it.isNull }?.asText()?.takeIf { it.isNotBlank() }
        val thumbStorageKey = node?.get("thumbnailStorageKey")?.takeIf { !it.isNull }?.asText()?.takeIf { it.isNotBlank() }
        val wrappedThumbDekB64 = node?.get("wrappedThumbnailDek")?.takeIf { !it.isNull }?.asText()?.takeIf { it.isNotBlank() }
        val thumbDekFormat = node?.get("thumbnailDekFormat")?.takeIf { !it.isNull }?.asText()?.takeIf { it.isNotBlank() }
        val previewStorageKey = node?.get("previewStorageKey")?.takeIf { !it.isNull }?.asText()?.takeIf { it.isNotBlank() }
        val wrappedPreviewDekB64 = node?.get("wrappedPreviewDek")?.takeIf { !it.isNull }?.asText()?.takeIf { it.isNotBlank() }
        val previewDekFormat = node?.get("previewDekFormat")?.takeIf { !it.isNull }?.asText()?.takeIf { it.isNotBlank() }
        val plainChunkSize = node?.get("plainChunkSize")?.asInt()?.takeIf { it > 0 }
        val durationSeconds = node?.get("durationSeconds")?.asInt()?.takeIf { it > 0 }
        val takenAt = node?.get("takenAt")?.asText()?.takeIf { it.isNotBlank() }
            ?.let { runCatching { Instant.parse(it) }.getOrNull() }
        when (val result = uploadService.confirmEncryptedUpload(
            storageKey, mimeType, fileSize, contentHash, tags,
            envelopeVersion, wrappedDekB64, dekFormat,
            encryptedMetaB64, encryptedMetaFormat,
            thumbStorageKey, wrappedThumbDekB64, thumbDekFormat,
            previewStorageKey, wrappedPreviewDekB64, previewDekFormat,
            plainChunkSize, durationSeconds, takenAt, userId,
        )) {
            UploadService.ConfirmResult.Created -> Response(CREATED)
            is UploadService.ConfirmResult.Duplicate ->
                Response(CONFLICT).header("Content-Type", "application/json")
                    .body("""{"storageKey":"${result.storageKey}"}""")
            is UploadService.ConfirmResult.Invalid -> Response(BAD_REQUEST).body(result.message)
        }
    } else {
        when (val result = uploadService.confirmLegacyUpload(storageKey, mimeType, fileSize, contentHash, tags, userId)) {
            UploadService.ConfirmResult.Created -> Response(CREATED)
            is UploadService.ConfirmResult.Duplicate ->
                Response(CONFLICT).header("Content-Type", "application/json")
                    .body("""{"storageKey":"${result.storageKey}"}""")
            is UploadService.ConfirmResult.Invalid -> Response(BAD_REQUEST).body(result.message)
        }
    }
}

data class RotationRequest(val rotation: Int)
data class TagsRequest(val tags: List<String>)

private val rotationRequestLens = Body.auto<RotationRequest>().toLens()
private val tagsRequestLens = Body.auto<TagsRequest>().toLens()

private val VALID_ROTATIONS = setOf(0, 90, 180, 270)

fun rotationContractRoute(uploadService: UploadService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/uploads" / id / "rotation" meta {
        summary = "Set image rotation"
        description = "Sets the display rotation for an image (0, 90, 180, or 270 degrees)."
        receiving(rotationRequestLens to RotationRequest(rotation = 90))
    } bindContract PATCH to { uploadId: UUID, _: String ->
        { request: Request ->
            try {
                val body = try { rotationRequestLens(request) } catch (_: Exception) { null }
                when {
                    body == null ->
                        Response(BAD_REQUEST).body("Malformed JSON")
                    body.rotation !in VALID_ROTATIONS ->
                        Response(BAD_REQUEST).body("rotation must be 0, 90, 180, or 270")
                    !uploadService.updateRotation(uploadId, body.rotation, request.authUserId()) ->
                        Response(NOT_FOUND)
                    else ->
                        Response(OK)
                }
            } catch (e: Exception) {
                Response(INTERNAL_SERVER_ERROR).body("Failed to update rotation: ${e.message}")
            }
        }
    }
}

fun viewUploadContractRoute(uploadService: UploadService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/uploads" / id / "view" meta {
        summary = "Record a detail view"
        description = "Sets last_viewed_at on the upload, removing it from the Just arrived plot."
    } bindContract POST to { uploadId: UUID, _: String ->
        { request: Request ->
            val viewed = uploadService.recordView(uploadId, request.authUserId())
            if (!viewed) Response(NOT_FOUND) else Response(NO_CONTENT)
        }
    }
}

fun tagsContractRoute(uploadService: UploadService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/uploads" / id / "tags" meta {
        summary = "Set tags"
        description = "Replaces all tags on an upload."
        receiving(tagsRequestLens to TagsRequest(tags = listOf("family", "2026-summer")))
    } bindContract PATCH to { uploadId: UUID, _: String ->
        { request: Request ->
            try {
                val body = try { tagsRequestLens(request) } catch (_: Exception) { null }
                if (body == null) {
                    Response(BAD_REQUEST).body("Malformed JSON")
                } else {
                    when (val result = validateTags(body.tags)) {
                        is TagValidationResult.Invalid ->
                            Response(BAD_REQUEST)
                                .header("Content-Type", "application/json")
                                .body("""{"error":"invalid tag","tag":"${result.tag}","reason":"${result.reason}"}""")
                        is TagValidationResult.Valid -> {
                            val userId = request.authUserId()
                            if (!uploadService.updateTags(uploadId, body.tags, userId)) {
                                Response(NOT_FOUND)
                            } else {
                                val record = uploadService.findUploadForUser(uploadId, userId)!!
                                Response(OK)
                                    .header("Content-Type", "application/json")
                                    .body(record.toJson())
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Response(INTERNAL_SERVER_ERROR).body("Failed to update tags: ${e.message}")
            }
        }
    }
}
