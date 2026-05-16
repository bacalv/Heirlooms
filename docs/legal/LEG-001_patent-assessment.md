# LEG-001 — Patent Assessment: Window Capsule and Related Novel IP

**Prepared by:** Legal Counsel  
**Date:** 2026-05-16  
**Status:** Final  
**Audience:** CTO (Bret Calvey)  
**Inputs:** RES-002 (Research Manager), docs/envelope_format.md, tasks/done/ARCH-007, docs/briefs/ARCH-003, docs/PA_NOTES.md

---

## Legal question

Does Heirlooms possess novel, patentable intellectual property in its cryptographic
constructions — specifically (1) the window capsule construction, (2) the versioned
E2EE envelope format with DEK-per-file model, and (3) the HMAC-tokenised E2EE tag
scheme? If so, what filing strategy is appropriate, and what are the disclosure risks?

---

## Applicable law and jurisdiction

**UK patent law:** Patents Act 1977 (as amended), sections 1–4 set the core criteria:
novelty, inventive step, industrial application, and excluded subject matter.

**Excluded subject matter (s.1(2)):** "A program for a computer as such" is excluded.
However, a computer-implemented invention that produces a "technical effect" going
beyond the normal physical interactions of running a program is patentable. Following
the UK Supreme Court's decision in *Emotional Perception AI Limited v Comptroller
General of Patents, Designs and Trade Marks* [2026] UKSC 3 (11 February 2026), the
UK has abandoned the Aerotel four-step test and now aligns with the EPO's approach in
G1/19. The threshold for patentability of computer-implemented inventions is materially
lower than it was before February 2026: if the claim requires any hardware at all, it
is not excluded "as such." The technical-character assessment then filters out purely
abstract features, but the remaining technical contribution is assessed against
novelty and inventive step in the normal way.

**Cryptographic methods in UK/EPO practice:** A new cryptographic algorithm or
protocol that provides enhanced security for stored or transmitted data has long been
treated as having a "technical character" — it solves a technical problem (information
security) by technical means. The window capsule, the envelope format, and the tag
scheme all fall within this established category. They are not business methods, mental
acts, or aesthetic creations. The 2026 Supreme Court ruling makes the patentability
position even more favourable than before.

**PCT route:** The Patent Cooperation Treaty provides a single international filing
that preserves the right to enter national phase in 150+ countries (including the US,
EU via EPO, and major markets) within 30 months of the priority date. A UK patent
application can serve as the priority filing, giving a 12-month window in which to
file a PCT application claiming the UK priority date.

**No UK "provisional" application in the US sense:** The UK does not have a
provisional patent application procedure as the US does. However, a UK filing
accomplishes the same purpose — it establishes a priority date immediately and can
be followed by a PCT or further national filings within 12 months. The UK filing
does not need to be in final claim form when filed; the description and priority date
are what matter at the outset.

**Novelty clock:** Under the Patents Act 1977 s.2(1), an invention is novel if it
does not form part of the state of the art. The state of the art includes everything
made available to the public before the priority date — including publications,
disclosures in conference papers, open-source code, and public GitHub repositories.

---

## Construction 1: Window capsule (tlock + Shamir threshold deletion)

### Novelty finding

The Research Manager's literature review (RES-002) is the governing input here.
Having reviewed it carefully alongside the patent search results from my own research,
my assessment is as follows.

**The individual components are all prior art:**
- tlock/drand (IBE over BLS12-381): published ePrint 2023/189, deployed in
  production by the League of Entropy since 2023.
- Shamir Secret Sharing: Shamir (1979), ubiquitous prior art.
- XOR blinding of a key: a standard technique in cryptographic engineering.
- Time-based deletion of key material: generally described in the literature
  (Xu, Zhang, Yang 2014; AWS Nitro KMS key deletion patterns).

**What is novel is the specific combination:**

1. Using tlock/drand (a decentralised IBE beacon, not a centralised time server)
   as a *trustless* lower bound on decryption access.
2. Using Shamir threshold deletion as an *upper bound* — the expiry guarantee.
3. Combining K_a (tlock-derived) and K_b (Shamir-reconstructed) by XOR, such that
   neither component alone permits decryption of the DEK.
4. Embedding this construction within a consumer-facing versioned envelope format with
   specific algorithm IDs and a defined protocol for custodian deletion certificates.

**Closest prior art reviewed:**

