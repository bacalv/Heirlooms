# ARCH-007 — E2EE Tag Scheme

*Authored: 2026-05-16. Status: proposed — awaiting PA approval. Design only; no production code.*

Prerequisite reading:
- `docs/envelope_format.md` — binary wire format for all encrypted blobs
- `docs/briefs/ARCH-003_m11-capsule-crypto-brief.md` — key derivation patterns and HKDF convention

---

## Summary

Tags are currently stored as plaintext `TEXT[]` on the `uploads` table. A tag like
"grandmother", "diagnosis", or "affair" is personal data that violates Heirlooms'
founding privacy principle: Heirlooms staff should never be able to read user content.

This brief specifies an HMAC token scheme that converts tag values to opaque tokens
while keeping server-side trellis evaluation intact. Display names are encrypted
client-side and stored alongside their tokens. The scheme is per-user (two users
with the same tag value produce different tokens). Migration is a three-phase additive
rollout with backward compatibility preserved throughout.

The accepted residual risk (the server can infer tag cardinality and co-occurrence
patterns from token correlation) is documented in SEC-012 and is out of scope here.

---

## Current state (what we have today)

**Schema:**
- `uploads.tags` — `TEXT[] NOT NULL DEFAULT '{}'`; indexed with GIN
- `plots.criteria` — JSONB; tag atoms store the raw string: `{"type": "tag", "tag": "grandmother"}`
- No `member_tags` table; per-member tags on shared-plot items are not yet supported

**Server behaviour:**
- `UploadRepository.updateTags()` writes plaintext strings directly
- `UploadRepository.listAllTags()` reads and returns plaintext strings (`DISTINCT UNNEST(tags)`)
- `UploadRepository.listUploadsPaginated()` filters with `tags && ARRAY[?]::text[]` and `tags @> ARRAY[?]::text[]`
- `CriteriaEvaluator` evaluates `{"type": "tag", "tag": "..."}` with `tags @> ARRAY[?]::text[]`
- `TrellisRepository.runUnstagedTrellisesForUpload()` calls `CriteriaEvaluator` to route uploads

**Client behaviour (Android / Web / iOS):**
- Clients send and receive tag values as plain UTF-8 strings in API payloads
- No client-side crypto involved in tags today

---

## Proposed scheme

### Tag token derivation

A tag token is a deterministic, keyed pseudonym for a tag value. It is derived from
the user's master key so that the same value produces a different token for each user.

```
tag_token_key  = HKDF-SHA256(
                     ikm  = master_key,
                     salt = [],
                     info = UTF-8("tag-token-v1")
                 )

tag_token      = HMAC-SHA256(key = tag_token_key, data = UTF-8(tag_value))
```

The token is 32 bytes (256 bits). It is stored hex-encoded (64 chars) or as BYTEA.
The server only ever sees tokens; it can compare them for equality but cannot reverse
them to plaintext.

**HMAC over HKDF-then-HMAC rationale:** HKDF produces a derived key from the master
key, scoped to the "tag-token-v1" purpose. HMAC-SHA256 then maps each tag value to a
deterministic 256-bit pseudonym. This two-step approach is the same pattern used in
the iOS `EnvelopeCrypto.wrapDEK` derivation chain and is consistent with RFC 5869.
Using HKDF output directly as a tag pseudonym (rather than as a HMAC key) would
couple the key material directly to plaintext — using it as an HMAC key is cleaner.

**Android:** `VaultCrypto.hkdf()` already implements HKDF-SHA256. Add `hmacSha256(key, data)`
using `javax.crypto.Mac` (`HmacSHA256`) — the same JCE mechanism already used for HKDF
internally.

**Web:** `vaultCrypto.js` already exports `hkdf()` using WebCrypto. Add `hmacSha256(key, data)`
using `crypto.subtle.sign('HMAC', ...)`.

**iOS:** `CryptoKit.HMAC<SHA256>.authenticationCode(for:using:)`.

