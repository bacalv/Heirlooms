# PA Notes — Working Memory

This file is maintained by the PA (claude.ai) as a working memory across sessions.
It captures things that are easy to forget but important to remember — preferences,
patterns, pending decisions, and context that doesn't fit neatly into PROMPT_LOG.md.

---

## Bret's preferences and working style

- Prefers IntelliJ IDEA Community Edition over Android Studio for this project
- Uses IntelliJ's built-in Git UI for commits; does all pushes manually as a
  deliberate human checkpoint before code enters the remote repo
- Dislikes the macOS Finder file picker — use Cmd+Shift+G to navigate by path
- Prefers the Claude Code plugin for hands-on fixes; uses claude.ai for architecture
  and product thinking
- Tends to work in focused sessions and likes a clean summary at the end of each one
- Appreciates being told when something is a one-time setup vs ongoing requirement

---

## Things to always remember

- Package name: digital.heirlooms (not com.heirloom — that was the old name)
- Domain: heirlooms.digital (registered 30 April 2026)
- GitHub: github.com/bacalv/Heirlooms (capital H)
- Current version: v0.30.0 (9 May 2026) — M7 E5: storage class rename, web EXIF, onboarding copy. M7 complete.
  v0.29.0 = M7 E4: web client encryption. v0.28.1 = post-E3 device testing fixes.
  v0.28.0 = M7 E3: Android client encryption. v0.27.0 = M7 E2: backend API for E2EE.
  v0.26.0 = M7 E1: schema + envelope format. v0.25.x = M6 D4 Android + post-ship fixes.
  M8 (multi-user) is next.
- One-time machine setup required: ~/.testcontainers.properties with
  docker.raw.sock path — see PROMPT_LOG.md for details

---

## Pre-launch operational constraints

These constraints govern how M6 (and adjacent work) handles data and infrastructure
operations during the pre-launch period. Bret has explicitly confirmed each.

**Data destruction is acceptable during M6.** None of the current data is
considered "live" — destructive schema changes (DROP TABLE, column type
changes, etc.) are fine if they make implementation cleaner. This means:

- Migrations during M6 can use simple drop-and-recreate patterns rather than
  ALTER-and-migrate flows.
- Pagination changes (D2) can change endpoint signatures without backwards-
  compatibility shims for un-paginated callers.
- EXIF backfill (D2) only needs to handle new uploads; retroactive processing
  of existing rows is optional.

**GCS objects are never auto-deleted outside the user-initiated compost flow.**
The compost flow is the only sanctioned auto-delete path. Any code touching
GCS object lifecycle (deletion, expiry, cleanup) requires explicit confirmation
before implementation. The bucket itself is the durable backup — as long as
files stay in GCS, the D1 re-import tool (Milestone 6) can rebuild any lost
upload rows from content.

**DB backup deferred but on the backlog.** A simple `pg_dump` to a GCS bucket
on a schedule would do it. Roughly one day of work; not an M6 deliverable, not
needed for current operations. Worth doing before the user count grows past
trivial.

**Re-import via SHA256 match (D1) is the recovery mechanism.** If the database
is nuked or corrupted, D1's standalone script scans GCS, recreates `uploads`
rows by content-addressed identity. Lost on recovery: tags, rotation, capsule
membership, compost state. Preserved: actual content. Acceptable trade-off for
the pre-launch period.

---

## Pending decisions / next actions

- Domain mapping: live — heirlooms.digital and api.heirlooms.digital confirmed
  working (end-to-end upload validated from Android app, 6 May 2026)
- Swagger UI confirmed at https://api.heirlooms.digital/docs/index.html
- heirlooms.com: Currently parked on venture.com. Worth monitoring
- License: Deliberately deferred


---

## Recovering from a database wipe

Use this procedure if the `uploads` table is lost due to DB corruption, a deliberate
schema nuke during M6 development, or accidental data loss. The GCS bucket is the
durable backup — as long as objects are in GCS, all `uploads` rows can be recreated.

**What is recovered:** every media file stored in GCS gets a new `uploads` row. The
`uploaded_at` timestamp is set from GCS object metadata (closest available approximation
to the original upload time). Content hash (SHA-256) is computed fresh during import.

