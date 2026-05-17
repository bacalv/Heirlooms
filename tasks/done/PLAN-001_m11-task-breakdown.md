---
id: PLAN-001
title: M11 task breakdown — create agent-sized task files from ARCH-010 waves
category: Planning
priority: High
assigned_to: DevManager
depends_on: [ARCH-010]
touches:
  - tasks/queue/
branch: M11
---

# PLAN-001 — M11 task breakdown

## Context

M11 is the next major server milestone: connections model, executor nominations, Shamir
share distribution, capsule sealing with tlock, and key delivery. The design is complete —
ARCH-003, ARCH-004, ARCH-005, ARCH-006, and ARCH-010 are all done and approved.

ARCH-010 defines 19 endpoints across 7 implementation waves. The CTO has approved a
server-first strategy: app development is frozen; M11 proceeds on the `M11` branch with
local-stack iteration sign-off (no cloud deploys between iterations).

Your job is to turn ARCH-010's 7 waves into discrete, agent-sized task files that
Developer agents can pick up cold and implement.

## Source material

Read all of these before writing tasks:

- `docs/briefs/ARCH-010_m11-api-surface-and-migration-sequencing.md` — the primary
  reference: endpoint list, wave ordering, validation sequences, migration notes
- `docs/briefs/ARCH-003_m11-capsule-crypto-brief.md` — crypto spec
- `docs/briefs/ARCH-004_connections-data-model.md` — connections schema and lifecycle
- `docs/briefs/ARCH-006_tlock-provider-interface.md` — TimeLockProvider interface
- `docs/envelope_format.md` — envelope binary format

## Task structure to produce

Create one task file per wave (or split a wave if it's too large for a single agent
session). Use the `DEV-` prefix, starting from `DEV-001`.

Each task file must include:

- Front-matter: `id`, `title`, `category: Feature`, `priority: High`,
  `assigned_to: Developer`, `depends_on: [list]`, `touches: [list of files/dirs]`,
  `branch: M11`
- **Context** — what this wave is for and what must already be done before it starts
- **Endpoints to implement** — exact method + path from ARCH-010, one-line description each
- **Implementation reference** — which sections of ARCH-010 (and other briefs) the
  developer must read
- **Acceptance criteria** — unit tests required, what a passing local stack run looks like
- **Completion notes** placeholder

## Wave → task mapping (guidance)

| Wave | Content | Suggested split |
|------|---------|-----------------|
| Wave 0 | V31 + V32 Flyway migrations only | 1 task (DEV-001) |
| Wave 1 | 5 connections CRUD endpoints | 1 task (DEV-002) |
| Wave 2 | 6 executor nomination lifecycle endpoints | 1 task (DEV-003) |
| Wave 3 | 1 capsule recipient linking endpoint | Batch into DEV-002 or DEV-003 |
| Wave 4 | 3 executor share distribution endpoints | 1 task (DEV-004) |
| Wave 5 | `/seal` endpoint — 16-step validation | 1 task (DEV-005) — this is the largest |
| Wave 6 | `/tlock-key` delivery | 1 task (DEV-006) |
| Wave 7 | 2 read-path amendments | 1 task or batch with DEV-006 |

Use your judgement on batching. Wave 5 (`/seal`) must always be its own task — it is
the most complex endpoint and has security-critical invariants (I-1 through I-8 in
ARCH-010 §6).

## Constraints

- Each task must be completable in a single agent session (estimate: ≤ 4 hours of
  agent work). Split if in doubt.
- `depends_on:` fields must reflect the ARCH-010 wave ordering — a wave cannot start
  before its predecessors are done.
- Every task must require unit tests as acceptance criteria. No exceptions.
- Do not include any client-app work (Android, web, iOS) — M11 is server-only.
- Do not write any implementation code — task files only.

## Deliverable

- Task files `DEV-001.md` through `DEV-006` (or however many you split to) written
  to `tasks/queue/` in your workspace.
- A short summary appended to this task file under `## Completion notes` describing
  how you split the waves and why.

## Completion notes

Six task files produced: DEV-001 through DEV-006 in `tasks/queue/`.

**Wave → task mapping decisions:**

- **DEV-001 (Wave 0 — migrations):** One task for both migrations. V33 and V34 are
  the correct file numbers for this repo — V31 and V32 are already in use for
  `shared_plot_trellis_staging` and `add_require_biometric_to_users`. The task makes
  this correction explicit so the Developer does not create a Flyway version conflict.
  Estimated 2 hours.

- **DEV-002 (Wave 1 — connections CRUD):** All 5 connections endpoints in one task.
  These are straightforward CRUD against a single table with no crypto. Well within a
  single agent session. Estimated 3 hours.

- **DEV-003 (Waves 2+3 — nominations + recipient link):** Wave 3 is a single PATCH
  endpoint with low complexity that shares the exact same connection-ID prerequisite
  as Wave 2. Batching keeps DEV-004 unblocked sooner and avoids a trivially small
  standalone task. The nomination state machine is the main complexity here. Estimated
  4 hours (at the upper limit — the lifecycle state enforcement is non-trivial).

- **DEV-004 (Wave 4 — share distribution):** Three endpoints handling opaque wrapped
  share blobs. Moderate complexity due to the per-share validation sequence and the
  index-coverage check. Estimated 3 hours.

- **DEV-005 (Wave 5 — /seal):** Always its own task per the task brief and ARCH-010.
  The most complex endpoint: 16 ordered validation steps, TimeLockProvider interface
  + StubTimeLockProvider implementation, blinding scheme invariants, multi-path fallback
  rule, and atomic write phase. Also responsible for the TimeLockProvider and
  StubTimeLockProvider implementations (new `crypto/tlock/` package). Estimated 4
  hours (tight; developer should not cut corners on the 16-test unit test suite).

- **DEV-006 (Waves 6+7 — tlock-key + read amendments):** Wave 7 is two small read
  additions. Batched with Wave 6 because both depend on DEV-005's sealed capsule state
  and neither has write logic. The `/tlock-key` logging prohibition is the main
  complexity. Estimated 3 hours.

**Total estimated agent work:** 19 hours across 6 tasks.

**Key corrections applied:**
- Migration file numbers corrected from V31/V32 (per ARCH-010 doc) to V33/V34
  (correct for this repo's actual migration sequence).
- ARCH-010 §5 explicitly cited as superseding ARCH-003 §4 and ARCH-006 §5 for the
  seal validation sequence in DEV-005.
- Invariants I-1 through I-8 from ARCH-010 §6 called out verbatim in DEV-005.
- Logging prohibitions for `tlock_dek_tlock` called out explicitly in DEV-005 and
  DEV-006 with specific assertions required in integration tests.
