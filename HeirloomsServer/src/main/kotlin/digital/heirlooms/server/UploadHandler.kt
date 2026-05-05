package digital.heirlooms.server

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import org.http4k.contract.ContractRoute
import org.http4k.contract.contract
import org.http4k.contract.meta
import org.http4k.contract.openapi.ApiInfo
import org.http4k.contract.openapi.v3.OpenApi3
import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.CREATED
import org.http4k.core.Status.Companion.FOUND
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.OK
import org.http4k.format.Jackson
import org.http4k.lens.binary
import org.http4k.routing.ResourceLoader
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes
import org.http4k.routing.static
import java.io.ByteArrayInputStream
import java.util.UUID

private const val SWAGGER_UI_VERSION = "5.11.8"

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

fun buildApp(storage: FileStore, database: Database): HttpHandler {
    val apiContract = contract {
        renderer = OpenApi3(ApiInfo("Heirlooms API", "v1"), Jackson)
        descriptionPath = "/openapi.json"
        routes += listOf(
            uploadContractRoute(storage, database),
            listUploadsContractRoute(database),
        )
    }

    return routes(
        "/api/content/uploads/{id}/file" bind GET to fileProxyHandler(storage, database),
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

private fun uploadContractRoute(storage: FileStore, database: Database): ContractRoute =
    "/upload" meta {
        summary = "Upload a file"
        description = "Upload an image or video. Content-Type header should reflect the file's MIME type (e.g. image/jpeg, video/mp4)."
        receiving(Body.binary(ContentType("application/octet-stream")).toLens())
    } bindContract POST to uploadHandler(storage, database)

private fun listUploadsContractRoute(database: Database): ContractRoute =
    "/uploads" meta {
        summary = "List uploads"
        description = "Returns all uploaded files as a JSON array with id, storageKey, mimeType, and fileSize fields."
    } bindContract GET to listUploadsHandler(database)

private fun uploadHandler(storage: FileStore, database: Database): HttpHandler = { request: Request ->
    val body = request.body.payload.array()

    if (body.isEmpty()) {
        Response(BAD_REQUEST).body("Request body is empty")
    } else {
        val mimeType = request.header("Content-Type")
            ?.substringBefore(";")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "application/octet-stream"

        try {
            val key = storage.save(body, mimeType)
            database.recordUpload(
                UploadRecord(
                    id = UUID.randomUUID(),
                    storageKey = key.value,
                    mimeType = mimeType,
                    fileSize = body.size.toLong(),
                )
            )
            Response(CREATED).body(key.value)
        } catch (e: Exception) {
            Response(INTERNAL_SERVER_ERROR).body("Failed to store file: ${e.message}")
        }
    }
}

private fun listUploadsHandler(database: Database): HttpHandler = {
    try {
        val uploads = database.listUploads()
        val json = buildString {
            append("[")
            uploads.forEachIndexed { i, u ->
                if (i > 0) append(",")
                append("""{"id":"${u.id}","storageKey":"${u.storageKey}","mimeType":"${u.mimeType}","fileSize":${u.fileSize},"uploadedAt":"${u.uploadedAt}"}""")
            }
            append("]")
        }
        Response(OK).header("Content-Type", "application/json").body(json)
    } catch (e: Exception) {
        Response(INTERNAL_SERVER_ERROR).body("Failed to list uploads: ${e.message}")
    }
}

private fun fileProxyHandler(storage: FileStore, database: Database): HttpHandler = { request: Request ->
    val idStr = request.path("id")
    val id = try { idStr?.let { UUID.fromString(it) } } catch (_: IllegalArgumentException) { null }
    if (id == null) {
        Response(NOT_FOUND)
    } else {
        val record = database.getUploadById(id)
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