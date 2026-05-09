# SE Brief: M7 E3 — Android client encryption

**Date:** 9 May 2026
**Milestone:** M7 — Vault E2EE
**Increment:** E3 of 5
**Type:** Android-only. No server changes. No web changes.

---

## Goal

Wire the E2 backend into a working Android vault. After E3, every photo and video
planted from Android is encrypted on-device before it reaches the server. Existing
`legacy_plaintext` uploads are not discarded — they continue to display and behave
normally; their storage class is treated as unencrypted and no migration is run.
New uploads are always `storage_class: "encrypted"`.

Three functional moments land in E3:

1. **First-launch vault setup** — master key generated, device registered with the
   server, passphrase backup created. Shown once; never shown again once complete.

2. **Encrypted upload** — every `UploadWorker` invocation encrypts the file + thumbnail
   on-device, calls `/api/content/uploads/initiate` with `storage_class: "encrypted"`,
   PUTs two ciphertext blobs, and calls `/api/content/uploads/confirm` with all E2EE
   envelope fields.

3. **Decrypted display** — Garden, Explore, and PhotoDetail transparently decrypt
   thumbnails and full content for encrypted rows. `legacy_plaintext` rows display
   exactly as today. The user should see no visible difference.

---

## Key design decisions

**Storage class branching.** Treat any `storageClass != "encrypted"` as plaintext.
`legacy_plaintext` items remain, display normally, and their storage class is never
changed by client code. `public` (when the server eventually supports it) would fall
into the same unencrypted path.

**Master key storage.** The master key is a 256-bit `ByteArray` generated from
`SecureRandom`. It lives in memory in `VaultSession` while the process is alive. At
rest it is wrapped by a Keystore-backed AES-256 key and the blob stored in
`SharedPreferences`. The Keystore key requires no user authentication, so the vault
auto-unlocks at process start.

**Device keypair.** A P-256 keypair generated in software (not in the Android Keystore
directly, to avoid the `PURPOSE_AGREE_KEY` API-31 requirement). The private key is
wrapped by the Keystore AES key and stored in `SharedPreferences`. The public key is
stored in plaintext. The server receives the pubkey in SPKI/X.509 format
(`pubkeyFormat: "p256-spki"`). The wrapped-master-key blob sent to the server uses the
asymmetric envelope format (`p256-ecdh-hkdf-aes256gcm-v1`): ECDH between an ephemeral
key and the device static public key, HKDF derive KEK, AES-256-GCM encrypt master key.

**DEK per file.** Each encrypted upload has its own 256-bit DEK. The content DEK is
wrapped under the master key (`master-aes256gcm-v1` symmetric envelope). The thumbnail
has its own DEK wrapped separately. Both wrapped DEKs are sent to the server in the
confirm body and stored in the upload row; the server returns them in all upload list
and detail responses.

**Passphrase backup.** Mandatory in E3. Argon2id (via BouncyCastle) derives a 32-byte
KEK from the user's passphrase + a random 16-byte salt. The master key is wrapped under
this KEK (`argon2id-aes256gcm-v1` envelope) and uploaded to `PUT /api/keys/passphrase`.
Parameters: `m=65536, t=3, p=1`.

**Thumbnail generation.** Client-side for encrypted uploads. Images: `BitmapFactory`
decode → `Bitmap.createScaledBitmap(maxDim=400)` → JPEG at quality 80. Videos:
`ThumbnailUtils.createVideoThumbnail` → JPEG at quality 80. The thumbnail is encrypted
with its own DEK before upload.

**Decrypted display.** A new `UploadThumbnail` composable replaces `HeirloomsImage`
at all call-sites that have an `Upload` object. For `storageClass == "encrypted"`:
fetch encrypted bytes from the thumb URL → decrypt with thumbnail DEK → decode to
`ImageBitmap` → display. Decrypted bitmaps are cached in-memory in `VaultSession` by
upload ID. For plaintext rows the existing Coil path is unchanged.

**Full image display.** PhotoDetailScreen fetches the full file for encrypted images
and decrypts in memory before display. For encrypted videos, the ciphertext is written
to a temp file in `context.cacheDir/vault_temp/`, decrypted to a second temp file,
then played with ExoPlayer from the local file URI. Streaming decryption is a known
limitation and a future-increment item.

