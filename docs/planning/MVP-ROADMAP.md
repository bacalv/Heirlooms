# Heirlooms — MVP Roadmap

*Created 2026-05-16. Maintained by PA. For CTO review.*

---

## Guiding principle

Ship a working, tested, secure product at each version. Research, legal, and architecture work runs continuously in the background. Implementation only starts when Phase 1 and Phase 2 are complete and CTO has approved. Testing and security gate every release.

**Current state**: v0.53.1 in production (held). All three production blockers fixed (BUG-020, BUG-022, WEB-001). Ready for staging deploy and smoke test.

---

## Version roadmap

### v0.55 — Production quality release (next: ready now)

**Goal**: promote the fixed codebase to production.

| Task | Owner | Status |
|------|-------|--------|
| BUG-022: web shared plot detail view | Developer | Done |
| BUG-020: Android trellis DEK re-wrap | Developer | Done |
| WEB-001: web friends list | Developer | Done |
| Staging deploy + smoke test | Bret | Pending |
| Production promotion | Bret + OpsManager | Pending |

**Gate**: clean smoke test cycle.

---

### v0.56 — Testing infrastructure + HMAC tag scheme

**Goal**: establish automated testing foundations and implement the first novel concept (EPIC-007 — HMAC tags).

| Task | Owner | Phase |
|------|-------|-------|
| TST-009: Android device farm design (3 devices, Appium) | TestManager | Phase 2 |
| TST-004: Playwright E2E suite | Developer | Phase 3 |
| EPIC-007 Phase 3: HMAC tag scheme implementation | Developer | Phase 3 |
| EPIC-007 Phase 4: automated tests + security review | TestManager + SecurityManager | Phase 4 |
| TAU-001: EPIC-007 notation + documentation | TechnicalAuthor | Phase 3/4 |

**Why HMAC tags first**: ARCH-007 is done, no new research needed, implementation is contained (server + clients), and it produces meaningful privacy improvement users will feel.

**Gate**: Playwright suite passing; Android device farm operational; HMAC tag security review green.

---

### v0.57 — Formal notation + window capsule Phase 1/2

**Goal**: establish the formal capsule notation system (EPIC-010) and complete Phase 1/2 for the window capsule (EPIC-001).

| Task | Owner | Phase |
|------|-------|-------|
| EPIC-010: formal notation system | ResearchManager + TechnicalAuthor | Phase 1/2 |
| TAU-002: notation document + paper outline | TechnicalAuthor | Phase 2 |
| EPIC-001 Phase 2: window capsule implementation spec | TechnicalArchitect | Phase 2 |
| EPIC-001 Phase 1: security threat model | SecurityManager | Phase 1 |
| EPIC-001 Phase 1: legal claims annex | Legal | Phase 1 |
| WEB-002: web invite generation | Developer | Phase 3 |
| BUG-021: video duration fix | Developer | Phase 3 |

**Why notation before implementation**: the notation feeds the patent claims, the academic paper, and the implementation spec. Getting it right once saves rework.

**Gate**: notation document approved by CTO and TA; window capsule implementation spec approved.

---

### v0.58 — Window capsule implementation (M11)

**Goal**: ship the window capsule — Heirlooms' core novel cryptographic feature.

| Task | Owner | Phase |
|------|-------|-------|
| EPIC-001 Phase 3: server implementation | Developer | Phase 3 |
| EPIC-001 Phase 3: Android implementation | Developer | Phase 3 |
| EPIC-001 Phase 3: web implementation | Developer | Phase 3 |
| EPIC-005 Phase 3: DEK blinding scheme | Developer | Phase 3 (alongside M11) |
| EPIC-001 Phase 4: automated tests (Android farm + Playwright) | TestManager | Phase 4 |
| EPIC-001 Phase 4: security review | SecurityManager | Phase 4 |
| TAU-003: window capsule academic paper draft | TechnicalAuthor | Phase 3 |
| LEG-003: window capsule claims annex (for patent attorney) | Legal | Phase 3 |
| EPIC-006 Phase 1/2: Care Mode foundation (DPIA started) | Legal + OpsManager | Phase 1/2 |

