# ARCH-015 — API Stability Contract

*Authored: 2026-05-17. Status: approved design document.*
*Prerequisite reading: `docs/briefs/ARCH-010_m11-api-surface-and-migration-sequencing.md`.*

---

## Context

App development (Android versionCode 59, web, iOS scaffold) is frozen at the current
feature set while M11 and M12 backend work proceeds on the `M11` branch. The apps
cannot be updated in lockstep with the server. Server changes must not silently break
these frozen clients.

This brief defines the frozen API surface, the stability policy governing changes to
it, the versioning mechanism, the enforcement test suite, and a wave-by-wave
compatibility assessment of M11 changes against frozen clients.

---

## 1. Frozen API Surface

The following endpoints are active in the v0.56 server and are called by at least one
frozen client (Android versionCode 59, web, or iOS scaffold). The full list was
enumerated by reading `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/routes/`.

### Auth (`/api/auth/...`)

| Method | Path | Frozen client |
|--------|------|---------------|
| POST | `/api/auth/challenge` | Android, web, iOS |
| POST | `/api/auth/login` | Android, web, iOS |
| POST | `/api/auth/logout` | Android, web |
| GET | `/api/auth/me` | Android, web, iOS |
| GET | `/api/auth/account` | Android, web |
| PATCH | `/api/auth/account` | Android (biometric gate) |
| GET | `/api/auth/invites` | Android, web |
| POST | `/api/auth/register` | Android, web |
| POST | `/api/auth/invites/{token}/connect` | Android, web |
| POST | `/api/auth/pairing/initiate` | Android |
| POST | `/api/auth/pairing/qr` | web |
| POST | `/api/auth/pairing/complete` | Android |
| GET | `/api/auth/pairing/status` | web |
| DELETE | `/api/auth/devices/{deviceId}` | Android, web |

### Keys (`/api/keys/...`)

| Method | Path | Frozen client |
|--------|------|---------------|
| POST | `/api/keys/devices` | Android, web (pairing) |
| GET | `/api/keys/devices` | Android, web |
| DELETE | `/api/keys/devices/{deviceId}` | Android, web |
| PATCH | `/api/keys/devices/{deviceId}/used` | Android, web |
| GET | `/api/keys/passphrase` | Android, web |
| PUT | `/api/keys/passphrase` | Android, web |
| DELETE | `/api/keys/passphrase` | Android, web |
| POST | `/api/keys/link/initiate` | Android |
| POST | `/api/keys/link/{linkId}/register` | web |
| GET | `/api/keys/link/{linkId}/status` | Android, web |
| POST | `/api/keys/link/{linkId}/wrap` | Android |
| PUT | `/api/keys/sharing` | Android, iOS |
| GET | `/api/keys/sharing/me` | Android, iOS |
| GET | `/api/keys/sharing/{userId}` | Android, iOS |

### Uploads (`/api/content/...`)

| Method | Path | Frozen client |
|--------|------|---------------|
| POST | `/api/content/upload` | Android |
| GET | `/api/content/uploads` | Android, web |
| GET | `/api/content/uploads/tags` | Android |
| GET | `/api/content/uploads/hash/{hash}` | Android |
| GET | `/api/content/uploads/{id}` | Android, web |
| GET | `/api/content/uploads/{id}/file` | Android, web |
| GET | `/api/content/uploads/{id}/thumb` | Android, web |
| GET | `/api/content/uploads/{id}/preview` | Android, web |
| GET | `/api/content/uploads/{id}/url` | Android, web |
| POST | `/api/content/uploads/prepare` | Android, web |
| POST | `/api/content/uploads/initiate` | Android, web |
| POST | `/api/content/uploads/resumable` | Android |
| POST | `/api/content/uploads/confirm` | Android, web |
| POST | `/api/content/uploads/{id}/migrate` | Android |
| POST | `/api/content/uploads/{id}/compost` | Android, web |
| POST | `/api/content/uploads/{id}/restore` | Android, web |
| POST | `/api/content/uploads/{id}/share` | Android |
| PATCH | `/api/content/uploads/{id}/rotation` | Android |
| POST | `/api/content/uploads/{id}/view` | Android |
| PATCH | `/api/content/uploads/{id}/tags` | Android |
| GET | `/api/content/uploads/{id}/capsules` | Android |
| GET | `/api/content/uploads/composted` | Android, web |

