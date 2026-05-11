# SE Brief: M8 E1 — Schema + Auth API

**Date:** 11 May 2026
**Milestone:** M8 — Multi-user access
**Increment:** E1 of 4
**Type:** Backend-only. No client changes.

---

## Goal

Lay the server-side foundations for multi-user access: a `users` table, per-user
session tokens, an invite system, and all auth API endpoints. After E1, the server
exposes working auth endpoints but every existing handler still runs unauthenticated
— enforcement is E2's job. Tests prove the schema migrates cleanly and the auth
endpoints behave correctly in isolation.

---

## Crypto protocol

The passphrase is the single merged credential — it both authenticates the user to
the server and unlocks the vault. The server never sees the passphrase or any vault
key material.

**Key derivation (client side):**

```
output  = Argon2id(passphrase, auth_salt, m=65536, t=3, p=1, outputLen=64)
auth_key        = output[0..31]   -- sent to server on login; proves passphrase knowledge
master_key_seed = output[32..63]  -- used locally to derive/unwrap vault master key; never sent
```

`auth_salt`: 16 random bytes generated client-side at registration. Stored on the
server so it can be returned to a client that has lost its local copy (e.g. a
web-only user on a fresh browser).

**Server-side storage:**

```
auth_verifier = SHA256(auth_key)   -- stored in users.auth_verifier; never the auth_key itself
```

**Login flow:**
1. Client fetches `auth_salt` via `POST /api/auth/challenge` (by username).
2. Client computes `Argon2id(passphrase, auth_salt)` → `auth_key`.
3. Client sends `auth_key` to `POST /api/auth/login`.
4. Server computes `SHA256(auth_key)`, compares to stored `auth_verifier`.
5. Server issues a session token (256-bit random, base64url-encoded).
6. Server stores `SHA256(session_token)` in `user_sessions`; raw token never persisted.

---

## Migrations

### V20 — auth tables

```sql
-- V20__m8_auth_tables.sql

CREATE TABLE users (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    username        TEXT        NOT NULL UNIQUE,  -- chosen at onboarding; used for challenge lookup
    display_name    TEXT        NOT NULL,
    auth_verifier   BYTEA       NULL,             -- SHA256(auth_key); NULL until passphrase set
    auth_salt       BYTEA       NULL,             -- 16 bytes; NULL until passphrase set
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE user_sessions (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL REFERENCES users(id),
    token_hash      BYTEA       NOT NULL UNIQUE,  -- SHA256(bearer token); raw token never stored
    device_kind     TEXT        NOT NULL CHECK (device_kind IN ('android', 'web', 'ios')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_used_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ NOT NULL          -- last_used_at + 90 days; refreshed on each use
);

CREATE INDEX idx_user_sessions_token   ON user_sessions (token_hash);
CREATE INDEX idx_user_sessions_expiry  ON user_sessions (expires_at);
CREATE INDEX idx_user_sessions_user    ON user_sessions (user_id);

CREATE TABLE invites (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    token           TEXT        NOT NULL UNIQUE,  -- 32 random bytes, base64url-encoded
    created_by      UUID        NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ NOT NULL,         -- created_at + 48 hours
    used_at         TIMESTAMPTZ NULL,
    used_by         UUID        NULL REFERENCES users(id)
);

CREATE INDEX idx_invites_token ON invites (token) WHERE used_at IS NULL;
```

### V21 — backfill + FK tightening

This migration seeds Bret's user row, backfills all existing rows to his `user_id`,
then tightens the NULL sentinels introduced in M6/M7.

