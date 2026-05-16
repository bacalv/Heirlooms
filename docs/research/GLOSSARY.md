# Heirlooms Research — Cryptographic Glossary

**Maintained by:** Research Manager  
**Last updated:** 2026-05-16 (RES-001, session wrap-up)  
**Purpose:** Plain-language definitions of terms used in research briefs. Updated at the end of every research task. Intended for any team member, not just cryptographers.

---

## A

**Attack window**
The period during which encrypted data is vulnerable following the compromise of a cryptographic algorithm. In Heirlooms' context, the attack window has three layers: (1) future data — closed the day hybrid keys are deployed; (2) existing key wrapping — closed when each user's device re-wraps their master key under a quantum-safe algorithm; (3) HNDL for already-harvested data — cannot be retroactively closed for data captured before hybrid deployment. Minimising the attack window is the primary goal of the PQC migration plan. See also *HNDL*, *Key rotation*, *Re-wrap*.

**AES-256-GCM** (Advanced Encryption Standard, 256-bit key, Galois/Counter Mode)  
The symmetric encryption algorithm used throughout Heirlooms to encrypt file content, thumbnails, metadata, and wrapped keys. AES-256 means the key is 256 bits long. GCM is an authenticated mode — it simultaneously encrypts and produces an authentication tag, so any tampering with the ciphertext is detected on decryption. Considered quantum-safe under Grover's algorithm (effective security drops to 128 bits, which remains computationally infeasible). Used in Heirlooms via the `aes256gcm-v1` algorithm identifier.

**Algorithm agility** — see *Cryptographic agility*.

**Argon2id**  
A password-based key derivation function (KDF) designed to be memory-hard: it requires a large amount of RAM to compute, making it expensive to parallelise on GPUs or ASICs. Heirlooms uses Argon2id to derive an encryption key from a user's recovery passphrase (m=64MiB, t=3, p=1). Winner of the Password Hashing Competition (2015). Considered quantum-safe — memory-hardness is not known to be weakened by quantum speedups.

---

## B

**Birthday bound**  
A statistical limit derived from the birthday paradox. For AES-GCM with 96-bit random nonces, the probability of two nonces colliding approaches 50% after approximately 2^48 messages under the same key. NIST recommends treating 2^32 operations as a practical safety limit. A nonce collision under AES-GCM is catastrophic — it allows plaintext recovery and forgery. In Heirlooms, per-file DEKs used for a single file are never at risk of hitting this bound.

**BLS12-381**  
A pairing-friendly elliptic curve defined over a 381-bit prime field. Used as the basis for the BLS signature scheme in drand's randomness beacon and the tlock time-lock encryption scheme planned for Heirlooms M11. Offers approximately 128 bits of classical security. **Not quantum-safe** — Shor's algorithm applied to the elliptic curve discrete logarithm problem (ECDLP) would break it on a sufficiently powerful fault-tolerant quantum computer. drand acknowledges this and estimates a "5-year long-term security horizon," though recent (2025–2026) research has compressed quantum timeline estimates.

**BLS signature scheme** (Boneh–Lynn–Shacham)  
A digital signature scheme built on pairing-based cryptography. Used by drand's League of Entropy nodes to collaboratively produce threshold signatures that form the basis of publicly verifiable randomness. The tlock scheme uses BLS signatures to encrypt data to a future drand round — once that round's signature is published, the ciphertext becomes decryptable.

---

## C

**Custodian**
In the window capsule design, a party that holds one Shamir share of K_b (the expiry half of the window key). Custodians release their share only to an authenticated receiver during the valid [unlock_time, expire_time] window, and destroy it at expire_time. Custodians can be individuals, legal entities (law firms, banks, notaries), or distributed Heirlooms nodes. The expiry guarantee is only as strong as the honesty of more than (N − M) custodians in a (M, N) threshold scheme. See also *Shamir's Secret Sharing*, *Window capsule*, *Verifiable deletion*.

**Cat-qubit**  
A type of physical qubit that encodes quantum information in superpositions of coherent states of a microwave resonator. Cat qubits have biased noise properties — one type of error is exponentially suppressed — making them potentially more resource-efficient for fault-tolerant quantum computation than surface-code qubits. Recent 2025–2026 papers using cat-qubit architectures have materially reduced physical qubit estimates for breaking P-256.

