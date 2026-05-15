/**
 * E2E test configuration — base URLs and API key.
 *
 * The staging API key is read from PLAYWRIGHT_STAGING_API_KEY with the
 * staging default for local runs. Do NOT hardcode the key in tests — always
 * reference `stagingApiKey` from this module.
 *
 * In CI, set PLAYWRIGHT_STAGING_API_KEY as a secret env var.
 */

export const stagingBaseUrl = 'https://test.heirlooms.digital';
export const stagingApiBaseUrl = 'https://test.api.heirlooms.digital';
export const localBaseUrl = 'http://localhost:8080';

/**
 * API key used for direct server-side test setup (invite generation, seeding).
 * Never used in browser automation — only in api.ts helper calls.
 */
export const stagingApiKey: string =
  process.env.PLAYWRIGHT_STAGING_API_KEY ??
  'k71CFcf59rdvmFqfV_nZhBd4W7DUao4jAvRvmTE4neA';

/**
 * Derive the API base URL from the browser baseURL project setting.
 * Falls back to staging API if not determinable.
 */
export function apiBaseUrlFromBrowserBase(browserBase: string | undefined): string {
  if (browserBase?.includes('localhost')) {
    // Assume local API runs on 8081 or adjust to your local setup
    return 'http://localhost:8081';
  }
  return stagingApiBaseUrl;
}
