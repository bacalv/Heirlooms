# ARCH-009 — Executor Revocation and Key Rotation

*Authored: 2026-05-16. Status: approved for M11 implementation.*
*Depends on: ARCH-003, ARCH-004, ARCH-006.*

---

## What this document is

ARCH-004 §4 specifies the executor nomination lifecycle (`pending → accepted → revoked`)
and explicitly defers key rotation: "Key rotation on revocation is non-trivial and is
tracked as a separate M11 task."

This brief fills that gap. It specifies:

1. The exact re-split and re-distribution flow when an executor nomination is revoked.
2. The server's role during rotation (relay vs. orchestrator).
3. Atomicity and failure recovery (nominator goes offline mid-rotation).
4. Interaction with SEC-011 device revocation — compatibility ruling and unified model.

Prerequisite reading:

- `docs/briefs/ARCH-003_m11-capsule-crypto-brief.md` — Shamir share encoding, sealing API, blinding scheme.
- `docs/briefs/ARCH-004_connections-data-model.md` — executor nomination lifecycle, `executor_nominations`, `executor_shares` schema.
- `docs/briefs/ARCH-006_tlock-provider-interface.md` — DEK blinding scheme; Shamir shares are over `DEK`, not `DEK_client`.

---

## 1. Re-split and re-distribution flow

### 1.1 What "revocation" means cryptographically

An accepted executor holds one wrapped Shamir share stored in `executor_shares`. The
share is wrapped to the executor's sharing pubkey (`capsule-ecdh-aes256gcm-v1`). The
server stores the opaque blob; it never sees the plaintext share value.

When the nominator revokes an accepted executor, the existing share must be
invalidated. There are two possible strategies:

| Strategy | Summary | Verdict |
|---|---|---|
| **Share deletion only** | Delete the `executor_shares` row. The executor loses access to the blob but Shamir still considers them a shareholder because the capsule's `shamir_total_shares` count is unchanged. If any other `threshold − 1` executors collude with the revoked one (if they retain a copy), interpolation is still possible in theory. | Rejected — leaves the old split integrity invalid. |
| **Full re-split** | Generate a new set of N' Shamir shares over the same `DEK`, distribute to the remaining accepted executors, and update `shamir_threshold`/`shamir_total_shares`. The revoked executor's old share is deleted server-side and is cryptographically useless — the new shares are over an independent polynomial. | **Adopted.** |

**Decision: re-split is mandatory on revocation of an accepted executor.**

The full re-split is required because Shamir Secret Sharing guarantees are defined
over a specific polynomial. A revoked executor's share was a point on the *old*
polynomial. Once the nominator generates a new polynomial over the same `DEK` and
distributes new shares, the old share is useless — it cannot contribute to
interpolation of the new polynomial regardless of whether the revoked executor
retained a local copy.

### 1.2 What "re-split" does NOT change

- The capsule `DEK` itself. The secret being shared is the same. Re-splitting generates
  a new random polynomial whose y-intercept (the secret) is still `DEK`. Content does
  not need to be re-encrypted.
- The `wrapped_capsule_key` / `wrapped_blinding_mask` per-recipient fields. These are
  independent of the Shamir distribution.
- The `tlock_wrapped_key` or `tlock_dek_tlock` fields. The blinding scheme is a
  delivery mechanism, not a property of the Shamir path (ARCH-003 §9.2).
- Master-key shares (`executor_shares` rows where `capsule_id IS NULL`). These are
  governed by a separate re-split flow that is not M11 scope (see §7).

### 1.3 Step-by-step revocation flow

#### Phase 1 — Nominator revokes (client-side)

1. Nominator taps "Revoke" on executor X in the Executors screen.
2. The nominator's device knows:
   - The capsule `DEK` (held in memory from the last vault unlock; the device has
     `wrapped_capsule_key` and its sharing private key to recover it).
   - The current `shamir_threshold` (k) and `shamir_total_shares` (N).
   - The set of remaining accepted executors (from `GET /api/executor-nominations?capsule=:id`).
3. Compute new parameters:
   - `N' = N − 1` (one executor removed).
   - `k' = k` unless `k > N'`, in which case `k' = N'` (must not exceed total shares).
   - If `N' = 0` or `k' = 0`: no Shamir distribution is possible — the capsule loses
     executor recovery path. See §1.5 for the guard.
