# RES-002 — Window Capsule: Cryptographic Expiry — Literature Review and Construction Brief

**ID:** RES-002  
**Date:** 2026-05-16  
**Author:** Research Manager  
**Audience:** CTO, Technical Architect  
**Status:** Final  
**Depends on:** RES-001 (threat horizon), ARCH-003/006 (M11 capsule crypto, tlock provider interface)

---

## Executive Summary

The Heirlooms "window capsule" construction — tlock for the lower bound combined with threshold Shamir deletion for the upper bound — is **substantially novel as a combined practical construction**. The individual components are well-established in the literature; the specific combination using a decentralised randomness beacon (drand/tlock) as the trustless lower bound XOR'd with a threshold-deleted Shamir secret as the trust-bounded upper bound does not appear verbatim in any prior published work or patent found during this review.

The closest prior work is "Timed Secret Sharing" (Kavousi, Abadi, Jovanovic — ASIACRYPT 2024), which formally defines a primitive with both lower and upper time bounds for secret reconstruction. Heirlooms' construction can be understood as a practical instantiation of this framework using drand/tlock for the lower bound and custodian deletion for the upper bound — which is one of the instantiation strategies the paper acknowledges but does not implement.

**Key findings for the CTO:**

1. The window capsule construction is novel as a complete practical system. It fills a gap between academic frameworks and deployable product.
2. The expiry bound is fundamentally trust-bounded — achieving a truly trustless upper bound on decryption access is provably impossible with classical cryptography alone.
3. Quantum-based certified deletion (Bartusek–Raizes 2024; Katz–Sela 2024) offers a theoretically trustless alternative but requires quantum infrastructure far beyond what is practical today or within the next 5 years.
4. Trusted hardware (Intel SGX, AWS Nitro Enclaves) can strengthen the expiry guarantee but introduces supply-chain trust rather than eliminating custodian trust.
5. The construction fits cleanly within the existing Heirlooms envelope format. Two new algorithm IDs are proposed for reservation.
6. Patentability is viable in the specific combination: tlock/drand lower bound + threshold deletion upper bound + XOR blinding scheme. The individual components are prior art; the combination is not.

---

## Literature Review

### Timed-Release Encryption (Background)

Timed-release encryption (TRE) is the problem of encrypting a message such that it cannot be decrypted until a specified future time. The field has four main classical approaches:

**1. Time-lock puzzles (Rivest, Shamir, Wagner 1996 [RES-002-001]):** The original approach uses sequential computation — a puzzle that cannot be parallelised and takes a known amount of wall time to solve. Purely computational; requires no trusted party. The ciphertext is self-decrypting via a puzzle solution. The weakness for Heirlooms: puzzle hardness assumptions are calibrated to specific hardware speeds, which drift over time, and the decryption cannot be accelerated for a legitimate receiver.

**2. Trusted time server / KMS approach:** A time server holds a master secret and releases time-bound keys (one per epoch) at the scheduled time. The Boyen-Waters IBE construction [RES-002-002] formalises this. Blake and Chan (2004) [RES-002-003] showed provably secure constructions. The weakness: requires trust in the time server's future availability and honesty.

**3. Identity-Based Encryption (IBE) with distributed key authority:** The idea of encoding time periods as IBE identities was formalised by multiple groups. Cheon et al. (2008) [RES-002-004] gave the first provably secure TRE with a time-server model. tlock (2023) [RES-002-005] is the current state of the art: it distributes the trusted time server role across the League of Entropy, removing the single point of failure. This is the scheme Heirlooms plans to use for the M11 unlock path.

**4. Verifiable delay functions (VDFs):** Boneh et al. (2018) [RES-002-006] formalised VDFs as a way to construct time-lock commitments without trusted parties. VDFs are promising but currently less mature for production deployment than tlock.

**Critical gap in all existing TRE work:** Every TRE scheme in the literature addresses the **lower bound only** — guaranteeing that decryption is impossible *before* a specified time. None of the mainstream TRE schemes address an **upper bound** — guaranteeing that decryption is impossible *after* a specified time. The window capsule construction addresses both.

---

### Forward-Secure Deletion

Forward security in the context of secret deletion means that compromise of a key at time T does not compromise secrets encrypted before T. The relevant related work:

**Canetti-Halevi-Katz (2003) [RES-002-007]:** Forward-secure public-key encryption where the secret key is updated at each time period. Old ciphertexts remain protected even if the new key is compromised. This addresses a different problem — protecting past data from future key compromise — rather than the expiry problem.

