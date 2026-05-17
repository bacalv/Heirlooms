# RET-004 — Updated Patent Strategy: Steve's Offer, Undisclosed IP Risk, and Abstract Claim Scope

**Prepared by:** Retirement Planner  
**Date:** 2026-05-17  
**Status:** Final  
**Audience:** Bret (personal — not a company document)  
**Inputs:** RET-001, RET-002, RET-003, LEG-001, RES-001, RES-002, PA_NOTES.md, session conversation 2026-05-17  

---

## Current position summary

Since RET-003, three new factors have emerged that update the patent funding picture and the IP strategy:

1. **Steve's conditional offer:** A retired family friend has offered £5,000 toward the patent, conditional on the initial application being "granted." He agreed the UK national filing (not PCT) is the right first step. Bret would need to fund the initial filing himself.

2. **Questions from Bret on IP risk:** Whether undisclosed similar systems might already exist, whether the patent can be drafted at a more abstract level covering implementations beyond the specific Heirlooms construction, and what the primary market differentiator is.

3. **Three pitch documents produced (PITCH-001/002/003):** At Beginner, Intermediate, and Advanced technical levels, covering applications, differentiator, and patent case. These are available to use with Steve and other potential supporters.

This brief updates my recommendations in light of all three.

---

## IP and asset value assessment

### Has anything changed in the underlying IP value?

No. The window capsule construction remains the primary patentable asset. The valuation range from RET-002 stands:

| Scenario | IP contribution to acquisition value |
|---|---|
| Conservative (UK patent, narrow claims, no licensing) | £125,000–£400,000 |
| Central (UK + PCT, strong claims, referenced in due diligence) | £300,000–£900,000 |
| Optimistic (UK + US/EU, demonstrated licensing or strategic buyer premium) | £700,000–£2,400,000 |

### Abstract claim scope — does it materially change the value estimate?

Yes, modestly upward. The analysis in this session confirmed that there is a viable intermediate level of claim abstraction — covering the combination of *any* distributed randomness beacon (not just drand) with *any* threshold deletion mechanism, via XOR blinding — that would be broader than the specific Heirlooms instantiation and would cover future implementations using different lower-bound primitives (VDFs, witness encryption SNARKs).

If Level 2 claims (intermediate abstraction) survive examination, they extend the patent's defensive perimeter significantly. A competitor who swaps drand for a different distributed beacon while keeping the XOR + deletion structure would still read on the claims. This makes the patent harder to design around and more valuable to acquirers who want clean IP coverage.

**Revised central estimate with broader claims:** £350,000–£1,100,000 incremental to acquisition value.

The attorney should be briefed to draft at all three levels (broad, intermediate, specific) and negotiate for the broadest surviving scope during prosecution.

---

## Steve's offer — analysis and updated funding picture

### The critical ambiguity: what does "granted" mean?

In patent law, "granted" means the patent has been examined and a grant certificate issued. For a UK patent application, this typically takes 3–5 years from the priority date. If Steve means "granted" in this technical sense, his £5,000 arrives in 2029–2031 — too late to contribute to the PCT decision (12 months after priority) or any near-term prosecution costs.

If Steve means "application filed" or "application accepted/published" (which is what most non-practitioners mean), the money could arrive within 6–18 months of filing.

**This ambiguity must be resolved before structuring Steve's contribution.** The conversation Bret has with Steve should establish explicitly: what event triggers the £5,000? Suggested framing: "I'll treat it as 'when the UK application is acknowledged as filed by the IPO' — typically a few months after submission."

### How the offer changes the funding maths

Assuming Steve means "application filed/acknowledged" (optimistic interpretation):

| Item | Cost | Funding |
|---|---|---|
| UK patent filing (attorney + IPO fees) | £10,000–£15,000 | Bret's director's loan |
| Steve's contribution (on filing) | — | £5,000 into company |
| Net Bret exposure | £5,000–£10,000 | — |
| PCT filing (12 months) | £5,500–£9,200 | Partially Steve's £5,000 |

Bret's net personal exposure for the initial filing reduces from £10,000–£15,000 to £5,000–£10,000. If Steve's contribution is timed around the filing confirmation, it arrives before the PCT decision and partially funds it.

If Steve means "actually granted" (technical interpretation), nothing changes in the near-term funding picture. The £5,000 arrives in 2029–2031 and funds prosecution costs at that stage. Still useful; not near-term helpful.

### Recommended structure for Steve's contribution

Regardless of timing, the contribution should be structured as a **loan from Steve to Heirlooms Digital Ltd** — not a personal loan to Bret, and not equity.

Key terms for the loan agreement:
- Lender: Steve [surname]
- Borrower: Heirlooms Digital Ltd (CRN: pending)
- Amount: £5,000
- Interest: 0% (or HMRC official rate, currently 2.25%, if desired)
- Repayment: On the earlier of (a) the first revenue event exceeding £10,000 received by the company, or (b) 5 years from the date of the loan
- Security: None (unsecured creditor)
- Conversion: No equity conversion rights

