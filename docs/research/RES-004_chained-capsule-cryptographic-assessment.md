# RES-004 — Chained Capsule: Cryptographic Assessment

**ID:** RES-004  
**Date:** 2026-05-16  
**Author:** Research Manager  
**Audience:** CTO, Technical Architect, Legal (see Patentability note)  
**Status:** Final  
**Depends on:** RES-002 (window capsule construction)

---

## Executive Summary

The chained capsule construction — a DAG of window capsules in which unlocking one capsule is a precondition for accessing the next — is **novel as a complete system** but can be built almost entirely by composing known cryptographic primitives with the window capsule design from RES-002. No single mechanism in the proposed design requires a new mathematical primitive; what is novel is the specific combination, the competitive "first solver wins" delivery model, and the application to family-archive use cases.

The most technically challenging components of the chained capsule design are:

1. **First-solver-wins exclusivity** — enforcing that only one of N recipients can open C₂ after solving C₁'s puzzle. This cannot be achieved trustlessly with classical cryptography alone; it requires either a trusted coordinator or a threshold-honest server-side lock.

2. **Expiry-as-death** — the property that C₂ is permanently destroyed if C₁'s window closes without a solve — inherits the same fundamental limitation identified in RES-002: classical cryptography cannot prove deletion. The same threshold-honest custodian model applies, extended to the inter-capsule link.

3. **The capsule reference token** (the encrypted pointer inside C₁ pointing to C₂) is implementable by composing existing E2EE and key-derivation techniques. No new primitive is needed.

4. **The consent capsule** for Care Mode is well-supported by existing self-sovereign identity (SSI) and verifiable credential (VC) standards, with meaningful prior work from the UK Office of the Public Guardian's digital LPA programme.

The literature most relevant to this brief spans: functional and attribute-based encryption, witness encryption, timed commitment schemes, fair exchange protocols, proxy re-encryption with temporal delegation, programmable threshold networks (Lit Protocol, NuCypher/Umbral), smart contract conditional release (Cassiopeia, Zama fhEVM), and SSI consent management.

**Key conclusion for the CTO:** the chained capsule is a feasible engineering project using the RES-002 construction as its building block. Two components need careful design decisions before implementation: the first-solver-wins mechanism (recommended: server-mediated atomic claim) and the inter-capsule link (recommended: nested key hierarchy within the existing envelope format). Both are achievable without new cryptographic machinery. The consent capsule is a straightforward application of W3C Verifiable Credentials with on-chain revocation.

---

## Literature Review

### Conditional / Programmable Encryption

**Functional Encryption (FE)** — Boneh, Sahai, Waters (TCC 2011) defined functional encryption as the generalisation of attribute-based encryption in which decryption of a ciphertext produces not just the plaintext but f(plaintext, key), where f is a function encoded in the decryption key [RES-004-001]. The CTO's chained capsule concept is conceptually analogous to a specific functional encryption policy: "decrypt C₂ if and only if the decryptor holds a valid witness to C₁'s puzzle." In principle, this can be expressed as a functional encryption instance. In practice, general-purpose FE with arbitrary functions remains computationally impractical for production systems in 2026; current deployed FE is limited to inner-product and linear functions.

**Ciphertext-Policy Attribute-Based Encryption (CP-ABE)** — Bethencourt, Sahai, Waters (IEEE S&P 2007) [RES-004-002] allows the encryptor to specify an access policy over attributes. Time can be treated as an attribute (e.g., "current_time BETWEEN T₁ AND T₂"). This is a well-established primitive for conditional access. Limitation for Heirlooms: CP-ABE requires a trusted attribute authority to issue keys — it does not eliminate the need for a trusted coordinator, and it does not model deletion of key material after expiry. Time-conditioned CP-ABE schemes [RES-004-003] append a temporal attribute to the access tree; revocation is handled by re-keying rather than deletion. This is architecturally different from the window capsule model.

**Programmable Cryptography** — a 2024 framing [RES-004-004] that subsumes ZK-SNARKs, FHE, and MPC into a unified "second-generation" of cryptographic primitives where arbitrary programs can be executed on top of cryptographic objects. The three affordances — verifiability, confidentiality, non-interactivity — map well to the chained capsule's needs. Practically: ZK-SNARKs could prove that a puzzle answer is correct without revealing the answer (enabling a solver to prove correctness to a server, which then releases C₂'s key); FHE could allow the server to evaluate a puzzle-check function on encrypted inputs. These are research-horizon capabilities that may be deployable in 5–7 years at the level of performance required for a consumer product.

**Witness Encryption (WE)** — the ideal cryptographic primitive for chained capsule conditional delivery. Garg, Gentry, Sahai, Waters (STOC 2013) [RES-004-005] formalised WE: encrypt a message to an NP statement; only a party holding a valid witness (proof of solution) decrypts. A chained capsule would be expressed as: encrypt C₂'s DEK-wrap key to the NP statement "the solver holds a valid solution to C₁'s puzzle AND the current time is within [T₀+2d, T₀+3d]." If WE were practical, this would give a trustless, single-round conditional delivery.

The current state (2025–2026) of WE practicality:

- General-purpose WE: impractical. Requires multilinear maps or obfuscation.
- Special-purpose WE from linearly verifiable SNARKs: CRYPTO 2025 (Garg, Hajiabadi, Kolonelos, Kothapalli, Policharla) [RES-004-006] demonstrates that special-purpose WE for targeted applications can be built from weaker assumptions with concrete efficiency. The paper presents a framework ("gadgets") that enables WE for specific well-structured NP relations.
- Practical on-chain WE: Cassiopeia (FC 2023 Workshop on Trusted Smart Contracts) [RES-004-007] implements WE using a cryptoeconomic committee: nodes hold shares and reveal decryption shares only when a witness is publicly verified on-chain. This is not information-theoretically secure but is a deployable approximation of WE under an honest-majority assumption.
- Verifiable Time-Lock Puzzles for NP (VTLP-NP): "Check-Before-you-Solve" (Xin, Papadopoulos, IEEE 2025) [RES-004-008] extends time-lock puzzles to NP relations: the generator publishes a succinct ZK proof that the puzzle solution satisfies an NP relation, enabling solvers to verify the puzzle is "worth solving" without knowing the answer. Instantiated with Groth16, proving a VTLP for a BLS signature requires 1.37 seconds with constant-size proof and 1ms verification. This is directly applicable to the chained capsule: the puzzle inside C₁ would be a VTLP — solvers can verify that a correct answer exists and is useful (it unlocks C₂) before committing resources.

**Assessment:** Witness encryption is the theoretically clean solution for chained capsule conditional delivery. The CRYPTO 2025 WE framework and VTLP-NP are important recent advances, but production-ready WE systems capable of expressing the full time-window + puzzle-correctness condition do not exist as of mid-2026. Heirlooms should design the chained capsule using server-mediated mechanisms now, with WE as the target architecture for a future trustless upgrade path. The VTLP-NP result is immediately useful: Heirlooms can use it to prove to potential solvers that a puzzle inside a capsule is "real" (its solution will unlock something valuable) without revealing the answer.

---

### DAG Composition of Window Capsules

The fundamental composability question is: can two RES-002 window capsules be chained such that solving C₁ is a precondition for C₂ becoming available, and C₁'s expiry event can destroy C₂?

**Composability without new cryptographic machinery:**

The RES-002 construction encodes unlock/expire bounds into the key material (K_a via tlock for the lower bound; K_b via Shamir-custody for the upper bound). A chain C₁ → C₂ can be constructed by embedding C₂'s link key L₂ inside C₁'s plaintext, where L₂ is a key that wraps C₂'s DEK. This is a straightforward key hierarchy:

```
C₂'s content
  → encrypted under DEK₂

DEK₂
  → wrapped under K_window,2

K_window,2
  → components K_a,2 ⊕ K_b,2
  → K_a,2 = tlock(round₂)          [trustless lower bound for C₂]
  → K_b,2 = Shamir-custodian shares [trust-bounded upper bound for C₂]

One of:
  (a) K_b,2 is stored inside C₁'s plaintext.
      C₁'s custodians only release K_b,2 to a receiver who has
      previously provided a valid puzzle solution and unlocked C₁.
  
  (b) K_b,2 is held by a server, behind a server-enforced lock
      that only releases upon receiving proof of C₁ unlock.
```

**Approach (a)** — embedding K_b,2 inside C₁ — is elegant but has a critical limitation: once C₁ is legitimately unlocked, the receiver holds K_b,2 and can use it independently of whether C₂'s custodians have been informed. The inter-capsule dependency is not enforced cryptographically; it relies on C₂'s tlock lower bound (K_a,2) being the binding constraint until T₂_unlock, and K_b,2 being useless without K_a,2 before T₂_unlock.

**Approach (b)** — server-held K_b,2 released upon C₁ proof — creates a server as coordinator of the inter-capsule transition. This is implementable with existing mechanisms (the server verifies C₁ was unlocked, then releases C₂'s Shamir shares to C₂'s custodians) but introduces a Heirlooms server as a required participant in the chain traversal.

**Sequential TRE composability:** The academic literature on sequential time-lock puzzles [RES-004-009] (e.g., multi-instance time-lock puzzles / Chained TLP) provides a relevant framework: solving puzzle P₁ yields the hardness parameter for puzzle P₂, creating a chain. This is computationally sequential, not key-hierarchy sequential. For Heirlooms, the key-hierarchy approach (K_b,2 inside C₁ or on the server) is more appropriate because it maintains the clean separation of concerns in the envelope format rather than relying on sequential computation.

**Expiry-as-death cascade:** The property "if C₁'s window closes without a solve, C₂ is permanently inaccessible" is achievable by having C₂'s custodians hold their shares in an escrow that is released to the intended recipient(s) of C₂ only upon receiving confirmation from C₁'s custodians that the puzzle was solved within C₁'s window. If C₁'s window closes without a solve, C₂'s custodians delete their shares. This is a policy-layer mechanism, not a cryptographic one. It inherits the same trust assumptions as the window capsule: the expiry-as-death is trust-bounded (honest custodian threshold), not trustless.

**Assessment:** DAG composition of window capsules is achievable without new cryptographic machinery. The recommended approach is: embed C₂'s Shamir-share material inside C₁'s plaintext (approach (a)), with C₂'s tlock lower bound providing the time-separation between capsules. The inter-capsule dependency is enforced by the key hierarchy, not by a separate protocol. The expiry cascade is enforced by custodian policy. This is consistent with RES-002's trust model and requires no new primitives.

---

### First-Solver-Wins Mechanisms

The CTO's proposal includes competitive delivery: the first of N recipients to solve C₁'s puzzle wins exclusive access to C₂. This is a novel and technically challenging requirement.

**Why this is hard:** Cryptographic fairness literature largely addresses the opposite problem — ensuring that if one party receives a benefit, the other party also receives theirs (fair exchange). The "first solver wins, others get nothing" model is closer to competitive auction dynamics than to classical fair exchange.

**Related prior work:**

**Timed commitments and sealed-bid auctions:** Rivest-Shamir-Wagner (1996) timed commitments [RES-004-010] allow a bidder to commit to a value that anyone can eventually reveal by solving a time-lock puzzle. In a sealed-bid auction context, the "first to solve" wins the prize. The "Sealed-bid Auctions on Blockchain with Timed Commitment Outsourcing" (arXiv 2024) [RES-004-011] introduces a competition protocol where solvers race to decrypt timed commitments for payment. This is structurally similar to the chained capsule's competitive delivery but in a financial context.

