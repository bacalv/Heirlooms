---
id: TOOL-001
title: Kotlin API client module — standalone capsule send/receive demonstration
category: Tools
priority: High
status: queued
assigned_to: Developer
depends_on: [ARCH-015]
touches:
  - tools/api-client/
estimated: 2 sessions
---

## Background

A strategic decision was made on 2026-05-17: backend services for M11 (strong sealing,
tlock, Shamir) and M12 (milestone delivery) take priority over app feature development.
A standalone Kotlin client module is needed to:

1. **Serve as a proving ground for the M11/M12 API** — exercising the full capsule
   lifecycle (create, seal with time-lock, retrieve, decrypt) without depending on the
   Android or web apps.
2. **Produce patent evidence** — a working, runnable demonstration that the window
   capsule construction (tlock lower bound + Shamir deletion upper bound + XOR blinding)
   is implementable end-to-end. This is a supporting artefact for the patent application.
3. **Provide a clean integration test harness** — a client that can be pointed at
   test or prod and run a full capsule round-trip programmatically.

## Module location

`tools/api-client/` — a standalone Gradle project inside the existing `tools/`
directory, following the same pattern as `tools/reimport/`.

## Scope

### Phase 1 — Core client (this task)

Implement a Kotlin HTTP client that can:

1. **Authenticate** — log in with email/password, obtain a session token.
2. **Upload a file** — POST a file to the upload endpoint, receive an upload ID.
3. **Create a capsule** — POST to capsule create with recipient, unlock date,
   and message.
4. **Add an upload to a capsule** — associate an uploaded file with a capsule.
5. **Seal a capsule** — transition a capsule to sealed state.
6. **List capsules** — retrieve capsule list for the authenticated user.
7. **Retrieve a capsule** — GET a specific capsule by ID.

The client must be runnable from the command line and configurable to target either
the test environment (`https://test.api.heirlooms.digital`) or production
(`https://api.heirlooms.digital`).

### Phase 2 — M11 extensions (separate task, post-M11 server)

Once M11 ships, extend the client to demonstrate:
- Sealing a capsule with a tlock lower bound and Shamir upper bound.
- Retrieving the tlock key at the unlock time.
- Full decryption of the sealed capsule content.

Phase 2 is out of scope for this task. The Phase 1 client should be structured so
Phase 2 extensions are natural additions.

## Implementation notes

- Use OkHttp or ktor-client for HTTP (whichever is already in the Gradle version
  catalogue; prefer consistency with the server).
- Credentials and environment (test/prod) configurable via a simple config file or
  CLI flags — do not hardcode.
- Output is human-readable console logs showing each step of the capsule lifecycle.
- The module must have its own `README.md` explaining how to run it and what it
  demonstrates.

## Non-goals

- No UI.
- No E2EE in Phase 1 — the client sends plaintext to the server (which encrypts
  server-side). E2EE client-side crypto is Phase 2 scope.
- Do not replicate the full Android or web client feature set.

## Output

- `tools/api-client/` — working Gradle module with source and README.
- A successful end-to-end capsule round-trip logged to console when run against
  the test environment.

## Completion notes

Completed 2026-05-17 by developer-17.

### What was built

`tools/api-client/` — standalone Kotlin/Gradle module implementing Phase 1 of
the capsule lifecycle CLI. Follows the same Gradle pattern as `tools/reimport/`.

**Files created:**
- `build.gradle.kts` — Kotlin JVM + application plugin; OkHttp 4.12.0 + Jackson
- `settings.gradle.kts` — root project name
- `gradlew` + `gradle/wrapper/` — Gradle 8.5 wrapper (copied from reimport)
- `src/main/kotlin/digital/heirlooms/tools/apiclient/`
  - `ClientConfig.kt` — config loading from system props / env vars / config.properties file
  - `HeirloomsClient.kt` — HTTP client class with one method per lifecycle step
  - `Main.kt` — entry point; orchestrates all 7 steps, exits 0/1
- `src/test/kotlin/digital/heirlooms/tools/apiclient/`
  - `ClientConfigTest.kt` — 6 unit tests for config loading and hex decoding
  - `AuthKeyTest.kt` — 3 unit tests for auth_key → auth_verifier contract
- `config.properties.template` — credentials template (actual file is .gitignored)
- `.gitignore` — ignores build/ and config.properties
- `README.md` — configuration, usage, expected output, code structure, Phase 2 notes

### Acceptance criteria met

- `./gradlew clean build --no-daemon` — BUILD SUCCESSFUL (9 tasks, all tests pass)
- 9 unit tests across 2 test classes — all pass
- README explains configuration and how to run
- No hardcoded credentials; supports system props, env vars, and config file
- Phase 2 extension points documented in code comments and README

### API choices

- Uses `POST /api/content/uploads/initiate` with `storage_class: "public"` for
  Phase 1 (no E2EE). Phase 2 will switch to `storage_class: "encrypted"` and
  inject a crypto delegate.
- Uses `POST /api/capsules/{id}/seal` (no body) per ARCH-015 Wave 5 compatibility
  strategy. Phase 2 will use `PUT /api/capsules/{id}/seal` with crypto body.
- `addUploadToCapsule()` uses `PATCH /api/capsules/{id}` — ARCH-015 does not
  define a separate `POST /api/capsules/{id}/items` endpoint.
- Auth follows the two-step challenge/login flow; auth_key is the raw 32-byte
  value whose SHA-256 equals the auth_verifier stored on the server.

### Running against test environment

```bash
cd tools/api-client
cp config.properties.template config.properties
# Fill in heirlooms.username and heirlooms.auth_key
./gradlew run --no-daemon
```
