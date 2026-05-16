# Heirlooms Research — Cryptographic Glossary

**Maintained by:** Research Manager  
**Last updated:** 2026-05-16 (RES-004, chained capsule cryptographic assessment)  
**Purpose:** Plain-language definitions of terms used in research briefs. Updated at the end of every research task. Intended for any team member, not just cryptographers.

---

## A

**Attack window**
The period during which encrypted data is vulnerable following the compromise of a cryptographic algorithm. In Heirlooms' context, the attack window has three layers: (1) future data — closed the day hybrid keys are deployed; (2) existing key wrapping — closed when each user's device re-wraps their master key under a quantum-safe algorithm; (3) HNDL for already-harvested data — cannot be retroactively closed for data captured before hybrid deployment. Minimising the attack window is the primary goal of the PQC migration plan. See also *HNDL*, *Key rotation*, *Re-wrap*.

**AES-256-GCM** (Advanced Encryption Standard, 256-bit key, Galois/Counter Mode)  
The symmetric encryption algorithm used throughout Heirlooms to encrypt file content, thumbnails, metadata, and wrapped keys. AES-256 means the key is 256 bits long. GCM is an authenticated mode — it simultaneously encrypts and produces an authentication tag, so any tampering with the ciphertext is detected on decryption. Considered quantum-safe under Grover's algorithm (effective security drops to 128 bits, which remains computationally infeasible). Used in Heirlooms via the `aes256gcm-v1` algorithm identifier.

**Algorithm agility** — see *Cryptographic agility*.

**Applied pi calculus (and spi calculus)**
A formal mathematical language for specifying and analysing security protocols. The applied pi calculus extends the pi calculus (a general model of concurrent processes communicating over channels) with cryptographic operations modelled as algebraic functions. The spi calculus (Abadi-Gordon 1999) is a closely related variant designed specifically for security protocols. Tools like ProVerif use the applied pi calculus to automatically verify security properties (secrecy, authentication) of protocols. Relevant to Heirlooms for formally modelling the chained capsule's unlock and cascade protocols and verifying that desired properties (e.g., "C₂ is never accessible to a non-winner") hold for an unbounded number of sessions.

**Attribute-Based Encryption (ABE)**
A public-key encryption scheme in which decryption is conditional on the decryptor's "attributes" satisfying a policy embedded in the ciphertext. Two main variants: Key-Policy ABE (KP-ABE), where the policy is in the decryption key; and Ciphertext-Policy ABE (CP-ABE, Bethencourt-Sahai-Waters 2007), where the policy is in the ciphertext. Policies can express boolean conditions over attributes, including time-based conditions (e.g., "current_time BETWEEN T₁ AND T₂"). Limitation: ABE requires a trusted attribute authority to issue keys, and time-based access is typically managed by re-keying rather than deletion of key material. Architecturally different from the Heirlooms window capsule model, which achieves time-bounding via tlock and custodian deletion. See also *Functional encryption (FE)*, *Window capsule*.

**Asynchronous Proactive Secret Sharing (APSS)**
A variant of Proactive Secret Sharing designed for networks where shareholders cannot be guaranteed to be online simultaneously. Standard PSS requires synchronised refresh epochs; APSS relaxes this requirement, allowing share refresh to proceed even when some shareholders are temporarily offline. Relevant for long-duration Heirlooms window capsules where custodian availability cannot be tightly coordinated. See also *Proactive Secret Sharing (PSS)*.

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

**Capsule reference token**
An encrypted pointer embedded inside a sealed capsule (e.g., C₁) that, when C₁ is legitimately decrypted, reveals the information needed to access the next capsule in the chain (e.g., C₂). In Heirlooms' chained capsule design, the capsule reference token is stored as plaintext inside C₁'s encrypted payload and contains: the UUID of C₂, and a link key (L₂) that provides access to C₂'s key material. The token is rendered as a QR code in the client UI for easy scanning. The token is only readable if C₁ is correctly unlocked within its time window; if C₁ expires without being opened, the token is inaccessible and C₂ is permanently destroyed. See also *Chained capsule*, *First-solver-wins*, *Expiry-as-death*.

**Chained capsule**
A directed acyclic graph (DAG) of Heirlooms capsules in which unlocking one capsule (C₁) is a precondition for gaining access to the next capsule (C₂). The chain is implemented via a nested key hierarchy: C₂'s key material (or a link key that unlocks C₂) is stored inside C₁'s plaintext. The chain can encode competitive delivery (first solver of C₁'s puzzle wins C₂), time windows for each capsule, and an expiry-as-death property (if C₁'s window closes without a solve, C₂ is permanently inaccessible). The construction is proposed in RES-004 and builds on the window capsule construction from RES-002. See also *Window capsule*, *Capsule reference token*, *First-solver-wins*, *Expiry-as-death*.

**Consent capsule**
A cryptographically timestamped, digitally signed record of a person's consent to a specific action or monitoring by a designated party (e.g., a power of attorney (POA) holder), stored in Heirlooms and revocable by the consenting person at any time while they retain capacity. In Heirlooms' Care Mode, the consent capsule is implemented as a W3C Verifiable Credential (VC) signed by the person using their Heirlooms signing key, with a trusted timestamp applied at creation and a revocation registry entry that the person can update to revoke. The consent capsule is not a window capsule in the cryptographic sense — it does not contain time-locked content — but it functions as a policy anchor for the Care Mode monitoring relationship. See also *Verifiable credential (VC)*, *Self-sovereign identity (SSI)*, *Care Mode*.