### Capsules (`/api/...`)

| Method | Path | Frozen client |
|--------|------|---------------|
| POST | `/api/capsules` | Android, web |
| GET | `/api/capsules` | Android, web |
| GET | `/api/capsules/{id}` | Android, web |
| PATCH | `/api/capsules/{id}` | Android, web |
| POST | `/api/capsules/{id}/seal` | Android, web |
| POST | `/api/capsules/{id}/cancel` | Android, web |

**Note on the existing `/api/capsules/{id}/seal` endpoint:** the v0.56 seal route is a
simple state transition that requires only that the capsule has at least one upload. It
writes no crypto columns. This endpoint will be **replaced** by the M11 seal handler
(see section 5). The transition requires care — see Wave 5 assessment below.

### Plots (`/api/...`)

| Method | Path | Frozen client |
|--------|------|---------------|
| GET | `/api/plots` | Android, web |
| POST | `/api/plots` | Android, web |
| PUT | `/api/plots/{id}` | Android, web |
| DELETE | `/api/plots/{id}` | Android, web |
| PATCH | `/api/plots` | Android, web |
| GET | `/api/plots/{id}/staging` | Android, web |
| POST | `/api/plots/{id}/staging/{uploadId}/approve` | Android, web |
| POST | `/api/plots/{id}/staging/{uploadId}/reject` | Android, web |
| DELETE | `/api/plots/{id}/staging/{uploadId}/decision` | Android, web |
| GET | `/api/plots/{id}/staging/rejected` | Android, web |
| GET | `/api/plots/{id}/items` | Android, web |
| POST | `/api/plots/{id}/items` | Android, web |
| DELETE | `/api/plots/{id}/items/{uploadId}` | Android, web |
| GET | `/api/plots/{id}/plot-key` | Android, web |
| GET | `/api/plots/{id}/members` | Android, web |
| POST | `/api/plots/{id}/members` | Android, web |
| POST | `/api/plots/{id}/invites` | Android, web |
| GET | `/api/plots/join-info` | Android, web |
| POST | `/api/plots/join` | Android, web |
| GET | `/api/plots/{id}/members/pending` | Android, web |
| POST | `/api/plots/{id}/members/pending/{inviteId}/confirm` | Android, web |
| DELETE | `/api/plots/{id}/members/me` | Android, web |
| POST | `/api/plots/{id}/leave` | Android, web |
| POST | `/api/plots/{id}/accept` | Android, web |
| POST | `/api/plots/{id}/rejoin` | Android, web |
| POST | `/api/plots/{id}/restore` | Android, web |
| POST | `/api/plots/{id}/transfer` | Android, web |
| PATCH | `/api/plots/{id}/status` | Android, web |
| GET | `/api/plots/shared` | Android, web |

### Trellises (`/api/...`)

| Method | Path | Frozen client |
|--------|------|---------------|
| GET | `/api/trellises` | Android, web |
| POST | `/api/trellises` | Android, web |
| PUT | `/api/trellises/{id}` | Android, web |
| DELETE | `/api/trellises/{id}` | Android, web |
| GET | `/api/trellises/{id}/staging` | Android, web |
| GET | `/api/flows` *(deprecated alias)* | Android |
| POST | `/api/flows` *(deprecated alias)* | Android |
| PUT | `/api/flows/{id}` *(deprecated alias)* | Android |
| DELETE | `/api/flows/{id}` *(deprecated alias)* | Android |
| GET | `/api/flows/{id}/staging` *(deprecated alias)* | Android |