### Tag display name encryption

The human-readable tag string is encrypted client-side for display. It uses a
separate HKDF context from the token key so that the display ciphertext and token
key are cryptographically independent.

```
tag_display_key  = HKDF-SHA256(
                       ikm  = master_key,
                       salt = [],
                       info = UTF-8("tag-display-v1")
                   )

tag_display_ciphertext = symmetric envelope with alg_id = "aes256gcm-v1"
                         encrypted under tag_display_key
                         plaintext = UTF-8(tag_value)
```

The symmetric envelope format is the standard Heirlooms envelope (version 0x01,
`aes256gcm-v1`, 12-byte random nonce, ciphertext, 16-byte auth tag). This does not
require a new algorithm ID; `aes256gcm-v1` is already registered and implemented on
all platforms.

**Storage:** the ciphertext is stored in BYTEA. Typical tag display names are 1–50
UTF-8 bytes; the resulting envelope is 31–81 bytes.

### Trellis criteria format

Trellis criteria that reference tags store the token, not the plaintext value. The
`CriteriaEvaluator` compares tokens rather than strings.

**Before (current):**
```json
{ "type": "tag", "tag": "grandmother" }
```

**After (E2EE):**
```json
{ "type": "tag", "token": "4a7f2c..." }
```

The client derives the token before constructing or evaluating a criteria expression.
The server evaluates by comparing `tag_token = ?` against the stored token column.
The `"tag"` field is dropped from the wire format; the `"token"` field is mandatory.

The `CriteriaEvaluator` `"tag"` case must be updated to read the `"token"` field and
generate SQL against the token column (see Schema changes below). The `"tag"` field
must be rejected after Phase 3 enforcement.

### Per-member tags on shared plot items

User A can tag a shared-plot upload that User B uploaded. Those tags must be:
- Invisible to User B
- Invisible to Heirlooms staff
- Usable in User A's trellises (User A's tags route to User A's plots)

A new `member_tags` table tracks per-user tag applications on any upload (own or shared):

```
(user_id, upload_id) → (tag_token, tag_display_ciphertext)
```

The existing `uploads.tag_tokens[]` column stores the uploader's own tags. When
evaluating trellis criteria for User A:
- The server checks `uploads.tag_tokens` for uploads owned by User A
- The server checks `member_tags` for uploads in shared plots where User A is a member

The `member_tags` table does not overlap with `uploads.tag_tokens`: an upload's own
uploader always uses `uploads.tag_tokens`; members always use `member_tags`.

### Auto-tagging loop prevention

If a trellis applies a tag to an uploaded item via an auto-tag action (not yet in
scope but architecturally anticipated), that auto-applied tag must not re-trigger the
same trellis on the next routing pass. A separate HKDF context prevents auto-tag
tokens from matching trellis tag criteria:

```
auto_tag_token_key  = HKDF-SHA256(
                          ikm  = master_key,
                          salt = [],
                          info = UTF-8("auto-tag-token-v1")
                      )

auto_tag_token = HMAC-SHA256(key = auto_tag_token_key, data = UTF-8(tag_value))
```

Trellis criteria atoms of type `"tag"` match only `tag-token-v1` tokens (stored in
`uploads.tag_tokens` and `member_tags.tag_token`). Auto-tag tokens from `auto-tag-token-v1`
are stored in a distinct column (e.g. `uploads.auto_tag_tokens`) or a separate
`auto_member_tags` table, and `CriteriaEvaluator` never queries them when evaluating
tag criteria. Auto-tagging is out of scope for this ticket; this section documents the
token namespace isolation so no future developer accidentally collapses the two.

---

## Schema changes

### uploads table