4. Generate a new Shamir split over `DEK` with parameters `(k', N')` using a fresh
   random polynomial. This runs entirely on the nominator's device; the server is not
   involved.
5. For each remaining accepted executor i (i = 1 … N'):
   - Wrap share i to executor i's sharing pubkey using `capsule-ecdh-aes256gcm-v1`.
   - Produce a new `executor_shares` row payload.

#### Phase 2 — Atomic server update

The nominator sends a single `POST /api/capsules/:id/executor-shares/rotate` request
(see §2 for the full API spec). The server performs the following operations in a single
database transaction:

1. Set `executor_nominations.status = 'revoked'` and `revoked_at = NOW()` for the
   revoked executor's nomination row.
2. Delete ALL existing `executor_shares` rows for this capsule (both the revoked
   executor's share and all remaining executors' old shares — they are now on the old
   polynomial and must be replaced).
3. Insert the new `executor_shares` rows (one per remaining executor, new polynomial).
4. Update `capsules.shamir_threshold = k'` and `capsules.shamir_total_shares = N'`.

Because all four operations are within one transaction, the server never holds an
inconsistent state. If the request fails partway, the transaction rolls back and the
old shares remain valid.

#### Phase 3 — Confirmation

The server returns 200 with the updated Shamir parameters. The nominator's device
presents a success state. The revoked executor's nomination is now `status = 'revoked'`.

### 1.4 Executor re-split for capsules vs. vault master key

This brief covers per-capsule Shamir shares (`executor_shares.capsule_id IS NOT NULL`).

Vault master key shares (`executor_shares.capsule_id IS NULL`) involve re-wrapping the
master key — which requires the nominator's vault to be unlocked and re-split. This is
a higher-stakes operation and is explicitly out of M11 scope. It is tracked as a
separate task. The re-split API in §2 MUST reject rotation requests for master-key
shares until that spec is completed.

### 1.5 Guard: last executor removed

If revocation would result in `N' = 0` (the last accepted executor is being revoked),
the capsule loses its executor recovery path entirely. The server MUST enforce the
multi-path rule from ARCH-003 §3:

```
REJECT revocation if:
  N' == 0
  AND NOT (capsule has tlock gate OR capsule has at least one bound recipient with sharing_pubkey)
```

Return HTTP 422:

```json
{
  "error": "last_executor_path_removed",
  "detail": "Revoking this executor removes the last recovery path for this capsule. Add a tlock gate or another executor before revoking."
}
```

If the capsule does have a tlock gate or bound recipients, the revocation is permitted
(the tlock/ECDH path remains). The `shamir_threshold` and `shamir_total_shares` columns
are set to `NULL` on the capsule row in this case.

---

## 2. Server role — relay, not orchestrator

**The server does not participate in Shamir computation.** This is the same
architectural constraint as for initial sealing (ARCH-003 §4): the server stores
opaque wrapped blobs and validates structural consistency, but never sees plaintext
share values or the capsule DEK.

The server's role during rotation is:

| Responsibility | Server | Client (nominator) |
|---|---|---|
| Shamir re-split computation | No | Yes |
| Per-executor share wrapping | No | Yes (ECDH-wrap to each executor's pubkey) |
| Atomically updating DB state | Yes | No |
| Validating nomination status | Yes | No |
| Validating share count vs. N' | Yes | No |
| Serving executor pubkeys | Yes (read from `connections.sharing_pubkey`) | No |

### 2.1 Pre-flight: fetch current executor state

Before computing the re-split, the nominator's device fetches current executor data:

```
GET /api/capsules/:id/executor-state
Authorization: Bearer <token>

Response 200:
{
  "nominations": [
    {
      "nomination_id": "<uuid>",
      "display_name": "Alice",
      "sharing_pubkey": "<base64url>",
      "status": "accepted"
    },
    ...
  ],
  "shamir_threshold": 2,
  "shamir_total_shares": 3
}
```

This gives the nominator's device the current set of executor pubkeys and parameters.
It MUST be called immediately before computing the re-split to avoid a race (see §3.2).

### 2.2 Rotation endpoint

```
POST /api/capsules/:id/executor-shares/rotate
Authorization: Bearer <token>
Content-Type: application/json

{
  "revoke_nomination_id": "<uuid>",
  "new_threshold": 2,
  "new_total_shares": 2,
  "shares": [
    {
      "nomination_id": "<uuid for remaining executor 1>",
      "share_index": 1,
      "wrapped_share": "<base64url of capsule-ecdh-aes256gcm-v1 envelope>",
      "share_format": "shamir-share-v1"
    },
    {
      "nomination_id": "<uuid for remaining executor 2>",
      "share_index": 2,
      "wrapped_share": "<base64url>",
      "share_format": "shamir-share-v1"
    }
  ]
}
```

**Server validation before committing:**

1. The calling user is the capsule owner.
2. `revoke_nomination_id` references an accepted nomination for this capsule.
3. `new_total_shares == shares.length`.
4. `1 <= new_threshold <= new_total_shares`.
5. `share_index` values are unique, 1-based, and cover 1..N' without gaps.
6. Each `nomination_id` in `shares` references an accepted nomination for this
   capsule that is not `revoke_nomination_id`.
7. Each `wrapped_share` is a structurally valid asymmetric envelope with
   `alg_id = "capsule-ecdh-aes256gcm-v1"`.
8. If `new_total_shares == 0`: the multi-path fallback guard (§1.5) passes.
9. The capsule is in state `sealed` (only sealed capsules have shares to rotate).
10. Rotation target is a per-capsule share (`capsule_id IS NOT NULL`); master-key
    shares are rejected with HTTP 422 until that spec is written.

**On success (200):**

```json
{
  "capsule_id": "<uuid>",
  "shamir_threshold": 2,
  "shamir_total_shares": 2,
  "revoked_nomination_id": "<uuid>"
}
```

**Error responses:**

| Code | `error` | Condition |
|---|---|---|
| 403 | `not_capsule_owner` | Caller is not the capsule owner |
| 404 | `nomination_not_found` | `revoke_nomination_id` not found or not accepted |
| 422 | `last_executor_path_removed` | §1.5 guard triggered |
| 422 | `master_key_shares_not_supported` | Attempt to rotate master-key shares |
| 422 | `share_count_mismatch` | `new_total_shares != shares.length` |
| 422 | `invalid_share_envelope` | A `wrapped_share` fails structural validation |
| 409 | `rotation_in_progress` | An optimistic-lock conflict; retry after re-fetch (see §3.2) |

---

## 3. Atomicity and failure recovery

### 3.1 Atomicity guarantee

The rotation endpoint executes within a single database transaction (PostgreSQL
`SERIALIZABLE` isolation is sufficient; `READ COMMITTED` with the constraints
below is also acceptable):

```sql
BEGIN;
  -- 1. Revoke nomination
  UPDATE executor_nominations
    SET status = 'revoked', revoked_at = NOW()
    WHERE id = :revoke_nomination_id AND owner_user_id = :caller_id;

  -- 2. Delete all current shares for this capsule
  DELETE FROM executor_shares
    WHERE capsule_id = :capsule_id;

  -- 3. Insert new shares
  INSERT INTO executor_shares (...) VALUES (...), (...);

  -- 4. Update capsule Shamir parameters
  UPDATE capsules
    SET shamir_threshold = :new_threshold,
        shamir_total_shares = :new_total_shares
    WHERE id = :capsule_id AND owner_user_id = :caller_id;
COMMIT;
```

If any step fails (constraint violation, disconnect), the transaction rolls back and the
old shares remain valid. The nominator's device receives an error response and can retry.

### 3.2 Nominator goes offline mid-rotation

The critical offline scenario is: the nominator's device fetches executor state, computes
the re-split, begins uploading — then drops network.

Three states are possible when the nominator reconnects:

| State | What happened | Recovery |
|---|---|---|
| **Not started** | Request never reached server (TCP timeout before connection). Old shares intact. | Re-fetch executor state; retry the rotation request. No harm done. |
| **Partially sent, transaction rolled back** | Server received an incomplete request; validation failed; transaction rolled back. | Same as above. |
| **Transaction committed** | Server received the complete request, committed. Nominator gets no response (network dropped before ACK). | When the nominator reconnects, they call `GET /api/capsules/:id/executor-state` to check current state. If `revoked_nomination_id` now has `status = 'revoked'` and `shamir_total_shares = new_total_shares`, the rotation succeeded. No retry needed. |

The key design property: `GET /api/capsules/:id/executor-state` is the nominator's
idempotency check. Before retrying, always re-fetch state. If the rotation already
committed, do not re-rotate.

The server MUST NOT apply a rotation request whose `revoke_nomination_id` already has
`status = 'revoked'`. It returns 409 `nomination_already_revoked` in this case.

### 3.3 Optimistic lock for concurrent requests

A user with two active devices (e.g. Android phone and web app) could simultaneously
issue revocation requests. To guard against this:

Add a `shamir_version INTEGER NOT NULL DEFAULT 0` column to `capsules`. The nominator
reads this version in the pre-flight fetch and includes it in the rotation request:

```json
{
  "shamir_version": 3,
  ...
}
```

The server validates `capsules.shamir_version = :submitted_version` before updating,
then increments it:

```sql
UPDATE capsules
  SET shamir_threshold = ..., shamir_total_shares = ..., shamir_version = shamir_version + 1
  WHERE id = :capsule_id AND shamir_version = :submitted_version;
```

If the version doesn't match (another rotation committed first), return HTTP 409
`rotation_in_progress`. The client re-fetches state and, if needed, re-computes and
retries.

### 3.4 What if the nominator never reconnects?

An accepted executor has been revoked in the UI but the rotation request never reached
the server:

- `executor_nominations.status` is still `'accepted'` on the server.
- The old shares are still present.
- The executor still has access via the old shares.

This is a **client-only revocation** — it has no server effect and provides no security
guarantee until the rotation request succeeds.

**Implication for UX:** The app must track pending rotations locally and retry them on
next launch / connectivity restoration. Pending revocations SHOULD be displayed as
"Revocation pending — waiting for connection" in the Executors screen. This is a UX
and implementation concern for the developer task; it is not a server-side concern.

**The server does not allow partial revocation.** Until a successful `/rotate` response
is received, the nomination is not revoked server-side.

---

## 4. Interaction with SEC-011 — device revocation

### 4.1 SEC-011 scope recap

SEC-011 specifies device revocation: when a user removes a device from Devices & Access,
the server deletes the `wrapped_keys` row for that device and invalidates its sessions.
SEC-011 explicitly notes: "This does not require master key rotation (the wrapped key is
simply deleted; no re-encryption of content is needed)."

### 4.2 Are the two revocation patterns compatible?

**Yes. SEC-011 may delete `wrapped_keys` rows without conflicting with the executor
re-split model.**

The two operations touch entirely disjoint tables and serve different threat models:

| Dimension | SEC-011 (device revocation) | ARCH-009 (executor revocation) |
|---|---|---|
| Table affected | `wrapped_keys` | `executor_shares`, `executor_nominations`, `capsules` |
| Key material involved | Master key wrapped to a device pubkey | Shamir shares of capsule DEK |
| Rotation required? | No — delete the row; no re-encryption | Yes — full re-split on new polynomial |
| Why rotation differs | Deleting `wrapped_keys` removes the server's ability to serve the wrapped master key. The old Keystore private key on the disposed device cannot fetch anything. | Merely deleting `executor_shares` does not invalidate old shares already distributed. A new polynomial is required to mathematically exclude the old share. |
| Server role | Delete row + invalidate sessions | Atomic transaction: revoke nomination + delete old shares + insert new shares + update parameters |

The "delete row, no rotation" pattern in SEC-011 is correct for device revocation because
the `wrapped_keys` entry is the server-side delivery mechanism; once deleted, there is
nothing to fetch. The old device's Keystore private key is useless without the wrapped
blob.

The "delete row and re-split" requirement in ARCH-009 is correct for executor revocation
because the executor holds a *local copy* of their share (fetched when they accepted the
nomination). Deleting the `executor_shares` row on the server does not invalidate that
local copy. Only generating a new polynomial over the same secret makes the old share
cryptographically irrelevant.

### 4.3 Unified revocation vocabulary

To avoid future confusion, the following terms are adopted project-wide:

| Term | Meaning |
|---|---|
| **Device revocation** | Delete `wrapped_keys` row; invalidate sessions. No key rotation. Governed by SEC-011. |
| **Executor revocation** | Mark nomination `revoked`; delete old `executor_shares` for capsule; compute and distribute new shares to remaining executors. Full re-split required. Governed by ARCH-009. |
| **Key rotation** (general) | Generating a new secret (e.g. new master key) and re-wrapping all dependent material. Neither SEC-011 nor ARCH-009 requires this. |

SEC-011 uses the term "no rotation" correctly under this vocabulary. Executor revocation
in ARCH-009 does not require key rotation (the capsule DEK is unchanged); it requires
re-split.

### 4.4 Future interaction: executor revocation after device revocation

If executor X's device is revoked (SEC-011), and executor X still holds an accepted
nomination, two things happen independently:

1. SEC-011 removes X's device access (session + `wrapped_keys` row deleted).
2. Executor X's `executor_shares` row is NOT affected by SEC-011.

This means X remains an accepted executor cryptographically until the nominator
explicitly revokes the nomination via the ARCH-009 flow. This is acceptable for M11:

- X cannot currently authenticate to the server (their session is gone and their
  wrapped_keys row is deleted — they cannot get a new session on the revoked device).
- X's local share copy may still reside on the revoked device. If that device is in
  hostile hands, the share is potentially recoverable from device storage (subject to
  the secure storage guarantees).

**Recommendation for the UX team:** When the server detects that an executor's only
registered device has been revoked (all `wrapped_keys` rows for that `user_id` are
deleted via SEC-011), notify the nominator: "Executor X's device has been removed.
Consider revoking their nomination." This is advisory only; the nominator decides
whether to trigger the ARCH-009 re-split.

This advisory is M12 UX scope. It is not a server enforcement requirement for M11.

---

## 5. Schema additions

The following column is required beyond the ARCH-004/ARCH-003 schema to support
optimistic locking (§3.3):

```sql
-- V33__executor_rotation_version.sql

-- Optimistic lock version for Shamir share rotation.
-- Incremented atomically on each successful /rotate request.
-- Clients must include the current version in rotation requests to detect concurrent updates.
ALTER TABLE capsules
    ADD COLUMN shamir_version INTEGER NOT NULL DEFAULT 0;
```

No other schema changes are required. The existing `executor_nominations`,
`executor_shares`, and `capsules` tables (defined in V31/V32) are sufficient.

---

## 6. API surface summary

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/capsules/:id/executor-state` | Pre-flight: fetch current nominations, pubkeys, and Shamir parameters (including `shamir_version`) |
| `POST` | `/api/capsules/:id/executor-shares/rotate` | Atomic revocation + re-split |

The existing `POST /api/capsules/:id/executor-shares` endpoint (initial distribution
at sealing time, ARCH-003 §7) is unchanged.

---

## 7. Out of scope for M11

The following are explicitly deferred:

- **Master-key Shamir re-split.** `executor_shares` rows with `capsule_id IS NULL`
  require re-splitting the vault master key. This is higher-stakes (affects vault
  access, not just one capsule), requires the vault to be unlocked during rotation,
  and needs its own brief. The `/rotate` endpoint rejects master-key rotation requests
  with HTTP 422 until that brief is written.
- **Death-verification-gated collection.** ARCH-003 §7 defers this to M13. Not affected
  by this brief.
- **Advisory notification when executor's device is revoked (SEC-011 intersection).** §4.4.
  Deferred to M12 UX scope.
- **Threshold-change without revocation.** Changing `k` without revoking anyone is a
  separate operation (expand or reduce sharing without re-splitting). Not specified here.

---

## 8. Dependency chain

```
ARCH-005 (algorithm IDs)
  └── ARCH-004 (connections model, executor nomination lifecycle)
       └── ARCH-003 (capsule crypto: Shamir encoding, sealing API)
            ├── ARCH-006 (tlock provider — blinding scheme; confirms Shamir over DEK)
            └── ARCH-009 (this document — executor revocation and re-split)
                 └── M11 developer task: POST /api/capsules/:id/executor-shares/rotate
                 └── M11 developer task: GET /api/capsules/:id/executor-state
                 └── M11 developer task: Android revoke executor UI
                 └── M11 developer task: Web revoke executor UI
```

SEC-011 runs independently and does not block or depend on ARCH-009.

---

## Cross-references

- `docs/briefs/ARCH-003_m11-capsule-crypto-brief.md` — Shamir encoding (§1, §7), sealing validation (§4).
- `docs/briefs/ARCH-004_connections-data-model.md` — executor nomination lifecycle (§4), `executor_nominations` schema (§5).
- `docs/briefs/ARCH-006_tlock-provider-interface.md` — confirms Shamir shares are over `DEK`, not `DEK_client` (§6.1, §9.2).
- `tasks/in-progress/SEC-011_device-revocation.md` — device revocation scope and threat model.
