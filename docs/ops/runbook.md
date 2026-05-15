# Heirlooms Operations Runbook

**Maintained by:** OpsManager  
**Last updated:** 2026-05-15  
**Audience:** Bret (CTO) and OpsManager only — contains full deploy commands with real service names

---

## Quick reference

| | Production | Staging |
|---|---|---|
| API | https://api.heirlooms.digital | https://test.api.heirlooms.digital |
| Web | https://heirlooms.digital | https://test.heirlooms.digital |
| Cloud Run service (server) | `heirlooms-server` | `heirlooms-server-test` |
| Cloud Run service (web) | `heirlooms-web` | `heirlooms-web-test` |
| Cloud Run region (server) | `europe-west2` | `us-central1` |
| Cloud Run region (web) | `us-central1` | `us-central1` |
| DB | `heirlooms` on `heirlooms-db` | `heirlooms-test` on `heirlooms-db` |
| GCP project | `heirlooms-495416` | `heirlooms-495416` |
| Artifact Registry | `europe-west2-docker.pkg.dev/heirlooms-495416/heirlooms/` | same registry |
| Server image tag | `heirlooms-server:latest` | `heirlooms-server:test` |
| Web image tag | `heirlooms-web:latest` | `heirlooms-web-test:latest` |

---

## Contents

