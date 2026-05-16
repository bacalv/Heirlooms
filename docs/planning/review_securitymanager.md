# Security Manager Review — Outstanding Queue Tasks

**Reviewer:** Security Manager  
**Date:** 2026-05-16  
**Scope:** All tasks currently in `tasks/queue/` (excluding BUG-017 and BUG-018, which are DONE)  
**Reference documents:** `docs/security/threat-model.md`, `docs/envelope_format.md`, task files

---

## Summary Table

| Task | Title (short) | Security Risk | E2EE Impact | Auth/Session Impact | Must Review Before Merge? |
|------|--------------|---------------|-------------|---------------------|--------------------------|
| BUG-022 | Web detail view blank (shared plot) | **High** | Yes — plot key DEK path missing in web client | No | Yes |
| TST-008 | Shared plot E2E smoke test spec | Medium | Yes — validates E2EE correctness end-to-end | No | No (test task, but Step 6 must not begin before ARCH-007 is approved) |
| ARCH-007 | E2EE tag scheme design | **High** | Yes — defines new crypto primitives for tags | No | Yes — full Security review before any implementation begins |
| SEC-012 | Tag metadata leakage disclosure | Medium | Yes — documents residual leakage of ARCH-007 | No | N/A (Security-owned task) |
| BUG-021 | Video duration extraction | Medium | No | No | Yes — server-side media processing is new attack surface |
| BUG-020 | Shared plot auto-approve DEK re-wrap | **Critical** | Yes — pre-wrapped DEKs accepted by server; membership validation is the gating concern | Yes (plot membership check) | Yes — server-side validation logic requires Security sign-off |
| FEAT-004 | Android invite friend (share sheet) | Medium | No | Yes — invite token generation and handling | Yes |
| SEC-011 | Device revocation | **High** | Yes — deletes wrapped key; no full key rotation | Yes — session invalidation completeness | N/A (Security-owned task) |
| BUG-019 | Registration error message fix | None | No | No | No |
| FEAT-003 | Android account pairing / recovery | **High** | Yes — any new path to unwrap master key | Yes — new auth / recovery flow | Yes — requires Security sign-off on whichever option is chosen before implementation |
| TST-006 | Android remote control research | Low | No | Low (test credentials, staging API key exposure) | No |
| UX-002 | Closed plot visual indicator | None | No | No | No |
| TST-004 | Playwright E2E suite | Low | No | Low (staging API key in test harness) | No |
| SEC-002 | 100% auth/crypto coverage | **Critical** | Yes — validates all E2EE paths have test coverage | Yes — validates all auth paths | N/A (Security-owned task) |
| UX-001 | Tap targets | None | No | No | No |
| WEB-001 | Web friends list | None | No | No | No |
| DOC-001 | UML sequence diagrams | Low | No | No | No |
| OPS-003 | Pre-production staging environment | Medium | Low (GCS bucket separation) | Low (new API key scope) | Yes — anonymisation pipeline design requires Security review |

---

## Detailed Notes Per Task

---

### BUG-022 — Web detail view blank for shared plot items
**Security risk: High**

This is not merely a UX bug — it represents a gap in the web client's cryptographic dispatch logic. The web `PhotoDetailPage.jsx` handles `ALG_P256_ECDH_HKDF_V1` (sharing key) and master-key DEK formats but omits the `plot-aes256gcm-v1` DEK format used for all staging-approved shared plot items. The consequence is that User B cannot view shared plot media in the web detail view.

**E2EE implications:** The fix requires the web client to correctly load the plot key from session state and use it in full-image DEK decryption. This is parallel logic to what `UploadThumb.jsx` already does for thumbnails. The critical security concern is that the DEK unwrap code path must behave identically to the thumbnail path — using the same plot key source and envelope format — so that the two paths cannot diverge in future refactors. The fix must be audited to confirm the plot key is obtained from an authenticated session store, not from any user-supplied parameter.

**Auth/session impact:** None beyond confirming that the plot key is sourced from the authenticated session.

**Recommendation:** Security review required before merge. Reviewer must confirm: (a) plot key is loaded from session state, not from a request parameter; (b) no new DEK wrapping or unwrapping occurs server-side; (c) the fix does not accidentally expose the plot key to a script injection surface.

