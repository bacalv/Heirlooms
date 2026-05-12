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

- **Kotlin `UUID` comparison (`<`, `>`) uses signed-long order, not PostgreSQL's lexicographic order (v0.45.0):**
  PostgreSQL `CHECK (user_id_1 < user_id_2)` uses string lexicographic ordering on the canonical UUID
  representation. Kotlin's `UUID.compareTo()` compares the most-significant bits as a signed `Long`,
  which gives different ordering for UUIDs whose MSB has bit 63 set (e.g. `e0...` < `00...` in Java
  but `e0...` > `00...` in Postgres). Any Kotlin code that enforces a canonical pair ordering to match
  a Postgres CHECK constraint must use `a.toString() < b.toString()`, not `a < b`.

- **Cloud SQL Proxy path on this machine (one-time setup note):**
  `cloud-sql-proxy` is at `/opt/homebrew/share/google-cloud-sdk/bin/cloud-sql-proxy`.
  `psql` is at `/opt/homebrew/Cellar/libpq/18.3/bin/psql`.
  Neither is in the default PATH — prepend both when scripting DB access.
  ADC must be current (`gcloud auth application-default login`) before the proxy will start.

- **Deleting a user from the live DB — FK dependency order (v0.45.0):**
  The `invites.used_by` FK is the non-obvious blocker. Before deleting a user, NULL out any
  `invites.used_by` references first (`UPDATE invites SET used_by = NULL WHERE used_by = ?`),
  then proceed in this order: `user_sessions` → `wrapped_keys` → `account_sharing_keys` →
  `friendships` → `pending_device_links` → `plots` → `uploads` → `invites` (created_by) → `users`.

- **`ActivityResultContracts.CaptureVideo()` crashes on Fire OS — use plain `ACTION_VIDEO_CAPTURE` intent (v0.44.0):**
  `CaptureVideo()` passes the output URI via `MediaStore.EXTRA_OUTPUT`. Fire OS camera apps don't honour this extra and crash immediately. Fix: use `StartActivityForResult` with `Intent(MediaStore.ACTION_VIDEO_CAPTURE)` (no output URI). The camera writes to its own media store and returns the content URI in `result.data?.data`. Retrieve it there and route through `copyContentUriToCache`. This bug was masked in earlier builds by the permission crash fixed in v0.43.1 — once the permission was granted the video path was exposed. Pattern: never assume `CaptureVideo()` works on Fire OS; always use the plain intent.

- **`zxing-android-embedded` adds `CAMERA` to merged manifest — must request at runtime (v0.43.1):**
  Adding `com.journeyapps:zxing-android-embedded` merges `CAMERA` into the app's manifest. On Fire OS
  (and Android 6+), once `CAMERA` is in the manifest the system enforces a runtime grant before any
  camera intent (`TakePicture`, `CaptureVideo`) can fire — without it the app crashes. Previously this
  worked because no CAMERA permission was declared and the implicit intent bypassed the check. Fix:
  `ContextCompat.checkSelfPermission` in `launchCamera()` + a `cameraPermissionLauncher`
  (`RequestPermission`) before launching any camera intent.

- **Shared item decryption — always check `dekFormat` before unwrapping (v0.45.1–v0.45.3):**
  Any code path that unwraps a DEK must check `dekFormat` (or `thumbnailDekFormat`) first.
  If it is `ALG_P256_ECDH_HKDF_V1`, use `unwrapWithSharingKey` with the session's sharing
  private key. If it is anything else, use `unwrapDekWithMasterKey`. This applies to:
  Android: `PhotoDetailViewModel` (full image + download), `HeirloomsImage` (thumbnail),
  `ShareSheet` (re-sharing a received item). Web: `UploadThumb` (thumbnail),
  `PhotoDetailPage` (full image + download). Forgetting this causes silent decryption failure
  (wrong key type → AES-GCM auth failure caught by the catch block, showing the fallback).

