#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────
# refresh-and-restart.sh
#
# 1) 로컬의 .env.prod.common + .env.prod.web3.<chain>.<network>.template 을
#    ssm-upsert.sh 로 SSM Parameter Store 에 푸시한다.
# 2) /mztk/prod/active-chain, /mztk/prod/active-network 스위치 값을
#    현재 chain×network 로 맞춘다.
# 3) refresh-env.sh 를 EC2 로 scp 한 뒤,
#    원격에서 실행해 ~/apps/.env 를 재생성한다.
# 4) Docker 컨테이너를 pull → stop → run → 헬스체크.
#
# 사전 조건:
#   - AWS CLI 로그인 (aws configure / 환경변수)
#   - 리포지토리 루트의 .env.ec2 에 EC2 접속 정보
#     (EC2_HOST, EC2_USER, EC2_KEY_PATH, DOCKER_HUB_USERNAME,
#      DOCKER_HUB_ACCESS_TOKEN)
#
# Usage:
#   ./scripts/ci/refresh-and-restart.sh                      # optimism testnet (기본)
#   CHAIN=optimism NETWORK=testnet AWS_REGION=ap-northeast-2 ./scripts/ci/refresh-and-restart.sh
#
# 나중에 base 로 옮길 때는 CHAIN=base 로만 덮어쓰면 된다.
# ─────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# 지금 단계에선 optimism × testnet 만 쓴다. 나중에 base 로 옮길 때만 덮어쓰면 됨.
CHAIN="${CHAIN:-optimism}"
NETWORK="${NETWORK:-testnet}"

AWS_REGION_DEFAULT="ap-northeast-2"
export AWS_REGION="${AWS_REGION:-${AWS_REGION_DEFAULT}}"

SSM_ROOT="/mztk/prod"

COMMON_FILE="${REPO_ROOT}/.env.prod.common"
WEB3_FILE="${REPO_ROOT}/.env.prod.web3.${CHAIN}.${NETWORK}.template"
REFRESH_ENV_LOCAL="${REPO_ROOT}/scripts/ci/refresh-env.sh"

# ─────────────────────────────────────────────
# 입력 파일 검증
# ─────────────────────────────────────────────
for f in "${COMMON_FILE}" "${WEB3_FILE}" "${REFRESH_ENV_LOCAL}"; do
  if [[ ! -f "$f" ]]; then
    echo "ERROR: required file not found: $f" >&2
    exit 1
  fi
done

# ─────────────────────────────────────────────
# EC2 접속 설정 로드 (.env.ec2)
# ─────────────────────────────────────────────
EC2_CONFIG="${REPO_ROOT}/.env.ec2"
if [[ ! -f "${EC2_CONFIG}" ]]; then
  echo "ERROR: ${EC2_CONFIG} not found." >&2
  echo "       Copy .env.ec2.example to .env.ec2 and fill in your EC2 details." >&2
  exit 1
fi