**Conditional proxy re-encryption (CPRE)**
A variant of proxy re-encryption in which a proxy can transform a ciphertext for a designated recipient only if a specified condition is met. For example, the condition might be a time window, an attribute, or a matching tag. If the condition is not met, the proxy cannot re-encrypt. CPRE enables fine-grained, policy-based delegation of decryption rights without exposing the underlying plaintext to the proxy. Relevant to Heirlooms as an alternative design pattern for chained capsule delivery: instead of a key hierarchy embedded in C₁'s plaintext, a CPRE proxy could re-encrypt C₂'s ciphertext for the winner upon condition verification. See also *Proxy re-encryption (PRE)*, *NuCypher/Umbral*.

**Custodian**
In the window capsule design, a party that holds one Shamir share of K_b (the expiry half of the window key). Custodians release their share only to an authenticated receiver during the valid [unlock_time, expire_time] window, and destroy it at expire_time. Custodians can be individuals, legal entities (law firms, banks, notaries), or distributed Heirlooms nodes. The expiry guarantee is only as strong as the honesty of more than (N − M) custodians in a (M, N) threshold scheme. See also *Shamir's Secret Sharing*, *Window capsule*, *Verifiable deletion*.

**Certified Deletion**
A cryptographic proof that a piece of information has been destroyed, in a way that is mathematically binding — the prover cannot have retained the data and still produce the proof. Provably impossible with classical cryptography alone: any classical deletion proof can be faked by a party who keeps a copy. Achievable with quantum information: quantum states have the property that measuring or copying them disturbs them irreversibly, so a valid deletion certificate implies the data is gone. Applied to Heirlooms window capsules: quantum certified deletion would provide a trustless expiry guarantee (custodians cannot fake deletion), but requires quantum communication infrastructure not available in production today. See also *Window capsule*, *Verifiable deletion*, *Proactive Secret Sharing (PSS)*.

**Commitment Scheme**
A two-phase cryptographic protocol in which a party commits to a value (the "commit" phase) without revealing it, and later reveals it (the "open" phase). The commitment is binding (the party cannot change the value after committing) and hiding (the commitment reveals nothing about the value). Used in Heirlooms window capsules: custodians commit to their Shamir shares at sealing time, providing an audit trail. At expire_time, they publish a deletion certificate — though classical commitment-based deletion certificates can be faked (a party can retain the share and still pretend to delete it). Contrast with *Certified Deletion* (which is binding). See also *Verifiable deletion*.

**Cat-qubit**  
A type of physical qubit that encodes quantum information in superpositions of coherent states of a microwave resonator. Cat qubits have biased noise properties — one type of error is exponentially suppressed — making them potentially more resource-efficient for fault-tolerant quantum computation than surface-code qubits. Recent 2025–2026 papers using cat-qubit architectures have materially reduced physical qubit estimates for breaking P-256.

**Cryptographic agility**  
The property of a system that allows its underlying cryptographic algorithms to be swapped without redesigning the entire system. Heirlooms' envelope format achieves agility by storing an explicit algorithm identifier string in every encrypted blob. Adding a new algorithm (e.g. a post-quantum key encapsulation mechanism) requires implementing a new codec and registering a new ID string — no wire-format change needed. This is the correct foundation for a post-quantum migration.

**CRYSTALS-Dilithium** — see *ML-DSA*.

**CRYSTALS-Kyber** — see *ML-KEM*.

---

## D

**Deletion Certificate**
A statement (or cryptographic proof) published by a custodian after destroying their Shamir share, asserting that the deletion has taken place. Classical deletion certificates (e.g., a signed timestamped statement, or a hash preimage) are weak — a dishonest custodian can publish the certificate while secretly retaining the share. Quantum certified deletion eliminates this weakness. In the absence of quantum infrastructure, Heirlooms relies on institutional accountability (named custodians), on-chain audit trails, and hardware enforcement (Nitro/HSM) as defence-in-depth. See also *Certified Deletion*, *Verifiable deletion*, *Custodian*.

**DEK** (Data Encryption Key)  
A random symmetric key generated per uploaded file. Each file's bytes, thumbnail, and encrypted metadata are encrypted under their DEK using AES-256-GCM. The DEK itself is then wrapped (encrypted) under the user's master key, also using AES-256-GCM. This two-layer model means the master key never directly encrypts file content, and a PQC migration of the key-wrapping layer does not require re-encrypting file content.

**DEK re-wrap**
The operation of decrypting a Data Encryption Key that was wrapped under one algorithm (e.g. AES-256-GCM under the old master key) and immediately re-encrypting it under the new algorithm (e.g. AES-256-GCM under the new master key). The DEK plaintext value is transiently in memory on the client device but never transmitted. Re-wrap cost is O(DEKs), not O(file bytes) — this is what makes PQC migration tractable even for large vaults. See also *Re-wrap*, *Migration phase*.

**drand** (Distributed Randomness)  
A distributed randomness beacon operated by the League of Entropy, a consortium of organisations including Cloudflare, EPFL, and others. Nodes use threshold BLS signatures over BLS12-381 to collaboratively produce publicly verifiable, unbiasable random values at regular intervals ("rounds"). Used as the foundation for the tlock time-lock scheme. drand acknowledges that BLS12-381 is not quantum-safe but estimates the threat is at least 5 years away.

---

## E

**Expiry-as-death**
The property of a chained capsule chain in which the failure to solve C₁'s puzzle before C₁'s expire_time results in C₂ becoming permanently inaccessible to everyone — including the sender. The term was coined in the context of Heirlooms' RES-004 research. In the recommended implementation, expiry-as-death is achieved by embedding C₂'s key material inside C₁'s plaintext: since C₁ was never decrypted, C₂'s key material was never exposed and is destroyed transitively when C₁'s custodians delete their Shamir shares at expire_time. The property is trust-bounded (not trustless) under the same custodian-deletion trust model as the window capsule expiry. See also *Chained capsule*, *Window capsule*, *Custodian*, *Certified Deletion*.

