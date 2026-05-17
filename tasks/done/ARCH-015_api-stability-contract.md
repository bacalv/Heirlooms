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

Completed 2026-05-17 by TechnicalArchitect.

**Output:** `docs/briefs/ARCH-015_api-stability-contract.md`

**Summary of findings:**

1. **Frozen surface enumerated.** ~80 routes across auth, keys, uploads, capsules,
   plots, trellises, social, and diagnostics. Full table in brief §1.

2. **Stability policy defined.** Permitted: new endpoints, new optional
   response/request fields, internal refactors. Prohibited: removed/renamed endpoints,
   removed/renamed response fields, auth flow changes, envelope format changes,
   tightened validation.

3. **Versioning recommendation:** no versioning. Strict no-breaking-change policy
   enforced exclusively by frozen-client regression tests. Rationale: the freeze is
   time-bounded (M11+M12, ~2-4 months); M11 additions are genuinely additive; tests
   provide a stronger guarantee than version headers at this scale.

4. **8 critical regression test pairs identified** (FC-01 through FC-08) covering:
   challenge+login, register, encrypted upload initiate+confirm, upload list
   pagination, sharing flow, capsule create+list, capsule seal (pre-M11 POST path),
   and sharing key registration+retrieval. Recommended class: `FrozenClientRegressionTest.kt`.

5. **M11 wave-by-wave assessment complete.** Waves 0-4 and 6: fully additive, no
   frozen client impact. Wave 5 (sealing): requires keeping `POST /seal` unchanged
   and adding `PUT /seal` as the new M11 path. Wave 7 (read-path): adding nullable
   fields to `GET /api/capsules/{id}` response — backwards-compatible given lenient
   deserialisers.

**Critical action items for the developer implementing M11:**
- Wave 5: **do not modify** `POST /api/capsules/{id}/seal`. Add `PUT` alongside it.
- Wave 7: verify client deserialiser leniency before deploying amended `GET /capsules/{id}`.
- Keep `/api/flows/*` deprecated aliases live throughout M11 and M12.

**Open questions raised for CTO:** 4 items in brief §7 (pre-M11 sealed capsule
delivery in M12; deserialiser verification; /api/flows alias removal timing;
GET /api/friends vs GET /api/connections routing after V31 migration).