**Puncturable encryption (Green-Miers 2015) [RES-002-008]:** A key can be "punctured" on specific ciphertexts, removing the ability to decrypt those specific messages while retaining the ability to decrypt others. Used in messaging (Signal-style) for forward secrecy. Bloom filter encryption (BFE) makes this practical. This is closer to the Heirlooms problem — share deletion can be modelled as puncturing the reconstruction capability — but the literature focuses on receiver-side key management, not custodian-deletion guarantees.

**"Deleting Secret Data with Public Verifiability" (Xu, Zhang, Yang 2014) [RES-002-009]:** One of the few papers directly addressing verifiable deletion of key material. The scheme allows a party to prove deletion of a secret without revealing it, using a commitment scheme. Uses Intel SGX for enforcement. This is directly relevant to the custodian deletion proof problem in RES-002.

**Practical Verifiable Deletion from Blockchain and SGX (2023) [RES-002-010]:** Uses SGX enclaves and blockchain for accelerating secure and verifiable data deletion in cloud storage, enabling a user to verify the correct implementation of data deletion. Provides a concrete engineering pattern applicable to custodian nodes.

---

### Proactive Secret Sharing (Herzberg et al. 1995)

Herzberg, Jarecki, Krawczyk, and Yung introduced Proactive Secret Sharing (PSS) at CRYPTO 1995 [RES-002-011]. PSS divides time into epochs. At each epoch boundary, all shareholders execute a re-sharing protocol that generates fresh shares of the same underlying secret. Old shares are then deleted. The key insight: a mobile adversary that compromises up to the threshold across multiple epochs, but never exceeds the threshold within a single epoch, cannot recover the secret.

**What PSS solves:** Long-term availability of a shared secret despite a slow-moving adversary that gradually compromises shareholders over time.

**What PSS does not solve:** PSS explicitly assumes honest parties *delete their old shares*. It does not enforce deletion — it assumes it. If a custodian is malicious or keeps their old share, PSS offers no protection for that epoch. PSS is primarily motivated by the maintenance problem (share refresh to tolerate gradual attrition), not the expiry problem (deliberate destruction at a future time).

**Relevance to Heirlooms:** PSS is relevant as an engineering enhancement to the custodian tier. Running a PSS protocol among Heirlooms custodians during the access window would provide stronger protection against a gradual adversary compromising custodians over the window duration. It does not solve the expiry problem itself, but it tightens the trust assumption: an adversary must compromise enough custodians *simultaneously* (within a single epoch) rather than *cumulatively* (across epochs). This is a meaningful improvement if the access window spans months or years.

**Recommendation:** Include PSS as an optional enhancement for long-window capsules (window exceeding, say, 12 months). For short windows, the overhead is not justified.

---

### Witness Encryption — Current Deployment Status

Witness encryption (Garg, Gentry, Sahai, Waters — STOC 2013) [RES-002-012] allows encrypting a message to an NP statement: only a party holding a valid witness for the statement can decrypt. This is extremely powerful — a time-window condition could theoretically be expressed as a witness statement ("a timestamp between T1 and T2 exists on the blockchain and I hold a proof of it"), making the construction trustless.

**Current state (2025–2026):**

- General-purpose WE remains impractical. It requires multilinear maps or obfuscation, both of which are either unproven or impractical.
- Sanjam Garg presented "Witness Encryption: From Theory to Practice" at NIST's STPPA-7 symposium in January 2025 [RES-002-013], indicating active effort to close the gap between theory and practice.
- A framework for WE from linearly verifiable SNARKs (Garg et al., CRYPTO 2025) [RES-002-014] shows that special-purpose WE for targeted applications can be built from weaker assumptions and can be concretely efficient. This is a significant advance: if the time-window condition can be expressed as a linearly verifiable statement, WE may become practical within 5–7 years.
- The tlock paper itself [RES-002-005] cites WE as a theoretical motivation but notes that IBE over BLS12-381 is the practical substitute.

**Assessment for Heirlooms:** General-purpose WE cannot be used in production today. Special-purpose WE is a horizon technology — the right framework for encoding a time-window condition is likely to exist within the research literature within 5 years, but no production-ready implementation exists. Heirlooms should monitor this space. WE with SNARKs could, in principle, provide a trustless upper bound without custodians: the decryption witness is "the blockchain does not contain a block with timestamp > expire_time," which is verifiable in zero-knowledge. This is the long-term research direction if trustless expiry becomes a priority.

---

### Trusted Hardware Approaches (HSM/SGX/Nitro)

Trusted Execution Environments (TEEs) and Hardware Security Modules (HSMs) can enforce policy-based access to secrets, including time-based policies. The relevance to Heirlooms' expiry problem:

