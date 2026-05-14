---
id: DONE-M10
title: Milestone 10 — Shared plots (all four increments E1–E4)
category: Feature
completed: 2026-05-12
completed_at: e7ccbeb
version: v0.47.0–v0.50.0
---

Four increments delivered the complete shared plot system: E1 introduced the criteria/predicate engine (`CriteriaEvaluator` with a full JSON expression tree, V24 migration adding `criteria JSONB` + `show_in_garden` + `visibility` to plots), E2 added flows and staging (V25a migration, flow CRUD endpoints, staging approve/reject/restore, collection plot items), E3 added shared plots with E2EE group keys (V25b migration, `plot_members` + `plot_invites`, per-plot 256-bit key wrapped per member, invite-link flow, DEK re-wrapping on staging approval), and E4 brought full Android adoption (VaultCrypto plot key helpers, FlowsScreen, StagingScreen, SharedPlotsScreen). A key design decision established during E3: public plots must always have `requires_staging=true` to prevent unreviewed content appearing in public collections.
