---
id: ARCH-013
title: Web app — module restructure and refactor brief
status: final
author: TechnicalArchitect
date: 2026-05-17
---

# Web App — Module Restructure and Refactor Brief

## Motivation

The web app has grown to 68 JS/JSX files and ~12,000 LOC with no architectural rule
against large files. Several page components have become god-objects that mix
unrelated concerns: upload pipelines, encryption, polling, data-fetching, routing,
and rendering all coexist in single files. The two largest files (`GardenPage.jsx`
at 1,460 lines and `PhotoDetailPage.jsx` at 891 lines) have almost no unit test
coverage because they cannot be meaningfully exercised in isolation.

The primary driver is test coverage. Every split in this plan must unlock testable
units. Aesthetics are secondary.

This plan models the server refactor (DONE-001, 8 phases) — pure restructuring with
no behaviour changes, each phase independently deployable, each phase gated by the
existing Vitest suite.

---

## 1. Proposed Module Structure

### 1.1 Target folder layout

```
src/
  api/
    authApi.js          # auth/challenge/login/logout/register/invite/pairing/devices
    contentApi.js       # upload initiate/resumable/confirm/hash/fetchBytes/blob PUT
    keysApi.js          # keys/sharing, keys/passphrase, keys/devices
    socialApi.js        # friends, share upload
    plotApi.js          # plots CRUD, membership, status, transfer
    trellisApi.js       # trellis / tags
    capsuleApi.js       # capsules (currently spread across pages)
    utils.js            # apiFetch, API_URL, formatters (date, bytes, recipients)
    index.js            # re-exports everything for backward compat (removed in Phase 7)
  hooks/
    useGardenPolling.js       # 5-second plot refresh loop
    useUpload.js              # file → encryptAndUpload pipeline, status + error state
    useTrellisRouting.js      # tag-change → double setPlotRefreshKey timing
    useVaultSession.js        # getMasterKey() / getSharingPrivkey() with locked-check
    useInfiniteScroll.js      # cursor-based paginated list (reusable)
  components/
    garden/
      PlotThumbCard.jsx       # extracted from GardenPage
      PlotItemsRow.jsx        # extracted from GardenPage
      TaggingSheet.jsx        # extracted from GardenPage (inline tag editor)
      UploadOverlay.jsx       # status banner + error toast during upload
      JustArrivedSection.jsx  # the sentinel plot row
      SharedPlotForm.jsx      # new-shared-plot name form + button
    photo/
      VideoPlayer.jsx         # preview video player (GardenPage VideoPlayer fn)
      ImageViewer.jsx         # encrypted/plain image modal
      TagsPanel.jsx           # inline tag editor used in PhotoDetailPage
      ActionsMenu.jsx         # kebab menu — add-to-capsule, compost
      CapsuleSection.jsx      # capsule association UI
    shared-plots/
      MembershipCard.jsx
      InvitationCard.jsx
      LeftCard.jsx
      TombstonedCard.jsx
      TransferOwnershipModal.jsx
      PlotHeaderSection.jsx
    explore/
      FilterChrome.jsx
      ExploreGrid.jsx
      ExploreThumb.jsx
      SharedPlotDetailPanel.jsx   # shared-plot detail currently conflated in ExplorePage
  crypto/
    vaultCrypto.js          # unchanged — already well-structured
    vaultSession.js         # unchanged
    encryptAndUpload.js     # MOVED from GardenPage (see §1.2)
    encryptedVideoStream.js # unchanged
    deviceKeyManager.js     # unchanged
  pages/                    # page components become thin shells
    GardenPage.jsx
    PhotoDetailPage.jsx
    SharedPlotsPage.jsx
    ExplorePage.jsx
    ... (other pages unchanged)
```

### 1.2 The `encryptAndUpload` function

Currently exported from `GardenPage.jsx` and imported by `AuthLayout.jsx`. This
creates an architectural violation: a layout component depends on a page component.

**Decision**: move `encryptAndUpload` and its private helpers
(`encryptAndUploadStreamingContent`, `buildEncryptedMetadata`, `sha256HexFile`,
`generateThumbnail`, `getVideoDurationSeconds`, `generatePreviewClip`, `getFFmpeg`,
`PLAIN_CHUNK_SIZE`, `LARGE_FILE_THRESHOLD`) to `src/crypto/encryptAndUpload.js`.