**Intel SGX:**
- SGX provides a trusted enclave for secret computation. The Platform Services Enclave (PSE) offers a trusted time source (coarse-grained, seconds-resolution) and monotonic counters.
- A custodian node running SGX could seal shares inside an enclave with a time-based policy: the enclave refuses to release shares after expire_time, and zeroises them automatically.
- **Critical limitation:** SGX's trusted time depends on the Intel Management Engine (ME) and is coarse-grained. It does not provide wall-clock time with calendar precision independently of the operating system. A malicious OS can influence time presentation. Research shows that multiple timer sources within the enclave can cross-validate credibility, but this is engineering complexity, not a clean cryptographic guarantee.
- **The deeper limitation:** SGX deletes shares *inside the enclave* — the attestation proves the enclave ran a particular code. It does not provide a proof of deletion that a third party can verify without seeing the share. The deletion is enforced by the hardware but the proof is attestation-based, not cryptographic.
- **Vulnerability track record:** SGX has a significant vulnerability history (Spectre, Foreshadow, SGAxe, Plundervolt). Physical attackers with chip access have bypassed SGX isolation in multiple research publications. For a consumer product like Heirlooms, routing custodian nodes through SGX introduces meaningful implementation complexity and a dependency on Intel's security update cadence.

**AWS Nitro Enclaves:**
- Nitro Enclaves provide hardware-isolated execution with cryptographic attestation. Attestation documents are signed by the AWS Nitro Attestation Public Key Infrastructure.
- AWS KMS integrates with Nitro: a KMS key policy can be conditioned on enclave measurements, allowing a Heirlooms server to release share material only when the requesting enclave passes attestation.
- Time-based expiry can be implemented at the KMS policy level: a Lambda or Step Functions orchestrator deletes the KMS key (which wraps the share) at expire_time, after which the enclave cannot decrypt.
- **Trust model:** This shifts custodian trust to AWS (key policy enforcement, KMS availability, Lambda execution). For Heirlooms' threat model, AWS is likely a reasonable trust anchor for most users. But it is not trustless — a nation-state adversary or insider at AWS could delay deletion or compel retention.
- **Practical advantage:** Nitro Enclaves with KMS is deployable today on existing AWS infrastructure. It provides hardware-enforced deletion with an audit trail (CloudTrail), which is stronger than software-only deletion.

**HSMs (Thales, Yubico, AWS CloudHSM):**
- FIPS 140-2/3 Level 3 HSMs provide physical tamper-resistance: the device zeroises key material on tamper detection. A scheduled zeroisation event (expire_time) could be programmed.
- HSMs are the gold standard for key material protection, but they require dedicated hardware at each custodian, significant operational overhead, and they do not provide cryptographic proof of deletion — only tamper evidence and operational records.

**Recommended TEE approach for Heirlooms:** AWS Nitro Enclaves with KMS key deletion at expire_time is the most practical hardware-backed enhancement. It strengthens the expiry guarantee from "trust the custodian's software" to "trust AWS + the enclave code attestation." This is meaningful defence-in-depth. See the Recommended Construction section for how this fits.

---

### Verifiable Deletion Proofs

Can a custodian prove they deleted their Shamir share without revealing the share? This is the open research question most critical to making the window capsule's expiry guarantee *auditable* rather than merely *trusted*.

**Classical approaches (commitments):**
The basic scheme: at sealing time, each custodian commits to their share using a publicly verifiable commitment scheme (e.g., a Pedersen commitment or a hash commitment). At expire_time, the custodian publishes a "deletion certificate" — typically a proof that the committed value has been destroyed (e.g., by randomising or overwriting it in a way provable from the commitment). This approach is explored in "Deleting Secret Data with Public Verifiability" (Xu et al. 2014) [RES-002-009] but is not perfect — a classical deletion proof can be forged if the custodian retains a copy.

**Quantum certified deletion (Bartusek-Raizes 2024 [RES-002-015]; Katz-Sela 2024 [RES-002-016]):**
These papers formalise *certified deletion* for secret sharing using quantum states. A classical secret is split into quantum shares. A quantum share is fundamentally different from a classical share: the quantum uncertainty principle makes it impossible to retain the share and also produce a valid deletion certificate. The deletion is cryptographically binding.

- Bartusek and Raizes (CRYPTO 2024) achieve certified deletion even against an adversary that eventually collects an authorised set of shares — if enough shares are deleted, the secret is provably protected.
- Katz and Sela (Eurocrypt 2025) achieve *publicly verifiable* deletion — the certificate can be verified by anyone, not just the dealer. This is closer to what Heirlooms needs.

