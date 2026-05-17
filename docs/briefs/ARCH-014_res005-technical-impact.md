# ARCH-014 — RES-005 Technical Impact Assessment: Presence-Gated Delivery and Count-Conditional Trigger

**ID:** ARCH-014
**Date:** 2026-05-17
**Author:** Technical Architect
**Audience:** CTO, PA, Developer (M12 planning)
**Status:** Final
**Depends on:** ARCH-003, ARCH-006, ARCH-008, ARCH-010, RES-005

---

## Purpose

This brief answers the seven questions posed in ARCH-014 regarding the technical impact of the two constructions defined in RES-005 on the existing M11/M12 architecture. No source code is modified.

**Blocker summary (read first):** No M11 blocker is identified. Both constructions are clean post-M11 additions. One reservation is needed before implementation can begin: `presence-ticket-v1` should be reserved in `docs/envelope_format.md` as a named scheme for the window payload format, even though no new binary algorithm ID is strictly required (see §3).

---

## §1 — Schema Impact

### §1 new tables (Presence-Gated Delivery)

Three new schema objects are required for the §1 construction. They should ship as a single migration, **V33** (V32 is the last M11 migration).

```sql
-- V33__presence_gated_delivery.sql

-- Per-capsule, per-recipient presence proof commitments and delivery masks.
-- Populated at sealing time by the author. One row per named recipient.
CREATE TABLE capsule_presence_recipients (
    capsule_id          UUID        NOT NULL REFERENCES capsules(id) ON DELETE CASCADE,
    connection_id       UUID        NOT NULL REFERENCES connections(id),
    -- H(K_ticket_i): stored at sealing time; used to verify proof submissions.
    proof_commitment    BYTEA       NOT NULL,   -- 32 bytes (SHA-256 hash)
    -- K_content XOR K_ticket_i: returned to winners at w2.
    delivery_mask       BYTEA       NOT NULL,   -- 32 bytes
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (capsule_id, connection_id)
);

CREATE INDEX idx_cpr_capsule ON capsule_presence_recipients(capsule_id);

-- Presence proofs submitted by recipients during [w1, w2].
-- Winning status is derived from this table at w2 evaluation time.
CREATE TABLE capsule_presence_proofs (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    capsule_id      UUID        NOT NULL REFERENCES capsules(id) ON DELETE CASCADE,
    connection_id   UUID        NOT NULL REFERENCES connections(id),
    -- H(K_ticket_i) as submitted by the recipient; matched against proof_commitment.
    submitted_proof BYTEA       NOT NULL,   -- 32 bytes (SHA-256 hash)
    proved_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (capsule_id, connection_id)     -- one proof per recipient per capsule
);

CREATE INDEX idx_cpp_capsule ON capsule_presence_proofs(capsule_id);
```

**No new columns on the existing `capsules` table are required for §1.** The window capsule timing parameters (`tlock_round`, `tlock_chain_id`, `unlock_at`) already express w1 and w2. The `C_window` and `C_content` separation is a client-side construction: the author seals a normal M11 window capsule for the window layer and a separate content capsule for the content layer; the `capsule_presence_recipients` table links the two.

**On the content capsule for §1:** The real content (`C_content`) is itself a standard M11 sealed capsule. It is addressed to the sender (or held in escrow) at sealing time. The per-winner delivery masks replace the standard ECDH-wrapped DEK path for recipients — this is handled entirely by `capsule_presence_recipients` and the winner delivery endpoint (see §2). The content capsule row exists in `capsules` with no modification to the capsule schema.

### §3 additional tables (Count-Conditional Trigger)

Two additional tables are required for §3, extending V33 or added as V34 if §3 is deferred to M13.

