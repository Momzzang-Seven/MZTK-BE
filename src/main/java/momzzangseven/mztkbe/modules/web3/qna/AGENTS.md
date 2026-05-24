# web3/qna — Sub-module Agent Guide

> 본 파일은 web3 QnA sub-module 전용 에이전트 가이드입니다.
> 상위 가이드: 루트 `AGENTS.md`, `src/main/AGENTS.md`.
> 이 파일은 git 에 tracked 됩니다 — `docs.local/` 처럼 ignore 되지 않음.

## 개요

QnA Web3 sub-module 은 on-chain `QnAEscrow` 컨트랙트와의 prepare → user-sign → execute
사이클을 담당한다. on-chain reward completion sync 와 7종 사용자 액션의 prepare flow
를 책임지며, server-sig 7 분기와 admin 2 분기를 모두 한 인코더에서 분기 처리한다.

## 9개 액션 — server-sig 7 vs admin 2 분기 매핑

| actionType | category | encoder | server sig | calldata 마지막 2 인자 |
|------------|----------|---------|------------|------------------------|
| `QNA_QUESTION_CREATE` | server-sig | 9-arg | yes | `signedAt, signature` |
| `QNA_QUESTION_UPDATE` | server-sig | 9-arg | yes | `signedAt, signature` |
| `QNA_QUESTION_DELETE` | server-sig | 9-arg | yes | `signedAt, signature` |
| `QNA_ANSWER_SUBMIT` | server-sig | 9-arg | yes | `signedAt, signature` |
| `QNA_ANSWER_UPDATE` | server-sig | 9-arg | yes | `signedAt, signature` |
| `QNA_ANSWER_DELETE` | server-sig | 9-arg | yes | `signedAt, signature` |
| `QNA_ANSWER_ACCEPT` | server-sig | 9-arg | yes | `signedAt, signature` |
| `QNA_ADMIN_SETTLE` | admin | 7-arg | no | — |
| `QNA_ADMIN_REFUND` | admin | 7-arg | no | — |

위 9 분기는 `QnaExecutionActionType` enum
(`modules/web3/qna/domain/vo/QnaExecutionActionType.java`) 에서 정의되며,
`QnaEscrowAbiEncoder` 가 7-arg / 9-arg 두 메서드로 split 한다 (server-sig 7 → 9-arg,
admin 2 → 7-arg). 신규 user 액션을 추가할 때는 9-arg switch 와 preimage sealed 패밀리
(`QnaServerSigPreimage`) 두 곳을 동시에 확장해야 한다.

## server-sig 흐름 (7 액션 공통)

prepare 시 `QnaExecutionDraftBuilderAdapter.build(...)` 안에서 다음 순서가 강제된다
(설계 doc §5-2 ordering invariant):

1. `SignQnaServerSigPort.sign(preimage)` — `QNA_SIGNER` 트레저리 키로 EIP-712 digest 서명
2. `QnaEscrowAbiEncoder.encode(..., signedAt, sigBytes)` — 9-arg overload 호출 (calldata 봉입)
3. `QnaContractCallSupport.prevalidateContractCall(...)` — `eth_call` 로 contract 측 검증
4. `QnaEscrowExecutionPayload` 에 `signedAt` / `signatureHex` 포함시켜 직렬화

이 순서를 어기면 사용자의 `submitSig` (`Mztk7702Execution`) 가 server sig 가 빠진
calldata 를 cover 하게 되어 contract 검증을 통과하지 못한다. 슬라이스 테스트
(`QnaExecutionDraftBuilderAdapterTest`) 가 mock signer `times(1)` + 동일 calldata 가
`unsignedTxSnapshot.data` / `calls[0].data` / prevalidation 인자 셋 모두에 흐르는지
검증한다.

## `QNA_SIGNER` 운영 룰

- alias: `qna-signer-treasury` (`TreasuryRole.QNA_SIGNER.toAlias()`).
- DB `web3_treasury_wallets` 에 row 1건, 다른 role 과 **다른** `kms_key_id` /
  `treasury_address`.
- `SignQnaServerSigAdapter` 는 row 가 없거나 `status != ACTIVE` 면
  `IllegalStateException` 으로 fail-fast → 5xx `INTERNAL_MISCONFIG`.
- rotation 절차: `docs.local/runbook/qna-signer-rotation.md`.

## 만료 / 재 prepare

`signatureExpiresAt = signedAt + properties.sigValidityDuration` (기본 900s = 15분).
응답 DTO `QnaExecutionIntentResult.signatureMeta` 에 노출 → FE 가 임박 시 재 prepare
유도. 자동 재 prepare / 만료 임박 알림은 별 follow-up 티켓 — 본 모듈 범위 외.

