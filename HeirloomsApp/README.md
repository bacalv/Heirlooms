# Heirlooms

A minimal Android app (written in Kotlin) that adds your server as a target in Android's
"Share with" sheet. When you share any photo or video to it, the file is HTTP POSTed to
your configured endpoint with the correct `Content-Type` header.

Supports automatic retry with exponential backoff, and shows the upload result as a
system notification so you can see the outcome even after leaving the share sheet.

---

## Configuration (before building)

### 1. Set the app name (shown in the share sheet)
Edit `app/src/main/res/values/strings.xml`:
```xml
<string name="app_name">My Heirlooms</string>
```

### 2. Set the upload endpoint
Edit `app/src/main/assets/config.properties`:
```
endpoint=https://your-server.example.com/upload
```

---

## Building

Requires Android Studio or the Android SDK command-line tools, and Java 8+.

### Android Studio
Open the `HeirloomsApp` folder, let Gradle sync, then build or run as normal.

### Command line
```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

### Install via ADB
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## How it works

1. Open Photos, Gallery, or any app that supports the Android share sheet
2. Share any image or video and select **My Heirlooms** (or your chosen name)
3. A "Uploading…" toast appears and the activity dismisses immediately
4. The upload runs in the background via a coroutine on `Dispatchers.IO`
5. A system notification appears when the upload completes or fails

The activity is transparent — no UI appears beyond the initial toast and the
final notification.

---

## Retry behaviour

Failed uploads are automatically retried with **exponential backoff**:

| Scenario | Retried? |
|---|---|
| Network error (no connection, timeout) | Yes |
| HTTP 5xx (server error) | Yes |
| HTTP 4xx (bad request, unauthorised) | No — permanent client error |

Default policy: up to **3 attempts**, with delays of 1s → 2s between them.
To adjust, change `DEFAULT_MAX_ATTEMPTS` and `DEFAULT_INITIAL_DELAY_MS` in `Uploader.kt`.

---

## Notifications

On Android 13+ the first time you share a file, the OS may prompt for notification
permission. If permission is denied the app falls back gracefully to a Toast.

The notification channel is created automatically on first launch.

---

## What the server receives

A plain HTTP POST with:
- `Content-Type: image/jpeg` (or `video/mp4`, etc. — matched to the actual file)
- Raw file bytes as the request body — no multipart encoding, no JSON wrapper

---

## Running the tests

No emulator required — all tests run on the JVM.

```bash
./gradlew test
# Report: app/build/reports/tests/testDebugUnitTest/index.html
```

### Test coverage

| Area | Tests |
|---|---|
| `isValidEndpoint` | https/http accepted; null, empty, whitespace, non-http schemes rejected |
| `resolveMimeType` | known types passed through; null/empty/whitespace fall back to `application/octet-stream` |
| `isRetryable` | Success never retried; network errors and 5xx retried; 4xx not retried |
| Input validation | null/empty endpoint and null/empty bytes fail fast before any network call |
| HTTP 2xx | 200 and 201 return `Success` with correct `httpCode` and `attempts` |
| HTTP 4xx | 400, 401 return `Failure` with no retry — exactly 1 request sent |
| Retry on 5xx | Succeeds on 2nd attempt after one 500; succeeds on 3rd after two 503s |
| Retry exhaustion | Three consecutive 500s → `Failure` after exactly 3 requests |
| Retry on network error | Reconnects and succeeds after a disconnect |
| `maxAttempts = 1` | Only one request sent even for retryable errors |
| Request shape | POST method, exact `Content-Type` header, exact body bytes |
| MIME variations | `image/png`, `video/quicktime`, `application/octet-stream` all correct |

---

## Project structure

```
HeirloomsApp/
├── app/
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── assets/
│       │   │   └── config.properties        ← set your endpoint here
│       │   ├── kotlin/com/heirloom/app/
│       │   │   ├── Uploader.kt              ← upload logic + retry
│       │   │   └── ShareActivity.kt         ← intent handling, coroutines, notifications
│       │   └── res/values/
│       │       ├── strings.xml              ← set your app name here
│       │       └── styles.xml
│       └── test/kotlin/com/heirloom/app/
│           └── UploaderTest.kt              ← 32 JUnit tests
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```
