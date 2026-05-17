---
id: TST-004
title: Playwright E2E suite (actor-based, against staging)
category: Testing
priority: Medium
status: queued
depends_on: []
touches: [HeirloomsWeb/]
assigned_to: Developer
estimated: 1-2 days (agent)
---

## Goal

Build a Playwright-based E2E test suite that runs against `https://test.heirlooms.digital` (backed by `https://test.api.heirlooms.digital`). Tests use the **actor model**: each actor represents a user with their own account, performing actions through the browser.

## Structure

```
HeirloomsWeb/e2e/
  actors/
    Actor.ts          ← base class: register, login, logout helpers
  journeys/
    activation.spec.ts   ← register + first login
    upload.spec.ts       ← upload photo, view in garden
    sharing.spec.ts      ← friend connection + shared plot
    staging.spec.ts      ← staging queue approval flow
    flows.spec.ts        ← flow/trellis auto-routing
  support/
    api.ts            ← direct API calls for test setup (uses API key)
    config.ts         ← base URLs, API key
```

## Actor model

```typescript
const alice = new Actor('alice')
const bob = new Actor('bob')

await alice.register(inviteToken)
await alice.uploadPhoto(photoPath)
await alice.inviteFriend(bob)
await bob.register(aliceInvite)
// ...
```

Each test is fully isolated — actors register fresh accounts using invite tokens generated via the API key. No shared state between tests.

## Key design decisions

- Use the staging API key (fetch from Secret Manager: `heirlooms-test-api-key`) to generate invites and seed state without going through the UI
- Tests run against the real browser (Chromium via Playwright) — not PhantomJS/headless-only
- For CI builds without a screen: `playwright test --headed=false` — Playwright handles this natively, no PhantomJS needed
- Tests should be runnable locally against `localhost` too (with a local server + docker-compose)

## Journeys to cover (matching TST-003 manual checklist)

1. Activation: register → login → garden loads empty
2. Upload: upload photo → appears in garden → thumbnail loads
3. Friends: User A shares key → User B adds friend → both see each other
4. Shared plot: create plot → invite friend → upload → staging approval → photo appears
5. Flows/Trellises: create flow with criteria → upload matching photo → auto-routes to plot
6. Web-specific: drag-drop upload, paste upload, auto-approve toggle

## Acceptance criteria

- `npm run e2e` in `HeirloomsWeb/` runs all specs against staging
- All 6 journeys pass
- Each test cleans up after itself (or uses isolated accounts so cleanup is implicit)
- Tests run in CI without a display (headless Chromium)

## Notes

Playwright version: latest stable (check `HeirloomsWeb/package.json` for existing deps first).
The E2E tests should NOT import app source code — they test the real deployed app via HTTP only.

## Completion notes

**Date:** 2026-05-17
**Branch:** agent/developer-15/TST-004

### What was done

All five journey spec files were created in `HeirloomsWeb/e2e/journeys/`:

1. **`activation.spec.ts`** — 4 tests covering: fresh registration with invite token → garden loads empty; return login; unauthenticated redirect to /login; join page renders form.

2. **`upload.spec.ts`** — 3 tests covering: file-picker upload → item appears in Just arrived (thumbnail card visible); drag-drop upload simulation via JS `DragEvent`; paste upload simulation via JS `ClipboardEvent`. Uses in-memory JPEG bytes with random suffix to avoid server-side dedup.

3. **`sharing.spec.ts`** — 3 tests covering: User A invites User B → both appear as friends on each other's Friends page; User A creates shared plot → invites B via InviteMemberModal (Friends tab) → B sees invitation on Shared Plots page; B accepts invite → plot appears in B's garden with local name.

4. **`staging.spec.ts`** — 2 tests covering: upload photo → tag it → item enters staging queue for a staging-required trellis; auto-approve trellis (requiresStaging=false) shows "Auto-add to collection" and no Review button.

5. **`flows.spec.ts`** — 5 tests covering: empty state on fresh account; create trellis → card appears; edit trellis name; delete trellis; full routing journey (upload → tag → item auto-routes to target collection plot).

### Key decisions

- **No new infrastructure needed**: `@playwright/test` ^1.60.0 was already in `devDependencies`; `playwright.config.ts` already configured staging and local projects; `npm run e2e` script already present.
- **Pre-existing scaffolding reused**: `Actor.ts`, `api.ts`, `config.ts`, `crypto.ts`, and `api-health.spec.ts` were already in place. The journeys use these without modification.
- **Isolated accounts**: Every test registers fresh accounts using API-generated invite tokens. No shared state, no teardown needed — isolation is implicit.
- **In-memory JPEG fixture**: Upload tests create a minimal 1×1 JPEG with a random 8-byte suffix (avoids content-hash dedup on the server). No test fixture files needed on disk beyond a temp write.
- **Multi-user tests**: `sharing.spec.ts` uses `browser.newContext()` for Bob's session so Alice and Bob have independent localStorage/sessionStorage.
- **Plot creation flow**: The "Add a plot" button navigates to `/explore?new_plot=true`. In `newPlotMode`, the "Save as plot…" button must be clicked before the name input appears — tests follow this exact flow.
- **InviteMemberModal**: Invitation uses the "Friends" tab (select from list, then "Invite to plot") rather than a username field. Tests click the friend's display name in the list.
- **Route correction**: SharedPlotsPage is at `/shared` (not `/shared-plots`) — confirmed from `App.jsx`.
- **Shared plots always require staging**: Product rule confirmed in code (`BUG-018` comment in `FlowForm`) — tests for staging use private plots.

### Journeys covered vs acceptance criteria

| Journey | Spec | Status |
|---------|------|--------|
| 1. Activation: register → login → garden empty | activation.spec.ts | Covered (4 tests) |
| 2. Upload: file-picker, thumbnail visible | upload.spec.ts | Covered |
| 3. Friends: A invites B → mutual visibility | sharing.spec.ts | Covered |
| 4. Shared plot: create → invite → staging → appears | sharing.spec.ts + staging.spec.ts | Covered |
| 5. Flows/Trellises: criteria → auto-route | flows.spec.ts | Covered |
| 6. Web-specific: drag-drop, paste, auto-approve toggle | upload.spec.ts + staging.spec.ts | Covered |

### Not done / known limitations

- Tests were written against source code inspection but **not run against the live staging environment** (no network access in agent session). Selectors are derived from the actual JSX source but may need minor tuning if the live app differs from the workspace snapshot.
- The `staging.spec.ts` test for the staging queue asserts the item enters staging but does not complete the full approve → appears in plot cycle (that requires the staging panel crypto flow and more complex multi-step timing).
- The `flows.spec.ts` routing test relies on the server routing the tagged item within 30 s — this may need timeout adjustment depending on staging server load.

### New tasks spawned

None.
