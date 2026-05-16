---
id: SIM-001
title: "Simulation: trustless expiry — what is the weakest possible construction without custodians?"
category: Simulation
priority: Low
status: queued
assigned_to: ResearchManager
depends_on: [RES-002]
estimated: 1 session
---

## Purpose

This is a SIMULATION task. Findings must NOT feed the task queue, architectural decisions,
or security recommendations. This is speculative thinking, branched from current knowledge,
to explore the theoretical boundary of what is possible — not to guide what Heirlooms builds.

Wrap up with a short summary. Do not produce a full research brief.

## The question

The CTO's window capsule construction (RES-002) requires trusted custodians for the expiry
bound. The CTO's intuition (2026-05-16) is that a fully trustless expiry is impossible.

This simulation explores: **is that intuition correct, and where exactly does the
impossibility lie?**

More precisely: assuming an adversary with unlimited classical computation, perfect memory,
and no ability to coerce any party — what is the weakest trust assumption under which
a cryptographic expiry guarantee can be constructed?

## Directions to explore (speculatively)

### 1. Why classical trustless expiry appears impossible
- The "un-publishing" problem: any information derivable from publicly available material
  remains derivable forever. tlock publishes its decryption key to the world. You cannot
  un-ring a bell.
- Formalise this as an information-theoretic argument: is there a proof that no classical
  scheme can achieve information-theoretic expiry without a party actively destroying secret
  material?

### 2. Quantum escape hatches
- The no-cloning theorem: a quantum state cannot be copied. If K_window is encoded as
  a quantum state and transmitted to the receiver, it physically cannot be retained after
  measurement. Does this give us anything useful?
- Quantum key distribution (QKD): does the physics of quantum measurement give any
  expiry-like properties?
- Limitations: requires quantum infrastructure; the receiver still measured the key and
  could have stored the plaintext derived from it.

### 3. Trusted hardware as a weaker-than-custodian assumption
- An HSM or SGX enclave can be programmed to delete key material at a specified time and
  to attest to that deletion cryptographically.
- Is "trust in hardware manufacturer" a weaker assumption than "trust in custodian"?
  In what threat models?
- What happens when hardware is physically attacked, compromised firmware is deployed,
  or the manufacturer is coerced?

### 4. "Computational expiry" — not information-theoretic, but practically sufficient
- Even if perfect expiry is impossible, could we construct a scheme where expiry is
  computationally infeasible to circumvent (rather than information-theoretically impossible)?
- For example: if reconstructing K_window after expire_time requires solving a problem
  that takes 2^128 operations even with custodian shares — is that "good enough"?

### 5. The oblivious RAM / memory-hard function angle
- Memory-hard functions (like Argon2) are expensive to compute on custom hardware.
  Could a memory-hard time-lock be constructed where the cost of computing the expiry
  bypass grows faster than the value of the content over time?

### 6. Physical world analogues
- A safety deposit box key that dissolves in water after 10 years.
- A legal instrument that self-voids.
- Do any of these have cryptographic analogues that are rigorous?

## Simulation output

A short (1–2 page) note summarising:
- Whether trustless expiry is provably impossible, or merely practically unachievable
- The weakest trust assumption under which a meaningful expiry guarantee exists
- Any theoretical primitives that could change this in the future
- Whether quantum approaches give anything real or are just science fiction at consumer scale

**This note is throw-away. It does not feed RES-002 or any downstream tasks.**
Save to session log only — not to `docs/research/`.
