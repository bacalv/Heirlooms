# Test Manager Review — Queue Task Assessment
**Date:** 2026-05-16  
**Author:** TestManager  
**Scope:** All outstanding queue tasks (excluding BUG-017 and BUG-018, confirmed done)

---

## Summary Table

| Task | Test Layer Needed | Automation Impact | Testing Concern | Blocks TST-008? |
|------|------------------|-------------------|-----------------|-----------------|
| BUG-022 | Integration + E2E | TST-004 (journey 4) | Critical gap: `unwrapDek()` in PhotoDetailPage ignores `ALG_PLOT_AES256GCM_V1` — no web-layer unit test exists for this path | **YES — Step 4.6 and 5.6** |
| TST-008 | Manual (now) → E2E (later) | Direct: is the smoke test spec | AC is structural (6 steps) — individual sub-steps need pass/fail criteria per assertion | Self |
| ARCH-007 | Unit (crypto) + Integration | TST-004 (all journeys using tags) | No testable AC yet — design task; AC must be written before implementation tasks are created | YES — Step 6 |
| SEC-012 | Review / documentation | None direct | Doc-only; verify threat model claims are accurate against ARCH-007 scheme | No |
| BUG-021 | Unit (ThumbnailGeneratorTest) + Integration (UploadApiTest) | TST-004 (upload journey) | `extractVideoDuration()` exists but has zero test coverage — verified by inspection | YES — Step 4 quality |
| BUG-020 | Integration (SharedPlotApiTest) + E2E | TST-004 (shared-plot journey) | No existing test for client-supplied pre-wrapped DEK bypass path; staging-bypass new endpoint needs contract test | YES — Step 4.4 |
| FEAT-004 | E2E (manual/Playwright) | TST-008 Step 1 automation | Acceptance criteria are sufficient; automation unblocked on web side once feature ships | YES — Step 1 |
| SEC-011 | Unit + Integration | TST-004 (security journey, new) | KeysHandlerTest has DELETE device coverage, but SEC-011 adds session invalidation on revoke — that path is not tested | No |
| BUG-019 | Unit (AuthHandlerTest) | None direct | Server-side 409 body already distinguishes device vs username (test at line 887 verifies "device" in message); client-side error mapping needs Android unit test | YES — Step 1 re-run setup |
| FEAT-003 | Integration + E2E (manual initially) | TST-004 (new pairing journey) | Zero test coverage path for Android passphrase recovery; existing pairing tests cover web→Android direction only | No (but smoke test setup fragility) |
| TST-006 | Research / comparison | TST-008 full Android automation | Deliverable is a recommendation doc; no direct test automation output | YES — full Android steps |
| UX-002 | E2E (Playwright + manual Android) | TST-004 (shared-plot journey edge case) | AC is specific and testable: lock icon visible, actions disabled, 403 never silently reached | No |
| TST-004 | E2E (is the task) | Direct: produces the suite | Actor base class and infra done (TST-005); 6 journey specs not yet written | Indirect — powers automation |
| SEC-002 | Unit + Integration (coverage gate) | TST-004 (triggers gate) | Blocked until TST-004 is done; JaCoCo gate setup is straightforward once coverage baseline runs | No |
| UX-001 | Manual (physical device only) | None | AC requires physical device verification — not automatable via Playwright or emulator | No |
| WEB-001 | E2E (Playwright) | TST-008 Step 2, TST-004 (friends journey) | AC is clear; Playwright friend-list assertion is a natural TST-004 journey 3 hook | YES — Step 2 verification |
| DOC-001 | None (docs) | Depends on TST-004 | Blocked on TST-004; diagram accuracy depends on test fidelity | No |
| OPS-003 | Infrastructure | TST-008 (future stable env) | Not a testing blocker today; required before TST-008 automation runs reliably in CI | No (future) |

---

## Detailed Notes Per Task

---

### BUG-022 — Web detail view blank for shared plot items

**Priority assessment:** This is the single highest-priority fix from a testing standpoint. Steps 4.6 and 5.6 of TST-008 cannot pass while this bug exists — the smoke test is incomplete without it.

