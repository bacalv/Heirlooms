# SE Brief: Android Garden — Plant button (camera + file picker)

**Date:** 10 May 2026
**Type:** Android-only. No server changes. No web changes.

---

## Goal

Add a FAB to the Garden that lets the user plant directly — without leaving the app to the
system camera or share sheet. Three source options: Photo (camera), Video (camera), File
(system picker for any type). After capture or selection, the user sees a preview and
confirms before the upload is queued. On success the upload appears in Just Arrived via the
existing 30-second poll.

---

## UX flow

```
Garden (FAB visible)
  └─ tap FAB → PlantSheet (bottom sheet)
       ├─ "Photo"  → system camera (TakePicture) → PlantPreview (image)
       │     ├─ "Plant"  → enqueue UploadWorker → back to Garden (Just Arrived picks it up)
       │     ├─ "Retake" → dismiss preview, re-launch camera immediately
       │     └─ back gesture / "Cancel" → back to Garden
       ├─ "Video"  → system camera (CaptureVideo) → PlantPreview (video, plays back)
       │     └─ same Plant / Retake / Cancel options
       └─ "File"   → system file picker (OpenDocument */*) → PlantPreview (file name + icon)
             ├─ "Plant"  → copy to cacheDir → enqueue UploadWorker → back to Garden
             └─ back gesture / "Cancel" → back to Garden
```

Post-plant: stay in Garden. The garden's 30-second poll picks up the new upload in Just
Arrived. No immediate navigate-to-detail.

---

## Architecture

### State machine — in `GardenScreen.kt` (not a separate VM)

```kotlin
sealed class PlantState {
    object Idle : PlantState()
    data class Preview(val uri: Uri, val mimeType: String, val isRetake: Boolean = false) : PlantState()
    object Queuing : PlantState()   // brief spinner while copying + enqueueing
}
```

- `plantState` is `remember { mutableStateOf<PlantState>(PlantState.Idle) }`
- Kept in `GardenScreen`, not `GardenViewModel` — it is ephemeral UI state that doesn't
  survive config changes (the camera intent will also restart on rotation anyway).
- `BackHandler` active when `plantState != Idle`: resets to `Idle`.

### ActivityResult launchers — registered in `GardenScreen`

```kotlin
// Temp URI written by TakePicture / CaptureVideo — created once and reused for retakes.
var captureUri by remember { mutableStateOf<Uri?>(null) }

val takePictureLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.TakePicture()
) { success ->
    val uri = captureUri
    if (success && uri != null) plantState = PlantState.Preview(uri, "image/jpeg")
    else plantState = PlantState.Idle
}

val captureVideoLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.CaptureVideo()
) { success ->
    val uri = captureUri
    if (success && uri != null) plantState = PlantState.Preview(uri, "video/mp4")
    else plantState = PlantState.Idle
}

val openFileLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument()
) { uri ->
    if (uri != null) {
        // Resolve mimeType from ContentResolver; default to application/octet-stream.
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        plantState = PlantState.Preview(uri, mime)
    } else {
        plantState = PlantState.Idle
    }
}
```

`captureUri` is created fresh for each capture (including retakes):

```kotlin
fun launchCamera(type: PlantType) {
    val ext = if (type == PlantType.Photo) "jpg" else "mp4"
    val file = File(context.cacheDir, "capture-${UUID.randomUUID()}.$ext")
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    captureUri = uri
    if (type == PlantType.Photo) takePictureLauncher.launch(uri)
    else captureVideoLauncher.launch(uri)
}
```

### Retake

In `PlantPreviewOverlay`, the "Retake" button calls `onRetake()` which is:

```kotlin
onRetake = {
    // Determine type from previous capture mimeType and immediately re-launch.
    val type = if ((plantState as? PlantState.Preview)?.mimeType?.startsWith("video") == true)
        PlantType.Video else PlantType.Photo
    plantState = PlantState.Idle
    launchCamera(type)
}
```

This works because `launchCamera` is a local function in the `GardenScreen` composable and
can be called from any click handler within the same scope.

### Enqueue

The "Plant" button in `PlantPreviewOverlay` calls `onPlant(uri, mimeType)`:

```kotlin
onPlant = { uri, mimeType ->
    plantState = PlantState.Queuing
    scope.launch(Dispatchers.IO) {
        // For camera captures (FileProvider URI): file is already on disk; use the path.
        // For file-picker URIs (content://): copy to cacheDir via ShareActivity pattern.
        val filePath: String? = when {
            uri.scheme == "file" -> uri.path
            uri.scheme == "content" && uri.path?.contains("cache") == true ->
                // FileProvider content URI — resolve the underlying File path.
                captureUri?.path  // captureUri was the File we created
            else -> {
                // Document URI from OpenDocument — copy to temp file.
                copyContentUriToCache(context, uri, mimeType)
            }
        }
        if (filePath == null) { plantState = PlantState.Idle; return@launch }

        val data = workDataOf(
            UploadWorker.KEY_FILE_PATH to filePath,
            UploadWorker.KEY_MIME_TYPE to mimeType,
            UploadWorker.KEY_API_KEY to apiKey,
            UploadWorker.KEY_TAGS to emptyArray<String>(),
        )
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(data)
            .addTag(UploadWorker.TAG)
            .build()
        WorkManager.getInstance(context).enqueue(request)
        plantState = PlantState.Idle
    }
}
```

