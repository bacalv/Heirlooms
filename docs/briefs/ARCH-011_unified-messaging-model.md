# ARCH-011 — Unified Messaging Model

**ID:** ARCH-011
**Date:** 2026-05-17
**Author:** Technical Architect
**Audience:** CTO, PA, Developer team
**Status:** Proposed — awaiting CTO decisions on open questions
**Depends on:** ARCH-003 (M11 capsule crypto), ARCH-007 (E2EE tag scheme / trellis criteria)

---

## Overview

Three related messaging concepts raised by the CTO on 2026-05-16 share enough
infrastructure to be specified together:

1. **Capsule item messages** — notes/annotations attached to items inside an open
   capsule, with per-message recipient visibility chosen by the sender.
2. **Shared plot messages** — comments attached to a shared plot or to individual
   items within it, with moderation configured at plot creation.
3. **Message trellises** — applying the trellis concept to message visibility:
   label-based routing that governs which recipients (or roles) can see which messages.

This brief answers all thirteen questions posed in the task and provides a concrete
design that can be implemented incrementally.

---

## 1. Recommended Data Model

### 1.1 The `messages` table — a first-class entity

A message is a distinct first-class entity, not a special upload MIME type and not
embedded in a capsule envelope. Reasons:

- Messages are short-lived (possibly deleted by sender), while uploads are intended
  as permanent heirlooms. Mixing them in `uploads` would pollute the vault model.
- Messages need their own moderation state machine (pending/approved/rejected)
  and their own visibility controls that do not map to `plot_items` semantics.
- Embedding in a capsule envelope is inflexible: it prevents adding messages after
  initial sealing and makes per-message visibility impossible without per-message
  re-sealing of the entire envelope.

**Entity definition:**

```
message
  id               UUID PK
  author_user_id   UUID → users(id)

  -- Attachment scope: exactly one of these is non-null
  capsule_id       UUID NULL → capsules(id) ON DELETE CASCADE
  plot_id          UUID NULL → plots(id) ON DELETE CASCADE

  -- Optional: message attached to a specific item within the capsule/plot
  upload_id        UUID NULL → uploads(id) ON DELETE SET NULL

  -- Encrypted body (see §2 for key construction)
  body_ciphertext  BYTEA NOT NULL
  body_format      TEXT  NOT NULL     -- algorithm ID, e.g. 'aes256gcm-v1'

  -- Moderation
  status           TEXT  NOT NULL     -- 'pending' | 'approved' | 'rejected'
  DEFAULT 'approved'                  -- auto-approve is the plot-level default;
                                      -- see §4 for how 'pending' is set

  -- Label (tag) applied by the sender — governs message trellis access
  label_token      BYTEA NULL         -- HMAC token (same scheme as ARCH-007 tags)

  -- Deletion
  deleted_at       TIMESTAMPTZ NULL   -- NULL = not deleted; see §1.4

  created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()

  -- Exactly one of capsule_id / plot_id must be set
  CONSTRAINT message_scope CHECK (
    (capsule_id IS NOT NULL)::int + (plot_id IS NOT NULL)::int = 1
  )
```

**Recipient wrapping table (per-message recipient list):**

```
message_recipient_keys
  message_id            UUID → messages(id) ON DELETE CASCADE
  -- Opaque recipient reference: a blinded token, not a user_id (see §3.3)
  recipient_token       BYTEA NOT NULL    -- 32 bytes, derived from (sender_key, recipient_id)
  wrapped_message_key   BYTEA NOT NULL    -- message DEK wrapped to recipient's sharing pubkey
  key_format            TEXT  NOT NULL    -- 'p256-ecdh-hkdf-aes256gcm-v1'
  PRIMARY KEY (message_id, recipient_token)
```

### 1.2 Storage location

- Messages are stored in the new `messages` table in PostgreSQL.
- The body ciphertext is stored inline in BYTEA (messages are short; no GCS/S3
  object needed).
- Maximum plaintext body size: **64 KB** enforced server-side at the API layer.
  This is generous for any annotation use case and keeps the DB row well within
  PostgreSQL's TOAST threshold.
- Thumbnails, attachments, and media are always uploads; a message can reference
  an `upload_id` but does not replace the upload model.

### 1.3 Label storage (message trellises)

