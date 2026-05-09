# SE Brief: M7 E4 — Web client encryption

**Date:** 9 May 2026
**Milestone:** M7 — Vault E2EE
**Increment:** E4 of 5
**Type:** Web-only. No server changes. No Android changes.

---

## Goal

Wire the E2 backend into a working web vault. After E4, photos and videos planted from
the browser are encrypted on-device before they reach the server. Existing
`legacy_plaintext` uploads continue to display normally. Android-encrypted uploads
(already on the server from E3) decrypt and display correctly on the web.

Three functional moments land in E4:

1. **Vault unlock** — after API key entry, the user enters their passphrase. The server
   returns the wrapped master key; the client derives the KEK from the passphrase and
   decrypts the master key into memory. For first-time web users whose vault was set up
   on Android, this is an unlock; for brand-new accounts, the same screen handles setup
   (generate master key, wrap with passphrase, upload). The vault stays unlocked for the
   lifetime of the browser session (tab close or sign-out locks it).

2. **Encrypted upload** — a new "Plant" file-picker UI encrypts content + thumbnail
   on-device, calls `POST /api/content/uploads/initiate` with
   `storage_class: "encrypted"`, PUTs two ciphertext blobs, and calls
   `POST /api/content/uploads/confirm` with all E2EE envelope fields.

3. **Decrypted display** — Garden, Explore, and PhotoDetail transparently decrypt
   thumbnails and full content for encrypted rows. `legacy_plaintext` rows display
   exactly as today.

---

## Key design decisions

**Passphrase as the unlock mechanism.** The web has no hardware keystore. The master key
lives in memory (a module-level variable in `vaultSession.js`) for the duration of the
session. On every page load the user enters their passphrase; the client fetches the
wrapped blob from `GET /api/keys/passphrase`, derives the KEK with Argon2id, and
decrypts. No master key material is ever written to `localStorage` or `sessionStorage`.

**Session lifetime.** The master key is cleared when the tab is closed or the user signs
out. No idle timeout in E4.

**One device per browser.** A P-256 keypair is generated once per browser installation
and stored as `CryptoKey` objects in IndexedDB (the browser equivalent of Android's
Keystore-persisted keypair). A stable `deviceId` UUID is written to `localStorage` on
first setup. The device is registered with the server once; subsequent sessions load the
keypair from IndexedDB and skip registration entirely. Browser data wipe is the equivalent
of a factory reset — a fresh keypair and new device registration follow on the next login.
No key material ever lands in `localStorage`; only the `deviceId` UUID and a
`vaultSetUp: "true"` flag are written there.

**WebCrypto for all symmetric + asymmetric operations.** `window.crypto.subtle` covers
AES-256-GCM, HKDF-SHA256, and P-256 ECDH natively. No third-party crypto library is
needed for these.

**Argon2id via `@noble/hashes`.** WebCrypto does not support Argon2id. The `@noble/hashes`
package (pure JS, no WASM) provides a well-audited Argon2id implementation. Parameters
match the locked spec: `m=65536, t=3, p=1`.

**EXIF deferred.** Encrypted metadata blob is sent with all fields null in E4. `takenAt`
is derived from `file.lastModified` (ISO 8601 string). Full EXIF extraction is an E5
item.

**Thumbnail generation.** Images: `<canvas>` + `drawImage` → `toBlob('image/jpeg', 0.8)`
→ scale to max 400px on the longer side. Videos: `<video>` element seeked to 1s →
`canvas.drawImage` → JPEG. If thumbnail generation fails, use a 1×1 white JPEG
placeholder (the server rejects confirms without a thumbnailStorageKey).

**Dual-path display.** A new `UploadThumb` component replaces all thumbnail `<img>` tags
that receive an `upload` object. For `storageClass === "encrypted"`: fetch ciphertext →
decrypt → `URL.createObjectURL` → `<img>`. For plaintext: existing `getThumb` path
unchanged. Decrypted blobs are cached in `vaultSession.thumbnailCache` (a `Map` keyed by
upload ID; blobs are revoked on cache eviction to avoid memory leaks).

---

## New files

### `HeirloomsWeb/src/crypto/vaultCrypto.js`

Pure-JS crypto utilities. All functions are `async` (WebCrypto is Promise-based). No
Android or Node-only imports. Runs in any browser supporting WebCrypto (all modern
browsers).

