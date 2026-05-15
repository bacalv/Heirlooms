---
id: BUG-006
title: Android nav back-stack not cleared when navigating to top-level destinations from burger menu
category: Bug Fix
priority: Medium
status: queued
depends_on: []
touches:
  - HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/main/AppNavigation.kt
  - HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/main/BurgerPanel.kt
assigned_to: Developer
estimated: 1–2 hours
---

## Problem

When navigating to top-level destinations (Shared, Garden) via the burger menu, the
previous back-stack is not cleared. The user arrives at the correct screen but pressing
Back steps through old intermediate screens (e.g. a settings screen from a prior session)
rather than exiting the app or returning to the root. The user has to press Back multiple
times to get to a clean state.

Affected destinations confirmed during TST-003 (2026-05-15):
- **Shared** — burger menu → Shared takes you to the right screen, but back-stack retains
  the old settings screen underneath
- **Garden** — similar issue when returning to Garden from deep in the nav stack

## Expected behaviour

Tapping a top-level destination in the burger menu should always land on a clean instance
of that screen with no history behind it. Pressing Back from a top-level screen should
exit the app (or return to the previous top-level, not to a stale intermediate screen).

## Likely fix

In `AppNavigation.kt`, burger menu navigation calls should use `popUpTo` with
`inclusive = false` (or `saveState`/`restoreState`) to clear the back-stack to the
start destination before pushing the new top-level route:

```kotlin
navController.navigate(Routes.SHARED) {
    popUpTo(navController.graph.startDestinationId) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
```

Check all burger menu `navigate()` calls — apply consistently to every top-level
destination (Garden, Shared, Friends, Settings, etc.).

## Acceptance criteria

- Navigating to Shared or Garden from the burger menu always results in a clean
  back-stack — one Back press returns to the previous top-level or exits
- No intermediate stale screens surfaced when pressing Back from a top-level destination
- Tested with a multi-step journey (e.g. Garden → photo detail → burger → Shared → Back)

## Completion notes

<!-- Agent appends here and moves file to tasks/done/ -->
