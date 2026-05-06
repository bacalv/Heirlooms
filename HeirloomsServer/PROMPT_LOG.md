# Prompt Log — HeirloomsServer Development Session

Timestamps are not available and are marked as `[unknown]`.

---

## Entry 1
**Time:** [unknown]
**Prompt:** Write a simple server that the Android application can post content to. The server should be written using Gradle, Kotlin and http4k. It should have a single endpoint: POST /api/content/upload. When receiving a file from the Android application, it should store it in a local directory using a random UUID as the filename and the appropriate file extension for the content type. The local storage location should be configurable in application.properties.

**Response:** Generated a complete http4k server project with five source files and 30 tests across three test classes.

**Files generated:**
- `HeirloomsServer/build.gradle.kts`
- `HeirloomsServer/settings.gradle.kts`
- `HeirloomsServer/src/main/kotlin/com/heirloom/server/Main.kt`
- `HeirloomsServer/src/main/kotlin/com/heirloom/server/AppConfig.kt`
- `HeirloomsServer/src/main/kotlin/com/heirloom/server/FileStorage.kt`
- `HeirloomsServer/src/main/kotlin/com/heirloom/server/ContentTypeExtensions.kt`
- `HeirloomsServer/src/main/kotlin/com/heirloom/server/UploadHandler.kt`
- `HeirloomsServer/src/main/resources/application.properties`
- `HeirloomsServer/src/test/kotlin/com/heirloom/server/ContentTypeExtensionsTest.kt`
- `HeirloomsServer/src/test/kotlin/com/heirloom/server/FileStorageTest.kt`
- `HeirloomsServer/src/test/kotlin/com/heirloom/server/UploadHandlerTest.kt`
- `HeirloomsServer/README.md`

---

## Entry 2
**Time:** [unknown]
**Prompt:** Could you rewrite the HeirloomsServer to use Java version 21.

**Response:** Changed `jvmToolchain(17)` to `jvmToolchain(21)` in `build.gradle.kts`. No other changes required.

**Files changed:**
- `HeirloomsServer/build.gradle.kts`

---

## Entry 3
**Time:** [unknown]
**Prompt:** This does not compile for me - it complains in Main.kt that the import 'asServer' on line 4 cannot be found.

**Response:** Identified the root cause as http4k version `5.14.1.0` having undergone a major package restructuring in v5/v6. Downgraded to stable `4.46.0.0` where all import paths are correct and which runs fine on Java 21.

**Files changed:**
- `HeirloomsServer/build.gradle.kts`

---

## Entry 4
**Time:** [unknown]
**Prompt:** Please tag both of the artefacts (the android app and the server) as version 0.0.1.

**Response:** Updated version to `0.0.1` in `build.gradle.kts`.

**Files changed:**
- `HeirloomsServer/build.gradle.kts`

---

## Entry 5
**Time:** [unknown]
**Prompt:** It worked. Please update the version of both apps to 0.1.0.

**Response:** Updated version to `0.1.0` in `build.gradle.kts`.

**Files changed:**
- `HeirloomsServer/build.gradle.kts`

---

## Entry 6
**Time:** [unknown]
**Prompt:** Can you create a file called "VERSION_HISTORY.md" and summarise the changes between each version?

**Response:** Created `VERSION_HISTORY.md` covering both projects across versions `0.0.1` and `0.1.0`.

**Files generated:**
- `HeirloomsServer/VERSION_HISTORY.md`

---

## Entry 7
**Time:** [unknown]
**Prompt:** In the Vaultserver, create an abstraction that stores the files. Currently, the code stores the file to a local directory. This will be the first implementation of the abstraction. The second implementation should store files in an Amazon S3 bucket using the asynchronous API. Ensure the prompt log is updated and documentation regarding how to set the amazon access and secret keys is documented in the README.

**Response:** Introduced `FileStore` interface and `StorageKey` value class. Renamed `FileStorage` to `LocalFileStore`. Added `S3FileStore` using AWS SDK v2 async client. Extended `AppConfig` with S3 properties. Updated `Main` to select backend from config. Updated `UploadHandler` to depend on the interface. Added `S3FileStoreTest` (8 tests). Updated README with full S3/IAM setup guide. Bumped version to `0.2.0`.

