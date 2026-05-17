# PAP-002 — Time-Window Capsule: A Step-by-Step Walk-Through

*Authored: 2026-05-16. Audience: patent attorney, technical reviewer, investor.*  
*Cryptographic foundation: RES-002, ARCH-003, ARCH-006.*

---

## The scenario

Alice wants to seal a capsule addressed to Bob with two guarantees:

- **Before w1 — impossible to open.** Not by Bob, not by Alice, not by Heirlooms, not by anyone.
- **During [w1, w2] — Bob can open it.** The window is the intended delivery period.
- **After w2 — permanently inaccessible.** Even Heirlooms cannot reconstruct the content. Even if Bob never opened it.

The content could be a video letter, a set of documents, or any digital material.

---

## The participants

| Party | Role |
|---|---|
| **Alice** | Creates and seals the capsule |
| **Bob** | Named recipient — can decrypt during [w1, w2] |
| **Heirlooms server** | Stores all encrypted material; brokers delivery; never sees plaintext |
| **drand** (League of Entropy) | Decentralised randomness beacon — publishes round keys on a fixed public schedule |
| **C1, C2, C3** | Custodians — each holds one Shamir share of a secret; delete it at w2 |

The custodians in the initial version are Heirlooms-operated nodes. Future versions may include law firms, banks, or nominated individuals chosen by Alice.

---

## The key structure

Three nested layers, each with its own key:

```
Plaintext content
  └── encrypted by DEK          (256-bit key, AES-256-GCM)
        └── DEK encrypted by K_window  (256-bit key, AES-256-GCM)

K_window = K_a  ⊕  K_b         (bitwise XOR — 256 bits each)
              │         │
              │         └── Shamir secret  —  reconstructable from ≥2 of 3 custodian shares
              │                                 custodians release shares only in [w1, w2]
              │                                 custodians delete shares at w2
              │
              └── Time-locked random value  —  sealed by drand's IBE scheme
                                               trustlessly revealed at the round corresponding to w1
```

**The XOR is the central insight.** `K_window` requires both `K_a` and `K_b`. Either alone is useless.

---

## Phase 0 — Alice seals the capsule (at time T_seal, before w1)

### Step 1: Generate the key material

Alice's device generates, entirely locally:

```
DEK       — 256 random bits    (the content key; never leaves Alice's device in plaintext)
K_a       — 256 random bits    (will be time-locked via drand)
K_b       — 256 random bits    (will be Shamir-shared to custodians)

K_window  = K_a ⊕ K_b         (computed locally)
```

### Step 2: Encrypt the content

```
C            = AES-256-GCM(key=DEK,      plaintext=content)
DEK_sealed   = AES-256-GCM(key=K_window, plaintext=DEK)
```

`C` and `DEK_sealed` are the only encrypted forms of the content that will ever leave Alice's device.

### Step 3: Time-lock K_a

Alice looks up the drand chain's schedule and finds the round number `R_w1` whose publication time corresponds to `w1`. She then produces:

```
K_a_sealed = tlock_encrypt(plaintext=K_a, round=R_w1)
```

`K_a_sealed` is an IBE ciphertext. The drand chain collectively holds the private key for round `R_w1`. Before that round publishes its randomness, no party — including drand itself, including Heirlooms, including Alice — can compute `K_a` from `K_a_sealed`. After the round publishes, anyone with `K_a_sealed` and the published randomness can compute `K_a`. This is the lower bound: it is enforced by mathematics, not by any party's honesty.

### Step 4: Shamir-split K_b

Alice splits `K_b` into three shares using Shamir's Secret Sharing over GF(2⁸):

```
[s1, s2, s3] = Shamir_split(secret=K_b, threshold=2, total=3)
```

Any two shares reconstruct `K_b`. One share alone reveals nothing.

Each share is encrypted to the corresponding custodian's public key and uploaded:

```
s1_sealed = ECDH_wrap(key=C1_pubkey, plaintext=s1)  →  sent to C1
s2_sealed = ECDH_wrap(key=C2_pubkey, plaintext=s2)  →  sent to C2
s3_sealed = ECDH_wrap(key=C3_pubkey, plaintext=s3)  →  sent to C3
```