**Expire time**
In a window capsule, the upper time bound after which the capsule becomes permanently undecryptable. Distinct from the *unlock time* (lower bound). After expire_time, custodians destroy their Shamir shares of K_b, making K_window irrecoverable even though K_a (the tlock component) remains permanently public. A receiver who did not access the capsule during [unlock_time, expire_time] can never access it. A receiver who did access it during the window holds K_window locally and is unaffected by the expiry. See also *Window capsule*, *Unlock time*, *Custodian*.

**Epoch (secret sharing context)**
A time period in Proactive Secret Sharing during which a fixed set of shares is valid. At the end of each epoch, shareholders execute a share refresh protocol, generating new shares of the same underlying secret. Old shares are deleted and become useless. A mobile adversary that compromises fewer than the threshold of shareholders within a single epoch cannot reconstruct the secret, even if it compromises different shareholders in different epochs. The epoch length is a security parameter — shorter epochs provide stronger security against slow-moving adversaries but require more frequent protocol executions. See also *Proactive Secret Sharing (PSS)*, *Mobile adversary*.

**ECDH** (Elliptic Curve Diffie-Hellman)  
A key agreement protocol in which two parties each have a keypair on an elliptic curve. They exchange public keys and each independently derives the same shared secret from their own private key and the other party's public key. In Heirlooms, P-256 ECDH is used to wrap the master key to each device's public key (`p256-ecdh-hkdf-aes256gcm-v1`). **Broken by Shor's algorithm** on a quantum computer.

**ECDLP** (Elliptic Curve Discrete Logarithm Problem)  
The hard mathematical problem underlying all elliptic curve cryptography: given a point P on a curve and the point Q = k·P, find the integer k. Classical algorithms require exponential time; Shor's quantum algorithm solves it in polynomial time, which is why all ECC is quantum-vulnerable.

**Envelope format**  
Heirlooms' versioned binary container for all encrypted blobs. Contains: version byte, algorithm ID length, algorithm ID string, nonce, ciphertext, and authentication tag (plus ephemeral public key for asymmetric variants). The algorithm ID field is what gives the format its cryptographic agility. See `docs/envelope_format.md`.

---

## F

**Fair exchange**
A cryptographic protocol property in which two parties swap values (e.g., a signature for payment, or a secret for a key) such that either both parties receive what they expect or neither does. Fair exchange is impossible without a trusted third party (TTP) in purely classical settings, but blockchain smart contracts can replace the TTP with a trustless contract. In the chained capsule context, the first-solver-wins mechanism is NOT a fair exchange problem — it is an exclusive delivery problem where one winner gets C₂ and all others get nothing. The asymmetry distinguishes it from classical fair exchange. See also *First-solver-wins*, *Hash Time-Lock Contract (HTLC)*.

**First-solver-wins**
The competitive delivery property of a chained capsule in which the first of N recipients to submit a valid solution to C₁'s puzzle claims exclusive access to C₂. Implemented in Heirlooms via a server-mediated atomic claim: the first valid submission atomically marks C₂ as "claimed" for the winner; subsequent valid submissions from other recipients are rejected. The mechanism is analogous to a Hash Time-Lock Contract (HTLC) preimage reveal — whoever reveals the correct preimage first to the coordinator claims the locked output — but implemented off-chain with the Heirlooms server as coordinator rather than a blockchain. See also *Chained capsule*, *HTLC*, *Fair exchange*, *Verifiable Time-Lock Puzzle (VTLP)*.

**Forward-secure encryption**
An encryption scheme where compromise of a key at time T does not enable decryption of messages encrypted before T. Achieved by evolving the key forward in time — each new time period uses a new key derived from the previous one, and the previous key is deleted. Forward security protects past messages from future key compromise. It is distinct from the expiry problem in window capsules (where the goal is to prevent access *after* a time, not to protect past data from future key exposure). See also *Puncturable encryption*, *Window capsule*.

**Functional encryption (FE)**
A generalisation of public-key encryption in which a decryption key can be issued for a function f: decrypting a ciphertext with a key for function f reveals f(plaintext) rather than the full plaintext. For example, an inner-product FE key reveals only the inner product of the plaintext vector with the key vector. FE can model conditional decryption (the decryption key is issued for the function "output the message if condition C is satisfied; output ⊥ otherwise"). General-purpose FE with arbitrary functions remains computationally impractical in 2026; deployed FE is limited to linear functions (inner products). Attribute-Based Encryption (ABE) is a special case of FE. In the long horizon, FE with time-window conditions could replace the custodian-based window capsule, but is not deployable today. See also *Attribute-Based Encryption (ABE)*, *Witness Encryption (WE)*.

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

**Gradual release (timed secret sharing)**
One of the two strategies proposed by Kavousi et al. (2024) for enforcing an upper time bound in Timed Secret Sharing. Under gradual release, the shares required to reconstruct the secret are released incrementally over time. After the expire_time, enough shares have been released that reconstruction becomes possible — but this is not the Heirlooms model. Heirlooms uses the opposite strategy: shares are *deleted* at expire_time, making reconstruction impossible thereafter. See also *Timed Secret Sharing (TSS)*, *Window capsule*, *Short-lived proofs*.

