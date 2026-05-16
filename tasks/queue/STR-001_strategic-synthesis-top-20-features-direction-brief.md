---
id: STR-001
title: Strategic synthesis — top 20 features and direction brief for CTO
category: Strategy
priority: Medium
status: queued
assigned_to: PA
depends_on: [MKT-001, RES-004, ARCH-008, LEG-002, PSY-001, PHI-001, RET-002]
estimated: 1 session
---

## Goal

Synthesise the seven persona thinking reports into a direction brief for the CTO.
This task must not begin until all seven reports are complete:

| Report | Persona | Output file |
|--------|---------|-------------|
| MKT-001 | Marketing Director | `docs/marketing/MKT-001_strategic-direction-thinking-report.md` |
| RES-004 | Research Manager | `docs/research/RES-004_chained-capsule-cryptographic-assessment.md` |
| ARCH-008 | Technical Architect | `docs/briefs/ARCH-008_chained-capsule-and-care-mode-feasibility.md` |
| LEG-002 | Legal | `docs/legal/LEG-002_care-mode-consent-chained-capsule-ip.md` |
| PSY-001 | Psychologist | `docs/psychology/PSY-001_grief-reframe-care-mode-experience-psychology.md` |
| PHI-001 | Philosopher | `docs/philosophy/PHI-001_ethics-conditional-delivery-consent-digital-legacy.md` |
| RET-002 | Retirement Planner | `docs/retirement/RET-002_three-segment-valuation-and-retirement-implications.md` |

## Background

The 2026-05-16 CTO/Marketing Director session explored the following directions:

1. Platform reframe: Heirlooms is a time-based digital archive, not a grief platform.
2. Three commercial segments: memory archive / care & consent / experience.
3. Care Mode: E2EE monitoring for Power of Attorney holders (Alzheimer's / diminishing capacity).
4. Chained capsules: conditional delivery DAG — time-windowed, competitive, expiry-as-death.
5. Experience segment: ARGs, brand campaigns, white-label, serialised storytelling.
6. Friend-tester / PLG: free storage for testing as a formalised growth model.
7. Novel IP: window capsule (LEG-001) + chained capsule (LEG-002) as a patent portfolio.

Each of the seven personas has produced a thinking report from their own perspective.
The PA's role in this task is to read all seven, synthesise the findings, and produce a
direction brief the CTO can use to update the roadmap.

## Instructions

### Step 1 — Read all seven reports

Read each report in full. Do not summarise from the task files — read the actual output
documents listed above. If any report has not been written, note which ones are missing
and ask the CTO whether to proceed with the available reports or wait.

### Step 2 — Synthesise PA summaries

Each report contains a mandatory PA Summary section. Extract and compile these into a
single cross-persona summary table covering:
- Key finding from each persona
- Decisions required from the CTO flagged by each persona
- Handoffs between personas that are pending

### Step 3 — Identify the top 20 most exciting features

Draw from all seven reports. A "feature" is defined broadly: it can be a product
capability, a commercial model, a technical primitive, an IP asset, or a design
principle. Rank by a combination of:
- Commercial potential (Marketing Director, Retirement Planner)
- Technical novelty and feasibility (Research Manager, Technical Architect)
- Legal buildability (Legal)
- Ethical soundness (Psychologist, Philosopher)
- Strategic fit with current roadmap (all personas)

For each of the top 20, record:
- Feature name
- One-sentence description
- Which personas flag it as significant
- Any blockers (legal, ethical, technical) before it can be built
- Suggested roadmap milestone (M11 / M12 / M13 / post-M13 / never)

### Step 4 — Identify convergent themes

Where multiple personas independently reach the same conclusion about a direction, flag
it as a convergent theme. Convergent support from four or more personas is a strong signal.

### Step 5 — Identify disagreements

Where personas reach conflicting conclusions (e.g. Marketing Director recommends a
direction that the Philosopher flags as ethically indefensible), surface the conflict
clearly. Do not resolve it — that is the CTO's decision.

### Step 6 — Draft the direction brief

Produce a direction brief the CTO can present as the basis for roadmap refinement.
The brief should be a document the CTO can read, annotate, and use as the input to
the next roadmap session — not a long report, but a crisp synthesis.

## Output

Produce the synthesis to `docs/planning/STR-001_strategic-synthesis-direction-brief.md`.

Report structure:
```
# STR-001 — Strategic Synthesis: Top 20 Features and Direction Brief

## Cross-persona summary table
## Top 20 most exciting features (ranked)
## Convergent themes (supported by 4+ personas)
## Key disagreements (requiring CTO decision)
## Recommended roadmap amendments
## Open decisions for CTO
## PA Summary
```

The direction brief (`docs/planning/STR-001_strategic-synthesis-direction-brief.md`)
should be concise — a document a CTO can read in 20 minutes and mark up.

The PA Summary must include:
- The single most important insight from the cross-persona synthesis
- The most urgent CTO decision (the one that blocks the most downstream work)
- A proposed trigger phrase for the next session: "Let's review the strategic synthesis"
