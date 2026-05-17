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

## Completion notes

**Root cause:** `VaultUnlockPage.handleSetup` called `POST /api/keys/devices`
unconditionally. For a user who registered via the web invite flow (`JoinPage`),
the device was already registered on the server by `authRegister` (the registration
body includes device_id, pubkey, wrapped_master_key). The passphrase PUT succeeded
but the subsequent 409 from `registerDevice` threw an error, the catch block showed
"Something went wrong", and `onUnlocked()` was never called. The passphrase backup
was saved on the server but inaccessible until the user successfully went through
the unlock path.

The same 409 would occur after a QR pairing flow (`PairPage`): the server's
`wrapLink` endpoint registers the new device, but `PairPage` never called
`markVaultSetUp()`, so `isVaultSetUp()` returned false.

**Fix applied (two files, no server changes needed):**

1. `HeirloomsWeb/src/pages/VaultUnlockPage.jsx` — Wrapped the `generateAndStoreKeypair`
   / `registerDevice` / `markVaultSetUp` block in `handleSetup` with a guard:
   `if (!deviceKeyManager.isVaultSetUp()) { ... }`. If the device is already
   registered, the block is skipped and the passphrase upload path completes
   successfully.

2. `HeirloomsWeb/src/pages/PairPage.jsx` — Added `markVaultSetUp()` call after
   `savePairingMaterial` succeeds in the polling handler, so that the
   `isVaultSetUp()` flag is correctly set for users who arrive at the passphrase
   setup screen after a QR pairing.

**Server-side change:** Not required. `KeysRoutes.kt` was listed as a possible
touch in the task spec, but the fix was entirely in the web client. The existing
409 behaviour of `POST /api/keys/devices` is correct and intentional.

**Tests added:** `HeirloomsWeb/src/test/vaultUnlock.test.jsx` — three new tests:
- `handleSetup` does NOT call `POST /api/keys/devices` when `isVaultSetUp=true`
- `handleSetup` DOES call `POST /api/keys/devices` when `isVaultSetUp=false`
- `PairPage` calls `markVaultSetUp()` after a successful pairing

All three tests pass. Two pre-existing failures in `auth.test.jsx` (AccessPage
invite URL display and IDB mount/reload) are unrelated to this task.

**Commit:** `0e8432c` on branch `agent/developer-12/BUG-023`

**Spawned tasks:** None.
