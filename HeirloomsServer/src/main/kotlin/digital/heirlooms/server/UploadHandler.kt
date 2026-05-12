package digital.heirlooms.server

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import org.http4k.contract.ContractRoute
import org.http4k.contract.contract
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.contract.openapi.ApiInfo
import org.http4k.contract.openapi.v3.OpenApi3
import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.PATCH
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.CREATED
import org.http4k.core.Status.Companion.FORBIDDEN
import org.http4k.core.Status.Companion.FOUND
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.CONFLICT
import org.http4k.core.Status.Companion.NOT_IMPLEMENTED
import org.http4k.core.Status.Companion.NO_CONTENT
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.PARTIAL_CONTENT
import org.http4k.core.Status.Companion.UNPROCESSABLE_ENTITY
import org.http4k.format.Jackson
import org.http4k.format.Jackson.auto
import org.http4k.lens.Path
import org.http4k.lens.binary
import org.http4k.lens.uuid
import org.http4k.routing.ResourceLoader
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.static
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID

private const val SWAGGER_UI_VERSION = "5.11.8"

private fun UploadPage.toJson(): String {
    val mapper = ObjectMapper()
    val node = mapper.createObjectNode()
    val arr = node.putArray("items")
    items.forEach { arr.add(mapper.readTree(it.toJson())) }
    if (nextCursor != null) node.put("next_cursor", nextCursor) else node.putNull("next_cursor")
    return node.toString()
}

private val PROCESSING_SUPPORTED_MIME_TYPES = THUMBNAIL_SUPPORTED_MIME_TYPES + METADATA_SUPPORTED_MIME_TYPES

private fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(bytes).joinToString("") { "%02x".format(it) }
}

private val swaggerInitializerJs = """
window.onload = function() {
  window.ui = SwaggerUIBundle({
    url: "/docs/api.json",
    dom_id: '#swagger-ui',
    deepLinking: true,
    presets: [SwaggerUIBundle.presets.apis, SwaggerUIStandalonePreset],
    plugins: [SwaggerUIBundle.plugins.DownloadUrl],
    layout: "StandaloneLayout",
    persistAuthorization: true,
    tryItOutEnabled: true
  });
};
""".trimIndent()

fun buildApp(
    storage: FileStore,
    database: Database,
    thumbnailGenerator: (ByteArray, String) -> ByteArray? = ::generateThumbnail,
    metadataExtractor: (ByteArray, String) -> MediaMetadata = MetadataExtractor()::extract,
    previewDurationSeconds: Int = 15,
    authSecret: ByteArray = ByteArray(32),
): HttpHandler {
    val directUpload = storage as? DirectUploadSupport

    val contentContract = contract {
        renderer = OpenApi3(ApiInfo("Heirlooms API", "v1"), Jackson)
        descriptionPath = "/openapi.json"
        routes += listOf(
            uploadContractRoute(storage, database, thumbnailGenerator, metadataExtractor),
            listUploadsContractRoute(storage, database),
            listTagsContractRoute(database),
            listCompostedUploadsContractRoute(database),
            getUploadByIdContractRoute(database),
            prepareUploadContractRoute(directUpload),
            initiateUploadContractRoute(directUpload, database),
            resumableUploadContractRoute(directUpload),
            confirmUploadContractRoute(storage, database, thumbnailGenerator, metadataExtractor),
            migrateUploadContractRoute(storage, database),
            fileProxyContractRoute(storage, database),
            thumbProxyContractRoute(storage, database),
            previewProxyContractRoute(storage, database),
            readUrlContractRoute(directUpload, database),
            rotationContractRoute(database),
            tagsContractRoute(database),
            viewUploadContractRoute(database),
            capsuleReverseLookupRoute(database),
            compostUploadContractRoute(database),
            restoreUploadContractRoute(database),
            shareUploadContractRoute(database),
        )
    }

    val capsuleContract = contract {
        renderer = OpenApi3(ApiInfo("Heirlooms API", "v1"), Jackson)
        descriptionPath = "/openapi.json"
        routes += capsuleRoutes(database) + plotRoutes(database) + flowRoutes(database) + plotItemRoutes(database) + sharedPlotRoutes(database)
    }

    val keysContract = contract {
        renderer = OpenApi3(ApiInfo("Heirlooms API", "v1"), Jackson)
        descriptionPath = "/openapi.json"
        routes += keysRoutes(database) + sharingKeyRoutes(database)
    }

    val socialContract = contract {
        renderer = OpenApi3(ApiInfo("Heirlooms API", "v1"), Jackson)
        descriptionPath = "/openapi.json"
        routes += friendsRoutes(database)
    }

    val authContract = contract {
        renderer = OpenApi3(ApiInfo("Heirlooms API", "v1"), Jackson)
        descriptionPath = "/openapi.json"
        routes += authRoutes(database, authSecret)
    }

    val diagContract = contract {
        renderer = OpenApi3(ApiInfo("Heirlooms API", "v1"), Jackson)
        descriptionPath = "/openapi.json"
        routes += listOf(
            "/diagnostics/events" meta { summary = "Post a diagnostic event" } bindContract POST to { req ->
                try {
                    val node = com.fasterxml.jackson.databind.ObjectMapper().readTree(req.bodyString())
                    database.insertDiagEvent(
                        deviceLabel = node?.get("deviceLabel")?.asText() ?: "",
                        tag         = node?.get("tag")?.asText()         ?: "unknown",
                        message     = node?.get("message")?.asText()     ?: "",
                        detail      = node?.get("detail")?.asText()      ?: "",
                        userId      = req.authUserId(),
                    )
                    Response(CREATED)
                } catch (e: Exception) {
                    Response(INTERNAL_SERVER_ERROR).body(e.message ?: "error")
                }
            },
            "/diagnostics/events" meta { summary = "List diagnostic events" } bindContract GET to { req ->
                try {
                    val events = database.listDiagEvents(userId = req.authUserId())
                    val json = com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(events)
                    Response(OK).header("Content-Type", "application/json").body(json)
                } catch (e: Exception) {
                    Response(INTERNAL_SERVER_ERROR).body(e.message ?: "error")
                }
            },
        )
    }

    return routes(
        "/api/content" bind contentContract,
        "/api/keys" bind keysContract,
        "/api/auth" bind authContract,
        "/api" bind capsuleContract,
        "/api" bind socialContract,
        "/api" bind diagContract,
        "/health" bind GET to { Response(OK).body("ok") },
        "/api/settings" bind GET to { Response(OK).header("Content-Type", "application/json").body("""{"previewDurationSeconds":$previewDurationSeconds}""") },
        "/docs/api.json" bind GET to { mergedSpecWithApiKeyAuth(contentContract, capsuleContract, keysContract) },
        "/docs" bind GET to { Response(FOUND).header("Location", "/docs/index.html") },
        "/docs/swagger-initializer.js" bind GET to {
            Response(OK).header("Content-Type", "application/javascript").body(swaggerInitializerJs)
        },
        "/docs" bind static(ResourceLoader.Classpath("META-INF/resources/webjars/swagger-ui/$SWAGGER_UI_VERSION")),
    )
}