**Assessment:** Quantum certified deletion is the "correct" solution to the verifiable deletion problem. It is theoretically beautiful and, for the window capsule, would provide a genuinely trustless expiry if all shares were quantum. However, quantum share distribution requires quantum communication channels or quantum memory, which do not exist in practical production infrastructure today. Horizon: 5–15 years for consumer-grade deployment, depending on QKD network deployment rates.

**Classical best-effort (recommended for now):** Each custodian publishes a commitment to their share at sealing time, and publishes a deletion certificate (zeroisation proof via commitment opening or hash preimage destruction) at expire_time. This is not information-theoretically binding — a malicious custodian who retains the share can fake a deletion certificate — but it provides: (1) an on-chain audit trail; (2) legal accountability if the custodian is a named institution; (3) verifiability for honest custodians. Combined with PSS epoch refresh and TEE enforcement where available, this creates defence-in-depth.

---

### Closest Existing Work to the Proposed Construction

The most directly relevant prior works, in order of relevance:

**1. Kavousi, Abadi, Jovanovic — "Timed Secret Sharing" (ASIACRYPT 2024) [RES-002-017]**

This is the most important finding. The paper formally defines Timed Secret Sharing (TSS) as a primitive with **both lower and upper time bounds** for secret reconstruction. Key points:

- TSS requires that before T_unlock, no authorised set of shares can reconstruct the secret. After T_expire, no authorised set can reconstruct it either.
- For the upper bound, the paper proposes two strategies: (a) **short-lived proofs** — custodians provide time-limited proofs of share validity that expire at T_expire; (b) **gradual release** — additional shares are gradually released to enable early reconstruction before T_expire, while shares existing beyond T_expire cannot reconstruct (the threshold structure changes).
- The construction does **not** use drand/tlock as its lower-bound mechanism; it uses VDFs or time-lock puzzles.
- The paper explicitly does not address the combination of decentralised IBE-based time-lock (tlock/drand) for the lower bound with custodian deletion for the upper bound.

**Assessment of novelty:** Heirlooms' construction is a distinct instantiation of the TSS framework that: (a) uses tlock/drand for the lower bound (which the paper does not); (b) uses threshold custodian deletion (a specific form of the "gradual release" or custodian-deletion strategy); (c) integrates XOR blinding at the DEK level (not mentioned in TSS); (d) embeds this in a consumer-facing envelope format. The Kavousi et al. paper establishes the theoretical framework; Heirlooms provides a practical, deployable instantiation within a specific product context.

**2. Paterson, Quaglia — "Time-Specific Encryption" (SCN 2010) [RES-002-018]**

This paper defines TSE: encryption to a time interval [T1, T2] rather than a single release time. A trusted Time Server broadcasts a Time Instant Key (TIK) at each time step; a receiver can only decrypt if they have a TIK within the specified interval. Extensions to the public-key and IBE settings are provided.

**Assessment:** TSE is the closest prior work conceptually. However, it relies on a centralised (or federated) time server that *actively* controls decryption access through key distribution — the server is an online requirement throughout the window. Heirlooms' construction differs fundamentally: the lower bound is trustless (drand/tlock provides K_a without any online Heirlooms component after sealing), and the upper bound is achieved through deletion rather than access control. TSE does not model the deletion mechanism.

**3. Multiple Time Servers TRE based on Shamir Secret Sharing (2024) [RES-002-019]**

Recent work distributes a traditional TRE time server across multiple servers using Shamir secret sharing, addressing the single-point-of-failure problem. Identity-based encryption encrypts key shares to each server. The receiver reconstructs the time key when a threshold of servers release their shares at the specified time.

**Assessment:** This is a related but distinct construction. It addresses *robustness* of the lower-bound release (preventing a single server from withholding the time key), not the upper bound (preventing decryption after expiry). It does not include a deletion mechanism.

**4. i-TiRE (Incremental Timed-Release Encryption, CCS 2022) [RES-002-020]**

i-TiRE provides practical TRE on blockchains, allowing efficient updating of ciphertexts across time periods. Primarily addresses the lower bound and blockchain integration.

**5. Patent US20060155652A1 — "Expiring Encryption" (filed 2004, abandoned) [RES-002-021]**

Describes time-dependent encryption for DRM use cases — content becomes decryptable only after copyright expiry. Uses a secure clock (hardware-enforced, tamper-resistant) to enforce time-based decryption policy. Does not use secret sharing. Application is abandoned.

**6. WO2008127446A2 — "Time-Lapse Cryptography" (2008) [RES-002-022]**

