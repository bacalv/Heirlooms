# RET-001 — Initial Retirement Assessment

**Prepared by:** Retirement Planner  
**Date:** 2026-05-16  
**Status:** First-pass assessment — assumptions explicit throughout  
**Audience:** Bret (personal — not a company document)

---

> **Important caveat throughout this document:** Bret has not yet answered the Phase 1 questionnaire (see `docs/retirement/RET-001-questionnaire.md`). Every number in this assessment is an assumption derived from inference, the product record, and general UK planning norms. Assumptions are marked **[ASSUMED]**. Once Bret completes the questionnaire, every section below should be revisited and the numbers replaced with actuals.

---

## Current position summary

### Who Bret is (from the record)

Bret is the sole founder of Heirlooms, operating from the UK. He is a technical CTO who is building a sophisticated, privacy-first, encrypted family media and legacy product. He appears to be the sole developer and architect of a product now at v0.53.1, spanning four platforms (Android, iOS, Web, Kotlin backend), with genuine cryptographic innovation in its envelope format and E2EE design. The product launched in April 2026 and has shipped ten milestones in approximately six weeks of intensive development.

There is currently one friend tester (onboarded May 2026). Revenue is **[ASSUMED] £0**. This is a very early-stage product with no commercial model yet in place.

### Assumed financial baseline

All assumptions below are placeholders. They represent a plausible profile for a UK-based technical founder in his working career, but they may be significantly wrong. **Replace all of these with actuals from the questionnaire.**

| Item | Assumed value | Confidence |
|---|---|---|
| Age | **[ASSUMED]** Mid-to-late 30s | Low |
| Target retirement age | **[ASSUMED]** 55–65 | Low |
| Annual income (total, gross) | **[ASSUMED]** £60,000–£90,000 | Low |
| Employment structure | **[ASSUMED]** Director of limited company + Heirlooms | Low |
| UK state pension — qualifying years | **[ASSUMED]** 15–25 years | Low |
| Workplace/SIPP combined pension value | **[ASSUMED]** £50,000–£150,000 | Low |
| ISA value | **[ASSUMED]** £20,000–£60,000 | Low |
| Primary residence equity | **[ASSUMED]** £100,000–£300,000 | Low |
| Investment property | **[ASSUMED]** None | Low |
| Total unsecured debt | **[ASSUMED]** Low (< £10,000) | Low |
| Monthly essential outgoings | **[ASSUMED]** £2,500–£4,000 | Low |
| Heirlooms equity stake | **[ASSUMED]** 100% | High (inferred from sole-founder status) |
| Heirlooms revenue | £0 currently | High |

### What this means right now

On the assumed baseline, Bret has a credible long-term retirement foundation from conventional savings (pension + ISA + property equity), but it is unlikely to deliver the kind of retirement that makes working into your 60s feel optional without a Heirlooms exit or similar liquidity event. The opportunity cost of devoting intensive development time to Heirlooms is therefore a live question. If the product succeeds, it is transformative. If it does not, the time cost is real.

---

## IP and asset value assessment

### What makes Heirlooms defensible

I have reviewed the full product record, envelope format specification, RES-001, and all team persona briefs. My assessment of the IP and asset picture is:

**Genuine architectural novelty — high value if defended**

The combination of:
1. A versioned, algorithm-agile E2EE envelope format applied across all encrypted blobs
2. A DEK-per-file model that enables O(keys) rather than O(files) PQC migration
3. A multi-path capsule unlock design (recipient pubkey wrapping + tlock + Shamir shares) that explicitly defends against the failure of any single cryptographic primitive
4. A time-windowed capsule construction (proposed for RES-002) that — if formalised — may have no close published equivalent

...represents a level of cryptographic architecture sophistication that is genuinely unusual for a consumer product at this stage. The Research Manager (RES-001) assessed it as "significantly more forward-looking than most products at its stage." I agree. This is real IP, not just a clever product.

