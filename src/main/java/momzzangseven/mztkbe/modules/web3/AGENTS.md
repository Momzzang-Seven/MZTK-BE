# web3/ — 블록체인 모듈 SSoT

> 본 파일은 web3 11 sub-module 의 책임 / API 청사진. 상위 module 개요는 `../AGENTS.md`.
> 외부 시스템 (RPC / KMS / Optimism on-chain) 동기화 규칙은 `docs.shared/EXTERNAL_SYSTEM_SYNC.md`.
> 모든 응답은 `ApiResponse<T>` 래퍼.

## 구성

```
wallet/ challenge/ signature/ transaction/ transfer/ execution/
qna/ eip7702/ treasury/ shared/ admin/
```

### Feature flag (대부분 `@ConditionalOnProperty`)

| Property | 영향 |
|----------|------|
| `web3.eip7702.enabled` | EIP-7702 fee sponsoring 사용 여부 |
| `web3.reward-token.enabled` | ERC-20 reward token 송금 활성화 |
| `web3.reward-token.treasury.provisioning.enabled` | treasury 키 provisioning API 노출 |
| `web3.qna.admin.enabled` | QnA 관리자 force-settle / force-refund API |

## 1. wallet — 지갑 등록/해제

책임: 사용자 지갑 EIP-4361 서명 검증 후 등록 (1 user ↔ 1 active wallet), 해제(unlink).
컨트롤러: `WalletController` (`/web3/wallets`).

| Method | Path | Request | Response | 비고 |
|--------|------|---------|----------|------|
| POST | `/web3/wallets` | `RegisterWalletRequestDTO{walletAddress, signature, nonce}` | 201 `RegisterWalletResponseDTO` | challenge nonce + 서명 검증 후 등록 |
| DELETE | `/web3/wallets/{walletAddress}` | path | `Void` | unlink (soft) |

## 2. challenge — EIP-4361 nonce 발급

책임: Sign-In with Ethereum 메시지 nonce 발급 (15 분 만료, purpose 별 분리).
컨트롤러: `ChallengeController` (`/web3/challenges`).

| Method | Path | Request | Response | 비고 |
|--------|------|---------|----------|------|
| POST | `/web3/challenges` | `CreateChallengeRequestDTO{purpose, walletAddress}` | `ChallengeResponseDTO` | EIP-4361 메시지 + nonce + expiresAt |

## 3. signature — EIP-712 typed data 검증

다른 sub-module 이 호출하는 typed-data 검증 라이브러리. HTTP API 없음.

## 4. transaction — TX lifecycle

책임: TX 상태 머신 (CREATED → SIGNED → BROADCASTED → CONFIRMED / FAILED) 의 SSoT, 외부
서비스가 발행한 TX 추적. lock-held external call 분리는 별 티켓
(memory: `mom351_lockheld_external_calls_followup`).

HTTP API 없음 — admin 의 force-mark 만 외부 노출 (아래 11번 참조).

## 5. transfer — 토큰 전송 intent

책임: 사용자 영역의 토큰 전송 (질문 보상, 인증 보상). intent 생성 → 외부 워커가 dispatch.
컨트롤러: `TransferController` — `web3.eip7702.enabled` && `web3.reward-token.enabled` true 일 때만 활성.

| Method | Path | Request | Response | 비고 |
|--------|------|---------|----------|------|
| POST | `/users/me/transfers` | `CreateTransferRequestDTO` | 201 `CreateTransferResponseDTO` | intent 생성 |
| GET | `/users/me/transfers/{resourceId}` | path | `GetTransferResponseDTO` | intent + 최신 TX 상태 |

## 6. execution — EIP-7702 execution intent

책임: 7702 sponsored TX 실행 intent 의 조회/제출. authorization + submit 서명을 검증해
BROADCASTED 상태 진입.

컨트롤러: `ExecutionIntentController` — `web3.eip7702.enabled=true` 일 때만 활성.

| Method | Path | Request | Response | 비고 |
|--------|------|---------|----------|------|
| GET | `/users/me/web3/execution-intents/{executionIntentId}` | path | `GetExecutionIntentResponseDTO` | |
| POST | `/users/me/web3/execution-intents/{executionIntentId}/execute` | `ExecuteExecutionIntentRequestDTO{authorizationSignature, submitSignature, signedRawTransaction}` | 202 `ExecuteExecutionIntentResponseDTO` | |

