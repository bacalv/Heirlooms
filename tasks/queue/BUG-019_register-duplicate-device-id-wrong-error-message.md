---
id: BUG-019
title: Registration shows "Username already exists" for duplicate device_id (409) collision
category: Bug Fix
priority: Low
status: queued
depends_on: []
touches:
  - HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/auth/RegisterScreen.kt
  - HeirloomsApp/app/src/main/kotlin/digital/heirlooms/api/HeirloomsApi.kt
assigned_to: Developer
estimated: 30 minutes
---

## Problem

After clearing app storage on Android and re-registering, the device generates the same
hardware-based device_id. The server (BUG-014 fix) correctly returns 409 Conflict for a
duplicate device_id. However the Android registration UI maps any 409 response to
"Username already exists", which is incorrect and confusing — the real problem is the
device, not the username.

Discovered during v0.54 staging smoke test setup (2026-05-15): cleared Fire OS app,
tried to register a fresh account, saw "Username already exists" repeatedly even with
usernames confirmed absent from the DB. Root cause was the old device_id still
registered under the previous account.

## Fix

In the registration API call / error handler, distinguish the 409 body:
- If the server body is `"device_id already registered"` (or similar) → show
  "This device is already linked to an account. Please contact support."
- If the server body is `"username already taken"` → show "Username already exists"

Check the exact 409 response body the server returns for duplicate device_id vs
duplicate username and map accordingly.

## Acceptance criteria

- Registering with a device_id already in the DB shows a clear device-specific error
- Registering with a username already taken still shows "Username already exists"

## Completion notes

<!-- Agent appends here and moves file to tasks/done/ -->
