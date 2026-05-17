# Android App — Package Restructure and Refactor Brief

**ID:** ARCH-012  
**Author:** Technical Architect  
**Date:** 2026-05-17  
**Status:** Approved for execution  

---

## Context

The Android app has grown to 76 Kotlin files and approximately 16,500 LOC. Several files
have become god-objects — classes that combine multiple distinct responsibilities and cannot
be meaningfully unit tested. The server underwent the same diagnosis in DONE-001 and the
eight-phase refactor delivered the biggest quality improvement of any initiative.

This brief applies that same discipline to the Android app. The driving motivation is
test coverage: files of 1,000+ lines cannot be usefully isolated in unit tests. Every
split in this plan must unlock concrete testable units.

**Constraint:** No behaviour changes. Each phase is pure restructuring — rename, move,
extract. If a change is required to make code testable, flag it as a separate task.

---

## 1. Proposed Package Structure

### 1.1 Current layout (problems highlighted)

```
digital.heirlooms/
  api/
    HeirloomsApi.kt          ← 1,047 lines — all HTTP calls in one class
    Models.kt                ← 152 lines — fine as-is
  app/
    DecryptingDataSource.kt
    EndpointStore.kt
    MainActivity.kt
    PairingQrParser.kt
    SettingsActivity.kt
    ShareActivity.kt
    UploadWorker.kt
    Uploader.kt              ← 1,028 lines — upload pipeline + thumbnail + preview + checkpoint
  crypto/
    DeviceKeyManager.kt
    VaultCrypto.kt
    VaultSession.kt
  ui/
    auth/
      BiometricGateScreen.kt
    brand/   (6 files — fine)
    capsules/ (7 files — fine)
    common/   (4 files — fine)
    explore/  (2 files — fine)
    flows/    (4 files)
    garden/
      GardenScreen.kt        ← 1,012 lines — garden + just-arrived + upload overlay
      GardenViewModel.kt     ← 420 lines — upload, poll, tag, trellis routing
      PhotoDetailScreen.kt   ← 1,167 lines — image + video + rotation + download
      PhotoDetailViewModel.kt← 491 lines — decryption + streaming + download + tag update
      ...
    main/
      AppNavigation.kt       ← 470 lines — all 20+ routes in one file
    shared/
      SharedPlotsScreen.kt   ← 582 lines — plot list + staging panel + invite modal
    social/  (3 files — fine)
```

### 1.2 Target layout

