# Heirlooms — Threat Model

**Version:** 1.0  
**Date:** 2026-05-14  
**Author:** Security Manager  
**Status:** Active — maintained at `docs/security/threat-model.md`

---

## 1. Security Model Summary

Heirlooms is an invite-only, end-to-end encrypted media vault. Its security model rests on four founding principles:

1. **Server never sees plaintext media.** All encryption and decryption occurs on client devices. The server stores and serves encrypted blobs only. The `EnvelopeFormat` validator in `HeirloomsServer` checks structural correctness (version, algorithm ID, lengths) but never decrypts.
2. **Forward secrecy at the device layer.** Each device holds a device key pair. The master key is wrapped separately per device (`p256-ecdh-hkdf-aes256gcm-v1`). Compromising one device key does not expose other devices' wrapped copies of the master key.
3. **No trust in transit.** Even if TLS were broken, all media remains AES-256-GCM encrypted under client-held keys. The server cannot reconstruct plaintext.
4. **Invite-only access.** The founding user controls who joins. There is no self-registration flow; every new account requires a valid, unexpired, single-use invite token.

### Authentication Architecture

- Users authenticate with a password-derived `auth_verifier` (SHA-256 of a KDF-derived key). The server stores only the verifier; it never sees the raw passphrase.
- Sessions are 32 random bytes from `SecureRandom`, identified by SHA-256 hash. Sessions expire after 90 days and are refreshed on each authenticated request.
- Fake salts (HMAC-SHA256 of username under `AUTH_SECRET`) prevent username enumeration via timing differences in the challenge flow.
- Session tokens are transmitted in `X-Api-Key` headers (Bearer-style); they are base64url-encoded and decoded before hashing.

### Key Hierarchy

```
User passphrase
  └─ Argon2id KDF → master key (backup, optional)
Device key pair (P-256)
  └─ ECDH + HKDF → wraps master key per device
Master key
  └─ AES-256-GCM → wraps per-plot plot keys
Plot key
  └─ AES-256-GCM → wraps per-upload DEKs
DEK
  └─ AES-256-GCM → encrypts file bytes, thumbnail, encrypted metadata
```

---

## 2. Findings

