# 레벨업 보상 Playwright E2E 보고서

## 개요

`level-reward.spec.ts` 는 레벨업과 온체인 보상 지급 플로우를 Playwright로 검증합니다.

- 사전 조건 오류
- 정상 레벨업 응답 / 이력 / 레벨 반영
- 중복 레벨업 멱등 처리
- worker 처리 후 온체인 지급 성공

테스트 파일:
- `src/test/java/momzzangseven/mztkbe/integration/play_wright/level-reward/level-reward.spec.ts`

## 커버 범위

| TC ID | 시나리오 | 예상 결과 |
|---|---|---|
| `TC-LR-A-01` | 미인증 레벨업 요청 | `401` |
| `TC-LR-A-02` | 지갑 미연결 상태에서 레벨업 | `400`, `WALLET_003` |
| `TC-LR-A-03` | XP 부족 상태에서 레벨업 | `409`, `LEVEL_001` |
| `TC-LR-B-01` | 지갑 등록 + XP 충족 후 레벨업 | `200`, 초기 `rewardTxStatus` 반환 |
| `TC-LR-B-02` | 이력 조회 | 레벨업 이력 row 존재 |
| `TC-LR-B-03` | 레벨 조회 | `currentLevel=2` |
| `TC-LR-C-01` | 재요청 멱등 처리 | `409` |
| `TC-LR-C-02` | 연속 레벨업 | Lv1→2, Lv2→3 각각 독립 이력 생성 |
| `TC-LR-D-01` | worker 처리 후 온체인 지급 완료 | `rewardTxStatus=SUCCEEDED`, `txHash` 수신 |
| `TC-LR-D-02` | worker 처리 중 상태 전이 확인 | 초기 `CREATED`, 최종 `SUCCEEDED/UNCONFIRMED/FAILED_ONCHAIN` |

## 최신 검증 결과

- 개별 실행 기준 최신 결과: `10 passed`
- 개별 on-chain 지급 성공 확인
  - `0x81323e39640f64ee9838a85262b23c8eeb3cda5f7d8285cc19cd9c92aaf6a2bc`
  - `0xb07f3593505498c4265c4f33132d053420e6b2e75d68db7d4c1042569d929183`

## 전제 조건

- 로컬 백엔드 서버 실행
- 아래 설정 활성화
  - `web3.reward-token.enabled=true`
  - `web3.eip7702.enabled=true`
- `web3_treasury_keys` DB 레코드 존재
- reward transaction worker 활성화
- OP Sepolia RPC 접근 가능

## 환경 변수

```dotenv
BACKEND_URL=http://127.0.0.1:8080

DB_HOST=localhost
DB_PORT=5432
DB_NAME=mztk
DB_USER=
DB_PASSWORD=

WEB3_EIP712_DOMAIN_NAME=MomzzangSeven
WEB3_EIP712_DOMAIN_VERSION=1
WEB3_EIP712_CHAIN_ID=11155420
WEB3_EIP712_VERIFYING_CONTRACT=
```

## 실행 방법

```bash
cd src/test/java/momzzangseven/mztkbe/integration/play_wright

npx tsc --noEmit
npx playwright test level-reward/level-reward.spec.ts
```

## 작성 시점 메모

- XP는 API를 통해 누적하지 않고 DB를 직접 조정합니다.
- 현재 스펙은 `user_progress` 를 UPSERT 하도록 보강돼, 초기 row 가 없는 사용자도 안정적으로 처리합니다.
- worker 경로는 treasury/native 잔고 threshold 영향을 받습니다.
- 개별 실행 green 은 기능 검증 기준으로 충분하고, 다른 on-chain suite 와 묶었을 때의 treasury 고갈은 운영성 이슈로 분리해 봅니다.
