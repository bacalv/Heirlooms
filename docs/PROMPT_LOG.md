# Heirlooms — Prompt Log

A record of the key decisions and prompts from the founding development session
(April 2026). Each entry captures the original intent, what was built, and any
important context or tradeoffs discovered along the way.

---

## Session — 2026-05-09 — Web: Just arrived fixes + thumbnail cache

Three bugs in the webapp's Garden / Just arrived view, mirroring issues previously
fixed in the Android app:

**30-second polling.** `GardenPage` had `plotRefreshKey` wired correctly but nothing
ever incremented it on a timer. Added a `setInterval` (30 000 ms) in a `useEffect`
that bumps the key; all plot rows silently re-fetch on each tick.

**Arrival animation.** `OliveBranchArrival` existed but was never used in the Garden.
Added `knownIdsRef` (a `useRef`, seeded on initial load) and `newlyArrivedIds` state
to `PlotItemsRow`. On each silent refresh, items not seen in the previous fetch are
added to `newlyArrivedIds`; each matching `PlotThumbCard` renders an `OliveBranchArrival`
overlay (88% parchment background, `withWordmark=false`, `pointer-events-none`) that
dismisses itself after the 3 s animation. Animation-only for the Just arrived system
plot — user-defined plot rows refresh silently without animation. `OliveBranchArrival`'s
`useEffect` now depends on `[]` (runs once on mount) with a `onCompleteRef` kept in
sync on every render, so per-tile inline callbacks no longer restart the animation.

