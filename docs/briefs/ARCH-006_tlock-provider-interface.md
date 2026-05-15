# ARCH-006 — TimeLock Provider Interface

*Authored: 2026-05-15. Status: approved. Depends on ARCH-003 (read first).*

---

## What this document is

ARCH-003 defines `tlock-bls12381-v1` as the time-lock algorithm for M11 capsules
and the blinding scheme that prevents the server from recovering the capsule `DEK`
even after the drand round publishes. The real drand/BLS12-381 integration is the
highest-risk item in M11 — it requires either a JNI bridge to the Go drand SDK or a
pure-Kotlin BLS12-381 port.

This brief defines the `TimeLockProvider` abstraction that decouples all other M11
sealing/unsealing work from the real drand integration. A `StubTimeLockProvider` lets
the full tlock path work end-to-end on staging (including the blinding scheme) without
real BLS12-381 cryptography. The real drand provider is M12 scope.

Prerequisite reading:

- `docs/briefs/ARCH-003_m11-capsule-crypto-brief.md` — blinding scheme, sealing API,
  iOS compatibility guarantee
- `docs/envelope_format.md` — binary envelope format

---

## 1. `TimeLockProvider` interface (Kotlin, server-side)

The server interacts with tlock exclusively through this interface. No route handler,
service, or repository touches BLS12-381 directly.

```kotlin
package digital.heirlooms.crypto

import java.time.Instant

/**
 * Abstraction over the tlock time-lock scheme.
 *
 * All implementations must satisfy:
 * - seal() and decrypt() are inverses: decrypt(seal(key, t)) == key when now() >= t.
 * - validate() is a structural check only; it must not perform decryption.
 * - decrypt() returns null (not an exception) when the gate is not yet open.
 *
 * BLINDING SCHEME NOTE (ARCH-003 §9):
 * The plaintext passed to seal() is DEK_client (the 32-byte client-side blinding
 * mask), NOT the capsule DEK. The server never sees or stores the full DEK.
 */
interface TimeLockProvider {

    /**
     * Produce a tlock ciphertext for the given plaintext key, gated to unlockAt.
     *
     * @param plaintextKey  32-byte value to encrypt (DEK_client under the blinding scheme).
     * @param unlockAt      The earliest instant at which decrypt() may succeed.
     * @return              A [TimeLockCiphertext] carrying the chain ID, round, and opaque blob.
     */
    fun seal(plaintextKey: ByteArray, unlockAt: Instant): TimeLockCiphertext

    /**
     * Attempt to decrypt a previously sealed ciphertext.
     *
     * Returns the plaintext key (DEK_client) if the gate is open (round has published
     * and now() >= unlockAt for stub), or null if the gate is not yet open.
     *
     * Never throws for a time-gate not-yet-open condition; only throws for structural
     * corruption or an unrecognised chain ID.
     */
    fun decrypt(ciphertext: TimeLockCiphertext): ByteArray?

    /**
     * Validate the structural integrity of a tlock ciphertext blob.
     * Called at sealing time — does not attempt decryption.
     *
     * @return true if the blob is structurally valid; false otherwise.
     */
    fun validate(ciphertext: TimeLockCiphertext): Boolean
}

/**
 * A tlock ciphertext and its associated chain metadata.
 *
 * @param chainId  drand chain hash (hex string). Stub uses STUB_CHAIN_ID.
 * @param round    drand round number. Stub derives from unlockAt.
 * @param blob     Opaque ciphertext bytes. Stored in capsules.tlock_wrapped_key.
 */
data class TimeLockCiphertext(
    val chainId: String,
    val round: Long,
    val blob: ByteArray
) {
    companion object {
        /** Fixed chain ID used by StubTimeLockProvider. Never matches any real drand chain. */
        const val STUB_CHAIN_ID = "stub-chain-0000000000000000000000000000000000000000000000000000000000000000"
    }

    override fun equals(other: Any?): Boolean =
        other is TimeLockCiphertext && chainId == other.chainId &&
            round == other.round && blob.contentEquals(other.blob)

    override fun hashCode(): Int = 31 * (31 * chainId.hashCode() + round.hashCode()) +
        blob.contentHashCode()
}
```

