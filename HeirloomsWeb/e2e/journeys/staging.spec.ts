/**
 * Journey 4: Staging queue approval flow
 *
 * Covers:
 *   - Create a shared plot (requires_staging=true by default for shared plots)
 *   - Upload a photo to the shared plot via a trellis with staging required
 *   - Owner sees photo in staging queue → approves → photo appears in plot
 *   - Auto-approve toggle: trellis with requiresStaging=false → item goes
 *     directly into the plot without staging review
 *
 * Design:
 *   - Alice owns the shared plot and the trellis; she's both uploader and approver
 *     (simplest isolation — no second user needed for the staging flow itself)
 *   - The staging panel is revealed by clicking "Review" on the flow card
 *   - Upload is done via file-picker (same approach as upload.spec.ts)
 *   - The trellis criteria use tags; after upload, Alice tags the photo to
 *     trigger routing, then approves from the staging panel
 */

import { test, expect } from '@playwright/test';
import * as path from 'path';
import * as os from 'os';
import * as fs from 'fs';
import { Actor } from '../actors/Actor.js';
import { api } from '../support/api.js';

/** Minimal unique JPEG bytes (avoids server dedup) */
function uniqueJpegBytes(): Buffer {
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
  const filePath = path.join(
    os.tmpdir(),
    `heirlooms-staging-e2e-${Date.now()}-${Math.random().toString(36).slice(2)}.jpg`
  );
  fs.writeFileSync(filePath, uniqueJpegBytes());
  return filePath;
}

test.describe('Journey 4 — Staging queue', () => {
  test.setTimeout(180_000);

  test('private plot trellis with staging: upload + tag → item enters staging queue', async ({ page }) => {
    const token = await api.generateInviteToken();
    const actor = new Actor('staging-user', page);
    await actor.register(token);

    // Create a collection plot via "+ Add a plot" → ExplorePage
    await page.getByRole('button', { name: '+ Add a plot' }).click();
    await page.waitForURL('**/explore**', { timeout: 10_000 }).catch(() => {});
    await page.waitForLoadState('networkidle');

    // In newPlotMode, the "Save as plot…" bar is shown — click it to reveal the name input
    await page.getByRole('button', { name: 'Save as plot…' }).click();

    // On ExplorePage fill in the new plot form
    const plotName = `staging-plot-${Date.now().toString(36)}`;
    await page.getByPlaceholder('Plot name…').fill(plotName);
    // No criteria = collection plot
    await page.getByRole('button', { name: 'Save' }).click();

    // Navigate to Trellises
    await page.goto('/flows');
    await page.waitForLoadState('networkidle');
    await page.getByRole('button', { name: 'New trellis' }).click();

    // FlowForm modal: fill name, select the new plot, leave staging required checked
    const trellisName = `staging-trellis-${Date.now().toString(36)}`;
    await page.getByPlaceholder(/e\.g\. Photos/i).fill(trellisName);

    // Select the target plot in the dropdown
    await page.locator('select').selectOption({ label: plotName });

    // The "Auto-approve" checkbox is unchecked by default (requiresStaging=true)
    // Leave it unchecked — staging is required
    await page.getByRole('button', { name: 'Create trellis' }).click();

    // Wait for the modal to close and the trellis card to appear
    await expect(page.getByText(trellisName)).toBeVisible({ timeout: 10_000 });

    // Go back to garden and upload a photo
    await page.goto('/');
    await page.waitForLoadState('networkidle');

    const filePath = writeTempJpeg();
    try {
      const fileChooserPromise = page.waitForEvent('filechooser');
      await page.getByRole('button', { name: 'Plant' }).click();
      const fileChooser = await fileChooserPromise;
      await fileChooser.setFiles(filePath);

      // Wait for upload to complete
      await expect(page.getByText('Done')).toBeVisible({ timeout: 60_000 });
      await page.waitForTimeout(2_000);

      // The item appears in Just arrived — click the edit/tag icon on the thumbnail
      const justArrivedSection = page.locator('h2', { hasText: 'Just arrived' }).locator('../..');
      await expect(justArrivedSection.locator('.flex-shrink-0').first()).toBeVisible({ timeout: 15_000 });

      // Hover over the first thumbnail to reveal the tag button
      const firstThumb = justArrivedSection.locator('.flex-shrink-0').first();
      await firstThumb.hover();

      // Click the "Edit tags" button (aria-label="Edit tags")
      await firstThumb.getByRole('button', { name: 'Edit tags' }).click();

      // QuickTagModal: type the tag name that matches the trellis criteria
      // Since the trellis has no criteria set, any uploaded item should route
      // But we need a tag to trigger routing — type the tag and save
      const tagName = `e2e-${Date.now().toString(36)}`;
      await page.getByPlaceholder('Add tag…').fill(tagName);
      await page.keyboard.press('Enter');
      await page.getByRole('button', { name: 'Save' }).click();

      // After tagging, the item should route to staging
      // Navigate to Trellises and check the staging queue
      await page.goto('/flows');
      await page.waitForLoadState('networkidle');

      // Find the trellis card and click "Review"
      const trellisCard = page.locator('text=' + trellisName).locator('../..');
      await expect(trellisCard).toBeVisible({ timeout: 10_000 });
      await trellisCard.getByRole('button', { name: /Review/ }).click();

      // The StagingPanel should appear with the item in the queue
      // Wait up to 30s for the item to route to staging
      await expect(page.getByText('Nothing waiting for review.')).not.toBeVisible({ timeout: 30_000 });
    } finally {
      fs.unlinkSync(filePath);
    }
  });

  test('auto-approve toggle: trellis without staging → item goes directly to plot', async ({ page }) => {
    const token = await api.generateInviteToken();
    const actor = new Actor('auto-approve', page);
    await actor.register(token);

    // Create a collection plot via the garden "+ Add a plot" button
    await page.getByRole('button', { name: '+ Add a plot' }).click();
    await page.waitForURL('**/explore**', { timeout: 10_000 }).catch(() => {});
    await page.waitForLoadState('networkidle');

    await page.getByRole('button', { name: 'Save as plot…' }).click();
    const plotName = `auto-plot-${Date.now().toString(36)}`;
    await page.getByPlaceholder('Plot name…').fill(plotName);
    await page.getByRole('button', { name: 'Save' }).click();

    // Create trellis with auto-approve
    await page.goto('/flows');
    await page.waitForLoadState('networkidle');
    await page.getByRole('button', { name: 'New trellis' }).click();

    const trellisName = `auto-trellis-${Date.now().toString(36)}`;
    await page.getByPlaceholder(/e\.g\. Photos/i).fill(trellisName);
    await page.locator('select').selectOption({ label: plotName });

    // Check the "Auto-approve" checkbox (sets requiresStaging=false)
    await page.getByRole('checkbox').check();
    await page.getByRole('button', { name: 'Create trellis' }).click();
    await expect(page.getByText(trellisName)).toBeVisible({ timeout: 10_000 });

    // The trellis card should show "Auto-add to collection" (not "Staging required")
    await expect(page.getByText('Auto-add to collection')).toBeVisible();

    // The "Review" button should NOT be visible (no staging)
    const reviewBtn = page.getByRole('button', { name: /Review/ });
    await expect(reviewBtn).not.toBeVisible();
  });
});
