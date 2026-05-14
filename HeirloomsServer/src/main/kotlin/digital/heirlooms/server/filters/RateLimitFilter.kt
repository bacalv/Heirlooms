package digital.heirlooms.server.filters

import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.TOO_MANY_REQUESTS
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Sliding-window in-process rate limiter for auth endpoints.
 *
 * Tracks request counts per client IP within a rolling [windowSeconds]-second window.
 * Requests exceeding [maxRequests] in the window receive 429 Too Many Requests.
 *
 * Client IP is resolved from X-Forwarded-For (first hop) when present, falling back
 * to the HTTP remote address header set by http4k or a default sentinel.
 *
 * A background thread evicts stale entries every [windowSeconds] seconds to cap memory use.
 */
class IpRateLimiter(
    private val maxRequests: Int = 10,
    private val windowSeconds: Long = 60,
) {
    // Maps IP → list of timestamps (epoch seconds) for requests within the current window.
    private val buckets = ConcurrentHashMap<String, ArrayDeque<Long>>()

    private val evictionScheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "rate-limiter-eviction").also { it.isDaemon = true }
    }.also { scheduler ->
        scheduler.scheduleAtFixedRate(::evictStale, windowSeconds, windowSeconds, TimeUnit.SECONDS)
    }

    /** Returns true if the request from [ip] is within the allowed rate. */
    fun isAllowed(ip: String): Boolean {
        val nowEpoch = Instant.now().epochSecond
        val cutoff = nowEpoch - windowSeconds

        val timestamps = buckets.compute(ip) { _, existing ->
            val deque = existing ?: ArrayDeque()
            // Drop timestamps older than the sliding window
            while (deque.isNotEmpty() && deque.first() <= cutoff) deque.removeFirst()
            deque.addLast(nowEpoch)
            deque
        }!!

        return timestamps.size <= maxRequests
    }

    /** Resolves the client IP from a http4k [Request]. */
    fun clientIp(request: Request): String {
        val forwarded = request.header("X-Forwarded-For")
        if (!forwarded.isNullOrBlank()) {
            return forwarded.split(",").first().trim()
        }
        // http4k sets X-Real-IP via some servers; fall back to a sentinel for local/test.
        return request.header("X-Real-IP")?.trim() ?: "unknown"
    }

    private fun evictStale() {
        // Use compute on each key so eviction is consistent with the isAllowed lock granularity.
        val keysToCheck = buckets.keys.toList()
        val cutoff = Instant.now().epochSecond - windowSeconds
        for (key in keysToCheck) {
            buckets.computeIfPresent(key) { _, deque ->
                while (deque.isNotEmpty() && deque.first() <= cutoff) deque.removeFirst()
                if (deque.isEmpty()) null else deque  // returning null removes the entry
            }
        }
    }

    fun shutdown() {
        evictionScheduler.shutdownNow()
    }
}

/** Singleton rate limiter instances for /challenge and /login endpoints. */
object AuthRateLimiter {
    val challengeAndLogin = IpRateLimiter(maxRequests = 10, windowSeconds = 60)
}

/**
 * http4k [Filter] that applies [limiter] to requests whose path starts with [pathPrefix].
 * Returns 429 when the IP is over the limit.
 */
fun rateLimitFilter(limiter: IpRateLimiter, vararg paths: String): Filter = Filter { next: HttpHandler ->
    { request: Request ->
        val path = request.uri.path
        if (paths.any { path.endsWith(it) || path.contains(it) }) {
            val ip = limiter.clientIp(request)
            if (!limiter.isAllowed(ip)) {
                Response(TOO_MANY_REQUESTS)
                    .header("Content-Type", "application/json")
                    .header("Retry-After", "60")
                    .body("""{"error":"Too many requests. Please try again later."}""")
            } else {
                next(request)
            }
        } else {
            next(request)
        }
    }
}
