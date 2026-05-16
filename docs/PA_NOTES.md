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

**Naming convention (agreed 2026-05-16):** Bret calls the non-production environment
**test** (not staging). Reserve "staging" for a future prod-snapshot environment (OPS-003).

| | Production | Test |
|--|--|--|
| API | https://api.heirlooms.digital | https://test.api.heirlooms.digital |
| Web | https://heirlooms.digital | https://test.heirlooms.digital |
| DB | `heirlooms` on `heirlooms-db` | `heirlooms-test` on `heirlooms-db` |
| API key | (secret — ask Bret) | `heirlooms-test-api-key` in Secret Manager |

Test environment is **throw-away** — accounts, data, and test sessions are disposable.
The GCS bucket is currently shared with production (known gap — OPS-003).

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

## Architecture decisions (2026-05-15)

- **ARCH-003/004/005 approved** — M11 capsule crypto spec, connections model, envelope format amendment all reviewed and approved by Bret.
- **ARCH-006 produced and approved** — TimeLock provider interface: `TimeLockProvider` Kotlin interface, `StubTimeLockProvider` for M11 (HMAC-based fake keys), real drand via Go sidecar for M12. See `docs/briefs/ARCH-006_tlock-provider-interface.md`.
- **tlock blinding scheme approved** — Instead of server re-encrypting the DEK (which would let Heirlooms read capsules post-unlock), the client splits the DEK into two halves at sealing: `DEK_client` (ECDH-wrapped per recipient in `wrapped_capsule_key`) and `DEK_tlock = DEK XOR DEK_client` (IBE-sealed). Server returns `DEK_tlock` at delivery; client XORs. Server never has the full DEK. "Heirlooms cannot read your capsule, ever" holds.
- **iOS compatibility (Option A)**: `wrapped_capsule_key` always wraps the real `DEK` (for iOS). New `wrapped_blinding_mask` wraps `DEK_client` for Android/web blinded path. iOS uses only the ECDH path unchanged.
- **Shamir + tlock**: Shamir shares are always over `DEK`, never `DEK_client`. Executor recovery does not require `/tlock-key`.
- **tlock risk flag**: drand/BLS12-381 integration is M12 scope. M11 uses the stub. Dev Manager should size the Go sidecar task before M12 dispatch.

## Pending actions (requires Bret)

- **SEC-009 Part 2 — biometric gate**: review Options A/B/C in the SEC-009 task file and decide before next iteration dispatch.

- **v0.54 production release — ON HOLD (2026-05-16)**: Bret decided to hold production
  at v0.53.1 and accumulate more fixes before promoting. Too many gaps found during the
  smoke test session to justify a release now. Revisit when BUG-020, BUG-022, and WEB-001
  are resolved and another smoke test cycle passes.

- **Next iteration planning**: large backlog of new tasks created during the smoke test
  session (BUG-019 through BUG-022, FEAT-003/004, SEC-011/012, ARCH-007, TST-008,
  OPS-003). Run "Let's plan the next iteration" to prioritise.

## Legal

Established 2026-05-16. Reports to CTO. Start a session: `@personalities/Legal.md`.

Responsibilities: patent strategy (UK IPO, PCT route), UK GDPR compliance, company law, IP ownership and assignment, terms of service, international law. Works closely with Retirement Planner (exit readiness) and Research Manager (patentability of novel constructions).

Legal briefs: `docs/legal/LEG-NNN_<slug>.md`

**Priority areas flagged at establishment:**
- Assess patentability of the window capsule construction (RES-002) before it is disclosed publicly
- Review IP ownership and contractor assignment agreements
- UK GDPR compliance review for posthumous data / executor access

## Psychologist

Established 2026-05-16. Reports to CTO. Start a session: `@personalities/Psychologist.md`.

Responsibilities: grief-aware UX, executor psychology, digital legacy design, trauma-informed product decisions. Produces briefs to `docs/psychology/PSY-NNN_<slug>.md`.

## Philosopher

Established 2026-05-16. Reports to CTO. Start a session: `@personalities/Philosopher.md`.

Responsibilities: ethics of posthumous consent, digital personhood, long-horizon product ethics, privacy as a value. Produces briefs to `docs/philosophy/PHI-NNN_<slug>.md`.

## Key decisions (2026-05-16 session)

- **Web invite generation**: add a task for web users to generate invite links (equivalent of FEAT-004 on Android) — not covered by WEB-001 which is display-only
- **PQC migration TA spec**: defer until after M11 ships — TA will pick up RES-003 then, not now
- **PQC classical component (X25519 vs P-256)**: defer to TA post-M11 with full RES-003 context
- **PQC grace period**: 180 days approved — Bret agrees this balances security with user realism
- **tlock user-facing positioning**: defer decision until Marketing Director produces a USP brief
- **New personas approved**: Psychologist, Philosopher, and Legal all added (2026-05-16)
- **Pension status**: partial — meaningful gap exists; Heirlooms carries material weight in retirement plan but not the sole vehicle
- **Go/no-go gate**: none — Heirlooms is a long-term personal project; retirement not contingent on exit
- **PQC user communication timing**: defer to Marketing Director (MKT-001 complete — can advise now)
- **Enterprise positioning**: consumer first, enterprise later; Care Mode is the natural B2B wedge when product matures
- **Incorporation**: Bret has a dormant limited company (former IT contracting) with outstanding VAT — decision: form a NEW limited company for Heirlooms; let dormant company proceed to strike-off
- **Intellectual property assignment**: on incorporation, Bret must execute a deed assigning all pre-incorporation Heirlooms intellectual property to the new company; any contractor contributors need retrospective assignment agreements
- **Heirlooms retirement role**: meaningful supplement — other savings could fund modest retirement; Heirlooms is significant upside but not essential
- **Funding strategy preference order**: (1) revisit after first revenue, (2) stay bootstrapped, (3) small strategic round as last resort