**Gate**: window capsule automated tests passing on all three Android devices and Playwright; security review green; patent attorney briefed.

---

### v0.59 — Care Mode MVP

**Goal**: ship Care Mode — the highest-value retirement direction.

| Task | Owner | Phase |
|------|-------|-------|
| EPIC-006 Phase 3: Care Mode server + Android | Developer | Phase 3 |
| EPIC-006 Phase 4: DPIA completed | Legal | Phase 4 gate |
| EPIC-006 Phase 4: security review (special category data) | SecurityManager | Phase 4 |
| EPIC-006 Phase 4: automated tests | TestManager | Phase 4 |
| TAU-004: Care Mode technical brief + consent capsule paper | TechnicalAuthor | Phase 3 |
| PSY-002: Care Mode UX review | Psychologist | Phase 4 gate |
| MKT-002: Care Mode positioning + launch brief | MarketingDirector | Phase 5 |
| BIO-001: Care Mode origin story (for press/presentations) | Biographer | Phase 5 |

**Gate**: DPIA completed; Psychologist care mode UX review passed; security review green; Legal sign-off.

---

### v0.60 — Chained capsule (basic)

**Goal**: ship a simple two-capsule chain (C₁ → C₂) with first-solver-wins. No full DAG yet.

| Task | Owner | Phase |
|------|-------|-------|
| ARCH-008: chained capsule feasibility | TechnicalArchitect | Phase 1/2 |
| EPIC-003 Phase 3: basic chained capsule implementation | Developer | Phase 3 |
| EPIC-004 Phase 3: first-solver-wins server-mediated claim | Developer | Phase 3 |
| EPIC-009 Phase 3: capsule reference tokens (QR) | Developer | Phase 3 |
| EPIC-003 Phase 4: automated tests | TestManager | Phase 4 |
| LEG-004: chained capsule claims annex | Legal | Phase 3 |
| TAU-005: chained capsule paper draft | TechnicalAuthor | Phase 3 |
| MKT-003: Experience segment launch brief | MarketingDirector | Phase 5 |

**Gate**: automated tests passing; security review; patent claims drafted.

---

### v1.0 — First public release

**Goal**: all three segments operational, tested, documented. Patent applications filed. Academic paper submitted.

| Milestone | Target |
|-----------|--------|
| Memory Archive: full E2EE vault, shared plots, window capsule | v0.58 |
| Care & Consent: Care Mode with cryptographic consent | v0.59 |
| Experience: chained capsule, first-solver-wins, QR reference tokens | v0.60 |
| Patent applications filed (window capsule + chained capsule) | By v0.58 |
| Academic paper submitted | By v0.60 |
| HMAC tag scheme shipped | v0.56 |
| Formal notation published | v0.57 |
| Automated test suite (Android farm + Playwright) operational | v0.56 |

---

## Background work running in parallel throughout

These run as background agents across all versions and never block shipping:

- **EPIC-008**: PQC migration spec (TA post-M11; RES-003 already complete)
- **EPIC-010**: Formal notation refinement (ongoing with TechnicalAuthor)
- **STR-001**: Strategic synthesis brief (PA — depends on ARCH-008, then runs)
- **RET-002/003**: Retirement planning updates as each epic ships
- **MKT briefs**: Marketing positioning updated per segment launch
- **Biographer**: ongoing journey documentation

---

## The three gates that cannot be skipped

Regardless of version or timeline pressure:

1. **Automated tests passing** — no merge without test coverage. Android device farm + Playwright both green.
2. **Security Manager review** — every novel cryptographic feature reviewed before shipping.
3. **Legal awareness** — no public disclosure of novel constructions before patent attorney is briefed.
