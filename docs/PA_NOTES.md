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
- Current version: v0.56 (in progress — all tasks merged, pending TST-012 sign-off)
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

## Key decisions (2026-05-16 iteration planning session)

- **Biometric gate (SEC-015)**: account-level server-synced `require_biometric` boolean. Default OFF (opt-in). Gates the vault Activity/screen — no Keystore key migration. Web shows info note only. iOS and Android both respect the flag. Task SEC-015 queued for next iteration.
- **SEC-011 timing**: approved to ship this iteration as scoped (delete wrapped_keys row, no rotation). ARCH-009 will confirm pattern compatibility retrospectively. BUG-019 batched into SEC-011 worktree (both touch auth server files).
- **FEAT-003 split**: FEAT-003a (design spike — answer 4 open questions, produce decision doc) dispatched this iteration. FEAT-003b (implementation) deferred to next iteration pending spike output.
- **iOS security**: SEC-013 (iOS security review) and SEC-014 (privacy screen on backgrounding) created and queued — next iteration.
- **v0.55 iteration scope approved** — see iteration plan below.

## Pending actions (requires Bret)

- **TST-012 staging deploy + sign-off (v0.56)**: All v0.56 tasks are merged to main
  (including BUG-029 fix). Before activating TST-012:
  1. Docker Desktop restart
  2. Build and deploy server + web images to test environment
  3. Build and sideload Android staging APK
  4. Activate `tasks/queue/TST-012_manual-staging-checklist-v056.md` and work through journeys.
  Key new journeys to verify: web pairing device entry (BUG-029), garden load on launch
  (BUG-027), inline-tag trellis routing (BUG-024), biometric gate (BUG-028).

- **v0.55 / v0.56 production release**: pending TST-012 conditional pass.
  Promote only after staging sign-off. All known v0.55 bugs (BUG-019–029) are fixed.

- **Xcode toolchain mismatch**: SourceKit shows "SDK not supported by compiler" errors
  (swiftlang-6.0.3.1.5 vs 6.0.3.1.10). Fix: Xcode → Settings → Locations →
  Command Line Tools — select the matching toolchain. Not blocking any agent work.

- **SEC-009 Part 2 decision deferred**: Keystore-backed biometric gate — deferred to future iteration.

- **Tag → Label rename (REF-002)**: queued. Dispatch when ready for a separate pass.

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

## Key decisions (2026-05-16 session — continued)

- **Tag → Label rename**: approved. Full rename across UI, API, code, and docs. REF-002 queued.
- **Unified messaging model**: ARCH-011 queued for TechnicalArchitect investigation.
  Three concepts: capsule item messages (per-message visibility/E2EE), shared plot messages
  (owner moderation — auto-approve vs owner-decides), message trellises (trellis concept
  applied to message visibility; labels govern access).
- **Docker web image**: must build with `--platform linux/amd64` flag. Without it, Docker
  buildx produces an OCI manifest index that Cloud Run rejects.
- **Task file moves**: always use `git mv` (or stage the move) when moving task files
  between queue/in-progress/done in the main workspace. Raw `mv` leaves deletions unstaged.
- **GCS bucket separated**: `gs://heirlooms-uploads-test` created and wired to
  `heirlooms-server-test` Cloud Run. Test environment no longer shares production GCS bucket.
- **PAP-001 NARRATIVE_PLACEHOLDER**: TAU-001 guide (docs/papers/PAP-001) has a placeholder
  for PHI-002 narrative (now in docs/philosophy/PHI-002). TechnicalAuthor needs one more pass
  to weave them together before PAP-001 is publication-ready.
- **Mermaid special chars**: Mermaid parses commas (`,`) and semicolons (`;`) as statement
  separators/terminators in message labels and Note text. Escape as `&#44;` and `&#59;`.

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
- [RES-002](../docs/research/RES-002_window-capsule-expiry-cryptography.md) — Window capsule (unlock + expire time) cryptographic construction and literature review. Key finding: tlock + Shamir deletion construction is substantially novel; patentable as a combination.
- [RES-004](../docs/research/RES-004_chained-capsule-cryptographic-assessment.md) — Chained capsule cryptographic assessment. Key finding: composable from existing primitives; first-solver-wins requires server-mediated atomic claim.
- [RES-005](../docs/research/RES-005_presence-gated-delivery.md) — Presence-gated post-window delivery construction. Key finding: novel two-phase capsule where a time window acts as a presence gate; only recipients who prove they opened during [w1, w2] receive content after expiry. Route to Legal before any external disclosure.

