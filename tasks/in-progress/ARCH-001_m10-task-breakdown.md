---
id: ARCH-001
title: M10 — produce queue tasks for all four increments
category: Planning
priority: High
status: queued
depends_on: []
touches: [tasks/queue/]
assigned_to: TechnicalArchitect
estimated: 1-2 hours (agent, research + writing)
---

## Goal

M10 (Shared Plots) is the current milestone and has no queue tasks yet — only briefs.
Read all four increment briefs and produce well-formed task files in `tasks/queue/` so
the Dev Manager can schedule implementation work.

## Source documents

- `docs/briefs/M10_brief.md` — overview, design decisions, out-of-scope list
- `docs/briefs/M10_E1_brief.md` — Increment 1
- `docs/briefs/M10_E2_brief.md` — Increment 2
- `docs/briefs/M10_E3_brief.md` — Increment 3
- `docs/briefs/M10_E4_brief.md` — Increment 4
- `docs/ROADMAP.md` — milestone context
- `tasks/progress.md` — existing queue, naming conventions

Also read the current codebase structure in the areas M10 will touch:
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/domain/plot/`
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/repository/plot/`
- `HeirloomsServer/src/main/resources/db/migration/` (latest migration number)
- `HeirloomsApp/app/src/main/kotlin/digital/heirlooms/` (plot-related screens)
- `HeirloomsWeb/src/` (plot-related components)

## Deliverables

One task file per increment in `tasks/queue/`, named `M10-E1_<slug>.md` through `M10-E4_<slug>.md`.

Each file must include:
- Standard front-matter: `id`, `title`, `category`, `priority`, `status: queued`, `depends_on`, `touches`, `assigned_to`, `estimated`
- `## Goal` — one paragraph
- `## Acceptance criteria` — bullet list, testable
- `## Notes` — anything an implementer needs to know (migration numbers, API paths, E2EE implications)
- `## Spawned tasks` — left blank for the implementer

Priority guidance:
- E1 is High (server foundation — everything else depends on it)
- E2–E3 are Medium (web surfaces)
- E4 is Medium (Android)

Dependency chain: E2 depends on E1, E3 depends on E2, E4 depends on E3.

## After creating the task files

Update `tasks/progress.md` to include all four new tasks in the Queue table.
Append `## Completion notes` to this file and move it to `tasks/done/`.
