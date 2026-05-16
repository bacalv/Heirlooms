# LEG-002 — Care Mode, Chained Capsule IP, and White-Label Legal Assessment

**Prepared by:** Legal Counsel  
**Date:** 2026-05-16  
**Status:** Final  
**Audience:** CTO (Bret Calvey)  
**Depends on:** LEG-001 (patent assessment — window capsule and related IP)  
**Inputs:** PA_NOTES.md, task brief LEG-002, MCA 2005, UK GDPR / ICO guidance, MHRA guidance on standalone software, Article 28 ICO guidance, ARG prior art literature

---

## Care Mode — consent and regulatory

### LPA scope

**The short answer:** Health & Welfare LPA is the relevant instrument. Property & Financial Affairs LPA is categorically irrelevant. Care Mode should be designed around H&W LPA only.

**Analysis:**

The Mental Capacity Act 2005 creates two distinct types of Lasting Power of Attorney:

1. **Property & Financial Affairs LPA** — covers the management of property, bank accounts, investments, and financial decisions. It can be exercised even when the donor still has capacity (unlike H&W). It has no scope to authorise monitoring of a person's physical location or welfare. It is irrelevant to Care Mode.

2. **Health & Welfare LPA** — covers personal welfare decisions: where the person lives, day-to-day care, medical treatment, and "personal welfare matters." It can only be used once the donor has lost capacity (or the attorney reasonably believes the donor has). Under ss. 11–13 of the MCA 2005, it covers decisions a person could lawfully make about their own life if they had capacity — which includes decisions about who may receive information about their whereabouts and welfare.

**Digital monitoring falls within the H&W LPA scope as a personal welfare decision**, not a medical one. Location monitoring to ensure the safety of a person with Alzheimer's is directly analogous to the decision about daily routine, safe living environment, and care arrangements. The Office of the Public Guardian's guidance confirms that H&W attorneys can make decisions about "where the person lives and how they're looked after" — location-based safety monitoring is plainly within this.

**If the donor has both types of LPA:** Only the H&W LPA is relevant for Care Mode authorisation. Having both does not change the position — the H&W LPA is what empowers the attorney to act on personal welfare matters.

**Critical constraint:** The H&W LPA can only operate once the donor lacks capacity for the decision in question. This is decision-specific under s.2 MCA 2005: a person may lack capacity to manage their finances but retain capacity to consent to (or refuse) monitoring. Care Mode must be designed so that the initial consent to monitoring is given by the donor personally, while they have capacity. The LPA then provides the legal basis for continuing that arrangement once capacity is lost — not for overriding a capable donor's refusal.

**Recommendation:** Care Mode should be built around the H&W LPA instrument. The product onboarding must obtain the donor's own explicit consent while they have capacity, with the LPA operating as the continuing authority once capacity is lost. The two stages (donor consent → LPA continuation) must be structurally distinct in both the product and the terms of service.

---

### Mental Capacity Act analysis

**Key questions:** Is consent granted while the donor has capacity valid for ongoing monitoring after capacity is lost? Does the MCA require periodic review? What happens on temporary regain of capacity?

**Pre-capacity-loss consent and its validity after capacity is lost:**

The MCA 2005 operates on a decision-by-decision, time-of-decision basis. A person has capacity to make a particular decision at a particular time; loss of capacity does not retroactively invalidate prior decisions. Consent given by a capable donor to an ongoing arrangement (such as location monitoring) survives the loss of capacity, provided:

1. The consent was valid when given (the person had capacity at the time, was not unduly influenced, and understood the nature and scope of the monitoring — including who receives the data and for how long).
2. The monitoring does not conflict with a valid Advance Decision to Refuse (ADRT) — an explicit prior refusal of monitoring would override a subsequent LPA attorney's continuation.
3. The H&W attorney, once acting, must at all times act in the donor's best interests under s.4 MCA 2005. If circumstances change such that continued monitoring is no longer in the donor's best interests, the attorney cannot continue it simply because an earlier consent exists.

**The MCA does not impose a statutory review period** for ongoing personal welfare arrangements. However, the best interests duty is ongoing. An H&W attorney is required to consider, as part of the best interests analysis, whether the person is likely to regain capacity (s.4(3)), and if so, whether the decision should wait. For Care Mode, this means the attorney must periodically consider whether circumstances have changed — the platform cannot replace that judgment.

