---
id: BUG-002
title: Remove "didn't take" messaging — replace with plain error strings
category: Bug Fix
priority: Medium
status: queued
depends_on: []
touches:
  - HeirloomsApp/app/src/main/res/values/strings.xml
  - HeirloomsApp/app/src/main/kotlin/digital/heirlooms/app/UploadWorker.kt
  - HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/brand/OliveBranchDidntTake.kt
  - HeirloomsApp/app/src/androidTest/kotlin/digital/heirlooms/ui/brand/OliveBranchDidntTakeTest.kt
  - HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/social/FriendsScreen.kt
  - HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/garden/GardenScreen.kt (DidntTake composable)
assigned_to: Developer
estimated: 1–2 hours
---

## Background

Discovered during TST-003 manual staging test (2026-05-15). After a failed upload, Android
showed a notification reading **"didn't take"** — a phrase left over from an old brand
concept ("OliveBranch didn't take"). This language is confusing to users and should have
been removed.

## Scope

### 1. String resources (`strings.xml`)

Replace both occurrences:

| Key | Current value | Replace with |
|-----|---------------|--------------|
| `upload_failed` | `didn't take` | `Upload failed` |
| `notif_didnt_take` | `didn't take` | `Upload failed` |

Rename `notif_didnt_take` to `notif_upload_failed` and update all references.

### 2. `UploadWorker.kt`

- Update `notifyResult(success = false)` to use the renamed string key `notif_upload_failed`.
- Check for any hardcoded "didn't take" strings in the file.

### 3. `OliveBranchDidntTake.kt`

The composable renders `"didn't take"` as hardcoded text (line 76) and is named after the
old concept. Assess whether it is called anywhere in production code — the current grep
shows **no production callers** (only its own test). If unused:

- Delete `OliveBranchDidntTake.kt`
- Delete `OliveBranchDidntTakeTest.kt`

If it is used (check thoroughly before deleting):

- Replace the hardcoded `"didn't take"` text with `stringResource(R.string.upload_failed)`
- Rename class and file to `UploadFailedAnimation` / `UploadFailedAnimationTest`

### 4. Sweep for any remaining occurrences

Run a search across all modules before marking done:

```bash
grep -r "didn.*take\|didnt.take\|DidntTake" \
  HeirloomsApp HeirloomsServer HeirloomsWeb HeirloomsiOS \
  --include="*.kt" --include="*.swift" --include="*.xml" \
  --include="*.tsx" --include="*.ts"
```

All hits must be resolved.

## Acceptance criteria

- No user-visible string in any platform reads "didn't take"
- `notif_didnt_take` string key is gone; `notif_upload_failed` (or equivalent) is in its place
- Failed upload notification on Android reads "Upload failed" (or equivalent plain language)
- `OliveBranchDidntTake` composable is either deleted (if unused) or renamed and updated
- Grep above returns zero hits

## Completion notes

<!-- Agent appends here and moves file to tasks/done/ -->