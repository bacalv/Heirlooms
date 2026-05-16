# Dev Manager Queue Review — 2026-05-16

Prepared by: Dev Manager  
Scope: All outstanding queue tasks as of 2026-05-16  
Purpose: Input to PA delivery plan synthesis

---

## Inconsistency Flag: BUG-017 and BUG-018

Both tasks appear in `tasks/queue/` with in-progress filenames
(`developer-4_BUG-017_...` and `developer-1_BUG-018_...`) but are confirmed
**done** — both completion files exist in `tasks/done/`. The queue directory
references are stale and should be cleaned up. This is a file-management error,
not a product issue, but it will confuse any automated tooling that scans `tasks/queue/`
for open work. The PA should delete or archive the stale queue references.

---

## Summary Table

| Task | Complexity | Effort | Assigned To | Can Run Parallel With | Cannot Run Alongside | Concerns |
|------|-----------|--------|-------------|----------------------|----------------------|----------|
| BUG-022 | Simple | 2h ✓ | Developer | BUG-019, BUG-021, UX-001, WEB-001, FEAT-004 | — | Fix is well-specified; codebase confirms the gap |
| BUG-021 | Simple | 2-3h ✓ | Developer | BUG-019, BUG-022, UX-001, WEB-001, FEAT-004 | — | `extractVideoDuration` already exists; this may be a display-only bug |
| BUG-020 | Complex | 3-4h stated; ~5-6h realistic | Developer | BUG-019, BUG-021, BUG-022, UX-001, WEB-001 | — | Touches E2EE DEK handling on both Android and server; higher risk than estimate implies |
| BUG-019 | Simple | 30min ✓ | Developer | All other bug/UX/web tasks | — | Context-rich task; needs one check of server 409 body format |
| FEAT-004 | Simple | 2-3h ✓ | Developer | BUG-019, BUG-021, BUG-022, UX-001, WEB-001 | FEAT-003 (same screens) | Android `getInvite()` API already exists; straightforward |
| FEAT-003 | Complex | 3-5h stated; 1-2 days realistic | Developer | BUG-022, BUG-021, BUG-019, UX-001, WEB-001 | FEAT-004 (same screens) | Option A requires passphrase flow; Option B requires new endpoints; under-estimated |
| UX-002 | Moderate | 2-3h ✓ | Developer | BUG-019, BUG-021, BUG-022, UX-001, WEB-001, FEAT-004 | — | Dual-platform (Android + web); straightforward visuals, but four touch-points |
| UX-001 | Simple | 1-2h ✓ | Developer | All other tasks | — | Pure Compose styling; two files |
| WEB-001 | Simple | Half-day ✓ | Developer | BUG-019, BUG-021, BUG-022, UX-001, UX-002, FEAT-004 | — | API exists; pure UI; good first task for web agent |
| TST-004 | Moderate | 1-2 days ✓ | Developer | WEB-001, UX-001, UX-002, BUG-019, BUG-022 | BUG-020 (web crypto state), FEAT-003 (auth flows in flux) | Foundation for SEC-002; do BUG-022 first so tests don't immediately fail |
| SEC-011 | Moderate | Half-day stated; 1 day realistic | SecurityManager | TST-006, SEC-012, OPS-003 | ARCH-007 (schema touches) | Server endpoint and soft-retire already exist; session invalidation is harder than the task implies |
| SEC-012 | Simple | 2-3h ✓ | SecurityManager | TST-006, OPS-003, SEC-011 | — | Blocked on ARCH-007; docs-only once unblocked |
| ARCH-007 | Complex | 1 day design ✓ | TechnicalArchitect | TST-006, OPS-003 | SEC-012 (blocks it), SEC-011 (schema touch conflict) | Migration strategy for existing plaintext tags is the hard part |
| SEC-002 | Complex | 2-3 days ✓ | SecurityManager | TST-006, OPS-003 | TST-004 (depends on it) | Blocked on TST-004; JaCoCo gate is the right outcome |
| TST-008 | Moderate | Half-day ✓ | TestManager | TST-006, OPS-003 | — | BUG-022 and BUG-020 should land first to reduce known-failing assertions |
| TST-006 | Simple | Half-day ✓ | TestManager | All tasks | — | Pure research; no code changes; good background task |
| DOC-001 | Moderate | 1 day ✓ | Developer | TST-006, OPS-003, SEC-012 | TST-004 (depends on it), SEC-002 (depends on both) | Low priority; truly blocked on TST-004 completion |
| OPS-003 | Complex | 1-2 days ✓ | OpsManager | TST-006, SEC-012, DOC-001 | — | GCS bucket sharing must be fixed first; not urgent per task itself |