**Fluctuating capacity (common in Alzheimer's):**

The MCA is explicit: a person can lack capacity for the purposes of the Act even if the loss is partial or temporary, or if capacity fluctuates (s.2(2)). This means:

- During a period of sufficient capacity, the donor may revoke their consent to monitoring. That revocation must be honoured immediately.
- If the donor revokes during a lucid interval, the H&W attorney's authority to continue monitoring under the LPA is suspended — the attorney cannot override a capable person's expressed wishes.
- Capacity is not binary (all or nothing) and is decision-specific. A donor may lack capacity for financial decisions but have capacity regarding monitoring on the same day.

**Product design implication:** Care Mode must include a real-time revocation mechanism. The terms of service must make clear that if the monitored person communicates a revocation (even informally, including to a carer), monitoring must cease pending a reassessment of capacity. The platform cannot detect capacity states, so the obligation to act on revocation must be placed squarely on the H&W attorney/POA holder.

**Recommendation:** Build a technically enforced revocation flow. The monitored person's device must be able to terminate Care Mode monitoring directly, without requiring the attorney's cooperation. Log all revocations with timestamps. This is both a legal requirement and a reputational necessity.

---

### UK GDPR Article 9 compliance

**The nature of the data problem:**

Care Mode processes two categories of data that require Article 9 analysis:

1. **Location data** — location data is not Article 9 special category data on its face. However, when location data is processed in a context that reveals a person's health status (specifically, that they have a condition requiring safety monitoring — Alzheimer's or similar), the ICO treats it as **data revealing health status**, which is Article 9 health data. The care context transforms the category of the data. Heirlooms cannot escape Article 9 by characterising Care Mode data as "just location."

2. **Health data by inference** — the fact that a person is enrolled in Care Mode, and who their attorney is, itself reveals information about their health status. Even metadata — that a geofenced alert fired — is health data in context.

**Dual legal basis requirement:**

For any Article 9 special category processing, UK GDPR requires:
- An **Article 6 lawful basis** (legitimate processing at all)
- An **Article 9 condition** (special category processing specifically)

**Article 6 analysis for Care Mode:**

The strongest Article 6 basis while the donor has capacity is **consent** (Article 6(1)(a)). Once capacity is lost and the attorney acts, consent by the data subject is unavailable — the applicable bases are:
- **Legitimate interests** (Article 6(1)(f)) of the attorney/family — arguable, though the balance test is complex given the vulnerability of the data subject.
- **Vital interests** (Article 6(1)(d)) — applicable where processing is necessary to protect life. Safety monitoring for a person with Alzheimer's who may wander into danger has a strong vital interests argument, but vital interests is a high bar (genuine threat to life, not merely welfare).

My assessment: rely on **consent** as the Article 6 basis while the donor has capacity, transitioning to **legitimate interests** after capacity is lost, with the attorney's H&W LPA authority as the legal framework for that legitimate interest. The legitimate interests assessment (LIA) must be documented and kept on file.

**Article 9 condition analysis:**

The available conditions for Article 9 health data processing are:
- **Explicit consent** (Schedule 1 Part 1 para 1, UK GDPR Art. 9(2)(a)) — the strongest option. Requires freely given, specific, informed, unambiguous consent. Applies while the donor has capacity.
- **Vital interests** (Art. 9(2)(c)) — available where processing is necessary to protect life and the person cannot give consent. Directly applicable once capacity is fully lost and the monitoring is safety-critical.
- **Health or social care** (Art. 9(2)(h) + DPA 2018 Schedule 1 Part 1 para 2) — requires processing to be by a health or social care professional or under their responsibility. Heirlooms is not a health or social care provider. This condition is **not available** to Heirlooms.
- **Substantial public interest** conditions (Art. 9(2)(g) + DPA 2018 Schedule 1 Part 2) — the safeguarding condition (para 18) permits processing for protecting children and adults at risk, subject to having an appropriate policy document. This is a viable secondary basis for Care Mode if the person at risk falls within "adults at risk" as defined. This warrants investigation with external counsel.

**Recommended approach:**
- While capacity is retained: explicit consent (Art. 9(2)(a)) + consent (Art. 6(1)(a)). Document fully.
- Once capacity is lost: vital interests (Art. 9(2)(c)) + legitimate interests (Art. 6(1)(f)), supported by the H&W LPA authority. Have a documented LIA.
- Consider also the safeguarding substantial public interest condition (DPA 2018 Schedule 1 para 18) as an additional basis.

**Effect of E2EE architecture on the Article 9 analysis:**

This is legally significant. Heirlooms' E2EE design means the server never holds the location data in decryptable form — only the designated POA holders can decrypt. This substantially changes the controller's technical exposure.

The ICO recognises that E2EE operators who genuinely cannot access user content occupy a different compliance position from operators who hold plaintext. However, **the ICO's position is that the technical inability to read data does not eliminate data controller status** — Heirlooms remains a data controller because it determines the purposes and means of the processing (the platform and infrastructure). What E2EE does is:

1. Significantly reduce the risk of data breach from Heirlooms' systems — which supports the Article 5(1)(f) integrity and confidentiality principle.
2. Eliminate the risk that Heirlooms staff can access or misuse the health data — relevant to demonstrating appropriate technical measures under Article 32.
3. Make Heirlooms a materially lower-risk data controller for ICO enforcement purposes.

The E2EE architecture should be prominently documented in Heirlooms' Data Protection Impact Assessment (DPIA), which is mandatory for Care Mode given the vulnerability of the data subjects (MCA-impaired individuals). Article 35 UK GDPR requires a DPIA where processing is "likely to result in a high risk to the rights and freedoms of natural persons" — monitoring people with cognitive impairment using location data is squarely within this.

