---
id: OPS-002
title: New environment setup guide
category: Operations
priority: Low
status: queued
depends_on: [OPS-001]
touches:
  - docs/ops/new-environment.md
assigned_to: OpsManager
estimated: half day
---

## Goal

Document how to stand up a complete Heirlooms environment from scratch — covering
all GCP infrastructure, secrets, domains, database, storage, and CORS config. Today
this knowledge only exists in session logs and tribal memory.

## Background

During TST-003 (2026-05-15), the staging environment had missing CORS config for the
test domain, undocumented deploy commands, and no record of the GCS setup steps. Each
gap cost time to diagnose. A new-environment guide would make it possible to spin up
a QA or feature-branch environment without help from Bret.

## Deliverables

Create `docs/ops/new-environment.md` covering:

### 1. GCP project setup
- Project creation and billing account
- Enable required APIs (Cloud Run, Cloud SQL, Secret Manager, Artifact Registry, GCS)
- Service account creation and IAM roles (see PA_NOTES git history for the exact
  `gcloud projects add-iam-policy-binding` commands that were required)

### 2. Cloud SQL
- Create or reuse the `heirlooms-db` instance
- Create a new database (`heirlooms-<env>`) and user
- Store DB password in Secret Manager

### 3. GCS bucket
- Create bucket `heirlooms-uploads-<env>` (or reuse `heirlooms-uploads` with path prefix)
- Grant `heirlooms-server` SA `objectAdmin` on the bucket — **critical, easy to miss**:
  ```bash
  gcloud storage buckets add-iam-policy-binding gs://heirlooms-uploads-<env> \
    --member="serviceAccount:heirlooms-server@heirlooms-495416.iam.gserviceaccount.com" \
    --role="roles/storage.objectAdmin"
  ```
- Set CORS config allowing PUT/GET/OPTIONS from the new environment's web domain:
  ```bash
  # cors.json: [{"origin":["https://<env>.heirlooms.digital"],"method":["GET","PUT","POST","OPTIONS"],"responseHeader":["Content-Type","X-Goog-Resumable"],"maxAgeSeconds":3600}]
  gcloud storage buckets update gs://heirlooms-uploads-<env> --cors-file=cors.json
  ```
  Missing CORS causes silent upload failures with "Couldn't upload" in the web app.
- Store GCS credentials JSON in Secret Manager

### 4. Artifact Registry
- Images are in `europe-west2-docker.pkg.dev/heirlooms-495416/heirlooms/`
- Tag convention: `:latest` (production), `:test` (staging), `:feature-<name>` (new envs)

### 5. Cloud Run services
- Server: `gcloud run deploy heirlooms-server-<env>` with full env var + secret set
  (see OPS-001 for the staging command as a reference template)
- Web: `gcloud run deploy heirlooms-web-<env>` — **must pass `VITE_API_URL` as a build arg**,
  not just as an env var. The Dockerfile has `ENV VITE_API_URL=$VITE_API_URL` in the build
  stage; omitting the arg bakes the prod URL into the JS bundle silently.

### 6. Domains and SSL
- Add custom domain mapping in Cloud Run
- DNS record pointing to Cloud Run
- SSL is provisioned automatically by Cloud Run once the domain is verified

### 7. Secret Manager
- `heirlooms-<env>-api-key` — generate a random API key for test seeding
- `heirlooms-db-password` — reuse or create per-env
- `heirlooms-gcs-credentials` — service account JSON

### 8. Flyway / first run
- On first server startup, Flyway applies all migrations to the new database
- Run the founding user seed (V29) — verify with `GET /health`

### 9. Android flavor (optional)
- Add a new product flavor in `app/build.gradle.kts` pointing at the new API URL
- Set `BASE_URL_OVERRIDE` to the new environment's API domain

### 10. Post-setup checklist
- [ ] `curl https://<env>.api.heirlooms.digital/health` returns `ok`
- [ ] GCS CORS allows PUT from the web domain
- [ ] Staging API key works: `curl -H "X-API-Key: ..." /api/auth/invites`
- [ ] First user can register and upload a photo end-to-end

## Notes

- Pull exact IAM and gcloud commands from the session archive
  (`docs/sessions/2026-01_to_2026-05-13_archive.md`, Milestone 3 section)
- OPS-001 runbook must be complete first — this guide builds on it

## Completion notes

**Completed by:** OpsManager  
**Date:** 2026-05-17  
**Branch:** `agent/ops-manager-1/OPS-002`  
**Deliverable:** `docs/ops/new-environment.md`

**What was written:**

The guide covers all 10 sections specified in the task, in runbook format
matching `docs/ops/runbook.md`:

1. GCP project setup — project creation, API enablement, and the three
   mandatory IAM role grants (`cloudsql.client`, `secretmanager.secretAccessor`,
   `storage.objectAdmin`) sourced from `docs/sessions/2026-05-14_PA_NOTES_archive.md`
   (section "GCP permissions — what actually worked")

2. Cloud SQL — create database and user inside the shared `heirlooms-db`
   instance; store password in Secret Manager

3. GCS bucket — create `heirlooms-uploads-<env>`; grant `objectAdmin` to the
   server SA (critical — easy to miss); set CORS config with explicit warning
   about silent upload failures; store GCS credentials in Secret Manager

4. Artifact Registry — tag convention table for prod/test/feature envs;
   authentication command

5. Cloud Run server — full `gcloud run deploy` command with all env vars and
   secrets; config diff table vs production; health check and log triage

6. Cloud Run web — `VITE_API_URL` build arg gotcha documented prominently:
   omitting `--build-arg` bakes an empty string into the JS bundle silently;
   `--platform linux/amd64` requirement noted; full deploy command

7. Domains and SSL — domain mapping commands; GoDaddy DNS record set; SSL
   auto-provisioning note

8. Secret Manager checklist — all three secrets, generation command for API key

9. Flyway / first run — migration verification via `flyway_schema_history`;
   founding user seed confirmation; first invite token command

10. Android flavor — `build.gradle.kts` snippet for a new product flavor;
    existing flavors (`prod`, `staging`) documented as reference

11. Post-setup checklist — 9-point ordered checklist covering health, CORS,
    SA grant, API key, Flyway, registration, upload end-to-end

**Sources used:**
- `docs/sessions/2026-05-14_PA_NOTES_archive.md` — exact IAM commands, Cloud
  Run deploy commands, domain mapping DNS records, VITE_API_URL note
- `docs/ops/runbook.md` (OPS-001) — staging deploy commands as reference
  template, CORS config, Docker restart protocol reference
- `HeirloomsWeb/Dockerfile` — confirmed `ARG VITE_API_URL` / `ENV VITE_API_URL`
  build-stage declaration
- `HeirloomsApp/app/build.gradle.kts` — confirmed existing flavor structure
- `docs/PA_NOTES.md` — GCS bucket separation decision (2026-05-16),
  naming convention (test not staging)
