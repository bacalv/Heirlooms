# Task 2 — Android Features: Changes Summary

## What was built

### 2A — New-flow shortcut on shared plot card (GardenScreen)

Shared plot rows in the Garden now show two icon buttons in the header:

1. **Add icon** (`Icons.Filled.Add`) — opens `CreateFlowDialog` with the plot pre-selected
2. **RateReview icon** (`Icons.Filled.RateReview`) + badge — shows the pending staging count; tapping navigates to the bulk staging screen

Both buttons are gated on `plot.isShared` so they never appear on personal or system-defined plots.

The `CreateFlowDialog` in `FlowsScreen.kt` was promoted from `private` to `internal` and gained an `initialPlotId` parameter to support pre-selection.

### 2B — Bulk approval view for shared plot

New screen and ViewModel for reviewing all pending staging items across a shared plot in one place.

- **`PlotBulkStagingViewModel`** — loads via `api.getPlotStaging(plotId)`, manages checkbox selection state, handles encrypted DEK re-wrapping on approve (same logic as `StagingViewModel`), batch approves/rejects with reload
- **`PlotBulkStagingScreen`** — 3-column grid, per-item checkmark overlay, "Select all" header row, bottom action bar with Approve/Reject buttons, loading and done states
- **`GardenViewModel.refreshSharedStagingCounts()`** — polls staging counts for shared plots every 5 s alongside the just-arrived poll
- Route: `plot_bulk_staging/{plotId}/{plotName}` added to `AppNavigation`

### 2C — Auto-approve toggle in CreateFlowDialog

The staging toggle in `CreateFlowDialog` was relabelled to **"Auto-approve"** with subtitle **"Items skip staging and go straight to the plot"**. The switch now uses an `autoApprove` variable (inverted: `autoApprove = true` → `requiresStaging = false`) so the label reads naturally. The `requiresStaging` field continues to be sent correctly to the server.

## Files changed

### Android app (`HeirloomsApp/app/src/main/kotlin/digital/heirlooms/`)

| File | Change |
|------|--------|
| `ui/garden/GardenScreen.kt` | Add `onBulkStaging` param; collect `sharedStagingCounts`; add `newFlowForPlotId` + `CreateFlowDialog` host; extend `PlotRowSection` with Add/Review buttons; poll staging counts |
| `ui/garden/GardenViewModel.kt` | Add `_sharedStagingCounts` state + `refreshSharedStagingCounts()` |
| `ui/flows/FlowsScreen.kt` | Promote `CreateFlowDialog` + `buildCriteria` to `internal`; add `initialPlotId` param; relabel staging toggle as "Auto-approve" |
| `ui/flows/PlotBulkStagingViewModel.kt` | **New file** — ViewModel for bulk staging state machine |
| `ui/flows/PlotBulkStagingScreen.kt` | **New file** — Composable screen for bulk review |
| `ui/main/AppNavigation.kt` | Add `PLOT_BULK_STAGING` route + `plotBulkStaging()` helper; wire `onBulkStaging` in GardenScreen composable; add `PlotBulkStagingScreen` to NavHost |

### Android tests (`HeirloomsApp/app/src/test/kotlin/digital/heirlooms/ui/flows/`)

| File | Change |
|------|--------|
| `PlotBulkStagingViewModelTest.kt` | **New file** — 16 unit tests for selection state machine |

### Server (`HeirloomsServer/`)

No changes. The following existing endpoints are sufficient:
- `GET /api/plots/:id/staging` — pending items across all flows for a plot
- `POST /api/plots/:id/staging/:uploadId/approve`
- `POST /api/plots/:id/staging/:uploadId/reject`
- `POST /api/flows` + `PUT /api/flows/:id` — already accept `requiresStaging`

## Test results

- Android: `./gradlew test --no-daemon` — **BUILD SUCCESSFUL** (all tests pass)
- Server: no changes, tests unaffected
