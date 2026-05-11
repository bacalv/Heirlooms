# M8 Bugfix Iteration 1 — Android UX fixes

Android-only. Four targeted fixes found during post-M8 multi-user testing.
No server or web changes. Target version: v0.44.0.

---

## Bug 1 — Blank garden after "Go to Garden" from downloads screen

**Symptom.** When all uploads are complete in UploadProgressScreen (reached via
Burger → Uploads), pressing "Go to Garden" produces a blank white screen.
Pressing Back reveals the garden underneath.

**Root cause.** `onGoToGarden` in the `UPLOAD_PROGRESS` composable calls
`navController.navigateToTab(Routes.GARDEN)`. `navigateToTab` uses
`popUpTo(startDestination) { saveState = true }` + `restoreState = true`, which
triggers state save/restore machinery on the Garden composable unnecessarily.
The user arrived at UPLOAD_PROGRESS by pushing it on top of GARDEN, so a simple
pop is all that's needed.

**Fix.** `AppNavHost`, UPLOAD_PROGRESS composable: change `onGoToGarden` from
`navController.navigateToTab(Routes.GARDEN)` to `navController.popBackStack()`.

---

## Bug 2 — New Just Arrived item appears at position −1

**Symptom.** When a new item lands in Just Arrived and the row hasn't been
scrolled before, the new item appears partially off-screen to the left of the
leftmost visible item, as if it is at index −1.

**Root cause.** Compose's `LazyRow` with item keys preserves the scroll offset
so the previously-visible item stays in view when a new item is prepended at
index 0. The recovery scroll is:

```kotlin
LaunchedEffect(shouldScrollToStart) {
    if (shouldScrollToStart) listState.scrollToItem(0)
}
```

`shouldScrollToStart` is `isJustArrived && newlyArrivedIds.isNotEmpty()`. If a
second item arrives while `newlyArrivedIds` is already non-empty, the boolean
does not change, the effect does not re-fire, and the new item stays off-screen.

**Fix.** Key the effect on `newlyArrivedIds` instead of `shouldScrollToStart`:

```kotlin
LaunchedEffect(newlyArrivedIds) {
    if (isJustArrived && newlyArrivedIds.isNotEmpty()) listState.scrollToItem(0)
}
```

---

## Bug 3 — Tapping Garden tab doesn't always dismiss open panels

**Symptom.** While the BurgerPanel sheet is open (or after navigating to a
screen launched from it, e.g. Settings), pressing the Garden tab in the bottom
nav does not fully return to the garden. The BurgerPanel sheet may remain
visible on top.

**Root cause.** The tab selection path calls `navController.navigateToTab()`
(which correctly pops the nav back stack to Garden) but never sets
`showBurger = false`. `BurgerPanel` is rendered unconditionally when
`showBurger == true`, so it reappears on top of the garden even after the nav
stack is correct.

**Fix.** In `onTabSelected` inside `MainNavigation`, set `showBurger = false`
before calling `navigateToTab()`. Also hide the burger sheet so the animation
plays cleanly:

```kotlin
onTabSelected = { tab ->
    if (tab == Tab.Burger) {
        scope.launch { showBurger = true; burgerSheetState.show() }
    } else {
        scope.launch { burgerSheetState.hide() }
        showBurger = false
        navController.navigateToTab(tab.route)
    }
}
```

---

## Bug 5 — Loading spinner instead of plant icon for thumbnails

**Symptom.** While an encrypted thumbnail is fetching and decrypting,
a `CircularProgressIndicator` is shown. The failed state already shows
`OliveBranchIcon`. Loading and failed should look the same — both are
"not yet available".

**Fix.** In `HeirloomsImage.kt`, `EncryptedThumbnail`, replace the
`CircularProgressIndicator` in the loading `else` branch with `OliveBranchIcon`
at the same 24 dp size, matching the failed state. Remove the
`CircularProgressIndicator` import if it becomes unused.

---

## Files touched

- `AppNavigation.kt` — bugs 1, 3
- `GardenScreen.kt` — bug 2
- `HeirloomsImage.kt` — bug 5
- `VERSIONS.md`, `PROMPT_LOG.md`, `PA_NOTES.md` — version bump to v0.44.0