**Cryptographic agility**  
The property of a system that allows its underlying cryptographic algorithms to be swapped without redesigning the entire system. Heirlooms' envelope format achieves agility by storing an explicit algorithm identifier string in every encrypted blob. Adding a new algorithm (e.g. a post-quantum key encapsulation mechanism) requires implementing a new codec and registering a new ID string — no wire-format change needed. This is the correct foundation for a post-quantum migration.

**CRYSTALS-Dilithium** — see *ML-DSA*.

**CRYSTALS-Kyber** — see *ML-KEM*.

---

## D

**DEK** (Data Encryption Key)  
A random symmetric key generated per uploaded file. Each file's bytes, thumbnail, and encrypted metadata are encrypted under their DEK using AES-256-GCM. The DEK itself is then wrapped (encrypted) under the user's master key, also using AES-256-GCM. This two-layer model means the master key never directly encrypts file content, and a PQC migration of the key-wrapping layer does not require re-encrypting file content.

**drand** (Distributed Randomness)  
A distributed randomness beacon operated by the League of Entropy, a consortium of organisations including Cloudflare, EPFL, and others. Nodes use threshold BLS signatures over BLS12-381 to collaboratively produce publicly verifiable, unbiasable random values at regular intervals ("rounds"). Used as the foundation for the tlock time-lock scheme. drand acknowledges that BLS12-381 is not quantum-safe but estimates the threat is at least 5 years away.

---

## E

**Expire time**
In a window capsule, the upper time bound after which the capsule becomes permanently undecryptable. Distinct from the *unlock time* (lower bound). After expire_time, custodians destroy their Shamir shares of K_b, making K_window irrecoverable even though K_a (the tlock component) remains permanently public. A receiver who did not access the capsule during [unlock_time, expire_time] can never access it. A receiver who did access it during the window holds K_window locally and is unaffected by the expiry. See also *Window capsule*, *Unlock time*, *Custodian*.

**ECDH** (Elliptic Curve Diffie-Hellman)  
A key agreement protocol in which two parties each have a keypair on an elliptic curve. They exchange public keys and each independently derives the same shared secret from their own private key and the other party's public key. In Heirlooms, P-256 ECDH is used to wrap the master key to each device's public key (`p256-ecdh-hkdf-aes256gcm-v1`). **Broken by Shor's algorithm** on a quantum computer.

**ECDLP** (Elliptic Curve Discrete Logarithm Problem)  
The hard mathematical problem underlying all elliptic curve cryptography: given a point P on a curve and the point Q = k·P, find the integer k. Classical algorithms require exponential time; Shor's quantum algorithm solves it in polynomial time, which is why all ECC is quantum-vulnerable.

**Envelope format**  
Heirlooms' versioned binary container for all encrypted blobs. Contains: version byte, algorithm ID length, algorithm ID string, nonce, ciphertext, and authentication tag (plus ephemeral public key for asymmetric variants). The algorithm ID field is what gives the format its cryptographic agility. See `docs/envelope_format.md`.

---

## F

**FIPS 203** (Federal Information Processing Standard 203)  
The NIST standard published August 2024 specifying ML-KEM (Module-Lattice-based Key Encapsulation Mechanism, derived from CRYSTALS-Kyber). The primary post-quantum replacement for ECDH key agreement. Final and implementable.

**FIPS 204**  
NIST standard specifying ML-DSA (Module-Lattice-based Digital Signature Algorithm, derived from CRYSTALS-Dilithium). Post-quantum digital signatures.

**FIPS 205**  
NIST standard specifying SLH-DSA (Stateless Hash-based Digital Signature Algorithm, derived from SPHINCS+). A backup post-quantum signature scheme based on hash functions rather than lattices — provides diversity against lattice-specific attacks.

**FIPS 206** (in development)  
Forthcoming NIST standard specifying FALCON, a lattice-based signature scheme with smaller signatures than ML-DSA but more complex implementation.

**Fault-tolerant quantum computer**  
A quantum computer that uses error correction to suppress the high error rates of physical qubits, producing reliable logical qubits. Breaking P-256 requires a fault-tolerant machine — current NISQ (noisy intermediate-scale quantum) devices cannot do it. Fault tolerance requires many physical qubits per logical qubit; the ratio depends on the error correction code used (surface codes require ~1,000:1; cat-qubit LDPC codes potentially much less).

---

## G

**Grover's algorithm**  
A quantum algorithm that provides a quadratic speedup for unstructured search. Applied to symmetric cryptography, it halves the effective key length: AES-256 becomes equivalent to AES-128 under Grover. 128-bit effective security is still considered computationally infeasible. This is why AES-256-GCM, HKDF-SHA-256, and Argon2id are considered quantum-safe, while P-256 (broken by Shor, not Grover) is not.