**Grover's algorithm**  
A quantum algorithm that provides a quadratic speedup for unstructured search. Applied to symmetric cryptography, it halves the effective key length: AES-256 becomes equivalent to AES-128 under Grover. 128-bit effective security is still considered computationally infeasible. This is why AES-256-GCM, HKDF-SHA-256, and Argon2id are considered quantum-safe, while P-256 (broken by Shor, not Grover) is not.

---

## H

**Hardware Security Module (HSM)**
A dedicated, physically tamper-resistant device for storing cryptographic keys and executing cryptographic operations. HSMs automatically zeroize (destroy) key material when tamper is detected (e.g., if the device is opened). Certified under FIPS 140-2/3 Level 3. In the context of window capsules, an HSM at a custodian node could hold Shamir shares and be programmed to zeroize them at expire_time, providing hardware-enforced deletion with physical tamper evidence. HSMs do not provide cryptographic proof of deletion (anyone who tampers with the device would see zeroization evidence), but they provide the strongest currently practical hardware guarantee. Contrast with *Intel SGX* (software enclave, more flexible but more vulnerable) and *AWS Nitro Enclaves* (cloud-hosted TEE).

**Hash Time-Lock Contract (HTLC)**
A type of smart contract (and the corresponding protocol) that locks funds or key material under two conditions: (1) a hash lock — release requires revealing a secret preimage of a published hash; (2) a time lock — if the preimage is not revealed before a deadline, the locked asset is returned to the sender. HTLCs are the foundational mechanism of the Bitcoin Lightning Network and cross-chain atomic swaps. In the chained capsule context, the HTLC is the on-chain analogue of the first-solver-wins atomic claim: the puzzle answer is the preimage; the key material for C₂ is the locked asset; the first solver to reveal the correct preimage claims C₂'s key. Heirlooms uses an off-chain server-mediated equivalent rather than an on-chain HTLC for v1. See also *First-solver-wins*, *Timed commitment*, *Fair exchange*.

**HKDF** (HMAC-based Key Derivation Function)  
A key derivation function that takes a shared secret (e.g. the output of ECDH) and derives one or more cryptographic keys of any desired length. Heirlooms uses HKDF-SHA-256 as part of the `p256-ecdh-hkdf-aes256gcm-v1` algorithm. Considered quantum-safe under Grover.

**HPKE** (Hybrid Public Key Encryption)
A standardised public-key encryption framework (RFC 9180) that combines a Key Encapsulation Mechanism (KEM), a Key Derivation Function (KDF), and an Authenticated Encryption with Associated Data (AEAD) scheme. HPKE is the recommended compositional framework for post-quantum-hybrid encryption. Apple's CryptoKit (iOS 26) high-level PQ API is based on HPKE with X-Wing as the KEM component. For Heirlooms, HPKE is the conceptual model underlying the `hybrid-p256-mlkem768-hkdf-aes256gcm-v1` algorithm ID.

**HNDL** (Harvest Now, Decrypt Later)  
A surveillance strategy in which an adversary captures and stores encrypted traffic today, intending to decrypt it when a sufficiently powerful quantum computer becomes available. Particularly relevant for long-lived data: a family photo archive encrypted today may still be sensitive in 30 years. Multiple intelligence and cybersecurity agencies (US DHS, UK NCSC, ENISA, ACSC) base their post-quantum migration guidance on the assumption that nation-state actors are actively conducting HNDL operations. This is the most immediate reason Heirlooms needs a P-256 migration plan — the threat is not future, it is present.

**HQC** (Hamming Quasi-Cyclic)  
A post-quantum key encapsulation mechanism selected by NIST in March 2025 as a second KEM standard alongside ML-KEM. Based on error-correcting codes rather than lattices, providing algorithm diversity.

**Hybrid key exchange**  
A key agreement scheme that combines a classical algorithm (e.g. X25519 or P-256) with a post-quantum algorithm (e.g. ML-KEM) such that an attacker must break *both* to recover the shared secret. Provides HNDL protection immediately — a future quantum computer cannot retrospectively decrypt traffic protected by a hybrid scheme unless it can also break the post-quantum component. X25519+ML-KEM-768 is already deployed in over one-third of HTTPS traffic on Cloudflare's network.

---

## I

**Intel SGX** (Software Guard Extensions)
A set of CPU instructions that create hardware-isolated memory regions ("enclaves") for sensitive code and data. Code running inside an SGX enclave is protected from inspection by the operating system, hypervisor, or physical attackers (with caveats). SGX provides a trusted time source (coarse-grained, from the Platform Services Enclave) and monotonic counters. In the window capsule context, Heirlooms-operated custodian nodes running in SGX enclaves could be programmed to zeroize shares at expire_time, with the enclave code attested via remote attestation. Limitations: (1) SGX's trusted time depends on the Intel ME and is not calendar-precise; (2) SGX has a significant published vulnerability history (Spectre variants, Foreshadow, SGAxe, Plundervolt); (3) attestation proves the enclave ran specific code, but not that shares were not exfiltrated before the enclave ran. Use as defence-in-depth, not as a sole enforcement mechanism. See also *Nitro Enclave (AWS)*, *Hardware Security Module (HSM)*.

**i-TiRE** (Incremental Timed-Release Encryption)
A practical timed-release encryption scheme for blockchain environments (CCS 2022). Allows efficient updating of ciphertexts across time periods without re-encryption of the underlying data. Addresses the lower-bound problem only (when decryption becomes available, not when it expires). Not a direct prior art for the window capsule upper bound.

**IBE** (Identity-Based Encryption)  
A form of public-key encryption where the public key can be an arbitrary string (e.g. an email address or a future date). A trusted authority holds a master secret and issues private keys corresponding to identities on request. The tlock scheme uses IBE over BLS12-381 to encrypt to a future drand round identifier — only when that round's private key (the threshold BLS signature) is published can the ciphertext be decrypted.