A message may carry a single label, stored as an HMAC token (same derivation as
ARCH-007 tag tokens). The plaintext label string is encrypted in the message body
ciphertext alongside the message text — the server never sees label values. The
server only sees the opaque `label_token` for trellis routing. Multiple labels per
message are a future extension (initially one label per message is sufficient).

### 1.4 Deletion semantics

Senders can delete their own messages. The server sets `deleted_at` and the body
ciphertext is zeroed (replaced with a zero-length envelope). Recipients who have
already decrypted the message retain their local copy — the server cannot retroactively
erase decrypted plaintext from devices. This is an explicit design trade-off:
messages are not ephemeral secrets; they are annotations that can be retracted from
the shared surface.

Owner/moderators can also delete any message in a plot they own.

---

## 2. E2EE Construction

### 2.1 Key hierarchy overview

There are three visibility scopes, each with a different key:

| Scope | Who can read | Key material |
|---|---|---|
| Capsule item message (broadcast) | All capsule recipients | Derived from capsule master key |
| Capsule item message (selective) | Sender-chosen subset of recipients | Per-message DEK, wrapped per recipient |
| Shared plot message (all members) | All current plot members | Plot key (`plot_key`) |
| Shared plot message (selective) | Sender-chosen subset of members | Per-message DEK, wrapped per recipient |
| Message trellis (label-gated) | Members with matching trellis | Per-message DEK, wrapped per trellis recipient |

**Design rule:** use the simplest key that satisfies the visibility requirement.
Reserve per-message DEK wrapping for selective visibility only.

### 2.2 Capsule item messages

#### Broadcast (all recipients)

A new HKDF-derived sub-key of the capsule master key is used:

```
capsule_message_key = HKDF-SHA256(
    ikm  = capsule_master_key,
    salt = [],
    info = UTF-8("capsule-message-v1")
)
```

This is a 256-bit symmetric AES-GCM key. All messages on an open capsule that are
visible to all recipients are encrypted under this key. The sender derives it from
their copy of `capsule_master_key` (the same key that wraps item DEKs in M11).

**New algorithm ID:** `capsule-message-aes256gcm-v1` — symmetric AES-256-GCM, keyed
by `capsule_message_key`. Format: standard Heirlooms symmetric envelope (version
`0x01`, 12-byte random nonce, variable ciphertext, 16-byte auth tag).

Recipients decrypt using their unwrapped copy of `capsule_master_key`, then
re-derive `capsule_message_key` with the same HKDF call.

#### Selective (sender-chosen recipients)

When the sender selects a subset of recipients:

1. Client generates a fresh 256-bit random **message DEK**.
2. For each selected recipient, wraps the DEK under that recipient's P-256 sharing
   pubkey using `p256-ecdh-hkdf-aes256gcm-v1`.
3. Inserts one row into `message_recipient_keys` per recipient (see §3.3 for
   blinding the recipient identity).
4. Message body is encrypted under the message DEK using `aes256gcm-v1`.

**New algorithm ID:** `message-aes256gcm-v1` — symmetric AES-256-GCM, keyed by a
per-message DEK. Same binary format as `aes256gcm-v1`; the distinct ID signals to
the client that this ciphertext requires DEK lookup rather than a derived key.

### 2.3 Shared plot messages

#### Broadcast (all members)

The existing `plot_key` (AES-256-GCM, established at plot creation) encrypts the
message body. No new key material is needed.

**Algorithm used:** `plot-aes256gcm-v1` (already registered in M10).

The sender encrypts the message body under `plot_key` and POSTs it. All members
who hold their `wrapped_plot_key` can decrypt.

#### Selective (subset of members)

Same per-message DEK + per-recipient wrapping as capsule selective messages above.
The sender selects recipients from the plot member list. The `message_recipient_keys`
table holds the wrapped copies.

### 2.4 Message trellis messages

A message trellis governs visibility by label rather than by explicit recipient list.
The sender assigns a `label_token` to the message. A message trellis specifies which
label tokens are accessible to which roles or users. At read time, the server returns
only messages where the requesting user's message trellis grants access to the
`label_token`.

For E2EE, a message trellis uses the **plot key path** for shared plot contexts and
the **capsule message key path** for capsule contexts. Label-gated visibility is
enforced at the server's query layer (the server does not serve the message row at
all if the label_token is not on the requesting user's accessible list); the
cryptographic layer ensures that even if the server misbehaved and returned the row,
the recipient could not decrypt without the correct key.

