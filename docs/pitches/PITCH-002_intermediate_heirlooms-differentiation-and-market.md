# Heirlooms — Differentiation, Market Opportunity, and IP

**Version:** 3.0 — May 2026
**Audience:** Technically informed — familiar with concepts such as encryption and cloud software
**Confidential** — not for onward distribution without permission from Heirlooms Digital Ltd
**Pre-patent safe** — mechanism not disclosed; may be shared before patent filing

---

## The gap

Most digital vaults offer a promise: *we will not look at your data*. Heirlooms offers a proof: *we cannot*.

End-to-end encryption is now something most people encounter daily. WhatsApp and Signal use it. The principle is simple: the service provider cannot read your messages, because the keys to decrypt them exist only on users' devices. The provider carries the sealed envelope; it cannot open it.

That property is almost entirely absent from digital storage. Google Drive, iCloud, and Dropbox encrypt your files, but the provider holds the keys. They can access your data if compelled by a court, if they suffer a breach, or if their policies change. For most files this is an acceptable trade-off. For personal content — messages sealed for people you love, consent records made while you still had capacity — it is not.

Beyond the privacy gap, there is a timing problem no existing product has addressed. People want to seal things for the future: a video for a child's eighteenth birthday made when she is eight, a family message to be opened only after someone is gone, a consent record that must hold its integrity for decades. The services that have tried to address this are operationally dependent on a company surviving and choosing to act. They are promises, not guarantees.

Heirlooms addresses both problems simultaneously: genuine device-side end-to-end encryption combined with a mathematically enforced time window on content access.

---

## What makes the approach genuinely different

The Heirlooms time-capsule mechanism provides two properties that, together, no existing consumer product offers:

**The lower bound — content cannot be accessed before the unlock date.** When a capsule is sealed for a future date, the decryption capability does not exist yet. This is not a matter of the key being locked away somewhere secure. The decryption capability has not been created. It comes into existence on the specified date, produced through an external mechanism that operates completely independently of Heirlooms. No single party — including Heirlooms — controls or can accelerate it. A court order served on Heirlooms before the unlock date cannot produce the key, because Heirlooms does not hold it.

**The upper bound — content cannot be accessed after the expiry date.** At expiry, the means of completing decryption are permanently destroyed across a network of independent parties, each of whom holds a portion and is formally obligated to destroy it at the specified time. Once enough have done so, the capability is permanently gone — even though the separately-released lower-bound component is by that point a matter of public record. The construction ensures that the two components are of no use to anyone individually; both are required. The lower bound is a mathematical guarantee. The upper bound is the strongest achievable without quantum infrastructure — it holds provided a threshold of independent parties act honestly, an assumption used by every distributed cryptographic system in production today.

The result is a genuine access window. Before it opens: the decryption capability does not exist. After it closes: the means of reconstruction are permanently gone. The content inside is end-to-end encrypted throughout.

This is not a policy. It is a technical property of the system.

A thorough search of academic literature and international patent databases has found no prior work providing a complete deployable system with this combination of properties. The closest academic work defines a theoretical framework for time-bounded secret sharing but does not provide a practical implementation — filling that gap is Heirlooms' specific contribution.

---

## The competitive landscape

No existing product combines all four of the properties below. The table reflects the state of each product at the time of writing (May 2026).

| Product | Device-side E2EE? | Time-locked delivery? | Cryptographic expiry? | Access window? |
|---|---|---|---|---|
| Google Drive / iCloud | No — provider holds keys | No | No | No |
| Signal / WhatsApp | Yes | No | No | No |
| Farewill / Co-op Legal | No | Partial — legal, not cryptographic | No | No |
| Lasting (care planning) | No | No | No | No |
| Facebook Legacy Contact | No | No | No | No |
| **Heirlooms** | **Yes (live)** | **Yes***  | **Yes*** | **Yes*** |

*Time-locked delivery, cryptographic expiry, and access window are properties of the window capsule mechanism, currently in development as the next major milestone. Device-side E2EE is live across all platforms today.*

The combination of all four is what is being patented. A competitor who implements any three without the fourth offers a different and weaker product. A competitor who implements all four without a licence infringes.

---

## Three markets, one underlying platform

### Memory Archive — personal digital legacy

The primary consumer market. Families, couples, and individuals storing personal media for time-locked delivery to named recipients.

The UK serviceable market is approximately 800,000–1,350,000 households — the 3–5% of smartphone-owning households who actively organise personal media in a privacy-first product, a penetration rate consistent with comparable categories (journaling apps, photo-book services, ancestry platforms). At a blended £8–10/month subscription, the UK serviceable market represents approximately £80m–£160m ARR. Global serviceable market (English-speaking markets and Western Europe) is broadly ten to fifteen times larger.

Central planning scenario: 2,500 paying subscribers within three years at £8/month = £240,000 ARR. At a 6x strategic acquisition multiple for a privacy-first subscription product with a filed patent, this represents a gross exit contribution of approximately £1.4m from this segment alone. Exit proceeds are sensitive to user count, churn, and acquirer type — these figures are illustrative, not a projection.