- *Timed Secret Sharing* (Kavousi, Abadi, Jovanovic, ASIACRYPT 2024): establishes the
  formal framework for both lower and upper time bounds in secret sharing. This is the
  most important prior art. It is a research paper, not a patent. It does not use
  drand/tlock and does not provide a practical deployable instantiation. The Heirlooms
  construction is a specific, distinct instantiation of this framework using
  production-ready components not described or anticipated in that paper.
- *Time-Specific Encryption* (Paterson and Quaglia, SCN 2010): defines a time-interval
  encryption primitive requiring an online time server. Structurally different from the
  Heirlooms construction (no deletion mechanism; requires an online server throughout
  the window).
- US20060155652A1 ("Expiring Encryption", filed 2004): abandoned; uses a hardware
  secure clock, not secret sharing; no XOR blinding; no threshold deletion.
- WO2008127446A2 ("Time-Lapse Cryptography", 2008): covers Feldman VSS for the lower
  bound only; no upper bound / expiry mechanism.
- US8918651B2 ("Cryptographic Erasure"): covers deletion of encrypted data records for
  HIPAA retention, not cryptographic time-windowing of a reconstruction key.

**No active patent found** describing: drand/tlock IBE lower bound + XOR blinding +
Shamir threshold deletion upper bound as a combined construction.

**Novelty verdict:** Novel as a complete practical system. The combination is not
anticipated by any prior art found. The Kavousi et al. paper is in the state of the
art (as a publication, not a patent) and limits the breadth of any abstract claim on
"time-windowed secret sharing" — but it does not anticipate the specific Heirlooms
instantiation. Inventive step (non-obviousness) is present: the specific insight that
drand/tlock provides a trustless lower bound that can be XOR-combined with a
threshold-deleted Shamir share is not derived in an obvious way from the prior art,
even with the Kavousi et al. paper in the picture.

### Patentability verdict

**Likely patentable.** The construction has technical character (it is a specific
cryptographic protocol producing a defined security outcome), is novel, and has
inventive step. Following *Emotional Perception* [2026] UKSC 3, the UK computer-
implemented invention exclusion is not a bar. The claim should be directed at the
specific combination of components and their interaction, not at the general concept
of time-windowed encryption (which would face an obviousness challenge in light of
Kavousi et al. and Paterson-Quaglia).

The strongest claim is narrow and specific. Broad claims on "time-windowed encryption
with expiry" are not viable — they would be anticipated by the Kavousi et al. paper
and the Paterson-Quaglia work. The defensible claim space is the specific instantiation.

### Strongest defensible claim (draft language)

Independent claim — method:

> A method for time-windowed cryptographic access to encrypted content, comprising:
>
> (a) generating a content encryption key (DEK) and a window key K_window, wherein
> K_window = K_a XOR K_b, K_a being a first key component sealed under an identity-based
> encryption scheme keyed to a decentralised randomness beacon (drand) round
> corresponding to a lower-bound time T_unlock, and K_b being a second key component
> independently generated and split into N shares using a threshold secret sharing
> scheme;
>
> (b) distributing each of the N shares to a respective custodian node, wherein each
> custodian node is configured to release its share only during the time interval
> [T_unlock, T_expire], and to irreversibly delete its share at T_expire;
>
> (c) encrypting content under the DEK and encrypting the DEK under K_window to
> produce an encrypted content record;
>
> (d) such that, prior to T_unlock, K_a is cryptographically inaccessible regardless
> of the custodian nodes' cooperation; and after T_expire, K_b is irrecoverable by
> virtue of threshold custodian deletion, rendering K_window and therefore the DEK
> permanently irrecoverable notwithstanding that K_a has become permanently public.

Dependent claims should add:

- The specific use of drand/BLS12-381 as the randomness beacon.
- The specific XOR combination of K_a and K_b for window key derivation.
- The deletion certificate protocol (custodian commitment at sealing time; deletion
  attestation at T_expire).
- The integration with the versioned envelope format (algorithm IDs:
  window-aes256gcm-v1, tlock-window-meta-v1).
- The proactive secret sharing refresh enhancement for windows exceeding a
  defined duration.
- A system claim directed at a distributed system comprising: a sealing client,
  N custodian nodes, and a drand randomness beacon.

**Attorney instruction needed before finalising claim language.** The draft above is
for strategic assessment only. A patent attorney experienced in cryptographic
method claims should be engaged before any filing.

### Trade secret alternative

