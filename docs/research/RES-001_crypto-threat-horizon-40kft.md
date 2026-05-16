# Cryptographic Threat Horizon — Initial 40,000ft Survey

**ID:** RES-001  
**Date:** 2026-05-16  
**Author:** Research Manager  
**Audience:** CTO  
**Status:** Final

---

## Threat summary

The dominant long-horizon threat to Heirlooms is **quantum computing**, specifically
Shor's algorithm applied to the elliptic curve discrete logarithm problem (ECDLP). This
directly attacks P-256, the curve underpinning every key-wrapping operation in the current
system. The timeline for a cryptographically relevant quantum computer has materially
shortened in the last twelve months; this is no longer a 2040 problem.

Beyond quantum, two other computing paradigms — **biological/DNA computing** and
**neuromorphic/photonic computing** — merit monitoring but pose no near-term cryptographic
threat. They are covered below for completeness.

---

## 1. Computing paradigms threatening cryptography

### 1.1 Quantum computing — PRIMARY THREAT

**What changed in 2025–2026:**
Three papers published between May 2025 and March 2026 have materially revised resource
estimates downward. The number of logical qubits required to break P-256 ECDH has dropped
from ~2,124 to ~1,193. Physical qubit estimates on advanced architectures (neutral atom,
cat-qubit LDPC codes) are now in the range of 10,000–26,000 — several orders of magnitude
lower than surface-code estimates from 2022. One analysis suggests a neutral-atom machine
with ~26,000 qubits could crack ECC-256 in roughly ten days; once precomputation is done,
the remaining computation against a specific public key takes approximately nine minutes.

**NIST's response is complete.** As of August 2024, three post-quantum standards are final:

| Standard | Algorithm | Purpose |
|---|---|---|
| FIPS 203 | ML-KEM (Kyber) | Key encapsulation — the P-256 replacement |
| FIPS 204 | ML-DSA (Dilithium) | Digital signatures |
| FIPS 205 | SLH-DSA (SPHINCS+) | Digital signatures (backup, hash-based) |

HQC was additionally selected in March 2025 as a second KEM standard. FIPS 206 (FALCON)
is in development. These are not drafts — they are implementable today.

**Hybrid deployment is already happening.** Over one-third of HTTPS traffic on Cloudflare
now uses hybrid post-quantum handshakes (X25519+ML-KEM-768). AWS KMS, ACM, and Secrets
Manager support ML-KEM TLS. The IETF draft `draft-ietf-tls-ecdhe-mlkem` is in active
progression. The industry is not waiting.

**What is quantum-safe, today:**
- AES-256-GCM: safe. Grover's algorithm halves the effective key length to 128 bits, which
  remains computationally infeasible to attack.
- HKDF-SHA-256: safe. Same Grover analysis; 128-bit effective security.
- Argon2id: safe. Memory-hardness is not affected by quantum speedups in any known way.

**What is quantum-vulnerable:**
- P-256 ECDH: **broken by Shor's algorithm** on a sufficiently large fault-tolerant quantum
  computer. Every key-wrapped master key, plot key, and sharing key in the Heirlooms
  database is at risk under a harvest-now-decrypt-later model.
- BLS12-381 (planned for M11 tlock): also vulnerable to Shor's algorithm. Covered in §1.1a.

**Harvest Now, Decrypt Later (HNDL):**
HNDL is not a future concern — it is a present one. Nation-state actors (China, Russia,
and others with advanced signals intelligence capability) are documented as exfiltrating
and storing encrypted traffic today for future decryption when quantum computers are
available. The US Department of Homeland Security, UK NCSC, ENISA, and the Australian
Cyber Security Centre all base their post-quantum migration guidance on the assumption
that adversaries are already harvesting.

This is especially significant for Heirlooms. A photo encrypted today under P-256-wrapped
keys and stored for thirty years is exactly the kind of high-value, long-lived target that
HNDL adversaries care about. Ephemeral chat tolerates a 10-year horizon; a digital family
archive does not.

**Timeline estimate:**
- 2026–2028 (near): Q-Day for RSA-2048 and P-256 is now credibly sub-10 years on
  optimistic hardware trajectories. Migration should begin now, not when it feels urgent.
- 2029–2032 (medium): The window in which migration must be complete to protect data
  uploaded today against a retrospective quantum attack.
- 2032+ (long): Any P-256-wrapped key material in production that has not been migrated
  should be considered potentially compromised.

---

### 1.1a drand and BLS12-381 specifically

