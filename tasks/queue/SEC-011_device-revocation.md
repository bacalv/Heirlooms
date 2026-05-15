---
id: SEC-011
title: Device revocation — allow users to remove old devices from Devices & Access
category: Security
priority: Medium
status: queued
depends_on: []
touches:
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/routes/auth/AuthRoutes.kt
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/service/auth/AuthService.kt
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/repository/auth/AuthRepository.kt
  - HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/auth/DevicesAccessScreen.kt
  - HeirloomsWeb/src/pages/DevicesAccessPage.jsx (or equivalent)
assigned_to: SecurityManager
estimated: half day
---

## Background

Raised during v0.54 staging smoke test (2026-05-15).

When a user sells or disposes of an old device, the device's entry in `wrapped_keys`
persists on the server indefinitely. There is currently no way for a user to revoke a
device from within the app.

## Threat model

For a new device owner to exploit the old hardware they would need simultaneously:

1. **The old device's Keystore private key** — used to unwrap the master key. On a
   proper Android factory reset, Keystore keys are wiped at hardware level and cannot
   be exported. An app uninstall also clears Keystore keys. This makes (1) extremely
   unlikely in normal circumstances.

2. **A valid session token or credentials** — to authenticate with the server and
   fetch the wrapped master key.

Both conditions together are unlikely, but the server holding an unrevoked
`wrapped_keys` entry means the attack surface remains open indefinitely after disposal.
A user has no in-app mechanism to close it.

## What revocation should do

When a user removes a device from Devices & Access:

1. **Delete the `wrapped_keys` row** for that device — the server no longer holds the
   wrapped master key for that device_id. Even if the attacker had the private key,
   there is nothing to decrypt.
2. **Invalidate all sessions** for that device — any stale session tokens associated
   with the device_id become invalid immediately.
3. **Cannot revoke the current device** — the UI must prevent users from removing
   the device they are currently using.

## Scope

### Server

- `DELETE /api/auth/devices/{deviceId}` endpoint — authenticated, scoped to the
  calling user. Deletes the `wrapped_keys` row and invalidates sessions for that
  device_id.
- Return 403 if the user tries to revoke the device associated with their current
  session token.

### Android (Devices & Access screen)

- Add a "Remove device" affordance to each non-current device row.
- On confirmation, call the delete endpoint and refresh the device list.

### Web (Devices & Access page)

- Same — add remove action for non-current devices.

## Acceptance criteria

- User can remove a non-current device from Devices & Access on both Android and web
- Removing a device deletes its `wrapped_keys` row from the server
- Removing a device invalidates all sessions for that device_id
- A user cannot remove their current device
- After removal, the device_id cannot be used to fetch wrapped keys or authenticate

## Security notes

- This does not require master key rotation (the wrapped key is simply deleted; no
  re-encryption of content is needed).
- Users should be advised in the UI to revoke old devices before selling/disposing,
  as a defence-in-depth measure alongside the factory reset.
- Full key rotation (generating a new master key and re-wrapping all DEKs) would be
  a stronger guarantee but is out of scope for this task — track separately if needed.

## Completion notes

<!-- Agent appends here and moves file to tasks/done/ -->
