# Heirlooms — Cryptographic Differentiation, IP Landscape, and Market Position

> ⚠ **PATENT SENSITIVE — NOT FOR DISTRIBUTION BEFORE UK PATENT FILING**
> This document constitutes a full technical disclosure of the window capsule construction.
> Circulation before filing permanently forecloses the UK patent position.
> Distribute only under executed NDA, and only after the patent application has been filed.
> Internal classification: Confidential — Heirlooms Digital Ltd only.

**Version:** 2.0 — May 2026
**Audience:** Technical — cryptographers, senior engineers, technical due diligence teams
**Prepared by:** Heirlooms Digital Ltd — Technical & IP Brief
**Related internal documents:** RES-001, RES-002, RES-003, RES-004, LEG-001, LEG-002, docs/envelope_format.md

---

## Executive summary

Heirlooms is a multi-platform E2EE personal archive with a novel cryptographic time-capsule mechanism at its core. The system provides a mathematically enforced lower bound on content access — trustless, via a distributed IBE randomness beacon — and a threshold-honest upper bound achieved through Shamir threshold key deletion, combined via XOR blinding of the data encryption key derivation path.

These two bounds have fundamentally different trust properties. **The lower bound is effectively trustless**: it is enforced by a threshold-honest distributed randomness beacon across geographically and institutionally diverse operators, not by any single party. **The upper bound is threshold-honest but not trustless**: it requires a threshold of independent custodians to honestly delete their shares. This asymmetry is a first-class design property, not a weakness — it is the strongest achievable combination under classical cryptographic assumptions, and the multi-path unlock design (§2.3) ensures the system remains usable even if any single path fails.

The window capsule construction — this specific combination of tlock/drand lower bound, XOR key blinding, and Shamir threshold deletion upper bound — has no active patent and no complete deployable implementation in the prior art. A UK patent application is in preparation. The construction applies to three commercially significant markets beyond personal consumer use.

A second novel construction, the chained capsule (§3.3), has been separately assessed (RES-004, LEG-002) and is also patent-viable. Patent counsel is being engaged to evaluate combined versus separate filing.

**Important deployment note:** The window capsule construction is in specification and early implementation. The current production system uses a stub `TimeLockProvider` (HMAC-based test keys, per ARCH-006). Real drand integration is scoped to M12. The construction described in this document reflects the designed and specified system; not everything described here is yet in production.

---

## 1. System architecture

### 1.1 Encryption model

Heirlooms uses a two-layer E2EE model:

- **Content layer:** Each file is encrypted under a per-file 256-bit data encryption key (DEK) using AES-256-GCM with 96-bit random nonces.
- **Key wrapping layer:** Each DEK is wrapped under the user's master key (AES-256-GCM). The master key is generated on the first device and never leaves any device in plaintext.
- **Device binding:** Each device holds a P-256 ECDH keypair. The master key is independently wrapped to each device's public key (ECDH + HKDF-SHA-256 → AES-256-GCM). Adding a device follows a linked-device flow where a trusted peer decrypts its master key copy and re-wraps to the new device's public key; the server is a relay only.
- **Passphrase recovery:** Argon2id (m=64MiB, t=3, p=1) derives a recovery key from a passphrase. The server holds a passphrase-wrapped master key blob for web client access.
- **Social recovery:** Shamir's Secret Sharing across nominated executors. Deferred to M11; not yet in production.

### 1.2 Envelope format

All encrypted content is stored in a self-describing binary envelope:

```
[1 byte: format version] [variable: algorithm ID string, UTF-8, max 64 bytes] [ciphertext]
```

The algorithm ID is an explicit string (`p256-ecdh-hkdf-aes256gcm-v1`, `argon2id-aes256gcm-v1`, etc.) rather than a numeric type field. Unknown algorithm IDs fail loudly. This design provides cryptographic agility: new algorithm identifiers (including post-quantum variants) can be added without changing the wire format, and old and new envelopes coexist during rolling migrations without a flag day.

This is functionally similar in intent to JWE compact serialisation but uses compact binary encoding, an algorithm ID strategy that is self-describing per envelope (rather than relying on a shared registry), and a strict loud-failure policy on unknown IDs that JWE does not enforce.

