# SE Brief: M8 E4 — Android Client + Capsule Creation

**Date:** 11 May 2026
**Milestone:** M8 — Multi-user access
**Increment:** E4 of 4
**Type:** Android-only. No server or web changes.

---

## Goal

Update the Android app to use per-user session tokens, add the first-run invite
redemption flow, the web pairing flow (generate numeric code + scan QR), and the
Devices & Access screen. Also ships Android capsule creation — the deferred M5
Increment 3 — alongside the auth work since both require significant new screens.

---

## Part A: Auth migration

### Session token storage

Replace the current `api_key` SharedPreferences entry with two new entries:

| Key | Type | Content |
|---|---|---|
| `session_token` | String | Current session token (base64url) |
| `username` | String | Username (for re-auth and display) |
| `auth_salt` | String | base64url 16 bytes — cached locally to avoid challenge roundtrip |
| `device_id` | String | Client-generated UUID, set once at first run, never changes |

Remove all reads/writes of `api_key`. Replace with `session_token` everywhere.
The `X-Api-Key` header value changes from the old API key to the session token.
No other change to the HTTP client or interceptors.

### First-run detection

On app start, `MainActivity` (or the top-level ViewModel) checks:

- **Has `session_token`?** → normal launch, attempt first request; if 401, go to step 2.
- **Has `wrapped_keys` entry for this device_id?** (check via `GET /api/keys/device/<device_id>`) → Bret migration path (see below).
- **Neither?** → first run, show invite redemption screen.

### Bret migration path

After M8 deploys, Bret's device has the old `api_key` but no `session_token` and
no `username`. Detection: `api_key` present but `session_token` absent.

Show a one-time "Set up your new passphrase" screen:

1. Explain: "Heirlooms now uses a personal passphrase instead of an API key."
2. Username field (pre-filled with "bret" if you want, or left blank).
3. Passphrase + passphrase-confirm fields.
4. On submit:
   a. Generate random 16-byte `auth_salt`.
   b. Run Argon2id on device (existing `VaultCrypto` path) → `auth_key` (bytes 0–31).
   c. Call `POST /api/auth/setup-existing` with username, device_id, auth_salt,
      `SHA256(auth_key)` as auth_verifier.
   d. Server responds with session token → store in SharedPreferences.
   e. Store username and auth_salt in SharedPreferences.
   f. Delete the old `api_key` entry.
   g. Navigate to normal app flow.

### Login screen (expired session)

If a session token exists but a request returns 401 (session expired):

1. Clear the `session_token` entry.
2. Show the login screen: username (pre-filled from SharedPreferences), passphrase field.
3. On submit:
   a. Read cached `auth_salt` from SharedPreferences (no challenge roundtrip needed).
   b. Argon2id → `auth_key`.
   c. `POST /api/auth/login` → new session token.
   d. Store new token; resume normal flow.

If `auth_salt` is somehow missing (edge case: SharedPreferences cleared), fall back
to `POST /api/auth/challenge` to fetch it.

### Crypto on Android

`VaultCrypto.kt` already has Argon2id derivation and AES-GCM. Add:

- `deriveAuthAndMasterKeys(passphrase: String, salt: ByteArray): Pair<ByteArray, ByteArray>`
  — runs Argon2id(passphrase, salt, m=65536, t=3, p=1, outputLen=64), returns
  `(auth_key = bytes[0..31], master_key_seed = bytes[32..63])`.
- `computeAuthVerifier(authKey: ByteArray): ByteArray` — returns `SHA256(authKey)`.

Both are thin wrappers over existing primitives.

---

## Part B: Devices & Access screen

New screen accessible from the burger menu (settings area).

### Section 1 — Invite someone

- **Generate invite** button.
- Calls `GET /api/auth/invites`.
- Shows the returned token as both:
  - A QR code (encode the URL `https://heirlooms.digital/join?token=<token>`)
  - A shareable text link with a Share button (Android share sheet).
- Shows expiry ("Expires in 47 hours").

### Section 2 — Link a web browser

- **Generate pairing code** button.
- Calls `POST /api/auth/pairing/initiate` → receives 6–8 digit numeric code + expiry.
- Shows the code large on screen with a countdown timer (5 minutes).
- Instructions: "Type this code into heirlooms.digital on your browser."
- After the user types the code on web, the browser shows a QR code. The Android user
  then taps **Scan QR code** to proceed.

### Section 2b — Scan QR (pairing completion)

- Opens the device camera via a QR scanner (use an existing QR library — ZXing or
  ML Kit, whichever is already in the project, or add ML Kit Barcode Scanning).
- Scans the QR code shown by the browser. QR encodes JSON: `{ "session_id": "<uuid>", "pubkey": "<base64url spki>" }`.
- Parse: extract `session_id` and `pubkey`.
- Wrap the vault master key to `pubkey` using ECDH-HKDF-AES-GCM (existing
  `VaultCrypto` path for wrapping to a P-256 pubkey).
- Call `POST /api/auth/pairing/complete` with `session_id`, `wrapped_master_key`,
  `wrap_format`, `web_pubkey`, `web_pubkey_format`.
- Show confirmation: "Browser linked successfully."

---

