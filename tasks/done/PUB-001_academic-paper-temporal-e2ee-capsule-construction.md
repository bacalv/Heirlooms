---
id: PUB-001
title: Academic paper — temporal E2EE capsule with multi-layer recovery
category: Publication
priority: Medium
status: queued
assigned_to: Philosopher + TechnicalAuthor
depends_on: [ARCH-003, ARCH-006, RES-004]
touches:
  - docs/publications/
estimated: 1 session (Philosopher selects concept; Technical Author drafts)
---

## Goal

Produce a draft academic paper (conference paper length, ~6–8 pages) exploring the most intellectually novel aspect of the Heirlooms capsule construction. The Philosopher selects the angle; the Technical Author produces the paper with formal mathematical notation.

## Background

Three completed research/architecture documents provide raw material:
- **ARCH-003** — M11 capsule cryptography brief: the full sealing construction (recipient pubkey wrapping + tlock IBE + Shamir SSS)
- **ARCH-006** — TimeLock provider interface and the DEK blinding scheme (`DEK = DEK_client XOR DEK_tlock`)
- **RES-004** — Chained capsule cryptographic assessment: conditional delivery, novelty assessment

The Legal assessment (LEG-001) found no active patent anticipating the specific combination of `tlock IBE lower-bound + XOR DEK blinding + Shamir threshold deletion upper-bound`. This is the combination most likely to constitute a scholarly contribution.

## Instructions for the Philosopher

Read ARCH-003, ARCH-006, and RES-004. Also read the ROADMAP.md M11 section for product framing.

Select the angle you find most philosophically and technically compelling — candidates include:
1. **The blinding scheme as epistemological claim**: the construction that means "Heirlooms cannot know your secret, ever" — not as a policy, but as a cryptographic impossibility. What does it mean to make a mathematical promise about the future?
2. **Multi-layer temporal redundancy**: three independent unlock paths (recipient key, time-lock, social recovery) each defending against the failure mode of the others — a system designed around the epistemology of long-horizon uncertainty.
3. **The trustless expiry impossibility and custodian trust gradient**: RES-004/SIM-001 framing — you cannot have both "no custodian" and "guaranteed expiry", and where Heirlooms sits on that spectrum.

You are not bound to these. Pick the framing you would most want to read in a philosophy of cryptography paper.

## Instructions for the Technical Author

Once the Philosopher has selected the angle, draft the paper with:
- Abstract (150 words)
- Introduction: the human problem (sealed memory, future delivery, mortality)
- Background: relevant primitives (IBE, SSS, ECDH key wrapping)
- Construction: formal specification of the relevant Heirlooms construction(s) with mathematical notation
- Properties: correctness theorem(s), informally stated with sketched proof
- Discussion: limitations, open problems, relation to prior work
- References: cite relevant papers (use RES-002 and RES-004 reference lists)

The paper should be suitable for submission to a cryptography or security venue (e.g. Financial Cryptography, CCS, PETS). Keep it honest about what is claimed vs. conjectured.

## Output

- Draft paper at `docs/publications/PUB-001_temporal-e2ee-capsule.md` (Markdown with LaTeX math blocks)
- One-paragraph abstract suitable for use in patent application annexes
- A note on which venue(s) the paper targets and what additional work would be needed to reach submission quality

## Acceptance criteria

- Paper is produced and committed
- Construction section is formally specified and internally consistent with ARCH-003/ARCH-006
- Philosopher has written a framing note (≥1 paragraph) explaining why this angle was chosen
- References are cited with the `[RES-NNN-NNN]` scheme from `docs/research/REFERENCES.md`

## Completion notes

**Completed:** 2026-05-16  
**Agent:** Philosopher + TechnicalAuthor  
**Output:** `docs/publications/PUB-001_temporal-e2ee-capsule.md`

**Angle chosen:** Structured ignorance as a cryptographic property — a synthesis of the blinding scheme (Candidate 1) and multi-layer temporal redundancy (Candidate 2).

**Rationale for angle selection:** The three candidate angles were assessed against one another. The trustless expiry impossibility (Candidate 3) is well-covered by RES-002 and SIM-001 and would largely recapitulate existing analysis. The blinding scheme alone (Candidate 1) is technically precise but would be a short contribution without the temporal framing that gives it human stakes. Multi-layer redundancy alone (Candidate 2) is an engineering observation rather than a cryptographic claim. The synthesis — "structured ignorance," meaning the server is cryptographically prevented from reading content during the pre-round window — provides both a precise formal claim (Theorem 2, pre-round blinding) and a philosophically compelling frame: arithmetic enforces a promise about the future, not policy. This angle is the strongest for academic publication because it is (a) formally stateable, (b) not found verbatim in prior work, and (c) connects to a genuine human problem with emotional weight.

**Key contributions in the paper:**
1. Formal specification of the three-path sealing construction with mathematical notation (Steps 1–5 of the sealing operation; delivery paths).
2. Pre-round blinding property (Theorem 2) with proof sketch under BDH assumption.
3. Multi-path independence (Theorem 3) with failure-scenario corollary table.
4. Discussion of trust-bounded vs. cryptographically enforced deletion, quantum vulnerabilities, and platform compatibility heterogeneity.
5. Patent annex abstract and venue targeting notes for FC/PETS/S&P.

**Consistency with source docs:** Construction is internally consistent with ARCH-003 §9 (blinding scheme), ARCH-006 §6.1 (blinding scheme column mapping), and the envelope format spec. All references use the [RES-NNN-NNN] citation scheme from REFERENCES.md.