```javascript
// Constants
export const ALG_AES256GCM_V1          = 'aes256gcm-v1'
export const ALG_MASTER_AES256GCM_V1   = 'master-aes256gcm-v1'
export const ALG_ARGON2ID_AES256GCM_V1 = 'argon2id-aes256gcm-v1'
export const ALG_P256_ECDH_HKDF_V1     = 'p256-ecdh-hkdf-aes256gcm-v1'

export function generateMasterKey()      // Uint8Array(32)  — crypto.getRandomValues
export function generateDek()            // Uint8Array(32)
export function generateNonce()          // Uint8Array(12)
export function generateSalt(size = 16)  // Uint8Array(size)

// Low-level AES-256-GCM via crypto.subtle. Returns ciphertext || auth_tag (16-byte tag).
export async function aesGcmEncrypt(key, nonce, plaintext)        // Uint8Array → Uint8Array
export async function aesGcmDecrypt(key, nonce, ciphertextWithTag) // Uint8Array → Uint8Array

// Symmetric envelope builder/parser (matches server EnvelopeFormat and Android VaultCrypto):
//   [1] version=0x01
//   [1] alg_id_len
//   [N] alg_id (UTF-8)
//  [12] nonce
//   [V] ciphertext
//  [16] auth_tag
export function buildSymmetricEnvelope(algorithmId, nonce, ct)  // → Uint8Array
export function parseSymmetricEnvelope(envelope)  // → { algorithmId, nonce, ciphertextWithTag }

// Convenience wrappers:
export async function encryptSymmetric(algorithmId, key, plaintext)  // → Uint8Array
export async function decryptSymmetric(envelope, key)                // → Uint8Array

export async function wrapDekUnderMasterKey(dek, masterKey)     // → Uint8Array (master-aes256gcm-v1)
export async function unwrapDekWithMasterKey(envelope, masterKey) // → Uint8Array

// HKDF-SHA256 via crypto.subtle.deriveBits. Returns 32 bytes.
export async function hkdf(ikm, salt = null, info = new Uint8Array(0))  // → Uint8Array

// Argon2id via @noble/hashes. Params: m=65536 KiB, t=3, p=1.
export const DEFAULT_ARGON2_PARAMS = { m: 65536, t: 3, p: 1 }
export async function wrapMasterKeyWithPassphrase(masterKey, passphrase, params = DEFAULT_ARGON2_PARAMS)
  // → { envelope: Uint8Array, salt: Uint8Array, params }
export async function unwrapMasterKeyWithPassphrase(envelope, passphrase, salt, params)
  // → Uint8Array

// Asymmetric envelope (matches server ParsedAsymmetricEnvelope and Android VaultCrypto):
//   [1] version=0x01
//   [1] alg_id_len
//   [N] alg_id
//  [65] ephemeral_pubkey (SEC1 uncompressed P-256 — from crypto.subtle.exportKey('raw', key))
//  [12] nonce
//   [V] ciphertext
//  [16] auth_tag
export function buildAsymmetricEnvelope(algorithmId, ephemeralPubkeyBytes, nonce, ct)  // → Uint8Array
export function parseAsymmetricEnvelope(envelope)
  // → { algorithmId, ephemeralPubkeyBytes, nonce, ciphertextWithTag }

// Build the p256-ecdh-hkdf-aes256gcm-v1 envelope that wraps masterKey under a device pubkey.
// devicePubkeySpki: Uint8Array (DER SPKI bytes from server)
export async function wrapMasterKeyForDevice(masterKey, devicePubkeySpki)  // → Uint8Array
```

**Implementation notes:**

- `aesGcmEncrypt`: `crypto.subtle.importKey('raw', key, 'AES-GCM', false, ['encrypt'])` →
  `crypto.subtle.encrypt({ name: 'AES-GCM', iv: nonce, tagLength: 128 }, ck, plaintext)`.
  The returned ArrayBuffer is ciphertext + 16-byte tag concatenated (WebCrypto appends the
  tag). Wrap in `new Uint8Array(result)`.
- `aesGcmDecrypt`: same import, `crypto.subtle.decrypt`. Throws `DOMException` (name
  `"OperationError"`) on auth tag mismatch — callers should catch and rethrow with a
  descriptive message.
- `hkdf`: `crypto.subtle.importKey('raw', ikm, 'HKDF', false, ['deriveBits'])` →
  `crypto.subtle.deriveBits({ name: 'HKDF', hash: 'SHA-256', salt: salt ?? new Uint8Array(32), info }, key, 256)`.