**What is lost:** tags, rotation, thumbnail keys, capsule memberships, and compost state.
Any item that was composted but still within its 90-day window will reappear as active.

**Pre-flight (important)**
1. Stop the running HeirloomsServer instance to prevent races with active uploads.
2. Ensure the database schema exists (run `flyway migrate` or start HeirloomsServer once
   so Flyway applies all migrations before the recovery script runs). The script INSERTs
   data; it does not create the schema.
3. Confirm you have credentials:
   - `gcloud auth application-default login` for ADC (no `GCS_CREDENTIALS_JSON` needed), OR
   - set `GCS_CREDENTIALS_JSON` to the service account JSON key string.

**Run the recovery script**

```
cd /path/to/Heirlooms/tools/reimport

export DB_URL="jdbc:postgresql://..."
export DB_USER="heirlooms"
export DB_PASSWORD="..."
export GCS_BUCKET="heirlooms-uploads"
# export GCS_CREDENTIALS_JSON="..."   # only if not using ADC

./gradlew run
```

**Read the output**

A successful recovery looks like:
```
[reimport] summary: scanned=N imported=N skippedExists=0 skippedContentType=M errored=0
[reimport] count parity OK: N GCS media objects, N DB rows
[reimport] sample integrity: checked=5 passed=5
```

If `count parity` reports a mismatch, the delta is logged — investigate before restarting
the server. Common causes: composted items still in GCS (expected), or the script was run
while uploads were in flight (run again after confirming the server is stopped).

If `errored` is non-zero, individual error lines appear above the summary. Investigate and
re-run; the script is idempotent so successfully imported rows are skipped on retry.

**After recovery**
1. Restart HeirloomsServer.
2. Verify the Garden loads and item counts match expectations.
3. Re-apply any tags, rotation adjustments, or capsule memberships manually if needed.

---

## M7 E2EE — key design decisions

**Algorithm ID registry (M7):** Four IDs are defined in `AlgorithmIds` in `EnvelopeFormat.kt`:
`aes256gcm-v1`, `master-aes256gcm-v1`, `p256-ecdh-hkdf-aes256gcm-v1`, `argon2id-aes256gcm-v1`.
Unknown IDs fail loudly. Symmetric and asymmetric IDs are in disjoint sets; calling the wrong
validator with the wrong ID throws immediately.

**BouncyCastle is test-only:** The server never decrypts. BouncyCastle appears only in
`testImplementation` and is used by `EnvelopeFormatTest` and `SchemaMigrationTest` to generate
reference envelopes. Production code has no crypto library dependency beyond `javax.crypto` (for
the server-side Flyway / DB operations, not crypto). The `EnvelopeFormat` library only validates
structure.

**`pending_blobs` table (V12):** Tracks GCS objects uploaded but not yet confirmed (both from
direct-upload initiate and from migration new-blob uploads). Rows are deleted on successful
`/confirm` or `/migrate`. A background job (E2) deletes rows older than 24h and their blobs. This
is the answer to orphaned-blob accumulation from any failure between upload and confirm.

**`user_id = NULL` as M7 sentinel for `wrapped_keys`:** Same pattern as `owner_user_id = NULL`
for plots in v0.23.0. All `wrapped_keys` rows in M7 have `user_id = NULL`. At M8, this becomes
`NOT NULL` with a FK to the users table. Search for `user_id` in `wrapped_keys` when implementing
M8.

**`recovery_passphrase` M8 upgrade path is rough:** The single-row constraint (`id INTEGER DEFAULT 1
CHECK (id = 1)`) works for M7 but the M8 upgrade requires a non-trivial migration: add `user_id`
column, drop `id` column, make `user_id` the PK. It's a single row so data migration is trivial,
but the schema change needs explicit planning. Flag this in the M8 brief.

**Dedup skipped for encrypted uploads:** `findByContentHash` dedup guard is not called for
`storage_class='encrypted'` uploads. Ciphertext is non-deterministic (different DEK + nonce each
time), so the hash never matches. Plaintext-based dedup is incompatible with E2EE; accepted
trade-off is that duplicate uploads produce duplicate rows. Implemented in E2.

