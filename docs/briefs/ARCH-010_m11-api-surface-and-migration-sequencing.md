# ARCH-010 — M11 API Surface and Migration Sequencing Brief

*Authored: 2026-05-16. Status: approved coordination document.*
*This document supersedes the split sealing-validation sequences in ARCH-003 §4 and ARCH-006 §5.*
*Prerequisite reading: `docs/envelope_format.md`.*
*A Developer can implement all M11 server endpoints using this brief as sole reference (alongside `docs/envelope_format.md`). No other ARCH document is required for implementation, though all four predecessor briefs remain the authoritative design rationale source.*

---

## 1. M11 server endpoint inventory — implementation dependency order

Implement in the numbered order below. An endpoint listed after another must not be
started before all its predecessors compile, pass unit tests, and integrate correctly
against the migration schema.

### Wave 0 — Schema prerequisites (no endpoints, migration only)

Before any endpoint work, run both Flyway migrations:

1. **V31 — connections schema** (see §3 for migration sequencing and rollback notes)
2. **V32 — capsule crypto schema** (see §3)

### Wave 1 — Connections bootstrap (V31 prerequisite)

These endpoints are purely CRUD against the `connections` and `executor_nominations`
tables. No capsule crypto is required. Implement these first so connection IDs are
available for all subsequent sealing and nomination work.

| # | Method + Path | One-line description |
|---|---|---|
| 1 | `GET /api/connections` | List the authenticated user's connections (all roles). |
| 2 | `POST /api/connections` | Create a new connection (bound or deferred-pubkey placeholder). Body: `{ display_name, contact_user_id?, email?, roles[] }`. |
| 3 | `PATCH /api/connections/:id` | Update a connection's display name or roles array. Also used to cache a newly-observed `sharing_pubkey` on upgrade from placeholder to bound. |
| 4 | `DELETE /api/connections/:id` | Remove a connection. Must reject if the connection has active `executor_nominations` rows in state `pending` or `accepted`. |
| 5 | `GET /api/connections/:id` | Fetch a single connection by ID (owner only). |

**Note on backfill.** The V31 migration backfills all existing friend pairs into
`connections` automatically (see §4). No client-initiated "backfill" endpoint is
needed. The GET list endpoint above will return these rows immediately after V31
runs.

### Wave 2 — Executor nomination lifecycle (depends on Wave 1)

These endpoints implement the offer-accept-revoke lifecycle defined in ARCH-004 §4.
Implement after Wave 1; shares cannot be distributed without accepted nominations,
and `/seal` validates nomination state.

| # | Method + Path | One-line description |
|---|---|---|
| 6 | `POST /api/executor-nominations` | Owner offers an executor nomination. Body: `{ connection_id, message? }`. Creates a `pending` row; triggers notification to the nominee. |
| 7 | `GET /api/executor-nominations` | Owner lists all nominations they have issued (all states). |
| 8 | `GET /api/executor-nominations/received` | Nominee lists nominations extended to them (all states). |
| 9 | `POST /api/executor-nominations/:id/accept` | Nominee accepts a nomination. Moves `status` to `accepted`, records `responded_at`. |
| 10 | `POST /api/executor-nominations/:id/decline` | Nominee declines. Moves `status` to `declined`, records `responded_at`. |
| 11 | `POST /api/executor-nominations/:id/revoke` | Owner revokes an accepted or pending nomination. Moves `status` to `revoked`, records `revoked_at`. In M11 no automatic share rotation is performed; the owner must manually re-seal or redistribute shares. |

### Wave 3 — Capsule recipient linking (depends on Wave 1)

Add the `connection_id` FK column to `capsule_recipients` (already in V31). These
endpoints allow the client to link existing free-text recipients to connection rows.
No sealing logic is involved.

| # | Method + Path | One-line description |
|---|---|---|
| 12 | `PATCH /api/capsules/:id/recipients/:recipientId/link` | Link a `capsule_recipients` row to a `connections` row. Body: `{ connection_id }`. Sets `capsule_recipients.connection_id`; validates the connection belongs to the capsule owner. |

### Wave 4 — Executor share distribution (depends on Wave 2)

Implement after executor nominations exist. The `/seal` endpoint (Wave 5) validates
that shares have been submitted, so these must be functional before sealing.

