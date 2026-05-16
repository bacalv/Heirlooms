# TST-009 — Android Device Farm Design
## 3-Device Automated Test Infrastructure

**Author:** TestManager  
**Date:** 2026-05-16  
**Status:** Approved for implementation (follow-on: TST-011)  
**Depends on:** TST-006 (Android remote-control investigation — still queued; this design is independent but TST-011 setup steps should be re-evaluated once TST-006 concludes in case findings materially change the tooling recommendation)

---

## 1. Tool Recommendation

### Decision: Dual-layer strategy — Espresso for crypto paths, Maestro for journey tests

A single tool is not optimal for Heirlooms because the test landscape splits cleanly into two categories with different requirements:

| Layer | Tool | Justification |
|-------|------|---------------|
| **Crypto / unit / integration** | Espresso + AndroidJUnit4 (in-process) | White-box access to `CryptoService`, `VaultKeyManager`, `EnvelopeFormat`. Can assert on wrapped DEK bytes, master key derivation outputs, and Keystore slot contents without network. Fast (seconds per suite). |
| **Journey / E2E / regression** | Maestro | YAML-based flows, zero app instrumentation. Runs over ADB on physical devices. Covers the full stack: Android app → test API → GCS. Tests are readable by non-engineers and fast to write. No Appium server overhead. |

### Why not Appium?

Appium is the mature cross-platform choice but carries significant setup cost: a running Appium server, WebDriver session management, capability matrices per device, and slower test execution. Given that Heirlooms already has a Playwright web suite (TST-005) using its own runner, adding Appium would introduce a third test server process with no shared infrastructure. Maestro's ADB-native model fits a 3-device local farm with far less operational overhead.

### Why not Espresso-only (black-box journeys)?

Espresso requires a test APK compiled against the app under test. Black-box journey tests written in Espresso must be maintained alongside the app code and built per flavor, adding CI complexity. For journey-level tests (multi-user, network, staging approval flows), Maestro's external-process model is more appropriate — it does not break when internal class names change.

### Why not Maestro-only?

Maestro cannot introspect cryptographic state. It can verify that an upload appears in the garden, but it cannot assert that the DEK was correctly wrapped, the envelope version byte is correct, or that the Keystore slot was written rather than SharedPreferences. The highest-value tests for Heirlooms (vault unlock, plot-key unwrap) require white-box access. Espresso provides this.

---

## 2. Device Setup

### 2.1 Recommended Android versions

Three physical devices should cover:

| Device | Role | Minimum target Android |
|--------|------|----------------------|
| **Device A** — "farm-minapi" | Minimum supported API | Android 9 (API 28) — Heirlooms minimum (EncryptedSharedPreferences requires API 23; Keystore ECDH requires API 31, but the app should degrade gracefully below) |
| **Device B** — "farm-current" | Current mainstream | Android 14 (API 34) — dominant installed base 2026 |
| **Device C** — "farm-latest" | Bleeding edge / regression | Android 15 (API 35) — catch regressions before users see them |

If the physical devices are already fixed, map them to these roles based on the Android version they run; the naming convention is what matters for test addressing.

### 2.2 Device naming convention

Each device is addressed by a stable ADB serial or TCP address. Define a `.env` file (not committed) with aliases:

```
FARM_DEVICE_MINAPI=<adb_serial_or_ip:port>
FARM_DEVICE_CURRENT=<adb_serial_or_ip:port>
FARM_DEVICE_LATEST=<adb_serial_or_ip:port>
```

Test scripts and Maestro flows reference devices via these environment variables, not hardcoded serials. This means swapping a device requires only updating `.env`.

### 2.3 ADB connection: USB hub vs WiFi ADB

**Recommendation: USB hub for primary connections, WiFi ADB as fallback.**

- A powered USB hub attached to the development machine gives the most reliable ADB connection (no IP changes, no reconnect after screen lock, no UDP packet loss during upload tests).
- WiFi ADB (`adb connect <device_ip>:5555`) is useful when the physical machine is not co-located with the devices, but adds a dependency on local network stability that can introduce flaky tests during upload/download E2E flows.
- Connect all three devices to a single powered USB hub. Label the hub ports to match the device roles. Run `adb devices` to confirm all three appear with `device` status before any test run.

### 2.4 Prod vs staging flavor testing

Heirlooms has two Android flavors:

- `prod` — targets `api.heirlooms.digital`; production data; burnt-orange-free icon
- `staging` — targets `test.api.heirlooms.digital`; burnt-orange icon; app ID `digital.heirlooms.app.test`

**Both flavors can be installed simultaneously** because their app IDs differ. The farm should:

1. Install the `staging` APK on all three devices for automated test runs (test API is throw-away data).
2. Keep the `prod` APK installed on Device B ("farm-current") only, for smoke tests that specifically verify production-flavor behavior (e.g., invite URL domain, UploadWorker URL — the class of bug that caused BUG-003 and BUG-008).
3. Never run automated tests against production data. All Maestro flows and Espresso integration tests must target the staging flavor and test API exclusively.

### 2.5 Reset / teardown between test runs

Each test run should start from a known clean state:

```bash
# Clear app data for staging flavor (does not uninstall)
adb -s $FARM_DEVICE_CURRENT shell pm clear digital.heirlooms.app.test

# Repeat for other devices as needed
adb -s $FARM_DEVICE_MINAPI shell pm clear digital.heirlooms.app.test
adb -s $FARM_DEVICE_LATEST shell pm clear digital.heirlooms.app.test
```

This clears SharedPreferences, EncryptedSharedPreferences, Keystore entries (bounded to the app), and cached files. After clearing, the app presents the registration screen — test accounts are seeded fresh via API calls in the test setup phase (see Section 3).

**Do not uninstall and reinstall on every run.** APK installation takes 30–60 seconds per device; clearing app data takes under 2 seconds and achieves the same clean-state guarantee for the test scenarios Heirlooms needs.

---

## 3. Test Account Management

### 3.1 Test environment

All automated tests run against the test environment only:

- API: `https://test.api.heirlooms.digital`
- The test environment is throw-away — accounts, uploads, and plot memberships created during test runs are disposable.
- The GCS bucket is currently shared with production (known gap — OPS-003). Until OPS-003 ships, test uploads land in the same GCS bucket as production. Tests must not delete blobs (the teardown only clears the DB rows via the API, which leaves GCS orphans). This is acceptable for now given the test API's throw-away nature.

### 3.2 Provisioning fresh accounts per run

Each test run provisions its own accounts via the test API before launching any Maestro flow or Espresso instrumented test. A shell helper (`scripts/test-farm/provision-accounts.sh`) does the following:

1. Fetch the test API key from GCP Secret Manager:
   ```bash
   API_KEY=$(gcloud secrets versions access latest \
     --secret=heirlooms-test-api-key \
     --project heirlooms-495416)
   ```
2. Create a unique invite token for the run:
   ```bash
   TOKEN=$(curl -s -X POST \
     -H "X-API-Key: $API_KEY" \
     https://test.api.heirlooms.digital/api/auth/invites \
     | jq -r '.token')
   ```
3. Register User A (the primary actor):
   ```bash
   curl -s -X POST \
     -H "Content-Type: application/json" \
     -d "{\"username\":\"farm-a-$RUN_ID\",\"password\":\"...\",\"token\":\"$TOKEN\",...}" \
     https://test.api.heirlooms.digital/api/auth/register
   ```
4. Create a second invite for User B (the secondary actor):
   ```bash
   TOKEN_B=$(curl -s -X POST \
     -H "X-API-Key: $API_KEY" \
     https://test.api.heirlooms.digital/api/auth/invites \
     | jq -r '.token')
   ```
5. Register User B.
6. Export `USER_A_USERNAME`, `USER_A_PASSWORD`, `USER_B_USERNAME`, `USER_B_PASSWORD` for use by Maestro flows via `--env` flags.

`RUN_ID` is a timestamp or short random string (`$(date +%s)`) that makes account names unique across concurrent runs. It is never committed.

### 3.3 Secrets management

- The test API key lives in GCP Secret Manager (`heirlooms-test-api-key`). It is never committed to the repo.
- The `provision-accounts.sh` script fetches it at runtime via `gcloud`. The local machine running the farm must be authenticated (`gcloud auth login` / ADC).
- Generated passwords for test accounts are random, printed to stdout once, captured in a run-scoped env file (`.farm-env`, gitignored), and discarded after the run.
- No secrets appear in Maestro YAML files. Credentials are injected via `maestro test --env USER_A_PASSWORD=$USER_A_PASSWORD`.
- `.farm-env` and `.env` (device serials) are both in `.gitignore`.

---

## 4. Test Scenario Mapping

### 4.1 Journeys from TST-003 and TST-007

The table below maps each manual journey to its automation status and recommended tool.

