# QnA Auto Accept Playwright E2E 보고서

## 개요

`qna-auto-accept.spec.ts` 는 자동 채택 기능 중 외부 RPC 통신이 개입되는 구간을 Playwright로 검증합니다.

- Java E2E: overdue 후보 선정, `PENDING_ACCEPT`, 성공/실패 후 로컬 DB 상태 전이
- Playwright: 실제 백엔드 서버 + 실제 PostgreSQL + 실제 RPC를 사용한 `QNA_ADMIN_SETTLE` internal issuer 진행 여부

즉 이 문서는 Java E2E를 대체하지 않고, 외부 서버 통신이 포함되는 마지막 구간을 별도로 보강합니다.

테스트 파일:
- `src/test/java/momzzangseven/mztkbe/integration/play_wright/qna-auto-accept/qna-auto-accept.spec.ts`

## 커버 범위

| TC ID | 시나리오 | 예상 결과 |
|---|---|---|
| `TC-QAA-PW-01` | overdue 질문/답변과 `QNA_ADMIN_SETTLE` intent 를 DB에 seed 한 뒤 internal issuer 가 실제 RPC를 통해 intent 를 진행 | `AWAITING_SIGNATURE` 이외 상태(`SIGNED`, `PENDING_ONCHAIN`, `FAILED_ONCHAIN`, `CONFIRMED`)로 전이. on-chain 제출 단계까지 진행되면 transaction id / txHash 도 확인 |

## 전제 조건

- 로컬 백엔드 서버가 실행 중이어야 합니다.
- 서버 설정에서 아래 값이 활성화되어 있어야 합니다.
  - `web3.reward-token.enabled=true`
  - `web3.eip7702.enabled=true`
  - `web3.execution.internal-issuer.enabled=true`
- `.env` 에 실제 RPC와 QnA escrow 컨트랙트 주소가 있어야 합니다.
- `web3_treasury_keys` 테이블에 `EXECUTION_SIGNER_ALIAS` 에 해당하는 signer row 가 있어야 합니다.

## 환경 변수

`.env.example` 에 아래 값을 추가했습니다.

```dotenv
WEB3_RPC_URL=
WEB3_ESCROW_QNA_CONTRACT_ADDRESS=
WEB3_CHAIN_ID=11155111
EXECUTION_SIGNER_ALIAS=sponsor-treasury
```

## 실행 방법

```bash
cd src/test/java/momzzangseven/mztkbe/integration/play_wright

# 타입 검증
npx tsc --noEmit

# QnA auto-accept external flow
npx playwright test qna-auto-accept/qna-auto-accept.spec.ts
```

## 작성 시점 메모

- 이 스크립트는 실제 RPC nonce / fee 를 조회해서 unsigned tx snapshot 을 seed 합니다.
- 질문/답변의 onchain 존재 여부까지 강제하지는 않습니다.
- 목적은 internal issuer 가 실제 외부 RPC를 통해 `QNA_ADMIN_SETTLE` intent 를 진행시키는지 확인하는 것입니다.
- 최종적으로 tx 가 체인에서 revert 되더라도, `AWAITING_SIGNATURE` 에서 빠져나가고 transaction summary 가 생성되면 external communication 자체는 성공한 것으로 봅니다.