Update callers:
- `AuthLayout.jsx` → import from `../crypto/encryptAndUpload`
- `GardenPage.jsx` → import from `../crypto/encryptAndUpload`
- Any existing tests referencing the old import path → update in the same phase.

This move belongs in Phase 1b (immediately after or alongside the api.js split),
before any other GardenPage extraction, so subsequent phases have a clean starting
point.

### 1.3 API layer splits

`api.js` currently exports ~45 named functions and 5 formatters in a single 424-line
file. Proposed domain splits:

| Module | Functions | Approx lines |
|--------|-----------|--------------|
| `authApi.js` | `authChallenge`, `authLogin`, `authMe`, `authLogout`, `authRegister`, `getInvite`, `createInvite`, `buildInviteUrl`, `getAccount`, `authInviteConnect`, `pairingQr`, `pairingStatus`, `listDevices`, `deleteDevice` | ~130 |
| `contentApi.js` | `initiateEncryptedUpload`, `initiateResumableUpload`, `putBlob`, `putBlobWithProgress`, `checkContentHash`, `confirmEncryptedUpload`, `fetchBytes` | ~110 |
| `keysApi.js` | `putSharingKey`, `putPassphrase`, `registerDevice` | ~50 |
| `socialApi.js` | `getFriends`, `getFriendSharingKey`, `shareUpload` | ~50 |
| `plotApi.js` | `listPlots`, `listSharedMemberships`, `listPlotMembers`, `acceptPlotInvite`, `leaveSharedPlot`, `rejoinSharedPlot`, `restoreSharedPlot`, `transferPlotOwnership`, `setSharedPlotStatus`, `createPlot`, `updatePlot`, `addPlotItem` | ~120 |
| `utils.js` | `apiFetch`, `API_URL`, `fetchSettings`, `formatUnlockDate`, `formatUploadDate`, `joinRecipients`, `capsuleTitle`, `formatCompactDate`, `daysUntilPurge`, `buildUnlockAt` | ~80 |

`index.js` re-exports everything from the above modules with the same names so all
existing imports of `'../api'` and `'./api'` continue to work without change during
the transition. The barrel file is removed in Phase 7 when all call sites have been
updated to direct imports.

### 1.4 Custom hooks

| Hook | Extracted from | Description |
|------|---------------|-------------|
| `useGardenPolling` | `GardenPage.jsx` | `setInterval` that increments `plotRefreshKey` every 5 s; returns `[refreshKey, forceRefresh]` |
| `useUpload` | `GardenPage.jsx` / `AuthLayout.jsx` | Wraps `encryptAndUpload`; manages `uploadStatus`, `uploadError`, `uploadingRef`; returns `{ uploadStatus, uploadError, handleFiles }` |
| `useTrellisRouting` | `GardenPage.jsx` | Encapsulates the double `setPlotRefreshKey` pattern (immediate + 1,500 ms) used after a tag update to wait for trellis routing |
| `useVaultSession` | various | Returns `{ masterKey, sharingPrivkey }`, throwing `VaultLockedError` if vault is locked; avoids scattered `getMasterKey()` try/catch |
| `useInfiniteScroll` | `GardenPage.jsx` (`PlotItemsRow`) | Cursor-based paginated fetch; returns `{ items, loading, loadingMore, hasMore, loadMore }` |

### 1.5 Crypto layer

`src/crypto/` is already well-structured. The only change is:
- Add `encryptAndUpload.js` (moved from `GardenPage.jsx`, as described in §1.2).
- No other changes to `vaultCrypto.js`, `vaultSession.js`, `encryptedVideoStream.js`,
  or `deviceKeyManager.js`.

---

## 2. Phased Execution Plan

Each phase must: (a) leave the Vitest suite green before the PR is merged, and (b)
produce no behaviour change visible to users or E2E tests.

### Phase 1 — Split `api.js` (safest, pure reorganisation)

