---
id: BUG-013
title: Viewing photo detail on Android silently saves rotation — corrupts orientation on both platforms
category: Bug Fix
priority: High
status: queued
assigned_to: Developer
depends_on: []
touches:
  - HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/
  - HeirloomsWeb/src/
estimated: 2-3 hours (agent)
---

## Steps to reproduce

1. Upload a portrait photo from Android.
2. Open the photo in the detail/full-screen view on Android.
3. Do NOT press rotate.
4. Press back to return to the Garden.

## Expected behaviour

Orientation is unchanged. Thumbnail shows the photo in its original orientation.

## Actual behaviour

The thumbnail in the Garden on both Android and web shows the photo rotated — as if the
user had pressed the rotate button once. The rotation is persisted server-side. The detail
view is silently applying and saving a rotation on open.

## Notes

Discovered during TST-007 Journey 1 (2026-05-15). Affects both platforms since the rotation
is saved to the server.

Suspect the detail view is reading the photo's EXIF orientation, applying it visually, and
also firing the rotation API call to "normalise" the image — without any user action. The fix
should ensure the rotation API is only ever called in response to an explicit user gesture.

Check:
- `FullScreenMediaView` / detail view composable on Android — does it call any rotation endpoint on load or on exit?
- Web detail view — same question.
- Server `rotationContractRoute` — is it being called with a non-zero rotation on detail open?