### 1.3 Multi-user and sharing model

Each user has an account-level P-256 sharing keypair (separate from device keypairs). Item sharing re-wraps the item DEK under the recipient's sharing public key; the recipient receives a new upload record pointing to the same GCS blob. Plot-level (shared gallery) sharing uses a per-plot AES-256-GCM group key, wrapped to each member's sharing public key individually.

---

## 2. The window capsule construction

### 2.1 Design goals

A sealed capsule must satisfy:

1. **Lower bound (unlock guarantee):** No party — including the platform, the recipient, and the sender — can decrypt content before T_unlock.
2. **Upper bound (expiry guarantee):** No party can decrypt content after T_expire, even in possession of all key material that was accessible during the window.
3. **Trustless lower bound:** The lower bound must not depend on the availability or honesty of any single party.
4. **Threshold-honest upper bound:** The upper bound degrades gracefully with threshold size; a minority of dishonest custodians does not collapse the guarantee.

### 2.2 Construction

```
K_window = K_a XOR K_b

K_a = IBE_encrypt(R_a, round_unlock)
      using drand/tlock over BLS12-381
      R_a is a 256-bit random scalar
      K_a is revealed trustlessly when drand publishes round_unlock

K_b = Shamir(M, N) split across N custodian nodes
      each custodian holds one share s_i
      custodians release s_i only during [T_unlock, T_expire]
      custodians delete s_i at T_expire

DEK encrypted under K_window (AES-256-GCM, algorithm ID: window-aes256gcm-v1)
Content encrypted under DEK (AES-256-GCM, algorithm ID: aes256gcm-v1)
```

**Lower bound enforcement:** K_a = R_a, which is the tlock decryption of K_a_sealed. Until drand publishes round_unlock, K_a is computationally inaccessible to any party. This is a threshold-honest assumption across the League of Entropy — a geographically and institutionally diverse set of beacon operators (Cloudflare, EPFL, Protocol Labs, UCL, and others). For a consumer product threat model, this is effectively trustless.

**Upper bound enforcement:** After T_expire, each custodian deletes s_i. Once M or more shares are deleted, K_b is permanently irrecoverable under the information-theoretic security of Shamir's scheme. Since K_window = K_a XOR K_b, and K_a is by then permanently public, K_window is irrecoverable as soon as K_b is irrecoverable. The XOR blinding is essential: K_a's permanent public availability after unlock does not compromise anything once K_b is destroyed.

**Trust model summary:**
- Lower bound: threshold-honest across League of Entropy. Effectively trustless for consumer threat model.
- Upper bound: threshold-honest across custodian nodes. Security degrades gracefully. Recommended production threshold: (3-of-5).
- The two bounds are independent: compromise of the lower bound does not compromise the upper bound, and vice versa.

**Known limitation — receiver retention:** A receiver who legitimately decrypts during the window holds the DEK locally. The expiry protocol does not retroactively revoke access for this receiver. Once content has been decrypted, the plaintext is in the receiver's possession — the same as any E2EE system. The expiry guarantee applies to parties who did not decrypt during the window; it does not erase content from a legitimate receiver's device.

### 2.3 Multi-path unlock and primitive resilience

The window capsule is one unlock path in a three-path M11 design:

1. **Recipient pubkey wrap:** DEK re-wrapped to recipient's P-256 sharing pubkey at sealing time. The recipient can decrypt immediately on T_unlock using their private key. This path does not enforce expiry — it is the "legitimate receiver during the window" path.
2. **Window capsule (tlock + Shamir):** As above. Enforces both bounds for any party that does not hold the recipient private key.
3. **Shamir executor recovery:** The per-capsule key (or master key, depending on configuration) split across nominated executors for posthumous unlock, conditioned on death verification. This path is M11 scope and not yet in production.

The multi-path design is explicitly resistant to single-primitive failure: a BLS12-381 quantum break weakens path 2's lower bound but does not affect paths 1 or 3. A drand availability failure does not affect path 1. Path redundancy is the primary resilience mechanism.

