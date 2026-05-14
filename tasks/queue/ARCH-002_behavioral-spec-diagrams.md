---
id: ARCH-002
title: Behavioral spec diagrams — use case inventory + Mermaid sequences
category: Docs
priority: High
status: queued
depends_on: []
touches:
  - docs/specs/
assigned_to: TechnicalArchitect
estimated: 3–4 hours (agent, codebase reading + diagram writing)
---

## Goal

Produce a normative behavioral specification for every user-facing interaction in
the system: a use case inventory and Mermaid sequence diagrams organized by
category. These documents are the source of truth for expected behavior — Playwright
tests are derived from them, not the other way around. If the test suite is lost,
it is regenerated from these specs.

One markdown file per category lives in `docs/specs/`. Each file contains:
1. A short use case inventory (bullet list: actor + action + outcome)
2. Mermaid `sequenceDiagram` blocks — one per use case or flow variant

## Scope

Derive everything from the actual codebase (routes, services, crypto layer) — not
from briefs or roadmap docs, which may be ahead of or behind the code. Where the
code and briefs disagree, the code wins. Note discrepancies in the Completion notes.

**Diagram depth:** message-level (actual HTTP endpoints, component handoffs, crypto
operations) for complex flows; swim-lane style for simple reads. Specifically:
- **Message-level:** all Onboarding flows, all E2EE operations, Shared Plots key
  distribution, Flows/Trellises staging lifecycle, Sending & Receiving DEK re-wrap
- **Swim-lane:** Garden & Explore browsing, Uploading, simple Settings actions

## Categories and files

| File | Category | Key flows to cover |
|------|----------|--------------------|
| `docs/specs/onboarding.md` | Onboarding | Register, device setup, passphrase + key generation, QR device pairing, friend invite redemption |
| `docs/specs/garden-explore.md` | Garden & Explore | Garden load, Just Arrived, plot row, Explore filter + sort, photo detail, thumbnail |
| `docs/specs/uploading.md` | Uploading | Android share-sheet upload, web drag+drop, paste upload, upload progress |
| `docs/specs/plots.md` | Plots | Create/edit/delete private plot, criteria builder (all atom types), hidden plots |
| `docs/specs/flows-trellises.md` | Flows / Trellises | Create flow, auto-routing trigger, staging queue, approve/reject/un-reject, requires_staging=false bypass |
| `docs/specs/shared-plots.md` | Shared Plots | Create shared plot, invite friend, join via link (async pending_plot_key_requests), E2EE group key distribution, member item add, staging approval with DEK re-wrap |
| `docs/specs/sending-receiving.md` | Sending & Receiving | Share item to friend (DEK re-wrap), receive in Just Arrived, attribution display |
| `docs/specs/capsules.md` | Capsules | Create capsule, add items, seal, view sealed capsule (delivery is M12 — note as out of scope) |
| `docs/specs/settings-compost.md` | Settings & Compost | Passphrase change, compost item (eligibility check, 90-day window), account info |

## Key source files to read

- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/routes/` — all route definitions and request/response shapes
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/service/` — business logic
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/crypto/` — E2EE operations
- `HeirloomsWeb/src/` — web client flows and crypto (`vaultCrypto.js`)
- `HeirloomsiOS/Sources/HeirloomsCore/` — iOS client flows
- `HeirloomsApp/app/src/main/kotlin/digital/heirlooms/` — Android flows
- `docs/envelope_format.md` — E2EE envelope specification

## Acceptance criteria

- All 9 `docs/specs/*.md` files created.
- Every file has a use case inventory section followed by at least one Mermaid
  `sequenceDiagram` block per distinct flow.
- E2EE flows (onboarding key gen, device pairing, shared plot key distribution,
  DEK re-wrap on share/staging-approve) show the crypto operations explicitly —
  which key wraps which, what the server receives vs stores.
- All HTTP endpoints in the diagrams match routes that actually exist in the
  codebase (no invented paths).
- Mermaid syntax is valid — each block opens with `sequenceDiagram` and uses
  `participant`, `->>`, and `-->>` correctly.
- Any discrepancies found between code and briefs/roadmap are noted in
  Completion notes.

## Notes

- Create `docs/specs/` directory if it does not exist.
- These diagrams feed directly into TST-005 (Playwright setup) and TST-004
  (Playwright test writing). The Test Manager will use them as the canonical
  description of what each Playwright journey must verify.
- Capsule delivery (M12) and posthumous unlock (M13) are out of scope — note them
  as stubs in `capsules.md`.
- REF-001 (Flow → Trellis rename) is queued but not yet done — use "Flow/Trellis"
  in diagrams and note the pending rename.

## Completion notes

<!-- Agent appends here and moves file to tasks/done/ -->
