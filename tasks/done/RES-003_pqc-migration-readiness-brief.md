---
id: RES-003
title: PQC migration readiness — algorithm break response plan for Technical Architect
category: Research
priority: High
status: done
assigned_to: ResearchManager
depends_on: []
estimated: 1 session
---

## Goal

Produce a formal migration readiness brief for the Technical Architect covering how
Heirlooms' envelope format and key hierarchy evolve if P-256 (or another currently
deployed algorithm) is broken by quantum computing or classical cryptanalysis.

The CTO has outlined the conceptual approach (conversation 2026-05-16). This task
formalises it into a Technical Architect-ready brief with specific algorithm IDs,
migration phases, timeline targets, and schema changes.

## Context

The three-layer attack window framework (established in conversation 2026-05-16):

- **Layer 1 (future data):** Closed the day hybrid keys are deployed. No HNDL adversary
  can benefit from a future P-256 break for data generated after hybrid deployment.
- **Layer 2 (existing key wrapping):** Closed when each user's device re-wraps their
  master key under ML-KEM-768. Window duration = time between break announcement and
  user's next authenticated session.
- **Layer 3 (HNDL for harvested data):** Closed only by full master key rotation —
  generating a new master key, re-wrapping all DEKs, deleting the old master key.
  Cannot be retroactively closed for data already harvested before hybrid deployment.

Key architectural fact: **file content does not need re-encryption.** AES-256-GCM is
quantum-safe. Only the P-256 key-wrapping layer needs migration. Migration cost is
O(keys), not O(files).

## Research questions and brief scope

1. **Algorithm IDs to reserve:** What exact strings should be added to the envelope
   format spec for hybrid and pure-PQC key wrapping?
   - Candidates: `hybrid-p256-mlkem768-hkdf-aes256gcm-v1`, `mlkem768-hkdf-aes256gcm-v1`
   - Confirm naming convention is consistent with existing IDs

2. **Migration phases — detailed spec:**
   - Phase 0: Hybrid key codec implementation (platform-by-platform scope)
   - Phase 1: ML-KEM keypair generation and upload protocol
   - Phase 2: Master key re-wrap protocol (client-side, silent, on next auth)
   - Phase 3: Full master key rotation and DEK re-wrap (background service spec)
   - Phase 4: Shared plot key and item sharing DEK re-wrap (coordinated, owner-triggered)

3. **Background re-wrap service spec:**
   - Rate limiting (avoid user-visible performance impact)
   - Priority ordering (recently-active users first)
   - Progress tracking schema (`migration_status` column on wrapped_keys table)
   - Failure and retry handling

4. **Server-side migration enforcement:**
   - After N days post-announcement, force migration before granting access
   - Deprecation of old algorithm IDs in write operations
   - Read-only backward compatibility for old IDs

5. **Cross-platform implementation scope:**
   - Android: BouncyCastle ML-KEM-768 support
   - iOS: CryptoKit has no ML-KEM support — custom implementation or third-party library?
   - Web: WebCrypto has no ML-KEM support — WASM library assessment
   - Server: validation only (structural, not cryptographic) — minimal change

6. **The HNDL honest communication question:**
   Layer 3 cannot be retroactively closed for already-harvested data. The brief should
   recommend how Heirlooms communicates this to users — what can be promised, what cannot.

## Output

`docs/research/RES-003_pqc-migration-readiness-brief.md` covering all six areas above,
structured as a brief the Technical Architect can act on directly.

Also produce a companion summary for the Security Manager covering the honest
limits of the migration (what Layer 3 means for users with existing vaults).

## Completion notes

Completed 2026-05-16 by Research Manager.

**Brief produced:** `docs/research/RES-003_pqc-migration-readiness-brief.md`

**Research conducted:**
- NIST FIPS 203 (ML-KEM) final standard confirmed — no revision risk.
- NIST SP 800-227 (September 2025) reviewed — endorses hybrid (X25519+ML-KEM) deployment.
- BouncyCastle 1.84 confirmed ready: ML-KEM-768 via `javax.crypto.KEM` API, available on Maven Central.
- Apple CryptoKit iOS 26 confirmed: `MLKEM768` and `XWingMLKEM768X25519` with Secure Enclave support; formally verified implementation.
- WebCrypto confirmed: ML-KEM not yet in standard; `mlkem-wasm` (17 KB gzipped) confirmed as suitable stopgap.
- Industry state: Proton Mail (ML-KEM + X25519, May 2026), Signal SPQR Triple Ratchet (October 2025), Meta PQC migration framework (April 2026) all in production.
- HNDL timeline: 2026-captured data decryptable by ~2032 on optimistic quantum trajectory.
- NIST SP 800-88 Rev. 2 (September 2025): cryptographic erasure (key deletion) confirmed as approved sanitisation for old P-256 key blobs.

**Algorithm IDs formalised:**
- `hybrid-p256-mlkem768-hkdf-aes256gcm-v1` — transitional hybrid scheme
- `mlkem768-hkdf-aes256gcm-v1` — pure PQC post-transition

**Key open decision for Technical Architect:** iOS X-Wing (X25519+ML-KEM) vs P-256+ML-KEM hybrid — affects cross-platform public key format compatibility.

**Glossary additions:** DEK re-wrap, HPKE, KDF (PQC context), Migration phase, ML-KEM-768 key sizes, SPQR, X-Wing (7 new terms).

**References added:** [RES-003-001] through [RES-003-019] (19 new sources).

**Follow-on tasks recommended to CTO/PA:**
- `ARCH-008`: Reserve PQC algorithm IDs in `docs/envelope_format.md`
- `ARCH-009`: Schema design for device_sharing_keys and migration phase tracking
- Phase 0 developer task (after CTO confirms timeline vs M11)
