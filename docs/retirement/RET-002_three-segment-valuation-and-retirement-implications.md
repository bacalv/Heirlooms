# RET-002 — Three-Segment Valuation and Retirement Implications

**Prepared by:** Retirement Planner  
**Date:** 2026-05-16  
**Status:** First-pass assessment — market data sourced; Bret's personal financial data still outstanding (questionnaire not completed)  
**Audience:** Bret (personal — not a company document)  
**Inputs:** RET-001, LEG-001, docs/PA_NOTES.md, docs/ROADMAP.md, web research (see citations throughout)

---

> **Important caveat throughout this document:** Bret has not yet completed the Phase 1 questionnaire. All personal financial figures remain assumed from RET-001. The market sizing and comparable exit data in this brief are sourced from web research but carry their own material uncertainty — market reports in emerging segments frequently overstate TAM, and exit multiples from comparable companies apply imperfectly to a pre-revenue UK bootstrapped startup. Where data is speculative, this is flagged explicitly. Do not treat any number in this document as a forecast.

---

## Market sizing

### Memory Archive

**The reframe matters for TAM.** The original "grief and legacy" framing addresses a real but emotionally deferred need — people know they should plan for death and don't. The 2026-05-16 session introduced a corrective: Heirlooms is primarily about *time*, not death. A video for a daughter's 18th birthday, sealed when she is 8, lives in the same product as a posthumous letter, and most users will plant the former far more often than the latter. This reframe opens TAM materially.

**TAM (global):** The digital health and personal data vault category broadly defined is USD 18.68 billion in 2025, growing at 15.94% CAGR. That is too broad a proxy — it includes fitness trackers, medical records apps, and wearables. A tighter proxy is the consumer subscription digital media/legacy category. No clean global TAM figure for "time-locked personal media storage" exists in published research; this is an emerging segment without standard classification.

A practical estimate built from first principles: in the UK alone, approximately 27 million households with at least one smartphone. If 3–5% of households actively organise and archive personal media in a privacy-first product (a credible SAM for an established product in this space, based on comparable penetration in journaling, photo-book, and ancestry-DNA apps), that is 800,000–1,350,000 UK households. At £8–£12/month, UK SAM is approximately £77m–£194m ARR. Global SAM (UK × 10–15 for English-speaking markets + Western Europe) is approximately £770m–£2.9bn ARR. **These are ceiling figures — SOM for a bootstrapped pre-revenue product is a small fraction of SAM.**

**Realistic SOM (5-year horizon):** 10,000–50,000 paying users at £8–12/month = £960,000–£7,200,000 ARR. This is the operational target range, not the TAM.

**Comparable consumer subscription exits:**
- *Ancestry.com* (Blackstone acquisition 2020 at $4.7bn; now being positioned at ~$10bn targeting ~10x revenue): at $1bn+ revenue, 10x is plausible for a category leader with deep moat. Not directly comparable to Heirlooms at its current stage, but illustrates the ceiling for a successful product in an adjacent space.
- *Headspace* (merged with Ginger 2021 at ~$3bn combined valuation): at ~$50m revenue, implied ~60x revenue multiple. **This is a 2021 peak-froth multiple and should not be used for planning.** Headspace's multiple collapsed rapidly with the market. Treat as a historical outlier.
- *23andMe*: peaked at $6bn (2021 SPAC), collapsed to $305m asset sale (2025). **An instructive failure case.** 23andMe built a massive consumer database, then could not find a sustainable revenue model. The privacy-sensitive data angle created both the product and its undoing — regulatory, consumer trust, and monetisation challenges compounded.
- *Calm* (meditation app): LTV ~$200/subscriber, CAC ~$40, LTV/CAC of ~3:1 — a well-run consumer subscription. Not publicly exited; last known valuation ~$2bn at ~$150m revenue = ~13x revenue.

**Applicable multiple range for planning (2025–2026 market conditions):** Median SaaS/subscription revenue multiples have contracted significantly from 2021 peaks. Current (Q1 2026) median is approximately 3.1x ARR for private companies; category-leading consumer subscription apps with strong retention and defensible positioning trade at 5–15x ARR in strategic acquisitions. For a privacy-first, cryptographically differentiated product, I apply a **5–12x ARR strategic premium** — but only at the point of meaningful scale (1,000+ paying users, demonstrable retention).

**Speculative flag:** Without paying users, these multiples are illustrative. Applying a multiple to zero revenue produces zero. The single most important near-term task for Memory Archive value creation is getting paying users — even at small scale.

---

### Care & Consent

This is the highest-novelty segment and the most commercially underexplored. It deserves the most careful sizing.

**The problem Heirlooms is uniquely placed to solve:**  
Approximately 900,000 people in the UK currently live with dementia. There are approximately 700,000 primary carers. LPA registrations have more than doubled in four years — from 691,746 in 2020/21 to 1,367,053 in 2024/25, with 28% year-on-year growth in 2023/24. A digital-first LPA system (Powers of Attorney Act 2023) is being modernised. This is a massive, growing, and structurally underserved population.