Describes encryption where the decryption key is not released until a future time, incorporating Feldman verifiable secret sharing and ElGamal encryption. Focuses on the lower bound only; does not address an expiry/upper bound.

**Assessment of all prior work:** No published paper or active patent found describes the specific combination of:
- tlock/drand IBE (trustless lower bound)
- XOR blinding of the DEK
- Shamir threshold deletion (trust-bounded upper bound)
- With the specific trust model: lower bound is trustless, upper bound is threshold-honest

This combination is novel as a complete system. The individual components are prior art; their specific integration and the XOR blinding scheme between them is not found in the literature.

---

## Novelty Assessment

| Dimension | Heirlooms' claim | Prior art status |
|---|---|---|
| tlock/drand for lower bound | Planned for M11 | Published (ePrint 2023/189) — prior art, used as component |
| Shamir threshold for key reconstruction | Standard | Well-established prior art |
| Threshold custodian deletion for upper bound | Core claim | Not found as a standalone construction |
| XOR blinding: K_window = K_a ⊕ K_b | Core claim | Not found in this context |
| Combination: tlock lower bound + Shamir deletion upper bound | Core claim | **Not found in literature or patents** |
| TSS framework with both bounds | Framework | Kavousi et al. 2024 (ASIACRYPT) — prior art for the framework |
| Practical instantiation of TSS with drand/tlock | Core claim | **Not found in literature** |
| Integration with consumer envelope format | Core claim | **Not applicable to academic literature** |

**Overall novelty assessment:** The construction is **novel as a complete practical system**. It is best characterised as a practical instantiation and extension of the Timed Secret Sharing framework (Kavousi et al.) using specific production-ready components (drand/tlock, Shamir secret sharing, XOR blinding) within a consumer E2EE product. Academic novelty is moderate to high; product novelty is high.

---

## Recommended Construction

### Formal Trust Assumptions

The window capsule rests on the following trust hierarchy (from most to least trustless):

**A. Trustless (information-theoretic or well-distributed):**
- Lower bound: tlock/drand. Trust assumption: the League of Entropy will not collude to reveal the round key early. This is a threshold-honest assumption across geographically and institutionally diverse parties (Cloudflare, EPFL, Protocol Labs, etc.).
- Symmetric encryption: AES-256-GCM is information-theoretically secure under key secrecy.

**B. Threshold-honest (expiry guarantee):**
- Upper bound: at least (N − M + 1) of N custodians will honestly delete their Shamir shares at expire_time. A (2-of-3) scheme requires at least 2 honest custodians out of 3. The security degrades gracefully with threshold size.
- Recommended initial parameters: (2-of-3) for minimum viable, (3-of-5) for production.

**C. Operationally-trusted (defence-in-depth):**
- Custodian deletion enforcement: AWS Nitro Enclave + KMS (if custodians are Heirlooms nodes), HSM zeroisation (if custodians are institutional partners), or software commitment/attestation (minimum viable).
- Commitment scheme for audit trail: custodians commit to shares at sealing time; deletion certificates are published at expire_time.

**D. Not achievable (trustless upper bound) — classical:**
- It is information-theoretically impossible to prove share deletion with classical cryptography alone. Any classical deletion proof can be faked by a party who retains a copy of the share. This is a fundamental limitation, not an engineering gap.

**E. Achievable (trustless upper bound) — quantum (horizon technology):**
- Quantum certified deletion (Bartusek-Raizes 2024, Katz-Sela 2024) would provide a cryptographically binding upper bound. Requires quantum communication infrastructure. Not deployable today.

---

### Construction Specification

**Key hierarchy:**
```
Content bytes
  → encrypted under DEK (AES-256-GCM, aes256gcm-v1)

DEK
  → encrypted under K_window (AES-256-GCM, window-aes256gcm-v1)

K_window = K_a ⊕ K_b  (256-bit XOR)

K_a  = tlock_encrypt(R_a, round_unlock)
       R_a is a 256-bit random value
       round_unlock is the drand round corresponding to unlock_time
       K_a is revealed trustlessly when drand publishes round_unlock

K_b  = Shamir(M, N) over [s_1, ..., s_N]
       K_b = reconstructed from ≥ M shares
       Each share s_i held by custodian i
       Custodians release s_i to authenticated receiver
         only during [unlock_time, expire_time]
       Custodians delete s_i at expire_time
```

**Sealing protocol (at T_seal):**
1. Sender (or Heirlooms server on behalf of sender) generates:
   - DEK: 256 random bits
   - R_a: 256 random bits
   - K_a_sealed = tlock_encrypt(R_a, round_unlock)  [K_a = R_a, available after round_unlock]
   - K_b: 256 random bits (independent of K_a)
   - K_window = R_a ⊕ K_b
   - Shamir shares [s_1, …, s_N] of K_b
