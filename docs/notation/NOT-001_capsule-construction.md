# NOT-001 — Capsule Construction: Formal Notation

**ID:** NOT-001  
**Date:** 2026-05-16  
**Author:** Technical Author  
**Status:** Draft  
**Relates to:** ARCH-003, ARCH-005, ARCH-006, ARCH-007, ARCH-008, TAU-001  

---

## Notation conventions

- $\mathcal{K}$ — a key space (set of uniformly distributed bitstrings of specified length)
- $\mathbf{k}$ — a specific key value (bold lowercase for key material)
- $\{0,1\}^n$ — the set of $n$-bit bitstrings
- $\xleftarrow{\$}$ — uniform random sampling
- $\oplus$ — bitwise XOR
- $\Vert$ — concatenation
- $\text{HKDF}(\text{ikm}, \text{salt}, \text{info})$ — HMAC-based key derivation function (RFC 5869), SHA-256 instantiation
- $\text{Argon2id}(p, s; \theta)$ — memory-hard password KDF with parameters $\theta$
- $\text{AESGCM}(\mathbf{k}, n, m)$ — AES-256-GCM encryption of message $m$ under key $\mathbf{k}$ and nonce $n$; output is $(c, \tau)$ where $c$ is ciphertext and $\tau$ is the 128-bit authentication tag
- $\text{AESGCM}^{-1}(\mathbf{k}, n, c, \tau)$ — AES-256-GCM decryption; returns plaintext or $\bot$
- $\text{ECDH}(a, B)$ — Elliptic-Curve Diffie-Hellman on P-256; returns a shared secret point
- $\text{IBE-seal}(pk, m)$ — Identity-Based Encryption seal under public key $pk$ (BLS12-381 drand tlock instantiation)
- $\text{IBE-open}(sk, c)$ — IBE decryption; returns $m$ or $\bot$ if the key is not yet published
- $\text{HMAC}(\mathbf{k}, m)$ — HMAC-SHA-256
- $\text{SHA256}(m)$ — SHA-256 hash function
- $[k, n]_{\text{GF}(2^8)}$ — Shamir $(k, n)$ secret sharing over $\text{GF}(2^8)$

---

## 1. Master key derivation

### 1.1 Android and web path (Argon2id)

Let $p \in \{0,1\}^*$ be the user's passphrase and $s \xleftarrow{\$} \{0,1\}^{128}$ a per-user random salt stored on the server.

$$
\mathbf{MK} = \text{Argon2id}\!\left(p,\, s;\; m=65536\,\text{KiB},\; t=3,\; p=1,\; \ell=32\right)
$$

$\mathbf{MK} \in \{0,1\}^{256}$ is the user's master key. It is never transmitted to the server.

### 1.2 iOS path (CryptoKit)

On iOS, `CryptoKit.HKDF<SHA256>` is used against device-resident key material managed by the Secure Enclave. The output semantic is identical: $\mathbf{MK} \in \{0,1\}^{256}$. Platform differences in the KDF are not cryptographically significant for the constructions below.

---

## 2. Sub-key derivation (HKDF tree)

All sub-keys are derived from $\mathbf{MK}$ via HKDF with a zero-length salt and a UTF-8 purpose string as the `info` parameter.

$$
\mathbf{SK}_x = \text{HKDF}\!\left(\mathbf{MK},\; \text{salt}=\varepsilon,\; \text{info}=\text{UTF-8}(x)\right)
$$

The defined purpose strings and their derived keys are:

| Purpose string $x$ | Derived key | Use |
|---|---|---|
| `"tag-token-v1"` | $\mathbf{K}_{\text{tag}}$ | HMAC key for tag token derivation (ARCH-007) |
| `"tag-display-v1"` | $\mathbf{K}_{\text{disp}}$ | AES-256-GCM key for tag display-name ciphertexts |
| `"auto-tag-token-v1"` | $\mathbf{K}_{\text{auto}}$ | HMAC key for auto-applied tag tokens; namespace-isolated from $\mathbf{K}_{\text{tag}}$ |