The current landscape for this population is: GPS trackers (hardware, relatively cheap, privacy-light), medical alert buttons (hardware), care management apps for care homes (B2B, institutional), and very little in the consumer / family-facing trust technology space. None of the current offerings provide cryptographically timestamped consent, E2EE location history, or an LPA-aligned consent revocation mechanism. The specific combination Heirlooms offers — E2EE monitoring, consent established and verifiable before capacity is lost, consent revocable while capacity is retained — has no direct comparable product.

**UK market TAM:**  
- 700,000 primary carers × £8–15/month family subscription = £67m–£126m/year UK TAM (consumer tier)
- Care home licences: approximately 15,600 care homes in England. A SaaS licence at £100–£300/month/home = £18.7m–£56m/year UK institutional TAM (speculative — no comps for this specific product category)
- NHS/insurance channel: highly uncertain; regulatory pathway matters here (see Risk section)
- **Total UK TAM (consumer + institutional): £86m–£182m/year** — a meaningful, addressable segment

**Global context:**  
Global dementia GPS tracker market is estimated at approximately $1.5bn (2024), growing at ~18% CAGR. Dementia care products globally exceed $25bn. Neither figure maps cleanly to Heirlooms' specific offering (which is software, not hardware, and focuses on consent and LPA alignment rather than location alone), but they establish that the addressable population is large globally and that care technology is a well-funded category.

**Comparable exits in the care technology space:**  
No clean exit comps exist for an E2EE consent + LPA monitoring product — this specific construction does not yet exist as a standalone company. Adjacent comparables:
- Care management SaaS platforms (B2B) typically sell at 4–8x ARR.
- Digital health software exits in 2025 averaged approximately $12.6bn globally across all sub-sectors; the Health Management Solutions sub-sector (which Care & Consent most resembles) accounted for ~16% of exit value.
- UK healthtech was valued at £32bn by end of 2024, with 168 companies having achieved exits.

**E2EE / LPA premium:** I believe this is real and material for the right acquirer. Candidate strategic acquirers for a Care & Consent product include: care home groups (Bupa, HC-One, Barchester), insurance companies (Vitality, Aviva) building elder-care propositions, law firms or will-writing platforms (Co-op Legal, Farewill) seeking digital consent infrastructure, NHS digital arms, and — potentially — financial services companies with LPA-adjacent products. A strategic acquirer in this space who values the cryptographic consent infrastructure could apply a **6–15x ARR multiple** on a product with demonstrable family traction and growing institutional take-up.

**Speculative flag:** This segment has the highest theoretical value and the highest execution uncertainty. Getting from the current Memory Archive product to a product that care home groups and insurance companies will licence requires product development, regulatory clarity (see Risk section), and a sales/partnerships capability Bret does not currently have. The TAM numbers above are ceilings; the realistic path to them is at minimum 3–5 years from now.

---

### Experience

**The segment definition:**  
Chained capsule campaigns for brands, publishers, ARG designers, and educational institutions. White-label / org-flavoured app. "First solver wins" competitive delivery. API tier. QR-code promotional distribution. The product's cryptographic novelty (conditional delivery, chained unlock) is directly applicable to immersive marketing campaigns, editorial puzzles, and educational experiences.

**Market sizing:**  
- *ARG / alternate reality game platforms market:* USD 1.34bn globally in 2024, growing at 14.7% CAGR, projected to reach USD 4.31bn by 2033. Marketing & advertising is the fastest-growing segment, driven by brands seeking immersive engagement.
- *Experiential marketing services market:* USD 55–68bn globally in 2024 (sources vary by methodology). Global experiential marketing spend reached USD 128.35bn in 2024 — the broader spend including events, live activations, and digital immersion.
- The Experience segment taps the *campaign execution layer* of this market — not the full experiential market, but the digital/interactive campaign tier where a publisher, brand, or educational institution commissions a cryptographic-delivery experience.

**Realistic addressable market for Heirlooms:**  
A white-label campaign product pitched at medium publishers, brands, and educational institutions. At the low end: 5–20 campaigns/year at £5,000–£20,000 per campaign = £25,000–£400,000/year (too small for an exit story on its own). At the mid-tier: 50–200 campaigns/year at £10,000–£50,000 = £500,000–£10m/year. At the platform licensing tier: 10–50 institutional licensees at £10,000–£100,000/year = £100,000–£5m/year. Combined, a realistic 5-year revenue ceiling for the Experience segment as a standalone is approximately £2m–£12m ARR — not enormous, but strategically interesting as a multiplier on the core product.

