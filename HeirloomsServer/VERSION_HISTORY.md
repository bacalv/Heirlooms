# Version History — HeirloomsServer

---

### 0.9.0 — 6 May 2026

- `generateReadUrl(key: StorageKey): String` added to `DirectUploadSupport`
  interface and implemented in `GcsFileStore` (V4 signed GET URL, 1-hour expiry)
- `GET /api/content/uploads/{id}/url` — new endpoint returning a signed GCS read
  URL; 501 for non-GCS backends, 404 if record not found
- `Dockerfile` simplified — no longer builds inside Docker; expects the fat JAR
  to be pre-built locally with `./gradlew shadowJar`; eliminates Gradle download
  failures caused by Docker Desktop connection drops on macOS

---

### 0.8.0 — 6 May 2026

- `DirectUploadSupport` interface + `PreparedUpload` data class added
- `GcsFileStore` implements `DirectUploadSupport`; switched to `ServiceAccountCredentials`
  for V4 signed URL generation; `prepareUpload()` generates 15-minute signed PUT URLs
- `POST /api/content/uploads/prepare` — returns signed GCS URL; 501 for non-GCS backends
- `POST /api/content/uploads/confirm` — records upload metadata after direct GCS PUT
- Validated: 34.57 MB video uploaded successfully end-to-end

---

### 0.7.0 — 6 May 2026

- Added `FileStore.get(key: StorageKey): ByteArray` to the interface; implemented
  in `LocalFileStore`, `S3FileStore`, and `GcsFileStore`
- New endpoint: `GET /api/content/uploads/{id}/file` — streams file bytes from
  GCS with the correct `Content-Type`; returns 404 if the upload record is missing
- `UploadRecord` extended with `uploadedAt: Instant`; list endpoint JSON now
  includes `uploadedAt`
- `Database.getUploadById(id: UUID): UploadRecord?` added
- `CorsFilter` added — handles OPTIONS preflight and appends
  `Access-Control-Allow-Origin: *` to all responses; wired as the outermost
  filter so preflight requests bypass `ApiKeyFilter`

---

### 0.2.0
- Introduced `FileStore` interface as an abstraction over storage backends
- Introduced `StorageKey` value class to represent the stored file's key/filename
- Renamed `FileStorage` to `LocalFileStore` (implements `FileStore`)
- Added `S3FileStore` — stores files in an Amazon S3 bucket using the AWS SDK v2 async client
- `UploadHandler` now depends on the `FileStore` interface rather than `LocalFileStore` directly
- `AppConfig` extended with `storage.backend`, `s3.bucket`, `s3.region`, `s3.access-key`, `s3.secret-key`
- `application.properties` updated with S3 configuration keys
- `Main` selects backend at startup based on `storage.backend` property, with clear validation messages if S3 properties are missing
- Added `S3FileStoreTest` — 8 tests using a mocked `S3AsyncClient` (no real AWS calls)
- Updated `LocalFileStoreTest` and `UploadHandlerTest` to use new types
- README updated with full Amazon S3 setup instructions including IAM policy

---

### 0.1.0
- Fixed `asServer` import error caused by http4k version `5.14.1.0` package restructuring
- Downgraded http4k to stable `4.46.0.0` where all import paths are correct
- Upgraded JVM toolchain from Java 17 to Java 21

---

### 0.0.1
**Initial release**

- Single endpoint: `POST /api/content/upload`
- Accepts raw file bytes with `Content-Type` header from the Heirloom Android app
- Stores uploaded files as `<UUID>.<extension>` in a configurable local directory
- MIME type to file extension mapping covering common image and video formats
- Storage directory and server port configurable via `application.properties`
- Returns `201 Created` with the saved filename on success
- Returns `400 Bad Request` for empty request body
- Returns `500 Internal Server Error` if the file cannot be written
- 30 unit tests covering MIME mapping, file storage, and HTTP handler behaviour

---

### 0.3.0
- Added PostgreSQL database support with HikariCP connection pool
- Added Flyway for database schema migrations
- New table: `uploads` — stores id, storage_key, mime_type, file_size, uploaded_at
- New endpoint: `GET /api/content/uploads` — returns JSON list of all uploads
- New endpoint: `GET /health` — returns 200/ok, used by Docker health checks
- `AppConfig` extended with `db.url`, `db.user`, `db.password` properties
- `AppConfig` now supports environment variable overrides (used in Docker)
- `S3FileStore` updated with `s3.endpoint-override` support for MinIO compatibility and `forcePathStyle` enabled
- `UploadHandler` now records metadata to the database on every successful upload
- Added `Dockerfile` — multi-stage build producing a minimal JRE Alpine image
- Added Jib plugin to `build.gradle.kts` for Docker image generation without a local daemon
- Bumped version to `0.3.0`

## 0.3.0 — 30 April 2026

- Project renamed from VaultShare → Heirloom → Heirlooms
- Package names updated from `com.vaultshare` to `com.heirloom` throughout
- Docker image renamed from `vaultserver` to `heirloom-server`
- Linux system user renamed from `vault` to `heirloom` in Dockerfile
- Switched from hand-rolled fat JAR (DuplicatesStrategy.EXCLUDE) to Shadow plugin
  with `mergeServiceFiles()` — fixes Flyway PostgreSQL plugin registration which was
  previously silently dropped, causing "relation uploads does not exist" at runtime
- `AppConfig.fromEnv()` fixed to read Docker env vars by exact uppercase name
  (e.g. `S3_ACCESS_KEY`) rather than converting underscores to dots, which caused
  silent config failure when running in Docker Compose
- Domain `heirlooms.digital` registered
- ROADMAP.md and PROMPT_LOG.md added to project root