---

## K

**K_a, K_b, K_window**
The three key components in the Heirlooms window capsule construction. K_window = K_a ⊕ K_b is the 256-bit key used to wrap the DEK. K_a is a 256-bit random value encrypted via tlock — it is revealed trustlessly after the drand round corresponding to unlock_time, and remains permanently public thereafter. K_b is a 256-bit random value Shamir-split among custodians — it is reconstructable from a threshold of shares during [unlock_time, expire_time], and becomes irrecoverable after custodians delete their shares at expire_time. Neither K_a alone nor K_b alone can reconstruct K_window. See also *Window capsule*, *tlock*, *Shamir's Secret Sharing*, *Custodian*.

**KDF** (Key Derivation Function)
A function that derives one or more secret keys from a shared secret (e.g. a Diffie-Hellman or KEM output). In Heirlooms, HKDF-SHA-256 is the KDF in `p256-ecdh-hkdf-aes256gcm-v1`. For hybrid key exchange, the KDF combines both the P-256 ECDH shared secret and the ML-KEM-768 shared secret into a single derived key — an attacker must break both algorithms to recover the result.

**Key rotation**
The process of generating new cryptographic keys and migrating all key-wrapped material to use them, then deleting the old keys. In Heirlooms' PQC migration context, key rotation has two levels: (1) shallow rotation — re-wrapping the master key under a new algorithm while keeping the same master key value; (2) full rotation — generating a new master key and re-wrapping every DEK under it. Only full rotation closes the HNDL attack window for already-harvested data. See also *Re-wrap*, *Attack window*.

**KEM** (Key Encapsulation Mechanism)  
A cryptographic primitive used to securely transmit a symmetric key from one party to another using public-key cryptography. The sender encapsulates a random key using the recipient's public key; only the recipient can decapsulate it using their private key. ML-KEM is the post-quantum KEM standardised in FIPS 203, intended to replace ECDH in key exchange protocols.

---

## L

**League of Entropy** — see existing entry (previously defined in RES-001 section).

**Lit Protocol**
A production-deployed decentralised threshold key management and conditional decryption network. Lit nodes each hold a share of a shared BLS key. When a user requests decryption, Lit nodes individually verify that the user satisfies the configured Access Control Conditions (which can include on-chain state such as token ownership, NFT holdings, smart contract conditions, and time windows). If the conditions are satisfied, nodes release their decryption key shares; the user combines enough shares (threshold) to reconstruct the decryption key. Lit Protocol uses a combination of threshold BLS cryptography and Trusted Execution Environments (TEEs). In 2024, Lit fulfilled over 24 million cryptographic requests. Relevant to Heirlooms as a potential vendor option for the custodian tier of window capsules and chained capsules, providing conditional key release without Heirlooms operating its own custodian infrastructure. See also *Custodian*, *Threshold signature*, *Window capsule*.

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

**Mobile adversary**
An adversary in a distributed system that does not remain fixed to a single set of corrupted parties over time, but can change which parties it has compromised between time epochs. In Proactive Secret Sharing, the mobile adversary model assumes the adversary corrupts at most a threshold t parties in any given epoch, but may corrupt entirely different parties in a later epoch. Over the full lifetime of the secret, the adversary may have compromised every party — but never simultaneously enough to reach the threshold in a single epoch. PSS is designed to remain secure against mobile adversaries. See also *Proactive Secret Sharing (PSS)*, *Epoch (secret sharing context)*.

**Master key**  
In Heirlooms, a 256-bit random symmetric key generated on a user's first device. Never leaves any device in plaintext. Each device holds its own copy, wrapped (encrypted) to that device's P-256 public key. The master key wraps per-plot keys; plot keys wrap per-file DEKs.

**Migration phase**
In the Heirlooms PQC migration, a numbered stage of the overall migration plan, tracked per-device in `devices.pqc_migration_phase`: Phase 0 — hybrid key codec implemented (no production impact); Phase 1 — ML-KEM keypair generated and uploaded; Phase 2 — master key re-wrapped from P-256 to hybrid scheme (silent, on next auth); Phase 3 — new master key generated, all DEKs re-wrapped (background service); Phase 4 — shared plot keys and item sharing DEKs re-wrapped. Each phase closes one layer of the HNDL attack window. See also *DEK re-wrap*, *Re-wrap*, *Attack window*.

**ML-KEM-768 key sizes**
ML-KEM-768 parameter sizes relevant for implementation and wire format planning: encapsulation key (public key) = 1184 bytes; decapsulation key (private key) = 2400 bytes; ciphertext = 1088 bytes; shared secret = 32 bytes. Compare to P-256: public key = 65 bytes (uncompressed SEC1), private key = 32 bytes, shared secret = 32 bytes. The larger key and ciphertext sizes require schema and envelope field length changes when adopting ML-KEM.

---

## N

**Nitro Enclave (AWS)**
An isolated compute environment on AWS EC2 instances, providing hardware-backed isolation for sensitive workloads. Nitro Enclaves produce cryptographic attestation documents (signed by the AWS Nitro Attestation PKI) that prove the exact code running inside the enclave. When combined with AWS KMS, KMS key policies can be conditioned on enclave attestation measurements — only the specific, attested enclave code can call KMS. In the window capsule context, Heirlooms-operated custodian nodes running inside Nitro Enclaves could use a KMS-managed share encryption key that is automatically deleted at expire_time via a scheduled KMS key deletion, providing hardware-backed expiry enforcement. This shifts trust from the custodian's software to AWS + the enclave code attestation. See also *Hardware Security Module (HSM)*, *Intel SGX*.

