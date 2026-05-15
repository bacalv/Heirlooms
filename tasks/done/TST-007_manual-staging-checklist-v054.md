---
id: TST-007
title: Manual staging test checklist — v0.54 iteration
category: Testing
priority: High
assigned_to: TestManager
status: held
touches: []
depends_on: []
---

## Status note

This task is **held** until Phase 6 (staging is green for the v0.54 iteration). Activate it once
the developer batch (BUG-008, BUG-009, BUG-010, BUG-011, BUG-012, SEC-007, SEC-008) has been
deployed to staging and OpsManager confirms staging is healthy.

## Goal

Walk through the key user journeys on staging to validate the full v0.54 stack before
closing the iteration. This checklist covers:
- Regression areas: auth, upload, E2EE vault, shared plots, Android (prod + staging flavors), iOS
- New fixes being validated this iteration (see journey notes below)
- Any new features added in v0.54

Results feed into the Playwright E2E suite (TST-004) — failures found here should generate BUG-XXX tasks.

## How to use this checklist

- Mark each step `[x]` as you go, or `[F]` for a failure
- Fill in the **Result** line under each journey: PASS / PARTIAL / FAIL + one-liner
- Note failures in the **Failures** section at the bottom — each becomes a BUG-XXX task
- When all journeys are done, fill in the **Summary** section and move this file to `tasks/done/`

## Environment

- API: https://test.api.heirlooms.digital
- Web: https://test.heirlooms.digital
- Android: Heirlooms Test (burnt-orange icon) — ensure you have the v0.54 staging APK
- iOS: TestFlight staging build (if available)
- API key (for setup): `(rotated 2026-05-15 — fetch from Secret Manager: heirlooms-test-api-key)`

**Pre-flight:** confirm staging is up before starting.
```
curl -s https://test.api.heirlooms.digital/health
```
Expected: `{"status":"ok"}`. If it fails, stop — ping OpsManager.

---

## Journey 1: Upload and view a photo (regression + BUG-005 retest)

**Setup:** logged in as User A on Android and web.

**What's new:** BUG-003 (UploadWorker URL) and BUG-004 (Garden system plot) were fixed in v0.53.
Validate the fixes hold and retest BUG-005 (thumbnail rotation).

- [ ] Upload a portrait-mode photo via Android (taken in non-default orientation if possible)
- [ ] Photo appears in Just Arrived in Garden on Android (within ~30 s)
- [ ] Photo appears in Just Arrived in Garden on web (within ~30 s)
- [ ] Thumbnail orientation matches the full-resolution detail view (BUG-005 retest)
- [ ] Full-resolution loads on both platforms
- [ ] Upload a second photo (landscape) — confirm thumbnail orientation also correct

**Result:** PARTIAL
**Notes:** Web PASS — thumbnails and detail correct, BUG-005 not reproduced. Android: garden requires manual refresh after upload (UX-003 — polling interval). Critical new bug: opening detail view without pressing rotate causes rotation to be persisted server-side on both platforms → BUG-013 logged (High priority).

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

**Result:** PASS

---

## Journey 3: Friend connection (regression)

**Setup:** User A and User B both exist (Journey 2 complete).

