# Heirlooms — Task Progress

Tasks move through: **queue** → **in-progress** → **done** (or **brainstorming** for ideas not yet actionable).

**Agent coordination:** To claim a task, move its file from `queue/` to `in-progress/` and prefix the filename with your agent ID (e.g. `in-progress/agent-abc123_TST-003.md`). Before claiming, check `in-progress/` for `touches:` conflicts. Each completed task appends `## Completion notes` and moves to `done/`.

**Persona files:** `personalities/` — PA, Developer, DevManager, TestManager, OpsManager, SecurityManager, TechnicalArchitect.

---

## Queue

| ID | Title | Category | Priority | Assigned to | Depends on |
|----|-------|----------|----------|-------------|------------|
| [BUG-014](queue/BUG-014_register-500-duplicate-device-id.md) | Register returns 500 instead of 409 when device_id already exists in wrapped_keys | Bug Fix | Medium | Developer | — |
| [BUG-015](queue/BUG-015_web-garden-shared-plot-row-still-stale.md) | Web garden shared plot row still stale after trellis routing — BUG-012 fix incomplete | Bug Fix | Medium | Developer | — |
| [BUG-016](queue/BUG-016_shared-plot-items-not-decrypting-for-member.md) | Shared plot items uploaded from web not decrypting for Android member (Fire 1) | Bug Fix | High | Developer | — |
| [UX-003](queue/UX-003_garden-polling-interval.md) | Garden polling interval — 5s default, Android configurable (2/5/10/30s) | UX | Medium | Developer | — |
| [BUG-006](queue/BUG-006_android-nav-backstack.md) | Android nav back-stack not cleared from burger menu | Bug Fix | Medium | Developer | — |
| [TST-006](queue/TST-006_android-remote-control-investigation.md) | Investigate remote-controlled Android testing for E2E automation | Testing | Medium | TestManager | — |
| [UX-002](queue/UX-002_closed-plot-visual-indicator.md) | Closed plots should show a locked state — disable approve/share actions | UX | Medium | Developer | — |
| [TST-004](queue/TST-004_playwright-e2e-suite.md) | Playwright E2E suite (actor-based, staging) | Testing | Medium | Developer | — |
| [SEC-002](queue/SEC-002_auth-crypto-coverage.md) | 100% coverage plan for auth/crypto paths | Security | High | SecurityManager | TST-004 |
| [BUG-002](queue/BUG-002_remove-didnt-take-messaging.md) | Remove "didn't take" messaging — replace with plain error strings | Bug Fix | Low | Developer | — |
| [BUG-005](queue/BUG-005_thumbnail-rotation.md) | Investigate thumbnail rotation — first upload shows incorrect orientation | Bug Fix | Low | Developer | — |
| [UX-001](queue/UX-001_shared-plot-button-tap-targets.md) | Android: shared plot action buttons have insufficient tap targets | UX | Low | Developer | — |
| [WEB-001](queue/WEB-001_friends-list-page.md) | Web: friends list page | Feature | Low | Developer | — |
| [DOC-001](queue/DOC-001_uml-sequence-diagrams.md) | UML sequence diagrams from test output | Docs | Low | Developer | TST-004 |
| [OPS-002](queue/OPS-002_new-environment-setup-guide.md) | New environment setup guide | Operations | Low | OpsManager | — |

## In Progress

