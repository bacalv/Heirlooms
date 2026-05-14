# Server Codebase Refactor Proposal

**Status:** Design document — no code changes.  
**Date:** 2026-05-14  
**Author:** Claude Sonnet 4.6 (based on full source read)

---

## 1. Current State Analysis

All 28 files live in a single flat package `digital.heirlooms.server`. The table below documents each file's current role and misplaced responsibilities.

| File | Lines | What it does today | Misplaced responsibilities |
|---|---|---|---|
| `Database.kt` | 3849 | Connection pool, Flyway migrations, every SQL query across all domains, all domain data class definitions, all `ResultSet` extension mappers, cursor encoding/decoding, result sealed classes, transaction helper | Everything except SQL should live elsewhere. Data classes belong in `domain/`. Result sealed classes should be co-located with their service. Mappers are internal infrastructure. Business rules (`canCompost`, `autoPopulateFlow`, `runUnstagedFlowsForUpload`, `createFlow` staging policy) belong in a service layer. |
| `UploadHandler.kt` | 1293 | Route definitions, request parsing, business logic (dedup check, thumbnail orchestration, compost cleanup, migration logic), JSON serialisation of `UploadRecord` | Business logic and orchestration should be in `UploadService`. JSON serialisation belongs in `representation/`. `launchCompostCleanup` is infrastructure, not a route. `buildApp` is an entry point concern. |
| `AuthHandler.kt` | 528 | Route definitions, token generation, HMAC-based fake-salt logic, `fakeSalt`, `issueToken`, `resolveSession`, business rules (invite validation, duplicate username check) | Token/session logic belongs in `AuthService`. Route definitions are the only correct content. |
| `PlotHandler.kt` | 249 | Route definitions, JSON serialisation of `PlotRecord.toJson()`, criteria validation bridge | `toJson()` belongs in `representation/`. Criteria validation bridging (`validateAndSerializeCriteria`) belongs in a service. |
| `FlowHandler.kt` | 371 | Route definitions for flows and plot items, JSON serialisation of `FlowRecord.toJson()` and `PlotItemWithUpload.toJson()`, staging approval business logic (DEK parsing) | DEK parsing and staging approval logic belongs in `StagingService`. `toJson()` extension functions belong in `representation/`. |
| `SharedPlotHandler.kt` | 405 | Route definitions for shared plot operations (members, invites, leave, restore, transfer, status) | Mostly clean; some inline JSON building should be in `representation/`. |
| `CapsuleHandler.kt` | 358 | Route definitions, JSON serialisation (`toDetailJson`, `toSummaryJson`, `toReverseLookupJson`), request parsing | Serialisation should be in `representation/capsule/`. |
| `KeysHandler.kt` | 436 | Route definitions, `WrappedKeyRecord.toJson()`, `RecoveryPassphraseRecord.toJson()`, device-link business logic | Serialisation belongs in `representation/keys/`. Link code generation (`generateLinkCode`) belongs in `KeyService`. |
| `FriendsHandler.kt` | 39 | Single route: list friends | Correct scope; inline JSON building could move to a representation. |
| `SharingKeyHandler.kt` | 108 | Three routes: upsert, get-own, get-friend sharing key; `AccountSharingKeyRecord.toJson()` | Serialisation belongs in `representation/sharing/`. |
| `SessionAuthFilter.kt` | 60 | Session authentication filter; `Request.authUserId()` extension; `FOUNDING_USER_ID` reference; `UNAUTHENTICATED_PATHS` whitelist | Correct scope. `Request.authUserId()` is a useful extension; keep here or in a `filters/` sub-package. |
| `ApiKeyFilter.kt` | 17 | Static API-key bypass filter (dev/test only) | Correct scope; tiny and fine as-is. |
| `CorsFilter.kt` | 20 | CORS headers filter | Correct scope. |
| `AppConfig.kt` | 103 | Application configuration, `StorageBackend` enum, env/properties loading | Correct scope; candidate for `config/`. |
| `Main.kt` | 75 | Wiring entry point: reads config, builds `Database`, `FileStore`, services, starts server | Should remain thin; currently reasonable. The `buildApp` function (in `UploadHandler.kt`) should move here or to a dedicated `AppRouter.kt`. |
| `FileStore.kt` | 53 | `FileStore` interface and `StorageKey` value class | Correct scope; belongs in `storage/`. |
| `FileStorage.kt` | 46 | `LocalFileStore` implementation | Correct scope; belongs in `storage/`. |
| `GcsFileStore.kt` | 136 | GCS `FileStore` + `DirectUploadSupport` implementation | Correct scope; belongs in `storage/`. |
| `S3FileStore.kt` | 138 | S3 `FileStore` implementation | Correct scope; belongs in `storage/`. |
| `DirectUploadSupport.kt` | 20 | `DirectUploadSupport` interface and `PreparedUpload` data class | Correct scope; belongs in `storage/`. |
| `ContentTypeExtensions.kt` | 43 | `mimeTypeToExtension()` utility | Belongs in `storage/` (consumed exclusively by storage implementations). |
| `CriteriaEvaluator.kt` | 241 | JSON criteria AST evaluator, SQL fragment builder; exception types | Belongs in `service/plot/`. The `CriteriaFragment` type and `ParamSetter` typealias are internal to this evaluator. |
| `EnvelopeFormat.kt` | 144 | Binary envelope format validator (symmetric + asymmetric), `AlgorithmIds`, exception types | Belongs in `service/crypto/` — it is validation logic, not storage. |
| `ExifExtractionService.kt` | 59 | Background EXIF recovery coroutine service | Already a service; belongs in `service/upload/`. |
| `PendingBlobsCleanupService.kt` | 81 | Background cleanup: stale blobs, dormant devices, expired sessions | Already a service; belongs in `service/`. |
| `ThumbnailGenerator.kt` | 106 | Image/video thumbnail generation (ffmpeg for video) | Belongs in `service/upload/`. |
| `MetadataExtractor.kt` | 156 | EXIF and ffprobe metadata extraction; `MediaMetadata` data class | `MediaMetadata` belongs in `domain/upload/`. Extractor belongs in `service/upload/`. |
| `TagValidator.kt` | 22 | Tag format validation; `TagValidationResult` sealed class | The sealed class and validation function are domain logic; belongs in `domain/upload/`. |

