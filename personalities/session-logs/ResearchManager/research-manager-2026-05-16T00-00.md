# Research Manager Session Log

**Date:** 2026-05-16  
**Persona:** Research Manager  
**Session type:** Department inception + first research task

---

## Tasks completed

| ID | Title | Outcome |
|---|---|---|
| RES-001 | Cryptographic threat horizon — initial 40,000ft survey for CTO | Brief produced at `docs/research/RES-001_crypto-threat-horizon-40kft.md`. 28 sources logged. |

---

## Tasks created

None queued — RES-001 was completed within the same session it was commissioned.

Potential follow-on tasks identified in RES-001 (pending CTO decisions before creation):
- ARCH: P-256 → hybrid ML-KEM migration spec (depends on CTO answer to open question 1)
- RES: drand post-quantum roadmap monitoring brief
- SEC: reserve ML-KEM algorithm IDs in envelope format spec

---

## Infrastructure created this session

The Research Manager department was established from scratch:

| Artefact | Location | Purpose |
|---|---|---|
| Persona file (updated) | `personalities/ResearchManager.md` | Added task cadence, wrap-up protocol, simulation task rules |
| Glossary | `docs/research/GLOSSARY.md` | 40+ plain-language cryptographic term definitions; maintained per task |
| Reference log | `docs/research/REFERENCES.md` | 28 numbered sources from RES-001; `[RES-NNN-NNN]` citation scheme |
| Horizon scan directory | `docs/research/horizon/` | For idle-queue digest outputs |
| Session logs directory | `personalities/session-logs/ResearchManager/` | This file |

Working conventions agreed with CTO this session:
- **"Do research"** triggers the task loop: drain queue, then horizon scan if empty
- **RES-** prefix for actionable research tasks; **SIM-** for speculative simulations
- Simulations do not feed the task queue; they are throw-away unless explicitly preserved
- Three mandatory wrap-up steps per task: append REFERENCES.md, update GLOSSARY.md, write PA Summary
- Research Manager reports to PA at task completion; PA routes decisions to CTO

---

## Key decisions and findings (RES-001)

- **Q-Day timeline has compressed.** Three 2025–2026 papers have reduced physical qubit estimates for breaking P-256 to as low as 10,000–26,000 on advanced architectures. The 2030–2032 window is when data uploaded today becomes retrospectively vulnerable.
- **HNDL is a present threat, not a future one.** Nation-state actors are documented as harvesting encrypted traffic now. Long-lived family archives are precisely the target HNDL is designed for.
- **NIST PQC standards are final and deployable.** FIPS 203/204/205 published August 2024. Hybrid X25519+ML-KEM already covers >33% of HTTPS traffic.
- **drand/BLS12-381 is quantum-vulnerable.** drand acknowledges this. The M11 multi-path capsule design (tlock + recipient pubkey + Shamir) is the correct mitigation — tlock failing does not unlock capsules.
- **Heirlooms' envelope format and DEK-per-file model are genuine strengths.** Cryptographic agility is built in from M7. A PQC migration can be incremental, file-by-file, with no flag day.
- **Primary gap:** No P-256 → ML-KEM migration plan exists. This needs specifying before the 2030 window closes.

---

## Recommended next actions

1. **CTO to answer the four open questions in RES-001** (most important: does the Technical Architect begin the hybrid migration spec now or after M11 ships?). PA to route these at next CTO check-in.

2. **Queue RES-002: drand post-quantum monitoring.** Brief on whether drand has a credible PQ migration roadmap; if not, what Heirlooms' contingency is for the tlock layer.

3. **Queue RES-003: P-256 attack surface deep-dive.** More detailed analysis of all three P-256 usage sites (device registration, plot membership, item sharing) with specific migration sequencing recommendations for the Technical Architect.