- `wrapMasterKeyForDevice`:
  1. `crypto.subtle.generateKey({ name: 'ECDH', namedCurve: 'P-256' }, true, ['deriveBits'])` → ephemeral keypair
  2. `crypto.subtle.exportKey('raw', ephemeral.publicKey)` → 65-byte SEC1 uncompressed
  3. `crypto.subtle.importKey('spki', devicePubkeySpki, { name: 'ECDH', namedCurve: 'P-256' }, false, [])` → recipient key
  4. `crypto.subtle.deriveBits({ name: 'ECDH', public: recipientKey }, ephemeral.privateKey, 256)` → sharedSecret
  5. `hkdf(sharedSecret, null, toUtf8('heirlooms-v1'))` → KEK
  6. nonce ← `generateNonce()`, ct ← `aesGcmEncrypt(KEK, nonce, masterKey)`
  7. `buildAsymmetricEnvelope(ALG_P256_ECDH_HKDF_V1, ephemeralPubkeyBytes, nonce, ct)`
- Argon2id: `import { argon2id } from '@noble/hashes/argon2'`. Call
  `argon2id(passphrase_bytes, salt, { m: params.m, t: params.t, p: params.p, dkLen: 32 })`.
  `passphrase` is passed as a string; convert with `new TextEncoder().encode(passphrase)`.
- Base64 helpers (private to this module): `toB64(bytes)` and `fromB64(str)` using
  `btoa`/`atob` with `String.fromCharCode`. Exposed only as needed by callers.

### `HeirloomsWeb/src/crypto/vaultSession.js`

Module-level singleton. Mirrors Android's `VaultSession` object.

```javascript
// In-memory master key — null when locked.
let _masterKey = null          // Uint8Array | null

// Decrypted thumbnail cache — upload ID → object URL string.
// Blobs behind object URLs are revoked when evicted (max 300 entries, LRU).
export const thumbnailCache = new Map()

export function unlock(masterKey) { _masterKey = masterKey }
export function lock() {
  _masterKey = null
  thumbnailCache.forEach(url => URL.revokeObjectURL(url))
  thumbnailCache.clear()
}
export function isUnlocked() { return _masterKey !== null }
export function getMasterKey() {
  if (!_masterKey) throw new Error('Vault is locked')
  return _masterKey
}
```

Cache eviction: when `thumbnailCache.size > 300`, revoke the first (oldest) entry's URL
and delete it before inserting the new one.

### `HeirloomsWeb/src/crypto/deviceKeyManager.js`

Manages the persistent P-256 keypair and device identity for the browser. Mirrors
Android's `DeviceKeyManager`. All state is scoped to the origin.

```javascript
const DB_NAME = 'heirlooms-vault'
const DB_VERSION = 1
const STORE_NAME = 'keys'
const KEY_PRIVATE = 'devicePrivateKey'
const KEY_PUBLIC  = 'devicePublicKey'
const LS_DEVICE_ID  = 'heirlooms-deviceId'
const LS_VAULT_SETUP = 'heirlooms-vaultSetUp'

// Open (or create) the IndexedDB database.
async function openDb() { ... }  // returns IDBDatabase

// True if a keypair already exists in IndexedDB.
export async function isDeviceRegistered()  // → boolean

// Generate a new P-256 keypair and persist both CryptoKey objects to IndexedDB.
// Also writes a fresh UUID to localStorage as deviceId.
// Returns the public key in SPKI DER format (Uint8Array) for server registration.
export async function generateAndStoreKeypair()  // → Uint8Array (SPKI)

// Load the private CryptoKey from IndexedDB.
// Returns null if no keypair is stored (browser data was cleared).
export async function loadPrivateKey()  // → CryptoKey | null

// Load the public key in SPKI DER format from IndexedDB.
// Returns null if no keypair is stored.
export async function loadPublicKeySpki()  // → Uint8Array | null

// A stable UUID for this browser installation.
// Reads from localStorage; returns null if not yet set.
export function getDeviceId()  // → string | null

// "${navigator.vendor} ${navigator.platform}" — sent to server as deviceLabel.
export function getDeviceLabel()  // → string

// True once generateAndStoreKeypair() has run and LS_VAULT_SETUP is 'true'.
export function isVaultSetUp()  // → boolean

// Call after successful server registration to mark setup complete.
export function markVaultSetUp()  // writes LS_VAULT_SETUP = 'true' to localStorage
```

**Implementation notes:**

- IndexedDB operations use a simple helper (`openDb` → `IDBOpenDBRequest`) with an
  `onupgradeneeded` handler that creates the `keys` object store. Wrap each IDB
  transaction in a `Promise` (IDB is callback-based).
