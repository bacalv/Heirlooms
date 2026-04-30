package com.heirloom.server

import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.CREATED
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.http4k.core.Status.Companion.OK
import org.http4k.routing.bind
import org.http4k.routing.routes
import java.util.UUID

fun buildApp(storage: FileStore, database: Database): HttpHandler = routes(
    "/api/content/upload"  bind POST to uploadHandler(storage, database),
    "/api/content/uploads" bind GET  to listUploadsHandler(database),
    "/health"              bind GET  to { Response(OK).body("ok") },
)

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
                append("""{"id":"${u.id}","storageKey":"${u.storageKey}","mimeType":"${u.mimeType}","fileSize":${u.fileSize}}""")
            }
            append("]")
        }
        Response(OK).header("Content-Type", "application/json").body(json)
    } catch (e: Exception) {
        Response(INTERNAL_SERVER_ERROR).body("Failed to list uploads: ${e.message}")
    }
}
