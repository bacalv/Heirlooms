---
id: BUG-004
title: New uploads never appear in Garden — system plot queries plot_items but uploads aren't added to it
category: Bug Fix
priority: High
status: done
depends_on: []
touches:
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/repository/upload/UploadRepository.kt
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/service/upload/UploadService.kt
assigned_to: Developer
estimated: 1–2 hours
---

## Background

Discovered during TST-003 manual staging test (2026-05-15). After a successful upload,
photos appear in Explore on both Android and web but never appear in the Garden on either
platform, even after a full restart.

## Root cause

Every user has a **system plot** (`is_system_defined = true`) created at registration
(`AuthService.kt:203`). `GardenViewModel` finds this system plot and queries:

```kotlin
api.listUploadsPage(plotId = systemPlot.id)
```

On the server, a non-shared `plotId` query filters:

```sql
id IN (SELECT upload_id FROM plot_items WHERE plot_id = ?)
```

But `UploadService.confirmEncryptedUpload()` and `confirmLegacyUpload()` only write to
the `uploads` table — they never insert a row into `plot_items`. So the system plot always
returns empty.

The `justArrived=true` fallback in `GardenViewModel` (which correctly queries uploads with
empty tags) is never reached because `systemPlot` is always non-null.

## Fix options

**Option A (recommended) — server-side: treat system plot query as justArrived**

In `UploadRepository.listUploadsPaginated`, when the resolved plot is `isSystemDefined`,
apply the `justArrived` SQL conditions (empty tags, not composted, not in capsule) instead
of the `plot_items` join. This requires no schema changes and no changes to upload paths.

```kotlin
if (plot.isSystemDefined) {
    // same conditions as justArrived=true
    conditions += "tags = '{}'::text[]"
    conditions += "composted_at IS NULL"
    conditions += """NOT EXISTS (
        SELECT 1 FROM capsule_contents cc
        JOIN capsules c ON c.id = cc.capsule_id
        WHERE cc.upload_id = uploads.id AND c.state IN ('open','sealed')
    )"""
} else if (plot.visibility == "shared") {
    // existing shared plot logic
} else {
    conditions += "id IN (SELECT upload_id FROM plot_items WHERE plot_id = ?)"
}
```

**Option B — server-side: insert into plot_items on upload**

In `UploadService.confirmEncryptedUpload()` and `confirmLegacyUpload()`, after recording
the upload, insert a row into `plot_items` for the user's system plot. Requires fetching
the system plot ID per user on every upload.

Option A is preferred: fewer moving parts, no extra DB write per upload, consistent with
the existing `justArrived` logic.

## Acceptance criteria

- After a successful upload with no tags, the photo appears in the Garden's "Just Arrived"
  row on both Android and web without requiring any manual intervention
- Explore continues to show the same items as before
- Uploading a photo with tags and a matching trellis still routes it to the correct plot
  (not Just Arrived)
- Existing integration tests pass; add a test for the system plot query returning uploads

## Completion notes

Implemented Option A (server-side fix, no schema changes).

### Change: `UploadRepository.kt`

In `listUploadsPaginated`, added an `else if (plot.isSystemDefined)` branch between the
`criteriaJson != null` check and the existing `else` (plot_items join). When the queried
plot is the system-defined "just arrived" plot, the method now applies the same three SQL
conditions as the top-level `justArrived=true` path:

- `tags = '{}'::text[]` — only untagged uploads
- `composted_at IS NULL` — exclude composted
- `NOT EXISTS (capsule_contents join)` — exclude uploads in open/sealed capsules

No `setters` entry is added for this branch (the conditions use no plot parameter).

The `PlotRecord.isSystemDefined` field was already populated by `getPlotByIdForUser` via
`getPlotByIdConn`, so no changes were needed to the repository or domain model.

### Change: `IsolationTest.kt`

Added `aliceGardenUploadId` — a second upload created for Alice in `@BeforeAll` that is
never added to any capsule. Added a new integration test:

> `Querying system plot returns untagged uploads not in capsule (Garden fix)`

The test retrieves Alice's system plot ID via `GET /api/plots`, then calls
`GET /api/content/uploads?plot_id=<systemPlotId>`, and asserts:

- `aliceGardenUploadId` (untagged, not in capsule) appears in the result
- `aliceUploadId` (in an open capsule) does not appear

No other files were modified. `UploadService.kt` did not need changes (Option A).
