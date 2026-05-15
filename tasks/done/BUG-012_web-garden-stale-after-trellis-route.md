---
id: BUG-012
title: Web garden doesn't update shared plot row after trellis routing — requires manual navigation
category: Bug Fix
priority: Medium
status: queued
depends_on: []
touches:
  - HeirloomsWeb/src/pages/GardenPage.jsx
assigned_to: Developer
estimated: 1–2 hours
---

## Background

Discovered during TST-003 (2026-05-15). When a photo in "Just Arrived" is tagged
with a trellis-matching tag on the web, two things happen:

1. The photo correctly disappears from the Just Arrived row (optimistic UI update)
2. The target shared plot row does **not** update — the photo only appears there
   after a full page refresh or navigating away and back

A related issue: the garden's background poll interval for new items feels slow in
general (user-reported).

## Problem

### Issue 1 — Stale shared plot row after trellis routing

When a tag is applied that triggers trellis routing, the server moves the item to
the staging queue (or directly into the plot if auto-approve is on). The web
correctly removes the item from Just Arrived optimistically, but does not refresh
the affected plot row. The plot row stays stale until the next poll cycle or manual
navigation.

**Expected:** After tagging triggers routing, the target plot row should refresh
immediately (or within a short poll interval) to reflect the new state.

**Fix approach:** After a successful tag update that triggers trellis routing,
trigger a targeted refresh of all shared plot rows in the garden. This can be done
by re-fetching the affected plot's items after the tag API call resolves — or by
triggering the existing garden refresh logic.

### Issue 2 — Garden poll interval feels slow

The 30-second poll interval for the Just Arrived row (`refreshJustArrived`) means
newly uploaded items can take up to 30 seconds to appear in the garden without a
manual action. Consider reducing the interval or triggering an immediate refresh
after an upload completes.

## Acceptance criteria

- After tagging a Just Arrived item with a trellis-matching tag, the target plot
  row updates within 2 seconds without requiring navigation or manual refresh
- The garden poll interval is reviewed and adjusted to feel responsive

## Completion notes

Implemented 2026-05-15 in `HeirloomsWeb/src/pages/GardenPage.jsx`.

**Issue 1 — Stale shared plot row after trellis routing:**
`handleQuickUpdateTags` already called `setPlotRefreshKey((k) => k + 1)` immediately after a successful tag update, but the server needs a short window to complete trellis routing before the refreshed plot list reflects the routed item. Added a follow-up `setTimeout(() => setPlotRefreshKey((k) => k + 1), 1500)` to fire a second silent re-fetch 1.5 s later. Together the immediate + delayed refresh satisfies the "within 2 seconds" acceptance criterion without any loading flash.

**Issue 2 — Poll interval:**
Reduced the background poll interval from 30 s to 10 s (`setInterval(..., 10_000)`) so newly uploaded or routed items surface sooner during normal use.

Build verified clean.