**Why not equity:** At the current pre-patent, pre-revenue stage, a £5,000 SEIS investment at a realistic pre-money valuation (£75,000–£200,000) would give Steve 2.5%–6.3% of the company. In the base scenario (£1m–£7.2m exit), that stake costs Bret £25,000–£450,000 in forgone proceeds — up to 90x the amount lent. A loan avoids this entirely.

**Why not personal to Bret:** A personal loan to Bret from Steve is a liability on Bret's personal balance sheet regardless of what happens to the company. A company loan is Heirlooms Digital Ltd's liability; Bret loses the loaned amount in a bear scenario but it does not follow him personally.

Legal Counsel should draft a one-page loan agreement from the above terms. CRN must be confirmed before any money changes hands.

---

## Undisclosed prior art — updated risk assessment

The probability of undisclosed parallel development remains the most credible near-term IP risk, assessed as follows:

### Pending patent applications (18-month publication gap)

UK and PCT applications do not publish until 18 months after filing. An application filed in late 2024 or early 2025 on a similar construction would not be publicly searchable until late 2026. This is a real and specific risk.

**Estimated probability:** 10–20% that a pending application covering the combination of tlock/IBE lower bound + threshold deletion upper bound exists but has not yet published. This is based on:
- The recency of drand's production deployment (March 2023): any implementation using drand could not have started before this
- The absence of any published product with this capability
- Large tech companies' aggressive patent filing norms (absence of published patents is a meaningful negative signal)
- The academic community's strong publication norm

**What the attorney should do:** Set up a patent monitoring service in the relevant technology classifications (G09C, H04L9) from the priority date. Newly published applications in these classes should be reviewed quarterly. If a conflicting application publishes within the priority year, there may be options for swearing behind the conflicting application's date or initiating an entitlement challenge — but these are expensive and uncertain remedies. The best protection is filing first.

### Independent startup development

The most credible undisclosed risk is a small startup developing a similar construction quietly, without public product launch or patent filing. This is unknowable, but the combination of drand's novelty, the absence of any product announcement, and the niche academic community working in this space makes it unlikely that such a startup is far ahead.

**If it exists, the priority date determines priority.** Filing now takes precedence over any work that has not yet been filed. This is the strongest argument for moving quickly.

---

## Does the pitch to Steve change how we should think about the patent value?

Yes, in one important way.

The process of articulating the pitch documents (PITCH-001/002/003) has clarified that the window capsule construction has strong, concrete applications across five sectors beyond personal consumer use: care technology, mental health advance care, examination integrity, enterprise governance, and journalist protection. These are all sectors with institutional buyers, recurring revenue potential, and patent licensing relevance.

This breadth of application strengthens the IP value case on two dimensions:
1. The patent's commercial relevance to multiple acquirer categories is documented — due diligence will find it in more than one market context
2. The broader the commercial application, the stronger the argument for broader claim scope during prosecution

From a retirement planning perspective, the applications analysis has raised my confidence in the central scenario. The addressable markets outside personal consumer use (particularly Care & Consent and examination integrity) have institutional buyers with balance sheets that dwarf the consumer subscription market. The IP is more broadly applicable than a narrow consumer-product patent, and that should be reflected in how the attorney approaches claim drafting.

---

## Revenue and exit scenario modelling

No material change from RET-002. The scenarios remain:

**Bear (20%):** No meaningful commercial scale. IP sale at £50,000–£200,000. No retirement impact.

**Base (50%):** Memory Archive 1,000–5,000 paying users; Care & Consent consumer tier; one or two institutional licences. Combined ARR £200,000–£900,000. Exit 5–8x ARR. Gross proceeds £1,000,000–£7,200,000. Net after CGT (BADR at 10% on first £1m, 20% above): approximately £870,000–£5,900,000. Timeline: achievable by May 2029.

**Bull (30%):** 10,000–50,000 paying users; 5–15 institutional licences; granted UK patent with PCT in progress; Experience segment traction. ARR £1,000,000–£5,000,000. Exit 8–15x ARR. Gross proceeds £8,000,000–£75,000,000. Timeline: more plausible at 5 years (May 2031).

The patent's contribution to these figures remains the same: £125,000–£400,000 conservative, £300,000–£900,000 central, upward-revised to £350,000–£1,100,000 if intermediate abstraction claims survive prosecution.

---

## Key risks to retirement value

No new risks identified since RET-003. Existing risks in priority order:

1. **Undisclosed competing patent application (highest urgency):** File before any competing application publishes. Every week of delay is uninsured.
2. **JUXT consent sequencing:** JUXT outside interests declaration (LEG-005) should be sent the moment CRN is received. Patent should not be filed until JUXT consent is in hand, to avoid IP ownership ambiguity.
3. **Steve's "granted" condition ambiguity:** Resolve before money changes hands. If the condition is "actual grant," Steve's contribution is misaligned with near-term cash flow needs.
4. **Care & Consent MHRA classification:** Design the product to avoid SaMD classification. This risk is unchanged and manageable by product design choices.
5. **Solo execution capacity:** The three-segment risk remains. Memory Archive first; Care & Consent second; Experience opportunistic.