```sql
-- V32__e2ee_tags.sql (Phase 1 — additive only)

-- New column: array of HMAC tokens (one per tag). Replaces tags TEXT[] once clients migrate.
-- NULL = not yet migrated; '{}' = migrated with no tags.
ALTER TABLE uploads
    ADD COLUMN tag_tokens  BYTEA[]  NULL DEFAULT NULL;

-- New column: corresponding display name envelopes, parallel array to tag_tokens.
ALTER TABLE uploads
    ADD COLUMN tag_display_ciphertexts  BYTEA[]  NULL DEFAULT NULL;

-- Index for server-side token lookup (GIN on array of tokens).
CREATE INDEX idx_uploads_tag_tokens ON uploads USING GIN (tag_tokens)
    WHERE tag_tokens IS NOT NULL;

-- NOTE: uploads.tags (TEXT[]) is retained unchanged in Phase 1.
-- It is removed only in Phase 3 after enforcement deadline.
```

**Column semantics:**
- `tag_tokens IS NULL` — upload not yet migrated (client has not re-encrypted)
- `tag_tokens = '{}'` — migrated, no tags
- `tag_tokens IS NOT NULL AND array_length(tag_tokens, 1) > 0` — migrated with tags

The parallel-array design mirrors how existing DEK columns work (`wrapped_dek`,
`dek_format` are separate columns rather than a joined table) and avoids a JOIN on
every upload fetch. The maximum practical tag count per upload is small (single
digits to low tens), so parallel BYTEA arrays are not a storage concern.

### member_tags table

```sql
-- V32__e2ee_tags.sql (Phase 1)

CREATE TABLE member_tags (
    user_id                  UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    upload_id                UUID        NOT NULL REFERENCES uploads(id) ON DELETE CASCADE,
    tag_token                BYTEA       NOT NULL,   -- HMAC-SHA256 (32 bytes)
    tag_display_ciphertext   BYTEA       NOT NULL,   -- aes256gcm-v1 envelope
    tagged_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, upload_id, tag_token)
);

CREATE INDEX idx_member_tags_upload ON member_tags(upload_id);
CREATE INDEX idx_member_tags_user   ON member_tags(user_id);
```

**Access patterns:**
- When User A tags a shared-plot upload: INSERT or update `member_tags` for
  `(user_id=A, upload_id=X, tag_token=T, tag_display_ciphertext=C)`
- When listing User A's tags on upload X: SELECT from `member_tags WHERE user_id=A AND upload_id=X`
- When evaluating trellis criteria for User A: check `member_tags WHERE user_id=A AND tag_token=?`
  in addition to `uploads.tag_tokens` for owned uploads

### trellises table (criteria field)

No DDL change. The JSONB `criteria` field stores either the old `{"type":"tag","tag":"..."}` format
(during migration) or the new `{"type":"tag","token":"4a7f..."}` format. The `CriteriaEvaluator`
must handle both during Phase 2 (backward compatibility). After Phase 3, only the token format
is accepted.

**Updated CriteriaEvaluator `"tag"` case:**

```kotlin
"tag" -> {
    // Phase 2: accept both "token" (new) and "tag" (legacy, deprecated)
    val token = node.get("token")?.asText()?.takeIf { it.isNotBlank() }
    val legacyTag = node.get("tag")?.asText()?.takeIf { it.isNotBlank() }
    when {
        token != null -> {
            // Decode hex token to bytes; compare against tag_tokens array
            val tokenBytes = hexToBytes(token)
            CriteriaFragment(
                "tag_tokens @> ARRAY[?]::bytea[]",
                listOf { stmt, idx -> stmt.setBytes(idx, tokenBytes); idx + 1 }
            )
        }
        legacyTag != null -> {
            // Phase 2 only: still evaluate against plaintext tags column for unmigrated uploads
            CriteriaFragment(
                "tags @> ARRAY[?]::text[]",
                listOf { stmt, idx -> stmt.setString(idx, legacyTag); idx + 1 }
            )
        }
        else -> throw CriteriaValidationException("'tag' atom requires 'token' field (or legacy 'tag' field)")
    }
}
```