| # | Method + Path | One-line description |
|---|---|---|
| 13 | `POST /api/capsules/:id/executor-shares` | Owner uploads Shamir shares for all accepted nominees. Body: `{ shares: [{ nomination_id, share_index, wrapped_share, share_format }] }`. Full validation spec in §2. |
| 14 | `GET /api/capsules/:id/executor-shares/mine` | Accepted executor fetches their own wrapped share for this capsule. |
| 15 | `GET /api/capsules/:id/executor-shares/collect` | Author-authenticated: collect all shares for testing/recovery. In M11, only the author-authenticated path is implemented. Death-verification-gated collection is M13 scope. |

### Wave 5 — Sealing endpoint (depends on Waves 1–4)

The most complex endpoint. Do not start until all Wave 1–4 endpoints are complete
and tested — the seal handler calls into connections, nominations, and shares tables.

| # | Method + Path | One-line description |
|---|---|---|
| 16 | `PUT /api/capsules/:id/seal` | Seal a capsule: validate all recipient keys, tlock fields (if present), Shamir config (if present), and multi-path fallback rule; write all crypto columns atomically; advance capsule `shape` to `sealed`. Full validation sequence in §5. |

### Wave 6 — tlock key delivery (depends on Wave 5)

Implement after sealing works. The `/tlock-key` endpoint is only meaningful for
sealed capsules; it depends on `capsules.tlock_dek_tlock` and
`capsules.tlock_key_digest` being written by `/seal`.

| # | Method + Path | One-line description |
|---|---|---|
| 17 | `GET /api/capsules/:id/tlock-key` | Return `DEK_tlock` to an authenticated Android/web recipient after the tlock round has published and `unlock_at` has passed. Full gate logic and logging prohibitions in §5.3. |

### Wave 7 — Capsule read-path amendments

These modifications to existing endpoints deliver the new M11 columns to clients.
No new tables are required. Implement after Wave 5.

| # | Method + Path | One-line description |
|---|---|---|
| 18 | `GET /api/capsules/:id` (amended) | Include M11 fields in the capsule response: `wrapped_capsule_key`, `capsule_key_format`, `tlock_round`, `tlock_chain_id`, `tlock_wrapped_key`, `tlock_key_digest`, `shamir_threshold`, `shamir_total_shares`. Null fields are omitted or null in JSON. |
| 19 | `GET /api/capsule-recipient-keys/:capsuleId` (new) | Return `capsule_recipient_keys` rows for a capsule (owner or recipient authenticated). Each row includes `connection_id`, `wrapped_capsule_key`, `wrapped_blinding_mask`. iOS receives `wrapped_capsule_key`; Android/web use `wrapped_blinding_mask` for the blinded path. |

---

## 2. Endpoint validation reference

### 2.1 POST /api/executor-shares — full validation

- Caller must be the capsule owner.
- Each `nomination_id` must reference an `executor_nominations` row with
  `status = 'accepted'` and `owner_user_id` matching the caller.
- `shares` array length must equal `capsules.shamir_total_shares` (must be set before
  or in the same sealing request — shares are uploaded after sealing).
- `share_index` values must be unique, 1-based, and cover `1..N` without gaps.
- Each `wrapped_share` must be a syntactically valid **asymmetric** envelope with
  `alg_id = "capsule-ecdh-aes256gcm-v1"` (use `EnvelopeFormat.validateAsymmetric`).
- `share_format` must be `"shamir-share-v1"`.

### 2.2 PATCH /api/connections/:id/link — full validation

- `connection_id` must reference a `connections` row owned by the authenticated user.
- The `capsule_recipients` row must belong to a capsule owned by the authenticated user.
- `connection_id` uniqueness per capsule: a connection may only be linked to one
  recipient row per capsule.

### 2.3 DELETE /api/connections/:id — blocking condition

- Reject with HTTP 409 if any `executor_nominations` row exists for this connection
  with `status IN ('pending', 'accepted')`.
- Cascade via FK (`ON DELETE CASCADE`) handles `executor_nominations` rows in
  `declined` or `revoked` states automatically.

---

## 3. Migration sequencing and rollback safety

### 3.1 Migration order