---

## H

**HKDF** (HMAC-based Key Derivation Function)  
A key derivation function that takes a shared secret (e.g. the output of ECDH) and derives one or more cryptographic keys of any desired length. Heirlooms uses HKDF-SHA-256 as part of the `p256-ecdh-hkdf-aes256gcm-v1` algorithm. Considered quantum-safe under Grover.

**HNDL** (Harvest Now, Decrypt Later)  
A surveillance strategy in which an adversary captures and stores encrypted traffic today, intending to decrypt it when a sufficiently powerful quantum computer becomes available. Particularly relevant for long-lived data: a family photo archive encrypted today may still be sensitive in 30 years. Multiple intelligence and cybersecurity agencies (US DHS, UK NCSC, ENISA, ACSC) base their post-quantum migration guidance on the assumption that nation-state actors are actively conducting HNDL operations. This is the most immediate reason Heirlooms needs a P-256 migration plan — the threat is not future, it is present.

**HQC** (Hamming Quasi-Cyclic)  
A post-quantum key encapsulation mechanism selected by NIST in March 2025 as a second KEM standard alongside ML-KEM. Based on error-correcting codes rather than lattices, providing algorithm diversity.

**Hybrid key exchange**  
A key agreement scheme that combines a classical algorithm (e.g. X25519 or P-256) with a post-quantum algorithm (e.g. ML-KEM) such that an attacker must break *both* to recover the shared secret. Provides HNDL protection immediately — a future quantum computer cannot retrospectively decrypt traffic protected by a hybrid scheme unless it can also break the post-quantum component. X25519+ML-KEM-768 is already deployed in over one-third of HTTPS traffic on Cloudflare's network.

---

## I

**IBE** (Identity-Based Encryption)  
A form of public-key encryption where the public key can be an arbitrary string (e.g. an email address or a future date). A trusted authority holds a master secret and issues private keys corresponding to identities on request. The tlock scheme uses IBE over BLS12-381 to encrypt to a future drand round identifier — only when that round's private key (the threshold BLS signature) is published can the ciphertext be decrypted.

---

## K

**Key rotation**
The process of generating new cryptographic keys and migrating all key-wrapped material to use them, then deleting the old keys. In Heirlooms' PQC migration context, key rotation has two levels: (1) shallow rotation — re-wrapping the master key under a new algorithm while keeping the same master key value; (2) full rotation — generating a new master key and re-wrapping every DEK under it. Only full rotation closes the HNDL attack window for already-harvested data. See also *Re-wrap*, *Attack window*.

**KEM** (Key Encapsulation Mechanism)  
A cryptographic primitive used to securely transmit a symmetric key from one party to another using public-key cryptography. The sender encapsulates a random key using the recipient's public key; only the recipient can decapsulate it using their private key. ML-KEM is the post-quantum KEM standardised in FIPS 203, intended to replace ECDH in key exchange protocols.

---

## L

**Lattice-based cryptography**  
A family of cryptographic schemes whose security rests on the hardness of problems in high-dimensional lattices (e.g. Learning With Errors, Module-LWE). Believed to be resistant to both classical and quantum attacks. The basis of ML-KEM (FIPS 203) and ML-DSA (FIPS 204).

**League of Entropy**  
The consortium of independent organisations (Cloudflare, EPFL, Kudelski Security, Protocol Labs, and others) that operate the drand distributed randomness beacon. Threshold BLS signatures require a quorum of nodes to cooperate, so no single member can bias or predict the output.

**Logical qubit**  
An error-corrected qubit, constructed from many physical qubits, that behaves reliably enough for computation. Breaking P-256 requires ~1,193 logical qubits. The ratio of physical to logical qubits depends on the error correction code — surface codes require roughly 1,000 physical qubits per logical qubit; more advanced codes (cat-qubit LDPC) potentially require far fewer.

---

## M

**ML-DSA** (Module-Lattice-based Digital Signature Algorithm)  
Post-quantum digital signature standard (FIPS 204). Derived from CRYSTALS-Dilithium. The primary post-quantum replacement for ECDSA and similar classical signature schemes.

**ML-KEM** (Module-Lattice-based Key Encapsulation Mechanism)  
Post-quantum key encapsulation standard (FIPS 203). Derived from CRYSTALS-Kyber. The primary post-quantum replacement for ECDH key agreement. In the context of a Heirlooms migration, ML-KEM-768 (offering ~192-bit classical security) would replace P-256 in device key wrapping, plot key wrapping, and sharing key operations.

