---
id: BUG-028
title: Biometric gate flashes prompt but doesn't block vault access
category: Bug Fix
priority: Medium
status: queued
depends_on: []
touches:
  - HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/auth/
  - HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/main/MainActivity.kt
assigned_to: Developer
estimated: 2–3 hours
---

## Problem

When the biometric gate is enabled (SEC-015), reopening the app shows a biometric
prompt briefly, but it dismisses itself and the vault opens without requiring
authentication. The gate triggers but doesn't enforce.

Also: the web app shows no informational note when `require_biometric=true` is set
on the account.

Found during TST-010 (2026-05-16), Journey 17f.

## Fix

Investigate the biometric prompt flow:
1. The prompt may be auto-resolving (e.g., if the device has face recognition that
   matches passively before the user interacts)
2. Or the success/failure callback is wired incorrectly — cancellation is treated
   as success
3. The gate should block navigation to the vault until `BiometricPrompt.SUCCESS`
   is received

For web: add the informational note to Settings or the vault unlock page when
`require_biometric=true` is returned from the account endpoint.

## Acceptance criteria

- With biometric gate enabled, the vault requires explicit biometric authentication
  on every cold open — cancel/dismiss does not grant access
- Web app shows a note explaining that biometric protection is active on mobile devices

## Completion notes

**Completed 2026-05-17 by Developer (agent/developer-14/BUG-028)**

### Root cause

The bug was in `MainApp.kt`. `biometricPassed` was declared with `rememberSaveable`
instead of `remember`. `rememberSaveable` persists state in Android's Activity
saved-state Bundle, which survives process death and recreation. On a cold open after
the OS had killed the backgrounded app, Android restores the Bundle and
`biometricPassed` is set to `true` from the previous session — the gate is never shown.

The callbacks in `BiometricGateScreen.kt` were correct: cancellation calls `onError`,
not `onSuccess`, so "cancel/dismiss treated as success" was not the issue.

### Fix

`MainApp.kt`: changed `biometricPassed` from `rememberSaveable` to `remember`.
`remember` is scoped to the current process lifetime; on every cold open the initial
value is re-evaluated from `store.getRequireBiometric()`, so the gate fires correctly.
The other three state vars (`sessionToken`, `welcomed`, `vaultReady`) intentionally
keep `rememberSaveable` because they hold non-sensitive session routing state that
should survive config changes.

Added an explanatory comment describing why `biometricPassed` must not use
`rememberSaveable`.

### Web status

The web informational note (`require_biometric=true` → lock icon + explanation) was
already implemented in `AccessPage.jsx` (the web's account/settings page) as part of
a prior partial implementation. No web changes were required.

### Spawned tasks

- `BUG-030_create-friend-invite-uses-get-not-post.md` — pre-existing test failure
  discovered during test run; `createFriendInvite()` uses GET instead of POST. Not
  related to BUG-028; filed separately per convention.

### Files changed

- `HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/main/MainApp.kt`
  — `rememberSaveable` → `remember` for `biometricPassed`

### Tests

All `digital.heirlooms.app.*` unit tests pass. The `CreateFriendInviteTest` failure
is pre-existing (tracked as BUG-030).