**The "time-windowed trust envelope" construction** (as the Research Manager named it) — an encrypted object that is mathematically inaccessible before an unlock time, enforced-unavailable after an expire time, accessible only to a named recipient through a quorum of custodians, on a server architecturally incapable of reading it — has no standardised name in the academic literature as of 2026. If RES-002 confirms this, it may be a publishable, patentable construction. This is the highest-value IP scenario.

**Brand and positioning are an asset too**

The product occupies a space that large platforms structurally cannot enter: grief-aware, dignity-first, cryptographically enforced legacy. As the Marketing Director persona notes, the framing that separates Heirlooms from cloud storage — "who should receive this memory, and when?" — is emotionally and commercially distinct. That positioning, if defended and built into a user base, is worth acquiring.

### Comparable acquisitions

No perfect comparables exist. The relevant adjacent markets:

| Sector | Example acquisitions | Observed multiples |
|---|---|---|
| Privacy / security consumer apps | 1Password, ProtonMail investment rounds | 10–20x revenue; or 2–8x ARR at scale |
| Digital legacy / estate planning | DeadHappy, Empathy, Cake (US) | Typically venture-backed, not yet exited |
| Encrypted messaging / vault | Wickr (AWS acquisition), Keybase (Zoom) | Acqui-hire or strategic, not revenue-based |
| Family media / photo vault | Google Photos, iCloud, Amazon Photos | Infrastructure giants; not relevant as acquirers |

The most relevant acquirers are not infrastructure companies but:
- Estate planning platforms seeking a technical differentiator
- Legal tech companies (law firms, will-writing platforms) seeking cryptographic trust infrastructure
- Enterprise document escrow providers
- Financial services companies exploring digital estate products
- Security-focused conglomerates (Thales, Entrust, Palo Alto Networks) if the PQC angle becomes a product pitch

### Valuation ranges

**Important caveat:** These are illustrative ranges. Without revenue, any valuation is speculative. The primary value driver in the near term is IP and team, not revenue.

**Bear scenario** — product never monetises, wound down:
- Asset value: IP only, if documented and a buyer emerges for it
- Likely outcome: £0–£50,000 (time and IP sunk)
- Retirement impact: None from Heirlooms; fallback plan carries the load

