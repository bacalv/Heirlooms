---
id: TST-002
title: Create first user in staging environment
category: Testing
priority: High
status: done
depends_on: []
touches: []
assigned_to: CTO
estimated: 5 minutes (manual)
---

## Goal

Register the first real user in the `heirlooms-test` database so staging can be used for manual testing.

## Acceptance criteria

- Registration completes without error
- App shows an empty garden (no uploads yet — this is a fresh database)
- Login works on subsequent app opens

## Completion notes

**Completed:** 2026-05-14 — manual test by Bret (CTO)

Used the staging Android app (Heirlooms Test, burnt-orange icon). Entered the API key as the invite code. Registration completed successfully. App shows an empty garden — staging database is live and isolated. TST-003 (manual staging checklist) is now unblocked.
