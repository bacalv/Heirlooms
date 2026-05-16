# RES-003 — PQC Migration Readiness: Algorithm Break Response Plan

**ID:** RES-003  
**Date:** 2026-05-16  
**Author:** Research Manager  
**Audience:** Technical Architect (primary), Security Manager (companion section), CTO  
**Status:** Final  
**Depends on:** RES-001 (three-layer attack window framework)

---

## Executive summary

Heirlooms' current E2EE design uses P-256 ECDH for all key-wrapping operations. P-256 is broken by Shor's algorithm on a sufficiently large fault-tolerant quantum computer. Nation-state adversaries are conducting Harvest Now, Decrypt Later (HNDL) operations against encrypted traffic today.

This brief formalises the CTO's three-layer attack window framework into a Technical Architect-ready migration plan. The core insight, established in the 2026-05-16 CTO session, is that **file content does not need re-encryption** — AES-256-GCM is quantum-safe. Only the P-256 key-wrapping layer needs migration. Migration cost is O(keys), not O(files).

**The migration has four phases and a background service.** Phases 0–1 are platform codec work with no user impact. Phase 2 is a silent client-side re-wrap on next authentication. Phases 3–4 are background service operations that complete the migration for existing vaults. Total elapsed time from announcement to complete migration, assuming a prompt announcement and normal user engagement, is estimated at 12–24 months.

**Platform readiness as of 2026:**
- Android (BouncyCastle): ML-KEM-768 available since v1.79, production-grade as of v1.84. Ready to implement.
- iOS (CryptoKit): ML-KEM-768 available in iOS 26, shipping late 2026. Includes Secure Enclave support and formally verified implementation. Available, but requires iOS 26 minimum.
- Web (WebCrypto): ML-KEM-768 not yet in the WebCrypto standard. WASM bridge required (mlkem-wasm, 17 KB gzipped). Suitable as a stopgap.
- Server: No cryptographic work required. Validation is structural only.

The industry has moved decisively. Proton Mail rolled out ML-KEM-768 + X25519 hybrid encryption to all users in May 2026. Signal deployed its Triple Ratchet (SPQR) in October 2025. Apple ships X-Wing (X25519 + ML-KEM-768) in iOS 26. The transition is no longer experimental — it is production-grade across the ecosystem.

---

## Current threat landscape update (since RES-001)

### Threat window has not materially changed — but urgency is confirmed

RES-001 (2026-05-16) assessed the HNDL threat as present and the quantum timeline as credibly within 5–10 years based on three papers from May 2025 – March 2026. That assessment stands.

Additional 2026 data points:
- Estimates now suggest encrypted traffic captured in 2026 could be decrypted as early as 2032 using anticipated quantum hardware.
- China's Zuchongzhi 3.0 processor (105 qubits, announced March 2025) marks continued hardware acceleration by a nation-state adversary.
- NSA CNSA 2.0 framework: quantum-safe algorithms required for new national security systems by **January 2027**, full migration by **2030**, infrastructure migration by **2035**.
- Harvard Business Review (January 2026): "Why Your Post-Quantum Cryptography Strategy Must Start Now."

**For Heirlooms specifically:** Long-lived family archive content is among the highest-value HNDL targets. A photograph encrypted today and stored for thirty years is a prime target if P-256 is broken. The exposure profile is worse than ephemeral messaging — a harvested Signal message from 2026 has limited value in 2032; a family archive has high personal, legal, and identity value indefinitely.

### Industry is executing, not planning

The key update from RES-001 is that the industry has moved from preparation to execution:
- Proton Mail: ML-KEM-768 + X25519 hybrid encryption live for all users, May 2026 [RES-003-007].
- Signal: PQXDH deployed 2023; Triple Ratchet (SPQR) with ML-KEM-768 deployed October 2025 [RES-003-009].
- Apple: CryptoKit ML-KEM-768 and X-Wing (MLKEM768+X25519) in iOS 26 / macOS Tahoe [RES-003-005].
- Meta: Five-level PQC migration maturity framework published April 2026 [RES-003-010].
- Cloudflare: 38% of human HTTPS traffic using hybrid post-quantum handshakes as of March 2025.

**The window for "plan now, implement later" is closing.** When Proton Mail, Signal, and Apple are all in production, Heirlooms' use of pure P-256 becomes a visible gap for any security-aware user.

### NIST standards remain stable — no revision risk

FIPS 203 (ML-KEM), FIPS 204 (ML-DSA), FIPS 205 (SLH-DSA) were finalised August 2024 and are in deployment. No revision is anticipated [RES-003-001]. NIST SP 800-227 (September 2025) provides operational guidance for KEM deployment and explicitly endorses hybrid deployment (X25519 + ML-KEM) during the transition period [RES-003-004].

---

## Algorithm IDs to reserve in envelope format

The following algorithm IDs must be added to `docs/envelope_format.md` under a new "Reserved (PQC migration)" section. These names follow the existing naming convention: `<kex-scheme>-<kdf>-<aead>-<version>`.

### Confirmed IDs (ready to implement)

| ID | Purpose | Envelope variant | Notes |
|---|---|---|---|
| `hybrid-p256-mlkem768-hkdf-aes256gcm-v1` | Master key, plot key, and sharing key wrapping — hybrid transitional scheme | Asymmetric (new layout — see §Phase 0) | Combines P-256 ECDH + ML-KEM-768; attacker must break both. For Phase 1/2. |
| `mlkem768-hkdf-aes256gcm-v1` | Master key, plot key, and sharing key wrapping — pure PQC post-transition | Asymmetric (ML-KEM layout) | Replaces hybrid once confidence is established. For Phase 3+. |

### Naming rationale

The existing ID `p256-ecdh-hkdf-aes256gcm-v1` names the key exchange scheme first, then the KDF, then the AEAD. The new IDs follow the same pattern. `hybrid-p256-mlkem768` names the composition (both classical and PQ components) clearly. This is consistent with IETF draft naming conventions (see `draft-ietf-tls-ecdhe-mlkem`).

### Wire format implications

**The existing envelope wire format (version 0x01) does not change.** Adding new algorithm IDs never requires a version bump — this is the cryptographic agility guarantee. However, the asymmetric envelope field for `ephemeral_pubkey` (currently 65 bytes for P-256 uncompressed) must be generalised for hybrid and pure-ML-KEM variants:

**Asymmetric envelope — field layout change for PQC algorithm IDs:**

