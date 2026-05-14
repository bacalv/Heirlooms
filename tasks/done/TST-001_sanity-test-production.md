---
id: TST-001
title: Sanity test production
category: Testing
priority: High
status: done
depends_on: []
touches: []
assigned_to: CTO
estimated: 5 minutes (manual)
---

## Goal

Confirm that the v0.53.1 production deployment is working end-to-end for an existing user with real data.

## This is a manual task

No agent can execute this — it requires a human with a real account and a physical device.

## Checklist

### Web (heirlooms.digital)
- [x] Log in successfully
- [x] Garden page loads and shows existing photos
- [x] Plots list loads

### Android (Heirlooms v0.53.0, versionCode 59)
- [x] App opens without crash
- [x] Garden loads with existing photos
- [x] Plots list loads
- [x] Can upload a photo

## Acceptance criteria

All checklist items pass. No regressions from v0.51.3 (the previous phone version).

## Completion notes

**Completed:** 2026-05-14 — manual test by Bret (CTO)

Garden loads, plots load, can see images, can upload a photo — verified on both Android app and webapp. No regressions observed. JSON serialisation refactor (DTOs) is safe.
