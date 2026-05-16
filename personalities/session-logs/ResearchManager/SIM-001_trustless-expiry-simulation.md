# SIM-001 — Trustless Expiry Impossibility: Simulation Note

**Date:** 2026-05-16  
**Author:** Research Manager  
**Status:** Throw-away simulation — does NOT feed task queue, ARCH decisions, or RES-002  
**Prompt:** Is the CTO's intuition correct that fully trustless expiry is impossible? Where exactly does the impossibility lie?

---

## The Core Impossibility

The CTO's intuition is correct, and the impossibility is information-theoretic rather than merely computational.

The fundamental problem: classical cryptography operates on deterministic transformations of bit strings. Once a bit string has been observed, it can be copied and stored with perfect fidelity. There is no operation on classical information that causes previously-learned information to become unknown. The "un-publishing" problem is not an engineering challenge — it is a consequence of classical information theory. Shannon entropy is non-negative and non-decreasing in an observer's possession: you cannot un-learn something.

Formalised: suppose a classical scheme S achieves expiry at time T_e — meaning that after T_e, no party can compute the plaintext M from the ciphertext C and public parameters. For this to hold, S must have caused all parties who could compute M to forget the key material K. But "forgetting" in the classical model means deleting a bit string. Deletion is a voluntary act — it cannot be cryptographically compelled. Therefore, for any classical scheme S claiming trustless expiry: either (a) some party can refuse to delete and retain the ability to compute M, violating the expiry guarantee; or (b) the scheme relies on an external enforcement mechanism (a trusted party, a threshold of honest parties, hardware, or law). There is no option (c) — no classical primitive forces information destruction.

This is not a proof in the formal sense (it would require formalising "forced deletion" as a game), but the argument is tight enough that the conclusion is clear: **classical trustless expiry is provably impossible under any model where an adversary controls their own memory.**

---

## Six Directions

### 1. Classical Impossibility — Where Exactly It Breaks

The "weakest" possible classical trust assumption is a threshold: M-of-N parties must cooperate to reconstruct the secret, and the expiry guarantee holds as long as at least (N − M + 1) parties honestly delete their shares. This is precisely the Heirlooms window capsule construction.

Can we go weaker? The next weaker assumption would be: a single party holds key material and we trust them to delete it. This is just a custodian. Going further — zero trusted parties — collapses: if no party holds any secret material, then the decryption capability must be derivable from public information alone, which means it is derivable forever.

The impossibility therefore traces to: **at least one entity must hold secret material, and that entity must voluntarily destroy it.** The threshold structure (Shamir secret sharing) makes this a distributed trust assumption, reducing the blast radius, but does not eliminate the assumption. This is the theoretical floor for classical expiry.

---

### 2. Quantum Escape Hatches — Real or Science Fiction?

The no-cloning theorem (Wootters-Zurek, 1982) is a genuine physical law: an unknown quantum state cannot be copied. This is fundamentally different from classical information, which can be copied arbitrarily.

**What this gives for expiry:**

If K_window is encoded as a quantum state |ψ⟩ and transmitted to the receiver, the receiver cannot both (a) measure |ψ⟩ to obtain a classical key and (b) retain a copy of |ψ⟩. The act of measurement collapses the quantum state irreversibly. This is the basis of quantum certified deletion (Bartusek-Raizes 2024; Katz-Sela 2024): a party who produces a valid deletion certificate for a quantum share cannot have retained the share, because producing the certificate requires a measurement that destroys the state.

**The catch for Heirlooms' expiry problem:**

The quantum escape hatch addresses verifiable deletion of shares — but it does not prevent a receiver who legitimately unlocked the capsule from retaining the plaintext. If Alice unlocks the capsule during [T_unlock, T_expire] and decrypts the DEK, she now holds a classical bit string (the DEK and the plaintext). No quantum mechanism can compel her to forget classical information she has already observed.

So quantum certified deletion solves the custodian deletion problem (proving shares were destroyed), not the receiver retention problem. The remaining impossibility — that a receiver who legitimately accessed the content cannot be cryptographically forced to forget it — is preserved even with quantum infrastructure. This is a fundamental limitation that is identical to the classical case for the receiver.

**Practical assessment:** QKD and quantum memory are not consumer-grade and will not be so within the next decade. Quantum certified deletion is theoretically correct and should be tracked as a horizon technology for custodian-tier deletion proofs. It does not change the product's fundamental expiry model.

