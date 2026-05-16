# Research Manager Session Log

**Date:** 2026-05-16  
**Persona:** Research Manager  
**Session type:** Strategic analysis, USP assessment, migration architecture, wrap-up

---

## Tasks completed

None — this session was strategic discussion and task/documentation creation.

---

## Tasks created

| ID | Title | Priority | Notes |
|---|---|---|---|
| [RES-003](../../../tasks/queue/RES-003_pqc-migration-readiness-brief.md) | PQC migration readiness — algorithm break response plan for Technical Architect | High | Formalises the three-layer attack window framework and migration phases |

---

## Key discussions and findings

### Multi-perspective USP and IP assessment

The CTO asked for an analysis of Heirlooms' unique selling points and intellectual
property from five perspectives, assuming a working product with E2EE, time-windowed
capsules, and trust network custodianship. Summary of findings:

**Professor of Computer Science (crypto):**
- The window capsule construction (tlock lower bound + threshold Shamir deletion upper
  bound) appears unpublished in the literature in this form — patentable and publishable
- The formal security model for the window capsule does not yet exist — formalising it
  is a research contribution
- Verifiable deletion (proving a Shamir share was destroyed without revealing it) is an
  open problem; solving it cleanly for Heirlooms would be independently publishable
- The product creates demand for a primitive that doesn't exist: post-quantum threshold
  time-lock encryption

**Security Manager:**
- The zero-knowledge server property, if independently audited and certified, is a
  marketable trust credential no competitor has earned
- The window capsule adds cryptographic self-destruction and distributed custody with
  no single point of failure — including Heirlooms itself
- Honest limitation: expiry is trust-bounded, not trustless; this must be clearly
  communicated or reputational damage from a failure would be severe
- P-256 migration remains the most urgent unresolved gap

**Marketing Executive:**
- Core proposition: "We are the only platform where your digital promise is enforced
  by mathematics, not by our word"
- Consumer: grief/legacy narrative as emotional entry point; premium tier for legal
  entity custody and multi-decade capsules
- Enterprise: legal sector (law firms as custodians), financial services (digital trust
  alongside estate planning), regulated industries (document escrow with cryptographic
  expiry), corporate succession
- The phrase "cryptographically enforced" is a differentiator no competitor can match
  without a fundamentally different architecture

**Retirement Planner:**
- IP position: window capsule construction is patentable and separable from the
  operating company — value independent of whether the product succeeds
- Long-duration subscriptions (20-year capsule → 20-year committed subscriber) create
  unusual revenue predictability vs standard SaaS
- Acquisition targets: financial institutions, legal tech, cloud storage providers,
  security companies — any would acquire rather than build
- Priority advice: file patents now, before publication clock starts; complete P-256
  migration before Q-Day as a retirement planning risk

**Technical Architect:**
- Envelope format is the most underrated IP — simple, correct, extensible; most systems
  bolt agility on afterwards
- Window capsule adds three new components: K_window split, custodian API protocol
  (no standard exists — Heirlooms would define it), cross-platform BLS12-381
- Custodian share-release protocol, if published as an open standard, is a significant
  technical contribution analogous to Signal's double-ratchet publication
- Cross-platform BLS12-381 is significant implementation work but a barrier to entry
  for competitors

**Grand summary finding:**
Heirlooms has built the foundations of a cryptographic primitive that does not yet have
a name in the literature: a time-windowed trust envelope. This is not a feature — it is
a new kind of object. The grief narrative is the correct consumer entry point; the
underlying infrastructure is a general-purpose digital trust network with enterprise,
legal, and regulatory applications. The most important near-term actions are: file
patents, deploy hybrid keys, and begin the post-quantum trust narrative publicly.

---

### PQC migration architecture under algorithm break

The CTO asked: if P-256 is broken in 8 years, how does the format evolve to re-wrap
existing data while minimising the attack window?

**Key architectural insight:** File content (AES-256-GCM) is already quantum-safe.
Only the P-256 key-wrapping layer needs migration. Cost is O(keys), not O(files).

**Three-layer attack window framework:**
- Layer 1 (future data): closed the day hybrid keys are deployed
- Layer 2 (existing key wrapping): closed when each user's device re-wraps on next auth
- Layer 3 (HNDL harvested data): only closed by full master key rotation + DEK re-wrap;
  cannot be retroactively closed for data harvested before hybrid deployment

**Migration phases:**
- Phase 0 (now): implement ML-KEM codecs, reserve algorithm IDs, build re-wrap service
- Phase 1 (on break): generate ML-KEM keypairs, upload public keys
- Phase 2 (on break, per user): re-wrap master key under ML-KEM on next auth (silent)
- Phase 3 (days to weeks): full master key rotation, background DEK re-wrap
- Phase 4: shared plot key and item sharing DEK re-wrap (coordinated)

**Envelope format evolution:** No version bump needed. New algorithm IDs:
`hybrid-p256-mlkem768-hkdf-aes256gcm-v1` → `mlkem768-hkdf-aes256gcm-v1`.
Old IDs supported for reading indefinitely; deprecated for writing after migration.

**The honest limit:** HNDL for already-harvested data cannot be retroactively closed.
This stops growing the day hybrid keys go live. Every day of delay on hybrid deployment
expands the permanent historical exposure.

---

### Window capsule concept (origin noted)

The CTO proposed the window capsule construction during this session. The Research
Manager confirmed it is architecturally sound using known primitives (tlock + Shamir
threshold deletion), noted the trustless expiry impossibility (information-theoretic
argument: once publicly derivable, always derivable), and queued RES-002 and SIM-001
in the previous session log.

RES-003 was queued in this session to formalise the migration architecture for the
Technical Architect.

---

## Documentation sweep (this session)

| Artefact | Action |
|---|---|
| `docs/research/GLOSSARY.md` | Added: Attack window, Custodian, Expire time, Key rotation, Re-wrap, Time-windowed trust envelope, Unlock time, Verifiable deletion, Window capsule |
| `docs/PA_NOTES.md` | Added Research Manager section with research outputs, completed/queued tasks, open CTO questions, and persona recommendations |
| `tasks/progress.md` | Added RES-003 |
| `tasks/queue/RES-003_*.md` | Created |

---

## State of the research queue at session end

| ID | Title | Priority | Status |
|---|---|---|---|
| RES-002 | Window capsule — cryptographic expiry | High | Queued |
| RES-003 | PQC migration readiness brief | High | Queued |
| SIM-001 | Trustless expiry impossibility (simulation) | Low | Queued, depends on RES-002 |

---

## Recommended next actions

1. **CTO: answer RES-001 open questions** — particularly the P-256 migration timing
   decision. This unblocks RES-003 and eventual ARCH task dispatch.

2. **CTO: consider patent filing** — the window capsule construction should be assessed
   by a patent attorney before RES-002 is published or the construction is described
   publicly. Filing before disclosure is essential.

3. **Next "do research" session: run RES-002 then RES-003** — in that order, since
   RES-002's literature review may inform the migration brief's recommendations around
   the custodian API protocol.

4. **CTO: consider adding Psychologist and Brand personas** — no code changes needed,
   just new personality files. These fill real current gaps in the team.
