# QnA Escrow Playwright E2E 보고서

## 개요

`qna-escrow.spec.ts` 는 QnA Escrow의 EIP-7702 write flow를 HTTP 레이어에서 검증합니다.

- 질문 생성 응답이 즉시 `web3.executionIntent` / `signRequest` 를 반환하는지 확인
- `GET /users/me/web3/execution-intents/{id}` 응답 계약 확인
- funded test wallet 이 준비된 경우 실제 RPC를 통해 `execute` 까지 진행
- 질문이 실제로 `CONFIRMED` 된 뒤 답변 생성 intent 가 이어지는지 확인

즉 이 스펙은 단순 DTO 확인이 아니라, `질문 생성 -> intent 조회 -> execute -> confirm -> 답변 intent 생성` 까지 점진적으로 커버합니다.

테스트 파일:
- `src/test/java/momzzangseven/mztkbe/integration/play_wright/qna-escrow/qna-escrow.spec.ts`

## 커버 범위

| TC ID | 시나리오 | 예상 결과 |
|---|---|---|
| `TC-QNA-A-01` | 인증 없이 execution intent 조회 | `401` |
| `TC-QNA-A-02` | 인증 없이 execution intent 실행 | `401` |
| `TC-QNA-A-03` | 인증 없이 질문 작성 | `401` |
| `TC-QNA-B-01` | funded asker wallet + allowance 준비 후 질문 작성 | `201`, `QNA_QUESTION_CREATE`, `AWAITING_SIGNATURE`, `signRequest` 포함 |
| `TC-QNA-C-01` | 질문 생성 후 GET intent 조회 | `200`, POST 응답과 동일한 intent/signRequest 확인 |
| `TC-QNA-C-02` | 존재하지 않는 intent 조회 | `4xx` |
| `TC-QNA-D-01` | question intent execute | `202`, `PENDING_ONCHAIN`, transaction summary 포함 |
| `TC-QNA-E-01` | question `CONFIRMED` 후 answer create | `201`, `QNA_ANSWER_SUBMIT`, `AWAITING_SIGNATURE` |

## 전제 조건

### 공통

- 로컬 백엔드 서버가 실행 중이어야 합니다.
- `web3.reward-token.enabled=true`
- `web3.eip7702.enabled=true`

### 질문 create/execute 실플로우

- 실제 RPC 접근 가능
- 실제 QnA Escrow 컨트랙트 주소 설정
- 실제 reward token 컨트랙트 주소 설정
- `QNA_TEST_ASKER_PRIVATE_KEY` 지갑이 아래를 만족해야 함
  - reward token balance 보유
  - approve tx 를 보낼 native gas 보유

### 질문 confirm 후 answer create

- 위 조건에 더해 `QNA_TEST_RESPONDER_PRIVATE_KEY` 필요
- receipt worker / sync 흐름이 활성화되어 question intent 가 `CONFIRMED` 까지 가야 함

## 환경 변수

`.env.example` 에 아래 항목이 반영되어 있습니다.

```dotenv
BACKEND_URL=http://127.0.0.1:8080
WEB3_RPC_URL=
WEB3_EIP712_DOMAIN_NAME=MomzzangSeven
WEB3_EIP712_DOMAIN_VERSION=1
WEB3_EIP712_CHAIN_ID=
WEB3_EIP712_VERIFYING_CONTRACT=
WEB3_ESCROW_QNA_CONTRACT_ADDRESS=
WEB3_REWARD_TOKEN_CONTRACT_ADDRESS=
WEB3_REWARD_TOKEN_DECIMALS=18
QNA_TEST_ASKER_PRIVATE_KEY=
QNA_TEST_RESPONDER_PRIVATE_KEY=
DATABASE_URL=
DB_HOST=localhost
DB_PORT=5432
DB_NAME=
DB_USER=
DB_PASSWORD=
```

## 실행 방법

```bash
cd src/test/java/momzzangseven/mztkbe/integration/play_wright

# 타입 검증
npx tsc --noEmit

# QnA Escrow scenario only
npx playwright test qna-escrow/qna-escrow.spec.ts

# Suite 별 실행 예시
npx playwright test qna-escrow/qna-escrow.spec.ts --grep "Suite A"
npx playwright test qna-escrow/qna-escrow.spec.ts --grep "TC-QNA-D-01"
```

## 작성 시점 메모

- 기존 placeholder였던 “intentId 노출 엔드포인트가 없다”는 가정은 더 이상 맞지 않습니다.
  - 현재 질문/답변 create 응답은 이미 `data.web3.executionIntent.id` 를 포함합니다.
- `execute` 서명은 `signMessage`가 아니라 raw digest 서명 기준으로 맞춰야 합니다.
  - backend verifier 가 prefixed message recovery 가 아니라 raw digest recovery 를 사용하기 때문입니다.
- ask/responder wallet 은 고정 테스트키를 재사용하므로, 스펙 내부에서 기존 `ACTIVE` wallet linkage 를 `UNLINKED` 로 정리한 뒤 재등록합니다.
- 질문 on-chain confirm 이후 답변 create 를 검증하도록 추가해, 단순 question intent 생성보다 더 실제적인 시나리오로 보강했습니다.

## 테스트 결과

> 실행 일시: 미실행
> 현재 단계: 시나리오 보강 + 타입 검증 예정

실제 실행 결과는 RPC / funded wallet 환경이 준비된 뒤 업데이트합니다.
