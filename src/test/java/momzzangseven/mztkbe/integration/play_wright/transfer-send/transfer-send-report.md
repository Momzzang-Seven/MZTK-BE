# Web3 Transfer Send Playwright E2E 보고서

## 개요

`transfer-send.spec.ts` 는 `TRANSFER_SEND` 성공 경로의 외부 RPC 통신을 검증합니다.

- transfer create 응답에서 즉시 sign payload 반환 여부 확인
- transfer read / generic execution-intent read 계약 일관성 확인
- 실제 execute 후 `CONFIRMED` 와 ERC20 잔액 이동 확인

테스트 파일:
- `src/test/java/momzzangseven/mztkbe/integration/play_wright/transfer-send/transfer-send.spec.ts`

## 커버 범위

| TC ID | 시나리오 | 예상 결과 |
|---|---|---|
| `TC-TRANSFER-PW-01` | `POST /users/me/transfers` | `AWAITING_SIGNATURE` intent, sign payload, execution mode 반환 |
| `TC-TRANSFER-PW-02` | `GET /users/me/transfers/{resourceId}` | create 응답과 동일한 execution contract 반환 |
| `TC-TRANSFER-PW-03` | create 후 execute | `CONFIRMED`, `txHash` 생성, sender/recipient 토큰 잔액이 정확히 이동 |

## 최신 검증 결과

- 개별 실행 기준 최신 결과: `3 passed`
- 검증 환경:
  - 실제 백엔드 서버
  - 실제 OP Sepolia RPC
  - 실제 reward token contract

## 전제 조건

- 로컬 백엔드 서버 실행
- 아래 설정 활성화
  - `web3.reward-token.enabled=true`
  - `web3.eip7702.enabled=true`
- `.env` 에 실제 RPC / EIP-712 / reward token 값 존재

## 환경 변수

```dotenv
WEB3_RPC_URL=
WEB3_EIP712_CHAIN_ID=
WEB3_EIP712_VERIFYING_CONTRACT=
WEB3_REWARD_TOKEN_CONTRACT_ADDRESS=
WEB3_REWARD_TOKEN_DECIMALS=18

TRANSFER_TEST_SENDER_PRIVATE_KEY=
TRANSFER_TEST_RECIPIENT_PRIVATE_KEY=
QNA_TEST_RESPONDER_PRIVATE_KEY=
TREASURY_PRIVATE_KEY=
```

## 실행 방법

```bash
cd src/test/java/momzzangseven/mztkbe/integration/play_wright

npx tsc --noEmit
npx playwright test transfer-send/transfer-send.spec.ts
```

## 작성 시점 메모

- 실행 모드는 환경에 따라 `EIP7702` 또는 `EIP1559` 가 될 수 있으므로 둘 다 처리합니다.
- 테스트는 고정 sender/recipient wallet 대신 매 실행마다 ephemeral wallet 을 생성합니다.
- ephemeral sender funding 은 아래 순서로 가능한 지갑을 선택합니다.
  1. `TRANSFER_TEST_SENDER_PRIVATE_KEY`
  2. `QNA_TEST_RESPONDER_PRIVATE_KEY`
  3. `TREASURY_PRIVATE_KEY`
- 선택된 funder 는 native gas 와 token balance 둘 다 충분해야 합니다.
- 최종 성공 기준은 `CONFIRMED` + `transaction.txHash` + ERC20 `balanceOf` 이동입니다.

## 운영성 주의사항

- transfer suite 는 funding 지갑의 native/token 잔고에 민감합니다.
- 따라서 개별 실행 green 을 기능 검증 기준으로 보고, 다른 on-chain suite 와의 묶음 실행은 운영성 smoke 수준으로 취급합니다.
