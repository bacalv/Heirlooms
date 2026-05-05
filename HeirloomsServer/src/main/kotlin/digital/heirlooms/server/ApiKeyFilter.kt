package digital.heirlooms.server

import org.http4k.core.Filter
import org.http4k.core.Response
import org.http4k.core.Status.Companion.UNAUTHORIZED

fun apiKeyFilter(apiKey: String): Filter = Filter { next ->
    { request ->
        if (request.uri.path == "/health") {
            next(request)
        } else if (request.header("X-Api-Key") == apiKey) {
            next(request)
        } else {
            Response(UNAUTHORIZED).body("Unauthorized")
        }
    }
}