# Heirlooms — Epic Framework and Delivery Cycle

*Created 2026-05-16. Maintained by PA.*

---

## What is an epic?

An epic is a major product concept that requires coordinated work across multiple team disciplines before it can ship. Each of Heirlooms' ten novel concepts is an epic. Epics are not sprints — they run across multiple iterations, with different phases active at different times.

---

## The ten epics

| ID | Concept | Tier | Status |
|----|---------|------|--------|
| EPIC-001 | Window Capsule (tlock + Shamir threshold deletion) | 1 — MVP critical | M11 design done; implementation next |
| EPIC-002 | Expiry-as-Death | 1 — MVP critical | Sub-feature of EPIC-001 and EPIC-003 |
| EPIC-003 | Chained Capsule DAG | 1 — MVP critical | RES-004 complete; ARCH-008 next |
| EPIC-004 | First-Solver-Wins exclusive delivery | 2 — MVP adjacent | Architecture design needed |
| EPIC-005 | DEK Blinding Scheme (tlock + ECDH XOR split) | 1 — MVP critical | ARCH-006 done; implementation M11 |
| EPIC-006 | Care Mode — consent before capacity loss | 1 — MVP critical | LEG-002 done; DPIA must start now |
| EPIC-007 | E2EE Tag Scheme (HMAC tokenisation) | 2 — MVP adjacent | ARCH-007 done; implementation next |
| EPIC-008 | DEK-per-file PQC migration (O(keys) cost) | 3 — Research/IP | RES-003 done; TA spec post-M11 |
| EPIC-009 | Capsule Reference Tokens (encrypted inter-capsule pointers) | 2 — MVP adjacent | Part of EPIC-003 |
| EPIC-010 | Formal Capsule Notation | 1 — Foundation | Enables all other epics to be communicated |

---

## Standard task flow per epic

Every epic follows the same five-phase structure. Not all phases require all disciplines — the PA determines which apply per epic.

```
┌─────────────────────────────────────────────────────────────────┐
│ PHASE 1 — FOUNDATION  (parallel, background)                    │
│                                                                 │
│  Research Manager  → RES-NNN: literature, notation, novelty    │
│  Technical Architect → ARCH-NNN: feasibility brief             │
│  Legal             → LEG-NNN: patentability + claims language  │
│  Security Manager  → SEC-NNN: threat model for this concept    │
│  Psychologist      → PSY-NNN: human impact (where relevant)   │
│  Philosopher       → PHI-NNN: ethical assessment (where rel.) │
└──────────────────────────────┬──────────────────────────────────┘
                               │ CTO review point
┌──────────────────────────────▼──────────────────────────────────┐
│ PHASE 2 — DESIGN  (sequential, CTO review gates)               │
│                                                                 │
│  Technical Architect → ARCH-NNN: implementation spec           │
│  Technical Author   → TAU-NNN: notation doc + paper outline   │
│  Test Manager       → TST-NNN: test strategy + automation plan │
│  Operations Manager → OPS-NNN: infrastructure + deploy plan   │
│  Marketing Director → MKT-NNN: commercial positioning brief   │
│  Retirement Planner → RET-NNN: valuation contribution         │
└──────────────────────────────┬──────────────────────────────────┘
                               │ CTO approval to implement
┌──────────────────────────────▼──────────────────────────────────┐
│ PHASE 3 — IMPLEMENTATION                                        │
│                                                                 │
│  Developer(s)       → DEV/FEAT/BUG: implementation             │
│  Technical Author   → TAU-NNN: academic paper draft            │
│  Legal              → LEG-NNN: formal patent claims annex      │
│  Biographer         → BIO-NNN: concept origin story            │
└──────────────────────────────┬──────────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────────┐
│ PHASE 4 — VERIFICATION                                          │
│                                                                 │
│  Test Manager       → TST-NNN: automated test suite execution  │
│                        Android device farm (3 devices)         │
│                        Playwright web automation               │
│  Security Manager   → SEC-NNN: security review                 │
│  Operations Manager → OPS-NNN: deployment review               │
│  Technical Author   → iteration sweep (docs, contradictions)   │
└──────────────────────────────┬──────────────────────────────────┘
                               │ Bret: staging deploy + smoke test
┌──────────────────────────────▼──────────────────────────────────┐
│ PHASE 5 — RELEASE                                               │
│                                                                 │
│  Operations Manager → staging deploy + production plan         │
│  Test Manager       → manual verification + sign-off          │
│  Marketing Director → external communications                  │
│  Technical Author   → final documentation + presentation deck  │
│  Biographer         → public narrative update                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Iteration delivery cycle

Each iteration maps onto the epic phases as follows. The key principle: **research and design tasks run in background; implementation tasks run in foreground; testing and security gate every release.**

```
Iteration start
│
├── Background (parallel agents, no CTO involvement):
│     Phase 1 tasks for upcoming epics
│     Phase 2 tasks for next-iteration epics
│     TAU sweep of completed epics
│
├── Foreground (CTO review gates):
│     Phase 3: implementation for current epic
│     Phase 4: test + security review
│
└── Wrap-up (Bret required):
      Staging deploy (Docker restart)
      Smoke test (Test Manager checklist)
      Phase 5: production release if green
      PA + Technical Author iteration sweep
      Biographer update (if significant narrative event)
