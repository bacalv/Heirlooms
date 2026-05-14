---
id: DONE-M8
title: Milestone 8 — Multi-user access
category: Feature
completed: 2026-05-11
completed_at: 3e8c6f7
version: v0.38.0–v0.42.0
---

Five increments replaced the single shared API key with a full per-user authentication system: E1 added the `users`/`user_sessions`/`invites` schema and all auth API endpoints, E2 enforced per-user scoping across all DB operations and added a 23-test isolation suite (Alice vs Bob), E3 delivered the web login/register/pairing flow (Argon2id KDF, P-256 device keypair, IndexedDB persistence), E4 added the Android auth migration + Devices & Access screen + PairingScreen, and E5 was a fixup pass (GET /auth/me endpoint, IDB auto-unlock, V22 diagnostic_events user scoping, 8 isolation tests). Post-deploy Android bugfixes (v0.43.0–v0.44.0) fixed QR scan on Fire OS, upload OOM errors, and several garden navigation regressions.