### 2.4 Deletion certificates and the custodian network

**Custodian network governance:** For the initial production implementation, Heirlooms' own server infrastructure will serve as default custodians for simplicity. The protocol is designed to accommodate institutional custodians (law firms, banks, notaries) as the product matures — each acting as one of N nodes, with obligations formalised in service agreements. The design of the custodian API, share distribution, and deletion attestation protocol is an active specification area.

**Proactive secret sharing (PSS):** For capsules with access windows exceeding 12 months, the construction should incorporate Proactive Secret Sharing (Herzberg et al., CRYPTO 1995). PSS re-shares the secret across custodians at each epoch boundary, ensuring that a mobile adversary who gradually compromises custodians across epochs — but never holds a full threshold within a single epoch — cannot reconstruct K_b. The initial implementation will use a static Shamir split; PSS will be added for long-window capsules in a subsequent milestone.

**Deletion certificates:** At sealing time, each custodian publishes a classical commitment Commit(s_i) to an append-only audit log. At T_expire, each custodian publishes a deletion attestation. Classical deletion proofs are not information-theoretically binding — a custodian who retains s_i can produce a forged attestation — but they provide an audit trail for institutional accountability and evidence admissible in dispute resolution. For Heirlooms-operated custodian nodes, deletion can be hardware-enforced using AWS Nitro Enclaves with KMS key deletion at T_expire, raising the bar from "software deletion with attestation" to "hardware-enforced deletion with cryptographic attestation."

Quantum certified deletion (Bartusek-Raizes, CRYPTO 2024; Katz-Sela, Eurocrypt 2025) would provide information-theoretically binding deletion proofs but requires quantum communication channels. This is a horizon technology (5–15 years) and the appropriate long-term upgrade path.

---

## 3. Prior art analysis

### 3.1 Academic prior art

| Paper | Year | What it covers | Why it does not anticipate Heirlooms |
|---|---|---|---|
| Rivest, Shamir, Wagner (time-lock puzzles) | 1996 | Computational lower bound via sequential squaring | Lower bound only; no deletion; puzzle hardness drifts with hardware |
| Boyen-Waters IBE | 2006 | IBE-based TRE with trusted time server | Centralised time server (not threshold-distributed); no upper bound |
| Paterson-Quaglia (Time-Specific Encryption) | SCN 2010 | Encryption to time interval [T1, T2] via active time server key distribution throughout the window | Requires online time server active throughout; decryption is possible at any point in the window, not a one-time event; no deletion mechanism; server is a gatekeeper, not a relay |
| tlock (De Santis et al.) | ePrint 2023/189 | IBE over BLS12-381 using drand as key authority | Lower bound only; no upper bound or expiry mechanism |
| Kavousi, Abadi, Jovanovic (Timed Secret Sharing) | ASIACRYPT 2024 | Formal definition of TSS with both lower and upper time bounds; uses VDFs or time-lock puzzles for lower bound | Academic framework only; lower bound mechanism is VDFs/time-lock puzzles, not drand/IBE; no XOR blinding; no deployable implementation; no consumer envelope integration |
| Multiple-TRE via SSS (2024) | 2024 | Distributes TRE time server across multiple servers using SSS to prevent single-server failure | Lower bound robustness only; no upper bound; no deletion |
| i-TiRE (Incremental Timed-Release Encryption) | CCS 2022 | Practical TRE on blockchains; efficient ciphertext updating across time periods | Lower bound only; blockchain integration focus; no deletion mechanism |

**Key finding:** Kavousi et al. (2024) establishes the Timed Secret Sharing (TSS) framework — a system with both lower and upper time bounds on secret reconstruction — as academic prior art for the abstract concept. This limits the breadth of any patent claim on "time-windowed secret sharing" generally. However, the paper uses VDFs or time-lock puzzles for the lower bound (not drand/IBE), provides no deployable implementation, describes no XOR blinding scheme, and does not integrate with a consumer envelope format. Heirlooms' construction is a specific, novel instantiation of this framework that is not anticipated by any prior work found.

### 3.2 Patent prior art