Flyway applies migrations in version order. Both migrations must run before any M11
endpoint is deployed. The two-migration sequence is:

```
V31__connections.sql          — connections, executor_nominations, capsule_recipients.connection_id
V32__m11_capsule_crypto.sql   — wrapped_capsule_key, tlock_*, shamir_*, capsule_recipient_keys, executor_shares
```

**V31 must precede V32.** V32 creates `capsule_recipient_keys` with a FK to
`connections(id)` and `executor_shares` with a FK to `executor_nominations(id)`. If
V31 has not run, V32 will fail at table creation (foreign key targets do not exist).

Deploy strategy: run both migrations in a single Flyway execution before the new
server binary is started. The old server binary is still running against the old
schema at that point; the new columns are `NULL DEFAULT` or new tables (additive only)
so the old binary continues to function normally during the brief deployment window.

### 3.2 V31 rollback analysis

**What V31 does:**

1. Creates `connections` table.
2. Creates `executor_nominations` table.
3. Runs an `INSERT` backfilling friend pairs from `friendships` into `connections`.
4. `ALTER TABLE capsule_recipients ADD COLUMN connection_id UUID NULL`.
5. Creates three indexes.

**Rollback classification: POSSIBLE but requires manual verification.**

The `ALTER TABLE` on `capsule_recipients` is additive (nullable column). It can be
reversed with `ALTER TABLE capsule_recipients DROP COLUMN connection_id`, but only
if no code has written non-NULL values into it. Immediately after V31 runs the column
is entirely NULL (the backfill does not touch `capsule_recipients`), so reverting is
safe at that instant.

The backfill INSERT into `connections` is idempotent on re-run (it uses `ON CONFLICT
DO NOTHING`) but not self-reversing — a rollback must explicitly `DROP TABLE
connections CASCADE` (which also drops `executor_nominations` and the indexes, via
their FK dependency on `connections`).

**Rollback procedure (V31):**

```sql
-- Step 1: drop new FK column on capsule_recipients (safe immediately after V31)
ALTER TABLE capsule_recipients DROP COLUMN IF EXISTS connection_id;

-- Step 2: drop executor_nominations (FK-depends on connections)
DROP TABLE IF EXISTS executor_nominations;

-- Step 3: drop connections
DROP TABLE IF EXISTS connections;

-- Step 4: remove Flyway's record of V31 (requires Flyway repair or manual delete)
-- Caution: do not run this unless the server binary is also rolled back.
```

Rollback is NOT safe after any of the following:
- Any `executor_nominations` rows have been written (nomination offers exist).
- Any `capsule_recipients.connection_id` values have been set (links have been made).
- V32 has been applied (FK constraints from `capsule_recipient_keys` and
  `executor_shares` reference `connections` and `executor_nominations` respectively).

**Verdict:** V31 can be rolled back cleanly in the narrow window between V31 applying
and any application writes occurring (e.g. a staging deploy where the server binary
is immediately rolled back). In production with any real traffic after V31, rollback
is effectively blocked by data integrity — treat it as non-reversible for practical
purposes.

### 3.3 V32 rollback analysis

**What V32 does:**

1. Adds multiple nullable columns to `capsules` (`wrapped_capsule_key`,
   `capsule_key_format`, `tlock_round`, `tlock_chain_id`, `tlock_wrapped_key`,
   `tlock_dek_tlock`, `tlock_key_digest`, `shamir_threshold`, `shamir_total_shares`).
2. Creates `capsule_recipient_keys` table (FK to `capsules` and `connections`).
3. Creates `executor_shares` table (FK to `capsules` and `executor_nominations`).
4. Creates indexes on both new tables.
5. Adds a `CHECK` constraint on `capsules.capsule_key_format`.

**Rollback classification: POSSIBLE IF no capsules have been sealed under M11.**

All new columns are nullable. If no sealing calls have been made, all new columns
remain NULL and can be dropped cleanly. The two new tables are empty and can be
dropped.

**Rollback procedure (V32):**