**Recommendation:** Conduct a DPIA for Care Mode before launch. The E2EE architecture is a substantial mitigant to include in the DPIA risk assessment. Ensure the DPIA documents the dual legal basis, the revocation mechanism, and the role of the H&W LPA. If DPIA reveals high residual risk, consult the ICO before launch (prior consultation under Art. 36).

---

### Medical device risk

**The threshold question: is Care Mode a medical device?**

Under the UK Medical Devices Regulations 2002 (as amended) and MHRA guidance, software (including apps) qualifies as a medical device if its **intended purpose** includes one or more medical purposes — specifically: diagnosis, prevention, monitoring, treatment, or alleviation of disease, injury, or disability in humans.

**The critical distinction is intended purpose, as stated by the manufacturer.** The MHRA guidance is unambiguous: "monitoring general fitness, general health, and general wellbeing is typically not considered a medical purpose." The question is how Heirlooms markets and describes Care Mode.

**Where the risk arises:**

If Heirlooms describes Care Mode as:
- A tool for "monitoring people with Alzheimer's" — this is monitoring a disease. Medical device classification risk is **high**.
- A tool for "safety monitoring for vulnerable adults" — borderline. May attract scrutiny.
- A "family safety communication tool" or "location sharing for peace of mind" — this is wellness/general care, not disease monitoring. Medical device classification risk is **low**.

The legal risk is not in what Care Mode technically does — it is in what Heirlooms says it does. The MHRA classifies on the basis of the manufacturer's stated intended purpose, including in marketing materials, app store descriptions, and the product UI.

**Practical risk level:**

Heirlooms' E2EE architecture means the platform has no visibility of the monitored person's condition, diagnosis, or health status. The platform delivers encrypted location alerts — it does not analyse symptoms, recommend treatment, or assist in clinical decisions. The comparison is closer to a baby monitor or a tracking app than to a clinical monitoring device. MHRA guidance specifically exempts "communication tools" from medical device classification.

**If classified as a medical device:**

Classification would likely be Class I (lowest risk) or Class IIa depending on MHRA's view. UKCA marking, registration with MHRA, post-market surveillance, and a technical file would be required. This is a significant compliance overhead for an MVP. Estimated cost: £50,000–£150,000 to achieve compliance properly.

**Recommendation:** Design Care Mode to stay clearly below the medical device threshold. Concretely:

1. **Do not describe Care Mode as a tool for monitoring medical conditions** in any marketing, UI copy, app store listings, or terms of service. Describe it as a family safety and communication tool.
2. **Do not reference specific conditions** (Alzheimer's, dementia, Parkinson's) in the product interface, marketing, or press releases. Conditions can be mentioned in legal context (e.g. terms of service examples) but must not become part of the intended purpose statement.
3. **Do not include any clinical features** — no symptom tracking, no health score computation, no fall detection, no heart rate integration.
4. **Commission a one-off MHRA qualification assessment** from a medical device regulatory consultant before public launch. Cost: approximately £2,000–£5,000. This produces a documented record that Heirlooms consciously assessed its position and concluded it is not a medical device — essential protection if MHRA ever questions the product. Recommend instructing external counsel with medical device regulatory experience.

The medical device risk is **manageable if addressed by design**. It becomes material only if Heirlooms is careless about intended purpose statements in its public-facing communications.

---

### Data controller question

**Who is the data controller for Care Mode data?**

This requires analysis of who determines the purposes and means of processing:

- **Heirlooms** determines the means: it provides the E2EE platform, sets the technical architecture, determines retention policies, and defines the product. Heirlooms is a **data controller** for the infrastructure processing.
- **The H&W attorney / POA holder** determines the purposes: they decide to enrol the family member, define the geofence, and receive the alerts. They are making personal welfare decisions about another person using Heirlooms as their tool. This is characteristic of a **controller** role, not a processor.
- **The monitored person (donor)** is the data subject who gave initial consent. Once they have lost capacity, they retain data subject rights but cannot exercise them without assistance.

**Assessment:** This is most likely a **joint controller** arrangement between Heirlooms and the H&W attorney/POA holder, or alternatively a two-controller arrangement where:
- Heirlooms controls the infrastructure processing (server, storage, delivery).
- The H&W attorney controls the purpose of processing (safety monitoring of the donor).

A pure controller/processor split (Heirlooms as processor, attorney as controller) is not appropriate because Heirlooms is not acting purely on the attorney's instructions — Heirlooms determines core technical means.

**Implications of joint controller status:**

Under UK GDPR Article 26, joint controllers must enter a transparent arrangement determining their respective responsibilities for compliance. Key practical consequences:
- Heirlooms must publish a transparent account of the joint controller relationship in its privacy notice.
- The terms of service / user agreement for Care Mode must function as the Article 26 arrangement — setting out who is responsible for what (Heirlooms: platform security, breach notification, DPO; attorney: enrolment decisions, revocation, data subject rights facilitation for the donor).
- Heirlooms cannot disclaim responsibility for the data simply because E2EE means it cannot read it.

