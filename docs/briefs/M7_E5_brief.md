# SE Brief: M7 E5 — Onboarding polish, web EXIF, storage class rename

**Date:** 9 May 2026
**Milestone:** M7 — Vault E2EE
**Increment:** E5 of 5 (final)
**Type:** Server + Android + Web.

---

## Goal

Wrap up M7 with four focused items:

1. **Storage class rename** — `legacy_plaintext` → `public` across all layers.
2. **Web EXIF extraction** — populate the encrypted metadata blob from real EXIF before upload.
3. **Onboarding copy polish** — Android `VaultSetupScreen` and web `VaultUnlockPage`.
4. **Doc sweep** — ROADMAP, PA_NOTES, PROMPT_LOG, VERSIONS.

No new cryptographic operations. No new API endpoints. No recovery phrase (deferred to M9).

---

## 1. Storage class rename: `legacy_plaintext` → `public`

The value `legacy_plaintext` was a placeholder name. The mechanism stays — plaintext uploads
are the correct path for future public plots and public uploads. The rename reflects intent.

### Server

**Migration V10** — rename existing rows:

```sql
UPDATE uploads SET storage_class = 'public' WHERE storage_class = 'legacy_plaintext';
```

**`UploadRecord` default** (`Database.kt:68`):

```kotlin
// before
val storageClass: String = "legacy_plaintext"

// after
val storageClass: String = "public"
```

**`exif_processed_at` auto-set** (`Database.kt:170`):

EXIF is extracted server-side for plaintext uploads only. The current guard is
`record.storageClass == "legacy_plaintext"`. Change to `record.storageClass == "public"`.

**Initiate handler** (`UploadHandler.kt:595–614`):

Remove the `"public"` block (`"public storage class is not yet supported"`). The legacy
else-branch now writes `storage_class = "public"` in the confirmed row. The client still
does not pass `storage_class` in the request body for a public upload — the server assigns
it. No API contract change visible to callers.

Also remove the `"legacy_plaintext"` block (line 598–600); it was only there to prevent
clients from accidentally requesting the old name. With the rename complete it's dead code.

**Confirm handler** (`UploadHandler.kt:783–785`):

Remove the `"public"` block (`"public storage class is not yet supported"`).
The confirm handler already falls through to `confirmLegacyUpload` for any
non-encrypted, non-public storage class; after removal the public path will
follow the same route.

**Migrate endpoint** (`UploadHandler.kt:627`):

```kotlin
// before
if (existing.storageClass != "legacy_plaintext")

// after
if (existing.storageClass != "public")
```

**SQL string literals** in `Database.kt` — any raw SQL that compares `storage_class =
'legacy_plaintext'` or `storage_class = 'encrypted'` must be updated:

```
grep -rn "legacy_plaintext" HeirloomsServer/src
```

Run the grep; there should be a small set of occurrences all in `Database.kt` and
`UploadHandler.kt`. Update each one.

### Android

**`Models.kt:18`** — `Upload` data class default:

```kotlin
// before
val storageClass: String = "legacy_plaintext"

// after
val storageClass: String = "public"
```

`isEncrypted` is `storageClass == "encrypted"` — no change needed.

### Web

**`UploadThumb.jsx:10`** — update the comment:

```javascript
// Handles encrypted and public storage classes transparently.
```

No logic change — the check is `upload.storageClass === 'encrypted'`; anything else takes the
plaintext path. The comment was the only place the old name appeared.

---

## 2. Web EXIF extraction

### Background

The encrypted metadata blob (`encrypted_metadata` column) was sent all-null from the web in E4.
Android extracts EXIF in `Uploader.kt` using Android's `ExifInterface`. The web equivalent uses
a JS library — `exifr` is the right choice (pure JS, no WASM, permissive licence, actively
maintained, handles both JPEG and HEIC).

### Dependency

```json
"dependencies": {
  "exifr": "^7.1.3"
}
```

No other new runtime dependencies.

### Implementation — `GardenPage.jsx` (`encryptAndUpload`)

Step 6 in the existing `encryptAndUpload` function builds the metadata JSON (currently all-null):

```javascript
// Replace the all-null stub with:
import { parse as parseExif } from 'exifr'

async function buildEncryptedMetadata(file) {
  let exif = null
  if (file.type.startsWith('image/')) {
    try {
      exif = await parseExif(file, {
        gps: true,
        exif: true,
        ifd0: true,
        // Only read the tags we actually need — smaller parse, lower memory.
        pick: ['GPSLatitude', 'GPSLongitude', 'GPSAltitude', 'Make', 'Model',
               'LensModel', 'FocalLength', 'ISO', 'ExposureTime', 'FNumber'],
      })
    } catch { /* leave exif null — all-null blob is valid */ }
  }
  // Videos: exifr can read some QuickTime tags but metadata extraction for video
  // is low value and unreliable in the browser. Leave null for videos.

  return JSON.stringify({
    v: 1,
    gps_lat:         exif?.latitude         ?? null,
    gps_lon:         exif?.longitude        ?? null,
    gps_alt:         exif?.GPSAltitude      ?? null,
    camera_make:     exif?.Make             ?? null,
    camera_model:    exif?.Model            ?? null,
    lens_model:      exif?.LensModel        ?? null,
    focal_length_mm: exif?.FocalLength      ?? null,
    iso:             exif?.ISO              ?? null,
    exposure_num:    exif?.ExposureTime ? 1           : null,
    exposure_den:    exif?.ExposureTime ? Math.round(1 / exif.ExposureTime) : null,
    aperture:        exif?.FNumber          ?? null,
  })
}
```

