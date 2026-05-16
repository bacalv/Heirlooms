# Heirlooms Research — Reference Log

**Maintained by:** Research Manager  
**Purpose:** Numbered log of all sources consulted during research tasks. Each task appends its own section. References are cited in briefs as `[RES-NNN-NNN]` (task ID + sequence number within that task), making them globally unique and git-merge-safe across parallel tasks.

---

## RES-001 — 2026-05-16 — Cryptographic threat horizon, initial 40,000ft survey

**[RES-001-001]** https://csrc.nist.gov/news/2024/postquantum-cryptography-fips-approved  
NIST announcement of FIPS 203, 204, 205 approval, August 2024.

**[RES-001-002]** https://www.nist.gov/news-events/news/2024/08/nist-releases-first-3-finalized-post-quantum-encryption-standards  
NIST press release: first three finalised post-quantum encryption standards.

**[RES-001-003]** https://csrc.nist.gov/pubs/fips/203/final  
FIPS 203 final publication — ML-KEM (Module-Lattice-Based Key-Encapsulation Mechanism Standard).

**[RES-001-004]** https://csrc.nist.gov/pubs/fips/204/final  
FIPS 204 final publication — ML-DSA (Module-Lattice-Based Digital Signature Standard).

**[RES-001-005]** https://thequantuminsider.com/2026/03/31/q-day-just-got-closer-three-papers-in-three-months-are-rewriting-the-quantum-threat-timeline/  
The Quantum Insider, March 2026: three papers compressing quantum threat timeline; resource estimates for breaking ECC revised downward.

**[RES-001-006]** https://www.coindesk.com/markets/2026/03/31/quantum-computers-could-break-crypto-wallet-encryption-with-just-10-000-qubits-researchers-say  
CoinDesk, March 2026: 10,000-qubit estimate for breaking ECC-256 on advanced architectures.

**[RES-001-007]** https://postquantum.com/security-pqc/algorithm-quantum-ecc/  
Post Quantum Inc: new algorithm shrinks quantum attack surface for ECC; ~1,193 logical qubit estimate for P-256.

**[RES-001-008]** https://thequantuminsider.com/2026/04/06/how-quantum-computing-affects-cryptography/  
The Quantum Insider, April 2026: overview of quantum computing impacts on cryptography.

**[RES-001-009]** https://eprint.iacr.org/2017/598.pdf  
IACR ePrint 2017/598: "Quantum Resource Estimates for Computing Elliptic Curve Discrete Logarithms" — baseline qubit estimates referenced in subsequent research.

**[RES-001-010]** https://www.federalreserve.gov/econres/feds/harvest-now-decrypt-later-examining-post-quantum-cryptography-and-the-data-privacy-risks-for-distributed-ledger-networks.htm  
Federal Reserve FEDS paper: HNDL — examining post-quantum cryptography and data privacy risks, 2025.

**[RES-001-011]** https://thequantuminsider.com/2026/05/01/harvest-now-decrypt-later-why-should-you-care/  
The Quantum Insider, May 2026: HNDL explainer with nation-state attribution context.

**[RES-001-012]** https://www.paloaltonetworks.com/cyberpedia/harvest-now-decrypt-later-hndl  
Palo Alto Networks Cyberpedia: HNDL overview, government agency guidance summary.

**[RES-001-013]** https://en.wikipedia.org/wiki/Harvest_now,_decrypt_later  
Wikipedia: Harvest now, decrypt later — summary of evidence and agency guidance.

**[RES-001-014]** https://datatracker.ietf.org/doc/draft-ietf-tls-ecdhe-mlkem/  
IETF datatracker: draft-ietf-tls-ecdhe-mlkem-04 — post-quantum hybrid ECDHE-MLKEM key agreement for TLS 1.3.

**[RES-001-015]** https://aws.amazon.com/blogs/security/ml-kem-post-quantum-tls-now-supported-in-aws-kms-acm-and-secrets-manager/  
AWS Security Blog: ML-KEM post-quantum TLS now supported in AWS KMS, ACM, and Secrets Manager.