```
digital.heirlooms/
  api/
    HeirloomsApi.kt          ← retained as facade (thin delegator, see §1.3)
    Models.kt                ← unchanged
    auth/
      AuthApi.kt             ← login, register, challenge, logout, me, pairing, devices
      ContentApi.kt          ← uploads CRUD, tags, rotation, compost/restore, view tracking
      KeysApi.kt             ← passphrase backup, sharing keys, device registration
      PlotApi.kt             ← plot CRUD, membership, invites, bulk staging
      CapsuleApi.kt          ← capsule CRUD, seal, cancel
      TrellisApi.kt          ← trellis CRUD, staging approval/rejection
      SocialApi.kt           ← friends list, share-upload
      DiagnosticsApi.kt      ← settings fetch, diag event post
      ApiClient.kt           ← OkHttpClient, base URL, auth header, get/post/put/patch/delete
      JsonMappers.kt         ← all toUpload(), toPlot(), toCapsule*(), etc. extension fns
  app/
    DecryptingDataSource.kt  ← unchanged
    EndpointStore.kt         ← unchanged
    MainActivity.kt          ← unchanged
    PairingQrParser.kt       ← unchanged
    SettingsActivity.kt      ← unchanged
    ShareActivity.kt         ← unchanged
    UploadWorker.kt          ← unchanged
    upload/
      Uploader.kt            ← thin orchestrator only (§1.4)
      UploadSession.kt       ← UploadCheckpoint + GcsSessionStatus + checkpoint r/w
      UploadTransport.kt     ← GCS PUT, resumable PUT chunks, MultiByteArrayRequestBody,
                               ProgressRequestBody, ProgressFileRequestBody
      UploadEncryption.kt    ← DEK generation, encrypt-in-memory, encrypt-streaming pipeline
      ThumbnailGenerator.kt  ← generateThumbnail(), makeFallbackThumbnail(), applyExifRotation()
      PreviewClipGenerator.kt← generatePreviewClip(), extractFileDurationSeconds(),
                               fetchPreviewDurationSeconds()
  crypto/                    ← unchanged
  ui/
    auth/                    ← unchanged
    brand/                   ← unchanged
    capsules/                ← unchanged
    common/                  ← unchanged
    explore/                 ← unchanged
    garden/
      GardenScreen.kt        ← top-level composable + scaffold only (~150 lines)
      GardenJustArrivedSection.kt  ← the horizontal just-arrived strip + badges
      GardenPlotRow.kt       ← per-plot horizontal scroll row
      GardenUploadOverlay.kt ← PlantSheet + camera/file picker launchers + progress
      GardenViewModel.kt     ← retained, slimmed (upload trigger → UploadWorker only)
      GardenUploadViewModel.kt ← upload dispatch, staging count refresh, friend loading
      PhotoDetailScreen.kt   ← top-level composable + scaffold only (~120 lines)
      PhotoImagePanel.kt     ← decrypted image display + zoom/rotate gesture
      VideoPlayerPanel.kt    ← ExoPlayer lifecycle, DecryptingDataSource wiring
      PhotoMetadataSheet.kt  ← tags, date, location, capsule refs
      PhotoDetailViewModel.kt← decryption + tag/rotation staging + save (keep, slimmed)
      PhotoDownloadHelper.kt ← downloadFullFile() + shareToApp() extracted from VM
    main/
      AppNavigation.kt       ← retained as entry point, delegates to sub-graphs
      nav/
        GardenNavGraph.kt    ← garden + photo detail + compost routes
        CapsulesNavGraph.kt  ← capsules + create + photo picker routes
        SharedNavGraph.kt    ← shared plots + friends routes
        SettingsNavGraph.kt  ← settings + diagnostics + devices + pairing routes
        TrellisNavGraph.kt   ← trellises + staging + bulk staging routes
      BurgerPanel.kt         ← unchanged
      MainApp.kt             ← unchanged
      ...
    shared/
      SharedPlotsScreen.kt   ← top-level composable (~120 lines)
      SharedPlotCard.kt      ← individual plot card with status badges
      SharedPlotStagingPanel.kt ← staging queue section (approve/reject)
      SharedPlotInviteModal.kt  ← name-prompt dialog + invite generation
    social/                  ← unchanged
    trellises/               ← (already exists as ui/flows — rename is REF-001 scope)
```

### 1.3 API layer decomposition

`HeirloomsApi.kt` currently has three responsibilities that must be separated:

**Transport layer → `ApiClient.kt`**  
The `OkHttpClient`, `baseUrl`, `apiKey`, and the private `get()`/`post()`/`put()`/`patch()`/`delete()` helpers. All domain-specific classes take an `ApiClient` in their constructor. No domain logic here.

**JSON mapping → `JsonMappers.kt`**  
All the private `toUpload()`, `toPlot()`, `toPlotItem()`, `toCapsule*()`, `toTrellis()`, `toSharedMembership()` extension functions moved to a single internal file. These are pure functions with no I/O — ideal for unit testing in isolation.

**Domain sub-classes → `auth/`, `ContentApi`, etc.**  
Each takes `ApiClient` as its only constructor argument. Method signatures are identical to the current `HeirloomsApi` methods — callers do not change.

**Facade → retained `HeirloomsApi.kt`**  
`HeirloomsApi` stays as a constructor-compatible facade that wires the sub-classes together. All call sites (`LocalHeirloomsApi`, `AppNavigation`) continue to see `HeirloomsApi` and require no changes. This is Phase 1's key risk-mitigation strategy.

```kotlin
// After Phase 1 — HeirloomsApi is a thin facade
class HeirloomsApi(val baseUrl: String = BASE_URL, internal val apiKey: String) {
    internal val client = ApiClient(baseUrl, apiKey)
    private val auth = AuthApi(client)
    private val content = ContentApi(client)
    private val keys = KeysApi(client)
    private val plot = PlotApi(client)
    private val capsule = CapsuleApi(client)
    private val trellis = TrellisApi(client)
    private val social = SocialApi(client)
    private val diagnostics = DiagnosticsApi(client)

    // Each public function delegates to the appropriate sub-class
    suspend fun authLogin(...) = auth.login(...)
    suspend fun listUploadsPage(...) = content.listUploadsPage(...)
    // ... etc.
}
```

