---
id: BUG-007
title: Web app never generates sharing key — web-only users can't be invited to shared plots
category: Bug Fix
priority: High
status: done
depends_on: []
touches:
  - HeirloomsWeb/src/pages/VaultUnlockPage.jsx
  - HeirloomsWeb/src/App.jsx
  - HeirloomsWeb/src/api.js
  - HeirloomsWeb/src/crypto/vaultCrypto.js
assigned_to: Developer
estimated: 2–3 hours
---

## Background

Discovered during TST-003 (2026-05-15). User B registered and logged in only via the
web app. When User A (Android) tried to invite User B to a shared plot, the app showed
"Bret hasn't set up sharing" — because User B has no sharing key on the server.

## Root cause

Android's `GardenViewModel.ensureSharingKey()` auto-generates an E2EE sharing keypair
and uploads it to `/api/keys/sharing` if none exists. The web app's `loadSharingKey()`
in `App.jsx` only reads an existing key — if the server returns 404, it silently returns.
`VaultUnlockPage.jsx` does the same. There is no `putSharingKey` call anywhere in the
web codebase.

Web-only users therefore never get a sharing key, and cannot be invited to shared plots
by anyone.

## Fix

In `VaultUnlockPage.jsx` (where the master key is available after unlock), after calling
`loadSharingKey`:

1. If the key was loaded successfully — done, existing path is fine.
2. If no key exists (404 / `setSharingPrivkey` was not called) — generate a new keypair
   and upload it:

```js
// Generate keypair (reuse existing vaultCrypto helpers)
const { privkeyPkcs8, pubkeySpki } = await generateSharingKeypair()
const wrappedPrivkey = await wrapDekUnderMasterKey(privkeyPkcs8, masterKey)
await putSharingKey(token, toB64(pubkeySpki), toB64(wrappedPrivkey))
const cryptoKey = await importSharingPrivkey(privkeyPkcs8)
setSharingPrivkey(cryptoKey)
```

Add `generateSharingKeypair`, `wrapDekUnderMasterKey`, and `putSharingKey` to
`vaultCrypto.js` and `api.js` respectively if not already present. Mirror the Android
implementation in `VaultCrypto.kt` and `GardenViewModel.ensureSharingKey()`.

## Acceptance criteria

- A user who registers and logs in only via the web has a sharing key uploaded after
  their first vault unlock
- Another user can successfully invite them to a shared plot without seeing "hasn't set
  up sharing"
- Existing users who already have a sharing key are unaffected (key is not regenerated)
- Verified end-to-end on staging: web-only User B can be invited to and join a shared plot

## Completion notes

Completed 2026-05-15 by developer-3 on branch `agent/developer-3/BUG-007`.

### Changes made

**`HeirloomsWeb/src/crypto/vaultCrypto.js`**
- Added `generateSharingKeypair()` — generates a P-256 ECDH keypair using `crypto.subtle.generateKey`, exports `privkeyPkcs8` (PKCS#8) and `pubkeySpki` (SPKI) as `Uint8Array`. Mirrors `VaultCrypto.generateSharingKeypair()` on Android.

**`HeirloomsWeb/src/api.js`**
- Added `putSharingKey(apiKey, pubkeyB64, wrappedPrivkeyB64, wrapFormat)` — `PUT /api/keys/sharing` with the keypair payload. Mirrors `api.putSharingKey()` on Android.

**`HeirloomsWeb/src/pages/VaultUnlockPage.jsx`**
- Added `ensureSharingKey(apiKey, masterKey)` helper: checks `vaultSession.getSharingPrivkey()` — if null, generates keypair, wraps private key under master key (`wrapDekUnderMasterKey`), uploads via `putSharingKey`, imports key into session. Best-effort (swallows errors; retries on next unlock).
- `handleUnlock`: now calls `ensureSharingKey` after `loadSharingKey` — covers returning users on existing browsers (Cases A and B).
- `handleSetup`: now calls `ensureSharingKey` after `vaultSession.unlock` — covers brand-new account creation (Case C), where there is no existing key to load.

**`HeirloomsWeb/src/App.jsx`**
- Added `ensureSharingKey` helper (same logic, same imports).
- Session-restore `useEffect`: calls `ensureSharingKey` after `loadSharingKey` — covers page-refresh auto-unlock via IDB pairing material.
- `handleLogin`: chains `ensureSharingKey` after `loadSharingKey` — covers the `LoginPage` and `JoinPage` paths that provide a masterKey directly.

### Design decisions
- `ensureSharingKey` is a no-op if the key is already in `vaultSession` (loaded by `loadSharingKey`), so existing users are never affected.
- Errors in generation/upload are silently swallowed (best-effort), consistent with the Android approach and the existing `loadSharingKey` error handling.
- The private key is wrapped using `ALG_MASTER_AES256GCM_V1` (same symmetric wrap used for DEKs), matching the Android implementation.