**NuCypher / Umbral (threshold proxy re-encryption)**
NuCypher is a decentralised network (now part of Threshold Network) offering threshold proxy re-encryption via the Umbral scheme. Umbral is a split-key proxy re-encryption scheme: the re-encryption key is split into fragments held by independent re-encryption nodes ("Ursulas"). A threshold M of N nodes must cooperate to re-encrypt data for a delegatee. NuCypher also offers Condition-Based Decryption (CBD): decryption is only permitted if an on-chain or off-chain condition is satisfied. Time-based and attribute-based conditions are supported. Relevant to Heirlooms as a design analogue for the custodian tier: rather than holding Shamir shares of K_b, proxy re-encryption nodes hold fragments of a re-encryption key and only re-encrypt if the time-window condition is satisfied. See also *Proxy re-encryption (PRE)*, *Conditional proxy re-encryption (CPRE)*, *Lit Protocol*.

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

**Programmable cryptography**
A framing (circa 2024) for a "second generation" of cryptographic primitives — specifically ZK-SNARKs, fully homomorphic encryption (FHE), and secure multi-party computation (MPC) — that allow arbitrary computations to be performed on top of cryptographic objects with guarantees of verifiability (prove a computation was done correctly), confidentiality (without revealing private inputs), and non-interactivity. Unlike classical cryptographic primitives (signatures, encryption, commitments) which are fixed in their operations, programmable cryptography allows a developer to express nearly any computation as a cryptographic object. In the chained capsule context, programmable cryptography could allow the puzzle-check and condition-verification logic to be executed directly on encrypted inputs (via FHE) or proved without revealing data (via ZK-SNARKs), removing the need for a trusted coordinator. Deployable for targeted applications within 5–7 years. See also *Functional encryption (FE)*, *Witness Encryption (WE)*.

**Proxy re-encryption (PRE)**
A cryptographic primitive that allows a semi-trusted proxy to transform a ciphertext encrypted under one public key into a ciphertext decryptable under a different public key, without the proxy ever seeing the plaintext. The original encryptor provides the proxy with a re-encryption key specific to the delegated recipient. PRE enables delegation of decryption rights (access control) without sharing private keys. Threshold PRE (used in NuCypher/Umbral) distributes the proxy's role across multiple nodes, so that no single node can perform re-encryption alone. Conditional PRE (CPRE) extends this: the proxy re-encrypts only if a condition is met (e.g., a time window is active). Temporally-scoped PRE is directly relevant to chained capsule delivery. See also *Conditional proxy re-encryption (CPRE)*, *Lit Protocol*.

**Post-quantum cryptography (PQC)**  
Cryptographic algorithms designed to be secure against both classical and quantum computers. Distinct from "quantum cryptography" (which uses quantum physics, e.g. QKD). NIST's finalised PQC standards are based on lattice problems (ML-KEM, ML-DSA), hash functions (SLH-DSA), and error-correcting codes (HQC).

**Plot key**  
In Heirlooms, a 256-bit symmetric key specific to a shared plot. Wrapped to each member's sharing public key (P-256 ECDH) and stored in `plot_members.wrapped_plot_key`. The plot key wraps per-item DEKs via `plot-aes256gcm-v1`.

**Proactive Secret Sharing (PSS)**
A technique introduced by Herzberg, Jarecki, Krawczyk, and Yung (CRYPTO 1995) for refreshing Shamir shares periodically so that a slow-moving adversary cannot accumulate enough shares to reconstruct the secret. Time is divided into epochs. At each epoch boundary, shareholders run a re-sharing protocol that produces fresh shares of the same underlying secret, then delete their old shares. An adversary that compromises up to the threshold in each epoch — but never simultaneously across epochs — cannot recover the secret. The fundamental assumption is that honest parties actually delete their old shares. PSS does not solve the expiry problem (it is designed for indefinite longevity, not deliberate destruction), but it strengthens the trust assumption during the access window of a window capsule. Recommended for Heirlooms window capsules with windows longer than 12 months. See also *Shamir's Secret Sharing*, *Mobile adversary*, *Epoch (secret sharing context)*, *Window capsule*, *Asynchronous Proactive Secret Sharing (APSS)*.

**Puncturable Encryption**
A public-key encryption scheme where the secret key can be "punctured" on a specific ciphertext: the punctured key can decrypt all other ciphertexts but not the punctured one. Used to achieve forward secrecy in messaging (Signal-style), where each received message causes the decryption key to be punctured on that message's ciphertext, preventing retroactive decryption if the key is later compromised. Green and Miers (2015) proposed a practical construction using Bloom filter data structures. In the window capsule context, puncturable encryption provides a modelling analogy for share deletion. See also *Forward-secure encryption*.

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

**Self-sovereign identity (SSI)**
A digital identity model in which individuals control their own identity data without relying on a central authority. SSI uses Decentralised Identifiers (DIDs — W3C standard) as globally unique identifiers anchored to a registry (such as a blockchain or a distributed ledger) rather than a central provider. Identity claims are represented as Verifiable Credentials (VCs — W3C standard) cryptographically signed by an issuer and stored in the user's own wallet. Users share only the attributes relevant to each interaction (selective disclosure) and can revoke credentials at any time. Relevant to Heirlooms for the consent capsule in Care Mode: a person's consent to monitoring by a POA holder can be represented as a VC issued by the person, revocable by the person, and verifiable by care providers without contacting Heirlooms' servers. See also *Verifiable credential (VC)*, *Consent capsule*.

