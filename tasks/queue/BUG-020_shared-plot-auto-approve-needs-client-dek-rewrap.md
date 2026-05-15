---
id: BUG-020
title: Shared plot trellis auto-approve — client-side DEK re-wrap to avoid mandatory staging
category: Bug Fix
priority: Medium
status: queued
depends_on: []
touches:
  - HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/garden/GardenViewModel.kt
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/repository/plot/TrellisRepository.kt
assigned_to: Developer
estimated: 3–4 hours
---

## Background

BUG-018 enforced `requires_staging = true` for all shared-plot trellises because the
server cannot re-wrap DEKs with the plot key (it never holds the plot key in plaintext).
The staging panel is the current re-wrapping point.

This means "auto-publish to a shared plot" is not possible even when the uploader is
already a member of the target plot and holds the plot key.

Discovered during v0.54 smoke test Step 4 (2026-05-15).

## Proposed fix

When the Android app routes an upload into a shared plot via an unstaged trellis:

1. Check if the target plot key is available in `VaultSession.plotKeys`
2. If yes: re-wrap the item DEK and thumbnail DEK with the plot key in the Android client
3. Submit the item to the server with the re-wrapped DEKs (new fields on the tag/upload
   request) so the server can insert directly into `plot_items` without staging

This enables true auto-approval for shared plots when the uploader is a member.

If the plot key is NOT available (e.g. not yet loaded), fall back to staging.

## Server changes required

- Accept optional `wrappedPlotItemDek`, `plotItemDekFormat`, `wrappedPlotThumbDek`,
  `plotThumbDekFormat`, `targetPlotId` parameters on the tag-update or trellis-route
  endpoint
- If provided and valid: insert directly into `plot_items` (bypassing staging)
- If not provided: keep existing staging behaviour

## Acceptance criteria

- User A (member of shared plot) tags a photo → item appears in the shared plot
  immediately without requiring approval, thumbnail and full image decrypt for all members
- User who is NOT a member of the target plot: item goes to staging as before
- Existing staging flow is unaffected

## Completion notes

<!-- Agent appends here and moves file to tasks/done/ -->
