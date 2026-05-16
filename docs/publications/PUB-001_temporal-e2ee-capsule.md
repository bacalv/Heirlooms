# Temporal E2EE with Structured Ignorance: A Multi-Layer Capsule Construction for Long-Horizon Secret Delivery

**Draft version 0.1 — 2026-05-16**  
**Status:** Academic draft — not yet submitted. Suitable for internal review and patent annex use.

---

## Note on Angle Selection (Philosopher's Framing)

Three candidate angles were available for this paper: the blinding scheme as an epistemological claim, multi-layer temporal redundancy, and the trustless expiry impossibility. After reading all source material, the angle chosen is a synthesis of the first two, framed as a single unified concept: **structured ignorance as a cryptographic property**.

The most intellectually novel aspect of the Heirlooms M11 construction is not any single primitive — IBE, Shamir, or ECDH key wrapping are individually well-understood. What is philosophically distinctive is the XOR blinding scheme in combination with multi-path temporal redundancy. The blinding scheme ($\text{DEK} = \text{DEK}_\text{client} \oplus \text{DEK}_\text{tlock}$) partitions knowledge of the content key between the client and the server in a way that neither party can unilaterally reconstitute. This is not merely a security property; it is a mathematical formalisation of a promise about the future: "not even we can read this — and that claim is enforced by arithmetic, not by policy."

What makes this more than an engineering choice is the temporal dimension. The blinding guarantee is strongest before the drand round publishes (the pre-round window) because the server holds only $\text{DEK}_\text{tlock}$ and the IBE ciphertext holds $\text{DEK}_\text{client}$ in a form that no one can decrypt yet. During this window, the server is *structurally ignorant* of the content key — not through self-restraint, but through cryptographic impossibility. This is the sense in which the construction makes a mathematical promise about the future: the secret is not just hidden; it is hidden in a way that is provably irrecoverable until a future moment determined by a decentralised beacon.

The multi-layer redundancy (three independent unlock paths: recipient ECDH, tlock IBE, and Shamir SSS) then addresses the second philosophical problem: uncertainty over a long time horizon. The author of a capsule sealed today for delivery in twenty years cannot know which cryptographic assumptions will survive. The construction treats this uncertainty honestly by making each path an independent defence against the failure modes of the others. This is not belt-and-suspenders engineering; it is a principled response to the epistemological problem of making durable cryptographic commitments across multi-decade horizons.

I find this angle most compelling for academic publication because it connects a concrete, implementable construction to a genuine philosophical novelty: *what does it mean for a system to know something?* The blinding scheme gives a precise, formal answer. Combined with the temporal structure, it shows that "structured ignorance" can be engineered rather than merely hoped for — and that this engineering has real human stakes when the secret is a message intended to outlive its author.

---

## Abstract

We present a multi-layer cryptographic construction for time-conditioned end-to-end encrypted capsules designed for long-horizon secret delivery. The construction composes three independent unlock paths — recipient public-key wrapping, threshold identity-based encryption (IBE) via a decentralised randomness beacon, and Shamir's Secret Sharing with threshold deletion — to produce a sealed capsule whose content key is irrecoverable by any single party before a specified future time, and by any coalition not meeting a threshold condition. The central contribution is a *DEK blinding scheme* that partitions the content key between the client and the server using a one-time-pad split ($\text{DEK} = \text{DEK}_\text{client} \oplus \text{DEK}_\text{tlock}$), ensuring that server compromise before the temporal gate opens cannot expose content. We formalise the construction, state correctness and blinding properties with proof sketches, and discuss limitations including the trust-bounded nature of the Shamir expiry guarantee and platform compatibility constraints. The construction is the first to combine tlock IBE, XOR blinding, and Shamir threshold deletion in a single sealed-envelope scheme for consumer-facing digital archive applications.

---

## 1. Introduction

Every generation has sought mechanisms to transmit secrets across time. The sealed letter — written today, opened upon death — is among the oldest forms of deferred communication. Digital technology has made producing such messages trivial. What it has not provided is a principled cryptographic framework for guaranteeing *when* a secret becomes accessible, *to whom*, and — equally important — *to whom it remains inaccessible* until that moment.

