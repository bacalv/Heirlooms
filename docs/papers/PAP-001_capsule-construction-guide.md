# Heirlooms Capsule Construction — A Technical Guide

**ID:** PAP-001  
**Date:** 2026-05-16  
**Author:** Technical Author  
**Status:** Draft — public narrative layer placeholder pending PHI-002  
**Relates to:** NOT-001, NOT-001-seq, ARCH-003, ARCH-005, ARCH-006, ARCH-007, ARCH-008, TAU-001  

---

## Part 1: What a capsule is

<!-- NARRATIVE_PLACEHOLDER — to be filled from PHI-002 output -->
<!-- PHI-002 will provide the philosophical framing for what a capsule means to the author and recipient: the act of sealing a moment against time, the ethics of conditional delivery, the asymmetry between the person who seals and the person who receives. That framing belongs here and will be integrated when PHI-002 is complete. -->

### Conceptual introduction (researcher-facing, pending PHI-002 narrative framing)

An Heirlooms capsule is a cryptographically sealed container that delivers content to a named recipient at a specified future time. Unlike a scheduled email — where the sending server holds plaintext and simply chooses when to transmit it — an Heirlooms capsule is sealed in a way that makes the content inaccessible to the Heirlooms infrastructure itself until delivery conditions are met.

This property — **server-blindness** — is the central design constraint of the system. It means that a compromise of the Heirlooms server before the scheduled delivery date cannot expose capsule content. The server is a routing and scheduling layer, not a custodian of plaintext.

Capsules may combine multiple delivery mechanisms: a calendar time gate, a cryptographic time-lock seeded by a public randomness beacon, a set of Shamir shares held by nominated executors, or a combination of all three. The cryptographic construction ensures that each delivery path is independent — the server cannot bypass one path by exploiting another.

The system also supports chained capsules (where solving a puzzle inside one capsule reveals the key to the next), and a Care Mode extension for encrypted location monitoring by Power of Attorney holders. These are M12 and M13 scope respectively; their cryptographic construction composes directly from the M11 primitives described in this guide.

---

## Part 2: The key hierarchy

### 2.1 Master key

Every Heirlooms vault is protected by a **master key** ($\mathbf{MK}$), a 256-bit secret derived from the user's login credentials. On Android and web, this derivation uses Argon2id — a memory-hard password KDF designed to resist brute-force attacks even on specialised hardware. On iOS, the derivation uses the system's CryptoKit stack, which integrates with the Secure Enclave.

$\mathbf{MK}$ exists only in device memory during an active vault session. It is never transmitted to the server.

For formal derivation see NOT-001 §1.

### 2.2 Sub-key derivation (HKDF tree)

All cryptographic purposes use keys **derived** from $\mathbf{MK}$ via HKDF (RFC 5869), not $\mathbf{MK}$ itself. This separates concerns: a key derived for one purpose cannot be used to attack a different purpose, even if an adversary somehow obtains one derived key.

The derivation pattern is:

$$
\mathbf{SK}_x = \text{HKDF}\!\left(\mathbf{MK},\; \text{salt}=\varepsilon,\; \text{info}=\text{UTF-8}(x)\right)
$$

Key purpose strings include `"tag-token-v1"` (tag privacy), `"tag-display-v1"` (encrypted tag names), and `"auto-tag-token-v1"` (auto-applied tags, namespace-isolated from user-applied tags).

Sharing keypairs and capsule DEKs are separately generated, not derived from $\mathbf{MK}$. They are **wrapped** (encrypted) to device public keys for storage.

For the full sub-key tree see NOT-001 §2.

### 2.3 Per-file data encryption keys (DEKs)

Each uploaded file is encrypted under a freshly generated, random 256-bit **Data Encryption Key** (DEK). The DEK is then wrapped (encrypted) under a higher-level key for storage. This layered approach means rotating or revoking access to a set of files requires only re-wrapping the DEK — the ciphertext of the file itself does not change.

The encryption format is AES-256-GCM with a fresh 12-byte random nonce per operation. All encrypted blobs use a versioned binary envelope format that makes the algorithm explicit in the blob itself; unknown algorithms fail loudly rather than silently misinterpreting bytes.

For the formal encryption construction see NOT-001 §4. For the envelope binary format see NOT-001 §7.

---

## Part 3: Sealing a capsule — the key ceremony

Sealing a capsule is the act of committing to a delivery condition and locking the content keys such that only the intended path can recover them. The ceremony has several phases.

### 3.1 Content preparation

Before sealing, the capsule's content files are already encrypted under their individual DEKs (uploaded as part of normal vault operation). At sealing time, the client generates a **capsule DEK** ($\mathbf{DEK}$) — a fresh 256-bit key — and re-wraps each file's DEK under the capsule DEK. This creates a single key that represents "access to this capsule."

### 3.2 The blinding split (tlock capsules)

For time-locked capsules, the capsule DEK is not transmitted to the server in any form. Instead, the client splits it into two 256-bit components using XOR:

$$
\mathbf{DEK}_{\text{client}} \xleftarrow{\$} \{0,1\}^{256}
\qquad
\mathbf{DEK}_{\text{tlock}} = \mathbf{DEK} \oplus \mathbf{DEK}_{\text{client}}
$$

- $\mathbf{DEK}_{\text{client}}$ (the **client mask**) is sealed inside a cryptographic time-lock (tlock IBE) keyed to a specific future drand round. It is inaccessible to anyone until that round publishes its randomness.
- $\mathbf{DEK}_{\text{tlock}}$ (the **server component**) is stored on the server as an opaque blob. The server cannot reconstruct $\mathbf{DEK}$ from this alone.

Neither the server nor drand holds $\mathbf{DEK}$. Reconstruction requires both halves — and the client-side half is locked behind the time-lock until the scheduled moment. This is the **blinding guarantee**: even a fully compromised server cannot read capsule content before the delivery date.

### 3.3 Recipient wrapping

For each intended recipient $i$ with a known P-256 sharing public key, the client performs an ECDH key agreement to produce a recipient-specific wrapped key:

- **iOS path** (and non-tlock capsules): wraps the full $\mathbf{DEK}$ directly.
- **Android/web tlock path**: wraps only $\mathbf{DEK}_{\text{client}}$ (the mask), so the recipient must later XOR with $\mathbf{DEK}_{\text{tlock}}$ obtained from the server.

Both wraps coexist on tlock capsules, ensuring iOS recipients can always decrypt without needing BLS12-381 support.

### 3.4 tlock IBE sealing

The client submits $\mathbf{DEK}_{\text{client}}$ to the tlock mechanism, which seals it inside an Identity-Based Encryption ciphertext locked to a specific drand round number. The round is chosen to publish at or before the capsule's `unlock_at` date.

The server stores a SHA-256 digest of $\mathbf{DEK}_{\text{tlock}}$ for tamper detection at delivery time.

### 3.5 Sealing validation

The server validates the structural integrity of all envelopes but never attempts decryption. It enforces the rule that every capsule must have at least one viable delivery path: a tlock gate, an accepted executor quorum, or all recipients having known public keys.

For the full sealing ceremony sequence see NOT-001-seq Diagram 3.

---

## Part 4: Delivering a capsule

### 4.1 Calendar-gated delivery (non-tlock)

For non-tlock capsules, delivery is straightforward. When `now() >= unlock_at`, the server surfaces the capsule to the delivery system. The recipient fetches the capsule and its `wrapped_capsule_key`, ECDH-unwraps it with their sharing private key, and decrypts the content.

The server's role is scheduling. The recipient's sharing private key is the cryptographic gate.

### 4.2 tlock delivery (Android/web)

When the drand round publishes its randomness, the server confirms the IBE gate is open. It then serves $\mathbf{DEK}_{\text{tlock}}$ to the authenticated recipient via `GET /api/capsules/:id/tlock-key`. The recipient:

1. ECDH-unwraps the blinding mask to recover $\mathbf{DEK}_{\text{client}}$.
2. XORs: $\mathbf{DEK} = \mathbf{DEK}_{\text{client}} \oplus \mathbf{DEK}_{\text{tlock}}$.
3. Decrypts content with $\mathbf{DEK}$.

**Server-blindness:** The server served $\mathbf{DEK}_{\text{tlock}}$ (one XOR half) but never had $\mathbf{DEK}_{\text{client}}$ (locked in the IBE ciphertext until the round). Even after delivery, the server cannot retroactively decrypt the content without also compromising the recipient's ECDH private key.

### 4.3 tlock delivery (iOS)

iOS recipients ignore the tlock machinery entirely. They use `wrapped_capsule_key` (which always wraps the full $\mathbf{DEK}$) and ECDH-unwrap directly. No BLS12-381, no XOR step.

For the full delivery sequence see NOT-001-seq Diagram 4.

---

## Part 5: Executor recovery

Shamir Secret Sharing provides a recovery path for situations where the tlock gate cannot be satisfied — for example, in the event of the capsule author's death before the delivery date, or a drand chain incident.

### 5.1 Share generation

At sealing time (or at a separate distribution step), the client splits the full $\mathbf{DEK}$ (not $\mathbf{DEK}_{\text{client}}$) into $n$ Shamir shares using GF(2^8) polynomial interpolation. A threshold $k$ shares, where $k \leq n$, is sufficient to reconstruct $\mathbf{DEK}$.

Each share is wrapped individually to the corresponding executor's P-256 sharing public key and uploaded to the server. The server stores the wrapped share; it never sees the plaintext share value.

### 5.2 Reconstruction

When an executor quorum of size $\geq k$ chooses to cooperate:

1. Each executor fetches their own wrapped share from the server.
2. Each executor ECDH-unwraps their share using their own private key.
3. The quorum performs Lagrange interpolation on their $k$ share values to reconstruct $\mathbf{DEK}$.

This reconstruction happens entirely on executor devices. The server is not involved in the assembly. The reconstructed $\mathbf{DEK}$ can then be used to decrypt capsule content directly, without any interaction with the tlock mechanism.

For the full executor recovery sequence see NOT-001-seq Diagram 5.

