---
id: MKT-001
title: Strategic direction thinking report — platform reframe, three segments, chained capsules
category: Marketing
priority: Medium
status: queued
assigned_to: MarketingDirector
depends_on: []
estimated: 1 session
---

## Context

This task was created following a CTO/Marketing Director session on 2026-05-16 that explored
several major strategic directions for Heirlooms. The session covered:

- A platform reframe: Heirlooms is a **time-based digital archive**, not a grief or loss platform.
  The word "loss" is now banned vocabulary alongside "cloud storage" and "backup".
- Five USPs identified (cryptographic enforcement, time-windows, PQC architecture, etc.)
- A **Care Mode** concept: E2EE monitoring for Power of Attorney holders caring for someone
  with diminishing capacity (e.g. Alzheimer's), with consent established cryptographically
  before capacity is lost. Consent is revocable at any time while the person still has capacity.
- **Chained capsules**: a DAG of capsules where one capsule's unlock is conditional on
  completing another. Key mechanics: time-windowed delivery, competitive delivery (first
  solver wins), expiry-as-death (nobody solves it → a second capsule is never delivered),
  and QR codes inside capsules pointing to encrypted references to other capsules.
  Notation introduced: `C₁({A,B}, [T₀+2d→T₀+3d], {puzzle, ref→C₂}) → C₂({winner}, [T₀+4d→T₀+5d], {prize})`
- **Experience segment**: ARGs, brand campaigns, serialised storytelling, white-label /
  org-flavoured app, promotional distribution (QR codes in magazines / "first 300 unlock codes").
- **Friend-tester / PLG model**: free storage in exchange for real-world testing — currently
  organic, worth formalising.

## Goal

Produce a Marketing Director thinking report that processes these ideas through a commercial
and brand lens. This is a reflective brief, not an execution plan — the output feeds a
cross-persona synthesis session.

## Questions to address

1. **Brand reframe**: How should "time-based digital archive for happy memories" be codified
   into brand language? What changes are needed in `docs/BRAND.md`? What vocabulary should
   be added or retired?

2. **Three-segment model**: What are the right names for the three commercial segments
   (memory archive / care & consent / experience)? Who is the customer in each, and what
   does the purchase decision look like?

3. **USP mapping**: Which USPs belong to which segment? Are there USPs that only emerge
   when segments are combined (e.g. a brand that uses the experience platform to also offer
   digital legacy features)?

4. **Care Mode positioning**: How do you position a monitoring product without alarming
   the mainstream user? What is the brand risk of associating Heirlooms with surveillance,
   even consent-based surveillance?

5. **Experience segment**: What is the commercial model — brand campaign fees, white-label
   licensing, API tier pricing? What is the competitive landscape? Who are the buyers?

6. **Chained capsules**: What are the most commercially exciting applications? Which
   audiences are immediately addressable (ARG designers, brand campaign agencies, publishers)?

7. **Friend-tester / PLG**: What does formalising this as a strategy look like? What is
   the "free tier" offer, and how does it convert to paid?

8. **Brand risk assessment**: The Experience segment introduces gamification and competition
   into a platform with a solemn, dignified voice. How is this managed? Are these audiences
   separable, or does serving one dilute the other?

9. **Top features**: From a commercial and market perspective, what are your top 10 most
   exciting features or product directions emerging from this session?

## Output

Produce a report to `docs/marketing/MKT-001_strategic-direction-thinking-report.md`.

Report structure:
```
# MKT-001 — Strategic Direction Thinking Report

## Brand reframe recommendations
## Three-segment model
## USP mapping by segment
## Care Mode positioning
## Experience segment commercial model
## Chained capsules — top commercial applications
## Friend-tester / PLG strategy
## Brand risk assessment
## Top 10 features (ranked, with rationale)
## PA Summary
```

The PA Summary section is mandatory. It must include:
- 3–5 bullet points of the most important findings
- Any decisions required from the CTO
- Any handoffs needed to other personas (Legal, Psychologist, etc.)

## Completion notes

Completed by Marketing Director on 2026-05-16.

Report produced at `docs/marketing/MKT-001_strategic-direction-thinking-report.md`.

All nine questions from the task brief are addressed. Key findings:

- The "time-based digital archive" reframe is validated commercially; three BRAND.md additions recommended (platform positioning section, banned vocabulary expansion, Experience tone register).
- Care & Consent identified as the highest near-term commercial opportunity; requires Legal persona validation before any positioning is finalised.
- Experience segment architecture (separate surface vs. segmented main product) flagged as a CTO decision gate; brand dissonance risk is real if audiences share a surface.
- Expiry-as-death identified as the single most commercially unique feature; Technical Architect must confirm cryptographic (not policy) basis before the claim is made publicly.
- Friend-tester / PLG formalisation recommended as first marketing priority over any paid acquisition.

Handoffs raised to: Legal, Psychologist, Technical Architect, Research Manager. Four CTO decisions requested (Experience surface architecture, BRAND.md additions, Care Mode legal validation, tlock positioning).
