# Shared Plot E2E Smoke Test — Formal Spec

**Task origin:** TST-008  
**First run:** 2026-05-15 (manual, CTO)  
**Environment:** Test — `test.heirlooms.digital` / `test.api.heirlooms.digital`  
**Version tested manually:** v0.54 (commit 94bc7df)  
**Status:** Spec formalised 2026-05-16. Steps 1–4 partially run; Steps 5–6 not yet run.

---

## Purpose

This smoke test validates the full end-to-end shared plot flow: account creation,
friendship, shared plot membership, cross-platform media upload, trellis routing,
staging approval, and decryption on both Android and web. It is the primary gating
check before any production release involving shared plot or trellis functionality.

The test is designed to be repeatable across releases with fresh accounts each run.
Once the automation prerequisites (TST-006, fixed invite flow on Android) are met, most
steps can be automated using Playwright (web side) and ADB (Android side).

---

## Known gaps — current status

| Gap ID | Description | Status as of 2026-05-16 |
|--------|-------------|------------------------|
| BUG-019 | Registration shows "Username already exists" for duplicate device_id | Open — queue |
| BUG-020 | Shared-plot trellis forced to staging (DEK re-wrap missing) | **Fixed** — merged 2026-05-16 |
| BUG-021 | Video uploads show 0-second duration on detail view | Open — queue |
| BUG-022 | Web detail view blank for shared plot images (plot key not used) | **Fixed** — merged 2026-05-16 |
| FEAT-004 | Android has no "Invite a friend" UI button | Open — queue (v0.55 iteration) |
| WEB-001 | Web friends list page not built | **Fixed/shipped** — merged 2026-05-16 |

---

## Pre-flight

Before starting, confirm the test environment is healthy:

```bash
curl -s https://test.api.heirlooms.digital/health
# Expected: {"status":"ok"}
```

If the health check fails, stop and alert OpsManager. Do not proceed.

Also confirm the test API key is available:

```bash
gcloud secrets versions access latest \
  --secret=heirlooms-test-api-key \
  --project=heirlooms-495416
```

---

## Test accounts

Each run uses freshly created accounts. Recommended usernames:

| Actor | Username | Display name | Platform |
|-------|----------|--------------|----------|
| User A | `smokeA` | `Smoke A` | Android (Fire OS, staging flavor) |
| User B | `smokeB` | `Smoke B` | Web |

> **Note:** BUG-019 (duplicate device_id) means clearing the Android staging app's
> storage between runs may be insufficient if the device_id is also shared. Workaround:
> wipe Android staging app fully (Clear Storage in Android settings), or use a fresh DB
> account prefix (smokeA-NNN) each run. The DB cleanup required to re-use identical
> usernames across runs is a known gap.

---

## Step 1 — Account Setup

**Intent:** Create two fresh test accounts with known credentials and establish a
friendship between them.

| Sub-step | Action | Expected result | Platform |
|----------|--------|-----------------|----------|
| 1.1 | Clear Storage on Android staging app | App returns to registration screen; device_id is reset | Android |
| 1.2 | Generate invite token for User A via API | Token returned; valid 48 h | API |
| 1.3 | Register User A on Android using token from 1.2 | Username `smokeA`, display `Smoke A`; vault created; garden appears | Android |
| 1.4 | Generate invite token for User B via API key OR User A via web (workaround for FEAT-004) | Token returned | API / Web |
| 1.5 | Register User B on web at `/join?token=...` | Username `smokeB`, display `Smoke B`; friendship A↔B auto-created | Web |

**Pass criteria:**
- Both accounts exist and can log in independently.
- `GET /api/friends` for User A returns User B (and vice versa).

**Known gaps:**
- **BUG-019 (Open):** If the Android device_id collides with an existing registration
  (e.g. from a previous run), the API returns 409 but the app shows "Username already
  exists". Workaround: ensure Clear Storage in step 1.1 resets the device_id. If the
  error still appears, flush the `devices` table row manually or generate a new
  device_id via app reinstall.
- **FEAT-004 (Open):** Android has no in-app "Invite a friend" button. To generate the
  invite token for step 1.4 during manual runs, use the API key directly (curl). During
  automation, use `ApiHelper.generateInviteToken()` from the Playwright support library.

---

## Step 2 — Friendship Verification

**Intent:** Confirm the A↔B friendship is visible in both front-ends.

| Sub-step | Action | Expected result | Platform |
|----------|--------|-----------------|----------|
| 2.1 | User A: burger menu → Friends | `Smoke B` listed with display name | Android |
| 2.2 | User B: Nav → Friends | `Smoke A` listed with display name | Web |
| 2.3 | (Optional) API verification | `GET /api/friends` for both users returns each other | API |

**Pass criteria:**
- Friendship appears in both the Android Friends screen and the web Friends page.