**[RES-001-016]** https://openjdk.org/jeps/527  
OpenJDK JEP 527: Post-Quantum Hybrid Key Exchange for TLS 1.3.

**[RES-001-017]** https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html  
OWASP Password Storage Cheat Sheet, current edition: Argon2id parameter recommendations (m=19MiB minimum, m=46MiB recommended).

**[RES-001-018]** https://docs.drand.love/docs/cryptography/  
drand official documentation: cryptographic background, BLS12-381, quantum vulnerability acknowledgement ("5-year long-term security horizon").

**[RES-001-019]** https://eprint.iacr.org/2023/189.pdf  
IACR ePrint 2023/189: "tlock: practical timelock encryption from threshold BLS" — original tlock paper.

**[RES-001-020]** https://github.com/drand/tlock  
drand/tlock GitHub repository: implementation and documentation for the tlock Go library.

**[RES-001-021]** https://soatok.blog/2024/07/01/blowing-out-the-candles-on-the-birthday-bound/  
Soatok (Trail of Bits cryptographer), July 2024: AES-GCM birthday bound analysis; 2^32 practical safety limit for 96-bit random nonces.

**[RES-001-022]** https://neilmadden.blog/2024/05/23/galois-counter-mode-and-random-nonces/  
Neil Madden, May 2024: GCM and random nonces — collision probability analysis.

**[RES-001-023]** https://www.rfc-editor.org/rfc/rfc8452.html  
RFC 8452: AES-GCM-SIV — nonce misuse-resistant authenticated encryption (referenced for comparison).

**[RES-001-024]** https://arxiv.org/abs/2601.16589  
arXiv 2601.16589, January 2026: "Emerging Threats and Countermeasures in Neuromorphic Systems" — survey of neuromorphic security threats.

**[RES-001-025]** https://pmc.ncbi.nlm.nih.gov/articles/PMC11895033/  
PMC / ScienceDirect, 2025: "Cyberbiosecurity: Advancements in DNA-based information security" — DNA computing security threats overview.

**[RES-001-026]** https://blog.sui.io/post-quantum-computing-cryptography-security/  
Sui Foundation blog: post-quantum computing and ECC security — blockchain ecosystem PQC transition context.

**[RES-001-027]** https://cloudsecurityalliance.org/blog/2024/08/15/nist-fips-203-204-and-205-finalized-an-important-step-towards-a-quantum-safe-future  
Cloud Security Alliance, August 2024: FIPS 203/204/205 finalisation analysis.

**[RES-001-028]** https://arxiv.org/html/2512.13333v1  
arXiv 2512.13333, December 2025: "Quantum Disruption: An SoK of How Post-Quantum Attackers Reshape Blockchain Security and Performance" — BLS12-381 and blockchain PQC context.

---

## RES-002 — 2026-05-16 — Window capsule expiry, literature review and construction brief

**[RES-002-001]** https://dl.acm.org/doi/10.5555/888615  
Rivest, Shamir, Wagner (1996): "Time-lock puzzles and timed-release crypto" — original construction using sequential squaring modulo RSA modulus. Foundational prior art for lower-bound time-lock schemes.

**[RES-002-002]** https://eprint.iacr.org/2010/347  
Paterson, Quaglia (SCN 2010): "Time-Specific Encryption" — formalises TSE with a time interval [T1, T2] for decryption using a centralised Time Server broadcasting Time Instant Keys (TIKs). Closest conceptual prior art to the window capsule (addresses both lower and upper bound); differs in requiring an online server and using key distribution rather than deletion for the upper bound.

**[RES-002-003]** https://dl.acm.org/doi/10.1145/1330332.1330336  
Blake, Chan (2004 / ACM TISSEC 2008): "Provably secure timed-release public key encryption" — provable security for TRE with time-server model.

**[RES-002-004]** https://dl.acm.org/doi/10.1145/3548606.3560704  
i-TiRE (CCS 2022): "Incremental Timed-Release Encryption or How to use Timed-Release Encryption on Blockchains" — practical blockchain TRE with efficient ciphertext updates across time periods.

