#!/usr/bin/env bash
# refresh-and-restart.sh
#
# 1. .env.prod → SSM Parameter Store 최신화 (ssm-upsert.sh)
# 2. EC2에 SSH 접속 → refresh-env.sh + restart-prod.sh 순차 실행
#
# 사전 조건:
#   - AWS CLI 설정 완료 (aws configure 또는 환경변수)
#   - 리포지토리 루트의 .env.ec2 파일에 EC2 접속 정보 기입
#     (없으면 .env.ec2.example을 복사하여 작성)
#
# Usage:
#   ./scripts/ci/refresh-and-restart.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# ─────────────────────────────────────────────
# EC2 접속 설정 로드
# ─────────────────────────────────────────────
EC2_CONFIG="${REPO_ROOT}/.env.ec2"

if [[ ! -f "${EC2_CONFIG}" ]]; then
  echo "ERROR: ${EC2_CONFIG} not found." >&2
  echo "       Copy .env.ec2.example to .env.ec2 and fill in your EC2 details." >&2
  exit 1
fi

# .env.ec2에서 EC2_HOST, EC2_USER, EC2_KEY_PATH 를 로드
# (주석 및 빈 줄 제외)
while IFS='=' read -r key val || [[ -n "$key" ]]; do
  [[ -z "$key" || "$key" =~ ^# ]] && continue
  # tilde expansion 을 위해 eval 없이 직접 export
  export "$key"="${val}"
done < "${EC2_CONFIG}"

# 필수 변수 검증
for var in EC2_HOST EC2_USER EC2_KEY_PATH DOCKER_HUB_USERNAME DOCKER_HUB_ACCESS_TOKEN; do
  if [[ -z "${!var:-}" ]]; then
    echo "ERROR: ${var} is not set in ${EC2_CONFIG}" >&2
    exit 1
  fi
done

# tilde를 실제 경로로 확장
EC2_KEY_PATH="${EC2_KEY_PATH/#\~/$HOME}"

if [[ ! -f "${EC2_KEY_PATH}" ]]; then
  echo "ERROR: EC2 key file not found: ${EC2_KEY_PATH}" >&2
  exit 1
fi

# ─────────────────────────────────────────────
# Step 1: SSM 최신화
# ─────────────────────────────────────────────
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "[1/2] Uploading .env.prod → SSM Parameter Store..."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

bash "${SCRIPT_DIR}/ssm-upsert.sh"

echo ""
echo "SSM update complete."

# ─────────────────────────────────────────────
# Step 2: EC2 접속 → env 갱신 + 컨테이너 재기동 + 헬스체크
# ─────────────────────────────────────────────
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "[2/2] Connecting to EC2 (${EC2_USER}@${EC2_HOST})..."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# DOCKER_HUB_USERNAME, DOCKER_HUB_ACCESS_TOKEN 은 로컬에서 치환되어 EC2로 전달됨.
# deploy-prod.yml 의 GitHub Actions secrets 치환 방식과 동일한 효과.
# 따라서 heredoc 이 unquoted(<< REMOTE) 이어야 로컬 변수가 확장된다.
# EC2 측 루프 변수($i)는 \$i 로 이스케이프해 원격에서 평가되도록 한다.
ssh -i "${EC2_KEY_PATH}" \
    -o StrictHostKeyChecking=no \
    -o ConnectTimeout=10 \
    "${EC2_USER}@${EC2_HOST}" << REMOTE

cd ~/apps

# ── env 갱신 ────────────────────────────────
./refresh-env.sh

# ── Docker Hub 로그인 (Private Repository) ──
echo "${DOCKER_HUB_ACCESS_TOKEN}" | docker login -u "${DOCKER_HUB_USERNAME}" --password-stdin

# ── 최신 이미지 pull ─────────────────────────
docker pull ${DOCKER_HUB_USERNAME}/mztk-be:latest

# ── 기존 컨테이너 중지·제거 ──────────────────
docker stop mztk-prod || true
docker rm   mztk-prod || true

# ── 새 컨테이너 실행 ─────────────────────────
docker run -d \
  --name mztk-prod \
  --restart unless-stopped \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  --env-file ~/apps/.env \
  ${DOCKER_HUB_USERNAME}/mztk-be:latest

# ── 오래된 이미지 정리 ───────────────────────
docker image prune -f

# ── 헬스체크 ────────────────────────────────
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

echo ""
echo "Done. Container is healthy."