**Base scenario** — modest acquisition, licensing deal, or revenue exit at 3–5 years:
- Acquisition of IP + team by an estate planning, legal tech, or security company
- Assumed range: £250,000–£1,500,000 gross proceeds
- After UK CGT on business sale (entrepreneur's relief / BADR at 10% up to £1m lifetime limit, then 20% above): approximately £225,000–£1,300,000 net **[ASSUMED — tax treatment depends on structure]**
- Retirement impact: Material improvement; adds 5–15 years of runway to a conventional pension-backed retirement

**Bull scenario** — strong acquisition or revenue-led exit:
- Product develops paying user base (5,000–50,000 users at £5–15/month) or achieves enterprise/legal distribution deal
- Acquisition by strategic acquirer valuing the PQC-first positioning and network
- Assumed range: £2,000,000–£10,000,000 gross proceeds
- After CGT: approximately £1,800,000–£9,000,000 net (BADR on first £1m, 20% on remainder) **[ASSUMED]**
- Retirement impact: Transformative; may fund full retirement even without any other savings

**These are ranges, not forecasts.** The bear scenario is a real possibility. The bull scenario requires things to go very well on multiple dimensions simultaneously. The base scenario is where I would anchor planning, with the bull as the upside worth working towards.

---

## Revenue / exit scenario modelling

### 1-year horizon (by May 2027)

The product is currently at v0.53.1 with one external tester. A 1-year horizon is too short for a revenue exit. The value of the next 12 months is:
- Completing M11 (strong sealing + social recovery) and M12 (milestone delivery)
- Onboarding a first cohort of beta users and testing the emotional resonance of the core product
- Establishing the PQC positioning and making first public claims about the cryptographic architecture

**Bear:** No monetisation, Heirlooms paused or wound down; Bret returns full attention to other income. Zero Heirlooms exit value.

**Base:** Product reaches beta with 50–500 users; Heirlooms is alive and credibly funded to continue. No exit. IP value notionally £100,000–£500,000 if sold, but unlikely to be sold at this stage.

**Bull:** An unexpected inbound from a strategic acquirer or a partnership deal (law firm, estate planner) that provides early validation. Acqui-hire at £500,000–£1,500,000 is possible but unlikely at this stage.

**My recommendation:** Do not model a 1-year exit. Use the 1-year horizon for building the product and the IP. The decisions that matter most right now are product completion and IP protection, not monetisation.

### 3-year horizon (by May 2029)

M12 and M13 are delivered. Scheduled delivery and posthumous delivery are live. The product has the features that distinguish it from everything else. This is where monetisation must begin.

**Bear:** Product has a small user base (< 1,000 paying) at low ARPU; Heirlooms is wound down or pivoted. Proceeds: £0–£100,000 from IP sale.

**Base:** 2,000–10,000 paying users at £8–12/month (£192,000–£1,440,000 ARR). Strategic acquirer or licensing deal. Exit proceeds: £500,000–£3,000,000 gross. After CGT: approximately £450,000–£2,700,000. **At the low end of the base, this is a meaningful improvement to retirement but not full retirement funding.** At the upper end, it is transformative.

**Bull:** Enterprise/legal distribution deal or VC-backed scale-up with 50,000+ users. Exit proceeds: £5,000,000–£15,000,000 gross. After CGT: £4,500,000–£13,500,000. **This is full-retirement-funding at any reasonable lifestyle assumption.**

### 5-year horizon (by May 2031)

If Heirlooms survives to 2031 with a real user base and the PQC positioning has become a mainstream concern (NIST mandates for enterprise, Q-Day moving closer), the bull scenario becomes considerably more plausible. The product could be acquired specifically for its post-quantum credibility, its community of trust-conscious users, or its unique capsule delivery infrastructure.

**Bear:** Same as 3-year bear but with more sunk opportunity cost. The 5-year bear is the most painful outcome: significant time invested with no return.

**Base:** Established product, 10,000–30,000 paying users, exit at £1,000,000–£5,000,000 gross. **After CGT: £850,000–£4,200,000.** This, added to pension savings accrued during the same period, likely funds a comfortable retirement at 60–65.

**Bull:** Heirlooms is a known name in privacy-conscious digital legacy. Acquirer pays £10,000,000–£25,000,000. After CGT: £8,500,000–£21,000,000. **Full retirement immediately, with generational wealth potential.**

---

## Key risks to retirement value

Ranked by severity × likelihood. A risk ranked HIGH on both dimensions is existential; a risk ranked HIGH severity but LOW likelihood is worth monitoring but not paralysing.

### 1. Single-founder dependency — SEVERITY: HIGH | LIKELIHOOD: HIGH (present risk)

Heirlooms is Bret. There is no team. Every line of code, every architectural decision, every product direction is held in one person's head. This is:
- The primary **operational risk** — if Bret is incapacitated, the product stops
- The primary **exit risk** — acquirers will apply a significant discount to a product with no team and no documentation trail adequate for handover
- A **bus factor of 1**

This is the single most important risk to fix before the 3-year horizon. It does not require hiring (which may not be feasible or desirable at this stage), but it does require:
- Thorough architectural documentation (already partially done via `docs/briefs/`)
- IP documentation adequate for a legal room (patents, trade secrets register)
- At minimum, a second person who understands the product direction

The PA agent system partially mitigates this — the briefs and task system create a documented trail. But it is not enough for an acquirer.

### 2. P-256 / HNDL cryptographic risk — SEVERITY: HIGH | LIKELIHOOD: MEDIUM (2027–2032 window)

As RES-001 establishes, Heirlooms' key-wrapping layer is entirely P-256, which is quantum-vulnerable. HNDL adversaries may already be harvesting encrypted user data. If Q-Day arrives before Heirlooms completes its PQC migration, the product's central value proposition — "we cannot read your data" — is undermined.

The risk to retirement value is:
- **Direct:** A documented quantum vulnerability discovered or publicised before migration is complete could destroy user trust and make Heirlooms unsaleable or unsaleable at any worthwhile price
- **Indirect:** If Heirlooms becomes known for using vulnerable cryptography while claiming "cryptographic guarantee," the reputational damage is severe
- **Mitigated by:** The versioned envelope format and DEK-per-file model, which make migration architecturally tractable. The mitigation exists; it needs to be executed.

**The positive framing:** If Heirlooms completes a credible hybrid P-256+ML-KEM migration *before* the competition, it becomes one of the very few consumer products that can truthfully claim post-quantum readiness. That is a significant differentiator for both users and acquirers.

### 3. Monetisation failure — SEVERITY: HIGH | LIKELIHOOD: MEDIUM

The product has no revenue model currently defined beyond the implicit "subscription." The consumer privacy/legacy space has a well-documented cold-start problem: people know they should plan for death and legacy but emotionally defer it. If Heirlooms cannot solve the emotional onboarding problem at scale, it may remain a product with a dedicated niche but insufficient revenue to justify a meaningful exit.

**Risk factors:**
- No marketing briefs yet produced (the Marketing Director persona has just been established; no `docs/marketing/` content exists yet)
- No pricing framework defined
- No distribution strategy documented
- The "death" framing needs careful management — broad consumer appeal requires the "time, not just death" reframe to land

### 4. Competitive emergence — SEVERITY: HIGH | LIKELIHOOD: MEDIUM

The space Heirlooms occupies — encrypted digital legacy with time-locked delivery — is currently lightly populated. But the underlying emotional problem (what happens to my data, my memories, my messages?) is large and increasingly recognised. The risk is:
- A well-funded incumbent (Google, Apple, Dropbox) adding a "legacy" feature
- A better-funded startup tackling the same space with more resources
- A law firm or estate planning company building a proprietary solution

**Mitigating factor:** The deep cryptographic architecture is a genuine moat. A Google Photos "legacy contact" feature does not provide what Heirlooms provides — cryptographic enforcement of delivery conditions. That distinction needs to be loudly articulated before a larger player muddies the positioning water.

### 5. Regulatory risk — SEVERITY: MEDIUM | LIKELIHOOD: LOW-MEDIUM

E2EE is increasingly under regulatory scrutiny in the UK (the Online Safety Act implications for E2EE are still contested as of 2026). A product that explicitly promises that the server cannot read user data could be legally challenged if future regulation requires access to encrypted data for law enforcement purposes.

This is not an immediate existential risk, but it is a planning consideration. The design's "Heirlooms cannot read your data" positioning may require legal nuancing depending on how UK/EU regulation evolves.

### 6. tlock/BLS12-381 dependency — SEVERITY: MEDIUM | LIKELIHOOD: LOW

If BLS12-381 is broken before Heirlooms has completed the M11 multi-path design, and if users have already sealed capsules relying on the tlock path, there is a user trust issue. The M11 multi-path design mitigates the worst case (a BLS break doesn't unlock capsules), but the marketing implication of "one of our three unlock mechanisms is no longer quantum-safe" needs handling.

**Mitigated by:** The explicit multi-path design and the planned "tlock is one tool, not the foundation" framing.

### 7. Personal time and opportunity cost — SEVERITY: MEDIUM | LIKELIHOOD: HIGH (ongoing)

Building Heirlooms is a significant time investment. The opportunity cost is real: time spent on Heirlooms is time not spent on consulting, employment, or other income. If the product does not reach a revenue or exit event, the accumulated time cost is unrecoverable. This is not a reason to stop — but it is a reason to set clear milestones and decision points at which Bret evaluates whether to continue.

---

## Recommended actions

### Immediate (next 90 days)

**1. Complete the questionnaire (Section 1–13)**
Without actual financial data, all modelling is illustrative. The questionnaire is the starting point. Estimated time: 2–3 hours to gather figures.

**2. Audit your pension position**
Check your state pension forecast at gov.uk/check-state-pension. If you have fewer than 35 qualifying years, calculate whether buying voluntary NI contributions makes sense — it almost always does at current prices. Locate all dormant workplace pension pots and check if consolidation is appropriate.

**3. Protect the IP — file a trademark**
Register the "Heirlooms" trademark with the UK Intellectual Property Office (UKIPO). Cost: £170 for one class online. This is a minimum viable IP protection step that a solicitor cannot reasonably omit from a due diligence checklist. Do not let this slip past the 3-year horizon unaddressed — it becomes harder (and more expensive) to enforce after competitors emerge.

**4. Consult a solicitor on structure**
Confirm whether Heirlooms is operated through a limited company. If not, it should be, primarily for tax efficiency on a future exit (BADR / entrepreneurs' relief), for limiting personal liability, and for clarity of IP ownership. If it is already a company, confirm that IP is correctly assigned to the company rather than sitting with Bret personally.

**5. Write one-page "hit by a bus" documentation**
A document that tells a competent technical person: what Heirlooms is, how the cryptographic design works, where the source code lives, and what the next 3 milestones are. This is not product documentation — it is the document an acquirer reads in the first 30 minutes of due diligence. It also protects Bret personally: if something happens to him, this gives any next-of-kin or executor enough context to act.

**6. Prioritise RES-003 (PQC migration readiness brief)**
Get the PQC migration readiness brief written. This is not just a technical task — it is the document that supports the "post-quantum ready by design" narrative that distinguishes Heirlooms for acquirers and regulators. Having it written before approaching any potential partners or acquirers is important.

### 1-year horizon

**7. Establish a revenue model and price point**
Engage the Marketing Director to produce the first marketing brief before M12 ships. The question to answer: what do customers pay, and how? A subscription tier (consumer), an enterprise tier (legal/estate planning), or a licensed custodian network fee? At minimum, have a model ready to test by M12 delivery.

**8. Onboard first paying users**
Even at a minimal price, having paying users before a potential exit is worth more than 10x the revenue in valuation terms. Paying users validate willingness to pay; it transforms the product from a technical artefact into a business.

**9. Begin the P-256 migration plan**
Commission ARCH (Technical Architect) to produce the hybrid P-256+ML-KEM migration brief. This need not be implemented yet, but it must be documented and ready. A potential acquirer doing technical due diligence will ask about quantum readiness; having a written migration plan is the correct answer.

**10. Increase pension contributions if income allows**
If Heirlooms generates any revenue, ensure pension contributions are optimised — company contributions to a SIPP via a limited company are the most tax-efficient way to extract profits, better than salary or dividends for a sole director. Target the full annual allowance (£60,000) if feasible.

**11. Build at minimum one other person into the product story**
This doesn't mean hiring. It could mean: a trusted technical adviser who understands the architecture; a friend tester who becomes a product champion; a first enterprise or legal partner who is publicly associated with the product. The goal is demonstrating that the product exists outside Bret's head.

### 3-year horizon

**12. Make an explicit go / no-go decision on Heirlooms**
By May 2029, if Heirlooms has not reached a viable user base or a plausible revenue trajectory, Bret needs to make an honest assessment: continue, pivot, or wind down. The 3-year point is the natural decision gate. The decision should be made with a full financial picture — what does Bret's retirement look like at each option?

**13. Evaluate IP monetisation separately from product monetisation**
If the product has not reached consumer scale by Year 3 but the cryptographic IP (window capsule, PQC-ready envelope format, multi-path capsule unlock) is documented and defensible, there is a pathway to licensing or selling the IP independently of the product. This requires the IP to have been formally protected (trademarks, possibly patents on the specific constructions) before this point.

**14. Review pension and ISA position annually**
By the 3-year mark, the goal should be: pension at or on track for a target pot adequate for a fallback retirement (see Key Decision 3 below); ISA contributions at maximum annual allowance; clear plan for property equity and any other assets.

---

## Key decisions for Bret

These are the decisions that most directly affect your retirement outcome. I've ranked them by urgency.

---

### Decision 1 — Is Heirlooms your primary retirement vehicle or a supplement?

**Why it matters:** This decision changes everything else. If Heirlooms is your primary retirement vehicle, you should be making different choices about how you invest your time and money right now than if it's a bonus scenario that your conventional savings can survive without.

**Options:**

- **Option A — Primary vehicle:** You are betting on Heirlooms to fund a significant portion of your retirement. Implication: it should be formally structured, IP-protected, and given enough runway (3–5 years) to reach an exit. Conventional savings become the safety net, not the plan.

- **Option B — Material supplement:** Heirlooms success makes retirement substantially better, but your pension and savings will get you there without it. Implication: continue building Heirlooms but with a clear go/no-go gate at year 3. Don't sacrifice pension contributions for product development time.

- **Option C — Bonus scenario:** Your pension and other assets are on track for the retirement you want regardless of Heirlooms. Implication: you can be more relaxed about the timeline and less stressed about go/no-go decisions.

**Trade-offs:** Option A carries more risk but more upside. Option C is the lowest-stress framing but may not be realistic given the time investment Heirlooms currently demands.

**Recommended option:** Without your questionnaire data I cannot say for certain, but my provisional recommendation is to aim for **Option B**. It is the most resilient framing: it keeps conventional savings healthy (pension maximised, ISA contributed to) while giving Heirlooms a fair run. It also gives you the psychological freedom to make clear-eyed exit decisions at the 3-year gate without the pressure of "this is the only plan."

**What to do:** Answer Section 10.4 of the questionnaire honestly. Then revisit this decision.

---

### Decision 2 — Do you want to seek external investment in Heirlooms?

**Why it matters:** Taking external investment (angel, VC, or strategic) would: (a) provide runway to hire and scale; (b) provide market validation; (c) accelerate the path to a meaningful exit. It would also: (a) dilute Bret's equity; (b) introduce investor expectations and governance requirements; (c) potentially constrain exit options.

**Options:**

- **Option A — Stay bootstrapped:** Continue building with no external capital. Retain 100% of any future exit. Maintain full control. Accept that the growth pace is limited by Bret's solo capacity.

- **Option B — Raise a small seed round:** Raise £250,000–£750,000 from angels or a small seed fund. Use it to bring one or two technical hires on board. Accept 15–25% dilution. Significantly accelerates the path to M12/M13 and monetisation.

- **Option C — Strategic investment from a partner:** Accept investment from a law firm, estate planning company, or financial services group that also becomes a distribution partner. Accept dilution but gain both capital and a go-to-market channel.

**Trade-offs:**
- Option A preserves equity but the solo constraint is real. A single developer building a four-platform cryptographic product is impressive but slow, and every month of delay is a month the competitive window narrows.
- Option B adds capital and people but dilutes. The post-investment expected value of Bret's stake (a smaller percentage of a larger outcome) often exceeds the pre-investment value — but not always.
- Option C is the highest-strategic-value option if the right partner exists, but also the highest constraint option if the partner's interests diverge from Bret's.

**Recommended option:** At this stage, **Option A for the next 12 months**, with a clear plan to re-evaluate at M12 delivery. The product is not yet at a stage where external investors would receive it confidently — there is one external user and no revenue. The right time to raise is after M12/M13, when the core product is complete, there are paying users, and the story is clean. Raising now would undervalue the equity.

**Exception:** If an inbound strategic partner emerges with both capital and a genuine distribution advantage, evaluate it seriously regardless of timing.

---

### Decision 3 — What is your target retirement pension pot, and are you on track?

**Why it matters:** Even if Heirlooms delivers a strong exit, you need a personal financial safety net that does not depend on it. The pension question is the foundational one that persists regardless of the Heirlooms outcome.

**Options:**

To illustrate, I'll work from assumptions (replace with actuals from the questionnaire):

Assuming a target annual income of £50,000 in retirement (today's money), and using a 4% drawdown rate as a rough rule:

- **Target pension pot (at retirement):** approximately £1,000,000 (before state pension is factored in)
- **State pension contribution (35 qualifying years, full):** £221.20/week = £11,500/year — effectively reduces the pension pot needed by approximately £287,000 at a 4% drawdown
- **Adjusted target pot:** approximately £700,000–£750,000

If your current pension pot is **[ASSUMED]** £50,000–£150,000 and you have **[ASSUMED]** 25–35 years to retirement, this is achievable with disciplined contributions but requires regular annual maximisation of pension contributions.

- **Option A — Maintain current contributions:** If current contributions are adequate, keep them as-is.
- **Option B — Maximise pension contributions now:** If you are not maximising the annual allowance (£60,000/year gross for 2026/27), consider doing so, especially via company contributions if operating as a limited company — these are the most tax-efficient mechanism available to a UK founder.
- **Option C — Defer pension maximisation in favour of product investment:** Accept a lower pension contribution now, betting that Heirlooms exit proceeds will compensate later.

**Recommended option:** **Option B** — maximise pension contributions now, within the constraints of your actual income. The tax relief on pension contributions is the most reliable return available in the UK. Even at basic rate, a £1,000 gross contribution costs £800 net. At higher rate, £600 net. If you are a company director, employer contributions have no NI cost and are deductible against corporation tax. This is not optional — it is the most important thing you can do today with surplus cash.

**What to do:** Establish your actual pension position (questionnaire Section 4/5) and calculate whether you have unused annual allowance from previous years (carry-forward rules allow up to 3 years' unused allowance to be used in a single year).

---

### Decision 4 — Should Heirlooms pursue a patent strategy?

**Why it matters:** If the window capsule construction (tlock lower bound + Shamir custodian deletion upper bound) is novel — which RES-002 may confirm — there is a potential patent filing opportunity. Patents create legally defensible IP that can be licensed, sold, or used to deter competitors. They also add material value in an acquisition.

**Options:**

- **Option A — No patent filing:** Rely on trade secret protection and speed-to-market. Keep everything internal. This is the default if you do nothing.

- **Option B — File UK patent application on the window capsule construction:** Cost: approximately £4,000–£8,000 with a specialist patent attorney. Provides 12 months' priority date from filing, during which you can file internationally. A granted UK patent takes 3–5 years; the priority date matters more than the grant date for competitive purposes.

- **Option C — File PCT application:** File internationally via the Patent Cooperation Treaty (PCT) for global coverage. Cost: £10,000–£20,000 at filing, significantly more to prosecute in each country. Appropriate only if the US, EU, or other markets are strategically important.

**Trade-offs:**
- Option A is cheapest but leaves IP unprotected. If a competitor independently develops the same construction, they can patent it and exclude Heirlooms.
- Option B is the minimum viable IP protection for a novel construction. The cost is modest relative to the potential exit value uplift.
- Option C is appropriate at a later stage, once the product has commercial traction and the specific geographies that matter for exit are clearer.

**Recommended option:** Wait for RES-002 to confirm novelty, then **file a UK patent application (Option B) if the construction is confirmed as novel.** Budget for this in the current financial year. Engage a patent attorney with cryptography experience — the specification must be technically precise.

**Caveat:** A solicitor must advise on this, not me. I can tell you why it matters; I cannot draft the claims.

---

### Decision 5 — What is your personal go/no-go gate for Heirlooms?

**Why it matters:** Heirlooms currently has zero revenue and one external user. The question of how much more time to invest before evaluating whether to continue is a personal financial decision with direct retirement implications. Continuing indefinitely without a clear gate is the worst outcome — it consumes time without forcing the honest question.

**Options:**

- **Option A — Continue until M13 ships (estimated ~12 months), then evaluate:** Heirlooms achieves its full planned product scope (sealed capsules, posthumous delivery) before any commercial evaluation. This is the "build the whole product first" approach.

- **Option B — Set a revenue gate at M12:** Establish a clear target — for example, "100 paying users within 6 months of M12 launch" — and treat missing it as a signal to pivot or wind down.

- **Option C — Set a time gate regardless of product state:** Decide now that you will make an honest go/no-go assessment at a specific date (e.g. May 2027, 12 months from now), whatever the product state.

**Trade-offs:**
- Option A is the most satisfying intellectually but risks 12+ more months of time investment before asking the commercial question.
- Option B aligns the gate with the product's first meaningful commercial feature (scheduled delivery is the feature that makes Heirlooms emotionally distinctive and saleable).
- Option C is the most personally protective but may force a premature decision if product development is on track.

**Recommended option:** A hybrid of B and C: **set a time gate of May 2027 (12 months from now) AND a revenue gate of £500/month recurring by the time M12 ships.** Either condition being badly missed triggers a formal re-evaluation. Neither condition requires Heirlooms to be a large company — just that commercial viability is being demonstrated.

**This is the decision that most requires Bret's personal input.** How much more time are you willing to invest, and under what conditions? There is no right answer here — only an honest one.

---

## Questions for Bret

These are the follow-up questions not covered by the Phase 1 questionnaire that I need answered before the next assessment.

1. **Questionnaire priority:** When will you complete the questionnaire? I cannot move beyond illustrative ranges without it.

2. **Company structure:** Is Heirlooms operated through a limited company? What is the company name?

3. **Income:** What is your current total annual income, and how much of it is from sources outside Heirlooms?

4. **Pension actual values:** What is the current combined value of all pension pots? Even a rough figure changes the analysis significantly.

5. **Time commitment:** Approximately what percentage of your working week is currently Heirlooms versus other income? This affects the opportunity cost calculation.

6. **Exit preference:** Is your preference for a trade sale, an acqui-hire, or something else? This shapes whether IP protection or team building is the higher priority today.

7. **Health and timeline:** Is there any health consideration or personal deadline (a child reaching a milestone age, a partner's plans, a geographic move) that creates urgency in the planning that I should know about?

8. **Existing advisers:** Do you have a financial adviser or accountant already? If so, I want to complement their work, not duplicate it.

9. **IP protection:** Has any IP protection work been done — trademark, patent application, formal trade secrets register? And has a solicitor confirmed that IP is correctly assigned to the company rather than Bret personally?

10. **Marketing Director activation:** The Marketing Director has been established as a persona but has produced no briefs yet. The revenue scenario modelling in this assessment is built on logical inference rather than market analysis. When will the Marketing Director be engaged to produce a TAM, competitive positioning, and revenue model brief? That brief will materially change the bull scenario assessment.

---

## PA Summary

**For:** PA (cross-session memory)  
**Date:** 2026-05-16

**Task completed:** RET-001 — Initial Retirement Assessment. Phase 1 (questionnaire), Phase 2 (intelligence gathering), and Phase 3 (assessment brief) all complete.

**Key findings:**

- Heirlooms has genuine, defensible IP value — the versioned envelope format, DEK-per-file model, multi-path capsule design, and potential window capsule construction are legitimately novel. If IP is protected and the product reaches commercial scale, an exit in the £1m–£10m range is plausible at the 3–5 year horizon.

- The assessment is built entirely on assumptions because Bret has not yet completed the questionnaire. **The single most important action is completing the questionnaire.** All financial modelling is illustrative until then.

- The five key decisions identified are: (1) Is Heirlooms primary or supplementary to retirement? (2) Seek external investment or stay bootstrapped? (3) Is the pension on track? (4) Should a patent be filed on the window capsule? (5) What is the go/no-go gate?

- The highest-severity near-term risk is the single-founder dependency: Heirlooms has bus factor 1 and no IP formally protected. This must be addressed before the 3-year horizon.

- The P-256 / HNDL cryptographic risk is a medium-term threat (2027–2032 window) but also a near-term marketing opportunity: completing the PQC migration plan before competitors would be a significant differentiator for both users and acquirers.

- No marketing briefs exist yet (`docs/marketing/` is empty). Revenue scenario modelling is based on logical inference. Engaging the Marketing Director to produce a TAM and competitive positioning brief is the highest-priority next step after the questionnaire.

**Decisions needed from Bret:**
1. Complete the questionnaire (Section 1–13 of RET-001-questionnaire.md)
2. Make Key Decision 1 (primary vehicle vs. supplement) — this changes the planning posture
3. Confirm company structure and IP assignment status
4. Decide whether to file a UK trademark before the end of the current financial year

**Follow-on tasks to create:**
- Once questionnaire is complete: RET-002 (updated assessment with actual figures)
- Once RES-002 is complete: evaluate patent filing decision
- Once Marketing Director produces first brief: RET-003 (integrated commercial and retirement assessment)
