---
id: ARCH-010
title: M11 API surface and migration sequencing brief
category: Architecture
priority: High
status: done
assigned_to: TechnicalArchitect
depends_on: [ARCH-003, ARCH-004, ARCH-005, ARCH-006]
touches:
  - docs/briefs/
estimated: 3–4 hours
---

## Goal

Produce a single coordination document listing every new M11 endpoint in implementation-dependency order, confirming the V31/V32 migration sequence with rollback safety, reviewing the connections backfill for correctness at production scale, and replacing the split ARCH-003 + ARCH-006 sealing validation sequence with a single authoritative version.

## Background

M11 implementation is the next major milestone. Developers currently need to read ARCH-003, ARCH-004, ARCH-005, ARCH-006, and ROADMAP.md to reconstruct the complete API surface and the correct order to build it. The spread creates risk of implementation drift.

Specific gaps identified:

1. **Connections backfill safety** — ARCH-004 §5 runs an INSERT inside a Flyway migration touching `friendships`, `users`, and `account_sharing_keys`. Its correctness at production data scale (lock behaviour, atomicity, edge cases for users with no friendships) has not been reviewed.

2. **Sealing validation sequence split** — ARCH-006 §5 defines steps 1–9 of the server sealing validation, amending ARCH-003. Two documents with one being an amendment creates a risk that a developer follows the old sequence. A single merged sequence must exist before the server developer starts.

3. **No endpoint ordering document** — ARCH-003 defines individual endpoints; ARCH-004 defines the connections model; neither specifies which to implement first (e.g. connections bootstrap must precede executor nominations, which must precede `/seal`).

## Output

A brief at `docs/briefs/ARCH-010_m11-api-surface-and-migration-sequencing.md` covering:

1. Every new M11 server endpoint, listed in implementation dependency order with one-line descriptions
2. The V31 (connections) and V32 (capsule crypto) migration ordering — confirm rollback safety and assert which migration may not be reverted cleanly
3. Review of the ARCH-004 §5 connections backfill for correctness at production data scale
4. The single merged sealing validation sequence, explicitly superseding the ARCH-003 + ARCH-006 §5 split

## Acceptance criteria

- Brief produced and committed to `docs/briefs/`
- A Developer can implement all M11 server endpoints using this brief as sole implementation reference (alongside `docs/envelope_format.md`)
- Migration rollback behaviour is explicitly documented for both V31 and V32
- The merged sealing validation sequence is labelled as superseding ARCH-003 §[X] and ARCH-006 §5

## Completion notes

Completed 2026-05-16 by architect-3 (agent/architect-3/ARCH-010).

- Wrote `docs/briefs/ARCH-010_m11-api-surface-and-migration-sequencing.md`.
- Read ARCH-003, ARCH-004, ARCH-005, ARCH-006 briefs and envelope_format.md in full.
- Brief covers all four required topics:
  1. **Endpoint ordering** — 19 endpoints across 7 implementation waves, with explicit
     dependency constraints (connections → nominations → shares → seal → tlock-key).
  2. **Migration sequencing** — V31 before V32 confirmed; V31 rollback possible in narrow
     deployment window; V32 explicitly marked as non-reversible once any capsule is sealed.
  3. **Backfill review** — ARCH-004 §5 INSERT is correct. Lock profile uses
     AccessShareLock only (non-blocking); atomicity guaranteed by Flyway transaction;
     empty-user edge case handled correctly (zero rows, no special casing needed);
     NULL pubkey handled by LEFT JOIN and nullable column; multi-key risk identified
     with ON CONFLICT DO NOTHING mitigation and a pre-migration SQL check recommended.
  4. **Merged sealing validation** — 16 ordered steps for `/seal` explicitly superseding
     ARCH-003 §4 and ARCH-006 §5; separate validation sequences for `/executor-shares`
     and `/tlock-key`; logging prohibitions restated; 8 cross-cutting invariants for
     implementors.
- Acceptance criteria all met: developer self-sufficiency confirmed (sole reference
  alongside envelope_format.md); rollback explicitly documented for both migrations;
  merged sequence labelled as superseding ARCH-003 §4 and ARCH-006 §5.