**Files changed/added:**
- `HeirloomsServer/build.gradle.kts`
- `HeirloomsServer/src/main/kotlin/com/heirloom/server/FileStore.kt` (new)
- `HeirloomsServer/src/main/kotlin/com/heirloom/server/FileStorage.kt`
- `HeirloomsServer/src/main/kotlin/com/heirloom/server/S3FileStore.kt` (new)
- `HeirloomsServer/src/main/kotlin/com/heirloom/server/AppConfig.kt`
- `HeirloomsServer/src/main/kotlin/com/heirloom/server/UploadHandler.kt`
- `HeirloomsServer/src/main/kotlin/com/heirloom/server/Main.kt`
- `HeirloomsServer/src/main/resources/application.properties`
- `HeirloomsServer/src/test/kotlin/com/heirloom/server/LocalFileStoreTest.kt` (was FileStorageTest.kt)
- `HeirloomsServer/src/test/kotlin/com/heirloom/server/S3FileStoreTest.kt` (new)
- `HeirloomsServer/src/test/kotlin/com/heirloom/server/UploadHandlerTest.kt`
- `HeirloomsServer/README.md`
- `HeirloomsServer/VERSION_HISTORY.md`

---

## Entry 8
**Time:** [unknown]
**Prompt:** Yes (confirming to add PROMPT_LOG.md to the server project).

**Response:** Created this file.

**Files generated:**
- `HeirloomsServer/PROMPT_LOG.md` (this file)

---

## Entry 10
**Date:** 2026-05-01
**Prompt:** Add Swagger to the backend server so API documentation is accessible in a
browser and can be used as a simple client.

**Response:** Added OpenAPI 3.0 spec generation and a fully self-hosted Swagger UI.

- Added `http4k-contract`, `http4k-format-jackson`, and `org.webjars:swagger-ui:5.11.8`
  to `build.gradle.kts`
- Converted `UploadHandler.kt` from plain `routes()` to http4k contract routing; the
  contract auto-generates and serves an OpenAPI 3.0 spec at `GET /api/content/openapi.json`
- Swagger UI assets served from the webjar classpath at `/docs/` via
  `static(ResourceLoader.Classpath("META-INF/resources/webjars/swagger-ui/5.11.8"))`
- `swagger-initializer.js` overridden by a specific route before the static handler,
  pointing Swagger UI at the local spec URL
- `GET /docs` redirects to `/docs/index.html`
- `Body.binary(ContentType("application/octet-stream")).toLens()` added to upload route
  meta so Swagger UI renders a file picker — `binary` is an extension on
  `org.http4k.core.Body.Companion` from `org.http4k.lens`; `org.http4k.lens.Body`
  does not exist in http4k 4.46
- Updated `UploadHandlerTest.kt`: `GET /api/content/upload` now expects 404 (not 405)
  because http4k-contract returns 404 for unregistered methods on contract-owned paths
- All 48 unit tests pass

**Key gotcha:** `Body.binary()` is not a standalone top-level function; it is an
extension on `org.http4k.core.Body.Companion`. Import `org.http4k.core.Body` and
`org.http4k.lens.binary` together to call it as `Body.binary(...)`.

