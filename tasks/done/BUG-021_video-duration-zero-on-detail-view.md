---
id: BUG-021
title: Video detail view shows 0-second duration — metadata not extracted on upload
category: Bug Fix
priority: Medium
status: done
depends_on: []
touches:
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/service/upload/UploadService.kt
  - HeirloomsiOS/Sources/HeirloomsCore/Networking/HeirloomsAPI.swift
  - HeirloomsiOS/HeirloomsShare/ShareUploadCoordinator.swift
  - HeirloomsiOS/HeirloomsApp/Views/HomeView.swift
assigned_to: Developer
estimated: 2–3 hours
completed: 2026-05-16
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

**Completed: 2026-05-16 by developer-2**

### Root cause analysis

The bug had three separate causes across the upload paths:

**1. Server — `UploadService.handleUpload()` (legacy plaintext POST path)**

`ThumbnailGenerator.extractVideoDuration()` already existed (using ffprobe) and was
correctly called by `confirmLegacyUpload()`. However, `handleUpload()` — the old
raw-POST-body upload endpoint — did not call `extractVideoDuration()`, so duration was
null for any upload going through that path.

**2. iOS — `ShareUploadCoordinator.encryptAndUploadVideo()` (share extension)**

The share extension upload coordinator read the video bytes and encrypted them, but
never extracted the video duration. The `confirmUpload()` call omitted `durationSeconds`.

**3. iOS — `HomeView.uploadSingleItem()` (main app)**

Same issue as the share extension: the main app's upload flow loaded the video via
AVAsset export but did not extract the duration before calling `confirmUpload()`.

**4. iOS — `HeirloomsAPI.confirmUpload()` (core API client)**

The `confirmUpload()` method had no `durationSeconds` parameter, so iOS callers could
not send it to the server even if they extracted it.

### What was NOT a bug

- `ThumbnailGenerator.kt` already had `extractVideoDuration()` (uses ffprobe)
- `UploadService.confirmLegacyUpload()` and `confirmEncryptedUpload()` already stored duration
- `UploadRecord.kt`, `UploadRepository.kt`, all SQL queries, and `UploadRepresentation.kt`
  already included `duration_seconds`
- The `uploads` DB table already has a `duration_seconds` column (confirmed by staging working)
- Android `HeirloomsApi.toUpload()` already parses `durationSeconds` from JSON
- Android `Models.kt`, `PhotoDetailScreen.kt`, `PhotoDetailViewModel.kt` already used the field
- Web `PhotoDetailPage.jsx` already used `upload.durationSeconds`
- No new Flyway migration was needed

### Changes made

**`HeirloomsServer/src/main/kotlin/digital/heirlooms/server/service/upload/UploadService.kt`**

Added `durationSeconds` extraction to `handleUpload()`, mirroring the pattern already
used by `confirmLegacyUpload()`:

```kotlin
val normalizedMime = mimeType.substringBefore(";").trim().lowercase()
val durationSeconds = if (normalizedMime.startsWith("video/"))
    runCatching { extractVideoDuration(body, mimeType) }.getOrNull()
else null
```

The extracted value is now included in the `UploadRecord` passed to `recordUpload()`.

**`HeirloomsiOS/Sources/HeirloomsCore/Networking/HeirloomsAPI.swift`**

Added `durationSeconds: Int? = nil` parameter to `confirmUpload()`. When non-nil, it is
included in the confirm request body as `"durationSeconds": <value>`.

**`HeirloomsiOS/HeirloomsShare/ShareUploadCoordinator.swift`**

- Added `import AVFoundation`
- Added private `extractVideoDuration(from:) -> Int?` helper using `AVURLAsset`
- `encryptAndUploadVideo()` now extracts duration immediately after reading the video
  bytes, and passes it to `api.confirmUpload()`

**`HeirloomsiOS/HeirloomsApp/Views/HomeView.swift`**

- `uploadSingleItem()` now extracts duration from the exported video file using
  `AVURLAsset`, storing it in `videoDurationSeconds: Int?`
- The `confirmUpload()` call passes `durationSeconds: videoDurationSeconds`

### Decisions

- Duration extraction on iOS uses `AVURLAsset.duration` (CMTime → seconds), which
  works without any additional dependencies and is available on iOS 16+.
- The server-side fix wraps the ffprobe call in `runCatching {}` so a missing ffprobe
  binary (dev/test environments) degrades gracefully to `null` rather than failing the upload.
- The `durationSeconds` parameter in `HeirloomsAPI.confirmUpload()` defaults to `nil`
  so all existing call sites remain valid without modification (only the new video upload
  paths pass a non-nil value).

### Spawned tasks

None.

---

**Additional fixes: 2026-05-16 by developer-2 (second pass)**

The previous completion note was partially incorrect: Android `PhotoDetailViewModel.kt` had a playback bug for large encrypted videos with no stored duration, and neither Android nor web showed duration in the detail metadata section.

### Additional changes made

**`HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/garden/PhotoDetailViewModel.kt`**

Fixed a playback regression for large encrypted videos (> 10 MB) when `durationSeconds` is null and there is no preview clip. The previous code had three separate checks that resulted in an early `return@runCatching` before the streaming-decrypt path was reached. Consolidated those three checks into a single guard: if the video file exceeds `LARGE_VIDEO_THRESHOLD`, set `_contentDek` and return (enabling `DecryptingDataSource` streaming in the UI). This covers under-threshold large files, legacy uploads with no stored duration, and over-threshold uploads without a preview clip.

**`HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/garden/PhotoDetailScreen.kt`**

Added `"Duration: M:SS"` metadata line below the "Uploaded" timestamp in both `GardenFlavour` and `ExploreFlavour`. The line is only shown when `upload.isVideo && upload.durationSeconds != null`. Uses the existing `formatDuration()` helper.

**`HeirloomsWeb/src/pages/PhotoDetailPage.jsx`**

Added `"Duration: M:SS"` metadata line in both `GardenFlavour` and `ExploreFlavour`. Shown only when `isVideo && upload.durationSeconds != null`. Uses the existing `formatDuration()` helper already in scope.
