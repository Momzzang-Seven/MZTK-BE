#!/usr/bin/env bash
set -euo pipefail

# ──────────────────────────────────────────────────────────────────────────
# check-env-coverage.sh
#
# Ensures that every ${KEY} placeholder in application.yml and
# application-prod.yml is documented in .env.example.
#
# How it works:
#   1. Extract all ${KEY} / ${KEY:default} patterns from the YML files.
#   2. Strip the ":default" part so only the bare key name remains.
#   3. Extract all KEY= entries from .env.example.
#   4. Report keys present in YML but absent from .env.example as failures.
#
# Allowlist:
#   Keys that intentionally have a sensible hard-coded default and do NOT
#   need to appear in .env.example can be listed in ALLOWLIST below.
#   Always add a comment explaining why the key is excluded.
# ──────────────────────────────────────────────────────────────────────────

YML_FILES=(
  src/main/resources/application.yml
  src/main/resources/application-dev.yml
  src/main/resources/application-prod.yml
)

ENV_EXAMPLE=".env.example"

# Keys intentionally omitted from .env.example because they carry a
# meaningful hard-coded default that is safe to ship as-is.
# When adding a new entry, explain WHY in the inline comment.
ALLOWLIST=(
  # ── App info: informational labels with safe defaults ───────────────────
  APP_NAME              # default: MZTK-BE
  APP_VERSION           # default: 0.0.1-SNAPSHOT
  APP_DESCRIPTION       # default: MZTK-BE Backend Service
  APP_ENVIRONMENT       # default: local

  # ── Public endpoints with well-known defaults ────────────────────────────
  WEB3_EXPLORER_API_URL # default: https://api.etherscan.io/v2/api

  # ── Async cleanup tuning: numeric/cron defaults cover local/dev usage ───
  ASYNC_CLEANUP_CRON                          # default: 0 30 * * * *
  ASYNC_CLEANUP_ZONE                          # default: Asia/Seoul
  COMMENT_HARD_DELETE_RETENTION_DAYS          # default: 30
  COMMENT_HARD_DELETE_BATCH_SIZE              # default: 100
  ANSWER_ORPHAN_CLEANUP_BATCH_SIZE            # default: 100
  IMAGE_POST_ORPHAN_CLEANUP_BATCH_SIZE        # default: 100
  IMAGE_ANSWER_ORPHAN_CLEANUP_BATCH_SIZE      # default: 100

  # ── Feature flags with safe defaults ────────────────────────────────────
  WEB3_EIP_7702_ENABLED                                    # default: true
  WEB3_TRANSFER_LEGACY_QUESTION_REWARD_HANDLER_ENABLED     # default: false
)

# ── 1. Extract ${KEY} and ${KEY:...} placeholders from YML files ──────────
# Matches uppercase-only identifiers (Spring convention for env vars).
yml_keys=$(grep -ohE '\$\{[A-Z_][A-Z0-9_]*(:[^}]*)?\}' "${YML_FILES[@]}" \
  | sed -E 's/\$\{([A-Z_][A-Z0-9_]*).*/\1/' \
  | sort -u)

# ── 2. Extract KEY= entries from .env.example ─────────────────────────────
env_keys=$(grep -E '^[A-Z_][A-Z0-9_]*=' "$ENV_EXAMPLE" \
  | cut -d'=' -f1 \
  | sort -u)

# ── 3. Build allowlist regex ──────────────────────────────────────────────
allowlist_pattern=$(
  for entry in "${ALLOWLIST[@]}"; do
    # Strip inline comment, then trim trailing whitespace
    key="${entry%%#*}"
    key="${key%"${key##*[! ]}"}"
    echo "$key"
  done | paste -sd '|' -
)

# ── 4. Detect missing keys ────────────────────────────────────────────────
missing=$(comm -23 \
  <(echo "$yml_keys") \
  <(echo "$env_keys") \
  | grep -vE "^(${allowlist_pattern})$" || true)

if [ -n "$missing" ]; then
  echo ""
  echo "ERROR: The following env vars from application.yml / application-prod.yml"
  echo "       are not documented in .env.example:"
  echo ""
  while IFS= read -r key; do
    echo "  - ${key}"
  done <<< "$missing"
  echo ""
  echo "Every \${KEY} placeholder added to the YML files must have a matching"
  echo "entry in .env.example so teammates know the variable exists."
  echo ""
  echo "If the key has a meaningful hard-coded default and truly does not need"
  echo "to be configured, add it to the ALLOWLIST inside this script with a"
  echo "comment explaining why."
  echo ""
  exit 1
fi

echo "OK: .env.example covers all env vars declared in application YML files."
