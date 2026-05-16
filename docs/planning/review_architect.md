# Architect Queue Review — v0.54 Iteration

**Reviewer:** Technical Architect
**Date:** 2026-05-16
**Scope:** All outstanding queue tasks. BUG-017 and BUG-018 excluded (done).

---

## Executive Summary

Of the 18 tasks reviewed, two require an architecture brief before development starts (ARCH-007 and FEAT-003). Several others carry significant cross-cutting concerns that developers will hit without warning. The most important finding is that the `user_sessions` table lacks a `device_id` foreign key, which constrains SEC-011's session-invalidation requirement to a `device_kind`-only strategy — a design gap the developer must know before starting. ARCH-007 is fully resolved in this document; FEAT-003's recommended path (Option A) is unblocked and safe to implement immediately.

---

## Per-Task Assessment

---

### BUG-022 — Web detail view blank for shared plot items (High, ~2h)

**Brief needed:** No. Root cause and fix are correctly identified.

**Architectural assessment:**

The bug is confirmed by code inspection. `PhotoDetailPage.jsx` line 20 defines a local `unwrapDek` function that handles only `ALG_P256_ECDH_HKDF_V1` (sharing key) and falls through to `unwrapDekWithMasterKey` for everything else. It does not import or call `unwrapDekWithPlotKey`. By contrast, `UploadThumb.jsx` correctly imports `unwrapPlotKey` and `unwrapDekWithPlotKey` and handles `ALG_PLOT_AES256GCM_V1` as a named branch.

**Plot key loading consistency across platforms:**

- Android (`PhotoDetailViewModel`): Uses `VaultSession.plotKeys` (a `ConcurrentHashMap`). The detail view does a `firstOrNull` scan to find the right plot key by trying to unwrap the DEK — this works but is O(n) in the number of loaded plot keys. Plot keys are loaded lazily when a shared plot is opened. If the user navigates directly to a detail view without opening the plot first, the key may not be loaded. This is a latent bug on Android too, worth tracking.
- Web (`UploadThumb.jsx`): Loads the plot key on demand via `loadPlotKey(plotId, apiKey)`, calling `/api/plots/{plotId}/members/me` to get the wrapped key and then decrypting it. This pattern must be replicated in `PhotoDetailPage.jsx`.
- iOS: Not inspected, but the M11 iOS brief exists. Status of shared plot E2EE support on iOS is unknown and should be verified before the smoke test step 5 is declared passing on iOS.

**Cross-platform concern flagged:** The plot key loading pattern is not a named, shared utility on web — it is duplicated between `UploadThumb.jsx` and will need to be duplicated again in `PhotoDetailPage.jsx`. This is acceptable for now but should be extracted into a single `ensurePlotKey(plotId)` helper in `vaultSession.js` when time permits (not in scope for this bug fix).

**Developer notes:** The fix is straightforward — import the plot key functions into `PhotoDetailPage.jsx`, replicate the `UploadThumb` loading pattern, and add `ALG_PLOT_AES256GCM_V1` as a branch in the local `unwrapDek` function. No server changes needed.

---

### TST-008 — Shared plot E2E smoke test spec + automation strategy (High, ~half day)

**Brief needed:** No. Blocked on ARCH-007 only for Step 6.

**Architectural assessment:**

The task file is well-structured. The automation strategy table is correct. The dependency on ARCH-007 for Step 6 is accurate — do not implement Step 6 auto-tagging before ARCH-007 is approved and briefed.

One architectural note for the TestManager: the smoke test's Step 4 tests the staging approval path (BUG-020 is the auto-approve path). Even after BUG-022 is fixed, the correct sequencing for automation is:

1. Fix BUG-022 first (web detail decryption) — Steps 4–5 cannot pass otherwise.
2. BUG-020 (auto-approve) is a separate improvement; the smoke test can pass via the staging path in the interim.

**Test data strategy recommendation:** Use dynamic account creation per run (fresh invite tokens via API key). Fixed usernames in a shared staging environment create state leakage between runs and will cause the BUG-019 duplicate device_id error to recur.

---

