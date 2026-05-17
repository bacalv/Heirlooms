---
id: DEV-012
title: Hub architecture — Phase 6 — vault-server standalone app assembly
category: Feature
priority: Medium
assigned_to: Developer
depends_on: [DEV-011]
touches:
  - vault-server/ (new module)
branch: refactor/hub-architecture
estimated: 3 hours
---

# DEV-012 — Hub architecture Phase 6: vault-server standalone app assembly

## Context

Sixth and final task in the hub architecture refactor. DEV-011 must be merged to
`refactor/hub-architecture` first. Read `docs/briefs/ARCH-016_hub-architecture-and-underwear.md`
before starting.

This phase is the proof-of-concept for the standalone vault product — a deployable server
that provides the time-locking E2EE capsule system without the Heirlooms photo-sharing domain.
It wires only `identity-hub` and `vault-hub`, with no dependency on `media-hub`, `social-hub`,
or `plot-hub`.

## What to build

### New module: vault-server

```
vault-server/
  src/main/kotlin/digital/heirlooms/vaultserver/
    Main.kt          — startup, config, server
    AppRoutes.kt     — routes for identity + vault only
    adapters/        — adapter implementations for identity-hub and vault-hub ports
```

### Routes to include

Wire only the endpoints that belong to `identity-hub` and `vault-hub`:

- All auth routes (`/api/auth/*`)
- All key routes (`/api/keys/*`)
- All capsule routes (`/api/capsules/*`)
- Health endpoint (`/health`)

Explicitly exclude: upload routes, plot routes, social routes, connection routes, diagnostics.

### vault-server module dependencies

```
vault-server depends on:
  identity-hub
  vault-hub
  hub-common
  underwear/postgres   (DataSource factory)
  underwear/gcs OR underwear/s3  (configurable via env)
  underwear/netty
  underwear/flyway
```

Does NOT depend on: `media-hub`, `social-hub`, `plot-hub`, `HeirloomsServer`.

### Migrations

`vault-server` runs only the Flyway migrations needed for its tables. Create a separate
migration path or filter that applies only the identity and vault schema migrations,
skipping upload, plot, and social tables.

### Configuration

Reuse `AppConfig` structure but only the fields relevant to vault-server (DB, storage,
auth secret, server port). Avoid pulling in the full `HeirloomsServer` config.

## Acceptance criteria

- `vault-server` starts cleanly against a fresh Postgres instance
- `GET /health` returns 200
- Auth flow works end-to-end (register, login, session)
- Capsule CRUD endpoints respond correctly
- Upload, plot, and social endpoints return 404 (not wired — not 500)
- `vault-server` Gradle module has zero compile-time dependency on `media-hub`,
  `social-hub`, or `plot-hub` (verify with `./gradlew :vault-server:dependencies`)
- `./gradlew test` passes across all modules

## This task also closes the refactor branch

Once DEV-012 passes review, the `refactor/hub-architecture` branch is ready to merge to
`main`. Update `tasks/progress.md` and note that the six-phase hub architecture refactor
is complete.
