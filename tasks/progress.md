# Heirlooms — Task Progress

Tasks move through: **queue** → **in-progress** → **done** (or **brainstorming** for ideas not yet actionable).

**Agent coordination:** To claim a task, move its file from `queue/` to `in-progress/` and prefix the filename with your agent ID (e.g. `in-progress/agent-abc123_TST-003.md`). Before claiming, check `in-progress/` for `touches:` conflicts. Each completed task appends `## Completion notes` and moves to `done/`.

**Persona files:** `personalities/` — PA, Developer, DevManager, TestManager, OpsManager, SecurityManager, TechnicalArchitect, ResearchManager.

**Research tasks** use the `RES-` prefix and are owned by the ResearchManager. Research briefs are written to `docs/research/`. Simulation tasks use `SIM-` prefix and produce throw-away session notes only — they do not feed the task queue.

---

## Queue

| ID | Title | Category | Priority | Assigned to | Depends on |
|---|---|---|---|---|---|
| [LEG-003](queue/LEG-003_patent-assessment-rес005-presence-gated-constructions.md) | Patent assessment — RES-005 presence-gated delivery and count-conditional trigger | Legal | High | Legal | LEG-001, LEG-002 |
| [BUG-025](queue/BUG-025_web-friends-no-nav-link.md) | Web Friends page has no navigation link | Bug Fix | Low | Developer | — |
| [BUG-026](queue/BUG-026_invite-link-no-auto-route.md) | Invite link doesn't auto-route token for logged-in users | Bug Fix | Low | Developer | — |
| [STR-001](queue/STR-001_strategic-synthesis-top-20-features-direction-brief.md) | Strategic synthesis — top 20 features and direction brief for CTO | Strategy | Medium | PA | *(ARCH-008 done — unblocked)* |
| [BIO-002](queue/BIO-002_hi-claude-book-and-blog-concept.md) | "Hi Claude..." — book and blog series concept development | Biography | Medium | Biographer | BIO-001 |
| [REF-002](queue/REF-002_tag-to-label-rename.md) | Tag → Label rename across all platforms and documentation | Refactoring | Medium | Developer | — |
| [TST-006](queue/TST-006_android-remote-control-investigation.md) | Investigate remote-controlled Android testing for E2E automation | Testing | Medium | TestManager | — |
| [TST-012](queue/TST-012_manual-staging-checklist-v056.md) | Manual staging checklist v0.56 — **held until staging ready** | Testing | High | TestManager | *(all v0.56 branches merged)* |
| [DEV-005](queue/DEV-005.md) | M11 Wave 5 — /seal endpoint (16-step validation) | Feature | High | Developer | ~~DEV-004~~ ✓ |
| [DEV-006](queue/DEV-006.md) | M11 Waves 6+7 — /tlock-key delivery + read-path amendments | Feature | High | Developer | DEV-005 |
| [DOC-001](queue/DOC-001_uml-sequence-diagrams.md) | UML sequence diagrams from test output | Docs | Low | Developer | TST-004 ✓ |
| [RES-005](queue/RES-005_glossary-self-reference-audit.md) | Glossary self-reference audit — ensure every cited term has an entry | Research | Low | ResearchManager | — |
| [OPS-003](queue/OPS-003_pre-production-staging-environment.md) | Pre-production staging environment — prod-snapshot + anonymisation pipeline | Operations | Low | OpsManager | — |
| [UX-001](queue/UX-001_shared-plot-button-tap-targets.md) | Android: shared plot action buttons have insufficient tap targets | UX | Low | Developer | — |

## In Progress

*(none)*

## Done

