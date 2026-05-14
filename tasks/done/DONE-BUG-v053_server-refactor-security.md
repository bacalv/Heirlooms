---
id: DONE-BUG-v053
title: Post-M10 fixes — server refactor + security hardening (v0.52.0–v0.53.1)
category: Bug Fix
completed: 2026-05-14
completed_at: af2e856
version: v0.52.0–v0.53.1
---

v0.52.0 was a multi-platform feature drop: drag+drop and paste-to-upload on web, shared plot shortcuts and bulk staging on Android, the iOS Swift Package scaffold, and a server refactor design proposal document. The server refactor itself (DONE-001) then landed in v0.53.0–v0.53.2 across eight phases, moving HeirloomsServer from a flat package to `config/ crypto/ domain/ filters/ repository/ routes/ service/ representation/ storage/` sub-packages. In parallel, SEC-001 (commit `af2e856`) delivered security hardening: CORS lockdown (origin allowlist replacing wildcard), structured auth logging, and server-side error redaction to prevent leaking stack traces to clients.
