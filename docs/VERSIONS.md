# Heirlooms — Version History

---

## v0.16.1 — Bug fixes (7 May 2026)

**Android: video upload OOM fix**
- `UploadWorker` was calling `file.readBytes()` before uploading, loading the entire video into the Java heap. On the Samsung Galaxy A02s (201 MB heap limit) this left no room for OkHttp's Okio buffers, causing `OutOfMemoryError` mid-upload. The error is a `java.lang.Error`, so `catch (_: Exception)` silently swallowed it — no failure notification appeared and no GCS object was created.
- Fix: new `Uploader.uploadViaSigned(File, ...)` overload streams the video from disk to GCS directly using `file.asRequestBody()`, never loading it into memory. SHA-256 is computed by streaming the file in 8 KB chunks. `UploadWorker` passes the `File` object directly, removing `readBytes()` entirely.

**Web: tag dropdown stays open after selection**
- After clicking a tag in the dropdown, the list was closing and required clicking away and back to reopen it.
- Root cause: `onBlur` was firing on the input and closing the dropdown. `e.preventDefault()` on the suggestion button's `mousedown` is not reliable across browsers (Safari on macOS does not focus buttons on click). `e.relatedTarget` was therefore null and could not be used to detect intra-dropdown focus movement.
- Fix: `suppressBlurRef` — the dropdown container's `onMouseDown` sets a ref flag before `onBlur` fires; `onBlur` skips the close while the flag is set; `onMouseUp` resets it. Browser-agnostic.

---

## v0.16.0 — Tags (6 May 2026)

Milestone 4 complete (tag support). Three increments delivered together:

**Schema and write API (Increment 1):**
- Flyway V6 migration: `tags TEXT[] NOT NULL DEFAULT '{}'` with GIN index on the uploads table; existing rows get the empty-array default automatically
- `TagValidator` enforces kebab-case (`^[a-z0-9]+(-[a-z0-9]+)*$`), length 1–50; rejects rather than coerces (`My-Children` → 400 with the offending tag named in the response body, not silent normalisation)
- `PATCH /api/content/uploads/{id}/tags` — full-replace semantics; 400 on invalid tag (offending tag + reason in body); 404 if upload not found; 200 with the updated upload record on success
- `tags` field always present in all upload JSON responses (`POST /upload`, `GET /uploads`); empty array default for pre-migration rows

**Read API and filtering (Increment 2):**
- `GET /api/content/uploads` extended with `?tag=X` and `?exclude_tag=Y` query params; backed by the V6 GIN index via `tags @> ARRAY[?]::text[]`
- OpenAPI body schemas added to both PATCH endpoints via `receiving(lens to example)` so Swagger UI shows a populated request body; `RotationRequest` and `TagsRequest` data classes must be non-`private` for Jackson's schema generator — `private` causes `IllegalAccessException` at the spec endpoint at runtime (not caught by unit tests); fixed and a spec-endpoint test added as a permanent regression guard

**Web UI (Increment 3):**
- Tag chips on each gallery card below the metadata row; hidden when no tags present
- Inline tag editor per card: tag icon in the card header opens the editor; existing chips show with × to remove; text input with autocomplete dropdown of all tags used across the gallery (derived client-side from the uploads list, filtered as you type); pending input text is flushed into the tag list on Save so pressing Enter before clicking Save is not required
- Tags updated in gallery state from the server response after a successful PATCH
- Exclude-filter is live on the API but not yet surfaced in the web UI

135 tests total, 134 passing, 1 skipped (FFmpeg video thumbnail — runs in Docker).

---

## v0.15.0 — Image rotation (6 May 2026)

- `PATCH /api/content/uploads/{id}/rotation` — accepts `{"rotation":0|90|180|270}` to set display rotation for an image; persisted in the database
- Flyway V5 migration: `rotation INT NOT NULL DEFAULT 0` on the uploads table
- HeirloomsWeb: ↻ rotate button on each image card; optimistic update on click; CSS `transform: rotate()` applied to both thumbnail and lightbox; lightbox swaps `max-w`/`max-h` constraints at 90°/270° so rotated images fill the viewport correctly
- 5 new tests; 107 total, 106 passing, 1 skipped

---

## v0.14.0 — Metadata extraction + Android overhaul (6 May 2026)

**EXIF and video metadata extraction:**
- `MetadataExtractor.kt` — extracts GPS (lat/lon/alt), capture timestamp, and device make/model from images via `com.drewnoakes:metadata-extractor`; extracts creation time and Apple QuickTime location/device tags from videos via `ffprobe` JSON output
- Flyway V4 migration: 6 nullable metadata columns on uploads table (`captured_at`, `latitude`, `longitude`, `altitude`, `device_make`, `device_model`)
- `GcsFileStore.getFirst()` — streams only the first 64 KB from GCS for image metadata extraction (JPEG EXIF is always within this range), avoiding loading full files into memory
- Cloud Run memory increased to 2 Gi to accommodate thumbnail generation for large images (4160×3120 ≈ 52 MB raw in BufferedImage)
- HeirloomsWeb: 📍 pin icon on gallery cards that have GPS coordinates; tooltip shows decimal coordinates on hover

