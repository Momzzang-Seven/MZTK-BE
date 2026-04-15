#!/bin/bash
# ─────────────────────────────────────────────────────────────
# refresh-env.sh
#
# 용도: EC2 상의 ~/apps/.env 를 SSM Parameter Store 에서 재생성한다.
#       web3 의 "chain × network" 두 축을 SSM 값 두 개만으로 스위칭한다.
#
# SSM 경로 규약:
#   /mztk/prod/active-chain                       → "optimism" | "base" | ...
#   /mztk/prod/active-network                     → "testnet"  | "mainnet"
#   /mztk/prod/common/<KEY>                       → 체인/네트워크 무관 공통값
#   /mztk/prod/web3/<chain>/<network>/<KEY>       → chain×network 별 web3 값
#
# 결과: ~/apps/.env 에 common + 선택된 (chain,network) 값이 flat 하게 병합된다.
#       ACTIVE_CHAIN / ACTIVE_NETWORK 마커도 함께 기록된다.
#
# 배포 스크립트 (deploy-prod.yml) 는 기존대로
#   docker run --env-file ~/apps/.env ...
# 를 그대로 쓰면 된다.
#
# 허용 조합 (예기치 못한 chainId 주입을 차단하기 위한 allow-list):
#   optimism  × testnet  → chainId 11155420
#   optimism  × mainnet  → chainId 10
#   base      × testnet  → chainId 84532
#   base      × mainnet  → chainId 8453
# ─────────────────────────────────────────────────────────────
set -euo pipefail

REGION="${AWS_REGION:-ap-northeast-2}"
PREFIX="/mztk/prod"
OUT="${HOME}/apps/.env"
TMP="${OUT}.new"

log() { printf '[refresh-env] %s\n' "$*"; }
die() { echo "ERROR: $*" >&2; exit 1; }

# ── 1) 활성 chain / network 결정 ──
CHAIN="$(
  aws ssm get-parameter \
    --region "$REGION" \
    --name "${PREFIX}/active-chain" \
    --query 'Parameter.Value' --output text
)"
NETWORK="$(
  aws ssm get-parameter \
    --region "$REGION" \
    --name "${PREFIX}/active-network" \
    --query 'Parameter.Value' --output text
)"

case "$CHAIN" in
  optimism|base) ;;
  *) die "unexpected active-chain value: '${CHAIN}' (allowed: optimism|base)";;
esac
case "$NETWORK" in
  testnet|mainnet) ;;
  *) die "unexpected active-network value: '${NETWORK}' (allowed: testnet|mainnet)";;
esac

# 허용된 chain×network 조합별 예상 chainId (가드용)
expected_chain_id() {
  case "${1}_${2}" in
    optimism_testnet) echo 11155420;;
    optimism_mainnet) echo 10;;
    base_testnet)     echo 84532;;
    base_mainnet)     echo 8453;;
    *) die "unsupported chain×network combo: ${1}×${2}";;
  esac
}
EXPECTED_CHAIN_ID="$(expected_chain_id "$CHAIN" "$NETWORK")"

log "active = ${CHAIN} × ${NETWORK} (expected chainId=${EXPECTED_CHAIN_ID})"

: > "$TMP"

# ── 2) SSM 경로 아래 모든 파라미터를 flat 하게 dump ──
dump_path() {
  local path="$1"
  aws ssm get-parameters-by-path \
    --region "$REGION" \
    --path "$path" \
    --with-decryption \
    --recursive \
    --query 'Parameters[*].[Name,Value]' \
    --output text \
  | awk -F'\t' '{ n=$1; sub(".*/","",n); printf "%s=%s\n", n, $2 }'
}

# ── 3) common 먼저 기록 ──
log "loading ${PREFIX}/common ..."
dump_path "${PREFIX}/common/" >> "$TMP"

# ── 4) 선택된 chain×network 값으로 이어쓰기 ──
#       동일 키가 common 과 겹치면 docker --env-file 는 "마지막 값이 이김"
log "loading ${PREFIX}/web3/${CHAIN}/${NETWORK} ..."
dump_path "${PREFIX}/web3/${CHAIN}/${NETWORK}/" >> "$TMP"

# ── 5) 런타임 식별용 마커 ──
printf 'ACTIVE_CHAIN=%s\n'   "$CHAIN"   >> "$TMP"
printf 'ACTIVE_NETWORK=%s\n' "$NETWORK" >> "$TMP"

# ── 6) chainId 무결성 가드 ──
ACTUAL_CHAIN_ID="$(grep -E '^WEB3_CHAIN_ID=' "$TMP" | tail -n1 | cut -d= -f2- || true)"
if [[ -z "$ACTUAL_CHAIN_ID" ]]; then
  die "WEB3_CHAIN_ID not set from SSM for ${CHAIN}×${NETWORK}"
fi
if [[ "$ACTUAL_CHAIN_ID" != "$EXPECTED_CHAIN_ID" ]]; then
  die "chainId mismatch for ${CHAIN}×${NETWORK}: expected=${EXPECTED_CHAIN_ID}, got=${ACTUAL_CHAIN_ID}"
fi

# ── 7) atomically 교체 ──
mv "$TMP" "$OUT"
chmod 600 "$OUT"
log "wrote ${OUT} (chain=${CHAIN}, network=${NETWORK}, chainId=${ACTUAL_CHAIN_ID})"