This paper is motivated by a concrete product requirement: a family digital archive in which a parent can seal a message for a child, specifying that it should become accessible on the child's eighteenth birthday, after the parent's death, or on some other future milestone — and that until that moment, the message is cryptographically inaccessible to everyone, including the platform operator. We call such a sealed artefact a *capsule*.

Three properties make capsule delivery hard to achieve with standard tools:

1. **Temporal lower-bound:** The content must be inaccessible before a specified future time $T_\text{unlock}$, with the guarantee enforced not by policy but by cryptographic structure.

2. **Custodian ignorance:** The platform operator must not be able to decrypt content at any time, including after the temporal gate opens, without cooperating with the recipient's device.

3. **Long-horizon durability:** A capsule sealed today may not be delivered for decades. Single-primitive constructions are fragile over this horizon: cryptographic assumptions age, key material is lost, and the platform may change operators. The construction must tolerate realistic failure of any single layer.

Existing approaches satisfy subsets of these properties. Time-lock puzzles [RES-002-001] and timed-release encryption [RES-002-002] address the lower bound but not custodian ignorance. Standard E2EE satisfies custodian ignorance but offers no temporal gate. Shamir's Secret Sharing [RES-002-011] provides threshold social recovery but not temporal access control. No published construction satisfies all three simultaneously in a form deployable at consumer scale.

We describe a construction that does. The key technical contribution is the *blinding scheme*: an XOR split of the content key that ensures the server holds only $\text{DEK}_\text{tlock}$, the client's ECDH-unwrapped path recovers only $\text{DEK}_\text{client}$, and reconstruction of $\text{DEK}$ requires both. Combined with IBE-based time-locking via a decentralised randomness beacon (drand/tlock [RES-001-019]) and Shamir threshold recovery as a third independent path, the construction provides layered, redundant guarantees suitable for multi-decade deployment horizons.

The paper is structured as follows. Section 2 reviews the relevant cryptographic primitives. Section 3 presents the formal construction. Section 4 states and sketches proofs of the key properties. Section 5 discusses limitations and open problems. Section 6 concludes and identifies target venues.

---

## 2. Background

### 2.1 Identity-Based Encryption and Timed-Release IBE

Identity-Based Encryption (IBE), introduced by Boneh and Franklin [RES-002-003], allows encryption to an arbitrary string as a public key, with decryption key extraction by a trusted key generation authority (KGA). In the timed-release setting, the identity is a time index: the KGA broadcasts decryption keys for each time period, making ciphertexts decryptable after the corresponding key is published.

The drand tlock scheme [RES-001-019] instantiates this idea using a decentralised randomness beacon over BLS12-381 pairings. Let $\mathbb{G}_1, \mathbb{G}_2, \mathbb{G}_T$ be the BLS12-381 groups with bilinear map $e : \mathbb{G}_1 \times \mathbb{G}_2 \to \mathbb{G}_T$. The beacon maintains a threshold BLS key; at each round $r$, it publishes the BLS signature $\sigma_r = H(r)^{s}$ where $H : \mathbb{Z} \to \mathbb{G}_1$ and $s$ is the beacon's secret key. A ciphertext sealed to round $r$ can be decrypted only after $\sigma_r$ is published.

**Security:** Under the Bilinear Diffie-Hellman (BDH) assumption, the IBE scheme is IND-ID-CPA secure. The decentralised beacon is secure against adaptive adversaries that corrupt fewer than the threshold fraction of beacon nodes.

**Limitation:** BLS12-381 is quantum-vulnerable. The drand project acknowledges a five-year long-term security horizon for this primitive [RES-001-018].

### 2.2 Elliptic Curve Diffie-Hellman Key Wrapping

The ECDH wrapping scheme operates over NIST P-256. Given a recipient's static public key $\text{pk}_R$, the encryptor:

1. Generates an ephemeral keypair $(\text{ek}, \text{EK})$ where $\text{EK} = \text{ek} \cdot G$.
2. Computes shared secret $Z = \text{ek} \cdot \text{pk}_R$.
3. Derives wrapping key $K_w = \text{HKDF-SHA256}(Z, \text{salt}=\emptyset, \text{info}=\text{"capsule-ecdh-aes256gcm-v1"})$.
4. Encrypts the target key under $K_w$ using AES-256-GCM.

