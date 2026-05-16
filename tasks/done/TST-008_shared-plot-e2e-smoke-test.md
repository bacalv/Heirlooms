---
id: TST-008
title: Shared plot E2E smoke test — formalise spec and identify automation strategy
category: Testing
priority: High
status: done
depends_on: []
touches:
  - docs/testing/
  - HeirloomsWeb/tests/ (Playwright)
assigned_to: TestManager
estimated: half day (spec) + separate automation tasks
---

## Background

Defined during v0.54 staging smoke test session (2026-05-15/16). The test was run
manually by the CTO and surfaced multiple bugs (BUG-019 through BUG-022) and feature
gaps (FEAT-003, FEAT-004, WEB-001, SEC-011, ARCH-007).

The goal is to formalise this as a reusable smoke test that can eventually be
automated — with Android devices plugged in via USB and agents running through the
steps with minimal manual intervention.

## Smoke test steps

### Step 1 — Account Setup

**Intent:** Create two fresh test accounts with known credentials and a friendship
between them.

| Sub-step | Action | Expected |
|----------|--------|----------|
| 1.1 | Wipe Fire OS staging app (Clear storage) | App returns to registration screen |
| 1.2 | Generate invite token for User A from an existing staging account | Token generated, expires in 48h |
| 1.3 | Register User A on Fire OS | Username: `smokeA`, display: `Smoke A` |
| 1.4 | Generate invite for User B from User A | Token generated |
| 1.5 | Register User B on web | Username: `smokeB`, display: `Smoke B`; friendship A↔B auto-created |

**Known gaps:**
- BUG-019: duplicate device_id shows "Username already exists" — requires DB cleanup
- FEAT-004: Android has no "Invite a friend" button — invite token must be generated
  via API or DB workaround during automation

---

### Step 2 — Friendship verification

**Intent:** Confirm the A↔B friendship is visible in both front-ends.

| Sub-step | Action | Expected |
|----------|--------|----------|
| 2.1 | User A → burger → Friends | Smoke B listed |
| 2.2 | User B → Friends (web) | Smoke A listed |

**Known gaps:**
- WEB-001: Web friends list page not yet built — API verification only

---

### Step 3 — Shared plot creation and member invite

**Intent:** User B creates a shared plot and invites User A.

| Sub-step | Action | Expected |
|----------|--------|----------|
| 3.1 | User B (web): Garden → + Shared plot → name `smoke-test-plot` | Plot created |
| 3.2 | User B: plot → Members → Invite → select Smoke A | Invite sent |
| 3.3 | User A (Android): Shared → pending invite → Accept | Plot appears as `smoke-test-plot` |

**Pass criteria:** Plot appears on both User A's Android and User B's web garden.

---

### Step 4 — One-way automatic publish (User A → shared plot → User B)

**Intent:** User A uploads media, tags it, and it routes to the shared plot via trellis.
User B can view and decrypt it.

| Sub-step | Action | Expected |
|----------|--------|----------|
| 4.1 | User A (Android): Trellises → create `smoke-test-trellis-a`, criteria: tag=`smoke-test-tag`, target: `smoke-test-plot` | Trellis created |
| 4.2 | User A: take or import a fresh photo | Item appears in Just Arrived |
| 4.3 | User A: tag item with `smoke-test-tag` | Item routes to staging queue |
| 4.4 | User A (or plot owner): approve item in staging panel | Item appears in shared plot |
| 4.5 | User B (web): refresh garden → open `smoke-test-plot` | Item visible, thumbnail decrypts |
| 4.6 | User B: open item detail | Full image decrypts and displays |

**Known gaps:**
- BUG-020: auto-approve currently forced to staging (BUG-018) — client-side DEK
  re-wrap needed before true auto-publish is possible
- BUG-022: web detail view shows blank for shared plot images (plot key DEK not handled)
- BUG-021: video uploads show 0-second duration on both platforms

---

### Step 5 — Bidirectional publish (User B → shared plot → User A)

**Intent:** User B contributes to the same shared plot; User A can decrypt it on Android.

| Sub-step | Action | Expected |
|----------|--------|----------|
| 5.1 | User B (web): create `smoke-test-trellis-b`, criteria: tag=`smoke-test-tag`, target: `smoke-test-plot` | Trellis created |
| 5.2 | User B: upload a fresh image | Item appears in User B's vault |
| 5.3 | User B: tag item with `smoke-test-tag` | Item routes to staging queue |
| 5.4 | User B: approve in staging panel | Item appears in shared plot |
| 5.5 | User A (Android): refresh garden → open `smoke-test-plot` | User B's item visible, thumbnail decrypts |
| 5.6 | User A: open item detail | Full image decrypts and displays |

