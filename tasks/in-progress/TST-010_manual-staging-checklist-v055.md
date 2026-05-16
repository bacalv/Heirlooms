---
id: TST-010
title: Manual staging test checklist — v0.55 iteration
category: Testing
priority: High
assigned_to: TestManager
status: active
touches: []
depends_on: []
---

## Status note

**ACTIVE** — OPS-004 complete (2026-05-16). Deployed commit `8ff03fe`. Both test
server (`https://test.api.heirlooms.digital`) and web (`https://test.heirlooms.digital`)
health-checked green.

Bugs resolved since TST-007 (do not re-test as regressions — just confirm they hold):
- **BUG-020** (DONE 2026-05-16): Shared plot trellis auto-approve — client-side DEK
  re-wrap so items can publish without mandatory staging.
- **BUG-022** (DONE 2026-05-16): Web detail view blank for shared plot images — full
  image DEK now decrypted correctly with plot key.

Bugs now fixed and deployed — **run Journeys 11 and 12** (do not skip):
- **BUG-021** (DONE 2026-05-16): Video detail view shows 0-second duration — fixed.
- **BUG-019** (DONE 2026-05-16): Registration shows "Username already exists" for
  duplicate device_id collision — fixed.

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

## Journey 17a: Web invite link generation (WEB-002)

**What's new:** Web users can now generate invite links from the Friends page — previously
web-only users had no way to invite anyone.

- [ ] Log in as User A on web
- [ ] Navigate to Friends page
- [ ] Click "Invite a friend" (or equivalent button)
- [ ] Invite link is generated and displayed / copyable
- [ ] Invite link reads `https://test.heirlooms.digital/invite?token=...` (not prod domain)
- [ ] Open the invite link in a new browser session — registration page loads with token pre-filled

**Result:** ___
**Notes:** ___

---

## Journey 17b: Android account pairing / recovery (FEAT-003)

**What's new:** Android users can now recover an existing account after clearing app data
or installing on a new device, without needing a prior web session.

**Setup:** User A exists with a known username and recovery phrase (or web session for QR pairing).

- [ ] Clear app data on Android staging device (Settings → Apps → Heirlooms Test → Clear data)
- [ ] Launch app — confirm "Sign in to existing account" option is visible on the welcome screen
- [ ] Pair via recovery phrase (24-word phrase): enter phrase → vault unlocks and Garden loads
- [ ] Confirm existing media and plot memberships are intact post-pairing
- [ ] (If web pairing path also shipped): log in on web → Android shows QR pairing option → scan → paired successfully

**Result:** ___
**Notes:** ___

---

## Journey 17c: Android invite friend from Friends screen (FEAT-004)

**What's new:** Android users can generate and share invite links directly from the
Friends screen (burger menu → Friends).

- [ ] Log in as User A on Android staging flavor
- [ ] Burger menu → Friends
- [ ] "Invite a friend" button is visible
- [ ] Tap it — invite link is generated
- [ ] Android share sheet opens with the link
- [ ] Link reads `https://test.heirlooms.digital/invite?token=...` (not prod domain) — BUG-008 hold

**Result:** ___
**Notes:** ___

---

## Journey 17d: Closed plot visual indicator (UX-002)

**What's new:** Closed shared plots now show a locked/disabled state — no more silent
approve failures after accidentally closing a plot.

**Setup:** User A is owner of a shared plot.

- [ ] Close the shared plot via the status toggle (Shared screen → plot → status toggle)
- [ ] Closed plot shows a distinct visual state (lock icon, greyed out, or similar) in the Shared screen
- [ ] Closed plot shows distinct visual state in the Garden row
- [ ] "Approve" action is disabled or hidden for staged items in the closed plot
- [ ] "Share to plot" action is disabled or hidden
- [ ] Re-open the plot — visual state returns to normal, actions re-enabled

**Result:** ___
**Notes:** ___

---

## Journey 17e: Device revocation (SEC-011)

**What's new:** Users can now remove old devices from their account via Devices & Access.

**Setup:** User A has at least two devices registered (e.g. Android + web session).

- [ ] Navigate to Devices & Access (Settings → Devices & Access, or equivalent)
- [ ] Both devices listed with identifiable labels (device name / platform)
- [ ] Revoke the secondary device — confirm prompt shown before revocation
- [ ] Revoked device no longer appears in the list
- [ ] Session on the revoked device is invalidated — confirm revoked device gets a 401 on next API call
- [ ] Primary device continues to function normally

**Result:** ___
**Notes:** ___

---

## Journey 17f: Biometric gate — account-level setting (SEC-015)

**What's new:** Users can enable a biometric requirement to open the vault — stored
server-side and synced across devices.

**Setup:** User A logged in on Android staging and web.

- [ ] Navigate to Settings → find biometric / vault security toggle
- [ ] Toggle is OFF by default
- [ ] Enable biometric gate — confirm setting is saved (no crash)
- [ ] Force-kill and reopen the Android app — biometric prompt appears before vault opens
- [ ] Authenticate with biometric → vault opens normally
- [ ] Web app shows informational note: "Biometric protection is enabled on your account — this applies to your mobile devices only" (or equivalent)
- [ ] Disable biometric gate in Settings — force-kill and reopen → vault opens without biometric prompt

