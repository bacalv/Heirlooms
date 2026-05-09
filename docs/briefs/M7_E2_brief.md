# SE Brief: M7 E2 — Backend API for E2EE

**Date:** 9 May 2026
**Milestone:** M7 — Vault E2EE
**Increment:** E2 of 5
**Type:** Backend-only. No client changes, no UI work.

---

## Goal

Wire the E1 schema foundations into working API endpoints. After E2, a correctly
implemented E3 Android client can perform a full encrypted upload, read it back,
and migrate a legacy upload — all against the live production server. Nothing user-
visible changes for the current app (v0.26.0).

---

## New migration: V15

One new table for the device-link state machine.

```sql
-- V15__m7_device_links.sql

CREATE TABLE pending_device_links (
    id                  UUID PRIMARY KEY,
    one_time_code       TEXT NOT NULL UNIQUE,        -- shown on trusted device, typed on new device
    expires_at          TIMESTAMPTZ NOT NULL,         -- link expires after 15 minutes
    state               TEXT NOT NULL DEFAULT 'initiated'
                            CHECK (state IN ('initiated', 'device_registered', 'wrap_complete')),
    -- Filled in when new device submits its pubkey (step 2 of link flow):
    new_device_id       TEXT NULL,
    new_device_label    TEXT NULL,
    new_device_kind     TEXT NULL CHECK (new_device_kind IN ('android', 'web', 'ios')),
    new_pubkey_format   TEXT NULL,
    new_pubkey          BYTEA NULL,
    -- Filled in when trusted device posts the wrapped master key (step 4):
    wrapped_master_key  BYTEA NULL,
    wrap_format         TEXT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pending_device_links_code ON pending_device_links (one_time_code)
    WHERE state = 'initiated';
```

---

## Domain model changes

### `UploadRecord` — add E2EE fields

```kotlin
data class UploadRecord(
    // ... existing fields ...
    val storageClass: String = "legacy_plaintext",
    val envelopeVersion: Int? = null,
    val wrappedDek: ByteArray? = null,
    val dekFormat: String? = null,
    val encryptedMetadata: ByteArray? = null,
    val encryptedMetadataFormat: String? = null,
    val thumbnailStorageKey: String? = null,
    val wrappedThumbnailDek: ByteArray? = null,
    val thumbnailDekFormat: String? = null,
)
```

All new fields default to values appropriate for `legacy_plaintext` (i.e. `storageClass =
"legacy_plaintext"`, all others null).

### New record types

```kotlin
data class WrappedKeyRecord(
    val id: UUID,
    val deviceId: String,
    val deviceLabel: String,
    val deviceKind: String,
    val pubkeyFormat: String,
    val pubkey: ByteArray,
    val wrappedMasterKey: ByteArray,
    val wrapFormat: String,
    val createdAt: Instant,
    val lastUsedAt: Instant,
    val retiredAt: Instant?,
)

data class RecoveryPassphraseRecord(
    val wrappedMasterKey: ByteArray,
    val wrapFormat: String,
    val argon2Params: String,    // raw JSON, stored and returned as-is
    val salt: ByteArray,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class PendingDeviceLinkRecord(
    val id: UUID,
    val oneTimeCode: String,
    val expiresAt: Instant,
    val state: String,
    val newDeviceId: String?,
    val newDeviceLabel: String?,
    val newDeviceKind: String?,
    val newPubkeyFormat: String?,
    val newPubkey: ByteArray?,
    val wrappedMasterKey: ByteArray?,
    val wrapFormat: String?,
)
```

### Binary fields in JSON: Base64

All `ByteArray` fields (wrapped keys, DEKs, encrypted blobs, pubkeys) are serialized in
JSON as standard Base64 strings (RFC 4648, with padding). The key name in JSON uses the
same camelCase as the Kotlin field. Clients must Base64-decode before use.

Example upload object for an encrypted row:

```json
{
  "id": "...",
  "storageKey": "uploads/abc.bin",
  "storageClass": "encrypted",
  "envelopeVersion": 1,
  "wrappedDek": "<base64>",
  "dekFormat": "master-aes256gcm-v1",
  "encryptedMetadata": "<base64>",
  "encryptedMetadataFormat": "aes256gcm-v1",
  "thumbnailStorageKey": "uploads/abc-thumb.bin",
  "wrappedThumbnailDek": "<base64>",
  "thumbnailDekFormat": "master-aes256gcm-v1",
  "mimeType": "image/jpeg",
  "fileSize": 2048000,
  "uploadedAt": "2026-05-09T17:56:04Z",
  "takenAt": "2026-05-09T17:56:04Z",
  "rotation": 0,
  "thumbnailKey": null,
  "tags": [],
  "compostedAt": null
}
```

For `legacy_plaintext` rows, `storageClass` is `"legacy_plaintext"` and all E2EE fields
are omitted (not present in the JSON). Clients must treat absent E2EE fields as null.

---

## Database operations needed

All SELECT queries that project upload columns need updating to include the new E2EE
fields. `toUploadRecord()` and `toJson()` both need extending. The full list of
affected queries:

- `findByContentHash` — add new columns to SELECT
- `getUploadById` — add new columns to SELECT
- `listUploadsPaginated` — add new columns to SELECT
- `listCompostedUploadsPaginated` — add new columns to SELECT
- `listUploadsByPlot` (if it exists separately) — add new columns
- `recordUpload` — INSERT now includes `storage_class`, `envelope_version`, `wrapped_dek`,
  `dek_format`, `encrypted_metadata`, `encrypted_metadata_format`, `thumbnail_storage_key`,
  `wrapped_thumbnail_dek`, `thumbnail_dek_format`

**Dedup guard:** in `recordUpload` / `confirmUploadHandler`, skip `findByContentHash` when
`storageClass == "encrypted"`. Ciphertext hashes are non-deterministic; the guard provides
no value and would incorrectly block re-uploads. For `legacy_plaintext`, dedup stays.

New DB operations:

```kotlin
// pending_blobs
fun insertPendingBlob(storageKey: String): UUID
fun deletePendingBlob(storageKey: String)
fun deleteStalePendingBlobs(olderThan: Instant): List<String>  // returns storage keys deleted

// wrapped_keys
fun insertWrappedKey(record: WrappedKeyRecord)
fun listWrappedKeys(includeRetired: Boolean = false): List<WrappedKeyRecord>
fun getWrappedKeyByDeviceId(deviceId: String): WrappedKeyRecord?
fun retireWrappedKey(id: UUID, retiredAt: Instant = Instant.now())
fun touchWrappedKey(id: UUID)                                   // updates last_used_at
fun retireDormantWrappedKeys(dormantBefore: Instant): Int       // retires > 180d last_used_at

// recovery passphrase
fun getRecoveryPassphrase(): RecoveryPassphraseRecord?
fun upsertRecoveryPassphrase(record: RecoveryPassphraseRecord)
fun deleteRecoveryPassphrase()

// device links
fun insertPendingDeviceLink(record: PendingDeviceLinkRecord)
fun getPendingDeviceLink(id: UUID): PendingDeviceLinkRecord?
fun getPendingDeviceLinkByCode(code: String): PendingDeviceLinkRecord?
fun registerNewDevice(id: UUID, deviceId: String, deviceLabel: String,
                      deviceKind: String, pubkeyFormat: String, pubkey: ByteArray)
fun completeDeviceLink(id: UUID, wrappedMasterKey: ByteArray, wrapFormat: String,
                       deviceId: String, deviceLabel: String, deviceKind: String,
                       pubkeyFormat: String, pubkey: ByteArray)
fun deleteExpiredDeviceLinks(before: Instant): Int

// upload migration
fun migrateUploadToEncrypted(id: UUID, newStorageKey: String, record: UploadRecord): Boolean
```

---

## API changes

### New endpoint: `POST /api/content/uploads/initiate`

Replaces `prepare` as the upload entry point for E3+ clients. `prepare` stays unchanged
for the existing app during the E2-E3 transition.

**Request body:**
```json
{
  "mimeType": "image/jpeg",
  "storage_class": "encrypted"
}
```

Also accepts the legacy un-classed shape `{"mimeType": "..."}` (no `storage_class`),
treating it as `legacy_plaintext` for backward compat. This shim is removed at E5.