**Comparable API/platform exits:**  
- *SendGrid* (Twilio acquisition 2019): $144m revenue, acquired for $2–3bn = 14–20x revenue. SendGrid was a mature, high-scale B2B API platform serving 74,000 customers. Not directly comparable to Heirlooms' scale, but establishes the ceiling for a well-positioned API infrastructure business.
- *Twilio itself* at acquisition time was valued at 13x revenue.
- *Private SaaS companies* in 2025 trade at median 3.1x ARR; high-growth platform businesses with strong NRR (net revenue retention) trade at 5–15x ARR in strategic M&A.

**Speculative flag:** The Experience segment is the most speculative of the three. It requires a go-to-market motion — finding publishers, brands, and ARG designers — that does not currently exist and that Bret cannot execute solo. The cryptographic novelty is genuinely useful for this use case, but the sales and marketing investment required to convert that novelty into ARR is substantial. This segment is best treated as a *future multiplier* on the platform value, not a near-term revenue driver.

---

## Revenue model assessment by segment

### Memory Archive revenue model

**Recommended model:** Freemium consumer subscription with a free storage tier (aligned to the existing friend-tester / PLG mechanic) converting to paid at a meaningful storage or feature threshold.

| Tier | Price | Access |
|---|---|---|
| Free | £0 | Limited uploads (e.g. 10 items), 1 active capsule, no scheduled delivery |
| Personal | £5–8/month | Full upload storage, up to 5 capsules, scheduled delivery |
| Family | £10–15/month | Multi-user, shared plots, up to 20 capsules, posthumous delivery |

At £8/month average (blended across Personal and Family tiers), and a 2.5% conversion from free to paid (consistent with freemium consumer app benchmarks), a base of 40,000 free users would produce 1,000 paying users = £96,000/year ARR. To hit £1m ARR requires either higher conversion or a user base of approximately 400,000 free users — the latter requiring serious marketing investment.

**LTV modelling:**  
- ARPU: £96–£180/year (£8–15/month)
- Expected churn for a sticky legacy product: 8–15%/year (lower than typical subscription apps because the emotional content creates lock-in)
- LTV at 10% annual churn and £120/year ARPU: £1,200/subscriber
- CAC target for an LTV/CAC ratio of 3:1: £400 maximum
- Referral / PLG-driven CAC (friend-tester model, chained capsule viral loop): potentially £20–80 per acquired user — **this is the correct cost model to pursue**

**Verdict:** Consumer subscription is the right model for this segment. The friend-tester and chained capsule viral mechanics are the path to unit economics that work without major paid acquisition spend. The pricing sensitivity analysis should be tested at first-revenue stage.

---

### Care & Consent revenue model

Three models are plausible; they are not mutually exclusive:

**Model A — Consumer family subscription:**  
A premium tier of the base product. £10–20/month for a "Care & Consent" add-on or family plan. This has the lowest sales cost — it is a product feature, not a separate sales motion. Revenue per user is modest; scale comes from the same consumer channel.

**Model B — Institutional B2B (care homes, law firms, insurance):**  
Annual SaaS licence. Care homes at £50–200/resident/year or £500–3,000/home/month. Law firms and will-writing platforms at £500–5,000/month per firm. Insurance companies as embedded product partners (white-label, revenue share). This is higher ACV and more predictable revenue — ideal for valuation purposes — but requires a B2B sales capability and significant regulatory clarity before institutional buyers will commit.

**Model C — Insurance / NHS integration:**  
Commission or partnership model. An insurance company embeds the Care & Consent product as a feature for LPA holders and pays Heirlooms a per-user annual fee (£30–100/user/year). At 50,000 embedded users, this is £1.5m–£5m/year. This is a long-cycle sales motion (12–18 months to close a strategic partner, then rollout); not a near-term revenue source.

**Recommended path:** Start with Model A (consumer tier — lowest friction, quickest revenue), validate product-market fit, then pursue selective B2B institutional partners who can act as reference customers. Do not pursue NHS or insurance channel without at minimum a reference care home customer and regulatory clarity on the MHRA question.

**Predictability premium:** B2B SaaS revenue is valued at 2–3x the multiple applied to equivalent consumer subscription revenue by most acquirers, because churn is lower and contracts are longer. If Heirlooms can demonstrate 3–5 institutional licence customers, even at modest ACV, the valuation story for Care & Consent improves materially.

---

### Experience segment revenue model

**Recommended model:** Three tiers, in order of commercial maturity:

1. **Campaign fees (near-term):** Per-campaign pricing for bespoke brand or editorial experiences. £5,000–£25,000 per campaign. Manually sold. This is the fastest path to cash, but it is services revenue (low multiple) and not scalable alone.

2. **White-label platform licence (medium-term):** Annual SaaS licence for organisations wanting an owned-brand instance. £10,000–£60,000/year depending on usage volume and org size. A medium publisher (50,000 subscribers) might pay £15,000–£30,000/year. An educational institution £5,000–£15,000/year. This is the model that creates exit-worthy recurring revenue.

