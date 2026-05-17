# Heirlooms — New Environment Setup Guide

**Maintained by:** OpsManager  
**Last updated:** 2026-05-17  
**Audience:** Bret (CTO) and OpsManager only

This guide covers standing up a complete, isolated Heirlooms environment from
scratch. Follow it in order — each section assumes the previous one is done.

> **Naming convention:** The non-production environment is called **test**, not
> staging. Reserve "staging" for a future prod-snapshot environment (OPS-003).
> For a new feature-branch environment, replace `<env>` below with a short slug
> (e.g. `qa`, `feature-payments`).

---

## Contents

1. [GCP project setup](#1-gcp-project-setup)
2. [Cloud SQL](#2-cloud-sql)
3. [GCS bucket](#3-gcs-bucket)
4. [Artifact Registry](#4-artifact-registry)
5. [Cloud Run — server](#5-cloud-run--server)
6. [Cloud Run — web](#6-cloud-run--web)
7. [Domains and SSL](#7-domains-and-ssl)
8. [Secret Manager checklist](#8-secret-manager-checklist)
9. [Flyway / first run](#9-flyway--first-run)
10. [Android flavor](#10-android-flavor-optional)
11. [Post-setup checklist](#11-post-setup-checklist)

---

## 1. GCP project setup

### 1.1 Project and billing

For a new GCP project (skip if reusing `heirlooms-495416`):

```bash
gcloud projects create <PROJECT_ID> --name="Heirlooms <env>"
gcloud billing projects link <PROJECT_ID> --billing-account=<BILLING_ACCOUNT_ID>
gcloud config set project <PROJECT_ID>
```

For existing environments, the project is always `heirlooms-495416`.

### 1.2 Enable required APIs

```bash
gcloud services enable \
  run.googleapis.com \
  sqladmin.googleapis.com \
  sql-component.googleapis.com \
  secretmanager.googleapis.com \
  artifactregistry.googleapis.com \
  storage.googleapis.com \
  --project heirlooms-495416
```

### 1.3 Service account and IAM roles

The server runs as `heirlooms-server@heirlooms-495416.iam.gserviceaccount.com`.
This SA already exists for the production and test environments — reuse it for
feature-branch environments unless isolation is required.

**If creating a new SA for an isolated environment:**

```bash
gcloud iam service-accounts create heirlooms-server-<env> \
  --display-name="Heirlooms Server <env>" \
  --project heirlooms-495416
```

**Grant the three required roles — all three are mandatory:**

```bash
# 1. Cloud SQL connection
gcloud projects add-iam-policy-binding heirlooms-495416 \
  --member="serviceAccount:heirlooms-server@heirlooms-495416.iam.gserviceaccount.com" \
  --role="roles/cloudsql.client"

# 2. Secret Manager read access
gcloud projects add-iam-policy-binding heirlooms-495416 \
  --member="serviceAccount:heirlooms-server@heirlooms-495416.iam.gserviceaccount.com" \
  --role="roles/secretmanager.secretAccessor"

# 3. GCS object access — see §3 for the bucket-level grant
#    (bucket-level is preferred over project-level for isolation)
```

> **Important:** These three roles were discovered to be necessary during M3
> (2026-05-05). Missing any one of them causes silent startup failures. Grant
> all three before first deploy.

---

## 2. Cloud SQL

Heirlooms uses a single Cloud SQL instance (`heirlooms-db`, `europe-west2`)
shared across environments. Each environment gets its own **database** (not
instance) within that instance.

### 2.1 Create the database

```bash
gcloud sql connect heirlooms-db \
  --user postgres \
  --project heirlooms-495416
```

In the psql shell:

```sql
CREATE DATABASE "heirlooms-<env>";
CREATE USER heirlooms WITH PASSWORD '<DB_PASSWORD>';
GRANT ALL PRIVILEGES ON DATABASE "heirlooms-<env>" TO heirlooms;
```

> The `heirlooms` user already exists for prod/test — reuse it for feature
> envs (it has access per-database, not server-wide). Store the password in
> Secret Manager (see §8) — never commit it.

### 2.2 Store the DB password in Secret Manager

```bash
gcloud secrets create heirlooms-<env>-db-password \
  --data-file=- \
  --project heirlooms-495416 <<< "<DB_PASSWORD>"
```

For prod/test, the shared secret `heirlooms-db-password` is already in place
and reused — do not create a duplicate.

---

## 3. GCS bucket

### 3.1 Create the bucket

Each environment must have its own bucket. Do not share production storage
with any other environment.

```bash
gcloud storage buckets create gs://heirlooms-uploads-<env> \
  --project heirlooms-495416 \
  --location us-central1 \
  --uniform-bucket-level-access
```

Existing buckets:
- `gs://heirlooms-uploads` — production
- `gs://heirlooms-uploads-test` — test environment (separated 2026-05-16)

### 3.2 Grant the server SA objectAdmin on the bucket

**This step is critical and easy to miss.** Without it the server starts
successfully (it only checks GCS at upload time), but every upload silently
fails with "Couldn't upload" in the web app and a 403 in server logs.

```bash
gcloud storage buckets add-iam-policy-binding gs://heirlooms-uploads-<env> \
  --member="serviceAccount:heirlooms-server@heirlooms-495416.iam.gserviceaccount.com" \
  --role="roles/storage.objectAdmin"
```

Verify the grant was applied:

```bash
gcloud storage buckets get-iam-policy gs://heirlooms-uploads-<env>
```

### 3.3 Set CORS configuration

The web app uploads directly to GCS via signed/resumable URLs. Without CORS
the browser blocks the upload silently. This was the root cause of the upload
failure discovered during TST-003 (2026-05-15).

Create `deploy/cors-<env>.json`:

```json
[{
  "origin": ["https://<env>.heirlooms.digital", "https://heirlooms-web-<env>-340655233963.us-central1.run.app"],
  "method": ["GET", "PUT", "POST", "OPTIONS"],
  "responseHeader": ["Content-Type", "X-Goog-Resumable"],
  "maxAgeSeconds": 3600
}]
```

Apply it:

```bash
gcloud storage buckets update gs://heirlooms-uploads-<env> \
  --cors-file=deploy/cors-<env>.json
```

Verify:

```bash
gcloud storage buckets describe gs://heirlooms-uploads-<env> --format="json(cors)"
```

> **Current CORS config for test environment (`gs://heirlooms-uploads-test`):**
> - `https://test.heirlooms.digital`
> - `https://heirlooms-web-test-340655233963.us-central1.run.app`

### 3.4 Store GCS service account credentials in Secret Manager

Download a JSON key for the server SA (or reuse the existing one):

```bash
gcloud iam service-accounts keys create /tmp/heirlooms-sa.json \
  --iam-account heirlooms-server@heirlooms-495416.iam.gserviceaccount.com \
  --project heirlooms-495416

gcloud secrets create heirlooms-<env>-gcs-credentials \
  --data-file=/tmp/heirlooms-sa.json \
  --project heirlooms-495416

rm /tmp/heirlooms-sa.json
```

For prod/test, the shared secret `heirlooms-gcs-credentials` is already in
Secret Manager — reuse it for feature environments unless key isolation is
required.

---

## 4. Artifact Registry

Images are stored in one registry shared across all environments:

```
europe-west2-docker.pkg.dev/heirlooms-495416/heirlooms/
```

Tag convention:

| Environment | Server image tag | Web image tag |
|---|---|---|
| Production | `heirlooms-server:latest` | `heirlooms-web:latest` |
| Test | `heirlooms-server:test` | `heirlooms-web-test:latest` |
| Feature `<env>` | `heirlooms-server:<env>` | `heirlooms-web-<env>:latest` |

No registry setup is needed for a new environment — the existing registry
already accepts any tag. Authenticate with:

```bash
gcloud auth configure-docker europe-west2-docker.pkg.dev
```

---

## 5. Cloud Run — server

### 5.1 Build and push the server image

```bash
cd HeirloomsServer && ./gradlew shadowJar

docker build \
  --platform linux/amd64 \
  -f Dockerfile.update \
  -t europe-west2-docker.pkg.dev/heirlooms-495416/heirlooms/heirlooms-server:<env> \
  .

docker push europe-west2-docker.pkg.dev/heirlooms-495416/heirlooms/heirlooms-server:<env>
```

> `Dockerfile.update` layers on top of `:latest` — keep build time to ~2s.
> See `docs/ops/runbook.md §1` for the Docker Desktop restart protocol
> required before every build on Bret's machine.

### 5.2 Deploy to Cloud Run

```bash
gcloud run deploy heirlooms-server-<env> \
  --image europe-west2-docker.pkg.dev/heirlooms-495416/heirlooms/heirlooms-server:<env> \
  --region us-central1 \
  --platform managed \
  --memory 512Mi \
  --cpu 1 \
  --concurrency 80 \
  --timeout 300 \
  --max-instances 10 \
  --no-allow-unauthenticated \
  --service-account heirlooms-server@heirlooms-495416.iam.gserviceaccount.com \
  --add-cloudsql-instances heirlooms-495416:europe-west2:heirlooms-db \
  --set-env-vars "STORAGE_BACKEND=GCS,GCS_BUCKET=heirlooms-uploads-<env>,DB_USER=heirlooms" \
  --set-env-vars "DB_URL=jdbc:postgresql:///heirlooms-<env>?cloudSqlInstance=heirlooms-495416:europe-west2:heirlooms-db&socketFactory=com.google.cloud.sql.postgres.SocketFactory" \
  --set-secrets "DB_PASSWORD=heirlooms-db-password:latest" \
  --set-secrets "GCS_CREDENTIALS_JSON=heirlooms-gcs-credentials:latest" \
  --set-secrets "API_KEY=heirlooms-<env>-api-key:latest" \
  --project heirlooms-495416
```

**Key differences from production (do not copy prod values):**

| Parameter | Test/feature value | Production value |
|---|---|---|
| `DB_URL` database name | `heirlooms-<env>` | `heirlooms` |
| `GCS_BUCKET` | `heirlooms-uploads-<env>` | `heirlooms-uploads` |
| Memory | `512Mi` | `2Gi` |
| Region | `us-central1` | `us-central1` (web); **`europe-west2`** (prod server — do not change) |

> Note: Domain mappings only work in `us-central1` — always deploy new
> environments there. Production server runs in `europe-west2` for latency
> reasons; this asymmetry is intentional and pre-dates domain mapping.

### 5.3 Health check

```bash
curl -s https://<env>.api.heirlooms.digital/health
# Expected: ok
```

If the health check fails, check server logs immediately:

```bash
gcloud logging read \
  'resource.type="cloud_run_revision" AND resource.labels.service_name="heirlooms-server-<env>" AND severity>=ERROR' \
  --limit=20 \
  --project heirlooms-495416 \
  --format='table(timestamp,textPayload)'
```

---

## 6. Cloud Run — web

### 6.1 VITE_API_URL must be passed as a build arg — not just an env var

**This is a common gotcha.** `VITE_API_URL` is baked into the JS bundle at
build time by Vite. The `HeirloomsWeb/Dockerfile` declares:

```dockerfile
ARG VITE_API_URL
ENV VITE_API_URL=$VITE_API_URL
```

If `--build-arg VITE_API_URL=...` is omitted, Docker falls back to the
`ARG` default — which is empty in the build stage. Vite then bakes in an
empty string, and the app silently sends all API requests to the wrong origin.
The deployed page will load, but every API call will fail.

**Always supply the build arg explicitly:**

```bash
cd HeirloomsWeb

docker build \
  --platform linux/amd64 \
  --build-arg VITE_API_URL=https://<env>.api.heirlooms.digital \
  --build-arg VITE_COMMIT=$(git rev-parse --short HEAD) \
  -t europe-west2-docker.pkg.dev/heirlooms-495416/heirlooms/heirlooms-web-<env>:latest \
  .

docker push europe-west2-docker.pkg.dev/heirlooms-495416/heirlooms/heirlooms-web-<env>:latest
```

> `--platform linux/amd64` is mandatory. Without it, `docker buildx` produces
> an OCI manifest index that Cloud Run rejects.

### 6.2 Deploy to Cloud Run

```bash
gcloud run deploy heirlooms-web-<env> \
  --image europe-west2-docker.pkg.dev/heirlooms-495416/heirlooms/heirlooms-web-<env>:latest \
  --region us-central1 \
  --platform managed \
  --port 80 \
  --memory 512Mi \
  --cpu 1 \
  --concurrency 80 \
  --max-instances 10 \
  --no-allow-unauthenticated \
  --project heirlooms-495416
```

### 6.3 Verify

```bash
curl -s -o /dev/null -w "%{http_code}" https://<env>.heirlooms.digital
# Expected: 200
```

---

## 7. Domains and SSL

### 7.1 Custom domain mapping

```bash
# Web
gcloud beta run domain-mappings create \
  --service heirlooms-web-<env> \
  --domain <env>.heirlooms.digital \
  --region us-central1

# Server (API)
gcloud beta run domain-mappings create \
  --service heirlooms-server-<env> \
  --domain <env>.api.heirlooms.digital \
  --region us-central1
```

### 7.2 DNS records

Add these records in the DNS registrar (currently GoDaddy) for
`heirlooms.digital`:

```
<env>.heirlooms.digital      A     216.239.32.21
<env>.heirlooms.digital      A     216.239.34.21
<env>.heirlooms.digital      A     216.239.36.21
<env>.heirlooms.digital      A     216.239.38.21
<env>.heirlooms.digital      AAAA  2001:4860:4802:32::15
<env>.heirlooms.digital      AAAA  2001:4860:4802:34::15
<env>.heirlooms.digital      AAAA  2001:4860:4802:36::15
<env>.heirlooms.digital      AAAA  2001:4860:4802:38::15
<env>.api.heirlooms.digital  CNAME ghs.googlehosted.com
```

### 7.3 SSL

SSL certificates are provisioned automatically by Cloud Run once the domain
mapping is active and DNS propagation is complete. Allow up to 15 minutes.
Check status:

```bash
gcloud beta run domain-mappings describe \
  --domain <env>.heirlooms.digital \
  --region us-central1
```

---

## 8. Secret Manager checklist

All secrets for a new environment:

| Secret name | Value | Notes |
|---|---|---|
| `heirlooms-<env>-api-key` | Random 32-byte base64 string | Generate with `openssl rand -base64 32` |
| `heirlooms-db-password` | DB user password | Shared across environments; already exists |
| `heirlooms-gcs-credentials` | Service account JSON | Shared; already exists. Create per-env if isolation required |

Generate and store the API key:

```bash
gcloud secrets create heirlooms-<env>-api-key \
  --data-file=<(openssl rand -base64 32) \
  --project heirlooms-495416
```

Verify all secrets are accessible to the server SA:

```bash
gcloud secrets get-iam-policy heirlooms-<env>-api-key --project heirlooms-495416
```

The `heirlooms-server` SA must appear with `roles/secretmanager.secretAccessor`.
This is covered by the project-level grant in §1.3 — but verify if you used a
per-environment SA instead.

---

## 9. Flyway / first run

Flyway runs automatically on every server startup. On first start against a
fresh database it applies all migrations in version order (V1 → current).

### Verify migrations applied

```bash
# Connect to the new database
gcloud sql connect heirlooms-db \
  --database heirlooms-<env> \
  --user heirlooms \
  --project heirlooms-495416

# In psql — check the migration history
SELECT version, description, installed_on, success
FROM flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 5;
```

All rows should have `success = true`. A `success = false` row means the server
will refuse to start on the next deploy — investigate before proceeding.

### Seed with a founding user

The V29 migration creates the founding user seed. Confirm it ran:

```bash
curl -s https://<env>.api.heirlooms.digital/health
# Expected: ok
```

If `/health` returns `ok`, Flyway completed successfully. Then generate an
invite token to onboard the first test user:

```bash
curl -s -X GET https://<env>.api.heirlooms.digital/api/auth/invites \
  -H "X-API-Key: $(gcloud secrets versions access latest \
        --secret=heirlooms-<env>-api-key \
        --project=heirlooms-495416)"
```

---

## 10. Android flavor (optional)

To point an Android build at the new environment, add a product flavor in
`HeirloomsApp/app/build.gradle.kts`:

```kotlin
create("<env>") {
    dimension = "environment"
    applicationIdSuffix = ".<env>"
    versionNameSuffix = "-<env>"
    resValue("string", "app_name", "Heirlooms <Env>")
    buildConfigField("String", "BASE_URL_OVERRIDE", "\"https://<env>.api.heirlooms.digital\"")
}
```

Build and install:

```bash
cd HeirloomsApp
./gradlew assemble<Env>Debug
adb install -r app/build/outputs/apk/<env>/debug/app-<env>-debug.apk
```

Existing flavors for reference:
- `prod` — `BASE_URL_OVERRIDE = ""` (uses default)
- `staging` — `BASE_URL_OVERRIDE = "https://test.api.heirlooms.digital"` (the test environment)

---

## 11. Post-setup checklist

Work through this in order. Do not tick a box until it passes.

- [ ] `curl -s https://<env>.api.heirlooms.digital/health` returns `ok`
- [ ] `curl -s -o /dev/null -w "%{http_code}" https://<env>.heirlooms.digital` returns `200`
- [ ] GCS CORS: confirm PUT is allowed from the new web domain  
      (`gcloud storage buckets describe gs://heirlooms-uploads-<env> --format="json(cors)"`)
- [ ] Server SA has `objectAdmin` on the GCS bucket  
      (`gcloud storage buckets get-iam-policy gs://heirlooms-uploads-<env>"`)
- [ ] API key works:  
      ```bash
      curl -s https://<env>.api.heirlooms.digital/api/auth/invites \
        -H "X-API-Key: $(gcloud secrets versions access latest \
              --secret=heirlooms-<env>-api-key --project=heirlooms-495416)"
      ```
      Expected: JSON response with invite token (not 401)
- [ ] Flyway migrations complete — all rows in `flyway_schema_history` have `success = true`
- [ ] First user can register via invite link
- [ ] First user can upload a photo end-to-end (web → signed URL → GCS → confirmed)
- [ ] No errors in Cloud Run logs 5 minutes after first upload

---

## Known gaps and future work

| Gap | Status |
|---|---|
| Domain mapping DNS records must be added manually in GoDaddy | Known — no API automation yet |
| Docker Desktop must be restarted manually before every build on Bret's machine | Known — automation planned, see `docs/ops/runbook.md §1` |
| GCS bucket creation and SA grant are not scripted — manual step | Known — candidate for `scripts/create-env.sh` |
| CORS file must be manually written and applied per environment | Known — same script candidate |

---

*Reference: `docs/ops/runbook.md` — routine operations for existing environments.*  
*Reference: `docs/sessions/2026-05-14_PA_NOTES_archive.md` — original GCP setup commands from M3.*
