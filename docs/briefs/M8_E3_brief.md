# SE Brief: M8 E3 — Web Client

**Date:** 11 May 2026
**Milestone:** M8 — Multi-user access
**Increment:** E3 of 4
**Type:** Web-only. No server or Android changes.

---

## Goal

Update HeirloomsWeb to use the M8 auth system. The current API-key-in-state model
is replaced with proper per-user sessions. Three distinct entry paths are supported:
new user registration via invite, returning user login, and web device pairing
(Android-initiated QR handshake). After E3, the web client is fully multi-user aware.

---

## Current auth model (to be replaced)

`App.jsx` currently holds `apiKey` in React state, populated from `localStorage`
on mount. The user types it on a login screen if absent. `RequireAuth` checks this
state and redirects to `/login` if null.

This entire mechanism is replaced. The stored value changes from a shared API key
to a per-user session token; the login screen becomes a passphrase-based auth flow;
and two new entry paths are added (invite registration and QR pairing).

---

## Session storage

Store the session token in `localStorage` under key `heirlooms_session_token`.
Store `username` and `display_name` under `heirlooms_username` /
`heirlooms_display_name` for display purposes.

On app mount, read the token from localStorage and validate it with the server
(`GET /api/auth/me` — add this lightweight endpoint in E2 if not already present,
or just attempt the first authenticated request and handle 401). If 401, clear
localStorage and redirect to `/login`.

On logout: call `POST /api/auth/logout`, clear localStorage, redirect to `/login`.

---

## Crypto in the browser

The web client already uses WebCrypto and `@noble/hashes/argon2` (added in M7).
E3 reuses those dependencies.

**Login flow:**
1. User enters username + passphrase.
2. Fetch `auth_salt` via `POST /api/auth/challenge` (returns base64url salt).
3. Run `Argon2id(passphrase, auth_salt, m=65536, t=3, p=1, outputLen=64)`.
   — Must yield the UI thread before this call (existing M7 pattern:
   `await new Promise(r => setTimeout(r, 0))`; see PA_NOTES).
4. `auth_key = output[0..31]`; `master_key_seed = output[32..63]`.
5. POST `auth_key` (base64url) to `POST /api/auth/login` → receive session token.
6. Store session token in localStorage.
7. Use `master_key_seed` to unwrap the `wrapped_master_key` blob from
   `GET /api/keys/device` (existing M7 endpoint) → hold master key in memory.

**Registration flow** (invite redemption):
1. Generate a P-256 keypair via WebCrypto (non-extractable private key).
2. User chooses username + passphrase.
3. Client generates a random 16-byte `auth_salt`.
4. Run Argon2id → derive `auth_key` + `master_key_seed`.
5. Generate master key (32 random bytes via WebCrypto).
6. Wrap master key using the P-256 pubkey (ECDH-HKDF-AES-GCM, same as M7 web device).
7. POST to `POST /api/auth/register` with invite_token, username, display_name,
   auth_salt, `SHA256(auth_key)` as auth_verifier, wrapped_master_key, pubkey,
   device_id (random UUID generated once and stored in localStorage), device_kind="web".
8. Server responds with session token → store in localStorage.

**Note on auth_verifier in the register call:** the client sends `SHA256(auth_key)`,
not `auth_key` itself. The server stores it directly as `auth_verifier`.

---

## Entry paths and routing

### `/login` — returning user

Passphrase entry screen. Two fields: username, passphrase. On submit:
1. Run the login crypto flow above.
2. On success: navigate to the `state.from` destination (existing `RequireAuth`
   redirect pattern) or `/` if none.
3. On failure: show "Invalid username or passphrase" — single message, no distinction.

Replace the current API-key `<input>` with this form. The existing `state.from`
pattern from M7 continues to work; no changes needed.

### `/join` — new user via invite

Route: `/join?token=<invite_token>` or `/join` with a manual token entry field.

Flow:
1. If token is present in the URL query param, pre-populate and skip the entry field.
2. Show: username field, display name field, passphrase field, passphrase-confirm field.
3. On submit: run the registration crypto flow above.
4. On success: navigate to `/`.
5. On error (409 username taken, 410 invite invalid): show inline error.

Android deep-link: `heirlooms://join?token=<token>` opens the Android app on first
run if installed; the web URL `/join?token=<token>` is the fallback for web-only
users. Both use the same invite token.