If Bret decides not to file a patent, the construction can be maintained as a trade
secret, provided:

1. The implementation details are not disclosed publicly (no open-source publication of
   the window capsule protocol without prior NDA or patent filing).
2. The GitHub repository remains private until a provisional filing is made.
3. Any investor or partner discussions about the window capsule are conducted under
   NDA before any filing.

The weakness of trade secret protection here is real: the Kavousi et al. paper is
already public and establishes the framework; an independent implementor who reads
that paper and then reaches the same combination (drand + Shamir deletion) would
have an independent invention defence against any trade secret claim. Trade secret is
the fallback if patent is not pursued, not a strategic substitute.

---

## Construction 2: Versioned E2EE envelope with DEK-per-file model

### Novelty finding

The envelope format (`docs/envelope_format.md`) comprises:
- A binary container with a one-byte version prefix identifying the wire layout.
- An explicit algorithm ID string per envelope (not a fixed-length type field), allowing
  cryptographic agility: new algorithm IDs can be added without changing the binary
  structure, and unknown IDs fail loudly.
- A DEK-per-file model: each file is encrypted under its own 256-bit AES-GCM key.
- Multiple wrapping formats for the DEK (symmetric under master key, ECDH to device
  pubkey, Argon2id under passphrase, tlock IBE, Shamir shares), all versioned by
  the same algorithm ID scheme.

**Prior art on the individual components:**
- AES-256-GCM for content encryption: standard.
- ECDH key agreement for key wrapping: standard (P-256 ECDH + HKDF).
- Per-file DEKs in E2EE systems: used in Signal, iMessage, ProtonMail, and the
  academic literature (e.g. EP2215795B1 on E2EE key-per-message schemes).
- Algorithm agility in cryptographic protocols: well-established in TLS, JOSE (JWA),
  and the academic literature.
- UTF-8 algorithm identifier strings within encrypted blobs: similar to JOSE (JWA/JWE)
  header fields.

**Assessment of novelty:**

The specific combination in Heirlooms' envelope format is a practical engineering
design with careful properties, but the individual elements and their general
combination are well-established. The format is most analogous to JOSE/JWE with
compact binary encoding — the conceptual territory of algorithm-ID-per-envelope is
occupied by JOSE. The DEK-per-file model is described in multiple academic and
product contexts. The design is *good* (it is cleanly extensible, cross-platform,
and has a safe failure mode on unknown IDs) but the combination of these known
techniques does not clearly rise to the level of inventive step required for patent
protection.

The specific claim that would need to be made is: that the exact combination of
(1) variable-length UTF-8 algorithm ID within the binary envelope prefix, (2)
DEK-per-file, and (3) multi-format DEK wrapping under a single container structure
is novel and non-obvious. I do not assess that this combination clears inventive
step. JOSE/JWE and Signal's ratchet design together constitute a strong obviousness
argument.

### Patentability verdict

**Weak — not recommended for independent patent filing.** The envelope format itself
is more likely to be characterised as an obvious engineering implementation of
well-known cryptographic agility principles. The window capsule construction (above)
is the novel layer; it happens to be integrated into this format, and that integration
can be claimed as a dependent claim of the window capsule patent. Filing an independent
patent on the envelope format alone would likely fail examination on obviousness
grounds given the JOSE/JWE prior art and the depth of the DEK-per-file literature.

It may be possible to argue a narrow claim based on the specific algorithm ID scheme
combined with the loud-failure-on-unknown-ID design principle in an E2EE context. This
is marginal territory and not worth the filing cost on a standalone basis.

### Strongest defensible claim (draft language)

If pursuing as a dependent claim only (part of the window capsule patent):

> An encrypted data system as claimed in claim [X], wherein the encrypted content
> record is structured as a self-describing binary envelope comprising: a single-byte
> format version field identifying the wire layout; a variable-length algorithm
> identifier field encoded as a UTF-8 string of up to 64 bytes, identifying the
> cryptographic scheme applied to the ciphertext field; and wherein a client
> encountering an unrecognised algorithm identifier value is required to fail loudly
> with an exception identifying the unrecognised identifier, without silently
> misinterpreting the ciphertext.

This may have marginal independent utility as a safety/correctness claim, but it is
not a primary commercial asset.

### Trade secret alternative

