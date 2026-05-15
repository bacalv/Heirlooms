---
id: BUG-011
title: Web trellis creation form still shows "Flow name" and "Create flow" labels
category: Bug Fix
priority: Low
status: queued
depends_on: []
touches:
  - HeirloomsWeb/src/pages/FlowsPage.jsx
assigned_to: Developer
estimated: 15 minutes
---

## Background

REF-001 (Flow → Trellis rename) was applied partially to the web. The modal title
reads "New trellis" correctly, but the form label and submit button were missed:

- "Flow name" → should be "Trellis name"
- "Create flow" → should be "Create trellis"

Spotted during TST-003 (2026-05-15).

## Fix

Search `HeirloomsWeb/src/pages/FlowsPage.jsx` for "Flow name" and "Create flow"
and replace with "Trellis name" and "Create trellis" respectively.

## Completion notes

Implemented 2026-05-15.

Two string literals in `HeirloomsWeb/src/pages/FlowsPage.jsx` `FlowForm` component updated:
- Label text: `"Flow name"` → `"Trellis name"`
- Submit button: `'Create flow'` → `'Create trellis'` (the `'Update flow'` branch renamed to `'Update trellis'` for consistency)

Build verified clean.