### Social (`/api/...`)

| Method | Path | Frozen client |
|--------|------|---------------|
| GET | `/api/friends` | Android, web, iOS |

### Diagnostics (`/api/...`)

| Method | Path | Frozen client |
|--------|------|---------------|
| POST | `/api/diagnostics/events` | Android, web |
| GET | `/api/diagnostics/events` | Android |

### Infrastructure

| Method | Path | Frozen client |
|--------|------|---------------|
| GET | `/health` | OpsManager / load balancer |
| GET | `/api/settings` | Android, web |

**Total frozen endpoints: approximately 80 routes.**

---

## 2. Stability Policy

### 2.1 Permitted changes (backwards-compatible, no client impact)

The following changes may be made to the frozen surface at any time without breaking
frozen clients:

- **New endpoints.** Adding entirely new paths or methods on new paths. Frozen clients
  do not call them and are unaffected.
- **New optional response fields.** Adding a JSON field to any response body that
  frozen clients parse. Clients that do not recognise the field will ignore it (Jackson
  `FAIL_ON_UNKNOWN_PROPERTIES=false` is the standard Android/web behaviour). Applies
  only when the client uses a lenient JSON deserialiser — verify this is true for all
  three platforms before relying on it.
- **New optional request fields.** Adding a field to a request body that the server
  reads but does not require. Server must default gracefully when the field is absent.
- **Internal refactors.** Changes to service, repository, or domain code that do not
  alter any HTTP request shape, response structure, status code mapping, or auth flow.
- **New nullable columns in database.** V31 and V32 migrations are additive — they add
  nullable columns and new tables. These do not affect the response shape of existing
  endpoints unless the server code is changed to include them.
- **Performance and observability changes.** Logging, metrics, tracing additions that
  do not touch the wire protocol.

### 2.2 Prohibited changes (breaking, must not land in M11 unless compatibility-gated)

The following changes to any frozen endpoint are **prohibited without an explicit
compatibility strategy** (see section 2.3):

- **Remove or rename an endpoint.** Removing a path or changing its method or path
  template breaks any frozen client that calls it.
- **Remove or rename a required request field.** A field the frozen app always sends
  being rejected as unknown or interpreted differently is a breaking change.
- **Remove or rename a response field.** A field the frozen app always reads disappearing
  silently produces null-dereference crashes or wrong behaviour.
- **Change a field's type.** String → integer, array → object, etc.
- **Change a status code mapping.** A response that was 200 becoming 422, or a 404
  becoming 403, breaks client-side error handling.
- **Change the auth flow.** Any modification to how `X-Api-Key` is validated, how
  session tokens are issued, or how challenge/login works, breaks all frozen clients.
- **Change envelope format contracts.** The `p256-ecdh-hkdf-aes256gcm-v1` envelope
  format used in `wrapped_master_key`, `wrapped_dek`, and all crypto blobs is frozen.
  Any change to the binary wire format is a breaking change for all clients that hold
  encrypted material.
- **Tighten validation on existing fields.** Making a previously-optional field required,
  or narrowing an accepted value range, rejects requests that frozen clients send.
- **Change capsule `shape`/`state` semantics** in ways that alter the existing
  state-machine transitions the frozen apps exercise.

### 2.3 Handling unavoidable breaking changes

If a breaking change is genuinely required (e.g. a security fix that cannot be made
backwards-compatible), the process is:

1. **Flag to the CTO before landing.** The CTO determines whether to unfreeze the apps,
   implement a server-side compatibility shim, or accept the breakage.
2. **Implement a shim if possible.** Run old and new behaviour in parallel behind a
   version header or by detecting the request shape (e.g. presence/absence of a new
   required field).
3. **Document in this brief.** Update section 5 with the specific endpoint, the nature
   of the breaking change, and the chosen compatibility strategy.