---

## Recommended actions

### Immediate

1. **Resolve Steve's "granted" condition.** Establish explicitly what event triggers the £5,000. Suggested trigger: "UK patent application acknowledged as filed by the UK IPO." Get this agreed verbally before the loan agreement is drafted.

2. **Commission the loan agreement.** Once CRN is received and Steve's trigger condition is agreed, ask Legal Counsel to draft a one-page loan agreement on the terms above. This is a short document and should not take more than a day to prepare.

3. **Commission the patent attorney.** CRN is pending (up to 2 working days from 2026-05-17). The moment CRN arrives: sign the IP assignment deed (LEG-004), send the JUXT outside interests declaration (LEG-005), and engage the patent attorney. The 8-week window to mid-July 2026 is now under 9 weeks from today.

4. **Brief the attorney on abstract claim strategy.** Provide PITCH-003 and RES-002 as background. Instruct explicitly to draft at three levels: broad independent claim covering any distributed randomness beacon + threshold deletion + XOR blinding; intermediate dependent claims on the IBE-specific instantiation; narrow specific claims on the exact drand + Shamir construction. Negotiate for the broadest surviving scope.

5. **Use PITCH-001 with Steve.** PITCH-001 is written for a non-technical reader and makes the case without an explicit funding ask. Share it with Steve before the conversation about the loan, so he can read it in his own time. Follow up in person.

### 1-year

6. **PCT decision at 12 months.** Once the UK application is filed, a 12-month window opens. Evaluate PCT coverage based on: (a) whether Care & Consent is targeting the US or EU market, (b) whether any acquirer conversations have indicated international IP coverage matters, (c) whether Steve's contribution and initial product revenue can fund the £5,500–£9,200 PCT cost.

7. **Monitor for competing patent publications.** Attorney should set up a quarterly watch service in G09C/H04L9 from the filing date. First review at 6 months.

8. **First paying users.** The IP value is contingent on the product reaching commercial scale. The single most important near-term task for exit value is getting to first revenue — even at a token subscription price. The go/no-go gate from RET-001 (£500/month recurring by M13) remains the right anchor.

### 3-year

9. **Revisit SEIS or institutional fundraising.** At the 3-year mark, if the product has a filed patent, 500+ paying users, and at least one institutional Care & Consent reference client, a fundraising conversation at a much stronger negotiating position becomes viable. Bret gives up less equity per pound raised; the company has demonstrable traction. This is the right time for external capital, not now.

---

## Questions for Bret

1. **What does Steve mean by "granted"?** Has this been clarified, or is it still the informal verbal conversation? This should be resolved in the next conversation with Steve.

2. **Does Bret have accessible personal cash savings (outside ISA and pension) to cover the director's loan for the initial filing?** If Steve's contribution is timed around filing (not grant), Bret's net exposure is £5,000–£10,000. Is that fundable from savings?

3. **Has the CRN arrived?** If so, the IP assignment deed and JUXT declaration should be executed immediately.

4. **Has a CIPA-registered patent attorney been identified?** The 8-week window to mid-July is approximately 9 weeks from today. Attorney availability is the binding constraint once CRN and JUXT consent are in place.

5. **Is there any consideration of sharing the PITCH-002 or PITCH-003 documents more widely?** If so, a patent filing must come first — any public disclosure of the window capsule construction before a UK application is filed forecloses the UK patent position with no grace period.

---

## PA Summary

**For:** PA (cross-session memory) and Legal Counsel  
**Date:** 2026-05-17  
**Task completed:** RET-004 — Steve's offer, undisclosed IP risk, abstract claim strategy

---

### Key updates in three lines

1. **Steve's £5,000 is structurally useful if timed around filing, not grant.** The "granted" condition must be resolved — if it means actual patent grant (3–5 years), the contribution does not help near-term. If it means "filed/acknowledged," it halves Bret's net director's loan exposure. Structure it as a company loan, not equity.

2. **Undisclosed prior art risk is 10–20% probability of a pending competing application.** File now. Set up a patent monitoring service from the priority date. Every week of delay is uninsured.

3. **Abstract claim strategy confirmed.** The attorney should draft at three levels of abstraction. Level 2 (intermediate — distributed randomness beacon + threshold deletion + XOR blinding) is the commercially significant scope. If it survives prosecution, it raises the central IP value estimate to £350,000–£1,100,000.

---

### Three pitch documents produced

| File | Audience | Technical level |
|---|---|---|
| PITCH-001_beginner_what-heirlooms-is-building.md | Steve and similar — no technical background | None — plain English and analogy |
| PITCH-002_intermediate_heirlooms-differentiation-and-market.md | General investors, business supporters | WhatsApp-level — key terms explained |
| PITCH-003_advanced_cryptographic-differentiation-and-ip.md | Technical due diligence, engineers, cryptographers | Full — construction, prior art, PQC |

These documents do not include a funding ask. The ask is to be made verbally by Bret.

---

*This brief is for Bret personally and should not be shared externally. The patent-sensitive content in PITCH-003 should not be shared before a UK application is filed.*
