---
id: BUG-015
title: Web garden shared plot row still stale after trellis routing — BUG-012 fix incomplete
category: Bug Fix
priority: Medium
status: done
assigned_to: Developer
depends_on: []
touches:
  - HeirloomsWeb/src/pages/GardenPage.jsx
  - HeirloomsWeb/src/test/garden.test.jsx
estimated: 1-2 hours (agent)
---

## Background

BUG-012 was marked done with a `setTimeout` 1.5s delayed re-fetch in `handleQuickUpdateTags`
(`setPlotRefreshKey((k) => k + 1)` immediate + 1500ms delayed). Retested in TST-007
(2026-05-15) — the fix does not work.

## Steps to reproduce

1. Log in on web.
2. Upload a photo — it appears in Just Arrived.
3. Create a trellis: tag `new-tag` → target shared plot, `requires_staging = false` (auto-approve).
4. Tag the photo with `new-tag` from the Garden page.
5. Photo disappears from Just Arrived (correct).
6. Wait 10+ seconds — the shared plot row in the garden does **not** update.
7. Manually refresh the page — the photo now appears in the shared plot row.

## Expected behaviour

The shared plot row updates within ~2 seconds of the tag being applied, without any
page refresh.

## Investigation notes

The BUG-012 fix assumed tagging goes through `handleQuickUpdateTags`. Possible reasons
the fix isn't working:
- The tag update in this flow may take a different code path that doesn't call
  `handleQuickUpdateTags`
- The `setPlotRefreshKey` increment may not be re-fetching the shared plot row data,
  only the Just Arrived row
- The server-side routing may complete after the 1.5s window

Check what function handles the tag-apply action on the Garden page and trace whether
`setPlotRefreshKey` is actually being called and whether the re-fetch includes shared
plot row data.

## Completion notes

### Root cause

The code path WAS going through `handleQuickUpdateTags` — that assumption was correct.
The `setPlotRefreshKey` increment was also being called for all rows correctly (both
immediate and delayed). The bug was elsewhere.

When `handleQuickUpdateTags` succeeds, it adds the upload ID to `justArrivedExclude`:

```js
setJustArrivedExclude((prev) => new Set([...prev, uploadId]))
```

This state is intended to be an optimistic hide-from-Just-Arrived. However, it was
being passed as `excludeIds` to **every** `SortablePlotRow` as well (line 1317
pre-fix). Since `PlotItemsRow` filters `visibleItems` using `excludeIds`, the tagged
photo was being hidden from ALL plot rows including the shared plot row it was just
routed to. The server correctly routed the photo to the target plot, the re-fetch
correctly fetched it — but the client immediately filtered it out of the display.

The Just Arrived row correctly received `excludeIds` (optimistic removal), but user
and shared plot rows must never receive this prop.

### Fix

Removed `excludeIds={justArrivedExclude}` from the `SortablePlotRow` render in
`GardenPage.jsx`. The prop is now only passed to `SystemPlotRow` (Just Arrived).

The BUG-012 setTimeout mechanism is unchanged — it remains a useful safety net to
give the server time to complete trellis routing before the re-fetch.

### Test added

Added regression test in `garden.test.jsx`:
`BUG-015: tagged item is excluded from Just Arrived but still visible in user plot row`

The test tags a photo from the Just Arrived row, then verifies:
- The photo's "Edit tags" button is gone from Just Arrived (optimistic exclusion works)
- The photo's "Edit tags" button is still present in the Family (user plot) row

### Tests

All pre-existing tests pass. Pre-existing failures (2) are unchanged:
- `e2e/smoke/api-health.spec.ts` — Playwright test in vitest runner (framework mismatch, pre-existing)
- `auth.test.jsx` — `mount handler reads IDB pairing material` — pre-existing failure unrelated to this task
