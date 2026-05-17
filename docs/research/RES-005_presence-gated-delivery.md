# RES-005 — Presence-Gated Post-Window Delivery: Construction and Analysis

**ID:** RES-005  
**Date:** 2026-05-17  
**Author:** Research Manager / Security Manager  
**Audience:** CTO, Technical Architect, Legal (patentability note below)  
**Status:** Draft — awaiting CTO review  
**Depends on:** RES-002 (window capsule construction), ARCH-003 (M11 capsule crypto)

---

## Executive Summary

This brief defines two related constructions, both using a time window [w1, w2] as a presence gate rather than a content delivery gate.

**§1 — Presence-gated delivery.** Named recipients who prove they opened the capsule during the window are cryptographically qualified to receive the real content after it closes. Non-attendees are permanently excluded. The server never learns the content key. The core mechanism is a per-recipient ticket scheme embedded inside the window-encrypted layer, combined with server-side XOR delivery masks.

**§3 — Count-conditional trigger.** The observed count of presence proofs at w2 determines whether a separate target capsule is released — to a fixed recipient who may be entirely different from the presence-gate recipients. The condition is a predicate on the count: zero openers, at least N, fewer than N, etc. Multiple conditions and multiple target capsules are composable, giving a branching structure where the outcome of the window determines which downstream capsules unlock. The server evaluates the predicate and delivers a masked key; it never learns the target capsule's content key.

No new cryptographic primitive is required by either construction. Both use AES-256-GCM, ECDH key wrapping, SHA-256 hash commitments, and the existing envelope format. The novel element in each case is architectural: using a window capsule as a **qualifying gate** or **trigger oracle** rather than a delivery vehicle.

**Key properties (§1):**

1. Before w1: no recipient can open the capsule or see the content.
2. During [w1, w2]: named recipients can prove presence; content remains encrypted under a second layer not visible during the window.
3. After w2: only recipients who proved presence receive the content key. Non-attendees are permanently excluded. The server never learns the content key.
4. The content is never visible during the proving phase — separating access proving from content delivery eliminates the opportunity for passive interception during the window.

**Key properties (§3):**

1. The server observes only a proof count — not who opened or what the content is.
2. The predicate (e.g. `count == 0`) is set by Alice at sealing time and cannot be changed.
3. If the predicate is met, a masked key is delivered to the target capsule's recipient; otherwise it is destroyed. The server never holds the target capsule's content key in plaintext.
4. Multiple predicates and multiple target capsules are independently composable.

**Relationship to prior work:** No published construction found that combines a time-lock window gate with per-recipient presence tickets, XOR delivery masks, or count-conditional trigger release. See §Literature below.

**Patentability:** Both constructions are likely patentable, and their combination strengthens the claim. Recommend routing to Legal before public disclosure.

---

## Motivation

The base window capsule construction (RES-002) guarantees:

- Nobody can open before w1 (tlock lower bound).
- Nobody can open after w2 (Shamir deletion upper bound).
- The content key is irrecoverable after w2.

But every named recipient who opens during [w1, w2] gets the content equally, and gets it immediately when the window opens. There is no discrimination between recipients based on presence, and the content is accessible as soon as K_a is published by drand.

The present construction addresses a different requirement: **the sender wants to reward presence, not merely define a window**. Use cases include:

- A message to multiple family members where only those who "checked in" during a defined window receive the full content. Those who were unreachable or disengaged are excluded.
- A posthumous instruction where the content is revealed only to those executors who actively participated in the opening ceremony.
- Any scenario where the sender wants to differentiate between recipients based on demonstrated engagement, enforced by cryptography rather than by social convention.

A secondary benefit is that content is not exposed during the window at all — reducing the surface for passive interception or screenshot retention by unintended observers.

---

## Construction Specification

### Participants

| Party | Role |
|---|---|
| **Alice (sender)** | Creates and seals the capsule |
| **Bob, Charlie (recipients)** | Named addressees; may or may not prove presence |
| **Heirlooms server** | Stores encrypted material; verifies presence proofs; delivers winner packages at w2 |
| **Custodians C1, C2, C3** | Hold Shamir shares of K_b (the window upper-bound key); delete at w2 |
| **drand** | Publishes round R_w1 at w1, revealing K_a |