**Response for `storage_class="encrypted"`:**
```json
{
  "storageKey": "uploads/<uuid>.bin",
  "uploadUrl": "<signed GCS URL for ciphertext>",
  "thumbnailStorageKey": "uploads/<uuid>-thumb.bin",
  "thumbnailUploadUrl": "<signed GCS URL for encrypted thumbnail>"
}
```

**Response for legacy shape:**
```json
{
  "storageKey": "uploads/<uuid>.bin",
  "uploadUrl": "<signed GCS URL>"
}
```

**Rejects:**
- `storage_class="public"` → 400 with body:
  `{"error":"public storage class is not yet supported"}`
- `storage_class="legacy_plaintext"` (explicit) → 400
- Missing or blank `mimeType` → 400

**Side effect:** inserts a `pending_blobs` row for each storage key returned (one for
legacy, two for encrypted). Rows are deleted when `confirm` or `migrate` succeeds.

---

### Modified endpoint: `POST /api/content/uploads/confirm`

Extended to accept E2EE fields for encrypted uploads. Backward-compatible: the existing
shape (no `storage_class`, no E2EE fields) still works and creates a `legacy_plaintext`
row.

**Request body for encrypted uploads:**
```json
{
  "storageKey": "uploads/<uuid>.bin",
  "mimeType": "image/jpeg",
  "fileSize": 2048000,
  "contentHash": "<sha256 hex of ciphertext bytes>",
  "storage_class": "encrypted",
  "envelopeVersion": 1,
  "wrappedDek": "<base64>",
  "dekFormat": "master-aes256gcm-v1",
  "encryptedMetadata": "<base64>",
  "encryptedMetadataFormat": "aes256gcm-v1",
  "thumbnailStorageKey": "uploads/<uuid>-thumb.bin",
  "wrappedThumbnailDek": "<base64>",
  "thumbnailDekFormat": "master-aes256gcm-v1",
  "takenAt": "2026-05-09T17:56:04Z",
  "tags": []
}
```

**Server validation for encrypted confirms:**
1. `storage_class` is `"encrypted"`.
2. `wrappedDek`, `dekFormat`, `envelopeVersion` are present.
3. `EnvelopeFormat.validateSymmetric(wrappedDek, dekFormat)` passes.
4. If `encryptedMetadata` is present: `EnvelopeFormat.validateSymmetric(encryptedMetadata,
   encryptedMetadataFormat)` passes.
5. If `wrappedThumbnailDek` is present: `EnvelopeFormat.validateSymmetric(wrappedThumbnailDek,
   thumbnailDekFormat)` passes. Thumbnail is optional — a valid encrypted upload may have no
   thumbnail (e.g., for video formats where thumbnail generation is deferred to E3).
6. Dedup guard (`findByContentHash`) is skipped.
7. On success: delete `pending_blobs` rows for `storageKey` and `thumbnailStorageKey`.

**Server validation for legacy (un-classed or explicit `legacy_plaintext`):**
- Existing behaviour unchanged. Thumbnail and EXIF extraction run server-side as today.
- `findByContentHash` dedup guard applies.

**Rejects:**
- `storage_class="public"` → 400
- `storage_class="encrypted"` with missing or invalid envelope fields → 400 with specific
  message identifying which field failed and why.

---

### Modified endpoints: upload list and detail

All endpoints that return upload objects (`GET /uploads`, `GET /uploads/{id}`, capsule
detail) must include the E2EE fields in the response for encrypted rows.

`takenAt`, `latitude`, `longitude`, `altitude`, `deviceMake`, `deviceModel` remain
in the response for `legacy_plaintext` rows (unchanged). For `encrypted` rows, these
plaintext EXIF fields are `null` server-side (the client holds them in the encrypted
metadata blob). The server should not omit them — return `null` explicitly.

The `thumbnailKey` field retains its existing semantics for `legacy_plaintext` rows.
For `encrypted` rows, `thumbnailKey` is `null` and `thumbnailStorageKey` carries the
encrypted thumbnail's GCS key instead.

**Read flow for file and thumbnail:**

`GET /uploads/{id}/file` and `GET /uploads/{id}/thumb` continue to stream whatever bytes
are at the stored key — ciphertext for encrypted rows, plaintext for legacy. The client
dispatches based on `storageClass`. No change needed to the proxy route logic itself,
but `thumbProxyContractRoute` must check `thumbnailStorageKey` for encrypted rows:

