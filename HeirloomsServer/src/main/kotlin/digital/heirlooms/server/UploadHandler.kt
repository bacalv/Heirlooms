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
import org.http4k.core.Status.Companion.FOUND
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.CONFLICT
import org.http4k.core.Status.Companion.NOT_IMPLEMENTED
import org.http4k.core.Status.Companion.OK
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
import java.util.UUID

private const val SWAGGER_UI_VERSION = "5.11.8"

private val PROCESSING_SUPPORTED_MIME_TYPES = THUMBNAIL_SUPPORTED_MIME_TYPES + METADATA_SUPPORTED_MIME_TYPES

private fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(bytes).joinToString("") { "%02x".format(it) }
}

private fun UploadRecord.toJson(): String = buildString {
    append("""{"id":"$id","storageKey":"$storageKey","mimeType":"$mimeType","fileSize":$fileSize,"uploadedAt":"$uploadedAt","rotation":$rotation,"thumbnailKey":${if (thumbnailKey != null) "\"$thumbnailKey\"" else "null"}""")
    val tagsJson = tags.joinToString(",") { "\"$it\"" }
    append(""","tags":[$tagsJson]""")
    if (capturedAt != null) append(""","capturedAt":"$capturedAt"""")
    if (latitude != null) append(""","latitude":$latitude""")
    if (longitude != null) append(""","longitude":$longitude""")
    if (altitude != null) append(""","altitude":$altitude""")
    if (deviceMake != null) append(""","deviceMake":"$deviceMake"""")
    if (deviceModel != null) append(""","deviceModel":"$deviceModel"""")
    append("}")
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
): HttpHandler {
    val directUpload = storage as? DirectUploadSupport

    val apiContract = contract {
        renderer = OpenApi3(ApiInfo("Heirlooms API", "v1"), Jackson)
        descriptionPath = "/openapi.json"
        routes += listOf(
            uploadContractRoute(storage, database, thumbnailGenerator, metadataExtractor),
            listUploadsContractRoute(database),
            prepareUploadContractRoute(directUpload),
            confirmUploadContractRoute(storage, database, thumbnailGenerator, metadataExtractor),
            fileProxyContractRoute(storage, database),
            thumbProxyContractRoute(storage, database),
            readUrlContractRoute(directUpload, database),
            rotationContractRoute(database),
            tagsContractRoute(database),
        )
    }

    return routes(
        "/api/content" bind apiContract,
        "/health" bind GET to { Response(OK).body("ok") },
        "/docs/api.json" bind GET to { specWithApiKeyAuth(apiContract) },
        "/docs" bind GET to { Response(FOUND).header("Location", "/docs/index.html") },
        "/docs/swagger-initializer.js" bind GET to {
            Response(OK).header("Content-Type", "application/javascript").body(swaggerInitializerJs)
        },
        "/docs" bind static(ResourceLoader.Classpath("META-INF/resources/webjars/swagger-ui/$SWAGGER_UI_VERSION")),
    )
}

private fun specWithApiKeyAuth(apiContract: HttpHandler): Response {
    val specResponse = apiContract(Request(GET, "/openapi.json"))
    val factory = JsonNodeFactory.instance
    val spec = ObjectMapper().readTree(specResponse.bodyString()) as? ObjectNode
        ?: return specResponse

    val apiKeyScheme = factory.objectNode().apply {
        put("type", "apiKey")
        put("in", "header")
        put("name", "X-Api-Key")
    }
    val components = (spec.get("components") as? ObjectNode ?: factory.objectNode()).apply {
        set<ObjectNode>("securitySchemes", factory.objectNode().apply {
            set<ObjectNode>("ApiKeyAuth", apiKeyScheme)
        })
    }
    spec.set<ObjectNode>("components", components)
    spec.set<ArrayNode>("security", factory.arrayNode().add(
        factory.objectNode().apply { set<ArrayNode>("ApiKeyAuth", factory.arrayNode()) }
    ))
    spec.set<ArrayNode>("servers", factory.arrayNode().add(
        factory.objectNode().apply { put("url", "/api/content") }
    ))

    // Remove per-operation "security": [] entries — an empty array means "no auth"
    // and overrides the global security block, so Swagger UI won't send the key.
    val paths = spec.get("paths") as? ObjectNode
    paths?.fields()?.forEach { (_, pathItem) ->
        (pathItem as? ObjectNode)?.fields()?.forEach { (_, operation) ->
            (operation as? ObjectNode)?.remove("security")
        }
    }

    return Response(OK).header("Content-Type", "application/json").body(spec.toString())
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

private fun listUploadsContractRoute(database: Database): ContractRoute =
    "/uploads" meta {
        summary = "List uploads"
        description = "Returns all uploaded files as a JSON array. Optional query params: `tag` (include only uploads that have this tag), `exclude_tag` (omit uploads that have this tag). Both can be combined."
    } bindContract GET to listUploadsHandler(database)

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

        val hash = sha256Hex(body)
        val existing = database.findByContentHash(hash)
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
                    capturedAt = metadata.capturedAt,
                    latitude = metadata.latitude,
                    longitude = metadata.longitude,
                    altitude = metadata.altitude,
                    deviceMake = metadata.deviceMake,
                    deviceModel = metadata.deviceModel,
                )
                database.recordUpload(record)
                Response(CREATED)
                    .header("Content-Type", "application/json")
                    .body(record.toJson())
            } catch (e: Exception) {
                Response(INTERNAL_SERVER_ERROR).body("Failed to store file: ${e.message}")
            }
        }
    }
}