**Shamir's Secret Sharing**  
A cryptographic scheme for splitting a secret into N shares such that any K shares (the threshold) can reconstruct the secret, but fewer than K shares reveal nothing.

**SPQR** (Sparse Post-Quantum Ratchet)
Signal's implementation of a post-quantum ratchet mechanism, deployed October 2025. SPQR runs alongside the existing Double Ratchet protocol using ML-KEM-768, producing what Signal calls the Triple Ratchet. Both the classical DH ratchet and the ML-KEM ratchet must be broken simultaneously for an attacker to recover session keys. Relevant to Heirlooms as a reference design if messaging features are ever added; demonstrates that hybrid PQC protocols can be deployed in production without flag-day client migration. Used in Heirlooms M11 to distribute capsule key shares to nominated executors. The threshold-of-N design tolerates executor attrition over a multi-decade horizon.

**Short-lived proofs (timed secret sharing)**
One of the two strategies proposed by Kavousi et al. (2024) for enforcing an upper time bound in Timed Secret Sharing. Under the short-lived proofs strategy, custodians provide time-limited proofs of share validity that expire at T_expire. Receivers must obtain valid proofs before T_expire; after that point, shares are no longer accompanied by valid proofs and cannot be used for reconstruction. The Heirlooms approach is conceptually related — custodians refuse to release shares after expire_time — but enforces this via policy and hardware rather than cryptographic proof expiry. See also *Gradual release (timed secret sharing)*, *Timed Secret Sharing (TSS)*.

**Shor's algorithm**  
A quantum algorithm published by Peter Shor in 1994 that solves integer factorisation and the discrete logarithm problem (including ECDLP) in polynomial time. This is what makes RSA, Diffie-Hellman, and all elliptic curve cryptography (including P-256 and BLS12-381) quantum-vulnerable. Does not affect symmetric cryptography or hash functions.

**SLH-DSA** (Stateless Hash-based Digital Signature Algorithm)  
Post-quantum digital signature standard (FIPS 205). Derived from SPHINCS+. Based entirely on hash functions — if SHA-256 is secure, SLH-DSA is secure. Slower and produces larger signatures than ML-DSA but provides algorithm diversity as a backup.

**Surface code**  
The most widely studied quantum error correction code. Requires approximately 1,000 physical qubits per logical qubit but has relatively high error thresholds, making it practical with current hardware. Surface-code estimates for breaking P-256 require millions of physical qubits; more efficient codes (LDPC, cat-qubit) dramatically reduce this.

---

## T

**Timed commitment**
A cryptographic commitment scheme (Boneh-Naor 2000) in which a committed value is guaranteed to become recoverable after a specified time delay, even without the committer's cooperation — but cannot be recovered before that delay. A timed commitment combines a regular commitment (hiding and binding before the delay) with a time-lock puzzle (enabling recovery after the delay). Used in blockchain contexts (sealed-bid auctions, fair contract signing) where all parties commit to values simultaneously and the commitment can be "forced open" after a deadline if a party refuses to reveal. In the chained capsule context, a solver who has found C₁'s answer could use a timed commitment to prove they committed to a solution before the window closed, even if they submit the reveal after a network delay. See also *Time-lock puzzle*, *Timed Secret Sharing (TSS)*, *Hash Time-Lock Contract (HTLC)*.

**Timed Secret Sharing (TSS)**
A cryptographic primitive introduced by Kavousi, Abadi, and Jovanovic (ASIACRYPT 2024) that generalises secret sharing with both a lower time bound (secret cannot be reconstructed before T_unlock) and an upper time bound (secret cannot be reconstructed after T_expire). The most directly relevant academic prior work to Heirlooms' window capsule construction. The paper proposes two strategies for the upper bound: short-lived proofs and gradual release. Heirlooms' construction is a distinct practical instantiation using drand/tlock for the lower bound and custodian deletion for the upper bound — an approach the paper acknowledges but does not implement. See also *Window capsule*, *Short-lived proofs (timed secret sharing)*, *Gradual release (timed secret sharing)*.

**Timed-release encryption (TRE)**
A family of encryption schemes guaranteeing that a ciphertext cannot be decrypted before a specified future time. The main classical approaches are: (1) time-lock puzzles (computational lower bound, no trusted party); (2) trusted time server with IBE (single trusted party releases the key at the right time); (3) distributed time server with threshold BLS, as in tlock/drand (distributed trust). All TRE schemes address the lower bound only. Combining TRE with an upper bound (expiry) is the subject of Timed Secret Sharing and the Heirlooms window capsule. See also *tlock*, *Timed Secret Sharing (TSS)*, *Time-Specific Encryption (TSE)*.

**Time-lock puzzle**
A computational puzzle designed to take a known amount of wall-clock time to solve, even on the fastest available hardware. Rivest, Shamir, and Wagner (1996) proposed the original construction based on repeated squaring modulo an RSA modulus — this computation cannot be parallelised. The puzzle forces a delay without requiring any trusted party. The weakness: calibrating puzzle hardness to a specific time duration is imprecise (hardware speeds change), and a legitimate receiver cannot solve the puzzle faster than an adversary. Used as the lower-bound mechanism in some TRE constructions; drand/tlock provides a superior alternative for Heirlooms because the lower bound is cryptographically exact (tied to a specific drand round) rather than computational.