**Queued research:**
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

## Session wrap-up (2026-05-17 — v0.56 deploy + sharing flow tests)

### What happened this session

**v0.56 deployed to test environment** — Server (revision 00012-4jc) and web (revision 00011-rsn) deployed to `test.api.heirlooms.digital` / `test.heirlooms.digital`. Both healthy. Android APK build deferred — Bret to sideload manually.

**23 API smoke tests run against test environment** — All passed. Covered: health, auth, challenge/login, register, pairing, invite generation, devices (BUG-029 endpoint confirmed), biometric account patch (BUG-028), friends, passphrase, link flow.

**TST-013 delivered** — `SharingFlowIntegrationTest.kt` written (6 tests: 4 integration via Testcontainers + LocalFileStore, 2 unit for the sha256 auth contract). All pass via `./gradlew test`. Side-fixed a pre-existing build failure in `SessionAuthFilterUnitTest.kt`.

**Sharing flow smoke script delivered** — `scripts/smoke_sharing_flow.py` runs 11 checks against the live test API: register two users, friend connect, upload, share, byte-exact retrieval, plus 3 negative cases. Passed 11/11 twice.

### Known gotchas for next session

- **TST-012 still the gate** — manual staging checklist not yet run. Android APK must be sideloaded first (journeys require Android + web). BUG-025 and BUG-026 not yet retested.
- **Android APK** — `cd HeirloomsApp && ./gradlew assembleStagingDebug`, then `adb install -r app/build/outputs/apk/staging/debug/app-staging-debug.apk`.
- **Production deploy** — pending TST-012 conditional pass.

---

## Session wrap-up (2026-05-17 — Legal / Retirement Planning session)

### What happened this session

**Patent filing steps briefed** — Legal Counsel walked through the full filing sequence: UK IPO → PCT → national phase. Cost: £10,000–£15,000 immediate (UK filing), £30,000–£70,000 total over 3–5 years. Mid-July 2026 target for UK filing established.

**Funding options assessed** — Bank loan to newly incorporated company ruled out (no trading history). Two viable paths: director's loan (Bret lends personal funds into the company) or SEIS angel investment.

**RET-003 produced** — Retirement Planner assessed both paths and strongly recommended director's loan over SEIS. Key finding: SEIS at this stage costs £111,000–£1,800,000 in base-scenario exit proceeds to raise £25,000. SEIS also cannot fund the initial filing (patent must be filed before approaching investors; HMRC advance assurance takes 4–6 weeks). PCT and national phase deferred to 12-month and 30-month marks respectively — only £10,000–£15,000 needed immediately.

**Key open question for Bret** — Does Bret have accessible personal cash savings (outside ISA and pension) of £10,000–£15,000? Answer determines whether director's loan path is clear or whether staged attorney billing / other bridging is needed.

### Known gotchas for next session (Legal)

- **CRN not yet received** — Certificate of Incorporation is pending (applied 2026-05-17, up to 2 working days). CRN required before: signing LEG-004 (IP assignment deed), sending JUXT outside interests declaration, and filing the patent in the company's name.
- **LEG-003 still queued** — RES-005 patent assessment (presence-gated delivery + count-conditional trigger) must be completed before briefing a patent attorney. Do not engage attorney without LEG-003 in hand.
- **No attorney engaged yet** — CIPA-registered patent attorney experienced in cryptographic methods must be commissioned. Target engagement within 2 weeks of CRN receipt to hit mid-July 2026 filing date.
- **JUXT consent sequence** — Declaration (LEG-005) drafted; send immediately on CRN receipt; ideally have written JUXT consent before patent is filed.

---

## Session wrap-up (2026-05-17)

### What happened this session

**TST-010 completed (v0.55 manual staging checklist)** — Ran all 22 journeys with Bret on physical devices. Conditional pass. 7 bugs logged (BUG-023 through BUG-029). 3 infrastructure issues fixed (GCS SA grant, VITE_API_URL in Dockerfile, GCS CORS).

**v0.56 iteration dispatched** — Full planning cycle, 9 agents dispatched in parallel. All 9 tasks completed and merged. Key deliverables: BUG-023–028 fixed, ARCH-012/013 briefs produced, TST-004 Playwright suite, SEC-002 auth unit tests, OPS-002 guide.