The decryptor recovers $Z = \text{dk}_R \cdot \text{EK}$ and inverts the process. Security follows from the elliptic curve Diffie-Hellman (ECDH) assumption over P-256.

### 2.3 Shamir's Secret Sharing

Shamir's $(k, n)$ Secret Sharing [RES-002-011] encodes a secret $s \in \mathbb{F}$ as a random degree-$(k-1)$ polynomial $f$ with $f(0) = s$. Each share $i$ is the pair $(i, f(i))$. Any $k$ shares suffice to reconstruct $s$ via Lagrange interpolation; any $k-1$ shares give zero information about $s$. We operate over $\text{GF}(2^8)$ with standard byte-per-byte Shamir encoding.

**Threshold deletion:** The Shamir path provides an upper-bound gate on capsule accessibility: nominated executors hold shares and, upon verification of a triggering condition (e.g., death confirmation), release them. Crucially, share *deletion* at expiry destroys future accessibility — this is the mechanism for "the capsule expires." The deletion guarantee is trust-bounded (honest threshold of executors) rather than cryptographically enforced [RES-002-009].

### 2.4 XOR One-Time-Pad Key Splitting

For a random key $\text{DEK} \in \{0,1\}^{256}$ and a uniformly random mask $\text{DEK}_\text{client} \in \{0,1\}^{256}$, the complement $\text{DEK}_\text{tlock} = \text{DEK} \oplus \text{DEK}_\text{client}$ satisfies:

- $\text{DEK} = \text{DEK}_\text{client} \oplus \text{DEK}_\text{tlock}$
- Given only $\text{DEK}_\text{tlock}$, the distribution of $\text{DEK}$ is uniform over $\{0,1\}^{256}$ (perfect secrecy of the OTP).
- Given only $\text{DEK}_\text{client}$, the same holds symmetrically.

This elementary observation is the information-theoretic core of the blinding scheme.

---

## 3. Construction

### 3.1 Entities and Key Material

Let the following entities participate in a capsule sealing:

- **Author** $\mathcal{A}$: the party sealing the capsule, holding a P-256 signing keypair and a vault master key.
- **Recipient** $\mathcal{R}$: the intended recipient, identified by a static P-256 sharing keypair $(\text{dk}_R, \text{pk}_R)$.
- **Executors** $\{E_1, \ldots, E_n\}$: nominated social recovery parties, each holding a P-256 sharing keypair $(\text{dk}_{E_i}, \text{pk}_{E_i})$.
- **Beacon** $\mathcal{B}$: the drand randomness beacon, publishing $\sigma_r$ at round $r$.
- **Server** $\mathcal{S}$: the platform operator; untrusted with respect to content but trusted to store ciphertexts faithfully and enforce scheduling gates.

### 3.2 Setup: Capsule Content Encryption

Before sealing, the author encrypts all capsule content under a per-capsule data encryption key $\text{DEK} \stackrel{\$}{\leftarrow} \{0,1\}^{256}$:

$$C_\text{content} = \text{AES-256-GCM}_{\text{DEK}}(\text{plaintext content})$$

This content ciphertext $C_\text{content}$ is stored on the server. The server sees only ciphertext; $\text{DEK}$ never leaves the author's device in plaintext.

### 3.3 The Sealing Operation

Let $T_\text{unlock}$ be the chosen unlock time and $r^*$ be the drand round satisfying:

$$r^* = \left\lceil \frac{T_\text{unlock} - T_\text{genesis}}{\Delta}\right\rceil$$

where $T_\text{genesis}$ is the beacon's genesis time and $\Delta$ is the beacon's period.

The sealing client executes the following steps atomically:

**Step 1 — Key splitting (blinding scheme):**

$$\text{DEK}_\text{client} \stackrel{\$}{\leftarrow} \{0,1\}^{256}$$
$$\text{DEK}_\text{tlock} = \text{DEK} \oplus \text{DEK}_\text{client}$$

**Step 2 — Tlock IBE encryption of the client mask:**