*(none)*

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
| [IOS-002](done/IOS-002_share-extension.md) | iOS Share Extension target | iOS | 2026-05-15 |
| [SEC-001](done/SEC-001_security-hardening.md) | Security hardening + threat model | Security | 2026-05-15 |
| [BUG-001](done/BUG-001_keychain-master-key-slot-collision.md) | Keychain slot collision — master key vs plot key | Bug Fix | 2026-05-15 |
| [SEC-003](done/SEC-003_client-security-review.md) | Client security flaw testing plan | Security | 2026-05-15 |
| [SEC-004](done/SEC-004_rate-limiting-auth-logging.md) | Rate limiting + failed-login logging | Security | 2026-05-15 |
| [SEC-005](done/SEC-005_session-invalidation-mime-validation.md) | Session invalidation + MIME validation | Security | 2026-05-15 |
| [SEC-006](done/SEC-006_criteria-date-input-validation.md) | Criteria date input validation | Security | 2026-05-15 |
| [ARCH-002](done/ARCH-002_behavioral-spec-diagrams.md) | Behavioral spec diagrams — use case inventory + Mermaid sequences | Docs | 2026-05-15 |
| [REF-001](done/REF-001_rename-flow-to-trellis.md) | Rename Flow → Trellis across all platforms | Refactoring | 2026-05-15 |
| [BUG-003](done/BUG-003_upload-worker-hardcoded-prod-url.md) | UploadWorker hardcoded prod URL fix | Bug Fix | 2026-05-15 |
| [BUG-004](done/BUG-004_garden-uploads-not-showing.md) | Garden uploads not showing — system plot fix | Bug Fix | 2026-05-15 |
| [BUG-007](done/BUG-007_web-no-sharing-key-generation.md) | Web app never generates sharing key — web-only users can't be invited to shared plots | Bug Fix | 2026-05-15 |
| [TST-003](done/TST-003_manual-staging-checklist.md) | Manual staging test checklist — 6 journeys, 11 issues found | Testing | 2026-05-15 |
| [SEC-007](done/SEC-007_android-session-token-keystore.md) | Android: encrypt session token with Keystore (EncryptedSharedPreferences) | Security | 2026-05-15 |
| [BUG-008](done/BUG-008_invite-url-hardcoded-prod-domain.md) | Android staging flavor generates invite links with production domain | Bug Fix | 2026-05-15 |
| [SEC-009](done/SEC-009_android-biometric-flag-secure.md) | Android: FLAG_SECURE on vault Activities (Part 1 only; Part 2 biometric deferred) | Security | 2026-05-15 |
| [BUG-009](done/BUG-009_staging-approval-sharing-key-not-loaded.md) | Staging approval fails if Garden not visited — sharing key eager-load fix | Bug Fix | 2026-05-15 |
| [TST-005](done/TST-005_playwright-infrastructure.md) | Playwright infrastructure setup | Testing | 2026-05-15 |
| [FEAT-001](done/FEAT-001_invite-link-friend-connect.md) | Invite link friend-connect for existing users | Feature | 2026-05-15 |
| [FEAT-002](done/FEAT-002_manual-add-to-shared-plot.md) | Manual "add to shared plot" from photo detail — Android + web | Feature | 2026-05-15 |
| [ARCH-005](done/ARCH-005_envelope-format-amendment.md) | Envelope format amendment — add plot key algo, reserve M11 IDs | Architecture | 2026-05-15 |
| [ARCH-004](done/ARCH-004_connections-data-model.md) | Connections data model brief — identity layer for M11 | Architecture | 2026-05-15 |
| [ARCH-003](done/ARCH-003_m11-capsule-crypto-brief.md) | M11 capsule cryptography brief | Architecture | 2026-05-15 |
| [TST-007](done/TST-007_manual-staging-checklist-v054.md) | Manual staging checklist v0.54 — conditional pass, 4 new bugs logged | Testing | 2026-05-15 |
| [BUG-010](done/BUG-010_members-cant-create-trellis-for-shared-plot.md) | Members can't create trellis for shared plot | Bug Fix | 2026-05-15 |
| [BUG-011](done/BUG-011_web-trellis-form-partial-rename.md) | Web trellis form "Flow name" / "Create flow" label fix | Bug Fix | 2026-05-15 |
| [BUG-012](done/BUG-012_web-garden-stale-after-trellis-route.md) | Web garden doesn't update shared plot row after trellis routing | Bug Fix | 2026-05-15 |
| [SEC-008](done/SEC-008_web-csp-session-token.md) | Web: CSP header + session token off localStorage | Security | 2026-05-15 |
| [SEC-010](done/SEC-010_git-history-secret-scan.md) | Git history secret scan + prevention hook | Security | 2026-05-15 |
| [OPS-001](done/OPS-001_deployment-runbook.md) | Deployment runbook | Operations | 2026-05-15 |
| [BUG-015](done/developer-4_BUG-015_web-garden-shared-plot-row-still-stale.md) | Web garden shared plot row still stale — excludeIds leak fix | Bug Fix | 2026-05-15 |
| [UX-003](done/UX-003_garden-polling-interval.md) | Garden polling 5s default; Android configurable (2/5/10/30s) | UX | 2026-05-15 |
| [BUG-014](done/developer-3_BUG-014_register-500-duplicate-device-id.md) | Register 409 for duplicate device_id + atomic transaction | Bug Fix | 2026-05-15 |
| [BUG-016](done/developer-2_BUG-016_shared-plot-items-not-decrypting-for-member.md) | Shared plot items not decrypting — sharing key overwrite on web pairing fixed | Bug Fix | 2026-05-15 |
| [BUG-013](done/developer-1_BUG-013_detail-view-triggers-rotation.md) | Detail view EXIF rotation no longer silently staged as server save | Bug Fix | 2026-05-15 |
| [ARCH-006](done/ARCH-006_tlock-provider-interface.md) | TimeLock provider interface — stub first, drand later; blinding scheme resolved | Architecture | 2026-05-15 |

## Brainstorming

| ID | Title | Category |
|----|-------|----------|
| [IDEA-001](brainstorming/IDEA-001_trellis-naming.md) | Flow → Trellis naming research | Brainstorming |