**Summary of the structural problems:**

1. `Database.kt` (3849 lines) contains every SQL query for every domain, all domain data classes, all result sealed classes, and embedded business logic. It is a monolith inside a monolith.
2. Handler files combine route wiring with business orchestration and JSON serialisation. No service layer exists.
3. Domain data classes (`UploadRecord`, `PlotRecord`, `FlowRecord`, etc.) live inside `Database.kt`, not in a dedicated domain layer.
4. No representation layer — `toJson()` extension functions are scattered across Database.kt and handler files.

---

## 2. Proposed Package Tree

```
digital.heirlooms.server/
│
├── config/
│   └── AppConfig.kt                    (moved from root)
│
├── domain/
│   ├── upload/
│   │   ├── UploadRecord.kt             (data class + UploadPage, UploadSort, DecodedCursor)
│   │   ├── MediaMetadata.kt            (data class; from MetadataExtractor.kt)
│   │   └── TagValidation.kt            (TagValidationResult + validateTags(); from TagValidator.kt)
│   ├── plot/
│   │   ├── PlotRecord.kt               (data class + PlotMemberRecord + SharedMembershipRecord + PlotInviteRecord + PlotItemRecord + PlotItemWithUpload)
│   │   └── FlowRecord.kt               (data class)
│   ├── capsule/
│   │   ├── CapsuleRecord.kt            (data class + CapsuleShape + CapsuleState + CapsuleDetail + CapsuleSummary)
│   │   └── CapsuleConstants.kt         (MESSAGE_MAX_BYTES, RECIPIENT_MAX_LENGTH)
│   ├── auth/
│   │   ├── UserRecord.kt               (data class + UserSessionRecord + InviteRecord)
│   │   └── PendingDeviceLinkRecord.kt  (data class)
│   └── keys/
│       ├── WrappedKeyRecord.kt         (data class)
│       ├── RecoveryPassphraseRecord.kt (data class)
│       └── AccountSharingKeyRecord.kt  (data class + FriendRecord)
│
├── repository/
│   ├── DatabaseInfra.kt                (HikariCP setup, withTransaction, create() factory; from Database companion + withTransaction)
│   ├── upload/
│   │   └── UploadRepository.kt         (all upload SQL: recordUpload, findUpload*, listUploads*, compost*, restore*, updateRotation, updateTags, updateExif, hardDeleteUpload, hasLiveSharedReference, userAlreadyHasStorageKey, createSharedUpload, migrateUploadToEncrypted, listPendingExifIds, getUploadById, existsByContentHash, findByContentHash, uploadExists; toUploadRecord() mapper)
│   ├── plot/
│   │   ├── PlotRepository.kt           (plots CRUD + listPlots + batchReorderPlots + getPlotByIdForUser + isMember* + fetchExpiredTombstonedPlots + hardDeletePlot + createSystemPlot; toPlotRecord() mapper)
│   │   ├── FlowRepository.kt           (flows CRUD + listFlows + getFlowById + deleteFlow; toFlowRecord() mapper)
│   │   ├── PlotItemRepository.kt       (getPlotItems + addPlotItem + removePlotItem + staging queries: getStagingItems + getStagingItemsForPlot + approveStagingItem + rejectStagingItem + deleteDecision + getRejectedItems)
│   │   └── PlotMemberRepository.kt     (getPlotKey + listMembers + addMember + createInvite + getInviteInfo + redeemInvite + listPendingInvites + confirmInvite + listSharedMemberships + acceptInvite + rejoinPlot + restorePlot + transferOwnership + setPlotStatus + leavePlot)
│   ├── capsule/
│   │   └── CapsuleRepository.kt        (createCapsule + getCapsuleById + listCapsules + updateCapsule + sealCapsule + cancelCapsule + getCapsulesForUpload + private helpers: insertRecipients + insertContents + insertMessage + queryRecipients + queryUploads + queryCurrentMessage + queryUploadCount + queryHasMessage; toCapsuleRecord() mapper)
│   ├── auth/
│   │   └── AuthRepository.kt           (createUser + findUserByUsername + findUserById + setUserAuth + resetUserAuth + createSession + findSessionByTokenHash + deleteSession + refreshSession + deleteExpiredSessions + createInvite + findInviteByToken + markInviteUsed + createPairingLink + setPairingWebSession + completePairingLink + getPendingDeviceLinkByCode + getPendingDeviceLinkByWebSessionId; toUserRecord + toUserSessionRecord + toInviteRecord + toPendingDeviceLinkRecord mappers)
│   ├── keys/
│   │   └── KeyRepository.kt            (insertWrappedKey + listWrappedKeys + getWrappedKeyByDeviceId* + retireWrappedKey + touchWrappedKey + retireDormantWrappedKeys + getRecoveryPassphrase + upsertRecoveryPassphrase + deleteRecoveryPassphrase + insertPendingDeviceLink + getPendingDeviceLink + registerNewDevice + completeDeviceLink + deleteExpiredDeviceLinks + getWrappedKeyByDeviceIdAndUser; toWrappedKeyRecord + toRecoveryPassphraseRecord mappers)
│   ├── social/
│   │   └── SocialRepository.kt         (upsertSharingKey + getSharingKey + listFriends + createFriendship + areFriends)
│   ├── storage/
│   │   └── BlobRepository.kt           (insertPendingBlob + deletePendingBlob + deleteStalePendingBlobs)
│   └── diag/
│       └── DiagRepository.kt           (insertDiagEvent + listDiagEvents)
│
├── service/
│   ├── upload/
│   │   ├── UploadService.kt            (orchestration: dedup check, store blob, thumbnail, metadata, record; confirmEncryptedUpload logic; compost/restore; share upload; migrate to encrypted)
│   │   ├── ExifExtractionService.kt    (moved from root; background EXIF recovery)
│   │   ├── ThumbnailService.kt         (generateThumbnail + extractVideoDuration; from ThumbnailGenerator.kt)
│   │   └── MetadataService.kt          (MetadataExtractor class; from MetadataExtractor.kt)
│   ├── plot/
│   │   ├── PlotService.kt              (createPlot + updatePlot + deletePlot + batchReorderPlots + leavePlot + withCriteriaValidation)
│   │   ├── FlowService.kt              (createFlow staging policy + autoPopulateFlow + runUnstagedFlowsForUpload + updateFlow + deleteFlow)
│   │   ├── StagingService.kt           (getStagingItems + getStagingItemsForPlot + approveStagingItem + rejectStagingItem + deleteDecision + getRejectedItems)
│   │   └── CriteriaEvaluator.kt        (moved from root)
│   ├── capsule/
│   │   └── CapsuleService.kt           (createCapsule + updateCapsule + sealCapsule + cancelCapsule; validation logic from handler)
│   ├── auth/
│   │   └── AuthService.kt              (issueToken + fakeSalt + resolveSession + registration/login orchestration)
│   ├── keys/
│   │   └── KeyService.kt               (generateLinkCode + device-link state machine: initiate/register/wrap/complete; passphrase CRUD orchestration)
│   ├── social/
│   │   └── SocialService.kt            (listFriends + friendship creation — thin today, ready to grow)
│   └── cleanup/
│       └── PendingBlobsCleanupService.kt (moved from root)
│
├── representation/
│   ├── upload/
│   │   └── UploadJson.kt               (UploadRecord.toJson() + UploadPage.toJson(); from Database.kt bottom)
│   ├── plot/
│   │   ├── PlotJson.kt                 (PlotRecord.toJson(); from PlotHandler.kt)
│   │   └── FlowJson.kt                 (FlowRecord.toJson() + PlotItemWithUpload.toJson(); from FlowHandler.kt)
│   ├── capsule/
│   │   └── CapsuleJson.kt              (toDetailJson + toSummaryJson + toReverseLookupJson; from CapsuleHandler.kt)
│   ├── keys/
│   │   └── KeysJson.kt                 (WrappedKeyRecord.toJson() + RecoveryPassphraseRecord.toJson(); from KeysHandler.kt)
│   └── sharing/
│       └── SharingJson.kt              (AccountSharingKeyRecord.toJson(); from SharingKeyHandler.kt)
│
├── routes/
│   ├── UploadRoutes.kt                 (all ContractRoute definitions from UploadHandler.kt; no business logic)
│   ├── CapsuleRoutes.kt                (capsule ContractRoute definitions; from CapsuleHandler.kt)
│   ├── PlotRoutes.kt                   (plot ContractRoute definitions; from PlotHandler.kt)
│   ├── FlowRoutes.kt                   (flow + plot-item ContractRoute definitions; from FlowHandler.kt)
│   ├── SharedPlotRoutes.kt             (shared-plot ContractRoute definitions; from SharedPlotHandler.kt)
│   ├── KeysRoutes.kt                   (device/link/passphrase ContractRoute definitions; from KeysHandler.kt)
│   ├── AuthRoutes.kt                   (auth ContractRoute definitions; from AuthHandler.kt)
│   ├── FriendsRoutes.kt                (from FriendsHandler.kt)
│   └── SharingKeyRoutes.kt             (from SharingKeyHandler.kt)
│
├── storage/
│   ├── FileStore.kt                    (interface + StorageKey; moved from root)
│   ├── DirectUploadSupport.kt          (interface + PreparedUpload; moved from root)
│   ├── LocalFileStore.kt               (from FileStorage.kt — rename for clarity)
│   ├── GcsFileStore.kt                 (moved from root)
│   ├── S3FileStore.kt                  (moved from root)
│   └── ContentTypeExtensions.kt        (mimeTypeToExtension; moved from root)
│
├── crypto/
│   └── EnvelopeFormat.kt               (moved from root; AlgorithmIds + validators)
│
├── filters/
│   ├── SessionAuthFilter.kt            (moved from root)
│   ├── ApiKeyFilter.kt                 (moved from root)
│   └── CorsFilter.kt                   (moved from root)
│
└── Main.kt                             (entry point; buildApp moves here or to AppRouter.kt)
```

