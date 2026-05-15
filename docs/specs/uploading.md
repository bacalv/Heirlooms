# Uploading — Behavioral Specification

_Derived from: `UploadRoutes.kt`, `UploadService.kt`, `HeirloomsAPI.swift`, `vaultCrypto.js`, `BackgroundUploadManager.swift`_

---

## Use Case Inventory

- **Android share-sheet upload** — user shares image/video from another app into Heirlooms; app reads file bytes, generates DEK, encrypts content + thumbnail, calls `POST /uploads/initiate` (encrypted), PUTs ciphertext blobs to signed GCS URLs, calls `POST /uploads/confirm` with wrapped DEKs.
- **Web drag-and-drop upload** — user drops file onto web app; client generates DEK, encrypts in the browser using Web Crypto API, initiates and confirms upload through the same GCS direct-upload path.
- **Web paste upload** — user pastes an image from clipboard; same encryption and upload flow as drag-and-drop.
- **iOS share extension upload** — iOS share extension passes a URL/data to `BackgroundUploadManager`; same E2EE initiate → PUT → confirm flow; upload runs in background URLSession.
- **Upload with resumable session** — for large files, client calls `POST /uploads/resumable` to get a GCS resumable session URI, then chunks the PUT; confirm is identical.
- **Legacy (plaintext) upload** — `POST /upload` (body-upload, no E2EE); content stored plaintext in GCS; server generates thumbnail; deprecated path retained for backward compat.
- **Upload progress feedback** — client tracks progress against total bytes during PUT to GCS signed URL; no server-side progress endpoint; client is responsible for UI feedback.
- **Tag assignment at upload time** — client includes `tags: [...]` in the confirm body; server validates (no empty strings, max 64 chars per tag, no spaces).

---

## Sequence Diagrams

### 1. E2EE Upload — Initiate → PUT → Confirm (primary path)

```mermaid
sequenceDiagram
    participant App as Client App
    participant C as Crypto Layer
    participant S as Server
    participant GCS as GCS (signed URL)

    App->>C: generateDek() -> contentDek (32 random bytes)
    App->>C: generateDek() -> thumbDek (32 random bytes)
    App->>C: encryptStreamingContent(plaintext, contentDek)<br/>-> encryptedBlob (streaming chunks: [nonce][ct+tag]...)
    App->>C: encryptThumbnail(thumbBytes, thumbDek)<br/>-> encryptedThumb (symmetric envelope)
    App->>C: wrapDekUnderMasterKey(contentDek, masterKey)<br/>-> wrappedDek (master-aes256gcm-v1 envelope)
    App->>C: wrapDekUnderMasterKey(thumbDek, masterKey)<br/>-> wrappedThumbDek

    App->>S: POST /uploads/initiate [X-Api-Key]<br/>{mimeType, storage_class: "encrypted"}
    S-->>S: generate two storageKeys<br/> insertPendingBlob for each
    S->>App: 200 {storageKey, uploadUrl, thumbnailStorageKey, thumbnailUploadUrl}

    App->>GCS: PUT {uploadUrl} - encrypted content blob
    GCS->>App: 200 OK

    App->>GCS: PUT {thumbnailUploadUrl} - encrypted thumbnail blob
    GCS->>App: 200 OK

    App->>S: POST /uploads/confirm [X-Api-Key]<br/>{storageKey, thumbnailStorageKey, mimeType, fileSize,<br/> storage_class: "encrypted", envelopeVersion: 1,<br/> wrappedDek (b64), dekFormat: "master-aes256gcm-v1",<br/> wrappedThumbnailDek (b64), thumbnailDekFormat,<br/> encryptedMetadata? (b64), encryptedMetadataFormat?,<br/> contentHash?, takenAt?, tags: [...]}
    S-->>S: validate envelope fields<br/> dedup check on contentHash<br/>insertUploadRecord with storage_class="encrypted"<br/>launchFlowRouting (auto-route upload through flows)
    S->>App: 201 Created
    Note over App: Item appears in Garden on next load
```

### 2. Android Share-Sheet Upload (Swim-lane)

