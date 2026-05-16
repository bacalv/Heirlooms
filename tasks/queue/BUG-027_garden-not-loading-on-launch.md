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
