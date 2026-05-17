---
id: TST-012
title: Manual staging test checklist — v0.56 iteration
category: Testing
priority: High
assigned_to: TestManager
status: held
touches: []
depends_on: []
---

## Status note

**HELD** — activate once the v0.56 agent batch has been deployed to staging and
OpsManager confirms staging is healthy. PA will update this note with the deployed
commit SHA and environment health-check before activating.

Bugs targeted for fix in this iteration (PA to confirm scope):

- **BUG-023** (High): Passphrase save fails 409 after web pairing
- **BUG-028** (Medium): Biometric gate flashes but doesn't block vault access
- **BUG-024** (Medium): Garden inline tag edit doesn't trigger trellis routing
- **BUG-027** (Medium): Garden not loading on launch — manual swipe required
- **BUG-029** (Medium): Web pairing session not registered as a device entry
- *(PA: add any additional fixes shipped in v0.56)*

---

## Goal

Walk through the key user journeys on staging to validate the full v0.56 stack before
closing the iteration. This checklist covers:

- Regression areas: auth, upload, E2EE vault, shared plots, Android (staging flavor),
  iOS, web
- Retests for bugs targeted in this iteration (see journey notes below)
- Confirmation that v0.55 fixes continue to hold
- Any new surfaces shipped in v0.56 (see placeholder section below)

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
- Android: Heirlooms Test (burnt-orange icon) — ensure you have the v0.56 staging APK
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
- [ ] Photo appears in Just Arrived in Garden on Android immediately on launch (BUG-027 retest — no swipe required)
- [ ] Photo appears in Just Arrived in Garden on web within ~30 s
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

## Journey 3: Friend connection and navigation (regression + BUG-025/BUG-026 retest)

**Setup:** User A and User B both exist (Journey 2 complete).

