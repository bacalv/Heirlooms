package digital.heirlooms.server.filters

import org.http4k.core.Filter
import org.http4k.core.Response
import org.http4k.core.Status.Companion.UNAUTHORIZED

fun apiKeyFilter(apiKey: String): Filter = Filter { next ->
    { request ->
        val path = request.uri.path
        when {
            path == "/health" -> next(request)
            path.startsWith("/docs") -> next(request)
            path.startsWith("/api/auth/") -> next(request)
            request.header("X-Api-Key") == apiKey -> next(request)
            else -> Response(UNAUTHORIZED).body("Unauthorized")
        }
    }
}