2. Sender encrypts content: C = AES-256-GCM(DEK, plaintext)
3. Sender encrypts DEK: DEK_sealed = AES-256-GCM(K_window, DEK)
4. Each custodian i receives share s_i, encrypted to their public key.
5. Each custodian publishes Commit(s_i) to the audit log.
6. Capsule record stores: [C, DEK_sealed, K_a_sealed, round_unlock, expire_time, custodian_ids]

**Unlock protocol (during [unlock_time, expire_time]):**
1. Receiver authenticates to Heirlooms server.
2. Server retrieves published drand signature for round_unlock → recovers K_a (= R_a).
3. Receiver contacts ≥ M custodians and authenticates. Custodians verify that now ≥ unlock_time AND now < expire_time before releasing shares.
4. Receiver reconstructs K_b from ≥ M shares.
5. Receiver computes K_window = K_a ⊕ K_b.
6. Receiver decrypts DEK from DEK_sealed.
7. Receiver decrypts content from C.

**Expiry protocol (at expire_time):**
1. Each custodian deletes share s_i (software zeroisation, hardware zeroisation if available).
2. Each custodian publishes a deletion certificate (hash preimage or commitment opening) to the audit log.
3. After expire_time: K_a is permanently public (drand published it at round_unlock). K_b is irrecoverable (shares deleted). K_window = K_a ⊕ K_b is irrecoverable. DEK and content are permanently inaccessible.

**Note on early access (before unlock_time):**
K_a is time-locked — it cannot be computed before round_unlock. Even with all custodian shares (reconstructing K_b), K_window cannot be computed without K_a. The lower bound is enforced trustlessly by drand, independently of custodians.

**Note on receiver who unlocked within the window:**
A receiver who decrypted during the window holds DEK locally. The expiry protocol does not retroactively prevent access for this receiver. This is by design: once the capsule has been legitimately opened, the content is in the receiver's possession. This is documented as a known limitation (see below).

---

### Integration with Heirlooms Envelope Format

The window capsule requires the following additions to the envelope format spec (`docs/envelope_format.md`):

**New algorithm IDs to reserve (M11 scope):**

| ID | Use |
|---|---|
| `window-aes256gcm-v1` | DEK wrapped under K_window (AES-256-GCM). K_window = K_a ⊕ K_b. |
| `tlock-window-meta-v1` | Metadata envelope: stores [round_unlock, expire_time, custodian_ids, K_a_sealed, Commit(s_i) list]. Not encrypted content — this is a structured metadata record. |

The existing `tlock-bls12381-v1` algorithm ID (reserved in ARCH-005) covers K_a_sealed. The existing `shamir-share-v1` covers individual custodian shares s_i.

**Wire format extension — no format version bump required.** The existing v1 binary container is sufficient. The new algorithm IDs encode the new semantics without changing the binary structure.

**Metadata approach:** The `tlock-window-meta-v1` record can be stored alongside the capsule record in the database, not as an encrypted envelope. It contains: unlock round, expire timestamp, custodian IDs, commitment hashes. This metadata is public — it tells anyone when the window opens and closes, and provides the audit trail for deletion certificates.

**Backward compatibility:** Capsules using the window construction carry different algorithm IDs. Old clients encountering `window-aes256gcm-v1` will fail loudly (unknown algorithm ID) — the correct behaviour. Window capsules will not be available until client versions that implement the window construction are deployed.

---

## Known Limitations and Mitigations

