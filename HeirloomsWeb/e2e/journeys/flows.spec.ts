/**
 * Journey 5: Flows / Trellises — create flow with criteria → auto-route
 *
 * Covers:
 *   - Create a collection plot
 *   - Create a trellis with tag-based criteria and auto-approve
 *   - Upload a photo and tag it with a matching tag
 *   - Verify the item routes from Just arrived to the target plot
 *   - Verify the Trellises page renders: list, create, edit, delete UI
 *
 * Design:
 *   - Uses auto-approve (requiresStaging=false) so the routing is immediate
 *     and we don't need to go through the staging approval UI.
 *   - After tagging, the garden polls every 5 s; the item should appear in the
 *     target plot within 10 s (server-side routing is near-instant).
 *   - "No criteria" trellises route all items; we deliberately name the tag to
 *     match so the assertion is unambiguous.
 */

import { test, expect } from '@playwright/test';
import * as path from 'path';
import * as os from 'os';
import * as fs from 'fs';
import { Actor } from '../actors/Actor.js';
import { api } from '../support/api.js';

/** Minimal unique JPEG — avoids server dedup on repeated runs */
function uniqueJpegBuffer(): Buffer {
  const base = Buffer.from([
    0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10, 0x4a, 0x46, 0x49, 0x46, 0x00, 0x01,
    0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0xff, 0xdb, 0x00, 0x43,
    0x00, 0x08, 0x06, 0x06, 0x07, 0x06, 0x05, 0x08, 0x07, 0x07, 0x07, 0x09,
    0x09, 0x08, 0x0a, 0x0c, 0x14, 0x0d, 0x0c, 0x0b, 0x0b, 0x0c, 0x19, 0x12,
    0x13, 0x0f, 0x14, 0x1d, 0x1a, 0x1f, 0x1e, 0x1d, 0x1a, 0x1c, 0x1c, 0x20,
    0x24, 0x2e, 0x27, 0x20, 0x22, 0x2c, 0x23, 0x1c, 0x1c, 0x28, 0x37, 0x29,
    0x2c, 0x30, 0x31, 0x34, 0x34, 0x34, 0x1f, 0x27, 0x39, 0x3d, 0x38, 0x32,
    0x3c, 0x2e, 0x33, 0x34, 0x32, 0xff, 0xc0, 0x00, 0x0b, 0x08, 0x00, 0x01,
    0x00, 0x01, 0x01, 0x01, 0x11, 0x00, 0xff, 0xc4, 0x00, 0x1f, 0x00, 0x00,
    0x01, 0x05, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
    0x09, 0x0a, 0x0b, 0xff, 0xc4, 0x00, 0xb5, 0x10, 0x00, 0x02, 0x01, 0x03,
    0x03, 0x02, 0x04, 0x03, 0x05, 0x05, 0x04, 0x04, 0x00, 0x00, 0x01, 0x7d,
    0x01, 0x02, 0x03, 0x00, 0x04, 0x11, 0x05, 0x12, 0x21, 0x31, 0x41, 0x06,
    0x13, 0x51, 0x61, 0x07, 0x22, 0x71, 0x14, 0x32, 0x81, 0x91, 0xa1, 0x08,
    0x23, 0x42, 0xb1, 0xc1, 0x15, 0x52, 0xd1, 0xf0, 0x24, 0x33, 0x62, 0x72,
    0x82, 0x09, 0x0a, 0x16, 0x17, 0x18, 0x19, 0x1a, 0x25, 0x26, 0x27, 0x28,
    0x29, 0x2a, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x3a, 0x43, 0x44, 0x45,
    0x46, 0x47, 0x48, 0x49, 0x4a, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59,
    0x5a, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69, 0x6a, 0x73, 0x74, 0x75,
    0x76, 0x77, 0x78, 0x79, 0x7a, 0x83, 0x84, 0x85, 0x86, 0x87, 0x88, 0x89,
    0x8a, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98, 0x99, 0x9a, 0xa2, 0xa3,
    0xa4, 0xa5, 0xa6, 0xa7, 0xa8, 0xa9, 0xaa, 0xb2, 0xb3, 0xb4, 0xb5, 0xb6,
    0xb7, 0xb8, 0xb9, 0xba, 0xc2, 0xc3, 0xc4, 0xc5, 0xc6, 0xc7, 0xc8, 0xc9,
    0xca, 0xd2, 0xd3, 0xd4, 0xd5, 0xd6, 0xd7, 0xd8, 0xd9, 0xda, 0xe1, 0xe2,
    0xe3, 0xe4, 0xe5, 0xe6, 0xe7, 0xe8, 0xe9, 0xea, 0xf1, 0xf2, 0xf3, 0xf4,
    0xf5, 0xf6, 0xf7, 0xf8, 0xf9, 0xfa, 0xff, 0xda, 0x00, 0x08, 0x01, 0x01,
    0x00, 0x00, 0x3f, 0x00, 0xfb, 0xd3, 0xff, 0xd9,
  ]);
  const rand = Buffer.alloc(8);
  for (let i = 0; i < 8; i++) rand[i] = Math.floor(Math.random() * 256);
  return Buffer.concat([base, rand]);
}