After Phase 3, remove the `legacyTag` branch and reject any criteria using `"tag"` field.

---

## Key derivation contexts (full list)

All contexts derive from `master_key` via HKDF-SHA256 with no salt and UTF-8 info string.

| Context string        | Output use                                      | Notes |
|-----------------------|-------------------------------------------------|-------|
| `"tag-token-v1"`      | HMAC-SHA256 key for deriving tag tokens         | Per-user; same value → different token across users |
| `"tag-display-v1"`    | AES-256-GCM key for encrypting tag display names | Per-user; display name ciphertexts are independent |
| `"auto-tag-token-v1"` | HMAC-SHA256 key for auto-applied tag tokens      | Namespace isolation; not evaluated by trellis criteria |

These three contexts are additive to the existing HKDF usage in the codebase. No
existing context string is modified or reused.

**Token version field (Open Question 3 — see below for resolution):**
A `tag_token_version` column is recommended; see the `uploads` schema addendum below.

---

## Migration strategy

### Phase 1: schema migration (additive)

**Timing:** Deploy immediately.

1. Run `V32__e2ee_tags.sql` (above): add `tag_tokens BYTEA[]`, `tag_display_ciphertexts BYTEA[]`,
   index, and `member_tags` table.
2. Existing `uploads.tags TEXT[]` column is untouched.
3. No client changes required to deploy Phase 1.
4. Server continues to read/write `uploads.tags` for all existing traffic.
5. Server begins accepting both formats from clients: if `tag_tokens` is included in an
   upload tag update request, write both columns. If only `tags` is present (old clients),
   write only `tags` as today.

**Safety property:** Phase 1 is purely additive. Rolling back drops the new columns
and index; no data is lost because the old column is unchanged.

### Phase 2: client-side re-encryption on login

**Timing:** Ship with the next client release on all three platforms (Android, Web, iOS
can ship independently; they do not need to coordinate).

**Migration trigger:** On vault unlock (after master key is available in `VaultSession`),
the client checks whether any of the user's uploads have `tag_tokens IS NULL`. If so,
it performs a background re-encryption pass.

**Migration process per upload:**
1. Fetch all uploads where `tag_tokens IS NULL` (server returns `tags` for these).
2. For each upload, for each tag string in `tags`:
   a. Derive `tag_token = HMAC(tag_token_key, UTF-8(tag_value))`
   b. Encrypt `tag_display_ciphertext = AES-256-GCM(tag_display_key, UTF-8(tag_value))`
3. PATCH the upload with `tag_tokens` and `tag_display_ciphertexts`.
4. Server writes both columns; `tag_tokens` becomes non-NULL.

**Partial migration safety:** Partial migration is safe. An upload with `tag_tokens IS NULL`
is still served correctly via the old `tags` column. An upload with `tag_tokens IS NOT NULL`
is served from the new column. Both can coexist indefinitely without data loss or
trellis misfires (Phase 2 `CriteriaEvaluator` handles both). A crash mid-migration
leaves some uploads in the old state; they will be picked up on the next login.

**Migration UX — resolution of Open Question 2:** Show a brief, non-blocking progress
indicator ("Securing your tags..." toast or banner) during the migration pass. Do not
block login or show a full-screen modal. Rationale:

- **Silent background migration** is tempting but risks the user not noticing if migration
  stalls (e.g. poor connectivity) and later wondering why trellis evaluation is inconsistent.
  A non-intrusive indicator closes this gap without disrupting flow.
- **Full blocking screen** is disproportionate for most users who have few tags (single
  digits to low tens). Even at 1,000 tags — an extreme case — the HMAC and AES
  operations take < 50 ms on the Galaxy A02s reference device. The migration is not
  user-perceptible in duration.
- **Brief indicator** (Android: `Snackbar`; Web: toast notification; iOS: status text in
  the unlock flow) informs the user without blocking them. Dismiss automatically after
  the pass completes. No coordination between platforms is required; each platform migrates
  its own session independently.