---

### 3. Trusted Hardware — Weaker Than a Custodian?

An HSM or SGX enclave programmed to delete key material at T_expire, and capable of cryptographically attesting to having done so, is a weaker trust assumption than a human custodian in some threat models and a stronger assumption in others.

**Where hardware is weaker (better):**
- Hardware cannot be socially engineered. An SGX enclave does not respond to a phone call from a lawyer saying "keep that share for another year."
- Hardware deletion is automated — no forgotten cron job, no employee who got distracted.
- Attestation provides a verifiable record: the enclave ran code X and at time T deleted the material, signed by Intel's attestation key.

**Where hardware is stronger (worse):**
- Hardware requires trusting the manufacturer (Intel, AWS). A nation-state that can compromise Intel's PKI or coerce AWS can invalidate all attestations.
- Physical attacks — power analysis, fault injection, chip decapping — have bypassed SGX isolation multiple times (Foreshadow, SGAxe, Plundervolt). A well-resourced adversary with physical access can extract sealed key material.
- Firmware compromise is a persistent threat; a malicious update can change enclave behaviour without affecting attestation if the attestation measurement is not independently verified.

**The weakest-trust-assumption hardware scenario:** A globally distributed network of HSMs from different manufacturers (Thales, Yubico, AWS CloudHSM), each programmed identically to delete at T_expire, where a threshold of M-of-N must be compromised for expiry to fail. This combines hardware enforcement with threshold distribution. Trust assumption: "M-of-N HSM manufacturers will not simultaneously be coerced or compromised at T_expire." This is plausibly a weaker assumption than "M-of-N human institutions will not collude" but it introduces supply-chain trust that is orthogonal to custodian trust.

**Verdict:** Trusted hardware is a meaningful defence-in-depth enhancement, not a fundamental improvement to the trust model. It shifts the threat surface from "social engineering of custodians" to "hardware supply chain compromise" — a different attack class, not a weaker one in the formal sense.

---

### 4. Computational Expiry — Practically Sufficient?

Even if information-theoretic expiry is impossible, we can define a weaker notion: **computational expiry** — after T_expire, the work required to recover the plaintext is computationally infeasible (e.g., 2^128 operations) even for an adversary who retains all public information and some subset of shares.

The Heirlooms window capsule already achieves this: once shares are deleted (even by a minority of honest custodians), reconstructing K_b requires solving a threshold Shamir problem with insufficient shares, which is information-theoretically impossible under Shamir's scheme — not just computationally hard, but literally impossible regardless of computation. This is stronger than computational hardness.

Where computational expiry matters is if the adversary *did* retain shares (a malicious custodian). In that case, the expiry guarantee degrades to: the adversary needs all M shares, and they may have k < M of them. There is no computational barrier to the ones they have — the protection is purely from not having enough shares.

A hypothetical computational expiry extension: combine Shamir shares with a time-lock puzzle (Rivest-Shamir-Wagner 1996). Each share s_i is itself locked inside a sequential computation puzzle that takes T_compute > (T_expire − T_now) seconds to solve. If the time to solve the puzzle exceeds the value of the content before the content becomes irrelevant, this is "good enough" expiry. The limitation: puzzle hardness is calibrated to current hardware and degrades as hardware improves; the adversary who acquires shares years in advance simply waits or uses faster hardware.

**Verdict:** Computational expiry is a viable engineering fallback for scenarios where perfect deletion cannot be guaranteed, but it is not trustless — it relies on the puzzle being hard enough on future hardware, which is a hardware speed assumption, not a cryptographic hardness assumption. Less elegant than threshold deletion; not recommended as a primary mechanism.

---

### 5. Memory-Hard Functions — Does Cost Asymmetry Help?

Memory-hard functions (Argon2, scrypt, balloon hashing) are expensive in terms of memory bandwidth as well as computation, making them resistant to GPU/ASIC acceleration. The interesting question: could a memory-hard time-lock be designed where the cost of reconstructing K_window after T_expire grows faster than the value of the content over time?

Concretely: wrap each Shamir share in an Argon2-style puzzle tuned so that on current hardware, solving the puzzle takes longer than the content's expected value lifetime. An adversary who retains shares would need to solve the puzzle before the content becomes worthless, and the memory-hard property prevents parallelisation.

