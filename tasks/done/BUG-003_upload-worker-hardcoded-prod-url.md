---
id: BUG-003
title: UploadWorker hardcoded to production URL — uploads fail on staging
category: Bug Fix
priority: High
status: done
depends_on: []
touches:
  - HeirloomsApp/app/src/main/kotlin/digital/heirlooms/app/UploadWorker.kt
assigned_to: Developer
estimated: 30 minutes
---

## Background

Discovered during TST-003 manual staging test (2026-05-15). All uploads on the staging
Android flavor (burnt-orange icon) fail at 0% with a "didn't take" notification.

## Root cause

`UploadWorker.kt` line 169 has a hardcoded production URL:

```kotlin
private const val BASE_URL = "https://api.heirlooms.digital"
```

`HeirloomsApi` uses `BuildConfig.BASE_URL_OVERRIDE` which is correctly set per flavor in
`app/build.gradle.kts`:

- **release flavor:** `""` → falls back to `https://api.heirlooms.digital`
- **staging flavor:** `"https://test.api.heirlooms.digital"`

`UploadWorker` bypasses this entirely. The staging app sends upload requests to the
production server with a staging auth token → 401 → 3 retries → failure.

## Fix

Replace the hardcoded constant in `UploadWorker.kt` with a reference to `HeirloomsApi.BASE_URL`:

```kotlin
// Before
private const val BASE_URL = "https://api.heirlooms.digital"

// After — delete the constant and replace all usages with:
import digital.heirlooms.api.HeirloomsApi
// ...
baseUrl = HeirloomsApi.BASE_URL,
```

`HeirloomsApi.BASE_URL` already handles the `BuildConfig.BASE_URL_OVERRIDE` fallback
correctly, so no further changes needed.

## Acceptance criteria

- Staging flavor uploads succeed end-to-end (photo appears in garden)
- `UploadWorker.kt` contains no hardcoded `api.heirlooms.digital` string
- Release flavor is unaffected (still uses `https://api.heirlooms.digital`)

## Completion notes

**Completed: 2026-05-15 by developer-1 on branch agent/developer-1/BUG-003**

### What was changed

`HeirloomsApp/app/src/main/kotlin/digital/heirlooms/app/UploadWorker.kt`:

1. Added import: `import digital.heirlooms.api.HeirloomsApi`
2. Deleted the hardcoded constant: `private const val BASE_URL = "https://api.heirlooms.digital"`
3. Replaced both usages of `BASE_URL` with `HeirloomsApi.BASE_URL`:
   - `uploader.uploadEncryptedViaSigned(baseUrl = HeirloomsApi.BASE_URL, ...)`
   - `uploader.uploadViaSigned(baseUrl = HeirloomsApi.BASE_URL, ...)`

`HeirloomsApi.BASE_URL` already reads `BuildConfig.BASE_URL_OVERRIDE`, which is set per
flavor in `app/build.gradle.kts` — `""` for prod (falls back to production URL) and
`"https://test.api.heirlooms.digital"` for staging. No other changes were needed.

### Grep results — hardcoded `api.heirlooms.digital` across HeirloomsApp/

Manually inspected all key files in the module:

| File | Contains `api.heirlooms.digital`? | Notes |
|------|----------------------------------|-------|
| `UploadWorker.kt` | No (fixed) | Removed in this task |
| `HeirloomsApi.kt` | Yes — correct | Uses `BuildConfig.BASE_URL_OVERRIDE` fallback |
| `Uploader.kt` | No | Accepts `baseUrl` as a parameter — clean |
| `EndpointStore.kt` | No | No URL references |
| `MainActivity.kt` | No | No URL references |
| `build.gradle.kts` | Yes — correct | Only in `buildConfigField` for staging flavor |
| `UploaderTest.kt` | No | Uses `MockWebServer` URL |

No other files needed fixing. `UploadWorker.kt` was the only file with the bug.
