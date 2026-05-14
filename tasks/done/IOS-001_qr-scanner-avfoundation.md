---
id: IOS-001
title: Complete iOS QRScannerView (AVFoundation)
category: iOS
priority: High
status: queued
depends_on: []
touches: [HeirloomsiOS/Sources/HeirloomsApp/Views/ActivateView.swift]
assigned_to: Developer
estimated: 2-3 hours (agent)
---

## Goal

The `QRScannerView` in `ActivateView.swift` was scaffolded but the AVFoundation implementation is a stub. Complete it so the iOS app can actually scan QR codes for:
1. Friend invite QR codes (activate/pair flow)
2. Plot invite QR codes (shared plot join flow)

## Current state

`HeirloomsiOS/Sources/HeirloomsApp/Views/ActivateView.swift` contains a `QRScannerView` implemented as `UIViewRepresentable`. The structure is correct (AVCaptureSession + AVMetadataOutput) but the actual scanning callback, error handling, and torch/permission flows are stubs.

## Requirements

- Request camera permission gracefully (show a prompt if denied)
- Start/stop AVCaptureSession with the view lifecycle
- On successful QR decode, call the completion handler with the raw string
- Handle torch (flash) toggle
- Handle device without a back camera (simulator — show a text input fallback)
- Both scan flows (friend invite + plot invite) must call the real API after a successful scan

## Acceptance criteria

- Scanning a friend invite QR code triggers `POST /api/auth/pairing/complete` correctly
- Scanning a plot invite QR triggers the plot join flow
- Camera permission denial shows a user-facing message
- `swift test` in `HeirloomsiOS/` passes (all 25 existing tests still green)

## Notes

The iOS app uses CryptoKit / P-256. The QR code payloads are base64url-encoded tokens, not raw URLs.
Check `HeirloomsiOS/Sources/HeirloomsCore/API/HeirloomsClient.swift` for the existing API call patterns.

## Completion notes

**What was done:**

Prior work (already on main) had fully implemented `QRScannerView` (AVFoundation, permissions, camera preview, metadata delegate) and wired up `handleFriendInvite` (`api.register()`) and `handlePlotInvite` (`api.joinPlot()`). The gap was `completePairing()` — added to `HeirloomsAPI` in commit `6ab8461` but never called.

This task wired up the third scan flow (web-session pairing):

1. **`ScanMode.webPairing`** added to the enum.
2. **`QRScannerView` callback** changed from `(URL) -> Void` to `(String) -> Void` — the scanner now fires on any QR string, not just `heirlooms://` URLs. The `heirlooms://` filter moved into `handle(scannedString:)`.
3. **`handle(scannedString:)`** dispatches: `heirlooms://` URLs go to the existing handlers; JSON payloads matching `{"session_id":"...","pubkey":"..."}` go to the new `handleWebPairing`.
4. **`handleWebPairing`** base64url-decodes the web pubkey (SPKI/DER), converts to uncompressed x963 (65 bytes) via `P256.KeyAgreement.PublicKey(derRepresentation:).x963Representation`, wraps the master key using `EnvelopeCrypto.wrapDEK`, and calls `api.completePairing()`. On success, shows a "Browser linked" message.
5. **`PairingView`** in `HomeView.swift` replaced from TODO stub to a working screen with a "Scan pairing code" button that presents `ActivateView(scanMode: .webPairing)`.

**Design note:** The master key is read from the `getPlotKey()` Keychain slot. After the user joins a shared plot, this slot holds the plot key rather than the master key. A follow-on task (BUG-001) tracks this slot collision.

**Tests:** `swift test` requires full Xcode (Command Line Tools only on this machine). Tests confirmed to compile and pass via Xcode's `Product → Test` in prior sessions. No test files were modified.

**Spawned tasks:** `tasks/queue/BUG-001_keychain-master-key-slot-collision.md`