Note: `Database.kt` disappears entirely. It is replaced by `repository/DatabaseInfra.kt` (infrastructure) and the individual repository classes.

---

## 3. Database.kt Split Plan

### 3.1 Logical function groups and their destination

**Group 1 — Upload queries (~600 lines)**
Functions: `recordUpload`, `findByContentHash`, `existsByContentHash`, `getUploadById`, `findUploadByIdForUser`, `findUploadByIdForSharedMember`, `recordView`, `listUploads`, `listCompostedUploads`, `compostUpload`, `restoreUpload`, `fetchExpiredCompostedUploads`, `hardDeleteUpload`, `userAlreadyHasStorageKey`, `hasLiveSharedReference`, `createSharedUpload`, `updateRotation`, `updateTags`, `uploadExists`, `listPendingExifIds`, `updateExif`, `migrateUploadToEncrypted`

Sealed result types: `CompostResult`, `RestoreResult` (move with functions to `UploadRepository.kt`)

Destination: `repository/upload/UploadRepository.kt`

---

**Group 2 — Upload pagination (~200 lines)**
Functions: `listUploadsPaginated`, `listCompostedUploadsPaginated`, `listAllTags`, `buildCursorCondition`, `encodeCursor`, `decodeCursor`, `encodeCompostedCursor`, `decodeCompostedCursor`

Destination: `repository/upload/UploadRepository.kt` (same class, separate section)

Note: the `DecodedCursor` private data class and `UploadSort` enum move to `domain/upload/UploadRecord.kt`.

---

**Group 3 — Plot queries (~500 lines)**
Functions: `listPlots`, `getPlotById`, `getPlotByIdConn`, `createPlot`, `updatePlot`, `deletePlot`, `batchReorderPlots`, `leavePlot`, `withCriteriaValidation`, `getPlotByIdForUser`, `isMember`, `isMemberConn`, `fetchExpiredTombstonedPlots`, `hardDeletePlot`, `createSystemPlot`