| Journey | Description | Automatable now? | Tool | Notes |
|---------|-------------|-----------------|------|-------|
| **J1** | Upload and view a photo (thumbnail rotation, detail view) | Yes — partial | Maestro | Upload via ADB `input tap` + share sheet or direct upload intent. Verify photo appears in Just Arrived. Full EXIF rotation assertion needs Espresso (pixel-level check) or a visual diff. |
| **J2** | Invite flow — register second user | Yes | Maestro + API | User B registration is API-driven (provision-accounts.sh); Maestro verifies login and empty garden. |
| **J3** | Friend connection | Yes | Maestro + API | Friendship created at invite redemption — verified via API assertion in provisioning. Maestro confirms Friends screen shows User B. |
| **J4** | Shared plot + staging approval | Yes — high value | Maestro | Multi-device flow: User A creates plot on Device A, User B joins on Device B, User A approves staged item. Requires two Maestro sessions coordinated by a shell wrapper. |
| **J5** | Member trellis creation for shared plot | Yes | Maestro | User B (member) creates trellis targeting shared plot. Owner approval. |
| **J9** | Android session token security (SEC-007 retest) | Yes — partial | adb + Espresso | `adb shell run-as digital.heirlooms.app.test` to verify no plaintext token in SharedPreferences. Full Keystore assertion requires Espresso white-box test. |
| **J12** | Android flavor smoke test | Yes | Maestro | Launch staging flavor, verify vault unlock screen, Garden loads, Trellises screen labels say "Trellis". |

### 4.2 Journeys deferred to future work

| Journey | Reason for deferral |
|---------|-------------------|
| Vault unlock (biometric) | Biometric prompt cannot be automated on physical devices without a Keystore test key provisioned via `adb shell`; requires instrumentation-level workaround. Design deferred to TST-006 investigation. |
| Camera capture | `ACTION_IMAGE_CAPTURE` intent launches a system camera app; Maestro cannot interact with third-party activities. Use pre-loaded test images via `adb push` instead. |
| Share sheet upload (native share) | Android share sheet is a system UI — Maestro can tap into it but behavior varies by Android version and launcher. Use direct upload intent as the primary automation path; share sheet as a secondary manual regression test. |
| iOS regression (Journey 11) | Not in scope for Android farm. See SEC-013 / potential future iOS automation task. |
| Web-specific journeys (J6–J8, J10) | Covered by Playwright suite (TST-005). Do not duplicate. |

### 4.3 Recommended first automation target

**Journey 1 (Upload + Garden) + Journey 12 (Flavor smoke)** as a combined smoke suite. Rationale:

- Journey 1 is the highest-frequency regression area (BUG-003, BUG-004, BUG-005, BUG-013 all touched this flow).
- Journey 12 is the cheapest to write and gives signal that the app launches and core navigation works before running longer flows.
- Together they form a 5–10 minute "green light" check that can gate every pull request merge.

---

## 5. CI Integration

### 5.1 Execution model

Physical devices cannot be attached to a remote CI runner (GitHub Actions, Cloud Build). Two options:

**Option A — Local runner (recommended for now):** A Mac Mini or the development machine acts as a self-hosted GitHub Actions runner with the USB hub attached. Maestro and ADB run locally. Triggered on-demand or on a nightly schedule.

**Option B — Firebase Test Lab (future):** Upload an instrumented APK and a Maestro/UI Automator test bundle to Firebase Test Lab. No physical device required. Higher per-run cost but fully cloud-native. Deferred until the local farm validates the test suite design.

Recommendation: ship Option A first. It unblocks automation immediately. TST-011 implements Option A; Firebase Test Lab is a stretch goal once the suite is stable.

### 5.2 Trigger strategy

| Trigger | Suite | Devices |
|---------|-------|---------|
| Pull request (Android code changes) | J1 + J12 smoke suite | Device B (farm-current) only |
| Nightly schedule (02:00 local) | Full journey suite (J1–J5, J9, J12) | All three devices in parallel |
| On-demand (manual) | Any suite via `make test-farm` | Any subset |

The PR trigger should be narrowed to changes under `HeirloomsApp/` to avoid running the farm on server-only or web-only changes.

### 5.3 Failure notifications

A test run that fails should:

1. Post a GitHub Actions annotation on the relevant commit/PR (automated via the runner).
2. Write a timestamped log to `test-results/farm/<run-id>/` (gitignored, retained for 30 days).
3. Capture a Maestro screenshot and ADB logcat dump for the failing flow and attach them to the GitHub Actions run artifact.

For nightly failures with no open PR, the runner should post a failure summary to the project's notification channel (exact channel TBD — e.g., email to bacalv@gmail.com or a local notification via `osascript`).

### 5.4 Test result format

Maestro outputs JUnit XML when run with `--format junit`. This integrates natively with GitHub Actions test reporting and allows trend tracking over time. Store results under `test-results/farm/` (gitignored).

---

## 6. Crypto-Path Testing Strategy

### 6.1 Philosophy

The cryptographic paths are Heirlooms' highest-value and highest-risk code. Two complementary strategies apply:

- **White-box unit tests (Espresso/AndroidJUnit4):** Assert on the internal cryptographic state — that the DEK is wrapped, the envelope version byte is correct, the Keystore slot was used rather than SharedPreferences, and that the vault unlock derives the expected master key from a known passphrase. These tests run against a test APK compiled with the `staging` flavor and can be exercised on the CI emulator without physical devices.
- **Black-box E2E tests (Maestro):** Assert on observable behavior — that a photo uploaded by User A can be decrypted and displayed by User B after sharing, that a photo uploaded by User A is not visible to an unauthenticated request, that approving a staged item makes it appear in the shared plot. These are the regression tests.

The two layers are complementary: white-box tests catch crypto bugs early in the build; black-box tests catch integration regressions that only appear when the full stack is running.

### 6.2 Priority crypto paths and recommended test type

| Crypto path | Recommended test type | Rationale |
|------------|----------------------|-----------|
| Vault unlock — master key derivation | Espresso (unit) | Must assert on the derived key bytes, not just that the screen advances. Use a known test passphrase and assert the derived AES key matches the expected value. |
| Upload — DEK generation and wrapping | Espresso (unit) + Maestro (E2E) | Unit: assert DEK is random (not constant), envelope version byte is 1, wrapped DEK length is correct. E2E: upload a file, fetch it as a different device, assert decryption succeeds. |
| Shared plot — plot-key unwrap and item decryption | Maestro (E2E) | Cross-device: User A shares an item; User B decrypts it. Maestro asserts the item is visible in the shared plot. White-box assertion of the plot-key unwrap belongs in a server-side integration test. |
| Keystore session token (SEC-007) | adb + Espresso | `adb shell run-as` to confirm no plaintext token; Espresso to assert `EncryptedSharedPreferences` is used. |
| Shared plot DEK re-wrap (BUG-018 regression) | Espresso (unit) + Maestro | Unit: assert that the re-wrapped DEK decrypts to the same plaintext. E2E: member adds item to shared plot without staging; owner sees it immediately. |
| Future: window capsule seal/open | Espresso (unit) | M11 work. Seal a capsule with a known DEK; assert the `wrapped_blinding_mask` and `wrapped_capsule_key` fields are independently correct. |
| Future: HMAC tag generation (ARCH-007) | Espresso (unit) | Assert HMAC output matches expected value for a known tag + key pair. Tag scheme must not leak plaintext tag names in any serialised field. |

### 6.3 Avoiding crypto path regressions

Two practices enforce this:

1. **Coverage gate**: The existing SEC-002 task targets 100% coverage on auth/crypto paths. The Espresso suite for crypto paths contributes to this gate. The CI pipeline should fail if crypto-path coverage drops below 100%.
2. **Canary test**: A dedicated `CryptoSmokeTest` Espresso test runs on every build and asserts that all three envelope primitives (DEK encrypt, DEK wrap, master key derive) produce structurally valid outputs with the expected version byte and algorithm ID. This catches build-config errors (wrong key size, algorithm swap) immediately.

---

## 7. Open Questions for CTO

1. **Device models**: Which three physical Android devices does Bret own? Confirming their Android versions will determine whether the version coverage plan (API 28 / 34 / 35) can be met without additional devices.
2. **Host machine**: Is the USB hub attached to the development Mac, or is a dedicated Mac Mini available as a self-hosted runner? This determines whether a local runner can run nightly without interfering with development work.
3. **TST-006 timing**: TST-006 (Android remote-control investigation) is still queued. If TST-006 concludes before TST-011 setup begins and recommends a different tooling approach, the Maestro recommendation should be revisited. The crypto-path Espresso layer is not affected by TST-006's outcome.
4. **Firebase Test Lab budget**: Is there appetite to allocate GCP spend for Firebase Test Lab runs in parallel with the local farm? This would give CI on PRs without requiring the local machine to be on and unlocked.

---

## 8. Implementation Path

The implementation is covered by TST-011 (see `tasks/queue/TST-011_android-device-farm-setup.md`). Recommended sequencing:

1. Device naming and ADB setup (USB hub, `.env` file, `adb devices` smoke test)
2. APK deployment scripts (`scripts/test-farm/deploy-apk.sh`)
3. Account provisioning script (`scripts/test-farm/provision-accounts.sh`)
4. First Maestro flow: Journey 12 flavor smoke (simplest, highest ROI)
5. Second Maestro flow: Journey 1 upload + garden
6. Espresso crypto smoke test (`CryptoSmokeTest`)
7. GitHub Actions self-hosted runner configuration
8. Nightly schedule + failure notifications
9. Full journey suite (J2–J5, J9)

---

*Document produced by TestManager for the v0.55 iteration. Implementation task: TST-011.*