**HTLC and atomic swaps:** Hashed Time-Lock Contracts (HTLCs), the core mechanism of the Lightning Network and cross-chain atomic swaps, implement a hash-lock / time-lock structure [RES-004-012]: reveal the preimage of hash H before time T to claim coins; after T, the sender reclaims. This is a "first claimer wins" primitive — whoever reveals the preimage first claims the output. For the chained capsule, C₁'s puzzle answer could function as the preimage of a hash: the first recipient to reveal the correct preimage to the Heirlooms server claims the lock on C₂.

**Cryptographic oracle conditional payments** (Madathil et al., eprint 2022/499) [RES-004-013] introduces verifiable witness encryption based on threshold signatures for oracle-based conditional payments. The construction allows encrypting payments that are released when a threshold of oracles attest to an event. This is relevant as a template for "condition is met → release key material."

**Commit-reveal schemes:** A classic approach to competitive scenarios: all participants commit to their answer, then reveal simultaneously. The first valid reveal wins. However, simultaneous reveal eliminates the "racing" dynamic. In an on-chain setting, frontrunning (a miner observing a reveal transaction and submitting their own first) is a known attack vector. FairBlock (eprint 2022/1066) [RES-004-014] addresses frontrunning using threshold IBE to delay when transactions become decryptable — providing order fairness in blockchain contexts.

**What "first solver wins" requires:**

The fundamental problem is exclusivity: once one solver has demonstrated a valid answer to the server, the server must atomically mark C₂ as "claimed" and ensure no second solver can also claim it. This is a distributed locking problem, not a purely cryptographic one. Options:

1. **Server-mediated atomic claim (recommended):** The Heirlooms server acts as coordinator. The first recipient to submit a valid puzzle answer (verified server-side) receives a "claim token" — a signed assertion that they won the race. The claim token is required for C₂'s custodians to release their shares. The server maintains a single "claimed / not claimed" flag per C₁ puzzle, protected by atomic write semantics. This is implementable with existing infrastructure (optimistic concurrency in the database, similar to a "first write wins" pattern). The trust assumption: Heirlooms server does not collude with losing parties to falsify the claim result.

2. **Smart contract mediation:** Deploy the lock on-chain. The first recipient to submit a valid ZK proof of puzzle solution to the smart contract claims C₂'s key material (or a hash lock to it). This is trustless with respect to Heirlooms, but introduces gas costs, public on-chain transaction visibility, and latency. More appropriate for high-value scenarios or if trustlessness is a priority.

3. **VTLP-based competition:** Using Verifiable Time-Lock Puzzles (Xin, 2025), the puzzle inside C₁ could be a VTLP where the solution, once found, must be submitted to a verifier before a deadline. The first valid submission wins. This provides a ZK proof of solution, preventing preimage copying, but still relies on the verifier (server or smart contract) to enforce exclusivity.

**Race condition risk:** In approach (1), a race condition exists if two recipients submit valid answers simultaneously. This is manageable with standard database-level locking (SELECT FOR UPDATE or equivalent). The claim is not a cryptographic primitive but a server-enforced coordination event — acceptable under Heirlooms' existing trust model, where the server is a necessary participant.

**Assessment:** First-solver-wins exclusivity is not a standard cryptographic primitive and does not appear verbatim in the literature as a named construction. It can be implemented as a composition of: HTLC-style hash-lock (the puzzle answer is a preimage), server-mediated atomic claim, and the window capsule's key hierarchy. The recommended implementation is server-mediated atomic claim (approach 1) for v1, with optional on-chain migration for trustless enforcement at higher value tiers.

---

### Expiry-As-Death Trustlessness

The property "if C₁'s window closes without a solve, C₂ is permanently inaccessible to anyone" is the inter-capsule analogue of the window capsule's expiry guarantee.

**The fundamental limitation (reiterated from RES-002):** Classical cryptography cannot prove deletion trustlessly. Any party who retains a copy of key material before the expiry event can fake a deletion certificate. This applies equally to the inter-capsule link: if C₂'s key material (K_b,2 or the Shamir shares) is held by parties who retain it after C₁ expires, C₂ is not permanently inaccessible.

**How the cascade works under the trust model:**

If C₁'s window closes without a solve:
- C₁'s custodians delete their Shamir shares of K_b,1. C₁ is permanently inaccessible (same as RES-002 expiry).
- C₂'s key material (wherever it is held: inside C₁'s plaintext or held by a server/C₂'s custodians) must also be destroyed.

