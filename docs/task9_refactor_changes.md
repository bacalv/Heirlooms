# Task 9 — Server Refactor: Phases 1–4

**Date:** 2026-05-14  
**Status:** Phases 1–4 complete; code compiles clean; full test run was interrupted before completion

---

## What was done

### Phase 1: Extract domain data classes

All domain data classes, enums, and sealed classes were moved out of `Database.kt` into a new `domain/` sub-package hierarchy:

| New file | Types moved |
|---|---|
| `domain/auth/UserRecord.kt` | `UserRecord`, `UserSessionRecord`, `InviteRecord`, `FOUNDING_USER_ID` |
| `domain/capsule/CapsuleRecord.kt` | `CapsuleRecord`, `CapsuleShape`, `CapsuleState`, `CapsuleSummary`, `CapsuleDetail` |
| `domain/upload/UploadRecord.kt` | `UploadRecord`, `UploadPage`, `UploadSort`, `DecodedCursor` |
| `domain/keys/WrappedKeyRecord.kt` | `WrappedKeyRecord`, `RecoveryPassphraseRecord`, `PendingDeviceLinkRecord`, `AccountSharingKeyRecord`, `FriendRecord` |
| `domain/plot/PlotRecord.kt` | `PlotRecord`, `FlowRecord`, `PlotItemRecord`, `PlotItemWithUpload`, `PlotMemberRecord`, `SharedMembershipRecord`, `PlotInviteRecord` |

**Backwards compatibility:** `Database.kt` was updated to import from the domain packages. A top-level `val FOUNDING_USER_ID` re-export was added so `SessionAuthFilter.kt` and other files in the root package continue to resolve it without changes.

All handler files that explicitly name domain types received the minimal required import statements. Test files were similarly updated.

**Commit:** `a66b529` — "refactor: phase 1 — extract domain data classes"

---

### Phases 2–4: Extract repository classes

All SQL methods were extracted from `Database.kt` into individual repository classes under `repository/`:

| New file | SQL groups covered |
|---|---|
| `repository/diag/DiagRepository.kt` | `insertDiagEvent`, `listDiagEvents` |
| `repository/storage/BlobRepository.kt` | `insertPendingBlob`, `deletePendingBlob`, `deleteStalePendingBlobs` |
| `repository/social/SocialRepository.kt` | `upsertSharingKey`, `getSharingKey`, `listFriends`, `createFriendship`, `areFriends` |
| `repository/auth/AuthRepository.kt` | All user, session, invite, and pairing-link SQL |
| `repository/keys/KeyRepository.kt` | All wrapped-key, recovery-passphrase, and device-link SQL |
| `repository/capsule/CapsuleRepository.kt` | All capsule SQL + all sealed result types (`UpdateResult`, `SealResult`, `CancelResult`) |
| `repository/plot/PlotRepository.kt` | All plot CRUD + criteria validation + `PlotUpdateResult`, `PlotDeleteResult`, `BatchReorderResult` |
| `repository/plot/FlowRepository.kt` | All flow SQL + `autoPopulateFlow`, `runUnstagedFlowsForUpload` + `FlowCreateResult`, `FlowUpdateResult` |
| `repository/plot/PlotItemRepository.kt` | All staging + collection-item SQL + `ApproveResult`, `RejectResult`, `AddItemResult`, `RemoveItemResult` |
| `repository/plot/PlotMemberRepository.kt` | All member/invite/lifecycle SQL + `AddMemberResult`, `RedeemInviteResult`, `AcceptInviteResult`, `RejoinResult`, `RestorePlotResult`, `TransferOwnershipResult`, `SetPlotStatusResult`, `LeavePlotResult` |
| `repository/upload/UploadRepository.kt` | All upload SQL including pagination + cursor logic + `CompostResult`, `RestoreResult` |

**Shim approach:** `Database.kt` was replaced with a facade that:
1. Instantiates all 11 repository classes
2. Delegates every public method to the appropriate repository
3. Does not change any handler files (all `database.someMethod()` calls continue to work)

**Result type aliases:** Because Kotlin sealed class subtype branches in `when` expressions cannot use typealiases, the handler files were updated to reference result types via their repository class (e.g., `is CapsuleRepository.UpdateResult.Success`). Top-level typealiases (`typealias UpdateResult = CapsuleRepository.UpdateResult`) remain in `Database.kt` for cases where the type is used as a return type annotation.

---

## Key decisions

1. **`withTransaction` is duplicated per repository.** Each repository that needs atomic multi-step operations has its own private `withTransaction` helper — a verbatim copy of the original in `Database.kt`. This is acceptable at this scale; a shared `DatabaseInfra.kt` utility can be extracted in a follow-up once all repositories are stable.

2. **`CriteriaEvaluator` is referenced by three repositories** (`PlotRepository`, `FlowRepository`, `PlotItemRepository`). All three import it from the root package `digital.heirlooms.server.CriteriaEvaluator` — no circular dependency since `CriteriaEvaluator` has no repository dependency.

3. **`updateTags` takes a callback lambda** to invoke `FlowRepository.runUnstagedFlowsForUpload`. This avoids a direct dependency from `UploadRepository` to `FlowRepository` while keeping the original behaviour (flows are re-evaluated after each tag update) intact.

4. **`listUploadsPaginated` takes `PlotRepository?` as an optional parameter.** The `Database` facade passes its own `plots` instance; callers that don't need plot filtering pass `null`.

5. **No behaviour changes.** Every SQL query, result type, and error handling path is identical to the original `Database.kt`.

---

## Files changed

**New files (domain):** 5 files under `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/domain/`

**New files (repository):** 11 files under `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/repository/`

**Modified source files:** `Database.kt` (replaced with delegation facade), `AuthHandler.kt`, `CapsuleHandler.kt`, `ExifExtractionService.kt`, `FlowHandler.kt`, `KeysHandler.kt`, `PlotHandler.kt`, `SharedPlotHandler.kt`, `SharingKeyHandler.kt`, `UploadHandler.kt`

**Modified test files:** `AuthHandlerTest.kt`, `CapsuleHandlerTest.kt`, `IsolationTest.kt`, `KeysHandlerTest.kt`, `PaginationTest.kt`, `PlotHandlerTest.kt`, `UploadHandlerTest.kt`

---

## What is NOT done (phases 5–8)

- Service extraction (business logic still in handlers)
- Route splitting (`UploadHandler.kt` etc. not yet split into `routes/`)
- Representation layer (`toJson()` extension functions still in handlers and `Database.kt`)
- Repository interfaces (concrete classes only; no `PostgresXxxRepository` pattern)
- Filter/config/storage sub-package moves
