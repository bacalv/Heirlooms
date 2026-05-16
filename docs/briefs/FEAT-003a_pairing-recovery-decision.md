# FEAT-003a — Android Account Pairing/Recovery: Design Spike Decision

*Author: Developer-4. Date: 2026-05-16. Status: decision — unblocks FEAT-003b.*

---

## Summary

This document answers the four open design questions from FEAT-003a and recommends
the implementation approach for FEAT-003b. The short answer: implement **Option C
(server-assisted passphrase recovery)**, which reuses existing endpoints without any
new server code and is fully consistent with M11.

---

## Q1: Which recovery flow is consistent with the existing pairing mechanism and M11 social recovery design?

### Existing pairing mechanism (Android → web direction)

The current pairing flow is Android-initiated, web-receiving:

1. Android calls `POST /api/auth/pairing/initiate` (authenticated) →
   server returns a short-lived numeric code
   (`AuthRoutes.kt:pairingInitiateRoute`, line 305).
2. Web enters the code at `/pair` (`PairPage.jsx`), calls
   `POST /api/pairing/qr` with the code → server returns `session_id`.
3. Web generates an ephemeral P-256 keypair and encodes `{session_id, pubkey}` as
   a QR code displayed to the user.
4. Android scans the QR code, wraps the master key to the web's ephemeral pubkey
   using ECDH, and calls `POST /api/auth/pairing/complete`
   (`AuthRoutes.kt:pairingCompleteRoute`, line 346).
5. Web polls `GET /api/auth/pairing/status?session_id=...` until `state=complete`,
   then unwraps the master key with its ephemeral private key.

There is also a **device link flow** in `KeysRoutes.kt` (`/link/initiate`,
`/link/{id}/register`, `/link/{id}/status`, `/link/{id}/wrap`) which handles
Android-to-Android device linking — a trusted Android device wraps the master key
for a new Android device.

### Option A: Passphrase-based (derive on new device)

The user enters their passphrase on the fresh Android install. The app:
1. Calls `POST /api/auth/challenge` → gets `auth_salt`.
2. Derives `authKey || masterKeySeed` via Argon2id(passphrase, auth_salt, 64 bytes).
3. Calls `POST /api/auth/login` with `authKey` → gets session token.
4. Calls `GET /api/keys/passphrase` → gets the passphrase-wrapped master key blob
   (envelope, salt, argon2Params).
5. Calls `VaultCrypto.unwrapMasterKeyWithPassphrase(envelope, passphrase, salt, params)`
   to recover the master key locally — no server-side decryption.
6. Registers the new device via `POST /api/keys/devices` (wrapped master key to
   its new Keystore pubkey).

This is **Option C from the task file** (server-assisted re-wrap). The "passphrase
recovery" option is not a separate re-wrapping option — it IS the
passphrase-based re-wrap: the user authenticates with the passphrase (step 2/3),
fetches the server-held `argon2id-aes256gcm-v1` blob (step 4), and unwraps it on
the client using the same passphrase (step 5). The server at no point learns the
master key.

### Option B: QR-code device-pairing (both devices present)

The existing web-pairing flow (`pairing/initiate`, `pairing/qr`, `pairing/complete`)
already supports an Android-to-web pairing. An Android-to-Android variant would
need the device link flow (`/link/*` endpoints in `KeysRoutes.kt`) — this is already
server-supported. However, this requires two devices to be simultaneously available.

### Option C: Server-assisted re-wrap (recommended)

This is the passphrase-based path described in Option A above, named "Option C" in
the task file. No server-side re-wrapping occurs — the server holds an opaque blob
and the client derives the key independently.

### M11 social recovery compatibility

ARCH-003 §7 defines Shamir shares stored in `executor_shares` (a `NULL capsule_id`
row means vault master-key recovery, not capsule-key recovery). This is an M13 flow
(death-verification-gated collection). FEAT-003b is M11 scope and must not depend on
this path.

### Recommendation

**Implement Option C (passphrase-based recovery) as the primary path in FEAT-003b.**
This requires:
- The user to have previously saved a passphrase backup (via `PUT /api/keys/passphrase`).
- A "Recover with passphrase" button on the Android login/registration screen.
- Matching Argon2id parameters on Android and server (confirmed below in Q2).

Option B (device link) should be offered as a secondary path for users who have
another trusted device available and no passphrase configured. The server already
supports it via `KeysRoutes.kt` — no new endpoints needed. However, it is lower
priority for FEAT-003b because Option C covers the primary reported scenario (Android-
only user with no web client).

---

## Q2: Argon2id parameter parity — Android vs. web

### Android parameters

Defined in `VaultCrypto.kt` (`data class Argon2Params`):

```kotlin
// HeirloomsApp/app/src/main/kotlin/digital/heirlooms/crypto/VaultCrypto.kt:131
data class Argon2Params(val m: Int = 65536, val t: Int = 3, val p: Int = 1)
```

