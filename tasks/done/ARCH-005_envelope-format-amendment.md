---
id: ARCH-005
title: Envelope Format v1 Amendment — add plot key algo, reserve M11 IDs
category: Architecture
priority: High
status: in-progress
assigned_to: TechnicalArchitect
---

# ARCH-005 — Envelope Format v1 Amendment

Amend `docs/envelope_format.md` to:

1. Add `plot-aes256gcm-v1` with its M10 semantics (DEK wrapped under the shared plot group key) — already in production in `EnvelopeFormat.kt` and the web/iOS clients, but missing from the spec doc.
2. Reserve M11 algorithm ID stubs: `capsule-ecdh-aes256gcm-v1`, `shamir-share-v1`, `tlock-bls12381-v1` — mark as "TBD by ARCH-003" so no developer independently invents conflicting names.
3. Clarify the versioning policy: adding a new algorithm ID does not require a version bump of the envelope format itself.

## Completion notes

Completed 2026-05-15.

- Added `plot-aes256gcm-v1` to the algorithm identifiers table with full M10 semantics.
- Reserved three M11 algorithm IDs as TBD stubs in a new "Reserved (M11)" table section.
- Added an explicit "Versioning policy" section making clear that new algorithm IDs do not require an envelope version bump.
- Moved task to done/.