private fun mergedSpecWithApiKeyAuth(
    contentContract: HttpHandler,
    capsuleContract: HttpHandler,
    keysContract: HttpHandler,
): Response {
    val mapper = ObjectMapper()
    val factory = JsonNodeFactory.instance

    val contentSpec = mapper.readTree(
        contentContract(Request(GET, "/openapi.json")).bodyString()
    ) as? ObjectNode ?: return Response(INTERNAL_SERVER_ERROR).body("Failed to generate content spec")

    val capsuleSpec = mapper.readTree(
        capsuleContract(Request(GET, "/openapi.json")).bodyString()
    ) as? ObjectNode ?: return Response(INTERNAL_SERVER_ERROR).body("Failed to generate capsule spec")

    val keysSpec = mapper.readTree(
        keysContract(Request(GET, "/openapi.json")).bodyString()
    ) as? ObjectNode ?: return Response(INTERNAL_SERVER_ERROR).body("Failed to generate keys spec")

    // Merge paths: prefix content paths with /api/content, capsule paths with /api, keys with /api/keys
    val mergedPaths = factory.objectNode()
    (contentSpec.get("paths") as? ObjectNode)?.fields()?.forEach { (path, item) ->
        mergedPaths.set<ObjectNode>("/api/content$path", item)
    }
    (capsuleSpec.get("paths") as? ObjectNode)?.fields()?.forEach { (path, item) ->
        mergedPaths.set<ObjectNode>("/api$path", item)
    }
    (keysSpec.get("paths") as? ObjectNode)?.fields()?.forEach { (path, item) ->
        mergedPaths.set<ObjectNode>("/api/keys$path", item)
    }
    contentSpec.set<ObjectNode>("paths", mergedPaths)

    // Merge component schemas
    val contentComponents = contentSpec.get("components") as? ObjectNode ?: factory.objectNode()
    val contentSchemas = contentComponents.get("schemas") as? ObjectNode ?: factory.objectNode()
    (capsuleSpec.get("components") as? ObjectNode)?.get("schemas")?.fields()?.forEach { (name, schema) ->
        contentSchemas.set<ObjectNode>(name, schema)
    }
    (keysSpec.get("components") as? ObjectNode)?.get("schemas")?.fields()?.forEach { (name, schema) ->
        contentSchemas.set<ObjectNode>(name, schema)
    }
    contentComponents.set<ObjectNode>("schemas", contentSchemas)

    val apiKeyScheme = factory.objectNode().apply {
        put("type", "apiKey")
        put("in", "header")
        put("name", "X-Api-Key")
    }
    contentComponents.set<ObjectNode>("securitySchemes", factory.objectNode().apply {
        set<ObjectNode>("ApiKeyAuth", apiKeyScheme)
    })
    contentSpec.set<ObjectNode>("components", contentComponents)
    contentSpec.set<ArrayNode>("security", factory.arrayNode().add(
        factory.objectNode().apply { set<ArrayNode>("ApiKeyAuth", factory.arrayNode()) }
    ))
    contentSpec.set<ArrayNode>("servers", factory.arrayNode().add(
        factory.objectNode().apply { put("url", "/") }
    ))

    // Remove per-operation "security": [] entries — empty array overrides global security block.
    (contentSpec.get("paths") as? ObjectNode)?.fields()?.forEach { (_, pathItem) ->
        (pathItem as? ObjectNode)?.fields()?.forEach { (_, operation) ->
            (operation as? ObjectNode)?.remove("security")
        }
    }

    return Response(OK).header("Content-Type", "application/json").body(contentSpec.toString())
}

private fun uploadContractRoute(
    storage: FileStore,
    database: Database,
    thumbnailGenerator: (ByteArray, String) -> ByteArray?,
    metadataExtractor: (ByteArray, String) -> MediaMetadata,
): ContractRoute =
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
    } bindContract POST to uploadHandler(storage, database, thumbnailGenerator, metadataExtractor)

private fun listUploadsContractRoute(storage: FileStore, database: Database): ContractRoute =
    "/uploads" meta {
        summary = "List uploads"
        description = "Returns uploads as a cursor-paginated JSON object. Query params: `cursor`, `limit` (1–200, default 50), `tag` (comma-separated, any-match), `exclude_tag`, `from_date` (ISO date, inclusive), `to_date` (ISO date, inclusive), `in_capsule` (true|false), `include_composted` (true), `has_location` (true|false), `sort` (upload_newest|upload_oldest|taken_newest|taken_oldest), `just_arrived` (true)."
    } bindContract GET to listUploadsHandler(storage, database)

