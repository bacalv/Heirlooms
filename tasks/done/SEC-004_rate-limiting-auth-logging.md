---
id: SEC-004
title: Rate limiting on auth endpoints + failed-login audit logging
category: Security
priority: Medium
status: queued
depends_on: [SEC-001]
touches:
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/routes/auth/AuthRoutes.kt
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/filters/
assigned_to: SecurityManager
estimated: 0.5 day (agent)
---

## Background

SEC-001 threat model identified two related MEDIUM findings:

- **F-05**: No rate limiting on `/api/auth/challenge` or `/api/auth/login`. A credential-stuffing or brute-force attack against the auth verifier is unconstrained at the application layer.
- **F-06**: Failed login attempts (`InvalidCredentials`) are not logged. There is no audit trail for detecting or alerting on brute-force activity.

## Goal

1. Add SLF4J `warn` logging for every `InvalidCredentials` result on `/login` and `/setup-existing`, including the attempted username (not the verifier) and source IP (from `X-Forwarded-For` if present, else remote address).
2. Evaluate and implement a lightweight in-process rate limiter for `/challenge` and `/login` — e.g. a per-IP sliding-window counter using a `ConcurrentHashMap` and a background eviction thread. Reject with `429 Too Many Requests` after N attempts within a time window (suggested: 10 attempts per IP per minute).
3. Alternatively, document why Cloud Run / Google Cloud Armor is the preferred enforcement point and create a runbook entry for configuring it, if in-process rate limiting is deemed insufficient.

## Acceptance criteria

- Every failed login logs at WARN level with username and client IP.
- Either: `/login` returns 429 after sustained brute-force attempts in integration tests, or: a runbook entry exists for platform-level rate limiting that the CTO can configure.
- All existing tests continue to pass.
- No secrets or auth verifier bytes appear in log output.

## Completion notes

Completed 2026-05-15 by SecurityManager.

### What was done

**F-06 — Failed login audit logging:**
- Added SLF4J WARN log in `AuthRoutes.kt` `loginRoute` for `InvalidCredentials` result, logging username and resolved client IP.
- Added SLF4J WARN log in `setupExistingRoute` for `InvalidCredentials` and `NoDeviceKey` results (both result in 401).
- Client IP is resolved from `X-Forwarded-For` header (first hop) if present, otherwise `X-Real-IP`, otherwise `"unknown"`. No verifier bytes or secrets are logged.

**F-05 — Rate limiting:**
- Created `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/filters/RateLimitFilter.kt` with:
  - `IpRateLimiter`: per-IP sliding-window counter using `ConcurrentHashMap<String, ArrayDeque<Long>>`. Default limit: 10 requests/60 seconds.
  - Background eviction thread (daemon) runs every `windowSeconds` to remove stale entries.
  - `rateLimitFilter(limiter, vararg paths)`: http4k `Filter` that checks rate limit for matching path suffixes.
  - `AuthRateLimiter` singleton with 10 req/min limit for `/challenge` and `/login`.
- Applied the filter in `AppRoutes.kt` wrapping the `authContract` handler: `rateLimitFilter(AuthRateLimiter.challengeAndLogin, "/challenge", "/login").then(authContract)`.
- Clients over the limit receive `429 Too Many Requests` with `Retry-After: 60` header.

### Tests added
- `RateLimitFilterTest.kt` — unit tests for `IpRateLimiter` (within limit, over limit, per-IP isolation, IP resolution) and `rateLimitFilter` (pass-through, 429, unprotected paths, Retry-After header).
- `AuthHandlerTest.kt` — integration test `login returns 429 after exceeding rate limit` using an isolated app instance with a tight limiter (3 req/min).