### 1.4 Upload pipeline decomposition

`Uploader.kt` currently mixes five distinct responsibilities:

| Responsibility | Target class |
|---|---|
| Orchestration (entry points: `upload`, `uploadViaSigned`, `uploadEncryptedViaSigned`) | `Uploader.kt` (retained, slimmed) |
| Checkpoint persist/read/delete, `GcsSessionStatus` sealed class | `UploadSession.kt` |
| GCS PUT, chunked PUT, `ProgressRequestBody`, `ProgressFileRequestBody`, `MultiByteArrayRequestBody`, `buildChunkNonce`, `buildChunkAad` | `UploadTransport.kt` |
| DEK generation, in-memory encrypt, streaming encrypt (`encryptAndUploadStreaming`) | `UploadEncryption.kt` |
| Thumbnail generation, EXIF rotation, fallback thumbnail | `ThumbnailGenerator.kt` |
| Preview clip extraction, duration extraction, preview settings fetch | `PreviewClipGenerator.kt` |

All non-public helpers that move out of `Uploader.kt` become `internal` classes in the
`digital.heirlooms.app.upload` package. `Uploader`'s public API does not change —
`UploadWorker` continues to call `Uploader.uploadEncryptedViaSigned()`.

The companion object constants (`CHUNK_SIZE`, `CIPHERTEXT_CHUNK_SIZE`, `LARGE_FILE_THRESHOLD`,
`NO_HTTP_CODE`, etc.) and pure static helpers (`sha256Hex`, `isRetryable`, `isValidEndpoint`,
`resolveMimeType`, `parseJson*`) move to `UploadTransport.kt` companion, as they are
transport/utility concerns.

### 1.5 Garden screen decomposition

`GardenScreen.kt` (1,012 lines) contains three overlapping sections:

| Section | Lines (approx) | Target |
|---|---|---|
| Upload flow — camera/file launchers, permission requests, `PlantState` FSM | ~250 | `GardenUploadOverlay.kt` |
| Just-arrived section — horizontal row with badge, auto-refresh | ~200 | `GardenJustArrivedSection.kt` |
| Per-plot row — horizontal thumbnail strip, long-press menu | ~200 | `GardenPlotRow.kt` |
| Top-level scaffold, pull-to-refresh, FAB | ~200 | `GardenScreen.kt` (retained) |
| Trellis-create dialog call site | ~50 | stays in `GardenScreen` |

`GardenViewModel.kt` (420 lines) mixes upload dispatch with garden data loading. The
upload-specific state (`ensureSharingKey`, `loadFriends`, `loadSharedMemberships`,
`refreshSharedStagingCounts`) moves to `GardenUploadViewModel`. Both VMs are
injected into `GardenScreen` via `viewModel()`.

### 1.6 PhotoDetail screen decomposition

`PhotoDetailScreen.kt` (1,167 lines):

| Section | Lines (approx) | Target |
|---|---|---|
| Video player setup — ExoPlayer, `DecryptingDataSource`, lifecycle | ~200 | `VideoPlayerPanel.kt` |
| Decrypted image display — bitmap display, zoom, download | ~150 | `PhotoImagePanel.kt` |
| Metadata panel — tags, date, capsule refs, sharing provenance | ~200 | `PhotoMetadataSheet.kt` |
| Top-level scaffold, top bar, overflow menu, rotation button | ~400 | `PhotoDetailScreen.kt` |
| `shareToApp()` helper (coroutine + Intent) | ~60 | `PhotoDownloadHelper.kt` |

`PhotoDetailViewModel.kt` (491 lines): the download and file-writing logic
(`downloadFullFile`, `shareToApp` coroutine bodies) moves to `PhotoDownloadHelper.kt`.
The VM retains: state, DEK management, tag staging, rotation staging, `saveChanges`.

### 1.7 SharedPlotsScreen decomposition

