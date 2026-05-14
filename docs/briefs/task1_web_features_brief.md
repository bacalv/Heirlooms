# Task 1 — Web Features Brief: Drag+Drop, Paste Upload, Auto-Approve Toggle

## Overview

Two features added to HeirloomsWeb:

1. **Task 1A** — Global drag+drop file upload + paste-to-upload on Garden and Shared Plots pages
2. **Task 1B** — Auto-approve toggle on flow create/edit forms

---

## Task 1A: Drag+Drop + Paste Upload

### Global Drag+Drop

**Architecture:** A new `DragDropProvider` component (`src/components/DragDropProvider.jsx`) wraps all authenticated pages inside `AuthLayout`. It attaches window-level event listeners for `dragenter`, `dragover`, `dragleave`, and `drop`, and renders a full-screen overlay when files are being dragged over the window.

**Overlay:** Semi-transparent forest-green background with backdrop blur, a centered upload arrow icon, and the text "Drop files here to upload". The overlay is `pointer-events-none` so it doesn't interfere with the drop event.

**DragleaveEdge case:** `dragleave` fires on every child element the pointer crosses. Using a `dragDepth` counter (increment on `dragenter`, decrement on `dragleave`) avoids false negatives. Only file drags are handled — text and link drags are ignored by checking `dataTransfer.types.includes('Files')`.

**Upload flow:** On drop, non-image/video files are filtered out. Remaining files are passed to `encryptAndUpload` (exported from `GardenPage.jsx`) in sequence. Status messages appear in a fixed toast (bottom-right) in `AuthLayout`. The existing garden upload flow (dedup check, encrypt, thumbnail, confirm) is reused unchanged.

**Context:** `DragDropContext` exposes a `triggerUpload(files)` function so pages that handle paste can call the same upload path.

### Paste Upload

Paste listeners are added in `useEffect` hooks that attach/detach on mount/unmount:

- **GardenPage** — calls the page's own `handlePlantFiles()` directly (no context needed)
- **SharedPlotsPage** — calls `triggerUpload` from `DragDropContext`

Both only respond to clipboard items where `kind === 'file'` and `type.startsWith('image/')`. Non-image paste events (text, HTML, links) are silently ignored.

### Design decisions

- `encryptAndUpload` was given an `export` keyword — it was already a standalone module-scope function; this is the minimal change needed to share it.
- `handlePlant` was split into `handlePlantFiles(files)` (the logic) and `handlePlant(e)` (the input-event wrapper) so the paste handler can call the same code without simulating an input event.
- Drag events are wired on `window` (not `document`) for maximum coverage.
- Only image and video files are accepted; others are silently dropped.

---

## Task 1B: Auto-Approve Toggle on Flows

### Server (FlowHandler.kt)

No changes needed. Both `POST /api/flows` and `PUT /api/flows/:id` already accept `requiresStaging` (and the snake_case alias `requires_staging`) in the JSON body and store/return it correctly.

### Web (FlowsPage.jsx)

The `FlowForm` component already had `requiresStaging` state initialized from `initial?.requiresStaging ?? true`. The previous staging toggle was only shown when the selected plot had `visibility === 'shared'` and no `initial` was present (so it was invisible on edit forms).

**Change:** The condition was replaced with an unconditional toggle labelled "Auto-approve — items matching this flow go straight to the plot without staging review". The checkbox is inverted: checked = `requiresStaging === false` (auto-approve on = staging off). This matches the task description precisely.

**Round-trip:** When editing an existing flow, `initial.requiresStaging` seeds the state, so the toggle correctly reflects the server-stored value.

**Default:** New flows default to `requiresStaging = true` (auto-approve OFF), which is safe — content goes through staging review unless explicitly bypassed.

---

## Files Changed

- `HeirloomsWeb/src/components/DragDropProvider.jsx` — new file
- `HeirloomsWeb/src/AuthLayout.jsx` — wraps with DragDropProvider, provides upload handler
- `HeirloomsWeb/src/pages/GardenPage.jsx` — exports encryptAndUpload, splits handlePlant, adds paste useEffect
- `HeirloomsWeb/src/pages/SharedPlotsPage.jsx` — imports DragDropContext, adds paste useEffect
- `HeirloomsWeb/src/pages/FlowsPage.jsx` — replaces conditional staging checkbox with unconditional auto-approve toggle
- `HeirloomsWeb/src/test/compost.test.jsx` — fixed pre-existing mock ordering bug (sequential vs URL-dispatched mocks)
- `HeirloomsWeb/src/test/explore.test.jsx` — fixed pre-existing stale assertion (`tag_criteria` → `criteria` expression tree)
