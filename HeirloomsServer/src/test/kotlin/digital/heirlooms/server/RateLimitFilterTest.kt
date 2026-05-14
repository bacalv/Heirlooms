package digital.heirlooms.server

import digital.heirlooms.server.filters.IpRateLimiter
import digital.heirlooms.server.filters.rateLimitFilter
import digital.heirlooms.server.routes.buildApp
import io.mockk.mockk
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.TOO_MANY_REQUESTS
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Unit tests for the in-process sliding-window rate limiter (SEC-004 / F-05).
 */
class RateLimitFilterTest {

    // ---- IpRateLimiter unit tests -------------------------------------------

    @Test
    fun `rate limiter allows requests within the limit`() {
        val limiter = IpRateLimiter(maxRequests = 5, windowSeconds = 60)
        repeat(5) { i ->
            val allowed = limiter.isAllowed("192.168.1.1")
            assert(allowed) { "Request $i should be allowed within limit" }
        }
    }

    @Test
    fun `rate limiter rejects requests over the limit`() {
        val limiter = IpRateLimiter(maxRequests = 3, windowSeconds = 60)
        repeat(3) { limiter.isAllowed("10.0.0.1") }
        // 4th request should be denied
        val allowed = limiter.isAllowed("10.0.0.1")
        assert(!allowed) { "4th request should be rejected after limit of 3" }
    }

    @Test
    fun `rate limiter tracks different IPs independently`() {
        val limiter = IpRateLimiter(maxRequests = 2, windowSeconds = 60)
        limiter.isAllowed("1.1.1.1")
        limiter.isAllowed("1.1.1.1")
        // IP 1.1.1.1 at limit; 2.2.2.2 should still be allowed
        assert(!limiter.isAllowed("1.1.1.1")) { "1.1.1.1 should be over limit" }
        assert(limiter.isAllowed("2.2.2.2")) { "2.2.2.2 should be within limit" }
    }

    @Test
    fun `rate limiter clientIp extracts first X-Forwarded-For hop`() {
        val limiter = IpRateLimiter()
        val request = Request(POST, "/")
            .header("X-Forwarded-For", "203.0.113.5, 10.0.0.1, 172.16.0.1")
        assertEquals("203.0.113.5", limiter.clientIp(request))
    }

    @Test
    fun `rate limiter clientIp uses X-Real-IP when no X-Forwarded-For`() {
        val limiter = IpRateLimiter()
        val request = Request(POST, "/")
            .header("X-Real-IP", "198.51.100.7")
        assertEquals("198.51.100.7", limiter.clientIp(request))
    }

    @Test
    fun `rate limiter clientIp returns unknown when no IP headers present`() {
        val limiter = IpRateLimiter()
        val request = Request(POST, "/")
        assertEquals("unknown", limiter.clientIp(request))
    }

    // ---- rateLimitFilter http4k filter tests --------------------------------

    @Test
    fun `rateLimitFilter passes request when under limit`() {
        val limiter = IpRateLimiter(maxRequests = 10, windowSeconds = 60)
        val filter = rateLimitFilter(limiter, "/api/auth/login")
        val handler = filter { _: Request -> Response(OK) }

        val response = handler(Request(POST, "/api/auth/login")
            .header("X-Forwarded-For", "192.168.99.1"))

        assertEquals(OK, response.status)
    }

    @Test
    fun `rateLimitFilter returns 429 after exceeding limit`() {
        val limiter = IpRateLimiter(maxRequests = 2, windowSeconds = 60)
        val filter = rateLimitFilter(limiter, "/api/auth/login")
        val handler = filter { _: Request -> Response(OK) }
        val req = Request(POST, "/api/auth/login")
            .header("X-Forwarded-For", "10.10.10.10")

        // Exhaust the limit
        repeat(2) { handler(req) }

        // 3rd request should be rejected
        val response = handler(req)
        assertEquals(TOO_MANY_REQUESTS, response.status)
    }

    @Test
    fun `rateLimitFilter does not rate-limit paths not in the protected list`() {
        val limiter = IpRateLimiter(maxRequests = 1, windowSeconds = 60)
        val filter = rateLimitFilter(limiter, "/api/auth/login")
        val handler = filter { _: Request -> Response(OK) }
        val req = Request(POST, "/api/auth/register")
            .header("X-Forwarded-For", "10.10.10.11")

        // Even after many requests the unprotected path should pass through
        repeat(10) {
            val resp = handler(req)
            assertEquals(OK, resp.status, "Unprotected path should not be rate-limited")
        }
    }

    @Test
    fun `rateLimitFilter 429 response includes Retry-After header`() {
        val limiter = IpRateLimiter(maxRequests = 1, windowSeconds = 60)
        val filter = rateLimitFilter(limiter, "/login")
        val handler = filter { _: Request -> Response(OK) }
        val req = Request(POST, "/login").header("X-Forwarded-For", "10.0.0.99")

        handler(req) // consume the limit
        val response = handler(req) // over limit

        assertEquals(TOO_MANY_REQUESTS, response.status)
        assertEquals("60", response.header("Retry-After"))
    }
}
