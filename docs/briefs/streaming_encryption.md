# SE Brief: Streaming encryption for large files

**Date:** 10 May 2026
**Type:** Android + Web. Server changes required (new upload protocol).

---

## Goal

Allow large files (88 MB video and above) to be uploaded from both Android and web without
loading the entire ciphertext into memory. The current approach buffers the whole plaintext,
encrypts it in one shot, then sends the result — which OOMs on large files.

The fix is to encrypt in fixed-size chunks and stream each chunk to the server as a multipart
or resumable upload, so peak memory is bounded to one chunk regardless of file size.

---

## Background and constraints

### Current upload flow (both platforms)

```
1. Read entire file into memory (byte[])
2. Encrypt entire byte[] → ciphertext byte[]           ← OOM here for large files
3. GCS signed-URL PUT of entire ciphertext
```

### Why GCS signed-URL PUT doesn't work for streaming

A single-object GCS PUT requires `Content-Length` upfront. For streaming encryption,
the final ciphertext size is not known until the last chunk is sealed. Options:

- **GCS resumable upload** — initiate session, send chunks of ≥ 256 KB (except last), finalize.
  Works for arbitrary sizes. More complex but GCS-native.
- **Chunked upload through our server** — client sends plaintext chunks to our server, server
  encrypts and streams to GCS. Defeats the purpose of client-side encryption.
- **Pre-compute ciphertext size** — AES-GCM output is `plaintext.size + 16` (tag) per chunk,
  plus per-chunk IV. Total size is predictable. Could use a regular PUT with the correct
  `Content-Length`. But this still requires generating each chunk and buffering at least the
  current chunk.

**Decision: use GCS resumable uploads.** The server initiates the resumable session and
returns the resumable URI to the client. The client encrypts and sends chunks sequentially.
The server is not involved in the data path after handing over the resumable URI.

---

## Encryption scheme for chunked uploads

Streaming AES-256-GCM with independent per-chunk nonces. Each chunk is a separately
authenticated ciphertext segment. A fixed **chunk size of 4 MB** (plaintext) is used.

### Chunk layout (on disk / in GCS)

```
[file_header][chunk_0][chunk_1]...[chunk_N]
```

**File header** (fixed, 4 bytes):

```
[chunk_size: uint32 big-endian]  // always 4 * 1024 * 1024 for current version
```

**Each chunk:**

```
[iv: 12 bytes][ciphertext+tag: chunk_plaintext_len + 16 bytes]
```

The last chunk is smaller (plaintext_len < chunk_size). Tag is always 16 bytes.

Total ciphertext size (predictable before encryption):
```
header_size + sum_over_chunks(12 + chunk_plaintext_len + 16)
= 4 + num_full_chunks * (12 + CHUNK_SIZE + 16) + (12 + remainder + 16)
```

### Nonce construction

Nonces are deterministic: `[upload_id_bytes(4)] [chunk_index: uint64 big-endian]`.
This avoids nonce collision risk across retries (same chunk index always produces same
nonce, which is safe because the content DEK is unique per upload and never reused).

The 4-byte upload ID prefix further reduces cross-upload collision risk.

### AAD (additional authenticated data)

Each chunk's AAD: `[upload_id_bytes][chunk_index: uint64 big-endian]`.
Binds each chunk to its position — prevents reordering attacks.

### Key

Same content DEK as today — a random 256-bit AES-GCM key, wrapped under the master key
and stored in `wrapped_key` alongside the upload. No change to the key management scheme.

---

## Server changes

### New: initiate resumable session

**`POST /api/content/uploads/:uploadId/resumable`**

Called after the existing `/initiate` has reserved the upload slot and returned a content
DEK. The server creates a GCS resumable upload session for the object key and returns the
resumable URI. The client uses this URI directly for all chunk PUTs.

```kotlin
// Pseudo-code — server creates resumable session
val object_key = "uploads/$uploadId"
val total_bytes = request.body["totalBytes"].asLong()
val resumable_uri = gcsClient.initiateResumableUpload(object_key, total_bytes, contentType)
Response(OK).body("""{"resumableUri":"$resumable_uri"}""")
```

The `totalBytes` is supplied by the client (computed from file size using the formula above).
The server trusts it — no validation needed beyond basic sanity (> 0, < some large cap).

### Confirm endpoint — no change

The existing `/confirm` endpoint is called after all chunks are sent (GCS auto-finalises
the object when the last byte arrives). The confirm call is identical to today.

### New Flyway migration: V18

Add `chunked` storage class for uploads created via the streaming path:

```sql
-- No schema change strictly required. The chunk format is transparent to the server.
-- The metadata column `chunk_size` is informational, not operationally required.
-- Optional: ALTER TABLE uploads ADD COLUMN chunk_size INTEGER;
```

**Decision: no V18 needed** — the server stores the object in GCS and returns it.
The client knows whether to stream-decrypt based on the file header (chunk_size field).
The `storage_class` stays `encrypted`. The server is agnostic to chunk vs. monolithic.

### GCS client: add `initiateResumableUpload`

The GCS client already exists. Add one new method:

```kotlin
fun initiateResumableUpload(objectKey: String, totalBytes: Long, contentType: String): String {
    // POST https://storage.googleapis.com/upload/storage/v1/b/{bucket}/o?uploadType=resumable
    // Headers: Authorization, X-Goog-Upload-Header-Content-Length, X-Goog-Upload-Header-Content-Type
    // Response: Location header = resumable session URI
    return response.header("Location")!!
}
```

---

## Android changes

### `UploadWorker.kt`

