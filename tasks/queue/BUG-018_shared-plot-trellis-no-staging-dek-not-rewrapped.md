---
id: BUG-018
title: Shared plot trellis without staging — items auto-flow with original DEK, members can't decrypt
category: Bug Fix
priority: High
status: queued
depends_on: []
touches:
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/repository/plot/TrellisRepository.kt
assigned_to: Developer
estimated: 1–2 hours
---

## Problem

When a trellis targets a **shared plot** and `requires_staging = false`, the server
auto-inserts matching uploads into `plot_items` with no DEK re-wrapping
(`runUnstagedTrellisesForUpload` inserts only `upload_id, plot_id, source_trellis_id,
added_by` — no wrapped DEK columns). The item's DEK remains wrapped under the
**owner's master key**. Other plot members see the item in the shared plot but cannot
decrypt it because they don't hold the owner's master key.

The same is true for `autoPopulateTrellis` (bulk-inserts on trellis creation/staging-off).

## Root cause

`TrellisRepository.createTrellis` enforces staging policy:
- `private` → always false (owner's own content)
- `public` → always true (public plots always require staging)
- **`shared` → defers to the caller's `requiresStaging` flag** ← gap

There is no enforcement that shared-plot trellises must use staging. If the user creates
a shared-plot trellis with `requiresStaging = false`, items auto-flow without DEK
re-wrapping and members can't decrypt.

Discovered during v0.54 staging retest (2026-05-15): Fire1 (web) created a trellis
targeting a shared plot without staging enabled. Fire2 (Android member) could see
items in the plot but thumbnails and detail view never decrypted.

## Fix

Enforce `requires_staging = true` for all trellises targeting shared plots, matching
the existing public-plot policy.

### 1. `TrellisRepository.createTrellis` — line ~76

```kotlin
val effectiveStaging = when (targetPlot.visibility) {
    "private"          -> false
    "public", "shared" -> true   // shared now forced, same as public
    else               -> requiresStaging
}
```

### 2. `TrellisRepository.updateTrellis` — enforce same policy

When updating a trellis whose target is a shared plot, clamp `requiresStaging` to
`true` (same pattern as create).

### 3. `runUnstagedTrellisesForUpload` — defensive guard

Add a filter to skip any trellis whose target plot has `visibility = 'shared'` even
if `requires_staging` is falsely stored as `false` in the DB (guards against legacy
rows and any future bypass):

```kotlin
val trellises = listTrellises(userId)
    .filter { !it.requiresStaging }
    .filter { it.targetPlotVisibility != "shared" }  // defensive guard
```

This may require adding `targetPlotVisibility` to `TrellisRecord` (join on `plots`
table) or a separate lookup.

### 4. Web UI — hide the staging toggle for shared plots

The trellis creation/edit form should either hide the staging toggle or lock it to
"on" when the target plot is a shared plot, matching server enforcement.

## Migration / existing data

Existing trellises in the DB with `requires_staging = false` targeting shared plots
are silently broken. A Flyway migration should update them:

```sql
UPDATE trellises t
SET requires_staging = true
FROM plots p
WHERE t.target_plot_id = p.id
  AND p.visibility = 'shared'
  AND t.requires_staging = false;
```

## Acceptance criteria

- Creating a trellis targeting a shared plot with `requiresStaging = false` returns
  the trellis with `requiresStaging = true` (server overrides)
- `runUnstagedTrellisesForUpload` never inserts into shared plots (even if legacy DB
  row has `requires_staging = false`)
- Web trellis form does not offer the staging toggle when the target is a shared plot
- Existing staging test: Fire1 tags a photo → item goes to staging queue (not
  auto-approved) → Fire1 approves → Fire2 can decrypt thumbnail and detail view

## Completion notes

<!-- Agent appends here and moves file to tasks/done/ -->