---

## Detailed Notes Per Task

---

### BUG-022 — Web detail view blank for shared plot items

**Complexity:** Simple  
**Effort:** 2h — accept  
**Context sufficient for cold pickup:** Yes  

I read `PhotoDetailPage.jsx` (lines 19–26 and the `loadContent` function starting at line 601). The `unwrapDek` function handles `ALG_P256_ECDH_HKDF_V1` (sharing key) and falls back to `unwrapDekWithMasterKey` for everything else. There is no branch for `ALG_PLOT_AES256GCM_V1`. Meanwhile `vaultCrypto.js` exports `unwrapDekWithPlotKey` (line 284) and `vaultSession.js` exports `getPlotKey(plotId)` (line 25). The fix is a third branch in `unwrapDek` that calls `getPlotKey(upload.plotId)` and then `unwrapDekWithPlotKey`. The `upload` object must carry a `plotId` field for this to work — the developer should verify that the API response includes it (check `UploadRepository`). The task file covers this correctly.

**Parallelism:** No conflicts. Can run alongside any other task in the queue.  
**Split recommendation:** None needed.  
**Priority:** High — this is a regression that breaks the shared-plot smoke test. Fix before automating TST-008 Steps 4-5.

---

### BUG-021 — Video detail view shows 0-second duration

**Complexity:** Simple  
**Effort:** 2-3h — accept, but may be shorter  
**Context sufficient for cold pickup:** Mostly yes, but the investigation section is worth following because the actual bug location may differ from the stated "likely cause"  

I read `ThumbnailGenerator.kt` and `UploadService.kt`. The `extractVideoDuration` function exists at line 113 of `ThumbnailGenerator.kt` and is called in `UploadService.confirmLegacyUpload` (line 271) and also plumbed through `confirmEncryptedUpload` as a parameter (line 177). However, in `confirmEncryptedUpload` the `durationSeconds` comes from the client, not from server-side extraction — the Android `Uploader.kt` extracts it locally at line 407. The schema column exists (V19 migration). The Android `HeirloomsApi.kt` sends it in the confirm body (line 762). This means the bug is likely on the client side, not in `ThumbnailGenerator`. If the imported video's format wasn't decoded by `MediaExtractor` correctly (line 904 of `Uploader.kt`), `durationUs` returns -1 and `durationSeconds` is null, resulting in 0 displayed. The developer should focus investigation here rather than on `ThumbnailGenerator`.

**Important finding for the agent:** The task description says "ThumbnailGenerator doesn't extract duration" — this is incorrect. Duration extraction exists. The actual gap is likely that the imported video file (from a third-party app) has a format or codec that `MediaExtractor.KEY_DURATION` doesn't return correctly on Fire OS. The fix may involve a fallback extractor or handling the null case more gracefully in the detail view. The server investigation steps in the task are misleading.

**Parallelism:** No conflicts.  
**Split recommendation:** None needed.  
**Priority:** Medium. No E2EE risk; straightforward display bug.

---

### BUG-020 — Shared plot auto-approve: client-side DEK re-wrap

**Complexity:** Complex  
**Effort:** 3-4h stated; I estimate 5-6h realistic  
**Context sufficient for cold pickup:** Yes, the task is well-written, but the agent will need to read additional files not listed under `touches:` (specifically the trellis routing code in `TrellisRepository.kt` and the plot_items insert path)  

This task requires coordinated changes across Android crypto code (DEK re-wrap in `GardenViewModel.kt`), the upload confirmation API request body (new optional fields), and server-side logic in `TrellisRepository.kt` to conditionally bypass staging. The E2EE surface makes this higher risk than a typical 3-4h task. The acceptance criteria are well-defined. The "fall back to staging if plot key not available" safety condition is important and must be tested explicitly.

