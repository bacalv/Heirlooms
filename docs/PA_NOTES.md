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
- Current version: v0.14.0 (6 May 2026) — metadata extraction + GPS + Android fixes
- One-time machine setup required: ~/.testcontainers.properties with
  docker.raw.sock path — see PROMPT_LOG.md for details

---

## Pending decisions / next actions

- Domain mapping: live — heirlooms.digital and api.heirlooms.digital confirmed
  working (end-to-end upload validated from Android app, 6 May 2026)
- Swagger UI confirmed at https://api.heirlooms.digital/docs/index.html
- heirlooms.com: Currently parked on venture.com. Worth monitoring
- License: Deliberately deferred
- Tags: Not yet in schema or UI — planned for Milestone 4 completion

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
| HeirloomsServer Cloud Run URL | https://heirlooms-server-340655233963.us-central1.run.app (revision heirlooms-server-00014-97p, 2Gi) |
| HeirloomsWeb Cloud Run URL | https://heirlooms-web-340655233963.us-central1.run.app (revision heirlooms-web-00004-vxm) |
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