Message trellises with selective per-label DEKs are a future extension (see open
questions). For v1, label routing is a server-side access gate layered on top of the
broadcast key for that scope.

---

## 3. Message Trellis Design

### 3.1 Relationship to the existing trellis model

The existing `trellises` table (V30 rename of `flows`) routes **uploads** from a
criteria expression into a target plot. The routing is evaluated at upload time
(both in `runUnstagedTrellisesForUpload` and in the staging approval flow).

A **message trellis** is a conceptually related but distinct entity: it governs
which users can *read* messages on a plot, based on a label attached to the message.
It does not route messages between plots; it gates access within a single plot.

**Decision: message trellises are a new entity, not an extension of `trellises`.**

Reasons:
- Existing trellises have a `target_plot_id` (routing destination); message trellises
  have a `source_plot_id` (scope of governance). The directionality is opposite.
- Existing trellises run at upload time (write path). Message trellises are evaluated
  at read time (query path).
- Mixing both semantics in one table would require nullable columns for
  incompatible fields and complicate the `CriteriaEvaluator`.

The word "trellis" is preserved in the name because the concept is architecturally
analogous: a structured set of criteria that governs what flows where. A message
trellis is a trellis in the conceptual sense — not a `trellises` table row.

### 3.2 Message trellis schema

```sql
-- V33__message_trellises.sql

CREATE TABLE message_trellises (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id  UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- Scope: the plot this trellis governs (capsule message trellises are a future extension)
    plot_id        UUID        NOT NULL REFERENCES plots(id) ON DELETE CASCADE,

    name           TEXT        NOT NULL,

    -- Which label tokens this trellis grants access to (NULL = grants access to all labels)
    label_token    BYTEA       NULL,

    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Which users a message trellis grants access to
CREATE TABLE message_trellis_members (
    trellis_id   UUID  NOT NULL REFERENCES message_trellises(id) ON DELETE CASCADE,
    user_id      UUID  NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (trellis_id, user_id)
);

CREATE INDEX idx_message_trellises_plot ON message_trellises(plot_id);
CREATE INDEX idx_message_trellis_members_user ON message_trellis_members(user_id);
```

### 3.3 Recipient identity blinding (server-side graph protection)

The `message_recipient_keys` table uses a `recipient_token` instead of `user_id`
to avoid leaking the social graph to a compromised server. The token is derived as:

```
recipient_token = HMAC-SHA256(
    key  = message_recipient_blind_key,
    data = recipient_user_id_bytes (16 bytes, raw UUID)
)

message_recipient_blind_key = HKDF-SHA256(
    ikm  = sender_master_key,
    salt = message_id_bytes (16 bytes, raw UUID),
    info = UTF-8("message-recipient-blind-v1")
)
```

The blind key is per-message (uses `message_id` as HKDF salt), so the same
recipient appears under a different token in each message. The server cannot
correlate which user_id holds a given wrapped key across messages.

At read time, the client computes `recipient_token` from the requesting user's
`user_id` and the message's `message_id`, then queries `message_recipient_keys`
for that token. The server can serve the wrapped key row without knowing which
user it belongs to.

**Residual risk:** the server knows the *count* of recipients per message (number
of `message_recipient_keys` rows). This is accepted metadata leakage, consistent
with the tag cardinality residual risk documented in SEC-012.

### 3.4 Relationship between message trellises and labels (tags)

A message trellis is a trellis whose criteria filter on `label_token` rather than on
upload metadata (date, media type, etc.). The label is applied to a message by its
sender at write time. The message trellis is configured by the plot owner at plot
creation or at any later point.

This is analogous to how an upload trellis uses `{"type":"tag","token":"..."}` as its
criteria: the message trellis uses `label_token` as its criteria. The parallel is
intentional.

A message with no `label_token` is visible to all plot members (subject to moderation
state). A message with a `label_token` is visible only to: (a) the message author,
(b) users who are members of a `message_trellis` whose `label_token` matches, (c) the
plot owner (always has access, as moderator).

### 3.5 Interaction with the existing trellis staging model

Message trellises govern **read access** to messages. The existing trellis staging
model governs **write routing** of uploads into plots. They are orthogonal: a user
with a message trellis granting access to label "family-only" can read those messages
regardless of how their own uploads were routed into the plot.

