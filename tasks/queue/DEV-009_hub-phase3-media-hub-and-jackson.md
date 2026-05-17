---
id: DEV-009
title: Hub architecture — Phase 3 — MediaHub extraction + underwear/jackson
category: Refactoring
priority: Medium
assigned_to: Developer
depends_on: [DEV-008]
touches:
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/service/upload/
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/repository/upload/
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/repository/storage/
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/domain/upload/
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/routes/AppRoutes.kt
branch: refactor/hub-architecture
estimated: 6 hours
---

# DEV-009 — Hub architecture Phase 3: MediaHub extraction + underwear/jackson

## Context

Third of six sequential tasks. DEV-008 must be merged to `refactor/hub-architecture` first.
Read `docs/briefs/ARCH-016_hub-architecture-and-underwear.md` before starting.

This phase resolves the dirtiest seam in `UploadService`:

```kotlin
class UploadService(
    private val uploadRepo: UploadRepository,
    private val blobRepo: BlobRepository,
    private val socialRepo: SocialRepository,   // ← crosses into SocialHub territory
    private val plotRepo: PlotRepository,        // ← crosses into PlotHub territory
    private val flowRepo: TrellisRepository,     // ← crosses into PlotHub territory
    ...
)
```

`UploadService` reaches into social and plot repositories for access control — resolving
whether a user can serve a blob because it belongs to a plot they're a member of. This is
replaced with a `SharesPort` outbound port that answers the same question without MediaHub
knowing about plots or social structure.

## What to build

### New module: media-hub

```
media-hub/
  src/main/kotlin/digital/heirlooms/media/
    domain/          — upload domain types (moved from HeirloomsServer)
    port/inbound/    — MediaService interface
    port/outbound/   — BlobStoragePort (re-export from underwear/common), SharesPort, IdentityPort
    service/         — UploadService (moved, cleaned of social/plot deps)
```

### SharesPort

Expressed in MediaHub's vocabulary — no mention of plots or social:

```kotlin
// media-hub outbound port
interface SharesPort : OutboundPort {
    fun isSharedWithUser(uploadId: UUID, userId: UserId): Boolean
}
```

`HeirloomsServer` provides the adapter implementation, which internally queries
`socialRepo` and `plotRepo` to answer the question. `media-hub` never knows this.

### underwear/jackson

Create `underwear/jackson` module:

```kotlin
// underwear/common
interface JsonPort {
    fun toJson(value: Any): String
    fun <T> fromJson(json: String, type: Class<T>): T
}

// underwear/jackson
class JacksonJson(private val mapper: ObjectMapper = ObjectMapper()) : JsonPort { ... }
```

Replace the inline `ObjectMapper()` calls in `AppRoutes.kt` (diagnostics handler, spec merger)
with `JsonPort`. Wire `JacksonJson` in `HeirloomsServer`.

### media-hub module dependencies

- `media-hub` depends on: `hub-common`, `underwear/common` (for `BlobPort` type reference)
- Does NOT depend on `HeirloomsServer`, Postgres, http4k, or any social/plot module

## Acceptance criteria

- `UploadService` lives in `media-hub` with no `socialRepo` or `plotRepo` constructor parameters
- `SharesPort` is the only route by which MediaHub resolves sharing access
- `underwear/jackson` module exists; no bare `ObjectMapper()` in `AppRoutes.kt`
- All existing upload tests pass unchanged
- `./gradlew test` passes with the same test count as before this phase