# SE Brief: M8 E5 — Fixup Pass Before Test and Deploy

**Date:** 11 May 2026
**Milestone:** M8 — Multi-user access
**Increment:** E5 of 5 (fixup)
**Type:** Server + Web + Android. Final pass before staging test and production deploy.

---

## Goal

A single consolidated pass that resolves issues spotted during PA review of the
E2/E3/E4 briefs, closes test-coverage gaps in the isolation suite, adds the
cross-platform recovery verification test that proves the end-to-end protocol
hangs together, and tidies the documentation and deploy hygiene so M8 ships
cleanly. After E5, M8 is ready for tester onboarding on staging and then
production deploy.

E5 touches small slices across all three layers rather than being scoped to a
single layer. Each section below is independently committable.

---

## Section 1 — `GET /api/auth/me` (server)

A lightweight endpoint that both clients use to validate a cached session
token without producing side effects, replacing the "attempt first authenticated
request and handle 401" pattern. Web uses this on mount to decide between
restoring a session and redirecting to `/login`; Android uses it after reading
`session_token` from SharedPreferences to confirm validity before any other
network activity.

**Handler:** authenticated. Reads `userId` from the request context (set by
`SessionAuthFilter` in E2). Returns the user's public-safe profile.

Response 200:
```json
{
  "user_id":      "<uuid>",
  "username":     "bret",
  "display_name": "Bret"
}
```
Response 401: invalid or expired session token (handled by the middleware, not
the handler).

**Tests:** add to the auth integration suite.
1. Authenticated → returns the calling user's row.
2. Unauthenticated → 401.
3. Expired session → 401 (advance `expires_at` in test).
4. Two-user isolation: Alice's token returns Alice's row; Bob's token returns
   Bob's row (covered by the broader isolation suite but worth a dedicated test).

**Wire-up:** add to web `App.jsx` mount handler and Android `MainActivity`
start-up branch, replacing the implicit-401-probe pattern in both clients.

---

## Section 2 — Web: persist pairing keys across refresh (web)

This is the only flag from the PA review with real architectural weight. After
a successful pairing, the web client has a session token in `localStorage` and
the master key in memory — but the WebCrypto private key used to unwrap that
master key was non-extractable and is lost on page refresh. The user has a
valid session but no way to decrypt any encrypted content, with no recovery
path (they paired *because* they don't know the passphrase on this browser).

### Fix

Persist the pairing keypair and the wrapped master key in IndexedDB. WebCrypto
non-extractable keys can be `structuredClone`d into IDB without ever becoming
extractable; this is the standard pattern for surviving refreshes without
weakening the key.

Add a small module `webPairingStore.js`:

```
async function savePairingMaterial({ privateKey, publicKeyRaw, wrappedMasterKey, wrapFormat })
async function loadPairingMaterial(): { privateKey, publicKeyRaw, wrappedMasterKey, wrapFormat } | null
async function clearPairingMaterial()
```

IDB layout: one object store `pairing` with a single record keyed `'current'`.

### Boot order on app mount

The mount handler in `App.jsx` becomes:

1. If no `heirlooms_session_token` in localStorage → `/login`.
2. Call `GET /api/auth/me` → if 401, clear everything and `/login`.
3. If `loadPairingMaterial()` returns non-null → use its `privateKey` to unwrap
   `wrappedMasterKey` → master key in memory → ready.
4. If `loadPairingMaterial()` returns null → the session is valid but vault is
   unreachable. Show a small banner: "This browser needs to be unlocked again."
   With two actions: "Enter passphrase" (full login flow) or "Pair with phone"
   (`/access/pair`). Do not auto-logout — the session is fine, only the vault
   needs re-unlocking.

### Pairing flow update

In the existing pairing flow (E3 section "/access/pair"), after a successful
`state: "complete"` response from `/api/auth/pairing/status`:

1. Receive `wrapped_master_key` + `session_token`.
2. Unwrap master key using the in-memory private key.
3. **New:** call `savePairingMaterial(...)` with the keypair and the
   wrapped blob.
4. Store session token in localStorage.
5. Hold master key in memory; navigate to `/`.

### Logout

`logout()` clears localStorage *and* calls `clearPairingMaterial()`. Don't leave
key material behind on logout.

### Password-login refreshes also benefit

The same persistence pattern should be used after a successful passphrase
login: the device's wrapped master key (retrieved from `GET /api/keys/device`)
and the device's keypair (generated at register / regenerated as needed) are
stored via the same mechanism. This avoids the same "valid session, no master
key after refresh" trap on password-login users.

If the password-login path doesn't currently regenerate a per-browser keypair
on each fresh-browser login, treat that as part of this section: each
password-login on a browser without existing pairing material should:
1. Generate a fresh keypair.
2. Register it as a new device under the user's account (existing
   `wrapped_keys` flow from M7 — needs a small "add device by re-wrapping from
   passphrase" endpoint if one doesn't exist).
3. Persist the keypair via `savePairingMaterial`.

If that endpoint doesn't exist (it probably doesn't — see Section 5), this
becomes interlocked with that work.