**Scale estimate:** 1,000 uploads × 5 tags avg = 5,000 HMAC + 5,000 AES-GCM operations.
HMAC-SHA256 throughput on Galaxy A02s is ~50 MB/s; each call operates on ~20 bytes → ~1 µs
per HMAC. 5,000 HMACs + 5,000 AES-GCM = ~10 ms total crypto + network round-trips for
batched PATCH calls. Total wall-clock time < 2 seconds for extreme cases; < 200 ms typical.

### Phase 3: server enforcement (reject plaintext tags after N days)

**Timing:** Set enforcement date N = 60 days after Phase 2 client release. Announce in
release notes.

1. Add a server flag: `ENFORCE_TAG_TOKENS_AFTER = <ISO date>`. When `now() >= flag date`:
   - Reject any upload tag update that does not include `tag_tokens`.
   - Reject any trellis criteria that uses `{"type":"tag","tag":"..."}` format.
   - Stop reading `uploads.tags` for trellis evaluation.
2. After enforcement: drop `uploads.tags TEXT[]` column and its GIN index.
3. Update `listAllTags()` to read from `uploads.tag_display_ciphertexts` and
   `member_tags.tag_display_ciphertext` (return encrypted blobs; client decrypts for display).

**Server `listAllTags()` after Phase 3:**
The server returns `(tag_token, tag_display_ciphertext)` pairs. The client decrypts the
display names. The server response body no longer contains any plaintext tag values.

---

## Open questions resolved

### 1. Display name storage — per-tag globally (deduped by token) vs per (user, upload) tuple

**Decision: per (user, upload) tuple — parallel arrays on `uploads` and dedicated rows on `member_tags`.**

The alternative — a global `(user_id, tag_token, tag_display_ciphertext)` dedup table —
would store one ciphertext per unique tag per user, regardless of how many uploads share
that tag. This saves storage but introduces coupling: deleting the last upload with a
given tag would require garbage-collecting the global row; renaming a tag would require
a single-row update. More importantly, a global dedup table creates an implicit mapping
from token → ciphertext, which makes it marginally easier to correlate tag frequency
across uploads.

The per-(user, upload) tuple approach stores one ciphertext per tag application. For a
user with 500 uploads each carrying 5 tags, this is 2,500 ciphertext blobs of ~60 bytes
each = ~150 KB total — negligible. In practice, the parallel-array design on `uploads`
(not a per-row join table) keeps reads fast (one row per upload). The display ciphertext
is small enough that fetching it alongside the token adds no meaningful overhead.

**Verdict:** per-tuple. More consistent with the existing DEK-per-upload model, avoids
garbage-collection complexity, and does not create a cross-upload tag enumeration surface.

### 2. Migration UX — what does the user see during re-encryption?

**Decision: brief, non-blocking "Securing your tags..." indicator. See Phase 2 above.**

Rationale: migration is fast (< 2 seconds even for extreme cases), partial migration is
safe, and platforms do not need to coordinate. A silent background pass is acceptable
technically but risks user confusion if it stalls. A blocking screen is disproportionate
given the migration duration. A dismissible toast or banner is the appropriate level of
visibility.

### 3. Token versioning — should `tag_token_version` be stored alongside the token?

**Decision: yes — add `tag_token_version` as a column on `uploads` and a field on `member_tags`.**

**Rationale:** Without a version field, rotating the tag token scheme (e.g. upgrading from
HMAC-SHA256 to a future algorithm, or changing the HKDF context string) requires issuing
new tokens for every trellis criteria expression in the database and every upload's token
array simultaneously — a coordinated, all-or-nothing migration. With a version field, the
server can serve mixed-version tokens during a rolling migration: old uploads retain
`tag_token_version = 1` tokens; newly re-encrypted uploads use `tag_token_version = 2`.
Trellis criteria atoms can carry a version field too, allowing the evaluator to route to
the correct column.

