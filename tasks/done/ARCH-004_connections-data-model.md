---
id: ARCH-004
title: Connections data model — identity layer for capsule recipients, executor nominees, and Shamir shareholders
category: Architecture
priority: High
status: done
assigned_to: TechnicalArchitect
---

# ARCH-004 — Connections Data Model Brief

Design the identity layer for capsule recipients, executor nominees, and Shamir
shareholders ahead of M11 development.

## Completion notes

Completed 2026-05-15.

- Read existing schema: `friendships`, `capsule_recipients`, `users`, `account_sharing_keys`, `plot_members`, `plot_invites`.
- Wrote `docs/briefs/ARCH-004_connections-data-model.md`.
- Answers: deferred-pubkey email placeholders, new `connections` table (not extending friendships), migration path for free-text rows, executor nomination lifecycle, full proposed schema, M11 vs M12 scope boundary.
