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

<!-- Agent appends here and moves file to tasks/done/ -->