---

### TST-008 — Shared plot E2E smoke test spec
**Security risk: Medium**

A testing task. Its security value is high: a formal E2E smoke test spec is the primary human-readable validation that the E2EE model is working end-to-end. Steps 1–5 validate that items encrypted by User A are decryptable only by User B (and vice versa) through the full plot key handshake.

**E2EE implications:** Step 6 (auto-tagging with E2EE tags) is explicitly marked "future feature, blocked on ARCH-007." This is correct. The smoke test spec must not attempt to implement or test Step 6 until ARCH-007 is approved and SEC-012 is complete. Any stub assertions for Step 6 that assume plaintext tags would be misleading and should be avoided.

**Security concern:** The automation strategy (Step 1) proposes driving account setup via API calls using the staging API key. The staging API key (`heirlooms-test-api-key`) must not be committed to source control or embedded in test fixtures. It must be fetched from Secret Manager at runtime. Confirm this is explicit in the spec deliverable.

**Recommendation:** No sign-off gate needed on the spec itself, but the spec must include explicit constraints: Step 6 is unimplementable until ARCH-007 + SEC-012 complete; API key handling must use Secret Manager, not hardcoded values.

---

### ARCH-007 — E2EE tag scheme (HMAC tokens + encrypted display names)
**Security risk: High**

This is a significant new cryptographic subsystem. The proposed scheme is sound in principle but has several areas requiring careful Security review before the TechnicalArchitect produces a final design brief.

**E2EE analysis:**

1. **Tag token construction:** `HMAC-SHA256(HKDF(master_key, "tag-token-v1"), tag_value)` — the HKDF derivation is correct. Using a master-key-derived HMAC key rather than the master key directly provides key separation and ensures that compromising the tag HMAC key does not expose the master key or any DEK. Sound.

2. **Per-user isolation:** Tokens are keyed to the user's master key, so cross-user correlation is not possible even for identical tag values. This is the correct design and must be stated explicitly in the brief.

3. **Auto-tag loop prevention:** Using a separate HKDF context (`"auto-tag-token-v1"`) to produce tokens that cannot match trellis criteria tokens is the right approach. However, the brief must specify what happens if a trellis criterion is constructed with an `auto-tag-token-v1` token (e.g. by a manipulated client) — the server must reject or ignore such criteria. This needs an explicit validation rule.

4. **Tag display name storage:** The open question about deduplication (per-tag globally vs. per (user, upload) tuple) has a security dimension: if display name ciphertext is stored globally (deduped), the nonce must still be unique per encryption, which precludes naive deduplication. If deduped by ciphertext equality, the same (key, nonce) pair could be reused — this violates the nonce-reuse prohibition in `envelope_format.md`. The brief must require per-encryption nonces and should not attempt to deduplicate by ciphertext.

