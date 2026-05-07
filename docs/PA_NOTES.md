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
- Current version: v0.18.1 (8 May 2026) — brand foundation (v0.17.0), share-sheet idle
  state (v0.17.1), capsule backend (v0.18.0), doc sweep + route fix (v0.18.1).
  Web UI for capsules (Milestone 5 Increment 2) and Android daily-use gallery are next.
- One-time machine setup required: ~/.testcontainers.properties with
  docker.raw.sock path — see PROMPT_LOG.md for details

---

## Pending decisions / next actions

- Domain mapping: live — heirlooms.digital and api.heirlooms.digital confirmed
  working (end-to-end upload validated from Android app, 6 May 2026)
- Swagger UI confirmed at https://api.heirlooms.digital/docs/index.html
- heirlooms.com: Currently parked on venture.com. Worth monitoring
- License: Deliberately deferred


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
- **Coil 2.5.0 added as an Android dependency (v0.17.1).** The share-sheet idle screen
  renders a grid of selected photo thumbnails. Coil handles bitmap loading and caching.
  Pinned at 2.5.0 — newer Coil versions occasionally break API surface in minor ways, so
  upgrade deliberately rather than reflexively.
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
| HeirloomsServer Cloud Run URL | https://heirlooms-server-340655233963.us-central1.run.app (revision heirlooms-server-00021-fqb, 2Gi) |
| HeirloomsWeb Cloud Run URL | https://heirlooms-web-340655233963.us-central1.run.app (revision heirlooms-web-00008-9qv) |
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
cd ~/Downloads/Heirlooms/HeirloomsServer
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
cd ~/Downloads/Heirlooms/HeirloomsWeb
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