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

<!-- Agent appends here and moves file to tasks/done/ -->
