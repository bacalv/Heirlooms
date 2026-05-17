---
id: LEG-003
title: Patent assessment — presence-gated delivery and count-conditional trigger (RES-005)
category: Legal
priority: High
status: queued
assigned_to: Legal
depends_on: [LEG-001, LEG-002]
touches:
  - docs/legal/
  - docs/research/RES-005_presence-gated-delivery.md
  - docs/research/RES-002_window-capsule-expiry-cryptography.md
  - docs/papers/PAP-002_window-capsule-walkthrough.md
estimated: 1 session
---

## Urgency

RES-005 defines two novel cryptographic constructions invented during a Security
Manager session on 2026-05-17. The repo is currently private. Both constructions
should be assessed for patentability and folded into the existing filing strategy
(LEG-001) before any public disclosure. The combined claim set (RES-002 + RES-005)
is likely stronger than RES-002 alone.

## Background

LEG-001 assessed the window capsule construction (RES-002) as likely patentable.
The recommended claim was narrow: the specific combination of tlock/drand IBE lower
bound + XOR blinding + Shamir threshold deletion upper bound. LEG-002 covered the
chained capsule and Care Mode consent constructions (RES-004).

RES-005 introduces two further constructions that extend the window capsule in
directions not covered by either prior assessment.

## Constructions to assess

### 1. Presence-gated delivery (RES-005 §1)

Read `docs/research/RES-005_presence-gated-delivery.md` §1 in full.

The construction uses a window capsule [w1, w2] as a **qualifying gate** rather
than a content delivery mechanism:

- The window layer contains per-recipient presence tickets (K_ticket_i), not the
  real content.
- During [w1, w2], recipients decrypt the window layer, obtain their ticket, and
  submit a hash-commitment presence proof to the server.
- At w2, the server delivers a masked content key `(K_content XOR K_ticket_i)` to
  each recipient who proved presence. Non-attendees are permanently excluded.
- The server never learns K_content (holds only the XOR-masked value).
- The real content is never visible during the proving phase.

Key novel elements:
- Per-recipient tickets embedded inside the window-encrypted layer.
- Server-side XOR delivery masks preserving content-key secrecy from the server.
- Hash-commitment presence proof.
- Self-destruction if no recipients prove presence (no-winner case).

### 2. Count-conditional trigger (RES-005 §3)

Read `docs/research/RES-005_presence-gated-delivery.md` §3 in full.

The construction extends §1 by making the server a **blind predicate evaluator**:
the count of presence proofs observed at w2 determines whether a separate target
capsule is released to a fixed downstream recipient (who may differ from the
presence-gate addressees).

- Alice specifies a predicate at sealing time: `count == 0`, `count >= N`, etc.
- At w2, the server evaluates the predicate against the proof count.
- If met, it delivers a masked key `(DEK_B XOR K_blind_carol)` to Carol (the target
  capsule's recipient). Carol holds K_blind_carol from sealing (ECDH-wrapped); the
  server never learns DEK_B.
- If not met, the mask is destroyed and the target capsule is permanently inaccessible.
- Multiple predicates and multiple target capsules are composable.

The `count == 0` instantiation is a **cryptographic dead man's switch**: if nobody
opens capsule A during the window, a fallback capsule is released to a designated
recipient — with the server acting as a blind conditional courier throughout.

Key novel elements:
- Presence-proof count as a cryptographic trigger (not found in literature).
- Server-blind predicate evaluation combined with XOR-masked conditional key delivery.
- Dead man's switch as a clean product instantiation.
- Branching structure (multiple predicates, multiple target capsules).

## Questions for Legal

1. **Combined filing strategy:** Should RES-005 §1 and §3 be added to the existing
   RES-002 application as dependent claims, or filed as a separate continuation/
   divisional? The layered structure (RES-002 → RES-005 §1 → RES-005 §3) may
   support a single application with independent and dependent claims at each layer.

2. **Claim strength of §3:** The count-conditional trigger is arguably more specific
   and commercially distinctive than §1 or RES-002 alone. Does this warrant an
   independent claim, or is it best protected as a dependent claim on §1?

3. **Dead man's switch:** Is the `count == 0` instantiation distinctive enough to
   warrant its own claim, or is it adequately protected as an embodiment of the
   general predicate construction?

4. **Prior art exposure:** The presence-proof mechanism (hash-commitment of a ticket
   derived from window decryption) resembles timed OT and CP-ABE in structure.
   How close is the prior art exposure, and does it affect claim breadth?

5. **Disclosure timeline:** PAP-002 (`docs/papers/PAP-002_window-capsule-walkthrough.md`)
   is a plain-language walk-through document. Can it be used with investors or a
   patent attorney before filing without starting the novelty clock? It does not
   name the §1 or §3 constructions explicitly but describes the window capsule
   mechanics in detail.

## Output

Produce `docs/legal/LEG-003_rес005-patent-assessment.md` covering:
- Patentability assessment for §1 and §3 individually and in combination.
- Recommended claim structure (independent vs. dependent; separate vs. continuation).
- Prior art exposure and risk for each construction.
- Disclosure guidance for PAP-002.
- Revised filing timeline if the scope has grown since LEG-001.

## Completion notes

<!-- Legal appends here and moves file to tasks/done/ -->
