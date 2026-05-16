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

<!-- Agent appends here and moves file to tasks/done/ -->
