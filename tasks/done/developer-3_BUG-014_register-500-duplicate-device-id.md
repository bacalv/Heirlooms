---
id: BUG-014
title: Register returns 500 instead of 409 when device_id already exists in wrapped_keys
category: Bug Fix
priority: Medium
status: done
assigned_to: developer-3
depends_on: []
touches:
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/routes/auth/AuthRoutes.kt
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/service/auth/AuthService.kt
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/routes/AppRoutes.kt
  - HeirloomsServer/src/test/kotlin/digital/heirlooms/server/AuthHandlerTest.kt
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

## Completion notes

Completed 2026-05-15 by developer-3.

### Fix 1: 409 for duplicate device_id

Added `DeviceIdTaken` to `AuthService.RegisterResult` sealed class. In `AuthService.register()`, added a pre-flight check using `keyRepo.getWrappedKeyByDeviceId(deviceId) != null` before creating the user — returns `RegisterResult.DeviceIdTaken` immediately if the device is already registered.

Added `DeviceIdTaken` handling in `registerRoute` in `AuthRoutes.kt`, returning HTTP 409 with `{"error":"This device is already registered to another account"}`.

Belt-and-suspenders: the transactional path also catches `PSQLException` with constraint `wrapped_keys_device_id_key` and returns `DeviceIdTaken` rather than re-throwing.

### Fix 2: Atomic transaction for register

Injected `DataSource?` as an optional parameter to `AuthService` (default null for backward compatibility with tests using mock repos). When a `DataSource` is present, `register()` performs all writes (user row, system plot, recovery passphrase, wrapped_key, invite mark, friendship, session) in a single JDBC transaction on one shared connection. On any exception the transaction is rolled back, preventing orphaned users or plots.

`AppRoutes.kt` updated: `dataSource = database.dataSource` added to the `internal buildApp(...)` signature and passed through to `AuthService`.

### Tests added (AuthHandlerTest.kt)

- `register with duplicate device_id returns 409` — verifies the HTTP response code and that the error body mentions "device".
- `register with duplicate device_id leaves no orphaned user row` — verifies that after a failed register (duplicate device_id), no `users` row exists for the attempted username.

All 33 `AuthHandlerTest` tests pass. Full `./gradlew test --no-daemon` suite: BUILD SUCCESSFUL, 0 failures.
