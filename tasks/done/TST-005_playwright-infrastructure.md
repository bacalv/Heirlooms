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
- The staging API key is stored in Secret Manager as `heirlooms-test-api-key` (rotated 2026-05-15). In code,
  read it from `process.env.PLAYWRIGHT_STAGING_API_KEY` with that value as the
  default for local runs. Do not hardcode it in config.ts — use env with fallback.
- TypeScript config: check whether `HeirloomsWeb/tsconfig.json` covers `e2e/` or
  if a separate `e2e/tsconfig.json` is needed (Playwright recommends the latter).
- Target Node version: whatever `HeirloomsWeb/` already uses.

## Completion notes

Completed 2026-05-15 by TestManager on branch `agent/test-manager/TST-005`.

### What was set up

**Playwright installation**
- `@playwright/test@^1.60.0` added as a dev dependency in `HeirloomsWeb/`.
- `playwright.config.ts` created at `HeirloomsWeb/playwright.config.ts` with:
  - `staging` project (default) → `https://test.heirlooms.digital`
  - `local` project → `http://localhost:8080`
  - Chromium only; global timeout 30s; assertion timeout 5s; 2 retries on CI, 0 locally.

**Directory structure**
```
HeirloomsWeb/e2e/
  actors/
    Actor.ts          ← base class (register, login, logout, gardenLoaded, justArrivedCount, generateInviteToken, inviteFriend)
  support/
    api.ts            ← ApiHelper class: generateInviteToken(), healthCheck()
    config.ts         ← stagingBaseUrl, stagingApiBaseUrl, localBaseUrl, stagingApiKey (env with fallback)
    crypto.ts         ← envelope format constants, isBase64Url(), fromBase64Url(), assertWrappedKeyPlausible()
  journeys/
    .gitkeep          ← placeholder for TST-004
  smoke/
    api-health.spec.ts ← 5 smoke tests (@smoke tagged)
  tsconfig.json       ← separate TypeScript config for e2e (as Playwright recommends)
```

**Actor API**
Implemented all required methods from the task spec. Actor vocabulary matches the behavioral spec use cases:
- `register(inviteToken)` → "Invited user registers" (onboarding spec)
- `login(username, passphrase)` → "Existing user logs in" (onboarding spec)
- `logout()` → clicks "Log out" in Nav, waits for `/login` URL
- `gardenLoaded()` → waits for "Plant" button (always present on garden page)
- `justArrivedCount()` → counts items in the "Just arrived" system plot row
- `generateInviteToken()` → GET /api/auth/invites via API key (onboarding spec: "User generates a friend invite")
- `inviteFriend(other)` → generates token, navigates `other`'s page to `/join?token=...`

**npm scripts added to package.json**
- `npm run e2e` — runs all tests against staging
- `npm run e2e:local` — runs against localhost:8080
- `npm run e2e:headed` — staged, headed (for debugging)
- `npm run e2e:install` — installs Chromium browser binaries (run once before first test run)

**Smoke test results**
Run `npm run e2e -- smoke/` to execute. With Chromium installed, all 5 tests should pass:
- `health check returns ok` — verified staging API is reachable
- `generateInviteToken returns a non-empty string` — verified API key works
- `login page is reachable` — browser smoke test
- `unauthenticated root redirects to /login` — routing smoke test
- `join page renders with invite token pre-filled` — registration entry-point smoke test

API tests (2/5) were confirmed passing against staging. Browser tests require Chromium; install with `npm run e2e:install` (one-time).

### How to run

```bash
cd HeirloomsWeb/

# Install Chromium browser binary (one-time setup)
npm run e2e:install

# Run smoke tests only
npm run e2e -- smoke/

# Run all e2e tests (will find no journey tests until TST-004)
npm run e2e

# Local dev run
npm run e2e:local
```

### Known gaps for TST-004

1. **Journey-specific Actor methods not implemented** — `uploadPhoto()`, `addToPlot()`, `shareKey()`, `addFriend()`, `approveStagingItem()` etc. are explicitly reserved for TST-004 per task spec.

2. **Actor.register() uses a generated username** — TST-004 may want to expose/return the generated username so it can be reused across login calls. Current approach uses `${label}-${timestamp}` suffix. TST-004 should subclass or extend Actor if it needs username persistence.

3. **VaultUnlockPage not handled** — if a test runs login on an actor that previously registered (has device key in IDB), the app may show a vault unlock page rather than redirecting directly to the garden. The `gardenLoaded()` method handles this via the "Plant" button, but login() may need a `VaultUnlockPage` interaction step for returning users (not needed for fresh registrations per test).

4. **No CI pipeline file** — see CI integration section below.

5. **`justArrivedCount()` uses structural selectors** — the thumbnail card locator (`[class*="w-40"][class*="h-40"]`) relies on Tailwind class names. If styles change, update this selector. A `data-testid` attribute on thumbnail cards would be more robust (consider adding in TST-004).

### CI integration

For a CI pipeline (GitHub Actions, Buildkite, etc.) to run `npm run e2e`:

1. **Environment variable**: set `PLAYWRIGHT_STAGING_API_KEY` as a CI secret. If not set, the default staging key is used (acceptable for a private staging environment).

2. **Browser install step** (must run before tests):
   ```
   npx playwright install --with-deps chromium
   ```
   On Linux CI: `--with-deps` installs OS-level dependencies (libgbm, libnss3, etc.) for headless Chromium.

3. **Test command**:
   ```
   npm run e2e
   ```
   Playwright automatically runs headless on CI (no display required).

4. **Artifact upload** (recommended): save `playwright-report/` and `test-results/` as CI artifacts for trace viewer on failures.

Actual CI file wiring is a separate task (not in scope for TST-005).
