---
id: ARCH-013
title: Web app — module restructure and refactor brief
category: Architecture
priority: High
status: queued
depends_on: []
touches:
  - HeirloomsWeb/src/
assigned_to: TechnicalArchitect
estimated: half day
---

## Background

The web app has grown to 68 JS/JSX files and ~12,000 LOC. The `pages/` folder
alone contains 5,800 LOC, with several page components acting as god-objects:

| File | Lines | Problem |
|------|-------|---------|
| GardenPage.jsx | 1,460 | Upload logic, encryption, polling, tagging, trellis routing all mixed |
| PhotoDetailPage.jsx | 891 | Video, image, capsules, download, sharing |
| ExplorePage.jsx | 578 | Explore + shared plot detail conflated |
| CapsuleDetailPage.jsx | 551 | |
| SharedPlotsPage.jsx | 546 | |
| api.js | 424 | Every API call — no domain separation |

The root cause: no architectural rule against large files. The Android and server
codebases are receiving the same treatment (ARCH-012, DONE-001). This brief covers
the web app.

The driver is test coverage. Large page components cannot be meaningfully unit tested.
Once split into focused components and hooks, each unit can be tested in isolation.
The existing test suite (`test/`) has good coverage of auth and capsule flows but
almost nothing for the garden or photo detail — the two largest files.

## Goal

Produce a concrete, phased refactor plan for the web app. Detailed enough for
Developer agents to execute each phase independently.

## Deliverables

Write `docs/briefs/web-refactor-brief.md` covering:

### 1. Proposed module structure

Recommend a new folder layout. Consider:

**API layer** (`src/api/`): split `api.js` by domain.
Likely splits: `authApi.js`, `contentApi.js`, `keysApi.js`, `socialApi.js`,
`trellisApi.js`, `plotApi.js`, `capsuleApi.js`. Each should export a focused
set of functions with consistent error handling.

**Custom hooks** (`src/hooks/`): extract data-fetching and business logic from
page components into reusable hooks. Candidates:
- `useGardenPolling` — the 5s poll loop in GardenPage
- `useUpload` / `useEncryptAndUpload` — the upload+encryption pipeline
- `useTrellisRouting` — tag-change → route logic
- `useVaultSession` — master key access pattern

**Sub-components** (`src/components/` or co-located): large pages should be split
into focused components. Candidates:
- `GardenPage`: JustArrivedSection, PlotRow, TaggingSheet, UploadOverlay
- `PhotoDetailPage`: VideoPlayer, ImageViewer, TagsPanel, ActionsMenu, CapsuleSection
- `SharedPlotsPage`: PlotList, StagingQueue, MembersList

**Crypto layer** (`src/crypto/`): already reasonably well-structured — review whether
`encryptAndUpload` (currently in GardenPage.jsx) should move here.

### 2. Phased execution plan

Each phase should be independently deployable with a clear before/after. Recommended
order:
1. Split `api.js` — safest, pure reorganisation, no behaviour change
2. Extract custom hooks from `GardenPage.jsx` — decouple logic from rendering
3. Split `GardenPage.jsx` into sub-components
4. Split `PhotoDetailPage.jsx`
5. Split `SharedPlotsPage.jsx` and `ExplorePage.jsx`
6. Any remaining large files

For each phase: files touched, estimated effort, risks, and what tests become
possible afterwards.

### 3. Test coverage targets

The existing test suite uses Vitest + React Testing Library. For each phase, specify:
- Which units become independently testable
- What tests should be written as part of that phase (not deferred)
- Priority: `useUpload`/`useEncryptAndUpload` and `api.js` domain modules are
  the highest-value targets

### 4. Constraints and risks

- All changes must be pure restructuring — no behaviour changes
- Each phase must pass the existing Vitest suite before merging
- The `encryptAndUpload` function in `GardenPage.jsx` is exported and used by tests —
  its public interface must not change (or tests must be updated in the same phase)
- Flag any components where Vite's hot-module reload behaviour may affect how
  state is scoped after extraction

## Notes

- Read `tasks/done/DONE-001_server-refactor-phases-1-8.md` for the phased approach
  that worked on the server
- Read ARCH-012 (Android brief) for consistency — both refactors should follow the
  same philosophy
- The driving motivation is test coverage, not aesthetics
- Bret's explicit requirement (2026-05-16): developers must write unit tests alongside
  every feature and fix going forward