private fun listUploadsHandler(database: Database): HttpHandler = { request ->
    try {
        val tag = request.query("tag")?.takeIf { it.isNotBlank() }
        val excludeTag = request.query("exclude_tag")?.takeIf { it.isNotBlank() }
        val uploads = database.listUploads(tag = tag, excludeTag = excludeTag)
        val json = buildString {
            append("[")
            uploads.forEachIndexed { i, u ->
                if (i > 0) append(",")
                append(u.toJson())
            }
            append("]")
        }
        Response(OK).header("Content-Type", "application/json").body(json)
    } catch (e: Exception) {
        Response(INTERNAL_SERVER_ERROR).body("Failed to list uploads: ${e.message}")
    }
}

private fun fileProxyContractRoute(storage: FileStore, database: Database): ContractRoute {
    val id = Path.uuid().of("id")
    return "/uploads" / id / "file" meta {
        summary = "Get file"
        description = "Streams the raw file bytes for the given upload ID with the correct Content-Type."
    } bindContract GET to { uploadId: UUID, _: String ->
        { _: Request ->
            val record = database.getUploadById(uploadId)
            if (record == null) {
                Response(NOT_FOUND)
            } else {
                try {
                    val bytes = storage.get(StorageKey(record.storageKey))
                    Response(OK)
                        .header("Content-Type", record.mimeType)
                        .body(ByteArrayInputStream(bytes), bytes.size.toLong())
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
        { _: Request ->
            val record = database.getUploadById(uploadId)
            if (record == null) {
                Response(NOT_FOUND)
            } else {
                val keyToFetch = record.thumbnailKey?.let { StorageKey(it) } ?: StorageKey(record.storageKey)
                val mimeType = if (record.thumbnailKey != null) "image/jpeg" else record.mimeType
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

private fun readUrlContractRoute(directUpload: DirectUploadSupport?, database: Database): ContractRoute {
    val id = Path.uuid().of("id")
    return "/uploads" / id / "url" meta {
        summary = "Get signed read URL"
        description = "Returns a 1-hour signed GCS URL for the given upload ID. Use directly as a video src for streaming."
    } bindContract GET to { uploadId: UUID, _: String ->
        { _: Request ->
            if (directUpload == null) {
                Response(NOT_IMPLEMENTED).body("Signed URLs not supported by the current storage backend")
            } else {
                val record = database.getUploadById(uploadId)
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
        val node = ObjectMapper().readTree(request.bodyString())
        val storageKey = node?.get("storageKey")?.asText()
        val mimeType = node?.get("mimeType")?.asText()
        val fileSize = node?.get("fileSize")?.asLong()
        val contentHash = node?.get("contentHash")?.asText()?.takeIf { it.isNotBlank() }
        val tags = node?.get("tags")?.takeIf { it.isArray }
            ?.map { it.asText() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()

        val tagValidation = validateTags(tags)
        if (storageKey.isNullOrBlank() || mimeType.isNullOrBlank() || fileSize == null) {
            Response(BAD_REQUEST).body("Missing storageKey, mimeType, or fileSize in request body")
        } else if (tagValidation is TagValidationResult.Invalid) {
            Response(BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body("""{"error":"invalid tag","tag":"${tagValidation.tag}","reason":"${tagValidation.reason}"}""")
        } else {
            val existing = if (contentHash != null) database.findByContentHash(contentHash) else null
            if (existing != null) {
                Response(CONFLICT)
                    .header("Content-Type", "application/json")
                    .body("""{"storageKey":"${existing.storageKey}"}""")
            } else {
                val bytes = fetchBytesIfNeeded(storageKey, mimeType, storage)
                val thumbKey = if (bytes != null) tryStoreThumbnail(bytes, mimeType, StorageKey(storageKey), storage, thumbnailGenerator) else null
                val metaBytes = fetchHeaderForMetadata(storageKey, mimeType, storage)
                val metadata = if (metaBytes != null) {
                    try { metadataExtractor(metaBytes, mimeType) } catch (_: Exception) { MediaMetadata() }
                } else MediaMetadata()
                val id = UUID.randomUUID()
                database.recordUpload(
                    UploadRecord(
                        id = id,
                        storageKey = storageKey,
                        mimeType = mimeType,
                        fileSize = fileSize,
                        contentHash = contentHash,
                        thumbnailKey = thumbKey?.value,
                        capturedAt = metadata.capturedAt,
                        latitude = metadata.latitude,
                        longitude = metadata.longitude,
                        altitude = metadata.altitude,
                        deviceMake = metadata.deviceMake,
                        deviceModel = metadata.deviceModel,
                    )
                )
                if (tags.isNotEmpty()) database.updateTags(id, tags)
                Response(CREATED)
            }
        }
    } catch (e: Exception) {
        Response(INTERNAL_SERVER_ERROR).body("Failed to confirm upload: ${e.message}")
    }
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
                    database.getUploadById(uploadId) == null ->
                        Response(NOT_FOUND)
                    else -> {
                        database.updateRotation(uploadId, body.rotation)
                        Response(OK)
                    }
                }
            } catch (e: Exception) {
                Response(INTERNAL_SERVER_ERROR).body("Failed to update rotation: ${e.message}")
            }
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
                        is TagValidationResult.Valid ->
                            if (!database.updateTags(uploadId, body.tags)) {
                                Response(NOT_FOUND)
                            } else {
                                val record = database.getUploadById(uploadId)!!
                                Response(OK)
                                    .header("Content-Type", "application/json")
                                    .body(record.toJson())
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
