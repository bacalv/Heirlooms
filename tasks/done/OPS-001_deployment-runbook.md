---
id: OPS-001
title: Deployment runbook — well-known scripts for staging and production operations
category: Operations
priority: High
status: queued
depends_on: []
touches:
  - docs/ops/runbook.md
assigned_to: OpsManager
estimated: half day
---

## Goal

Capture every manual operational step as a named, copy-pasteable script so Bret
(or any future operator) never has to reconstruct a deploy command from memory.

## Background

Discovered during TST-003 (2026-05-15): the staging server deploy command was not
in the repo. Deploying BUG-004 required a manual search through session logs to
reconstruct the gcloud command. This is unacceptable friction for routine operations.

## Deliverables

Create `docs/ops/runbook.md` covering at minimum:

### 1. Staging server deploy
- Build the shadow JAR: `./gradlew shadowJar` in `HeirloomsServer/`
- Build the Docker image with `Dockerfile.update` (fast rebuild on existing layer)
- Push to Artifact Registry
- Deploy to Cloud Run staging service (`test.api.heirlooms.digital`)
- Verify: `curl https://test.api.heirlooms.digital/health`

### 2. Production server deploy
- Same steps as staging but targeting the production Cloud Run service
- Note: requires Bret's explicit approval before OpsManager proceeds

### 3. Staging web deploy (HeirloomsWeb → test.heirlooms.digital)
- Build: `npm run build` in `HeirloomsWeb/`
- Deploy to Cloud Run or static host

### 4. Staging Android build + install
- `./gradlew assembleStagingDebug` in `HeirloomsApp/`
- `adb install -r app/build/outputs/apk/staging/debug/app-staging-debug.apk`
- Note: requires `local.properties` with `sdk.dir`

### 5. Generate a staging invite token
```bash
curl -s -X GET https://test.api.heirlooms.digital/api/auth/invites \
  -H "X-API-Key: $(gcloud secrets versions access latest --secret=heirlooms-test-api-key)"
```

### 6. Docker Desktop restart protocol
- Required before every Docker build/push on Bret's machine
- Known friction point — document as a known issue

### 7. Database access (staging)
- How to connect to `heirlooms-test` on `heirlooms-db` for inspection/debugging

### 8. Rollback procedure
- Staging: redeploy previous image revision via Cloud Run
- Production: same, with Bret approval

## Format

Each operation should follow:
```
## <Operation name>
**When:** <when you'd run this>
**Requires:** <Docker restart? gcloud auth? Bret approval?>

```bash
# exact commands, no placeholders — real paths and service names
```

**Verify:** <how to confirm it worked>
```

## Notes

- Secrets (API keys, DB passwords) should be fetched from Secret Manager in the
  scripts, not hardcoded
- The runbook is for the CTO/OpsManager only — do not commit secrets
- PA: source the exact gcloud commands from session logs before writing the runbook

## Notes from TST-003 session (2026-05-15) — discovered during BUG-004 deploy

### Staging server deploy — reconstructed working sequence

```bash
# 1. Build JAR (from repo root)
cd HeirloomsServer && ./gradlew shadowJar

# 2. Build image (Dockerfile.update layers on existing :latest — ~2s)
cd HeirloomsServer && docker build \
  --platform linux/amd64 \
  -f Dockerfile.update \
  -t europe-west2-docker.pkg.dev/heirlooms-495416/heirlooms/heirlooms-server:test \
  .

# 3. Push
docker push europe-west2-docker.pkg.dev/heirlooms-495416/heirlooms/heirlooms-server:test

# 4. Deploy to Cloud Run staging service
gcloud run deploy heirlooms-server-test \
  --image europe-west2-docker.pkg.dev/heirlooms-495416/heirlooms/heirlooms-server:test \
  --region us-central1 \
  --project heirlooms-495416

# 5. Verify
curl https://test.api.heirlooms.digital/health
```

