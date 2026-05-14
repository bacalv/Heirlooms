---
id: SEC-005
title: Session invalidation on passphrase change + server-side MIME validation
category: Security
priority: Medium
status: queued
depends_on: [SEC-001]
touches:
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/service/auth/AuthService.kt
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/repository/auth/AuthRepository.kt
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/routes/upload/
assigned_to: SecurityManager
estimated: 0.5 day (agent)
---

## Background

SEC-001 threat model identified two MEDIUM findings:

- **F-07**: `AuthService.setupExisting()` sets new auth credentials but does not expire existing sessions for the same user. A stolen session remains valid for up to 90 days after a passphrase change.
- **F-08**: Upload routes accept whatever MIME type the client declares. No server-side validation against actual content or an allowlist. Since content is E2EE blobs the active exploitation risk is low, but defence-in-depth recommends validation.

## Goal

### F-07 — Session invalidation

1. In `AuthRepository`, add a method `deleteAllSessionsForUser(userId: UUID)` that deletes all rows from `user_sessions` for the given user.
2. In `AuthService.setupExisting()`, call `deleteAllSessionsForUser(user.id)` before issuing the new session, so the newly-issued token is the only live session post-passphrase-setup.
3. Write a test: after `setupExisting`, the old session token should be rejected with 401.

### F-08 — MIME type allowlist

1. Define an allowlist of accepted MIME types for upload initiation (e.g. `image/*`, `video/*`). Store it as a `Set<Regex>` constant in a new `UploadValidation.kt`.
2. In the upload initiation route, validate the declared `mime_type` against the allowlist before creating a pending blob record. Return `400 Bad Request` with `"Unsupported media type"` for types not on the list.
3. Write a test for the rejection path.

## Acceptance criteria

- Old sessions are invalidated when `setupExisting` completes; old token returns 401.
- Upload initiation with a MIME type outside the allowlist returns 400.
- All existing tests continue to pass.
