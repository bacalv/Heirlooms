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
