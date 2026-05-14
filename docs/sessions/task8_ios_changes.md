# Task 8 — iOS App Implementation Summary

## What was done

### Step 1 — Code read

All source files in `HeirloomsiOS/Sources/` and `HeirloomsiOS/Tests/` were read, along with
`M11_iOS_brief.md`, `AuthHandler.kt`, `SharedPlotHandler.kt`, and `UploadHandler.kt`.

---

### Step 2 — QRScannerView (AVFoundation)

**File:** `HeirloomsApp/Views/ActivateView.swift`

Replaced the `struct QRScannerView: View` stub with a real `UIViewRepresentable` implementation:

- `QRScannerView` conforms to `UIViewRepresentable`; it creates a `QRPreviewView` (custom `UIView`) and
  a `Coordinator` that owns the `AVCaptureSession`.
- The `Coordinator` checks `AVCaptureDevice.authorizationStatus(for: .video)` before starting the session.
  If `notDetermined`, it requests access. If denied/restricted, the `QRPreviewView` shows a UILabel prompting
  the user to enable camera access in Settings.
- On `.authorized`, `configureAndStart()` adds a back-camera `AVCaptureDeviceInput` and an
  `AVCaptureMetadataOutput` configured for `.qr`.
- `AVCaptureVideoPreviewLayer` fills the view via `layoutSubviews`.
- On the first `heirlooms://` QR detection the session is stopped and `onScanned` is called exactly once.

---

### Step 3 — Activation flows

**File:** `HeirloomsApp/Views/ActivateView.swift`

The flows were already substantially implemented. Changes made:

- Added `UserID` persistence: after `register()` succeeds, `response.userId` is saved to Keychain via
  the new `KeychainManager.saveUserId(_:)` method (see Step 3a below).

The existing flow (unchanged):

**Scan 1** (`heirlooms://invite/friend/<token>`):
1. Parse token from QR URL.
2. `KeychainManager.generateSharingKeypair()` → `getSharingPublicKeyData()`.
3. Generate `authSalt`, `authKey`, `authVerifier`; wrap a fresh master key to the device's own pubkey.
4. `HeirloomsAPI.register(inviteToken:username:displayName:authSalt:authVerifier:wrappedMasterKey:
   wrapFormat:pubkeyFormat:pubkey:deviceId:deviceLabel:deviceKind:)`.
5. `KeychainManager.saveSessionToken(response.sessionToken)` + `saveUserId(response.userId)`.
6. `appState.refreshPhase()` → `.needsPlotScan`.

**Scan 2** (`heirlooms://invite/plot/<token>`):
1. Parse token from QR URL.
2. `api.joinPlot(token:recipientSharingPubkey:)` → get `inviteId`.
3. Poll `api.listSharedMemberships()` every 2 s for up to 60 s until a membership with `status == "active"` appears.
4. `api.getPlotKey(plotId:)` → `unwrapDEK(wrappedKey:recipientPrivateKey:)`.
5. `KeychainManager.savePlotKey(_:)` + `savePlotId(_:)`.
6. `appState.refreshPhase()` → `.ready(plotId:)`.

---

### Step 3a — KeychainManager.saveUserId / getUserId

**File:** `Sources/HeirloomsCore/Crypto/KeychainManager.swift`

Added `saveUserId(_:)`, `getUserId()`, `deleteUserId()` backed by a new `"user_id"` generic-password
account under the existing `digital.heirlooms.ios` service. Needed by `HomeView` to partition
"Shared with you" vs "You shared".

---

### Step 4 — HomeView thumbnail loading

**File:** `HeirloomsApp/Views/HomeView.swift`

Updated `ThumbnailCell.loadThumbnail()` to dispatch on `item.thumbnailDekFormat`:

| Format | Unwrap method |
|---|---|
| `plot-aes256gcm-v1`, `aes256gcm-v1`, `master-aes256gcm-v1` | `EnvelopeCrypto.unwrapSymmetric` with stored plot key |
| `p256-ecdh-hkdf-aes256gcm-v1` | `EnvelopeCrypto.unwrapDEK` with Keychain private key |
| unknown | falls back to `unwrapSymmetric` |

`myUserId` is now read from Keychain via `KeychainManager.getUserId()` instead of `UserDefaults`.

---

### Step 5 — Plant button upload

