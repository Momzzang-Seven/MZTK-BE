#!/bin/bash
# ─────────────────────────────────────────────────────────────
# ssm-upsert.sh
#
# .env 형식 파일의 KEY=VALUE 를 AWS SSM Parameter Store 에 업로드한다.
# SSM 경로 접두어는 파일명에서 자동 유도되므로 사용자가 직접 입력하지 않는다.
#
# 파일명 → SSM 접두어 매핑 규칙:
#   .env.prod.common                              → /mztk/prod/common
#   .env.prod.web3.<chain>.<network>[.template]   → /mztk/prod/web3/<chain>/<network>
#
# (파일명 중 ".template" 접미사는 있으면 제거한 뒤 파싱한다.)
#
# Usage:
#   ./scripts/ci/ssm-upsert.sh <env-file>
#
# 예시:
#   ./scripts/ci/ssm-upsert.sh .env.prod.common
#   ./scripts/ci/ssm-upsert.sh .env.prod.web3.optimism.testnet.template
#   ./scripts/ci/ssm-upsert.sh .env.prod.web3.base.mainnet.template
#
# 허용된 chain / network (오주입 방지용 allow-list):
#   chain   : optimism | base
#   network : testnet  | mainnet
#
# 동작:
#   - 빈 줄/#주석 줄은 건너뜀
#   - value 가 빈 라인(KEY=)은 SSM put-parameter 가 거부하므로 skip + 경고
#   - 민감 키(이름에 SECRET/KEY/PASSWORD/KEY_B64/API_KEY 포함,
#     또는 DB_URL_PROD / JWT_SECRET)는 SecureString (KMS alias/mztk-ssm-prod)
#     로 저장, 그 외는 String
#   - add-tags-to-resource 로 Project/Env 태그 부여
# ─────────────────────────────────────────────────────────────
set -euo pipefail
# -e: 실패 즉시 종료 / -u: 미정의 변수 에러 / -o pipefail: 파이프 실패 전파

REGION="${AWS_REGION:-ap-northeast-2}"
KMS_KEY_ALIAS="alias/mztk-ssm-prod"
SSM_ROOT="/mztk/prod"

usage() {
  cat >&2 <<'EOF'
Usage: ssm-upsert.sh <env-file>

  <env-file>  업로드할 .env 형식 파일 경로.
              파일명으로부터 SSM 접두어가 자동 결정된다.

  지원되는 파일명:
    .env.prod.common                              → /mztk/prod/common
    .env.prod.web3.<chain>.<network>[.template]   → /mztk/prod/web3/<chain>/<network>
      chain   ∈ {optimism, base}
      network ∈ {testnet,  mainnet}
EOF
  exit 1
}

if [[ $# -ne 1 ]]; then
  usage
fi

ENV_FILE="$1"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "ERROR: env file not found: ${ENV_FILE}" >&2
  exit 1
fi

# ─────────────────────────────────────────────────────────────
# 파일명 → SSM 접두어 자동 유도
#
# basename 만 사용하므로 상대/절대 경로 모두 지원된다.
# ─────────────────────────────────────────────────────────────
derive_ssm_prefix() {
  local fname
  fname="$(basename "$1")"

  # 1) 반드시 .env.prod. 로 시작해야 함
  if [[ "${fname}" != .env.prod.* ]]; then
    echo "ERROR: unsupported env file name: '${fname}'" >&2
    echo "       expected prefix '.env.prod.'" >&2
    return 1
  fi

  # 2) 접두어 / 접미어 제거
  local body="${fname#.env.prod.}"      # 예: "web3.optimism.testnet.template" / "common"
  body="${body%.template}"              # .template 접미사 있으면 떼기

  # 3) common 은 단일 세그먼트
  if [[ "${body}" == "common" ]]; then
    printf '%s/common\n' "${SSM_ROOT}"
    return 0
  fi

  # 4) web3.<chain>.<network> 파싱
  #    kind 는 현재 "web3" 만 허용되지만, 추후 다른 네임스페이스(e.g. "infra")
  #    가 생겨도 케이스를 추가해 확장할 수 있도록 변수로 뽑아둔다.
  local kind chain network extra
  IFS='.' read -r kind chain network extra <<< "${body}"

  if [[ -z "${kind}" || -z "${chain}" || -z "${network}" || -n "${extra:-}" ]]; then
    echo "ERROR: cannot parse 'web3.<chain>.<network>' from file name: '${fname}'" >&2
    echo "       expected form: .env.prod.web3.<chain>.<network>[.template]" >&2
    return 1
  fi

  if [[ "${kind}" != "web3" ]]; then
    echo "ERROR: unsupported namespace '${kind}' in file name: '${fname}'" >&2
    echo "       expected '.env.prod.web3.<chain>.<network>[.template]'" >&2
    return 1
  fi

  # 5) allow-list 검증 (오타/미지원 조합 차단)
  case "${chain}" in
    optimism|base) ;;
    *)
      echo "ERROR: unsupported chain '${chain}' (allowed: optimism|base)" >&2
      return 1
      ;;
  esac
  case "${network}" in
    testnet|mainnet) ;;
    *)
      echo "ERROR: unsupported network '${network}' (allowed: testnet|mainnet)" >&2
      return 1
      ;;
  esac

  printf '%s/web3/%s/%s\n' "${SSM_ROOT}" "${chain}" "${network}"
}

SSM_PREFIX="$(derive_ssm_prefix "${ENV_FILE}")"

echo "▶ uploading ${ENV_FILE}  →  ${SSM_PREFIX}/*  (region=${REGION})"

uploaded=0
skipped_empty=0

while IFS='=' read -r key val || [[ -n "${key:-}" ]]; do
  # 빈 줄 / 주석 스킵
  [[ -z "${key}" || "${key}" =~ ^[[:space:]]*# ]] && continue
  # key 에 공백 끼어있으면 무시 (잘못된 라인 방어)
  [[ "${key}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue

  # SSM put-parameter 는 빈 값 거부 → skip
  if [[ -z "${val}" ]]; then
    echo "  · skip (empty value): ${key}"
    skipped_empty=$((skipped_empty + 1))
    continue
  fi

  # 민감도 분류.
  #   *KEY*      → KEY_B64 / API_KEY / ENCRYPTION_KEY / JWT_KEY 등 전부 포괄
  #   *SECRET*   → JWT_SECRET / *_SECRET 포괄
  #   *PASSWORD* → DB_PASSWORD 등
  #   DB_URL_PROD→ 이름에 KEY/SECRET 이 없어서 명시 예외
  case "${key}" in
    *SECRET*|*KEY*|*PASSWORD*|DB_URL_PROD)
      type="SecureString";;
    *) type="String";;
  esac

  key_id_flag=()
  if [[ "${type}" == "SecureString" ]]; then
    key_id_flag=(--key-id "${KMS_KEY_ALIAS}")
  fi

  name="${SSM_PREFIX}/${key}"

  aws ssm put-parameter \
    --region "${REGION}" \
    --name "${name}" \
    --value "${val}" \
    --type "${type}" \
    "${key_id_flag[@]}" \
    --overwrite > /dev/null

  aws ssm add-tags-to-resource \
    --region "${REGION}" \
    --resource-type Parameter \
    --resource-id "${name}" \
    --tags "Key=Project,Value=mztk" "Key=Env,Value=prod" > /dev/null

  echo "  + ${type}  ${name}"
  uploaded=$((uploaded + 1))
done < "${ENV_FILE}"

echo "✓ done: ${uploaded} uploaded, ${skipped_empty} skipped (empty) from ${ENV_FILE}"
