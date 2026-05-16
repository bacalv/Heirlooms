#!/usr/bin/env bash
# deploy-apk.sh — Install the staging APK on a single device and clear app data.
#
# Usage:
#   scripts/test-farm/deploy-apk.sh --apk <path-to-apk> --device <serial>
#
# Arguments:
#   --apk     Absolute or relative path to the .apk file to install.
#   --device  ADB serial (USB serial or IP:port) of the target device.
#             Typically the value of $FARM_DEVICE_CURRENT etc. from .env.
#
# Exit code 0  : install + clear succeeded
# Exit code 1  : any step failed

set -euo pipefail

STAGING_APP_ID="digital.heirlooms.app.test"

usage() {
  echo "Usage: $0 --apk <path> --device <serial>" >&2
  exit 1
}

APK_PATH=""
DEVICE_SERIAL=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --apk)    APK_PATH="$2";    shift 2 ;;
    --device) DEVICE_SERIAL="$2"; shift 2 ;;
    *) echo "Unknown argument: $1" >&2; usage ;;
  esac
done

[[ -z "${APK_PATH}"     ]] && { echo "ERROR: --apk is required." >&2; usage; }
[[ -z "${DEVICE_SERIAL}" ]] && { echo "ERROR: --device is required." >&2; usage; }
[[ -f "${APK_PATH}"     ]] || { echo "ERROR: APK not found: ${APK_PATH}" >&2; exit 1; }

echo "[deploy-apk] Installing ${APK_PATH} on ${DEVICE_SERIAL} ..."
adb -s "${DEVICE_SERIAL}" install -r -t "${APK_PATH}"

echo "[deploy-apk] Clearing app data for ${STAGING_APP_ID} on ${DEVICE_SERIAL} ..."
adb -s "${DEVICE_SERIAL}" shell pm clear "${STAGING_APP_ID}"

echo "[deploy-apk] Done. ${DEVICE_SERIAL} is ready."
