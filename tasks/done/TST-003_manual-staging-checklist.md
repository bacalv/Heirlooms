---
id: TST-003
title: Manual staging test checklist
category: Testing
priority: High
status: in-progress
depends_on: [TST-002]
touches: []
assigned_to: TestManager
estimated: 30-60 minutes (manual)
---

## Goal

Walk through the key user journeys on the staging environment to validate the full stack before writing automated tests. This becomes the reference for the Playwright suite (TST-004).

## How to use this checklist

- Mark each step `[x]` as you go, or `[F]` for a failure
- Fill in the **Result** line under each journey with PASS / PARTIAL / FAIL and a one-liner
- Note any failures in the **Failures** section at the bottom — each becomes a BUG-XXX task
- When all journeys are done, fill in the **Summary** section and move this file to `tasks/done/`

## Environment

- API: https://test.api.heirlooms.digital
- Web: https://test.heirlooms.digital
- Android: Heirlooms Test (burnt-orange icon)
- API key (for setup): `(rotated 2026-05-15 — fetch from Secret Manager: heirlooms-test-api-key)`

**Pre-flight:** confirm staging is up before starting.
```
curl -s https://test.api.heirlooms.digital/health
```
Expected: `{"status":"ok"}` (or similar). If it fails, stop — ping OpsManager.

---

## Journey 1: Upload and view a photo

**Setup:** logged in as User A on Android and web.

