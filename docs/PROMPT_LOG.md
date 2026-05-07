# Heirlooms — Prompt Log

A record of the key decisions and prompts from the founding development session
(April 2026). Each entry captures the original intent, what was built, and any
important context or tradeoffs discovered along the way.

---

## Session — 2026-05-07 (v0.17.0 — Brand, Increment 3a)

**PA brief:** SE Brief — Brand, Increment 3a: Android Static Brand (Icon + Resources + Receive Screen).

**What was built:**
- App icon: VectorDrawable foreground (`ic_launcher_foreground.xml`, ellipses converted to arc paths
  with `<group>` rotations), adaptive icon XML at `mipmap-anydpi-v26/` with `<monochrome>` for
  Android 13+ themed icons, legacy PNGs at all five densities generated via sharp-cli from the
  favicon SVG, Play Store icon at 512×512.
- `res/values/colors.xml` — full brand palette + tints + text shades.
- `ui/theme/Color.kt`, `Type.kt`, `Theme.kt` — Compose brand theme. `HeirloomsTheme { }` ready
  to wrap Activity content.
- `ui/brand/WorkingDots.kt` — Compose three-dot pulse component. `rememberInfiniteTransition`
  called unconditionally inside `repeat(3)` to satisfy Rules of Compose; `reduceMotion` only
  affects which value is used, not whether the composable is called.
- `build.gradle.kts` updated: Compose BOM 2024.01.00, Compose Compiler 1.5.8, JVM 11,
  `buildFeatures { compose = true }`.
- `strings.xml` — full garden voice string set.
- `UploadWorker` — notifications use R.string brand strings; small icon changed from
  `android.R.drawable.ic_menu_upload` to `R.drawable.ic_launcher_foreground`.
