# SE Brief: M8 E2 — Server Enforcement

**Date:** 11 May 2026
**Milestone:** M8 — Multi-user access
**Increment:** E2 of 4
**Type:** Backend-only. No client changes.

---

## Goal

Wire the E1 auth infrastructure into every existing handler. After E2, every
request to a protected endpoint must carry a valid session token; requests without
one are rejected with 401. Every data-returning query is filtered by the
authenticated user's `user_id` — a user can never read or modify another user's
data. Cross-user probes return 404 (not 403 — a probing attacker must not learn
whether a resource exists). The non-negotiable correctness property for M8 is a
comprehensive two-user isolation test suite that covers every endpoint.

---

## Auth middleware

Replace the existing `ApiKeyFilter` (which checks a single shared API key from an
env var) with a `SessionAuthFilter` that:

1. Reads the `X-Api-Key` header value as a session token.
2. Computes `SHA256(token)` and looks up the matching `user_sessions` row.
3. Rejects with 401 if: no row found, or `expires_at < NOW()`.
4. On success: attaches `userId: UUID` and `deviceKind: String` to the request
   context (http4k `RequestContextLens`).
5. Refreshes the session: `UPDATE user_sessions SET last_used_at = NOW(),
   expires_at = NOW() + INTERVAL '90 days' WHERE id = <session_id>`.

The refresh update runs on every authenticated request. At M8's scale this is
acceptable; a caching layer can be added later if it becomes a hot path.

**Unauthenticated routes** (must be explicitly allowlisted in the filter):
- `POST /api/auth/challenge`
- `POST /api/auth/login`
- `POST /api/auth/setup-existing`
- `POST /api/auth/register`
- `POST /api/auth/pairing/qr`
- `GET  /api/auth/pairing/status`
- `GET  /docs/*` (Swagger UI)
- `GET  /health`

All other routes require authentication.

---

## Per-user query filtering

Every handler that reads or writes data must scope its queries to the authenticated
`user_id`. The changes below are exhaustive — every affected handler is listed.

### uploads

All `Database` methods that touch `uploads` must accept a `userId: UUID` parameter
and add `AND user_id = :userId` to every WHERE clause. Affected queries:

- `recordUpload` — INSERT: set `user_id = :userId`.
- `findUploadById` — add `AND user_id = :userId`; return `null` if not found
  (handler returns 404 — privacy-preserving).
- `listUploads` (all variants: cursor, filter, sort) — add `AND user_id = :userId`.
- `updateTags`, `updateRotation`, `setLastViewedAt`, `setExifProcessedAt`,
  `compostUpload`, `hardDeleteUpload` — add `AND user_id = :userId` to every
  UPDATE/DELETE; if `0 rows affected`, handler returns 404.
- `findByContentHash` (dedup guard) — add `AND user_id = :userId`.
- `getThumbnailKey`, `getStorageKey` (for GCS operations) — add `AND user_id = :userId`.

### plots

- `listPlots`, `getPlot`, `createPlot`, `updatePlot`, `deletePlot`,
  `reorderPlots` — add `AND owner_user_id = :userId` / set `owner_user_id = :userId`
  on INSERT.
- System plot seed (the `__just_arrived__` INSERT in V10) — already backfilled
  to Bret in V21. New users get their own system plot created at registration time
  (see registration handler below).

### capsules

- `createCapsule` — set `user_id = :userId` on INSERT.
- `getCapsule`, `listCapsules`, `sealCapsule`, `cancelCapsule`,
  `addPhoto`, `removePhoto`, `addMessage`, `listMessages` — add
  `AND user_id = :userId`; return 404 if not found.

### wrapped_keys

- `getWrappedKey`, `listDevices`, `addDevice`, `retireDevice` — add
  `AND user_id = :userId`.

### recovery_passphrase

- `getRecoveryPassphrase`, `setRecoveryPassphrase` — filter by `user_id = :userId`
  (the PK is now `user_id`; no separate WHERE clause needed for single-row ops).

### pending_blobs

`pending_blobs` tracks orphaned GCS uploads server-wide. No user scoping needed —
the cleanup job runs globally and uses `storage_key` for GCS deletion.

### diagnostic_events

`diagnostic_events` is server-wide telemetry. No user scoping in M8.

---

## System plot creation for new users

When `POST /api/auth/register` creates a new user, the registration handler must
also INSERT the `__just_arrived__` system plot for that user:

```sql
INSERT INTO plots (id, owner_user_id, name, sort_order, is_system_defined)
VALUES (gen_random_uuid(), :userId, '__just_arrived__', -1000, TRUE);
```

Add this INSERT to the registration transaction in `AuthHandler.kt`.

---

## Session expiry cleanup

Add a daily cleanup to the existing background job that purges orphaned blobs:

```sql
DELETE FROM user_sessions WHERE expires_at < NOW();
DELETE FROM pending_device_links WHERE expires_at < NOW();
DELETE FROM invites WHERE expires_at < NOW() AND used_at IS NULL;
```

---

## Two-user isolation test suite

This is the milestone's non-negotiable correctness property. The test setup creates
two users (Alice and Bob) via `POST /api/auth/register`, each with their own session
token. Every test checks that Alice cannot read or modify Bob's data.

The test helper `twoUserSetup()` returns `(aliceToken, aliceUserId, bobToken, bobUserId)`
using the existing Testcontainers setup.

**Upload isolation (~10 tests)**

1. Alice uploads a file; Bob's `GET /api/content/uploads` does not include it.
2. Bob calls `GET /api/content/uploads/:aliceUploadId` → 404.
3. Bob calls `PATCH /api/content/uploads/:aliceUploadId/tags` → 404.
4. Bob calls `POST /api/content/uploads/:aliceUploadId/compost` → 404.
5. Bob calls `GET /api/content/uploads/:aliceUploadId/url` → 404.
6. Bob calls `GET /api/content/uploads/:aliceUploadId/thumbnail` → 404.
7. Upload dedup: Alice and Bob upload identical content; each gets their own row
   (no cross-user dedup).
8. After Alice compostsr an upload, Bob's garden is unaffected.
9. Alice's `GET /api/content/uploads` returns only her own uploads (count check).
10. Unauthenticated `GET /api/content/uploads` → 401.

**Plot isolation (~6 tests)**

11. Alice creates a plot; Bob's `GET /api/plots` does not include it.
12. Bob calls `GET /api/plots/:alicePlotId` → 404.
13. Bob calls `PATCH /api/plots/:alicePlotId` → 404.
14. Bob calls `DELETE /api/plots/:alicePlotId` → 404.
15. Each user has exactly one `__just_arrived__` system plot; they are distinct rows.
16. Bob cannot reorder Alice's plots.

**Capsule isolation (~8 tests)**

17. Alice creates a capsule; Bob's `GET /api/capsules` does not include it.
18. Bob calls `GET /api/capsules/:aliceCapsuleId` → 404.
19. Bob calls `POST /api/capsules/:aliceCapsuleId/seal` → 404.
20. Bob calls `POST /api/capsules/:aliceCapsuleId/photos` → 404.
21. Bob calls `POST /api/capsules/:aliceCapsuleId/messages` → 404.
22. Bob calls `DELETE /api/capsules/:aliceCapsuleId` → 404.
23. Alice seals a capsule; Bob's capsule list is unaffected.
24. Photo added to Alice's capsule is not visible from Bob's capsule endpoints.

**Auth isolation (~5 tests)**

25. Bob's session token cannot call Alice's account endpoints.
26. Alice calls `POST /api/auth/logout` → her token is invalidated; subsequent
    requests with it → 401.
27. Bob's requests continue to work after Alice logs out.
28. Expired session token → 401 (advance `expires_at` via direct DB update in test).
29. Tampered session token (random bytes) → 401.

---

## What E2 does NOT include

- Client changes (E3 and E4).
- The invite generation UI or web pairing UI (E3).
- Android login screen or session storage (E4).

---

## Acceptance criteria

1. `./gradlew test` passes — all new isolation tests green, no regressions from E1.
2. Every protected endpoint returns 401 for an unauthenticated request.
3. Every isolation test passes: Alice cannot read or affect Bob's data anywhere.
4. All cross-user resource accesses return 404 (not 403).
5. Session refresh: after an authenticated request, `last_used_at` and `expires_at`
   are updated in `user_sessions`.
6. Unauthenticated routes (challenge, login, register, pairing/qr, pairing/status)
   continue to work without a token.

---

## Documentation updates

- `docs/VERSIONS.md` — entry when E2 ships
- `docs/PROMPT_LOG.md` — standard entry
