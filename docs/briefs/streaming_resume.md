# SE Brief: Resume interrupted streaming uploads (Android)

**Date:** 10 May 2026
**Type:** Android only. No server changes.

---

## Goal

When a streaming encrypted upload is interrupted (network drop, process kill,
WorkManager retry), the worker should resume the GCS resumable session from
where it left off rather than re-uploading from chunk 0.

Currently the worker restarts from scratch on every retry, which means a 91%
upload that fails wastes all prior bandwidth on the next attempt.

---

## Background

GCS resumable sessions are valid for up to 7 days from their last activity.
A session can be queried at any point to find the last byte GCS received:

```
PUT {resumableUri}
Content-Range: bytes */{totalCiphertextBytes}
→ 308 Resume Incomplete
   Range: bytes=0-{lastByteReceived}   ← offset of last confirmed byte
```

If the session has expired, GCS returns 404 or 410, and the upload must
restart from scratch.

The existing `UploadWorker` retries up to `MAX_ATTEMPTS` times via WorkManager's
backoff mechanism. Each retry calls `uploadEncryptedViaSigned`, which currently
re-runs the full flow: `/initiate` → `/resumable` → all chunks from 0.

---

## Design

### Checkpoint file

After `/initiate` and `/resumable` succeed (i.e., once the GCS session is
established and we hold all the keys), write a checkpoint file to `cacheDir`:

```
{mediaFile.nameWithoutExtension}.upload_checkpoint.json
```

Contents (JSON):

```json
{
  "storageKey": "...",
  "thumbnailStorageKey": "...",
  "thumbnailUploadUrl": "...",
  "wrappedContentDekB64": "...",
  "wrappedThumbnailDekB64": "...",
  "resumableUri": "...",
  "totalCiphertextBytes": 104858328,
  "mimeType": "video/mp4",
  "takenAt": "2026-05-10T16:30:00Z",
  "tags": []
}
```

**No plaintext DEKs are written to disk.** On resume, the wrapped DEKs are
re-unwrapped using the master key (same path as the existing vault auto-unlock
in `UploadWorker`).

The thumbnail signed URL has a 15-minute expiry. If the thumbnail hasn't been
uploaded yet when we resume, we may need to re-initiate just the thumbnail URL.
See "Thumbnail handling" below.

### Resume flow (on each retry attempt)

```
1. Look for checkpoint file alongside the media file.

2a. No checkpoint → run the full flow (existing behaviour):
      /initiate → /resumable → upload chunks from 0 → write checkpoint after step 1+2

2b. Checkpoint found → resume:
      a. Query GCS for session status (PUT with Content-Range: bytes */total).
      b. GCS 404/410 → delete checkpoint, fall through to full restart (2a).
      c. GCS 308 with Range header → parse lastByteReceived.
         Compute resumeChunkIndex = lastByteReceived / CIPHERTEXT_CHUNK_SIZE
         (each full chunk is exactly 4194304 bytes; any partial chunk at the
         end was not confirmed by GCS and must be re-sent).
         Set ciphertextOffset = resumeChunkIndex * CIPHERTEXT_CHUNK_SIZE.
         Seek the media file to resumeChunkIndex * CHUNK_SIZE (plaintext offset).
         Resume the encryption loop from that position.
      d. GCS 200/201 → upload was actually complete; call /confirm and finish.

3. On successful upload completion → delete checkpoint.
4. On final failure (runAttemptCount >= MAX_ATTEMPTS - 1) → delete checkpoint.
```

### Thumbnail handling

The thumbnail signed URL in the checkpoint expires in 15 minutes. Two options:

- **Option A (simple):** Always re-upload the thumbnail on resume regardless
  of whether it was already uploaded. The thumbnail PUT to a signed URL is
  idempotent — GCS will just overwrite with the same bytes. No special tracking
  needed.
- **Option B:** Store a `thumbnailUploaded: Boolean` flag in the checkpoint.

**Decision: Option A.** Thumbnails are < 1 MB. Re-uploading is negligible cost
and avoids tracking another state flag. If the thumbnail signed URL has expired
(15 min), we need a new one — add a `/api/content/uploads/thumbnail-url` endpoint
or just restart fresh for that edge case. Given the thumbnail is tiny and fast,
the simplest path is: if thumbnail PUT fails with 4xx, fall back to a full restart.

### Chunk index and file seek

The media file is in `cacheDir` and is not modified. To seek to the right
plaintext position:

```kotlin
val resumeChunkIndex = lastByteReceived / CIPHERTEXT_CHUNK_SIZE
val plaintextOffset = resumeChunkIndex.toLong() * CHUNK_SIZE
```

`FileInputStream` supports skipping:

```kotlin
file.inputStream().use { input ->
    var skipped = 0L
    while (skipped < plaintextOffset) {
        val n = input.skip(plaintextOffset - skipped)
        if (n <= 0) break
        skipped += n
    }
    // then run the chunk loop from resumeChunkIndex
}
```

The `ciphertextOffset` and `chunkIndex` in the loop start at
`resumeChunkIndex * CIPHERTEXT_CHUNK_SIZE` and `resumeChunkIndex` respectively.

### Constants

```kotlin
const val CHUNK_SIZE = 4 * 1024 * 1024 - 28          // plaintext bytes per chunk
const val CIPHERTEXT_CHUNK_SIZE = CHUNK_SIZE + 28     // = 4194304, always 4 MiB
```

---

## Changes required

### `Uploader.kt`

- Add `readOrWriteCheckpoint(file, checkpointData)` and `deleteCheckpoint(file)` helpers.
- In `uploadEncryptedViaSigned`:
  - After `/initiate` + `/resumable` succeed, write checkpoint.
  - At the top of the method, check for existing checkpoint and take the resume
    path if found.
  - On success or final failure, delete checkpoint.
- Refactor `encryptAndUploadStreaming` to accept `startChunkIndex` and
  `startCiphertextOffset` parameters (both default to 0 for the non-resume path).

### `UploadWorker.kt`

No changes needed — `doWork()` already calls `uploadEncryptedViaSigned` and
handles `Result.retry()`. The resume logic lives entirely in `Uploader`.

---

## Out of scope

- Web: browser uploads have no persistent retry mechanism; restart is correct.
- Parallel chunk uploads: out of scope in the original brief; still deferred.
- Resume for the small-file (< 10 MB) in-memory path: those uploads are fast
  enough that restart is acceptable.
- Server-side changes: none required.

---

## Acceptance criteria

1. A 91% upload interrupted by a network drop resumes from approximately 91%
   on the next WorkManager attempt rather than 0%.
2. If the GCS session expires (simulated by using an invalid URI), the upload
   falls back to a full restart without crashing.
3. A successful upload deletes the checkpoint file.
4. A final failure (all retries exhausted) deletes the checkpoint file.
5. Small files (< 10 MB) are unaffected.
6. All existing upload tests pass.