**Test layer:** Integration test in `vaultCrypto.js` (unit, browser context) verifying `unwrapDek()` handles `ALG_PLOT_AES256GCM_V1`; E2E assertion in TST-004 journey 4 (shared plot) that the full image loads.

**Codebase finding:** Confirmed by reading `PhotoDetailPage.jsx`. The `unwrapDek()` function at line 19–26 handles only two cases: `ALG_P256_ECDH_HKDF_V1` (sharing key) and a fallback to `unwrapDekWithMasterKey()`. There is no branch for `ALG_PLOT_AES256GCM_V1`. In contrast, `UploadThumb.jsx` line 76–78 correctly checks `thumbnailDekFormat === ALG_PLOT_AES256GCM_V1` and calls `unwrapDekWithPlotKey()`. The fix requires mirroring that pattern in `PhotoDetailPage.jsx` and calling `getPlotKey(plotId)` from `vaultSession.js` before the decrypt call.

**Acceptance criteria sufficiency:** Sufficient for implementation, but the AC should be extended to explicitly state that other DEK formats (`master-aes256gcm-v1`, sharing key) must still pass a regression check.

**AC extension recommended:**
- Add: "Regression: existing personal-vault items (master-key DEK) continue to decrypt correctly in the detail view."
- Add: "Regression: sharing-key items (`ALG_P256_ECDH_HKDF_V1`) continue to decrypt correctly."

**Test automation impact:** TST-004 journey 4 (shared-plot) must include a `page.waitFor` assertion on the full image `<img>` element being visible and non-blank after navigating to the detail page. This is automatable with Playwright.

---

### TST-008 — Shared plot E2E smoke test spec

**Priority assessment:** This is my primary deliverable. The task description is thorough on step content but the sub-step acceptance criteria are inconsistent in testability.

**AC sufficiency issues found:**

1. **Step 1.1 (app wipe):** "App returns to registration screen" — this is a device UI action. No verifiable assertion is possible via Playwright. For automation, this should be ADB-driven (`adb shell pm clear <package>`). The spec should note ADB command explicitly.

2. **Step 2.1 / 2.2 (friendship visible):** "Smoke B listed" — the criterion should specify the exact UI element to check. For web (User B), blocked until WEB-001 ships. The spec correctly flags WEB-001 as a gap.

3. **Step 4.4 (approve item in staging panel):** The actor performing the approval is listed as "User A (or plot owner)." This needs to be deterministic — it should always be the plot owner (User B in this scenario). Ambiguity will cause test flakiness.

4. **Step 4.5 ("thumbnail decrypts"):** A rendered thumbnail in the browser is an implicit assertion — the test should explicitly assert the `<img>` `src` attribute is not a data URL for a broken image and that the element is within the viewport. Playwright's `toBeVisible()` passes for `<img>` elements that are broken (zero dimension).

5. **Step 6 (auto-tagging):** Correctly flagged as future/blocked on ARCH-007. This step should be marked `status: NOT-RUNNABLE` with a blocker reference in the spec document.

**Automation strategy assessment:**
- Steps 1–2 are automatable today via API + Playwright for the web side. Android account setup requires ADB; BUG-019 fix is a prerequisite for reliable re-runs.
- Steps 3–5 are partially automatable now via Playwright for the web actor. The Android actor requires TST-006 resolution.
- Recommend authoring the spec first with deliberate placeholder assertions for Android steps, then layering in ADB/Maestro calls after TST-006.

**Test data strategy recommendation:** Dynamic per-run account creation is correct (as done in Actor.ts with timestamp suffix). The smoke test spec should adopt the same pattern: `smokeA-<timestamp>`, `smokeB-<timestamp>`. This eliminates the BUG-019 re-run friction for web. For Android, ADB-driven app wipe is still needed.

---

### ARCH-007 — E2EE tag scheme

**Priority assessment:** Design-only task; no implementation yet. Testing implications are significant and must be planned before the first implementation task is created.