**[RES-002-005]** https://eprint.iacr.org/2023/189.pdf  
Burdges, Nicolas et al. (ePrint 2023/189): "tlock: practical timelock encryption from threshold BLS" — the tlock scheme Heirlooms plans for M11. Already cited as RES-001-019; duplicated here for RES-002 completeness.

**[RES-002-006]** https://eprint.iacr.org/2018/601  
Boneh, Bonneau, Bünz, Fisch (2018): "Verifiable Delay Functions" — formal definition and first constructions of VDFs as an alternative trustless time-lock primitive.

**[RES-002-007]** https://www.cs.umd.edu/~jkatz/papers/forward-enc-full.pdf  
Canetti, Halevi, Katz (2003): "A forward-secure public-key encryption scheme" — forward security via key evolution. Related background for the forward-secure deletion literature.

**[RES-002-008]** https://www.cs.umd.edu/~imiers/pdf/forwardsec.pdf  
Green, Miers (2015): "Forward Secure Asynchronous Messaging from Puncturable Encryption" — puncturable encryption and Bloom filter encryption for forward secrecy in messaging.

**[RES-002-009]** https://eprint.iacr.org/2014/364.pdf  
Xu, Zhang, Yang (2014): "Deleting Secret Data with Public Verifiability" — verifiable deletion of key material using commitment schemes. Directly relevant to custodian deletion certificate design.

**[RES-002-010]** https://arxiv.org/abs/2307.04316  
Zhang et al. (2023): "Accelerating Secure and Verifiable Data Deletion in Cloud Storage via SGX and Blockchain" — practical verifiable deletion using Intel SGX and blockchain audit trail.

**[RES-002-011]** https://www.researchgate.net/profile/Amir-Herzberg/publication/221355399_Proactive_Secret_Sharing_Or_How_to_Cope_With_Perpetual_Leakage/links/02e7e52e0ecf4dbae1000000/Proactive-Secret-Sharing-Or-How-to-Cope-With-Perpetual-Leakage.pdf  
Herzberg, Jarecki, Krawczyk, Yung (CRYPTO 1995): "Proactive Secret Sharing Or: How to Cope With Perpetual Leakage" — the original PSS paper introducing epoch-based share refresh and the mobile adversary model.

**[RES-002-012]** https://eprint.iacr.org/2013/258  
Garg, Gentry, Sahai, Waters (STOC 2013): "Witness Encryption and its Applications" — the foundational witness encryption paper. NP-based encryption where a witness for a statement enables decryption.

**[RES-002-013]** https://csrc.nist.gov/Presentations/2025/stppa7-witness-encryption  
Garg, Sanjam (NIST STPPA-7, January 2025): "Witness Encryption: From Theory to Practice" — presentation on bridging WE from academic theory to practical deployment. Indicates active research toward practical WE.

**[RES-002-014]** https://eprint.iacr.org/2025/1364  
Garg et al. (CRYPTO 2025): "A Framework for Witness Encryption from Linearly Verifiable SNARKs and Applications" — special-purpose WE from weaker assumptions, concretely efficient for targeted applications. Key 2025 advance in WE practicality.

**[RES-002-015]** https://eprint.iacr.org/2024/736  
Bartusek, Raizes (CRYPTO 2024): "Secret Sharing with Certified Deletion" — quantum secret sharing where classical secrets are split into quantum shares; deletion is cryptographically certified and binding. Provides security even against an adversary that eventually collects an authorized set of shares (after deletions).

**[RES-002-016]** https://eprint.iacr.org/2024/1596  
Katz, Sela (Eurocrypt 2025): "Secret Sharing with Publicly Verifiable Deletion" — extends certified deletion to the publicly verifiable setting: any third party can verify the deletion certificate, not just the dealer. Based on LWE and post-quantum one-way functions.

**[RES-002-017]** https://eprint.iacr.org/2023/1024  
Kavousi, Abadi, Jovanovic (ASIACRYPT 2024): "Timed Secret Sharing" — the closest academic prior work to the Heirlooms window capsule construction. Formally defines TSS with both lower and upper time bounds; proposes short-lived proofs and gradual release strategies for the upper bound. Does not use drand/tlock or XOR blinding.