**Testcontainers Docker API version note:** The schema migration tests (`SchemaMigrationTest`) use
testcontainers 2.0.5 (same as HeirloomsTest) with `GenericContainer("postgres:16")` rather than
`PostgreSQLContainer`. Reason: testcontainers 1.x bundles docker-java that requests Docker API
v1.32, which Docker Engine 29.x rejects (minimum 1.40). testcontainers 2.0.5 uses docker-java
3.7.x which negotiates correctly. The `postgresql` module has no 2.0.5 release; `GenericContainer`
with manual env vars achieves the same result.

---

## Things that tripped us up (don't repeat)

- ~/.testcontainers.properties must use docker.raw.sock not docker.sock
- Zip files must be built fresh each time
- .idea/ must always be excluded from zips and commits
- local.properties in HeirloomsApp: sdk.dir=/Users/bac/Library/Android/sdk
- GCP permissions must be granted via CLI, not the Console
- Cloud Run domain-mappings only work in us-central1 — not europe-west2
- CNAME value for GoDaddy: ghs.googlehosted.com (no trailing dot)
- Docker images for Cloud Run must be built with `--platform linux/amd64` —
  building on an Apple Silicon Mac produces an arm64 manifest list that Cloud
  Run rejects with "must support amd64/linux"
- Always run `./gradlew clean shadowJar` before `docker build` — the Dockerfile
  glob `HeirloomsServer-*-all.jar` matches all accumulated JARs in build/libs;
  without `clean`, Docker picks the wrong one (e.g. `0.9.0` sorts after `0.11.0`
  lexicographically, so the older JAR wins)
- `private data class` in Kotlin breaks Jackson's OpenAPI schema generator
  at runtime — fails at the `/openapi.json` (or equivalent spec) endpoint,
  not at unit test time. Without a spec-endpoint test, the failure ships.
  The spec-endpoint test added in v0.16.0 is now the canary for any future
  schema-affecting change — keep it in place. Keywords: `private`, `data class`,
  `Jackson`, `OpenAPI`, `schema`.
- Never use `file.readBytes()` before a large network upload on Android — it loads
  the entire file into the Java heap. On a budget device (e.g. Samsung Galaxy A02s,
  201 MB heap limit) this leaves no room for OkHttp's Okio buffers, causing a silent
  `OutOfMemoryError` mid-upload (`Error`, not `Exception`, so `catch (_: Exception)`
  misses it). Always stream from a `File` or `InputStream` directly. Fixed in v0.16.1.
- **Android orientation change mid-upload (v0.17.1).** `ShareActivity` was being recreated
  on device rotation during an upload, destroying upload state and producing a confused UI.
  Fixed for v1 by adding `android:configChanges="orientation|screenSize|keyboardHidden"` to
  `ShareActivity`'s manifest entry — the activity handles the change itself rather than being
  recreated. Tactical fix only; the proper long-term answer is ViewModel + SavedStateHandle
  so upload state survives recreation regardless of cause. Worth migrating when the Android
  app gains a gallery and capsule view (the daily-use increment), because by then there will
  be more state worth preserving than just upload progress.
- **`@ExperimentalLayoutApi` opt-in for `FlowRow` (v0.17.1).** The tag-chip layout uses
  `androidx.compose.foundation.layout.FlowRow`, which is still annotated experimental at
  the Compose version we're on. Build fails without an opt-in. Add
  `@OptIn(ExperimentalLayoutApi::class)` at the function or file level. If a future Compose
  upgrade promotes `FlowRow` to stable, the opt-in can be removed.
- **Upload-confirm contract: tags now travel in the confirm body (v0.17.1).** From v0.17.1
  the confirm body carries the user's chosen tags, and the server validates them (kebab-case,
  length 1–50, regex `^[a-z0-9]+(-[a-z0-9]+)*$`) before persisting. Any future client that
  talks to `POST /api/content/uploads/confirm` needs to know it can send tags in the body.