- **Web sharing key API path is `/api/keys/sharing/me`, not `/api/sharing/me` (v0.45.5):**
  Sharing key routes live in `keysContract` which is bound to `/api/keys`. The correct path
  for the web to fetch Bret's own sharing keypair (to decrypt received shared items) is
  `GET /api/keys/sharing/me`. Load this at vault unlock time, await it before `onUnlocked()`,
  so thumbnails start loading with the key already available.

- **Rotation save race: block back navigation while save is in flight (v0.45.4):**
  `rotateAndSave()` fires an async save. If the user navigates back and shares before the
  save API call completes, the share reads the old rotation from the DB. Fix: set
  `rotateInFlight = true` at the start of `rotateAndSave`, false at the end, and make
  `navigateBack()` bail early if `rotateInFlight` is true.

- **`gcloud run deploy` without full flags loses env vars (v0.45.x):**
  Passing only `--image` to `gcloud run deploy` on a service that has env vars and Cloud SQL
  connections will create a revision that starts without those vars — the container will fail
  to connect to the DB. Always pass the full flag set: `--set-env-vars`, `--set-secrets`,
  `--add-cloudsql-instances`, `--service-account`. The correct command is in PA_NOTES.md.

- **EXIF Orientation on Android — three-part fix (v0.45.6):**
  `BitmapFactory.decodeFile()` and `decodeByteArray()` ignore the JPEG EXIF Orientation tag;
  browsers apply it automatically via `image-orientation: from-image`. Fix has three parts:
  (1) `generateThumbnail()` in `Uploader.kt` — apply EXIF rotation to the bitmap before
  encoding, so new thumbnails have correct pixels. Add `androidx.exifinterface:exifinterface:1.3.7`.
  (2) `loadEncryptedContent()` in `PhotoDetailViewModel.kt` — read EXIF from decrypted bytes;
  if non-zero and `upload.rotation == 0` and no staged rotation, auto-stage the EXIF value.
  The staged rotation saves to DB via the existing `navigateBack()` → `saveChanges()` path,
  and the Garden refreshes on re-entry picking up the corrected `rotation` column.
  Do NOT apply EXIF directly to the bitmap pixels — that would double-rotate images that
  were manually corrected to compensate for missing EXIF. The `rotation` column is the
  single source of truth; EXIF auto-staging populates it on first open.

- **Rotation-on-share race — send rotation in payload (v0.45.7):**
  The server reads `rotation` from DB when creating a shared copy. If the sender's rotation
  save (from EXIF auto-stage or manual rotate) races with the share API call, the copy gets
  rotation=0. Fix: include `upload.rotation` from the client in the share request body;
  server reads it as `rotationOverride` and uses it in `createSharedUpload` instead of
  relying solely on the DB value. Client: `HeirloomsApi.shareUpload()` sends `rotation`;
  `ShareSheet.shareWithFriend()` passes `upload.rotation`. Server: `handleShareUpload`
  reads optional `rotation` field; `createSharedUpload` accepts `rotationOverride: Int? = null`.

- **Web sharing key not loaded on auto-unlock paths (v0.45.8):**
  `loadSharingKey` was only called from `VaultUnlockPage.handleUnlock`. Two paths in
  `App.jsx` bypass `VaultUnlockPage` and never loaded the sharing key, leaving
  `getSharingPrivkey()` null so all p256-DEK thumbnails and images failed silently:
  (1) IDB auto-unlock on page refresh (stored pairing material in `loadPairingMaterial()`).
  (2) Login-time auto-unlock when `tryUnlockVaultAfterLogin` succeeds (device key in IDB).
  Fix: extracted `loadSharingKey` as a top-level async function in `App.jsx`; call it in
  both paths, awaited before `setVaultUnlocked(true)`. Pattern: any new auto-unlock path
  in `App.jsx` MUST call `loadSharingKey` before marking the vault as unlocked.