### Key hierarchy

```
Real content
  └── encrypted by K_content          (256-bit, AES-256-GCM)
        └── K_content delivered via:
              winner i:  (K_content XOR K_ticket_i)  XOR  K_ticket_i  =  K_content

Window layer (gate only — contains tickets, not content)
  └── encrypted by K_window           (256-bit, AES-256-GCM)
        └── K_window = K_a XOR K_b
              K_a  = tlock-locked random value, revealed by drand at R_w1
              K_b  = Shamir secret, deleted by custodians at w2

Per-recipient presence tickets (inside window layer):
  K_ticket_bob     — 32 random bytes, specific to Bob
  K_ticket_charlie — 32 random bytes, specific to Charlie
```

The window layer contains **only** tickets — not the real content. The real content is in a separate envelope, encrypted under `K_content`, which is never distributed during the window.

### Phase 0 — Alice seals (at T_seal, before w1)

**Step 1 — Generate keys**

```
K_content       = random 256 bits   (the real content key)
K_a             = random 256 bits   (will be tlock-sealed)
K_b             = random 256 bits   (will be Shamir-split)
K_window        = K_a XOR K_b
K_ticket_bob    = random 256 bits
K_ticket_charlie = random 256 bits
```

**Step 2 — Encrypt real content**

```
C_content = AES-256-GCM(key=K_content, plaintext=real_content)
```

**Step 3 — Build window layer (tickets only)**

Alice constructs a window payload containing the presence tickets for each recipient:

```
window_payload = { "bob": K_ticket_bob, "charlie": K_ticket_charlie }
C_window = AES-256-GCM(key=K_window, plaintext=window_payload)
```

**Step 4 — Seal K_a via tlock**

```
K_a_sealed = tlock_encrypt(plaintext=K_a, round=R_w1)
```

**Step 5 — Shamir-split K_b**

```
[s1, s2, s3] = Shamir_split(secret=K_b, threshold=2, total=3)
s1_sealed = ECDH_wrap(C1_pubkey, s1)
s2_sealed = ECDH_wrap(C2_pubkey, s2)
s3_sealed = ECDH_wrap(C3_pubkey, s3)
```

Custodians publish `Commit(si) = H(si || salt_i)` to the audit log.

**Step 6 — Compute server-side delivery masks**

```
mask_bob     = K_content XOR K_ticket_bob
mask_charlie = K_content XOR K_ticket_charlie
```

**Step 7 — Upload to server**

Alice sends:

```
{
  C_content,                    // real content ciphertext
  C_window,                     // window layer (tickets inside)
  K_a_sealed,                   // tlock ciphertext
  R_w1, w1, w2,                 // timing parameters
  custodian_ids,
  presence_proofs: {
    bob:     H(K_ticket_bob),   // hash commitment for proof verification
    charlie: H(K_ticket_charlie)
  },
  delivery_masks: {
    bob:     mask_bob,          // K_content XOR K_ticket_bob
    charlie: mask_charlie       // K_content XOR K_ticket_charlie
  }
}
```

The server stores all of the above. It never holds `K_content`, `K_a`, `K_b`, `K_window`, or any `K_ticket_i` in plaintext.

### Phase 1 — Before w1

`C_window` is withheld by the server (consistent with the base window capsule model). No recipient has `K_a` (drand has not published R_w1). Even with all custodian shares reconstructing `K_b`, `K_window = K_a XOR K_b` cannot be computed. `K_content` is not held by anyone other than Alice's device at sealing time.

### Phase 2 — During [w1, w2]: presence proving

When drand publishes round R_w1, `K_a` becomes available. The server releases `C_window` to named recipients.

**Bob opens the window:**

```
K_window        = K_a XOR K_b              (Bob reconstructs: tlock + custodian paths)
window_payload  = AES-256-GCM-decrypt(key=K_window, ciphertext=C_window)
K_ticket_bob    = window_payload["bob"]
```

**Bob sends a presence proof:**

```
proof = H(K_ticket_bob)
POST /api/capsules/:id/presence  { "proof": proof }
```

Server verifies `proof == H(K_ticket_bob)` (stored at sealing time). On match, marks Bob as a winner.