The two mechanisms share the same conceptual vocabulary (trellis = structured access
criteria) but are implemented in separate tables and evaluated at different points
in the request lifecycle.

---

## 4. Moderation Flow

### 4.1 Plot-level moderation setting

At shared plot creation, the owner sets a `message_policy` on the `plots` table:

```sql
ALTER TABLE plots
    ADD COLUMN messages_enabled   BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN message_policy     TEXT    NULL
        CHECK (message_policy IN ('auto_approve', 'owner_approve'));
    -- NULL when messages_enabled = false
    -- 'auto_approve': messages go directly to status='approved'
    -- 'owner_approve': messages start at status='pending'
```

**Design rationale:** this is a per-plot setting, not a per-trellis or per-message
setting. The plot owner sets a single policy for the entire plot at creation. This
matches the existing staging model where `requires_staging` is per-trellis (and
enforced to `true` for shared/public plots). Messages have simpler semantics —
there is no "flow" concept for messages — so a per-plot boolean is appropriate.

### 4.2 Auto-approve path (`message_policy = 'auto_approve'`)

On `POST /api/plots/:id/messages`:
1. Server validates the sender is a plot member.
2. Server inserts message row with `status = 'approved'`.
3. Message is immediately readable by permitted members.

No moderation step. The plot owner trusts all members to self-moderate.

### 4.3 Owner-approve path (`message_policy = 'owner_approve'`)

On `POST /api/plots/:id/messages`:
1. Server inserts message row with `status = 'pending'`.
2. Message is not returned in member read queries.
3. Owner calls `GET /api/plots/:id/messages/pending` — sees all pending messages
   (owner can decrypt them; owner holds the plot key).
4. Owner calls `POST /api/plots/:id/messages/:msgId/approve` or `.../reject`.
   - Approve: sets `status = 'approved'`.
   - Reject: sets `status = 'rejected'` (message is soft-deleted from member view;
     the row remains for audit purposes).

**State machine:** `pending → approved` or `pending → rejected`. No transitions back
from approved to pending (approved messages are committed to the shared surface).
The owner can delete an approved message entirely (`deleted_at` path).

### 4.4 Reuse of existing trellis staging model

The moderation state machine for messages mirrors the trellis staging model
(`plot_staging_decisions`: pending/approved/rejected, revisable). However, messages
encode their moderation state directly in `messages.status` rather than in a separate
`plot_staging_decisions` row. This avoids a join on every message read and keeps the
staging decisions table scoped to upload routing (its original purpose).

The conceptual parallel is preserved: auto-approve messages behave like trellises
with `requires_staging = false`; owner-approve messages behave like trellises with
`requires_staging = true`.

---

## 5. Platform Impact Assessment

### 5.1 Server

**New migrations:**

- `V33__messages.sql` — `messages` table, `message_recipient_keys` table,
  `messages_enabled`/`message_policy` columns on `plots`.
- `V34__message_trellises.sql` — `message_trellises` and `message_trellis_members` tables.

**New domain types:**

- `MessageRecord.kt` — domain model for a message row.
- `MessageRecipientKey.kt` — domain model for a wrapped recipient key row.
- `MessageTrellisRecord.kt` — domain model for a message trellis.

**New repositories:**

- `MessageRepository.kt` — CRUD + moderation queries for `messages` and
  `message_recipient_keys`. Key queries:
  - `listMessages(plotId, userId, labelTokens)` — returns approved messages
    where `label_token IS NULL OR label_token IN (accessible label tokens)`,
    plus messages authored by `userId`.
  - `listPendingMessages(plotId, ownerUserId)` — returns all `status='pending'`
    messages for the plot owner.
  - `insertMessage(...)` — with `status` set based on `message_policy`.
- `MessageTrellisRepository.kt` — CRUD for `message_trellises` and
  `message_trellis_members`.

**New services:**

- `MessageService.kt` — business logic for message CRUD, moderation, label access
  evaluation.
- `MessageTrellisService.kt` — business logic for trellis CRUD.

**New routes:**

