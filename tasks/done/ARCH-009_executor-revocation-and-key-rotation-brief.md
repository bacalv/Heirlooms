---
id: ARCH-009
title: Executor nomination — revocation and key rotation architecture brief
category: Architecture
priority: High
status: done
assigned_to: TechnicalArchitect
depends_on: [ARCH-004]
touches:
  - docs/briefs/
estimated: 2–3 hours
---

## Goal

Produce a design brief covering the full executor nomination revocation lifecycle: the re-split flow, server role, atomicity and failure recovery, and the interaction with SEC-011 device revocation — so these two revocation paths do not establish conflicting patterns.

## Background

ARCH-004 §4 specifies the executor nomination lifecycle (pending → accepted → revoked) and explicitly flags: "key rotation on revocation is non-trivial and is tracked as a separate M11 task." No brief exists yet.

A Developer implementing `POST /api/executor-nominations/:id/revoke` has no authoritative spec on:
- Whether re-splitting is done client-side on the nominator's device
- Whether the server orchestrates a multi-party rotation
- What happens if the nominator is offline when a revocation fires

SEC-011 (device revocation) is being scheduled for the same iteration. It deletes a `wrapped_keys` row — a simpler operation — but if it establishes a "revocation = delete row, no rotation" pattern without checking whether that conflicts with executor re-split, the executor revocation path will need a breaking change later.

This brief is a prerequisite for any developer starting on executor revocation, and should be reviewed by the SecurityManager before SEC-011 ships to confirm the two patterns are compatible.

## Output

A brief at `docs/briefs/ARCH-009_executor-revocation-and-key-rotation.md` covering:

1. The exact re-split and re-distribution flow when an executor nomination is revoked
2. The server's role during rotation (relay vs. orchestrator — same question as M7)
3. Atomicity and failure recovery (what if the nominator goes offline mid-rotation)
4. Interaction with SEC-011 device revocation — confirm both paths are compatible or prescribe a unified model

## Acceptance criteria

- Brief produced and committed to `docs/briefs/`
- SEC-011 scope confirmed compatible: SEC-011 may delete `wrapped_keys` rows without conflicting with the executor re-split model
- Brief is self-consistent with ARCH-004 §4 and ARCH-006 blinding scheme

## Completion notes

Completed 2026-05-16.

- Read ARCH-003, ARCH-004, ARCH-006 briefs and SEC-011 task file in full.
- Wrote `docs/briefs/ARCH-009_executor-revocation-and-key-rotation.md`.
- Key decisions:
  - **Re-split is mandatory** on revocation of an accepted executor. Deleting the
    `executor_shares` row alone is insufficient — a revoked executor's local copy of
    their share remains valid on the old polynomial. Only a fresh polynomial (over the
    same `DEK`) makes the old share cryptographically irrelevant.
  - **Server is a relay, not an orchestrator.** All Shamir computation runs on the
    nominator's device. The server provides a single atomic `POST /rotate` endpoint
    that revokes the nomination, deletes old shares, inserts new shares, and updates
    capsule Shamir parameters in one transaction.
  - **Atomicity via single DB transaction.** The four-step update (revoke nomination +
    delete shares + insert new shares + update capsule parameters) runs in one PostgreSQL
    transaction. Rollback on failure preserves old shares.
  - **Offline recovery** via idempotent pre-flight check: nominator calls
    `GET /api/capsules/:id/executor-state` after reconnecting to determine whether the
    rotation already committed before retrying.
  - **Optimistic lock** (`capsules.shamir_version INTEGER`) prevents concurrent rotations
    from two of the nominator's devices from producing inconsistent state.
  - **SEC-011 compatibility confirmed.** SEC-011 may delete `wrapped_keys` rows without
    conflicting with the executor re-split model. The two operations touch disjoint
    tables (`wrapped_keys` vs. `executor_shares`/`executor_nominations`/`capsules`) and
    serve different threat models. Adopted unified revocation vocabulary to prevent
    future confusion: "device revocation" (delete row, no rotation), "executor revocation"
    (re-split required), and "key rotation" (new secret, not required by either).
  - **Master-key Shamir re-split deferred** to a future brief. The `/rotate` endpoint
    rejects master-key share rotation with HTTP 422 until that spec is written.
- Schema addition: `capsules.shamir_version INTEGER NOT NULL DEFAULT 0` (V33 migration)
  for optimistic locking.
- New API surface: `GET /api/capsules/:id/executor-state` and
  `POST /api/capsules/:id/executor-shares/rotate`.