`C_content` is not released during this phase. Bob has `K_ticket_bob` but has no component of `K_content`.

### Phase 3 — At w2: Shamir deletion and winner delivery

Custodians delete shares and publish deletion certificates. The server simultaneously delivers winner packages.

**Bob receives his delivery mask:**

```
mask_bob = K_content XOR K_ticket_bob    (server sends this to Bob at w2)
```

**Bob reconstructs K_content:**

```
K_content = mask_bob XOR K_ticket_bob
```

**Bob decrypts real content:**

```
real_content = AES-256-GCM-decrypt(key=K_content, ciphertext=C_content)
```

Charlie, who did not prove presence, receives no delivery mask. `mask_charlie` is discarded by the server at w2.

### Phase 4 — After w2

| Value | Status |
|---|---|
| `K_a` | Permanently public (drand published it at w1) |
| `K_b` | Permanently gone (Shamir shares deleted) |
| `K_window` | Uncomputable — `K_b` is gone |
| `K_ticket_bob` | Held by Bob (he derived it during the window) |
| `K_ticket_charlie` | Gone — Charlie never decrypted the window layer |
| `mask_charlie` | Discarded by server — no use without `K_ticket_charlie` |
| `K_content` | Held only by winners who XOR'd their ticket |
| `C_content` | On server — permanently inaccessible to non-winners |

---

## Security Properties

**Property 1 — Lower bound (w1) is trustless.**  
Identical to the base window capsule. `K_a` cannot be computed before drand publishes R_w1. Even holding all Shamir shares (knowing `K_b`) does not give `K_window` without `K_a`.

**Property 2 — Upper bound (w2) is threshold-honest.**  
Identical to the base window capsule. After custodians delete their shares, no new party can open the window layer and obtain a `K_ticket_i`. Non-winners who did not prove presence are permanently excluded.

**Property 3 — Server never knows K_content.**  
The server holds `mask_i = K_content XOR K_ticket_i` for each recipient. To compute `K_content` from `mask_i`, the server would need `K_ticket_i`. The server only holds `H(K_ticket_i)` (a one-way hash). Recovering `K_ticket_i` from its hash is a preimage attack on SHA-256 — computationally infeasible.

**Property 4 — Content is not visible during the proving phase.**  
`C_content` is not released during [w1, w2]. Recipients open the window layer (which contains only tickets) and submit presence proofs. The real content is delivered only after w2, to winners only. This removes the opportunity for passive observation of content during the window.

**Property 5 — Non-transferability of cryptographic access.**  
`mask_i` is only useful to the holder of `K_ticket_i`. If Bob shares `mask_bob` with Charlie, Charlie still needs `K_ticket_bob` to compute `K_content`. Bob would have to share `K_ticket_bob` itself (not just the mask) — at which point he has chosen to share his decryption capability, which is outside the scope of any E2EE scheme.

**Property 6 — Presence proof cannot be forged by non-openers.**  
A party who did not open the window layer cannot produce `H(K_ticket_i)` without first computing `K_ticket_i`, which requires decrypting `C_window` under `K_window`. The proof is existentially unforgeable against anyone who lacks `K_window`.

**Note on collusion between recipients:**  
Bob and Charlie both have access to `C_window` if they are named recipients. Bob could share `K_ticket_charlie` with Charlie after deriving the full window payload, allowing Charlie to submit a presence proof without independently opening the capsule. This is a social-layer collusion — both parties would need to cooperate. The cryptographic scheme cannot prevent it, in the same way it cannot prevent Bob from sharing his decrypted content. This is documented as a known limitation.

---

## The No-Winner Case

If no named recipient proves presence during [w1, w2], the delivery masks are discarded at w2 and `K_content` becomes permanently irrecoverable. The content capsule is sealed forever.

This may be a desired property — the capsule self-destructs if no one attends — or an unacceptable risk depending on the sender's intent.

**Mitigation options:**

1. Alice includes herself as a named recipient, ensuring at least one winner.
2. A configurable minimum-winner threshold: if fewer than M recipients prove presence, the server falls back to releasing `K_content` to all named recipients (treating the presence gate as advisory).
3. A fallback envelope: Alice also seals a conventional (non-presence-gated) capsule with the same content, delivered unconditionally at w2 or later.