- [ ] Friendship is created automatically at invite redemption — confirm via API (both
  users in each other's friends list)
- [ ] User A can see User B in the Friends screen on Android (burger menu → Friends)
- [ ] Web Friends page: navigate to it via the main nav link — **BUG-025 retest** (link must exist, not require direct URL entry)
- [ ] User B visible on web Friends page
- [ ] User A sends an invite link to an already-logged-in User B — token is auto-applied
  and friend connection confirmed without manual paste — **BUG-026 retest**

**Result:** ___
**Notes:** ___

---

## Journey 4: Shared plot — creation, invite, and member accept (regression)

**Setup:** User A and User B are friends.

- [ ] User A creates a new shared plot ("v056 Test share")
- [ ] User A invites User B to the shared plot (Members → Invite → select User B)
- [ ] User B (Android): Shared → pending invite → Accept
- [ ] Both users see "v056 Test share" in their plot lists
- [ ] User A generates a share invite link via Android staging flavor
- [ ] Verify the invite link reads `https://test.heirlooms.digital/join?token=...`
  (not `heirlooms.digital`) — BUG-008 hold

**Result:** ___
**Notes:** ___

---

## Journey 5: Garden inline tag triggering trellis routing (BUG-024 retest)

**Setup:** User A is owner of "v056 Test share". A trellis exists routing tag
"v056share" → "v056 Test share" with `requires_staging=false` (auto-approve).

**What's new (expected):** BUG-024 fix — garden inline tag editor now builds
`prewrappedDeks` and passes them to `updateTags`, triggering trellis routing.

- [ ] User A uploads a photo — it lands in Just Arrived on the garden
- [ ] Tag the photo using the garden inline tag editor (NOT the detail view) with "v056share"
- [ ] Photo routes from Just Arrived directly to "v056 Test share" without visiting detail view — **BUG-024 retest**
- [ ] Photo appears in the shared plot for User B
- [ ] Repeat from web garden inline tag input — same result — **BUG-024 retest (web)**

**Result:** ___
**Notes:** ___

---

## Journey 6: Shared plot — staging approval flow (regression)

**Setup:** User A has a trellis routing tag "v056stage" → "v056 Test share" with
`requires_staging=true`.

- [ ] User A uploads a photo tagged "v056stage" — routes to staging queue
- [ ] User A opens app fresh (force-kill and reopen) → navigates to Shared →
  "v056 Test share" → approves the staged item WITHOUT visiting Garden first (BUG-009 hold)
- [ ] Approval succeeds (no "1 item(s) failed to approve" error)
- [ ] Photo appears in the shared plot for both users

**Result:** ___
**Notes:** ___

---

## Journey 7: Garden initial load and post-tag refresh (BUG-027 retest)

**Setup:** User A has existing items in garden. App fully closed.

**What's new (expected):** BUG-027 fix — GardenViewModel fires load immediately on
screen entry; plot staging counts update within 5 s after tag changes.

- [ ] Force-kill the Android app and reopen — Garden loads items immediately, no swipe required — **BUG-027 retest**
- [ ] Tag a Just Arrived item from the garden inline editor
- [ ] Plot staging count updates within 5 seconds without manual refresh — **BUG-027 retest**
- [ ] Web: upload a new photo — it appears in Just Arrived within the polling interval (no page reload required)

**Result:** ___
**Notes:** ___

---

## Journey 8: Member trellis creation (regression)

**Setup:** User B is a member (not owner) of "v056 Test share".

- [ ] User B (member) opens the web trellis creation form
- [ ] "v056 Test share" appears in the target plot dropdown (BUG-010 hold)
- [ ] User B creates a trellis: tag "b-share" → "v056 Test share"
- [ ] User B uploads a photo tagged "b-share" — routes correctly
- [ ] Item (approved if staged) appears in the shared plot for User A

**Result:** ___
**Notes:** ___

---

## Journey 9: Web garden reactivity after trellis routing (regression)

**Setup:** User A has a photo in Just Arrived. A trellis-matching tag is ready.

- [ ] On the web Garden page, a photo is visible in Just Arrived
- [ ] Apply the trellis-matching tag to the photo from the detail view
- [ ] Photo disappears from Just Arrived (optimistic update)
- [ ] WITHOUT navigating away or refreshing, the "v056 Test share" shared plot row
  updates within ~5 seconds (BUG-015 hold)
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

## Journey 11: Android session token security (regression)

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

## Journey 12: Web CSP + session token (regression)

- [ ] Open web app → DevTools → Application → Local Storage — confirm session token is
  NOT stored there (SEC-008 hold)
- [ ] DevTools → Network → confirm `Content-Security-Policy` header present on HTML responses
- [ ] App functions normally (login, upload, browse)

**Result:** ___
**Notes:** ___

---

## Journey 13: iOS regression (if iOS staging build available)

**Setup:** iOS staging build installed via TestFlight or direct install.

- [ ] App launches and vault unlock screen appears
- [ ] Log in successfully
- [ ] Upload a photo via iOS share sheet or camera
- [ ] Photo appears in Just Arrived in Garden
- [ ] QR scanner functions (scan a test QR code or invite link QR)
- [ ] Background the app while vault is visible — app switcher shows privacy overlay,
  not vault content (SEC-014 hold)

**Result:** ___
**Time taken:** ___
**Notes:** ___

---

## Journey 14: Android flavor smoke (regression)

**Setup:** Android staging flavor (burnt-orange icon) installed.

- [ ] App launches and vault unlock screen appears
- [ ] Log in as User A
- [ ] Garden shows Just Arrived row with items loading immediately (no swipe required — BUG-027 hold)
- [ ] Shared screen shows "v056 Test share"
- [ ] Trellises screen loads (labels all say "Trellis", not "Flow")

**Result:** ___
**Notes:** ___

---

## Journey 15: Passphrase setup after web pairing (BUG-023 retest)

**What's targeted:** BUG-023 fix — passphrase setup calls the correct endpoint so
web-registered users are not blocked by a 409 conflict.

**Setup:** Fresh web-only user (User C) — register via invite token, do NOT create
an Android pairing first.

- [ ] Register User C via web invite token — registration succeeds
- [ ] Navigate to vault → prompted to set a passphrase
- [ ] Enter and confirm a passphrase — save completes without error (no 409) — **BUG-023 retest**
- [ ] Force-reload the page (Cmd+Shift+R) — vault unlocks with the passphrase without error
- [ ] Re-login (new session) → vault unlocks with the passphrase
- [ ] No duplicate entry in `wrapped_keys` for User C's device

**Result:** ___
**Notes:** ___

---

## Journey 16: Biometric gate blocks vault access (BUG-028 retest)

**What's targeted:** BUG-028 fix — biometric gate must not auto-dismiss; cancel/dismiss
must not grant vault access; web informational note should appear.

**Setup:** User A logged in on Android staging and web. Biometric capability present
on test device.

- [ ] Navigate to Settings → biometric / vault security toggle
- [ ] Toggle is OFF by default
- [ ] Enable biometric gate — setting saved (no crash)
- [ ] Force-kill and reopen the Android app — biometric prompt appears and **stays open**,
  requiring explicit authentication — **BUG-028 retest**
- [ ] Cancel or dismiss the prompt — vault does NOT open, app remains on the unlock screen — **BUG-028 retest (dismiss = block)**
- [ ] Authenticate successfully with biometric → vault opens normally
- [ ] Web app Settings / vault unlock page shows informational note indicating biometric
  protection is active on mobile — **BUG-028 retest (web)**
- [ ] Disable biometric gate → force-kill and reopen → vault opens without biometric prompt

**Result:** ___
**Notes:** ___

---

## Journey 17: Device revocation with paired web session (BUG-029 retest)

**What's targeted:** BUG-029 fix — web pairing session registers a distinct device
entry so it is visible in Devices & Access and can be revoked.

**Setup:** User A has an Android device registered. User A completes web pairing
flow (QR or pairing code) in a browser session.

- [ ] After web pairing, navigate to Settings → Devices & Access on Android
- [ ] Both the Android device AND the web browser session appear as distinct entries — **BUG-029 retest**
- [ ] Revoke the web browser device from the Android Devices & Access screen
- [ ] Revoked web session receives a 401 on next API call (or is visibly signed out)
- [ ] Android device continues to function normally

**Result:** ___
**Notes:** ___

---

## Journey 18: *(PA placeholder — new v0.56 feature A)*

*(PA to fill in scope and steps for the first major feature shipping in v0.56.)*

**Result:** ___
**Notes:** ___

---

## Journey 19: *(PA placeholder — new v0.56 feature B)*

*(PA to fill in scope and steps for the second major feature shipping in v0.56.)*

**Result:** ___
**Notes:** ___

---

## Summary

**Date tested:** ___
**Tester:** Bret Calvey (CTO)
**Staging build:** ___ (server/web); Android built from HEAD same day

| Journey | Result | Notes |
|---------|--------|-------|
| 1. Upload and view | | |
| 2. Invite flow | | |
| 3. Friend connection and nav (BUG-025/026) | | |
| 4. Shared plot — creation and invite | | |
| 5. Garden inline tag → trellis routing (BUG-024) | | |
| 6. Staging approval flow | | |
| 7. Garden initial load and refresh (BUG-027) | | |
| 8. Member trellis creation | | |
| 9. Web garden reactivity | | |
| 10. Web upload methods | | |
| 11. Android session token security | | |
| 12. Web CSP + session token | | |
| 13. iOS regression | | |
| 14. Android flavor smoke | | |
| 15. Passphrase setup after web pairing (BUG-023) | | |
| 16. Biometric gate blocks access (BUG-028) | | |
| 17. Device revocation with web session (BUG-029) | | |
| 18. New v0.56 feature A | | |
| 19. New v0.56 feature B | | |

**Overall verdict:** ___

---

## Failures log

<!-- One entry per failure. Each becomes a BUG-XXX task in queue/. -->

---

## Completion notes

<!-- Test Manager appends here and moves file to tasks/done/ -->