- **Coil migrated to 3.x (v0.20.2).** Originally added at 2.5.0 in v0.17.1 for the
  share-sheet idle screen's photo grid. Migrated to Coil 3.x as a prerequisite for the
  combined Android Increment 3 + Daily-Use, which substantially expands image-loading
  surfaces (Garden thumbnails, Capsules thumbnails, photo detail, heap rows). Coil 3.x's
  package changed from `coil` to `coil3`; artifact changed to
  `io.coil-kt.coil3:coil-compose`. Pinned at 3.0.4 — Coil 3.1.x+ pulls in JetBrains
  Compose 1.8.x+ which requires compileSdk 35 and AGP 8.6.0+; 3.0.4 is the highest
  compatible with the current compileSdk 34 + AGP 8.3.0 build config. Upgrade 3.0.4 →
  latest deliberately when the build toolchain is also upgraded.
- **Transaction rollback safety with non-local returns (v0.18.0).** The `withTransaction`
  helper uses an explicit `committed` flag tracked in a `try/finally`. The reason for the
  explicit flag rather than "set committed = true at the end of try" is that a **non-local
  return from inside the lambda** (e.g. `return@handler` from a validation failure halfway
  through) jumps past the rest of the try block but still runs the finally. Without the
  flag, that path silently commits a transaction the caller intended to abandon. Pattern:
  `var committed = false; try { ... block(); committed = true; conn.commit() } finally { if
  (!committed) conn.rollback() }`. Follow this pattern for any new transaction code —
  Kotlin's non-local return semantics will bite you if you don't.
- **`UploadRecord.toJson()` is the canonical upload serialisation (v0.18.0).** Capsule
  detail responses include full upload objects for each photo. Rather than build a
  capsule-specific upload serialiser, the existing `toJson()` was promoted from `private`
  in `UploadHandler.kt` to `internal` in `Database.kt` and reused. Future endpoints that
  include uploads in their response should use `UploadRecord.toJson()` — single source of
  truth for what "an upload, viewed from the API" looks like.
- **OpenAPI spec is two contract blocks, merged (v0.18.0).** The server exposes its API
  spec at `/docs/api.json` as a single document, but internally it's two http4k contract
  blocks: content routes bound at `/api/content` and capsule routes bound at `/api`. The
  merge happens at the routing layer in `mergedSpecWithApiKeyAuth`. If adding a new feature
  surface, create a third contract block at its own prefix rather than mixing into the
  existing two. Keep contract blocks separable — the spec generator handles the merge cleanly
  when each block has a consistent path prefix.
- **Manual JSON serialisation in Kotlin is brittle (v0.19.2/v0.19.5).** Three capsule
  serialisers (`toDetailJson`, `toSummaryJson`, `toReverseLookupJson`) were originally
  written by manual string concatenation using Kotlin's triple-quoted (`"""..."""`) strings.
  A quoting bug — the closing `"""` consumed the trailing `"` meant to close the `state`
  field's value — produced malformed output like `"state":"open,"created_at":...`, with the
  comma leaking into the string value. Browsers' strict `JSON.parse` rejected this; users
  got *didn't take* on every capsule list and create. Worse, **all 49 integration tests
  passed** because Jackson's `ObjectMapper.readTree()` (the test client's parser) is lenient
  by default and accepted `open,` as a string value. Fix in v0.19.5: rewrite all three
  serialisers using Jackson's `ObjectNode` API (`mapper.createObjectNode()`, `putArray()`,
  `writeValueAsString()`) and add 13 unit tests with strict round-trip parsing as a
  regression guard. The general principle: prefer `ObjectNode` or data-class serialisation
  over manual string building for any non-trivial JSON. If manual building is genuinely
  necessary, write strict round-trip parse tests at the unit level. Keywords:
  `triple-quoted`, `manual JSON`, `serialisation`, `Jackson`, `lenient parsing`, `regression`.
- **Integration tests with permissive parsers can hide field-value bugs (v0.19.2).** A
  consequence of the previous entry, but generalisable beyond JSON: integration tests that
  round-trip data through a permissive parser (Jackson by default, many JSON-RPC clients,
  protobuf-with-unknown-fields-ignored, etc.) won't notice field-shape bugs that strict
  consumers reject. The 49 integration tests for the capsule API all passed because Jackson
  smoothed over the malformed JSON. The bug only surfaced in the browser. Lesson: integration
  tests catch endpoint-shape and status-code regressions well, but field-value correctness
  is better verified by unit tests with strict assertions on the exact serialised output.
  Adding `mapper.readTree(json).toString()` round-trips with explicit type checks is the
  cheap fix when the codebase relies on integration testing alone. Keywords: `lenient`,
  `strict`, `Jackson`, `regression guard`, `unit test`.