drand's own documentation acknowledges that BLS12-381 is **not quantum-resistant**:

> "Neither BLS nor the IBE scheme used in tlock are quantum resistant. Therefore if a
> quantum computer is built that is able to threaten their security, the current design
> would not resist. A quantum computer seems unlikely to be built within the next 5–10
> years and therefore the design can be expected to have a long term security horizon of
> at least 5 years."

That statement was written before the 2025–2026 papers that compressed timelines. The
5-year horizon is now a more aggressive claim than drand's team likely intended.

**The specific risk for Heirlooms:** A quantum break of BLS12-381 means an adversary
could derive the drand private key from published public randomness, and from that
retroactively compute the tlock decryption key for **any capsule sealed under a past
round** — including rounds that have already published. Every historically sealed capsule
becomes retroactively decryptable.

**drand's post-quantum roadmap:** As of this research, drand has no announced
post-quantum replacement in active development. The broader blockchain ecosystem
(Algorand, Ethereum) is moving faster on PQC than drand itself.

**The good news:** Heirlooms' M11 multi-path capsule design (recipient pubkey wrap +
tlock + Shamir shares) explicitly treats tlock as one layer in a redundant system, not
the foundation. The multi-path design was motivated by exactly this class of failure.
tlock failing quantum-catastrophically does not unlock capsules — only tlock fails; the
recipient pubkey path and Shamir path remain. This is sound design.

---

### 1.2 DNA/Biological computing — THEORETICAL, NOT NEAR-TERM

DNA computing exploits molecular parallelism: a vial of DNA can represent and test
billions of candidate solutions simultaneously. In theory, this could accelerate certain
NP-hard searches relevant to cryptanalysis.

**In practice:** Current DNA computers have not demonstrated the error correction or
deterministic output required to attack well-designed cryptographic systems. DNA
computation is slow (hours to days per operation), prone to errors, and specialised for
narrow problem classes. There is no credible near-term path to using DNA computation to
attack AES, P-256, or SHA-256.

The more relevant threat from biology is **cyberbiosecurity**: synthetic DNA-encoded
malware, re-identification of users from genomic data, and supply-chain attacks on
sequencing pipelines. These are not relevant to Heirlooms' threat model today.

**Assessment:** Monitor but do not act. Review in 5 years.

---

### 1.3 Neuromorphic and photonic computing — EMERGING, INDIRECT THREAT

Neuromorphic chips (Intel Loihi, IBM NorthPole) mimic the brain's event-driven, sparse
computation. Photonic accelerators use light rather than electrons for matrix operations.
Neither currently provides a path to breaking standard cryptographic primitives.

**The emerging concern** is that neuromorphic and photonic systems may dramatically
accelerate classical cryptanalysis for certain problem classes — not by running Shor's
algorithm, but by making brute-force or lattice-reduction attacks faster by several
orders of magnitude. This is a second-order concern; it would pressure smaller key sizes
and weaker hash functions before affecting AES-256 or ML-KEM.

**For Heirlooms:** No near-term action required. The threat is relevant only if Heirlooms
were ever to adopt key sizes below current recommendations.

**Assessment:** Low priority. Monitor academic literature annually.

---

## 2. Heirlooms' attack surface — current system

| Component | Algorithm | Quantum safe? | HNDL risk | Notes |
|---|---|---|---|---|
| Device key wrapping | P-256 ECDH | **No** | **High** | Every device's wrapped master key in DB is harvestable |
| Plot key wrapping | P-256 ECDH | **No** | **High** | All shared plot keys wrapped to sharing pubkeys |
| Item sharing | P-256 ECDH | **No** | **High** | Per-item DEK re-wraps to recipient sharing pubkeys |
| Passphrase recovery | Argon2id + AES-256-GCM | Yes | Low | Memory-hard KDF; server holds wrapped blob |
| File/thumbnail encryption | AES-256-GCM | Yes (128-bit effective) | Low | Per-file DEK; Grover attack infeasible |
| DEK wrapping under master key | AES-256-GCM | Yes | Low | Symmetric; not vulnerable to Shor |
| Auth verifier | SHA-256 | Yes (128-bit effective) | Low | Not key material; online attack only |
| Session tokens | SHA-256 hash | Yes | Low | 32 random bytes; short-lived |
| tlock (M11, planned) | BLS12-381 | **No** | **High** | See §1.1a; mitigated by multi-path design |