`SharedPlotsScreen.kt` (582 lines):

| Section | Target |
|---|---|
| `NamePromptDialog` (accept/rejoin) | `SharedPlotInviteModal.kt` |
| Per-plot card (status badge, role display, actions) | `SharedPlotCard.kt` |
| Staging approval/rejection panel | `SharedPlotStagingPanel.kt` |
| Top-level scaffold + FAB + pull-to-refresh | `SharedPlotsScreen.kt` (retained) |

### 1.8 AppNavigation decomposition

`AppNavigation.kt` (470 lines) splits into sub-graphs per feature area. `AppNavHost`
delegates to each sub-graph builder:

| Sub-graph | Routes covered |
|---|---|
| `GardenNavGraph.kt` | `GARDEN`, `PHOTO_DETAIL`, `COMPOST` |
| `CapsulesNavGraph.kt` | `CAPSULES`, `CAPSULE_DETAIL`, `CAPSULE_CREATE`, `PHOTO_PICKER` |
| `SharedNavGraph.kt` | `SHARED`, `FRIENDS` |
| `SettingsNavGraph.kt` | `SETTINGS`, `DIAGNOSTICS`, `DEVICES_ACCESS`, `PAIRING`, `UPLOAD_PROGRESS` |
| `TrellisNavGraph.kt` | `FLOWS`, `STAGING`, `PLOT_BULK_STAGING` |

`Routes` object, `Tab` enum, `HeirloomsBottomNav`, `MainNavigation`, and the
`navigateToTab`/`navigateFromBurger` extensions stay in `AppNavigation.kt`. The
`AppNavHost` function becomes a one-screen-per-sub-graph delegator.

Each sub-graph file is a top-level `fun NavGraphBuilder.<Feature>NavGraph(navController)`.

### 1.9 Core / common candidates

No Gradle module split. Items that could move to `ui/common/` if they are referenced
from three or more feature packages:

- `formatInstantDate`, `formatOffsetDate`, `daysUntilDeletion` → `ui/common/DateUtils.kt`
  (already exists — confirm all date helpers are there)
- `UploadThumbnail` composable → already in `ui/common/HeirloomsImage.kt`; verify
  `PhotoDetailScreen` imports it correctly after split

---

## 2. Phased Execution Plan

Each phase must:
- compile and pass all existing tests before merge
- introduce no behaviour changes
- include unit tests for every newly-isolated class

### Phase 1 — Split `HeirloomsApi.kt` (API layer)

**Estimated effort:** 1 day  
**Files touched:**
- `api/HeirloomsApi.kt` — gutted to a facade delegator
- `api/ApiClient.kt` — new (OkHttpClient, base URL, auth header, raw HTTP verbs)
- `api/JsonMappers.kt` — new (all `toXxx()` extension functions, internal visibility)
- `api/auth/AuthApi.kt` — new
- `api/ContentApi.kt` — new
- `api/KeysApi.kt` — new
- `api/PlotApi.kt` — new
- `api/CapsuleApi.kt` — new
- `api/TrellisApi.kt` — new
- `api/SocialApi.kt` — new
- `api/DiagnosticsApi.kt` — new

**Before:** `HeirloomsApi` is a 1,047-line monolith. All callers reference `HeirloomsApi`.

**After:** `HeirloomsApi` is a ~80-line facade. All callers still reference `HeirloomsApi`
unchanged. The 8 domain sub-classes each contain 80–180 lines.

**Risk:** Low. Pure move — no logic changes. The facade pattern guarantees all 50+
call sites (ViewModels, Activities) continue to compile without modification.

**Tests unlocked:**
- `JsonMappersTest` — unit test every `toXxx()` mapper with hand-crafted JSON strings.
  No HTTP, no mocks. Target: 100% mapper coverage.
- `AuthApiTest` — mock `ApiClient`, verify request paths, method, body format for each
  auth endpoint. Use `MockWebServer` or inline `OkHttpClient` test double.
- `ContentApiTest`, `PlotApiTest`, `TrellisApiTest`, `CapsuleApiTest` — same pattern.
- `ApiClientTest` — verify auth header injection, timeout configuration, error propagation.