**Files touched**: `src/api.js` → split into `src/api/authApi.js`,
`src/api/contentApi.js`, `src/api/keysApi.js`, `src/api/socialApi.js`,
`src/api/plotApi.js`, `src/api/utils.js`, `src/api/index.js`.

**Approach**:
1. Create the `src/api/` directory.
2. Create each domain module, paste the relevant functions verbatim.
3. Create `src/api/index.js` that re-exports everything from all domain modules.
4. Replace `src/api.js` with a redirect: `export * from './api/index.js'` — or
   simply delete it and update `vite.config.js` alias if one exists.
   Prefer updating the barrel: keep `src/api.js` as a one-liner re-export for now;
   remove it in Phase 7.
5. Run Vitest — all tests must pass without any other changes.

**Effort**: 2–3 hours.

**Risks**: None — purely mechanical copy/split. No logic changes. One risk: circular
imports between domain modules. Avoid by ensuring `utils.js` (which owns `apiFetch`)
is imported by the domain modules, not vice versa.

**Tests unlocked**: `authApi.js`, `contentApi.js`, `keysApi.js`, `plotApi.js`
functions can now be unit-tested in isolation with `vi.fn()` replacing `apiFetch`.
Write tests for these modules as part of this phase (see §3, Phase 1).

---

### Phase 1b — Move `encryptAndUpload` to `src/crypto/`

**Files touched**: `src/pages/GardenPage.jsx` (remove ~300 lines),
`src/crypto/encryptAndUpload.js` (new file), `src/AuthLayout.jsx` (1 import),
any test files that import `encryptAndUpload` from `GardenPage`.

**Approach**:
1. Create `src/crypto/encryptAndUpload.js`. Move into it:
   - `encryptAndUpload` (currently exported — keep it exported)
   - `encryptAndUploadStreamingContent` (private helper)
   - `buildEncryptedMetadata` (private helper)
   - `sha256HexFile` (private helper)
   - `generateThumbnail` (private helper)
   - `getVideoDurationSeconds` (private helper)
   - `generatePreviewClip` + `getFFmpeg` (private helpers, lazy-load FFmpeg)
   - `PLAIN_CHUNK_SIZE`, `LARGE_FILE_THRESHOLD` constants
2. Update `GardenPage.jsx`: replace the removed block with
   `import { encryptAndUpload } from '../crypto/encryptAndUpload'`.
3. Update `AuthLayout.jsx`: `import { encryptAndUpload } from './crypto/encryptAndUpload'`.
4. Update any test files referencing the old GardenPage import path.
5. Run Vitest.

**Effort**: 2–3 hours.

**Risks**: `GardenPage.jsx` currently both defines and uses `encryptAndUpload` — the
internal call at line 1138 (`handlePlantFiles`) just needs the new import. The FFmpeg
singleton (`ffmpegInstance`) must remain in `encryptAndUpload.js` — do not split it
further in this phase.

HMR note: moving `encryptAndUpload` out of `GardenPage.jsx` removes a module-scope
singleton (`ffmpegInstance`) from the page module. Vite HMR reloads the module on
edit; the singleton will be re-initialised on the next encode. This is correct
behaviour and an improvement over the current state (HMR on `GardenPage.jsx` already
drops the singleton).

**Tests unlocked**: `encryptAndUpload` can now be tested without rendering
`GardenPage`. Write tests for the upload pipeline (mocking `initiateEncryptedUpload`,
`putBlobWithProgress`, `confirmEncryptedUpload`) as part of this phase.

---

### Phase 2 — Extract custom hooks from `GardenPage.jsx`

**Files touched**: `src/pages/GardenPage.jsx`, `src/hooks/useGardenPolling.js`,
`src/hooks/useUpload.js`, `src/hooks/useTrellisRouting.js`,
`src/hooks/useVaultSession.js`, `src/hooks/useInfiniteScroll.js`.

**Approach**: Extract each hook as described in §1.4. GardenPage replaces inline
logic with hook calls. No component tree changes.

Key extraction notes:

- `useGardenPolling`: the `setInterval` at GardenPage line 1008 becomes
  `const [plotRefreshKey, forceRefresh] = useGardenPolling(5_000)`.
