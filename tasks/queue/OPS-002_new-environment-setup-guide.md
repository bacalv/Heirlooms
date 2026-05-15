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
- Set CORS config allowing PUT from the new environment's web domain:
  ```bash
  gsutil cors set cors.json gs://heirlooms-uploads-<env>
  ```
- Store GCS credentials JSON in Secret Manager

### 4. Artifact Registry
- Images are in `europe-west2-docker.pkg.dev/heirlooms-495416/heirlooms/`
- Tag convention: `:latest` (production), `:test` (staging), `:feature-<name>` (new envs)

### 5. Cloud Run services
- Server: `gcloud run deploy heirlooms-server-<env>` with full env var + secret set
  (see OPS-001 for the staging command as a reference template)
- Web: `gcloud run deploy heirlooms-web-<env>` with `VITE_API_URL` build arg

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

<!-- OpsManager appends here and moves file to tasks/done/ -->