The cost is one `SMALLINT` column per upload row and one field on `member_tags` rows —
negligible.

**Schema amendment:**

```sql
ALTER TABLE uploads
    ADD COLUMN tag_token_version  SMALLINT  NULL DEFAULT NULL;
    -- NULL = not yet migrated (uses legacy plaintext tags column)
    -- 1 = initial E2EE scheme (HMAC-SHA256 with "tag-token-v1" context)
```

```sql
-- Add to member_tags table definition (in V32):
    tag_token_version  SMALLINT  NOT NULL DEFAULT 1
```

**Trellis criteria token format (amended):**
```json
{ "type": "tag", "token": "4a7f2c...", "token_version": 1 }
```

`token_version` defaults to 1 if absent (backward compatibility during Phase 2). After
Phase 3 enforcement, `token_version` is required.

---

## Cross-platform implementation scope

### Android

**Files touched (design — no code written here):**

- `HeirloomsApp/app/src/main/kotlin/digital/heirlooms/crypto/VaultCrypto.kt`
  — add `hmacSha256(key: ByteArray, data: ByteArray): ByteArray`
  — add `deriveTagTokenKey(masterKey: ByteArray): ByteArray` (HKDF with `"tag-token-v1"`)
  — add `deriveTagDisplayKey(masterKey: ByteArray): ByteArray` (HKDF with `"tag-display-v1"`)
  — add `computeTagToken(tagValue: String, masterKey: ByteArray): ByteArray`
  — add `encryptTagDisplayName(tagValue: String, masterKey: ByteArray): ByteArray` (returns envelope)
  — add `decryptTagDisplayName(envelope: ByteArray, masterKey: ByteArray): String`

- Upload tag update flow: before calling the API, compute `tag_tokens` and `tag_display_ciphertexts`
  from the plaintext list using the new helpers. Send both arrays in the request body.

- Login flow: after `VaultSession.unlock()`, trigger background migration pass. Display
  `Snackbar` while in progress.

- Tag listing: `listAllTags()` response changes from `List<String>` to
  `List<Pair<ByteArray, ByteArray>>` (token, ciphertext). Decrypt display names in the
  UI layer using `decryptTagDisplayName()`.

**No new algorithm ID is required.** `aes256gcm-v1` covers tag display name encryption.

### Web

**Files touched:**

- `HeirloomsWeb/src/crypto/vaultCrypto.js`
  — add `async function hmacSha256(key, data)` using `crypto.subtle.sign`
  — add `async function computeTagToken(tagValue, masterKey)`
  — add `async function encryptTagDisplayName(tagValue, masterKey)`
  — add `async function decryptTagDisplayName(envelope, masterKey)`

- Upload tag update and listing flows: same as Android.

- Login flow: trigger migration pass with toast notification.

### iOS

**Files touched:**

- `HeirloomsiOS/Sources/HeirloomsCore/Crypto/EnvelopeCrypto.swift`
  — add `static func computeTagToken(tagValue: String, masterKey: SymmetricKey) -> Data`
  — add `static func encryptTagDisplayName(tagValue: String, masterKey: SymmetricKey) throws -> Data`
  — add `static func decryptTagDisplayName(envelope: Data, masterKey: SymmetricKey) throws -> String`

  iOS HKDF uses `CryptoKit.HKDF<SHA256>.deriveKey(inputKeyMaterial:salt:info:outputByteCount:)`.
  iOS HMAC uses `CryptoKit.HMAC<SHA256>.authenticationCode(for:using:)`.

- Login flow: run migration pass with status text in the unlock screen.

**iOS note:** iOS is currently a scaffold with `QRScannerView` as a stub (IOS-001 in queue).
Tag E2EE migration can be deferred until iOS has a working vault unlock flow. The server
will continue serving `tags TEXT[]` for iOS clients in Phase 2; iOS will be migrated in
Phase 3 before enforcement.

