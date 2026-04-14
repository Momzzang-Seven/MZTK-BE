#!/bin/bash

set -euo pipefail
#  - -e: 명령어 하나라도 실패하면 즉시 종료
#  - -u: 정의되지 않은 변수 참조 시 에러
#  - -o pipefail: 파이프(|) 중간 명령이 실패해도 에러 전파

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
ENV_PROD="${REPO_ROOT}/.env.prod"

if [[ ! -f "${ENV_PROD}" ]]; then
  echo "ERROR: ${ENV_PROD} not found." >&2
  exit 1
fi

while IFS='=' read -r key val; do
  # 구분자를 =로 설정하고, 각 줄을 key=value 형태로 파싱.
  [[ -z "$key" || "$key" =~ ^# ]] && continue
  # 빈 줄이거나 #으로 시작하는 주석 줄은 건너뜀.
  case "$key" in
  # key 이름으로 민감도 분류:
  #  - 이름에 SECRET, PASSWORD, KEY_B64, API_KEY 포함되거나 DB_URL_PROD, JWT_SECRET이면 → SecureString (KMS 암호화 저장)
  #  - 나머지 → String (평문 저장)
    *SECRET*|*KEY*|*PASSWORD*|*KEY_B64*|*API_KEY*|DB_URL_PROD|JWT_SECRET)
      type="SecureString";;
    *) type="String";;
  esac
  key_id_flag=""
  if [[ "$type" == "SecureString" ]]; then
    key_id_flag="--key-id alias/mztk-ssm-prod"
  fi
  aws ssm put-parameter \
    --name "/mztk/prod/$key" \
    --value "$val" \
    --type "$type" $key_id_flag \
    --overwrite \
    --region ap-northeast-2 > /dev/null
    #  - --name "/mztk/prod/$key": SSM 경로 계층 구조로 파라미터 이름 설정 (e.g. /mztk/prod/DB_URL)
    #  - --value "$val": 값 등록
    #  - --type "$type": String 또는 SecureString
    #  - --key-id alias/mztk-ssm-prod: SecureString 암호화에 사용할 KMS 키
    #  - --overwrite: 이미 존재하는 파라미터면 덮어씀
  echo "{ \"Name\": \"/mztk/prod/${key}\", \"Type\": \"${type}\" }"

  aws ssm add-tags-to-resource \
    --resource-type Parameter \
    --resource-id "/mztk/prod/$key" \
    --tags "Key=Project,Value=mztk" "Key=Env,Value=prod" \
    --region ap-northeast-2
    #  - add-tags-to-resource: 신규/기존 파라미터 모두에 태그를 안전하게 추가/업데이트함
done < "${ENV_PROD}"
# 입력 파일로 .env.prod를 while 루프에 공급.