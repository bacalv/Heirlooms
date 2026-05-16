---
id: LEG-001
title: Patent assessment — window capsule construction and related novel IP
category: Legal
priority: High
status: in-progress
assigned_to: Legal
depends_on: []
touches:
  - docs/legal/
  - docs/research/RES-002_window-capsule-expiry-cryptography.md
  - docs/briefs/ARCH-007_e2ee-tag-scheme.md
  - docs/envelope_format.md
estimated: 1 session
---

## Goal

Produce a patent assessment brief covering the novel technical constructions in
Heirlooms, before any of them are publicly disclosed. Assess patentability, recommend
a filing strategy, and flag timing risks.

**Urgency:** RES-002 (window capsule construction) has been committed to this
repository. If the repo ever becomes public, or if the brief is shared externally,
the novelty clock starts. A patent application should ideally be filed — or at least
a provisional application prepared — before any public disclosure.

## Constructions to assess

### 1. Window capsule — tlock lower-bound + Shamir threshold deletion upper-bound

Read `docs/research/RES-002_window-capsule-expiry-cryptography.md` in full.

The construction:
- Content encrypted under DEK
- DEK split into K_a (tlock/drand IBE-sealed — trustlessly available after unlock_time)
  and K_b (Shamir split M-of-N across independent custodians)
- K_b shares available only during [unlock_time, expire_time]
- Custodians destroy shares at expire_time
- After expire_time: K_a is permanently public but K_b is gone → content irrecoverable

Key questions:
- Is this construction novel, or does prior art in the literature anticipate it?
  (RES-002 contains a literature review — read it before forming a view)
- What is the broadest defensible patent claim?
- Is the combination patentable even if individual components (tlock, Shamir) are not?
- UK filing vs PCT (provisional, then international)? Timing?

### 2. Versioned E2EE envelope format with DEK-per-file model

Read `docs/envelope_format.md`.

The scheme:
- Every file encrypted under its own DEK
- DEK wrapped per-recipient using ECDH key agreement
- Multiple wrapping formats versioned via algorithm ID string
- Designed for cryptographic agility — algorithm IDs can be added without breaking
  existing data

Key questions:
- Is this patentable, or is it a straightforward application of well-known primitives?
- Is the cryptographic agility design (algorithm ID versioning, DEK-per-file) novel
  enough to be a defensible claim?

### 3. E2EE tag scheme — HMAC token identifiers with encrypted display names

Read `docs/briefs/ARCH-007_e2ee-tag-scheme.md`.

The scheme:
- Tags stored as HMAC tokens (server sees tokens, not values)
- Tag display names stored as AES-GCM ciphertext alongside the token
- Per-user tag isolation (tokens keyed to master key — different users, same tag
  value produce different tokens)
- Per-member tags on shared content (member_tags table)
- Auto-tagging loop prevention via separate HKDF context

Key questions:
- Novelty: is HMAC-tokenised tag storage for E2EE systems in the prior art?
- Is the per-member tag isolation scheme patentable?
- Is this stronger as a trade secret than a patent?

## Deliverables

`docs/legal/LEG-001_patent-assessment.md` covering:

1. **Per-construction assessment** — for each of the three constructions above:
   - Novelty finding (based on RES-002 literature review + your own assessment)
   - Patentability verdict: patentable / likely patentable / weak / not patentable
   - Strongest defensible claim language (draft)
   - Trade secret alternative if patent is weak

2. **Filing strategy recommendation**
   - Which construction(s) to file on, and in which order
   - UK provisional filing (IPO) vs PCT route vs both
   - Recommended timeline given disclosure risk
   - Cost estimate range (not exact — order of magnitude for CTO planning)

3. **Disclosure risk assessment**
   - Has any of this been publicly disclosed already? (Consider: GitHub repo, any
     published docs, conversations with third parties)
   - What constitutes a disclosure that starts the novelty clock?
   - What should Bret avoid doing until a provisional application is filed?

4. **IP ownership check**
   - Is it clear that all IP is owned by the company (not Bret personally)?
   - Have any contractors contributed to these constructions? If so, are IP
     assignment agreements in place?

5. **Recommended immediate actions** (numbered list)

## Completion notes

**Completed:** 2026-05-16  
**Completed by:** Legal Counsel  
**Output:** `docs/legal/LEG-001_patent-assessment.md`

**Summary of findings:**

- Construction 1 (window capsule): **Likely patentable.** Novel as a complete practical
  system. No active patent found anticipating the combination of drand/tlock IBE lower
  bound + XOR blinding + Shamir threshold deletion upper bound. Closest prior art is
  the Kavousi et al. "Timed Secret Sharing" paper (ASIACRYPT 2024, not a patent), which
  limits the breadth of any abstract claim but does not anticipate the specific
  Heirlooms instantiation. Recommend UK patent filing within 8 weeks.

- Construction 2 (versioned envelope + DEK-per-file): **Weak for independent filing.**
  Individual components and their general combination are well-established (JOSE/JWE
  prior art, DEK-per-file in Signal/ProtonMail). Include as dependent claims in the
  window capsule application. No standalone filing recommended.

- Construction 3 (HMAC tag scheme): **Weak for independent filing.** Base HMAC
  tokenisation for searchable E2EE is prior art (US9454673B1, Skyhigh Networks).
  Per-user HKDF isolation and per-member tags are distinguishing elements but margin
  is thin. Treat as trade secret; pursue FTO analysis against US9454673B1 before US
  launch; add as dependent claim if filing the window capsule patent.

**Critical IP ownership gap flagged:** No confirmation that Heirlooms is incorporated
as a limited company, or that contractor IP assignment agreements are in place. Both
must be resolved before filing. See Recommended Immediate Actions in the brief.

**Disclosure risk:** No public disclosure identified. GitHub repo appears private.
RES-002 is patent-sensitive — must not be shared externally until after UK filing.

**Next steps for CTO:**
1. Confirm company structure (incorporated? contractors?)
2. Engage CIPA-registered cryptographic patent attorney
3. Target UK filing by mid-July 2026