**Tests to write as part of Phase 1:**
```
digital.heirlooms.api/
  JsonMappersTest.kt       (pure unit, no Android deps)
  ApiClientTest.kt         (MockWebServer)
  AuthApiTest.kt           (MockWebServer)
  ContentApiTest.kt        (MockWebServer)
  KeysApiTest.kt           (MockWebServer)
  PlotApiTest.kt           (MockWebServer)
```

---

### Phase 2 — Split `Uploader.kt` (upload pipeline)

**Estimated effort:** 1 day  
**Files touched:**
- `app/Uploader.kt` — retained as thin orchestrator (~150 lines)
- `app/upload/UploadSession.kt` — new
- `app/upload/UploadTransport.kt` — new
- `app/upload/UploadEncryption.kt` — new
- `app/upload/ThumbnailGenerator.kt` — new
- `app/upload/PreviewClipGenerator.kt` — new
- `app/UploadWorker.kt` — update import of `Uploader` (no logic change)

**Before:** `Uploader.kt` is a 1,028-line monolith with five distinct concerns.

**After:** `Uploader.kt` is ~150 lines. It constructs the supporting classes and
delegates. `UploadWorker` continues to call `Uploader.uploadEncryptedViaSigned()` unchanged.

**Risk:** Medium. The streaming encrypt path and checkpoint resume path have complex
control flow. Extraction must be done file-by-file with compilation checks between each
file. Suggested extraction order:
1. `ThumbnailGenerator` (no upload dependencies)
2. `PreviewClipGenerator` (no upload dependencies)
3. `UploadSession` (checkpoint I/O only — no HTTP)
4. `UploadTransport` (HTTP only — pass `OkHttpClient` in constructor)
5. `UploadEncryption` (crypto only — no HTTP, takes `UploadTransport` for GCS PUT)
6. Slim `Uploader` to orchestrator

**Compose state coupling:** None — `Uploader` is not a composable.

**Tests unlocked:**
- `ThumbnailGeneratorTest` — test fallback thumbnail, scaling logic (no Android
  `BitmapFactory`; use Robolectric or test-only in-memory bitmap).
- `UploadSessionTest` — test checkpoint write/read/delete round-trip with a temp file.
- `UploadTransportTest` — mock `OkHttpClient`; test `buildChunkNonce`, `buildChunkAad`,
  `computeTotalCiphertextSize`, `sha256Hex`.
- `UploadEncryptionTest` — test encrypt-then-decrypt round-trip for in-memory path and
  verify ciphertext structure. Existing `VaultCryptoTest` covers primitives; this tests
  the pipeline assembly.

**Tests to write as part of Phase 2:**
```
digital.heirlooms.app.upload/
  UploadSessionTest.kt
  UploadTransportTest.kt
  UploadEncryptionTest.kt
  ThumbnailGeneratorTest.kt
```

---

### Phase 3 — Split `GardenScreen.kt` and `GardenViewModel.kt`

**Estimated effort:** 1.5 days  
**Files touched:**
- `ui/garden/GardenScreen.kt` — retained, gutted to scaffold (~200 lines)
- `ui/garden/GardenJustArrivedSection.kt` — new composable
- `ui/garden/GardenPlotRow.kt` — new composable
- `ui/garden/GardenUploadOverlay.kt` — new composable (PlantSheet + launchers)
- `ui/garden/GardenViewModel.kt` — retained, slimmed (remove upload/friend state)
- `ui/garden/GardenUploadViewModel.kt` — new ViewModel

**Before:** `GardenScreen` is 1,012 lines with upload, just-arrived, and per-plot-row
logic mixed into one composable. `GardenViewModel` mixes data loading with upload
dispatch state.

**After:** `GardenScreen` is a scaffold composable (~200 lines) that composes the
three sections. Each section is an independently-composable function with its own
preview annotation.

**Risk:** High — Compose state coupling. Shared state (e.g. `plantState`, `PlantState`
FSM, `captureFile`, `captureUri`) flows from `GardenUploadOverlay` back up to
`GardenScreen`. Use the hoisted state pattern: `GardenScreen` owns these state variables
and passes them down as parameters. Do not use `rememberSaveable` in the sub-composables
for state that needs to be shared — hoist it.

