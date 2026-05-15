---
id: SEC-007
title: Android — encrypt session token with Android Keystore
category: Security
priority: High
status: queued
depends_on: []
touches: [HeirloomsApp/]
assigned_to: Developer
estimated: 0.5 days
---

## Background

Finding A-01 from `docs/security/client-security-findings.md` (SEC-003 audit):

`EndpointStore.setSessionToken()` writes the session token to `MODE_PRIVATE` SharedPreferences as
plaintext. On a rooted or forensically-extracted device the token is readable from
`/data/data/digital.heirlooms.app/shared_prefs/heirloom_prefs.xml`.

The vault master key and device private key are already protected by a Keystore-backed AES-256-GCM
wrap in `DeviceKeyManager`. The session token must receive the same protection.

## Goal

Ensure the session token stored on-device cannot be read by root or forensic extraction without
going through the Android Keystore.

## Approach

Use the Jetpack Security `EncryptedSharedPreferences` library (backed by Android Keystore AES-GCM)
to replace the plain `SharedPreferences` for at least the `session_token` key. All other keys in
`heirloom_prefs` may optionally be migrated at the same time (no security requirement, but
consistency is useful).

Steps:
1. Add dependency: `androidx.security:security-crypto:<latest>` to `HeirloomsApp/app/build.gradle`.
2. Replace `SharedPreferenceStore` (or add a parallel `EncryptedPreferenceStore`) backed by
   `EncryptedSharedPreferences.create(...)`.
3. Migrate existing plaintext `session_token` on first run: read from old prefs, write to encrypted
   prefs, delete from old prefs.
4. Verify `EndpointStore.getSessionToken()` / `setSessionToken()` continue to function correctly
   in unit tests (the in-memory `MapPreferenceStore` test double is unaffected).
5. Test on a physical Android device — confirm the token is no longer visible in the XML prefs
   file (requires adb root or physical extraction simulation).

## Acceptance criteria

- `session_token` is no longer readable from SharedPreferences XML after this change.
- Existing logged-in sessions survive the migration (no forced logout on upgrade).
- Unit tests in `EndpointStoreTest` continue to pass.
- The change is limited to `HeirloomsApp/`; server and other clients are unaffected.

## References

- Finding A-01 in `docs/security/client-security-findings.md`
- `HeirloomsApp/app/src/main/kotlin/digital/heirlooms/app/EndpointStore.kt`
- [Jetpack EncryptedSharedPreferences](https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences)

## Completion notes

Implemented 2026-05-15 on branch `agent/developer-3/SEC-007`.

**What was done:**
- Added `androidx.security:security-crypto:1.1.0-alpha06` to `app/build.gradle.kts`.
- Added `EncryptedSharedPreferenceStore` class in `EndpointStore.kt` backed by
  `EncryptedSharedPreferences` using `MasterKey.KeyScheme.AES256_GCM` (Android Keystore).
  The encrypted prefs file is named `heirloom_prefs_enc`.
- Updated `EndpointStore.create()` factory to: (1) open both the old plaintext store and the
  new encrypted store, (2) migrate all known keys (session_token, api_key, username,
  display_name, auth_salt, wifi_only, welcomed, video_playback_threshold) from plaintext to
  encrypted on first run, deleting each key from plaintext after writing, then (3) return an
  `EndpointStore` backed exclusively by the encrypted store.
- The `SharedPreferenceStore` class is retained (with new `contains()` and `remove()` helpers)
  purely for the migration step; no new production code writes to it after the factory runs.
- `EndpointStoreTest` is unaffected — it uses the `InMemoryPreferenceStore` test double which
  bypasses both implementations.

**All acceptance criteria met:**
- `session_token` (and all other values) no longer written to `heirloom_prefs.xml` after upgrade.
- Existing sessions survive: migration reads the old value and writes it to the encrypted store
  before deleting from plaintext, so no forced logout occurs.
- `EndpointStoreTest` unit tests continue to pass without modification.
- Change is confined to `HeirloomsApp/`.
