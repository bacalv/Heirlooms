---
id: ARCH-015
title: API stability contract — freeze client-facing API surface before M11
category: Architecture
priority: High
status: queued
assigned_to: TechnicalArchitect
depends_on: [ARCH-010]
touches:
  - docs/briefs/
  - HeirloomsServer/src/
  - HeirloomsServer/src/test/
estimated: 1 session
---

## Background

A strategic decision was made on 2026-05-17: app development (Android, web, iOS) is
frozen at the current feature set while M11 and M12 backend work proceeds. The apps
will not receive new features until the server-side cryptographic construction is
complete and stable.

This creates a new constraint: **server changes must not silently break frozen app
versions**. The apps cannot be updated in lockstep with the server during M11/M12.
The Technical Architect must define what "stable API" means in practice and put a
mechanism in place to enforce it.

## Task

Produce a brief (`docs/briefs/ARCH-015_api-stability-contract.md`) that defines:

### 1. Scope — what is frozen

List the current API endpoints that the frozen app versions depend on. These are the
endpoints the Android app (versionCode 59), web app, and iOS scaffold currently call.
Cross-reference ARCH-010 (M11 API surface) to identify which new endpoints M11 will
add vs which existing endpoints it may modify.

### 2. Stability policy

Define what "stable" means for the frozen surface:
- Which changes are permitted (additive: new endpoints, new optional fields)?
- Which changes are prohibited (breaking: removed endpoints, changed field names,
  changed auth flows, changed envelope formats)?
- How are breaking changes flagged and handled if unavoidable?

### 3. Versioning mechanism

Recommend a lightweight versioning approach appropriate for Heirlooms' current scale.
Options include:
- API version header (`X-Heirlooms-API-Version`)
- URL versioning (`/api/v2/...`)
- No versioning, strict no-breaking-changes policy enforced by tests

Recommend the simplest option that enforces the constraint without adding maintenance
burden.

### 4. Enforcement — frozen client integration tests

Define a test strategy that catches regressions against frozen client behaviour.
Recommended approach: a small set of integration tests in the server test suite that
simulate the request shapes the frozen apps send, asserting the responses remain
structurally compatible. These tests must pass on every server commit throughout M11
and M12.

Identify the 5–10 most critical request/response pairs to cover (e.g., auth, upload,
capsule create/seal, key wrapping endpoints).

### 5. M11 compatibility assessment

Review the M11 API surface (ARCH-010) against the frozen endpoint list. Flag any
M11 changes that would break a frozen client, and propose how to introduce them
without a breaking change (e.g., additive schema migration, new endpoint alongside
old).

## Output

- `docs/briefs/ARCH-015_api-stability-contract.md` — the brief covering all five
  sections above, with explicit recommendations and any open questions for the CTO.

## Completion notes

<!-- TechnicalArchitect appends here and moves file to tasks/done/ -->
