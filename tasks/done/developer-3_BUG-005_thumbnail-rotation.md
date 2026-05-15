---
id: BUG-005
title: Investigate thumbnail rotation — first upload shows incorrect thumb orientation
category: Bug Fix
priority: Low
status: done
depends_on: []
touches:
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/service/upload/ThumbnailGenerator.kt
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/service/upload/UploadService.kt
assigned_to: Developer
estimated: 1–2 hours
---

## Background

Discovered during TST-003 manual staging test (2026-05-15). The first photo uploaded
during the session showed an incorrectly rotated thumbnail, while the full-resolution
detail view was correctly oriented. A second photo uploaded in the same session had
both thumbnail and detail view correctly oriented.

## Context

The first upload went through multiple failure/retry cycles due to BUG-003
(UploadWorker pointing at production URL). It's unclear whether the rotation bug
is related to the retry behaviour or is a pre-existing intermittent issue with
EXIF-based rotation not being applied when generating thumbnails.

## Investigation steps

1. Check `ThumbnailGenerator.kt` — does it read and apply the EXIF `Orientation`
   tag before generating the thumbnail, or does it process the raw pixel data only?
2. Check whether the full-resolution image served by the API also has rotation
   applied, or relies on the client to honour EXIF metadata (which would explain
   why the detail view looks correct — the client honours EXIF; the thumbnail does not).
3. Reproduce reliably: upload a portrait-mode JPEG taken in non-default orientation
   and check if the thumbnail is consistently misrotated.
4. If the thumbnail generator doesn't apply EXIF rotation: fix it to normalise
   orientation before encoding the thumbnail (strip EXIF and bake rotation into pixels).

## Acceptance criteria

- Thumbnail orientation matches the detail view for all tested orientations
  (portrait, landscape, upside-down)
- Fix verified on staging with a freshly uploaded photo in each orientation

## Completion notes

**Root cause confirmed**: `extractImageThumbnail` in `ThumbnailGenerator.kt` used
`ImageIO.read()` which decodes raw pixel data only and ignores EXIF metadata entirely.
The full-resolution detail view appeared correct because the Android/iOS clients honour
the EXIF Orientation tag themselves; the thumbnail had no such correction applied,
so it was always misrotated for non-standard-orientation shots.

**Fix** (`ThumbnailGenerator.kt`):
- Added `readExifOrientation(bytes)` — reads `TAG_ORIENTATION` from the EXIF IFD0
  directory using the already-available `com.drewnoakes:metadata-extractor` library.
  Returns 1 (normal) safely if the tag is absent or the EXIF block is malformed.
- Added `applyOrientation(image, orientation)` — applies the correct
  `AffineTransform` for each of the 8 EXIF orientation values, baking the rotation
  and/or flip into the pixel data of the scaled thumbnail.
- `extractImageThumbnail` now calls both helpers before `scaleAndEncode`, so
  orientation is normalised before the thumbnail is written.

**No changes to `UploadService.kt`** — the service calls `generateThumbnail` via the
injected lambda; no orchestration changes were needed.

**Tests added** (`ThumbnailGeneratorTest.kt`):
- `readExifOrientation` round-trips all 8 orientation values via a minimal hand-crafted
  EXIF APP1 segment injected into a synthetic JPEG.
- `applyOrientation` unit tests verify dimension swap for orientations 6 and 8, and
  dimension preservation for orientation 3.
- End-to-end `generateThumbnail` tests verify that a landscape JPEG with
  orientation=6 produces a portrait thumbnail, orientation=8 likewise, and
  orientation=3 (180°) preserves the 2:1 aspect ratio.
- All 17 tests pass (`BUILD SUCCESSFUL`).

**Staging verification**: manual re-test required — upload a portrait-mode JPEG (each
of orientations 1, 3, 6, 8) and confirm the thumbnail matches the detail view.