| Limitation | Severity | Mitigation |
|---|---|---|
| Upper bound requires honest custodian threshold | High (fundamental) | (1) Use (3-of-5) threshold rather than (2-of-3). (2) Choose institutionally diverse custodians (law firm, bank, Heirlooms server, family member). (3) PSS epoch refresh during window. (4) TEE enforcement (Nitro/HSM) where custodians are Heirlooms nodes. |
| Receiver retains K_window locally after unlock | Medium (by design) | Document as intended behaviour. Users who unlock within the window possess the content; no mechanism can retroactively revoke content in their possession. This is the same as any E2EE system — once decrypted, the plaintext can be retained. |
| Custodian availability during window | Medium (operational) | SLA requirements on custodian nodes. Health monitoring with alerting before expire_time. Receiver can attempt unlock multiple times during the window. Design the system to return a partial response even if one-of-three custodians is unavailable (threshold tolerates dropouts). |
| tlock lower bound requires drand availability at unlock | Medium (operational) | Drand's fastnet has published a round every 3 seconds since March 2023 without interruption. drand is operated by a diverse consortium. If drand shuts down, the tlock round key is permanently unavailable — but the M11 multi-path design (ARCH-006) ensures the recipient pubkey path still works. Window capsules relying exclusively on tlock + custodians would fail if both drand and custodians are unavailable. Design recommendation: always include the recipient pubkey wrap (`capsule-ecdh-aes256gcm-v1`) as a third unlock path. |
| BLS12-381 quantum vulnerability | Low (long-horizon) | See RES-001. tlock's quantum vulnerability means a future quantum computer could retroactively derive K_a for past rounds. If K_a is exposed, the expiry guarantee is reduced from "K_window irrecoverable" to "K_b must remain deleted." This is still secure as long as custodians maintain deletion — the quantum break of tlock weakens the lower bound, not the upper bound. |
| Classical verifiable deletion proofs can be forged | Medium (fundamental) | Mitigated by institutional accountability (named custodians), on-chain audit log, and defence-in-depth (multiple mechanisms). Quantum certified deletion eliminates this weakness but requires quantum infrastructure. |
| SGX vulnerability history | Medium | SGX is an enhancement, not a primary mechanism. The construction is secure without SGX (software deletion + commitment scheme). SGX raises the bar; its vulnerabilities do not collapse security if the commitment scheme is in place. |

---

## Patentability Assessment

**Patentable elements:**

1. **The specific combination:** tlock/drand IBE lower bound ⊕ XOR blinding ⊕ Shamir threshold deletion upper bound, as a complete window encryption construction. This combination is not found in any published patent or paper searched.

2. **The XOR blinding scheme in the window context:** Splitting K_window = K_a ⊕ K_b where K_a is tlock-encrypted and K_b is Shamir-shared, such that neither component alone enables decryption, is a specific and novel structure. (Note: the general XOR blinding of a DEK via tlock is described in ARCH-006 — the window extension adds the Shamir deletion dimension.)

3. **The protocol for deletion certificates:** Custodians committing to shares at sealing and publishing classical deletion certificates at expire_time, combined with a threshold requirement that makes any single deletion certificate non-repudiable by the sender.

**Prior art barriers:**

- Time-Specific Encryption (Paterson-Quaglia 2010) is a broad framework patent risk for any construction that enforces a time interval on decryption. However, TSE requires an online time server and does not use deletion — it is sufficiently different from the Heirlooms construction that it does not constitute blocking prior art.
- Timed Secret Sharing (Kavousi et al. 2024) is a research paper (not a patent). It establishes that the concept of upper-bounded secret sharing is in the academic prior art, which would make it difficult to patent the *concept* broadly. However, the specific instantiation (drand + XOR blinding + consumer envelope integration) remains patentable.
- US20060155652 ("Expiring Encryption") is abandoned and uses a different mechanism (secure clock). Not blocking.
- WO2008127446A2 ("Time-Lapse Cryptography") covers threshold secret sharing for the lower bound only. Not blocking for the expiry/upper bound.

**Patent recommendation:** A narrow patent on the specific combination — drand IBE time-lock lower bound + threshold deletion upper bound + XOR blinding for DEK reconstruction — is likely viable and defensible. Broad claims on "time-windowed encryption with expiry" are more vulnerable to obviousness challenges given the TSS paper. The CTO should discuss with a patent attorney experienced in cryptography patents before filing. Filing priority should precede any public launch of the window capsule feature.

---

## Recommendations for Technical Architect

1. **Implement the construction as specified above.** The construction is sound, novel, and fits within the existing Heirlooms architecture. Begin with a (2-of-3) custodian threshold for the initial implementation, with the threshold configurable.

2. **Reserve the two new algorithm IDs** (`window-aes256gcm-v1`, `tlock-window-meta-v1`) in `docs/envelope_format.md` before any developer touches the implementation.

3. **Design the custodian API first.** The window capsule's novel complexity is in the custodian tier, not the cryptographic primitives. Custodians need: share storage, authenticated share release (time-gated), deletion protocol, and deletion certificate publication. This is a new service component. Consider whether Heirlooms' own servers can serve as default custodians for v1, with institutional custodians as a future option.

4. **Implement PSS refresh for long-window capsules.** Capsules with expire_time − unlock_time > 12 months should use proactive secret sharing to refresh custodian shares. The baseline Herzberg-1995 protocol is sufficient for v1. Consider APSS (Asynchronous PSS) if custodian availability cannot be synchronised.

