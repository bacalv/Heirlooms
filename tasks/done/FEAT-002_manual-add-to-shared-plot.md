---
id: FEAT-002
title: Manual "add to shared plot" from photo detail — Android and web
category: Feature
priority: High
status: queued
depends_on: []
touches:
  - HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/garden/PhotoDetailScreen.kt
  - HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/garden/PhotoDetailViewModel.kt
  - HeirloomsWeb/src/pages/PhotoDetailPage.jsx
  - HeirloomsWeb/src/api.js
assigned_to: Developer
estimated: 1–2 days
---

## Background

Discovered during TST-003 (2026-05-15). The only way to get a photo into a shared plot
is via a trellis (auto-routing by criteria). There is no manual "add this photo to a
shared plot" gesture anywhere in the UI. The `addPlotItem` API exists on both server
and client (`HeirloomsApi.addPlotItem`, `POST /api/plots/{id}/items`) but is never
called from any UI surface.

## Proposed UX

**Photo detail → action menu → "Add to plot" → plot picker → confirm**

- Available on any photo the user owns
- Shows only shared plots the user is a member of (personal plots are for trellis use only)
- For shared plots: wraps the DEK under the plot key before calling `addPlotItem`
  (same E2EE pattern used when approving a staged item)
- If the photo already exists in the target plot: show "Already in this plot"

### Android

From `PhotoDetailScreen`, add an action (menu item or button):
- "Add to plot" → bottom sheet listing the user's shared plots
- Select a plot → confirm → call `api.addPlotItem(plotId, uploadId, wrappedDek, ...)`
- Wrap DEK under plot key using `VaultSession.getPlotKey(plotId)` +
  `VaultCrypto.wrapDekWithPlotKey`

### Web

From `PhotoDetailPage`, add a similar action:
- "Add to plot" → modal/dropdown listing shared plots
- Same DEK wrapping via `vaultCrypto.js` `wrapDekWithPlotKey`
- Call `POST /api/plots/{id}/items`

## Acceptance criteria

- User can manually add any owned photo to any shared plot they belong to
- DEK is correctly wrapped under the plot key (E2EE maintained)
- Photo appears in the shared plot's staging queue for other members to approve
  (if `requires_staging = true`) or directly in the plot (if not)
- Already-in-plot case handled gracefully
- Tested end-to-end on staging: Bret adds photo to Test share → User Y sees it in
  staging queue → User Y approves → photo visible in shared plot for both

## Completion notes

Completed 2026-05-15 by Developer-6 on branch `agent/developer-6/FEAT-002`.

### What was implemented

**Android** (two commits: `51da7cd`, `99897d5` — first is android, second is web):

- `PhotoDetailViewModel`: added `AddToPlotResult` sealed class, `addToPlotResult` StateFlow,
  `listSharedPlots()` (fetches + filters `visibility == "shared"`), and `addToPlot()` which:
  1. Fetches plot key from `VaultSession` cache or from server via `api.getPlotKey()` +
     `VaultCrypto.unwrapPlotKey()`
  2. Unwraps the photo DEK (handles master-key, sharing-key, and plot-key wrapped formats)
  3. Re-wraps DEK under the target plot key via `VaultCrypto.wrapDekWithPlotKey()`
  4. Repeats for thumbnail DEK if present
  5. Calls `api.addPlotItem()`, emits `Success` / `AlreadyPresent` / `Error`

- `PhotoDetailScreen`: added `AddToPlotSheet` (AlertDialog listing shared plots),
  `showAddToPlotSheet` state, toast feedback for all result states. The "Add to a shared plot"
  button is shown only for encrypted uploads in `GardenFlavour`.

- `HeirloomsApi.addPlotItem`: made `wrappedThumbnailDek` and `thumbnailDekFormat` params
  nullable (matching the server which accepts them as optional JSON fields).

**Web**:

- `api.js`: added `addPlotItem(apiKey, plotId, body)` helper (throws `'already_present'` on 409).
- `AddToPlotModal.jsx`: new component — lists shared plots, uses `loadSharedPlotKey` +
  `buildAddToPlotBody` (same DEK unwrap → re-wrap pattern as `StagingPanel`), calls `addPlotItem`.
- `PhotoDetailPage.jsx`: imports `AddToPlotModal`, adds `showAddToPlotModal` state, passes
  `onAddToPlot` prop to `GardenFlavour` (only for encrypted, non-composted uploads), renders modal.

### Acceptance criteria status

- User can manually add any owned encrypted photo to any shared plot they belong to: ✓
- DEK is correctly wrapped under the plot key (E2EE maintained): ✓ (mirrors staging approval pattern)
- Photo goes to staging queue (if `requires_staging=true`) or directly to plot: ✓ (server handles this)
- Already-in-plot case handled gracefully: ✓ (409 → toast "Already in this plot" on Android;
  error message in web modal)
- End-to-end test on staging: pending (requires manual QA)

### Notes

- The button is intentionally restricted to `isEncrypted` uploads. Plaintext/legacy uploads
  have no DEK to re-wrap and cannot be added to a shared E2EE plot.
- The `GardenFlavour` (garden view / default `from` param) exposes the action. `ExploreFlavour`
  does not — shared photos viewed via Explore belong to other users; the owner can add from
  their own Garden view.