---

## Implementation order recommendation

1. **Server (Phase 1 schema migration):** Deploy `V32__e2ee_tags.sql` immediately. No client
   changes required. This is the only server-side change needed before client work begins.

2. **Android (Phase 2):** Implement crypto helpers in `VaultCrypto`, update upload tag flow,
   add background migration pass on login. Ship in the next Android release.

3. **Web (Phase 2):** Implement crypto helpers in `vaultCrypto.js`, update tag flow. Ship
   in the same web release or independently.

4. **iOS (Phase 2):** Implement crypto helpers in `EnvelopeCrypto`, update tag flow. Can
   lag Android/Web if the iOS vault unlock flow is not yet complete.

5. **Server (Phase 2):** Update `CriteriaEvaluator` to handle both `"tag"` (legacy) and
   `"token"` (new) criteria atoms. Update `listAllTags()` to return ciphertext blobs.
   Deploy before client Phase 2 releases go live.

6. **Server + All clients (Phase 3):** After enforcement date, remove plaintext column,
   enforce token-only criteria. Coordinate with a release that has reached minimum
   adoption threshold.

---

## What this does NOT solve (see SEC-012)

- **Token correlation:** The server can observe which uploads share a token and infer
  that they share a tag, even without knowing the tag value. This leaks cardinality and
  co-occurrence patterns. SEC-012 documents this as an accepted residual risk.

- **Trellis criteria leaks:** A trellis with a tag criteria atom leaks the token to the
  server when the trellis is created or updated. The token is stable across all uploads
  with that tag, so the server can correlate the trellis to all uploads bearing that tag.
  Accepted residual risk (SEC-012).

- **Tag count leaks:** The server knows how many tags each upload has (from the array length).
  This is a metadata leak that cannot be eliminated without padding; not addressed here.

- **Full-text search on tag values:** The scheme is intentionally not searchable beyond
  exact-match token comparison. Fuzzy or prefix search on tags is not possible server-side.
  If required in future, it must be implemented client-side after decryption.

- **Shared plot member tag visibility:** Member A's `member_tags` rows are invisible to
  Member B at the database level (rows are scoped to `user_id`). The server enforces this
  by only returning `member_tags` for the authenticated user. No cross-member tag leakage
  is possible at the schema level.

---

## PA Summary

ARCH-007 specifies an HMAC token scheme for E2EE tag storage. The core design is
unchanged from the task brief; this document resolves the three open questions and
adds full schema DDL, CriteriaEvaluator pseudocode, and a three-phase migration plan.

**Three open questions resolved:**

1. **Display name storage:** Per (user, upload) tuple (not deduped globally). Consistent
   with the DEK-per-upload model; avoids garbage-collection complexity; uses parallel BYTEA
   arrays on `uploads` and dedicated rows on `member_tags`.

2. **Migration UX:** Brief, non-blocking progress indicator ("Securing your tags..." toast/
   snackbar) during login-triggered background pass. Not silent (risk of unnoticed stall);
   not blocking (migration is < 2 seconds even at extreme scale). Platforms migrate
   independently; no cross-platform coordination required.

3. **Token versioning:** Yes — add `tag_token_version SMALLINT` to `uploads` and `member_tags`.
   Enables future key rotation without a coordinated all-or-nothing migration. Cost is one
   small column per row; benefit is a clean rotation path if the HMAC scheme changes.

**Implementation order:** Server Phase 1 schema (deploy now) → Android + Web Phase 2
(next client release) → iOS Phase 2 (when vault unlock is ready) → Server Phase 2
(CriteriaEvaluator dual-mode) → Phase 3 enforcement after 60-day grace period.

**No new envelope algorithm ID needed.** `aes256gcm-v1` covers tag display name encryption.

**SEC-012 residual risks:** token correlation, trellis token leakage, and tag count
leakage are accepted and documented in SEC-012. This brief does not address them.