**Master key**  
In Heirlooms, a 256-bit random symmetric key generated on a user's first device. Never leaves any device in plaintext. Each device holds its own copy, wrapped (encrypted) to that device's P-256 public key. The master key wraps per-plot keys; plot keys wrap per-file DEKs.

---

## N

**Neutral-atom quantum computer**  
A type of quantum computer that uses individual neutral atoms (e.g. rubidium) trapped in optical tweezer arrays as qubits. Recent 2026 research suggests a neutral-atom machine with ~26,000 qubits could break ECC-256 in approximately 10 days.

**Neuromorphic computing**  
Computing architectures inspired by the structure and function of biological neural systems — event-driven, sparse, massively parallel. Examples: Intel Loihi, IBM NorthPole. Not currently a path to breaking standard cryptographic primitives; the primary security concern is side-channel attacks against neuromorphic hardware itself, not cryptanalysis.

**NIST** (National Institute of Standards and Technology)  
US federal agency responsible for cryptographic standards. Published FIPS 203, 204, and 205 in August 2024, finalising the first post-quantum cryptographic standards.

**Nonce**  
A "number used once." In AES-256-GCM, a 96-bit value that must be unique for every encryption operation under the same key. Heirlooms generates nonces from a cryptographically secure random number generator. Nonce reuse under AES-GCM is catastrophic — it enables plaintext recovery and forgery.

---

## P

**P-256** (also NIST P-256, secp256r1, prime256v1)  
A 256-bit elliptic curve defined by NIST. The curve used in Heirlooms for device keypairs and all ECDH-based key wrapping operations. Provides approximately 128 bits of classical security. **Not quantum-safe** — Shor's algorithm breaks the ECDLP on a fault-tolerant quantum computer. With recent algorithmic improvements, breaking P-256 requires as few as ~1,193 logical qubits.

**Pairing-based cryptography**  
Cryptography built on bilinear pairings — mathematical maps between elliptic curve groups with special algebraic properties. Enables advanced constructions like BLS signatures, IBE, and tlock. All pairing-based schemes over classical curves (including BLS12-381) are vulnerable to Shor's algorithm.

**Physical qubit**  
An actual physical system (superconducting circuit, trapped ion, neutral atom, photon, etc.) used to represent a quantum bit. Physical qubits have high error rates; many physical qubits are combined into one logical qubit via error correction codes. Estimates for breaking P-256 range from ~10,000 (cat-qubit LDPC, optimistic) to millions (surface code, conservative).

**Post-quantum cryptography (PQC)**  
Cryptographic algorithms designed to be secure against both classical and quantum computers. Distinct from "quantum cryptography" (which uses quantum physics, e.g. QKD). NIST's finalised PQC standards are based on lattice problems (ML-KEM, ML-DSA), hash functions (SLH-DSA), and error-correcting codes (HQC).

**Plot key**  
In Heirlooms, a 256-bit symmetric key specific to a shared plot. Wrapped to each member's sharing public key (P-256 ECDH) and stored in `plot_members.wrapped_plot_key`. The plot key wraps per-item DEKs via `plot-aes256gcm-v1`.

---

## Q

**Q-Day** (also Y2Q)  
The hypothetical future date on which a quantum computer becomes capable of breaking currently deployed public-key cryptography (particularly RSA and ECC). Not a single fixed date — different systems will become vulnerable at different times depending on key sizes and algorithm choices. Recent research has moved credible Q-Day estimates for ECC closer to 2030–2035.

**Quantum computer**  
A computing device that exploits quantum mechanical phenomena (superposition, entanglement, interference) to perform certain computations exponentially faster than classical computers. Not generally faster than classical computers — the speedup applies only to specific problem classes, most notably integer factorisation (Shor's algorithm) and unstructured search (Grover's algorithm).

**Qubit**  
The fundamental unit of quantum information. Unlike a classical bit (0 or 1), a qubit can exist in a superposition of 0 and 1 simultaneously. Measurement collapses the superposition to a definite value. Entanglement between qubits enables quantum algorithms to explore many solutions simultaneously.

---

## R

**Re-wrap**
The operation of decrypting a wrapped key using the current algorithm and re-encrypting it under a new algorithm, without ever exposing the key's plaintext value outside the client device. In Heirlooms, re-wrapping a master key means: (1) the device decrypts the existing P-256-wrapped master key using its local private key; (2) immediately re-encrypts the master key under the new ML-KEM-768 public key; (3) uploads the new wrapped key. The master key value never leaves the device. Re-wrap cost is O(keys), not O(files) — this is the architectural property that makes PQC migration tractable. See also *Key rotation*, *DEK*, *Attack window*.