Currently calls `Uploader.encrypt(bytes)` on the full file, then PUTs. Replace with:

```kotlin
fun encryptAndUploadStreaming(filePath: String, mimeType: String, contentDek: ByteArray, uploadId: String, resumableUri: String, onProgress: (Long) -> Unit)
```

Algorithm:
1. Open `FileInputStream` on the file. Never call `readBytes()`.
2. Compute `totalCiphertextBytes` from `file.length()` and CHUNK_SIZE.
3. Read CHUNK_SIZE bytes at a time into a `ByteArray(CHUNK_SIZE)`.
4. Encrypt the chunk: `AES-GCM(key=contentDek, iv=buildNonce(uploadId, chunkIndex), aad=buildAad(uploadId, chunkIndex), plaintext=chunk)`.
5. Append `[iv][ciphertext+tag]` to the chunk buffer.
6. PUT the chunk to the GCS resumable URI with correct `Content-Range: bytes START-END/TOTAL`.
7. Repeat until EOF. Call `onProgress` after each chunk.

Write the file header (4 bytes) as the first bytes of the first PUT, prepended to chunk 0.

### `Uploader.kt`

Keep the existing `encrypt(bytes)` path for files below a threshold (e.g. 10 MB).
Add the streaming path for larger files. The threshold avoids adding HTTP round-trips
for small files.

### `UploadProgressViewModel.kt` / `UploadProgressScreen.kt`

WorkManager already passes progress via `setProgress`. Streaming adds natural per-chunk
progress updates. No UI changes needed — existing progress bar already handles it.

---

## Web changes

### `api.js`

The existing `putBlobWithProgress(signedUrl, bytes, onProgress)` uses XHR with a regular PUT.
Replace the large-file path with a chunked approach:

```javascript
async function encryptAndUploadStreaming(file, contentDek, uploadId, resumableUri, onProgress) {
    const CHUNK_SIZE = 4 * 1024 * 1024
    const totalCiphertextBytes = computeTotalCiphertextSize(file.size, CHUNK_SIZE)
    let offset = 0
    let chunkIndex = 0
    let ciphertextOffset = 0

    // Write file header (4 bytes) prepended to first chunk's PUT
    const header = new Uint8Array(4)
    new DataView(header.buffer).setUint32(0, CHUNK_SIZE, false)  // big-endian

    while (offset < file.size) {
        const slice = file.slice(offset, offset + CHUNK_SIZE)
        const plaintext = new Uint8Array(await slice.arrayBuffer())
        const iv = buildNonce(uploadId, chunkIndex)
        const aad = buildAad(uploadId, chunkIndex)
        const ciphertext = await encryptChunk(contentDek, iv, aad, plaintext)

        const chunk = chunkIndex === 0
            ? concat(header, iv, ciphertext)
            : concat(iv, ciphertext)

        const chunkCiphertextLen = chunk.byteLength
        await putResumableChunk(resumableUri, chunk, ciphertextOffset, totalCiphertextBytes)
        ciphertextOffset += chunkCiphertextLen
        offset += plaintext.byteLength
        chunkIndex++
        onProgress(offset)
    }
}
```

`encryptChunk` uses `crypto.subtle.encrypt` with the existing WebCrypto wiring.

### Threshold

Use streaming for files > 10 MB. Below that, continue with the existing single-buffer path
to avoid extra HTTP round-trips.

---

## Decryption (read path)

The existing `decryptSymmetric` is called on the entire object today. For chunked objects,
the client detects the chunk format by reading the 4-byte header:

```
if (bytes[0..3] == CHUNK_MAGIC) → streaming decrypt
else → legacy single-block decrypt
```

The `CHUNK_MAGIC` value is the chunk_size itself (e.g. `0x00400000` for 4 MB). Since a
legacy ciphertext begins with a 12-byte IV (random bytes), a leading `0x00400000` value is
astronomically unlikely to occur naturally — this doubles as a format discriminator.

Streaming decrypt: read 4-byte header to get chunk_size, then loop reading
`12 + chunk_size + 16` byte segments, decrypt each, concatenate plaintext.

The decrypt path is needed in:
- **Android**: `PhotoDetailScreen` / `GardenViewModel` (thumbnail fetch) — thumbnails are
  tiny (< 10 MB), so chunked thumbnails won't exist. Full-size file download for video
  streaming would need chunked decrypt. This is a read-path concern and can be deferred.
- **Web**: `decryptAndDisplay` in `UploadThumb.jsx` and `GardenPage.jsx`.

**Decision: defer read-path streaming decrypt.** Thumbnails are always small. Full-size
video streaming already deferred. The write path is the blocker — implement that first.
The legacy decrypt path will still work for all existing uploads. New large uploads will
play back once we add the read path in a follow-up.

---

## Acceptance criteria

1. An 88 MB video uploaded from Android completes without OOM or crash.
2. An 88 MB video uploaded from the web completes without OOM or crash.
3. Peak memory during upload is bounded to ~2× CHUNK_SIZE (~8 MB) not the file size.
4. Upload progress updates per chunk (not just 0% → 100%).
5. Small files (< 10 MB) continue to use the existing single-buffer path unchanged.
6. All existing integration tests pass.
7. A small file uploaded before this change and a large file uploaded after both appear
   in the Garden (legacy decrypt still works; new chunked file decrypt deferred).

---

## Out of scope (this increment)

- Streaming **decrypt** / playback of large videos (deferred — separate brief needed).
- Resume of interrupted uploads (GCS resumable supports it but requires state persistence).
- Parallel chunk uploads (sequential is simpler and sufficient for now).
- Server-side chunk validation beyond what GCS provides.