| Field | P-256 (current) | Hybrid (new) | Pure ML-KEM (new) |
|---|---|---|---|
| `ephemeral_pubkey` | 65 bytes (P-256 SEC1) | 65 bytes (P-256) + 800 bytes (ML-KEM-768 ek) = 865 bytes | 800 bytes (ML-KEM-768 ek) |
| `kem_ciphertext` | (implicit — none) | 0 bytes (P-256 shared secret via ECDH) + 1088 bytes (ML-KEM-768 ct) = 1088 bytes appended | 1088 bytes |
| `nonce` | 12 bytes | 12 bytes | 12 bytes |
| `ciphertext` | variable | variable | variable |
| `auth_tag` | 16 bytes | 16 bytes | 16 bytes |

**Implementation note for codec authors:** The `alg_id` field unambiguously identifies which layout to parse. Old clients encountering a new algorithm ID will fail loudly (by the spec's "unknown IDs must fail loudly" rule) — this is safe. No existing envelope is affected.

**KDF note:** For the hybrid scheme, both the P-256 ECDH shared secret and the ML-KEM-768 shared secret are combined via HKDF-SHA-256: `HKDF(IKM = P256_SS || MLKEM_SS, salt = context, info = alg_id)`. The concatenation ensures an attacker must break both algorithms simultaneously to recover the wrapped key.

---

## Migration phases — detailed specification

### Overview

```
Phase 0: Hybrid key codec implemented on all platforms (no user impact)
Phase 1: ML-KEM keypair generation and upload on first auth post-Phase-0
Phase 2: Master key re-wrap to hybrid scheme (client-side, on next auth, silent)
Phase 3: Full master key rotation and DEK re-wrap (background service)
Phase 4: Shared plot key and item sharing DEK re-wrap (coordinated, background)
```

All phases run in order. Phases 0–1 are pure infrastructure (no user sees anything). Phase 2 is triggered automatically on the user's next authenticated session. Phases 3–4 run as background services on the server, triggered by client completion of Phase 2.

---

### Phase 0: Hybrid key codec

**Goal:** All platforms can generate, serialise, deserialise, and use `hybrid-p256-mlkem768-hkdf-aes256gcm-v1` envelopes. No production traffic uses this format yet.

**Completion criteria:** Cross-platform round-trip tests pass for the new algorithm ID. Server can validate the structural integrity of a hybrid-format envelope (correct byte lengths, valid algorithm ID).

**Android (BouncyCastle):**
- Add `bcprov-jdk18on:1.84` (or latest) to the app's `implementation` dependencies.
- Register `BouncyCastleProvider` for ML-KEM operations: `Security.insertProviderAt(BouncyCastleProvider(), 1)`.
- ML-KEM keypair: `KeyPairGenerator.getInstance("ML-KEM", "BC")` with `MLKEMParameterSpec.ML_KEM_768`.
- Encapsulation: `KeyGenerator.getInstance("ML-KEM", "BC")` using recipient's ML-KEM public key. Yields shared secret + ciphertext.
- Decapsulation: Using the private key and received ciphertext.
- Combine with P-256 shared secret via HKDF-SHA-256 as per the KDF note above.

**iOS (CryptoKit, iOS 26+ only):**
- `MLKEM768.PrivateKey()` for key generation. Also available via `SecureEnclave.MLKEM768` for hardware-isolated keys.
- Apple provides `XWingMLKEM768X25519` as a combined hybrid construction — consider adopting this directly as it is formally verified and hardware-accelerated.
- Minimum deployment target will increase to iOS 26 for any feature using ML-KEM. Clients on iOS < 26 must fall back to P-256 (`p256-ecdh-hkdf-aes256gcm-v1`). The server must continue accepting P-256 envelopes from older clients.

**Web (WASM bridge):**
- Add `mlkem-wasm` (github.com/dchest/mlkem-wasm) as a production dependency. Single JS file, 17 KB gzipped, no external WASM files needed.
- The WebCrypto standard does not include ML-KEM as of May 2026; the WICG draft specification exists but is not yet implemented in browsers.
- Alternative: `noble-post-quantum` (paulmillr/noble-post-quantum) — pure JavaScript, audited, supports ml_kem768.
- `mlkem-wasm` is the preferred choice: smaller footprint, WASM-based performance, ships as a single self-contained ES module.
- When the WebCrypto standard ships ML-KEM (expected 2026–2027), migrate to the native API and remove the WASM dependency.

**Server (Kotlin/http4k):**
- No cryptographic implementation required. Server validates structure only.
- Update `EnvelopeFormat.kt` `KNOWN_ALGORITHM_IDS` to include `hybrid-p256-mlkem768-hkdf-aes256gcm-v1` and `mlkem768-hkdf-aes256gcm-v1`.
- Validate that `ephemeral_pubkey` field lengths are consistent with the declared algorithm ID. Reject mismatched lengths with 400.

---

### Phase 1: ML-KEM keypair generation and upload

**Goal:** Every authenticated device has an ML-KEM-768 keypair on the server alongside its existing P-256 sharing keypair.

**Trigger:** On first authenticated session after Phase 0 ships. Client checks: "Do I have an `mlkem768` entry in my sharing keypairs? If not, generate and upload."

**Protocol:**
1. Client generates `(mlkem_pk, mlkem_sk)` using the platform codec from Phase 0.
2. `mlkem_sk` is wrapped under the device's existing master key using `master-aes256gcm-v1` (symmetric wrap — the master key is already available on this authenticated session). This is safe: wrapping a PQC private key symmetrically under an AES-256 master key does not introduce any classical-to-PQC dependency issues.
3. Client uploads: `PUT /v1/devices/{deviceId}/sharing-keys` with `{ algorithm: "mlkem768", public_key: <base64>, wrapped_private_key: <envelope> }`.
4. Server stores in a new `device_sharing_keys` table (or appends to the existing sharing key row — TBD by Technical Architect based on current schema).
5. Client also stores `mlkem_sk` in local secure storage (Android Keystore, iOS Keychain, IndexedDB encrypted under the master key on web).

**Server schema addition (required):**

```sql
ALTER TABLE devices ADD COLUMN mlkem_public_key BYTEA;
ALTER TABLE devices ADD COLUMN wrapped_mlkem_private_key BYTEA;
-- or: a separate device_sharing_keys table if the schema needs extensibility for future algorithms
```

**Notes:**
- This phase does not change how existing wrapped master keys work. The P-256 keypair continues to be used for all wrapping.
- Phase 1 is a precondition for Phase 2. The server should block Phase 2 re-wrap requests from devices that have not completed Phase 1.

---

### Phase 2: Master key re-wrap (client-side, on next auth)

**Goal:** Each device's wrapped master key is re-wrapped from `p256-ecdh-hkdf-aes256gcm-v1` to `hybrid-p256-mlkem768-hkdf-aes256gcm-v1`. This closes Layer 2 of the attack window (existing key wrapping) for that device.

**Trigger:** On the next authenticated session after Phase 1 is complete for that device.

**Protocol:**
1. Client authenticates normally; master key is available in memory.
2. Client generates a new hybrid-wrapped blob of the master key using `hybrid-p256-mlkem768-hkdf-aes256gcm-v1`. Uses the device's existing P-256 keypair and newly generated ML-KEM keypair as the recipient keys.
3. Client uploads the new wrapped master key blob: `PUT /v1/devices/{deviceId}/wrapped-master-key` with algorithm `hybrid-p256-mlkem768-hkdf-aes256gcm-v1`.
4. Server replaces the old wrapped master key row. The old P-256-wrapped blob is deleted from the server immediately upon successful upload of the hybrid blob.
5. Client flags local state: `masterKeyMigrationPhase = 2`.

**This is silent.** No user prompt, no visible UX change. The user is already authenticated; the re-wrap takes milliseconds.

**Failure handling:**
- If upload fails (network error), retry on the next authenticated session. The old P-256 blob is not deleted until the new hybrid blob is confirmed uploaded.
- If the device does not complete Phase 2 within N days of Phase 0 shipping (the grace period — suggest 180 days), the server should flag the device as "migration pending" and eventually (after further grace) require completion before granting vault access.

**Backward compatibility:** Devices on older client versions that cannot parse `hybrid-p256-mlkem768-hkdf-aes256gcm-v1` must be prompted to update before accessing the vault. This is the expected forcing function for client update adoption.

---

### Phase 3: Full master key rotation and DEK re-wrap

**Goal:** Generate a new master key; re-wrap all DEKs under the new master key; wrap the new master key under the hybrid scheme; delete the old master key. This closes Layer 3 of the attack window for data generated after the user's first authenticated session under Phase 2.

**Context:** Phase 2 re-wraps the *same* master key under a hybrid scheme. Any adversary who harvested the P-256-wrapped master key before Phase 2 completes, and who later breaks P-256, can still derive the original master key. Phase 3 generates a new master key, making the old one permanently irrelevant.

**Trigger:** Background service, initiated server-side after Phase 2 completion is confirmed for a device. Requires the user to have an active authenticated session to perform the DEK re-wrap (master key must be in memory). The background service coordinates with the client.

**Protocol:**
1. Server signals to client (on next auth): "You are Phase 2 complete. Initiate master key rotation."
2. Client generates a new 256-bit master key `MK2`.
3. Client fetches all DEK blobs from the server (all `wrapped_item_dek` and `wrapped_thumbnail_dek` rows where this user is the vault owner).
4. For each DEK blob: decrypt with old master key → re-encrypt under `MK2` using `master-aes256gcm-v1`. Upload the new wrapped DEK.
5. Client wraps `MK2` under the hybrid scheme (`hybrid-p256-mlkem768-hkdf-aes256gcm-v1`) and uploads to replace all existing wrapped master key rows for all of this user's devices.
6. Upon server confirmation that all DEKs are re-wrapped and new master key blobs are uploaded, client signals completion. Old master key blobs are deleted.

**Rate limiting (see background re-wrap service spec §below).**

**What this closes:** Any adversary who harvested P-256-wrapped key material before Phase 2 and then breaks P-256 will recover the old master key `MK1`. But `MK1` no longer wraps any DEK on the server — all DEKs are now wrapped under `MK2`, and `MK2` is wrapped under the hybrid scheme (which requires breaking ML-KEM-768 in addition to P-256). Phase 3 effectively makes the old P-256 attack surface worthless.

**What this cannot close:** If an adversary harvested DEK blobs *before* Phase 3 completes and *also* harvested the wrapped master key *before* Phase 2 completes, they have both halves. Full master key rotation is not retroactively protective for data captured before Phase 2. See the HNDL honest communication section.

---

### Phase 4: Shared plot key and item sharing DEK re-wrap

**Goal:** All plot membership keys and item sharing DEKs that were wrapped to P-256 sharing pubkeys are re-wrapped to hybrid sharing pubkeys. This closes the HNDL window for shared data.

**Complexity:** Phase 4 is the most complex phase because shared keys involve multiple parties. A plot owner cannot unilaterally re-wrap a plot key to a member's hybrid pubkey — the member must have completed Phase 1 (uploaded their ML-KEM public key) first.

**Protocol:**
1. Server-side background service identifies: all `plot_members.wrapped_plot_key` rows that are still wrapped under `p256-ecdh-hkdf-aes256gcm-v1` where the member device has completed Phase 1.
2. For each eligible member, the plot owner is prompted to re-wrap the plot key (requires the plot owner to have an authenticated session with the master key in memory to unwrap their copy of the plot key, then re-wrap it for the member using `hybrid-p256-mlkem768-hkdf-aes256gcm-v1`).
3. Item sharing DEKs (`plot_items.wrapped_item_dek` using `p256-ecdh-hkdf-aes256gcm-v1` for per-recipient wraps) are re-wrapped similarly.

**Owner-triggered vs background:**
- Prefer owner-triggered on next active session: when the plot owner authenticates, the client detects pending plot key re-wraps and performs them in the background.
- This avoids a server-side key operation and preserves the "server never holds plaintext keys" invariant.

**Coordination timeout:** If the plot owner does not complete Phase 2+ within 90 days of Phase 0 shipping, the server should mark the plot as "PQC migration blocked — owner action required." Members can still read existing content (under old key wrapping) but new items require the owner to complete migration.

---

## Background re-wrap service specification

### Architecture

The background re-wrap service is a server-side coordination component only — it does not perform any cryptographic operations. All crypto happens on client devices. The service tracks migration state and coordinates client actions.

**Migration status column (to be added to `wrapped_keys` or equivalent table):**

```sql
ALTER TABLE devices ADD COLUMN migration_status TEXT NOT NULL DEFAULT 'phase0_pending';
-- Valid values: 'phase0_pending', 'phase1_complete', 'phase2_complete', 'phase3_complete', 'phase4_complete'
-- Or use a migration_phase INTEGER: 0, 1, 2, 3, 4

ALTER TABLE devices ADD COLUMN migration_started_at TIMESTAMPTZ;
ALTER TABLE devices ADD COLUMN migration_completed_at TIMESTAMPTZ;
```

### Priority ordering

Re-wrap prompts are issued to recently-active users first:
1. Users who authenticated in the last 7 days (highest priority — Phase 2 prompt delivered on next login).
2. Users who authenticated in the last 30 days.
3. Users who authenticated in the last 90 days.
4. Dormant users (last auth > 90 days ago) — no active prompt; Phase 2 occurs naturally when they next log in.

### Rate limiting

Phase 3 (DEK re-wrap) involves uploading many small blobs. For a user with 10,000 photos, this means 10,000 re-encrypted DEK blobs uploaded. Rate limit the re-wrap operation to avoid user-visible slowness or server overload:

- Client-side: batch DEK re-wraps in groups of 100, with a 500ms pause between batches.
- Server-side: accept re-wrap batches at up to 500 DEKs per minute per user. Reject excess with 429.
- Background: run Phase 3 during an idle authenticated session (screen on, connected to WiFi, not actively uploading). Pause on metered network.

Estimated Phase 3 duration at 500 DEKs/minute: 10,000 photos = 20 minutes background. This is acceptable for a once-ever migration.

### Failure and retry

- All phases are idempotent: re-running Phase 2 produces the same result (re-wraps the same master key under the same hybrid envelope).
- Phase 3 is resumable: track which DEKs have been re-wrapped via a server-side `dek_migration_status` column. On resume, skip already-re-wrapped DEKs.
- Maximum retries: 5 per session, then back off exponentially. Alert user if Phase 3 remains incomplete after 30 days.
- Phase 3 failures that leave some DEKs under old wrapping and some under new wrapping are safe: both DEK versions decrypt correctly; the migration is simply incomplete.

### Progress tracking schema additions

```sql
-- Track per-device migration phase
ALTER TABLE devices ADD COLUMN pqc_migration_phase INTEGER NOT NULL DEFAULT 0;
-- 0 = pre-migration (P-256 only)
-- 1 = ML-KEM keypair uploaded
-- 2 = master key re-wrapped to hybrid
-- 3 = master key rotated, all DEKs re-wrapped
-- 4 = shared plot keys and sharing DEKs re-wrapped

-- Track per-DEK re-wrap status (Phase 3)
ALTER TABLE uploads ADD COLUMN dek_pqc_rewrapped BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE uploads ADD COLUMN dek_pqc_rewrapped_at TIMESTAMPTZ;

-- Aggregate migration progress per user (denormalised for reporting)
ALTER TABLE users ADD COLUMN pqc_min_device_phase INTEGER;
-- Set by trigger or background job: min(devices.pqc_migration_phase) for this user's devices
```

---

## Server-side migration enforcement

### Timeline (from announcement date)

| Days from announcement | Server action |
|---|---|
| Day 0 | Phase 0 ships in client update. Server accepts (but does not require) hybrid algorithm IDs. Old P-256 algorithm IDs remain fully supported for both reads and writes. |
| Day 1–30 | Grace period. No enforcement. Monitor migration progress in DB. |
| Day 30 | Server stops accepting new `p256-ecdh-hkdf-aes256gcm-v1` write operations for master key wrapping. Existing P-256 blobs remain readable. Clients on old versions cannot update their wrapped master key without upgrading. |
| Day 90 | New device registrations require `hybrid-p256-mlkem768-hkdf-aes256gcm-v1` or better for the initial master key wrap. Old device logins still work if they have not yet re-wrapped (Phase 2 trigger fires on login). |
| Day 180 | Server blocks vault access for devices with `pqc_migration_phase < 2`. User sees a "Please open the app and complete your security update" message. This forces Phase 2 completion. Phase 2 takes seconds — it is not disruptive, just requires an authenticated session. |
| Day 365 | Old `p256-ecdh-hkdf-aes256gcm-v1` envelopes become read-only (no new wraps). Deprecation logged in server metrics. |
| Day 365+ | Ongoing: monitor Phase 3/4 completion per user. No forced deadline for Phase 3/4 — these are long-running background operations and there is no urgency after Phase 2 is complete. Phase 3/4 can run over months. |

### Algorithm ID deprecation policy

Three states for each algorithm ID:

1. **Active (read + write):** Server accepts the ID for new encryptions and validates existing blobs.
2. **Deprecated (read-only):** Server rejects new writes with this ID (400 Bad Request, body: `{ "error": "algorithm_deprecated", "id": "<alg_id>", "successor": "<new_alg_id>" }`). Old blobs remain readable.
3. **Tombstoned:** After all blobs of this type are confirmed re-wrapped (Phase 3 complete for all users), the ID can be removed from the KNOWN_ALGORITHM_IDS list. Until then, keep in read-only mode.

`p256-ecdh-hkdf-aes256gcm-v1` transitions: Active → Deprecated (Day 30) → Read-only forever (until all vaults are fully migrated) → eventually Tombstoned (years hence, if ever needed).

### Write-rejection response format

```
HTTP 400 Bad Request
{
  "error": "algorithm_deprecated",
  "algorithm_id": "p256-ecdh-hkdf-aes256gcm-v1",
  "minimum_required": "hybrid-p256-mlkem768-hkdf-aes256gcm-v1",
  "upgrade_url": "https://heirlooms.digital/security/pqc-migration"
}
```

---

## Cross-platform implementation scope

### Android

**Library:** BouncyCastle `bcprov-jdk18on:1.84` (or latest release from Maven Central).

**Available now.** ML-KEM support has been present since v1.79 (August 2024). As of v1.84, Java 17 users can access ML-KEM via the standard `javax.crypto.KEM` API. Android using BouncyCastle as an explicit provider (not the system provider) has full access.

**Key operations:**
- Keypair generation: `KeyPairGenerator.getInstance("ML-KEM", "BC")` with `MLKEMParameterSpec.ML_KEM_768`.
- Encapsulation: `KEM.getInstance("ML-KEM", "BC")` using recipient's public key.
- Decapsulation: Using the private key + received ciphertext.
- Hybrid KDF: Combine P-256 shared secret and ML-KEM shared secret via `HKDF-SHA-256` (already in use in the existing codec).

**Scope of change:**
- New `HybridKemCodec.kt` implementing the `hybrid-p256-mlkem768-hkdf-aes256gcm-v1` encapsulation/decapsulation.
- New `MlKemCodec.kt` for the pure `mlkem768-hkdf-aes256gcm-v1` (Phase 3+).
- Registration of both codecs in `EnvelopeFormat.kt`'s algorithm dispatch table.
- Secure storage of ML-KEM private key in Android Keystore (EncryptedSharedPreferences or KeyStore-backed AES wrap). Note: Android Keystore does not natively support ML-KEM as of Android 15; wrap the ML-KEM private key under a Keystore-backed AES key.
- Phase 1 upload: new API call to upload the ML-KEM public key and wrapped private key to the server.

**Risk:** Low. BouncyCastle is already a dependency for P-256 operations. Adding ML-KEM uses the same library.

---

### iOS

**Library:** Apple CryptoKit (iOS 26+ / macOS Tahoe+, available late 2026).

**Not yet available for users on iOS < 26.** This is the critical deployment constraint for the iOS platform. Heirlooms iOS currently supports iOS 16+. Requiring iOS 26 for PQC features will exclude users on older devices/OS versions for a transition period.

**Recommended approach:**
- Implement conditional compilation: `#available(iOS 26, *)` guard around all ML-KEM code paths.
- Users on iOS < 26 remain on P-256 until they upgrade their OS. The server must continue accepting P-256 for these users.
- Do not block vault access for iOS < 26 users during the 180-day grace period. Extend their deadline until iOS 26 adoption reaches sufficient levels (suggest monitoring: when < 5% of iOS users are on < iOS 26, then enforce).

**Key API (iOS 26+):**
- `MLKEM768.PrivateKey()` — generates a new ML-KEM-768 keypair.
- `SecureEnclave.MLKEM768.PrivateKey()` — Secure Enclave-backed keypair (preferred for long-lived device keys).
- `XWingMLKEM768X25519` — Apple's combined X-Wing hybrid (X25519 + ML-KEM-768). Consider adopting directly for the `hybrid` scheme instead of composing manually — it is formally verified and benefits from hardware acceleration.
- Encapsulation and decapsulation follow the standard `KEM` protocol.

**Recommendation:** Adopt `XWingMLKEM768X25519` (X-Wing) as the iOS implementation of `hybrid-p256-mlkem768-hkdf-aes256gcm-v1`. X-Wing uses X25519 rather than P-256 — the Technical Architect must confirm whether this divergence from the Android/server P-256 usage is acceptable, or whether the iOS implementation should use a P-256+ML-KEM composition instead. If Heirlooms uses X25519 on iOS and P-256 on Android, the public keys stored on the server are in different formats; the server must store both. This is manageable but adds schema complexity. **Decision required from Technical Architect before iOS Phase 0.**

**Secure Enclave option:** Using `SecureEnclave.MLKEM768` means the ML-KEM private key never leaves the Secure Enclave in plaintext. This is strictly better than software-only storage but means the key cannot be backed up or transferred. This is the same tradeoff as the existing P-256 device key. Recommend Secure Enclave for new installations on iOS 26+.

---

### Web

**Library:** `mlkem-wasm` (github.com/dchest/mlkem-wasm) as a stopgap.

**WebCrypto gap:** The Web Cryptography API (W3C standard, implemented in all browsers) does not include ML-KEM as of May 2026. The WICG "Modern Algorithms in the Web Cryptography API" specification is in draft but not yet adopted by browsers. Node.js v24.7.0 (August 2025) added ML-KEM to its WebCrypto implementation — this benefits server-side Node.js but not browser clients.

**mlkem-wasm characteristics:**
- Implements ML-KEM-768 via WebAssembly compiled from `mlkem-native` (a memory-safe, type-safe C implementation).
- Exposes a WebCrypto-compatible API (matching the WICG draft spec) so migration to native WebCrypto requires only a dependency swap.
- Single ES module, 53 KB unminified (17 KB gzipped / 14 KB brotli). Acceptable for web use.
- Works in browsers and Node.js.
- No external `.wasm` files — WASM is inlined in the JS file.

**Alternative:** `noble-post-quantum` (paulmillr/noble-post-quantum) — pure JavaScript, audited by Trail of Bits, no WASM dependency. Slightly larger but avoids the WASM trustchain. Either is acceptable; `mlkem-wasm` has better performance.

**Migration path:** When the WebCrypto standard adds ML-KEM (expected when WICG draft is adopted — likely 2026–2027 at current pace), replace the WASM call with `crypto.subtle.generateKey("ML-KEM-768", ...)`. The algorithm ID and protocol remain identical.

**Scope of change:**
- New `hybridKemCodec.ts` using `mlkem-wasm` for ML-KEM operations + existing WebCrypto ECDH for P-256.
- Register new algorithm IDs in the web `EnvelopeFormat.ts` dispatch table.
- Store ML-KEM private key: encrypt under the master key (same as other sensitive material on web) and store in IndexedDB. WebCrypto `CryptoKey` objects for ML-KEM may not be available until the standard ships; use raw bytes wrapped under the AES master key as an interim.

---

### Server

**Scope: minimal.** The server performs structural validation only and never decrypts.

**Required changes:**
1. Add `hybrid-p256-mlkem768-hkdf-aes256gcm-v1` and `mlkem768-hkdf-aes256gcm-v1` to `KNOWN_ALGORITHM_IDS` in `EnvelopeFormat.kt`.
2. Update the asymmetric envelope validator to accept variable-length `ephemeral_pubkey` fields based on the declared algorithm ID (current code assumes 65 bytes for P-256 uncompressed).
3. Add `pqc_migration_phase` column to `devices` table (schema addition, Flyway migration).
4. Add `dek_pqc_rewrapped` column to `uploads` table.
5. New endpoints: `PUT /v1/devices/{id}/mlkem-public-key`, `PUT /v1/devices/{id}/pqc-migration-phase`.
6. Enforcement logic: check `pqc_migration_phase >= 2` when `migration_enforcement_enabled = true` (feature flag).

**BouncyCastle note:** BouncyCastle appears in `testImplementation` only in the current server. Do not add it to `implementation`. The server does not perform PQC operations; the test-only usage for reference envelope generation is fine and should not expand.

---

## HNDL honest communication recommendation

### What the migration can promise

| Claim | Accurate? | Notes |
|---|---|---|
| "All data generated after [Phase 0 date] is protected against 'harvest now, decrypt later' attacks." | Yes | Hybrid key wrapping means an attacker must break ML-KEM-768 as well as P-256 — no currently known quantum algorithm breaks ML-KEM. |
| "Your account key is protected against quantum attack." | Yes (after Phase 2) | Once the master key is re-wrapped under the hybrid scheme, it cannot be decrypted using P-256 quantum attack alone. |
| "All your data is protected against quantum attack." | Only after Phase 3 | Phase 2 protects future key wraps; Phase 3 rotates the master key so old DEKs are no longer accessible via the old master key. |
| "Your data that was encrypted before [Phase 0 date] is fully protected." | **No.** | This is Layer 3 of the attack window. Data harvested before hybrid deployment cannot be retroactively protected. |

### What cannot be promised (and must not be overstated)

If a nation-state adversary harvested Heirlooms server traffic before Phase 0, they have:
- P-256-wrapped master key blobs (readable if P-256 is broken).
- AES-256-GCM encrypted DEKs (quantum-safe — not readable even with a quantum computer).
- AES-256-GCM encrypted file content (quantum-safe).

An adversary who breaks P-256 after Phase 0 but before Phase 3 completes for a given user gets:
- The old master key.
- Any DEKs still wrapped under the old master key (those not yet re-wrapped in Phase 3).
- From those DEKs, the file content.

After Phase 3 completes: the old master key is useless. All DEKs are under the new master key, which is wrapped under the hybrid scheme.

### Recommended user communication

Heirlooms should be honest in three tiers:

**Tier 1 — General users (in-app notification, simple language):**
> "We're upgrading Heirlooms to protect your family archive against future quantum computers. Starting [date], all new content is automatically protected. We'll also upgrade the security on your existing content in the background — this may take a few weeks and requires no action from you."

**Tier 2 — Privacy-conscious users (Help Centre article):**
> "We use a post-quantum hybrid encryption scheme (ML-KEM-768 + P-256) for all key operations. Content encrypted after [date] requires breaking both a classical and a post-quantum algorithm simultaneously — no current or anticipated quantum computer can do this. Content encrypted before [date] was protected by P-256 only. If a quantum computer were to break P-256 in the future, an attacker who had previously captured our encrypted traffic could, in theory, access that older content. We are rotating all keys in the background to remove this risk for your existing vault. The rotation will complete within [N] weeks of your next app login."

**Tier 3 — Enterprise / security-auditor audience (security whitepaper):**
Full honest disclosure of the three-layer attack window framework. Layer 3 cannot be retroactively closed for data captured before hybrid deployment. This is true of every product that has deployed encryption before the PQC transition — it is not a Heirlooms-specific failure. The honest disclosure is that Heirlooms is actively migrating and has a documented, phased plan.

**What not to say:** Do not claim that enabling PQC "protects all your data." This is false for pre-Phase-0 data captured by an adversary. Signal, Proton Mail, and Apple all use similar hedged language — Heirlooms should do the same.

---

## Recommendations for Technical Architect

The following decisions are ready for Technical Architect action and do not require further CTO input:

1. **Reserve the two PQC algorithm IDs immediately.** Add `hybrid-p256-mlkem768-hkdf-aes256gcm-v1` and `mlkem768-hkdf-aes256gcm-v1` to `docs/envelope_format.md` under a "Reserved (PQC migration)" section. This takes one hour and prevents naming collisions if any developer independently explores PQC.

2. **Plan Phase 0 as a standalone deliverable.** Phase 0 (codec implementation + round-trip tests) can be scoped and dispatched as a normal task without any production deployment risk. It is purely additive code with no production traffic implications.

3. **Resolve the iOS X-Wing vs P-256+ML-KEM decision.** Apple ships X-Wing (X25519 + ML-KEM-768). The rest of Heirlooms uses P-256. Using X-Wing on iOS means the device public key format differs between Android and iOS. The server must store both a P-256 sharing pubkey and an X25519 pubkey per iOS device, or Heirlooms must unify on X25519 everywhere. This is the most important cross-platform decision before Phase 0 begins.

4. **Design the `device_sharing_keys` schema extension.** The current schema stores one P-256 sharing pubkey per device. Supporting multiple algorithms requires either: (a) additional columns on the `devices` table, or (b) a separate `device_public_keys` table keyed on `(device_id, algorithm)`. Option (b) is more extensible.

5. **Add `pqc_migration_phase` to the devices table now.** This costs nothing and provides a durable migration state anchor that will be needed once Phase 0 ships.

6. **Do not create a dependency on iOS 26 for Phase 0–2.** The WASM bridge makes web viable without waiting for the WebCrypto standard. Android is ready now. iOS 26 users get native CryptoKit; iOS < 26 users stay on P-256 temporarily.

**Decision required from CTO before Phase 0 can be dispatched:**
- Is the migration aligned with the upcoming iteration, or deferred until after M11?
- What is the grace period for old clients (suggested 180 days from Phase 0)?

---

## Companion summary for Security Manager

This section summarises the migration's security-relevant constraints and honest limits for the Security Manager's records.

### What the migration achieves

- **Layer 1 (future data):** Closed on Phase 0 ship date. All new key-wrapping operations use the hybrid scheme. HNDL adversaries capturing traffic after this date cannot benefit from a future P-256 break.
- **Layer 2 (existing key wrapping):** Closed per-device when Phase 2 completes for that device. Window duration = time between a hypothetical P-256 break announcement and the user's next authenticated session.
- **Layer 3 (HNDL for harvested data):** Closed per-user when Phase 3 completes. Cannot be retroactively closed for data harvested before Phase 0.

### What the migration cannot achieve

- Data harvested by an adversary before Phase 0 and *also* with the old master key blob harvested before Phase 2 is irrecoverably exposed if P-256 is broken. File content (AES-256-GCM) remains protected; only the key-wrapping layer is at risk.
- Users who never return after Phase 0 ships (dormant vaults) will never complete Phase 2/3. Their old P-256 key wrapping persists indefinitely on the server. The Security Manager should track the percentage of user-devices at each migration phase as a security KPI.
- The iOS < 26 population cannot complete Phase 0–2 until they update their OS. They remain on P-256. This is a known gap; the server must track these users and notify them when iOS 26 is available to them.

### Key security invariants to preserve

1. The server never holds a plaintext master key or ML-KEM private key at any phase.
2. Phase 3 DEK re-wrap is client-side only. The server never has access to the AES-256-GCM master key at any point in the migration.
3. Phase 2 is not merely a key algorithm change — it also produces a new key wrapping. If the existing P-256 private key is compromised (classical attack), a hybrid-wrapped master key is no safer unless a new device keypair is generated. The migration assumes device P-256 private keys are not classically compromised. If a device private key is suspected compromised, full device revocation (SEC-011) takes priority over migration.
4. Argon2id-wrapped recovery passphrase blobs (`argon2id-aes256gcm-v1`) are **not** addressed by this migration. They wrap the master key using a passphrase-derived symmetric key, not P-256. They are already quantum-safe (AES-256-GCM). No action required.

### NIST SP 800-88 Rev. 2 — key material deletion

NIST SP 800-88 Rev. 2 (September 2025) covers secure erasure of key material. For Heirlooms:
- Old P-256-wrapped master key blobs, once superseded by hybrid blobs (Phase 2), should be **immediately and verifiably deleted** from the server database — not archived, not soft-deleted. A hard delete with a commit log entry is sufficient for database records.
- Old DEK blobs (Phase 3) should be hard-deleted upon confirmation of successful re-wrap.
- The server should log a deletion event (with timestamp and device ID) for each deleted blob, for audit trail purposes.
- "Cryptographic erasure" (key destruction) is an accepted sanitisation technique under NIST 800-88. Deleting the P-256-wrapped master key blob achieves cryptographic erasure of the P-256-wrapped content, assuming the underlying storage medium is not separately compromised.

---

## Glossary additions

The following terms are new to this brief and should be appended to `docs/research/GLOSSARY.md`:

**DEK re-wrap** — The operation of decrypting a Data Encryption Key that was wrapped under one algorithm (e.g. AES-256-GCM under the old master key) and immediately re-encrypting it under the new algorithm (e.g. AES-256-GCM under the new master key). The DEK plaintext value is transiently in memory on the client device but never transmitted. Re-wrap cost is O(DEKs), not O(file bytes). See also: *Re-wrap*, *Phase 3*.

**HPKE** (Hybrid Public Key Encryption) — A standardised framework (RFC 9180) for public-key encryption combining a Key Encapsulation Mechanism (KEM), a Key Derivation Function (KDF), and an Authenticated Encryption with Associated Data (AEAD) scheme. HPKE is the recommended framework for post-quantum-hybrid encryption. Apple's CryptoKit iOS 26 high-level PQ API is based on HPKE. ML-KEM can serve as the KEM component; HKDF-SHA-256 as the KDF; AES-256-GCM as the AEAD.

**KDF (Key Derivation Function)** — A function that derives one or more secret keys from a shared secret (e.g. a Diffie-Hellman or KEM shared secret). In Heirlooms, HKDF-SHA-256 is the KDF used in the `p256-ecdh-hkdf-aes256gcm-v1` scheme. For hybrid key exchange, the KDF combines multiple shared secrets (P-256 and ML-KEM) into a single derived key, ensuring that breaking either input alone is insufficient.

**Migration phase** — In the Heirlooms PQC migration, a numbered stage of the migration plan: Phase 0 (codec implementation), Phase 1 (ML-KEM keypair upload), Phase 2 (master key re-wrap), Phase 3 (master key rotation and DEK re-wrap), Phase 4 (shared plot and sharing key re-wrap). Tracked in the `devices.pqc_migration_phase` column.

**ML-KEM-768 key sizes** — ML-KEM-768 parameter sizes relevant for implementation: encapsulation key (public key) = 1184 bytes; decapsulation key (private key) = 2400 bytes; ciphertext = 1088 bytes; shared secret = 32 bytes. Compare to P-256: public key = 65 bytes uncompressed, private key = 32 bytes, shared secret = 32 bytes. The larger key and ciphertext sizes require schema and wire format changes.

**SPQR** (Sparse Post-Quantum Ratchet) — Signal's implementation of a post-quantum ratchet mechanism deployed in October 2025. SPQR runs alongside the existing Double Ratchet protocol using ML-KEM-768, producing what Signal calls the Triple Ratchet. A related Heirlooms concept: if Heirlooms ever adds a messaging feature, SPQR is the reference design.

**X-Wing** — A general-purpose post-quantum hybrid KEM (draft-connolly-cfrg-xwing-kem-10) combining X25519 and ML-KEM-768. Provides the classical security of X25519 and the post-quantum security of ML-KEM-768 simultaneously. Adopted by Apple in CryptoKit (iOS 26) as `XWingMLKEM768X25519`. Not yet an RFC (I-D status as of March 2026, expiry September 2026). The Heirlooms hybrid scheme uses P-256 (not X25519) as the classical component on Android/web — the Technical Architect must decide whether to adopt X25519 uniformly to enable X-Wing adoption on all platforms.

**XWing (iOS alias)** — See *X-Wing*.

---

## New references (append to REFERENCES.md format)

## RES-003 — 2026-05-16 — PQC migration readiness brief

**[RES-003-001]** https://csrc.nist.gov/pubs/fips/203/final  
NIST FIPS 203 final publication (August 2024): ML-KEM (Module-Lattice-Based Key-Encapsulation Mechanism Standard). Final standard, not a draft. Specifies ML-KEM-512, ML-KEM-768, ML-KEM-1024.

**[RES-003-002]** https://developer.apple.com/documentation/cryptokit/mlkem768  
Apple Developer Documentation: `MLKEM768` in CryptoKit — key generation, encapsulation, decapsulation API. iOS 26 / macOS Tahoe.

**[RES-003-003]** https://developer.apple.com/videos/play/wwdc2025/314/  
Apple WWDC 2025 session 314: "Get ahead with quantum-secure cryptography." Covers CryptoKit ML-KEM API, X-Wing, Secure Enclave support, formally verified implementation, iOS 26 TLS integration.

**[RES-003-004]** https://csrc.nist.gov/pubs/sp/800/227/final  
NIST SP 800-227: Recommendations for Key-Encapsulation Mechanisms (September 2025). Endorses hybrid deployment (X25519 + ML-KEM) during PQC transition; provides X-Wing as example. Operational guidance complementing FIPS 203.

**[RES-003-005]** https://www.encryptionconsulting.com/microsoft-and-apple-advance-post-quantum-cryptography-support-in-upcoming-os-releases/  
Encryption Consulting, 2025: Analysis of Apple iOS 26 and Microsoft Windows PQC support. CryptoKit ML-KEM-768 and X-Wing availability.

**[RES-003-006]** https://github.com/dchest/mlkem-wasm  
mlkem-wasm GitHub repository: ML-KEM-768 in WebAssembly, 17 KB gzipped, single ES module, WebCrypto-compatible API. Stopgap for browser deployment before native WebCrypto support.

**[RES-003-007]** https://proton.me/blog/introducing-post-quantum-encryption  
Proton, May 2026: Post-quantum encryption rollout for all Proton Mail users. Uses ML-KEM-768 + X25519 composite (OpenPGP v6 algorithm 35). Does not retroactively re-encrypt existing mailbox content; future re-encryption planned.

**[RES-003-008]** https://www.helpnetsecurity.com/2026/05/06/proton-mail-post-quantum-protection-feature/  
Help Net Security, May 2026: Coverage of Proton Mail PQC rollout — technical details, scope, roadmap for retroactive re-encryption.

**[RES-003-009]** https://signal.org/blog/spqr/  
Signal Blog, October 2025: "Signal Protocol and Post-Quantum Ratchets." SPQR (Sparse Post Quantum Ratchet) using ML-KEM-768 alongside the Double Ratchet protocol, forming the Triple Ratchet. Gradual rollout, backward-compatible.

**[RES-003-010]** https://engineering.fb.com/2026/04/16/security/post-quantum-cryptography-migration-at-meta-framework-lessons-and-takeaways/  
Meta Engineering Blog, April 2026: PQC migration framework with five maturity levels (PQ-Unaware → PQ-Enabled). Risk prioritisation, inventory, hybrid deployment, guardrails.

**[RES-003-011]** https://datatracker.ietf.org/doc/draft-connolly-cfrg-xwing-kem/  
IETF Datatracker: draft-connolly-cfrg-xwing-kem-10 (March 2026). X-Wing KEM specification — X25519 + ML-KEM-768 hybrid. Informational I-D, expiry September 2026. Adopted by Apple.

**[RES-003-012]** https://datatracker.ietf.org/doc/html/draft-ietf-hpke-pq-02  
IETF: Post-Quantum and Post-Quantum/Traditional Hybrid Algorithms for HPKE. Defines KEM algorithms for HPKE based on ML-KEM and hybrid combinations.

**[RES-003-013]** https://wicg.github.io/webcrypto-modern-algos/  
WICG: Modern Algorithms in the Web Cryptography API. Draft spec adding ML-KEM (and other PQC algorithms) to the WebCrypto standard. Not yet implemented in browsers as of May 2026.

**[RES-003-014]** https://csrc.nist.gov/pubs/sp/800/88/r2/final  
NIST SP 800-88 Rev. 2: Guidelines for Media Sanitization (September 2025). Supersedes Rev. 1. Cryptographic erasure (key deletion) is an approved sanitisation technique for encrypted data.

**[RES-003-015]** https://stateofsurveillance.org/news/harvest-now-decrypt-later-quantum-surveillance-threat-2026/  
State of Surveillance, 2026: HNDL threat update — encrypted data captured in 2026 may be decryptable as early as 2032 given quantum hardware trajectory. Nation-state active harvesting confirmed by multiple intelligence agencies.

**[RES-003-016]** https://github.com/paulmillr/noble-post-quantum  
noble-post-quantum GitHub repository: Auditable, minimal pure-JavaScript post-quantum cryptography. ML-KEM-768 support (ml_kem768). Alternative to mlkem-wasm for the web platform.

**[RES-003-017]** https://www.bouncycastle.org/resources/new-releases-bouncy-castle-java-1-84-and-bouncy-castle-java-lts-2-73-11/  
BouncyCastle release notes: Java 1.84 and LTS 2.73.11. ML-KEM available via `javax.crypto.KEM` API on Java 17+. ML-KEM, ML-DSA, SLH-DSA in LTS release. Maven Central available.

**[RES-003-018]** https://dchest.com/2025/08/09/mlkem-webcrypto/  
dchest blog, August 2025: ML-KEM in WebCrypto API — status of the WICG draft, current state of browser support, mlkem-wasm as stopgap.

**[RES-003-019]** https://hbr.org/sponsored/2026/01/why-your-post-quantum-cryptography-strategy-must-start-now  
Harvard Business Review, January 2026: Enterprise PQC migration urgency — NSA CNSA 2.0 deadlines (January 2027: new systems; 2030: full application migration; 2035: infrastructure). Market signal for enterprise-facing Heirlooms positioning.

---

## PA Summary

**For:** PA (to route to CTO and Technical Architect)  
**Urgency:** High — industry has moved to production; Heirlooms' P-256-only stance is becoming a visible gap

**Key findings:**
- The HNDL threat assessment from RES-001 is confirmed and unchanged. Nation-state adversaries are harvesting now. Q-Day for ECC remains credibly within 5–10 years. Data captured in 2026 could be decryptable by 2032.
- The industry has executed. Proton Mail (May 2026), Signal (October 2025), Apple CryptoKit (iOS 26), and Meta (PQC migration framework April 2026) are all in production with ML-KEM. Heirlooms using pure P-256 is now a visible gap versus peers.
- Platform readiness: Android (BouncyCastle 1.84) is ready now. iOS (CryptoKit iOS 26) is available from late 2026 but requires minimum OS version bump. Web requires a small WASM library (17 KB gzipped) as a stopgap until WebCrypto ships ML-KEM.
- Algorithm IDs are specified and ready to reserve: `hybrid-p256-mlkem768-hkdf-aes256gcm-v1` for the transitional hybrid scheme, `mlkem768-hkdf-aes256gcm-v1` for pure PQC post-transition.
- File content does not need re-encryption. Migration cost is O(keys), not O(files). Phase 2 (silent master key re-wrap) takes seconds per device. Phase 3 (DEK re-wrap for 10,000 photos) takes ~20 minutes as a background operation.

**Decisions needed from CTO:**
1. Is PQC migration aligned with the next iteration, or deferred until after M11 ships?
2. What is the grace period for old clients before vault access is blocked pending Phase 2 completion? (Suggested: 180 days from Phase 0 ship date.)
3. Should Heirlooms adopt X25519 uniformly (enabling X-Wing on all platforms) or stay on P-256 as the classical component (requiring manual hybrid composition on iOS)?

**Decisions needed from Technical Architect:**
1. iOS X-Wing vs P-256+ML-KEM decision (most urgent — blocks Phase 0 iOS design).
2. `device_sharing_keys` schema design (new table vs additional columns on `devices`).
3. Enforcement timeline confirmation (Day 30/90/180 milestones).

**Follow-on tasks created:**
- None as new research tasks. The output of this brief is actionable by the Technical Architect directly.
- Expected follow-on engineering tasks (to be created by PA/CTO):
  - `ARCH-008`: PQC envelope format amendment — reserve migration IDs in `docs/envelope_format.md`
  - `ARCH-009`: Schema design for `device_sharing_keys` and migration phase tracking
  - Phase 0 developer task (after CTO approves timeline)

---

## Research sources

Full numbered references in `docs/research/REFERENCES.md` under section RES-003.

Key sources: NIST FIPS 203 [RES-003-001]; Apple CryptoKit ML-KEM [RES-003-002, RES-003-003]; NIST SP 800-227 KEM guidance [RES-003-004]; mlkem-wasm [RES-003-006]; Proton Mail PQC rollout [RES-003-007, RES-003-008]; Signal SPQR [RES-003-009]; Meta PQC migration framework [RES-003-010]; X-Wing draft [RES-003-011]; WICG WebCrypto draft [RES-003-013]; NIST SP 800-88 Rev. 2 key deletion [RES-003-014]; HNDL 2026 update [RES-003-015]; BouncyCastle 1.84 [RES-003-017]; HBR enterprise urgency [RES-003-019].
