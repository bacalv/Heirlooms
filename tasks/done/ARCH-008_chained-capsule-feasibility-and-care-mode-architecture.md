---
id: ARCH-008
title: Chained capsule feasibility and Care Mode architecture assessment
category: Architecture
priority: Medium
status: done
assigned_to: TechnicalArchitect
depends_on: [RES-004]
estimated: 1 session
---

## Context

Two major new product directions were discussed in the 2026-05-16 CTO session:

**1. Chained capsules** — a DAG of time-windowed capsules where one capsule's unlock is
conditional on a receiver completing a task inside a prior capsule:
- C₁ opens at T+2d, closes at T+3d (1-day window)
- C₁ is sent to recipients A and B
- Inside C₁: a puzzle and a QR code containing an encrypted reference to C₂'s key
- First of {A, B} to solve the puzzle and scan the QR unlocks C₂ for themselves only
- If nobody solves it before C₁ closes → C₂ is never delivered (expiry-as-death)
- Proposed notation: `C₁({A,B}, [T₀+2d→T₀+3d], {puzzle, ref→C₂}) → C₂({winner}, [T₀+4d→T₀+5d], {prize})`

**2. Care Mode** — E2EE monitoring for Power of Attorney holders:
- Geofenced alerts ("if mum leaves the safe zone, notify only these people")
- E2EE location history (only POA holders can decrypt)
- Consent established and cryptographically timestamped while the person has capacity
- Consent revocable at any time while capacity is retained

This task depends on RES-004 to understand the cryptographic feasibility first. The
architect should read RES-004's findings before beginning this assessment.

## Goal

Produce an architectural feasibility assessment covering both concepts. This is a
thinking brief — not an implementation spec. The output informs the STR-001 synthesis
session and may lead to design tasks in a later iteration.

## Questions to address

### Chained capsules

1. **Server-side event model**: How does the server know C₂ should become available when
   someone solves C₁'s puzzle? Is this a webhook, a polling model, or something the client
   orchestrates? What are the trust implications of each approach?

2. **"First solver wins" fairness**: If A and B both submit solutions within milliseconds,
   how is the winner determined fairly? Is a commit-reveal scheme (commit answer hash,
   then reveal) the right model? What are the race condition risks?

3. **QR-as-capsule-reference**: What does the QR code inside C₁ actually encode? Is it
   an encrypted capsule ID, an encrypted key fragment, or something else? What protocol
   handles the scanning → unlock flow?

4. **Expiry-as-death enforcement**: If C₁'s window closes without a solve, what happens
   to C₂? Is C₂'s key material deleted, or simply never released? Who holds C₂'s key
   during the window? Is this the custodian model from RES-002?

5. **Envelope format extensions**: What new algorithm IDs or envelope fields are needed?
   Does this require changes to `docs/envelope_format.md`?

6. **Minimum viable architecture**: What is the smallest change to the current system
   (M10 foundation) that would support a simple two-capsule chain? What does a fuller
   DAG model require?

### Care Mode

7. **Location infrastructure**: The current system has no real-time data. What
   infrastructure would E2EE location tracking require — a new data type, a new service,
   a new client SDK surface? Is this a significant architectural departure from the
   vault model?

8. **E2EE geofencing**: Geofence matching requires knowing the person's location against
   a safe zone. If location is E2EE, how does geofence evaluation work without the server
   seeing plaintext coordinates? Options: client-side evaluation, trusted hardware,
   approximate/bucketed coordinates.

9. **Consent capsule data model**: How is the consent record modelled — as a sealed
   capsule variant, as a new table, or as a signed assertion? How is revocation recorded
   and enforced?

10. **Separation from core product**: Should Care Mode be a separate service/app or
    integrated into the core Heirlooms platform? What are the architectural coupling risks?

## Output

Produce an architecture brief to `docs/briefs/ARCH-008_chained-capsule-and-care-mode-feasibility.md`.

Report structure:
```
# ARCH-008 — Chained Capsule and Care Mode: Feasibility Assessment

## Chained capsules
  ### Server-side event model options
  ### First-solver-wins mechanism
  ### QR-as-capsule-reference protocol
  ### Expiry-as-death enforcement
  ### Envelope format extensions required
  ### Minimum viable architecture
## Care Mode
  ### Location infrastructure options
  ### E2EE geofencing approaches
  ### Consent capsule data model
  ### Separation vs integration
## Complexity and sequencing recommendation
## PA Summary
```

The PA Summary must include:
- Feasibility verdict for chained capsules (build on current foundation / needs new primitive / not feasible)
- Feasibility verdict for Care Mode (build on current foundation / significant new infrastructure / separate product)
- Recommended sequencing relative to current roadmap (M11, M12, M13)
- Any blockers that require CTO decisions before design can proceed

## Completion notes

**Completed:** 2026-05-16
**Output:** `docs/briefs/ARCH-008_chained-capsule-and-care-mode-feasibility.md`

**Key findings:**

- **Chained capsules — Build on current foundation.** Two-capsule MVP is directly composable
  from M11 primitives. Requires two new tables (`capsule_chain_links`, `capsule_puzzles`),
  one new endpoint (`POST /api/capsules/{id}/claim`), and an additive sealing request
  extension. No new cryptographic machinery. Recommended for M12 alongside the delivery
  scheduler work.

- **Care Mode — Significant new infrastructure, integrate within the platform (not a
  separate product) for v1.** The cryptography is straightforward (shared-plot-key variant;
  W3C VC for consent). The engineering cost is in background location services, push
  notification infrastructure, and a new high-frequency data type. Client-side geofence
  evaluation is the recommended v1 approach. Recommended for M13 alongside POA/executor
  and push notification work.

- **Envelope format:** No binary layout changes required. Three new algorithm IDs to
  register (`capsule-chain-ref-v1`, `vtlp-groth16-v1`, `consent-vc-v1`).

- **CTO decisions needed:** (1) VTLP-NP puzzle format adoption; (2) server-mediated vs.
  on-chain first-solver-wins; (3) regulatory readiness for GDPR Art. 9 (Care Mode);
  (4) Ed25519 signing key added to M11 identity model; (5) Care Mode pricing tier.

**Status transition:** queue → done