- `useUpload`: wraps `encryptAndUpload` (now in `src/crypto/`); returns
  `{ uploadStatus, uploadError, handleFiles }`. `AuthLayout.jsx` also switches to
  this hook, replacing its current inline `handleFiles` + `uploadingRef`.
- `useTrellisRouting`: returns `triggerTrellisRefresh = () => { forceRefresh();
  setTimeout(forceRefresh, 1500) }` — used after tag updates in
  `handleQuickUpdateTags`.
- `useVaultSession`: thin wrapper; returns `{ getMasterKey, getSharingPrivkey }` as
  stable function references. Components call `getMasterKey()` at invocation time
  (not during render), so no additional memo is needed.
- `useInfiniteScroll`: extracted from `PlotItemsRow` — takes a URL builder and
  returns paginated state. Reusable by `ExplorePage` in Phase 5.

**Effort**: 4–6 hours.

**Risks**: React hooks must not be called conditionally; verify no existing code
gates hook calls on runtime conditions. `useVaultSession` must not read the master
key during render — it must only surface the accessor function.

HMR note: extracting hooks into separate files means Vite will hot-reload only the
changed hook file. State in `useState` is preserved across HMR reloads of parent
components as long as the component tree does not unmount. No issues expected.

**Tests unlocked**: Each hook is independently testable with `renderHook` from
`@testing-library/react`. Write tests for `useGardenPolling` (timer behaviour),
`useUpload` (duplicate/error paths), `useTrellisRouting` (double-fire timing),
`useInfiniteScroll` (pagination).

---

### Phase 3 — Split `GardenPage.jsx` into sub-components

**Files touched**: `src/pages/GardenPage.jsx`,
`src/components/garden/PlotThumbCard.jsx`,
`src/components/garden/PlotItemsRow.jsx`,
`src/components/garden/TaggingSheet.jsx`,
`src/components/garden/UploadOverlay.jsx`,
`src/components/garden/JustArrivedSection.jsx`,
`src/components/garden/SharedPlotForm.jsx`.

**Approach**: Extract the named inner components from `GardenPage.jsx`:
- `PlotThumbCard` (lines ~41–186): self-contained card, no page-level state.
- `PlotItemsRow` (lines ~190–260): uses `useInfiniteScroll` (from Phase 2);
  accepts `plot`, `apiKey`, and callback props.
- `TaggingSheet` (lines ~340–470): the inline modal-like tag editor; accepts
  `upload`, `apiKey`, `onSave`, `onClose`.
- Inline video/image preview modals → extract to `VideoPlayer.jsx` and
  `ImageViewer.jsx` in `src/components/garden/` (these are simpler than the ones
  in PhotoDetailPage and should remain separate).
- `UploadOverlay` (the fixed-position status banner): trivial.
- `JustArrivedSection`: wraps `PlotItemsRow` with the sentinel plot logic.
- `SharedPlotForm`: the name-input + create-button widget.

After extraction, `GardenPage.jsx` becomes a page shell: it calls hooks, manages
top-level dialog state, and renders the component tree.

**Effort**: 6–8 hours.

**Risks**: `PlotItemsRow` maintains local state (`items`, `nextCursor`, `loading`,
`newlyArrivedIds`). Extraction is straightforward but verify that `refreshKey`
(from `useGardenPolling`) is passed as a prop correctly — it is used as a dependency
in `PlotItemsRow`'s `useEffect`. Do not lift this state to GardenPage; keep it local
to `PlotItemsRow`.

**Tests unlocked**: `PlotThumbCard` (render tests for video/image/composted states),
`TaggingSheet` (user interaction: add tag, remove tag, save error), `PlotItemsRow`
(pagination, new-arrival animation trigger).

---

### Phase 4 — Split `PhotoDetailPage.jsx`

**Files touched**: `src/pages/PhotoDetailPage.jsx`,
`src/components/photo/VideoPlayer.jsx`,
`src/components/photo/TagsPanel.jsx`,
`src/components/photo/ActionsMenu.jsx`,
`src/components/photo/CapsuleSection.jsx`.

**Approach**: PhotoDetailPage contains two flavour components (`GardenFlavour` at
line 266 and `ExploreFlavour` at line 426) plus utility functions (`loadPlotKey`,
`unwrapDek`, formatters, icons). Extract:

