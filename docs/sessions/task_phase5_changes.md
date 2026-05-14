# Phase 5 — Service Layer Extraction: Summary

**Date:** 14 May 2026  
**Commit:** `refactor: phase 5 — extract service classes from handlers`

---

## What changed

Phase 5 extracts all business logic out of handler files into service classes. Handlers now contain only: parse HTTP request → validate inputs → call service → format response. No SQL, no repository calls, no business decisions remain in handler files.

### New files

| File | Lines | Contents |
|---|---|---|
| `service/upload/UploadService.kt` | ~340 | Dedup check, thumbnail + metadata orchestration, confirm encrypted/legacy, migration, share-upload, compost cleanup |
| `service/auth/AuthService.kt` | ~250 | Token issuance, fake salt, session resolution, login, setup-existing, register, invite, full pairing flow, logout |
| `service/capsule/CapsuleService.kt` | ~110 | Create/update validation (recipients, message size, upload existence), read, seal, cancel |
| `service/plot/PlotService.kt` | ~100 | Criteria validation + serialisation, create with visibility guards, update, delete, batch reorder |
| `service/plot/FlowService.kt` | ~200 | Criteria validation, flow CRUD, staging approval (DEK decoding), DEK guards on addPlotItem, collection item CRUD |
| `service/plot/SharedPlotService.kt` | ~90 | Membership lifecycle delegation; `getPlotKey` guards non-shared plots |
| `service/keys/KeyService.kt` | ~200 | `generateLinkCode`, `registerDevice`, device link state machine (initiate/register/wrap), passphrase CRUD |
| `service/social/SocialService.kt` | ~75 | Sharing key Base64 decode + upsert, friend-access guard for friend key lookup, list friends |

### Modified files

- All 9 handler files updated to call service methods instead of `Database` directly
- `UploadHandler.kt`: `buildApp()` now constructs all 8 service instances and passes them to route builders; also removes `launchCompostCleanup` (now in `UploadService`)
- `UploadHandlerTest.kt`: Added missing `findUploadByIdForSharedMember` stubs in two 404 test cases (pre-existing gap, exposed by MockK strictness)

---

## Design decisions

### Services take `Database` not repositories

Services accept `Database` (the delegation facade) as their primary constructor parameter. Individual repositories are not passed directly. Rationale: all existing unit tests use `mockk<Database>()` — injecting repositories would require test rewrites. A future phase can change service constructors to accept repository interfaces when unit-testing services in isolation becomes a requirement.

### `buildApp()` as wiring point

`buildApp()` constructs service instances locally (not in `Main.kt`). This keeps `Main.kt` identical to before and preserves the existing test interface (`buildApp(mockStorage, mockDatabase)`). The service creation is a one-liner per service inside `buildApp()`.

### Two-level lambda extraction pattern

http4k contract lambdas with two path params use `{ pathParam: T, _ -> { request: Request -> ... } }`. Non-local `return` in the inner lambda does not compile. Fix: extract the inner body to a private named helper function (`private fun handleXxx(...): Response`) and use regular `return`. Applied to: `SharedPlotHandler.kt` (8 routes), `KeysHandler.kt` (2 routes), `UploadHandler.kt` (1 route), `SharingKeyHandler.kt` (1 route).

---

## Build and test results

- `./gradlew clean shadowJar` — **BUILD SUCCESSFUL** (warnings only)
- `./gradlew clean test` — **326+ tests pass** (0 failures)
- Integration tests (`coverageTest`): pending Docker

---

## Coverage

Baseline (phase 1–4): INSTRUCTION 52.1% (28,650/54,998).

Expected for phase 5: flat or slight increase. The service layer adds new classes but the same integration test paths exercise the same code paths — the ratio should be approximately unchanged. No coverage was removed.