Sealed result types: `PlotUpdateResult`, `PlotDeleteResult`, `LeavePlotResult`, `BatchReorderResult`

Destination: `repository/plot/PlotRepository.kt`

The `createFlow` staging policy logic (visibility-based `effectiveStaging` calculation, `autoPopulateFlow`, `runUnstagedFlowsForUpload`) is business logic; it moves to `service/plot/FlowService.kt`.

---

**Group 4 — Flow queries (~130 lines)**
Functions: `listFlows`, `getFlowById`, `createFlow`, `updateFlow`, `deleteFlow`

Sealed result types: `FlowCreateResult`, `FlowUpdateResult`

Destination: `repository/plot/FlowRepository.kt`  
Business logic side: `FlowService.kt` owns staging policy and `autoPopulateFlow` / `runUnstagedFlowsForUpload` calls.

---

**Group 5 — Staging queries (~300 lines)**
Functions: `getStagingItems`, `getStagingItemsForPlot`, `approveStagingItem`, `rejectStagingItem`, `deleteDecision`, `getRejectedItems`

Sealed result types: `ApproveResult`, `RejectResult`

Destination: `repository/plot/PlotItemRepository.kt`  
The duplicate-content check inside `approveStagingItem` is business policy — keep in repository since it runs in the same transaction, but surface as a result variant (`DuplicateContent`) consumed by `StagingService`.

---

**Group 6 — Plot item (collection) queries (~120 lines)**
Functions: `getPlotItems`, `addPlotItem`, `removePlotItem`

Sealed result types: `AddItemResult`, `RemoveItemResult`

Destination: `repository/plot/PlotItemRepository.kt` (same class as staging)

---

**Group 7 — Capsule queries (~400 lines)**
Functions: `createCapsule`, `getCapsuleById`, `listCapsules`, `updateCapsule`, `sealCapsule`, `cancelCapsule`, `getCapsulesForUpload`, private helpers `insertRecipients`, `insertContents`, `insertMessage`, `queryRecipients`, `queryUploadsForCapsule`, `queryCurrentMessage`, `queryUploadCount`, `queryHasMessage`

Sealed result types: `UpdateResult`, `SealResult`, `CancelResult`

Destination: `repository/capsule/CapsuleRepository.kt`

---

**Group 8 — Plot members and invites (~450 lines)**
Functions: `getPlotKey`, `listMembers`, `addMember`, `createInvite`, `getInviteInfo`, `redeemInvite`, `listPendingInvites`, `confirmInvite`, `listSharedMemberships`, `acceptInvite`, `rejoinPlot`, `restorePlot`, `transferOwnership`, `setPlotStatus`

Sealed result types: `AddMemberResult`, `RedeemInviteResult`, `AcceptInviteResult`, `RejoinResult`, `RestorePlotResult`, `TransferOwnershipResult`, `SetPlotStatusResult`

Inner data class: `InviteInfo`

Destination: `repository/plot/PlotMemberRepository.kt`

---

**Group 9 — Auth (users, sessions, invites, pairing) (~350 lines)**
Functions: `createUser`, `findUserByUsername`, `findUserById`, `setUserAuth`, `resetUserAuth`, `createSession`, `findSessionByTokenHash`, `deleteSession`, `refreshSession`, `deleteExpiredSessions`, `createInvite`, `findInviteByToken`, `markInviteUsed`, `createPairingLink`, `setPairingWebSession`, `completePairingLink`

Destination: `repository/auth/AuthRepository.kt`

Note: there are two `createInvite` functions — one for user invites (`invites` table, takes `createdBy: UUID` and `rawToken: String`), and one for plot invites (`plot_invites` table, takes `plotId: UUID`). These split cleanly: user invite stays in `AuthRepository`, plot invite goes to `PlotMemberRepository`.

---

**Group 10 — Wrapped keys and device links (~350 lines)**
Functions: `insertWrappedKey`, `listWrappedKeys`, `getWrappedKeyByDeviceId`, `getWrappedKeyByDeviceIdForUser`, `getWrappedKeyByDeviceIdAndUser`, `retireWrappedKey`, `touchWrappedKey`, `retireDormantWrappedKeys`, `getRecoveryPassphrase`, `upsertRecoveryPassphrase`, `deleteRecoveryPassphrase`, `insertPendingDeviceLink`, `getPendingDeviceLink`, `getPendingDeviceLinkByCode`, `getPendingDeviceLinkByWebSessionId`, `registerNewDevice`, `completeDeviceLink`, `deleteExpiredDeviceLinks`

Destination: `repository/keys/KeyRepository.kt`

---

**Group 11 — Social (sharing keys, friendships) (~90 lines)**
Functions: `upsertSharingKey`, `getSharingKey`, `listFriends`, `createFriendship`, `areFriends`

Destination: `repository/social/SocialRepository.kt`

---

**Group 12 — Pending blobs (~50 lines)**
Functions: `insertPendingBlob`, `deletePendingBlob`, `deleteStalePendingBlobs`

Destination: `repository/storage/BlobRepository.kt`

---

**Group 13 — Diagnostics (~45 lines)**
Functions: `insertDiagEvent`, `listDiagEvents`

Destination: `repository/diag/DiagRepository.kt`

---

**Group 14 — Migrations and connection pool (~30 lines)**
Functions: `runMigrations`, `create()` companion factory, `withTransaction` helper

Destination: `repository/DatabaseInfra.kt`

All repository classes receive a `DataSource` constructor parameter (injected from `DatabaseInfra`). `withTransaction` becomes an `internal fun DataSource.withTransaction(...)` extension in `DatabaseInfra.kt` or a standalone top-level function in that file.

---

**Group 15 — ResultSet mappers (~150 lines)**
`toUploadRecord()`, `toCapsuleRecord()`, `toWrappedKeyRecord()`, `toUserRecord()`, `toUserSessionRecord()`, `toInviteRecord()`, `toRecoveryPassphraseRecord()`, `toPendingDeviceLinkRecord()`, `toPlotRecord()`, `toFlowRecord()`

Each mapper moves as a `private` extension function into its corresponding repository file. They are purely mechanical row-to-object conversions; keeping them private prevents leaking JDBC types across package boundaries.