### ARCH-007 — E2EE tag scheme: HMAC tokens + encrypted display names (High, ~1 day design)

**Brief needed:** Yes — this is the design task. Full resolution below.

#### Current state (from code inspection)

Tags are stored as `TEXT[]` on `uploads.tags` with a GIN index (`idx_uploads_tags`). The `CriteriaEvaluator` handles `"tag"` atoms by generating `tags @> ARRAY[?]::text[]` SQL — a direct plaintext string comparison. There is no `member_tags` table. The criteria JSON format currently stores `{"type": "tag", "tag": "<plaintext_value>"}`.

The transition to token-based criteria is a breaking schema change that affects: the `uploads.tags` column, the `CriteriaEvaluator`, trellis criteria stored in the `trellises.criteria` JSONB column, and all four client platforms.

#### Resolved design decisions

**Question 1: Display name ciphertext — per-tag globally (deduped) or per (user, upload) tuple?**

**Decision: Per (user, upload) tuple, stored in a separate `user_upload_tags` table.**

Rationale: Deduplication into a global `user_tags` table (one ciphertext per user per tag) initially appears more space-efficient, but introduces two problems. First, it requires a join table regardless — you still need `(user_id, upload_id, tag_id)` to associate a tag with a specific upload. Second, deduplication provides no privacy benefit because the token itself already acts as the deduplication key; having the ciphertext in one place versus many does not reduce leakage (the server can already identify co-occurrence from the tokens alone). Third, deduplication complicates deletion: deleting a tag from one upload requires checking whether any other upload still references the same tag before deleting the global display name row — this is a race condition under concurrent modifications.

Storing `(user_id, upload_id, tag_token, tag_display_ciphertext)` per tuple is simpler, correct, and avoids a class of referential integrity bugs. The marginal storage overhead is negligible: a tag display ciphertext is at most ~100 bytes (a short name encrypted under AES-GCM with a 12-byte nonce and 16-byte tag).

**Question 2: Migration UX — "re-encrypting your tags" progress step on next login?**

**Decision: Silent background migration per session, no blocking progress step. Explicit acknowledgement only if the user has more than 500 tagged items.**

Rationale: The migration requires the client to, for each upload, hash the existing plaintext tag with HMAC-SHA256 to produce the token, encrypt the display name with the display key, and send both to the server. For a typical vault of tens to hundreds of items, this completes in seconds. Showing a progress step for a trivially fast operation is worse UX than doing it silently. However, users with large vaults (thousands of tagged items) should see a brief "Securing your tags" non-blocking indicator to explain any perceived latency.

The server migration path: add the new `user_upload_tags` table alongside the existing `uploads.tags` column. During the migration window, both exist. A client that has migrated sends tokens; a client that has not yet migrated sends plaintext tags. The server must accept both during the migration window. After a migration deadline (one release cycle), the server can reject plaintext tags and drop `uploads.tags`.

On first login after the client update, the client fetches all uploads, tokenises existing tags, writes to `user_upload_tags`, and clears `uploads.tags` entries for that user. This is idempotent — re-running it is safe.

**Question 3: Should the tag token scheme version be stored alongside the token?**

**Decision: Yes. Store a `tag_scheme_version` column (or a scheme prefix on the token itself).**

Rationale: HMAC keys derived from the master key via HKDF are version-stable as long as the master key and the HKDF context string do not change. However, if the master key is ever rotated (a future feature) or if the HKDF context string needs to change (e.g. if a weakness is found in the current scheme), all tokens must be re-derived. Without a version field, there is no way to identify which tokens need rotation. The cost of storing a single-byte version is negligible; the cost of retrofitting versioning later is high. Store `tag_scheme_version = 1` alongside each token.

#### Full schema design

