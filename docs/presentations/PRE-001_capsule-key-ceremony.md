# PRE-001 — Capsule Key Ceremony: Presentation and Visualisation Brief

**ID:** PRE-001  
**Date:** 2026-05-16  
**Author:** Technical Author  
**Status:** Draft  
**Relates to:** NOT-001, PAP-001, ARCH-003, ARCH-006, TAU-001  

---

## Purpose

This document collects materials supporting the external presentation of the Heirlooms capsule key ceremony — the cryptographic process by which a capsule is sealed with a time-lock, delivered, and opened. It is intended for use in:

- Investor and press briefings (accessible narrative)
- Academic conference presentations (technical depth)
- Whitepaper and technical report insets (print-quality figures)

The animated versions are in `docs/presentations/manim/`. The formal notation is in `docs/notation/NOT-001_capsule-construction.md`. The layered guide is `docs/papers/PAP-001_capsule-construction-guide.md`.

---

## Print Visualisation

### Goal

Produce one or two figures that, in a single printed panel, convey:

1. **The blinding split**: $\mathbf{DEK} = \mathbf{DEK}_{\text{client}} \oplus \mathbf{DEK}_{\text{tlock}}$. Two halves that must meet on the client to reconstruct the key.
2. **Server-blindness**: the server holds only $\mathbf{DEK}_{\text{tlock}}$ (one XOR half) and the IBE ciphertext of $\mathbf{DEK}_{\text{client}}$. It never possesses both halves simultaneously before delivery.
3. **The temporal gate**: the IBE-locked half ($\mathbf{DEK}_{\text{client}}$) is inaccessible until drand publishes the round key at $t_{\text{open}}$.

The audience is technically literate — PhD-level or senior engineer — but may not be familiar with IBE or Shamir schemes. Jargon should be labelled, not assumed.

---

### Approach 1 — Circuit diagram with knowledge-boundary colouring (Recommended — Primary)

**Concept:** Model the key ceremony as a circuit diagram. Keys are horizontal wires; operations are gates; party boundaries are coloured regions.

**Layout:**

- Three horizontal swim lanes (vertical partition): **Client (Seal time)** | **Server** | **Client (Delivery time)**
- The DEK wire enters on the left (Client, seal time), where it forks at an XOR gate into two daughter wires: $\mathbf{DEK}_{\text{client}}$ (upper, blue) and $\mathbf{DEK}_{\text{tlock}}$ (lower, red).
- The $\mathbf{DEK}_{\text{client}}$ wire enters an **IBE lock** icon (a padlock with a clock face) before crossing into the server lane — indicating it is sealed and the server cannot read it.
- The $\mathbf{DEK}_{\text{tlock}}$ wire crosses into the server lane as a red plaintext wire, labelled "server holds this half."
- On the delivery side (right), after $t_{\text{open}}$, the IBE lock opens: $\mathbf{DEK}_{\text{client}}$ emerges from the lock as a blue wire, crosses back to the Client lane, and meets the red $\mathbf{DEK}_{\text{tlock}}$ wire at a second XOR gate — producing the reconstructed $\mathbf{DEK}$.
- Party boundaries are coloured background regions: **blue** for Client, **grey** for Server.

**Why it works:** The XOR split is a familiar circuit primitive. The padlock-with-clock communicates "locked until time $t$" without requiring knowledge of IBE. The knowledge-boundary colouring makes the server's epistemic limitation immediately visible: the red wire exists in the server region but the blue wire (locked) does not.

**Recommended colour palette:** Client region — `#E8F4FD` (light blue); Server region — `#F5F5F5` (light grey). $\mathbf{DEK}_{\text{client}}$ wire — `#1A7DC0` (medium blue). $\mathbf{DEK}_{\text{tlock}}$ wire — `#C0392B` (medium red). Reconstructed DEK — `#2ECC71` (green, indicating success).

**Caption text:** "The blinding split. The sealing client divides $\mathbf{DEK}$ into two XOR halves. The server holds one half ($\mathbf{DEK}_{\text{tlock}}$, red). The other half ($\mathbf{DEK}_{\text{client}}$, blue) is sealed inside a time-lock and released by the drand beacon at $t_{\text{open}}$. The server can never reconstruct $\mathbf{DEK}$ alone."