private fun listTagsContractRoute(database: Database): ContractRoute =
    "/uploads/tags" meta {
        summary = "List all tags"
        description = "Returns all distinct tags used across non-composted uploads, sorted alphabetically."
    } bindContract GET to { request: Request ->
        try {
            val userId = request.authUserId()
            val tags = database.listAllTags(userId)
            val json = com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(tags)
            Response(OK).header("Content-Type", "application/json").body(json)
        } catch (e: Exception) {
            Response(INTERNAL_SERVER_ERROR).body("Failed to list tags: ${e.message}")
        }
    }

private fun getUploadByIdContractRoute(database: Database): ContractRoute {
    val id = Path.uuid().of("id")
    return "/uploads" / id meta {
        summary = "Get upload by ID"
        description = "Returns a single upload by ID regardless of composted state. Returns 404 if not found."
    } bindContract GET to { uploadId: UUID ->
        { request: Request ->
            val record = database.findUploadByIdForUser(uploadId, request.authUserId())
            if (record == null) Response(NOT_FOUND)
            else Response(OK).header("Content-Type", "application/json").body(record.toJson())
        }
    }
}

private fun listCompostedUploadsContractRoute(database: Database): ContractRoute =
    "/uploads/composted" meta {
        summary = "List composted uploads"
        description = "Returns composted uploads as a cursor-paginated JSON object. Query params: `cursor`, `limit` (1–200, default 50)."
    } bindContract GET to listCompostedUploadsHandler(database)

private fun uploadHandler(
    storage: FileStore,
    database: Database,
    thumbnailGenerator: (ByteArray, String) -> ByteArray?,
    metadataExtractor: (ByteArray, String) -> MediaMetadata,
): HttpHandler = { request: Request ->
    val body = request.body.payload.array()

    if (body.isEmpty()) {
        Response(BAD_REQUEST).body("Request body is empty")
    } else {
        val mimeType = request.header("Content-Type")
            ?.substringBefore(";")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "application/octet-stream"

        val userId = request.authUserId()
        val hash = sha256Hex(body)
        val existing = database.findByContentHash(hash, userId)
        if (existing != null) {
            Response(CONFLICT)
                .header("Content-Type", "application/json")
                .body("""{"storageKey":"${existing.storageKey}"}""")
        } else {
            try {
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
                database.recordUpload(record, userId)
                Response(CREATED)
                    .header("Content-Type", "application/json")
                    .body(record.toJson())
            } catch (e: Exception) {
                Response(INTERNAL_SERVER_ERROR).body("Failed to store file: ${e.message}")
            }
        }
    }
}