The cascade link is the crux:
- **Option A (K_b,2 inside C₁'s plaintext):** K_b,2 was never accessible to anyone because C₁'s window closed without a solve. C₁'s plaintext was never decrypted. K_b,2 is therefore inaccessible by the same guarantee as C₁'s plaintext. This is the strongest option: the cascade expiry is guaranteed by C₁'s own expiry. No additional protocol is needed.
- **Option B (server-held K_b,2):** The server must delete K_b,2 at C₁'s expire_time. This requires the server to be trusted to execute the deletion. Same trust model as the window capsule custodian deletion.

**Option A is strongly preferred** for expiry-as-death: embedding C₂'s key material inside C₁'s plaintext means the cascade is automatic. If C₁ expires without a solve, C₂'s key material is never extracted from C₁, and C₁'s expiry destroys access to it transitively.

**Trustless limit:** Even with Option A, the expiry-as-death property of C₂ is no more trustless than C₁'s own expiry, which is threshold-honest (not trustless) per RES-002. The expiry-as-death guarantee is: "if the threshold of C₁'s custodians honestly deletes their shares of K_b,1 at expire_time, then C₂ is permanently inaccessible to anyone who did not solve C₁ within its window." This is the same trust level as the window capsule's own expiry guarantee. No additional trust is required for the cascade.

**Quantum certified deletion (long horizon):** As noted in RES-002, Bartusek-Raizes 2024 and Katz-Sela 2024 [RES-002-015, RES-002-016] provide quantum certified deletion for secret shares. If Heirlooms eventually deploys quantum shares for C₁, the expiry-as-death property of C₂ would also become cryptographically certified (since C₂'s key material inside C₁'s plaintext is protected by C₁'s quantum-certified deleted shares). This is the long-horizon research direction.

**Assessment:** Expiry-as-death is achievable under the same trust model as RES-002's window capsule expiry. The recommended design (K_b,2 embedded in C₁'s plaintext) provides the strongest cascade guarantee without additional trust assumptions. The property is trust-bounded, not trustless, by the same fundamental limitation identified in RES-002.

---

### Capsule Reference Tokens

A capsule reference token is the encrypted pointer inside C₁ that, when C₁ is decrypted, reveals a reference to C₂ (or the key material needed to access C₂). This is a key usability mechanism: the QR code inside C₁ that the winner scans to claim C₂.

**Crypto-pointers in the literature:** US patent 7,185,205 / 8,145,900 [RES-004-015] describes "crypto-pointers" — pairing a cryptographic key with each pointer in a data structure, such that the key is needed to access the data at the pointer's location. This is a memory-safety mechanism, not a document-chaining mechanism, but the concept is directly analogous.

**Structured encryption for linked documents:** The field of structured encryption (Cash, Tessaro 2013; Kamara et al.) [RES-004-016] addresses encrypting structured data (graphs, trees, linked lists) such that a server holding the encrypted structure can answer relational queries without learning the data. Applied to capsule chaining: the capsule graph (C₁ → C₂ → C₃) is an encrypted linked list. The encrypted pointer inside each capsule is a ciphertext of the next capsule's access token.

**Proxy re-encryption with chaining:** NuCypher's Umbral (threshold proxy re-encryption) [RES-004-017] allows an encryptor to delegate decryption to a re-encryption proxy, which re-encrypts data for a designated recipient. Chaining is possible: re-encryption of re-encryption, though each re-encryption link introduces the proxy into the trust model. For Heirlooms, proxy re-encryption is not needed because the capsule reference token is contained in plaintext inside C₁ — the "chaining" is key-hierarchy based, not re-encryption based.

**Capsule reference token design for Heirlooms:**

The simplest viable design stores, inside C₁'s plaintext, the following:

```
{
  "capsule_ref": {
    "capsule_id": "<C₂ UUID>",
    "link_key": "<L₂>",           // 256-bit random value
    "link_key_role": "dek_wrap"   // L₂ wraps K_window,2
  },
  "puzzle": { ... },
  "content": { ... }
}
```

Where L₂ is a link key that wraps K_window,2 (which in turn wraps DEK₂). The winner of C₁ extracts L₂ and presents it to C₂'s system, which verifies the claim (via the server-mediated atomic claim) and then releases the custodian shares of K_b,2.

Alternatively, L₂ could directly *be* K_b,2 (the Shamir secret itself), eliminating a key-hierarchy layer. This is simpler but means C₂'s K_b,2 is fully exposed to the C₁ winner. The additional wrapping layer (L₂ → K_b,2) allows the server to maintain a checkpoint (verifying the winner's claim before releasing K_b,2) without holding K_b,2 itself.

**QR code encoding:** The capsule reference token, once C₁ is decrypted, could be rendered as a QR code in the client UI. The QR code encodes {capsule_id, L₂} (or a URL pointing to the C₂ unlock flow). The winner scans it with an Heirlooms app; the app submits the claim to the server. This is a UX choice with no cryptographic implications.

**Assessment:** Capsule reference tokens (encrypted pointers from one sealed document to another) are implementable within the existing Heirlooms envelope format using a nested key hierarchy. No new cryptographic machinery is needed. The concept has scattered prior art in structured encryption and crypto-pointer literature but no published work specifically addressing the use case of inter-capsule references in consumer family archive software.

---

### Formal Notation for Capsule DAGs

The CTO proposed a notation:

```
C₁({A,B}, [T₀+2d→T₀+3d], {puzzle, ref→C₂}) → C₂({winner}, [T₀+4d→T₀+5d], {prize})
```

This is an informal process notation. The question is whether a formal academic framework exists that can express time-conditioned dependency graphs over encrypted documents.

**Timed automata:** The standard formalism for reasoning about time-conditioned systems. Timed automata (UPPAAL, Alur-Dill 1994) [RES-004-018] extend finite-state automata with clock variables. Security protocols with timestamps have been verified using timed automata (Corin et al.; Sprenger et al.) [RES-004-019]. The capsule DAG could be expressed as a timed automaton: each capsule is a state; transitions are labelled with guards (puzzle_solved AND T_unlock ≤ now < T_expire); clocks enforce the time bounds.

A formal model of C₁ → C₂ in timed automaton style:
```
State: LOCKED_C₁ —[puzzle_solved ∧ T₀+2d ≤ now < T₀+3d]→ CLAIMED_C₂
State: LOCKED_C₁ —[now ≥ T₀+3d ∧ ¬puzzle_solved]→ EXPIRED_C₁_C₂_DEAD
State: CLAIMED_C₂ —[T₀+4d ≤ now < T₀+5d]→ OPEN_C₂
State: CLAIMED_C₂ —[now ≥ T₀+5d]→ EXPIRED_C₂
```

This expresses the full semantics. UPPAAL could verify liveness (if the puzzle is solved in time, C₂ eventually becomes available) and safety (C₂ is never accessible to a non-winner).

**Applied pi calculus / spi calculus:** ProVerif (Blanchet) [RES-004-020] uses the applied pi calculus to model and verify cryptographic protocols. The spi calculus (Abadi-Gordon 1999) was specifically designed for security protocols with cryptographic operations. A capsule chain could be modelled in the applied pi calculus: each capsule is a process; channels between processes correspond to the key-hierarchy links; time conditions would be modelled via a separate time process that evolves clock values.

