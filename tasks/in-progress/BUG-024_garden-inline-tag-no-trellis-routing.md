---
id: BUG-024
title: Garden inline tag edit doesn't trigger trellis routing — missing prewrappedDeks
category: Bug Fix
priority: Medium
status: queued
depends_on: []
touches:
  - HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/garden/GardenScreen.kt
  - HeirloomsWeb/src/pages/GardenPage.jsx
assigned_to: Developer
estimated: 2–3 hours
---

## Problem

Applying a trellis-matching tag to an item from the Garden inline editor (the quick
tag input on the garden row) does not trigger trellis routing. The item stays in Just
Arrived instead of routing to the target plot.

Tagging from the photo detail view works correctly because it calls
`api.updateTags(uploadId, tags, prewrappedDeks)` with DEK re-wrap data.

The garden inline editor calls `api.updateTags(uploadId, newTags)` with no
`prewrappedDeks`, so the server has no way to perform the DEK re-wrap needed for
shared-plot trellis routing. On web, the same problem exists.

Found during TST-010 (2026-05-16), Journeys 5 and 9.

## Fix

The garden inline tag editor needs to:
1. Load the upload's wrapped DEK
2. Build `prewrappedDeks` for all matched trellis target plots (same logic as
   `PhotoDetailViewModel.buildPrewrappedDeks`)
3. Include them in the `updateTags` call

This is more complex than it sounds — the inline editor needs access to the master
key and plot keys. Consider reusing the logic from `PhotoDetailViewModel`.

## Acceptance criteria

- Tagging an item from the garden row triggers trellis routing correctly
- Auto-approve trellises route immediately; staging trellises go to the queue
- Same fix applied to web garden inline tag input