4. **Add a regression test that verifies the old behaviour still works** before removing
   the shim.

---

## 3. Versioning Recommendation

### Recommendation: no URL versioning; strict no-breaking-change policy enforced by tests

**Recommended approach:** maintain the current unversioned API (`/api/...`) and enforce
the no-breaking-change policy exclusively through the frozen-client regression test
suite described in section 4. No `X-Heirlooms-API-Version` header, no `/api/v2/` URL
prefix.

**Rationale:**

1. **Scale.** At current scale (two founding users, one test environment), the overhead
   of maintaining parallel API versions would cost more developer time than it saves.
   Version headers and URL namespacing add routing complexity, documentation debt, and
   potential for subtle drift between versions.

2. **Frozen-app constraint is time-bounded.** The apps are frozen until M12 ships, not
   forever. Once M12 is complete the apps will be updated and the freeze lifts. The
   stability window is 2–4 months, not a multi-year compatibility commitment.

3. **M11 additions are genuinely additive.** As section 5 demonstrates, the only M11
   endpoint that modifies existing behaviour is `GET /api/capsules/{id}` (Wave 7 —
   new nullable fields added to an existing response) and `PUT /api/capsules/{id}/seal`
   (which becomes the M11 seal handler). The seal endpoint change requires a
   compatibility strategy (see Wave 5 assessment) but does not require versioning.

4. **Tests provide a stronger guarantee than versioning.** A version header can be
   present but incorrect. Regression tests that simulate the exact request shapes
   frozen apps send will catch breakage on every commit, regardless of what version
   header is (or is not) present.

**If the situation changes** — e.g. if frozen app versions must coexist with updated
apps in production simultaneously for an extended period — revisit this recommendation
and consider an `X-Heirlooms-API-Version` request header with server-side routing by
version. Do not implement URL versioning at this scale; it creates permanent divergence
in path-space that is expensive to maintain.

---

## 4. Enforcement: Frozen Client Integration Tests

The following 8 request/response pairs are the highest-risk regression points for
frozen clients. Each should be an integration test in `HeirloomsServer/src/test/`
using Testcontainers (following the `SharingFlowIntegrationTest.kt` pattern), or at
minimum an in-process handler test against `buildApp()`.

The suite is named `FrozenClientRegressionTest.kt` and must be tagged so it runs on
every M11 commit as part of CI.

### FC-01 — Challenge + Login (auth flow)

**Why critical:** All three frozen clients authenticate using this two-step flow. Any
change to either endpoint breaks login globally.

**Request shape to assert:**
```
POST /api/auth/challenge  { "username": "<test-user>" }
  → 200 { "salt": "<base64url>" }

POST /api/auth/login  { "username": "<test-user>", "auth_key": "<base64url>" }
  → 200 { "token": "<string>", "user_id": "<uuid>", "expires_at": "<iso8601>" }
```

**Assertions:** status 200 on both; `token` is a non-empty string; `user_id` is a
valid UUID; `salt` is a non-empty base64url string. The `token` from login can
authenticate subsequent requests via `X-Api-Key`.

### FC-02 — Register (invite + account creation)

**Why critical:** New user onboarding is exercised by the web app. The request body
includes 11 required fields; tightening any of them breaks registration.

**Request shape to assert:**
```
GET  /api/auth/invites  X-Api-Key: <founder-token>
  → 200 { "token": "<invite-token>", "expires_at": "<iso8601>" }

POST /api/auth/register  {
  "invite_token": "<token>", "username": "<string>", "display_name": "<string>",
  "auth_salt": "<base64url>", "auth_verifier": "<base64url>",
  "wrapped_master_key": "<base64url>", "wrap_format": "<string>",
  "pubkey_format": "<string>", "pubkey": "<base64url>",
  "device_id": "<string>", "device_label": "<string>", "device_kind": "<string>"
}
  → 201 { "token": "<string>", "user_id": "<uuid>", "expires_at": "<iso8601>" }
```