| Method | Path | Purpose |
|---|---|---|
| `GET`    | `/api/plots/:id/messages` | List approved messages (member reads) |
| `POST`   | `/api/plots/:id/messages` | Post a message |
| `DELETE` | `/api/plots/:id/messages/:msgId` | Author soft-delete or owner hard-delete |
| `GET`    | `/api/plots/:id/messages/pending` | Owner: list pending messages |
| `POST`   | `/api/plots/:id/messages/:msgId/approve` | Owner: approve |
| `POST`   | `/api/plots/:id/messages/:msgId/reject` | Owner: reject |
| `GET`    | `/api/capsules/:id/messages` | List capsule item messages |
| `POST`   | `/api/capsules/:id/messages` | Post a capsule item message |
| `DELETE` | `/api/capsules/:id/messages/:msgId` | Author soft-delete |
| `GET`    | `/api/plots/:id/message-trellises` | List message trellises for a plot |
| `POST`   | `/api/plots/:id/message-trellises` | Create message trellis |
| `PUT`    | `/api/plots/:id/message-trellises/:tId` | Update message trellis |
| `DELETE` | `/api/plots/:id/message-trellises/:tId` | Delete message trellis |

**Estimated scope:** medium. Three new tables, two services, two repository classes,
a set of routes, and modest `PlotHandler` changes for the new `plots` columns.

### 5.2 Android

**New crypto:**

- `VaultCrypto.kt` — add:
  - `deriveCapsuleMessageKey(capsuleMasterKey: ByteArray): ByteArray` (HKDF
    with `"capsule-message-v1"` info string)
  - `encryptMessageBody(plaintext: ByteArray, key: ByteArray): ByteArray`
  - `decryptMessageBody(envelope: ByteArray, key: ByteArray): ByteArray`
  - `deriveMessageRecipientBlindKey(masterKey: ByteArray, messageId: UUID): ByteArray`
  - `computeRecipientToken(blindKey: ByteArray, recipientUserId: UUID): ByteArray`
  - `wrapMessageDek(dek: ByteArray, recipientPubkey: ByteArray): ByteArray`
  - `unwrapMessageDek(wrappedDek: ByteArray, ownSharingPrivkey: ByteArray): ByteArray`

**New UI:**

- `MessagesScreen.kt` — message list and compose for both capsule and plot contexts.
- `MessageTrellisScreen.kt` — manage message trellises (plot owner only).

**API:**

- `HeirloomsApi.kt` — new endpoints for messages and message trellises.

**Estimated scope:** medium-large. Crypto primitives are straightforward extensions
of existing patterns. The UI surface (compose, thread view, moderation) is the
dominant cost.

### 5.3 Web

**New crypto (`vaultCrypto.js`):**

- `async function deriveCapsuleMessageKey(capsuleMasterKey)`
- `async function encryptMessageBody(plaintext, key)`
- `async function decryptMessageBody(envelope, key)`
- `async function deriveMessageRecipientBlindKey(masterKey, messageId)`
- `async function computeRecipientToken(blindKey, recipientUserId)`
- `async function wrapMessageDek(dek, recipientPubkeySpki)`
- `async function unwrapMessageDek(wrappedDek, ownSharingPrivkey)`

**New UI:**

- `MessagesPanel.jsx` — embedded thread view for plot and capsule item contexts.
- `MessageCompose.jsx` — compose widget with label picker and recipient selector
  (for selective visibility).
- `MessageTrellisModal.jsx` — owner creates/edits message trellises.
- `ModerationQueue.jsx` — owner: pending messages list with approve/reject actions.

**Estimated scope:** medium. The cryptographic additions are small (same patterns as
M9/M10 sharing). The UI is the main cost — compose, thread, and moderation surfaces
are new interaction patterns for the web app.

### 5.4 iOS

iOS is currently at the M11 scaffold stage (IOS-001 in queue for QR scanner).
Messaging is M12+ work. The crypto primitives follow the same CryptoKit patterns
established for M10/M11:

- `EnvelopeCrypto.swift` — add `deriveCapsuleMessageKey`, `encryptMessageBody`,
  `decryptMessageBody`, and message DEK wrap/unwrap functions.
- SwiftUI views for message thread and compose.

**Estimated scope:** medium. Parallel to Android, leveraging the same CryptoKit
primitives. Deferred until after M11 iOS work is complete.

---

## 6. Open Questions for CTO

The following require product decisions before implementation. They are ordered by
priority: items marked **blocking** cannot be deferred past initial implementation;
items marked **deferrable** can be resolved in a follow-up iteration.

### OQ-1 [blocking] — Which milestone does messaging land in?