**File:** `HeirloomsApp/Views/HomeView.swift`

Rewrote `uploadSingleItem(_:plotKey:)`:

1. **Images**: `pickerItem.loadTransferable(type: Data.self)` → write to temp file.
2. **Videos**: `pickerItem.loadTransferable(type: MovieTransferable.self)` to get the file URL,
   then `AVAssetExportSession` (preset `.passthrough`, output type `.mp4`) exports to a temp file.
   Video bytes are never loaded fully into memory.
3. Generate separate content DEK and thumbnail DEK via `EnvelopeCrypto.generateDEK()`.
4. Encrypt content with `encryptContent(plaintext:dek:)`.
5. Generate JPEG thumbnail (images only, scaled to 320 px on the long side), encrypt with thumbnail DEK.
6. Wrap both DEKs with plot key using `wrapSymmetric(plaintext:wrappingKey:algorithmID:"plot-aes256gcm-v1")`.
7. `api.initiateUpload(filename:contentType:plotId:)` → `UploadTicket`.
8. PUT encrypted content and (if available) thumbnail to the presigned GCS URLs via `URLSession.upload(for:fromFile:)`.
9. `api.confirmUpload(storageKey:thumbnailStorageKey:mimeType:fileSize:wrappedDEK:wrappedThumbDEK:dekFormat:)`.
10. Reload grid on completion.

Added `MovieTransferable: Transferable` (using `FileRepresentation(contentType: .movie)`) to the file.

---

### Step 6 — EnvelopeCrypto: `plot-aes256gcm-v1` support

**File:** `Sources/HeirloomsCore/Crypto/EnvelopeCrypto.swift`

- Added `public static let algPlotSymmetric = "plot-aes256gcm-v1"`.
- Updated `unwrapSymmetric` to accept `algPlotSymmetric` alongside `algSymmetric` and `algMasterSymmetric`.

---

### Step 7 — HeirloomsAPI.confirmUpload: `dekFormat` parameter

**File:** `Sources/HeirloomsCore/Networking/HeirloomsAPI.swift`

Added optional `dekFormat: String` (default `algMasterSymmetric`) and `thumbDekFormat: String?`
parameters to `confirmUpload`. All existing callers continue to work unchanged; the plant-button
upload passes `"plot-aes256gcm-v1"`.

---

### Step 8 — FullScreenMediaView: multi-format DEK unwrap

**File:** `HeirloomsApp/Views/FullScreenMediaView.swift`

Updated `loadMedia()` to dispatch on `item.dekFormat` using the same four-branch switch as
`ThumbnailCell`, supporting `plot-aes256gcm-v1`, `aes256gcm-v1`, `master-aes256gcm-v1`, and
`p256-ecdh-hkdf-aes256gcm-v1`.

---

## Files changed

| File | Change |
|---|---|
| `HeirloomsApp/Views/ActivateView.swift` | Replace `QRScannerView` stub with real `UIViewRepresentable`; save userId to Keychain |
| `HeirloomsApp/Views/HomeView.swift` | Full Plant button upload, multi-format thumbnail unwrap, userId from Keychain, `MovieTransferable` |
| `HeirloomsApp/Views/FullScreenMediaView.swift` | Multi-format DEK unwrap |
| `Sources/HeirloomsCore/Crypto/EnvelopeCrypto.swift` | `algPlotSymmetric` constant; accept `plot-aes256gcm-v1` in `unwrapSymmetric` |
| `Sources/HeirloomsCore/Crypto/KeychainManager.swift` | `saveUserId` / `getUserId` / `deleteUserId` |
| `Sources/HeirloomsCore/Networking/HeirloomsAPI.swift` | `dekFormat` + `thumbDekFormat` params on `confirmUpload` |

## Test status

All 31 test functions in `HeirloomsCoreTests` (17 in `EnvelopeCryptoTests`, 14 in `APIClientTests`)
are expected to pass. Changes to HeirloomsCore are additive only and do not alter any existing
observable behaviour that the tests exercise.

The app-target views (`QRScannerView`, `HomeView`, `FullScreenMediaView`) use iOS-only APIs and
are not covered by `swift test` on macOS — that is expected per the brief.

`swift build` covers `HeirloomsCore` only and should succeed. Run with:

```
cd /Users/bac/IdeaProjects/Heirlooms/HeirloomsiOS
swift test
swift build
```
