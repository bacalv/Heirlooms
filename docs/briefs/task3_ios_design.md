# HeirloomsiOS — Technical Design Document

*Generated 2026-05-14. Covers architecture, screen map, crypto, networking, and free-tier sideloading.*

---

## 1. Architecture overview

```
┌─────────────────────────────────────────────────────────┐
│                    HeirloomsApp (SwiftUI)                │
│  ActivateView  HomeView  FullScreenMediaView  Settings  │
└────────────────────────┬────────────────────────────────┘
                         │ imports
┌────────────────────────▼────────────────────────────────┐
│                    HeirloomsCore (Swift library)         │
│  Crypto/          Networking/           Models/          │
│  KeychainManager  HeirloomsAPI          PlotItem         │
│  EnvelopeCrypto   BackgroundUploadMgr   UploadTicket     │
│                                         UserCredentials  │
└─────────────────────────────────────────────────────────┘
         │ Keychain                │ URLSession (background)
         ▼                         ▼
    iOS Keychain              api.heirlooms.digital
    (Secure Enclave)          HTTPS / X-Api-Key

┌────────────────────────────────────────────────────────┐
│              ShareExtension (separate target)          │
│  Picks items → writes to App Group container           │
│  Signals main app via UserDefaults(suiteName:)         │
└────────────────────────────────────────────────────────┘
```

### Key technology choices

| Concern | Choice | Rationale |
|---|---|---|
| UI | SwiftUI | Declared in brief |
| Crypto | CryptoKit (AES.GCM, HKDF, P256) | First-party, Secure Enclave integration |
| Keychain | Security framework (`SecKey`, `SecItem`) | Required for Secure Enclave |
| Networking | URLSession async/await + background session | Background upload survives app kill |
| QR scanning | AVFoundation (`AVCaptureSession`) | No third-party dependency |
| Photo picker | PhotosUI (`PHPickerViewController`) | System picker, no permissions needed |

---

## 2. Screen map

```
Cold start
    │
    ├─► [Activate screen]          ← app has no session token
    │       "Welcome to Heirlooms"
    │       [Activate] button
    │           │
    │           ▼
    │       [QR scanner — Scan 1: friend invite]
    │           Reads: heirlooms://invite/friend/<token>
    │           On success → register user, generate keypair, store token
    │           │
    │           ▼
    │       [QR scanner — Scan 2: plot invite]
    │           Reads: heirlooms://invite/plot/<token>
    │           On success → fetch + unwrap plot key, store plotId
    │           │
    │           ▼
    │       [Optional: camera-roll import prompt]
    │           Shows count, honesty copy, skippable
    │
    └─► [Home screen]               ← session + plotId both stored
            Two tab segments:
            "Shared with you"  /  "You shared"
            Grid of thumbnails (lazy load)
            [Plant] FAB → PhotosPicker → encrypt → upload
            │
            ├─► [Full-screen media view]
            │       Photo: pinch-to-zoom
            │       Video: AVPlayer, auto-play
            │       iOS share sheet: Save to Photos, share out
            │       No delete affordance
            │
            └─► [Settings sheet]
                    App version
                    API key reset (entry only — behaviour TBD)
                    [Devices & Access] → pairing code generation
                    [Diagnostics] → local 28-day log table
                    [Reset shared plot] → clears plotId, returns to Scan 2 state
```

---

## 3. Swift target / module structure

### Package.swift targets

```
HeirloomsiOS/
├── Package.swift
├── Sources/
│   └── HeirloomsCore/
│       ├── Crypto/
│       │   ├── KeychainManager.swift
│       │   └── EnvelopeCrypto.swift
│       ├── Networking/
│       │   ├── HeirloomsAPI.swift
│       │   └── BackgroundUploadManager.swift
│       └── Models/
│           └── Models.swift
└── Tests/
    └── HeirloomsCoreTests/
        ├── EnvelopeCryptoTests.swift
        └── APIClientTests.swift
```

### App-level files (outside the Swift package — added to Xcode target manually)

```
HeirloomsApp/
├── App.swift                          ← @main, AppDelegate hooks
├── Views/
│   ├── ActivateView.swift
│   ├── HomeView.swift
│   └── FullScreenMediaView.swift
└── ShareExtension/                    ← separate Xcode target
    └── ShareViewController.swift
```

