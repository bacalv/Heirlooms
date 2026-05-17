---
id: TST-014
title: M11 local stack — feasibility and design for iteration sign-off
category: Testing
priority: High
assigned_to: TestManager
depends_on: []
touches:
  - HeirloomsServer/src/test/
  - tasks/queue/TOOL-001_kotlin-api-client-module.md
branch: M11
---

# TST-014 — M11 local stack: feasibility and design for iteration sign-off

## Context

Heirlooms is moving to a **server-first** development strategy for M11 and M12. The test
environment (Cloud Run + GCS + Cloud SQL) will not be redeployed after every M11 iteration.
Instead, each iteration should be signed off by running an automated test suite against a
**fully local stack** — no cloud dependencies, no Docker Desktop restart, no manual deploy.

The pattern already exists in `SharingFlowIntegrationTest.kt`, which starts the server
in-process via `buildApp`, uses Testcontainers for a real PostgreSQL instance, and
`LocalFileStore` for file storage. M11 needs this elevated into a first-class iteration
gate.

## Objective

Assess the feasibility of a local stack for M11 iteration sign-off and produce a design
brief covering:

1. **Stack definition** — what components are needed (server, DB, file store, any stubs)
2. **Test driver** — whether to extend the existing Testcontainers integration tests, use
   the standalone Kotlin API client (TOOL-001), or both
3. **Coverage scope** — which API surfaces must be covered for an M11 iteration pass
4. **Iteration gate definition** — what a passing run looks like; what is an automatic
   fail vs a known skip
5. **CI integration** — can this run in GitHub Actions without cloud credentials?

## Questions to answer

- Can the full server start in-process for all M11 endpoints, or are there hard
  dependencies on GCS / Cloud SQL that must be stubbed?
- `LocalFileStore` already exists — is it suitable as the file store for all M11 tests,
  or does the capsule / tlock path require a different stub?
- Should the TOOL-001 Kotlin client be the external test driver (black-box, HTTP over
  localhost) or should tests stay in-process (faster, but less realistic)?
- Is Testcontainers the right PostgreSQL provider, or would an in-memory alternative
  (e.g. H2 with PostgreSQL compatibility mode) be sufficient and faster for unit-level
  iteration tests?
- What does a clean run look like end-to-end — one Gradle command? A shell script?

## Deliverable

Produce a brief (`docs/testing/TST-014_m11-local-stack-design.md`) covering:

- Recommended stack architecture (diagram or table)
- Recommended test driver approach
- Gradle task / command to run the full iteration gate
- Any open questions or blockers for the Test Manager to flag to the CTO

Do **not** implement the stack in this task — design only. Implementation will follow as
a separate task once the CTO approves the design.

## Completion notes

Completed 2026-05-17 by TestManager (agent/test-manager-5/TST-014).

Design brief produced at `docs/testing/TST-014_m11-local-stack-design.md`.

**Feasibility answers (summary):**

1. **In-process server: feasible.** `buildApp` accepts `FileStore` and `Database` by
   constructor injection with no hard GCS/Cloud SQL dependencies. The tlock path
   requires `StubTimeLockProvider` to be injected (not yet implemented on the M11
   branch).

2. **`LocalFileStore` is fully suitable.** All M11 capsule crypto columns are stored
   in PostgreSQL (`BYTEA`), not in the file store. `LocalFileStore` covers all upload
   content paths. Pre-signed URL routes (which require `DirectUploadSupport`) return
   501 and are acceptable skips.

3. **TOOL-001 is not the iteration gate driver.** Tests stay in-process (http4k
   `HttpHandler` — same pattern as `SharingFlowIntegrationTest.kt`). TOOL-001 is
   reserved for supplementary black-box validation against a live server. This avoids
   a blocking dependency on ARCH-015.

4. **Testcontainers (real PostgreSQL 16), not H2.** M11 schema uses PostgreSQL-specific
   features (`gen_random_uuid()`, `BYTEA` constraints, `JSONB`) that H2 does not
   support. The Testcontainers pattern is already proven by two existing test classes.

5. **Clean run: `./gradlew :HeirloomsServer:test`.** No shell script needed. Env vars
   for Testcontainers are already configured in `build.gradle.kts`. A dedicated
   `integrationTest` Gradle task is recommended once test volume warrants it.

**Key blocker flagged to CTO:** `buildApp` must be extended to accept a
`TimeLockProvider` parameter before Wave 5/6 integration tests can be written. The
first M11 developer task implementing `/seal` must include this change.

**M11 schema blocker:** V31/V32 connections and capsule crypto migrations
(ARCH-010 §3) are not yet in the codebase. The highest existing migration is
`V32__add_require_biometric_to_users.sql`, so the M11 schema migrations will be V33
and V34 (or higher). Developer tasks must check the current highest version before
naming migration files.

**CI feasibility: confirmed.** GitHub Actions `ubuntu-latest` runners support Docker
natively. No cloud credentials are needed. Recommended workflow file specified in the
brief (§7).
