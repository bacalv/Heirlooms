---
id: PHI-002
title: Capsule narrative framing — consent, futurity, and the key ceremony as a symbolic act
category: Philosophy
priority: High
status: in-progress
depends_on: [PHI-001]
touches:
  - docs/philosophy/
assigned_to: Philosopher
estimated: 2–3 hours
---

## Context

The Technical Author is producing a layered technical guide to Heirlooms capsules
(TAU-001). The public-facing layer needs a 500–800 word narrative framing that explains
what a capsule *means* — not just how it works — for a technically sophisticated but
not necessarily cryptographically specialist audience (researchers, collaborators, press).

Your output will be inserted into that guide as Part 1: "What a capsule is."

## What we need from you

Write 500–800 words answering these questions through a philosophical lens:

1. **What is a capsule?**
   Not the technical definition — the *human* one. A capsule is a promise made across
   time. It is an act of consent directed at a future you cannot fully anticipate.
   What does it mean to seal something and direct it to open under conditions you
   specify but cannot enforce?

2. **The key ceremony as a symbolic act**
   When a user seals a capsule in Heirlooms, the cryptographic operation involves
   splitting a key into two halves — one held by the user, one sealed by a time-lock
   provider — such that neither half alone can decrypt the content. The server that
   holds the sealed half can never read the content, even after delivery, because it
   never has the other half.
   
   What is the ethical and symbolic significance of this design? Who does it protect?
   What obligations does it impose on Heirlooms as custodian?

3. **Futurity and consent**
   The person who seals a capsule may not be alive when it is delivered. What is the
   moral status of instructions given by the living to the future? Can consent survive
   death? (Reference your work in PHI-001 where relevant.)

4. **Trust without surveillance**
   Heirlooms' server-blindness property means Heirlooms literally cannot read users'
   capsules. What does it mean for a custodian to hold something they cannot open?
   Is this a stronger or weaker form of trust than a custodian who *chooses* not to
   look?

## Tone and audience

- Rigorous but accessible — this is for a technical whitepaper, not a philosophy journal
- No jargon (neither crypto nor philosophy jargon without brief definition)
- Engage seriously with the hardest questions; do not hedge everything into vagueness
- 500–800 words

## Output

Write your framing to `docs/philosophy/PHI-002_capsule-narrative-framing.md`.

The Technical Author will pull this directly into PAP-001 Part 1. Mark clearly where
you leave deliberate open ends for the Technical Author to tie into the technical
content that follows.

## Completion notes

**Completed:** 2026-05-16  
**Assigned to:** Philosopher  
**Output:** `docs/philosophy/PHI-002_capsule-narrative-framing.md`

All four required themes addressed:

1. **What a capsule is** — framed as a promissory obligation across time, analogous to a will or advance directive, but cryptographically irrevocable once sealed.
2. **The key ceremony** — ethical distinction drawn between a custodian who chooses not to look and one who is structurally unable to see; reframes Heirlooms' obligation as continuity/fidelity of delivery rather than secrecy.
3. **Futurity and consent** — posthumous consent treated as valid in principle (per PHI-001), with the tension between the sender's instructions and the recipient's present autonomy explicitly held open rather than falsely resolved.
4. **Trust without surveillance** — cryptographic trust analysed as different in kind from relational custodial trust: stronger against institutional betrayal, differently vulnerable around operational continuity.

Two deliberate open ends are marked `[Technical Author: ...]` for the Technical Author to tie into Part 2 (crypto implementation details) and Part 4 (long-horizon operational risk).