**Android app fixes and improvements:**
- `ACCESS_MEDIA_LOCATION` permission added; `MediaStore.setRequireOriginal()` called on Android 10+ so shared photos include GPS EXIF (with graceful fallback)
- Wildcard MIME type fix: Samsung Gallery shares with `intent.type = "image/*"`; app now falls back to `ContentResolver.getType()` to get the specific type, fixing `.bin` storage key and missing metadata
- Silent upload failure fix: `readBytes()` threw `SecurityException` (not caught by `catch (e: IOException)`); changed to `catch (e: Exception)` so the upload always falls back rather than silently dying
- SHA-256 hash now sent in confirm request body, enabling deduplication for signed-URL uploads
- Upload progress notification: `ProgressRequestBody` reports byte-level progress during GCS PUT; `IMPORTANCE_LOW` progress channel shows silent progress bar; `IMPORTANCE_HIGH` result channel pops heads-up banner on completion
- App renamed from "My Heirlooms" to "Heirlooms"

---

## v0.11.0 — SHA-256 duplicate detection (6 May 2026)

Prevents the same file being stored twice, on both upload paths.

- **Direct upload** (`POST /upload`) — server computes SHA-256 of the incoming
  bytes; if a matching hash is found in the database, returns `409 Conflict` with
  `{"storageKey":"..."}` pointing to the existing copy; nothing is stored
- **Signed URL flow** (`POST /uploads/confirm`) — client sends the hash in the
  request body as `contentHash`; same 409 logic applies; the file will already
  have been PUT to GCS but no metadata record is created
- Flyway V2 migration: `content_hash VARCHAR(64)` column + partial index;
  existing rows backfilled with random placeholders (no false positives)

---

## v0.10.0 — Custom domain live + full Swagger coverage (6 May 2026)

- **Custom domains live** — `https://heirlooms.digital` and
  `https://api.heirlooms.digital` confirmed working; GoDaddy DNS A records
  cleaned up (WebsiteBuilder parking record removed), Google SSL cert issued
- **Android app hardcoded to `api.heirlooms.digital`** — endpoint URL no
  longer user-configurable; settings screen reduced to API key only; app
  always targets the production domain
- **Full Swagger coverage** — `GET /uploads/{id}/file` and
  `GET /uploads/{id}/url` were previously invisible to the OpenAPI contract;
  converted to typed `ContractRoute` entries using `import org.http4k.contract.div`
  so all six endpoints now appear in Swagger UI at `api.heirlooms.digital/docs`

---

## v0.9.0 — Video player + streaming (6 May 2026)

- **Video player** — clicking a video card in HeirloomsWeb opens a native
  `<video controls>` modal; a distinct video icon with "Click to play" label
  replaces the generic file icon for video files
- **Streaming via signed read URLs** — new `GET /api/content/uploads/{id}/url`
  endpoint generates a 1-hour signed GCS read URL; the web app sets this directly
  as `<video src>` so the browser handles streaming, buffering, and seeking
  natively via GCS range requests — no full download required
- **Dockerfile simplified** — JAR now built locally with `./gradlew shadowJar`
  before `docker build`; eliminates Gradle distribution downloads inside the
  container that were causing Docker Desktop connection drops on macOS; build
  time drops from ~3 minutes to ~2 seconds
- Validated end-to-end: video streaming confirmed working

---

## v0.8.0 — Large file upload via GCS signed URLs (6 May 2026)

Fixes the 34 MB+ video upload failure (Cloud Run enforces a hard 32 MB request
body limit that cannot be configured away).

Three-step upload flow in HeirloomsApp:
1. `POST /api/content/uploads/prepare` → server returns a signed GCS PUT URL (15-min expiry)
2. Mobile app PUTs file bytes **directly to GCS** — Cloud Run is bypassed entirely
3. `POST /api/content/uploads/confirm` → server records metadata in the database

- `DirectUploadSupport` interface + `PreparedUpload` data class added to HeirloomsServer
- `GcsFileStore` implements `DirectUploadSupport`; uses `ServiceAccountCredentials`
  (already in `GCS_CREDENTIALS_JSON`) for V4 URL signing — no new secrets needed
- Prepare endpoint returns 501 for non-GCS backends (local, S3)
- `Uploader.uploadViaSigned()` added to HeirloomsApp; `ShareActivity` now always
  uses this path; OkHttp write timeout raised to 300s
- Validated end-to-end: 34.57 MB video uploaded successfully

---