```sql
-- V32: E2EE tag scheme
-- Phase 1: add new tables, keep uploads.tags for migration window.

CREATE TABLE user_upload_tags (
    id                    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    upload_id             UUID        NOT NULL REFERENCES uploads(id) ON DELETE CASCADE,
    tag_token             BYTEA       NOT NULL,   -- HMAC-SHA256(HKDF(mk, "tag-token-v1"), tag_utf8) — 32 bytes
    tag_display           BYTEA       NOT NULL,   -- AES-GCM envelope (aes256gcm-v1) of tag_utf8 bytes
    tag_scheme_version    SMALLINT    NOT NULL DEFAULT 1,
    is_auto               BOOLEAN     NOT NULL DEFAULT FALSE,  -- auto-applied tags use auto-tag-token-v1 HKDF context
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, upload_id, tag_token)
);

CREATE INDEX idx_user_upload_tags_user       ON user_upload_tags(user_id);
CREATE INDEX idx_user_upload_tags_upload     ON user_upload_tags(upload_id);
CREATE INDEX idx_user_upload_tags_token      ON user_upload_tags(user_id, tag_token);
```

Notes:
- `tag_token` is `BYTEA` (32 raw bytes), not a hex string, for index efficiency.
- `is_auto` distinguishes auto-applied tags from user-applied tags without needing a separate table. The `CriteriaEvaluator` `tag` atom matches only rows where `is_auto = FALSE`.
- The `UNIQUE (user_id, upload_id, tag_token)` constraint prevents duplicate tags on the same upload.

#### Key derivation spec

All HKDF operations use HKDF-SHA256. The master key is the IKM. Salt is empty (HKDF default). Context strings are the `info` parameter.

| Purpose | HKDF context string | Output length | Usage |
|---|---|---|---|
| User-applied tag token | `"tag-token-v1"` | 32 bytes | HMAC-SHA256 key; then HMAC(key, tag_utf8) = 32-byte token |
| Auto-applied tag token | `"auto-tag-token-v1"` | 32 bytes | HMAC-SHA256 key; then HMAC(key, tag_utf8) = 32-byte token |
| Tag display encryption | `"tag-display-v1"` | 32 bytes | AES-256-GCM key; encrypt tag_utf8 as `aes256gcm-v1` envelope |

The tag display ciphertext is a standard `aes256gcm-v1` symmetric envelope (see `docs/envelope_format.md`). This reuses the existing envelope format and codec on all platforms with no new format work.

#### Trellis criteria format change

Current: `{"type": "tag", "tag": "<plaintext_value>"}`
New: `{"type": "tag", "token": "<base64url_32_bytes>", "scheme_version": 1}`

The `CriteriaEvaluator` `tag` branch changes from:
```kotlin
"tags @> ARRAY[?]::text[]"  -- current
```
to:
```kotlin
"EXISTS (SELECT 1 FROM user_upload_tags uut WHERE uut.upload_id = uploads.id AND uut.user_id = ? AND uut.tag_token = ? AND uut.is_auto = FALSE AND uut.tag_scheme_version = ?)"
```

This is a correlated subquery, not a GIN array check. The existing `idx_uploads_tags` GIN index becomes unused for tag criteria; the new `idx_user_upload_tags_token` index on `(user_id, tag_token)` handles the lookup. Performance is comparable for typical vault sizes.

#### Per-member tags on shared plot items

The `user_upload_tags` table already handles this correctly. User A can insert rows for `upload_id` owned by User B (a shared plot item). The `user_id` on the tag row scopes it to User A. User B's tags on the same upload have User B's `user_id`. Neither user sees the other's tags. Heirlooms staff see only tokens.

The server must enforce that a user can only insert tags where the `upload_id` is accessible to them (i.e. they own the upload or they are a member of a shared plot containing that upload). This is an authorisation check on the tag-write endpoint, not a schema constraint.

#### Auto-tagging loop prevention

The `is_auto = TRUE` flag and the separate HKDF context (`"auto-tag-token-v1"`) work together. The `CriteriaEvaluator` `tag` atom generates SQL filtering `is_auto = FALSE`. A trellis criterion of `{"type": "tag", "token": "..."}` will only match user-applied tags. Auto-applied tags (from Step 6 of the smoke test) cannot re-trigger trellises because they produce tokens under a different HKDF context — even if a user were to manually apply the same semantic tag value, their manual token (under `"tag-token-v1"`) would be different from the auto-tag token (under `"auto-tag-token-v1"`).

