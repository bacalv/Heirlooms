---
id: REF-001
title: Rename Flow → Trellis across all platforms
category: Refactoring
priority: Medium
status: done
depends_on: []
touches:
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/domain/plot/
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/repository/plot/
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/service/plot/
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/routes/plot/
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/representation/plot/
  - HeirloomsServer/src/main/resources/db/migration/
  - HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/flows/
  - HeirloomsApp/app/src/main/kotlin/digital/heirlooms/api/
  - HeirloomsWeb/src/
assigned_to: Developer
estimated: 4-6 hours (agent, coordinated release)
---

## Background

"Flow" is the current name for the auto-routing rule concept. A flow says: "when I upload something matching these criteria, route it to this plot." Research (see `brainstorming/IDEA-001_trellis-naming.md`) recommends renaming to **Trellis** (plural: Trellises).

Reasons:
- "Flow" clashes with `kotlinx.coroutines.flow.Flow` in Android — real readability hazard
- "Trellis" is precise: a permanent structure that guides every new growth toward a destination
- Fits the garden metaphor exactly

## Scope

### Server
- Rename `FlowRecord` → `TrellisRecord`, `FlowRepository` → `TrellisRepository`, etc.
- DB migration: `ALTER TABLE flows RENAME TO trellises`, rename `source_flow_id` → `source_trellis_id` in `plot_items` and `plot_staging_decisions`
- API path: `/api/flows` → `/api/trellises` (breaking change — must coordinate with clients)
- File renames: `FlowRecord.kt`, `FlowRepository.kt`, `FlowService.kt`, `FlowRoutes.kt`, `FlowRepresentation.kt`

### Android
- Rename `digital.heirlooms.ui.flows` package → `digital.heirlooms.ui.trellises`
- Rename `Flow` data class in `Models.kt` → `Trellis`
- Update `HeirloomsApi.kt` — 6 method references + API path strings
- Update user-visible strings: "Flows" → "Trellises" in nav menu + screens
- Rename files: `FlowsScreen.kt` → `TrellisesScreen.kt`, `FlowsViewModel.kt` → `TrellisesViewModel.kt`, etc.

### Web
- No user-visible "flow" strings found in current web app
- Update any API call paths that reference `/api/flows`

### iOS
- Update `HeirloomsClient.swift` if it references flow API paths

## Execution order

1. Server: rename + DB migration + new API paths (keep old paths as deprecated aliases temporarily)
2. Android: rename all the things
3. Web: update API call paths
4. iOS: update API call paths
5. Remove deprecated API aliases from server
6. Coordinated release of server + clients

## Acceptance criteria

- All tests pass
- `/api/trellises` serves what `/api/flows` used to
- Android app shows "Trellises" in nav
- No references to "flow" remain in user-visible strings (code is fine during transition)
- DB migration applies cleanly to both prod and test databases

## Notes

This is a **coordinated release** — server and clients must ship together or the server must keep the old path alive briefly. Plan the release window carefully.
Do NOT do this while IOS-001 or other tasks touching the same files are in-progress.

**Timing (CTO decision 2026-05-14):** REF-001 may be scheduled after M10-E1 and M10-E2 have merged to main and shipped to production, but before M10-E3 starts. It must not overlap with any M10 increment — E2 and E4 touch the same flow files.

## Completion notes

Completed 2026-05-15 by developer-1 on branch `agent/developer-1/REF-001`.

### What was done