```sql
-- Step 1: drop new tables (safe if empty)
DROP TABLE IF EXISTS executor_shares;
DROP TABLE IF EXISTS capsule_recipient_keys;

-- Step 2: drop new columns from capsules
ALTER TABLE capsules
    DROP COLUMN IF EXISTS wrapped_capsule_key,
    DROP COLUMN IF EXISTS capsule_key_format,
    DROP COLUMN IF EXISTS tlock_round,
    DROP COLUMN IF EXISTS tlock_chain_id,
    DROP COLUMN IF EXISTS tlock_wrapped_key,
    DROP COLUMN IF EXISTS tlock_dek_tlock,
    DROP COLUMN IF EXISTS tlock_key_digest,
    DROP COLUMN IF EXISTS shamir_threshold,
    DROP COLUMN IF EXISTS shamir_total_shares;
```

**CRITICAL — V32 CANNOT be cleanly reverted once any capsule has been sealed
using the M11 `/seal` endpoint.** After sealing:

- `capsules.wrapped_capsule_key` holds the sole copy of the recipient-wrapped DEK
  for a sealed capsule. Dropping this column destroys the recipient's ability to
  decrypt the capsule permanently.
- `capsule_recipient_keys` rows hold per-recipient wrapped DEKs. Dropping the table
  destroys all additional-recipient key material permanently and irrecoverably.
- `executor_shares` rows hold Shamir shares wrapped to executor pubkeys. Dropping the
  table destroys all share material permanently.

There is no practical rollback of V32 in a production environment where any capsule
has been sealed. Treat V32 as irreversible once the first seal call succeeds in
production.

### 3.4 Deployment gate

Do not deploy V32 to production until:
1. V31 has been deployed and verified (connections list endpoint returns expected
   backfilled data).
2. The full M11 server binary (all Wave 1–7 endpoints) is ready for simultaneous
   promotion.
3. At least one full staging cycle has exercised sealing, share distribution, and
   `/tlock-key` delivery end-to-end.

---

## 4. V31 connections backfill — production-scale correctness review

This section reviews the `INSERT ... SELECT` block in `V31__connections.sql`
(ARCH-004 §5) for correctness at production data scale.

### 4.1 The backfill statement (verbatim from ARCH-004)

```sql
INSERT INTO connections (id, owner_user_id, contact_user_id, display_name, sharing_pubkey, roles, created_at)
SELECT
    gen_random_uuid(),
    u1.id AS owner_user_id,
    u2.id AS contact_user_id,
    u2.display_name,
    ask.pubkey,
    ARRAY['recipient'],
    f.created_at
FROM friendships f
JOIN users u1 ON u1.id = f.user_id_1
JOIN users u2 ON u2.id = f.user_id_2
LEFT JOIN account_sharing_keys ask ON ask.user_id = u2.id
UNION ALL
SELECT
    gen_random_uuid(),
    u2.id AS owner_user_id,
    u1.id AS contact_user_id,
    u1.display_name,
    ask.pubkey,
    ARRAY['recipient'],
    f.created_at
FROM friendships f
JOIN users u1 ON u1.id = f.user_id_1
JOIN users u2 ON u2.id = f.user_id_2
LEFT JOIN account_sharing_keys ask ON ask.user_id = u1.id
ON CONFLICT DO NOTHING;
```

### 4.2 Lock behaviour

**Table-level locks acquired:**

- `friendships`: read (SELECT) — `AccessShareLock`. No DDL runs against `friendships`
  in V31; it is read-only in this statement.
- `users`: read (SELECT) — `AccessShareLock`.
- `account_sharing_keys`: read (SELECT — LEFT JOIN) — `AccessShareLock`.
- `connections`: write (INSERT) — `RowExclusiveLock` on the new table. Since
  `connections` is brand-new (just created in this migration), no other session can
  hold any lock on it while the migration transaction is running.

**Implication:** The backfill holds `AccessShareLock` on `friendships`, `users`, and
`account_sharing_keys` for the duration of the INSERT SELECT. This is the weakest
possible read lock — it does not block concurrent reads or writes (DML) on those
tables. Normal application traffic (`GET /api/uploads`, friend list endpoints, etc.)
will not be blocked.

The INSERT itself holds `RowExclusiveLock` on `connections`, which again is not
contended (the table is new).

**Risk at scale:** At current user scale (small — staging tester plus founding user),
the `friendships` table has O(10) rows at most. Even at 100,000 friendship rows the
SELECT across three tables with indexed joins is sub-second in PostgreSQL. No lock
timeout risk exists.