**Test layer required (for future implementation):**
- **Unit:** HMAC token derivation correctness — known-answer tests with fixed master key + tag value inputs, verifying token is deterministic and format-correct.
- **Unit:** `auto-tag-token-v1` context produces a different token than `tag-token-v1` for the same tag value (loop prevention property).
- **Unit:** Tag display name round-trip: encrypt + decrypt returns original tag value.
- **Integration:** Trellis evaluation using HMAC token criteria — server matches items with the correct token and rejects items with a different token.
- **Integration:** Per-member tags are invisible across members (User B cannot see User A's `member_tags` for User B's item).
- **Migration:** Existing plaintext tags survive the migration step without data loss.

**AC sufficiency:** The current task has no testable acceptance criteria — it is architecture-only, which is appropriate. However, the implementation task that follows ARCH-007 **must** include explicit test requirements for each of the above properties before it can be considered complete. Flag this for the PA to enforce when creating follow-on tasks.

**TST-008 impact:** Step 6 (auto-tagging) is entirely blocked on ARCH-007 completing and an implementation task being created and done.

---

### SEC-012 — Tag metadata leakage accepted risk

**Priority assessment:** Documentation task; testing role is to verify the threat model claims are accurate.

**Test layer:** Review/verification only. Before the SecurityManager signs off, they should confirm:
1. The HMAC token scheme under ARCH-007 actually provides cross-user isolation (different tokens for same tag value across users — provable from the key derivation spec).
2. The claim "server cannot reconstruct semantic meaning" holds (it does, because the HMAC key is derived from the per-user master key which the server never sees).

**Testing concern:** No automated test is needed for a documentation task, but the threat model claims should be referenced in future SEC-002 unit tests (e.g., a test that two users with the same tag value produce different HMAC tokens using their respective master keys).

---

### BUG-021 — Video shows 0-second duration

**Priority assessment:** Medium. Blocks TST-008 Step 4 from being a clean pass (video upload sub-step), but does not prevent Steps 4.5/4.6 from being tested with images.

**Test layer:** Unit test in `ThumbnailGeneratorTest.kt` for `extractVideoDuration()`.

**Codebase finding (critical gap):** I confirmed that `extractVideoDuration()` exists in `ThumbnailGenerator.kt` (line 113) and is distinct from `generateThumbnail()`. However, the existing `ThumbnailGeneratorTest` has **zero test coverage for `extractVideoDuration()`**. The test for `valid MP4 produces non-null thumbnail` (line 137) only calls `generateThumbnail()` — it does not call `extractVideoDuration()` or assert on any duration value. This is a confirmed coverage gap.

**Tests needed (as part of BUG-021 fix):**
1. `extractVideoDuration returns correct duration for valid MP4` — create a 3-second test MP4 via ffmpeg, assert return value is 3 (or within ±1 for rounding).
2. `extractVideoDuration returns null for corrupt video` — assert graceful null return.
3. `extractVideoDuration returns null for non-video MIME type` — e.g., `image/jpeg` input.
4. Integration test in `UploadApiTest` or `UploadJourneyTest`: upload a valid MP4 and assert the returned upload record has `duration_seconds > 0`.

**AC sufficiency:** The task AC requires "correct duration in detail view" — this is verifiable but the AC should add "and `duration_seconds` field is correctly populated in the API response for a video upload." Without that, the developer might fix the display without fixing the data source.

---

### BUG-020 — Shared plot auto-approve (client-side DEK re-wrap)

**Priority assessment:** High from a testing standpoint. This is a new code path (bypass staging when client provides pre-wrapped DEK) that touches both server and Android client. The surface area is wide and the E2EE correctness property is security-critical.

**Test layer:** Integration test (HeirloomsTest `SharedPlotApiTest`) + E2E (TST-008 Step 4 without staging approval).

**Codebase finding:** `SharedPlotApiTest` covers the existing staging path extensively (lines 195–530) but has no test for the proposed new endpoint fields (`wrappedPlotItemDek`, `plotItemDekFormat`, `targetPlotId`). These are new server contract additions that need explicit contract tests before the developer ships.

