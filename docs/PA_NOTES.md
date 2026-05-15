# PA Notes — Working Memory

Maintained by the PA across sessions. For task state, see `tasks/progress.md`.
Previous notes archived in `docs/sessions/2026-05-14_PA_NOTES_archive.md`.

---

## Bret's preferences

- Uses IntelliJ IDEA (not Android Studio) for this project
- All git pushes are done manually by Bret — never push without being asked
- Prefers multiple-choice questions over open-ended clarification requests
- Likes a clean end-of-session summary
- `say "..."` on macOS is useful for async audio alerts on long agent tasks
- Secrets: only Bret knows production secrets — never ask for them, never log them

## Alert / mute protocol

If any task has been waiting for Bret's input for **more than 30 minutes**, play an audio alert via `say "..."`.

**Mute mode:** After the first alert, enter mute with exponential back-off — alert again after 1h, then 2h, then 4h, then 8h, etc. Bret's next prompt automatically resets mute (he is "off mute" from the moment he types anything). Bret can also say "on mute" explicitly to suppress alerts immediately.

The intent: wake him once for urgent things (e.g. 2am access request), then back off gracefully rather than spamming.

## Docker build / deployment protocol

- There is only **one Docker client** — each build or deployment requires a **manual Docker Desktop restart** by Bret.
- Never start a docker build or deployment without alerting Bret first and receiving explicit approval.
- **Always batch same-session work**: if multiple builds or deployments are queuing up in the same session, ask the dev team to merge all compatible changes before triggering a single build/deploy. Minimise the number of Docker restarts.
- Flag this as a known friction point (manual restart). Bret intends to automate it eventually — do not raise it as a task unless asked.

## Project facts

- Package: `digital.heirlooms` (not com.heirloom)
- Domain: `heirlooms.digital`
- GitHub: `github.com/bacalv/Heirlooms`
- GCP project: `heirlooms-495416`, region: `us-central1`
- Current version: v0.53.1 (14 May 2026)
- Android versionCode: 59

## Live environments

| | Production | Staging |
|--|--|--|
| API | https://api.heirlooms.digital | https://test.api.heirlooms.digital |
| Web | https://heirlooms.digital | https://test.heirlooms.digital |
| DB | `heirlooms` on `heirlooms-db` | `heirlooms-test` on `heirlooms-db` |
| API key | (secret — ask Bret) | `heirlooms-test-api-key` in Secret Manager |

## Architecture summary

- **Server**: Kotlin/http4k, PostgreSQL (Flyway), GCS. Package: `config/ crypto/ domain/ filters/ repository/ routes/ service/ representation/ storage/`. Database.kt is 37 lines — migration façade only.
- **Android**: Jetpack Compose. Flavors: `prod` (production) and `staging` (burnt-orange icon, `.staging` app ID)
- **Web**: React/Vite, nginx on Cloud Run
- **iOS**: Swift/SwiftUI, CryptoKit. Scaffold exists; QRScannerView is a stub (IOS-001 in queue)
- **E2EE**: `p256-ecdh-hkdf-aes256gcm-v1` envelope — see `docs/envelope_format.md`

## M10 design decisions (retrospectively confirmed 2026-05-14 — M10 shipped v0.47.0–v0.50.0)

These decisions were made during M10 implementation and confirmed by reviewing the codebase:
- V28 columns (`status`, `local_name`, `plot_status`, `tombstoned_at`, etc.) are E3 scope — implemented and tested
- `FlowService.createFlow()` returns 400 if target plot has `criteria IS NOT NULL`
- Shared plot invite join: async `pending_plot_key_requests` design (not synchronous)
- **REF-001 window:** M10 is fully shipped — REF-001 may now be scheduled before M11 starts

## Key decisions

- Flow → Trellis rename approved — see `tasks/brainstorming/IDEA-001_trellis-naming.md`, task REF-001 queued
- Coverage gate: 90% overall wired; target 100% for auth/crypto paths — task SEC-002 queued
- Playwright E2E: actor-based, against staging — task TST-004 queued
- Production deployments always require Bret's explicit approval before OpsManager proceeds

## Agent workspace / branching strategy

Each agent that edits code or commits docs works in its own git worktree, not in the CTO workspace.

**CTO workspace** (`~/IdeaProjects/Heirlooms`) — Bret's. PA also commits task files and docs directly to `main` here.

**Agent workspaces** — `~/IdeaProjects/agent-workspaces/Heirlooms/<agent-name>/`

To create one:
```bash
./scripts/create-agent-workspace.sh <agent-name> <task-id>
# e.g. ./scripts/create-agent-workspace.sh developer-1 IOS-001
```

- Worktree is created at `~/IdeaProjects/agent-workspaces/Heirlooms/<agent-name>/`
- Branch: `agent/<agent-name>/<task-id>` based off `origin/main`
- Agent commits to that branch only
- PA (from CTO workspace) reviews, merges to main, then cleans up:
  ```
  git worktree remove ~/IdeaProjects/agent-workspaces/Heirlooms/<agent-name>
  git branch -d agent/<agent-name>/<task-id>
  ```

Agent naming convention: `developer-1`, `developer-2`, `security`, `test-manager`, `ops`, `architect`.

## Pending actions (requires Bret)

- **Disable old staging API key version 1** (2026-05-15): version 2 is live; disable version 1:
  `gcloud secrets versions disable 1 --secret=heirlooms-test-api-key --project heirlooms-495416`

- **Activate prevention hook in main worktree** (2026-05-15):
  `git config core.hooksPath .githooks`
  Also add this line to `scripts/create-agent-workspace.sh` so new worktrees get it automatically.

- **v0.54 iteration — next session checklist** (Phase 5 → 7):
  1. Docker Desktop restart
  2. Build + deploy v0.54 server and web images to staging
  3. **Review manager/architect outputs** before activating TST-007:
     - `docs/briefs/ARCH-003_m11-capsule-crypto-brief.md` — M11 capsule crypto spec
     - `docs/briefs/ARCH-004_connections-data-model.md` — connections schema design
     - `docs/envelope_format.md` — envelope format amendment (ARCH-005 changes)
     - `docs/ops/runbook.md` — deployment runbook (OPS-001)
     - `docs/testing/TST-007_journey-diagrams.md` — Mermaid journey diagrams for all 12 test journeys (being generated now by Architect on branch agent/architect/TST-007-diagrams — merge before review)
  4. Activate TST-007 (12-journey v0.54 staging checklist)
  5. Triage any bugs found → critical re-enter iteration, minor go to queue
  6. Once staging green + manager outputs approved → Operations Manager prepares production release plan for v0.54
  7. Bret approves and promotes to production

- **SEC-009 Part 2 — biometric gate**: review Options A/B/C in the SEC-009 task file and decide before next iteration dispatch.

## Task system

See `tasks/progress.md` for the full queue.
Persona files: `personalities/` — PA, Developer, DevManager, TestManager, OpsManager, SecurityManager, TechnicalArchitect.
Start any session: `@personalities/PA.md`