```sql
-- Trigger records: predicate set by Alice at sealing time; evaluated at w2.
-- One row per trigger (Alice may register multiple triggers against the same source capsule).
CREATE TABLE capsule_triggers (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    source_capsule_id       UUID        NOT NULL REFERENCES capsules(id) ON DELETE CASCADE,
    -- Structured predicate record (see §7 for policy schema).
    -- v1 minimal form: { "required_ids": [...], "min_count": N }
    predicate               JSONB       NOT NULL,
    -- The target capsule whose mask_B is conditionally released.
    target_capsule_id       UUID        NOT NULL REFERENCES capsules(id),
    -- The recipient who receives mask_B if the predicate is met.
    recipient_connection_id UUID        NOT NULL REFERENCES connections(id),
    -- DEK_B XOR K_blind_carol: stored at sealing time; released conditionally.
    mask_b                  BYTEA       NOT NULL,   -- 32 bytes
    -- Lifecycle: pending → released | destroyed
    status                  TEXT        NOT NULL DEFAULT 'pending'
        CHECK (status IN ('pending', 'released', 'destroyed')),
    evaluated_at            TIMESTAMPTZ NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ct_source ON capsule_triggers(source_capsule_id);
CREATE INDEX idx_ct_status ON capsule_triggers(status) WHERE status = 'pending';
```

**Note on `mask_b` storage security:** `mask_b` is a one-time-pad mask over `DEK_B`. The server cannot recover `DEK_B` from `mask_b` alone (it requires `K_blind_carol`, held only by Carol). However, `mask_b` must be treated as sensitive at rest — stored encrypted at the database layer if the server uses Transparent Data Encryption (TDE), and never logged. The same logging prohibition that applies to `tlock_dek_tlock` (ARCH-003 §6.5, ARCH-006 §6.2) applies to `mask_b`.

### Migration number

**§1 tables: V33** (immediately post-M11)
**§3 trigger table: V33 if §3 is built as part of M12; V34 if §3 is deferred to M13**

The tables are purely additive. They do not modify any existing M11 table and are therefore safe to deploy before or after V32 with no ordering constraint beyond V32 existing.

---

## §2 — API Surface

### New endpoints for §1

#### `POST /api/capsules/:id/presence`

Called by a named recipient during [w1, w2] to submit a presence proof.

```
POST /api/capsules/:id/presence
Authorization: Bearer <token>
Content-Type: application/json

{
  "proof": "<base64url: 32 bytes — H(K_ticket_i)>"
}
```

**Validation sequence:**
1. Authenticate caller. Must be a named recipient (present in `capsule_presence_recipients` for this capsule).
2. Load capsule. Must be sealed and have a non-null `tlock_round` (presence-gated capsules are tlock capsules by construction).
3. Verify `now() >= capsules.unlock_at` (w1 has passed). If not: HTTP 425 `{ "error": "window_not_open" }`.
4. Verify `now() < capsules.expire_at` (w2 has not passed). If not: HTTP 410 `{ "error": "window_closed" }`.
5. Decode `proof` to 32 bytes. If malformed: HTTP 422 `{ "error": "invalid_proof_format" }`.
6. Load `capsule_presence_recipients` row for `(capsule_id, caller.connection_id)`. If absent: HTTP 403 `{ "error": "not_a_presence_recipient" }`.
7. Compare `proof == proof_commitment` (constant-time comparison). If mismatch: HTTP 403 `{ "error": "proof_invalid" }`.
8. Upsert into `capsule_presence_proofs` with `UNIQUE (capsule_id, connection_id)` (idempotent on re-submission).
9. Return: HTTP 200 `{ "status": "proof_accepted" }`.

**Security note:** Step 7 uses constant-time comparison to prevent timing attacks on the proof commitment. The proof commitment is `H(K_ticket_i)` — an honest server cannot derive `K_ticket_i` from the commitment, but a timing side-channel could confirm whether a guess matches.

#### `GET /api/capsules/:id/presence/winners` (winner delivery at w2)

Called by winners at w2 to collect their delivery mask.

```
GET /api/capsules/:id/presence/winners
Authorization: Bearer <token>

Response (200 — winner):
{
  "delivery_mask": "<base64url: 32 bytes — K_content XOR K_ticket_i>"
}

Response (403 — non-winner or proof not submitted):
{
  "error": "not_a_winner"
}

Response (425 — w2 not yet reached):
{
  "error": "window_not_closed",
  "retry_after_seconds": <N>
}
```

