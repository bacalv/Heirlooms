---
id: SEC-012
title: Tag metadata leakage — document and disclose accepted residual risk
category: Security
priority: Medium
status: done
depends_on: [ARCH-007]
touches:
  - docs/security/
assigned_to: SecurityManager
estimated: 2–3 hours
---

## Background

Decision made 2026-05-16 during v0.54 smoke test session. The E2EE tag scheme
(ARCH-007) uses HMAC tokens as tag identifiers. This eliminates semantic tag leakage
but introduces residual metadata leakage that must be formally accepted and disclosed.

## Residual leakage

The HMAC token scheme means the server can observe:

1. **Tag equality within a user's vault**: the server can tell that items A and B
   share the same tag token — i.e. they have the same tag — without knowing what
   the tag is.
2. **Tag frequency**: the server can count how many items a user has with a given
   token — i.e. which tags are used most.
3. **Tag co-occurrence**: the server can see which tokens appear together on the
   same item.

The server **cannot** reconstruct the semantic meaning of any tag. Tokens are keyed
to the individual user's master key, so cross-user correlation is not possible (two
users with the same tag value produce different tokens).

This is analogous to postal metadata: a postal service can see that you write to the
same address 500 times without reading the letters.

## Heirlooms' commitment

- Heirlooms staff will never attempt to infer tag meaning from token patterns.
- Heirlooms will never sell, share, or monetise tag token data.
- Tag token data will never be used for advertising, recommendation, or profiling.
- This commitment applies in perpetuity, including in the event of acquisition.

## Deliverables

1. Add a **Security model** section to `docs/` (or update existing threat model)
   documenting the tag metadata leakage formally as an accepted residual risk.
2. Draft **user-facing disclosure wording** for the privacy policy / in-app notice:

   > *Your tags are stored as anonymous identifiers. Heirlooms cannot read what
   > your tags mean. We may be able to tell that two of your items share the same
   > tag, but not what that tag is. We will never sell, share, or use this
   > information for any purpose.*

3. Confirm the threat model holds under ARCH-007's scheme — verify that the token
   scheme provides the privacy properties claimed above.
4. Note any future-work items (e.g. moving to client-side evaluation to eliminate
   even this leakage) as long-term aspirations.

## Completion notes

Completed 2026-05-16 by SecurityManager.

### Files created

- `docs/security/tag-metadata-leakage.md` — the primary deliverable.

### What the document covers

1. **Residual leakage catalogue** — five specific leakage vectors are documented:
   token equality within a vault, tag frequency, tag co-occurrence, tag count per
   item (array length), and trellis criteria correlation. The last two were implicit
   in ARCH-007 but not explicitly called out in the task brief; both are real and
   included for completeness.

2. **Privacy guarantees** — formally verified that (a) semantic meaning cannot be
   recovered from tokens, (b) cross-user correlation is impossible because tokens
   are keyed per user's master key, (c) display names are confidential via
   AES-256-GCM, and (d) auto-tag namespace isolation holds.

3. **Threat model verification table** — each of the five ARCH-007 privacy claims
   is checked against the scheme design and confirmed.

4. **Formal risk acceptance** — Risk ID SEC-012-TAG-METADATA, severity Low,
   accepted with rationale (practical privacy is preserved; server-side evaluation
   is a current architectural requirement). Heirlooms' four commitments restated.

5. **User-facing disclosure wording** — verbatim from the task brief, plus
   placement guidance (Settings privacy notice, privacy policy, tagging onboarding).

6. **Future work note** — client-side tag evaluation documented as a long-term
   aspiration, with the three blockers (no local sync architecture, trellis routing
   contract, engineering cost) and three re-evaluation triggers stated explicitly.

### No code changes

SEC-012 is a documentation and risk-acceptance task only. No production code was
modified.