**[RES-002-018]** https://eprint.iacr.org/2010/347  
Paterson, Quaglia (SCN 2010): "Time-Specific Encryption" — (same as RES-002-002; cited separately for clarity in the closest-prior-art section).

**[RES-002-019]** https://journalofcloudcomputing.springeropen.com/articles/10.1186/s13677-024-00676-y  
Li et al. (Journal of Cloud Computing, 2024): "Multiple time servers timed-release encryption based on Shamir secret sharing for EHR cloud system" — distributes TRE time server role across multiple servers using Shamir secret sharing to eliminate single point of failure. Related but addresses robustness of the lower bound, not the upper bound.

**[RES-002-020]** https://eprint.iacr.org/2021/800  
Döttling et al. (CCS 2022): "i-TiRE: Incremental Timed-Release Encryption" — (same paper as RES-002-004; cited separately).

**[RES-002-021]** https://patents.google.com/patent/US20060155652A1/en  
US Patent Application 20060155652 (filed 2004, abandoned): "Expiring Encryption" — DRM-focused expiry scheme using a tamper-resistant secure clock. Does not use secret sharing. Abandoned; not blocking prior art for Heirlooms.

**[RES-002-022]** https://patents.google.com/patent/WO2008127446A2/en  
PCT/WO2008127446A2 (2008): "Method and apparatus for time-lapse cryptography" — threshold secret sharing and ElGamal for lower-bound time-lock. Does not address upper bound or expiry. Not blocking prior art for the window capsule upper bound.

**[RES-002-023]** https://link.springer.com/article/10.1007/s10623-016-0324-2  
Timed-release computational secret sharing and threshold encryption (Designs, Codes and Cryptography, 2016) — TR-CSS scheme allowing threshold reconstruction only after a specified time. Related background; lower-bound only.

**[RES-002-024]** https://whitepaper.secubit.io/appendix/hsm-vs-aws-nitro-enclave.html  
Secubit whitepaper: "HSM vs AWS Nitro Enclave" — practical comparison of HSM and Nitro Enclave trust models, capabilities, and FIPS certification status. Used for hardware deletion mechanism assessment.

**[RES-002-025]** https://docs.aws.amazon.com/enclaves/latest/user/set-up-attestation.html  
AWS documentation: "Cryptographic attestation — AWS Nitro Enclaves" — official documentation for Nitro Enclave attestation documents and KMS integration.

---

## RES-003 — 2026-05-16 — PQC migration readiness brief

**[RES-003-001]** https://csrc.nist.gov/pubs/fips/203/final  
NIST FIPS 203 final publication (August 2024): ML-KEM (Module-Lattice-Based Key-Encapsulation Mechanism Standard). Final standard. Specifies ML-KEM-512, ML-KEM-768, ML-KEM-1024. ML-KEM-768 key sizes: ek = 1184 bytes, dk = 2400 bytes, ct = 1088 bytes, ss = 32 bytes.

**[RES-003-002]** https://developer.apple.com/documentation/cryptokit/mlkem768  
Apple Developer Documentation: `MLKEM768` in CryptoKit — key generation, encapsulation, decapsulation. Available iOS 26 / macOS Tahoe. Also `SecureEnclave.MLKEM768` for hardware-isolated execution.

**[RES-003-003]** https://developer.apple.com/videos/play/wwdc2025/314/  
Apple WWDC 2025 session 314: "Get ahead with quantum-secure cryptography." Covers CryptoKit ML-KEM API, X-Wing (`XWingMLKEM768X25519`), Secure Enclave support, formally verified ML-KEM implementation, iOS 26 TLS auto-negotiation of hybrid key exchange.

**[RES-003-004]** https://csrc.nist.gov/pubs/sp/800/227/final  
NIST SP 800-227: Recommendations for Key-Encapsulation Mechanisms (September 2025). Endorses hybrid deployment (X25519 + ML-KEM) during PQC transition; provides X-Wing as practical example. Recommends ephemeral key discard after single use. Final publication.

**[RES-003-005]** https://www.encryptionconsulting.com/microsoft-and-apple-advance-post-quantum-cryptography-support-in-upcoming-os-releases/  
Encryption Consulting, 2025: Apple iOS 26 and Microsoft Windows PQC support analysis. CryptoKit ML-KEM-768, X-Wing availability and deployment scope.

