---
id: DEV-011
title: Hub architecture — Phase 5 — PlotHub extraction
category: Refactoring
priority: Medium
assigned_to: Developer
depends_on: [DEV-010]
touches:
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/service/plot/
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/repository/plot/
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/domain/plot/
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/routes/AppRoutes.kt
branch: refactor/hub-architecture
estimated: 5 hours
---

# DEV-011 — Hub architecture Phase 5: PlotHub extraction

## Context

Fifth of six sequential tasks. DEV-010 must be merged to `refactor/hub-architecture` first.
Read `docs/briefs/ARCH-016_hub-architecture-and-underwear.md` before starting.

PlotHub is the most Heirlooms-specific domain. It sits at the top of the hub dependency stack
— it depends on MediaHub and SocialHub, but nothing depends on it (except the app). Extracting
it last means all its dependencies are already clean ports by the time we get here.

## What to build

### New module: plot-hub

```
plot-hub/
  src/main/kotlin/digital/heirlooms/plot/
    domain/          — plot domain types (moved from HeirloomsServer)
    port/inbound/    — PlotService, TrellisService, SharedPlotService interfaces
    port/outbound/   — MediaPort, ConnectionsPort, IdentityPort
    service/         — implementations (moved)
```

### Outbound ports (plot-hub vocabulary — no leaking of media or social internals)

```kotlin
// plot-hub outbound ports
interface MediaPort : OutboundPort {
    fun getUploadMetadata(uploadId: UUID, userId: UserId): UploadMetadata?
}

interface ConnectionsPort : OutboundPort {
    fun areConnected(ownerUserId: UserId, memberUserId: UserId): Boolean
}
```

`HeirloomsServer` adapter for `MediaPort` calls `UploadRepository` directly.
`HeirloomsServer` adapter for `ConnectionsPort` calls `SocialRepository` or `ConnectionRepository`.

Note: `SharesPort` (defined in DEV-009 for MediaHub) and `ConnectionsPort` (defined here for
PlotHub) serve similar conceptual purposes but are declared separately in each hub's own
vocabulary — they are not the same interface.

### PlotHub implements SharesPort

`plot-hub` provides an implementation of `media-hub`'s `SharesPort` in `HeirloomsServer`:

```kotlin
// HeirloomsServer adapter
class PlotBasedSharesAdapter(
    private val plotService: PlotService,
    private val memberRepo: PlotMemberRepository,
) : SharesPort {
    override fun isSharedWithUser(uploadId: UUID, userId: UserId): Boolean {
        // check plot membership
    }
}
```

This is wired in `AppRoutes.kt`, replacing the previous stub.

### plot-hub module dependencies

- `plot-hub` depends on: `hub-common` only
- Does NOT depend on `media-hub`, `social-hub`, `identity-hub`, or `HeirloomsServer`
- Cross-hub calls go through port interfaces wired by the app

## Acceptance criteria

- `PlotService`, `TrellisService`, `SharedPlotService` live in `plot-hub`
- `plot-hub` has no direct imports from `media-hub`, `social-hub`, or `identity-hub`
- `SharesPort` in `media-hub` is now implemented by `PlotBasedSharesAdapter` in `HeirloomsServer`
- All existing plot, trellis, and shared plot tests pass unchanged
- `./gradlew test` passes with the same test count as before this phase
- `HeirloomsServer` `AppRoutes.kt` no longer instantiates any service directly from another hub's package