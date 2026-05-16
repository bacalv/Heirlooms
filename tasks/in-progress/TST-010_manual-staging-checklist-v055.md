---
id: TST-010
title: Manual staging test checklist — v0.55 iteration
category: Testing
priority: High
assigned_to: TestManager
status: held
touches: []
depends_on: []
---

## Status note

This task is **held** until staging is green for the v0.55 iteration. Activate it once
the developer batch for this iteration has been deployed to staging and OpsManager
confirms staging is healthy.

Bugs resolved since TST-007 (do not re-test as regressions — just confirm they hold):
- **BUG-020** (DONE 2026-05-16): Shared plot trellis auto-approve — client-side DEK
  re-wrap so items can publish without mandatory staging.
- **BUG-022** (DONE 2026-05-16): Web detail view blank for shared plot images — full
  image DEK now decrypted correctly with plot key.

Bugs still open going into this cycle (require explicit retest):
- **BUG-021**: Video detail view shows 0-second duration — metadata not extracted on
  upload. Medium priority.
- **BUG-019**: Registration shows "Username already exists" for duplicate device_id
  collision. Low priority.

---

## Goal

Walk through the key user journeys on staging to validate the full v0.55 stack before
closing the iteration. This checklist covers:
- Regression areas: auth, upload, E2EE vault, shared plots, Android (prod + staging
  flavors), iOS, web
- Retests for BUG-021 and BUG-019 (if fixed this iteration)
- Confirmation that BUG-020 and BUG-022 fixes hold
- Any new surfaces shipped in v0.55 (see placeholder section below)

Results feed into the Playwright E2E suite (TST-004) — failures found here should
generate BUG-XXX tasks.

## How to use this checklist

- Mark each step `[x]` as you go, or `[F]` for a failure
- Fill in the **Result** line under each journey: PASS / PARTIAL / FAIL + one-liner
- Note failures in the **Failures** section at the bottom — each becomes a BUG-XXX task
- When all journeys are done, fill in the **Summary** section and move this file to
  `tasks/done/`

## Environment

- API: https://test.api.heirlooms.digital
- Web: https://test.heirlooms.digital
- Android: Heirlooms Test (burnt-orange icon) — ensure you have the v0.55 staging APK
- iOS: TestFlight staging build (if available)
- API key (for setup): `(fetch from Secret Manager: heirlooms-test-api-key)`

**Pre-flight:** confirm staging is up before starting.
```
curl -s https://test.api.heirlooms.digital/health
```
Expected: `{"status":"ok"}`. If it fails, stop — ping OpsManager.

---

## Journey 1: Upload and view a photo (regression)

**Setup:** logged in as User A on Android and web.

- [ ] Upload a portrait-mode photo via Android (taken in non-default orientation if possible)
- [ ] Photo appears in Just Arrived in Garden on Android (within ~30 s, with polling)
- [ ] Photo appears in Just Arrived in Garden on web (within ~30 s)
- [ ] Thumbnail orientation matches the full-resolution detail view (BUG-005 hold)
- [ ] Full-resolution loads on both platforms
- [ ] Upload a second photo (landscape) — confirm thumbnail orientation also correct
- [ ] Opening detail view does NOT silently trigger a server-side rotation save (BUG-013 hold)

**Result:** ___
**Notes:** ___

---

## Journey 2: Invite flow (regression)

**Setup:** User A exists. No User B account yet (use a fresh email/username for this session).

- [ ] Generate invite token via API:
  ```
  curl -H "X-API-Key: $(gcloud secrets versions access latest --secret=heirlooms-test-api-key --project heirlooms-495416)" \
       https://test.api.heirlooms.digital/api/auth/invites
  ```
- [ ] Register User B via web using the invite token
- [ ] User B can log in successfully
- [ ] User B sees an empty garden

**Result:** ___
**Notes:** ___

---

## Journey 3: Friend connection (regression)

**Setup:** User A and User B both exist (Journey 2 complete).

