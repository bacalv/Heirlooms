# Task 3 — HeirloomsiOS scaffold

*Created 2026-05-14.*

---

## What was created

### Design document
- `/docs/briefs/task3_ios_design.md` — full technical design covering architecture,
  screen map, target structure, crypto design (with binary layout of both envelope
  variants matched to `envelope_format.md`), networking API contract, background
  URLSession, Share Extension handoff, Scan 1/2 setup flow, and free-tier sideloading
  instructions.

### Swift package (`HeirloomsiOS/`)

**Package.swift** — Swift 5.9, targets iOS 16+ and macOS 13+ (macOS needed for
`swift test` on a Mac without a connected device). Two targets: `HeirloomsCore`
library and `HeirloomsCoreTests`.

**`HeirloomsCore/Crypto/KeychainManager.swift`**
- `generateSharingKeypair()` — P-256 with Secure Enclave where available
  (`kSecAttrTokenIDSecureEnclave`), soft Keychain fallback otherwise.
- `getSharingPrivateKey()`, `getSharingPublicKeyData()` — returns 65-byte
  uncompressed SEC1 (0x04 || x || y).
- Generic password helpers for session token, plot key (32-byte raw AES key),
  and plot UUID. All items use `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`.

**`HeirloomsCore/Crypto/EnvelopeCrypto.swift`**
- `generateDEK()` → `SymmetricKey` (256-bit, CryptoKit CSPRNG).
- `encryptAESGCM` / `decryptAESGCM` — wraps `AES.GCM` seal/open.
- `wrapSymmetric` / `unwrapSymmetric` — symmetric envelope serialisation
  (`aes256gcm-v1`, `master-aes256gcm-v1`).
- `encryptContent` / `decryptContent` — convenience wrappers for content blobs.
- `wrapDEK` — full `p256-ecdh-hkdf-aes256gcm-v1` asymmetric envelope:
  ephemeral P-256 → ECDH → HKDF-SHA256 (no salt, empty info) → AES-GCM wrap.
- `unwrapDEK(wrappedKey:recipientPrivateKey:SecKey)` — ECDH via Security
  framework (`SecKeyCopyKeyExchangeResult`) so it works with Secure Enclave keys.
- `unwrapDEK(wrappedKey:recipientPrivateKey:P256.KeyAgreement.PrivateKey)` —
  CryptoKit overload for tests.
- Unknown algorithm IDs **fail loudly** with `HeirloomsError.unknownAlgorithmID`.
- Envelope version mismatches throw `HeirloomsError.envelopeVersionMismatch`.

**`HeirloomsCore/Networking/HeirloomsAPI.swift`**
- Async/await URLSession client.
- `initiateUpload` → `POST /api/content/uploads/initiate` (encrypted).
- `confirmUpload` → `POST /api/content/uploads/confirm` (encrypted).
- `listPlotItems` → `GET /api/content/uploads?plot_id=...`.
- `fetchItem` / `fetchThumbnail` → `GET /api/content/uploads/<id>/file|thumb`.
- `register` → `POST /api/auth/register`.
- `joinPlot` → `POST /api/plots/join`.
- `listSharedMemberships` → `GET /api/plots/shared`.
- `getPlotKey` → `GET /api/plots/<id>/plot-key`.
- `initiatePairing` → `POST /api/auth/pairing/initiate`.
- `X-Api-Key` header set from Keychain on every authenticated request.
- Internal `buildRequest(url:method:body:)` is package-internal for test access.

**`HeirloomsCore/Networking/BackgroundUploadManager.swift`**
- `URLSessionConfiguration.background(withIdentifier:)` with
  `sessionSendsLaunchEvents = true`.
- `enqueueUpload(localURL:ticket:isThumbnail:)` — uses `uploadTask(with:fromFile:)`
  for disk streaming (no RAM load of video bytes).
- `handleEventsForBackgroundURLSession(identifier:completionHandler:)` — stores
  handler; fires in `urlSessionDidFinishEvents`.
- `setAllowsCellular(_:)` — persists to `UserDefaults`, recreates session.

**`HeirloomsCore/Models/Models.swift`**
- `PlotItem` — Codable with snake_case key mapping.
- `UploadTicket`, `UserCredentials`, `RegisterResponse`, `UploadListPage`,
  `PlotKeyResponse`, `SharedMembership`.
- `HeirloomsError` — domain error enum with `LocalizedError` conformance.

**App-level stubs** (`HeirloomsApp/` — must be added to Xcode app target manually):
- `App.swift` — `@main`, `AppDelegate` background session hooks, `AppState`
  observable (phase: needsActivation / needsPlotScan / ready), `RootView`.
- `ActivateView.swift` — Scan 1 (friend invite) and Scan 2 (plot invite) flows,
  `QRScannerView` stub (AVFoundation implementation TODO), polling logic for
  membership confirmation.
- `HomeView.swift` — segmented "Shared with you / You shared" grid, `ThumbnailCell`
  with lazy encrypted thumbnail decryption, Plant button with `PhotosPicker`,
  `SettingsView` with "Reset shared album" action, `PairingView` stub,
  `DiagnosticsView` stub.
- `FullScreenMediaView.swift` — `ZoomableImageView` (UIScrollView pinch-to-zoom),
  `VideoPlayer` (AVKit), `ShareSheet`, no delete affordance.

