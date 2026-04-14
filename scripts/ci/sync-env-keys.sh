#!/usr/bin/env bash
# sync-env-keys.sh
#
# Compares .env.example with .env.prod and appends any keys that exist
# in .env.example but are missing (or only commented-out) in .env.prod.
#
# Usage:
#   ./scripts/ci/sync-env-keys.sh [EXAMPLE_FILE] [PROD_FILE]
#
# Defaults:
#   EXAMPLE_FILE = <repo-root>/.env.example
#   PROD_FILE    = <repo-root>/.env.prod

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

EXAMPLE_FILE="${1:-${REPO_ROOT}/.env.example}"
PROD_FILE="${2:-${REPO_ROOT}/.env.prod}"

# --------------------------------------------------------------------------
# Validate inputs
# --------------------------------------------------------------------------
if [[ ! -f "${EXAMPLE_FILE}" ]]; then
  echo "ERROR: example file not found: ${EXAMPLE_FILE}" >&2
  exit 1
fi

if [[ ! -f "${PROD_FILE}" ]]; then
  echo "ERROR: prod file not found: ${PROD_FILE}" >&2
  exit 1
fi

# --------------------------------------------------------------------------
# Extract active (non-commented) keys from a file.
# A valid key line looks like:   KEY=...   or   KEY=
# Lines starting with '#' or blank lines are skipped.
# --------------------------------------------------------------------------
extract_keys() {
  local file="$1"
  grep -E '^[A-Za-z_][A-Za-z0-9_]*=' "${file}" | cut -d'=' -f1
}

EXAMPLE_KEYS=$(extract_keys "${EXAMPLE_FILE}")
PROD_KEYS=$(extract_keys "${PROD_FILE}")

# --------------------------------------------------------------------------
# Keys intentionally omitted from .env.prod (local-dev / infra-only vars)
# --------------------------------------------------------------------------
EXCLUDED_KEYS=(
  DB_URL
  DB_URL_E2E
  POSTGRES_CONTAINER_NAME
  POSTGRES_DB
  POSTGRES_USER
  POSTGRES_PASSWORD
  POSTGRES_PORT
  POSTGRES_VOLUME_NAME
  PROJECT_NETWORK
)

is_excluded() {
  local key="$1"
  for excluded in "${EXCLUDED_KEYS[@]}"; do
    [[ "${key}" == "${excluded}" ]] && return 0
  done
  return 1
}

# --------------------------------------------------------------------------
# Find keys in example but absent from prod (as active keys)
# --------------------------------------------------------------------------
MISSING_KEYS=()
while IFS= read -r key; do
  if is_excluded "${key}"; then
    continue
  fi
  if ! echo "${PROD_KEYS}" | grep -qx "${key}"; then
    MISSING_KEYS+=("${key}")
  fi
done <<< "${EXAMPLE_KEYS}"

if [[ ${#MISSING_KEYS[@]} -eq 0 ]]; then
  echo "No missing keys found. ${PROD_FILE} is already in sync with ${EXAMPLE_FILE}."
  exit 0
fi

# --------------------------------------------------------------------------
# Append missing keys with empty values to .env.prod
# --------------------------------------------------------------------------
echo "" >> "${PROD_FILE}"
echo "# ====================================================================" >> "${PROD_FILE}"
echo "# Keys added by sync-env-keys.sh — fill in before deploying" >> "${PROD_FILE}"
echo "# ====================================================================" >> "${PROD_FILE}"

for key in "${MISSING_KEYS[@]}"; do
  echo "${key}=" >> "${PROD_FILE}"
done

echo "Appended ${#MISSING_KEYS[@]} missing key(s) to ${PROD_FILE}:"
for key in "${MISSING_KEYS[@]}"; do
  echo "  + ${key}"
done