**Status:** Defined, not yet run. Likely to surface the same BUG-022 (web detail) gap
in reverse on the Android side — TBD.

---

### Step 6 — Automatic shared plot tagging *(future feature)*

**Intent:** When a new item appears in a member's shared plot, the system automatically
applies one or more private tags to it. These tags are:
- Private to the member who configured them (invisible to other members and to
  Heirlooms staff)
- E2EE — stored as HMAC tokens (see ARCH-007)
- Loop-safe — auto-applied tags cannot re-trigger trellises

**Status:** Feature not yet designed or implemented. Blocked on ARCH-007 (E2EE tag
scheme). Do not implement until ARCH-007 is approved.

---

## Automation strategy

| Step | Automatable now? | Tool | Notes |
|------|-----------------|------|-------|
| 1 — Account setup | ✅ Mostly | API calls | Device wipe and APK install require ADB |
| 2 — Friendship verification | ✅ | API + Playwright | Web UI gap (WEB-001) |
| 3 — Shared plot + invite | ✅ | Playwright (web) + ADB (Android) | Android accept requires TST-006 |
| 4 — One-way publish | ⚠️ Partial | Playwright + ADB | Android tagging requires TST-006; staging approval automatable via Playwright |
| 5 — Bidirectional publish | ⚠️ Partial | Same as Step 4 | |
| 6 — Auto-tagging | ❌ | — | Feature not yet built |

**Dependency:** Full Android automation requires TST-006 (Android remote control
investigation). Playwright infrastructure is already in place (TST-005 done).

## Deliverables

1. Formal smoke test spec document in `docs/testing/smoke_test_shared_plot.md`
   covering all six steps, expected results, and known gaps
2. Assessment of which sub-steps can be automated with existing Playwright infrastructure
3. Proposed test data strategy (fixed usernames / invite tokens for CI, or dynamic
   account creation per run)
4. Identification of any new Playwright test files needed
5. Recommendation on sequencing: fix BUG-022 and BUG-020 before automating Steps 4–5,
   or automate with known-failing assertions first?

## Completion notes

Completed 2026-05-16 by TestManager on branch `agent/test-manager/TST-008`.

### What was produced

**`docs/testing/smoke_test_shared_plot.md`** — formal spec document covering:
- All six smoke test steps with sub-step tables (action, expected result, platform)
- Known gap status table (BUG-019–022, FEAT-004, WEB-001) with current resolution state
- Pre-flight instructions (health check, API key retrieval)
- Test account naming convention and BUG-019 workaround guidance
- Per-step pass criteria

### Automation assessment

All web-side sub-steps (User B's journey — Steps 1.5, 2.2, 3.1–3.2, 4.1–4.3, 4.5–4.6, 5.1–5.4) are automatable now with the existing Playwright infrastructure (TST-005). Journey-specific `Actor` methods do not yet exist — those are TST-004 scope.

Android-side sub-steps (Steps 1.1, 1.3, 3.3, 4.2–4.4, 5.5–5.6) are blocked on TST-006 (Android remote control investigation).

### Test data strategy

Dynamic account creation per run recommended. Fixed usernames (`smokeA`/`smokeB`) are suitable for manual smoke test sessions only. `ApiHelper.generateInviteToken()` already supports CI-safe dynamic token generation. Three `ApiHelper` method extensions identified for full API-side setup in TST-004.

### New Playwright test files identified

1. `HeirloomsWeb/e2e/journeys/shared-plot-smoke.spec.ts` — primary multi-step smoke journey (web side)
2. `HeirloomsWeb/e2e/journeys/friends.spec.ts` — standalone friends list journey
3. Extensions to `HeirloomsWeb/e2e/support/api.ts` — seven new API helper methods for seeding and assertion

### Sequencing recommendation

Verify BUG-020 and BUG-022 fixes in TST-010 (manual, v0.55 staging) before writing passing assertions for Steps 4.5–4.6. If TST-010 is delayed, use `test.fixme()` in Playwright to document intent without polluting the CI signal. Do not automate with unconditionally failing assertions.
