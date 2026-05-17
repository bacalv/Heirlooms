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

## Completion notes

**Date:** 2026-05-17
**Branch:** agent/security-manager-1/SEC-002
**Scope completed:** Phases 1 and 2 only (as instructed). Phase 3 (integration tests) and Phase 4 (JaCoCo gate) deferred pending TST-004.

### Phase 1 — Coverage audit findings

Audited by source-code inspection (JaCoCo runner not available in this agent environment). Key uncovered branches identified:

**AuthService:**
- `decodeBase64Url`: null, blank, url-safe, std-base64-fallback, truly-invalid inputs
- `resolveSession`: null key, non-decodable token, no session found, expired session
- `getSaltForChallenge`: user not found → fakeSalt, user with null authSalt → fakeSalt
- `setupExisting`: all four sealed variants; null and blank wrapFormatRecovery branches; presence/absence of recovery passphrase upsert
- `register`: all four sealed variants; non-transactional path with and without recovery passphrase
- `connectViaInvite`: all four sealed variants including null inviter name fallback
- `pairingQr`: null, expired, wrong-state paths
- `completePairing`: NotFound (null), NotFound (expired), WrongState, NotFound (wrong user)
- `pairingStatus`: NotFound, Expired, Pending, Complete variants
- `revokeDevice`: callerDeviceKindHint fallback when session is null; Forbidden when both null

**SessionAuthFilter:**
- `isUnauthenticated`: /health, /docs/*, /api/auth/pairing/status* all bypass paths
- Static API key bypass: match, mismatch, empty staticApiKey
- Session token: missing header, valid token, expired session, null session (not found), std-base64 fallback
- `authUserId()`: valid UUID, missing header (FOUNDING_USER_ID fallback), invalid UUID (FOUNDING_USER_ID fallback)

**EnvelopeFormat:**
- `algIdLen == 0` branch (both symmetric and asymmetric)
- `validateAsymmetric`: version rejection, expectedAlgorithmId mismatch
- `isValidAsymmetric`: false case (structurally bad blob and expectedAlgorithmId mismatch)

**KeyService:**
- `registerDevice`: invalid deviceKind, AlreadyRegistered, invalid pubkey b64, invalid wrappedMasterKey b64
- `retireDevice`: device not found, already retired
- `touchDevice`: device not found, device retired
- `putPassphrase`: invalid wrappedMasterKey b64, invalid salt b64
- `registerOnLink`: NotFound, Expired, WrongState, code mismatch, invalid deviceKind, invalid pubkey b64
- `wrapLink`: NotFound, Expired, WrongState, invalid wrappedMasterKey b64
- `generateLinkCode`: format and charset validation
- `initiateLink`, `getLinkStatus`, `getPassphrase`, `deletePassphrase` delegation

### Phase 2 — Tests written

Four files added/modified on branch `agent/security-manager-1/SEC-002`:

| File | New tests |
|------|-----------|
| `AuthServiceUnitTest.kt` (new) | 64 |
| `SessionAuthFilterUnitTest.kt` (new) | 19 |
| `KeyServiceUnitTest.kt` (new) | 38 |
| `EnvelopeFormatTest.kt` (extended) | 7 |
| **Total** | **128** |

All tests use mockk — no database/Testcontainers required. Compile-time correctness verified by inspection against all relevant source signatures.

### Remaining work (blocked on TST-004)

- Phase 3: integration tests for end-to-end auth flows in HeirloomsTest/
- Phase 4: JaCoCo gate enforcement in HeirloomsTest/build.gradle.kts

The acceptance criteria for phases 3+4 (JaCoCo gate, 100% instruction coverage confirmed by tooling) cannot be met until TST-004 delivers the integration test harness and coverage runner.
