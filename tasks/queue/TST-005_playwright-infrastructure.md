---
id: TST-005
title: Playwright infrastructure setup
category: Testing
priority: Medium
status: queued
depends_on: [TST-003, ARCH-002]
touches:
  - HeirloomsWeb/e2e/
  - HeirloomsWeb/package.json
  - HeirloomsWeb/playwright.config.ts
assigned_to: TestManager
estimated: 1 day (agent)
---

## Goal

Lay the Playwright infrastructure that TST-004 (E2E test writing) builds on. This
task produces zero test scenarios — it produces the scaffold, actor helpers,
staging wiring, and CI configuration that make test writing fast and consistent.

The behavioral spec diagrams from ARCH-002 (`docs/specs/`) describe what each
journey must verify. Read them before designing the actor API — the actor helper
methods should map cleanly onto the use cases described there.

## Deliverables

### 1. Playwright installation and config

- Install Playwright (`@playwright/test`) as a dev dependency in `HeirloomsWeb/`
  if not already present. Check `package.json` first.
- Create `HeirloomsWeb/playwright.config.ts` with:
  - Two projects: `staging` (default, points at `test.heirlooms.digital`) and
    `local` (points at `localhost:8080` for local dev runs)
  - Chromium only for now (add Firefox/WebKit later if needed)
  - `testDir: './e2e'`
  - Global timeout: 30s per test, 5s per assertion
  - Retries: 2 on CI, 0 locally

### 2. Directory structure

```
HeirloomsWeb/e2e/
  actors/
    Actor.ts          ← base class (see below)
  support/
    api.ts            ← direct API helper (invite generation, seeding)
    config.ts         ← base URLs, staging API key (read from env)
    crypto.ts         ← test-side crypto helpers if needed for E2EE assertions
  journeys/           ← empty directory, placeholder for TST-004
    .gitkeep
```

### 3. Actor base class (`Actor.ts`)

The actor model is the core abstraction. Each actor is an isolated user. Design
the API to match the use cases in `docs/specs/` — specifically onboarding,
uploading, and sharing flows. Minimum required methods:

```typescript
class Actor {
  constructor(label: string, page: Page)

  // Onboarding
  register(inviteToken: string): Promise<void>
  login(username: string, password: string): Promise<void>
  logout(): Promise<void>

  // State access
  gardenLoaded(): Promise<void>      // waits for garden to be visible
  justArrivedCount(): Promise<number>

  // Invite helpers (uses api.ts under the hood)
  generateInviteToken(): Promise<string>
  inviteFriend(other: Actor): Promise<void>
}
```

Do not implement journey-specific methods (upload, share, plot management) —
those belong in TST-004. Keep Actor.ts focused on identity and session management.

### 4. API support helper (`support/api.ts`)

Direct HTTP calls using the staging API key for test setup — not browser
automation. At minimum:

```typescript
generateInviteToken(): Promise<string>  // POST /api/auth/invites with API key
healthCheck(): Promise<boolean>         // GET /health
```

### 5. npm script

Add to `HeirloomsWeb/package.json`:
```json
"e2e": "playwright test --project=staging",
"e2e:local": "playwright test --project=local",
"e2e:headed": "playwright test --project=staging --headed"
```

### 6. CI note (do not implement — document only)

Add a `## CI integration` section to the Completion notes describing what a CI
pipeline would need: `PLAYWRIGHT_STAGING_API_KEY` env var, `npx playwright install
--with-deps chromium`, and the `npm run e2e` command. Actual CI wiring is a
separate task.

## Acceptance criteria

- `npm run e2e` in `HeirloomsWeb/` starts Playwright against staging (may find
  no tests yet — that is expected; the run should not error on missing test files).
- `playwright.config.ts` has both `staging` and `local` projects.
- `Actor.ts` compiles with no TypeScript errors.
- `support/api.ts` `generateInviteToken()` successfully calls the staging API
  and returns a token string (write a single smoke test to verify, tag it
  `@smoke` so it can be run standalone).
- Directory structure matches the spec above.
- `docs/specs/` has been read — actor method names align with the use case
  vocabulary used in the spec diagrams.

## Notes

- Check `HeirloomsWeb/package.json` for existing Playwright or testing deps before
  installing — avoid duplicate installs or version conflicts.
- The staging API key must be fetched from Secret Manager (`heirlooms-test-api-key`).
  In code, read it from `process.env.PLAYWRIGHT_STAGING_API_KEY` — do not hardcode
  a fallback value in config.ts. Set the env var from Secret Manager for local runs.
- TypeScript config: check whether `HeirloomsWeb/tsconfig.json` covers `e2e/` or
  if a separate `e2e/tsconfig.json` is needed (Playwright recommends the latter).
- Target Node version: whatever `HeirloomsWeb/` already uses.

## Completion notes

<!-- Agent appends here and moves file to tasks/done/ -->
