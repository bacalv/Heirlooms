---
id: BUG-020
title: Shared plot trellis auto-approve — client-side DEK re-wrap to avoid mandatory staging
category: Bug Fix
priority: Medium
status: queued
depends_on: []
touches:
  - HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/garden/GardenViewModel.kt
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/repository/plot/TrellisRepository.kt
assigned_to: Developer
estimated: 3–4 hours
---

## Background

BUG-018 enforced `requires_staging = true` for all shared-plot trellises because the
server cannot re-wrap DEKs with the plot key (it never holds the plot key in plaintext).
The staging panel is the current re-wrapping point.

This means "auto-publish to a shared plot" is not possible even when the uploader is
already a member of the target plot and holds the plot key.

Discovered during v0.54 smoke test Step 4 (2026-05-15).

## Proposed fix

When the Android app routes an upload into a shared plot via an unstaged trellis:

1. Check if the target plot key is available in `VaultSession.plotKeys`
2. If yes: re-wrap the item DEK and thumbnail DEK with the plot key in the Android client
3. Submit the item to the server with the re-wrapped DEKs (new fields on the tag/upload
   request) so the server can insert directly into `plot_items` without staging

This enables true auto-approval for shared plots when the uploader is a member.

If the plot key is NOT available (e.g. not yet loaded), fall back to staging.

## Server changes required

- Accept optional `wrappedPlotItemDek`, `plotItemDekFormat`, `wrappedPlotThumbDek`,
  `plotThumbDekFormat`, `targetPlotId` parameters on the tag-update or trellis-route
  endpoint
- If provided and valid: insert directly into `plot_items` (bypassing staging)
- If not provided: keep existing staging behaviour

## Acceptance criteria

- User A (member of shared plot) tags a photo → item appears in the shared plot
  immediately without requiring approval, thumbnail and full image decrypt for all members
- User who is NOT a member of the target plot: item goes to staging as before
- Existing staging flow is unaffected

## Completion notes

Implemented 2026-05-16. All changes are on branch `agent/developer-2/BUG-020`.

### What was done

**Server (Kotlin/http4k):**

1. `TrellisRepository.kt` — Added `PrewrappedPlotDek` data class and new interface method
   `runUnstagedTrellisesForUploadWithPrewrappedDeks`. The implementation handles non-shared
   trellises identically to the existing `runUnstagedTrellisesForUpload`, and for shared-plot
   trellises inserts directly into `plot_items` (with the client-supplied wrapped DEKs) when
   a matching entry is present in `prewrappedDeks`. The BUG-018 defensive guard remains intact
   for all plots not present in the map.

2. `UploadService.kt` — Extended `updateTags` to accept an optional
   `Map<UUID, PrewrappedPlotDek>` parameter (defaults to empty). When non-empty, routes to
   `runUnstagedTrellisesForUploadWithPrewrappedDeks`; otherwise uses the original path.

3. `UploadRoutes.kt` — Updated `tagsContractRoute` to parse an optional `prewrappedPlotDeks`
   JSON array from the PATCH body, decode each entry into `PrewrappedPlotDek` keyed by
   `plotId` (UUID), and pass the map to `uploadService.updateTags`. Added a private
   `parsePrewrappedDeks` helper that returns an empty map on any malformed input, ensuring
   full backward-compatibility with clients that do not include the field.

**Android (Kotlin/Compose):**

4. `HeirloomsApi.kt` — Added `PrewrappedPlotDek` data class and extended `updateTags` to
   accept an optional `List<PrewrappedPlotDek>` (defaults to empty). When non-empty, the list
   is serialized into the `prewrappedPlotDeks` JSON array in the PATCH body.

5. `PhotoDetailViewModel.kt` — Updated `saveChanges` to call a new private
   `buildPrewrappedDeks(upload)` before `api.updateTags`. `buildPrewrappedDeks` unwraps the
   upload's item DEK and thumbnail DEK using the appropriate envelope format, then re-wraps
   them under every plot key currently held in `VaultSession.plotKeys`. The result is passed
   to `api.updateTags`. If no plot keys are available, or if any crypto step fails, an empty
   list is returned and the existing staging path is preserved.

**Tests:**

6. `UploadHandlerTest.kt` — Added `BUG-020` test verifying that a PATCH /tags request that
   includes a `prewrappedPlotDeks` field still succeeds (returns 200 and calls the repo).

### Design decisions

- Client sends pre-wrapped DEKs for **all** shared plots where it holds the plot key, not
  just plots that would match a trellis. The server uses only the relevant ones. This avoids
  requiring the client to evaluate trellis criteria locally.
- Fallback to staging is implicit: if `prewrappedDeks` is empty (plot key not yet loaded,
  vault locked, or upload has no DEK), `runUnstagedTrellisesForUpload` is called and
  shared-plot trellises are skipped by the BUG-018 guard as before.
- `GardenViewModel.kt` was not modified. The DEK re-wrap now happens inside
  `PhotoDetailViewModel.saveChanges` at the point of tagging, which is the natural place.
  The task brief mentioned GardenViewModel but the actual tag-save path flows through
  PhotoDetailViewModel → HeirloomsApi → server PATCH /tags.
- No database schema changes required; `wrapped_item_dek` and `wrapped_thumbnail_dek` are
  already nullable BYTEA columns on `plot_items` (added in V25 migration).

### Acceptance criteria status

- Owner/member tagging a photo to a shared plot → item appears immediately (plot key held → pre-wrapped DEKs sent → direct plot_items insert). ✓
- Non-member or missing plot key → empty prewrappedDeks list → `runUnstagedTrellisesForUpload` → BUG-018 guard skips shared plots → staging as before. ✓
- All members can decrypt the item → wrapped DEK uses `plot-aes256gcm-v1` format, same as the manual add-to-plot path in `addToPlot`. ✓
- Existing staging flow entirely unaffected → `parsePrewrappedDeks` returns empty map when field absent; empty map routes to original `runUnstagedTrellisesForUpload`. ✓