#### Platform implementation order

1. Server: V32 migration, new `user_upload_tags` repository, updated tag-write endpoint, updated `CriteriaEvaluator`. Accept plaintext tags in parallel during migration window.
2. Android: HKDF derivation (BouncyCastle already present), HMAC-SHA256, migration loop on first login post-update.
3. Web: HKDF via Web Crypto API (`deriveKey` with `HKDF` algorithm), HMAC-SHA256 via `crypto.subtle.sign`, migration loop.
4. iOS: CryptoKit HKDF (`HKDF<SHA256>.deriveKey`), HMAC via `HMAC<SHA256>`, migration loop.

iOS is lowest priority given the current milestone focus.

#### Deliverables for this brief

This document constitutes the design resolution. The developer implementing ARCH-007 should produce:
1. `docs/tag_scheme.md` — formal spec (distilled from this section)
2. V32 Flyway migration
3. Updated `CriteriaEvaluator` and tag repository
4. Cross-platform HKDF derivation reference (to be shared with Android, Web, iOS developers)
5. Migration idempotency test

---

### SEC-012 — Tag metadata leakage doc (Medium, ~2-3h)

**Brief needed:** No. Documentation task, unblocked after ARCH-007 is resolved above.

**Architectural assessment:**

The threat model in the task file is accurate. The HMAC token scheme eliminates semantic leakage but preserves structural metadata (frequency, co-occurrence, equality). The proposed disclosure wording is appropriate. One addition: the disclosure should note that the isolation is per-user — cross-user token correlation is cryptographically impossible because tokens are keyed to per-user master keys. This is a meaningful privacy property that distinguishes Heirlooms from systems where all users share a common token space.

No architectural concerns. Depends on ARCH-007; do not publish the disclosure until the scheme is implemented.

---

### BUG-021 — Video duration extraction (Medium, ~2-3h)

**Brief needed:** No. The fix path is clear from the code.

**Architectural assessment (from code inspection):**

`ThumbnailGenerator.kt` already contains a working `extractVideoDuration` function (lines 112–138) that calls `ffprobe` via `ProcessBuilder`. FFmpeg is confirmed present in the Cloud Run container (it is already used by `extractVideoThumbnail` on lines 81–109 via `ffmpeg`). The function is implemented and correct.

The gap is in `UploadService` — `extractVideoDuration` exists but is not being called, or its result is not being stored. The `uploads` table schema needs inspection for a `duration_seconds` column.

**Schema check needed:** The developer must verify whether `uploads.duration_seconds` (or equivalent) exists. If not, a new Flyway migration is required. The column should be `INTEGER NULL` (whole seconds, nullable for images and failed extractions).

**Cloud Run concern resolved:** ffmpeg and ffprobe are both available (confirmed by the existing thumbnail generation code). No infrastructure change needed.

**Cross-platform note:** Both Android and web read and display the duration field from the upload API response. The fix is server-side only — add the column, call `extractVideoDuration`, store the result. Client display code already handles this field (or handles zero gracefully enough that the fix is purely additive).

---

### BUG-020 — Shared plot auto-approve: client-side DEK re-wrap (Medium, ~3-4h)

**Brief needed:** No, but the API contract needs clarification for the developer.

**Architectural assessment:**

The design in the task file is correct. The server cannot hold plot keys in plaintext (that is the E2EE invariant), so client-side re-wrap before submission is the only valid path for auto-approval.

**API contract clarification:** The task says to add optional parameters to the "tag-update or trellis-route endpoint." The developer needs to know which endpoint. From the code, trellis routing happens in `TrellisRepository.routeUploadToPlots` after a tag update. The cleanest extension is to add optional fields to the PATCH `/api/uploads/{id}/tags` request body:

```json
{
  "tags": ["smoke-test-tag"],
  "plotDekWraps": [
    {
      "plotId": "<uuid>",
      "wrappedItemDek": "<base64>",
      "itemDekFormat": "plot-aes256gcm-v1",
      "wrappedThumbnailDek": "<base64>",
      "thumbnailDekFormat": "plot-aes256gcm-v1"
    }
  ]
}
```

