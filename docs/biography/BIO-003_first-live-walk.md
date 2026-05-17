# BIO-003 — The First Live Walk

**Prepared by:** Biographer
**Date:** 2026-05-17
**Status:** Internal only — not for publication before patent filing.
**Sources:** PA session log 2026-05-17, task BIO-003, ARCH-016 brief, TOOL-002 queue entry

---

## Scene note: the first live walkthrough of an M11 capsule, 2026-05-17

There is no app for this.

The Android client does not yet have a capsule UI. Neither does the web client. The iOS client is further behind still. The front-end work is waiting for the back-end work to settle, and the back-end work is Milestone 11, and Milestone 11 is not done. What exists, on the evening of 2026-05-17, is the server: a Kotlin/http4k process on port 8080, a PostgreSQL container, a local disk for uploaded files, and a stub tlock provider that generates structurally real keys from an HMAC calculation and a genesis timestamp. The construction is implemented. It runs. It enforces its invariants.

But Bret has not yet walked through it.

That is the thing that is missing, and the evening of 2026-05-17 is when it gets done. Not through a UI, not through an automated test, not by reviewing a diff. By doing it — step by step, in order, in conversation, as if you were a user who had nothing but a terminal and a session token.

---

### The sequence

It starts where every authenticated session starts: `POST /api/auth/challenge`. The server returns a challenge string. `POST /api/auth/login`, challenge included. The server returns a session token. This is M11's two-step auth pattern — not new, not complicated, but it is the door, and the door has to be unlocked before anything else.

Step two: `POST /api/content/upload`. A 41-byte payload. The server writes it to local disk as `15ba48a7-….jpg`. The file is stored. This is the content that will go inside the capsule — a test blob, not a photograph of anything, but in structure it is identical to a photograph of everything.

Between this step and the next, Bret asks a question. It is the kind of question that looks small from the outside and isn't. He asks why the upload goes through the server rather than directly to storage. This is not a naive question — Bret knows why the architecture is shaped this way, but he is asking it aloud because asking it aloud is how you check that the answer still makes sense. The answer is that the server needs to validate the upload, issue a storage reference, and track ownership. But the question opens something: in the hub/underwear world — the local-first architecture currently sitting in the ARCH-016 brief — a `LocalHttpFileStore` could give local storage real signed URLs, making the dev stack shape-identical to production from the upload path's perspective. The insight is written into the brief before the walkthrough continues. This is a characteristic moment: the system, being examined, generates its own improvement.

Step three: `POST /api/capsules`. A capsule is created. ID `858fda4f-9af0-4824-a1ca-4e3ba6d86e5b`. Shape: open. Unlock time: 2027-05-17T22:00:00Z. Message: *"Hello from the future. This capsule was sealed on 2026-05-17."*

One year from tonight.

Step four — the PATCH step, which would attach content to the capsule — is skipped. Bret elects to go straight to the seal. This is a reasonable choice, and also a small act of trust: he is confident enough in the construction that he does not need to run every step to believe the important step will work. He wants to see the seal.

Step five: `POST /api/capsules/858fda4f-.../seal`. The 16-step validation sequence fires. Each step either passes or returns an error that tells you exactly what failed and why. Here, everything passes. The response comes back: `sealed_at: 2026-05-17T22:32:52Z`. Shape: sealed.

The capsule exists. It is locked.

Step six: `GET /api/capsules?state=open,sealed`. Three capsules are listed. Two are stranded open — test artefacts from earlier in the session. One is sealed. The list is a health check: the state machine is working, the query filter is working, the data is real.

Step seven: `GET /api/capsules/858fda4f-...`. Full detail. The response includes `wrapped_capsule_key: ARljYXBz…` — the key material, base64-encoded, structurally valid. The key is random bytes; the real ECDH wrapping is Phase 2 of the CLI toolchain. But the shape is there. The field is there. The envelope is there.

Seven steps. One session. One capsule, sealed, set to open in a year.

---

### What was real, what wasn't

This distinction matters because it is easy, in the first walkthrough of a new system, to let the experience feel more complete than it is. The honest account:

**Real:** The server code. The PostgreSQL connection. The state machine transitions. The 16-step sealing validation. The atomic write. The `sealed_at` timestamp. The capsule ID. The existence of the record in the database.

**Stub:** The key material inside `wrapped_capsule_key` is random bytes, not an ECDH-wrapped DEK. The real client-side key derivation — the splitting of the content key, the blinding, the per-recipient wrapping — is Phase 2 of the CLI. It has not been built yet. What was walked through tonight is the server's half of the construction: the validation, the state machine, the storage, the return format. The client's half — the half that makes the cryptographic promise real — is coming.

**Stub, but wired:** tlock. The stub provider is wired into the sealing path. It does not use BLS12-381 pairing. It does not contact drand. But it is there, in the right place, returning the right shape. The plumbing exists. When the real provider is plugged in, it will go here.

The capsule `858fda4f` will unlock at 2027-05-17T22:00:00Z. Whether it is actually opened by the real tlock network on that date depends on work that has not yet been done. But the record exists. The timestamp is real. The intention is encoded in the database.

---

### The REPL question

At the end of the walkthrough, Bret asks for a REPL.

Not a UI. Not a web form. A prompt — `heirlooms> ` — with tab completion and commands like `seal` and `tlock-key`. A developer's interface, for people who want to interact with the system directly rather than through a polished front-end that abstracts the mechanics away.

This is a request that tells you something about where Bret is in relation to his own product. He has just walked through the capsule lifecycle step by step, narrated in conversation, seven API calls against a running server. He knows what the system does and how it does it. What he wants now is a tool that lets him do it himself, fluently, without scaffolding. Not because he wants to avoid building the UI — the UI is coming — but because the REPL is the right tool for the person who builds the thing. The person who builds the thing does not want to use a polished interface. They want to be close to the wire.

TOOL-002 was queued.

---

### The night's record

A capsule sealed at 22:32 UTC. One year to unlock. A local PostgreSQL container, a stub TimeLock provider, and seven API calls in sequence. No UI. No app. Just the construction, running, in conversation.

The construction was specified in ARCH-010. The sealing endpoint was built in DEV-005, which merged earlier today, passing 717 tests. BIO-005 — written this afternoon, before the walkthrough — describes what it meant for that merge to happen: the difference between a design that exists on paper and the same design that compiles, passes tests, and enforces cryptographic invariants against a real database.

Tonight was the next step: Bret, the person who designed the thing, walking through it himself. Not testing it. Walking through it. Checking that the system he described, and the system that was built from that description, are the same system.

They are.

There is a particular satisfaction in this that has no obvious name. The word "validation" doesn't cover it. Validation is a test result. This is something else: the moment you stop trusting the specification and start trusting the thing. The specification told you it would work. The specification has been right. But the thing is different from the specification, and now you have walked through the thing, and the thing works, and the thing is real.

Capsule `858fda4f`. Sealed 2026-05-17T22:32:52Z. Unlock 2027-05-17T22:00:00Z.

One year from tonight.

---

## Biographer's note

This scene is disclosure-safe: it describes the walkthrough experience and the server-side behaviour, not the novel cryptographic construction. The stub tlock detail is safe for the same reason documented in BIO-005. The wrapped_capsule_key is random bytes at this stage — the DEK blinding scheme is not being exercised here.

The REPL observation (TOOL-002) and the ARCH-016 sidebar (LocalHttpFileStore / signed URLs) are safe to reference because neither discloses the novel combination.

This document may be published, at Bret's discretion, at any time. The Chapter 6 material — the construction itself — remains on hold until after patent filing.

---

*End of scene note.*
