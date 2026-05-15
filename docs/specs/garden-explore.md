# Garden & Explore — Behavioral Specification

_Derived from: `UploadRoutes.kt`, `UploadService.kt`, `listUploadsHandler`_

---

## Use Case Inventory

- **User loads Garden** — authenticated user fetches their upload list; server returns cursor-paginated results ordered by `upload_newest` by default; client decrypts thumbnails for display.
- **User views Just Arrived** — user requests uploads with `just_arrived=true`; returns items where `last_viewed_at IS NULL`; first detail open clears Just Arrived status via `POST /uploads/{id}/view`.
- **User views a plot row** — user fetches uploads filtered by `plot_id`; server evaluates plot membership or ownership before returning items.
- **User opens photo detail** — client fetches upload record (`GET /uploads/{id}`), then fetches encrypted file bytes (`GET /uploads/{id}/file`), decrypts locally with DEK unwrapped from master key; `POST /uploads/{id}/view` marks as viewed.
- **User fetches thumbnail** — client calls `GET /uploads/{id}/thumb`; for encrypted uploads, server returns the encrypted thumbnail blob (`application/octet-stream`); client decrypts with wrapped thumbnail DEK.
- **User explores with filter + sort** — user sets query params on `GET /uploads`: `tag`, `exclude_tag`, `from_date`, `to_date`, `media_type`, `has_location`, `is_received`, `sort` (upload_newest/upload_oldest/taken_newest/taken_oldest), `in_capsule`; response is cursor-paginated.
- **User checks content hash before upload** — client calls `GET /uploads/hash/{sha256hex}` to detect duplicates before initiating; 200 = exists, 404 = new.

---

## Sequence Diagrams

### 1. Garden Load (Swim-lane)

```mermaid
sequenceDiagram
    participant App as Client App
    participant S as Server
    participant GCS as GCS / Storage

    App->>S: GET /uploads?limit=50&sort=upload_newest [X-Api-Key]
    S-->>S: query uploads for user (not composted)<br/>apply filters; cursor-paginate
    S->>App: 200 {items: [...], nextCursor}

    loop For each visible thumbnail
        App->>S: GET /uploads/{id}/thumb
        S->>GCS: fetch encrypted thumbnail blob
        GCS->>S: blob bytes
        S->>App: 200 Content-Type: application/octet-stream (encrypted)
        App-->>App: unwrapDekWithMasterKey(wrappedThumbDek)<br/>→ thumbDek<br/>decryptSymmetric(blob, thumbDek) → JPEG bytes<br/>display thumbnail
    end
```

### 2. Just Arrived

```mermaid
sequenceDiagram
    participant App as Client App
    participant S as Server

    App->>S: GET /uploads?just_arrived=true [X-Api-Key]
    S-->>S: filter WHERE last_viewed_at IS NULL
    S->>App: 200 {items: [...]}

    Note over App: User taps item to open detail

    App->>S: POST /uploads/{id}/view [X-Api-Key]
    S-->>S: UPDATE uploads SET last_viewed_at = NOW()
    S->>App: 204 No Content

    Note over App: Item no longer appears in Just Arrived on next load
```

### 3. Photo Detail — Encrypted File Fetch

```mermaid
sequenceDiagram
    participant App as Client App
    participant C as Crypto Layer
    participant S as Server
    participant GCS as GCS / Storage

    App->>S: GET /uploads/{id} [X-Api-Key]
    S->>App: 200 {upload record including wrappedDek, dekFormat, storageKey, ...}

    App->>C: unwrapDekWithMasterKey(wrappedDek) → contentDek

    App->>S: GET /uploads/{id}/file [X-Api-Key]
    Note over S: Supports Range header for video streaming
    S->>GCS: fetch file bytes (storageKey)
    GCS->>S: encrypted blob
    S->>App: 200 Content-Type: application/octet-stream<br/>(or 206 Partial Content for range requests)

    App->>C: decryptStreamingContent(blob, contentDek)<br/>→ plaintext bytes
    App-->>App: display image / play video

    App->>S: POST /uploads/{id}/view [X-Api-Key]
    S->>App: 204 No Content
```

### 4. Explore — Filter and Sort

```mermaid
sequenceDiagram
    participant App as Client App
    participant S as Server

    App->>S: GET /uploads?tag=family&media_type=image<br/>&from_date=2024-01-01&sort=taken_newest<br/>&limit=50 [X-Api-Key]
    S-->>S: apply tag filter (tags @> ARRAY[tag])<br/>apply media_type filter (mime_type LIKE 'image/%')<br/>apply date range on taken_at<br/>sort by taken_at DESC<br/>cursor paginate
    S->>App: 200 {items: [...], nextCursor}

    Note over App: User scrolls to bottom of page

    App->>S: GET /uploads?...&cursor={nextCursor}
    S->>App: 200 {items: [...], nextCursor: null}
    Note over App: nextCursor null → end of results
```

### 5. Plot Row (Filter by Plot)

```mermaid
sequenceDiagram
    participant App as Client App
    participant S as Server

    App->>S: GET /uploads?plot_id={uuid} [X-Api-Key]
    S-->>S: verify plot ownership or membership<br/>if shared plot: check member status<br/>filter uploads matching plot criteria OR plot_items membership
    S->>App: 200 {items: [...], nextCursor}
    Note over App: 404 if plot not found / not accessible
```

### 6. Duplicate Check Before Upload

```mermaid
sequenceDiagram
    participant App as Client App
    participant S as Server

    App-->>App: SHA-256(plaintext bytes) → hexHash

    App->>S: GET /uploads/hash/{hexHash} [X-Api-Key]
    alt Duplicate found
        S->>App: 200 {exists: true}
        Note over App: Skip upload — already in library
    else New content
        S->>App: 404
        Note over App: Proceed with upload flow
    end
```
