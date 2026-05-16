# Operations Manager Review — Queue Assessment
**Prepared by:** OpsManager
**Date:** 2026-05-16
**Scope:** All tasks currently in `tasks/queue/` (excluding BUG-017 and BUG-018, which are already done)

---

## Methodology

Each task is assessed against seven ops lenses: server deployment required, database
migration required and its risk, test/production environment differences, new
infrastructure requirements, deployment ordering constraints, rollback and data-integrity
concerns, and OPS-003 readiness implications.

Infrastructure facts grounding this review:

- **Two live environments:** Test (`heirlooms-test` DB, `us-central1`) and Production
  (`heirlooms` DB, `europe-west2`). Both on the same Cloud SQL instance (`heirlooms-db`).
- **GCS bucket shared:** `heirlooms-uploads` is used by both environments — known gap.
- **Docker Desktop must be manually restarted before every build.** This is the binding
  constraint on how many separate deploy events can be tolerated in a session.
- **Flyway runs on startup.** Migrations apply automatically; a failed migration blocks
  the new revision and keeps the previous one live — which is a safety net, not a risk.
- **Production deploys require Bret's explicit GO.** OpsManager prepares the release plan;
  Bret approves and supervises execution.

---

## Per-Task Assessment

### BUG-022 — Web detail view blank for shared plot image

| Lens | Assessment |
|---|---|
| Server deployment | No — pure React/Vite change. Web deploy only. |
| DB migration | None. |
| Test vs Production difference | None — same web build goes to both. |
| New infrastructure | None. GCS CORS config unchanged. |
| Deployment order | Must deploy to test first and smoke-test the fix before production promotion. No server dependency. |
| Ops concerns | Low. Web-only. Rollback is a single `gcloud run deploy` with the previous image digest. |
| OPS-003 relevance | None. |

**Summary:** Straightforward web deploy. Batch with WEB-001 and UX-002's web component to
minimise Docker restarts.

---

### TST-008 — Smoke test spec document

| Lens | Assessment |
|---|---|
| Server deployment | No. |
| DB migration | None. |
| Test vs Production difference | N/A — document only at this stage. |
| New infrastructure | None. When TST-008 automation matures, it will target the test environment. |
| Deployment order | No constraint. |
| Ops concerns | None. |
| OPS-003 relevance | TST-008 is a direct prerequisite for OPS-003's seeded smoke test accounts. The spec must be stable before investing in staging infrastructure. |

**Summary:** No ops action. Flag to OpsManager when TST-008 transitions from spec to
automated runner — at that point we need to verify the test environment's seed state and
the GCS bucket isolation fix.

---

### ARCH-007 — E2EE tag scheme design

| Lens | Assessment |
|---|---|
| Server deployment | No — design phase only. Implementation will require server deploy + migration. |
| DB migration | Design will produce a migration plan; none shipped now. |
| Test vs Production difference | N/A at design stage. Implementation phase will require migration across both environments in sequence. |
| New infrastructure | None yet. Implementation may require new Secret Manager entries if a key-wrapping server key is introduced. |
| Deployment order | Implementation (when scheduled) must run on test first with full smoke test cycle before production. Tag metadata is user-visible and E2EE-sensitive — extra caution warranted. |
| Ops concerns | Low now. High when implemented — tag crypto touches every item in the database. |
| OPS-003 relevance | If the tag scheme involves new columns, staging snapshot will need to capture them. No blocker to OPS-003 today. |

**Summary:** No immediate ops action. Flag ARCH-007 implementation as a high-scrutiny
deployment when it arrives — migration will touch all uploads rows.

---

### SEC-012 — Tag metadata leakage accepted-risk doc

| Lens | Assessment |
|---|---|
| Server deployment | No. Documentation only. |
| DB migration | None. |
| Test vs Production difference | N/A. |
| New infrastructure | None. |
| Deployment order | No constraint. |
| Ops concerns | None. |
| OPS-003 relevance | None. |

**Summary:** No ops action required.

---

### BUG-021 — Video duration extraction

| Lens | Assessment |
|---|---|
| Server deployment | Yes — `ThumbnailGenerator` and `UploadService` changes require a server build + Cloud Run deploy. |
| DB migration | The `duration_seconds` column already exists (V19 migration shipped). No new migration required. This removes a significant risk — the column is present in both `heirlooms` and `heirlooms-test` already. |
| Test vs Production difference | Test deploy first, smoke-test video upload, then promote to production. |
| New infrastructure | None. No new GCS resources. If an FFmpeg binary or a Java media library is added to the server Docker image, build time will increase and the image size will grow — flag to developer before task starts. |
| Deployment order | Must deploy server before expecting the fix on any client. Android and web client changes (display logic) can ship after or alongside, but the server is the authoritative data source. |
| Ops concerns | Low migration risk (no schema change). If a media library dependency is added, test the server image build locally before submitting — image size increase could affect Cloud Run cold-start time. |
| OPS-003 relevance | None directly. |