- `generateAndStoreKeypair`:
  ```javascript
  const keypair = await crypto.subtle.generateKey(
    { name: 'ECDH', namedCurve: 'P-256' },
    false,         // private key is non-extractable
    ['deriveBits']
  )
  // Store both CryptoKey objects in IDB — browsers support CryptoKey as IDB values.
  await idbPut(KEY_PRIVATE, keypair.privateKey)
  await idbPut(KEY_PUBLIC, keypair.publicKey)
  // Export public key as SPKI for server registration.
  const spki = new Uint8Array(await crypto.subtle.exportKey('spki', keypair.publicKey))
  localStorage.setItem(LS_DEVICE_ID, crypto.randomUUID())
  return spki
  ```
- `getDeviceLabel`: `\`${navigator.vendor} ${navigator.platform}\`` (e.g.
  `"Google Inc. MacIntel"`). Matches the Android pattern of
  `"${Build.MANUFACTURER} ${Build.MODEL}"`.
- The private key is `extractable: false`. It can still be used for `deriveBits`
  (ECDH), which is all that E4 needs. If E5 requires exporting the private key for
  a device-link handshake, re-generate with `extractable: true` at that point —
  existing users will re-register on first E5 login.

### `HeirloomsWeb/src/pages/VaultUnlockPage.jsx`

Handles three cases on the same screen. On mount, runs a `'checking'` phase that
inspects `DeviceKeyManager.isVaultSetUp()` and calls `GET /api/keys/passphrase` to
decide which state to transition into.

**Case A — returning user (vault set up, passphrase backup exists):**
- Detected when `DeviceKeyManager.isVaultSetUp()` is true and `GET /api/keys/passphrase`
  returns 200.
- Show a single passphrase field + "Unlock vault" button.
- On submit: derive KEK → `unwrapMasterKeyWithPassphrase` → `VaultSession.unlock(masterKey)`
  → call `onUnlocked()`.
- Device keypair is loaded from IndexedDB but not re-registered.

**Case B — new browser, existing account (no local vault but passphrase backup exists):**
- Detected when `DeviceKeyManager.isVaultSetUp()` is false but
  `GET /api/keys/passphrase` returns 200.
- Show a single passphrase field + "Unlock vault" button (same UI as Case A).
- On submit: derive KEK → `unwrapMasterKeyWithPassphrase` → recover master key.
- Then: `DeviceKeyManager.generateAndStoreKeypair()` → `wrapMasterKeyForDevice(masterKey, spki)`
  → `POST /api/keys/devices` → `DeviceKeyManager.markVaultSetUp()`.
- `VaultSession.unlock(masterKey)` → call `onUnlocked()`.

**Case C — brand-new account (no passphrase backup anywhere):**
- Detected when `GET /api/keys/passphrase` returns 404.
- Show two passphrase fields (entry + confirm) with mismatch validation.
- On submit: `generateMasterKey()` → `wrapMasterKeyWithPassphrase(masterKey, passphrase)`
  → `PUT /api/keys/passphrase`.
- Then: `DeviceKeyManager.generateAndStoreKeypair()` → `wrapMasterKeyForDevice(masterKey, spki)`
  → `POST /api/keys/devices` → `DeviceKeyManager.markVaultSetUp()`.
- `VaultSession.unlock(masterKey)` → call `onUnlocked()`.

**Loading state:** `WorkingDots` from brand/ + "Unlocking…" while async crypto runs.

**Error state:** Inline error text below the button. "Incorrect passphrase" for decrypt
failures; generic "Something went wrong" for network errors.

**Brand copy:** italic sub-label below the passphrase field (Cases A + B): *"Your
passphrase protects your vault."* (Case C, confirm field): *"Your passphrase protects
your vault if you ever lose access to this device."*

States as a `useState` discriminated union:
`'checking' | 'unlock' | 'setup' | 'working' | 'error'`.

Cases A and B share the `'unlock'` state (same UI); Case C uses `'setup'`.

Props: `apiKey: string, onUnlocked: () => void`.

---

## Modified files

### `HeirloomsWeb/package.json`

```json
"dependencies": {
  "@noble/hashes": "^1.6.1"
}
```

No other new runtime dependencies. WebCrypto is built into every modern browser.

### `HeirloomsWeb/src/App.jsx`

Add vault unlock to the auth flow. After `apiKey` is set and before rendering
`AuthLayout`/routes, check `isUnlocked()`. If locked, render `VaultUnlockPage` instead
of the main navigation.