## v0.7.0 — Web gallery UI (6 May 2026)

Milestone 4 complete. Adds HeirloomsWeb and a file retrieval endpoint.

- `GET /api/content/uploads/{id}/file` — new HeirloomsServer endpoint; looks up
  the upload record by UUID, fetches the file from GCS, streams it back with the
  correct `Content-Type`; returns 404 if not found
- `FileStore.get(key)` — new method on the interface, implemented in
  `LocalFileStore`, `S3FileStore`, and `GcsFileStore`
- `uploadedAt` added to `UploadRecord` and the list endpoint JSON response
- CORS support added to HeirloomsServer (all origins; tighten when domain is live);
  OPTIONS preflight answered before `ApiKeyFilter` runs
- `HeirloomsWeb/` — new React 18 + Tailwind CSS + Vite sub-project; gallery grid
  with image thumbnails (blob URL fetch so `X-Api-Key` header can be sent), file
  icons for non-images, upload date, MIME type, file size; lightbox on click;
  in-session API key login (React state only — cleared on every page reload);
  multi-stage Dockerfile (Node 22 → nginx:alpine)

---

## v0.6.0 — Swagger UI + API key auth in docs (5 May 2026)

- Swagger UI integrated and validated on Cloud Run
- API key auth surfaced in Swagger UI (`X-Api-Key` header scheme)
- `persistAuthorization: true` so the key survives page refresh in the docs UI

---

## v0.5.0 — GCS backend + Cloud Run deployment (2–4 May 2026)

- `GcsFileStore` — Google Cloud Storage backend; credentials from
  `GCS_CREDENTIALS_JSON` env var (never written to disk)
- Cloud SQL PostgreSQL socket factory for Cloud Run → Cloud SQL connectivity
- API key authentication added to HeirloomsServer (`ApiKeyFilter`) and
  HeirloomsApp (settings screen + `X-Api-Key` header on uploads)
- Full GCP infrastructure provisioned: Cloud Run, Cloud SQL, GCS bucket,
  Artifact Registry, service account with least-privilege IAM, secrets in
  Secret Manager

---

## v0.4.0 — Self-hosted deployment (1 May 2026)

Milestone 3 complete. Adds a `deploy/` folder so the full stack can be run on a
home server or cheap VPS with a single command.

- `deploy/docker-compose.yml` — production compose with named volumes, host port
  binding (8080:8080), restart policies, and a `build:` directive that compiles
  HeirloomsServer from source
- `deploy/.env.example` — credential template; real `.env` is gitignored
- `deploy/README.md` — step-by-step setup guide for a VPS or home server

Key differences from the test compose: credentials from `.env` (not hardcoded),
named volumes so data survives restarts, `restart: unless-stopped` on all services.

---

## v0.3.0 — Package rename + docs (30 April 2026)

Administrative release following domain registration (`heirlooms.digital`).

- Renamed package `com.heirloom` → `digital.heirlooms` across all 22 Kotlin source
  files in HeirloomsApp, HeirloomsServer, and HeirloomsTest
- Added `TEAM.md`, `PA_NOTES.md`, `SE_NOTES.md`, `PROMPT_LOG.md`, `ROADMAP.md`
- Reorganised all docs into `docs/`; updated `README.md` with a doc index

---

## v0.2.0 — Backend server + integration tests (30 April 2026)

Milestone 2 complete. Adds HeirloomsServer and HeirloomsTest.

**HeirloomsServer** — Kotlin/http4k server with three endpoints:
- `POST /api/content/upload` — receives file bytes, stores to MinIO, records
  metadata in PostgreSQL, returns storage key
- `GET /api/content/uploads` — returns JSON array of all uploads
- `GET /health` — liveness check

Stack: http4k + Netty, PostgreSQL + HikariCP + Flyway, AWS SDK v2 S3 client
(with `forcePathStyle` for MinIO compatibility). Runs in Docker via a multi-stage
Dockerfile using Eclipse Temurin 21.

**HeirloomsTest** — Gradle integration test project using Testcontainers 2.x and
OkHttp. Spins up the full Docker Compose stack (postgres, minio, minio-init,
heirloom-server) and runs 10 tests across two test classes: upload API tests and
end-to-end journey tests. All tests passing.

---

## v0.1.0 — Android share-target app (30 April 2026)

Milestone 1 complete. Adds HeirloomsApp.

An Android app (Kotlin) that registers as a share target for `image/*` and
`video/*`. When a photo or video is shared to Heirlooms, it HTTP POSTs the file
to a configurable endpoint with the correct `Content-Type`. A settings screen
(accessible from the share sheet) lets the endpoint URL be changed without
editing config files. Endpoint stored in `SharedPreferences`. Sideloaded in
developer mode — no app store required.

Originally written in Java; rewritten in Kotlin with the settings screen added
in the same session.