while IFS='=' read -r key val || [[ -n "${key:-}" ]]; do
  [[ -z "$key" || "$key" =~ ^# ]] && continue
  export "$key"="${val}"
done < "${EC2_CONFIG}"

for var in EC2_HOST EC2_USER EC2_KEY_PATH DOCKER_HUB_USERNAME DOCKER_HUB_ACCESS_TOKEN; do
  if [[ -z "${!var:-}" ]]; then
    echo "ERROR: ${var} is not set in ${EC2_CONFIG}" >&2
    exit 1
  fi
done

EC2_KEY_PATH="${EC2_KEY_PATH/#\~/$HOME}"
if [[ ! -f "${EC2_KEY_PATH}" ]]; then
  echo "ERROR: EC2 key file not found: ${EC2_KEY_PATH}" >&2
  exit 1
fi

# ─────────────────────────────────────────────
# Step 1: SSM 업로드 (common + web3/<chain>/<network>)
# ─────────────────────────────────────────────
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "[1/4] Uploading env files → SSM"
echo "      chain × network = ${CHAIN} × ${NETWORK}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

bash "${SCRIPT_DIR}/ssm-upsert.sh" "${COMMON_FILE}"
echo
bash "${SCRIPT_DIR}/ssm-upsert.sh" "${WEB3_FILE}"

# ─────────────────────────────────────────────
# Step 2: active-chain / active-network 스위치 갱신
# ─────────────────────────────────────────────
echo
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "[2/4] Setting active selectors in SSM"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

aws ssm put-parameter \
  --region "${AWS_REGION}" \
  --name "${SSM_ROOT}/active-chain" \
  --value "${CHAIN}" \
  --type String --overwrite > /dev/null
echo "  + String  ${SSM_ROOT}/active-chain = ${CHAIN}"

aws ssm put-parameter \
  --region "${AWS_REGION}" \
  --name "${SSM_ROOT}/active-network" \
  --value "${NETWORK}" \
  --type String --overwrite > /dev/null
echo "  + String  ${SSM_ROOT}/active-network = ${NETWORK}"

# ─────────────────────────────────────────────
# Step 3: EC2 로 refresh-env.sh 복사
# ─────────────────────────────────────────────
echo
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "[3/4] scp refresh-env.sh → EC2"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

scp -q -i "${EC2_KEY_PATH}" \
    -o StrictHostKeyChecking=no \
    "${REFRESH_ENV_LOCAL}" \
    "${EC2_USER}@${EC2_HOST}:~/apps/refresh-env.sh"
echo "  ✓ refresh-env.sh uploaded"

ssh -T -i "${EC2_KEY_PATH}" \
    -o StrictHostKeyChecking=no \
    "${EC2_USER}@${EC2_HOST}" 'chmod +x ~/apps/refresh-env.sh'

# ─────────────────────────────────────────────
# Step 4: EC2 에서 env 재생성 + 컨테이너 재기동
# ─────────────────────────────────────────────
echo
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "[4/4] Refresh .env and restart container on ${EC2_USER}@${EC2_HOST}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# heredoc 이 unquoted(<< REMOTE) 이어야 로컬 변수(DOCKER_HUB_*)가 확장된다.
# EC2 측 루프 변수($i)는 \$i 로 이스케이프.
ssh -T -i "${EC2_KEY_PATH}" \
    -o StrictHostKeyChecking=no \
    -o ConnectTimeout=10 \
    "${EC2_USER}@${EC2_HOST}" << REMOTE

set -euo pipefail
cd ~/apps

# ── 1) SSM 에서 chain×network 조합에 맞는 .env 재생성 ──
./refresh-env.sh

# ── 2) Docker Hub 로그인 (Private Repo 접근용) ──
echo "${DOCKER_HUB_ACCESS_TOKEN}" | docker login -u "${DOCKER_HUB_USERNAME}" --password-stdin

# ── 3) 최신 이미지 pull ──
docker pull ${DOCKER_HUB_USERNAME}/mztk-be:latest

# ── 4) 기존 컨테이너 중지/제거 ──
docker stop mztk-prod || true
docker rm   mztk-prod || true

# ── 5) 새 컨테이너 실행 ──
docker run -d \
  --name mztk-prod \
  --restart unless-stopped \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  --env-file ~/apps/.env \
  ${DOCKER_HUB_USERNAME}/mztk-be:latest

docker image prune -f

# ── 6) 헬스체크 ──
echo ""
echo "Waiting for application to start (20s)..."
sleep 20

echo "Starting health check (max 60s, 12 attempts)..."
for i in {1..12}; do
  if curl -f -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo ""
    echo "Health check passed!"
    curl -s http://localhost:8080/actuator/health
    echo ""
    # 배포된 실제 chain×network 마커도 함께 찍어줌
    grep -E '^(ACTIVE_CHAIN|ACTIVE_NETWORK|WEB3_CHAIN_ID)=' ~/apps/.env || true
    exit 0
  fi

  if [ "\$i" -eq 12 ]; then
    echo ""
    echo "ERROR: Health check failed after 60 seconds."
    echo "Last 50 lines of container logs:"
    docker logs --tail 50 mztk-prod
    exit 1
  fi

  echo "Attempt \$i/12 failed. Retrying in 5s..."
  sleep 5
done
REMOTE

echo
echo "Done. chain=${CHAIN}, network=${NETWORK} deployed."
