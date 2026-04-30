# Version History

## Heirloom Android App

---

### 0.1.0
- Fixed cleartext HTTP traffic blocked by Android network security policy
- Added `network_security_config.xml` permitting plain HTTP to private network ranges (192.168.x.x, 10.x.x.x, 172.16.x.x) and localhost
- Registered network security config in `AndroidManifest.xml`

---

### 0.0.1
**Initial release**

- Share target activity — appears in Android "Share with" sheet for images and videos
- HTTP POST of raw file bytes to a configurable endpoint with correct `Content-Type` header
- Retry logic with exponential backoff (up to 3 attempts; 5xx and network errors retried, 4xx not retried)
- Coroutine-based background upload replacing Executor/Handler pattern
- System notification showing upload success or failure (falls back to Toast if notification permission denied)
- Settings screen (launchable from app drawer) for configuring the upload URL at runtime
- Upload URL persisted in SharedPreferences and survives app restarts
- Defaults to `http://12.34.56.78:8080/api/content/upload`
- 40 unit tests covering upload logic, retry behaviour, MIME type resolution, and endpoint persistence

---

## HeirloomsServer

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

## 0.3.0 — 30 April 2026

- Project renamed from VaultShare → Heirloom → Heirlooms
- Package names updated from `com.vaultshare` to `com.heirloom` throughout
- Domain `heirlooms.digital` registered
- All "Vault" terminology removed to avoid confusion with secret/credential vaults
- ROADMAP.md and PROMPT_LOG.md added to project root documenting product vision,
  early brainstorm, and full decision history