**[RES-003-006]** https://github.com/dchest/mlkem-wasm  
mlkem-wasm GitHub repository: ML-KEM-768 in WebAssembly. Single ES module, 17 KB gzipped, no external WASM files. WebCrypto-compatible API (matches WICG draft). Based on mlkem-native (memory-safe C). Stopgap for browser deployment before native WebCrypto support ships.

**[RES-003-007]** https://proton.me/blog/introducing-post-quantum-encryption  
Proton, May 2026: Post-quantum encryption rollout for all Proton Mail users (including free tier). Uses ML-KEM-768 + X25519 composite (OpenPGP v6 algorithm ID 35). Does not retroactively re-encrypt existing mailbox content; future re-encryption planned.

**[RES-003-008]** https://www.helpnetsecurity.com/2026/05/06/proton-mail-post-quantum-protection-feature/  
Help Net Security, May 2026: Technical details of Proton Mail PQC rollout. HNDL threat cited as primary motivation. Proton collaborating with Thunderbird for cross-provider PQC interoperability.

**[RES-003-009]** https://signal.org/blog/spqr/  
Signal Blog, October 2025: "Signal Protocol and Post-Quantum Ratchets." SPQR (Sparse Post-Quantum Ratchet) using ML-KEM-768 alongside Double Ratchet, forming Triple Ratchet. Gradual rollout, backward-compatible. Reference design for hybrid PQC protocol deployment without flag-day migration.

**[RES-003-010]** https://engineering.fb.com/2026/04/16/security/post-quantum-cryptography-migration-at-meta-framework-lessons-and-takeaways/  
Meta Engineering Blog, April 2026: PQC migration framework with five maturity levels (PQ-Unaware → PQ-Enabled). Six-step strategy: prioritise risks, inventory cryptographic assets, address dependencies, build PQC components, deploy, establish guardrails.

**[RES-003-011]** https://datatracker.ietf.org/doc/draft-connolly-cfrg-xwing-kem/  
IETF Datatracker: draft-connolly-cfrg-xwing-kem-10 (March 2026). X-Wing KEM specification — X25519 + ML-KEM-768. Informational I-D, expiry September 2026. Adopted by Apple in CryptoKit iOS 26.

**[RES-003-012]** https://datatracker.ietf.org/doc/html/draft-ietf-hpke-pq-02  
IETF draft-ietf-hpke-pq-02: Post-Quantum and Post-Quantum/Traditional Hybrid Algorithms for HPKE. Defines ML-KEM-based and hybrid KEM algorithms for the HPKE framework (RFC 9180).

**[RES-003-013]** https://wicg.github.io/webcrypto-modern-algos/  
WICG: Modern Algorithms in the Web Cryptography API. Draft specification adding ML-KEM, ML-DSA, SLH-DSA, and other modern algorithms to the WebCrypto standard. Not yet implemented in browsers as of May 2026.

**[RES-003-014]** https://csrc.nist.gov/pubs/sp/800/88/r2/final  
NIST SP 800-88 Rev. 2: Guidelines for Media Sanitization (September 2025, supersedes Rev. 1). Cryptographic erasure (key deletion) is an approved sanitisation technique. Relevant to Heirlooms' obligation to hard-delete old P-256 wrapped key blobs after Phase 2 re-wrap.

**[RES-003-015]** https://stateofsurveillance.org/news/harvest-now-decrypt-later-quantum-surveillance-threat-2026/  
State of Surveillance, 2026: HNDL threat update — encrypted traffic captured in 2026 may be decryptable as early as 2032. Nation-state active harvesting confirmed by US DHS, UK NCSC, ENISA, ACSC.

**[RES-003-016]** https://github.com/paulmillr/noble-post-quantum  
noble-post-quantum GitHub repository: Auditable, minimal pure-JavaScript post-quantum cryptography. ML-KEM-768 support (ml_kem768 export). Audited by Trail of Bits. Alternative to mlkem-wasm for web platform — no WASM dependency, slightly larger bundle.

