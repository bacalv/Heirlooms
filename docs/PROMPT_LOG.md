# Heirlooms — Prompt Log

---

## Session — 11 May 2026 — M9 deploy, test user cleanup, partial test plan

**M9 deployed to production.** Server revision `heirlooms-server-00042-qmx`. V23 migration ran cleanly:
`account_sharing_keys` and `friendships` tables created, friendship backfill from redeemed invites
ran correctly (Bret ↔ Sadaar friendship created). Two nullable columns added to `uploads`.

**Test user "Sadaar's Safari Browser" deleted.** A third user account had been accidentally created
during M8 web pairing testing. Deleted via Cloud SQL Proxy + psql using ADC credentials. The
`invites.used_by` FK required NULLing before the user row could be deleted (see SE_NOTES for the
full deletion order). Two users remain: Bret (founding user) and Sadaar (daughter's account).

**M9 partial test results:**
- ✅ Server health and V23 migration confirmed
- ✅ Friendship backfill: Bret sees Sadaar in Friends list and vice versa
- ✅ Item sharing end-to-end: Sadaar shared a photo from the Fire tablet to Bret's account
- ✅ Shared item appeared in Bret's Just Arrived with arrival animation
- ⏳ Friend indicator (person icon on thumbnail), "Shared by" attribution — not yet confirmed
- ⏳ Per-user rotation/tag independence — not yet confirmed
- ⏳ Plot management UI — not yet confirmed

**First human tester** (third-party, older Android phone) onboarding deferred to next session.

---

## Session — 11 May 2026 — v0.45.0: M9 friends, item sharing, Android plot management

Full M9 implementation. Server + Android only.

**Server changes:**
- V23 migration: `account_sharing_keys`, `friendships` tables; `shared_from_upload_id` / `shared_from_user_id` on `uploads`; friendship backfill from redeemed invites.
- `SharingKeyHandler.kt`: `PUT /api/keys/sharing`, `GET /api/keys/sharing/me`, `GET /api/keys/sharing/{userId}` (friends only).
- `FriendsHandler.kt`: `GET /api/friends`.
- `UploadHandler.kt`: `POST /api/content/uploads/{id}/share` creates recipient upload record with re-wrapped DEKs. Compost cleanup guards GCS deletion against live shared references.
- `AuthHandler.kt`: `createFriendship` called in register after invite is marked used. UUID ordering bug fixed (Kotlin `UUID <` uses signed-long order, PostgreSQL uses string lexicographic — fixed to use `toString()` comparison).

**Android changes:**
- `VaultCrypto`: `generateSharingKeypair()`, `unwrapWithSharingKey()`, `sec1ToSpki()` helper.
- `VaultSession`: `sharingPrivkey` field + `setSharingPrivkey()`.
- `HeirloomsApi`: plot CRUD (`createPlot`, `updatePlot`, `deletePlot`), sharing key endpoints, friends list, `shareUpload`. `Upload` model gets `sharedFromUserId` / `sharedFromDisplayName` / `isShared`.
- `FriendsScreen.kt` (new): Burger → Friends list with display name + username.
- `ShareSheet.kt` (new): friend picker, DEK re-wrap, share API call.
- `PlotSheets.kt` (new): `PlotCreateSheet` and `PlotEditSheet` with name field, tag criteria, delete confirmation.
- `GardenViewModel`: `ensureSharingKey()` (lazy sharing keypair init on vault unlock), `loadFriends()`, `createPlot()`, `renamePlot()`, `deletePlot()`, `enrichWithFriendNames()`.
- `GardenScreen`: share icon (bottom-right, encrypted+owned items), friend indicator (top-right, received items), edit pencil on plot headers, "+ Add plot" row, `PlotCreateSheet` wired.
- `HeirloomsImage`: shared-item thumbnail path unwraps DEK via `unwrapWithSharingKey` when `thumbnailDekFormat == p256-ecdh-hkdf-aes256gcm-v1`.
- `PhotoDetailScreen`: "Shared by [name]" attribution line in `GardenFlavour`.

---

## Session — 11 May 2026 — web: pairing code entry unreachable from login page

`PairPage` (the code-entry screen for Android-initiated pairing) was behind `RequireAuth` at `/access/pair`. A new browser session with no token could never reach it — and the login page had no link to it. Fix: added `/pair` as a public route in `App.jsx` (alongside `/login` and `/join`), and added a "Have a pairing code from the app? Pair with phone" link at the bottom of `LoginPage`. The existing `/access/pair` route stays for users who are already logged in and want to link a new browser.

---

## Session — 11 May 2026 — v0.44.0: M8 bugfix iteration 1

Post-M8 testing with sadaar (second user on Fire OS tablet) surfaced four Android UX bugs. Brief at `docs/briefs/M8_bugfix1_brief.md`.

**Bug 1 — Blank garden after "Go to Garden" (`AppNavigation.kt`).** `onGoToGarden` changed from `navigateToTab(Routes.GARDEN)` to `popBackStack()`. UPLOAD_PROGRESS is always pushed on top of GARDEN; `navigateToTab` was triggering save/restore state machinery unnecessarily, producing a blank white screen.

**Bug 2 — New Just Arrived item at position −1 (`GardenScreen.kt`).** `LaunchedEffect` in `PlotRowSection` rekeyed on `newlyArrivedIds` (the `Set`) instead of `shouldScrollToStart` (a `Boolean`). When a second item arrived while the set was already non-empty, the boolean didn't change so the scroll never re-fired.

**Bug 3 — Garden tab doesn't dismiss BurgerPanel (`AppNavigation.kt`).** `onTabSelected` non-Burger branch now calls `burgerSheetState.hide()` and sets `showBurger = false` before `navigateToTab()`. Nav stack was popping correctly but `showBurger` was never reset, so the sheet re-rendered on top of the garden.

**Bug 5 — Spinner instead of plant icon during thumbnail load (`HeirloomsImage.kt`).** `EncryptedThumbnail` loading branch replaced `CircularProgressIndicator` with `OliveBranchIcon` (24 dp, matching the failed state). Removed unused `CircularProgressIndicator` import.

**Bug 6 — + Video crashes on Fire OS (`GardenScreen.kt`).** `ActivityResultContracts.CaptureVideo()` passes `EXTRA_OUTPUT` (a pre-created FileProvider URI) to `ACTION_VIDEO_CAPTURE`. Fire OS camera apps don't honour this extra and crash. This was masked before v0.43.1 by the camera permission crash. Fix: replaced `CaptureVideo()` with `StartActivityForResult` + plain `ACTION_VIDEO_CAPTURE` (no output URI); URI retrieved from `result.data?.data` and passed through the existing `copyContentUriToCache` path with `isFile = true`.

versionCode 47→48, versionName 0.43.1→0.44.0.

---

## Session — 11 May 2026 — v0.43.1: Camera permission on Fire OS

**Camera permission crash on Fire OS (bug).** Adding `zxing-android-embedded` in v0.43.0 merged the `CAMERA` permission into the app manifest. Fire OS enforces declared permissions strictly — `TakePicture`/`CaptureVideo` in `GardenScreen.launchCamera()` crashed without a runtime grant. Photo worked fine on Bret's Android phone (confirmed), crash was Fire OS specific. Fix: added `cameraPermissionLauncher` (RequestPermission) in `GardenScreen`; `launchCamera()` checks `ContextCompat.checkSelfPermission` first and requests the permission if missing, otherwise launches the camera intent directly. Note: `PackageManager.PERMISSION_GRANTED` import added.

Also bumped versionCode 46→47, versionName 0.43.0→0.43.1.

---

## Session — 11 May 2026 — v0.43.0: Android bugfixes (QR scan + upload OOM)

Two bugs found during post-M8 testing with second user (sadaar).

**PairingScreen QR scan (bug: missing camera option).** `PairingScreen` showed "Scan the QR code" in its instructions but offered only a paste-JSON text field — the camera path was deferred with a TODO comment. Added `com.journeyapps:zxing-android-embedded:4.3.0` (no Google Play Services dependency, safe for Fire OS). `CAMERA` permission added to `AndroidManifest.xml`. `PairingScreen` now leads with a "Scan QR code" button using `rememberLauncherForActivityResult(ScanContract())`; a successful scan auto-submits via a shared `submit(json)` function. Paste-JSON field demoted to secondary (outlined button).

**Upload OOM — confirm never fires (bug: `Exception` catch misses `OutOfMemoryError`).** Investigation via GCS + Cloud Run logs: content (49 MB) and thumbnail landed in GCS at 11:51:03, but `POST /api/content/uploads/confirm` never appeared in server logs. Root cause: preview clip generation in `uploadEncryptedViaSigned` catches `Exception`; `OutOfMemoryError` is a `Throwable`/`Error` subclass and escaped, crashing the WorkManager CoroutineWorker (uncaught Throwable = permanent failure, not retry). Fix: both the outer block and `generatePreviewClip`'s inner catch changed to `catch (_: Throwable)`. `UploadWorker.doWork()` now wraps the uploader call in `try/catch (t: Throwable)` as a safety net so future unexpected errors go through the normal retry/failure path. Sadaar's lost upload (GCS objects will be cleaned up within 24h by pending_blobs job) requires a re-upload.

---

## Session — 11 May 2026 — v0.42.1: M8 deploy + first invite test

**Deploy.** M8 shipped to production in the following order: (1) Android APK installed on founding user's device via ADB. (2) Server deployed (`heirlooms-server-00041-rm4`) — Flyway applied V20→V22 cleanly. (3) Founding user completed `setup-existing` via `MigrationScreen` immediately after server came live — session token issued, api_key cleared, Garden confirmed loading. (4) Web deployed (`heirlooms-web-00054-tnj`).

**First invite test.** Invite generated on founding user's Android device (Devices & Access). Redeemed on a Fire OS tablet via `InviteRedemptionScreen`. Registration succeeded — new user landed on the Garden ("A garden begins with a single seed"). Multi-user confirmed working end-to-end.

**`InviteRedemptionScreen` UX fix (v0.42.1).** During testing, the registration form's submit button was hidden behind the keyboard in landscape mode on the Fire tablet. Fix: `verticalScroll` + `imePadding` on the Column so the button is always reachable; `ImeAction.Next` chains field focus in order; `ImeAction.Done` on the confirm field submits. Submit logic extracted to a local `submit()` function shared by button and keyboard action. Portrait mode was sufficient to complete the test so the fix ships as a follow-up patch.

---

## Session — 11 May 2026 — v0.42.0: M8 E5 — Fixup pass before deploy

Implements `docs/briefs/M8_E5_brief.md`. Server + Web + Android. M8 ready to deploy.

**Section 1 — `GET /api/auth/me` (server).** New contract route in `AuthHandler`. Reads `userId` from request context (set by `SessionAuthFilter`), fetches the user row, returns `{user_id, username, display_name}`. Added to `authRoutes()`. Tests: authenticated returns calling user's row; unauthenticated returns 401 (via filter); expired session returns 401. Also tested in `IsolationTest` to confirm Alice and Bob each see their own row.

**Section 2 — Web IDB pairing persistence.** New `webPairingStore.js` module: `savePairingMaterial`, `loadPairingMaterial`, `clearPairingMaterial` backed by IndexedDB (one-record store `pairing`, key `'current'`). WebCrypto keys survive `structuredClone` into IDB without becoming extractable. `App.jsx` gains a mount `useEffect` that validates the cached session token via `GET /api/auth/me`; on 401 clears IDB and session; on 200 calls `loadPairingMaterial()` and auto-unlocks the vault if material is present. `PairPage.jsx` calls `savePairingMaterial` after a successful pairing. `handleSignOut` calls `clearPairingMaterial` before clearing the session. `api.js` gains `authMe`. 3 vitest tests: pairing saves to IDB, mount recovers from IDB (vault unlocks, garden page renders), 401 clears IDB.

**Section 3 — Android MigrationScreen username lock.** Username initialised from `store.getUsername()` and shown read-only when the value is known (muted Forest15 border). First-run migration (username not yet in store) stays editable. No personal names hardcoded in source.

**Section 4 — Diagnostic events user scoping.** V22 migration adds `user_id UUID NULL REFERENCES users(id) ON DELETE SET NULL` to `diagnostic_events`. `Database.insertDiagEvent` and `listDiagEvents` accept `userId` param. `UploadHandler` passes `request.authUserId()` for both diag POST and GET. Isolation tests: Bob's `GET /api/diag` returns only Bob's events; Bob's `POST /api/diag` writes row with Bob's `user_id`.

**Section 5 — Recovery protocol fix.** Root cause: `master_key_seed` (32-byte symmetric) cannot unwrap a P-256 ECDH asymmetric envelope. Fix: `registerRoute` and `setupExistingRoute` accept optional `wrapped_master_key_recovery` + `wrap_format_recovery`; server stores them in `recovery_passphrase` (`master-aes256gcm-v1` format). Fresh-browser login: derive `master_key_seed` → `GET /api/keys/recovery` → decrypt blob → master key. Cross-platform recovery test added to `AuthHandlerTest`. `IsolationTest` gets 3 wrapped_keys tests + 1 recovery passphrase test.

**Section 6 — Deploy hygiene.** Removed `--set-secrets API_KEY=heirlooms-api-key:latest` from PA_NOTES Cloud Run deploy command (dead binding; `ApiKeyFilter` removed in E2). Updated PA_NOTES current version to v0.42.0. Documented founding user deploy window, QR library decision (ZXing — no Play Services dependency), and M8 recovery flow.

**Tests.** All 116 web tests pass. All server tests pass (integration suite covers isolation, auth, and diag event scoping).

---

## Session — 11 May 2026 — v0.41.0: M8 E4 — Android auth + Devices & Access

Implements `docs/briefs/M8_E4_brief.md`. Android-only. Milestone 8 complete.

**Auth migration.** `EndpointStore` gains session token, username, and auth_salt slots. `MainApp` detects three paths: session present → normal; legacy api_key present → `MigrationScreen` (calls `setup-existing`); neither → `InviteRedemptionScreen`. `LoginScreen` handles expired-session re-auth (challenge → Argon2id → login). `ShareActivity` and `UploadWorker` read from session token first.

**Crypto.** `VaultCrypto` gains `deriveAuthAndMasterKeys` (64-byte Argon2id, splits to auth_key + master_key_seed), `computeAuthVerifier` (SHA-256), `wrapMasterKeyForRecipient` (ECDH-HKDF-AES-GCM for any P-256 SPKI — used by PairingScreen), `toBase64Url`/`fromBase64Url` (pure JVM, avoids android.util.Base64 in unit tests).

**Devices & Access.** `DevicesAccessScreen` generates invite QR codes (ZXing) + Android share sheet, and shows a pairing code with instructions. `PairingScreen` accepts the web client's QR JSON, ECDH-wraps the vault master key to the web pubkey, and calls `pairingComplete`. `PairingQrParser` is a pure JVM utility for parsing `{session_id, pubkey}` JSON.

**CapsuleCreateViewModel.** New ViewModel with StateFlows for all form state. Validates recipient (required) and unlock date (must be future). `submit(api)` calls `createCapsule`. The existing `CapsuleCreateScreen` uses local state; the ViewModel is an independent addition (screens can be migrated later). Both compile and tests pass.

**Tests.** 8 auth unit tests + 5 capsule ViewModel tests = 13 new. Key challenge: `org.json.JSONObject` is an Android stub in JVM tests — resolved by adding `org.json:json` to testImplementation. ViewModel IO tests: `Thread.sleep(300)` + second `advanceUntilIdle()` needed after `server.takeRequest` to allow OkHttp to finish processing on the IO thread before the Main dispatcher continuation runs. All 117 Android tests pass.

---

## Session — 11 May 2026 — v0.40.0: M8 E3 — Web client auth

Implements `docs/briefs/M8_E3_brief.md`. Web-only.

**Session model.** `heirlooms_session_token` in localStorage replaces `heirlooms-apiKey`. `App.jsx` reads it on mount; `sessionToken` is the canonical context value with `apiKey` as an alias for backwards compat. `handleSignOut` calls `POST /api/auth/logout` before clearing state. `RequireAuth` checks `sessionToken`.

**Auth flows.** `LoginPage` implements challenge → Argon2id → login → vault auto-unlock (via device private key from IndexedDB if registered). `JoinPage` (`/join`) handles invite registration: P-256 keypair generation, master key wrapping, `POST /api/auth/register`; shows 409/410 inline errors. `PairPage` (`/access/pair`) implements QR pairing: code entry, ephemeral P-256 keypair, QR code display, 1-second poll, ECDH master key unwrap on completion.

**`AccessPage`** (`/access`) provides invite generation (copy-to-clipboard URL + expiry) and a link to the pairing flow. Added as "Access" entry in Nav.

**`api.js`** additions: `authChallenge`, `authLogin`, `authLogout`, `authRegister`, `getInvite`, `pairingQr`, `pairingStatus`. **`vaultCrypto.js`** additions: `unwrapMasterKeyForDevice`, `toB64url`, `fromB64url`, `sha256`.

**Tests.** 10 new vitest tests in `auth.test.jsx`. Key testing challenge: `vi.useFakeTimers` conflicts with `waitFor`'s internal setTimeout retry loop — resolved by running polling tests with real timers and extended timeouts. All 113 web tests pass.

---

## Session — 11 May 2026 — v0.39.0: M8 E2 — Per-user enforcement + isolation tests

Implements `docs/briefs/M8_E2_brief.md`. Server-only.

**`SessionAuthFilter`.** New http4k `Filter` (`SessionAuthFilter.kt`) wraps the entire app. Reads `X-Api-Key` header as a session token, hashes it with SHA-256, and looks up the session in the DB. Unauthenticated paths (`/challenge`, `/login`, `/setup-existing`, `/register`, `/pairing/qr`) are allowlisted and pass through. Any other route without a valid, non-expired token returns 401. On success, injects `X-Auth-User-Id: <uuid>` into the request before forwarding. Also calls `database.refreshSession()` to update `last_used_at`. `Request.authUserId()` extension function reads the header and falls back to `FOUNDING_USER_ID` so existing unit tests that bypass the filter continue to work.

**Per-user DB scoping.** `Database.kt` gains user-scoped variants of all read methods: `findUploadByIdForUser`, `findByContentHash`, `listUploadsPaginated`, `listCompostedUploadsPaginated`, `listAllTags`, `compostUpload`, `restoreUpload`, `updateRotation`, `updateTags`, `recordView`, capsule CRUD, plot CRUD, and keys CRUD — all now accept `userId: UUID` and add `AND user_id = ?` to WHERE clauses. All handler routes (`UploadHandler`, `CapsuleHandler`, `PlotHandler`, `KeysHandler`) read `request.authUserId()` and forward it.

**Wiring.** `Main.kt` replaces `apiKeyFilter` with `sessionAuthFilter(database)`. `PendingBlobsCleanupService` adds a `deleteExpiredSessions()` coroutine. `registerRoute` in `AuthHandler` calls `database.createSystemPlot(newUser.id)` after user creation.

**Isolation tests.** `IsolationTest` uses Testcontainers to spin up a real PostgreSQL instance, registers two users (Alice + Bob) via the auth API, and runs 23 tests verifying that uploads, plots, capsules, and sessions belonging to one user are invisible to the other. Addressed test-ordering hazard: the logout test now uses a throwaway registered user rather than the shared `aliceToken`, so Alice's session remains valid for all other tests.

All 269 server tests pass.

---

## Session — 11 May 2026 — v0.38.0: M8 E1 — Schema + Auth API

Implements `docs/briefs/M8_E1_brief.md`. Server-only. No client changes.

**Migrations.** V20 creates `users`, `user_sessions`, and `invites` tables. Session tokens are 32 raw bytes (base64url); only `SHA256(token)` is persisted. Sessions expire 90 days from `last_used_at`. V21 seeds the founding user (`id = 00000000-0000-0000-0000-000000000001`), backfills `user_id` onto `uploads`, `capsules`, `plots`, `wrapped_keys`, and `recovery_passphrase`, then tightens all those columns to `NOT NULL` with FK constraints. `recovery_passphrase` drops its `id` sentinel column and makes `user_id` the primary key. `pending_device_links` gains `user_id`, `web_session_id`, `raw_session_token`, and `session_expires_at` for the M8 QR pairing flow.

**Auth endpoints** (`/api/auth/*`). `POST /challenge` returns the stored `auth_salt` for known usernames; returns a deterministic fake salt (HMAC-SHA256 of server secret + username, truncated to 16 bytes) for unknown ones — no enumeration. `POST /login` verifies `SHA256(auth_key)` against `users.auth_verifier`. `POST /setup-existing` is a one-time path for the founding user to set their passphrase after the M8 deploy — gated on `auth_verifier IS NULL` and a matching `device_id` in `wrapped_keys`. `GET /invites` generates a 48-hour invite token. `POST /register` redeems an invite, creates a user + `wrapped_keys` row, issues a session. `POST /logout` deletes the calling session. Pairing flow: `POST /pairing/initiate` (authenticated Android, generates 8-digit numeric code) → `POST /pairing/qr` (web enters code, gets `session_id`) → `POST /pairing/complete` (Android wraps master key, creates web session) → `GET /pairing/status` (web polls, receives session token + wrapped key when ready).

**Bridge pattern.** All existing DB INSERT methods gain an optional `userId: UUID = FOUNDING_USER_ID` parameter so they continue to work after V21's NOT NULL constraints land. E2 replaces the default with the authenticated user's ID. `recovery_passphrase` queries updated to use `user_id` PK (old `id = 1` sentinel removed). `listPlots()` updated from `owner_user_id IS NULL` to `owner_user_id = FOUNDING_USER_ID`.

**Tests.** 8 schema canary tests (Testcontainers) verify V20/V21 shape and constraints. 20 auth endpoint integration tests cover the full auth lifecycle including the complete pairing flow. All 246 server tests pass.

---

A record of the key decisions and prompts from the founding development session
(April 2026). Each entry captures the original intent, what was built, and any
important context or tradeoffs discovered along the way.

---

## Session — 2026-05-10 — v0.35.0: Web encrypted video MSE streaming

Implements Fix 2 from `docs/briefs/web_encrypted_video.md`. Web-only change.

**Problem.** Fix 1 (v0.34.0) made large encrypted videos playable but required the entire file to be downloaded before playback could start.

**What shipped.** `src/crypto/encryptedVideoStream.js` (new) + `PhotoDetailPage.jsx` update. `mp4box` added as a dependency (`^2.3.0`, dynamically imported → Vite code-splits it to a separate 32 KB gzip chunk). `aesGcmDecryptWithAad` promoted from private to exported in `vaultCrypto.js`.

**Approach — faststart detection then two paths:**

`openEncryptedVideoStream` downloads and decrypts chunk 0 first, then feeds a copy to a mp4box sniffer instance. For faststart MP4 (moov at the beginning, before mdat), mp4box fires `onReady` synchronously during that `appendBuffer` call, providing the codec string. For non-faststart (moov at the end), `onReady` does not fire.

- **Faststart path** (`type: 'mse'`): create a `MediaSource`, return its URL immediately. A background `streamFaststart` function appends chunk 0 (which already contains `ftyp + moov + start of mdat`) to a `SourceBuffer`, then continues fetching and appending remaining chunks as they arrive. MSE accepts regular faststart MP4 as a contiguous byte stream; playback starts after the first chunk is buffered.
- **Non-faststart path** (`type: 'blob'`): download all remaining chunks, decrypt them all, combine into a single `Uint8Array`, wrap in a `Blob`, return a blob URL. Same latency as Fix 1, but correct (Fix 1 still covers small files and images).

**Why not mp4box segmentation API.** The initial implementation used `setSegmentOptions` / `initializeSegmentation` / `start` / `onSegment` to produce fMP4 segments. This failed because `initializeSegmentation()` internally calls `resetTables()` which deletes `trak.samples`. `start()` then finds no samples and `onSegment` never fires — SourceBuffer receives only the init segment, video plays black with no playback. Also: mp4box v2 `initializeSegmentation()` returns `{ tracks, buffer }` (not an iterable array), and there is no `default` export (module exports `createFile` directly).

**Cleanup.** `openEncryptedVideoStream` returns `{ type: 'mse', msUrl, cleanup() }` or `{ type: 'blob', blobUrl }`. For MSE, `cleanup()` calls `abort.abort()` (cancels in-flight fetches) and `URL.revokeObjectURL(msUrl)`. `PhotoDetailPage` handles both return types and passes the `cleanup` function to the `useEffect` return value.

**Faststart in practice.** Not all Android camera recordings are faststart — some older devices (and certain encoders) write the moov at the end. Those correctly fall through to the blob path and play after full download. Faststart videos get true progressive playback.

---

## Session — 2026-05-10 — v0.34.0: Web encrypted video playback fix

Implements Fix 1 from `docs/briefs/web_encrypted_video.md`. Web-only change.

**Problem.** `PhotoDetailPage.jsx` always called `decryptSymmetric` for encrypted uploads. For files > 10 MB the server stores streaming-format ciphertext (`[nonce(12)][ct+tag]` 4 MiB chunks, produced by `encryptAndUploadStreamingContent`). `decryptSymmetric` calls `parseSymmetricEnvelope` which expects the first byte to be `0x01` (envelope version); the streaming format's first byte is the first byte of the nonce (an ASCII character from the upload ID prefix, never `0x01`). The parse threw, `blobUrl` was never set, and large encrypted videos showed as blank spinners.

**Fix.** Added `decryptStreamingContent` to `vaultCrypto.js`. It walks the ciphertext in 4 MiB strides, reading `nonce(12)` then `ct+tag` for each chunk, and decrypts with `aesGcmDecryptWithAad` (nonce == AAD, matching the upload path). A local `aesGcmDecryptWithAad` helper was also added (the encrypt counterpart already existed). In `PhotoDetailPage.jsx`, the `decryptSymmetric` call in `loadContent` is now preceded by a format check: `encBytes[0] === 0x01` → envelope path (unchanged); otherwise → streaming path.

**Test.** Added test 15 to `vaultCrypto.test.js`: constructs a single-chunk streaming blob manually (encrypt with `aesGcmEncryptWithAad`, prepend nonce), calls `decryptStreamingContent`, asserts plaintext round-trips. All 103 web tests pass.

---

## Session — 2026-05-10 — v0.33.0: Streaming encryption for large files

Implements the brief at `docs/briefs/streaming_encryption.md`. Three-platform change: server, Android, web.

**Server.** New endpoint `POST /api/content/uploads/resumable` added to `UploadHandler.kt`. Accepts `storageKey`, `totalBytes`, `contentType`; returns `resumableUri`. `GcsFileStore` implements `initiateResumableUpload` via a direct HTTP POST to the GCS JSON upload API using service account credentials (scoped, refreshed via `OAuth2Credentials.refreshIfExpired()`). The method returns the GCS `Location` header. `DirectUploadSupport` interface gains the method with a default that throws `NotImplementedError`, so `S3FileStore` needs no change. Also fixed a pre-existing test failure: `POST /uploads/initiate` with `storage_class: "public"` now explicitly returns 400 (the test expected this but the validation was missing).

**Android.** `uploadEncryptedViaSigned` in `Uploader.kt` restructured: `/initiate` is now the first network call (before any file reads). After `/initiate`, the function branches on `file.length() > 10 MB`:
- *Large file path*: calls `/resumable` to get a session URI, then `encryptAndUploadStreaming` which reads and encrypts the file in 4 MB chunks. Each chunk: deterministic 12-byte nonce (`uploadIdPrefix[4] + chunkIndex[8 big-endian]`) + AES-256-GCM with matching AAD + PUT to GCS resumable URI with `Content-Range` header. GCS returns 308 for intermediate chunks and 200 for the last.
- *Small file path*: existing in-memory encrypt+PUT via signed URL, unchanged.
`VaultCrypto.aesGcmEncryptWithAad` added with optional `length` parameter to avoid copying the last partial chunk from the reusable plaintext buffer. `MultiByteArrayRequestBody` avoids a third CHUNK_SIZE allocation by writing header + nonce + ciphertext in sequence. Peak memory ~8 MB (2× CHUNK_SIZE) regardless of file size.

**Web.** `encryptAndUpload` in `GardenPage.jsx` restructured identically: `/initiate` first, then size branch (same 10 MB threshold, same chunk format). `encryptAndUploadStreamingContent` uses `file.slice()` + `arrayBuffer()` per chunk (no full-file buffer). Chunks PUT via `fetch` with `Content-Range`. `aesGcmEncryptWithAad` added to `vaultCrypto.js` using WebCrypto `additionalData`. `initiateResumableUpload` added to `api.js`.

**Chunk format.** `[4-byte big-endian header (= CHUNK_SIZE)]` prepended to the first chunk only. The header value `0x00400000` (4 MB) is astronomically unlikely to appear at the start of a legacy monolithic ciphertext (which begins with a random 12-byte IV envelope header), so it doubles as a format discriminator for the eventual streaming decrypt path. Decrypt path is deferred per the brief — all existing uploads remain decryptable via the legacy path.

---

## Session — 2026-05-09 — Web: Just arrived fixes + thumbnail cache

Three bugs in the webapp's Garden / Just arrived view, mirroring issues previously
fixed in the Android app:

**30-second polling.** `GardenPage` had `plotRefreshKey` wired correctly but nothing
ever incremented it on a timer. Added a `setInterval` (30 000 ms) in a `useEffect`
that bumps the key; all plot rows silently re-fetch on each tick.

**Arrival animation.** `OliveBranchArrival` existed but was never used in the Garden.
Added `knownIdsRef` (a `useRef`, seeded on initial load) and `newlyArrivedIds` state
to `PlotItemsRow`. On each silent refresh, items not seen in the previous fetch are
added to `newlyArrivedIds`; each matching `PlotThumbCard` renders an `OliveBranchArrival`
overlay (88% parchment background, `withWordmark=false`, `pointer-events-none`) that
dismisses itself after the 3 s animation. Animation-only for the Just arrived system
plot — user-defined plot rows refresh silently without animation. `OliveBranchArrival`'s
`useEffect` now depends on `[]` (runs once on mount) with a `onCompleteRef` kept in
sync on every render, so per-tile inline callbacks no longer restart the animation.

**Thumbnail cache.** New `src/thumbCache.js`: module-level in-memory LRU Map (300
Blob entries, oldest evicted) backed by the browser's Cache API (500 entries,
`heirlooms-thumbs-v1`). `getThumb(uploadId, fetchUrl, apiKey)` returns a fresh
object URL on each call — callers own it and revoke it. Memory layer eliminates
re-fetches when navigating away and back within a session; Cache API layer survives
page reloads (equivalent to Coil's 50 MB disk cache on Android). Used in both
`PlotThumbCard` (GardenPage) and `Thumb` (PhotoGrid / Explore).

Deployed to Cloud Run (heirlooms-web).

---

## Session — 2026-05-09 — Web: Garden tile actions (preview, pencil, compost)

**Image preview modal.** Clicking an image thumbnail in the Garden previously
navigated directly to the photo detail page. Changed to open a `QuickImageModal`
(full-size image, max 75vh, loading spinner) so the user stays in context.
`onImagePreview` prop threaded through `PlotItemsRow` → `SystemPlotRow` /
`SortablePlotRow` → `GardenPage`. Follows the same pattern as `onVideoPlay` /
`QuickVideoModal` already in place for videos.

**Pencil / detail button.** A hover-reveal pencil icon added at `top-1 right-7`
(immediately left of the tag button) on every Garden tile — images and videos.
Uses `useNavigate` directly in `PlotThumbCard` to avoid an extra prop. Videos
now have a path to the detail page without having to avoid the player; images
have a path to detail without going through the preview modal.

**Compost button on Garden tiles.** Hover-reveal trash icon at `bottom-1 left-1`
(earth/70 background) on eligible tiles. Clicking raises a `ConfirmDialog`
("Compost this item? / Keep it") before any action is taken. On confirm, POSTs
to `/api/content/uploads/:id/compost` and triggers a silent plot re-fetch to
remove the item. API errors shown inline in the dialog. Button is hidden when
`upload.tags?.length > 0` — items with tags cannot be composted, consistent with
the `compostDisabled` check on the photo detail page. Capsule membership is not
checked on the tile (no data available); the API rejects those cases and the error
surfaces in the dialog.

---

## Session — 2026-05-09 — Web: Explore nav fix, video playback, video badge

**Explore → photo detail redirected via login screen.**
`ExploreGrid` used a plain `<a href="...">` instead of React Router's `<Link>`.
The native anchor caused a full page reload, wiping `useState(null)` for `apiKey`
in `App.jsx`. `RequireAuth` saw `null` and bounced to `/login`. Fixed by replacing
with `<Link to="..." state={{ upload }}>` (client-side nav, API key preserved;
router state passed for fast first paint in `PhotoDetailPage`). `ExploreThumb` was
also missing the `getThumb` cache — fixed in the same change.

**Video never loads in photo detail.**
Two bugs: (1) `displayUrl` used `thumbnailKey` if present — for videos this produced
a JPEG thumbnail blob, not playable video. (2) Downloading the full file as a blob
before setting `<video src>` means a large file fully buffers before anything plays.
Fix mirrors `QuickVideoModal` in `GardenPage`: try `/api/content/uploads/:id/url`
first (signed GCS URL — browser streams and seeks natively), fall back to a full
blob download only if that fails. Images keep the existing thumbnail path.

**Video indicator badge on Explore thumbnails.**
`ExploreThumb` now renders a small play-arrow badge (forest/75% alpha, bottom-right
corner, top-left rounded) when `mimeType` starts with `video/`. The parent `<Link>`
already has `relative overflow-hidden` so the badge clips cleanly to the tile
boundary. Consistent with the Garden tile video badges added in v0.25.9 on Android.

---

## Session — 2026-05-09 — v0.26.1: Explore filter tags use TagInputField

The Explore filter sheet's Tags section was still using `FilterChip` toggles
from v0.25.8. Replaced with `TagInputField` — now consistent with the Garden
quick-tag sheet and photo detail. `RecentTagsStore` used for suggestions.
`InputChip` import restored (used by the active-filter summary chips, not by
the Tags section that was replaced).

---

## Session — 2026-05-09 — v0.26.0: Unified TagInputField + staged photo detail edits

**New shared `TagInputField` composable** (`ui/common/TagInputField.kt`).
Chips for existing tags (× to remove) + inline `BasicTextField` + suggestion list
below. When input is empty: last 5 from `RecentTagsStore` (labelled "recent").
When typing: filtered from `availableTags`, falling back to `recentTags` when
`availableTags` is empty (share flow). Duplicate prevention; invalid-input error
line; `rememberSaveable` for text input. Replaces all previous per-screen tag UI.

**Staged changes in photo detail** (`PhotoDetailViewModel`).
`stageTags()` and `stageRotate()` buffer changes locally. `saveChanges()` is
`suspend` — patches tags and/or rotation then clears staged state. `isDirty:
StateFlow<Boolean>` derived via `combine()`. `BackHandler` in `PhotoDetailScreen`
intercepts both the system back gesture and the top-bar label; calls
`RecentTagsStore.record()` for newly added tags then `vm.saveChanges()` before
`onBack()`. Navigation waits for the save to complete.

**Rotation in photo detail — both flavours, images only.** `MediaArea` now takes
an explicit `rotation: Int` (effective = `stagedRotation ?: upload.rotation`).
`RotateRight` button added to `ExploreFlavour` (Garden already had it); hidden
when `upload.isVideo`. `PhotoDetailViewModel.availableTags` populated the same
way as Garden and Explore ViewModels.

**Garden quick-tag sheet.** `QuickTagDialog` (AlertDialog) replaced by
`QuickTagSheet` (ModalBottomSheet + `TagInputField`). Staged: dismiss commits if
tags changed. `onQuickTag` callback renamed `onTagsUpdated` with full old/new
list signature; `RecentTagsStore.record()` called for added tags.

**Share flow simplified.** `ReceiveState.Idle` loses `currentTagInput`. Four
IdleScreen callbacks (`onTagInputChanged`, `onTagCommit`, `onTagRemoved`,
`onRecentTagTapped`) replaced by one `onTagsChange: (List<String>) → Unit`.

**Test cleanup.** `tagInput_survives_savedstate_round_trip` removed (property
gone). `ShareViewModelTest` orphaned tests for `pendingWorkerId` /
`uploadPhotoCount` (removed from VM in an earlier session) cleaned up.

APK v0.26.0 (versionCode 31) installed on Samsung Galaxy A02s.

---

## Session — 2026-05-08 — v0.25.8–v0.25.10: Explore filter tags + video badges + garden tag button

**v0.25.10 — Visible tag button on Garden tiles.**
Long-press → dropdown → "Add tag…" existed but was invisible. Added a small
`Label` icon button (Forest 65% alpha, bottom-left corner, `RoundedCornerShape(topEnd=4.dp)`)
to every Garden tile. Placed in the outer `Box` after the inner `Box` (higher Z order),
so taps on it are consumed before reaching the inner Box's `pointerInput`/`onPhotoTap`.

`QuickTagDialog` upgraded: accepts `existingTags` and `availableTags`; shows a
`FlowRow` of `FilterChip`s for tags in the library that aren't already on the item —
tapping one immediately calls `onAdd(tag)` and closes the dialog. The text field
remains below for adding a new tag. Placeholder adapts ("e.g. family" vs "New tag…"
depending on whether suggestions are available).

`GardenViewModel` gains `availableTags: StateFlow<List<String>>`, populated by a
silent parallel coroutine in `load()`, same pattern as `ExploreViewModel`.

---

## Session — 2026-05-08 — v0.25.8–v0.25.9: Explore filter tags + video badges

**v0.25.9 — Video indicator badge on thumbnails.**
Both the Garden plot-row tiles and the Explore grid tiles now show a small
`PlayArrow` icon badge (14dp, Forest/65% alpha background, bottom-right corner,
`RoundedCornerShape(topStart=4.dp)`) whenever `upload.isVideo` is true.
`Upload.isVideo` was already defined in `Models.kt` as `mimeType.startsWith("video/")`.
`Icons.Filled.PlayArrow` is available via `material-icons-extended` (already a dep).
No model or API changes needed.

---

## Session — 2026-05-08 — v0.25.8: Explore filter tags

**Tags multi-select in Explore filter sheet.**
The Filters bottom sheet had no tag section — tag filters could only be applied
programmatically (e.g. via plot row title navigation) but not through the UI.

`ExploreViewModel` gains `availableTags: StateFlow<List<String>>` populated by a
silent parallel coroutine in `load()` (failure is swallowed — tags section simply
stays hidden). `FilterSheet` accepts `availableTags: List<String>` and renders a
"Tags" `FilterSection` as a `FlowRow` of `FilterChip`s between Sort and Capsule.
Selected tags fill Forest/Parchment; toggling adds/removes from `draft.tags`.
The section is omitted entirely when the tag list is empty, so new accounts see
no visual gap. The existing `draft.tags` → `filters.tags` → `listUploadsPage(tag=…)`
pipeline was already wired; no API layer changes needed.

---

## Session — 2026-05-08 — v0.25.4–v0.25.7: Android bugfix round (continued)

Hands-on device testing session on Samsung Galaxy A02s. All fixes driven by live observation.

**v0.25.4 — Share screen video thumbnail + upload progress jump.**
Share idle screen showed blank tiles for videos — `AsyncImage` used Coil's singleton
`ImageLoader` which had no `VideoFrameDecoder`. Added `coil-video:3.1.0`. `ShareActivity`
now creates a lightweight `ImageLoader` with `VideoFrameDecoder` and provides it via
`CompositionLocalProvider(LocalImageLoader)`. `IdleScreen`'s `AsyncImage` calls updated to
`imageLoader = LocalImageLoader.current`. Upload progress screen flashed "No uploads in
progress" before showing the active upload — `collectAsState(initial = emptyList())` triggered
`DoneState` (including a premature `pruneFinished()`) before WorkManager had any jobs.
Fixed by splitting the `when` branch: `allDone` → DoneState + prune; `files.isEmpty()` →
blank `Box` while the IO copy+enqueue is in flight; otherwise `InProgressState`.

**v0.25.5 — Just arrived scroll and animation reliability.**
Two bugs: (1) new item at index 0 appeared off-screen to the left because
`rememberLazyListState(initialFirstVisibleItemIndex=…)` is creation-only — Compose's
scroll-preservation kept existing items in place. Fixed: `shouldScrollToStart` parameter
on `PlotRowSection` calls `listState.scrollToItem(0)` via `LaunchedEffect`. (2) Arrival
animation was unreliable: `newItemsArrived` was a plain `var` on the ViewModel — not
observable by Compose — and was read during composition as a side effect (bad pattern).
Converted to `StateFlow<Boolean>`, collected via `collectAsStateWithLifecycle()`, triggered
from `LaunchedEffect(newItemsArrived)`.

**v0.25.6 — Arrival animation scoped to tile, not full screen.**
User observation: animation shouldn't cover the whole screen, just the item(s) that arrived.
Replaced full-screen `OliveBranchArrival` overlay with per-tile overlays. `GardenViewModel`
now exposes `newlyArrivedIds: StateFlow<Set<String>>` (the exact IDs from `genuinelyNew`).
`PlotRowSection` overlays `OliveBranchArrival` (clipped to tile shape, 88% parchment
background, `withWordmark = false`) on each matching thumbnail. `onComplete` clears the set.

**v0.25.7 — Video playback in photo detail.**
Two bugs: (1) ExoPlayer's default HTTP stack has no `X-Api-Key` header → 401 on every
request to `/api/content/uploads/{id}/file` → nothing played. Added
`media3-datasource-okhttp:1.4.1`; configured `ExoPlayer` with `OkHttpDataSource.Factory`
that injects the auth header. `HeirloomsApi.apiKey` promoted from `private` to `internal`.
(2) `PlayerView` had no height — `AndroidView` fell back to wrap_content, rendering a tiny
strip. Fixed with `aspectRatio(16f/9f)`. Added `player.playWhenReady = true`.

---

## Session — 2026-05-08 — v0.25.3: Upload progress clear-finished + auto-prune

Upload progress screen was accumulating completed and failed uploads indefinitely.
WorkManager keeps terminal-state `WorkInfo` records for ~7 days; `allUploadsFlow()`
queries all jobs tagged `heirloom_upload` without filtering, so old completed entries
reappeared in every new session's upload list.

Two fixes: a "Clear finished" `TextButton` appears inline next to the divider above the
file list whenever `session.files.any { it.isDone }` — tapping calls `workManager.pruneWork()`
which atomically removes all SUCCEEDED/FAILED/CANCELLED records. Auto-prune via `LaunchedEffect`
also fires when the screen naturally reaches the done state, so the cleanup happens silently
for the normal flow (user plants, uploads complete, screen shows "No uploads in progress").

Stuck ENQUEUED jobs: the per-file × cancel button (already present, since `isActive` includes
ENQUEUED) moves them to CANCELLED; "Clear finished" then removes them. No separate UI needed.

---

## Session — 2026-05-08 — v0.25.2: Android bug fixes (login, Just arrived, image cache)

Three post-D4 Android bugs fixed:

**Login screen appearing on Explore → photo detail navigation.**
Root cause: `MainApp.kt` used `remember { mutableStateOf(store.getApiKey()) }` for the
API key. On Activity recreation (rotation, or OS killing the process on the RAM-constrained
A02s and restoring via saved instance state), `remember` resets its value by re-running the
initialiser. The NavController back stack IS preserved (via `rememberNavController`'s built-in
Bundle save — hence "ends up in the right place" after re-entry) but `apiKey` reset. Fixed by
switching to `rememberSaveable`, which saves the value to the Activity's Bundle and survives
recreation. SharedPreferences remains the authoritative store; `rememberSaveable` is a
Belt-and-suspenders safety net. Also switched `SharedPreferenceStore.putString` from `apply()`
(async disk write) to `commit()` (synchronous) so the key is guaranteed on disk before the
caller returns — important on a device with historically full storage.

**Just arrived arrival animation not firing when row was empty.**
`GardenViewModel.refreshJustArrived()` guarded `newItemsArrived = true` with
`knownJustArrivedIds.isNotEmpty() && genuinelyNew.isNotEmpty()`. If Just arrived had no items
(knownJustArrivedIds was empty) and the first item arrived, the check short-circuited and the
animation never fired. Removed the `isNotEmpty()` guard — `genuinelyNew.isNotEmpty()` alone is
sufficient. The poll only fires after a 30s delay, so `load()` has always run and set
`knownJustArrivedIds` before the first poll.

**Thumbnail caching (disk cache).**
The `ImageLoader` in `AppNavigation.kt` was configured without a disk cache, so thumbnails
had to be re-fetched from the network on every app restart. Added a Coil `DiskCache` (50 MB,
`context.cacheDir/image_cache`) and an explicit `MemoryCache` (20% of available heap).
Thumbnails use stable non-signed URLs (`/api/content/uploads/:id/thumb`) with auth via
`X-Api-Key` header, so cache keys are stable across sessions.

---

## Session — 2026-05-08 (post-D4 brainstorm + doc sweep)

A brainstorming session held after Android D4 shipped, before the M7 brief
was drafted. Output: a doc sweep across IDEAS.md, IDIOMS.md, ROADMAP.md,
PA_NOTES.md, and PROMPT_LOG.md, all applied in a single commit.

### Topics explored

1. **iOS strategy.** Concluded with the *minimal iOS app* shape (Option 4
   in the IDEAS.md entry): web is the primary surface as a PWA; iOS app
   exists only to register a Share Extension, with a three-screen host app
   wrapping it. Android stays as-is through M6/M7. Decision recorded but
   not committed; this is post-M7 work.

2. **Gamification.** Considered and rejected. Trophies, ranks, persona,
   levels, XP, streaks — all rejected as fundamentally incompatible with
   the brand register. The *gardener's notebook* / *year in the garden*
   surface recorded as a possible alternative direction for the underlying
   feature-discovery problem.

3. **Tokens and monetisation.** Tokens as in-app currency rejected, with
   the per-currency pricing complexity accepted as the cost. Pricing tiers
   to be named in brand voice (illustrative: *steward* subscription, *long
   capsules* one-time fees) and tied to real underlying costs. Gift
   mechanic recorded as separate future feature.

4. **Trust posture and encryption.** Sealed-from-host recorded as the
   *eventual default*, not a paid feature. Engineering realities flagged
   (key management, no server-side processing, capsule delivery
   complications). M12+ horizon; recorded now to shape near-term
   architectural decisions.

5. **Third-party delivery integrations.** Recorded as M8+ second wave;
   email delivery flagged as the actual M8 baseline. Print-on-delivery
   (Moonpig, etc.) as the most brand-aligned integration but with a high
   reliability bar.

6. **Engagement without gamification.** Recorded as a feature space
   distinct from the gamification rejection. *The garden remembers you*,
   not the other way around. Existing schema columns (`uploaded_at`,
   `unlock_at`, `last_viewed_at`) flagged as the engineering hooks.

7. **The death/life recalibration.** The most significant outcome of the
   session. The PA had been calibrating the brand register toward
   *grief-product*, which is narrower than the product actually is.
   Heirlooms is about *time* — the gap between now and a future moment when
   something will mean more. Death is one shape that gap takes; most
   capsules will not involve death at all. The brand voice stays solemn
   and dignified; the *content* of what users put into the product is up
   to them, including humour and lightness. New IDEAS.md entry *Heirlooms
   is about time, not just death* captures the recalibration; new
   IDIOMS.md sub-section *The voice is solemn; the room belongs to the
   user* captures the discipline.

### The four-catch pattern

A meta-observation worth recording: this is the fourth time in recent
sessions that Bret has corrected a PA instinct that pulls the brand in a
particular direction. The pattern in chronological order:

1. *Didn't take* (v0.20.3) — the PA had introduced a brand verb for
   errors. Bret pointed out it was opaque to first-time readers; the
   brand-vocabulary-for-errors decision was reversed.
2. *Plant a seed for someone* (v0.20.3) — original capsule create form
   opening line. Bret caught the unwanted reproductive composition before
   it shipped.
3. *Productivity-app shelf-layout instinct* (Milestone 6 planning) — the
   PA initially proposed an inbox/recent/tag shelf layout for Garden,
   importing the *getting-behind/zero-as-achievement* register. Bret
   reframed to plots-and-explore.
4. *Over-solemnification of the brand register* (this session) — the PA
   had been treating Heirlooms as a grief product. Bret reframed to *time,
   not just death*.

The pattern is real: the PA's productive-by-default instinct extends
metaphors, surfaces, and emotional registers; Bret's discipline restrains
them. Both directions of restraint are now visible — *don't make it fun*
(catches 1 and 2) and *don't make it grim* (catch 4) — and they're sibling
disciplines, not opposing ones. The brand voice is dignified; the user's
use of it is theirs. Captured in IDIOMS.md.

### Roadmap renumbering

Decided in this session: M7 = multi-user, M8 = milestone delivery (the
inverse of the prior renumbering recorded on 2026-05-10). Reasoning:
delivery should be designed against real recipient accounts, which means
multi-user has to land first. Friend-tester onboarding sequencing also
only makes sense under this numbering. ROADMAP.md, PA_NOTES.md, and the
relevant IDEAS.md entries updated accordingly.

### Output artefacts

- `docs/IDEAS.md` — eight new entries appended; three existing entries
  edited for renumbering.
- `docs/IDIOMS.md` — one new sub-section.
- `docs/ROADMAP.md` — *Origin* section augmented; M7 and M8 sections
  rewritten.
- `docs/PA_NOTES.md` — three small edits per renumbering.
- `docs/PROMPT_LOG.md` — this entry.

No code changes.

---

## Session — 2026-05-08 (evening) — v0.25.0/v0.25.1: M6 D4 Android adoption + post-ship fixes

**D4 brief delivered and executed.** Closes Milestone 6. Android picks up the
Garden/Explore restructure that shipped on web in D2/D3. After D4, Android and
web are surface-equivalent for browsing, filtering, and photo detail.

**What shipped in v0.25.0 (D4):**

- **4E — ViewModel + SavedStateHandle migration.** All seven screens (Garden,
  Explore, Photo detail, Capsules, Capsule detail, Compost heap, ShareActivity)
  now have ViewModels. State that should survive configuration changes lives in
  the ViewModel; state that should survive process death goes through
  SavedStateHandle. `android:configChanges` removed from ShareActivity —
  rotation mid-upload now works because the ViewModel holds upload state and
  re-observes the WorkManager job on recreation.

- **4A — Four-tab bottom nav.** Garden | Explore | Capsules | Burger. Burger
  opens a `ModalBottomSheet` (chosen over a right-side slide panel — more
  natural Android idiom) containing Settings and Compost heap. Compost heap
  migrated from the Garden footer and Settings screen to Burger as the sole
  entry point.

- **4C — Explore tab.** Mirrors web `/explore`. Filter chrome always collapses
  to a "Filters" button + bottom sheet (all phone viewports are narrow). Tags,
  date range, capsule/location segmented controls, composted toggle, sort. 4-column
  thumbnail grid with cursor-paginated "Load more".

- **4B — Garden plot rows.** 2-column grid replaced with horizontal plot rows.
  Just arrived fixed at top. User plots in sort_order. Interactive row titles
  (chevron → Explore with plot tags pre-applied). "Load more" chip at end of
  partial rows; "See all in Explore →" tile at end of exhausted rows. Long-press
  thumbnail → DropdownMenu with Rotate 90° (immediate PATCH) and Add tag
  (AlertDialog).

- **4D — Photo detail flavours.** `?from=garden|explore|compost`. Garden:
  action-forward with rotate button, inline tag editor, Add to capsule, Compost
  below divider. Explore: content-forward, tags read-only, Compost in overflow.
  Compost: faded image, countdown, Restore. Back label is context-aware.
  Every photo detail open fires `POST .../view` once (tracked in ViewModel).

- **API layer.** Upload model gains `capturedAt`, `latitude`, `longitude`,
  `lastViewedAt`. Plot model added. `listUploadsPage()` replaces `listUploads()`
  with full filter/cursor/sort support. `listPlots()`, `listTags()`,
  `rotateUpload()`, `trackView()` added. ExoPlayer (media3:1.4.1) for inline
  video playback.

- **Tests.** 14 new automated tests: ExploreViewModel filter persistence via
  SavedStateHandle, PhotoDetailViewModel trackView fires exactly once and hits
  the correct endpoint, ShareViewModel state survives process death, Compose UI
  tests for Garden/Explore photo detail flavour split.

**Deployment.** HeirloomsServer and HeirloomsWeb unchanged for D4. Android APK
built with `./gradlew assembleDebug` and sideloaded via `adb install` to
Samsung Galaxy A02s (R9HR102XT8J). Device had ~512 KB free internal storage
at first install attempt — had to uninstall old APK first to free space. APK
is ~21 MB debug build.

**Post-ship fixes shipped same session as v0.25.1:**

*API response key mismatch (immediate deploy fix).* The live server returns
`"items"` not `"uploads"` for paginated upload lists, `"items"` not `"uploads"`
for composted uploads, a plain JSON array for `/api/plots` (not `{"plots":[...]}`),
and a plain JSON array for `/api/content/uploads/tags`. All four corrected in
`HeirloomsApi.kt`. Root cause: keys were assumed from code reading; not verified
against the live server before shipping D4.

*async/launch exception propagation.* `GardenViewModel.load()` used `async { }`
inside `launch { }`. When an `async` child fails, it cancels the parent
`StandaloneCoroutine` and the exception propagates past the `try/catch`, crashing
the app. Fixed by wrapping the parallel fetches in `coroutineScope { }` so child
exceptions are re-thrown at the `coroutineScope` boundary and caught correctly.

*Upload progress screen (new feature, v0.25.1).* The share-sheet uploading screen
replaced. Key decisions:
- **One WorkManager job per file** (was one batch for all): enables per-file
  cancellation and byte-level progress via `setProgressAsync()`.
- **Progress callback changed** from `(Int)` percent to `(Long, Long)` bytes so
  multiple workers aggregate into an overall %.
- **ShareActivity.enqueueUploads() is now fully async** (`lifecycleScope.launch`):
  progress screen appears immediately on Plant; no more ANR on 12-video batches
  where the old `runBlocking` blocked the main thread copying large files.
  Files are copied and workers enqueued sequentially (one at a time) to avoid
  holding multiple large temp files in memory simultaneously on the A02s.
- **Unified job list**: progress screen observes all `heirloom_upload`-tagged
  jobs, not just the current session's batch.
- **Retry**: 3 attempts with 30s exponential backoff before marking failed.
- **Burger entry** appears dynamically (WorkManager LiveData) only while uploads
  are active.

*System plot rogue row.* `listPlots()` returns all plots including the system
`__just_arrived__` plot. `GardenViewModel` was passing it through to user-plot
rows, which rendered it with raw name `__just_arrived__` and no filter (empty
`tag_criteria` → fetches all uploads). Fixed by adding `isSystemDefined` to the
`Plot` model and filtering it out before building user-plot rows.

*Garden staleness.* `LaunchedEffect(Unit) { if (Loading) vm.load() }` only ran
on first composition. If the ViewModel was retained across navigation (which it
is), data stayed stale. Fixed with `refresh()` (silent background re-fetch that
replaces Ready state without a loading flash) called on every composition.

*Just arrived scroll drift.* Android saves row scroll positions in the ViewModel.
After refresh, the row started at the saved offset, making the visible items
differ from the web (which always starts at the beginning). Fixed: scroll
position for `__just_arrived__` resets to 0 whenever new data arrives.

*30-second Just arrived poll.* Lightweight `refreshJustArrived()` fetches only
that row every 30 seconds. Detects genuinely new arrivals by comparing against a
known-ID set seeded on first load. Triggers `OliveBranchArrival` animation as a
semi-transparent overlay when new items land. Note for future: replace with
Server-Sent Events (see IDEAS.md) — polling is the interim approach, the
detection/animation logic stays in place.

**Things that tripped us up (don't repeat):**

- The live server's upload list endpoint returns `"items"` not `"uploads"`.
  Always verify actual HTTP responses before assuming field names from code.
- `async { }` inside `launch { }` without `coroutineScope { }` wrapping: child
  exceptions cancel the parent coroutine and bypass the outer `try/catch`,
  producing a FATAL crash instead of an error state.
- `runBlocking` on the Android main thread for large file copies = ANR. Always
  use `lifecycleScope.launch(Dispatchers.IO)` for any IO on the main thread.
- The A02s has ~201 MB heap and was nearly out of storage — both affect
  stability. Copy large files sequentially, not in parallel.
- WorkManager's `getWorkInfosByTagFlow()` (from `work-runtime-ktx`) is correct
  for Compose; `getWorkInfosByTagLiveData()` needs `lifecycle-livedata-compose`
  (not in deps) for `observeAsState`. Use Flow API throughout.
- `async { }` in Kotlin does not expose `inputData` on `WorkInfo` — that is a
  worker-side concept. Use progress data (`setProgressAsync`) for the ViewModel
  to observe state from running workers.

---

## Session — 2026-05-10 (Milestone 6 — deliverable restructure + tools addition)

A follow-up to the prior Milestone 6 planning session. The body of work is the
same; the structure has been reframed.

**From phases to deliverables.** The original 4-phase × 3-increment decomposition
(12 small briefs across backend / web / Android streams) reframed as 4
deliverables shipping one per session, each ~1 day of focused work:

- D1 — Tools (re-import utility)
- D2 — Backend + Explore basic
- D3 — Web complete
- D4 — Android adoption

The phase-and-stream view is still useful as a "what's in scope" map, but it
was a misleading execution plan given the founder + AI working pattern. Each
deliverable bundles work that previously sat across multiple phases:

- D2 = Phase 1 entirely (1A EXIF, 1B pagination, 1C plot schema) plus 2A (basic
  /explore route).
- D3 = 2B/2C (filters, photo detail) plus all of Phase 3 (Garden plots, plot
  management).
- D4 = Phase 4 unchanged.

The decomposition is preserved as sub-tasks within each deliverable's brief.

**Tools added as D1.** A re-import utility — standalone script that scans the
configured GCS bucket, computes SHA256 of each object, and INSERTs `uploads`
rows pointing at existing GCS keys. Originally suggested as a deferred / backlog
item, promoted to a first-class deliverable on Bret's direction. Justification:
D1 first acts as a safety net for the rest of M6 — with the re-import tool in
place, subsequent deliverables can experiment freely with the schema and data,
knowing recovery is one script away.

**Velocity finding (perceived).** The project to date — roughly nine days from
end of April through 8 May, with substantial backend / web / Android / brand
work shipped — would plausibly take a solo founder working evenings and
weekends about a year. Perceived multiplier: roughly 40×. Recorded in the deck
(slide 12: *Velocity*) as an observation, not a benchmarked claim. The
multiplier is specific to this product, this working pattern, and this team —
founder owning context across sessions, tight scoping discipline,
ship-then-polish cadence, AI doing the typing while the founder steers. None
of those factors is AI alone; the multiplier wouldn't transfer to a context
where any is missing.

**Operational constraints recorded.** A new section in PA_NOTES.md (*Pre-launch
operational constraints*) captures three durable rules:

1. Data destruction during M6 is acceptable — destructive schema changes can
   simplify implementation.
2. GCS objects are never auto-deleted outside the user-initiated compost flow.
3. DB backup is deferred but on the backlog (a small `pg_dump` to GCS on a
   schedule).

**Pending before D1 brief is drafted.**

1. Where does the re-import script live? (`scripts/`, `tools/`, or part of the
   server module?)
2. How does it run? (Gradle task, standalone jar, bash wrapper?)
3. Does it run locally pointing at remote DB and GCS, or on the Cloud Run
   instance?
4. One-shot recovery tool, or reusable for periodic drift-detection?
5. Does the v0.20.0 compost flow currently auto-purge GCS objects after the
   90-day window? The D1 brief needs to know whether composted GCS keys should
   be re-imported (no — they were intentionally removed) or whether they're
   already absent from the bucket (no GCS object to re-import).

Plus the four open questions still pending from the prior planning session
(plot configuration scope, vocabulary doc timing, plot criteria language,
v0.21.0 sequencing).

**Output artefacts updated.** `docs/presentations/Garden_Explore_Plan.pptx` now
13 slides. Slide 5 (*Plan at a glance*) shows 4 deliverables. Slides 6-9 are
deliverable detail slides (D1 Tools, D2 Backend + Explore basic, D3 Web
complete, D4 Android adoption). Slide 10 (*Sequence and dependencies*) replaces
the prior Gantt with a simpler view: D1 standalone with annotation,
D2 → D3 → D4 sequential. Slide 12 (*Velocity*) updated to four sessions
matching the deliverables.

---

## Session — 2026-05-10 (Milestone 6 — Garden / Explore restructure planning)

A scoping session before any code is written. The session settled the structural shape
of a new milestone — *Milestone 6, Garden / Explore restructure* — that's been inserted
before the originally-planned Milestone 6 (delivery, now Milestone 7). The decisions
came out of a working observation that the Garden tab today is doing two jobs at once:
helping the user *do work* (tag, process, compost incoming items) and helping the user
*explore memories* (browse what's been kept). Loading everything every time is slow.
The two jobs have different emotional registers and should live in different surfaces.

**The split.** Garden becomes the work surface — a vertically-scrolling stack of
horizontal Netflix-style "plots". The mandatory top plot is *Just arrived* — items
the user hasn't yet acted on (tag, encapsulate, compost). Below sit user-defined
plots (tag-based criteria) so advanced users can pin a "Family" plot, a "2026" plot,
etc. Explore becomes the leisure surface — paginated grid, full filter set (tag,
date range, capsule membership, composted toggle, location-boolean), photo-detail
emphasising content presentation rather than action affordances.

**Vocabulary settled.**
- *Just arrived* — the noun-phrase for items received but not yet acted on. Replaces
  *Untended* (which carried unintended productivity-app *neglect* connotations).
- *Plot* — the noun for a section of the Garden tab. *"Add a plot"* affordance.
  Garden contains plots. Verb forms (*plotted*, *plotting*) deliberately stay out of
  user-facing copy; the noun carries the metaphor.
- The unifier noun for content stays *items* — the brand has no canonical user-facing
  noun for "the unit of content". Considered and rejected: *seed* (reproductive
  connotations when paired with *plant*), *leaf* (mixed verb usage with *shelved*),
  *keepsake* / *treasure* / *plot itself*. The brand voice lives in the verbs (plant,
  seal, bloom, compost), not in the noun for the unit of content. This matches the
  v0.20.3 brand-restraint discipline.
- A new design principle: *negative-action button separation* — destructive actions
  (compost, cancel, restore from compost) live in a different visual region from
  positive actions (start a capsule, seal, add to capsule). To be added to BRAND.md
  during the v0.20.3 vocabulary cleanup or Phase 1 of Milestone 6, whichever lands
  first.

**Phased plan.** Four phases, three streams (backend / web / Android), twelve
incremental briefs.

- **Phase 1: Backend foundations.** Three increments — EXIF extraction (background
  job populating taken-date and location lat/lng on uploads), pagination on list
  endpoints (cursor-based, backwards-compatible), plot schema and endpoints. No UI
  changes; v0.21.0 Android ships unaffected during this phase.
- **Phase 2: Web Explore tab.** Three increments — basic /explore route with
  paginated grid, filter elaboration (date range, capsule membership, composted,
  location-boolean), photo detail variant emphasising content. Built first because
  it's mostly additive; Garden's existing behaviour unchanged during this phase.
- **Phase 3: Web Garden plots.** Three increments — *Just arrived* plot, user plot
  management with "Add a plot" affordance, photo detail variant emphasising actions.
  Riskier; done after Explore so users have a fallback during the Garden transition.
- **Phase 4: Android adoption.** Three increments — Android Explore tab, Android
  Garden plots, bottom-nav restructure (burger menu replacing v0.21.0's three-tab
  Settings entry).

**Sequencing.** v0.21.0 Android (currently in flight) ships first as scoped, then
this milestone begins. v0.21.0's bottom-nav structure changes during Phase 4 — users
see two nav structures in close succession. Acceptable.

**Timescales (rough estimates, ranges not commitments).**
- One engineer, sequential: 5–7 weeks.
- Two engineers, parallel where dependencies allow: 3–5 weeks.
- Three engineers, maximum useful parallelism: 3–4 weeks. Diminishing returns past
  three because the dependency graph constrains how much can run truly in parallel.

**Pending before Phase 1A brief is drafted.** Four small questions: plot
configuration scope (single-user only at v1, or per-user-foreshadowing schema); the
timing of vocabulary doc updates (with Phase 1, with Phase 2, or as a separate
doc-only patch); plot criteria language at v1 (tag-matching only, or richer queries
including date ranges and capsule membership); and confirming v0.21.0 Android ships
first as currently scoped.

**Output artefacts.**
- `docs/presentations/Garden_Explore_Plan.pptx` — the phased plan with timescale
  estimates, dependency Gantt-style chart, and milestone renumbering. The first
  presentation in a new `docs/presentations/` directory; future presentations may
  cover retrospectives of past milestones, scoping for upcoming ones, or
  team-shareable views of project state.

**Milestone renumbering.** The originally-planned M6 (delivery) becomes M7. The
originally-planned M7 (multi-user) becomes M8. ROADMAP.md updated to reflect the
new ordering. The reasoning: delivery deserves to land on a settled foundation
rather than a flat surface that's about to change shape; loading and brand-register
problems compound with every increment delayed; multi-user makes scaling decisions
more expensive to retrofit.

**Brand-discipline note worth recording.** During the session, the PA initially
proposed a *shelf layout* with productivity-app conventions (inbox shelf, recent
shelf, tag shelf) on the Garden tab. Bret pushed back — those conventions import
the *getting-behind* / *zero-as-achievement* register the brand has been resisting.
The eventual answer (Garden = work surface with plots, Explore = leisure surface)
came from Bret's reframing, not the PA's initial proposal. This is the third such
catch in recent sessions (the others being *didn't take* opacity and *plant a seed
for someone* near-miss in v0.20.3). The pattern is real: PA's productive-by-default
instinct extends metaphors and surfaces; the brand's discipline restrains them.
Captured in PA_NOTES.md at v0.20.3; reinforced here.

**Drive-by:** Milestone 3 (self-hosted deployment) retroactively marked as done in
ROADMAP.md. The full Docker Compose stack runs locally and meets the substantive
intent of the milestone. Some original M3 sub-goals around polished non-developer
self-hosting setup remain unaddressed but are no longer relevant given the project
shipped on Cloud Run for production. Marker change brings ROADMAP.md into alignment
with the actual project state — M0, M1, M2, and M4 had `(done)` markers; M3 didn't,
which surfaced as a question when the *Where we are* slide was added to the
Milestone 6 deck.

---

## Session — 2026-05-10 (v0.20.2 — Coil 3.x migration prerequisite)

**v0.20.2 (10 May 2026) — Coil 3.x migration prerequisite.** Migrated the Android app's
Coil dependency from 2.5.0 to Coil 3.x ahead of the combined Android Increment 3 +
Daily-Use, which will substantially expand image-loading surfaces. ShareActivity's
idle-screen photo grid verified end-to-end. Drive-by: fixed two stale
`~/Downloads/Heirlooms/` path references in PA_NOTES.md's Cloud Run deploy commands (the
SE_NOTES.md correction in v0.20.1 caught one location; this fixes the others). PA_NOTES.md's
Coil-version gotcha updated to reflect the migration.

---

## Session — 2026-05-09 (v0.20.1 — No-flash fix + documentation sweep)

**v0.20.1 (9 May 2026) — No-flash fix on compost + post-v0.20.0 documentation sweep.**

**Code fix:** `PhotoDetailPage.jsx` had a `finally` block that reset the `composting`
React state to `false` after a successful compost. On success the component is about to
unmount (navigation fires immediately after the POST succeeds), so the state reset caused
a re-render in the non-composted state before the component disappeared — a brief, visible
flash of the file detail view. Removing the reset from `finally` into the `catch` block
only (where it still belongs on failure) eliminates the flash. One-line change.

**Documentation sweep:**
- `VERSIONS.md`: v0.20.1 entry added.
- `ROADMAP.md`: Increment 2 and Brand follow-up updated from "(planned)" to "(shipped)".
  Brand follow-up note revised — it shipped before Increment 2, not after. Compost heap
  added as a non-milestone interstitial between Milestone 5 and 6. Android Daily-Use and
  Increment 3 noted as combined. `~v0.19.0` timing estimate removed; replaced with
  positional language (after v0.20.1).
- `PA_NOTES.md`: current version bumped to v0.20.1.
- `IDEAS.md`: stale "Planned for ~v0.19.0" timing on Android daily-use updated to
  "combined Android Increment 3 + Daily-Use increment (after v0.20.1)"; noted that both
  the capsule web UI and compost heap are now shipped, so the Android increment builds
  against a settled schema.
- `BRAND.md`: status line updated to record *compost* verb addition at v0.20.0.
- `SE_NOTES.md`: project path corrected from `~/Downloads/Heirlooms/` to
  `~/IdeaProjects/Heirlooms/`; memory store path corrected to match.

No new tests. No behaviour change beyond the flash fix.

---

## Session — 2026-05-09 (v0.20.0 — Compost heap)

**v0.20.0 (9 May 2026) — Compost heap: soft-delete with 90-day auto-purge.**
Introduces composting as the first user-facing removal mechanism. The product is
slow and considered about removal: composting requires no tags and no active
capsule memberships, the 90-day window is the safety net, and the only path to
true hard-delete is the system-driven lazy cleanup. No public hard-delete endpoint
is added.

**Schema:** Flyway V8 migration — `ALTER TABLE uploads ADD COLUMN composted_at
TIMESTAMP WITH TIME ZONE`. Partial index `uploads_composted_at_idx WHERE
composted_at IS NOT NULL` covers heap-list and cleanup queries. Purely additive;
existing uploads default to `composted_at = NULL` (active).

**Backend:**
- `Database.compostUpload(id)`: wraps the precondition check and `SET composted_at
  = NOW()` in a `withTransaction` lock; returns a sealed `CompostResult` (Success /
  NotFound / AlreadyComposted / PreconditionFailed).
- `Database.restoreUpload(id)`: clears `composted_at`; returns `RestoreResult`.
- `Database.listCompostedUploads()`: `WHERE composted_at IS NOT NULL ORDER BY
  composted_at DESC`.
- `Database.canCompost(uploadId, conn)`: checks `tags IS EMPTY` and no active
  (`open` / `sealed`) capsule membership. Called inside the compost transaction.
- `Database.fetchExpiredCompostedUploads()` + `Database.hardDeleteUpload(id)`:
  support the lazy cleanup.
- `Database.listUploads()` extended with `composted_at IS NULL` filter; all
  existing callers now return only active items by default.
- `UploadRecord.toJson()` extended with `compostedAt` (null-safe).
- `UploadHandler`: three new contract routes (`POST /compost`, `POST /restore`,
  `GET /uploads/composted`); new `GET /uploads/:id` returning the upload regardless
  of composted state; `launchCompostCleanup()` fires on every active-list call as
  a daemon thread (GCS delete → DB delete, retry-safe).

**Web:**
- `api.js`: added `formatCompactDate` (compact en-GB date, year omitted for
  current year) and `daysUntilPurge` (days until 90-day window closes).
- `PhotoDetailPage.jsx`: fallback fetch updated to use `GET /uploads/:id` (finds
  composted photos too); compost/restore button with earth-ghost / forest-fill
  styling; disabled state with helper text when preconditions unmet; composted
  state renders faded image, countdown metadata, `Restore` replacing `Compost`.
- `GardenPage.jsx`: transient italic confirmation message on `location.state.composted`
  (clears browser history state on mount); `GET /uploads/composted` count fetch;
  quiet `Compost heap (N)` link below the grid.
- `CompostHeapPage.jsx`: new page at `/compost` — list view with thumbnail, dates,
  days-remaining, inline `Restore`; empty state randomised from a pool of five
  brand-voice lines (`brandStrings.js`).
- `brandStrings.js`: new module in `src/brand/` holding the empty-state pool.
  Pool expansion requires PA review (noted in a comment and in BRAND.md).
- `App.jsx`: `/compost` route added.

**Tests:**
- `CompostApiTest.kt` (new): ~16 integration tests via Testcontainers covering
  compost preconditions, restore, list filtering, GET-by-id on composted items,
  lazy-cleanup non-expiry (HTTP-only; expiry path verified by logic inspection).
- `compost.test.jsx` (new): ~10 Vitest tests covering `PhotoDetailPage` compost
  button states, `GardenPage` compost heap link + transient message, and
  `CompostHeapPage` render, restore, and empty state.

**Documentation:** IDEAS.md cascade-warning entry removed (compost preconditions
resolve the problem). PA_NOTES.md bumped to v0.20.0; two new gotchas: lazy-cleanup
doesn't scale to multi-user (Milestone 7: Cloud Scheduler), hard-delete is
system-only by design. BRAND.md voice section: *compost* verb added; canonical
strings: empty-state pool reference. VERSIONS.md: v0.20.0 entry. PROMPT_LOG.md:
this entry.

---

## Session — 2026-05-09 (v0.19.6 — Post-v0.19.5 documentation sweep)

**v0.19.6 (9 May 2026) — Post-v0.19.5 documentation sweep.** Captured the v0.19.x
series' substantive lessons in PA_NOTES.md: manual JSON serialisation in Kotlin (the
v0.19.2 quoting bug — triple-quoted string delimiter consumed the trailing quote on the
`state` field value, producing `"state":"open,"` with the comma leaking into the string);
integration tests with permissive parsers hiding field-value bugs (all 49 integration
tests passed because Jackson's `ObjectMapper.readTree()` accepted the malformed JSON
while the browser's strict `JSON.parse` rejected it); SPA routing requires nginx
`try_files $uri $uri/ /index.html` fallback (v0.19.3); the post-login auth-redirect
interim pattern (`RequireAuth` + `state.from` → `navigate(from, { replace: true })`,
to be replaced with cookie-based sessions during Milestone 7 multi-user work, v0.19.4).
Added a new "Architectural notes worth remembering" section to PA_NOTES.md covering:
the photo detail route migration (lightbox modal replaced with a real `/photos/:id` route,
v0.19.0); the `?sealed=1` query-param handshake pattern for post-action transition
animations (v0.19.0); the confirmation that the held-lightly capsule-message typography
decision (italic Georgia for sealed/delivered) landed cleanly at first render (v0.19.0).
Documented the five derived Tailwind tokens (`forest-75`, `bloom-15`, `bloom-25`,
`earth-10`, `earth-20`) in BRAND.md as a derived-tokens sub-table, with code-verified
usages; updated the palette discipline line to distinguish primary colours from derived
tokens. Loosened the Android Daily-Use Increment's stale `~v0.19.0` version estimate in
ROADMAP.md to positional language (after Increment 3). No code changes.

---

## Session — 2026-05-09 (v0.19.1–v0.19.5 — Bug fixes and hardening)

**Context:** Post-deploy testing of the v0.19.0 capsule web UI revealed four bugs
and two improvement opportunities. All were addressed in the same session.

**Bugs found and fixed:**

1. **Photo rotation not applied in picker/capsule grids (v0.19.1).**
   `PhotoGrid.jsx`'s `Thumb` component rendered images with no CSS transform.
   The `upload.rotation` property existed but was never applied. One-line fix.

2. **"Start Capsule" and capsule list both returning "didn't take" (v0.19.2).**
   Root cause: all three capsule JSON serialisers (`toDetailJson`, `toSummaryJson`,
   `toReverseLookupJson`) had a Kotlin triple-quoted string quoting bug. The closing
   `"""` delimiter consumed the `"` that was meant to close the `state` field value,
   producing `"state":"open,"created_at":...` — the comma leaked into the string value.
   JavaScript's `JSON.parse` is strict and rejected this at position 88. Jackson
   (used by the integration tests' HTTP client) is lenient and parsed `open,` as a
   valid string value, so all 49 integration tests passed undetected. Fixed in the
   same commit by adding one `"` to each serialiser (`}"""` → `}""""`).

3. **Deep-link 404 on page refresh (v0.19.3).**
   nginx was serving the static build without a SPA fallback. Navigating to
   `/capsules` directly or refreshing returned 404 because nginx looked for a real
   file at that path. Fixed by adding `nginx.conf` with `try_files $uri $uri/ /index.html`
   and COPYing it into the Dockerfile.

4. **Post-login redirect always went to `/` rather than the intended page (v0.19.4).**
   Auth state is held in React memory, so refresh clears it. The old `<Navigate to="/login">` didn't
   carry the intended destination. Fixed by introducing a `RequireAuth` component that
   passes `location.pathname + location.search` as `state.from` in the redirect, and
   updating `LoginPage` to `navigate(from, { replace: true })` after successful login.
   Noted by Bret as a temporary workaround until proper cookie-based server auth is in place.

**Improvements made:**

5. **Jackson for capsule JSON serialisation (v0.19.5).**
   Prompted by the v0.19.2 bug: manual string building is the wrong tool for JSON.
   Jackson was already a compile dependency (used for request parsing). All three
   serialisers were rewritten to use `mapper.createObjectNode()` / `putArray()` /
   `writeValueAsString()`. The private helpers `jsonString()` and `toJsonArray()` were
   removed. Functions changed from `private` to `internal` to allow direct unit testing.

6. **Serialiser unit tests added (v0.19.5).**
   `CapsuleHandlerTest.kt`: 13 new tests, one per serialiser × state variant + field
   type checks. Key test: `state field is a bare string value` for each of the three
   serialisers — this is the regression guard that would have caught the v0.19.2 bug
   at unit-test time. Unit test count: 135 → 148 passing.

**Why integration tests missed the bug:**
Jackson's `ObjectMapper.readTree()` (used in integration tests to parse responses)
is lenient by default. It parsed `"state":"open,` as the string value `open,` and
continued. The tests apparently checked HTTP status codes and high-level structure
but not exact field values with strict type checking. Lesson: serialiser unit tests
with a strict round-trip parse are cheaper and faster to catch this class of bug
than relying on the integration test layer.

**Key decisions:**
- Auth state remains in React memory (per existing design). The return-URL pattern
  (`state.from`) is the right interim fix. Proper cookie-based session tokens are the
  long-term solution (Bret noted this explicitly).
- Jackson ObjectNode API preferred over data class serialisation for the capsule
  responses, because the response shapes embed `UploadRecord.toJson()` (which is
  still manual for now). A full migration to Jackson data class serialisation would
  be a wider refactor and was deferred.

---

## Session — 2026-05-09 (v0.19.0 — Capsule web UI, Milestone 5 Increment 2)

**PA brief:** SE Brief — Capsules, Increment 2: Web UI. Nine sub-areas covering
routing, list view, detail view, create form, photo picker modal, inline edits,
photo-detail integration, confirmation dialogs, and sealing animation.

**What was done:**
- Installed `react-router-dom` v6; restructured App.jsx from single-page to routed
  app with BrowserRouter, AuthContext, and AuthLayout (outlet pattern).
- Four new routes: `/`, `/photos/:id`, `/capsules`, `/capsules/new`, `/capsules/:id`.
- Extracted all existing Gallery code into `GardenPage.jsx` (nav removed from component,
  thumbnails link to `/photos/:id` rather than opening lightbox).
- `PhotoDetailPage.jsx`: new proper route replacing the lightbox modal. Fetches upload
  from router state (passed by Gallery) or falls back to the full list. Includes "In
  capsules:" line, "Add this to a capsule" button with `AddToCapsuleModal`, and toast
  on add success.
- `Nav.jsx`: top-of-page nav bar with earth-coloured active underline, mobile hamburger
  slide-in panel.
- `WaxSealOlive.jsx`: reusable SVG brand component, `currentColor` fill.
- `CapsulesListPage.jsx`: card grid with filter/sort dropdowns (client-side sort for
  `created_at` since server only supports `updated_at`/`unlock_at`). All four card state
  treatments. Empty states (never-had-capsules vs filter-excludes-all). Skeletons, error.
- `CapsuleDetailPage.jsx`: four state variants sharing structural shape. Inline edit state
  machine (field/phase enum: idle|editing|saving|error). Auto-save on context-switch.
  Date edit opens `BrandModal` with `DatePickerDropdowns`. Photo edit opens
  `PhotoPickerModal` in edit mode. Navigation guard with `ConfirmDialog`.
  Sealing animation via `?sealed=1` query param on arrival from create form.
- `CapsuleCreatePage.jsx`: brand-voice opening line, all four fields, three-dropdown date
  picker, `IncludeStrip` component (horizontal thumbnail strip), recipient-aware message
  placeholder (updates on every For-field keystroke). Both Start and Seal commit paths.
  `?include=uuid` preselection. Discard confirmation.
- `PhotoPickerModal.jsx`: shared component (create + edit modes), tag filter chips,
  corner-mark + darken selection treatment, live count in Done button.
- `AddToCapsuleModal.jsx`: lists open capsules soonest-first, disabled rows for
  already-in capsules, empty state with "Start a capsule with this" CTA.
- `ConfirmDialog.jsx`: seal (italic Georgia title, bloom-seal button), cancel (recipient
  name in body, earth primary), discard (plain sans, routine guard).
- `Toast.jsx`: italic Georgia, parchment bg, top-centre, 3s auto-dismiss, slide-in animation.
- `DatePickerDropdowns.jsx`: day/month/year selects, auto-bounds day count by month/year.
- `BrandModal.jsx`: parchment-background brand dialog (distinct from the dark lightbox Modal).
- `api.js`: shared fetch helper, `formatUnlockDate`, `capsuleTitle`, `joinRecipients`,
  `buildUnlockAt` (8am local time convention).
- Tailwind config: added `forest-75`, `bloom-15`, `bloom-25`, `earth-10`, `earth-20`.
- `index.css`: `toast-in` keyframe animation.
- 48 new vitest tests covering all four capsule states, inline edit flows (message,
  recipients, date, discard guard), create form validation and submission, picker modal
  selection, add-to-capsule modal, and confirmation dialogs.

**Key decisions:**
- **Sealed message typography (held-lightly decision):** italic Georgia renders cleanly
  at message-body length. No revision needed — confirmed at first render alongside open
  state. Recorded here per PA brief instructions.
- **Client-side `created_at` sort:** server's `listCapsulesHandler` only accepts
  `updated_at`/`unlock_at` as order params. `created_at` sort is done client-side after
  fetching. Acceptable at v1 (no pagination).
- **Photo detail as a real route:** the existing lightbox modal was replaced with a proper
  `/photos/:id` page. Garden thumbnails now navigate to it. This enables the capsule
  photo→detail→capsule navigation loop specified in the brief.
- **Sealing animation via query param:** `?sealed=1` on the detail route URL is the
  handshake between the create form and the detail page for the post-create sealing
  animation. The param is removed from history immediately on mount.
- **Garden page header:** the old Gallery had an inner header with auth controls. This
  was removed (nav handles auth now). A slim toolbar row remains for auto-refresh and
  the refresh icon.
- **`AddToCapsuleModal` fetch strategy:** uses GET-then-PATCH (fetch current upload IDs
  from capsule detail, append new one, PATCH full replacement) as specified in the PA
  brief. One extra round-trip per add, acceptable at v1.

---

## Session — 2026-05-08 (v0.18.2 — Capsule visual mechanic added to BRAND.md)

**PA brief:** SE Brief — Capsule Visual Mechanic (BRAND.md update).

**What was done:**
- `BRAND.md` status line updated to reflect both the v0.17.0 foundation and the
  v0.18.2 capsule-mechanic addition.
- Voice section: *sealed* verb added between *planted* and *bloomed* — reserved
  for the capsule mechanic, not routine affordances.
- Motion language section: two new states added (*sealing*, ~700ms olive forms
  in corner; *delivering*, ~2.5s olive grows to fill + parchment-to-bloom wash,
  Milestone 6 territory), with a separator note distinguishing capsule-state
  transitions from arrival-animation phases.
- New "Capsule states" section added in full: the wax-seal olive (form, colour,
  sizes, reference SVG, distinction from brand mark); state visual treatments
  (open=forest, sealed=forest+olive, delivered=bloom-tinted, cancelled=earth-tinted);
  capsule card and detail view specs; Start/Seal button hierarchy; photo detail
  "in N capsules" line; visibility rule for cancelled capsules; sealing and
  delivery animations; reduced-motion fallback.
- "What is NOT in this document" — capsule visual mechanic line removed.

**Key decisions:**
- Capsule states map onto the existing forest/bloom/earth signal vocabulary.
  No new palette tokens.
- The bloom colour earns two appearances in a capsule's lifecycle: the small
  wax-seal olive at sealing (promise) and the full ripened state at delivery
  (fulfilment). The two appearances are causally linked by design.
- Capsule message typography shifts from system serif (open, draft) to italic
  Georgia (sealed/delivered, committed brand voice). Sealing promotes the
  message from draft to delivery-bound.
- The wax-seal olive is a new brand element, distinct from the brand mark's
  apex olive — simpler form, no stem, more geometric. Keep the two assets
  separate in the codebase.

**No code changes.** The rendering work lives in Increment 2 (web UI) and
Increment 3 (Android), which will reference this spec.

---

## Session — 2026-05-08 (v0.18.1 — Documentation sweep + reverse-lookup path fix)

**PA brief:** SE Brief — Post-v0.18.0 Documentation Sweep.

**What was done:**
- `PA_NOTES.md` — current version updated to v0.18.1. Added seven accumulated gotchas
  from v0.17.0–v0.18.0: Android orientation change mid-upload, `@ExperimentalLayoutApi`
  opt-in for `FlowRow`, upload-confirm tag contract, Coil 2.5.0 pinning,
  `withTransaction` rollback pattern, `UploadRecord.toJson()` canonical serialisation,
  OpenAPI spec contract-block merge.
- `ROADMAP.md` — Milestone 5 expanded from one-line description to full increment plan
  (Increment 1 shipped, Increment 2 web UI planned, brand follow-up, Increment 3
  Android, Android Daily-Use Increment).
- `IDEAS.md` — Android daily-use gallery entry added.
- API — moved capsule reverse-lookup from `GET /api/uploads/{id}/capsules` to
  `GET /api/content/uploads/{id}/capsules` for consistency with the existing upload
  resource path (`/api/content/uploads/{id}`). The endpoint was moved from the capsule
  contract block (bound at `/api`) to the content contract block (bound at
  `/api/content`). Handler logic unchanged. No client uses this endpoint yet; safe move.
- Integration tests for the reverse-lookup endpoint updated to the new path.

**Test count:** unchanged. 135 HeirloomsServer unit tests (134 passing, 1 skipped —
FFmpeg); 49 HeirloomsTest integration tests.

---

## Session — 2026-05-08 (v0.18.0 — Capsules: Schema and Backend API)

**PA brief:** SE Brief — Capsules, Increment 1: Schema and Backend API.

**What was built:**
- `V7__capsules.sql` — four new tables: `capsules`, `capsule_contents`,
  `capsule_recipients`, `capsule_messages` with five indexes and
  `ON DELETE CASCADE` constraints on both FK columns of `capsule_contents`.
- `Database.kt` extended — `CapsuleShape`, `CapsuleState` enums;
  `CapsuleRecord`, `CapsuleSummary`, `CapsuleDetail` data classes;
  `createCapsule`, `getCapsuleById`, `listCapsules`, `updateCapsule`,
  `sealCapsule`, `cancelCapsule`, `getCapsulesForUpload`, `uploadExists`
  methods; inline `withTransaction` with committed-flag rollback safety.
- `CapsuleHandler.kt` — seven ContractRoute handlers wired via
  `capsuleRoutes(database)`.
- `UploadHandler.kt` — replaced single `apiContract` with `contentContract`
  (at `/api/content`) + `capsuleContract` (at `/api`); `mergedSpecWithApiKeyAuth`
  combines both OpenAPI specs with absolute path prefixes and server `"/"`.
- `UploadRecord.toJson()` moved from private in `UploadHandler.kt` to internal
  in `Database.kt` so `CapsuleHandler.kt` can reuse it for detail responses.
- 49 integration tests in `HeirloomsTest/capsule/CapsuleApiTest.kt` covering
  create/read/list/update/seal/cancel/reverse-lookup flows, all rejection paths,
  message versioning, and the spec-generation canary.

**Key decisions:**
- `withTransaction` is `inline` so non-local returns work from within the lambda.
  A `committed` flag in `finally` ensures rollback when the lambda exits early via
  non-local return (all early exits in practice happen before any DB modification).
- Second capsule contract at `/api` (not `/api/content`) matches the brief's URL
  spec; OpenAPI spec merged at `/docs/api.json` by prefixing content paths with
  `/api/content` and capsule paths with `/api`, server set to `"/"`.
- `UploadRecord.toJson()` made `internal` rather than duplicated.
- `unlock_at` read from Postgres as `OffsetDateTime` (returns UTC offset) per the
  brief's type guidance; all other timestamps use `Instant`.
- `created_by_user` is the placeholder `"api-user"` — Milestone 7 will wire real
  user identity once the auth model exists.

**Test count:** 135 HeirloomsServer unit tests (134 passing, 1 skipped — FFmpeg);
49 new HeirloomsTest integration tests (run against Docker build).

---

## Session — 2026-05-07 (v0.17.0 — Brand, Increment 3b)

**PA brief:** SE Brief — Brand, Increment 3b: Android Animation Components.

**What was built:**
- `AccessibilityHelpers.kt` — `rememberReducedMotion()` reading `Settings.Global.ANIMATOR_DURATION_SCALE`.
  `WorkingDots` refactored to call it (removing the inline Settings.Global read).
- `OliveBranchArrival.kt` — Compose `Animatable<Float>` 0→1 over 3s (`LinearEasing` — phase ranges
  assume constant-rate progress; FastOutSlowInEasing would shift the visual beats). Canvas rendering
  via `PathMeasure.getSegment` for branch reveal, `withTransform { rotate; scale }` for leaf grow-in,
  `lerp(Forest, Bloom, t)` for olive ripening. `withWordmark` param; `LaunchedEffect` snaps to 1f
  under reduced motion and fires `onComplete` immediately.
- `OliveBranchDidntTake.kt` — same pattern, 2s; partial branch + leaf pair + pause + earth seed +
  "didn't take" text. Shares `internal` helpers from `OliveBranchArrival.kt` (same module, same package).
- `ShareActivity` — full rewrite as `ComponentActivity` with `setContent { HeirloomsTheme { ... } }`.
  Sealed `ReceiveState` class drives the Compose UI. Upload is enqueued via WorkManager;
  `observeWorkToCompletion(id)` uses `suspendCancellableCoroutine` + `LiveData.observeForever` to
  await terminal state without explicit `lifecycle-livedata-ktx` dependency.
  `Arriving` → `Arrived` and `FailedAnimating` → `Failed` transitions driven by animation `onComplete`.
- `styles.xml` — `Theme.Heirlooms.Share` added; ShareActivity manifest theme updated.
- 5 Compose instrumentation tests in `androidTest/kotlin/...`.

**Key decisions:**
- `scale` in `DrawTransform` takes `scaleX`/`scaleY`, not a single `scale` param — caught at first
  compile; brief's pseudocode used a non-existent named param. Fixed to `scale(scaleX = p, scaleY = p, pivot = pivot)`.
- `observeWorkToCompletion` uses `suspendCancellableCoroutine` + `observeForever` rather than
  `LiveData.asFlow()` to avoid needing an explicit import of the ktx extension; cleaner given that
  `lifecycle-livedata-ktx` is transitive but not declared.
- `photoCountString` is `@Composable` because it calls `stringResource` — called inline within the
  composable `when` branches, not from a non-composable context.
- Instrumentation tests use `reduceMotion = true` to exercise the fast-path without mocking
  `Settings.Global` or dealing with animation timing in tests.

**Test count:** 148 total (135 Kotlin + 8 web + 5 Android instrumented), 147 passing, 1 skipped.

---

## Session — 2026-05-07 (v0.17.0 — Brand, Increment 3a)

**PA brief:** SE Brief — Brand, Increment 3a: Android Static Brand (Icon + Resources + Receive Screen).

**What was built:**
- App icon: VectorDrawable foreground (`ic_launcher_foreground.xml`, ellipses converted to arc paths
  with `<group>` rotations), adaptive icon XML at `mipmap-anydpi-v26/` with `<monochrome>` for
  Android 13+ themed icons, legacy PNGs at all five densities generated via sharp-cli from the
  favicon SVG, Play Store icon at 512×512.
- `res/values/colors.xml` — full brand palette + tints + text shades.
- `ui/theme/Color.kt`, `Type.kt`, `Theme.kt` — Compose brand theme. `HeirloomsTheme { }` ready
  to wrap Activity content.
- `ui/brand/WorkingDots.kt` — Compose three-dot pulse component. `rememberInfiniteTransition`
  called unconditionally inside `repeat(3)` to satisfy Rules of Compose; `reduceMotion` only
  affects which value is used, not whether the composable is called.
- `build.gradle.kts` updated: Compose BOM 2024.01.00, Compose Compiler 1.5.8, JVM 11,
  `buildFeatures { compose = true }`.
- `strings.xml` — full garden voice string set.
- `UploadWorker` — notifications use R.string brand strings; small icon changed from
  `android.R.drawable.ic_menu_upload` to `R.drawable.ic_launcher_foreground`.
- `ShareActivity` — toast messages updated to brand voice ("uploading…" / "Waiting for WiFi
  to plant your photos.").

**Flagged gap — receive screen:**
The current `ShareActivity` has no visible UI. It is a transparent Activity that immediately copies
files, enqueues WorkManager, shows a Toast, and finishes. The brief's "receive-screen Composable"
does not exist. Building a full branded receive screen (photo previews, tag chips, "plant" button)
requires a new Compose Activity — scoped to a follow-up, not a restyling of existing code.

**Key decisions:**
- VectorDrawable ellipse conversion: each SVG ellipse with a rotation transform became a
  `<path>` with arc commands (`M cx-rx,cy A rx,ry 0 1 0 cx+rx,cy A rx,ry 0 1 0 cx-rx,cy Z`)
  inside a `<group android:rotation="..." android:pivotX="..." android:pivotY="...">`. This is
  the standard VectorDrawable pattern since VectorDrawable has no `<ellipse>` element.
- JVM target bumped 1.8 → 11 (Compose minimum). No existing code uses Java 8-only APIs that
  would break at JVM 11.
- Notification small icon changed to `ic_launcher_foreground` (our VectorDrawable). On Android 8+
  notification icons must be monochromatic; the parchment-on-transparent foreground renders as
  white on the system's accent colour, which is correct behaviour.
- Compose UI tests deferred: no emulator/device CI runner configured. Existing JUnit tests
  (135 Kotlin) are unaffected by Compose dependency additions.

---

## Session — 2026-05-07 (v0.17.0 — Brand, Increment 2)

**PA brief:** SE Brief — Brand, Increment 2: Web Arrival and Didn't-Take Animations.

**What was built:**
- `src/brand/animations.js` — `lerp`, `interpolateHexColour`, `prefersReducedMotion` pure helpers
- `src/brand/OliveBranchArrival.jsx` — 3s rAF animation, six phases, `withWordmark` prop, reduced-motion fast-path
- `src/brand/OliveBranchDidntTake.jsx` — 2s rAF animation, partial branch + pause + earth seed + "didn't take" text, reduced-motion fast-path
- `src/brand/OliveBranchArrival.test.jsx` and `OliveBranchDidntTake.test.jsx` — 5 smoke tests (render, withWordmark, reduced-motion onComplete)
- `src/test/setup.js` updated — `Element.prototype.getTotalLength` stub (JSDOM 29 doesn't implement it; `window.SVGPathElement.prototype` patching silently failed because JSDOM 29 exposes SVG constructors on `window`, not as bare globals)
- `src/App.jsx` — `UploadCard` rewritten with 6-state tile machine (`loading/arriving/arrived/error-animating/failed/dismissed`); `FailedTile` component added; `Gallery` tracks `seenIdsRef` to only animate newly-appeared uploads (first load is silent; auto-refresh arrivals animate)
- `src/index.css` — tile animation CSS classes added

**Key decisions:**
- "New" upload = first time an ID is seen in this browser session. First page load → all items skip animation (quiet); auto-refresh → new items animate. This is the right semantic — "moment of arrival" is when the upload is detected by the web client for the first time, not every page load.
- `animateArrivalRef` captured at mount and cleared after first successful use — retry never re-plays the arrival animation.
- Blob URLs revoked properly via `blobUrlRef` (the original code captured `blobUrl` in the closure at effect creation time when it was null, so revocation never ran; fixed here).
- `gallery-tile--arrived-fading-in` CSS is defined but not applied — kept for Increment 3 review or PA follow-up if the hard-cut feels abrupt in production.

**Test count:** 143 total (135 Kotlin + 8 frontend), 142 passing, 1 skipped.

---

## Session — 2026-05-07 (v0.17.0 — Brand, Increment 1)

**PA brief:** SE Brief — Brand, Increment 1: Tokens + BRAND.md + Static Web Application.

**Prompt:** Apply the new Heirlooms brand foundation to HeirloomsWeb. Add design tokens, create BRAND.md, add SVG brand components, restyle header/tags/empty state/working indicator, apply three-colour signal discipline, update garden copy.

**What was built:**
- `docs/BRAND.md` — canonical brand reference: palette, identity system, typography, voice, motion language
- Design tokens in `tailwind.config.js` (theme extension) and `src/index.css` (CSS custom properties on `:root`); body background/text updated to parchment/text-body
- `src/brand/OliveBranchMark.jsx` — 140×200 SVG mark with `state` prop (forest/bloomed apex olive)
- `src/brand/OliveBranchIcon.jsx` — 30×30 simplified icon for header/small contexts
- `src/brand/WorkingDots.jsx` — three-dot pulse animation, `prefers-reduced-motion`, accessible `role="status"` + live region
- `src/brand/EmptyGarden.jsx` — empty gallery state with brand voice copy
- `src/App.jsx` — header replaced with OliveBranchIcon + italic Georgia wordmark; tag chips restyled to forest-08/rounded-chip; Spinner replaced with WorkingDots in card tiles and loading state; EmptyGarden replaces "No uploads yet."; all `text-red-500` replaced with `text-earth font-serif italic`; `index.html` title updated to "Heirlooms — your garden"
- `vitest` + `@testing-library/react` test infrastructure added; 3 smoke tests for OliveBranchMark

**Key decisions:**
- JSX (not TSX) throughout to match project convention; relative imports (no `@/` alias) to match existing `./App` convention
- `EmptyGarden` takes optional `onUpload` prop (no web upload yet, so button is hidden when prop is absent — avoids a dead CTA)
- Tag chips use Tailwind arbitrary values (`text-[11px]`, `px-[9px]`, `py-[3px]`) per brief; these match the brief's specified sizes
- `WorkingDots` replaces `Spinner` for image/video thumbnail loading tiles (the closest existing analogue to "upload-in-progress tile"); `Spinner` component removed as no longer needed
- Forest tints (`forest-04`, `forest-08`, etc.) defined as named Tailwind colors so opacity values stay enumerable and don't drift from the CSS variables

**Test count:** 138 total (135 Kotlin + 3 frontend), 137 passing, 1 skipped.

---

## Session — 2026-05-07 (post-v0.16.1 doc follow-ups)

**PA brief:** Refresh Cloud Run revision identifiers in PA_NOTES.md and add an explicit test count to the v0.16.1 entry in VERSIONS.md.

**Cloud Run revisions verified via `gcloud run services describe`:**
- `heirlooms-server`: `heirlooms-server-00021-fqb` — unchanged. No server code was deployed in v0.16.1, so no revision bump. Confirmed stable.
- `heirlooms-web`: moved from `heirlooms-web-00006-wlf` → `heirlooms-web-00008-9qv`. Web was redeployed for the tag-dropdown fix.

**Test count for v0.16.1:** No new tests were added. The Android OOM fix is a memory-pressure scenario requiring a real constrained-heap device — not unit-testable. HeirloomsWeb has no frontend test runner. Count remains 135 total, 134 passing, 1 skipped (FFmpeg video thumbnail — runs in Docker).

**Also took the soft suggestion from the SE brief:**
- One-line comment on `suppressBlurRef` in `HeirloomsWeb/src/App.jsx` explaining why `e.preventDefault()` / `e.relatedTarget` are unreliable — prevents a future reader from "simplifying" the fix away.
- Strengthened KDoc on `Uploader.uploadViaSigned(File, ...)` with explicit warning against `file.readBytes()`.

**Commit:** `cfbc501` — `docs: post-v0.16.1 follow-ups (refresh Cloud Run revisions, add test count to v0.16.1)`. No tag. v0.16.1 is already tagged.

---

## Phase 1 — Product brainstorm

**Prompt:** "Hi Claude! I'm new here. Could you brainstorm creative concepts?"
Chose: "A project or product" / "I have a rough idea — help me expand it"

**What happened:** A brainstorm around digital legacy and inheritance. Eight concept
cards were generated covering the digital safe, dead man's switch, digital estate
planner, memory book, time-locked messages, collective memory space, digital rights
passport, and digital executor service.

---

**Prompt:** "Tell me more about concept: a personal digital vault app where people
can organise their photos, videos, and messages into a time-capsule-style legacy
that gets unlocked for next of kin."

**What happened:** A detailed breakdown of how the vault would work in two phases
(building the vault while alive; the unlock after death), a feature comparison
against cloud storage, a UI sketch, and an analysis of key challenges. The milestone
delivery mechanic — a video arriving on a child's 18th birthday — was identified as
the single most powerful differentiator.

---

## Phase 2 — Android app (v1, Java)

**Prompt:** "I'd like to start off by creating an Android app, just for me.
When I use the universal Android 'share with' function for any image or video,
I can choose 'Share with APPLICATION_NAME'. The application simply does a HTTP POST
to AN_ENDPOINT with the video/photo contents as the body. Content-Type should reflect
the file type. APPLICATION_NAME and AN_ENDPOINT are configurable in application
properties. I should be able to build the project using Gradle."

**What was built:** A complete Android project in Java. `ShareActivity` registers as
a share target for `image/*` and `video/*`, reads the file via `ContentResolver`,
and HTTP POSTs it using OkHttp. App name and endpoint configured via
`assets/config.properties`. Transparent activity theme so no UI flash appears.

---

**Prompt:** "I have Android Studio installed and I've opened the folder but when I
Build → Make Project I see the error... [missing gradle-wrapper.jar]"

**Decision:** Added `gradle/wrapper/gradle-wrapper.jar` to the project and added a
`run-tests.sh` script that auto-downloads it if missing, so the project works without
Android Studio being involved in the build setup.

---

## Phase 3 — Kotlin rewrite + settings screen

**Prompt:** "Could we use Kotlin rather than Java for this Android project?"

**What was built:** Full rewrite of the Android app in Kotlin. Split into four files:
`ShareActivity.kt`, `Uploader.kt`, `EndpointStore.kt`, and `SettingsActivity.kt`.
Added a settings screen accessible from the share sheet so the endpoint URL can be
changed without editing config files. Endpoint stored in `SharedPreferences`.

---

## Phase 4 — Backend server (HeirloomsServer)

**Prompt:** "Could we now look at the server side? I'd like to look at what we need
to do in order to have a very simple server, that the android app could post images
or videos to, that would store them."

**Decision:** Kotlin/http4k server chosen over Spring Boot for minimal footprint.
PostgreSQL for metadata, MinIO (S3-compatible) for file storage. Flyway for database
migrations. HikariCP connection pool. AWS SDK v2 S3 async client with
`forcePathStyle(true)` for MinIO compatibility.

**What was built:** `HeirloomsServer` with four endpoints:
- `POST /api/content/upload` — receives file bytes, stores to S3/MinIO, records
  metadata in PostgreSQL, returns storage key
- `GET /api/content/uploads` — returns JSON array of all uploads
- `GET /health` — returns 200 "ok"

`AppConfig` reads from `application.properties` locally or directly from environment
variables (by exact uppercase name: `DB_URL`, `S3_ACCESS_KEY` etc.) when running in
Docker. The env var approach was fixed after an early bug where underscores were
converted to dots (`S3_ACCESS_KEY` → `s3.access.key`) but the property lookup used
hyphens (`s3.access-key`), causing silent config failure.

---

## Phase 5 — Docker + end-to-end tests (HeirloomsTest)

**Prompt:** "I wonder if we might want a third 'project' within Heirloom that runs
integration tests against the server."

**What was built:** `HeirloomsTest` — a Gradle project using Testcontainers and OkHttp
that spins up the full Docker Compose stack (PostgreSQL, MinIO, minio-init, HeirloomsServer)
and runs API tests and journey tests against it.

**Key decisions:**
- Testcontainers 2.0.5 chosen over 1.x because Docker Engine 29.x raised its minimum
  API version to 1.40, and docker-java (used by Testcontainers 1.x) hardcoded API
  version 1.32, making it incompatible. Testcontainers 2.x handles API negotiation
  correctly.
- Playwright removed from journey tests because Chromium cannot reach the Testcontainers
  socat proxy port from its sandboxed process. All journey tests use OkHttp instead.
- Shadow plugin (8.1.1) with `mergeServiceFiles()` used instead of a hand-rolled fat
  JAR task. The original `DuplicatesStrategy.EXCLUDE` approach silently dropped
  `META-INF/services/org.flywaydb.core.extensibility.Plugin`, which prevented the
  Flyway PostgreSQL plugin from registering, causing "relation uploads does not exist"
  errors even though the migration file was present and correctly named.

---

## Key bugs fixed during HeirloomsTest development

**Docker socket on macOS** — `/var/run/docker.sock` and `~/.docker/run/docker.sock`
return stub 400 responses on macOS Docker Desktop. The working socket is at
`~/Library/Containers/com.docker.docker/Data/docker.raw.sock`. Fixed by auto-detecting
socket candidates in `HeirloomsTestEnvironment` and setting `DOCKER_HOST` accordingly.

**Ryuk failing before test code** — Ryuk's static initialiser fires before any test
code runs and fails independently. Fixed by `ryuk.disabled=true` in
`~/.testcontainers.properties`.

**Testcontainers 2.x API changes** — `withLocalCompose()` was removed (local compose
is now the default); `junit-jupiter` artifact renamed to `testcontainers-junit-jupiter`.

**`AppConfig.fromEnv()` hyphen vs dot mismatch** — see Phase 4 above.

**Flyway "0 migrations applied"** — `DuplicatesStrategy.EXCLUDE` silently dropped the
Flyway service registration file. Fixed by switching to Shadow plugin with
`mergeServiceFiles()`.

**`docker-compose.yml` port format** — Testcontainers 2.x requires ports declared as
`"8080"` (no host binding) rather than `"8080:8080"` so it can manage port mapping
via its socat ambassador container.

**`version:` field in docker-compose.yml** — removed as it is obsolete in Compose V2
and was generating warnings.

**GRADLE_OPTS native crash** — `GRADLE_OPTS="-Dorg.gradle.native=false"` added
throughout Docker builds to prevent a SIGSEGV crash of `libnative-platform-file-events.so`
on Apple Silicon (ARM64).

---

## One-time machine setup

```
~/.testcontainers.properties

docker.host=unix:///Users/YOUR_USERNAME/Library/Containers/com.docker.docker/Data/docker.raw.sock
testcontainers.reuse.enable=false
ryuk.disabled=true
```

Replace `YOUR_USERNAME` with your macOS username. This is the only persistent
machine-level configuration required. Everything else is self-contained in the repo.

---

## Final state

**10 tests passing** across two test classes:

`UploadApiTest`: health check, POST image returns 201, POST video returns 201, POST
without Content-Type returns 201, POST empty body returns 400, GET uploads returns
JSON array, uploaded file appears in listing.

`UploadJourneyTest`: upload and verify in listing, multi-type upload journey,
health endpoint reachable.

Build time on a warm cache (Docker layers cached): under 20 seconds.
Build time on a cold cache (first run): 3–5 minutes for dependency downloads.

---

## Domain registration

**Date:** 30 April 2026

`heirlooms.digital` registered. Several names were considered during this session:

- `digital-legacy.com` — available but too generic
- `heirloom.digital` — strong first choice, but misspelled as "hierloom" on first
  attempt (fat fingers)
- `heirloom.co.uk` — correct spelling but country-locked
- `heirlooms.digital` — chosen: plural feels warmer ("a collection" rather than
  "a single object"), .digital is thematically appropriate, not country-locked
- `heirlooms.com` — parked on venture.com, potentially acquirable in future if the
  project grows to warrant it

The project name was updated from **Heirloom** to **Heirlooms** to match the domain.
The rename is the first task queued for the next development session.

---

## Session — 30 April 2026 (v0.3.0 polish + package rename)

**Fix: `Uploader.kt` compile error**

`IntRange` implements `Iterable`, not `Sequence`, so calling `.zip(Sequence)` on it
failed to compile. Fixed by inserting `.asSequence()` before `.zip()`.

---

**Tag: v0.3.0**

Annotated git tag `v0.3.0` created on `main` to mark the state of the project at the
end of the founding development session.

---

**Package rename: `com.heirloom` → `digital.heirlooms`**

Queued at the end of the previous session to align with the `heirlooms.digital` domain.
Completed across all three subprojects — 22 Kotlin source files, 3 `build.gradle.kts`
files, and the corresponding source directory layout:

- `HeirloomsApp`: `com/heirloom/app/` → `digital/heirlooms/app/`
- `HeirloomsServer`: `com/heirloom/server/` → `digital/heirlooms/server/`
- `HeirloomsTest`: `com/heirloom/test/` → `digital/heirlooms/test/`

---

**TEAM.md added**

Documents the team structure: Bret Adam Calvey as Founder & CTO, the PA (claude.ai)
for strategic/architectural thinking, and the Software Engineer (Claude Code in
IntelliJ) for hands-on implementation. Establishes that the Software Engineer commits
but Bret always does the final push.

---

**PA_NOTES.md added**

The PA's (claude.ai) working memory file, committed to the repo so it persists across
sessions and is visible to all team members. Captures Bret's preferences and working
style, project facts to always remember, pending decisions, known gotchas, and team
reminders.

---

**SE_NOTES.md added**

The Software Engineer's (Claude Code) own working memory file. Covers how to get
session context, commit conventions, project structure at a glance, and code-level
things worth remembering between sessions.

---

**docs/chats/2026-04-30-initial-chat.md added**

The original claude.ai founding session chat, formatted as markdown. 356 turns
spanning 24–30 April 2026. Converted from `Original_chat.txt` (now removed):
day-separated sections, `**Human**` / `**Claude**` blocks, action/tool lines
stripped, duplicate lines deduplicated.

---

**Docs reorganisation**

All markdown files except `README.md` moved from the project root into `docs/`:
`PROMPT_LOG.md`, `ROADMAP.md`, `TEAM.md`, `PA_NOTES.md`, `SE_NOTES.md`.
`README.md` updated with a Docs table linking to each file with a description.

## Session — 1 May 2026 (Milestone 3 planning + deployment research)

**Milestone 3 patch produced**

The PA produced a patch for the Software Engineer containing three new files
in a new `deploy/` folder at the repo root:
- `docker-compose.yml` — production compose with named volumes, restart policies,
  host port binding (8080:8080), and a `build:` directive pointing at HeirloomsServer
- `.env.example` — credential template; the real `.env` is gitignored
- `README.md` — step-by-step setup guide for a VPS or home server

Key differences from the test compose:
- Credentials sourced from .env (not hardcoded)
- Named volumes: postgres_data, minio_data (data survives container restarts)
- `restart: unless-stopped` on postgres, minio, and heirloom-server
- Port 8080 bound to the host as 8080:8080
- `build:` context points to ../HeirloomsServer so `docker compose up --build`
  compiles and packages the JAR automatically

HeirloomsTest's docker-compose.yml was not modified.

---

**Deployment research — cloud and VPS options evaluated**

Google Cloud Run + Cloud SQL + Cloud Storage was evaluated as a cloud path.
Viable but Cloud SQL alone costs ~£10-15/month, which is disproportionate for
a personal project at this stage.

Hetzner CX22 (~€4/month) chosen as the recommended deployment target.
Runs the full stack on a single VPS via the Milestone 3 docker-compose.yml.

---

**Agreed next steps (queued for next session)**

1. Provision a Hetzner CX22 VPS
2. Add a DNS A record: `heirlooms.digital` → VPS IP (TTL 300)
3. SSH in, clone repo, copy `.env.example` to `.env`, fill in passwords
4. `docker compose up -d --build` from the `deploy/` folder
5. Verify: `curl http://heirlooms.digital:8080/health`
6. Update Android app endpoint to `http://heirlooms.digital:8080/api/content/upload`

HTTPS (via Caddy reverse proxy + Let's Encrypt) deferred to Milestone 4.

---

## Session — 2026-05-01 (Milestone 3 — self-hosted deployment)

**deploy/ folder added**

Three files added to a new `deploy/` folder at the repo root:
- `docker-compose.yml` — production compose with named volumes, restart policies,
  host port binding (8080:8080), and a `build:` directive pointing at HeirloomsServer
- `.env.example` — credential template; the real `.env` is gitignored
- `README.md` — step-by-step setup guide for a VPS or home server

Key differences from the test compose:
- Credentials sourced from .env (not hardcoded)
- Named volumes: postgres_data, minio_data (data survives container restarts)
- restart: unless-stopped on postgres, minio, and heirloom-server
- Port 8080 bound to the host as 8080:8080
- build: context points to ../HeirloomsServer so `docker compose up --build`
  compiles and packages the JAR automatically

HeirloomsTest's docker-compose.yml was not modified.

---

## Session — 2026-05-01 (Swagger UI / OpenAPI documentation)

**Prompt:** "Is it possible to add swagger to our backend server so I can access
detailed API documentation in a browser and use it as a simple client for our server?"

**What was built:** Interactive API documentation served at `GET /docs`, backed by
a fully self-hosted Swagger UI (no CDN dependency).

- Added `http4k-contract`, `http4k-format-jackson`, and `org.webjars:swagger-ui:5.11.8`
  to `build.gradle.kts`
- Converted `UploadHandler.kt` from plain `routes()` to http4k contract routing;
  the contract auto-generates and serves an OpenAPI 3.0 spec at
  `GET /api/content/openapi.json`
- Swagger UI assets served from the webjar on the classpath at `/docs/` via
  http4k's `static(ResourceLoader.Classpath(...))` handler
- `swagger-initializer.js` overridden via a specific route listed before the static
  handler, pointing Swagger UI at `/api/content/openapi.json`
- `GET /docs` redirects to `/docs/index.html`
- `Body.binary(ContentType("application/octet-stream")).toLens()` declared in the
  upload route meta so Swagger UI renders a file picker for the POST endpoint.
  Note: `binary` is an extension on `org.http4k.core.Body.Companion` from the
  `org.http4k.lens` package — `org.http4k.lens.Body` does not exist in http4k 4.46
- Updated `UploadHandlerTest.kt`: `GET /api/content/upload` now returns 404 (not 405)
  because http4k-contract does not produce METHOD_NOT_ALLOWED for wrong methods on
  contract-owned paths

**Key decision — CDN vs webjar:**
CDN approach (unpkg) was considered first — simpler, no extra dependency, but requires
the browser to have internet access. Webjar was preferred: all assets are bundled in the
fat JAR, the server is fully self-contained, and no internet access is needed at runtime.

**Files changed:**
- `HeirloomsServer/build.gradle.kts`
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/UploadHandler.kt`
- `HeirloomsServer/src/test/kotlin/digital/heirlooms/server/UploadHandlerTest.kt`
- `HeirloomsServer/README.md`
- `HeirloomsServer/PROMPT_LOG.md`
- `docs/SE_NOTES.md`

---

## Milestone 3 — 2026-05-05 (GCP deployment, GCS storage, API key auth, end-to-end test)

Full-stack milestone: server deployed to Google Cloud Run, storage migrated to GCS,
API key authentication added across server and Android app, end-to-end photo upload
confirmed from a real Android device.

**What was built:**

### HeirloomsServer

- **`GcsFileStore.kt`** — new `FileStore` implementation backed by Google Cloud Storage.
  Service account credentials are supplied as a JSON string via the `GCS_CREDENTIALS_JSON`
  environment variable and loaded in-memory; credentials are never written to disk.
  Activated by setting `STORAGE_BACKEND=GCS`, `GCS_BUCKET`, and `GCS_CREDENTIALS_JSON`.

- **Cloud SQL socket factory** — added `com.google.cloud.sql:postgres-socket-factory:1.19.0`
  dependency to support IAM-authenticated connections to Cloud SQL (PostgreSQL) via the
  Cloud SQL Auth Proxy socket.

- **`ApiKeyFilter.kt`** — http4k `Filter` that enforces `X-Api-Key` header authentication
  on all requests. `/health` is unconditionally exempt (required for Cloud Run health checks).
  Returns HTTP 401 for missing or incorrect keys. Key value read from the `API_KEY`
  environment variable via `AppConfig`. Filter is only wired in `Main.kt` when `apiKey`
  is non-empty, so local development works without a key.

### HeirloomsApp

- **`EndpointStore.kt`** — added `getApiKey()` / `setApiKey()` backed by SharedPreferences
  key `api_key`.
- **`Uploader.kt`** — added optional `apiKey: String?` parameter to `upload()`;
  injects `X-Api-Key` header when non-blank.
- **`SettingsActivity.kt` / `activity_settings.xml`** — added a masked password input
  field for the API key alongside the existing endpoint URL field.
- **`ShareActivity.kt`** — reads API key from `EndpointStore` and passes it to `upload()`.

### GCP infrastructure provisioned

- **Cloud Run** — HeirloomsServer deployed as a containerised service (Artifact Registry,
  Jib build)
- **Cloud SQL** — PostgreSQL instance, connected via Cloud SQL socket factory
- **Cloud Storage** — GCS bucket for file storage
- **Secret Manager** — secrets for API key and service account credentials
- **Service account** — created with roles scoped to Cloud SQL, GCS, and Secret Manager

### End-to-end validation

Photo uploaded from a physical Android device → Cloud Run endpoint → stored in GCS bucket.
Upload confirmed by checking the GCS bucket directly.

**Files changed/added:**
- `HeirloomsServer/build.gradle.kts` — GCS and Cloud SQL socket factory dependencies
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/GcsFileStore.kt` (new)
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/ApiKeyFilter.kt` (new)
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/AppConfig.kt` — GCS fields, `apiKey`
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/Main.kt` — GCS and filter wiring
- `HeirloomsApp/app/src/main/kotlin/digital/heirlooms/app/EndpointStore.kt`
- `HeirloomsApp/app/src/main/kotlin/digital/heirlooms/app/Uploader.kt`
- `HeirloomsApp/app/src/main/kotlin/digital/heirlooms/app/SettingsActivity.kt`
- `HeirloomsApp/app/src/main/kotlin/digital/heirlooms/app/ShareActivity.kt`
- `HeirloomsApp/app/src/main/res/layout/activity_settings.xml`
- `HeirloomsApp/app/src/main/res/values/strings.xml`

---

## Session — 2026-05-05 (Swagger UI — API key auth integration)

**Prompt:** "When I access the swagger UI using the Cloud Run URL /docs I get
unauthorised. The security filter is kicking in. We need to fix the filter so
it excludes the docs path, and add an Authorize mechanism in Swagger UI for
the API key."

**What was built:**

- **`ApiKeyFilter.kt`** — added `path.startsWith("/docs")` exemption so the
  Swagger UI and all its static assets load without credentials.

- **`/docs/api.json` route** — new handler (`specWithApiKeyAuth`) that calls
  the http4k contract internally to get the raw OpenAPI spec, then patches it
  with Jackson before returning it:
  - Adds `components.securitySchemes.ApiKeyAuth` (`type: apiKey`, `in: header`,
    `name: X-Api-Key`)
  - Adds a global `security: [{ApiKeyAuth: []}]` block
  - Overrides `servers` to `[{url: "/api/content"}]` (http4k generates `"/"` which
    caused Swagger UI to POST to `/upload` instead of `/api/content/upload`)
  - Removes per-operation `security: []` entries — an empty array overrides the
    global block, so Swagger UI was silently dropping the key after re-authorisation

- **`swaggerInitializerJs`** updated:
  - `url` changed from `/api/content/openapi.json` to `/docs/api.json`
    (the patched spec endpoint, already exempt from the filter)
  - `persistAuthorization: true` — key survives page refresh
  - `tryItOutEnabled: true` — request form open by default, no extra click

- **`docker-compose.yml`** (test) — added `API_KEY: "${API_KEY:-}"` so the key
  can be injected for manual local testing without breaking the e2e tests
  (which run without a key and rely on the filter being inactive).

**Key gotcha — per-operation `security: []`:**
http4k generates `"security": []` on every operation when no contract-level
security is configured. In OpenAPI 3, an empty array means "no security" and
overrides the global block. This caused Swagger UI to stop sending the key after
logout and re-authorisation, despite the Authorize dialog appearing to work.
Fix: remove the per-operation `security` field so operations inherit global.

**Files changed:**
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/ApiKeyFilter.kt`
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/UploadHandler.kt`
- `HeirloomsTest/src/test/resources/docker-compose.yml`

**Validated end-to-end:**
Deployed to Cloud Run (revision `heirlooms-server-00006-ckl`). Swagger UI loads at
`https://heirlooms-server-340655233963.europe-west2.run.app/docs` without credentials,
API key authorisation works via the Authorize button, and POST /upload returns 201.
Tagged as **v0.6.0**.

---

## Milestone 4 — Web gallery UI + large file support (6 May 2026)

### Part 1 — File proxy endpoint + HeirloomsWeb

**Prompt:** Build Milestone 4: a file proxy endpoint on HeirloomsServer and a new HeirloomsWeb sub-project (React gallery).

**What was built:**

*HeirloomsServer:*
- `FileStore.get(key)` added to interface; implemented in LocalFileStore, S3FileStore, GcsFileStore
- `GET /api/content/uploads/{id}/file` — streams file bytes from GCS with correct Content-Type; 404 if not found
- `uploadedAt: Instant` added to UploadRecord; list endpoint JSON now includes it
- CORS filter added (all origins); handles OPTIONS preflight before ApiKeyFilter

*HeirloomsWeb (new sub-project):*
- React 18 + Tailwind CSS + Vite; gallery grid with image thumbnails, file icons for videos, upload date, MIME type, file size; lightbox on click
- API key entered at login per session, held in React state only (cleared on reload, never stored)
- Images fetched as blob URLs (fetch + createObjectURL) so X-Api-Key header can be sent
- Multi-stage Dockerfile: Node 22 build → nginx:alpine

Deployed to Cloud Run (revision `heirlooms-server-00007-7vw`). Gallery confirmed working at http://localhost:5173 against production server. Tagged as **v0.7.0**.

---

### Part 2 — Large file upload via GCS signed URLs

**Problem discovered:** Uploading a 34.57 MB video from the Android app returns HTTP 413. Root cause: Cloud Run enforces a hard 32 MB request body limit at the load balancer level — no server-side config change can fix this.

**Solution — three-step signed URL upload flow:**

1. Mobile app `POST /api/content/uploads/prepare` with `{"mimeType":"video/mp4"}` → server returns `{"storageKey":"uuid.mp4","uploadUrl":"https://...signed-gcs-url..."}` (15-minute expiry)
2. Mobile app `PUT {signedUrl}` with file bytes **directly to GCS** — bypasses Cloud Run entirely, no size limit
3. Mobile app `POST /api/content/uploads/confirm` with `{"storageKey":"...","mimeType":"...","fileSize":...}` → server records metadata in the database

**Server changes:**
- `DirectUploadSupport` interface + `PreparedUpload` data class (new file)
- `GcsFileStore` now implements `DirectUploadSupport`; switched from `GoogleCredentials` to `ServiceAccountCredentials` so the credentials can sign URLs (V4 signing); `prepareUpload()` generates a signed PUT URL with 15-minute expiry
- `POST /api/content/uploads/prepare` and `POST /api/content/uploads/confirm` added as contract routes; prepare returns 501 if the storage backend doesn't support direct upload (i.e. local/S3)

**Android app changes:**
- `Uploader.uploadViaSigned()` — new method implementing the three-step flow; no API key sent on the GCS PUT (signed URL is self-authenticating)
- `ShareActivity` now calls `uploadViaSigned()` instead of `upload()`; derives base URL from stored endpoint by splitting on `/api/`
- OkHttp write timeout increased from 120s → 300s to accommodate large video uploads

**Note for deployment:** The new server image must be built and deployed to Cloud Run before large video uploads will work. The existing `POST /api/content/upload` direct endpoint still works for small files. No change to stored endpoint format in the Android app.

**Validated end-to-end (6 May 2026):**
Server deployed to Cloud Run (revision `heirlooms-server-00008-vt7`). Fresh APK installed via `adb install -r`. Large video (34.57 MB) shared successfully from Android — three-step signed URL flow completed transparently. Tagged as **v0.8.0**.

---

## Session summary — 6 May 2026 (continued from Milestone 4)

### Video player + streaming (v0.9.0)

**Video player:** HeirloomsWeb now shows a video icon with "Click to play" for video files in the gallery. Clicking opens a native `<video controls>` modal.

**Streaming:** Initial implementation fetched the full video as a blob before playback (slow for large files). Replaced with GCS signed read URLs — a new `GET /api/content/uploads/{id}/url` endpoint generates a 1-hour signed URL; the video element uses it as `src` directly. The browser handles streaming, buffering, and seeking natively. No full download required.

**Dockerfile fix:** Docker Desktop on macOS was dropping the build daemon connection during long Gradle downloads inside the container, requiring a manual Docker restart every deployment. Fixed by removing the multi-stage build: JAR is now built locally with `./gradlew shadowJar` first, then `docker build` simply copies the pre-built JAR into a JRE image. Build time: ~2 seconds. PA_NOTES.md updated with the new deploy sequence.

**Validated end-to-end:** Video streaming confirmed working. Server deployed to Cloud Run revision `heirlooms-server-00009-58m`. Tagged as **v0.9.0**.

---

## Session — 2026-05-06 (All endpoints in Swagger)

`GET /uploads/{id}/file` and `GET /uploads/{id}/url` were registered directly
in `routes()`, making them invisible to the http4k contract and Swagger UI.

**Fix:** converted both to `ContractRoute` entries inside the contract.

Key discovery: `String.div(PathLens<A>)` is a top-level extension in
`org.http4k.contract.ExtensionsKt` and requires `import org.http4k.contract.div`.
Without this import, the `/` operator was unresolved and the meta block failed
with cascade errors.

`ContractRouteSpec1<A>.div(String)` (member method) returns
`ContractRouteSpec2<A, String>`, whose `Binder.to` takes `(A, String) -> HttpHandler`.
The trailing String parameter is the constant path segment ("file"/"url") and is
ignored with `_` in the handler.

String-based paths like `"/uploads/{id}/file"` in contract routes do NOT do
path variable matching — they are treated as literal strings, returning 404 for
all real UUID paths. Typed path lenses are required for routing to work.

A malformed UUID in the path returns 404 (route doesn't match) not 400
(the typed lens fails silently and falls through to the 404 handler).

**Files changed:**
- `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/UploadHandler.kt`
- `HeirloomsServer/src/test/kotlin/digital/heirlooms/server/UploadHandlerTest.kt`

---

## Session — 2026-05-06 (Hardcode server URL in Android app)

Now that the app targets `https://api.heirlooms.digital` exclusively, the endpoint
URL is no longer user-configurable. The settings screen is reduced to API key only.

**Changes:**

- `EndpointStore.kt` — removed `get()`, `set()`, `isConfigured()`, `DEFAULT_ENDPOINT`,
  and `KEY_ENDPOINT`. Removed `contains()` from `PreferenceStore` interface (was only
  needed by `isConfigured()`). Class now persists the API key only.
- `ShareActivity.kt` — replaced the stored endpoint + `baseUrl` derivation with a
  hardcoded `val baseUrl = "https://api.heirlooms.digital"`.
- `SettingsActivity.kt` — removed endpoint `EditText` and URL validation; screen now
  shows API key field only.
- `activity_settings.xml` — removed endpoint label, input, and help text views;
  API key section anchored directly below the title.
- `strings.xml` — removed `settings_endpoint_label`, `settings_endpoint_hint`,
  `settings_help`, `settings_invalid_url`; updated `settings_saved` to "Settings saved".
- `EndpointStoreTest.kt` — replaced endpoint tests with equivalent API key tests
  (5 tests, all passing).

---

## Session wrap-up — 2026-05-06 (v0.10.0)

**Validated end-to-end:** Upload from Android confirmed working via
`https://api.heirlooms.digital`. Swagger UI confirmed at
`https://api.heirlooms.digital/docs/index.html`. All 6 endpoints visible.

**Cloud Run:** server deployed as revision `heirlooms-server-00002-stq`
(us-central1). Tagged as **v0.10.0**.

---

## Session — 2026-05-06 (Phase 1 thumbnail generation)

**Prompt:** Add synchronous image thumbnail generation at upload time —
Phase 1 of a three-phase pipeline (Phase 2: video first-frame via FFmpeg,
Phase 3: async generation).

**What was built:**

### Database
- `V3__add_thumbnail_key.sql` — adds nullable `thumbnail_key VARCHAR(512)`
  column to the uploads table. Nullable because existing uploads have none,
  non-image files never get one, and generation can fail silently.

### ThumbnailGenerator
- `ThumbnailGenerator.kt` — top-level `generateThumbnail(bytes, mimeType)`
  function using only `javax.imageio.ImageIO` and `java.awt` (no extra
  dependencies). Scales to fit a 400×400 bounding box preserving aspect
  ratio, outputs JPEG. Returns null for unsupported types (everything except
  image/jpeg, image/png, image/gif, image/webp) or if ImageIO can't decode
  the input. Try/catch ensures thumbnail failure never propagates to the
  upload response.

### FileStore — saveWithKey
- `FileStore.saveWithKey(bytes, key, mimeType)` added to the interface.
  Implemented in `LocalFileStore`, `S3FileStore`, and `GcsFileStore`.
  Used to store thumbnails under an explicit key (`{uuid}-thumb.jpg`)
  alongside the original file.

### Database — thumbnailKey
- `UploadRecord` gains `thumbnailKey: String? = null` (trailing default,
  backward compatible).
- All INSERT and SELECT queries updated to include `thumbnail_key`.

### Upload flow
- `buildApp` gains an injectable `thumbnailGenerator` parameter (default
  `::generateThumbnail`) so tests can inject a stub or failing lambda.
- `POST /upload` (direct path): after storing the original, calls
  `tryStoreThumbnail` to generate + store the thumbnail, then records
  `thumbnailKey` in the database. On any failure, proceeds without thumbnail.
- `POST /uploads/confirm` (signed URL path): calls
  `tryFetchAndStoreThumbnail`, which first checks if the MIME type is
  supported (skip early for videos, avoiding a wasteful GCS fetch) then
  fetches bytes, generates, and stores the thumbnail. On any failure,
  proceeds without thumbnail.
- Thumbnail stored under `{original-uuid}-thumb.jpg` in the same bucket.

### API changes
- `GET /api/content/uploads` — list JSON now includes `"thumbnailKey":null`
  or `"thumbnailKey":"uuid-thumb.jpg"` on each item.
- `GET /api/content/uploads/{id}/thumb` — new contract route. Returns the
  JPEG thumbnail if `thumbnailKey` is set; falls back to the full file if
  not. Returns 404 if the upload record doesn't exist.

### HeirloomsWeb
- `UploadCard` uses `GET /uploads/{id}/thumb` when `upload.thumbnailKey` is
  non-null (fetching the smaller thumbnail for the grid), falling back to
  `GET /uploads/{id}/file` for uploads without a thumbnail.

### Tests
- `ThumbnailGeneratorTest.kt` (8 tests): supported JPEG returns non-null,
  output is valid JPEG, unsupported type returns null, invalid bytes returns
  null, fits within 400×400, preserves aspect ratio, no upscaling for small
  images, octet-stream returns null.
- `UploadHandlerTest.kt` (11 new tests): thumbnail generated and stored for
  supported type, no thumbnail for video/mp4, upload succeeds when generator
  throws, thumbnailKey null in list for non-image, thumbnailKey present in
  list, thumb endpoint returns thumbnail bytes, thumb endpoint falls back to
  full file, thumb endpoint returns 404.

All tests passing (95 total across 6 test classes, 0 failures).

**Cloud Run:** server deployed as revision `heirlooms-server-00007-gvl`,
web as revision `heirlooms-web-00002-jjr` (us-central1). Tagged as **v0.12.0**.

---

## Session — 2026-05-06 (POST /upload JSON response)

**Fix:** `POST /upload` 201 response was returning a raw storage key string
(`780aa0d2-fd28-4ad0-8c6d-e3aec4d30fa3.jpg`). Changed to return a full JSON
object matching the shape of items in the `GET /uploads` list:
`{"id":"...","storageKey":"...","mimeType":"...","fileSize":...,"uploadedAt":"...","thumbnailKey":...}`.

`uploadedAt` is captured at the point of the `save()` call in Kotlin (very
close to the DB `DEFAULT NOW()` value — the column is not included in the
INSERT so the DB sets it independently). `Content-Type: application/json`
header added to the 201 response. All 95 tests still passing.

**Cloud Run:** server deployed as revision `heirlooms-server-00008-kdz`.

---

## Session — 2026-05-06 (Phase 2 thumbnails — video first-frame via FFmpeg)

**Prompt:** Extend the thumbnail pipeline to support video files. Add FFmpeg to the Docker image, extend `ThumbnailGenerator` to extract the first frame from video/mp4, video/quicktime, video/x-msvideo, and video/webm using FFmpeg via `ProcessBuilder`, and add tests.

**What was built:**

- **`Dockerfile`** — `apt-get install -y ffmpeg` added before `USER heirloom` (runs as root).

- **`ThumbnailGenerator.kt`** — dispatches to `extractVideoThumbnail` for video MIME types. Writes video bytes to a temp file, runs `ffmpeg -vframes 1 -f image2 output.jpg` via `ProcessBuilder` with a 30-second timeout, reads the output JPEG, scales via the shared `scaleAndEncode` helper. All failures return null gracefully and temp files are always cleaned up in `finally`.

- **`THUMBNAIL_SUPPORTED_MIME_TYPES`** now includes the four video types, so the confirm-flow's `tryFetchAndStoreThumbnail` no longer skips them.

- **Tests (ThumbnailGeneratorTest):** 2 new — `valid MP4 produces non-null thumbnail` (uses `assumeTrue(isFFmpegAvailable())` to skip gracefully when FFmpeg is absent) and `corrupt video returns null gracefully` (always runs).

- **Test adjustment:** `returns null for unsupported MIME type` updated from `video/mp4` (now supported) to `audio/mpeg`. `no thumbnail generated for unsupported MIME type` in `UploadHandlerTest` renamed to `no thumbnail stored when video bytes are invalid`.

**Result:** 97 tests, 0 failures, 1 skipped locally (valid-MP4 test runs in Docker where FFmpeg is installed).

**Deployed:** Cloud Run revision `heirlooms-server-00009-gdv`. Health check confirmed `ok`. Tagged as **v0.13.0**.

---

## Session — 2026-05-06 (Web gallery — video thumbnails)

**Prompt:** Update HeirloomsWeb to use the Phase 2 video thumbnails.

**What was built:**

`UploadCard` previously ignored `thumbnailKey` for video files, always showing a generic
video icon. Now:

- Videos with a `thumbnailKey` pre-fetch the JPEG thumbnail (via the same `/thumb` endpoint
  used for images) and display it in the card with a semi-transparent play-button overlay.
  Clicking the card still opens the video via the signed read URL.
- Videos without a `thumbnailKey` keep the existing `VideoIcon` + "Click to play" behaviour.
  While a thumbnail is loading, a spinner is shown.
- Added `PlayIcon` component (circular button, 48×48, play arrow).

**Files changed:**
- `HeirloomsWeb/src/App.jsx`

**Deployed:** Cloud Run revision `heirlooms-web-00003-4nx`.

---

## Session — 2026-05-06 (EXIF and video metadata extraction)

**Prompt:** Add EXIF and video metadata extraction to HeirloomsServer. Metadata extracted at upload time alongside thumbnail generation and stored in six new nullable database columns (captured_at, latitude, longitude, altitude, device_make, device_model). GPS pin icon in HeirloomsWeb for cards with coordinates.

**What was built:**

### HeirloomsServer
- **`V4__add_metadata_columns.sql`** — adds six nullable columns to the uploads table.
- **`MetadataExtractor.kt`** (new) — `MediaMetadata` data class; `MetadataExtractor` class with `extract(bytes, mimeType): MediaMetadata`. Image path uses `com.drewnoakes:metadata-extractor:2.19.0` for EXIF GPS (lat/lon/alt), capture timestamp, and device make/model. Video path runs `ffprobe -v quiet -print_format json` and parses `format.tags.creation_time`, ISO 6709 location string, and Apple QuickTime make/model tags. All failures return `MediaMetadata()` with all nulls.
- **`UploadRecord`** — six new nullable fields.
- **`Database.kt`** — all INSERT and SELECT queries updated. `ResultSet.toUploadRecord()` private extension eliminates duplicated mapping code.
- **`UploadHandler.kt`** — `buildApp` gains `metadataExtractor` parameter (default `MetadataExtractor()::extract`). Direct upload path calls metadata extraction on the request bytes. Confirm path refactored to call `fetchBytesIfNeeded` once, passing bytes to both `tryStoreThumbnail` and `metadataExtractor` (single GCS fetch instead of two). `UploadRecord.toJson()` private extension: metadata fields omitted when null; used in both upload and list handlers.

### HeirloomsWeb
- **`App.jsx`** — `PinIcon` component (📍 with lat/lon tooltip). `UploadCard` outer div gains `relative` class; pin shown when both `latitude` and `longitude` are non-null.

### Tests
- `MetadataExtractorTest` (4 tests): GPS JPEG with hand-crafted TIFF/EXIF bytes returns correct lat/lon/alt; plain JPEG returns null coords; unsupported MIME type returns all nulls; invalid bytes return null.
- `UploadHandlerTest`: 1 new test (metadata exception does not fail upload); three confirm-flow tests updated to stub `storage.get()`.
- **102 tests total, 101 passing, 1 skipped** (FFmpeg video thumbnail test — runs in Docker).

**Key gotchas:**
- Adding `metadataExtractor` as the last `buildApp` parameter broke existing tests that used trailing lambda syntax for `thumbnailGenerator`. Fixed by using named parameter syntax throughout.
- Confirm path previously fetched bytes inside `tryFetchAndStoreThumbnail`. Refactored to fetch once and share with metadata extraction.

---

## Session — 2026-05-06 (Metadata extraction debugging and stabilisation)

**Context:** Follow-on to the metadata extraction session. End-to-end testing with a real Samsung Galaxy A02s revealed several issues that were diagnosed and fixed iteratively.

**Issues found and fixed:**

### capturedAt missing on Samsung
Samsung writes `DateTime` to `ExifIFD0Directory` rather than `DateTimeOriginal` in `ExifSubIFDDirectory`. Added two fallbacks in `extractCapturedAt()`: IFD0 DateTime, then SubIFD DateTimeDigitized. Deployed as `heirlooms-server-00011-gbq`.

### GPS returning (0, 0)
Samsung entry-level cameras write GPS IFD tags with zero values when the GPS fix hasn't been acquired at shutter time. The library parsed them as `GeoLocation(0.0, 0.0)` rather than null. Added a filter: if both lat and lon are exactly 0.0, treat as null. Deployed as `heirlooms-server-00012-6ll`.

### OutOfMemoryError on large image uploads
Cloud Run default 512Mi heap was exhausted when loading a 5.4 MB photo: GCS `readAllBytes()` (5.4 MB) + `BufferedImage` decode (4160×3120 = ~52 MB) + JVM overhead. Two fixes: (1) increased Cloud Run memory to 2Gi; (2) metadata extraction in the confirm path now calls `GcsFileStore.getFirst()` which streams only the first 64 KB from GCS via `Storage.reader()` ReadChannel — JPEG EXIF is always within that range. Thumbnails still fetch the full file. Deployed as `heirlooms-server-00014-97p`.

### No metadata at all (mimeType: "image/*")
Samsung Gallery provides `intent.type = "image/*"` (wildcard) in the share intent. The app was using this directly, so uploads were stored as `.bin` with MIME type `image/*`, which is not in the metadata or thumbnail supported sets. Fixed by skipping wildcards and falling back to `contentResolver.getType(uri)` for the real specific type. Installed via ADB.

### Silent upload failure (SecurityException not caught)
`MediaStore.setRequireOriginal()` requires `ACCESS_MEDIA_LOCATION`. When denied, `openInputStream()` on the original URI threw `SecurityException`, which propagated uncaught through `catch (e: IOException)` and silently killed the coroutine. Fixed: (1) `readBytes()` wraps the entire `setRequireOriginal` + `openInputStream` in a single try/catch and falls back to the plain URI; (2) catch block in `ShareActivity` changed from `IOException` to `Exception`.

**End state:** Photo shared from Samsung Galaxy A02s → full metadata response including `capturedAt`, `latitude`, `longitude`, `deviceMake`, `deviceModel`. Coordinates confirmed real (East Midlands, UK). GPS pin 📍 visible in web gallery.

**Android gotchas for future reference:**
- `ACCESS_MEDIA_LOCATION` must be declared in manifest AND requested at runtime AND `setRequireOriginal()` must be called — three separate requirements
- Samsung Galaxy shares with wildcard MIME types
- Notification channel importance is immutable after first creation — bump the channel ID to change it
- Samsung Camera "Location tags" toggle (in Camera Settings) is separate from the system Location permission

---

## Session — 2026-05-06 (Image rotation)

**Prompt:** Add the ability to rotate images 90° in the web gallery. Rotation persists and applies to both thumbnail and lightbox view.

**What was built:**

- **`V5__add_rotation.sql`** — `rotation INT NOT NULL DEFAULT 0` on uploads table
- **`Database.updateRotation(id, rotation)`** — UPDATE statement; `rotation` added to all SELECT queries and `UploadRecord`
- **`PATCH /api/content/uploads/{id}/rotation`** — new contract route accepting `{"rotation":0|90|180|270}`; returns 400 for invalid values, 404 if upload not found
- **`UploadRecord.toJson()`** — `rotation` always included (even 0)
- **HeirloomsWeb** — `RotateIcon` component; ↻ button in each image card's info row; `handleRotate` in Gallery with optimistic state update + fire-and-forget PATCH call; CSS `transform: rotate(Xdeg)` on thumbnail image with `overflow-hidden` container clipping; `Lightbox` accepts `rotation` prop and swaps `max-w`/`max-h` at 90°/270° so portrait-rotated images fill the viewport; `lightboxUrl` state replaced with `lightbox: {url, rotation}` object
- **5 new tests** in `UploadHandlerTest`: valid rotation returns 200 + verifies DB call, invalid rotation returns 400, upload not found returns 404, rotation field in list response, rotation defaults to 0

**107 tests total, 106 passing, 1 skipped** (FFmpeg video thumbnail — passes in Docker).

---

## Session — 2026-05-06 (Tags — Increment 1: schema + write API)

**Prompt:** Add tag support to HeirloomsServer. New Flyway V6 migration adds a `tags TEXT[] NOT NULL DEFAULT '{}'` column to the uploads table with a GIN index. New `PATCH /api/content/uploads/{id}/tags` endpoint accepts `{"tags":["family","2026-summer"]}` with full-replace semantics, validates each tag against `^[a-z0-9]+(-[a-z0-9]+)*$` with length 1–50, and returns 400 naming the offending tag on failure or 404 if the upload doesn't exist. Tags appear in all upload JSON responses (`POST /upload`, `GET /uploads`, `GET /uploads/{id}`) as a `tags` array, always present, empty when none. Mirror the existing rotation endpoint's structure (added in v0.15.0).

**What was built:**

- **`V6__add_tags.sql`** — `tags TEXT[] NOT NULL DEFAULT '{}'` on uploads table plus `CREATE INDEX idx_uploads_tags ON uploads USING GIN (tags)`
- **`TagValidator.kt`** — `validateTags(tags)` enforces kebab-case (`^[a-z0-9]+(-[a-z0-9]+)*$`), length 1–50, with specific rejection reasons per tag; sealed `TagValidationResult` (Valid / Invalid(tag, reason))
- **`Database.updateTags(id, tags): Boolean`** — UPDATE via JDBC `createArrayOf("text", ...)`, returns false if no row matched
- **`tags` added to `UploadRecord`** — `List<String> = emptyList()`, all SELECT queries include the column, `toUploadRecord()` reads via `getArray("tags")`
- **`PATCH /api/content/uploads/{id}/tags`** — full-replace semantics; 400 on malformed JSON or invalid tag (offending tag + reason in response body); 404 if upload not found; 200 with full updated UploadRecord JSON on success
- **`UploadRecord.toJson()`** — `tags` always included, empty array when none
- **14 new tests** in `TagValidatorTest` (unit), **8 new tests** in `UploadHandlerTest` (integration)

**129 tests total, 128 passing, 1 skipped** (FFmpeg video thumbnail — passes in Docker).

**Notes for future increments:**
- Increment 2 (read API + filtering) will use the GIN index for `tag` and `exclude_tag` query params on `GET /uploads`
- Increment 3 (web UI) will surface tags as chips and an inline editor
- Tag rename, merge, colours, and Android tagging are all out of scope and remain parked in IDEAS.md

**v0.16.0 not yet tagged** — releasing once all three increments land.

---

## Session — 2026-05-06 (Tags — Increment 2: read API + filtering)

**Prompt:** Add `tag` and `exclude_tag` query parameters to `GET /uploads` so the list can be filtered by tag using the GIN index added in Increment 1.

**What was built:**

- **`Database.listUploads(tag, excludeTag)`** — optional parameters; builds a dynamic WHERE clause using `tags @> ARRAY[?]::text[]` (GIN-indexed) and `NOT (tags @> ARRAY[?]::text[])` for inclusion/exclusion; no WHERE clause when both are null (unchanged behaviour)
- **`GET /uploads?tag=family`** — returns only uploads that have this tag
- **`GET /uploads?exclude_tag=trash`** — omits uploads that have this tag
- Both params can be combined in a single request
- Updated `listUploadsContractRoute` description to document the new params
- 5 new tests in `UploadHandlerTest` covering: tag filter, exclude_tag filter, both combined, unknown tag returns empty array, no params passes nulls

**134 tests total, 133 passing, 1 skipped** (FFmpeg).

**Cloud Run:** deployed as revision `heirlooms-server-00018-w2g`.

**v0.16.0 not yet tagged** — releasing once Increment 3 (web UI) also lands.

---

## Session — 2026-05-06 (Tags — Increment 3: web UI)

**Prompt:** Surface tags in the web gallery as chips on each card, with an inline editor backed by an autocomplete dropdown of previously used tags.

**What was built:**

- **`TagEditor` component** — removable chips per selected tag (× to remove, Backspace removes last), text input with autocomplete dropdown filtered from `allTags`, Enter or Save commits; pending input text is flushed into the tag list on Save so typing a tag and clicking Save directly works without pressing Enter first
- **`allTags`** — derived in `Gallery` via `useMemo` over all uploads, sorted, passed down to each `UploadCard`; automatically includes newly saved tags after a successful PATCH
- **Tag chips** — shown in display mode below card metadata; hidden when no tags
- **`TagIcon` SVG** — added to card header row next to rotate button; highlighted when editor is open
- **`overflow-hidden` fix** — moved from outer card div to image container (`rounded-t-xl overflow-hidden`) so the dropdown is not clipped by the card boundary
- **OpenAPI body spec** — `RotationRequest` and `TagsRequest` data classes made non-private (were `private`, causing `IllegalAccessException` in Jackson schema generator and 500s on `/docs/api.json`); `receiving(lens to example)` added to both PATCH endpoints; spec endpoint test added as a permanent regression guard
- **CORS/Swagger fix for example bodies** — examples surface in Swagger UI for both PATCH endpoints
- **Tag filtering** (Increment 2 addition) — `GET /uploads?tag=X` and `GET /uploads?exclude_tag=X` use `tags @> ARRAY[?]::text[]` against the GIN index; `.env` updated to `https://api.heirlooms.digital`

**Cloud Run:** latest revision `heirlooms-server-00021-fqb`.

**v0.16.0 not yet tagged** — releasing once all three increments are confirmed working end-to-end.

---

## Session — 2026-05-08 (v0.17.1 — share-sheet Idle state)

Added pre-upload Idle state to the Android share-sheet receive screen. When
`ShareActivity` receives a share intent it now lands in `ReceiveState.Idle`
(a new data class carrying the photo URIs, in-progress tags, current tag
input, and recent-tag list) instead of jumping straight to *Uploading*.

`IdleScreen.kt` renders: *Heirlooms* wordmark header, photo grid (1–6) or
thumbnail strip + count (7+), tag input with kebab-case validation (Earth
underline + inline italic message for invalid input, no glyphs), recent-tag
chips backed by `RecentTagsStore` (SharedPreferences, last 12 tags, updated
only on successful upload), forest *plant* pill button, ghost *cancel* button.

Tags wired through to the server: `UploadWorker` reads them from work data,
`Uploader.uploadViaSigned(file)` includes them in the confirm body, and
`confirmUploadHandler` validates and records them via `database.updateTags`.

6 new tests (4 `IdleScreenTest` Compose UI + 2 `TagValidationTest` unit).
Coil 2.5.0 added for photo preview rendering.

---

## Session — 2026-05-07 (v0.16.1 — video upload OOM fix + tag dropdown UX)

**Android: video upload OOM fix**

Root cause identified via ADB logcat: `UploadWorker` called `file.readBytes()` before `uploadViaSigned`, loading the entire video into the Java heap (201 MB growth limit on Samsung Galaxy A02s). When OkHttp then tried to allocate an 8 KB Okio buffer segment to begin the GCS PUT, the heap was exhausted — `OutOfMemoryError: Failed to allocate a 8208 byte allocation with 55224 free bytes`. Because `OutOfMemoryError` is a `java.lang.Error` (not `Exception`), the `catch (_: Exception)` in `UploadWorker` did not catch it: the coroutine crashed silently, the GCS PUT never completed, no DB record was created, and no result notification appeared.

Fix: new `Uploader.uploadViaSigned(File, ...)` overload that streams the video from disk to GCS directly using `file.asRequestBody()` / `ProgressFileRequestBody`, never loading the full content into memory. SHA-256 computed by reading the file in 8 KB chunks. `UploadWorker` now passes the `File` object directly, removing `readBytes()` entirely. Confirmed working: 1-minute Samsung video (~90 MB) uploaded in ~8 min 46 s on slow home WiFi.

**Web: tag dropdown stays open after selection**

The tag editor dropdown was closing after each selection, requiring the user to click away and back to reopen it. Three approaches were tried before finding a reliable fix:

1. Removed `setDropdownOpen(false)` from `addTag` — failed because `onBlur` was still firing and closing the dropdown.
2. Used `e.relatedTarget` in `onBlur` to detect intra-dropdown clicks — failed because Safari on macOS does not focus `<button>` elements on click, so `e.relatedTarget` was null.
3. `suppressBlurRef`: the dropdown container's `onMouseDown` sets a ref flag before `onBlur` fires on the input; `onBlur` skips the close while the flag is set; `onMouseUp` clears it. This is browser-agnostic. ✓

**Cloud Run:** `heirlooms-web-00008-9qv` (web), server unchanged at `heirlooms-server-00021-fqb`.

**v0.16.1 not yet tagged** — Bret tags after push.

---

## Session — 2026-05-08 (Milestone 6 D2 — backend + Explore basic)

**What was built.** D2 of Milestone 6: schema foundations (V9/V10 migrations), EXIF
recovery service, cursor pagination on list endpoints, plot CRUD (4 endpoints), and the
/explore page with nav entry.

**2A — EXIF recovery.** The server already extracted EXIF inline at upload time and
stored it in the existing `captured_at`, `latitude`, `longitude` etc. columns. D2 adds
a single new column: `exif_processed_at TIMESTAMPTZ`. Set to `NOW()` at INSERT time for
new uploads. Migration marks all pre-existing rows as processed. `ExifExtractionService`
is a Kotlin coroutine-based recovery service — on startup it queries `WHERE
exif_processed_at IS NULL` and re-processes any stranded rows. In normal operation this
should find nothing; it's a crash-recovery safety net. `kotlinx-coroutines-core:1.7.3`
added as a dependency.

**2B — Cursor pagination.** Both `GET /api/content/uploads` and `GET
/api/content/uploads/composted` now return `{"items":[...],"next_cursor":"..."|null}`.
Cursor encodes `(uploadedAt.epochMilli, id)` as URL-safe base64. The DB query uses a
compound `(uploaded_at < ? OR (uploaded_at = ? AND id < ?::uuid))` predicate. Limit
param: 1–200, default 50. Garden and CompostHeap updated to call `?limit=10000` and read
`data.items` — unchanged single-page behaviour for D2, proper restructure in D3.

**2C — Plot schema + CRUD.** V10 creates `plots` and `plot_tag_criteria` tables. System
*Just arrived* plot seeded with sentinel name `__just_arrived__`, sort_order -1000,
`is_system_defined = TRUE`. `owner_user_id = NULL` for all v1 plots (FK + NOT NULL at
M8). Four endpoints: GET list, POST create, PUT update (403 on system plots), DELETE
(403 on system plots). Routes added to the capsule contract block under `/api`.

**Web — /explore.** `ExplorePage` renders a 5-column paginated photo grid via the
existing `PhotoGrid` component. *Load more* button (not infinite scroll — simpler,
sufficient for v1). Empty state. Nav updated: Garden | Explore | Capsules desktop + mobile.

**Surprises / decisions:**
- `MetadataExtractor` and all EXIF extraction was already complete from earlier milestones;
  D2 just adds the tracking column and recovery service. No rewrite needed.
- `listUploads(null, null)` mocks in UploadHandlerTest needed updating to
  `listUploadsPaginated(any(), any(), any(), any())` returning `UploadPage(...)`. 13 mock
  calls updated; verify calls updated to match paginated signature.
- `capture()` with nullable `String?` type required `captureNullable(slot)` not
  `capture(mutableListOf<String?>())` — mockk type inference issue with nullables.

**Test counts:** 21 new backend (8 pagination, 13 plot); 6 new web (explore); 3 existing
compost tests updated for new response format. All 169 backend + 65 web tests pass.

**Docs:** VERSIONS.md (v0.23.0), ROADMAP.md (D2 marked done), PA_NOTES.md (two
architectural notes: EXIF in-process pattern, owner_user_id sentinel), PROMPT_LOG.md.

---

## Session — 2026-05-08 (Milestone 6 D1 — re-import utility)

**What was built.** A standalone Gradle subproject at `tools/reimport/` implementing
the M6 D1 re-import utility described in the D1 brief.

**`tools/reimport/` subproject structure:**
- `build.gradle.kts` / `settings.gradle.kts` — standalone Gradle project (Kotlin JVM 21,
  same GCS/Postgres/HikariCP versions as HeirloomsServer).
- `BucketReader.kt` — `BucketReader` interface + `GcsObject` data class. Thin abstraction
  over the GCS listing API; makes unit/integration tests independent of the real GCS client.
- `GcsBucketReader.kt` — real GCS implementation. Paginated listing via `storage.list()`.
  Supports service account JSON (`GCS_CREDENTIALS_JSON`) or ADC fallback.
- `Importer.kt` — core logic: import phase (scan, filter, dedup, insert) + verify phase
  (count parity + sample integrity). SHA-256 computed by downloading each object during
  import — correct for a small bucket; acceptable for recovery use.
- `Main.kt` — entry point; wires config → GcsBucketReader → HikariDataSource → Importer;
  prints final summary with warnings if parity or integrity checks fail.
- `ReimportConfig.kt` — config loaded from `DB_URL`, `DB_USER`, `DB_PASSWORD`, `GCS_BUCKET`,
  `GCS_CREDENTIALS_JSON` env vars.

**Schema note.** The actual DB column is `storage_key` (not `gcs_key` as the brief draft
called it). Dedup check and INSERT use `storage_key` throughout.

**Test count: 16 tests** (8 unit, 8 integration). Unit tests cover content-type filter,
sha256Hex, and ImportSummary counting via a fake BucketReader (no DB needed). Integration
tests use Testcontainers Postgres and a fake BucketReader; they cover: image/video import,
idempotency, non-media filtering, already-existing row skipping, metadata correctness
(mime_type, file_size, content_hash, storage_key), count parity verify, mismatch detection,
and sample integrity verify. The 8 unit tests pass; integration tests require Docker Desktop
to be restarted (known requirement, see PA_NOTES.md — `docker.raw.sock`). Run
`./gradlew test` from `tools/reimport/` after a Docker restart to confirm integration tests.

**Surprises / decisions during implementation:**
- Kotlin compiler required explicit `.invoke()` for nullable lambda property access
  (`obj?.downloadContent?.invoke()`). Minor; fixed immediately.
- GCS `Page.nextPage` typed cleanly as `Page<Blob>` — no unchecked cast needed with the
  nullable-while-loop pattern (`page = if (page.hasNextPage()) page.nextPage else null`).
- The verify's sample integrity re-uses `reader.listObjects()` for the download path,
  which re-scans the bucket for each sample. For 5 items against a small bucket this is
  fine; for scale, a direct `storage.get(BlobId.of(bucket, key))` lookup would be better.
  Not changed — acceptable for D1's scope.

**Docs updated:** `PA_NOTES.md` (recovery runbook), `VERSIONS.md` (v0.22.0), `ROADMAP.md`
(D1 marked done), `PROMPT_LOG.md` (this entry).

---

## Session: M6 D3 — Web complete (v0.24.0) — 8 May 2026

**Brief:** D3 of Milestone 6 — three sub-tasks bundled: Explore filters (3A), Garden
plots (3B), PhotoDetail variants (3C). See D3 brief in `docs/chats/` for full spec.

**Decisions resolved at session start:**
- Add a plot UX: inline form (not modal).
- Batch reorder: `PATCH /api/plots` bulk endpoint (not sequential PUTs).
- Sort cursors: sort-aware encoding (`SORT_NAME:epochMs_or_null:id`). Old-format cursors
  silently restart pagination (acceptable; D2 never exposed cursors to end users).

**What was built:**

*Schema:* V11 migration — `last_viewed_at TIMESTAMPTZ NULL` on `uploads`. Partial index
`idx_uploads_just_arrived` for the Just arrived predicate.

*Backend 3A:* `listUploadsPaginated` extended with `tags` (any-match), `fromDate`, `toDate`,
`inCapsule`, `includeComposted`, `hasLocation`, `sort`, `justArrived`. Sort-aware cursor
system replaces old single-sort cursor. `tryParseDate` helper handles ISO date strings with
inclusive boundaries (from = start of day, to = exclusive start of next day).

*Backend 3B:* `POST /api/content/uploads/:id/view` sets `last_viewed_at = NOW()`, idempotent.
`PATCH /api/plots` batch reorder is atomic (checks system-defined status before writing).
`Database.recordView` + `Database.batchReorderPlots` added.

*Web 3A — Explore:* Filter chrome added above the grid. `FilterChrome` component with tag
input, date range pickers, capsule/location segmented controls, composted checkbox, sort
dropdown. Collapses to a *Filters* toggle on narrow viewports. Re-fetches on any filter change.
Sort dropdown always visible. `ExploreGrid` replaces `PhotoGrid` to add composted-item
desaturation and *no date* tag on taken-date sorts.

*Web 3B — Garden:* Complete rewrite. Plots fetched from `GET /api/plots`. System plot renders
as `SystemPlotRow` (no DnD, no gear). User plots render as `SortablePlotRow` with `@dnd-kit/
sortable`. Gear menu: Edit, Delete, Move up, Move down. Drag-and-drop fires `PATCH /api/plots`.
Up/down arrows fire the same API. `PlotItemsRow` fetches its own items with cursor pagination.
`PlotForm` shared between Add and Edit. Delete uses `ConfirmDialog`. Compost count and
composted-message toast preserved.

*Web 3C — PhotoDetail:* `?from=garden|explore` query param. `GardenFlavour` component:
action-forward layout, *Compost* below a divider. `ExploreFlavour` component: larger hero,
metadata prominent (taken date, location, capsule count), kebab menu for actions, tags read-
only with *Edit tags* link. Back link context-aware. Every open fires `POST .../view`.

*IDIOMS.md:* Three new entries — *Plot*, *Just arrived*, *Negative-action button separation*.

**Test counts:**
- Backend integration: 21 new (18 × UploadFilterApiTest, 5 × PlotApiTest). Total now 190+.
- Web: 22 new (13 × garden.test.jsx, 5 × explore filter additions, 4 × photo_detail.test.jsx).
  3 existing compost tests updated (view mock added to fetch-ordering). Total: 86 web.

**Surprises / decisions during implementation:**
- `@dnd-kit/sortable` v10 installs cleanly with React 18. No JSdom incompatibilities in tests.
- `compost.test.jsx` mock ordering broke because `POST .../view` is now fired before the blob
  and capsules fetches. Fixed by inserting a view mock first in affected test cases.
- `GardenPage` compost count used two nested fetch calls; simplified to one `limit=200` call.
- `ExploreThumb` fetches thumbnails inside `ExploreGrid` rather than reusing `PhotoGrid`, to
  allow the composted-filter desaturation and no-date overlay without modifying the shared
  component.
- Cursor format `SORT_NAME:sortKeyMs_or_null:id` handles null `capturedAt` naturally: a `null`
  sortKeyMs indicates the cursor sits at the NULL-tail of a taken-date sort.

**Docs updated:** `IDIOMS.md` (3 entries + quick-reference update + status line), `VERSIONS.md`
(v0.24.0), `ROADMAP.md` (D3 marked done), `PA_NOTES.md` (view tracking note), `PROMPT_LOG.md`
(this entry).

---

## Session: D3 polish — testing, fixes, UX iteration (v0.24.1) — 8 May 2026

**Context:** Hands-on testing of the v0.24.0 D3 release. Bret tested the new Garden,
Explore, and PhotoDetail surfaces and reported a series of issues. This session worked
through each one, deploying continuously to production after each fix.

**Issues found and resolved:**

1. **Explore thumbnails blank in production.** `ExploreThumb` constructed URLs as
   `/api/content/uploads/…` (relative). In dev (same-origin) this works; in prod
   (api.heirlooms.digital vs heirlooms.digital) it fetches from the wrong host. Fixed
   by prepending `API_URL`.

2. **Video thumbnails in Garden had no play indicator.** `PlotThumbCard` showed the
   thumbnail image but no overlay. Added a play circle overlay for video items.

3. **Garden detail page missing rotate and tag actions.** `GardenFlavour` in
   `PhotoDetailPage` only had capsule + compost affordances. Added a rotate button
   (images only) and inline `InlineTagEditor`.

4. **Plot tag filtering returned all items regardless of criteria.** Root cause: the
   SQL `tags && ?::text[]` with JDBC `setArray` was unreliable (JDBC array param
   + `::text[]` cast). Fixed by switching to `ARRAY[?,?]::text[]` with individual
   `setString` params per tag — the same pattern used by the working `@>` filter. Also
   fixed the form: the `PlotTagPicker` discarded pending text (not yet confirmed with
   Enter) when the user clicked Create, so plots were created with empty `tag_criteria`.

5. **Tag modal caused visible page reload.** After tagging a Just arrived item, all
   plot rows reset to loading state. Fixed by optimistic exclusion (`justArrivedExclude`
   set) for Just arrived plus silent background re-fetch for user plots.

6. **Plot management UX redesigned.** The inline `PlotTagPicker/PlotForm` was brittle
   and confusing. New flow: "Add a plot" navigates to Explore; when a tag filter is
   active, a "Save as plot…" bar appears. Gear → Edit navigates to
   `/explore?edit_plot=<id>` with filter pre-loaded and an "Editing [name]" banner.

7. **Delete and Update plot silently did nothing.** `CorsFilter` listed only
   `GET, POST, PATCH, OPTIONS`. `DELETE` and `PUT` were blocked by CORS preflight.
   Fixed by adding both to the allowed methods.

8. **Rotate button on Garden thumbnails.** Rotate icon top-left on hover (images only).
   Handled inside `PlotItemsRow` (optimistic state update + background PATCH).

9. **Multi-tag filter in Explore.** Single text input replaced with `TagChromePicker`:
   chips for selected tags, dropdown populated from new `GET /api/content/uploads/tags`
   endpoint, keyboard (Enter/Backspace). "Save as plot" and "Update plot" send the full
   `tag_criteria` array. `PlotItemsRow` already used `plot.tag_criteria.join(',')` so
   Garden plot rows work with multi-tag criteria automatically.

**New backend endpoint:** `GET /api/content/uploads/tags` — returns all distinct tags
across non-composted uploads, sorted alphabetically. Uses `UNNEST(tags)` + `DISTINCT`.

**Architectural note — CORS:** The `CorsFilter` list of allowed methods should include
all HTTP verbs the web app uses. At the time of writing: `GET, POST, PUT, PATCH, DELETE,
OPTIONS`. Revisit if new verbs are introduced.

**Tests:** 88 web tests passing (up from 85 before this session). explore.test.jsx
switched from positional `mockResolvedValueOnce` chains to URL-routing
`mockImplementation` to handle the new tags fetch that fires alongside uploads fetch.

**Docs updated:** `VERSIONS.md` (v0.24.1), `PROMPT_LOG.md` (this entry),
memory/project_milestone_state.md updated.

---

## Session: Roadmap reshape — E2EE inserted as M7 (documentation pass) — 9 May 2026

**Brief:** Update project documentation to insert end-to-end encryption (Vault
E2EE) as the new Milestone 7, ahead of multi-user access. Renumber subsequent
milestones (multi-user → M8; new strong-sealing M9; delivery → M10; new
posthumous-delivery M11). Retire M3 (self-hosted deployment). Documentation-only
change; no code, no schema, no tests.

**Decision rationale:**

A claude.ai session on 9 May 2026 between Bret and the PA explored E2EE as
the next milestone instead of multi-user. The session worked through the
cryptographic design — envelope encryption with per-file DEKs wrapped under
a master key; device keypairs (Android Keystore, WebCrypto non-extractable)
for cross-device access; three independent unlock paths for sealed capsules
(recipient pubkey, drand-based time-lock, executor-held Shamir shares); three
recovery options (24-word phrase, passphrase-wrapped server backup, social
recovery via Shamir); crypto agility through versioned envelopes from day one.

The sequencing argument: doing E2EE before multi-user is meaningfully easier
than doing it after. Single-user has no key-sharing problem. Building
multi-user with plaintext, then re-encrypting once a friend tester has
uploaded their data, is the worse migration. Existing data is destroyable per
pre-launch posture, so M7 can start from a clean slate.

The harder cryptographic decisions (recipient pubkey wrapping, social recovery,
executor-mediated unlock, time-lock against drand) land in M9 and M11 rather
than being packed into a single bloated multi-user milestone. Each part of
the design earns its own milestone with the attention it needs.

M3 (self-hosted) retired because the GCP/Cloud Run path replaced its
operational shape, and M7's E2EE replaces its trust argument: "the operator
can't read your data" is a stronger promise than "you can run it yourself,"
delivered with less operational burden.

**What was changed:**

- ROADMAP.md — replaced M7 (Multi-user → Vault E2EE), shifted M8 (Delivery →
  Multi-user), added M9 (Strong sealing + social recovery), M10 (Milestone
  delivery, was M8), M11 (Posthumous delivery). Added "Retired milestones"
  section covering M3.
- IDEAS.md — renumbered milestone references throughout. Substantially
  reworked the "Trust posture and encryption" section (formerly framed as
  M12+, now framed as M7). Added new "Open questions for the M7
  implementation brief" section covering web posture, asymmetric scheme
  choice, and brand voice on recovery-failure copy.
- PA_NOTES.md — renumbered milestone references. Updated the "next
  milestone" pointer from M7 (multi-user) to M7 (Vault E2EE). Updated
  per-user view tracking and owner_user_id notes to reference M8 (the new
  multi-user milestone).
- BRAND.md — fixed pre-existing stale M6 references (delivery is now M10).
  Pre-dated this reshape; addressed in the same sweep for coherence.
- IDIOMS.md — fixed pre-existing stale M6 references (M10 now) and updated
  the connections-evolution reference from M7 to M9. Pre-dated this reshape;
  addressed in the same sweep.

**Surprises / decisions during implementation:**

None — the brief was complete and accurate. All edits landed as described.

**Docs updated:** ROADMAP.md, IDEAS.md, PA_NOTES.md, BRAND.md, IDIOMS.md,
PROMPT_LOG.md (this entry).

---

## Session: iOS Secure Enclave curve note for M7 asymmetric-scheme question — 9 May 2026

**Brief:** Small follow-up to the E2EE roadmap reshape session. iOS Secure
Enclave historically supports only P-256 for ECDH and signing, not X25519.
This is a material input to the M7 asymmetric-scheme decision that was
missing from the original "Open questions" write-up.

**What was changed:**

- IDEAS.md "Open questions for the M7 implementation brief" → "Asymmetric
  scheme for device keypairs" subsection extended to include P-256 as a
  third candidate alongside X25519 and RSA-OAEP. Hardware-enclave support
  across Android Keystore, WebCrypto, and iOS Secure Enclave is now
  explicit. Added a "regardless of choice" commitment that the M7 envelope
  format must be curve-agnostic, so iOS can later use a different scheme
  without an envelope migration.
- IDEAS.md "iOS strategy" → "Things to flag for the eventual iOS brief"
  gains a new bullet cross-referencing the asymmetric-scheme question.

**Decision rationale:**

The cleanest architectural response to iOS's enclave constraints is to bake
curve-agnosticism into the envelope format from M7 day one, rather than
trying to pick a single scheme that works everywhere in hardware. P-256 is
the only curve hardware-supported on all three platforms today, but committing
the protocol to one scheme limits future agility. Algorithm identifiers in
envelope metadata — already a M7 commitment for crypto agility — handle the
multi-scheme case naturally.

The M7 brief will still pick a default scheme (likely P-256 for conservatism,
or X25519 if Apple has shipped Secure Enclave support since this was last
verified). The point is that whichever it picks, iOS later can join the
protocol without rewriting it.

**Docs updated:** IDEAS.md, PROMPT_LOG.md (this entry).

**Not in scope:** the M7 implementation brief itself; resolution of the
three open questions still pending Bret's input.

---

## Session — 9 May 2026 (M7 E2: backend API for E2EE)

**Prompt:** "Let's implement M7 E2."

**What was built:** Full backend implementation of the E2EE API layer (v0.27.0),
ready for the E3 Android client. Backend-only; no client changes.

### New files

- `V15__m7_device_links.sql` — `pending_device_links` table and index for the
  trusted-device key-wrap state machine.
- `KeysHandler.kt` — `/api/keys/` contract: device CRUD (register, list, retire,
  touch), passphrase CRUD, and a 4-step device-link flow (initiate → register
  → status poll → wrap). Each step is a separate endpoint; the server is a dumb
  relay throughout the wrap flow.
- `PendingBlobsCleanupService.kt` — coroutine-based background service running two
  periodic jobs: pending-blob TTL cleanup (24 h TTL, 6 h interval) and dormant
  device pruning (180 d last_used_at, 24 h interval). Both jobs run at startup.
- `KeysHandlerTest.kt` — 13 unit tests covering device CRUD and passphrase CRUD.
- `E2EncryptedUploadTest.kt` — 9 integration tests including the BouncyCastle
  round-trip canary (test 26), migration canary (test 29), and device-link happy
  path (test 35).

### Modified files

- `Database.kt` — `UploadRecord` + 9 E2EE fields; 3 new record types; all SELECT
  queries updated; new DB operations for pending_blobs, wrapped_keys, passphrase,
  device-links, and `migrateUploadToEncrypted`; `toUploadRecord()` and `toJson()`
  extended.
- `UploadHandler.kt` — `POST /uploads/initiate`, extended `POST /uploads/confirm`
  (encrypted path with envelope validation + dedup bypass), `POST /uploads/{id}/migrate`,
  encrypted thumbnail serving on `/thumb`, encrypted thumbnail compost cleanup.
  OpenAPI spec merges now include the keys contract.
- `S3FileStore.kt` — added `DirectUploadSupport` implementation using `S3Presigner`
  with path-style addressing. Content-type is NOT included in the signed headers
  (avoids 403 when client PUTs with a different type).
- `Main.kt` — `PendingBlobsCleanupService` started at boot.
- `run-tests.sh` — fixed `./gradlew jar` → `./gradlew shadowJar`.
- `HeirloomsTest/build.gradle.kts` — added BouncyCastle 1.79 and AWS SDK S3 2.25.11.
- `docker-compose.yml` (test) — MinIO port 9000 exposed for integration test signed-URL flows.
- `HeirloomTestEnvironment.kt` — `minioBaseUrl` and `minioS3Client` added; test helper
  `putToMinio()` for direct credential-based PUT (presigned URL host validation fails
  across Docker network boundaries).
- `UploadHandlerTest.kt` — 12 new unit tests (tests 14–25).
- `S3FileStoreTest.kt` — updated to inject mock `S3Presigner`.

### Key decisions made during implementation

**Dedup bypass for encrypted confirms:** `findByContentHash` not called when
`storage_class = "encrypted"` — ciphertext hashes are non-deterministic (different DEK
+ nonce each time), so the guard provides no value and would incorrectly block re-uploads.

**S3 presigned URL + MinIO integration test gap:** MinIO validates the `host` in the
presigned URL signature, but the test client rewrites `minio:9000` to `localhost:{port}`.
The mismatch produces a 403. Fixed by having the test PUT directly to MinIO using the
AWS SDK S3 client with credentials, bypassing the presigned URL signature check. The
server logic (initiate / confirm / migrate) is still fully exercised.

**`run-tests.sh` stale JAR bug:** Script called `./gradlew jar` (thin JAR) but the
Dockerfile copies `*-all.jar` (shadow/fat JAR). Image was running the previous version's
fat JAR. Fixed by changing to `./gradlew shadowJar`.

**http4k two-level lambda `return` limitation:** In double-lambda contract handlers
(`bindContract POST to { param -> { req -> ... } }`), early returns from the inner
lambda require extracting to a named function. See `KeysHandler.kt`.

### Infrastructure gaps surfaced and fixed

1. `S3FileStore` didn't implement `DirectUploadSupport` — `/initiate` returned 501.
2. `S3Presigner` missing `pathStyleAccessEnabled(true)` — presigned URLs used
   virtual-hosted format (`{bucket}.host`) which fails DNS on MinIO.
3. Content-type in presigned headers caused 403 — removed from the `PutObjectRequest`
   used to generate the signature.
4. `run-tests.sh` called `./gradlew jar` not `shadowJar` — Docker image used stale code.

### Test results

- 218 unit tests pass (HeirloomsServer); 0 failures.
- 9/9 E2EE integration tests pass; 8 pre-existing failures remain (JSONArray tests
  written before paginated list was introduced — not E2 regressions).

### Deployment

Deployed to Cloud Run as revision `heirlooms-server-00033-dqp`. Health confirmed
(`/health` → `ok`). V15 migration applied automatically at startup (Flyway).

**Next:** E3 — Android client encryption + migration flow.

---

## Session — 9 May 2026 (M7 E3: Android client encryption)

**Prompt:** "Let's start the next phase."

**What was built:** Full Android client implementation of vault E2EE (v0.28.0). Android-only;
no server or web changes.

### New files

- `crypto/VaultCrypto.kt` — pure-Kotlin crypto utilities: AES-256-GCM, HKDF-SHA256 (manual
  RFC 5869 implementation), Argon2id (BouncyCastle), symmetric + asymmetric envelope
  builder/parser matching the server's `EnvelopeFormat`. No Android dependencies; fully testable
  on JVM.
- `crypto/DeviceKeyManager.kt` — Android Keystore AES-256 key for local master key wrapping;
  software P-256 device keypair (private wrapped by Keystore key, public plaintext) for server
  registration. `setupVault()` is one-time; `loadMasterKey()` auto-unlocks on process restart.
  `wrapMasterKeyForServer()` produces a `p256-ecdh-hkdf-aes256gcm-v1` asymmetric envelope.
- `crypto/VaultSession.kt` — top-level singleton holding the in-memory master key and a
  thumbnail cache (`ConcurrentHashMap<String, ImageBitmap>`).
- `ui/main/VaultSetupViewModel.kt` — orchestrates first-launch setup: generate master key,
  register device with `/api/keys/devices`, upload passphrase backup to `/api/keys/passphrase`,
  unlock `VaultSession`. `Factory` pattern for `apiKey` injection.
- `ui/main/VaultSetupScreen.kt` — three-state UI: generating keys → passphrase entry (2×
  `OutlinedTextField`, match-check, show/hide toggle, "Save passphrase" CTA) → saving.
- `test/.../crypto/VaultCryptoTest.kt` — 14 JVM unit tests covering all crypto round-trips,
  wrong-key failure cases, HKDF determinism, Argon2id round-trip, asymmetric ECDH end-to-end.

### Modified files

- `build.gradle.kts` — BouncyCastle `bcprov-jdk18on:1.79` added (production + test); packaging
  options exclude BC META-INF signature files; versionCode 32, versionName "0.28.0".
- `api/Models.kt` — `Upload` gains `storageClass` (default "legacy_plaintext"), `envelopeVersion`,
  `wrappedDek`, `dekFormat`, `wrappedThumbnailDek`, `thumbnailDekFormat` (all `ByteArray?`);
  `isEncrypted` computed property.
- `api/HeirloomsApi.kt` — `put()` method added; `toUpload()` decodes E2EE fields from base64;
  new methods: `registerDevice`, `putPassphrase`, `initiateEncryptedUpload`, `confirmEncryptedUpload`,
  `fetchBytes`. `jsonEsc()` helper for label quoting.
- `app/Uploader.kt` — `uploadEncryptedViaSigned()`: generates content + thumbnail DEKs, encrypts
  file (AES-256-GCM symmetric envelope), generates thumbnail (`BitmapFactory` for images,
  `ThumbnailUtils` for video, max 400px JPEG), encrypts thumbnail, wraps DEKs under master key,
  calls `/initiate` + two presigned PUTs + `/confirm` with full E2EE fields. Fallback: 1×1
  white JPEG if thumbnail generation fails.
- `app/UploadWorker.kt` — auto-unlocks vault via `DeviceKeyManager.loadMasterKey()` on worker
  start; calls `uploadEncryptedViaSigned()` when vault is set up, `uploadViaSigned()` otherwise.
- `ui/main/MainApp.kt` — vault setup gate inserted (shows `VaultSetupScreen` when
  `!deviceKeyManager.isVaultSetUp()`); auto-unlocks `VaultSession` on entry to main nav.
- `ui/common/HeirloomsImage.kt` — new `UploadThumbnail(upload)` composable: for encrypted uploads,
  fetches encrypted bytes, decrypts with thumbnail DEK, decodes to `ImageBitmap`, caches in
  `VaultSession.thumbnailCache`; plaintext uploads delegate to `HeirloomsImage(url)`. `rotated()`
  helper extracted to avoid duplication.
- `ui/garden/PhotoDetailViewModel.kt` — `load()` accepts `context` parameter; if upload is
  encrypted, calls `loadEncryptedContent()` which fetches+decrypts the full file: images →
  `ImageBitmap` via `_decryptedBitmap`; videos → decrypted to `context.cacheDir/vault_temp/`
  then exposed as `Uri` via `_decryptedVideoUri`.
- `ui/garden/PhotoDetailScreen.kt` — `MediaArea` updated with `decryptedBitmap`/`decryptedVideoUri`
  params; renders decrypted content for encrypted uploads, shows progress spinner while loading;
  `GardenFlavour` and `ExploreFlavour` accept and thread these params down to `MediaArea`.

### Design decisions made

**Existing `legacy_plaintext` items become "public".** ROADMAP said "clean slate" but Bret
clarified existing items should remain accessible as an unencrypted storage class. No migration
run; client treats `storageClass != "encrypted"` as plaintext. No data loss.

**Keystore AES-256 for local key wrapping (not Keystore ECDH).** `PURPOSE_AGREE_KEY` requires
API 31; app minSdk is 26. Using Keystore `PURPOSE_ENCRYPT | PURPOSE_DECRYPT` (AES-256-GCM)
to wrap the master key at rest — works on all supported API levels. P-256 keypair generated in
software (private key wrapped by Keystore AES key, public stored plaintext). ECDH for the
server's wrapped master key uses software ECDH (`ephemeral_private × device_static_public`),
not Keystore ECDH.

**Encrypted video streaming deferred.** Large videos are fully decrypted to a temp file before
ExoPlayer plays them. Noted as a known limitation; streaming decryption is a future increment.

**No migration UI in E3.** `/migrate` endpoint exists on server but is unused. Bret will
re-upload existing content via the new encrypted path.

### Test results

- 14/14 `VaultCryptoTest` pass (new).
- 78/78 existing Android unit tests pass (no regressions).
- Total: 92 Android unit tests, 0 failures.

**Next:** E4 — Web client vault (passphrase-based unlock, encrypted upload/download in browser).

---

## Session — 9 May 2026 (post-E3 device testing fixes — v0.28.1)

Three bugs found during hands-on testing of v0.28.0 on Samsung Galaxy A02s.

**1. Encrypted thumbnails not displaying in Garden.**
All five thumbnail call sites (Garden, Explore, Compost heap, Photo picker, Capsule detail)
were still calling `HeirloomsImage(url = api.thumbUrl(upload.id))` directly. For encrypted
uploads this fetches raw ciphertext that Coil cannot decode — images showed blank.
Fix: updated all five to `UploadThumbnail(upload)`, which dispatches to `EncryptedThumbnail`
for encrypted rows (fetches, decrypts, decodes) and to `HeirloomsImage` for plaintext rows.
Upload was confirmed encrypted server-side before diagnosing the display path.

**2. Rotate button not updating the image immediately.**
Pressing rotate saved the rotation to the server (visible after restart) but the image
didn't visually update. Root cause: `_stagedRotation` was a private `MutableStateFlow`
not directly observed by the screen. The screen only collected `isDirty` (a boolean);
the first rotate press changes `isDirty` from false → true and does trigger a recomposition,
but any subsequent press leaves `isDirty = true → true` with no recomposition. Fix: expose
`stagedRotation: StateFlow<Int?>` as a public property on the ViewModel; collect it directly
in `PhotoDetailScreen`; replace `vm.effectiveRotation()` (a plain function call) with
`stagedRotation ?: u.rotation` (a reactive expression).

**3. Just arrived drops items on view.**
Opening any photo from Just arrived silently removed it from the row — not on save, but
immediately on open. Root cause: `trackView()` fires on every `PhotoDetailScreen` open,
setting `last_viewed_at = NOW()`. The `just_arrived=true` server predicate included
`last_viewed_at IS NULL`, so any viewed item stopped qualifying.
Fix: remove `last_viewed_at IS NULL` from the `just_arrived` predicate. Items now leave
Just arrived only when tagged or composted (i.e., actually acted on). Also removed the
`AND last_viewed_at IS NULL` guard from the `trackView` UPDATE so subsequent views keep
`last_viewed_at` current. Two integration tests updated to match the new contract:
`just_arrived=true keeps item after it has been viewed` (was: excludes after view).
Deployed: Cloud Run revision `heirlooms-server-00034-frz`.

---

## Session — 9 May 2026 (M7 E4 — web client encryption — v0.29.0)

### What was built

M7 E4: web vault. After this session, photos and videos planted from the browser are
encrypted on-device before reaching the server. Android-encrypted uploads (E3) decrypt
and display correctly in the web client. `legacy_plaintext` uploads are unchanged.

### Brief

`docs/briefs/M7_E4_brief.md` written before implementation. Key design decisions recorded there:
passphrase-as-unlock-mechanism (no hardware keystore on web), one-device-per-browser via
IndexedDB-persisted P-256 keypair, WebCrypto for all symmetric + asymmetric ops, Argon2id
via `@noble/hashes` (only new runtime dependency), EXIF deferred to E5.

The brief was updated mid-session to fix a design error in the first draft: device
registration was originally described as "best-effort per session" (would accumulate a new
`wrapped_keys` row each login). Corrected to: generate keypair once, store in IndexedDB,
register once, `localStorage` flag guards re-registration on subsequent sessions.

### New files

- `src/crypto/vaultCrypto.js` — pure WebCrypto + `@noble/hashes` crypto layer. Matches
  the server's `EnvelopeFormat` and Android's `VaultCrypto.kt` byte-for-byte. Functions:
  `generateMasterKey/Dek/Nonce/Salt`, `aesGcmEncrypt/Decrypt`, `buildSymmetricEnvelope`,
  `parseSymmetricEnvelope`, `encryptSymmetric`, `decryptSymmetric`, `wrapDekUnderMasterKey`,
  `unwrapDekWithMasterKey`, `hkdf`, `wrapMasterKeyWithPassphrase`,
  `unwrapMasterKeyWithPassphrase`, `buildAsymmetricEnvelope`, `parseAsymmetricEnvelope`,
  `wrapMasterKeyForDevice`, `toB64`, `fromB64`. All async (WebCrypto is Promise-based);
  Argon2id is synchronous — a `setTimeout(resolve, 0)` yield is added before the KDF call
  so the "Unlocking…" spinner renders before the main thread freezes.
- `src/crypto/vaultSession.js` — module-level master key singleton (mirrors Android's
  `VaultSession`). `unlock/lock/isUnlocked/getMasterKey`. LRU thumbnail object-URL cache
  (max 300 entries; evicted URLs are revoked to prevent memory leaks).
- `src/crypto/deviceKeyManager.js` — IndexedDB-backed P-256 keypair persistence (mirrors
  Android's `DeviceKeyManager`). `generateAndStoreKeypair()` creates the keypair, stores
  both `CryptoKey` objects in IDB (`extractable: false`), writes a UUID deviceId to
  `localStorage`. `isVaultSetUp()` / `markVaultSetUp()` guard registration to once per
  browser. `loadPrivateKey()` / `loadPublicKeySpki()` restore across sessions.
- `src/pages/VaultUnlockPage.jsx` — three-case screen:
  - **Case A** (returning browser, vault set up): passphrase → decrypt master key only.
  - **Case B** (new browser, existing account): passphrase → decrypt → generate keypair →
    register device with server.
  - **Case C** (brand-new account): generate master key → wrap with passphrase →
    `PUT /api/keys/passphrase` → generate keypair → register device.
  Probe on mount (`GET /api/keys/passphrase`) determines case. Cases A + B share the
  `'unlock'` UI state; Case C uses `'setup'` (two passphrase fields + match validation).
- `src/components/UploadThumb.jsx` — dual-path thumbnail composable (mirrors Android's
  `UploadThumbnail`). `storageClass === 'encrypted'`: fetch ciphertext → decrypt with
  thumbnail DEK → `URL.createObjectURL` → `<img>`. Plaintext: existing `getThumb` via
  `thumbCache.js`. Decrypted object URLs cached in `vaultSession.thumbnailCache` with LRU
  eviction and `URL.revokeObjectURL` on evict.
- `src/test/vaultCrypto.test.js` — 14 vitest unit tests mirroring Android's
  `VaultCryptoTest`. All Uint8Array comparisons use `Array.from()` to work around vitest's
  cross-buffer-origin equality check. Argon2id tests use `{ m: 64, t: 1, p: 1 }` to keep
  the suite fast.

### Modified files

- `package.json` — `@noble/hashes: ^1.8.0` added (resolved to 1.8.0 by npm).
- `src/App.jsx` — `vaultUnlocked` state + `onVaultUnlocked` callback added to
  `AuthContext.Provider`. `RequireAuth` checks `vaultUnlocked`; renders `VaultUnlockPage`
  when false. `handleSignOut` calls `vaultSession.lock()` before clearing state.
- `src/api.js` — new functions: `putPassphrase`, `registerDevice`,
  `initiateEncryptedUpload`, `putBlob` (raw PUT to signed URL, no auth header),
  `confirmEncryptedUpload`, `fetchBytes`.
- `src/pages/GardenPage.jsx` — `PlotThumbCard` switches from manual `getThumb` useEffect
  to `UploadThumb`; encrypted uploads navigate to detail page instead of opening quick
  modals. `Plant` button (top-right) triggers hidden file input; `encryptAndUpload()`
  encrypts content + thumbnail on-device and calls initiate/PUT/confirm. `generateThumbnail()`
  uses `createImageBitmap` + `<canvas>` for images, `<video>` seek + canvas for video;
  1×1 white JPEG fallback if generation fails. Upload status shown inline ("Encrypting…" →
  "Uploading…" → "Done").
- `src/pages/PhotoDetailPage.jsx` — content-loading `useEffect` extended: if
  `upload.storageClass === 'encrypted'`, fetches `/file`, decrypts with content DEK,
  creates object URL. Encrypted video plays from blob URL directly in `<video>` (no temp
  file needed on web). Plaintext path unchanged.
- `src/pages/ExplorePage.jsx` — `ExploreThumb` rewritten to delegate to `UploadThumb`;
  manual `getThumb` useEffect removed.
- `src/pages/CompostHeapPage.jsx` — `ThumbImage` component replaced with `UploadThumb`.
- `src/components/PhotoGrid.jsx` — `Thumb` component rewritten to use `UploadThumb`;
  `apiKey` prop removed from `PhotoGrid` (now sourced internally via `useAuth`).

### Design decisions made

**One device per browser (not one per session).** The first E4 brief draft said "generate
a fresh keypair each login session, register as a new device each time." Bret caught this
during review. Fixed: keypair generated once, stored in IndexedDB as non-extractable
`CryptoKey` objects. `localStorage` flag (`heirlooms-vaultSetUp`) gates registration to
once per browser. Browser data wipe = new device registration (equivalent to Android factory
reset).

**Passphrase is the sole web unlock mechanism.** No hardware keystore on the browser. The
master key lives in memory only. Every page refresh re-prompts for the passphrase. Consistent
with the existing pattern of clearing the API key on refresh — the user already expects to
re-authenticate each session.

**Argon2id blocks the main thread; yield before it.** `@noble/hashes/argon2` runs
synchronously. Without a `setTimeout(resolve, 0)` yield before the KDF call, React's
batched `setState('working')` never flushes to the DOM before the ~3s freeze, leaving the
user staring at an unresponsive button. The yield ensures the "Unlocking…" spinner renders
first. Observed and fixed during post-deploy testing.

**EXIF deferred to E5.** Encrypted metadata blob sent as all-null JSON. `takenAt` derived
from `file.lastModified`. Full EXIF extraction (requires a third-party library) is E5 scope.

### Test results

- 14/14 new `vaultCrypto.test.js` tests pass.
- 88/88 existing web tests pass (no regressions). Total: 102.

### Deployment

- Built with `--platform linux/amd64 --build-arg VITE_API_URL=https://api.heirlooms.digital`.
- Deployed to Cloud Run revision `heirlooms-web-00036-9lm`.
- Tested end-to-end: entered Android vault passphrase on web → vault unlocked → Garden
  loaded. First page load showed blank (Cloud Run cold start); second load worked correctly.

**Next:** E5 — onboarding, recovery, polish, legacy retirement.

---

## Session — 9–10 May 2026 (M7 E5 — final wrap — v0.30.0)

### What was built

M7 E5: four items closed out the milestone — storage class rename, web EXIF extraction,
onboarding copy polish, and one deployment bug fixed (GCS CORS). M7 is now complete.

### Brief

`docs/briefs/M7_E5_brief.md` written before implementation. Key scoping decisions:
recovery phrase deferred to M9; `legacy_plaintext` renamed to `public` (mechanism stays,
name reflects future use for public plots); web EXIF via `exifr`; no new API endpoints.

### Changes

**Storage class rename (`legacy_plaintext` → `public`):**
- V16 Flyway migration: `UPDATE uploads SET storage_class = 'public' WHERE storage_class =
  'legacy_plaintext'` — also covers `capsule_messages`. Renumbered from the initially
  mis-numbered V10 (V10 was already taken by the plots migration; V15 by device links).
- Server `Database.kt`: default, `exif_processed_at` guard, SQL WHERE clause, and ResultSet
  fallback updated.
- Server `UploadHandler.kt`: `"public"` and `"legacy_plaintext"` "not yet supported" blocks
  removed from initiate and confirm handlers; migrate endpoint guard updated.
- Android `Models.kt`: default `"public"`. `HeirloomsApi.kt`: JSON parser fallback updated.
- Web `UploadThumb.jsx`: comment updated (only occurrence in web source).
- Tests: `SchemaMigrationTest` (test name + assertions), `UploadHandlerTest` (two assertions),
  `E2EncryptedUploadTest` (two assertions).

**Web EXIF extraction:**
- `exifr ^7.1.3` added to `package.json` (pure JS, no WASM).
- `buildEncryptedMetadata(file)` added to `GardenPage.jsx`: reads GPS, camera make/model,
  lens model, focal length, ISO, shutter, aperture from JPEG EXIF via `exifr`. ExposureTime
  split into numerator/denominator (`et >= 1` → long-exposure branch, otherwise `1/round(1/et)`).
  Videos stay all-null. Wrapped in try/catch — EXIF failure silently produces all-null blob.
- `confirmEncryptedUpload` in `api.js` updated to accept and pass `encryptedMetadataB64` +
  `encryptedMetadataFormat`. Server already stored the blob; client was the gap.

**Onboarding copy:**
- Android `VaultSetupScreen`: headline "Heirlooms" → "Your vault"; sub-copy → "Your
  passphrase unlocks your vault on any device. Keep it somewhere safe."; button "Save
  passphrase" → "Protect vault".
- Web `VaultUnlockPage` Case C sub-label: "Your passphrase protects your vault if you ever
  lose access to this device." → "Your passphrase unlocks your vault from any browser. Keep
  it somewhere safe." Cases A + B sub-label unchanged ("Your passphrase protects your vault.").

### Deployment issues found and fixed

**GCS CORS missing.** The Plant button's `putBlob` call is a direct browser PUT to a GCS
signed URL. GCS requires explicit CORS configuration to allow cross-origin PUTs from a browser.
The bucket had none — because direct browser upload had never been tested end-to-end before E5.
Fixed with `gsutil cors set` (method: PUT, origins: heirlooms.digital +
heirlooms-web Cloud Run URL).

**Wrong web Docker image.** When running server and web Docker builds in parallel (from the
same terminal session), the web image was accidentally built from `HeirloomsServer/` instead of
`HeirloomsWeb/` because the working directory had been set by the preceding `cd`. Fixed by
always using an explicit `cd` before `docker build`. The bad image produced a "container failed
to start on port 80" Cloud Run error immediately on deploy — caught before any traffic routed.

**Upload error never cleared.** After a failed upload attempt (the CORS error), the error
message persisted indefinitely — there was no dismiss button and the next file picker open
called `setUploadError(null)` but only at the start of the next upload. Fixed: auto-clear
via `setTimeout(..., 4000)` in the catch block.

### Test results

- 102/102 web tests pass.
- `npm run build` clean (exifr bundled without issue, 428 kB JS bundle).

### Deployment

- Server `heirlooms-server-00035-qwc` (V16 migration ran on first startup).
- Web `heirlooms-web-00039-g86` (error auto-clear fix; CORS applied at bucket level, not
  per-revision).
- End-to-end confirmed: planted a JPEG from the web; Garden showed decrypted thumbnail.

**M7 complete. Next: M8 — multi-user.**

---

## Session — 10 May 2026 (Android: Garden Plant button — v0.31.0)

### What was built

A Plant FAB in the Android Garden that lets the user capture and upload directly in-app —
no camera app, no share sheet required. Three source options: Photo, Video, File (any type).

### Brief

`docs/briefs/plant_button_android.md` written before implementation. Key design decisions:
in-screen state machine (no new Nav destination — avoids URI serialisation across Nav and
simplifies retake flow); system camera intents (`TakePicture` / `CaptureVideo`) rather than
CameraX (no CAMERA permission needed); ExoPlayer reused from `PhotoDetailScreen` for video
preview; `copyContentUriToCache` for file-picker URIs mirrors `ShareActivity.copyToTempFile`.

### New files

- `PlantSheet.kt` — `ModalBottomSheet` with Photo / Video / File options. Also defines
  `PlantType` enum and `PlantState` sealed class (`Idle`, `Preview(uri, mimeType, isFile)`,
  `Queuing`) used by `GardenScreen`.
- `PlantPreviewOverlay.kt` — full-screen composable. Photo: Coil `AsyncImage` with
  `ContentScale.Fit`. Video: ExoPlayer wrapped in `AndroidView` (no auth interceptor needed
  for local URIs). File: display name resolved from `OpenableColumns.DISPLAY_NAME` via
  `LaunchedEffect` + `Dispatchers.IO`; drive-file icon shown. Bottom bar: "Retake" (photo
  and video only) + "Plant".
- `res/xml/file_provider_paths.xml` — `<cache-path name="captures" path="." />` covering
  the `cacheDir` temp files written before launching the system camera.

### Modified files

- `GardenScreen.kt` — plant state vars, three `rememberLauncherForActivityResult` launchers
  (OpenDocument, TakePicture, CaptureVideo), `launchCamera` local function (creates temp
  file, gets FileProvider URI, stores reference for zero-copy upload), `BackHandler`,
  FAB, `PlantSheet` conditional, `AnimatedVisibility` overlay switching between `Preview`
  and `Queuing` states, `copyContentUriToCache` private suspend function at file bottom.
  WorkManager enqueue mirrors `ShareActivity` (BackoffPolicy, UploadWorker.TAG, session tag).
- `AndroidManifest.xml` — `FileProvider` declaration with `${applicationId}.fileprovider`
  authority.
- `app/build.gradle.kts` — versionCode 33, versionName "0.31.0".

### Design decisions made

**System camera, no CAMERA permission.** `TakePicture` / `CaptureVideo` launch the system
camera app, which holds its own camera permission. Our app does not need to declare or
request `CAMERA` — the system camera handles all of it transparently.

**Zero-copy for camera captures.** The file is created before launching the camera (so we
have the path). `TakePicture` writes directly into it. `onPlant` passes the absolute path
straight to `UploadWorker` — no second copy. File-picker URIs (content://) do require a
copy since `UploadWorker` needs a stable file path and content URI access expires with the
Activity.

**State machine in GardenScreen, not GardenViewModel.** Plant state is ephemeral UI — it
doesn't survive config changes and doesn't need to (the camera intent will also re-trigger
on rotation). Keeping it in the composable avoids adding VM complexity for a transient flow.

### Test results

- `./gradlew :app:compileDebugKotlin` — BUILD SUCCESSFUL, zero warnings.
- Installed on Samsung Galaxy A02s (SM-A025F, Android 12) via USB. Photo capture,
  preview, Plant, and Just Arrived appearance all confirmed working.

---

## Session — 10 May 2026 (Diagnostics screen + FreeTime detection — v0.32.0)

### What was built

A Diagnostics screen reachable from the burger menu that captures in-session error events,
lets the user expand detail, and reports them to the server with one tap. Motivated by an
ongoing Fire OS file-picker bug where files were silently unreadable.

### Root cause identified via diagnostics

First report from Amazon KFRAPWI revealed:

```
DATA path=/data/securedStorageLocation/com.android.camera2/FreeTime/Sadie/IMG_20260510_084641_3.heic
tryStream(content://media/external/images/media/307) threw FileNotFoundException: Failed opening content provider
```

Files in Amazon FreeTime (Kids+) child profiles live under `/data/securedStorageLocation/`
— Amazon's encrypted store, inaccessible to third-party apps via any mechanism. Detection
added: if the DATA path contains `securedStorageLocation`, show a friendly message and skip
diagnostic logging. Files from the main account work correctly (confirmed: camera-captured
photo on the main profile uploaded successfully).

### New files

- `DiagnosticsStore.kt` — in-process `mutableStateListOf<DiagEvent>` singleton (max 300 events).
- `DiagnosticsScreen.kt` — collapsible event rows; Report button cycles
  `Idle → Sending → Sent ✓ / Failed — tap to retry`.
- `V17__diagnostic_events.sql` — `diagnostic_events` table.

### Modified files

- `UploadHandler.kt` — `diagContract` with POST + GET routes bound to `/api`.
- `Database.kt` — `insertDiagEvent()`, `listDiagEvents()`.
- `HeirloomsApi.kt` — `postDiagEvent()`; `jsonEsc()` fixed to escape `\n`/`\r`/`\t`
  (previously produced invalid JSON for multi-line detail, silently failing the POST).
- `AppNavigation.kt` — `Routes.DIAGNOSTICS`, `apiKey` threaded into `AppNavHost`,
  composable route, `onDiagnosticsTap` wired.
- `BurgerPanel.kt` — `onDiagnosticsTap` parameter + Diagnostics row.
- `GardenScreen.kt` — `copyContentUriToCache` logs to `DiagnosticsStore`; returns
  `Pair<String?, String>` with friendly messages; FreeTime early-exit added.

### Deployment

Server `heirlooms-server-00036-9hw` (V17 migration). APK v0.32.0 installed on KFRAPWI.

### Next

Streaming encryption for large files (88 MB video). Brief at
`docs/briefs/streaming_encryption.md`.

## Session — 10 May 2026 (Streaming encryption + web video playback — v0.33.0–v0.35.0)

**v0.33.0 — Streaming encryption for large files**

Brief at `docs/briefs/streaming_encryption.md`. Server endpoint `POST /api/content/uploads/resumable` added. Android and web `encryptAndUpload` restructured: files > 10 MB use GCS resumable upload, encrypting and PUTting in 4 MiB plaintext chunks (`[nonce(12)][ciphertext+tag(n+16)]`). `aesGcmEncryptWithAad` added for chunk-level AAD binding. `decryptStreamingContent` added to both platforms.

**v0.34.0 — Web streaming-format decrypt fix**

`decryptStreamingContent` in `vaultCrypto.js` was not aligning reads to correct chunk boundaries, causing GCM auth failures on large encrypted files. Fixed.

**v0.35.0 — Web encrypted video playback + MSE streaming**

`openEncryptedVideoStream()` in `encryptedVideoStream.js`. Downloads chunk 0, probes with mp4box to detect faststart. Faststart → MSE streaming (raw decrypted bytes appended to SourceBuffer). Non-faststart → full download then blob URL. `PhotoDetailPage` routes large encrypted videos through this path.

**Deployment:** Server `heirlooms-server-00035-qwc`. Web `heirlooms-web-00039-g86`.

---

## Session — 11 May 2026 (Preview clips, chunk size, parallel prefetch, download — v0.36.0)

**Brief:** Improve encrypted video performance. Replace MSE streaming complexity with a short preview clip approach: client generates the first N seconds of any large encrypted video at upload time; the detail page plays the clip inline. Download button provides access to the full file. Chunk size drops from 4 MiB to 1 MiB for new encrypted uploads; stored per-upload for backward compat. MSE streaming gains 4-wide parallel prefetch.

**Server changes:**
- V18 migration: `preview_storage_key`, `wrapped_preview_dek`, `preview_dek_format`, `plain_chunk_size` columns on `uploads`.
- `GET /api/settings` → `{"previewDurationSeconds":N}` (env `PREVIEW_DURATION_SECONDS`, default 15).
- `GET /api/content/uploads/{id}/preview` → proxies encrypted preview clip bytes.
- `confirmEncryptedUpload` accepts and stores all new fields.

**Web changes:**
- `@ffmpeg/ffmpeg`, `@ffmpeg/util`, `@ffmpeg/core` added. WASM copied to `public/` via postinstall (`scripts/copy-ffmpeg.mjs`). Dockerfile uses `--ignore-scripts` then explicit copy.
- `fetchSettings(apiKey)` fetches and caches `previewDurationSeconds`.
- Large video uploads: initiate a second GCS slot for preview, run ffmpeg.wasm to trim first N seconds, encrypt with separate DEK, upload. `plainChunkSize` and preview fields sent in confirm.
- `encryptedVideoStream.js` rewritten: chunk size read from `upload.plainChunkSize` (default old 4 MiB), 4-wide parallel prefetch in both MSE and full-download paths.
- `PhotoDetailPage`: encrypted videos with `previewStorageKey` play the preview clip; download button for full file.

**Android changes:**
- `CHUNK_SIZE` → 1 MiB. `generatePreviewClip()` via `MediaExtractor` + `MediaMuxer`. Preview clip encrypted and uploaded; fields sent in confirm. `DecryptingDataSource` chunk size from upload record. `downloadFullFile()` saves to Downloads via `MediaStore`.

**Deployment:** Server `heirlooms-server-00039-62p`. Web `heirlooms-web-00052-n5k`. APK installed.

---

## Session — 11 May 2026 (Duration-based playback threshold + preview UX — v0.37.0)

**Brief:** `docs/briefs/preview_clip_playback.md`. Store video duration per upload; use it to decide whether to play the full video or preview clip. Per-device threshold on Android; hardcoded default on web. Show preview label while clip plays; full translucent overlay on end.

**Server changes:**
- V19 migration: `duration_seconds INTEGER` on `uploads`.
- Plaintext video confirms extract duration via `ffprobe` (`extractVideoDuration()` in `ThumbnailGenerator.kt`, same ProcessBuilder pattern as thumbnail generation).
- Encrypted confirms accept `durationSeconds` from client, store it.

**Web changes:**
- `FULL_PLAYBACK_THRESHOLD_SECONDS = 120` constant in `PhotoDetailPage.jsx`.
- `getVideoDurationSeconds(file)` reads `<video>.duration` at upload time; sent in confirm.
- Playback routing: known duration → compare to threshold; unknown duration → fall back to 10 MB file-size check.
- `PreviewVideoPlayer` component: "Preview · 0:08 of 5:32" label via `timeupdate`; full translucent overlay on `ended` with Download button; overlay dismissible by clicking outside button.

**Android changes:**
- `extractFileDurationSeconds(file)` via `MediaExtractor.getTrackFormat(KEY_DURATION)`; sent in confirm.
- `EndpointStore.getVideoPlaybackThreshold()` / `setVideoPlaybackThreshold()` backed by `SharedPreferences`.
- Settings screen: "Play full video up to" segmented picker — 1 min / 5 min / 15 min / No limit (default 5 min). No limit = `Int.MAX_VALUE`, always plays full video.
- `PhotoDetailViewModel.load()` accepts `thresholdSeconds`. Routing mirrors web logic.
- `PreviewVideoPlayer` composable: `Player.Listener` for `STATE_ENDED`; 500ms position poll via `LaunchedEffect`; overlay as `Box` over the `AndroidView`.

**Deployment:** Server `heirlooms-server-00040-w5h`. Web `heirlooms-web-00053-gbw`. APK installed.

---

## Sessions — 11 May 2026 (M8 bugfixes v0.38.0–v0.43.1, M9 v0.45.0)

*Note: Sessions between v0.37.0 and v0.45.0 were not logged here at the time. Brief summary:*

**v0.38.0–v0.43.1 — M8: Multi-user, QR pairing, Fire OS fixes**
- Multi-user auth (Argon2id passphrase → auth_key + master_key_seed), invite-only registration,
  per-user session tokens, Android passphrase setup flow (`setup-existing` for founding user).
- Web device pairing: Android generates numeric code → web encodes pubkey as QR → Android wraps
  master key → web polls for completion. Deep link `heirlooms://pair?session=...` triggers pairing screen.
- `PairPage.jsx` added as public route; `navigate()` race fixed with `window.location.replace('/')`.
- Fire OS `CaptureVideo()` crash: replaced with plain `ACTION_VIDEO_CAPTURE` intent.
- `zxing-android-embedded` adds `CAMERA` to manifest; runtime permission request added.
- OOM fix: `catch (_: Exception)` → `catch (_: Throwable)` in `UploadWorker`.
- Android versionCode 35→46, versionName 0.33.0→0.43.0.

**v0.44.0 — M8 bugfix iteration 1**
- Brief at `docs/briefs/M8_bugfix_iteration_1.md`.
- QR scan in `PairingScreen`, upload OOM fix, CAMERA permission request, Fire OS video intent.

**v0.45.0 — M9: Friends, item sharing, Android plot management**
- DB: `account_sharing_keys`, `friendships` tables (V23 migration). `shared_from_upload_id`,
  `shared_from_user_id` nullable columns on `uploads`.
- Server: `SharingKeyHandler` (`PUT/GET /api/keys/sharing`, `GET /api/keys/sharing/{userId}`),
  `FriendsHandler` (`GET /api/friends`), `shareUploadContractRoute`, `hasLiveSharedReference`
  compost guard, friendship created on `register`.
- Android: P-256 sharing keypair generation (`generateSharingKeypair`, `sec1ToSpki`,
  `unwrapWithSharingKey`), `VaultSession.sharingPrivkey`, `ensureSharingKey()` lazy init,
  `ShareSheet`, `FriendsScreen`, `PlotCreateSheet`, `PlotEditSheet`, `GardenScreen` share icon
  + friend indicator + plot management. `HeirloomsImage` routes decryption by `thumbnailDekFormat`.
- versionCode 49, versionName 0.45.0. Deployed: server `heirlooms-server-00036-xxx`.

---

## Session — 12 May 2026 (M9 bug fix iteration 1, first human tester — v0.45.1–v0.45.5)

### What was built / fixed

**First human tester onboarded:** Wighty (TCL T517D, Android 15) invited by Bret via Devices &
Access → Generate invite link. Friendship confirmed in DB. APK installed via ADB.
Added `InviteRedemptionScreen` "Scan" button so new users can scan the invite QR code directly.

**v0.45.1 — Shared item decryption + rotation copy + invite scan**
- `PhotoDetailViewModel`: unwrap DEK via `unwrapWithSharingKey` when `dekFormat == ALG_P256_ECDH_HKDF_V1`.
  Same fix for `downloadFullFile`. Without this, full-screen shared images spun forever.
- `Database.createSharedUpload`: added `rotation` to INSERT and copied from `fromRecord.rotation`
  (was hardcoded 0 — shared items always arrived unrotated).
- `InviteRedemptionScreen`: "Scan" button using ZXing `ScanContract`; extracts `token` query
  param from the scanned URL, populates the invite code field.

**v0.45.2 — Re-share dedup guard**
- `Database.userAlreadyHasStorageKey`: checks if recipient already has any live upload with
  the same storage key.
- `handleShareUpload`: returns 409 if recipient already has the item.
- `ShareSheet`: shows "X already has this item." on 409, generic message on other errors.

**v0.45.3 — Re-sharing fix + poll interval**
- `ShareSheet.shareWithFriend`: was always using `unwrapDekWithMasterKey`. Fixed to check
  `upload.dekFormat` and use `unwrapWithSharingKey` when re-sharing a received item (same
  fix as PhotoDetailViewModel — received items have DEK wrapped with sharing privkey, not master key).
- `GardenScreen`: Just Arrived poll interval 30s → 5s.

**v0.45.4 — Web sharing decryption + rotation race guard**
- Web: `vaultSession.js` gains `_sharingPrivkey`, `setSharingPrivkey`, `getSharingPrivkey`.
  `vaultCrypto.js` gains `importSharingPrivkey`, `unwrapWithSharingKey`.
  `VaultUnlockPage`: `loadSharingKey()` fetches `GET /api/keys/sharing/me`, unwraps privkey
  with master key, imports as P-256 CryptoKey, stores in session. Awaited before `onUnlocked()`.
  `UploadThumb` and `PhotoDetailPage`: check `thumbnailDekFormat`/`dekFormat`, route to
  `unwrapWithSharingKey` for `ALG_P256_ECDH_HKDF_V1`.
- Android `PhotoDetailScreen`: `rotateAndSave()` sets `rotateInFlight` flag; `navigateBack()`
  bails early while flag is set, preventing share from racing the rotation save.

**v0.45.5 — Web sharing key path fix**
- `VaultUnlockPage.loadSharingKey`: URL was `/api/sharing/me` (404) — corrected to
  `/api/keys/sharing/me` (sharing routes live in `keysContract` bound to `/api/keys`).
- First shared video thumbnail now loads in browser after vault unlock. 3 of 4 shared
  image thumbnails still showing fallback — under investigation (possible `wrappedThumbnailDek`
  format difference for older shares).

### Early users
- **Wighty** onboarded 12 May 2026. TCL T517D, Android 15.
- **Sadaar** (Fire OS tablet) used as share recipient for cross-device testing.
- TEAM.md updated with Early Users section.

### Design notes
- **Comments/messages system deferred to M10:** User wanted encrypted per-share messages with
  audience targeting and revocation. Scope grew to a comments system — parked for M10.
- **drand timelock encryption noted for future:** BLS12-381 pairing operations not in Android
  standard crypto stack; requires dedicated milestone.
- **Rotation save must complete before sharing:** Share API re-reads upload from DB. If rotation
  save is still in flight when share fires, recipient gets old rotation. Fixed by blocking back
  navigation while save is in flight.

**v0.45.6 — EXIF orientation fix (Android)**

Prompt: Images uploaded from Android cameras have an EXIF Orientation tag embedded in the JPEG. Web
browsers apply this automatically; Android's BitmapFactory ignores it, so images appeared sideways
on Android while displaying correctly on web. Root cause confirmed via DB: shared copies were
receiving rotation=0 because uploads were being shared before the user noticed and manually rotated
to compensate — the manual rotation was being applied after the share, not before.

Changes:
- `app/build.gradle.kts`: added `androidx.exifinterface:exifinterface:1.3.7` dependency; bumped
  versionCode 49→50, versionName to 0.45.6
- `Uploader.kt`: `generateThumbnail()` now applies EXIF orientation to the image bitmap before
  encoding to JPEG, so new thumbnails have correct pixel orientation from day one
- `PhotoDetailViewModel.kt`: `loadEncryptedContent()` now reads EXIF orientation from the
  decrypted image bytes and rotates the bitmap accordingly, so the full-image detail view
  displays correctly on Android without requiring a manual rotation

Two-part fix:
1. `generateThumbnail()` in Uploader.kt applies EXIF rotation to the bitmap before encoding, so
   new upload thumbnails have correct pixel orientation from day one.
2. `loadEncryptedContent()` in PhotoDetailViewModel.kt reads EXIF rotation from the decrypted
   full-image bytes. If non-zero and the upload has no manual rotation set, it auto-stages that
   value as a rotation — which gets saved to the DB when the user navigates back. This causes the
   Garden to refresh with the updated rotation column, fixing the thumbnail on subsequent views.

This approach avoids applying EXIF directly to the decoded bitmap (which would double-rotate
images that were already manually corrected). Instead, the rotation column becomes the single
source of truth, populated from EXIF on first open for images that haven't been manually rotated.

Images previously hand-rotated to compensate for missing EXIF (rotation=90 on a photo whose EXIF
also encodes 90°) will double-rotate and need to be reset to rotation=0.

**v0.45.7 — Rotation included in share payload**

Prompt: when Bret rotated a received share and re-shared it, Sadaar received rotation=0. Root
cause: the server reads rotation from DB at share time, but the client's rotation save may not yet
be committed (race between the EXIF auto-stage save and the share API call). Fix: include
`upload.rotation` from the client in the share payload; the server uses this value as an override
rather than solely relying on the DB read. This closes the race: the client always knows the
rotation as currently displayed to the user.

- `HeirloomsApi.shareUpload()`: added `rotation: Int = 0` parameter, included in JSON body
- `ShareSheet.shareWithFriend()`: passes `upload.rotation` to `shareUpload()`
- `UploadHandler.handleShareUpload()`: reads optional `rotation` field from request body
- `Database.createSharedUpload()`: accepts `rotationOverride: Int?`, uses it if present

**v0.45.8 — Web: load sharing key on all auto-unlock paths**

Prompt: shared images from Sadaar never loaded on the web — thumbnails showed the olive branch
fallback and photo detail showed nothing. Root cause: `loadSharingKey` was only called inside
`VaultUnlockPage.handleUnlock`. Two other unlock paths in `App.jsx` bypass `VaultUnlockPage`
entirely and never loaded the sharing key:
1. IDB auto-unlock on page refresh (stored pairing material).
2. Login-time auto-unlock when `tryUnlockVaultAfterLogin` succeeds (stored device key in IDB).

Fix: extracted `loadSharingKey` as a module-level function in `App.jsx` and call it in both
paths, awaited before setting `vaultUnlocked(true)` so thumbnails start loading with the key
already available.

### Deployment
Server `heirlooms-server-00043-4ss` (v0.45.7). Web `heirlooms-web-00057-jcp` (v0.45.8).
APK v0.45.6–v0.45.7 installed on Bret's Samsung A02. Sadaar's Fire OS tablet needs v0.45.6+ APK.
