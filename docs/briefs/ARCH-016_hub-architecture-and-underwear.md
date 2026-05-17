# ARCH-016 — Hub Architecture and Underwear Layer

**Status:** Design complete — implementation queued post-M11  
**Author:** Technical Architect  
**Date:** 2026-05-17

---

## Overview

The server codebase has grown to a point where `AppRoutes.kt` conflates dependency wiring, service construction, route assembly, and inline business logic. This brief proposes a structural reorganisation based on two complementary principles:

1. **Hub architecture** — bounded contexts with explicit inbound/outbound ports (hexagonal architecture)
2. **Underwear layer** — every third-party library is accessed only through a project-owned interface

Together these give us: clean bounded contexts, swappable infrastructure, a stability buffer against library churn, and a clear path to deploying a standalone vault product.

---

## Hub Architecture

### Principle

Each hub is a self-contained bounded context with:
- A **domain core** — business logic, no framework dependencies
- **Inbound ports** — interfaces the hub exposes to callers
- **Outbound ports** — interfaces the hub depends on, expressed in the hub's own vocabulary

Crucially, outbound port names must be expressed in the vocabulary of the hub that declares them, not the hub that implements them. A `MediaHub` that needs to check sharing access declares a `SharesPort` — it does not declare a `PlotPort`, because MediaHub does not know or care that plots exist.

### The five hubs

**IdentityHub**  
Auth, sessions, devices, key wrapping, recovery passphrase, device pairing.  
Outbound ports: `KeyStoragePort`, `CryptoPort`  
This hub knows nothing about plots, uploads, or social connections.

**VaultHub**  
Time-locked capsules, executor shares, recipient links, Shamir secret sharing.  
Outbound ports: `IdentityPort`, `KeyPort`  
This is the product that can be deployed standalone — the E2EE time-lock vault without any photo-sharing domain.

**MediaHub**  
Upload lifecycle, blob staging, EXIF extraction, thumbnails, tags.  
Outbound ports: `BlobStoragePort`, `IdentityPort`, `SharesPort`  
`SharesPort` answers "has this upload been shared with this user?" — MediaHub does not know how that sharing is organised.

**SocialHub**  
Friends, sharing keys, connections, nominations.  
Outbound ports: `IdentityPort`

**PlotHub**  
Plots, trellis, flows, items, shared plots. The Heirlooms-specific photo-sharing domain.  
Outbound ports: `MediaPort`, `ConnectionsPort`, `IdentityPort`  
This hub is the most product-specific layer. VaultHub and MediaHub have no dependency on it.

### Current dirty seams

Two services currently violate hub boundaries and will need refactoring:

**AuthService → SocialRepository + PlotRepository**  
Likely used for account deletion data purge. Fix: extract a `UserLifecycleOrchestrator` at the application layer that coordinates across hubs when an account is deleted. AuthService fires, orchestrator fans out.

**UploadService → SocialRepository + PlotRepository + TrellisRepository**  
Used for access control — resolving whether a user can serve a blob via plot membership. Fix: `SharesPort` and `ConnectionsPort` port calls replace the direct repo dependencies.

These are the only significant seam violations. All other services are already close to hub-clean.

### Standalone vault deployment

The "vault-server" app wires only:
- IdentityHub + VaultHub
- underwear/postgres (for both)
- underwear/gcs or underwear/s3 for key storage
- A thin http4k HTTP layer

No PlotHub, SocialHub, or MediaHub. This is the product that could be licensed or deployed independently of the Heirlooms photo-sharing domain.

---

## Underwear Layer

### Principle

No production code in any hub or app may import a third-party library directly. For each third-party library used, there is a dedicated underwear module that:

1. Defines an interface exposing only the operations the project actually calls
2. Provides an implementation that delegates to the library

The interface starts as a thin pass-through. Over time, as libraries release breaking changes, the underwear module absorbs that churn and keeps the interface stable. If a library needs to be swapped, the change is contained entirely within one module.

### underwear-common

Interfaces that multiple underwear implementations satisfy live in `underwear-common`. This module has no third-party dependencies of its own — it is pure interfaces.

```
underwear/common/    BlobPort, JsonPort, CipherPort, ...
```

Hubs that depend on any underwear library get `underwear-common` transitively and code against `BlobPort` throughout — never against `GcsStorage` or `S3Storage` directly. Which implementation is wired in is an app-layer decision.

### Module structure

```
underwear/
  common/          BlobPort, JsonPort, CipherPort (pure interfaces, no third-party deps)
  jackson/         JacksonJson implements JsonPort
  bouncy-castle/   BouncyCastleCipher implements CipherPort
  gcs/             GcsStorage implements BlobPort
  s3/              S3Storage implements BlobPort
  local-fs/        LocalStorage implements BlobPort  ← FileStore already exists here
  netty/           NettyServer implements ServerPort
  flyway/          FlywayMigration implements MigrationPort
  postgres/        DataSource factory + connection pool (HikariCP)
```

Note: `FileStore` / `GcsFileStore` / `S3FileStore` / `LocalFileStore` already implement this pattern — they just need to be renamed and relocated into the underwear structure.

### What is not underwear'd

Some libraries are foundational infrastructure accepted as permanent:
- **http4k** routing and contract DSL — it is the web framework, not a swappable detail
- **Kotlin coroutines** — language-adjacent
- **JUnit / test libraries** — test scope only

---

## hub-common

Minimal shared primitives that all hubs depend on. Grows slowly and only contains things with no better home.