- `VideoPlayer.jsx`: the `PreviewVideoPlayer` component (lines ~200–264); takes
  `{ upload, blobUrl, onDownload, downloading }`.
- `TagsPanel.jsx`: `InlineTagEditor` (lines ~74–137); identical in signature to
  the one in `GardenPage.jsx` — consolidate into one shared component. This is the
  highest-value consolidation opportunity across phases.
- `ActionsMenu.jsx`: the `KebabMenu` component (lines ~159–198); takes
  `{ onAddToCapsule, onCompost, compostDisabled }`.
- `CapsuleSection.jsx`: capsule association UI from within `GardenFlavour`.
- Keep `GardenFlavour` and `ExploreFlavour` in `PhotoDetailPage.jsx` for now;
  they are tightly bound to page-level state and can be addressed in a later cleanup
  pass if needed.

**Effort**: 4–6 hours.

**Risks**: `TagsPanel`/`InlineTagEditor` appears in both `GardenPage.jsx` (Phase 3)
and `PhotoDetailPage.jsx`. If Phase 3 extracted it to `src/components/garden/TaggingSheet.jsx`,
rename and promote it to `src/components/TagsPanel.jsx` (shared) in this phase.
Coordinate with the Phase 3 developer via the task file to avoid a merge conflict.

HMR note: no concerns; sub-component files will HMR independently.

**Tests unlocked**: `VideoPlayer` (play/pause, time update, ended event), `TagsPanel`
(add/remove/save), `ActionsMenu` (open/close, item click).

---

### Phase 5 — Split `SharedPlotsPage.jsx` and `ExplorePage.jsx`

**Files touched**: `src/pages/SharedPlotsPage.jsx`, `src/pages/ExplorePage.jsx`,
`src/components/shared-plots/` (all sub-components listed in §1.1),
`src/components/explore/` (all sub-components listed in §1.1).

**Approach**:

`SharedPlotsPage.jsx` already has well-named inner components (`NamePromptModal`,
`TransferOwnershipModal`, `MembershipCard`, `InvitationCard`, `LeftCard`,
`TombstonedCard`). Extract them verbatim into `src/components/shared-plots/`.
The page shell retains: `useEffect` for data fetch, `reload()`, and the render
of sections.

`ExplorePage.jsx` already has `TagChromePicker`, `FilterChrome`, `ExploreGrid`,
`ExploreThumb` as inner functions. Extract them to `src/components/explore/`.
`ExplorePage` also conflates the explore view and the shared-plot detail view
(controlled by search params). This conflation is intentional product behaviour;
do not separate them. Extract only the UI sub-components.

Use `useInfiniteScroll` (from Phase 2) for `ExplorePage`'s cursor-based fetch.

**Effort**: 4–6 hours.

**Risks**: `MembershipCard` and related components have their own local async
handlers (`handleLeave`, `handleToggleStatus`). These call imported API functions
directly. After extraction, they still import from `../api/plotApi` (or the barrel).
No state lifting needed.

**Tests unlocked**: `MembershipCard` (leave flow, toggle flow, error states),
`InvitationCard` (accept with name prompt), `FilterChrome` (filter chip renders,
collapse/expand), `ExploreGrid` (item render, sort change).

---

### Phase 6 — Update all call sites to direct imports; remove the barrel

**Files touched**: All files currently importing from `'../api'` or `'./api'` →
update to import from the specific domain module. Remove `src/api.js` (one-liner
re-export) and `src/api/index.js`.

**Approach**: Use IDE search-and-replace guided by the function-to-module mapping
from §1.3. Each import site should import only what it uses from the correct module.
Verify with Vitest and a production build (`vite build`).

**Effort**: 2–3 hours.

**Risks**: Low — purely mechanical. The barrel removal is safe once all call sites
are updated. Run `vite build` to catch any missed imports (TypeScript is not used,
so build errors are the safety net).

**Tests unlocked**: No new units unlocked, but this phase enables tree-shaking of
the API layer in production bundles.

---

### Phase 7 — Remaining files and cleanup