**Assertions:** 201 status; response contains `token`, `user_id`, `expires_at`.

### FC-03 — Upload initiate + confirm (encrypted E2EE flow)

**Why critical:** The primary data ingestion path for Android. Used for every media
upload. The `storage_class: "encrypted"` branch carries all the E2EE field names
that must not change.

**Request shape to assert:**
```
POST /api/content/uploads/initiate  { "mimeType": "image/jpeg", "storage_class": "encrypted" }
  → 200 { "storageKey": "<string>", "uploadUrl": "<string>",
           "thumbnailStorageKey": "<string>", "thumbnailUploadUrl": "<string>" }

POST /api/content/uploads/confirm  {
  "storageKey": "<string>", "mimeType": "image/jpeg", "fileSize": 1024,
  "storage_class": "encrypted", "envelopeVersion": 1,
  "wrappedDek": "<base64>", "dekFormat": "p256-ecdh-hkdf-aes256gcm-v1",
  "thumbnailStorageKey": "<string>", "wrappedThumbnailDek": "<base64>",
  "thumbnailDekFormat": "p256-ecdh-hkdf-aes256gcm-v1"
}
  → 201
```

**Assertions:** `initiate` returns 200 with all four keys; `confirm` returns 201;
`GET /api/content/uploads` subsequently lists the upload with the correct `storageKey`
and `mimeType`.

### FC-04 — Upload list (pagination contract)

**Why critical:** The garden screen paginated list is the main UI surface on Android
and web. The response shape drives the entire media grid.

**Request shape to assert:**
```
GET /api/content/uploads?limit=50
  → 200 { "uploads": [...], "next_cursor": "<string or null>" }
```

**Assertions:** 200 status; response contains `uploads` array; each item has `id`,
`storageKey`, `mimeType`, `fileSize`, `uploadedAt`; `next_cursor` is present (may be
null). The exact field list should be pinned against the current `toJson()` output in
`UploadRecord`.

### FC-05 — Sharing flow (friend + re-wrap upload)

**Why critical:** The core sharing feature. `SharingFlowIntegrationTest.kt` already
covers this; the frozen-client regression suite should import its assertions directly
or reuse the same helper methods. Specifically:

```
POST /api/content/uploads/{id}/share  {
  "toUserId": "<uuid>", "wrappedDek": "<base64>",
  "wrappedThumbnailDek": "<base64>", "dekFormat": "p256-ecdh-hkdf-aes256gcm-v1"
}
  → 201 { ... upload record for recipient ... }
```

**Assertions:** 201 status; recipient can subsequently `GET /api/content/uploads/{id}`
and retrieve the re-wrapped DEK fields; `GET /api/content/uploads?is_received=true`
returns the shared upload.

### FC-06 — Capsule create and list

**Why critical:** Capsule CRUD is used on Android and web. The `shape`, `unlock_at`,
`recipients`, and `upload_ids` fields are required in the current request; their
presence must continue to be the correct contract.

**Request shape to assert:**
```
POST /api/capsules  {
  "shape": "open", "unlock_at": "<iso8601>",
  "recipients": ["<display-name>"], "upload_ids": ["<uuid>"]
}
  → 201 { "id": "<uuid>", "shape": "open", "state": "open", ... }

GET /api/capsules?state=open,sealed
  → 200 { "capsules": [ ... ] }
```

**Assertions:** 201 with `id` UUID; 200 with `capsules` array; each capsule summary
includes `id`, `shape`, `state`, `unlock_at`.

### FC-07 — Capsule seal (pre-M11 shape)

**Why critical:** This is the most sensitive transition in M11. The v0.56 seal
endpoint (`POST /api/capsules/{id}/seal`) accepts no body and seals by state
transition only. The M11 seal endpoint (`PUT /api/capsules/{id}/seal`) is a different
method on the same path, and carries a mandatory crypto body.