3. **API tier (long-term):** Usage-based API access for developers and platforms building on the chained capsule primitive. Priced per-capsule-created or per-delivery-event. This is the highest-multiple business model if Heirlooms becomes an infrastructure layer.

**Revenue projection (5-year, optimistic):** 50 white-label licences × £20,000/year = £1m ARR. 200 campaigns/year × £10,000 = £2m campaign revenue. API tier: speculative at this stage. Combined: £3m/year is an optimistic ceiling; £500,000–£1.5m/year is a base planning assumption.

---

## Combined model — exit story analysis

**Does three segments increase or decrease exit attractiveness?**

This is the central strategic question, and the answer is nuanced: **it depends which segments have demonstrated revenue, and whether the acquirer is strategic or financial.**

**The case that three segments increase exit value:**

1. *Addressable market size is substantially larger.* A single-segment Memory Archive product might be acquired by an estate planning company or a legal tech firm. A three-segment product is also interesting to care technology companies, insurers, media groups, brand agencies, and cryptographic infrastructure buyers. Larger set of potential acquirers increases competition for the asset and typically increases price.

2. *The IP cross-cuts all three segments.* The window capsule construction (LEG-001 — likely patentable) underpins all three use cases. A single patent defends the core primitive across Memory Archive (personal capsules), Care & Consent (consent with revocation), and Experience (chained unlock campaigns). This is leverage: one patent, three revenue streams.

3. *The Care & Consent segment is, by far, the largest addressable market in structural terms.* 700,000 UK carers, 1.37 million LPA registrations per year, and a global dementia economy measured in tens of billions. Even modest penetration of this market dwarfs the Memory Archive segment.

**The case that three segments complicate the exit:**

1. *No clear identity.* An acquirer doing a 30-minute initial review wants a single sentence: "what is this company?" Three segments with different buyers, different revenue models, and different regulatory profiles make that harder. "We are a cryptographic time-capsule platform" is a single sentence. "We are a personal archive, a dementia monitoring app, and a brand campaign platform" is three sentences — and three due diligence processes.

2. *Focus cost.* Bret is one person. Building and commercially validating three segments simultaneously is not feasible. Trying to do so may mean none of them reach the scale that creates exit-worthy traction.

3. *Regulatory risk is concentrated in Care & Consent.* If Care & Consent is classified as a medical device (MHRA, see Risk section), the regulatory burden is substantial. That risk applies to the entire company if Care & Consent is a named segment, even if Memory Archive and Experience are unaffected.

**My recommendation on sequencing:**  
Memory Archive is the foundation — build it, get paying users, establish the brand. Care & Consent is the highest long-term value direction; approach it deliberately with a consumer tier first, institutional B2B second. Experience is optional for the exit story unless it develops naturally (e.g. a publisher or brand approaches Heirlooms) — do not actively build a sales motion for it while solo. In a 3–5 year exit story, present Memory Archive + Care & Consent as the core business and Experience as a demonstrated "platform extensibility" proof point.

---

## IP portfolio valuation

### Window capsule patent (LEG-001)

**Status (from LEG-001):** Likely patentable. The specific combination of tlock/drand IBE lower bound + XOR DEK blinding + Shamir threshold deletion upper bound has no active patent found. The post-*Emotional Perception* [2026] UKSC 3 ruling lowers the UK patentability threshold for computer-implemented inventions. LEG-001 recommends UK filing within 8 weeks, with PCT decision deferred to 6-month mark.

**If patent granted — what licensing revenue is plausible?**

Licensing a cryptographic method patent is unusual in the consumer product world but well-established in the enterprise security, semiconductor, and platform technology industries. Realistic scenarios:

- **Defensive licence (most likely):** The patent deters competitors and supports acquisition value. No royalty income in this scenario — the value is entirely in the acquisition premium it creates. An acquirer in the security or enterprise space would pay a documented premium for a granted patent covering a novel cryptographic primitive; industry estimates suggest patents add **20–40% uplift** to M&A valuations in technology deals.
- **Cross-licence:** Heirlooms grants a licence to a larger company in exchange for IP access or a commercial partnership. No cash, but strategic value.
- **Royalty licence:** If another consumer or enterprise product independently implements a construction that reads on the Heirlooms patent, royalties are possible. This requires active enforcement, which is expensive. Realistic royalty rate for a software patent in the security space: 0.5–3% of infringing product revenue. The enforcement costs for a UK startup pursuing patent royalties against large companies make this scenario commercially viable only after significant scale or if Heirlooms is part of a larger entity.
- **Patent sale:** If Heirlooms needs liquidity before it reaches exit scale, the patent could be sold independently to a patent holding entity or a strategic buyer. Cryptographic method patents in active prosecution sell at a wide range: **£50,000–£500,000 for a narrow patent; £500,000–£2,000,000 for a patent with broad claims and strong prior art differentiation.** The blockchain/crypto patent market reached $703m in 2025 globally; Heirlooms' construction is narrower than blockchain but arguably more novel.

