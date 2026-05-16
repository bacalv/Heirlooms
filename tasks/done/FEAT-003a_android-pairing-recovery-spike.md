---
id: FEAT-003a
title: Android account pairing/recovery — design spike
category: Feature
priority: Medium
status: done
assigned_to: Developer
depends_on: []
touches:
  - No production code — investigation only
estimated: 2–3 hours
---

## Goal

Answer the four open design questions in FEAT-003 before committing to an implementation approach. Produce a decision document that unblocks FEAT-003b.

## Background

FEAT-003 (Android account pairing/recovery) was flagged as too large and under-specified for direct implementation. Four design questions must be resolved first.

## Questions to answer

**Q1: Which recovery flow?**
- Option A: Passphrase-based — user enters their Heirlooms passphrase on the new device; new device derives the master key from passphrase (same Argon2id path as web). Question: what Argon2id parameters does Android use vs. web? They must match or re-wrapping is not possible without a server round-trip.
- Option B: QR-code device-pairing — existing device generates a QR code containing the master key (or a wrapped copy), new device scans it. Requires both devices to be simultaneously available.
- Option C: Server-assisted re-wrap — user authenticates on new device, server returns the passphrase-wrapped blob, new device derives key from passphrase. Essentially the same as the web login flow.

Which option is consistent with the existing pairing mechanism and the M11 social recovery design?

**Q2: Argon2id parameter parity**
What are the current Argon2id parameters in the Android codebase? What are the web parameters? Are they identical? If not, can the new device derive the same key from the same passphrase?

**Q3: Server impact**
Does any recovery option require new server endpoints, or can all of them work with existing `/api/auth/wrapped-keys` and `/api/auth/wrapped-keys/passphrase` endpoints?

**Q4: M11 compatibility**
ARCH-010 is specifying the M11 API surface. Does FEAT-003b need to wait for ARCH-010, or can it proceed independently (i.e. the recovery flow is orthogonal to the M11 changes)?

## Output

A decision document at `docs/briefs/FEAT-003a_pairing-recovery-decision.md` answering all four questions with concrete code references (file:line where relevant). Include a recommendation for which option (A/B/C) to implement in FEAT-003b.

## Acceptance criteria

- All four questions answered with code references
- Argon2id parameters confirmed (match or mismatch — either is an acceptable answer)
- Clear recommendation for FEAT-003b implementation approach
- Brief note on whether FEAT-003b should wait for ARCH-010

## Completion notes

Completed 2026-05-16 by developer-4.

All four questions answered. Decision document produced at
`docs/briefs/FEAT-003a_pairing-recovery-decision.md`.

**Q1 answer:** Option C (server-assisted passphrase recovery) is the recommended
primary path. It is the same flow as the web login — the user authenticates with
their passphrase, fetches the server-held `argon2id-aes256gcm-v1` blob via
`GET /api/keys/passphrase`, and unwraps it locally. The existing device link flow
(`/api/keys/link/*`) provides a secondary path (device-to-device) that requires no
new server code.

**Q2 answer:** Parameters match exactly on both platforms — **m=65536, t=3, p=1**.
Android: `VaultCrypto.kt:131`. Web: `VaultCrypto.js:132` and `LoginPage.jsx:10`.
A fresh Android install can unwrap the passphrase blob stored by web (or by Android
itself) without any parameter mismatch.

**Q3 answer:** No new server endpoints are required for either recovery option. The
existing `GET /api/keys/passphrase` endpoint (KeysRoutes.kt) and the existing device
link flow already cover both paths. FEAT-003b is purely a client-side Android task.

**Q4 answer:** FEAT-003b does not need to wait for ARCH-010. The recovery flow uses
only pre-existing auth and key management endpoints (M8-era). ARCH-010 covers M11
capsule crypto endpoints and the connections model — zero overlap.