**The frozen client sends `POST /api/capsules/{id}/seal` with no body.**

**Assertion the regression test must make:**
```
POST /api/capsules/{id}/seal   (no body, X-Api-Key: <token>)
  → 200 { "id": "<uuid>", "shape": "sealed", "state": "sealed", ... }
       OR
  → 409 { "error": "capsule cannot be sealed in its current state" }  (already sealed)
       OR
  → 422 { "error": "Cannot seal an empty capsule" }  (no uploads)
```

Status codes 200, 409, and 422 must all remain the same. The `shape: "sealed"` and
`state: "sealed"` fields in the 200 response must remain present.

See Wave 5 in section 5 for the full compatibility analysis of the M11 seal changes.

### FC-08 — Sharing key registration and retrieval

**Why critical:** The sharing keypair is the foundation of all E2EE recipient wrapping.
iOS registers its sharing key on first launch; Android retrieves a friend's sharing key
before sharing. The `PUT /api/keys/sharing` idempotency contract (409 if already
exists) must be preserved.

**Request shape to assert:**
```
PUT /api/keys/sharing  { "pubkey": "<base64>", "wrappedPrivkey": "<base64>",
                          "wrapFormat": "p256-ecdh-hkdf-aes256gcm-v1" }
  → 204   (first registration)
  → 409   (if already registered)

GET /api/keys/sharing/{friendUserId}  X-Api-Key: <requester-token>
  → 200 { "pubkey": "<base64>" }
  → 403   (not friends)
  → 404   (no key registered)
```

**Assertions:** 204 on first PUT; 409 on repeat PUT; GET returns 200 with `pubkey`
field containing a non-empty base64 string; 403 when caller and target are not friends.

---

### Running the suite

The regression test class must:

1. Use Testcontainers (`PostgreSQLContainer`) to spin up a real database.
2. Call `buildApp()` with `LocalFileStore` (following `SharingFlowIntegrationTest` pattern).
3. Run V1 through V30 Flyway migrations only (the pre-M11 schema). The frozen clients
   depend on the v0.56 schema, not the M11 schema.
4. Include a `@Tag("frozen-client")` annotation so CI can run this suite on every M11
   branch commit with `./gradlew test -t frozen-client`.

---

## 5. M11 Compatibility Assessment

The following table assesses each ARCH-010 wave against the frozen surface. For each
wave: does it touch any existing endpoint? If yes, is the change backwards-compatible?
If not, how to introduce it safely?

### Wave 0 — Schema prerequisites (V31 + V32 migrations, no endpoints)

**Modifies existing endpoints?** No.

**Compatibility:** Safe. V31 and V32 are purely additive migrations (new nullable
columns, new tables). The old server binary continues to operate correctly against the
new schema during the deployment window. Frozen clients never read the new columns
because no existing endpoint exposes them.

**One caveat:** the V31 backfill populates the `connections` table from `friendships`.
The `GET /api/friends` endpoint still reads from `friendships` directly and is
unaffected. The two tables coexist.

### Wave 1 — Connections bootstrap (5 new endpoints)

**Modifies existing endpoints?** No. All five endpoints (`GET/POST /api/connections`,
`GET/PATCH/DELETE /api/connections/:id`) are new paths not previously served.

**Compatibility:** Fully safe. Frozen clients do not call any connections endpoint.
The deprecated `GET /api/friends` endpoint continues to serve its existing contract
unchanged.

### Wave 2 — Executor nomination lifecycle (6 new endpoints)

**Modifies existing endpoints?** No. All six endpoints
(`POST/GET /api/executor-nominations`, `GET /api/executor-nominations/received`,
`POST /api/executor-nominations/:id/accept`, `…/decline`, `…/revoke`) are new.

**Compatibility:** Fully safe. No frozen client is aware of nominations.

### Wave 3 — Capsule recipient linking (1 new endpoint)