The specific implementation (algorithm ID table, binary layout, cross-platform
round-trip discipline, nonce generation policy) should be documented internally and
treated as a technical trade secret to the extent it reflects non-obvious engineering
choices. In practice, the format will be discoverable by examining the binary blobs,
so technical trade secrecy is weak here. The better protection is through
implementation excellence (cross-platform conformance testing, server-side validation)
and the competitive moat of being a working multi-platform E2EE product.

---

## Construction 3: E2EE tag scheme (HMAC tokenisation + encrypted display names)

### Novelty finding

The ARCH-007 tag scheme comprises:
- Tag tokens: HMAC-SHA256 keyed with HKDF(master_key, "tag-token-v1"), with the
  tag value as the HMAC data. The server stores tokens, never values.
- Tag display names: AES-256-GCM ciphertext of the tag value, stored alongside the
  token, decrypted client-side for display.
- Per-user isolation: tokens are keyed to individual user master keys; the same tag
  value produces different tokens for different users.
- Per-member tags: a `member_tags` table allows one user to tag shared content
  invisibly to other members.
- Auto-tagging loop prevention: a separate HKDF context ("auto-tag-token-v1") for
  auto-applied tags prevents trellis criteria (which match only "tag-token-v1") from
  being re-triggered by auto-tags.

**Most directly relevant prior art:**
- US9454673B1 (Skyhigh Networks, "Searchable encryption for cloud storage"): tokenises
  keywords using HMAC-SHA1 and appends tokens to encrypted file headers to enable
  cloud-side search. This patent covers the general concept of HMAC-tokenised
  keywords for searchable encryption. It is an active, assigned US patent.
- US9256764B2 (searchable encrypted data): covers HMAC-based field tokenisation for
  database records, where HMAC(k, field_value) is stored rather than the plaintext.
- US8812867B2 (searchable symmetric encryption): covers key-word-level HMAC tokens
  for E2EE search.
- CN110086830A: HMAC-tokenised tag storage for sensitive data.

**Assessment:**

The general concept of HMAC-tokenising searchable fields in an E2EE context is
well-established in both the academic literature (searchable symmetric encryption) and
patent databases. US9454673B1 in particular is a live US patent covering HMAC
tokenisation of keywords combined with AES-GCM encryption of the content, which
directly anticipates the core of the ARCH-007 tag scheme for US purposes. The
combination of HMAC tokens with encrypted display names is a natural and obvious
extension — an examiner would likely find that adding an encrypted plaintext copy
alongside the token is an obvious design choice for a system that needs both
searchability and display.

**Potentially distinguishing elements:**
- The per-user isolation mechanism (HKDF-derived token key per user's master key,
  rather than a shared system key) ensures different users produce different tokens
  for the same value. This is not found in explicit form in US9454673B1 (which uses
  a system-level HMAC key, not per-user HKDF).
- The per-member tagging on shared content (member_tags table) creates a new privacy
  property: tag visibility is scoped to the individual member, not the item.
- The auto-tag loop prevention via separate HKDF context is a specific operational
  safety mechanism not described in the prior art.

The per-user isolation + per-member tags + auto-tag loop prevention combination is
potentially distinguishable. However, the base technology is clearly in the art, and
US9454673B1 creates a meaningful obstacle to a broad US claim. A narrow claim focused
specifically on per-user HKDF derivation within an E2EE system where the server is a
zero-knowledge party might survive examination, but it is a borderline case.

### Patentability verdict

**Weak for a standalone patent; possible as a narrow dependent claim.** The base
HMAC tokenisation scheme is prior art. The per-user HKDF isolation and per-member
tagging are the most defensible differentiators, but the distance from US9454673B1
is not large. The risk of a failed or expensive examination is high relative to the
strategic value of the standalone claim.

This construction is better protected as a trade secret in the near term, with
the possibility of adding it as a dependent claim in a broader Heirlooms patent
application covering the window capsule.

**US filing risk is particularly high** given US9454673B1 (assigned to Skyhigh
Networks, now part of Skyhigh Security / McAfee). If Heirlooms ever commercialises
the tag scheme and enters the US market, freedom-to-operate analysis against
US9454673B1 is essential before launch. This is a separate task from patenting.

### Strongest defensible claim (draft language)

If pursuing as a dependent claim:

> A method for privacy-preserving tag storage in an end-to-end encrypted system,
> comprising:
>
> (a) for each user, deriving a tag token key T_k from the user's master encryption
> key using a key derivation function (KDF) with a context string identifying the
> tag tokenisation purpose;
>
> (b) computing, for each tag value, a tag token = MAC(T_k, tag_value), wherein the
> MAC key T_k is unique to each user such that the same tag value produces distinct
> tokens for distinct users;
>
> (c) storing only the tag token on a server, such that the server is unable to
> determine the semantic content of any tag from the stored token alone;
>
> (d) computing, for shared content items, a per-member tag association stored in a
> member-specific record, wherein the per-member record is inaccessible to other
> members and to the server's operational staff; and
>
> (e) deriving a separate auto-tag token key using a distinct KDF context string, such
> that auto-applied tags produce tokens that are distinguishable from user-applied
> tags by the server, preventing automated tag-application processes from re-triggering
> criteria that match only user-applied tag tokens.

### Trade secret alternative

The ARCH-007 tag scheme is a strong candidate for trade secret treatment:
- The scheme is not trivially reverse-engineered from black-box observation of the API
  (the server only exposes tokens, not values or the derivation structure).
- The per-user HKDF derivation, the HKDF context strings, and the loop-prevention
  mechanism are implementation details not visible from the binary protocol.
- The `member_tags` table schema and the scope of member-tag visibility are
  architectural decisions that are not apparent from client behaviour.

Recommended approach: treat as a trade secret now. Do freedom-to-operate analysis
against US9454673B1 before US launch. Revisit patentability after the window capsule
filing is complete.

---

## Filing strategy recommendation

**Priority order:**

1. **Construction 1 (window capsule) — file first.** This is the strongest novel
   construction with the clearest inventive step. It is the construction most at risk
   from disclosure (RES-002 is committed to this private repository; if the repo ever
   becomes public, or if this document is shared externally, the novelty clock starts).
   File a UK patent application immediately to establish a priority date.

2. **Construction 3 (tag scheme) — dependent claim only, if filing at all.** If a UK
   patent application is filed on the window capsule, consider adding the per-user
   HKDF tag isolation as a dependent claim in the same application. This adds
   marginal cost. An independent filing is not recommended.

3. **Construction 2 (envelope format) — no independent filing recommended.** Include
   as a dependent claim describing the integration of the window capsule within the
   envelope format. This adds no extra cost if included in the window capsule
   application.

**UK filing route:**

File a UK patent application at the UK IPO to establish the priority date. This
requires:
- A description of the invention (can be a draft specification — a full claims draft is
  not required at the initial filing date for UK purposes, as long as it is added
  within the examination timeline).
- Application fee: £75 (post April 2026 increase).
- Search fee: £200.
- Examination fee: £130.
- Total UK IPO official fees: approximately £405.

These official fees are trivial. The significant cost is external patent attorney fees
for drafting the specification and claims. For a cryptographic method claim of this
technical complexity, expect £8,000–£15,000 in attorney fees for drafting and filing a
UK application. This is an order-of-magnitude estimate; specialist cryptographic patent
attorneys at the upper end of the London IP market will charge more.

**PCT route (international):**

Within 12 months of the UK priority filing, file a PCT application to preserve rights
in 150+ countries. The PCT international filing fee is approximately 1,400 CHF (around
£1,200) plus search fees of £1,300–£2,000 depending on the International Searching
Authority. Attorney fees for the PCT stage add another £3,000–£6,000.

National phase entry (choosing which countries to pursue patents in) occurs at 30
months from priority. Each national phase costs translation fees, local filing fees,
and local attorney fees — typically £3,000–£8,000 per jurisdiction. The US, EU (EPO),
and key commercial markets (Australia, Canada) are the most commercially relevant
choices for Heirlooms.

**Realistic total cost estimate:**
- UK filing (first 12 months): £10,000–£20,000 including attorney drafting.
- PCT filing (months 12–18): £5,000–£10,000 including attorney fees.
- National phase (months 18–30 and beyond): £15,000–£40,000 depending on number of
  jurisdictions pursued.
- **Total to reach granted patents in UK + US + EU:** £30,000–£70,000 over 3–5 years.

This is a significant investment for a pre-revenue startup. The strategic question is
whether the window capsule is sufficiently core to Heirlooms' long-term defensibility
and exit value to justify this expenditure. My view: the UK filing cost (£10,000–
£20,000) is justified immediately. The PCT and national phase decisions should be made
once the product is generating revenue or Bret is in fundraising discussions where IP
portfolio matters.

