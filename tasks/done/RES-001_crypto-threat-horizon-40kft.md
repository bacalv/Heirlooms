---
id: RES-001
title: Cryptographic threat horizon — initial 40,000ft survey for CTO
category: Research
priority: High
status: done
assigned_to: ResearchManager
estimated: 1 session
---

## Goal

Produce an initial 40,000ft view research report for the CTO covering:
- Current trends in cryptography with regard to future attacks (quantum and beyond)
- How drand is addressing quantum vulnerability
- Attack vectors in Heirlooms' current system regarding contemporary encryption algorithms
- How Heirlooms has already started addressing some of these concerns

## Output

`docs/research/RES-001_crypto-threat-horizon-40kft.md`

## Completion notes

Research completed 2026-05-16. Brief produced from live web research across NIST, IACR,
The Quantum Insider, OWASP, drand documentation, and academic sources. Covers quantum
computing (primary threat), DNA/biological computing (theoretical), neuromorphic/photonic
computing (emerging), plus Heirlooms-specific exposure assessment and a forward-looking
mitigation roadmap. Key finding: the envelope format's cryptographic agility and M11's
multi-path capsule design are genuinely strong foundations. Primary action item is planning
the P-256 → hybrid/ML-KEM migration before the 2030–2032 window closes.
