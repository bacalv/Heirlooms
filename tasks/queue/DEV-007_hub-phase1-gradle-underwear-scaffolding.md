---
id: DEV-007
title: Hub architecture — Phase 1 — Gradle restructure + underwear scaffolding
category: Refactoring
priority: Medium
assigned_to: Developer
depends_on: [M11 merged]
touches:
  - HeirloomsServer/build.gradle.kts
  - settings.gradle.kts
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/storage/
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/Main.kt
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/routes/AppRoutes.kt
branch: refactor/hub-architecture
estimated: 4 hours
---

# DEV-007 — Hub architecture Phase 1: Gradle restructure + underwear scaffolding

## Context

This is the first of six sequential tasks implementing the hub architecture described in
`docs/briefs/ARCH-016_hub-architecture-and-underwear.md`. Read that brief in full before starting.

**Single-developer constraint:** All six phases (DEV-007 through DEV-012) must be completed
by the same developer on the `refactor/hub-architecture` branch. No other developer should
start server-touching work until DEV-012 merges.

This phase is purely structural — no business logic changes. Its job is to establish the
Gradle multi-project layout and migrate the existing `FileStore` abstraction into the
underwear structure as a concrete example of the pattern.

## What to build

### 1. Gradle multi-project layout

Convert the repo to a Gradle multi-project build. Create the following modules (empty skeletons
are fine for modules not populated in this phase):

```
underwear/common/        — BlobPort interface
underwear/gcs/           — GcsFileStore (moved from HeirloomsServer)
underwear/s3/            — S3FileStore (moved from HeirloomsServer)
underwear/local-fs/      — LocalFileStore (moved from HeirloomsServer)
hub-common/              — UserId value class, InboundPort and OutboundPort marker interfaces
HeirloomsServer/         — depends on all of the above
```

### 2. underwear/common

Create `BlobPort` — the interface that GCS, S3, and local-fs all implement. Extract it from
the existing `FileStore` interface. `BlobPort` should expose only the operations that
`FileStore` currently declares. Rename `FileStore` → `BlobPort` throughout, or keep `FileStore`
as a typealias in `HeirloomsServer` temporarily if needed to avoid a large rename.

```kotlin
// underwear/common
interface BlobPort {
    // mirror current FileStore operations exactly
}
```

### 3. underwear/gcs, underwear/s3, underwear/local-fs

Move `GcsFileStore`, `S3FileStore`, and `LocalFileStore` into their respective modules.
Each module depends on `underwear/common` and its one third-party library only.

- `underwear/gcs` depends on: `underwear/common`, Google Cloud Storage SDK
- `underwear/s3` depends on: `underwear/common`, AWS S3 SDK
- `underwear/local-fs` depends on: `underwear/common` only (no third-party deps)

### 4. hub-common

```kotlin
// hub-common
@JvmInline value class UserId(val value: UUID)

interface InboundPort
interface OutboundPort
```

`UserId` should be used in preference to raw `UUID` for user identity going forward, but
**do not rename existing parameters in this phase** — that is scope creep. Just introduce
the type.

### 5. HeirloomsServer wiring

Update `Main.kt` and `AppRoutes.kt` to import from the new module paths. The server module
depends on all underwear modules and `hub-common`.

## Dependency rules (enforce via Gradle)

- `underwear/*` modules must not depend on anything in `HeirloomsServer` or `hub-common`
- `underwear/gcs`, `underwear/s3`, `underwear/local-fs` each depend on exactly one
  third-party library plus `underwear/common`
- `underwear/common` has no third-party dependencies

## Acceptance criteria

- `./gradlew test` passes with the same test count as before this task
- `GcsFileStore`, `S3FileStore`, `LocalFileStore` no longer live in `HeirloomsServer/`
- `HeirloomsServer` code references `BlobPort` (or `FileStore` typealias) — never
  `GcsStorage`, `S3Storage` etc. directly
- Gradle dependency graph enforces the rules above (verify with `./gradlew dependencies`)
- No behaviour changes — this is a pure structural refactor