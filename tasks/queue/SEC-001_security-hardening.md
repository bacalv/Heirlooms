---
id: SEC-001
title: Security hardening + threat model
category: Security
priority: High
status: queued
depends_on: []
touches:
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/filters/
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/routes/auth/
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/service/auth/
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/crypto/
estimated: 1 day (agent, research-heavy)
---

## Goal

Perform a systematic security review of the server and produce:
1. A threat model document listing identified risks, severity, and mitigations
2. Concrete code changes for any high/critical findings
3. A UML sequence diagram for each auth flow (generated from test output — see DOC-001)

## Areas to review

### Authentication
- Session token entropy and expiry (current: 32 random bytes, SHA-256 stored)
- Fake-salt anti-enumeration in `AuthService.fakeSalt()` — is the HMAC key rotated? Is it constant-time?
- Brute-force protection: is there rate limiting on `/challenge` and `/login`?
- Session invalidation on password change / logout
- `FOUNDING_USER_ID` bypass scope — can it reach any endpoint it shouldn't?

### Transport
- HTTPS enforced everywhere? HTTP → HTTPS redirect?
- CORS policy — `Access-Control-Allow-Origin: *` is current; should be locked to known origins in production

### Input validation
- Are all route parameters validated before hitting the DB?
- JSON injection risks in criteria fields (stored as raw JSON, evaluated by CriteriaEvaluator)
- File upload: are MIME types validated server-side or just trusted from the client?

### Cryptography
- Envelope format validation — does the server reject malformed envelopes cleanly (no oracle)?
- DEK wrapping — is the wrapped DEK validated before storage?
- Preview token signing — `previewDurationSeconds` — is the signing key strong?

### Secrets management
- Are secrets (DB password, GCS credentials) only accessible from Cloud Run, not hardcoded anywhere?
- Is `AUTH_SECRET` set in production? (Currently falls back to `ByteArray(32)` — all zeros)

### OWASP Top 10
- A01 Broken Access Control: can User A access User B's uploads?
- A03 Injection: SQL injection in dynamic query builders (CriteriaEvaluator, pagination cursor)?
- A07 Authentication failures: covered above
- A09 Security logging: are auth failures logged with enough context?

## Deliverables

1. `docs/security/threat-model.md` — table of findings (risk, severity, mitigation, status)
2. Code changes for any HIGH or CRITICAL findings
3. New tasks in `queue/` for MEDIUM findings that need separate work
4. Input to DOC-001 (sequence diagrams) for auth flows

## Acceptance criteria

- No HIGH or CRITICAL unmitigated findings remain
- CORS locked to `https://heirlooms.digital` in production
- `AUTH_SECRET` confirmed set in production (check Cloud Run env vars)
- All tests still pass after any code changes