**Codebase note:** This task modifies the trellis routing path that BUG-018 fixed. The agent should read the BUG-018 completion notes carefully before starting.

**Parallelism:** No inherent conflicts, but avoid running this alongside TST-004 since the trellis routing path will be in a changed state.  
**Split recommendation:** Consider splitting into (a) server endpoint changes only and (b) Android client DEK re-wrap + integration, if an agent run stalls.  
**Priority:** Medium. Important for UX but not a regression relative to v0.54 — staging still works.

---

### BUG-019 — Registration shows wrong error for duplicate device_id

**Complexity:** Simple  
**Effort:** 30min — accept  
**Context sufficient for cold pickup:** Yes  

The task specifies exactly where to look and what to distinguish. The agent needs to verify the exact 409 body returned by the server for duplicate `device_id` vs duplicate username. This is a one-line conditional in the error handler. Low risk.

**Parallelism:** No conflicts. Good task to assign while other agents are working on longer tasks.  
**Priority:** Low. UX improvement, not a blocker.

---

### FEAT-004 — Android invite a friend from Friends screen

**Complexity:** Simple  
**Effort:** 2-3h — accept  
**Context sufficient for cold pickup:** Yes  

I confirmed that `HeirloomsApi.kt` already has `getInvite()` at line 597 and that the server endpoint (`GET /api/auth/invites`) is implemented. The Android share sheet is standard Android API. The `FriendsScreen.kt` currently shows "No friends yet. Share an invite to get started." but has no button. This is straightforward UI wiring with no E2EE implications.

**Parallelism:** Cannot run alongside FEAT-003 (both touch `FriendsScreen.kt`/`FriendsViewModel.kt`). Fine alongside all other tasks.  
**Priority:** Medium. Addresses a real product gap (Android-only users blocked).

---

### FEAT-003 — Android account pairing/recovery

**Complexity:** Complex  
**Effort:** 3-5h stated; I estimate 1-2 days realistic  
**Context sufficient for cold pickup:** Partially — the task correctly frames the E2EE implications, but the "Option A vs Option B" choice is unresolved. An agent picking this up cold would need to decide which option to implement, which is an architectural decision that should be made by the PA or TechnicalArchitect first.  

**Concerns:**  
- The passphrase path (Option A) requires the Android client to implement Argon2id key derivation matching the web flow — this is non-trivial cryptographic plumbing.  
- The pairing code path (Option B) requires a new server endpoint and bidirectional key exchange protocol.  
- Neither option was scoped with a complete design; the task is more of a brief than a spec.  
- This task should not be assigned to a Developer until the PA or TechnicalArchitect specifies which option to implement and produces a design note. Consider raising a task for ARCH-008 (account recovery design) before this goes to a developer.  

**Parallelism:** Cannot run alongside FEAT-004 (shared screens).  
**Split recommendation:** Strong — this should be (a) architecture decision task and (b) implementation task.  
**Priority:** Medium product priority, but blocked on design.

---

### UX-002 — Closed plot visual indicator

**Complexity:** Moderate  
**Effort:** 2-3h — accept, possibly top of that range given dual-platform  
**Context sufficient for cold pickup:** Yes  

Four touch-points across Android and web. The task is well-specified — add a lock icon, mute the visual, disable approve/share buttons. No E2EE implications. The Android side requires checking `plotStatus == "closed"` in three composables; the web side requires the same in `SharedPlotsPage.jsx`. The Android staging approval screen banner is the least obvious requirement and the most useful (prevents the 403 confusion). Make sure the agent reads the existing plot status field in the API responses before starting.

**Parallelism:** No conflicts.  
**Priority:** Medium. Low risk, clearly defined, good UX improvement.

---

### UX-001 — Android shared plot button tap targets

**Complexity:** Simple  
**Effort:** 1-2h — accept  
**Context sufficient for cold pickup:** Yes  

Two files, pure Compose layout adjustment. The task gives three implementation options. `Modifier.minimumInteractiveComponentSize()` is the cleanest approach and requires the fewest lines of change. Recommend the agent use that approach.