- **`plot-aes256gcm-v1` envelope format (v0.49.0):**
  Per-plot group key wraps item DEKs symmetrically. The envelope layout is identical to
  `master-aes256gcm-v1` — same `encryptSymmetric` / `decryptSymmetric` path, just keyed by
  the plot key rather than the master key. The plot key itself is wrapped asymmetrically using
  `p256-ecdh-hkdf-aes256gcm-v1` (same as device key wrapping). `wrapDekWithPlotKey` /
  `unwrapDekWithPlotKey` in `vaultCrypto.js` handle the symmetric layer.
  Any code path that unwraps a `plot_items.wrapped_item_dek` must check `item_dek_format`:
  if `plot-aes256gcm-v1`, use `unwrapDekWithPlotKey`; if anything else, use the existing DEK
  unwrap path. Same pattern as the `dekFormat` check for shared uploads (SE_NOTES v0.45.1–v0.45.3).

- **Invite link async join — inviter must confirm (v0.49.0 M10 constraint):**
  Invite link redemption is a 2-step flow: recipient stores their sharing pubkey on the invite,
  inviter wraps the plot key and calls the confirm endpoint. The inviter must check
  `GET /api/plots/:id/members/pending` to see waiting joins. Fully async key delivery
  (no inviter action needed) is an M11+ concern.

- **`LEAST/GREATEST` pattern for friendship pair ordering (v0.49.0):**
  The `friendships` table has a CHECK constraint `user_id_1 < user_id_2` (string-lexicographic order
  per SE_NOTES v0.45.0 UUID comparison note). Queries must canonicalize the pair:
  `WHERE user_id_1 = LEAST(?, ?) AND user_id_2 = GREATEST(?, ?)` with the same two UUIDs
  repeated in both positions. See `addMember` in `Database.kt`.

- **Literal path segments consume `_: String` params in http4k contract lambdas (v0.48.0):**
  When a contract path has a mix of UUID params and literal string segments, the two-level lambda
  must declare each literal segment as `_: String` (or `_s1: String, _s2: String` when more than
  one is needed). Example: `"/plots" / plotId / "staging" / uploadId / "approve"` → lambda is
  `{ pId: UUID, _s1: String, uId: UUID, _s2: String -> { request: Request -> ... } }`. Kotlin
  cannot infer these types without explicit annotations — you'll get "Cannot infer a type" errors
  at compile time. Four-segment paths with two UUIDs produce `ContractRouteSpec4<UUID, String, UUID, String>`.
  For routes where the inner handler needs an early `return`, still extract to a named function.

- **`CriteriaEvaluator` usage pattern (v0.47.0):**
  `CriteriaEvaluator.evaluate(criteriaJson, userId, conn)` returns `CriteriaFragment(sql, setters)`.
  The `sql` field is appended to the `conditions` list in `listUploadsPaginated`; `setters` is appended
  to the parallel `setters` list. The `Connection` is passed through because `plot_ref` atoms need
  a DB lookup. Calling code must be inside a `dataSource.connection.use` block — no connection is
  opened by the evaluator itself. Validation at write time (create/update plot) goes through
  `Database.withCriteriaValidation`, which opens its own connection.

- **`just_arrived` predicate discrepancy — evaluator vs legacy path (v0.47.0):**
  `CriteriaEvaluator`'s `just_arrived` atom includes `last_viewed_at IS NULL`. The legacy
  `listUploadsPaginated(justArrived = true)` path does NOT include this condition (historical
  omission). After E4, Android will switch to `plot_id` and the legacy `just_arrived=true`
  parameter can be deprecated. Until then: web Garden (via `plot_id`) and Android (via
  `just_arrived=true`) have slightly different semantics — viewed-but-untagged items vanish
  from web "Just arrived" but remain on Android. Don't "fix" the legacy path without coordinating
  the Android release.

- **`handler@` label for early returns in `HttpHandler` lambdas (v0.47.0):**
  For a simple `HttpHandler = { request -> }` (no path params), labeling the lambda
  (`= handler@{ request -> }`) allows `return@handler Response(...)` for early returns.
  This is the right pattern for single-level handlers. For two-level contract lambdas
  (`bindContract METHOD to { param -> { req -> ... } }`), extract the inner body to a named
  function instead (non-local return does not compile from the inner lambda). Both patterns
  are now in use — see `listUploadsHandler` vs `handleUpdatePlot` in `PlotHandler.kt`.

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