Used in `deriveArgon2id()` (line 160) and `deriveAuthAndMasterKeys()` (line 256):

- Master key wrapping (`wrapMasterKeyWithPassphrase`): **m=65536, t=3, p=1, outputLen=32**
  (passphrase → KEK wrapping the master key blob).
- Auth key derivation (`deriveAuthAndMasterKeys`): **m=65536, t=3, p=1, outputLen=64**
  (passphrase → 32-byte authKey || 32-byte masterKeySeed, the masterKeySeed path is
  NOT used for recovery — only authKey is sent to the server at login).

### Web parameters

Defined in two places:

```javascript
// HeirloomsWeb/src/crypto/VaultCrypto.js:132
export const DEFAULT_ARGON2_PARAMS = { m: 65536, t: 3, p: 1 }
```

```javascript
// HeirloomsWeb/src/pages/LoginPage.jsx:10
const ARGON2_PARAMS = { m: 65536, t: 3, p: 1 }
```

The web login uses `dkLen: 64` (line 55 in `LoginPage.jsx`), the same layout as
Android: first 32 bytes = authKey, last 32 bytes = masterKeySeed.

### Conclusion: parameters match exactly

| Parameter | Android | Web |
|---|---|---|
| Memory (m) | 65536 KiB | 65536 KiB |
| Iterations (t) | 3 | 3 |
| Parallelism (p) | 1 | 1 |
| Output length (auth) | 64 bytes | 64 bytes |
| Output length (passphrase wrap) | 32 bytes | 32 bytes |

The parameters are **identical**. A fresh Android install can derive the same KEK
from the same passphrase and salt as the web did when storing the backup, so it can
unwrap the `argon2id-aes256gcm-v1` blob fetched from `GET /api/keys/passphrase`
without any server round-trip for key material.

The params are also stored on the server alongside the blob (`argon2Params` field in
the `getPassphrase()` response — `HeirloomsApi.kt:716`), so FEAT-003b should always
read parameters from the server response rather than hardcoding, future-proofing for
any parameter rotation.

---

## Q3: Server endpoint impact — do any options require new endpoints?

### Option C (passphrase recovery — recommended)

Uses only existing endpoints, all of which are implemented and deployed:

| Endpoint | Purpose | Status |
|---|---|---|
| `POST /api/auth/challenge` | Get auth_salt for username | Exists (`AuthRoutes.kt:challengeRoute`) |
| `POST /api/auth/login` | Authenticate, get session token | Exists (`AuthRoutes.kt:loginRoute`) |
| `GET /api/keys/passphrase` | Fetch passphrase-wrapped master key | Exists (`KeysRoutes.kt:getPassphraseRoute`) |
| `POST /api/keys/devices` | Register new device with wrapped master key | Exists (`KeysRoutes.kt:registerDeviceRoute`) |

No new server endpoints are required for Option C.

The Android API client already has all these methods: `authChallenge()`,
`authLogin()`, `getPassphrase()`, `registerDevice()` — see
`HeirloomsApi.kt` lines 579, 593, 705, 731.

### Option B (device link — secondary path)

Uses the existing link flow:

| Endpoint | Purpose | Status |
|---|---|---|
| `POST /api/keys/link/initiate` | Trusted device starts link session | Exists (`KeysRoutes.kt:initiateLinkRoute`) |
| `POST /api/keys/link/{id}/register` | New device submits pubkey | Exists (`KeysRoutes.kt:registerLinkRoute`) |
| `GET /api/keys/link/{id}/status` | Poll for completion | Exists (`KeysRoutes.kt:linkStatusRoute`) |
| `POST /api/keys/link/{id}/wrap` | Trusted device posts wrapped key | Exists (`KeysRoutes.kt:wrapLinkRoute`) |

No new server endpoints are required for Option B either.

### Conclusion

**Neither recovery option requires new server endpoints.** FEAT-003b is purely a
client-side (Android) task. The server is already capable.

---

## Q4: Must FEAT-003b wait for ARCH-010?

### What ARCH-010 covers

ARCH-010 is the M11 API surface and migration sequencing brief. Its scope
(from `tasks/queue/ARCH-010_m11-api-surface-and-migration-sequencing.md`):

- New M11 server endpoints in implementation-dependency order (connections,
  executor nominations, capsule sealing with ECDH/tlock/Shamir).
- V31 (connections) and V32 (capsule crypto) migration ordering.
- Merged sealing validation sequence superseding ARCH-003 + ARCH-006.

### Orthogonality to FEAT-003b

The recovery flow (Option C, passphrase-based) uses only pre-existing auth and key
management endpoints:
- `POST /api/auth/challenge` — M8.
- `POST /api/auth/login` — M8.
- `GET /api/keys/passphrase` — post-M7 (passphrase backup added in the M7 E2EE work).
- `POST /api/keys/devices` — M8.