```jsx
// Simplified sketch:
const [apiKey, setApiKey] = useState(null)
const [vaultUnlocked, setVaultUnlocked] = useState(false)

// ...

return (
  <AuthContext.Provider value={{ apiKey, onSignOut: () => { lock(); setApiKey(null); setVaultUnlocked(false) } }}>
    <Routes>
      <Route path="/login" element={<LoginPage onLogin={setApiKey} />} />
      <Route path="/*" element={
        !apiKey ? <Navigate to="/login" replace /> :
        !vaultUnlocked ? <VaultUnlockPage apiKey={apiKey} onUnlocked={() => setVaultUnlocked(true)} /> :
        <AuthLayout>...</AuthLayout>
      } />
    </Routes>
  </AuthContext.Provider>
)
```

When the user signs out, call `VaultSession.lock()` to clear the master key before
resetting `apiKey`.

### `HeirloomsWeb/src/api.js`

New functions (all `async`):

```javascript
// Keys API
export async function getPassphrase(apiKey)
  // GET /api/keys/passphrase → { wrappedMasterKey (b64), wrapFormat, argon2Params: {m,t,p}, salt (b64) }
  // Throws on non-2xx. Caller distinguishes 404 (not set up) from other errors.

export async function putPassphrase(apiKey, { wrappedMasterKeyB64, wrapFormat, argon2Params, saltB64 })
  // PUT /api/keys/passphrase

export async function registerDevice(apiKey, { deviceId, deviceLabel, deviceKind, pubkeyFormat, pubkeyB64, wrappedMasterKeyB64, wrapFormat })
  // POST /api/keys/devices

// Encrypted upload
export async function initiateEncryptedUpload(apiKey, mimeType)
  // POST /api/content/uploads/initiate  { mimeType, storage_class: 'encrypted' }
  // → { storageKey, uploadUrl, thumbnailStorageKey, thumbnailUploadUrl }

export async function putBlob(signedUrl, bytes)
  // PUT bytes (Uint8Array) directly to a signed URL — no X-Api-Key header, no Content-Type
  // Returns response (caller checks .ok)

export async function confirmEncryptedUpload(apiKey, {
  storageKey, mimeType, fileSize, contentHash,
  envelopeVersion, wrappedDekB64, dekFormat,
  thumbnailStorageKey, wrappedThumbnailDekB64, thumbnailDekFormat,
  takenAt, tags,
})
  // POST /api/content/uploads/confirm
  // → upload JSON object

// Byte fetch (for decryption)
export async function fetchBytes(url, apiKey)
  // GET url with X-Api-Key header → Uint8Array of response body
```

Update `toUpload` (if it exists as a mapping function, or add inline in callers):
parse `storageClass`, `envelopeVersion`, `wrappedDek` (base64 → Uint8Array),
`dekFormat`, `wrappedThumbnailDek` (base64 → Uint8Array), `thumbnailDekFormat` from
the JSON response. All new fields optional with safe defaults.

Add `upload.isEncrypted` computed as `upload.storageClass === 'encrypted'`.

### `HeirloomsWeb/src/components/UploadThumb.jsx`

New component. Replaces all thumbnail `<img>` tags that have an `upload` object in scope.

```jsx
export default function UploadThumb({ upload, className, style, alt = '', ...imgProps }) {
  if (!upload.isEncrypted) {
    // Existing plaintext path — delegates to getThumb (thumbCache.js)
    return <PlaintextThumb upload={upload} className={className} style={style} alt={alt} {...imgProps} />
  }
  return <EncryptedThumb upload={upload} className={className} style={style} alt={alt} {...imgProps} />
}

function EncryptedThumb({ upload, className, style, alt, ...imgProps }) {
  const { apiKey } = useAuth()
  const [src, setSrc] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (vaultSession.thumbnailCache.has(upload.id)) {
      setSrc(vaultSession.thumbnailCache.get(upload.id))
      setLoading(false)
      return
    }
    let cancelled = false
    ;(async () => {
      try {
        const masterKey = vaultSession.getMasterKey()
        const thumbDek = await unwrapDekWithMasterKey(upload.wrappedThumbnailDek, masterKey)
        const encBytes = await fetchBytes(thumbUrl(upload.id), apiKey)
        const plainBytes = await decryptSymmetric(encBytes, thumbDek)
        const blob = new Blob([plainBytes], { type: 'image/jpeg' })
        const url = URL.createObjectURL(blob)
        if (!cancelled) {
          // LRU eviction
          if (vaultSession.thumbnailCache.size >= 300) {
            const firstKey = vaultSession.thumbnailCache.keys().next().value
            URL.revokeObjectURL(vaultSession.thumbnailCache.get(firstKey))
            vaultSession.thumbnailCache.delete(firstKey)
          }
          vaultSession.thumbnailCache.set(upload.id, url)
          setSrc(url)
        }
      } catch { /* show placeholder */ }
      if (!cancelled) setLoading(false)
    })()
    return () => { cancelled = true }
  }, [upload.id])

  if (loading) return <ThumbPlaceholder className={className} style={style} />
  if (!src)    return <ThumbFallback className={className} style={style} />  // olive-branch icon
  return <img src={src} alt={alt} className={className} style={style} {...imgProps} />
}
```

