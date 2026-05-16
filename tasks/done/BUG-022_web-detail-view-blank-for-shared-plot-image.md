---
id: BUG-022
title: Web detail view shows blank image for shared plot items — full image DEK not decrypted with plot key
category: Bug Fix
priority: High
status: done
depends_on: []
touches:
  - HeirloomsWeb/src/pages/PhotoDetailPage.jsx
  - HeirloomsWeb/src/pages/GardenPage.jsx
assigned_to: Developer
estimated: 2 hours
---

## Problem

When a member (User B) views a shared plot item in the web detail page, the thumbnail
decrypts and displays correctly in the plot list, but clicking through to the detail
view shows a blank image. The full image is not displayed.

Discovered during v0.54 smoke test Step 4 (2026-05-15).

## Root cause

`UploadThumb.jsx` (thumbnail rendering) correctly handles `ALG_PLOT_AES256GCM_V1`
wrapped DEKs by looking up the plot key from session state. The web detail page
(`PhotoDetailPage.jsx`) decrypts the full image separately but likely only handles
`ALG_P256_ECDH_HKDF_V1` (sharing key) and master-key DEK formats — not the plot key
format used for shared plot items after staging approval.

Android works correctly because `PhotoDetailViewModel` already has the plot key loaded
in `VaultSession` and handles all three DEK formats.

## Fix

In `PhotoDetailPage.jsx` (or the relevant full-image fetch/decrypt function):

1. Check the item's `dekFormat` field
2. If `ALG_PLOT_AES256GCM_V1`: load the plot key (same way `UploadThumb` does) and
   use it to decrypt the full image
3. Ensure the plot key is loaded in the web session state before attempting decryption
   (mirror what `ensurePlotKeys` does on Android)

## Acceptance criteria

- User B (web) opens the detail view of a shared plot item uploaded by User A → full
  image displays correctly
- Thumbnail and full image both decrypt using the plot key
- Other DEK formats (master key, sharing key) still work correctly in the detail view

## Completion notes

### What was done

**`HeirloomsWeb/src/pages/PhotoDetailPage.jsx`**

- Added `loadPlotKey(plotId, apiKey)` helper — mirrors the same helper in `UploadThumb.jsx`.
  Checks the session cache first (`getPlotKey`), then fetches `/api/plots/:plotId/plot-key`,
  unwraps with the sharing private key, and stores in session (`setPlotKey`).
- Updated the module-level `unwrapDek` function to accept `plotId` and `apiKey` as additional
  parameters and handle the `ALG_PLOT_AES256GCM_V1` case by calling `loadPlotKey` then
  `unwrapDekWithPlotKey`.
- Added `plotId` extraction from `location.state?.plotId` (read-only, set at navigation time).
- Updated all three `unwrapDek` call sites (MSE video path, full-download path in
  `loadContent`, and `handleDownload`) to pass `plotId` and `apiKey`.
- Updated imports: added `getPlotKey`, `setPlotKey` from `vaultSession`; added
  `unwrapPlotKey`, `unwrapDekWithPlotKey`, `ALG_PLOT_AES256GCM_V1` from `vaultCrypto`.

**`HeirloomsWeb/src/pages/GardenPage.jsx`**

- Updated both navigation points in `PlotThumbCard` (the `<Link>` and the pencil button
  `navigate()` call) to include `plotId` in `location.state`. The `plotId` is already
  a prop of `PlotThumbCard` and is set to `plot.id` only for shared plots
  (`plot.visibility === 'shared'`), so private-plot and non-plot navigation is unaffected
  (`plotId` will be `undefined`, which becomes `null` in the detail page).

### Decisions

- The `plotId` is threaded via React Router `location.state` rather than added to the
  upload API response. This avoids a server change and mirrors how `UploadThumb` receives
  `plotId` as a prop from the list context.
- No changes to `vaultCrypto.js` were needed — all required functions already existed.
- The preview-clip decrypt path (for long videos) uses `unwrapDekWithMasterKey` directly
  for `wrappedPreviewDek` and was left unchanged — preview DEKs are always master-key
  wrapped per the current upload flow.

### Spawned tasks

None.