```

---

## Testing strategy

### Automated web testing
- **Tool**: Playwright (TST-004 — already in queue)
- **Scope**: actor-based E2E scenarios against staging
- **Trigger**: every implementation task completion; gated in Phase 4

### Automated Android testing (new infrastructure)
- **Device farm**: 3 physical Android devices (one per developer slot)
- **Tool**: Appium / Android UIAutomator2 orchestrated via test runner
- **Scope**: E2E journeys matching Playwright scenarios; crypto path regression
- **Infrastructure task**: TST-009 (see below) — Test Manager to design

### Cryptographic path unit testing
- **Coverage target**: 100% for auth/crypto paths (SEC-002)
- **Scope**: DEK wrap/unwrap, HKDF contexts, envelope format codec, HMAC tokenisation

### Security review gate (every Phase 4)
- Security Manager runs a targeted review against the epic's threat model
- Mandatory before Phase 5 promotion
- Output: SEC-NNN review note appended to the epic task

### Operational review gate (every Phase 4)
- Operations Manager reviews infrastructure impact, monitoring coverage, and deployment risk
- Output: OPS-NNN review note

---

## Task naming for epics

| Discipline | Prefix | Example |
|---|---|---|
| Research Manager | RES- | RES-005_window-capsule-notation |
| Technical Architect | ARCH- | ARCH-009_window-capsule-impl-spec |
| Legal | LEG- | LEG-003_window-capsule-claims-annex |
| Security Manager | SEC- | SEC-013_window-capsule-threat-model |
| Psychologist | PSY- | PSY-002_window-capsule-ux-impact |
| Philosopher | PHI- | PHI-002_window-capsule-ethics |
| Marketing Director | MKT- | MKT-002_window-capsule-positioning |
| Retirement Planner | RET- | RET-003_window-capsule-ip-value |
| Test Manager | TST- | TST-009_android-device-farm-design |
| Operations Manager | OPS- | OPS-004_window-capsule-deploy-plan |
| Technical Author | TAU- | TAU-001_window-capsule-notation |
| Developer | FEAT- / BUG- | FEAT-005_window-capsule-server |
| Biographer | BIO- | BIO-001_window-capsule-origin-story |

---

## Cross-cutting concerns (every epic)

These are not optional — they apply to every epic regardless of concept:

1. **Security review** — mandatory Phase 4 gate
2. **Automated test coverage** — Phase 4 output; no merge without tests
3. **Operations sign-off** — Phase 4 gate; no deploy without OPS review
4. **Technical Author documentation** — Phase 3/4; no release without docs
5. **Legal awareness** — every novel concept gets a LEG task in Phase 1; patent claims drafted before public disclosure