The `rememberLauncherForActivityResult` calls in the upload flow **cannot** be moved to
a child composable if they are registered after the composable is attached to the
composition tree. Keep the launcher registrations in `GardenScreen` (the host) and pass
callbacks as lambdas to `GardenUploadOverlay`. This is the safe pattern for
`ActivityResultLauncher` in Compose.

**Tests unlocked:**
- `GardenViewModelTest` — test `load()`, `refresh()`, `refreshJustArrived()` in
  isolation with a mock `HeirloomsApi`. State transitions: Loading → Ready → Error.
  (Existing tests may already exist; extend them.)
- `GardenUploadViewModelTest` — test `ensureSharingKey()`, `loadFriends()`,
  `refreshSharedStagingCounts()` with mocked api. Verify state flows emit correctly.

**Tests to write as part of Phase 3:**
```
digital.heirlooms.ui.garden/
  GardenViewModelTest.kt
  GardenUploadViewModelTest.kt
```

---

### Phase 4 — Split `PhotoDetailScreen.kt` and `PhotoDetailViewModel.kt`

**Estimated effort:** 1.5 days  
**Files touched:**
- `ui/garden/PhotoDetailScreen.kt` — retained, scaffold (~150 lines)
- `ui/garden/PhotoImagePanel.kt` — new composable
- `ui/garden/VideoPlayerPanel.kt` — new composable
- `ui/garden/PhotoMetadataSheet.kt` — new composable
- `ui/garden/PhotoDetailViewModel.kt` — retained, slimmed (remove download logic)
- `ui/garden/PhotoDownloadHelper.kt` — new (plain Kotlin object, no ViewModel)

**Before:** `PhotoDetailScreen` is 1,167 lines mixing image viewer, video player,
ExoPlayer lifecycle, rotation, tags, download, and share-to-app.

**After:** `PhotoDetailScreen` is the scaffold and top bar only. The three panels are
composable functions that receive the state they need as parameters (no direct VM access
inside panels).

**Risk:** High — ExoPlayer lifecycle coupling. `VideoPlayerPanel` must own the
`ExoPlayer` instance and its `DisposableEffect` for release. The `DecryptingDataSource`
wiring (which requires the `contentDek` and `ApiKey`) must be passed into the panel as
a factory lambda or a `DataSource.Factory`. Extract this carefully: the ExoPlayer must
not outlive the composable.

Specific flag: `PhotoDetailScreen` conditionally renders either the image or video path
based on `upload.isVideo`. This conditional must stay in `PhotoDetailScreen` — do not
move it into either panel, as the panel selection is a routing decision.

`PhotoDownloadHelper` is a plain `object` (not a ViewModel) with suspend functions.
The VM calls these helpers rather than containing the logic inline.

**Tests unlocked:**
- `PhotoDetailViewModelTest` — test tag staging, rotation staging, `isDirty` combine,
  `saveChanges()` with mock api. (Test file already exists — extend it.)
- `PhotoDownloadHelperTest` — test file-writing logic with a temp directory. No Android
  `Context` needed if file paths are injectable.

**Tests to write as part of Phase 4:**
```
digital.heirlooms.ui.garden/
  PhotoDetailViewModelTest.kt   (extend existing)
  PhotoDownloadHelperTest.kt
```

---

### Phase 5 — Split `SharedPlotsScreen.kt`

**Estimated effort:** 0.5 days  
**Files touched:**
- `ui/shared/SharedPlotsScreen.kt` — retained, top-level scaffold (~150 lines)
- `ui/shared/SharedPlotCard.kt` — new composable
- `ui/shared/SharedPlotStagingPanel.kt` — new composable
- `ui/shared/SharedPlotInviteModal.kt` — new composable (was `NamePromptDialog`)

**Before:** `SharedPlotsScreen` is 582 lines combining the plot list, staging panel,
and invite modal in one file.

**After:** Each sub-component is a separately composable function. `SharedPlotsScreen`
composes them.

**Risk:** Low. The Compose state in `SharedPlotsScreen` is relatively straightforward —
most state lives in `SharedPlotsViewModel`. The sub-composables receive VM state as
parameters; they do not hold ViewModel references directly.