```sql
-- V21__m8_backfill_and_fk_tighten.sql

-- 1. Insert Bret as the founding user.
--    auth_verifier and auth_salt are NULL; Bret sets his passphrase on first
--    M8 Android launch via POST /api/auth/setup-existing (see below).
INSERT INTO users (id, username, display_name)
VALUES ('00000000-0000-0000-0000-000000000001', 'bret', 'Bret');

-- 2. Add user_id to uploads (no FK yet — add after backfill).
ALTER TABLE uploads ADD COLUMN user_id UUID NULL;
UPDATE uploads SET user_id = '00000000-0000-0000-0000-000000000001';
ALTER TABLE uploads
    ALTER COLUMN user_id SET NOT NULL,
    ADD CONSTRAINT fk_uploads_user FOREIGN KEY (user_id) REFERENCES users(id);
CREATE INDEX idx_uploads_user ON uploads (user_id);

-- 3. Add owner_user_id FK to capsules.
--    capsules has created_by_user TEXT (free text, M5 v1 sentinel). Add a
--    proper UUID FK alongside it; backfill to Bret.
ALTER TABLE capsules ADD COLUMN user_id UUID NULL;
UPDATE capsules SET user_id = '00000000-0000-0000-0000-000000000001';
ALTER TABLE capsules
    ALTER COLUMN user_id SET NOT NULL,
    ADD CONSTRAINT fk_capsules_user FOREIGN KEY (user_id) REFERENCES users(id);
CREATE INDEX idx_capsules_user ON capsules (user_id);

-- 4. Tighten plots.owner_user_id (NULL sentinel from V10).
UPDATE plots SET owner_user_id = '00000000-0000-0000-0000-000000000001'
    WHERE owner_user_id IS NULL;
ALTER TABLE plots
    ALTER COLUMN owner_user_id SET NOT NULL,
    ADD CONSTRAINT fk_plots_owner FOREIGN KEY (owner_user_id) REFERENCES users(id);

-- 5. Tighten wrapped_keys.user_id (NULL sentinel from V12).
UPDATE wrapped_keys SET user_id = '00000000-0000-0000-0000-000000000001'
    WHERE user_id IS NULL;
ALTER TABLE wrapped_keys
    ALTER COLUMN user_id SET NOT NULL,
    ADD CONSTRAINT fk_wrapped_keys_user FOREIGN KEY (user_id) REFERENCES users(id);

-- 6. Fix recovery_passphrase: drop single-row sentinel, make user_id the PK.
--    The existing row has user_id = NULL from V12; backfill first.
UPDATE recovery_passphrase
    SET user_id = '00000000-0000-0000-0000-000000000001';
-- Drop the old single-row constraint and id column.
ALTER TABLE recovery_passphrase DROP COLUMN id;
ALTER TABLE recovery_passphrase
    ALTER COLUMN user_id SET NOT NULL,
    ADD CONSTRAINT pk_recovery_passphrase PRIMARY KEY (user_id),
    ADD CONSTRAINT fk_recovery_passphrase_user
        FOREIGN KEY (user_id) REFERENCES users(id);

-- 7. Add user_id to pending_device_links so pairing is per-user.
ALTER TABLE pending_device_links ADD COLUMN user_id UUID NULL
    REFERENCES users(id);
UPDATE pending_device_links
    SET user_id = '00000000-0000-0000-0000-000000000001'
    WHERE user_id IS NULL;
```

Note: `pending_device_links.user_id` is left nullable — it is set when the pairing
code is generated (step 1 of the web pairing flow). Rows in state `initiated` before
`user_id` is set are invalid and cleaned up by the 15-minute expiry.

---

## API endpoints

All new endpoints live under `/api/auth/`. Existing handlers are unchanged in E1.

### POST /api/auth/challenge
Unauthenticated. Returns the `auth_salt` for a given username so the client can
run Argon2id before sending `auth_key`. Does not confirm whether the username
exists (to avoid enumeration) — returns a deterministic fake salt for unknown
usernames derived from `HMAC-SHA256(server_secret, username)`.

Request:
```json
{ "username": "bret" }
```
Response 200:
```json
{ "auth_salt": "<base64url 16 bytes>" }
```

### POST /api/auth/login
Unauthenticated. Verifies `auth_key` against stored `auth_verifier`, issues a
session token.

Request:
```json
{
  "username": "bret",
  "auth_key": "<base64url 32 bytes>"
}
```
Response 200:
```json
{
  "session_token": "<base64url 32 bytes>",
  "user_id":       "<uuid>",
  "expires_at":    "<iso8601>"
}
```
Response 401: invalid credentials (single generic message — do not distinguish
"unknown username" from "wrong auth_key").

### POST /api/auth/setup-existing
Unauthenticated, but gated: only succeeds if the requester's `device_id` matches
a row in `wrapped_keys` for the target user AND `users.auth_verifier IS NULL`.
This is the one-time path for Bret to set his passphrase after the M8 migration.
After this call, the auth_verifier is set and the endpoint rejects future calls
for the same user.