**Auto-unlock on process start.** `MainActivity.onCreate()` calls
`DeviceKeyManager.loadMasterKey()` and unlocks `VaultSession`. `UploadWorker.doWork()`
does the same before encrypting. If the vault is not yet set up, the worker returns
`Result.failure()` (no plaintext fallback).

---

## New files

### `HeirloomsApp/app/src/main/kotlin/digital/heirlooms/crypto/VaultCrypto.kt`

Pure-Kotlin crypto utilities (no Android dependencies; testable on JVM).

```kotlin
object VaultCrypto {
    // Constants
    const val ALG_AES256GCM_V1           = "aes256gcm-v1"
    const val ALG_MASTER_AES256GCM_V1    = "master-aes256gcm-v1"
    const val ALG_ARGON2ID_AES256GCM_V1  = "argon2id-aes256gcm-v1"
    const val ALG_P256_ECDH_HKDF_V1      = "p256-ecdh-hkdf-aes256gcm-v1"

    fun generateMasterKey(): ByteArray        // 32 random bytes
    fun generateDek(): ByteArray              // 32 random bytes
    fun generateNonce(): ByteArray            // 12 random bytes
    fun generateSalt(size: Int = 16): ByteArray

    // Low-level AES-256-GCM (JCE). Returns ciphertext || auth_tag.
    fun aesGcmEncrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray): ByteArray
    fun aesGcmDecrypt(key: ByteArray, nonce: ByteArray, ciphertextWithTag: ByteArray): ByteArray

    // Symmetric envelope builder/parser (matches server EnvelopeFormat):
    //   [1] version=0x01
    //   [1] alg_id_len
    //   [N] alg_id
    //  [12] nonce
    //   [V] ciphertext
    //  [16] auth_tag
    fun buildSymmetricEnvelope(algorithmId: String, nonce: ByteArray, ct: ByteArray): ByteArray
    fun decryptSymmetric(envelope: ByteArray, key: ByteArray): ByteArray

    // Convenience wrappers using the above:
    fun encryptSymmetric(algorithmId: String, key: ByteArray, plaintext: ByteArray): ByteArray
    fun wrapDekUnderMasterKey(dek: ByteArray, masterKey: ByteArray): ByteArray     // master-aes256gcm-v1
    fun unwrapDekWithMasterKey(envelope: ByteArray, masterKey: ByteArray): ByteArray

    // Argon2id (BouncyCastle). Params: m=65536 KiB, t=3, p=1.
    data class Argon2Params(val m: Int = 65536, val t: Int = 3, val p: Int = 1)
    data class PassphraseWrap(val envelope: ByteArray, val salt: ByteArray, val params: Argon2Params)
    fun wrapMasterKeyWithPassphrase(masterKey: ByteArray, passphrase: CharArray,
                                    params: Argon2Params = Argon2Params()): PassphraseWrap
    fun unwrapMasterKeyWithPassphrase(envelope: ByteArray, passphrase: CharArray,
                                      salt: ByteArray, params: Argon2Params): ByteArray

    // Asymmetric envelope builder/parser (matches server ParsedAsymmetricEnvelope):
    //   [1] version=0x01
    //   [1] alg_id_len
    //   [N] alg_id
    //  [65] ephemeral_pubkey (SEC1 uncompressed P-256)
    //  [12] nonce
    //   [V] ciphertext
    //  [16] auth_tag
    data class ParsedAsymmetricEnvelope(
        val algorithmId: String,
        val ephemeralPubkeyBytes: ByteArray,  // 65-byte SEC1 uncompressed
        val nonce: ByteArray,
        val ciphertextWithTag: ByteArray,
    )
    fun buildAsymmetricEnvelope(algorithmId: String, ephemeralPubkeyBytes: ByteArray,
                                nonce: ByteArray, ct: ByteArray): ByteArray
    fun parseAsymmetricEnvelope(envelope: ByteArray): ParsedAsymmetricEnvelope

    // HKDF-SHA256 (RFC 5869), extract+expand for 32 bytes.
    // Implemented using javax.crypto.Mac — no external dependency.
    fun hkdf(ikm: ByteArray, salt: ByteArray? = null, info: ByteArray = ByteArray(0)): ByteArray
}
```

### `HeirloomsApp/app/src/main/kotlin/digital/heirlooms/crypto/DeviceKeyManager.kt`

Android-specific key management. Depends on `Context`, Keystore, and `SharedPreferences`.

