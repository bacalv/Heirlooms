---
id: BUG-009
title: Staging approval fails if Garden hasn't been visited — sharing key not loaded
category: Bug Fix
priority: High
status: queued
depends_on: []
touches:
  - HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/flows/PlotBulkStagingViewModel.kt
  - HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/flows/StagingViewModel.kt
assigned_to: Developer
estimated: 1 hour
---

## Background

Discovered during TST-003 (2026-05-15). Approving a staged item in a shared plot
fails with "1 item(s) failed to approve" if the user navigated to the Shared screen
without first visiting the Garden in the same session.

## Root cause

`PlotBulkStagingViewModel` approval flow (line ~82) requires `VaultSession.sharingPrivkey`
to unwrap the plot key:

```kotlin
val plotKey = VaultSession.getPlotKey(plotId) ?: run {
    val (wrappedKey, _) = api.getPlotKey(plotId)
    val raw = VaultCrypto.unwrapPlotKey(
        Base64.decode(wrappedKey, Base64.NO_WRAP),
        VaultSession.sharingPrivkey ?: error("Sharing key not loaded"),
    )
    ...
}
```

`VaultSession.sharingPrivkey` is only populated by `GardenViewModel.ensureSharingKey()`,
which runs when `GardenScreen` enters composition. If the user opens the app and
navigates directly to Shared → staging approval without visiting Garden first,
`sharingPrivkey` is null and the approval throws.

## Fix

Move sharing key loading out of `GardenViewModel` into a shared singleton or load
it eagerly at vault unlock. Options:

**Option A (recommended):** Load the sharing key in `PlotBulkStagingViewModel.approveSelected()`
if not already present — same lazy-fetch pattern already used for plot keys:

```kotlin
if (VaultSession.sharingPrivkey == null) {
    val existing = api.getSharingKeyMe()
    if (existing != null) {
        val privkeyBytes = VaultCrypto.unwrapDekWithMasterKey(existing.wrappedPrivkey, VaultSession.masterKey)
        VaultSession.setSharingPrivkey(privkeyBytes)
    }
}
```

**Option B:** Load sharing key at vault unlock (in `DeviceKeyManager` or the unlock
flow) so it is always available for the session regardless of navigation order.

Option A is a targeted fix with minimal blast radius. Option B is more robust long-term.

## Acceptance criteria

- Approving a staged item succeeds whether or not the Garden has been visited first
- Tested by: fresh app open → Shared → Test share → approve → no error

## Completion notes

**Implemented 2026-05-15 — Option A (targeted lazy-fetch in approval functions)**

### Changes made

**`HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/flows/PlotBulkStagingViewModel.kt`**
- Added eager sharing-key fetch at the top of `approveSelected()` coroutine, before
  iterating items. If `VaultSession.sharingPrivkey` is null, calls `api.getSharingKeyMe()`;
  if a key exists on the server, unwraps and stores it via `VaultSession.setSharingPrivkey()`.

**`HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/flows/StagingViewModel.kt`**
- Same pattern added to `approve()`, guarded by `isSharedPlot` (no-op for private plots).

**`HeirloomsApp/app/src/test/kotlin/digital/heirlooms/ui/flows/PlotBulkStagingViewModelTest.kt`**
- Added BUG-009 regression test `approveSelected_fetches_sharing_key_when_not_cached`
  using MockWebServer. Verifies that the first outbound request when `sharingPrivkey` is
  null is `GET /api/keys/sharing/me`, proving the key is no longer silently skipped.

### Approach
Option A was chosen: targeted fix in each approval function with minimal blast radius.
Both `PlotBulkStagingViewModel` (bulk staging) and `StagingViewModel` (single-item staging)
were updated, as both had the same `error("Sharing key not loaded")` guard.

### Tests
Existing unit tests pass. New regression test covers the BUG-009 path.
Run: `./gradlew :app:testProdDebugUnitTest --no-daemon`