| Patent | Status | What it covers | Why it does not anticipate Heirlooms |
|---|---|---|---|
| US20060155652A1 (Expiring Encryption) | Abandoned | Hardware clock-enforced time-based decryption for DRM | Uses hardware secure clock (not IBE beacon); no threshold deletion; no XOR blinding |
| WO2008127446A2 (Time-Lapse Cryptography) | Active | Feldman VSS for the lower bound (key release at future time) | Lower bound only; no upper bound mechanism |
| US9454673B1 (Skyhigh Networks, searchable encryption) | Active, assigned to Skyhigh Security | HMAC-tokenised keyword search in E2EE cloud storage | Covers searchable encryption, not time-windowed capsules; relevant to Heirlooms' tag scheme — separate FTO analysis required before US launch |
| US8812867B2 (Searchable symmetric encryption) | Active | Per-keyword HMAC tokens for E2EE search | Same category; not relevant to window capsule |

**No active patent found** describing: drand/tlock IBE lower bound + XOR blinding + Shamir threshold deletion upper bound in combination.

### 3.3 Chained capsule — second novel construction

RES-004 and LEG-002 assess a second distinct novel construction: the chained capsule, which enables time-windowed competitive delivery and narrative branching via a key hierarchy.

The chained capsule construction includes:
- **DAG chaining via key hierarchy:** A second capsule's K_b,2 is sealed inside the first capsule's plaintext. Opening C₁ during its access window gives the recipient access to K_b,2, which enables opening C₂. Neither capsule can be opened out of sequence.
- **Expiry-as-death cascade:** If C₁ expires before being opened, K_b,2 is permanently lost, making C₂ irrecoverable — a cryptographic "if you don't open this, the chain dies" property.
- **First-solver-wins atomic claim:** A race condition enforced server-side plus cryptographically: the first recipient to claim the reward prevents others from decrypting, enforced in a consumer off-chain context.
- **Capsule reference token:** An inter-capsule encrypted pointer allowing a capsule to reference another without revealing its contents pre-unlock.

RES-004's novelty assessment rates the combination as novel in its specific application. Patent counsel is being engaged to evaluate whether a single application covering both constructions (window capsule + chained capsule) is more efficient than separate applications. The Experience segment (branded experiences, ARG campaigns, editorial puzzles) is the primary commercial application of the chained capsule.

---

## 4. Post-quantum posture

### 4.1 Current exposure

| Component | Algorithm | Quantum safe? | HNDL risk |
|---|---|---|---|
| Device key wrapping | P-256 ECDH | No — Shor's algorithm | High |
| Plot key and sharing | P-256 ECDH | No | High |
| tlock lower bound (M12) | BLS12-381 | No — Shor's algorithm | Medium (mitigated by multi-path) |
| Content encryption | AES-256-GCM | Yes (128-bit effective under Grover) | Low |
| DEK wrapping | AES-256-GCM | Yes | Low |
| Passphrase KDF | Argon2id | Yes | Low |

P-256 usage across device registration, plot membership, and item sharing is the primary quantum exposure. Harvest-now-decrypt-later (HNDL) adversaries collecting encrypted traffic today can retroactively break P-256-wrapped key material with a sufficiently large fault-tolerant quantum computer. Recent 2025–2026 papers have revised CRQC resource estimates materially downward: logical qubit requirements for breaking P-256 are now estimated at approximately 1,193 (vs ~2,124 in prior estimates), with physical qubit counts on neutral-atom architectures as low as ~26,000.

NIST PQC standards are final and deployable: FIPS 203/ML-KEM (key encapsulation), FIPS 204/ML-DSA (signatures), FIPS 205/SLH-DSA (signatures), and HQC (second KEM standard, selected March 2025).

### 4.2 Migration design

The versioned envelope format is the critical architectural asset for PQC migration. Adding `mlkem768-hkdf-aes256gcm-v1` or `hybrid-p256-mlkem768-hkdf-aes256gcm-v1` as new algorithm IDs requires no wire format change. Old and new envelopes coexist during a rolling migration without a flag day. The DEK-per-file model means migrating the key-wrapping layer does not require bulk re-encryption of content — only re-wrapping of DEKs under new keys.

