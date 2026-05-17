---
id: SEC-002
title: 100% coverage plan for auth/crypto server paths
category: Security
priority: High
status: queued
depends_on: [TST-004]
touches:
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/service/auth/
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/crypto/
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/routes/auth/
  - HeirloomsTest/
assigned_to: SecurityManager
estimated: 2-3 days (agent)
---

## Goal

Achieve 100% instruction coverage on security-critical server code paths. "Impossible" is the wrong framing — the goal is: every line of auth and crypto code is executed by at least one test, so no untested code path exists that could hide a vulnerability.

## Scope (security-critical classes)

Priority 1 — must hit 100%:
- `AuthService` (login, register, session, pairing, invite flows)
- `EnvelopeFormat` (all validation branches)
- `SessionAuthFilter` (all auth paths including API key bypass)
- `AuthRoutes` (all route handlers)

Priority 2 — target 90%+:
- `KeyService` (device registration, pairing state machine, passphrase CRUD)
- `KeysRoutes`
- `CriteriaEvaluator` (SQL injection surface — all branch conditions)

Priority 3 — current state audit:
- Run `./gradlew coverageTest` and extract per-class coverage for all security-critical classes
- Document the gap between current and target

## Plan

### Phase 1: Audit current coverage
Run the JaCoCo report and extract line-by-line coverage for the priority 1 classes. Document every uncovered branch.

### Phase 2: Write unit tests for uncovered branches
Add tests to `HeirloomsServer/src/test/kotlin/` for:
- Error paths (invalid invite, wrong password, expired session)
- Edge cases in `EnvelopeFormat` (wrong algorithm ID, truncated payload, etc.)
- API key bypass behaviour in `SessionAuthFilter`
- All sealed class variants in `AuthService` results

### Phase 3: Write integration tests for end-to-end auth flows
Add tests to `HeirloomsTest/` for:
- Full register → login → authenticated request cycle
- Session expiry
- Concurrent login from two devices

### Phase 4: Enforce with JaCoCo gate
Tighten the JaCoCo gate in `HeirloomsTest/build.gradle.kts` for the security-critical package:
```kotlin
classDirectories.setFrom(files(classDirectories.files.map {
    fileTree(it) { include("**/service/auth/**", "**/crypto/**", "**/filters/**") }
}))
violationRules { rule { limit { minimum = "1.0".toBigDecimal() } } }
```

## Acceptance criteria

- 100% instruction coverage on `AuthService`, `EnvelopeFormat`, `SessionAuthFilter`, `AuthRoutes`
- 90%+ on `KeyService`, `KeysRoutes`, `CriteriaEvaluator`
- JaCoCo gate enforced — build fails if coverage drops
- All existing 326 unit tests + integration tests still pass