**Modifies existing endpoints?** No. `PATCH /api/capsules/:id/recipients/:recipientId/link`
is a new sub-path of the capsule namespace. It does not modify the response of
`GET /api/capsules/:id` or any other existing endpoint.

**Compatibility:** Fully safe.

### Wave 4 — Executor share distribution (3 new endpoints)

**Modifies existing endpoints?** No. `POST /api/capsules/:id/executor-shares`,
`GET /api/capsules/:id/executor-shares/mine`, and
`GET /api/capsules/:id/executor-shares/collect` are all new paths.

**Compatibility:** Fully safe.

### Wave 5 — Sealing endpoint (1 modified/replaced endpoint) — REQUIRES ATTENTION

**Modifies existing endpoints?** **YES.** This is the one wave in M11 that touches an
existing endpoint used by frozen clients.

**The conflict:**

- **v0.56 (frozen client):** `POST /api/capsules/{id}/seal` — no body; seals by state
  transition only; writes no crypto columns. Returns `200` with capsule detail JSON.
- **M11 design (ARCH-010 §1 Wave 5):** `PUT /api/capsules/{id}/seal` — full crypto
  body with `recipient_keys[]`, optional `tlock`, optional `shamir`; writes crypto
  columns atomically. Returns `200` with `{ capsule_id, shape, state, sealed_at }`.

**These are different HTTP methods on the same path.** The M11 `PUT` does not remove
the `POST`. However, if the M11 `POST` handler is left unchanged while the `PUT` is
added, frozen clients will continue to use the old `POST` seal — which writes no crypto
columns and advances `shape` to `sealed` without any key material. This is correct
behaviour for pre-M11 capsules (they have no crypto columns to populate).

**Compatibility strategy (recommended):**

1. **Keep the `POST /api/capsules/{id}/seal` handler exactly as it is** throughout
   M11. Do not change its request validation, response structure, or status codes.
   Pre-M11 capsules sealed by frozen clients use the old path and produce a sealed
   capsule with `wrapped_capsule_key IS NULL`. This is a valid pre-M11 sealed state.

2. **Add `PUT /api/capsules/{id}/seal` as the new M11 path.** The M11 Kotlin client
   (`TOOL-001`) and any future updated apps call `PUT`. The frozen apps never call it.

3. **Distinguish sealed capsule generations by `capsule_key_format`.** A capsule with
   `capsule_key_format IS NULL` was sealed by a frozen client (no crypto columns). A
   capsule with `capsule_key_format = "capsule-ecdh-aes256gcm-v1"` was sealed via M11
   `PUT /seal`. This distinction will be important for M12 delivery logic.

4. **Add to the frozen-client regression suite (FC-07 above):** assert that
   `POST /api/capsules/{id}/seal` returns the same status codes and response structure
   after M11 deploys.

**Is the change backwards-compatible as recommended?** Yes, with the POST-preserved
approach. The `PUT` is additive; the `POST` is unchanged.

**Open question for CTO:** Should pre-M11 sealed capsules (those sealed via `POST`
with no crypto columns) be deliverable after M12? If M12 delivery requires
`wrapped_capsule_key` to be non-null, pre-M11 sealed capsules will never be delivered.
A one-time re-sealing path (or a different delivery fallback) would be needed. Flag
this before M12 design begins.

### Wave 6 — tlock key delivery (1 new endpoint)

**Modifies existing endpoints?** No. `GET /api/capsules/{id}/tlock-key` is a new path.

**Compatibility:** Fully safe. Frozen clients do not know about tlock delivery. The
logging prohibition (ARCH-010 §5.3) applies to the new endpoint only and does not
affect any existing endpoint.

### Wave 7 — Capsule read-path amendments (1 modified, 1 new)

**Modifies existing endpoints?** **YES.** `GET /api/capsules/{id}` (existing) gains
new fields in its response. `GET /api/capsule-recipient-keys/{capsuleId}` is new.

