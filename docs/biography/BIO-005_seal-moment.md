# BIO-005 — The Seal Moment

**Prepared by:** Biographer
**Date:** 2026-05-17
**Status:** Internal only — not for publication. Hold alongside Chapter 6 until after patent filing.
**Classification:** HOLD — contains specific construction detail that is pre-patent disclosure.

---

## Scene note: the moment the construction became real

*This document is the private version of the Chapter 6 opening scene. It was written on 2026-05-17, the day DEV-005 — `PUT /api/capsules/:id/seal` — was implemented, passed 717 tests, and merged into the M11 branch. It cannot be published until after the UK patent application is filed. It should be published immediately thereafter, because that moment is also a marketing event.*

---

There is a particular kind of afternoon that happens in engineering work, and it has no good name. It is the afternoon when something that existed as a diagram, as a brief, as a carefully numbered list of validation steps, starts executing. Not "works in principle." Not "compiles." Executes, against a real database, and comes back correct.

That afternoon arrived on 2026-05-17, some weeks after the design was complete.

The design had been complete for a while. ARCH-003 specified the blinding scheme. ARCH-006 specified the TimeLock provider interface and its stub. ARCH-010 spelled out all sixteen steps of the sealing validation sequence in order, each with its HTTP error body, its logging prohibition, its exact failure condition. The brief was so detailed that a developer who had never discussed the construction with Bret could implement it from the document alone. That level of specification is not bureaucracy. It is the form that care takes when you cannot be in the room.

But a specification is not a thing. A specification is a description of a thing that does not yet exist.

On 2026-05-17, it started to exist.

The endpoint is `PUT /api/capsules/:id/seal`. Its job is to receive a capsule — in its open, unprotected state — and lock it under the construction. Sixteen steps fire in order. The caller's identity is confirmed. The capsule's state machine is checked: it must be open, not already sealed, not delivered. The recipient keys are validated, each envelope format inspected, the blinding masks verified for tlock capsules. The tlock blob is passed to the TimeLockProvider — in M11, a stub, not drand; the real cryptographic network that will eventually gate these capsules does not know this code exists yet — and the provider returns a key that is, in the stub's case, entirely deterministic, but structurally real. The SHA-256 of the DEK blinding half is checked against the digest the client submitted at sealing. If they match, the invariant holds. The capsule's pieces — the wrapped DEK, the blinding mask, the tlock ciphertext, the Shamir configuration — are written atomically, all or nothing. The shape column flips to `sealed`. The response comes back: `200 OK`, capsule shape sealed, sealed_at now.

What this means is: the client split the content key in two halves before it left the device. One half was given to the recipients' public keys, individually. One half was sealed inside the tlock ciphertext — inside a cryptographic round that will not publish until the unlock time passes. The server holds both halves. The server cannot combine them. The server cannot read the capsule even if its database and its bucket are both fully compromised, because the halves mean nothing alone. One half is locked inside a future moment. The other half is wrapped to keys the server has never seen. The invariant `SHA-256(DEK_tlock) == tlock_key_digest` is a tamper-evident seal on the whole arrangement: if anyone meddles with the tlock half between sealing and delivery, the delivery endpoint will detect it and return HTTP 500. There is no soft failure for that. It is an error, not a warning.

Shamir adds the third path. An executor, holding their wrapped share, can recover the DEK without the tlock network, without waiting for the round to publish. The executor path is over the full DEK, not the blinding half. No call to `/tlock-key` required. The Shamir recovery does not touch the time-lock mechanism at all. It is there for the case where the cryptographic network is unavailable, or where the capsule needs to be delivered in a different way than the time mechanism intended. Two independent unlock paths. Three if you count the direct ECDH path for bound recipients.

All of this was specified. All of it was designed. None of it was real until the integration test ran against a real PostgreSQL instance and came back green.

This is the gap worth naming: the StubTimeLockProvider stands in for drand. It is HMAC-based. It has a genesis timestamp and a period and it generates fake-but-structurally-valid keys on request. The real network — the global decentralised randomness beacon that will eventually publish the keys that open these capsules on their scheduled dates — does not know this code exists. It is doing its work on the other side of a wall. The stub keeps the shape of the real thing while the real thing is not yet wired up. But the shape is real. The validation sequence is real. The invariants are enforced. When M12 replaces the stub with the drand sidecar, the construction does not change. Only the key source changes.

Before DEV-005 merged, the novel cryptographic construction at the heart of Heirlooms was: a research brief, an architecture spec, a set of numbered validation steps in a document. 

After DEV-005 merged: it runs. It enforces its own invariants against a real database. It rejects bad envelopes. It rejects tlock blobs with bad digest matches. It enforces the multi-path fallback rule. It writes atomically. It advances the state machine. 717 tests pass.

There is a sentence in the Architecture brief — ARCH-010, §6 — that lists the invariants every M11 implementor must preserve. Invariant I-5: `tlock_key_digest = SHA-256(DEK_tlock)` is stored at sealing time and MUST be verified at delivery time. A mismatch is HTTP 500, not a soft error. Not a warning. Not a retry. An error.

That invariant is now verified by code. It was previously verified by a document.

The difference between a design that exists on paper and the same design that compiles, passes tests, and enforces cryptographic invariants against a real database is the difference between a promise and a proof. Before today, Heirlooms made a promise: we cannot read your data. After today, that promise has a working implementation. The proof is partial — M12 will wire in the real tlock network, and there is still much to build. But the shape is correct. The logic is enforced. The state machine advances.

This is the moment Bret was pointing at when he said: *it basically defines the moment this work becomes 'real.'*

He was right. This is that moment.

---

## Biographer's note

This scene cannot be published before the patent is filed. The XOR DEK blinding scheme, the tlock IBE lower-bound, and the Shamir threshold deletion upper-bound in combination — this is the novel construction, and it is described here directly enough that publication would constitute disclosure. Hold this document alongside the Chapter 6 draft.

When the UK patent application is filed, this document — or a version of it lightly revised for public register — becomes the first post in the Heirlooms blog. It is the chapter that was kept private for the duration of the patent gap. Its publication is the announcement that the gap has closed.

The StubTimeLockProvider detail is disclosure-safe: it describes the M11 implementation, not the construction's novelty. The construction's novelty is in the combination, not the stub. The stub is just scaffolding.

Everything else in this document should be reviewed against §7 of BIO-002 before any public use.

---

*End of scene note.*