**Conclusion on locking:** The backfill lock profile is safe at any realistic
Heirlooms production scale.

### 4.3 Atomicity

The entire V31 migration runs in a single Flyway-managed transaction. If the
backfill INSERT fails for any reason (constraint violation, OOM, etc.), Flyway rolls
back the entire migration — including the `CREATE TABLE connections` DDL and the
`ALTER TABLE capsule_recipients` DDL. The database is left in the pre-V31 state with
no partial writes. This is the correct behaviour.

**PostgreSQL DDL is transactional**, so `CREATE TABLE`, `ALTER TABLE`, and the
`INSERT` all participate in the same transaction. There is no risk of the tables
being created but the backfill being partial.

### 4.4 Edge case: users with no friendships

A user with zero rows in `friendships` (new user or a user who has never accepted an
invite) will produce zero rows from both legs of the UNION ALL. This is correct
behaviour — they get zero connections, which is the accurate starting state for a user
with no friends.

**Proof:** Both SELECT legs inner-join on `friendships f`. No `friendships` row means
no rows pass the join, and the UNION ALL contributes nothing to the INSERT. The user
row in `users` is unaffected. The `connections` table ends up with zero rows for this
user, which is semantically correct.

No separate handling for "users with no friendships" is needed in the migration.

### 4.5 Edge case: a user with a sharing key vs. without

The JOIN to `account_sharing_keys` is a LEFT JOIN. If a user has never generated
a sharing keypair (e.g. a legacy account from before M9 sharing keypair
provisioning), `ask.pubkey` will be NULL for their contact row. The `sharing_pubkey`
column on `connections` is nullable by design and accepts NULL without constraint
violation.

A connection with `sharing_pubkey IS NULL` is a valid deferred-pubkey connection —
the client will cache the pubkey when it next observes the contact's sharing key
(e.g. at next login, or when the contact generates a key for the first time). The
server enforces at sealing time that deferred-pubkey connections must have a fallback
path (see §5.1 step 9).

### 4.6 Edge case: multiple sharing keys per user

`account_sharing_keys` has not been reviewed for a UNIQUE constraint on `user_id`.
If a user has multiple rows (e.g. after a key rotation that left orphan rows), the
LEFT JOIN will produce multiple rows per friendship, and the INSERT will attempt to
insert multiple `connections` rows for the same `(owner_user_id, contact_user_id)`
pair.

**Mitigation:** The `connections` table carries a UNIQUE constraint on
`(owner_user_id, contact_user_id)`. The `ON CONFLICT DO NOTHING` clause on the
INSERT means only the first row for each pair is inserted (ordering is
non-deterministic in a UNION ALL, so which sharing key is cached is undefined if
there are multiple). This is safe — the cached pubkey can be overwritten later by
the client via `PATCH /api/connections/:id`.

**Recommended pre-migration check:** Before running V31 in production, verify that
`account_sharing_keys` has at most one row per user:

```sql
SELECT user_id, COUNT(*) FROM account_sharing_keys GROUP BY user_id HAVING COUNT(*) > 1;
```

If any rows are returned, identify and remove the orphan rows before running the
migration. If the schema already enforces `UNIQUE (user_id)` on
`account_sharing_keys`, this check is moot.

### 4.7 ON CONFLICT correctness

The `ON CONFLICT DO NOTHING` clause fires on the `UNIQUE (owner_user_id,
contact_user_id)` constraint and the `UNIQUE (owner_user_id, email)` constraint on
`connections`. There is no `email` column set in the backfill (it is NULL for all
backfilled rows since these are bound connections with a `contact_user_id`). The
email uniqueness constraint therefore does not trigger for any backfilled row.

The `(owner_user_id, contact_user_id)` constraint fires when there are duplicate
friendship rows or multiple sharing-key rows (§4.6 above). In both cases silently
skipping the duplicate is the correct outcome.

### 4.8 Overall verdict

The ARCH-004 §5 backfill is **correct at production scale** with the following caveats:

1. **Run the multi-key pre-migration check** from §4.6 before production deployment.
2. **No structural changes are required to the backfill SQL itself.** The locking,
   atomicity, empty-user, and NULL-pubkey edge cases are all handled correctly.
