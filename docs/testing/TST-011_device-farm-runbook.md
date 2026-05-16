# TST-011 — Android Device Farm Runbook

**Author:** TestManager  
**Date:** 2026-05-16  
**Task:** TST-011  
**Status:** Initial version — requires physical device verification by Bret

---

## 1. Overview

The Heirlooms Android device farm runs automated journey tests (Maestro) and
crypto smoke tests (Espresso/AndroidJUnit4) across three physical Android devices.

| Layer | Tool | Scope |
|-------|------|-------|
| Journey / E2E | Maestro (YAML flows) | Full-stack: app → test API → GCS |
| Crypto / unit | Espresso + AndroidJUnit4 | In-process: VaultCrypto, envelope format |

Infrastructure summary:
- **3 physical devices** connected via USB hub to the self-hosted runner.
- **Staging flavor** (`digital.heirlooms.app.test`) used exclusively.
- **Test API** (`https://test.api.heirlooms.digital`) — throw-away data.
- **GitHub Actions** self-hosted runner with label `android-farm`.

---

## 2. First-time setup

### 2.1 Physical devices

1. Connect all three devices to a powered USB hub attached to the Mac runner.
2. Enable **USB Debugging** on each device:
   - Settings → About Phone → tap Build Number 7 times → Developer Options → USB Debugging ON.
3. Authorize the Mac when the device prompts "Allow USB debugging from this computer?"
4. Verify all three appear with `device` status:
   ```bash
   adb devices
   # Expected output (example serials):
   # List of devices attached
   # RFCR804A device
   # RFCR912B device
   # RFCR103C device
   ```

### 2.2 Device environment file

```bash
cp scripts/test-farm/.env.template scripts/test-farm/.env
```

Edit `scripts/test-farm/.env` and fill in the three ADB serials from `adb devices`:

```
FARM_DEVICE_MINAPI=<serial of Android 9 / API 28 device>
FARM_DEVICE_CURRENT=<serial of Android 14 / API 34 device>
FARM_DEVICE_LATEST=<serial of Android 15 / API 35 device>
```

The file is gitignored. Never commit it.

### 2.3 GCP authentication

The provisioning script fetches the test API key from Secret Manager. Ensure the
runner machine has GCP ADC configured:

```bash
gcloud auth application-default login
# or, on a CI runner with a service account:
gcloud auth activate-service-account --key-file=/path/to/key.json
```

### 2.4 Maestro CLI installation

```bash
curl -Ls "https://get.maestro.mobile.dev" | bash
# Follow the instructions to add maestro to PATH.
maestro --version  # verify
```

### 2.5 GitHub Actions self-hosted runner

Register the Mac as a self-hosted runner with the `android-farm` label:

1. Go to the Heirlooms GitHub repo → Settings → Actions → Runners → New self-hosted runner.
2. Choose macOS, follow the setup instructions.
3. When asked for labels, add: `self-hosted,macOS,android-farm`
4. Start the runner as a background service:
   ```bash
   ./svc.sh install
   ./svc.sh start
   ```

---

## 3. Daily operations

### 3.1 Run the smoke suite manually

```bash
# Quick sanity check — J12 + J1 + CryptoSmokeTest on farm-current:
make test-farm-smoke
```

### 3.2 Run the full suite manually

```bash
# All journeys on all three devices:
make test-farm-full
```

### 3.3 Run a single Maestro flow

```bash
source scripts/test-farm/.env
source scripts/test-farm/.farm-env  # ensure this exists; run provision-accounts.sh first

maestro test \
  --device $FARM_DEVICE_CURRENT \
  --env USER_A_PASSWORD=$USER_A_PASSWORD \
  --env USER_A_USERNAME=$USER_A_USERNAME \
  scripts/test-farm/maestro/journey-12-flavor-smoke.yaml
```

### 3.4 Run CryptoSmokeTest only

```bash
source scripts/test-farm/.env
./gradlew :HeirloomsApp:app:connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=digital.heirlooms.crypto.CryptoSmokeTest \
  -Pandroid.injected.invocation.noshards=true \
  -Pandroid.device.serial=$FARM_DEVICE_CURRENT
```

---

## 4. Adding a new device

1. Connect the device to the USB hub and enable USB debugging (Section 2.1).
2. Note the ADB serial with `adb devices`.
3. Decide which role the device fills:
   - `FARM_DEVICE_MINAPI` — Android 9 / API 28
   - `FARM_DEVICE_CURRENT` — Android 14 / API 34
   - `FARM_DEVICE_LATEST` — Android 15 / API 35