private fun listUploadsHandler(storage: FileStore, database: Database): HttpHandler = handler@{ request ->
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
            database.listUploadsPaginated(
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
        launchCompostCleanup(storage, database)
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

private fun listCompostedUploadsHandler(database: Database): HttpHandler = { request ->
    try {
        val cursor = request.query("cursor")?.takeIf { it.isNotBlank() }
        val limit = request.query("limit")?.toIntOrNull()?.coerceIn(1, 200) ?: 50
        val page = database.listCompostedUploadsPaginated(cursor = cursor, limit = limit, userId = request.authUserId())
        Response(OK).header("Content-Type", "application/json").body(page.toJson())
    } catch (e: Exception) {
        Response(INTERNAL_SERVER_ERROR).body("Failed to list composted uploads: ${e.message}")
    }
}

private fun launchCompostCleanup(storage: FileStore, database: Database) {
    Thread {
        try {
            val expired = database.fetchExpiredCompostedUploads()
            for (record in expired) {
                try {
                    val hasLiveRef = database.hasLiveSharedReference(record.storageKey, record.id)
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
                    database.hardDeleteUpload(record.id)
                    println("[compost-cleanup] INFO: hard-deleted upload ${record.id} (composted ${record.compostedAt})")
                } catch (e: Exception) {
                    println("[compost-cleanup] WARNING: failed to hard-delete upload ${record.id}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            println("[compost-cleanup] ERROR: cleanup failed: ${e.message}")
        }
    }.also { it.isDaemon = true }.start()
}

private fun compostUploadContractRoute(database: Database): ContractRoute {
    val id = Path.uuid().of("id")
    return "/uploads" / id / "compost" meta {
        summary = "Compost an upload"
        description = "Soft-deletes an upload. Requires no tags and no active capsule memberships."
    } bindContract POST to { uploadId: UUID, _: String ->
        { request: Request ->
            try {
                when (val result = database.compostUpload(uploadId, request.authUserId())) {
                    is Database.CompostResult.Success ->
                        Response(OK).header("Content-Type", "application/json").body(result.record.toJson())
                    is Database.CompostResult.NotFound ->
                        Response(NOT_FOUND)
                    is Database.CompostResult.AlreadyComposted ->
                        Response(CONFLICT).header("Content-Type", "application/json")
                            .body("""{"error":"Upload is already composted"}""")
                    is Database.CompostResult.PreconditionFailed ->
                        Response(UNPROCESSABLE_ENTITY).header("Content-Type", "application/json")
                            .body("""{"error":"Cannot compost: upload has tags or is in active capsules"}""")
                }
            } catch (e: Exception) {
                Response(INTERNAL_SERVER_ERROR).body("Failed to compost upload: ${e.message}")
            }
        }
    }
}

private fun restoreUploadContractRoute(database: Database): ContractRoute {
    val id = Path.uuid().of("id")
    return "/uploads" / id / "restore" meta {
        summary = "Restore a composted upload"
        description = "Removes composted_at from an upload, returning it to the active garden."
    } bindContract POST to { uploadId: UUID, _: String ->
        { request: Request ->
            try {
                when (val result = database.restoreUpload(uploadId, request.authUserId())) {
                    is Database.RestoreResult.Success ->
                        Response(OK).header("Content-Type", "application/json").body(result.record.toJson())
                    is Database.RestoreResult.NotFound ->
                        Response(NOT_FOUND)
                    is Database.RestoreResult.NotComposted ->
                        Response(CONFLICT).header("Content-Type", "application/json")
                            .body("""{"error":"Upload is not composted"}""")
                }
            } catch (e: Exception) {
                Response(INTERNAL_SERVER_ERROR).body("Failed to restore upload: ${e.message}")
            }
        }
    }
}

private fun shareUploadContractRoute(database: Database): ContractRoute {
    val id = Path.uuid().of("id")
    return "/uploads" / id / "share" meta {
        summary = "Share an upload with a friend"
        description = "Creates a recipient upload record with a re-wrapped DEK. Requester must own the upload and be friends with the recipient."
    } bindContract POST to { uploadId: UUID, _: String ->
        { request: Request -> handleShareUpload(uploadId, request, database) }
    }
}

private fun handleShareUpload(uploadId: UUID, request: Request, database: Database): Response {
    return try {
        val requesterId = request.authUserId()
            ?: return Response(FORBIDDEN)
        val record = database.findUploadByIdForUser(uploadId, requesterId)
            ?: return Response(NOT_FOUND)
        val node = ObjectMapper().readTree(request.bodyString())
        val toUserIdStr = node?.get("toUserId")?.asText()
        val wrappedDekB64 = node?.get("wrappedDek")?.asText()
        val wrappedThumbnailDekB64 = node?.get("wrappedThumbnailDek")?.asText()
        val dekFormat = node?.get("dekFormat")?.asText()
        if (toUserIdStr.isNullOrBlank() || wrappedDekB64.isNullOrBlank() || dekFormat.isNullOrBlank())
            return Response(BAD_REQUEST).body("Missing required fields")
        val toUserId = runCatching { UUID.fromString(toUserIdStr) }.getOrNull()
            ?: return Response(BAD_REQUEST).body("Invalid toUserId")
        if (!database.areFriends(requesterId, toUserId))
            return Response(FORBIDDEN).body("Not friends")
        if (database.userAlreadyHasStorageKey(toUserId, record.storageKey))
            return Response(CONFLICT).body("Recipient already has this item")
        val dec = Base64.getDecoder()
        val wrappedDek = runCatching { dec.decode(wrappedDekB64) }.getOrNull()
            ?: return Response(BAD_REQUEST).body("wrappedDek is not valid Base64")
        val wrappedThumbnailDek = if (!wrappedThumbnailDekB64.isNullOrBlank())
            runCatching { dec.decode(wrappedThumbnailDekB64) }.getOrNull() else null
        val rotationOverride = node?.get("rotation")?.asInt()
        val shared = database.createSharedUpload(record, requesterId, toUserId, wrappedDek, wrappedThumbnailDek, dekFormat, rotationOverride)
        Response(CREATED).header("Content-Type", "application/json").body(shared.toJson())
    } catch (e: Exception) {
        Response(INTERNAL_SERVER_ERROR).body("shareUpload failed: ${e.message}")
    }
}

private fun fileProxyContractRoute(storage: FileStore, database: Database): ContractRoute {
    val id = Path.uuid().of("id")
    return "/uploads" / id / "file" meta {
        summary = "Get file"
        description = "Streams the raw file bytes for the given upload ID with the correct Content-Type."
    } bindContract GET to { uploadId: UUID, _: String ->
        { request: Request ->
            val record = database.findUploadByIdForUser(uploadId, request.authUserId())
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

private fun thumbProxyContractRoute(storage: FileStore, database: Database): ContractRoute {
    val id = Path.uuid().of("id")
    return "/uploads" / id / "thumb" meta {
        summary = "Get thumbnail"
        description = "Returns the JPEG thumbnail for the given upload ID if one exists, otherwise falls back to the full file."
    } bindContract GET to { uploadId: UUID, _: String ->
        { request: Request ->
            val record = database.findUploadByIdForUser(uploadId, request.authUserId())
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

private fun previewProxyContractRoute(storage: FileStore, database: Database): ContractRoute {
    val id = Path.uuid().of("id")
    return "/uploads" / id / "preview" meta {
        summary = "Get encrypted preview clip"
        description = "Returns the encrypted preview clip for the given upload ID if one exists, otherwise 404."
    } bindContract GET to { uploadId: UUID, _: String ->
        { request: Request ->
            val record = database.findUploadByIdForUser(uploadId, request.authUserId())
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

private fun readUrlContractRoute(directUpload: DirectUploadSupport?, database: Database): ContractRoute {
    val id = Path.uuid().of("id")
    return "/uploads" / id / "url" meta {
        summary = "Get signed read URL"
        description = "Returns a 1-hour signed GCS URL for the given upload ID. Use directly as a video src for streaming."
    } bindContract GET to { uploadId: UUID, _: String ->
        { request: Request ->
            if (directUpload == null) {
                Response(NOT_IMPLEMENTED).body("Signed URLs not supported by the current storage backend")
            } else {
                val record = database.findUploadByIdForUser(uploadId, request.authUserId())
                if (record == null) {
                    Response(NOT_FOUND)
                } else {
                    try {
                        val url = directUpload.generateReadUrl(StorageKey(record.storageKey))
                        Response(OK)
                            .header("Content-Type", "application/json")
                            .body("""{"url":"$url"}""")
                    } catch (e: Exception) {
                        Response(INTERNAL_SERVER_ERROR).body("Failed to generate URL: ${e.message}")
                    }
                }
            }
        }
    }
}

private fun prepareUploadContractRoute(directUpload: DirectUploadSupport?): ContractRoute =
    "/uploads/prepare" meta {
        summary = "Prepare a direct upload"
        description = "Returns a signed GCS URL the client can PUT the file to directly, bypassing the 32 MB Cloud Run limit. Body: {\"mimeType\":\"video/mp4\"}."
    } bindContract POST to prepareUploadHandler(directUpload)

private fun initiateUploadContractRoute(directUpload: DirectUploadSupport?, database: Database): ContractRoute =
    "/uploads/initiate" meta {
        summary = "Initiate an upload (E2EE-aware)"
        description = "Returns signed upload URL(s). For encrypted: two URLs (content + thumbnail). For legacy: one URL. Body: {\"mimeType\":\"...\",\"storage_class\":\"encrypted\"} or {\"mimeType\":\"...\"}."
    } bindContract POST to initiateUploadHandler(directUpload, database)

private fun resumableUploadContractRoute(directUpload: DirectUploadSupport?): ContractRoute =
    "/uploads/resumable" meta {
        summary = "Initiate a resumable (chunked) upload"
        description = "Creates a GCS resumable upload session and returns the session URI. Body: {\"storageKey\":\"...\",\"totalBytes\":...,\"contentType\":\"...\"}. The client PUTs ciphertext chunks directly to the returned URI."
    } bindContract POST to resumableUploadHandler(directUpload)

private fun resumableUploadHandler(directUpload: DirectUploadSupport?): HttpHandler = { request ->
    if (directUpload == null) {
        Response(NOT_IMPLEMENTED).body("Resumable uploads not supported by the current storage backend")
    } else {
        try {
            val node = ObjectMapper().readTree(request.bodyString())
            val storageKey = node?.get("storageKey")?.asText()
            val totalBytes = node?.get("totalBytes")?.asLong()
            val contentType = node?.get("contentType")?.asText()

            when {
                storageKey.isNullOrBlank() || totalBytes == null || contentType.isNullOrBlank() ->
                    Response(BAD_REQUEST).body("Missing storageKey, totalBytes, or contentType in request body")
                totalBytes <= 0 ->
                    Response(BAD_REQUEST).body("totalBytes must be positive")
                else -> {
                    val resumableUri = directUpload.initiateResumableUpload(StorageKey(storageKey), totalBytes, contentType)
                    Response(OK)
                        .header("Content-Type", "application/json")
                        .body("""{"resumableUri":"$resumableUri"}""")
                }
            }
        } catch (e: Exception) {
            Response(INTERNAL_SERVER_ERROR).body("Failed to initiate resumable upload: ${e.message}")
        }
    }
}

private fun migrateUploadContractRoute(storage: FileStore, database: Database): ContractRoute {
    val id = Path.uuid().of("id")
    return "/uploads" / id / "migrate" meta {
        summary = "Migrate a legacy upload to encrypted"
        description = "Atomically replaces a public upload's bytes with an encrypted envelope. Returns 409 if already encrypted. Returns 410 if the record was concurrently migrated."
    } bindContract POST to { uploadId: UUID, _: String ->
        { request: Request ->
            migrateUploadHandler(uploadId, request, storage, database)
        }
    }
}

private fun initiateUploadHandler(directUpload: DirectUploadSupport?, database: Database): HttpHandler = { request ->
    if (directUpload == null) {
        Response(NOT_IMPLEMENTED).body("Direct upload not supported by the current storage backend")
    } else {
        try {
            val node = ObjectMapper().readTree(request.bodyString())
            val mimeType = node?.get("mimeType")?.asText()
            val storageClassRaw = node?.get("storage_class")?.asText()

            when {
                mimeType.isNullOrBlank() ->
                    Response(BAD_REQUEST).body("Missing or invalid mimeType in request body")
                storageClassRaw == "public" ->
                    Response(BAD_REQUEST).body("Cannot use public storage class — omit storage_class or use encrypted")
                storageClassRaw == "encrypted" -> {
                    val content = directUpload.prepareUpload(mimeType)
                    val thumb = directUpload.prepareUpload("application/octet-stream")
                    database.insertPendingBlob(content.storageKey.value)
                    database.insertPendingBlob(thumb.storageKey.value)
                    val json = """{"storageKey":"${content.storageKey}","uploadUrl":"${content.uploadUrl}","thumbnailStorageKey":"${thumb.storageKey}","thumbnailUploadUrl":"${thumb.uploadUrl}"}"""
                    Response(OK).header("Content-Type", "application/json").body(json)
                }
                else -> {
                    // Public shape: no storage_class or unrecognised value — server-assigned
                    val prepared = directUpload.prepareUpload(mimeType)
                    database.insertPendingBlob(prepared.storageKey.value)
                    val json = """{"storageKey":"${prepared.storageKey}","uploadUrl":"${prepared.uploadUrl}"}"""
                    Response(OK).header("Content-Type", "application/json").body(json)
                }
            }
        } catch (e: Exception) {
            Response(INTERNAL_SERVER_ERROR).body("Failed to initiate upload: ${e.message}")
        }
    }
}

private fun migrateUploadHandler(uploadId: UUID, request: Request, storage: FileStore, database: Database): Response {
    return try {
        val existing = database.findUploadByIdForUser(uploadId, request.authUserId())
            ?: return Response(NOT_FOUND)
        if (existing.storageClass != "public")
            return Response(CONFLICT).header("Content-Type", "application/json")
                .body("""{"error":"upload is already migrated or wrong storage class"}""")

        val node = ObjectMapper().readTree(request.bodyString())
        val newStorageKey = node?.get("newStorageKey")?.asText()
        val contentHash = node?.get("contentHash")?.asText()?.takeIf { it.isNotBlank() }
        val envelopeVersion = node?.get("envelopeVersion")?.asInt()
        val wrappedDekB64 = node?.get("wrappedDek")?.asText()
        val dekFormat = node?.get("dekFormat")?.asText()
        val encryptedMetaB64 = node?.get("encryptedMetadata")?.asText()
        val encryptedMetaFormat = node?.get("encryptedMetadataFormat")?.asText()
        val thumbStorageKey = node?.get("thumbnailStorageKey")?.asText()?.takeIf { it.isNotBlank() }
        val wrappedThumbDekB64 = node?.get("wrappedThumbnailDek")?.asText()?.takeIf { it.isNotBlank() }
        val thumbDekFormat = node?.get("thumbnailDekFormat")?.asText()?.takeIf { it.isNotBlank() }

        if (newStorageKey.isNullOrBlank() || wrappedDekB64.isNullOrBlank() || dekFormat.isNullOrBlank() || envelopeVersion == null)
            return Response(BAD_REQUEST).body("Missing required fields: newStorageKey, envelopeVersion, wrappedDek, dekFormat")

        val dec = Base64.getDecoder()
        val wrappedDek = try { dec.decode(wrappedDekB64) } catch (_: Exception) {
            return Response(BAD_REQUEST).body("wrappedDek is not valid Base64")
        }
        val encryptedMeta = encryptedMetaB64?.let {
            try { dec.decode(it) } catch (_: Exception) {
                return Response(BAD_REQUEST).body("encryptedMetadata is not valid Base64")
            }
        }
        val wrappedThumbDek = wrappedThumbDekB64?.let {
            try { dec.decode(it) } catch (_: Exception) {
                return Response(BAD_REQUEST).body("wrappedThumbnailDek is not valid Base64")
            }
        }

        try { EnvelopeFormat.validateSymmetric(wrappedDek, dekFormat) } catch (e: EnvelopeFormatException) {
            return Response(BAD_REQUEST).body("wrappedDek envelope invalid: ${e.message}")
        }
        if (encryptedMeta != null && encryptedMetaFormat != null) {
            try { EnvelopeFormat.validateSymmetric(encryptedMeta, encryptedMetaFormat) } catch (e: EnvelopeFormatException) {
                return Response(BAD_REQUEST).body("encryptedMetadata envelope invalid: ${e.message}")
            }
        }
        if (wrappedThumbDek != null && thumbDekFormat != null) {
            try { EnvelopeFormat.validateSymmetric(wrappedThumbDek, thumbDekFormat) } catch (e: EnvelopeFormatException) {
                return Response(BAD_REQUEST).body("wrappedThumbnailDek envelope invalid: ${e.message}")
            }
        }

        val oldStorageKey = existing.storageKey
        val oldThumbnailKey = existing.thumbnailKey

        val migrated = database.migrateUploadToEncrypted(
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
        if (!migrated)
            return Response(CONFLICT).header("Content-Type", "application/json")
                .body("""{"error":"upload was already migrated concurrently"}""")

        // Delete old plaintext blobs (best effort — GCS failure does not roll back the DB update)
        try { storage.delete(StorageKey(oldStorageKey)) } catch (e: Exception) {
            println("[migrate] WARNING: failed to delete old blob $oldStorageKey: ${e.message}")
        }
        if (oldThumbnailKey != null) {
            try { storage.delete(StorageKey(oldThumbnailKey)) } catch (e: Exception) {
                println("[migrate] WARNING: failed to delete old thumbnail $oldThumbnailKey: ${e.message}")
            }
        }

        // Clean up pending_blobs entries for the new ciphertext keys
        try { database.deletePendingBlob(newStorageKey) } catch (_: Exception) {}
        if (thumbStorageKey != null) {
            try { database.deletePendingBlob(thumbStorageKey) } catch (_: Exception) {}
        }

        val updated = database.findUploadByIdForUser(uploadId, request.authUserId())
            ?: return Response(NOT_FOUND)
        Response(OK).header("Content-Type", "application/json").body(updated.toJson())
    } catch (e: Exception) {
        Response(INTERNAL_SERVER_ERROR).body("Failed to migrate upload: ${e.message}")
    }
}

private fun confirmUploadContractRoute(
    storage: FileStore,
    database: Database,
    thumbnailGenerator: (ByteArray, String) -> ByteArray?,
    metadataExtractor: (ByteArray, String) -> MediaMetadata,
): ContractRoute =
    "/uploads/confirm" meta {
        summary = "Confirm a direct upload"
        description = "Records upload metadata after the client has PUT the file directly to GCS. Body: {\"storageKey\":\"...\",\"mimeType\":\"...\",\"fileSize\":...,\"contentHash\":\"<sha256-hex>\"}. If contentHash matches an existing upload, returns 409 with the existing storageKey."
    } bindContract POST to confirmUploadHandler(storage, database, thumbnailGenerator, metadataExtractor)

private fun prepareUploadHandler(directUpload: DirectUploadSupport?): HttpHandler = { request: Request ->
    if (directUpload == null) {
        Response(NOT_IMPLEMENTED).body("Direct upload not supported by the current storage backend")
    } else {
        val mimeType = try {
            ObjectMapper().readTree(request.bodyString())?.get("mimeType")?.asText()
        } catch (_: Exception) { null }

        if (mimeType.isNullOrBlank()) {
            Response(BAD_REQUEST).body("Missing or invalid mimeType in request body")
        } else {
            try {
                val prepared = directUpload.prepareUpload(mimeType)
                val json = """{"storageKey":"${prepared.storageKey}","uploadUrl":"${prepared.uploadUrl}"}"""
                Response(OK).header("Content-Type", "application/json").body(json)
            } catch (e: Exception) {
                Response(INTERNAL_SERVER_ERROR).body("Failed to prepare upload: ${e.message}")
            }
        }
    }
}

private fun confirmUploadHandler(
    storage: FileStore,
    database: Database,
    thumbnailGenerator: (ByteArray, String) -> ByteArray?,
    metadataExtractor: (ByteArray, String) -> MediaMetadata,
): HttpHandler = { request: Request ->
    try {
        confirmUpload(request, request.authUserId(), storage, database, thumbnailGenerator, metadataExtractor)
    } catch (e: Exception) {
        Response(INTERNAL_SERVER_ERROR).body("Failed to confirm upload: ${e.message}")
    }
}

private fun confirmUpload(
    request: Request,
    userId: java.util.UUID,
    storage: FileStore,
    database: Database,
    thumbnailGenerator: (ByteArray, String) -> ByteArray?,
    metadataExtractor: (ByteArray, String) -> MediaMetadata,
): Response {
    val node = ObjectMapper().readTree(request.bodyString())
    val storageKey = node?.get("storageKey")?.asText()
    val mimeType = node?.get("mimeType")?.asText()
    val fileSize = node?.get("fileSize")?.asLong()
    val contentHash = node?.get("contentHash")?.asText()?.takeIf { it.isNotBlank() }
    val storageClassRaw = node?.get("storage_class")?.asText()
    val tags = node?.get("tags")?.takeIf { it.isArray }
        ?.map { it.asText() }
        ?.filter { it.isNotBlank() }
        ?: emptyList()

    val tagValidation = validateTags(tags)
    if (storageKey.isNullOrBlank() || mimeType.isNullOrBlank() || fileSize == null)
        return Response(BAD_REQUEST).body("Missing storageKey, mimeType, or fileSize in request body")
    if (tagValidation is TagValidationResult.Invalid)
        return Response(BAD_REQUEST).header("Content-Type", "application/json")
            .body("""{"error":"invalid tag","tag":"${tagValidation.tag}","reason":"${tagValidation.reason}"}""")

    return if (storageClassRaw == "encrypted") {
        confirmEncryptedUpload(node, storageKey, mimeType, fileSize, contentHash, tags, database, userId)
    } else {
        confirmLegacyUpload(storageKey, mimeType, fileSize, contentHash, tags, storage, database, thumbnailGenerator, metadataExtractor, userId)
    }
}

private fun confirmEncryptedUpload(
    node: com.fasterxml.jackson.databind.JsonNode?,
    storageKey: String,
    mimeType: String,
    fileSize: Long,
    contentHash: String?,
    tags: List<String>,
    database: Database,
    userId: java.util.UUID,
): Response {
    val dec = Base64.getDecoder()
    val envelopeVersion = node?.get("envelopeVersion")?.asInt()
    val wrappedDekB64 = node?.get("wrappedDek")?.asText()
    val dekFormat = node?.get("dekFormat")?.asText()
    val encryptedMetaB64 = node?.get("encryptedMetadata")?.asText()?.takeIf { it.isNotBlank() }
    val encryptedMetaFormat = node?.get("encryptedMetadataFormat")?.asText()?.takeIf { it.isNotBlank() }
    val thumbStorageKey = node?.get("thumbnailStorageKey")?.asText()?.takeIf { it.isNotBlank() }
    val wrappedThumbDekB64 = node?.get("wrappedThumbnailDek")?.asText()?.takeIf { it.isNotBlank() }
    val thumbDekFormat = node?.get("thumbnailDekFormat")?.asText()?.takeIf { it.isNotBlank() }
    val previewStorageKey = node?.get("previewStorageKey")?.asText()?.takeIf { it.isNotBlank() }
    val wrappedPreviewDekB64 = node?.get("wrappedPreviewDek")?.asText()?.takeIf { it.isNotBlank() }
    val previewDekFormat = node?.get("previewDekFormat")?.asText()?.takeIf { it.isNotBlank() }
    val plainChunkSize = node?.get("plainChunkSize")?.asInt()?.takeIf { it > 0 }
    val durationSeconds = node?.get("durationSeconds")?.asInt()?.takeIf { it > 0 }
    val takenAt = node?.get("takenAt")?.asText()?.takeIf { it.isNotBlank() }
        ?.let { runCatching { Instant.parse(it) }.getOrNull() }

    if (envelopeVersion == null || wrappedDekB64.isNullOrBlank() || dekFormat.isNullOrBlank())
        return Response(BAD_REQUEST).body("Encrypted confirm requires envelopeVersion, wrappedDek, and dekFormat")

    val wrappedDek = runCatching { dec.decode(wrappedDekB64) }.getOrNull()
        ?: return Response(BAD_REQUEST).body("wrappedDek is not valid Base64")
    val encryptedMeta = encryptedMetaB64?.let {
        runCatching { dec.decode(it) }.getOrNull()
            ?: return Response(BAD_REQUEST).body("encryptedMetadata is not valid Base64")
    }
    val wrappedThumbDek = wrappedThumbDekB64?.let {
        runCatching { dec.decode(it) }.getOrNull()
            ?: return Response(BAD_REQUEST).body("wrappedThumbnailDek is not valid Base64")
    }
    val wrappedPreviewDek = wrappedPreviewDekB64?.let {
        runCatching { dec.decode(it) }.getOrNull()
            ?: return Response(BAD_REQUEST).body("wrappedPreviewDek is not valid Base64")
    }

    runCatching { EnvelopeFormat.validateSymmetric(wrappedDek, dekFormat) }.onFailure { e ->
        return Response(BAD_REQUEST).body("wrappedDek envelope invalid: ${e.message}")
    }
    if (encryptedMeta != null && encryptedMetaFormat != null) {
        runCatching { EnvelopeFormat.validateSymmetric(encryptedMeta, encryptedMetaFormat) }.onFailure { e ->
            return Response(BAD_REQUEST).body("encryptedMetadata envelope invalid: ${e.message}")
        }
    }
    if (wrappedThumbDek != null && thumbDekFormat != null) {
        runCatching { EnvelopeFormat.validateSymmetric(wrappedThumbDek, thumbDekFormat) }.onFailure { e ->
            return Response(BAD_REQUEST).body("wrappedThumbnailDek envelope invalid: ${e.message}")
        }
    }
    if (wrappedPreviewDek != null && previewDekFormat != null) {
        runCatching { EnvelopeFormat.validateSymmetric(wrappedPreviewDek, previewDekFormat) }.onFailure { e ->
            return Response(BAD_REQUEST).body("wrappedPreviewDek envelope invalid: ${e.message}")
        }
    }

    val id = UUID.randomUUID()
    database.recordUpload(
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
    if (tags.isNotEmpty()) database.updateTags(id, tags, userId)
    runCatching { database.deletePendingBlob(storageKey) }
    if (thumbStorageKey != null) runCatching { database.deletePendingBlob(thumbStorageKey) }
    if (previewStorageKey != null) runCatching { database.deletePendingBlob(previewStorageKey) }
    return Response(CREATED)
}

private fun confirmLegacyUpload(
    storageKey: String,
    mimeType: String,
    fileSize: Long,
    contentHash: String?,
    tags: List<String>,
    storage: FileStore,
    database: Database,
    thumbnailGenerator: (ByteArray, String) -> ByteArray?,
    metadataExtractor: (ByteArray, String) -> MediaMetadata,
    userId: java.util.UUID,
): Response {
    val existing = if (contentHash != null) database.findByContentHash(contentHash, userId) else null
    if (existing != null)
        return Response(CONFLICT).header("Content-Type", "application/json")
            .body("""{"storageKey":"${existing.storageKey}"}""")

    val bytes = fetchBytesIfNeeded(storageKey, mimeType, storage)
    val thumbKey = if (bytes != null) tryStoreThumbnail(bytes, mimeType, StorageKey(storageKey), storage, thumbnailGenerator) else null
    val metaBytes = fetchHeaderForMetadata(storageKey, mimeType, storage)
    val metadata = if (metaBytes != null) {
        runCatching { metadataExtractor(metaBytes, mimeType) }.getOrDefault(MediaMetadata())
    } else MediaMetadata()
    val normalizedMime = mimeType.substringBefore(";").trim().lowercase()
    val durationSeconds = if (bytes != null && normalizedMime.startsWith("video/"))
        runCatching { extractVideoDuration(bytes, mimeType) }.getOrNull()
    else null
    val id = UUID.randomUUID()
    database.recordUpload(
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
    if (tags.isNotEmpty()) database.updateTags(id, tags, userId)
    runCatching { database.deletePendingBlob(storageKey) }
    return Response(CREATED)
}

private const val EXIF_HEADER_BYTES = 65_536 // JPEG EXIF lives in APP1, always within first 64 KB

private fun fetchBytesIfNeeded(storageKey: String, mimeType: String, storage: FileStore): ByteArray? {
    val normalized = mimeType.substringBefore(";").trim().lowercase()
    if (normalized !in PROCESSING_SUPPORTED_MIME_TYPES) return null
    return try { storage.get(StorageKey(storageKey)) } catch (_: Exception) { null }
}

private fun fetchHeaderForMetadata(storageKey: String, mimeType: String, storage: FileStore): ByteArray? {
    val normalized = mimeType.substringBefore(";").trim().lowercase()
    if (normalized !in METADATA_SUPPORTED_MIME_TYPES) return null
    return try {
        if (normalized in METADATA_IMAGE_MIME_TYPES) {
            storage.getFirst(StorageKey(storageKey), EXIF_HEADER_BYTES)
        } else {
            storage.get(StorageKey(storageKey)) // videos still need the full file for ffprobe
        }
    } catch (_: Exception) { null }
}

data class RotationRequest(val rotation: Int)
data class TagsRequest(val tags: List<String>)

private val rotationRequestLens = Body.auto<RotationRequest>().toLens()
private val tagsRequestLens = Body.auto<TagsRequest>().toLens()

private val VALID_ROTATIONS = setOf(0, 90, 180, 270)

private fun rotationContractRoute(database: Database): ContractRoute {
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
                    !database.updateRotation(uploadId, body.rotation, request.authUserId()) ->
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

private fun viewUploadContractRoute(database: Database): ContractRoute {
    val id = Path.uuid().of("id")
    return "/uploads" / id / "view" meta {
        summary = "Record a detail view"
        description = "Sets last_viewed_at on the upload, removing it from the Just arrived plot. Idempotent — subsequent calls on an already-viewed item are no-ops."
    } bindContract POST to { uploadId: UUID, _: String ->
        { request: Request ->
            val viewed = database.recordView(uploadId, request.authUserId())
            if (!viewed) Response(NOT_FOUND) else Response(NO_CONTENT)
        }
    }
}

private fun tagsContractRoute(database: Database): ContractRoute {
    val id = Path.uuid().of("id")
    return "/uploads" / id / "tags" meta {
        summary = "Set tags"
        description = "Replaces all tags on an upload. Tags must be kebab-case (lowercase letters, numbers, hyphens, max 50 chars)."
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
                            if (!database.updateTags(uploadId, body.tags, userId)) {
                                Response(NOT_FOUND)
                            } else {
                                val record = database.findUploadByIdForUser(uploadId, userId)!!
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

private fun tryStoreThumbnail(
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
