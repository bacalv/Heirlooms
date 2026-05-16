---
id: UX-002
title: Closed plots should show a locked state — disable approve/share actions
category: UX
priority: Medium
status: queued
depends_on: []
touches:
  - HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/shared/SharedPlotsScreen.kt
  - HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/garden/GardenScreen.kt
  - HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/flows/PlotBulkStagingViewModel.kt
  - HeirloomsWeb/src/pages/SharedPlotsPage.jsx
assigned_to: Developer
estimated: 2–3 hours
---

## Background

Discovered during TST-003 (2026-05-15). The user accidentally closed "Test share" via
the status toggle in the Shared screen (small tap target + BUG-006 back-stack confusion).
Nothing in the UI indicated the plot was closed — the garden row and shared plot looked
identical to an open plot. When they tried to approve a staged item, they got a
cryptic "1 item(s) failed to approve" error (HTTP 403 from the server).

## Problem

When a shared plot is closed (`plot_status = 'closed'`):
- No visual distinction from an open plot in the Garden or Shared screens
- Approve staging items still appears active → fails with opaque error
- Trellis routing and share actions still appear available → fail silently or with errors

## Deliverables

### Android

**Garden row:** If the plot's `plotStatus == "closed"`, show a lock icon on the plot
row header and mute/grey the row to distinguish it from open plots.

**Shared screen:** Show a prominent "Closed" badge or lock icon on the plot card.
Disable the "Review" (staging approval) button — either hide it or show it greyed
out with a tooltip "Plot is closed".

**Staging approval screen:** If navigated to a closed plot's staging queue (e.g. via
deep link or stale nav state), show an inline banner "This plot is closed — reopen
it to approve items" rather than silently failing on approve.

### Web

Mirror the same treatment on `SharedPlotsPage.jsx`:
- Closed plot cards get a lock icon and muted appearance
- Approve/share actions are disabled with a visible reason

## Acceptance criteria

- A closed shared plot is visually distinct from an open one on all surfaces
- Approve and share actions are disabled (not just failing) when the plot is closed
- Reopening the plot immediately restores the active appearance and actions
- No silent 403 errors from attempting actions on a closed plot

## Completion notes

Completed 2026-05-16 by developer-5 on branch `agent/developer-5/UX-002`.

### Changes made

**Android — GardenScreen.kt (`PlotRowSection`)**
- Added `Icons.Filled.Lock` import
- Introduced `plotIsClosed` and `rowAlpha` (0.5f when closed) derived from `plot?.plotStatus`
- Title text, chevron icon, PeopleAlt icon, and "Shared by" text all receive muted alpha when closed
- Lock icon + "closed" text label displayed in the row header when closed
- Staging review `IconButton` is disabled (`enabled = !plotIsClosed`) with greyed tint when closed
- Staging count badge suppressed when plot is closed

**Android — SharedPlotsScreen.kt (`JoinedCard`)**
- Added `Icons.Filled.Lock` import
- `JoinedCard` computes `cardAlpha = 0.55f` when closed; applies to `containerColor`, text and icon tints
- Replaced plain "closed" text label with lock icon + "Closed" text badge

**Android — PlotBulkStagingViewModel.kt**
- Added `plotClosed: Boolean = false` to `PlotBulkStagingState`
- `load()` now fetches `listPlots()` after the staging items load and sets `plotClosed` from the matching plot's `plotStatus`; fails silently (defaults to false) if the API call throws

**Android — PlotBulkStagingScreen.kt (flows/)**
- Added `Icons.Filled.Lock` import
- Added `plotClosed: Boolean = false` parameter (optional early hint for callers; VM state is source of truth)
- `isClosed = state.plotClosed || plotClosed` drives all gating
- Inline banner shown when `isClosed`: lock icon + "This plot is closed — reopen it to approve items"
- Approve and Reject buttons have `!isClosed` added to their `enabled` condition

**Web — SharedPlotsPage.jsx**
- Added `LockIcon` SVG component (padlock)
- `MembershipCard`: closed plots get `opacity-70`, lighter border (`border-forest-08`), `bg-white/60`, and forest text at 60% opacity
- Closed badge replaced with lock icon + "Closed" text in a small pill
- Transfer Ownership button is disabled (with tooltip) when plot is closed; re-enabled on reopen

**Android unit tests — PlotBulkStagingViewModelTest.kt**
- Added 4 new tests covering `plotClosed` default, state mutation round-trips, and post-close invariant

### API model confirmation
`Plot.plotStatus` and `SharedMembership.plotStatus` were already present in `Models.kt` — no DTO changes needed.

### Navigation note
`PlotBulkStagingScreen` accepts `plotClosed: Boolean = false` as an optional hint but self-determines the closed state via `PlotBulkStagingViewModel.load()` (which calls `listPlots()`). This handles the deep-link / stale navigation case without requiring navigation graph changes.
