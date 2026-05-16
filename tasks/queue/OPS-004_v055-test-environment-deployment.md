---
id: OPS-004
title: v0.55 test environment deployment
category: Operations
priority: High
status: queued
assigned_to: OpsManager
depends_on: []
touches:
  - GCP Cloud Run (test services only)
estimated: 1–2 hours (coordinating with Bret for Docker restart)
---

## Goal

Build and deploy the v0.55 iteration to the test environment once all developer agent branches are merged to main. Confirm the test environment is healthy and hand off to the Test Manager for TST-010.

## Prerequisites (confirm before requesting Docker restart from Bret)

1. All server-side task branches from this iteration are merged to main (coordinate with DevManager)
2. All web-side task branches merged to main (web and server can deploy separately if needed)
3. GCS bucket separation: `heirlooms-uploads-test` created and test Cloud Run `GCS_BUCKET` env var updated. This is a one-liner Bret can run: `gcloud run services update heirlooms-server-test --update-env-vars GCS_BUCKET=heirlooms-uploads-test --region us-central1`. Required before TST-010 runs any destructive test steps.

## Deployment steps

### Server

1. Build `:test` image: `docker build -t heirlooms-server:test .` (inside HeirloomsServer)
2. Tag and push to Artifact Registry: `docker tag heirlooms-server:test europe-west2-docker.pkg.dev/heirlooms-495416/heirlooms/heirlooms-server:test && docker push europe-west2-docker.pkg.dev/heirlooms-495416/heirlooms/heirlooms-server:test`
3. Deploy: `gcloud run deploy heirlooms-server-test --image europe-west2-docker.pkg.dev/heirlooms-495416/heirlooms/heirlooms-server:test --region us-central1`
4. Health check: `curl https://test.api.heirlooms.digital/health` → should return 200

### Web

5. Build web image (if web changes in this iteration)
6. Push and deploy `heirlooms-web-test` service
7. Smoke check: `curl https://test.heirlooms.digital` → 200

### Android

8. No Docker restart needed. Build Android staging flavor in IntelliJ and side-load after server is confirmed healthy.

## Notes

- Requires **one Docker Desktop restart** by Bret before the first build — coordinate timing. Batch all server changes into a single build.
- DevManager should coordinate a merge window: all agent branches merged before requesting the Docker restart.
- Test environment is throw-away; data/accounts created during TST-010 are disposable.
- Commit the deployed SHA to this task's completion notes.

## Acceptance criteria

- `GET https://test.api.heirlooms.digital/health` returns 200
- `GET https://test.heirlooms.digital` returns 200
- TestManager confirms ready for TST-010
- Deployed commit SHA recorded in completion notes

## Completion notes

<!-- Agent appends here and moves file to tasks/done/ -->
