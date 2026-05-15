import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright configuration for Heirlooms E2E tests.
 *
 * Two projects:
 *   staging  — default, runs against https://test.heirlooms.digital
 *   local    — for local dev, runs against http://localhost:8080
 *
 * Run:
 *   npm run e2e            → staging (headless)
 *   npm run e2e:local      → local (headless)
 *   npm run e2e:headed     → staging (headed, for debugging)
 */

const isCI = !!process.env.CI;

export default defineConfig({
  testDir: './e2e',

  /* Global timeout per test */
  timeout: 30_000,

  /* Assertion timeout */
  expect: {
    timeout: 5_000,
  },

  /* Retry on CI only */
  retries: isCI ? 2 : 0,

  /* Opt out of parallel runs on CI to avoid resource contention */
  workers: isCI ? 1 : undefined,

  /* Reporter */
  reporter: isCI ? 'github' : 'list',

  use: {
    /* Collect trace on first retry */
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },

  projects: [
    {
      name: 'staging',
      use: {
        ...devices['Desktop Chrome'],
        baseURL: 'https://test.heirlooms.digital',
      },
    },
    {
      name: 'local',
      use: {
        ...devices['Desktop Chrome'],
        baseURL: 'http://localhost:8080',
      },
    },
  ],
});
