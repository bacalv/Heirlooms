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

Completed 2026-05-16 by SecurityManager on branch `agent/security/SEC-011`.

### What was done

All three layers were already partially implemented on the branch. I audited each layer,
filled in the remaining gaps, and wired them together end-to-end.

**Server (fully implemented — verified)**
- `DELETE /api/auth/devices/{deviceId}` route in `AuthRoutes.kt` — authenticated, scoped to
  the calling user, returns 403 when the caller tries to revoke their own current device.
- `AuthService.revokeDevice()` — deletes the `wrapped_keys` row and invalidates all sessions
  for the revoked device's `device_kind`.
- `AuthRepository.deleteSessionsByDeviceKind()` — hard-deletes sessions by userId + deviceKind.
- `KeyRepository.deleteWrappedKeyByDeviceId()` + `getWrappedKeyByDeviceIdForUser()` — scoped
  deletes to prevent cross-user revocation.
- `DeviceRevocationServiceTest` — 5 unit tests covering NotFound, Forbidden (sole device),
  Success (second device), cross-kind revocation, and unresolvable-session guard.
- Added `isCurrent: Boolean` to `WrappedKeyResponse` and `listDevicesRoute` — the server
  now stamps `isCurrent=true` on the device that matches the calling session's `device_kind`
  when it is the only active device of that kind. This lets both clients hide the Remove
  button without a separate round-trip.

**Android (`DevicesAccessScreen.kt`, `HeirloomsApi.kt`)**
- `DeviceRecord` extended with `isCurrent: Boolean = false`; `listDevices()` parses it via
  `optBoolean("isCurrent", false)`.
- `DevicesAccessScreen` already had `currentDeviceId`-based guard; updated to also check
  `device.isCurrent` so it works even if `currentDeviceId` is not provided.
- Remove button suppressed for current device; 403 error case still handled gracefully.

**Web (`AccessPage.jsx`)**
- Added `(this device)` label suffix for `device.isCurrent` entries.
- Remove button is only rendered when `!device.isCurrent` — current device row shows label
  only, no affordance to remove.
- Error handler for 403 kept as defence-in-depth.

### Tests run
- `HeirloomsServer: ./gradlew test --no-daemon` — BUILD SUCCESSFUL
- `HeirloomsApp: ./gradlew :app:testProdDebugUnitTest --no-daemon` — BUILD SUCCESSFUL

### Security posture
- Server enforces revocation scope: only the authenticated user's own devices are accessible.
- Self-revocation blocked at both server (403) and UI (button hidden).
- Revocation is hard-delete: wrapped key is permanently removed; even if the old device
  hardware and Keystore key were intact, the server holds nothing to decrypt.
- Session invalidation covers all sessions for the revoked device_kind (best granularity
  available without a device_id FK on user_sessions).
