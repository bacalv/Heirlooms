---
id: ARCH-007
title: E2EE tag scheme — HMAC token identifiers + encrypted display names
category: Architecture
priority: High
status: queued
depends_on: []
touches:
  - HeirloomsServer/src/main/resources/db/migration/
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/repository/
  - HeirloomsApp/app/src/main/kotlin/digital/heirlooms/crypto/
  - HeirloomsWeb/src/crypto/
assigned_to: TechnicalArchitect
estimated: 1 day (design only)
---

## Background

Tags are currently stored in plaintext on the server. This violates Heirlooms'
founding privacy principle: staff should never be able to read user data. A tag like
"grandmother", "diagnosis", or "affair" is personal data.

Decision made 2026-05-16: adopt an HMAC token scheme as the practical balance between
privacy and server-side criteria evaluation. See SEC-012 for the accepted residual risk.

## Design goals

1. **Staff cannot read tag values** — the semantic meaning of a tag is never stored in
   plaintext on the server or in transit
2. **Per-user tag isolation** — two users with the same tag value produce different
   server-side tokens (tokens are keyed to the individual user's master key)
3. **Server-side trellis evaluation remains possible** — the server can check whether
   an item has a given tag by comparing tokens, enabling server-side routing
4. **Per-member tags on shared plot items** — User A can tag a shared-plot item that
   User B uploaded; those tags are invisible to User B and to Heirlooms staff

## Proposed scheme

### Tag token
```
tag_token = HMAC-SHA256(key=HKDF(master_key, "tag-token-v1"), data=tag_value_utf8)
```
Stored on the server as the identifier for a tag. The server only ever sees tokens.

### Tag display name
```
tag_display_ciphertext = AES-256-GCM(key=HKDF(master_key, "tag-display-v1"), plaintext=tag_value_utf8)
```
Stored alongside the token. Decrypted client-side for display. The server never decrypts this.

### Trellis criteria
Trellis criteria that reference tags store the token, not the value:
```json
{ "type": "tag", "token": "abc123..." }
```
The client constructs the token before sending; the server evaluates by token comparison.

### Per-member tags on shared plot items
A separate `member_tags` table (or similar) tracks:
```
(user_id, upload_id, tag_token, tag_display_ciphertext)
```
This allows User A to tag User B's shared-plot item without User B seeing the tag.
The upload's own tag list (owned by the uploader) is unchanged.

### Auto-tagging loop prevention
Auto-applied tags must use a separate HKDF context:
```
auto_tag_token = HMAC-SHA256(key=HKDF(master_key, "auto-tag-token-v1"), data=tag_value_utf8)
```
Trellis criteria only match `tag-token-v1` tokens, never `auto-tag-token-v1` tokens.
This prevents auto-tags from re-triggering trellises and causing infinite loops.

## Deliverables

1. Updated schema design covering `uploads` tag storage, `member_tags` table, and
   trellis criteria format
2. Key derivation spec for all tag-related contexts
3. Migration strategy for existing plaintext tags (re-encrypt at next client login)
4. Updated `docs/envelope_format.md` or a new `docs/tag_scheme.md` brief
5. Identify which client platforms (Android, web, iOS) need changes and in what order

## Open questions

- Should the display name ciphertext be stored per-tag globally (deduped) or per
  (user, upload) tuple?
- What is the migration UX — do users see a "re-encrypting your tags" progress step
  on next login?
- Should the tag token scheme version be stored alongside the token to allow future
  rotation?

## Completion notes

<!-- Architect appends here and moves file to tasks/done/ -->