- [x] Upload a photo via Android
- [x] Photo appears in garden on Android
- [x] Photo appears in garden on web (https://test.heirlooms.digital)
- [x] Thumbnail loads on both platforms
- [x] Full-resolution loads on both platforms

**Result:** PARTIAL — required BUG-003 + BUG-004 fixes before passing; first upload had incorrect thumbnail rotation
**Time taken:** ~45 min (including bug investigation and deploy)
**Notes:** Second upload (post-deploy) passed cleanly. First photo thumbnail was rotated incorrectly; detail view correct. Logged as BUG-005.

---

## Journey 2: Create a second user (invite flow)

**Setup:** logged in as User A. User B account does not exist yet.

- [x] Generate invite token via API:
  ```
  curl -H "X-API-Key: $(gcloud secrets versions access latest --secret=heirlooms-test-api-key --project heirlooms-495416)" \
       https://test.api.heirlooms.digital/api/auth/invites
  ```
- [x] Copy the invite token from the response
- [x] Open https://test.heirlooms.digital in a fresh browser session (incognito)
- [x] Register User B using the invite token
- [x] User B can log in successfully
- [x] User B sees an empty garden (no items from User A)

**Result:** PASS
**Time taken:** ~5 min
**Notes:** Token generated via curl by Test Manager. Registration and login worked first time.

---

## Journey 3: Friend connection

**Setup:** User A and User B both exist (Journey 2 complete).

- [x] Friendship created automatically when User B redeemed User A's invite (confirmed via API — both users visible in friends list)
- [x] User A can see User B in the Friends screen on Android (burger menu → Friends)
- [x] Web has no standalone friends list UI — `getFriends` exists in api.js but is only used internally for sharing. Not a bug, missing feature.

**Note:** The original checklist steps ("Settings → Sharing Key", "User B adds User A") describe a flow that no longer exists. Friendships are created implicitly at invite redemption (`AuthService.createFriendship`). Checklist updated to match current behaviour.

**Result:** PASS
**Time taken:** ~5 min
**Notes:** Web has no friends list page — API client exists but no UI surface. Consider adding to web backlog.

---

## Journey 4: Shared plot

**Setup:** User A and User B are friends (Journey 3 complete).

- [x] User A creates a new **shared** plot ("Test share") via Shared screen → +
- [x] User A invites User Y (via Bret's Android invite link, correcting domain BUG-008)
- [x] User Y accepts invite on web — joins the plot
- [x] Both users can see the shared plot in their plot list
- [x] User A sets up trellis routing tag "share" → Test share (requires_staging=true)
- [x] User A uploads a photo tagged "share" — routes to staging queue
- [x] User A approves the staging item (after reopening accidentally-closed plot)
- [x] Photo appears in the shared plot for both users

**Result:** PARTIAL — works end-to-end but required multiple bug workarounds
**Time taken:** ~60 min
**Notes:** BUG-007 (web sharing key), BUG-008 (invite URL domain), BUG-009 (approval sharing key), UX-001 (small tap targets), UX-002 (no closed plot indicator) all found. No direct "add to shared plot" gesture (FEAT-002). Plot accidentally closed via small tap target.

---

## Journey 5: Flows / Trellises

**Setup:** User A logged in. At least one plot exists to route to.

- [x] User A creates a trellis with tag criteria ("share" → Test share, requires_staging=true)
- [x] User A uploads a photo with the matching tag
- [x] Photo auto-routes to the staging queue for Test share (not in main garden)
- [x] Photo visible in shared plot after approval

**Result:** PASS — covered as part of Journey 4
**Time taken:** —
**Notes:** App still shows old label "Flow" (REF-001 rename not yet deployed to staging APK).

---

## Journey 6: Web-specific

**Setup:** User A logged in on web (https://test.heirlooms.digital).

- [x] Drag-and-drop upload: drag a photo file onto the garden — upload completes successfully (required GCS CORS fix for staging domain)
- [x] CMD+V paste upload: copy an image to clipboard, paste in the app — upload completes successfully
- [x] Create a trellis via web UI with auto-approve toggle ON — saves correctly, shows "Auto-add to collection" on trellis card

**Result:** PASS — required GCS CORS update to add staging origins
**Time taken:** ~20 min
**Notes:** GCS bucket CORS was missing `test.heirlooms.digital` — fixed with `gsutil cors set`. Web trellis form still shows "Flow name"/"Create flow" labels (BUG-011). Members can't target shared plots in trellis creation (BUG-010). Friend sharing web-to-web confirmed working (BUG-007 validated).

---

## Summary

**Date tested:** 2026-05-15
**Tester:** Bret
**Staging build:** fa3f41d (main, post BUG-003/004/007 fixes)

| Journey | Result | Notes |
|---------|--------|-------|
| 1. Upload and view | PARTIAL | BUG-003, BUG-004 fixed during session; BUG-005 (thumb rotation) queued |
| 2. Invite / second user | PASS | Clean |
| 3. Friend connection | PASS | Auto-created at invite redemption; no separate add-friend flow |
| 4. Shared plot | PARTIAL | BUG-007, BUG-008, BUG-009, UX-001, UX-002 found; FEAT-002 queued |
| 5. Flows / Trellises | PASS | Covered in Journey 4; BUG-010, BUG-011 found |
| 6. Web-specific | PASS | GCS CORS fix required; all 3 web features confirmed |

**Overall verdict:** ISSUES FOUND — all journeys eventually passed but 11 bugs/features discovered and logged

---

## Failures log

<!-- One entry per failure. Each becomes a BUG-XXX task in queue/. -->

### [F3] New uploads never appear in Garden (confirmed on Android and web)
- **Journey:** Journey 1
- **Step:** Photo appears in garden on Android / web
- **Observed:** Photo visible in Explore on both platforms, absent from Garden on both, even after app restart and page reload
- **Root cause:** System plot (`is_system_defined=true`) queries `plot_items` table, but uploads are never inserted into `plot_items`. The `justArrived=true` fallback (tags-based query) is unreachable while a system plot exists.
- **Expected:** Photo appears in Garden's "Just Arrived" row immediately after upload
- **Severity:** High — blocks all Garden journey testing
- **Spawned task:** BUG-004

### [F2] UploadWorker hardcoded to production URL — all staging uploads fail at 0%
- **Journey:** Journey 1
- **Step:** Upload a photo via Android
- **Observed:** Upload sits at 0%, disappears after ~3 retries, "didn't take" notification shown
- **Root cause:** `UploadWorker.kt` has `BASE_URL = "https://api.heirlooms.digital"` hardcoded — ignores `BuildConfig.BASE_URL_OVERRIDE` used correctly by `HeirloomsApi`. Staging token sent to prod server → 401.
- **Expected:** Staging flavor uploads to `https://test.api.heirlooms.digital`
- **Severity:** High — blocks all Journey 1–6 testing on Android staging
- **Spawned task:** BUG-003

### [F1] "didn't take" error notification on upload failure
- **Journey:** Journey 1
- **Step:** Upload a photo via Android
- **Observed:** Upload failed (likely bad wifi) and Android showed a notification reading "didn't take" — a phrase from an old brand concept
- **Expected:** Plain-language error such as "Upload failed"
- **Severity:** Medium
- **Spawned task:** BUG-002

---

## Completion notes

All 6 journeys completed on 2026-05-15 with Bret as tester. 11 issues found and
logged as tasks during the session:

**Bugs fixed during session:** BUG-003, BUG-004, BUG-007 (deployed to staging)
**Bugs queued:** BUG-005, BUG-006, BUG-008, BUG-009, BUG-010, BUG-011
**Features queued:** FEAT-001, FEAT-002
**UX tasks queued:** UX-001, UX-002
**Ops tasks queued:** OPS-001, OPS-002
**Security task queued:** SEC-010
**Web feature queued:** WEB-001
**Investigation queued:** TST-006

TST-005 (Playwright infrastructure) is now unblocked — ARCH-002 and TST-003 both complete.