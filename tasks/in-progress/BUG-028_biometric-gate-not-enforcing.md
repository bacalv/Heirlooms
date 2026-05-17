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
