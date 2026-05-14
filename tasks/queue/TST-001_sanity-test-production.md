---
id: TST-001
title: Sanity test production
category: Testing
priority: High
status: queued
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
- [ ] Log in successfully
- [ ] Garden page loads and shows existing photos
- [ ] Open a photo — full-resolution view loads
- [ ] Thumbnail loads correctly
- [ ] Plots list loads
- [ ] Open a shared plot (if any) — members and items visible

### Android (Heirlooms v0.53.0, versionCode 59)
- [ ] App opens without crash
- [ ] Garden loads with existing photos
- [ ] Tap a photo — detail view opens, media plays/displays
- [ ] Plots list loads
- [ ] Settings screen opens

### API (automated smoke test — can be run by an agent)
```bash
curl -s https://api.heirlooms.digital/health
curl -s -o /dev/null -w "%{http_code}" https://api.heirlooms.digital/api/content/uploads  # expect 401
```

## Acceptance criteria

All checklist items pass. No regressions from v0.51.3 (the previous phone version).

## Notes

The main risk is the JSON serialisation refactor (hand-rolled → DTOs). If any field changed name or nullability, the Android app might crash parsing a response. Watch for:
- Photos not loading (upload list response shape changed)
- Crash on plot detail (plot response shape changed)