| ID | Title | Category | Completed |
|----|-------|----------|-----------|
| [DEV-004](done/DEV-004.md) | M11 Wave 4 — executor share distribution (3 endpoints, 36 tests) | Feature | 2026-05-17 |
| [DEV-003](done/DEV-003.md) | M11 Waves 2+3 — executor nominations + recipient linking (7 endpoints, 63 tests) | Feature | 2026-05-17 |
| [DEV-002](done/DEV-002.md) | M11 Wave 1 — connections CRUD endpoints (5 endpoints, 35 tests) | Feature | 2026-05-17 |
| [TOOL-001](done/TOOL-001_kotlin-api-client-module.md) | Kotlin API client module — Phase 1 capsule lifecycle CLI | Tools | 2026-05-17 |
| [DEV-001](done/DEV-001.md) | M11 Wave 0 — V33 + V34 Flyway migrations (schema only) | Feature | 2026-05-17 |
| [PLAN-001](done/PLAN-001_m11-task-breakdown.md) | M11 task breakdown — DEV-001 through DEV-006 created | Planning | 2026-05-17 |
| [ARCH-014](done/ARCH-014_res005-technical-impact-assessment.md) | Technical impact assessment — RES-005 presence-gated delivery and count-conditional trigger | Architecture | 2026-05-17 |
| [ARCH-015](done/ARCH-015_api-stability-contract.md) | API stability contract — frozen surface, policy, 8 regression pairs | Architecture | 2026-05-17 |
| [TST-014](done/TST-014_m11-local-stack-feasibility.md) | M11 local stack — feasibility and design brief | Testing | 2026-05-17 |
| [TST-013](done/TST-013_sharing-flow-integration-test.md) | Sharing flow integration test — 6 tests (4 integration + 2 unit) | Testing | 2026-05-17 |
| [BUG-029](done/BUG-029_web-pairing-no-device-entry.md) | Web pairing — register persistent device key after pairing | Bug Fix | 2026-05-17 |
| [BUG-023](done/BUG-023_passphrase-save-409-after-web-pairing.md) | Passphrase save 409 after web pairing — guard registerDevice, fix PairPage | Bug Fix | 2026-05-17 |
| [BUG-024](done/BUG-024_garden-inline-tag-no-trellis-routing.md) | Garden inline tag now sends prewrappedDeks for trellis routing | Bug Fix | 2026-05-17 |
| [BUG-027](done/BUG-027_garden-not-loading-on-launch.md) | Garden loads on launch; staging counts refresh after tagging | Bug Fix | 2026-05-17 |
| [BUG-028](done/BUG-028_biometric-gate-not-enforcing.md) | Biometric gate — rememberSaveable → remember, gate now enforces | Bug Fix | 2026-05-17 |
| [TST-004](done/TST-004_playwright-e2e-suite.md) | Playwright E2E suite — 5 journeys, 17 tests | Testing | 2026-05-17 |
| [ARCH-011](done/ARCH-011_unified-messaging-model.md) | Unified messaging model design brief | Architecture | 2026-05-17 |
| [ARCH-012](done/ARCH-012_android-package-restructure-brief.md) | Android 7-phase restructure brief | Architecture | 2026-05-17 |
| [ARCH-013](done/ARCH-013_web-app-restructure-brief.md) | Web 8-phase restructure brief | Architecture | 2026-05-17 |
| [OPS-002](done/OPS-002_new-environment-setup-guide.md) | New environment setup guide | Operations | 2026-05-17 |
| [SEC-002](done/SEC-002_auth-crypto-coverage.md) | Auth/crypto coverage — 128 unit tests (phases 1+2) | Security | 2026-05-17 |
| [TST-010](done/TST-010_manual-staging-checklist-v055.md) | Manual staging checklist v0.55 — conditional pass, 7 bugs logged | Testing | 2026-05-16 |
| [OPS-004](done/OPS-004_v055-test-environment-deployment.md) | v0.55 test environment deployment | Operations | 2026-05-16 |
| [SEC-016](done/SEC-016_ios-ats-infoplist.md) | iOS — add committed Info.plist with ATS domain restriction | Security | 2026-05-16 |
| [SEC-017](done/SEC-017_ios-memory-zeroing-temp-file-cleanup.md) | iOS — zero DEK byte buffers after use, delete video temp files on dismiss | Security | 2026-05-16 |
| [SEC-014](done/SEC-014_ios-vault-background-privacy-screen.md) | iOS vault — privacy screen on backgrounding | Security | 2026-05-16 |
| [TAU-001](done/TAU-001_capsule-construction-guide.md) | Capsule construction guide — formal notation, diagrams, print viz, Manim scripts | Documentation | 2026-05-16 |
| [ARCH-008](done/ARCH-008_chained-capsule-feasibility-and-care-mode-architecture.md) | Chained capsule feasibility and Care Mode architecture assessment | Architecture | 2026-05-16 |
| [ARCH-009](done/ARCH-009_executor-revocation-and-key-rotation-brief.md) | Executor nomination — revocation and key rotation architecture brief | Architecture | 2026-05-16 |
| [ARCH-010](done/ARCH-010_m11-api-surface-and-migration-sequencing.md) | M11 API surface and migration sequencing brief | Architecture | 2026-05-16 |
| [TST-008](done/TST-008_shared-plot-e2e-smoke-test.md) | Shared plot E2E smoke test — formalise spec and identify automation strategy | Testing | 2026-05-16 |
| [TST-009](done/TST-009_android-device-farm-design.md) | Android device farm design — 3-device automated test infrastructure | Testing | 2026-05-16 |
| [WEB-002](done/WEB-002_web-invite-generation.md) | Web — generate and share invite links | Feature | 2026-05-16 |
| [FEAT-003](done/FEAT-003_android-account-pairing-recovery.md) | Android account pairing / recovery — pair a fresh Android install to an existing account | Feature | 2026-05-16 |
| [FEAT-003a](done/FEAT-003a_android-pairing-recovery-spike.md) | Android account pairing/recovery — design spike (4 open questions) | Feature | 2026-05-16 |
| [FEAT-004](done/FEAT-004_android-invite-friend-from-friends-screen.md) | Android — invite a friend from the Friends screen | Feature | 2026-05-16 |
| [UX-002](done/UX-002_closed-plot-visual-indicator.md) | Closed plots should show a locked state — disable approve/share actions | UX | 2026-05-16 |
| [SEC-011](done/SEC-011_device-revocation.md) | Device revocation — allow users to remove old devices from Devices & Access | Security | 2026-05-16 |
| [BUG-019](done/BUG-019_register-duplicate-device-id-wrong-error-message.md) | Registration shows "Username already exists" for duplicate device_id (409) collision | Bug Fix | 2026-05-16 |
| [SEC-012](done/SEC-012_tag-metadata-leakage-accepted-risk.md) | Tag metadata leakage — document and disclose accepted residual risk | Security | 2026-05-16 |
| [BUG-021](done/BUG-021_video-duration-zero-on-detail-view.md) | Video detail view shows 0-second duration — metadata not extracted on upload | Bug Fix | 2026-05-16 |
| [PUB-001](done/PUB-001_academic-paper-temporal-e2ee-capsule-construction.md) | Academic paper — temporal E2EE capsule with multi-layer recovery | Publication | 2026-05-16 |
| [SIM-001](done/SIM-001_trustless-expiry-impossibility.md) | Simulation: trustless expiry — weakest possible construction without custodians | Simulation | 2026-05-16 |
| [BIO-001](done/BIO-001_bret-web-research.md) | Bret — brief web research and biographical profile | Biography | 2026-05-16 |
| [RES-004](done/RES-004_chained-capsule-cryptographic-assessment.md) | Chained capsule — cryptographic novelty assessment | Research | 2026-05-16 |
| [MKT-001](done/MKT-001_strategic-direction-thinking-report.md) | Strategic direction thinking report | Marketing | 2026-05-16 |
| [PSY-001](done/PSY-001_grief-reframe-care-mode-and-experience-psychology.md) | Grief reframe, Care Mode dignity, Experience psychology | Psychology | 2026-05-16 |
| [PHI-002](done/PHI-002_capsule-narrative-framing.md) | Capsule narrative framing — consent, futurity, and the key ceremony | Philosophy | 2026-05-16 |
| [PHI-001](done/PHI-001_ethics-of-conditional-delivery-consent-and-digital-legacy.md) | Ethics of conditional delivery, consent, long-horizon promise | Philosophy | 2026-05-16 |
| [LEG-002](done/LEG-002_care-mode-consent-chained-capsule-ip.md) | Care Mode consent, chained capsule intellectual property, white-label | Legal | 2026-05-16 |
| [RET-002](done/RET-002_three-segment-valuation-and-retirement-implications.md) | Three-segment valuation and retirement implications | Retirement | 2026-05-16 |
| [LEG-001](done/LEG-001_patent-assessment-window-capsule.md) | Patent assessment — window capsule construction and related novel IP | Legal | 2026-05-16 |
| [ARCH-007](done/ARCH-007_e2ee-tag-scheme.md) | E2EE tag scheme — HMAC token identifiers + encrypted display names | Architecture | 2026-05-16 |
| [BUG-022](done/BUG-022_web-detail-view-blank-for-shared-plot-image.md) | Web detail view blank for shared plot items — full image DEK not decrypted with plot key | Bug Fix | 2026-05-16 |
| [BUG-020](done/BUG-020_shared-plot-auto-approve-needs-client-dek-rewrap.md) | Shared plot trellis auto-approve — client-side DEK re-wrap to avoid mandatory staging | Bug Fix | 2026-05-16 |
| [RES-002](done/RES-002_window-capsule-expiry-cryptography.md) | Window capsule — cryptographic expiry, literature review and construction brief | Research | 2026-05-16 |
| [RES-003](done/RES-003_pqc-migration-readiness-brief.md) | PQC migration readiness — algorithm break response plan for Technical Architect | Research | 2026-05-16 |
| [WEB-001](done/WEB-001_friends-list-page.md) | Web: friends list page | Feature | 2026-05-16 |
| [RES-001](done/RES-001_crypto-threat-horizon-40kft.md) | Cryptographic threat horizon — initial 40,000ft survey for CTO | Research | 2026-05-16 |
| [RET-001](done/RET-001_initial-retirement-assessment.md) | Initial retirement assessment — questionnaire, intelligence gathering, and key decisions | Retirement Planning | 2026-05-16 |
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
| [BUG-006](done/developer-1_BUG-006_android-nav-backstack.md) | Android burger menu nav clears back-stack via navigateFromBurger() | Bug Fix | 2026-05-15 |
| [BUG-002](done/BUG-002_remove-didnt-take-messaging.md) | Remove "didn't take" messaging — replace with plain error strings | Bug Fix | 2026-05-15 |
| [BUG-005](done/developer-3_BUG-005_thumbnail-rotation.md) | Thumbnail generator now applies EXIF orientation — rotation baked into pixels | Bug Fix | 2026-05-15 |
| [BUG-017](done/developer-4_BUG-017_android-plot-key-not-loaded-after-accept-invite.md) | Android: eagerly fetch plot key after accepting invite so thumbnails decrypt without restart | Bug Fix | 2026-05-15 |
| [BUG-018](done/developer-1_BUG-018_shared-plot-trellis-no-staging-dek-not-rewrapped.md) | Shared-plot trellis always requires staging — prevent items flowing without DEK re-wrap | Bug Fix | 2026-05-15 |
| [TST-011](done/TST-011_android-device-farm-setup.md) | Android device farm setup — 3-device automated test infrastructure (scripts, Maestro flows, Espresso crypto smoke test, CI workflows) | Testing | 2026-05-16 |
| [SEC-015](done/SEC-015_biometric-gate-account-setting.md) | Biometric gate — account-level setting synced via server | Security | 2026-05-16 |
| [SEC-013](done/SEC-013_ios-client-security-review.md) | iOS client security review — parity with SEC-003 | Security | 2026-05-16 |
| [RES-001](done/RES-001_crypto-threat-horizon-40kft.md) | Cryptographic threat horizon — initial 40,000ft survey for CTO | Research | 2026-05-16 |
| [RET-001](done/RET-001_initial-retirement-assessment.md) | Initial retirement assessment — questionnaire, intelligence gathering, and key decisions | Retirement Planning | 2026-05-16 |

## Brainstorming

| ID | Title | Category |
|----|-------|----------|
| [IDEA-001](brainstorming/IDEA-001_trellis-naming.md) | Flow → Trellis naming research | Brainstorming |
