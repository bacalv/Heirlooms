---
id: TST-004
title: Playwright E2E suite (actor-based, against staging)
category: Testing
priority: Medium
status: queued
depends_on: [TST-003]
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

- Use the staging API key (`k71CFcf59rdvmFqfV_nZhBd4W7DUao4jAvRvmTE4neA`) to generate invites and seed state without going through the UI
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
