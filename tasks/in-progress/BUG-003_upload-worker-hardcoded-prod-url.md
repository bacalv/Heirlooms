---
id: BUG-003
title: UploadWorker hardcoded to production URL — uploads fail on staging
category: Bug Fix
priority: High
status: queued
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

<!-- Agent appends here and moves file to tasks/done/ -->