```mermaid
sequenceDiagram
    participant ShareSheet as Android Share Sheet
    participant App as Heirlooms App
    participant C as Crypto (BouncyCastle)
    participant S as Server
    participant GCS as GCS

    ShareSheet->>App: ACTION_SEND intent with Uri
    App-->>App: read file bytes via ContentResolver
    App->>C: generateDEK()<br/> encrypt content + thumbnail
    App->>C: wrapDEKUnderMasterKey(contentDek)
    App->>S: POST /uploads/initiate [X-Api-Key]<br/>{mimeType, storage_class: "encrypted"}
    S->>App: 200 {storageKey, uploadUrl, thumbnailStorageKey, thumbnailUploadUrl}
    App->>GCS: PUT encryptedBlob to uploadUrl
    App->>GCS: PUT encryptedThumb to thumbnailUploadUrl
    App->>S: POST /uploads/confirm [X-Api-Key] {all fields}
    S->>App: 201 Created
    App-->>App: show success toast<br/> finish share activity
```

### 3. Web Drag-and-Drop / Paste Upload (Swim-lane)

```mermaid
sequenceDiagram
    participant Browser as Browser (Web Crypto API)
    participant S as Server
    participant GCS as GCS

    Browser-->>Browser: drop event or paste event -> File object
    Browser-->>Browser: generateDek() via crypto.getRandomValues
    Browser-->>Browser: encrypt file chunks (streaming AES-GCM)<br/>encrypt thumbnail (AES-GCM)<br/>wrapDekUnderMasterKey(dek, masterKey) -> wrappedDek

    Browser->>S: POST /uploads/initiate [X-Api-Key]<br/>{mimeType, storage_class: "encrypted"}
    S->>Browser: 200 {storageKey, uploadUrl, thumbnailStorageKey, thumbnailUploadUrl}

    Browser->>GCS: PUT encryptedBlob (with progress tracking)
    GCS->>Browser: 200

    Browser->>GCS: PUT encryptedThumb
    GCS->>Browser: 200

    Browser->>S: POST /uploads/confirm [X-Api-Key] {all fields}
    S->>Browser: 201 Created
```

### 4. iOS Background Upload (Swim-lane)

```mermaid
sequenceDiagram
    participant Ext as Share Extension / App
    participant BUM as BackgroundUploadManager
    participant C as EnvelopeCrypto (CryptoKit)
    participant S as Server
    participant GCS as GCS

    Ext->>BUM: scheduleUpload(fileURL, mimeType)
    BUM->>C: generateDEK()<br/> encryptAESGCM(content)<br/> buildEnvelope
    BUM->>C: wrapDEKUnderMasterKey(dek) -> wrappedDek
    BUM->>S: POST /uploads/initiate [X-Api-Key]<br/>{mimeType, storage_class: "encrypted"}
    S->>BUM: 200 {storageKey, uploadUrl, thumbnailStorageKey, thumbnailUploadUrl}
    BUM->>GCS: background URLSession PUT encryptedBlob
    BUM->>GCS: background URLSession PUT encryptedThumb
    BUM->>S: POST /uploads/confirm [X-Api-Key] {all fields}
    S->>BUM: 201 Created
    Note over BUM: Upload completes even if app is backgrounded/suspended
```

### 5. Resumable Upload (Large Files)

```mermaid
sequenceDiagram
    participant App as Client App
    participant S as Server
    participant GCS as GCS (Resumable API)

    App->>S: POST /uploads/initiate {mimeType, storage_class: "encrypted"}
    S->>App: 200 {storageKey, uploadUrl, thumbnailStorageKey, thumbnailUploadUrl}

    App->>S: POST /uploads/resumable<br/>{storageKey, totalBytes, contentType}
    S-->>S: call GCS initiateResumableUpload
    S->>App: 200 {resumableUri}

    loop Upload chunks
        App->>GCS: PUT {resumableUri} Content-Range: bytes X-Y/Total
        GCS->>App: 308 Resume Incomplete (or 200 on last chunk)
    end

    App->>S: POST /uploads/confirm [X-Api-Key] {all fields}
    S->>App: 201 Created
```

### 6. Legacy Plaintext Upload (Deprecated Path)

```mermaid
sequenceDiagram
    participant App as Client App
    participant S as Server
    participant GCS as GCS

    App->>S: POST /upload [X-Api-Key]<br/>Content-Type: image/jpeg<br/>[raw file bytes in body]
    S-->>S: SHA-256 hash -> dedup check<br/>store plaintext bytes in GCS<br/>generate thumbnail (server-side)<br/>extract EXIF metadata (server-side)
    alt Duplicate
        S->>App: 409 {storageKey}
    else New
        S->>App: 201 {upload record}
    end
    Note over App: Plaintext storage class<br/> no wrappedDek<br/> legacy_plaintext storage_class
```
