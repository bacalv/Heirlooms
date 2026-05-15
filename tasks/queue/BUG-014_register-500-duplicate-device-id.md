---
id: BUG-014
title: Register returns 500 instead of 409 when device_id already exists in wrapped_keys
category: Bug Fix
priority: Medium
status: queued
assigned_to: Developer
depends_on: []
touches:
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/routes/auth/AuthRoutes.kt
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/service/auth/AuthService.kt
estimated: 1 hour (agent)
---

## Steps to reproduce

1. Register a device on staging (creates an entry in `wrapped_keys` with the device's `device_id`).
2. Without clearing the app data, attempt to register a *new* user account on the same device.

## Expected behaviour

Server returns 409 Conflict with a human-readable message such as
"This device is already registered to another account."

## Actual behaviour

`org.postgresql.util.PSQLException: duplicate key value violates unique constraint
"wrapped_keys_device_id_key"` propagates to the route handler's catch block and
returns HTTP 500 "Internal server error".

## Fix

Two issues to fix together:

1. In `registerRoute` (or `AuthService.register`), catch the `PSQLException` when its
   `serverErrorMessage.constraint == "wrapped_keys_device_id_key"` and return 409 instead.

2. Wrap the entire register operation in a single database transaction so that a failure
   at any step (including `wrapped_keys` insert) rolls back the user row and any
   auto-created system plot. Currently a failure after user creation leaves an orphaned
   user with no `wrapped_keys` entry, blocking re-registration with the same username.

## Notes

Discovered during TST-007 (2026-05-15) when trying to register a second test account on
a Fire HD 8 tablet that had previously been used for staging tests.
Workaround applied to staging DB: randomised device_ids for non-Bret accounts via direct
SQL so testing could continue.