---

## S

**Shamir's Secret Sharing**  
A cryptographic scheme for splitting a secret into N shares such that any K shares (the threshold) can reconstruct the secret, but fewer than K shares reveal nothing. Used in Heirlooms M11 to distribute capsule key shares to nominated executors. The threshold-of-N design tolerates executor attrition over a multi-decade horizon.

**Shor's algorithm**  
A quantum algorithm published by Peter Shor in 1994 that solves integer factorisation and the discrete logarithm problem (including ECDLP) in polynomial time. This is what makes RSA, Diffie-Hellman, and all elliptic curve cryptography (including P-256 and BLS12-381) quantum-vulnerable. Does not affect symmetric cryptography or hash functions.

**SLH-DSA** (Stateless Hash-based Digital Signature Algorithm)  
Post-quantum digital signature standard (FIPS 205). Derived from SPHINCS+. Based entirely on hash functions — if SHA-256 is secure, SLH-DSA is secure. Slower and produces larger signatures than ML-DSA but provides algorithm diversity as a backup.

**Surface code**  
The most widely studied quantum error correction code. Requires approximately 1,000 physical qubits per logical qubit but has relatively high error thresholds, making it practical with current hardware. Surface-code estimates for breaking P-256 require millions of physical qubits; more efficient codes (LDPC, cat-qubit) dramatically reduce this.

---

## T

**Time-windowed trust envelope**
The Research Manager's working name for the conceptual primitive that Heirlooms is building towards: an encrypted object that is mathematically inaccessible before an unlock time, enforced-unavailable after an expire time, recoverable only by a named recipient through a quorum of independent custodians, on a server that is architecturally incapable of reading it. No standardised name exists for this combination in the academic literature as of 2026. Related to *tlock*, *Window capsule*, *Shamir's Secret Sharing*, *Custodian*.

**tlock**  
A time-lock encryption scheme built on drand's randomness beacon and IBE over BLS12-381. A sender encrypts data to a future drand round number; the ciphertext can only be decrypted once that round's threshold BLS signature is published. Planned for Heirlooms M11 as one of three unlock paths for sealed capsules. Not quantum-safe — a quantum break of BLS12-381 would enable retroactive decryption of all historically tlock-encrypted data.

**Threshold signature**  
A signature produced collaboratively by K-of-N parties, where no single party holds the full private key. drand uses threshold BLS signatures across League of Entropy nodes to produce its randomness — K nodes must cooperate to produce each round's signature, preventing any single node from biasing the output.

---

## U

**Unlock time**
In a window capsule, the lower time bound before which the capsule cannot be decrypted. Enforced cryptographically via tlock — the drand round corresponding to unlock_time has not yet published its randomness, so the IBE decryption key does not yet exist. Contrast with *Expire time* (upper bound). Together they define the access window [unlock_time, expire_time].

---

## V

**Verifiable deletion**
A cryptographic proof that a party has destroyed secret material (such as a Shamir share) without revealing the material itself. An open research problem as of 2026. In theory: a custodian commits to their share at sealing time; at expire_time they publish a proof of deletion derived from the commitment. Related to "proof of erasure." Solving this cleanly for the window capsule context would be a publishable research contribution and would make the expiry guarantee auditable rather than merely trusted. See also *Custodian*, *Window capsule*.

---

## W

**Window capsule**
A Heirlooms capsule construction (proposed by CTO, 2026-05-16) with both an unlock time (lower bound, enforced by tlock) and an expire time (upper bound, enforced by threshold custodian deletion). Content is permanently undecryptable by anyone — server, sender, receiver, or adversary — after expire_time, provided the threshold of custodians honestly destroys their Shamir shares of K_b. The construction is: K_window = K_a ⊕ K_b, where K_a is tlock-encrypted (trustless lower bound) and K_b is Shamir-split across N custodians (trust-bounded upper bound). The expiry guarantee is not trustless — see *Verifiable deletion*, *Custodian*. Formalisation is the subject of RES-002.

---

## X

**X25519**  
An elliptic curve Diffie-Hellman function using Curve25519 (a Montgomery curve). Widely used in TLS and other protocols. Like P-256, it is **not quantum-safe** (vulnerable to Shor's algorithm). The X25519+ML-KEM-768 hybrid key exchange is the current de facto standard for post-quantum TLS deployment.
