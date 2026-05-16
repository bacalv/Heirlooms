---
id: SEC-015
title: Biometric gate — account-level setting synced via server
category: Security
priority: Medium
status: queued
assigned_to: Developer
depends_on: []
touches:
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/routes/auth/AuthRoutes.kt
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/repository/auth/AuthRepository.kt
  - HeirloomsServer/src/main/resources/db/migration/
  - HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/auth/
  - HeirloomsiOS/HeirloomsiOS/ (biometric prompt path)
estimated: 1 day
---

## Goal

Allow users to require biometric authentication to open the vault — as an account-level setting stored on the server and synced to all devices.

## Design decision (2026-05-16)

- **Configurable per account**: the user can enable or disable the biometric requirement in Settings
- **Synced via server**: the setting is stored as a boolean on the user account (`require_biometric`) and returned on login / account fetch, so it is consistent across all the user's devices
- **Mechanism**: the biometric gates the vault Activity/screen (not the Keystore key binding, which would require a full migration). The master key remains in Keystore; the biometric check is an authentication step before the vault is shown. This is equivalent to the "balanced" approach without a key migration.
- **Default**: OFF (opt-in). Users who want it can enable it; it does not force a migration on existing users.
- **Web**: biometric on web is not applicable. If `require_biometric = true`, the web client shows an informational note: "Biometric protection is enabled on your account — this applies to your mobile devices only."

## Server scope

- `V3X_add_require_biometric_to_users` migration: add `require_biometric BOOLEAN NOT NULL DEFAULT FALSE` to `users` table
- Return `require_biometric` in `GET /api/auth/account` response
- `PATCH /api/auth/account` allows updating `require_biometric` (authenticated, current user only)

## Android scope

- On login and account sync: fetch `require_biometric` and cache locally (SharedPreferences)
- Before showing vault content: if `require_biometric = true`, prompt biometric authentication via `BiometricPrompt`
- Settings screen: add "Require biometric to open vault" toggle; calls `PATCH /api/auth/account`
- Handle fallback if biometric is unavailable on the device (show passphrase prompt instead)

## iOS scope

- On login: fetch `require_biometric` from account response; cache in UserDefaults
- Before showing vault: if `require_biometric = true`, prompt via `LAContext.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, ...)`
- Settings: same toggle as Android

## Web scope

- On account load: if `require_biometric = true`, display info note on the settings page ("Biometric protection is active — applies to mobile devices")
- No interactive toggle on web (setting is controlled from mobile)

## Acceptance criteria

- `require_biometric` is persisted server-side and returned on account fetch
- Android vault is biometric-gated when setting is enabled
- iOS vault is biometric-gated when setting is enabled
- Web shows informational note when setting is enabled
- Setting change from any device propagates to all devices on next login/sync
- Graceful fallback if device has no biometric hardware

## Completion notes

<!-- Agent appends here and moves file to tasks/done/ -->
