# ARCH-008 — Chained Capsule and Care Mode: Feasibility Assessment

**ID:** ARCH-008
**Date:** 2026-05-16
**Author:** Technical Architect
**Audience:** CTO, PA (for STR-001 synthesis)
**Status:** Final
**Depends on:** RES-004 (chained capsule cryptographic assessment), ARCH-003 (M11 capsule
crypto brief), ARCH-006 (tlock provider interface)

---

## Overview

This brief assesses the architectural feasibility of two product directions raised in the
2026-05-16 CTO session:

1. **Chained capsules** — a DAG of time-windowed capsules where unlock of one capsule is
   conditional on a receiver completing a task inside a prior capsule.
2. **Care Mode** — E2EE monitoring for Power of Attorney holders, including geofenced alerts
   and cryptographically timestamped consent.

RES-004 established the cryptographic foundations. This brief translates those findings into
an architectural evaluation: what server infrastructure, data model changes, API extensions,
and product sequencing each direction requires.

---

## Chained Capsules

### Server-side event model options

The fundamental question is: how does the system transition from "C₁ was solved" to "C₂
becomes available"? Three models are viable.

**Option 1 — Server-orchestrated transition (recommended for v1)**

The Heirlooms server acts as the state coordinator for the capsule chain. When a solver
submits a valid puzzle answer to C₁, the server atomically records the claim and triggers
the C₂ delivery pipeline. The server maintains the chain state machine: `C₁ LOCKED →
C₁ CLAIMED (winner: user_id) → C₂ LOCKED → C₂ WINDOW OPEN → C₂ DELIVERED`.

The state machine is stored in a new `capsule_chain_links` table (see Envelope Format
Extensions below). Transitions are driven by three event sources:
- A solver submitting a valid answer (claim transition).
- The scheduler job that already fires at `unlock_at` (gate-open transition for C₂).
- The expiry job that fires at `expire_at` (cascade deletion if unclaimed).

Trust implication: the Heirlooms server is the authoritative coordinator of chain state.
A malicious server could falsely award or suppress a claim. This is acceptable under
the existing trust model — Heirlooms is already a trusted participant in capsule delivery.
The server cannot decrypt capsule content, but it does control which user receives C₂'s
key material.

**Option 2 — Client-orchestrated transition**

The C₁ winner's client, after solving the puzzle and extracting C₂'s link key (L₂) from
C₁'s plaintext, directly requests C₂'s custodians to release their shares. The server is
a relay only; chain state is not explicitly tracked server-side.

Trust implication: this eliminates server coordination, but creates a race condition — if
two clients simultaneously extract L₂ from C₁ (edge case: both opened C₁ simultaneously
before one claimed the solve), both could attempt to register as winner. Without server
coordination, there is no atomic "first write wins" enforcement. This model is weaker than
Option 1 for competitive delivery and is not recommended.

**Option 3 — On-chain smart contract mediation**

Chain state is managed by an on-chain contract (e.g. HTLC on Ethereum/L2). The server is
purely a content store; the smart contract adjudicates winner determination and key release.

Trust implication: trustless with respect to Heirlooms. However, this introduces gas costs,
on-chain transaction latency (seconds to minutes), public transaction visibility, and
significant client complexity. Appropriate for a premium high-stakes tier in the future,
not for v1 consumer delivery.

**Recommendation:** Option 1 for all v1 chained capsule work. Option 3 can be offered as a
future high-value tier if the addressable market demands it.

---

### First-solver-wins mechanism

RES-004 established that first-solver-wins exclusivity is not a native cryptographic
primitive — it requires a coordination event. The recommended design is:

**Server-mediated atomic claim:**

1. Solver submits `POST /api/capsules/{c1_id}/claim` with `{answer_hash, vtlp_proof}`.
2. Server verifies: current time within C₁'s window; `HMAC(puzzle_secret, answer) == puzzle_hash`;
   VTLP-NP proof valid (if VTLP is adopted for the puzzle format).