The revenue model is freemium consumer subscription: a free tier for limited storage and basic use, converting to paid at a meaningful storage or feature threshold. The product-led growth mechanic — every capsule recipient becomes a potential user — drives acquisition at near-zero cost per acquired user.

### Care & Consent — dementia and advance care planning

The highest long-term value segment.

There are currently 900,000 people in the UK living with dementia and approximately 700,000 primary carers. Lasting Power of Attorney (LPA) registrations reached 1.37 million in 2024/25, up 28% year-on-year — a structural trend driven by an ageing population and the Powers of Attorney Act 2023, which is digitising the LPA process. The same need extends to anyone with a condition that may affect their capacity in future, not only those with dementia.

No consumer product currently provides a cryptographically timestamped, tamper-evident consent record — one where the timestamp is produced by an external mechanism at sealing time, making backdating cryptographically infeasible. The product is designed as a consent management and secure communication tool; it is not a clinical monitoring device and is not positioned as such.

Revenue model: a consumer add-on tier (£10–20/month for a family plan), scaling to institutional B2B licences for care homes (£500–3,000/month) and legal platforms (£500–5,000/month).

Strategic acquirers for this segment include care home groups, insurers, legal technology platforms, and NHS digital arms — buyers with significantly larger balance sheets than typical consumer technology acquirers, and a clear strategic rationale for acquiring a product with this capability. B2B recurring revenue from institutional clients typically attracts exit multiples 2–3x higher than equivalent consumer subscription revenue.

### Experience — time-locked content delivery

A more speculative but commercially interesting extension of the same mechanism to institutional use cases.

Exam paper distribution is the clearest example. Awarding bodies spend significantly on physical security for examination papers. A distribution system where papers cannot be opened before exam time — not by the school, not by the exam board's own staff — eliminates a recurring and costly failure mode. The addressable institutional market includes AQA, OCR, Pearson, and international equivalents.

Publishers and media organisations routinely manage content under embargo. A seal that opens automatically at a specified time, without any single party holding a key, solves an active operational problem. Corporate governance use cases — sealed board resolutions, succession plans, acquisition terms pending regulatory clearance — represent an enterprise B2B market served today by expensive law-firm escrow arrangements.

This segment is best treated as a platform extensibility demonstration at the current stage rather than a near-term commercial focus.

---

## The patent

The specific combination of techniques that produces the access window has been checked thoroughly against academic literature and international patent databases. No active patent was found covering this specific combination of properties in a deployable system. A UK patent application is being prepared for filing in mid-2026.

Filing establishes a priority date — from that point, Heirlooms' claim to this IP is dated and protected. A 12-month window then opens to pursue international coverage via the Patent Cooperation Treaty, preserving rights across 150+ countries.

Based on comparable technology acquisitions, a granted patent on a novel cryptographic construction in this space typically contributes 20–40% to acquisition valuations — representing an estimated £300,000–£900,000 in incremental exit value over and above the revenue multiple, at the central planning scenario.

One timing risk worth naming: patent applications are not published for 18 months after filing, meaning a competing application could exist in a pending and unsearchable state. The correct response is to file as quickly as possible and establish a priority date. Every week without a filing is a week of uninsured exposure to this risk.

---

## Post-quantum posture — a genuine differentiator

For a product designed to store personal data for decades, the post-quantum question matters. Heirlooms has designed for this from the start.

All content is encrypted with AES-256-GCM, which is quantum-safe. The encryption scheme is designed with cryptographic agility built in from day one: every encrypted blob carries an explicit algorithm identifier, meaning new post-quantum algorithms can be added without changing the file format and without losing access to existing content. The platform can migrate its key-wrapping layer to post-quantum standards without bulk re-encryption of stored content — the correct architecture for a long-lived E2EE system.

No competing product at this stage has designed for post-quantum migration with this level of architectural foresight.

---

## Development status

Heirlooms is a working product in active use, not a proposal.

The backend runs on Google Cloud infrastructure and has been in continuous development since April 2026, with consistent weekly releases throughout. The Android application is production-ready. The web application is live. An iOS application is in active development. A first external user was onboarded in May 2026 — E2EE from their first upload, with no "we'll add encryption later" caveat.

All content stored today is end-to-end encrypted. The server holds only ciphertext; staff access to the full database reveals nothing readable. This is a technical fact, not a policy position.

The time-capsule mechanism — the access window with lower and upper bounds — is the next major development milestone. The cloud infrastructure, encryption architecture, and key management system on which it will run are already in production.

---

## Summary

Heirlooms has built a working multi-platform E2EE personal archive and is filing a patent on a novel cryptographic construction that gives the product a property no competitor currently offers: a mathematically enforced time window on content access, with a genuine lower bound and a genuine upper bound. The construction applies across three commercially distinct markets — personal digital legacy, care technology, and institutional content delivery — each with a different and identifiable set of potential acquirers. The patent protects the construction across all three.

---

*The financial projections in this document are illustrative estimates based on market research and comparable transactions. They are not audited figures and should not be relied upon as a formal valuation. This document is confidential. If you are considering an investment in Heirlooms Digital Ltd, please contact the company directly for current financial information and relevant disclosures.*