**Result:** ___
**Notes:** ___

---

## Journey 17g: iOS background privacy screen (SEC-014)

**Setup:** iOS staging build installed. Vault contains at least one photo.

**What's new:** iOS now shows a privacy overlay (brand background + logo) in the app
switcher when the vault is visible — equivalent to Android FLAG_SECURE.

- [ ] Open the vault on iOS (view a photo in the vault)
- [ ] Press Home button (or swipe up) to background the app while vault content is visible
- [ ] Open the app switcher — Heirlooms thumbnail shows the privacy overlay, NOT vault content
- [ ] Tap the app to return — vault content resumes correctly, overlay removed

**Result:** ___
**Notes:** ___

---

## Summary

**Date tested:** 2026-05-16
**Tester:** Bret Calvey (CTO)
**Staging build:** `8ff03fe` (server/web); Android built from HEAD same day

| Journey | Result | Notes |
|---------|--------|-------|
| 1. Upload and view | PASS | |
| 2. Invite flow | PASS | |
| 3. Friend connection | PARTIAL | Friendship created and visible on Android. Web /friends route exists but no nav link. Invite link doesn't auto-route token. |
| 4. Shared plot — creation and invite | PARTIAL | In-app invite and accept work. Shareable plot invite link not implemented on Android — BUG-008 hold skipped. |
| 5. Auto-approve without staging (BUG-020) | PARTIAL | Works via photo detail view. Garden inline tag edit doesn't send prewrappedDeks so routing silently fails from there. |
| 6. Web detail view decryption (BUG-022) | PASS | |
| 7. Staging approval flow | PARTIAL | Approval works (1 item processed, routed to plot). BUG-009 hold untestable — app always opens to Garden on launch so sharing key is always loaded before approval screen. |
| 8. Member trellis creation | BLOCKED | Passphrase setup fails 409 on POST /api/keys/devices (conflict with web pairing device entry). User B vault won't unlock after page refresh — can't test member trellis creation. |
| 9. Web garden reactivity | PARTIAL | Routing from detail view works, garden row updates correctly. Tagging from garden inline editor doesn't trigger trellis routing (BUG-G). New uploads intermittently don't appear in Just Arrived without manual refresh. |
| 10. Web upload methods | PASS | Drag-and-drop and paste both work after fixing CORS on test bucket. |
| 11. Video duration (BUG-021) | PASS | Duration label correct. Two Android fixes required: MediaExtractor → MediaMetadataRetriever for duration extraction; VideoPlayer OkHttpDataSource → DefaultDataSource for file:// URIs (video wouldn't play). |
| 12. Duplicate device_id error (BUG-019) | PASS | Validated during setup — second registration on same device showed device-specific error, not "Username already exists". |
| 13. Android session token security | PASS | Only heirloom_prefs_enc.xml exists — session token stored in EncryptedSharedPreferences, no plaintext. |
| 14. Web CSP + session token | PASS | CSP header present on HTML responses. Session token in sessionStorage (not localStorage) — cleared on tab close. Local Storage empty. |
| 15. iOS regression | SKIPPED | No iOS staging build available. |
| 16. Android flavor smoke | PARTIAL | Vault, login, Shared, Trellises all correct. Garden requires manual refresh (swipe down) before items appear — initial poll not loading on launch. |
| 17a. Web invite link generation | PASS | Tested during Journey 2 setup — invite link generated and redeemed successfully. |
| 17b. Android account pairing/recovery | PARTIAL | Account recovery via fresh device install worked. Web pairing flow confirmed. Full 24-word phrase recovery not tested. |
| 17c. Android invite from Friends screen | PASS | Fixed BUG-C (POST→GET), invite link generated and friendship created. |
| 17d. Closed plot visual indicator | PASS | Visual state changes correctly, approve disabled, re-open restores normal state. Garden UX issues noted: items appear outside row bounds; after tagging in Just Arrived, plot staging counts don't update without manual refresh. |
| 17e. Device revocation | PARTIAL | Devices & Access screen loads and shows current Android device correctly. Web pairing session did not register a separate device entry — only one device visible, so multi-device revocation flow untestable. |
| 17f. Biometric gate | PARTIAL | Biometric prompt flashes on reopen but dismisses immediately without blocking access — gate triggers but doesn't enforce. Web shows no informational note when biometric is enabled. |
| 17g. iOS background privacy screen | SKIPPED | No iOS build available. |

**Overall verdict:** CONDITIONAL PASS — core E2EE upload, decrypt, sharing, approval flows all work. Several bugs found and fixed inline during the session. Remaining issues logged as new tasks before production release.

---

## Failures log

<!-- One entry per failure. Each becomes a BUG-XXX task in queue/. -->

---

## Completion notes

<!-- Test Manager appends here and moves file to tasks/done/ -->