**Summary:** Server deploy required. No migration risk. Batch server build with BUG-020
and SEC-011 to minimise Docker restarts.

---

### BUG-020 — Shared plot auto-approve DEK re-wrap

| Lens | Assessment |
|---|---|
| Server deployment | Yes — server accepts new optional fields on the trellis-route endpoint. Build + Cloud Run deploy required. |
| DB migration | None — the existing `plot_items` schema is sufficient; only the insertion path changes. |
| Test vs Production difference | Test deploy first. This is a crypto-adjacent path (DEK re-wrapping) — smoke test must verify both the auto-approve path and the staging fallback path before production. |
| New infrastructure | None. |
| Deployment order | Server must deploy before the updated Android APK is installed. The server change is backward-compatible (optional new fields) — old APKs continue working against the new server. |
| Ops concerns | Medium. DEK re-wrapping is a crypto-sensitive operation. A bug here could produce items that members cannot decrypt. The staging fallback path must remain fully functional after the deploy. Two explicit acceptance criteria: member can decrypt auto-approved items; non-member items still stage correctly. |
| OPS-003 relevance | None. |

**Summary:** Server + Android deploy. Server change is backward-compatible — safe to
deploy server first, APK second. Batch server build with BUG-021 and SEC-011.

---

### FEAT-004 — Android invite friend

| Lens | Assessment |
|---|---|
| Server deployment | No — the invite-link API already exists. Android-only change. |
| DB migration | None. |
| Test vs Production difference | APK builds for `stagingDebug` (test) and `prodRelease` (production). |
| New infrastructure | None. |
| Deployment order | No server dependency. Can be batched with other Android APK changes. |
| Ops concerns | Low. UI-only change. |
| OPS-003 relevance | None. |

**Summary:** Android APK only. Batch with BUG-019, FEAT-003, UX-001 to form one APK build event.

---

### SEC-011 — Device revocation

