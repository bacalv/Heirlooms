#!/usr/bin/env bash
# check-devices.sh — Verify that all three farm devices are connected via ADB.
#
# Exit code 0  : all three devices are present with "device" status
# Exit code 1  : one or more devices are missing or have a non-"device" status
#
# Usage:
#   scripts/test-farm/check-devices.sh
#
# Requires: .env in the same directory with FARM_DEVICE_MINAPI,
#           FARM_DEVICE_CURRENT, FARM_DEVICE_LATEST set.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

ENV_FILE="${SCRIPT_DIR}/.env"
if [[ ! -f "${ENV_FILE}" ]]; then
  echo "ERROR: ${ENV_FILE} not found." >&2
  echo "       Copy .env.template to .env and fill in the device serials." >&2
  exit 1
fi

# shellcheck source=/dev/null
source "${ENV_FILE}"

: "${FARM_DEVICE_MINAPI:?FARM_DEVICE_MINAPI is not set in .env}"
: "${FARM_DEVICE_CURRENT:?FARM_DEVICE_CURRENT is not set in .env}"
: "${FARM_DEVICE_LATEST:?FARM_DEVICE_LATEST is not set in .env}"

FAILURES=0

check_device() {
  local role="$1"
  local serial="$2"

  # `adb -s <serial> get-state` prints "device" when connected, or fails.
  local state
  state=$(adb -s "${serial}" get-state 2>&1) || state="offline"

  if [[ "${state}" == "device" ]]; then
    # Also print the Android version for informational purposes.
    local android_ver
    android_ver=$(adb -s "${serial}" shell getprop ro.build.version.release 2>/dev/null | tr -d '[:space:]')
    local api_level
    api_level=$(adb -s "${serial}" shell getprop ro.build.version.sdk 2>/dev/null | tr -d '[:space:]')
    echo "OK  [${role}] serial=${serial}  Android ${android_ver} (API ${api_level})"
  else
    echo "FAIL [${role}] serial=${serial}  state=${state}" >&2
    FAILURES=$((FAILURES + 1))
  fi
}

echo "=== Heirlooms device farm — connectivity check ==="
echo ""

check_device "farm-minapi  (API 28 target)" "${FARM_DEVICE_MINAPI}"
check_device "farm-current (API 34 target)" "${FARM_DEVICE_CURRENT}"
check_device "farm-latest  (API 35 target)" "${FARM_DEVICE_LATEST}"

echo ""

if [[ "${FAILURES}" -eq 0 ]]; then
  echo "All devices ready."
  exit 0
else
  echo "ERROR: ${FAILURES} device(s) not ready. Check USB connections and USB debugging." >&2
  exit 1
fi
