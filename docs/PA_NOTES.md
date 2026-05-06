# PA Notes — Working Memory

This file is maintained by the PA (claude.ai) as a working memory across sessions.
It captures things that are easy to forget but important to remember — preferences,
patterns, pending decisions, and context that doesn't fit neatly into PROMPT_LOG.md.

---

## Bret's preferences and working style

- Prefers IntelliJ IDEA Community Edition over Android Studio for this project
- Uses IntelliJ's built-in Git UI for commits; does all pushes manually as a
  deliberate human checkpoint before code enters the remote repo
- Dislikes the macOS Finder file picker — use `Cmd+Shift+G` to navigate by path
- Prefers the Claude Code plugin for hands-on fixes; uses claude.ai for architecture
  and product thinking
- Tends to work in focused sessions and likes a clean summary at the end of each one
- Appreciates being told when something is a one-time setup vs ongoing requirement

---

## Things to always remember

- **Package name:** `digital.heirlooms` (not `com.heirloom` — that was the old name)
- **Domain:** `heirlooms.digital` (registered 30 April 2026)
- **GitHub:** `github.com/bacalv/Heirlooms` (capital H)
- **Current version:** v0.3.0 (tagged on main)
- **One-time machine setup required:** `~/.testcontainers.properties` with
  `docker.raw.sock` path — see PROMPT_LOG.md for details

---

## Pending decisions / next actions

- **Milestone 3:** Self-hosted deployment — a `docker-compose.yml` for running the
  full stack on a cheap VPS so the Android app has a real endpoint to point at
- **heirlooms.com:** Currently parked on venture.com. Worth monitoring — only worth
  acquiring if the project grows to consumer scale
- **License:** Deliberately deferred. Revisit when deciding whether Heirlooms will
  be open source, source-available, or strictly proprietary

---

## Things that tripped us up (don't repeat)

- The `~/.testcontainers.properties` file must use `docker.raw.sock`, not
  `docker.sock` or `docker-cli.sock` — the others return stub 400 responses on
  macOS Docker Desktop
- Zip files must be built fresh each time — `zip -r` with `--exclude` only works
  on a clean build; updating an existing zip preserves entries that were already in it
- `.idea/` must always be excluded from zips and commits — IntelliJ generates it
  fresh on first open and stale paths cause import failures
- `local.properties` in HeirloomsApp must point to the Android SDK:
  `sdk.dir=/Users/bac/Library/Android/sdk`

---

## Team reminders

- The Software Engineer (Claude Code) creates commits but **Bret always pushes**
- Ask the Software Engineer to update PROMPT_LOG.md after significant code changes
- At the start of a new claude.ai session, paste both PROMPT_LOG.md and TEAM.md
- PA_NOTES.md (this file) should also be pasted in if working memory is needed

---

## GCP Infrastructure (Milestone 3)

| Resource | Value |
|---|---|
| Project ID | `heirlooms-495416` |
| Region | `europe-west2` |
| Cloud SQL instance | `heirlooms-db` |
| Database name | `heirlooms` |
| Database user | `heirlooms` |
| Cloud Storage bucket | `heirlooms-uploads` |
| Service account | `heirlooms-server` |
| Artifact Registry | `heirlooms` |
| Full image path | `europe-west2-docker.pkg.dev/heirlooms-495416/heirlooms/heirlooms-server` |

**Credentials:** Service account JSON key downloaded locally. DB password stored
separately. Neither should ever be committed to GitHub.

---

## GCP permissions — what actually worked (learned the hard way)

All permissions must be granted via CLI, not the Console — the Console UI did not
reliably apply them. Use these commands:

```bash
# Cloud SQL access
gcloud projects add-iam-policy-binding heirlooms-495416 \
  --member="serviceAccount:heirlooms-server@heirlooms-495416.iam.gserviceaccount.com" \
  --role="roles/cloudsql.client"

# Secret Manager access
gcloud projects add-iam-policy-binding heirlooms-495416 \
  --member="serviceAccount:heirlooms-server@heirlooms-495416.iam.gserviceaccount.com" \
  --role="roles/secretmanager.secretAccessor"

# GCS bucket access
gcloud storage buckets add-iam-policy-binding gs://heirlooms-uploads \
  --member="serviceAccount:heirlooms-server@heirlooms-495416.iam.gserviceaccount.com" \
  --role="roles/storage.objectAdmin"
```

## Cloud Run deploy command (current, working)

```bash
gcloud run deploy heirlooms-server \
  --image europe-west2-docker.pkg.dev/heirlooms-495416/heirlooms/heirlooms-server:latest \
  --region europe-west2 \
  --platform managed \
  --allow-unauthenticated \
  --set-env-vars "STORAGE_BACKEND=GCS" \
  --set-env-vars "GCS_BUCKET=heirlooms-uploads" \
  --set-env-vars "DB_URL=jdbc:postgresql:///heirlooms?cloudSqlInstance=heirlooms-495416:europe-west2:heirlooms-db&socketFactory=com.google.cloud.sql.postgres.SocketFactory" \
  --set-env-vars "DB_USER=heirlooms" \
  --set-secrets "DB_PASSWORD=heirlooms-db-password:latest" \
  --set-secrets "GCS_CREDENTIALS_JSON=heirlooms-gcs-credentials:latest" \
  --set-secrets "API_KEY=heirlooms-api-key:latest" \
  --service-account heirlooms-server@heirlooms-495416.iam.gserviceaccount.com \
  --add-cloudsql-instances heirlooms-495416:europe-west2:heirlooms-db
```

## Docker build and push command (for future deployments)

```bash
cd ~/Downloads/Heirlooms/HeirloomsServer
docker build \
  --platform linux/amd64 \
  -t europe-west2-docker.pkg.dev/heirlooms-495416/heirlooms/heirlooms-server:latest \
  .
docker push europe-west2-docker.pkg.dev/heirlooms-495416/heirlooms/heirlooms-server:latest
```

## Current version
v0.8.0 (6 May 2026)

---

## Key documents in the repo

| File | Purpose |
|---|---|
| `PROMPT_LOG.md` | Full history of decisions and what was built |
| `TEAM.md` | Team structure and working practices |
| `PA_NOTES.md` | This file — PA working memory and preferences |
| `ROADMAP.md` | Milestone plan and product vision |
| `IDEAS.md` | Product brainstorms not yet ready for the roadmap |

**At the start of a new claude.ai session, paste:**
`PROMPT_LOG.md`, `TEAM.md`, `PA_NOTES.md` — and `IDEAS.md` if discussing product direction.