Custodians store their wrapped share. Each custodian publishes a commitment `Commit(si)` to the audit log at this point — a hash binding them to the share's existence without revealing it. This creates an on-chain record for the deletion certificate later.

### Step 5: Wrap DEK for Bob

Bob has a P-256 key pair for receiving capsules (his "sharing keypair"). Alice performs:

```
DEK_for_bob = ECDH_wrap(key=Bob_sharing_pubkey, plaintext=DEK)
```

This gives Bob a direct path to `DEK` once the Heirlooms server releases the capsule after `w1`. (See §iOS compatibility note below.)

### Step 6: Upload to Heirlooms server

Alice sends to the server:

```
{
  C,              // encrypted content
  DEK_sealed,     // DEK wrapped under K_window
  K_a_sealed,     // time-locked K_a (IBE ciphertext)
  R_w1,           // drand round number
  w1,             // unlock time
  w2,             // expire time
  DEK_for_bob,    // ECDH-wrapped DEK for Bob's direct path
  custodian_ids   // C1, C2, C3
}
```

### What each party holds after sealing

| Party | Holds | Does NOT hold |
|---|---|---|
| **Alice's device** | Everything (she generated it all) | — |
| **Heirlooms server** | `C`, `DEK_sealed`, `K_a_sealed`, `R_w1`, `w1`, `w2`, `DEK_for_bob` | `K_a`, `K_b`, `K_window`, `DEK`, plaintext |
| **C1** | `s1_sealed` (one Shamir share of `K_b`) | `K_a`, `K_b`, `K_window`, `DEK`, plaintext |
| **C2** | `s2_sealed` | same |
| **C3** | `s3_sealed` | same |
| **Bob** | Nothing yet | — |
| **drand network** | The private key for round `R_w1` (used collectively at w1) | `K_a` — not computable yet even by drand |

---

## Phase 1 — Before w1 (the capsule is sealed)

Bob cannot open the capsule. Neither can anyone else. Here is why each attack fails:

**Attack: Bob tries to decrypt using `DEK_for_bob`.**  
The Heirlooms server withholds `DEK_for_bob` until `now() ≥ w1`. Bob cannot fetch it early.