**Tests unlocked:**
- `SharedPlotsViewModelTest` — test `load()`, `acceptInvite()`, `rejectInvite()`,
  `createPlot()` with mock api. Verify state transitions.

**Tests to write as part of Phase 5:**
```
digital.heirlooms.ui.shared/
  SharedPlotsViewModelTest.kt
```

---

### Phase 6 — Split `AppNavigation.kt`

**Estimated effort:** 0.5 days  
**Files touched:**
- `ui/main/AppNavigation.kt` — retained as entry point + Routes + Tab + BottomNav
- `ui/main/nav/GardenNavGraph.kt` — new
- `ui/main/nav/CapsulesNavGraph.kt` — new
- `ui/main/nav/SharedNavGraph.kt` — new
- `ui/main/nav/SettingsNavGraph.kt` — new
- `ui/main/nav/TrellisNavGraph.kt` — new

**Before:** `AppNavHost` is a single 170-line `composable` call listing all 20+ routes.

**After:** `AppNavHost` delegates to five `NavGraphBuilder` extension functions, each
~30–50 lines. `AppNavigation.kt` drops to ~200 lines.

**Risk:** Low. Navigation routes are pure configuration — no runtime behaviour.
Each sub-graph file is a `fun NavGraphBuilder.<Feature>NavGraph(navController, ...)`.
The `Routes` object and route helper functions (`photoDetail`, `staging`, etc.) stay in
`AppNavigation.kt` so all sub-graphs share the same route strings.

**Tests unlocked:**
- Navigation tests are integration-level (Compose UI tests with `TestNavController`).
  Flag for a future test iteration rather than blocking this phase.

**Tests to write as part of Phase 6:**
- None that are strictly unit tests. Confirm existing integration navigation tests
  (if any) still pass.

---

### Phase 7 — Remaining files (if any)

After Phases 1–6, audit the full file list again. Any remaining files above 400 lines
that contain multiple distinct concerns should be flagged to the Developer for a Phase 7.

Candidates to re-examine:
- `GardenViewModel.kt` — if still above 300 lines after Phase 3, review for further split
- `ExploreScreen.kt` / `ExploreViewModel.kt` — currently under 400 lines each, monitor

---

## 3. Test Coverage Targets

| Phase | New classes | Tests to write | What they prove |
|---|---|---|---|
| 1 | `ApiClient`, `JsonMappers`, 7 domain APIs | `JsonMappersTest`, `ApiClientTest`, domain API tests | All JSON shapes parsed correctly; all API paths and request bodies correct |
| 2 | `UploadSession`, `UploadTransport`, `UploadEncryption`, `ThumbnailGenerator`, `PreviewClipGenerator` | `UploadSessionTest`, `UploadTransportTest`, `UploadEncryptionTest`, `ThumbnailGeneratorTest` | Checkpoint round-trip; chunk nonce/AAD correctness; encrypt-decrypt round-trip |
| 3 | `GardenUploadViewModel` | `GardenViewModelTest` (extended), `GardenUploadViewModelTest` | Upload dispatch state; staging count refresh; sharing key lazy provision |
| 4 | `PhotoDownloadHelper` | `PhotoDetailViewModelTest` (extended), `PhotoDownloadHelperTest` | Tag/rotation staging and save; download file-write correctness |
| 5 | `SharedPlotCard`, `SharedPlotStagingPanel`, `SharedPlotInviteModal` | `SharedPlotsViewModelTest` | Accept/reject/create plot state transitions |
| 6 | Nav sub-graphs | None (nav config is integration-level) | — |

**Baseline:** Existing tests must continue to pass after every phase. Run `./gradlew :app:testDebugUnitTest` before merge.

**Phase completion criterion:** A Developer agent may not close a phase until:
1. The new classes compile cleanly.
2. All pre-existing tests pass.
3. The tests listed for that phase are written and passing.

---

## 4. Constraints and Risks

### Hard constraints

- **No Gradle module splits.** Single `:app` module. Package/class decomposition only.
- **No behaviour changes.** Any change required to make code testable must be flagged
  as a separate task before proceeding.
- **Compile before commit.** Each phase must produce a green build before the branch is
  submitted for merge.
- **One phase per branch.** Each phase gets its own `agent/developer-N/ARCH-012-phN`
  branch. The PA merges phases sequentially.

