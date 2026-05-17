---
id: ARCH-014
title: Technical impact assessment — RES-005 presence-gated delivery and count-conditional trigger
category: Architecture
priority: Medium
status: queued
assigned_to: TechnicalArchitect
depends_on: [ARCH-003, ARCH-006, ARCH-008, ARCH-010]
touches:
  - docs/research/RES-005_presence-gated-delivery.md
  - docs/briefs/ARCH-003_m11-capsule-crypto-brief.md
  - docs/briefs/ARCH-006_tlock-provider-interface.md
  - docs/briefs/ARCH-008_chained-capsule-and-care-mode-feasibility.md
  - docs/ROADMAP.md
estimated: 1 session
---

## Goal

Assess the technical impact of the two constructions defined in RES-005 on the
existing M11/M12 architecture and the broader roadmap. Produce a brief that answers
whether these constructions require new schema, new API surfaces, or new envelope
algorithm IDs — and where they sit in the milestone sequence.

## Background

RES-005 defines two novel constructions, both invented during a Security Manager
session on 2026-05-17:

**§1 — Presence-gated delivery.** A two-phase capsule where the time window [w1, w2]
acts as a presence gate. Recipients who prove they opened the window layer during the
window receive a masked content key after w2. Non-attendees are permanently excluded.
The server never learns the content key. The real content is invisible during the
proving phase.

**§3 — Count-conditional trigger.** The count of presence proofs at w2 is evaluated
against a predicate set by Alice at sealing time. If met, the server delivers a
masked content key to a fixed downstream recipient (who may differ from the
presence-gate addressees). Multiple predicates and multiple target capsules are
composable. The `count == 0` case is a cryptographic dead man's switch.

Both constructions sit on top of the M11 window capsule foundation (ARCH-003/006).
Neither requires a new cryptographic primitive. The question is what new schema,
API, and envelope format work they require — and when.

## Read first

Before assessing, read in full:
- `docs/research/RES-005_presence-gated-delivery.md` — the complete construction spec
- `docs/briefs/ARCH-003_m11-capsule-crypto-brief.md` — existing M11 capsule schema and API
- `docs/briefs/ARCH-006_tlock-provider-interface.md` — TimeLockProvider and /tlock-key
- `docs/briefs/ARCH-008_chained-capsule-and-care-mode-feasibility.md` — chained capsule model

## Questions to answer

### 1. Schema impact

RES-005 §1 requires:
- Per-capsule, per-recipient presence tickets embedded in the window layer.
- Server-side storage of `H(K_ticket_i)` (presence proof commitments) and
  `K_content XOR K_ticket_i` (delivery masks) per named recipient.
- A presence proof table (who submitted a proof, when).

RES-005 §3 additionally requires:
- A trigger table: (capsule_id, predicate, target_capsule_id, recipient_id, mask_B).
- Predicate evaluation at w2 (a scheduled job or delivery hook).

Assess: can these be accommodated within the existing capsule schema (V32 and later
migrations), or do they require a new migration epoch? Are there conflicts with
existing columns?

### 2. API surface

RES-005 §1 requires:
- A presence proof endpoint: `POST /api/capsules/:id/presence` — accepts
  `H(K_ticket_i)`, verifies against stored commitment, records the proof.
- A winner delivery endpoint (may be an extension of the existing delivery flow):
  returns `K_content XOR K_ticket_i` to each winner at w2.

RES-005 §3 requires:
- A trigger registration endpoint (called at sealing time): stores the predicate
  and masked key.
- A trigger evaluation job: fires at w2, evaluates predicates, dispatches masked
  keys to qualifying recipients.

Assess: how do these fit with the existing sealing endpoint (`PUT /api/capsules/:id/seal`)
and delivery system? Can the presence proof endpoint be added without breaking the
existing M11 sealing contract (ARCH-003 §8)? Does the trigger evaluation need to
be a new scheduled job or can it be folded into the existing w2 expiry hook?

### 3. Envelope format impact

The window layer in §1 must contain per-recipient tickets (K_ticket_i values). These
are symmetric 32-byte values, not new algorithm IDs. Assess whether they can be
embedded as named fields inside the existing window payload (a JSON blob encrypted
under K_window using `aes256gcm-v1`), or whether a new envelope algorithm ID is
needed.

If a new ID is needed, it must be reserved in `docs/envelope_format.md` before any
developer touches the implementation.

### 4. TimeLockProvider interaction

The §1 and §3 constructions do not change the tlock path itself — the window layer
(K_a, K_b) is sealed and unsealed identically to the base window capsule. Confirm
that the `TimeLockProvider` interface (ARCH-006) requires no changes. If the presence
proof count needs to be available before `TimeLockProvider.decrypt()` fires, assess
whether the ordering of w2 events (presence proof collection → predicate evaluation →
tlock key delivery) is correct.

### 5. Milestone sequencing

M11 implements the base window capsule (tlock + ECDH wrapping). RES-005 builds on
top of it. Assess:

- Can §1 (presence-gated delivery) be scoped as an M12 feature, or does it require
  M11 schema to be amended before M11 ships?
- Can §3 (count-conditional trigger) be scoped as M13 or later?
- Does the dead man's switch (`count == 0` → release fallback capsule) have any
  dependency on the posthumous delivery work planned for M13?
- Is there a natural ordering between §1 and §3, or can they be implemented
  independently?

### 6. Chained capsule compatibility (ARCH-008)

RES-005 §3 is described in the brief as a special case of the chained capsule model
(RES-004 / ARCH-008), where the trigger is a presence-proof count rather than a
puzzle solve. Assess:

- Does the trigger mechanism from §3 generalise the existing chained capsule link
  structure, or is it a parallel mechanism?
- If §3 is implemented, should ARCH-008's chained capsule design be amended to
  treat count-conditional triggers as a first-class trigger type alongside puzzle-solve?
- Are there schema or API conflicts between §3 and the existing chained capsule model?

### 7. Open questions inherited from RES-005

Two open questions from RES-005 are technical rather than product decisions:

- **Predicate integrity:** The server is trusted to evaluate predicates honestly. Is
  this acceptable under the existing server trust model, or should on-chain predicate
  verification be designed in from the start? What is the engineering cost of each?
- **Overlapping predicates:** Should the sealing API validate non-overlapping
  predicates, or leave this to the sender? What is the simplest enforcement approach?

## Output

Produce `docs/briefs/ARCH-014_res005-technical-impact.md` covering:

1. Schema changes required for §1 and §3, with a proposed migration number.
2. New API endpoints and their fit with the existing sealing/delivery contract.
3. Envelope format changes (new algorithm IDs, if any).
4. Confirmation that TimeLockProvider requires no changes, or a spec for any changes needed.
5. Recommended milestone placement for §1 and §3.
6. Assessment of §3's relationship to ARCH-008 chained capsules — merge or parallel.
7. Recommendation on predicate integrity and overlapping predicate enforcement.

Flag any blocker that would require amending M11 before it ships.

## Completion notes

<!-- TechnicalArchitect appends here and moves file to tasks/done/ -->