5. **Migration of existing plaintext tags:** The migration is a one-time HMAC computation per tag value per user. The migration window (when plaintext tags exist server-side and haven't been converted) is a brief residual risk window. The brief must specify that plaintext tags are scrubbed from the database immediately after successful client migration, not retained as a fallback.

6. **Token scheme versioning:** The open question about storing the scheme version alongside the token is important for future rotation. Security recommends storing a version prefix on the token (e.g. `v1:<hex>`) to enable a future re-keying without a full re-tag operation.

**Threat model impact:** ARCH-007 introduces a new server-visible metadata channel (tag tokens). This is formally documented in SEC-012 and is an accepted residual risk. The threat model (§2 of `threat-model.md`) must be updated once ARCH-007 is finalised to add this channel to the accepted risk table.

**Recommendation:** TechnicalArchitect must produce a formal brief before any developer begins implementation. Security must review the brief. Specifically: nonce handling for display names, auto-tag loop prevention enforcement, migration scrubbing guarantee, and token versioning approach.

---

### SEC-012 — Tag metadata leakage documentation
**Security risk: Medium (Security-owned task)**

Blocked on ARCH-007 being finalised. Once ARCH-007 is complete, this task documents the residual metadata leakage (tag equality, frequency, co-occurrence observable to server) and produces user-facing disclosure wording.

**Security assessment of the leakage:** The leakage is genuine but bounded. The server observes token patterns, not semantic meaning. Cross-user correlation is structurally impossible (per-user HMAC keys). The postal metadata analogy in the task file is apt.

**Priority:** Must complete before any user-facing tag feature ships. Cannot be deferred until after launch.

**Deliverables must include:** (1) formal entry in `threat-model.md` under Accepted Risks; (2) user-facing privacy notice text; (3) confirmation that the threat model holds under the proposed scheme (i.e. that HKDF context separation is sufficient to prevent cross-context token correlation). Future-work item: fully client-side tag evaluation (no server-side token storage) to eliminate even this leakage.

---

### BUG-021 — Video duration extraction (server-side FFmpeg/media library)
**Security risk: Medium**

This bug fix introduces server-side parsing of user-supplied video data. This is a meaningful new attack surface.

**Attack surface analysis:** `ThumbnailGenerator` currently extracts a video frame. Adding duration extraction requires parsing video metadata (container headers, codec info). Any parser that processes attacker-controlled binary data is a potential vulnerability surface:
- **Malformed video attacks:** A crafted video file could exploit bugs in the media library (e.g. integer overflow in container length fields, heap corruption in codec parsers). FFmpeg has a long history of such CVEs.
- **Denial of service:** An excessively large or deeply nested media container could consume CPU or memory during parsing.
- **Path traversal / file inclusion:** If a media library references external resources (e.g. HTTP URIs in media containers), a crafted file could cause the server to make outbound network requests.

**Mitigations the developer must implement:**
1. Use a well-maintained, up-to-date library (FFmpeg or similar). Pin the version and include it in the dependency update cycle.
2. Set strict resource limits on the parsing operation (CPU timeout, memory cap).
3. Run duration extraction in a sandboxed subprocess or with OS-level resource limits if FFmpeg is used as an external process.
4. Validate that the result (duration) is a reasonable positive integer before storage. Reject files where parsing fails rather than storing a default `0`.
5. Log extraction failures at WARN level with enough context to detect systematic attacks (many failing parses from a single user).

**E2EE impact:** None. Duration is stored in plaintext in the `uploads` table, consistent with existing plaintext fields (`taken_at` etc.). This is correct behaviour.

**Recommendation:** Security review required before merge. Reviewer must confirm: library version is pinned and up-to-date; resource limits are in place; extraction failures are logged; no outbound network access is possible from the parsing path.

---

### BUG-020 — Shared plot auto-approve: client-side DEK re-wrap
**Security risk: Critical**

This is the highest-risk developer task in the queue. The fix introduces a new server API path that accepts pre-wrapped DEKs from the client and inserts items directly into `plot_items` without going through staging. The attack vector is clear: a malicious or compromised client could submit pre-wrapped DEKs targeting a plot it is not a member of.

**Attack scenarios:**

1. **Non-member plot poisoning:** Client submits `targetPlotId` of a plot it is not a member of, with a `wrappedPlotItemDek` it generated itself. If the server does not validate membership, the item is inserted into `plot_items` for a plot the client has no authorisation over. Other members of that plot would see a foreign item (though they cannot decrypt it, because the DEK was not correctly wrapped with the real plot key).

2. **DEK integrity bypass:** A compromised client could submit a malformed or attacker-controlled DEK. The server must not store a pre-wrapped DEK without verifying that it is structurally a valid envelope (`EnvelopeFormat.validateSymmetric` with algorithm `plot-aes256gcm-v1`).

3. **Plot key inference oracle:** If the server returns different error codes for "not a member" vs "invalid DEK format," an attacker might use differential responses to probe plot membership or DEK structure.

**Required server-side validations (non-negotiable before merge):**
1. `targetPlotId` must resolve to a plot where the authenticated user (`authUserId`) is an active, approved member. Check `plot_members` with `status = 'approved'`. Return 403 (not 404) if not a member.
2. `wrappedPlotItemDek` and `wrappedPlotThumbDek` must pass `EnvelopeFormat.validateSymmetric` with algorithm `plot-aes256gcm-v1`. Reject any other algorithm ID.
3. The error responses for "not a member" and "invalid DEK format" must be indistinguishable from the caller's perspective (same HTTP status, same opaque body). Log the specific reason server-side only.
4. The existing staging fallback path must be unaffected — no regression on non-member or missing-plot-key cases.

**E2EE implications:** The server cannot verify that the DEK is correctly wrapped under the plot key (it does not hold the plot key). This is inherent to the E2EE model. The server's only recourse is membership validation. This limitation must be documented in a comment in the server code.

**Threat model impact:** This task widens the server API surface. The threat model must be updated to include this new endpoint and its membership validation requirement once the fix is shipped.

**Recommendation:** This task requires explicit Security sign-off before merge. The pull request must include evidence of: (a) membership validation test (non-member attempt returns 403); (b) envelope format validation test (invalid algorithm ID rejected); (c) indistinguishable error responses.

---

### FEAT-004 — Android invite friend (share sheet)
**Security risk: Medium**

Invite tokens are short-lived credentials. Exposing them via the Android share sheet introduces a risk that the token appears in:
- Share sheet history / recent items caches
- Clipboard (if the user copies instead of shares)
- Third-party apps with share sheet integration (which may log or process the URL)
- Messaging app link previews (which may make an HTTP HEAD request to the invite URL)

**Required mitigations:**
1. **Expiry display:** The task file already specifies showing expiry (48 hours) before sharing. This is correct and must be implemented.
2. **Token single-use enforcement:** The server must invalidate the invite token immediately on first use (registration or friend-connect). Confirm this is already true for the existing web flow (FEAT-001) and does not need to be added.
3. **Token entropy:** Confirm the invite token from `GET /api/auth/invites` is generated from `SecureRandom` with sufficient entropy (current implementation should be verified — the threat model notes 32 random bytes as standard for session tokens; invite tokens should be at minimum 16 bytes / 128 bits).
4. **URL construction:** Use HTTPS only (`https://heirlooms.digital/invite?token=...`). The task file specifies this correctly. The staging flavor must use `https://test.heirlooms.digital/...` — confirm the flavor-aware URL construction mirrors the fix applied in BUG-008 for the earlier invite link issue.
5. **Link preview requests:** If the messaging app makes an HTTP request to the invite URL when rendering a link preview, the server should not consume (invalidate) the token on that GET — only on the actual registration/connect action. Confirm the server-side invite flow does not invalidate on the initial landing page load.

**E2EE impact:** None directly. Invite tokens grant access to register/connect; they do not expose any vault content.

**Recommendation:** Security review before merge. Focus on token entropy, single-use enforcement, staging flavor URL correctness, and link preview behaviour.

---

### SEC-011 — Device revocation
**Security risk: High (Security-owned task)**

Device revocation is a security-critical feature. The task file is well-scoped. My assessment of the implementation requirements:

**IDOR risk:** The `DELETE /api/auth/devices/{deviceId}` endpoint must be scoped to the authenticated user's devices only. A simple check on `wrapped_keys.user_id = authUserId` is required. Without this, User A could delete User B's device keys — a severe access control failure. The endpoint must return 404 (not 403) when the `deviceId` does not exist in the authenticated user's device list, to prevent device ID enumeration across users.

**Current-device protection:** The server must check whether `deviceId` matches the device associated with the caller's current session token. This requires looking up `device_id` from the session record and comparing. The check must happen server-side, not only in the UI — a crafted API call bypassing the UI must also be rejected.

**Session invalidation completeness:** Deleting the `wrapped_keys` row is necessary but not sufficient if sessions are stored separately. All `user_sessions` rows for the target `device_id` must be deleted atomically in the same transaction as the `wrapped_keys` deletion. If these are in separate tables, use a database transaction to ensure consistency.

**No re-encryption required:** The task correctly notes that revocation does not require re-wrapping DEKs. The wrapped key for the revoked device is simply deleted; the master key (and all DEKs) remain intact and accessible from other devices. This is correct for the current threat model. Full key rotation (new master key, re-wrap all DEKs) is a separate future task and should remain out of scope here.

**Forward secrecy gap:** Revocation removes the server-side wrapped key but cannot retroactively invalidate any cached decryption state on the revoked device itself. If the device retained the master key in memory or in an insecure cache, it could continue decrypting previously fetched content. The UI warning ("revoke old devices before selling/disposing") addresses this operationally, but the technical limitation should be documented in the threat model.

**Recommendations:** Implement and test: (a) IDOR prevention (user_id scoping); (b) current-device self-revocation rejection, server-side; (c) atomic transaction covering `wrapped_keys` deletion and session invalidation; (d) 404 for non-existent or cross-user device IDs. Add to threat model after completion.

---

### BUG-019 — Registration error message fix
**Security risk: None**

The fix corrects a misleading error message ("Username already exists" shown for a duplicate device_id collision). No security implications. The underlying 409 handling is already implemented (BUG-014 is done). This is a pure UX fix.

One minor observation: ensure the corrected error message does not leak internal implementation details (e.g. "device_id collision"). A user-facing message like "Registration failed. Try again or use a different device." is appropriate.

---

### FEAT-003 — Android account pairing / recovery
**Security risk: High**

Any new path to authenticate and recover the master key is a potential weakening of the E2EE model. This task is appropriately marked for investigation rather than immediate implementation. My security assessment of the two options:

**Option A — "Recover with passphrase" on Android:**
- This reuses the existing `argon2id-aes256gcm-v1` passphrase recovery path that already exists in the web client. The cryptographic flow (email + passphrase → Argon2id → master key blob → decrypt master key) is already proven.
- Security risk: the Argon2id parameters (64 MiB, 3 iterations, as specified in `envelope_format.md`) must be used identically on Android. Any deviation (lower memory cost, fewer iterations) weakens passphrase brute-force resistance. The developer must not tune parameters down for performance without Security review and explicit approval.
- The Argon2id implementation on Android must use a vetted library. Note the reference low-spec device (Galaxy A02s) — the envelope format spec already notes that 32 MiB may be needed on low-spec devices. Any parameter change requires Security review.
- This option does NOT introduce a new authentication bypass; it reuses an existing server-side mechanism. Lower risk than Option B.

**Option B — Android-initiated pairing code:**
- This requires designing a new key exchange handshake. The existing web → Android pairing uses a short-lived QR code to initiate P-256 ECDH key exchange. Inverting this (Android generates the code, web or another Android scans) is architecturally symmetric, but requires careful design:
  - The code displayed on the fresh Android must not embed any key material — it should be a session identifier only.
  - The trusted device must authenticate with the server before the key exchange begins — no unauthenticated device should be able to initiate a key transfer.
  - The code must expire (e.g. 5 minutes), be single-use, and be invalidated immediately after the handshake completes.
  - The key transfer path must use the existing `p256-ecdh-hkdf-aes256gcm-v1` envelope to wrap the master key — no new crypto primitives.
- Security risk: higher than Option A because it requires new server endpoints and new client-side pairing state machine code. New auth flows are historically where vulnerabilities are introduced.

**Recommendation:** Option A is the lower-risk choice and should be implemented first. Option B should require a formal security brief (similar to ARCH-007) before any code is written. Regardless of which option is chosen, Security must review the implementation before merge. This task must not be dispatched to a developer without first agreeing the option and producing a brief.

---

### TST-006 — Android remote control investigation
**Security risk: Low**

A research task with two minor security considerations:

1. **Test credentials management:** The staging API key (`heirlooms-test-api-key`) must not appear in the recommendation document, test YAML files, or committed configuration. The recommendation must specify that all credentials are read from Secret Manager or environment variables at runtime.

2. **Staging API key scope:** The staging API key grants `FOUNDING_USER_ID` identity (per the threat model, F-14). Any Android automation harness that uses this key effectively has superuser access to the test environment. This is acceptable for the test environment (which is throw-away), but the recommendation must note that the test harness must never be pointed at production.

---

### UX-002 — Closed plot visual indicator
**Security risk: None**

Disabling UI affordances (approve/share actions) for closed plots is a UX improvement. There are no security implications as long as the server enforces closed-plot restrictions independently of the client-side UI state. Confirm during review that the server already rejects approval/share actions for closed plots at the API level (so that a client bypassing the UI cannot force-approve into a closed plot). If server enforcement is not in place, this becomes a Medium security issue — but that would be a pre-existing gap, not introduced by this task.

---

### TST-004 — Playwright E2E suite
**Security risk: Low**

The primary security value of this task is that it is a prerequisite for SEC-002 (auth/crypto coverage gate). Without TST-004, the Playwright-based coverage validation cannot run.

**Security concerns in the test harness itself:**
1. The staging API key must be read from Secret Manager, not committed to source control or hardcoded in `config.ts`. The task file notes fetching from Secret Manager — confirm this is the implementation path.
2. Actor registration uses real invite tokens against the staging environment. Ensure the staging environment's rate limiting (SEC-004, if implemented) is not triggered by rapid actor registration in CI runs. Either exclude the test agent IP from rate limiting in staging, or design the test harness to space registrations.
3. Tests must clean up created accounts (or use isolated accounts per run with implicit cleanup). Accumulation of stale test accounts in the staging environment is an operational risk, not a security risk.

---

### SEC-002 — 100% auth/crypto coverage gate
**Security risk: Critical (Security-owned task)**

This is the most important long-term security task in the queue. The current baseline of 53.3% instruction coverage on security-critical server code means that nearly half of the auth and crypto code paths have never been exercised by any test. That is an unacceptable situation for a product whose security model claims are the primary value proposition.

**Why this is Critical, not merely High:**
- Untested code paths in `AuthService`, `EnvelopeFormat`, and `SessionAuthFilter` are where vulnerabilities hide. If an error path in `EnvelopeFormat` does not fail loudly on an unknown algorithm ID (as required by the spec), and that path is never tested, the vulnerability would be invisible until exploited.
- The JaCoCo gate enforces this as a build-time invariant — once established, regressions are caught before they reach review.

**Dependencies:** TST-004 (Playwright E2E suite) is a dependency because the Playwright suite contributes integration-level coverage for auth flows that unit tests cannot reach (e.g. the full register → session → authenticated request cycle against a real HTTP stack).

**Implementation notes:**
- Phase 1 (audit) should be the first thing the SecurityManager does on this task — run `./gradlew coverageTest` and publish the per-class breakdown. The `docs/coverage_baseline.md` file should be the target for this report.
- Priority 1 classes (100% target): `AuthService`, `EnvelopeFormat`, `SessionAuthFilter`, `AuthRoutes`. These are the classes most directly responsible for the security claims.
- The JaCoCo gate must be scoped to the security-critical packages specifically — a project-wide gate would be gamed by high-coverage non-security code masking gaps in the critical paths.

---

### UX-001 — Tap targets
**Security risk: None**

No security implications.

---

### WEB-001 — Web friends list
**Security risk: None**

The web friends list page displays user data already returned by an authenticated API endpoint. No new data access paths, no new auth flows, no E2EE implications. The server already enforces that the friends list is scoped to the authenticated user.

---

### DOC-001 — UML sequence diagrams
**Security risk: Low**

Sequence diagrams generated from test output are security-adjacent: they make auth flows auditable and maintain the threat model. From a security perspective, the diagrams must not include actual session tokens, keys, or other secret values — even in test output used as diagram source. Confirm that test output is sanitised before diagram generation.

---

### OPS-003 — Pre-production staging environment
**Security risk: Medium**

Two security-relevant concerns:

**1. GCS bucket sharing (current gap — flagged as High priority):**
The test environment currently shares `heirlooms-uploads` GCS bucket with production. This means test uploads and production uploads coexist in the same bucket. Although all content is encrypted blobs (meaningless without device keys), an operational error (e.g. test deletion script that matches too broadly) could destroy production objects. This is an **existing** risk that OPS-003 should resolve as a prerequisite, and it is correctly called out in the task file. Fixing bucket separation should be treated as a security-relevant prerequisite, not merely an operational nicety.

**2. Anonymisation pipeline design:**
The proposed pipeline (snapshot → restore → anonymise → seed) touches production data. The anonymisation script (`scripts/anonymise-snapshot.sh`) must:
- Scramble all PII fields (`username`, `display_name`) using a one-way transform (hash with a random salt, not reversible)
- Clear all `user_sessions` rows before the snapshot is restored (no production session tokens in staging)
- Not attempt to decrypt or re-encrypt vault content (correctly noted in the task file — vault content is already opaque)
- Run on a secure, access-controlled machine, not on a developer laptop
- Produce a verification step that confirms no plaintext PII remains in the scrambled fields

**Seeded smoke test accounts:**
Fresh E2EE key material generated by the seed script must not reuse any key material from production or from previous seeding runs. Each seed run must generate fresh Argon2id salts, device key pairs, and master keys.

**New staging API key scope:**
The staging environment needs its own API key (noted in the task). Confirm this key is rotated independently from the test environment key and has its scope documented.

---

## Security Task Priority Ordering

The three Security-assigned tasks are ordered as follows:

### Priority 1: SEC-002 — Auth/Crypto Coverage Gate
**Reason:** This is the foundational safety net for all other security work. A 53.3% coverage baseline on auth/crypto code means every other security task lands in code that may have untested error paths hiding vulnerabilities. The JaCoCo gate, once established, catches regressions in perpetuity. **Do not ship M11 without this gate in place.**

**Note on dependency:** SEC-002 depends on TST-004 (Playwright E2E suite). TST-004 should be prioritised immediately after BUG-020 and BUG-022 are resolved, because the E2E tests will fail on those bugs if run now. Fix the bugs, then build the Playwright suite, then close SEC-002.

### Priority 2: SEC-011 — Device Revocation
**Reason:** Device revocation is a user-facing security control that closes a permanently open attack surface (stale `wrapped_keys` rows for disposed devices). It is also a straightforward, well-scoped task with a clear spec. It does not depend on any other open task. It should be dispatched in the next iteration.

**Key implementation requirements:** IDOR prevention (user_id scoping); current-device self-revocation rejection server-side; atomic transaction (wrapped_keys + sessions); 404 for cross-user device IDs.

### Priority 3: SEC-012 — Tag Metadata Leakage Disclosure
**Reason:** SEC-012 is blocked on ARCH-007 (E2EE tag scheme design). It cannot begin until ARCH-007 is approved. Once ARCH-007 is finalised, SEC-012 must complete before any tag feature ships to production users. The user-facing disclosure wording is a product requirement, not optional.

---

## Developer Tasks Requiring Security Sign-Off Before Merge

The following developer-assigned tasks must have a Security review of the pull request before they are merged to main:

| Task | Reason for sign-off requirement |
|------|--------------------------------|
| **BUG-020** | New server API path accepting pre-wrapped DEKs; membership validation is the sole server-side guard. Highest-risk task in the queue. |
| **BUG-022** | Web client DEK dispatch logic extended to plot key path; must confirm key is sourced from authenticated session state only. |
| **FEAT-003** | New authentication / master key recovery path. Regardless of which option is implemented, any new path to unwrap the master key requires Security review. Must not be dispatched without an agreed option and a design brief reviewed by Security first. |
| **FEAT-004** | Invite token handling in Android share sheet; staging flavor URL correctness; link preview token consumption behaviour. |
| **BUG-021** | New server-side binary parsing of attacker-controlled video data. Library version, resource limits, and failure logging must be reviewed. |
| **ARCH-007** | Before any developer begins implementation of the tag scheme, the TechnicalArchitect's design brief must be reviewed by Security. Implementation PR also requires Security review. |
| **OPS-003** | Anonymisation pipeline design and GCS bucket separation must be reviewed before the pipeline script is written or executed. |

Tasks not on this list (BUG-019, UX-001, UX-002, WEB-001, TST-004, TST-006, TST-008, DOC-001) do not require Security sign-off. Standard code review is sufficient.

---

## Threat Model Maintenance Notes

The following items should be added to `docs/security/threat-model.md` as tasks complete:

1. **After BUG-020 merges:** Add a new finding entry documenting the pre-wrapped DEK acceptance path and its membership validation requirement.
2. **After ARCH-007 + SEC-012 complete:** Add tag token metadata leakage to the Accepted Risks table (§8) with the full characterisation.
3. **After SEC-011 merges:** Add device revocation to the Authentication Architecture section and document the forward secrecy gap (client-cached master key cannot be retroactively invalidated).
4. **After FEAT-003 merges:** Add the new recovery path to the Authentication Architecture section and document any Argon2id parameter decisions made for Android.
5. **After OPS-003 design is approved:** Document GCS bucket isolation as a resolved risk, and the anonymisation pipeline as an accepted operational process.
