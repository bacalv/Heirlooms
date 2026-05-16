# SEC-012 — Tag Metadata Leakage: Accepted Residual Risk

**Risk ID:** SEC-012-TAG-METADATA  
**Date:** 2026-05-16  
**Author:** Security Manager  
**Status:** Accepted  
**Relates to:** ARCH-007 (E2EE Tag Scheme)  
**Severity:** Low  

---

## 1. Background

The E2EE tag scheme specified in ARCH-007 replaces plaintext tag strings with
HMAC-SHA256 tokens derived from each user's master key. Prior to this scheme, tags
were stored as `TEXT[]` on the `uploads` table — a direct privacy violation of
Heirlooms' founding principle that the server should never see plaintext user content.

ARCH-007 eliminates semantic tag leakage entirely: the server can no longer reconstruct
what any tag means. However, the deterministic nature of the token derivation introduces
a class of residual metadata leakage through token correlation. This document formally
records that leakage, confirms the privacy guarantees the scheme provides, accepts the
residual risk on behalf of the security function, and provides user-facing disclosure
wording.

---

## 2. What the Server CAN Observe

Because tag tokens are deterministic per user (the same tag value on the same user's
account always produces the same HMAC token), the server can draw the following
structural inferences from token data alone:

### 2.1 Tag equality within a user's vault

If two uploads in a user's vault carry the same token, the server can infer that both
items share the same tag — without knowing what that tag is. This allows the server to
group or cluster a user's items by shared tag identity.

### 2.2 Tag frequency

The server can count how many items a given token appears on. This reveals which tags a
user applies most often and how evenly or unevenly tag usage is distributed across the
vault.

### 2.3 Tag co-occurrence

The server can observe which pairs (or larger sets) of tokens appear together on the
same item. For example, if token A and token B always appear together, the server can
infer that the two tags are frequently applied as a pair — without knowing what either
tag means.

### 2.4 Tag count per item

The length of the `tag_tokens` array for each upload reveals how many tags that item
carries. This is a minor structural leak that cannot be eliminated without padding.

### 2.5 Trellis criteria correlation

When a user creates or updates a trellis with a tag-based criteria atom, the criteria
JSONB stores the tag token. The server can correlate this token against all uploads
bearing the same token, effectively mapping the trellis to the set of tagged uploads.
This does not reveal the tag's meaning, but does reveal which items would be matched by
that trellis rule.

---

## 3. What the Server CANNOT Observe

**Semantic meaning.** The server cannot reconstruct any tag value from a token.
HMAC-SHA256 with a 256-bit key is a one-way pseudorandom function; inverting it is
computationally infeasible under current cryptographic assumptions.

**Cross-user correlation.** Tokens are keyed to each user's individual
`tag_token_key`, derived from that user's master key via:

```
tag_token_key = HKDF-SHA256(ikm = master_key, salt = [], info = "tag-token-v1")
tag_token     = HMAC-SHA256(key = tag_token_key, data = UTF-8(tag_value))
```

Two users who apply the same tag value (for example, "birthday") produce entirely
different tokens. The server cannot determine whether any two users share a tag, and
there is no shared token namespace across the user population.

**Tag display names.** Tag display names are encrypted client-side using AES-256-GCM
under a per-user key derived from `master_key` with the `"tag-display-v1"` HKDF
context. The server stores only ciphertext envelopes; the decryption key never leaves
the client.

---

## 4. Threat Model Verification

ARCH-007 claims the following privacy properties. Each is verified here against the
scheme as designed:

| Claim | Verification |
|---|---|
| Server cannot read tag values | Confirmed. Only HMAC tokens and AES-GCM ciphertexts reach the server after Phase 3 enforcement. No plaintext tag value is present in any server-readable column. |
| Tokens are per-user | Confirmed. `tag_token_key = HKDF(master_key, info="tag-token-v1")`. Different users have different master keys, so the same tag value maps to different tokens for different users. |
| Cross-user correlation is impossible | Confirmed. There is no shared token namespace across users; token equality is meaningful only within a single user's vault. The per-user keying is a cryptographic guarantee, not a policy constraint. |
| Display names are confidential | Confirmed. `tag_display_key = HKDF(master_key, info="tag-display-v1")`. The server stores encrypted blobs; the key never leaves the client. The two HKDF contexts (`tag-token-v1` and `tag-display-v1`) are independent, so knowledge of the token does not aid decryption of the display name. |
| Auto-tag tokens cannot trigger trellis re-evaluation | Confirmed. Auto-tag tokens use a separate HKDF context (`"auto-tag-token-v1"`) and a distinct column; `CriteriaEvaluator` never queries them when evaluating tag criteria. |

