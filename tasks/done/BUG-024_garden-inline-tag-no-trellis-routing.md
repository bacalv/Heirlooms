---
id: BUG-024
title: Garden inline tag edit doesn't trigger trellis routing — missing prewrappedDeks
category: Bug Fix
priority: Medium
status: queued
depends_on: []
touches:
  - HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/garden/GardenScreen.kt
  - HeirloomsWeb/src/pages/GardenPage.jsx
assigned_to: Developer
estimated: 2–3 hours
---

## Problem

Applying a trellis-matching tag to an item from the Garden inline editor (the quick
tag input on the garden row) does not trigger trellis routing. The item stays in Just
Arrived instead of routing to the target plot.

Tagging from the photo detail view works correctly because it calls
`api.updateTags(uploadId, tags, prewrappedDeks)` with DEK re-wrap data.

The garden inline editor calls `api.updateTags(uploadId, newTags)` with no
`prewrappedDeks`, so the server has no way to perform the DEK re-wrap needed for
shared-plot trellis routing. On web, the same problem exists.

Found during TST-010 (2026-05-16), Journeys 5 and 9.

## Fix

The garden inline tag editor needs to:
1. Load the upload's wrapped DEK
2. Build `prewrappedDeks` for all matched trellis target plots (same logic as
   `PhotoDetailViewModel.buildPrewrappedDeks`)
3. Include them in the `updateTags` call

This is more complex than it sounds — the inline editor needs access to the master
key and plot keys. Consider reusing the logic from `PhotoDetailViewModel`.

## Acceptance criteria

- Tagging an item from the garden row triggers trellis routing correctly
- Auto-approve trellises route immediately; staging trellises go to the queue
- Same fix applied to web garden inline tag input

## Completion notes

Fixed on branch `agent/developer-13/BUG-027` (same branch as BUG-027, both touch garden files).

### Android (`GardenScreen.kt`)

- Added a package-level `buildPrewrappedDeks(upload: Upload)` function that mirrors
  `PhotoDetailViewModel.buildPrewrappedDeks`. It reads `VaultSession.plotKeys`, unwraps
  the upload's content and thumbnail DEKs under the appropriate format, then re-wraps
  them under every cached plot key, returning a `List<HeirloomsApi.PrewrappedPlotDek>`.
- Changed `PlotRowSection`'s `onTagsUpdated` callback signature from
  `(uploadId, oldTags, newTags)` to `(upload: Upload, newTags)` so the outer
  `GardenScreen` scope receives the full `Upload` object needed by `buildPrewrappedDeks`.
- `GardenScreen`'s `onTagsUpdated` handler now calls `buildPrewrappedDeks(upload)` inside
  the coroutine launch and passes the result to `api.updateTags(upload.id, newTags, prewrappedDeks)`.
- Added imports: `android.util.Base64`, `digital.heirlooms.crypto.VaultCrypto`,
  `digital.heirlooms.crypto.VaultSession`.

### Web (`GardenPage.jsx`)

- Added a module-level async `buildPrewrappedDeks(upload, plots, apiKey)` function.
  It filters the garden plots for shared ones, unwraps the raw DEK once (honouring
  all three DEK formats), then re-wraps it under each plot key (cached or freshly
  fetched and cached on demand). Returns an array of `prewrappedPlotDeks` entries.
- `handleQuickUpdateTags` now calls `buildPrewrappedDeks` and includes the result in
  the PATCH body when non-empty.
- Added imports: `getPlotKey`, `setPlotKey` from `vaultSession`; `unwrapDekWithPlotKey`,
  `wrapDekWithPlotKey`, `unwrapWithSharingKey`, `unwrapPlotKey`, `ALG_P256_ECDH_HKDF_V1`,
  `ALG_PLOT_AES256GCM_V1` from `vaultCrypto`.

### Notes

- `buildPrewrappedDeks` is best-effort (all exceptions caught, returns empty list on
  any failure). This matches the existing behaviour in `PhotoDetailViewModel` — routing
  just won't happen if the vault has no plot keys, which is the same outcome as before.
- The one pre-existing test failure (`CreateFriendInviteTest`) is unrelated to this fix.
- Build confirmed: `./gradlew :app:testProdDebugUnitTest --no-daemon` passes (157/157 tests
  excluding the pre-existing failure).

Date completed: 2026-05-17
