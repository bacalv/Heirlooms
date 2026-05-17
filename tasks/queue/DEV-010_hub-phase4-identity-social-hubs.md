---
id: DEV-010
title: Hub architecture — Phase 4 — IdentityHub + SocialHub extraction
category: Refactoring
priority: Medium
assigned_to: Developer
depends_on: [DEV-009]
touches:
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/service/auth/
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/service/social/
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/service/connection/
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/service/keys/
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/domain/auth/
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/domain/keys/
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/domain/connection/
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/routes/AppRoutes.kt
branch: refactor/hub-architecture
estimated: 6 hours
---

# DEV-010 — Hub architecture Phase 4: IdentityHub + SocialHub extraction

## Context

Fourth of six sequential tasks. DEV-009 must be merged to `refactor/hub-architecture` first.
Read `docs/briefs/ARCH-016_hub-architecture-and-underwear.md` before starting.

This phase resolves the second dirty seam — `AuthService` currently depends on `socialRepo`
and `plotRepo`, which it should not:

```kotlin
class AuthService(
    private val authRepo: AuthRepository,
    private val keyRepo: KeyRepository,
    private val socialRepo: SocialRepository,   // ← crosses into SocialHub territory
    private val plotRepo: PlotRepository,        // ← crosses into PlotHub territory
    ...
)
```

This dependency likely exists for account deletion — purging a user requires cleaning up
social connections and plot memberships. The fix is a `UserLifecycleOrchestrator` at the
application layer that coordinates across hubs. `AuthService` no longer orchestrates deletion
itself; it fires and the orchestrator fans out.

## What to build

### New module: identity-hub

```
identity-hub/
  src/main/kotlin/digital/heirlooms/identity/
    domain/          — auth and key domain types
    port/inbound/    — IdentityService interface
    port/outbound/   — KeyStoragePort, CryptoPort
    service/         — AuthService (moved, cleaned of social/plot deps)
                     — KeyService (moved)
```

Remove `socialRepo` and `plotRepo` from `AuthService`'s constructor entirely.

### UserLifecycleOrchestrator

Add to `HeirloomsServer` (application layer — not inside any hub):

```kotlin
class UserLifecycleOrchestrator(
    private val identityService: IdentityService,
    private val socialService: SocialService,
    private val plotService: PlotService,
) {
    fun deleteAccount(userId: UserId) {
        socialService.purgeUser(userId)
        plotService.purgeUser(userId)
        identityService.deleteAccount(userId)
    }
}
```

The orchestrator calls each hub's inbound port in turn. No hub knows about the others.

### New module: social-hub

```
social-hub/
  src/main/kotlin/digital/heirlooms/social/
    domain/          — social, connection, nomination domain types
    port/inbound/    — SocialService, ConnectionService, NominationService interfaces
    port/outbound/   — IdentityPort
    service/         — implementations (moved)
```

### Module dependencies

- `identity-hub` depends on: `hub-common`, `underwear/common` (for `CryptoPort`)
- `social-hub` depends on: `hub-common`
- Neither depends on `HeirloomsServer`, Postgres, or each other

## Acceptance criteria

- `AuthService` has no `socialRepo` or `plotRepo` constructor parameters
- Account deletion is orchestrated via `UserLifecycleOrchestrator` in `HeirloomsServer`
- `identity-hub` and `social-hub` modules have no cross-hub domain imports
- All existing auth, key, social, connection, and nomination tests pass unchanged
- `./gradlew test` passes with the same test count as before this phase