**Files changed:**
- `HeirloomsServer/build.gradle.kts`
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/UploadHandler.kt`
- `HeirloomsServer/src/test/kotlin/digital/heirlooms/server/UploadHandlerTest.kt`
- `HeirloomsServer/README.md`
- `HeirloomsServer/PROMPT_LOG.md` (this file)

---

## Entry 9
**Time:** [unknown]
**Prompt:** You asked "where to host the server". We should add a Dockerfile to each backend service and change the gradle build to generate a docker image. Also introduced PostgreSQL, Flyway, MinIO support, and a new HeirloomsTest project.

**Response:** Added PostgreSQL + HikariCP + Flyway. Added `Database.kt`, `UploadRecord`. Updated `AppConfig` with DB and S3 endpoint override properties and environment variable support. Updated `UploadHandler` with database integration, `GET /api/content/uploads`, and `GET /health`. Updated `S3FileStore` with endpoint override and `forcePathStyle`. Updated `Main.kt` to wire database. Added Flyway migration `V1__create_uploads_table.sql`. Added `Dockerfile`. Added Jib plugin to `build.gradle.kts`. Bumped version to `0.3.0`.

**Files changed/added:**
- `HeirloomsServer/build.gradle.kts`
- `HeirloomsServer/Dockerfile` (new)
- `HeirloomsServer/src/main/kotlin/com/heirloom/server/Database.kt` (new)
- `HeirloomsServer/src/main/kotlin/com/heirloom/server/AppConfig.kt`
- `HeirloomsServer/src/main/kotlin/com/heirloom/server/UploadHandler.kt`
- `HeirloomsServer/src/main/kotlin/com/heirloom/server/S3FileStore.kt`
- `HeirloomsServer/src/main/kotlin/com/heirloom/server/Main.kt`
- `HeirloomsServer/src/main/resources/application.properties`
- `HeirloomsServer/src/main/resources/db/migration/V1__create_uploads_table.sql` (new)
- `HeirloomsServer/VERSION_HISTORY.md`

---

## Entry [2026-05-05]
**Prompt:** Milestone 3 — add Google Cloud Storage support to HeirloomsServer. New GcsFileStore implementing FileStore; extend AppConfig with GCS enum value and two new fields (GCS_BUCKET, GCS_CREDENTIALS_JSON); wire up in Main.kt. Existing S3FileStore and LocalFileStore unchanged.

**Response:** Added GCS backend. Service account credentials are passed in as a JSON string via the GCS_CREDENTIALS_JSON environment variable (never written to disk). Set STORAGE_BACKEND=GCS, GCS_BUCKET, and GCS_CREDENTIALS_JSON to activate.

**Files changed/added:**
- `HeirloomsServer/build.gradle.kts` — added `com.google.cloud:google-cloud-storage:2.36.1`
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/GcsFileStore.kt` (new)
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/AppConfig.kt` — GCS enum + gcsBucket/gcsCredentialsJson fields
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/Main.kt` — GCS wiring

---

## Entry [2026-05-06] — Dockerfile fix
**Prompt:** Docker Desktop on macOS drops the build connection during long Gradle downloads inside the container, requiring a manual Docker restart every build. Fix it.

**Response:** Simplified Dockerfile to remove the multi-stage Gradle build entirely. JAR is now built locally with `./gradlew shadowJar` first, then `docker build` just COPYs the pre-built JAR into the JRE image. Build time dropped from ~3 minutes (with repeated failures) to ~2 seconds. Updated PA_NOTES.md deploy command. Validated: `heirlooms-server-00009-58m` deployed successfully without any Docker restarts. Tagged as **v0.9.0**.

**Files changed:**
- `HeirloomsServer/Dockerfile` — removed multi-stage Gradle build, COPY pre-built JAR only
- `docs/PA_NOTES.md` — updated Docker build command to include `./gradlew shadowJar` step

---

## Entry [2026-05-06] — Signed read URL for video streaming
**Prompt:** Add a signed GCS read URL endpoint so the web app can stream videos instead of downloading them as blobs.

**Response:** Added `generateReadUrl(key: StorageKey): String` to `DirectUploadSupport` interface and implemented in `GcsFileStore` (V4 signed GET URL, 1-hour expiry). New top-level route `GET /api/content/uploads/{id}/url` looks up the upload record, generates the signed URL, returns `{"url":"..."}`. Returns 501 for non-GCS backends, 404 if record not found. 3 new tests.

**Files changed:**
- `DirectUploadSupport.kt` — added `generateReadUrl`
- `GcsFileStore.kt` — implemented `generateReadUrl`
- `UploadHandler.kt` — added `readUrlHandler` and route
- `UploadHandlerTest.kt` — 3 new tests

---

## Entry [2026-05-06] — Signed URL upload — validated
**Validated end-to-end:** Server deployed to Cloud Run revision `heirlooms-server-00008-vt7`. Fresh APK installed via `adb install -r`. 34.57 MB video uploaded successfully via the three-step signed URL flow. Tagged as **v0.8.0**.

---

## Entry [2026-05-06] — Signed URL upload
**Prompt:** Add GCS signed URL upload flow to bypass Cloud Run 32 MB request body limit. Three-step flow: prepare (get signed URL) → PUT directly to GCS → confirm (record metadata). Update HeirloomsApp to use the new flow.

