---
id: TAU-001
title: Capsule construction guide — formal notation, key exchange diagrams, print visualisation, Manim scripts
category: Documentation
priority: High
status: done
depends_on: [ARCH-003, ARCH-005, ARCH-006, ARCH-007, ARCH-008, ARCH-010]
touches:
  - docs/notation/
  - docs/papers/
  - docs/presentations/
assigned_to: TechnicalAuthor
estimated: half day
completed: 2026-05-16
---

## Goal

Produce a layered technical guide to how Heirlooms capsules work, covering all
key and secret exchange mechanisms in the system. The guide has two layers:

1. **Public narrative layer** — accessible to technically sophisticated non-implementers
   (researchers, potential collaborators, press). Will incorporate framing from PHI-002.
2. **Technical appendix** — formal mathematical notation, full sequence diagrams,
   implementation details.

Additionally: investigate and recommend novel visualisation approaches for print
material, and produce Manim starter scripts for the core cryptographic animations.

## Source material (read these before writing)

All briefs live under `docs/briefs/` unless noted:

- `docs/envelope_format.md` — canonical E2EE envelope spec (read first)
- `ARCH-003_m11-capsule-crypto-brief.md` — M11 capsule crypto design
- `ARCH-004_connections-data-model.md` — identity layer
- `ARCH-005_envelope-format-amendment.md` — envelope format amendment (new algo IDs)
- `ARCH-006_tlock-provider-interface.md` — TimeLock provider; XOR DEK blinding scheme; iOS compatibility
- `ARCH-007_e2ee-tag-scheme.md` — HMAC tag tokens
- `ARCH-008_chained-capsule-and-care-mode-feasibility.md` — chained capsules, Care Mode
- `ARCH-010_m11-api-surface-and-migration-sequencing.md` — full API surface and sequencing

## Deliverable 1 — Formal notation document

Output: `docs/notation/NOT-001_capsule-construction.md`

## Deliverable 2 — Layered technical guide

Output: `docs/papers/PAP-001_capsule-construction-guide.md`

## Deliverable 3 — Sequence diagrams

Output: `docs/notation/NOT-001_sequence-diagrams.md`

## Deliverable 4 — Print visualisation brief

Output: `docs/presentations/PRE-001_capsule-key-ceremony.md`

## Deliverable 5 — Manim scripts

Output: `docs/presentations/manim/`

## Completion notes

**Completed:** 2026-05-16  
**Author:** TechnicalAuthor  

### Deliverables produced

| # | Output path | Status |
|---|---|---|
| 1 | `docs/notation/NOT-001_capsule-construction.md` | Complete |
| 2 | `docs/papers/PAP-001_capsule-construction-guide.md` | Complete |
| 3 | `docs/notation/NOT-001_sequence-diagrams.md` | Complete |
| 4 | `docs/presentations/PRE-001_capsule-key-ceremony.md` (§Print Visualisation) | Complete |
| 5a | `docs/presentations/manim/key_hierarchy.py` | Complete |
| 5b | `docs/presentations/manim/capsule_seal.py` | Complete |
| 5c | `docs/presentations/manim/capsule_delivery.py` | Complete |

### Decisions made

1. **PHI-002 placeholder:** Part 1 of PAP-001 contains a clearly marked `NARRATIVE_PLACEHOLDER` comment block. A 2-3 paragraph researcher-facing conceptual introduction is written alongside it so the document is readable now; the PHI-002 narrative will replace or augment this when available.

2. **NOT-001 scope:** Formal LaTeX-style mathematical notation covers all constructions in the task brief: Argon2id master key derivation, iOS CryptoKit path, HKDF sub-key tree, HMAC tag tokens, per-file DEK generation and AES-256-GCM encryption, envelope format, plot key wrapping, capsule DEK blinding split (DEK = DEK_client XOR DEK_tlock), ECDH recipient wrapping (both iOS and Android/web paths), IBE sealing, delivery decryption, Shamir threshold construction, chained capsule key embedding, and Care Mode key ceremony.

3. **Sequence diagrams:** All seven diagrams specified in the task are in NOT-001-seq. Each annotates knowledge boundaries explicitly using `// [PARTY knows: ...]` comments within the Mermaid blocks. Server-blindness is stated as a named property in Diagram 4.

4. **Print visualisation:** Three approaches documented in PRE-001 §Print Visualisation. Primary recommendation is Circuit diagram with knowledge-boundary colouring (Approach 1); secondary is Timeline layout with coloured key bands (Approach 2). Approach 3 (topological trust diagram) is recommended for academic paper appendices.

5. **Manim scripts:** Three working Python scripts using Manim Community Edition conventions (Scene subclass, `construct` method). Render instructions included as top-of-file docstrings. Scripts prioritise cryptographic story clarity over visual polish: layout is functional, key material colour-coded consistently (green = DEK, blue = DEK_client, red = DEK_tlock, orange = IBE ciphertext, purple = drand material). Each script fades out cleanly at the end.

6. **ARCH-005 not in workspace:** ARCH-005 was not present in the agent workspace but all M11 algorithm IDs are documented directly in `docs/envelope_format.md` §Algorithm identifiers and in ARCH-003 §1. NOT-001 §7 captures the full algorithm ID table from those sources.

7. **ARCH-010 not in workspace:** ARCH-010 (API surface and migration sequencing) is an implementation coordination document; its content is referenced through ARCH-003 and ARCH-006 which were available. The sequencing details are not directly relevant to the notation or guide but are cited appropriately in the dependency chain.