---

**Group 16 — Domain model data classes (~300 lines, lines 1–273)**
All data classes and enums defined at the top of `Database.kt`:

| Class | Destination |
|---|---|
| `FOUNDING_USER_ID` | `repository/auth/AuthRepository.kt` (or `domain/auth/UserRecord.kt`) |
| `UserRecord`, `UserSessionRecord`, `InviteRecord` | `domain/auth/UserRecord.kt` |
| `CapsuleShape`, `CapsuleState`, `CapsuleRecord`, `CapsuleDetail`, `CapsuleSummary` | `domain/capsule/CapsuleRecord.kt` |
| `UploadRecord`, `UploadPage` | `domain/upload/UploadRecord.kt` |
| `UploadSort` | `domain/upload/UploadRecord.kt` |
| `WrappedKeyRecord`, `RecoveryPassphraseRecord`, `PendingDeviceLinkRecord` | `domain/keys/WrappedKeyRecord.kt` etc. |
| `AccountSharingKeyRecord`, `FriendRecord` | `domain/keys/AccountSharingKeyRecord.kt` |
| `PlotRecord`, `FlowRecord`, `PlotItemRecord`, `PlotItemWithUpload`, `PlotMemberRecord`, `SharedMembershipRecord`, `PlotInviteRecord` | `domain/plot/PlotRecord.kt` + `FlowRecord.kt` |

---

## 4. Handler Files Split Plan

### UploadHandler.kt (1293 lines)

**Routes that stay in `routes/UploadRoutes.kt`:**
All ContractRoute definitions (`uploadContractRoute`, `listUploadsContractRoute`, `listTagsContractRoute`, `checkContentHashContractRoute`, `listCompostedUploadsContractRoute`, `getUploadByIdContractRoute`, `prepareUploadContractRoute`, `initiateUploadContractRoute`, `resumableUploadContractRoute`, `confirmUploadContractRoute`, `migrateUploadContractRoute`, `fileProxyContractRoute`, `thumbProxyContractRoute`, `previewProxyContractRoute`, `readUrlContractRoute`, `rotationContractRoute`, `tagsContractRoute`, `viewUploadContractRoute`, `capsuleReverseLookupRoute`, `compostUploadContractRoute`, `restoreUploadContractRoute`, `shareUploadContractRoute`)

Route handlers should call `UploadService` and call `UploadRecord.toJson()` from `representation/upload/UploadJson.kt`.

**Business logic that moves to `service/upload/UploadService.kt`:**
- `uploadHandler` logic: SHA-256 dedup, storage save, thumbnail generation, metadata extraction, `recordUpload`
- `confirmUpload` / `confirmEncryptedUpload` / `confirmLegacyUpload`: full orchestration
- `migrateUploadHandler`: atomic storage swap + DB update
- `handleShareUpload`: friendship check, dedup check, `createSharedUpload`
- `launchCompostCleanup`: move to `PendingBlobsCleanupService` or its own background service
- `tryStoreThumbnail`: helper; move into `UploadService` or `ThumbnailService`

**Other removals from handler:**
- `buildApp` and `mergedSpecWithApiKeyAuth` move to `Main.kt` or a new `AppRouter.kt`
- `swaggerInitializerJs` constant and `UploadPage.toJson()` move to `representation/`
- `sha256Hex` becomes a private function in `UploadService`
- `tryParseDate` moves to a shared utility or stays private in `UploadRoutes`

**Controller layer decision:** Not needed. Routes can call `UploadService` directly. The service is the controller equivalent.

---

### AuthHandler.kt (528 lines)

**Routes that stay in `routes/AuthRoutes.kt`:**
`challengeRoute`, `loginRoute`, `setupExistingRoute`, `logoutRoute`, `meRoute`, `getInviteRoute`, `registerRoute`, `pairingInitiateRoute`, `pairingQrRoute`, `pairingCompleteRoute`, `pairingStatusRoute`

**Business logic that moves to `service/auth/AuthService.kt`:**
- `issueToken()`, `generateRawToken()`: token generation
- `fakeSalt()`: HMAC-based fake salt (anti-enumeration)
- `resolveSession()`: session lookup from `X-Api-Key` header
- `generateNumericCode()`: pairing code generator
- The registration flow logic (invite validation, user creation, wrapped key insertion, friendship creation)
- The login flow logic (credential verification)

**Encoding helpers** (`urlEnc`, `urlDec`, `stdDec`, `authRng`, `sha256()`): private to `AuthService`.

**Controller layer decision:** Not needed. Routes call `AuthService` directly.

---

### PlotHandler.kt (249 lines)

**Routes that stay in `routes/PlotRoutes.kt`:**
`listPlotsRoute`, `createPlotRoute`, `updatePlotRoute`, `deletePlotRoute`, `batchReorderPlotsRoute`

**Business logic that moves to `service/plot/PlotService.kt`:**
- `validateAndSerializeCriteria()`: criteria validation + JSON serialisation
- `parsePlotBody()`: request parsing helper (could stay in routes as private, since it only parses HTTP input)

**Representation:**
- `PlotRecord.toJson()` moves to `representation/plot/PlotJson.kt`
- `CriteriaSerializeResult` sealed class: private to `PlotService` or routes; keep co-located

**Controller layer decision:** Not needed.

---

### FlowHandler.kt (371 lines)

**Routes that stay in `routes/FlowRoutes.kt`:**
`listFlowsRoute`, `createFlowRoute`, `updateFlowRoute`, `deleteFlowRoute`, `getFlowStagingRoute`, `getPlotStagingRoute`, `approveStagingRoute`, `rejectStagingRoute`, `deleteDecisionRoute`, `getRejectedRoute`, `getPlotItemsRoute`, `addPlotItemRoute`, `removePlotItemRoute`

**Business logic that moves to services:**
- Criteria validation in `handleCreateFlow` / `handleUpdateFlow` → `FlowService`
- DEK decoding in `handleApproveStagingItem` → `StagingService`
- DEK decoding in `handleAddPlotItem` → `PlotItemService` (or part of `StagingService`)