**[RES-003-017]** https://www.bouncycastle.org/resources/new-releases-bouncy-castle-java-1-84-and-bouncy-castle-java-lts-2-73-11/  
BouncyCastle release notes: Java 1.84 and LTS 2.73.11. ML-KEM accessible via `javax.crypto.KEM` API on Java 17+. ML-KEM, ML-DSA, SLH-DSA all available. Available on Maven Central.

**[RES-003-018]** https://dchest.com/2025/08/09/mlkem-webcrypto/  
dchest blog, August 2025: Status of ML-KEM in WebCrypto API — WICG draft exists, browsers have not yet implemented it, mlkem-wasm as production-suitable stopgap.

**[RES-003-019]** https://hbr.org/sponsored/2026/01/why-your-post-quantum-cryptography-strategy-must-start-now  
Harvard Business Review, January 2026: Enterprise PQC migration urgency. NSA CNSA 2.0 deadlines: new national security systems quantum-safe by January 2027; full application migration by 2030; infrastructure by 2035. Enterprise positioning signal for Heirlooms.

---

## RES-004 — 2026-05-16 — Chained capsule cryptographic assessment

**[RES-004-001]** https://eprint.iacr.org/2010/543.pdf  
Boneh, Sahai, Waters (TCC 2011): "Functional Encryption: Definitions and Challenges" — foundational definition of functional encryption as the generalisation of ABE. Establishes the conceptual framework for conditional decryption based on key functions. Cited for the background on FE as the theoretical home of the chained capsule's conditional delivery concept.

**[RES-004-002]** https://www.cs.utexas.edu/~bwaters/publications/papers/cp-abe.pdf  
Bethencourt, Sahai, Waters (IEEE S&P 2007): "Ciphertext-Policy Attribute-Based Encryption" — the seminal CP-ABE paper. Access policy is in the ciphertext; decryption key is tied to user attributes. Establishes time-conditioned ABE as a known technique (time as an attribute). Cited for conditional/programmable encryption background.

**[RES-004-003]** https://eprint.iacr.org/2018/330.pdf  
Time-Based Direct Revocable Ciphertext-Policy ABE (2018): extends CP-ABE with temporal access windows and direct revocation via a short revocation list. Establishes that time-bounded ABE exists but relies on re-keying rather than deletion for expiry enforcement. Cited for the CP-ABE time-condition literature.

**[RES-004-004]** https://thequantuminsider.com/2024/04/23/guest-post-decrypting-the-future-programmable-cryptography-and-its-role-in-modern-tech/  
ARPA / The Quantum Insider, April 2024: "Programmable Cryptography and Its Role in Modern Tech" — overview of programmable cryptography as a second-generation framework encompassing ZK-SNARKs, FHE, and MPC. Establishes the framing used to contextualise chained capsule conditional logic vs. near-term deployability.

**[RES-004-005]** https://eprint.iacr.org/2013/258.pdf  
Garg, Gentry, Sahai, Waters (STOC 2013): "Witness Encryption and its Applications" — the foundational WE paper. NP-based encryption where a witness for the statement enables decryption. Already cited as RES-002-012; duplicated here for RES-004 completeness and the specific application to puzzle-conditioned decryption.

**[RES-004-006]** https://eprint.iacr.org/2025/1364  
Garg, Hajiabadi, Kolonelos, Kothapalli, Policharla (CRYPTO 2025): "A Framework for Witness Encryption from Linearly Verifiable SNARKs and Applications" — demonstrates that special-purpose WE for targeted NP relations can be built from weaker assumptions and can be concretely efficient. Key 2025 advance enabling a practical upgrade path for trustless chained capsule conditional delivery. Already cited as RES-002-014; duplicated here for RES-004 completeness.

**[RES-004-007]** https://eprint.iacr.org/2023/635  
Cassiopeia: Practical On-Chain Witness Encryption (FC 2023 WTSC Workshop): smart contract WE using a cryptoeconomic committee with stake-and-slash incentives. Nodes hold shares and reveal them when a witness is publicly verified on-chain. Directly relevant as a deployable approximation of WE for chained capsule conditional release.