`ThumbPlaceholder`: a `div` with the same dimensions as the image slot, background
`#e8ede4` (Forest08 equivalent), no spinner (thumbnails load fast enough).
`ThumbFallback`: same background + centered olive-branch SVG.

`thumbUrl(id)` is a helper that returns `${API_URL}/api/content/uploads/${id}/thumb`.

**Call-site migration:** replace `<img src={getThumb(...)} ...>` (and the `useEffect` that
calls `getThumb`) with `<UploadThumb upload={upload} ...>` in:
- `GardenPage.jsx` — `PlotThumbCard` (line ~31) and any quick-view modals
- `ExplorePage.jsx` — `ExploreThumb` (line ~254)
- `PhotoDetailPage.jsx` — thumbnail strip in capsule detail (if present)
- `CompostHeapPage.jsx` — compost grid thumbnails

Leave `getThumb` and `thumbCache.js` unchanged — `PlaintextThumb` (inside `UploadThumb`)
continues to use it.

### `HeirloomsWeb/src/pages/GardenPage.jsx`

Add a "Plant" upload button. A hidden `<input type="file" accept="image/*,video/*" multiple>`
triggered by a styled button. On file selection, call `encryptAndUpload(file)` for each
selected file sequentially (parallel uploads risk OOM on large files).

New helper (can live in `GardenPage.jsx` or a separate `uploader.js`):

```javascript
async function encryptAndUpload(file, apiKey, masterKey, onProgress) {
  // 1. Read file → Uint8Array
  const fileBytes = new Uint8Array(await file.arrayBuffer())

  // 2. Generate content DEK + nonce, encrypt content
  const contentDek = generateDek()
  const contentEnvelope = await encryptSymmetric(ALG_AES256GCM_V1, contentDek, fileBytes)

  // 3. Generate thumbnail → encrypt with separate DEK
  const thumbBytes = await generateThumbnail(file)       // Uint8Array (JPEG)
  const thumbDek = generateDek()
  const thumbEnvelope = await encryptSymmetric(ALG_AES256GCM_V1, thumbDek, thumbBytes)

  // 4. Wrap both DEKs under master key
  const wrappedDek = await wrapDekUnderMasterKey(contentDek, masterKey)
  const wrappedThumbDek = await wrapDekUnderMasterKey(thumbDek, masterKey)

  // 5. Content hash (SHA-256 of plaintext, before encryption)
  const hashBuffer = await crypto.subtle.digest('SHA-256', fileBytes)
  const contentHash = Array.from(new Uint8Array(hashBuffer))
    .map(b => b.toString(16).padStart(2, '0')).join('')

  // 6. Encrypted metadata (all-null for E4)
  const metaJson = JSON.stringify({ v: 1, gps_lat: null, gps_lon: null, gps_alt: null,
    camera_make: null, camera_model: null, lens_model: null,
    focal_length_mm: null, iso: null, exposure_num: null, exposure_den: null, aperture: null })
  const metaBytes = new TextEncoder().encode(metaJson)
  const metaDek = contentDek  // metadata encrypted under same DEK as content
  const encryptedMetadata = await encryptSymmetric(ALG_AES256GCM_V1, metaDek, metaBytes)

  // 7. Initiate — get signed URLs
  const { storageKey, uploadUrl, thumbnailStorageKey, thumbnailUploadUrl } =
    await initiateEncryptedUpload(apiKey, file.type)

  // 8. PUT blobs (content first, then thumbnail)
  await putBlob(uploadUrl, contentEnvelope)
  onProgress?.('content')
  await putBlob(thumbnailUploadUrl, thumbEnvelope)
  onProgress?.('thumbnail')

  // 9. Confirm
  const takenAt = new Date(file.lastModified).toISOString()
  const upload = await confirmEncryptedUpload(apiKey, {
    storageKey,
    mimeType: file.type,
    fileSize: fileBytes.length,
    contentHash,
    envelopeVersion: 1,
    wrappedDekB64: toB64(wrappedDek),
    dekFormat: ALG_MASTER_AES256GCM_V1,
    thumbnailStorageKey,
    wrappedThumbnailDekB64: toB64(wrappedThumbDek),
    thumbnailDekFormat: ALG_MASTER_AES256GCM_V1,
    encryptedMetadataB64: toB64(encryptedMetadata),
    encryptedMetadataFormat: ALG_AES256GCM_V1,
    takenAt,
    tags: [],
  })

  return upload
}
```