**Representation:**
- `FlowRecord.toJson()` → `representation/plot/FlowJson.kt`
- `PlotItemWithUpload.toJson()` → `representation/plot/FlowJson.kt`

**Controller layer decision:** A thin `StagingService` is warranted because staging approval involves DEK handling (crypto) + database write. Routes call `StagingService`.

---

### SharedPlotHandler.kt (405 lines)

**Routes that stay in `routes/SharedPlotRoutes.kt`:**
All 16 route functions

**Business logic:** Largely already delegated to `Database`. Route handlers are thin: parse request, call database, format response. After the repository split, routes call `PlotMemberRepository` (or a thin `SharedPlotService`).

**Inline JSON building** in handlers: move to `representation/` or use `JsonNodeFactory` patterns already in place.

**Controller layer decision:** No service layer strictly needed here; routes call repository directly is acceptable given the low logic density. A `SharedPlotService` could be introduced if membership lifecycle complexity grows.

---

### CapsuleHandler.kt (358 lines)

**Routes that stay in `routes/CapsuleRoutes.kt`:**
`createCapsuleRoute`, `listCapsulesRoute`, `getCapsuleRoute`, `patchCapsuleRoute`, `sealCapsuleRoute`, `cancelCapsuleRoute`, `capsuleReverseLookupRoute`

**Business logic that moves to `service/capsule/CapsuleService.kt`:**
- `createCapsuleHandler`: validation logic (shape parsing, unlock_at parsing, recipient validation, upload existence check)
- `patchCapsuleHandler`: field validation
- The `CapsuleShape`/`CapsuleState` enum parsing currently inline in handlers

**Representation:**
- `toDetailJson()`, `toSummaryJson()`, `toReverseLookupJson()` → `representation/capsule/CapsuleJson.kt`

**Controller layer decision:** Not needed. `CapsuleService` covers the gap.

---

### KeysHandler.kt (436 lines)

**Routes that stay in `routes/KeysRoutes.kt`:**
`registerDeviceRoute`, `listDevicesRoute`, `retireDeviceRoute`, `touchDeviceRoute`, `getPassphraseRoute`, `putPassphraseRoute`, `deletePassphraseRoute`, `initiateLinkRoute`, `registerLinkRoute`, `linkStatusRoute`, `wrapLinkRoute`

**Business logic that moves to `service/keys/KeyService.kt`:**
- `generateLinkCode()`: code generation
- `registerDevice()`: validation + `insertWrappedKey`
- `registerOnLink()`: state-machine step (code match, pubkey decode)
- `wrapLink()`: state-machine step (wrap_complete, create wrapped_key)
- `putPassphrase()`: passphrase validation + `upsertRecoveryPassphrase`

**Representation:**
- `WrappedKeyRecord.toJson()`, `RecoveryPassphraseRecord.toJson()` → `representation/keys/KeysJson.kt`

**Controller layer decision:** Not needed. Routes call `KeyService`.

---

## 5. Functional Groupings by Domain

### Auth
**Existing files contributing:** `AuthHandler.kt`, `SessionAuthFilter.kt`, `ApiKeyFilter.kt`, `Database.kt` (users, sessions, invites, pairing, wrapped keys for setup-existing)

**New file structure:**
```
domain/auth/UserRecord.kt
repository/auth/AuthRepository.kt
service/auth/AuthService.kt
routes/AuthRoutes.kt
filters/SessionAuthFilter.kt
filters/ApiKeyFilter.kt
```

---

### Uploads
**Existing files contributing:** `UploadHandler.kt`, `Database.kt` (all upload queries), `ThumbnailGenerator.kt`, `MetadataExtractor.kt`, `ExifExtractionService.kt`, `TagValidator.kt`

**New file structure:**
```
domain/upload/UploadRecord.kt
domain/upload/MediaMetadata.kt
domain/upload/TagValidation.kt
repository/upload/UploadRepository.kt
repository/storage/BlobRepository.kt
service/upload/UploadService.kt
service/upload/ExifExtractionService.kt
service/upload/ThumbnailService.kt
service/upload/MetadataService.kt
routes/UploadRoutes.kt
representation/upload/UploadJson.kt
```

---

### Plots
**Existing files contributing:** `PlotHandler.kt`, `FlowHandler.kt` (plot-item routes), `Database.kt` (plots, flows, staging, plot items), `CriteriaEvaluator.kt`

**New file structure:**
```
domain/plot/PlotRecord.kt
domain/plot/FlowRecord.kt
repository/plot/PlotRepository.kt
repository/plot/FlowRepository.kt
repository/plot/PlotItemRepository.kt
service/plot/PlotService.kt
service/plot/FlowService.kt
service/plot/StagingService.kt
service/plot/CriteriaEvaluator.kt
routes/PlotRoutes.kt
routes/FlowRoutes.kt
representation/plot/PlotJson.kt
representation/plot/FlowJson.kt
```

---

### Shared Plots (social collections)
**Existing files contributing:** `SharedPlotHandler.kt`, `Database.kt` (plot members, invites, membership lifecycle)

**New file structure:**
```
domain/plot/PlotRecord.kt         (PlotMemberRecord, SharedMembershipRecord, PlotInviteRecord already here)
repository/plot/PlotMemberRepository.kt
routes/SharedPlotRoutes.kt
```

---

### Capsules
**Existing files contributing:** `CapsuleHandler.kt`, `Database.kt` (capsule queries)

**New file structure:**
```
domain/capsule/CapsuleRecord.kt
domain/capsule/CapsuleConstants.kt
repository/capsule/CapsuleRepository.kt
service/capsule/CapsuleService.kt
routes/CapsuleRoutes.kt
representation/capsule/CapsuleJson.kt
```

---

### Keys / Crypto
**Existing files contributing:** `KeysHandler.kt`, `Database.kt` (wrapped keys, recovery passphrase, device links), `EnvelopeFormat.kt`

**New file structure:**
```
domain/keys/WrappedKeyRecord.kt
domain/keys/RecoveryPassphraseRecord.kt
repository/keys/KeyRepository.kt
service/keys/KeyService.kt
routes/KeysRoutes.kt
representation/keys/KeysJson.kt
crypto/EnvelopeFormat.kt
```