## Part C: Android capsule creation (M5 Increment 3)

This is the deferred increment from Milestone 5. It brings capsule authoring to
Android, mirroring the web UI's create-capsule flow.

### New screens

**CapsuleCreateScreen**
Accessed from:
- A **+ New capsule** button on the Capsules tab.
- After selecting a photo in the share sheet, an "Add to new capsule" option
  alongside "Upload to Garden" (extend `ShareActivity`).

Fields:
- **Recipient** — free-text field (M8; becomes a contacts picker at M9).
- **Unlock date** — date picker. Default: 1 year from today. Must be in the future.
- **Message** — multiline text field. Optional.
- **Photos** — photo picker (multi-select from device gallery) or carry through
  from the share sheet.
- **Shape** — not surfaced directly. All new capsules start as `open`
  (editable). Sealing is a separate action from the detail screen.

On submit:
1. `POST /api/capsules` → creates the capsule.
2. For each selected photo: run the existing encrypted upload flow
   (`uploadEncryptedViaSigned`), then `POST /api/capsules/:id/photos`.
3. If a message was entered: `POST /api/capsules/:id/messages`.
4. Navigate to `CapsuleDetailScreen` for the new capsule.

**CapsuleDetailScreen (updated)**
The existing read-only detail screen gains:
- **Add photo** button (opens photo picker; runs encrypted upload + add-to-capsule).
- **Edit message** button (opens message edit; calls update-message endpoint).
- **Seal capsule** button (visible when `shape == "open"`). Confirmation dialog
  before sealing. On confirm: `POST /api/capsules/:id/seal`.
- **Cancel capsule** button (visible when `state != "cancelled"`). Confirmation
  dialog. On confirm: `POST /api/capsules/:id/cancel`.

These actions were already available on web; this brings Android to parity.

### ViewModel

`CapsuleCreateViewModel`:
- `recipient: MutableStateFlow<String>`
- `unlockDate: MutableStateFlow<LocalDate>`
- `message: MutableStateFlow<String>`
- `selectedPhotos: MutableStateFlow<List<Uri>>`
- `isSubmitting: MutableStateFlow<Boolean>`
- `error: MutableStateFlow<String?>`
- `fun submit()` — suspend function; calls APIs in sequence; updates `isSubmitting`.

`CapsuleDetailViewModel` (extend existing):
- `fun seal()`, `fun cancel()`, `fun addPhoto(uri: Uri)`, `fun editMessage(body: String)`.

### Orientation safety

All new screens must handle orientation changes correctly. The ViewModel +
`SavedStateHandle` pattern is already in place across the app (M6 D4) — follow
it for new ViewModels. `isSubmitting` state must survive rotation.

---

## Tests

**Auth unit tests (~8)**

1. `deriveAuthAndMasterKeys` — known passphrase + salt → deterministic output matches
   a pre-computed reference vector (compute the reference with the web implementation).
2. `computeAuthVerifier` — `SHA256(auth_key)` matches expected value.
3. Login flow (mocked server): challenge → argon2id → login → session token stored.
4. Expired session (401 on first request) → login screen shown.
5. Bret migration detection: `api_key` present, `session_token` absent → setup-existing screen shown.
6. Pairing QR parse: valid JSON → session_id and pubkey extracted correctly.
7. Pairing QR parse: malformed JSON → error state, no crash.
8. Logout: session token cleared from SharedPreferences; app returns to first-run state.

**Capsule create tests (~5)**

9. `CapsuleCreateViewModel`: submit with valid fields → capsule created, photos
   uploaded, message posted; navigates to detail.
10. Submit with no recipient → validation error, no API calls.
11. Submit with past unlock date → validation error.
12. Submit fails on `POST /api/capsules` (500) → error state shown, `isSubmitting` cleared.
13. Share sheet → "Add to new capsule" → carries photo URI into `CapsuleCreateScreen`.

---

## What E4 does NOT include

- Passphrase change flow — deferred.
- "Manage logged-in devices" list (show/revoke active sessions) — deferred.
- Multi-recipient capsules — deferred to M9.
- Capsule photo encryption at the capsule level (cryptographic sealing) — M9.
- Web changes (E3).

---

## Acceptance criteria

1. `./gradlew test` passes — all new tests green, no regressions.
2. Bret can open the updated app, set his passphrase via the migration flow, and
   reach the garden with existing data intact.
3. A new user (friend tester) can open the app for the first time, scan an invite
   QR code from Bret's device, complete onboarding, and see their own empty garden.
4. The tester's garden is completely separate from Bret's — no data crosses over.
5. Bret can generate a pairing code, have the tester enter it on web, scan the
   resulting QR code, and confirm "Browser linked successfully."
6. Both users can create a capsule on Android: pick a date, add photos, write a
   message, seal it, and see it on the Capsules tab.
7. Invite QR generation on the Devices & Access screen produces a scannable code
   whose URL opens `/join?token=<token>` in a browser.

---

## Documentation updates

- `docs/PA_NOTES.md` — update current version
- `docs/VERSIONS.md` — entry when E4 ships; close M8
- `docs/PROMPT_LOG.md` — standard entry
- `docs/ROADMAP.md` — mark M8 as shipped