The server, on receiving a tag update that triggers a trellis route into a shared plot, checks for a matching `plotId` entry in `plotDekWraps`. If found, it uses the provided wrapped DEKs to insert directly into `plot_items`. If not found, it falls back to staging.

**Validation requirements server-side:** The server must validate that:
1. The `plotId` in `plotDekWraps` matches an actual shared plot that the upload's trellis targets.
2. The calling user is a member of that plot (otherwise they should not have the plot key).
3. The `itemDekFormat` is `plot-aes256gcm-v1` (no other format is valid here).
4. The wrapped DEK bytes are structurally valid envelopes (call `EnvelopeFormat.validate`).

**Coupling concern:** This extends the tag-update endpoint with DEK re-wrap payload, creating a coupling between the tagging flow and the E2EE key management flow. This is the same coupling that the staging approval panel has — it is unavoidable given the E2EE model. The task file acknowledges this correctly.

---

### FEAT-004 — Android invite friend (Medium, ~2-3h)

**Brief needed:** No. Architecturally trivial.

**Architectural assessment:**

The existing `POST /api/auth/invites` endpoint exists and is used by the web client. Android simply needs a UI button on the Friends screen that calls this endpoint and feeds the resulting token into an Android share sheet. No new server endpoints, no E2EE implications. The task is correctly scoped.

---

### SEC-011 — Device revocation (Medium, ~half day)

**Brief needed:** No, but there is a significant schema gap the developer must know.

**Architectural assessment (from code inspection):**

`wrapped_keys` has the correct columns for deletion by `(user_id, device_id)`: both `user_id` and `device_id` are indexed, and a `DELETE WHERE device_id = ? AND user_id = ?` query is straightforward.

**Critical gap — session invalidation by device_id:** The `user_sessions` table schema (V20) stores `device_kind` (android/web/ios) but does NOT store `device_id`. There is no foreign key from `user_sessions` to `wrapped_keys`. The `createSession` function signature is `createSession(userId, tokenHash, deviceKind)` — `device_id` is not passed.

This means the requirement "invalidate all sessions for that device_id" cannot be implemented as written without a schema change. The options are:

**Option A (recommended, requires migration):** Add `device_id TEXT NULL REFERENCES wrapped_keys(device_id)` to `user_sessions`. Backfill is not possible for existing sessions (the device_id was never recorded), so backfilled rows would be NULL. After migration, revocation deletes the `wrapped_keys` row and deletes sessions WHERE `device_id = ?`. Existing sessions with NULL `device_id` remain (acceptable — they will expire naturally or on the next login attempt, which will fail because the wrapped key is gone).

**Option B (no migration, weaker guarantee):** On revocation, delete the `wrapped_keys` row. The stale session token, if used, will fail at the next request that requires fetching the wrapped key — but it will NOT fail immediately at the `SessionAuthFilter` because session validity is checked by token hash alone, not by the presence of a `wrapped_keys` row. This means a revoked device's session remains technically valid for authentication until it expires (90 days), even though the device can no longer load the master key. For most threat scenarios (lost/sold device), this is acceptable — the attacker cannot decrypt vault content without the Keystore private key anyway. But it leaves the attack surface open at the session layer.