### Target summary

| Target | Type | Purpose |
|---|---|---|
| `HeirloomsCore` | Swift Package library | All testable logic: crypto, networking, models |
| `HeirloomsCoreTests` | Swift Package test | Unit + integration tests for HeirloomsCore |
| `HeirloomsApp` | Xcode app target | SwiftUI host app; imports HeirloomsCore |
| `HeirloomsShareExtension` | Xcode extension target | Share sheet; writes to App Group, signals main app |

---

## 4. Crypto design

### 4.1 P-256 keypair — generation and Keychain storage

The sharing keypair is generated once at activation and never leaves the device (except via the registration API call for the public key half).

**Secure Enclave path** (A-series and M-series chips, iPhone/iPad with biometric):

```swift
var error: Unmanaged<CFError>?
let accessControl = SecAccessControlCreateWithFlags(
    nil,
    kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
    .privateKeyUsage,          // required for Secure Enclave
    &error
)
let params: [String: Any] = [
    kSecAttrKeyType as String:            kSecAttrKeyTypeECSECPrimeRandom,
    kSecAttrKeySizeInBits as String:      256,
    kSecAttrTokenID as String:            kSecAttrTokenIDSecureEnclave,
    kSecPrivateKeyAttrs as String: [
        kSecAttrIsPermanent as String:    true,
        kSecAttrApplicationTag as String: "digital.heirlooms.sharing.privkey",
        kSecAttrAccessControl as String:  accessControl!,
    ]
]
let privateKey = SecKeyCreateRandomKey(params as CFDictionary, &error)
```

**Fallback path** (simulator, devices without Secure Enclave):

Same call without `kSecAttrTokenIDSecureEnclave`. Key is stored in software Keychain with `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`.

`KeychainManager.getSharingPublicKeyData()` calls `SecKeyCopyPublicKey` then `SecKeyCopyExternalRepresentation` — the result is the SEC1 uncompressed point: `0x04 || x[32] || y[32]` (65 bytes). This matches the `ephemeral_pubkey` field in the asymmetric envelope.

### 4.2 AES-256-GCM via CryptoKit

```swift
// Encrypt
let sealedBox = try AES.GCM.seal(plaintext, using: key)
// sealedBox.nonce  — 12 bytes (random, CryptoKit-generated)
// sealedBox.ciphertext — variable
// sealedBox.tag   — 16 bytes

// Decrypt
let sealedBox = try AES.GCM.SealedBox(
    nonce: AES.GCM.Nonce(data: nonceData),
    ciphertext: ciphertext,
    tag: tag
)
let plaintext = try AES.GCM.open(sealedBox, using: key)
```

### 4.3 HKDF via CryptoKit

```swift
let derivedKey = HKDF<SHA256>.deriveKey(
    inputKeyMaterial: SymmetricKey(data: sharedSecretData),
    info: Data(),           // empty — matches Android/Web
    outputByteCount: 32
)
```

No salt is used (matches the Android and web implementations). `info` is empty bytes.

### 4.4 Envelope wrapping — `p256-ecdh-hkdf-aes256gcm-v1`

The asymmetric envelope format (from `envelope_format.md`) is:

```
envelope_version (1 byte)  = 0x01
alg_id_len       (1 byte)  = len("p256-ecdh-hkdf-aes256gcm-v1") = 27
alg_id           (27 bytes) = "p256-ecdh-hkdf-aes256gcm-v1" as UTF-8
ephemeral_pubkey (65 bytes) = 0x04 || ephemX[32] || ephemY[32]
nonce            (12 bytes) = random AES-GCM nonce
ciphertext       (variable) = AES-GCM ciphertext (typically 32 bytes for a DEK)
auth_tag         (16 bytes) = AES-GCM tag
```

**wrapDEK algorithm:**

1. Generate ephemeral P-256 keypair (software key, not Secure Enclave — ephemeral keys must be exportable).
2. `sharedSecret = ECDH(ephemeralPrivate, recipientPublic)` via `P256.KeyAgreement.PrivateKey.sharedSecretFromKeyAgreement`.
3. `wrapKey = HKDF<SHA256>(ikm: sharedSecret, salt: none, info: "", outputBytes: 32)` via `sharedSecret.hkdfDerivedSymmetricKey`.
4. `sealedBox = AES.GCM.seal(dek.rawRepresentation, using: wrapKey)`.
5. Export `ephemeralPublicKey` as SEC1 uncompressed 65-byte representation.
6. Serialise the asymmetric envelope as binary per the spec above.

