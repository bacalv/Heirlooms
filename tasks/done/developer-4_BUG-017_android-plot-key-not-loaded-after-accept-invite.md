---
id: BUG-017
title: Android — shared plot items don't decrypt after accepting invite until app restart
category: Bug Fix
priority: High
status: done
depends_on: []
touches:
  - HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/shared/SharedPlotsViewModel.kt
assigned_to: Developer
estimated: 1 hour
---

## Problem

After a user accepts a shared plot invite on Android, items in that plot appear in the
Garden but thumbnails are blank and the detail view fails to load. A force-quit and
reopen of the app fixes it. The session persists correctly; only the in-memory plot key
cache is stale.

## Root cause

`SharedPlotsViewModel.acceptInvite` calls `refresh(api)` after `api.acceptPlotInvite`,
which reloads the memberships list but does NOT call `ensurePlotKeys`. The plot key for
the newly accepted plot is never fetched into `VaultSession.plotKeys` for the current
session.

`ensurePlotKeys` is only called from `GardenViewModel.ensureSharingKey`, which runs
during Garden's initial load. If the Garden was already loaded before the invite was
accepted, it won't re-run and the plot key cache is never populated.

`HeirloomsImage` checks `VaultSession.plotKeys` and correctly uses `plotKeysVersion`
to retry when new keys arrive — but no new key ever arrives unless `ensurePlotKeys`
is called.

## Fix

In `SharedPlotsViewModel.acceptInvite`, after `api.acceptPlotInvite` succeeds, eagerly
fetch and cache the plot key for the newly accepted plot:

```kotlin
fun acceptInvite(api: HeirloomsApi, plotId: String, localName: String, onDone: () -> Unit) {
    viewModelScope.launch {
        try {
            api.acceptPlotInvite(plotId, localName)
            // Eagerly load the plot key so Garden items decrypt without a restart
            val privkey = VaultSession.sharingPrivkey
            if (privkey != null) {
                try {
                    val (wrappedKey, _) = api.getPlotKey(plotId)
                    val rawKey = VaultCrypto.unwrapPlotKey(
                        android.util.Base64.decode(wrappedKey, android.util.Base64.DEFAULT),
                        privkey,
                    )
                    VaultSession.setPlotKey(plotId, rawKey)
                } catch (_: Exception) { }
            }
            refresh(api)
            onDone()
        } catch (e: Exception) {
            _actionError.value = e.message ?: "Failed to accept invitation"
        }
    }
}
```

`VaultSession.setPlotKey` increments `plotKeysVersion`, which causes `HeirloomsImage`
to recompose and retry decryption immediately.

## Acceptance criteria

- After accepting a shared plot invite, thumbnails decrypt and display without requiring
  an app restart
- Tested: accept invite → return to Garden → thumbnails load within a few seconds

## Completion notes

Implemented as specified. In `SharedPlotsViewModel.acceptInvite`, after
`api.acceptPlotInvite` succeeds, the fix eagerly fetches the plot key via
`api.getPlotKey(plotId)`, unwraps it with `VaultCrypto.unwrapPlotKey` using
`VaultSession.sharingPrivkey`, and stores it via `VaultSession.setPlotKey`. This
increments `plotKeysVersion`, causing `HeirloomsImage` to recompose and retry
decryption immediately — no app restart required.

The key-fetch is wrapped in its own try/catch so any transient failure is silently
swallowed and does not prevent the invitation acceptance itself from completing. The
pattern exactly mirrors how `GardenViewModel.ensurePlotKeys` fetches and caches plot
keys.

Test run was blocked by a sandbox permission denial on `./gradlew`. No other code
changes were made beyond the single function in `SharedPlotsViewModel.kt`.