**Known gaps:**
- **WEB-001 (Fixed 2026-05-16):** Web friends list page shipped. Sub-step 2.2 is now
  testable on web at `/friends`.

---

## Step 3 — Shared Plot Creation and Member Invite

**Intent:** User B creates a shared plot and invites User A to join it.

| Sub-step | Action | Expected result | Platform |
|----------|--------|-----------------|----------|
| 3.1 | User B: Garden → `+` → Shared plot → name `smoke-test-plot` | Plot created; appears in User B's garden | Web |
| 3.2 | User B: open plot → Members → Invite → select `Smoke A` | Invite sent to User A | Web |
| 3.3 | User A: Shared → pending invite banner → Accept | `smoke-test-plot` appears in User A's Shared screen | Android |

**Pass criteria:**
- `smoke-test-plot` visible in User A's Shared screen (Android) and User B's garden (web).
- `GET /api/plots` for User A includes the shared plot.

---

## Step 4 — One-Way Automatic Publish (User A → shared plot → User B)

**Intent:** User A uploads media, tags it with a trellis tag, and the item routes to
the shared plot. User B can view and decrypt it on the web.

| Sub-step | Action | Expected result | Platform |
|----------|--------|-----------------|----------|
| 4.1 | User A: Trellises → New trellis → name `smoke-test-trellis-a`, criteria: tag=`smoke-test-tag`, target: `smoke-test-plot` | Trellis saved | Android |
| 4.2 | User A: take or import a fresh photo | Item appears in Just Arrived | Android |
| 4.3 | User A: open item → tag with `smoke-test-tag` → Save | Item leaves Just Arrived; routes to `smoke-test-plot` (auto-publish now that BUG-020 is fixed) | Android |
| 4.4 | *If requires_staging: true* — User A or plot owner: Shared → `smoke-test-plot` → staging panel → Approve | Item moves from staging queue to plot | Android / Web |
| 4.5 | User B: refresh garden → open `smoke-test-plot` | Item visible; thumbnail decrypts | Web |
| 4.6 | User B: click item for detail view | Full image decrypts and displays (now that BUG-022 is fixed) | Web |

**Pass criteria:**
- Item visible and fully decryptable in `smoke-test-plot` for User B on web.
- Thumbnail and full image both render without blank screen.

**Known gaps:**
- **BUG-020 (Fixed 2026-05-16):** Auto-publish now works when User A holds the plot
  key. The Android client re-wraps the DEK before sending. Sub-step 4.4 should no
  longer be required when User A is a member with the plot key loaded. If staging is
  still triggered (e.g. plot key not yet loaded), the fallback staging approval path
  remains correct.
- **BUG-022 (Fixed 2026-05-16):** Web detail view now decrypts full images for shared
  plot items using `ALG_PLOT_AES256GCM_V1`. Sub-step 4.6 should pass.
- **BUG-021 (Open):** If the item in step 4.2 is a video, the detail view will show
  0-second duration. Use a photo (not video) for this sub-step until BUG-021 is resolved.

---

## Step 5 — Bidirectional Publish (User B → shared plot → User A)

**Intent:** User B contributes to the same shared plot; User A can decrypt it on Android.

| Sub-step | Action | Expected result | Platform |
|----------|--------|-----------------|----------|
| 5.1 | User B: Trellises → New trellis → name `smoke-test-trellis-b`, criteria: tag=`smoke-test-tag`, target: `smoke-test-plot` | Trellis saved | Web |
| 5.2 | User B: upload a fresh image (drag-drop or paste) | Item appears in User B's Just Arrived | Web |
| 5.3 | User B: open item → tag with `smoke-test-tag` → Save | Item routes to `smoke-test-plot` | Web |
| 5.4 | *If requires_staging: true* — User B: staging panel → Approve | Item moves to plot | Web |
| 5.5 | User A: Android → refresh garden → open `smoke-test-plot` | User B's item visible; thumbnail decrypts | Android |
| 5.6 | User A: open item detail | Full image decrypts and displays | Android |

**Pass criteria:**
- User B's item visible and fully decryptable in `smoke-test-plot` for User A on Android.
- Step 5 is the bidirectional mirror of Step 4.

**Status:** Defined; not yet run. Expected to pass based on BUG-020 and BUG-022 fixes,
but must be confirmed in the next manual smoke test cycle (TST-010).

---

## Step 6 — Automatic Shared Plot Tagging (future feature)

**Intent:** When a new item appears in a member's shared plot, the system automatically
applies one or more private tags. These tags are:
- Private to the configuring member (invisible to other members and to Heirlooms staff)
- E2EE — stored as HMAC tokens (see ARCH-007)
- Loop-safe — auto-applied tags cannot re-trigger trellises

