# Glossary Audit — RES-005

**Date:** 2026-05-17  
**Auditor:** Research Manager  
**Scope:** Full pass of `docs/research/GLOSSARY.md` as at RES-004 state (last updated 2026-05-16)  
**Result:** 24 gaps identified and resolved; glossary now has 80+ entries across A–Z

---

## Method

Every entry description was read in full. Each term, acronym, or compound phrase used as though the reader already knows it was checked against the entry list. A gap was recorded when:
- the phrase had no standalone entry, AND
- no "see also" cross-reference pointed to an entry that defined it.

---

## Gaps Found and Resolutions Applied

| # | Term / phrase | Found in entry (example) | Gap type | Resolution |
|---|--------------|--------------------------|----------|------------|
| 1 | **PQC migration** | Attack window, DEK, DEK re-wrap, Key rotation, Migration phase, Re-wrap (6+ entries) | No standalone entry; compound used as a known concept | **New entry** under P — full definition linking the phases and the HNDL threat |
| 2 | **HNDL attack window** | Key rotation ("closes the HNDL attack window") | Compound of two existing terms; no combined entry or cross-reference | **Cross-reference stub** under H pointing to Attack window and HNDL, with inline clarification |
| 3 | **Care Mode** | Consent capsule, Verifiable credential, Self-sovereign identity — all list it in "See also" but no entry existed | Referenced as a "See also" target with no entry | **New entry** under C — product-level description of Care Mode and its cryptographic anchoring |
| 4 | **Double Ratchet** | SPQR ("SPQR runs alongside the existing Double Ratchet protocol") | Used by name with no entry | **New entry** under D — Signal Double Ratchet protocol description |
| 5 | **Triple Ratchet** | SPQR ("what Signal calls the Triple Ratchet") | Used by name with no entry | **New entry** under T — compound of Double Ratchet + SPQR |
| 6 | **TEE** (Trusted Execution Environment) | Hardware Security Module (HSM) ("cloud-hosted TEE"), Lit Protocol ("Trusted Execution Environments (TEEs)") | Acronym used without a reachable definition | **Cross-reference stub + inline expansion** under T, pointing to Intel SGX and Nitro Enclave |
| 7 | **QKD** (Quantum Key Distribution) | Post-quantum cryptography ("Distinct from quantum cryptography … e.g. QKD") | Acronym used with parenthetical name but no entry | **New entry** under Q — definition and distinction from PQC |
| 8 | **NISQ** (Noisy Intermediate-Scale Quantum) | Fault-tolerant quantum computer ("current NISQ … devices cannot do it") | Acronym expanded inline but no standalone entry | **New entry** under N — definition of NISQ era and why it cannot break P-256 |
| 9 | **LDPC** (Low-Density Parity-Check codes) | Fault-tolerant quantum computer, Surface code, Logical qubit | Acronym used repeatedly, no entry | **New entry** under L — definition of LDPC codes and relevance to quantum resource estimates |
| 10 | **Learning With Errors (LWE)** | Lattice-based cryptography ("Learning With Errors, Module-LWE") | Mentioned as the foundational hard problem, no entry | **New entry** under L — definition of LWE and its role as the security basis of ML-KEM and ML-DSA |
| 11 | **Link key** | Capsule reference token ("a link key (L₂) that provides access to C₂'s key material"), Chained capsule | Technical term used with definition scattered across two entries | **New entry** under L — dedicated definition with cross-references |
| 12 | **ZK-SNARK** | Programmable cryptography ("ZK-SNARKs"), Verifiable Time-Lock Puzzle ("Groth16 ZK proofs") | Acronym used but no standalone entry | **New entry** under Z — definition, relevance to chained capsules, links to FHE and MPC |
| 13 | **FHE** (Fully Homomorphic Encryption) | Programmable cryptography ("fully homomorphic encryption (FHE)") | Acronym expanded inline but no standalone entry | **New entry** under F — definition and applicability to programmable cryptography |
| 14 | **MPC** (Secure Multi-Party Computation) | Programmable cryptography ("secure multi-party computation (MPC)") | Acronym expanded inline but no standalone entry | **New entry** under M — definition and applicability to distributed capsule computation |
| 15 | **RSA** | Q-Day ("breaking … RSA and ECC"), Shor's algorithm ("RSA, Diffie-Hellman"), Time-lock puzzle ("RSA modulus") | Classic cryptosystem used by name with no entry | **New entry** under R — definition and quantum vulnerability |
| 16 | **ECDSA** | ML-DSA ("The primary post-quantum replacement for ECDSA") | Used as the thing ML-DSA replaces, no entry | **New entry** under E — definition and quantum vulnerability |
| 17 | **DAG** (Directed Acyclic Graph) | Chained capsule ("A directed acyclic graph (DAG) of Heirlooms capsules") | Expanded inline but no standalone entry | **New entry** under D — brief definition for readers unfamiliar with graph theory |
| 18 | **Bloom filter** | Puncturable Encryption ("Bloom filter data structures") | Technical data structure cited with no entry | **New entry** under B — definition and role in puncturable encryption |
| 19 | **Hybrid scheme** | Hybrid key exchange description ("a hybrid scheme"), X-Wing ("Heirlooms' Android/web hybrid scheme") | Informal shorthand used without cross-reference to the full entry | **Cross-reference stub** under H: "Hybrid scheme — see *Hybrid key exchange*" |
| 20 | **SPHINCS+** | FIPS 205 ("derived from SPHINCS+"), SLH-DSA ("Derived from SPHINCS+") | Name used as a source/origin with no entry | **Cross-reference stub** under S: "SPHINCS+ — see *SLH-DSA*" |
| 21 | **FALCON** | FIPS 206 ("specifying FALCON") | Algorithm name used with no entry | **Cross-reference stub** under F: "FALCON — see *FIPS 206*" |
| 22 | **League of Entropy** (duplicate stub) | L section had both a malformed stub ("see existing entry (previously defined in RES-001 section)") and a real entry | Malformed cross-reference pointing at itself | **Fixed**: removed the malformed stub; retained the real entry with added "See also" links |
| 23 | **Re-wrap cost** | DEK re-wrap ("Re-wrap cost is O(DEKs), not O(file bytes)"), Re-wrap ("Re-wrap cost is O(keys), not O(files)") | Compound phrase used as a technical metric without definition | **Inline clarification** added to Re-wrap entry: "Re-wrap cost (the computational and network burden of the re-wrapping process) is O(keys), not O(files)" |
| 24 | **re-wrap cost** cross-reference in DEK re-wrap | DEK re-wrap uses the phrase without an inline explanation at point of use | No gap in the entry itself — phrase is used naturally and Re-wrap entry defines it | **No change needed** — DEK re-wrap already cross-references Re-wrap via "See also" |