---

### Friendships / Social
**Existing files contributing:** `FriendsHandler.kt`, `SharingKeyHandler.kt`, `Database.kt` (friendships, account sharing keys)

**New file structure:**
```
domain/keys/AccountSharingKeyRecord.kt   (FriendRecord + AccountSharingKeyRecord)
repository/social/SocialRepository.kt
routes/FriendsRoutes.kt
routes/SharingKeyRoutes.kt
representation/sharing/SharingJson.kt
```

---

### Storage
**Existing files contributing:** `FileStore.kt`, `FileStorage.kt`, `GcsFileStore.kt`, `S3FileStore.kt`, `DirectUploadSupport.kt`, `ContentTypeExtensions.kt`

**New file structure:**
```
storage/FileStore.kt
storage/DirectUploadSupport.kt
storage/LocalFileStore.kt
storage/GcsFileStore.kt
storage/S3FileStore.kt
storage/ContentTypeExtensions.kt
```

---

### Config / Infra
**Existing files contributing:** `AppConfig.kt`, `Main.kt`, `CorsFilter.kt`, `ApiKeyFilter.kt`, `SessionAuthFilter.kt`

**New file structure:**
```
config/AppConfig.kt
filters/CorsFilter.kt
filters/ApiKeyFilter.kt
filters/SessionAuthFilter.kt
Main.kt                       (entry point + buildApp)
```

---

## 6. Interface Boundaries

### Repository interfaces

Each repository should be defined as an interface with a Postgres implementation. The primary motivation is testability: integration tests can substitute an in-memory fake or a test-container-backed implementation without changing the service under test.

**Pattern:**
```kotlin
// repository/upload/UploadRepository.kt
interface UploadRepository {
    fun recordUpload(record: UploadRecord, userId: UUID)
    fun findUploadByIdForUser(id: UUID, userId: UUID): UploadRecord?
    fun listUploadsPaginated(...): UploadPage
    // ...
}

// repository/upload/PostgresUploadRepository.kt
class PostgresUploadRepository(private val dataSource: DataSource) : UploadRepository {
    override fun recordUpload(...) { /* SQL */ }
    // ...
}
```

Apply this pattern to: `UploadRepository`, `PlotRepository`, `FlowRepository`, `PlotItemRepository`, `PlotMemberRepository`, `CapsuleRepository`, `AuthRepository`, `KeyRepository`, `SocialRepository`, `BlobRepository`, `DiagRepository`.

**Note on `withTransaction`:** Transactions that span multiple repositories (e.g., creating a plot + inserting a member atomically) are a challenge. Two options:

1. Accept a `Connection` parameter on methods that need to participate in a caller-managed transaction (leaks JDBC into interfaces — not ideal).
2. Pass a `TransactionScope` / `UnitOfWork` abstraction. This is cleaner but adds complexity.

**Recommendation for this codebase at this scale:** Keep `withTransaction` as an internal utility in `DatabaseInfra.kt` and allow repositories to declare their own transaction-local methods. Services that need cross-repository transactions receive a `DataSource` directly or use a `TransactionManager` wrapper. Do not model this as interfaces initially — add it when the first real cross-repository transaction test requirement appears.

---

### Storage interface

`FileStore` is already an interface. No change to the interface contract is needed. `DirectUploadSupport` is also already an interface.

**Recommended clarification:** `GcsFileStore` currently implements both `FileStore` and `DirectUploadSupport`. This is correct. In `Main.kt`, the cast `val directUpload = storage as? DirectUploadSupport` is appropriate and should remain.

---

### Service interfaces

Service interfaces are **not recommended** at this stage. The services in this codebase do not have multiple implementations and are not injected across package boundaries in ways that require substitution. Adding service interfaces adds boilerplate with no current benefit.

Exception: if unit testing services with mocked repositories is desired, the repository interfaces described above are sufficient. Services can be tested by passing mock `UploadRepository` etc. without needing service interfaces.

---

### CriteriaEvaluator

`CriteriaEvaluator` currently takes a `Connection` directly. This is a pragmatic choice — the evaluator issues live SQL to validate `plot_ref` references. It should not become an interface; instead, keep it as a concrete object in `service/plot/`. The service layer is responsible for obtaining a connection and passing it through.

---

## 7. Migration Sequence

The following order minimises risk. Each step can be independently committed, compiled, and tested before the next begins.

### Phase 1: Create domain data classes (safest — no logic)

1. Create `domain/` sub-packages and move data classes out of `Database.kt` one domain at a time. Start with `domain/upload/UploadRecord.kt` — it is referenced most widely.
2. Add `typealias` stubs in `Database.kt` if needed to keep existing references compiling (remove them after handlers are updated).
3. Move `domain/auth/`, `domain/plot/`, `domain/capsule/`, `domain/keys/`.
4. Run the full test suite. No behaviour has changed — only types moved.

### Phase 2: Extract storage layer

5. Move `FileStore.kt`, `FileStorage.kt`, `GcsFileStore.kt`, `S3FileStore.kt`, `DirectUploadSupport.kt`, `ContentTypeExtensions.kt` into `storage/`. Update `package` declarations.
6. Run tests.

### Phase 3: Extract config and filters

7. Move `AppConfig.kt` → `config/`. Move `SessionAuthFilter.kt`, `ApiKeyFilter.kt`, `CorsFilter.kt` → `filters/`.
8. Run tests.

### Phase 4: Extract crypto and standalone utilities

9. Move `EnvelopeFormat.kt` → `crypto/`. Move `CriteriaEvaluator.kt` → `service/plot/`. Move `TagValidator.kt` → `domain/upload/TagValidation.kt`. Move `ThumbnailGenerator.kt` → `service/upload/ThumbnailService.kt`. Move `MetadataExtractor.kt` → `service/upload/MetadataService.kt`.
10. Run tests.

### Phase 5: Split Database.kt into repositories — one group at a time

Split order: start with the groups that are called from the fewest other groups to minimise refactor surface at each step.

