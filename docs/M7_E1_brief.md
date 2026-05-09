# SE Brief: M7 E1 — Foundations

**Date:** 9 May 2026
**Milestone:** M7 — Vault E2EE
**Increment:** E1 of 5
**Type:** Backend-only. No client changes. No new API endpoints.

---

## Goal

Lay the server-side foundations for E2EE before any client code touches them.
Three deliverables: schema migrations, an envelope format library, and a format
spec document. Tests prove the migrations are clean and the library behaves
correctly under good and bad input. After E1, no user-facing behaviour changes —
a fresh DB and an existing DB both migrate cleanly, and the app continues to work
exactly as before.

---

## Prerequisite: rename `captured_at` → `taken_at`

The master brief uses `taken_at` throughout. The current schema (V4) and all
server code uses `captured_at`. This rename must land in E1, as V13 references
`taken_at` and the sort enum has a `TAKEN_NEWEST`/`TAKEN_OLDEST` path.

**Schema:** V12 handles the rename (see below).

**Code:** update every occurrence of `captured_at` / `capturedAt` in:

- `Database.kt` — `UploadRecord.capturedAt`, the `recordUpload` INSERT, all
  SELECT projections, sort predicates referencing `captured_at`.
- `UploadRecord.toJson()` — the serialised field name (check what the current
  key is; align with `taken_at` if it wasn't already).
- Any test that references `capturedAt` or `captured_at` by name.

This is a rename, not a design change. Do it mechanically and update all call
sites in one pass before V12 runs.

---

## Migrations

### V12 — rename + key-management tables

```sql
-- V12__m7_foundations.sql

-- 1. Rename captured_at to taken_at (existing data preserved)
ALTER TABLE uploads RENAME COLUMN captured_at TO taken_at;

-- 2. Device key store
CREATE TABLE wrapped_keys (
  id              UUID PRIMARY KEY,
  user_id         UUID NULL,           -- NULL in M7 (single-user sentinel); NOT NULL at M8
  device_id       TEXT NOT NULL UNIQUE,
  device_label    TEXT NOT NULL,
  device_kind     TEXT NOT NULL CHECK (device_kind IN ('android', 'web', 'ios')),
  pubkey_format   TEXT NOT NULL,       -- e.g. 'p256-spki'
  pubkey          BYTEA NOT NULL,
  wrapped_master_key BYTEA NOT NULL,
  wrap_format     TEXT NOT NULL,       -- e.g. 'p256-ecdh-hkdf-aes256gcm-v1'
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  last_used_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  retired_at      TIMESTAMPTZ NULL     -- NULL = active; non-NULL = soft-retired
);

CREATE INDEX idx_wrapped_keys_active ON wrapped_keys (last_used_at)
  WHERE retired_at IS NULL;

-- 3. Passphrase-wrapped master key backup (single row in M7)
CREATE TABLE recovery_passphrase (
  id              INTEGER PRIMARY KEY DEFAULT 1 CHECK (id = 1),
  user_id         UUID NULL,
  wrapped_master_key BYTEA NOT NULL,
  wrap_format     TEXT NOT NULL,       -- e.g. 'argon2id-aes256gcm-v1'
  argon2_params   JSONB NOT NULL,      -- {m: <kib>, t: <iters>, p: <lanes>}
  salt            BYTEA NOT NULL,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 4. Orphaned-blob tracking (blobs uploaded but not yet confirmed or migrated)
--    Rows are deleted on successful /confirm or /migrate.
--    A background job (E2) deletes rows older than 24h and their blobs.
CREATE TABLE pending_blobs (
  id              UUID PRIMARY KEY,
  storage_key     TEXT NOT NULL UNIQUE,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pending_blobs_created_at ON pending_blobs (created_at);
```

### V13 — E2EE columns on uploads

Additive only. No column is dropped. No existing data is destroyed. Existing
rows get `storage_class = 'legacy_plaintext'` from the DEFAULT, and all new
nullable columns default to NULL. The consistency constraint is satisfied by all
existing rows at the moment it is added.

```sql
-- V13__m7_uploads_e2ee.sql

ALTER TABLE uploads
  ADD COLUMN storage_class TEXT NOT NULL DEFAULT 'legacy_plaintext'
    CHECK (storage_class IN ('encrypted', 'public', 'legacy_plaintext')),
  ADD COLUMN envelope_version     INTEGER NULL,
  ADD COLUMN wrapped_dek          BYTEA NULL,
  ADD COLUMN dek_format           TEXT NULL,
  ADD COLUMN encrypted_metadata   BYTEA NULL,
  ADD COLUMN encrypted_metadata_format TEXT NULL,
  ADD COLUMN thumbnail_storage_key TEXT NULL,
  ADD COLUMN wrapped_thumbnail_dek BYTEA NULL,
  ADD COLUMN thumbnail_dek_format  TEXT NULL;

-- Storage-class consistency:
-- encrypted rows MUST have wrapped_dek and dek_format
-- non-encrypted rows MUST have those fields NULL
ALTER TABLE uploads ADD CONSTRAINT uploads_storage_class_consistency
  CHECK (
    (storage_class = 'encrypted'
      AND wrapped_dek IS NOT NULL
      AND dek_format IS NOT NULL)
    OR
    (storage_class IN ('public', 'legacy_plaintext')
      AND wrapped_dek IS NULL
      AND dek_format IS NULL)
  );
```

`envelope_version` is set to `1` for all new encrypted uploads at INSERT time.
It is left NULL for `legacy_plaintext` rows (no backfill required).

`thumbnail_storage_key` is the GCS key for the encrypted thumbnail. The
existing `thumbnail_key` column (V3) is retained for `legacy_plaintext` and
`public` thumbnails. The read path dispatches on `storage_class`:
`legacy_plaintext` → use `thumbnail_key`; `encrypted` → use
`thumbnail_storage_key`. Compost cleanup must handle both.

`content_hash` semantics: for `encrypted` rows, this is the SHA-256 of the
ciphertext, not the plaintext. The dedup guard (`findByContentHash`) is
**skipped** for encrypted uploads — ciphertext is non-deterministic and the
hash provides no dedup value. This is a deliberate decision (documented in
M7 design decisions). E2 removes the guard from `confirmUploadHandler` for
encrypted uploads.

### V14 — E2EE columns on capsule messages

`capsule_messages.body` becomes nullable to admit encrypted rows (where the
plaintext body is replaced by an encrypted blob). Existing rows are
`legacy_plaintext` and keep their non-null `body`. The constraint enforces
this invariant.

```sql
-- V14__m7_capsule_messages_e2ee.sql

ALTER TABLE capsule_messages
  ALTER COLUMN body DROP NOT NULL;

ALTER TABLE capsule_messages
  ADD COLUMN storage_class    TEXT NOT NULL DEFAULT 'legacy_plaintext'
    CHECK (storage_class IN ('encrypted', 'public', 'legacy_plaintext')),
  ADD COLUMN envelope_version INTEGER NULL,
  ADD COLUMN encrypted_body   BYTEA NULL,
  ADD COLUMN body_format      TEXT NULL,
  ADD COLUMN wrapped_dek      BYTEA NULL,
  ADD COLUMN dek_format       TEXT NULL;

ALTER TABLE capsule_messages ADD CONSTRAINT capsule_messages_storage_class_consistency
  CHECK (
    (storage_class = 'encrypted'
      AND encrypted_body IS NOT NULL
      AND wrapped_dek IS NOT NULL
      AND dek_format IS NOT NULL
      AND body IS NULL)
    OR
    (storage_class IN ('public', 'legacy_plaintext')
      AND body IS NOT NULL
      AND encrypted_body IS NULL
      AND wrapped_dek IS NULL
      AND dek_format IS NULL)
  );
```

---

## Envelope format library

**File:** `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/EnvelopeFormat.kt`

The server does **no** crypto operations in production. It only validates that
uploaded blobs are structurally sound envelopes — correct version byte, a known
algorithm ID, sane lengths for nonce and auth tag. Decryption is the client's job.

### Constants and registry

```kotlin
object AlgorithmIds {
    const val AES256GCM_V1              = "aes256gcm-v1"
    const val MASTER_AES256GCM_V1       = "master-aes256gcm-v1"
    const val P256_ECDH_HKDF_AES256GCM_V1 = "p256-ecdh-hkdf-aes256gcm-v1"
    const val ARGON2ID_AES256GCM_V1     = "argon2id-aes256gcm-v1"

    val SYMMETRIC  = setOf(AES256GCM_V1, MASTER_AES256GCM_V1, ARGON2ID_AES256GCM_V1)
    val ASYMMETRIC = setOf(P256_ECDH_HKDF_AES256GCM_V1)
    val ALL        = SYMMETRIC + ASYMMETRIC
}
```

### Binary format (recap)

Symmetric envelope:
```
[1 byte]  envelope_version  — must be 0x01
[1 byte]  alg_id_len        — byte length of the algorithm ID string (max 64)
[N bytes] alg_id            — UTF-8 algorithm ID string
[12 bytes] nonce
[variable] ciphertext        — zero or more bytes
[16 bytes] auth_tag
```

Asymmetric envelope (P-256 ECDH wraps only):
```
[1 byte]  envelope_version
[1 byte]  alg_id_len
[N bytes] alg_id
[65 bytes] ephemeral_pubkey — P-256 SEC1 uncompressed (0x04 prefix)
[12 bytes] nonce
[variable] ciphertext
[16 bytes] auth_tag
```

Minimum valid symmetric envelope size: `1 + 1 + 1 + 12 + 0 + 16 = 31` bytes
(shortest possible alg_id is 1 character, ciphertext can be empty).

Minimum valid asymmetric envelope size: `1 + 1 + 1 + 65 + 12 + 0 + 16 = 96` bytes.

### Public API

```kotlin
class EnvelopeFormatException(message: String) : Exception(message)

data class ParsedEnvelope(
    val version: Int,
    val algorithmId: String,
    val nonce: ByteArray,         // always 12 bytes
    val ciphertext: ByteArray,
    val authTag: ByteArray,       // always 16 bytes
)

data class ParsedAsymmetricEnvelope(
    val version: Int,
    val algorithmId: String,
    val ephemeralPubkey: ByteArray, // always 65 bytes (P-256 uncompressed)
    val nonce: ByteArray,           // always 12 bytes
    val ciphertext: ByteArray,
    val authTag: ByteArray,         // always 16 bytes
)

object EnvelopeFormat {
    // Validate and parse a symmetric envelope.
    // expectedAlgorithmId: if non-null, the parsed ID must match exactly.
    // Throws EnvelopeFormatException on any structural problem.
    fun validateSymmetric(blob: ByteArray, expectedAlgorithmId: String? = null): ParsedEnvelope

    // Validate and parse an asymmetric envelope.
    fun validateAsymmetric(blob: ByteArray, expectedAlgorithmId: String? = null): ParsedAsymmetricEnvelope

    // Non-throwing convenience wrappers.
    fun isValidSymmetric(blob: ByteArray, expectedAlgorithmId: String? = null): Boolean
    fun isValidAsymmetric(blob: ByteArray, expectedAlgorithmId: String? = null): Boolean
}
```

`EnvelopeFormatException` is the single error type for all structural failures.
The message should be specific enough to diagnose the problem from server logs
(e.g., "unknown algorithm ID: foo-v9", "nonce length 8, expected 12").

---

## Encrypted metadata JSON schema

**Must be locked in E1** — Android (E3) and web (E4) both write to this schema;
divergence is a cross-platform correctness failure.

Documented in full in `docs/envelope_format.md` (see below). Summary here for the
implementer:

```json
{
  "v": 1,
  "gps_lat": null,
  "gps_lon": null,
  "gps_alt": null,
  "camera_make": null,
  "camera_model": null,
  "lens_model": null,
  "focal_length_mm": null,
  "iso": null,
  "exposure_num": null,
  "exposure_den": null,
  "aperture": null
}
```

Field semantics:

| Field | Type | Notes |
|---|---|---|
| `v` | Int, required | Schema version. Always `1` in M7. |
| `gps_lat` | Double? | WGS84 decimal degrees. Negative = South. |
| `gps_lon` | Double? | WGS84 decimal degrees. Negative = West. |
| `gps_alt` | Double? | Metres above sea level. |
| `camera_make` | String? | EXIF Make tag (e.g. "Apple", "Samsung"). |
| `camera_model` | String? | EXIF Model tag (e.g. "iPhone 15 Pro"). |
| `lens_model` | String? | EXIF LensModel tag, if present. |
| `focal_length_mm` | Double? | Actual (not 35mm-equivalent) focal length. |
| `iso` | Int? | ISO speed rating. |
| `exposure_num` | Int? | Exposure time numerator. 1 for 1/250s. |
| `exposure_den` | Int? | Exposure time denominator. 250 for 1/250s. |
| `aperture` | Double? | F-number (e.g. 1.8 for f/1.8). |

All fields except `v` are optional. An all-null blob is valid (for media where
EXIF extraction produced nothing). Clients must tolerate unknown fields — future
schema versions may add fields; M7 clients should ignore what they don't
recognise.

The `taken_at` timestamp is **not** in this blob — it lives plaintext on the
`uploads` row and is operational infrastructure (sort, filters, Just arrived).

---

## Documentation: `docs/envelope_format.md`

Create this file in E1. It is the cross-platform contract that E3 and E4 implement
against. Contents:

1. **Purpose** — one paragraph on what the format solves and why it's versioned.
2. **Binary format specification** — both variants (symmetric, asymmetric), with
   field-by-field byte layout, as per the master brief.
3. **Algorithm identifier table** — the four M7 IDs, their use cases, and the
   note that unknown IDs must fail loudly.
4. **Argon2id parameters** — defaults (`m=65536`, `t=3`, `p=1`, 16-byte salt,
   32-byte output). Note that parameters are stored alongside each wrapped blob in
   `recovery_passphrase.argon2_params` so future tuning doesn't break existing wraps.
5. **Encrypted metadata JSON schema** — the full field table from this brief.
6. **Nonce generation** — 12 random bytes per encryption operation, from a
   cryptographically secure RNG. Never a counter. Never reused across encryptions
   with the same key.
7. **Minimum and maximum blob sizes** — for each envelope type, so validators
   can do a fast length pre-check before parsing.

---

## Tests

**File:** `HeirloomsServer/src/test/kotlin/digital/heirlooms/server/EnvelopeFormatTest.kt`

BouncyCastle is used **in test scope only** to construct well-formed envelopes for
round-trip testing. The production server never decrypts; the test harness does.

Add to `build.gradle.kts` test dependencies:
```kotlin
testImplementation("org.bouncycastle:bcprov-jdk18on:1.79.0")
```

### Envelope format unit tests (~20)

**Happy-path round-trips (BouncyCastle constructs; EnvelopeFormat parses):**
1. Symmetric envelope, `aes256gcm-v1` — parse succeeds; nonce, ciphertext, tag match
2. Symmetric envelope, `master-aes256gcm-v1` — parse succeeds
3. Asymmetric envelope, `p256-ecdh-hkdf-aes256gcm-v1` — parse succeeds;
   ephemeral pubkey is 65 bytes
4. `expectedAlgorithmId` matches — parse succeeds
5. `expectedAlgorithmId` mismatches — `EnvelopeFormatException`

**Version errors:**
6. Version byte `0x00` — `EnvelopeFormatException`
7. Version byte `0x02` — `EnvelopeFormatException`

**Algorithm ID errors:**
8. Unknown algorithm ID — `EnvelopeFormatException`
9. Algorithm ID string too long (>64 bytes) — `EnvelopeFormatException`
10. Algorithm ID length byte claims more bytes than remain in blob — `EnvelopeFormatException`
11. `validateSymmetric` called with an asymmetric algorithm ID — `EnvelopeFormatException`
12. `validateAsymmetric` called with a symmetric algorithm ID — `EnvelopeFormatException`

**Structural size errors:**
13. Nonce 8 bytes (truncated) — `EnvelopeFormatException`
14. Auth tag 8 bytes (truncated, blob too short at the end) — `EnvelopeFormatException`
15. Blob truncated mid-nonce — `EnvelopeFormatException`
16. Empty blob — `EnvelopeFormatException`
17. Asymmetric: ephemeral pubkey is 64 bytes (not 65) — `EnvelopeFormatException`
18. `isValidSymmetric` returns false for a bad blob (non-throwing equivalent)

### Schema architecture canary tests

These tests use Testcontainers (already in the test suite). They verify that the
storage_class architecture works as designed at both the schema and API levels.

19. Direct SQL: INSERT a `storage_class='public'` row into `uploads` with NULL
    `wrapped_dek` and `dek_format` — must **succeed** at the DB level. (The
    architecture admits public; it's the API that rejects it.)
20. Direct SQL: INSERT a `storage_class='encrypted'` row with NULL `wrapped_dek`
    — must **fail** with a constraint violation.
21. Direct SQL: INSERT a `storage_class='legacy_plaintext'` row with non-NULL
    `wrapped_dek` — must **fail** with a constraint violation.
22. Verify all existing `uploads` rows after migration have `storage_class =
    'legacy_plaintext'` and NULL `wrapped_dek`.

---

## What E1 does NOT include

- No new API endpoints. The API shape doesn't change in E1.
- No client changes.
- No encryption or decryption operations in production code.
- No orphaned-blob cleanup job (table is created in V12; job is E2).
- No link-flow state machine or `pending_link_requests` table (E2).

---

## Acceptance criteria

1. `./gradlew test` passes with all new tests green and no regressions.
2. Migration runs cleanly on a fresh database (Testcontainers).
3. Migration runs cleanly against a database that has existing `uploads` rows —
   all existing rows end up with `storage_class = 'legacy_plaintext'`, NULL
   E2EE columns, and satisfy the consistency constraint.
4. All four schema canary tests pass (public insert succeeds at DB; encrypted
   with null DEK fails; legacy with non-null DEK fails; existing rows are
   legacy_plaintext).
5. `docs/envelope_format.md` exists and covers all seven sections listed above.
6. The `captured_at` → `taken_at` rename is complete with no remaining
   `captured_at` or `capturedAt` references in production code.
7. `EnvelopeFormat` has no production dependency on BouncyCastle.

---

## Documentation updates

- `docs/envelope_format.md` — **create** (the E1 deliverable itself)
- `docs/IDIOMS.md` — add entries: *Envelope*, *Wrapped key*, *Master key*,
  *Storage class*, *DEK*
- `docs/PA_NOTES.md` — add: algorithm-ID registry, BouncyCastle test-only
  pattern, `pending_blobs` table purpose, `user_id = NULL` as M7 sentinel for
  `wrapped_keys` (mirrors plots pattern)
- `docs/VERSIONS.md` — v0.26.0 entry when E1 ships
- `docs/PROMPT_LOG.md` — standard entry

---

## Ship state after E1

v0.26.0. No user-visible behaviour change. The app works identically to v0.25.x.
The schema is E2EE-ready; the envelope library is server-verified. Old clients
continue to upload as today. E2 adds the API endpoints that use these foundations.