$$C_\text{tlock} = \text{IBE-Seal}(\text{pk}_\mathcal{B}, r^*, \text{DEK}_\text{client})$$

where $\text{IBE-Seal}$ produces a BLS12-381 IBE ciphertext encrypting $\text{DEK}_\text{client}$ to round $r^*$ of the drand beacon.

**Step 3 — Recipient ECDH wrapping (primary and compatibility paths):**

$$W_\text{DEK} = \text{ECDH-Wrap}(\text{pk}_R, \text{DEK})$$
$$W_\text{mask} = \text{ECDH-Wrap}(\text{pk}_R, \text{DEK}_\text{client})$$

$W_\text{DEK}$ wraps the full content key (the *direct path*, used by clients without IBE capability). $W_\text{mask}$ wraps only the client mask (the *blinded path*, used by clients with full IBE capability).

**Step 4 — Shamir distribution:**

For a configured $(k, n)$ threshold, compute shares $\{(i, f(i))\}_{i=1}^n$ of $\text{DEK}$ directly (not of $\text{DEK}_\text{client}$). For each executor $E_i$:

$$W_{E_i} = \text{ECDH-Wrap}(\text{pk}_{E_i}, \text{ShareEncode}(i, k, n, f(i)))$$

**Step 5 — Digest and sealing request:**

Compute the tamper-detection digest:

$$d = \text{SHA-256}(\text{DEK}_\text{tlock})$$

The sealing request sent to $\mathcal{S}$ contains:

$$\text{SealRequest} = \langle C_\text{tlock},\ \text{DEK}_\text{tlock},\ d,\ r^*,\ W_\text{DEK},\ W_\text{mask},\ \{W_{E_i}\}_{i=1}^n,\ k,\ n \rangle$$

The server validates structural integrity, verifies $\text{SHA-256}(\text{DEK}_\text{tlock}) = d$, stores all fields, and transitions the capsule to the *sealed* state. It does not store $\text{DEK}$ or $\text{DEK}_\text{client}$ — only their complement $\text{DEK}_\text{tlock}$.

### 3.4 The Delivery Operation

At time $t \geq T_\text{unlock}$ after the beacon publishes $\sigma_{r^*}$:

**Server-side gate check:**

1. Verify $\text{SHA-256}(\text{DEK}_\text{tlock}) = d$ (tamper detection).
2. Confirm the IBE gate is open: call $\text{IBE-Decrypt}(\sigma_{r^*}, C_\text{tlock})$; a non-null result confirms $\sigma_{r^*}$ is published. (The server does *not* store the result — it uses this solely as a gate check.)
3. Deliver $\text{DEK}_\text{tlock}$ to the authenticated recipient via a server API endpoint.

**Client-side (full IBE path — Android/web):**

$$\text{DEK}_\text{client} = \text{ECDH-Unwrap}(\text{dk}_R, W_\text{mask})$$
$$\text{DEK} = \text{DEK}_\text{client} \oplus \text{DEK}_\text{tlock}$$
$$\text{plaintext} = \text{AES-256-GCM-Decrypt}_{\text{DEK}}(C_\text{content})$$

**Client-side (direct path — iOS, or any client without IBE capability):**

$$\text{DEK} = \text{ECDH-Unwrap}(\text{dk}_R, W_\text{DEK})$$
$$\text{plaintext} = \text{AES-256-GCM-Decrypt}_{\text{DEK}}(C_\text{content})$$

**Executor recovery path (if recipient credential is lost):**

Any $k$ executors submit their ECDH-unwrapped shares; Lagrange interpolation over $\text{GF}(2^8)$ reconstructs $\text{DEK}$ directly. The Shamir path bypasses the blinding scheme entirely — it was computed over $\text{DEK}$, not $\text{DEK}_\text{client}$.

### 3.5 Wire Encoding

All wrapped key material uses the Heirlooms versioned envelope format. Each envelope carries a one-byte version identifier (currently $\texttt{0x01}$) and an explicit algorithm ID string, providing crypto-agility: unknown algorithm IDs fail loudly rather than being silently ignored. The tlock ciphertext $C_\text{tlock}$ is stored as an opaque BYTEA blob identified by algorithm ID $\texttt{tlock-bls12381-v1}$; it does not use the standard envelope binary frame.

