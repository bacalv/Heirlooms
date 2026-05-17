---
id: ARCH-012
title: Android app — package restructure and refactor brief
category: Architecture
priority: High
status: queued
depends_on: []
touches:
  - HeirloomsApp/app/src/main/kotlin/
assigned_to: TechnicalArchitect
estimated: half day
---

## Background

The Android app has grown to 76 Kotlin files and ~16,500 LOC. A small number of files
have become god-objects that are hard to read, test, or change safely:

| File | Lines | Problem |
|------|-------|---------|
| PhotoDetailScreen.kt | 1,167 | UI, player logic, download, rotation all mixed |
| HeirloomsApi.kt | 1,047 | Every API call in one class |
| Uploader.kt | 1,028 | Full upload pipeline — prep, encrypt, GCS PUT, confirm, preview |
| GardenScreen.kt | 1,012 | Garden, just arrived, tagging, upload overlay |
| SharedPlotsScreen.kt | 582 | Plot list, staging panel, invite modal |
| PhotoDetailViewModel.kt | 491 | Decryption, streaming, download, tag update |
| AppNavigation.kt | 470 | All 20+ routes in one file |
| GardenViewModel.kt | 420 | Upload, polling, tag, trellis routing |

The root cause: no architectural rule against large files. The server was refactored
in 8 phases (DONE-001) and delivered the biggest quality improvement of any initiative.
The same approach is needed here.

The driver for this work is test coverage — files of 1,000+ lines cannot be meaningfully
unit tested. Once split into focused classes, each unit can be tested in isolation.

## Goal

Produce a concrete, phased refactor plan for the Android app. The plan must be
detailed enough for Developer agents to execute each phase independently without
architectural judgement calls.

## Deliverables

Write `docs/briefs/android-refactor-brief.md` covering:

### 1. Proposed package structure

Recommend a new package layout. Consider:
- **API layer** (`digital.heirlooms.api`): split `HeirloomsApi.kt` by domain.
  Likely splits: `AuthApi`, `ContentApi`, `KeysApi`, `SocialApi`, `TrellisApi`,
  `PlotApi`, `CapsuleApi`. Each should be ~100–200 lines. `Models.kt` may need
  splitting too.
- **Upload pipeline** (`digital.heirlooms.upload` or `digital.heirlooms.app`):
  split `Uploader.kt` into focused classes — session/checkpoint management,
  encryption, GCS transport, confirmation, thumbnail/preview generation.
- **UI screens**: extract composable sub-components out of large screen files.
  Recommend a consistent naming convention (e.g. `GardenJustArrivedSection`,
  `VideoPlayerPanel`, `MetadataSheet`).
- **Navigation**: consider splitting `AppNavigation.kt` by feature area.
- Identify anything that should move to a shared `common` or `core` module.

### 2. Phased execution plan

Model this on the server refactor (DONE-001 phases 1–8). Each phase should:
- Be independently deployable (no half-finished states)
- Have a clear "before" and "after" description
- Estimate effort (hours or days)
- Identify which files are touched and any risks

Recommended order (adjust if dependencies suggest otherwise):
1. Split `HeirloomsApi.kt` — safest, pure reorganisation, unlocks API-level tests
2. Split `Uploader.kt` — self-contained upload pipeline
3. Split `GardenScreen.kt` and `GardenViewModel.kt`
4. Split `PhotoDetailScreen.kt` and `PhotoDetailViewModel.kt`
5. Split `SharedPlotsScreen.kt`
6. Split `AppNavigation.kt`
7. Any remaining god-files

### 3. Test coverage targets

For each phase, specify what unit tests become possible after the split and should
be written as part of that phase. The goal: no phase completes without tests for
the extracted units.

### 4. Constraints and risks

- All changes must be pure restructuring — no behaviour changes in this refactor
- Each phase must compile and pass existing tests before merging
- No Gradle module splits (single `:app` module is fine for now — focus on
  package/class decomposition only)
- Flag any files where extraction is risky due to tight Compose state coupling

## Notes

- Read the server refactor task (`tasks/done/DONE-001_server-refactor-phases-1-8.md`)
  for the pattern that worked well
- The driving motivation is test coverage, not aesthetics — every split must unlock
  testable units
- Bret's explicit requirement (2026-05-16): developers must write unit tests alongside
  every feature and fix going forward

## Completion notes

**Completed:** 2026-05-17  
**Branch:** agent/tech-architect-1/ARCH-012  
**Deliverable:** `docs/briefs/android-refactor-brief.md`

Brief covers all four required sections:

1. **Package structure** — Full target layout with 8 API sub-classes + facade pattern,
   5 upload pipeline classes, screen decomposition for all four god-screen pairs, and
   5 nav sub-graph files. Naming conventions defined for screens, panels, sheets, modals,
   VMs, helpers, and nav graphs.

2. **Phased execution plan** — 7 phases (6 concrete + 1 audit), each with before/after
   description, effort estimate, files touched, risk level, and specific risk flags.
   Order: API (safest) → Upload → Garden → PhotoDetail → SharedPlots → Navigation → Audit.

3. **Test coverage targets** — Per-phase test file list and what each test proves.
   Phase completion criterion requires tests written and passing before merge.

4. **Constraints and risks** — Hard constraints documented (no Gradle split, no behaviour
   changes, one phase per branch). Risk register with three High-severity items:
   ActivityResultLauncher scoping, ExoPlayer lifecycle, and Compose state hoisting pattern.

Key architectural decisions made:
- `HeirloomsApi` retained as a facade delegator (all 50+ call sites unchanged)
- `UploadEncryption` / `UploadTransport` circular dependency prevented via callback
  injection pattern rather than direct import
- `ActivityResultLauncher` registrations must stay in `GardenScreen` (host composable)
- ExoPlayer `DisposableEffect` ownership stays in `VideoPlayerPanel` composable
