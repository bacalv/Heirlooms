---
id: DEV-008
title: Hub architecture — Phase 2 — VaultHub extraction
category: Refactoring
priority: Medium
assigned_to: Developer
depends_on: [DEV-007]
touches:
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/service/capsule/
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/repository/capsule/
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/domain/capsule/
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/routes/AppRoutes.kt
branch: refactor/hub-architecture
estimated: 4 hours
---

# DEV-008 — Hub architecture Phase 2: VaultHub extraction

## Context

Second of six sequential tasks. DEV-007 must be merged to `refactor/hub-architecture` first.
Read `docs/briefs/ARCH-016_hub-architecture-and-underwear.md` before starting.

`CapsuleService` currently only depends on `capsuleRepo` — it is the cleanest seam in the
codebase and the right first hub to extract. This phase validates the Gradle module pattern
on a low-risk domain before tackling the dirtier seams in later phases.

## What to build

### New module: vault-hub

```
vault-hub/
  src/main/kotlin/digital/heirlooms/vault/
    domain/          — capsule domain types (moved from HeirloomsServer)
    port/inbound/    — VaultService interface (inbound port)
    port/outbound/   — IdentityPort, KeyPort (outbound ports)
    service/         — CapsuleService implementation (moved)
```

### Outbound ports

Define in `vault-hub` using vault vocabulary — no leaking of auth or key storage internals:

```kotlin
// vault-hub outbound ports
interface IdentityPort : OutboundPort {
    fun userExists(userId: UserId): Boolean
}

interface KeyPort : OutboundPort {
    fun storeWrappedKey(capsuleId: UUID, userId: UserId, wrappedKey: ByteArray)
    fun loadWrappedKey(capsuleId: UUID, userId: UserId): ByteArray?
}
```

Only expose operations that `CapsuleService` actually calls. Do not speculate about future needs.

### Inbound port

```kotlin
interface VaultService : InboundPort {
    // mirror the public methods of the current CapsuleService exactly
}
```

### HeirloomsServer adapter wiring

In `AppRoutes.kt`, wire the adapters:
- `IdentityPort` implemented by a thin adapter calling `AuthRepository`
- `KeyPort` implemented by a thin adapter calling `KeyRepository`
- Pass the adapters into `CapsuleService` constructor

`CapsuleRoutes` depends on `VaultService` (the inbound port), not the concrete `CapsuleService`.

### vault-hub module dependencies

- `vault-hub` depends on: `hub-common` only
- `vault-hub` does NOT depend on `HeirloomsServer`, any repository module, or any underwear module
- Adapter implementations live in `HeirloomsServer` (they know about Postgres)

## Acceptance criteria

- `CapsuleService` lives in `vault-hub`, not `HeirloomsServer`
- `vault-hub` has no Postgres, http4k, or GCS imports
- `CapsuleRoutes` depends on `VaultService` interface
- All existing capsule tests pass unchanged
- `./gradlew test` passes with the same test count as before this phase