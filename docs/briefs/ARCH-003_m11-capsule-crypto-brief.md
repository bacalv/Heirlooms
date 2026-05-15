# ARCH-003 — M11 Capsule Cryptography Brief

*Authored: 2026-05-15. Status: approved. This document unlocks all M11 developer
tasks. Read it before writing any M11 crypto code.*

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
DEK or 32-byte master key. All shares must be the same length as the secret.

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
-- tlock_wrapped_key is the IBE ciphertext.
ALTER TABLE capsules
    ADD COLUMN tlock_round          BIGINT  NULL,
    ADD COLUMN tlock_chain_id       TEXT    NULL,    -- drand chain hash (hex string)
    ADD COLUMN tlock_wrapped_key    BYTEA   NULL;

-- Shamir fields. NULL on non-Shamir capsules.
-- These columns describe the split configuration; actual shares live in executor_shares.
ALTER TABLE capsules
    ADD COLUMN shamir_threshold     SMALLINT NULL CHECK (shamir_threshold >= 1),
    ADD COLUMN shamir_total_shares  SMALLINT NULL CHECK (shamir_total_shares >= 1),
    CONSTRAINT shamir_threshold_lte_total
        CHECK (shamir_threshold IS NULL OR shamir_threshold <= shamir_total_shares);

-- One wrapped DEK per recipient (primary recipient uses capsules.wrapped_capsule_key;
-- additional recipients use this table).
CREATE TABLE capsule_recipient_keys (
    capsule_id          UUID    NOT NULL REFERENCES capsules(id) ON DELETE CASCADE,
    connection_id       UUID    NOT NULL REFERENCES connections(id),
    wrapped_capsule_key BYTEA   NOT NULL,   -- capsule-ecdh-aes256gcm-v1 envelope
    capsule_key_format  TEXT    NOT NULL DEFAULT 'capsule-ecdh-aes256gcm-v1',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
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
| `wrapped_capsule_key` | Capsule is sealed and has at least one bound recipient |
| `capsule_key_format` | Same as `wrapped_capsule_key` |
| `tlock_round` | Capsule is sealed with a tlock gate |
| `tlock_chain_id` | Same as `tlock_round` |
| `tlock_wrapped_key` | Same as `tlock_round` |
| `shamir_threshold` | Capsule uses Shamir distribution |
| `shamir_total_shares` | Same as `shamir_threshold` |

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

## 6. iOS compatibility — sealed-without-tlock

tlock/BLS12-381 is Android and web only in M11. iOS cannot produce or consume
tlock material. However, iOS can still unseal any capsule addressed to an iOS user,
provided the capsule has the ECDH-wrapped path.

**Rule:** Every sealed capsule MUST have a `capsule-ecdh-aes256gcm-v1` wrapped DEK
for every bound recipient, regardless of whether tlock is also used. The tlock path
is an additional layer; it is never the only path.

**What a sealed-without-tlock capsule looks like (iOS-readable):**

```json
{
  "capsule_id": "...",
  "wrapped_capsule_key": "<base64url: capsule-ecdh-aes256gcm-v1 envelope>",
  "capsule_key_format": "capsule-ecdh-aes256gcm-v1",
  "tlock_round": null,
  "tlock_chain_id": null,
  "tlock_wrapped_key": null,
  "shamir_threshold": null,
  "shamir_total_shares": null
}
```

The iOS client unwraps `wrapped_capsule_key` using its sharing private key (P-256,
Keychain/Secure Enclave), recovers the capsule DEK, and decrypts the content. No
BLS12-381 or drand library required.

**What a sealed-with-tlock capsule looks like (Android/web readable, iOS reads via ECDH path):**

The tlock fields are populated, but `wrapped_capsule_key` and `capsule_key_format`
are ALSO populated for the recipient's ECDH path. iOS ignores the tlock fields and
uses the ECDH path. Android and web use tlock if it is available (round published),
falling back to ECDH if the client chooses (ECDH is always present and always valid
after unlock_at).

**Server enforcement:** Reject any sealing request where `tlock_round IS NOT NULL`
but `wrapped_capsule_key IS NULL`. The tlock path must always accompany an ECDH path.

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
  "recipient_keys": [
    {
      "connection_id": "<uuid>",
      // base64url of capsule-ecdh-aes256gcm-v1 asymmetric envelope
      "wrapped_capsule_key": "<base64url>",
      "capsule_key_format": "capsule-ecdh-aes256gcm-v1"
    }
  ],

  // tlock gate — omit entirely (or send null fields) for non-tlock capsules.
  "tlock": {
    "round": 1234567,                          // bigint
    "chain_id": "dbd506d6ef76e5f386f41c651dcb808c5bcbd75471cc4eafa3f4df7ad4e4c493",
    // base64url of the raw IBE ciphertext (not a Heirlooms envelope)
    "wrapped_key": "<base64url>"
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

**iOS:** Performs only the ECDH wrapping path. Sends `recipient_keys` with one entry,
omits the `tlock` field entirely, omits `shamir` unless configured. The sealing
request is valid — the server accepts it. iOS will only be used to seal capsules
without tlock in M11; this is a UX decision (the author uses Android or web for
capsules that need tlock), not a server constraint.

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
