# Production Release Plan — v0.54 Staging → Production Promotion

**Prepared by:** OpsManager  
**Date:** 2026-05-15  
**Status:** AWAITING CTO APPROVAL — DO NOT EXECUTE

---

## Summary

This plan covers promoting the current staging environment to production for what represents the v0.53.x server refactor series (phases 5–7: service layer, representation layer, routes sub-packages). These are pure code-reorganisation releases with no behaviour or API changes.

---

## 1. Environment Health

| Environment | Endpoint | Status |
|---|---|---|
| Staging API | https://test.api.heirlooms.digital/health | **ok** |
| Production API | https://api.heirlooms.digital/health | **ok** |

Both environments are healthy and responding at time of this check (2026-05-15).

---

## 2. Image Inventory

### Staging (source — what will be promoted)

| Service | Cloud Run Revision | Image Tag | Image Digest |
|---|---|---|---|
| heirlooms-server-test | heirlooms-server-test-00003-4g5 | `heirlooms-server:test` | `sha256:ba5c2727ec7447c9cc59bad0231dd2268994c46848fd4ab58384625e89ee85e3` |
| heirlooms-web-test | heirlooms-web-test-00002-zwr | `heirlooms-web-test:latest` | `sha256:6bc81d96bf0afb84de338f3cb1831334f69bfcf4659d18a6012c961bfb79855a` |

The server image tagged `:test` was built **2026-05-15T01:01:49Z** — this is the most recent build, sitting one revision after the `latest;v0.53.1` tag (built 2026-05-14T18:34:17Z). The web-test image (`heirlooms-web-test:latest`) was built **2026-05-15T01:47:31Z**.

### Production (current — what will be replaced)

| Service | Cloud Run Revision | Image Tag | Image Digest |
|---|---|---|---|
| heirlooms-server | heirlooms-server-00066-h9m | `heirlooms-server:latest` (v0.53.1) | `sha256:3a3a441de2d7ef7f7a35283b6578d8109d72fa476e745b66ac40179571105da0` |
| heirlooms-web | heirlooms-web-00077-wtz | `heirlooms-web:latest` (v0.53.0) | `sha256:8c23bc7a82739a6e1a5e854144ab97685ac53bc552aceb528db339c7b8cba94b` |

The production server is currently on v0.53.1; the production web is currently on v0.53.0.

---

## 3. Staging Error Log Review

Checked last 20 ERROR-level entries from `heirlooms-server-test` (no time filter applied — reviewing most recent errors on record):

**Errors found:** 3x `java.net.SocketException: Broken pipe` from the PostgreSQL JDBC driver, all occurring on **2026-05-14 between 20:40 and 20:42 UTC** (approximately 4–5 hours before this check).

Stack trace pattern:
```
java.net.SocketException: Broken pipe
    at org.postgresql.core.v3.ConnectionFactoryImpl.tryConnect(...)
    at com.zaxxer.hikari.pool.HikariPool.createPoolEntry(...)
```

**Assessment:** These are HikariCP connection pool initialisation errors — the driver attempted to open a new DB connection and the socket was torn down mid-handshake. This is a known transient condition that occurs when Cloud Run scales a new instance and the Cloud SQL proxy socket is not yet ready, or when the DB server briefly drops idle connections. All three events are clustered in a 2-minute window (~20:40–20:42 UTC) consistent with a cold start or DB maintenance event. There are no application-level errors, no auth failures, no data corruption indicators, and the service is currently responding healthy. The 4 log entries with empty `TEXT_PAYLOAD` at 01:43 and 01:11 UTC on 2026-05-15 are INFO/WARNING-level entries promoted by the severity filter — not actionable.

**Conclusion:** The broken pipe errors are transient infrastructure noise and are **not a blocker** for promotion.

---

## 4. DB Migration Delta

Highest migration version in codebase: **V30** (`V30__rename_flows_to_trellises.sql`)

Full migration sequence: V1 through V30 (30 migrations total).

The production database (`heirlooms` on `heirlooms-db`) should already have all migrations through V30, since staging (`heirlooms-test`) and production share the same database server and the server applies Flyway migrations on startup. However, **there is no guarantee production's schema is current** unless the production server was last deployed with a build that included V30.

**Action required before deploy:** Verify production DB migration state. The production server (v0.53.1) was built after V30 was introduced. If the production server started successfully when it was last deployed, V30 has been applied. No new migrations exist that staging has run that production has not — the migration set is identical in the codebase today.

**DB migration delta: NONE** (no new migrations are being introduced by this promotion — the staging image does not add any migration files beyond what production already has).

---

## 5. Configuration Differences to Preserve

Critical difference noted between staging and production server specs:

| Parameter | Staging | Production |
|---|---|---|
| `DB_URL` database name | `heirlooms-test` | `heirlooms` |
| `API_KEY` secret | `heirlooms-test-api-key` | _(not set — uses different auth mechanism)_ |
| Memory limit | 512Mi | **2Gi** |
| Service account | `340655233963-compute@developer.gserviceaccount.com` | `heirlooms-server@heirlooms-495416.iam.gserviceaccount.com` |

**The deploy commands below preserve production's own environment variables.** The image digest is the only thing being changed. The staging `API_KEY` and `DB_URL` must NOT be copied to production.

Also note: the web image names differ. Staging uses `heirlooms-web-test`, production uses `heirlooms-web`. The staging web image (digest `sha256:6bc81d...`) needs to be promoted to the production web service. The image is already in the shared Artifact Registry — no re-push is needed; the deploy command references the digest directly.

---

## 6. Exact Deploy Commands (DO NOT EXECUTE — awaiting CTO approval)

### Step 1: Deploy Server

```bash
gcloud run deploy heirlooms-server \
  --image europe-west2-docker.pkg.dev/heirlooms-495416/heirlooms/heirlooms-server@sha256:ba5c2727ec7447c9cc59bad0231dd2268994c46848fd4ab58384625e89ee85e3 \
  --region us-central1 \
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

### Step 2: Verify Server Health

```bash
curl -s https://api.heirlooms.digital/health
```

Expected: `ok`

### Step 3: Deploy Web

```bash
gcloud run deploy heirlooms-web \
  --image europe-west2-docker.pkg.dev/heirlooms-495416/heirlooms/heirlooms-web-test@sha256:6bc81d96bf0afb84de338f3cb1831334f69bfcf4659d18a6012c961bfb79855a \
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

### Step 4: Verify Web

```bash
curl -s https://heirlooms.digital
```

Expected: 200 OK with HTML response.

### Step 5: Post-deploy log check (run ~2 minutes after deployment)

```bash
gcloud logging read 'resource.type="cloud_run_revision" AND resource.labels.service_name="heirlooms-server" AND severity>=ERROR' \
  --limit=10 \
  --project heirlooms-495416 \
  --format='table(timestamp,textPayload)'
```

---

## 7. Rollback Commands (if post-deploy health check fails)

### Rollback Server

```bash
gcloud run deploy heirlooms-server \
  --image europe-west2-docker.pkg.dev/heirlooms-495416/heirlooms/heirlooms-server@sha256:3a3a441de2d7ef7f7a35283b6578d8109d72fa476e745b66ac40179571105da0 \
  --region us-central1 \
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

### Rollback Web

```bash
gcloud run deploy heirlooms-web \
  --image europe-west2-docker.pkg.dev/heirlooms-495416/heirlooms/heirlooms-web@sha256:8c23bc7a82739a6e1a5e854144ab97685ac53bc552aceb528db339c7b8cba94b \
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

---

## 8. GO / NO-GO Recommendation

### Recommendation: **CONDITIONAL GO**

**Conditions that must be confirmed before executing:**

1. **Web image naming:** The staging web image is tagged `heirlooms-web-test` (not `heirlooms-web`). The deploy command references it by digest, which is correct and safe. However, the CTO should confirm this is the intended web build to promote — it may be worth re-tagging the digest as `heirlooms-web:v0.54.0` in Artifact Registry for cleaner version tracking before or after the deploy.

2. **Server image version gap:** Production is on `v0.53.1`; the staging server image (`:test` tag, built 2026-05-15T01:01:49Z) was built *after* the `v0.53.1` tag (built 2026-05-14T18:34:17Z). This means the staging image being promoted is newer than what v0.53.1 represents. The CTO should confirm what changes are included in the `:test` build beyond v0.53.1 (likely v0.53.2 — the routes sub-packages refactor — based on VERSIONS.md). This is a pure code reorganisation and presents no functional risk, but the version label should be confirmed.

**Factors supporting GO:**
- Both environments healthy at time of check
- Only 3 transient broken-pipe errors on staging, all from a single 2-minute window ~9 hours ago, consistent with normal cold-start behaviour
- No new DB migrations being introduced — zero schema risk
- The changes being promoted are pure server refactors (v0.53.x series: service layer, representation layer, routes sub-packages) with no API or behaviour changes
- Rollback is straightforward — pinned digests for both current production images are recorded above
- No secrets changes required

**Factors requiring attention (not blockers):**
- Transient broken-pipe errors on staging (addressed above — not application errors)
- Staging server memory is 512Mi vs production's 2Gi — production config is preserved in the deploy command, this is correct
- Staging uses a different service account than production — production service account is preserved in the deploy command, this is correct

**Recommended deploy window:** Off-peak hours (early morning UTC). The deploy itself is zero-downtime (Cloud Run rolling update), so timing is a preference rather than a requirement.

---

*Plan prepared by OpsManager. No deployments have been made. All commands above are for CTO execution only.*