4. Update `scripts/test-farm/.env` with the new serial.
5. Run `bash scripts/test-farm/check-devices.sh` to confirm connectivity.
6. No changes to test scripts or workflows are required — they reference the env vars,
   not hardcoded serials.

---

## 5. Updating the test APK

The staging debug APK is built from source as part of every CI run:
```bash
./gradlew :HeirloomsApp:app:assembleStaging
```

The Makefile and CI workflows refer to:
```
HeirloomsApp/app/build/outputs/apk/staging/debug/app-staging-debug.apk
```

If the Gradle module path or flavor name changes, update `STAGING_APK` in:
- `Makefile` (`STAGING_APK` variable at the top)
- `.github/workflows/android-farm-pr.yml` (`env.STAGING_APK`)
- `.github/workflows/android-farm-nightly.yml` (`env.STAGING_APK`)

---

## 6. Debugging a flaky test

### 6.1 Check ADB logcat

```bash
source scripts/test-farm/.env
adb -s $FARM_DEVICE_CURRENT logcat -d | grep -i "heirlooms\|error\|crash" | tail -100
```

### 6.2 Run Maestro in debug mode

```bash
maestro test --debug scripts/test-farm/maestro/journey-12-flavor-smoke.yaml
```

Maestro writes screenshots and a full event log to `~/.maestro/tests/` for the
failing run.

### 6.3 Selector names out of date

If `assertVisible` selectors stop matching after an app UI update:

1. Open `maestro studio` (interactive Maestro UI inspector):
   ```bash
   maestro studio
   ```
2. Navigate to the screen in question.
3. Tap the element to see its exact text / content description / id.
4. Update the YAML flow with the new selector.

### 6.4 "Device not found" in CI

1. Check that the USB hub power adapter is connected.
2. Check that the runner machine hasn't lost USB connection: `adb devices`.
3. On the device, revoke and re-authorize USB debugging:
   - Settings → Developer Options → Revoke USB debugging authorizations → reconnect.
4. If using Wi-Fi ADB, reconnect: `adb connect <device_ip>:5555`.

---

## 7. Test result retention

- Results are written to `test-results/farm/<run_id>/` (gitignored, not committed).
- GitHub Actions artifacts are retained for **30 days** (nightly) and **7 days** (PR).
- To browse results locally:
  ```bash
  ls test-results/farm/
  ```

---

## 8. Secret rotation

The test API key is stored in GCP Secret Manager under `heirlooms-test-api-key`
in project `heirlooms-495416`.

To rotate:
1. Create a new secret version in Secret Manager.
2. Update the secret value to the new key.
3. Disable the old version.
4. No script changes are needed — `provision-accounts.sh` always fetches `latest`.

---

## 9. Known limitations and deferred work

| Item | Status |
|------|--------|
| Biometric vault unlock automation | Deferred to TST-006. Requires Keystore test key via `adb shell`. |
| Firebase Test Lab | Deferred. Local farm validates the suite design first. |
| Journey 4 (shared plot + staging approval) — multi-device coordination | Maestro flow structure planned; shell wrapper needed to sequence two Maestro sessions. Not yet implemented. |
| Journey 5 (member trellis) | Planned; requires J4 infrastructure first. |
| Journey 9 (SEC-007 retest) | Planned; requires `adb shell run-as` helper. |
| GCS orphan cleanup | OPS-003 tracks this. Teardown leaves GCS blobs; only DB rows are removed. |
| TST-006 outcome | If TST-006 recommends a different tool than Maestro, the journey test layer should be re-evaluated. The Espresso crypto-path layer is unaffected. |

---

## 10. Physical verification required (Bret)

All scripts were written based on the TST-009 design document and the VaultCrypto
source code. The following steps **must be performed with physical devices connected**
before the farm is production-ready:

- [ ] `adb devices` confirms all three serials and maps correctly to `FARM_DEVICE_MINAPI`,
      `FARM_DEVICE_CURRENT`, `FARM_DEVICE_LATEST`.
- [ ] `make check-devices` exits 0 with correct Android version output.
- [ ] `make deploy-staging` installs the APK and clears app data without error.
- [ ] `provision-accounts.sh` creates accounts and writes `.farm-env`.
- [ ] Journey 12 Maestro flow runs and passes (selector names may need adjustment
      against the actual Compose UI — see Section 6.3).
- [ ] Journey 1 Maestro flow runs and passes (upload intent path may need tuning).
- [ ] `CryptoSmokeTest` all 8 test methods pass on at least one physical device.
- [ ] GitHub Actions self-hosted runner picks up the `android-farm-pr.yml` workflow.
- [ ] Nightly workflow triggers at the scheduled time and posts an osascript notification
      on failure.