Request:
```json
{
  "username":     "bret",
  "device_id":    "<device id matching a wrapped_keys row>",
  "auth_salt":    "<base64url 16 bytes>",
  "auth_verifier":"<base64url 32 bytes>"
}
```
Response 200: same shape as `/api/auth/login` — issues a session token.
Response 409: auth_verifier already set.
Response 401: device_id not found for user, or user not found.

### POST /api/auth/logout
Authenticated (session token in `X-Api-Key` header). Deletes the calling session.

Response 204.

### GET /api/auth/invites
Authenticated. Generates a new invite token for the authenticated user.

Response 200:
```json
{
  "token":      "<base64url 32 bytes>",
  "expires_at": "<iso8601>"
}
```

### POST /api/auth/register
Unauthenticated. Redeems an invite token and creates a new user account, completing
the passphrase and crypto setup in one call. The invite must be unused and unexpired.

Request:
```json
{
  "invite_token":       "<token from invite>",
  "username":           "alice",
  "display_name":       "Alice",
  "auth_salt":          "<base64url 16 bytes>",
  "auth_verifier":      "<base64url 32 bytes>",
  "wrapped_master_key": "<base64url bytes>",
  "wrap_format":        "p256-ecdh-hkdf-aes256gcm-v1",
  "pubkey_format":      "p256-spki",
  "pubkey":             "<base64url bytes>",
  "device_id":          "<client-generated device id>",
  "device_label":       "Alice's Pixel 9",
  "device_kind":        "android"
}
```
Response 201: same shape as `/api/auth/login` — issues a session token immediately.
Response 409: username already taken.
Response 410: invite token expired or already used.

### POST /api/auth/pairing/initiate
Authenticated (Android). Generates a 6–8 digit numeric code and inserts a
`pending_device_links` row tied to the authenticated user. The code is the Android
device's `one_time_code`; the web user types it into the browser.

Response 200:
```json
{
  "code":       "38291047",
  "expires_at": "<iso8601, 5 minutes from now>"
}
```

### POST /api/auth/pairing/qr
Unauthenticated. Web calls this after the user types the numeric code. Validates
the code, returns a QR payload (a session ID) that the web UI renders as a QR code
for the Android app to scan.

Request:
```json
{ "code": "38291047" }
```
Response 200:
```json
{ "session_id": "<uuid>" }
```
Response 404: code not found or expired.