**Recommendation:** Draft Care Mode terms of service to reflect a joint controller framework. Clearly define: (i) what Heirlooms is responsible for, (ii) what the attorney is responsible for, (iii) data subject rights process for the monitored person. Consult external data protection counsel to review the joint controller arrangement before launch — this is technically complex and the ICO has been active in scrutinising joint controller claims.

---

### Recommended terms of service provisions

Care Mode requires the following specific provisions in Heirlooms' terms of service / a separate Care Mode addendum:

1. **Eligibility for Care Mode:** Available only where the monitored person (a) is 18 or over, (b) has provided their own explicit, informed consent while having mental capacity, and (c) the attorney has a registered H&W LPA (OPG-registered).

2. **Consent certification:** The attorney must certify (not just tick a box) that the monitored person gave informed consent while having capacity. A brief description of what that consent was should be recorded with timestamp. This protects Heirlooms if the consent is later challenged.

3. **Revocation mechanism:** The monitored person must have an in-app mechanism to revoke Care Mode immediately without the attorney's involvement. The terms must state that revocation is immediate and irrevocable by the attorney.

4. **Fluctuating capacity:** The terms must state that if the monitored person communicates a wish to stop monitoring — through any channel, not just the app — the attorney must immediately suspend monitoring and seek legal/medical advice on capacity. The platform is not responsible for detecting capacity states.

5. **Attorney obligations:** The attorney using Care Mode accepts: (a) to act in the monitored person's best interests at all times; (b) that Care Mode data may only be used for the safety of the monitored person, not for any other purpose; (c) to comply with MCA 2005 and the Code of Practice; (d) that they are a joint data controller with responsibilities under UK GDPR.

6. **Disclosure to monitored person:** The monitored person must have been informed that monitoring is taking place, who receives the data, and what it is used for. The terms must require the attorney to make these disclosures. Heirlooms should additionally provide in-app information to the monitored person about the monitoring (consistent with transparency obligations under Art. 13/14 UK GDPR).

7. **Medical device disclaimer:** Clear statement that Care Mode is not a medical device, is not intended to diagnose or monitor any medical condition, and is not a substitute for professional medical or social care.