**Status:** Feature not designed or implemented. Blocked on ARCH-007 approval.
ARCH-007 is now complete (2026-05-16) — design may proceed. Do not automate or
manually test Step 6 until an implementation task is created and shipped.

---

## Automation Assessment

### Which sub-steps can be automated now (with existing Playwright infrastructure)?

The Playwright infrastructure (TST-005) provides:
- `Actor` class: `register()`, `login()`, `logout()`, `gardenLoaded()`, `justArrivedCount()`, `generateInviteToken()`, `inviteFriend()`
- `ApiHelper`: `generateInviteToken()`, `healthCheck()`
- Two Playwright projects: `staging` and `local`

| Sub-step | Automatable now? | Tool | Blocker if not |
|----------|-----------------|------|----------------|
| 1.2 — Generate invite token | Yes | `ApiHelper.generateInviteToken()` | — |
| 1.5 — Register User B on web | Yes | `Actor.register()` | — |
| 2.2 — Web friends list | Yes | Playwright navigate to `/friends`, assert display name | — |
| 2.3 — API friendship verification | Yes | `ApiHelper` (extend with `getFriends()`) | Minor extension needed |
| 3.1 — Create shared plot on web | Yes | Playwright — click `+`, fill form | Journey-specific Actor method needed (TST-004) |
| 3.2 — Invite member on web | Yes | Playwright — plot Members panel | Journey-specific Actor method needed (TST-004) |
| 4.1 — Create trellis on web | Yes | Playwright — Trellises page | Journey-specific Actor method needed (TST-004) |
| 4.2 — Upload on web | Yes | Playwright — file input or drag-drop | Journey-specific Actor method needed (TST-004) |
| 4.3 — Tag item on web | Yes | Playwright — item detail, tag field | Journey-specific Actor method needed (TST-004) |
| 4.5 — View plot on web (User B) | Yes | Playwright — navigate to plot, assert thumbnail | Journey-specific Actor method needed (TST-004) |
| 4.6 — Web detail view decrypt | Yes | Playwright — assert image `src` not blank | Journey-specific Actor method needed (TST-004) |
| 5.1–5.4 — User B web trellis + upload + tag | Yes | Playwright | Journey-specific Actor method needed (TST-004) |
| **Android steps (1.1, 1.3, 3.3, 4.2–4.4, 5.5–5.6)** | **No — blocked** | ADB / UI Automator | **TST-006 required** |