### Tests

Add to vitest:
1. After successful pairing → reload simulation (clear in-memory state, recall
   mount handler) → master key is recovered from IDB and the app boots normally.
2. After logout → IDB is cleared; subsequent mount has no pairing material.
3. Session expires server-side (mock 401 from `/auth/me`) → IDB is cleared and
   user is redirected to `/login`.

---

## Section 3 — Android: lock first-run detection and migration UX (android)

The E4 brief had two issues to resolve.

### First-run detection: settle on local SharedPreferences

The E4 brief listed two formulations of the detection logic. Use only the local
one:

```
- session_token present       → normal launch
- session_token absent,
  api_key   present           → Bret migration (setup-existing)
- session_token absent,
  api_key   absent             → first run (invite redemption)
```

Drop any reference to probing `wrapped_keys` via an auth-less endpoint. The
local check is correct and avoids needing a new unauthenticated probe.

### Bret migration username field

Lock the username field on the migration screen to `"bret"` (read-only, not
editable). The migration is one-time and the username already exists in V21's
seed row. Asking Bret to "choose" a username he can't actually choose is
confusing; showing it read-only is honest about the situation.

The screen should read roughly: "Username: bret" (greyed out, non-interactive)
+ "New passphrase" + "Confirm passphrase".

### QR library — lock the decision and document Fire OS implications

If ML Kit Barcode Scanning was chosen during E4, verify it doesn't break the
Fire OS path (Kindle Fire devices have no Play Services). If it does, switch
to ZXing (`com.journeyapps:zxing-android-embedded`) which has no Google
Services dependency. Document the chosen library in `PA_NOTES.md` under
"Things to always remember" so future work doesn't accidentally swap it.

If ZXing is already chosen, no code change — just the documentation note.

---

## Section 4 — Test coverage gaps in the isolation suite (server)

The E2 two-user isolation test list has three gaps. Add tests for each.

### Wrapped keys isolation (~4 tests, critical)

`wrapped_keys` rows contain a user's wrapped master key per device. Cross-user
reads of these rows are a data-confidentiality leak even if the wrapped blob is
useless without the private key. The E2 brief added user filtering to the
queries; add tests to prove it works.

1. Bob's `GET /api/keys/device/<aliceDeviceId>` → 404.
2. Bob's `GET /api/keys/devices` returns only his own device(s), not Alice's.
3. Bob's `POST /api/keys/devices` (add device) sets `user_id = bobUserId`, not
   Alice's.
4. Bob's `DELETE /api/keys/device/<aliceDeviceId>` (retire) → 404; Alice's row
   is untouched.

### Recovery passphrase isolation (~2 tests)

1. Bob's `GET /api/keys/recovery` returns Bob's blob (or 404 if none), never Alice's.
2. Bob's `PUT /api/keys/recovery` writes against `user_id = bobUserId`; Alice's
   row is unchanged.

### Diagnostic events scoping (~2 tests + a small schema change)

The E2 brief says `diagnostic_events` is "server-wide telemetry — no user
scoping in M8." This is a problem: events from the Android client contain file
paths and device info (e.g., the FreeTime file path reported on KFRAPWI in
v0.32.0). After E2 turns authentication on, any authenticated user could call
`GET /api/diag` and read every other user's diagnostic events. That's a real
privacy leak.