**Compatibility of `GET /api/capsules/{id}` amendment:**

The M11 change adds the following fields to the capsule detail response:
`wrapped_capsule_key`, `capsule_key_format`, `tlock_round`, `tlock_chain_id`,
`tlock_wrapped_key`, `tlock_key_digest`, `shamir_threshold`, `shamir_total_shares`.

ARCH-010 states these are null-omitted or null in JSON for capsules that were not
sealed with M11. This means:

- For capsules sealed by frozen clients (pre-M11): all new fields are null or absent.
  The response shape is identical to v0.56.
- For capsules sealed by the M11 `PUT /seal`: new fields are populated.

**Frozen client behaviour with null/absent fields:** Both Android (Kotlin/Gson or Moshi
with `@SerializedName`) and web (JSON.parse or Axios with destructuring) will
either ignore unknown fields or treat them as undefined/null. This is backwards
compatible provided the frozen client's deserialiser does not throw on unknown fields.

**Verification required:** Confirm that `CapsuleDetail` on Android uses a lenient
deserialiser (Gson defaults to lenient; Moshi requires `@Json(ignore=true)` is not
set). Confirm that the web client does not use a strict schema validator against the
capsule response. iOS scaffold — same check for `Codable` (`CodingKeys` enum — unknown
keys are ignored by default in Swift's `JSONDecoder`). This is a low-risk assumption
but should be explicitly verified before M11 deploys.

**Compatibility verdict:** Backwards-compatible. New nullable fields added to an
existing response are universally safe given lenient deserialisers (the industry
standard). Enforce this via FC-06 regression test: ensure the test does NOT break when
the capsule response gains additional null fields.

**`GET /api/capsule-recipient-keys/{capsuleId}` (new):** Fully additive. Frozen clients
do not call it.

---

## 6. Summary Table

| Wave | Modifies existing endpoint? | Backwards-compatible? | Action required |
|------|-----------------------------|-----------------------|-----------------|
| 0 — Schema migrations | No | Yes | Pre-migration check (§4.6 of ARCH-010) |
| 1 — Connections CRUD | No | Yes | None |
| 2 — Executor nominations | No | Yes | None |
| 3 — Recipient linking | No | Yes | None |
| 4 — Executor share distribution | No | Yes | None |
| 5 — Sealing endpoint | Yes (POST → POST + PUT) | Yes, if POST preserved | Keep POST handler unchanged; add PUT as new path; FC-07 regression test |
| 6 — tlock key delivery | No | Yes | None |
| 7 — Read-path amendments | Yes (GET capsule gains nullable fields) | Yes (lenient deserialiser required) | Verify client deserialiser leniency; FC-06 regression test |

---

## 7. Open Questions for CTO

1. **Pre-M11 sealed capsules and M12 delivery:** capsules sealed by frozen clients via
   `POST /seal` have no `wrapped_capsule_key`. If M12 delivery requires this column,
   these capsules will not be deliverable. Should a re-sealing migration path be
   designed before M12?

2. **Deserialiser leniency verification:** Before M11 Wave 7 deploys, someone should
   explicitly verify that Android's `CapsuleDetail` data class and the web capsule
   response parsing do not throw on unknown JSON fields. This is a safe assumption but
   worth confirming as a one-time check, not an ongoing concern.

3. **Deprecated `/api/flows` aliases:** The `/api/flows/*` routes are deprecated aliases
   for `/api/trellises/*`. The Android versionCode 59 build may still call `/api/flows`.
   These aliases must not be removed during M11 or M12. Confirm removal is not planned
   until after the apps are unfrozen and updated.

4. **`GET /api/friends` vs `GET /api/connections`:** M11 adds `GET /api/connections` as
   the new connection list. The existing `GET /api/friends` is the frozen-client
   contract and must continue to read from the `friendships` table (or a compatible
   view). Confirm the M11 implementation does not route `GET /api/friends` through the
   new connections service.