---

## 2. `StubTimeLockProvider` implementation spec

The stub makes the full tlock path (sealing, gate checking, unsealing, the blinding
scheme, and the `/tlock-key` endpoint) work end-to-end on staging without any
BLS12-381 library.

### 2.1 Construction

```kotlin
class StubTimeLockProvider(
    private val stubSecret: ByteArray,   // 32 bytes from TLOCK_STUB_SECRET env var
    private val clock: Clock = Clock.systemUTC()
) : TimeLockProvider
```

`stubSecret` is derived from the `TLOCK_STUB_SECRET` environment variable (see §4).
The provider MUST NOT be instantiated if the variable is absent.

### 2.2 `seal()` — blob construction

The stub blob is an HMAC-SHA-256 of a deterministic input:

```
blob = HMAC-SHA256(
    key  = stubSecret,
    data = unlockAt_epoch_seconds (big-endian int64) || plaintextKey (32 bytes)
)
```

This produces a 32-byte blob that `decrypt()` can verify and reverse.

Round derivation uses stub genesis/period constants:

```
STUB_GENESIS_UNIX  = 1_700_000_000L   // arbitrary fixed epoch (2023-11-14)
STUB_PERIOD_SECS   = 3L               // 3-second rounds (fast for testing)
round = ceil((unlockAt.epochSecond - STUB_GENESIS_UNIX) / STUB_PERIOD_SECS)
```

If `unlockAt` is before `STUB_GENESIS_UNIX`, round is 1.

Chain ID is always `TimeLockCiphertext.STUB_CHAIN_ID`.

### 2.3 `decrypt()` — gate check and plaintext recovery

```
if clock.instant() < unlockAt:   return null   // gate not yet open
verify HMAC-SHA256(stubSecret, unlockAt_epoch_seconds || expected_plaintext) == blob
  → if mismatch: throw TimeLockDecryptionException("stub blob MAC verification failed")
reconstruct unlockAt from round:
  unlockAt_epoch_seconds = STUB_GENESIS_UNIX + round * STUB_PERIOD_SECS
extract plaintext:
  try each 32-byte candidate P until HMAC-SHA256(stubSecret, unlockAt_epoch || P) == blob
```

In practice the stub stores `plaintextKey` inside the blob directly (alongside a MAC
for integrity) so that `decrypt()` does not need to brute-force:

**Revised blob format (32 bytes is too small to hold key + MAC):**

Use a 64-byte blob:

```
blob[0..31]  = plaintextKey (the 32-byte DEK_client)
blob[32..63] = HMAC-SHA256(stubSecret, unlockAt_epoch_seconds || plaintextKey)
```

`decrypt()` steps:
1. Extract `plaintextKey = blob[0..31]`.
2. Extract `mac = blob[32..63]`.
3. Recompute `expected_mac = HMAC-SHA256(stubSecret, unlockAt_epoch_seconds || plaintextKey)`.
4. Compare `mac == expected_mac` (constant-time). If mismatch → throw.
5. Return `plaintextKey`.

`validate()` steps:
1. Check `blob.size == 64`.
2. Check `chainId == STUB_CHAIN_ID`.
3. Check `round >= 1`.
4. Return true if all pass; false otherwise. Do not attempt MAC verification.

### 2.4 Error types

```kotlin
class TimeLockDecryptionException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

class TimeLockValidationException(message: String) : RuntimeException(message)
```

These are distinct from `EnvelopeFormatException` and must not be conflated.

---

## 3. Sidecar microservice (recommended for real drand — M12)

**Option A (in-process):** The provider is a Kotlin class. The real implementation
would need JNI or a pure-Kotlin BLS12-381 port — significant, high-risk work.

**Option B (sidecar — recommended):** The server calls a small HTTP sidecar process.
The stub is the current in-process implementation. The real drand provider in M12 is
a Go process (natural fit for the Go drand SDK), called via HTTP — no JNI required.