3. **The `ON CONFLICT DO NOTHING` is the correct conflict resolution** given the
   nullable-email design of the `connections` table.

---

## 5. Merged sealing validation sequence

*This section supersedes ARCH-003 §4 and ARCH-006 §5. Those documents remain
the authoritative design rationale source. This is the single implementation
reference for the `/seal` route handler. Do not implement against ARCH-003 §4
or ARCH-006 §5 directly — use this section only.*

### 5.1 Full validation sequence for `PUT /api/capsules/:id/seal`

Execute the following checks **in order**. At the first failure, return HTTP 422
with the indicated error body and abort the request without writing any database
rows.

**Pre-conditions (checked before any crypto validation):**

```
[0] Authenticate caller: must be the capsule owner.
    → HTTP 403 { "error": "forbidden" } on failure.

[1] Load capsule: capsule must exist and belong to the caller.
    → HTTP 404 { "error": "not_found" } on failure.

[2] Check capsule state machine: capsule.shape must be 'open'.
    → HTTP 409 { "error": "capsule_not_sealable",
                 "detail": "capsule is not in 'open' shape" } on failure.

[3] Check at least one recipient_key entry is present in the request body.
    → HTTP 422 { "error": "missing_recipient_keys",
                 "detail": "at least one recipient key is required" } on failure.
```

**Per-recipient validation (repeat for each entry in `recipient_keys[]`):**

```
[4] Validate wrapped_capsule_key: must be a syntactically valid ASYMMETRIC envelope
    with alg_id = "capsule-ecdh-aes256gcm-v1".
    Use EnvelopeFormat.validateAsymmetric(wrappedCapsuleKey).
    → HTTP 422 { "error": "invalid_wrapped_capsule_key",
                 "detail": "recipient <connection_id>: invalid envelope" }

[5] If tlock fields are present in the request body (tlock != null):
    Validate wrapped_blinding_mask is present and is a syntactically valid ASYMMETRIC
    envelope with alg_id = "capsule-ecdh-aes256gcm-v1" for every recipient.
    → HTTP 422 { "error": "missing_wrapped_blinding_mask",
                 "detail": "recipient <connection_id>: tlock capsules require wrapped_blinding_mask" }

[6] Validate connection_id references an existing connections row owned by the
    capsule owner and with contact_user_id IS NOT NULL (bound connection — not a
    deferred-pubkey placeholder for ECDH wrapping validation).
    → HTTP 422 { "error": "invalid_connection_id",
                 "detail": "connection <id> is not bound or does not belong to this user" }
```

**tlock-specific validation (only if request body contains non-null `tlock` fields):**

```
[7] Validate tlock_wrapped_key: must be non-null and non-empty.
    → HTTP 422 { "error": "missing_tlock_wrapped_key" }

[8] Call provider.validate(TimeLockCiphertext(chain_id, round, tlock_wrapped_key_bytes)).
    If TLOCK_PROVIDER=disabled: reject immediately.
    → HTTP 422 { "error": "tlock_not_enabled",
                 "detail": "tlock provider is disabled on this server" }
    If provider.validate() returns false:
    → HTTP 422 { "error": "tlock_blob_invalid",
                 "detail": "tlock blob failed structural validation" }

[9] Validate tlock_chain_id is a known chain:
    Stub: chain_id must equal TimeLockCiphertext.STUB_CHAIN_ID.
    Sidecar (M12): validate against real drand chain registry.
    → HTTP 422 { "error": "unknown_tlock_chain",
                 "detail": "chain_id is not recognised by the configured tlock provider" }

[10] Validate tlock_round timing: the round's expected publish time must be ≤
     capsule.unlock_at + 1 hour.
     For stub: expected_publish = STUB_GENESIS_UNIX + round * STUB_PERIOD_SECS.
     For sidecar: query the sidecar's chain parameters.
     → HTTP 422 { "error": "tlock_round_mismatch",
                  "detail": "tlock round expected publish time is more than 1 hour after unlock_at" }

[11] Validate tlock_key_digest is present and is exactly 32 bytes (after base64url decode).
     → HTTP 422 { "error": "missing_tlock_key_digest" }

[12] Validate SHA-256(dek_tlock) == tlock_key_digest.
     → HTTP 422 { "error": "tlock_digest_mismatch",
                  "detail": "SHA-256(dek_tlock) does not match tlock_key_digest" }
     NOTE: dek_tlock is key material. Do NOT log the dek_tlock value. Log only
     "tlock digest validation failed for capsule <id>" at WARN level.
```