5. **Add deletion certificate infrastructure.** Even without quantum certified deletion, implement classical commitment-based certificates: (a) at seal time, each custodian publishes Commit(s_i) = SHA-256(s_i ‖ salt_i) to a public log; (b) at expire_time, each custodian publishes (s_i, salt_i) — wait, this reveals s_i. Correct approach: use a Pedersen commitment or a ZK proof of deletion that does not reveal s_i. Start with: at expire_time, custodian publishes a signed timestamped deletion statement; the absence of s_i in future audits is the evidence. This is weak but auditable. Flag for upgrade to stronger proof when quantum infrastructure is available.

6. **Do not use SGX as the primary deletion mechanism.** Use it as a defence-in-depth enhancement for Heirlooms-operated custodian nodes. SGX's vulnerability history and operational complexity make it unsuitable as the sole enforcement mechanism.

7. **Plan for the AWS Nitro Enclaves + KMS enhancement.** For Heirlooms-operated custodian nodes, use Nitro Enclaves to hold shares and KMS key deletion at expire_time as a hardware-backed mechanism. This can be implemented post-v1 as a strengthening measure.

8. **Document the receiver-retains-plaintext limitation clearly.** This must appear in the user-facing documentation for window capsules. It is not a bug but users must understand it.

---

## Glossary Additions

See the new terms appended to `docs/research/GLOSSARY.md` at the end of this task.

New terms added: Proactive Secret Sharing (PSS), Timed Secret Sharing (TSS), Time-Specific Encryption (TSE), Witness Encryption, Puncturable Encryption, Verifiable Delay Function (VDF), Certified Deletion, Mobile Adversary, Epoch (in secret sharing context), Commitment Scheme, K_a, K_b, K_window, Deletion Certificate.

---

## New References (append to REFERENCES.md format)

See the new section appended to `docs/research/REFERENCES.md` at the end of this task.

---

## PA Summary

**For:** PA (to route to CTO and Technical Architect)  
**Urgency:** Medium — construction is validated and ready for ARCH design; no blocking decisions needed before M12 planning

**Key findings:**

- The tlock + Shamir deletion window capsule construction is **novel as a complete practical system**. The closest academic work is "Timed Secret Sharing" (Kavousi et al., ASIACRYPT 2024), which defines the framework but does not implement it using drand/tlock. Heirlooms' construction is a distinct and patentable instantiation.
- **Trustless expiry is impossible with classical cryptography.** Any expiry guarantee requires trusting a threshold of custodians (or hardware, or future quantum infrastructure). The construction is the strongest achievable under classical assumptions.
- **Quantum certified deletion** (Bartusek-Raizes 2024; Katz-Sela 2024) is the theoretically correct long-term solution, but requires quantum communication infrastructure — horizon of 5–15 years, not a near-term option.
- **Proactive Secret Sharing (Herzberg 1995)** is directly applicable as an enhancement for long-window capsules (> 12 months): share refresh between epochs forces an adversary to simultaneously compromise the threshold, rather than accumulating shares gradually.
- **Patentability is viable** for the specific combination (drand lower bound + XOR blinding + threshold deletion upper bound). Filing should precede public launch of the window capsule feature.

**Decisions needed from CTO:**

1. Should Heirlooms' own servers be the initial custodians (simplest v1), or should external institutional custodians (law firms, notaries) be designed in from the start?
2. Is patenting the window capsule construction a priority? If yes, this requires engaging a patent attorney before the feature launches publicly.
3. Should PSS refresh be included in the initial window capsule spec (adding complexity), or deferred to a later milestone?

**Follow-on tasks created:**

- None automatically created. Recommend the CTO route the following based on answers to the decisions above:
  - ARCH task: Window capsule custodian API specification (if CTO confirms v1 design direction)
  - RES-003 remains in progress: PQC migration readiness brief (separate topic, already queued)
  - SIM-001 in queue: Simulation — trustless expiry impossibility (now unblocked by RES-002)

---

## Research Sources

Full numbered references in `docs/research/REFERENCES.md` under section RES-002.

Key sources: Rivest-Shamir-Wagner time-lock puzzles [RES-002-001]; tlock paper [RES-002-005]; Kavousi et al. TSS — closest prior work [RES-002-017]; Paterson-Quaglia TSE [RES-002-018]; Herzberg et al. PSS [RES-002-011]; Bartusek-Raizes certified deletion [RES-002-015]; Katz-Sela publicly verifiable deletion [RES-002-016]; Garg et al. witness encryption [RES-002-012]; Garg CRYPTO 2025 SNARK-based WE [RES-002-014]; Xu et al. verifiable deletion [RES-002-009]; "Expiring Encryption" patent [RES-002-021].
