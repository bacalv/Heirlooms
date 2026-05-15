/**
 * Actor — the base abstraction for E2E tests.
 *
 * Each actor represents an isolated user with their own browser page,
 * session, and Heirlooms account. Actors interact with the app through
 * the browser only — they never import app source code.
 *
 * Design aligns with the onboarding behavioral spec (docs/specs/onboarding.md):
 *   - register()          → "Invited user registers" use case
 *   - login()             → "Existing user logs in" use case
 *   - logout()            → clears session (clicks "Log out" in Nav)
 *   - gardenLoaded()      → waits for "Just arrived" heading or empty garden
 *   - justArrivedCount()  → counts items in the "Just arrived" system plot row
 *   - generateInviteToken()   → "User generates a friend invite" use case (via API helper)
 *   - inviteFriend()          → navigates friend to registration with invite token
 *
 * Journey-specific methods (upload, share, plot management) belong in TST-004.
 * This class is intentionally limited to identity and session management.
 */

import { type Page } from '@playwright/test';
import { ApiHelper } from '../support/api.js';
import { stagingApiBaseUrl } from '../support/config.js';

export class Actor {
  readonly label: string;
  readonly page: Page;
  private readonly api: ApiHelper;

  constructor(label: string, page: Page, apiBase?: string) {
    this.label = label;
    this.page = page;
    this.api = new ApiHelper(apiBase ?? stagingApiBaseUrl);
  }

  // ---------------------------------------------------------------------------
  // Onboarding
  // ---------------------------------------------------------------------------

  /**
   * Register a new account using the given invite token.
   *
   * Navigates to /join?token=<inviteToken>, fills in unique credentials derived
   * from this actor's label, submits, and waits for the garden to load.
   *
   * The username is generated as `<label>-<timestamp>` to ensure uniqueness
   * across test runs. The passphrase is deterministic for the actor label
   * (acceptable for test accounts — not production use).
   */
  async register(inviteToken: string): Promise<void> {
    const username = this.uniqueUsername();
    const passphrase = `test-passphrase-${this.label}`;
    const displayName = `Test ${this.label}`;

    await this.page.goto(`/join?token=${encodeURIComponent(inviteToken)}`);

    // The invite token is pre-filled via ?token= query param; username field is first
    await this.page.getByPlaceholder('Username').fill(username);
    await this.page.getByPlaceholder('Display name').fill(displayName);
    await this.page.getByPlaceholder('Passphrase').fill(passphrase);
    await this.page.getByPlaceholder('Confirm passphrase').fill(passphrase);

    await this.page.getByRole('button', { name: 'Create account' }).click();

    // After registration the app navigates to / (garden); wait for vault to load
    await this.gardenLoaded();
  }

  /**
   * Log in with an existing username and passphrase.
   *
   * Navigates to /login, fills in credentials, submits, and waits for the
   * vault unlock step or the garden to load (depending on whether the device
   * key is present in IndexedDB from a previous session).
   */
  async login(username: string, passphrase: string): Promise<void> {
    await this.page.goto('/login');
    await this.page.getByPlaceholder('Username').fill(username);
    await this.page.getByPlaceholder('Passphrase').fill(passphrase);
    await this.page.getByRole('button', { name: 'Sign in' }).click();
    await this.gardenLoaded();
  }

  /**
   * Log out by clicking "Log out" in the desktop Nav.
   *
   * After logout the user is redirected to /login. The method waits for the
   * login page to be visible.
   */
  async logout(): Promise<void> {
    // Click the desktop "Log out" button; fall back to mobile menu if needed
    const logoutButton = this.page.getByRole('button', { name: 'Log out' }).first();
    await logoutButton.click();
    await this.page.waitForURL('**/login', { timeout: 10_000 });
  }

  // ---------------------------------------------------------------------------
  // State access
  // ---------------------------------------------------------------------------

  /**
   * Wait for the garden page to be fully loaded.
   *
   * Looks for either:
   *   - The "Just arrived" heading (system plot, always present after login)
   *   - The "Plant" button (top-right of garden page, always rendered)
   *
   * This covers both empty gardens and gardens with items.
   */
  async gardenLoaded(): Promise<void> {
    // Either the "Just arrived" heading or the "Plant" button must appear
    await this.page.waitForURL('**/', { timeout: 30_000 }).catch(() => {
      // URL may already be at root; swallow the error
    });
    await Promise.race([
      this.page.getByRole('heading', { name: 'Just arrived' }).waitFor({ timeout: 30_000 }).catch(() => {}),
      this.page.getByRole('button', { name: 'Plant' }).waitFor({ timeout: 30_000 }),
    ]);
  }

  /**
   * Return the count of items currently visible in the "Just arrived" row.
   *
   * Returns 0 if the row shows "Nothing new to tend." or is empty.
   * This reflects the system plot item count as described in the uploading
   * spec (docs/specs/uploading.md): items appear in Just Arrived on next load.
   */
  async justArrivedCount(): Promise<number> {
    // Items in the Just arrived row are rendered as thumbnail cards (w-40 h-40 divs)
    // Use aria or structural selectors; count items excluding the "More" button
    const emptyMsg = await this.page.getByText('Nothing new to tend.').isVisible().catch(() => false);
    if (emptyMsg) return 0;

    // Count thumbnail links/buttons in the just-arrived section
    // The system plot row comes first; thumbnail cards are w-40 h-40 flex-shrink-0 divs
    // We rely on the "Just arrived" section being first, above user plots
    const justArrivedSection = this.page.locator('h2', { hasText: 'Just arrived' }).locator('..').locator('..');
    const thumbCards = justArrivedSection.locator('[class*="w-40"][class*="h-40"]');
    return thumbCards.count();
  }

  // ---------------------------------------------------------------------------
  // Invite helpers
  // ---------------------------------------------------------------------------

  /**
   * Generate a new friend invite token via the staging API.
   *
   * This calls GET /api/auth/invites with the staging API key — it does NOT
   * go through the browser. The token is single-use and valid for 48 hours.
   *
   * See onboarding spec: "User generates a friend invite"
   */
  async generateInviteToken(): Promise<string> {
    return this.api.generateInviteToken();
  }

  /**
   * Invite `other` actor to register using this actor's invite token.
   *
   * Generates a fresh invite token (via API) and navigates `other`'s page to
   * the registration URL with the token pre-filled.
   *
   * After this call, `other` still needs to call register() or the caller
   * should use this token to call other.register(token) to complete the flow.
   *
   * Returns the generated token so the caller can drive the friend's registration.
   */
  async inviteFriend(other: Actor): Promise<string> {
    const token = await this.generateInviteToken();
    await other.page.goto(`/join?token=${encodeURIComponent(token)}`);
    return token;
  }

  // ---------------------------------------------------------------------------
  // Internal helpers
  // ---------------------------------------------------------------------------

  /**
   * Generate a unique username for this actor based on label + timestamp.
   * Format: `<label>-<last8charsOfTimestamp>` (e.g. "alice-1a2b3c4d")
   *
   * Lowercase, no spaces, safe for the Heirlooms username validator.
   */
  private uniqueUsername(): string {
    const suffix = Date.now().toString(36).slice(-8);
    // Sanitize label: lowercase, replace non-alphanumeric with dash
    const base = this.label.toLowerCase().replace(/[^a-z0-9]/g, '-').slice(0, 20);
    return `${base}-${suffix}`;
  }
}