**Shamir-specific validation (only if request body contains non-null `shamir` fields):**

```
[13] Validate shamir.threshold >= 1 and shamir.total_shares >= 1.
     Validate shamir.threshold <= shamir.total_shares.
     → HTTP 422 { "error": "invalid_shamir_config",
                  "detail": "<specific constraint>" }

[14] Count executor_nominations rows with status='accepted' for this capsule owner.
     The count must be >= shamir.total_shares.
     → HTTP 422 { "error": "insufficient_accepted_nominations",
                  "detail": "need <N> accepted nominations; only <M> found" }
     NOTE: Shares are uploaded AFTER sealing via POST /executor-shares. At sealing
     time the server only validates that enough accepted nominations exist to receive
     the shares. The actual share blobs are submitted in a separate request.
```

**Multi-path fallback rule (always checked):**

```
[15] Evaluate the fallback rule:
     let all_bound    = all recipient_keys entries have a bound connection
                        (sharing_pubkey IS NOT NULL, confirmed at step [6])
     let has_tlock    = tlock fields are non-null in the request
     let has_executor = shamir fields are present AND shamir.total_shares >= 1

     If NOT all_bound AND NOT (has_tlock OR has_executor):
     → HTTP 422 { "error": "sealing_validation_failed",
                  "detail": "one or more recipients lack a sharing pubkey and no tlock or executor fallback is configured" }
```

**Write phase (only reached if all checks above pass):**

```
[16] In a single database transaction:
     a. Write wrapped_capsule_key, capsule_key_format to capsules row (primary recipient).
     b. Write tlock columns (tlock_round, tlock_chain_id, tlock_wrapped_key,
        tlock_dek_tlock, tlock_key_digest) if tlock is present.
        CRITICAL: tlock_dek_tlock MUST NOT be logged at any level. Failure to comply
        violates the blinding-scheme security guarantee.
     c. Write shamir_threshold, shamir_total_shares to capsules row if shamir is present.
     d. Upsert capsule_recipient_keys rows for each entry in recipient_keys[]
        (wrapped_capsule_key + wrapped_blinding_mask for tlock; wrapped_capsule_key only
        for non-tlock).
     e. Set capsules.shape = 'sealed', capsules.sealed_at = now().
     Commit. If any step fails, rollback the entire transaction.
```

**Success response:**

```json
HTTP 200
{
  "capsule_id": "<uuid>",
  "shape": "sealed",
  "state": "sealed",
  "sealed_at": "2026-05-16T10:00:00Z"
}
```

### 5.2 POST /api/executor-shares — validation sequence

Called after a successful sealing of a Shamir capsule.

```
[1] Authenticate caller: must be the capsule owner.
[2] Load capsule: must be sealed (shape = 'sealed') with shamir_total_shares set.
[3] shares array length must equal capsules.shamir_total_shares.
    → HTTP 422 { "error": "wrong_share_count", "detail": "expected <N>, got <M>" }
[4] share_index values must be unique, 1-based, covering 1..N without gaps.
    → HTTP 422 { "error": "invalid_share_indices" }
[5] Each nomination_id must reference an executor_nominations row with
    status='accepted' and owner_user_id matching the capsule owner.
    → HTTP 422 { "error": "invalid_nomination_id", "detail": "<id>" }
[6] Each wrapped_share must be a syntactically valid ASYMMETRIC envelope with
    alg_id = "capsule-ecdh-aes256gcm-v1".
    → HTTP 422 { "error": "invalid_wrapped_share", "detail": "share <index>" }
[7] share_format must be "shamir-share-v1".
    → HTTP 422 { "error": "invalid_share_format" }
[8] In a single transaction: insert all executor_shares rows. Return 200.
```

### 5.3 GET /api/capsules/:id/tlock-key — gate logic and logging requirements