**Response:** Added `DirectUploadSupport` interface and `PreparedUpload` data class. `GcsFileStore` now implements it using V4 signed PUT URLs (15-minute expiry); switched from `GoogleCredentials` to `ServiceAccountCredentials` for signing capability. Added `POST /api/content/uploads/prepare` and `POST /api/content/uploads/confirm` contract routes; prepare returns 501 for non-GCS backends. Android `Uploader.uploadViaSigned()` implements the three-step flow; `ShareActivity` now calls it and derives the base URL from the stored endpoint. Write timeout increased to 300s. All tests pass (server + Android).

**Files changed:**
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/DirectUploadSupport.kt` (new)
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/GcsFileStore.kt` — ServiceAccountCredentials, prepareUpload()
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/UploadHandler.kt` — prepare/confirm routes and handlers
- `HeirloomsServer/src/test/kotlin/digital/heirlooms/server/UploadHandlerTest.kt` — prepare/confirm tests
- `HeirloomsApp/app/src/main/kotlin/digital/heirlooms/app/Uploader.kt` — uploadViaSigned()
- `HeirloomsApp/app/src/main/kotlin/digital/heirlooms/app/ShareActivity.kt` — use uploadViaSigned, 300s timeout
- `HeirloomsApp/app/src/test/kotlin/digital/heirlooms/app/UploaderTest.kt` — uploadViaSigned tests

---

## Entry [2026-05-06]
**Prompt:** Milestone 4 — Part 1: Add `GET /api/content/uploads/{id}/file` file proxy endpoint. Add `get(key: StorageKey): ByteArray` to the FileStore interface and all implementations (LocalFileStore, S3FileStore, GcsFileStore). Add `getUploadById(id: UUID): UploadRecord?` to Database. Add `uploadedAt: Instant` to UploadRecord (backed by the existing `uploaded_at` DB column). Update the list endpoint JSON to include `uploadedAt`. Add tests for the new interface method in LocalFileStoreTest and S3FileStoreTest, and for the new endpoint in UploadHandlerTest.

**Response:** Implemented all changes. FileStore interface extended with `get`. LocalFileStore, S3FileStore, GcsFileStore all implement it. Database.getUploadById added; listUploads updated to SELECT uploaded_at. UploadRecord now includes uploadedAt with default Instant.now(). New top-level route `/api/content/uploads/{id}/file` (GET) added before the contract binding so it takes priority; protected by the existing ApiKeyFilter. 17 new/updated tests; all pass.

**Files changed:**
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/FileStore.kt` — added `get` method
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/FileStorage.kt` — implemented `get`
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/S3FileStore.kt` — implemented `get`
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/GcsFileStore.kt` — implemented `get`
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/Database.kt` — added `uploadedAt` to UploadRecord, added `getUploadById`, updated `listUploads`
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/UploadHandler.kt` — added file proxy route and handler, updated list JSON
- `HeirloomsServer/src/test/kotlin/digital/heirlooms/server/LocalFileStoreTest.kt` — added `get` tests
- `HeirloomsServer/src/test/kotlin/digital/heirlooms/server/S3FileStoreTest.kt` — added `get` tests
- `HeirloomsServer/src/test/kotlin/digital/heirlooms/server/UploadHandlerTest.kt` — added file proxy tests

---

## Entry [2026-05-06] — HeirloomsWeb: in-memory auth
**Prompt:** Replace VITE_API_KEY build-time env var with a login screen. API key entered at runtime, held in React state only — cleared on page reload, never written to localStorage or disk.

**Response:** Added LoginScreen component that validates the key against the server before proceeding (returns a real 401 check, not just a truthy test). Removed VITE_API_KEY from App.jsx, Dockerfile ARG, and README. Image thumbnails now fetched via blob URLs (fetch + createObjectURL) so the X-Api-Key header can be sent — plain `<img src>` can't send custom headers. Sign out button clears state. VITE_API_URL remains as the only build-time variable.

**Files changed:**
- `HeirloomsWeb/src/App.jsx` — login screen, in-memory key, blob URL thumbnails
- `HeirloomsWeb/Dockerfile` — removed VITE_API_KEY ARG
- `HeirloomsWeb/.env` — removed VITE_API_KEY line
- `HeirloomsWeb/README.md` — updated instructions

---

## Entry [2026-05-06] — CORS
**Prompt:** Add CORS support to HeirloomsServer — allow all origins for now, tighten later when domain is set up.

**Response:** Added `CorsFilter.kt` — a simple http4k Filter that returns 200 + CORS headers for OPTIONS preflight requests, and appends `Access-Control-Allow-Origin: *` to all other responses. Wired as the outermost filter in `Main.kt` (before `ApiKeyFilter`) so browser preflight requests are answered without an API key. Allows `Content-Type` and `X-Api-Key` headers; `GET`, `POST`, `OPTIONS` methods. 6 tests in `CorsFilterTest.kt`, all green.

**Files changed/added:**
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/CorsFilter.kt` (new)
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/Main.kt` — wrap with corsFilter()
- `HeirloomsServer/src/test/kotlin/digital/heirlooms/server/CorsFilterTest.kt` (new)

---

## Entry [2026-05-06] — Part 2
**Prompt:** Milestone 4 — Part 2: Create HeirloomsWeb sub-project — React + Tailwind + Vite gallery UI for browsing uploads, with nginx for production serving.

**Response:** Created `HeirloomsWeb/` at monorepo root. React 18 + Tailwind CSS 3 + Vite 6. Gallery grid shows image thumbnails (fetched via `/api/content/uploads/{id}/file`), generic file icon for videos/other types, upload date, MIME type, and file size. Clicking an image opens a lightbox. All API requests include `X-Api-Key` header from `VITE_API_KEY` env var. Multi-stage Dockerfile (Node build → nginx:alpine). `.gitignore` excludes node_modules/, dist/, .env. Vite build verified clean (27 modules, no errors).

**Files created:**
- `HeirloomsWeb/package.json`
- `HeirloomsWeb/vite.config.js`
- `HeirloomsWeb/tailwind.config.js`
- `HeirloomsWeb/postcss.config.js`
- `HeirloomsWeb/index.html`
- `HeirloomsWeb/src/main.jsx`
- `HeirloomsWeb/src/index.css`
- `HeirloomsWeb/src/App.jsx`
- `HeirloomsWeb/Dockerfile`
- `HeirloomsWeb/.gitignore`
- `HeirloomsWeb/README.md`

---

## Entry [2026-05-05]
**Prompt:** Add simple API key authentication to HeirloomsServer. All requests must include `X-Api-Key` header matching the `API_KEY` environment variable. `/health` is exempt for Cloud Run health checks.

**Response:** Added `ApiKeyFilter.kt` (http4k Filter). Updated `AppConfig` with `apiKey` field (env: `API_KEY`, property: `api.key`). Updated `Main.kt` to wrap `buildApp` with the filter when `apiKey` is non-empty — allowing local dev without a key while enforcing auth in production. Filter returns 401 for missing or incorrect keys; `/health` bypasses it unconditionally.

**Files changed/added:**
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/ApiKeyFilter.kt` (new)
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/AppConfig.kt` — added `apiKey` field
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/Main.kt` — wire filter conditionally

