---
id: RES-004
title: Chained capsule — cryptographic novelty assessment and conditional delivery literature review
category: Research
priority: Medium
status: queued
assigned_to: ResearchManager
depends_on: [RES-002]
estimated: 1–2 sessions
---

## Context

Following the session that produced RES-002 (window capsule construction), the CTO
proposed a further concept: **chained capsules** — a directed acyclic graph (DAG) of
capsules where the unlock of one capsule is conditional on a receiver completing a task
inside a prior capsule. Key mechanics discussed:

- **Time-windowed delivery**: capsule C₁ opens at T+2 days, closes at T+3 days (1-day window)
- **Competitive delivery**: the first of multiple recipients to solve a puzzle inside C₁
  gains access to C₂ — the others do not
- **Expiry-as-death**: if nobody solves the puzzle before C₁ closes, C₂ is never delivered
  and its contents are permanently inaccessible
- **Capsule reference tokens**: a QR code inside C₁ contains an encrypted reference to
  C₂'s key material; scanning it with the correct answer triggers C₂'s unlock
- **Notation proposed**: `C₁({A,B}, [T₀+2d→T₀+3d], {puzzle, ref→C₂}) → C₂({winner}, [T₀+4d→T₀+5d], {prize})`

The CTO also proposed a **consent capsule** for the Care Mode use case: a sealed,
cryptographically timestamped record of a person's consent to monitoring by a POA holder,
revocable by the person at any time while they retain capacity.

## Research questions

1. **Conditional/programmable encryption**: Is chained conditional capsule delivery a known
   cryptographic primitive? Search under names including: conditional encryption, functional
   encryption, attribute-based encryption with time conditions, programmable encryption,
   witness encryption with puzzle conditions.

2. **DAG composition of window capsules**: Can two window capsule constructions (as specified
   in RES-002) be composed such that the expiry event of C₁ conditionally triggers the
   availability of C₂? Is this composable without new cryptographic machinery, or does it
   require a new primitive?

3. **"First solver wins" cryptography**: What cryptographic mechanism enforces exclusive
   access for the first of N recipients to provide a correct puzzle answer? Is this a fair
   exchange problem? Is an oblivious transfer or commit-reveal scheme relevant?

4. **Expiry-as-death**: The property "if C₁ window closes without a solve, C₂ is never
   accessible to anyone" — is this achievable trustlessly? Or does it require the same
   threshold-custodian trust assumption as the window capsule upper bound?

5. **Capsule reference tokens**: Is there prior work on encrypted pointers from one sealed
   document to another — where the pointer itself is only readable after the referencing
   document is unlocked?

6. **Formal notation for capsule DAGs**: Is there an academic notation for time-conditioned
   dependency graphs over encrypted documents? Relevant fields: process calculi, temporal
   logic, timed automata, smart contract formalisms.

7. **Consent capsule**: Is there existing work on cryptographically timestamped, immutable
   consent records that are revocable by the consenting party? Search in the healthcare
   data governance and self-sovereign identity literature.

8. **Smart contract analogues**: Ethereum-style smart contracts can encode conditional
   release logic. What has been published on using smart contracts as the conditional
   engine for encrypted content release? Are there relevant security properties or attacks
   from that literature that apply to Heirlooms' off-chain model?

## Output

Produce a research brief to `docs/research/RES-004_chained-capsule-cryptographic-assessment.md`.

Append new references to `docs/research/REFERENCES.md` under section RES-004.
Update `docs/research/GLOSSARY.md` with any new terms.

Report structure:
```
# RES-004 — Chained Capsule: Cryptographic Assessment

## Executive Summary
## Literature review
  ### Conditional / programmable encryption
  ### DAG composition of window capsules
  ### First-solver-wins mechanisms
  ### Expiry-as-death trustlessness
  ### Capsule reference tokens
  ### Formal notation for capsule DAGs
  ### Consent capsule prior work
  ### Smart contract analogues
## Novelty assessment
## Recommended construction (if feasible)
## Patentability note (flag for Legal)
## PA Summary
```

The PA Summary must include:
- Whether the chained capsule construction is novel and to what degree
- Whether any component requires new cryptographic machinery vs. composing existing primitives
- Any elements that should be flagged to Legal for patent assessment
- Recommended follow-on tasks
