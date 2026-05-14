---
id: DONE-004
title: Staging environment
category: Infrastructure
priority: High
status: done
completed: 2026-05-14
---

## What was done

- Cloud SQL: `heirlooms-test` database created on existing `heirlooms-db` instance
- Cloud Run: `heirlooms-server-test` deployed to us-central1 (same image, test DB, API_KEY enabled)
- Cloud Run: `heirlooms-web-test` deployed to us-central1 (points at test API)
- Domain mappings: `test.api.heirlooms.digital` and `test.heirlooms.digital` — SSL certs active
- Android: `staging` product flavor — `applicationId .staging`, burnt-orange icon with TEST badge, `test.api.heirlooms.digital`
- Test API key stored in `heirlooms-test-api-key` secret (Secret Manager)
- V29 Flyway migration seeds founding user so API key works on fresh DB

## URLs

- Test API: https://test.api.heirlooms.digital
- Test web: https://test.heirlooms.digital
- Generate invite: `curl -H "X-Api-Key: k71CFcf59rdvmFqfV_nZhBd4W7DUao4jAvRvmTE4neA" https://test.api.heirlooms.digital/api/auth/invites`