**unwrapDEK algorithm:**

1. Parse the envelope: validate version byte (0x01), read alg_id_len + alg_id (must equal `p256-ecdh-hkdf-aes256gcm-v1`, else throw), read ephemeral_pubkey[65], nonce[12], then split remaining bytes into ciphertext and last 16 bytes as tag.
2. Import `ephemeralPublicKey` from the 65-byte uncompressed representation.
3. `sharedSecret = ECDH(recipientPrivate, ephemeralPublic)` via `SecKeyCreateSharedSecret` or `P256.KeyAgreement.PrivateKey.sharedSecretFromKeyAgreement` (the latter requires an exportable private key; Secure Enclave keys require `SecKeyCreateSharedSecret` + manual HKDF).
4. `wrapKey = HKDF<SHA256>(sharedSecret, ...)`.
5. `dek = AES.GCM.open(sealedBox, using: wrapKey)`.

**Secure Enclave ECDH note:** Secure Enclave private keys cannot be exported; ECDH must use `SecKeyCreateSharedSecret(privateKey, publicKey, &error)` → `CFData`. The result is the raw shared secret X-coordinate (32 bytes for P-256). Feed that into HKDF manually via `HKDF<SHA256>.deriveKey(inputKeyMaterial: SymmetricKey(data: xCoord), ...)`.

### 4.5 Symmetric envelope format — `aes256gcm-v1`

Used to encrypt file content and thumbnails (DEK encrypts bytes):

```
envelope_version (1 byte)  = 0x01
alg_id_len       (1 byte)  = len("aes256gcm-v1") = 12
alg_id           (12 bytes) = "aes256gcm-v1" UTF-8
nonce            (12 bytes) = random
ciphertext       (variable)
auth_tag         (16 bytes)
```

### 4.6 DEK generation

```swift
func generateDEK() -> SymmetricKey {
    SymmetricKey(size: .bits256)   // CryptoKit CSPRNG
}
```

---

## 5. Networking

### 5.1 Base URL and auth

```
Base: https://api.heirlooms.digital
Auth header: X-Api-Key: <session_token>
```

Session token is stored in Keychain under `digital.heirlooms.session_token`.

### 5.2 Upload flow (E2EE path)

1. **Initiate** — `POST /api/content/uploads/initiate`

   Request body:
   ```json
   { "mimeType": "image/jpeg", "storage_class": "encrypted" }
   ```
   Response:
   ```json
   {
     "storageKey": "<uuid>",
     "uploadUrl": "https://storage.googleapis.com/...",
     "thumbnailStorageKey": "<uuid>",
     "thumbnailUploadUrl": "https://storage.googleapis.com/..."
   }
   ```

2. **Encrypt locally** — generate DEK, encrypt file bytes with AES-GCM (`aes256gcm-v1` envelope), generate thumbnail DEK + encrypt thumbnail.

3. **Upload ciphertext** — `PUT <uploadUrl>` with encrypted envelope bytes, `Content-Type: application/octet-stream`. Background URLSession handles this. Same for thumbnail.