---

## Entry [2026-05-06] — Swagger content-type dropdown for /upload

**Prompt:** Add image and video MIME types to the Swagger UI content-type dropdown
for `POST /upload` — currently only `application/octet-stream` is shown.

**Response:** Added 14 additional `receiving(...)` calls to `uploadContractRoute`,
one per content type, mirroring the types already in `ContentTypeExtensions.kt`.
http4k accumulates rather than overwrites when `receiving` is called multiple times,
so all 15 types appear in the generated OpenAPI spec and Swagger UI dropdown. No
test changes required (handler behaviour is unchanged).

Note: `receiving` in http4k 4.46.0 takes a single `HttpMessageMeta<Request>` — there
is no vararg overload.

**Files changed:**
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/UploadHandler.kt`

---

## Entry [2026-05-06] — Deployed v0.11.0 to Cloud Run

Built shadow JAR, Docker image (linux/amd64), pushed to Artifact Registry, deployed
to Cloud Run. Flyway V2 migration applied on startup — `content_hash` column live in
production. Revision: `heirlooms-server-00004-hg4`.

**Gotcha:** first build attempt omitted `--platform linux/amd64`, producing an arm64
manifest list that Cloud Run rejected ("must support amd64/linux"). Always use
`docker build --platform linux/amd64` for Cloud Run images. This is now documented in
PA_NOTES.md (Things that tripped us up) and SE_NOTES.md.

---

## Entry [2026-05-06] — SHA-256 duplicate detection

**Prompt:** Add SHA-256-based duplicate detection to HeirloomsServer. Database migration adds nullable `content_hash VARCHAR(64)` column with index and populates existing rows with random placeholder values. Direct upload computes SHA-256 of incoming bytes and returns 409 Conflict with existing storageKey if a match is found. Confirm flow accepts optional `contentHash` in request body and likewise returns 409 if a duplicate is detected. `UploadRecord` gains a nullable `contentHash` field. 8 new tests covering new file, duplicate, two-distinct-files, null-hash-no-false-positive, and confirm flow variants.

**Files changed/added:**
- `HeirloomsServer/src/main/resources/db/migration/V2__add_content_hash.sql` (new) — adds column, index, populates existing rows
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/Database.kt` — `contentHash` field on `UploadRecord`; `recordUpload` now persists it; new `findByContentHash(hash)`; all SELECTs include `content_hash`
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/UploadHandler.kt` — `sha256Hex` helper; `uploadHandler` checks hash before storing; `confirmUploadHandler` accepts and checks `contentHash`; CONFLICT imported
- `HeirloomsServer/src/test/kotlin/digital/heirlooms/server/UploadHandlerTest.kt` — `findByContentHash` stubbed in shared helper and patched into existing tests that bypassed it; 8 new duplicate-detection tests

---

## Entry [2026-05-06] — Phase 2 thumbnails: video first-frame extraction via FFmpeg

**Prompt:** Extend `ThumbnailGenerator` to support video MIME types (mp4, quicktime, x-msvideo, webm) by extracting the first frame using FFmpeg via `ProcessBuilder`. Add FFmpeg to the Docker image. Add tests.

**What was built:**

- **`Dockerfile`** — added `RUN apt-get update && apt-get install -y ffmpeg && rm -rf /var/lib/apt/lists/*` before the `USER heirloom` instruction so it runs as root.

- **`ThumbnailGenerator.kt`** — extended to handle video types:
  - `THUMBNAIL_VIDEO_MIME_TYPES` private constant for the four supported video types
  - `THUMBNAIL_SUPPORTED_MIME_TYPES` now includes image + video types (used by `tryFetchAndStoreThumbnail` in `UploadHandler.kt`)
  - `generateThumbnail` dispatches to `extractImageThumbnail` or `extractVideoThumbnail` based on MIME type
  - `extractVideoThumbnail`: writes bytes to a temp file, runs `ffmpeg -vframes 1 -f image2`, reads the output JPEG, scales via `scaleAndEncode`, cleans up temp files in finally
  - `scaleAndEncode` extracted as a shared helper used by both image and video paths
  - All failures (FFmpeg not found, non-zero exit, invalid frame bytes) return null gracefully

- **`ThumbnailGeneratorTest.kt`** — 2 new tests:
  - `valid MP4 produces non-null thumbnail` — uses `assumeTrue(isFFmpegAvailable())` so it skips gracefully when FFmpeg is not on the PATH; generates a synthetic 1-frame test video via FFmpeg lavfi source
  - `corrupt video returns null gracefully` — always runs; passes invalid bytes as video/mp4 and expects null
  - Updated `returns null for unsupported MIME type` from `video/mp4` (now supported) to `audio/mpeg`

- **`UploadHandlerTest.kt`** — renamed `no thumbnail generated for unsupported MIME type` to `no thumbnail stored when video bytes are invalid` (video/mp4 is now a supported type; the test still passes because fake bytes cause FFmpeg extraction to fail and return null)

**Test result:** 97 tests, 0 failures, 1 skipped (`valid MP4 produces non-null thumbnail` skipped locally — FFmpeg not installed; runs in Docker)

**Deployed:** Cloud Run revision `heirlooms-server-00009-gdv`. Tagged as **v0.13.0**.

**Files changed:**
- `HeirloomsServer/Dockerfile`
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/ThumbnailGenerator.kt`
- `HeirloomsServer/src/test/kotlin/digital/heirlooms/server/ThumbnailGeneratorTest.kt`
- `HeirloomsServer/src/test/kotlin/digital/heirlooms/server/UploadHandlerTest.kt`

---

## Entry [2026-05-06] — EXIF and video metadata extraction (Phase 1)

**Prompt:** Add EXIF and video metadata extraction to HeirloomsServer. Metadata extracted at upload time alongside thumbnail generation and stored in the database. Six new nullable columns: captured_at, latitude, longitude, altitude, device_make, device_model. MetadataExtractor class extracts from images (via metadata-extractor library) and videos (via ffprobe JSON). Failures never fail the upload. GPS pin icon in HeirloomsWeb for cards with coordinates.

**What was built:**

### Database
- `V4__add_metadata_columns.sql` — adds six nullable columns to the uploads table.

### MetadataExtractor
- `MetadataExtractor.kt` — new class. `MediaMetadata` data class (six nullable fields). `extract(bytes, mimeType): MediaMetadata`. Image path uses `com.drewnoakes:metadata-extractor:2.19.0` to read GPS directory (lat/lon/alt via `GpsDirectory.getGeoLocation()` and `TAG_ALTITUDE`), capture time from `ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL` (UTC), make/model from `ExifIFD0Directory`. Video path writes bytes to a temp file, runs `ffprobe -v quiet -print_format json -show_format -show_streams`, parses `format.tags.creation_time`, `format.tags.location` (ISO 6709 regex), `com.apple.quicktime.make/model`. All exceptions caught, return `MediaMetadata()` with all nulls.

### Upload flow
- `buildApp` gains `metadataExtractor` parameter (default `MetadataExtractor()::extract`).
- Direct upload path (`/upload`): bytes already in hand; metadata extracted after thumbnail.
- Confirm path (`/uploads/confirm`): bytes fetched once from storage (using new `fetchBytesIfNeeded`) and passed to both thumbnail and metadata extraction. Removed the old `tryFetchAndStoreThumbnail` helper.
- All failures wrapped in try/catch — metadata failure never fails the upload.

### JSON
- `UploadRecord.toJson()` private extension: metadata fields included only when non-null (omitted otherwise); `thumbnailKey` still always present (null serialised as JSON null).
- Both `listUploadsHandler` and `uploadHandler` use `toJson()`.

### HeirloomsWeb
- `PinIcon` component: 📍 emoji with coordinates tooltip.
- `UploadCard`: outer div gains `relative` class; pin rendered when `upload.latitude` and `upload.longitude` are both non-null.

### Tests
- `MetadataExtractorTest` (4 tests): GPS JPEG returns correct lat/lon/alt (bytes constructed from raw TIFF/EXIF structure), plain JPEG returns null coords, unsupported MIME type returns all nulls, invalid bytes return null.
- `UploadHandlerTest`: 1 new test — upload succeeds even when metadata extraction throws. Three existing confirm-flow tests updated to stub `storage.get()` (now called once per confirm to fetch bytes for both thumbnail and metadata).
- All 102 tests pass (1 skipped — FFmpeg-dependent video thumbnail test).

**Key decision — trailing lambda fix:** Adding `metadataExtractor` as the last parameter to `buildApp` meant existing tests using trailing lambda syntax for `thumbnailGenerator` bound to the wrong parameter. Fixed by switching to named parameter syntax: `buildApp(..., thumbnailGenerator = { _, _ -> ... })`.

**Key decision — single fetch in confirm path:** Confirm flow previously called `storage.get()` inside `tryFetchAndStoreThumbnail`. With metadata extraction added, refactored to fetch bytes once via `fetchBytesIfNeeded` and pass to both thumbnail and metadata, avoiding a second GCS round-trip.

**Files changed:**
- `HeirloomsServer/build.gradle.kts` — `com.drewnoakes:metadata-extractor:2.19.0` dependency
- `HeirloomsServer/src/main/resources/db/migration/V4__add_metadata_columns.sql` (new)
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/MetadataExtractor.kt` (new)
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/Database.kt` — UploadRecord + all SQL queries
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/UploadHandler.kt` — metadata integration + JSON
- `HeirloomsServer/src/test/kotlin/digital/heirlooms/server/MetadataExtractorTest.kt` (new)
- `HeirloomsServer/src/test/kotlin/digital/heirlooms/server/UploadHandlerTest.kt` — new test + 3 confirm tests updated
- `HeirloomsWeb/src/App.jsx` — PinIcon + relative positioning on UploadCard