**Planned migration path:**
1. Near-term: Define hybrid P-256+ML-KEM-768 algorithm IDs in the envelope spec.
2. Medium-term: Implement hybrid key exchange for new device registrations. Existing devices re-wrap on next authentication.
3. Long-term: Deprecate P-256 for new key wraps; maintain P-256 decode support for existing envelopes.

**tlock-specific risk:** A quantum break of BLS12-381 would allow retroactive derivation of past drand round keys, making K_a for past rounds available to an adversary. Importantly, this only compromises capsules where K_b has not yet been deleted (i.e., within the access window). After T_expire, K_b has been destroyed, and knowledge of K_a provides nothing. The multi-path design (§2.3) further limits impact: a BLS12-381 break affects path 2 only; paths 1 and 3 are unaffected. No standardised post-quantum time-lock scheme exists yet — witness encryption based on SNARK-friendly primitives (Garg et al., CRYPTO 2025) is the likely long-term upgrade path for the lower bound.

---

## 5. Applications at technical depth

Applications are ordered by commercial priority.

### 5.1 Care & Consent (LPA and advance care planning)

The Powers of Attorney Act 2023 is digitising the LPA process in England and Wales. A cryptographically timestamped consent record — where the timestamp is produced by the drand beacon at sealing time — provides a level of verifiability not achievable with a hash-of-a-document approach: the timestamp is a function of external public randomness at sealing time, making backdating cryptographically infeasible.

For revocable consent, the window capsule expiry mechanism provides a natural model: consent is sealed with an expiry corresponding to a future capacity assessment event. While capacity is retained, the consent document exists within the window and can be updated. After the expiry event, the document transitions to a permanent state that cannot be modified.

**Regulatory positioning:** The product is designed to avoid MHRA SaMD classification by limiting claims to consent management and secure communication — not clinical monitoring or symptom detection. The Care & Consent feature is a general-purpose record-keeping and communication tool whose content happens to be relevant to care planning; it is not a diagnostic or therapeutic device. An MHRA qualification opinion should be obtained before any marketing to NHS or institutional care buyers references clinical or capacity-assessment functions.

### 5.2 Enterprise governance

Sealed board resolutions, M&A terms, regulatory submissions, and succession plans are current use cases for law firm escrow. The cryptographic model replaces law firm escrow with a threshold-honest custodian network — potentially including the law firm as one of N nodes — removing the single point of trust and providing a machine-verifiable audit trail. A single enterprise contract in this segment is worth more than thousands of consumer subscriptions.

### 5.3 Examination integrity

A sealed distribution system where papers cannot be opened before exam time (because the drand round key for that time does not yet exist) eliminates the paper-leak risk category. Each exam centre holds the ciphertext in advance; no party holds the decryption capability until the specified round publishes. The window capsule expiry can additionally seal papers so they become inaccessible to centres after a defined period post-exam. Deployable on existing Heirlooms infrastructure once M12 (real drand integration) is complete, with an institutional API wrapper.

### 5.4 Journalist source protection — future design extension

A dead man's switch use case: a journalist seals sensitive documents in a capsule with an auto-expiry model — each successful login resets a renewal window; failure to log in allows the window to open. This is architecturally a complement to the window capsule (activity-gated rather than date-gated) and requires an activity-monitoring service component not in the current architecture. This is a future design extension, not a current or near-term capability. It is noted here as a direction that the underlying construction makes natural, pending an architecture brief.

---

## 6. Patent strategy

### 6.1 Claim hierarchy

The patent application should be structured with three levels of claim abstraction:

**Level 1 — Broad independent claim (maximum defensible scope):**
> A method for time-windowed cryptographic access, comprising: combining a first key component derived from a decentralised threshold randomness beacon for a specified future round with a second key component held in threshold-split form across custodian nodes, via an XOR operation, to produce a window key; encrypting content under a data key derived from the window key; wherein the first component is computationally inaccessible before the specified round and the second component is made permanently irrecoverable by threshold custodian deletion at a specified expiry time.