```
if storageClass == "encrypted": serve thumbnailStorageKey (ciphertext)
if storageClass == "legacy_plaintext": serve thumbnailKey (plaintext) as today
```

---

### New endpoint: `POST /api/content/uploads/{id}/migrate`

Atomically replaces a `legacy_plaintext` upload's bytes with the encrypted equivalent.

**Request body:**
```json
{
  "newStorageKey": "uploads/<new-uuid>.bin",
  "contentHash": "<sha256 hex of new ciphertext bytes>",
  "envelopeVersion": 1,
  "wrappedDek": "<base64>",
  "dekFormat": "master-aes256gcm-v1",
  "encryptedMetadata": "<base64>",
  "encryptedMetadataFormat": "aes256gcm-v1",
  "thumbnailStorageKey": "uploads/<new-uuid>-thumb.bin",
  "wrappedThumbnailDek": "<base64>",
  "thumbnailDekFormat": "master-aes256gcm-v1"
}
```

**Server steps:**
1. Fetch the upload; return 404 if not found.
2. Return 409 if `storage_class != "legacy_plaintext"` (already migrated or wrong class).
3. Validate all envelope fields as in `confirm` (step 3 above). Return 400 on failure.
4. Atomically in a single DB transaction:
   a. Update the upload row: `storage_class = 'encrypted'`, fill all E2EE columns, set
      `storage_key = newStorageKey`, update `content_hash` to the ciphertext hash.
      Null out `latitude`, `longitude`, `altitude`, `device_make`, `device_model` — these
      move into the encrypted metadata blob.
   b. Delete the old plaintext blob from GCS (`storage.delete(old storage_key)`).
   c. Delete the old plaintext thumbnail blob from GCS if `thumbnail_key` was set.
   d. Null out `thumbnail_key` on the row (plaintext thumbnail superseded by encrypted one).
5. Delete `pending_blobs` rows for `newStorageKey` and `thumbnailStorageKey`.
6. Return 200 with the updated upload object.

**Failure handling:** if any step before the transaction fails, the row stays
`legacy_plaintext` and the new ciphertext blob is orphaned in GCS (the `pending_blobs`
entry covers it for cleanup). If the GCS delete in step 4b fails after the DB commit,
log a warning — the orphan will be caught by a future GCS audit. Do not roll back the
DB update over a GCS failure.

**Idempotency:** the endpoint is safely retryable if called before step 4 commits.
After step 4 commits, a second call returns 409.

---

### New endpoints: key management (`/api/keys/`)

New handler file: `KeysHandler.kt`. All endpoints in this section are part of the
`/api/keys` contract block.

#### Device registration

```
POST /api/keys/devices
```
Register a new device's public key. Stores a `wrapped_keys` row.

**Request:**
```json
{
  "deviceId": "<client-generated UUID>",
  "deviceLabel": "Pixel 8",
  "deviceKind": "android",
  "pubkeyFormat": "p256-spki",
  "pubkey": "<base64>",
  "wrappedMasterKey": "<base64>",
  "wrapFormat": "p256-ecdh-hkdf-aes256gcm-v1"
}
```

Validates: `deviceKind` is one of `android`, `web`, `ios`; `deviceId` is not already
registered (return 409 if it is); `pubkey` is non-empty.

**Response:** 201 with the created `WrappedKeyRecord` as JSON.

```
GET /api/keys/devices
```
List all `wrapped_keys` rows where `retired_at IS NULL`.

```
DELETE /api/keys/devices/{deviceId}
```
Soft-retire a device (sets `retired_at = NOW()`). Returns 404 if not found, 409 if
already retired.

```
PATCH /api/keys/devices/{deviceId}/used
```
Updates `last_used_at = NOW()`. Called by the client on each authenticated session.
Returns 204. Returns 404 if device is not found or is retired.

#### Passphrase-wrapped backup

```
GET /api/keys/passphrase
```
Returns the `recovery_passphrase` row as JSON, or 404 if none exists. Response:
```json
{
  "wrappedMasterKey": "<base64>",
  "wrapFormat": "argon2id-aes256gcm-v1",
  "argon2Params": {"m": 65536, "t": 3, "p": 1},
  "salt": "<base64>",
  "createdAt": "...",
  "updatedAt": "..."
}
```

