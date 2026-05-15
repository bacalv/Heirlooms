# ARCH-003 — M11 Capsule Cryptography Brief

*Authored: 2026-05-15. Status: approved — amended 2026-05-15 (§9 blinding scheme
addendum). This document unlocks all M11 developer tasks. Read it before writing
any M11 crypto code.*

---

## What this document is

Milestone 11 makes "sealed" a cryptographic property. Before M11, sealing a capsule
sets a database flag; the content is encrypted under the author's own vault key and
the server could (if the vault key were compromised) read it. After M11, a sealed
capsule's content key is wrapped such that only the intended recipient(s) can unwrap
it. The author cannot undo this. Heirlooms cannot undo this.

This brief is the single agreed spec for all of that crypto work. No developer
should add, rename, or reinterpret any algorithm ID, schema column, or API field
without updating this document first.

Prerequisite reading:

- `docs/envelope_format.md` — binary wire format for all encrypted blobs
- `docs/briefs/ARCH-004_connections-data-model.md` — identity layer for recipients,
  executors, shareholders
- `docs/ROADMAP.md` M11 section

---

## 1. New envelope algorithm IDs

All three M11 algorithm IDs are reserved in `docs/envelope_format.md` (ARCH-005).
Their full semantics are defined here.

### `capsule-ecdh-aes256gcm-v1`

**Category:** Asymmetric envelope (same binary layout as `p256-ecdh-hkdf-aes256gcm-v1`).

**Purpose:** The per-capsule DEK wrapped to a specific recipient's P-256 sharing
pubkey at sealing time. After sealing, the wrapped DEK stored on the server can only
be unwrapped by the holder of the corresponding private key.

**Key derivation:** Identical to `p256-ecdh-hkdf-aes256gcm-v1`:
1. Generate ephemeral P-256 keypair.
2. ECDH(ephemeral_privkey, recipient_sharing_pubkey) → shared secret.
3. HKDF-SHA256(shared_secret, salt=[], info="capsule-ecdh-aes256gcm-v1") → 32-byte AES key.
4. AES-256-GCM encrypt the 32-byte capsule DEK.
5. Output: asymmetric envelope with `alg_id = "capsule-ecdh-aes256gcm-v1"`.

The only difference from `p256-ecdh-hkdf-aes256gcm-v1` is the algorithm ID string,
which makes auditing and algorithm selection unambiguous — a validator can assert
exactly which wrapping purpose was intended.

**Stored in:** `capsules.wrapped_capsule_key` (BYTEA). One row per capsule; the row
stores the wrap for the primary named recipient. Where multiple recipients exist, each
gets their own row in `capsule_recipient_keys` (see schema, §2).

### `shamir-share-v1`

**Category:** Symmetric envelope (`aes256gcm-v1` inner content, outer wrapper is this ID).

**Purpose:** A single Shamir Secret Sharing share of a capsule DEK or vault master
key, distributed to a nominated executor.

**Encoding:** The plaintext inside the envelope is a fixed 64-byte structure:

```
[2 bytes]  share_index    — big-endian uint16, 1-based (share 1 of N)
[2 bytes]  threshold      — big-endian uint16 (k of N scheme)
[2 bytes]  total_shares   — big-endian uint16
[26 bytes] reserved       — zeroed; reserved for future fields
[32 bytes] share_value    — the raw Shamir share byte string
```

The outer envelope uses `aes256gcm-v1` to protect the share in transit from server
to executor device; the outer key is derived via `capsule-ecdh-aes256gcm-v1` from
the executor's sharing pubkey. In practice the server stores the already-wrapped
share; it never sees the plaintext structure above.

**Shamir scheme:** GF(2^8) polynomial interpolation (standard Shamir over bytes,
matching the `tss` family of implementations). The secret is the raw 32-byte capsule
`DEK` or 32-byte master key. All shares must be the same length as the secret.

**Critical:** For tlock capsules, Shamir shares are computed over `DEK` (the full
capsule content key), not over `DEK_client` (the blinding mask). Executor recovery
reconstitutes `DEK` directly and does not require a call to `/tlock-key`. The
blinding scheme is a server-delivery mechanism, not a property of the Shamir path.

**Threshold:** Configurable per capsule. UX default is ⌈N/2⌉ + 1 (majority plus one)
for 2 ≤ N ≤ 5, and ⌈0.6 × N⌉ for N > 5. The server enforces `1 ≤ threshold ≤ total_shares`.