**[RES-004-008]** https://eprint.iacr.org/2025/225  
Xin, Papadopoulos (IEEE 2025): "Check-Before-you-Solve: Verifiable Time-lock Puzzles" — introduces VTLP for NP: the puzzle generator publishes a succinct ZK proof that the solution satisfies a specified NP relation, motivating solvers without revealing the solution. Groth16 instantiation: 1.37s proving, constant proof size, 1ms verification. Directly applicable to the puzzle inside Heirlooms' chained capsule C₁.

**[RES-004-009]** https://discovery.ucl.ac.uk/id/eprint/10132608/1/C-TLP.pdf  
Chained Time-Lock Puzzles (C-TLP) / Multi-Instance Time-Lock Puzzles: allows puzzles to be solved sequentially, with each solution yielding the hardness parameter for the next. Relevant as the academic framework for sequential TRE composability; cited for the DAG composition section.

**[RES-004-010]** https://dl.acm.org/doi/10.5555/888615  
Rivest, Shamir, Wagner (1996): "Time-lock puzzles and timed-release crypto" — original timed commitment foundation. Already cited as RES-002-001; duplicated here for the timed commitment / first-solver-wins section context.

**[RES-004-011]** https://arxiv.org/abs/2410.10607  
Sealed-bid Auctions on Blockchain with Timed Commitment Outsourcing (arXiv October 2024): introduces a timed commitment competition mechanism where solvers race to decrypt commitments for payment. Directly relevant to the competitive delivery model — establishes "solver competition for timed commitment solutions" as a known design pattern.

**[RES-004-012]** https://bitcoinops.org/en/topics/htlc/  
Bitcoin Optech: Hash Time Locked Contracts (HTLCs) — documentation of the HTLC mechanism used in the Lightning Network and atomic swaps. Hash lock (preimage reveal) + time lock (deadline for refund). The on-chain analogue of Heirlooms' first-solver-wins atomic claim. Cited for the first-solver-wins and smart contract analogues sections.

**[RES-004-013]** https://eprint.iacr.org/2022/499  
Madathil et al. (eprint 2022/499): "Cryptographic Oracle-Based Conditional Payments" — introduces verifiable witness encryption based on threshold signatures (VweTS) for oracle-attested conditional payments. Relevant as a formal cryptographic template for the chained capsule's "threshold custodians attest condition is met → release key" structure.

**[RES-004-014]** https://eprint.iacr.org/2022/1066.pdf  
FairBlock (eprint 2022/1066): "Preventing Blockchain Front-running with Minimal Overheads" — uses threshold IBE to delay when transactions become decryptable, providing order fairness. Cited for the front-running attack relevance to competitive puzzle submission in the first-solver-wins section.

**[RES-004-015]** https://patents.google.com/patent/US8145900B2/en  
US Patent 8,145,900 (and related 7,185,205): "Crypto-pointers for secure data storage" — pairs a cryptographic key with each pointer in a data structure, encrypting the data at each pointer location. Prior art on the concept of encrypted inter-object references. Cited for the capsule reference token section.

**[RES-004-016]** https://link.springer.com/article/10.1007/s00521-019-04440-1  
Structured Encryption for Conceptual Graphs / knowledge graphs (various, 2013–2024): encrypting graph-structured data while preserving the ability to query relationships. Provides the academic context for encrypted inter-document pointers (capsule reference tokens) as a form of structured encryption. Cited for the capsule reference token section.

**[RES-004-017]** https://raw.githubusercontent.com/nucypher/umbral-doc/master/umbral-doc.pdf  
NuCypher Umbral Threshold Proxy Re-Encryption scheme documentation: Umbral is a split-key PRE scheme where re-encryption keys are fragmented across nodes. Time-based and condition-based re-encryption policies are supported. Cited for the conditional PRE and smart contract analogues sections.

**[RES-004-018]** https://dl.acm.org/doi/10.1145/3534967  
"Timed Automata as a Formalism for Expressing Security: A Survey on Theory and Practice" (ACM Computing Surveys, 2022): comprehensive survey of timed automata applications in security protocol modelling and verification. Cited for the formal notation for capsule DAGs section.