- **SPA routing requires nginx `try_files` fallback (v0.19.3).** When deploying a
  single-page app behind nginx (as HeirloomsWeb is), deep links and page refreshes return
  404 unless nginx is told to fall back to `index.html` for unrecognised paths. The fix:
  an `nginx.conf` with `try_files $uri $uri/ /index.html;` inside the relevant `location`
  block, COPYed into the Dockerfile. Without this, `/capsules/some-uuid` works only when
  the user navigates from within the app — direct URL entry or refresh produces 404.
  Standard SPA-on-nginx pattern; worth recording so deploy-pipeline work doesn't repeat
  the discovery. Keywords: `SPA`, `nginx`, `try_files`, `404`, `deep link`, `refresh`.
- **Post-login redirect: capture the intended destination (v0.19.4).** HeirloomsWeb holds
  auth state in React memory only — page refresh clears it. The original `<Navigate to="/login">`
  redirected unauthenticated users to the login screen but didn't preserve where they were
  trying to go; after login, they always landed at `/`, losing the link they actually wanted.
  Fix: a `RequireAuth` wrapper component that passes `location.pathname + location.search`
  as `state.from` in the redirect, and a `LoginPage` that calls `navigate(from, { replace: true })`
  after successful login. **Interim pattern only** — Bret has noted this should be replaced
  with cookie-based server-side sessions when proper auth lands (probably during Milestone 8's
  multi-user work). Future Milestone 8 work should expect to revisit and replace this pattern,
  not extend it. Keywords: `auth`, `RequireAuth`, `state.from`, `redirect`, `interim`.
- **Lazy cleanup of composted uploads doesn't scale to multi-user (v0.20.0).** The compost
  heap increment uses lazy cleanup — every active-list query triggers a background thread
  that hard-deletes items past their 90-day window (GCS object first, then DB row). At v1
  with a single user this works fine: queries are frequent, items past the window are few.
  At multi-user scale, users who go inactive could leave composted GCS objects sitting
  indefinitely, accumulating storage cost. Milestone 8 (multi-user) should revisit with a scheduled
  cleanup job (Cloud Scheduler hitting an internal endpoint, weekly or daily). The lazy
  approach was a deliberate v1 choice for simplicity. Keywords: `compost`, `lazy cleanup`,
  `Cloud Scheduler`, `multi-user`, `GCS storage`.
- **EXIF extraction is in-process, startup-recovery pattern (v0.23.0).** The
  `ExifExtractionService` runs on server startup and re-queues any `uploads` rows where
  `exif_processed_at IS NULL`. New uploads set `exif_processed_at = NOW()` at INSERT time
  (inline extraction). Recovery is a safety net for crashes. Suitable for a single-instance
  workload; revisit if multi-instance Cloud Run becomes a real configuration (two instances
  would both query the same pending rows).

- **ExoPlayer needs an OkHttp data source for authenticated endpoints (v0.25.7).** ExoPlayer's
  default HTTP stack has no `X-Api-Key` header. Any endpoint protected by `ApiKeyFilter` returns
  401; the player silently fails to load. Fix: add `media3-datasource-okhttp` and configure
  `ExoPlayer.Builder` with `setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(...)))`.
  The `OkHttpDataSource.Factory` accepts a custom `OkHttpClient` where auth interceptors can be
  added. Keywords: `ExoPlayer`, `OkHttpDataSource`, `401`, `ApiKeyFilter`, `media3`.

- **Coil needs `VideoFrameDecoder` for local video URI thumbnails (v0.25.4).** The default Coil
  `ImageLoader` has no video decoder — video URIs render blank. Add `coil-video` artifact and
  register `VideoFrameDecoder.Factory()` in the `ImageLoader`'s `.components { }` block.
  `ShareActivity` needs its own `ImageLoader` (not the singleton) because it loads local
  content:// URIs rather than remote URLs; provide it via `CompositionLocalProvider(LocalImageLoader)`.
  Keywords: `VideoFrameDecoder`, `coil-video`, `share sheet`, `blank thumbnail`.