The choice between these is a product and UX decision, not a cryptographic one. The brief does not prescribe a default.

---

## §3 — Count-Conditional Trigger: Releasing a Target Capsule Based on Presence Count

### Motivation

The §1 construction awards content to those who showed up. A complementary requirement is: **let the count of attendees determine what happens to a separate, downstream capsule**. Examples:

- *"If nobody opens this capsule during the window, release my letter to my solicitor."* (Dead man's switch — the unread-letter trigger.)
- *"If at least three of my five children open this capsule, release the accompanying family document to all of them."* (Quorum-release.)
- *"If fewer than half open it, release a fallback capsule to my estate executor."* (Engagement-failure fallback.)

In all cases the server must evaluate the condition honestly but never learn the content key of the target capsule. The condition and the masked key are set at sealing time by Alice; the server is a blind evaluator.

### Participants

| Party | Role |
|---|---|
| **Alice** | Creates capsule A (presence gate) and capsule B (target) at sealing time |
| **Bob, Charlie** | Named recipients of capsule A — their presence count triggers the condition |
| **Carol** | Recipient of capsule B — fixed at sealing time, may differ from A's recipients |
| **Heirlooms server** | Counts presence proofs; evaluates predicate; delivers masked key if met; destroys it if not |
| **Custodians, drand** | As in §1 — gate the window layer of capsule A |

### Key structure

```
Capsule A (presence gate — identical to §1):
  window layer encrypted by K_window = K_a XOR K_b
  window payload contains K_ticket_bob, K_ticket_charlie (presence tickets)

Capsule B (target capsule):
  C_B         = AES-256-GCM(key=DEK_B, plaintext=content_B)
  DEK_B       — Alice's content key; never sent to server in plaintext
  K_blind_carol — 32 random bytes; ECDH-wrapped to Carol's sharing pubkey at sealing time
  mask_B      = DEK_B XOR K_blind_carol   — stored on server; released conditionally
```

Carol holds `K_blind_carol` from sealing time. It is useless without `mask_B`. The server holds `mask_B` but not `K_blind_carol` or `DEK_B`.

### Phase 0 — Alice seals both capsules (at T_seal)

Alice generates and performs all the §1 sealing steps for capsule A, then additionally:

```
DEK_B          = random 256 bits
K_blind_carol  = random 256 bits
mask_B         = DEK_B XOR K_blind_carol
C_B            = AES-256-GCM(key=DEK_B, plaintext=content_B)
```

Alice ECDH-wraps `K_blind_carol` to Carol's sharing pubkey and sends it directly — Carol stores it, not the server.

Alice sends the server:

```
{
  // Capsule A fields (as in §1)
  ...

  // Capsule B trigger
  trigger: {
    predicate:    "count == 0",          // or ">= 2", "< 3", etc.
    target_capsule_id: <uuid of B>,
    recipient_id: carol,
    mask_B:       DEK_B XOR K_blind_carol
  },

  // Capsule B ciphertext (released to Carol unconditionally — it's encrypted)
  C_B
}
```

`C_B` can be stored and served to Carol at any time — it is encrypted and the server cannot read it. Only `mask_B` is withheld.

### Phase 1 and 2 — Before and during [w1, w2]

Identical to §1. Bob and/or Charlie may prove presence. The server accumulates a count. Carol holds `K_blind_carol` but has no component of `DEK_B` and cannot decrypt `C_B`.

### Phase 3 — At w2: predicate evaluation and conditional delivery

The server counts presence proofs received during [w1, w2]. It evaluates the predicate Alice specified:

**If predicate is TRUE (e.g. count == 0 and no one proved presence):**

```
Server sends Carol: mask_B = DEK_B XOR K_blind_carol

Carol computes:  DEK_B = mask_B XOR K_blind_carol
Carol decrypts:  content_B = AES-256-GCM-decrypt(key=DEK_B, ciphertext=C_B)
```

**If predicate is FALSE (e.g. count == 0 but someone proved presence):**

```
Server destroys mask_B.
Carol never receives it.
C_B remains permanently inaccessible.
```

In neither case does the server compute or hold `DEK_B`. It holds only `mask_B = DEK_B XOR K_blind_carol`, and `K_blind_carol` is held only by Carol.

### Supported predicates

The predicate is stored as a policy record at sealing time. The server evaluates it against the **proof set** at w2 — the set of recipient identities who submitted valid presence proofs. Because each proof is `H(K_ticket_i)` and `K_ticket_i` is specific to recipient i, the server can unambiguously identify which named recipient submitted each proof. The predicate therefore has access to both the count and the identity of every winner.

**Count-only predicates** (evaluated against `|proof_set|`):

| Predicate | Meaning |
|---|---|
| `count == 0` | Nobody opened A — dead man's switch |
| `count >= N` | At least N opened — quorum release |
| `count < N` | Fewer than N opened — failure-to-engage fallback |
| `count == M` | All named recipients opened — unanimity required |

**Named-recipient predicates** (evaluated against membership of `proof_set`):

| Predicate | Meaning |
|---|---|
| `bob IN proof_set` | Bob specifically must have opened |
| `bob NOT IN proof_set` | Fires only if Bob did not open |
| `{bob, charlie} ⊆ proof_set` | Both Bob and Charlie must have opened |

**Composite predicates** (combining count and named requirements):

| Predicate | Meaning |
|---|---|
| `bob IN proof_set AND count >= 3` | Bob plus at least 2 others |
| `bob IN proof_set AND count == M` | Bob and everyone else (full attendance + named) |
| `{bob, charlie} ⊆ proof_set AND count >= 2` | Both named recipients opened (sufficient on their own) |
| `bob IN proof_set OR count >= 4` | Either Bob specifically, or a large enough crowd without him |

The policy is stored as a structured record at sealing time rather than as a free-form expression, to keep server-side evaluation simple and auditable. A minimal v1 policy structure covers the most useful cases:

```json
{
  "required_ids": ["bob_id"],
  "min_count": 3
}
```

This expresses "Bob must be in the proof set AND total count must be ≥ 3." Both fields are optional: omitting `required_ids` gives a count-only predicate; omitting `min_count` gives a named-only predicate. More expressive policies (disjunctions, exclusions, thresholds over named subsets) can be added in later versions without changing the underlying cryptographic mechanism — the predicate is always evaluated against the same proof set.

### Multiple predicates and branching

Alice may register multiple triggers against the same capsule A, with different predicates and different target capsules:

```
trigger_1: predicate "count == 0"   → release capsule B to Carol
trigger_2: predicate "count >= 2"   → release capsule C to David
```

The server evaluates all predicates independently at w2. Predicates may overlap (both could be true, or neither). Each target capsule has its own `mask` and its own recipient. The branching logic is a set of independent conditional deliveries, not a mutually exclusive switch — Alice is responsible for designing predicates that do not produce unintended combinations.

A mutually exclusive branch structure (if-else) requires Alice to ensure predicates partition the outcome space: `count == 0` and `count >= 1` are exhaustive and non-overlapping, giving a clean binary branch.

### The dead man's switch in detail

The `count == 0` case deserves explicit treatment because it is the most distinctive instance and the most likely to appear in posthumous use cases.

Alice seals capsule A addressed to her family. The window [w1, w2] corresponds to a period she expects them to check in (e.g., an annual date). If the window closes with no presence proofs, the server releases capsule B to her solicitor — a fallback instruction, a legal document, a contingency message.

The cryptographic properties:

- Before w1: neither Carol nor the solicitor can access B. The tlock lower bound holds.
- During [w1, w2]: if anyone proves presence, `mask_B` will be destroyed at w2. The family's engagement has voided the trigger.
- After w2: if no one proved presence, Carol (the solicitor) receives `mask_B` and can decrypt B. The server has confirmed the condition was met without learning what B contains.

This is a cryptographically enforced contingency capsule. The server acts as a blind witness: it observed that the condition was met and delivered the appropriate key material, without ever being able to read the contingency content.

### Security properties

**Server blindness.** The server holds `mask_B = DEK_B XOR K_blind_carol`. Without `K_blind_carol` (held only by Carol, ECDH-wrapped), the server cannot compute `DEK_B`. This is the same one-time-pad property as the §1 delivery masks.

**Predicate integrity.** The predicate is set at sealing time and stored on the server. The server cannot alter it without Alice's involvement — the predicate record is part of the capsule's sealed state. A dishonest server could miscount presence proofs (deliver `mask_B` when the predicate is false, or withhold it when true). This is the same trust assumption as the base window capsule: the server is honest-but-curious, not Byzantine. A Byzantine-resistant predicate evaluation would require on-chain verification (smart contract) or MPC — noted as a future direction.

**Carol's blind is independent of capsule A.** `K_blind_carol` is ECDH-wrapped to Carol's key and sent directly — it does not flow through capsule A's window layer. Carol has no relationship with the presence-gate recipients (Bob, Charlie) at the cryptographic level. The presence-gate and the target capsule are linked only by the server's predicate record.

**Non-interference between branches.** Each trigger has its own `mask` value. Delivery of one target capsule does not affect the keys or delivery of another.

### Connection to Chained Capsules (RES-004)

RES-004 describes chained capsules as a DAG: unlocking C₁ is a precondition for C₂ becoming available. The present construction is a special case of this structure in which:

- **C₁** is the window layer (presence gate).
- **C₂** is the content layer (the real message).
- The "unlock" event for C₁ is not solving a puzzle but proving presence within the time window.
- The C₁ → C₂ link is the delivery mask `K_content XOR K_ticket_i`, which is computed at sealing time and held by the server until w2.

The key difference from the general chained capsule model is that the C₁ → C₂ link here is **non-transferable**: the link is per-recipient (each winner gets a different mask), and usability of the mask requires the recipient's own ticket. In the general chained capsule model, the C₂ access credential could in principle be passed between parties. The presence-gated construction makes the link cryptographically bound to the recipient's identity.

---

## Literature Assessment

### Conditional / Timed Oblivious Transfer

Oblivious Transfer (OT) protocols allow a sender to transfer one of N secrets to a receiver, where the sender does not learn which secret was transferred and the receiver learns only the chosen one. **Timed OT** (Garay and Jakobsson, CT-RSA 2003) adds a time gate: the receiver can only choose within a time window. The present construction resembles a selective timed OT where the "choice" is whether to show up (presence) rather than which item to select.

Critical difference: standard OT is 1-of-N (receiver gets exactly one); the present construction is M-of-N (all winners get the same content key `K_content` via independent paths). OT does not model winner selection based on demonstrated presence, nor does it use a deletion-based upper bound.

### Timed Commitment Schemes

Boneh and Naor (CRYPTO 2000) defined timed commitments: a party commits to a value and the commitment can be forced open after a fixed time via a time-lock puzzle. This is structurally related — the presence proof is a form of commitment by the recipient — but the forced-open property is not relevant here (the server does not force open anything; winners self-select).

### Attribute-Based Encryption with Temporal Attributes

CP-ABE schemes with time attributes can express "decrypt if the current time is in [T1, T2] AND you have attribute X." The presence-gated construction can be understood as CP-ABE where the required attribute is "demonstrated presence during [w1, w2]." However, CP-ABE requires a trusted attribute authority to issue decryption keys. The present construction replaces the attribute authority with the ticket mechanism: the window layer itself acts as the attribute-issuance ceremony, and the server acts as the policy enforcer only for the presence proof, not as a decryption authority.

### No direct prior art found

No published construction found that combines:

- A time-lock window layer (tlock lower bound + Shamir deletion upper bound) as a presence gate
- Per-recipient presence tickets embedded inside the window-encrypted layer
- Server-side XOR delivery masks that preserve content-key secrecy from the server
- Post-window delivery to winners only

The combination is novel as a complete system.

---

## Patentability Assessment

Both constructions in this brief are likely patentable, and their combination strengthens the overall claim surface.

**§1 (presence-gated delivery):**
1. Using a window capsule as a presence gate rather than a delivery vehicle is a specific novel application not anticipated by any prior work reviewed.
2. The per-recipient ticket mechanism, XOR delivery masks, and hash-commitment presence proof are a specific combination with no close prior art.
3. The self-destruction property (content permanently inaccessible if no one attends) has potential applicability in legal, financial, and archival contexts beyond Heirlooms.

**§3 (count-conditional trigger):**
1. Using a presence-proof count as a cryptographic trigger for releasing a separate target capsule — without the server learning the target's content key — is not found in any prior work reviewed.
2. The dead man's switch instantiation (`count == 0` → release fallback capsule) is a distinctive and immediately commercialisable application.
3. The branching structure (multiple predicates, multiple target capsules, independent evaluation) is a further specific combination not found in the literature.

**Combined claim:** The full construction — window capsule as presence gate, per-recipient tickets, count-conditional branching, XOR-masked target key delivery, server blindness throughout — is a layered novel system. Each layer cites the one below as a dependent claim, building from RES-002 upward.

**Risk:** The conditional key delivery mechanism could face an obviousness challenge over secret sharing or conditional OT. Recommend narrow claims on the specific time-window + presence-count + XOR-mask combination, not broad claims on "conditional encryption."

**Action:** Route to Legal before any public disclosure. The repo must remain private until a UK patent filing covers RES-002, RES-005 §1, and RES-005 §3 as a layered claim set.

---

## Open Questions for CTO

1. **No-winner handling (§1):** Should the system have a configurable fallback (unconditional release if zero winners), or is the self-destruction property always desirable? This is a product decision with legal implications for posthumous use cases. Note that §3 now provides a clean cryptographic answer: Alice can register a `count == 0` trigger pointing to a fallback capsule, making self-destruction the default and the fallback an explicit opt-in.

2. **Presence privacy:** The proof `H(K_ticket_i)` reveals to the server that recipient i showed up, and the exact time. Is this acceptable? A zero-knowledge proof of hash preimage would allow proving presence without timing correlation, but adds significant complexity.

3. **Window payload privacy:** All named recipients can see the full window payload (including tickets for other recipients) once the window opens. A recipient could share another recipient's ticket, allowing collusion. Does this matter for the intended use cases?

4. **Predicate integrity (§3):** The server is trusted to evaluate the predicate honestly. A dishonest server could miscount and deliver `mask_B` when the predicate is false. Is this acceptable under the existing server-trust model, or should on-chain predicate evaluation be designed in from the start?

5. **Overlapping predicates (§3):** Alice is responsible for ensuring predicates do not produce unintended combinations. Should the sealing API validate that predicates on the same capsule are non-overlapping, or leave this to the sender?

6. **Custodian model:** Should the same custodians who hold Shamir shares of K_b also hold delivery masks? Or should delivery mask management be a separate server responsibility? Separating them reduces the blast radius of a custodian compromise.

---

## PA Summary

**For:** CTO and Technical Architect  
**Urgency:** Low — novel constructions, no implementation dependency yet. Route to Legal before any external disclosure.

**Key finding (§1):** The presence-gated delivery construction is a clean, implementable extension of the RES-002 window capsule that uses the window as a qualifying ceremony rather than a content delivery mechanism. No new cryptographic primitive is required. Real content is never visible during the window, which partially addresses the retained-plaintext limitation noted in PAP-002.

**Key finding (§3):** The count-conditional trigger extends §1 by making the server a blind predicate evaluator: it counts presence proofs and delivers a masked content key to a fixed downstream recipient if the condition is met, without ever learning the target capsule's content key. The dead man's switch instantiation (`count == 0` → release fallback capsule) is immediately applicable to posthumous use cases and resolves the no-winner self-destruction question from §1 by making the fallback an explicit Alice-configured trigger rather than a system default.

**Combined patent surface:** RES-002 (window capsule) → RES-005 §1 (presence gate) → RES-005 §3 (count-conditional branching) form a layered claim set of increasing specificity. Each layer is novel. The combination is substantially stronger than any individual component. Route all three to Legal as a single filing package.

**Decisions needed from CTO:**

1. **Predicate integrity:** Accept server-honest-but-curious trust model for predicate evaluation, or design for on-chain verification from the start?
2. **Overlapping predicates:** Should the sealing API enforce non-overlapping predicates, or leave it to the sender?
3. **Route to Legal:** Confirm the repo stays private and engage patent attorney before any external disclosure. Priority: the combined RES-002 + RES-005 claim set should be filed as a single application.