This claim covers implementations using any distributed randomness beacon (not just drand), any threshold deletion mechanism, and any XOR-blended key derivation in the window context. It will face an obviousness challenge based on Kavousi et al. — whether it survives examination depends on the examiner and the cited art.

**Level 2 — Intermediate dependent claims:**
- The use of a distributed IBE beacon (not just any randomness beacon) as the lower bound
- The specific XOR blinding of K_a and K_b where K_a is a tlock-encrypted random scalar
- The deletion certificate protocol (commitment at sealing, attestation at expiry)
- The proactive secret sharing refresh protocol for long windows

**Level 3 — Narrow specific claims (highly defensible):**
- The exact drand/BLS12-381 instantiation
- Integration with the versioned envelope format (algorithm IDs window-aes256gcm-v1 and tlock-window-meta-v1)
- The multi-path unlock design with three independent paths

The attorney should be instructed to draft at all three levels and negotiate for the broadest surviving scope during prosecution. Level 3 claims are highly defensible. Level 1 claims may be challenged and narrowed.

**DEK blinding architecture note:** The PA_NOTES ARCH decision (2026-05-15) records a specific DEK blinding scheme where the split is between the recipient-wrapped DEK_client and the tlock-sealed DEK_tlock = DEK XOR DEK_client, ensuring the server never sees the full DEK even at delivery. Claim language should be cross-checked against this ARCH-approved design before finalisation to ensure the claims describe what is actually being built.

### 6.2 Claim strategy against key prior art

Against Kavousi et al.: the paper establishes the TSS framework abstractly and uses VDFs or time-lock puzzles for the lower bound. Heirlooms uses a distributed IBE beacon (drand), which is not described or suggested in the paper. The XOR blinding scheme between K_a and K_b is novel. This is a strong argument for Level 2 claims.

Against Paterson-Quaglia: the key distinguishing feature is that Heirlooms' lower bound is trustless (drand requires no online Heirlooms component after sealing), whereas TSE requires an active time server throughout the window. This distinction is commercially and technically significant.

Against the patent prior art: no active patent addresses the combination. The most careful watch is US9454673B1 (Skyhigh Networks) for the tag scheme — FTO analysis required before US launch of that feature.

### 6.3 Chained capsule filing strategy

The chained capsule (§3.3) is a second novel construction. Patent counsel is being engaged to advise on: (a) whether a single application covering both window and chained capsule is more efficient than separate applications; (b) the appropriate claim structure for the chained capsule's novelties (key hierarchy chaining, expiry-as-death cascade, first-solver-wins atomic claim, capsule reference token); and (c) whether the Experience segment's commercial relevance supports a separate international prosecution strategy for the chained capsule.

---

## 7. Competitive positioning

The practical barrier to replication has two components: the patent and the implementation.

The patent (once granted) protects the specific combination of drand IBE lower bound, XOR key blinding, and Shamir threshold deletion upper bound at multiple levels of abstraction. A competitor cannot implement the same construction without a licence. A competitor who builds a weaker version — using a centralised time server instead of drand, or omitting the deletion mechanism — offers a materially inferior product: the "trustless lower bound" claim, which is the primary differentiator in the Care & Consent and legal markets, depends specifically on the decentralised beacon.

The implementation barrier is real but not permanent: the multi-platform E2EE architecture, the versioned envelope format, the DEK-per-file model, and the post-quantum migration design could in principle be replicated by a well-resourced team. The combination of a filed patent plus a working production system plus a user base creates the acquisition rationale: a care home group, insurer, or legal tech firm with a strategic need for this capability faces a build-vs-buy decision in which the IP cost of building independently, the time cost of replication, and the execution risk both favour acquisition over independent development.

The set of potential acquirers who reach this conclusion spans personal legacy, care technology, insurance, legal services, education, and enterprise governance — a wider and better-funded universe than any single-segment product could address.

---

*This document is patent-sensitive and must be treated as equivalent to RES-002 in terms of disclosure risk. It must not leave the private repository before the UK patent application is filed. After filing, distribute only under NDA. The claim language sections may require redaction before external distribution depending on prosecution strategy — consult patent counsel.*
