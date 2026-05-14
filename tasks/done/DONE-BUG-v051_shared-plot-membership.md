---
id: DONE-BUG-v051
title: Post-M10 fixes — shared plot membership overhaul (v0.50.1–v0.51.5)
category: Bug Fix
completed: 2026-05-14
completed_at: 1e5e2df
version: v0.50.1–v0.51.5
---

Two clusters of work shipped after M10 closed. The first (v0.50.1–v0.50.5) addressed immediate post-deploy issues: staging policy enforcement by plot visibility, client-side dedup for encrypted uploads, share-with-friend from Explore, and web image upload failures (Jackson NullNode bug introduced in v0.36.0). The second cluster (v0.51.0–v0.51.5) was a full membership UX overhaul across three increments: E1 reworked the server schema (V28 migration adding `status`, `local_name`, `left_at` to `plot_members`; `plot_status`, `tombstoned_at` to `plots`) and added seven new endpoints (accept, leave, rejoin, restore, transfer, setStatus, shared list); E2 and E3 brought matching web and Android UI (Shared Plots screen with Invitations/Joined/Left/Recently removed sections). v0.51.5 added server-side dedup to prevent the same content appearing twice in a shared plot.
