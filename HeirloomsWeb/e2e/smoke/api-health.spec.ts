/**
 * @smoke
 *
 * Smoke tests for the Playwright infrastructure itself.
 *
 * These tests verify the following before any journey tests run:
 *   1. The staging API is reachable (health check)
 *   2. generateInviteToken() returns a valid token string
 *   3. The staging web app is reachable and renders the login page
 *
 * Tag: @smoke — run standalone with:
 *   npx playwright test smoke/ --project=staging
 *
 * These tests do NOT create real user accounts; they only call read-only or
 * token-generation endpoints.
 */

import { test, expect } from '@playwright/test';
import { api } from '../support/api.js';

test.describe('@smoke Staging API', () => {
  test('health check returns ok', async () => {
    const healthy = await api.healthCheck();
    expect(healthy).toBe(true);
  });

  test('generateInviteToken returns a non-empty string', async () => {
    const token = await api.generateInviteToken();
    expect(typeof token).toBe('string');
    expect(token.length).toBeGreaterThan(8);
  });
});

test.describe('@smoke Staging web app', () => {
  test('login page is reachable', async ({ page }) => {
    await page.goto('/login');
    // The login page renders the Heirlooms wordmark and a username field
    await expect(page.getByText('Heirlooms')).toBeVisible();
    await expect(page.getByPlaceholder('Username')).toBeVisible();
    await expect(page.getByPlaceholder('Passphrase')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible();
  });

  test('unauthenticated root redirects to /login', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveURL(/\/login$/);
  });

  test('join page renders with invite token pre-filled', async ({ page }) => {
    const token = await api.generateInviteToken();
    await page.goto(`/join?token=${encodeURIComponent(token)}`);

    // The join page should show the registration form
    await expect(page.getByText('Create your account.')).toBeVisible();
    await expect(page.getByPlaceholder('Username')).toBeVisible();
    await expect(page.getByPlaceholder('Display name')).toBeVisible();
    await expect(page.getByPlaceholder('Passphrase')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Create account' })).toBeVisible();
  });
});