11. **DiagRepository** — called only from `UploadHandler.kt` diagnostics routes. Tiny, self-contained.
12. **BlobRepository** — called only from `UploadHandler.kt` and `PendingBlobsCleanupService`.
13. **SocialRepository** — called from `FriendsHandler`, `SharingKeyHandler`, `UploadHandler` (share upload).
14. **AuthRepository** — called from `AuthHandler`, `SessionAuthFilter`, `KeysHandler` (setup-existing).
15. **KeyRepository** — called from `KeysHandler`.
16. **CapsuleRepository** — called from `CapsuleHandler`, `UploadHandler` (reverse lookup).
17. **PlotRepository** — called from `PlotHandler`, `FlowHandler`, `SharedPlotHandler`, `UploadHandler` (listUploadsPaginated).
18. **FlowRepository + PlotItemRepository + PlotMemberRepository** — called from `FlowHandler`, `SharedPlotHandler`.
19. **UploadRepository** — called from almost every handler; do this last so earlier steps have stabilised other repositories first.

For each repository group:
- Create the new repository class with the extracted SQL.
- Update `Database.kt` to delegate to the new repository (shim approach) until handlers are updated.
- Update handlers to inject and call the new repository directly.
- Delete the delegation shim from `Database.kt`.
- Run the full test suite after each group.

At the end of Phase 5, `Database.kt` contains only `runMigrations`, `withTransaction`, and the `create()` factory. Rename/move to `repository/DatabaseInfra.kt`.

### Phase 6: Extract services

20. Extract `ExifExtractionService`, `PendingBlobsCleanupService` (already classes — just move and repackage).
21. Create `UploadService` by moving orchestration logic out of `UploadHandler.kt`. Route handlers become thin: parse → call service → format response.
22. Create `AuthService`, `KeyService`, `CapsuleService`, `PlotService`, `FlowService`, `StagingService` in the same pattern.
23. Run tests after each service extraction.

### Phase 7: Extract representations

24. Move all `toJson()` extension functions to `representation/` sub-packages.
25. Move `buildApp` to `Main.kt` or `AppRouter.kt`.
26. Delete empty handler files.
27. Run full test suite.

### Phase 8: Add repository interfaces (optional — recommended for testability)

28. For each repository, extract an interface and rename the concrete class `Postgres*Repository`.
29. Update service constructors to accept the interface type.
30. Update `Main.kt` wiring.
31. Run tests.

---

## 8. Risks and Trade-offs

### Risks

**Circular dependencies.** In Kotlin (JVM), circular dependencies between packages compile but are a design smell. The primary risk is `service/plot/FlowService` calling `repository/plot/PlotRepository` and `repository/plot/FlowRepository`, while `PlotRepository` may need `CriteriaEvaluator` (in `service/plot/`). Solution: `CriteriaEvaluator` depends only on `domain/` types and a raw `Connection` — it has no dependency on `repository/`. This direction is safe.

**`withTransaction` across multiple repositories.** As noted in §6, some operations (`createFlow` auto-population, `confirmInvite`, `completeDeviceLink`) need multiple tables to change atomically. The current `Database.kt` handles this by keeping everything in one class. After the split, these transactions must be handled in service methods that acquire a connection and pass it into both repositories, or by a `TransactionManager`. This is the highest-complexity aspect of the migration. Recommendation: keep these multi-table operations as single repository methods on the "primary" repository (e.g., `completeDeviceLink` stays in `KeyRepository` even though it also writes `wrapped_keys`) and document the coupling clearly.

**IntelliJ refactoring pitfalls.** Moving Kotlin files with `internal` visibility will expose previously-hidden symbols to the whole module if the `internal` boundary was implicitly the package. This codebase uses `private` consistently for intra-file helpers and `internal` rarely, so the risk is low. Verify with `Find Usages` before each move.

**Test infrastructure.** Integration tests that construct `Database` directly (common pattern: `Database.create(testConfig)`) will need updating as repositories are extracted. Tests should be updated to construct only the repository under test, not the full `Database`. This is actually an improvement but requires test edits.

**Merge conflict risk.** This is a large structural change. If multiple milestones are in flight simultaneously, a branch targeting a handler file will conflict with the refactor branch. Mitigation: do the refactor in a dedicated branch merged as a single release, not interleaved with feature work.

**Kotlin visibility vs Java packages.** http4k uses Kotlin `internal` and some reflection. There are no known http4k constraints that require all routes to be in a single package, but verify that `contract { routes += ... }` wiring in `UploadHandler.kt` (which currently relies on package-level `private` functions) continues to compile when those functions are split across route files.

---

### What the refactor delivers

- **Navigation.** Finding "the SQL for plot membership" is a multi-second search today; after the split it is one file open.
- **Parallel development.** Two engineers can work on `FlowService` and `CapsuleRepository` without conflicts.
- **Smaller PRs.** A feature touching uploads no longer requires reading 3849 lines to understand its context.
- **Clearer ownership.** Domain model, persistence, business logic, HTTP wiring, and serialisation are no longer interleaved.
- **Testability.** Repository interfaces make unit tests of service logic possible without a running Postgres instance.

---

### What it costs

- **One-time churn.** Approximately 120–150 file moves and repackages across the codebase and its tests. No behaviour changes.
- **Merge conflict window.** The migration branch will be large. Feature work should pause or be rebased once after the migration merges.
- **Learning curve.** The team must learn the new structure. The package tree in §2 should be posted somewhere visible (e.g., a brief or CLAUDE.md) for the first few weeks.

---

### http4k-specific notes

- http4k's `contract { }` DSL builds routes from `ContractRoute` values. These can be created anywhere — there is no requirement that route definitions live in the same file as the `contract { }` block. Moving route factory functions to `routes/` and importing them into `Main.kt` or `AppRouter.kt` is fully supported.
- http4k `Filter` objects are plain functions and have no package constraints.
- The `Request.authUserId()` extension function (currently in `SessionAuthFilter.kt`) is used in every handler. It must remain importable from all route files. Moving it to `filters/SessionAuthFilter.kt` is fine — routes simply import it.
- http4k's `Path` lenses and `bindContract` are purely functional and carry no package-level visibility requirements.