---

### Approach 2 — Timeline layout with coloured key bands (Recommended — Secondary)

**Concept:** A temporal reading: the horizontal axis is time ($t_{\text{seal}} \to t_{\text{open}} \to t_{\text{delivered}}$); the vertical axis shows three parties (Author/Client, Server, drand Beacon). Key material flows as coloured bands along party lines.

**Layout:**

- Three horizontal party lanes, each extending across the time axis.
- At $t_{\text{seal}}$: Author lane — a wide green band labelled $\mathbf{DEK}$ splits into a blue band ($\mathbf{DEK}_{\text{client}}$) and a red band ($\mathbf{DEK}_{\text{tlock}}$). Blue band has a padlock icon applied immediately. Red band descends to the Server lane via a vertical arrow.
- Between $t_{\text{seal}}$ and $t_{\text{open}}$: Server lane holds a red band (opaque; it knows $\mathbf{DEK}_{\text{tlock}}$) and a locked-blue-band icon (it holds the IBE ciphertext but cannot open it).
- At $t_{\text{open}}$: drand Beacon lane emits a vertical flash (the published round key). This opens the padlock on the blue band in the Server lane.
- After $t_{\text{open}}$: The blue band travels from the Server lane to the Recipient/Client lane (delivered via `/tlock-key`). The red band also travels to the Client lane. At the recipient device, blue + red bands merge at an XOR icon to produce a green band: $\mathbf{DEK}$ — "content decrypted."
- Annotation boxes: above the server region between $t_{\text{seal}}$ and $t_{\text{open}}$, a label: "Server holds $\mathbf{DEK}_{\text{tlock}}$ but not $\mathbf{DEK}_{\text{client}}$ — cannot reconstruct $\mathbf{DEK}$."

**Why it works:** The temporal dimension makes the delivery narrative legible. The coloured bands communicate ownership and flow intuitively. The padlock icon on the blue band in the server region makes the IBE gate concrete without requiring a technical definition.

**Best suited for:** Whitepaper insets, investor decks, press briefings where the reader needs the story in a single glance.

---

### Approach 3 — Topological trust diagram (Secondary — Academic use)

**Concept:** A graph where nodes are parties (Client-at-seal, Server, drand, Client-at-delivery) and edges are labelled with what each party can and cannot derive. Pre-delivery and post-delivery are shown as two separate graph states side by side.

**Layout:**

**State 1 (pre-delivery, $t < t_{\text{open}}$):**

```
Client-seal ──(DEK_tlock)──► Server
Client-seal ──(IBE-enc(DEK_client))──► Server
Server       ──╳──► DEK          (cannot derive)
drand        ──╳──► DEK_client   (round key not yet published)
```

Edges that represent "cannot derive" use a red dashed style with an ✗ label.

**State 2 (post-delivery, $t \geq t_{\text{open}}$):**

```
Server        ──(DEK_tlock)──► Client-recv
drand         ──(sk_r)──► IBE-open ──(DEK_client)──► Client-recv
Client-recv   ──(XOR)──► DEK
Server        ──╳──► DEK   (still cannot; never had DEK_client in plaintext)
```

**Why it works:** Security researchers and cryptographers are comfortable with information-flow graph notation. The two-state layout makes the pre/post-delivery contrast explicit. The "cannot derive" edges make the server's epistemic limitations precise and verifiable.

**Limitation:** More abstract than Approaches 1 and 2; requires the reader to follow directed graph semantics. Less suitable for investor or press materials.

---

### Recommendation summary

| Priority | Approach | Best use | Primary message |
|---|---|---|---|
| 1 | Circuit diagram with knowledge-boundary colouring | Whitepaper figure, conference slide | XOR split mechanics; server region visible |
| 2 | Timeline layout with coloured key bands | Investor deck, press briefing, report inset | Temporal narrative; delivery story |
| 3 | Topological trust diagram | Academic paper, security audit brief | Information-flow precision; pre/post contrast |

For the standard Heirlooms whitepaper, **Approach 1 as the main figure** with **Approach 2 as an accompanying panel** is recommended. Approach 3 is available for the technical appendix of any academic paper draft.

---

*For animated versions of these concepts, see `docs/presentations/manim/capsule_seal.py` and `capsule_delivery.py`.*
