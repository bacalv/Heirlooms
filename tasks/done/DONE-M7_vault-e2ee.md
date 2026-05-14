---
id: DONE-M7
title: Milestone 7 — Vault E2EE
category: Feature
completed: 2026-05-09
completed_at: 0c4a647
version: v0.26.0–v0.30.0
---

Five increments delivered end-to-end encryption across all three platforms: E1 added the schema foundations and `EnvelopeFormat` (taken_at rename, wrapped_keys/recovery_passphrase/pending_blobs tables), E2 built the full server E2EE API (initiate/confirm/migrate, keys endpoints, device-link flow), E3 implemented Android client encryption with `VaultCrypto.kt` + `DeviceKeyManager.kt` + `VaultSession.kt`, E4 added WebCrypto encryption in the browser (vaultCrypto.js, vaultSession.js, deviceKeyManager.js, GardenPage Plant button), and E5 renamed the storage class from `legacy_plaintext` to `public`, added web EXIF extraction, and polished onboarding copy. A key design decision: the envelope binary format is byte-for-byte compatible across server, Android, and web, verified by unit tests on all three platforms.