Scope: any file not addressed in Phases 1–6 that still exceeds 300 lines and mixes
concerns. Based on current inventory, candidates are:

- `CapsuleDetailPage.jsx` (551 lines): extract `CapsuleMediaGrid`, `CapsuleHeader`,
  `CapsuleLockStatus` into `src/components/capsule/`.
- `FlowsPage.jsx` (301 lines): below threshold — review and skip unless it has
  testability problems.
- `vaultCrypto.js` (309 lines): crypto primitives file; do not split — it is already
  purely functional and fully tested by `vaultCrypto.test.js`.

**Effort**: 3–4 hours depending on scope.

---

## 3. Test Coverage Targets

The existing suite uses Vitest + React Testing Library. Tests must be written as part
of each phase — not deferred.

### Phase 1 — api.js split

Write unit tests for:
- `authApi.js`: `authChallenge` (fetch call, error throw), `authLogin` (raw response
  returned), `buildInviteUrl` (staging vs prod domain).
- `contentApi.js`: `confirmEncryptedUpload` (request body shape), `putBlobWithProgress`
  (XHR progress events via `vi.fn()`).
- `plotApi.js`: `createPlot`, `updatePlot` (body serialisation), `leaveSharedPlot`
  (`must_transfer` error on 403).
- `utils.js`: all formatters (`formatUploadDate`, `capsuleTitle`, `daysUntilPurge`,
  `buildUnlockAt`).

**Priority**: `contentApi.js` (upload confirm body is complex and has caused bugs in
the past) and `utils.js` formatters (pure functions, trivially testable).

### Phase 1b — encryptAndUpload

Write unit tests for `src/crypto/encryptAndUpload.js`:
- Happy path: file → encrypted upload → confirm (mock all API calls and crypto
  primitives with `vi.fn()`).
- Duplicate detection: when `checkContentHash` returns `true`, function returns
  `{ duplicate: true }` without proceeding.
- Error path: `putBlobWithProgress` rejects → function throws.

These are the highest-value tests in the entire refactor. `encryptAndUpload` is
complex, stateful, and currently untestable because it lives inside a page component.

### Phase 2 — Custom hooks

- `useGardenPolling`: use `vi.useFakeTimers()`; assert `forceRefresh` has been called
  N times after N intervals; assert cleanup clears the interval.
- `useUpload`: mock `encryptAndUpload`; test duplicate path, error path, success path.
- `useTrellisRouting`: use `vi.useFakeTimers()`; assert two refresh calls — one
  immediate, one at 1,500 ms.
- `useInfiniteScroll`: mock `apiFetch`; assert initial load, `loadMore` appends items,
  cursor exhausted stops further loads.

### Phase 3 — GardenPage sub-components

- `PlotThumbCard`: render for image / video / composted / encrypted variants;
  hover action button presence.
- `TaggingSheet`: add tag, remove tag, save with API mock, error display.
- `PlotItemsRow`: initial load, load-more, new-arrival animation class applied.

### Phase 4 — PhotoDetailPage sub-components

- `VideoPlayer`: `onTimeUpdate` fires `setPreviewPosition`; `onEnded` fires callback.
- `TagsPanel`: same tests as `TaggingSheet` (they should share test fixtures after
  consolidation).
- `ActionsMenu`: opens on click, closes on outside click, fires `onCompost`.

### Phase 5 — SharedPlotsPage / ExplorePage sub-components

- `MembershipCard`: `handleLeave` calls `leaveSharedPlot` then `onAction`; error
  state on failure.
- `InvitationCard`: shows `NamePromptModal` on accept click; calls `acceptPlotInvite`.
- `FilterChrome`: collapse/expand behaviour; `setTags` called on tag chip click.

### Phase 6 — No new units

Run the full Vitest suite and assert `vite build` succeeds.

---

## 4. Constraints and Risks

### Behaviour preservation

All phases are pure restructuring. No logic changes, no API changes, no user-visible
behaviour changes. The Vitest suite is the gate for every phase.

### `encryptAndUpload` public interface

