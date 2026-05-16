#!/usr/bin/env bash
# teardown-accounts.sh — Clean up test accounts after a farm run.
#
# Reads scripts/test-farm/.farm-env, attempts to delete the test accounts via
# the test API (if a DELETE /api/users/:username endpoint is available), and
# clears .farm-env.
#
# If no delete endpoint exists yet, the script logs the orphaned usernames for
# manual cleanup and exits with code 0 so CI is not blocked.
#
# Usage:
#   scripts/test-farm/teardown-accounts.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FARM_ENV="${SCRIPT_DIR}/.farm-env"
TEST_API="https://test.api.heirlooms.digital"
GCP_PROJECT="heirlooms-495416"
SECRET_NAME="heirlooms-test-api-key"

if [[ ! -f "${FARM_ENV}" ]]; then
  echo "[teardown] .farm-env not found — nothing to tear down."
  exit 0
fi

# shellcheck source=/dev/null
source "${FARM_ENV}"

echo "=== Heirlooms device farm — account teardown ==="
echo "[teardown] Run ID: ${RUN_ID:-unknown}"
echo "[teardown] User A: ${USER_A_USERNAME:-unknown}"
echo "[teardown] User B: ${USER_B_USERNAME:-unknown}"

# Fetch API key to attempt deletion
API_KEY=""
if command -v gcloud &>/dev/null; then
  API_KEY=$(gcloud secrets versions access latest \
    --secret="${SECRET_NAME}" \
    --project="${GCP_PROJECT}" \
    --quiet 2>/dev/null || true)
fi

delete_account() {
  local username="$1"
  if [[ -z "${API_KEY}" ]]; then
    echo "[teardown] SKIP delete ${username} — no API key (gcloud not available)."
    return
  fi

  local http_code
  http_code=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE \
    -H "X-API-Key: ${API_KEY}" \
    "${TEST_API}/api/admin/users/${username}" 2>/dev/null || echo "000")

  case "${http_code}" in
    200|204) echo "[teardown] Deleted ${username}." ;;
    404)     echo "[teardown] ${username} not found (already deleted or never created)." ;;
    405|501) echo "[teardown] DELETE endpoint not implemented — orphan: ${username}" ;;
    000)     echo "[teardown] Network error deleting ${username} — orphan logged." ;;
    *)       echo "[teardown] Unexpected HTTP ${http_code} deleting ${username} — orphan logged." ;;
  esac
}

delete_account "${USER_A_USERNAME:-}"
delete_account "${USER_B_USERNAME:-}"

# Clear .farm-env
rm -f "${FARM_ENV}"
echo "[teardown] .farm-env cleared."
echo "=== Teardown complete ==="