ARCH-011 is a design-only document. The features it describes span M10's shared
plot infrastructure (which is current) and M11's capsule key model (which is in
progress). There are two realistic options:

**Option A — Post-M11, dedicated milestone (recommended).**
Messaging on shared plots reuses M10's `plot_key` and requires only M10 to be
stable. Capsule messages require M11's `capsule_master_key` model (ARCH-003).
Implementing both in one milestone (post-M11) avoids splitting a coherent feature
across milestones. The milestone would be M12 or an M11.5 increment.

**Option B — Shared plot messages in M10 E5, capsule messages in M12.**
Shared plot messages are self-contained within M10's key infrastructure. This would
add an E5 increment to M10. Capsule messages would wait for M11.

Recommendation: Option A. Messaging is a coherent user-facing feature; splitting it
degrades the product experience and creates a second-class capsule messaging surface.

### OQ-2 [blocking] — Selective visibility: opt-in or the default?

Per-message selective visibility (choosing a recipient subset) requires per-message
DEK generation and per-recipient key wrapping. This is more complex client-side and
adds latency to message posting.

Options:
- **A: Broadcast only for v1.** All messages go to all permitted members (using
  `plot_key` or `capsule_message_key`). Selective visibility is a future extension.
  Simplest to implement; acceptable for family-scale shared plots.
- **B: Selective as an opt-in.** Sender can choose "all members" (broadcast) or
  select specific recipients. Default is broadcast; selective is one extra step.
- **C: Selective is the only model.** Every message has an explicit recipient list.

Recommendation: Option B. Broadcast as the default, selective as an opt-in.
The implementation must support both paths (the schema is designed for both), but
the UX defaults to broadcast. This avoids accidental over-sharing while keeping
the common case simple.

### OQ-3 [blocking] — Care Mode executor messages (ARCH-008 interaction)

ARCH-008 describes Care Mode where a Power of Attorney (POA) holder monitors a care
recipient. ARCH-008 noted that "the Care Mode key is a shared-plot-key variant."

Can a Care Mode executor leave messages for recipients? If so:

- Care Mode context is distinct from open capsules and shared plots. The message
  scope would be `care_session_id`, not `capsule_id` or `plot_id`.
- The `messages` table constraint enforces exactly one of `capsule_id`/`plot_id`.
  A Care Mode message scope would require a third nullable column and an updated
  constraint.

**Recommendation:** Do not include Care Mode messages in this milestone. Care Mode
itself is deferred to M13 (ARCH-008 sequencing). If Care Mode proceeds, extend
the `messages` table then with a `care_session_id` column. The constraint
structure already anticipates this pattern.

**CTO decision needed:** confirm that Care Mode executor messaging is M13+ scope.

### OQ-4 [deferrable] — Per-label DEKs for message trellises

The v1 design uses `label_token` as a server-side access gate on top of the
broadcast plot key. A stronger model would give each label its own DEK, so that
even a compromised server cannot serve the ciphertext to unauthorized recipients.

This requires:
- A `message_trellis_key` per label, wrapped to each trellis member's sharing pubkey.
- A new algorithm ID: `message-trellis-aes256gcm-v1`.
- Client-side trellis key management (analogous to plot key management).

This is more complex but follows the same pattern as the plot key. The current
schema (`message_recipient_keys`) can be extended to support trellis key wrapping
in a later increment without schema migration.

**CTO decision needed:** is server-side label gating sufficient for v1, or is
per-label E2EE required from day one?

**Recommendation:** Server-side gating is sufficient for v1. The threat model for
labelled messages within a shared plot is already bounded by plot-level E2EE.
Per-label DEKs can be added incrementally.

### OQ-5 [deferrable] — Message retention and read receipts

Can a sender delete a message after it has been read? The schema supports soft
deletion (`deleted_at`). The product question is:

- Does "deleted" mean the server stops serving it (yes, immediately on `deleted_at`)?
- Do recipients who already decrypted it lose access? (No — see §1.4; this is
  explicitly not possible without device-side key destruction, which is out of scope.)
- Are read receipts in scope? Knowing whether a recipient decrypted a message requires
  either a server-side acknowledgement endpoint or client-side beacon. The server
  can infer a read from `GET /api/plots/:id/messages` calls, but cannot confirm
  decryption. Explicit read receipts are not recommended for v1 (privacy implications).

