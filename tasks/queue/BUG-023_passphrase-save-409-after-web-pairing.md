---
id: BUG-023
title: Passphrase save fails 409 after web pairing — POST /api/keys/devices conflict
category: Bug Fix
priority: High
status: queued
depends_on: []
touches:
  - HeirloomsWeb/src/pages/VaultUnlockPage.jsx
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/routes/keys/KeysRoutes.kt
assigned_to: Developer
estimated: 2–3 hours
---

## Problem

When a user registers on web via an invite link and then attempts to set up a vault
passphrase, the save fails with "Something went wrong. Please try again." The server
returns 409 on `POST /api/keys/devices`.

The device key entry was already created during registration. The passphrase setup
flow calls `POST /api/keys/devices` again, hitting the unique constraint.

Found during TST-010 (2026-05-16) with user DAVE_2_055. After the 409, the vault
cannot be re-unlocked after a page refresh because no passphrase backup exists.

## Impact

Any web-only user who tries to set up a passphrase backup is permanently blocked.
Vault becomes inaccessible after any session restart.

## Fix

The passphrase setup flow should call `PUT /api/keys/passphrase` (or equivalent) to
store only the passphrase-wrapped backup key, not re-register the device. Investigate
whether the web flow is calling the wrong endpoint or whether a dedicated passphrase
endpoint is needed.

## Acceptance criteria

- Web user can set a passphrase after registration without 409
- Vault unlocks correctly after page refresh using the passphrase
- Existing device entry in wrapped_keys is not duplicated