```kotlin
class DeviceKeyManager(private val context: Context) {

    // A stable UUID for this installation, generated on first access.
    val deviceId: String

    // "${Build.MANUFACTURER} ${Build.MODEL}"
    val deviceLabel: String

    // True once setupVault() has completed.
    fun isVaultSetUp(): Boolean

    // One-time setup. Generates and stores:
    //   - Keystore AES-256 key (alias "HeirloomsVaultKey") for local wrapping
    //   - Random 256-bit master key, wrapped by the Keystore key → SharedPreferences
    //   - Software P-256 keypair; private key wrapped by Keystore key → SharedPreferences
    //   - Public key in plaintext → SharedPreferences
    // Returns: the device P-256 public key bytes (SEC1 uncompressed, 65 bytes)
    fun setupVault(masterKey: ByteArray): ByteArray

    // Load + unwrap master key from SharedPreferences using the Keystore AES key.
    // Returns null if vault is not set up.
    fun loadMasterKey(): ByteArray?

    // Returns the stored device P-256 public key in X.509/SPKI DER format (for server registration).
    // Returns null if vault is not set up.
    fun getDevicePublicKeySpki(): ByteArray?

    // Returns the device P-256 public key in SEC1 uncompressed format (65 bytes).
    // Returns null if vault is not set up.
    fun getDevicePublicKeySec1(): ByteArray?

    // Build the asymmetric envelope that wraps the master key under the device public key.
    // Used for server device registration (POST /api/keys/devices).
    // Algorithm: p256-ecdh-hkdf-aes256gcm-v1
    // Method: ECDH(ephemeral_private, device_static_public) → HKDF → KEK → AES-GCM(master_key)
    fun wrapMasterKeyForServer(masterKey: ByteArray): ByteArray
}
```

**Implementation notes:**
- Keystore AES-256 key: `KeyGenParameterSpec.Builder(PURPOSE_ENCRYPT | PURPOSE_DECRYPT)`
  with `BLOCK_MODE_GCM`, `ENCRYPTION_PADDING_NONE`, no user authentication required.
  Works at API 26+.
- Local wrap format (Keystore AES → master key): nonce || GCM-ciphertext+tag. Not an
  envelope blob — just raw cipher output stored in SharedPreferences. The Keystore key is
  not sent to the server and not an envelope.
- P-256 keypair: `KeyPairGenerator.getInstance("EC")` + `ECGenParameterSpec("secp256r1")`.
  Private key stored as `keyPair.private.encoded` (PKCS8) wrapped by Keystore AES key.
  Public key stored as `keyPair.public.encoded` (X.509/SPKI) plaintext.
- For `wrapMasterKeyForServer`: generate ephemeral P-256 keypair (software),
  `KeyAgreement("ECDH")` with ephemeral private + device static public, `hkdf(sharedSecret,
  info="heirlooms-v1")` → KEK, `aesGcmEncrypt(KEK, nonce, masterKey)`, build asymmetric
  envelope with ephemeral public key bytes in SEC1 uncompressed (0x04 || x || y, 65 bytes).
- `getDevicePublicKeySpki()`: return the stored SPKI bytes. For the server's `pubkey` field.
- `getDevicePublicKeySec1()`: decode the stored SPKI bytes, extract x,y, prepend 0x04.

### `HeirloomsApp/app/src/main/kotlin/digital/heirlooms/crypto/VaultSession.kt`

```kotlin
object VaultSession {
    // In-memory master key; null if not unlocked.
    val isUnlocked: Boolean
    val masterKey: ByteArray  // throws if not unlocked

    // Thumbnail cache: upload ID → decrypted ImageBitmap.
    val thumbnailCache: java.util.concurrent.ConcurrentHashMap<String, androidx.compose.ui.graphics.ImageBitmap>

    fun unlock(masterKey: ByteArray)
    fun lock()
}
```

`VaultSession` is a top-level `object` (Kotlin singleton). The master key lives in memory
until the process is killed. No idle timeout in E3 — add in a later increment if needed.

### `HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/main/VaultSetupScreen.kt`

Three states rendered as a single screen:

1. **Generating keys** — `CircularProgressIndicator` + "Setting up your vault…" (shown
   briefly while `setupVault()` runs on IO dispatcher).

2. **Passphrase entry** — two `OutlinedTextField` (passphrase + confirm); `Button`
   disabled until both match and neither is blank; italic Georgia brand-voice sub-label:
   *"Your passphrase protects your vault if you ever lose this phone."*; toggle show/hide
   passphrase. `Button` label: "Save passphrase" (not "Continue" — emphasises the action).