`toB64`: `btoa(String.fromCharCode(...bytes))`.

**Thumbnail generation:**

```javascript
async function generateThumbnail(file) {
  const MAX_DIM = 400
  const canvas = document.createElement('canvas')
  const ctx = canvas.getContext('2d')

  if (file.type.startsWith('image/')) {
    const img = await createImageBitmap(file)
    const scale = Math.min(1, MAX_DIM / Math.max(img.width, img.height))
    canvas.width = Math.round(img.width * scale)
    canvas.height = Math.round(img.height * scale)
    ctx.drawImage(img, 0, 0, canvas.width, canvas.height)
    img.close()
  } else {
    // video
    const url = URL.createObjectURL(file)
    const video = document.createElement('video')
    video.src = url
    video.muted = true
    await new Promise(resolve => { video.onloadeddata = resolve; video.load() })
    video.currentTime = 1
    await new Promise(resolve => { video.onseeked = resolve })
    const scale = Math.min(1, MAX_DIM / Math.max(video.videoWidth, video.videoHeight))
    canvas.width = Math.round(video.videoWidth * scale)
    canvas.height = Math.round(video.videoHeight * scale)
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height)
    URL.revokeObjectURL(url)
  }

  return new Promise((resolve, reject) => {
    canvas.toBlob(blob => {
      if (!blob) { reject(new Error('thumbnail generation failed')); return }
      blob.arrayBuffer().then(ab => resolve(new Uint8Array(ab)))
    }, 'image/jpeg', 0.8)
  })
}
```

If thumbnail generation throws, fall back to a 1×1 white JPEG (hardcoded bytes). Do not
proceed without a thumbnail — the server currently rejects confirms without
`thumbnailStorageKey`.

**Upload UX:** A "Plant" button (top-right area of GardenPage, near existing actions).
While uploading, show inline progress: "Encrypting…" → "Uploading…" → "Done". On error,
show a toast via `Toast.jsx`. After successful confirm, prepend the returned upload to the
local upload list without a full page refresh.

### `HeirloomsWeb/src/pages/PhotoDetailPage.jsx`

Full content display for encrypted uploads. Add a `useEffect` that fires when
`upload.isEncrypted` is true:

```javascript
useEffect(() => {
  if (!upload?.isEncrypted) return
  let cancelled = false
  let objectUrl = null
  ;(async () => {
    try {
      const masterKey = vaultSession.getMasterKey()
      const dek = await unwrapDekWithMasterKey(upload.wrappedDek, masterKey)
      const encBytes = await fetchBytes(fileUrl(upload.id), apiKey)
      const plainBytes = await decryptSymmetric(encBytes, dek)
      const blob = new Blob([plainBytes], { type: upload.mimeType })
      objectUrl = URL.createObjectURL(blob)
      if (!cancelled) setDecryptedSrc(objectUrl)
    } catch (e) {
      if (!cancelled) setDecryptError(true)
    }
  })()
  return () => {
    cancelled = true
    if (objectUrl) URL.revokeObjectURL(objectUrl)
  }
}, [upload?.id])
```

Add `const [decryptedSrc, setDecryptedSrc] = useState(null)` and
`const [decryptError, setDecryptError] = useState(false)`.

For **image** display: replace the `<img src={blobUrl}>` block with:
- If `upload.isEncrypted` and `decryptedSrc`: `<img src={decryptedSrc} ...>`
- If `upload.isEncrypted` and no `decryptedSrc` yet: `<WorkingDots />`
- If `upload.isEncrypted` and `decryptError`: error placeholder
- If not encrypted: existing fetch-blob-from-/file path unchanged

For **video** display: if `upload.isEncrypted` and `decryptedSrc`:
`<video src={decryptedSrc} controls>`. The blob URL plays directly in the browser
`<video>` element — no temp file needed (browsers can play video from object URLs).
If not encrypted: existing signed-URL or blob-download path unchanged.

