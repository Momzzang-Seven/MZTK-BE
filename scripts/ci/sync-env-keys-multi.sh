#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────
# sync-env-keys-multi.sh
#
# .env.example 에 새로 추가된 키를 검출해, 아래 5개 파일 중
# "적절한 파일(들)" 에 빈 값(KEY=) 으로 append 한다.
#
#   1. .env.prod.common
#   2. .env.prod.web3.optimism.testnet.template
#   3. .env.prod.web3.optimism.mainnet.template
#   4. .env.prod.web3.base.testnet.template
#   5. .env.prod.web3.base.mainnet.template
#
# "값" 은 절대 자동으로 채우지 않는다. 스프린트 마감 직전 담당자가
# 손으로 채워 넣어야 하며, 빈 값이 남아있는 한 ssm-upsert.sh 가
# 해당 키를 skip + 경고하므로 배포 시 자연스럽게 "미완성" 신호가 된다.
#
# 분류 휴리스틱 (keyname 기준):
#   아래 패턴 중 하나에 매칭되면 chain×network 의존 키로 판단 →
#   4개 web3 템플릿에 모두 추가 (키 집합 동일성 유지).
#     - WEB3_*           : 체인/RPC/컨트랙트/EIP-712/EIP-7702 등
#     - MZTK_TOKEN_*     : 리워드 토큰 컨트랙트 주소
#     - TREASURY_*       : 트레저리 지갑/키/임계치
#     - SPONSOR_*        : 스폰서 지갑/키
#
#   그 외는 chain/network 무관으로 판단 → .env.prod.common 에 추가.
#
#   휴리스틱이 애매한 키는 "unclassified" 섹션으로 모아 사람이
#   리뷰하도록 한다. (현재는 모든 키가 위 규칙 내에 떨어지므로
#   unclassified 가 비어있음 — 엣지 케이스 생기면 추가할 것.)
#
# Usage:
#   ./scripts/ci/sync-env-keys-multi.sh             # append 모드
#   ./scripts/ci/sync-env-keys-multi.sh --check     # diff 검출만, 변경 X
#                                                   # 드리프트 있으면 exit 1
#   ./scripts/ci/sync-env-keys-multi.sh --dry-run   # 분류 결과만 출력, 변경 X
#
# Exit codes:
#   0  드리프트 없음 또는 성공적으로 append 완료
#   1  --check 모드에서 드리프트 검출 / 4개 web3 템플릿 키셋 불일치
#   2  입력 파일 누락 등 실행 전 에러
# ─────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

EXAMPLE_FILE="${REPO_ROOT}/.env.example"
COMMON_FILE="${REPO_ROOT}/.env.prod.common"
WEB3_FILES=(
  "${REPO_ROOT}/.env.prod.web3.optimism.testnet.template"
  "${REPO_ROOT}/.env.prod.web3.optimism.mainnet.template"
  "${REPO_ROOT}/.env.prod.web3.base.testnet.template"
  "${REPO_ROOT}/.env.prod.web3.base.mainnet.template"
)

# ─────────────────────────────────────────────
# .env.example 에만 있고 prod 에는 들어가면 안 되는 키
# (로컬/통합테스트 전용)
# ─────────────────────────────────────────────
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

# ─────────────────────────────────────────────
# 플래그 파싱
# ─────────────────────────────────────────────
MODE="apply"
for arg in "$@"; do
  case "$arg" in
    --check)   MODE="check";;
    --dry-run) MODE="dry-run";;
    -h|--help)
      sed -n '2,40p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *)
      echo "ERROR: unknown arg: $arg" >&2
      exit 2
      ;;
  esac
done

# ─────────────────────────────────────────────
# 입력 파일 검증
# ─────────────────────────────────────────────
if [[ ! -f "${EXAMPLE_FILE}" ]]; then
  echo "ERROR: ${EXAMPLE_FILE} not found." >&2
  exit 2
fi
for f in "${COMMON_FILE}" "${WEB3_FILES[@]}"; do
  if [[ ! -f "$f" ]]; then
    echo "ERROR: target file not found: $f" >&2
    exit 2
  fi
done

# ─────────────────────────────────────────────
# 유틸: 파일에서 active 키 이름만 추출
# ─────────────────────────────────────────────
extract_keys() {
  grep -E '^[A-Za-z_][A-Za-z0-9_]*=' "$1" | cut -d'=' -f1 | sort -u
}

is_excluded() {
  local key="$1"
  for excluded in "${EXCLUDED_KEYS[@]}"; do
    [[ "${key}" == "${excluded}" ]] && return 0
  done
  return 1
}

# chain×network 에 의존하는 키인지 판정
is_web3_key() {
  case "$1" in
    WEB3_*|MZTK_TOKEN_*|TREASURY_*|SPONSOR_*) return 0;;
    *) return 1;;
  esac
}

# ─────────────────────────────────────────────
# Step A: 4 web3 템플릿 키 집합 동일성 검증
# ─────────────────────────────────────────────
echo "▶ verifying web3 template key-set parity ..."
REF_KEYS="$(extract_keys "${WEB3_FILES[0]}")"
parity_ok=1
for f in "${WEB3_FILES[@]:1}"; do
  OTHER_KEYS="$(extract_keys "$f")"
  if ! diff -q <(echo "$REF_KEYS") <(echo "$OTHER_KEYS") > /dev/null; then
    echo "  ✗ key-set mismatch between:" >&2
    echo "      ${WEB3_FILES[0]}" >&2
    echo "      $f" >&2
    echo "    diff (- ref / + other):" >&2
    diff <(echo "$REF_KEYS") <(echo "$OTHER_KEYS") | sed 's/^/      /' >&2
    parity_ok=0
  fi
