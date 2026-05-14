---
id: REF-001
title: Rename Flow → Trellis across all platforms
category: Refactoring
priority: Medium
status: queued
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
