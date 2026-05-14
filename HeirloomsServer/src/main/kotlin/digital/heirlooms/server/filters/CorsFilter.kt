package digital.heirlooms.server.filters

import org.http4k.core.Filter
import org.http4k.core.Method.OPTIONS
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK

/**
 * CORS filter.
 *
 * When [allowedOrigins] is empty (the default, used in tests and local dev) the
 * filter falls back to `Access-Control-Allow-Origin: *` — this is intentional for
 * development convenience but MUST NOT be used in production.
 *
 * In production, pass the explicit set of allowed origins, e.g.:
 *   `corsFilter(setOf("https://heirlooms.digital", "https://test.heirlooms.digital"))`
 *
 * The filter reflects the request `Origin` header back only when it matches an
 * entry in [allowedOrigins].  An unrecognised origin gets no CORS headers, which
 * causes the browser to block the response.
 */
fun corsFilter(allowedOrigins: Set<String> = emptySet()): Filter = Filter { next ->
    { request ->
        val requestOrigin = request.header("Origin")
        val allowOriginValue: String = when {
            allowedOrigins.isEmpty() -> "*"
            requestOrigin != null && requestOrigin in allowedOrigins -> requestOrigin
            else -> ""   // unrecognised origin — omit CORS headers
        }

        if (request.method == OPTIONS) {
            val resp = Response(OK)
                .header("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS")
                .header("Access-Control-Allow-Headers", "Content-Type, X-Api-Key")
                .header("Access-Control-Max-Age", "86400")
            if (allowOriginValue.isNotEmpty()) {
                resp.header("Access-Control-Allow-Origin", allowOriginValue)
                    .let { if (allowedOrigins.isNotEmpty()) it.header("Vary", "Origin") else it }
            } else resp
        } else {
            val resp = next(request)
            if (allowOriginValue.isNotEmpty()) {
                resp.header("Access-Control-Allow-Origin", allowOriginValue)
                    .let { if (allowedOrigins.isNotEmpty()) it.header("Vary", "Origin") else it }
            } else resp
        }
    }
}
