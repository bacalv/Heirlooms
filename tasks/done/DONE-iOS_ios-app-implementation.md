---
id: DONE-iOS
title: iOS app implementation
category: Feature
completed: 2026-05-14
completed_at: 39a3a3a
version: v0.52.0
---

The iOS implementation proceeded in two phases: a Swift Package scaffold (`HeirloomsiOS/`) created at v0.52.0 with `HeirloomsCore` library stubs, followed by commit `39a3a3a` which converted the scaffold to working code — `KeychainManager` (Secure Enclave P-256), `EnvelopeCrypto` (full `p256-ecdh-hkdf-aes256gcm-v1` + `aes256gcm-v1` support with binary-layout verification), `HeirloomsAPI`, `BackgroundUploadManager`, SwiftUI app stubs (`ActivateView`, `HomeView`, `FullScreenMediaView`), and 25 unit tests covering envelope round-trips and URL construction. `QRScannerView` (IOS-001) and the Share Extension (IOS-002) remained as stubs, queued for follow-on tasks. Sideload-to-iPad instructions are in `HeirloomsiOS/SETUP.md`.