3. Server executes an atomic write: `INSERT INTO capsule_claims (capsule_id, winner_user_id, claimed_at) WHERE NOT EXISTS (SELECT 1 FROM capsule_claims WHERE capsule_id = ?)`. This is a single `INSERT ... WHERE NOT EXISTS` or equivalent `SELECT FOR UPDATE` with constraint violation on duplicate — standard database-level first-write-wins.
4. If the insert succeeds: the solver is the winner. Server issues a signed claim token
   (a server-signed JWT containing `{capsule_id, winner_user_id, claimed_at}`).
5. If the insert fails (a race was lost): HTTP 409 — "another solver was first."

**Race condition handling:** Two simultaneous valid submissions will produce one INSERT
success and one constraint violation. The loser receives an honest 409. No key material
is exposed to the loser. This is the same atomicity guarantee that any database-backed
distributed system uses for idempotent resource creation (e.g. payment processing).

**VTLP integration:** RES-004 recommends adopting Verifiable Time-Lock Puzzles (Xin 2025)
for the puzzle format. With VTLP, a solver can verify the puzzle is "worth solving" (a ZK
proof accompanies the puzzle, confirming its solution will unlock C₂) before committing
effort. The server verifies the VTLP proof at claim time. This does not change the
server-side claim mechanism but significantly improves the user experience and reduces
"wasted effort" from misfired attempts.

**Commit-reveal is not used:** A classic commit-reveal scheme (commit answer hash, then
reveal in a second round) would eliminate the race condition but also eliminate the
competitive racing dynamic that is core to the product mechanic. The database-atomic
single-round approach preserves the race while making it fair at millisecond resolution.

---

### QR-as-capsule-reference protocol

The QR code inside C₁ is the UX mechanism that connects C₁'s solve event to C₂'s
unlock. It encodes a capsule reference token — an encrypted pointer to C₂.

**What the token contains:**

After C₁ is decrypted and its plaintext is parsed, the client extracts the capsule
reference token from the `capsule_ref` field (see Envelope Format Extensions). The token
contains:

```json
{
  "capsule_ref": {
    "capsule_id": "<C₂ UUID>",
    "link_key": "<L₂: 256-bit base64url>",
    "link_key_role": "dek_wrap"
  }
}
```

`L₂` is the link key that wraps C₂'s Shamir custodian shares (or directly is `K_b,2`,
C₂'s upper-bound key material). See Expiry-as-death enforcement and Envelope Format
Extensions for the full key hierarchy.

**QR rendering and scan flow:**

The client renders a QR code encoding `{capsule_id: C₂_UUID, link_key: L₂}` (or a
deep-link URL: `heirlooms://capsules/{C₂_UUID}?lk={L₂_base64url}`). The winner scans
it with an Heirlooms app (or the same app auto-renders it from the parsed plaintext —
the QR is a UX affordance, not a cryptographic requirement; the client could skip the
QR and proceed directly to the claim flow). The app:

1. Presents the claim to the server: `POST /api/capsules/{c1_id}/claim` (as above).
2. On success, proceeds to the C₂ unlock flow: the server, having recorded the claim,
   releases C₂'s custodian shares to the winner during C₂'s window.

**No cryptographic novelty in the QR itself.** The QR code encodes data that is already
in the C₁ plaintext; it is purely a UX routing mechanism. The security properties are
entirely in the key hierarchy and the server-mediated claim.

---

### Expiry-as-death enforcement

**Recommended design — embed C₂'s key material inside C₁'s plaintext (Option A from
RES-004):**

C₂'s upper-bound key material (`K_b,2`, the Shamir shares or equivalent) is embedded
inside C₁'s encrypted plaintext as part of the capsule reference token. This means:

- If C₁'s window closes without a solve, C₁'s custodians delete their Shamir shares of
  `K_b,1`. C₁'s plaintext was never decrypted. `K_b,2`, embedded within C₁'s plaintext,
  is therefore inaccessible by exactly the same guarantee as C₁'s own content.
- No additional deletion protocol is needed for C₂. The cascade is automatic and inherits
  C₁'s expiry guarantee exactly.
- C₂'s tlock lower bound (`K_a,2`) gates C₂ independently of whether C₁ was solved — this
  provides time-separation between C₁'s close and C₂'s open window, preventing a solver
  from immediately consuming C₂.

