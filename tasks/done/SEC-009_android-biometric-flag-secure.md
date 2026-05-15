---
id: SEC-009
title: Android — add FLAG_SECURE and evaluate biometric vault-unlock gate
category: Security
priority: Medium
status: queued
depends_on: [SEC-007]
touches: [HeirloomsApp/]
assigned_to: Developer
estimated: 1 day
---

## Background

Two MEDIUM findings from `docs/security/client-security-findings.md` (SEC-003 audit):

**A-05:** `MainActivity` does not set `FLAG_SECURE` on its window. The system's recent-apps
thumbnail and screenshot APIs can capture decrypted vault photos. A malicious background app with
screen-capture access (Accessibility services, screen recording) can capture live frames on older
Android versions.

**A-02:** The Android Keystore key wrapping the vault master key is generated with
`.setUserAuthenticationRequired(false)`. Any code running in the app process can call
`DeviceKeyManager.loadMasterKey()` without a biometric or PIN challenge.

## Goals

1. Set `FLAG_SECURE` to prevent screenshot and recent-apps thumbnail capture of vault content.
2. Evaluate and implement a biometric gate for vault unlock (Keystore key with
   `setUserAuthenticationRequired(true)` and a `BiometricPrompt` flow).

## Approach

### Part 1 — FLAG_SECURE (simpler, do first)

In `MainActivity.onCreate()`, add before `setContent`:
```kotlin
window.setFlags(
    WindowManager.LayoutParams.FLAG_SECURE,
    WindowManager.LayoutParams.FLAG_SECURE
)
```

This immediately prevents screenshots and recent-apps thumbnails across the entire app. No UX
change for users except that screenshots will be blank.

If the product team decides certain non-sensitive screens (e.g. Settings, Invite link) should
allow screenshots, those screens can be hosted in a separate Activity without FLAG_SECURE.

### Part 2 — Biometric gate for vault unlock

This is more complex and has UX implications. The approach depends on the product decision:

**Option A — Per-operation biometric (strictest):**
Set `.setUserAuthenticationRequired(true)` and `.setUserAuthenticationValidityDurationSeconds(-1)`
on the Keystore key. Every call to `DeviceKeyManager.loadMasterKey()` will require a
`BiometricPrompt` challenge. Users must biometric-authenticate each time the vault unlocks
(e.g. on app foreground after background timeout).

**Option B — Timed biometric (balanced):**
Set `.setUserAuthenticationRequired(true)` and `.setUserAuthenticationValidityDurationSeconds(30)`.
The Keystore key is usable for 30 seconds after biometric authentication. The user authenticates
once at vault unlock; subsequent operations within 30 seconds proceed without re-prompting.

**Option C — Explicit unlock screen (current-ish, enhanced):**
Keep `setUserAuthenticationRequired(false)` on the Keystore key but add a `BiometricPrompt` in
the vault unlock screen UI before calling `DeviceKeyManager.loadMasterKey()`. This is softer
(code injection can bypass the UI check) but avoids Keystore migration complexity.

**Recommendation:** Implement Option B for the Keystore key (hardware-enforced), paired with a
`BiometricPrompt` on vault unlock. The app already has a vault unlock screen; adding a biometric
step there is the natural UX entry point.

**Important:** Changing `setUserAuthenticationRequired` on an existing Keystore key requires
deleting and regenerating the key. This means users will need to re-pair or re-enter their
passphrase to re-wrap the master key on first run after the update. Design the migration carefully.

## Acceptance criteria

- `FLAG_SECURE` is set: screenshots of the vault UI produce a blank image.
- Biometric gate (whichever option is chosen by the CTO): vault unlock requires biometric
  or PIN confirmation before the master key is loaded.
- The vault unlock flow remains functional on devices without biometrics (fall back to PIN/password
  via `BiometricPrompt` configuration or graceful degradation).
- Existing enrolled users are not permanently locked out of their vault on upgrade.

## References

- Findings A-02 and A-05 in `docs/security/client-security-findings.md`
- `HeirloomsApp/app/src/main/kotlin/digital/heirlooms/crypto/DeviceKeyManager.kt` L63
- `HeirloomsApp/app/src/main/kotlin/digital/heirlooms/app/MainActivity.kt`
- [BiometricPrompt docs](https://developer.android.com/training/sign-in/biometric-auth)

## Completion notes

Implemented 2026-05-15 on branch `agent/developer-3/SEC-007`.

### Part 1 — FLAG_SECURE (DONE)

`window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)` added to `onCreate()` in both:
- `MainActivity` — hosts the full vault UI (garden, photo detail, capsules, etc.)
- `ShareActivity` — shows a preview of decrypted media before upload

`SettingsActivity` was intentionally excluded: it shows no decrypted vault content (only upload
queue status and Wi-Fi toggle), so FLAG_SECURE is not required there and would unnecessarily
prevent screenshots of non-sensitive UI.

**Acceptance criteria for Part 1 met:** screenshots and recent-apps thumbnails of vault content
will render as a blank frame on all API levels supported by the app (minSdk 26+).

### Part 2 — Biometric gate (DEFERRED — requires CTO decision)

Part 2 (finding A-02: `setUserAuthenticationRequired(false)` on the Keystore key wrapping the
vault master key) has **not** been implemented in this iteration. It is deferred for the following
reason:

The task presents three implementation options (A, B, C) with meaningfully different UX trade-offs:
- **Option A** (per-operation biometric) — most secure, most friction
- **Option B** (30-second timed biometric) — balanced; recommended in the task
- **Option C** (UI-only gate, softer) — least friction, bypassable in-process

Option B requires migrating the existing Keystore key (delete + regenerate), which forces all
enrolled users to re-pair or re-enter their passphrase on upgrade. This is a significant user
experience decision that needs explicit CTO sign-off before implementation.

**Action required:** CTO to choose Option A, B, or C and confirm acceptable migration UX before
Part 2 is scheduled. Once decided, a new task should be created targeting `DeviceKeyManager.kt`
and the vault unlock screen with the chosen option specified.
