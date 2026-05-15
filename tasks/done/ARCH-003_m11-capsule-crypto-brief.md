---
id: ARCH-003
title: M11 capsule cryptography brief — agreed spec before any M11 developer touches crypto code
category: Architecture
priority: High
status: done
assigned_to: TechnicalArchitect
---

# ARCH-003 — M11 Capsule Cryptography Brief

Write the single agreed spec before any M11 developer touches crypto code. Cover:
new envelope algorithm IDs, new schema columns, connections model for recipients,
server validation semantics, tlock/BLS12-381 Android/web-only constraint, Shamir
share encoding and distribution API, cross-platform API contract for the sealing
request body.

## Completion notes

Completed 2026-05-15.

- Wrote `docs/briefs/ARCH-003_m11-capsule-crypto-brief.md`.
- References ARCH-005 for algorithm IDs and ARCH-004 for connections model.
- Covers all eight required topics.
- This brief unlocks all M11 developer tasks.