**Unit test mandate established** — All future tasks must include unit tests. No exceptions. Logged as a systemic feedback memory.

**BUG-029 fixed (this session, manually)** — Web pairing never called `POST /api/keys/devices`, so the web browser was invisible in Devices & Access. Fix: `PairPage.jsx` now generates the persistent device keypair and registers it using the pairing session token immediately after the ECDH unwrap. Regression test added to `vaultUnlock.test.jsx`.

### Known gotchas for next session

- **TST-012 is the gate** — don't start any new iteration planning until staging checklist passes.
- **Docker Desktop restart required** before each build/deploy (known friction, not a task).
- **BUG-025, BUG-026** still queued (web friends nav link, invite link auto-route). Low priority — can batch into a maintenance pass.
- **ARCH-012/013 refactor briefs are ready** — web and Android package restructures are documented but not dispatched. Prerequisite for meaningful unit test coverage. Plan these as M-prefix milestone tasks when iteration bandwidth allows.
- The `tasks/progress.md` queue stale entry for BUG-029 was cleaned up in this session.

---

## Key decisions (2026-05-17 — strategy session)

- **Server-first strategy approved** — App development (Android, web, iOS) is frozen at current feature set. No new features to client apps until M11 + M12 backend is complete and stable.
- **M11 + M12 backend is the next priority** — After TST-012 passes and production is deployed, iteration planning begins for M11 (strong sealing, tlock, Shamir) and M12 (milestone delivery). ARCH-003/004/005/006 specs are already done and approved.
- **Standalone Kotlin client approved** — `tools/api-client/` Gradle module (TOOL-001) to serve as M11/M12 proving ground and patent demonstration artefact. Phase 1: full capsule lifecycle against current API. Phase 2: tlock + Shamir extensions post-M11.
- **API stability contract required** — ARCH-015 tasked before M11 starts. Must define frozen endpoint surface, stability policy, versioning approach, and enforcement via integration tests simulating frozen client request shapes.
- **iOS work is a parallel personal stream** — Getting the iOS app onto Bret's mother's phone (requires IOS-001 QR scanner completion) runs in parallel with server development. It does not block M11. iOS fixes raised as IOS-NNN tasks as needed; they do not interrupt the server stream.
- **Priority patent application strategy** — LEG-006 tasked to Legal to assess what a UK priority application actually protects vs a full application, and whether it prevents third-party implementation during the gap. Outcome will inform filing sequencing.
- **Attorney fee reduction options** — Marketing Director assessed three approaches: (1) hybrid cash + equity arrangement with a CIPA sole practitioner, (2) shopping to boutique/sole practitioners (30–40% cheaper than large firms), (3) priority filing first to buy 12 months at low cost. No decision taken — pending LEG-006 output and Bret's personal cash assessment.
- **JUXT investment options assessed** — Marketing Director outlined four structures: Jon Pither personally via SEIS, JUXT Ltd straight equity, convertible loan note, services-for-equity. Recommended sequencing: get JUXT consent (LEG-005) before any investment conversation. Investment conversation strongest after patent is filed.

## M11 branch strategy (2026-05-17)

- **`main`** — frozen at v0.56. Used for TST-012 manual sign-off and the production deploy. No M11 work lands here until M11 is ready to release.
- **`M11`** — long-lived development branch. All M11 agent tasks branch off `origin/M11` and merge back into `M11`. When creating agent workspaces for M11 tasks, the `create-agent-workspace.sh` script must be given the base branch `M11` (not `main`).
- **Iteration sign-off for M11** — no test environment deploy between iterations. Instead, a local stack (Testcontainers PostgreSQL + LocalFileStore + in-process server) will be used. TST-014 is investigating the design.

## Sequence from here

```
Now          → TST-012 (Bret, hands-on)
              → Production deploy (OpsManager, Bret approval)

After prod   → Two parallel streams:

  SERVER STREAM (agents)              PERSONAL STREAM (Bret)
  ──────────────────────              ──────────────────────
  ARCH-015: API stability contract    iOS testing on own phone
  M11 iteration planning              IOS-001 dispatched if fixes needed
  M11 backend implementation          Mother's phone install (no fixed date)
  TOOL-001: Kotlin client module
```