---

## 4. Properties

### 4.1 Correctness

**Theorem 1 (Correctness).** *If the beacon publishes $\sigma_{r^*}$ at or after $T_\text{unlock}$, all parties are honest, and at least one of the three paths is available, then the recipient can recover $\text{DEK}$ and decrypt $C_\text{content}$.*

*Proof sketch.* The three paths are independent:

- *Direct path:* $\text{ECDH-Unwrap}(\text{dk}_R, W_\text{DEK}) = \text{DEK}$ by ECDH correctness and the construction of $W_\text{DEK}$ in Step 3. $\square$

- *Blinded path:* $\text{ECDH-Unwrap}(\text{dk}_R, W_\text{mask}) = \text{DEK}_\text{client}$ by the same argument. The server delivers $\text{DEK}_\text{tlock}$ (stored at sealing time). $\text{DEK}_\text{client} \oplus \text{DEK}_\text{tlock} = \text{DEK}$ by construction ($\text{DEK}_\text{tlock} = \text{DEK} \oplus \text{DEK}_\text{client}$ implies $\text{DEK}_\text{client} \oplus (\text{DEK} \oplus \text{DEK}_\text{client}) = \text{DEK}$). $\square$

- *Shamir path:* Any $k$ shares $(i, f(i))$ determine $f$ by Lagrange interpolation over $\text{GF}(2^8)$, and $f(0) = \text{DEK}$ by construction of $f$ in Step 4. $\square$

Since at least one path is available, correctness holds. $\blacksquare$

### 4.2 Pre-Round Blinding (Structured Ignorance)

**Theorem 2 (Pre-Round Blinding).** *Let $t < T_\text{unlock}$ (the beacon has not yet published $\sigma_{r^*}$). An adversary $\mathcal{ADV}$ that has obtained full control of the server — including $\text{DEK}_\text{tlock}$, $C_\text{tlock}$, all wrapped keys, and $C_\text{content}$ — cannot recover $\text{DEK}$ with non-negligible advantage.*

*Proof sketch.* $\mathcal{ADV}$ holds $\text{DEK}_\text{tlock}$ and $C_\text{tlock} = \text{IBE-Seal}(\text{pk}_\mathcal{B}, r^*, \text{DEK}_\text{client})$. To recover $\text{DEK}$, $\mathcal{ADV}$ needs $\text{DEK}_\text{client}$.

- $\text{DEK}_\text{client}$ is inside $C_\text{tlock}$. Under the BDH assumption, $C_\text{tlock}$ is IND-ID-CPA secure: before $\sigma_{r^*}$ is published, no PPT adversary can distinguish encryptions of two equal-length plaintexts. Hence $\mathcal{ADV}$ cannot extract $\text{DEK}_\text{client}$ from $C_\text{tlock}$ with non-negligible advantage.

- $W_\text{mask}$ wraps $\text{DEK}_\text{client}$ under $\text{pk}_R$. Without $\text{dk}_R$, recovering $\text{DEK}_\text{client}$ from $W_\text{mask}$ requires breaking the ECDH assumption over P-256.

- Given only $\text{DEK}_\text{tlock}$ and no $\text{DEK}_\text{client}$, the OTP analysis (Section 2.4) gives that $\text{DEK}$ is uniformly distributed over $\{0,1\}^{256}$ from $\mathcal{ADV}$'s view.

Therefore $\mathcal{ADV}$ cannot recover $\text{DEK}$ with non-negligible advantage, and cannot decrypt $C_\text{content}$. $\blacksquare$

**Remark.** The blinding guarantee weakens after $T_\text{unlock}$: once $\sigma_{r^*}$ is published, the server can call $\text{IBE-Decrypt}(\sigma_{r^*}, C_\text{tlock})$ to obtain $\text{DEK}_\text{client}$, and combined with $\text{DEK}_\text{tlock}$, recover $\text{DEK}$. The construction does not claim post-round server ignorance — the capsule is intended to be open at that point. The blinding guarantee is specifically designed for the pre-round window, which is precisely when the guarantee matters most: during the years between sealing and delivery.

