---
id: TST-013
title: Sharing flow integration test — two users, friend connect, upload, share, retrieve
category: Testing
priority: High
status: queued
depends_on: []
touches:
  - HeirloomsServer/src/test/kotlin/digital/heirlooms/server/SharingFlowIntegrationTest.kt
assigned_to: Developer
estimated: 2h
---

## Goal

Write a Kotlin integration test (`SharingFlowIntegrationTest.kt`) that exercises the
complete server-side sharing pipeline: two real user accounts, friend connection,
upload, direct share, and retrieval — all in-process against a real Postgres
database (Testcontainers).

## Background

A full smoke test running the sharing flow was requested after the v0.56 deploy. The
server stores encrypted blobs opaquely; it does not validate crypto correctness. This
means a test can use trivially-constructed key material (no real ECDH) and fake
ciphertext while still exercising every server-side access control, ownership, and
retrieval code path.

The auth trick: `AuthService.login` verifies `sha256(authKey) == authVerifier`. So if
a test user registers with `authVerifier = sha256(knownBytes)`, it can log in by
sending `authKey = knownBytes`. No real derivation needed.

## Infrastructure pattern

Copy the setup from `AuthHandlerTest.kt` exactly:
- Testcontainers Postgres (`postgres:16`)
- Flyway migration via classpath
- `buildApp(storage, database, authSecret = ...)` — but use `LocalFileStore(tempDir)`,
  NOT `mockk(relaxed = true)`, so that upload bytes are actually stored and retrieved
- Wrap with `sessionAuthFilter(authRepo).then(rawApp)`
- Set `System.setProperty("ryuk.disabled", "true")` and the docker.host socket path

`AuthHandlerTest` already has `registerUser(username, inviteToken)` and
`generateInvite(sessionToken)` helpers — replicate these in the new test class.
`setupInviterSession()` from `AuthHandlerTest` is also a good reference for seeding
the founding user.

## Exact flow to implement

```
Step 1 — Seed founding user session
  setupInviterSession() → foundingSession

Step 2 — Register User A
  GET  /api/auth/invites (foundingSession)      → invite1.token
  POST /api/auth/register (invite1.token)        → sessionA, userIdA

Step 3 — Register User B
  GET  /api/auth/invites (foundingSession)      → invite2.token
  POST /api/auth/register (invite2.token)        → sessionB, userIdB

Step 4 — Connect A and B as friends
  GET  /api/auth/invites (sessionA)             → invite3.token
  POST /api/auth/invites/{invite3.token}/connect (sessionB) → 200, friends

Step 5 — User A uploads a blob
  POST /api/content/upload (sessionA)
    body  = "hello heirlooms".toByteArray()
    Content-Type: application/octet-stream
  → 201, uploadId

Step 6 — User A shares with User B
  POST /api/content/uploads/{uploadId}/share (sessionA)
    body = {
      "toUserId":     userIdB.toString(),
      "wrappedDek":   Base64.getEncoder().encodeToString(ByteArray(32) { 0xAB.toByte() }),
      "dekFormat":    "test-format-v1"
    }
  → 201, recipientRecord  (extract recipientRecord.id = recipientUploadId)

Step 7 — User B retrieves the file
  GET /api/content/uploads/{recipientUploadId}/file (sessionB) → 200
  Assert responseBytes == "hello heirlooms".toByteArray()

Step 8 — User B sees item in their received list
  GET /api/content/uploads?is_received=true (sessionB) → 200
  Assert the response JSON contains at least one item
```

## Negative tests to include (in the same class, separate @Test methods)

1. **B cannot access A's original upload directly** — before or after sharing, user B
   calling `GET /api/content/uploads/{uploadId}` (A's original uploadId, not the
   recipient record) should return 404.
2. **Sharing requires friendship** — register a third user C (no friend connection to A),
   attempt `POST /api/content/uploads/{uploadId}/share` with `toUserId = C` as user A,
   expect 403.
3. **Non-member cannot retrieve** — register User D with no connection; attempt
   `GET /api/content/uploads/{recipientUploadId}/file` as D, expect 404.

## Notes on LocalFileStore

`LocalFileStore` (already exists in the codebase) stores files under a temp directory.
Create it with `Files.createTempDirectory("heirlooms-test-")` and pass it to `buildApp`.
This allows real round-trip storage without needing GCS.

## Register body shape (from AuthHandlerTest)

```kotlin
val authKey     = ByteArray(32) { (username.length + it).toByte() }
val authSalt    = ByteArray(16) { it.toByte() }
val authVerifier = sha256(authKey)
val wrappedMasterKey = ByteArray(64) { 5 }
val pubkey      = ByteArray(65) { 6 }
// POST body:
// invite_token, username, display_name,
// auth_salt (base64url), auth_verifier (base64url),
// wrapped_master_key (base64std), wrap_format, pubkey_format,
// pubkey (base64std), device_id (UUID), device_label, device_kind
```

## Unit test mandate

Per project policy, all tasks must include unit tests. In addition to the integration
test, add at least one focused unit test that validates the `sha256(authKey) == authVerifier`
login check directly against `AuthService` in isolation (using mocked repos). This
documents the auth contract the integration test relies on.

## Deliverable

- `HeirloomsServer/src/test/kotlin/digital/heirlooms/server/SharingFlowIntegrationTest.kt`
- All tests must pass: `./gradlew test --tests "digital.heirlooms.server.SharingFlowIntegrationTest"`
- Append `## Completion notes` and move this file to `tasks/done/`
