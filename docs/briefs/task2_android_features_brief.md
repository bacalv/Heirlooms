# Task 2 — Android Features Brief

## Overview

Three quality-of-life improvements for owners and members of shared plots in the Heirlooms Android app.

---

## 2A — New-flow shortcut on shared plot card

**User story:** As a shared-plot owner, I want to quickly create a flow targeting that plot directly from the garden, without navigating to the Flows screen first.

**What was built:**
- A small `Add` icon button added to the header row of every shared plot section in the Garden screen.
- Tapping the button opens the existing `CreateFlowDialog` with the target plot pre-selected.
- The button only appears when `plot.visibility == "shared"` (using the existing `Plot.isShared` extension); personal and system-defined plots are unaffected.
- Flow creation is handled via `api.createFlow(...)` inline from the GardenScreen scope.

**Key files changed:**
- `ui/garden/GardenScreen.kt` — new `onNewFlow` callback on `PlotRowSection`; `CreateFlowDialog` shown in-screen
- `ui/flows/FlowsScreen.kt` — `CreateFlowDialog` promoted from `private` to `internal`; `initialPlotId` parameter added; `buildCriteria` promoted to `internal`

---

## 2B — Bulk approval view for shared plot

**User story:** As a shared-plot owner, I want a single screen to review and approve/reject all pending staging items across every flow feeding that plot, with checkboxes for batch actions.

**What was built:**
- A new `PlotBulkStagingViewModel` that:
  - Loads pending items via `GET /api/plots/:id/staging` (existing endpoint, via `HeirloomsApi.getPlotStaging`)
  - Manages a `selected: Set<String>` for the checkbox state machine
  - Handles `approveSelected` (with DEK re-wrapping for encrypted items) and `rejectSelected` as batch operations
  - Reloads after each batch and tracks a `doneCount`
- A new `PlotBulkStagingScreen` composable with:
  - 3-column thumbnail grid with top-right checkmark overlays
  - "Select all" checkbox in the header with count label
  - Bottom action bar: "Reject selected" (outlined, red) + "Approve selected" (filled, Forest green) buttons
  - Loading / empty / done states
- A `RateReview` icon button + `BadgedBox` badge showing pending count in each shared plot row header
- New route `plot_bulk_staging/{plotId}/{plotName}` wired in `AppNavigation`
- `GardenViewModel.refreshSharedStagingCounts()` polls counts alongside `refreshJustArrived` every 5 seconds

**Server:** No changes needed. `GET /api/plots/:id/staging` and the existing approve/reject endpoints are sufficient.

---

## 2C — Auto-approve toggle in flows

**User story:** As a flow creator, I want to configure a flow so that items matching its criteria go straight into the plot without needing manual approval.

**What was built:**
- The `requiresStaging` field was already stored server-side and sent by the Android API client (`createFlow`, `updateFlow`).
- The `CreateFlowDialog` already had a staging toggle. Its label was updated from "Require approval before adding" to an **Auto-approve** toggle with subtitle "Items skip staging and go straight to the plot", using inverted logic (`autoApprove = true` → `requiresStaging = false`).
- No server changes required.

---

## Testing

- 16 new unit tests in `PlotBulkStagingViewModelTest` covering the selection state machine (toggleItem, toggleSelectAll, allSelected, clearError, doneCount).
- All 140+ existing tests continue to pass.
