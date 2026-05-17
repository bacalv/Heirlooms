/**
 * Journey 3: Friend connection + shared plot
 *
 * Covers:
 *   - User A generates invite link → User B registers → both appear as friends
 *   - User A creates a shared plot → invites User B → B sees plot invitation
 *   - User B accepts the shared plot invite → it appears in B's garden
 *
 * Design:
 *   - Two browser contexts: alice uses page, bob uses a second page from a
 *     second BrowserContext so they have independent sessions / localStorage.
 *   - Invite token is generated via API (no UI needed for token generation).
 *   - The friend connection is established when Bob registers with Alice's
 *     invite token — the API automatically links them as friends.
 *   - Shared plot invitation uses the InviteMemberModal flow (gear → Manage
 *     members → invite by username).
 */

import { test, expect } from '@playwright/test';
import { Actor } from '../actors/Actor.js';
import { api } from '../support/api.js';

// ---------------------------------------------------------------------------
// Helper: register a user in a given page
// ---------------------------------------------------------------------------

async function registerUser(
  page: import('@playwright/test').Page,
  inviteToken: string,
  label: string
): Promise<{ username: string; passphrase: string }> {
  const suffix = Date.now().toString(36).slice(-8);
  const username = `${label}-${suffix}`;
  const passphrase = `test-passphrase-${label}`;

  await page.goto(`/join?token=${encodeURIComponent(inviteToken)}`);
  await page.getByPlaceholder('Username').fill(username);
  await page.getByPlaceholder('Display name').fill(`Test ${label}`);
  await page.getByPlaceholder('Passphrase').fill(passphrase);
  await page.getByPlaceholder('Confirm passphrase').fill(passphrase);
  await page.getByRole('button', { name: 'Create account' }).click();

  // Wait for garden
  await page.waitForURL('**/', { timeout: 30_000 }).catch(() => {});
  await page.getByRole('button', { name: 'Plant' }).waitFor({ timeout: 30_000 });

  return { username, passphrase };
}