**Parallelism:** No conflicts. Can run alongside anything.  
**Priority:** Low. Quality-of-life fix.

---

### WEB-001 — Web friends list page

**Complexity:** Simple  
**Effort:** Half-day — accept  
**Context sufficient for cold pickup:** Yes  

The API function `getFriends` already exists in `api.js` (confirmed). This is a pure UI task: new `FriendsPage.jsx` component, empty state, route wiring in `App.jsx`. No crypto or state management complexity. Good candidate for a first web task.

**Parallelism:** No conflicts.  
**Priority:** Medium. Needed for TST-008 Step 2 verification. Sequence: do this before automating TST-008.

---

### TST-004 — Playwright E2E suite

**Complexity:** Moderate  
**Effort:** 1-2 days — accept  
**Context sufficient for cold pickup:** Yes — good structure spec in the task  

The task is well-specified with actor model, directory structure, and journey coverage. The agent should check existing `HeirloomsWeb/package.json` for Playwright version first. 

**Important sequencing note:** BUG-022 should be resolved before TST-004 is run against staging, otherwise the shared plot journey (Journey 4) will have a known-failing assertion on full image decrypt. Either fix BUG-022 first, or have the agent write the assertion as `expect.soft` / mark it with a skip comment until BUG-022 lands.

TST-004 is a **prerequisite for SEC-002 and DOC-001**. It should be prioritised above those two tasks.

**Parallelism:** Can run alongside most tasks. Avoid running concurrently with BUG-020 (trellis routing in flux).  
**Split recommendation:** The 6 journeys could be split across two agent runs if time-boxing is needed: Journeys 1-3 in one run, Journeys 4-6 in a second run (after BUG-022 lands).  
**Priority:** High from an infrastructure perspective. Blocks SEC-002.

---

### SEC-011 — Device revocation

**Complexity:** Moderate  
**Effort:** Half-day stated; I estimate 1 day realistic  
**Context sufficient for cold pickup:** Partially — the task spec says "delete the `wrapped_keys` row" but the existing server implementation (`retireDeviceRoute` in `KeysRoutes.kt`) does a **soft-retire** (sets `retired_at`), not a hard delete. The task also says "invalidate all sessions for that device" but `user_sessions` has no `device_id` column (confirmed by reading V20 migration and `SessionAuthFilter.kt`). These are gaps the task file does not acknowledge.

**Codebase findings:**  
1. `DELETE /api/keys/devices/{deviceId}` already exists and soft-retires the device. The task is 60% done on the server side.
2. `SessionAuthFilter` does not check `retiredAt` — so a retired device with a live session token can still authenticate. This is the real gap.
3. Session invalidation for a specific device requires either: (a) a migration to add `device_id` to `user_sessions`, or (b) deleting all sessions for the user on device revocation (too aggressive), or (c) accepting that sessions expire naturally (acceptable given the threat model described in the task).
4. The Android and web UI work (adding a Remove button) is the cleanest and most immediately user-visible part.

