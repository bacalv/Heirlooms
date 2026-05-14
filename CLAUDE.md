# Heirlooms — Claude Code Instructions

## Who is running this session?

Read `personalities/PERSONAS.md` to understand the team structure.

- **CTO / PA sessions**: started with `@personalities/PA.md`. CWD is `~/IdeaProjects/Heirlooms` (the main worktree). The PA may commit task files and docs directly here.
- **Agent sessions**: dispatched by the PA with a persona file and a task file. **Your workspace is NOT `~/IdeaProjects/Heirlooms`.** See below.

---

## Agent workspace rule (CRITICAL)

If you are a Developer, SecurityManager, TestManager, OpsManager, or TechnicalArchitect dispatched on a specific task, your workspace is a **separate git worktree**:

```
~/IdeaProjects/agent-workspaces/Heirlooms/<agent-name>/
```

The PA will tell you your exact workspace path and branch name at the start of your task.

**Before doing anything else, verify your working directory:**
```bash
pwd
```

If `pwd` does not return your assigned workspace path, `cd` there immediately:
```bash
cd ~/IdeaProjects/agent-workspaces/Heirlooms/<agent-name>
```

**All file reads and writes must use your workspace path, not `~/IdeaProjects/Heirlooms/`.**  
Writing to the main worktree from an agent branch will cause merge conflicts and corrupt the main workspace.

Commit only to your assigned branch (e.g. `agent/developer-1/IOS-001`). Never push.

---

## Repo layout

```
HeirloomsServer/     Kotlin/http4k backend
HeirloomsApp/        Android (Jetpack Compose)
HeirloomsWeb/        React/Vite web app
HeirloomsiOS/        Swift/SwiftUI iOS app
docs/                Architecture docs, briefs, security
tasks/               queue/ → in-progress/ → done/
personalities/       Persona files for each agent role
scripts/             create-agent-workspace.sh and helpers
```

## Task system

Tasks live in `tasks/`. Agents claim a task by reading it, doing the work, appending `## Completion notes`, and moving the file from `in-progress/` to `done/`. Update `tasks/progress.md` when done.

## Key docs

- `docs/PA_NOTES.md` — live environment URLs, architecture summary, working protocols
- `docs/envelope_format.md` — E2EE envelope spec (read before touching any crypto code)
- `docs/ROADMAP.md` — product direction and milestone history
