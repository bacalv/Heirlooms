/**
 * Journey 1: Activation
 *
 * Covers:
 *   - Register a new account via invite token
 *   - First login → garden loads (empty)
 *   - Unauthenticated access redirects to /login
 *
 * Design:
 *   - Uses an API-generated invite token so no UI prerequisite is needed
 *   - Each test uses a fresh Actor (unique username via timestamp suffix)
 *   - Isolation is implicit: fresh accounts created per test, no teardown needed
 */

import { test, expect } from '@playwright/test';
import { Actor } from '../actors/Actor.js';
import { api } from '../support/api.js';

test.describe('Journey 1 — Activation', () => {
  test('register with invite token → garden loads empty', async ({ page }) => {
    const token = await api.generateInviteToken();
    const alice = new Actor('alice', page);

    await alice.register(token);

    // After registration the garden should be visible
    await expect(page).toHaveURL(/\/$/);

    // The garden shows the "Just arrived" system plot heading or the Plant button
    await expect(
      page.getByRole('heading', { name: 'Just arrived' })
        .or(page.getByRole('button', { name: 'Plant' }))
    ).toBeVisible();

    // A brand-new account has nothing in Just arrived
    const emptyMsg = page.getByText('Nothing new to tend.');
    await expect(emptyMsg).toBeVisible({ timeout: 10_000 });
  });

  test('registered user can log in again on a fresh page', async ({ page }) => {
    // Register
    const token = await api.generateInviteToken();
    const suffix = Date.now().toString(36).slice(-8);
    const username = `return-${suffix}`;
    const passphrase = 'test-passphrase-return';

    await page.goto(`/join?token=${encodeURIComponent(token)}`);
    await page.getByPlaceholder('Username').fill(username);
    await page.getByPlaceholder('Display name').fill(`Test Return`);
    await page.getByPlaceholder('Passphrase').fill(passphrase);
    await page.getByPlaceholder('Confirm passphrase').fill(passphrase);
    await page.getByRole('button', { name: 'Create account' }).click();

    // Wait for garden
    await page.waitForURL('**/', { timeout: 30_000 }).catch(() => {});
    await page.getByRole('button', { name: 'Plant' }).waitFor({ timeout: 30_000 });

    // Log out
    await page.getByRole('button', { name: 'Log out' }).first().click();
    await page.waitForURL('**/login', { timeout: 10_000 });

    // Log back in
    const actor = new Actor('return', page);
    await actor.login(username, passphrase);

    await expect(page).toHaveURL(/\/$/);
    await expect(page.getByRole('button', { name: 'Plant' })).toBeVisible();
  });

  test('unauthenticated user visiting / is redirected to /login', async ({ page }) => {
    // Fresh context — no session
    await page.goto('/');
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByPlaceholder('Username')).toBeVisible();
  });

  test('join page renders registration form when token is present', async ({ page }) => {
    const token = await api.generateInviteToken();
    await page.goto(`/join?token=${encodeURIComponent(token)}`);

    await expect(page.getByText('Create your account.')).toBeVisible();
    await expect(page.getByPlaceholder('Username')).toBeVisible();
    await expect(page.getByPlaceholder('Display name')).toBeVisible();
    await expect(page.getByPlaceholder('Passphrase')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Create account' })).toBeVisible();
  });
});
