# Milestone 9 — Friends, item sharing, Android plot management

Three workstreams. No web changes. Server + Android throughout.

Target version: v0.45.0 (or split across v0.45.x increments).

---

## Context and scope

This milestone inserts between M8 (multi-user) and the former M9 (strong sealing,
now M11). It adds the social layer needed before shared plots (M10) and real sealing
(M11) make sense.

**In scope:**
- Account-level sharing keypair per user (crypto foundation for all sharing features)
- Friendships (automatic on invite redemption; bidirectional)
- Individual item sharing: Android UI, friend picker, DEK re-wrap, recipient upload record
- Received shared items: land in Just Arrived with friend indicator; per-user rotation and tags
- Android plot management: create, rename, delete (server endpoints already exist)

**Out of scope:**
- Web sharing (Android-only for this milestone)
- Shared plots (M10)
- Automation rules (future)
- Re-sharing (to be decided; not implemented in M9)

---

## Design decisions (confirmed)

| Decision | Choice |
|---|---|
| Sharing keypair | Account-level P-256 keypair per user; private key wrapped to master key and stored server-side; public key on user record |
| Compost independence | Recipient composting their shared copy has no effect on owner's copy (and vice versa) |
| Revocation | None — once shared, it's shared |
| Tags on shared items | Per-user (recipient tags independently; owner doesn't see them) |
| Rotation on shared items | Per-user (already true since each user has their own upload record) |
| Friends list placement | Burger → Friends (new dedicated screen) |
| Detail view attribution | Photo detail shows "Shared by [display name]" |

---

## Technical design

### Sharing keypair

Each user has one account-level P-256 keypair for receiving shared DEKs. It is
distinct from device pairing keypairs.

- **Server storage:** new `account_sharing_keys` table (see V22 migration).
- **Private key storage:** wrapped to the user's master key using `master-aes256gcm-v1`,
  stored in `account_sharing_keys.wrapped_privkey`. Loaded into `VaultSession` alongside
  the master key on vault unlock.
- **Public key:** stored in `account_sharing_keys.pubkey` (base64-encoded SPKI).
  Readable by any authenticated user (needed to share to a friend).
- **Generation:** during `register` and `setup-existing`. Lazy migration for
  existing users: detect missing sharing key on vault unlock, generate and upload.
- **Wrapping algorithm for shared DEKs:** `p256-ecdh-hkdf-aes256gcm-v1` (same
  algorithm already used for device pairing key wrapping in M8).
- **Distinguishing from master-key-wrapped items:** the `wrap_format` field on the
  upload record already differentiates. `master-aes256gcm-v1` → unwrap with master key.
  `p256-ecdh-hkdf-aes256gcm-v1` → unwrap with sharing private key.

### Friendships

- **Formation:** automatic when `POST /api/auth/register` redeems an invite token.
  The server creates a `friendships` row linking inviter and new user. No explicit
  accept step.
- **Direction:** bidirectional. Both users see each other in their friends list.
- **Table:** `friendships (user_id_1, user_id_2, created_at)` with PK
  `(user_id_1, user_id_2)` and `CHECK (user_id_1 < user_id_2)` to prevent duplicate
  pairs. No delete path in M9 (unfriend deferred).
- **Backfill:** V22 migration creates friendship rows from redeemed invites already
  in the `invites` table.

### Recipient upload record

When a share is created, the server creates a new `uploads` row for the recipient:

| Column | Value |
|---|---|
| `id` | new UUID |
| `user_id` | recipient's user ID |
| `gcs_key` | same as original (blob not copied) |
| `content_type`, `content_hash`, `size_bytes`, `storage_class` | copied from original |
| `wrapped_dek` | DEK re-wrapped to recipient's sharing pubkey (from client) |
| `wrapped_thumbnail_dek` | thumbnail DEK re-wrapped similarly |
| `wrap_format` | `p256-ecdh-hkdf-aes256gcm-v1` |
| `rotation` | 0 (per-user) |
| `tags` | `{}` (per-user) |
| `uploaded_at` | `now()` (share time) |
| `last_viewed_at` | NULL (so it appears in Just Arrived) |
| `shared_from_upload_id` | original upload ID (FK, nullable) |
| `shared_from_user_id` | original owner's user ID (FK, nullable) |

GCS access requires no change: the server looks up by the recipient's upload ID, which
passes the existing `user_id = authenticated_user` check. The GCS key happens to point
to the same blob; the server doesn't distinguish.

### Pending blob / orphan cleanup

Shared upload records reference GCS objects they don't own. The lazy compost cleanup
(which deletes the GCS object when the last owner-side record is composted) must be
updated in M9 to check for surviving shared references before deleting a blob.
Specifically: before deleting a GCS object, check that no `uploads` row with the same
`gcs_key` and a non-null `composted_at IS NULL` exists for any user.

### Share flow (Android)

1. User taps share icon on thumbnail or in photo detail.
2. Friend picker sheet opens (fetches `GET /api/friends`).
3. User selects friend.
4. App:
   - Unwraps item DEK and thumbnail DEK using `VaultSession.masterKey`.
   - Fetches recipient's sharing pubkey: `GET /api/keys/sharing/{friendUserId}`.
   - Re-wraps both DEKs using `p256-ecdh-hkdf-aes256gcm-v1` to the recipient's pubkey.
   - Posts `POST /api/content/uploads/{uploadId}/share` with wrapped DEKs and
     `to_user_id`.
5. Server creates recipient's upload record (must be friends; 403 otherwise).
6. Toast: "Shared with [friend display name]."

---

## Server changes (new endpoints)

| Method | Path | Purpose |
|---|---|---|
| `PUT` | `/api/keys/sharing` | Upload own sharing pubkey + wrapped privkey |
| `GET` | `/api/keys/sharing/me` | Fetch own sharing key (for vault unlock) |
| `GET` | `/api/keys/sharing/{userId}` | Fetch a friend's sharing pubkey |
| `GET` | `/api/friends` | List friends `[{userId, username, displayName}]` |
| `POST` | `/api/content/uploads/{id}/share` | Share an upload with a friend |

### V22 migration

```sql
-- Sharing keypairs
CREATE TABLE account_sharing_keys (
    user_id        UUID PRIMARY KEY REFERENCES users(id),
    pubkey         TEXT NOT NULL,
    wrapped_privkey TEXT NOT NULL,
    wrap_format    TEXT NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Friendships
CREATE TABLE friendships (
    user_id_1  UUID NOT NULL REFERENCES users(id),
    user_id_2  UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id_1, user_id_2),
    CHECK (user_id_1 < user_id_2)
);

-- Backfill friendships from redeemed invites
INSERT INTO friendships (user_id_1, user_id_2, created_at)
SELECT
    LEAST(invited_by_user_id, redeemed_by_user_id),
    GREATEST(invited_by_user_id, redeemed_by_user_id),
    redeemed_at
FROM invites
WHERE redeemed_by_user_id IS NOT NULL
ON CONFLICT DO NOTHING;

-- Shared upload provenance columns
ALTER TABLE uploads
    ADD COLUMN shared_from_upload_id UUID REFERENCES uploads(id) ON DELETE SET NULL,
    ADD COLUMN shared_from_user_id   UUID REFERENCES users(id)   ON DELETE SET NULL;
```

---

## Android changes

### E1 — Sharing keypair + friends list

- `HeirloomsApi`: add `putSharingKey`, `getSharingKeyMe`, `getSharingKey(userId)`,
  `getFriends`.
- `VaultSession`: add `sharingPrivateKey` field; load from server during vault unlock
  (`getSharingKeyMe`, unwrap with master key).
- Lazy migration: on vault unlock, if `GET /api/keys/sharing/me` returns 404, generate
  a new P-256 keypair, wrap privkey with master key, upload via `PUT /api/keys/sharing`.
- New `FriendsScreen` composable (Burger → Friends): lists friends by display name +
  username. No actions in M9 beyond display.
- Wire `FriendsScreen` into `BurgerPanel` and `AppNavigation`.

### E2 — Item sharing UI + received items

- `HeirloomsApi`: add `shareUpload(uploadId, toUserId, wrappedDek, wrappedThumbnailDek)`.
- `Upload` model: add `sharedFromUserId: String?` and `sharedFromDisplayName: String?`
  fields (display name resolved client-side from the cached friends list).
- `VaultCrypto`: add `unwrapDekWithSharingKey(wrappedDek, sharingPrivateKey)` and
  `wrapDekForSharingPubkey(dek, pubkeyBytes)`.
- `EncryptedThumbnail` / full-size decrypt: check `wrap_format`; route to sharing key
  unwrap path when `p256-ecdh-hkdf-aes256gcm-v1`.
- Garden thumbnail: add share icon overlay (alongside existing tag icon at bottom-left).
  Tapping opens `ShareSheet` (friend picker).
- `ShareSheet`: coroutine fetches friend's pubkey, re-wraps DEKs, calls `shareUpload`,
  shows toast on success.
- Photo detail: show "Shared by [display name]" attribution line when
  `sharedFromDisplayName != null`.
- Just Arrived: shared items land naturally (no code change needed — `last_viewed_at`
  is NULL at share time). Add a small person/friend icon on the thumbnail overlay for
  items where `sharedFromUserId != null`.

### E3 — Android plot management

Server endpoints already exist (create, update, delete, batch reorder).

- `HeirloomsApi`: add `createPlot(name, tagCriteria)`, `updatePlot(id, name,
  tagCriteria)`, `deletePlot(id)`.
- `GardenViewModel`: add `createPlot`, `renamePlot`, `deletePlot` methods with
  optimistic updates.
- Garden UI: "Add plot" button at bottom of the plot list (or inline after last row).
  Each plot row header gains an edit icon (pencil) that opens a `PlotEditSheet`
  (rename field + delete button with confirmation).

---

## Files touched

**Server:**
- New: `SharingKeyHandler.kt`, `FriendsHandler.kt`
- Modified: `PlotHandler.kt` (share endpoint), `AuthHandler.kt` (create friendship on
  register), `Database.kt`, `Server.kt` (route wiring)
- New migration: `V22__sharing_friends.sql`

**Android:**
- Modified: `HeirloomsApi.kt`, `VaultSession.kt`, `VaultCrypto.kt`
- Modified: `AppNavigation.kt`, `BurgerPanel.kt` (Friends entry)
- Modified: `GardenScreen.kt`, `GardenViewModel.kt`
- Modified: `HeirloomsImage.kt` (sharing key decrypt path)
- Modified: `PhotoDetailScreen.kt` (attribution)
- New: `FriendsScreen.kt`, `ShareSheet.kt`, `PlotEditSheet.kt`