This brief specifies the sidecar REST API contract so M12 can implement the Go sidecar
without changing the server code.

### 3.1 Sidecar REST API contract

Base URL: `TLOCK_SIDECAR_URL` (e.g. `http://localhost:8090`). All requests are
HTTP/1.1 JSON. Authentication is by network isolation only (sidecar is not
externally accessible).

**`POST /seal`**

Request:
```json
{
  "plaintext_key": "<base64url: 32 bytes>",
  "unlock_at": "2026-07-01T00:00:00Z"
}
```

Response (200):
```json
{
  "chain_id": "<hex string>",
  "round": 1234567,
  "blob": "<base64url: opaque ciphertext bytes>"
}
```

Error (422):
```json
{ "error": "unlock_at_in_past", "detail": "..." }
```

**`POST /decrypt`**

Request:
```json
{
  "chain_id": "<hex string>",
  "round": 1234567,
  "blob": "<base64url>"
}
```

Response — gate open (200):
```json
{
  "plaintext_key": "<base64url: 32 bytes>",
  "gate_open": true
}
```

Response — gate closed (200):
```json
{
  "plaintext_key": null,
  "gate_open": false
}
```

Error (422 — structural):
```json
{ "error": "invalid_blob", "detail": "..." }
```

**`POST /validate`**

Request: same shape as `/decrypt`.

Response (200):
```json
{ "valid": true }
```
or
```json
{ "valid": false, "reason": "..." }
```

### 3.2 `SidecarTimeLockProvider` (M12 implementation sketch)

```kotlin
class SidecarTimeLockProvider(
    private val sidecarUrl: String,
    private val httpClient: HttpClient
) : TimeLockProvider {
    // Delegates all calls to the sidecar REST API.
    // seal()    → POST /seal
    // decrypt() → POST /decrypt
    // validate() → POST /validate
}
```

The server code path is identical regardless of which provider is active. The switch
is purely configuration (see §4).

---

## 4. Configuration and environment flags

| Variable | Values | Default | Notes |
|---|---|---|---|
| `TLOCK_PROVIDER` | `stub`, `sidecar`, `disabled` | `disabled` | Controls which provider is instantiated at startup. |
| `TLOCK_STUB_SECRET` | base64url-encoded 32 bytes | — | Required when `TLOCK_PROVIDER=stub`. Server fails to start if absent. |
| `TLOCK_SIDECAR_URL` | HTTP URL | — | Required when `TLOCK_PROVIDER=sidecar`. |

**Enforcement:**

- If `TLOCK_PROVIDER=disabled`, the server rejects any sealing request that contains
  non-null `tlock` fields with HTTP 422:
  ```json
  { "error": "tlock_not_enabled", "detail": "tlock provider is disabled on this server" }
  ```
- If `TLOCK_PROVIDER=stub` and `TLOCK_STUB_SECRET` is absent: server fails to start
  with a clear log message (`FATAL: TLOCK_PROVIDER=stub but TLOCK_STUB_SECRET is not set`).
- If `TLOCK_PROVIDER=sidecar` and `TLOCK_SIDECAR_URL` is absent: same fail-fast behaviour.

**Environment mapping:**

| Environment | Setting |
|---|---|
| Local dev | `TLOCK_PROVIDER=disabled` (default; tlock UI not shown) |
| Staging | `TLOCK_PROVIDER=stub`; `TLOCK_STUB_SECRET` in Secret Manager |
| Production | `TLOCK_PROVIDER=disabled` until M12 sidecar ships |
| M12 production | `TLOCK_PROVIDER=sidecar`; `TLOCK_SIDECAR_URL` points to Go sidecar |

---

## 5. Impact on ARCH-003 §4 sealing validation

ARCH-003 §4 states the server validates `tlock_wrapped_key` as "non-null and non-empty
(content is opaque)". With the `TimeLockProvider` abstraction, the server additionally
calls `provider.validate()` at sealing time.

**Updated sealing validation sequence for tlock capsules:**