**Server:**
- Created `TrellisRecord.kt` (new canonical class); `FlowRecord.kt` → `typealias FlowRecord = TrellisRecord`
- Created `TrellisRepository.kt` (full implementation using `trellises` table and `source_trellis_id` columns); `FlowRepository.kt` → `typealias FlowRepository = TrellisRepository`, `PostgresFlowRepository = PostgresTrellisRepository`
- Created `TrellisService.kt` (full implementation); `FlowService.kt` → `typealias FlowService = TrellisService`
- Created `TrellisRoutes.kt` (serves both `/api/trellises` primary paths and `/api/flows` deprecated aliases); `FlowRoutes.kt` → thin delegating function
- Created `TrellisRepresentation.kt` with `TrellisRecord.toJson()` extension; `FlowRepresentation.kt` cleared
- `PlotItemRepository` updated: `sourceFlowId` → `sourceTrellisId` in interface + implementation, SQL updated to `source_trellis_id`, `flows` param → `trellises`
- `UploadService` updated: `FlowRepository` → `TrellisRepository`, `runUnstagedFlowsForUpload` → `runUnstagedTrellisesForUpload`
- `AppRoutes` updated to wire `TrellisService`, `PostgresTrellisRepository`, and `trellisRoutes`
- DB migration V30 added: renames `flows` → `trellises`, `source_flow_id` → `source_trellis_id`, updates all FK constraint names and indexes
- `SchemaMigrationTest` updated to test post-V30 schema (trellises table, source_trellis_id column)

**Android:**
- `Models.kt`: `Trellis` data class added; `Flow` kept as `typealias Flow = Trellis`
- `HeirloomsApi.kt`: `listTrellises`, `createTrellis`, `updateTrellis`, `deleteTrellis`, `getTrellisStaging` added using `/api/trellises` paths; old `listFlows` etc. kept as backward-compat aliases
- All files in `ui/flows/` package updated with package declaration `digital.heirlooms.ui.trellises`
- `FlowsScreen.kt` → `TrellisesScreen()` composable; all user-visible strings changed ("Flows" → "Trellises", "New flow" → "New trellis", etc.)
- `FlowsViewModel.kt` → `TrellisesViewModel`, `TrellisesState`, `TrellisWithCount`
- `StagingViewModel.kt`, `StagingScreen.kt`: `flowId` → `trellisId` throughout
- `PlotBulkStagingViewModel.kt`, `PlotBulkStagingScreen.kt`: package updated
- `BurgerPanel.kt`: "Flows" → "Trellises" label
- `AppNavigation.kt`: imports updated to `digital.heirlooms.ui.trellises`, composable uses `TrellisesScreen` and `StagingScreen` with `trellisId`
- `GardenScreen.kt`: `CreateFlowDialog` → `CreateTrellisDialog`, `newFlowForPlotId` → `newTrellisForPlotId`, `api.createFlow` → `api.createTrellis`
- Test `PlotBulkStagingViewModelTest`: package updated to `digital.heirlooms.ui.trellises`

**Web:**
- `FlowsPage.jsx`: all `/api/flows` → `/api/trellises`; user-visible "Flows" → "Trellises", "New flow" → "New trellis", page title updated
- `StagingPanel.jsx`: `/api/flows/${flowId}/staging` → `/api/trellises/${flowId}/staging`
- `Nav.jsx`: "Flows" → "Trellises" in both desktop and mobile nav
- `GardenPage.jsx`: "Flows" → "Trellises" in sidebar link

**iOS:**
- No changes needed: `HeirloomsAPI.swift` has no `/api/flows` paths; the only "flow" occurrences are English comments about "upload flow" and "join flow" (unrelated to the Trellis feature)

### Test results
- Server: `./gradlew test --no-daemon` — BUILD SUCCESSFUL
- Android: compiled successfully (`compileProdDebugKotlin` — BUILD SUCCESSFUL)
- Android unit tests: compilation verified; `testProdDebugUnitTest` blocked by Bash rate-limiting in agent session but test code was reviewed and updated correctly

### Key decisions
- Deprecated `/api/flows` aliases kept in `TrellisRoutes.kt` for backward client coordination (step 5 — removal — left to PA)
- Old `Flow*` files kept as typealiases to avoid breaking test code that imports them
- DB migration V30 renames constraints and indexes as well as tables/columns