### Risk register

| Risk | Severity | Mitigation |
|---|---|---|
| `ActivityResultLauncher` moved inside a child composable | High | Keep all launcher registrations in `GardenScreen`; pass callbacks as lambdas. See Phase 3 note. |
| ExoPlayer not released on composable dispose | High | `VideoPlayerPanel` owns the `DisposableEffect`; verify `player.release()` is called. See Phase 4 note. |
| `HeirloomsApi` facade misses a delegation call | Medium | Run the full existing test suite after Phase 1; any missing delegation causes a compile error. |
| `UploadEncryption` and `UploadTransport` circular dependency | Medium | `UploadEncryption` calls transport only through a callback / injected `PutFunction` type alias, not a direct import of `UploadTransport`. |
| Compose recomposition regression from hoisted state | Medium | Use snapshot testing or manual smoke-test on device before phase merge. |
| `JsonMappers.kt` extension functions on `JSONObject` — scoping | Low | Mark file-level with `@file:JvmName("JsonMappers")` and `internal` visibility so they are only accessible within the `api` package. |

### Files flagged as risky due to Compose state coupling

- **`GardenScreen.kt`** — `PlantState` FSM, camera/file launcher state, and `captureFile`/`captureUri` are tightly coupled. Hoist all to `GardenScreen`; pass as params.
- **`PhotoDetailScreen.kt`** — `ExoPlayer` creation inside `AndroidView` callback. Must remain inside the composable that owns the `DisposableEffect`.
- **`SharedPlotsScreen.kt`** — `showInviteModal`, `showStagingPanel` state variables must stay in the parent composable until VM state can replace them. Do not move to child composables without a ViewModel refactor.

---

## 5. Naming Conventions

Adopt these conventions across all phases. Developer agents must follow them exactly.

**Screen files:** `<Feature>Screen.kt` — one top-level `@Composable fun <Feature>Screen(...)`.

**Section composables:** `<Feature><Section>Section.kt` for distinct scrollable regions
within a screen. Example: `GardenJustArrivedSection`.

**Panel composables:** `<Feature><Content>Panel.kt` for self-contained visual panels
that own their own state display (but not their state). Example: `VideoPlayerPanel`.

**Sheet composables:** `<Feature><Content>Sheet.kt` for bottom sheets or modal overlays.
Example: `PhotoMetadataSheet`.

**Modal composables:** `<Feature><Action>Modal.kt` for dialogs / full-screen modals.
Example: `SharedPlotInviteModal`.

**ViewModel files:** `<Feature>ViewModel.kt`. If a screen needs two VMs, the second is
`<Feature><Concern>ViewModel.kt`. Example: `GardenUploadViewModel`.

**Helper objects:** `<Feature><Concern>Helper.kt` — plain Kotlin `object`, no VM, no
Android lifecycle. Example: `PhotoDownloadHelper`.

**Nav graph functions:** `fun NavGraphBuilder.<Feature>NavGraph(navController: NavController, ...)`.

---

## 6. Developer Agent Instructions

When dispatched on a phase:

1. Read this brief in full before writing any code.
2. Extract exactly the classes listed for your phase. Do not make changes outside the phase scope.
3. Confirm compilation after each file extraction (`./gradlew :app:compileDebugKotlin`).
4. Write the tests listed for your phase before submitting.
5. Run `./gradlew :app:testDebugUnitTest` and confirm all tests pass.
6. If you encounter a tight coupling that requires a behaviour change to resolve, stop and raise it to the PA — do not make the behaviour change unilaterally.
7. Commit with a message of the form: `refactor(ARCH-012-ph<N>): <short description>`.

---

## 7. Relationship to Other Tasks

| Task | Relationship |
|---|---|
| DONE-001 (server refactor) | Pattern model for this refactor |
| REF-001 (Flow → Trellis rename) | `ui/flows/` → `ui/trellises/` rename is REF-001 scope; Phase 3/6 must not rename this package |
| REF-002 (Tag → Label rename) | Label rename is out of scope for ARCH-012 |
| SEC-002 (coverage gate) | ARCH-012 phases unlock the coverage needed to reach the 90% gate |

---

*End of brief.*