**Summary:** All web-side sub-steps (User B's journey) can be automated with existing
Playwright infrastructure plus a set of new journey-specific `Actor` methods. All
Android-side sub-steps (User A's journey) require TST-006 (Android remote control
investigation) before they can be automated.

---

## Test Data Strategy

### Recommendation: dynamic account creation per run (CI-safe)

Do not use fixed usernames or hard-coded invite tokens in CI. The reasons:

1. **Account collision:** Fixed usernames like `smokeA`/`smokeB` will collide across
   parallel CI runs, across developers running tests locally, and across any run where a
   previous run's accounts were not cleaned up. BUG-019 (duplicate device_id) also means
   the Android-side registrations are not reliably re-runnable with the same username.

2. **Stale state:** If a previous run left `smoke-test-plot` or `smoke-test-trellis-a`
   in the test DB, a subsequent run may find unexpected pre-existing data, breaking
   assertions about fresh state.

3. **Invite token expiry:** Tokens expire in 48 h. Hard-coded tokens would expire
   between CI runs.

### Proposed strategy

**For fully automated CI runs (web-only, pending TST-006 for Android):**

- Use `ApiHelper.generateInviteToken()` at the start of each test run to create a fresh
  token. This already works (confirmed in TST-005 smoke tests).
- Use `Actor.register()` with a timestamp-suffixed username (the default in `Actor.ts`
  already does this: `${label}-${Date.now().toString(36).slice(-8)}`).
- Create the shared plot, trellises, and uploads fresh each run. This adds ~15–30 s per
  run but eliminates all state-contamination risks.
- Do not clean up after runs — the test environment is throw-away. Manual DB cleanup
  before major test cycles is acceptable (OpsManager task).

**For manual smoke test sessions:**

- Use readable usernames (`smokeA`, `smokeB`) with a run-specific numeric suffix
  if needed (e.g. `smokeA-16` for the 16 May run). Record the suffix in the TST-010
  checklist so results are traceable.
- Generate tokens via `curl` with the API key (pattern from TST-007 Journey 2).

**Environment variable requirements for CI:**
- `PLAYWRIGHT_STAGING_API_KEY` — already documented in TST-005. Must be set as a CI
  secret for `ApiHelper.generateInviteToken()` to work.

---

## New Playwright Test Files Needed

The following new files should be created as part of TST-004 (Playwright E2E suite):

### `HeirloomsWeb/e2e/journeys/shared-plot-smoke.spec.ts`

The primary smoke test journey. Covers all web-automatable sub-steps of Steps 1–5.

Suggested structure:
```
test.describe('Shared plot smoke — web side', () => {
  // Setup: two actors (actorA web proxy, actorB)
  // Step 1: register actorB via invite token
  // Step 2: verify actorB sees actorA in friends list
  // Step 3: actorB creates smoke-test-plot, invites actorA (API-based invite for Android side)
  // Step 4: actorA (web proxy) — use a pre-seeded item or web upload path
  //         actorA tags item → verify item appears in smoke-test-plot
  //         actorB opens plot → assert thumbnail and detail view decrypt
  // Step 5: actorB uploads item, tags, approves → actorA asserts decrypt
});
```

Note: For fully cross-platform Step 4 validation (User A on Android), the web-only
automation will need a "User A on web" proxy until TST-006 provides Android automation.

### `HeirloomsWeb/e2e/journeys/friends.spec.ts`

Dedicated friends list journey (shorter, can run independently):
```
test('friends list shows mutual friend after invite redemption', async ({ page }) => {
  // register actorA, generate invite, register actorB
  // actorB navigates to /friends → assert actorA display name visible
  // actorA navigates to /friends → assert actorB display name visible
});
```

### `HeirloomsWeb/e2e/support/api.ts` — extensions needed

The `ApiHelper` class needs the following additional methods for smoke test setup:

```typescript
getFriends(sessionToken: string): Promise<Friend[]>
createSharedPlot(sessionToken: string, name: string): Promise<Plot>
inviteToPlot(sessionToken: string, plotId: string, friendId: string): Promise<void>
createTrellis(sessionToken: string, tag: string, plotId: string): Promise<Trellis>
uploadPhoto(sessionToken: string, imageBytes: Buffer): Promise<Upload>
tagUpload(sessionToken: string, uploadId: string, tags: string[]): Promise<void>
getPlotItems(sessionToken: string, plotId: string): Promise<PlotItem[]>
```

These methods allow the test to bypass the browser for setup steps (account creation,
friendship seeding) and to assert state directly via the API without depending solely on
UI rendering.

---

## Automation Sequencing Recommendation

### Should BUG-022 and BUG-020 fixes be verified before automating Steps 4–5?

**Recommendation: verify fixes first, then automate with passing assertions.**

Both BUG-020 and BUG-022 are marked as fixed and merged (2026-05-16). However, they
have not yet been re-tested on staging (the next manual cycle, TST-010, is the gate).

The correct sequencing is:

1. **Deploy BUG-020 and BUG-022 fixes to the test environment** (OPS-004 / next staging
   build).
2. **Run TST-010 (manual staging checklist v0.55)** — specifically re-run Steps 4 and 5
   of this smoke test to confirm the fixes hold.
3. **Then write the Playwright automation** (TST-004 / `shared-plot-smoke.spec.ts`) with
   passing assertions for Steps 4.5 and 4.6 (web decrypt).

**Do NOT automate with known-failing assertions.** The reasons:

- Playwright tests run in CI. A test that is expected to fail provides no signal — it
  will always appear as a failure, making the test suite untrustworthy and training
  developers to ignore red test runs.
- The `test.fail()` / `test.fixme()` mechanisms in Playwright exist for this, but they
  should be used for genuinely intermittent issues, not for known-broken features.
  Using `test.fixme()` for a known-broken web decrypt step would obscure whether the
  test is failing for the expected reason or a new reason.
- BUG-022 is straightforward (a missing DEK format handler in `PhotoDetailPage.jsx`).
  The fix is complete. Waiting for TST-010 confirmation adds days, not weeks.

**If blocking TST-004 on TST-010 is unacceptable** (e.g. TST-010 is delayed by an
OPS-004 deployment gap), an acceptable intermediate approach is:

- Write the spec file with `test.fixme('BUG-022: web detail view decrypt — awaiting TST-010 confirmation')` on the detail-view assertions only.
- Merge it to document intent.
- Remove `test.fixme()` after TST-010 confirms the fix.

This keeps the failing test visible and clearly labelled without contaminating the pass/fail signal for the suite as a whole.

---

## Acceptance Criteria Summary

| Step | Pass condition |
|------|---------------|
| 1 | Both accounts registered; API confirms A↔B friendship |
| 2 | Friendship visible in Android Friends screen and web `/friends` page |
| 3 | `smoke-test-plot` visible in User A Shared screen and User B garden |
| 4 | User B (web) sees User A's item in plot; thumbnail and full image decrypt |
| 5 | User A (Android) sees User B's item in plot; thumbnail and full image decrypt |
| 6 | N/A — feature not yet implemented |

---

*Spec owner: TestManager. Next review: after TST-010 (v0.55 manual staging checklist).*