**Summary:** All P-256 usage is the critical exposure. There are three distinct places
where P-256 wraps key material: device registration, shared plot membership, and item
sharing. All three produce database rows that are HNDL targets today.

**AES-256-GCM nonce concern (low, but noted):** With 96-bit random nonces, the birthday
bound is reached at approximately 2^32 operations under the same key. For per-file DEKs
this is a non-issue — each DEK is used for one file, never 4 billion times. At the master
key level (which wraps DEKs), the number of wrap operations is bounded by the number of
files, which is fine. No action needed, but worth documenting as a design invariant to
preserve.

**Argon2id parameters (adequate):** Heirlooms uses m=64MiB, t=3, p=1. OWASP's 2025
minimum is m=19MiB, t=2, p=1. Heirlooms exceeds the minimum. The empty `argon2_params`
JSON (finding F-13 in the threat model) means parameter upgrades require a client
release rather than a database migration — technical debt, not an active vulnerability.

---

## 3. How Heirlooms has already started addressing this

Heirlooms' design is significantly more forward-looking than most products at its stage.
The following are genuine strengths from a long-horizon security perspective:

### 3.1 Cryptographic agility — strong

The versioned envelope format is the most important architectural decision for
long-term security. Every encrypted blob carries its algorithm identifier explicitly.
Unknown algorithm IDs fail loudly. Adding a new post-quantum algorithm ID (e.g.
`mlkem768-hkdf-aes256gcm-v1`) requires no wire format change — just a new string
registered in the algorithm table and a codec implementation on each platform.

This is exactly how PQC migrations are supposed to work: old and new envelopes coexist
during a rolling migration; no flag day required.

### 3.2 Multi-path capsule unlock design — strong

M11's three independent unlock paths (recipient pubkey wrapping, tlock, Shamir shares)
explicitly defend against the failure of any single cryptographic primitive. The design
acknowledges that a BLS12-381 break, a key loss, or an executor attrition event are all
realistic over a 30-year horizon, and that each path defends against a different failure
mode.

The explicit note in the roadmap — "tlock is one tool in the kit, not the foundation" —
is exactly the right framing. No other consumer product in this space is designing at
this level of honesty about long-horizon cryptographic failure.

### 3.3 DEK-per-file model enables incremental migration — strong

Because each file has its own DEK (wrapped under the master key), a PQC migration of the
key-wrapping layer does not require bulk re-encryption of file content. Migrating from
P-256 to ML-KEM for device key wrapping means re-wrapping DEKs under new keys — a
background operation that can proceed file-by-file. This is the correct architecture for
an evergreen E2EE system.

### 3.4 What is not yet addressed

- **No hybrid key exchange.** New device registrations still use P-256 only. A hybrid
  P-256+ML-KEM scheme for device key wrapping would provide HNDL protection immediately
  without requiring full PQC deployment.
- **No migration plan for existing production wrapped keys.** Keys already in the database
  under P-256 wrapping need a defined re-wrapping path.
- **BLS12-381 in tlock has no post-quantum alternative specified.** The multi-path design
  mitigates the catastrophic case, but a PQ-safe time-lock scheme doesn't yet exist in
  standardised form.
- **argon2_params stored as empty JSON** (F-13) means parameter rotation is harder than
  it should be.

---

## Recommended mitigations

### Near-term (now–2027)

1. **Fix F-13:** Store actual Argon2id parameters (m, t, p) in `recovery_passphrase.argon2_params`. This is a small change that makes future parameter upgrades a server-side migration rather than a client release requirement.

2. **Define the P-256 migration plan.** This does not mean implementing it now. It means the Technical Architect produces a brief specifying: what a hybrid `p256-mlkem768-hkdf-aes256gcm-v1` algorithm ID would look like in the envelope format; how existing device keys would be re-wrapped; what the client-side migration UX looks like. The plan should exist before implementation pressure arrives.

3. **Add ML-KEM algorithm IDs to the envelope format spec.** Reserve `mlkem768-hkdf-aes256gcm-v1` and `hybrid-p256-mlkem768-hkdf-aes256gcm-v1` in `docs/envelope_format.md` the same way M11 IDs are reserved. This prevents naming collisions and signals intent.

### Medium-term (2027–2030)

4. **Implement hybrid device key wrapping** for new device registrations: P-256+ML-KEM-768. This protects all key material generated after the migration date from HNDL without breaking existing clients. Existing devices re-wrap on next login.

5. **Monitor drand's post-quantum roadmap.** If drand does not publish a credible PQ migration plan by 2028, Heirlooms should evaluate alternative time-lock schemes or increase reliance on the Shamir path for long-duration capsules.