1. [Docker Desktop restart protocol](#1-docker-desktop-restart-protocol)
2. [Staging server deployment](#2-staging-server-deployment)
3. [Staging web deployment](#3-staging-web-deployment)
4. [Staging Android build and install](#4-staging-android-build-and-install)
5. [Generate a staging invite token](#5-generate-a-staging-invite-token)
6. [Production promotion](#6-production-promotion)
7. [Health checks](#7-health-checks)
8. [Rollback procedure](#8-rollback-procedure)
9. [Secret rotation](#9-secret-rotation)
10. [Database access and migration notes](#10-database-access-and-migration-notes)
11. [GCS CORS configuration](#11-gcs-cors-configuration)

---

## 1. Docker Desktop restart protocol

**When:** Before every Docker `build` or `push` command on Bret's machine. There is only one Docker client, and it requires a fresh restart before each build or deployment session.

**Why:** Without the restart the build daemon is in an inconsistent state and will fail silently or hang. This is a known friction point — automation is planned but not yet in place.

**Requires:** Physical access to Bret's machine (or Bret's manual action).

**Steps:**

1. Quit Docker Desktop: click the Docker whale icon in the menu bar → **Quit Docker Desktop**
2. Wait for the icon to disappear from the menu bar (approx 5–10 seconds)
3. Re-open Docker Desktop from `/Applications/Docker.app` (or Spotlight)
4. Wait until the Docker whale icon in the menu bar shows a steady green dot (not animating)
5. Confirm the daemon is ready:

```bash
docker info | grep "Server Version"
```

Expected output: a line like `Server Version: 27.x.x` (version number will vary).

**After this step:** proceed immediately to the build. Do not restart Docker again mid-session unless a subsequent build fails with a daemon error.

---

## 2. Staging server deployment

**When:** After merging a code change to the staging branch, or when deploying a hotfix to the test environment.

**Requires:** Docker Desktop restart (see §1). `gcloud` authenticated as a project editor. All commands run from the repo root unless otherwise noted.

### Step 1 — Build the shadow JAR

```bash
cd HeirloomsServer && ./gradlew shadowJar
```

**Verify:** Output ends with `BUILD SUCCESSFUL`. The JAR appears at:
```
HeirloomsServer/build/libs/HeirloomsServer-all.jar
```

### Step 2 — Build the Docker image

```bash
cd HeirloomsServer && docker build \
  --platform linux/amd64 \
  -f Dockerfile.update \
  -t europe-west2-docker.pkg.dev/heirlooms-495416/heirlooms/heirlooms-server:test \
  .
```

`Dockerfile.update` layers on top of the existing `:latest` image — typical build time is ~2 seconds.

**Verify:** Output ends with `Successfully tagged ...heirlooms-server:test`.

### Step 3 — Push to Artifact Registry

```bash
docker push europe-west2-docker.pkg.dev/heirlooms-495416/heirlooms/heirlooms-server:test
```

**Verify:** Each layer shows `Pushed` or `Layer already exists`. Output ends with the image digest (`sha256:...`). Record this digest.

### Step 4 — Deploy to Cloud Run staging

```bash
gcloud run deploy heirlooms-server-test \
  --image europe-west2-docker.pkg.dev/heirlooms-495416/heirlooms/heirlooms-server:test \
  --region us-central1 \
  --project heirlooms-495416
```

**Verify:** `gcloud` prints the service URL and `Service [heirlooms-server-test] revision [...] has been deployed`.

### Step 5 — Health check

```bash
curl -s https://test.api.heirlooms.digital/health
```

**Expected:** `ok`

If the health check returns anything other than `ok`, or returns a 5xx, check the logs immediately (see §7).

---

## 3. Staging web deployment

**When:** After merging a frontend change that should be visible on the staging web environment.

**Requires:** Docker Desktop restart (see §1). `gcloud` authenticated.

### Step 1 — Build the web bundle

```bash
cd HeirloomsWeb && npm run build
```

**Verify:** Output ends with `✓ built in ...` (Vite). The `dist/` directory is populated.

### Step 2 — Build and push the Docker image

```bash
cd HeirloomsWeb && docker build \
  --platform linux/amd64 \
  --build-arg VITE_API_URL=https://test.api.heirlooms.digital \
  -t europe-west2-docker.pkg.dev/heirlooms-495416/heirlooms/heirlooms-web-test:latest \
  .

docker push europe-west2-docker.pkg.dev/heirlooms-495416/heirlooms/heirlooms-web-test:latest
```

### Step 3 — Deploy to Cloud Run staging

```bash
gcloud run deploy heirlooms-web-test \
  --image europe-west2-docker.pkg.dev/heirlooms-495416/heirlooms/heirlooms-web-test:latest \
  --region us-central1 \
  --project heirlooms-495416
```

### Step 4 — Verify

```bash
curl -s -o /dev/null -w "%{http_code}" https://test.heirlooms.digital
```

**Expected:** `200`

---

## 4. Staging Android build and install

**When:** After merging Android changes that need device testing against the staging API.

**Requires:** Android SDK installed, `sdk.dir` set in `HeirloomsApp/local.properties`, device/emulator connected via ADB.

```bash
# Verify ADB sees the device
adb devices

# Build the staging debug APK
cd HeirloomsApp && ./gradlew assembleStagingDebug

# Install on device (replace previous install)
adb install -r app/build/outputs/apk/staging/debug/app-staging-debug.apk
```

**Verify:** `adb install` prints `Success`. The app appears on the device with a burnt-orange icon (staging flavour). The app ID is `digital.heirlooms.staging`.

---

## 5. Generate a staging invite token

**When:** Onboarding a tester to the staging environment.

**Requires:** Access to Secret Manager (project editor or secretAccessor role).

```bash
curl -s -X GET https://test.api.heirlooms.digital/api/auth/invites \
  -H "X-API-Key: $(gcloud secrets versions access latest \
        --secret=heirlooms-test-api-key \
        --project=heirlooms-495416)"
```

**Verify:** Response is a JSON object containing an invite token or invite URL.

---

## 6. Production promotion

**REQUIRES BRET'S EXPLICIT APPROVAL BEFORE ANY STEP IS EXECUTED.**

Production deployments are never triggered by OpsManager alone. The OpsManager prepares an itemised release plan (see `docs/ops/production-release-plan-v054.md` as a template), presents it to Bret, and waits for explicit GO instruction.

The standard flow:

1. OpsManager prepares release plan — environment health check, image inventory, migration delta, config diff, exact deploy commands with pinned digests, rollback commands
2. Bret reviews and gives GO/NO-GO
3. Bret (or OpsManager under Bret's supervision) executes

### Pinned-digest deploys

Production deploys always reference images by SHA-256 digest, not by tag. This prevents a tag from being mutated between the time the plan is written and the time it is executed.

Example pattern (replace digests with the actual values from the release plan):

```bash
gcloud run deploy heirlooms-server \
  --image europe-west2-docker.pkg.dev/heirlooms-495416/heirlooms/heirlooms-server@sha256:<DIGEST> \
  --region europe-west2 \
  --project heirlooms-495416 \
  --set-env-vars STORAGE_BACKEND=GCS,GCS_BUCKET=heirlooms-uploads,DB_USER=heirlooms,DB_URL=jdbc:postgresql:///heirlooms?cloudSqlInstance=heirlooms-495416:europe-west2:heirlooms-db\&socketFactory=com.google.cloud.sql.postgres.SocketFactory \
  --set-secrets DB_PASSWORD=heirlooms-db-password:latest,GCS_CREDENTIALS_JSON=heirlooms-gcs-credentials:latest \
  --add-cloudsql-instances heirlooms-495416:europe-west2:heirlooms-db \
  --service-account heirlooms-server@heirlooms-495416.iam.gserviceaccount.com \
  --memory 2Gi \
  --cpu 1 \
  --concurrency 80 \
  --timeout 300 \
  --max-instances 20 \
  --no-allow-unauthenticated
```

```bash
gcloud run deploy heirlooms-web \
  --image europe-west2-docker.pkg.dev/heirlooms-495416/heirlooms/heirlooms-web@sha256:<DIGEST> \
  --region us-central1 \
  --project heirlooms-495416 \
  --service-account 340655233963-compute@developer.gserviceaccount.com \
  --memory 512Mi \
  --cpu 1 \
  --concurrency 80 \
  --timeout 300 \
  --max-instances 20 \
  --no-allow-unauthenticated
```

**Critical config differences to preserve (never copy staging values to production):**

| Parameter | Staging value | Production value |
|---|---|---|
| `DB_URL` database name | `heirlooms-test` | `heirlooms` |
| `API_KEY` secret | `heirlooms-test-api-key` | _(not used — different auth mechanism)_ |
| Server memory | 512Mi | **2Gi** |
| Server region | us-central1 | **europe-west2** |
| Server service account | `340655233963-compute@...` | `heirlooms-server@heirlooms-495416.iam.gserviceaccount.com` |

### Post-deploy verification

Run these immediately after each service is deployed:

```bash
# Server health
curl -s https://api.heirlooms.digital/health
# Expected: ok

# Web health
curl -s -o /dev/null -w "%{http_code}" https://heirlooms.digital
# Expected: 200

# Error log check (run ~2 minutes after deploy)
gcloud logging read \
  'resource.type="cloud_run_revision" AND resource.labels.service_name="heirlooms-server" AND severity>=ERROR' \
  --limit=10 \
  --project heirlooms-495416 \
  --format='table(timestamp,textPayload)'
```

**Healthy state:** Health endpoint returns `ok`. Error log shows no new entries since the deploy. HikariCP "Broken pipe" entries immediately after deploy are expected and transient (cold-start Cloud SQL proxy initialisation) — they are not a blocker unless they persist beyond 2 minutes.

---

## 7. Health checks

### Staging

```bash
# API server
curl -s https://test.api.heirlooms.digital/health
# Expected: ok

# Web
curl -s -o /dev/null -w "%{http_code}" https://test.heirlooms.digital
# Expected: 200
```

### Production

```bash
# API server
curl -s https://api.heirlooms.digital/health
# Expected: ok

# Web
curl -s -o /dev/null -w "%{http_code}" https://heirlooms.digital
# Expected: 200
```

### What a healthy server response looks like

- `/health` returns the literal string `ok` (no JSON wrapper, no newline issues)
- HTTP status 200
- Response time < 500ms from a nearby machine (the Cloud Run cold-start is longer — up to ~3s on first request after a scale-from-zero)

### Checking Cloud Run logs

```bash
# Staging — last 20 errors
gcloud logging read \
  'resource.type="cloud_run_revision" AND resource.labels.service_name="heirlooms-server-test" AND severity>=ERROR' \
  --limit=20 \
  --project heirlooms-495416 \
  --format='table(timestamp,textPayload)'

# Production — last 20 errors
gcloud logging read \
  'resource.type="cloud_run_revision" AND resource.labels.service_name="heirlooms-server" AND severity>=ERROR' \
  --limit=20 \
  --project heirlooms-495416 \
  --format='table(timestamp,textPayload)'
```

---

## 8. Rollback procedure

Cloud Run rollback means re-deploying the previous pinned image digest. The digest of the currently-running image must have been recorded **before** the deploy (always captured in the release plan).

### Staging rollback

```bash
# Find the digest of the previous revision
gcloud run revisions list \
  --service heirlooms-server-test \
  --region us-central1 \
  --project heirlooms-495416 \
  --format='table(name,spec.containers[0].image,status.observedGeneration)'

# Re-deploy previous image by digest
gcloud run deploy heirlooms-server-test \
  --image europe-west2-docker.pkg.dev/heirlooms-495416/heirlooms/heirlooms-server@sha256:<PREVIOUS_DIGEST> \
  --region us-central1 \
  --project heirlooms-495416
```

**Verify:** `curl -s https://test.api.heirlooms.digital/health` returns `ok`.

### Production rollback

**Requires Bret's approval** — same rule as a forward deploy.

The previous production image digest must be in the release plan for the deploy that is being rolled back. If the release plan is unavailable:

```bash
# List recent revisions and their image digests
gcloud run revisions list \
  --service heirlooms-server \
  --region europe-west2 \
  --project heirlooms-495416 \
  --format='table(name,spec.containers[0].image,status.observedGeneration)'
```

Then re-deploy with the previous digest using the full production deploy command from §6, substituting the rollback digest.

**Key point:** Cloud Run rolling updates are zero-downtime. Rollback is instant once the re-deploy completes. You do not need to fiddle with traffic splits — deploying a prior image revision is sufficient.

---

## 9. Secret rotation

All secrets live in GCP Secret Manager under project `heirlooms-495416`. Rotation adds a new secret version and leaves the old one active until the service picks up the new value — there is no downtime.

### General rotation pattern

```bash
# Add a new secret version
gcloud secrets versions add <SECRET_NAME> \
  --data-file=- \
  --project heirlooms-495416 <<< "<SECRET_VALUE>"

# Or generate a random value inline (preferred for API keys):
gcloud secrets versions add <SECRET_NAME> \
  --data-file=<(openssl rand -base64 32) \
  --project heirlooms-495416
```

After adding the new version, Cloud Run services using `--set-secrets ... :latest` will pick up the new version on the next request (secrets are mounted as environment variables and reloaded on each container start — a new Cloud Run revision is not required if the service is configured with `:latest`).

### Staging API key rotation (SEC-010 pattern)

```bash
gcloud secrets versions add heirlooms-test-api-key \
  --data-file=<(openssl rand -base64 32) \
  --project heirlooms-495416
```

After rotating:
1. Tell the SecurityManager so they can complete the SEC-010 scan (checking for the old key in logs, redacting, and wiring the prevention hook).
2. Update any CI/CD scripts or test harnesses that use the old key.
3. Disable the old secret version once you have confirmed the new version is working:

```bash
# List versions to find the old one
gcloud secrets versions list heirlooms-test-api-key --project heirlooms-495416

# Disable the old version (replace VERSION with the version number)
gcloud secrets versions disable VERSION \
  --secret heirlooms-test-api-key \
  --project heirlooms-495416
```

### Production DB password rotation

Do not rotate the production DB password without a planned maintenance window and Bret's explicit approval. The rotation sequence is:

1. Add the new version to Secret Manager
2. Verify Cloud Run picks it up (watch logs for HikariCP auth errors)
3. Disable the old version only after the service has been stable for 15 minutes
4. Update the PostgreSQL user password to match:

```bash
# Connect to Cloud SQL and update the password
gcloud sql connect heirlooms-db --user=heirlooms --project heirlooms-495416
# In psql:
# ALTER USER heirlooms PASSWORD '<NEW_PASSWORD>';
```

**Never hardcode secret values.** Always fetch from Secret Manager or pass via environment variable.

---

## 10. Database access and migration notes

### Connecting to the staging database for inspection

```bash
gcloud sql connect heirlooms-db \
  --database heirlooms-test \
  --user heirlooms \
  --project heirlooms-495416
```

Enter the DB password when prompted (fetch it from Secret Manager first if needed):

```bash
gcloud secrets versions access latest \
  --secret=heirlooms-db-password \
  --project heirlooms-495416
```

### Flyway migration behaviour

Flyway runs automatically on server startup. On every container start, the server compares the migration files in `HeirloomsServer/src/main/resources/db/migration/` against the `flyway_schema_history` table and applies any pending migrations in version order (V1, V2, ... V30, ...).

**If a migration fails:**

1. The server will refuse to start and log a Flyway error. The Cloud Run health check will fail and the revision will not receive traffic — the previous revision stays live.

2. Check the server logs for the specific migration that failed:

```bash
gcloud logging read \
  'resource.type="cloud_run_revision" AND resource.labels.service_name="heirlooms-server" AND textPayload=~"Flyway"' \
  --limit=20 \
  --project heirlooms-495416 \
  --format='table(timestamp,textPayload)'
```

3. Common causes:
   - Migration SQL has a syntax error — fix the migration file and redeploy
   - Migration conflicts with existing schema (e.g. column already exists) — check if the migration was already partially applied and use `REPAIR` carefully
   - DB user lacks privileges for the DDL in the migration — check Cloud SQL user grants

4. If a migration is partially applied and left the schema in a broken state, do not attempt to fix it without Bret's involvement — partial Flyway repairs can corrupt the schema history table.

5. To inspect the current migration state (run from psql connection):

```sql
SELECT version, description, installed_on, success
FROM flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 10;
```

A `success = false` row indicates the failed migration. The server will not start until this is resolved (either repair or fix and re-run).

### Notes on shared DB server

Staging (`heirlooms-test`) and production (`heirlooms`) are separate databases on the **same Cloud SQL instance** (`heirlooms-db` in `europe-west2`). A migration applied to staging does not affect production. Each environment's server applies its own migrations independently on startup.

The GCS bucket (`heirlooms-uploads`) is currently **shared** between staging and production — uploads from staging tests may appear in production storage. This is a known issue tracked separately.

---

## 11. GCS CORS configuration

**When:** Adding a new web origin (e.g. a new Cloud Run URL) that needs to read GCS objects directly.

**Requires:** `cors.json` file at repo root (or `deploy/cors.json`), `gsutil` authenticated.

```bash
gsutil cors set cors.json gs://heirlooms-uploads
```

Verify:

```bash
gsutil cors get gs://heirlooms-uploads
```

**Current allowed origins (as of 2026-05-15):**

- `https://heirlooms.digital`
- `https://heirlooms-web-340655233963.us-central1.run.app`
- `https://test.heirlooms.digital`
- `https://heirlooms-web-test-340655233963.us-central1.run.app`

Update `cors.json` and commit the change before running `gsutil cors set`.

---

## Known friction points and open questions

| Issue | Status |
|---|---|
| Docker Desktop must be restarted manually before every build | Known — automation planned, no task yet |
| Staging and production share `heirlooms-uploads` GCS bucket | Known — staging may pollute production storage; track separately |
| Staging uses `us-central1`; production server uses `europe-west2` | Asymmetry is intentional but should be documented in architecture notes |
| Web deploy command not yet fully automated | Captured in this runbook — no further action needed |

---

*This runbook is the single reference for standard operations. For a production promotion, also read the most recent release plan in `docs/ops/`.*
