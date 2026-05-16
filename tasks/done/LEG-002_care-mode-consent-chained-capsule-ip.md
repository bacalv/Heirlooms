---
id: LEG-002
title: Legal assessment — Care Mode consent framework, chained capsule IP, and white-label licensing
category: Legal
priority: Medium
status: queued
assigned_to: Legal
depends_on: [LEG-001]
estimated: 1 session
---

## Context

The 2026-05-16 CTO/Marketing Director session produced three legally significant product
directions. LEG-001 (currently in progress) covers patentability of the window capsule
construction. This task covers the new directions that emerged in the same session.

**1. Care Mode** — E2EE monitoring for Power of Attorney holders:
- A POA holder (e.g. a son with LPA for his mother with Alzheimer's) uses Heirlooms to
  receive E2EE geofenced location alerts: "if mum leaves the safe zone, notify only us."
- Consent is established and cryptographically timestamped by the person while they still
  have full mental capacity.
- The consent is revocable at any time while capacity is retained; once capacity is lost,
  the consent continues in force under the LPA.
- The platform cannot see the location data — only the designated POA holders can decrypt.

**2. Chained capsules** — conditional delivery DAG:
- Capsule C₁ is delivered to multiple recipients with a time window.
- Inside is a puzzle and an encrypted reference to capsule C₂.
- The first recipient to solve the puzzle unlocks C₂ for themselves only.
- If nobody solves it, C₂ is never delivered and its contents are permanently inaccessible.
- Commercial applications include ARGs, brand campaigns, serialised storytelling, and
  educational gamification.

**3. White-label / org-flavoured app**:
- Heirlooms sells a flavoured version of the app to an organisation (e.g. a publisher,
  brand, or institution) for running campaigns.
- The organisation's users interact with a branded experience built on Heirlooms'
  cryptographic infrastructure.
- "First 300 unlock codes free with 1GB storage" — promotional distribution via QR codes
  in magazines or similar.

## Goal

Produce a legal assessment brief covering the key questions below.

## Questions to address

### Care Mode — consent and regulatory

1. **LPA scope**: Health & Welfare LPA vs Property & Financial Affairs LPA — which type
   covers consent to digital monitoring? Can monitoring be authorised under either, or
   only Health & Welfare? What if the person has both types?

2. **Mental Capacity Act 2005**: A person granting consent to monitoring while they have
   capacity — is that consent valid for ongoing monitoring after capacity is lost? Does the
   MCA require periodic review of the consent terms? What happens if the person regains
   capacity temporarily (as can happen with Alzheimer's)?

3. **UK GDPR Article 9**: Location data and health data are special category data. What
   does compliant processing look like for Care Mode? What is the legal basis — explicit
   consent, or vital interests, or something else? Does the E2EE architecture (server
   cannot see data) affect the analysis?

4. **Medical device risk**: Does a product that provides health-adjacent monitoring (location
   alerts for a person with a medical condition) risk classification as a medical device
   under the UK Medical Device Regulations 2002 / MHRA guidance? What is the threshold
   and how should Heirlooms stay below it?

5. **Data controller question**: Who is the data controller for Care Mode data — Heirlooms,
   the POA holder, or a joint arrangement? What are the implications for liability?

6. **Terms of service**: What terms are needed to make Care Mode legally defensible? What
   disclosures must be made to the person being monitored, and in what form?

### Chained capsules — IP

7. **Patentability**: Is the chained capsule construction (conditional unlock via puzzle
   solve + QR capsule reference + competitive delivery + expiry-as-death) patentable
   independently of the window capsule (LEG-001)? Are they best filed as a single patent
   family or separately?

8. **Prior art risk**: ARG platforms and digital mystery experiences exist. What is the
   risk that the chained capsule mechanic (without the cryptographic enforcement) is
   anticipated by prior art in the entertainment industry?

### White-label / org licensing

9. **Data controller / processor split**: In a white-label arrangement, is the organisation
   the data controller and Heirlooms the processor? What contractual framework is needed
   (UK GDPR Article 28 processor agreement)?

10. **IP protection**: When Heirlooms sells a flavoured app to an organisation, how is
    the core cryptographic IP protected? What licence terms prevent the organisation from
    reverse-engineering or reproducing the platform?

11. **Liability**: If an organisation runs a campaign using the chained capsule mechanic
    and a participant suffers harm (e.g. a puzzle causes distress), who is liable? What
    disclaimers and indemnities are needed?

## Output

Produce a legal brief to `docs/legal/LEG-002_care-mode-consent-chained-capsule-ip.md`.

Report structure:
```
# LEG-002 — Care Mode, Chained Capsule IP, and White-Label Legal Assessment

## Care Mode — consent and regulatory
  ### LPA scope
  ### Mental Capacity Act analysis
  ### UK GDPR Article 9 compliance
  ### Medical device risk
  ### Data controller question
  ### Recommended terms of service provisions
## Chained capsules — IP
  ### Patentability assessment
  ### Prior art risks
  ### Filing strategy (standalone vs family with LEG-001)
## White-label licensing
  ### Controller/processor framework
  ### IP protection
  ### Liability and indemnity
## Priority actions (ranked)
## PA Summary
```

The PA Summary must include:
- Whether Care Mode is legally buildable under current UK law (with what conditions)
- Whether the medical device risk is material
- Whether chained capsule patentability should be pursued alongside LEG-001
- Any actions required from the CTO before legal work can proceed

## Completion notes

**Completed:** 2026-05-16  
**Output:** `docs/legal/LEG-002_care-mode-consent-chained-capsule-ip.md`

**Key findings:**

1. **Care Mode — legally buildable.** H&W LPA is the correct legal vehicle; Property & Financial Affairs LPA is irrelevant. Donor consent while capacity is retained is valid ongoing authority; LPA provides continuation once capacity is lost. Revocation mechanism for the monitored person is legally mandatory. DPIA required before launch. E2EE architecture is a material compliance mitigant. Care Mode is a joint controller arrangement with the attorney/POA holder.

2. **Medical device risk — manageable, not material.** Classification risk is controlled by intended purpose statements in marketing and UI. Key rule: never describe Care Mode as monitoring a specific medical condition. Recommend commissioning a written MHRA qualification opinion (£2,000–£5,000) before launch.

3. **UK GDPR Article 9 — dual basis needed.** While donor has capacity: explicit consent (Art. 9(2)(a)) + consent (Art. 6(1)(a)). After capacity is lost: vital interests (Art. 9(2)(c)) + legitimate interests (Art. 6(1)(f)) supported by H&W LPA authority. Document with a Legitimate Interests Assessment.

4. **Chained capsule — file alongside window capsule.** Likely independently patentable. Prior art in the ARG space covers application-layer sequential puzzle mechanics but does not anticipate cryptographically enforced conditional delivery with irrecoverable expiry. The novelty is in the cryptographic layer, not the game mechanic. Recommend including as an independent claim in the same patent application as the window capsule. Marginal additional cost: £2,000–£4,000.

5. **White-label controller/processor split — clean.** Organisation is controller; Heirlooms is processor. Article 28 DPA template needed for each white-label customer. Standard white-label licence needed covering IP protection, indemnity, and liability cap. Gambling Act 2005 risk for paid-entry competitive campaigns — case-by-case review required.

**Actions required from CTO (carried to PA):**
- Authorise patent attorney engagement (window capsule + chained capsule, single filing)
- Confirm incorporation status and contractor IP assignments (LEG-001 carry-forward)
- Decide if Care Mode is in scope for next iteration (DPIA takes 6–8 weeks)
- Authorise white-label legal documentation if white-label is in next commercial sprint
- Confirm whether paid-entry campaigns are intended (triggers Gambling Act review)
