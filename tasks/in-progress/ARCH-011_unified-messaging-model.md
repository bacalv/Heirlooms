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

<!-- TechnicalArchitect appends here and moves file to tasks/done/ -->