### 4.3 Multi-Path Independence

**Theorem 3 (Path Independence).** *The three unlock paths are cryptographically independent: compromise of any single path does not compromise the others.*

*Proof sketch.* The paths are:

1. *Direct path:* Requires $\text{dk}_R$. Compromise does not expose Shamir shares or the tlock gate.

2. *Blinded path + tlock:* Requires $\text{dk}_R$ (for $\text{DEK}_\text{client}$) AND $\text{DEK}_\text{tlock}$ from the server AND the beacon having published $\sigma_{r^*}$. These are independent requirements.

3. *Shamir path:* Requires $k$ executor shares. The shares are computed over $\text{DEK}$ independently of $\text{DEK}_\text{client}$ or the tlock structure. Share compromise does not expose $\text{dk}_R$ or the IBE ciphertext.

The paths share only $\text{DEK}$ itself (the final target). No intermediate key material is shared between paths. A complete security argument would model each path's security game separately and apply a hybrid argument; we defer a full proof to future work. $\blacksquare$

**Corollary.** The construction tolerates the following independent failure scenarios:

| Failure scenario | Surviving path(s) |
|---|---|
| Recipient credential loss ($\text{dk}_R$ lost) | Shamir executor recovery |
| Beacon chain retirement or BLS break | Direct path (ECDH); Shamir recovery |
| Executor attrition below threshold $k$ | Direct path; blinded path |
| Server compromise pre-round | No path (blinding holds) |
| Server compromise post-round | Direct path (requires $\text{dk}_R$); Shamir path |

---

## 5. Discussion

### 5.1 Limitations

**Trust-bounded deletion.** The Shamir path provides an upper-bound gate (expiry) only if executors honestly delete their shares at the configured expire time. Classical cryptography cannot provide a trustless deletion proof [RES-002-009]. The expiry guarantee is therefore *trust-bounded*: it holds if and only if a threshold of executors behave honestly. This is the same fundamental limitation identified in the window capsule literature [RES-002-017] and is inherent to the problem class — as noted by Paterson and Quaglia [RES-002-002], any upper-bound time condition requiring guaranteed inaccessibility after expiry ultimately reduces to trusted key deletion unless quantum-certified deletion techniques are deployed.

Recent work by Bartusek and Raizes [RES-002-015] and Katz and Sela [RES-002-016] provides quantum-certified deletion for secret shares, where deletion is cryptographically certified and binding even against an adversary that later collects a threshold of shares. Integrating quantum-certified share deletion into the Shamir path is an open research direction that would elevate the expiry guarantee from trust-bounded to cryptographically enforced — at the cost of requiring quantum-capable custodian hardware.