Minimum fix:
- Add migration V22 adding a `user_id UUID NULL REFERENCES users(id)` column to
  `diagnostic_events`. Existing rows stay NULL (pre-M8 data, no owner). New
  rows set `user_id` to the authenticated user.
- `POST /api/diag` sets `user_id` from the request context.
- `GET /api/diag` filters by `user_id = :userId`. Users see only their own events.
  Bret-as-admin viewing all events is out of scope for M8 — defer the admin
  UI to a later milestone.

Tests:
1. Bob's `GET /api/diag` returns only Bob's events.
2. Bob's `POST /api/diag` writes a row with `user_id = bobUserId`.

---

## Section 5 — Cross-platform recovery verification (test only, possibly fix)

This is the most important verification step before deploy. The E1 brief
introduces `master_key_seed` (the second half of the Argon2id output) but
doesn't fully specify what writes to `recovery_passphrase` during `/register`
or `/setup-existing`, and the `/register` payload doesn't carry a
passphrase-wrapped master key. The login flow described in the E3 brief
("use `master_key_seed` to unwrap the `wrapped_master_key` blob from
`GET /api/keys/device`") is mistyped — a 32-byte symmetric key can't unwrap an
asymmetric (P-256 ECDH) envelope. So there's at least a documentation bug, and
possibly a real protocol gap.

This needs verification, not speculation. The acceptance test below proves
whether the implementation is sound; if it fails, that points to the gap.

### The verification test

A new integration test that runs end-to-end across server + a JS implementation
of the web crypto path (the existing vitest setup has the WebCrypto primitives
needed).

Scenario:
1. User Alice registers via `POST /api/auth/register` from a simulated browser:
   generates keypair, derives auth + master-key-seed, generates master key,
   wraps for the keypair, posts to register.
2. Alice's session is established; she uploads one encrypted file.
3. Alice logs out (calls `/api/auth/logout`, server deletes session).
4. The simulated browser clears all client state (localStorage, IDB, in-memory).
5. Alice logs in fresh: `POST /api/auth/challenge` → `POST /api/auth/login`.
6. Alice can decrypt her uploaded file.

If step 6 succeeds, the recovery model is sound. If it fails, the gap is real.

### Likely fix if the test fails

The cleanest correction is to extend `/register` and `/setup-existing` to also
accept a passphrase-wrapped master key:

```
"wrapped_master_key_recovery": "<base64url bytes — master key wrapped
                                 under master_key_seed via aes256gcm-v1>",
"wrap_format_recovery":        "master-aes256gcm-v1"
```

Server stores these as the `recovery_passphrase` row (replacing any existing
row for that user). Fresh-browser login then unwraps via:
- `POST /api/auth/login` → session
- `GET /api/keys/recovery` → recovery blob
- `master_key_seed` unwraps recovery blob → master key
- (Optional, recommended) generate a new keypair for this browser, register it
  as a device, persist via `webPairingStore` (Section 2).

### If the verification test passes as-is

Then the protocol works in some way I haven't reconstructed from the briefs.
Document the actual recovery flow in `PA_NOTES.md` under the "M8 Multi-user"
section so the next session has it written down.

---

## Section 6 — Deploy hygiene

A short list of mechanical items the SE should knock off before the deploy.

### Cloud Run config

The current `heirlooms-server` deploy passes `--set-secrets
API_KEY=heirlooms-api-key:latest`. After E2, the `ApiKeyFilter` is removed
and `API_KEY` is dead config. Two reasonable options:

- **Drop the binding.** Remove `--set-secrets API_KEY=...` from the deploy
  command in PA_NOTES. Leave the secret in Secret Manager (it costs nothing
  and we may want it back for an admin tool).
- **Leave the binding, ignore the value.** Less hygienic but lower-risk if
  there's any chance of partial rollback.

Recommendation: drop the binding. Update the deploy command in PA_NOTES to
match. The old secret value can be deleted from Secret Manager after the
deploy is verified stable.

### Bret-window during deploy

Between the moment the new server is live and the moment Bret runs through
`setup-existing` on his Android device, his old API key no longer authenticates
anything. Practical implication: Bret should run the migration flow within
minutes of the deploy completing — not days later. If something goes wrong
between deploy and setup-existing, the recovery is to redeploy the prior
revision; nothing on Bret's device is broken.

Document this in the deploy checklist. Don't reinstall the Android app between
deploy and `setup-existing` running successfully — a fresh `device_id` won't
match any `wrapped_keys` row, and Bret would be locked out of his own data
until M9's account-recovery flows.

### Migration order verification

V20 + V21 (+ V22 if Section 4 adds it) run via Flyway in order. Verify on a
fresh Testcontainers database that the full sequence applies cleanly with no
manual intervention.

### Documentation updates

- `PA_NOTES.md` — bump "Current version" to v0.38.0 (or whatever the E1 ship
  version was; verify against VERSIONS.md).
- `PA_NOTES.md` — add the QR library decision under "Things to always remember".
- `PROMPT_LOG.md` — add session entries for E1 (already shipped, missing
  entry), E2, E3, E4. Standard format.
- `ROADMAP.md` — leave M8-shipped marker for after deploy verification, not
  in E5.

---

## Section 7 — Cosmetic / housekeeping

Catch-all for tiny items.

- E2 brief test 8 of upload isolation: typo "compostsr" → "composts". Cosmetic;
  fix if and only if the test name in code reflects the typo. If the SE chose
  better names, ignore.
- Verify the web `qrcode` npm dependency is locked in `package-lock.json`
  rather than installed ad-hoc. Same check applies to whatever Android QR
  library was chosen (Section 3).
- Ensure all new screens (Android Devices & Access, Bret migration, login,
  invite redemption, Web `/access/pair`, `/join`, `/access`) handle orientation
  change correctly (Android) or browser refresh (web) without losing state.
  The ViewModel + SavedStateHandle pattern is already in place on Android;
  state derived from in-memory React for web should survive refresh on routes
  that are recoverable, and prompt for re-entry on routes that are not.

---

## What E5 does NOT include

- Any new product surface area beyond the items above.
- Admin tooling for diagnostic events (deferred — Section 4 only adds scoping,
  not an admin UI).
- A passphrase-change flow (deferred from E3/E4 already).
- A "manage logged-in devices" list (deferred).
- Multi-recipient capsules or contacts picker (M9).

---

## Acceptance criteria

1. `./gradlew test` passes — all new tests green, no regressions from E1–E4.
2. All vitest web tests pass — including the new IDB-persistence tests.
3. `GET /api/auth/me` works for both clients and is wired into mount/launch
   handlers in place of implicit-401 probes.
4. Web pairing survives a refresh: the post-pairing state is fully recovered
   from IDB without re-entering a passphrase or re-pairing.
5. Android first-run detection matches the locked formulation (local
   SharedPreferences only); Bret migration screen shows username locked to
   `"bret"`.
6. The two-user isolation suite covers `wrapped_keys`, `recovery_passphrase`,
   and `diagnostic_events` in addition to the E2 surfaces.
7. The cross-platform recovery verification test (Section 5) passes; if it
   required a `/register` and `/setup-existing` payload extension, that
   extension is in place and documented.
8. Cloud Run deploy command in PA_NOTES is updated; the dead `API_KEY` secret
   binding is removed.
9. PA_NOTES current-version line matches VERSIONS.md head; PROMPT_LOG has
   entries for E1–E5.

---

## Documentation updates

- `docs/PA_NOTES.md` — current version bump, QR library decision, recovery flow
  documentation (per Section 5 outcome), updated deploy command.
- `docs/VERSIONS.md` — E5 entry when E5 ships (likely the same release that
  closes M8, since E5 is a fixup pass rather than a separate user-visible ship).
- `docs/PROMPT_LOG.md` — standard entry, plus catch-up entries for any of
  E1–E4 that don't already have one.
- `docs/ROADMAP.md` — M8 shipped marker after deploy verification (not in E5).