**CTO decision needed:** confirm the deletion policy (soft delete / body zeroing)
is acceptable, and confirm read receipts are out of scope for v1.

### OQ-6 [deferrable] — Message ordering and threading

Are messages a flat list per plot/capsule, or do they support threaded replies?

The schema as designed supports a flat list (ordered by `created_at`). Threading
would require a `parent_message_id UUID NULL → messages(id)` column. This is a
schema-level decision that is easier to get right in v1 than to retrofit.

**CTO decision needed:** flat list or threaded for v1?

**Recommendation:** flat list for v1. Threading adds significant UI complexity.
Reserve a `parent_message_id` column (nullable, initially NULL for all rows) so
threading can be added without migration.

---

## 7. New Algorithm IDs

The following algorithm IDs must be registered in `docs/envelope_format.md` before
any implementation begins:

| ID | Use | Introduced |
|---|---|---|
| `capsule-message-aes256gcm-v1` | Message body encrypted under `capsule_message_key` (HKDF-derived from capsule master key with info `"capsule-message-v1"`) | Messaging milestone |
| `message-aes256gcm-v1` | Message body encrypted under a per-message DEK (selective visibility) | Messaging milestone |

`plot-aes256gcm-v1` (already registered in M10) is reused for shared plot broadcast
messages. No new algorithm ID is needed for that path.

---

## 8. Schema Sketch (Full)

```sql
-- V33__messages.sql

-- Moderation settings on plots
ALTER TABLE plots
    ADD COLUMN messages_enabled  BOOLEAN  NOT NULL DEFAULT FALSE,
    ADD COLUMN message_policy    TEXT     NULL
        CHECK (message_policy IN ('auto_approve', 'owner_approve'));

-- First-class message entity
CREATE TABLE messages (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    author_user_id   UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- Scope (exactly one non-null)
    capsule_id       UUID        NULL REFERENCES capsules(id) ON DELETE CASCADE,
    plot_id          UUID        NULL REFERENCES plots(id) ON DELETE CASCADE,

    -- Optional item attachment
    upload_id        UUID        NULL REFERENCES uploads(id) ON DELETE SET NULL,

    -- Encrypted body
    body_ciphertext  BYTEA       NOT NULL,
    body_format      TEXT        NOT NULL,   -- 'capsule-message-aes256gcm-v1' | 'plot-aes256gcm-v1' | 'message-aes256gcm-v1'

    -- Moderation
    status           TEXT        NOT NULL DEFAULT 'approved'
                                 CHECK (status IN ('pending', 'approved', 'rejected')),

    -- Label token for message trellis routing (NULL = no label; visible to all members)
    label_token      BYTEA       NULL,

    -- Deletion (sender or owner can soft-delete)
    deleted_at       TIMESTAMPTZ NULL,

    -- Optional: reserve threading column now, NULL for v1
    parent_message_id UUID       NULL REFERENCES messages(id) ON DELETE SET NULL,

    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT message_scope CHECK (
        (capsule_id IS NOT NULL)::int + (plot_id IS NOT NULL)::int = 1
    )
);

CREATE INDEX idx_messages_plot    ON messages(plot_id, status, created_at)
    WHERE plot_id IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX idx_messages_capsule ON messages(capsule_id, created_at)
    WHERE capsule_id IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX idx_messages_author  ON messages(author_user_id);

-- Per-recipient wrapped keys for selective-visibility messages
CREATE TABLE message_recipient_keys (
    message_id           UUID   NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    -- Blinded recipient token (not a user_id; see §3.3)
    recipient_token      BYTEA  NOT NULL,
    wrapped_message_key  BYTEA  NOT NULL,
    key_format           TEXT   NOT NULL,   -- 'p256-ecdh-hkdf-aes256gcm-v1'
    PRIMARY KEY (message_id, recipient_token)
);

-- V34__message_trellises.sql

-- Message trellises: label-based read access control within a plot
CREATE TABLE message_trellises (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id  UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    plot_id        UUID        NOT NULL REFERENCES plots(id) ON DELETE CASCADE,
    name           TEXT        NOT NULL,
    -- Which label token this trellis grants access to (NULL = all labels)
    label_token    BYTEA       NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Members of a message trellis (users who can read messages with the trellis label)
CREATE TABLE message_trellis_members (
    trellis_id  UUID NOT NULL REFERENCES message_trellises(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (trellis_id, user_id)
);

CREATE INDEX idx_message_trellises_plot ON message_trellises(plot_id);
CREATE INDEX idx_mtm_user ON message_trellis_members(user_id);
```