- **Synchronous crypto in an async function still blocks before the first real await (v0.29.0).**
  `@noble/hashes/argon2` is synchronous. Calling it inside an `async` function runs it
  immediately — before the function's caller has a chance to `await` and yield. In practice:
  `setState('working')` in a React event handler is batched and never flushes before the
  ~3s Argon2id freeze, so the loading spinner never appears. Fix: `await new Promise(resolve
  => setTimeout(resolve, 0))` immediately before the sync KDF call. This yields to the
  event loop, React flushes its state update, the spinner renders, then the freeze begins.
  Pattern: any synchronous heavy computation in an `async` path needs an explicit yield if
  UI feedback must appear first. Keywords: `Argon2id`, `sync`, `setTimeout yield`,
  `React batch`, `spinner`.

- **vitest `toEqual` doesn't always deep-compare `Uint8Array` across buffer origins (v0.29.0).**
  Two `Uint8Array` instances with identical bytes can fail `toEqual` when their underlying
  `ArrayBuffer` objects originate from different WebCrypto operations or `TextEncoder` calls.
  vitest shows "no visual difference" but still reports a failure. Fix: `Array.from(u)` on
  both sides — plain arrays compare reliably. Keywords: `Uint8Array`, `toEqual`, `vitest`,
  `ArrayBuffer`, `no visual difference`.

- **Plain `var` on a ViewModel is not observable by Compose (v0.25.5).** Reading a plain `var`
  during composition and mutating it as a side effect is unreliable — Compose may not recompose
  when the value changes, and state mutation during composition can behave unexpectedly. Use
  `MutableStateFlow` / `StateFlow` and collect with `collectAsStateWithLifecycle()`. Trigger
  reactions from `LaunchedEffect`, not from composition-time `if` blocks.
  Keywords: `StateFlow`, `observe`, `LaunchedEffect`, `side effect`, `composition`.

- **`rememberLazyListState(initialFirstVisibleItemIndex=…)` is creation-only (v0.25.5).** The
  `initialFirstVisibleItemIndex` is only applied when the `LazyListState` is first created.
  Subsequent changes to the value passed in do not scroll the list. To scroll programmatically
  in response to state changes, call `listState.scrollToItem(n)` or `listState.animateScrollToItem(n)`
  from a `LaunchedEffect`. Keywords: `LazyListState`, `scroll`, `rememberLazyListState`.

- **`owner_user_id = NULL` is the v1 single-user sentinel for plots (v0.23.0).** All plots
  in v1 have `owner_user_id = NULL`. At M8, this column becomes NOT NULL with a FK to the
  `users` table; plot endpoints filter to the authenticated user's UUID. Search for
  `owner_user_id` when implementing M8 — all write paths need to switch from NULL to the
  user's UUID.

- **Hard-delete is system-only by design (v0.20.0).** When compost was added in v0.20.0 as
  the first removal mechanism, no public hard-delete endpoint was added alongside it. The
  only path to true hard-delete is the lazy cleanup of items past their compost window. This
  is a deliberate brand and product choice — the 90-day compost window is the safety net
  that lets the rest of the design avoid confirmation dialogs. If a future increment needs a
  true hard-delete path (admin tool, account deletion, etc.), it must be either a new
  internal mechanism or a deliberate decision to add a public endpoint with appropriate
  safeguards. Don't add a parallel hard-delete path without careful consideration. Keywords:
  `hard delete`, `compost`, `system-only`, `90-day window`.

---

## Architectural notes worth remembering

- **Photo detail is a real route, not a modal (v0.19.0).** The original Gallery had a
  lightbox modal for full-size photo viewing. Increment 2 replaced it with a proper
  `/photos/:id` route, because the capsule detail view's photo-grid → photo-detail →
  capsule-name navigation loop required photos to have URLs. Future work that touches photo
  detail should treat it as a routed page (with `useParams`, route state, etc.) — the modal
  pattern is gone. Component: `PhotoDetailPage.jsx` at `/photos/:id`. Gallery thumbnails
  navigate via `useNavigate`, passing the upload as router state for fast first paint with
  a fallback to the full-list fetch.