```
PUT /api/keys/passphrase
```
Creates or replaces the `recovery_passphrase` row. Body mirrors the GET response
(minus `createdAt`/`updatedAt`). Returns 200 with the updated record.

```
DELETE /api/keys/passphrase
```
Deletes the row. Returns 204. Returns 404 if none exists.

#### Device-link flow

The link flow is a three-step polling handshake. The trusted device (Android) must be
running and foregrounded throughout — the wrap operation happens on-device.

```
POST /api/keys/link/initiate
```
Trusted device starts a link session.

**Request:** empty body (or `{}`).

**Response:**
```json
{
  "linkId": "<uuid>",
  "code": "HXYZ-7342"
}
```

The code is 8 random alphanumeric characters (uppercase, no ambiguous chars), hyphen-
separated in the middle. It expires in 15 minutes. A new `pending_device_links` row is
created with `state = "initiated"`.

```
POST /api/keys/link/{linkId}/register
```
New device submits its public key after the user types the code.

**Request:**
```json
{
  "code": "HXYZ-7342",
  "deviceId": "<client-generated UUID>",
  "deviceLabel": "Chrome on MacBook",
  "deviceKind": "web",
  "pubkeyFormat": "p256-spki",
  "pubkey": "<base64>"
}
```

Returns 400 if code doesn't match `linkId`, 410 if expired, 409 if already registered.
On success: updates `pending_device_links` to `state = "device_registered"`, stores the
new device's pubkey. Returns 202.

```
GET /api/keys/link/{linkId}/status
```
Polling endpoint. Both trusted device and new device call this.

**Response:**
```json
{
  "state": "device_registered",
  "newDeviceKind": "web",
  "newPubkeyFormat": "p256-spki",
  "newPubkey": "<base64>",
  "wrappedMasterKey": null,
  "wrapFormat": null
}
```

`newPubkey` and `newPubkey*` fields are included only when `state = "device_registered"`
or later. `wrappedMasterKey` is included only when `state = "wrap_complete"`.

Returns 404 if `linkId` is unknown. Returns 410 if expired and not yet complete.

```
POST /api/keys/link/{linkId}/wrap
```
Trusted device posts the wrapped master key after retrieving the new device's pubkey.

**Request:**
```json
{
  "wrappedMasterKey": "<base64>",
  "wrapFormat": "p256-ecdh-hkdf-aes256gcm-v1"
}
```

Server atomically:
1. Updates `pending_device_links` to `state = "wrap_complete"`.
2. Inserts a `wrapped_keys` row for the new device (using `new_device_*` fields from the
   link row).

Returns 409 if state is not `device_registered`. Returns 410 if expired. Returns 201 on
success with the new `WrappedKeyRecord`.

---

## Background jobs

### `PendingBlobsCleanupService`

New service class, analogous to `ExifExtractionService`. Uses a `CoroutineScope` with
`Dispatchers.IO + SupervisorJob()`.

On server startup: scans `pending_blobs` for rows with `created_at < NOW() - INTERVAL
'24 hours'`, deletes the GCS objects, then deletes the rows. Runs again every 6 hours.

```kotlin
class PendingBlobsCleanupService(
    private val database: Database,
    private val storage: FileStore,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) {
    fun startPeriodicCleanup()     // called from server startup; runs immediately then every 6h
    private suspend fun runCleanup()
}
```

### Dormant device pruning

On server startup and every 24 hours: call `database.retireDormantWrappedKeys(dormantBefore =
Instant.now().minus(180, ChronoUnit.DAYS))`. Log the count of retired rows. No UI; device
management UI is M8.

Add this to `PendingBlobsCleanupService` as a second periodic task, or as a separate
`KeysMaintenanceService` if it grows. Keep it simple for E2.

### Compost cleanup fix

`launchCompostCleanup` in `UploadHandler.kt` currently only deletes `thumbnailKey`. For
encrypted uploads, it must also delete `thumbnailStorageKey`. Update the existing cleanup
thread:

```kotlin
// After deleting thumbnailKey blob (if present):
if (record.thumbnailStorageKey != null) {
    try { storage.delete(StorageKey(record.thumbnailStorageKey)) } catch (e: Exception) {
        println("[compost-cleanup] WARNING: failed to delete encrypted thumbnail ${record.thumbnailStorageKey}: ${e.message}")
    }
}
```

---

## Test plan

**~45 tests total.** Split: ~25 unit tests in `HeirloomsServer/src/test/`, ~20 integration
tests in `HeirloomsTest/`.

### Unit tests (`HeirloomsServer/src/test/`)

Add `KeysHandlerTest.kt`:

1. Register device → 201, row persisted
2. Register duplicate deviceId → 409
3. Register with invalid deviceKind → 400
4. List devices → returns active rows only
5. Retire device → 204, then list excludes it
6. Retire already-retired → 409
7. Patch used → 204, updates last_used_at
8. Patch used for unknown device → 404
9. Get passphrase → 200 with record
10. Get passphrase when none → 404
11. Put passphrase (create) → 200
12. Put passphrase (replace) → 200, updatedAt changes
13. Delete passphrase → 204, subsequent get → 404

Extend `UploadHandlerTest.kt`:

14. `POST /initiate` with `storage_class="encrypted"` → 200, two signed URLs returned
15. `POST /initiate` with `storage_class="public"` → 400
16. `POST /initiate` with legacy body (no storage_class) → 200, single signed URL
17. `POST /confirm` encrypted with valid envelopes → 201
18. `POST /confirm` encrypted with invalid wrapped_dek envelope → 400
19. `POST /confirm` encrypted with missing wrappedDek → 400
20. `POST /confirm` legacy body → 201, behaves as today
21. Confirm encrypted skips dedup check (mock verifies findByContentHash not called)
22. `GET /uploads/{id}` for encrypted row → includes all E2EE fields, Base64 encoded
23. `GET /uploads/{id}` for legacy row → no E2EE fields in response
24. `GET /uploads` list → storageClass field present on all items
25. `GET /uploads/{id}/thumb` for encrypted → serves thumbnailStorageKey bytes

### Integration tests (`HeirloomsTest/`)

Add BouncyCastle to `HeirloomsTest/build.gradle.kts`:
```kotlin
testImplementation("org.bouncycastle:bcprov-jdk18on:1.79")
```

New test class `E2EncryptedUploadTest.kt`:

26. **Full encrypted upload round-trip.** BouncyCastle: generate master key + DEK, encrypt
    128-byte payload, wrap DEK under master key. `initiate` → PUT ciphertext to signed URL
    → `confirm` with all E2EE fields. `GET /uploads/{id}` → assert `storageClass =
    "encrypted"`, `wrappedDek` matches. `GET /uploads/{id}/file` → assert bytes match
    ciphertext. Decode `wrappedDek` from Base64, unwrap DEK with BouncyCastle, decrypt
    file bytes, assert plaintext matches original. This is the canary test.

27. **Legacy upload round-trip** (unchanged). POST to `/upload` → list → detail → file
    download. Assert `storageClass = "legacy_plaintext"`, no E2EE fields, plaintext bytes
    match. Regression guard for backward compat.

28. **Mixed list.** Upload one encrypted + one legacy. `GET /uploads` → assert both appear
    with correct `storageClass` values.

29. **Migration: legacy → encrypted.** Upload legacy. Call `initiate` to get signed URLs.
    Download legacy bytes. Encrypt with BouncyCastle. PUT ciphertext. Call `migrate`.
    Assert response `storageClass = "encrypted"`. `GET /uploads/{id}/file` → decrypt and
    assert plaintext matches original. Assert old plaintext key no longer exists in GCS
    (HEAD request returns 404).

30. **Migration idempotency.** Call `migrate` on an already-encrypted row → assert 409.

31. **Migration retry safety.** Call `migrate` but return an error before DB commit (inject
    a bad newStorageKey). Assert row is still `legacy_plaintext`. Assert `pending_blobs`
    row exists for the orphaned blob.

32. **`initiate` public rejects.** `POST /initiate` with `storage_class="public"` → 400.

33. **`confirm` with invalid envelope.** `initiate` encrypted, PUT a real blob, but in
    `confirm` send a malformed `wrappedDek` (wrong version byte) → 400.

