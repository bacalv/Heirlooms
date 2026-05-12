# Heirlooms — Version History

---

## v0.45.6–v0.45.8 — M9 bug fix iteration 2 (12 May 2026)

Android (v0.45.6–v0.45.7), server (v0.45.7), web (v0.45.8).

- **EXIF orientation on Android (v0.45.6)** — `BitmapFactory` ignores the JPEG Orientation tag; browsers apply it automatically. Three-part fix: (1) `generateThumbnail()` applies EXIF rotation to the bitmap before encoding, so new thumbnails have correct pixel orientation. (2) `loadEncryptedContent()` reads EXIF from decrypted bytes and auto-stages the rotation; it saves to the `rotation` column via the navigate-back save path, fixing the garden thumbnail on next refresh. EXIF is not applied to bitmap pixels (would double-rotate manually-corrected images). `rotation` column is the single source of truth. Added `androidx.exifinterface:exifinterface:1.3.7`.
- **Rotation included in share payload (v0.45.7)** — When re-sharing a received item, the server read `rotation` from DB at share time, but the sender's rotation save could race with the share call. Fix: client sends `upload.rotation` in the share body; server uses it as `rotationOverride` in `createSharedUpload`, bypassing the race.
- **Web sharing key on all auto-unlock paths (v0.45.8)** — `loadSharingKey` was only called from `VaultUnlockPage.handleUnlock`. IDB auto-unlock (page refresh) and login-time auto-unlock (stored device key) bypassed it entirely, leaving all p256-DEK thumbnails and images silently blank. Fix: extracted `loadSharingKey` to `App.jsx` and called it on both auto-unlock paths, awaited before `setVaultUnlocked(true)`.

---

## v0.45.0 — M9: Friends, item sharing, Android plot management (11 May 2026)

Server + Android. No web changes.

