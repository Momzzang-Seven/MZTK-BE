# QnA Escrow Playwright E2E 보고서

## 개요

`qna-escrow.spec.ts` 는 QnA Escrow 공개 write 성공 경로를 실제 HTTP + RPC 기준으로 검증합니다.

- 질문 생성 응답의 execution intent / signRequest 계약 확인
- generic execution intent 조회/실행 API 확인
- 실제 OP Sepolia execute 이후 `CONFIRMED` 와 read-back 반영 확인

이 스펙은 아래 7개 action 을 모두 커버합니다.

1. `QNA_QUESTION_CREATE`
2. `QNA_QUESTION_UPDATE`
3. `QNA_QUESTION_DELETE`
4. `QNA_ANSWER_SUBMIT`
5. `QNA_ANSWER_UPDATE`
6. `QNA_ANSWER_DELETE`
7. `QNA_ANSWER_ACCEPT`

테스트 파일:
- `src/test/java/momzzangseven/mztkbe/integration/play_wright/qna-escrow/qna-escrow.spec.ts`

## 커버 범위

| TC ID | 시나리오 | 예상 결과 |
|---|---|---|
| `TC-QNA-A-01` | 인증 없이 execution intent 조회 | `401` |
| `TC-QNA-A-02` | 인증 없이 execution intent 실행 | `401` |
| `TC-QNA-A-03` | 인증 없이 질문 작성 | `401` |
| `TC-QNA-B-01` | 질문 작성 | `201`, `QNA_QUESTION_CREATE`, `AWAITING_SIGNATURE`, mode별 `signRequest` 포함 |
| `TC-QNA-C-01` | 질문 생성 후 GET intent 조회 | `200`, create 응답과 동일한 execution contract |
| `TC-QNA-C-02` | 존재하지 않는 intent 조회 | `4xx` |
| `TC-QNA-D-01` | question execute | `202`, `PENDING_ONCHAIN` 또는 `CONFIRMED` |
| `TC-QNA-D-02` | 만료된 question-create intent execute 후 recover-create | execute `409`/`WEB3_013`, intent `EXPIRED` 저장, 새 create intent `AWAITING_SIGNATURE` |
| `TC-QNA-E-01` | confirmed question 후 answer create | `201`, `QNA_ANSWER_SUBMIT`, `AWAITING_SIGNATURE` |
| `TC-QNA-F-01` | confirmed question update | execute 후 `CONFIRMED`, post detail content 갱신 |
| `TC-QNA-F-02` | confirmed question delete | execute 후 `CONFIRMED`, post detail 제거 |
| `TC-QNA-G-01` | confirmed answer submit | execute 후 `CONFIRMED`, answer row 유지 |
| `TC-QNA-G-02` | confirmed answer update | execute 후 `CONFIRMED`, answer content 갱신 |
| `TC-QNA-G-03` | confirmed answer delete | execute 후 `CONFIRMED`, answer row 제거 |
| `TC-QNA-G-04` | confirmed answer accept | execute 후 `CONFIRMED`, question solved + answer accepted 반영 |

## 최신 검증 결과

- 기존 개별 실행 기준 최신 결과: `14 passed` (`TC-QNA-D-02` 추가 전)
- `TC-QNA-D-02`: 스펙 추가 후 아직 실외부 API 기준 재실행하지 않음
- 검증 환경:
  - 실제 백엔드 서버
  - 실제 PostgreSQL
  - 실제 OP Sepolia RPC
  - funded asker / responder wallet

## 전제 조건

- 로컬 백엔드 서버 실행
- 아래 설정 활성화
  - `web3.reward-token.enabled=true`
  - `web3.eip7702.enabled=true`
- 실제 RPC 접근 가능
- 실제 QnA Escrow 컨트랙트 주소 설정
- 실제 reward token 컨트랙트 주소 설정
- `QNA_TEST_ASKER_PRIVATE_KEY`
  - reward token balance 보유
  - approve tx 를 보낼 native gas 보유
- `QNA_TEST_RESPONDER_PRIVATE_KEY`
  - answer submit/update/delete/accept 를 위한 별도 responder 키
  - 소량의 native gas 가 있는 편이 안전

## 환경 변수

```dotenv
BACKEND_URL=http://127.0.0.1:8080
WEB3_RPC_URL=
WEB3_EIP712_DOMAIN_NAME=MomzzangSeven
WEB3_EIP712_DOMAIN_VERSION=1
WEB3_EIP712_CHAIN_ID=11155420
WEB3_EIP712_VERIFYING_CONTRACT=
WEB3_ESCROW_QNA_CONTRACT_ADDRESS=
WEB3_REWARD_TOKEN_CONTRACT_ADDRESS=
WEB3_REWARD_TOKEN_DECIMALS=18
QNA_TEST_ASKER_PRIVATE_KEY=
QNA_TEST_RESPONDER_PRIVATE_KEY=
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
npx playwright test qna-escrow/qna-escrow.spec.ts
```

## 작성 시점 메모

- 실행 모드는 환경에 따라 `EIP7702` 또는 `EIP1559` 가 선택될 수 있으므로 둘 다 허용합니다.
- `EIP7702` 는 raw digest 서명 기준으로 맞춥니다.
- `EIP1559` fallback 이 선택되면 `signedRawTransaction` 을 직접 생성해 execute 엔드포인트에 제출합니다.
- nonce stale 재현 가능성이 있어, 스펙 내부에서 일부 실행은 retry/recover 경로를 포함합니다.

## 운영성 주의사항

- 이 스펙의 green 은 QnA 공개 write 성공 경로 검증 기준으로 충분합니다.
- 다른 on-chain suite 와 한 번에 묶어 돌리는 것은 운영성 smoke test 성격이며, 개별 suite green 이 더 중요한 기준입니다.
