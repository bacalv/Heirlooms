/**
 * Direct API helper for test setup — uses the staging API key for
 * server-side operations (invite generation, seeding, health checks).
 *
 * These calls use the Fetch API (Node 18+ / Playwright's built-in fetch)
 * and do NOT go through the browser. They exist to set up test preconditions
 * without requiring UI automation for every prerequisite step.
 *
 * Based on the onboarding spec (docs/specs/onboarding.md):
 *   - GET /invites  → returns {token, expires_at}
 *   - GET /health   → health check
 */

import { stagingApiKey, stagingApiBaseUrl } from './config.js';

export class ApiHelper {
  private readonly apiBase: string;
  private readonly apiKey: string;

  constructor(apiBase: string = stagingApiBaseUrl, apiKey: string = stagingApiKey) {
    this.apiBase = apiBase;
    this.apiKey = apiKey;
  }

  private headers(): Record<string, string> {
    return {
      'X-Api-Key': this.apiKey,
      'Content-Type': 'application/json',
    };
  }

  /**
   * Generate a single-use invite token via the staging API.
   * See onboarding spec: "User generates a friend invite" — GET /invites
   *
   * @returns the raw token string, valid for 48 hours
   * @throws if the API returns a non-2xx response
   */
  async generateInviteToken(): Promise<string> {
    const url = `${this.apiBase}/api/auth/invites`;
    const response = await fetch(url, {
      method: 'GET',
      headers: this.headers(),
    });

    if (!response.ok) {
      const body = await response.text();
      throw new Error(
        `generateInviteToken: API returned ${response.status} from ${url}: ${body}`
      );
    }

    const data = (await response.json()) as { token: string; expires_at: string };
    if (!data.token) {
      throw new Error(`generateInviteToken: response missing token field: ${JSON.stringify(data)}`);
    }
    return data.token;
  }

  /**
   * Health check — verifies the staging API is reachable before running tests.
   *
   * @returns true if the server is healthy, false otherwise
   */
  async healthCheck(): Promise<boolean> {
    try {
      const response = await fetch(`${this.apiBase}/health`, {
        method: 'GET',
        headers: { 'X-Api-Key': this.apiKey },
      });
      return response.ok;
    } catch {
      return false;
    }
  }
}

/** Shared default instance for convenience in tests */
export const api = new ApiHelper();
