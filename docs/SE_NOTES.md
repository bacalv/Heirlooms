# SE Notes — Software Engineer Working Memory

Maintained by the Software Engineer (Claude Code in IntelliJ). Things worth
remembering between sessions that don't belong in PROMPT_LOG.md or PA_NOTES.md.

---

## Getting context at the start of a session

Referencing `@PROMPT_LOG.md` in the first message is enough for most code-level
tasks. For larger or architectural work, also reference `@TEAM.md` and
`@PA_NOTES.md`. I will read them before doing anything.

I also maintain a persistent memory store that survives between sessions:
`~/.claude/projects/-Users-bac-IdeaProjects-Heirlooms/memory/`
This is loaded automatically — Bret does not need to paste it in.

---

## Commit conventions

- Short subject line, blank line, brief body only if the why isn't obvious
- Always include: `Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>`
- Update PROMPT_LOG.md **in the same commit** as the change it documents — not
  as a follow-up
- Create commits freely; **never push** — Bret always does the final push

---

## Project structure

Three Gradle subprojects under `/Users/bac/IdeaProjects/Heirlooms/`:

| Subproject | Package | Purpose |
|---|---|---|
| `HeirloomsApp` | `digital.heirlooms.app` | Android app (Gallery, Capsules, share-sheet) |
| `HeirloomsServer` | `digital.heirlooms.server` | Kotlin/http4k backend |
| `HeirloomsTest` | `digital.heirlooms.test` | Testcontainers integration tests |

---

## Things to remember

- Package is `digital.heirlooms` — `com.heirloom` was the old name, fully replaced
- `HeirloomsTest` requires `~/.testcontainers.properties` with `docker.raw.sock`
  (see PA_NOTES.md for details) — one-time machine setup, not a code problem
- `local.properties` in `HeirloomsApp` must contain:
  `sdk.dir=/Users/bac/Library/Android/sdk`
- Swagger UI is at `http://localhost:8080/docs` (assets served from webjar — no CDN,
  no internet required). OpenAPI spec at `http://localhost:8080/api/content/openapi.json`.
  Routes use http4k-contract, which returns 404 (not 405) for wrong methods on
  contract-owned paths. `Body.binary()` is an extension on `org.http4k.core.Body.Companion`
  from `org.http4k.lens` — import both `org.http4k.core.Body` and `org.http4k.lens.binary`.
- Docker images for Cloud Run **must** be built with `--platform linux/amd64`.
  Building on Apple Silicon without this flag produces an arm64 manifest list that
  Cloud Run rejects. The correct command is in PA_NOTES.md under "Cloud Run deploy
  commands".
- Always run `./gradlew clean shadowJar` (not just `shadowJar`) before `docker build`.
  The Dockerfile glob matches all `*-all.jar` files; without `clean`, an older JAR
  with a higher version string (e.g. `0.9.0` > `0.11.0` lexicographically) gets
  packed into the image instead of the latest one. `run-tests.sh` now calls
  `shadowJar` correctly (was `jar` — fixed in v0.27.0).
- `S3FileStore` implements `DirectUploadSupport` (added in v0.27.0). The `S3Presigner`
  requires `S3Configuration.builder().pathStyleAccessEnabled(true)` when using an
  endpoint override (MinIO / any non-AWS S3 endpoint), otherwise the presigner generates
  virtual-hosted URLs (`{bucket}.{host}`) that fail DNS resolution. Presigned PUT URLs
  should not include `contentType` in the signed headers — clients may PUT with any
  content type and the signature will verify correctly.
- In http4k two-level contract lambdas (`bindContract METHOD to { param: T, _ -> { req -> ... } }`),
  `return` from the inner lambda does not compile as non-local. Extract the inner body
  to a named function and use regular `return` — see `KeysHandler.kt`.
- **`catch (_: Exception)` does not catch `OutOfMemoryError` (v0.43.0):** `OutOfMemoryError` is a
  subclass of `Error`, not `Exception`. Any "best-effort, swallow all failures" block in Android code
  must use `catch (_: Throwable)`, not `catch (_: Exception)`, or an OOM will escape and crash the
  enclosing Worker/coroutine. WorkManager treats an uncaught `Throwable` from `doWork()` as a
  permanent failure (not a retry), so the work silently stops with no notification. Diagnostic
  signature: GCS objects present but no `/confirm` call in server logs. Also wrap the top-level
  uploader call in `UploadWorker.doWork()` in `try/catch (t: Throwable)` as a safety net.

- **`ActivityResultContracts.CaptureVideo()` crashes on Fire OS — use plain `ACTION_VIDEO_CAPTURE` intent (v0.44.0):**
  `CaptureVideo()` passes the output URI via `MediaStore.EXTRA_OUTPUT`. Fire OS camera apps don't honour this extra and crash immediately. Fix: use `StartActivityForResult` with `Intent(MediaStore.ACTION_VIDEO_CAPTURE)` (no output URI). The camera writes to its own media store and returns the content URI in `result.data?.data`. Retrieve it there and route through `copyContentUriToCache`. This bug was masked in earlier builds by the permission crash fixed in v0.43.1 — once the permission was granted the video path was exposed. Pattern: never assume `CaptureVideo()` works on Fire OS; always use the plain intent.

- **`zxing-android-embedded` adds `CAMERA` to merged manifest — must request at runtime (v0.43.1):**
  Adding `com.journeyapps:zxing-android-embedded` merges `CAMERA` into the app's manifest. On Fire OS
  (and Android 6+), once `CAMERA` is in the manifest the system enforces a runtime grant before any
  camera intent (`TakePicture`, `CaptureVideo`) can fire — without it the app crashes. Previously this
  worked because no CAMERA permission was declared and the implicit intent bypassed the check. Fix:
  `ContextCompat.checkSelfPermission` in `launchCamera()` + a `cameraPermissionLauncher`
  (`RequestPermission`) before launching any camera intent.

- **mp4box v2 (npm `mp4box ^2.3.0`) pitfalls (v0.35.0):**
  - No `default` export — import as `const MP4Box = await import('mp4box')`, then `MP4Box.createFile()`.
  - `initializeSegmentation()` returns `{ tracks, buffer }` (one combined init segment), NOT an iterable array.
  - Do NOT use the segmentation API (`setSegmentOptions` / `initializeSegmentation` / `start` / `onSegment`)
    for remuxing regular (non-fragmented) MP4. `initializeSegmentation()` calls `resetTables()` internally,
    which deletes `trak.samples`; `start()` then finds no samples and `onSegment` never fires → empty
    SourceBuffer → black screen.
  - Safe use: feed chunks to a sniffer instance to get `onReady` (codec detection only), then append
    raw decrypted bytes to the SourceBuffer directly. Works for faststart MP4 (moov before mdat).
    For non-faststart (moov at end), fall back to full download + blob URL.