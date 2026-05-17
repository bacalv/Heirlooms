---
id: ARCH-011
title: Unified messaging model — capsule messages, shared plot messages, and message trellises
category: Architecture
priority: Medium
status: queued
depends_on: [ARCH-003, ARCH-007]
touches:
  - docs/briefs/
assigned_to: TechnicalArchitect
estimated: half day
---

## Background

Three related concepts proposed by CTO (2026-05-16) that share common infrastructure:

1. **Capsule item messages** — users can attach messages (notes, annotations) to items
   inside an open capsule. Visibility is configurable per message: the sender chooses
   which recipients can see each message.

2. **Shared plot messages** — users can add messages to a shared plot and to individual
   items within it. Moderation is configurable at plot creation:
   - A "allow messages" toggle in shared plot creation options
   - An owner setting: "auto-approve messages" vs "owner must approve each message"
   (This mirrors the existing trellis staging model applied to messages rather than media.)

3. **Message trellises** — applying the trellis concept to message visibility. Rather
   than a trellis governing when media flows to a shared plot, a message trellis governs
   which recipients (or roles) can see which messages. Tags (labels) can be applied to
   messages; message trellises govern access by label.

## Key questions for the TA to answer

### Data model
1. What is a "message" as a first-class entity? Does it have its own envelope (E2EE),
   or does it share the parent item's DEK?
2. Where are messages stored — in the `uploads` table (as a special MIME type), in a
   separate `messages` table, or embedded in a capsule envelope?
3. How are per-message visibility choices represented server-side without leaking
   recipient identity?

### E2EE implications
4. Messages on capsule items must be E2EE — the server must not see plaintext. What
   key material encrypts a message? Per-message DEK (new), parent item DEK (reuse), or
   a new message key derived from the capsule master key?
5. For shared plot messages: the plot key is shared with all plot members. Does a
   message use the plot key (all members can read all messages), or per-message
   recipient wrapping (selective visibility)?
6. If per-message visibility is supported, how does the sender specify recipients
   without the server learning the social graph?

### Message trellises
7. Can the existing `Trellis` / `TrellisService` infrastructure be extended to handle
   message routing, or does message visibility require a separate concept?
8. How do "message trellises" interact with the existing trellis staging model? Are
   they the same entity or distinct?
9. What is the relationship between message trellises and labels (tags)? Is a message
   trellis a trellis whose criteria filter on label rather than time/status?

### Moderation
10. For shared plot messages with owner approval: does the approval flow use the
    existing trellis pending/approved state machine, or a new mechanism?
11. Auto-approve vs owner-approve — is this a per-plot setting, per-trellis setting,
    or per-message setting?

### Cross-cutting
12. How does the messaging model interact with the Care Mode / chained capsule design
    (ARCH-008)? Can a Care Mode executor leave messages for recipients?
13. What is the retention/deletion model for messages? Can a sender delete a message
    after it has been read?

## Deliverable

A brief at `docs/briefs/ARCH-011_unified-messaging-model.md` covering:

1. **Recommended data model** — entity definitions, storage locations, schema sketch
2. **E2EE construction** — key management for messages at each visibility scope
   (capsule item, shared plot, message trellis)
3. **Message trellis design** — how it extends or reuses the trellis concept
4. **Moderation flow** — how auto-approve vs owner-approve maps to existing state machines
5. **Platform impact assessment** — what each platform (server, Android, web, iOS) needs
   to implement; estimated scope per platform
6. **Open questions for CTO** — anything requiring a product decision before implementation

## Completion notes

**Completed:** 2026-05-17
**Author:** TechnicalArchitect
**Deliverable:** `docs/briefs/ARCH-011_unified-messaging-model.md`

### Summary of decisions made

1. **Messages are a first-class entity** — new `messages` table (not uploads, not embedded envelopes). Body stored inline as BYTEA (64 KB max). Two new migration files: V33 (messages + `plots` column additions) and V34 (message trellises).

2. **Three encryption paths** cover all visibility scopes:
   - `capsule-message-aes256gcm-v1`: HKDF-derived sub-key from `capsule_master_key` (info `"capsule-message-v1"`) for broadcast capsule messages.
   - `plot-aes256gcm-v1` (existing M10 ID): reused for broadcast shared plot messages.
   - `message-aes256gcm-v1`: per-message DEK with per-recipient P-256 ECDH wrapping for selective visibility in both contexts.

3. **Message trellises are a new entity** (`message_trellises` / `message_trellis_members` tables), not rows in the existing `trellises` table. They govern read access at query time (not write routing at upload time). Label tokens use a new HKDF context `"message-label-v1"` per ARCH-007 pattern.

4. **Moderation maps to the staging conceptual model**: `message_policy = 'auto_approve'` mirrors `requires_staging = false`; `'owner_approve'` mirrors `requires_staging = true`. State is stored in `messages.status` (not `plot_staging_decisions`).

5. **Recipient identity blinding** via per-message HMAC tokens prevents the server from inferring the social graph from `message_recipient_keys` rows.

6. **Six open questions** documented for CTO, two blocking: OQ-1 (which milestone) and OQ-2 (selective visibility as opt-in vs. default).

7. **New algorithm IDs to register in `docs/envelope_format.md`:** `capsule-message-aes256gcm-v1`, `message-aes256gcm-v1`. No binary envelope format version bump needed.

### Care Mode interaction (ARCH-008)
Confirmed out of scope for this brief. Care Mode executor messages require a third scope column (`care_session_id`); deferred to M13 per ARCH-008 sequencing.

### ARCH-007 interaction
`label_token` uses the same HMAC scheme as upload tag tokens, with a new HKDF context string (`"message-label-v1"`) to keep message labels cryptographically separate from upload tags.
