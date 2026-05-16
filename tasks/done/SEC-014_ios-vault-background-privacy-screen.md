---
id: SEC-014
title: iOS vault — privacy screen on backgrounding
category: Security
priority: Medium
status: queued
assigned_to: Developer
depends_on: [SEC-013]
touches:
  - HeirloomsiOS/HeirloomsiOS/App/HeirloomsApp.swift
  - HeirloomsiOS/HeirloomsiOS/ (SceneDelegate or AppDelegate if present)
estimated: 2–3 hours
---

## Goal

Prevent vault content from appearing in the iOS app switcher screenshot. The equivalent of Android's `FLAG_SECURE` (implemented in SEC-009 Part 1).

## Background

When an iOS app is backgrounded, the OS takes a screenshot for the app switcher. If the vault or any media view is visible at that moment, the screenshot will contain unencrypted content. This is visible to anyone who opens the app switcher — including someone who picks up an unlocked phone.

SEC-009 Part 1 closed this on Android via `FLAG_SECURE`. iOS uses a different mechanism.

## Implementation

In the app lifecycle, override `applicationWillResignActive` (or `sceneWillResignActive` in a Scene-based app) to overlay a privacy view over the window. Remove the overlay in `applicationDidBecomeActive` / `sceneDidBecomeActive`.

Approach:
1. Create a `PrivacyScreenView` (a solid view using the Heirlooms brand background colour, optionally with the Heirlooms logo centred)
2. In `sceneWillResignActive`: add `PrivacyScreenView` as a fullscreen subview of the key window
3. In `sceneDidBecomeActive`: remove the `PrivacyScreenView`

This should be applied to ALL windows, not just vault screens — the same rationale as FLAG_SECURE: it is simpler and more future-proof to always hide the content.

## Acceptance criteria

- Backgrounding the app (pressing Home or switching apps) shows the privacy screen rather than vault content in the app switcher
- The privacy screen is removed when the app is foregrounded
- No visual glitch — the overlay appears before the OS takes its screenshot

## Notes

- SEC-013 (iOS security review) should be done first so its findings can inform whether additional Keychain access flag changes should be included in the same worktree.
- This task does not require a server change.

## Completion notes

**Completed:** 2026-05-16
**Agent:** developer-9

### What was done

Modified `HeirloomsiOS/HeirloomsApp/App.swift`:

1. Added `@Environment(\.scenePhase) private var scenePhase` to `HeirloomsApp`.

2. Applied a conditional `.overlay` on the `WindowGroup`'s `RootView` that renders `PrivacyScreenView()` whenever `scenePhase != .active`. This covers both `.inactive` (the state the OS transitions to just before taking the app-switcher screenshot) and `.background`.

3. Added a new `PrivacyScreenView` struct: a black fullscreen `ZStack` with the `lock.shield.fill` SF Symbol (blue, 64pt) and "Heirlooms" label centred — matching the brand visual used in `ActivateView`.

4. Added a subtle `.easeInOut(duration: 0.15)` animation so the overlay fades in/out smoothly without jarring the user on foreground.

### Design decisions

- **SwiftUI `scenePhase` over UIKit delegate:** The app uses the SwiftUI `@main App` lifecycle (no `SceneDelegate.swift` file exists). Using `@Environment(\.scenePhase)` is the idiomatic approach and avoids mixing UIKit delegate callbacks into the SwiftUI hierarchy.

- **`.inactive` is the correct trigger:** `scenePhase` transitions to `.inactive` before the OS takes its screenshot (equivalent to `sceneWillResignActive`). Showing the overlay on both `.inactive` and `.background` is safe — it means the overlay is also visible if the user inspects the app switcher after the app is fully backgrounded.

- **Applied to ALL windows:** The overlay is on the root `WindowGroup`, so it covers every screen in the app — matching the task requirement and the rationale for Android's FLAG_SECURE.

- **No SceneDelegate needed:** A dedicated `SceneDelegate` would add boilerplate for no benefit. The SwiftUI-native approach is cleaner and equally reliable.

- **Brand background colour:** The existing app uses `Color.black` for full-screen media views (`FullScreenMediaView`) and `.blue` as the accent/icon colour. `PrivacyScreenView` uses the same palette.

### No server changes

This is a pure iOS client change. No server, Android, or web changes were required.

### New tasks spawned

None.
