# Tag Metadata Leakage — Accepted Residual Risk

*Authored: 2026-05-16. Status: accepted. Relates to: ARCH-007, SEC-012.*

---

## Summary

The E2EE tag scheme specified in ARCH-007 replaces plaintext tag strings with
HMAC-SHA256 tokens derived from each user's master key. This eliminates semantic
tag leakage entirely: the server can never reconstruct what a tag means. However,
the token scheme introduces a class of residual metadata leakage through token
correlation. This document formally records that leakage, confirms the privacy
guarantees the scheme does provide, accepts the residual risk, and provides the
user-facing disclosure wording.

---

## What the server CAN observe

Because tags are deterministic per user (the same tag value on the same user's
account always produces the same HMAC token), the server can draw the following
structural inferences from the token data alone:

### 1. Tag equality within a user's vault

If two uploads in a user's vault carry the same token, the server can infer that
both items carry the same tag — without knowing what that tag is. This allows the
server to group or cluster a user's items by shared tag identity.

### 2. Tag frequency

The server can count how many items a given token appears on. This reveals which
tags a user applies most often and how evenly or unevenly tag usage is distributed
across the vault.

### 3. Tag co-occurrence

The server can observe which pairs (or larger sets) of tokens appear together on
the same item. For example, if token A and token B always appear together, the
server can infer that the two tags are frequently applied as a pair, without
knowing what either tag means.

### 4. Tag count per item

The length of the `tag_tokens` array for each upload reveals how many tags that
item carries. This is a minor structural leak that cannot be eliminated without
padding.

### 5. Trellis criteria correlation

When a user creates or updates a trellis with a tag-based criteria atom, the
criteria JSONB stores the tag token. The server can correlate this token against
all uploads bearing the same token, effectively mapping the trellis to the set of
tagged uploads. This does not reveal the tag's meaning, but does reveal which items
would be matched by that trellis rule.

---

## What the server CANNOT observe

- **Semantic meaning.** The server cannot reconstruct any tag value from a token.
  HMAC-SHA256 with a 256-bit key is a one-way pseudorandom function; inverting it
  is computationally infeasible.

- **Cross-user correlation.** Tokens are keyed to each user's individual
  `tag_token_key`, which is derived from that user's master key via HKDF. Two
  users who apply the same tag value (for example, "birthday") produce entirely
  different tokens. The server cannot determine whether any two users share a tag.

- **Tag display names.** Tag display names are encrypted client-side using
  AES-256-GCM under a per-user key derived from `master_key` with the
  `"tag-display-v1"` HKDF context. The server stores only ciphertext envelopes;
  it never has the decryption key.

---

## Threat model verification

ARCH-007 claims the following privacy properties. Each is verified here against
the scheme as designed:

| Claim | Verification |
|---|---|
| Server cannot read tag values | Confirmed. Only HMAC tokens and AES-GCM ciphertexts are stored. No plaintext reaches the server after Phase 3 enforcement. |
| Tokens are per-user | Confirmed. `tag_token_key = HKDF(master_key, info="tag-token-v1")`. Different users have different master keys, so the same tag value maps to different tokens. |
| Cross-user correlation is impossible | Confirmed. There is no shared token namespace across users; token equality is meaningful only within a single user's vault. |
| Display names are confidential | Confirmed. `tag_display_key = HKDF(master_key, info="tag-display-v1")`. The server stores encrypted blobs; the key never leaves the client. |
| Auto-tag tokens cannot trigger trellis re-evaluation | Confirmed. Auto-tag tokens use a separate HKDF context (`"auto-tag-token-v1"`) and a distinct column; `CriteriaEvaluator` never queries them for tag criteria. |

The residual leakage (equality, frequency, co-occurrence, tag count, trellis
correlation) is a structural consequence of deterministic token derivation and
server-side trellis evaluation. It cannot be eliminated within the current
architecture without moving tag evaluation fully to the client.

---

## Formal acceptance of residual risk

**Risk ID:** SEC-012-TAG-METADATA  
**Severity:** Low  
**Likelihood:** Requires an adversarial Heirlooms insider or a compromised server  
**Impact:** Structural metadata about tag usage patterns; no semantic content exposed  

**Acceptance rationale:**

The residual leakage is an acceptable trade-off for two reasons:

1. **Practical privacy is preserved.** The information available to the server
   (token correlation patterns) provides no semantic content about the user's tags.
   A hostile insider would learn only that certain items cluster together — not why.
   This is analogous to a postal service knowing that you write to the same address
   frequently, without being able to read the letters.

2. **Server-side trellis evaluation is a current architectural requirement.** The
   trellis routing system evaluates criteria in PostgreSQL on the server. Moving all
   evaluation client-side would require streaming the full upload corpus to the
   client on every routing pass, which is not viable at the current scale.

**Accepted by:** Security Manager (SEC-012)  
**Date:** 2026-05-16  
**Review trigger:** Re-evaluate if tag token data is ever proposed for secondary use,
or if a viable client-side trellis evaluation architecture emerges.

**Heirlooms' commitment:**

- Heirlooms staff will never attempt to infer tag meaning from token patterns.
- Heirlooms will never sell, share, or monetise tag token data.
- Tag token data will never be used for advertising, recommendation, or profiling.
- This commitment applies in perpetuity, including in the event of acquisition.

---

## User-facing disclosure wording

The following wording is approved for use in the Heirlooms privacy policy and
in-app privacy notice. It must not be shortened or paraphrased in a way that
weakens any of the commitments below.

> *Your tags are stored as anonymous identifiers. Heirlooms cannot read what your
> tags mean. We may be able to tell that two of your items share the same tag, but
> not what that tag is. We will never sell, share, or use this information for any
> purpose.*

**Placement guidance:** This disclosure should appear in:
- The in-app privacy notice (accessible from Settings)
- The Heirlooms privacy policy, under a "Tag Privacy" or "Metadata" sub-heading
- Any onboarding flow that introduces the tagging feature

---

## Future work — client-side tag evaluation (long-term aspiration)

The residual leakage documented above is a consequence of server-side trellis
evaluation. The long-term architectural solution is to move tag criteria evaluation
fully to the client:

- The client would maintain a local index of `(tag_token, upload_id)` pairs in
  the device's secure storage (or in an encrypted local database).
- Trellis routing criteria would be evaluated locally against this index.
- The server would no longer need to compare tag tokens during query evaluation.

This would eliminate token equality, frequency, co-occurrence, and trellis
correlation leakage entirely, reducing the server's knowledge of tag structure to
zero.

**Why this is not implemented now:**

- Client-side evaluation requires a reliable local copy of all upload metadata,
  which implies a sync architecture that does not yet exist.
- Offline evaluation conflicts with server-side staging and routing guarantees
  that are part of the current trellis contract.
- The engineering cost is high relative to the severity of the residual leakage
  (Low, per the acceptance above).

This aspiration should be revisited when:
1. A local-first or offline-capable sync architecture is introduced.
2. The trellis routing system is redesigned to support client-initiated routing.
3. The user base or regulatory environment changes the acceptable risk threshold.

**Tracking:** Log this as a future-facing item in `docs/ROADMAP.md` under a
"Privacy enhancements" section when the roadmap is next updated.

---

*This document is owned by the Security Manager. Re-review is required before any
change to the tag token scheme, the trellis evaluation architecture, or Heirlooms'
data handling commitments.*