Limitation: Standard ProVerif does not model time natively. Timed extensions exist (Chrétien, Cortier, Pelletier) but are less mature than the time-free version.

**Smart contract formalisms:** Ethereum-style smart contracts have formalisms specifically designed to express conditional state transitions over time: Solidity's temporal logic, the Event-Driven Petri Net formalism [RES-004-021], and formalisms for automated verification of EVM bytecode. The capsule DAG is structurally similar to a state machine contract with time-based guards.

**Proposed Heirlooms capsule DAG notation (refinement of CTO's proposal):**

Building on the CTO's informal notation, a lightweight formal notation can be defined as:

```
Capsule := Cap(recipients, window, content, [next])

window   := (T_unlock : Timestamp, T_expire : Timestamp)

content  := Encrypted(plaintext, key)
             where key = tlock(T_unlock) ⊕ shamir(custodians, M, N)

next     := Cond(
              test   : PuzzleVerify(puzzle, answer),
              winner : ExclusiveClaim(recipients, server),
              then   : Cap(winner, window₂, content₂, [next₂]),
              dead   : Destroy(content₂)
            )
```

This captures: recipients, time window, encrypted content, and a conditional link to the next capsule. The Cond() structure expresses the first-solver-wins semantics and the expiry-as-death cascade. The notation is not formally defined in any published paper found during this review.

**Assessment:** No published academic notation specifically addresses time-conditioned dependency graphs over encrypted documents. The CTO's informal notation can be formalised using timed automata for verification purposes and the applied pi calculus for protocol analysis. For internal Heirlooms documentation, a lightweight domain-specific notation (as proposed above) is sufficient and clearer than either timed automata or pi calculus for a multi-discipline team.

---

### Consent Capsule Prior Work

The Care Mode consent capsule — a sealed, cryptographically timestamped record of a person's consent to monitoring by a POA holder, revocable while the person retains capacity — is a specific application of the broader SSI and verifiable credential literature.

**W3C Verifiable Credentials (VC) Data Model v2.0** [RES-004-022]: A W3C standard (2024) for issuing cryptographically verifiable claims. A VC is: a JSON-LD document signed by an issuer, binding claims to a subject (identified by a DID), with optional expiry and revocation mechanisms. VCs are already used in healthcare consent management: a patient issues a consent VC to a care provider, specifying the scope and duration of consent; the patient can revoke the VC at any time using a revocation registry.

**Self-sovereign identity in healthcare** (npj Digital Medicine, 2025) [RES-004-023]: Proposes an SSI framework for health data sharing where patients hold their own consent credentials in a digital wallet, grant access by presenting VCs, and revoke access by cancelling the VC in a revocation registry (DID-based, on-chain or in a trusted registry). This directly maps to the consent capsule use case.

**UK Office of the Public Guardian — Digital LPA** [RES-004-024]: The UK government's modernisation of the Lasting Power of Attorney process is exploring Verifiable Credentials using the W3C data model and BBS+ signatures (zero-knowledge selective disclosure). The OPG has published technical architecture decisions including VC issuance, attorney credential verification, and revocation. This is the closest existing production analogue to Heirlooms' consent capsule: a VC-based representation of a person's delegation of authority to a POA holder, revocable by the person.

**BBS+ signatures for consent VCs:** BBS+ is a pairing-based signature scheme that supports selective disclosure and zero-knowledge proofs [RES-004-025]. A consent capsule VC signed with BBS+ allows the POA holder to prove to a care provider that consent was granted, revealing only the relevant attributes (e.g., "monitoring consent granted from Date X") without revealing other personal data. Revocation is handled by a W3C Status List 2021 or similar on-chain registry.

**Ethical AI and smart contract consent governance** (PMC 2025) [RES-004-026]: Proposes combining ZK proofs and smart contracts for transparent, tamper-evident consent records in healthcare data governance. Smart contracts enforce consent conditions automatically; ZK proofs allow verifying consent properties without revealing identity. This is a more complex version of the consent capsule, potentially useful for the Care Mode's high-stakes context.

**Consent capsule design for Heirlooms (recommended):**

The consent capsule is not a cryptographic capsule in the same sense as C₁/C₂ (it does not encrypt hidden content that is time-locked). It is a cryptographic record with two properties:

1. **Immutable timestamp:** The consent record is signed by the person (using their Heirlooms signing key) and its timestamp is committed to a verifiable log (e.g., RFC 3161 trusted timestamp authority, or a public ledger). This proves when consent was granted.

2. **Revocable:** The person can revoke the consent at any time. Revocation is implemented as: (a) a signed revocation credential in a registry (W3C Status List); (b) the POA holder's monitoring access tokens expire when the revocation is detected.

3. **Capacity attestation:** While the person retains capacity, they can re-issue or revoke the consent. If they lose capacity, the consent becomes permanent until the POA holder explicitly terminates it. This is a legal / operational constraint, not a cryptographic one.

The simplest viable implementation: the consent capsule is a signed JSON document containing {granting_person_DID, poa_holder_DID, scope, grant_time, revocation_registry_pointer}, stored in Heirlooms' database with the person's digital signature and a trusted timestamp. Revocation updates the status in the revocation registry. This requires no new cryptographic machinery beyond what Heirlooms already uses (signing keys) plus a revocation registry.

**Assessment:** Prior work on the consent capsule is well-developed in the SSI / VC literature and in the UK digital LPA programme. The consent capsule is a straightforward application of W3C VCs with digital signatures and a revocation registry. It is the simplest component of the proposed chained capsule system to implement, and does not require the complex key hierarchies or custodian coordination of the window capsule or chained capsule.

---

### Smart Contract Analogues

Smart contracts are the most obvious analogue to the chained capsule's conditional release logic. This section assesses what the smart contract literature offers and where the on-chain model diverges from Heirlooms' off-chain model.

**HTLCs (Hash Time-Lock Contracts):** The foundational on-chain analogue [RES-004-012]. An HTLC locks funds under a hash (H = Hash(preimage)) and releases them to whoever provides the preimage before a deadline. After the deadline, the sender reclaims. This maps directly to: C₁'s puzzle answer is the preimage; C₂'s key material is released when the preimage is submitted before C₁'s expire_time. HTLCs enforce this trustlessly on-chain. Heirlooms' off-chain model replaces "on-chain trustless enforcement" with "server-mediated atomic claim" — same semantics, different trust model.

**Lit Protocol** [RES-004-027]: A production-deployed threshold decryption network that conditions key release on arbitrary on-chain and off-chain conditions. Lit nodes hold shares of a BLS key; threshold shares are released to a requester only when they satisfy Access Control Conditions (smart contract state checks, NFT ownership, time windows, etc.). In 2024, Lit fulfilled over 24 million cryptographic requests. Lit is the closest production analogue to what Heirlooms' custodian tier needs to do: conditional key release based on time windows and verifiable conditions. The Lit Protocol is relevant as a vendor option for Heirlooms' custodian nodes — rather than running its own custodian infrastructure, Heirlooms could delegate to Lit nodes with appropriately configured Access Control Conditions.

**NuCypher / Umbral** (threshold proxy re-encryption) [RES-004-017]: Allows condition-based and time-based re-encryption delegation. The miner (re-encryption node) holds a re-encryption key for a specific condition; if the requester satisfies the condition, the miner re-encrypts the ciphertext for the requester. Temporal delegation was introduced by Ateniese et al. (2012) in this context. NuCypher is now part of Threshold Network. Unlike Lit (which does threshold decryption), NuCypher uses re-encryption — the data remains encrypted at rest; the access changes. Relevant to Heirlooms as an alternative design: instead of custodians holding Shamir shares, a re-encryption proxy network re-encrypts the capsule DEK for the winner upon condition verification.

**Zama fhEVM** [RES-004-028]: Fully homomorphic encryption for smart contracts. The fhEVM coprocessor allows deploying confidential smart contracts on any EVM chain. The contract can hold encrypted state (e.g., encrypted puzzle answers, encrypted key material) and evaluate conditions over this encrypted state without revealing it. The threshold MPC network holds decryption key shares and releases decryption only when the smart contract authorises it. This is the most technically advanced model: the conditional logic is executed on encrypted data, providing information-theoretic privacy for the conditions themselves. Current performance: ~20 TPS with significant latency. Not suitable for consumer-product latency requirements in 2026, but a compelling long-horizon architecture.

**Cassiopeia** (FC 2023, on-chain WE) [RES-004-007]: A cryptoeconomic smart contract that implements WE through a committee of nodes. Nodes deposit stake; those who correctly reveal their decryption shares when a witness is publicly verified on-chain are rewarded; those who misbehave are slashed. This is a game-theoretic enforcement of the WE primitive — not information-theoretically secure but practical under honest-majority assumptions. Directly relevant to Heirlooms: a Cassiopeia-style committee could serve as C₂'s custodians, releasing shares only when the puzzle solution is verified on-chain.

**Security properties from the on-chain literature relevant to Heirlooms' off-chain model:**

1. **Frontrunning:** In competitive scenarios (first solver wins), blockchain frontrunning (a validator observing a reveal and submitting their own) is a known attack. FairBlock [RES-004-014] addresses this with threshold IBE delay. For Heirlooms' off-chain model, frontrunning is a server-level race condition (two simultaneous submissions); standard database-level atomicity handles it.

2. **Miner extractable value (MEV):** On-chain competitive puzzles are vulnerable to MEV — validators can reorder transactions to extract value. Off-chain, this is not applicable, but a malicious Heirlooms server playing the role of the first-solver-wins coordinator has an analogous power: it could falsely award the prize. This is a trust assumption in the off-chain model.

3. **Oracle problem:** Smart contracts cannot natively access off-chain state. The Heirlooms chained capsule has an oracle problem too: the server (or custodians) must determine whether C₁'s puzzle was solved within its window. For an on-chain implementation, Chainlink oracles or Chainlink Confidential Compute [RES-004-029] could attest to the puzzle solution. For the off-chain model, the Heirlooms server is the oracle.

4. **Conditional payments with oracle-based signatures** (Madathil et al., 2022) [RES-004-013]: A threshold oracle attests to a real-world event; a payment is released only upon threshold attestation. This maps to: threshold custodians attest that C₁'s puzzle was solved within the window; C₂'s key is released upon attestation. This paper's verifiable witness encryption based on threshold signatures (VweTS) is a formal cryptographic primitive that could underpin a more rigorous version of the chained capsule's conditional release.

**Assessment:** The smart contract literature provides rich analogues for the chained capsule's conditional release logic. The most relevant insights are: (1) HTLC structure maps directly to the puzzle-preimage → key-release model; (2) Lit Protocol is a production-ready threshold decryption network that could serve as a custodian-tier vendor; (3) Zama fhEVM is the long-horizon architecture for truly private conditional execution; (4) frontrunning / MEV attacks have off-chain analogues that Heirlooms must address in the server-mediated atomic claim design.

---

## Novelty Assessment

| Component | Heirlooms design | Prior art status | Novelty |
|---|---|---|---|
| Window capsule (tlock + Shamir deletion) | C₁ and C₂ individually | RES-002: novel as complete system | Established by RES-002 |
| DAG chaining via key hierarchy (K_b,2 inside C₁) | Embedding next capsule's key material inside current capsule plaintext | Structured encryption, crypto-pointers (scattered, different applications) | **Novel in this application** |
| Expiry-as-death cascade via key hierarchy | C₂ inaccessible if C₁ expires without solve | Not found in literature | **Novel** |
| First-solver-wins atomic claim | Server-mediated lock on C₂ upon first valid puzzle solve | HTLC (on-chain analogue), auction timed commitment competition | **Novel in off-chain consumer context** |
| Capsule reference token (QR code → L₂) | UX mechanism for inter-capsule navigation | No direct prior art | **Novel** |
| VTLP-NP for puzzle verification | Proving puzzle is "worth solving" without revealing answer | Xin-Papadopoulos 2025 [RES-004-008] | **Applies existing (2025) primitive directly** |
| Consent capsule (VC + timestamp + revocation) | W3C VC with BBS+, revocation registry | W3C VC standard, UK OPG digital LPA [RES-004-024] | **Well-established; straightforward application** |
| DAG formal notation | Informal notation proposed by CTO | No academic notation for time-conditioned encrypted DAGs | **Novel (informal notation; worthy of formalisation)** |
| Smart contract analogue (off-chain) | Server as conditional release coordinator | HTLCs, Lit Protocol, Cassiopeia | **Novel combination off-chain; analogues exist on-chain** |
| WE-based trustless version (horizon) | Encrypt C₂'s key to "puzzle solved AND time in window" | Garg et al. CRYPTO 2025 WE framework | **Not yet deployable; future direction** |

**Overall novelty assessment:** The chained capsule construction is **highly novel as a complete consumer-facing application**. The individual cryptographic components (window capsules, key hierarchies, timed commitments) are prior art. The combination — time-windowed competitive delivery, expiry-as-death cascade, capsule reference tokens in a family archive context — is not found in any published paper or patent reviewed.

Academic novelty is moderate: the construction can be expressed as a composition of known primitives. Product novelty is high: no competing product offers this capability. The consent capsule component has low novelty (direct application of W3C VC standard) but high product novelty (not implemented in any consumer family archive product found in the literature).

---

## Recommended Construction (If Feasible)

The chained capsule is feasible using the following design:

### Capsule Chain Structure

**C₁ (competitive delivery capsule):**
- Recipients: {A, B} (competitive set)
- Window: [T₀+2d, T₀+3d]
- Plaintext contains:
  - Puzzle (puzzle text / QR challenge)
  - Capsule reference token: {capsule_id: C₂_UUID, link_key: L₂}
  - L₂ = HKDF(C₂_master_seed, "link_key", 32 bytes) [or directly K_b,2]
  - Content prize for C₁ itself (if any)
- Sealed using RES-002 construction (tlock lower + Shamir-custodian upper)
- Puzzle format: a VTLP for NP [RES-004-008] — the puzzle answer is a preimage; a ZK proof accompanies the puzzle proving the preimage is "valid" (unlocks C₂) without revealing it. Recipients verify the proof before investing effort in solving.

**C₂ (winner-only capsule):**
- Recipient: {winner of C₁} (determined at claim time)
- Window: [T₀+4d, T₀+5d]
- Content: prize
- Sealed using RES-002 construction
- K_b,2 is derived from L₂ extracted from C₁'s plaintext
  OR L₂ is directly the material needed to access C₂

**Server-mediated atomic claim (first-solver-wins):**
1. Solver submits {capsule_id: C₁, answer: preimage, proof: VTLP-NP proof}.
2. Server verifies: (a) current time in [T₀+2d, T₀+3d]; (b) Hash(preimage) = puzzle_hash; (c) VTLP proof valid.
3. Server atomically writes claim record: {C₁_puzzle_id: claimed_by: solver_id, timestamp}. Uses database-level SELECT FOR UPDATE or equivalent. First write wins.
4. Server issues claim token: signed assertion {C₁_puzzle_id, winner: solver_id, timestamp}. Only one claim token is ever issued per puzzle.
5. C₂'s custodians accept the claim token as proof of C₁ victory and release their Shamir shares of K_b,2 to solver during C₂'s window [T₀+4d, T₀+5d].

**Expiry-as-death cascade:**
- If T₀+3d passes without a claim: C₁'s custodians delete their shares (standard RES-002 expiry). L₂ was inside C₁'s plaintext — never decrypted — therefore inaccessible. C₂'s custodians, observing that no claim token exists and C₁ has expired, delete their shares of K_b,2 at T₀+3d. C₂ is permanently inaccessible.

### Consent Capsule

Implemented as a W3C Verifiable Credential:
- Issuer: the person (signed with their Heirlooms signing key)
- Subject: the POA holder (identified by DID or Heirlooms user ID)
- Claims: {scope: "care_monitoring", granted_at: timestamp, revocation_registry: URL}
- Timestamp: RFC 3161 trusted timestamp applied at creation
- Revocation: W3C Status List 2021 entry; person can update their status entry to "revoked" at any time
- Storage: stored in Heirlooms database alongside the Care Mode configuration; not an encrypted capsule in the window-capsule sense

### Algorithm IDs to Reserve

| ID | Use |
|---|---|
| `capsule-chain-ref-v1` | Capsule reference token embedded in plaintext: {capsule_id, link_key, puzzle_hash} |
| `vtlp-groth16-v1` | Verifiable Time-Lock Puzzle proof (ZK proof of puzzle answer validity) |
| `consent-vc-v1` | Consent capsule verifiable credential format |

These are in addition to the algorithm IDs reserved by RES-002 (`window-aes256gcm-v1`, `tlock-window-meta-v1`).

---

## Patentability Note (Flag for Legal)

**Elements warranting patent assessment:**

1. **Chained window capsule construction with expiry-as-death cascade:** Embedding the next capsule's key material inside the current capsule's plaintext, such that expiry of the current capsule automatically destroys access to the next capsule. This specific construction is not found in any patent or paper reviewed. It is a novel combination of the RES-002 window capsule (itself potentially patentable per RES-002's patentability assessment) and a key-hierarchy chaining mechanism.

2. **Competitive delivery with first-solver-wins atomic claim:** A server-mediated atomic locking mechanism enforcing exclusive access to C₂ for the first of N recipients to solve C₁'s puzzle, within a time-windowed capsule framework. Not found in prior art for off-chain consumer applications. The HTLC analogue exists on-chain (prior art); the off-chain consumer adaptation may be patentable, though the claim would need to be narrow.

3. **Capsule reference token (encrypted inter-capsule pointer):** The specific mechanism of embedding an encrypted pointer from one time-windowed capsule to another, such that the pointer is only accessible after the referencing capsule is legitimately opened. Crypto-pointers exist in the patent literature for memory safety (US 7,185,205; US 8,145,900) but not in the context of time-windowed encrypted documents.

4. **VTLP-NP applied to capsule puzzle verification:** Using a verifiable time-lock puzzle for NP to prove to prospective solvers that a puzzle's solution is "valid" (unlocks the next capsule) without revealing the solution. The VTLP-NP primitive is published (Xin 2025 — academic prior art). The specific application to capsule puzzle verification in a family archive context may be patentable as a system claim.

**Prior art concerns:**
- HTLC patents: several HTLC-related patents exist in the payment space. The capsule reference token and competitive delivery claims should be drafted to distinguish from payment-specific HTLC claims.
- Structured encryption: the field is primarily academic (Cash, Tessaro et al.) with limited patent activity. Not a blocking concern.
- W3C VC consent capsule: no novel cryptography; the consent capsule alone is unlikely to be patentable.

**Recommendation:** Engage a patent attorney experienced in cryptographic systems patents before any public announcement of the chained capsule feature. The strongest claim is the combination of RES-002 + chaining via key hierarchy + expiry-as-death cascade. This should be assessed alongside the RES-002 patentability recommendation to determine whether a single patent application covering both constructions is more efficient than separate applications.

---

## PA Summary

**For:** PA (to route to CTO, Technical Architect, and Legal)  
**Urgency:** Medium — no blocking decisions required for current milestone; decisions needed before chained capsule enters implementation planning

**Key findings:**

- The chained capsule construction is **novel as a complete consumer application**. It can be built entirely from existing cryptographic primitives, composing the RES-002 window capsule with a key-hierarchy chaining mechanism and a server-mediated atomic claim for first-solver-wins exclusivity. No new mathematical primitive is required.

- **First-solver-wins** is the only component that does not have a direct classical cryptographic primitive. It requires server-mediated atomic claim (database-level locking) for off-chain deployment, or an HTLC-style on-chain contract for a trustless version. The server-mediated approach is recommended for v1.

- **Expiry-as-death** (C₂ permanently destroyed if C₁ expires unsolved) is achievable at the same trust level as the window capsule's expiry guarantee: threshold-honest custodians, not trustless. The strongest implementation embeds C₂'s key material inside C₁'s plaintext — C₂'s inaccessibility is then automatic if C₁ expires without a solve.

- **The consent capsule** for Care Mode is a straightforward application of W3C Verifiable Credentials, with mature prior work in the self-sovereign identity literature and the UK government's digital Lasting Power of Attorney programme. No novel cryptography is required.

- **Three horizon technologies** could upgrade the chained capsule to a trustless model if they mature: (1) practical WE from linearly verifiable SNARKs (CRYPTO 2025 — 5–7 year horizon for consumer deployment); (2) Verifiable Time-Lock Puzzles for NP (2025 — applicable now for puzzle verification); (3) Zama fhEVM for private conditional execution (deployed but too slow for consumer latency in 2026).

**Decisions needed from CTO:**

1. Should the first-solver-wins mechanism be server-mediated (lower complexity, Heirlooms server as coordinator) or on-chain (trustless, higher complexity and cost)? Recommendation: server-mediated for v1.
2. Should Heirlooms build and operate its own custodian tier for chained capsules, or evaluate Lit Protocol as a vendor for the conditional decryption network? Lit Protocol is production-deployed and could accelerate delivery of the custodian tier.
3. Should the chained capsule and window capsule patent assessment be consolidated into a single patent application? This should be decided before any public feature announcement.
4. Should VTLPs (Xin, 2025) be adopted for the puzzle format inside C₁? Recommendation: yes — this gives prospective solvers verifiable assurance that solving the puzzle is "worth it," improving UX and reducing abandonment.

**Follow-on tasks created:**

- None automatically queued. Recommend CTO consider:
  - ARCH task: Chained capsule wire format and custodian API extension (depends on CTO decision on items 1 and 2 above)
  - RES-005 (potential): Lit Protocol technical assessment — architecture, trust model, SLA, pricing, and fit with Heirlooms envelope format (if CTO selects Lit as custodian vendor option)
  - Legal engagement: Patent assessment for RES-002 + RES-004 combined construction (if CTO confirms patenting intent)

---

## Research Sources

Full numbered references in `docs/research/REFERENCES.md` under section RES-004.

Key sources: Boneh-Sahai-Waters functional encryption [RES-004-001]; Bethencourt-Waters CP-ABE [RES-004-002]; Garg et al. CRYPTO 2025 WE framework [RES-004-006]; Cassiopeia on-chain WE [RES-004-007]; Xin-Papadopoulos VTLP-NP [RES-004-008]; HTLC Lightning Network [RES-004-012]; Madathil et al. oracle-based conditional payments [RES-004-013]; FairBlock anti-frontrunning [RES-004-014]; crypto-pointers patent [RES-004-015]; NuCypher Umbral PRE [RES-004-017]; timed automata security survey [RES-004-018]; W3C VC data model [RES-004-022]; UK OPG digital LPA [RES-004-024]; Lit Protocol [RES-004-027]; Zama fhEVM [RES-004-028].
