# SE Brief: Preview clips, duration-based playback, and end-of-preview UX

**Date:** 11 May 2026
**Type:** Server + Web + Android

---

## Goal

Store video duration per upload. Use it to decide whether to play the full encrypted
video or a short preview clip, with a threshold that differs per platform and is
per-device configurable on Android. Give the user clear UX when they are watching a
preview rather than the full video.

---

## Background

v0.36.0 added encrypted preview clips (first N seconds, generated client-side via
ffmpeg.wasm on web and MediaExtractor+MediaMuxer on Android) and a download button
for the full video. The playback routing currently uses a fixed 10 MB file-size
threshold to decide between streaming and full-download paths. That is a poor proxy
for video length — bitrate varies widely across devices and quality settings.

The new approach uses actual video duration (in seconds) as the routing signal.

---

## Changes required

### Server — V19 migration

- Add `duration_seconds INTEGER` column to `uploads` (nullable — old rows have no value).
- Thread `durationSeconds: Int?` through `UploadRecord`, all SELECT queries, INSERT,
  `toJson()`, and `toUploadRecord()`.
- **Plaintext uploads:** extract duration via FFmpeg at confirm time (FFmpeg is already
  in the Docker image for thumbnail generation). Parse seconds from `ffprobe`-style output
  or from `ffmpeg -i` stderr. Store in `duration_seconds`.
- **Encrypted uploads:** client sends `durationSeconds` in the confirm body; server stores
  it as-is. Server never sees plaintext so cannot extract it independently.

### Web

**Threshold constant (hardcoded, revisit when settings page exists):**
```js
const FULL_PLAYBACK_THRESHOLD_SECONDS = 120  // 2 minutes
```

**Upload flow:**
- Before encrypting a video, read `videoElement.duration` (in seconds, float → round to Int).
- Send `durationSeconds` in the confirm body.

**Playback logic in `PhotoDetailPage.jsx`:**
```
if upload.durationSeconds is known:
    if durationSeconds <= FULL_PLAYBACK_THRESHOLD → play full video (existing path)
    else if previewStorageKey exists → play preview clip
    else → show thumbnail only (no video player)
else:  # old upload, no duration stored
    fall back to existing 10 MB file-size check
```

**Player UX — label while preview plays:**
- Below the `<video>` element, show a small label: `Preview · 0:08 of 5:32`
- Current position ticks up via `timeupdate` event. Full duration from `upload.durationSeconds`.
- Only shown when playing a preview clip (not for full-video playback).

**Player UX — overlay on end:**
- Listen for the `ended` event on the `<video>` element.
- Freeze (video already pauses at end by default).
- Render a full translucent overlay covering the player:
  - Headline: **Preview ended**
  - Body: "This is a 15-second preview. The full video is 5 minutes 32 seconds."
  - Button: **Download full video** (triggers existing download handler)
- Tapping/clicking outside the button dismisses the overlay back to the frozen frame
  with the label still visible.
- Overlay does NOT appear for full-video playback.

### Android

**Per-device DataStore preference:**
- Key: `playback_threshold_seconds`
- Type: Int (sentinel value `Int.MAX_VALUE` = No limit)
- Default: `300` (5 minutes)

**Settings screen (`SettingsScreen.kt`):**
- New "Video playback" section with a segmented control:
  `1 min | 5 min | 15 min | No limit`
  Maps to: `60 | 300 | 900 | Int.MAX_VALUE`
- Default selection: 5 min.

**Upload flow (`Uploader.kt`):**
- After opening the file with `MediaExtractor`, read duration from
  `getTrackFormat(videoTrackIndex).getLong(MediaFormat.KEY_DURATION)` (returns microseconds).
  Convert to seconds: `(durationUs / 1_000_000L).toInt()`.
- Include `durationSeconds` in the confirm JSON body.

**`Upload` model + `HeirloomsApi`:**
- Add `durationSeconds: Int?` to `Upload` data class.
- Parse from JSON in `toUpload()`.

**Playback logic (`PhotoDetailViewModel.kt`):**
- Read `playbackThresholdSeconds` from DataStore.
- Same decision tree as web, substituting the per-device threshold.
- `Int.MAX_VALUE` (No limit) always takes the full-video path regardless of duration.
- Old uploads without `durationSeconds` fall back to the 10 MB file-size check.

**Player UX — label while preview plays:**
- Below the `VideoPlayer` composable, show a `Text`: `Preview · 0:08 of 5:32`
- Drive the current-position counter from ExoPlayer's `currentPosition` polled via
  a `LaunchedEffect` + `delay(500ms)` loop (or `Player.Listener.onPositionDiscontinuity`).
- Full duration from `upload.durationSeconds`.
- Only shown when playing a preview clip.

**Player UX — overlay on end:**
- Listen for `Player.STATE_ENDED` in `Player.Listener.onPlaybackStateChanged`.
- Set a `previewEnded: Boolean` state flag in the ViewModel (or via a `StateFlow`).
- When true, render a `Box` overlay covering the player with translucent background:
  - Headline: **Preview ended**
  - Body text with full duration.
  - **Download full video** button (calls `vm.downloadFullFile()`).
- Dismiss by tapping outside the button (set flag back to false).
- Flag resets to false when a new upload is loaded.

---

## Decisions already made

| Decision | Choice |
|---|---|
| Android threshold options | 1 min / 5 min / 15 min / No limit |
| Android default | 5 minutes (300s) |
| Web default | 2 minutes (120s) |
| "No limit" meaning | Always play full video, preview system invisible |
| Preview end behaviour | Freeze on last frame + full translucent overlay |
| Overlay dismissal | Tap/click outside button |
| Label format | "Preview · 0:08 of 5:32" |
| Label visibility | Only when playing a preview, not full-video playback |

---

## Out of scope

- Web settings page (deferred — revisit at Milestone 8 multi-user work).
- Retroactive duration extraction for existing uploads (old rows stay NULL, fall back
  to file-size check).
- Seek support within the preview clip.
