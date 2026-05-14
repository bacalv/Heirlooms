# Heirlooms — Task Progress

Tasks move through: **queue** → **in-progress** → **done** (or **brainstorming** for ideas not yet actionable).

When running agents in parallel, an agent claims a task by moving its file from `queue/` to `in-progress/` and renaming it with its agent ID prefix (e.g. `in-progress/agent-abc123_TST-003.md`). Before claiming, the agent checks `in-progress/` for any task whose `touches:` overlaps with its target task — if there's a conflict, it picks a different task.

Each completed task may append a `## Spawned tasks` section listing new task IDs it created in `queue/`.

---

## Queue

| ID | Title | Category | Priority | Depends on |
|----|-------|----------|----------|------------|
| [TST-001](queue/TST-001_sanity-test-production.md) | Sanity test production | Testing | High | — |
| [TST-002](queue/TST-002_staging-first-user.md) | Create first user in staging | Testing | High | — |
| [TST-003](queue/TST-003_manual-staging-checklist.md) | Manual staging test checklist | Testing | High | TST-002 |
| [IOS-001](queue/IOS-001_qr-scanner-avfoundation.md) | Complete iOS QRScannerView (AVFoundation) | iOS | High | — |
| [IOS-002](queue/IOS-002_share-extension.md) | iOS Share Extension target | iOS | Medium | IOS-001 |
| [REF-001](queue/REF-001_rename-flow-to-trellis.md) | Rename Flow → Trellis across all platforms | Refactoring | Medium | — |
| [TST-004](queue/TST-004_playwright-e2e-suite.md) | Playwright E2E suite (actor-based, staging) | Testing | Medium | TST-003 |
| [SEC-001](queue/SEC-001_security-hardening.md) | Security hardening + threat model | Security | High | — |
| [SEC-002](queue/SEC-002_auth-crypto-coverage.md) | 100% coverage plan for auth/crypto paths | Security | High | TST-004 |
| [SEC-003](queue/SEC-003_client-security-review.md) | Client security flaw testing plan | Security | Medium | SEC-001 |
| [DOC-001](queue/DOC-001_uml-sequence-diagrams.md) | UML sequence diagrams from test output | Docs | Low | TST-004 |

## In Progress

| ID | Title | Category | Claimed by |
|----|-------|----------|------------|
| — | — | — | — |

## Done

| ID | Title | Category | Completed |
|----|-------|----------|-----------|
| [DONE-001](done/DONE-001_server-refactor-phases-1-8.md) | Server refactor phases 1–8 | Refactoring | 2026-05-14 |
| [DONE-002](done/DONE-002_logging-slf4j.md) | Replace println with SLF4J/Logback | Code Quality | 2026-05-14 |
| [DONE-003](done/DONE-003_json-dto-serialisation.md) | Replace hand-rolled JSON with DTOs | Code Quality | 2026-05-14 |
| [DONE-004](done/DONE-004_staging-environment.md) | Staging environment (test server, DB, domains, Android flavor) | Infrastructure | 2026-05-14 |
| [DONE-005](done/DONE-005_founding-user-seed.md) | Founding user seed + auth route fixes | Bug Fix | 2026-05-14 |

## Brainstorming

| ID | Title | Category |
|----|-------|----------|
| [IDEA-001](brainstorming/IDEA-001_trellis-naming.md) | Flow → Trellis naming research | Brainstorming |
