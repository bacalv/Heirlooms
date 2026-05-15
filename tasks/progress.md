# Heirlooms — Task Progress

Tasks move through: **queue** → **in-progress** → **done** (or **brainstorming** for ideas not yet actionable).

**Agent coordination:** To claim a task, move its file from `queue/` to `in-progress/` and prefix the filename with your agent ID (e.g. `in-progress/agent-abc123_TST-003.md`). Before claiming, check `in-progress/` for `touches:` conflicts. Each completed task appends `## Completion notes` and moves to `done/`.

**Persona files:** `personalities/` — PA, Developer, DevManager, TestManager, OpsManager, SecurityManager, TechnicalArchitect.

---

## Currently Running

| ID | Title | Agent | Branch | Started |
|----|-------|-------|--------|---------|
| [TST-003](in-progress/TST-003_manual-staging-checklist.md) | Manual staging test checklist | TestManager (interactive) | — | 2026-05-15 |
| [ARCH-002](in-progress/ARCH-002_behavioral-spec-diagrams.md) | Behavioral spec diagrams | architect | agent/architect/ARCH-002 | 2026-05-15 |
| [REF-001](in-progress/REF-001_rename-flow-to-trellis.md) | Rename Flow → Trellis | developer-1 | agent/developer-1/REF-001 | 2026-05-15 |
| [IOS-002](in-progress/IOS-002_share-extension.md) | iOS Share Extension target | developer-2 | agent/developer-2/IOS-002 | 2026-05-15 |
| [BUG-001](in-progress/BUG-001_keychain-master-key-slot-collision.md) | Keychain slot collision fix | developer-3 | agent/developer-3/BUG-001 | 2026-05-15 |
| [SEC-003](in-progress/SEC-003_client-security-review.md) | Client security flaw testing plan | security-audit | agent/security-audit/SEC-003 | 2026-05-15 |
| [SEC-004](in-progress/SEC-004_rate-limiting-auth-logging.md) | Rate limiting + failed-login logging | security | agent/security/SEC-004 | 2026-05-15 |
| [SEC-005](in-progress/SEC-005_session-invalidation-mime-validation.md) | Session invalidation + MIME validation | security | agent/security/SEC-004 | 2026-05-15 |
| [SEC-006](in-progress/SEC-006_criteria-date-input-validation.md) | Criteria date input validation | security | agent/security/SEC-004 | 2026-05-15 |

---

## Queue

| ID | Title | Category | Priority | Assigned to | Depends on |
|----|-------|----------|----------|-------------|------------|
| [TST-005](queue/TST-005_playwright-infrastructure.md) | Playwright infrastructure setup | Testing | Medium | TestManager | TST-003, ARCH-002 |
| [TST-004](queue/TST-004_playwright-e2e-suite.md) | Playwright E2E suite (actor-based, staging) | Testing | Medium | Developer | TST-005 |
| [SEC-002](queue/SEC-002_auth-crypto-coverage.md) | 100% coverage plan for auth/crypto paths | Security | High | SecurityManager | TST-004 |
| [DOC-001](queue/DOC-001_uml-sequence-diagrams.md) | UML sequence diagrams from test output | Docs | Low | Developer | TST-004 |

## In Progress

*(see Currently Running above)*

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
| [DONE-M5](done/DONE-M5_capsules.md) | Milestone 5 — Capsules (web + backend) | Feature | 2026-05-08 |
| [DONE-M6](done/DONE-M6_garden-explore.md) | Milestone 6 — Garden / Explore restructure | Feature | 2026-05-08 |
| [DONE-M7](done/DONE-M7_vault-e2ee.md) | Milestone 7 — Vault E2EE | Feature | 2026-05-09 |
| [DONE-M8](done/DONE-M8_multi-user-access.md) | Milestone 8 — Multi-user access | Feature | 2026-05-11 |
| [DONE-M9](done/DONE-M9_friends-sharing.md) | Milestone 9 — Friends, item sharing, Android plot management | Feature | 2026-05-12 |
| [DONE-M10](done/DONE-M10_shared-plots.md) | Milestone 10 — Shared plots (all four increments E1–E4) | Feature | 2026-05-12 |
| [DONE-iOS](done/DONE-iOS_ios-app-implementation.md) | iOS app implementation | Feature | 2026-05-14 |
| [DONE-BUG-v051](done/DONE-BUG-v051_shared-plot-membership.md) | Post-M10 fixes — shared plot membership overhaul (v0.50.1–v0.51.5) | Bug Fix | 2026-05-14 |
| [DONE-BUG-v053](done/DONE-BUG-v053_server-refactor-security.md) | Post-M10 fixes — server refactor + security hardening (v0.52.0–v0.53.1) | Bug Fix | 2026-05-14 |
| [DOC-002](done/DOC-002_doc-cleanup-history-backfill.md) | Doc cleanup + history backfill from git | Docs | 2026-05-14 |
| [IOS-001](done/IOS-001_qr-scanner-avfoundation.md) | Complete iOS QRScannerView (AVFoundation) | iOS | 2026-05-15 |
| [SEC-001](done/SEC-001_security-hardening.md) | Security hardening + threat model | Security | 2026-05-15 |
| [BUG-001](done/BUG-001_keychain-master-key-slot-collision.md) | Keychain slot collision — master key vs plot key | Bug Fix | 2026-05-15 |
| [SEC-004](done/SEC-004_rate-limiting-auth-logging.md) | Rate limiting + failed-login logging | Security | 2026-05-15 |
| [SEC-005](done/SEC-005_session-invalidation-mime-validation.md) | Session invalidation + MIME validation | Security | 2026-05-15 |
| [SEC-006](done/SEC-006_criteria-date-input-validation.md) | Criteria date input validation | Security | 2026-05-15 |

## Brainstorming

| ID | Title | Category |
|----|-------|----------|
| [IDEA-001](brainstorming/IDEA-001_trellis-naming.md) | Flow → Trellis naming research | Brainstorming |
