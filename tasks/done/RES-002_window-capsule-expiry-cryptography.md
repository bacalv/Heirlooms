---
id: RES-002
title: Window capsule — cryptographic expiry, literature review and construction brief
category: Research
priority: High
status: queued
assigned_to: ResearchManager
depends_on: []
estimated: 1–2 sessions
---

## Goal

Investigate whether Heirlooms can implement a "window capsule" — a capsule with both
an unlock time (lower bound) and an expire time (upper bound), after which the content
becomes permanently undecryptable by anyone, including the server.

The CTO has proposed a practical construction (conversation 2026-05-16) that uses:
- tlock/drand for the lower bound (trustless, already designed for M11)
- Threshold custodian deletion of Shamir shares for the upper bound (requires honest custodians)

This task must:
1. Confirm or refute the novelty of this construction through literature review
2. Identify the closest existing academic work
3. Assess whether a stronger (more trustless) construction is achievable
4. Produce a cryptographic brief for the Technical Architect covering the recommended
   construction, its trust assumptions, and its fit with the existing envelope format

## Research questions

1. Does the combination of tlock (lower bound) + threshold Shamir deletion (upper bound)
   appear in the literature? If so, under what name and with what properties?

2. What is "Proactive Secret Sharing" (Herzberg et al. 1995) and does it offer anything
   useful for the expiry problem?

3. Are there any "timed commitment" or "forward-secure deletion" schemes that apply here?

4. What is the state of "witness encryption" (Garg et al. 2013) — is it any closer to
   practical deployment? Could it theoretically encode a time-window condition?

5. Are there trusted hardware approaches (HSM-enforced deletion, Intel SGX) that could
   strengthen the expiry guarantee without requiring custodian trust?

6. What verifiable deletion proofs exist? Can a custodian prove they deleted their share
   without revealing the share itself?

7. Is the window capsule construction patentable? What prior art exists?

## Proposed construction (to be validated)

- Content encrypted under DEK (unchanged from current design)
- DEK encrypted under K_window
- K_window split (2-of-2): K_a XOR K_b = K_window
- K_a = tlock_encrypt(R_unlock) — trustlessly available after unlock_time
- K_b = Shamir split (M-of-N) across independent custodians
- Custodians release K_b shares only to authenticated receiver during [unlock_time, expire_time]
- Custodians destroy shares at expire_time (with optional verifiable deletion proof)
- After expire_time: K_a is permanently public but K_b is gone → K_window irrecoverable

## Known limitations to assess

- Expiry bound is not trustless — requires honest custodian threshold
- Receiver who decrypts within the window retains K_window locally (cannot retroactively expire)
- Custodian availability during the window must be guaranteed (uptime, key management)

## Output

`docs/research/RES-002_window-capsule-expiry-cryptography.md` covering:
- Literature review findings
- Novelty assessment
- Recommended construction with formal trust assumptions
- Cryptographic agility notes (fit with envelope format)
- Recommendations for the Technical Architect
- PA Summary

## Completion notes

**Completed:** 2026-05-16  
**Output:** `docs/research/RES-002_window-capsule-expiry-cryptography.md`

**Key findings:**
1. The tlock + Shamir deletion window capsule construction is novel as a complete practical system. The closest prior academic work is "Timed Secret Sharing" (Kavousi, Abadi, Jovanovic — ASIACRYPT 2024), which defines the same framework abstractly but does not instantiate it using drand/tlock or XOR blinding.
2. Time-Specific Encryption (Paterson-Quaglia 2010) is the other important prior work: it formally models a time interval [T1,T2] for decryption but uses an online centralised time server and key distribution, not deletion.
3. Trustless upper bound is information-theoretically impossible with classical cryptography. The construction achieves the strongest possible classical upper bound: threshold-honest custodian deletion.
4. Quantum certified deletion (Bartusek-Raizes CRYPTO 2024; Katz-Sela Eurocrypt 2025) is the correct long-term solution but requires quantum infrastructure not available in production today.
5. Proactive Secret Sharing (Herzberg 1995) is recommended as an enhancement for long-window capsules (>12 months).
6. Patentability: the specific combination (drand lower bound + XOR blinding + threshold deletion upper bound) is likely patentable. Filing should precede public launch of the feature.

**Decisions routed to CTO via PA Summary:**
1. Should Heirlooms' own servers be the initial custodians, or should external institutional custodians be designed in from the start?
2. Is patenting the window capsule construction a priority?
3. Should PSS refresh be included in the initial spec or deferred?

**Follow-on tasks:** SIM-001 is now unblocked (depends on RES-002).