---

## Part 6: Chained capsules and Care Mode

### 6.1 Chained capsules (M12)

A chained capsule pair $(C_1, C_2)$ creates a conditional delivery structure: $C_2$ is only accessible to the solver of $C_1$. The key innovation is that $C_2$'s link key is embedded inside $C_1$'s encrypted plaintext. This means:

- If $C_1$'s window closes without a solve, $C_1$'s content (including $C_2$'s link key) was never decrypted — so $C_2$ becomes permanently inaccessible by the same guarantee as $C_1$ itself. No additional deletion protocol is required.
- The first solver is determined by a server-mediated atomic claim: a database-level first-write-wins constraint ensures exactly one winner.

Optionally, the puzzle format can use Verifiable Time-Lock Puzzles (VTLP-NP) — a zero-knowledge proof attached to the puzzle confirming it has a valid solution, without revealing that solution. This allows solvers to verify effort is worthwhile before committing.

The cryptographic construction composes directly from M11 primitives: M11 Shamir shares, M11 ECDH wrapping, and the existing delivery scheduler. No new cryptographic machinery is introduced.

For formal notation see NOT-001 §6.

### 6.2 Care Mode (M13)

Care Mode enables a Power of Attorney (POA) holder to receive encrypted location updates from a care recipient (for example, an Alzheimer's patient). The cryptographic design is a shared-plot-key variant:

- A fresh 256-bit Care Mode key $\mathbf{K}_{\text{care}}$ is generated and ECDH-wrapped to each authorised POA holder.
- Location data is encrypted client-side under $\mathbf{K}_{\text{care}}$ before transmission.
- The server stores and relays ciphertext. It never sees plaintext coordinates.

Consent is recorded as a W3C Verifiable Credential signed by the care recipient's Ed25519 signing key. Revocation by the care recipient immediately terminates the monitoring session.

The geofence evaluation model for v1 is client-side only: the POA holder's device decrypts coordinates and evaluates geofence membership locally. The server has no visibility into whether a geofence boundary was crossed.

For the architectural assessment see ARCH-008.

---

## Part 7: Tag privacy

### 7.1 The tag token scheme

User-assigned tags (such as "grandmother", "birthday", or personal categorisations of any kind) are personal data. Before M11 Phase 2, tags were stored as plaintext strings on the server — a direct violation of Heirlooms' server-blindness principle.

The E2EE tag scheme (ARCH-007) replaces plaintext tags with HMAC tokens:

$$
\mathbf{K}_{\text{tag}} = \text{HKDF}\!\left(\mathbf{MK},\; \varepsilon,\; \texttt{"tag-token-v1"}\right)
\qquad
T_v = \text{HMAC}\!\left(\mathbf{K}_{\text{tag}},\; \text{UTF-8}(v)\right)
$$

The server stores $T_v$ and can test equality ($T_v = T_{v'}$) but cannot reverse $T_v$ to recover $v$. Because $\mathbf{K}_{\text{tag}}$ is derived from a per-user master key, the same tag value produces different tokens for different users — cross-user correlation is cryptographically prevented.

Tag display names are stored as separate AES-256-GCM ciphertexts under a different derived key (`"tag-display-v1"`). The server returns both; the client decrypts the display name for rendering.

### 7.2 Residual risk (SEC-012)

The deterministic token derivation creates a class of residual metadata leakage: the server can observe which uploads share a token (equality), how often a token appears (frequency), and which tokens tend to co-occur. This does not reveal semantic content but does reveal structural patterns.

This residual risk is formally documented in SEC-012 and accepted as a proportionate trade-off against the requirement for server-side trellis evaluation. The long-term mitigation is client-side trellis evaluation; this is deferred to a future milestone when a local-first sync architecture is available.

For the tag search sequence see NOT-001-seq Diagram 7.

---

## Appendix A: Formal notation

All mathematical notation for the constructions described in this guide is in:

**NOT-001** — `docs/notation/NOT-001_capsule-construction.md`

Covers: master key derivation (§1), HKDF sub-key tree (§2), tag token generation (§3), per-file encryption (§4), capsule construction and blinding split (§5), chained capsule construction (§6), envelope format (§7), and security properties summary (§8).

---

## Appendix B: Sequence diagrams

All Mermaid sequence diagrams with knowledge-boundary annotations are in:

**NOT-001-seq** — `docs/notation/NOT-001_sequence-diagrams.md`

Diagrams:

1. Device registration and master key derivation
2. Upload: DEK generation, encryption, wrapping
3. Capsule seal: DEK blinding split, ECDH wrapping, tlock IBE sealing
4. Capsule delivery: server returns DEK_tlock, client XORs and decrypts
5. Executor recovery: Shamir reconstruction without tlock
6. Shared plot: plot key exchange when a member joins
7. Tag search: HMAC token generation and server-side lookup

---

*For animated versions of the key hierarchy and capsule sealing ceremony, see the Manim scripts in `docs/presentations/manim/`.*  
*For print visualisation recommendations, see `docs/presentations/PRE-001_capsule-key-ceremony.md` §Print Visualisation.*