## Legal — key findings from LEG-001 (2026-05-16)

**Window capsule: LIKELY PATENTABLE.** No active patent found anticipating tlock IBE lower-bound + XOR DEK blinding + Shamir threshold deletion upper-bound. Closest prior art is Kavousi et al. "Timed Secret Sharing" (ASIACRYPT 2024) — research paper only. Post-*Emotional Perception* [2026] UKSC 3, UK cryptographic method patent threshold is materially lower. Narrow claim on the specific combination recommended.

**Envelope format: WEAK standalone.** Anticipated by JOSE/JWE and Signal-style designs. Add as dependent claims in the window capsule application.

**HMAC tag scheme: WEAK + US FTO risk.** US9454673B1 (Skyhigh Networks) directly relevant. Treat as trade secret. FTO analysis needed before US launch.

**URGENT gap — IP ownership**: No confirmation that Heirlooms is incorporated as a limited company, or that contractor IP assignments exist. Must resolve before filing.

**Disclosure risk**: None identified (repo is private). Do not disclose externally before filing.

**Filing estimate**: UK £10k–£15k (attorney). PCT adds £5k–£10k. Full international portfolio over 3–5 years: £30k–£70k.

**Recommended actions (from Legal):**
1. Confirm incorporation status; incorporate if not yet done
2. Engage CIPA-registered patent attorney — target UK filing by mid-July 2026
3. Identify contractors who contributed to M7/M10/M11 crypto code; confirm IP assignments
4. Keep repo private until after filing
5. Execute NDAs before any investor discussion referencing the window capsule
6. US FTO analysis against US9454673B1 before US public launch of tag scheme

## Research Manager

Established 2026-05-16. Reports directly to CTO. Start a session: `@personalities/ResearchManager.md`. Trigger research: say **"do research"**.

**Research outputs:**
- `docs/research/GLOSSARY.md` — cryptographic term glossary (maintained per task)
- `docs/research/REFERENCES.md` — numbered source log (`[RES-NNN-NNN]` citation scheme)
- `docs/research/RES-NNN_*.md` — research briefs (each ends with PA Summary section)
- `docs/research/horizon/` — idle-queue horizon scan digests

**Completed research:**
- [RES-001](../docs/research/RES-001_crypto-threat-horizon-40kft.md) — 40,000ft cryptographic threat horizon brief. Key finding: P-256 has a credible 8-year window; HNDL is a present threat; Heirlooms' envelope format and DEK-per-file model are strong PQC migration foundations.

**Queued research:**
- [RES-002](../tasks/queue/RES-002_window-capsule-expiry-cryptography.md) — Window capsule (unlock + expire time) cryptographic construction and literature review
- [RES-003](../tasks/queue/RES-003_pqc-migration-readiness-brief.md) — PQC migration readiness brief for Technical Architect (three-layer attack window, migration phases, re-wrap service spec)
- [SIM-001](../tasks/queue/SIM-001_trustless-expiry-impossibility.md) — Simulation: trustless expiry impossibility (throw-away, depends on RES-002)

**Open questions for CTO from RES-001 (unresolved — route at next check-in):**
1. Should the Technical Architect begin specifying the hybrid P-256+ML-KEM migration path now, or after M11 ships?
2. Is there appetite to de-emphasise tlock as a user-facing guarantee given its quantum vulnerability?
3. At what point does Heirlooms communicate post-quantum migration to users?
4. Should Heirlooms pursue regulatory/enterprise positioning (NIST mandate, financial services) as a trust signal?

**Persona recommendations from Research Manager (CTO decision pending):**
- Add Psychologist persona — grief-aware UX, executor psychology; no current owner
- Add Philosopher persona — ethics of posthumous consent, long-horizon product questions

## Marketing Director

Established 2026-05-16. Reports to CTO. Start a session: `@personalities/MarketingDirector.md`.

Responsibilities: brand stewardship (BRAND.md guardian), go-to-market strategy, USP articulation for different audiences, revenue model advice, competitive positioning, partnership strategy (legal entity custodians etc).

Marketing briefs: `docs/marketing/MKT-NNN_<slug>.md`

No briefs produced yet — persona just established.

## Retirement Planner

Established 2026-05-16. Reports directly to Bret (personal adviser, not a company role). Start a session: `@personalities/RetirementPlanner.md`.

Synthesises Research Manager (IP/technical novelty) and Marketing Director (revenue potential, market size) outputs into personal retirement value assessments for Bret. Expertise: UK pensions (SIPP, state pension), startup equity/IP valuation, exit strategy, risk-adjusted investment planning.

Retirement briefs: `docs/retirement/RET-NNN_<slug>.md`

No briefs produced yet — persona just established. First session should begin with a review of RES-001 and the multi-perspective USP analysis from the 2026-05-16 Research Manager session logs.

## Task system

See `tasks/progress.md` for the full queue.
Persona files: `personalities/` — PA, Developer, DevManager, TestManager, OpsManager, SecurityManager, TechnicalArchitect, ResearchManager.
Start any session: `@personalities/PA.md`