- **Post-action transition animations: the `?sealed=1` query-param handshake (v0.19.0).**
  When the user clicks *Seal capsule* in the create form, the page POSTs the capsule, then
  navigates to the new capsule's detail view. The detail view needs to know it should run
  the sealing animation rather than render the static sealed state. The handshake: the create
  form navigates to `/capsules/{id}?sealed=1`; the detail page reads the param on mount, runs
  the animation, then removes the param from history with `history.replaceState` so a refresh
  doesn't re-trigger the animation. This pattern is reusable for any future post-action
  transition animation — Milestone 6's delivery animation, recipient-side opening, etc. The
  URL is the message, then it cleans up after itself. Component: `CapsuleDetailPage.jsx`
  mount handler; the animation trigger flag derived from `searchParams.get('sealed')`.
- **The held-lightly typography decision landed (v0.19.0).** The capsule message body's
  typography shift from system serif (open) to italic Georgia (sealed/delivered) was flagged
  in the PA brief as *held-lightly* — the SE was authorised to revise without a new brief if
  it didn't read well in implementation. At first render, italic Georgia at message-body length
  read cleanly; no revision was needed. The decision is now locked. Future work should treat
  the typography shift as a real piece of the brand spec, not as something to revisit.

---

## Team reminders

- The Software Engineer creates commits but Bret always pushes
- Ask the Software Engineer to update PROMPT_LOG.md after significant code changes
- At the start of a new claude.ai session, paste PROMPT_LOG.md, TEAM.md, PA_NOTES.md
- Add IDEAS.md if discussing product direction

---

## GCP Infrastructure

| Resource | Value |
|---|---|
| Project ID | heirlooms-495416 |
| Region (services) | us-central1 |
| Region (Cloud SQL, GCS) | europe-west2 |
| Cloud SQL instance | heirlooms-db |
| Database name | heirlooms |
| Database user | heirlooms |
| Cloud Storage bucket | heirlooms-uploads |
| Service account | heirlooms-server |
| Artifact Registry | heirlooms (europe-west2) |
| HeirloomsServer image | europe-west2-docker.pkg.dev/heirlooms-495416/heirlooms/heirlooms-server |
| HeirloomsWeb image | europe-west2-docker.pkg.dev/heirlooms-495416/heirlooms/heirlooms-web |
| HeirloomsServer Cloud Run URL | https://heirlooms-server-340655233963.us-central1.run.app (revision heirlooms-server-00035-qwc, 2Gi) |
| HeirloomsWeb Cloud Run URL | https://heirlooms-web-340655233963.us-central1.run.app (revision heirlooms-web-00039-g86) |
| Target domain (web) | https://heirlooms.digital (live) |
| Target domain (server) | https://api.heirlooms.digital (live) |

Credentials: Service account JSON key downloaded locally. DB password stored
separately. Neither should ever be committed to GitHub.

---

## GCP permissions — what actually worked

All permissions must be granted via CLI, not the Console:

gcloud projects add-iam-policy-binding heirlooms-495416 \
--member="serviceAccount:heirlooms-server@heirlooms-495416.iam.gserviceaccount.com" \
--role="roles/cloudsql.client"

gcloud projects add-iam-policy-binding heirlooms-495416 \
--member="serviceAccount:heirlooms-server@heirlooms-495416.iam.gserviceaccount.com" \
--role="roles/secretmanager.secretAccessor"

gcloud storage buckets add-iam-policy-binding gs://heirlooms-uploads \
--member="serviceAccount:heirlooms-server@heirlooms-495416.iam.gserviceaccount.com" \
--role="roles/storage.objectAdmin"

---

## Cloud Run deploy commands (current, working)

NOTE: Services run in us-central1. Artifact Registry remains in europe-west2.
Domain mappings only work in us-central1 — do not deploy to europe-west2.

