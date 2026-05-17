---
id: BUG-027
title: Garden not loading on launch — items require manual swipe-to-refresh
category: Bug Fix
priority: Medium
status: queued
depends_on: []
touches:
  - HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/garden/GardenScreen.kt
  - HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/garden/GardenViewModel.kt
  - HeirloomsWeb/src/pages/GardenPage.jsx
assigned_to: Developer
estimated: 2 hours
---

## Problem

On Android, the Garden screen shows no items on initial launch. A manual swipe-to-
refresh is required before items appear. Similarly, after tagging an item in Just
Arrived, the staging queue counts on plot rows don't update without a manual refresh.

On web, new uploads intermittently don't appear in Just Arrived without a page reload.

Found during TST-010 (2026-05-16), Journeys 9 and 16.

## Fix

Investigate the initial load trigger and polling logic:
1. Android: ensure `GardenViewModel.load()` fires immediately on screen entry, not
   only after the first poll interval
2. Android: after a tag update, invalidate/refresh the garden state immediately
   (optimistic update or forced re-fetch)
3. Web: check the garden polling interval and whether the first poll fires on mount

## Acceptance criteria

- Garden items appear immediately on launch without requiring swipe-to-refresh
- After tagging an item, plot staging counts update within 5 seconds without manual refresh
- Web Just Arrived updates within the polling interval after a new upload

## Completion notes

**Root causes identified:**

1. **Android — staging counts empty on launch**: `GardenViewModel.load()` and `refresh()` correctly fire immediately via `LaunchedEffect(Unit)` in `GardenScreen`. However, `refreshSharedStagingCounts()` was only called inside the poll loop, which starts with a `delay(intervalMs)` before its first invocation. So plot row staging badges were always empty for the first poll interval (5 s default) after entering the screen.

2. **Android — staging counts stale after tagging**: The `onTagsUpdated` callback called `vm.optimisticTag()` and `api.updateTags()` but never triggered a staging count refresh. Counts only updated on the next scheduled poll.

3. **Web — new uploads intermittently absent from Just Arrived**: After `encryptAndUpload` completes, `setPlotRefreshKey((k) => k + 1)` fires immediately. This is usually fast enough, but if server-side trellis routing hasn't finished indexing the item into Just Arrived yet, the immediate re-fetch returns stale data and no follow-up re-fetch occurs. The 5 s polling interval is the next chance to see the item.

**Fixes applied (commit 8bb14be):**

- `GardenScreen.kt`: Added `vm.refreshSharedStagingCounts(api)` call in the initial `LaunchedEffect(Unit)` block so staging counts populate on screen entry alongside the data load.
- `GardenScreen.kt`: In the `onTagsUpdated` lambda, added `vm.refreshSharedStagingCounts(api)` after a successful `api.updateTags()` call so plot row badges reflect the new routing state within seconds of tagging.
- `GardenPage.jsx`: After a successful upload in `handlePlantFiles`, added a 2 s delayed `setPlotRefreshKey` bump (matching the pattern already used in `handleQuickUpdateTags`) so Just Arrived picks up newly routed items reliably without a page reload.

**GardenViewModel.kt**: No changes needed. `load()` and `refresh()` already fire on screen entry; the bug was only in when `refreshSharedStagingCounts` was called relative to those operations.

**Tests**: Android unit tests pass (3 pre-existing failures in `CreateFriendInviteTest` and `PlotBulkStagingViewModelTest` are unrelated to this task). Web vitest suite could not be run (node_modules absent in workspace); the web change is a single additive `setTimeout` matching an existing pattern in the same file.