- **Account-level sharing keypair** — Each user has a P-256 keypair generated on first vault unlock after upgrade. Private key wrapped to master key and stored server-side (`account_sharing_keys` table). Public key available to friends. Used to re-wrap DEKs when sharing items.
- **Friendships** — Automatic on invite redemption. Bidirectional. Backfilled from existing redeemed invites. New `friendships` table.
- **Item sharing (Android)** — Share icon (bottom-right) on encrypted garden thumbnails. Tapping opens a friend picker sheet. The sender unwraps the item DEK and thumbnail DEK, re-wraps both to the recipient's sharing pubkey, and calls `POST /api/content/uploads/{id}/share`. Server creates a recipient upload record pointing to the same GCS blob.
- **Received shared items** — Land in Just Arrived with a person icon indicator. "Shared by [name]" attribution in photo detail view. Per-user rotation and tags (independent of the original owner's).
- **Friends screen** — Burger → Friends. Shows display name + username for each friend.
- **Android plot management** — Create (inline "+ Add plot" row at bottom of garden scroll), rename, and delete plots. Each plot row header has a pencil edit icon.
- **Compost GCS guard** — Compost cleanup now skips GCS blob deletion if any active (non-composted) shared copy references the same storage key.

---

## v0.44.0 — Android bugfix iteration 1 (11 May 2026)

Android only.

- **Bug 1 — Blank garden after "Go to Garden"** — `UploadProgressScreen` reached via Burger → Uploads is a pushed route on top of Garden. "Go to Garden" now calls `popBackStack()` instead of `navigateToTab()`, which was triggering unnecessary state save/restore machinery and producing a blank white screen.
- **Bug 2 — New Just Arrived item at position −1** — `LaunchedEffect` in `PlotRowSection` is now keyed on `newlyArrivedIds` (a `Set`) rather than `shouldScrollToStart` (a `Boolean`). When a second item arrives while the set is already non-empty, the boolean didn't change so the scroll never re-fired; keying on the set ensures it always re-fires.
- **Bug 3 — Garden tab doesn't dismiss BurgerPanel** — `onTabSelected` in `MainNavigation` now calls `burgerSheetState.hide()` and sets `showBurger = false` before `navigateToTab()`, so the sheet plays its dismiss animation cleanly when the user taps any tab.
- **Bug 5 — Loading spinner instead of plant icon for thumbnails** — `EncryptedThumbnail` loading state now shows `OliveBranchIcon` (matching the failed state) instead of `CircularProgressIndicator`. Both "not yet available" states look the same.
- **Bug 6 — + Video crashes on Fire OS** — Fire OS camera apps don't support `EXTRA_OUTPUT` for `ACTION_VIDEO_CAPTURE` (the URI pre-creation that `ActivityResultContracts.CaptureVideo()` uses). Switched to `StartActivityForResult` with a plain `ACTION_VIDEO_CAPTURE` intent; URI is retrieved from `result.data?.data` and routed through the existing `copyContentUriToCache` path. This was masked in earlier versions by the camera permission crash fixed in v0.43.1.

---

## v0.43.1 — Android bugfix: camera permission on Fire OS (11 May 2026)

Android only.

- **Camera permission request** — Adding `zxing-android-embedded` in v0.43.0 put `CAMERA` into the merged manifest. Fire OS enforces it strictly at runtime — `TakePicture`/`CaptureVideo` crashes without a prior grant. `GardenScreen.launchCamera()` now checks `ContextCompat.checkSelfPermission` and requests the permission via `cameraPermissionLauncher` if not already granted before launching the camera intent.

---

## v0.43.0 — Android bugfix: QR scan + upload OOM (11 May 2026)

Android only.

- **PairingScreen QR scan** — Added camera scan button using `zxing-android-embedded`. "Scan QR code" is now the primary action; scanning auto-submits without a second button press. Paste-JSON field retained as fallback. `CAMERA` permission added to manifest.
- **Upload OOM fix** — Preview clip generation (`generatePreviewClip`) and its enclosing block in `uploadEncryptedViaSigned` changed from `catch (_: Exception)` to `catch (_: Throwable)`. On memory-constrained devices (e.g. Fire OS tablet), `outputFile.readBytes()` after a large video upload could throw `OutOfMemoryError` (a `Throwable`, not an `Exception`), which escaped all catch guards, crashed the WorkManager worker, and caused confirm to never fire — leaving the upload invisible in the garden despite both GCS objects landing successfully. `UploadWorker.doWork()` also now wraps the entire uploader call in `try/catch (t: Throwable)` so any future unexpected error routes through the existing retry/failure path instead of silently failing.

---

## v0.42.1 — InviteRedemptionScreen UX fix (11 May 2026)

Android only. Post-deploy fix found during first live invite test on Fire OS.

- **Scrollable form** — `verticalScroll` + `imePadding` added to the Column so the "Create account" button is always reachable regardless of screen size or keyboard state.
- **Keyboard navigation** — `ImeAction.Next` on each field advances focus in order (Invite code → Username → Display name → Passphrase → Confirm); `ImeAction.Done` on the last field submits the form directly.
- **Submit extracted** — form submission logic moved to a local `submit()` function shared by the button `onClick` and the `onDone` keyboard action.

---

## v0.42.0 — M8 E5: Fixup pass before deploy (11 May 2026)

Server + Web + Android. Milestone 8 ready to deploy.

- **`GET /api/auth/me`** — Lightweight session-validation endpoint. Returns `{user_id, username, display_name}` for the authenticated caller. Used by both clients on mount to confirm a cached session token is still valid before performing any other request. Returns 401 via `SessionAuthFilter` if token is missing, expired, or invalid.
- **Web: IDB pairing persistence** — New `webPairingStore.js` module persists the pairing keypair and wrapped master key in IndexedDB (one record keyed `'current'`). On app mount, the boot handler calls `GET /api/auth/me`; on 200, loads IDB material and auto-unlocks the vault without re-entering a passphrase. On 401, clears IDB and session. `PairPage` saves material after successful pairing. `handleSignOut` clears IDB on logout.
- **`authMe` in `api.js`** — Thin wrapper for `GET /api/auth/me`.
- **V22 migration** — Adds `user_id UUID NULL REFERENCES users(id) ON DELETE SET NULL` to `diagnostic_events`. Pre-M8 rows stay `NULL`; new rows are scoped to the authenticated user. `GET /api/diag` filters by caller's `user_id`; `POST /api/diag` sets `user_id`.
- **Isolation test suite** — Added 8 tests covering: `GET /auth/me` isolation, `wrapped_keys` cross-user access (4 tests), `recovery_passphrase` cross-user isolation (1 test), `diagnostic_events` scoping (2 tests).
- **Recovery protocol fix** — `POST /api/auth/register` and `POST /api/auth/setup-existing` now accept optional `wrapped_master_key_recovery` + `wrap_format_recovery` fields and store them in `recovery_passphrase` (format `master-aes256gcm-v1`). Fresh-browser login can derive `master_key_seed` from passphrase and decrypt the recovery blob to recover the master key.
- **`AuthHandlerTest`** — Added tests for `GET /me` (authenticated, unauthenticated, expired session), and cross-platform recovery verification.
- **Android `MigrationScreen`** — Username field is now read-only when the stored username is already known (locked to the account's existing username). Muted border indicates non-editable state.
- **Deploy hygiene** — Removed dead `API_KEY` secret binding from Cloud Run deploy command (E2 removed `ApiKeyFilter`; secret stays in Secret Manager). Deploy window documented.
- **3 IDB persistence vitest tests** — pairing saves to IDB, mount recovers from IDB, 401 clears IDB.
- All 116 web tests pass. All 251+ server tests pass.

---

## v0.41.0 — M8 E4: Android auth migration, Devices & Access, capsule ViewModel (11 May 2026)

Android only. Milestone 8 complete.

- **Session token** — `EndpointStore` gains `getSessionToken/setSessionToken/clearSessionToken`, `getUsername/setUsername`, `getAuthSalt/setAuthSalt`. `ShareActivity` uses session token (falls back to legacy api_key) for upload workers.
- **`VaultCrypto` additions** — `deriveAuthAndMasterKeys(passphrase, salt)`: Argon2id with 64-byte output, returns `(auth_key[0..31], master_key_seed[32..63])`. `computeAuthVerifier(authKey)`: SHA-256. `wrapMasterKeyForRecipient(masterKey, recipientSpki)`: ECDH-HKDF-AES-GCM wrap for any P-256 SPKI recipient. `toBase64Url` / `fromBase64Url` / `padStart` helpers (pure JVM).
- **`HeirloomsApi` auth endpoints** — `authChallenge`, `authLogin`, `setupExisting`, `authLogout`, `getInvite`, `pairingInitiate`, `pairingComplete`, `authRegister`.
- **`MainApp`** — Multi-path first-run: has session_token → normal; has api_key but no session_token → `MigrationScreen`; neither → `InviteRedemptionScreen`. `WelcomeScreen` follows registration; `VaultSetupScreen` for vault-not-ready.
- **`MigrationScreen`** — One-time founding user passphrase setup via `POST /api/auth/setup-existing`. Clears the old api_key on success.
- **`InviteRedemptionScreen`** — First-run invite registration. Argon2id auth key derivation + P-256 device keypair + `POST /api/auth/register`.
- **`LoginScreen`** — Re-auth screen for expired sessions. Challenge → Argon2id → login → store session.
- **`DevicesAccessScreen`** — Invite section: QR code (ZXing) + Android share sheet. Pairing section: shows 8-digit code, navigates to PairingScreen.
- **`PairingScreen`** — Accepts QR JSON from web browser; ECDH-wraps master key to web pubkey; calls `POST /api/auth/pairing/complete`.
- **`PairingQrParser`** — Pure utility for parsing `{session_id, pubkey}` JSON from pairing QR.
- **`CapsuleCreateViewModel`** — New ViewModel with `recipient`, `unlockDate`, `message`, `selectedPhotos`, `isSubmitting` StateFlows. Validates recipient (required), unlock date (must be future). `submit(api)` calls `POST /api/capsules`.
- **Nav** — Devices & Access entry added to BurgerPanel. `DEVICES_ACCESS` and `PAIRING` routes added to AppNavigation.
- **13 new unit tests** — 8 auth tests (VaultCrypto KDF, challenge/login flow, session detection, pairing QR parse, logout) + 5 capsule ViewModel tests. All 117 Android tests pass.
- **Build** — `com.google.zxing:core:3.5.3` (QR generation), `org.json:json:20231013` (test JVM JSON), added to dependencies.

---

## v0.40.0 — M8 E3: Web client auth (11 May 2026)

Web only.

- **`LoginPage`** — Replaced API-key input with username + passphrase form. Challenge flow: `POST /api/auth/challenge` → Argon2id(passphrase, auth_salt, m=65536, t=3, p=1, 64 bytes) → derive auth_key (bytes 0–31) + master_key_seed (bytes 32–63) → `POST /api/auth/login` with base64url auth_key → receive session token. After login, attempts to auto-unlock vault using device private key from IndexedDB if the device was previously registered.
- **`JoinPage`** (new, route `/join`) — Invite registration. Token from URL query param or manual entry. Generates P-256 keypair, generates random master key, wraps with P-256 pubkey (ECDH-HKDF-AES-GCM). Posts to `POST /api/auth/register`. Unlocks vault immediately in memory. Shows inline errors for 409 (username taken) and 410 (expired invite).
- **`AccessPage`** (new, route `/access`) — Devices & Access. "Generate invite link" button calls `GET /api/auth/invites` and shows shareable `/join?token=<token>` URL with copy-to-clipboard and expiry countdown. "Pair with phone" link navigates to `/access/pair`.
- **`PairPage`** (new, route `/access/pair`) — QR pairing. Numeric code entry → `POST /api/auth/pairing/qr` → generate ephemeral P-256 keypair (private key in memory) → QR code displayed (JSON `{session_id, pubkey}`) → polls `GET /api/auth/pairing/status` every 1 second → on complete: ECDH-unwrap master key, store session token, navigate to `/`.
- **Session storage** — Session token stored in `localStorage` under `heirlooms_session_token`. `sessionToken` replaces `apiKey` as the canonical context value; `apiKey` alias retained for backwards compat with existing pages and tests.
- **`App.jsx`** — `sessionToken` state; new routes `/join`, `/access`, `/access/pair`. `handleSignOut` calls `POST /api/auth/logout`. `handleLogin(token, masterKey?)` sets session and optionally unlocks vault.
- **`Nav.jsx`** — "Access" link added to both desktop and mobile menus.
- **`api.js`** — Added: `authChallenge`, `authLogin`, `authLogout`, `authRegister`, `getInvite`, `pairingQr`, `pairingStatus`.
- **`vaultCrypto.js`** — Added: `unwrapMasterKeyForDevice` (ECDH-HKDF-AES-GCM decrypt), `toB64url`, `fromB64url`, `sha256`.
- **10 new vitest tests** — cover login, login failure, registration, 409/410 errors, pairing flow, invite generation, logout, RequireAuth redirect. All 113 web tests pass.

---

## v0.39.0 — M8 E2: Per-user auth enforcement + isolation tests (11 May 2026)

Server only.

- **`SessionAuthFilter`** — http4k `Filter` wrapping the whole app. Validates `X-Api-Key` as a session token (`SHA256(token)` lookup), attaches `X-Auth-User-Id` to the forwarded request, refreshes `last_used_at`. Unauthenticated paths (`/api/auth/challenge`, `/login`, `/setup-existing`, `/register`, `/pairing/qr`) bypass the filter. All other routes return `401` with no token or an expired/invalid token.
- **Per-user DB scoping** — All read/write operations now scope to the authenticated `userId`. All upload, capsule, plot, and keys handler routes read `request.authUserId()` and pass it to the DB layer.
- **`Main.kt`** — `apiKeyFilter` replaced with `sessionAuthFilter(database)`.
- **`PendingBlobsCleanupService`** — periodic coroutine now also calls `database.deleteExpiredSessions()`.
- **`createSystemPlot` on register** — `registerRoute` calls `database.createSystemPlot(userId)` after user creation, ensuring every new user has a `__just_arrived__` system plot.
- **`IsolationTest`** — Two-user Testcontainers integration suite (Alice + Bob). 23 tests covering upload, plot, capsule, and auth isolation. All 269 server tests pass.

---

## v0.38.0 — M8 E1: Schema + Auth API (11 May 2026)

Server only.

- **V20 migration** — `users`, `user_sessions`, `invites` tables. Session tokens are 256-bit random values; only `SHA256(token)` is stored. Sessions expire after 90 days of inactivity.
- **V21 migration** — Seeds the founding user row; backfills `user_id` onto all existing `uploads`, `capsules`, `plots`, `wrapped_keys`, and `recovery_passphrase` rows. Tightens NULL sentinels to proper FK constraints. Adds `user_id`, `web_session_id`, `raw_session_token`, and `session_expires_at` to `pending_device_links`.
- **Auth endpoints** (`/api/auth/*`) — `POST /challenge`, `POST /login`, `POST /setup-existing` (one-time founding user migration path), `POST /logout`, `GET /invites`, `POST /register`, `POST /pairing/initiate`, `POST /pairing/qr`, `POST /pairing/complete`, `GET /pairing/status`.
- Existing handlers are **unchanged** — all continue to work without authentication in E1. Enforcement is E2.
- `FOUNDING_USER_ID` constant in `Database.kt` used as the default `user_id` for all existing INSERT paths until E2 wires up per-user context.
- 8 schema canary tests + 20 auth endpoint integration tests (Testcontainers). All 246 server tests pass.

---

## v0.37.0 — Duration-based playback threshold, preview label + end overlay (11 May 2026)

Server + Web + Android.

- **Server** — V19 migration: `duration_seconds INTEGER` column on `uploads`. Plaintext video confirms extract duration via `ffprobe`. Encrypted confirms store the client-supplied `durationSeconds`.
- **Web** — `FULL_PLAYBACK_THRESHOLD_SECONDS = 120` (2 min). Duration read from `<video>.duration` at upload time and sent in confirm. Under threshold → play full video; over threshold + preview clip → `PreviewVideoPlayer`; over threshold, no preview → thumbnail only. `PreviewVideoPlayer` shows "Preview · 0:08 of 5:32" label while playing; full translucent overlay with Download button on `ended` event.
- **Android** — Duration extracted via `MediaExtractor` at upload time; sent in confirm. Per-device playback threshold stored in `SharedPreferences` via `EndpointStore.getVideoPlaybackThreshold()` (default 300s / 5 min). Settings screen gains a "Play full video up to" segmented picker: 1 min / 5 min / 15 min / No limit. No limit always plays the full video, preview system invisible. `PhotoDetailViewModel.load()` takes the threshold. `PreviewVideoPlayer` composable shows label (ExoPlayer position polled every 500ms) and overlay on `STATE_ENDED`.

---

## v0.36.0 — Preview clips, 1 MiB chunks, parallel prefetch, download button (11 May 2026)

Server + Web + Android.

- **Server** — V18 migration: `preview_storage_key`, `wrapped_preview_dek`, `preview_dek_format`, `plain_chunk_size` columns on `uploads`. `GET /api/settings` returns `previewDurationSeconds` (env `PREVIEW_DURATION_SECONDS`, default 15). `GET /api/content/uploads/{id}/preview` proxies the encrypted preview clip.
- **Web** — ffmpeg.wasm (`@ffmpeg/ffmpeg`, `@ffmpeg/core`) trims the first N seconds of large encrypted videos into a preview clip at upload time; clip encrypted with its own DEK and uploaded. Full video accessible via Download button only. Chunk size drops from 4 MiB to 1 MiB for new uploads; `plain_chunk_size` stored per-upload so old files decrypt correctly. MSE streaming gains 4-wide parallel prefetch. `PhotoDetailPage` uses preview clip for large videos.
- **Android** — Same 1 MiB chunk size. Preview clip via `MediaExtractor` + `MediaMuxer`. `downloadFullFile()` saves plaintext to Downloads folder via `MediaStore`. `DecryptingDataSource` chunk size read from upload record.

---

## v0.35.0 — Web encrypted video playback + MSE streaming (10 May 2026)

Web only.

- Encrypted videos play in the browser without full download. Strategy: download chunk 0, detect faststart (moov in chunk 0) via mp4box. Faststart → MSE streaming (raw decrypted bytes appended to `SourceBuffer`). Non-faststart → full download then blob URL.
- `openEncryptedVideoStream()` in `encryptedVideoStream.js`. Codec sniffing via mp4box `onReady` fires synchronously on `appendBuffer` for faststart files.

---

## v0.34.0 — Web streaming-format decrypt fix for large encrypted videos (10 May 2026)

Web only. Bug fix.

- `decryptStreamingContent` was using a fixed 4 MiB block size that didn't match the actual chunk boundaries of streaming-encrypted content, producing GCM authentication failures on large files. Fixed by aligning reads to the correct cipher chunk boundaries (nonce + ciphertext + tag per chunk).

---

## v0.33.0 — Streaming encryption for large files (10 May 2026)

Server + Android + Web. No schema changes.

- **`POST /api/content/uploads/resumable`** — new server endpoint. Initiates a GCS resumable upload session for a given storageKey and returns the session URI. Called after `/initiate` for files above the 10 MB threshold; the client PUTs ciphertext chunks directly to GCS using `Content-Range` headers.
- **Chunk format** — 4-byte big-endian header (chunk_size = 4 MB, doubles as format discriminator for the decrypt path) + per-chunk `[nonce(12)][ciphertext+tag(n+16)]`. Legacy single-buffer uploads are unchanged.
- **Android** — `uploadEncryptedViaSigned` restructured: `/initiate` is called before reading any file bytes; files > 10 MB take the streaming path (peak memory ~8 MB), smaller files keep the existing in-memory path. `VaultCrypto.aesGcmEncryptWithAad` added for chunk-level AAD binding.
- **Web** — `encryptAndUpload` restructured identically. `aesGcmEncryptWithAad` added to `vaultCrypto.js`. Streaming helpers in `GardenPage.jsx`.
- Also fixes a pre-existing server test failure: `POST /uploads/initiate` with `storage_class: "public"` now returns 400 as the test expected.

---

## v0.32.0 — Diagnostics screen + Fire OS FreeTime detection (10 May 2026)

Android-only. No server data-path changes; new `diagnostic_events` table and two
API endpoints.

- **Diagnostics screen** — accessible from the burger menu. Lists in-session error events
  (most recent first). Tap any event to expand the full detail. "Report to server" button
  sends the event to `POST /api/diagnostics/events`; shows `Sent ✓` or `Failed — tap to
  retry` feedback. "Clear" button in the top bar wipes all events for the session.
- **`DiagnosticsStore`** — in-process singleton (`mutableStateListOf`) holding up to 300
  events. Logged from `copyContentUriToCache` on file-picker failures.
- **`copyContentUriToCache` diagnostic logging** — on failure, appends a step-by-step trace
  (URI, docId, mediaUri, DATA path, exception per attempt) to `DiagnosticsStore` instead of
  returning an opaque error string.
- **FreeTime secure-storage detection** — if the DATA column returns a path under
  `/data/securedStorageLocation/` (Amazon's FreeTime / Kids+ encrypted store), the function
  returns immediately with a friendly message: *"This file belongs to a different account on
  this device and can't be accessed."* No diagnostic event is logged for this case (it is
  expected, not a bug).
- **`jsonEsc()` fix** — the JSON serialiser for `postDiagEvent` was not escaping `\n`, `\r`,
  or `\t`, producing invalid JSON bodies and silently failing the POST. Fixed.
- **Server: `diagnostic_events` table** — Flyway V17 migration. Columns: `id UUID`,
  `created_at TIMESTAMP`, `device_label TEXT`, `tag TEXT`, `message TEXT`, `detail TEXT`.
- **Server: diagnostics endpoints** — `POST /api/diagnostics/events` (insert event) and
  `GET /api/diagnostics/events` (list last 200, newest first), both under `/api`.
- versionCode 34. Installed on Amazon KFRAPWI (Fire HD 8, Fire OS) and Samsung Galaxy A02s.

---

## v0.31.0 — Android: Garden Plant button (10 May 2026)

First Android APK release since v0.28.1. Also picks up the E5 Android changes
(VaultSetupScreen copy, storageClass rename) that shipped in v0.30.0 server/web but
had no corresponding APK at the time.

- **Plant FAB** — Forest-coloured floating action button (bottom-right of Garden).
  Tapping opens a `ModalBottomSheet` with three options: Photo, Video, File.
- **Photo capture** — launches system camera via `TakePicture`; on return shows
  `PlantPreviewOverlay` with the captured JPEG rendered full-screen via Coil. "Retake"
  re-launches the camera immediately. "Plant" enqueues an `UploadWorker` job using the
  file written to `cacheDir` by the camera — no copy needed.
- **Video capture** — same flow via `CaptureVideo`; preview plays back in-app via
  ExoPlayer (already a dep from `PhotoDetailScreen`).
- **File picker** — `OpenDocument(*/*)`; any file type accepted. Preview shows the file
  name resolved from `OpenableColumns.DISPLAY_NAME` and a drive-file icon. File is
  copied to `cacheDir` before enqueueing (content URI access is only valid while the
  Activity is alive).
- **Back gesture** — `BackHandler` resets the plant flow at any point without uploading.
- **FileProvider** — added to `AndroidManifest.xml` with a `cache-path` provider; required
  for passing a writable output URI to the system camera.
- Upload appears in Just Arrived via the existing 30-second poll — no new API.
- versionCode 33. Installed on Samsung Galaxy A02s (SM-A025F, Android 12).

---

## v0.30.0 — M7 E5: storage class rename, web EXIF, onboarding copy (9–10 May 2026)

Final M7 increment. No new cryptographic operations; no new API endpoints.

- **V16 migration** — renames all `storage_class = 'legacy_plaintext'` rows to `'public'` in
  both `uploads` and `capsule_messages`. Reflects the mechanism's intended future use
  (public plots, public uploads) rather than its transitional origin. Migration runs on
  server startup; takes effect immediately on first cold start of `heirlooms-server-00035-qwc`.
- **Server** — `UploadRecord` default changed to `"public"`. "Not yet supported" guards for
  `public` removed from initiate and confirm handlers. Migrate endpoint updated to check for
  `!= "public"`. `exif_processed_at` auto-set guard updated to `== "public"`.
- **Android** — `Upload.storageClass` default and JSON parser fallback updated to `"public"`.
- **Web EXIF** — `exifr ^7.1.3` added. `buildEncryptedMetadata(file)` reads GPS coordinates,
  camera make/model, lens model, focal length, ISO, shutter speed, and aperture from JPEG EXIF
  before encryption. Encrypted under the content DEK and passed to `confirmEncryptedUpload`.
  Videos remain all-null (browser EXIF extraction from video is unreliable). Server already
  stored the blob; only the client-side extraction was missing.
- **Android onboarding copy** — `VaultSetupScreen`: headline → "Your vault", sub-copy →
  "Your passphrase unlocks your vault on any device. Keep it somewhere safe.", button →
  "Protect vault".
- **Web onboarding copy** — `VaultUnlockPage` Case C sub-label →
  "Your passphrase unlocks your vault from any browser. Keep it somewhere safe."
- **GCS CORS** — `PUT` method added to CORS policy on `gs://heirlooms-uploads`. Required
  for direct browser-to-GCS uploads via signed URLs (Plant button). Was missing because
  the Plant flow had never been tested end-to-end before E5.
- **Upload error auto-clear** — plant error message now clears after 4 seconds (same cadence
  as "Done" status).
- **Tests** — `SchemaMigrationTest`, `UploadHandlerTest`, `E2EncryptedUploadTest` updated for
  `"public"`. 102 web tests pass.
- Deployed: server `heirlooms-server-00035-qwc`, web `heirlooms-web-00039-g86`.

---

## v0.29.0 — M7 E4: web client encryption (9 May 2026)

- `src/crypto/vaultCrypto.js` — full WebCrypto + Argon2id (via `@noble/hashes`) crypto
  layer. Symmetric envelopes (AES-256-GCM), asymmetric envelopes (P-256 ECDH + HKDF),
  passphrase wrapping (Argon2id), DEK wrapping under master key. Byte-for-byte compatible
  with the server's `EnvelopeFormat` and Android's `VaultCrypto.kt`. A `setTimeout(0)`
  yield precedes the synchronous Argon2id call so the "Unlocking…" spinner renders before
  the main thread blocks (~2-3s on an average device).
- `src/crypto/vaultSession.js` — in-memory master key singleton + LRU thumbnail
  object-URL cache (max 300 entries, evicted with `URL.revokeObjectURL`).
- `src/crypto/deviceKeyManager.js` — P-256 keypair generated once per browser and stored
  as `CryptoKey` objects in IndexedDB (`extractable: false`). Device registered with server
  once; `localStorage` flag guards re-registration. Browser data wipe = new device
  (equivalent to Android factory reset).
- `src/pages/VaultUnlockPage.jsx` — three-case passphrase screen: returning browser
  (Case A: unlock only), new browser + existing account (Case B: unlock + register device),
  brand-new account (Case C: generate master key + setup + register device).
- `src/components/UploadThumb.jsx` — dual-path thumbnail: encrypted uploads fetch
  ciphertext, decrypt on-device, display via object URL; plaintext uploads use existing
  `getThumb` path. Decrypted URLs cached in `vaultSession.thumbnailCache`.
- `src/App.jsx` — vault unlock gate between API key login and main navigation.
  Sign-out calls `vaultSession.lock()`.
- `src/api.js` — `putPassphrase`, `registerDevice`, `initiateEncryptedUpload`,
  `putBlob`, `confirmEncryptedUpload`, `fetchBytes`.
- `src/pages/GardenPage.jsx` — "Plant" button: file picker → client-side encryption →
  initiate / PUT / confirm. Thumbnail generated via canvas (images) or video seek (video).
  `PlotThumbCard` switches to `UploadThumb`; encrypted items navigate to detail page
  instead of quick modals.
- `src/pages/PhotoDetailPage.jsx` — encrypted images and videos fetched, decrypted,
  played from object URL. Plaintext path unchanged.
- `src/pages/ExplorePage.jsx`, `CompostHeapPage.jsx`, `PhotoGrid.jsx` — thumbnail
  display updated to `UploadThumb`.
- `src/test/vaultCrypto.test.js` — 14 unit tests (mirrors Android `VaultCryptoTest`).
  102 total tests pass.
- Deployed: Cloud Run revision `heirlooms-web-00036-9lm`.

---

## v0.28.1 — Post-E3 fixes: thumbnail display, rotate UI, Just arrived (9 May 2026)

Three bugs found during hands-on device testing of v0.28.0:

- **Thumbnail display.** Five call sites (Garden, Explore, Compost heap, Photo picker,
  Capsule detail) were still calling `HeirloomsImage(url = thumbUrl())` directly. For
  encrypted uploads this fetches raw ciphertext that Coil cannot decode. All five updated
  to `UploadThumbnail(upload)`, which transparently decrypts before display.

- **Rotate button.** `_stagedRotation` was a private `MutableStateFlow` not directly
  observed by the screen. Only `isDirty` (a boolean) was collected; a second rotate press
  left `isDirty = true → true` with no recomposition. Fix: expose `stagedRotation` as a
  public `StateFlow<Int?>` and collect it directly so every press triggers an immediate
  visual update.

- **Just arrived drops items on view.** The `just_arrived=true` predicate included
  `last_viewed_at IS NULL`, so opening any photo detail (which calls `trackView`) silently
  removed it from Just arrived. Fix: remove that condition. Items now leave Just arrived
  only when tagged or composted. `trackView` no longer has the IS NULL guard — every view
  updates `last_viewed_at` (useful future signal). Integration tests updated.
  Deployed: Cloud Run revision `heirlooms-server-00034-frz`.

---

## v0.28.0 — M7 E3: Android client encryption (9 May 2026)

- `VaultCrypto.kt` — pure-Kotlin crypto utilities: AES-256-GCM encrypt/decrypt, symmetric and
  asymmetric envelope builder/parser (matching server's `EnvelopeFormat`), HKDF-SHA256 (RFC 5869,
  manual implementation — no dependency), Argon2id passphrase key derivation (BouncyCastle 1.79).
- `DeviceKeyManager.kt` — Android Keystore AES-256 key wraps the master key at rest; software
  P-256 device keypair (private key wrapped by Keystore key) used for server registration.
  `setupVault()` one-time setup; `loadMasterKey()` auto-unlocks from Keystore on process restart.
- `VaultSession.kt` — singleton holding the master key in memory and a thumbnail cache
  (`ConcurrentHashMap<String, ImageBitmap>`).
- `VaultSetupScreen.kt` + `VaultSetupViewModel.kt` — first-launch vault setup: generates master
  key, registers device with server (`POST /api/keys/devices`), uploads passphrase backup
  (`PUT /api/keys/passphrase` with Argon2id-derived KEK). Shown once; guarded by
  `DeviceKeyManager.isVaultSetUp()`.
- `MainApp.kt` — vault setup gate inserted between welcome/API-key screens and main navigation;
  auto-unlock on process restart.
- `Uploader.uploadEncryptedViaSigned()` — encrypted upload path: generates content DEK +
  thumbnail DEK, AES-256-GCM encrypts file + client-generated thumbnail, wraps both DEKs under
  master key, calls `/api/content/uploads/initiate` (storage_class: "encrypted"), PUTs two
  ciphertext blobs, calls `/confirm` with full E2EE envelope fields.
- `UploadWorker.kt` — auto-unlocks vault and calls `uploadEncryptedViaSigned()` when vault is set
  up; falls back to legacy `uploadViaSigned()` if vault not yet configured.
- `HeirloomsApi.kt` — new methods: `registerDevice`, `putPassphrase`, `initiateEncryptedUpload`,
  `confirmEncryptedUpload`, `fetchBytes`; `toUpload()` decodes E2EE fields from base64.
- `Models.kt` — `Upload` gains `storageClass`, `envelopeVersion`, `wrappedDek`, `dekFormat`,
  `wrappedThumbnailDek`, `thumbnailDekFormat`; `isEncrypted` computed property.
- `HeirloomsImage.kt` — new `UploadThumbnail` composable: for encrypted uploads, fetches
  encrypted bytes, decrypts with thumbnail DEK, displays decrypted bitmap; `legacy_plaintext`
  uploads use existing Coil path unchanged. In-memory thumbnail cache via `VaultSession`.
- `PhotoDetailViewModel.kt` — `loadEncryptedContent()` fetches and decrypts full content for
  encrypted uploads; images decoded to `ImageBitmap`, videos decrypted to temp file and exposed
  as `Uri` for ExoPlayer.
- `PhotoDetailScreen.kt` — `MediaArea` updated to render from `decryptedBitmap`/
  `decryptedVideoUri` for encrypted uploads; `GardenFlavour` and `ExploreFlavour` accept and
  thread these params.
- BouncyCastle 1.79 added to `build.gradle.kts` (production + test); packaging options silence
  META-INF signature conflicts.
- 14 new `VaultCryptoTest` unit tests (JVM); 0 failures. All 92 existing tests continue to pass.
- `docs/briefs/M7_E3_brief.md` added.

---

## v0.27.0 — M7 E2: backend API for E2EE (9 May 2026)

- V15: `pending_device_links` table for the trusted-device key-wrap handshake.
- `UploadRecord` extended with 9 E2EE fields; all SELECT queries updated to include them.
- `POST /api/content/uploads/initiate` — E2EE-aware slot creation; creates two
  `pending_blobs` rows for encrypted uploads (content + thumbnail), one for legacy.
- `POST /api/content/uploads/confirm` — extended to accept encrypted path with envelope
  validation (`EnvelopeFormat.validateSymmetric`); dedup guard bypassed for encrypted.
- `POST /api/content/uploads/{id}/migrate` — atomically replaces a `legacy_plaintext`
  upload with its encrypted equivalent; deletes old plaintext blobs from GCS.
- `GET /uploads`, `GET /uploads/{id}`: `storageClass` field present on all rows;
  E2EE fields (Base64-encoded) present on encrypted rows only.
- `GET /uploads/{id}/thumb`: serves `thumbnailStorageKey` for encrypted rows.
- `KeysHandler.kt` — full `/api/keys/` contract: device CRUD, passphrase CRUD,
  4-step device-link flow (initiate → register → status poll → wrap).
- `PendingBlobsCleanupService` — pending blob TTL cleanup (every 6 h) and dormant
  device pruning >180 d (every 24 h); both jobs run at startup.
- `S3FileStore` gains `DirectUploadSupport` — presigned PUT/GET URLs via `S3Presigner`
  with path-style addressing (required for MinIO and S3 endpoint-override scenarios).
- `run-tests.sh` fixed: was calling `./gradlew jar`; changed to `./gradlew shadowJar`
  so the Docker image picks up the correct fat JAR.
- 218 unit tests pass; 9/9 E2EE integration tests pass (BouncyCastle round-trip canary
  + migration + device-link + edge cases). 8 pre-existing integration test failures
  remain (`JSONArray` tests from before the paginated list was introduced) — unchanged.
- Deployed: Cloud Run revision `heirlooms-server-00033-dqp`.

---

## v0.26.0 — M7 E1: schema foundations + envelope format (9 May 2026)

- Renamed `captured_at` → `taken_at` across schema, server, Android, and web. API JSON
  field is now `takenAt`.
- V12: renamed column; added `wrapped_keys`, `recovery_passphrase`, `pending_blobs` tables.
- V13: added `storage_class` + E2EE columns to `uploads`; existing rows backfilled as
  `legacy_plaintext`; storage-class consistency constraint added.
- V14: added `storage_class` + E2EE columns to `capsule_messages`; `body` made nullable.
- `EnvelopeFormat.kt`: server-side structural validator for encrypted envelopes; no
  production crypto dependency.
- 22 new tests (envelope format unit tests + schema canary tests).
- Fixed 6 pre-existing stale MockK matchers in `UploadHandlerTest` (`tag` → `tags`).

---

## v0.25.7 — Video player: auth + sizing (8 May 2026)

- `VideoPlayer` used ExoPlayer's default HTTP stack — no `X-Api-Key` header, so every
  request to `/api/content/uploads/{id}/file` got a 401 and nothing played. Added
  `media3-datasource-okhttp:1.4.1`; ExoPlayer now uses an `OkHttpDataSource.Factory` with
  an auth interceptor. `HeirloomsApi.apiKey` promoted from `private` to `internal` so the
  composable can read it via `LocalHeirloomsApi.current`.
- `PlayerView` had no height constraint (caller passed `fillMaxWidth`); `AndroidView` fell
  back to wrap_content and rendered a tiny strip. Fixed by adding `aspectRatio(16f/9f)` to
  the view modifier.
- Added `player.playWhenReady = true` — video now starts automatically once buffered.

---

## v0.25.6 — Just arrived: arrival animation per-tile (8 May 2026)

- `OliveBranchArrival` was a full-screen parchment overlay. Moved to play inside each newly
  arrived thumbnail tile. `GardenViewModel` now exposes `newlyArrivedIds: StateFlow<Set<String>>`
  (the IDs from `genuinelyNew` on each poll). `PlotRowSection` overlays `OliveBranchArrival`
  (clipped to tile bounds, 88% parchment background) on each matching thumbnail. `onComplete`
  clears the ID set via `clearNewlyArrived()`. Full-screen overlay removed.

---

## v0.25.5 — Just arrived: scroll-to-start + animation trigger fix (8 May 2026)

- New item at index 0 appeared off to the left: `rememberLazyListState(initialFirstVisibleItemIndex=…)`
  only applies the saved index on first creation; Compose's scroll-preservation kept old items
  in place. Fixed by adding `shouldScrollToStart: Boolean` to `PlotRowSection` and calling
  `listState.scrollToItem(0)` in a `LaunchedEffect` for the Just arrived row.
- Arrival animation was unreliable: `newItemsArrived` was a plain `var` — Compose cannot
  observe it — and the check ran during composition as a side effect. Converted to
  `StateFlow<Boolean>`, collected via `collectAsStateWithLifecycle()`, triggered from
  `LaunchedEffect(newItemsArrived)`.

---

## v0.25.4 — Share screen: video thumbnail + upload progress jump (8 May 2026)

- Share idle screen showed blank for videos: `AsyncImage` used Coil's singleton ImageLoader
  which had no `VideoFrameDecoder`. Added `coil-video:3.1.0`; `ShareActivity` now provides
  a `CompositionLocalProvider(LocalImageLoader)` containing a `VideoFrameDecoder`-capable
  loader. `IdleScreen`'s `AsyncImage` calls updated to `imageLoader = LocalImageLoader.current`.
- Upload progress screen flashed "No uploads in progress" before showing the active upload:
  `collectAsState(initial = SessionUploadState(emptyList()))` triggered `files.isEmpty()` →
  `DoneState` (and a premature `pruneFinished()`) before any jobs were enqueued. Split the
  `when` branch: `allDone` → DoneState + prune; `files.isEmpty()` → blank `Box` (brief, while
  IO copy+enqueue is in flight); otherwise → `InProgressState`.

---

## v0.25.3 — Upload progress: clear finished button + auto-prune (8 May 2026)

`workManager.pruneWork()` is now called in two places:
- **"Clear finished" button** — appears inline above the file list (right-aligned, next to the
  divider) whenever any job is in a terminal state (SUCCEEDED / FAILED / CANCELLED). Tapping
  it removes all terminal records from WorkManager's database immediately. Stuck ENQUEUED jobs
  can be cancelled individually with the per-file × button, which moves them to CANCELLED;
  "Clear finished" then removes them.
- **Auto-prune on done state** — when all active jobs finish and the screen reaches "No uploads
  in progress", `pruneWork()` fires automatically via `LaunchedEffect` so stale records don't
  accumulate and reappear in the next session's upload list.

Upload progress subtitle changed from `"N of M in progress"` to `"N uploading"`: `M` was
`files.size` which counted all historical SUCCEEDED/FAILED records, not just active jobs.

---

## v0.25.2 — Android bug fixes: login, Just arrived animation, image disk cache (8 May 2026)

Post-D4 patch.

**Bug fixes:**
- Login screen appearing when navigating Explore → photo detail. `MainApp` used `remember`
  (not `rememberSaveable`) for `apiKey`, so Activity recreation (OS process-kill + restore on
  the RAM-constrained A02s) reset it to the initialiser value. NavController back stack was
  preserved but `apiKey` reset, showing the API key screen. Fixed by switching to
  `rememberSaveable`. SharedPreferences remains the authoritative store; `rememberSaveable`
  is a safety net. `SharedPreferenceStore.putString` also changed from `apply()` to `commit()`
  to guarantee synchronous disk writes.
- Just arrived arrival animation not firing when the row was empty. The guard
  `knownJustArrivedIds.isNotEmpty() && genuinelyNew.isNotEmpty()` short-circuited when no
  items had been seen yet, preventing the animation when the very first item arrived. Removed
  the redundant `isNotEmpty()` guard — `genuinelyNew.isNotEmpty()` alone is sufficient since
  `refreshJustArrived` only runs after the initial `load()` has set `knownJustArrivedIds`.

**Performance:**
- Added Coil `DiskCache` (50 MB) and explicit `MemoryCache` (20% of heap) to the `ImageLoader`.
  Thumbnails use stable non-signed URLs, so cache keys are consistent across sessions.
  Thumbnails previously had to be re-fetched from GCS on every app restart.

---

## v0.25.1 — Upload progress screen + Garden refresh fixes (8 May 2026)

Patch on D4. All changes driven by hands-on testing of v0.25.0 on device.

**Bug fixes:**
- API response keys corrected: server returns `"items"` (not `"uploads"`) for
  paginated upload lists and composted uploads; plain arrays for `/api/plots`
  and `/api/content/uploads/tags`. All four corrected in `HeirloomsApi.kt`.
- `async {}` inside `launch {}` without `coroutineScope {}` caused a FATAL crash
  in `GardenViewModel.load()` — child exceptions were propagating past the outer
  `try/catch`. Wrapped parallel fetches in `coroutineScope {}`.
- System plot (`__just_arrived__`, `is_system_defined=true`) was being rendered
  as a second user-plot row with no tag filter, showing all uploads. Fixed by
  adding `isSystemDefined` to the `Plot` model and filtering system plots from
  the user-plot list in `GardenViewModel`.
- Garden data was stale after navigation: `LaunchedEffect(Unit)` guarded by
  `if (Loading)` meant retained ViewModels never re-fetched. Added a silent
  `refresh()` that replaces Ready state without a loading flash, called on every
  composition.
- Just arrived scroll position drifted after refresh: saved offset caused the
  row to start mid-list rather than at the newest item. Scroll position now
  resets to 0 whenever new data arrives.

**Features:**
- Upload progress screen replaces the blank "Uploading…" state in the share
  sheet. One WorkManager job per file (was one batch). Byte-level progress bar
  per file and overall %. Per-file cancel button. "Continue in background"
  dismisses the Activity while uploads keep running. Success state shows "No
  uploads in progress" with "Go to Garden" and "← Back". Burger panel shows
  "Uploads in progress" entry dynamically while any job is active.
- File copies and worker enqueuing are now async (`lifecycleScope.launch`) so
  Plant responds instantly regardless of batch size.
- Unified job list: progress screen shows all active uploads across all
  sessions, not just the current batch.
- Upload retry: 3 attempts with 30s exponential backoff before marking failed.
- Just arrived polls every 30 seconds (`refreshJustArrived()` — that row only).
  New arrivals trigger the `OliveBranchArrival` animation as an overlay.

---

## v0.25.0 — Milestone 6 D4: Android adoption (8 May 2026)

Closes Milestone 6. Android picks up the Garden/Explore restructure from D2/D3.
After D4, Android and web are surface-equivalent for browsing, filtering, and
photo detail. Capsule creation and plot management remain web-only.

**4E — ViewModel + SavedStateHandle migration:**
All seven screens get ViewModels. `android:configChanges` removed from
ShareActivity. Upload state survives rotation because the ViewModel holds the
WorkManager request ID and re-observes on recreation.

**4A — Four-tab bottom nav:**
Garden | Explore | Capsules | Burger. Burger opens `ModalBottomSheet` with
Settings and Compost heap. Compost heap removed from Garden footer and Settings.

**4C — Explore tab:**
Mirrors web `/explore`. Filter chrome collapses to "Filters" button + bottom
sheet on all phone viewports. 4-column grid, "Load more" cursor pagination.

**4B — Garden plot rows:**
Horizontal scrolling plot rows replace the 2-column grid. Just arrived fixed at
top. Interactive titles navigate to Explore with plot tags pre-applied.
Long-press thumbnail → Rotate 90° or Add tag. End-of-row "Load more" / "See
all in Explore →" tiles.

**4D — Photo detail flavours:**
Garden (action-forward), Explore (content-forward), Compost (faded + restore).
Context-aware back label. View tracking fires on every open.

**API layer:**
Upload model gains `capturedAt`, `latitude`, `longitude`, `lastViewedAt`. Plot
model added. `listUploadsPage()` with full filter/cursor support. ExoPlayer
(media3:1.4.1) for inline video in photo detail.

**Tests:**
14 new automated tests (ViewModel SavedStateHandle survival, trackView behaviour,
photo detail flavour split).

---

## v0.24.1 — D3 polish: plot UX, multi-tag filter, CORS fix (8 May 2026)

Patch on D3. All changes driven by hands-on testing of v0.24.0.

**Bug fixes:**
- Explore thumbnails were blank in production — `ExploreThumb` used relative URLs without
  `API_URL` prefix, which breaks when API and web are on different origins.
- `DELETE` and `PUT` missing from `CorsFilter` allowed methods — silently blocked plot
  deletion and plot-criteria updates from the browser (CORS preflight failure).
- Plot tag filtering returned all items regardless of tag criteria — `tags && ?::text[]`
  with JDBC `setArray` was unreliable; switched to `ARRAY[?,?]::text[]` with individual
  `setString` params, mirroring the working `@>` pattern.
- Plot `tag_criteria` saved empty when user clicked Create without pressing Enter first
  in the old `PlotTagPicker` — now moot (form replaced).

**Features:**
- Plot management redesigned: inline `PlotTagPicker/PlotForm` removed from Garden.
  Creating a plot now uses Explore's filter controls: apply a tag filter, see the results,
  click "Save as plot…" to name it. Editing navigates to `/explore?edit_plot=<id>` with
  an "Editing [name]" banner and "Update plot" / "Cancel" buttons.
- Multi-tag filter in Explore: `TagChromePicker` replaces the single text input — shows
  chips for selected tags, dropdown of all used tags (from new `GET /uploads/tags`
  endpoint), keyboard-friendly (Enter to add, Backspace to remove last).
- Plots now store and filter by multiple tags (`tag_criteria` already an array).
- Video modal in Garden: clicking a video thumbnail opens an inline player (signed URL
  streaming, with proxy fallback) instead of navigating to the detail page.
- Quick tag on Garden thumbnails: tag icon appears top-right on hover, opens a modal.
- Rotate button on Garden thumbnails: rotate icon appears top-left on hover, images only,
  optimistic update.
- Photo detail (Garden flavour): rotate button and inline tag editor added.
- `GET /api/content/uploads/tags` — new endpoint returning all distinct tags.

**Tests:** 88 web tests passing.

---

## v0.24.0 — Milestone 6 D3: Web complete (8 May 2026)

Minor bump — D3 of Milestone 6. Garden becomes plot rows, Explore gains filters, PhotoDetail
gains layout variants, view-tracking column added.

**Schema (V11):**
- `last_viewed_at TIMESTAMPTZ NULL` added to `uploads`. Partial index on `(uploaded_at DESC)`
  for the *Just arrived* predicate (untagged, unviewed, non-composted rows).

**Backend — upload list filters:**
- `GET /api/content/uploads` accepts: `tag` (comma-separated any-match), `from_date`, `to_date`
  (ISO date, inclusive), `in_capsule` (true|false), `include_composted` (true), `has_location`
  (true|false), `sort` (upload_newest|upload_oldest|taken_newest|taken_oldest), `just_arrived`
  (true — the *Just arrived* predicate).
- Cursor encoding updated to be sort-aware: format `SORT_NAME:sortKeyMs_or_null:id`.
  Legacy upload-oldest cursor format is unsupported; any mismatched cursor restarts pagination.

**Backend — view tracking:**
- `POST /api/content/uploads/:id/view` — sets `last_viewed_at = NOW()`. Idempotent. Called
  by the web photo detail page on every open.

**Backend — plot reorder:**
- `PATCH /api/plots` — batch reorder: `[{id, sort_order},...]`. Atomic. Returns 403 on system
  plot and 404 on unknown id.

**Web — Garden:**
- Redesigned as vertical stack of horizontal Netflix-style plot rows.
- *Just arrived* (system plot) fixed at top. No drag handle, no gear menu.
- User-defined plots: drag-and-drop reorder via `@dnd-kit/sortable` (desktop); gear menu with
  Move up / Move down fallback (mobile/accessibility).
- Per-plot gear menu: Edit, Delete, Move up, Move down. Edit/Add opens an inline form.
- Inline *Add a plot* form at the bottom (name + tag criteria picker).
- Delete confirmation dialog. Empty states per plot.
- Composted message toast preserved on navigation-from-compost.
- `?limit=10000` kludge retired.

**Web — Explore:**
- Filter chrome: tag (single, comma-separated for plot fetches), date range (uploaded_at),
  capsule membership, location, composted toggle.
- Sort dropdown: newest/oldest by upload date, newest/oldest by taken date.
- Items without `capturedAt` tagged *no date* when sorting by taken date.
- Composted items rendered with desaturation when `include_composted=true`.
- Responsive: collapses to a *Filters* toggle on narrow viewports.

**Web — PhotoDetail:**
- Single route `/photos/:id?from=garden|explore`. Default (no param) = Garden flavour.
- Garden flavour: action-forward. *Add to capsule* prominent. *Compost* below a visual divider
  (negative-action button separation principle in practice).
- Explore flavour: content-forward. Larger hero, metadata prominent (taken date, upload date,
  location, capsule count). Tags read-only with *Edit tags* link. Actions in kebab menu.
- Back link context-aware: *← Garden* / *← Explore* / *← Compost heap*.
- Every page open fires `POST .../view` to remove the item from *Just arrived*.

**IDIOMS.md:** Three new entries — *Plot*, *Just arrived*, *Negative-action button separation*.

**Tests:** 21 new backend (18 upload filters/sort, 3 view endpoint, 5 plot reorder, now in
`UploadFilterApiTest.kt` + `PlotApiTest.kt`); 22 new web (13 garden, 5 explore filters, 4 photo
detail). All passing: 190+ backend, 86 web.

---

## v0.23.0 — Milestone 6 D2: backend + Explore basic (8 May 2026)

Minor bump — D2 of Milestone 6. Schema foundations, cursor pagination, and /explore.

**Schema (V9 + V10):**
- V9: `exif_processed_at TIMESTAMPTZ` column on `uploads`. Partial index on pending rows. All pre-D2 rows marked done at migration time.
- V10: `plots` and `plot_tag_criteria` tables. System-defined *Just arrived* plot seeded with sentinel name `__just_arrived__` (sort_order -1000, is_system_defined = TRUE). `owner_user_id` nullable for v1 (FK + NOT NULL added at M8).

**EXIF recovery service:** `ExifExtractionService` runs on server startup, queries `WHERE exif_processed_at IS NULL`, and re-processes any stranded rows via background coroutine. Inline extraction in upload handlers sets `exif_processed_at = NOW()` at insert time.

**Cursor pagination:** `GET /api/content/uploads` and `GET /api/content/uploads/composted` now return `{"items":[...],"next_cursor":"..."|null}`. Cursor encodes `(uploadedAt, id)` as URL-safe base64. Default limit 50, max 200. Old un-paginated response format is gone; existing callers (Garden, CompostHeap) updated to use `?limit=10000` for unchanged single-page behaviour during D2.

**Plot CRUD endpoints:**
- `GET /api/plots` — list ordered by sort_order
- `POST /api/plots` — create user-defined plot
- `PUT /api/plots/:id` — update name/sort_order/tag_criteria; 403 on system plots
- `DELETE /api/plots/:id` — delete user-defined plot; 403 on system plots

**Web — Explore:**
- New `/explore` route and `ExplorePage` component. Cursor-paginated photo grid (50/page), *Load more* button, empty state.
- Top nav: Garden | Explore | Capsules (desktop + mobile drawer).
- Garden and CompostHeap updated for new paginated response format.

**Tests:** 21 new backend (8 pagination, 13 plot); 6 new web (explore); 3 compost tests updated for new response format. All passing: 169 backend, 65 web.

---

## v0.22.0 — Milestone 6 D1: re-import utility (8 May 2026)

Minor bump — first Milestone 6 deliverable. Standalone Gradle tool under `tools/reimport/`.

**New: `tools/reimport/` subproject.** A standalone Kotlin/Gradle project (separate from
HeirloomsServer) that scans the configured GCS bucket and recreates `uploads` rows for
any GCS objects that don't yet have a corresponding DB row. Serves as the M6 safety net:
schema changes in later deliverables can be rolled back in minutes by nuking the DB and
running this script.

- Paginated GCS listing — never loads the full object list into memory.
- Content-type filter — only `image/*` and `video/*` objects are imported.
- Idempotent dedup — dedup key is `storage_key`; re-runs are a no-op.
- SHA-256 hash computed during import (downloads each object); stored in `content_hash`.
- Progress logging every 50 objects for long-running scans.
- Verify phase after import: GCS↔DB count parity check + 5-object sample integrity check
  (SHA-256 round-trip or existence-only if no hash stored).
- Credentials: service account JSON (`GCS_CREDENTIALS_JSON`) or ADC fallback.
- 16 tests: 8 unit (content-type filter, sha256Hex, ImportSummary counting) +
  8 integration (Testcontainers Postgres).
- Run: `cd tools/reimport && ./gradlew run` with `DB_URL`, `DB_USER`, `DB_PASSWORD`,
  `GCS_BUCKET` set (plus optionally `GCS_CREDENTIALS_JSON`).

**`docs/PA_NOTES.md`:** New *Recovering from a database wipe* runbook section.

---

## v0.20.3 — Brand vocabulary cleanup (10 May 2026)

Patch increment. Vocabulary-only changes; no functional behaviour change.

**Dropped *didn't take* from the brand vocabulary.** Error states now use plain
operational copy (*"Couldn't save. Try again."*, *"Couldn't upload. Try again."*,
context-appropriate). Sans typography, earth colour, retry affordance — visual
treatment unchanged; only the words and typography of error copy shift.

**Replaced *Plant something for someone* with *Keep something for someone*** as
the capsule create form's brand-voice opening line (web). Uses the existing *keep*
concept from the welcome screen; no reproductive connotation. The *plant* verb is
now upload-only.

**IDIOMS.md substantially updated:** Errors section added (brand voice deliberately
absent from error states); *Keep / Keeping* entry added; *Bloom* entry rewritten
(visual moment vs technical state split); *Plant* entry updated (upload-only);
brand-discipline meta-note added (*Checking combinations, not just words*); *Known
unsettled* section added (the *open* overload, the recipients-as-categories
question); *The unit of content* section added (*items* for counts — no new brand
vocabulary noun); *Quick reference* table added (absorbed from GLOSSARY.md).

**GLOSSARY.md deleted.** Content merged into IDIOMS.md as a *Quick reference*
section (B2 path). Single source of truth; no parallel-update overhead.

**BRAND.md updated:** Removed *didn't take* verb; updated canonical strings;
updated motion language table; updated palette token descriptions.

**Tests updated** to match new error copy strings and new opening line.

Test counts unchanged: ~149 backend integration tests; ~40 web tests.

---

## v0.21.0 — Combined Android Increment 3 + Daily-Use (10 May 2026)

Minor bump — substantial new feature surface. Closes Milestone 5.

**Toolchain:**
- AGP 8.3.0 → 8.8.2; compileSdk/targetSdk 34 → 35; Kotlin 1.9.22 → 2.0.21 (with
  `kotlin.plugin.compose` replacing `kotlinCompilerExtensionVersion`); Compose BOM
  2024.01.00 → 2024.12.01; Coil 3.0.4 → 3.1.0; JVM target 11 → 17.

**New Android surface — `MainActivity`:**
- Two-activity structure: `ShareActivity` (unchanged) handles share-sheet intents;
  `MainActivity` is the new launcher icon entry point.
- Three-tab bottom nav: Garden (simplified olive-branch icon), Capsules (wax-seal olive
  icon), Settings (Material gear icon).
- Welcome screen (once per install): single brand-voice line + *Get started* button.
- API key entry screen on first launch or after Settings reset.

**Garden tab:**
- 2-column photo grid (revised from spec's 1-column; 2-col reads better on phone — see
  PROMPT_LOG.md for rationale); tag filter chips; pull-to-refresh.
- *Compost heap (N)* link below the grid; navigates to compost heap view.
- Skeleton loading state; empty state with brand-voice copy; error state with retry.

**Photo detail:**
- Active state: full-width photo, tag chips with inline add, upload date, capsule
  membership line, *Add this to a capsule* button, *Compost* button with precondition
  disabled-state messaging.
- Composted state: faded photo (saturation 0.6, alpha 0.85), countdown metadata, *Restore*.
- *Add to a capsule* picker: lists open capsules, PATCHes upload list on selection.

**Compost heap view:**
- Restore-only list; tap on row body is a no-op; *Restore* per row; pull-to-refresh.
- Empty state: randomised brand-voice line from a pool of five (stable per session).

**Capsules tab:**
- List with state filter (Active / Delivered / Cancelled / All); pull-to-refresh.
- *+ Start a capsule* CTA; delivered/cancelled state-tinted card backgrounds.
- Capsule detail (four state variants — open, sealed, delivered, cancelled); all
  read-only except state transitions.
- Cancel and Seal confirmation dialogs with recipient-aware copy.
- Sealing animation: wax-seal olive forms over ~700ms ease-out; reduced-motion respects
  `ANIMATOR_DURATION_SCALE`.
- Capsule create flow: chip recipient input, native Material 3 date picker, full-screen
  photo picker, recipient-aware message placeholder, *Start* primary + *Seal* overflow.
- Photo picker: 4-column grid, tag filter, selection overlay, result returned via
  `SavedStateHandle`.

**Settings tab:**
- Three items: API key reset (clears stored key, navigates to entry — welcome flag
  preserved per "once per install" rule), app version (`BuildConfig.VERSION_NAME`),
  Compost heap link.

**Drive-by:**
- Removed unused Tailwind tokens `bloom-25` and `earth-20` from
  `HeirloomsWeb/tailwind.config.js` and the BRAND.md derived-tokens table.

**Test counts:** Android testing remains manual; no new automated UI tests. Backend
integration test count and web test count unchanged.

---

## v0.20.2 — Coil 3.x migration prerequisite (10 May 2026)

Patch increment. Dependency upgrade only; no behaviour change.

- `HeirloomsApp/app/build.gradle.kts`: Coil dependency updated from
  `io.coil-kt:coil-compose:2.5.0` to `io.coil-kt.coil3:coil-compose:3.0.4`. Pinned at
  3.0.4 — Coil 3.1.x and later pull in JetBrains Compose 1.8.x+ which requires
  compileSdk 35 and AGP 8.6.0+; 3.0.4 (JetBrains Compose 1.7.0, AndroidX Compose 1.7.x)
  is the latest compatible with the current compileSdk 34 + AGP 8.3.0 build config.
- `IdleScreen.kt`: import updated from `coil.compose.AsyncImage` to
  `coil3.compose.AsyncImage`. No call-site changes needed — `AsyncImage`'s API
  surface (`model`, `contentDescription`, `contentScale`, `modifier`) is unchanged.
- `PA_NOTES.md`: fixed two stale `~/Downloads/Heirlooms/` path references in the
  Cloud Run deploy-commands block (now `~/IdeaProjects/Heirlooms/`); Coil-version
  gotcha updated; current-version line bumped.
- `PROMPT_LOG.md`: v0.20.2 entry added.

Test counts unchanged: ~149 backend integration tests; ~40 web tests. ShareActivity
verified manually: 1-photo, 4-photo, and 8-photo cases all render thumbnails correctly;
rotation during idle screen retains thumbnails; upload completes end-to-end.

---

## v0.20.1 — No-flash fix on compost + post-v0.20.0 documentation sweep (9 May 2026)

Patch increment. One code fix, no behaviour change beyond removing the flash.

- `PhotoDetailPage.jsx`: removed the `finally` block that reset `composting` state to
  `false` after a successful compost. The reset triggered a re-render in the
  non-composted state immediately before navigation unmounted the component, causing a
  brief visible flash of the file detail view. On success the component is about to
  unmount — resetting state is unnecessary. On failure the `catch` block still resets
  correctly. Symptom: brief white/non-composted flash visible during the navigate-to-garden
  transition after clicking *Compost*.
- `ROADMAP.md`: Increment 2 and Brand follow-up updated from "(planned)" to "(shipped)".
  Compost heap added as a non-milestone interstitial between Milestone 5 and 6. Android
  Daily-Use noted as combined with Increment 3.
- `PA_NOTES.md`: current version bumped to v0.20.1.
- `IDEAS.md`: stale "Planned for ~v0.19.0" timing on Android daily-use updated.
- `BRAND.md`: status line updated to mention compost verb addition at v0.20.0.
- `SE_NOTES.md`: project path corrected from `~/Downloads/` to `~/IdeaProjects/`.

Test counts unchanged: ~149 backend integration tests; ~40 web tests.

---

## v0.20.0 — Compost heap (soft-delete with 90-day auto-purge) (9 May 2026)

The first user-facing removal mechanism in the product. Composting is soft and
considered: a photo can only be composted if it has no tags and no active capsule
memberships. The 90-day window is the safety net. No public hard-delete endpoint
is added — the only path to true hard-delete is the system-driven lazy cleanup.

**Schema:** Flyway V8 migration adds `composted_at TIMESTAMPTZ` (nullable) to
`uploads` with a partial index on non-null values. Null = active; non-null =
composted. Purely additive — no data migration needed.

**Backend (HeirloomsServer):**
- Three new endpoints: `POST /api/content/uploads/:id/compost`,
  `POST /api/content/uploads/:id/restore`,
  `GET /api/content/uploads/composted`.
- New `GET /api/content/uploads/:id` endpoint (returns upload regardless of
  composted state — needed for heap → detail navigation).
- `GET /api/content/uploads` (active list) now filters out composted items.
  Lazy cleanup fires on every active-list call: a daemon thread hard-deletes
  items past their 90-day window (GCS object first, then DB row; retry-safe).
- `canCompost` helper wraps the precondition check (no tags, no active capsule
  memberships) in a `withTransaction` lock to prevent races.
- `UploadRecord.toJson()` extended with `compostedAt` field.

**Web UI (HeirloomsWeb):**
- *Compost* button on photo detail view: earth ghost style, disabled (with
  helper text) when tags or active capsule memberships are present.
- Successful compost navigates to Garden with a transient italic confirmation:
  *Composted. Find it in the compost heap below.*
- Composted photo detail: faded/desaturated image, countdown metadata, *Restore*
  replacing *Compost*, no other affordances.
- Garden footer: quiet *Compost heap (N)* link, shown even when count is zero.
- New `/compost` route: list view with thumbnail, upload date, composted date,
  days-remaining metadata, and inline *Restore*. Empty state randomises per
  session from a pool of five brand-voice lines (`brandStrings.js`).

**Tests:** ~16 new backend integration tests (`CompostApiTest.kt`); ~10 new
Vitest tests (`compost.test.jsx`). ~149 backend tests total; ~40 web tests total.

**Documentation:** IDEAS.md cascade-warning entry removed (resolved by compost
preconditions). PA_NOTES.md updated with two new gotchas (lazy-cleanup scaling,
hard-delete-is-system-only). BRAND.md voice section gets the *compost* verb and
a reference to the empty-state pool.

---

## v0.19.6 — Post-v0.19.5 documentation sweep (9 May 2026)

Doc-only patch. No code changes. No behaviour change. Test counts unchanged.

- `PA_NOTES.md`: current-version line bumped. Four new gotchas added: manual JSON
  serialisation in Kotlin (`triple-quoted` string quoting bug; v0.19.2/v0.19.5);
  permissive-parser integration tests hiding field-value bugs (Jackson lenient parsing;
  v0.19.2); SPA nginx `try_files` fallback (v0.19.3); post-login auth-redirect interim
  pattern (`RequireAuth` + `state.from`; v0.19.4). New "Architectural notes worth
  remembering" section: photo detail as a real route (v0.19.0); `?sealed=1` query-param
  handshake for post-action animations (v0.19.0); held-lightly typography decision
  confirmed (v0.19.0).
- `BRAND.md`: palette discipline line updated; new "Derived tokens" sub-table documenting
  `forest-75`, `bloom-15`, `bloom-25`, `earth-10`, `earth-20` with code-verified usages.
- `ROADMAP.md`: Android Daily-Use Increment version estimate changed from `~v0.19.0` to
  positional language (after Increment 3).

---

## v0.19.0 — Capsule web UI (Milestone 5, Increment 2) (9 May 2026)

Web UI for the capsule mechanic. After this increment, capsules are a real feature
on heirlooms.digital — browsing, creating, editing, sealing, cancelling, and adding
photos to capsules from the gallery.

**Navigation:**
- Top-level nav bar on all authenticated pages: Heirlooms wordmark (left), Garden and
  Capsules peer links (centre), Log out (right). Active page underlined in earth (#B5694B).
- Mobile hamburger panel (slide-in from right, ~80% wide, parchment background).
- React Router v6 added; app now uses proper client-side routing throughout.

**New routes:**
- `/capsules` — Capsules list screen
- `/capsules/new` — Create form (with optional `?include=uuid` preselection)
- `/capsules/:id` — Capsule detail view (four state variants)
- `/photos/:id` — Gallery photo detail view (replaces lightbox modal)

**Capsules list screen:**
- Card grid (3 cols desktop / 2 tablet / 1 mobile), state filter and sort dropdowns,
  soonest-unlock-first default sort. Client-side sort for `created_at` (no server change).
- Sealed cards show wax-seal olive (top-left). Delivered: bloom-tinted. Cancelled: earth-tinted.
- Brand-voice empty state ("A garden grows things to keep. / A capsule grows things to give."),
  loading skeletons (no shimmer), and error state with retry.

**Capsule detail view (four state variants):**
- Open: fully editable with inline edit affordances (message, recipients, date modal, photo
  picker modal). Seal and Cancel capsule actions.
- Sealed: ceremonial wax-seal olive at full size (~56px) beside identity block. Photo contents
  frozen (no add/remove affordance). Message and recipients editable. Cancel action.
- Delivered: bloom-tinted page background, backdrop-size olive (140px, 25% opacity), read-only.
- Cancelled: earth-tinted (CSS filter: saturate(0.6)), read-only, shows Cancelled date.
- Loading skeleton and error state (general error + 404 handling).

**Inline edit affordances:**
- Message, recipients, unlock date (opens modal with three-dropdown date picker), photos
  (opens picker modal). Auto-save on context-switch; navigation guard with Discard dialog
  for unsaved changes.
- Save failure shows "didn't take" inline. Working dots during save.
- State machine: idle | editing | saving | error per field.

**Create form (`/capsules/new`):**
- Brand-voice opening line "Plant something for someone." in italic Georgia.
- Fields: For (multi-recipient, + add another), To open on (three-dropdown), Include
  (photo picker, scrollable thumbnail strip), Message (textarea, recipient-aware placeholder).
- Both commit paths: Start capsule (open) and Seal capsule (sealed, secondary with wax-seal
  olive on button). Sealing animation triggers on resulting detail view.
- `?include=uuid` query parameter pre-populates Include field (comma-separated multi-UUID
  format supported; v1 only generates single-UUID URLs).
- Client-side validation: inline brand-voice errors beneath offending fields.

**Shared photo picker modal:**
- Used by create form (empty-initial) and capsule detail edit-photos (pre-populated).
- Tag filter chips, newest-first, corner-mark selection with bloom colour + thumbnail darken.
- Footer: live count, Cancel, Done(N).

**Photo detail page (`/photos/:id`):**
- Replaces lightbox — proper navigable route. Garden thumbnails now link to `/photos/:id`.
- "In capsules:" line with sealed-capsule wax-seal olives and links to each capsule.
- "Add this to a capsule" button opens a small modal listing open capsules (sorted
  soonest-first, sealed excluded). Already-in rows shown but disabled.
- Empty add-to-capsule state shows brand-voice copy and "Start a capsule with this" CTA
  (navigates to `/capsules/new?include=<uuid>`).
- Add success: toast with italic Georgia copy ("Added to For Sophie."), +3s auto-dismiss.
  In-capsules line updates immediately.

**Confirmation dialogs:**
- Seal: italic Georgia title, body explains frozen contents, seal button with wax-seal olive.
- Cancel: italic Georgia title, recipient name in body ("Sophie won't receive it."), earth button.
- Discard changes: plain sans title (routine guard), field-specific body copy.
- ESC and backdrop-click close all dialogs; focus management per brand spec (safer default
  focused for destructive primaries).

**Sealing animation:**
- Wax-seal olive forms in ~700ms (scale 0→1, opacity 0→1, ease-out) on both create-then-seal
  and seal-existing paths. URL query param `?sealed=1` triggers animation on detail page load.
- `prefers-reduced-motion: reduce` shows olive immediately at full state, no transition.

**New brand components:**
- `WaxSealOlive` — reusable SVG component, three render sizes (list/inline/ceremonial),
  uses `currentColor` to inherit bloom from container. From `docs/BRAND.md` reference SVG.

**Tailwind additions:**
- `forest-75`, `bloom-15`, `bloom-25`, `earth-10`, `earth-20` tokens added to config.

**Typography decision (held-lightly):**
- Sealed/delivered capsule messages render in italic Georgia per the PA brief spec.
  Reviewed at first render alongside open state (system sans); the shift reads clearly
  at message-body length. No revision needed. Recorded here per brief instructions.

**Content-type neutral copy:**
- "items" for counts, "Contents" and "Include" for fields throughout. "Photos" survives
  only on the photo detail page itself (genuinely photo-specific for now).

**Tests:**
- 48 new tests across 4 test files (3 existing + 1 new capsules.test.jsx).
  Component tests for all four capsule states, list filtering/sorting, inline edit flows
  (message/recipients/date), create form validation and submission, photo picker modal,
  add-to-capsule modal, confirmation dialogs, and the discard navigation guard.
- Total web test count: 48 (up from 8 brand animation tests).

No backend changes. All API endpoints consumed in this increment existed in v0.18.0.

---

## v0.18.2 — Capsule visual mechanic added to BRAND.md (8 May 2026)

Doc-only patch. No code changes. No behaviour change.

Closes the brand gap deferred in BRAND.md since v0.17.0. The capsule
mechanic now has a complete visual language:

- Capsule states map onto the existing forest/bloom/earth signal vocabulary:
  open=forest (in-progress), delivered=bloom (the ripened olive),
  cancelled=earth (didn't take). Sealed adds a new motif — the wax-seal
  olive — a small bloom-coloured ovoid marking committed contents.
- The bloom colour appears twice in a capsule's lifecycle: as the small
  olive at sealing (promise), and as the full ripened state at delivery
  (fulfilment). The two appearances are causally linked by design.
- Capsule message typography shifts from system serif (open, draft) to
  italic Georgia (sealed/delivered, committed brand voice).
- Two new motion states scoped: sealing (~700ms, olive forms in corner),
  delivering (~2.5s, olive grows to fill, parchment washes to bloom).
  Reserved for Milestone 6.
- New brand element: the wax-seal olive, distinct from the brand mark's
  apex olive. Reference SVG in BRAND.md.

Test counts unchanged.

---

## v0.18.1 — Documentation sweep + reverse-lookup path fix (8 May 2026)

Patch increment. No behaviour change beyond the route move.

- `PA_NOTES.md`: current version updated; seven accumulated gotchas added from
  v0.17.0/v0.17.1/v0.18.0 (Android orientation handling, FlowRow opt-in, upload-confirm
  tag contract, Coil pinning, `withTransaction` rollback pattern, `UploadRecord.toJson()`
  canonical serialisation, OpenAPI spec contract-block merge).
- `ROADMAP.md`: Milestone 5 expanded to full increment plan.
- `IDEAS.md`: Android daily-use gallery entry added.
- API: `GET /api/uploads/{id}/capsules` moved to `GET /api/content/uploads/{id}/capsules`
  for consistency with the existing upload resource path. No client uses this endpoint yet.
- Integration tests for the reverse-lookup endpoint updated to the new path.

Test counts unchanged: 135 HeirloomsServer unit tests (134 passing, 1 skipped);
49 HeirloomsTest integration tests.

---

## v0.18.0 — Capsules: schema and backend API (8 May 2026)

Milestone 5, Increment 1. Backend-only — no web UI, no Android changes.

- Flyway V7 migration: `capsules`, `capsule_contents`, `capsule_recipients`,
  `capsule_messages` tables with indexes and `ON DELETE CASCADE` constraints.
- Seven HTTP endpoints:
  - `POST /api/capsules` — create
  - `GET /api/capsules` — list (state filter, order by updated_at or unlock_at)
  - `GET /api/capsules/{id}` — read (full detail: uploads, recipients, current message)
  - `PATCH /api/capsules/{id}` — update editable fields
  - `POST /api/capsules/{id}/seal` — seal an open capsule
  - `POST /api/capsules/{id}/cancel` — cancel a capsule
  - `GET /api/uploads/{id}/capsules` — reverse lookup (moved to `/api/content/uploads/{id}/capsules` in v0.18.1)
- Capsule state machine: `open → sealed/delivered/cancelled`; sealed capsules block
  content edits but allow message, recipients, and unlock_at changes.
- Message versioning: each edit inserts a new `capsule_messages` row; identical body
  is a no-op. API always returns the current (highest-version) message.
- OpenAPI spec merged: `GET /docs/api.json` now includes both content and capsule routes.
- 49 new integration tests in HeirloomsTest covering all flows and rejection paths.
- HeirloomsServer unit test count: 135 passing, 1 skipped (FFmpeg thumbnail).

Web UI (increment 2) and Android (increment 3) follow in later sessions.

---

## v0.17.1 — Share-sheet Idle state: photo previews and tag input (8 May 2026)

Closes the share-flow gap from v0.17.0. The share-sheet receive screen now has a
pre-upload *Idle* state where users see what they're about to share and can
optionally tag it before planting.

- `ReceiveState.Idle` data class added as the new entry point for `ShareActivity`.
  The activity now lands here (instead of jumping straight to *Uploading*) after
  parsing the share intent.
- `IdleScreen` Composable (`ui/share/IdleScreen.kt`): parchment background,
  *Heirlooms* wordmark header, photo grid for 1–6 photos (1-, 2-, or 3-column
  depending on count) or a horizontally-scrolling thumbnail strip + count label
  for 7+, kebab-case tag input, recent-tag chips, forest *plant* pill button,
  ghost *cancel* button.
- Tag validation (`ui/share/TagValidation.kt`): mirrors the server-side regex
  `^[a-z0-9]+(-[a-z0-9]+)*$`, length 1–50. Invalid input shows earth-coloured
  underline and inline italic message; no warning glyphs.
- Recent-tag persistence (`ui/share/RecentTagsStore.kt`): SharedPreferences store,
  last 12 tags used, newest-first, deduplicated, updated only on successful upload.
- Tags wired through to the upload request: `UploadWorker` passes the tag list to
  `Uploader.uploadViaSigned`, which includes `tags` in the confirm body sent to
  `POST /api/content/uploads/confirm`.
- Server: `confirmUploadHandler` now accepts an optional `tags` array in the
  confirm body, validates them, and calls `database.updateTags` if non-empty.
- 6 new tests: 4 `IdleScreenTest` Compose UI tests + 2 `TagValidationTest` unit
  tests. Total: 113 tests.

---

## v0.17.0 — Brand foundation, web animations, Android brand (7 May 2026)

The full Heirlooms brand milestone. Five commits, single tag. Parchment/forest/bloom/earth
palette, olive branch identity, and garden voice applied across HeirloomsWeb and HeirloomsApp.

### Summary across all five brand commits

**1. Web brand foundation (Increment 1)**
- `docs/BRAND.md` — canonical brand reference: palette, identity system, typography, voice, motion.
- Design tokens in `tailwind.config.js` and `src/index.css` (`--hl-*` custom properties).
- SVG brand components: `OliveBranchMark`, `OliveBranchIcon`, `WorkingDots`, `EmptyGarden`.
- Site header: olive branch icon + italic Georgia wordmark; parchment background.
- Tag chips: `bg-forest-08 text-forest rounded-chip`.
- Empty gallery: garden voice copy ("A garden begins with a single seed.").
- Three-colour signal discipline: all `text-red-500` → `text-earth font-serif italic`.
  No green checkmarks, red X-marks, or warning icons remain in the web codebase.
- Browser tab title → "Heirlooms — your garden".
- `vitest` + `@testing-library/react` frontend test infrastructure added.

**2. Favicon and browser-tab brand assets (post-Increment-1 fix)**
- `public/favicon.svg` — forest rounded-square + parchment olive branch, no fixed width/height.
- `public/apple-touch-icon.png` — 180×180, generated from the favicon SVG.
- `index.html` — favicon link, apple-touch-icon link, `theme-color` meta (#3F4F33 forest).

**3. Web arrival and didn't-take animations (Increment 2)**
- `src/brand/animations.js` — `lerp`, `interpolateHexColour`, `prefersReducedMotion` helpers.
- `src/brand/OliveBranchArrival.jsx` — 3s rAF, six phases: branch draws, three leaf pairs emerge
  base-to-tip, olive forms in forest, ripens to bloom, optional wordmark settles.
- `src/brand/OliveBranchDidntTake.jsx` — 2s rAF: partial branch, one leaf pair, pause, earth
  seed, "didn't take" text. Both honour `prefers-reduced-motion`.
- Gallery tile state machine: `loading → arriving → arrived` / `loading → error-animating → failed`.
  `seenIdsRef` ensures animations only play for newly-seen uploads (auto-refresh arrivals).
- `FailedTile` with *try again* / *dismiss* controls.

**4. Android static brand (Increment 3a)**
- App icon: adaptive (`mipmap-anydpi-v26/`, VectorDrawable foreground + forest background,
  `<monochrome>` for Android 13+ themed icons), legacy PNGs at all five densities, round variants,
  512×512 Play Store icon. `@android:drawable/ic_menu_upload` removed from manifest.
- `res/values/colors.xml` — full palette + tints + text shades.
- Compose theme: `Color.kt`, `Type.kt`, `Theme.kt` (`HeirloomsTheme { }`).
  Compose BOM 2024.01.00, Compiler 1.5.8, JVM 11.
- `WorkingDots.kt` — Compose three-dot pulse; uses `rememberReducedMotion()`.
- `strings.xml` — garden voice strings (upload_success, upload_failed, share_save_button, etc.).
- `UploadWorker` — notifications use brand strings; `ic_launcher_foreground` as small icon.

**5. Android arrival and didn't-take animations (Increment 3b)**
- `AccessibilityHelpers.kt` — `rememberReducedMotion()` reading `ANIMATOR_DURATION_SCALE`.
  `WorkingDots` refactored to call it.
- `OliveBranchArrival.kt` — Compose, 3s `Animatable<Float>` driven by `LaunchedEffect`,
  Canvas render via `PathMeasure` (branch), `withTransform` (leaf scale+rotate), `lerp` (olive
  ripening). `LinearEasing` required — phase ranges assume constant-rate progress.
- `OliveBranchDidntTake.kt` — Compose, 2s, same pattern. Shares internal helpers with Arrival.
- `ShareActivity` rewritten as `ComponentActivity` with `setContent { HeirloomsTheme { ... } }`.
  State machine: `Idle → Uploading → Arriving → Arrived` / `→ FailedAnimating → Failed`.
  Arrival and failure transitions driven by `onComplete` callbacks, not fixed timers.
  `observeWorkToCompletion()` uses `suspendCancellableCoroutine` + `observeForever` to await
  the WorkManager terminal state without pulling in `lifecycle-livedata-ktx` explicitly.
  Arrived state: "Something has been planted." + photo count + *view garden* / *done*.
  Failed state: "didn't take" earth + *try again* / *dismiss*.
- `styles.xml` — `Theme.Heirlooms.Share` (parchment windowBackground, no action bar).
- 5 Compose instrumentation tests in `androidTest/` (require device/emulator): arrival renders
  wordmark, omits wordmark when asked, fires onComplete under reduced motion; didn't-take renders
  text, fires onComplete under reduced motion.

**Test totals:** 148 total (135 Kotlin + 8 web vitest + 5 Android instrumented), 147 passing,
1 skipped (FFmpeg thumbnail — Docker only). Android instrumentation tests require a connected
device or emulator; not included in local `./gradlew test` run.

---

## v0.16.1 — Bug fixes (7 May 2026)

**Android: video upload OOM fix**
- `UploadWorker` was calling `file.readBytes()` before uploading, loading the entire video into the Java heap. On the Samsung Galaxy A02s (201 MB heap limit) this left no room for OkHttp's Okio buffers, causing `OutOfMemoryError` mid-upload. The error is a `java.lang.Error`, so `catch (_: Exception)` silently swallowed it — no failure notification appeared and no GCS object was created.
- Fix: new `Uploader.uploadViaSigned(File, ...)` overload streams the video from disk to GCS directly using `file.asRequestBody()`, never loading it into memory. SHA-256 is computed by streaming the file in 8 KB chunks. `UploadWorker` passes the `File` object directly, removing `readBytes()` entirely.

**Web: tag dropdown stays open after selection**
- After clicking a tag in the dropdown, the list was closing and required clicking away and back to reopen it.
- Root cause: `onBlur` was firing on the input and closing the dropdown. `e.preventDefault()` on the suggestion button's `mousedown` is not reliable across browsers (Safari on macOS does not focus buttons on click). `e.relatedTarget` was therefore null and could not be used to detect intra-dropdown focus movement.
- Fix: `suppressBlurRef` — the dropdown container's `onMouseDown` sets a ref flag before `onBlur` fires; `onBlur` skips the close while the flag is set; `onMouseUp` resets it. Browser-agnostic.

No new tests. The Android OOM fix is a memory-pressure scenario that requires a real device with a constrained heap to reproduce — not unit-testable. HeirloomsWeb has no frontend test runner; adding one just for this fix is disproportionate. 135 tests total, 134 passing, 1 skipped (FFmpeg video thumbnail — runs in Docker).

---

## v0.16.0 — Tags (6 May 2026)

Milestone 4 complete (tag support). Three increments delivered together:

**Schema and write API (Increment 1):**
- Flyway V6 migration: `tags TEXT[] NOT NULL DEFAULT '{}'` with GIN index on the uploads table; existing rows get the empty-array default automatically
- `TagValidator` enforces kebab-case (`^[a-z0-9]+(-[a-z0-9]+)*$`), length 1–50; rejects rather than coerces (`My-Children` → 400 with the offending tag named in the response body, not silent normalisation)
- `PATCH /api/content/uploads/{id}/tags` — full-replace semantics; 400 on invalid tag (offending tag + reason in body); 404 if upload not found; 200 with the updated upload record on success
- `tags` field always present in all upload JSON responses (`POST /upload`, `GET /uploads`); empty array default for pre-migration rows

**Read API and filtering (Increment 2):**
- `GET /api/content/uploads` extended with `?tag=X` and `?exclude_tag=Y` query params; backed by the V6 GIN index via `tags @> ARRAY[?]::text[]`
- OpenAPI body schemas added to both PATCH endpoints via `receiving(lens to example)` so Swagger UI shows a populated request body; `RotationRequest` and `TagsRequest` data classes must be non-`private` for Jackson's schema generator — `private` causes `IllegalAccessException` at the spec endpoint at runtime (not caught by unit tests); fixed and a spec-endpoint test added as a permanent regression guard

**Web UI (Increment 3):**
- Tag chips on each gallery card below the metadata row; hidden when no tags present
- Inline tag editor per card: tag icon in the card header opens the editor; existing chips show with × to remove; text input with autocomplete dropdown of all tags used across the gallery (derived client-side from the uploads list, filtered as you type); pending input text is flushed into the tag list on Save so pressing Enter before clicking Save is not required
- Tags updated in gallery state from the server response after a successful PATCH
- Exclude-filter is live on the API but not yet surfaced in the web UI

135 tests total, 134 passing, 1 skipped (FFmpeg video thumbnail — runs in Docker).

---

## v0.15.0 — Image rotation (6 May 2026)

- `PATCH /api/content/uploads/{id}/rotation` — accepts `{"rotation":0|90|180|270}` to set display rotation for an image; persisted in the database
- Flyway V5 migration: `rotation INT NOT NULL DEFAULT 0` on the uploads table
- HeirloomsWeb: ↻ rotate button on each image card; optimistic update on click; CSS `transform: rotate()` applied to both thumbnail and lightbox; lightbox swaps `max-w`/`max-h` constraints at 90°/270° so rotated images fill the viewport correctly
- 5 new tests; 107 total, 106 passing, 1 skipped

---

## v0.14.0 — Metadata extraction + Android overhaul (6 May 2026)

**EXIF and video metadata extraction:**
- `MetadataExtractor.kt` — extracts GPS (lat/lon/alt), capture timestamp, and device make/model from images via `com.drewnoakes:metadata-extractor`; extracts creation time and Apple QuickTime location/device tags from videos via `ffprobe` JSON output
- Flyway V4 migration: 6 nullable metadata columns on uploads table (`captured_at`, `latitude`, `longitude`, `altitude`, `device_make`, `device_model`)
- `GcsFileStore.getFirst()` — streams only the first 64 KB from GCS for image metadata extraction (JPEG EXIF is always within this range), avoiding loading full files into memory
- Cloud Run memory increased to 2 Gi to accommodate thumbnail generation for large images (4160×3120 ≈ 52 MB raw in BufferedImage)
- HeirloomsWeb: 📍 pin icon on gallery cards that have GPS coordinates; tooltip shows decimal coordinates on hover

**Android app fixes and improvements:**
- `ACCESS_MEDIA_LOCATION` permission added; `MediaStore.setRequireOriginal()` called on Android 10+ so shared photos include GPS EXIF (with graceful fallback)
- Wildcard MIME type fix: Samsung Gallery shares with `intent.type = "image/*"`; app now falls back to `ContentResolver.getType()` to get the specific type, fixing `.bin` storage key and missing metadata
- Silent upload failure fix: `readBytes()` threw `SecurityException` (not caught by `catch (e: IOException)`); changed to `catch (e: Exception)` so the upload always falls back rather than silently dying
- SHA-256 hash now sent in confirm request body, enabling deduplication for signed-URL uploads
- Upload progress notification: `ProgressRequestBody` reports byte-level progress during GCS PUT; `IMPORTANCE_LOW` progress channel shows silent progress bar; `IMPORTANCE_HIGH` result channel pops heads-up banner on completion
- App renamed from "My Heirlooms" to "Heirlooms"

---

## v0.11.0 — SHA-256 duplicate detection (6 May 2026)

Prevents the same file being stored twice, on both upload paths.

- **Direct upload** (`POST /upload`) — server computes SHA-256 of the incoming
  bytes; if a matching hash is found in the database, returns `409 Conflict` with
  `{"storageKey":"..."}` pointing to the existing copy; nothing is stored
- **Signed URL flow** (`POST /uploads/confirm`) — client sends the hash in the
  request body as `contentHash`; same 409 logic applies; the file will already
  have been PUT to GCS but no metadata record is created
- Flyway V2 migration: `content_hash VARCHAR(64)` column + partial index;
  existing rows backfilled with random placeholders (no false positives)

---

## v0.10.0 — Custom domain live + full Swagger coverage (6 May 2026)

- **Custom domains live** — `https://heirlooms.digital` and
  `https://api.heirlooms.digital` confirmed working; GoDaddy DNS A records
  cleaned up (WebsiteBuilder parking record removed), Google SSL cert issued
- **Android app hardcoded to `api.heirlooms.digital`** — endpoint URL no
  longer user-configurable; settings screen reduced to API key only; app
  always targets the production domain
- **Full Swagger coverage** — `GET /uploads/{id}/file` and
  `GET /uploads/{id}/url` were previously invisible to the OpenAPI contract;
  converted to typed `ContractRoute` entries using `import org.http4k.contract.div`
  so all six endpoints now appear in Swagger UI at `api.heirlooms.digital/docs`

---

## v0.9.0 — Video player + streaming (6 May 2026)

- **Video player** — clicking a video card in HeirloomsWeb opens a native
  `<video controls>` modal; a distinct video icon with "Click to play" label
  replaces the generic file icon for video files
- **Streaming via signed read URLs** — new `GET /api/content/uploads/{id}/url`
  endpoint generates a 1-hour signed GCS read URL; the web app sets this directly
  as `<video src>` so the browser handles streaming, buffering, and seeking
  natively via GCS range requests — no full download required
- **Dockerfile simplified** — JAR now built locally with `./gradlew shadowJar`
  before `docker build`; eliminates Gradle distribution downloads inside the
  container that were causing Docker Desktop connection drops on macOS; build
  time drops from ~3 minutes to ~2 seconds
- Validated end-to-end: video streaming confirmed working

---

## v0.8.0 — Large file upload via GCS signed URLs (6 May 2026)

Fixes the 34 MB+ video upload failure (Cloud Run enforces a hard 32 MB request
body limit that cannot be configured away).

Three-step upload flow in HeirloomsApp:
1. `POST /api/content/uploads/prepare` → server returns a signed GCS PUT URL (15-min expiry)
2. Mobile app PUTs file bytes **directly to GCS** — Cloud Run is bypassed entirely
3. `POST /api/content/uploads/confirm` → server records metadata in the database

- `DirectUploadSupport` interface + `PreparedUpload` data class added to HeirloomsServer
- `GcsFileStore` implements `DirectUploadSupport`; uses `ServiceAccountCredentials`
  (already in `GCS_CREDENTIALS_JSON`) for V4 URL signing — no new secrets needed
- Prepare endpoint returns 501 for non-GCS backends (local, S3)
- `Uploader.uploadViaSigned()` added to HeirloomsApp; `ShareActivity` now always
  uses this path; OkHttp write timeout raised to 300s
- Validated end-to-end: 34.57 MB video uploaded successfully

---

## v0.7.0 — Web gallery UI (6 May 2026)

Milestone 4 complete. Adds HeirloomsWeb and a file retrieval endpoint.

- `GET /api/content/uploads/{id}/file` — new HeirloomsServer endpoint; looks up
  the upload record by UUID, fetches the file from GCS, streams it back with the
  correct `Content-Type`; returns 404 if not found
- `FileStore.get(key)` — new method on the interface, implemented in
  `LocalFileStore`, `S3FileStore`, and `GcsFileStore`
- `uploadedAt` added to `UploadRecord` and the list endpoint JSON response
- CORS support added to HeirloomsServer (all origins; tighten when domain is live);
  OPTIONS preflight answered before `ApiKeyFilter` runs
- `HeirloomsWeb/` — new React 18 + Tailwind CSS + Vite sub-project; gallery grid
  with image thumbnails (blob URL fetch so `X-Api-Key` header can be sent), file
  icons for non-images, upload date, MIME type, file size; lightbox on click;
  in-session API key login (React state only — cleared on every page reload);
  multi-stage Dockerfile (Node 22 → nginx:alpine)

---

## v0.6.0 — Swagger UI + API key auth in docs (5 May 2026)

- Swagger UI integrated and validated on Cloud Run
- API key auth surfaced in Swagger UI (`X-Api-Key` header scheme)
- `persistAuthorization: true` so the key survives page refresh in the docs UI

---

## v0.5.0 — GCS backend + Cloud Run deployment (2–4 May 2026)

- `GcsFileStore` — Google Cloud Storage backend; credentials from
  `GCS_CREDENTIALS_JSON` env var (never written to disk)
- Cloud SQL PostgreSQL socket factory for Cloud Run → Cloud SQL connectivity
- API key authentication added to HeirloomsServer (`ApiKeyFilter`) and
  HeirloomsApp (settings screen + `X-Api-Key` header on uploads)
- Full GCP infrastructure provisioned: Cloud Run, Cloud SQL, GCS bucket,
  Artifact Registry, service account with least-privilege IAM, secrets in
  Secret Manager

---

## v0.4.0 — Self-hosted deployment (1 May 2026)

Milestone 3 complete. Adds a `deploy/` folder so the full stack can be run on a
home server or cheap VPS with a single command.

- `deploy/docker-compose.yml` — production compose with named volumes, host port
  binding (8080:8080), restart policies, and a `build:` directive that compiles
  HeirloomsServer from source
- `deploy/.env.example` — credential template; real `.env` is gitignored
- `deploy/README.md` — step-by-step setup guide for a VPS or home server

Key differences from the test compose: credentials from `.env` (not hardcoded),
named volumes so data survives restarts, `restart: unless-stopped` on all services.

---

## v0.3.0 — Package rename + docs (30 April 2026)

Administrative release following domain registration (`heirlooms.digital`).

- Renamed package `com.heirloom` → `digital.heirlooms` across all 22 Kotlin source
  files in HeirloomsApp, HeirloomsServer, and HeirloomsTest
- Added `TEAM.md`, `PA_NOTES.md`, `SE_NOTES.md`, `PROMPT_LOG.md`, `ROADMAP.md`
- Reorganised all docs into `docs/`; updated `README.md` with a doc index

---

## v0.2.0 — Backend server + integration tests (30 April 2026)

Milestone 2 complete. Adds HeirloomsServer and HeirloomsTest.

**HeirloomsServer** — Kotlin/http4k server with three endpoints:
- `POST /api/content/upload` — receives file bytes, stores to MinIO, records
  metadata in PostgreSQL, returns storage key
- `GET /api/content/uploads` — returns JSON array of all uploads
- `GET /health` — liveness check

Stack: http4k + Netty, PostgreSQL + HikariCP + Flyway, AWS SDK v2 S3 client
(with `forcePathStyle` for MinIO compatibility). Runs in Docker via a multi-stage
Dockerfile using Eclipse Temurin 21.

**HeirloomsTest** — Gradle integration test project using Testcontainers 2.x and
OkHttp. Spins up the full Docker Compose stack (postgres, minio, minio-init,
heirloom-server) and runs 10 tests across two test classes: upload API tests and
end-to-end journey tests. All tests passing.

---

## v0.1.0 — Android share-target app (30 April 2026)

Milestone 1 complete. Adds HeirloomsApp.

An Android app (Kotlin) that registers as a share target for `image/*` and
`video/*`. When a photo or video is shared to Heirlooms, it HTTP POSTs the file
to a configurable endpoint with the correct `Content-Type`. A settings screen
(accessible from the share sheet) lets the endpoint URL be changed without
editing config files. Endpoint stored in `SharedPreferences`. Sideloaded in
developer mode — no app store required.

Originally written in Java; rewritten in Kotlin with the settings screen added
in the same session.