### `/access/pair` — web device pairing (Android-initiated)

Route: `/access/pair` or accessible from the Devices & Access page.

This is the primary "first time on a new browser" path for users who have Android.

Flow:
1. Show a numeric code entry field with instructions: "Open Heirlooms on your
   phone and go to Devices & Access to generate a pairing code."
2. User enters the 6–8 digit code → POST to `POST /api/auth/pairing/qr`.
3. Server returns a `session_id`. Generate a P-256 keypair in WebCrypto
   (non-extractable); store the private key in memory.
4. Encode `{ session_id, pubkey }` as a QR code and display it full-screen.
   Use a QR code library (e.g. `qrcode` npm package — already available or add it).
5. Begin polling `GET /api/auth/pairing/status?session_id=<id>` every 1 second.
6. On `state: "complete"`:
   a. Receive `wrapped_master_key` + `session_token`.
   b. Unwrap master key using the in-memory private key (ECDH-HKDF-AES-GCM).
   c. Store session token in localStorage.
   d. Hold master key in memory for this session.
   e. Navigate to `/`.
7. If polling receives 404 (expired) or the user navigates away: clean up and show
   "Code expired — go back and try again."

The QR code must encode the `pubkey` so the Android app can wrap the master key
to it. Encode as JSON: `{ "session_id": "<uuid>", "pubkey": "<base64url spki>" }`.

---

## Devices & Access page

Route: `/access`
Nav: add an entry in the settings/account menu (wherever the logout button lives).

Two sections:

### Invite someone
- Button: **Generate invite link**.
- Calls `GET /api/auth/invites` → receive token + expiry.
- Show: a shareable URL (`https://heirlooms.digital/join?token=<token>`) and a
  copy-to-clipboard button. Show expiry time ("Expires in 47 hours").
- Generating a new invite does not invalidate previous ones.
- No list of past invites needed in E3.

### Link this browser
- Button: **Pair with phone**.
- Navigates to `/access/pair` (the pairing flow above).
- If a session is already active (user is already logged in), this allows them to
  transfer the session to another browser. Show a note: "Only needed if you're
  setting up a new browser."

---

## RequireAuth changes

The existing `RequireAuth` wrapper checks `apiKey !== null`. Replace this with
`sessionToken !== null` (reading from localStorage on mount). The rest of the
redirect logic (`state.from`) is unchanged.

---

## API key header

All existing `api.js` calls currently send `'X-Api-Key': apiKey`. Replace `apiKey`
with the session token from the new auth state. No other changes to `api.js` — the
header name stays the same.

---

## Tests

All existing vitest web tests must continue to pass. New tests:

1. Login form submits challenge + login calls in sequence; on success, session token
   is stored and user is navigated to destination.
2. Login with wrong passphrase: server returns 401 → error message shown, no navigation.
3. Register flow: invite token + passphrase → register call → session stored.
4. Register with duplicate username: 409 → inline error shown.
5. Register with expired invite: 410 → inline error shown.
6. Pairing flow: code entered → qr endpoint called → QR shown → status polled →
   on complete, session stored and master key held in memory.
7. Pairing expired (404 from status) → error message shown.
8. Invite generation: button click → GET /api/auth/invites → shareable URL displayed.
9. Logout: clears localStorage, redirects to /login.
10. `RequireAuth` with no session token in localStorage → redirects to /login with `state.from`.

---

## What E3 does NOT include

- Any server changes (all auth endpoints are E1/E2 deliverables).
- Android changes (E4).
- A "manage devices" list (which devices are logged in) — deferred.
- Passphrase change flow — deferred.

---

## Acceptance criteria

1. All vitest tests pass — existing tests green, new tests green.
2. A returning user can log in with username + passphrase and reach the garden.
3. A new user can register via an invite link and reach the garden.
4. A user on a new browser can pair with their Android device via QR code and
   reach the garden with the vault unlocked (master key in memory).
5. The Devices & Access page generates a shareable invite URL.
6. Logout clears the session and returns to /login.
7. A page refresh on any route with no session token redirects to /login with the
   correct `state.from` destination preserved.

---

## Documentation updates

- `docs/VERSIONS.md` — entry when E3 ships
- `docs/PROMPT_LOG.md` — standard entry
