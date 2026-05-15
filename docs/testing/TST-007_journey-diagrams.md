# TST-007 Journey Diagrams — v0.54 Manual Staging Checklist

## What these diagrams represent

These are **test journey diagrams**, not protocol-level sequence diagrams. Each `journey` block
describes the steps a tester walks through when executing a specific test journey from the
TST-007 manual staging checklist. Satisfaction scores (1–5) express expected test confidence:
- **5** — established happy-path regression; expected to pass every iteration
- **3** — new bug fix being retested this iteration; moderate confidence
- **1** — conditional path or uncertain outcome (e.g. depends on a deployment flag)

For the underlying protocol flows (crypto, API sequences, state machines) see the spec docs:
- `docs/specs/uploading.md` — upload E2EE flows
- `docs/specs/onboarding.md` — registration, login, device pairing
- `docs/specs/shared-plots.md` — shared plot membership and key distribution
- `docs/specs/flows-trellises.md` — trellis creation, auto-routing, staging approval

---

## Journey dependency table

| Journey | Title | Depends on |
|---------|-------|------------|
| 1 | Upload and view a photo | — |
| 2 | Invite flow | — |
| 3 | Friend connection | Journey 2 |
| 4 | Shared plot + staging approval | Journey 3 |
| 5 | Member trellis creation for shared plot | Journey 4 |
| 6 | Web trellis UI labels | — (standalone) |
| 7 | Web garden reactivity after trellis routing | Journey 4 (or fresh setup) |
| 8 | Web-specific upload methods | — |
| 9 | Android session token security (SEC-007) | — (conditional on deployment) |
| 10 | Web CSP + session token (SEC-008) | — (conditional on deployment) |
| 11 | iOS regression | — (conditional on TestFlight build) |
| 12 | Android flavor smoke | Journey 4 (shared plot must exist) |

---

## Journey 1: Upload and view a photo

```mermaid
journey
  title Journey 1: Upload and view a photo (regression + BUG-005 retest)
  section Setup
    Confirm staging API is healthy: 5: Bret
    Ensure User A logged in on Android and web: 5: Bret, Android, Web
  section Portrait upload (Android)
    Upload portrait-mode photo via Android: 5: Android
    Photo appears in Just Arrived on Android within 30s: 5: Android, Server
    Photo appears in Just Arrived on web within 30s: 5: Web, Server
  section BUG-005 retest - thumbnail orientation
    Thumbnail orientation matches full-res detail view on Android: 3: Android
    Thumbnail orientation matches full-res detail view on web: 3: Web
    Full-resolution photo loads on both platforms: 5: Android, Web
  section Landscape upload (second photo)
    Upload second photo in landscape orientation: 5: Android
    Landscape thumbnail orientation correct on Android: 3: Android
    Landscape thumbnail orientation correct on web: 3: Web
```

---

## Journey 2: Invite flow

```mermaid
journey
  title Journey 2: Invite flow (regression)
  section Setup
    Confirm User A exists and is logged in: 5: Bret
    Identify a fresh email and username for User B: 5: Bret
  section Generate invite token
    Generate invite token via API (curl + gcloud secret): 5: Bret, Server
    Token returned in response: 5: Server
  section User B registration
    User B opens invite URL on web: 5: Web
    User B registers using the invite token: 5: Web, Server
    Registration succeeds and session token issued: 5: Server
  section Verify User B account
    User B logs in successfully: 5: Web, Server
    User B sees an empty garden: 5: Web
```

---

## Journey 3: Friend connection

```mermaid
journey
  title Journey 3: Friend connection (regression)
  section Prerequisites
    Journey 2 complete - User A and User B both exist: 5: Bret
  section Automatic friendship at invite redemption
    Confirm friendship created via API for User A: 5: Bret, Server
    Confirm friendship created via API for User B: 5: Bret, Server
  section Android friends list UI
    User A opens burger menu on Android: 5: Android
    User A navigates to Friends screen: 5: Android
    User B appears in User A friends list: 5: Android
  section Web (no standalone friends list - expected)
    Confirm web has no separate friends list page: 5: Web
```

---

## Journey 4: Shared plot and staging approval

```mermaid
journey
  title Journey 4: Shared plot + staging approval (regression + BUG-008, BUG-009 retest)
  section Prerequisites
    Journey 3 complete - User A and User B are friends: 5: Bret
  section Create shared plot
    User A creates new shared plot v054 Test share: 5: Android
    Shared plot appears in User A plot list: 5: Android
  section BUG-008 retest - invite link domain
    User A generates share invite link via Android: 3: Android
    Verify link reads test.heirlooms.digital (not heirlooms.digital): 3: Bret, Android
  section User B joins shared plot
    User B opens the invite link in browser: 5: Web
    User B joins v054 Test share: 5: Web, Server
    Both users see shared plot in their plot lists: 5: Android, Web
  section Trellis and upload setup
    User A creates trellis - tag v054share to v054 Test share (requires_staging=true): 5: Android
    User A uploads photo tagged v054share: 5: Android, Server
    Upload routes to staging queue for shared plot: 5: Server
  section BUG-009 retest - cold-start staging approval
    User A force-kills and reopens Android app: 3: Android
    User A navigates directly to Shared > v054 Test share: 3: Android
    User A approves staged item WITHOUT visiting Garden first: 3: Android
    Approval succeeds - no error toast: 3: Android, Server
    Approved photo appears in shared plot for User A: 5: Android
    Approved photo appears in shared plot for User B: 5: Android, Web
```

---

## Journey 5: Member trellis creation for shared plot

