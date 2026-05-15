---
id: BUG-015
title: Web garden shared plot row still stale after trellis routing — BUG-012 fix incomplete
category: Bug Fix
priority: Medium
status: queued
assigned_to: Developer
depends_on: []
touches:
  - HeirloomsWeb/src/pages/GardenPage.jsx
estimated: 1-2 hours (agent)
---

## Background

BUG-012 was marked done with a `setTimeout` 1.5s delayed re-fetch in `handleQuickUpdateTags`
(`setPlotRefreshKey((k) => k + 1)` immediate + 1500ms delayed). Retested in TST-007
(2026-05-15) — the fix does not work.

## Steps to reproduce

1. Log in on web.
2. Upload a photo — it appears in Just Arrived.
3. Create a trellis: tag `new-tag` → target shared plot, `requires_staging = false` (auto-approve).
4. Tag the photo with `new-tag` from the Garden page.
5. Photo disappears from Just Arrived (correct).
6. Wait 10+ seconds — the shared plot row in the garden does **not** update.
7. Manually refresh the page — the photo now appears in the shared plot row.

## Expected behaviour

The shared plot row updates within ~2 seconds of the tag being applied, without any
page refresh.

## Investigation notes

The BUG-012 fix assumed tagging goes through `handleQuickUpdateTags`. Possible reasons
the fix isn't working:
- The tag update in this flow may take a different code path that doesn't call
  `handleQuickUpdateTags`
- The `setPlotRefreshKey` increment may not be re-fetching the shared plot row data,
  only the Just Arrived row
- The server-side routing may complete after the 1.5s window

Check what function handles the tag-apply action on the Garden page and trace whether
`setPlotRefreshKey` is actually being called and whether the re-fetch includes shared
plot row data.
