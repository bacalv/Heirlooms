/**
 * Journey 2: Upload
 *
 * Covers:
 *   - File-picker upload: click "Plant", select file → item appears in Just Arrived
 *   - Thumbnail is rendered (UploadThumb card is visible in the row)
 *   - Drag-drop upload: drop a file onto the garden → same result
 *   - Paste upload: paste an image from clipboard → item appears
 *
 * Design:
 *   - A small JPEG (< 10 KiB) is synthesised in-memory as a Buffer so no
 *     local fixture file is needed and tests don't import app source code.
 *   - The encrypted upload flow runs server-side after file submission — the
 *     test only asserts the UI reflects the upload (thumbnail card visible).
 *   - The garden polls every 5 s; we refresh after upload and wait up to 30 s.
 *
 * Note on upload timing:
 *   The app encrypts and uploads in the browser; on staging this can take a
 *   few seconds. We wait for the status text to disappear before asserting.
 */

import { test, expect } from '@playwright/test';
import * as path from 'path';
import * as os from 'os';
import * as fs from 'fs';
import { Actor } from '../actors/Actor.js';
import { api } from '../support/api.js';

/**
 * Produce a minimal valid JPEG as a Buffer.
 * This is a 1×1 white JPEG (148 bytes), sufficient for the upload pipeline.
 */
function minimalJpegBytes(): Buffer {
  return Buffer.from([
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
}

/**
 * Write unique JPEG bytes to a temp file and return the path.
 * Each call uses a random suffix so the app doesn't see it as a duplicate.
 */
function writeTempJpeg(): string {
  const base = minimalJpegBytes();
  // Append 8 random bytes so content hash differs each call (avoids dedup)
  const rand = Buffer.alloc(8);
  for (let i = 0; i < 8; i++) rand[i] = Math.floor(Math.random() * 256);
  const unique = Buffer.concat([base, rand]);
  const filePath = path.join(os.tmpdir(), `heirlooms-e2e-${Date.now()}-${Math.random().toString(36).slice(2)}.jpg`);
  fs.writeFileSync(filePath, unique);
  return filePath;
}

test.describe('Journey 2 — Upload', () => {
  test.setTimeout(90_000); // upload + encryption can be slow on staging

  test('file-picker upload → item appears in Just arrived', async ({ page }) => {
    const token = await api.generateInviteToken();
    const actor = new Actor('uploader', page);
    await actor.register(token);

    const filePath = writeTempJpeg();

    try {
      // Set up the file chooser listener before clicking the button
      const fileChooserPromise = page.waitForEvent('filechooser');
      await page.getByRole('button', { name: 'Plant' }).click();
      const fileChooser = await fileChooserPromise;
      await fileChooser.setFiles(filePath);

      // Wait for upload status to cycle through to Done or disappear
      // The status text reads "Checking…" → "Preparing…" → "Uploading…" → "Done"
      await expect(page.getByText('Done')).toBeVisible({ timeout: 60_000 });

      // After upload completes the garden refreshes; wait for the item to appear
      // Items in Just arrived are w-40 h-40 flex-shrink-0 divs (PlotThumbCard)
      await page.waitForTimeout(2_000); // allow the 5s poll or immediate refresh
      const justArrivedSection = page.locator('h2', { hasText: 'Just arrived' }).locator('../..');
      await expect(justArrivedSection.locator('.flex-shrink-0').first()).toBeVisible({ timeout: 15_000 });
    } finally {
      fs.unlinkSync(filePath);
    }
  });

  test('drag-drop upload → item appears in Just arrived', async ({ page }) => {
    const token = await api.generateInviteToken();
    const actor = new Actor('drag-drop', page);
    await actor.register(token);

    const filePath = writeTempJpeg();

    try {
      const fileBytes = fs.readFileSync(filePath);

      // Playwright drag-drop via setInputFiles on the hidden file input is not
      // straightforward without a dropzone element, so we simulate via the
      // dataTransfer API injected into the page.
      await page.evaluate(
        async ({ base64, mimeType }: { base64: string; mimeType: string }) => {
          const bytes = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
          const file = new File([bytes], 'drop-test.jpg', { type: mimeType });
          const dt = new DataTransfer();
          dt.items.add(file);
          document.dispatchEvent(new DragEvent('drop', { bubbles: true, dataTransfer: dt }));
        },
        { base64: fileBytes.toString('base64'), mimeType: 'image/jpeg' }
      );

      // Wait for the upload status to progress — 'Checking…' is first text shown
      await expect(
        page.getByText('Checking…')
          .or(page.getByText('Preparing…'))
          .or(page.getByText('Uploading…'))
          .or(page.getByText('Done'))
      ).toBeVisible({ timeout: 30_000 });

      // Wait for Done
      await expect(page.getByText('Done')).toBeVisible({ timeout: 60_000 });

      await page.waitForTimeout(2_000);
      const justArrivedSection = page.locator('h2', { hasText: 'Just arrived' }).locator('../..');
      await expect(justArrivedSection.locator('.flex-shrink-0').first()).toBeVisible({ timeout: 15_000 });
    } finally {
      fs.unlinkSync(filePath);
    }
  });

  test('paste upload → item appears in Just arrived', async ({ page }) => {
    const token = await api.generateInviteToken();
    const actor = new Actor('paste-up', page);
    await actor.register(token);

    const fileBytes = writeTempJpeg();

    try {
      const bytes = fs.readFileSync(fileBytes);

      // Simulate a paste event with an image file in clipboardData
      await page.evaluate(
        async ({ base64, mimeType }: { base64: string; mimeType: string }) => {
          const uint8 = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
          const file = new File([uint8], 'paste-test.jpg', { type: mimeType });

          // Build a DataTransfer with the file
          const dt = new DataTransfer();
          dt.items.add(file);

          document.dispatchEvent(
            new ClipboardEvent('paste', {
              bubbles: true,
              clipboardData: dt as unknown as DataTransfer,
            })
          );
        },
        { base64: bytes.toString('base64'), mimeType: 'image/jpeg' }
      );

      // The paste handler (in GardenPage) picks up the image and starts upload
      await expect(
        page.getByText('Checking…')
          .or(page.getByText('Preparing…'))
          .or(page.getByText('Done'))
      ).toBeVisible({ timeout: 30_000 });

      await expect(page.getByText('Done')).toBeVisible({ timeout: 60_000 });

      await page.waitForTimeout(2_000);
      const justArrivedSection = page.locator('h2', { hasText: 'Just arrived' }).locator('../..');
      await expect(justArrivedSection.locator('.flex-shrink-0').first()).toBeVisible({ timeout: 15_000 });
    } finally {
      fs.unlinkSync(fileBytes);
    }
  });
});