3. **Working** — `CircularProgressIndicator` + "Saving…" while device registration +
   passphrase backup upload runs.

### `HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/main/VaultSetupViewModel.kt`

```kotlin
class VaultSetupViewModel(application: Application, private val apiKey: String)
    : AndroidViewModel(application) {

    sealed class SetupState {
        object GeneratingKeys : SetupState()
        object AwaitingPassphrase : SetupState()
        object Saving : SetupState()
        object Done : SetupState()
        data class Error(val message: String) : SetupState()
    }

    val state: StateFlow<SetupState>

    // Called on composition: generates master key + device keypair, transitions to
    // AwaitingPassphrase. Runs on Dispatchers.Default (crypto is CPU-bound).
    fun startSetup()

    // Called when user taps "Save passphrase". Runs on Dispatchers.IO:
    //   1. Wrap master key with passphrase → PassphraseWrap
    //   2. POST /api/keys/devices with device pubkey + asymmetric-wrapped master key
    //   3. PUT /api/keys/passphrase with passphrase-wrapped master key
    //   4. DeviceKeyManager.setupVault(masterKey) to persist locally
    //   5. VaultSession.unlock(masterKey)
    //   6. Transition to Done
    fun submitPassphrase(passphrase: CharArray)
}
```

Factory: `VaultSetupViewModelFactory(application, apiKey)`.

---

## Modified files

### `HeirloomsApp/app/build.gradle.kts`

```kotlin
// New dependency:
implementation("org.bouncycastle:bcprov-jdk18on:1.79")

// Packaging options to silence META-INF signature conflicts from BC jar:
android {
    packaging {
        resources.excludes += "META-INF/BC*.DSA"
        resources.excludes += "META-INF/BC*.SF"
        resources.excludes += "META-INF/BCEL.SF"
        resources.excludes += "META-INF/BCEL.DSA"
    }
}

// versionCode → 32, versionName → "0.28.0"
```

### `HeirloomsApp/app/src/main/kotlin/digital/heirlooms/api/Models.kt`

Extend `Upload` with E2EE fields. All new fields optional and defaulted for legacy rows:

```kotlin
data class Upload(
    // ... existing fields unchanged ...
    val storageClass: String = "legacy_plaintext",   // "encrypted" or "legacy_plaintext"
    val envelopeVersion: Int? = null,
    val wrappedDek: ByteArray? = null,               // decoded from base64
    val dekFormat: String? = null,
    val wrappedThumbnailDek: ByteArray? = null,
    val thumbnailDekFormat: String? = null,
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
    val isEncrypted: Boolean get() = storageClass == "encrypted"
}
```

Note: `ByteArray` is used here rather than `String` so callers work with decoded bytes
directly. `HeirloomsApi.toUpload()` does the base64 decode.

### `HeirloomsApp/app/src/main/kotlin/digital/heirlooms/api/HeirloomsApi.kt`

New API methods:

```kotlin
// Keys API
suspend fun registerDevice(
    deviceId: String,
    deviceLabel: String,
    deviceKind: String,              // "android"
    pubkeyFormat: String,            // "p256-spki"
    pubkeyB64: String,
    wrappedMasterKeyB64: String,
    wrapFormat: String,              // "p256-ecdh-hkdf-aes256gcm-v1"
)

suspend fun putPassphrase(
    wrappedMasterKeyB64: String,
    wrapFormat: String,              // "argon2id-aes256gcm-v1"
    argon2Params: VaultCrypto.Argon2Params,
    saltB64: String,
)

// Encrypted upload initiation
data class InitiateResponse(
    val storageKey: String,
    val uploadUrl: String,
    val thumbnailStorageKey: String,
    val thumbnailUploadUrl: String,
)

suspend fun initiateEncryptedUpload(mimeType: String): InitiateResponse

// Confirm encrypted upload
suspend fun confirmEncryptedUpload(
    storageKey: String,
    mimeType: String,
    fileSize: Long,
    envelopeVersion: Int,
    wrappedDekB64: String,
    dekFormat: String,
    thumbnailStorageKey: String,
    wrappedThumbnailDekB64: String,
    thumbnailDekFormat: String,
    takenAt: String?,
    tags: List<String>,
): Upload
```

Update `toUpload()` to decode new E2EE fields:

```kotlin
val wrappedDekB64 = optString("wrappedDek").takeIf { it.isNotEmpty() && it != "null" }
val wrappedThumbnailDekB64 = optString("wrappedThumbnailDek").takeIf { it.isNotEmpty() && it != "null" }

// Decode from base64 into ByteArray:
wrappedDek = wrappedDekB64?.let { android.util.Base64.decode(it, android.util.Base64.DEFAULT) },
wrappedThumbnailDek = wrappedThumbnailDekB64?.let { android.util.Base64.decode(it, android.util.Base64.DEFAULT) },
```

### `HeirloomsApp/app/src/main/kotlin/digital/heirlooms/app/Uploader.kt`

New method `uploadEncryptedViaSigned`:

```kotlin
// Encrypted variant of uploadViaSigned:
// 1. Reads file, generates image thumbnail (or video thumbnail)
// 2. Generates content DEK + thumbnail DEK
// 3. Encrypts file bytes and thumbnail bytes (AES-256-GCM)
// 4. Wraps both DEKs under masterKey
// 5. POST /uploads/initiate (storage_class: "encrypted") → two signed URLs
// 6. PUT encrypted content to contentUrl (with progress callback)
// 7. PUT encrypted thumbnail to thumbnailUrl
// 8. POST /uploads/confirm with all E2EE fields
//
// Thumbnail generation: images via BitmapFactory + createScaledBitmap (max 400px);
// videos via ThumbnailUtils.createVideoThumbnail. If thumbnail gen fails, proceed
// without thumbnail (thumbnailStorageKey will be absent from confirm body — this is
// rejected by current server; include an empty-thumbnail fallback).
fun uploadEncryptedViaSigned(
    baseUrl: String?,
    file: File,
    mimeType: String,
    masterKey: ByteArray,
    apiKey: String? = null,
    tags: List<String> = emptyList(),
    onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)? = null,
    onConfirming: (() -> Unit)? = null,
): UploadResult
```

Keep the existing `uploadViaSigned` method unchanged (legacy fallback).

**Thumbnail notes:**
- `BitmapFactory.decodeFile(file.path)` for images. If this fails (e.g. unsupported
  format or OOM), log and proceed with an empty placeholder thumbnail of 1×1 white pixel.
- `ThumbnailUtils.createVideoThumbnail(file.path, ThumbnailKind.MINI_KIND)` for videos
  (API 29+). For API 26-28: `ThumbnailUtils.createVideoThumbnail(file.path,
  MediaStore.Video.Thumbnails.MINI_KIND)`.
- Compress thumbnail to JPEG: `bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)`.
- Max thumbnail dimension 400px on the longer side; maintain aspect ratio.
- The thumbnail DEK is separate from the content DEK. Both use `VaultCrypto.generateDek()`.

### `HeirloomsApp/app/src/main/kotlin/digital/heirlooms/app/UploadWorker.kt`

In `doWork()`, before calling uploader, auto-unlock the vault:

```kotlin
val deviceKeyManager = DeviceKeyManager(context)
if (!VaultSession.isUnlocked) {
    val mk = deviceKeyManager.loadMasterKey() ?: return Result.failure(
        workDataOf("error" to "Vault not set up")
    )
    VaultSession.unlock(mk)
}

val result = if (VaultSession.isUnlocked) {
    uploader.uploadEncryptedViaSigned(
        baseUrl = BASE_URL,
        file = file,
        mimeType = mimeType,
        masterKey = VaultSession.masterKey,
        apiKey = apiKey,
        tags = tags,
        onProgress = { bw, ft -> setProgressAsync(...) },
    )
} else {
    uploader.uploadViaSigned(...)  // unreachable after E3 but kept as dead fallback
}
```

### `HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/main/MainApp.kt`

Add vault setup to the first-launch flow. `DeviceKeyManager` is instantiated with
`LocalContext.current`. Vault setup is shown once: when `apiKey` is set, `welcomed` is
true, and `DeviceKeyManager.isVaultSetUp()` is false.

Auto-unlock on entry to `MainNavigation`: if the vault is set up and `VaultSession` is
locked, unlock it immediately (synchronously, main thread — the Keystore decrypt is fast).