Sharing sub-keys and wrapping sub-keys are not derived from $\mathbf{MK}$ directly but are stored as separately generated key material wrapped to device public keys (see §3 and §4).

---

## 3. Tag token generation (ARCH-007)

$$
\mathbf{K}_{\text{tag}} = \text{HKDF}\!\left(\mathbf{MK},\; \varepsilon,\; \text{UTF-8}(\texttt{"tag-token-v1"})\right)
$$

$$
T_v = \text{HMAC}\!\left(\mathbf{K}_{\text{tag}},\; \text{UTF-8}(v)\right)
$$

where $v$ is a tag value string. $T_v \in \{0,1\}^{256}$ is the opaque tag token stored on the server. The server can compare $T_v = T_{v'}$ but cannot invert $T_v$ to recover $v$. Cross-user correlation is prevented because different users hold different $\mathbf{MK}$ and thus different $\mathbf{K}_{\text{tag}}$.

Residual leakage: equality, frequency, co-occurrence, and trellis correlation — accepted per SEC-012.

---

## 4. Per-file encryption

### 4.1 DEK generation

For each uploaded file, a fresh Data Encryption Key is sampled:

$$
\mathbf{DEK} \xleftarrow{\$} \{0,1\}^{256}
$$

### 4.2 File encryption (symmetric envelope)

Let $f \in \{0,1\}^*$ be the plaintext file bytes, $n \xleftarrow{\$} \{0,1\}^{96}$ a fresh nonce.

$$
(c_f, \tau_f) = \text{AESGCM}\!\left(\mathbf{DEK},\; n,\; f\right)
$$

The stored blob is the versioned envelope $\mathcal{E}_{\text{sym}}$:

$$
\mathcal{E}_{\text{sym}} = \underbrace{0x01}_{\text{version}} \Vert \underbrace{\text{alg}}_{\text{e.g.~aes256gcm-v1}} \Vert n \Vert c_f \Vert \tau_f
$$

### 4.3 DEK wrap under master key

$$
n_d \xleftarrow{\$} \{0,1\}^{96}
$$
$$
(c_d, \tau_d) = \text{AESGCM}\!\left(\mathbf{MK},\; n_d,\; \mathbf{DEK}\right)
$$

The stored blob is $\mathcal{E}_{\text{sym}}$ with $\text{alg} = \texttt{"master-aes256gcm-v1"}$.

### 4.4 Plot key wrapping (M10 shared plots)

Let $\mathbf{K}_{\text{plot}} \in \{0,1\}^{256}$ be the shared plot group key and $(d_{\text{priv}}, D_{\text{pub}})$ a device's P-256 sharing keypair.

$$
\mathbf{W}_{\text{plot}} = \text{ECDH-HKDF-wrap}\!\left(D_{\text{pub}},\; \mathbf{K}_{\text{plot}}\right)
$$

where $\text{ECDH-HKDF-wrap}(P, m)$ denotes:
1. $e_{\text{priv}} \xleftarrow{\$} \mathbb{Z}_q$, compute $E_{\text{pub}} = e_{\text{priv}} \cdot G$ (ephemeral P-256 keypair)
2. $z = \text{ECDH}(e_{\text{priv}},\, P)$
3. $\mathbf{k}_{\text{wrap}} = \text{HKDF}(z,\, \varepsilon,\, \text{UTF-8}(\texttt{"p256-ecdh-hkdf-aes256gcm-v1"}))$
4. $n_e \xleftarrow{\$} \{0,1\}^{96}$; $(c_e, \tau_e) = \text{AESGCM}(\mathbf{k}_{\text{wrap}},\, n_e,\, m)$
5. Output $\mathcal{E}_{\text{asym}} = 0x01 \Vert \text{alg} \Vert E_{\text{pub}} \Vert n_e \Vert c_e \Vert \tau_e$

The plot DEK is then wrapped under $\mathbf{K}_{\text{plot}}$:

$$
\mathcal{E}_{\text{plot-dek}} = \text{AESGCM}\!\left(\mathbf{K}_{\text{plot}},\, n_p,\, \mathbf{DEK}\right)
$$

with $\text{alg} = \texttt{"plot-aes256gcm-v1"}$.

---

## 5. Capsule construction (M11)

A capsule binds one or more files (each encrypted under their own $\mathbf{DEK}$) to a delivery condition. In M11, the primary delivery condition is either:
- a scheduled time gate (`unlock_at`); or
- a tlock IBE gate (BLS12-381 drand round); or
- a Shamir executor recovery path.

These are not mutually exclusive; a single capsule may combine multiple paths.

### 5.1 DEK blinding split (tlock capsules only)

For tlock-sealed capsules, the client generates two 256-bit components whose XOR is the capsule's content key:

$$
\mathbf{DEK} \xleftarrow{\$} \{0,1\}^{256}
\qquad
\mathbf{DEK}_{\text{client}} \xleftarrow{\$} \{0,1\}^{256}
\qquad
\mathbf{DEK}_{\text{tlock}} = \mathbf{DEK} \oplus \mathbf{DEK}_{\text{client}}
$$

**Security property:** The server stores $\mathbf{DEK}_{\text{tlock}}$ and the IBE ciphertext of $\mathbf{DEK}_{\text{client}}$. Neither component alone recovers $\mathbf{DEK}$. A server compromise pre-round cannot decrypt capsule content.

### 5.2 ECDH recipient wrapping

For each recipient $i$ with sharing public key $R_i^{\text{pub}}$:

**Non-tlock capsule** — wrap full $\mathbf{DEK}$:

$$
\mathbf{W}_{\text{cap},i} = \text{ECDH-HKDF-wrap}\!\left(R_i^{\text{pub}},\; \mathbf{DEK};\; \text{alg}=\texttt{"capsule-ecdh-aes256gcm-v1"}\right)
$$

**Tlock capsule** — dual wrapping:

$$
\mathbf{W}_{\text{cap},i} = \text{ECDH-HKDF-wrap}\!\left(R_i^{\text{pub}},\; \mathbf{DEK}\right)
\quad\text{(iOS-compatible direct path)}
$$

$$
\mathbf{W}_{\text{blind},i} = \text{ECDH-HKDF-wrap}\!\left(R_i^{\text{pub}},\; \mathbf{DEK}_{\text{client}}\right)
\quad\text{(Android/web blinded path)}
$$

Both use $\text{alg} = \texttt{"capsule-ecdh-aes256gcm-v1"}$.

### 5.3 tlock IBE sealing

The tlock ciphertext seals $\mathbf{DEK}_{\text{client}}$ (not $\mathbf{DEK}$) against a specific drand round $r$:

$$
C_{\text{tlock}} = \text{IBE-seal}\!\left(pk_{\text{drand}}^{(r)},\; \mathbf{DEK}_{\text{client}}\right)
$$

The round number $r$ is chosen such that the expected publication time $t_r \leq \texttt{unlock\_at} + 1\,\text{h}$.

The digest for tamper detection is:

$$
d = \text{SHA256}\!\left(\mathbf{DEK}_{\text{tlock}}\right)
$$

At sealing time, the client submits $(\mathbf{W}_{\text{cap},i},\, \mathbf{W}_{\text{blind},i},\, C_{\text{tlock}},\, \mathbf{DEK}_{\text{tlock}},\, d)$ to the server. The server stores $\mathbf{DEK}_{\text{tlock}}$ in column `tlock_dek_tlock` and $d$ in `tlock_key_digest`.

### 5.4 iOS compatibility path

iOS cannot produce or consume BLS12-381 material in M11. An iOS recipient of a tlock-sealed capsule uses only the direct ECDH path:

$$
\mathbf{DEK} = \text{ECDH-HKDF-unwrap}\!\left(R_i^{\text{priv}},\; \mathbf{W}_{\text{cap},i}\right)
$$

No call to `/tlock-key`, no XOR step. The presence of $\mathbf{W}_{\text{cap},i}$ on every tlock capsule is the iOS compatibility guarantee.

### 5.5 Delivery decryption (Android/web tlock path)

After the drand round $r$ publishes its round key $sk^{(r)}$:

1. Client ECDH-unwraps the blinding mask: $\mathbf{DEK}_{\text{client}} = \text{ECDH-HKDF-unwrap}(R_i^{\text{priv}},\, \mathbf{W}_{\text{blind},i})$

2. Server confirms gate open: $\mathbf{DEK}_{\text{client}}' = \text{IBE-open}(sk^{(r)},\, C_{\text{tlock}}) \neq \bot$ (confirmation only; not returned to client)

3. Server verifies: $\text{SHA256}(\mathbf{DEK}_{\text{tlock}}) \stackrel{?}{=} d$

4. Server serves $\mathbf{DEK}_{\text{tlock}}$ via `GET /api/capsules/:id/tlock-key`

5. Client reconstructs: $\mathbf{DEK} = \mathbf{DEK}_{\text{client}} \oplus \mathbf{DEK}_{\text{tlock}}$

6. Client decrypts content: $f = \text{AESGCM}^{-1}(\mathbf{DEK},\, n,\, c_f,\, \tau_f)$

### 5.6 Shamir threshold recovery

The Shamir scheme is computed over the full capsule $\mathbf{DEK}$ (not over $\mathbf{DEK}_{\text{client}}$). This is true regardless of whether tlock is used.

$$
\{S_1, S_2, \ldots, S_n\} = [k, n]_{\text{GF}(2^8)}(\mathbf{DEK})
$$

where $k$ is the threshold (minimum shares required to reconstruct) and $n$ is the total number of shares. Each share $S_j$ is a 32-byte string in $\{0,1\}^{256}$.

Share $S_j$ is wrapped to executor $j$'s sharing public key $E_j^{\text{pub}}$:

$$
\mathbf{W}_{S_j} = \text{ECDH-HKDF-wrap}\!\left(E_j^{\text{pub}},\; \mathbf{S}_j^{\text{struct}}\right)
$$

where $\mathbf{S}_j^{\text{struct}}$ is the 64-byte Shamir share encoding:

$$
\mathbf{S}_j^{\text{struct}} = \underbrace{j}_{\text{2B, share index}} \Vert \underbrace{k}_{\text{2B, threshold}} \Vert \underbrace{n}_{\text{2B, total}} \Vert \underbrace{0^{26}}_{\text{reserved}} \Vert \underbrace{S_j}_{\text{32B, share value}}
$$

Reconstruction by an executor quorum of size $\geq k$:

$$
\mathbf{DEK} = \text{Lagrange-interpolate}\!\left(\{(j, S_j)\}_{|Q|=k}\right)
$$

An executor holding the reconstructed $\mathbf{DEK}$ does **not** need to call `/tlock-key` and does not interact with the blinding scheme.

---

## 6. Chained capsule construction (ARCH-008)

A chained capsule pair $(C_1, C_2)$ is a directed edge in a capsule DAG. $C_2$ is only accessible to the winner of $C_1$'s puzzle.

### 6.1 Key embedding

The link key $\mathbf{L}_2$ (sufficient to access $C_2$'s key material) is embedded inside $C_1$'s plaintext as a JSON field `capsule_ref`:

$$
\text{plaintext}(C_1) \ni \left\{ \texttt{capsule\_id}: C_2.\text{UUID},\; \texttt{link\_key}: \mathbf{L}_2 \right\}
$$

Because $\text{plaintext}(C_1)$ is protected by $C_1$'s delivery gate, $\mathbf{L}_2$ is inaccessible until $C_1$ is solved. If $C_1$'s window expires without a solve, $\mathbf{L}_2$ is permanently inaccessible.

### 6.2 Puzzle verification

Let $s_p$ be the puzzle secret (generated by author, embedded in $C_1$'s plaintext, never stored server-side), and $a$ the correct answer.

$$
h_p = \text{SHA256}(a) \quad \text{(or } \text{HMAC}(s_p, a) \text{ if VTLP-NP is used)}
$$

At claim time, the solver submits $a'$; the server verifies:

$$
\text{SHA256}(a') \stackrel{?}{=} h_p
$$

The first successful submission is atomically recorded as the winner.

### 6.3 Care Mode key ceremony

The Care Mode monitoring key $\mathbf{K}_{\text{care}}$ is modelled as a shared-plot-key variant: one writer (care recipient), one or more readers (POA holders).

$$
\mathbf{K}_{\text{care}} \xleftarrow{\$} \{0,1\}^{256}
$$

$$
\mathbf{W}_{\text{care},j} = \text{ECDH-HKDF-wrap}\!\left(P_j^{\text{pub}},\; \mathbf{K}_{\text{care}}\right)
$$

for each POA holder $j$ with sharing key $P_j^{\text{pub}}$. Location events are encrypted:

$$
(c_{\text{loc}}, \tau_{\text{loc}}) = \text{AESGCM}\!\left(\mathbf{K}_{\text{care}},\; n_{\text{loc}},\; \text{GPS coordinates}\right)
$$

The server stores $(c_{\text{loc}}, \tau_{\text{loc}})$ and relays the ciphertext to POA holders. The server never sees plaintext coordinates.

---

## 7. Envelope format summary

All encrypted blobs use the Heirlooms versioned envelope (version byte `0x01`). Two variants:

**Symmetric envelope:**
$$
\mathcal{E}_{\text{sym}} = \underbrace{[1]}_{\text{0x01}} \Vert \underbrace{[1+|\text{alg}|]}_{\text{alg length + string}} \Vert \underbrace{[12]}_{\text{nonce}} \Vert \underbrace{[*]}_{\text{ciphertext}} \Vert \underbrace{[16]}_{\text{GCM tag}}
$$

**Asymmetric envelope (P-256):**
$$
\mathcal{E}_{\text{asym}} = \underbrace{[1]}_{\text{0x01}} \Vert \underbrace{[1+|\text{alg}|]}_{\text{alg}} \Vert \underbrace{[65]}_{\text{ephemeral pubkey}} \Vert \underbrace{[12]}_{\text{nonce}} \Vert \underbrace{[*]}_{\text{ciphertext}} \Vert \underbrace{[16]}_{\text{GCM tag}}
$$

Algorithm IDs in use (M7–M11):

| ID | Use |
|---|---|
| `aes256gcm-v1` | Content encryption under DEK |
| `master-aes256gcm-v1` | DEK wrapped under master key |
| `p256-ecdh-hkdf-aes256gcm-v1` | Master key / sharing key wrapped to device pubkey |
| `argon2id-aes256gcm-v1` | Master key wrapped under passphrase |
| `plot-aes256gcm-v1` | Upload DEK wrapped under plot group key |
| `capsule-ecdh-aes256gcm-v1` | Capsule DEK (or $\mathbf{DEK}_{\text{client}}$) wrapped to recipient sharing pubkey |
| `shamir-share-v1` | Shamir share of capsule DEK wrapped to executor |
| `tlock-bls12381-v1` | IBE ciphertext identifier (not a binary prefix; stored as opaque BYTEA) |

---

## 8. Security properties summary

| Property | Guarantee |
|---|---|
| Server cannot read file content | Files encrypted under $\mathbf{DEK}$; $\mathbf{DEK}$ never transmitted to server in plaintext |
| Server cannot read tags | Tags stored as $\text{HMAC}(\mathbf{K}_{\text{tag}}, v)$; $\mathbf{K}_{\text{tag}}$ never leaves client |
| Sealed capsule cannot be opened early | Recipient's $\mathbf{W}_{\text{cap},i}$ is withheld server-side until $\texttt{unlock\_at}$ |
| tlock: server-blind delivery | Server holds $\mathbf{DEK}_{\text{tlock}}$ but never $\mathbf{DEK}_{\text{client}}$; neither alone decrypts |
| Executor recovery without tlock | Shamir shares are over $\mathbf{DEK}$; executor quorum reconstructs $\mathbf{DEK}$ directly |
| iOS compatibility | $\mathbf{W}_{\text{cap},i}$ always wraps $\mathbf{DEK}$; iOS decrypts without BLS12-381 |
| Tag cross-user isolation | $\mathbf{K}_{\text{tag}}$ is per-user; same value produces different tokens across users |

---

*See also: PAP-001 (layered guide), NOT-001-sequence-diagrams (Mermaid sequence diagrams).*