**Tests needed:**
1. Server: POST tag-update with valid `wrappedPlotItemDek` + `plotItemDekFormat=plot-aes256gcm-v1` + `targetPlotId` → item appears directly in `plot_items`, not staging queue.
2. Server: POST tag-update without `wrappedPlotItemDek` → item goes to staging as before (regression).
3. Server: POST with `targetPlotId` for a plot the user is NOT a member of → returns 403 or goes to staging (security boundary).
4. Server: POST with malformed `wrappedPlotItemDek` envelope → returns 400 (envelope validation).
5. E2E: TST-008 Step 4.4 — after the fix, the approval step should be removed from the happy path and replaced with an assertion that the item appears in the shared plot immediately after tagging.

**AC sufficiency:** The task AC for the "not a member" case is implicitly covered ("User who is NOT a member: item goes to staging as before") but the test must explicitly verify this as a security boundary, not just as a graceful fallback.

---

### FEAT-004 — Android invite friend button

**Priority assessment:** Medium. Directly enables TST-008 Step 1 automation (the Android actor can generate its own invite token without API workaround).

**Test layer:** Manual on device (Android UI) + API verification (invite token is valid and redeemable). Playwright cannot exercise Android UI.

**AC sufficiency:** Sufficient. The four acceptance criteria are discrete and testable:
1. Button visible — manual visual check.
2. Tapping generates token + opens share sheet — manual interaction check.
3. Recipient registration creates friendship — API-level verification possible (call `GET /api/auth/friends` after recipient registers).
4. Expiry displayed — manual visual check.

**TST-008 automation note:** Even after FEAT-004 ships, the share sheet step requires a human (or TST-006 automation) to intercept the share intent. For CI automation, the API-level invite generation (as done in `Actor.ts::generateInviteToken()`) remains the preferred path. FEAT-004 unblocks the manual run of TST-008 Step 1 without an API workaround, which is valuable.

---

### SEC-011 — Device revocation

**Priority assessment:** Medium. Security feature with significant test coverage implications.

**Test layer:** Unit (AuthHandlerTest) + Integration (HeirloomsTest) + Manual (both clients).

