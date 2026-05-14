# Task 1 Changes Summary

## What was built

### Task 1A: Drag+Drop + Paste Upload

- **Global drag+drop** on all authenticated pages. When files are dragged over the browser window a full-screen overlay appears ("Drop files here to upload"). On drop, image/video files are encrypted and uploaded to the garden default (same path as the Plant button). The overlay disappears correctly on drag-out using a depth counter to handle the `dragleave`-on-every-child problem.

- **Paste-to-upload** on Garden and Shared Plots pages. Pasting an image from the clipboard triggers the same encrypted upload flow. Non-image clipboard content is silently ignored.

### Task 1B: Auto-Approve Toggle on Flows

- The flow create and edit forms now always show an "Auto-approve" toggle (was previously hidden unless the target plot was shared). Toggle is inverted from the underlying `requiresStaging` field — checked means auto-approve ON (staging OFF). Defaults to OFF (staging required). Correctly round-trips the server value when editing an existing flow.

- **Server:** No changes required. `POST /api/flows` and `PUT /api/flows/:id` already accept `requiresStaging` in the request body.

## Files changed

| File | Change |
|------|--------|
| `HeirloomsWeb/src/components/DragDropProvider.jsx` | New — global drag+drop overlay + context |
| `HeirloomsWeb/src/AuthLayout.jsx` | Wraps layout with DragDropProvider, handles drop uploads, shows status toast |
| `HeirloomsWeb/src/pages/GardenPage.jsx` | Export `encryptAndUpload`, split `handlePlant` → `handlePlantFiles`, add paste useEffect |
| `HeirloomsWeb/src/pages/SharedPlotsPage.jsx` | Import DragDropContext, add paste useEffect |
| `HeirloomsWeb/src/pages/FlowsPage.jsx` | Replace conditional shared-plot staging checkbox with unconditional auto-approve toggle |
| `HeirloomsWeb/src/test/compost.test.jsx` | Fix pre-existing mock ordering bug (URL-dispatch instead of sequential) |
| `HeirloomsWeb/src/test/explore.test.jsx` | Fix pre-existing stale assertion (tag_criteria → criteria expression tree) |

## Server changes

None. FlowHandler.kt already handles `requiresStaging` on both create and update endpoints.

## Test results

All 116 tests pass (`npm run test -- --run`).

Two pre-existing test failures were fixed:
1. `compost.test.jsx` — GardenPage mock used sequential `mockResolvedValueOnce` but `Promise.all([plots, sharedMemberships])` meant fetch calls happened in non-sequential order. Fixed by switching to URL-dispatch `mockImplementation`.
2. `explore.test.jsx` — Test asserted `body.tag_criteria` but ExplorePage sends `body.criteria` (expression tree). Fixed to match current API shape.