```
1. Validate wrapped_capsule_key: syntactically valid capsule-ecdh-aes256gcm-v1 envelope.
2. For each recipient: validate wrapped_blinding_mask present and is a valid
   capsule-ecdh-aes256gcm-v1 envelope.
3. Validate tlock_wrapped_key: non-null, non-empty.
4. Call provider.validate(TimeLockCiphertext(chain_id, round, tlock_wrapped_key_bytes)).
   If false → HTTP 422 { "error": "tlock_blob_invalid" }.
5. Validate tlock_chain_id is a known chain: stub accepts only STUB_CHAIN_ID;
   sidecar validates against real drand chain registry.
6. Validate tlock_round is in the future relative to unlock_at (within 1-hour tolerance
   as specified in ARCH-003 §5).
7. Validate tlock_key_digest is present (32 bytes).
8. Validate shamir config if present (ARCH-003 §4 rules unchanged).
9. Validate multi-path fallback rule (ARCH-003 §3).
```

---

## 6. iOS compatibility

The stub produces a 64-byte blob stored in `capsules.tlock_wrapped_key`. iOS clients
never inspect this blob — they read `wrapped_capsule_key` (which wraps `DEK` directly)
and ignore all tlock fields. The stub blob format is transparent to iOS.

The ARCH-003 §6 iOS guarantee is unchanged: `wrapped_capsule_key` always wraps `DEK`,
not `DEK_client`. The `TimeLockProvider` abstraction does not affect iOS decryption.

**Stub blinding scheme correctness:**

The stub's `seal()` receives `DEK_client` (the 32-byte client mask, as required by
the blinding scheme). The sealing client has already computed `DEK_tlock = DEK XOR
DEK_client` and sends it to the server in the `tlock.dek_tlock` sealing request field
(stored in `capsules.tlock_dek_tlock`). The stub blob therefore only needs to preserve
`DEK_client` for gate-check purposes — the server does not reconstruct `DEK_tlock` from
the blob; it reads it directly from `tlock_dek_tlock`.

### 6.1 Blinding scheme — server role and column mapping

The sealing client is responsible for computing all key material. The server stores it:

```
At sealing (client side):
  DEK          = random 32 bytes (capsule content key)
  DEK_client   = random 32 bytes (client-side mask)
  DEK_tlock    = DEK XOR DEK_client  (server-side component)

  tlock_wrapped_key  = IBE-encrypt(DEK_client)  → capsules.tlock_wrapped_key
  tlock_dek_tlock    = DEK_tlock                → capsules.tlock_dek_tlock (NEVER logged)
  tlock_key_digest   = SHA-256(DEK_tlock)       → capsules.tlock_key_digest

  wrapped_capsule_key   = ECDH-wrap(DEK)         → iOS path
  wrapped_blinding_mask = ECDH-wrap(DEK_client)  → Android/web path

At delivery (server side, after round publishes and now() >= unlock_at):
  provider.decrypt(tlock_wrapped_key) → DEK_client  (confirms IBE gate open; not stored)
  verify SHA-256(tlock_dek_tlock) == tlock_key_digest
  serve tlock_dek_tlock to the authenticated Android/web recipient via /tlock-key

At delivery (Android/web client side):
  ECDH-unwrap(wrapped_blinding_mask) → DEK_client
  DEK = DEK_client XOR DEK_tlock
```

The blinding security property: the server holds `DEK_tlock` but never `DEK_client`
(locked in the IBE ciphertext until the round publishes) and never `DEK` (the XOR of
both). The benefit is specifically the **pre-round window**: a server compromise before
the round publishes cannot decrypt capsule content. After the round publishes both halves
are reconstructable server-side, but at that point the capsule is intended to be open.

**`tlock_dek_tlock` column (already in ARCH-003 §2 schema):**

```sql
ALTER TABLE capsules
    ADD COLUMN tlock_dek_tlock  BYTEA NULL;
    -- DEK_tlock = DEK XOR DEK_client, stored at sealing time.
    -- Delivered to Android/web clients via /tlock-key after unlock.
    -- NULL on non-tlock capsules.
    -- NEVER logged (see ARCH-003 §6.5 and ARCH-006 §6.2).
```