The residual leakage (equality, frequency, co-occurrence, tag count, trellis
correlation) is a structural consequence of deterministic token derivation combined with
server-side trellis evaluation. It cannot be eliminated within the current architecture
without moving tag evaluation fully to the client.

**Threat model holds.** The scheme provides the privacy properties claimed in ARCH-007.
The residual risks identified below are accepted and do not undermine the correctness of
the threat model.

---

## 5. Formal Acceptance of Residual Risk

**Risk ID:** SEC-012-TAG-METADATA  
**Severity:** Low  
**Likelihood:** Exploitation requires an adversarial Heirlooms insider or a
compromised server — not an external network attacker  
**Impact:** Structural metadata about tag usage patterns; no semantic content exposed;
no cross-user correlation possible  

**Acceptance rationale:**

The residual leakage is an acceptable trade-off for two reasons:

1. **Practical privacy is substantially preserved.** The information available to the
   server (token correlation patterns) conveys no semantic content about the user's
   tags. A hostile insider would learn only that certain items cluster together — not
   why. This is analogous to a postal service knowing that you write to the same
   address 500 times without being able to read any of the letters.

2. **Server-side trellis evaluation is a current architectural requirement.** The
   trellis routing system evaluates criteria in PostgreSQL on the server. Moving all
   tag evaluation client-side would require streaming the full upload corpus to the
   client on every routing pass, which is not viable at the current architecture and
   scale.

**Accepted by:** Security Manager (SEC-012)  
**Date:** 2026-05-16  
**Review trigger:** Re-evaluate if tag token data is ever proposed for any secondary
use; if a viable client-side trellis evaluation architecture emerges; or if the user
base or regulatory environment raises the acceptable risk threshold.

---

## 6. Heirlooms' Commitment

The following commitments apply regardless of the residual leakage accepted above:

- Heirlooms staff will never attempt to infer tag meaning from token patterns.
- Heirlooms will never sell, share, or monetise tag token data.
- Tag token data will never be used for advertising, recommendation, or profiling.
- This commitment applies in perpetuity, including in the event of acquisition.

---

## 7. User-Facing Disclosure Wording

The following wording is approved for use in the Heirlooms privacy policy and
in-app privacy notice. It must not be shortened or paraphrased in a way that weakens
any of the commitments stated above.

> *Your tags are stored as anonymous identifiers. Heirlooms cannot read what your tags
> mean. We may be able to tell that two of your items share the same tag, but not what
> that tag is. We will never sell, share, or use this information for any purpose.*

**Placement guidance:** This disclosure should appear in:

- The in-app privacy notice (accessible from Settings)
- The Heirlooms privacy policy, under a "Tag Privacy" or "Metadata" sub-heading
- Any onboarding flow that introduces the tagging feature

---

## 8. Future Work — Client-Side Tag Evaluation (Long-Term Aspiration)

The residual leakage documented above is a structural consequence of server-side trellis
evaluation. The long-term architectural solution is to move tag criteria evaluation fully
to the client:

- The client would maintain a local index of `(tag_token, upload_id)` pairs in the
  device's secure storage (or in an encrypted local database).
- Trellis routing criteria would be evaluated locally against this index.
- The server would no longer need to compare tag tokens during query evaluation.

This would eliminate token equality, frequency, co-occurrence, and trellis correlation
leakage entirely, reducing the server's knowledge of tag structure to zero.

**Why this is not implemented now:**

- Client-side evaluation requires a reliable local copy of all upload metadata, which
  implies a sync architecture that does not yet exist.
- Offline evaluation conflicts with server-side staging and routing guarantees that are
  part of the current trellis contract.
- The engineering cost is high relative to the severity of the residual leakage (Low,
  per the acceptance above).

This aspiration should be revisited when:

1. A local-first or offline-capable sync architecture is introduced.
2. The trellis routing system is redesigned to support client-initiated routing.
3. The user base or regulatory environment raises the acceptable risk threshold.

**Tracking:** This item should be noted in `docs/ROADMAP.md` under a "Privacy
enhancements" section when the roadmap is next updated.

---

## 9. Relationship to Existing Threat Model

This document extends `docs/security/threat-model.md`. The residual risks documented
here do not modify any existing finding in the threat model. The following entry should
be added to the Accepted Risks table (§8) of the threat model:

| Finding | Rationale |
|---------|-----------|
| SEC-012 — Tag metadata leakage (equality, frequency, co-occurrence) | Structural consequence of deterministic HMAC token scheme; no semantic content exposed; cross-user correlation is cryptographically prevented; accepted per SEC-012 |

---

*This document is owned by the Security Manager. Re-review is required before any
change to the tag token scheme, the trellis evaluation architecture, or Heirlooms'
data handling commitments.*