---

## Entries Added (new)

| Entry | Section |
|-------|---------|
| Bloom filter | B |
| Care Mode | C |
| DAG (Directed Acyclic Graph) | D |
| Double Ratchet | D |
| ECDSA | E |
| FALCON (stub) | F |
| FHE | F |
| HNDL attack window (stub + inline) | H |
| Hybrid scheme (stub) | H |
| LDPC | L |
| Learning With Errors (LWE) | L |
| Link key | L |
| MPC | M |
| NISQ | N |
| PQC migration | P |
| QKD | Q |
| RSA | R |
| SPHINCS+ (stub) | S |
| TEE (stub + inline) | T |
| Triple Ratchet | T |
| ZK-SNARK | Z |

---

## Entries Modified (inline expansion or "See also" added)

| Entry | Change |
|-------|--------|
| Lattice-based cryptography | Added italic link to Learning With Errors and "See also *Learning With Errors (LWE)*" |
| Hybrid key exchange | Added "See also *ML-KEM*, *HKDF*, *X-Wing*, *HPKE*" |
| Re-wrap | Expanded "Re-wrap cost" phrase with inline clarification; added *DEK re-wrap* and *PQC migration* to "See also" |
| League of Entropy (duplicate stub) | Removed malformed stub; remaining entry updated with "See also *drand*, *BLS signature scheme*, *Threshold signature*" |

---

## Acceptance criteria check

- [ ] No entry description contains a bolded or all-caps term without a reachable definition — **met**: all newly cited terms have entries or stubs.
- [ ] PQC migration gap resolved with at minimum a cross-reference stub — **met**: full standalone entry added under P.
- [ ] GLOSSARY.md header updated with new last-updated date and RES-005 reference — **met**: updated to 2026-05-17 (RES-005).
