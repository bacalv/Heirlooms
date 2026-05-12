# SE Brief: M10 E4 — Android Adoption

**Date:** 12 May 2026
**Milestone:** M10 — Shared Plots
**Increment:** E4 of 4
**Type:** Android only.

---

## Goal

Bring all M10 features to Android. After E4, the Android app supports:
the full criteria/predicate system for plot creation, flow creation and management,
staging review, and shared plot browsing and contribution. The server and web changes
are complete (E1–E3); this increment is Android-only.

---

## Dependency

E4 requires E1–E3 to be deployed to production. The Android app should be built and
tested against the updated server API. Review E1, E2, and E3 briefs and the M10 brief
before starting.

---

## VaultSession + VaultCrypto changes

### `VaultCrypto.kt` — new functions

```kotlin
// Plot key generation (new shared plots)
fun generatePlotKey(): ByteArray  // 32 random bytes

// Wrap plot_key to a member's sharing pubkey (ECDH-HKDF-AES-GCM, same as item sharing)
fun wrapPlotKeyForMember(plotKeyBytes: ByteArray, memberSpkiBytes: ByteArray): WrappedKey

// Unwrap plot_key from own wrapped copy
fun unwrapPlotKey(wrappedPlotKey: WrappedKey, sharingPrivKey: PrivateKey): ByteArray

// Wrap/unwrap item DEK under plot key (plot-aes256gcm-v1)
fun wrapDekWithPlotKey(dekBytes: ByteArray, plotKeyBytes: ByteArray): WrappedKey
fun unwrapDekWithPlotKey(wrappedDek: WrappedKey, plotKeyBytes: ByteArray): ByteArray
```

The `WrappedKey` data class already exists (from M9). Extend `AlgorithmIds` in
`EnvelopeFormat.kt` to include `plot-aes256gcm-v1`.

### `VaultSession.kt` — plot key cache

Add a `plotKeys: MutableMap<String, ByteArray>` (plotId → raw key bytes) to
`VaultSession`. Populated lazily when a shared plot is first accessed. Cleared on
vault lock. This avoids re-fetching the plot key per item.

---

## HeirloomsApi changes

### Updated plot endpoints

- `createPlot(name: String, criteria: JsonObject?, showInGarden: Boolean, visibility: String, wrappedPlotKey: String?, plotKeyFormat: String?)` — updated to accept criteria JSON and visibility.
- `updatePlot(id: String, name: String, criteria: JsonObject?, showInGarden: Boolean)` — updated.
- `listPlots()` — updated response model: `Plot` gains `criteria: JsonObject?`, `showInGarden: Boolean`, `visibility: String`.

### New flow endpoints

```kotlin
data class Flow(
    val id: String,
    val name: String,
    val criteria: JsonObject,
    val targetPlotId: String,
    val requiresStaging: Boolean,
)

suspend fun listFlows(): List<Flow>
suspend fun createFlow(name: String, criteria: JsonObject, targetPlotId: String, requiresStaging: Boolean): Flow
suspend fun updateFlow(id: String, name: String, criteria: JsonObject, requiresStaging: Boolean)
suspend fun deleteFlow(id: String)
```

### New staging endpoints

```kotlin
data class StagingItem(val upload: Upload, val sourceFlowId: String?)

suspend fun getFlowStaging(flowId: String, cursor: String? = null): Page<StagingItem>
suspend fun getPlotStaging(plotId: String, cursor: String? = null): Page<StagingItem>
suspend fun getRejectedItems(plotId: String): List<StagingItem>
suspend fun approveItem(plotId: String, uploadId: String,
                        wrappedItemDek: String? = null, itemDekFormat: String? = null,
                        wrappedThumbnailDek: String? = null, thumbnailDekFormat: String? = null)
suspend fun rejectItem(plotId: String, uploadId: String)
suspend fun unrejectItem(plotId: String, uploadId: String)
```

### New shared plot endpoints

```kotlin
data class PlotKey(val wrappedPlotKey: String, val plotKeyFormat: String)
data class PlotMember(val userId: String, val displayName: String, val username: String, val role: String)
data class PlotItem(val id: String, val upload: Upload, val addedBy: String,
                    val wrappedItemDek: String?, val itemDekFormat: String?,
                    val wrappedThumbnailDek: String?, val thumbnailDekFormat: String?)

suspend fun getPlotKey(plotId: String): PlotKey
suspend fun listPlotMembers(plotId: String): List<PlotMember>
suspend fun addPlotMember(plotId: String, userId: String, wrappedPlotKey: String, plotKeyFormat: String)
suspend fun generatePlotInvite(plotId: String): PlotInvite
suspend fun listPlotItems(plotId: String, cursor: String? = null): Page<PlotItem>
suspend fun addPlotItem(plotId: String, uploadId: String,
                        wrappedItemDek: String, itemDekFormat: String,
                        wrappedThumbnailDek: String, thumbnailDekFormat: String)
suspend fun removePlotItem(plotId: String, uploadId: String)
```

---

