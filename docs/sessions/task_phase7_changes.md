# Phase 7 — Routes Sub-packages: Change Summary

**Date:** 14 May 2026  
**Version:** v0.53.2  
**Type:** Pure code reorganisation — no behaviour changes

---

## What moved where

| New file | Old handler |
|---|---|
| `routes/upload/UploadRoutes.kt` | `UploadHandler.kt` (all route functions) |
| `routes/auth/AuthRoutes.kt` | `AuthHandler.kt` |
| `routes/capsule/CapsuleRoutes.kt` | `CapsuleHandler.kt` |
| `routes/plot/PlotRoutes.kt` | `PlotHandler.kt` |
| `routes/plot/FlowRoutes.kt` | `FlowHandler.kt` |
| `routes/plot/SharedPlotRoutes.kt` | `SharedPlotHandler.kt` |
| `routes/keys/KeysRoutes.kt` | `KeysHandler.kt` |
| `routes/social/FriendsRoutes.kt` | `FriendsHandler.kt` |
| `routes/social/SharingKeyRoutes.kt` | `SharingKeyHandler.kt` |
| `routes/AppRoutes.kt` | `buildApp()` from `UploadHandler.kt` |

## How `buildApp()` was handled

`buildApp()` was extracted from `UploadHandler.kt` into `routes/AppRoutes.kt`
(`package digital.heirlooms.server.routes`). It imports all route builder
functions from the new sub-packages.

`Main.kt` was updated: `import digital.heirlooms.server.routes.buildApp` added.

Six unit test files in `HeirloomsServer` and `HeirloomTestEnvironment.kt` in
`HeirloomsTest` had the same import added.

## `authUserId()` extension

Not moved. It remains in `SessionAuthFilter.kt` in the root package
(`digital.heirlooms.server`). All new routes files import it explicitly:
`import digital.heirlooms.server.authUserId`.

## Old handler files

All nine `*Handler.kt` files replaced with stub files:
```kotlin
package digital.heirlooms.server
// Moved to routes/xxx/XxxRoutes.kt
```

## Build results

- `./gradlew clean shadowJar` — BUILD SUCCESSFUL (warnings only, no errors)
- `./gradlew test` — 251 tests, all passed
- `./gradlew coverageTest` — BUILD SUCCESSFUL

## Coverage (before / after)

| Metric | Before | After |
|---|---|---|
| INSTRUCTION | 51.7% | 51.8% |
| LINE | 56.8% | 56.8% |
| METHOD | 57.3% | 57.3% |
| CLASS | 67.0% | 67.0% |
| BRANCH | 33.8% | 33.8% |