**`ExposureTime` note.** `exifr` returns `ExposureTime` as a decimal (e.g. `0.004` for 1/250s).
The schema stores numerator + denominator as separate ints. Use `1` as the numerator and
`Math.round(1 / exifr.ExposureTime)` as the denominator. This is accurate for typical shutter
speeds (1/30 → 30, 1/250 → 250, 1/4000 → 4000). Sub-second exposures (e.g. 2s = ExposureTime
2.0) should store `numerator = Math.round(exif.ExposureTime)`, `denominator = 1` — add that
branch.

**GPS note.** `exifr` returns `latitude` / `longitude` as signed decimal degrees (positive N/E,
negative S/W) when `gps: true`. Map directly to `gps_lat` / `gps_lon`. `GPSAltitude` is in
metres, positive = above sea level.

Call `buildEncryptedMetadata(file)` at step 6 in `encryptAndUpload` and pass the result into
`encryptSymmetric`. Replace the current hardcoded all-null JSON string.

### No server changes

The server already stores the encrypted metadata blob opaquely; E4 already wired the confirm
endpoint to accept it. No changes needed on the server.

---

## 3. Onboarding copy polish

### Android — `VaultSetupScreen.kt`

**Headline.** The current `PassphraseEntryContent` renders `Text("Heirlooms", ...)` in
`headlineMedium` + italic. Replace with `OliveBranchWordmark` (the branded component used
elsewhere) at an appropriate size, or at minimum change the text to `"Your vault"` to avoid
repeating the product name as a plain string.

**Sub-copy.** Current: `"Your passphrase protects your vault if you ever lose this phone."`

Change to: `"Your passphrase unlocks your vault on any device. Keep it somewhere safe."`

Rationale: the current copy implies the passphrase is only for device loss — but it's also the
web unlock mechanism. The updated copy is forward-compatible with M8 (multi-user) without
needing a further change.

**Button label.** Current: `"Save passphrase"`. Change to `"Protect vault"`. More evocative;
matches the brand voice used elsewhere.

**Error state.** Current: `"Couldn't save. Try again."`. Leave unchanged — functional and clear.

### Web — `VaultUnlockPage.jsx`

Verify the brand copy from the E4 brief spec is actually in the rendered page:

- Cases A + B sub-label (below passphrase field): *"Your passphrase protects your vault."*
- Case C sub-label (below confirm field): *"Your passphrase protects your vault if you ever
  lose access to this device."*
- Case C button: verify it says something distinct from Cases A + B (brief said "Set passphrase"
  or equivalent; Cases A + B say "Unlock vault").

If the copy matches the brief, no change needed — this is a verification item.

If the Case C sub-label is still the E4 placeholder or all-null, update it to:
`"Your passphrase unlocks your vault from any browser. Keep it somewhere safe."` — matches the
updated Android copy above.

---

## 4. E4 wire-up checklist pass

Go through the E4 wire-up checklist in `M7_E4_brief.md` and tick or fix each item. Particular
items to verify:

- [ ] Logging in from the same browser a second time does NOT add a new row to
      `GET /api/keys/devices` (device count stays stable). The `isVaultSetUp()` guard in
      `deviceKeyManager.js` was the fix; confirm it's working end-to-end.
- [ ] Page refresh prompts passphrase again (master key not in localStorage or IndexedDB).
- [ ] Android-encrypted uploads (from E3) display correctly when viewed on web.
- [ ] All 14 `vaultCrypto.test.js` tests pass.
- [ ] `npm run build` completes cleanly.

---

## 5. Doc sweep

Update the following after implementation:

- **ROADMAP.md** — change M7 status line from `*(in progress — E1, E2, E3 shipped; E4 next)*`
  to `*(shipped — v0.30.0)*` (or similar).
- **PA_NOTES.md** — update version line to v0.30.0; change "M7 E5 is next" to M8.
- **VERSIONS.md** — add v0.30.0 entry.
- **PROMPT_LOG.md** — session entry (as normal).

---

## Acceptance criteria

1. `grep -rn "legacy_plaintext"` returns zero results across all source files.
2. Existing uploads in the DB serve correctly with `storage_class = 'public'` after migration.
3. A photo planted from the web with EXIF data (taken with a phone camera) has non-null
   `camera_make` and `camera_model` in its decrypted metadata blob.
4. Android `VaultSetupScreen` shows updated copy; "Protect vault" button visible.
5. Web `VaultUnlockPage` brand copy matches spec for all three cases.
6. All E4 wire-up checklist items confirmed.
7. `npm test` passes (102+ tests, no regressions).
8. `npm run build` and `./gradlew clean shadowJar` complete without errors.

---

## Ship state after E5

v0.30.0. M7 complete. All photos and videos are end-to-end encrypted from Android and web.
The storage class model is clean (`public` / `encrypted`). Web EXIF is live. M8 (multi-user)
is next.