**My best estimate of the window capsule patent's contribution to acquisition value:**

Assuming a granted UK patent (3–5 years to grant; priority date from filing matters more):
- *Conservative:* £75,000–£200,000 incremental to acquisition price (defensive value only; narrow claims; no demonstrated licensing)
- *Central:* £200,000–£600,000 (granted UK patent with PCT application pending; documented commercial relevance in Care & Consent and Experience segments; referenced in due diligence)
- *Optimistic:* £600,000–£2,000,000 (granted UK + US/EU patent; demonstrated licensing traction; acquirer is a security or enterprise company that specifically values the primitive for their own product roadmap)

**Speculative flag:** Patent valuation is notoriously uncertain before licensing revenue or demonstrated enforcement. The conservative estimate is where I'd anchor for planning purposes.

### Chained capsule patent (LEG-002, pending assessment)

**Status:** LEG-002 has not yet been assessed. The chained capsule / conditional delivery construction (where one capsule's delivery unlocks another, enabling branching narrative structures) is commercially relevant to the Experience segment. Its patentability depends on a prior art search that has not been completed.

**Preliminary assessment:**  
Conditional delivery and branching content unlock are concepts with prior art in DRM (digital rights management) and interactive fiction. However, the specific application of cryptographic chaining — where a recipient's possession of one capsule's key material is a condition for accessing the next — may have a more defensible narrow claim than the envelope format or tag scheme. This is worth investigating via the same patent attorney engagement recommended in LEG-001.

**Speculative contribution to portfolio value if patentable:** An additional £100,000–£400,000 incremental to the acquisition price, on top of the window capsule patent. The two together begin to constitute a credible cryptographic IP portfolio.

### Combined IP portfolio value

Combining both patents (assuming both granted):
- *Conservative:* £125,000–£400,000 incremental to acquisition value
- *Central:* £300,000–£900,000 incremental to acquisition value
- *Optimistic:* £700,000–£2,400,000 incremental to acquisition value

**Important nuance:** These figures represent the *incremental contribution of the IP to an exit price*, not standalone IP asset values. The IP does not generate value in isolation — it must be embedded in a product with users and revenue. The IP's value is maximised by filing early (establishing priority date), documenting thoroughly, and presenting it clearly in acquisition due diligence. Without these steps, even a granted patent adds little to a trade sale.

**Recommended immediate actions from IP perspective:**
1. Commission UK patent filing on the window capsule (budget £10,000–£15,000 attorney fees) — this is the time-sensitive item
2. Commission LEG-002 assessment on chained capsule construction
3. Confirm IP ownership chain (is Heirlooms incorporated? contractor IP assignments in place?)

---

## Friend-tester / PLG financial model

### Current state

One external tester onboarded (May 2026), in exchange for real-world testing in return for free storage. This is the seed of a product-led growth model: acquire users at near-zero CAC, extract feedback, convert a fraction to paying.

### CAC analysis

The friend-tester model has two components of CAC:
1. **Direct cost:** Free storage (bandwidth + GCS storage costs). At current GCS pricing (~$0.02/GB/month), a user storing 10GB of photos/video costs approximately £0.20/month. Even at 1,000 testers storing 10GB each, the cost is £200/month. **Economically negligible at this scale.**
2. **Indirect cost:** Bret's time in onboarding, support, and feedback processing. At even a conservative valuation of Bret's time at £50/hour, 2 hours/tester = £100 opportunity cost per tester.

The current model implies a CAC of approximately **£100–£200 per acquired user** (mainly time cost). At an LTV of £1,200 (as modelled above), LTV/CAC is 6:1–12:1. **This is an excellent ratio** — well above the 3:1 benchmark.

**However:** This CAC is not scalable. Bret cannot personally onboard 10,000 users. The PLG model only works if the product itself drives acquisition (chained capsule viral loop, Care & Consent family network effect, QR-code Experience campaigns) with minimal Bret involvement per acquisition.

### Viral coefficient (K-factor) analysis

Two natural viral loops exist:

**Loop 1 — Chained capsule recipient becomes a user.** A capsule recipient receives a notification that they have been named as the recipient of a sealed capsule. To view it (when delivered), they need an Heirlooms account. If even 20% of capsule recipients become users, and each user plants an average of 3 capsules for distinct recipients, the K-factor is 0.6. A K-factor below 1.0 does not produce viral growth on its own — it requires external acquisition to seed the loop. At K = 0.6, every 100 acquired users produces an additional 150 from the loop (not 100, because the loop repeats). This is a *multiplier on paid acquisition*, not a replacement for it.

**Loop 2 — Care & Consent family trust network.** When a primary carer sets up Care Mode for a parent, the parent and possibly other family members (siblings, a sibling's spouse) are pulled into the product as co-carers or secondary consent holders. A family unit of 4–6 people produces 3–5 additional users per acquisition. At this conversion rate, Care & Consent could have a K-factor of 0.6–0.8 per *family*. Combined with carer-to-carer word of mouth (carer networks are well-documented community-based support systems), the K-factor for Care & Consent may approach 0.9–1.1 — the threshold for organic growth.

**Speculative flag:** These K-factor estimates are theoretical. No product with this specific mechanic has published real-world K-factor data. Real K-factors for consumer apps average 0.3–0.5; a K-factor above 0.8 would be exceptional and unlikely without deliberate referral mechanics (in-product share prompts, referral incentives).

### PLG investability assessment

The friend-tester model is currently:
- **CAC:** Low (time-heavy, not capital-heavy) — good
- **LTV/CAC:** Estimated 6:1–12:1 — excellent if it scales
- **Scalability:** Low — dependent on Bret's time; not yet automated

For the PLG model to be investable (i.e., to support a fundraising conversation or to be cited favourably in an acquisition due diligence), Heirlooms needs:
1. First paying users (even at £1/month — the act of paying validates willingness-to-pay)
2. Demonstrated churn data (even with 10 subscribers, a 6-month churn rate is credible data)
3. A referral mechanic that is in-product, not dependent on Bret's personal network

The friend-tester mechanic, formalised as "free storage in exchange for active feedback plus referral," is a reasonable near-term PLG approach. The referral mechanic is the gap.

---

## Risk assessment

### Care Mode regulatory risk

**The central question:** Is Care & Consent, specifically the E2EE monitoring and consent management component, a medical device under MHRA / UK MDR?

**Assessment:** This is genuinely uncertain, and the answer depends on product design and marketing claims.

The MHRA published new guidance on Digital Mental Health Technologies (DMHTs) in February 2025. The key test is whether the software is "intended by the manufacturer to be used for a medical purpose" — specifically diagnosis, prevention, monitoring, prediction, prognosis, treatment, or alleviation of disease or mental health condition.

**If Heirlooms' Care & Consent marketing claims are limited to:** "helping families coordinate care, share location data, and manage consent" — this is likely a **non-medical device** (a general-purpose communication and data management tool). Many family GPS apps and care coordination tools operate in this space without MHRA classification.

**If claims extend to:** "monitoring progression of dementia symptoms," "detecting changes in behaviour consistent with capacity loss," or "supporting clinical decision-making" — this is **likely a medical device (Class I or IIa SaMD)** under UK MDR.

**The regulatory cost of getting it wrong:**  
- Class I SaMD: self-certification, modest cost and timeline (3–6 months, £10,000–£30,000 in compliance costs)
- Class IIa SaMD: requires a UKCA-certified notified body assessment. Timeline: 12–24 months. Cost: £50,000–£200,000+ in compliance, testing, and documentation

**My recommendation:** Design the Care & Consent product explicitly to avoid triggering medical device classification. The legal and financial cost of inadvertent medical device classification at a pre-revenue stage is disproportionate. The product should focus on consent management, secure communication, and location awareness — not clinical monitoring or symptom tracking. This is also better product design: Heirlooms is not a medical device company and should not position itself as one. Leave the clinical layer to established medical device companies.

**Residual risk:** Even with careful positioning, if Care & Consent is adopted by NHS trusts or care home groups, regulators may classify it as SaMD based on *actual use* rather than stated intent. This is a medium-term risk to monitor, not an immediate crisis.

**Valuation impact of misclassification:** If Care & Consent is classified as medical device Class IIa before Heirlooms has revenue or an institutional partner to absorb the compliance cost, it would likely force suspension of the feature, incur £50,000–£200,000 in compliance investment before relaunch, and delay the segment by 18–24 months. This is a material risk to the 3-year exit timeline.

---

### Execution risk across three segments

**The fundamental constraint is Bret's solo capacity.**

Three segments, each requiring product development, go-to-market execution, and customer support, is not achievable by one person simultaneously. The risks:

1. **None of the three segments reaches the scale needed for an exit.** Spreading effort across three commercial directions means no single direction accumulates the user base, revenue, or reference customers that make an exit story compelling. A financially rational acquirer buys a business with demonstrable traction, not three promising concepts.

2. **Opportunity cost of the Experience segment.** The Experience segment requires an outbound sales motion (finding publishers, brands, ARG designers) that is time-intensive and uncertain. If Bret spends 6 months pursuing campaign clients and none convert, that is 6 months not building Memory Archive users or Care & Consent validation.

3. **Care & Consent requires more than product work.** Getting institutional B2B clients (care homes, law firms, insurers) requires sales relationships, legal/commercial contracting capability, and regulatory navigation. None of these can be done solo at the same time as shipping product.

**My recommendation:** Treat the three segments as sequential, not simultaneous. Memory Archive first (product foundation, first paying users, PLG model established). Care & Consent second (product extension, consumer tier first, one or two institutional reference clients as stretch goal within 18 months). Experience as opportunistic (respond to inbound interest; do not invest in outbound sales unless a major brand approaches unprompted).

**The alternative path — external support:**  
Bret's stated preference is bootstrapped, revenue-before-investment. That preference is coherent and protective. But it means the multi-segment risk is real. A single B2B development or partnerships hire — even part-time — could unlock the Care & Consent institutional channel without requiring a VC round. This is worth modelling once Memory Archive has first revenue.

---

## Recommended prioritisation for Bret's timeline

The task brief asks for the single highest-value direction for Bret's personal retirement outcome. Here is my direct assessment.

**The single highest-value direction is Care & Consent.**

Here is why:
1. It addresses the largest addressable market: 700,000 UK primary carers + 1.37m LPA registrations/year, growing at 19–28%/year.
2. It carries a genuine moat: no other consumer product offers E2EE monitoring with cryptographically timestamped, revocable consent aligned to the LPA framework. The window capsule patent, applied to the consent revocation mechanism, is directly relevant.
3. The strategic acquirer universe is large and well-funded: care home groups, insurers, legal tech companies, NHS digital arms, financial services groups. These buyers have the balance sheets to pay meaningful acquisition prices.
4. The institutional B2B revenue model, once established, attracts higher exit multiples than consumer subscription (4–10x ARR vs 3–7x ARR) because churn is lower and contracts are longer.
5. The demographic tailwind is structural and decades-long: UK dementia prevalence is growing; LPA registrations are rising sharply; elder care technology is a well-funded sector.

**However:** Care & Consent cannot be built before Memory Archive. The M12 (scheduled delivery) and M11 (strong sealing) milestones are the cryptographic foundation that Care & Consent requires. The sequencing is correct in the current roadmap — build the foundation, then extend to Care & Consent as a feature extension of the same platform.

**Recommended 3-year prioritisation sequence:**

| Timeframe | Priority | Goal |
|---|---|---|
| Now → M13 (12–18 months) | Memory Archive foundation | Ship M12 + M13. First paying users. UK patent filing (window capsule). |
| 12–24 months | Care & Consent consumer tier | Add geofenced monitoring and consent management as premium feature. Consumer subscriptions. 500–2,000 paying users. |
| 24–36 months | Care & Consent institutional | One or two care home or law firm reference clients on annual licences. Validate B2B pricing. |
| 36+ months | Exit evaluation | At £200,000–£1,000,000 ARR from combined Memory Archive + Care & Consent, initiate structured sale process or strategic partner conversations. |

**Experience segment:** Build only in response to inbound demand. Do not allocate planned development capacity to it while solo.

---

## Exit scenario modelling (updated for three-segment model)

The following revises and extends the RET-001 scenarios to reflect the three-segment commercial model.

### Bear scenario (20% probability, my subjective estimate)

Memory Archive reaches 200–500 paying users but fails to reach product-market fit at consumer scale. Care & Consent is built as a feature but lacks validation (no institutional clients, no consumer traction beyond early adopters). Experience is not pursued. Heirlooms is wound down or kept alive as a side project.

**Proceeds:** IP sale (window capsule patent, if filed) at £50,000–£200,000. No meaningful exit. Time invested is the main cost.

**Retirement impact:** No change from conventional savings trajectory.

### Base scenario (50% probability)

Memory Archive reaches 1,000–5,000 paying users over 3 years (£96,000–£720,000 ARR). Care & Consent consumer tier adds 500–2,000 users. One or two institutional licences at £20,000–£50,000/year. Combined ARR: £200,000–£900,000. Exit via trade sale to estate planning company, legal tech firm, or care technology company.

**Exit multiple:** 5–8x ARR strategic premium (justified by IP, growth trajectory, and strategic fit for acquirer)  
**Gross proceeds:** £1,000,000–£7,200,000  
**After UK CGT** (BADR at 10% on first £1m lifetime gains, 20% above): approximately **£870,000–£5,900,000 net**

At the low end of base: a meaningful improvement to retirement — adds 8–15 years of runway. At the upper end: potentially transformative.

**3-year timing:** This scenario is achievable by May 2029 if the prioritisation sequence above is followed.

### Bull scenario (30% probability)

Memory Archive + Care & Consent together reach 10,000–50,000 paying users and 5–15 institutional licences. Combined ARR: £1,000,000–£5,000,000. Window capsule patent granted (UK, PCT in progress). Experience segment demonstrates traction from 1–2 major brand or publisher clients. Strategic acquirer (care home group, insurer, legal tech) pays a premium for the combined asset.

**Exit multiple:** 8–15x ARR (strategic acquirer with clear IP value and institutional revenue)  
**Gross proceeds:** £8,000,000–£75,000,000  
**After CGT:** approximately **£6,500,000–£60,000,000 net** — though the upper end requires things to go exceptionally well and is aspirational rather than planned for

At the mid-range of the bull scenario (£8m–£20m net), this is transformative: full retirement funded immediately, with material wealth for Bret's family.

**5-year timing:** The bull scenario is more plausible at the 5-year horizon (May 2031) than at 3 years, assuming the prioritisation sequence is followed and Care & Consent achieves institutional traction.

---

## PA Summary

**For:** PA (cross-session memory)  
**Date:** 2026-05-16  
**Task:** RET-002 — Three-Segment Valuation and Retirement Implications

---

### The single highest-value direction for Bret's personal retirement outcome

**Care & Consent is the single highest-value direction** — not because it can be built first, but because it addresses a larger, better-funded, and structurally growing market (700,000 UK dementia carers; 1.37m LPA registrations/year; a global dementia care economy measured in tens of billions), has no direct comparable product (zero competition in E2EE + cryptographic consent + LPA alignment), attracts institutional acquirers with deep balance sheets, and commands higher exit multiples (B2B SaaS) than consumer subscription alone.

Memory Archive must be built first — it is the cryptographic foundation and the first paying-user channel. But Care & Consent is the segment that can make this business genuinely transformative for Bret's retirement.

---

### Do three segments increase or decrease exit attractiveness?

**Net assessment: moderate increase**, but only if sequenced correctly.

Three segments with zero revenue is a story, not a business. Three segments where Memory Archive has paying users, Care & Consent has one institutional reference client, and Experience has one major brand case study is an exit story: "We are a cryptographic time-capsule platform with proven consumer demand, institutional healthcare traction, and demonstrated extensibility into a $55bn experiential marketing market."

The risk is trying to pursue all three simultaneously while solo. The sequencing recommendation (Memory Archive → Care & Consent consumer → Care & Consent institutional → Experience opportunistic) is critical to avoiding the "no traction anywhere" failure mode.

---

### IP portfolio value range

| Scenario | Value |
|---|---|
| Conservative (UK patent granted, narrow claims, no licensing) | £125,000–£400,000 incremental to acquisition price |
| Central (UK + PCT, strong claims, referenced in due diligence) | £300,000–£900,000 incremental |
| Optimistic (UK + US/EU granted, demonstrated licensing or strategic value to acquirer) | £700,000–£2,400,000 incremental |

**Note:** The window capsule patent (LEG-001) is the primary asset. A LEG-002 assessment on the chained capsule construction should be commissioned alongside the LEG-001 attorney engagement — the marginal additional cost is low and the upside is material if chained capsule is also patentable.

---

### Decisions the CTO needs to make to protect or maximise personal value

**Urgent (next 8 weeks):**

1. **Commission the UK patent attorney for the window capsule (LEG-001).** Budget £10,000–£15,000. Target filing by mid-July 2026. This is time-sensitive: no public disclosure has occurred yet, but any move toward open-sourcing, a public blog, or a fundraising pitch changes that. The priority date matters more than the grant date.

2. **Confirm incorporation status.** Is Heirlooms a limited company? If not, incorporate immediately (£50, same-day online). IP assignment from Bret to the company must be in place before patent filing. This is a prerequisite for a clean exit.

3. **Commission LEG-002.** Assess patentability of the chained capsule / conditional delivery construction. Marginal cost if done alongside LEG-001 engagement.

**3–6 months:**

4. **Get first paying users.** Even at a token price. A product with one paying user is worth more in valuation terms than a product with 1,000 free users. The go/no-go gate from RET-001 (£500/month recurring by M12) remains the right anchor.

5. **Design Care & Consent as a non-medical device.** Before building, confirm product design and marketing claims with a regulatory adviser (not expensive — a 1-hour consultation with a digital health regulatory specialist costs £200–£500 and could prevent a £50,000–£200,000 compliance crisis). The product should focus on secure communication and consent management, not clinical monitoring.

6. **Formalise the friend-tester referral mechanic.** In-product referral prompts, a clear offer (e.g. "give one month free, get one month free"), and tracking. This converts the current ad-hoc PLG model into a structured channel.

**12–18 months:**

7. **Make the go/no-go decision (RET-001 framework).** By May 2027: £500/month recurring revenue OR clear institutional pipeline. If neither condition is met, a formal re-evaluation of time allocation is warranted.

8. **Evaluate PCT filing.** Once the UK patent application is filed and the 12-month priority window begins, decide whether to pursue international (PCT) coverage. This decision should align with whether Care & Consent is targeting the US or EU market — if so, PCT is justified; if UK-only initially, defer.

---

*This assessment remains illustrative until Bret completes the Phase 1 questionnaire (RET-001-questionnaire.md). All exit multiples, revenue projections, and CGT calculations are based on market research and assumptions. Bret should consult a tax adviser and solicitor before acting on any exit or IP decisions.*