**Minimum viable action:** UK provisional filing only, to establish priority date,
at a cost of approximately £10,000–£15,000 in attorney fees. This buys a 12-month
window to decide on PCT without disclosing anything publicly.

---

## Disclosure risk assessment

### What constitutes a disclosure that starts the novelty clock

Under Patents Act 1977 s.2(2), the state of the art includes "everything made available
to the public." This includes:

- Making a GitHub repository public (even for a moment).
- Publishing a blog post, technical article, white paper, or conference paper describing
  the construction.
- Presenting the construction at a meetup, conference, or investor pitch without an NDA.
- Filing a research paper (e.g. submitting RES-002 to a conference or preprint server).
- An oral disclosure to a third party not covered by a confidentiality agreement.

UK patent law has no grace period for self-disclosure. If Bret discloses the
window capsule construction publicly *before* a UK patent application is filed, the
invention falls into the state of the art and the UK patent cannot be granted.
(Note: the US has a 12-month grace period for the inventor's own disclosures, but
the UK and EU do not.)

### Current disclosure status

Based on my review of the repository:

- The GitHub repository (`github.com/bacalv/Heirlooms`) appears to be private. If
  correct, no public disclosure has occurred through the repository.
- RES-002 is committed to the private repository. If the repository remains private,
  this is not a public disclosure.
- The window capsule construction is described in RES-002 and ARCH-003 in sufficient
  detail to constitute full disclosure if those documents were made public.
- No evidence of public conference presentation, blog post, or investor pitch on the
  window capsule construction.
- No evidence of pre-print submission of the Research Manager's analysis.

**Assessment: no public disclosure has yet occurred** — but the risk is live. The
construction is fully documented internally. Any move toward open-sourcing, a public
technical blog, or a fundraising deck that describes the window capsule would start
the clock.

### What Bret must avoid until a provisional filing is made

1. Do not make the GitHub repository public.
2. Do not publish RES-002, ARCH-003, ARCH-006, or this document externally without a
   patent filing already in place.
3. Do not present the window capsule construction at a conference, meetup, or podcast
   without an NDA with each attendee — and NDAs at conferences are not practicable.
   The rule is: do not present it publicly.
4. Do not include window capsule technical details in a fundraising deck shared with
   investors without a prior NDA. NDAs are normal and achievable in investor contexts —
   but they must be in place before the meeting.
5. Do not commission academic collaborators to publish on this construction without
   first filing.
6. Do not submit RES-002 or any derivative to a preprint server (arXiv, IACR ePrint)
   or academic conference.

The Research Manager's reference to IACR ePrint 2023/189 (the tlock paper) indicates
awareness of that publication community. RES-002 should not be submitted there.

---

## IP ownership check

### Current position (best assessment from available information)

The PA_NOTES.md and ROADMAP.md refer to "Bret" as the CTO and sole operator. The
Legal persona brief flags that IP should be clearly owned by the company, not Bret
personally as a sole trader. The retirement questionnaire (RET-001) asks whether
Heirlooms is operated through a limited company — the answer is not in any document
I have access to.

**This is an urgent gap.**

**If Heirlooms is not yet incorporated as a limited company:**
- The IP in the window capsule, envelope format, and tag scheme is currently owned
  by Bret personally as its inventor (Patents Act 1977 s.7 — the inventor is entitled
  to the patent unless an employer-employee relationship or agreement provides
  otherwise).
- This is not inherently wrong, but it creates structural problems for:
  - Exit readiness: acquirers and investors expect IP to be held by the legal entity
    being acquired, not a natural person.
  - Tax efficiency: IP held personally may attract different CGT treatment than IP
    held within a company.
  - Third-party contractor contributions: any contractor who has written code
    implementing these constructions may have moral rights and potentially IP rights
    that need to be assigned.

**If Heirlooms is already incorporated:**
- Any IP created by Bret in the course of his duties as an employee or director of the
  company automatically vests in the company under Patents Act 1977 s.39.
- Any contractors who have contributed to the implementation need IP assignment
  agreements. Without these, there is a risk that contractors retain IP in their
  contributions.

**Contractor IP assignment status:**
The codebase (HeirloomsServer, HeirloomsApp, HeirloomsWeb, HeirloomsiOS) has clearly
been built over multiple milestones. If external contractors contributed code,
particularly to the cryptographic paths (M7 E3/E4, M11 crypto), written IP assignment
agreements are essential. The existence of such agreements is not confirmed in any
document I have reviewed.

### Required immediate action on IP ownership

