---
id: FEAT-003
title: Android account pairing / recovery — pair a fresh Android install to an existing account
category: Feature
priority: Medium
status: queued
depends_on: []
touches:
  - HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/routes/
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/service/
assigned_to: Developer
estimated: 3–5 hours
---

## Background

Clearing app data on Android (or a fresh install on a new device) leaves the user on the
registration screen with no visible path to sign in to an existing account. The only current
recovery paths are:

- **Web pairing (QR code)** — requires an active web session on the same account, which in
  turn requires the passphrase-wrapped backup to be configured.
- **Recovery phrase** — 24-word phrase shown at first setup; must be recorded in advance.

If neither was set up (e.g. an Android-only user who never opened the web client), clearing
storage is permanently destructive. This was surfaced during BUG-016 staging retest (2026-05-15)
when Fire 1's app data was cleared and there was no way back in.

## Goal

Investigate and implement a pairing code flow that lets a fresh Android install re-authenticate
to an existing account without needing the web client — the reverse of the current web QR
pairing flow.

## Investigation questions

1. **What does "sign in" mean in our E2EE model?** The master key never leaves a device in
   plaintext, so a username/password login alone cannot restore vault access. Any pairing flow
   needs a trusted device (or the server-held passphrase blob) to hand off the master key.

2. **Existing pairing mechanism (web → Android):** A trusted device (web) generates a
   short-lived QR code; the new device scans it to initiate key exchange. Can this be
   inverted so that the trusted device is Android and the new device shows a code?

3. **Passphrase recovery path:** If the user has a passphrase-wrapped backup on the server,
   should the registration/login screen offer a "Recover with passphrase" option that
   bypasses the need for a trusted device entirely?

4. **Server support:** Does any new endpoint need to be added, or can the existing device
   pairing endpoint be reused?

## Proposed direction

Implement one or both of:

**Option A — "Recover with passphrase" on Android login screen**
Add a secondary button below the registration form: "Already have an account? Recover with
passphrase." This prompts for email + passphrase, derives the master key via Argon2id
(matching the web flow), and restores vault access. Requires the user to have set up a
passphrase previously.

**Option B — Android-initiated pairing code**
On the fresh Android install, show a short-lived numeric code (or QR). The user enters
this on a trusted device (web or another Android) to initiate the key-exchange handshake.
Mirror of the current web QR flow.

Option A is lower effort and covers the most common case. Option B is more robust but
requires both ends to support the new flow.

## Acceptance criteria

- A user who clears Android app data (or installs fresh) can recover their existing account
  without registering a new one
- The recovery path is visible on the Android login/registration screen
- Recovery restores the master key and sharing keypair correctly
- Existing registration flow is unaffected

## Completion notes

Completed 2026-05-16 by developer-7.

**Approach:** Implemented Option C (server-assisted passphrase recovery) as recommended by
FEAT-003a spike. No new server endpoints required — the existing `GET /api/keys/passphrase`
and `POST /api/auth/login` endpoints handle the full flow.

**Files changed (all in HeirloomsApp):**

- `ui/main/AccountRecoveryViewModel.kt` — NEW. State machine for the recovery flow:
  `authChallenge` → `authLogin` → `getPassphrase` → `unwrapMasterKeyWithPassphrase` →
  `setupVault` → `registerDevice`. Derives the master key from the passphrase-wrapped
  blob stored on the server using Argon2id with confirmed-matching parameters (m=65536,
  t=3, p=1 on both Android and web per the spike).

- `ui/main/AccountRecoveryScreen.kt` — NEW. Compose screen with username + passphrase
  fields and a "Back to registration" link. Wired to `AccountRecoveryViewModel`.

- `ui/main/InviteRedemptionScreen.kt` — Added `onRecoverAccount: () -> Unit` parameter
  (defaults to no-op for backward compat) and "Already have an account? Recover access"
  `TextButton` at the bottom of the registration form.

- `ui/main/MainApp.kt` — Added `recovering: Boolean` state variable and a new `when`
  branch that shows `AccountRecoveryScreen` when `recovering == true`. The branch sits
  above the invite-redemption branch so it takes priority. `onRecovered` sets
  `welcomed = true` so the welcome screen is skipped on successful recovery.

**Tests added** in `app/src/test/kotlin/digital/heirlooms/app/AuthTest.kt`:
- `recoveryFlow_challengeAndLogin_returnSessionToken` — verifies the auth exchange sequence.
- `recoveryFlow_wrongPassphrase_throwsAEADBadTagException` — wrong passphrase causes AEAD failure.
- `recoveryFlow_correctPassphrase_recoversMasterKey` — correct passphrase restores the master key.
- `recoveryFlow_noPassphraseBackup_getPassphraseReturnsNull` — 404 from server returns null.
- `recoveryFlow_badCredentials_loginThrowsUnauthorized` — 401 login throws "UNAUTHORIZED".

**No server changes** — the spike confirmed all required endpoints already exist.

**Tests passed:** `./gradlew :app:testProdDebugUnitTest --no-daemon` (24 tasks, BUILD SUCCESSFUL)
and `./gradlew test --no-daemon` in HeirloomsServer (BUILD SUCCESSFUL).

**SEC-015 note:** No biometric logic was added. The recovery screen is intentionally plain
(username + passphrase only) to leave SEC-015's biometric gate as a clean addition.