### 6.2 Logging prohibition — `tlock_dek_tlock` and `/tlock-key`

`tlock_dek_tlock` is the 32-byte server-side complement to the client mask. It must
never appear in:

- Application logs (SLF4J or any other logger)
- Access logs (nginx, Cloud Run request logs)
- Request/response tracing (OpenTelemetry, Stackdriver Trace)
- Error reporting (Sentry, Crashlytics, etc.)

This applies both to the column value in any DB query logs (ensure `log_min_duration`
in PostgreSQL is not set so low that query results are logged) and to the `/tlock-key`
HTTP response body.

**Implementation checklist for the `/tlock-key` route:**

- [ ] Do not log the response body at any log level.
- [ ] If using an HTTP request-logging middleware, configure it to redact the response
      body for this endpoint path.
- [ ] In integration tests, assert the response body is present but do not log it.
- [ ] The server's access log should record: capsule ID, caller user ID, HTTP status,
      latency. Not: the response body.

---

## 7. `/tlock-key` API endpoint

```
GET /api/capsules/:id/tlock-key
Authorization: Bearer <token>

Response (200 — gate open):
{
  "dek_tlock": "<base64url: 32 bytes>",
  "chain_id": "<hex>",
  "round": 1234567
}

Response (202 — gate not yet open):
{
  "error": "tlock_gate_not_open",
  "detail": "round not yet published",
  "retry_after_seconds": 30
}

Response (403 — not a recipient):
{
  "error": "not_a_recipient"
}

Response (404 — capsule not found or not sealed):
{
  "error": "not_found"
}
```

**Gate logic:**

```
if TLOCK_PROVIDER == disabled:
    return 503 { "error": "tlock_not_enabled" }

if now() < capsules.unlock_at:
    return 202 with retry_after

decryptResult = provider.decrypt(tlock_ciphertext)
if decryptResult == null:
    return 202 with retry_after    // IBE gate not yet open

// decryptResult is DEK_client; server uses it only to confirm round published.
// Server returns tlock_dek_tlock (already stored) — it does NOT return DEK_client.

verify SHA-256(capsules.tlock_dek_tlock) == capsules.tlock_key_digest
    → mismatch: return 500, log ERROR with capsule ID

return 200 { "dek_tlock": base64url(capsules.tlock_dek_tlock), ... }
```

Note: The server calls `provider.decrypt()` solely to confirm the IBE round has
published (non-null result = round is live). It then serves the already-stored
`tlock_dek_tlock` value rather than recomputing anything from `DEK_client`.

---

## 8. Updated sealing request — tlock fields

The sealing request body (ARCH-003 §8) is amended to include `dek_tlock`:

```json
"tlock": {
  "round": 1234567,
  "chain_id": "...",
  "wrapped_key": "<base64url: IBE-encrypt(DEK_client)>",
  "dek_tlock": "<base64url: 32 bytes — DEK XOR DEK_client>",
  "tlock_key_digest": "<base64url: SHA-256(DEK_tlock)>"
}
```

The server validates that `SHA-256(dek_tlock) == tlock_key_digest` at sealing time
(reject if mismatch with HTTP 422). It then stores `dek_tlock` in
`capsules.tlock_dek_tlock`.

`dek_tlock` is 32 bytes of key material. The same logging prohibition applies to
this field in request logs as to the `/tlock-key` response body.

---

## Dependency chain

```
ARCH-005 (algorithm IDs)
  └── ARCH-004 (connections model)
       └── ARCH-003 (capsule crypto brief + blinding scheme)
            └── ARCH-006 (this document — TimeLockProvider interface)
                 ├── M11 developer task: server sealing endpoint (uses provider.validate())
                 ├── M11 developer task: /tlock-key endpoint (uses provider.decrypt())
                 ├── M11 developer task: Android tlock sealing client
                 ├── M11 developer task: web tlock sealing client
                 └── M12 developer task: Go sidecar (real drand integration)
```

No M11 tlock developer task should begin without reading both ARCH-003 and this brief.