**Recommendation:** The SecurityManager should clarify option (c) before the developer writes session invalidation code. If the threat model section of SEC-011 is accurate (retired device can't fetch wrapped keys, and sessions expire naturally), then option (c) is defensible — but the task currently says "invalidate all sessions" which implies option (a) or (b).

**Parallelism:** Avoid running alongside ARCH-007 (both touch server repository/schema area).  
**Priority:** Medium. The server infrastructure is largely in place; this is mostly UI work with one important design decision to resolve first.

---

### SEC-012 — Tag metadata leakage: document accepted risk

**Complexity:** Simple  
**Effort:** 2-3h — accept  
**Context sufficient for cold pickup:** Yes  
**Blocked on:** ARCH-007 (must confirm the HMAC token scheme design before the risk documentation is final)

Pure documentation task once ARCH-007 is approved. The task is well-scoped. No code changes.

**Parallelism:** Can run alongside anything except ARCH-007 itself (would be writing stale docs if ARCH-007 is still being revised).  
**Priority:** Low until ARCH-007 is done; then quick to resolve.

---

### ARCH-007 — E2EE tag scheme design

**Complexity:** Complex  
**Effort:** 1 day design — accept (design-only; implementation is a separate workstream)  
**Context sufficient for cold pickup:** Yes — the task is a thorough brief  

The proposed HMAC token scheme is well-reasoned. The hard design questions are: (1) migration strategy for existing plaintext tags (the task rightly flags this as open), (2) whether tag display names are stored per-tag or per (user, upload) tuple, and (3) the `member_tags` table design for per-member tagging of shared plot items.

**Concerns:**  
- The migration question has UX implications (users may see a "re-encrypting tags" spinner at next login). This should be explicitly designed, not left for the implementation agent.  
- ARCH-007 blocks SEC-012 and the TST-008 Step 6 automation. It does not block any other queue task directly.  
- Implementation of the scheme (DB migration, client changes across Android/Web/iOS) is a multi-day effort not captured in the current estimate. The current task is design-only, which is appropriate staging.

**Parallelism:** Avoid running alongside SEC-011 (both touch server schema area); otherwise no conflicts.  
**Priority:** High for the roadmap; not urgent for immediate delivery. Schedule after the bugs and UX tasks clear.

---

### SEC-002 — 100% auth/crypto coverage plan

**Complexity:** Complex  
**Effort:** 2-3 days — accept  
**Context sufficient for cold pickup:** Yes  
**Blocked on:** TST-004  

Well-scoped task. The phased approach (audit → unit tests → integration tests → JaCoCo gate) is correct. The existing 326 unit tests give a solid baseline.

**Concern:** This task is assigned to SecurityManager, who will be writing server-side Kotlin tests. Ensure the SecurityManager has Kotlin/JUnit test-writing capability (vs purely security analysis). If not, this should be split: SecurityManager does the coverage audit and identifies gaps; a Developer writes the test code.

**Parallelism:** Cannot start until TST-004 is done (depends_on is listed). DOC-001 also depends on this.  
**Priority:** High for security posture, but blocked.

---

### TST-008 — Shared plot E2E smoke test spec

**Complexity:** Moderate  
**Effort:** Half-day — accept  
**Context sufficient for cold pickup:** Yes — this is one of the most detailed task files in the queue  
**Assigned to:** TestManager  

The 6-step spec is essentially already written in the task file itself. The deliverable is formalising it as `docs/testing/smoke_test_shared_plot.md` and producing the automation assessment. Good candidate for an early TestManager run.

**Recommendation:** Schedule TST-008 after BUG-022 and WEB-001 land, so the formalised spec reflects the "fixed" state rather than documenting known gaps as expected failures. Step 6 (auto-tagging) should be marked as "future" explicitly in the spec.

**Parallelism:** Can run alongside most tasks; no code conflicts.  
**Priority:** Medium. Produces useful documentation; unblocks TST-008 automation planning.

---

### TST-006 — Android remote control investigation

**Complexity:** Simple  
**Effort:** Half-day — accept  
**Context sufficient for cold pickup:** Yes  
**Assigned to:** TestManager  

Pure research task; produces a recommendation doc. No code changes. Good background task that can run in parallel with almost anything else. The candidates table in the task (Maestro, Espresso/UI Automator, Appium, Firebase Test Lab) covers the right landscape.

**Parallelism:** No conflicts. Can run immediately, alongside anything.  
**Priority:** Medium. Needed before TST-008 can be fully automated.

---

### DOC-001 — UML sequence diagrams from test output

**Complexity:** Moderate  
**Effort:** 1 day — accept  
**Context sufficient for cold pickup:** Yes  
**Blocked on:** TST-004 (and SEC-001 which is done)  

The "living diagrams from test execution" approach is elegant but adds tooling complexity. The agent will need to instrument Playwright tests with structured log emission and write a post-processing script. This is more tool-building than documentation work.

**Concern:** If TST-004 specs change, the diagram generator will need maintenance. This is appropriate tech debt but the agent should document the dependency explicitly.

**Parallelism:** No conflicts once TST-004 is done.  
**Priority:** Low. Nice-to-have; schedule after TST-004 is stable.

---

### OPS-003 — Pre-production staging environment

**Complexity:** Complex  
**Effort:** 1-2 days — accept  
**Context sufficient for cold pickup:** Yes — the task self-describes as not urgent  
**Assigned to:** OpsManager  

The task correctly identifies the GCS bucket sharing problem as a prerequisite. The anonymisation approach is sound (vault content is already opaque; only user metadata needs scrambling).

**Concern:** This task requires GCP access and Cloud SQL permissions. Ensure the OpsManager has the necessary IAM roles before scheduling. The task also requires TST-008 automation to be mature before the environment makes full sense.

**Parallelism:** No code conflicts; pure infrastructure.  
**Priority:** Low. Defer until TST-008 automation is in progress.

---

## Developer-Assigned Tasks: Recommended Priority Order

The following tasks are assigned to Developer agents. My recommended order is based on (1) unblocking downstream work, (2) addressing active regressions first, (3) grouping by platform to minimise context-switching.

### Wave 1 — Immediate (can start now, no dependencies)

| Order | Task | Rationale |
|-------|------|-----------|
| 1 | **BUG-022** | Active regression; blocks TST-008 automation Steps 4-5; well-specified fix |
| 2 | **BUG-019** | 30-minute fix; clear spec; no risk; good quick win |
| 3 | **WEB-001** | Pure UI; no dependencies; unblocks TST-008 Step 2; good warm-up task for a web agent |
| 4 | **UX-001** | 1-2h; no dependencies; pure Compose; easy parallelism |

### Wave 2 — After Wave 1 (parallel-eligible)

| Order | Task | Rationale |
|-------|------|-----------|
| 5 | **BUG-021** | Server + display; agent should focus on Android client, not ThumbnailGenerator (see notes above) |
| 6 | **UX-002** | Dual-platform; straightforward; no E2EE |
| 7 | **FEAT-004** | Android invite flow; API exists; low risk |

### Wave 3 — After bugs and UX are clear

| Order | Task | Rationale |
|-------|------|-----------|
| 8 | **TST-004** | Foundation for SEC-002; do BUG-022 first so shared-plot journey doesn't fail immediately |
| 9 | **BUG-020** | Complex E2EE; higher risk; schedule as a dedicated agent run with time to test thoroughly |

### Wave 4 — Blocked or design-pending

| Order | Task | Rationale |
|-------|------|-----------|
| 10 | **DOC-001** | Blocked on TST-004 |
| — | **FEAT-003** | Hold until PA/Architect specifies Option A or B; then reassign |

---

## Cross-Cutting Concerns

**1. FEAT-003 needs an architecture decision before assignment.**  
The task is described as a feature investigation with two implementation options, neither of which is chosen. Assigning this to a Developer now risks wasted work. Recommend adding an ARCH task (ARCH-008) to resolve the design, then convert FEAT-003 into a proper implementation spec.

**2. SEC-011 session invalidation gap.**  
The existing `retireDevice` server implementation does a soft-retire but does not invalidate live sessions. The `user_sessions` table has no `device_id` column. The SecurityManager should decide whether to: (a) add a `device_id` column to `user_sessions` (new migration), (b) delete all user sessions on any device revocation (aggressive), or (c) rely on natural session expiry (acceptable per the stated threat model). This decision should be made before the developer writes the Android/web UI, as option (a) changes the server API contract.

**3. BUG-021 root cause is likely on Android, not the server.**  
The task description points at `ThumbnailGenerator.kt` but `extractVideoDuration` already exists there and is called from `confirmLegacyUpload`. For encrypted uploads, duration comes from the Android client (`Uploader.kt` line 407). The bug is likely that `MediaExtractor.KEY_DURATION` fails for the specific codec/container of the imported video on Fire OS. The developer should verify the server-stored `duration_seconds` value in the DB before spending time in `ThumbnailGenerator.kt`.

**4. TST-004 should land before BUG-020 changes the trellis routing path.**  
If BUG-020 modifies the trellis routing endpoint and TST-004 tests that path, running them concurrently on the same branch will produce confusing test failures. Recommend: TST-004 on its own branch first, then BUG-020 on a separate branch, then merge.

**5. ARCH-007 is a prerequisite for FEAT-003's passphrase recovery option.**  
If the tag scheme changes how the master key is used in derivations, FEAT-003 Option A (passphrase recovery) may need to be updated. Sequence ARCH-007 before FEAT-003 implementation.