Before a patent application is filed, the inventorship and ownership chain must be
clean. A patent application naming the wrong owner, or failing to name all inventors,
can be challenged and potentially invalidated after grant.

---

## Recommended immediate actions

1. **Establish whether Heirlooms is incorporated.** If not, incorporate immediately.
   A simple UK Ltd company incorporation costs approximately £50 online and takes
   hours. IP assignment from Bret personally to the company should follow as part of
   the incorporation process.

2. **Commission a UK patent attorney experienced in cryptographic methods.** Provide
   them with this brief and RES-002 as background. Ask them to produce a full patent
   specification and independent claim set for the window capsule construction. Target
   a UK filing within the next 8 weeks (i.e., by mid-July 2026 at the latest, assuming
   no prior disclosure has occurred).

3. **Keep the GitHub repository private.** Do not make it public, even briefly, until
   after a patent application has been filed. If open-sourcing is a long-term goal,
   that conversation happens after the patent is secured.

4. **Conduct freedom-to-operate (FTO) analysis on the tag scheme against US9454673B1**
   before Heirlooms launches publicly in the US. This is separate from patenting and
   should be tasked to the same attorney.

5. **Obtain IP assignment agreements from all contractors.** Identify anyone who has
   contributed code to the cryptographic paths (M7 E3/E4 envelope implementation,
   M11 capsule crypto, M10 plot key sharing). Obtain signed written assignments before
   the patent application is filed. UK law requires the assignment to be in writing
   and signed; a properly worded IP assignment clause in a past contract may suffice
   if it covers the specific work — have an attorney review each contract.

6. **Execute NDAs before any investor or partner discussions about the window capsule.**
   This is standard practice and investors expect it. Do not present the window capsule
   technical details in any fundraising meeting without a signed NDA first.

7. **Brief the Research Manager and Technical Architect** that RES-002 is patent-
   sensitive and must not be shared outside the private repository until a patent
   application is filed. This should be communicated as a standing restriction.

8. **Evaluate PCT strategy at the 6-month mark.** Once a UK application is filed and
   the 12-month priority window begins, assess which international markets matter for
   commercial strategy. The PCT decision should align with fundraising and market
   expansion plans. Do not commit PCT fees until there is either investor capital or
   revenue to support the international portfolio cost.

---

## When to instruct external counsel

**Immediately, for:**
- Patent application drafting and filing (UK IPO). Internal advice is insufficient —
  a qualified patent attorney (registered with CIPA, the Chartered Institute of Patent
  Attorneys) must draft and file the application. Mistakes in claim drafting at this
  stage are difficult and expensive to correct later.
- IP assignment agreements with contractors, if these do not already exist.

**Within 6 months, for:**
- PCT filing strategy — same attorney can advise.
- Freedom-to-operate analysis against US9454673B1 and US8812867B2 for the tag scheme.
- Company structure review for IP holding if not yet incorporated.

**Before US launch:**
- US FTO clearance for the tag scheme.
- US patent prosecution — US patent attorneys work differently from UK patent attorneys;
  the UK/PCT attorney should have a US correspondent firm relationship.

---

## PA Summary

**For:** CTO (Bret Calvey)  
**Urgency:** High — the window capsule is fully documented in the private repository;
any public disclosure starts the UK novelty clock with no grace period.

**Three-line verdict:**
1. The window capsule (tlock + Shamir threshold deletion) is likely patentable in the
   UK and internationally. File a UK patent application within 8 weeks. Attorney cost:
   £10,000–£15,000.
2. The envelope format and tag scheme do not warrant independent patent filings but
   should be added as dependent claims in the window capsule application and assessed
   for FTO risk (especially US9454673B1 for the tag scheme in the US market).
3. IP ownership must be confirmed before filing — is Heirlooms incorporated? Are
   contractor IP assignments in place? Answer these questions this week.

**Decisions needed from CTO:**

1. Is Heirlooms operated through a limited company? If not, incorporate immediately.
2. Authorise engagement of a specialist cryptographic patent attorney (CIPA-registered).
   I can provide a shortlist of firms if helpful.
3. Identify all contractors who contributed to M7, M10, or M11 cryptographic code and
   confirm whether IP assignment agreements are in place.
4. Confirm GitHub repository will remain private until post-filing.
5. Decide at the 6-month mark whether to pursue PCT (international) filing — defer
   this decision until funding or commercial traction supports the cost.