### Long-term (2030+)

6. **Deprecate P-256 for all new key wraps.** All new device registrations, plot memberships, and sharing operations use ML-KEM (or its successor). P-256 remains supported for reading existing envelopes only.

7. **Background re-wrap of existing production key material.** A background service re-wraps all P-256-wrapped DEKs and master keys under the hybrid/PQC scheme. Users are prompted to re-authenticate on next login to trigger their device's re-wrap.

---

## Cryptographic agility assessment

| Swap needed | Difficulty | Blocker |
|---|---|---|
| P-256 → ML-KEM (key wrapping) | Medium | New algorithm codec on Android, iOS, Web; envelope format already supports it |
| P-256 → ML-KEM (sharing pubkeys) | Medium | Same codec work; DB schema needs new pubkey columns |
| BLS12-381 → PQ alternative (tlock) | High | No standardised PQ time-lock scheme exists yet; depends on drand or a replacement |
| AES-256-GCM → successor | Low | Envelope format is designed for exactly this; no urgency |
| Argon2id → successor | Low | Parameters stored per-blob; old blobs keep working |

The envelope format's agility guarantee is real and valuable. The hard problem is BLS12-381
— not because of the envelope format, but because no post-quantum threshold time-lock
primitive exists in standardised form. This is an active research area, not a solved one.

---

## Open questions for CTO

1. **Migration timing:** Do you want to begin specifying the hybrid P-256+ML-KEM migration path now (as a planning task for the Technical Architect), or hold until after M11 ships?

2. **drand dependency:** The multi-path M11 design mitigates a BLS12-381 break, but it doesn't eliminate tlock from the product. Is there an appetite to de-emphasise tlock as a user-facing guarantee, or is the "cryptographic time gate" narrative too important to the product story to soften?

3. **User communication:** At what point does Heirlooms communicate post-quantum migration to users? The HNDL risk is real today — for users whose content is already uploaded, their data is being harvested now. There is a product question about whether and how to communicate this before migration is complete.

4. **Regulatory horizon:** NIST has mandated PQC migration for US federal systems by 2030. While Heirlooms is not a federal system, regulated industries (healthcare, finance) that may be potential enterprise customers will face this mandate. Does Heirlooms want to be ahead of this curve as a trust signal?

---

## PA Summary

**For:** PA (to route to CTO at next review)  
**Urgency:** Medium — no immediate action required, but planning decisions are time-sensitive

**Key findings:**
- The quantum threat to P-256 (used everywhere in Heirlooms' key-wrapping layer) has materially accelerated. Recent 2025–2026 research puts Q-Day for ECC credibly within 5–10 years. HNDL adversaries are harvesting encrypted data now.
- NIST PQC standards (FIPS 203/204/205) are final and deployable. Hybrid key exchange (P-256+ML-KEM) is already live in over one-third of HTTPS traffic on the internet.
- drand/BLS12-381 (planned for M11 tlock) is not quantum-safe; drand acknowledges this. The M11 multi-path capsule design correctly mitigates this — tlock failing does not unlock capsules.
- Heirlooms' envelope format and DEK-per-file model are genuinely strong foundations for a future PQC migration. No other product at this stage is designed this well for the transition.
- Primary gap: no P-256 → ML-KEM migration plan exists. This needs to be planned before it becomes urgent.

**Decisions needed from CTO (see §Open questions in full brief):**
1. Should the Technical Architect begin specifying the hybrid P-256+ML-KEM migration path now, or after M11 ships?
2. Is there appetite to de-emphasise tlock as a user-facing guarantee given its quantum vulnerability?
3. At what point does Heirlooms communicate post-quantum migration to users?

**Follow-on tasks created:** None from this brief — the above decisions must be made first. Once decided, expected follow-on tasks: ARCH (P-256 migration spec), SEC (hybrid key exchange implementation), RES (drand post-quantum monitoring).

---

## Research sources

Full numbered references in `docs/research/REFERENCES.md` under section RES-001.

Key sources: NIST FIPS 203/204/205 [RES-001-001–004]; The Quantum Insider Q-Day timeline [RES-001-005]; HNDL government guidance [RES-001-010–013]; drand quantum acknowledgement [RES-001-018]; tlock paper [RES-001-019]; OWASP Argon2id [RES-001-017]; AES-GCM birthday bound [RES-001-021–022]; hybrid TLS IETF draft [RES-001-014].