done
if [[ $parity_ok -eq 0 ]]; then
  echo "ERROR: web3 template keys are not in sync. fix manually first." >&2
  exit 1
fi
echo "  ✓ all ${#WEB3_FILES[@]} web3 templates share the same key set"
echo

# ─────────────────────────────────────────────
# Step B: .env.example vs 전체 target 의 drift 계산
# ─────────────────────────────────────────────
EXAMPLE_KEYS="$(extract_keys "${EXAMPLE_FILE}")"
COMMON_KEYS="$(extract_keys "${COMMON_FILE}")"
WEB3_KEYS="$REF_KEYS"   # 위에서 parity 검증 끝났으므로 ref 로 대표

missing_for_common=()
missing_for_web3=()
unclassified=()

while IFS= read -r key; do
  [[ -z "$key" ]] && continue
  is_excluded "$key" && continue

  # 이미 common 또는 web3 templates 중 어디든 존재하면 skip
  if echo "$COMMON_KEYS" | grep -qx "$key"; then continue; fi
  if echo "$WEB3_KEYS"   | grep -qx "$key"; then continue; fi

  if is_web3_key "$key"; then
    missing_for_web3+=("$key")
  else
    # 여기 떨어지는 건 common 이 자연스러운 기본값이지만,
    # 도메인이 애매한 키는 사람이 한 번 더 봐주는 편이 안전.
    # 현재 규칙상 "common 이 기본" 이므로 missing_for_common 에 넣되,
    # 이름에 명백한 공통 키워드가 없고 알 수 없는 접두어면
    # unclassified 로 빼 별도 보고.
    case "$key" in
      DB_*|JWT_*|KAKAO_*|GOOGLE_*|AWS_*|APP_*|GEMINI_*|LAMBDA_*|\
      ADMIN_*|ASYNC_*|ANSWER_*|COMMENT_*|IMAGE_*|SPRING_*|PROD_*)
        missing_for_common+=("$key")
        ;;
      *)
        unclassified+=("$key")
        ;;
    esac
  fi
done <<< "$EXAMPLE_KEYS"

total=$(( ${#missing_for_common[@]} + ${#missing_for_web3[@]} + ${#unclassified[@]} ))

# ─────────────────────────────────────────────
# Step C: 결과 보고
# ─────────────────────────────────────────────
echo "▶ drift report"
echo "    .env.example            : $(echo "$EXAMPLE_KEYS" | wc -l | tr -d ' ') keys"
echo "    .env.prod.common        : $(echo "$COMMON_KEYS"   | wc -l | tr -d ' ') keys"
echo "    .env.prod.web3.*        : $(echo "$WEB3_KEYS"     | wc -l | tr -d ' ') keys (× 4 files)"
echo

if [[ $total -eq 0 ]]; then
  echo "✓ no drift. all example keys are already present in target files."
  exit 0
fi

print_list() {
  local title="$1"; shift
  if [[ $# -gt 0 ]]; then
    echo "  $title (${#}):"
    printf '    + %s\n' "$@"
  fi
}

echo "  missing keys detected:"
print_list "→ .env.prod.common"    "${missing_for_common[@]:-}"
print_list "→ 4× .env.prod.web3.*" "${missing_for_web3[@]:-}"
print_list "⚠ unclassified (needs human review)" "${unclassified[@]:-}"
echo

# ─────────────────────────────────────────────
# Step D: 모드별 분기
# ─────────────────────────────────────────────
case "$MODE" in
  check)
    echo "ERROR: drift detected in --check mode." >&2
    echo "       run './scripts/ci/sync-env-keys-multi.sh' (without --check) to append." >&2
    exit 1
    ;;
  dry-run)
    echo "(dry-run) no files modified."
    exit 0
    ;;
  apply)
    # unclassified 가 있으면 자동 append 거부 — 사람 개입 필요
    if [[ ${#unclassified[@]} -gt 0 ]]; then
      echo "ERROR: unclassified keys present. refusing to auto-append." >&2
      echo "       classify them manually (update the heuristic in this script," >&2
      echo "       or edit target files directly), then re-run." >&2
      exit 1
    fi
    ;;
esac

# ─────────────────────────────────────────────
# Step E: append
# ─────────────────────────────────────────────
stamp="$(date +%Y-%m-%d)"

append_header() {
  local file="$1"
  {
    echo ""
    echo "# ===================================================================="
    echo "# Added by sync-env-keys-multi.sh on ${stamp} — fill before deploying"
    echo "# ===================================================================="
  } >> "$file"
}

if [[ ${#missing_for_common[@]} -gt 0 ]]; then
  append_header "${COMMON_FILE}"
  for key in "${missing_for_common[@]}"; do
    printf '%s=\n' "$key" >> "${COMMON_FILE}"
  done
  echo "  + appended ${#missing_for_common[@]} key(s) to ${COMMON_FILE#${REPO_ROOT}/}"
fi

if [[ ${#missing_for_web3[@]} -gt 0 ]]; then
  for f in "${WEB3_FILES[@]}"; do
    append_header "$f"
    for key in "${missing_for_web3[@]}"; do
      printf '%s=\n' "$key" >> "$f"
    done
    echo "  + appended ${#missing_for_web3[@]} key(s) to ${f#${REPO_ROOT}/}"
  done
fi

echo
echo "✓ done. fill the appended empty values before running refresh-and-restart.sh."
