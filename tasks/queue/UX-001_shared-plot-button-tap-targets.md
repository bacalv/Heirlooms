---
id: UX-001
title: Android: shared plot action buttons have insufficient tap targets
category: UX
priority: Low
status: queued
depends_on: []
touches:
  - HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/garden/PlotSheets.kt
  - HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/shared/SharedPlotsScreen.kt
assigned_to: Developer
estimated: 1–2 hours
---

## Problem

The Invite, Review, and Edit action buttons on shared plots are visually appealing but
physically small — difficult to tap accurately on a phone screen. Observed during
TST-003 (2026-05-15).

## Investigation

Review the current button implementation in `PlotSheets.kt` and `SharedPlotsScreen.kt`.
Consider one or more of the following approaches:

- **Minimum tap target size** — apply `Modifier.minimumInteractiveComponentSize()` or
  explicit `Modifier.size(48.dp)` padding to bring buttons up to Material Design's
  recommended 48×48dp minimum touch target, without changing the visual size
- **Larger touch area via padding** — wrap small buttons in extra clickable padding using
  `Modifier.clickable` on a larger container
- **Bottom sheet actions** — move Invite / Review / Edit into a bottom sheet action list
  (similar to the existing plot settings sheet) where each row has a full-width tap target
- **Icon + label rows** — replace small icon buttons with full-width labeled rows that are
  easier to hit

Whichever approach is chosen should preserve the current visual style.

## Acceptance criteria

- All three actions (Invite, Review, Edit) meet the 48dp minimum tap target on a
  physical device
- Visual appearance is unchanged or improved
- Verified on a physical device (not emulator only)

## Completion notes

<!-- Agent appends here and moves file to tasks/done/ -->
