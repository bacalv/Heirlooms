# Phase 6 Changes — Representation Layer Extraction

**Date:** 2026-05-14  
**Type:** Pure code reorganisation — no behaviour changes

---

## What changed

### New files created

| File | Package | Source of extracted code |
|---|---|---|
| `UploadRepresentation.kt` | `representation/upload/` | `UploadRecord.toJson()` from `Database.kt`; `UploadPage.toJson()` from `UploadHandler.kt` |
| `CapsuleRepresentation.kt` | `representation/capsule/` | `toDetailJson()`, `toSummaryJson()`, `toReverseLookupJson()` from `CapsuleHandler.kt` |
| `PlotRepresentation.kt` | `representation/plot/` | `PlotRecord.toJson()` from `PlotHandler.kt` |
| `FlowRepresentation.kt` | `representation/plot/` | `FlowRecord.toJson()`, `PlotItemWithUpload.toJson()` from `FlowHandler.kt` |
| `SharedPlotRepresentation.kt` | `representation/plot/` | Inline JSON building extracted from `SharedPlotHandler.kt`: member list, shared memberships, plot key response, invite response, join info, pending join response |
| `AuthRepresentation.kt` | `representation/auth/` | `sessionTokenJson()` from `AuthHandler.kt`; challenge, invite, pairing initiate, pairing complete response builders extracted from inline code |
| `KeysRepresentation.kt` | `representation/keys/` | `WrappedKeyRecord.toJson()`, `RecoveryPassphraseRecord.toJson()` from `KeysHandler.kt` |
| `SocialRepresentation.kt` | `representation/social/` | `AccountSharingKeyRecord.toJson()` from `SharingKeyHandler.kt`; `List<FriendRecord>.toFriendsJson()` extracted from `FriendsHandler.kt`; `friendSharingKeyResponseJson()` from `SharingKeyHandler.kt` |

### Modified files

**Handler files** — local serialisation functions removed, replaced by imports from representation packages:
- `UploadHandler.kt`: removed `UploadPage.toJson()`, added `import representation.upload.toJson`
- `CapsuleHandler.kt`: removed 3 toJson functions, added 3 representation imports, removed unused `CapsuleDetail`, `CapsuleSummary` imports
- `PlotHandler.kt`: removed `PlotRecord.toJson()`, added representation import, removed unused `JsonNodeFactory` import
- `FlowHandler.kt`: removed `FlowRecord.toJson()` and `PlotItemWithUpload.toJson()`, added representation imports (both plot + upload for staging item lists), removed unused `JsonNode` and `JsonNodeFactory` imports
- `SharedPlotHandler.kt`: replaced 5 inline JSON-building blocks with calls to representation functions, removed unused `JsonNodeFactory` import
- `AuthHandler.kt`: removed `sessionTokenJson()`, replaced 4 inline JSON-building blocks, removed unused `JsonNodeFactory`, `Instant`, `Base64` imports
- `KeysHandler.kt`: removed 2 toJson functions, added representation import
- `SharingKeyHandler.kt`: removed `AccountSharingKeyRecord.toJson()`, replaced inline friend-key response, removed unused `JsonNodeFactory`, `sharingEnc`, `Base64` imports
- `FriendsHandler.kt`: replaced inline friends-array building with `friends.toFriendsJson()`, removed unused `ObjectMapper` import and `friendsMapper` variable

**`Database.kt`** — `UploadRecord.toJson()` extension removed (replaced by comment pointing to representation package)

**Test file:**
- `CapsuleHandlerTest.kt`: added imports for representation functions that moved from handler to representation package

---

## Design decisions

1. **All functions are top-level extension functions**, matching the existing pattern in the codebase.
2. **SharedPlotHandler had no dedicated serialisation functions** — all JSON was inline. Rather than leave them inline, each response shape became a named function in `SharedPlotRepresentation.kt`. This makes the shapes visible and reusable.
3. **AuthRepresentation extracts response shapes, not request parsing** — request parsing (ObjectMapper.readTree, field extraction) stays in the handler because it is HTTP-layer logic.
4. **Import ambiguity (`toJson` in both `representation.upload` and `representation.plot`)** — Kotlin resolves these correctly by receiver type. Both imports coexist in `FlowHandler.kt` without conflict.
5. **No new behaviour** — every field and every null-handling branch is preserved exactly.

---

## Build results

- `./gradlew clean shadowJar`: BUILD SUCCESSFUL
- `./gradlew test` (unit tests): BUILD SUCCESSFUL
- `./gradlew coverageTest` (integration tests): BUILD SUCCESSFUL

**Coverage (no regression):**
- INSTRUCTION: 51.7% (baseline 51.7%)
- LINE: 56.8% (baseline 56.9%)
- METHOD: 57.3% (baseline 57.3%)
- CLASS: 67.0% (baseline 66.8%)
- BRANCH: 33.8% (baseline 33.8%)