- [ ] Friendship is created automatically at invite redemption — confirm via API (both
  users in each other's friends list)
- [ ] User A can see User B in the Friends screen on Android (burger menu → Friends)
- [ ] Web friends list page (WEB-001): User B visible on web if feature shipped

**Result:** ___
**Notes:** ___

---

## Journey 4: Shared plot — creation, invite, and member accept (regression)

**Setup:** User A and User B are friends.

- [ ] User A creates a new shared plot ("v055 Test share")
- [ ] User A invites User B to the shared plot (Members → Invite → select User B)
- [ ] User B (Android): Shared → pending invite → Accept
- [ ] Both users see "v055 Test share" in their plot lists
- [ ] User A generates a share invite link via Android staging flavor
- [ ] Verify the invite link reads `https://test.heirlooms.digital/join?token=...`
  (not `heirlooms.digital`) — BUG-008 hold

**Result:** ___
**Notes:** ___

---

## Journey 5: Shared plot — trellis auto-approve without mandatory staging (BUG-020 retest)

**Setup:** User A is owner of "v055 Test share". User A has a trellis routing tag
"v055share" → "v055 Test share" with `requires_staging=false` (auto-approve).

**What's new:** BUG-020 fixed (2026-05-16) — client-side DEK re-wrap now allows
auto-approve trellises targeting shared plots to work without forcing staging.

- [ ] User A creates a trellis: tag "v055share" → "v055 Test share",
  `requires_staging=false`
- [ ] User A uploads a photo tagged "v055share"
- [ ] Photo routes directly to "v055 Test share" (does NOT land in staging queue)
- [ ] Photo appears in the shared plot for User A immediately
- [ ] Photo appears in the shared plot for User B — **BUG-020 retest**

**Result:** ___
**Notes:** ___

---

## Journey 6: Shared plot — web detail view decryption (BUG-022 retest)

**Setup:** "v055 Test share" has at least one item (from Journey 5 or uploaded fresh).
User B is logged in on web.

**What's new:** BUG-022 fixed (2026-05-16) — web detail view now decrypts full image
using the plot key DEK correctly.

- [ ] User B opens "v055 Test share" on web
- [ ] Shared plot item thumbnail is visible (not blank)
- [ ] User B clicks the item → detail view opens
- [ ] Full-resolution image decrypts and displays correctly — **BUG-022 retest**
- [ ] No blank / failed-to-decrypt state

**Result:** ___
**Notes:** ___

---

## Journey 7: Staging approval flow (regression)

**Setup:** User A has a trellis routing tag "v055stage" → "v055 Test share" with
`requires_staging=true`.

- [ ] User A uploads a photo tagged "v055stage" — routes to staging queue
- [ ] User A opens app fresh (force-kill and reopen) → navigates to Shared →
  "v055 Test share" → approves the staged item WITHOUT visiting Garden first (BUG-009 hold)
- [ ] Approval succeeds (no "1 item(s) failed to approve" error)
- [ ] Photo appears in the shared plot for both users

**Result:** ___
**Notes:** ___

---

## Journey 8: Member trellis creation (regression)

**Setup:** User B is a member (not owner) of "v055 Test share".

- [ ] User B (member) opens the web trellis creation form
- [ ] "v055 Test share" appears in the target plot dropdown (BUG-010 hold)
- [ ] User B creates a trellis: tag "b-share" → "v055 Test share"
- [ ] User B uploads a photo tagged "b-share" — routes correctly
- [ ] Item (approved if staged) appears in the shared plot for User A

**Result:** ___
**Notes:** ___

---

## Journey 9: Web garden reactivity after trellis routing (BUG-015 hold)

**Setup:** User A has a photo in Just Arrived. A trellis-matching tag is ready.

- [ ] On the web Garden page, a photo is visible in Just Arrived
- [ ] Apply the trellis-matching tag to the photo
- [ ] Photo disappears from Just Arrived (optimistic update)
- [ ] WITHOUT navigating away or refreshing, the "v055 Test share" shared plot row
  updates within ~5 seconds (BUG-015 excludeIds fix hold)
- [ ] Photo appears in the shared plot row

**Result:** ___
**Notes:** ___

---

## Journey 10: Web upload methods (regression)

**Setup:** User A logged in on web.

- [ ] Drag-and-drop upload: drag a photo file onto the garden — upload completes
- [ ] CMD+V paste upload: copy an image to clipboard, paste in the app — upload completes
- [ ] Both uploads appear in Just Arrived within ~30 s

**Result:** ___
**Notes:** ___

---

## Journey 11: Video upload and duration display (BUG-021 retest — if fixed)

**Setup:** Staging must have BUG-021 fix deployed. Skip and mark SKIPPED if not yet in
staging.

**What's needed:** Video duration extracted on upload and stored; both Android and web
detail views display the correct duration.

- [ ] Confirm BUG-021 is deployed (check build notes or ask Developer)
- [ ] Record or import a video (externally recorded) and upload via Android staging
- [ ] Thumbnail generates correctly
- [ ] Android detail view shows correct duration (not 0 seconds) — **BUG-021 retest**
- [ ] Web detail view shows correct duration — **BUG-021 retest**
- [ ] Video plays correctly on both platforms

**Result:** ___
**Notes:** ___

---

## Journey 12: Duplicate device_id error message (BUG-019 retest — if fixed)

**Setup:** Staging must have BUG-019 fix deployed. Skip and mark SKIPPED if not yet in
staging.

**What's needed:** Registration shows a clear device-specific error (not "Username
already exists") when device_id collision is detected.

- [ ] Confirm BUG-019 is deployed
- [ ] Clear app storage on Android staging device to force a duplicate device_id scenario
- [ ] Attempt to register a fresh username on the cleared device
- [ ] Error message is clearly device-specific, not "Username already exists" — **BUG-019 retest**

**Result:** ___
**Notes:** ___

---

## Journey 13: Android session token security (regression)

- [ ] Log in on Android staging — app functions normally
- [ ] Confirm session token is NOT stored as plaintext in SharedPreferences:
  ```
  adb shell run-as digital.heirlooms.app.test cat shared_prefs/heirloom_prefs.xml
  ```
  Expected: no plaintext token (SEC-007 hold)
- [ ] Log out and log in again — session resumes correctly

**Result:** ___
**Notes:** ___

---

## Journey 14: Web CSP + session token (regression)

- [ ] Open web app → DevTools → Application → Local Storage — confirm session token is
  NOT stored there (SEC-008 hold)
- [ ] DevTools → Network → confirm `Content-Security-Policy` header present on HTML responses
- [ ] App functions normally (login, upload, browse)

**Result:** ___
**Notes:** ___

---

## Journey 15: iOS regression (if iOS staging build available)

**Setup:** iOS staging build installed via TestFlight or direct install.

- [ ] App launches and vault unlock screen appears
- [ ] Log in successfully
- [ ] Upload a photo via iOS share sheet or camera
- [ ] Photo appears in Just Arrived in Garden
- [ ] QR scanner functions (scan a test QR code or invite link QR)

**Result:** ___
**Time taken:** ___
**Notes:** ___

---

## Journey 16: Android flavor smoke (regression)

**Setup:** Android staging flavor (burnt-orange icon) installed.

- [ ] App launches and vault unlock screen appears
- [ ] Log in as User A
- [ ] Garden shows Just Arrived row with polling working
- [ ] Shared screen shows "v055 Test share"
- [ ] Trellises screen loads (labels all say "Trellis", not "Flow")

**Result:** ___
**Notes:** ___

---

## Journey 17: New surfaces — v0.55 additions

<!-- PLACEHOLDER — fill this in once the developer batch for v0.55 is known. -->
<!-- For each new feature or fix deployed in v0.55, add a journey here covering: -->
<!--   - What was shipped -->
<!--   - Steps to exercise it -->
<!--   - Pass criteria -->

*(To be completed by PA / Developer when v0.55 scope is finalised.)*

---

## Summary

**Date tested:** ___
**Tester:** ___
**Staging build:** ___ (git SHA or build tag)

| Journey | Result | Notes |
|---------|--------|-------|
| 1. Upload and view | | |
| 2. Invite flow | | |
| 3. Friend connection | | |
| 4. Shared plot — creation and invite | | |
| 5. Auto-approve without staging (BUG-020) | | |
| 6. Web detail view decryption (BUG-022) | | |
| 7. Staging approval flow | | |
| 8. Member trellis creation | | |
| 9. Web garden reactivity | | |
| 10. Web upload methods | | |
| 11. Video duration (BUG-021) | | |
| 12. Duplicate device_id error (BUG-019) | | |
| 13. Android session token security | | |
| 14. Web CSP + session token | | |
| 15. iOS regression | | |
| 16. Android flavor smoke | | |
| 17. New surfaces — v0.55 | | |

**Overall verdict:** ___

---

## Failures log

<!-- One entry per failure. Each becomes a BUG-XXX task in queue/. -->

---

## Completion notes

<!-- Test Manager appends here and moves file to tasks/done/ -->