test.describe('Journey 3 — Friends & Shared plot', () => {
  test.setTimeout(120_000);

  test('User A invites User B → both appear as friends', async ({ page, browser }) => {
    // Register Alice with a root invite token from the API
    const aliceToken = await api.generateInviteToken();
    await registerUser(page, aliceToken, 'alice');

    // Alice generates a friend invite from the Friends page
    await page.goto('/friends');
    await page.waitForLoadState('networkidle');
    await page.getByRole('button', { name: 'Generate invite link' }).click();

    // Wait for the invite URL to appear (it renders as a text span)
    await expect(page.getByText('Copy link')).toBeVisible({ timeout: 10_000 });

    // Extract the invite URL from the page
    const inviteUrl = await page.locator('span.select-all').textContent();
    expect(inviteUrl).toBeTruthy();
    const inviteToken = new URL(inviteUrl!.trim()).searchParams.get('token');
    expect(inviteToken).toBeTruthy();

    // Open a second browser context for Bob
    const bobContext = await browser.newContext();
    const bobPage = await bobContext.newPage();

    try {
      // Bob registers with Alice's invite link
      await registerUser(bobPage, inviteToken!, 'bob');

      // Alice refreshes the Friends page and should see Bob
      await page.goto('/friends');
      await page.waitForLoadState('networkidle');
      // Bob's display name or username should appear in the friends list
      await expect(page.getByText(/Test bob/i).or(page.getByText(/@bob-/i))).toBeVisible({ timeout: 15_000 });

      // Bob's Friends page should show Alice
      await bobPage.goto('/friends');
      await bobPage.waitForLoadState('networkidle');
      await expect(bobPage.getByText(/Test alice/i).or(bobPage.getByText(/@alice-/i))).toBeVisible({ timeout: 15_000 });
    } finally {
      await bobContext.close();
    }
  });

  test('User A creates shared plot → invites User B → B sees invitation', async ({ page, browser }) => {
    // Register Alice
    const aliceToken = await api.generateInviteToken();
    await registerUser(page, aliceToken, 'alice2');

    // Alice generates friend invite
    await page.goto('/friends');
    await page.waitForLoadState('networkidle');
    await page.getByRole('button', { name: 'Generate invite link' }).click();
    await expect(page.getByText('Copy link')).toBeVisible({ timeout: 10_000 });
    const inviteUrl = await page.locator('span.select-all').textContent();
    const inviteToken = new URL(inviteUrl!.trim()).searchParams.get('token');

    // Register Bob in second context
    const bobContext = await browser.newContext();
    const bobPage = await bobContext.newPage();

    try {
      await registerUser(bobPage, inviteToken!, 'bob2');

      // Alice creates a shared plot from the garden
      await page.goto('/');
      await page.waitForLoadState('networkidle');
      await page.getByRole('button', { name: '+ Shared plot' }).click();

      // Fill in the shared plot name
      const plotName = `e2e-shared-${Date.now().toString(36)}`;
      await page.getByPlaceholder('Plot name…').fill(plotName);
      await page.getByRole('button', { name: 'Create' }).click();

      // Wait for the modal to close and the plot to appear in the garden
      await expect(page.getByRole('button', { name: '+ Shared plot' })).toBeVisible({ timeout: 10_000 });
      await expect(page.getByText(plotName)).toBeVisible({ timeout: 10_000 });

      // Alice opens the gear menu on the new shared plot → Manage members
      // The shared plot row shows a gear icon; click gear next to the plot name
      const plotRow = page.locator('h2', { hasText: plotName }).locator('../..');
      await plotRow.getByRole('button', { name: 'Plot options' }).click();
      await page.getByRole('button', { name: 'Manage members' }).click();

      // InviteMemberModal opens on "Friends" tab — Bob should appear in the list
      // (Alice and Bob are friends because Bob registered with Alice's invite token)
      await page.waitForTimeout(2_000); // wait for friends to load
      // Click Bob's row in the friends list to select him
      await page.getByText(/Test bob2/i).click();
      // Click "Invite to plot"
      await page.getByRole('button', { name: 'Invite to plot' }).click();

      // Wait for the success message (FriendsTab shows "{name} has been invited.")
      await expect(page.getByText(/has been invited/i)).toBeVisible({ timeout: 10_000 });
      // Close modal via Escape
      await page.keyboard.press('Escape');

      // Bob goes to Shared Plots — should see the invitation
      await bobPage.goto('/shared');
      await bobPage.waitForLoadState('networkidle');

      // The InvitationCard renders the plot name and an "Accept" button
      await expect(bobPage.getByText(plotName)).toBeVisible({ timeout: 15_000 });
      await expect(bobPage.getByRole('button', { name: 'Accept' })).toBeVisible({ timeout: 10_000 });
    } finally {
      await bobContext.close();
    }
  });

  test('User B accepts shared plot invite → plot appears in B\'s garden', async ({ page, browser }) => {
    // Register Alice
    const aliceToken = await api.generateInviteToken();
    await registerUser(page, aliceToken, 'alice3');

    // Alice generates friend invite
    await page.goto('/friends');
    await page.waitForLoadState('networkidle');
    await page.getByRole('button', { name: 'Generate invite link' }).click();
    await expect(page.getByText('Copy link')).toBeVisible({ timeout: 10_000 });
    const inviteUrl = await page.locator('span.select-all').textContent();
    const inviteToken = new URL(inviteUrl!.trim()).searchParams.get('token');

    // Register Bob
    const bobContext = await browser.newContext();
    const bobPage = await bobContext.newPage();

    try {
      await registerUser(bobPage, inviteToken!, 'bob3');

      // Alice creates shared plot and invites Bob
      await page.goto('/');
      await page.waitForLoadState('networkidle');
      await page.getByRole('button', { name: '+ Shared plot' }).click();
      const plotName = `e2e-accept-${Date.now().toString(36)}`;
      await page.getByPlaceholder('Plot name…').fill(plotName);
      await page.getByRole('button', { name: 'Create' }).click();
      await expect(page.getByText(plotName)).toBeVisible({ timeout: 10_000 });

      const plotRow = page.locator('h2', { hasText: plotName }).locator('../..');
      await plotRow.getByRole('button', { name: 'Plot options' }).click();
      await page.getByRole('button', { name: 'Manage members' }).click();

      // Friends tab is shown by default; select Bob from the list and invite
      await page.waitForTimeout(2_000);
      await page.getByText(/Test bob3/i).click();
      await page.getByRole('button', { name: 'Invite to plot' }).click();
      await expect(page.getByText(/has been invited/i)).toBeVisible({ timeout: 10_000 });
      await page.keyboard.press('Escape');

      // Bob accepts the invite
      await bobPage.goto('/shared');
      await bobPage.waitForLoadState('networkidle');
      await expect(bobPage.getByText(plotName)).toBeVisible({ timeout: 15_000 });
      await bobPage.getByRole('button', { name: 'Accept' }).click();

      // NamePromptModal asks for a local name — fill it in
      const localName = `${plotName}-bob`;
      await bobPage.getByPlaceholder('Your name for this plot').fill(localName);
      await bobPage.getByRole('button', { name: 'Confirm' }).click();
      await bobPage.waitForTimeout(2_000);

      // Bob's garden should now contain the shared plot row
      await bobPage.goto('/');
      await bobPage.waitForLoadState('networkidle');
      // The shared plot appears with the local name Bob chose
      await expect(bobPage.getByText(localName)).toBeVisible({ timeout: 15_000 });
    } finally {
      await bobContext.close();
    }
  });
});