응답에는 두 종류의 만료 시각이 동시에 노출되며 **timeline 의 서로 다른 phase 에 적용**된다 —
capping / 통합하지 말 것:

- `executionIntent.expiresAt` (= `now + eip7702.authorization.ttl-seconds`, 기본 5분) — BE 가
  user-sign 을 수신할 deadline. 초과 시 `TransactionalExecuteExecutionIntentDelegate` 가
  `EXECUTION_INTENT_EXPIRED` 로 terminate. FE 사용자 가시 카운트다운은 이 값을 사용.
- `signatureMeta.signatureExpiresAt` (= `signedAt + sigValidityDuration`, 기본 15분) — broadcast
  된 tx 가 chain 에 포함되어야 할 deadline. 초과 시 on-chain `_verifyServerSig` 가
  `SignatureExpired` revert. FE 는 broadcast 후 mining 지연 모니터링용으로만 활용.

## lock-held 외부 호출 — Rule 1 예외 (calldata-bound server signature)

`prepare*` 7 액션은 `TransactionalQuestion/AnswerEscrowExecutionUseCase` decorator 의
트랜잭션 안에서 동작하며, update / delete / accept 는 projection 행에
PESSIMISTIC_WRITE lock 을 보유한다. 같은 lock window 안에서 KMS 호출 1회 + RPC 호출
2회 가 발생한다.

### 왜 `docs.shared/EXTERNAL_SYSTEM_SYNC.md` Rule 1 예외인가

Rule 1 default 인 "DB writes 만 트랜잭션 내, 외부 mutation 은 AFTER_COMMIT" 를 그대로
적용하면 `signedAt` / `signatureBytes` 가 calldata `Uint256` 에 직접 봉입되는 본 흐름이
무너진다. AFTER_COMMIT 으로 sign 을 미루면 DB 에는 calldata 가 미완성 (server-sig 가
누락된 7-arg) 으로 저장되고, broadcast 시 9-arg contract 시그너처와 어긋나 즉시 revert.
즉 KMS sign 이 **calldata 직렬화 직전** 시점에 동기 호출되어야 하는 ordering 제약이
설계의 기둥이다 (`QnaEscrowAbiEncoder.java:21-39` docstring, `QnaExecutionDraftBuilderAdapter:62-82`).

### Trade-off

KMS sign 자체는 **idempotent / read-only** — 새 키를 만들지도, 외부 상태를 변경하지도
않는 EIP-712 digest signing 1회. DB rollback 시 KMS 측에는 어떤 mutation 도 남지 않으며
"orphan signature" 는 트래픽 측면에서만 의미가 있다. Rule 2 의 `TransactionSynchronization`
cleanup 등록도 필요 없다 (정리할 외부 상태가 없음). Rule 5 ("idempotent recovery") 가
trivially 충족.

### 운영 모니터링

KMS 5xx / throttle 비율은 `docs.local/runbook/qna-prepare-kms-degradation.md` 의 임계값을
따른다. cascading 장애 가능성 (KMS 응답 지연 → tx hold → DB lock hold) 이 실재하므로,
운영 환경에서는 KMS `Latency` / `ThrottledRequests` / `SigningFailures` 메트릭을 prepare
QPS 와 함께 추적한다.

## 테스트 진입점

- `SignQnaServerSigAdapterTest` — golden vector × 7 typehash + domain separator +
  fail-fast.
- `QnaEscrowAbiEncoderTest` — 9-arg overload 7 분기 + admin rejection + defensive copy.
- `QnaExecutionDraftBuilderAdapterTest` — 7 액션 slice + prevalidation alignment.
- `QuestionEscrowExecutionServiceTest` / `AnswerEscrowExecutionServiceTest` — recover
  회귀.

## 본 sub-module 이 절대 건드리지 않는 영역

- `modules/marketplace/**`, `modules/web3/marketplace/**` (별 담당자, 별 티켓).
- `LegacyEscrowTransactionDisabledAdapter`, `EscrowDispatchEvent*`, `SubmitEscrowTransactionPort`.

## 관련 문서

- 설계: `docs.local/design/MOM-393-add-serversig-to-payload/MOM-393-add-serversig-to-payload.md`
- 분석: `docs.local/analysis/post-question-create-eip7702-eip1559-server-kms-signing-flow.md`
- contract: `docs.local/abi&sol/QnAEscrow.sol`
- runbook: `docs.local/runbook/qna-signer-rotation.md`,
  `docs.local/runbook/qna-prepare-kms-degradation.md`