### `tlock-bls12381-v1`

**Category:** Special — not an AES-GCM envelope. The tlock ciphertext is a
BLS12-381-based IBE ciphertext; it does not use the standard Heirlooms binary frame.
Instead it is stored as an opaque BYTEA blob in `capsules.tlock_wrapped_key` alongside
its metadata columns. The algorithm ID `tlock-bls12381-v1` appears only in
`capsules.capsule_key_format` to identify the wrapping scheme, not as a byte prefix
inside the blob itself.

**Purpose:** The per-capsule DEK time-locked to a specific drand round. Until the
round publishes its randomness, no party — author, recipient, Heirlooms, drand — can
decrypt. After the round publishes, anyone with the ciphertext and the public beacon
chain can decrypt.

**Scheme:** drand tlock (https://drand.love/docs/tlock/). The ciphertext is an IBE
ciphertext under the BLS12-381 public key of the drand chain identified by
`capsules.tlock_chain_id`. The round is `capsules.tlock_round`.

**Android/web only in M11.** iOS does not have a Go-based BLS12-381 library available
at M11 scope. iOS clients must not attempt to produce or consume `tlock-bls12381-v1`
material. See §6 for the iOS compatibility guarantee.

---

## 2. New schema columns on `capsules`

```sql
-- V32__m11_capsule_crypto.sql

-- Per-capsule DEK, generated at creation time and re-wrapped at sealing.
-- Stored as a capsule-ecdh-aes256gcm-v1 asymmetric envelope (primary recipient).
-- NULL until the capsule is sealed.
ALTER TABLE capsules
    ADD COLUMN wrapped_capsule_key  BYTEA   NULL,
    -- Algorithm ID of the wrapping scheme. Currently always 'capsule-ecdh-aes256gcm-v1'
    -- when non-NULL. Stored explicitly to support future algorithm migration.
    ADD COLUMN capsule_key_format   TEXT    NULL
        CHECK (capsule_key_format IN ('capsule-ecdh-aes256gcm-v1', 'tlock-bls12381-v1'));
        -- Note: a capsule sealed with tlock stores the tlock blob in tlock_wrapped_key;
        -- capsule_key_format then reflects the tlock wrapping, and wrapped_capsule_key
        -- holds the recipient-ECDH wrap separately. See §3 for the multi-path model.

-- tlock fields. NULL on all non-tlock capsules.
-- tlock_round and tlock_chain_id identify which drand round gates the key.
-- tlock_wrapped_key is the IBE ciphertext. Under the blinding scheme (§9), the
-- IBE ciphertext encrypts DEK_client (the client-side mask), not the full DEK.
-- tlock_dek_tlock = DEK XOR DEK_client, stored at sealing time, served via /tlock-key.
-- tlock_key_digest = SHA-256(DEK_tlock) stored at sealing time for tamper detection.
-- SECURITY: tlock_dek_tlock MUST NEVER appear in application logs, access logs,
-- or request traces. See §6.5 and ARCH-006 §6.2 for the full logging prohibition.
ALTER TABLE capsules
    ADD COLUMN tlock_round          BIGINT  NULL,
    ADD COLUMN tlock_chain_id       TEXT    NULL,    -- drand chain hash (hex string)
    ADD COLUMN tlock_wrapped_key    BYTEA   NULL,    -- IBE-encrypt(DEK_client)
    ADD COLUMN tlock_dek_tlock      BYTEA   NULL,    -- DEK XOR DEK_client; delivered via /tlock-key
    ADD COLUMN tlock_key_digest     BYTEA   NULL;    -- SHA-256(DEK_tlock); tamper detection

-- Shamir fields. NULL on non-Shamir capsules.
-- These columns describe the split configuration; actual shares live in executor_shares.
ALTER TABLE capsules
    ADD COLUMN shamir_threshold     SMALLINT NULL CHECK (shamir_threshold >= 1),
    ADD COLUMN shamir_total_shares  SMALLINT NULL CHECK (shamir_total_shares >= 1),
    CONSTRAINT shamir_threshold_lte_total
        CHECK (shamir_threshold IS NULL OR shamir_threshold <= shamir_total_shares);

-- One wrapped DEK per recipient (primary recipient uses capsules.wrapped_capsule_key;
-- additional recipients use this table).
-- For tlock capsules, wrapped_blinding_mask holds ECDH-wrap(DEK_client) for the
-- Android/web blinded delivery path. See §9 for the full blinding scheme.
CREATE TABLE capsule_recipient_keys (
    capsule_id              UUID    NOT NULL REFERENCES capsules(id) ON DELETE CASCADE,
    connection_id           UUID    NOT NULL REFERENCES connections(id),
    wrapped_capsule_key     BYTEA   NOT NULL,   -- capsule-ecdh-aes256gcm-v1 envelope wrapping DEK
    capsule_key_format      TEXT    NOT NULL DEFAULT 'capsule-ecdh-aes256gcm-v1',
    -- wrapped_blinding_mask: ECDH-wrap(DEK_client) — non-NULL only on tlock capsules.
    -- Used by Android/web to recover DEK_client and complete blinded decryption.
    -- NULL on non-tlock capsules; iOS never reads this field.
    wrapped_blinding_mask   BYTEA   NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (capsule_id, connection_id)
);

-- Shamir shares distributed to executors.
-- Each row is a shamir-share-v1 envelope wrapped to the executor's sharing pubkey.
CREATE TABLE executor_shares (
    id                  UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    capsule_id          UUID    NULL REFERENCES capsules(id) ON DELETE CASCADE,
    -- NULL capsule_id means a master-key share (vault recovery), not a capsule-key share.
    nomination_id       UUID    NOT NULL REFERENCES executor_nominations(id),
    share_index         SMALLINT NOT NULL,          -- 1-based
    wrapped_share       BYTEA   NOT NULL,           -- shamir-share-v1 envelope
    share_format        TEXT    NOT NULL DEFAULT 'shamir-share-v1',
    distributed_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_executor_shares_capsule ON executor_shares(capsule_id)
    WHERE capsule_id IS NOT NULL;
CREATE INDEX idx_executor_shares_nomination ON executor_shares(nomination_id);
```

**Nullability rules:**

| Column | When non-NULL |
|---|---|
| `wrapped_capsule_key` | Capsule is sealed and has at least one bound recipient. Always wraps `DEK` (not `DEK_client`) for iOS compatibility. |
| `capsule_key_format` | Same as `wrapped_capsule_key` |
| `tlock_round` | Capsule is sealed with a tlock gate |
| `tlock_chain_id` | Same as `tlock_round` |
| `tlock_wrapped_key` | Same as `tlock_round`. IBE ciphertext wraps `DEK_client` under the blinding scheme (see §9). |
| `tlock_dek_tlock` | Same as `tlock_round`. The server-held complement (`DEK XOR DEK_client`); served via `/tlock-key`. NEVER logged. |
| `tlock_key_digest` | Same as `tlock_round`. SHA-256(`DEK_tlock`) for tamper detection. |
| `shamir_threshold` | Capsule uses Shamir distribution |
| `shamir_total_shares` | Same as `shamir_threshold` |

For `capsule_recipient_keys`:

| Column | When non-NULL |
|---|---|
| `wrapped_capsule_key` | Always — every row wraps `DEK` (for iOS compatibility on tlock capsules, and the only field used on non-tlock capsules) |
| `wrapped_blinding_mask` | Tlock capsules only — wraps `DEK_client` for the Android/web blinded path (see §9) |

---

## 3. Connections model for capsule recipients

See `docs/briefs/ARCH-004_connections-data-model.md` for the full design. Summary
of what matters for sealing:

**Bound recipient** — has `connections.sharing_pubkey` populated. The client wraps
the capsule DEK to this pubkey at sealing time using `capsule-ecdh-aes256gcm-v1`.
The wrapped blob goes into `capsules.wrapped_capsule_key` (primary recipient) or
`capsule_recipient_keys` (additional recipients).

**Deferred-pubkey recipient** — `connections.sharing_pubkey IS NULL`. The server
rejects a seal request for a capsule whose *only* unlock path is a deferred-pubkey
recipient. The capsule must have a tlock gate or ≥1 accepted executor Shamir share
as a concurrent path. This is enforced at sealing time, not at recipient-addition
time.

**Sealing validation (server-side):**
```
let has_tlock       = tlock_round IS NOT NULL
let has_executor    = COUNT(accepted executor_nominations for this capsule) >= threshold
let all_bound       = all capsule_recipients have a non-null connection_id
                      AND all connections have sharing_pubkey IS NOT NULL
REJECT seal if: NOT all_bound AND NOT (has_tlock OR has_executor)
```

---

## 4. Server validation at sealing vs. opaque content

### What the server validates at sealing time

The server validates structure and consistency; it never decrypts content.

**Always validated:**
- `wrapped_capsule_key` is a syntactically valid asymmetric envelope with
  `alg_id = "capsule-ecdh-aes256gcm-v1"` (uses `EnvelopeFormat.validateAsymmetric`).
- All `capsule_recipient_keys` rows have valid `capsule-ecdh-aes256gcm-v1` envelopes.
- If `tlock_round` is set: `tlock_chain_id` is a known drand chain hash, and
  `tlock_round` is in the future relative to `capsules.unlock_at` (see §5).
  `tlock_wrapped_key` is non-null and non-empty (content is opaque).
- If Shamir columns are set: `shamir_threshold` and `shamir_total_shares` are
  consistent, and the number of `executor_shares` rows matches `shamir_total_shares`.
  Each `executor_shares.wrapped_share` is a valid symmetric envelope with
  `alg_id = "shamir-share-v1"`.
- The multi-path fallback rule (§3 above) is satisfied.
- The capsule state machine: `shape = 'open'` is a prerequisite for sealing; after
  sealing, `shape = 'sealed'`.

**Opaque to the server (never inspected):**
- The plaintext capsule DEK (never sent to the server at all).
- The plaintext content of any upload DEK.
- The ephemeral ECDH pubkeys inside wrapped envelopes (present in the binary but
  the server does not interpret them — it only validates structural presence).
- The share_value bytes inside `shamir-share-v1` envelopes.
- The IBE ciphertext inside `tlock_wrapped_key`.

---

## 5. `unlock_at` and tlock — the relationship

`capsules.unlock_at` is the **canonical delivery gate** for all capsules. It is set
by the author and controls when the server surfaces the capsule to the delivery
system. It is not encrypted; the server uses it for scheduling.

For tlock-sealed capsules, `tlock_round` is chosen by the client to correspond to
the drand round that will publish **at or before** `unlock_at`. The client:
1. Looks up the drand chain's genesis time and period.
2. Computes `round = ceil((unlock_at - genesis_time) / period)`.
3. Submits both `unlock_at` and `tlock_round` to the server.

The server validates that the round's expected publish time ≤ `unlock_at + 1 hour`
(a tolerance to allow for chain timing variance). If the drand chain publishes later
than expected (chain incident), the tlock gate delays; `unlock_at` does not override
the cryptographic gate — the recipient cannot decrypt until the randomness is
published regardless of what `unlock_at` says.

**The relationship is:** `unlock_at` is the scheduling gate (server acts); tlock
round is the cryptographic gate (client decrypts). They are expected to align; if
they diverge due to a drand incident, the cryptographic gate wins for decryption
but the server-side delivery notification still fires at `unlock_at`. The recipient
sees the notification, opens the capsule, and gets a "cryptographic gate not yet
open — please try again" message until the round publishes.

For non-tlock capsules, `unlock_at` remains the sole gate. The recipient's ECDH-
wrapped DEK is immediately usable after unlock; the server simply withholds the
capsule from the delivery query until `now() >= unlock_at`.

---

## 6. iOS compatibility — blinding scheme and dual ECDH fields for tlock capsules

tlock/BLS12-381 is Android and web only in M11. iOS cannot produce or consume
tlock material. However, iOS can still unseal any capsule addressed to an iOS user,
provided the capsule has the ECDH-wrapped path for the full `DEK`.

### 6.1 The blinding scheme (tlock capsules only)

For tlock-sealed capsules, the M11 design uses a *blinding scheme* to ensure the
server never sees the capsule `DEK` even while brokering key delivery after the
tlock round publishes:

```
DEK          — the 32-byte capsule content key (generated by the sealing client)
DEK_client   — a 32-byte random mask generated by the sealing client
DEK_tlock    = DEK XOR DEK_client   — the server-side component
```

At sealing time the client:
1. Generates `DEK` (the real content key).
2. Generates `DEK_client` (random 32 bytes — the client-held mask).
3. Computes `DEK_tlock = DEK XOR DEK_client`.
4. IBE-encrypts `DEK_client` (not `DEK`) under the tlock public key → stored in
   `capsules.tlock_wrapped_key`.
5. Sends `DEK_tlock` (32 bytes) in the sealing request as `tlock.dek_tlock` → server
   stores it in `capsules.tlock_dek_tlock` (delivered to Android/web via `/tlock-key`).
6. Sends `tlock_key_digest = SHA-256(DEK_tlock)` → stored in `capsules.tlock_key_digest`
   for tamper detection at delivery time.
7. ECDH-wraps `DEK` → `wrapped_capsule_key` (iOS-compatible direct path).
8. ECDH-wraps `DEK_client` → `wrapped_blinding_mask` (Android/web blinded path).

After the tlock round publishes, the server uses `provider.decrypt()` solely to confirm
the IBE gate is open (non-null return = round is live). It then serves the already-stored
`capsules.tlock_dek_tlock` value via `/tlock-key`. See §6.2 for the full delivery flow.

### 6.2 Decryption path by platform

**iOS (no BLS12-381 capability):**
1. Receives `wrapped_capsule_key` from the server.
2. ECDH-unwraps with its sharing private key → recovers `DEK`.
3. Decrypts content. Done. No `/tlock-key` call, no BLS12-381, no XOR.

**Android / web (full tlock path):**
1. Receives `wrapped_blinding_mask` from the server.
2. ECDH-unwraps with its sharing private key → recovers `DEK_client`.
3. Calls `GET /api/capsules/:id/tlock-key` (authenticated) to obtain `DEK_tlock`.
   The server has recovered `DEK_tlock` via `TimeLockProvider.decrypt()` and returns
   it only after the tlock round has published and `unlock_at` has passed.
4. Computes `DEK = DEK_client XOR DEK_tlock`.
5. Decrypts content.

**Security property:** The server holds `DEK_tlock` (the XOR complement) but never
`DEK_client` (the client mask) — that is recovered by the client from its own
ECDH-wrapped copy. Neither party alone holds `DEK`; reconstruction requires both
components. This is the blinding guarantee.

**Shamir recovery path:** Shamir shares are always computed over `DEK` (the full
content key), not over `DEK_client`. Executor devices that reconstruct the key via
Shamir do not need to call `/tlock-key` and do not interact with the blinding scheme.
See §7.

### 6.3 iOS contradiction resolution — Option A (chosen)

A contradiction exists between the blinding scheme and iOS compatibility: if
`wrapped_capsule_key` wrapped `DEK_client` (the mask), iOS would only recover the
mask and could not decrypt content. Three options were considered:

- **Option A (chosen):** Store two ECDH-wrapped values per recipient on tlock capsules.
  `wrapped_capsule_key` always wraps `DEK` (preserving iOS compatibility). A new
  `wrapped_blinding_mask` field in `capsule_recipient_keys` wraps `DEK_client` for
  the Android/web blinded path. iOS uses `wrapped_capsule_key` and ignores
  `wrapped_blinding_mask`. Android/web use the blinded path.
- **Option B (rejected):** Exclude iOS recipients from tlock capsules. Too restrictive —
  iOS users could not receive tlock-sealed content at all.
- **Option C (rejected):** Let the sealing client choose the variant based on recipient
  device types. Creates a split-field interpretation that is hard to validate and audit.

**Option A rationale:** iOS gets the existing non-blinded path (which matches its
current behaviour and security model). The blinding security improvement applies to
Android and web. All platforms can receive the same tlock-sealed capsule without any
capability negotiation.

### 6.4 Schema shape for tlock capsules (all platforms)

**Non-tlock capsule (all platforms including iOS):**

```json
{
  "wrapped_capsule_key": "<base64url: ECDH-wrap(DEK)>",
  "capsule_key_format": "capsule-ecdh-aes256gcm-v1",
  "tlock_round": null,
  "tlock_chain_id": null,
  "tlock_wrapped_key": null,
  "tlock_key_digest": null
}
```

**Tlock capsule (iOS uses `wrapped_capsule_key`; Android/web use `wrapped_blinding_mask`):**

```json
{
  "wrapped_capsule_key": "<base64url: ECDH-wrap(DEK)>",
  "capsule_key_format": "capsule-ecdh-aes256gcm-v1",
  "tlock_round": 1234567,
  "tlock_chain_id": "dbd506d6...",
  "tlock_wrapped_key": "<base64url: IBE-encrypt(DEK_client)>",
  "tlock_key_digest": "<base64url: SHA-256(DEK_tlock)>"
}
```

`wrapped_blinding_mask` lives in `capsule_recipient_keys` (one row per recipient):

```json
{
  "connection_id": "<uuid>",
  "wrapped_capsule_key": "<base64url: ECDH-wrap(DEK)>",
  "wrapped_blinding_mask": "<base64url: ECDH-wrap(DEK_client)>"
}
```

### 6.5 `/tlock-key` endpoint security requirements

The `/tlock-key` response delivers `DEK_tlock` to authenticated Android/web clients.

**Logging requirement (CRITICAL):** The `/tlock-key` response body MUST NEVER be
written to application logs, access logs, or request-tracing systems. The body
contains a 32-byte value that — combined with the client's ECDH-wrapped mask — gives
the capsule `DEK`. Log the request (capsule ID, caller user ID, status code, latency)
but redact or omit the response body entirely. This applies to all logging middleware,
request-interceptors, and any distributed tracing instrumentation.

**Gate requirements:** The server MUST NOT return `DEK_tlock` until:
1. The tlock round has published (confirmed via `TimeLockProvider.decrypt()` returning
   non-null), AND
2. `now() >= capsules.unlock_at`.

**Server enforcement:** Reject any sealing request where `tlock_round IS NOT NULL`
but `wrapped_capsule_key IS NULL` (the iOS-compatible full-DEK wrap must always be
present). Also reject if `wrapped_blinding_mask IS NULL` for any recipient on a tlock
capsule (all recipients need both wraps).

**Tamper detection:** At delivery time the server SHOULD verify
`SHA-256(DEK_tlock) == capsules.tlock_key_digest` before returning `DEK_tlock`.
A mismatch indicates data corruption or tampering and should be treated as a server
error (HTTP 500), with the event logged at ERROR level including the capsule ID.

---

## 7. Shamir share encoding format and distribution API

### Encoding

See §1 (`shamir-share-v1`) for the 64-byte plaintext structure. The outer envelope
is an AES-256-GCM symmetric encryption of that structure, where the encryption key
is the `capsule DEK` or `master key` — effectively making each share an envelope
within an envelope. The outer wrap uses `aes256gcm-v1` with the executor's ECDH-
unwrapped key.

In practice, the full flow is:

1. Client computes N Shamir shares of the 32-byte capsule DEK.
2. For each executor (accepted nomination):
   a. Wrap the 64-byte share structure under the executor's sharing pubkey
      using `capsule-ecdh-aes256gcm-v1`.
   b. Upload to `POST /api/capsules/:id/executor-shares` (see API below).

The server stores the opaque wrapped blob. The executor device, on request,
fetches the blob, unwraps with its sharing private key, and reads the 64-byte
plaintext. Threshold reconstruction runs entirely on executor devices; the server
is never involved in share assembly.

### Distribution API

```
POST /api/capsules/:id/executor-shares
Body: {
  "shares": [
    {
      "nomination_id": "<uuid>",
      "share_index": 1,
      "wrapped_share": "<base64url of capsule-ecdh-aes256gcm-v1 envelope>",
      "share_format": "shamir-share-v1"
    },
    ...
  ]
}
Response: 200 OK
```

Constraints:
- The requesting user must be the capsule owner.
- `nomination_id` must reference an `executor_nominations` row with `status = 'accepted'`
  for the same `owner_user_id`.
- `shares` must have exactly `shamir_total_shares` entries (set in the sealing request).
- `share_index` values must be unique, 1-based, and cover 1..N without gaps.
- Each `wrapped_share` must be a syntactically valid asymmetric envelope with
  `alg_id = "capsule-ecdh-aes256gcm-v1"` (structural validation only).

```
GET /api/capsules/:id/executor-shares/mine
Response: {
  "share": {
    "wrapped_share": "<base64url>",
    "share_format": "shamir-share-v1",
    "share_index": 2,
    "capsule_id": "<uuid>"
  }
}
```

- Requires the caller to be an accepted executor for this capsule.
- Returns the single share assigned to this executor. Only one share per executor.

```
GET /api/capsules/:id/executor-shares/collect
Response: {
  "shares": [
    { "share_index": 1, "wrapped_share": "..." },
    { "share_index": 3, "wrapped_share": "..." }
  ],
  "threshold": 3,
  "total": 5
}
```

- Requires a quorum signal: caller must prove they have the right to collect
  (authenticated as the capsule author, OR authenticated as an executor who has
  already submitted their own share + a death-verification token). In M11, only
  the author-authenticated path is implemented (for testing). Death-verification-
  gated collection is M13 work.

---

## 8. Cross-platform API contract — sealing request body

All platforms MUST send exactly this JSON body to seal a capsule:

```
PUT /api/capsules/:id/seal
Authorization: Bearer <token>
Content-Type: application/json

{
  // Per-recipient wrapped DEKs. At least one entry required.
  // Primary recipient ALSO stored in capsules.wrapped_capsule_key.
  // For tlock capsules, wrapped_blinding_mask must ALSO be present for each recipient
  // (ECDH-wrap(DEK_client) — used by Android/web for the blinded delivery path).
  // For non-tlock capsules, wrapped_blinding_mask is omitted or null.
  "recipient_keys": [
    {
      "connection_id": "<uuid>",
      // base64url of capsule-ecdh-aes256gcm-v1 asymmetric envelope wrapping DEK.
      // Always present. Used by iOS directly; also present for non-blinded fallback.
      "wrapped_capsule_key": "<base64url>",
      "capsule_key_format": "capsule-ecdh-aes256gcm-v1",
      // base64url of capsule-ecdh-aes256gcm-v1 asymmetric envelope wrapping DEK_client.
      // Required for tlock capsules; omit or null for non-tlock capsules.
      "wrapped_blinding_mask": "<base64url or null>"
    }
  ],

  // tlock gate — omit entirely (or send null fields) for non-tlock capsules.
  // wrapped_key is IBE-encrypt(DEK_client) — NOT IBE-encrypt(DEK) (see §6.1).
  // dek_tlock = DEK XOR DEK_client — the server-held complement, delivered via /tlock-key.
  // tlock_key_digest = SHA-256(dek_tlock); server validates SHA-256(dek_tlock) == tlock_key_digest
  //   at sealing time. dek_tlock is key material — subject to the same logging prohibition
  //   as the /tlock-key response body (see §6.5).
  "tlock": {
    "round": 1234567,                          // bigint
    "chain_id": "dbd506d6ef76e5f386f41c651dcb808c5bcbd75471cc4eafa3f4df7ad4e4c493",
    // base64url of the raw IBE ciphertext (not a Heirlooms envelope); encrypts DEK_client
    "wrapped_key": "<base64url>",
    // base64url of 32-byte DEK_tlock = DEK XOR DEK_client; stored in capsules.tlock_dek_tlock
    "dek_tlock": "<base64url>",
    // base64url of SHA-256(DEK_tlock); stored for tamper detection at delivery time
    "tlock_key_digest": "<base64url>"
  },                                            // or null / absent

  // Shamir configuration — omit entirely for non-Shamir capsules.
  "shamir": {
    "threshold": 3,
    "total_shares": 5
    // Actual shares are submitted separately via POST /executor-shares
    // after the sealing call succeeds.
  }                                             // or null / absent
}
```

**Server response on success:**

```json
{
  "capsule_id": "<uuid>",
  "shape": "sealed",
  "state": "sealed",
  "sealed_at": "2026-05-15T10:00:00Z"
}
```

**Server response on validation failure (HTTP 422):**

```json
{
  "error": "sealing_validation_failed",
  "detail": "one or more recipients lack a sharing pubkey and no tlock or executor fallback is configured"
}
```

### Platform notes

**Android:** Generates the capsule DEK in `VaultCrypto`, performs all wrapping ops
using `EnvelopeFormat`, serialises to the JSON body above. Full tlock support via
the drand Go SDK (JNI bridge or a pure-Kotlin port — implementation choice for the
developer task, not specified here).

**Web:** Same flow in TypeScript using WebCrypto for ECDH and a drand JS client for
tlock. The capsule DEK must be non-extractable during its lifetime in the browser —
extract to bytes only at the instant of wrapping.

**iOS:** Performs only the ECDH wrapping path. Sends `recipient_keys` with one entry
(only `wrapped_capsule_key`, no `wrapped_blinding_mask`), omits the `tlock` field
entirely, omits `shamir` unless configured. The sealing request is valid — the server
accepts it. iOS will only be used to seal capsules without tlock in M11; this is a
UX decision (the author uses Android or web for capsules that need tlock), not a
server constraint.

When iOS *receives* a tlock-sealed capsule (sealed by Android/web), it reads
`wrapped_capsule_key` (which always wraps `DEK`) and ignores `wrapped_blinding_mask`
and the tlock fields entirely. No `/tlock-key` call, no BLS12-381. See §6.

---

## Dependency chain

```
ARCH-005 (algorithm IDs)
  └── ARCH-004 (connections model)
       └── ARCH-003 (this document)
            ├── M11 developer tasks: server schema migration (V32)
            ├── M11 developer tasks: sealing endpoint
            ├── M11 developer tasks: Android tlock integration
            ├── M11 developer tasks: web tlock integration
            ├── M11 developer tasks: executor share distribution
            └── M11 developer tasks: iOS sealing path (ECDH-only)
```

No M11 developer task should begin crypto implementation without reading this brief.
Changes to algorithm IDs, field names, or the sealing request shape require updating
this document and notifying all platform developers before code is written.

---

## 9. Security addendum — blinding scheme (added 2026-05-15)

This section consolidates the blinding scheme design following Security Manager review.
It supersedes any implicit assumptions in §§1–8 about what `tlock_wrapped_key` encrypts.

### 9.1 Summary of the blinding scheme

For tlock-sealed capsules, the IBE ciphertext in `tlock_wrapped_key` encrypts
`DEK_client` (a random 32-byte mask), not the full capsule `DEK`. The complement
`DEK_tlock = DEK XOR DEK_client` is what the server derives and delivers via
`/tlock-key` after the round publishes. Neither the server nor drand ever holds `DEK`
directly — the server holds only `DEK_tlock`, and the client holds only `DEK_client`.
Reconstruction of `DEK` requires both components.

For non-tlock capsules, there is no blinding scheme. `wrapped_capsule_key` wraps `DEK`
directly and the above does not apply.

### 9.2 Key assignments per scheme

| Field / path | Non-tlock capsule | Tlock capsule |
|---|---|---|
| `wrapped_capsule_key` | ECDH-wrap(`DEK`) | ECDH-wrap(`DEK`) — iOS path |
| `wrapped_blinding_mask` | absent | ECDH-wrap(`DEK_client`) — Android/web path |
| `tlock_wrapped_key` | absent | IBE-encrypt(`DEK_client`) |
| `tlock_dek_tlock` | absent | `DEK XOR DEK_client` (server-stored, never logged) |
| `tlock_key_digest` | absent | SHA-256(`DEK_tlock`) |
| Shamir shares | Shares of `DEK` | Shares of `DEK` (not `DEK_client`) |
| `/tlock-key` response | N/A | Returns `DEK_tlock` |

### 9.3 Invariants for implementors

1. `wrapped_capsule_key` ALWAYS wraps `DEK` — no exceptions. This is the iOS
   compatibility guarantee.
2. `wrapped_blinding_mask` is ONLY present on tlock capsules and ALWAYS wraps
   `DEK_client`.
3. Shamir shares are computed over `DEK` regardless of whether tlock is used.
4. The `/tlock-key` response body and `capsules.tlock_dek_tlock` MUST NEVER appear in
   any log, trace, or metric. Log the request metadata (capsule ID, caller, latency,
   status) but not the body. This prohibition also applies to `dek_tlock` in the sealing
   request body — log that a tlock sealing request was received, not its key material.
5. `tlock_key_digest = SHA-256(DEK_tlock)` is stored at sealing time and verified
   at delivery time. A mismatch is a hard server error (HTTP 500, log at ERROR).
6. The server MUST NOT return `DEK_tlock` via `/tlock-key` until both:
   - `TimeLockProvider.decrypt()` returns non-null (round has published), AND
   - `now() >= capsules.unlock_at`.

### 9.4 iOS compatibility guarantee (restated)

iOS clients in M11 and beyond can receive any tlock-sealed capsule sent by Android
or web, because `wrapped_capsule_key` always holds ECDH-wrap(`DEK`). iOS does not
need BLS12-381, does not call `/tlock-key`, and does not participate in the blinding
scheme. The blinding scheme is transparent to iOS.

### 9.5 Why the blinding scheme matters

Without blinding, `tlock_wrapped_key` would encrypt the full `DEK`. After the round
publishes, the server could call `TimeLockProvider.decrypt()` and obtain `DEK` — which
means a server compromise after unlock exposes all capsule content. The blinding scheme
ensures that even a fully compromised server post-unlock cannot decrypt capsule content
without also compromising the client's ECDH private key (which gives `DEK_client`).
