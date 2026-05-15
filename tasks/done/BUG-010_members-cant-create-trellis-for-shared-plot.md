---
id: BUG-010
title: Shared plot members can't create a trellis targeting a plot they're a member of
category: Bug Fix
priority: High
status: queued
depends_on: []
touches:
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/service/plot/TrellisService.kt
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/repository/plot/TrellisRepository.kt
assigned_to: Developer
estimated: 1–2 hours
---

## Background

Discovered during TST-003 (2026-05-15). User Y (a member of "Test share", not the owner)
tried to create a trellis on the web targeting "Test share". The form showed
"Target plot not found" — the server rejected the request because the trellis service
checks ownership, not membership.

## Problem

Members should be able to create trellises routing their own photos into shared plots
they have joined — that is the primary mechanism for contributing to a shared plot.
Only allowing owners to do this makes shared plots one-directional.

## Investigation

Check `TrellisService.createTrellis` (or equivalent) — find the plot ownership check
and determine whether it should be relaxed to allow `plot_members` with `status = 'joined'`
as valid targets.

Also check whether there are legitimate reasons to restrict this to owners only
(e.g. preventing members from routing spam into a plot without the owner's knowledge).
If so, consider a plot-level setting: "allow members to create trellises targeting
this plot" (default: true for shared plots).

## Acceptance criteria

- A member of a shared plot can create a trellis targeting that plot
- The trellis routes their photos into the plot's staging queue (respecting
  `requires_staging` as normal)
- Owner-only plots (private plots) remain restricted to the owner

## Completion notes

**Completed: 2026-05-15**

### Root cause

`TrellisService.createTrellis` had a hard ownership check:
```kotlin
if (targetPlot.ownerUserId != userId)
    return CreateTrellisResult.Invalid("Target plot not found")
```
This rejected any user who wasn't the plot owner, even joined members of shared plots.

### Fix

Replaced the owner-only guard in `TrellisService.createTrellis` with a combined check:
- User is the plot owner, **OR**
- The target plot is `visibility = "shared"` AND `plotRepo.isMember(targetPlotId, userId)` returns true (status='joined')

Private plots remain owner-only. Public plots were already reachable by owners, and the `requires_staging=true` enforcement in `TrellisRepository.createTrellis` handles the spam concern for public plots.

No plot-level setting was needed — the existing staging mechanism already gives the owner full control over what gets into the collection.

### Files changed

- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/service/plot/TrellisService.kt` — relaxed ownership check in `createTrellis`
- `HeirloomsServer/src/test/kotlin/digital/heirlooms/server/TrellisServiceTest.kt` — new unit tests covering: owner on shared plot, owner on private plot, joined member on shared plot (the bug fix), non-member rejected, non-owner on private plot rejected

### Tests

All tests pass: `./gradlew test --no-daemon` — BUILD SUCCESSFUL