```kotlin
when {
    apiKey.isEmpty() -> ApiKeyScreen(...)
    !welcomed -> WelcomeScreen(...)
    !deviceKeyManager.isVaultSetUp() -> VaultSetupScreen(
        apiKey = apiKey,
        onComplete = { vaultReady = true },
    )
    else -> {
        // Auto-unlock vault if process was restarted
        if (!VaultSession.isUnlocked) {
            deviceKeyManager.loadMasterKey()?.let { VaultSession.unlock(it) }
        }
        MainNavigation(apiKey = apiKey, onApiKeyReset = { ... })
    }
}
```

Add `var vaultReady by rememberSaveable { mutableStateOf(deviceKeyManager.isVaultSetUp()) }`
to track setup completion across recompositions.

### `HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/common/HeirloomsImage.kt`

Add `UploadThumbnail` composable:

```kotlin
@Composable
fun UploadThumbnail(
    upload: Upload,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    colorFilter: ColorFilter? = null,
    rotation: Int = 0,
) {
    if (upload.isEncrypted) {
        EncryptedThumbnail(upload = upload, contentDescription = contentDescription,
            modifier = modifier, contentScale = contentScale, colorFilter = colorFilter,
            rotation = rotation)
    } else {
        val api = LocalHeirloomsApi.current
        HeirloomsImage(url = api.thumbUrl(upload.id), contentDescription = contentDescription,
            modifier = modifier, contentScale = contentScale, colorFilter = colorFilter,
            rotation = rotation)
    }
}

@Composable
private fun EncryptedThumbnail(upload: Upload, ...) {
    val api = LocalHeirloomsApi.current
    val context = LocalContext.current
    var imageBitmap by remember(upload.id) { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember(upload.id) { mutableStateOf(true) }

    LaunchedEffect(upload.id) {
        VaultSession.thumbnailCache[upload.id]?.let {
            imageBitmap = it
            isLoading = false
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            runCatching {
                val mk = VaultSession.masterKey
                val wrappedThumbDek = upload.wrappedThumbnailDek ?: return@runCatching
                val thumbDek = VaultCrypto.unwrapDekWithMasterKey(wrappedThumbDek, mk)
                val encryptedBytes = api.fetchBytes(api.thumbUrl(upload.id))
                val decryptedBytes = VaultCrypto.decryptSymmetric(encryptedBytes, thumbDek)
                val bmp = BitmapFactory.decodeByteArray(decryptedBytes, 0, decryptedBytes.size)
                bmp?.asImageBitmap()?.also { VaultSession.thumbnailCache[upload.id] = it }
            }.onSuccess { bm -> imageBitmap = bm }
        }
        isLoading = false
    }

    if (imageBitmap != null) {
        Image(bitmap = imageBitmap!!, contentDescription = contentDescription,
            modifier = rotationModifier(modifier, rotation), contentScale = contentScale,
            colorFilter = colorFilter)
    } else if (isLoading) {
        Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else {
        // failed to decrypt or no thumbnail — show olive icon placeholder
        Box(modifier.background(Forest08), contentAlignment = Alignment.Center) {
            OliveBranchIcon(Modifier.size(24.dp))
        }
    }
}
```

Add `fetchBytes(url: String): ByteArray` to `HeirloomsApi` — synchronous OkHttp GET
that returns the response body bytes.

**Call-site migration:** all places that currently call `HeirloomsImage(url = api.thumbUrl(upload.id))`
and have an `Upload` in scope should call `UploadThumbnail(upload = upload)` instead.
The existing `HeirloomsImage(url = ...)` signature stays for other use-cases
(e.g., passing a non-thumb URL directly).

Affected composables: `PlotThumbCard` (GardenScreen), `ExploreThumb` (ExploreScreen),
the thumbnail strip in `CapsuleDetailScreen`, and any thumbnail in `PhotoDetailScreen`.

### `HeirloomsApp/.../garden/PhotoDetailScreen.kt` + `PhotoDetailViewModel.kt`

Full content display for encrypted uploads:

```kotlin
// In PhotoDetailViewModel (or PhotoDetailScreen LaunchedEffect):
// If upload.isEncrypted:
//   1. fetch /file bytes via api.fetchBytes(api.fileUrl(upload.id))
//   2. decrypt: VaultCrypto.unwrapDekWithMasterKey(wrappedDek, masterKey) → dek
//               VaultCrypto.decryptSymmetric(encryptedBytes, dek) → plaintext
//   3. if image: BitmapFactory.decodeByteArray → show as Image composable
//   4. if video: write plaintext to context.cacheDir/vault_temp/{id}.{ext}
//               expose as StateFlow<Uri?> → play with ExoPlayer

// For legacy uploads: existing flow unchanged.
```