```
[1] Authenticate caller: must be an authenticated recipient of this capsule
    (present in capsule_recipient_keys).
    → HTTP 403 { "error": "not_a_recipient" }

[2] Load capsule: must exist and be sealed with non-null tlock fields.
    → HTTP 404 { "error": "not_found" }

[3] Check TLOCK_PROVIDER != disabled.
    → HTTP 503 { "error": "tlock_not_enabled" }

[4] Check now() >= capsules.unlock_at.
    If not: → HTTP 202 { "error": "tlock_gate_not_open",
                          "detail": "unlock_at has not passed",
                          "retry_after_seconds": <seconds until unlock_at> }

[5] Call provider.decrypt(TimeLockCiphertext(chain_id, round, tlock_wrapped_key)).
    If null (gate not open): → HTTP 202 { "error": "tlock_gate_not_open",
                                           "detail": "round not yet published",
                                           "retry_after_seconds": 30 }

[6] Verify SHA-256(capsules.tlock_dek_tlock) == capsules.tlock_key_digest.
    Mismatch: → HTTP 500 (log ERROR: "tlock tamper detected for capsule <id>";
                           DO NOT log tlock_dek_tlock value).

[7] Return:
    HTTP 200 { "dek_tlock": "<base64url: capsules.tlock_dek_tlock>",
               "chain_id": "<hex>",
               "round": <bigint> }
```

**LOGGING PROHIBITION (CRITICAL):** The response body of this endpoint and the value
of `capsules.tlock_dek_tlock` must never appear in:
- Application logs (SLF4J at any level)
- Access/request logs (nginx, Cloud Run)
- Distributed traces (OpenTelemetry, Stackdriver)
- Error reporting (Sentry, Crashlytics)

Log the request only: capsule ID, caller user ID, HTTP status, latency.
Configure HTTP request-logging middleware to redact the response body for this path.
In integration tests assert the response body is present but do not log it.

The same prohibition applies to the `dek_tlock` field in the `PUT /seal` request
body — log that a tlock seal request was received, not its key material.

---

## 6. Key invariants for all M11 implementors

The following invariants must hold across every endpoint. Violation of any invariant
constitutes a security defect.

| # | Invariant |
|---|---|
| I-1 | `capsules.wrapped_capsule_key` ALWAYS wraps the full capsule `DEK` — not `DEK_client`. This is the iOS compatibility guarantee. No exception. |
| I-2 | `capsule_recipient_keys.wrapped_blinding_mask` is non-NULL ONLY on tlock capsules and ALWAYS wraps `DEK_client` (the client-side blinding mask). |
| I-3 | Shamir shares (in `executor_shares`) are ALWAYS computed over `DEK` (the full capsule content key), not `DEK_client`. Executor recovery does not require a `/tlock-key` call. |
| I-4 | `capsules.tlock_dek_tlock` and the `/tlock-key` response body must never appear in any log, trace, or metric. |
| I-5 | `tlock_key_digest = SHA-256(DEK_tlock)` is stored at sealing time and MUST be verified at `/tlock-key` delivery time. A mismatch is HTTP 500, not a soft error. |
| I-6 | The server MUST NOT return `DEK_tlock` until BOTH: `TimeLockProvider.decrypt()` returns non-null AND `now() >= capsules.unlock_at`. |
| I-7 | The multi-path fallback rule (§5.1 step [15]) MUST be enforced at sealing time. A deferred-pubkey connection as the SOLE unlock path is always rejected. |
| I-8 | The capsule state machine gate (`shape = 'open'` required to seal) is checked before any crypto validation. Never allow sealing of an already-sealed or delivered capsule. |

---

## 7. Cross-references

- `docs/envelope_format.md` — binary wire format for all envelopes; defines
  `validateAsymmetric` semantics
- `docs/briefs/ARCH-003_m11-capsule-crypto-brief.md` — full blinding scheme design,
  algorithm IDs, schema columns, iOS compatibility rationale
- `docs/briefs/ARCH-004_connections-data-model.md` — connections table schema,
  executor nomination lifecycle, M11 vs M12 scope boundary
- `docs/briefs/ARCH-006_tlock-provider-interface.md` — `TimeLockProvider` interface,
  `StubTimeLockProvider` spec, sidecar REST API contract (M12), configuration env vars
- `docs/ROADMAP.md` M11 section — product framing and milestone context