`encryptAndUpload(file, apiKey, onStatus)` is exported and called from two places
(`AuthLayout.jsx` and `GardenPage.jsx`). Its signature must not change. After the
move to `src/crypto/encryptAndUpload.js`, it is still exported with the same name
and signature. Test files that import it from the old path must be updated in
Phase 1b.

### Vite HMR and module-scope state

Three pieces of module-scope state require attention:

1. **`ffmpegInstance` (GardenPage → encryptAndUpload.js)**: A singleton holding the
   loaded FFmpeg WASM instance. After move to `src/crypto/encryptAndUpload.js`, HMR
   reloads of that module will reset it. This is acceptable — FFmpeg re-loads on next
   encode. No state loss risk for the user (encode is always user-triggered).

2. **`cachedSettings` (api.js → utils.js)**: A module-scope settings cache. After
   Phase 1, this lives in `utils.js`. HMR reloads of `utils.js` reset it. Acceptable
   — the next API call re-fetches settings.

3. **`thumbnailCache` (vaultSession.js)**: Unchanged — stays in `vaultSession.js`.

### Shared `TagsPanel` / `InlineTagEditor` consolidation

`GardenPage.jsx` and `PhotoDetailPage.jsx` each contain a local `InlineTagEditor`
component with the same props interface but slightly different styling. During Phase 3
and Phase 4, consolidate these into a single `src/components/TagsPanel.jsx`. Document
the styling differences (font-size, padding) as props. This is the one case where the
"pure restructuring" rule is slightly bent — the consolidation is low risk because
both components are render-only with no side effects.

### React state coupling in `PlotItemsRow`

`PlotItemsRow` has local state (`items`, `nextCursor`, `loading`, `newlyArrivedIds`,
`knownIdsRef`). When extracted in Phase 3, this state remains local to
`PlotItemsRow`. The `refreshKey` prop from `useGardenPolling` is passed down as a
`useEffect` dependency to trigger re-fetches. Do not lift `items` to `GardenPage` —
that would require significant state management changes inconsistent with the
restructuring-only mandate.

### Testing boundaries for encrypted content

Tests for `encryptAndUpload` must mock the Web Crypto API. Vitest runs in jsdom,
which provides a partial `crypto.subtle` implementation. Supplement with
`vi.spyOn(crypto.subtle, 'encrypt')` where needed. Do not attempt to test the
FFmpeg encode path — mock `generatePreviewClip` at the module boundary.

### Phase sequencing dependency

Phase 2 (hooks) depends on Phase 1b (`encryptAndUpload` move) because `useUpload`
wraps `encryptAndUpload`. Phases 3–5 depend on Phase 2 (hooks must exist before page
components are thinned). Phases 1 and 1b can run in parallel if two developers are
available, but Phase 1b is safer after Phase 1 (api modules must exist for
`encryptAndUpload.js` to import from).

Recommended sequencing for a single developer:
```
Phase 1 → Phase 1b → Phase 2 → Phase 3 → Phase 4 → Phase 5 → Phase 6 → Phase 7
```

### No Vite alias changes needed

`src/api.js` is not currently aliased in `vite.config.js`. The barrel re-export
approach (one-liner `export * from './api/index.js'`) preserves all existing import
paths without touching Vite config.

---

## Summary table

| Phase | Files touched | Effort | Tests unlocked |
|-------|--------------|--------|----------------|
| 1 | api.js → api/ | 2–3 h | API domain function unit tests |
| 1b | GardenPage → crypto/encryptAndUpload.js | 2–3 h | encryptAndUpload pipeline tests (highest value) |
| 2 | GardenPage → src/hooks/ | 4–6 h | Hook unit tests (polling, upload, trellis, scroll) |
| 3 | GardenPage → components/garden/ | 6–8 h | Card, row, sheet component tests |
| 4 | PhotoDetailPage → components/photo/ | 4–6 h | Video, tags, actions component tests |
| 5 | SharedPlotsPage + ExplorePage → components/ | 4–6 h | Membership card, filter tests |
| 6 | All call sites → direct imports; remove barrel | 2–3 h | Full suite + build gate |
| 7 | CapsuleDetailPage + remaining god-files | 3–4 h | Capsule sub-component tests |

**Total estimated effort**: 27–39 hours across 7–8 developer-days.
