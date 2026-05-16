---
id: RET-002
title: Three-segment valuation — Care Mode, Experience segment, and retirement implications
category: Retirement Planning
priority: Medium
status: queued
assigned_to: RetirementPlanner
depends_on: [RET-001]
estimated: 1 session
---

## Context

The 2026-05-16 CTO session identified three distinct commercial segments for Heirlooms,
significantly expanding the initial single-segment view from RET-001. Each segment has
different market size, revenue model, and exit valuation implications.

**Segment 1 — Memory Archive (original)**
Personal time-based digital archive. Consumers plant happy memories for chosen recipients
at chosen moments. E2EE, post-quantum architecture, time-windowed capsule delivery.
Consumer subscription model.

**Segment 2 — Care & Consent**
E2EE monitoring for Power of Attorney holders. Geofenced alerts, E2EE location history,
cryptographically timestamped consent established before capacity is lost. Consent
revocable while capacity is retained. Target: families caring for relatives with
Alzheimer's, dementia, or other diminishing capacity conditions.
Market sizing indicator: ~900,000 people in the UK with dementia; ~700,000 primary
carers; >1 million LPA registrations per year in England and Wales alone.

**Segment 3 — Experience**
Chained capsule campaigns for brands, ARG designers, publishers, and educational
institutions. White-label / org-flavoured app. "First solver wins" competitive delivery.
Promotional distribution via QR codes. API tier / platform licensing.
Example: "First 300 QR codes in this magazine unlock 1GB free" — sold to a publisher.

**Novel IP identified**:
- Window capsule construction (LEG-001 patent in progress)
- Chained capsule / conditional delivery construction (LEG-002 to assess separately)

**Friend-tester / PLG model**: Free storage in exchange for real-world testing. Currently
organic. Could be formalised as a product-led growth strategy.

## Goal

Produce a retirement planning brief that synthesises the commercial potential of the
three-segment model and its implications for Bret's personal retirement planning.

Read RET-001 first for the baseline assessment and Bret's personal context.

## Questions to address

### Market sizing

1. **Memory Archive segment**: Refine the TAM/SAM/SOM from RET-001 in light of the
   "time-based digital archive" reframe (broader appeal than the grief framing).
   What comparable consumer subscription products (Ancestry, 23andMe, Headspace) traded
   at in exit events, and what multiples apply?

2. **Care & Consent segment**: Size the UK and global market for dementia/Alzheimer's
   care technology. What do competitors in this space (GPS trackers, medical alert systems,
   care management apps) trade at? What premium does E2EE and LPA-alignment command?

3. **Experience segment**: Size the ARG / interactive experience market, the brand
   campaign / experiential marketing market, and the white-label platform licensing
   market. What comparable API/platform businesses (Twilio, SendGrid before acquisition)
   traded at?

### Revenue model

4. **Care & Consent revenue**: Subscription (family tier), institutional licensing
   (care homes, NHS partnerships, private healthcare), insurance partnerships? Which
   model aligns with the brand and generates the most predictable revenue for valuation
   purposes?

5. **Experience segment revenue**: Brand campaign fees (per-campaign), white-label
   licensing (annual SaaS), API tier (usage-based)? What is the likely contract size
   for a medium publisher vs a large brand?

6. **Combined model**: Does a three-segment model increase or complicate the exit story?
   Acquirers value focus — does the three segments make Heirlooms a better acquisition
   target (more addressable market) or a harder one (no clear identity)?

### IP valuation

7. **Window capsule patent (LEG-001)**: If the patent is granted, what licensing revenue
   is plausible? What does a comparable cryptographic patent portfolio trade at?

8. **Chained capsule patent (LEG-002 pending)**: If patentable, how does this add to the
   IP portfolio value?

9. **Combined IP value**: A two-patent cryptographic portfolio covering novel time-locked
   and conditionally-chained encryption constructions — what is a reasonable valuation
   range for this IP in a UK/international context?

### Friend-tester / PLG

10. **User acquisition model**: The current friend-tester model gives free storage for
    testing. What user acquisition cost (CAC) does this imply? What lifetime value (LTV)
    is reasonable for a consumer subscriber in this space? Is the CAC/LTV ratio investable?

11. **Viral coefficient**: Chained capsules (where recipients become users) and Care Mode
    (where monitoring creates a family trust network) both have natural viral loops. What
    is the estimated K-factor and its impact on growth projections?

### Risk assessment

12. **Care Mode regulatory risk**: If Care Mode is classified as a medical device (MHRA),
    what are the certification costs and timeline implications? How does this affect the
    valuation and exit timeline?

13. **Segment prioritisation for Bret's timeline**: Given the retirement planning context
    from RET-001, which segment should be prioritised to maximise Bret's personal outcome
    within a realistic timeframe? Is there a sequencing that maximises both revenue and
    exit optionality?

## Output

Produce a retirement planning brief to `docs/retirement/RET-002_three-segment-valuation-and-retirement-implications.md`.

Report structure:
```
# RET-002 — Three-Segment Valuation and Retirement Implications

## Market sizing
  ### Memory Archive
  ### Care & Consent
  ### Experience
## Revenue model assessment by segment
## Combined model — exit story analysis
## IP portfolio valuation
## Friend-tester / PLG financial model
## Risk assessment
  ### Care Mode regulatory risk
  ### Execution risk across three segments
## Recommended prioritisation for Bret's timeline
## PA Summary
```

The PA Summary must include:
- The single highest-value direction for Bret's personal retirement outcome
- Whether three segments increase or decrease exit attractiveness
- The IP portfolio value range (conservative / central / optimistic)
- Any decisions the CTO needs to make to protect or maximise personal value

## Completion notes

**Completed:** 2026-05-16  
**Output:** `docs/retirement/RET-002_three-segment-valuation-and-retirement-implications.md`

**Summary of findings:**

- **Highest-value direction:** Care & Consent — largest addressable market (700,000 UK primary carers + 1.37m LPA registrations/year), no direct comparable product, institutional B2B revenue model commands higher exit multiples, structural demographic tailwind.
- **Three segments net assessment:** Moderate increase to exit attractiveness if sequenced correctly (Memory Archive → Care & Consent consumer → Care & Consent institutional → Experience opportunistic). Simultaneous pursuit is not viable solo.
- **IP portfolio value:** Conservative £125k–£400k incremental; Central £300k–£900k; Optimistic £700k–£2.4m. Window capsule (LEG-001) is the primary asset.
- **Exit scenario range:** Bear £0–£200k; Base £870k–£5.9m net (3-year horizon); Bull £6.5m–£60m net (5-year horizon).
- **Urgent decisions:** Commission UK patent attorney for LEG-001 (target mid-July 2026 filing); confirm Heirlooms incorporated status; commission LEG-002 (chained capsule); get first paying users; design Care & Consent to avoid MHRA medical device classification.
- **Research note:** Web research conducted on UK dementia/care technology market size, LPA registration trends, consumer subscription exit multiples (Ancestry, 23andMe, Headspace, Calm), ARG/experiential marketing TAM, API platform comparable exits (Twilio/SendGrid), cryptographic patent portfolio valuations, MHRA digital health classification, SaaS/subscription revenue multiples, and PLG/CAC/LTV benchmarks.