```mermaid
journey
  title Journey 5: Member trellis creation for shared plot (BUG-010 retest)
  section Prerequisites
    Journey 4 complete - User B is a member of v054 Test share: 5: Bret
  section BUG-010 retest - shared plot in member dropdown
    User B opens web trellis creation form: 3: Web
    v054 Test share appears in target plot dropdown: 3: Web
  section Member creates trellis targeting shared plot
    User B creates trellis - tag b-share to v054 Test share (requires_staging=true): 3: Web, Server
    Trellis saved and visible in User B trellis list: 3: Web
  section Member upload routes to shared staging queue
    User B uploads photo tagged b-share: 3: Web, Server
    Upload routes to staging queue for v054 Test share: 3: Server
  section Owner approval
    User A (owner) opens staging queue for v054 Test share on Android: 5: Android
    User A approves the item: 5: Android, Server
    Photo appears in v054 Test share for both users: 5: Android, Web
```

---

## Journey 6: Web trellis UI labels

```mermaid
journey
  title Journey 6: Web trellis UI labels (BUG-011 retest)
  section Setup
    User A logged in on web: 5: Bret, Web
    Navigate to Trellises page: 5: Web
  section BUG-011 retest - form label
    Click to create a new trellis: 3: Web
    Form label reads Trellis name (not Flow name): 3: Bret, Web
    Submit button reads Create trellis (not Create flow): 3: Bret, Web
  section Create trellis and verify persistence
    Submit the trellis creation form: 5: Web
    New trellis saves without error: 5: Web, Server
    New trellis appears in trellis list: 5: Web
```

---

## Journey 7: Web garden reactivity after trellis routing

```mermaid
journey
  title Journey 7: Web garden reactivity after trellis routing (BUG-012 retest)
  section Setup
    User A has trellis routing tag v054share to v054 Test share: 5: Bret, Web
    A photo is visible in Just Arrived on web Garden page: 5: Web
  section Tag and optimistic removal
    Apply tag v054share to the photo in Just Arrived: 5: Web
    Photo disappears from Just Arrived (optimistic update): 5: Web
  section BUG-012 retest - live shared plot row update
    Remain on Garden page without navigating away or refreshing: 3: Bret, Web
    v054 Test share shared plot row updates within ~2 seconds: 3: Web, Server
    Photo appears in the v054 Test share shared plot row: 3: Web
```

---

## Journey 8: Web-specific upload methods

```mermaid
journey
  title Journey 8: Web-specific upload methods (regression)
  section Setup
    User A logged in on web: 5: Bret, Web
  section Drag-and-drop upload
    Drag a photo file onto the Garden page: 5: Bret, Web
    Upload completes successfully: 5: Web, Server
    Photo appears in Just Arrived within ~30s: 5: Web
  section Clipboard paste upload
    Copy an image to the system clipboard: 5: Bret
    Paste into the web app (CMD+V or CTRL+V): 5: Bret, Web
    Upload completes successfully: 5: Web, Server
    Pasted photo appears in Just Arrived within ~30s: 5: Web
```

---

## Journey 9: Android session token security

```mermaid
journey
  title Journey 9: Android session token security (SEC-007 retest)
  section Prerequisites
    Confirm SEC-007 is deployed to staging: 1: Bret
  section Login smoke test
    Log in on Android staging flavor: 3: Android, Server
    App functions normally after login: 3: Android
  section Keystore verification
    Inspect shared_prefs via adb shell run-as: 3: Bret, Android
    Session token is NOT stored in plaintext in SharedPreferences: 3: Bret, Android
  section Session continuity
    Log out of Android staging app: 3: Android
    Log back in: 3: Android, Server
    Session resumes correctly - app usable: 3: Android
```

---

## Journey 10: Web CSP and session token storage

```mermaid
journey
  title Journey 10: Web CSP + session token (SEC-008 retest)
  section Prerequisites
    Confirm SEC-008 is deployed to staging: 1: Bret
  section LocalStorage check
    Open web app in browser: 3: Web
    Open DevTools > Application > Local Storage: 3: Bret
    Session token is NOT present in LocalStorage: 3: Bret, Web
  section CSP header check
    Open DevTools > Network panel: 3: Bret
    Inspect HTML response headers: 3: Bret, Web
    Content-Security-Policy header is present: 3: Bret, Web
  section App functionality
    Log in, upload a photo, browse Garden: 3: Web, Server
    App functions normally under new CSP: 3: Web
```

---

## Journey 11: iOS regression

```mermaid
journey
  title Journey 11: iOS regression (if TestFlight build available)
  section Prerequisites
    iOS staging build installed via TestFlight or direct install: 1: Bret, iOS
  section App launch and auth
    App launches and vault unlock screen appears: 3: iOS
    Log in successfully: 3: iOS, Server
  section Upload via iOS
    Upload a photo via iOS share sheet or camera: 3: iOS, Server
    Photo appears in Just Arrived in Garden: 3: iOS
  section QR scanner
    Open QR scanner within the iOS app: 3: iOS
    Scanner activates and can read a test QR code or invite link QR: 3: Bret, iOS
```

---

## Journey 12: Android flavor smoke test

```mermaid
journey
  title Journey 12: Android flavor smoke test (regression)
  section Prerequisites
    Android staging flavor (burnt-orange icon, v0.54 APK) installed: 5: Bret, Android
    Journey 4 complete - v054 Test share shared plot exists: 5: Bret
  section App launch and auth
    App launches and vault unlock screen appears: 5: Android
    Log in as User A: 5: Android, Server
  section Key screens
    Garden screen loads with Just Arrived row: 5: Android
    Shared screen shows v054 Test share: 5: Android
  section Trellis labels regression
    Open Trellises screen: 5: Android
    All labels read Trellis (not Flow): 5: Android, Bret
```