**[RES-004-019]** https://www.academia.edu/393600/Verifying_Security_Protocols_With_Timestamps_via_Translation_to_Timed_Automata  
"Verifying Security Protocols With Timestamps via Translation to Timed Automata" — formalises how protocols with timestamps can be expressed as timed automata and verified using UPPAAL. Directly relevant to formalising the chained capsule's time-conditioned unlock/expire transitions. Cited for the formal notation section.

**[RES-004-020]** https://bblanche.gitlabpages.inria.fr/publications/BlanchetFnTPS16.pdf  
Blanchet (2016): "Modeling and Verifying Security Protocols with the Applied Pi Calculus and ProVerif" — foundational survey on ProVerif and applied pi calculus for protocol verification. Cited for the formal notation section as the framework for modelling chained capsule processes.

**[RES-004-021]** https://csrc.nist.gov/pubs/sp/800/88/r2/final  
(Referenced for context on smart contract formalisms and Petri net-based workflow modelling. Cited for the formal notation section's mention of smart contract state machine analogues.)

**[RES-004-022]** https://www.w3.org/TR/vc-data-model-2.0/  
W3C Verifiable Credentials Data Model v2.0 (2024): the W3C standard for cryptographically verifiable claims. Defines issuer, subject, claim, and revocation semantics. Cited as the foundation for the Heirlooms consent capsule design.

**[RES-004-023]** https://www.nature.com/articles/s41746-025-01945-z  
npj Digital Medicine (2025): "Enabling secure and self-determined health data sharing and consent management" — proposes SSI-based healthcare consent framework using DIDs and VCs for patient consent management with revocation. Cited as prior work directly relevant to the consent capsule's self-sovereign revocability requirement.

**[RES-004-024]** https://docs.modernising.opg.service.justice.gov.uk/adr/articles/0002-verifiable-credentials/  
UK Office of the Public Guardian — Digital LPA / Verifiable Credentials architecture decision record: technical approach to issuing and verifying digital Lasting Power of Attorney credentials using W3C VCs. The closest existing production analogue to Heirlooms' consent capsule (VC-based representation of POA authority delegation with cryptographic verification). Cited for consent capsule prior work.

**[RES-004-025]** https://docs.modernising.opg.service.justice.gov.uk/research-development/articles/verifiable-credentials-bbs/  
UK OPG — BBS+ Signatures research: evaluation of BBS+ pairing-based signatures for the digital LPA VC, providing selective disclosure and zero-knowledge proofs without tracking. Cited for BBS+ relevance to the consent capsule's privacy properties.

**[RES-004-026]** https://pmc.ncbi.nlm.nih.gov/articles/PMC12650700/  
PMC (2025): "Ethical AI in Healthcare: Integrating Zero-Knowledge Proofs and Smart Contracts for Transparent Data Governance" — proposes ZK proofs and smart contracts for tamper-evident, privacy-preserving consent records in healthcare. Cited as a more complex academic analogue to the Heirlooms consent capsule.

**[RES-004-027]** https://developer.litprotocol.com/sdk/access-control/intro  
Lit Protocol developer documentation: Access Control Conditions and threshold decryption. Threshold BLS key shares released upon condition verification across decentralised nodes. 2024: 24M+ cryptographic requests fulfilled. Cited as the primary production-deployed analogue to the Heirlooms custodian tier for conditional key release.

**[RES-004-028]** https://www.zama.org/post/fhevm-coprocessor  
Zama: "Introducing the fhEVM Coprocessor" — FHE for smart contracts on any EVM chain. Threshold MPC network holds decryption key shares; decryption occurs only when the smart contract authorises it. Mainnet launched December 2025. Cited as the long-horizon architecture for truly private conditional execution in the smart contract analogues section.

**[RES-004-029]** https://blog.chain.link/chainlink-confidential-compute/  
Chainlink Confidential Compute: threshold-encrypted secrets management with TEE-based execution for private smart contracts. Relevant as the oracle layer for on-chain versions of the chained capsule's condition verification. Cited for the oracle problem discussion in the smart contract analogues section.