- [ ] Friendship is created automatically at invite redemption — confirm via API (both users in each other's friends list)
- [ ] User A can see User B in the Friends screen on Android (burger menu → Friends)
- [ ] Web has no standalone friends list UI (expected — no regression)

**Result:** PASS
**Notes:** API confirms User B in User A's friends list immediately after invite redemption. Friends screen check deferred to Android manual step.

---

## Journey 4: Shared plot + staging approval (regression + BUG-008, BUG-009 retest)

**Setup:** User A and User B are friends. User A owns a shared plot.

**What's new:**
- BUG-008 fix: Android staging flavor should now generate invite links pointing to `test.heirlooms.digital`
- BUG-009 fix: Staging approval should succeed even if Garden hasn't been visited in the same session

- [ ] User A creates a new shared plot ("v054 Test share")
- [ ] User A generates a share invite link via Android (Device & Access or share flow)
- [ ] Verify the invite link reads `https://test.heirlooms.digital/join?token=...` (not `heirlooms.digital`) — **BUG-008 retest**
- [ ] User B opens the invite link and joins the shared plot
- [ ] Both users see the shared plot in their plot list
- [ ] User A creates a trellis: tag "v054share" → "v054 Test share" (requires_staging=true)
- [ ] User A uploads a photo tagged "v054share" — routes to staging queue
- [ ] User A opens the app fresh (force-kill and reopen) → navigates directly to Shared → "v054 Test share" → approve the staged item WITHOUT visiting Garden first — **BUG-009 retest**
- [ ] Approval succeeds (no "1 item(s) failed to approve" error)
- [ ] Photo appears in the shared plot for both users

**Result:** PASS
**Notes:** BUG-008 PASS — invite link shows test.heirlooms.digital. BUG-009 PASS — approval succeeded with no error (sharing key loaded at vault unlock, not lazily at Garden visit). Direct add-member flow used for shared plot invite (no link-based member invite in this flow). Staging approval tested with requires_staging=true trellis.

---

## Journey 5: Member trellis creation for shared plot (BUG-010 retest)

**Setup:** User B is a member (not owner) of "v054 Test share".

**What's new:** BUG-010 fix — members should now be able to create trellises targeting plots they've joined.

- [ ] User B (member, not owner) opens the web trellis creation form
- [ ] "v054 Test share" appears in the target plot dropdown (not just User B's private plots)
- [ ] User B creates a trellis: tag "b-share" → "v054 Test share" (requires_staging=true)
- [ ] User B uploads a photo tagged "b-share" — routes to staging queue for "v054 Test share"
- [ ] User A (owner) approves the item → photo appears in the shared plot

**Result:** PASS
**Notes:** BUG-010 PASS — Fire 1 (member) can see "v054 Test share" as a trellis target. Approved items visible to owner.

---

## Journey 6: Web trellis UI labels (BUG-011 retest)

**Setup:** User A logged in on web.

**What's new:** BUG-011 fix — trellis creation form should no longer show "Flow name" or "Create flow".

- [ ] Open the Trellises page on web
- [ ] Click to create a new trellis
- [ ] Confirm form label reads "Trellis name" (not "Flow name") — **BUG-011 retest**
- [ ] Confirm submit button reads "Create trellis" (not "Create flow") — **BUG-011 retest**
- [ ] Create the trellis — it saves correctly and appears in the list

**Result:** PASS
**Notes:** BUG-011 PASS — form shows "New trellis" label (acceptable variant, no "Flow" terminology remaining).

---

## Journey 7: Web garden reactivity after trellis routing (BUG-012 retest)

**Setup:** User A has a trellis routing tag "v054share" → "v054 Test share" (from Journey 4 or set up fresh). User A has a photo in Just Arrived.

**What's new:** BUG-012 fix — the target shared plot row should update without requiring a manual page refresh.

- [ ] On the web Garden page, a photo is visible in Just Arrived
- [ ] Apply the trellis-matching tag to the photo
- [ ] Photo disappears from Just Arrived (optimistic update — expected)
- [ ] Without navigating away or refreshing, the "v054 Test share" shared plot row updates within ~2 seconds — **BUG-012 retest**
- [ ] Photo appears in the shared plot row

**Result:** FAIL
**Notes:** BUG-012 fix incomplete. Auto-approve trellis confirmed (screenshots), item disappears from Just Arrived correctly, but shared plot row does not update after 10+ seconds. Manual refresh required. Logged as BUG-015.

---

## Journey 8: Web-specific upload methods (regression)

**Setup:** User A logged in on web.

- [ ] Drag-and-drop upload: drag a photo file onto the garden — upload completes
- [ ] CMD+V paste upload: copy an image to clipboard, paste in the app — upload completes
- [ ] Both uploads appear in Just Arrived within ~30 s

**Result:** PASS

---

## Journey 9: Android session token security (SEC-007 retest, if deployed)

**Setup:** SEC-007 (Android Keystore session token encryption) must be deployed. Skip if not yet in staging.

- [ ] Confirm SEC-007 is deployed (check build notes or ask Developer)
- [ ] Log in on Android staging — app functions normally
- [ ] Session token is stored in Keystore (not SharedPreferences plaintext) — verify via `adb shell run-as digital.heirlooms.app.test cat shared_prefs/...` and confirm token is not plaintext
- [ ] Log out and log in again — session resumes correctly

**Result:** PASS
**Notes:** heirloom_prefs.xml empty; heirloom_prefs_enc.xml contains only Tink-encrypted blobs. No plaintext session token in SharedPreferences.

---

## Journey 10: Web CSP + session token (SEC-008 retest, if deployed)

**Setup:** SEC-008 (CSP header + session token off localStorage) must be deployed. Skip if not yet in staging.

- [ ] Confirm SEC-008 is deployed (check build notes or ask Developer)
- [ ] Open web app, open browser DevTools → Application → Local Storage — confirm session token is NOT stored there
- [ ] Open DevTools → Network — confirm a `Content-Security-Policy` header is present on HTML responses
- [ ] App functions normally (login, upload, browse)

**Result:** PASS
**Notes:** localStorage contains only heirlooms-deviceId, heirlooms-vaultSetUp, heirlooms_display_name — no session token. CSP header verified via curl (connect-src test.api.heirlooms.digital). App functions normally.

---

## Journey 11: iOS regression (if iOS staging build available)

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

## Journey 12: Android flavor regression (smoke test)

**Setup:** Android staging flavor (burnt-orange icon) installed.

- [ ] App launches and vault unlock screen appears
- [ ] Log in as User A
- [ ] Garden shows Just Arrived row
- [ ] Shared screen shows "v054 Test share"
- [ ] Trellises screen loads (labels all say "Trellis", not "Flow")

**Result:** PASS

---

## Summary

**Date tested:** 2026-05-15
**Tester:** Bret (CTO)
**Staging build:** 94bc7df (main)

| Journey | Result | Notes |
|---------|--------|-------|
| 1. Upload and view | PARTIAL | Web PASS; Android no auto-refresh (UX-003); BUG-013 rotation on detail view |
| 2. Invite flow | PASS | |
| 3. Friend connection | PASS | API confirmed |
| 4. Shared plot + staging approval | PASS | BUG-008 PASS; BUG-009 PASS |
| 5. Member trellis creation | PASS | BUG-010 PASS |
| 6. Web trellis UI labels | PASS | BUG-011 PASS — shows "New trellis" |
| 7. Web garden reactivity | FAIL | BUG-012 fix incomplete → BUG-015 |
| 8. Web upload methods | PASS | Drag-and-drop + paste both work |
| 9. Android session token (SEC-007) | PASS | No plaintext in SharedPreferences |
| 10. Web CSP + session token (SEC-008) | PASS | CSP correct; no session token in localStorage |
| 11. iOS regression | SKIPPED | Xcode project not yet set up |
| 12. Android flavor smoke | PASS | |

**Overall verdict:** CONDITIONAL PASS — 9 pass, 1 partial, 1 fail, 1 skipped. Two new high-priority bugs (BUG-013 rotation, BUG-016 E2EE decryption) plus BUG-015 regression. Recommend fixing BUG-013 and BUG-016 before production release.

---

## Failures log

<!-- One entry per failure. Each becomes a BUG-XXX task in queue/. -->

**Setup — pairing flow (3 issues found and fixed inline, 2026-05-15):**

- **[FIXED]** Android staging flavor pairing instruction showed `heirlooms.digital` (hardcoded) instead of `test.heirlooms.digital`. Fix: use `INVITE_BASE_URL` in `DevicesAccessScreen.kt`. Commit `aaf3961`.
- **[FIXED]** Staging web bundle baked with production API URL — `VITE_API_URL` build-arg was never passed to `docker build`. Fix: runbook §3 updated, image rebuilt. Commit `41045f8`.
- **[FIXED]** Staging web CSP `connect-src` hardcoded to `api.heirlooms.digital`, blocking XHR to `test.api.heirlooms.digital`. Fix: `nginx.conf` → template with `envsubst`. Commit `94bc7df`.

---

## Completion notes

<!-- Test Manager appends here and moves file to tasks/done/ -->
