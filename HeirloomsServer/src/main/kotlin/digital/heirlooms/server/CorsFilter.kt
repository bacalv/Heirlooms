package digital.heirlooms.server

import org.http4k.core.Filter
import org.http4k.core.Method.OPTIONS
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK

fun corsFilter(): Filter = Filter { next ->
    { request ->
        if (request.method == OPTIONS) {
            Response(OK)
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Methods", "GET, POST, PATCH, OPTIONS")
                .header("Access-Control-Allow-Headers", "Content-Type, X-Api-Key")
                .header("Access-Control-Max-Age", "86400")
        } else {
            next(request).header("Access-Control-Allow-Origin", "*")
        }
    }
}