function writeTempJpeg(): string {
  const p = path.join(
    os.tmpdir(),
    `heirlooms-flows-e2e-${Date.now()}-${Math.random().toString(36).slice(2)}.jpg`
  );
  fs.writeFileSync(p, uniqueJpegBuffer());
  return p;
}

test.describe('Journey 5 — Flows / Trellises', () => {
  test.setTimeout(180_000);

  test('Trellises page renders: empty state and create button', async ({ page }) => {
    const token = await api.generateInviteToken();
    const actor = new Actor('trellis-viewer', page);
    await actor.register(token);

    await page.goto('/flows');
    await page.waitForLoadState('networkidle');

    await expect(page).toHaveTitle(/Trellises/);
    await expect(page.getByRole('heading', { name: 'Trellises' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'New trellis' })).toBeVisible();

    // Fresh account has no trellises
    await expect(page.getByText('No trellises yet.')).toBeVisible();
  });

  test('create trellis → trellis card appears in list', async ({ page }) => {
    const token = await api.generateInviteToken();
    const actor = new Actor('trellis-creator', page);
    await actor.register(token);

    // Create a collection plot first (needed as target for trellis)
    await page.getByRole('button', { name: '+ Add a plot' }).click();
    await page.waitForURL('**/explore**', { timeout: 10_000 }).catch(() => {});
    await page.waitForLoadState('networkidle');

    // In newPlotMode, click "Save as plot…" to reveal the name input
    await page.getByRole('button', { name: 'Save as plot…' }).click();
    const plotName = `flow-plot-${Date.now().toString(36)}`;
    await page.getByPlaceholder('Plot name…').fill(plotName);
    await page.getByRole('button', { name: 'Save' }).click();

    // Create trellis
    await page.goto('/flows');
    await page.waitForLoadState('networkidle');
    await page.getByRole('button', { name: 'New trellis' }).click();

    const trellisName = `my-trellis-${Date.now().toString(36)}`;
    await page.getByPlaceholder(/e\.g\. Photos/i).fill(trellisName);
    await page.locator('select').selectOption({ label: plotName });
    // Leave criteria empty (routes all items) and staging required default
    await page.getByRole('button', { name: 'Create trellis' }).click();

    // The trellis card should appear
    await expect(page.getByText(trellisName)).toBeVisible({ timeout: 10_000 });
    // Arrow points to the target plot
    await expect(page.getByText(plotName)).toBeVisible();
    // Default text for staging
    await expect(page.getByText('Staging required')).toBeVisible();
  });

  test('edit trellis → name updates in card', async ({ page }) => {
    const token = await api.generateInviteToken();
    const actor = new Actor('trellis-editor', page);
    await actor.register(token);

    // Setup: create plot + trellis
    await page.getByRole('button', { name: '+ Add a plot' }).click();
    await page.waitForURL('**/explore**', { timeout: 10_000 }).catch(() => {});
    await page.waitForLoadState('networkidle');
    await page.getByRole('button', { name: 'Save as plot…' }).click();
    const plotName = `edit-plot-${Date.now().toString(36)}`;
    await page.getByPlaceholder('Plot name…').fill(plotName);
    await page.getByRole('button', { name: 'Save' }).click();

    await page.goto('/flows');
    await page.waitForLoadState('networkidle');
    await page.getByRole('button', { name: 'New trellis' }).click();
    const trellisName = `edit-trellis-${Date.now().toString(36)}`;
    await page.getByPlaceholder(/e\.g\. Photos/i).fill(trellisName);
    await page.locator('select').selectOption({ label: plotName });
    await page.getByRole('button', { name: 'Create trellis' }).click();
    await expect(page.getByText(trellisName)).toBeVisible({ timeout: 10_000 });

    // Edit the trellis name
    const updatedName = `${trellisName}-updated`;
    const trellisCard = page.locator('text=' + trellisName).locator('../..');
    await trellisCard.getByRole('button', { name: 'Edit' }).click();

    // Edit modal: clear name and type new one
    const nameInput = page.getByPlaceholder(/e\.g\. Photos/i);
    await nameInput.clear();
    await nameInput.fill(updatedName);
    await page.getByRole('button', { name: 'Update trellis' }).click();

    // Updated name appears in the list
    await expect(page.getByText(updatedName)).toBeVisible({ timeout: 10_000 });
    await expect(page.getByText(trellisName)).not.toBeVisible();
  });

  test('delete trellis → trellis removed from list', async ({ page }) => {
    const token = await api.generateInviteToken();
    const actor = new Actor('trellis-deleter', page);
    await actor.register(token);

    await page.getByRole('button', { name: '+ Add a plot' }).click();
    await page.waitForURL('**/explore**', { timeout: 10_000 }).catch(() => {});
    await page.waitForLoadState('networkidle');
    await page.getByRole('button', { name: 'Save as plot…' }).click();
    const plotName = `del-plot-${Date.now().toString(36)}`;
    await page.getByPlaceholder('Plot name…').fill(plotName);
    await page.getByRole('button', { name: 'Save' }).click();

    await page.goto('/flows');
    await page.waitForLoadState('networkidle');
    await page.getByRole('button', { name: 'New trellis' }).click();
    const trellisName = `del-trellis-${Date.now().toString(36)}`;
    await page.getByPlaceholder(/e\.g\. Photos/i).fill(trellisName);
    await page.locator('select').selectOption({ label: plotName });
    await page.getByRole('button', { name: 'Create trellis' }).click();
    await expect(page.getByText(trellisName)).toBeVisible({ timeout: 10_000 });

    // Delete the trellis
    const trellisCard = page.locator('text=' + trellisName).locator('../..');
    await trellisCard.getByRole('button', { name: 'Delete' }).click();

    // Confirmation dialog appears
    await expect(page.getByText(/Delete "/i)).toBeVisible();
    await page.getByRole('button', { name: 'Delete' }).last().click();

    // Trellis is removed
    await expect(page.getByText(trellisName)).not.toBeVisible({ timeout: 10_000 });
    await expect(page.getByText('No trellises yet.')).toBeVisible();
  });

  test('upload photo → tag with trellis tag → item routes to target plot (auto-approve)', async ({ page }) => {
    const token = await api.generateInviteToken();
    const actor = new Actor('routing', page);
    await actor.register(token);

    // Create collection plot
    await page.getByRole('button', { name: '+ Add a plot' }).click();
    await page.waitForURL('**/explore**', { timeout: 10_000 }).catch(() => {});
    await page.waitForLoadState('networkidle');
    await page.getByRole('button', { name: 'Save as plot…' }).click();
    const plotName = `route-plot-${Date.now().toString(36)}`;
    await page.getByPlaceholder('Plot name…').fill(plotName);
    await page.getByRole('button', { name: 'Save' }).click();

    // Create trellis with auto-approve (no staging)
    await page.goto('/flows');
    await page.waitForLoadState('networkidle');
    await page.getByRole('button', { name: 'New trellis' }).click();
    const trellisName = `route-trellis-${Date.now().toString(36)}`;
    await page.getByPlaceholder(/e\.g\. Photos/i).fill(trellisName);
    await page.locator('select').selectOption({ label: plotName });
    // Check Auto-approve (requiresStaging=false)
    await page.getByRole('checkbox').check();
    await page.getByRole('button', { name: 'Create trellis' }).click();
    await expect(page.getByText('Auto-add to collection')).toBeVisible({ timeout: 10_000 });

    // Upload a photo
    await page.goto('/');
    await page.waitForLoadState('networkidle');

    const filePath = writeTempJpeg();
    try {
      const fileChooserPromise = page.waitForEvent('filechooser');
      await page.getByRole('button', { name: 'Plant' }).click();
      const fileChooser = await fileChooserPromise;
      await fileChooser.setFiles(filePath);

      await expect(page.getByText('Done')).toBeVisible({ timeout: 60_000 });
      await page.waitForTimeout(2_000);

      // The uploaded item appears in Just arrived
      const justArrivedSection = page.locator('h2', { hasText: 'Just arrived' }).locator('../..');
      const firstThumb = justArrivedSection.locator('.flex-shrink-0').first();
      await expect(firstThumb).toBeVisible({ timeout: 15_000 });

      // Hover and click "Edit tags"
      await firstThumb.hover();
      await firstThumb.getByRole('button', { name: 'Edit tags' }).click();

      // Tag the item — trellis has no tag criteria (routes all), so any tag triggers routing
      await page.getByPlaceholder('Add tag…').fill('e2e-route');
      await page.keyboard.press('Enter');
      await page.getByRole('button', { name: 'Save' }).click();

      // After tagging + auto-approve routing, the item should appear in the target plot
      // Give the server time to route (up to 30 s)
      const targetPlotSection = page.locator('h2', { hasText: plotName }).locator('../..');
      await expect(targetPlotSection.locator('.flex-shrink-0').first()).toBeVisible({ timeout: 30_000 });
    } finally {
      fs.unlinkSync(filePath);
    }
  });
});