**Time-Specific Encryption (TSE)**
A cryptographic primitive introduced by Paterson and Quaglia (SCN 2010) that allows a sender to specify a time interval [T1, T2] during encryption. A trusted Time Server broadcasts a Time Instant Key (TIK) at each time step; a receiver can only decrypt if they have a TIK corresponding to a time within the specified interval. Supports public-key and identity-based extensions. The most conceptually similar academic prior work to the window capsule: TSE explicitly models a time interval for access, not just a release time. However, TSE requires the Time Server to be online throughout the entire interval, and does not model a deletion mechanism — access is controlled by TIK distribution, not by destruction of key material. See also *Timed Secret Sharing (TSS)*, *Window capsule*.

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

**Verifiable credential (VC)**
A W3C-standardised digital document containing cryptographically verifiable claims. A VC is issued by an issuer (e.g., a person, organisation, or institution), refers to a subject (e.g., the person giving consent), and contains signed claims (attributes). The issuer's signature allows any verifier to confirm the claims are authentic without contacting the issuer. VCs support selective disclosure (revealing only a subset of claims) and revocation (the issuer can invalidate the credential). In Heirlooms' Care Mode, the consent capsule is implemented as a VC: the person (issuer and subject) signs a consent claim granting a POA holder monitoring access; this consent is revocable by updating a revocation registry entry. See also *Self-sovereign identity (SSI)*, *Consent capsule*.

**Verifiable Delay Function (VDF)**
A function that takes a specified minimum amount of time to compute (even with unlimited parallelism), and produces a unique output with a short proof that can be verified quickly. VDFs can serve as a trustless time-lock mechanism: the output is only available after the delay has elapsed. Boneh et al. (2018) formalised VDFs. Relevant to Heirlooms as an alternative or supplement to drand/tlock for the lower bound in window capsules. VDFs do not require a distributed network like drand, but require trusted setup for some constructions. Not currently used in Heirlooms.

**Verifiable Time-Lock Puzzle (VTLP)**
An extension of time-lock puzzles (Rivest-Shamir-Wagner 1996) in which the puzzle generator publishes a succinct proof that the puzzle's solution satisfies a specified NP relation — without revealing the solution itself. This allows solvers to verify that the puzzle is "worth solving" (its solution will produce a useful output, e.g., unlocking the next capsule) before committing the computational resources required to solve it. The "Check-Before-you-Solve" paper (Xin, Papadopoulos, IEEE 2025) gives a construction instantiated with Groth16 ZK proofs: proving a VTLP for a BLS signature requires 1.37 seconds with constant proof size and 1ms verification, regardless of the puzzle difficulty parameter T. Directly applicable to Heirlooms chained capsules: the puzzle inside C₁ would be a VTLP, letting solvers verify that a correct solution exists and unlocks C₂ before investing effort. See also *Time-lock puzzle*, *Witness Encryption (WE)*, *First-solver-wins*.

**Verifiable deletion**
A cryptographic proof that a party has destroyed secret material (such as a Shamir share) without revealing the material itself. An open research problem as of 2026. In theory: a custodian commits to their share at sealing time; at expire_time they publish a proof of deletion derived from the commitment. Related to "proof of erasure." Solving this cleanly for the window capsule context would be a publishable research contribution and would make the expiry guarantee auditable rather than merely trusted. See also *Custodian*, *Window capsule*, *Certified Deletion*, *Deletion Certificate*.

---

## W

**Window capsule**
A Heirlooms capsule construction (proposed by CTO, 2026-05-16) with both an unlock time (lower bound, enforced by tlock) and an expire time (upper bound, enforced by threshold custodian deletion). Content is permanently undecryptable by anyone — server, sender, receiver, or adversary — after expire_time, provided the threshold of custodians honestly destroys their Shamir shares of K_b. The construction is: K_window = K_a ⊕ K_b, where K_a is tlock-encrypted (trustless lower bound) and K_b is Shamir-split across N custodians (trust-bounded upper bound). The expiry guarantee is not trustless — see *Verifiable deletion*, *Custodian*. Formalisation is the subject of RES-002.

---

## X

**Witness Encryption (WE)**
A cryptographic scheme (Garg, Gentry, Sahai, Waters — STOC 2013) that allows encrypting a message to an NP statement: only a party holding a valid witness for the statement can decrypt. General-purpose WE is currently impractical (requires multilinear maps or obfuscation). Special-purpose WE for targeted applications is now closer to practical (Garg et al., CRYPTO 2025: WE from linearly verifiable SNARKs). In the window capsule context, WE could theoretically provide a trustless upper bound — the decryption witness could be "proof that no blockchain block exists with timestamp > expire_time" — eliminating the need for custodians. This remains a research horizon: no production-ready WE implementation capable of expressing time-window conditions exists as of 2026. Horizon estimate: 5–10 years for special-purpose practical deployment. See also *Window capsule*, *Timed Secret Sharing (TSS)*.

**X-Wing**
A general-purpose post-quantum hybrid KEM (draft-connolly-cfrg-xwing-kem-10, March 2026) combining X25519 and ML-KEM-768. An attacker must break both the classical X25519 component and the post-quantum ML-KEM-768 component simultaneously to recover the shared secret. Adopted by Apple in CryptoKit (iOS 26) as `XWingMLKEM768X25519`, with formal verification and Secure Enclave support. Internet-Draft status as of May 2026 (not yet an RFC). Heirlooms' Android/web hybrid scheme uses P-256 (not X25519) as the classical component — the Technical Architect must decide whether to adopt X25519 uniformly to enable X-Wing on all platforms.

**X25519**  
An elliptic curve Diffie-Hellman function using Curve25519 (a Montgomery curve). Widely used in TLS and other protocols. Like P-256, it is **not quantum-safe** (vulnerable to Shor's algorithm). The X25519+ML-KEM-768 hybrid key exchange is the current de facto standard for post-quantum TLS deployment.
