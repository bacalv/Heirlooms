---
id: BUG-021
title: Video detail view shows 0-second duration — metadata not extracted on upload
category: Bug Fix
priority: Medium
status: queued
depends_on: []
touches:
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/service/upload/ThumbnailGenerator.kt
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/service/upload/UploadService.kt
assigned_to: Developer
estimated: 2–3 hours
---

## Problem

When a video is imported and uploaded, the thumbnail generates correctly but the detail
view on both Android and web shows a 0-second duration. The video does not play.

Discovered during v0.54 smoke test Step 4 (2026-05-15). Video was recorded in a
separate app and imported as a file into the Heirlooms staging app.

## Likely cause

`ThumbnailGenerator` extracts a video frame (for the thumbnail) but does not extract
or store the video duration. The duration is likely stored as `0` or `null` in the
`uploads` table, causing the detail view to display 0 seconds.

## Investigation steps

1. Check `ThumbnailGenerator.extractVideoThumbnail` — does it extract duration?
2. Check the `uploads` table schema — is there a `duration_seconds` or similar column?
3. Check `UploadService` — is duration passed to the upload record on creation?
4. Check Android detail view and web detail page — how do they read and display duration?

## Fix

Extract video duration during thumbnail generation (e.g. using FFmpeg metadata or
a Java media library) and store it in the `uploads` table. Display it in both the
Android and web detail views.

## Acceptance criteria

- Uploaded video shows correct duration in the Android detail view
- Uploaded video shows correct duration in the web detail view
- Video plays correctly in both views
- Tested with at least one video recorded externally and imported

## Completion notes

<!-- Agent appends here and moves file to tasks/done/ -->