---

## 9. Interaction with ARCH-003, ARCH-007, ARCH-008

**ARCH-003 (M11 capsule crypto):** The `capsule_message_key` HKDF derivation
requires the capsule master key to be available on the client device. This is
already the case for the capsule author (who holds the master key). Recipients
gain access to the capsule master key only after the capsule is unsealed (M11).
Capsule item messages are therefore only writeable to open capsules (where the
author still holds the master key) and readable by recipients after unsealing.

**ARCH-007 (E2EE tag scheme):** The `label_token` on `messages` uses the same
HMAC derivation scheme as upload tag tokens:

```
label_token = HMAC-SHA256(
    key  = HKDF-SHA256(master_key, salt=[], info=UTF-8("message-label-v1")),
    data = UTF-8(label_value)
)
```

A new HKDF context string (`"message-label-v1"`) keeps message labels
cryptographically separate from upload tags. The display name for a label is
encrypted in the message body by the sender; the server stores only the token.

**ARCH-008 (Care Mode):** Care Mode executor messages are out of scope for this
brief. The `messages` table is designed to accommodate a future `care_session_id`
scope column if Care Mode proceeds.

---

## 10. Implementation Order Recommendation

The following sequence minimises risk and keeps increments shippable:

1. **Server V33 schema migration (additive).** Deploy `messages` and
   `message_recipient_keys` tables plus `plots` column additions. No client
   changes required. Gate the messaging API behind a feature flag.

2. **Server API — shared plot broadcast messages.** `POST/GET/DELETE` on
   `/api/plots/:id/messages`. Uses existing `plot_key` for encryption.
   Moderation (`auto_approve` / `owner_approve` paths). No selective visibility
   yet. This is the simplest, most self-contained increment.

3. **Web UI — shared plot message thread + compose.** Reuses `vaultCrypto.js`
   plot key handling from M10. Broadcast messages only.

4. **Server V34 schema migration.** `message_trellises` and
   `message_trellis_members` tables. Message trellis CRUD endpoints.

5. **Web UI — message trellis management.** Owner creates and assigns trellises;
   members see label-filtered message views.

6. **Server + client — selective visibility.** Per-message DEK path. Requires
   new crypto functions (`wrapMessageDek`, `unwrapMessageDek`, recipient token
   blinding). Ship on all platforms together (web first, then Android, then iOS).

7. **Server API — capsule item messages.** Requires M11 capsule master key
   model to be stable. `POST/GET/DELETE` on `/api/capsules/:id/messages`. Uses
   `capsule_message_key` derivation.

8. **Android + iOS — full messaging.** UI and crypto for all message types.

---

## PA Summary

ARCH-011 specifies a unified messaging model that satisfies all thirteen questions
from the task. The key design decisions are:

1. **Messages are a first-class entity** in a new `messages` table. Not uploads,
   not embedded in envelopes. Stored inline in BYTEA (64 KB max).

2. **Three key paths** cover all visibility scopes: `capsule_message_key`
   (HKDF from capsule master key, for broadcast capsule messages), `plot_key`
   (existing M10 key, for broadcast plot messages), and per-message DEK with
   per-recipient wrapping (for selective visibility in both contexts).

3. **Message trellises are a new entity** (`message_trellises` table), not rows
   in the existing `trellises` table. They govern read access by label, not write
   routing. Conceptually analogous to upload trellises; mechanically distinct.

4. **Moderation reuses the conceptual model** of the trellis staging state machine
   (`auto_approve` = requires_staging false; `owner_approve` = requires_staging true)
   but is implemented as a `status` column on `messages`, not in
   `plot_staging_decisions`.

5. **Recipient identity blinding** uses per-message HMAC tokens to prevent the
   server from building a social graph from `message_recipient_keys`.

6. **Six open questions** require CTO decisions, two of which are blocking for
   implementation: which milestone messaging lands in (OQ-1) and whether selective
   visibility is an opt-in or the only model (OQ-2).

**New algorithm IDs to register:** `capsule-message-aes256gcm-v1`,
`message-aes256gcm-v1`.

**No binary envelope format changes.** Both new IDs use the existing symmetric
envelope layout (`envelope_version = 0x01`).
