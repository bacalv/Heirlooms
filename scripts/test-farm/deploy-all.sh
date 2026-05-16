#!/usr/bin/env bash
# deploy-all.sh — Deploy the staging APK to all three farm devices in parallel.
#
# Usage:
#   scripts/test-farm/deploy-all.sh --apk <path-to-apk>
#
# Requires: scripts/test-farm/.env with FARM_DEVICE_MINAPI, FARM_DEVICE_CURRENT,
#           FARM_DEVICE_LATEST set.
#
# Exit code 0  : all three deploys succeeded
# Exit code 1  : one or more deploys failed

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

ENV_FILE="${SCRIPT_DIR}/.env"
if [[ ! -f "${ENV_FILE}" ]]; then
  echo "ERROR: ${ENV_FILE} not found." >&2
  exit 1
fi

# shellcheck source=/dev/null
source "${ENV_FILE}"

: "${FARM_DEVICE_MINAPI:?FARM_DEVICE_MINAPI is not set in .env}"
: "${FARM_DEVICE_CURRENT:?FARM_DEVICE_CURRENT is not set in .env}"
: "${FARM_DEVICE_LATEST:?FARM_DEVICE_LATEST is not set in .env}"

APK_PATH=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --apk) APK_PATH="$2"; shift 2 ;;
    *) echo "Unknown argument: $1" >&2; exit 1 ;;
  esac
done
[[ -z "${APK_PATH}" ]] && { echo "ERROR: --apk is required." >&2; exit 1; }
[[ -f "${APK_PATH}" ]] || { echo "ERROR: APK not found: ${APK_PATH}" >&2; exit 1; }

DEPLOY="${SCRIPT_DIR}/deploy-apk.sh"

echo "=== Deploying ${APK_PATH} to all farm devices in parallel ==="

"${DEPLOY}" --apk "${APK_PATH}" --device "${FARM_DEVICE_MINAPI}"  &
PID_MINAPI=$!
"${DEPLOY}" --apk "${APK_PATH}" --device "${FARM_DEVICE_CURRENT}" &
PID_CURRENT=$!
"${DEPLOY}" --apk "${APK_PATH}" --device "${FARM_DEVICE_LATEST}"  &
PID_LATEST=$!

FAILURES=0
wait "${PID_MINAPI}"  || { echo "ERROR: deploy to farm-minapi failed."  >&2; FAILURES=$((FAILURES+1)); }
wait "${PID_CURRENT}" || { echo "ERROR: deploy to farm-current failed." >&2; FAILURES=$((FAILURES+1)); }
wait "${PID_LATEST}"  || { echo "ERROR: deploy to farm-latest failed."  >&2; FAILURES=$((FAILURES+1)); }

echo ""
if [[ "${FAILURES}" -eq 0 ]]; then
  echo "=== All devices ready ==="
  exit 0
else
  echo "ERROR: ${FAILURES} deploy(s) failed." >&2
  exit 1
fi