- `ShareActivity` — toast messages updated to brand voice ("uploading…" / "Waiting for WiFi
  to plant your photos.").

**Flagged gap — receive screen:**
The current `ShareActivity` has no visible UI. It is a transparent Activity that immediately copies
files, enqueues WorkManager, shows a Toast, and finishes. The brief's "receive-screen Composable"
does not exist. Building a full branded receive screen (photo previews, tag chips, "plant" button)
requires a new Compose Activity — scoped to a follow-up, not a restyling of existing code.

**Key decisions:**
- VectorDrawable ellipse conversion: each SVG ellipse with a rotation transform became a
  `<path>` with arc commands (`M cx-rx,cy A rx,ry 0 1 0 cx+rx,cy A rx,ry 0 1 0 cx-rx,cy Z`)
  inside a `<group android:rotation="..." android:pivotX="..." android:pivotY="...">`. This is
  the standard VectorDrawable pattern since VectorDrawable has no `<ellipse>` element.
- JVM target bumped 1.8 → 11 (Compose minimum). No existing code uses Java 8-only APIs that
  would break at JVM 11.
- Notification small icon changed to `ic_launcher_foreground` (our VectorDrawable). On Android 8+
  notification icons must be monochromatic; the parchment-on-transparent foreground renders as
  white on the system's accent colour, which is correct behaviour.
- Compose UI tests deferred: no emulator/device CI runner configured. Existing JUnit tests
  (135 Kotlin) are unaffected by Compose dependency additions.

---

## Session — 2026-05-07 (v0.17.0 — Brand, Increment 2)

**PA brief:** SE Brief — Brand, Increment 2: Web Arrival and Didn't-Take Animations.

**What was built:**
- `src/brand/animations.js` — `lerp`, `interpolateHexColour`, `prefersReducedMotion` pure helpers
- `src/brand/OliveBranchArrival.jsx` — 3s rAF animation, six phases, `withWordmark` prop, reduced-motion fast-path
- `src/brand/OliveBranchDidntTake.jsx` — 2s rAF animation, partial branch + pause + earth seed + "didn't take" text, reduced-motion fast-path
- `src/brand/OliveBranchArrival.test.jsx` and `OliveBranchDidntTake.test.jsx` — 5 smoke tests (render, withWordmark, reduced-motion onComplete)
- `src/test/setup.js` updated — `Element.prototype.getTotalLength` stub (JSDOM 29 doesn't implement it; `window.SVGPathElement.prototype` patching silently failed because JSDOM 29 exposes SVG constructors on `window`, not as bare globals)
- `src/App.jsx` — `UploadCard` rewritten with 6-state tile machine (`loading/arriving/arrived/error-animating/failed/dismissed`); `FailedTile` component added; `Gallery` tracks `seenIdsRef` to only animate newly-appeared uploads (first load is silent; auto-refresh arrivals animate)
- `src/index.css` — tile animation CSS classes added

**Key decisions:**
- "New" upload = first time an ID is seen in this browser session. First page load → all items skip animation (quiet); auto-refresh → new items animate. This is the right semantic — "moment of arrival" is when the upload is detected by the web client for the first time, not every page load.
- `animateArrivalRef` captured at mount and cleared after first successful use — retry never re-plays the arrival animation.
- Blob URLs revoked properly via `blobUrlRef` (the original code captured `blobUrl` in the closure at effect creation time when it was null, so revocation never ran; fixed here).
- `gallery-tile--arrived-fading-in` CSS is defined but not applied — kept for Increment 3 review or PA follow-up if the hard-cut feels abrupt in production.

**Test count:** 143 total (135 Kotlin + 8 frontend), 142 passing, 1 skipped.

---

## Session — 2026-05-07 (v0.17.0 — Brand, Increment 1)

**PA brief:** SE Brief — Brand, Increment 1: Tokens + BRAND.md + Static Web Application.

**Prompt:** Apply the new Heirlooms brand foundation to HeirloomsWeb. Add design tokens, create BRAND.md, add SVG brand components, restyle header/tags/empty state/working indicator, apply three-colour signal discipline, update garden copy.

**What was built:**
- `docs/BRAND.md` — canonical brand reference: palette, identity system, typography, voice, motion language
- Design tokens in `tailwind.config.js` (theme extension) and `src/index.css` (CSS custom properties on `:root`); body background/text updated to parchment/text-body
- `src/brand/OliveBranchMark.jsx` — 140×200 SVG mark with `state` prop (forest/bloomed apex olive)
- `src/brand/OliveBranchIcon.jsx` — 30×30 simplified icon for header/small contexts
- `src/brand/WorkingDots.jsx` — three-dot pulse animation, `prefers-reduced-motion`, accessible `role="status"` + live region
- `src/brand/EmptyGarden.jsx` — empty gallery state with brand voice copy
- `src/App.jsx` — header replaced with OliveBranchIcon + italic Georgia wordmark; tag chips restyled to forest-08/rounded-chip; Spinner replaced with WorkingDots in card tiles and loading state; EmptyGarden replaces "No uploads yet."; all `text-red-500` replaced with `text-earth font-serif italic`; `index.html` title updated to "Heirlooms — your garden"
- `vitest` + `@testing-library/react` test infrastructure added; 3 smoke tests for OliveBranchMark

**Key decisions:**
- JSX (not TSX) throughout to match project convention; relative imports (no `@/` alias) to match existing `./App` convention
- `EmptyGarden` takes optional `onUpload` prop (no web upload yet, so button is hidden when prop is absent — avoids a dead CTA)
- Tag chips use Tailwind arbitrary values (`text-[11px]`, `px-[9px]`, `py-[3px]`) per brief; these match the brief's specified sizes
- `WorkingDots` replaces `Spinner` for image/video thumbnail loading tiles (the closest existing analogue to "upload-in-progress tile"); `Spinner` component removed as no longer needed
- Forest tints (`forest-04`, `forest-08`, etc.) defined as named Tailwind colors so opacity values stay enumerable and don't drift from the CSS variables

**Test count:** 138 total (135 Kotlin + 3 frontend), 137 passing, 1 skipped.

---

## Session — 2026-05-07 (post-v0.16.1 doc follow-ups)

**PA brief:** Refresh Cloud Run revision identifiers in PA_NOTES.md and add an explicit test count to the v0.16.1 entry in VERSIONS.md.

**Cloud Run revisions verified via `gcloud run services describe`:**
- `heirlooms-server`: `heirlooms-server-00021-fqb` — unchanged. No server code was deployed in v0.16.1, so no revision bump. Confirmed stable.
- `heirlooms-web`: moved from `heirlooms-web-00006-wlf` → `heirlooms-web-00008-9qv`. Web was redeployed for the tag-dropdown fix.

**Test count for v0.16.1:** No new tests were added. The Android OOM fix is a memory-pressure scenario requiring a real constrained-heap device — not unit-testable. HeirloomsWeb has no frontend test runner. Count remains 135 total, 134 passing, 1 skipped (FFmpeg video thumbnail — runs in Docker).

**Also took the soft suggestion from the SE brief:**
- One-line comment on `suppressBlurRef` in `HeirloomsWeb/src/App.jsx` explaining why `e.preventDefault()` / `e.relatedTarget` are unreliable — prevents a future reader from "simplifying" the fix away.
- Strengthened KDoc on `Uploader.uploadViaSigned(File, ...)` with explicit warning against `file.readBytes()`.

**Commit:** `cfbc501` — `docs: post-v0.16.1 follow-ups (refresh Cloud Run revisions, add test count to v0.16.1)`. No tag. v0.16.1 is already tagged.

---

## Phase 1 — Product brainstorm

**Prompt:** "Hi Claude! I'm new here. Could you brainstorm creative concepts?"
Chose: "A project or product" / "I have a rough idea — help me expand it"

**What happened:** A brainstorm around digital legacy and inheritance. Eight concept
cards were generated covering the digital safe, dead man's switch, digital estate
planner, memory book, time-locked messages, collective memory space, digital rights
passport, and digital executor service.

---

**Prompt:** "Tell me more about concept: a personal digital vault app where people
can organise their photos, videos, and messages into a time-capsule-style legacy
that gets unlocked for next of kin."

**What happened:** A detailed breakdown of how the vault would work in two phases
(building the vault while alive; the unlock after death), a feature comparison
against cloud storage, a UI sketch, and an analysis of key challenges. The milestone
delivery mechanic — a video arriving on a child's 18th birthday — was identified as
the single most powerful differentiator.

---

## Phase 2 — Android app (v1, Java)

**Prompt:** "I'd like to start off by creating an Android app, just for me.
When I use the universal Android 'share with' function for any image or video,
I can choose 'Share with APPLICATION_NAME'. The application simply does a HTTP POST
to AN_ENDPOINT with the video/photo contents as the body. Content-Type should reflect
the file type. APPLICATION_NAME and AN_ENDPOINT are configurable in application
properties. I should be able to build the project using Gradle."

**What was built:** A complete Android project in Java. `ShareActivity` registers as
a share target for `image/*` and `video/*`, reads the file via `ContentResolver`,
and HTTP POSTs it using OkHttp. App name and endpoint configured via
`assets/config.properties`. Transparent activity theme so no UI flash appears.

---

**Prompt:** "I have Android Studio installed and I've opened the folder but when I
Build → Make Project I see the error... [missing gradle-wrapper.jar]"

**Decision:** Added `gradle/wrapper/gradle-wrapper.jar` to the project and added a
`run-tests.sh` script that auto-downloads it if missing, so the project works without
Android Studio being involved in the build setup.

---

## Phase 3 — Kotlin rewrite + settings screen

**Prompt:** "Could we use Kotlin rather than Java for this Android project?"

**What was built:** Full rewrite of the Android app in Kotlin. Split into four files:
`ShareActivity.kt`, `Uploader.kt`, `EndpointStore.kt`, and `SettingsActivity.kt`.
Added a settings screen accessible from the share sheet so the endpoint URL can be
changed without editing config files. Endpoint stored in `SharedPreferences`.

---

## Phase 4 — Backend server (HeirloomsServer)

**Prompt:** "Could we now look at the server side? I'd like to look at what we need
to do in order to have a very simple server, that the android app could post images
or videos to, that would store them."

**Decision:** Kotlin/http4k server chosen over Spring Boot for minimal footprint.
PostgreSQL for metadata, MinIO (S3-compatible) for file storage. Flyway for database
migrations. HikariCP connection pool. AWS SDK v2 S3 async client with
`forcePathStyle(true)` for MinIO compatibility.

**What was built:** `HeirloomsServer` with four endpoints:
- `POST /api/content/upload` — receives file bytes, stores to S3/MinIO, records
  metadata in PostgreSQL, returns storage key
- `GET /api/content/uploads` — returns JSON array of all uploads
- `GET /health` — returns 200 "ok"

`AppConfig` reads from `application.properties` locally or directly from environment
variables (by exact uppercase name: `DB_URL`, `S3_ACCESS_KEY` etc.) when running in
Docker. The env var approach was fixed after an early bug where underscores were
converted to dots (`S3_ACCESS_KEY` → `s3.access.key`) but the property lookup used
hyphens (`s3.access-key`), causing silent config failure.

---

## Phase 5 — Docker + end-to-end tests (HeirloomsTest)

**Prompt:** "I wonder if we might want a third 'project' within Heirloom that runs
integration tests against the server."

**What was built:** `HeirloomsTest` — a Gradle project using Testcontainers and OkHttp
that spins up the full Docker Compose stack (PostgreSQL, MinIO, minio-init, HeirloomsServer)
and runs API tests and journey tests against it.

**Key decisions:**
- Testcontainers 2.0.5 chosen over 1.x because Docker Engine 29.x raised its minimum
  API version to 1.40, and docker-java (used by Testcontainers 1.x) hardcoded API
  version 1.32, making it incompatible. Testcontainers 2.x handles API negotiation
  correctly.
- Playwright removed from journey tests because Chromium cannot reach the Testcontainers
  socat proxy port from its sandboxed process. All journey tests use OkHttp instead.
- Shadow plugin (8.1.1) with `mergeServiceFiles()` used instead of a hand-rolled fat
  JAR task. The original `DuplicatesStrategy.EXCLUDE` approach silently dropped
  `META-INF/services/org.flywaydb.core.extensibility.Plugin`, which prevented the
  Flyway PostgreSQL plugin from registering, causing "relation uploads does not exist"
  errors even though the migration file was present and correctly named.

---

## Key bugs fixed during HeirloomsTest development

**Docker socket on macOS** — `/var/run/docker.sock` and `~/.docker/run/docker.sock`
return stub 400 responses on macOS Docker Desktop. The working socket is at
`~/Library/Containers/com.docker.docker/Data/docker.raw.sock`. Fixed by auto-detecting
socket candidates in `HeirloomsTestEnvironment` and setting `DOCKER_HOST` accordingly.

**Ryuk failing before test code** — Ryuk's static initialiser fires before any test
code runs and fails independently. Fixed by `ryuk.disabled=true` in
`~/.testcontainers.properties`.

**Testcontainers 2.x API changes** — `withLocalCompose()` was removed (local compose
is now the default); `junit-jupiter` artifact renamed to `testcontainers-junit-jupiter`.

**`AppConfig.fromEnv()` hyphen vs dot mismatch** — see Phase 4 above.

**Flyway "0 migrations applied"** — `DuplicatesStrategy.EXCLUDE` silently dropped the
Flyway service registration file. Fixed by switching to Shadow plugin with
`mergeServiceFiles()`.

**`docker-compose.yml` port format** — Testcontainers 2.x requires ports declared as
`"8080"` (no host binding) rather than `"8080:8080"` so it can manage port mapping
via its socat ambassador container.

**`version:` field in docker-compose.yml** — removed as it is obsolete in Compose V2
and was generating warnings.

**GRADLE_OPTS native crash** — `GRADLE_OPTS="-Dorg.gradle.native=false"` added
throughout Docker builds to prevent a SIGSEGV crash of `libnative-platform-file-events.so`
on Apple Silicon (ARM64).

---

## One-time machine setup

```
~/.testcontainers.properties

docker.host=unix:///Users/YOUR_USERNAME/Library/Containers/com.docker.docker/Data/docker.raw.sock
testcontainers.reuse.enable=false
ryuk.disabled=true
```

Replace `YOUR_USERNAME` with your macOS username. This is the only persistent
machine-level configuration required. Everything else is self-contained in the repo.

---

## Final state

**10 tests passing** across two test classes:

`UploadApiTest`: health check, POST image returns 201, POST video returns 201, POST
without Content-Type returns 201, POST empty body returns 400, GET uploads returns
JSON array, uploaded file appears in listing.

`UploadJourneyTest`: upload and verify in listing, multi-type upload journey,
health endpoint reachable.

Build time on a warm cache (Docker layers cached): under 20 seconds.
Build time on a cold cache (first run): 3–5 minutes for dependency downloads.

---

## Domain registration

**Date:** 30 April 2026

`heirlooms.digital` registered. Several names were considered during this session:

- `digital-legacy.com` — available but too generic
- `heirloom.digital` — strong first choice, but misspelled as "hierloom" on first
  attempt (fat fingers)
- `heirloom.co.uk` — correct spelling but country-locked
- `heirlooms.digital` — chosen: plural feels warmer ("a collection" rather than
  "a single object"), .digital is thematically appropriate, not country-locked
- `heirlooms.com` — parked on venture.com, potentially acquirable in future if the
  project grows to warrant it

The project name was updated from **Heirloom** to **Heirlooms** to match the domain.
The rename is the first task queued for the next development session.

---

## Session — 30 April 2026 (v0.3.0 polish + package rename)

**Fix: `Uploader.kt` compile error**

`IntRange` implements `Iterable`, not `Sequence`, so calling `.zip(Sequence)` on it
failed to compile. Fixed by inserting `.asSequence()` before `.zip()`.

---

**Tag: v0.3.0**

Annotated git tag `v0.3.0` created on `main` to mark the state of the project at the
end of the founding development session.

---

**Package rename: `com.heirloom` → `digital.heirlooms`**

Queued at the end of the previous session to align with the `heirlooms.digital` domain.
Completed across all three subprojects — 22 Kotlin source files, 3 `build.gradle.kts`
files, and the corresponding source directory layout:

- `HeirloomsApp`: `com/heirloom/app/` → `digital/heirlooms/app/`
- `HeirloomsServer`: `com/heirloom/server/` → `digital/heirlooms/server/`
- `HeirloomsTest`: `com/heirloom/test/` → `digital/heirlooms/test/`

---

**TEAM.md added**

Documents the team structure: Bret Adam Calvey as Founder & CTO, the PA (claude.ai)
for strategic/architectural thinking, and the Software Engineer (Claude Code in
IntelliJ) for hands-on implementation. Establishes that the Software Engineer commits
but Bret always does the final push.

---

**PA_NOTES.md added**

The PA's (claude.ai) working memory file, committed to the repo so it persists across
sessions and is visible to all team members. Captures Bret's preferences and working
style, project facts to always remember, pending decisions, known gotchas, and team
reminders.

---

**SE_NOTES.md added**

The Software Engineer's (Claude Code) own working memory file. Covers how to get
session context, commit conventions, project structure at a glance, and code-level
things worth remembering between sessions.

---

**docs/chats/2026-04-30-initial-chat.md added**

The original claude.ai founding session chat, formatted as markdown. 356 turns
spanning 24–30 April 2026. Converted from `Original_chat.txt` (now removed):
day-separated sections, `**Human**` / `**Claude**` blocks, action/tool lines
stripped, duplicate lines deduplicated.

---

**Docs reorganisation**

All markdown files except `README.md` moved from the project root into `docs/`:
`PROMPT_LOG.md`, `ROADMAP.md`, `TEAM.md`, `PA_NOTES.md`, `SE_NOTES.md`.
`README.md` updated with a Docs table linking to each file with a description.

## Session — 1 May 2026 (Milestone 3 planning + deployment research)

**Milestone 3 patch produced**

The PA produced a patch for the Software Engineer containing three new files
in a new `deploy/` folder at the repo root:
- `docker-compose.yml` — production compose with named volumes, restart policies,
  host port binding (8080:8080), and a `build:` directive pointing at HeirloomsServer
- `.env.example` — credential template; the real `.env` is gitignored
- `README.md` — step-by-step setup guide for a VPS or home server

Key differences from the test compose:
- Credentials sourced from .env (not hardcoded)
- Named volumes: postgres_data, minio_data (data survives container restarts)
- `restart: unless-stopped` on postgres, minio, and heirloom-server
- Port 8080 bound to the host as 8080:8080
- `build:` context points to ../HeirloomsServer so `docker compose up --build`
  compiles and packages the JAR automatically

HeirloomsTest's docker-compose.yml was not modified.

---

**Deployment research — cloud and VPS options evaluated**

Google Cloud Run + Cloud SQL + Cloud Storage was evaluated as a cloud path.
Viable but Cloud SQL alone costs ~£10-15/month, which is disproportionate for
a personal project at this stage.

Hetzner CX22 (~€4/month) chosen as the recommended deployment target.
Runs the full stack on a single VPS via the Milestone 3 docker-compose.yml.

---

**Agreed next steps (queued for next session)**

1. Provision a Hetzner CX22 VPS
2. Add a DNS A record: `heirlooms.digital` → VPS IP (TTL 300)
3. SSH in, clone repo, copy `.env.example` to `.env`, fill in passwords
4. `docker compose up -d --build` from the `deploy/` folder
5. Verify: `curl http://heirlooms.digital:8080/health`
6. Update Android app endpoint to `http://heirlooms.digital:8080/api/content/upload`

HTTPS (via Caddy reverse proxy + Let's Encrypt) deferred to Milestone 4.

---

## Session — 2026-05-01 (Milestone 3 — self-hosted deployment)

**deploy/ folder added**

Three files added to a new `deploy/` folder at the repo root:
- `docker-compose.yml` — production compose with named volumes, restart policies,
  host port binding (8080:8080), and a `build:` directive pointing at HeirloomsServer
- `.env.example` — credential template; the real `.env` is gitignored
- `README.md` — step-by-step setup guide for a VPS or home server

Key differences from the test compose:
- Credentials sourced from .env (not hardcoded)
- Named volumes: postgres_data, minio_data (data survives container restarts)
- restart: unless-stopped on postgres, minio, and heirloom-server
- Port 8080 bound to the host as 8080:8080
- build: context points to ../HeirloomsServer so `docker compose up --build`
  compiles and packages the JAR automatically

HeirloomsTest's docker-compose.yml was not modified.

---

## Session — 2026-05-01 (Swagger UI / OpenAPI documentation)

**Prompt:** "Is it possible to add swagger to our backend server so I can access
detailed API documentation in a browser and use it as a simple client for our server?"

**What was built:** Interactive API documentation served at `GET /docs`, backed by
a fully self-hosted Swagger UI (no CDN dependency).

- Added `http4k-contract`, `http4k-format-jackson`, and `org.webjars:swagger-ui:5.11.8`
  to `build.gradle.kts`
- Converted `UploadHandler.kt` from plain `routes()` to http4k contract routing;
  the contract auto-generates and serves an OpenAPI 3.0 spec at
  `GET /api/content/openapi.json`
- Swagger UI assets served from the webjar on the classpath at `/docs/` via
  http4k's `static(ResourceLoader.Classpath(...))` handler
- `swagger-initializer.js` overridden via a specific route listed before the static
  handler, pointing Swagger UI at `/api/content/openapi.json`
- `GET /docs` redirects to `/docs/index.html`
- `Body.binary(ContentType("application/octet-stream")).toLens()` declared in the
  upload route meta so Swagger UI renders a file picker for the POST endpoint.
  Note: `binary` is an extension on `org.http4k.core.Body.Companion` from the
  `org.http4k.lens` package — `org.http4k.lens.Body` does not exist in http4k 4.46
- Updated `UploadHandlerTest.kt`: `GET /api/content/upload` now returns 404 (not 405)
  because http4k-contract does not produce METHOD_NOT_ALLOWED for wrong methods on
  contract-owned paths

**Key decision — CDN vs webjar:**
CDN approach (unpkg) was considered first — simpler, no extra dependency, but requires
the browser to have internet access. Webjar was preferred: all assets are bundled in the
fat JAR, the server is fully self-contained, and no internet access is needed at runtime.

**Files changed:**
- `HeirloomsServer/build.gradle.kts`
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/UploadHandler.kt`
- `HeirloomsServer/src/test/kotlin/digital/heirlooms/server/UploadHandlerTest.kt`
- `HeirloomsServer/README.md`
- `HeirloomsServer/PROMPT_LOG.md`
- `docs/SE_NOTES.md`

---

## Milestone 3 — 2026-05-05 (GCP deployment, GCS storage, API key auth, end-to-end test)

Full-stack milestone: server deployed to Google Cloud Run, storage migrated to GCS,
API key authentication added across server and Android app, end-to-end photo upload
confirmed from a real Android device.

**What was built:**

### HeirloomsServer

- **`GcsFileStore.kt`** — new `FileStore` implementation backed by Google Cloud Storage.
  Service account credentials are supplied as a JSON string via the `GCS_CREDENTIALS_JSON`
  environment variable and loaded in-memory; credentials are never written to disk.
  Activated by setting `STORAGE_BACKEND=GCS`, `GCS_BUCKET`, and `GCS_CREDENTIALS_JSON`.

- **Cloud SQL socket factory** — added `com.google.cloud.sql:postgres-socket-factory:1.19.0`
  dependency to support IAM-authenticated connections to Cloud SQL (PostgreSQL) via the
  Cloud SQL Auth Proxy socket.

- **`ApiKeyFilter.kt`** — http4k `Filter` that enforces `X-Api-Key` header authentication
  on all requests. `/health` is unconditionally exempt (required for Cloud Run health checks).
  Returns HTTP 401 for missing or incorrect keys. Key value read from the `API_KEY`
  environment variable via `AppConfig`. Filter is only wired in `Main.kt` when `apiKey`
  is non-empty, so local development works without a key.

### HeirloomsApp

- **`EndpointStore.kt`** — added `getApiKey()` / `setApiKey()` backed by SharedPreferences
  key `api_key`.
- **`Uploader.kt`** — added optional `apiKey: String?` parameter to `upload()`;
  injects `X-Api-Key` header when non-blank.
- **`SettingsActivity.kt` / `activity_settings.xml`** — added a masked password input
  field for the API key alongside the existing endpoint URL field.
- **`ShareActivity.kt`** — reads API key from `EndpointStore` and passes it to `upload()`.

### GCP infrastructure provisioned

- **Cloud Run** — HeirloomsServer deployed as a containerised service (Artifact Registry,
  Jib build)
- **Cloud SQL** — PostgreSQL instance, connected via Cloud SQL socket factory
- **Cloud Storage** — GCS bucket for file storage
- **Secret Manager** — secrets for API key and service account credentials
- **Service account** — created with roles scoped to Cloud SQL, GCS, and Secret Manager

### End-to-end validation

Photo uploaded from a physical Android device → Cloud Run endpoint → stored in GCS bucket.
Upload confirmed by checking the GCS bucket directly.

**Files changed/added:**
- `HeirloomsServer/build.gradle.kts` — GCS and Cloud SQL socket factory dependencies
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/GcsFileStore.kt` (new)
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/ApiKeyFilter.kt` (new)
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/AppConfig.kt` — GCS fields, `apiKey`
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/Main.kt` — GCS and filter wiring
- `HeirloomsApp/app/src/main/kotlin/digital/heirlooms/app/EndpointStore.kt`
- `HeirloomsApp/app/src/main/kotlin/digital/heirlooms/app/Uploader.kt`
- `HeirloomsApp/app/src/main/kotlin/digital/heirlooms/app/SettingsActivity.kt`
- `HeirloomsApp/app/src/main/kotlin/digital/heirlooms/app/ShareActivity.kt`
- `HeirloomsApp/app/src/main/res/layout/activity_settings.xml`
- `HeirloomsApp/app/src/main/res/values/strings.xml`

---

## Session — 2026-05-05 (Swagger UI — API key auth integration)

**Prompt:** "When I access the swagger UI using the Cloud Run URL /docs I get
unauthorised. The security filter is kicking in. We need to fix the filter so
it excludes the docs path, and add an Authorize mechanism in Swagger UI for
the API key."

**What was built:**

- **`ApiKeyFilter.kt`** — added `path.startsWith("/docs")` exemption so the
  Swagger UI and all its static assets load without credentials.

- **`/docs/api.json` route** — new handler (`specWithApiKeyAuth`) that calls
  the http4k contract internally to get the raw OpenAPI spec, then patches it
  with Jackson before returning it:
  - Adds `components.securitySchemes.ApiKeyAuth` (`type: apiKey`, `in: header`,
    `name: X-Api-Key`)
  - Adds a global `security: [{ApiKeyAuth: []}]` block
  - Overrides `servers` to `[{url: "/api/content"}]` (http4k generates `"/"` which
    caused Swagger UI to POST to `/upload` instead of `/api/content/upload`)
  - Removes per-operation `security: []` entries — an empty array overrides the
    global block, so Swagger UI was silently dropping the key after re-authorisation

- **`swaggerInitializerJs`** updated:
  - `url` changed from `/api/content/openapi.json` to `/docs/api.json`
    (the patched spec endpoint, already exempt from the filter)
  - `persistAuthorization: true` — key survives page refresh
  - `tryItOutEnabled: true` — request form open by default, no extra click

- **`docker-compose.yml`** (test) — added `API_KEY: "${API_KEY:-}"` so the key
  can be injected for manual local testing without breaking the e2e tests
  (which run without a key and rely on the filter being inactive).

**Key gotcha — per-operation `security: []`:**
http4k generates `"security": []` on every operation when no contract-level
security is configured. In OpenAPI 3, an empty array means "no security" and
overrides the global block. This caused Swagger UI to stop sending the key after
logout and re-authorisation, despite the Authorize dialog appearing to work.
Fix: remove the per-operation `security` field so operations inherit global.

**Files changed:**
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/ApiKeyFilter.kt`
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/UploadHandler.kt`
- `HeirloomsTest/src/test/resources/docker-compose.yml`

**Validated end-to-end:**
Deployed to Cloud Run (revision `heirlooms-server-00006-ckl`). Swagger UI loads at
`https://heirlooms-server-340655233963.europe-west2.run.app/docs` without credentials,
API key authorisation works via the Authorize button, and POST /upload returns 201.
Tagged as **v0.6.0**.

---

## Milestone 4 — Web gallery UI + large file support (6 May 2026)

### Part 1 — File proxy endpoint + HeirloomsWeb

**Prompt:** Build Milestone 4: a file proxy endpoint on HeirloomsServer and a new HeirloomsWeb sub-project (React gallery).

**What was built:**

*HeirloomsServer:*
- `FileStore.get(key)` added to interface; implemented in LocalFileStore, S3FileStore, GcsFileStore
- `GET /api/content/uploads/{id}/file` — streams file bytes from GCS with correct Content-Type; 404 if not found
- `uploadedAt: Instant` added to UploadRecord; list endpoint JSON now includes it
- CORS filter added (all origins); handles OPTIONS preflight before ApiKeyFilter

*HeirloomsWeb (new sub-project):*
- React 18 + Tailwind CSS + Vite; gallery grid with image thumbnails, file icons for videos, upload date, MIME type, file size; lightbox on click
- API key entered at login per session, held in React state only (cleared on reload, never stored)
- Images fetched as blob URLs (fetch + createObjectURL) so X-Api-Key header can be sent
- Multi-stage Dockerfile: Node 22 build → nginx:alpine

Deployed to Cloud Run (revision `heirlooms-server-00007-7vw`). Gallery confirmed working at http://localhost:5173 against production server. Tagged as **v0.7.0**.

---

### Part 2 — Large file upload via GCS signed URLs

**Problem discovered:** Uploading a 34.57 MB video from the Android app returns HTTP 413. Root cause: Cloud Run enforces a hard 32 MB request body limit at the load balancer level — no server-side config change can fix this.

**Solution — three-step signed URL upload flow:**

1. Mobile app `POST /api/content/uploads/prepare` with `{"mimeType":"video/mp4"}` → server returns `{"storageKey":"uuid.mp4","uploadUrl":"https://...signed-gcs-url..."}` (15-minute expiry)
2. Mobile app `PUT {signedUrl}` with file bytes **directly to GCS** — bypasses Cloud Run entirely, no size limit
3. Mobile app `POST /api/content/uploads/confirm` with `{"storageKey":"...","mimeType":"...","fileSize":...}` → server records metadata in the database

**Server changes:**
- `DirectUploadSupport` interface + `PreparedUpload` data class (new file)
- `GcsFileStore` now implements `DirectUploadSupport`; switched from `GoogleCredentials` to `ServiceAccountCredentials` so the credentials can sign URLs (V4 signing); `prepareUpload()` generates a signed PUT URL with 15-minute expiry
- `POST /api/content/uploads/prepare` and `POST /api/content/uploads/confirm` added as contract routes; prepare returns 501 if the storage backend doesn't support direct upload (i.e. local/S3)

**Android app changes:**
- `Uploader.uploadViaSigned()` — new method implementing the three-step flow; no API key sent on the GCS PUT (signed URL is self-authenticating)
- `ShareActivity` now calls `uploadViaSigned()` instead of `upload()`; derives base URL from stored endpoint by splitting on `/api/`
- OkHttp write timeout increased from 120s → 300s to accommodate large video uploads

**Note for deployment:** The new server image must be built and deployed to Cloud Run before large video uploads will work. The existing `POST /api/content/upload` direct endpoint still works for small files. No change to stored endpoint format in the Android app.

**Validated end-to-end (6 May 2026):**
Server deployed to Cloud Run (revision `heirlooms-server-00008-vt7`). Fresh APK installed via `adb install -r`. Large video (34.57 MB) shared successfully from Android — three-step signed URL flow completed transparently. Tagged as **v0.8.0**.

---

## Session summary — 6 May 2026 (continued from Milestone 4)

### Video player + streaming (v0.9.0)

**Video player:** HeirloomsWeb now shows a video icon with "Click to play" for video files in the gallery. Clicking opens a native `<video controls>` modal.

**Streaming:** Initial implementation fetched the full video as a blob before playback (slow for large files). Replaced with GCS signed read URLs — a new `GET /api/content/uploads/{id}/url` endpoint generates a 1-hour signed URL; the video element uses it as `src` directly. The browser handles streaming, buffering, and seeking natively. No full download required.

**Dockerfile fix:** Docker Desktop on macOS was dropping the build daemon connection during long Gradle downloads inside the container, requiring a manual Docker restart every deployment. Fixed by removing the multi-stage build: JAR is now built locally with `./gradlew shadowJar` first, then `docker build` simply copies the pre-built JAR into a JRE image. Build time: ~2 seconds. PA_NOTES.md updated with the new deploy sequence.

**Validated end-to-end:** Video streaming confirmed working. Server deployed to Cloud Run revision `heirlooms-server-00009-58m`. Tagged as **v0.9.0**.

---

## Session — 2026-05-06 (All endpoints in Swagger)

`GET /uploads/{id}/file` and `GET /uploads/{id}/url` were registered directly
in `routes()`, making them invisible to the http4k contract and Swagger UI.

**Fix:** converted both to `ContractRoute` entries inside the contract.

Key discovery: `String.div(PathLens<A>)` is a top-level extension in
`org.http4k.contract.ExtensionsKt` and requires `import org.http4k.contract.div`.
Without this import, the `/` operator was unresolved and the meta block failed
with cascade errors.

`ContractRouteSpec1<A>.div(String)` (member method) returns
`ContractRouteSpec2<A, String>`, whose `Binder.to` takes `(A, String) -> HttpHandler`.
The trailing String parameter is the constant path segment ("file"/"url") and is
ignored with `_` in the handler.

String-based paths like `"/uploads/{id}/file"` in contract routes do NOT do
path variable matching — they are treated as literal strings, returning 404 for
all real UUID paths. Typed path lenses are required for routing to work.

A malformed UUID in the path returns 404 (route doesn't match) not 400
(the typed lens fails silently and falls through to the 404 handler).

**Files changed:**
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/UploadHandler.kt`
- `HeirloomsServer/src/test/kotlin/digital/heirlooms/server/UploadHandlerTest.kt`

---

## Session — 2026-05-06 (Hardcode server URL in Android app)

Now that the app targets `https://api.heirlooms.digital` exclusively, the endpoint
URL is no longer user-configurable. The settings screen is reduced to API key only.

**Changes:**

- `EndpointStore.kt` — removed `get()`, `set()`, `isConfigured()`, `DEFAULT_ENDPOINT`,
  and `KEY_ENDPOINT`. Removed `contains()` from `PreferenceStore` interface (was only
  needed by `isConfigured()`). Class now persists the API key only.
- `ShareActivity.kt` — replaced the stored endpoint + `baseUrl` derivation with a
  hardcoded `val baseUrl = "https://api.heirlooms.digital"`.
- `SettingsActivity.kt` — removed endpoint `EditText` and URL validation; screen now
  shows API key field only.
- `activity_settings.xml` — removed endpoint label, input, and help text views;
  API key section anchored directly below the title.
- `strings.xml` — removed `settings_endpoint_label`, `settings_endpoint_hint`,
  `settings_help`, `settings_invalid_url`; updated `settings_saved` to "Settings saved".
- `EndpointStoreTest.kt` — replaced endpoint tests with equivalent API key tests
  (5 tests, all passing).

---

## Session wrap-up — 2026-05-06 (v0.10.0)

**Validated end-to-end:** Upload from Android confirmed working via
`https://api.heirlooms.digital`. Swagger UI confirmed at
`https://api.heirlooms.digital/docs/index.html`. All 6 endpoints visible.

**Cloud Run:** server deployed as revision `heirlooms-server-00002-stq`
(us-central1). Tagged as **v0.10.0**.

---

## Session — 2026-05-06 (Phase 1 thumbnail generation)

**Prompt:** Add synchronous image thumbnail generation at upload time —
Phase 1 of a three-phase pipeline (Phase 2: video first-frame via FFmpeg,
Phase 3: async generation).

**What was built:**

### Database
- `V3__add_thumbnail_key.sql` — adds nullable `thumbnail_key VARCHAR(512)`
  column to the uploads table. Nullable because existing uploads have none,
  non-image files never get one, and generation can fail silently.

### ThumbnailGenerator
- `ThumbnailGenerator.kt` — top-level `generateThumbnail(bytes, mimeType)`
  function using only `javax.imageio.ImageIO` and `java.awt` (no extra
  dependencies). Scales to fit a 400×400 bounding box preserving aspect
  ratio, outputs JPEG. Returns null for unsupported types (everything except
  image/jpeg, image/png, image/gif, image/webp) or if ImageIO can't decode
  the input. Try/catch ensures thumbnail failure never propagates to the
  upload response.

### FileStore — saveWithKey
- `FileStore.saveWithKey(bytes, key, mimeType)` added to the interface.
  Implemented in `LocalFileStore`, `S3FileStore`, and `GcsFileStore`.
  Used to store thumbnails under an explicit key (`{uuid}-thumb.jpg`)
  alongside the original file.

### Database — thumbnailKey
- `UploadRecord` gains `thumbnailKey: String? = null` (trailing default,
  backward compatible).
- All INSERT and SELECT queries updated to include `thumbnail_key`.

### Upload flow
- `buildApp` gains an injectable `thumbnailGenerator` parameter (default
  `::generateThumbnail`) so tests can inject a stub or failing lambda.
- `POST /upload` (direct path): after storing the original, calls
  `tryStoreThumbnail` to generate + store the thumbnail, then records
  `thumbnailKey` in the database. On any failure, proceeds without thumbnail.
- `POST /uploads/confirm` (signed URL path): calls
  `tryFetchAndStoreThumbnail`, which first checks if the MIME type is
  supported (skip early for videos, avoiding a wasteful GCS fetch) then
  fetches bytes, generates, and stores the thumbnail. On any failure,
  proceeds without thumbnail.
- Thumbnail stored under `{original-uuid}-thumb.jpg` in the same bucket.

### API changes
- `GET /api/content/uploads` — list JSON now includes `"thumbnailKey":null`
  or `"thumbnailKey":"uuid-thumb.jpg"` on each item.
- `GET /api/content/uploads/{id}/thumb` — new contract route. Returns the
  JPEG thumbnail if `thumbnailKey` is set; falls back to the full file if
  not. Returns 404 if the upload record doesn't exist.

### HeirloomsWeb
- `UploadCard` uses `GET /uploads/{id}/thumb` when `upload.thumbnailKey` is
  non-null (fetching the smaller thumbnail for the grid), falling back to
  `GET /uploads/{id}/file` for uploads without a thumbnail.

### Tests
- `ThumbnailGeneratorTest.kt` (8 tests): supported JPEG returns non-null,
  output is valid JPEG, unsupported type returns null, invalid bytes returns
  null, fits within 400×400, preserves aspect ratio, no upscaling for small
  images, octet-stream returns null.
- `UploadHandlerTest.kt` (11 new tests): thumbnail generated and stored for
  supported type, no thumbnail for video/mp4, upload succeeds when generator
  throws, thumbnailKey null in list for non-image, thumbnailKey present in
  list, thumb endpoint returns thumbnail bytes, thumb endpoint falls back to
  full file, thumb endpoint returns 404.

All tests passing (95 total across 6 test classes, 0 failures).

**Cloud Run:** server deployed as revision `heirlooms-server-00007-gvl`,
web as revision `heirlooms-web-00002-jjr` (us-central1). Tagged as **v0.12.0**.

---

## Session — 2026-05-06 (POST /upload JSON response)

**Fix:** `POST /upload` 201 response was returning a raw storage key string
(`780aa0d2-fd28-4ad0-8c6d-e3aec4d30fa3.jpg`). Changed to return a full JSON
object matching the shape of items in the `GET /uploads` list:
`{"id":"...","storageKey":"...","mimeType":"...","fileSize":...,"uploadedAt":"...","thumbnailKey":...}`.

`uploadedAt` is captured at the point of the `save()` call in Kotlin (very
close to the DB `DEFAULT NOW()` value — the column is not included in the
INSERT so the DB sets it independently). `Content-Type: application/json`
header added to the 201 response. All 95 tests still passing.

**Cloud Run:** server deployed as revision `heirlooms-server-00008-kdz`.

---

## Session — 2026-05-06 (Phase 2 thumbnails — video first-frame via FFmpeg)

**Prompt:** Extend the thumbnail pipeline to support video files. Add FFmpeg to the Docker image, extend `ThumbnailGenerator` to extract the first frame from video/mp4, video/quicktime, video/x-msvideo, and video/webm using FFmpeg via `ProcessBuilder`, and add tests.

**What was built:**

- **`Dockerfile`** — `apt-get install -y ffmpeg` added before `USER heirloom` (runs as root).

- **`ThumbnailGenerator.kt`** — dispatches to `extractVideoThumbnail` for video MIME types. Writes video bytes to a temp file, runs `ffmpeg -vframes 1 -f image2 output.jpg` via `ProcessBuilder` with a 30-second timeout, reads the output JPEG, scales via the shared `scaleAndEncode` helper. All failures return null gracefully and temp files are always cleaned up in `finally`.

- **`THUMBNAIL_SUPPORTED_MIME_TYPES`** now includes the four video types, so the confirm-flow's `tryFetchAndStoreThumbnail` no longer skips them.

- **Tests (ThumbnailGeneratorTest):** 2 new — `valid MP4 produces non-null thumbnail` (uses `assumeTrue(isFFmpegAvailable())` to skip gracefully when FFmpeg is absent) and `corrupt video returns null gracefully` (always runs).

- **Test adjustment:** `returns null for unsupported MIME type` updated from `video/mp4` (now supported) to `audio/mpeg`. `no thumbnail generated for unsupported MIME type` in `UploadHandlerTest` renamed to `no thumbnail stored when video bytes are invalid`.

**Result:** 97 tests, 0 failures, 1 skipped locally (valid-MP4 test runs in Docker where FFmpeg is installed).

**Deployed:** Cloud Run revision `heirlooms-server-00009-gdv`. Health check confirmed `ok`. Tagged as **v0.13.0**.

---

## Session — 2026-05-06 (Web gallery — video thumbnails)

**Prompt:** Update HeirloomsWeb to use the Phase 2 video thumbnails.

**What was built:**

`UploadCard` previously ignored `thumbnailKey` for video files, always showing a generic
video icon. Now:

- Videos with a `thumbnailKey` pre-fetch the JPEG thumbnail (via the same `/thumb` endpoint
  used for images) and display it in the card with a semi-transparent play-button overlay.
  Clicking the card still opens the video via the signed read URL.
- Videos without a `thumbnailKey` keep the existing `VideoIcon` + "Click to play" behaviour.
  While a thumbnail is loading, a spinner is shown.
- Added `PlayIcon` component (circular button, 48×48, play arrow).

**Files changed:**
- `HeirloomsWeb/src/App.jsx`

**Deployed:** Cloud Run revision `heirlooms-web-00003-4nx`.

---

## Session — 2026-05-06 (EXIF and video metadata extraction)

**Prompt:** Add EXIF and video metadata extraction to HeirloomsServer. Metadata extracted at upload time alongside thumbnail generation and stored in six new nullable database columns (captured_at, latitude, longitude, altitude, device_make, device_model). GPS pin icon in HeirloomsWeb for cards with coordinates.

**What was built:**

### HeirloomsServer
- **`V4__add_metadata_columns.sql`** — adds six nullable columns to the uploads table.
- **`MetadataExtractor.kt`** (new) — `MediaMetadata` data class; `MetadataExtractor` class with `extract(bytes, mimeType): MediaMetadata`. Image path uses `com.drewnoakes:metadata-extractor:2.19.0` for EXIF GPS (lat/lon/alt), capture timestamp, and device make/model. Video path runs `ffprobe -v quiet -print_format json` and parses `format.tags.creation_time`, ISO 6709 location string, and Apple QuickTime make/model tags. All failures return `MediaMetadata()` with all nulls.
- **`UploadRecord`** — six new nullable fields.
- **`Database.kt`** — all INSERT and SELECT queries updated. `ResultSet.toUploadRecord()` private extension eliminates duplicated mapping code.
- **`UploadHandler.kt`** — `buildApp` gains `metadataExtractor` parameter (default `MetadataExtractor()::extract`). Direct upload path calls metadata extraction on the request bytes. Confirm path refactored to call `fetchBytesIfNeeded` once, passing bytes to both `tryStoreThumbnail` and `metadataExtractor` (single GCS fetch instead of two). `UploadRecord.toJson()` private extension: metadata fields omitted when null; used in both upload and list handlers.

### HeirloomsWeb
- **`App.jsx`** — `PinIcon` component (📍 with lat/lon tooltip). `UploadCard` outer div gains `relative` class; pin shown when both `latitude` and `longitude` are non-null.

### Tests
- `MetadataExtractorTest` (4 tests): GPS JPEG with hand-crafted TIFF/EXIF bytes returns correct lat/lon/alt; plain JPEG returns null coords; unsupported MIME type returns all nulls; invalid bytes return null.
- `UploadHandlerTest`: 1 new test (metadata exception does not fail upload); three confirm-flow tests updated to stub `storage.get()`.
- **102 tests total, 101 passing, 1 skipped** (FFmpeg video thumbnail test — runs in Docker).

**Key gotchas:**
- Adding `metadataExtractor` as the last `buildApp` parameter broke existing tests that used trailing lambda syntax for `thumbnailGenerator`. Fixed by using named parameter syntax throughout.
- Confirm path previously fetched bytes inside `tryFetchAndStoreThumbnail`. Refactored to fetch once and share with metadata extraction.

---

## Session — 2026-05-06 (Metadata extraction debugging and stabilisation)

**Context:** Follow-on to the metadata extraction session. End-to-end testing with a real Samsung Galaxy A02s revealed several issues that were diagnosed and fixed iteratively.

**Issues found and fixed:**

### capturedAt missing on Samsung
Samsung writes `DateTime` to `ExifIFD0Directory` rather than `DateTimeOriginal` in `ExifSubIFDDirectory`. Added two fallbacks in `extractCapturedAt()`: IFD0 DateTime, then SubIFD DateTimeDigitized. Deployed as `heirlooms-server-00011-gbq`.

### GPS returning (0, 0)
Samsung entry-level cameras write GPS IFD tags with zero values when the GPS fix hasn't been acquired at shutter time. The library parsed them as `GeoLocation(0.0, 0.0)` rather than null. Added a filter: if both lat and lon are exactly 0.0, treat as null. Deployed as `heirlooms-server-00012-6ll`.

### OutOfMemoryError on large image uploads
Cloud Run default 512Mi heap was exhausted when loading a 5.4 MB photo: GCS `readAllBytes()` (5.4 MB) + `BufferedImage` decode (4160×3120 = ~52 MB) + JVM overhead. Two fixes: (1) increased Cloud Run memory to 2Gi; (2) metadata extraction in the confirm path now calls `GcsFileStore.getFirst()` which streams only the first 64 KB from GCS via `Storage.reader()` ReadChannel — JPEG EXIF is always within that range. Thumbnails still fetch the full file. Deployed as `heirlooms-server-00014-97p`.

### No metadata at all (mimeType: "image/*")
Samsung Gallery provides `intent.type = "image/*"` (wildcard) in the share intent. The app was using this directly, so uploads were stored as `.bin` with MIME type `image/*`, which is not in the metadata or thumbnail supported sets. Fixed by skipping wildcards and falling back to `contentResolver.getType(uri)` for the real specific type. Installed via ADB.

### Silent upload failure (SecurityException not caught)
`MediaStore.setRequireOriginal()` requires `ACCESS_MEDIA_LOCATION`. When denied, `openInputStream()` on the original URI threw `SecurityException`, which propagated uncaught through `catch (e: IOException)` and silently killed the coroutine. Fixed: (1) `readBytes()` wraps the entire `setRequireOriginal` + `openInputStream` in a single try/catch and falls back to the plain URI; (2) catch block in `ShareActivity` changed from `IOException` to `Exception`.

**End state:** Photo shared from Samsung Galaxy A02s → full metadata response including `capturedAt`, `latitude`, `longitude`, `deviceMake`, `deviceModel`. Coordinates confirmed real (East Midlands, UK). GPS pin 📍 visible in web gallery.

**Android gotchas for future reference:**
- `ACCESS_MEDIA_LOCATION` must be declared in manifest AND requested at runtime AND `setRequireOriginal()` must be called — three separate requirements
- Samsung Galaxy shares with wildcard MIME types
- Notification channel importance is immutable after first creation — bump the channel ID to change it
- Samsung Camera "Location tags" toggle (in Camera Settings) is separate from the system Location permission

---

## Session — 2026-05-06 (Image rotation)

**Prompt:** Add the ability to rotate images 90° in the web gallery. Rotation persists and applies to both thumbnail and lightbox view.

**What was built:**

- **`V5__add_rotation.sql`** — `rotation INT NOT NULL DEFAULT 0` on uploads table
- **`Database.updateRotation(id, rotation)`** — UPDATE statement; `rotation` added to all SELECT queries and `UploadRecord`
- **`PATCH /api/content/uploads/{id}/rotation`** — new contract route accepting `{"rotation":0|90|180|270}`; returns 400 for invalid values, 404 if upload not found
- **`UploadRecord.toJson()`** — `rotation` always included (even 0)
- **HeirloomsWeb** — `RotateIcon` component; ↻ button in each image card's info row; `handleRotate` in Gallery with optimistic state update + fire-and-forget PATCH call; CSS `transform: rotate(Xdeg)` on thumbnail image with `overflow-hidden` container clipping; `Lightbox` accepts `rotation` prop and swaps `max-w`/`max-h` at 90°/270° so portrait-rotated images fill the viewport; `lightboxUrl` state replaced with `lightbox: {url, rotation}` object
- **5 new tests** in `UploadHandlerTest`: valid rotation returns 200 + verifies DB call, invalid rotation returns 400, upload not found returns 404, rotation field in list response, rotation defaults to 0

**107 tests total, 106 passing, 1 skipped** (FFmpeg video thumbnail — passes in Docker).

---

## Session — 2026-05-06 (Tags — Increment 1: schema + write API)

**Prompt:** Add tag support to HeirloomsServer. New Flyway V6 migration adds a `tags TEXT[] NOT NULL DEFAULT '{}'` column to the uploads table with a GIN index. New `PATCH /api/content/uploads/{id}/tags` endpoint accepts `{"tags":["family","2026-summer"]}` with full-replace semantics, validates each tag against `^[a-z0-9]+(-[a-z0-9]+)*$` with length 1–50, and returns 400 naming the offending tag on failure or 404 if the upload doesn't exist. Tags appear in all upload JSON responses (`POST /upload`, `GET /uploads`, `GET /uploads/{id}`) as a `tags` array, always present, empty when none. Mirror the existing rotation endpoint's structure (added in v0.15.0).

**What was built:**

- **`V6__add_tags.sql`** — `tags TEXT[] NOT NULL DEFAULT '{}'` on uploads table plus `CREATE INDEX idx_uploads_tags ON uploads USING GIN (tags)`
- **`TagValidator.kt`** — `validateTags(tags)` enforces kebab-case (`^[a-z0-9]+(-[a-z0-9]+)*$`), length 1–50, with specific rejection reasons per tag; sealed `TagValidationResult` (Valid / Invalid(tag, reason))
- **`Database.updateTags(id, tags): Boolean`** — UPDATE via JDBC `createArrayOf("text", ...)`, returns false if no row matched
- **`tags` added to `UploadRecord`** — `List<String> = emptyList()`, all SELECT queries include the column, `toUploadRecord()` reads via `getArray("tags")`
- **`PATCH /api/content/uploads/{id}/tags`** — full-replace semantics; 400 on malformed JSON or invalid tag (offending tag + reason in response body); 404 if upload not found; 200 with full updated UploadRecord JSON on success
- **`UploadRecord.toJson()`** — `tags` always included, empty array when none
- **14 new tests** in `TagValidatorTest` (unit), **8 new tests** in `UploadHandlerTest` (integration)

**129 tests total, 128 passing, 1 skipped** (FFmpeg video thumbnail — passes in Docker).

**Notes for future increments:**
- Increment 2 (read API + filtering) will use the GIN index for `tag` and `exclude_tag` query params on `GET /uploads`
- Increment 3 (web UI) will surface tags as chips and an inline editor
- Tag rename, merge, colours, and Android tagging are all out of scope and remain parked in IDEAS.md

**v0.16.0 not yet tagged** — releasing once all three increments land.

---

## Session — 2026-05-06 (Tags — Increment 2: read API + filtering)

**Prompt:** Add `tag` and `exclude_tag` query parameters to `GET /uploads` so the list can be filtered by tag using the GIN index added in Increment 1.

**What was built:**

- **`Database.listUploads(tag, excludeTag)`** — optional parameters; builds a dynamic WHERE clause using `tags @> ARRAY[?]::text[]` (GIN-indexed) and `NOT (tags @> ARRAY[?]::text[])` for inclusion/exclusion; no WHERE clause when both are null (unchanged behaviour)
- **`GET /uploads?tag=family`** — returns only uploads that have this tag
- **`GET /uploads?exclude_tag=trash`** — omits uploads that have this tag
- Both params can be combined in a single request
- Updated `listUploadsContractRoute` description to document the new params
- 5 new tests in `UploadHandlerTest` covering: tag filter, exclude_tag filter, both combined, unknown tag returns empty array, no params passes nulls

**134 tests total, 133 passing, 1 skipped** (FFmpeg).

**Cloud Run:** deployed as revision `heirlooms-server-00018-w2g`.

**v0.16.0 not yet tagged** — releasing once Increment 3 (web UI) also lands.

---

## Session — 2026-05-06 (Tags — Increment 3: web UI)

**Prompt:** Surface tags in the web gallery as chips on each card, with an inline editor backed by an autocomplete dropdown of previously used tags.

**What was built:**

- **`TagEditor` component** — removable chips per selected tag (× to remove, Backspace removes last), text input with autocomplete dropdown filtered from `allTags`, Enter or Save commits; pending input text is flushed into the tag list on Save so typing a tag and clicking Save directly works without pressing Enter first
- **`allTags`** — derived in `Gallery` via `useMemo` over all uploads, sorted, passed down to each `UploadCard`; automatically includes newly saved tags after a successful PATCH
- **Tag chips** — shown in display mode below card metadata; hidden when no tags
- **`TagIcon` SVG** — added to card header row next to rotate button; highlighted when editor is open
- **`overflow-hidden` fix** — moved from outer card div to image container (`rounded-t-xl overflow-hidden`) so the dropdown is not clipped by the card boundary
- **OpenAPI body spec** — `RotationRequest` and `TagsRequest` data classes made non-private (were `private`, causing `IllegalAccessException` in Jackson schema generator and 500s on `/docs/api.json`); `receiving(lens to example)` added to both PATCH endpoints; spec endpoint test added as a permanent regression guard
- **CORS/Swagger fix for example bodies** — examples surface in Swagger UI for both PATCH endpoints
- **Tag filtering** (Increment 2 addition) — `GET /uploads?tag=X` and `GET /uploads?exclude_tag=X` use `tags @> ARRAY[?]::text[]` against the GIN index; `.env` updated to `https://api.heirlooms.digital`

**Cloud Run:** latest revision `heirlooms-server-00021-fqb`.

**v0.16.0 not yet tagged** — releasing once all three increments are confirmed working end-to-end.

---

## Session — 2026-05-07 (v0.16.1 — video upload OOM fix + tag dropdown UX)

**Android: video upload OOM fix**

Root cause identified via ADB logcat: `UploadWorker` called `file.readBytes()` before `uploadViaSigned`, loading the entire video into the Java heap (201 MB growth limit on Samsung Galaxy A02s). When OkHttp then tried to allocate an 8 KB Okio buffer segment to begin the GCS PUT, the heap was exhausted — `OutOfMemoryError: Failed to allocate a 8208 byte allocation with 55224 free bytes`. Because `OutOfMemoryError` is a `java.lang.Error` (not `Exception`), the `catch (_: Exception)` in `UploadWorker` did not catch it: the coroutine crashed silently, the GCS PUT never completed, no DB record was created, and no result notification appeared.

Fix: new `Uploader.uploadViaSigned(File, ...)` overload that streams the video from disk to GCS directly using `file.asRequestBody()` / `ProgressFileRequestBody`, never loading the full content into memory. SHA-256 computed by reading the file in 8 KB chunks. `UploadWorker` now passes the `File` object directly, removing `readBytes()` entirely. Confirmed working: 1-minute Samsung video (~90 MB) uploaded in ~8 min 46 s on slow home WiFi.

**Web: tag dropdown stays open after selection**

The tag editor dropdown was closing after each selection, requiring the user to click away and back to reopen it. Three approaches were tried before finding a reliable fix:

1. Removed `setDropdownOpen(false)` from `addTag` — failed because `onBlur` was still firing and closing the dropdown.
2. Used `e.relatedTarget` in `onBlur` to detect intra-dropdown clicks — failed because Safari on macOS does not focus `<button>` elements on click, so `e.relatedTarget` was null.
3. `suppressBlurRef`: the dropdown container's `onMouseDown` sets a ref flag before `onBlur` fires on the input; `onBlur` skips the close while the flag is set; `onMouseUp` clears it. This is browser-agnostic. ✓

**Cloud Run:** `heirlooms-web-00008-9qv` (web), server unchanged at `heirlooms-server-00021-fqb`.

**v0.16.1 not yet tagged** — Bret tags after push.