**Gate logic:**
1. Authenticate caller.
2. `now() >= capsules.expire_at` (w2 must have passed). If not: HTTP 425.
3. Check `capsule_presence_proofs` for a valid proof from this caller. If absent: HTTP 403.
4. Return `delivery_mask` from `capsule_presence_recipients` for `(capsule_id, caller.connection_id)`.

**On mask disposal:** Delivery masks for non-winners are logically destroyed at w2 — the server can mark them `delivered_at` or `discarded_at` in `capsule_presence_recipients`. A background job that runs at w2 (see §4) records this. Physically deleting the row provides slightly stronger forward secrecy; a `discarded_at` timestamp with a NULL `delivery_mask` is the recommended approach.

### Fit with existing sealing contract

The §1 construction extends the M11 sealing request with an optional `presence` block. This is an **additive extension** — non-presence capsules omit the block and see no behaviour change.

```json
"presence": {
  "recipients": [
    {
      "connection_id": "<uuid>",
      "proof_commitment": "<base64url: 32 bytes — H(K_ticket_i)>",
      "delivery_mask":   "<base64url: 32 bytes — K_content XOR K_ticket_i>"
    }
  ]
}
```

The server inserts all rows into `capsule_presence_recipients` atomically within the existing sealing transaction (write phase step [16] of ARCH-010 §5.1). If the `presence` block is present, the server additionally validates:
- Each `connection_id` must be a named recipient in `recipient_keys[]` (a presence-gated capsule's window layer addresses the same set of people who are being presence-gated).
- `proof_commitment` is exactly 32 bytes.
- `delivery_mask` is exactly 32 bytes.
- No duplicate `connection_id` entries.

### New endpoints for §3

#### Extension to sealing request: `triggers[]`

```json
"triggers": [
  {
    "predicate": {
      "min_count": 0,
      "required_ids": []
    },
    "target_capsule_id": "<uuid of capsule B>",
    "recipient_connection_id": "<uuid — Carol's connection>",
    "mask_b": "<base64url: 32 bytes — DEK_B XOR K_blind_carol>"
  }
]
```

Inserted into `capsule_triggers` atomically within the sealing transaction. The target capsule must already exist (Alice seals B first, then seals A with the trigger referencing B's UUID).

#### `POST /api/capsules/:id/triggers/evaluate` (internal / scheduler-called)

This is not a public-facing endpoint but a server-internal operation fired by the w2 scheduled job (see §4). It is described here for completeness:
- Counts entries in `capsule_presence_proofs` for the capsule.
- For each pending trigger: evaluates the predicate against the proof count and proof set.
- If predicate met: delivers `mask_b` to the target recipient (via the normal notification/delivery channel). Sets `capsule_triggers.status = 'released'`.
- If predicate not met: sets `status = 'destroyed'` and NULLs `mask_b`.

### Fit with existing delivery system

The §1 winner delivery and §3 trigger evaluation both fire at w2. The existing M11 delivery scheduler (the job that fires at `capsules.expire_at`) is the correct hook for both. The scheduler already identifies capsules whose window has closed; extending it to check `capsule_presence_proofs` counts and evaluate `capsule_triggers` predicates is an additive concern with no structural change to the existing delivery path.

**The existing `PUT /api/capsules/:id/seal` endpoint is not broken by either addition.** The `presence` and `triggers` blocks are optional; the sealing validation sequence (ARCH-010 §5.1) is extended with new optional checks appended after step [16d], not interspersed with existing steps.

---

## §3 — Envelope Format Impact

### No new binary algorithm ID is required

The per-recipient tickets (`K_ticket_i` values) are 32-byte symmetric values stored as named fields inside the window payload JSON:

```json
{
  "bob_connection_id":     "<base64url: 32 bytes>",
  "charlie_connection_id": "<base64url: 32 bytes>"
}
```

This JSON payload is encrypted under `K_window` using the existing `aes256gcm-v1` algorithm ID. The binary envelope structure is unchanged. The envelope format version (`0x01`) does not change.

**Why no new ID is needed:** The envelope format's versioning policy (docs/envelope_format.md) states that adding a new algorithm ID is only required when introducing a new algorithm. The window layer tickets use the same `aes256gcm-v1` symmetric encryption as any other window payload — they are simply new plaintext fields inside an existing symmetric envelope, not a new cryptographic operation.

### Reservation recommended for documentation clarity

Even though no new binary algorithm ID is required, a named scheme `presence-ticket-v1` should be **reserved in `docs/envelope_format.md`** before any developer touches the implementation. This serves two purposes:

1. It names the logical format (JSON object with connection-keyed ticket values) for auditability.
2. It provides an unambiguous label for the window payload variant that contains tickets — distinguishing it from a future window payload that might contain something else.

```
| presence-ticket-v1 | M12 | Window payload containing per-recipient presence tickets.
|                    |     | Plaintext is a JSON object: { "<connection_id>": "<base64url:
|                    |     | 32-byte K_ticket_i>" }. Encrypted under K_window using
|                    |     | aes256gcm-v1. Never stored as a binary envelope; this ID
|                    |     | names the logical format only. |
```

**Action required before M12 implementation begins:** Add the `presence-ticket-v1` entry to the Reserved table in `docs/envelope_format.md` (as a logical format name, not a binary algorithm ID). This reservation must happen before any developer writes M12 crypto code.

---

## §4 — TimeLockProvider Interaction

### No changes to TimeLockProvider

The `TimeLockProvider` interface (ARCH-006) requires no changes for either §1 or §3. The tlock path for the window layer is identical to the base M11 window capsule:

- `provider.seal()` is called at sealing time with `DEK_client` (the client-side blinding mask) to produce the IBE ciphertext in `tlock_wrapped_key`.
- `provider.decrypt()` is called at delivery time to confirm the IBE gate is open.
- `provider.validate()` is called at sealing time for structural validation.

The presence-gated construction adds no new cryptographic operation to the tlock path. The window layer is sealed and unsealed identically to the base window capsule. The presence tickets are inside the window payload — the tlock mechanism encrypts and reveals `K_window`, which then decrypts the payload containing the tickets.

### Ordering of w2 events

The correct ordering of events at w2 is:

1. `capsules.expire_at` passes → scheduler job fires.
2. Custodians delete Shamir shares of `K_b`. `K_window` becomes uncomputable for new parties.
3. The scheduler evaluates `capsule_presence_proofs` (proof collection is now closed — no new proofs can be submitted because the window is closed).
4. For §1: winner delivery masks are dispatched to confirmed winners. Non-winner masks are discarded.
5. For §3: trigger predicates are evaluated against the final proof count. `mask_b` is released or destroyed.

Steps 3–5 do not require `TimeLockProvider.decrypt()` to fire. The tlock gate controls whether recipients can open the window layer during [w1, w2] — it has already opened at w1. The w2 events are driven by the scheduler, not by tlock. There is no ordering dependency between `TimeLockProvider.decrypt()` and predicate evaluation.

**Confirmation:** `TimeLockProvider` requires no changes. The interface, `StubTimeLockProvider`, and the sidecar REST contract (ARCH-006) are unchanged.

---

## §5 — Milestone Sequencing

### §1 (Presence-Gated Delivery) as M12

**Recommendation: §1 is M12 scope.** M11 schema requires no amendment. The V33 migration (§1 tables) is additive and can be deployed independently of the M11 binary. The `POST /api/capsules/:id/presence` and `GET /api/capsules/:id/presence/winners` endpoints are new surfaces that do not touch any M11 route handler.

**Dependency chain:** §1 requires M11 (sealing, tlock, window layer) to be live. M11 must ship before any presence-gated capsule can be created. There is no circular dependency and no M11 amendment.

M12 is the delivery milestone (ARCH-008 recommendation). This is the natural home for §1 because:
- The delivery scheduler (being built for M12) is the correct hook for w2 winner delivery.
- The window capsule flow (built in M11) is the prerequisite.
- Client UX for the presence-gate ceremony can be scoped to M12 alongside the broader capsule delivery UX.

### §3 (Count-Conditional Trigger) as M13

**Recommendation: §3 is M13 scope.** §3 depends on §1 (the presence proof count is what drives the predicate). It also maps naturally onto M13's posthumous delivery work:

- The dead man's switch (`count == 0` → release fallback capsule) is directly applicable to posthumous use cases — exactly what M13 targets.
- The trigger evaluation job and the M13 death-verification gate are both conditional-delivery mechanisms; they can share scheduler infrastructure.
- The executor-mediated unlock path being built for M13 provides the verification framework that gives the trigger evaluation mechanism additional legitimacy (the server's honest predicate evaluation is corroborated by the executor pathway).

**On the dead man's switch and M13 posthumous delivery:** The `count == 0` trigger is a special case of the §3 construction. It does not depend on the M13 death-verification protocol (the trigger fires based solely on proof count, not on any death signal). However, it is conceptually adjacent and its UX belongs in M13's posthumous-delivery screen. Implementing the trigger table (V33 or V34) at M12 time would allow §3 to be activated in M13 without a further schema migration.

**Recommendation on schema timing:** Add the `capsule_triggers` table in V33 (alongside the §1 tables) even if §3 UI is deferred to M13. This allows the sealing endpoint to optionally accept `triggers[]` at M12 time without a schema migration at M13. The table is empty until §3 clients start using it.

### Natural ordering between §1 and §3

§1 must precede §3. The trigger mechanism in §3 evaluates proof counts from the §1 proof table. A §3-only implementation (trigger without presence proofs) would have no count to evaluate. §1 is therefore a hard prerequisite for §3, and the sequencing M12 → M13 for §1 → §3 is correct.

---

## §6 — Chained Capsule Compatibility (ARCH-008)

### §3 generalises the chained capsule link

ARCH-008 defines `capsule_chain_links` as a directed edge from a prerequisite capsule C₁ to a successor capsule C₂. The transition event is a **puzzle solve** — the solver submits an answer, the server records a claim, and C₂ becomes available to the winner.

The §3 count-conditional trigger is a different trigger type: the transition event is **presence count evaluation** rather than a puzzle solve. Both mechanisms:
- Have a source capsule (C₁ in chained capsules; the gate capsule A in §3).
- Have one or more successor capsules (C₂; target capsule B).
- Are server-evaluated.
- Are set at sealing time and cannot be changed.

However, §3 is not merely a special case that fits unchanged into `capsule_chain_links`. The key differences are:

| Dimension | ARCH-008 chained capsule | RES-005 §3 trigger |
|---|---|---|
| Transition event | Puzzle solve (single winner) | Proof count predicate (no winner; conditional on count) |
| Winner | A specific user (first solver) | No winner — the predicate fires or doesn't |
| Target recipient | The winning solver | A fixed recipient set at sealing time (Carol) |
| Multiple targets | One C₂ per C₁ (extensible) | Multiple targets with different predicates per source |
| Key delivery | C₂'s key material is in C₁'s plaintext | `mask_b` is held server-side, not in C₁'s plaintext |
| Server blindness | Server knows C₁ winner; C₂ key is in C₁ (opaque) | Server holds `mask_b` but not `DEK_B`; Carol holds `K_blind_carol` |

### Merge or parallel mechanism?

**Recommendation: parallel mechanism with a shared conceptual parent, not a merge.**

The §3 trigger table (`capsule_triggers`) and the ARCH-008 `capsule_chain_links` table serve distinct purposes and should remain separate tables. Merging them would require either:
- A polymorphic `trigger_type` column with different semantics per type (puzzle-solve vs. count-predicate), which makes validation and querying complex.
- Or a single super-table that poorly represents both semantics.

The cleaner design is two separate tables with a shared understanding that both implement "conditional capsule delivery." The scheduler job that evaluates them at w2 can run both checks in sequence.

**However, ARCH-008 should be amended to acknowledge §3 triggers as a first-class trigger type.** The chained capsule section of ARCH-008 should add a note that:
- `capsule_chain_links` (puzzle-solve triggers) is one implementation of conditional delivery.
- `capsule_triggers` (count-predicate triggers) is a parallel implementation.
- Both are M12+ features layered on M11's window capsule foundation.
- Future work may introduce a unified "conditional delivery graph" that abstracts over both.

### Schema conflicts

There are no schema conflicts between §3 and ARCH-008. `capsule_triggers` has no FK or column conflicts with `capsule_chain_links` or `capsule_puzzles`. Both tables reference `capsules(id)` and `connections(id)` in compatible ways.

**API conflict check:** ARCH-008 proposes `POST /api/capsules/{id}/claim` for puzzle claims. §3 adds `POST /api/capsules/:id/presence` for presence proofs. These are distinct endpoints with no overlap. The sealing request body extensions (`chain` block from ARCH-008; `presence` and `triggers` blocks from RES-005) are additive JSON fields with no naming collision.

---

## §7 — Predicate Integrity and Overlapping Predicate Enforcement

### Predicate integrity

**Current trust model:** The server is trusted to evaluate predicates honestly (honest-but-curious, not Byzantine). A dishonest server could deliver `mask_b` when the predicate is false, or withhold it when true, without detection by Alice or Carol. This is the same trust assumption that governs the base window capsule (ARCH-003 §2, ARCH-008 Option 1 rationale).

**Assessment:** The honest-but-curious model is acceptable for v1 under the existing trust model. The reasons are:

1. **Consistency with M11 design.** M11 already places the server in a position where it controls proof count (through the `capsule_presence_proofs` table), delivery timing, and key material release. Adding predicate evaluation does not meaningfully expand the server's trust surface — the server already "decides" when to deliver key material.

2. **Engineering cost of alternatives is high.** On-chain predicate evaluation (smart contract) would require: a public blockchain or L2 integration, gas costs, on-chain transaction latency, and public transaction visibility. MPC predicate evaluation would require a threshold of cooperative evaluators and significant infrastructure. Neither is appropriate for a v1 consumer product.

3. **Audit mitigation.** The predicate record is set at sealing time and stored immutably in `capsule_triggers`. The proof records in `capsule_presence_proofs` are append-only with timestamps. A forensic audit can reconstruct whether the server evaluated honestly by comparing the proof count at `evaluated_at` time against the `status` field. This does not prevent dishonest evaluation but makes it detectable after the fact.

**Recommendation for v1:** Accept the honest-but-curious model. Document it explicitly in the `capsule_triggers` table comment and in the API contract for the triggers sealing extension. Note on-chain verification as a future upgrade path for a premium tier (consistent with ARCH-008's Option 3 framing).

**Engineering cost comparison:**

| Approach | Engineering cost | Trustlessness | Appropriate for |
|---|---|---|---|
| Server-evaluated (recommended v1) | Minimal (scheduler extension) | Honest-but-curious | v1 consumer product |
| On-chain smart contract | Very high (blockchain integration, gas, latency) | Trustless | Future premium tier |
| MPC evaluation | High (MPC coordinator infrastructure) | Threshold-honest | Future research direction |

### Overlapping predicate enforcement

**The sealing API should validate that predicates are structurally valid but should NOT enforce non-overlapping predicates.** The reasons:

1. **Overlap is sometimes intentional.** Alice may deliberately design overlapping predicates. For example:
   - `count == 0` → release B to solicitor (dead man's switch).
   - `count >= 1` → release C to family (engagement confirmation).
   These two predicates are exhaustive and non-overlapping by construction, but Alice might also choose:
   - `count >= 1` → release C to family.
   - `count >= 3` → release D to trustee (quorum confirmation).
   Here both could fire simultaneously (if count is 3+). This may be exactly what Alice wants — it is not an error.

2. **Non-overlapping enforcement requires solving the predicate satisfiability problem server-side.** For arbitrary predicates, determining whether any proof-set outcome satisfies multiple predicates simultaneously is non-trivial. Even for the v1 structured predicate format (`{ required_ids, min_count }`), checking mutual exclusion requires evaluating all possible proof-set outcomes, which is feasible but adds significant complexity with unclear benefit.

3. **The sender is better placed to reason about their own predicates.** Alice knows her recipients and their likely behaviour. The API cannot know whether overlapping delivery is intentional or a mistake.

**Recommended validation approach (simple and correct):**

The sealing API validates the following structural constraints on each trigger:
- `predicate` is a valid JSON object matching the v1 schema.
- `min_count` (if present) is a non-negative integer ≤ the total number of named presence recipients for the source capsule.
- All `required_ids` (if present) are `connection_id` values that appear in the source capsule's `capsule_presence_recipients` rows.
- `mask_b` is exactly 32 bytes.
- `target_capsule_id` references an existing capsule owned by the sealing user.

**If Alice wants non-overlapping enforcement:** The client (author-side) should warn when the predicates appear to have disjoint coverage (e.g. no predicate handles the case where `count == 2` out of 5 recipients). This is a UX concern for the capsule-authoring UI, not a server-side constraint. The server enforces structure; the client enforces intent.

**A practical default worth considering:** For the initial UI implementation, the capsule-authoring flow might offer two modes:
- **Branching mode** (two exhaustive, non-overlapping predicates with guidance): "If nobody checks in, release X; otherwise release Y."
- **Independent mode** (any number of triggers, potentially overlapping): for advanced users.

This is a UX design decision and does not require server-side non-overlapping enforcement.

---

## M11 Blocker Assessment

**No M11 blocker.** Both constructions can be implemented cleanly as post-M11 additions.

Specifically:

1. **V32 (the last M11 migration) does not need to be amended.** The V33 migration is independent and additive.
2. **The M11 sealing endpoint (`PUT /api/capsules/:id/seal`) does not need to be changed before M11 ships.** The `presence` and `triggers` blocks are optional extensions; the existing validation sequence handles their absence gracefully (treat null blocks as non-presence, non-trigger capsules).
3. **The `TimeLockProvider` interface is unchanged.** No M11 tlock work is affected.
4. **The envelope format (`docs/envelope_format.md`) requires only one addition before M12 implementation begins:** reserve the `presence-ticket-v1` logical format name. This is a documentation change only and does not require amending any M11 code.

The only action needed before M12 implementation starts (and before M11 ships, for hygiene) is the `presence-ticket-v1` reservation in `docs/envelope_format.md`.

---

## Summary Table

| Question | Answer | Migration / Milestone |
|---|---|---|
| §1 schema | `capsule_presence_recipients`, `capsule_presence_proofs` | V33 / M12 |
| §3 schema | `capsule_triggers` | V33 (recommended) or V34 / M12 schema, M13 UI |
| §1 API | `POST /presence`, `GET /presence/winners` + sealing extension | M12 |
| §3 API | `triggers[]` in sealing request + scheduler extension | M13 |
| Envelope format | No new binary algorithm ID; reserve `presence-ticket-v1` name | Before M12 implementation |
| TimeLockProvider | No changes required | N/A |
| §1 milestone | M12 | — |
| §3 milestone | M13 | — |
| Dead man's switch M13 dependency | No hard dependency; conceptually M13; schema can be in V33 | — |
| ARCH-008 compatibility | Parallel mechanism; no conflicts; ARCH-008 should note §3 as a parallel trigger type | — |
| Predicate integrity | Honest-but-curious model accepted for v1; on-chain as future premium tier | — |
| Overlapping predicates | Server validates structure only; client UX handles intent guidance | — |
| M11 blocker | None | — |