`fileUrl(id)` returns `${API_URL}/api/content/uploads/${id}/file`.

---

## Tests

### `HeirloomsWeb/src/test/vaultCrypto.test.js`

Vitest unit tests. WebCrypto is available globally in Node.js 18+ (the vitest runtime).
No additional polyfills needed. All 14 tests (mirrors Android `VaultCryptoTest`):

1. `generateMasterKey` returns 32 bytes; two calls differ
2. `generateDek` returns 32 bytes; differs from master key
3. `aesGcmEncrypt` / `aesGcmDecrypt` round-trip: plaintext → encrypt → decrypt → matches
4. `aesGcmDecrypt` with wrong key throws
5. `buildSymmetricEnvelope` produces correct byte layout (version=1, algId, nonce, ct+tag)
6. `decryptSymmetric` round-trip: `encryptSymmetric` then `decryptSymmetric` → matches
7. `wrapDekUnderMasterKey` / `unwrapDekWithMasterKey` round-trip
8. Symmetric envelope with wrong key → throws
9. `hkdf` is deterministic for same inputs; differs for different IKMs
10. `wrapMasterKeyWithPassphrase` returns non-null envelope, salt, params
11. `unwrapMasterKeyWithPassphrase` round-trip: wrap → unwrap → master key matches
12. `unwrapMasterKeyWithPassphrase` with wrong passphrase → throws
13. `buildAsymmetricEnvelope` / `parseAsymmetricEnvelope` round-trip preserves all fields
14. Asymmetric envelope ECDH round-trip: generate P-256 keypair (WebCrypto), ephemeral
    keypair, ECDH + HKDF, encrypt master key into asymmetric envelope, parse back,
    ECDH + HKDF + decrypt → matches original master key

---

## Wire-up checklist

Before accepting E4 as done:

- [ ] `VaultUnlockPage` shown after API key entry; passphrase unlocks master key into memory
- [ ] Case C (new account): generates master key, uploads passphrase-wrapped blob, generates keypair, registers device, proceeds to Garden
- [ ] Case B (new browser, existing account): passphrase decrypts master key from server, generates keypair, registers device once, proceeds to Garden
- [ ] Case A (returning browser): passphrase decrypts master key; no keypair generated, no device registration called
- [ ] Logging in from the same browser a second time does NOT add a new row to `GET /api/keys/devices` (device count stays stable)
- [ ] Sign-out calls `VaultSession.lock()` and clears master key
- [ ] Page refresh prompts passphrase again (no master key in localStorage or IndexedDB)
- [ ] "Plant" button visible on GardenPage; file picker accepts images + videos
- [ ] A planted photo produces `storageClass: "encrypted"` on the server
- [ ] `GET /api/content/uploads` returns `wrappedThumbnailDek` for the encrypted upload
- [ ] Garden and Explore thumbnails decrypt and display for the freshly-uploaded encrypted photo
- [ ] PhotoDetail full image decrypts and displays for an encrypted photo
- [ ] PhotoDetail encrypted video decrypts to object URL and plays in `<video>`
- [ ] Existing `legacy_plaintext` uploads display unchanged in Garden, Explore, PhotoDetail, Compost
- [ ] Android-encrypted uploads (from E3) display correctly when viewed on web
- [ ] All 14 `vaultCrypto.test.js` tests pass (`npm test`)
- [ ] Existing web unit tests pass with no regressions
- [ ] `npm run build` completes cleanly (no bundler errors from `@noble/hashes`)

---

## Acceptance criteria

1. All 14 `vaultCrypto.test.js` tests pass.
2. Existing web tests (`npm test`) pass with no regressions.
3. End-to-end: plant a photo from the web → server stores `storage_class='encrypted'`
   row → Garden shows decrypted thumbnail → PhotoDetail shows decrypted full image.
4. Passphrase backup: `GET /api/keys/passphrase` returns 200 after web vault setup.
5. Device persistence: logging in from the same browser twice results in exactly one entry
   in `GET /api/keys/devices` for the web device (not two).
6. Android-encrypted photo: Garden on web shows decrypted thumbnail without error.
6. `legacy_plaintext` photos display normally in Garden, Explore, and Compost.

---

## Ship state after E4

v0.29.0. All photos planted from Android and from the web are end-to-end encrypted. The
server holds only ciphertext and wrapped keys. The web client decrypts on the fly using
the master key derived from the user's passphrase each session. E5 brings onboarding
polish, recovery flows, and legacy migration.
