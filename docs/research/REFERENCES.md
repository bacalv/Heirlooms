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