**Attack: Bob tries to reconstruct `K_window` directly.**  
Bob does not have `K_a` (drand hasn't published round `R_w1`). Even if Bob somehow obtained all three custodian shares and reconstructed `K_b`, he still cannot compute `K_window = K_a ⊕ K_b` without `K_a`. `K_b` alone is mathematically useless.

**Attack: An adversary compromises the Heirlooms server.**  
The server holds `K_a_sealed` but not `K_a`. It holds `DEK_sealed` but not `K_window` or `DEK`. Content is irrecoverable from the server's data alone.

**Attack: An adversary compromises all three custodians.**  
The adversary reconstructs `K_b`. But without `K_a` (locked in the IBE ciphertext), `K_window` is uncomputable. The lower bound is enforced by the drand network independently of the custodians.

**Attack: An adversary bribes drand nodes.**  
drand's round key for `R_w1` is a threshold secret held collectively across a geographically and institutionally diverse consortium (Cloudflare, EPFL, Protocol Labs, and others). Colluding enough nodes to release the key early would require corrupting multiple independent institutions simultaneously.

**Key property:** The lower bound (`w1`) is trustless. No single party can breach it. The cryptographic separation of `K_a` and `K_b` means the custodians (who hold `K_b`) have no power to advance the lower bound, and the drand network (which publishes `K_a`) has no power to collapse the upper bound.

---

## Phase 2 — At w1 (drand publishes round R_w1)

The drand network publishes the randomness for round `R_w1`. This is a public, scheduled event — exactly like a clock striking the hour.

From the published randomness, anyone holding `K_a_sealed` can now compute:

```
K_a = tlock_decrypt(ciphertext=K_a_sealed, round_randomness=drand[R_w1])
```

`K_a` is now permanently public. Anyone in the world who has `K_a_sealed` and internet access can derive it.

This is intentional. `K_a` being public after `w1` does not compromise the capsule because `K_window = K_a ⊕ K_b`, and `K_b` is still held exclusively by the custodians. Public `K_a` + absent `K_b` = no `K_window`.

The Heirlooms server detects the round publication, confirms the gate is open, and makes the capsule visible to Bob.

---

## Phase 3 — During [w1, w2] (Bob opens the capsule)

Bob receives a notification from Heirlooms. He has two routes to `DEK`:

### Route A — Direct ECDH path (simpler; used by iOS)

```
DEK = ECDH_unwrap(key=Bob_sharing_privkey, ciphertext=DEK_for_bob)
```

The server releases `DEK_for_bob` to Bob after `w1` passes. Bob unwraps it with his private key in a single step. He does not need to interact with custodians or drand.

This path is always available as a fallback.

### Route B — Window path (full cryptographic walk-through)

This route demonstrates the construction explicitly and is the primary path for Android and web clients.

**Step 1:** Bob contacts any two custodians (e.g. C1 and C2) and authenticates. Each custodian checks `w1 ≤ now() < w2` before releasing their share.

```
s1 = ECDH_unwrap(key=Bob_sharing_privkey, ciphertext=s1_sealed)   // C1 releases
s2 = ECDH_unwrap(key=Bob_sharing_privkey, ciphertext=s2_sealed)   // C2 releases
```

Wait — the shares are wrapped to the *custodians'* keys, not Bob's. Correction: custodians unwrap their own share with their private key and re-encrypt it to Bob for transit, or the server brokers the release. The mechanics of authenticated share release to Bob are an API concern; the cryptographic point stands: Bob receives the plaintext share values `s1` and `s2` over an authenticated channel.

**Step 2:** Bob reconstructs `K_b`:

```
K_b = Shamir_reconstruct([s1, s2])
```

**Step 3:** Bob fetches `K_a` (now public since w1):

```
K_a = tlock_decrypt(K_a_sealed, drand[R_w1])
```

**Step 4:** Bob computes the window key:

```
K_window = K_a ⊕ K_b
```

**Step 5:** Bob decrypts `DEK`:

```
DEK = AES-256-GCM-decrypt(key=K_window, ciphertext=DEK_sealed)
```

**Step 6:** Bob decrypts the content:

```
plaintext = AES-256-GCM-decrypt(key=DEK, ciphertext=C)
```

The Heirlooms server is not involved in steps 2–6. The reconstruction happens entirely on Bob's device.

---

## Phase 4 — At w2 (custodians delete their shares)

At the expiry time `w2`, each custodian executes a deletion protocol:

1. Custodian overwrites their stored share with zeroes (software; hardware zeroisation where available).
2. Custodian publishes a deletion certificate to the audit log — a signed, timestamped statement that the share has been destroyed, alongside the commitment `Commit(si)` they published at sealing time.

After the threshold of deletions (2 of 3), `K_b` is irrecoverable. No one can reconstruct it.

---

## Phase 5 — After w2 (permanently inaccessible)

After `w2` the state of the world is:

| Value | Status |
|---|---|
| `K_a` | Permanently public (drand published it at `R_w1`) |
| `K_b` | **Permanently gone** (shares deleted by custodians) |
| `K_window = K_a ⊕ K_b` | **Permanently uncomputable** — `K_a` is known but `K_b` is not |
| `DEK` | Wrapped under `K_window`; permanently inaccessible |
| Content | Encrypted under `DEK`; permanently inaccessible |

The content is gone — not locked, not archived somewhere, not recoverable by Heirlooms, not recoverable by Alice. Anyone holding all the encrypted blobs (`C`, `DEK_sealed`) and the public `K_a` is still stuck: without `K_b`, `K_window` cannot be formed, and `DEK` cannot be unwrapped.

---

## The XOR — why it matters

A natural question is: why XOR `K_a` and `K_b` rather than simply requiring both as independent keys?

The XOR construction produces a single 256-bit `K_window` that is:

1. **Indistinguishable from random** to any party holding only one component. `K_a ⊕ K_b` with unknown `K_b` is a one-time pad over `K_a` — it reveals nothing about `K_window`. Similarly, known `K_b` with unknown `K_a` reveals nothing.

2. **Cryptographically independent bounds.** The lower bound (`K_a` locked by drand) and the upper bound (`K_b` deletable by custodians) act on separate halves of the XOR. An adversary who defeats one bound gains nothing without also defeating the other. Breaking drand's lower bound (obtaining `K_a` early) does not help without `K_b`. Breaking the upper bound (custodian fails to delete `s_i`) does not help before drand publishes `K_a`.

3. **No online Heirlooms dependency for reconstruction.** Bob XORs two values on his own device in a single instruction. There is no interactive protocol with Heirlooms at the moment of decryption — the server's role ends when it delivers `K_a_sealed` and brokers custodian share release.

4. **Quantum robustness of the expiry guarantee.** A future quantum computer that breaks BLS12-381 (drand's scheme) would allow early recovery of `K_a`. But this only breaks the *lower* bound. If the custodians have already deleted their shares at `w2`, the expiry guarantee survives: `K_a` is known, but `K_b` is gone, and `K_window` remains uncomputable. The XOR ensures a quantum break of the time-lock does not collapse the expiry.

---

## Summary: what each bound is and who enforces it

| Bound | Enforced by | Trust model |
|---|---|---|
| **Lower (w1)** | drand — distributed IBE, League of Entropy | Threshold-honest across independent institutions; no single point of failure |
| **Upper (w2)** | Custodians — Shamir share deletion | Threshold-honest: requires ≥ 2 of 3 custodians to delete |

The lower bound is *trustless in the classical sense* — it cannot be breached without corrupting a threshold of independent international institutions simultaneously. The upper bound is *threshold-honest* — a known limitation of classical cryptography; genuinely trustless upper bounds require quantum infrastructure (certified quantum deletion) which does not yet exist in production.

---

## Known limitation: receiver who decrypted within the window

A recipient who opened the capsule during `[w1, w2]` holds `DEK` (and potentially the plaintext) on their device. The expiry protocol does not and cannot retroactively revoke content already in the recipient's possession. This is a fundamental property of any E2EE system: once plaintext has been decrypted by a legitimate receiver, it is under their control. This is documented as intended behaviour, not a defect.

### Can this be prevented?

Not fully — but the scope of the limitation is worth stating precisely.

The irreducible barrier is the **analog hole**: any system in which Bob can legitimately view content necessarily grants him the ability to retain it, whether by screenshotting, screen recording, or pointing a second device at the display. No cryptographic scheme can revoke information that has already entered a recipient's sensory system or been captured by any means outside the software stack.

There is, however, a meaningful partial mitigation: the application layer need never hold `DEK` as an extractable value. Decryption could be confined to a hardware-backed trusted execution environment — Apple's Secure Enclave or Android's StrongBox Keystore — with the key marked non-exportable and access-controlled. The rendered output reaches the display; `DEK` never reaches application memory.

This closes a different but real attack surface:

- Malware on Bob's device after `w2` cannot extract `DEK` from storage or memory.
- A stolen or seized device after `w2` cannot be used to programmatically re-decrypt.
- Content that Bob did not actively choose to save is genuinely gone from the system.

It does not prevent Bob from deliberately screenshotting during the window, or recording his screen with a second device. This is the same boundary reached by hardware-backed DRM systems (e.g. Widevine L1, Apple FairPlay) used in commercial video streaming — they make bulk programmatic extraction hard without closing the analog hole.

**The correct framing of the expiry guarantee** is therefore:

> The capsule does not persist in a decryptable form beyond `w2` — it is inaccessible to third parties, to Heirlooms, and to a future Bob who did not actively choose to retain it. A recipient who deliberately saved the content during the window is outside the scope of the cryptographic guarantee, in the same way that a user who prints a bank statement is outside the scope of a bank's data-retention policy.

This framing is honest, defensible, and consistent with how analogous limitations are described in the DRM and secure messaging literature.

---

## Relation to published academic work

The construction is a practical instantiation of the **Timed Secret Sharing (TSS)** framework of Kavousi, Abadi, and Jovanovic (ASIACRYPT 2024), which formally proves that constructions with both lower and upper time bounds are achievable. Heirlooms' contribution is the specific combination:

- **drand/tlock** as the lower bound (the TSS paper uses VDFs/time-lock puzzles)
- **XOR blinding** between the two key components (not present in TSS)
- **Threshold custodian deletion** as the upper bound mechanism
- Integration within a **versioned consumer envelope format** across multiple client platforms

The XOR blinding scheme — splitting the window key so that neither the tlock half nor the Shamir half alone has any cryptographic value — is the novel structural element not found in TSS or any prior published work identified in the literature review (RES-002).