**Thumbnail cache.** New `src/thumbCache.js`: module-level in-memory LRU Map (300
Blob entries, oldest evicted) backed by the browser's Cache API (500 entries,
`heirlooms-thumbs-v1`). `getThumb(uploadId, fetchUrl, apiKey)` returns a fresh
object URL on each call — callers own it and revoke it. Memory layer eliminates
re-fetches when navigating away and back within a session; Cache API layer survives
page reloads (equivalent to Coil's 50 MB disk cache on Android). Used in both
`PlotThumbCard` (GardenPage) and `Thumb` (PhotoGrid / Explore).

Deployed to Cloud Run (heirlooms-web).

---

## Session — 2026-05-09 — Web: Explore nav fix, video playback, video badge

**Explore → photo detail redirected via login screen.**
`ExploreGrid` used a plain `<a href="...">` instead of React Router's `<Link>`.
The native anchor caused a full page reload, wiping `useState(null)` for `apiKey`
in `App.jsx`. `RequireAuth` saw `null` and bounced to `/login`. Fixed by replacing
with `<Link to="..." state={{ upload }}>` (client-side nav, API key preserved;
router state passed for fast first paint in `PhotoDetailPage`). `ExploreThumb` was
also missing the `getThumb` cache — fixed in the same change.

**Video never loads in photo detail.**
Two bugs: (1) `displayUrl` used `thumbnailKey` if present — for videos this produced
a JPEG thumbnail blob, not playable video. (2) Downloading the full file as a blob
before setting `<video src>` means a large file fully buffers before anything plays.
Fix mirrors `QuickVideoModal` in `GardenPage`: try `/api/content/uploads/:id/url`
first (signed GCS URL — browser streams and seeks natively), fall back to a full
blob download only if that fails. Images keep the existing thumbnail path.

**Video indicator badge on Explore thumbnails.**
`ExploreThumb` now renders a small play-arrow badge (forest/75% alpha, bottom-right
corner, top-left rounded) when `mimeType` starts with `video/`. The parent `<Link>`
already has `relative overflow-hidden` so the badge clips cleanly to the tile
boundary. Consistent with the Garden tile video badges added in v0.25.9 on Android.

---

## Session — 2026-05-09 — v0.26.1: Explore filter tags use TagInputField

The Explore filter sheet's Tags section was still using `FilterChip` toggles
from v0.25.8. Replaced with `TagInputField` — now consistent with the Garden
quick-tag sheet and photo detail. `RecentTagsStore` used for suggestions.
`InputChip` import restored (used by the active-filter summary chips, not by
the Tags section that was replaced).

---

## Session — 2026-05-09 — v0.26.0: Unified TagInputField + staged photo detail edits

**New shared `TagInputField` composable** (`ui/common/TagInputField.kt`).
Chips for existing tags (× to remove) + inline `BasicTextField` + suggestion list
below. When input is empty: last 5 from `RecentTagsStore` (labelled "recent").
When typing: filtered from `availableTags`, falling back to `recentTags` when
`availableTags` is empty (share flow). Duplicate prevention; invalid-input error
line; `rememberSaveable` for text input. Replaces all previous per-screen tag UI.

**Staged changes in photo detail** (`PhotoDetailViewModel`).
`stageTags()` and `stageRotate()` buffer changes locally. `saveChanges()` is
`suspend` — patches tags and/or rotation then clears staged state. `isDirty:
StateFlow<Boolean>` derived via `combine()`. `BackHandler` in `PhotoDetailScreen`
intercepts both the system back gesture and the top-bar label; calls
`RecentTagsStore.record()` for newly added tags then `vm.saveChanges()` before
`onBack()`. Navigation waits for the save to complete.

**Rotation in photo detail — both flavours, images only.** `MediaArea` now takes
an explicit `rotation: Int` (effective = `stagedRotation ?: upload.rotation`).
`RotateRight` button added to `ExploreFlavour` (Garden already had it); hidden
when `upload.isVideo`. `PhotoDetailViewModel.availableTags` populated the same
way as Garden and Explore ViewModels.

**Garden quick-tag sheet.** `QuickTagDialog` (AlertDialog) replaced by
`QuickTagSheet` (ModalBottomSheet + `TagInputField`). Staged: dismiss commits if
tags changed. `onQuickTag` callback renamed `onTagsUpdated` with full old/new
list signature; `RecentTagsStore.record()` called for added tags.

**Share flow simplified.** `ReceiveState.Idle` loses `currentTagInput`. Four
IdleScreen callbacks (`onTagInputChanged`, `onTagCommit`, `onTagRemoved`,
`onRecentTagTapped`) replaced by one `onTagsChange: (List<String>) → Unit`.

**Test cleanup.** `tagInput_survives_savedstate_round_trip` removed (property
gone). `ShareViewModelTest` orphaned tests for `pendingWorkerId` /
`uploadPhotoCount` (removed from VM in an earlier session) cleaned up.

APK v0.26.0 (versionCode 31) installed on Samsung Galaxy A02s.

---

## Session — 2026-05-08 — v0.25.8–v0.25.10: Explore filter tags + video badges + garden tag button

**v0.25.10 — Visible tag button on Garden tiles.**
Long-press → dropdown → "Add tag…" existed but was invisible. Added a small
`Label` icon button (Forest 65% alpha, bottom-left corner, `RoundedCornerShape(topEnd=4.dp)`)
to every Garden tile. Placed in the outer `Box` after the inner `Box` (higher Z order),
so taps on it are consumed before reaching the inner Box's `pointerInput`/`onPhotoTap`.

`QuickTagDialog` upgraded: accepts `existingTags` and `availableTags`; shows a
`FlowRow` of `FilterChip`s for tags in the library that aren't already on the item —
tapping one immediately calls `onAdd(tag)` and closes the dialog. The text field
remains below for adding a new tag. Placeholder adapts ("e.g. family" vs "New tag…"
depending on whether suggestions are available).

`GardenViewModel` gains `availableTags: StateFlow<List<String>>`, populated by a
silent parallel coroutine in `load()`, same pattern as `ExploreViewModel`.

---

## Session — 2026-05-08 — v0.25.8–v0.25.9: Explore filter tags + video badges

**v0.25.9 — Video indicator badge on thumbnails.**
Both the Garden plot-row tiles and the Explore grid tiles now show a small
`PlayArrow` icon badge (14dp, Forest/65% alpha background, bottom-right corner,
`RoundedCornerShape(topStart=4.dp)`) whenever `upload.isVideo` is true.
`Upload.isVideo` was already defined in `Models.kt` as `mimeType.startsWith("video/")`.
`Icons.Filled.PlayArrow` is available via `material-icons-extended` (already a dep).
No model or API changes needed.

---

## Session — 2026-05-08 — v0.25.8: Explore filter tags

**Tags multi-select in Explore filter sheet.**
The Filters bottom sheet had no tag section — tag filters could only be applied
programmatically (e.g. via plot row title navigation) but not through the UI.

`ExploreViewModel` gains `availableTags: StateFlow<List<String>>` populated by a
silent parallel coroutine in `load()` (failure is swallowed — tags section simply
stays hidden). `FilterSheet` accepts `availableTags: List<String>` and renders a
"Tags" `FilterSection` as a `FlowRow` of `FilterChip`s between Sort and Capsule.
Selected tags fill Forest/Parchment; toggling adds/removes from `draft.tags`.
The section is omitted entirely when the tag list is empty, so new accounts see
no visual gap. The existing `draft.tags` → `filters.tags` → `listUploadsPage(tag=…)`
pipeline was already wired; no API layer changes needed.

---

## Session — 2026-05-08 — v0.25.4–v0.25.7: Android bugfix round (continued)

Hands-on device testing session on Samsung Galaxy A02s. All fixes driven by live observation.

**v0.25.4 — Share screen video thumbnail + upload progress jump.**
Share idle screen showed blank tiles for videos — `AsyncImage` used Coil's singleton
`ImageLoader` which had no `VideoFrameDecoder`. Added `coil-video:3.1.0`. `ShareActivity`
now creates a lightweight `ImageLoader` with `VideoFrameDecoder` and provides it via
`CompositionLocalProvider(LocalImageLoader)`. `IdleScreen`'s `AsyncImage` calls updated to
`imageLoader = LocalImageLoader.current`. Upload progress screen flashed "No uploads in
progress" before showing the active upload — `collectAsState(initial = emptyList())` triggered
`DoneState` (including a premature `pruneFinished()`) before WorkManager had any jobs.
Fixed by splitting the `when` branch: `allDone` → DoneState + prune; `files.isEmpty()` →
blank `Box` while the IO copy+enqueue is in flight; otherwise `InProgressState`.

**v0.25.5 — Just arrived scroll and animation reliability.**
Two bugs: (1) new item at index 0 appeared off-screen to the left because
`rememberLazyListState(initialFirstVisibleItemIndex=…)` is creation-only — Compose's
scroll-preservation kept existing items in place. Fixed: `shouldScrollToStart` parameter
on `PlotRowSection` calls `listState.scrollToItem(0)` via `LaunchedEffect`. (2) Arrival
animation was unreliable: `newItemsArrived` was a plain `var` on the ViewModel — not
observable by Compose — and was read during composition as a side effect (bad pattern).
Converted to `StateFlow<Boolean>`, collected via `collectAsStateWithLifecycle()`, triggered
from `LaunchedEffect(newItemsArrived)`.

**v0.25.6 — Arrival animation scoped to tile, not full screen.**
User observation: animation shouldn't cover the whole screen, just the item(s) that arrived.
Replaced full-screen `OliveBranchArrival` overlay with per-tile overlays. `GardenViewModel`
now exposes `newlyArrivedIds: StateFlow<Set<String>>` (the exact IDs from `genuinelyNew`).
`PlotRowSection` overlays `OliveBranchArrival` (clipped to tile shape, 88% parchment
background, `withWordmark = false`) on each matching thumbnail. `onComplete` clears the set.

**v0.25.7 — Video playback in photo detail.**
Two bugs: (1) ExoPlayer's default HTTP stack has no `X-Api-Key` header → 401 on every
request to `/api/content/uploads/{id}/file` → nothing played. Added
`media3-datasource-okhttp:1.4.1`; configured `ExoPlayer` with `OkHttpDataSource.Factory`
that injects the auth header. `HeirloomsApi.apiKey` promoted from `private` to `internal`.
(2) `PlayerView` had no height — `AndroidView` fell back to wrap_content, rendering a tiny
strip. Fixed with `aspectRatio(16f/9f)`. Added `player.playWhenReady = true`.

---

## Session — 2026-05-08 — v0.25.3: Upload progress clear-finished + auto-prune

Upload progress screen was accumulating completed and failed uploads indefinitely.
WorkManager keeps terminal-state `WorkInfo` records for ~7 days; `allUploadsFlow()`
queries all jobs tagged `heirloom_upload` without filtering, so old completed entries
reappeared in every new session's upload list.

Two fixes: a "Clear finished" `TextButton` appears inline next to the divider above the
file list whenever `session.files.any { it.isDone }` — tapping calls `workManager.pruneWork()`
which atomically removes all SUCCEEDED/FAILED/CANCELLED records. Auto-prune via `LaunchedEffect`
also fires when the screen naturally reaches the done state, so the cleanup happens silently
for the normal flow (user plants, uploads complete, screen shows "No uploads in progress").

Stuck ENQUEUED jobs: the per-file × cancel button (already present, since `isActive` includes
ENQUEUED) moves them to CANCELLED; "Clear finished" then removes them. No separate UI needed.

---

## Session — 2026-05-08 — v0.25.2: Android bug fixes (login, Just arrived, image cache)

Three post-D4 Android bugs fixed:

**Login screen appearing on Explore → photo detail navigation.**
Root cause: `MainApp.kt` used `remember { mutableStateOf(store.getApiKey()) }` for the
API key. On Activity recreation (rotation, or OS killing the process on the RAM-constrained
A02s and restoring via saved instance state), `remember` resets its value by re-running the
initialiser. The NavController back stack IS preserved (via `rememberNavController`'s built-in
Bundle save — hence "ends up in the right place" after re-entry) but `apiKey` reset. Fixed by
switching to `rememberSaveable`, which saves the value to the Activity's Bundle and survives
recreation. SharedPreferences remains the authoritative store; `rememberSaveable` is a
Belt-and-suspenders safety net. Also switched `SharedPreferenceStore.putString` from `apply()`
(async disk write) to `commit()` (synchronous) so the key is guaranteed on disk before the
caller returns — important on a device with historically full storage.

**Just arrived arrival animation not firing when row was empty.**
`GardenViewModel.refreshJustArrived()` guarded `newItemsArrived = true` with
`knownJustArrivedIds.isNotEmpty() && genuinelyNew.isNotEmpty()`. If Just arrived had no items
(knownJustArrivedIds was empty) and the first item arrived, the check short-circuited and the
animation never fired. Removed the `isNotEmpty()` guard — `genuinelyNew.isNotEmpty()` alone is
sufficient. The poll only fires after a 30s delay, so `load()` has always run and set
`knownJustArrivedIds` before the first poll.

**Thumbnail caching (disk cache).**
The `ImageLoader` in `AppNavigation.kt` was configured without a disk cache, so thumbnails
had to be re-fetched from the network on every app restart. Added a Coil `DiskCache` (50 MB,
`context.cacheDir/image_cache`) and an explicit `MemoryCache` (20% of available heap).
Thumbnails use stable non-signed URLs (`/api/content/uploads/:id/thumb`) with auth via
`X-Api-Key` header, so cache keys are stable across sessions.

---

## Session — 2026-05-08 (post-D4 brainstorm + doc sweep)

A brainstorming session held after Android D4 shipped, before the M7 brief
was drafted. Output: a doc sweep across IDEAS.md, IDIOMS.md, ROADMAP.md,
PA_NOTES.md, and PROMPT_LOG.md, all applied in a single commit.

### Topics explored

1. **iOS strategy.** Concluded with the *minimal iOS app* shape (Option 4
   in the IDEAS.md entry): web is the primary surface as a PWA; iOS app
   exists only to register a Share Extension, with a three-screen host app
   wrapping it. Android stays as-is through M6/M7. Decision recorded but
   not committed; this is post-M7 work.

2. **Gamification.** Considered and rejected. Trophies, ranks, persona,
   levels, XP, streaks — all rejected as fundamentally incompatible with
   the brand register. The *gardener's notebook* / *year in the garden*
   surface recorded as a possible alternative direction for the underlying
   feature-discovery problem.

3. **Tokens and monetisation.** Tokens as in-app currency rejected, with
   the per-currency pricing complexity accepted as the cost. Pricing tiers
   to be named in brand voice (illustrative: *steward* subscription, *long
   capsules* one-time fees) and tied to real underlying costs. Gift
   mechanic recorded as separate future feature.

4. **Trust posture and encryption.** Sealed-from-host recorded as the
   *eventual default*, not a paid feature. Engineering realities flagged
   (key management, no server-side processing, capsule delivery
   complications). M12+ horizon; recorded now to shape near-term
   architectural decisions.

5. **Third-party delivery integrations.** Recorded as M8+ second wave;
   email delivery flagged as the actual M8 baseline. Print-on-delivery
   (Moonpig, etc.) as the most brand-aligned integration but with a high
   reliability bar.

6. **Engagement without gamification.** Recorded as a feature space
   distinct from the gamification rejection. *The garden remembers you*,
   not the other way around. Existing schema columns (`uploaded_at`,
   `unlock_at`, `last_viewed_at`) flagged as the engineering hooks.

7. **The death/life recalibration.** The most significant outcome of the
   session. The PA had been calibrating the brand register toward
   *grief-product*, which is narrower than the product actually is.
   Heirlooms is about *time* — the gap between now and a future moment when
   something will mean more. Death is one shape that gap takes; most
   capsules will not involve death at all. The brand voice stays solemn
   and dignified; the *content* of what users put into the product is up
   to them, including humour and lightness. New IDEAS.md entry *Heirlooms
   is about time, not just death* captures the recalibration; new
   IDIOMS.md sub-section *The voice is solemn; the room belongs to the
   user* captures the discipline.

### The four-catch pattern

A meta-observation worth recording: this is the fourth time in recent
sessions that Bret has corrected a PA instinct that pulls the brand in a
particular direction. The pattern in chronological order:

1. *Didn't take* (v0.20.3) — the PA had introduced a brand verb for
   errors. Bret pointed out it was opaque to first-time readers; the
   brand-vocabulary-for-errors decision was reversed.
2. *Plant a seed for someone* (v0.20.3) — original capsule create form
   opening line. Bret caught the unwanted reproductive composition before
   it shipped.
3. *Productivity-app shelf-layout instinct* (Milestone 6 planning) — the
   PA initially proposed an inbox/recent/tag shelf layout for Garden,
   importing the *getting-behind/zero-as-achievement* register. Bret
   reframed to plots-and-explore.
4. *Over-solemnification of the brand register* (this session) — the PA
   had been treating Heirlooms as a grief product. Bret reframed to *time,
   not just death*.

The pattern is real: the PA's productive-by-default instinct extends
metaphors, surfaces, and emotional registers; Bret's discipline restrains
them. Both directions of restraint are now visible — *don't make it fun*
(catches 1 and 2) and *don't make it grim* (catch 4) — and they're sibling
disciplines, not opposing ones. The brand voice is dignified; the user's
use of it is theirs. Captured in IDIOMS.md.

### Roadmap renumbering

Decided in this session: M7 = multi-user, M8 = milestone delivery (the
inverse of the prior renumbering recorded on 2026-05-10). Reasoning:
delivery should be designed against real recipient accounts, which means
multi-user has to land first. Friend-tester onboarding sequencing also
only makes sense under this numbering. ROADMAP.md, PA_NOTES.md, and the
relevant IDEAS.md entries updated accordingly.

### Output artefacts

- `docs/IDEAS.md` — eight new entries appended; three existing entries
  edited for renumbering.
- `docs/IDIOMS.md` — one new sub-section.
- `docs/ROADMAP.md` — *Origin* section augmented; M7 and M8 sections
  rewritten.
- `docs/PA_NOTES.md` — three small edits per renumbering.
- `docs/PROMPT_LOG.md` — this entry.

No code changes.

---

## Session — 2026-05-08 (evening) — v0.25.0/v0.25.1: M6 D4 Android adoption + post-ship fixes

**D4 brief delivered and executed.** Closes Milestone 6. Android picks up the
Garden/Explore restructure that shipped on web in D2/D3. After D4, Android and
web are surface-equivalent for browsing, filtering, and photo detail.

**What shipped in v0.25.0 (D4):**

- **4E — ViewModel + SavedStateHandle migration.** All seven screens (Garden,
  Explore, Photo detail, Capsules, Capsule detail, Compost heap, ShareActivity)
  now have ViewModels. State that should survive configuration changes lives in
  the ViewModel; state that should survive process death goes through
  SavedStateHandle. `android:configChanges` removed from ShareActivity —
  rotation mid-upload now works because the ViewModel holds upload state and
  re-observes the WorkManager job on recreation.

- **4A — Four-tab bottom nav.** Garden | Explore | Capsules | Burger. Burger
  opens a `ModalBottomSheet` (chosen over a right-side slide panel — more
  natural Android idiom) containing Settings and Compost heap. Compost heap
  migrated from the Garden footer and Settings screen to Burger as the sole
  entry point.

- **4C — Explore tab.** Mirrors web `/explore`. Filter chrome always collapses
  to a "Filters" button + bottom sheet (all phone viewports are narrow). Tags,
  date range, capsule/location segmented controls, composted toggle, sort. 4-column
  thumbnail grid with cursor-paginated "Load more".

- **4B — Garden plot rows.** 2-column grid replaced with horizontal plot rows.
  Just arrived fixed at top. User plots in sort_order. Interactive row titles
  (chevron → Explore with plot tags pre-applied). "Load more" chip at end of
  partial rows; "See all in Explore →" tile at end of exhausted rows. Long-press
  thumbnail → DropdownMenu with Rotate 90° (immediate PATCH) and Add tag
  (AlertDialog).

- **4D — Photo detail flavours.** `?from=garden|explore|compost`. Garden:
  action-forward with rotate button, inline tag editor, Add to capsule, Compost
  below divider. Explore: content-forward, tags read-only, Compost in overflow.
  Compost: faded image, countdown, Restore. Back label is context-aware.
  Every photo detail open fires `POST .../view` once (tracked in ViewModel).

- **API layer.** Upload model gains `capturedAt`, `latitude`, `longitude`,
  `lastViewedAt`. Plot model added. `listUploadsPage()` replaces `listUploads()`
  with full filter/cursor/sort support. `listPlots()`, `listTags()`,
  `rotateUpload()`, `trackView()` added. ExoPlayer (media3:1.4.1) for inline
  video playback.

- **Tests.** 14 new automated tests: ExploreViewModel filter persistence via
  SavedStateHandle, PhotoDetailViewModel trackView fires exactly once and hits
  the correct endpoint, ShareViewModel state survives process death, Compose UI
  tests for Garden/Explore photo detail flavour split.

**Deployment.** HeirloomsServer and HeirloomsWeb unchanged for D4. Android APK
built with `./gradlew assembleDebug` and sideloaded via `adb install` to
Samsung Galaxy A02s (R9HR102XT8J). Device had ~512 KB free internal storage
at first install attempt — had to uninstall old APK first to free space. APK
is ~21 MB debug build.

**Post-ship fixes shipped same session as v0.25.1:**

*API response key mismatch (immediate deploy fix).* The live server returns
`"items"` not `"uploads"` for paginated upload lists, `"items"` not `"uploads"`
for composted uploads, a plain JSON array for `/api/plots` (not `{"plots":[...]}`),
and a plain JSON array for `/api/content/uploads/tags`. All four corrected in
`HeirloomsApi.kt`. Root cause: keys were assumed from code reading; not verified
against the live server before shipping D4.

*async/launch exception propagation.* `GardenViewModel.load()` used `async { }`
inside `launch { }`. When an `async` child fails, it cancels the parent
`StandaloneCoroutine` and the exception propagates past the `try/catch`, crashing
the app. Fixed by wrapping the parallel fetches in `coroutineScope { }` so child
exceptions are re-thrown at the `coroutineScope` boundary and caught correctly.

*Upload progress screen (new feature, v0.25.1).* The share-sheet uploading screen
replaced. Key decisions:
- **One WorkManager job per file** (was one batch for all): enables per-file
  cancellation and byte-level progress via `setProgressAsync()`.
- **Progress callback changed** from `(Int)` percent to `(Long, Long)` bytes so
  multiple workers aggregate into an overall %.
- **ShareActivity.enqueueUploads() is now fully async** (`lifecycleScope.launch`):
  progress screen appears immediately on Plant; no more ANR on 12-video batches
  where the old `runBlocking` blocked the main thread copying large files.
  Files are copied and workers enqueued sequentially (one at a time) to avoid
  holding multiple large temp files in memory simultaneously on the A02s.
- **Unified job list**: progress screen observes all `heirloom_upload`-tagged
  jobs, not just the current session's batch.
- **Retry**: 3 attempts with 30s exponential backoff before marking failed.
- **Burger entry** appears dynamically (WorkManager LiveData) only while uploads
  are active.

*System plot rogue row.* `listPlots()` returns all plots including the system
`__just_arrived__` plot. `GardenViewModel` was passing it through to user-plot
rows, which rendered it with raw name `__just_arrived__` and no filter (empty
`tag_criteria` → fetches all uploads). Fixed by adding `isSystemDefined` to the
`Plot` model and filtering it out before building user-plot rows.

*Garden staleness.* `LaunchedEffect(Unit) { if (Loading) vm.load() }` only ran
on first composition. If the ViewModel was retained across navigation (which it
is), data stayed stale. Fixed with `refresh()` (silent background re-fetch that
replaces Ready state without a loading flash) called on every composition.

*Just arrived scroll drift.* Android saves row scroll positions in the ViewModel.
After refresh, the row started at the saved offset, making the visible items
differ from the web (which always starts at the beginning). Fixed: scroll
position for `__just_arrived__` resets to 0 whenever new data arrives.

*30-second Just arrived poll.* Lightweight `refreshJustArrived()` fetches only
that row every 30 seconds. Detects genuinely new arrivals by comparing against a
known-ID set seeded on first load. Triggers `OliveBranchArrival` animation as a
semi-transparent overlay when new items land. Note for future: replace with
Server-Sent Events (see IDEAS.md) — polling is the interim approach, the
detection/animation logic stays in place.

**Things that tripped us up (don't repeat):**

- The live server's upload list endpoint returns `"items"` not `"uploads"`.
  Always verify actual HTTP responses before assuming field names from code.
- `async { }` inside `launch { }` without `coroutineScope { }` wrapping: child
  exceptions cancel the parent coroutine and bypass the outer `try/catch`,
  producing a FATAL crash instead of an error state.
- `runBlocking` on the Android main thread for large file copies = ANR. Always
  use `lifecycleScope.launch(Dispatchers.IO)` for any IO on the main thread.
- The A02s has ~201 MB heap and was nearly out of storage — both affect
  stability. Copy large files sequentially, not in parallel.
- WorkManager's `getWorkInfosByTagFlow()` (from `work-runtime-ktx`) is correct
  for Compose; `getWorkInfosByTagLiveData()` needs `lifecycle-livedata-compose`
  (not in deps) for `observeAsState`. Use Flow API throughout.
- `async { }` in Kotlin does not expose `inputData` on `WorkInfo` — that is a
  worker-side concept. Use progress data (`setProgressAsync`) for the ViewModel
  to observe state from running workers.

---

## Session — 2026-05-10 (Milestone 6 — deliverable restructure + tools addition)

A follow-up to the prior Milestone 6 planning session. The body of work is the
same; the structure has been reframed.

**From phases to deliverables.** The original 4-phase × 3-increment decomposition
(12 small briefs across backend / web / Android streams) reframed as 4
deliverables shipping one per session, each ~1 day of focused work:

- D1 — Tools (re-import utility)
- D2 — Backend + Explore basic
- D3 — Web complete
- D4 — Android adoption

The phase-and-stream view is still useful as a "what's in scope" map, but it
was a misleading execution plan given the founder + AI working pattern. Each
deliverable bundles work that previously sat across multiple phases:

- D2 = Phase 1 entirely (1A EXIF, 1B pagination, 1C plot schema) plus 2A (basic
  /explore route).
- D3 = 2B/2C (filters, photo detail) plus all of Phase 3 (Garden plots, plot
  management).
- D4 = Phase 4 unchanged.

The decomposition is preserved as sub-tasks within each deliverable's brief.

**Tools added as D1.** A re-import utility — standalone script that scans the
configured GCS bucket, computes SHA256 of each object, and INSERTs `uploads`
rows pointing at existing GCS keys. Originally suggested as a deferred / backlog
item, promoted to a first-class deliverable on Bret's direction. Justification:
D1 first acts as a safety net for the rest of M6 — with the re-import tool in
place, subsequent deliverables can experiment freely with the schema and data,
knowing recovery is one script away.

**Velocity finding (perceived).** The project to date — roughly nine days from
end of April through 8 May, with substantial backend / web / Android / brand
work shipped — would plausibly take a solo founder working evenings and
weekends about a year. Perceived multiplier: roughly 40×. Recorded in the deck
(slide 12: *Velocity*) as an observation, not a benchmarked claim. The
multiplier is specific to this product, this working pattern, and this team —
founder owning context across sessions, tight scoping discipline,
ship-then-polish cadence, AI doing the typing while the founder steers. None
of those factors is AI alone; the multiplier wouldn't transfer to a context
where any is missing.

**Operational constraints recorded.** A new section in PA_NOTES.md (*Pre-launch
operational constraints*) captures three durable rules:

1. Data destruction during M6 is acceptable — destructive schema changes can
   simplify implementation.
2. GCS objects are never auto-deleted outside the user-initiated compost flow.
3. DB backup is deferred but on the backlog (a small `pg_dump` to GCS on a
   schedule).

**Pending before D1 brief is drafted.**

1. Where does the re-import script live? (`scripts/`, `tools/`, or part of the
   server module?)
2. How does it run? (Gradle task, standalone jar, bash wrapper?)
3. Does it run locally pointing at remote DB and GCS, or on the Cloud Run
   instance?
4. One-shot recovery tool, or reusable for periodic drift-detection?
5. Does the v0.20.0 compost flow currently auto-purge GCS objects after the
   90-day window? The D1 brief needs to know whether composted GCS keys should
   be re-imported (no — they were intentionally removed) or whether they're
   already absent from the bucket (no GCS object to re-import).

Plus the four open questions still pending from the prior planning session
(plot configuration scope, vocabulary doc timing, plot criteria language,
v0.21.0 sequencing).

**Output artefacts updated.** `docs/presentations/Garden_Explore_Plan.pptx` now
13 slides. Slide 5 (*Plan at a glance*) shows 4 deliverables. Slides 6-9 are
deliverable detail slides (D1 Tools, D2 Backend + Explore basic, D3 Web
complete, D4 Android adoption). Slide 10 (*Sequence and dependencies*) replaces
the prior Gantt with a simpler view: D1 standalone with annotation,
D2 → D3 → D4 sequential. Slide 12 (*Velocity*) updated to four sessions
matching the deliverables.

---

## Session — 2026-05-10 (Milestone 6 — Garden / Explore restructure planning)

A scoping session before any code is written. The session settled the structural shape
of a new milestone — *Milestone 6, Garden / Explore restructure* — that's been inserted
before the originally-planned Milestone 6 (delivery, now Milestone 7). The decisions
came out of a working observation that the Garden tab today is doing two jobs at once:
helping the user *do work* (tag, process, compost incoming items) and helping the user
*explore memories* (browse what's been kept). Loading everything every time is slow.
The two jobs have different emotional registers and should live in different surfaces.

**The split.** Garden becomes the work surface — a vertically-scrolling stack of
horizontal Netflix-style "plots". The mandatory top plot is *Just arrived* — items
the user hasn't yet acted on (tag, encapsulate, compost). Below sit user-defined
plots (tag-based criteria) so advanced users can pin a "Family" plot, a "2026" plot,
etc. Explore becomes the leisure surface — paginated grid, full filter set (tag,
date range, capsule membership, composted toggle, location-boolean), photo-detail
emphasising content presentation rather than action affordances.

**Vocabulary settled.**
- *Just arrived* — the noun-phrase for items received but not yet acted on. Replaces
  *Untended* (which carried unintended productivity-app *neglect* connotations).
- *Plot* — the noun for a section of the Garden tab. *"Add a plot"* affordance.
  Garden contains plots. Verb forms (*plotted*, *plotting*) deliberately stay out of
  user-facing copy; the noun carries the metaphor.
- The unifier noun for content stays *items* — the brand has no canonical user-facing
  noun for "the unit of content". Considered and rejected: *seed* (reproductive
  connotations when paired with *plant*), *leaf* (mixed verb usage with *shelved*),
  *keepsake* / *treasure* / *plot itself*. The brand voice lives in the verbs (plant,
  seal, bloom, compost), not in the noun for the unit of content. This matches the
  v0.20.3 brand-restraint discipline.
- A new design principle: *negative-action button separation* — destructive actions
  (compost, cancel, restore from compost) live in a different visual region from
  positive actions (start a capsule, seal, add to capsule). To be added to BRAND.md
  during the v0.20.3 vocabulary cleanup or Phase 1 of Milestone 6, whichever lands
  first.

**Phased plan.** Four phases, three streams (backend / web / Android), twelve
incremental briefs.

- **Phase 1: Backend foundations.** Three increments — EXIF extraction (background
  job populating taken-date and location lat/lng on uploads), pagination on list
  endpoints (cursor-based, backwards-compatible), plot schema and endpoints. No UI
  changes; v0.21.0 Android ships unaffected during this phase.
- **Phase 2: Web Explore tab.** Three increments — basic /explore route with
  paginated grid, filter elaboration (date range, capsule membership, composted,
  location-boolean), photo detail variant emphasising content. Built first because
  it's mostly additive; Garden's existing behaviour unchanged during this phase.
- **Phase 3: Web Garden plots.** Three increments — *Just arrived* plot, user plot
  management with "Add a plot" affordance, photo detail variant emphasising actions.
  Riskier; done after Explore so users have a fallback during the Garden transition.
- **Phase 4: Android adoption.** Three increments — Android Explore tab, Android
  Garden plots, bottom-nav restructure (burger menu replacing v0.21.0's three-tab
  Settings entry).

**Sequencing.** v0.21.0 Android (currently in flight) ships first as scoped, then
this milestone begins. v0.21.0's bottom-nav structure changes during Phase 4 — users
see two nav structures in close succession. Acceptable.

**Timescales (rough estimates, ranges not commitments).**
- One engineer, sequential: 5–7 weeks.
- Two engineers, parallel where dependencies allow: 3–5 weeks.
- Three engineers, maximum useful parallelism: 3–4 weeks. Diminishing returns past
  three because the dependency graph constrains how much can run truly in parallel.

**Pending before Phase 1A brief is drafted.** Four small questions: plot
configuration scope (single-user only at v1, or per-user-foreshadowing schema); the
timing of vocabulary doc updates (with Phase 1, with Phase 2, or as a separate
doc-only patch); plot criteria language at v1 (tag-matching only, or richer queries
including date ranges and capsule membership); and confirming v0.21.0 Android ships
first as currently scoped.

**Output artefacts.**
- `docs/presentations/Garden_Explore_Plan.pptx` — the phased plan with timescale
  estimates, dependency Gantt-style chart, and milestone renumbering. The first
  presentation in a new `docs/presentations/` directory; future presentations may
  cover retrospectives of past milestones, scoping for upcoming ones, or
  team-shareable views of project state.

**Milestone renumbering.** The originally-planned M6 (delivery) becomes M7. The
originally-planned M7 (multi-user) becomes M8. ROADMAP.md updated to reflect the
new ordering. The reasoning: delivery deserves to land on a settled foundation
rather than a flat surface that's about to change shape; loading and brand-register
problems compound with every increment delayed; multi-user makes scaling decisions
more expensive to retrofit.

**Brand-discipline note worth recording.** During the session, the PA initially
proposed a *shelf layout* with productivity-app conventions (inbox shelf, recent
shelf, tag shelf) on the Garden tab. Bret pushed back — those conventions import
the *getting-behind* / *zero-as-achievement* register the brand has been resisting.
The eventual answer (Garden = work surface with plots, Explore = leisure surface)
came from Bret's reframing, not the PA's initial proposal. This is the third such
catch in recent sessions (the others being *didn't take* opacity and *plant a seed
for someone* near-miss in v0.20.3). The pattern is real: PA's productive-by-default
instinct extends metaphors and surfaces; the brand's discipline restrains them.
Captured in PA_NOTES.md at v0.20.3; reinforced here.

**Drive-by:** Milestone 3 (self-hosted deployment) retroactively marked as done in
ROADMAP.md. The full Docker Compose stack runs locally and meets the substantive
intent of the milestone. Some original M3 sub-goals around polished non-developer
self-hosting setup remain unaddressed but are no longer relevant given the project
shipped on Cloud Run for production. Marker change brings ROADMAP.md into alignment
with the actual project state — M0, M1, M2, and M4 had `(done)` markers; M3 didn't,
which surfaced as a question when the *Where we are* slide was added to the
Milestone 6 deck.

---

## Session — 2026-05-10 (v0.20.2 — Coil 3.x migration prerequisite)

**v0.20.2 (10 May 2026) — Coil 3.x migration prerequisite.** Migrated the Android app's
Coil dependency from 2.5.0 to Coil 3.x ahead of the combined Android Increment 3 +
Daily-Use, which will substantially expand image-loading surfaces. ShareActivity's
idle-screen photo grid verified end-to-end. Drive-by: fixed two stale
`~/Downloads/Heirlooms/` path references in PA_NOTES.md's Cloud Run deploy commands (the
SE_NOTES.md correction in v0.20.1 caught one location; this fixes the others). PA_NOTES.md's
Coil-version gotcha updated to reflect the migration.

---

## Session — 2026-05-09 (v0.20.1 — No-flash fix + documentation sweep)

**v0.20.1 (9 May 2026) — No-flash fix on compost + post-v0.20.0 documentation sweep.**

**Code fix:** `PhotoDetailPage.jsx` had a `finally` block that reset the `composting`
React state to `false` after a successful compost. On success the component is about to
unmount (navigation fires immediately after the POST succeeds), so the state reset caused
a re-render in the non-composted state before the component disappeared — a brief, visible
flash of the file detail view. Removing the reset from `finally` into the `catch` block
only (where it still belongs on failure) eliminates the flash. One-line change.

**Documentation sweep:**
- `VERSIONS.md`: v0.20.1 entry added.
- `ROADMAP.md`: Increment 2 and Brand follow-up updated from "(planned)" to "(shipped)".
  Brand follow-up note revised — it shipped before Increment 2, not after. Compost heap
  added as a non-milestone interstitial between Milestone 5 and 6. Android Daily-Use and
  Increment 3 noted as combined. `~v0.19.0` timing estimate removed; replaced with
  positional language (after v0.20.1).
- `PA_NOTES.md`: current version bumped to v0.20.1.
- `IDEAS.md`: stale "Planned for ~v0.19.0" timing on Android daily-use updated to
  "combined Android Increment 3 + Daily-Use increment (after v0.20.1)"; noted that both
  the capsule web UI and compost heap are now shipped, so the Android increment builds
  against a settled schema.
- `BRAND.md`: status line updated to record *compost* verb addition at v0.20.0.
- `SE_NOTES.md`: project path corrected from `~/Downloads/Heirlooms/` to
  `~/IdeaProjects/Heirlooms/`; memory store path corrected to match.

No new tests. No behaviour change beyond the flash fix.

---

## Session — 2026-05-09 (v0.20.0 — Compost heap)

**v0.20.0 (9 May 2026) — Compost heap: soft-delete with 90-day auto-purge.**
Introduces composting as the first user-facing removal mechanism. The product is
slow and considered about removal: composting requires no tags and no active
capsule memberships, the 90-day window is the safety net, and the only path to
true hard-delete is the system-driven lazy cleanup. No public hard-delete endpoint
is added.

**Schema:** Flyway V8 migration — `ALTER TABLE uploads ADD COLUMN composted_at
TIMESTAMP WITH TIME ZONE`. Partial index `uploads_composted_at_idx WHERE
composted_at IS NOT NULL` covers heap-list and cleanup queries. Purely additive;
existing uploads default to `composted_at = NULL` (active).

**Backend:**
- `Database.compostUpload(id)`: wraps the precondition check and `SET composted_at
  = NOW()` in a `withTransaction` lock; returns a sealed `CompostResult` (Success /
  NotFound / AlreadyComposted / PreconditionFailed).
- `Database.restoreUpload(id)`: clears `composted_at`; returns `RestoreResult`.
- `Database.listCompostedUploads()`: `WHERE composted_at IS NOT NULL ORDER BY
  composted_at DESC`.
- `Database.canCompost(uploadId, conn)`: checks `tags IS EMPTY` and no active
  (`open` / `sealed`) capsule membership. Called inside the compost transaction.
- `Database.fetchExpiredCompostedUploads()` + `Database.hardDeleteUpload(id)`:
  support the lazy cleanup.
- `Database.listUploads()` extended with `composted_at IS NULL` filter; all
  existing callers now return only active items by default.
- `UploadRecord.toJson()` extended with `compostedAt` (null-safe).
- `UploadHandler`: three new contract routes (`POST /compost`, `POST /restore`,
  `GET /uploads/composted`); new `GET /uploads/:id` returning the upload regardless
  of composted state; `launchCompostCleanup()` fires on every active-list call as
  a daemon thread (GCS delete → DB delete, retry-safe).

**Web:**
- `api.js`: added `formatCompactDate` (compact en-GB date, year omitted for
  current year) and `daysUntilPurge` (days until 90-day window closes).
- `PhotoDetailPage.jsx`: fallback fetch updated to use `GET /uploads/:id` (finds
  composted photos too); compost/restore button with earth-ghost / forest-fill
  styling; disabled state with helper text when preconditions unmet; composted
  state renders faded image, countdown metadata, `Restore` replacing `Compost`.
- `GardenPage.jsx`: transient italic confirmation message on `location.state.composted`
  (clears browser history state on mount); `GET /uploads/composted` count fetch;
  quiet `Compost heap (N)` link below the grid.
- `CompostHeapPage.jsx`: new page at `/compost` — list view with thumbnail, dates,
  days-remaining, inline `Restore`; empty state randomised from a pool of five
  brand-voice lines (`brandStrings.js`).
- `brandStrings.js`: new module in `src/brand/` holding the empty-state pool.
  Pool expansion requires PA review (noted in a comment and in BRAND.md).
- `App.jsx`: `/compost` route added.

**Tests:**
- `CompostApiTest.kt` (new): ~16 integration tests via Testcontainers covering
  compost preconditions, restore, list filtering, GET-by-id on composted items,
  lazy-cleanup non-expiry (HTTP-only; expiry path verified by logic inspection).
- `compost.test.jsx` (new): ~10 Vitest tests covering `PhotoDetailPage` compost
  button states, `GardenPage` compost heap link + transient message, and
  `CompostHeapPage` render, restore, and empty state.

**Documentation:** IDEAS.md cascade-warning entry removed (compost preconditions
resolve the problem). PA_NOTES.md bumped to v0.20.0; two new gotchas: lazy-cleanup
doesn't scale to multi-user (Milestone 7: Cloud Scheduler), hard-delete is
system-only by design. BRAND.md voice section: *compost* verb added; canonical
strings: empty-state pool reference. VERSIONS.md: v0.20.0 entry. PROMPT_LOG.md:
this entry.

---

## Session — 2026-05-09 (v0.19.6 — Post-v0.19.5 documentation sweep)

**v0.19.6 (9 May 2026) — Post-v0.19.5 documentation sweep.** Captured the v0.19.x
series' substantive lessons in PA_NOTES.md: manual JSON serialisation in Kotlin (the
v0.19.2 quoting bug — triple-quoted string delimiter consumed the trailing quote on the
`state` field value, producing `"state":"open,"` with the comma leaking into the string);
integration tests with permissive parsers hiding field-value bugs (all 49 integration
tests passed because Jackson's `ObjectMapper.readTree()` accepted the malformed JSON
while the browser's strict `JSON.parse` rejected it); SPA routing requires nginx
`try_files $uri $uri/ /index.html` fallback (v0.19.3); the post-login auth-redirect
interim pattern (`RequireAuth` + `state.from` → `navigate(from, { replace: true })`,
to be replaced with cookie-based sessions during Milestone 7 multi-user work, v0.19.4).
Added a new "Architectural notes worth remembering" section to PA_NOTES.md covering:
the photo detail route migration (lightbox modal replaced with a real `/photos/:id` route,
v0.19.0); the `?sealed=1` query-param handshake pattern for post-action transition
animations (v0.19.0); the confirmation that the held-lightly capsule-message typography
decision (italic Georgia for sealed/delivered) landed cleanly at first render (v0.19.0).
Documented the five derived Tailwind tokens (`forest-75`, `bloom-15`, `bloom-25`,
`earth-10`, `earth-20`) in BRAND.md as a derived-tokens sub-table, with code-verified
usages; updated the palette discipline line to distinguish primary colours from derived
tokens. Loosened the Android Daily-Use Increment's stale `~v0.19.0` version estimate in
ROADMAP.md to positional language (after Increment 3). No code changes.

---

## Session — 2026-05-09 (v0.19.1–v0.19.5 — Bug fixes and hardening)

**Context:** Post-deploy testing of the v0.19.0 capsule web UI revealed four bugs
and two improvement opportunities. All were addressed in the same session.

**Bugs found and fixed:**

1. **Photo rotation not applied in picker/capsule grids (v0.19.1).**
   `PhotoGrid.jsx`'s `Thumb` component rendered images with no CSS transform.
   The `upload.rotation` property existed but was never applied. One-line fix.

2. **"Start Capsule" and capsule list both returning "didn't take" (v0.19.2).**
   Root cause: all three capsule JSON serialisers (`toDetailJson`, `toSummaryJson`,
   `toReverseLookupJson`) had a Kotlin triple-quoted string quoting bug. The closing
   `"""` delimiter consumed the `"` that was meant to close the `state` field value,
   producing `"state":"open,"created_at":...` — the comma leaked into the string value.
   JavaScript's `JSON.parse` is strict and rejected this at position 88. Jackson
   (used by the integration tests' HTTP client) is lenient and parsed `open,` as a
   valid string value, so all 49 integration tests passed undetected. Fixed in the
   same commit by adding one `"` to each serialiser (`}"""` → `}""""`).

3. **Deep-link 404 on page refresh (v0.19.3).**
   nginx was serving the static build without a SPA fallback. Navigating to
   `/capsules` directly or refreshing returned 404 because nginx looked for a real
   file at that path. Fixed by adding `nginx.conf` with `try_files $uri $uri/ /index.html`
   and COPYing it into the Dockerfile.

4. **Post-login redirect always went to `/` rather than the intended page (v0.19.4).**
   Auth state is held in React memory, so refresh clears it. The old `<Navigate to="/login">` didn't
   carry the intended destination. Fixed by introducing a `RequireAuth` component that
   passes `location.pathname + location.search` as `state.from` in the redirect, and
   updating `LoginPage` to `navigate(from, { replace: true })` after successful login.
   Noted by Bret as a temporary workaround until proper cookie-based server auth is in place.

**Improvements made:**

5. **Jackson for capsule JSON serialisation (v0.19.5).**
   Prompted by the v0.19.2 bug: manual string building is the wrong tool for JSON.
   Jackson was already a compile dependency (used for request parsing). All three
   serialisers were rewritten to use `mapper.createObjectNode()` / `putArray()` /
   `writeValueAsString()`. The private helpers `jsonString()` and `toJsonArray()` were
   removed. Functions changed from `private` to `internal` to allow direct unit testing.

6. **Serialiser unit tests added (v0.19.5).**
   `CapsuleHandlerTest.kt`: 13 new tests, one per serialiser × state variant + field
   type checks. Key test: `state field is a bare string value` for each of the three
   serialisers — this is the regression guard that would have caught the v0.19.2 bug
   at unit-test time. Unit test count: 135 → 148 passing.

**Why integration tests missed the bug:**
Jackson's `ObjectMapper.readTree()` (used in integration tests to parse responses)
is lenient by default. It parsed `"state":"open,` as the string value `open,` and
continued. The tests apparently checked HTTP status codes and high-level structure
but not exact field values with strict type checking. Lesson: serialiser unit tests
with a strict round-trip parse are cheaper and faster to catch this class of bug
than relying on the integration test layer.

**Key decisions:**
- Auth state remains in React memory (per existing design). The return-URL pattern
  (`state.from`) is the right interim fix. Proper cookie-based session tokens are the
  long-term solution (Bret noted this explicitly).
- Jackson ObjectNode API preferred over data class serialisation for the capsule
  responses, because the response shapes embed `UploadRecord.toJson()` (which is
  still manual for now). A full migration to Jackson data class serialisation would
  be a wider refactor and was deferred.

---

## Session — 2026-05-09 (v0.19.0 — Capsule web UI, Milestone 5 Increment 2)

**PA brief:** SE Brief — Capsules, Increment 2: Web UI. Nine sub-areas covering
routing, list view, detail view, create form, photo picker modal, inline edits,
photo-detail integration, confirmation dialogs, and sealing animation.

**What was done:**
- Installed `react-router-dom` v6; restructured App.jsx from single-page to routed
  app with BrowserRouter, AuthContext, and AuthLayout (outlet pattern).
- Four new routes: `/`, `/photos/:id`, `/capsules`, `/capsules/new`, `/capsules/:id`.
- Extracted all existing Gallery code into `GardenPage.jsx` (nav removed from component,
  thumbnails link to `/photos/:id` rather than opening lightbox).
- `PhotoDetailPage.jsx`: new proper route replacing the lightbox modal. Fetches upload
  from router state (passed by Gallery) or falls back to the full list. Includes "In
  capsules:" line, "Add this to a capsule" button with `AddToCapsuleModal`, and toast
  on add success.
- `Nav.jsx`: top-of-page nav bar with earth-coloured active underline, mobile hamburger
  slide-in panel.
- `WaxSealOlive.jsx`: reusable SVG brand component, `currentColor` fill.
- `CapsulesListPage.jsx`: card grid with filter/sort dropdowns (client-side sort for
  `created_at` since server only supports `updated_at`/`unlock_at`). All four card state
  treatments. Empty states (never-had-capsules vs filter-excludes-all). Skeletons, error.
- `CapsuleDetailPage.jsx`: four state variants sharing structural shape. Inline edit state
  machine (field/phase enum: idle|editing|saving|error). Auto-save on context-switch.
  Date edit opens `BrandModal` with `DatePickerDropdowns`. Photo edit opens
  `PhotoPickerModal` in edit mode. Navigation guard with `ConfirmDialog`.
  Sealing animation via `?sealed=1` query param on arrival from create form.
- `CapsuleCreatePage.jsx`: brand-voice opening line, all four fields, three-dropdown date
  picker, `IncludeStrip` component (horizontal thumbnail strip), recipient-aware message
  placeholder (updates on every For-field keystroke). Both Start and Seal commit paths.
  `?include=uuid` preselection. Discard confirmation.
- `PhotoPickerModal.jsx`: shared component (create + edit modes), tag filter chips,
  corner-mark + darken selection treatment, live count in Done button.
- `AddToCapsuleModal.jsx`: lists open capsules soonest-first, disabled rows for
  already-in capsules, empty state with "Start a capsule with this" CTA.
- `ConfirmDialog.jsx`: seal (italic Georgia title, bloom-seal button), cancel (recipient
  name in body, earth primary), discard (plain sans, routine guard).
- `Toast.jsx`: italic Georgia, parchment bg, top-centre, 3s auto-dismiss, slide-in animation.
- `DatePickerDropdowns.jsx`: day/month/year selects, auto-bounds day count by month/year.
- `BrandModal.jsx`: parchment-background brand dialog (distinct from the dark lightbox Modal).
- `api.js`: shared fetch helper, `formatUnlockDate`, `capsuleTitle`, `joinRecipients`,
  `buildUnlockAt` (8am local time convention).
- Tailwind config: added `forest-75`, `bloom-15`, `bloom-25`, `earth-10`, `earth-20`.
- `index.css`: `toast-in` keyframe animation.
- 48 new vitest tests covering all four capsule states, inline edit flows (message,
  recipients, date, discard guard), create form validation and submission, picker modal
  selection, add-to-capsule modal, and confirmation dialogs.

**Key decisions:**
- **Sealed message typography (held-lightly decision):** italic Georgia renders cleanly
  at message-body length. No revision needed — confirmed at first render alongside open
  state. Recorded here per PA brief instructions.
- **Client-side `created_at` sort:** server's `listCapsulesHandler` only accepts
  `updated_at`/`unlock_at` as order params. `created_at` sort is done client-side after
  fetching. Acceptable at v1 (no pagination).
- **Photo detail as a real route:** the existing lightbox modal was replaced with a proper
  `/photos/:id` page. Garden thumbnails now navigate to it. This enables the capsule
  photo→detail→capsule navigation loop specified in the brief.
- **Sealing animation via query param:** `?sealed=1` on the detail route URL is the
  handshake between the create form and the detail page for the post-create sealing
  animation. The param is removed from history immediately on mount.
- **Garden page header:** the old Gallery had an inner header with auth controls. This
  was removed (nav handles auth now). A slim toolbar row remains for auto-refresh and
  the refresh icon.
- **`AddToCapsuleModal` fetch strategy:** uses GET-then-PATCH (fetch current upload IDs
  from capsule detail, append new one, PATCH full replacement) as specified in the PA
  brief. One extra round-trip per add, acceptable at v1.

---

## Session — 2026-05-08 (v0.18.2 — Capsule visual mechanic added to BRAND.md)

**PA brief:** SE Brief — Capsule Visual Mechanic (BRAND.md update).

**What was done:**
- `BRAND.md` status line updated to reflect both the v0.17.0 foundation and the
  v0.18.2 capsule-mechanic addition.
- Voice section: *sealed* verb added between *planted* and *bloomed* — reserved
  for the capsule mechanic, not routine affordances.
- Motion language section: two new states added (*sealing*, ~700ms olive forms
  in corner; *delivering*, ~2.5s olive grows to fill + parchment-to-bloom wash,
  Milestone 6 territory), with a separator note distinguishing capsule-state
  transitions from arrival-animation phases.
- New "Capsule states" section added in full: the wax-seal olive (form, colour,
  sizes, reference SVG, distinction from brand mark); state visual treatments
  (open=forest, sealed=forest+olive, delivered=bloom-tinted, cancelled=earth-tinted);
  capsule card and detail view specs; Start/Seal button hierarchy; photo detail
  "in N capsules" line; visibility rule for cancelled capsules; sealing and
  delivery animations; reduced-motion fallback.
- "What is NOT in this document" — capsule visual mechanic line removed.

**Key decisions:**
- Capsule states map onto the existing forest/bloom/earth signal vocabulary.
  No new palette tokens.
- The bloom colour earns two appearances in a capsule's lifecycle: the small
  wax-seal olive at sealing (promise) and the full ripened state at delivery
  (fulfilment). The two appearances are causally linked by design.
- Capsule message typography shifts from system serif (open, draft) to italic
  Georgia (sealed/delivered, committed brand voice). Sealing promotes the
  message from draft to delivery-bound.
- The wax-seal olive is a new brand element, distinct from the brand mark's
  apex olive — simpler form, no stem, more geometric. Keep the two assets
  separate in the codebase.

**No code changes.** The rendering work lives in Increment 2 (web UI) and
Increment 3 (Android), which will reference this spec.

---

## Session — 2026-05-08 (v0.18.1 — Documentation sweep + reverse-lookup path fix)

**PA brief:** SE Brief — Post-v0.18.0 Documentation Sweep.

**What was done:**
- `PA_NOTES.md` — current version updated to v0.18.1. Added seven accumulated gotchas
  from v0.17.0–v0.18.0: Android orientation change mid-upload, `@ExperimentalLayoutApi`
  opt-in for `FlowRow`, upload-confirm tag contract, Coil 2.5.0 pinning,
  `withTransaction` rollback pattern, `UploadRecord.toJson()` canonical serialisation,
  OpenAPI spec contract-block merge.
- `ROADMAP.md` — Milestone 5 expanded from one-line description to full increment plan
  (Increment 1 shipped, Increment 2 web UI planned, brand follow-up, Increment 3
  Android, Android Daily-Use Increment).
- `IDEAS.md` — Android daily-use gallery entry added.
- API — moved capsule reverse-lookup from `GET /api/uploads/{id}/capsules` to
  `GET /api/content/uploads/{id}/capsules` for consistency with the existing upload
  resource path (`/api/content/uploads/{id}`). The endpoint was moved from the capsule
  contract block (bound at `/api`) to the content contract block (bound at
  `/api/content`). Handler logic unchanged. No client uses this endpoint yet; safe move.
- Integration tests for the reverse-lookup endpoint updated to the new path.

**Test count:** unchanged. 135 HeirloomsServer unit tests (134 passing, 1 skipped —
FFmpeg); 49 HeirloomsTest integration tests.

---

## Session — 2026-05-08 (v0.18.0 — Capsules: Schema and Backend API)

**PA brief:** SE Brief — Capsules, Increment 1: Schema and Backend API.

**What was built:**
- `V7__capsules.sql` — four new tables: `capsules`, `capsule_contents`,
  `capsule_recipients`, `capsule_messages` with five indexes and
  `ON DELETE CASCADE` constraints on both FK columns of `capsule_contents`.
- `Database.kt` extended — `CapsuleShape`, `CapsuleState` enums;
  `CapsuleRecord`, `CapsuleSummary`, `CapsuleDetail` data classes;
  `createCapsule`, `getCapsuleById`, `listCapsules`, `updateCapsule`,
  `sealCapsule`, `cancelCapsule`, `getCapsulesForUpload`, `uploadExists`
  methods; inline `withTransaction` with committed-flag rollback safety.
- `CapsuleHandler.kt` — seven ContractRoute handlers wired via
  `capsuleRoutes(database)`.
- `UploadHandler.kt` — replaced single `apiContract` with `contentContract`
  (at `/api/content`) + `capsuleContract` (at `/api`); `mergedSpecWithApiKeyAuth`
  combines both OpenAPI specs with absolute path prefixes and server `"/"`.
- `UploadRecord.toJson()` moved from private in `UploadHandler.kt` to internal
  in `Database.kt` so `CapsuleHandler.kt` can reuse it for detail responses.
- 49 integration tests in `HeirloomsTest/capsule/CapsuleApiTest.kt` covering
  create/read/list/update/seal/cancel/reverse-lookup flows, all rejection paths,
  message versioning, and the spec-generation canary.

**Key decisions:**
- `withTransaction` is `inline` so non-local returns work from within the lambda.
  A `committed` flag in `finally` ensures rollback when the lambda exits early via
  non-local return (all early exits in practice happen before any DB modification).
- Second capsule contract at `/api` (not `/api/content`) matches the brief's URL
  spec; OpenAPI spec merged at `/docs/api.json` by prefixing content paths with
  `/api/content` and capsule paths with `/api`, server set to `"/"`.
- `UploadRecord.toJson()` made `internal` rather than duplicated.
- `unlock_at` read from Postgres as `OffsetDateTime` (returns UTC offset) per the
  brief's type guidance; all other timestamps use `Instant`.
- `created_by_user` is the placeholder `"api-user"` — Milestone 7 will wire real
  user identity once the auth model exists.

**Test count:** 135 HeirloomsServer unit tests (134 passing, 1 skipped — FFmpeg);
49 new HeirloomsTest integration tests (run against Docker build).

---

## Session — 2026-05-07 (v0.17.0 — Brand, Increment 3b)

**PA brief:** SE Brief — Brand, Increment 3b: Android Animation Components.

**What was built:**
- `AccessibilityHelpers.kt` — `rememberReducedMotion()` reading `Settings.Global.ANIMATOR_DURATION_SCALE`.
  `WorkingDots` refactored to call it (removing the inline Settings.Global read).
- `OliveBranchArrival.kt` — Compose `Animatable<Float>` 0→1 over 3s (`LinearEasing` — phase ranges
  assume constant-rate progress; FastOutSlowInEasing would shift the visual beats). Canvas rendering
  via `PathMeasure.getSegment` for branch reveal, `withTransform { rotate; scale }` for leaf grow-in,
  `lerp(Forest, Bloom, t)` for olive ripening. `withWordmark` param; `LaunchedEffect` snaps to 1f
  under reduced motion and fires `onComplete` immediately.
- `OliveBranchDidntTake.kt` — same pattern, 2s; partial branch + leaf pair + pause + earth seed +
  "didn't take" text. Shares `internal` helpers from `OliveBranchArrival.kt` (same module, same package).
- `ShareActivity` — full rewrite as `ComponentActivity` with `setContent { HeirloomsTheme { ... } }`.
  Sealed `ReceiveState` class drives the Compose UI. Upload is enqueued via WorkManager;
  `observeWorkToCompletion(id)` uses `suspendCancellableCoroutine` + `LiveData.observeForever` to
  await terminal state without explicit `lifecycle-livedata-ktx` dependency.
  `Arriving` → `Arrived` and `FailedAnimating` → `Failed` transitions driven by animation `onComplete`.
- `styles.xml` — `Theme.Heirlooms.Share` added; ShareActivity manifest theme updated.
- 5 Compose instrumentation tests in `androidTest/kotlin/...`.

**Key decisions:**
- `scale` in `DrawTransform` takes `scaleX`/`scaleY`, not a single `scale` param — caught at first
  compile; brief's pseudocode used a non-existent named param. Fixed to `scale(scaleX = p, scaleY = p, pivot = pivot)`.
- `observeWorkToCompletion` uses `suspendCancellableCoroutine` + `observeForever` rather than
  `LiveData.asFlow()` to avoid needing an explicit import of the ktx extension; cleaner given that
  `lifecycle-livedata-ktx` is transitive but not declared.
- `photoCountString` is `@Composable` because it calls `stringResource` — called inline within the
  composable `when` branches, not from a non-composable context.
- Instrumentation tests use `reduceMotion = true` to exercise the fast-path without mocking
  `Settings.Global` or dealing with animation timing in tests.

**Test count:** 148 total (135 Kotlin + 8 web + 5 Android instrumented), 147 passing, 1 skipped.

---

## Session — 2026-05-07 (v0.17.0 — Brand, Increment 3a)

**PA brief:** SE Brief — Brand, Increment 3a: Android Static Brand (Icon + Resources + Receive Screen).

**What was built:**
- App icon: VectorDrawable foreground (`ic_launcher_foreground.xml`, ellipses converted to arc paths
  with `<group>` rotations), adaptive icon XML at `mipmap-anydpi-v26/` with `<monochrome>` for
  Android 13+ themed icons, legacy PNGs at all five densities generated via sharp-cli from the
  favicon SVG, Play Store icon at 512×512.
- `res/values/colors.xml` — full brand palette + tints + text shades.
- `ui/theme/Color.kt`, `Type.kt`, `Theme.kt` — Compose brand theme. `HeirloomsTheme { }` ready
  to wrap Activity content.
- `ui/brand/WorkingDots.kt` — Compose three-dot pulse component. `rememberInfiniteTransition`
  called unconditionally inside `repeat(3)` to satisfy Rules of Compose; `reduceMotion` only
  affects which value is used, not whether the composable is called.
- `build.gradle.kts` updated: Compose BOM 2024.01.00, Compose Compiler 1.5.8, JVM 11,
  `buildFeatures { compose = true }`.
- `strings.xml` — full garden voice string set.
- `UploadWorker` — notifications use R.string brand strings; small icon changed from
  `android.R.drawable.ic_menu_upload` to `R.drawable.ic_launcher_foreground`.
- `ShareActivity` — toast messages updated to brand voice ("uploading…" / "Waiting for WiFi
  to plant your photos.").

**Flagged gap — receive screen:**
The current `ShareActivity` has no visible UI. It is a transparent Activity that immediately copies
files, enqueues WorkManager, shows a Toast, and finishes. The brief's "receive-screen Composable"
does not exist. Building a full branded receive screen (photo previews, tag chips, "plant" button)
requires a new Compose Activity — scoped to a follow-up, not a restyling of existing code.

**Key decisions:**
- VectorDrawable ellipse conversion: each SVG ellipse with a rotation transform became a
  `<path>` with arc commands (`M cx-rx,cy A rx,ry 0 1 0 cx+rx,cy A rx,ry 0 1 0 cx-rx,cy Z`)
  inside a `<group android:rotation="..." android:pivotX="..." android:pivotY="...">`. This is
  the standard VectorDrawable pattern since VectorDrawable has no `<ellipse>` element.
- JVM target bumped 1.8 → 11 (Compose minimum). No existing code uses Java 8-only APIs that
  would break at JVM 11.
- Notification small icon changed to `ic_launcher_foreground` (our VectorDrawable). On Android 8+
  notification icons must be monochromatic; the parchment-on-transparent foreground renders as
  white on the system's accent colour, which is correct behaviour.
- Compose UI tests deferred: no emulator/device CI runner configured. Existing JUnit tests
  (135 Kotlin) are unaffected by Compose dependency additions.

---

## Session — 2026-05-07 (v0.17.0 — Brand, Increment 2)

**PA brief:** SE Brief — Brand, Increment 2: Web Arrival and Didn't-Take Animations.

**What was built:**
- `src/brand/animations.js` — `lerp`, `interpolateHexColour`, `prefersReducedMotion` pure helpers
- `src/brand/OliveBranchArrival.jsx` — 3s rAF animation, six phases, `withWordmark` prop, reduced-motion fast-path
- `src/brand/OliveBranchDidntTake.jsx` — 2s rAF animation, partial branch + pause + earth seed + "didn't take" text, reduced-motion fast-path
- `src/brand/OliveBranchArrival.test.jsx` and `OliveBranchDidntTake.test.jsx` — 5 smoke tests (render, withWordmark, reduced-motion onComplete)
- `src/test/setup.js` updated — `Element.prototype.getTotalLength` stub (JSDOM 29 doesn't implement it; `window.SVGPathElement.prototype` patching silently failed because JSDOM 29 exposes SVG constructors on `window`, not as bare globals)
- `src/App.jsx` — `UploadCard` rewritten with 6-state tile machine (`loading/arriving/arrived/error-animating/failed/dismissed`); `FailedTile` component added; `Gallery` tracks `seenIdsRef` to only animate newly-appeared uploads (first load is silent; auto-refresh arrivals animate)
- `src/index.css` — tile animation CSS classes added

**Key decisions:**
- "New" upload = first time an ID is seen in this browser session. First page load → all items skip animation (quiet); auto-refresh → new items animate. This is the right semantic — "moment of arrival" is when the upload is detected by the web client for the first time, not every page load.
- `animateArrivalRef` captured at mount and cleared after first successful use — retry never re-plays the arrival animation.
- Blob URLs revoked properly via `blobUrlRef` (the original code captured `blobUrl` in the closure at effect creation time when it was null, so revocation never ran; fixed here).
- `gallery-tile--arrived-fading-in` CSS is defined but not applied — kept for Increment 3 review or PA follow-up if the hard-cut feels abrupt in production.

**Test count:** 143 total (135 Kotlin + 8 frontend), 142 passing, 1 skipped.

---

## Session — 2026-05-07 (v0.17.0 — Brand, Increment 1)

**PA brief:** SE Brief — Brand, Increment 1: Tokens + BRAND.md + Static Web Application.

**Prompt:** Apply the new Heirlooms brand foundation to HeirloomsWeb. Add design tokens, create BRAND.md, add SVG brand components, restyle header/tags/empty state/working indicator, apply three-colour signal discipline, update garden copy.

**What was built:**
- `docs/BRAND.md` — canonical brand reference: palette, identity system, typography, voice, motion language
- Design tokens in `tailwind.config.js` (theme extension) and `src/index.css` (CSS custom properties on `:root`); body background/text updated to parchment/text-body
- `src/brand/OliveBranchMark.jsx` — 140×200 SVG mark with `state` prop (forest/bloomed apex olive)
- `src/brand/OliveBranchIcon.jsx` — 30×30 simplified icon for header/small contexts
- `src/brand/WorkingDots.jsx` — three-dot pulse animation, `prefers-reduced-motion`, accessible `role="status"` + live region
- `src/brand/EmptyGarden.jsx` — empty gallery state with brand voice copy
- `src/App.jsx` — header replaced with OliveBranchIcon + italic Georgia wordmark; tag chips restyled to forest-08/rounded-chip; Spinner replaced with WorkingDots in card tiles and loading state; EmptyGarden replaces "No uploads yet."; all `text-red-500` replaced with `text-earth font-serif italic`; `index.html` title updated to "Heirlooms — your garden"
- `vitest` + `@testing-library/react` test infrastructure added; 3 smoke tests for OliveBranchMark

**Key decisions:**
- JSX (not TSX) throughout to match project convention; relative imports (no `@/` alias) to match existing `./App` convention
- `EmptyGarden` takes optional `onUpload` prop (no web upload yet, so button is hidden when prop is absent — avoids a dead CTA)
- Tag chips use Tailwind arbitrary values (`text-[11px]`, `px-[9px]`, `py-[3px]`) per brief; these match the brief's specified sizes
- `WorkingDots` replaces `Spinner` for image/video thumbnail loading tiles (the closest existing analogue to "upload-in-progress tile"); `Spinner` component removed as no longer needed
- Forest tints (`forest-04`, `forest-08`, etc.) defined as named Tailwind colors so opacity values stay enumerable and don't drift from the CSS variables

**Test count:** 138 total (135 Kotlin + 3 frontend), 137 passing, 1 skipped.

---

## Session — 2026-05-07 (post-v0.16.1 doc follow-ups)

**PA brief:** Refresh Cloud Run revision identifiers in PA_NOTES.md and add an explicit test count to the v0.16.1 entry in VERSIONS.md.

**Cloud Run revisions verified via `gcloud run services describe`:**
- `heirlooms-server`: `heirlooms-server-00021-fqb` — unchanged. No server code was deployed in v0.16.1, so no revision bump. Confirmed stable.
- `heirlooms-web`: moved from `heirlooms-web-00006-wlf` → `heirlooms-web-00008-9qv`. Web was redeployed for the tag-dropdown fix.

**Test count for v0.16.1:** No new tests were added. The Android OOM fix is a memory-pressure scenario requiring a real constrained-heap device — not unit-testable. HeirloomsWeb has no frontend test runner. Count remains 135 total, 134 passing, 1 skipped (FFmpeg video thumbnail — runs in Docker).

**Also took the soft suggestion from the SE brief:**
- One-line comment on `suppressBlurRef` in `HeirloomsWeb/src/App.jsx` explaining why `e.preventDefault()` / `e.relatedTarget` are unreliable — prevents a future reader from "simplifying" the fix away.
- Strengthened KDoc on `Uploader.uploadViaSigned(File, ...)` with explicit warning against `file.readBytes()`.

**Commit:** `cfbc501` — `docs: post-v0.16.1 follow-ups (refresh Cloud Run revisions, add test count to v0.16.1)`. No tag. v0.16.1 is already tagged.

---

## Phase 1 — Product brainstorm

**Prompt:** "Hi Claude! I'm new here. Could you brainstorm creative concepts?"
Chose: "A project or product" / "I have a rough idea — help me expand it"

**What happened:** A brainstorm around digital legacy and inheritance. Eight concept
cards were generated covering the digital safe, dead man's switch, digital estate
planner, memory book, time-locked messages, collective memory space, digital rights
passport, and digital executor service.

---

**Prompt:** "Tell me more about concept: a personal digital vault app where people
can organise their photos, videos, and messages into a time-capsule-style legacy
that gets unlocked for next of kin."

**What happened:** A detailed breakdown of how the vault would work in two phases
(building the vault while alive; the unlock after death), a feature comparison
against cloud storage, a UI sketch, and an analysis of key challenges. The milestone
delivery mechanic — a video arriving on a child's 18th birthday — was identified as
the single most powerful differentiator.

---

## Phase 2 — Android app (v1, Java)

**Prompt:** "I'd like to start off by creating an Android app, just for me.
When I use the universal Android 'share with' function for any image or video,
I can choose 'Share with APPLICATION_NAME'. The application simply does a HTTP POST
to AN_ENDPOINT with the video/photo contents as the body. Content-Type should reflect
the file type. APPLICATION_NAME and AN_ENDPOINT are configurable in application
properties. I should be able to build the project using Gradle."

**What was built:** A complete Android project in Java. `ShareActivity` registers as
a share target for `image/*` and `video/*`, reads the file via `ContentResolver`,
and HTTP POSTs it using OkHttp. App name and endpoint configured via
`assets/config.properties`. Transparent activity theme so no UI flash appears.

---

**Prompt:** "I have Android Studio installed and I've opened the folder but when I
Build → Make Project I see the error... [missing gradle-wrapper.jar]"

**Decision:** Added `gradle/wrapper/gradle-wrapper.jar` to the project and added a
`run-tests.sh` script that auto-downloads it if missing, so the project works without
Android Studio being involved in the build setup.

---

## Phase 3 — Kotlin rewrite + settings screen

**Prompt:** "Could we use Kotlin rather than Java for this Android project?"

**What was built:** Full rewrite of the Android app in Kotlin. Split into four files:
`ShareActivity.kt`, `Uploader.kt`, `EndpointStore.kt`, and `SettingsActivity.kt`.
Added a settings screen accessible from the share sheet so the endpoint URL can be
changed without editing config files. Endpoint stored in `SharedPreferences`.

---

## Phase 4 — Backend server (HeirloomsServer)

**Prompt:** "Could we now look at the server side? I'd like to look at what we need
to do in order to have a very simple server, that the android app could post images
or videos to, that would store them."

**Decision:** Kotlin/http4k server chosen over Spring Boot for minimal footprint.
PostgreSQL for metadata, MinIO (S3-compatible) for file storage. Flyway for database
migrations. HikariCP connection pool. AWS SDK v2 S3 async client with
`forcePathStyle(true)` for MinIO compatibility.

**What was built:** `HeirloomsServer` with four endpoints:
- `POST /api/content/upload` — receives file bytes, stores to S3/MinIO, records
  metadata in PostgreSQL, returns storage key
- `GET /api/content/uploads` — returns JSON array of all uploads
- `GET /health` — returns 200 "ok"

`AppConfig` reads from `application.properties` locally or directly from environment
variables (by exact uppercase name: `DB_URL`, `S3_ACCESS_KEY` etc.) when running in
Docker. The env var approach was fixed after an early bug where underscores were
converted to dots (`S3_ACCESS_KEY` → `s3.access.key`) but the property lookup used
hyphens (`s3.access-key`), causing silent config failure.

---

## Phase 5 — Docker + end-to-end tests (HeirloomsTest)

**Prompt:** "I wonder if we might want a third 'project' within Heirloom that runs
integration tests against the server."

**What was built:** `HeirloomsTest` — a Gradle project using Testcontainers and OkHttp
that spins up the full Docker Compose stack (PostgreSQL, MinIO, minio-init, HeirloomsServer)
and runs API tests and journey tests against it.

**Key decisions:**
- Testcontainers 2.0.5 chosen over 1.x because Docker Engine 29.x raised its minimum
  API version to 1.40, and docker-java (used by Testcontainers 1.x) hardcoded API
  version 1.32, making it incompatible. Testcontainers 2.x handles API negotiation
  correctly.
- Playwright removed from journey tests because Chromium cannot reach the Testcontainers
  socat proxy port from its sandboxed process. All journey tests use OkHttp instead.
- Shadow plugin (8.1.1) with `mergeServiceFiles()` used instead of a hand-rolled fat
  JAR task. The original `DuplicatesStrategy.EXCLUDE` approach silently dropped
  `META-INF/services/org.flywaydb.core.extensibility.Plugin`, which prevented the
  Flyway PostgreSQL plugin from registering, causing "relation uploads does not exist"
  errors even though the migration file was present and correctly named.

---

## Key bugs fixed during HeirloomsTest development

**Docker socket on macOS** — `/var/run/docker.sock` and `~/.docker/run/docker.sock`
return stub 400 responses on macOS Docker Desktop. The working socket is at
`~/Library/Containers/com.docker.docker/Data/docker.raw.sock`. Fixed by auto-detecting
socket candidates in `HeirloomsTestEnvironment` and setting `DOCKER_HOST` accordingly.

**Ryuk failing before test code** — Ryuk's static initialiser fires before any test
code runs and fails independently. Fixed by `ryuk.disabled=true` in
`~/.testcontainers.properties`.

**Testcontainers 2.x API changes** — `withLocalCompose()` was removed (local compose
is now the default); `junit-jupiter` artifact renamed to `testcontainers-junit-jupiter`.

**`AppConfig.fromEnv()` hyphen vs dot mismatch** — see Phase 4 above.

**Flyway "0 migrations applied"** — `DuplicatesStrategy.EXCLUDE` silently dropped the
Flyway service registration file. Fixed by switching to Shadow plugin with
`mergeServiceFiles()`.

**`docker-compose.yml` port format** — Testcontainers 2.x requires ports declared as
`"8080"` (no host binding) rather than `"8080:8080"` so it can manage port mapping
via its socat ambassador container.

**`version:` field in docker-compose.yml** — removed as it is obsolete in Compose V2
and was generating warnings.

**GRADLE_OPTS native crash** — `GRADLE_OPTS="-Dorg.gradle.native=false"` added
throughout Docker builds to prevent a SIGSEGV crash of `libnative-platform-file-events.so`
on Apple Silicon (ARM64).

---

## One-time machine setup

```
~/.testcontainers.properties

docker.host=unix:///Users/YOUR_USERNAME/Library/Containers/com.docker.docker/Data/docker.raw.sock
testcontainers.reuse.enable=false
ryuk.disabled=true
```

Replace `YOUR_USERNAME` with your macOS username. This is the only persistent
machine-level configuration required. Everything else is self-contained in the repo.

---

## Final state

**10 tests passing** across two test classes:

`UploadApiTest`: health check, POST image returns 201, POST video returns 201, POST
without Content-Type returns 201, POST empty body returns 400, GET uploads returns
JSON array, uploaded file appears in listing.

`UploadJourneyTest`: upload and verify in listing, multi-type upload journey,
health endpoint reachable.

Build time on a warm cache (Docker layers cached): under 20 seconds.
Build time on a cold cache (first run): 3–5 minutes for dependency downloads.

---

## Domain registration

**Date:** 30 April 2026

`heirlooms.digital` registered. Several names were considered during this session:

- `digital-legacy.com` — available but too generic
- `heirloom.digital` — strong first choice, but misspelled as "hierloom" on first
  attempt (fat fingers)
- `heirloom.co.uk` — correct spelling but country-locked
- `heirlooms.digital` — chosen: plural feels warmer ("a collection" rather than
  "a single object"), .digital is thematically appropriate, not country-locked
- `heirlooms.com` — parked on venture.com, potentially acquirable in future if the
  project grows to warrant it

The project name was updated from **Heirloom** to **Heirlooms** to match the domain.
The rename is the first task queued for the next development session.

---

## Session — 30 April 2026 (v0.3.0 polish + package rename)

**Fix: `Uploader.kt` compile error**

`IntRange` implements `Iterable`, not `Sequence`, so calling `.zip(Sequence)` on it
failed to compile. Fixed by inserting `.asSequence()` before `.zip()`.

---

**Tag: v0.3.0**

Annotated git tag `v0.3.0` created on `main` to mark the state of the project at the
end of the founding development session.

---

**Package rename: `com.heirloom` → `digital.heirlooms`**

Queued at the end of the previous session to align with the `heirlooms.digital` domain.
Completed across all three subprojects — 22 Kotlin source files, 3 `build.gradle.kts`
files, and the corresponding source directory layout:

- `HeirloomsApp`: `com/heirloom/app/` → `digital/heirlooms/app/`
- `HeirloomsServer`: `com/heirloom/server/` → `digital/heirlooms/server/`
- `HeirloomsTest`: `com/heirloom/test/` → `digital/heirlooms/test/`

---

**TEAM.md added**

Documents the team structure: Bret Adam Calvey as Founder & CTO, the PA (claude.ai)
for strategic/architectural thinking, and the Software Engineer (Claude Code in
IntelliJ) for hands-on implementation. Establishes that the Software Engineer commits
but Bret always does the final push.

---

**PA_NOTES.md added**

The PA's (claude.ai) working memory file, committed to the repo so it persists across
sessions and is visible to all team members. Captures Bret's preferences and working
style, project facts to always remember, pending decisions, known gotchas, and team
reminders.

---

**SE_NOTES.md added**

The Software Engineer's (Claude Code) own working memory file. Covers how to get
session context, commit conventions, project structure at a glance, and code-level
things worth remembering between sessions.

---

**docs/chats/2026-04-30-initial-chat.md added**

The original claude.ai founding session chat, formatted as markdown. 356 turns
spanning 24–30 April 2026. Converted from `Original_chat.txt` (now removed):
day-separated sections, `**Human**` / `**Claude**` blocks, action/tool lines
stripped, duplicate lines deduplicated.

---

**Docs reorganisation**

All markdown files except `README.md` moved from the project root into `docs/`:
`PROMPT_LOG.md`, `ROADMAP.md`, `TEAM.md`, `PA_NOTES.md`, `SE_NOTES.md`.
`README.md` updated with a Docs table linking to each file with a description.

## Session — 1 May 2026 (Milestone 3 planning + deployment research)

**Milestone 3 patch produced**

The PA produced a patch for the Software Engineer containing three new files
in a new `deploy/` folder at the repo root:
- `docker-compose.yml` — production compose with named volumes, restart policies,
  host port binding (8080:8080), and a `build:` directive pointing at HeirloomsServer
- `.env.example` — credential template; the real `.env` is gitignored
- `README.md` — step-by-step setup guide for a VPS or home server

Key differences from the test compose:
- Credentials sourced from .env (not hardcoded)
- Named volumes: postgres_data, minio_data (data survives container restarts)
- `restart: unless-stopped` on postgres, minio, and heirloom-server
- Port 8080 bound to the host as 8080:8080
- `build:` context points to ../HeirloomsServer so `docker compose up --build`
  compiles and packages the JAR automatically

HeirloomsTest's docker-compose.yml was not modified.

---

**Deployment research — cloud and VPS options evaluated**

Google Cloud Run + Cloud SQL + Cloud Storage was evaluated as a cloud path.
Viable but Cloud SQL alone costs ~£10-15/month, which is disproportionate for
a personal project at this stage.

Hetzner CX22 (~€4/month) chosen as the recommended deployment target.
Runs the full stack on a single VPS via the Milestone 3 docker-compose.yml.

---

**Agreed next steps (queued for next session)**

1. Provision a Hetzner CX22 VPS
2. Add a DNS A record: `heirlooms.digital` → VPS IP (TTL 300)
3. SSH in, clone repo, copy `.env.example` to `.env`, fill in passwords
4. `docker compose up -d --build` from the `deploy/` folder
5. Verify: `curl http://heirlooms.digital:8080/health`
6. Update Android app endpoint to `http://heirlooms.digital:8080/api/content/upload`

HTTPS (via Caddy reverse proxy + Let's Encrypt) deferred to Milestone 4.

---

## Session — 2026-05-01 (Milestone 3 — self-hosted deployment)

**deploy/ folder added**

Three files added to a new `deploy/` folder at the repo root:
- `docker-compose.yml` — production compose with named volumes, restart policies,
  host port binding (8080:8080), and a `build:` directive pointing at HeirloomsServer
- `.env.example` — credential template; the real `.env` is gitignored
- `README.md` — step-by-step setup guide for a VPS or home server

Key differences from the test compose:
- Credentials sourced from .env (not hardcoded)
- Named volumes: postgres_data, minio_data (data survives container restarts)
- restart: unless-stopped on postgres, minio, and heirloom-server
- Port 8080 bound to the host as 8080:8080
- build: context points to ../HeirloomsServer so `docker compose up --build`
  compiles and packages the JAR automatically

HeirloomsTest's docker-compose.yml was not modified.

---

## Session — 2026-05-01 (Swagger UI / OpenAPI documentation)

**Prompt:** "Is it possible to add swagger to our backend server so I can access
detailed API documentation in a browser and use it as a simple client for our server?"

**What was built:** Interactive API documentation served at `GET /docs`, backed by
a fully self-hosted Swagger UI (no CDN dependency).

- Added `http4k-contract`, `http4k-format-jackson`, and `org.webjars:swagger-ui:5.11.8`
  to `build.gradle.kts`
- Converted `UploadHandler.kt` from plain `routes()` to http4k contract routing;
  the contract auto-generates and serves an OpenAPI 3.0 spec at
  `GET /api/content/openapi.json`
- Swagger UI assets served from the webjar on the classpath at `/docs/` via
  http4k's `static(ResourceLoader.Classpath(...))` handler
- `swagger-initializer.js` overridden via a specific route listed before the static
  handler, pointing Swagger UI at `/api/content/openapi.json`
- `GET /docs` redirects to `/docs/index.html`
- `Body.binary(ContentType("application/octet-stream")).toLens()` declared in the
  upload route meta so Swagger UI renders a file picker for the POST endpoint.
  Note: `binary` is an extension on `org.http4k.core.Body.Companion` from the
  `org.http4k.lens` package — `org.http4k.lens.Body` does not exist in http4k 4.46
- Updated `UploadHandlerTest.kt`: `GET /api/content/upload` now returns 404 (not 405)
  because http4k-contract does not produce METHOD_NOT_ALLOWED for wrong methods on
  contract-owned paths

**Key decision — CDN vs webjar:**
CDN approach (unpkg) was considered first — simpler, no extra dependency, but requires
the browser to have internet access. Webjar was preferred: all assets are bundled in the
fat JAR, the server is fully self-contained, and no internet access is needed at runtime.

**Files changed:**
- `HeirloomsServer/build.gradle.kts`
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/UploadHandler.kt`
- `HeirloomsServer/src/test/kotlin/digital/heirlooms/server/UploadHandlerTest.kt`
- `HeirloomsServer/README.md`
- `HeirloomsServer/PROMPT_LOG.md`
- `docs/SE_NOTES.md`

---

## Milestone 3 — 2026-05-05 (GCP deployment, GCS storage, API key auth, end-to-end test)

Full-stack milestone: server deployed to Google Cloud Run, storage migrated to GCS,
API key authentication added across server and Android app, end-to-end photo upload
confirmed from a real Android device.

**What was built:**

### HeirloomsServer

- **`GcsFileStore.kt`** — new `FileStore` implementation backed by Google Cloud Storage.
  Service account credentials are supplied as a JSON string via the `GCS_CREDENTIALS_JSON`
  environment variable and loaded in-memory; credentials are never written to disk.
  Activated by setting `STORAGE_BACKEND=GCS`, `GCS_BUCKET`, and `GCS_CREDENTIALS_JSON`.

- **Cloud SQL socket factory** — added `com.google.cloud.sql:postgres-socket-factory:1.19.0`
  dependency to support IAM-authenticated connections to Cloud SQL (PostgreSQL) via the
  Cloud SQL Auth Proxy socket.

- **`ApiKeyFilter.kt`** — http4k `Filter` that enforces `X-Api-Key` header authentication
  on all requests. `/health` is unconditionally exempt (required for Cloud Run health checks).
  Returns HTTP 401 for missing or incorrect keys. Key value read from the `API_KEY`
  environment variable via `AppConfig`. Filter is only wired in `Main.kt` when `apiKey`
  is non-empty, so local development works without a key.

### HeirloomsApp

- **`EndpointStore.kt`** — added `getApiKey()` / `setApiKey()` backed by SharedPreferences
  key `api_key`.
- **`Uploader.kt`** — added optional `apiKey: String?` parameter to `upload()`;
  injects `X-Api-Key` header when non-blank.
- **`SettingsActivity.kt` / `activity_settings.xml`** — added a masked password input
  field for the API key alongside the existing endpoint URL field.
- **`ShareActivity.kt`** — reads API key from `EndpointStore` and passes it to `upload()`.

### GCP infrastructure provisioned

- **Cloud Run** — HeirloomsServer deployed as a containerised service (Artifact Registry,
  Jib build)
- **Cloud SQL** — PostgreSQL instance, connected via Cloud SQL socket factory
- **Cloud Storage** — GCS bucket for file storage
- **Secret Manager** — secrets for API key and service account credentials
- **Service account** — created with roles scoped to Cloud SQL, GCS, and Secret Manager

### End-to-end validation

Photo uploaded from a physical Android device → Cloud Run endpoint → stored in GCS bucket.
Upload confirmed by checking the GCS bucket directly.

**Files changed/added:**
- `HeirloomsServer/build.gradle.kts` — GCS and Cloud SQL socket factory dependencies
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/GcsFileStore.kt` (new)
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/ApiKeyFilter.kt` (new)
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/AppConfig.kt` — GCS fields, `apiKey`
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/Main.kt` — GCS and filter wiring
- `HeirloomsApp/app/src/main/kotlin/digital/heirlooms/app/EndpointStore.kt`
- `HeirloomsApp/app/src/main/kotlin/digital/heirlooms/app/Uploader.kt`
- `HeirloomsApp/app/src/main/kotlin/digital/heirlooms/app/SettingsActivity.kt`
- `HeirloomsApp/app/src/main/kotlin/digital/heirlooms/app/ShareActivity.kt`
- `HeirloomsApp/app/src/main/res/layout/activity_settings.xml`
- `HeirloomsApp/app/src/main/res/values/strings.xml`

---

## Session — 2026-05-05 (Swagger UI — API key auth integration)

**Prompt:** "When I access the swagger UI using the Cloud Run URL /docs I get
unauthorised. The security filter is kicking in. We need to fix the filter so
it excludes the docs path, and add an Authorize mechanism in Swagger UI for
the API key."

**What was built:**

- **`ApiKeyFilter.kt`** — added `path.startsWith("/docs")` exemption so the
  Swagger UI and all its static assets load without credentials.

- **`/docs/api.json` route** — new handler (`specWithApiKeyAuth`) that calls
  the http4k contract internally to get the raw OpenAPI spec, then patches it
  with Jackson before returning it:
  - Adds `components.securitySchemes.ApiKeyAuth` (`type: apiKey`, `in: header`,
    `name: X-Api-Key`)
  - Adds a global `security: [{ApiKeyAuth: []}]` block
  - Overrides `servers` to `[{url: "/api/content"}]` (http4k generates `"/"` which
    caused Swagger UI to POST to `/upload` instead of `/api/content/upload`)
  - Removes per-operation `security: []` entries — an empty array overrides the
    global block, so Swagger UI was silently dropping the key after re-authorisation

- **`swaggerInitializerJs`** updated:
  - `url` changed from `/api/content/openapi.json` to `/docs/api.json`
    (the patched spec endpoint, already exempt from the filter)
  - `persistAuthorization: true` — key survives page refresh
  - `tryItOutEnabled: true` — request form open by default, no extra click

- **`docker-compose.yml`** (test) — added `API_KEY: "${API_KEY:-}"` so the key
  can be injected for manual local testing without breaking the e2e tests
  (which run without a key and rely on the filter being inactive).

**Key gotcha — per-operation `security: []`:**
http4k generates `"security": []` on every operation when no contract-level
security is configured. In OpenAPI 3, an empty array means "no security" and
overrides the global block. This caused Swagger UI to stop sending the key after
logout and re-authorisation, despite the Authorize dialog appearing to work.
Fix: remove the per-operation `security` field so operations inherit global.

**Files changed:**
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/ApiKeyFilter.kt`
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/UploadHandler.kt`
- `HeirloomsTest/src/test/resources/docker-compose.yml`

**Validated end-to-end:**
Deployed to Cloud Run (revision `heirlooms-server-00006-ckl`). Swagger UI loads at
`https://heirlooms-server-340655233963.europe-west2.run.app/docs` without credentials,
API key authorisation works via the Authorize button, and POST /upload returns 201.
Tagged as **v0.6.0**.

---

## Milestone 4 — Web gallery UI + large file support (6 May 2026)

### Part 1 — File proxy endpoint + HeirloomsWeb

**Prompt:** Build Milestone 4: a file proxy endpoint on HeirloomsServer and a new HeirloomsWeb sub-project (React gallery).

**What was built:**

*HeirloomsServer:*
- `FileStore.get(key)` added to interface; implemented in LocalFileStore, S3FileStore, GcsFileStore
- `GET /api/content/uploads/{id}/file` — streams file bytes from GCS with correct Content-Type; 404 if not found
- `uploadedAt: Instant` added to UploadRecord; list endpoint JSON now includes it
- CORS filter added (all origins); handles OPTIONS preflight before ApiKeyFilter

*HeirloomsWeb (new sub-project):*
- React 18 + Tailwind CSS + Vite; gallery grid with image thumbnails, file icons for videos, upload date, MIME type, file size; lightbox on click
- API key entered at login per session, held in React state only (cleared on reload, never stored)
- Images fetched as blob URLs (fetch + createObjectURL) so X-Api-Key header can be sent
- Multi-stage Dockerfile: Node 22 build → nginx:alpine

Deployed to Cloud Run (revision `heirlooms-server-00007-7vw`). Gallery confirmed working at http://localhost:5173 against production server. Tagged as **v0.7.0**.

---

### Part 2 — Large file upload via GCS signed URLs

**Problem discovered:** Uploading a 34.57 MB video from the Android app returns HTTP 413. Root cause: Cloud Run enforces a hard 32 MB request body limit at the load balancer level — no server-side config change can fix this.

**Solution — three-step signed URL upload flow:**

1. Mobile app `POST /api/content/uploads/prepare` with `{"mimeType":"video/mp4"}` → server returns `{"storageKey":"uuid.mp4","uploadUrl":"https://...signed-gcs-url..."}` (15-minute expiry)
2. Mobile app `PUT {signedUrl}` with file bytes **directly to GCS** — bypasses Cloud Run entirely, no size limit
3. Mobile app `POST /api/content/uploads/confirm` with `{"storageKey":"...","mimeType":"...","fileSize":...}` → server records metadata in the database

**Server changes:**
- `DirectUploadSupport` interface + `PreparedUpload` data class (new file)
- `GcsFileStore` now implements `DirectUploadSupport`; switched from `GoogleCredentials` to `ServiceAccountCredentials` so the credentials can sign URLs (V4 signing); `prepareUpload()` generates a signed PUT URL with 15-minute expiry
- `POST /api/content/uploads/prepare` and `POST /api/content/uploads/confirm` added as contract routes; prepare returns 501 if the storage backend doesn't support direct upload (i.e. local/S3)

**Android app changes:**
- `Uploader.uploadViaSigned()` — new method implementing the three-step flow; no API key sent on the GCS PUT (signed URL is self-authenticating)
- `ShareActivity` now calls `uploadViaSigned()` instead of `upload()`; derives base URL from stored endpoint by splitting on `/api/`
- OkHttp write timeout increased from 120s → 300s to accommodate large video uploads

**Note for deployment:** The new server image must be built and deployed to Cloud Run before large video uploads will work. The existing `POST /api/content/upload` direct endpoint still works for small files. No change to stored endpoint format in the Android app.

**Validated end-to-end (6 May 2026):**
Server deployed to Cloud Run (revision `heirlooms-server-00008-vt7`). Fresh APK installed via `adb install -r`. Large video (34.57 MB) shared successfully from Android — three-step signed URL flow completed transparently. Tagged as **v0.8.0**.

---

## Session summary — 6 May 2026 (continued from Milestone 4)

### Video player + streaming (v0.9.0)

**Video player:** HeirloomsWeb now shows a video icon with "Click to play" for video files in the gallery. Clicking opens a native `<video controls>` modal.

**Streaming:** Initial implementation fetched the full video as a blob before playback (slow for large files). Replaced with GCS signed read URLs — a new `GET /api/content/uploads/{id}/url` endpoint generates a 1-hour signed URL; the video element uses it as `src` directly. The browser handles streaming, buffering, and seeking natively. No full download required.

**Dockerfile fix:** Docker Desktop on macOS was dropping the build daemon connection during long Gradle downloads inside the container, requiring a manual Docker restart every deployment. Fixed by removing the multi-stage build: JAR is now built locally with `./gradlew shadowJar` first, then `docker build` simply copies the pre-built JAR into a JRE image. Build time: ~2 seconds. PA_NOTES.md updated with the new deploy sequence.

**Validated end-to-end:** Video streaming confirmed working. Server deployed to Cloud Run revision `heirlooms-server-00009-58m`. Tagged as **v0.9.0**.

---

## Session — 2026-05-06 (All endpoints in Swagger)

`GET /uploads/{id}/file` and `GET /uploads/{id}/url` were registered directly
in `routes()`, making them invisible to the http4k contract and Swagger UI.

**Fix:** converted both to `ContractRoute` entries inside the contract.

Key discovery: `String.div(PathLens<A>)` is a top-level extension in
`org.http4k.contract.ExtensionsKt` and requires `import org.http4k.contract.div`.
Without this import, the `/` operator was unresolved and the meta block failed
with cascade errors.

`ContractRouteSpec1<A>.div(String)` (member method) returns
`ContractRouteSpec2<A, String>`, whose `Binder.to` takes `(A, String) -> HttpHandler`.
The trailing String parameter is the constant path segment ("file"/"url") and is
ignored with `_` in the handler.

String-based paths like `"/uploads/{id}/file"` in contract routes do NOT do
path variable matching — they are treated as literal strings, returning 404 for
all real UUID paths. Typed path lenses are required for routing to work.

A malformed UUID in the path returns 404 (route doesn't match) not 400
(the typed lens fails silently and falls through to the 404 handler).

**Files changed:**
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/UploadHandler.kt`
- `HeirloomsServer/src/test/kotlin/digital/heirlooms/server/UploadHandlerTest.kt`

---

## Session — 2026-05-06 (Hardcode server URL in Android app)

Now that the app targets `https://api.heirlooms.digital` exclusively, the endpoint
URL is no longer user-configurable. The settings screen is reduced to API key only.

**Changes:**

- `EndpointStore.kt` — removed `get()`, `set()`, `isConfigured()`, `DEFAULT_ENDPOINT`,
  and `KEY_ENDPOINT`. Removed `contains()` from `PreferenceStore` interface (was only
  needed by `isConfigured()`). Class now persists the API key only.
- `ShareActivity.kt` — replaced the stored endpoint + `baseUrl` derivation with a
  hardcoded `val baseUrl = "https://api.heirlooms.digital"`.
- `SettingsActivity.kt` — removed endpoint `EditText` and URL validation; screen now
  shows API key field only.
- `activity_settings.xml` — removed endpoint label, input, and help text views;
  API key section anchored directly below the title.
- `strings.xml` — removed `settings_endpoint_label`, `settings_endpoint_hint`,
  `settings_help`, `settings_invalid_url`; updated `settings_saved` to "Settings saved".
- `EndpointStoreTest.kt` — replaced endpoint tests with equivalent API key tests
  (5 tests, all passing).

---

## Session wrap-up — 2026-05-06 (v0.10.0)

**Validated end-to-end:** Upload from Android confirmed working via
`https://api.heirlooms.digital`. Swagger UI confirmed at
`https://api.heirlooms.digital/docs/index.html`. All 6 endpoints visible.

**Cloud Run:** server deployed as revision `heirlooms-server-00002-stq`
(us-central1). Tagged as **v0.10.0**.

---

## Session — 2026-05-06 (Phase 1 thumbnail generation)

**Prompt:** Add synchronous image thumbnail generation at upload time —
Phase 1 of a three-phase pipeline (Phase 2: video first-frame via FFmpeg,
Phase 3: async generation).

**What was built:**

### Database
- `V3__add_thumbnail_key.sql` — adds nullable `thumbnail_key VARCHAR(512)`
  column to the uploads table. Nullable because existing uploads have none,
  non-image files never get one, and generation can fail silently.

### ThumbnailGenerator
- `ThumbnailGenerator.kt` — top-level `generateThumbnail(bytes, mimeType)`
  function using only `javax.imageio.ImageIO` and `java.awt` (no extra
  dependencies). Scales to fit a 400×400 bounding box preserving aspect
  ratio, outputs JPEG. Returns null for unsupported types (everything except
  image/jpeg, image/png, image/gif, image/webp) or if ImageIO can't decode
  the input. Try/catch ensures thumbnail failure never propagates to the
  upload response.

### FileStore — saveWithKey
- `FileStore.saveWithKey(bytes, key, mimeType)` added to the interface.
  Implemented in `LocalFileStore`, `S3FileStore`, and `GcsFileStore`.
  Used to store thumbnails under an explicit key (`{uuid}-thumb.jpg`)
  alongside the original file.

### Database — thumbnailKey
- `UploadRecord` gains `thumbnailKey: String? = null` (trailing default,
  backward compatible).
- All INSERT and SELECT queries updated to include `thumbnail_key`.

### Upload flow
- `buildApp` gains an injectable `thumbnailGenerator` parameter (default
  `::generateThumbnail`) so tests can inject a stub or failing lambda.
- `POST /upload` (direct path): after storing the original, calls
  `tryStoreThumbnail` to generate + store the thumbnail, then records
  `thumbnailKey` in the database. On any failure, proceeds without thumbnail.
- `POST /uploads/confirm` (signed URL path): calls
  `tryFetchAndStoreThumbnail`, which first checks if the MIME type is
  supported (skip early for videos, avoiding a wasteful GCS fetch) then
  fetches bytes, generates, and stores the thumbnail. On any failure,
  proceeds without thumbnail.
- Thumbnail stored under `{original-uuid}-thumb.jpg` in the same bucket.

### API changes
- `GET /api/content/uploads` — list JSON now includes `"thumbnailKey":null`
  or `"thumbnailKey":"uuid-thumb.jpg"` on each item.
- `GET /api/content/uploads/{id}/thumb` — new contract route. Returns the
  JPEG thumbnail if `thumbnailKey` is set; falls back to the full file if
  not. Returns 404 if the upload record doesn't exist.

### HeirloomsWeb
- `UploadCard` uses `GET /uploads/{id}/thumb` when `upload.thumbnailKey` is
  non-null (fetching the smaller thumbnail for the grid), falling back to
  `GET /uploads/{id}/file` for uploads without a thumbnail.

### Tests
- `ThumbnailGeneratorTest.kt` (8 tests): supported JPEG returns non-null,
  output is valid JPEG, unsupported type returns null, invalid bytes returns
  null, fits within 400×400, preserves aspect ratio, no upscaling for small
  images, octet-stream returns null.
- `UploadHandlerTest.kt` (11 new tests): thumbnail generated and stored for
  supported type, no thumbnail for video/mp4, upload succeeds when generator
  throws, thumbnailKey null in list for non-image, thumbnailKey present in
  list, thumb endpoint returns thumbnail bytes, thumb endpoint falls back to
  full file, thumb endpoint returns 404.

All tests passing (95 total across 6 test classes, 0 failures).

**Cloud Run:** server deployed as revision `heirlooms-server-00007-gvl`,
web as revision `heirlooms-web-00002-jjr` (us-central1). Tagged as **v0.12.0**.

---

## Session — 2026-05-06 (POST /upload JSON response)

**Fix:** `POST /upload` 201 response was returning a raw storage key string
(`780aa0d2-fd28-4ad0-8c6d-e3aec4d30fa3.jpg`). Changed to return a full JSON
object matching the shape of items in the `GET /uploads` list:
`{"id":"...","storageKey":"...","mimeType":"...","fileSize":...,"uploadedAt":"...","thumbnailKey":...}`.

`uploadedAt` is captured at the point of the `save()` call in Kotlin (very
close to the DB `DEFAULT NOW()` value — the column is not included in the
INSERT so the DB sets it independently). `Content-Type: application/json`
header added to the 201 response. All 95 tests still passing.

**Cloud Run:** server deployed as revision `heirlooms-server-00008-kdz`.

---

## Session — 2026-05-06 (Phase 2 thumbnails — video first-frame via FFmpeg)

**Prompt:** Extend the thumbnail pipeline to support video files. Add FFmpeg to the Docker image, extend `ThumbnailGenerator` to extract the first frame from video/mp4, video/quicktime, video/x-msvideo, and video/webm using FFmpeg via `ProcessBuilder`, and add tests.

**What was built:**

- **`Dockerfile`** — `apt-get install -y ffmpeg` added before `USER heirloom` (runs as root).

- **`ThumbnailGenerator.kt`** — dispatches to `extractVideoThumbnail` for video MIME types. Writes video bytes to a temp file, runs `ffmpeg -vframes 1 -f image2 output.jpg` via `ProcessBuilder` with a 30-second timeout, reads the output JPEG, scales via the shared `scaleAndEncode` helper. All failures return null gracefully and temp files are always cleaned up in `finally`.

- **`THUMBNAIL_SUPPORTED_MIME_TYPES`** now includes the four video types, so the confirm-flow's `tryFetchAndStoreThumbnail` no longer skips them.

- **Tests (ThumbnailGeneratorTest):** 2 new — `valid MP4 produces non-null thumbnail` (uses `assumeTrue(isFFmpegAvailable())` to skip gracefully when FFmpeg is absent) and `corrupt video returns null gracefully` (always runs).

- **Test adjustment:** `returns null for unsupported MIME type` updated from `video/mp4` (now supported) to `audio/mpeg`. `no thumbnail generated for unsupported MIME type` in `UploadHandlerTest` renamed to `no thumbnail stored when video bytes are invalid`.

**Result:** 97 tests, 0 failures, 1 skipped locally (valid-MP4 test runs in Docker where FFmpeg is installed).

**Deployed:** Cloud Run revision `heirlooms-server-00009-gdv`. Health check confirmed `ok`. Tagged as **v0.13.0**.

---

## Session — 2026-05-06 (Web gallery — video thumbnails)

**Prompt:** Update HeirloomsWeb to use the Phase 2 video thumbnails.

**What was built:**

`UploadCard` previously ignored `thumbnailKey` for video files, always showing a generic
video icon. Now:

- Videos with a `thumbnailKey` pre-fetch the JPEG thumbnail (via the same `/thumb` endpoint
  used for images) and display it in the card with a semi-transparent play-button overlay.
  Clicking the card still opens the video via the signed read URL.
- Videos without a `thumbnailKey` keep the existing `VideoIcon` + "Click to play" behaviour.
  While a thumbnail is loading, a spinner is shown.
- Added `PlayIcon` component (circular button, 48×48, play arrow).

**Files changed:**
- `HeirloomsWeb/src/App.jsx`

**Deployed:** Cloud Run revision `heirlooms-web-00003-4nx`.

---

## Session — 2026-05-06 (EXIF and video metadata extraction)

**Prompt:** Add EXIF and video metadata extraction to HeirloomsServer. Metadata extracted at upload time alongside thumbnail generation and stored in six new nullable database columns (captured_at, latitude, longitude, altitude, device_make, device_model). GPS pin icon in HeirloomsWeb for cards with coordinates.

**What was built:**

### HeirloomsServer
- **`V4__add_metadata_columns.sql`** — adds six nullable columns to the uploads table.
- **`MetadataExtractor.kt`** (new) — `MediaMetadata` data class; `MetadataExtractor` class with `extract(bytes, mimeType): MediaMetadata`. Image path uses `com.drewnoakes:metadata-extractor:2.19.0` for EXIF GPS (lat/lon/alt), capture timestamp, and device make/model. Video path runs `ffprobe -v quiet -print_format json` and parses `format.tags.creation_time`, ISO 6709 location string, and Apple QuickTime make/model tags. All failures return `MediaMetadata()` with all nulls.
- **`UploadRecord`** — six new nullable fields.
- **`Database.kt`** — all INSERT and SELECT queries updated. `ResultSet.toUploadRecord()` private extension eliminates duplicated mapping code.
- **`UploadHandler.kt`** — `buildApp` gains `metadataExtractor` parameter (default `MetadataExtractor()::extract`). Direct upload path calls metadata extraction on the request bytes. Confirm path refactored to call `fetchBytesIfNeeded` once, passing bytes to both `tryStoreThumbnail` and `metadataExtractor` (single GCS fetch instead of two). `UploadRecord.toJson()` private extension: metadata fields omitted when null; used in both upload and list handlers.

### HeirloomsWeb
- **`App.jsx`** — `PinIcon` component (📍 with lat/lon tooltip). `UploadCard` outer div gains `relative` class; pin shown when both `latitude` and `longitude` are non-null.

### Tests
- `MetadataExtractorTest` (4 tests): GPS JPEG with hand-crafted TIFF/EXIF bytes returns correct lat/lon/alt; plain JPEG returns null coords; unsupported MIME type returns all nulls; invalid bytes return null.
- `UploadHandlerTest`: 1 new test (metadata exception does not fail upload); three confirm-flow tests updated to stub `storage.get()`.
- **102 tests total, 101 passing, 1 skipped** (FFmpeg video thumbnail test — runs in Docker).

**Key gotchas:**
- Adding `metadataExtractor` as the last `buildApp` parameter broke existing tests that used trailing lambda syntax for `thumbnailGenerator`. Fixed by using named parameter syntax throughout.
- Confirm path previously fetched bytes inside `tryFetchAndStoreThumbnail`. Refactored to fetch once and share with metadata extraction.

---

## Session — 2026-05-06 (Metadata extraction debugging and stabilisation)

**Context:** Follow-on to the metadata extraction session. End-to-end testing with a real Samsung Galaxy A02s revealed several issues that were diagnosed and fixed iteratively.

**Issues found and fixed:**

### capturedAt missing on Samsung
Samsung writes `DateTime` to `ExifIFD0Directory` rather than `DateTimeOriginal` in `ExifSubIFDDirectory`. Added two fallbacks in `extractCapturedAt()`: IFD0 DateTime, then SubIFD DateTimeDigitized. Deployed as `heirlooms-server-00011-gbq`.

### GPS returning (0, 0)
Samsung entry-level cameras write GPS IFD tags with zero values when the GPS fix hasn't been acquired at shutter time. The library parsed them as `GeoLocation(0.0, 0.0)` rather than null. Added a filter: if both lat and lon are exactly 0.0, treat as null. Deployed as `heirlooms-server-00012-6ll`.

### OutOfMemoryError on large image uploads
Cloud Run default 512Mi heap was exhausted when loading a 5.4 MB photo: GCS `readAllBytes()` (5.4 MB) + `BufferedImage` decode (4160×3120 = ~52 MB) + JVM overhead. Two fixes: (1) increased Cloud Run memory to 2Gi; (2) metadata extraction in the confirm path now calls `GcsFileStore.getFirst()` which streams only the first 64 KB from GCS via `Storage.reader()` ReadChannel — JPEG EXIF is always within that range. Thumbnails still fetch the full file. Deployed as `heirlooms-server-00014-97p`.

### No metadata at all (mimeType: "image/*")
Samsung Gallery provides `intent.type = "image/*"` (wildcard) in the share intent. The app was using this directly, so uploads were stored as `.bin` with MIME type `image/*`, which is not in the metadata or thumbnail supported sets. Fixed by skipping wildcards and falling back to `contentResolver.getType(uri)` for the real specific type. Installed via ADB.

### Silent upload failure (SecurityException not caught)
`MediaStore.setRequireOriginal()` requires `ACCESS_MEDIA_LOCATION`. When denied, `openInputStream()` on the original URI threw `SecurityException`, which propagated uncaught through `catch (e: IOException)` and silently killed the coroutine. Fixed: (1) `readBytes()` wraps the entire `setRequireOriginal` + `openInputStream` in a single try/catch and falls back to the plain URI; (2) catch block in `ShareActivity` changed from `IOException` to `Exception`.

**End state:** Photo shared from Samsung Galaxy A02s → full metadata response including `capturedAt`, `latitude`, `longitude`, `deviceMake`, `deviceModel`. Coordinates confirmed real (East Midlands, UK). GPS pin 📍 visible in web gallery.

**Android gotchas for future reference:**
- `ACCESS_MEDIA_LOCATION` must be declared in manifest AND requested at runtime AND `setRequireOriginal()` must be called — three separate requirements
- Samsung Galaxy shares with wildcard MIME types
- Notification channel importance is immutable after first creation — bump the channel ID to change it
- Samsung Camera "Location tags" toggle (in Camera Settings) is separate from the system Location permission

---

## Session — 2026-05-06 (Image rotation)

**Prompt:** Add the ability to rotate images 90° in the web gallery. Rotation persists and applies to both thumbnail and lightbox view.

**What was built:**

- **`V5__add_rotation.sql`** — `rotation INT NOT NULL DEFAULT 0` on uploads table
- **`Database.updateRotation(id, rotation)`** — UPDATE statement; `rotation` added to all SELECT queries and `UploadRecord`
- **`PATCH /api/content/uploads/{id}/rotation`** — new contract route accepting `{"rotation":0|90|180|270}`; returns 400 for invalid values, 404 if upload not found
- **`UploadRecord.toJson()`** — `rotation` always included (even 0)
- **HeirloomsWeb** — `RotateIcon` component; ↻ button in each image card's info row; `handleRotate` in Gallery with optimistic state update + fire-and-forget PATCH call; CSS `transform: rotate(Xdeg)` on thumbnail image with `overflow-hidden` container clipping; `Lightbox` accepts `rotation` prop and swaps `max-w`/`max-h` at 90°/270° so portrait-rotated images fill the viewport; `lightboxUrl` state replaced with `lightbox: {url, rotation}` object
- **5 new tests** in `UploadHandlerTest`: valid rotation returns 200 + verifies DB call, invalid rotation returns 400, upload not found returns 404, rotation field in list response, rotation defaults to 0

**107 tests total, 106 passing, 1 skipped** (FFmpeg video thumbnail — passes in Docker).

---

## Session — 2026-05-06 (Tags — Increment 1: schema + write API)

**Prompt:** Add tag support to HeirloomsServer. New Flyway V6 migration adds a `tags TEXT[] NOT NULL DEFAULT '{}'` column to the uploads table with a GIN index. New `PATCH /api/content/uploads/{id}/tags` endpoint accepts `{"tags":["family","2026-summer"]}` with full-replace semantics, validates each tag against `^[a-z0-9]+(-[a-z0-9]+)*$` with length 1–50, and returns 400 naming the offending tag on failure or 404 if the upload doesn't exist. Tags appear in all upload JSON responses (`POST /upload`, `GET /uploads`, `GET /uploads/{id}`) as a `tags` array, always present, empty when none. Mirror the existing rotation endpoint's structure (added in v0.15.0).

**What was built:**

- **`V6__add_tags.sql`** — `tags TEXT[] NOT NULL DEFAULT '{}'` on uploads table plus `CREATE INDEX idx_uploads_tags ON uploads USING GIN (tags)`
- **`TagValidator.kt`** — `validateTags(tags)` enforces kebab-case (`^[a-z0-9]+(-[a-z0-9]+)*$`), length 1–50, with specific rejection reasons per tag; sealed `TagValidationResult` (Valid / Invalid(tag, reason))
- **`Database.updateTags(id, tags): Boolean`** — UPDATE via JDBC `createArrayOf("text", ...)`, returns false if no row matched
- **`tags` added to `UploadRecord`** — `List<String> = emptyList()`, all SELECT queries include the column, `toUploadRecord()` reads via `getArray("tags")`
- **`PATCH /api/content/uploads/{id}/tags`** — full-replace semantics; 400 on malformed JSON or invalid tag (offending tag + reason in response body); 404 if upload not found; 200 with full updated UploadRecord JSON on success
- **`UploadRecord.toJson()`** — `tags` always included, empty array when none
- **14 new tests** in `TagValidatorTest` (unit), **8 new tests** in `UploadHandlerTest` (integration)

**129 tests total, 128 passing, 1 skipped** (FFmpeg video thumbnail — passes in Docker).

**Notes for future increments:**
- Increment 2 (read API + filtering) will use the GIN index for `tag` and `exclude_tag` query params on `GET /uploads`
- Increment 3 (web UI) will surface tags as chips and an inline editor
- Tag rename, merge, colours, and Android tagging are all out of scope and remain parked in IDEAS.md

**v0.16.0 not yet tagged** — releasing once all three increments land.

---

## Session — 2026-05-06 (Tags — Increment 2: read API + filtering)

**Prompt:** Add `tag` and `exclude_tag` query parameters to `GET /uploads` so the list can be filtered by tag using the GIN index added in Increment 1.

**What was built:**

- **`Database.listUploads(tag, excludeTag)`** — optional parameters; builds a dynamic WHERE clause using `tags @> ARRAY[?]::text[]` (GIN-indexed) and `NOT (tags @> ARRAY[?]::text[])` for inclusion/exclusion; no WHERE clause when both are null (unchanged behaviour)
- **`GET /uploads?tag=family`** — returns only uploads that have this tag
- **`GET /uploads?exclude_tag=trash`** — omits uploads that have this tag
- Both params can be combined in a single request
- Updated `listUploadsContractRoute` description to document the new params
- 5 new tests in `UploadHandlerTest` covering: tag filter, exclude_tag filter, both combined, unknown tag returns empty array, no params passes nulls

**134 tests total, 133 passing, 1 skipped** (FFmpeg).

**Cloud Run:** deployed as revision `heirlooms-server-00018-w2g`.

**v0.16.0 not yet tagged** — releasing once Increment 3 (web UI) also lands.

---

## Session — 2026-05-06 (Tags — Increment 3: web UI)

**Prompt:** Surface tags in the web gallery as chips on each card, with an inline editor backed by an autocomplete dropdown of previously used tags.

**What was built:**

- **`TagEditor` component** — removable chips per selected tag (× to remove, Backspace removes last), text input with autocomplete dropdown filtered from `allTags`, Enter or Save commits; pending input text is flushed into the tag list on Save so typing a tag and clicking Save directly works without pressing Enter first
- **`allTags`** — derived in `Gallery` via `useMemo` over all uploads, sorted, passed down to each `UploadCard`; automatically includes newly saved tags after a successful PATCH
- **Tag chips** — shown in display mode below card metadata; hidden when no tags
- **`TagIcon` SVG** — added to card header row next to rotate button; highlighted when editor is open
- **`overflow-hidden` fix** — moved from outer card div to image container (`rounded-t-xl overflow-hidden`) so the dropdown is not clipped by the card boundary
- **OpenAPI body spec** — `RotationRequest` and `TagsRequest` data classes made non-private (were `private`, causing `IllegalAccessException` in Jackson schema generator and 500s on `/docs/api.json`); `receiving(lens to example)` added to both PATCH endpoints; spec endpoint test added as a permanent regression guard
- **CORS/Swagger fix for example bodies** — examples surface in Swagger UI for both PATCH endpoints
- **Tag filtering** (Increment 2 addition) — `GET /uploads?tag=X` and `GET /uploads?exclude_tag=X` use `tags @> ARRAY[?]::text[]` against the GIN index; `.env` updated to `https://api.heirlooms.digital`

**Cloud Run:** latest revision `heirlooms-server-00021-fqb`.

**v0.16.0 not yet tagged** — releasing once all three increments are confirmed working end-to-end.

---

## Session — 2026-05-08 (v0.17.1 — share-sheet Idle state)

Added pre-upload Idle state to the Android share-sheet receive screen. When
`ShareActivity` receives a share intent it now lands in `ReceiveState.Idle`
(a new data class carrying the photo URIs, in-progress tags, current tag
input, and recent-tag list) instead of jumping straight to *Uploading*.

`IdleScreen.kt` renders: *Heirlooms* wordmark header, photo grid (1–6) or
thumbnail strip + count (7+), tag input with kebab-case validation (Earth
underline + inline italic message for invalid input, no glyphs), recent-tag
chips backed by `RecentTagsStore` (SharedPreferences, last 12 tags, updated
only on successful upload), forest *plant* pill button, ghost *cancel* button.

Tags wired through to the server: `UploadWorker` reads them from work data,
`Uploader.uploadViaSigned(file)` includes them in the confirm body, and
`confirmUploadHandler` validates and records them via `database.updateTags`.

6 new tests (4 `IdleScreenTest` Compose UI + 2 `TagValidationTest` unit).
Coil 2.5.0 added for photo preview rendering.

---

## Session — 2026-05-07 (v0.16.1 — video upload OOM fix + tag dropdown UX)

**Android: video upload OOM fix**

Root cause identified via ADB logcat: `UploadWorker` called `file.readBytes()` before `uploadViaSigned`, loading the entire video into the Java heap (201 MB growth limit on Samsung Galaxy A02s). When OkHttp then tried to allocate an 8 KB Okio buffer segment to begin the GCS PUT, the heap was exhausted — `OutOfMemoryError: Failed to allocate a 8208 byte allocation with 55224 free bytes`. Because `OutOfMemoryError` is a `java.lang.Error` (not `Exception`), the `catch (_: Exception)` in `UploadWorker` did not catch it: the coroutine crashed silently, the GCS PUT never completed, no DB record was created, and no result notification appeared.

Fix: new `Uploader.uploadViaSigned(File, ...)` overload that streams the video from disk to GCS directly using `file.asRequestBody()` / `ProgressFileRequestBody`, never loading the full content into memory. SHA-256 computed by reading the file in 8 KB chunks. `UploadWorker` now passes the `File` object directly, removing `readBytes()` entirely. Confirmed working: 1-minute Samsung video (~90 MB) uploaded in ~8 min 46 s on slow home WiFi.

**Web: tag dropdown stays open after selection**

The tag editor dropdown was closing after each selection, requiring the user to click away and back to reopen it. Three approaches were tried before finding a reliable fix:

1. Removed `setDropdownOpen(false)` from `addTag` — failed because `onBlur` was still firing and closing the dropdown.
2. Used `e.relatedTarget` in `onBlur` to detect intra-dropdown clicks — failed because Safari on macOS does not focus `<button>` elements on click, so `e.relatedTarget` was null.
3. `suppressBlurRef`: the dropdown container's `onMouseDown` sets a ref flag before `onBlur` fires on the input; `onBlur` skips the close while the flag is set; `onMouseUp` clears it. This is browser-agnostic. ✓

**Cloud Run:** `heirlooms-web-00008-9qv` (web), server unchanged at `heirlooms-server-00021-fqb`.

**v0.16.1 not yet tagged** — Bret tags after push.

---

## Session — 2026-05-08 (Milestone 6 D2 — backend + Explore basic)

**What was built.** D2 of Milestone 6: schema foundations (V9/V10 migrations), EXIF
recovery service, cursor pagination on list endpoints, plot CRUD (4 endpoints), and the
/explore page with nav entry.

**2A — EXIF recovery.** The server already extracted EXIF inline at upload time and
stored it in the existing `captured_at`, `latitude`, `longitude` etc. columns. D2 adds
a single new column: `exif_processed_at TIMESTAMPTZ`. Set to `NOW()` at INSERT time for
new uploads. Migration marks all pre-existing rows as processed. `ExifExtractionService`
is a Kotlin coroutine-based recovery service — on startup it queries `WHERE
exif_processed_at IS NULL` and re-processes any stranded rows. In normal operation this
should find nothing; it's a crash-recovery safety net. `kotlinx-coroutines-core:1.7.3`
added as a dependency.

**2B — Cursor pagination.** Both `GET /api/content/uploads` and `GET
/api/content/uploads/composted` now return `{"items":[...],"next_cursor":"..."|null}`.
Cursor encodes `(uploadedAt.epochMilli, id)` as URL-safe base64. The DB query uses a
compound `(uploaded_at < ? OR (uploaded_at = ? AND id < ?::uuid))` predicate. Limit
param: 1–200, default 50. Garden and CompostHeap updated to call `?limit=10000` and read
`data.items` — unchanged single-page behaviour for D2, proper restructure in D3.

**2C — Plot schema + CRUD.** V10 creates `plots` and `plot_tag_criteria` tables. System
*Just arrived* plot seeded with sentinel name `__just_arrived__`, sort_order -1000,
`is_system_defined = TRUE`. `owner_user_id = NULL` for all v1 plots (FK + NOT NULL at
M8). Four endpoints: GET list, POST create, PUT update (403 on system plots), DELETE
(403 on system plots). Routes added to the capsule contract block under `/api`.

**Web — /explore.** `ExplorePage` renders a 5-column paginated photo grid via the
existing `PhotoGrid` component. *Load more* button (not infinite scroll — simpler,
sufficient for v1). Empty state. Nav updated: Garden | Explore | Capsules desktop + mobile.

**Surprises / decisions:**
- `MetadataExtractor` and all EXIF extraction was already complete from earlier milestones;
  D2 just adds the tracking column and recovery service. No rewrite needed.
- `listUploads(null, null)` mocks in UploadHandlerTest needed updating to
  `listUploadsPaginated(any(), any(), any(), any())` returning `UploadPage(...)`. 13 mock
  calls updated; verify calls updated to match paginated signature.
- `capture()` with nullable `String?` type required `captureNullable(slot)` not
  `capture(mutableListOf<String?>())` — mockk type inference issue with nullables.

**Test counts:** 21 new backend (8 pagination, 13 plot); 6 new web (explore); 3 existing
compost tests updated for new response format. All 169 backend + 65 web tests pass.

**Docs:** VERSIONS.md (v0.23.0), ROADMAP.md (D2 marked done), PA_NOTES.md (two
architectural notes: EXIF in-process pattern, owner_user_id sentinel), PROMPT_LOG.md.

---

## Session — 2026-05-08 (Milestone 6 D1 — re-import utility)

**What was built.** A standalone Gradle subproject at `tools/reimport/` implementing
the M6 D1 re-import utility described in the D1 brief.

**`tools/reimport/` subproject structure:**
- `build.gradle.kts` / `settings.gradle.kts` — standalone Gradle project (Kotlin JVM 21,
  same GCS/Postgres/HikariCP versions as HeirloomsServer).
- `BucketReader.kt` — `BucketReader` interface + `GcsObject` data class. Thin abstraction
  over the GCS listing API; makes unit/integration tests independent of the real GCS client.
- `GcsBucketReader.kt` — real GCS implementation. Paginated listing via `storage.list()`.
  Supports service account JSON (`GCS_CREDENTIALS_JSON`) or ADC fallback.
- `Importer.kt` — core logic: import phase (scan, filter, dedup, insert) + verify phase
  (count parity + sample integrity). SHA-256 computed by downloading each object during
  import — correct for a small bucket; acceptable for recovery use.
- `Main.kt` — entry point; wires config → GcsBucketReader → HikariDataSource → Importer;
  prints final summary with warnings if parity or integrity checks fail.
- `ReimportConfig.kt` — config loaded from `DB_URL`, `DB_USER`, `DB_PASSWORD`, `GCS_BUCKET`,
  `GCS_CREDENTIALS_JSON` env vars.

**Schema note.** The actual DB column is `storage_key` (not `gcs_key` as the brief draft
called it). Dedup check and INSERT use `storage_key` throughout.

**Test count: 16 tests** (8 unit, 8 integration). Unit tests cover content-type filter,
sha256Hex, and ImportSummary counting via a fake BucketReader (no DB needed). Integration
tests use Testcontainers Postgres and a fake BucketReader; they cover: image/video import,
idempotency, non-media filtering, already-existing row skipping, metadata correctness
(mime_type, file_size, content_hash, storage_key), count parity verify, mismatch detection,
and sample integrity verify. The 8 unit tests pass; integration tests require Docker Desktop
to be restarted (known requirement, see PA_NOTES.md — `docker.raw.sock`). Run
`./gradlew test` from `tools/reimport/` after a Docker restart to confirm integration tests.

**Surprises / decisions during implementation:**
- Kotlin compiler required explicit `.invoke()` for nullable lambda property access
  (`obj?.downloadContent?.invoke()`). Minor; fixed immediately.
- GCS `Page.nextPage` typed cleanly as `Page<Blob>` — no unchecked cast needed with the
  nullable-while-loop pattern (`page = if (page.hasNextPage()) page.nextPage else null`).
- The verify's sample integrity re-uses `reader.listObjects()` for the download path,
  which re-scans the bucket for each sample. For 5 items against a small bucket this is
  fine; for scale, a direct `storage.get(BlobId.of(bucket, key))` lookup would be better.
  Not changed — acceptable for D1's scope.

**Docs updated:** `PA_NOTES.md` (recovery runbook), `VERSIONS.md` (v0.22.0), `ROADMAP.md`
(D1 marked done), `PROMPT_LOG.md` (this entry).

---

## Session: M6 D3 — Web complete (v0.24.0) — 8 May 2026

**Brief:** D3 of Milestone 6 — three sub-tasks bundled: Explore filters (3A), Garden
plots (3B), PhotoDetail variants (3C). See D3 brief in `docs/chats/` for full spec.

**Decisions resolved at session start:**
- Add a plot UX: inline form (not modal).
- Batch reorder: `PATCH /api/plots` bulk endpoint (not sequential PUTs).
- Sort cursors: sort-aware encoding (`SORT_NAME:epochMs_or_null:id`). Old-format cursors
  silently restart pagination (acceptable; D2 never exposed cursors to end users).

**What was built:**

*Schema:* V11 migration — `last_viewed_at TIMESTAMPTZ NULL` on `uploads`. Partial index
`idx_uploads_just_arrived` for the Just arrived predicate.

*Backend 3A:* `listUploadsPaginated` extended with `tags` (any-match), `fromDate`, `toDate`,
`inCapsule`, `includeComposted`, `hasLocation`, `sort`, `justArrived`. Sort-aware cursor
system replaces old single-sort cursor. `tryParseDate` helper handles ISO date strings with
inclusive boundaries (from = start of day, to = exclusive start of next day).

*Backend 3B:* `POST /api/content/uploads/:id/view` sets `last_viewed_at = NOW()`, idempotent.
`PATCH /api/plots` batch reorder is atomic (checks system-defined status before writing).
`Database.recordView` + `Database.batchReorderPlots` added.

*Web 3A — Explore:* Filter chrome added above the grid. `FilterChrome` component with tag
input, date range pickers, capsule/location segmented controls, composted checkbox, sort
dropdown. Collapses to a *Filters* toggle on narrow viewports. Re-fetches on any filter change.
Sort dropdown always visible. `ExploreGrid` replaces `PhotoGrid` to add composted-item
desaturation and *no date* tag on taken-date sorts.

*Web 3B — Garden:* Complete rewrite. Plots fetched from `GET /api/plots`. System plot renders
as `SystemPlotRow` (no DnD, no gear). User plots render as `SortablePlotRow` with `@dnd-kit/
sortable`. Gear menu: Edit, Delete, Move up, Move down. Drag-and-drop fires `PATCH /api/plots`.
Up/down arrows fire the same API. `PlotItemsRow` fetches its own items with cursor pagination.
`PlotForm` shared between Add and Edit. Delete uses `ConfirmDialog`. Compost count and
composted-message toast preserved.

*Web 3C — PhotoDetail:* `?from=garden|explore` query param. `GardenFlavour` component:
action-forward layout, *Compost* below a divider. `ExploreFlavour` component: larger hero,
metadata prominent (taken date, location, capsule count), kebab menu for actions, tags read-
only with *Edit tags* link. Back link context-aware. Every open fires `POST .../view`.

*IDIOMS.md:* Three new entries — *Plot*, *Just arrived*, *Negative-action button separation*.

**Test counts:**
- Backend integration: 21 new (18 × UploadFilterApiTest, 5 × PlotApiTest). Total now 190+.
- Web: 22 new (13 × garden.test.jsx, 5 × explore filter additions, 4 × photo_detail.test.jsx).
  3 existing compost tests updated (view mock added to fetch-ordering). Total: 86 web.

**Surprises / decisions during implementation:**
- `@dnd-kit/sortable` v10 installs cleanly with React 18. No JSdom incompatibilities in tests.
- `compost.test.jsx` mock ordering broke because `POST .../view` is now fired before the blob
  and capsules fetches. Fixed by inserting a view mock first in affected test cases.
- `GardenPage` compost count used two nested fetch calls; simplified to one `limit=200` call.
- `ExploreThumb` fetches thumbnails inside `ExploreGrid` rather than reusing `PhotoGrid`, to
  allow the composted-filter desaturation and no-date overlay without modifying the shared
  component.
- Cursor format `SORT_NAME:sortKeyMs_or_null:id` handles null `capturedAt` naturally: a `null`
  sortKeyMs indicates the cursor sits at the NULL-tail of a taken-date sort.

**Docs updated:** `IDIOMS.md` (3 entries + quick-reference update + status line), `VERSIONS.md`
(v0.24.0), `ROADMAP.md` (D3 marked done), `PA_NOTES.md` (view tracking note), `PROMPT_LOG.md`
(this entry).

---

## Session: D3 polish — testing, fixes, UX iteration (v0.24.1) — 8 May 2026

**Context:** Hands-on testing of the v0.24.0 D3 release. Bret tested the new Garden,
Explore, and PhotoDetail surfaces and reported a series of issues. This session worked
through each one, deploying continuously to production after each fix.

**Issues found and resolved:**

1. **Explore thumbnails blank in production.** `ExploreThumb` constructed URLs as
   `/api/content/uploads/…` (relative). In dev (same-origin) this works; in prod
   (api.heirlooms.digital vs heirlooms.digital) it fetches from the wrong host. Fixed
   by prepending `API_URL`.

2. **Video thumbnails in Garden had no play indicator.** `PlotThumbCard` showed the
   thumbnail image but no overlay. Added a play circle overlay for video items.

3. **Garden detail page missing rotate and tag actions.** `GardenFlavour` in
   `PhotoDetailPage` only had capsule + compost affordances. Added a rotate button
   (images only) and inline `InlineTagEditor`.

4. **Plot tag filtering returned all items regardless of criteria.** Root cause: the
   SQL `tags && ?::text[]` with JDBC `setArray` was unreliable (JDBC array param
   + `::text[]` cast). Fixed by switching to `ARRAY[?,?]::text[]` with individual
   `setString` params per tag — the same pattern used by the working `@>` filter. Also
   fixed the form: the `PlotTagPicker` discarded pending text (not yet confirmed with
   Enter) when the user clicked Create, so plots were created with empty `tag_criteria`.

5. **Tag modal caused visible page reload.** After tagging a Just arrived item, all
   plot rows reset to loading state. Fixed by optimistic exclusion (`justArrivedExclude`
   set) for Just arrived plus silent background re-fetch for user plots.

6. **Plot management UX redesigned.** The inline `PlotTagPicker/PlotForm` was brittle
   and confusing. New flow: "Add a plot" navigates to Explore; when a tag filter is
   active, a "Save as plot…" bar appears. Gear → Edit navigates to
   `/explore?edit_plot=<id>` with filter pre-loaded and an "Editing [name]" banner.

7. **Delete and Update plot silently did nothing.** `CorsFilter` listed only
   `GET, POST, PATCH, OPTIONS`. `DELETE` and `PUT` were blocked by CORS preflight.
   Fixed by adding both to the allowed methods.

8. **Rotate button on Garden thumbnails.** Rotate icon top-left on hover (images only).
   Handled inside `PlotItemsRow` (optimistic state update + background PATCH).

9. **Multi-tag filter in Explore.** Single text input replaced with `TagChromePicker`:
   chips for selected tags, dropdown populated from new `GET /api/content/uploads/tags`
   endpoint, keyboard (Enter/Backspace). "Save as plot" and "Update plot" send the full
   `tag_criteria` array. `PlotItemsRow` already used `plot.tag_criteria.join(',')` so
   Garden plot rows work with multi-tag criteria automatically.

**New backend endpoint:** `GET /api/content/uploads/tags` — returns all distinct tags
across non-composted uploads, sorted alphabetically. Uses `UNNEST(tags)` + `DISTINCT`.

**Architectural note — CORS:** The `CorsFilter` list of allowed methods should include
all HTTP verbs the web app uses. At the time of writing: `GET, POST, PUT, PATCH, DELETE,
OPTIONS`. Revisit if new verbs are introduced.

**Tests:** 88 web tests passing (up from 85 before this session). explore.test.jsx
switched from positional `mockResolvedValueOnce` chains to URL-routing
`mockImplementation` to handle the new tags fetch that fires alongside uploads fetch.

**Docs updated:** `VERSIONS.md` (v0.24.1), `PROMPT_LOG.md` (this entry),
memory/project_milestone_state.md updated.
