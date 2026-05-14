# Heirlooms — Task Progress

Tasks move through: **queue** → **in-progress** → **done** (or **brainstorming** for ideas not yet actionable).

**Agent coordination:** To claim a task, move its file from `queue/` to `in-progress/` and prefix the filename with your agent ID (e.g. `in-progress/agent-abc123_TST-003.md`). Before claiming, check `in-progress/` for `touches:` conflicts. Each completed task appends `## Completion notes` and moves to `done/`.

**Persona files:** `personalities/` — PA, Developer, DevManager, TestManager, OpsManager, SecurityManager, TechnicalArchitect.

---

## Currently Running

| Agent ID | Task | Started |
|----------|------|---------|
| developer-1 | [IOS-001](in-progress/IOS-001_qr-scanner-avfoundation.md) | 2026-05-14 |
| security | [SEC-001](in-progress/SEC-001_security-hardening.md) | 2026-05-14 |

---

## Queue

| ID | Title | Category | Priority | Assigned to | Depends on |
|----|-------|----------|----------|-------------|------------|
| [TST-003](queue/TST-003_manual-staging-checklist.md) | Manual staging test checklist | Testing | High | TestManager | — |
| [DOC-002](queue/DOC-002_doc-cleanup-history-backfill.md) | Doc cleanup + history backfill from git | Docs | Medium | TechnicalArchitect | — |
| [IOS-002](queue/IOS-002_share-extension.md) | iOS Share Extension target | iOS | Medium | Developer | IOS-001 |
| [REF-001](queue/REF-001_rename-flow-to-trellis.md) | Rename Flow → Trellis across all platforms | Refactoring | Medium | Developer | — |
| [TST-004](queue/TST-004_playwright-e2e-suite.md) | Playwright E2E suite (actor-based, staging) | Testing | Medium | Developer | TST-003 |
| [SEC-002](queue/SEC-002_auth-crypto-coverage.md) | 100% coverage plan for auth/crypto paths | Security | High | SecurityManager | TST-004 |
| [SEC-003](queue/SEC-003_client-security-review.md) | Client security flaw testing plan | Security | Medium | SecurityManager | SEC-001 |
| [DOC-001](queue/DOC-001_uml-sequence-diagrams.md) | UML sequence diagrams from test output | Docs | Low | Developer | TST-004 |

## In Progress

| ID | Title | Category | Assigned to | Claimed by |
|----|-------|----------|-------------|------------|
| [IOS-001](in-progress/IOS-001_qr-scanner-avfoundation.md) | Complete iOS QRScannerView (AVFoundation) | iOS | Developer | developer-1 |
| [SEC-001](in-progress/SEC-001_security-hardening.md) | Security hardening + threat model | Security | SecurityManager | security |

## Done

| ID | Title | Category | Completed |
|----|-------|----------|-----------|
| [DONE-001](done/DONE-001_server-refactor-phases-1-8.md) | Server refactor phases 1–8 | Refactoring | 2026-05-14 |
| [DONE-002](done/DONE-002_logging-slf4j.md) | Replace println with SLF4J/Logback | Code Quality | 2026-05-14 |
| [DONE-003](done/DONE-003_json-dto-serialisation.md) | Replace hand-rolled JSON with DTOs | Code Quality | 2026-05-14 |
| [DONE-004](done/DONE-004_staging-environment.md) | Staging environment (test server, DB, domains, Android flavor) | Infrastructure | 2026-05-14 |
| [DONE-005](done/DONE-005_founding-user-seed.md) | Founding user seed + auth route fixes | Bug Fix | 2026-05-14 |
| [ARCH-001](done/ARCH-001_m10-task-breakdown.md) | M10 — produce queue tasks for all four increments | Planning | 2026-05-14 |
| [TST-001](done/TST-001_sanity-test-production.md) | Sanity test production | Testing | 2026-05-14 |
| [TST-002](done/TST-002_staging-first-user.md) | Create first user in staging | Testing | 2026-05-14 |

## Brainstorming

| ID | Title | Category |
|----|-------|----------|
| [IDEA-001](brainstorming/IDEA-001_trellis-naming.md) | Flow → Trellis naming research | Brainstorming |