8. **Prohibited uses:** Care Mode must not be used for covert monitoring (i.e. without the monitored person's knowledge), monitoring of persons without an H&W LPA in force, or any commercial purpose beyond personal family safety.

9. **DPIA reference:** Inform Care Mode users that a DPIA has been conducted and is available on request to data subjects.

---

## Chained capsules — IP

### Patentability assessment

**The construction:**

Chained capsules add a novel layer on top of (or independent of) the window capsule construction assessed in LEG-001. The specific mechanic is:

- Capsule C₁ is delivered to N recipients simultaneously within a time window.
- C₁ contains a puzzle and a cryptographically encrypted reference (pointer) to C₂.
- The puzzle solution, when computed, unlocks C₂ — but only for the first solver.
- If no solver completes the puzzle within the window, C₂ is never delivered; it expires and becomes permanently inaccessible (via the window capsule upper-bound mechanism).
- C₂ delivery is therefore conditional on: (a) at least one recipient solving the puzzle, (b) doing so within the delivery window, and (c) being the first to do so.

**Assessment of patentable subject matter:**

The chained capsule construction involves a specific technical mechanism: a cryptographically enforced DAG (directed acyclic graph) of capsules where:
1. Delivery of downstream capsules is gated on cryptographic proof of puzzle-solve.
2. The competitive element (first-solver wins) is enforced by the cryptographic architecture — it is not merely a game rule, it is a property of the key management system.
3. Expiry-as-death: if the puzzle is not solved within the window, the cryptographic key material for C₂ is irretrievably destroyed.

The technical novelty is the **cryptographically enforced conditional delivery chain with competitive exclusion and expiry**. This is distinct from:
- A mere game rule that says "first solver wins" (no cryptographic enforcement).
- A server-side gating system that could be bypassed by server compromise.
- Standard content delivery networks with access control.

The construction creates a new technical property: **the outcome of a game is irreversibly encoded in the key material, not merely tracked in a database**. This is a technical contribution beyond the game mechanic as such.

**Inventive step:**

The prior art in the gaming and ARG space (discussed below) describes conditional puzzle mechanics at the application layer. No prior art found implements the specific combination of:
- Threshold/IBE key management for capsule delivery.
- Puzzle-solve as a key derivation event.
- Competitive exclusion enforced at the cryptographic layer.
- Expiry via threshold deletion making the prize permanently inaccessible.

The jump from "application-layer puzzle gates" to "cryptographically enforced conditional delivery with irrecoverable expiry" is not obvious. It requires applying the window capsule construction (itself novel per LEG-001) in a chained topology, which is a separate inventive insight.

**Verdict: likely patentable as an independent construction**, or at minimum as a strong dependent claim extending the window capsule patent. The patentability of the chained capsule construction is, if anything, cleaner than the tag scheme assessed in LEG-001 — the prior art landscape is less saturated and the technical novelty is concrete.

---

### Prior art risks

**ARG and interactive experience prior art:**

The ARG genre has significant prior art on sequential puzzle mechanics going back to the early 2000s:

- *Webrunner: The Hidden Agenda* (1996, Wizards of the Coast): sequential "gates" — solving one puzzle unlocks the next.
- *The Beast* (2001, Microsoft, for A.I. the film): a large-scale ARG with sequential puzzle delivery across a network of fictional websites, competitive solving by a community, and staged content revelation.
- *I Love Bees* (2004, 42 Entertainment, for Halo 2): competitive phone network puzzle, where players competed to answer calls in specific locations to unlock story content.
- *Frog Fractions 2* (2016): clues buried across 23 games, solved sequentially over two years.
- Patent US20060246970A1 (filed 2005): covers methods for running an ARG using collectible game pieces with unique identifiers, puzzle solutions, and multi-stage delivery. Does not cover cryptographic enforcement.

**What the ARG prior art does and does not anticipate:**

The ARG prior art establishes that:
- Sequential puzzle delivery (solve puzzle → get next content) is prior art.
- Competitive solving (first solver progresses) is prior art in the application layer.
- Staged content revelation is prior art.
- QR codes and physical tokens as puzzle delivery mechanisms are prior art (post ~2010).

The ARG prior art **does not** anticipate:
- Cryptographic enforcement of delivery conditions (the ARG mechanics are all server-side rules, bypassable by server operators).
- Expiry-as-irrecoverable-destruction: in all known ARGs, unused content simply goes undelivered; it is not cryptographically destroyed and permanently inaccessible.
- The trustless property: in no ARG is the game operator technically unable to deliver C₂ outside the rules — the mechanic is always a policy choice, not a cryptographic guarantee.
- Competitive exclusion at the key management layer: "first solver" is always a server-enforced rule, not a property of the cryptography.

**The line between prior art and the Heirlooms construction:**

The Heirlooms chained capsule is not a new game mechanic — sequential puzzles and competitive unlocking are old. What is potentially novel is the **cryptographic enforcement of those mechanics**, specifically:
- The mechanics are implemented at the key management layer using the window capsule construction.
- The result is mathematically guaranteed, not just policy-enforced.
- The game operator (Heirlooms or the white-label customer) is technically unable to cheat.

A patent examiner will assess obviousness from the perspective of a skilled person in the field. The skilled person in cryptographic key management would not obviously combine sequential puzzle-gated delivery with the window capsule construction. The skilled person in ARG design would not obviously implement their mechanics in cryptographic key management. The chained capsule sits in the intersection of two fields where the combination is non-obvious to practitioners of either alone.

**Prior art risk rating: Medium.** The application-layer mechanics are clearly prior art; the cryptographic enforcement layer is not. The patent claim must be carefully limited to the cryptographic construction, not the game mechanic as such. A broadly drafted claim covering "conditional delivery based on puzzle solve" would fail on ARG prior art. A narrowly drafted claim covering "cryptographic key derivation conditional on puzzle-solve attestation, with threshold expiry guaranteeing permanent inaccessibility of undelivered content" is defensible.

---

### Filing strategy (standalone vs family with LEG-001)

**Option A: Single patent family (recommended)**

File the window capsule (LEG-001) and the chained capsule as a single patent application with:
- Independent claim 1: the window capsule construction (tlock + Shamir threshold deletion).
- Independent claim 2: the chained capsule construction (conditional delivery DAG using window capsule mechanics).
- Dependent claims: variants, specific algorithm choices, envelope integration, competitive exclusion mechanics.

**Advantages:**
- Single priority date for both constructions.
- Cost savings — one specification, one set of attorney fees for drafting.
- Examiner sees both constructions together; if window capsule is novel, it supports patentability of the chained extension.
- Broader claim coverage in a single filing is more attractive to potential licensees or acquirers.

**Option B: Separate filings**

File the window capsule first (immediately, for urgency reasons established in LEG-001). File the chained capsule as a separate continuation or divisional within the 12-month priority window.

**Advantages:**
- Window capsule priority date is established now, without waiting for chained capsule analysis to complete.
- If the window capsule is granted and the chained capsule application is filed as a continuation-in-part, it inherits the priority date for overlapping subject matter.

**Recommendation: Option A, but file immediately.** The chained capsule construction is sufficiently developed to include in the initial filing. Adding it increases attorney drafting costs by an estimated £2,000–£4,000 (a marginal addition to the £10,000–£15,000 already estimated). The additional IP coverage is disproportionately valuable: the chained capsule has clear commercial applications in white-label licensing to publishers and brands, which is a distinct revenue stream from the core consumer product. Having a patent that expressly covers the white-label use case strengthens Heirlooms' licensing position.

**Practical action:** When engaging the patent attorney for the LEG-001 filing, provide this brief and the task brief LEG-002 context so they can incorporate the chained capsule claims in the initial specification. Identify whether the chained capsule is sufficiently implemented to describe accurately in the specification (a working implementation or detailed technical design is preferable to a vague concept).

---

## White-label licensing

### Controller/processor framework

**The structure:**

In a white-label arrangement, an organisation (publisher, brand, institution) deploys a Heirlooms-branded experience to its users. The organisation:
- Selects which features to activate (e.g. chained capsule ARG mechanic).
- Controls the creative content and campaign structure.
- Has direct contractual relationships with its users.
- Determines the purposes of the campaign (marketing, storytelling, education).

Heirlooms:
- Provides the cryptographic infrastructure.
- Processes personal data of the organisation's users.
- Does not determine the purposes of the campaign.

**Controller/processor analysis:**

This is a clean controller/processor split. The organisation is the **data controller** — it determines the purposes and means of processing (running the campaign, identifying users, defining what data is collected). Heirlooms is the **data processor** — it processes personal data on behalf of the controller (the organisation) using the cryptographic infrastructure.

This is the standard SaaS DPA relationship, confirmed by MHRA guidance and ICO controller/processor guidance. The fact that Heirlooms designed and built the platform does not make it a controller for the white-label use case — determining means is distinguishable from determining purposes when Heirlooms has no stake in the campaign's content or user interactions.

**Note on E2EE complication:** Because Heirlooms cannot decrypt the content, it arguably cannot fulfil all Article 28(3) processor obligations as written — for example, it cannot assist the controller with responding to data subject access requests for content held in encrypted capsules. This must be addressed contractually: the organisation (controller) must acknowledge that capsule content is held in E2EE form by the processor, and the controller is responsible for assisting data subjects with any rights they wish to exercise over content they (the data subjects) can decrypt themselves.

**Article 28 DPA requirements:**

For each white-label customer, Heirlooms must execute a Data Processing Agreement meeting UK GDPR Article 28(3) requirements. The DPA must specify:

1. **Subject matter and duration:** campaign name, data categories processed, duration of the arrangement.
2. **Nature and purpose:** encrypted storage and delivery of campaign capsules; no Heirlooms access to content.
3. **Processing only on documented instructions:** Heirlooms processes data as directed by the organisation's campaign configuration. Heirlooms must not use campaign user data for any Heirlooms purpose.
4. **Confidentiality:** Heirlooms staff authorised to process campaign data are bound by confidentiality. (Note: E2EE means staff effectively cannot access content — this should be stated.)
5. **Technical and organisational security measures:** reference to Heirlooms' security standards, the E2EE architecture, and penetration testing schedule.
6. **Sub-processors:** Heirlooms uses GCP (Google Cloud Platform) as a sub-processor. This must be disclosed to the controller. Heirlooms is responsible for its sub-processors' compliance.
7. **Data subject rights assistance:** Heirlooms will assist the controller in responding to data subject rights requests to the extent technically possible (given E2EE limitations, set out explicitly).
8. **Breach notification:** Heirlooms must notify the controller without undue delay (and in any event within 72 hours of awareness) of any personal data breach.
9. **End-of-contract provisions:** on termination, Heirlooms deletes or returns campaign data (format to be agreed).
10. **Audit rights:** the controller may audit Heirlooms' compliance with the DPA on reasonable notice.

**Recommendation:** Draft a standard white-label DPA template as part of the white-label product launch preparation. Have it reviewed by external data protection counsel before first use. The E2EE-specific clauses (especially the data subject rights limitation and the sub-processor chain) require careful drafting.

---

### IP protection

**The risk:**

When Heirlooms delivers a white-label/flavoured app to an organisation, the organisation's technical staff or a commissioned engineer may attempt to reverse-engineer the cryptographic implementation. This risk is elevated because:
- The app binaries are distributed to end users (and therefore to the organisation's engineers).
- Mobile apps are routinely decompiled and reverse-engineered.
- The cryptographic constructions that are potentially patentable (window capsule, chained capsule) are implemented in the app code.

**Protection layers:**

1. **Patent protection (primary).** Once the window capsule and chained capsule patents are filed, Heirlooms has statutory exclusivity. Any organisation that reverse-engineers and reproduces the patented constructions infringes the patent regardless of what the licence says. Patent protection is the strongest layer and further confirms the urgency of the LEG-001 filing.

2. **Contractual protection (essential regardless of patent status).** The white-label licence agreement must include:
   - **Strict IP licence scope:** the licence grants the organisation a right to use the Heirlooms platform for a defined campaign purpose only. No right to copy, modify, reproduce, reverse-engineer, or create derivative works.
   - **No reverse engineering clause:** explicitly prohibited, referencing the specific constructions (cryptographic key management, capsule delivery mechanics). Rely on: Copyright, Designs and Patents Act 1988 s.296A (contractual prohibition of decompilation is permissible for non-interoperability purposes under UK law).
   - **Source code retention:** Heirlooms retains all source code. The organisation receives a compiled app and API access only.
   - **IP assignment clause:** any modifications or improvements made by the organisation (e.g. campaign-specific content or UI) — does the organisation own those, or does Heirlooms? Heirlooms should own any improvements to the core platform; the organisation owns its creative content.
   - **Confidentiality:** the cryptographic architecture (to the extent not yet public by reason of patent publication) is a trade secret. The organisation must maintain confidentiality of any technical disclosures received in the course of the white-label relationship.
   - **Audit rights:** Heirlooms may audit the organisation's use of the platform for compliance with the licence terms.

3. **Technical obfuscation.** The app binaries should employ standard code obfuscation (ProGuard/R8 on Android, standard iOS build tooling). This is not legal protection but raises the cost of reverse engineering. Do not rely on obfuscation alone.

4. **API key management.** White-label customers access the platform via API. API credentials should be scoped to the specific campaign and rotated on expiry. A compromised API key should not allow access to the cryptographic internals.

**Recommendation:** Prepare a standard white-label licence agreement template in parallel with the DPA template. Engage external commercial solicitors to draft both. The IP protection clauses and the DPA are interrelated — they should be reviewed together as a single commercial package.

---

### Liability and indemnity

**The risk scenario:**

An organisation runs a chained capsule campaign using the Heirlooms white-label platform. A participant:
- Suffers psychological distress from puzzle content (e.g. a capsule contains grief-related or disturbing material).
- Spends significant money or time in competitive pursuit of a capsule that proves unwinnable for them.
- Is a minor who participates in a campaign not designed for minors.
- Makes decisions (financial, personal) in reliance on capsule content that turns out to be incorrect or misleading.

**Liability analysis:**

Primary liability for the campaign experience lies with the **organisation (controller)** — it designed the campaign, chose the content, and invited participants. Heirlooms provided the infrastructure. The distinction is analogous to a website hosting company's limited liability for the content hosted by its customers.

However, Heirlooms is not automatically immune. Potential exposure arises where:
- The platform itself causes or contributes to harm (e.g. a bug that causes a puzzle to be unwinnable when it should have been solvable, or a key management failure that destroys a capsule prematurely).
- Heirlooms fails to implement adequate safeguards that it had undertaken to provide (e.g. age verification if committed to in terms).
- The harm is caused by a feature that Heirlooms controls (e.g. the expiry mechanic causing permanent loss of content a participant had a reasonable expectation to access).

**Required contractual provisions in the white-label agreement:**

1. **Indemnity from the organisation to Heirlooms:** the organisation indemnifies Heirlooms against all claims, losses, and costs arising from the campaign content, design, or the organisation's use of the platform. This is the primary protection.

2. **Heirlooms' liability cap:** Heirlooms' liability to the organisation is capped at the licence fees paid by the organisation in the 12 months preceding the claim. Standard SaaS limitation of liability. Exclude consequential and indirect losses.

3. **Platform warranties (limited):** Heirlooms warrants only that the platform will perform materially in accordance with its specification. No warranty as to fitness for any particular campaign purpose.

4. **Organisation's obligations:** the organisation warrants that it will: (a) ensure campaign content complies with all applicable laws (including ASA advertising standards, age rating requirements, and consumer protection law); (b) not use the platform for campaigns targeting minors without appropriate safeguards; (c) ensure participants receive adequate information about the competitive mechanic and the consequences of non-solve (permanent inaccessibility); (d) comply with UK GDPR as data controller.

5. **Participant-facing terms:** the organisation must ensure its own terms of service with campaign participants include: (a) clear disclosure of the competitive mechanic; (b) disclosure that unclaimed capsules are permanently destroyed; (c) no implied guarantee of winnability or accessibility; (d) appropriate content warnings for sensitive capsule content.

6. **Distress and consumer protection:** under UK consumer law (Consumer Rights Act 2015 and related regulations), participants may have rights to refunds if a digital service does not conform to what was promised. If the campaign charges for participation, the organisation must ensure compliance. Heirlooms' platform should not be configured to facilitate any structure that resembles an unlicensed lottery (gambling law risk — Gambling Act 2005).

**Gambling law note:** If participants pay to enter, and the unlock of a capsule constitutes a prize, the arrangement may be a lottery under the Gambling Act 2005. Lotteries require a licence or must fall within an exempt category. The "first to solve" competitive structure may be characterisable as a "prize competition" (which is not a lottery if the outcome depends on skill, not chance) — but this requires the puzzle to be genuinely skill-based, not random. **Recommend that all white-label campaigns involving paid entry and competitive prizes are reviewed for Gambling Act compliance before launch.** This is a non-trivial risk for the brand campaign use case.

**Recommendation:** The white-label agreement must contain a robust indemnity from the organisation, a liability cap for Heirlooms, and clear obligations on the organisation for campaign design, participant terms, and regulatory compliance. The gambling law question is a live issue for paid campaigns and must be assessed on a campaign-by-campaign basis. Heirlooms should build a pre-launch compliance checklist for white-label partners covering all of these issues.

---

## Priority actions (ranked)

1. **[IMMEDIATE] File window capsule + chained capsule patent (UK IPO).** Engage patent attorney now. Include chained capsule construction in the initial specification as recommended in this brief and LEG-001. Target filing by mid-July 2026 at the latest. Attorney cost: £12,000–£19,000 for the combined application.

2. **[IMMEDIATE] Commission MHRA qualification assessment for Care Mode.** Instruct a medical device regulatory consultant to produce a written qualification opinion confirming Care Mode is not a medical device under UK MDR 2002. This protects Heirlooms against future regulatory challenge. Cost: £2,000–£5,000. Do this before any public marketing of Care Mode.

3. **[BEFORE CARE MODE LAUNCH] Conduct DPIA for Care Mode.** UK GDPR Article 35 mandates a DPIA before processing begins. The DPIA must document: the E2EE architecture, joint controller arrangement, dual legal basis, revocation mechanism, and safeguards for MCA-impaired data subjects. If DPIA identifies high residual risk, consult ICO before launch (Art. 36 prior consultation).

4. **[BEFORE CARE MODE LAUNCH] Draft Care Mode terms of service / addendum.** Include all provisions recommended above (LPA certification, revocation mechanism, attorney obligations, joint controller disclosure, medical device disclaimer, prohibited uses). Have reviewed by external data protection counsel.

5. **[BEFORE WHITE-LABEL LAUNCH] Draft white-label licence agreement and DPA templates.** Standard documents covering IP protection, licence scope, indemnity, liability cap, organisation obligations, and Article 28 DPA. Engage external commercial solicitors. Cost: £3,000–£7,000 in attorney fees.

6. **[BEFORE FIRST PAID CAMPAIGN] Gambling Act compliance review.** Any white-label campaign involving paid entry and competitive prizes must be assessed against the Gambling Act 2005. Instruct a gambling/gaming law specialist. Cost: £1,500–£3,000 per campaign type assessed.

7. **[ONGOING] Confirm IP ownership position (from LEG-001).** All actions from LEG-001 remain outstanding: incorporation confirmation, contractor IP assignments, NDA protocol. These must be resolved before the patent application is filed.

8. **[WITHIN 6 MONTHS] Review Care Mode joint controller arrangement with ICO guidance.** ICO has published joint controller guidance; the Care Mode arrangement is novel and should be reviewed by external data protection counsel familiar with ICO enforcement practice.

---

## PA Summary

**For:** CTO (Bret Calvey)  
**Urgency:** High across three workstreams — patent filing, Care Mode regulatory readiness, white-label documentation.

---

**Is Care Mode legally buildable under current UK law?**

**Yes — with conditions.** The H&W LPA mechanism provides the legal vehicle; UK GDPR is manageable given the E2EE architecture. The conditions are:

- Donor must give explicit informed consent to monitoring while they have capacity. This is mandatory, not optional.
- A revocation mechanism that the monitored person controls directly must be built into the product.
- A DPIA must be completed before launch.
- Care Mode terms of service must reflect the joint controller arrangement and H&W LPA obligations.
- A written MHRA qualification opinion must be obtained confirming Care Mode is not a medical device.

None of these conditions are blockers — they are compliance steps. Care Mode is buildable; it needs a compliance framework built around it before public launch.

---

**Is the medical device risk material?**

**No — if managed correctly.** The risk is real but fully manageable by design. The decisive factor is how Heirlooms describes Care Mode in public-facing communications. If Heirlooms describes it as a family safety and communication tool (accurate — that is what it is), the MHRA classification risk is low. The risk becomes material only if Heirlooms references monitoring specific medical conditions in its marketing or UI. Commission the MHRA qualification opinion as a precaution; it is inexpensive protection.

---

**Should chained capsule patentability be pursued alongside LEG-001?**

**Yes — include in the same filing.** The chained capsule construction is likely independently patentable and is clearly defensible as a second independent claim in the same application as the window capsule. Including it adds modest cost (£2,000–£4,000 additional attorney time) for disproportionate benefit: it covers the white-label/ARG commercial use case explicitly within the patent, which strengthens Heirlooms' licensing position and exit value. File both in a single application immediately.

---

**Actions required from the CTO before legal work can proceed:**

1. **Authorise engagement of patent attorney** to file window capsule + chained capsule UK patent application. Provide attorney with LEG-001, this brief, and all technical specifications. Target: mid-July 2026.

2. **Confirm Heirlooms' incorporation status** and confirm contractor IP assignments are in place (carried forward from LEG-001 — still unresolved).

3. **Decide whether Care Mode is in scope for the next product iteration.** If yes, the DPIA and MHRA qualification work must begin now — they are not one-hour tasks. They should be initiated 6–8 weeks before any public announcement of Care Mode.

4. **Authorise white-label legal documentation** (licence + DPA template) if white-label is being planned for the next commercial sprint. External solicitor engagement is needed; internal drafts are insufficient for commercial agreements.

5. **Confirm: does Heirlooms intend to offer paid-entry white-label campaigns?** If yes, a Gambling Act review is required before launch. If campaigns are free to enter, this risk disappears.
