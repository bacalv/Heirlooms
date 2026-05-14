# Domain & Repository Refactor — One Class Per File

**Date:** 2026-05-14
**Branch:** main (isolated worktree)
**Commit message:** refactor: one-class-per-file across domain/ and repository/

## Summary

Mechanical refactor only — no logic changes. Each type that was bundled in a multi-class file was extracted into its own `.kt` file following strict one-class-per-file convention.

## Files Split

### domain/auth/

| New file | Extracted type |
|---|---|
| `AuthConstants.kt` | `FOUNDING_USER_ID` (top-level val) |
| `UserRecord.kt` | `UserRecord` (kept in place, other types removed) |
| `UserSessionRecord.kt` | `UserSessionRecord` |
| `InviteRecord.kt` | `InviteRecord` |

### domain/capsule/

| New file | Extracted type |
|---|---|
| `CapsuleRecord.kt` | `CapsuleRecord` (kept in place, other types removed) |
| `CapsuleShape.kt` | `CapsuleShape` (enum) |
| `CapsuleState.kt` | `CapsuleState` (enum) |
| `CapsuleSummary.kt` | `CapsuleSummary` |
| `CapsuleDetail.kt` | `CapsuleDetail` |

### domain/keys/

| New file | Extracted type |
|---|---|
| `WrappedKeyRecord.kt` | `WrappedKeyRecord` (kept in place, other types removed) |
| `RecoveryPassphraseRecord.kt` | `RecoveryPassphraseRecord` |
| `PendingDeviceLinkRecord.kt` | `PendingDeviceLinkRecord` |
| `AccountSharingKeyRecord.kt` | `AccountSharingKeyRecord` |
| `FriendRecord.kt` | `FriendRecord` |

### domain/plot/

| New file | Extracted type |
|---|---|
| `PlotRecord.kt` | `PlotRecord` (kept in place, other types removed) |
| `FlowRecord.kt` | `FlowRecord` |
| `PlotItemRecord.kt` | `PlotItemRecord` |
| `PlotItemWithUpload.kt` | `PlotItemWithUpload` |
| `PlotMemberRecord.kt` | `PlotMemberRecord` |
| `SharedMembershipRecord.kt` | `SharedMembershipRecord` |
| `PlotInviteRecord.kt` | `PlotInviteRecord` |

### domain/upload/

| New file | Extracted type |
|---|---|
| `UploadRecord.kt` | `UploadRecord` (kept in place, other types removed) |
| `UploadPage.kt` | `UploadPage` |
| `UploadSort.kt` | `UploadSort` (enum) |
| `DecodedCursor.kt` | `DecodedCursor` |

## Repository files

No splitting required. Each repository file (`AuthRepository.kt`, `CapsuleRepository.kt`, `DiagRepository.kt`, `KeyRepository.kt`, `FlowRepository.kt`, `PlotItemRepository.kt`, `PlotMemberRepository.kt`, `PlotRepository.kt`, `SocialRepository.kt`, `BlobRepository.kt`, `UploadRepository.kt`) contains exactly one class. Nested sealed result types (e.g. `CapsuleRepository.UpdateResult`, `UploadRepository.CompostResult`) and `PlotMemberRepository.InviteInfo` remain in place per the sealed-family convention.

## Import changes

None required. All types remain in the same package (`digital.heirlooms.server.domain.*` or `digital.heirlooms.server.repository.*`), so all existing import statements continue to resolve correctly.

## Build verification

`./gradlew compileKotlin` — BUILD SUCCESSFUL (only pre-existing warnings about unused parameters).

`./gradlew test` — 324 tests passed, 2 pre-existing failures in `FinUploadHandlerTest` unrelated to this refactor (MockK stub missing for `findUploadByIdForSharedMember` — a handler-layer mock setup issue).