## Updated models

`Plot` data class updated:
```kotlin
data class Plot(
    val id: String,
    val name: String,
    val criteria: JsonObject?,       // null = collection plot
    val showInGarden: Boolean,
    val visibility: String,          // "private", "shared", "public"
    val sortOrder: Int,
    val isSystemDefined: Boolean,
)
```

---

## New screens

### Criteria builder (`CriteriaBuilder.kt`)

Reusable composable that renders the filter UI and produces a criteria `JsonObject`.
Mirrors the web `CriteriaBuilder.jsx`. For E4, support:
- Tag multi-select (existing tag picker component).
- Media type toggle (All / Photos / Videos).
- Date taken from/to (date pickers).
- Has location toggle.
- Received items toggle.

Produces the same JSON expression format as the web. Validated client-side before
posting.

### Flow management (`FlowScreen.kt`)

Accessible from: Burger → Flows (new entry).

- List of flows: name + target plot name + staging item count badge.
- Create flow: bottom sheet with name field, criteria builder, target plot picker
  (shows only collection plots), staging toggle.
- Edit flow: same sheet pre-populated.
- Delete flow: confirmation dialog.

### Staging review (`StagingScreen.kt`)

Accessible from: a flow row (shows staging for that flow), or a collection/shared
plot header (shows all staging for that plot).

- Grid of staging thumbnails (same `HeirloomsImage` component, decrypt path as normal).
- Tap item → approve / reject actions.
- Rejected section: collapsed, expandable, each item has "Add after all" button.

### Shared plot screens

**Create shared plot:** addition to the new-plot flow. Name field + "Shared" toggle.
On creation: generate plot key, wrap to own sharing pubkey, post to server.

**Shared plot in Garden:** shared plots appear in the Garden plot rows alongside
private plots. A visual badge or icon distinguishes them. Item count from `plot_items`.
Tapping a plot row thumbnail navigates to a plot detail view.

**Plot detail (shared):** grid of items from `GET /api/plots/:id/items`. Decrypt
using plot key path. "Add item" button: opens a picker of own uploads not yet in
this plot; taps wrap the item DEK under the plot key and call `addPlotItem`.

**Manage members screen:** list of members with role labels. "Invite" button opens
friend picker (wraps plot key for selected friend on selection) or generates an
invite link via the two-step flow.

### HeirloomsImage / EncryptedThumbnail

Add a new decrypt path: when `thumbnailDekFormat == "plot-aes256gcm-v1"`, unwrap
using the cached plot key from `VaultSession.plotKeys`. If the plot key is not yet
cached, fetch it (`getPlotKey`) and cache before decrypting.

---

## Tests

### VaultCrypto unit tests (~6)

1. `generatePlotKey()` returns 32 bytes.
2. `wrapPlotKeyForMember` + `unwrapPlotKey` round-trip: unwrapped key equals original.
3. `wrapDekWithPlotKey` + `unwrapDekWithPlotKey` round-trip.
4. `plot-aes256gcm-v1` is a known algorithm ID (no exception from `AlgorithmIds`).
5. Cross-platform compatibility: wrap plot key on Android → wrap item DEK → verify the
   wrapped format is accepted by server validation (use a stub server in test).
6. Plot key cache in `VaultSession`: second access returns cached value without re-fetch.

### Integration / UI tests (~6)

7. Create private collection plot → appears in Garden as collection type.
8. Create flow targeting collection plot → flow appears in Flows screen.
9. Upload item matching flow criteria → appears in staging (flow staging screen).
10. Approve item → item appears in collection plot detail.
11. Create shared plot → `VaultSession.plotKeys` populated on first plot access.
12. Add item to shared plot → `wrapped_item_dek` sent in request body.

### Regression

13. All existing Android tests pass.
14. Garden loads correctly with mixed query plots and collection plots.
15. HeirloomsImage: existing `p256-ecdh-hkdf-aes256gcm-v1` decrypt path unaffected.

---

## What E4 does NOT include

- `near` atom in the Android criteria builder (still deferred).
- Plot key rotation / member removal UI.
- Public plot creation UI.
- Async invite key delivery for invite-link joins (same constraint as E3).

---

## Acceptance criteria

1. All Android tests pass.
2. Create a collection plot with a flow on Android — items matching flow criteria
   appear in staging on the Android staging screen.
3. Approve an item from Android staging — item appears in the plot on both Android
   and web.
4. Create a shared plot from Android. Invite Sadaar from the friends list.
   Sadaar's Android app shows the shared plot in her Garden.
5. Bret adds an item to the shared plot from Android. Sadaar can see and decrypt it.
6. `HeirloomsImage` loads and decrypts a shared plot thumbnail using the
   `plot-aes256gcm-v1` path without errors.

---

## Documentation updates

- `docs/PA_NOTES.md` — add any new gotchas surfaced during Android implementation
- `docs/VERSIONS.md` — entry when E4 ships (v0.50.0); marks M10 complete
- `docs/PROMPT_LOG.md` — standard entry