| # | Finding | Area | Severity | Status | Mitigation |
|---|---------|------|----------|--------|------------|
| F-01 | CORS policy used `Access-Control-Allow-Origin: *` in production — any origin could make credentialed requests | Transport | HIGH | Mitigated (af2e856) | `CorsFilter.kt` now reads `CORS_ALLOWED_ORIGINS` env var; reflects origin only when it matches the allowlist; falls back to `*` only when the list is empty (dev mode). Production must set `CORS_ALLOWED_ORIGINS=https://heirlooms.digital`. |
| F-02 | `AUTH_SECRET` defaulted to `ByteArray(32)` (all zeros) when unset — fake-salt HMAC key predictable | Secrets Management | HIGH | Mitigated (af2e856) | `Main.kt` logs `SECURITY: AUTH_SECRET env var is not set` at ERROR level on startup; falls back to zeros only as a fail-open to preserve uptime. **Manual verification required**: CTO must confirm `AUTH_SECRET` is set in Cloud Run (see §3). |
| F-03 | Auth error responses previously leaked exception messages | Authentication | HIGH | Mitigated (af2e856) | All auth error paths in `AuthRoutes.kt` now return opaque `"Internal server error"` and log the full exception server-side via SLF4J. |
| F-04 | `SessionAuthFilter.authUserId()` silently fell back to `FOUNDING_USER_ID` when header was absent | Authentication | HIGH | Mitigated (af2e856) | `SessionAuthFilter.kt` now logs `SECURITY: X-Auth-User-Id header missing or invalid` at WARN level. The fallback is a dev convenience only; the real gate is `sessionAuthFilter` which returns 401 before route handlers are reached. |
| F-05 | No rate limiting on `/challenge` or `/login` — brute-force credential stuffing is unconstrained | Authentication | MEDIUM | Open — task SEC-004 created | Cloud Run's default request throttling provides no per-IP login rate limiting. A targeted attack could enumerate auth verifiers or brute-force weak passphrases. |
| F-06 | Failed login attempts are not logged — no audit trail for brute-force detection | Authentication / Logging | MEDIUM | Open — task SEC-004 created | `AuthRoutes.loginRoute` returns 401 on `InvalidCredentials` without emitting any log entry. Same for `setup-existing`. Defenders cannot detect or alert on credential stuffing attacks. |
| F-07 | Session tokens not invalidated on passphrase change | Authentication | MEDIUM | Open — task SEC-005 created | `AuthService.setupExisting()` sets new auth credentials but does not expire existing sessions for the same user. An attacker with a stolen session token retains access indefinitely until the 90-day session TTL expires. |
| F-08 | No server-side MIME type validation on uploads — content type is trusted from client | Input Validation | MEDIUM | Open — task SEC-005 created | `FileStore` and upload routes accept any MIME type the client declares. A malicious client could upload a script or executable and the server would store and later serve it. Since all content is encrypted blobs, active exploitation is low, but defence-in-depth recommends validation. |
| F-09 | `CriteriaEvaluator` date fields (`taken_after`, `taken_before`, etc.) accept arbitrary strings — no format validation | Input Validation | MEDIUM | Open — task SEC-006 created | The `date` field is passed as a raw string to `?::date` in a prepared statement. PostgreSQL will raise an error on malformed dates (no SQL injection), but the server surfaces a 500 rather than a 400, and no input length limit is applied. |
| F-10 | Pagination cursor is not authenticated — a crafted cursor could probe internal timestamps/UUIDs | Input Validation | LOW | Accepted | The cursor is base64url-encoded plaintext (`SORT:timestamp:uuid`). It is used only in parameterised SQL (`?` placeholders) so there is no injection path. Malformed cursors are silently ignored (return `null`). The risk is that a curious client can craft arbitrary cursor values; all values are bounded by the authenticated user's own data (`user_id` filter always applied), so cross-user probing is not possible. |
| F-11 | Pairing one-time code is 8 decimal digits (10M possibilities) with 5-minute window — brute-force theoretical risk | Authentication | LOW | Accepted | `generateNumericCode()` produces 8-digit codes. At 5-minute expiry, an attacker can make at most a few hundred requests before the code expires. The pairing flow also requires a live authenticated session on the initiating device. No evidence of high-frequency pairing abuse in practice. |
| F-12 | `previewDurationSeconds` (signed URL TTL) is configurable but no minimum is enforced — could be set to a very large value | Cryptography / Config | LOW | Accepted | The default is 15 seconds. If `PREVIEW_DURATION_SECONDS` is set to a large value by an operator, pre-signed download URLs would be long-lived. This is an operational concern; no code fix warranted now. Document in runbook. |
| F-13 | `argon2_params` stored as `{}` (empty JSON) in recovery passphrase records | Cryptography | LOW | Accepted | The `RecoveryPassphraseRecord.argon2Params` field is hardcoded to `"{}"` in `AuthService`. The actual Argon2id parameters are baked into client code. This means parameter rotation requires a client code change rather than a database lookup. Acceptable for M7; track as technical debt. |
| F-14 | Static API key bypass (`API_KEY` env var) grants `FOUNDING_USER_ID` identity with no expiry | Authentication | INFO | Accepted | The static key is explicitly for integration tests and local dev. `Main.kt` logs `"Static API key auth enabled (development/test mode)"`. This should never be set in production. CTO confirms only `AUTH_SECRET` (not `API_KEY`) is set in production Cloud Run. |
| F-15 | `HTTPS` is not enforced at the application layer — no HTTP→HTTPS redirect in the server | Transport | INFO | Accepted | Cloud Run terminates TLS and only accepts HTTPS on the public endpoint. The application does not need its own redirect. HTTP access to the Cloud Run service URL is blocked by the platform. |
| F-16 | Username `display_name` field is not sanitised for XSS before being embedded in JSON | Input Validation | INFO | Accepted | Heirlooms is an invite-only vault; all users are trusted friends. The web client is responsible for escaping displayed values. Server-side sanitisation is acceptable to defer until a broader input validation pass. |

---

## 3. `AUTH_SECRET` Production Verification

The server code (confirmed in `Main.kt` and `AppConfig.kt`) reads `AUTH_SECRET` from the environment at startup:

- If set and valid base64: decoded and used as the HMAC key for fake salts.
- If set but invalid base64: ERROR logged, falls back to all-zeros.
- If unset: ERROR logged, falls back to all-zeros (predictable fake salts).

**This cannot be verified from code alone.** The CTO must verify that `AUTH_SECRET` is set to a securely-generated 32-byte base64url value in the Cloud Run service configuration before production traffic is served.

Verification steps (manual, CTO only):
1. `gcloud run services describe heirlooms --region=<region> --format='yaml(spec.template.spec.containers[0].env)'`
2. Confirm `AUTH_SECRET` appears in the env list with a non-empty value.
3. Confirm `CORS_ALLOWED_ORIGINS=https://heirlooms.digital` is set.
4. Confirm `API_KEY` is **not** set (or is empty) in production.

---

