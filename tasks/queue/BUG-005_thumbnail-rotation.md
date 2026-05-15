---
id: BUG-005
title: Investigate thumbnail rotation — first upload shows incorrect thumb orientation
category: Bug Fix
priority: Low
status: queued
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

<!-- Agent appends here and moves file to tasks/done/ -->
