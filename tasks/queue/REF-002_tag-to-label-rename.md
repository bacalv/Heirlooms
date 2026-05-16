---
id: REF-002
title: Tag → Label rename across all platforms and documentation
category: Refactoring
priority: Medium
status: queued
depends_on: []
touches:
  - HeirloomsServer/
  - HeirloomsApp/
  - HeirloomsWeb/
  - HeirloomsiOS/
  - docs/
assigned_to: Developer
estimated: half day
---

## Background

Decision made 2026-05-16 by CTO: rename "tag" to "label" across the product.

**Rationale:** In the Heirlooms garden metaphor, plants have labels — a label on a
pot saying "runner bean" — not tags. "Tag" also carries social media baggage that
works against Heirlooms' archival, personal tone. "Label" is more natural in the
context of physical archive organisation.

This is a full rename (UI, API, code, and documentation) modelled on REF-001
(Flow → Trellis).

## Scope

### User-facing strings
- All "tag" / "tags" / "tagged" occurrences in Android, web, and iOS UI → "label" / "labels" / "labelled"
- "Tag name" → "Label name", "Add tag" → "Add label", etc.

### API
- Any API endpoints or request/response fields using "tag" → "label"
  (check: item tag fields, tag search endpoints, trellis criteria referencing tags)

### Server code
- Classes, methods, and variables named `*Tag*` / `*tag*` → `*Label*` / `*label*`
- Database column names: check if any `tag_*` columns exist; if so, add a Flyway
  migration to rename them

### Android code
- All `Tag`-named composables, ViewModels, data classes, repository methods → `Label`

### Web code
- All `tag`-named components, state variables, API call references → `label`

### iOS code
- All `Tag`-named structs, views, and functions → `Label`

### Documentation
- `docs/briefs/ARCH-007_e2ee-tag-scheme.md` — the internal technical scheme can keep
  "tag token" as an implementation detail (HMAC token), but user-facing language should
  use "label". Update any user-facing phrasing.
- `docs/notation/NOT-001_capsule-construction.md` — update notation to use `K_label`
  and `label_token` for user-facing references; keep internal `tag_token_key` name with
  a note that "label" is the user-facing term.
- `docs/security/SEC-012_tag-metadata-leakage.md` — update title and references to
  "label metadata leakage"
- `tasks/progress.md` and any task files referencing "tag scheme" → update to "label scheme"

## Notes

- The HMAC token internals (ARCH-007) use "tag token key" / "tag_token" as an
  implementation concept. The rename is **user-facing first**: what the user sees and
  what the API exposes. Internal crypto variable names may keep "tag" if the TA
  confirms that is cleaner.
- Coordinate with TechnicalAuthor to update TAU-001 outputs after this task completes.
- Check `docs/research/GLOSSARY.md` — add "label" entry, update or deprecate "tag" entry.

## Acceptance criteria

- No user-facing "tag" string remains in any platform UI
- API fields and endpoints updated
- All tests pass after rename
- Documentation updated

## Completion notes

<!-- Agent appends here and moves file to tasks/done/ -->