**Problems with this approach:**

1. **Hardware trajectory:** Memory bandwidth is improving, particularly with HBM (High Bandwidth Memory) architectures. The hardness calibration becomes a moving target. What takes 10 years today may take 2 years in 2040.

2. **Value inversion:** For Heirlooms specifically, content may increase in value over time (sentimental, legal). The "content value decays faster than puzzle cost" assumption may not hold for wills, medical records, or family archives.

3. **Composition with threshold:** Adding a memory-hard layer on top of Shamir shares means an adversary with M shares must *also* solve M puzzles. This is meaningful defence-in-depth but does not change the fundamental trust model — an adversary with M shares and infinite time eventually wins.

**Theoretical maximum:** A construction where the memory-hard cost to bypass expiry grows as O(2^n) per year, faster than all conceivable hardware improvements, would approximate computational expiry indefinitely. No such construction exists — the hardness parameter n must be fixed at sealing time, and hardware improvements are unbounded over long time horizons.

**Verdict:** Memory-hard functions are a useful timing enhancement for short-horizon expiry (months to a few years) but not a solution to the fundamental expiry problem over long time horizons (decades — which is Heirlooms' actual operating range).

---

### 6. Physical Analogues — Do Any Have Rigorous Cryptographic Counterparts?

Physical world analogues are instructive for intuition:

**Safety deposit box key that dissolves:** The physical key is destroyed; the lock mechanism still exists and can be picked. Cryptographic analogue: the "key" (Shamir shares) is deleted, but the ciphertext remains and an adversary who retained the key before dissolution wins. The physical analogue is exact — dissolution proves the originating party's key is gone but says nothing about copies.

**Legal instrument that self-voids:** A contract that contains language like "this document becomes void and unenforceable 10 years after signing." This is pure legal enforcement — a trusted institution (court, government) enforces the void status. Cryptographic analogue: exactly a trusted custodian with legal obligations. No cryptographic enforcement occurs.

**One-time pad used and then burned:** A message encrypted with a OTP, where the pad is physically destroyed after use. The ciphertext is permanently unreadable — information-theoretically. But the message was already transmitted and the receiver has it. Cryptographic analogue: this is precisely what Heirlooms' lower bound (tlock) provides before T_unlock — the decryption key has not been released and cannot be computed. But after unlock, the receiver has the content. The burning-of-the-pad analogue maps to deleting custodian shares after T_expire, but does not prevent a receiver who unlocked within the window from retaining the content.

**The candle that burns out:** A candle that provides light only while burning — once extinguished, the light is gone. This is the closest physical analogue to a genuinely trustless mechanism, but no classical cryptographic primitive implements it. The quantum certified deletion papers (Bartusek-Raizes) are the closest digital analogue: once the quantum state is measured, the "light" (decryption capability) is consumed and cannot be reconstructed.

---

## Summary

**Is trustless expiry provably impossible?** Yes, for classical cryptography. The argument is information-theoretic: no classical operation can compel an adversary to forget a bit string they have retained. The proof is not yet formalised in the literature in the form of an explicit impossibility theorem (this could be a publishable result), but the informal argument is airtight.

**The weakest trust assumption for a meaningful expiry guarantee:** A threshold of M-of-N parties must honestly delete their secret shares. Below this — any construction that claims to provide expiry with fewer than one trusted party — is either (a) vacuously secure (the content is never accessible) or (b) insecure (expiry fails). The Heirlooms construction sits exactly at this minimum.

**Theoretical primitives that could change this:** Quantum certified deletion (Bartusek-Raizes 2024; Katz-Sela 2024) for the custodian tier. Witness encryption from SNARKs (Garg et al., CRYPTO 2025) for a potential trustless upper bound where the decryption witness is "the blockchain does not contain a post-T_expire block." Both are horizon technologies.

**Are quantum approaches real or science fiction at consumer scale?** Real physics, science fiction timelines for consumers. No-cloning is a physical law and quantum certified deletion is theoretically correct. Consumer-grade QKD networks are 10–20 years away from the scale needed to replace classical custodian deletion. Track the space; do not build on it.

**The CTO's intuition:** Fully correct. Trustless expiry with classical cryptography is impossible. The window capsule construction achieves the minimum possible trust level (threshold honest custodians) that produces a meaningful guarantee. There is no weaker trust assumption that works.
