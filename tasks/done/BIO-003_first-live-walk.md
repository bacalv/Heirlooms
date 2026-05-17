---
id: BIO-003
title: The First Live Walk — M11 capsule walkthrough, 2026-05-17
category: Biography
priority: Medium
status: in-progress
assigned_to: Biographer
depends_on: [BIO-002]
touches:
  - docs/biography/
estimated: 1 session
---

## Brief

Write a biographical scene note documenting the first live, interactive walkthrough of
the M11 capsule lifecycle — the evening of 2026-05-17. This was the first time a capsule
was sealed against the real M11 server code, step by step, in conversation.

## The scene (PA summary — use as source material, not as the final text)

Bret asked to see the M11 capsule system run. There was no app — the Android and web
clients don't have capsule UIs yet. The walkthrough was done entirely through API calls
in conversation: a local PostgreSQL container, the M11 server on port 8080 with stub
tlock, and seven steps narrated one at a time.

**The sequence:**
1. `POST /api/auth/challenge` + `POST /api/auth/login` — two-step auth, session token returned
2. `POST /api/content/upload` — 41-byte test payload, stored to local disk as `15ba48a7-….jpg`
3. `POST /api/capsules` — capsule `858fda4f-9af0-4824-a1ca-4e3ba6d86e5b` created, shape=open,
   unlock_at=2027-05-17, message: *"Hello from the future. This capsule was sealed on 2026-05-17."*
4. Step 4 (PATCH) skipped — Bret elected to go straight to the seal
5. `POST /api/capsules/858fda4f-.../seal` — 16-step M11 validation, `sealed_at: 2026-05-17T22:32:52Z`
6. `GET /api/capsules?state=open,sealed` — three capsules listed, one sealed, two stranded open
7. `GET /api/capsules/858fda4f-...` — full detail retrieved, `wrapped_capsule_key: ARljYXBz…`

**Between steps, architecture happened.** Bret asked why the upload went through the
server rather than direct to storage. The answer turned into an architectural note on
ARCH-016: in the hub/underwear world, a `LocalHttpFileStore` would give local storage
real signed URLs, making the dev stack shape-identical to production. The note was
written into the brief before the walkthrough continued.

**At the end, Bret asked for a REPL.** A `heirlooms> ` prompt, tab completion, commands
like `seal` and `tlock-key`. TOOL-002 was queued.

**What was real, what was stub:**
- Server code, DB, connections, seal validation, atomic write — all real M11 code
- The `wrapped_capsule_key` envelope — structurally valid, key material is random bytes
  (real ECDH key wrapping is Phase 2 of the CLI)
- tlock — stub (HMAC, not BLS12-381 pairing). Wired in, not exercised on this capsule.

**The timestamp that matters:** capsule `858fda4f` unlocks 2027-05-17T22:00:00Z.
One year from the night it was sealed.

## Output

Produce `docs/biography/BIO-003_first-live-walk.md` — a scene note in the style of
BIO-001 and BIO-002. Warm, honest, technically grounded. Not over-dramatised.
The human thread: what it feels like to walk through your own system for the first time,
step by step, in the dark, with only a conversation for company.

Read `personalities/Bret.md` and `docs/biography/` for tone and context before writing.

## Completion notes

Completed 2026-05-17. Scene note written to `docs/biography/BIO-003_first-live-walk.md`.

The note covers the full seven-step walkthrough, the ARCH-016 sidebar (LocalHttpFileStore / signed URLs), the TOOL-002 REPL request, the real-vs-stub distinction, and the human thread the brief asked for — what it means to stop trusting a specification and start trusting the thing. The scene is disclosure-safe: the real/stub section is honest about the key material being random bytes at this stage, and the construction's novelty is not described.

BIO-005 (the seal moment, written this afternoon) forms a companion piece. BIO-003 is the walkthrough; BIO-005 is the merge. Together they bracket the day.

Committed to branch `agent/biographer-3/BIO-003`.