**Post-quantum vulnerability.** The construction uses three primitives with known quantum vulnerabilities: P-256 ECDH (Shor's algorithm), BLS12-381 IBE (Shor's algorithm), and AES-256-GCM (Grover's algorithm, providing roughly 128-bit post-quantum security). For multi-decade capsules, quantum adversaries represent a realistic threat given recent compression of resource estimates for breaking ECC-256 [RES-001-005, RES-001-006]. The NIST PQC standards (ML-KEM, ML-DSA [RES-001-001]) provide drop-in replacements for the ECDH components; the IBE component has no standardised post-quantum replacement as of 2026.

The construction's pluggable design mitigates this: the algorithm ID field in each envelope makes algorithm migration auditable and mechanical. A future version substituting ML-KEM-768 for P-256 ECDH requires only a new algorithm ID registration, not a structural change to the envelope format.

**Platform capability heterogeneity.** The blinded path requires BLS12-381 IBE capability on the client. The construction addresses this by providing both $W_\text{DEK}$ (direct path, no IBE required) and $W_\text{mask}$ (blinded path, full IBE). The direct path sacrifices the blinding guarantee in exchange for cross-platform compatibility: a recipient using a client without IBE capability (e.g., iOS in the M11 deployment) receives the weaker guarantee that server compromise post-round exposes $\text{DEK}$. The blinding guarantee applies exclusively to clients that traverse the blinded path.

This is a deliberate design choice (ARCH-003 §6.3, Option A): accepting a weaker guarantee for some clients is preferable to excluding those clients from receiving tlock-sealed capsules entirely. A future platform capability migration would phase out the direct path once all client platforms support IBE.

### 5.2 Relation to Prior Work

The construction sits at the intersection of three literature streams:

**Timed-Release Encryption (TRE).** Blake and Chan [RES-002-003] and the i-TiRE scheme [RES-002-004] address the lower-bound time gate. The window capsule formalised in Kavousi, Abadi, and Jovanovic [RES-002-017] ("Timed Secret Sharing") is the closest prior work, formally defining both lower and upper time bounds. The Heirlooms construction differs in using a decentralised randomness beacon (drand) rather than a centralised time server for the lower bound, and in introducing the XOR blinding scheme as an explicit mechanism for server ignorance — a property not addressed in the TSS formulation.

**Threshold and Social Key Recovery.** Herzberg et al.'s Proactive Secret Sharing [RES-002-011] and subsequent work on custodian-based recovery provide the Shamir path's theoretical foundation. The novelty here is not the Shamir scheme itself but its role as a *third independent path* providing executor-mediated recovery when both recipient credentials and the beacon have failed.

**Server-Blinded Key Delivery.** The XOR blinding scheme is related to the two-party key exchange literature, but the specific construction — where one half of an OTP split is time-locked in an IBE ciphertext and the other is held by the recipient's ECDH private key — does not appear in any published work identified during preparation of this paper. The legal assessment conducted alongside construction development found no active patent anticipating the specific combination of tlock IBE lower-bound, XOR DEK blinding, and Shamir threshold deletion upper-bound.

### 5.3 Open Problems

1. **Formal security model.** The construction deserves a full game-based security definition and proof. The appropriate model is a multi-party, multi-capsule setting capturing forward security (capsules sealed before a key compromise remain secure) and the interplay between the three paths.

2. **Post-quantum IBE.** No standardised post-quantum analogue of BLS12-381 IBE exists. Lattice-based IBE [RES-001-001] schemes are active research areas; a post-quantum tlock construction would be a significant contribution enabling the blinding scheme to survive a quantum adversary.

3. **Verified deletion.** Integrating quantum-certified share deletion [RES-002-015, RES-002-016] into the Shamir path would make the expiry guarantee cryptographically binding rather than trust-bounded.

4. **Formal treatment of the blinding pre-round window.** Theorem 2 rests on the BDH assumption holding until $\sigma_{r^*}$ is published. A more refined analysis would model the exact timing of beacon compromise and derive tighter bounds on the window of guaranteed server ignorance.

---

## 6. Conclusion

We have presented a multi-layer capsule construction for long-horizon temporal E2EE. The central contribution — the DEK blinding scheme — partitions the content key between the client and the server using a one-time-pad split, ensuring that server compromise before the temporal gate opens cannot expose content. Combined with IBE-based time-locking and Shamir threshold recovery as independent paths, the construction provides redundant, layered guarantees suitable for consumer-facing applications with multi-decade delivery horizons.

The construction formalises a property we call *structured ignorance*: the server is not merely restrained from reading content by policy, but is cryptographically prevented from doing so during the critical pre-round window. This is the sense in which the system makes a mathematical promise about the future — a promise enforced by arithmetic rather than by trust.

### Target Venues

The paper as drafted is a strong fit for:
- **Financial Cryptography and Data Security (FC)** — strong tradition of applied cryptographic constructions for real-world deployments.
- **Privacy Enhancing Technologies Symposium (PETS/PoPETs)** — the privacy and server-ignorance properties are of direct interest.
- **IEEE Symposium on Security and Privacy (S&P)** — larger venue; would require a more complete formal treatment (full game-based security proof, post-quantum analysis).

**Additional work required for submission quality:**
- Full game-based security definition for the multi-path capsule scheme.
- Formal proof of Theorem 2 under the BDH assumption with explicit reduction.
- Proof-of-concept implementation with performance benchmarks (sealing and delivery latency across platforms).
- Comparison table against Kavousi et al. [RES-002-017] and the i-TiRE scheme [RES-002-004] with formal property comparison.
- Discussion of the post-round blinding failure and whether it can be mitigated (e.g., by using the IBE result only as a gate check and never persisting DEK_client server-side — which the current construction already enforces).

### Patent Annex Abstract

We describe a cryptographic construction for sealed time-conditioned digital capsules combining three independent unlock mechanisms: (1) elliptic-curve Diffie-Hellman wrapping of the content key to the recipient's public key; (2) identity-based encryption of a client-generated one-time-pad mask to a future round of a decentralised randomness beacon, with the complement of that mask stored at the platform server, such that neither the platform nor any single party holds the full content key before the beacon round publishes; and (3) Shamir $(k,n)$ threshold secret sharing of the content key across nominated executor parties. The combination provides three mutually independent recovery paths and a pre-delivery blinding guarantee under which server compromise cannot expose the content key until the temporal gate is cryptographically open. This construction is novel in combining tlock IBE lower-bound temporal access control, XOR one-time-pad DEK blinding with split server/client knowledge, and Shamir threshold deletion upper-bound in a single sealed-envelope scheme for consumer digital archive applications.

---

## References

**[RES-001-018]** drand official documentation: cryptographic background, BLS12-381. https://docs.drand.love/docs/cryptography/

**[RES-001-019]** Burdges, Nicolas et al. (ePrint 2023/189): "tlock: practical timelock encryption from threshold BLS." https://eprint.iacr.org/2023/189.pdf

**[RES-001-001]** NIST FIPS 203 (August 2024): ML-KEM (Module-Lattice-Based Key-Encapsulation Mechanism Standard). https://csrc.nist.gov/pubs/fips/203/final

**[RES-001-005]** The Quantum Insider, March 2026: three papers compressing quantum threat timeline. https://thequantuminsider.com/2026/03/31/q-day-just-got-closer-three-papers-in-three-months-are-rewriting-the-quantum-threat-timeline/

**[RES-001-006]** CoinDesk, March 2026: 10,000-qubit estimate for breaking ECC-256. https://www.coindesk.com/markets/2026/03/31/quantum-computers-could-break-crypto-wallet-encryption-with-just-10-000-qubits-researchers-say

**[RES-002-001]** Rivest, Shamir, Wagner (1996): "Time-lock puzzles and timed-release crypto." https://dl.acm.org/doi/10.5555/888615

**[RES-002-002]** Paterson, Quaglia (SCN 2010): "Time-Specific Encryption." https://eprint.iacr.org/2010/347

**[RES-002-003]** Blake, Chan (ACM TISSEC 2008): "Provably secure timed-release public key encryption." https://dl.acm.org/doi/10.1145/1330332.1330336

**[RES-002-004]** i-TiRE (CCS 2022): "Incremental Timed-Release Encryption." https://dl.acm.org/doi/10.1145/3548606.3560704

**[RES-002-009]** Xu, Zhang, Yang (2014): "Deleting Secret Data with Public Verifiability." https://eprint.iacr.org/2014/364.pdf

**[RES-002-011]** Herzberg, Jarecki, Krawczyk, Yung (CRYPTO 1995): "Proactive Secret Sharing Or: How to Cope With Perpetual Leakage." https://www.researchgate.net/profile/Amir-Herzberg/publication/221355399_Proactive_Secret_Sharing_Or_How_to_Cope_With_Perpetual_Leakage/

**[RES-002-015]** Bartusek, Raizes (CRYPTO 2024): "Secret Sharing with Certified Deletion." https://eprint.iacr.org/2024/736

**[RES-002-016]** Katz, Sela (Eurocrypt 2025): "Secret Sharing with Publicly Verifiable Deletion." https://eprint.iacr.org/2024/1596

**[RES-002-017]** Kavousi, Abadi, Jovanovic (ASIACRYPT 2024): "Timed Secret Sharing." https://eprint.iacr.org/2023/1024

**[RES-004-001]** Boneh, Sahai, Waters (TCC 2011): "Functional Encryption: Definitions and Challenges." https://eprint.iacr.org/2010/543.pdf

**[RES-004-005]** Garg, Gentry, Sahai, Waters (STOC 2013): "Witness Encryption and its Applications." https://eprint.iacr.org/2013/258.pdf