## 7. qna — 질문 보상 escrow 동기화

책임: post / answer 의 escrow create / settle / refund 결과를 on-chain 상태와 동기화.
HTTP API 없음 — 이벤트 (`Web3TransactionSucceededEvent` 등) 만 소비. 진입은 post/answer
controller 의 recover-create / accept 에서 호출.

## 8. eip7702 — Account Abstraction fee sponsoring

책임: 일일 sponsor quota 관리, 7702 nonce / authorization payload 빌드. HTTP API 없음.

## 9. treasury — 프로토콜 지갑 키 관리

책임: AWS KMS 기반 protocol wallet 생성·조회·disable·archive. KMS 미가용 시 BouncyCastle
fallback. memory: `feedback_treasury_audit_recorder_pattern.md`,
`feedback_treasury_save_first_ordering.md` — DB-first / KMS post-commit 분리 원칙 엄수,
inline `REQUIRES_NEW` 로 합치지 말 것 (Spring AOP self-invocation).

HTTP API 는 admin sub-module 의 `TreasuryKeyController` 가 노출 (아래 11번 참조).

## 10. shared — 공용 VO / 암호 util

`WalletAddress`, `Amount`, ABI 디코더 등. HTTP API 없음.

## 11. admin — 관리자 web3 API

책임: admin 전용 force-settle / force-refund / mark-tx-succeeded / treasury 키 운영.

컨트롤러: `QnaAdminEscrowController`, `TransactionController`, `TreasuryKeyController`.

### QnA escrow (`@ConditionalOnQnaAdminFeatureEnabled`)

| Method | Path | Request | Response | 비고 |
|--------|------|---------|----------|------|
| GET | `/admin/web3/qna/questions/{postId}/answers/{answerId}/settlement-review` | path | `GetQnaAdminSettlementReviewResponseDTO` | |
| POST | `/admin/web3/qna/questions/{postId}/answers/{answerId}/settle` | path | `ForceQnaAdminSettlementResponseDTO` | force-settle |
| GET | `/admin/web3/qna/questions/{postId}/refund-review` | path | `GetQnaAdminRefundReviewResponseDTO` | |
| POST | `/admin/web3/qna/questions/{postId}/refund` | path | `ForceQnaAdminRefundResponseDTO` | force-refund |

### Transaction (`web3.reward-token.enabled=true`)

| Method | Path | Request | Response | 비고 |
|--------|------|---------|----------|------|
| POST | `/admin/web3/transactions/{txId}/mark-succeeded` | `MarkTransactionSucceededRequestDTO{txHash, explorerUrl, reason, evidence}` | `MarkTransactionSucceededResponseDTO` | 수동 CONFIRMED 처리, audit 필수 |

### Treasury Key (`web3.reward-token.treasury.provisioning.enabled=true`)

| Method | Path | Request | Response | 비고 |
|--------|------|---------|----------|------|
| POST | `/admin/web3/treasury-keys/provision` | `ProvisionTreasuryKeyRequestDTO` | `ProvisionTreasuryKeyResponseDTO` | KMS 키 생성 + DB 등록 |
| GET | `/admin/web3/treasury-keys/{walletAlias}` | path | `TreasuryWalletView` | 없으면 `TreasuryWalletStateException` |
| POST | `/admin/web3/treasury-keys/{walletAlias}/disable` | path | `TreasuryWalletView` | KMS disable AFTER_COMMIT |
| POST | `/admin/web3/treasury-keys/{walletAlias}/archive` | path | `TreasuryWalletView` | |

## Cross-cutting 규칙 (web3 전반)

- 사용자 식별: `@AuthenticationPrincipal Long userId | operatorId`. null → `UserNotAuthenticatedException`.
- DB 우선 commit, 외부 시스템 (KMS/RPC) 호출은 AFTER_COMMIT 핸들러에서.
  관련 memory: `feedback_treasury_save_first_ordering.md`.
- treasury audit 기록은 별 bean 사용 — `@Transactional(REQUIRES_NEW)` 를 같은 클래스 inline
  으로 두지 말 것 (AOP self-invocation 무력화).
- Feature flag 가 false 면 controller bean 자체가 등록되지 않음 (`@ConditionalOnProperty`).