4. **Wrap DEK** — `wrapDEK(dek, recipientPublicKey)` using the plot key (the plot key IS the shared symmetric key used directly, but for the DEK wrap we use the user's own device public key so they can decrypt).

   Actually: the DEK is wrapped to the *plot key* (AES-256, `master-aes256gcm-v1` format) OR directly to each member's sharing pubkey (`p256-ecdh-hkdf-aes256gcm-v1`). For iOS uploads into a shared plot, wrap the DEK under the plot key using `master-aes256gcm-v1` (symmetric wrap). The plot key was obtained at Scan 2 by unwrapping the wrapped_plot_key with the device P-256 private key.

5. **Confirm** — `POST /api/content/uploads/confirm`

   Request body:
   ```json
   {
     "storageKey": "<from step 1>",
     "mimeType": "image/jpeg",
     "fileSize": 12345,
     "storage_class": "encrypted",
     "envelopeVersion": 1,
     "wrappedDek": "<base64 symmetric envelope>",
     "dekFormat": "master-aes256gcm-v1",
     "thumbnailStorageKey": "<from step 1>",
     "wrappedThumbnailDek": "<base64>",
     "thumbnailDekFormat": "master-aes256gcm-v1",
     "takenAt": "2026-05-14T12:00:00Z"
   }
   ```

### 5.3 Item listing

```
GET /api/content/uploads?plot_id=<uuid>&limit=50&cursor=<cursor>
```

Response is a paginated JSON object: `{"items": [...], "next_cursor": "..." | null}`.

Each item contains: `id`, `storageKey`, `thumbnailStorageKey`, `mimeType`, `uploadedAt`, `wrappedDek`, `dekFormat`, `wrappedThumbnailDek`, `thumbnailDekFormat`, `uploaderUserId`.

### 5.4 Thumbnail and full-res fetch

```
GET /api/content/uploads/<id>/thumb    → encrypted thumbnail blob
GET /api/content/uploads/<id>/file     → encrypted file blob (streams)
```

Both return `application/octet-stream`. Client decrypts using the plot key to unwrap the DEK, then AES-GCM decrypts the blob.

### 5.5 Registration (Scan 1)

```
POST /api/auth/register
{
  "invite_token": "<token>",
  "username": "<generated UUID or user-chosen>",
  "display_name": "<from brief: set by technical user at QR generation>",
  "auth_salt": "<base64url>",
  "auth_verifier": "<base64url>",
  "wrapped_master_key": "<base64url>",
  "wrap_format": "p256-ecdh-hkdf-aes256gcm-v1",
  "pubkey_format": "p256-uncompressed",
  "pubkey": "<base64 65-byte SEC1 uncompressed>",
  "device_id": "<UUID>",
  "device_label": "iPhone",
  "device_kind": "ios"
}
```

Returns: `{ "session_token": "...", "user_id": "...", "expires_at": "..." }`.

The `wrapped_master_key` at registration is the master key (a fresh 256-bit random key) wrapped to the device's own P-256 public key using `p256-ecdh-hkdf-aes256gcm-v1`.

### 5.6 Plot invite redemption (Scan 2)

```
POST /api/plots/join
{
  "token": "<plot invite token>",
  "recipientSharingPubkey": "<base64 65-byte uncompressed P-256>"
}
```

This registers the recipient's pubkey with the server. The inviting device (Android/web) is then responsible for wrapping the plot key to that pubkey and calling `POST /api/plots/<id>/members/pending/<inviteId>/confirm`.

The iOS app polls `GET /api/plots/shared` to watch for its membership to transition to `status=active`, at which point it calls `GET /api/plots/<id>/plot-key` to retrieve the wrapped plot key, then unwraps it with the device private key.

---

## 6. Background URLSession

```swift
let config = URLSessionConfiguration.background(
    withIdentifier: "digital.heirlooms.upload"
)
config.isDiscretionary = false
config.sessionSendsLaunchEvents = true
config.allowsCellularAccess = UserDefaults.standard.bool(forKey: "allowCellular")
                               // default false (WiFi only)
let session = URLSession(configuration: config, delegate: uploadDelegate, delegateQueue: nil)
```

The app delegate implements `application(_:handleEventsForBackgroundURLSession:completionHandler:)` and stores the completion handler. The `URLSessionDelegate` calls it when all tasks complete.

Upload tasks use `uploadTask(with:fromFile:)` so iOS streams from disk (no RAM spike). The file written to the temp directory is the complete encrypted envelope.

---

## 7. Share Extension

### App Group

Bundle IDs:
- Main app: `digital.heirlooms.ios`
- Extension: `digital.heirlooms.ios.share`
- App Group: `group.digital.heirlooms`

The extension writes each picked item as a temporary file into the App Group container directory, then appends an entry to a `pendingUploads.json` file in the shared container.

### Handoff to main app

After writing, the extension opens the main app via `extensionContext?.open(URL(string: "heirlooms://upload-pending")!)`. The main app's scene delegate picks up the URL and starts `BackgroundUploadManager` to drain the queue.

### Memory constraint

The Share Extension memory limit is ~120 MB. Items are never loaded into RAM in full — they are written incrementally using `InputStream` → `OutputStream` to a temp file. Encryption is performed in streaming chunks (64 KB chunk size). This matches the non-negotiable requirement in the brief.

---

## 8. Setup flow detail

### State machine

```
State: fresh (no token, no plotId)
    ─► ActivateView (Scan 1)
        ─► generateKeypair
        ─► POST /api/auth/register with invite token
        ─► store session_token in Keychain
State: activated (token, no plotId)
    ─► HomeView showing [Scan QR Code] button
        ─► QR scanner (Scan 2)
        ─► POST /api/plots/join
        ─► poll GET /api/plots/shared until active
        ─► GET /api/plots/<id>/plot-key
        ─► unwrapDEK → store plotKey + plotId in Keychain
State: ready (token + plotId)
    ─► HomeView with grid
```

### Token URL schemes

| QR content | Meaning |
|---|---|
| `heirlooms://invite/friend/<token>` | Scan 1 — account invite |
| `heirlooms://invite/plot/<token>` | Scan 2 — plot invite |

Registered in `Info.plist` under `CFBundleURLTypes` with scheme `heirlooms`.

---

## 9. Free-tier sideloading for Bret's iPad

### Prerequisites

- macOS with Xcode 15+ installed (free from App Store).
- Apple ID (any — does not require paid developer enrolment).
- iPad connected via USB (or WiFi pairing if previously trusted).

### Steps

1. Open Xcode. In **Xcode → Settings → Accounts**, sign in with your Apple ID. Xcode will create a free personal team.

2. Select the `HeirloomsApp` target in the project navigator. Under **Signing & Capabilities → Team**, choose your personal team (shown as "Firstname Lastname (Personal Team)").

3. Set a unique Bundle Identifier: `digital.heirlooms.ios.dev` (or any reverse-DNS string that doesn't conflict — the free tier can use any value you choose).

4. Connect the iPad. In Xcode's device picker (top toolbar), select the iPad. The first time, Xcode will prompt to **Trust This Computer** on the iPad — accept it.

5. Press **Run** (Cmd-R) or **Product → Run**. Xcode signs the app with your free certificate and installs it.

6. On the iPad, go to **Settings → General → VPN & Device Management → Developer App → [your Apple ID]** and tap **Trust**. The app will then open normally.

### 7-day re-signing

Free-tier certificates expire after 7 days. To re-sign:

1. Reconnect the iPad and open the same Xcode project.
2. Press Run. Xcode automatically renews the certificate and re-signs the app.
3. The app's data (Keychain items, UserDefaults) survives re-signing as long as the Bundle ID stays the same.

**Important:** Keychain items stored with `kSecAttrAccessibleWhenUnlockedThisDeviceOnly` are tied to the device, not the signing identity, so they survive re-signing and app re-installs on the same device.

### Limitations of free tier

| Feature | Free tier | Paid ($99/yr) |
|---|---|---|
| TestFlight | No | Yes |
| Push notifications | No | Yes |
| Associated domains | No | Yes |
| iCloud entitlements | No | Yes |
| App Groups | Yes (with manual entitlement profile) | Yes |
| Background URLSession | Yes | Yes |
| Secure Enclave | Yes | Yes |

None of the disabled features affect M11 functionality. App Groups require adding the entitlement manually in Xcode (Signing & Capabilities → + Capability → App Groups).

---

## 10. Open questions carried forward from brief

- **API key reset semantics** in a multi-credential world (session token + device keypair). Brief defers this. Settings entry exists but has no committed behaviour.
- **auth_salt / auth_verifier scheme** for iOS: Android uses SRP-like key derivation. The iOS register endpoint takes `auth_salt` and `auth_verifier` — these should be generated consistently. Simplest safe approach: derive `auth_verifier = SHA-256(random_auth_key)` where `auth_key` is a fresh 32-byte random, stored in Keychain. `auth_salt` is a separate 16-byte random. This avoids SRP complexity while matching the server contract.
- **Plot key wrap format for uploads**: the server confirm endpoint accepts `dekFormat`; the format used when wrapping a DEK to the plot key is `master-aes256gcm-v1` (symmetric envelope). Confirm this matches what Android and web write before shipping.
