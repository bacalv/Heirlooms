# Coverage Baseline — 14 May 2026

**Run:** `cd HeirloomsTest && ./gradlew coverageTest --no-daemon`  
**Mode:** in-process (Netty :18080 + Testcontainers Postgres + MinIO)  
**Source:** pre-refactor flat package `digital.heirlooms.server`

---

## Overall

| Counter     | Coverage  | Covered | Total  |
|---|---|---|---|
| INSTRUCTION | **52.3%** | 24,998  | 47,840 |
| LINE        | 58.1%     | 3,297   | 5,677  |
| METHOD      | 63.6%     | 735     | 1,155  |
| CLASS       | 70.8%     | 318     | 449    |
| BRANCH      | 33.3%     | 1,155   | 3,465  |

---

## By class (instruction coverage, top-level only)

| Class | % | Covered | Missed |
|---|---|---|---|
| `GcsFileStore` | 0.0% | 0 | 466 |
| `PendingBlobsCleanupService` | 0.0% | 0 | 208 |
| `ExifExtractionService` | 0.0% | 0 | 181 |
| `LocalFileStore` | 0.0% | 0 | 105 |
| `UserRecord` | 0.0% | 0 | 51 |
| `CreateCapsuleRequest` | 0.0% | 0 | 66 |
| `UserSessionRecord` | 0.0% | 0 | 66 |
| `InviteRecord` | 0.0% | 0 | 60 |
| `RecoveryPassphraseRecord` | 0.0% | 0 | 57 |
| `ParsedAsymmetricEnvelope` | 0.0% | 0 | 54 |
| `PatchCapsuleRequest` | 0.0% | 0 | 54 |
| `PlotItemRecord` | 0.0% | 0 | 54 |
| `AccountSharingKeyRecord` | 0.0% | 0 | 39 |
| `FriendRecord` | 0.0% | 0 | 30 |
| `DecodedCursor` | 0.0% | 0 | 27 |
| `ApiKeyFilterKt` | 0.0% | 0 | 19 |
| `CriteriaCycleException` | 0.0% | 0 | 7 |
| `MetadataExtractor` | 18.1% | 160 | 723 |
| `CriteriaEvaluator` | 24.6% | 212 | 651 |
| `EnvelopeFormat` | 31.2% | 135 | 297 |
| `ThumbnailGeneratorKt` | 37.1% | 186 | 315 |
| `SharingKeyHandlerKt` | 41.5% | 85 | 120 |
| `AuthHandlerKt` | 46.7% | 237 | 271 |
| `Database` | 48.0% | 7,509 | 8,151 |
| `UploadHandlerKt` | 52.3% | 2,138 | 1,947 |
| `S3FileStore` | 54.8% | 184 | 152 |
| `SharedPlotHandlerKt` | 55.2% | 905 | 734 |
| `PlotHandlerKt` | 61.0% | 398 | 254 |
| `KeysHandlerKt` | 65.6% | 1,006 | 527 |
| `FlowHandlerKt` | 72.4% | 1,244 | 474 |
| `CapsuleHandlerKt` | 84.8% | 939 | 168 |
| `SessionAuthFilterKt` | 85.0% | 113 | 20 |
| `ContentTypeExtensionsKt` | 86.4% | 153 | 24 |
| `PendingDeviceLinkRecord` | 90.2% | 129 | 14 |
| `PlotRecord` | 90.9% | 140 | 14 |
| `AlgorithmIds` | 91.1% | 41 | 4 |
| `FlowRecord` | 95.8% | 69 | 3 |
| `CapsuleRecord` | 96.2% | 75 | 3 |
| `UploadRecord` | 97.1% | 432 | 13 |

---

## Zero-coverage classes — reasons

| Class | Reason |
|---|---|
| `GcsFileStore` | Tests use S3/MinIO; GCS path never exercised |
| `LocalFileStore` | Tests use S3/MinIO; local path never exercised |
| `ExifExtractionService` | Background startup-recovery service; not triggered in test run |
| `PendingBlobsCleanupService` | Background periodic cleanup; timer never fires in tests |
| `UserRecord`, `InviteRecord` etc. | Multi-user auth records; invite/register/session flows under-tested |
| `ApiKeyFilterKt` | Tests use session token auth, not static API key filter |

---

## HTML report location

`HeirloomsTest/build/reports/jacoco/html/index.html`

---

## Next step

After phases 1–4 server refactor lands, run `coverageTest` again and compare to this baseline.  
90% gate is configured in `jacocoCoverageVerify` — will be enforced once the refactor is complete.