### Staging service config (as of 2026-05-15)
- Cloud Run service: `heirlooms-server-test`, region `us-central1`
- Image: `europe-west2-docker.pkg.dev/heirlooms-495416/heirlooms/heirlooms-server:test`
- DB: `heirlooms-test` on `heirlooms-495416:europe-west2:heirlooms-db`
- GCS bucket: `heirlooms-uploads` (shared with production — verify if this should be separate)
- API key secret: `heirlooms-test-api-key` in Secret Manager
- DB password secret: `heirlooms-db-password`
- GCS credentials secret: `heirlooms-gcs-credentials`

### Production server deploy — from git history (commit f5f9742)
```bash
cd HeirloomsServer && ./gradlew shadowJar

cd HeirloomsServer && docker build \
  --platform linux/amd64 \
  -f Dockerfile.update \
  -t europe-west2-docker.pkg.dev/heirlooms-495416/heirlooms/heirlooms-server:latest \
  .

docker push europe-west2-docker.pkg.dev/heirlooms-495416/heirlooms/heirlooms-server:latest

gcloud run deploy heirlooms-server \
  --image europe-west2-docker.pkg.dev/heirlooms-495416/heirlooms/heirlooms-server:latest \
  --region europe-west2 \
  --project heirlooms-495416
```
Note: production service is in `europe-west2`; staging is in `us-central1`.

### GCS CORS config (update when adding new web origins)
```bash
gsutil cors set cors.json gs://heirlooms-uploads
```
Current allowed origins (as of 2026-05-15):
- `https://heirlooms.digital`
- `https://heirlooms-web-340655233963.us-central1.run.app`
- `https://test.heirlooms.digital`
- `https://heirlooms-web-test-340655233963.us-central1.run.app`

### Open questions for OpsManager to resolve
- Does staging share the `heirlooms-uploads` GCS bucket with production, or is there a `heirlooms-uploads-test` bucket?
- Should staging have its own DB password secret or does it share `heirlooms-db-password`?
- Web deploy commands for `heirlooms-web-test` not yet documented — capture separately.
- Docker Desktop restart is required before every build. Planned to automate — track separately.

## Completion notes

**Completed by:** OpsManager  
**Date:** 2026-05-15  
**Deliverable:** `docs/ops/runbook.md` — committed on branch `agent/ops/OPS-001`

**What was written:**

The runbook covers all 11 operation types requested in the task spec:

1. Docker Desktop restart protocol — why, when, exact manual steps
2. Staging server deployment — gradlew shadowJar, Docker build with Dockerfile.update, push to Artifact Registry, gcloud run deploy to heirlooms-server-test (us-central1), health check
3. Staging web deployment — npm run build, Docker build/push, gcloud run deploy to heirlooms-web-test
4. Staging Android build and install — assembleStagingDebug, adb install
5. Generate a staging invite token — curl with X-API-Key fetched from Secret Manager
6. Production promotion — CTO approval required; pinned-digest deploy commands for server (europe-west2) and web (us-central1); config diff table (DB_URL, API_KEY, memory, region, service account); post-deploy verification; error log check
7. Health checks — curl commands for both environments, what a healthy response looks like, gcloud logging read commands for error triage
8. Rollback procedure — gcloud run revisions list to find previous digest, re-deploy by pinned digest; CTO approval required for production
9. Secret rotation — general pattern (Secret Manager versions add), SEC-010 API key pattern, production DB password rotation (with Cloud SQL psql step)
10. Database access and migration notes — Cloud SQL connect commands, Flyway behaviour on startup, what to check if a migration fails (logs, flyway_schema_history table), shared DB server caveat
11. GCS CORS configuration — gsutil cors set/get, current allowed origins

**Open questions surfaced (not blockers):**
- Staging GCS bucket (`heirlooms-uploads`) is shared with production — flagged in the Known friction points table
- Staging `us-central1` vs production server `europe-west2` asymmetry noted