Add `var decryptedBitmap: ImageBitmap? = null` and `var decryptedVideoUri: Uri? = null`
to `PhotoDetailViewModel` as `StateFlow`s. The screen observes these and renders
accordingly.

---

## Tests

### `VaultCryptoTest.kt`  (JVM unit tests, HeirloomsServer or HeirloomsApp test module)

Add to `HeirloomsApp/app/src/test/kotlin/digital/heirlooms/crypto/VaultCryptoTest.kt`.
All tests run on the JVM (no Android dependencies). Add BouncyCastle to `testImplementation`.

1. `generateMasterKey` returns 32 bytes of apparent randomness (two calls differ)
2. `generateDek` returns 32 bytes, distinct from master key
3. `aesGcmEncrypt / aesGcmDecrypt` round-trip: plaintext → encrypt → decrypt → matches
4. `aesGcmDecrypt` with wrong key throws `AEADBadTagException`
5. `buildSymmetricEnvelope` produces expected byte layout (version=1, algId, nonce, ct+tag)
6. `decryptSymmetric` round-trip: `encryptSymmetric` then `decryptSymmetric` → matches
7. `wrapDekUnderMasterKey / unwrapDekWithMasterKey` round-trip
8. Symmetric envelope with wrong key → exception
9. `hkdf` is deterministic for same inputs, different for different IKMs
10. `wrapMasterKeyWithPassphrase` returns non-null envelope, salt, params; envelope is valid symmetric envelope
11. `unwrapMasterKeyWithPassphrase` round-trip: wrap → unwrap → master key matches
12. `unwrapMasterKeyWithPassphrase` with wrong passphrase → exception
13. `buildAsymmetricEnvelope / parseAsymmetricEnvelope` round-trip preserves fields
14. Asymmetric envelope ECDH round-trip: generate P-256 keypair (software), ephemeral keypair,
    ECDH + HKDF, encrypt master key into asymmetric envelope, parse back, ECDH + HKDF + decrypt → matches

---

## Wire-up checklist

Before accepting E3 as done:

- [ ] `VaultSetupScreen` shown exactly once after API key entry, never again once vault is set up
- [ ] After vault setup, `VaultSession.isUnlocked` is true in the same process
- [ ] After app restart (process kill), `UploadWorker.doWork()` auto-unlocks the vault via `DeviceKeyManager.loadMasterKey()`
- [ ] A planted photo produces `storageClass: "encrypted"` on the server (verify via Swagger or PROMPT_LOG test)
- [ ] `GET /api/content/uploads` returns `wrappedThumbnailDek` and `thumbnailDekFormat` for the encrypted upload
- [ ] Garden thumbnails decrypt and display for the freshly-uploaded encrypted photo
- [ ] PhotoDetail full image decrypts and displays for an encrypted photo
- [ ] PhotoDetail encrypted video decrypts to temp file and plays in ExoPlayer
- [ ] Existing `legacy_plaintext` uploads display unchanged in Garden, Explore, and PhotoDetail
- [ ] `VaultCryptoTest` all 14 tests pass on the JVM
- [ ] `./gradlew test` in HeirloomsApp passes (no regressions in existing test suite)
- [ ] APK builds cleanly (`./gradlew assembleDebug`) — no packaging conflicts from BC

---

## Acceptance criteria

1. All 14 `VaultCryptoTest` tests pass.
2. Existing Android unit tests (UploaderTest, ShareViewModelTest, etc.) continue to pass.
3. End-to-end upload: plant a photo → server stores `storage_class='encrypted'` row → Garden
   shows decrypted thumbnail → PhotoDetail shows decrypted full image.
4. Passphrase backup: `GET /api/keys/passphrase` on the live server returns a 200 with
   `wrapFormat: "argon2id-aes256gcm-v1"` after vault setup.
5. Device registration: `GET /api/keys/devices` returns the Android device entry.
6. `legacy_plaintext` photos continue to display in Garden and Explore with no visible
   difference from before E3.

---

## Ship state after E3

v0.28.0. All photos and videos planted from Android are end-to-end encrypted. The server
holds only ciphertext and wrapped keys. Bret's existing `legacy_plaintext` uploads remain
accessible and unmodified. The web client does not yet have a vault; E4 brings the web
client's encrypted upload + passphrase-based unlock flow.