The `pending_device_links` row transitions to state `device_registered`; `session_id`
is stored in a new `web_session_id` column (see V21 note — add this column to
`pending_device_links` in V21 or as part of this endpoint's first use).

Add `web_session_id TEXT NULL` to `pending_device_links` in V21:
```sql
ALTER TABLE pending_device_links ADD COLUMN web_session_id TEXT NULL;
```

### POST /api/auth/pairing/complete
Authenticated (Android). Android scans the QR code, extracts `session_id`, derives
an ephemeral keypair for the web session, and posts the wrapped master key for it.
The server transitions the row to `wrap_complete` and stores a new `user_sessions`
row for the web session along with the wrapped key delivery payload.

Request:
```json
{
  "session_id":         "<uuid from QR>",
  "wrapped_master_key": "<base64url bytes — master key wrapped to web ephemeral pubkey>",
  "wrap_format":        "p256-ecdh-hkdf-aes256gcm-v1",
  "web_pubkey":         "<base64url bytes — web's ephemeral pubkey>",
  "web_pubkey_format":  "p256-spki"
}
```
Response 200:
```json
{ "ok": true }
```

### GET /api/auth/pairing/status
Unauthenticated (web polls this while waiting). Returns the pairing result once
Android has completed the handshake.

Query param: `?session_id=<uuid>`

Response 200 (pending):
```json
{ "state": "pending" }
```
Response 200 (complete):
```json
{
  "state":              "complete",
  "session_token":      "<base64url 32 bytes>",
  "wrapped_master_key": "<base64url bytes>",
  "wrap_format":        "p256-ecdh-hkdf-aes256gcm-v1",
  "expires_at":         "<iso8601>"
}
```
Response 404: session_id not found or expired.

Web should poll every 1 second. The endpoint returns immediately either way —
there is no long-hold behaviour. If the user stops waiting (navigates away),
polling stops; the `pending_device_links` row expires naturally after 15 minutes.

---

## Session token mechanics

- Token value: 32 random bytes, base64url-encoded. Never stored — only `SHA256(token)`.
- Header: `X-Api-Key: <token>` (keeps the same header name as the current shared key).
- Expiry: 90 days from `last_used_at`. Refreshed on every authenticated request (see E2).
- A background job (add to the existing orphaned-blob cleanup) deletes expired sessions
  daily. No active revocation needed in E1 — logout deletes the calling row, which is enough.

---

## Tests

**Schema canary tests (~8)**

Using Testcontainers (PostgreSQL), confirm:

1. Fresh migration produces all new tables with correct columns.
2. V21 backfill: all `uploads` rows have `user_id = Bret's UUID` after migration.
3. V21 backfill: all `capsules` rows have `user_id = Bret's UUID` after migration.
4. V21 backfill: all `plots` rows have `owner_user_id = Bret's UUID` after migration.
5. V21 tighten: inserting an `uploads` row with `user_id = NULL` fails with FK/NOT NULL error.
6. V21 `recovery_passphrase`: `id` column no longer exists; `user_id` is the PK.
7. Inserting two `recovery_passphrase` rows for the same `user_id` fails (PK violation).
8. `pending_device_links` has `user_id` and `web_session_id` columns after migration.

**Auth endpoint integration tests (~20)**

9. `POST /api/auth/challenge` — known username returns correct salt.
10. `POST /api/auth/challenge` — unknown username returns a response (no 404; salt is fake but deterministic).
11. `POST /api/auth/login` — correct auth_key returns session token with `expires_at`.
12. `POST /api/auth/login` — wrong auth_key returns 401.
13. `POST /api/auth/login` — unknown username returns 401 (same as wrong key; no enumeration).
14. `POST /api/auth/setup-existing` — succeeds with valid device_id and NULL auth_verifier; returns session token.
15. `POST /api/auth/setup-existing` — rejected if auth_verifier already set (409).
16. `POST /api/auth/setup-existing` — rejected if device_id not in wrapped_keys (401).
17. `GET /api/auth/invites` — authenticated → returns token with 48-hour expiry.
18. `GET /api/auth/invites` — unauthenticated → 401.
19. `POST /api/auth/register` — valid invite token → creates user row, issues session token, marks invite used.
20. `POST /api/auth/register` — expired invite → 410.
21. `POST /api/auth/register` — already-used invite → 410.
22. `POST /api/auth/register` — duplicate username → 409.
23. `POST /api/auth/logout` — deletes calling session; subsequent requests with same token → 401.
24. Full pairing flow: initiate → qr → complete → status returns complete with session token and wrapped key.
25. `GET /api/auth/pairing/status` — returns `pending` before `complete` is called.
26. `GET /api/auth/pairing/status` — returns 404 for unknown session_id.
27. `POST /api/auth/pairing/qr` — returns 404 for expired or unknown code.
28. Two users, same username → 409 on register.

---

## What E1 does NOT include

- Enforcement: existing handlers continue to work without authentication in E1.
- Session refresh on use (E2 adds this to the auth middleware).
- Per-user DB query filtering (E2).
- Any client changes (E3 and E4).

---

## Bret migration path

When the updated server first deploys, Bret's `users` row has `auth_verifier = NULL`.
On his first M8 Android launch:

1. App detects: a `wrapped_keys` row exists for this device but no stored session token.
2. App presents a "Set your new passphrase" screen (distinct from the new-user invite screen).
3. Bret enters passphrase → client derives `auth_key` + `auth_salt` → calls
   `POST /api/auth/setup-existing` with his device_id and the new credentials.
4. Server looks up device_id in `wrapped_keys`, finds Bret's user_id, confirms
   `auth_verifier IS NULL`, stores the credentials, issues a session token.
5. Normal flow resumes.

This path is only possible once per user (enforced by the 409 response once
`auth_verifier IS NOT NULL`).

---

## Acceptance criteria

1. `./gradlew test` passes — all new tests green, no regressions.
2. V20 and V21 run cleanly on a fresh database (Testcontainers).
3. V21 runs cleanly on a database with existing data (all backfills applied correctly).
4. All 8 schema canary tests pass.
5. All 20 auth endpoint tests pass.
6. Existing integration tests still pass (no handler behaviour has changed in E1).

---

## Documentation updates

- `docs/PA_NOTES.md` — add M8 design decisions section (see PA_NOTES update)
- `docs/VERSIONS.md` — entry when E1 ships
- `docs/PROMPT_LOG.md` — standard entry
