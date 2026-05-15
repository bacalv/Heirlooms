---
id: BUG-008
title: Android staging flavor generates invite links with production domain
category: Bug Fix
priority: Medium
status: queued
depends_on: []
touches:
  - HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/main/DevicesAccessScreen.kt
assigned_to: Developer
estimated: 30 minutes
---

## Background

Discovered during TST-003 (2026-05-15). The invite link generated from Device & Access
on the staging Android app reads `https://heirlooms.digital/join?token=...` instead of
`https://test.heirlooms.digital/join?token=...`.

Same root cause as BUG-003: a hardcoded production constant that ignores the build
flavor.

## Root cause

`DevicesAccessScreen.kt` line 55:

```kotlin
private const val INVITE_BASE_URL = "https://heirlooms.digital"
```

## Fix

Replace the hardcoded constant with a reference to `BuildConfig.BASE_URL_OVERRIDE`
(same approach used to fix BUG-003 in `UploadWorker`), deriving the web base URL
from the API base URL:

```kotlin
private val INVITE_BASE_URL: String
    get() = if (BuildConfig.BASE_URL_OVERRIDE.isNotEmpty())
        BuildConfig.BASE_URL_OVERRIDE
            .replace("test.api.", "test.")
            .replace("api.", "")
            .trimEnd('/')
    else "https://heirlooms.digital"
```

Or alternatively, add a separate `WEB_BASE_URL` build config field per flavor in
`app/build.gradle.kts` (cleaner but slightly more config to maintain).

## Acceptance criteria

- Staging flavor generates invite links pointing to `https://test.heirlooms.digital`
- Production flavor continues to generate links pointing to `https://heirlooms.digital`

## Completion notes

Implemented 2026-05-15 on branch `agent/developer-3/SEC-007`.

**What was done:**
- Replaced the `private const val INVITE_BASE_URL = "https://heirlooms.digital"` constant in
  `DevicesAccessScreen.kt` with a computed `private val INVITE_BASE_URL: String` property that
  derives the web base URL from `BuildConfig.BASE_URL_OVERRIDE`:
  - Staging: `"https://test.api.heirlooms.digital"` → `"https://test.heirlooms.digital"` ✓
  - Prod: `""` (empty) → `"https://heirlooms.digital"` ✓
- Added `import digital.heirlooms.app.BuildConfig`.

**All acceptance criteria met:**
- Staging flavor generates invite links pointing to `https://test.heirlooms.digital`.
- Production flavor continues to generate links pointing to `https://heirlooms.digital`.