**Tests** (`Tests/HeirloomsCoreTests/`):
- `EnvelopeCryptoTests.swift` — 14 tests covering DEK generation, AES-GCM round
  trip, symmetric envelope round trip and binary layout, asymmetric envelope round
  trip and binary layout, wrong-key throws, tampered-tag throws, unique nonces,
  unknown alg ID throws, version mismatch throws.
- `APIClientTests.swift` — 11 tests covering URL construction (correct base URL
  and path for each endpoint), `X-Api-Key` header presence, `listPlotItems`
  response parsing (items + empty page), HTTP 401/500 error throws,
  `initiateUpload` ticket parsing, missing field throws. Uses a `MockURLProtocol`
  that intercepts URLSession requests without making real network calls.

**`HeirloomsiOS/SETUP.md`** — instructions for:
- Creating the Xcode app target and wiring up HeirloomsCore as a package.
- Signing with a free personal team.
- Installing on iPad (USB + Wifi).
- Trusting the developer certificate on device.
- Re-signing after the 7-day expiry.
- Upgrading to paid Developer Program for TestFlight.
- Share Extension setup.
- Troubleshooting table.

---

## What works (after running `swift test`)

- All `HeirloomsCore` types compile cleanly against macOS 13 / iOS 16 SDKs.
- Crypto round-trip correctness is enforced by the test suite.
- The envelope binary layout is verified in tests to match `envelope_format.md`.
- Mock URLProtocol tests run without a network connection.

---

## What is still TODO

The scaffold is intentionally incomplete in the areas listed below. None of
these is a blocker for bootstrapping the project or running the test suite.

### P1 — required before first real use

| Item | Where | Notes |
|---|---|---|
| QR scanner implementation | `ActivateView.swift` `QRScannerView` | AVFoundation `AVCaptureSession` + `AVMetadataObjectTypeQRCode`. The stub compiles but does nothing. |
| Share Extension | `HeirloomsApp/ShareExtension/` directory doesn't exist yet | Write `ShareViewController.swift`, wire App Group shared container, add `NSExtension` key to `Info.plist`. |
| Confirm-after-background-complete | `HomeView.swift` `uploadSingleItem` | Currently calls confirm synchronously before background upload finishes. Production path: `BackgroundUploadManager` delegate fires confirm after GCS PUT completes. |
| Camera-roll import prompt | Post-Scan-2 flow | One-time bulk import after plot setup — designed in brief but not yet implemented. |
| Diagnostics log | `DiagnosticsView` stub | 28-day local log (Time, Category, Message, Detail) — append-only SQLite or flat file. |
| Pairing code UI | `PairingView` stub | Call `initiatePairing()`, display 8-char code on screen for the web to type. |

### P2 — quality / production-readiness

| Item | Notes |
|---|---|
| Streaming encryption for large files | `HomeView.uploadSingleItem` loads the entire file into RAM via `loadTransferable`. Must be replaced with a chunked stream writer for videos > a few MB. |
| Auth salt / verifier scheme alignment | iOS uses `SHA-256(random_key)` as verifier. Android uses a different derivation. Confirm the server accepts both before pairing iOS ↔ Android accounts. |
| DEK wrap format for uploads | Confirm `master-aes256gcm-v1` is what Android and web write for plot uploads; the server confirm endpoint accepts it. |
| WiFi-only toggle UI | `BackgroundUploadManager.setAllowsCellular` is wired but no Settings toggle is surfaced. Add a `Toggle` to `SettingsView`. |
| Video temp file cleanup | `FullScreenMediaView` writes decrypted video to `/tmp/<id>.mp4` but never deletes it. Delete on view dismissal. |
| Error handling UX | Most error paths print to console. Surface errors as SwiftUI alerts or a dedicated error banner. |
| `userId` persistence | `myUserId` in `HomeView` reads from `UserDefaults`. It should be stored in Keychain at registration and read back from there. |
| Multiple pages for item list | `listPlotItems` fetches one page (50 items). Add cursor-based pagination as a lazy-load on scroll. |
| `takenAt` from PHAsset | Upload confirm sends `takenAt` but `HomeView` does not extract it from `PHAsset`. Use `PHAsset.creationDate`. |

### P3 — explicitly out of v1 (brief)

- Push notifications
- Capsule unlock
- Tag UI
- Multiple shared plots
- Receive-side notifications

---

## Crypto compatibility assurance

The envelope implementation matches `envelope_format.md` at the binary level:

- Asymmetric envelope: `[0x01][27]["p256-ecdh-hkdf-aes256gcm-v1"][ephemeral:65][nonce:12][ciphertext][tag:16]`
- Symmetric envelope: `[0x01][N][alg_id:N][nonce:12][ciphertext][tag:16]`
- HKDF: SHA-256, no salt, empty info, 32-byte output (matches Android + Web)
- Ephemeral key: uncompressed SEC1 (0x04 || x || y), 65 bytes — same as Android
- AES-GCM nonces: CryptoKit random generation, fresh per encryption

Cross-platform round-trip testing (iOS envelope readable by Android/Web) should
be added as an integration test in `HeirloomsTest/` once the first real iOS
activation is performed.
