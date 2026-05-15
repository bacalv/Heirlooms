---
id: BUG-022
title: Web detail view shows blank image for shared plot items — full image DEK not decrypted with plot key
category: Bug Fix
priority: High
status: queued
depends_on: []
touches:
  - HeirloomsWeb/src/pages/PhotoDetailPage.jsx
  - HeirloomsWeb/src/crypto/vaultCrypto.js
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

<!-- Agent appends here and moves file to tasks/done/ -->