34. **`pending_blobs` cleanup.** `initiate` encrypted, do NOT call `confirm`. Advance
    time past 24h (or directly insert a stale `pending_blobs` row). Trigger cleanup.
    Assert blob deleted from GCS and row removed from DB.

35. **Device link flow happy path.** `initiate` link. `register` with new device pubkey.
    Poll `status` → state `device_registered`, pubkey returned. `wrap` with BouncyCastle-
    generated wrapped master key. Poll `status` → state `wrap_complete`, `wrappedMasterKey`
    returned. Assert `wrapped_keys` row created.

36. **Link: wrong code → 400.** `register` with incorrect code → 400.

37. **Link: expired → 410.** Insert a link row with `expires_at` in the past. `register`
    → 410.

38. **Link: double wrap → 409.** `wrap` twice → second call returns 409.

39. **Dormant device pruning.** Insert a `wrapped_keys` row with `last_used_at` 181 days
    ago. Run pruning. Assert row `retired_at` is set.

40. **Compost cleanup deletes encrypted thumbnail.** Upload encrypted upload with thumbnail.
    Compost it. Wait for cleanup. Assert `thumbnail_storage_key` blob is deleted from GCS.

---

## New files

- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/KeysHandler.kt`
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/PendingBlobsCleanupService.kt`
- `HeirloomsServer/src/main/resources/db/migration/V15__m7_device_links.sql`
- `HeirloomsServer/src/test/kotlin/digital/heirlooms/server/KeysHandlerTest.kt`
- `HeirloomsTest/src/test/kotlin/digital/heirlooms/test/e2ee/E2EncryptedUploadTest.kt`

---

## Modified files

- `Database.kt` — new record types, extended `UploadRecord`, new DB operations, updated
  SELECT queries + `toUploadRecord()`, updated `toJson()`
- `UploadHandler.kt` — new `initiate` endpoint, extended `confirm`, new `migrate` endpoint,
  updated `thumbProxyContractRoute`, updated `launchCompostCleanup`
- `Main.kt` (or wherever the server is wired up) — register `KeysHandler` routes, start
  `PendingBlobsCleanupService`
- `buildApp()` in `UploadHandler.kt` — add new contract routes
- `HeirloomsServer/build.gradle.kts` — no new dependencies needed for production;
  BouncyCastle already in testImplementation from E1
- `HeirloomsTest/build.gradle.kts` — add BouncyCastle to testImplementation

---

## Wire-up checklist

Before accepting E2 as done:

- [ ] `POST /api/content/uploads/initiate` creates two `pending_blobs` rows for encrypted,
      one for legacy
- [ ] `POST /api/content/uploads/confirm` deletes `pending_blobs` rows on success
- [ ] `POST /api/content/uploads/{id}/migrate` atomically swaps bytes + deletes old blobs
- [ ] `GET /uploads` and `GET /uploads/{id}` include `storageClass` for all rows
- [ ] `GET /uploads/{id}/file` and `/thumb` work for both storage classes
- [ ] `KeysHandler` routes registered in `buildApp()`
- [ ] `PendingBlobsCleanupService.startPeriodicCleanup()` called on server startup
- [ ] Dormant device pruning runs on startup
- [ ] Compost cleanup deletes `thumbnailStorageKey` for encrypted uploads
- [ ] `POST /initiate` and `confirm` reject `storage_class="public"` with 400
- [ ] OpenAPI spec generates cleanly (run the spec-endpoint test)

---

## Acceptance criteria

1. All 45+ new tests pass alongside existing 192 tests.
2. Cross-platform canary (test 26): BouncyCastle encrypts, server stores ciphertext, 
   BouncyCastle decrypts after reading back — plaintext matches byte-for-byte. This test
   is a release-blocker if it fails.
3. Migration canary (test 29): legacy upload migrated to encrypted, old plaintext blob
   gone from GCS, decrypted content matches original.
4. Legacy round-trip (test 27) continues to pass — backward compat intact.
5. Swagger/OpenAPI spec endpoint returns 200 with valid JSON.

---

## Ship state after E2

v0.27.0. Server ready for E2EE clients. Existing app (v0.26.0) continues to work unchanged —
it calls `prepare`/`confirm` which still function as `legacy_plaintext` paths. E3 Android
work starts once E2 is green.