**Codebase finding:** `KeysHandlerTest.kt` line 177 and `IsolationTest.kt` line 458 already test `DELETE /api/keys/devices/{deviceId}` for ownership scoping (Bob cannot delete Alice's device). However, SEC-011 introduces a new endpoint: `DELETE /api/auth/devices/{deviceId}`. The existing tests cover the key-deletion path but **not the session invalidation behavior** specified in SEC-011 (all sessions for the revoked device_id become invalid immediately). This is a new behavior that needs dedicated tests.

**Tests needed:**
1. `DELETE /api/auth/devices/{deviceId}` returns 204 for a valid device owned by calling user.
2. After deletion, a session token associated with the revoked device_id returns 401 on subsequent requests.
3. `DELETE /api/auth/devices/{currentDeviceId}` returns 403 (cannot revoke self).
4. `DELETE /api/auth/devices/{otherUsersDeviceId}` returns 403 (isolation).
5. After deletion, `GET /api/keys/devices` no longer lists the revoked device.
6. `wrapped_keys` row for the device is confirmed deleted (can verify via direct DB assertion in integration test).

**AC sufficiency:** The task AC is comprehensive and testable. All five criteria map 1:1 to the tests listed above.

---

### BUG-019 — Registration shows wrong error for duplicate device_id

**Priority assessment:** Low for the fix itself, but Medium for TST-008 Step 1 reliability. When re-running the smoke test on the same Fire OS device, this error is the first thing testers hit.

**Test layer:** Server-side unit test already exists and is adequate. Android client-side unit test needed.

**Codebase finding:** `AuthHandlerTest.kt` line 858–887 already tests the server 409 response and asserts the error message contains "device" (ignoring case). The server-side behavior is correctly tested. The gap is exclusively on the Android client: `HeirloomsApi.kt` and `RegisterScreen.kt` need to parse the 409 body and show the correct message. There are no Android unit tests for this error mapping — and Android unit tests for network error handling are typically thin in this codebase.

**Tests needed:**
- Android: mock an HTTP 409 response with `{"error": "device_id already registered"}` body and assert the UI shows a device-specific error string (not "Username already exists").
- Android: mock a 409 with `{"error": "username already taken"}` body and assert the UI shows "Username already exists".

**AC sufficiency:** Sufficient for the fix. The two AC bullets map directly to the two test cases above.

---

### FEAT-003 — Android account pairing/recovery

**Priority assessment:** Medium for the feature, but this is a significant E2EE operation with no current test coverage path.

**Test layer:** Integration (server pairing endpoints) + Manual E2E (Android physical device required for Option A — passphrase recovery, or Option B — pairing code).

**Codebase finding:** `AuthHandlerTest.kt` lines 442–490 cover the existing web→Android pairing flow (initiate → QR → complete → status complete). These tests cover the current server pairing endpoints. However, FEAT-003 proposes either a new "recover with passphrase" Android flow or an Android-initiated pairing code — both of which would require new server endpoints or extension of existing ones. Neither direction has any test coverage yet.

**Tests needed (when implementation direction is chosen):**
- If Option A (passphrase recovery): integration test that `POST /api/auth/passphrase-recover` (or equivalent) with correct email + derived key returns a valid session and wrapped master key; test that wrong passphrase returns 401.
- If Option B (Android-initiated code): mirror of existing pairing tests but with Android as the initiator.
- E2E: manual walkthrough on a wiped Android device — this cannot be automated until TST-006 is resolved.

**AC sufficiency:** The current AC is directional ("can recover without registering a new one") but deliberately deferred until implementation direction is chosen. When a specific option is selected, the AC must be sharpened to specify the recovery mechanism and error cases before work starts.

---

### TST-006 — Android remote control investigation

**Priority assessment:** High from a testing roadmap perspective. This is a research task, but its output determines whether TST-008 can ever be fully automated.

**Key questions that determine testing architecture:**
1. **Biometric/vault unlock:** The Heirlooms Android app uses Keystore for master key unwrapping. Biometric prompts cannot be automated on a real device without system-level access; emulators do not have Keystore hardware. This is the single biggest blocker for Android E2E automation.
2. **Emulator vs real device:** E2EE key operations may behave differently on emulators. The investigation must explicitly test whether vault operations (DEK decryption, plot key unwrap) work correctly on an emulator before recommending it for CI.
3. **Maestro is the leading candidate:** Based on the candidates listed, Maestro is the best fit for black-box E2E without instrumentation. It runs over ADB, has a clean YAML syntax, and supports network request interception for stubbing. However, the biometric blocker applies equally to Maestro.

**Recommended deliverable additions:**
- Add to the investigation: "Test whether `adb shell settings put secure mock_biometric 1` (or equivalent) bypasses the biometric prompt in the staging flavor."
- Add: "Determine if the staging Android flavor can be built with `BiometricPrompt` disabled (e.g., via a debug flag) to allow CI automation."

**TST-008 dependency:** Until TST-006 is resolved, TST-008 Android steps (1.1 app wipe, 1.3 register User A, 3.3 accept plot invite, 4.2–4.3 upload and tag, 5.5–5.6 view shared item) remain manual. The web half of TST-008 can be automated independently using Playwright.

---

### UX-002 — Closed plot visual indicator

**Priority assessment:** Medium. Has clear, testable AC.

**Test layer:** Playwright E2E (web) for the lock icon and disabled actions; manual on Android device for the lock icon and staging approval banner.

**Tests needed:**
- Playwright: create a shared plot, close it via API (or UI toggle), navigate to the plot — assert lock icon is visible, approve button is disabled or absent.
- Playwright: attempt to call the approve staging endpoint for a closed plot via UI — assert the 403 is handled gracefully (no silent error, visible feedback).
- Android: manual — verify the Shared screen shows a "Closed" badge and the Review button is greyed out.

**AC sufficiency:** Sufficient and specific. All four criteria are testable. The AC correctly calls out that reopening the plot must restore the active appearance — this makes it a good regression check too.

---

### TST-004 — Playwright E2E suite

**Priority assessment:** The highest-leverage testing task in the queue. The Playwright infrastructure (TST-005) is done; the actor base class (`Actor.ts`) is implemented and correct; the smoke spec (`api-health.spec.ts`) passes. The six journey specs are the outstanding work.

**Current state confirmed by codebase inspection:**
- `HeirloomsWeb/e2e/actors/Actor.ts` — complete: register, login, logout, gardenLoaded, justArrivedCount, generateInviteToken, inviteFriend.
- `HeirloomsWeb/e2e/support/api.ts` — complete: generateInviteToken, healthCheck.
- `HeirloomsWeb/e2e/support/config.ts` — complete: staging URLs, API key from env.
- `HeirloomsWeb/e2e/smoke/api-health.spec.ts` — complete: 5 smoke checks passing.
- `HeirloomsWeb/e2e/journeys/` — **empty directory.** No journey specs written.

**Journey spec priorities (in order):**
1. `activation.spec.ts` — register + login. Unblocked today.
2. `upload.spec.ts` — upload + garden view. Unblocked today.
3. `friends.spec.ts` — friend connection. Unblocked on web; requires WEB-001 for friends list assertion.
4. `sharing.spec.ts` — shared plot create + invite + join (web-only actor). Unblocked today on web.
5. `staging.spec.ts` — staging approval flow. Unblocked today.
6. `flows.spec.ts` — trellis routing. Unblocked today; BUG-020 fix changes the expected behavior for Step 4.

**Actor gaps:** The current `Actor.ts` has no methods for upload, plot creation, trellis creation, or staging approval. These journey-specific methods must be added as part of TST-004. The task description notes this correctly.

**AC sufficiency:** Sufficient — `npm run e2e` passing all 6 journeys is a binary acceptance gate.

---

### SEC-002 — 100% auth/crypto coverage with JaCoCo gate

**Priority assessment:** High security value, but correctly blocked on TST-004. Cannot run a meaningful JaCoCo gate until the E2E suite exercises the real deployed app and the integration tests are complete.

**Current coverage baseline (estimated from test inspection):**
- `AuthHandlerTest.kt` is extensive (930+ lines) and covers the majority of `AuthService` paths.
- `EnvelopeFormatTest.kt` covers symmetric, asymmetric, and multiple error branches.
- `SessionAuthFilter` appears to have coverage via `IsolationTest.kt` and `AuthHandlerTest.kt`.
- Uncovered areas likely include: FEAT-003 recovery flows (not yet implemented), SEC-011 session-invalidation-on-revoke, and some edge cases in `CriteriaEvaluator`.

**Recommendation:** Run `./gradlew coverageTest` first to establish a baseline before writing new tests — this prevents wasted effort on already-covered paths. The JaCoCo gate should be set at the current baseline plus 5% to enforce forward progress without blocking on legacy gaps.

---

### UX-001 — Android tap targets

**Priority assessment:** Low. Testing is manual only.

**Test layer:** Manual physical device only. The AC requires verification on a physical device, not an emulator. Not automatable.

**Testing concern:** The only verification path is a human tapping the buttons. There is no programmatic assertion for 48dp tap targets. As a lightweight alternative, an Espresso/UI Automator test could assert `clickable` state on the view and measure the view's touch delegate bounds — but this is disproportionate effort for a Low-priority UX polish task. Manual verification on a Fire OS device before marking done is sufficient.

---

### WEB-001 — Web friends list page

**Priority assessment:** Medium for the feature, High for TST-008 Step 2.

**Test layer:** Playwright E2E in TST-004 journey 3 (friends).

**Tests needed:**
- Navigate to friends page after User A and User B have connected → assert both friends are listed with display name and username.
- Empty state: navigate to friends page with no friends → assert "No friends yet" message.
- Playwright assertion: `page.getByText('Smoke B')` is visible after friendship is established.

**AC sufficiency:** Sufficient. Clear visual and empty-state criteria.

**TST-008 integration:** Step 2.2 (User B sees Smoke A in web friends list) can only be automated after WEB-001 ships. The Step 2 sub-step should reference WEB-001 as a dependency in the smoke test spec.

---

### DOC-001 — UML sequence diagrams

**Priority assessment:** Low. Correctly blocked on TST-004.

**Testing concern:** The accuracy of generated diagrams depends entirely on test fidelity. If TST-004 journey specs have gaps (e.g., do not exercise the full staging approval flow), the generated diagrams will be incomplete. The dependency order must be enforced: TST-004 fully passes before DOC-001 starts.

**No direct testing requirement** — this task generates documentation from test output rather than adding test coverage.

---

### OPS-003 — Pre-production staging environment

**Priority assessment:** Low for now. Not a testing blocker in the current phase.

**Testing relevance:** The primary testing value of OPS-003 is providing a stable environment for TST-008 automation to run against prod-shaped data. Until TST-008 automation is mature (after TST-006 is resolved and Android steps are automated), this is premature.

**Prerequisite concern:** OPS-003 notes that the current test environment shares the `heirlooms-uploads` GCS bucket with production. This is a test isolation problem that affects current smoke tests: a test that uploads content to the test env is writing to the production bucket. This should be flagged as a Medium blocker to be resolved as a prerequisite to OPS-003, not as part of it.

---

## Confirmed Testing Gaps Found by Codebase Inspection

These gaps were identified by reading source files — they are not covered in any existing task:

1. **`extractVideoDuration()` has zero test coverage** (`ThumbnailGeneratorTest.kt` only calls `generateThumbnail()` for MP4 — never `extractVideoDuration()`). This function exists at `ThumbnailGenerator.kt:113` and is the root cause of BUG-021. A fix to BUG-021 must include tests for this function.

2. **`PhotoDetailPage.jsx` `unwrapDek()` does not handle `ALG_PLOT_AES256GCM_V1`** (confirmed lines 19–26). This is the exact root cause of BUG-022. The function falls through to `unwrapDekWithMasterKey()` for any unknown format, silently producing wrong output rather than throwing. A fix should add an explicit `throw new Error()` for truly unknown formats after adding the `ALG_PLOT_AES256GCM_V1` branch, to prevent silent decrypt failures in future.

3. **SEC-011 session invalidation on device revoke is not tested.** The existing `KeysHandlerTest.kt` and `IsolationTest.kt` only test the key deletion isolation, not the session invalidation behavior that SEC-011 requires. The session invalidation test must be written as part of SEC-011.

4. **BUG-019 Android client-side 409 body parsing is not tested.** The server correctly returns a distinguishable error message (verified by `AuthHandlerTest.kt:887`). The Android client error mapping exists but has no test coverage.

5. **`Actor.ts` `justArrivedCount()` uses a fragile CSS class selector** (`[class*="w-40"][class*="h-40"]`). If Tailwind class names change or the layout is modified, this selector will silently return 0. This should be replaced with an `aria-label` or `data-testid` attribute on thumbnail cards before TST-004 journey specs are written.

---

## Recommended Sequencing: TST-006, TST-008, TST-004

### Phase 1 — Fix the smoke test blockers (parallel, 1–2 days)

Run these in parallel before any automation work:
- **BUG-022** (2h) — unblocks TST-008 Steps 4.6 and 5.6.
- **WEB-001** (half day) — unblocks TST-008 Step 2.2.
- **BUG-019** (30min) — unblocks reliable TST-008 Step 1 re-runs.

After Phase 1: re-run the TST-008 manual smoke test. Steps 1–5 should pass end-to-end for the first time (excluding video, pending BUG-021).

### Phase 2 — TST-008 spec formalisation (half day, this task)

Write `docs/testing/smoke_test_shared_plot.md` as the formal deliverable of TST-008. This can be authored now using the existing task description as the foundation. Include:
- Deliberate `[BLOCKED: WEB-001]` and `[BLOCKED: FEAT-004]` annotations for steps that need those features.
- Explicit pass/fail assertions for each sub-step (not just "expected" column).
- Test data strategy: dynamic usernames, ADB commands for Android steps.

### Phase 3 — TST-004 journey specs (1–2 days, agent task)

With the smoke test spec written and Playwright infra already in place, dispatch a Developer agent to write the six journey specs. The Actor base class is complete. Priority order:
1. `activation.spec.ts` and `upload.spec.ts` — no blockers, high value.
2. `friends.spec.ts` — blocked on WEB-001 (Phase 1 prerequisite).
3. `sharing.spec.ts` and `staging.spec.ts` — web actor only; Android steps are manual.
4. `flows.spec.ts` — authoring can start now; assertions may need updating after BUG-020 ships.

### Phase 4 — TST-006 investigation (half day, parallel with TST-004)

Run TST-006 in parallel with TST-004. The investigation output determines:
- Whether Android steps in TST-008 can be automated.
- Whether an emulator is viable for CI (biometric/Keystore question is the critical unknown).
- The follow-up implementation task scope and effort.

TST-006 does not block TST-004 — they can run concurrently.

### Phase 5 — Android automation (dependent on TST-006)

Only after TST-006 delivers a recommendation and the PA approves the follow-on implementation task. Do not start Android automation tooling until the tool choice is confirmed.

### Phase 6 — SEC-002 JaCoCo gate (after TST-004 complete)

SEC-002 depends on TST-004. Once the journey specs are written and passing, run the coverage report and set the gate. SEC-002 can be executed concurrently with Phase 5.

### Interrelation diagram (text form)

```
BUG-022 ──┐
WEB-001 ──┤──► TST-008 spec (formal) ──► TST-008 Step 1–5 manual run passes
BUG-019 ──┘

TST-004 (journey specs) ──► SEC-002 (JaCoCo gate) ──► DOC-001

TST-006 (investigation) ──► Android automation follow-on ──► TST-008 full automation

ARCH-007 ──► SEC-012 (doc)
ARCH-007 ──► [implementation task TBD] ──► TST-008 Step 6

BUG-020 ──► TST-008 Step 4 (auto-approve path, no staging approval needed)
BUG-021 ──► TST-008 Step 4 (video sub-step passes)
FEAT-004 ──► TST-008 Step 1 (Android actor generates invite without API workaround)
```

---

## Flags for PA Attention

1. **GCS bucket sharing (production/test):** OPS-003 notes the test environment currently shares `heirlooms-uploads` with production. This means every smoke test upload goes to the production bucket. This is a Medium-severity test isolation risk that should be resolved as a standalone task before OPS-003 (not as part of it).

2. **`Actor.ts` fragile CSS selector in `justArrivedCount()`:** The `[class*="w-40"][class*="h-40"]` selector will break if Tailwind classes change. Recommend adding `data-testid="thumbnail-card"` to the thumbnail component before TST-004 journey specs are written, to make the selector robust.

3. **BUG-020 security boundary test:** The new bypass-staging endpoint path must have an explicit integration test verifying that a non-member cannot submit pre-wrapped DEKs to inject items into a shared plot they do not belong to. This security boundary test should be a named acceptance criterion in BUG-020 before it is assigned.

4. **FEAT-003 direction decision:** This task cannot be tested or estimated reliably until the PA chooses Option A (passphrase recovery) or Option B (Android-initiated pairing code). The testing implications differ significantly — Option A is testable with a server integration test; Option B requires a two-device test harness. Request a direction decision before dispatching this task.

5. **ARCH-007 follow-on task must include test requirements:** When the PA creates the implementation task following ARCH-007 approval, the test requirements (HMAC known-answer tests, cross-user isolation, loop prevention property) listed in the ARCH-007 section above should be included in the task AC verbatim.
