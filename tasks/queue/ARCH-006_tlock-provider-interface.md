---
id: ARCH-006
title: TimeLock provider interface — stub first, drand later
category: Architecture
priority: High
status: queued
assigned_to: TechnicalArchitect
depends_on: [ARCH-003]
touches:
  - docs/briefs/ARCH-006_tlock-provider-interface.md
estimated: 2-3 hours (agent)
---

## Goal

Design a `TimeLockProvider` abstraction that decouples M11's tlock sealing flow from
the real drand/BLS12-381 integration. The drand integration is the highest-risk item
in M11 — this abstraction lets all other M11 work proceed against a working stub,
with the real provider swapped in later without touching the sealing/unsealing logic.

## Background

ARCH-003 defines `tlock-bls12381-v1` as the time-lock algorithm for M11. The real
implementation requires either a JNI bridge to the Go drand SDK or a pure-Kotlin
BLS12-381 port — both are significant, high-risk engineering work.

The CTO decision (2026-05-15): ship a `StubTimeLockProvider` for M11 that makes the
tlock path work end-to-end (sealing, gate checking, unsealing) without real drand
cryptography. The stub is intentionally fake — it is for internal testing and staging
only, never production. The real drand provider is M12 scope.

## What the brief should specify

### 1. `TimeLockProvider` interface (Kotlin, server-side)

Define the Kotlin interface the server calls. At minimum:

```kotlin
interface TimeLockProvider {
    // Seal: given a target unlock time, produce a tlock ciphertext + chain/round metadata.
    // The ciphertext wraps the given plaintextKey (32 bytes).
    fun seal(plaintextKey: ByteArray, unlockAt: Instant): TimeLockCiphertext

    // Decrypt: if the round has "published" (i.e. now >= unlockAt for stub),
    // returns the plaintext key; otherwise returns null (gate not yet open).
    fun decrypt(ciphertext: TimeLockCiphertext): ByteArray?

    // Validate structural integrity of a ciphertext blob (called at sealing time).
    fun validate(ciphertext: TimeLockCiphertext): Boolean
}

data class TimeLockCiphertext(
    val chainId: String,   // drand chain hash (hex) — stub uses a fixed fake value
    val round: Long,       // drand round number — stub derives from unlockAt
    val blob: ByteArray    // opaque ciphertext bytes — stored in capsules.tlock_wrapped_key
)
```

### 2. `StubTimeLockProvider` implementation spec

The stub must:
- Accept any `unlockAt` and produce a valid-looking `TimeLockCiphertext`
- Generate `blob` deterministically: e.g. `HMAC-SHA256(stub_secret, unlockAt_epoch_seconds || plaintextKey)` — this is reversible (the stub can "decrypt" it)
- Use a fixed stub `chainId` (e.g. `"stub-chain-0000000000000000"`) that is distinct from any real drand chain
- Derive `round` from `unlockAt` using the same formula as real drand (genesis + period) but against a stub genesis/period
- `decrypt()` returns the key if `Instant.now() >= unlockAt`, null otherwise — pure time-check, no real crypto
- The `stub_secret` is a server config value (env var `TLOCK_STUB_SECRET`); absent → stub disabled in production

### 3. Microservice boundary (optional but preferred)

Consider whether `TimeLockProvider` should be called:

**Option A — In-process:** The provider is a Kotlin class instantiated in the server.
Simple, no network hop, but the real drand provider would need JNI or pure-Kotlin BLS12-381.

**Option B — Sidecar microservice:** The server calls a small HTTP service
(`POST /seal`, `POST /decrypt`, `GET /validate`). The stub is an HTTP server
returning fake data. The real drand provider is a separate Go process (natural fit
for the Go drand SDK) — no JNI required.

Recommendation: Option B. The Go drand SDK is the canonical implementation; calling
it from a Go sidecar avoids JNI entirely. The sidecar speaks a minimal REST API.
Specify that REST API contract.

### 4. Configuration and environment flags

- `TLOCK_PROVIDER=stub|sidecar|disabled` (default: `disabled`)
- `TLOCK_STUB_SECRET` — required when `TLOCK_PROVIDER=stub`
- `TLOCK_SIDECAR_URL` — required when `TLOCK_PROVIDER=sidecar`
- Server rejects any sealing request with `tlock` fields if `TLOCK_PROVIDER=disabled`
- Staging uses `stub`; production uses `sidecar` (M12)

### 5. Impact on ARCH-003 sealing validation

Review ARCH-003 §4 (server validation at sealing time). The server validates
`tlock_wrapped_key` as "non-null and non-empty (content is opaque)". With the
`TimeLockProvider` abstraction, the server additionally calls `provider.validate()`
at sealing time. Update the validation spec accordingly.

### 6. iOS compatibility note

The stub must produce `blob` bytes that iOS clients can safely ignore (they use the
ECDH path). Confirm the existing ARCH-003 §6 iOS guarantee is unchanged.

## Output

Produce `docs/briefs/ARCH-006_tlock-provider-interface.md` covering all six points
above. The brief is the contract that M11 developer tasks (server sealing endpoint,
Android tlock client, web tlock client) will implement against. Include the sidecar
REST API contract in full if Option B is recommended.

Do not write any Kotlin or Go code — this is a design doc only.