| Lens | Assessment |
|---|---|
| Server deployment | Yes — new `DELETE /api/auth/devices/{deviceId}` endpoint. Server build + Cloud Run deploy required. |
| DB migration | None — deletion operates on existing `wrapped_keys` and `user_sessions` tables. No schema change. |
| Test vs Production difference | Test deploy and manual verification first (revoke a test device, confirm the row is gone and sessions are invalidated). Then production. |
| New infrastructure | None. No new Secret Manager entries. |
| Deployment order | Server must deploy before the Android and web UI changes are live. Old clients (without the revoke UI) are unaffected — the endpoint is additive. |
| Ops concerns | Medium. Session invalidation is a high-trust operation. A bug that over-invalidates sessions (e.g. invalidates the calling user's own session) would log users out. The 403-on-current-device guard is critical — verify this in testing. Zero downtime expected: Cloud Run rolling updates mean no existing sessions are interrupted by the deploy itself. |
| OPS-003 relevance | None. |

**Summary:** Server + Android + web deploy. Batch server build with BUG-020 and BUG-021.
Batch web deploy with BUG-022 and WEB-001.

---

### BUG-019 — Registration error message

| Lens | Assessment |
|---|---|
| Server deployment | No. Android-only change. |
| DB migration | None. |
| Test vs Production difference | APK flavours as usual. |
| New infrastructure | None. |
| Deployment order | No constraint. |
| Ops concerns | Low. |
| OPS-003 relevance | None. |

**Summary:** Android APK only. Batch with FEAT-004, FEAT-003, UX-001.

---

### FEAT-003 — Android account pairing / recovery

| Lens | Assessment |
|---|---|
| Server deployment | Depends on chosen option. Option A (passphrase recovery on Android) likely reuses an existing endpoint — no new server deploy. Option B (Android-initiated pairing code) requires a new server endpoint, build + Cloud Run deploy. |
| DB migration | Option A: none. Option B: likely a new short-lived pairing-codes table or use of an existing session mechanism — low-risk additive migration if needed. |
| Test vs Production difference | If a server endpoint is added, test environment first. Android APK then tested against test API. |
| New infrastructure | None for Option A. Option B may need a new Secret Manager entry if a signing key for pairing codes is introduced. |
| Deployment order | If server endpoint is needed: server deploy before APK installation. Option A has no server ordering constraint. |
| Ops concerns | Medium. Pairing and recovery flows touch the E2EE master-key handoff path. A bug in key wrapping during pairing would produce an inaccessible account. Careful integration testing on test environment before production. |
| OPS-003 relevance | None. |

**Summary:** Option A is ops-simpler and preferred from an infrastructure standpoint.
Option B adds a server deploy event — if chosen, batch with the server deploy wave.
Regardless, Android APK is required.

---

### TST-006 — Android remote control research

| Lens | Assessment |
|---|---|
| Server deployment | No. |
| DB migration | None. |
| Test vs Production difference | N/A — research only. Output may influence how TST-004 Playwright suite interacts with Android, but that is a future concern. |
| New infrastructure | None now. |
| Deployment order | No constraint. |
| Ops concerns | None. |
| OPS-003 relevance | If Android remote control requires the test environment to be up and responsive, confirm test health before dispatch. No infrastructure changes needed. |

**Summary:** No ops action.

---

### UX-002 — Closed plot visual indicator

| Lens | Assessment |
|---|---|
| Server deployment | No server changes. Android + web UI only. |
| DB migration | None. |
| Test vs Production difference | Two deploy events: web deploy (test → production) and Android APK. |
| New infrastructure | None. |
| Deployment order | No server dependency. Web change can be batched with other web deploys. |
| Ops concerns | Low. Visual-only change. |
| OPS-003 relevance | None. |

**Summary:** Web deploy + Android APK. Batch the web component with BUG-022, SEC-011
(web), WEB-001. Batch Android component with other APK changes.

---

### TST-004 — Playwright E2E suite

| Lens | Assessment |
|---|---|
| Server deployment | No. |
| DB migration | None. |
| Test vs Production difference | Playwright runs against the test environment. Production is never targeted by automated tests. The test environment's state (accounts, data) must be predictable — seeded accounts are ideal. This links directly to OPS-003 once TST-008 is stable. |
| New infrastructure | Depends on where Playwright runs. If it runs in CI, no infrastructure changes now. If it runs as a Cloud Run job or Cloud Build trigger, that is a small infrastructure task (not in scope here). |
| Deployment order | No constraint to start development. Infrastructure decision for CI runner is a follow-on. |
| Ops concerns | None for the suite itself. The test environment's GCS bucket sharing with production is a latent risk if tests write uploads — shared bucket means test uploads land in production GCS storage. This is a reason to prioritise the GCS bucket fix. |
| OPS-003 relevance | High. TST-004 is the automation that OPS-003's staging environment is designed to run against. Do not invest in OPS-003 until TST-004 has a working baseline. |

**Summary:** No immediate ops action. Highlight GCS bucket sharing as a concern once
Playwright suite starts writing uploads.

---

### SEC-002 — Auth/crypto test coverage

| Lens | Assessment |
|---|---|
| Server deployment | No production deployment — test/CI only. Changes are in `HeirloomsServer` test sources. |
| DB migration | None. |
| Test vs Production difference | N/A — test code only. Testcontainers spins up an ephemeral PostgreSQL — no cloud environment involvement. |
| New infrastructure | None. Note the known constraint: Docker Desktop restart is required before Testcontainers can run integration tests on Bret's machine. |
| Deployment order | No constraint. |
| Ops concerns | None. Docker Desktop restart is required before running tests locally — not a blocker, just the known friction point. |
| OPS-003 relevance | None. |

**Summary:** No ops action. Flag Docker Desktop restart requirement to developer at task
start.

---

### UX-001 — Android tap targets

| Lens | Assessment |
|---|---|
| Server deployment | No. Android-only. |
| DB migration | None. |
| Test vs Production difference | APK flavours. |
| New infrastructure | None. |
| Deployment order | No constraint. |
| Ops concerns | Low. |
| OPS-003 relevance | None. |

**Summary:** Android APK only. Batch with BUG-019, FEAT-003, FEAT-004.

---

### WEB-001 — Web friends list

| Lens | Assessment |
|---|---|
| Server deployment | No server changes — API already exists. Web deploy only. |
| DB migration | None. |
| Test vs Production difference | Same web build goes to both environments after test verification. |
| New infrastructure | None. |
| Deployment order | No server dependency. Batch with BUG-022 and the web components of SEC-011 and UX-002. |
| Ops concerns | Low. Display-only UI change. |
| OPS-003 relevance | None. |

**Summary:** Web deploy only. Good candidate for the first web deploy batch.

---

### DOC-001 — Sequence diagrams

| Lens | Assessment |
|---|---|
| Server deployment | No. |
| DB migration | None. |
| Test vs Production difference | N/A. |
| New infrastructure | None. |
| Deployment order | No constraint. |
| Ops concerns | None. |
| OPS-003 relevance | None. |

**Summary:** No ops action.

---

### OPS-003 — Pre-production staging environment

This task is ops-owned. Full assessment below.

| Lens | Assessment |
|---|---|
| Server deployment | Yes — two new Cloud Run services: `heirlooms-server-staging` and `heirlooms-web-staging`. These are new services, not redeployments of existing ones. |
| DB migration | New database `heirlooms-staging` on the existing `heirlooms-db` Cloud SQL instance (or a new instance). Flyway runs on first startup to build the schema. No migration of existing data — schema is built from scratch, then populated from an anonymised prod snapshot. |
| Test vs Production difference | This task creates the staging tier — it is definitionally a third environment, not a variant of test or production. |
| New infrastructure | Substantial: new Cloud Run services × 2, new Cloud SQL database (or instance), new GCS bucket (`heirlooms-uploads-staging`), new Secret Manager entries (staging API key, staging DB password), DNS entries (`staging.api.heirlooms.digital`, `staging.heirlooms.digital`), two new scripts (`anonymise-snapshot.sh`, `seed-staging-accounts.sh`), CORS config updated for the new web origin. |
| Deployment order | Prerequisites (see below) must be resolved before OPS-003 is started. |
| Ops concerns | High effort, low operational risk if done carefully. The anonymisation script must be audited before the first production snapshot is taken — a bug that fails to scramble usernames could expose real user metadata in staging. The seed script must generate fresh E2EE key material (not copied from production). The staging GCS bucket must have separate IAM bindings from production. |
| OPS-003 readiness | Not ready — see prerequisites below. |

---

## OPS-003 Readiness and Sequencing

OPS-003 should be held until the following conditions are met:

1. **GCS bucket isolation (prerequisite, must be first):** The test environment currently
   shares `heirlooms-uploads` with production. Before creating a staging environment, the
   test environment must be moved to its own bucket (`heirlooms-uploads-test`). This
   prevents three environments from sharing one bucket. This is a standalone infrastructure
   task that should be scheduled before OPS-003 is dispatched. Steps: create new bucket,
   update `heirlooms-server-test` Cloud Run env var, update CORS config, verify test uploads
   land in the new bucket, confirm production bucket is clean. Low risk since test data is
   throw-away.

2. **TST-008 spec is stable:** The smoke test spec must be settled before seeded accounts
   are designed, because the seed script must produce accounts that can pass the smoke test.

3. **TST-004 has a working baseline:** OPS-003's primary value is giving Playwright a
   predictable, prod-shaped environment. Until TST-004 has a running suite, the staging
   environment has no consumer.

4. **Current priority batch clears production hold:** Bret has held production at v0.53.1.
   The immediate queue (BUG-020, BUG-022, WEB-001) must ship and another smoke test pass
   before OPS-003 is meaningful.

**Recommendation:** Schedule OPS-003 after BUG-020/022/WEB-001 ship, TST-008 is
drafted, and the GCS bucket isolation fix is complete. Estimated earliest: two iterations
from now.

---

## Deployment Grouping Table

Each group represents a single Docker Desktop restart event. Tasks in the same group
should have their code merged and tested before the build is triggered.

### Server deploy wave (one Docker restart + build)

| Task | Change | Notes |
|---|---|---|
| BUG-021 | `ThumbnailGenerator`, `UploadService` | Confirm no new binary dependencies before build |
| BUG-020 | Trellis-route endpoint — new optional fields | Backward-compatible; old APKs unaffected |
| SEC-011 | `DELETE /api/auth/devices/{deviceId}` | Additive endpoint |
| FEAT-003 (Option B only) | New pairing-code endpoint | Only if Option B is chosen |

All four changes are independent and additive. Merge to a single server branch, build
once, push once, deploy to test, verify, then promote to production.

If FEAT-003 chooses Option A, it drops off this list and the server wave becomes three
tasks.

### Web deploy wave (one Docker restart + build)

| Task | Change | Notes |
|---|---|---|
| BUG-022 | `PhotoDetailPage.jsx`, `vaultCrypto.js` | High priority — production hold blocker |
| WEB-001 | `FriendsPage.jsx`, `App.jsx` | No API changes |
| SEC-011 (web UI) | `DevicesAccessPage.jsx` | Requires server deploy to be live first |
| UX-002 (web UI) | Closed plot visual indicator | Low risk |

The web wave has a partial ordering constraint: SEC-011's web UI depends on the server
endpoint being live. If the server wave and web wave are deployed in the same session,
deploy the server first, verify health, then build and deploy the web image.

BUG-022 and WEB-001 are production-hold blockers and should be in the earliest possible
web deploy wave, even if SEC-011 and UX-002 are not yet ready.

### Android APK wave (one Gradle build event)

| Task | Change | Notes |
|---|---|---|
| BUG-019 | Registration error message | Trivial |
| BUG-020 (Android client) | DEK re-wrap in `GardenViewModel` | Requires server wave to be live first |
| FEAT-003 (Android) | Recovery flow on login screen | E2EE-sensitive — test carefully |
| FEAT-004 | Invite friend from Friends screen | UI-only |
| UX-001 | Tap targets | Visual-only |
| UX-002 (Android) | Closed plot visual indicator | Visual-only |

BUG-020's Android client must not be installed before the server wave is deployed
(the server must accept the new optional fields). All other Android tasks are
independent of the server wave.

### No deployment required

| Task | Reason |
|---|---|
| TST-008 | Docs/spec |
| ARCH-007 | Design phase |
| SEC-012 | Documentation |
| TST-006 | Research |
| TST-004 | Test suite (no production deploy) |
| SEC-002 | Server-side test code only — Testcontainers, no cloud deploy |
| DOC-001 | Documentation |

---

## Recommended Deployment Order

The recommended sequence for deploying tasks to production (each step assumes test
verification first):

```
Step 1 — Server wave
  Deploy: BUG-021 + BUG-020 (server) + SEC-011 (server) [+ FEAT-003 server if Option B]
  One Docker restart, one build, one Cloud Run deploy to test, smoke test, then production.

Step 2 — Web wave (core blockers)
  Deploy: BUG-022 + WEB-001
  These are the production-hold blockers. Deploy as soon as the web code is ready,
  even if SEC-011 and UX-002 web UI are not finished.
  One Docker restart, one build, one web Cloud Run deploy to test, then production.

Step 3 — Web wave (remainder)
  Deploy: SEC-011 (web UI) + UX-002 (web UI)
  Batch into the next web deploy event. SEC-011 server endpoint must already be live.

Step 4 — Android APK wave
  Build: BUG-019 + BUG-020 (Android) + FEAT-003 (Android) + FEAT-004 + UX-001 + UX-002 (Android)
  BUG-020 Android client requires the Step 1 server to be live.
  One Gradle build, install stagingDebug on test device, smoke test, then prodRelease.
```

Steps 1 and 2 are the critical path to lifting the v0.54 production hold. Steps 3 and
4 can follow in the next session.

---

## Infrastructure Gaps to Address Before Specific Tasks

| Gap | Blocks | Recommended action |
|---|---|---|
| GCS bucket shared between test and production (`heirlooms-uploads`) | OPS-003, TST-004 (once writing uploads) | Create `heirlooms-uploads-test` bucket, update test Cloud Run env var, update CORS config. Schedule before OPS-003. |
| No staging environment (OPS-003 not started) | TST-004 (Playwright against prod-shaped data), TST-008 automation | Hold until GCS fix, TST-008 spec stable, and TST-004 baseline running. |
| Runbook's "Staging" terminology vs agreed "Test" naming | All ops documentation | Update runbook §2–§4 headings to say "Test" not "Staging" — low priority but reduces confusion in future sessions. |
| Production server in `europe-west2`, test server in `us-central1` | Not a blocker, but asymmetry means latency behaviour is untested | Document in architecture notes. If OPS-003 staging environment is created, decide whether it mirrors production's region (`europe-west2`) or stays in `us-central1`. |
| Docker Desktop manual restart before every build | All build/deploy events | Known friction point. No task assigned. Bret intends to automate — do not raise as a task unless asked. |

---

## Summary Judgement

The queue is well-shaped for a two-session delivery cycle:

- **Session A (server + critical web):** Deploy the server wave (BUG-021, BUG-020 server,
  SEC-011 server) and the core web wave (BUG-022, WEB-001). This unblocks the production
  hold. Two Docker restarts, two builds.

- **Session B (remainder):** Deploy the remaining web UI (SEC-011, UX-002) and the full
  Android APK wave. Two more build events (one web, one Gradle).

No task in the current queue requires new GCP resources, new secrets, or new DNS
entries — with the exception of OPS-003, which is explicitly not ready to start.
Migration risk is essentially zero: the only server task touching DB schema (BUG-021)
relies on a column that already exists (V19). All other server changes are additive
endpoint changes with no schema delta.

OPS-003 should be scheduled independently, after the immediate queue clears and the GCS
bucket isolation prerequisite is completed. It is a meaningful infrastructure investment
but has no bearing on the current production hold or the tasks needed to lift it.
