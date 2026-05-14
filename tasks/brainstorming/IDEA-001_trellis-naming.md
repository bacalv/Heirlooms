---
id: IDEA-001
title: Flow → Trellis naming research
category: Brainstorming
status: research-complete
---

## Recommendation: Trellis

A trellis is a physical structure you set up once that guides every new growth automatically toward a destination — exactly what a "flow" does. It's genuinely botanical, free of SDK name collisions, and metaphorically precise.

See REF-001 for the implementation task.

## Why not Harvest

A harvest is a manual, one-time event (you pick what's already grown). A flow is a persistent, automated conduit. The metaphor is backwards.

## Why not Channel

"Channel" is intuitive but generic — doesn't advance the garden brand. Also clashes with `kotlinx.coroutines.channels.Channel` in Android.

## Why not Runner

Genuinely botanical (a stem that spreads horizontally and roots at a new location) but requires explanation. "Runner" in tech usually means a CI/CD runner.

## Rename scope (estimated)

- ~55–60 files across server + Android
- 1 DB migration (rename `flows` table, `source_flow_id` columns)
- API breaking change (`/api/flows` → `/api/trellises`) — needs coordinated release
- Web: minimal (no user-visible flow strings currently)