**Recommendation:** Implement Option A (migration). The schema is young enough that adding `device_id` to `user_sessions` is low risk. The developer should add V32 (or the next version number after ARCH-007's migration) with the column addition, update `createSession` to accept `device_id`, and update `AuthRepository.deleteAllSessionsByDevice(userId, deviceId)`.

**Cannot-revoke-current-device requirement:** The server must compare the `device_id` of the session's wrapped key against the session making the DELETE request. The session record needs `device_id` for this check — another reason to add it via Option A.

---

### BUG-019 — Registration error message (Low, ~30min)

**Brief needed:** No. Trivial client-side string fix.

**Architectural assessment:** No architectural concerns. The task is correctly scoped to the client error message string.

---

### FEAT-003 — Android account pairing/recovery (Medium, ~3-5h)

**Brief needed:** Yes — recommendation below.

**Architectural assessment:**

This is the most architecturally interesting task in the queue after ARCH-007. The E2EE implications are significant and the task file raises the right questions.

**From code inspection:** `VaultSetupViewModel` already implements the correct pattern for Option A (passphrase recovery on a new device). When `startSetup()` is called, the ViewModel checks `api.getPassphrase()`. If a passphrase backup exists on the server, the state transitions to `AwaitingUnlock`, which calls `submitUnlock(passphrase)`. `submitUnlock` fetches the wrapped master key from the server, derives the Argon2id KEK from the passphrase, unwraps the master key, generates a new device keypair, and registers the new device with the server.

This is precisely Option A from the task file — and it is already implemented for the web client's session restoration flow. The `LoginScreen.kt` uses `deriveAuthAndMasterKeys` for session re-authentication (passphrase → auth key), but this is distinct from vault recovery (passphrase → wrapped master key via `unwrapMasterKeyWithPassphrase`).

**The gap is in the registration/login screen navigation, not in the crypto.** Android currently reaches `VaultSetupViewModel` only after a successful session (i.e. the user already has a session token). A fresh install with no stored session lands on the registration screen with no path to `VaultSetupViewModel`. The fix is to add a "Recover existing account" navigation path from the registration screen that:
1. Collects username + passphrase.
2. Calls `api.authChallenge(username)` to get the auth salt.
3. Calls `deriveAuthAndMasterKeys(passphrase, salt)` to derive both `authKey` (for login) and the 64-byte output — then calls `api.authLogin(username, authKey)` to create a session.
4. With a valid session, calls `api.getPassphrase()` to retrieve the wrapped master key blob.
5. Calls `VaultCrypto.unwrapMasterKeyWithPassphrase(wrappedMasterKey, passphrase, salt, params)` to recover the master key.
6. Registers the new device and unlocks the vault session.

**Recommendation: Implement Option A only, explicitly.** Option B (Android-initiated pairing code) requires a trusted device to be online simultaneously — an unreliable dependency when the scenario is precisely "I have cleared my phone and have no other device." Option A is the correct solution for the common case (Android-only user who set up a passphrase). Option B can be added later as a complementary path for users who want device-to-device pairing without a passphrase.

**Design decisions for the developer:**

- The "Recover existing account" button should appear on the initial registration/login screen (not behind a secondary menu — it must be discoverable).
- The UX copy should say "I already have a Heirlooms account" rather than "Recover" — "recover" implies data loss.
- After step 3 (successful login), the flow should check whether a passphrase backup exists. If it does, proceed to step 4–6. If it does not, the user has no server-side recovery path and should be told clearly: "No passphrase backup found for this account. To recover, use a trusted device."
- The sharing keypair must be regenerated or fetched from the server on recovery. Check whether the server stores the sharing pubkey separately — if so, a new signing keypair may need to be generated and uploaded. This is the same step that `VaultSetupViewModel.submitUnlock` performs via `deviceKeyManager.setupVault(mk)`.

No new server endpoints are needed. The existing `/api/auth/challenge`, `/api/auth/login`, `/api/keys/passphrase`, and `/api/keys/devices` endpoints are sufficient.

---

### TST-006 — Android remote control research (Medium, ~half day)

**Brief needed:** No.

**Architectural assessment:** Research task, not architectural. No design decisions required before the investigation begins. The outcome will inform the TST-008 automation strategy.

---

### UX-002 — Closed plot visual indicator (Medium, ~2-3h)

**Brief needed:** No. Pure UI. No architectural concerns.

---

### TST-004 — Playwright E2E suite (Medium, ~1-2 days)

**Brief needed:** No. The structure in the task file is sound.

**Architectural assessment:**

The actor-based model is the right approach for E2E tests involving multi-user flows (which every interesting Heirlooms flow is). The key design decisions in the task file are sound:

- Fresh account registration per test run via API key — correct. Avoids shared state.
- API key used for invite generation and seeding — correct. This is what the API key is for.
- No import of app source code in E2E tests — correct. Tests the deployed system, not the implementation.

**One concern:** The task depends on `TST-005` (which appears to be already done, given the reference to "Playwright infrastructure is already in place"). Verify that `TST-005` is in `tasks/done/` before starting.

**JaCoCo note for SEC-002:** The task file specifies `HeirloomsTest/build.gradle.kts` as the location for the JaCoCo gate. This is correct — the integration test module is where server coverage is measured. The unit test coverage in `HeirloomsServer/src/test/` is separate and should remain separate.

---

### SEC-002 — 100% auth/crypto coverage (High, ~2-3 days)

**Brief needed:** No. The plan is architecturally sound.

**Architectural assessment:**

The phased approach (audit → unit tests → integration tests → JaCoCo gate) is correct. The class-level coverage targets are appropriate (100% for `AuthService`, `EnvelopeFormat`, `SessionAuthFilter`, `AuthRoutes`; 90%+ for `KeyService`, `KeysRoutes`, `CriteriaEvaluator`).

**JaCoCo placement concern:** The task places the gate in `HeirloomsTest/build.gradle.kts`, filtering by `**/service/auth/**`, `**/crypto/**`, `**/filters/**`. This is appropriate for integration-test coverage. The developer should verify that the class filter paths match the actual package structure (e.g. `**/server/service/auth/**` not just `**/service/auth/**`) — a wrong filter silently reports 100% coverage on an empty class set.

**`CriteriaEvaluator` coverage note:** After ARCH-007, the `tag` branch of `CriteriaEvaluator` will be significantly changed (token comparison vs. plaintext array check). SEC-002 should be scheduled after ARCH-007 is merged, or the coverage tests for the `tag` branch will need rewriting immediately after.

---

### UX-001 — Tap targets (Low, ~1-2h)

**Brief needed:** No. Purely UI. No architectural concerns.

---

### WEB-001 — Web friends list (Medium, ~half day)

**Brief needed:** No. Trivial UI backed by an existing API endpoint. No architectural concerns.

---

### DOC-001 — Sequence diagrams (Low, ~1 day)

**Brief needed:** No. The approach is sound.

**Architectural assessment:**

Generating sequence diagrams from test execution is valuable and the proposed mechanism (structured event logs from Playwright hooks or JUnit extensions, post-processed to PlantUML/Mermaid) is practical. Mermaid is preferred over PlantUML for this project because it renders natively in GitHub Markdown without a separate render step.

The 6 flows listed are the right ones to document. Flow 6 (staging approval) is the most complex and the most useful to have as a living diagram — it is where the E2EE DEK re-wrap happens and where BUG-020 lives.

Depends on TST-004; do not start until the Playwright suite is stable.

---

### OPS-003 — Pre-production staging environment (Low, ~1-2 days)

**Brief needed:** No. The task file is a complete design.

**Architectural assessment:**

The three-environment model (test → staging → production) is correctly described. The critical prerequisite — fixing the shared `heirlooms-uploads` GCS bucket between test and production — is correctly called out and must be done first.

**E2EE simplification is real:** Because all vault content is encrypted under user-specific keys, the anonymisation script only needs to touch `users.username`, `users.display_name`, and `user_sessions`. GCS blobs, `wrapped_keys`, `plot_members.wrapped_plot_key`, and all DEK columns are already opaque ciphertext. This significantly reduces anonymisation risk.

**Seeded smoke test accounts concern:** The seed script must generate full E2EE key material (master key, device keypair, passphrase backup) for the smoke test accounts. This is not a trivial script — it must invoke the same crypto operations that a real client registration does. Recommend building this as a standalone Kotlin CLI tool (alongside the existing `tools/reimport/` tool) rather than a shell script, so it can use `VaultCrypto` directly.

**Timing:** Low priority. The current test environment is sufficient for the current team size. Build when TST-008 automation is mature enough to justify a formal pre-production gate.

---

## Cross-Cutting Architectural Concerns

### 1. Plot key loading — no universal "ensure loaded" contract

There is no shared contract across platforms for "ensure the plot key is loaded before decrypting a plot item." On Android, `VaultSession.plotKeys` is populated when plots are opened. On web, `UploadThumb.jsx` loads on demand. `PhotoDetailPage.jsx` does not load at all (BUG-022). This ad hoc approach will produce similar bugs as new surfaces are added (iOS, capsule contexts, etc.).

**Recommendation:** Define a formal `ensurePlotKey(plotId)` contract in each client's session layer. On Android, this is already partially implemented in `SharedPlotsViewModel`. On web, extract from `UploadThumb.jsx` into `vaultSession.js`. Document the contract in `docs/envelope_format.md` or a new `docs/plot_key_contract.md`.

### 2. Session-device_id linkage gap (SEC-011 prerequisite)

`user_sessions` does not link to `device_id`. This affects both SEC-011 (device revocation) and any future feature that needs to enumerate or invalidate sessions by device (e.g. "sign out everywhere"). A V32/V33 migration adding `device_id TEXT NULL REFERENCES wrapped_keys(device_id)` to `user_sessions` should be included in the SEC-011 implementation.

### 3. Tag scheme migration is a coordinated multi-platform rollout

ARCH-007's migration requires client and server changes to ship in a specific order: server must accept both plaintext tags and token-based tags during the migration window. If the server migration ships before client updates, existing clients continue to work. If a client update ships before the server migration, clients will send tokens the server cannot process. The correct order is server-first. This must be documented in the ARCH-007 developer brief.

### 4. CriteriaEvaluator tag atom will break with ARCH-007

The current `tag` atom in `CriteriaEvaluator` generates SQL against `uploads.tags TEXT[]`. After ARCH-007, it must query `user_upload_tags`. Any trellis criteria containing a `tag` atom will need migration. The server must handle criteria JSONB rows that still contain `{"type": "tag", "tag": "<plaintext>"}` (old format) and `{"type": "tag", "token": "<hex>", "scheme_version": 1}` (new format) during the migration window. This is a server-side concern only — the evaluator can dispatch on which field is present.

### 5. FFmpeg/ffprobe in Cloud Run — no architectural risk

Confirmed by code: `ThumbnailGenerator.kt` already shells out to `ffmpeg` and `ffprobe`. Both are present in the Cloud Run container. BUG-021's fix is purely an application-level gap (calling `extractVideoDuration` and storing the result).

---

## Tasks Requiring an Architecture Brief Before Development

| Task | Status | Action |
|---|---|---|
| **ARCH-007** | Resolved in this document | Developer can proceed using this document as the brief. A standalone `docs/tag_scheme.md` should be produced as a deliverable of the implementation task. |
| **FEAT-003** | Recommended approach defined above (Option A) | Developer can proceed. No additional brief needed — the path is described in detail in the FEAT-003 section above. |
| **SEC-011** | Schema gap identified | Developer must implement Option A (add `device_id` to `user_sessions`) as part of this task. The Flyway migration version must be coordinated with ARCH-007 (ARCH-007 gets V32; SEC-011 gets V33, or they share one migration if sequenced together). |

All other tasks in the queue are architecturally unambiguous and can be picked up without a separate brief.

---

## Recommended Sequencing

Given the dependencies and unblocking relationships:

**Immediate (unblock smoke test):**
1. BUG-022 — unblocks TST-008 Steps 4–5
2. BUG-019 — trivial, ship in same cycle

**Short-term (current iteration):**
3. ARCH-007 implementation (using this document as the brief)
4. BUG-020 — client-side DEK re-wrap
5. BUG-021 — video duration
6. FEAT-003 — Android passphrase recovery
7. SEC-011 — device revocation (coordinate Flyway version with ARCH-007)

**Medium-term:**
8. FEAT-004 — Android invite friend
9. WEB-001 — web friends list
10. SEC-012 — tag leakage doc (after ARCH-007 ships)
11. TST-004 — Playwright E2E suite
12. SEC-002 — auth/crypto coverage (after TST-004, after ARCH-007)

**Long-term:**
13. TST-008 — formal smoke test spec
14. TST-006 — Android remote control research
15. DOC-001 — sequence diagrams (after TST-004)
16. OPS-003 — pre-production staging (after TST-008 automation is mature)
17. UX-001, UX-002 — fill spare capacity in any cycle