`copyContentUriToCache(context, uri, mimeType)` mirrors `ShareActivity.copyToTempFile` —
opens the content stream, writes to a temp file in `cacheDir`, returns the path.

For FileProvider URIs (camera captures), the file already exists at `captureUri`'s path.
`TakePicture` writes the JPEG directly to the file we created. No copy needed.

---

## New files

### `HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/garden/PlantSheet.kt`

`ModalBottomSheet` with three `ListItem`-style rows: Photo, Video, File.

```kotlin
@Composable
fun PlantSheet(
    onDismiss: () -> Unit,
    onPhoto: () -> Unit,
    onVideo: () -> Unit,
    onFile: () -> Unit,
) { ... }
```

Each row: leading icon + label text. Forest foreground on Parchment background.
Icons: `Icons.Filled.PhotoCamera`, `Icons.Filled.Videocam`, `Icons.Filled.InsertDriveFile`.

### `HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/garden/PlantPreviewOverlay.kt`

Full-screen composable (Parchment background, `fillMaxSize`) shown via `AnimatedVisibility`
when `plantState is PlantState.Preview`. Sits inside a `Box` that wraps the entire Garden
content.

```kotlin
@Composable
fun PlantPreviewOverlay(
    uri: Uri,
    mimeType: String,
    onPlant: (Uri, String) -> Unit,
    onRetake: (() -> Unit)?,  // null for File previews (no retake)
    onCancel: () -> Unit,
)
```

**Layout:**
- Top bar: back arrow (→ `onCancel`) + "Preview" title
- Content area (`weight(1f)`): see below
- Bottom bar: "Retake" button (if `onRetake != null`, outline style) + "Plant" button
  (Forest fill). If `mimeType` is not image or video (i.e., file), show file name + MIME
  type icon centred in the content area instead.

**Image preview:** `AsyncImage(model = uri, ...)` with `ContentScale.Fit`. Coil handles
`content://` and `file://` URIs equally.

**Video preview:** `AndroidView` wrapping `PlayerView` (media3). Create an `ExoPlayer` in a
`DisposableEffect`, set `MediaItem.fromUri(uri)`, call `prepare()` and `playWhenReady = true`.
Release the player on `onDispose`. Mirrors the `VideoPlayer` composable in `PhotoDetailScreen`.

**File preview:** a centred `Column` with a large `InsertDriveFile` icon (64dp, Forest) and
the file name below it (sourced from `ContentResolver.query` on the URI, or the URI's last
path segment as fallback).

---

## Modified files

### `AndroidManifest.xml`

Add permissions:
```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

Add FileProvider inside `<application>`:
```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_provider_paths" />
</provider>
```

### `res/xml/file_provider_paths.xml` (new file)

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="captures" path="." />
</paths>
```

`cache-path` covers `context.cacheDir`, where we write all capture temp files.

### `GardenScreen.kt`

- Add plant state, launchers, `launchCamera`, `copyContentUriToCache`, FAB, and overlays as
  described above.
- Wrap existing `GardenContent` in a `Box`. Add `PlantPreviewOverlay` and a brief
  `QueuingOverlay` (WorkingDots + "Planting…") as additional `Box` children, shown via
  `AnimatedVisibility`.
- Add `FloatingActionButton` (Forest background, `Icons.Filled.Add`, 56dp) anchored to
  `Alignment.BottomEnd` with `16.dp` padding. Hidden when `plantState != Idle` (no FAB while
  preview is showing).
- `BackHandler(enabled = plantState != PlantState.Idle) { plantState = PlantState.Idle }`.
- Add `var showPlantSheet by remember { mutableStateOf(false) }`. FAB click sets it true.
  `PlantSheet` shown when true; its dismiss/option callbacks handle camera/picker launch and
  set showPlantSheet = false.

### `AppNavigation.kt`

No changes. The plant flow is entirely within `GardenScreen`.

---

## Permissions handling

`TakePicture` and `CaptureVideo` require `CAMERA`. Request it at runtime before launching,
using `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())`.
`RECORD_AUDIO` is required by the system camera app for video — declared in the manifest but
the system camera handles the runtime prompt itself; we do not need to request it explicitly.

```kotlin
val cameraPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
) { granted ->
    if (granted) launchCamera(pendingCaptureType)
    // If denied: do nothing (FAB sheet will re-appear on next tap)
}
```

`pendingCaptureType` is a `var` set before calling the permission launcher.

For the file picker (`OpenDocument`), no runtime permission is required on Android 13+.

---

## Acceptance criteria

1. FAB visible on Garden; tapping it shows a bottom sheet with Photo, Video, File.
2. Photo: system camera opens; captured JPEG shown in preview; "Retake" re-opens camera;
   "Plant" queues a WorkManager upload job; item appears in Just Arrived.
3. Video: system camera opens in video mode; preview plays back in-app; "Retake" / "Plant"
   work as above.
4. File: system file picker opens for any type; file name shown in preview; "Plant" copies to
   cache and queues upload; item appears in Just Arrived.
5. Back gesture at any point in the flow returns to Garden without uploading.
6. Camera permission denied: no crash; sheet closes gracefully.
7. Existing share-sheet uploads continue to work unchanged.
8. `./gradlew :HeirloomsApp:app:compileDebugKotlin` passes.
