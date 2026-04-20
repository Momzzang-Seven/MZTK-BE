# QnA Auto Accept Playwright E2E 보고서

## 개요

`qna-auto-accept.spec.ts` 는 `QNA_ADMIN_SETTLE` 자동 채택 경로의 마지막 구간을 검증합니다.

- 실제 백엔드 서버
- 실제 PostgreSQL
- 실제 OP Sepolia RPC
- 실제 internal issuer scheduler

즉 Java E2E가 검증하는 로컬 상태 전이 위에, `DB seed -> internal issuer claim -> RPC submit/receipt 반영`까지 붙여서 확인하는 문서입니다.

테스트 파일:
- `src/test/java/momzzangseven/mztkbe/integration/play_wright/qna-auto-accept/qna-auto-accept.spec.ts`

## 커버 범위

| TC ID | 시나리오 | 예상 결과 |
|---|---|---|
| `TC-QAA-PW-01` | overdue 질문/답변과 `QNA_ADMIN_SETTLE` intent 를 DB에 seed 한 뒤 internal issuer 가 intent 를 진행 | `AWAITING_SIGNATURE` 를 벗어난다. `SIGNED`, `PENDING_ONCHAIN`, `FAILED_ONCHAIN`, `CONFIRMED` 중 하나면 통과 |

## 최신 검증 결과

- 개별 실행 기준 최신 결과: `1 passed`
- 검증 기준:
  - intent 가 `AWAITING_SIGNATURE` 에 고정되지 않을 것
  - on-chain 제출까지 진행되면 `transaction.id`, `txHash` 가 생성될 것
- 참고:
  - 이 스펙은 `CONFIRMED`만 성공으로 보지 않습니다.
  - 자동 채택의 목적상 external communication 경로 검증이 핵심이므로, `FAILED_ONCHAIN` 이어도 `AWAITING_SIGNATURE` 탈출과 tx 생성이 확인되면 RPC/internals 는 정상 동작으로 봅니다.

## 전제 조건

- 로컬 백엔드 서버 실행
- 아래 설정 활성화
  - `web3.reward-token.enabled=true`
  - `web3.eip7702.enabled=true`
  - `web3.execution.internal-issuer.enabled=true`
  - `web3.qna.auto-accept.enabled=true`
- `.env` 에 실제 RPC / chain / escrow 값 존재
- `web3_treasury_keys` 에 `EXECUTION_SIGNER_ALIAS` signer row 존재

## 환경 변수

```dotenv
WEB3_RPC_URL=
WEB3_ESCROW_QNA_CONTRACT_ADDRESS=
WEB3_CHAIN_ID=11155420
EXECUTION_SIGNER_ALIAS=sponsor-treasury
DB_HOST=localhost
DB_PORT=5432
DB_NAME=mztk
DB_USER=
DB_PASSWORD=
```

## 실행 방법

```bash
cd src/test/java/momzzangseven/mztkbe/integration/play_wright

npx tsc --noEmit
npx playwright test qna-auto-accept/qna-auto-accept.spec.ts
```

## 작성 시점 메모

- 이 스펙은 실제 `7일`을 기다리지 않습니다.
  - overdue 후보를 만들기 위해 질문/답변 `created_at` 을 과거 시점으로 seed 합니다.
- 현재 목적은 `AUTO_ACCEPT_DELAY` 스케줄 시간이 아니라, overdue 대상이 생겼을 때 internal issuer 와 on-chain submit 이 정상 동작하는지 검증하는 것입니다.
- 이번 작업에서 internal issuer poison-pill 버그를 수정했습니다.
  - 종료 훅 실패가 intent terminal 전이를 롤백시키지 않도록 보강
  - `rollbackPendingAccept()` 는 `REQUIRES_NEW` 로 분리

## 운영성 주의사항

- 이 스펙은 개별 실행 green 을 기준으로 봅니다.
- 다른 on-chain Playwright와 한 번에 묶어 실행하면 shared signer/native 잔고에 따라 결과가 흔들릴 수 있습니다.
- 따라서 기능 검증 기준의 SSOT는 개별 suite green 입니다.