## 4. CORS Production Posture

`CorsFilter.kt` (committed in af2e856) reflects the request `Origin` header only when it matches the `CORS_ALLOWED_ORIGINS` allowlist. An origin not on the list receives no CORS headers, causing the browser to block the response.

Production setting required:
```
CORS_ALLOWED_ORIGINS=https://heirlooms.digital
```

Staging may additionally include `https://test.heirlooms.digital`.

The fallback to `Access-Control-Allow-Origin: *` is only active when `CORS_ALLOWED_ORIGINS` is empty, which is the expected state in local dev and unit tests. A startup warning is logged in that case.

---

## 5. SQL Injection Surface Assessment

All database access is via JDBC `PreparedStatement` with `?` placeholders. Spot checks:

- **`CriteriaEvaluator`**: SQL fragments are composed from a whitelist of known node types. User data (tags, dates, UUIDs) flows through `setString`/`setObject` setters — no string interpolation. Unknown node types throw `CriteriaValidationException`, not a DB error.
- **`UploadRepository` cursor**: Cursor values are base64-decoded and parsed into typed fields (`UploadSort.valueOf`, `toLong`, `UUID.fromString`). All values are bound via parameterised setters. A malformed cursor returns `null`; no SQL is constructed from raw cursor bytes.
- **`plot_ref` in CriteriaEvaluator**: Plot ID is validated as UUID before use in a parameterised query, and is additionally constrained to `owner_user_id = ?` (the authenticated user's ID) preventing cross-user plot_ref traversal.

**Assessment: No SQL injection paths identified.**

---

## 6. Envelope Oracle Assessment

`EnvelopeFormat.validateSymmetric` and `validateAsymmetric` (committed in the M7 E2EE milestone) perform structural validation only:

- Version byte must be `0x01`.
- Algorithm ID must be in the known allowlist (`AlgorithmIds.SYMMETRIC` or `ASYMMETRIC`).
- Length fields are bounds-checked; overshooting the blob size throws immediately.
- No decryption occurs server-side; GCM authentication tags are stored opaquely.

Thrown exceptions are `EnvelopeFormatException` — a distinct type with a descriptive message. These are caught and converted to opaque HTTP 400 responses at the route level. No oracle (distinguishing valid from invalid ciphertexts) exists because the server never attempts decryption.

**Assessment: No envelope oracle identified.**

---

## 7. Access Control Assessment (A01)

All authenticated routes set `X-Auth-User-Id` from the verified session via `sessionAuthFilter`. Route handlers call `request.authUserId()` to obtain the caller's UUID and pass it to repository methods. Repository queries always include `AND user_id = ?` or `AND owner_user_id = ?` clauses bound to the authenticated user's ID.

Spot checks:
- `findUploadByIdForUser(id, userId)` — upload access scoped to owner.
- `listUploadsPaginated(..., userId = ...)` — list scoped to owner.
- `plot_ref` in `CriteriaEvaluator` — cross-user plot references are rejected at the DB query level (`AND owner_user_id = ?`).
- Pairing complete: `if (link.userId != callerUserId) return PairingCompleteResult.NotFound` — caller must own the pairing link.

**Assessment: No cross-user data access (A01) paths identified. User isolation is enforced at the repository layer.**

---

## 8. Accepted Risks

The following LOW and INFO findings are consciously not acted on at this time:

| Finding | Rationale |
|---------|-----------|
| F-10 — unauthenticated cursor | No injection path; scoped to caller's data |
| F-11 — 8-digit pairing code | Short expiry + authenticated initiating session limits exposure |
| F-12 — configurable preview TTL | Operational concern; default is 15 s |
| F-13 — argon2_params empty JSON | M7 technical debt; parameters are baked into clients |
| F-14 — static API key | Dev/test only; never deployed to production |
| F-15 — no HTTP→HTTPS redirect in app | Cloud Run enforces HTTPS at the platform level |
| F-16 — display_name not sanitised | Invite-only vault; web client responsible for escaping |

---

## 9. Open Tasks Created by this Audit

| Task | Findings addressed | Priority |
|------|--------------------|----------|
| [SEC-004](../../tasks/queue/SEC-004_rate-limiting-auth-logging.md) | F-05 (no rate limiting), F-06 (no failed-login logging) | MEDIUM |
| [SEC-005](../../tasks/queue/SEC-005_session-invalidation-mime-validation.md) | F-07 (session invalidation on passphrase change), F-08 (MIME type validation) | MEDIUM |
| [SEC-006](../../tasks/queue/SEC-006_criteria-date-input-validation.md) | F-09 (criteria date field format validation) | MEDIUM |