HeirloomsServer:
cd ~/IdeaProjects/Heirlooms/HeirloomsServer
./gradlew clean shadowJar --no-daemon
docker build --platform linux/amd64 \
-t europe-west2-docker.pkg.dev/heirlooms-495416/heirlooms/heirlooms-server:latest .
docker push europe-west2-docker.pkg.dev/heirlooms-495416/heirlooms/heirlooms-server:latest
gcloud run deploy heirlooms-server \
--image europe-west2-docker.pkg.dev/heirlooms-495416/heirlooms/heirlooms-server:latest \
--region us-central1 --platform managed --allow-unauthenticated \
--memory 2Gi \
--set-env-vars "STORAGE_BACKEND=GCS,GCS_BUCKET=heirlooms-uploads,DB_USER=heirlooms" \
--set-env-vars "DB_URL=jdbc:postgresql:///heirlooms?cloudSqlInstance=heirlooms-495416:europe-west2:heirlooms-db&socketFactory=com.google.cloud.sql.postgres.SocketFactory" \
--set-secrets "DB_PASSWORD=heirlooms-db-password:latest" \
--set-secrets "GCS_CREDENTIALS_JSON=heirlooms-gcs-credentials:latest" \
--set-secrets "API_KEY=heirlooms-api-key:latest" \
--service-account heirlooms-server@heirlooms-495416.iam.gserviceaccount.com \
--add-cloudsql-instances heirlooms-495416:europe-west2:heirlooms-db

HeirloomsWeb:
cd ~/IdeaProjects/Heirlooms/HeirloomsWeb
docker build --platform linux/amd64 \
--build-arg VITE_API_URL=https://api.heirlooms.digital \
-t europe-west2-docker.pkg.dev/heirlooms-495416/heirlooms/heirlooms-web:latest .
docker push europe-west2-docker.pkg.dev/heirlooms-495416/heirlooms/heirlooms-web:latest
gcloud run deploy heirlooms-web \
--image europe-west2-docker.pkg.dev/heirlooms-495416/heirlooms/heirlooms-web:latest \
--region us-central1 --platform managed --allow-unauthenticated --port 80

---

## Domain mapping commands (for reference)

gcloud beta run domain-mappings create \
--service heirlooms-web \
--domain heirlooms.digital \
--region us-central1

gcloud beta run domain-mappings create \
--service heirlooms-server \
--domain api.heirlooms.digital \
--region us-central1

DNS records added to GoDaddy:
heirlooms.digital    A     216.239.32.21
heirlooms.digital    A     216.239.34.21
heirlooms.digital    A     216.239.36.21
heirlooms.digital    A     216.239.38.21
heirlooms.digital    AAAA  2001:4860:4802:32::15
heirlooms.digital    AAAA  2001:4860:4802:34::15
heirlooms.digital    AAAA  2001:4860:4802:36::15
heirlooms.digital    AAAA  2001:4860:4802:38::15
api.heirlooms.digital CNAME ghs.googlehosted.com

---

## HeirloomsWeb authentication note

API key entered at login, held in React state only. Cleared on every page reload,
never persisted, never baked into the build. VITE_API_KEY removed — only
VITE_API_URL is a build-time variable.

---

## Key documents in the repo

| File | Purpose |
|---|---|
| PROMPT_LOG.md | Full history of decisions and what was built |
| TEAM.md | Team structure and working practices |
| PA_NOTES.md | This file — PA working memory and preferences |
| SE_NOTES.md | Software Engineer working memory |
| ROADMAP.md | Milestone plan and product vision |
| IDEAS.md | Product brainstorms not yet ready for the roadmap |
| VERSIONS.md | Version history |
---

## View tracking and Just arrived — multi-user scope note

`last_viewed_at` on `uploads` tracks the first time a user opens a photo's detail page.
In v1 (single-user), this is a global flag shared across any viewer. At M8 (multi-user),
view tracking will need to become per-user (e.g., a `upload_views` junction table keyed
on `(upload_id, user_id)`). The current column-on-uploads approach is intentionally simple
for v1 and will need a migration + predicate update.

The deferral only stays honest if M8's ownership model never lets a user read another
user's upload outside of capsule delivery. The moment a second viewer enters the picture,
global view tracking starts lying — *Just arrived* no longer means what it says. The M8
brief must guarantee single-owner reads or this deferral collapses.

The *Just arrived* predicate is: `last_viewed_at IS NULL AND tags = '{}' AND composted_at IS NULL
AND NOT IN active capsule`. The partial index `idx_uploads_just_arrived` covers the three
static conditions; the capsule exclusion is a runtime subquery. Re-check this index at M8 when
the predicate adds a user-id dimension.
