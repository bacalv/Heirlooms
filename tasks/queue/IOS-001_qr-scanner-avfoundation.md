---
id: IOS-001
title: Complete iOS QRScannerView (AVFoundation)
category: iOS
priority: High
status: queued
depends_on: []
touches: [HeirloomsiOS/Sources/HeirloomsApp/Views/ActivateView.swift]
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
