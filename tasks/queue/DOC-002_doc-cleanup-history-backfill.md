---
id: DOC-002
title: Doc cleanup + history backfill from git
category: Docs
priority: Medium
status: queued
depends_on: []
touches:
  - tasks/done/
  - tasks/progress.md
  - docs/PROMPT_LOG.md
  - docs/VERSIONS.md
  - docs/sessions/
assigned_to: TechnicalArchitect
estimated: 2–3 hours (agent, research-heavy)
---

## Goal

The project has migrated from a flat doc structure (PROMPT_LOG, VERSIONS.md, session
notes) to a structured task system (tasks/queue/, tasks/in-progress/, tasks/done/).
The done/ folder currently reflects only recent work. This task backfills the task
history from the git log and old docs, and archives docs that are now superseded.

## Source documents

- `git log --oneline` — authoritative record of what shipped and when
- `docs/VERSIONS.md` — version-by-version changelog
- `docs/PROMPT_LOG.md` (if present) and `docs/sessions/` — old PA session notes
- `docs/briefs/` — milestone briefs (read-only reference; do not modify)
- `docs/ROADMAP.md` — milestone definitions and sequencing

## Deliverables

### 1. Backfill done/ task files

Create one `tasks/done/` file per completed milestone, plus individual files for
notable bugfix batches. Use this naming convention:

- Milestones: `DONE-M5_capsules.md`, `DONE-M6_garden-explore.md`, `DONE-M7_vault-e2ee.md`, etc.
- Bugfix batches: `DONE-BUG-v0503_post-m10-fixes.md` (group by version range if many)

Each file must use this frontmatter:

```yaml
---
id: DONE-M7
title: Milestone 7 — Vault E2EE
category: Feature
completed: 2026-05-09
completed_at: <short git hash of version bump or final feature commit>
version: v0.30.0
---
```

Body: two or three sentences on what shipped. Pull from VERSIONS.md and git log.
Do not reproduce full changelogs — just the headline and any non-obvious decisions.

### 2. Update tasks/progress.md

Add all new done/ files to the Done table with their completion dates.

### 3. Archive superseded docs

- `docs/PROMPT_LOG.md` — if it still exists at root level and has not been archived,
  move it to `docs/sessions/PROMPT_LOG_archive.md`. Do not delete it.
- `docs/VERSIONS.md` — leave in place (still useful as a detailed changelog reference).
  Add a one-line note at the top: "Superseded for task tracking by tasks/done/ — kept
  as a detailed changelog reference."
- Session files in `docs/sessions/` — leave as-is; they are already archived.

### 4. Do NOT modify

- `docs/briefs/` — design specs, not session logs
- `docs/PA_NOTES.md` — active working memory
- `docs/ROADMAP.md` — active product doc
- Any code files

## Frontmatter for each done file

Milestones to cover (derive git hashes from `git log`):

| ID | Milestone | Approx version |
|----|-----------|----------------|
| DONE-M5 | Capsules (web + backend; Android increment deferred) | v0.18.0–v0.19.6 |
| DONE-M6 | Garden / Explore restructure | v0.22.0–v0.25.1 |
| DONE-M7 | Vault E2EE | v0.30.0 |
| DONE-M8 | Multi-user access | v0.38.0–v0.41.0 |
| DONE-M9 | Friends + item sharing + Android plot management | v0.45.0–v0.46.2 |
| DONE-M10 | Shared plots (all four increments E1–E4) | v0.47.0–v0.50.0 |
| DONE-iOS | iOS app implementation | see commit 39a3a3a |

For bugfixes: group post-M10 fixes (v0.50.1–v0.53.1) into one or two files by theme.

## Acceptance criteria

- Every milestone from M5 onward has a done/ file with a valid `completed_at` git hash.
- `tasks/progress.md` Done table includes all new entries.
- `docs/PROMPT_LOG.md` archived if present at root level.
- No briefs, code, or active docs modified.
- `git log --oneline` confirms the hashes used in frontmatter actually exist.

## Completion notes

<!-- Agent appends here and moves file to tasks/done/ -->