**Trust boundary:** This is trust-bounded, not trustless. The expiry guarantee is:
"if the threshold of C₁'s custodians honestly deletes their shares at `expire_at`, then
C₂ is permanently inaccessible to any party who did not solve C₁ within its window."
This is the same custodian-trust model as M11's window capsule expiry. No additional trust
assumption is introduced by the chaining.

**Custodian coordination for expiry cascade:** When C₁'s expiry event fires (scheduler at
`expire_at`), the server must notify C₂'s custodians that C₂ should be cancelled if no
claim was recorded. Under Option A, this notification is largely redundant (C₂'s custodian
shares have never been released from C₁'s plaintext), but serves as an audit signal. A
new `capsule_chain_links.status = 'expired_unclaimed'` state records the cascade outcome.

**C₂ as an independent capsule with a conditional gate:** From the server's perspective,
C₂ exists as a standard capsule row in the database (created at chain-sealing time, with
its own `unlock_at`, `expire_at`, and key fields). The conditional gate is expressed as:
`capsule_chain_links.prerequisite_capsule_id = C₁_UUID`. The delivery scheduler checks
this link before surfacing C₂ to anyone — it will not deliver C₂ unless a claim token
for C₁ exists and the winner matches the C₂ recipient list.

---

### Envelope format extensions required

The existing envelope format (`docs/envelope_format.md`, binary layout version `0x01`)
requires no binary layout changes. The versioning policy explicitly states that new
algorithm IDs do not require a version bump. The following additions are required:

**New algorithm IDs to register:**

| ID | Use | Introduced |
|---|---|---|
| `capsule-chain-ref-v1` | Capsule reference token embedded in C₁ plaintext: encodes `{capsule_id, link_key, puzzle_hash}`. This is a JSON structure, not an encrypted blob — it lives inside C₁'s plaintext envelope, not as a separate outer wrapper. | M12 or later |
| `vtlp-groth16-v1` | Verifiable Time-Lock Puzzle proof attached to the puzzle definition inside C₁. A ZK proof (Groth16 instantiation) that the puzzle has a valid solution. Stored as an opaque blob alongside the puzzle. | M12 or later |
| `consent-vc-v1` | Consent capsule verifiable credential (see Care Mode section). A signed JSON-LD document, not a binary envelope. Stored in a new `consent_records` table. | Care Mode milestone |

**New schema tables required:**

```sql
-- Chained capsule link: a directed edge in the capsule DAG.
CREATE TABLE capsule_chain_links (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prerequisite_capsule_id UUID NOT NULL REFERENCES capsules(id),  -- C₁
    successor_capsule_id    UUID NOT NULL REFERENCES capsules(id),  -- C₂
    -- Status: pending (no claim yet), claimed (winner recorded), expired_unclaimed
    status                  TEXT NOT NULL DEFAULT 'pending'
        CHECK (status IN ('pending', 'claimed', 'expired_unclaimed')),
    winner_user_id          UUID NULL REFERENCES users(id),   -- set on claim
    claimed_at              TIMESTAMPTZ NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (prerequisite_capsule_id, successor_capsule_id)
);

-- Puzzle definitions embedded in capsules (for server-side claim verification).
-- The puzzle secret (for HMAC verification) is stored server-side;
-- the puzzle presentation (for users) is in C₁'s encrypted plaintext.
CREATE TABLE capsule_puzzles (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    capsule_id      UUID NOT NULL REFERENCES capsules(id) ON DELETE CASCADE,
    puzzle_hash     BYTEA NOT NULL,   -- HMAC-SHA256(puzzle_secret, correct_answer)
    vtlp_proof      BYTEA NULL,       -- optional VTLP-NP ZK proof blob
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (capsule_id)
);
```

Note: the `puzzle_secret` (used to generate `puzzle_hash`) must never be stored in the
database. It is generated by the capsule author's client, used to produce `puzzle_hash`,
and then discarded. The server verifies `HMAC(puzzle_secret, submitted_answer) == puzzle_hash`
only if the puzzle_secret is also embedded in C₁'s plaintext. Alternatively, `puzzle_hash`
can be a straight `SHA-256(correct_answer)` if the answer space is large enough to resist
brute force — a CTO decision on puzzle design.

**No changes to the binary wire format.** The `capsule_ref` token and puzzle definition are
JSON fields inside C₁'s plaintext (inside the existing envelope). The envelope format is
unchanged at the binary layer.

**Impact on ARCH-003:** The sealing request body (`PUT /api/capsules/:id/seal`) needs one
optional extension field for chain registration:

```json
{
  "chain": {
    "successor_capsule_id": "<C₂ UUID>",
    "puzzle_hash": "<base64url of HMAC-SHA256(puzzle_secret, correct_answer)>",
    "vtlp_proof": "<base64url, optional>"
  }
}
```

This is an additive extension to the M11 sealing contract. Non-chained capsules omit the
`chain` field. The server creates the `capsule_chain_links` row on sealing.

---

### Minimum viable architecture

**Two-capsule chain (simplest viable chained capsule product):**

The smallest change to the M11 foundation that supports a two-capsule chain:

1. Two new tables: `capsule_chain_links` and `capsule_puzzles` (schema above).
2. One new API endpoint: `POST /api/capsules/{id}/claim` — verifies answer, records winner.
3. Extension to the sealing request body: optional `chain` field (above).
4. Extension to the delivery scheduler: before surfacing any capsule for delivery, check
   `capsule_chain_links.status == 'claimed' AND winner_user_id == requesting_user_id` if
   the capsule has a prerequisite link.
5. Extension to the expiry job: when C₁ expires unclaimed, set
   `capsule_chain_links.status = 'expired_unclaimed'` and mark C₂ as cancelled.

Client changes required:
- Author flow: when sealing C₁, the author creates both C₁ and C₂, seals C₂ first (to
  get C₂'s UUID and key material), embeds the capsule reference token in C₁'s plaintext,
  then seals C₁ with the chain registration.
- Recipient flow: C₁ is delivered normally; after unlocking, the client parses the
  `capsule_ref` from C₁'s plaintext, renders the QR / claim UI, submits the claim.

**What a full DAG model requires additionally:**

- Recursive `capsule_chain_links` support (multiple successors per capsule; prerequisite
  chains of depth > 2).
- A DAG validation step at sealing time (cycle detection; confirming all linked capsules
  exist and are owned by the same author or have delegated chain authority).
- Fan-out delivery: C₁ having multiple successors (C₂A for winner, C₂B for runner-up, etc.).
- Fork semantics: multiple C₁ capsules each being prerequisites for a single C₂ (AND logic:
  all must be solved before C₂ unlocks).

The minimal two-capsule chain requires none of these. The `capsule_chain_links` table is
designed to accommodate full DAG semantics as a future extension without schema migration.

**M11 compatibility:** The minimum viable two-capsule chain does not require any changes to
M11's core crypto model. It is an additive feature layer on top of M11. It should not be
scheduled for M11; M11's scope is already defined (ARCH-003). The chained capsule is M12+
work.

---

## Care Mode

### Location infrastructure options

Care Mode requires continuous E2EE location tracking — a capability with no precedent in
Heirlooms' current architecture. The vault model handles large, infrequently-uploaded
media files; location tracking requires small, high-frequency data points (GPS coordinates,
timestamps) from a continuously running mobile background process.

**Option A — Heirlooms server as location relay (recommended for v1)**

The care recipient's mobile device (iOS or Android) runs a background location service.
At configurable intervals (e.g. every 5 minutes, or on significant location change), the
client encrypts the current GPS coordinate under an E2EE Care Mode key and POSTs it to a
new `POST /api/care/location` endpoint. The server stores the encrypted blob in a new
`location_events` table and delivers it to subscribed POA holder devices via push or
polling. The server sees only ciphertext.

Infrastructure delta from current:
- New `location_events` table (small rows: timestamp, capsule_id reference for key lookup,
  encrypted_payload BYTEA).
- New server endpoints: `POST /api/care/location`, `GET /api/care/location/latest`,
  `GET /api/care/location/history` (time-bounded, paginated).
- Client SDK additions: background location service (iOS CoreLocation / Android
  FusedLocationProvider), encryption at write, background-to-foreground wake for push
  delivery.
- Push notification integration for alerts (new: Heirlooms currently has no push
  infrastructure).

**Option B — Dedicated Care Mode microservice**

A separate backend service, distinct from the Heirlooms vault server, handles all location
data. The vault server issues auth tokens to the Care Mode service but does not touch
location data. The care service has its own database, its own key management, and its own
client SDK.

Advantage: strict architectural separation; vault codebase is not contaminated with
real-time telemetry patterns. Disadvantage: two services to operate, two databases, two
auth boundaries, significant additional engineering overhead. Recommended only if Care Mode
is productised as a separate SKU.

**Option C — Third-party real-time location service (Radar, Foursquare, etc.)**

Use a vendor for location collection and geofence evaluation; apply E2EE at the
application layer on top of the vendor's infrastructure. This would require the vendor
to store E2EE location blobs rather than plaintext — most vendor SDKs are not designed
for this and would require significant custom integration. Not recommended; the E2EE
constraint makes off-the-shelf location vendors a poor fit.

**Assessment:** Option A is the minimal viable approach. It reuses Heirlooms' auth and
storage infrastructure, adds one new data type and one new API surface, and maintains
the E2EE invariant. It is a significant architectural expansion (background services,
push infrastructure, real-time telemetry patterns) but not a separate product.

---

### E2EE geofencing approaches

The fundamental tension in E2EE geofencing is: geofence evaluation (does point P lie
within polygon Z?) requires knowing the plaintext coordinates. If coordinates are
encrypted and the server never decrypts, how does the server know to send an alert?

Three approaches resolve this tension with different tradeoff profiles:

**Approach 1 — Client-side evaluation (recommended for v1)**

The POA holder's device holds the Care Mode decryption key. When a new encrypted location
event arrives (via push or polling), the POA holder's client decrypts the coordinate and
evaluates the geofence locally. If the coordinate is outside the safe zone, the client
emits an in-app alert. The server never evaluates the geofence; it is a pure ciphertext
relay.

Limitation: the POA holder's device must be active (or wake on push) to evaluate the
geofence. If the POA holder's phone is off, the alert is delayed until the device comes
online. For safety-critical monitoring (e.g. an Alzheimer's patient at immediate risk),
this latency may be unacceptable.

Mitigation: push notifications can wake a background process. The app can run a background
geofence check on push receipt without the user unlocking the phone (iOS ≥ 13 background
decryption is possible if the decryption key is stored in Keychain with `.afterFirstUnlock`
accessibility class).

**Approach 2 — Approximate/bucketed coordinates (privacy-preserving server evaluation)**

The client encrypts the precise GPS coordinate for POA holders (using the full E2EE path)
but also sends a coarser bucketed representation (e.g. H3 geospatial index at a resolution
that reveals the neighbourhood but not the street address) to the server in plaintext. The
server evaluates geofence membership against the bucketed coordinate and sends alerts based
on the bucket. The POA holder's decrypted view uses the precise coordinate.

Limitation: the server learns that the care recipient is in a particular neighbourhood —
a meaningful privacy concession. For a product that brands itself on "we cannot see your
data," this is a notable inconsistency. Acceptable only if the bucket resolution is
calibrated to be below the threshold of personally identifying location (e.g. a 1km radius
cell).

**Approach 3 — Trusted execution environment (TEE) server-side evaluation**

A server-side TEE (e.g. AWS Nitro Enclaves, Azure Confidential Computing) performs
geofence evaluation inside a hardware-attested enclave. The client sends encrypted coordinates;
the enclave decrypts, evaluates, and emits only a binary alert signal (inside / outside)
without leaking the coordinate to the server process. The enclave attests its code to the
client before the client trusts it with the decryption key.

This is theoretically the strongest approach (server-side evaluation with E2EE guarantees),
but it is complex to deploy, costly, and introduces a new trust dependency (Intel/AMD/AWS).
Not appropriate for v1.

**Recommendation:** Approach 1 (client-side evaluation) for v1. It is consistent with
Heirlooms' existing trust model (server as ciphertext relay), avoids server-side geofence
infrastructure entirely, and is deployable using standard mobile background processing APIs.
Approach 2 is a pragmatic fallback if Approach 1's alert latency proves unacceptable in
user research.

---

### Consent capsule data model

The consent capsule is the cryptographic record of the care recipient's agreement to
monitoring. RES-004 established that this is a straightforward application of W3C
Verifiable Credentials (VCs). The architectural question is: how does this fit into
Heirlooms' existing data model?

**Recommended model — signed JSON document in `consent_records` table:**

The consent capsule is not an encrypted time-windowed capsule in the M11 sense. It is a
cryptographic record with three properties: immutable timestamp, revocability, and
signature by the granting party.

```sql
CREATE TABLE consent_records (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    grantor_user_id         UUID NOT NULL REFERENCES users(id),
    grantee_user_id         UUID NOT NULL REFERENCES users(id),  -- POA holder
    scope                   TEXT NOT NULL,   -- e.g. 'care_monitoring_location'
    vc_document             JSONB NOT NULL,  -- W3C VC JSON-LD document
    grantor_signature       BYTEA NOT NULL,  -- Ed25519 signature over vc_document
    timestamp_token         BYTEA NULL,      -- RFC 3161 trusted timestamp token
    revoked_at              TIMESTAMPTZ NULL,
    revocation_signature    BYTEA NULL,      -- signed revocation assertion
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

The `vc_document` contains: `{issuer: grantor_DID, subject: grantee_DID, scope, granted_at,
revocation_registry_url}`. The grantor signs with their Heirlooms signing key (a new key
type: Ed25519, to be added to the connections/identity model alongside the existing P-256
sharing keypair). The RFC 3161 timestamp token is obtained from a public Timestamp Authority
at creation time, binding the consent to a verifiable external clock.

**Revocation:** The grantor calls `POST /api/care/consent/{id}/revoke`. The server:
1. Records `revoked_at` and `revocation_signature` in `consent_records`.
2. Terminates the active Care Mode session for this grantee (revokes any active
   care monitoring tokens).
3. Queues delivery of a signed revocation notification to the grantee.

The grantor can revoke at any time. The server enforces revocation immediately — a
revoked consent record prevents the grantee from receiving further location updates.

**Capacity question (legal/operational, not cryptographic):** While the person retains
capacity, they can issue, modify, and revoke consent freely. If they lose capacity, the
consent record continues in force and only the POA holder (grantee) can terminate the
monitoring session. This distinction is a legal/operational policy — Heirlooms enforces
it by checking `revoked_at IS NULL` before serving location data, not by cryptographic
means. Whether the platform should ever allow a POA holder to terminate their own
monitoring access is a CTO/Legal decision.

**Integration with ARCH-004 connections model:** The grantor and grantee must be connected
users in the connections graph before a consent record can be created. The `grantee_user_id`
must reference a `connections` row with `status = 'accepted'`. This prevents consent being
granted to unknown parties.

**Algorithm ID reservation:** `consent-vc-v1` should be registered in `docs/envelope_format.md`
as a named scheme, even though the consent capsule is not a binary envelope — this
maintains the principle that all cryptographic formats in Heirlooms are named.

---

### Separation vs integration

The critical architectural question for Care Mode: should it be a feature within the
Heirlooms vault product, or a separate product/service?

**Arguments for integration:**

- The consent model maps naturally onto the connections graph (ARCH-004).
- The auth system, device model, and E2EE key infrastructure are reused directly.
- The Care Mode key (for encrypting location events) can be modelled as a specialised
  variant of the shared plot key (M10): one party writes, the other reads, with a server
  that relays ciphertext without decrypting.
- The consent capsule's signing key can be the user's existing Heirlooms signing key plus
  an Ed25519 extension, not an entirely new identity layer.
- Regulatory framing: integrating Care Mode into a digital legacy / family archive product
  is coherent — families who already use Heirlooms for photos and capsules are exactly the
  demographic who also need POA monitoring support.

**Arguments for separation:**

- Real-time location telemetry is architecturally unlike vault-style large-blob media
  storage. High-frequency small writes vs. low-frequency large writes are different load
  profiles and require different infrastructure.
- Push notification infrastructure (required for timely geofence alerts) does not exist in
  Heirlooms and is significant new infrastructure.
- The regulatory surface is different: location data + health context (Alzheimer's) is
  UK GDPR Article 9 special category data. Mixing this with the general vault data in the
  same service creates a broader data processing scope and a more complex privacy notice.
  A separate service with its own data processing agreement may be legally cleaner.
- Brand coherence: "family media vault" and "real-time location monitoring" are genuinely
  different product experiences. Combining them in one app risks each one undermining the
  other's brand clarity.

**Recommendation — phased approach:**

For v1 Care Mode (if the CTO decides to build it), integrate within the Heirlooms platform.
Share auth, connections, and key infrastructure. Introduce a `care` API namespace
(`/api/care/...`) that is clearly bounded but not yet separated. Use feature flags to
gate the Care Mode UI.

If Care Mode grows in complexity, regulatory scope, or user demand, extract it to a
dedicated microservice with shared auth but its own database. This extraction is easier
if the API namespace was cleanly bounded from the start.

**Do not build Care Mode as a fully separate product in v1.** The engineering overhead of
a separate app + separate service + separate onboarding flow is disproportionate to the
feature's maturity at v1. Integration first, extract later if needed.

---

## Complexity and Sequencing Recommendation

### Relative complexity

| Feature | Cryptographic complexity | Engineering complexity | Infrastructure delta |
|---|---|---|---|
| Chained capsules (two-capsule MVP) | Low — composes M11 primitives | Medium — new tables, API endpoint, client flow | Minimal — no new services |
| Chained capsules (full DAG) | Medium — DAG validation, fan-out | High — recursive chain logic, complex author UX | Minimal |
| Care Mode v1 (client-side geofencing) | Low — shared-plot-key variant | High — background services, push infrastructure, new data type | Significant — push notifications, real-time telemetry patterns |
| Care Mode v2 (server-side geofencing) | Medium — TEE or bucketing | Very high — TEE deployment or privacy-preserving computation | Very significant |

### Recommended sequencing relative to M11, M12, M13

**M11 (current focus):** No changes. M11 scope is defined and locked by ARCH-003 and
ARCH-006. Neither chained capsules nor Care Mode require M11 work. Any attempt to add
either to M11 would destabilise the milestone.

**M12 (scheduled delivery):** M12 is the delivery milestone — the moment when sealed
capsules are surfaced to recipients on their scheduled date. This is the right milestone
to introduce the two-capsule chain MVP, because:
- The delivery scheduler is being built/extended for M12 anyway.
- The expiry enforcement mechanism (for expiry-as-death) is part of M12 delivery logic.
- The claim endpoint and chain state machine are additive to M12's delivery infrastructure.
- Author UX for chained capsules can piggyback on M12's capsule creation improvements.

**M13 (posthumous delivery):** M13 adds executor-mediated unlock and death-verification.
This milestone already involves extending the delivery scheduler with conditional gates
(Shamir threshold + death verification). The full DAG model (multi-hop chains, fan-out,
AND prerequisites) is a natural M13 extension — the conditional gate logic is structurally
similar. Care Mode v1 is also a candidate for M13, as M13 already involves:
- The POA/executor model (the Care Mode grantee maps naturally to an executor role).
- Legal and consent machinery (M13 death verification is legally adjacent to M13 Care
  Mode consent).
- Push notification infrastructure (M13 posthumous delivery likely requires push).

**Post-M13 (new milestone):** Care Mode as a standalone capability with geofence alerting,
location history, and revocation flows. Full DAG chained capsules with deep chains and
commercial white-label integration.

### Sequencing summary

```
M11: Strong sealing + social recovery     [current scope — no changes]
M12: Milestone delivery + chained capsule MVP (two-capsule chain)
M13: Posthumous delivery + Care Mode v1 (client-side geofencing, consent model)
M14+: Full DAG chained capsules, Care Mode v2 (server-side geofencing),
      white-label commercial tier
```

---

## PA Summary

### Feasibility verdict — Chained capsules

**Verdict: Build on current foundation.**

The two-capsule chained capsule is architecturally feasible on the M11 foundation with
no new cryptographic primitives and no new services. It requires:
- Two new database tables (`capsule_chain_links`, `capsule_puzzles`).
- One new API endpoint (`POST /api/capsules/{id}/claim`).
- An additive extension to the sealing request body.
- Client changes for the chain-author flow and the winner claim UI.
- Three new algorithm ID registrations in `docs/envelope_format.md`.

The core mechanism (embed C₂'s key material in C₁'s plaintext; server-mediated atomic
claim; expiry cascade via C₁'s existing custodian deletion) composes directly from M11's
key hierarchy and Shamir infrastructure. No new cryptographic machinery is required.

The full DAG model (depth > 2, fan-out, AND prerequisites) requires additional schema
and validation work but no new cryptographic foundations. It is a future extension.

**Recommended milestone: M12.**

### Feasibility verdict — Care Mode

**Verdict: Significant new infrastructure — but build within the current platform (not
a separate product) for v1.**

Care Mode requires more new infrastructure than chained capsules:
- Background mobile location services (iOS CoreLocation, Android FusedLocationProvider).
- Push notification infrastructure (new: Heirlooms has none).
- A new high-frequency small-write data type (`location_events`) that is architecturally
  unlike the vault's large-blob media model.
- A consent record model (new table, W3C VC format, Ed25519 signing key extension).
- A new API namespace (`/api/care/...`) with location write, location read, consent CRUD,
  and revocation endpoints.

The cryptography for Care Mode is straightforward: the Care Mode key is a shared-plot-key
variant (one writer, multiple readers, server relays ciphertext). The consent capsule is a
W3C VC with an existing standard and prior art (UK digital LPA programme). There is no
novel cryptographic challenge in Care Mode.

The dominant cost is the infrastructure and engineering, not the cryptography. E2EE
geofencing at v1 is solved by client-side evaluation — the POA holder's device decrypts
and evaluates, the server remains a ciphertext relay. Alert latency is acceptable for
family-scale use cases (minutes, not seconds).

**Recommended milestone: M13.** Care Mode fits M13's existing scope (POA/executor model,
legal consent machinery, push infrastructure) better than M12. It should not be
attempted before M11's strong sealing is stable.

### CTO decisions needed before design can proceed

1. **Chained capsule puzzle format:** Should puzzles be VTLP-NP (Xin 2025) — providing
   a ZK proof that the puzzle has a valid solution — or a simpler hash-preimage puzzle
   with no ZK proof? VTLP adds proof-generation cost to the author flow (~1.37s per
   puzzle) and proof-verification cost to the server at claim time (~1ms). It materially
   improves solver UX and reduces wasted effort. Recommendation: adopt VTLP-NP for the
   puzzle format.

2. **First-solver-wins coordinator:** Server-mediated atomic claim (recommended) vs.
   on-chain HTLC for a trustless model. The on-chain model is a separate product tier,
   not a v1 requirement. Recommendation: server-mediated for v1, document the on-chain
   upgrade path for a future premium tier.

3. **Care Mode regulatory scope:** Does Heirlooms have the legal readiness to process
   UK GDPR Article 9 special category data (location + health context)? LEG-002 covers
   this question in detail. The architecture can be built independently of the legal
   answer (the data is E2EE and the server sees only ciphertext), but product launch
   requires legal sign-off. Engage Legal before committing to M13 Care Mode scope.

4. **Ed25519 signing keys for consent records:** The consent model requires a signing
   key type (Ed25519 is recommended). Heirlooms' current identity model uses P-256
   sharing keypairs (ARCH-004). Should Ed25519 be added to the connections/identity
   model in M11 (minimal overhead, forward-compatible) or deferred to the Care Mode
   milestone? Recommendation: reserve the Ed25519 key slot in ARCH-004's schema during
   M11, even if the key is not yet generated for all users — this avoids a schema
   migration later.

5. **Care Mode as a separate product tier:** Is Care Mode a premium feature (additional
   subscription cost) or part of the core Heirlooms offering? This affects the API design
   (feature gating, billing integration) and the long-term architectural decision on
   integration vs. separation. No architectural work blocked on this decision for M13 v1,
   but it should be resolved before public launch.