```
hub-common/
  UserId           — universal value type; prevents impedance mismatch at hub boundaries
  ResourceId       — typed wrapper if needed
  InboundPort      — marker interface for hub-facing APIs
  OutboundPort     — marker interface for infrastructure dependencies
```

Does not contain: business logic, database infrastructure, HTTP wiring, or any domain concept belonging to a specific hub.

---

## Full project structure

```
underwear/
  common/
  jackson/
  bouncy-castle/
  gcs/
  s3/
  local-fs/
  netty/
  flyway/
  postgres/

hub-common/

libs/
  identity-hub/        depends on: hub-common, underwear/common
  vault-hub/           depends on: hub-common, underwear/common
  media-hub/           depends on: hub-common, underwear/common
  social-hub/          depends on: hub-common, underwear/common
  plot-hub/            depends on: hub-common, underwear/common

adapters/
  postgres-adapters/   repository implementations for all hubs
                       depends on: all hub libs, underwear/postgres

apps/
  heirlooms-server/    wires all hubs + all underwear implementations
  vault-server/        wires identity-hub + vault-hub only (future)
```

### Dependency rules

- Nothing in `underwear/` depends on anything in `libs/` or `apps/`
- Nothing in `hub-common/` depends on third-party libraries (except the Kotlin stdlib)
- No hub imports another hub's domain classes directly — cross-hub calls go through port interfaces
- App-layer modules (`apps/`) are the only place that knows which underwear implementation is wired in

---

## Database ownership

Single PostgreSQL instance. Each hub owns its tables and no hub issues SQL JOINs across hub boundaries. Cross-hub data access goes through port calls (in-process method calls in the monolith deployment; HTTP calls if ever deployed separately).

This avoids the operational complexity of separate databases while still enforcing the boundary discipline at the code level.

---

## Implementation phasing

This is a milestone-level refactor, not a sprint task.

### Single-developer constraint

**This entire refactor must be executed by one developer on a dedicated branch.** The Gradle restructure and module moves in Phase 1 will touch import paths and build files across almost every server file. Any other developer branching off `main` mid-refactor would face an unmergeable diff. No parallel server feature work should be started until the final phase merges.

Each phase merges to the refactor branch independently, giving the PA visibility of progress and a clean rollback point if a phase introduces a regression.

### Phases

**Phase 1 — Gradle restructure + underwear scaffolding** → DEV-007  
Create the Gradle multi-project layout. Migrate `FileStore`/`GcsFileStore`/`S3FileStore`/`LocalFileStore` into `underwear/gcs`, `underwear/s3`, `underwear/local-fs` as the first underwear modules. Create `underwear/common` with `BlobPort`. Create `hub-common` with `UserId` and port marker interfaces. No business logic changes — all tests must pass unchanged.

> **Phase 1 must ship `LocalHttpFileStore` alongside the restructure.** `BlobPort` must include `generatePresignedPutUrl(key, mimeType): String`. The existing `LocalFileStore` cannot implement this (no local HTTP server), so Phase 1 delivers a replacement: `LocalHttpFileStore` — a minimal embedded http4k server that accepts `PUT /uploads/<key>` and serves `GET /uploads/<key>`, returning real `http://localhost:<port>/…` signed URLs. Once this exists, the initiate → direct-to-storage → confirm upload flow works identically in local dev, test, and production. **The `POST /api/content/upload` direct-upload route (bytes through the server) must not be removed in Phase 1** — it remains available as an internal/tooling path. However, it should be moved to `/api/internal/upload` and excluded from the public OpenAPI spec, making clear it is not part of the stable E2EE upload surface. A local dev stack then requires only two containers: PostgreSQL (already in place) and `LocalHttpFileStore`. The existing `StubTimeLockProvider` wires in-process for tlock — no third container needed until M12 ships a real drand sidecar.

**Phase 2 — VaultHub extraction** → DEV-008  
`CapsuleService` currently only depends on `capsuleRepo` — it is the cleanest seam in the codebase. Extract it into `vault-hub` with `IdentityPort` and `KeyPort` as outbound ports. Validates the Gradle module pattern before touching dirtier seams.

**Phase 3 — MediaHub extraction + underwear/jackson** → DEV-009  
Extract the upload domain into `media-hub`. Requires defining `SharesPort` (replaces the direct `socialRepo`/`plotRepo` dependencies in `UploadService` — the dirty seam). Wrap Jackson into `underwear/jackson` at the same time.

**Phase 4 — IdentityHub + SocialHub extraction** → DEV-010  
Resolve the `AuthService` dirty seam (`AuthService` currently depends on `socialRepo` and `plotRepo`) by extracting a `UserLifecycleOrchestrator` at the application layer. Extract identity and social domains into their respective hubs.

**Phase 5 — PlotHub extraction** → DEV-011  
Extract the remaining Heirlooms-specific domain into `plot-hub`. Define `ConnectionsPort` as the outbound port replacing the direct social dependency.

**Phase 6 — vault-server app assembly** → DEV-012  
Assemble the standalone vault product app module wiring only `identity-hub` and `vault-hub`. Verify it starts, passes health check, and serves capsule endpoints. This is the proof-of-concept for the independent vault deployment.

---

## Out of scope

- Separate database instances per hub
- Network transport between hubs (gRPC, HTTP) — hubs communicate in-process in the monolith
- Client-side (Android / iOS / Web) structural changes — this brief covers the server only