None of these endpoints are touched by ARCH-010. The M11 work adds new capsule
crypto endpoints and the connections model — neither overlaps with the auth/passphrase
path used for device recovery.

There is no data model dependency: FEAT-003b writes to `wrapped_keys` and reads from
`passphrase_keys` (existing tables). M11 adds `connections` (V31) and capsule columns
(V32). These are entirely separate schema objects.

### Conclusion: FEAT-003b does NOT need to wait for ARCH-010

FEAT-003b can proceed independently. There is zero code or schema overlap between
the device recovery feature and the M11 capsule crypto surface. Blocking FEAT-003b
on ARCH-010 would add unnecessary delay for a feature users need now.

---

## Recommendation for FEAT-003b

**Implement Option C (passphrase recovery) as the primary flow, Option B (device link) as secondary.**

### Primary: Passphrase recovery screen on Android

1. Add a "Recover with passphrase" route/screen to the Android authentication flow
   (e.g. a secondary button below "Create account" on the registration screen,
   or a link on the login screen adjacent to the one for users who already have an account).
2. The screen collects username and passphrase.
3. Recovery sequence:
   a. Call `api.authChallenge(username)` → `authSaltB64url`.
   b. Derive 64-byte Argon2id output: `VaultCrypto.deriveAuthAndMasterKeys(passphrase, salt)`.
   c. Call `api.authLogin(username, authKeyB64url)` → session token. Display error on
      401 ("Wrong username or passphrase").
   d. Call `api.getPassphrase()`. If null (404), show error: "No passphrase backup found.
      Use a paired device to recover." Do not proceed.
   e. Unwrap: `VaultCrypto.unwrapMasterKeyWithPassphrase(backup.wrappedMasterKey,
      passphrase, backup.salt, backup.params)`.
   f. Generate a new device keypair and wrap the recovered master key to it.
   g. Call `api.registerDevice(...)` to register the new device.
   h. Store the session token and proceed to the main app.

4. The `getPassphrase()` response provides the Argon2id params from the server — always
   use these (do not hardcode). This ensures forward compatibility if params change.

### Secondary: Device link (existing Android device as trusted source)

Surface the existing link flow (`/api/keys/link/*`) on a "Pair with another device"
screen for users who have another Android device available. This flow is already
server-supported; FEAT-003b only needs to build the UI.

### What FEAT-003b does not need to do

- Add any server endpoints.
- Wait for ARCH-010.
- Integrate with M11 social recovery (Shamir shares, executor nominations) — that is M13.

---

## File references

| Finding | File | Lines |
|---|---|---|
| Android Argon2id params (default) | `HeirloomsApp/app/src/main/kotlin/digital/heirlooms/crypto/VaultCrypto.kt` | 131 |
| Android auth+master key derivation | `HeirloomsApp/app/src/main/kotlin/digital/heirlooms/crypto/VaultCrypto.kt` | 256–279 |
| Android passphrase wrap/unwrap | `HeirloomsApp/app/src/main/kotlin/digital/heirlooms/crypto/VaultCrypto.kt` | 139–175 |
| Android getPassphrase() API call | `HeirloomsApp/app/src/main/kotlin/digital/heirlooms/api/HeirloomsApi.kt` | 705–729 |
| Android putPassphrase() API call | `HeirloomsApp/app/src/main/kotlin/digital/heirlooms/api/HeirloomsApi.kt` | 741–749 |
| Android pairingInitiate() API call | `HeirloomsApp/app/src/main/kotlin/digital/heirlooms/api/HeirloomsApi.kt` | 635–638 |
| Android pairingComplete() API call | `HeirloomsApp/app/src/main/kotlin/digital/heirlooms/api/HeirloomsApi.kt` | 671–678 |
| Web Argon2id params (default) | `HeirloomsWeb/src/crypto/VaultCrypto.js` | 132 |
| Web Argon2id params (login) | `HeirloomsWeb/src/pages/LoginPage.jsx` | 10 |
| Web passphrase wrap/unwrap | `HeirloomsWeb/src/crypto/VaultCrypto.js` | 134–154 |
| Web QR pairing screen (existing) | `HeirloomsWeb/src/pages/PairPage.jsx` | 1–135 |
| Server auth routes (pairing initiate/complete/status) | `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/routes/auth/AuthRoutes.kt` | 304–404 |
| Server passphrase GET/PUT/DELETE | `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/routes/keys/KeysRoutes.kt` | 147–199 |
| Server device link flow | `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/routes/keys/KeysRoutes.kt` | 203–326 |
| ARCH-010 scope | `tasks/queue/ARCH-010_m11-api-surface-and-migration-sequencing.md` | — |
