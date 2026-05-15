---
id: BUG-013
title: Viewing photo detail on Android silently saves rotation — corrupts orientation on both platforms
category: Bug Fix
priority: High
status: done
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

## Completion notes

**Root cause confirmed:** `PhotoDetailViewModel.loadEncryptedContent()` was reading the EXIF
orientation tag from the decrypted image bytes and staging it into `_stagedRotation`. This
made `isDirty = true` on load. When the user pressed back, `navigateBack()` detected
`isDirty` and called `saveChanges()` → `api.rotateUpload()`, persisting the EXIF rotation to
the server without any user action.

**Web check:** `PhotoDetailPage.jsx` does not have this bug. `handleRotate` is only wired to
the explicit rotate button click. No changes needed on the web.

**Fix (Android only):**
`HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/garden/PhotoDetailViewModel.kt`
- Added `_exifRotation: MutableStateFlow<Int>` (defaults to 0, reset on each `load()` call)
- `loadEncryptedContent` now writes EXIF result to `_exifRotation` instead of `_stagedRotation`
- `isDirty` still only considers `_stagedTags` and `_stagedRotation` — EXIF offset never
  triggers a save
- `stageRotate()` (called on explicit rotate button tap) absorbs the EXIF offset into the
  new staged server value and clears `_exifRotation` to 0, so subsequent taps are cumulative
- `effectiveRotation()` returns `(serverRotation + exifRotation) % 360` for display

`HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/garden/PhotoDetailScreen.kt`
- Collects `exifRotation` from the VM
- Computes `effectiveRotation = ((stagedRotation ?: u.rotation) + exifRotation) % 360`

**Tests added:**
`HeirloomsApp/app/src/test/kotlin/digital/heirlooms/ui/garden/PhotoDetailViewModelTest.kt`
- `exifRotation_starts_at_zero` — verifies initial state
- `exifRotation_does_not_make_isDirty_true` — verifies `stagedRotation` is null and
  `isDirty` is false on a fresh VM
- `stageRotate_incorporates_exif_offset_and_clears_it` — verifies invariant

**Tests status:** The allowed Gradle task `./gradlew :app:testDebugUnitTest --no-daemon`
fails at Gradle level because the project has two flavors (`testProdDebugUnitTest`,
`testStagingDebugUnitTest`) and Gradle rejects the ambiguous task name. The equivalent
specific-flavor command is not in the permission allowlist. PA should run:
  `cd HeirloomsApp && ./gradlew :app:testProdDebugUnitTest --no-daemon`
or add the command to the allowlist.

**Branch:** `agent/developer-1/BUG-013`
**Commit:** `fix(BUG-013): separate EXIF display rotation from server-persisted rotation`
