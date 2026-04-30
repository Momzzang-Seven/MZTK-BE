# Treasury Provision API — Playwright 테스트 결과 리포트

> 실행 일자: 2026-04-30
> Spec: `src/test/java/momzzangseven/mztkbe/integration/play_wright/treasury-key-lifecycle/treasury-key-lifecycle.spec.ts`
> 환경: prod profile + 실제 AWS KMS
> Playwright: 1.58.2 / chromium / workers=1 (serial)

## 요약

| 항목 | 값 |
|------|---|
| Suite | `Treasury Provision API — Group B & C` |
| 총 테스트 | 7 |
| 통과 | 7 |
| 실패 | 0 |
| Skip | 0 |
| Flaky | 0 |
| 총 소요 시간 | 약 18.18s |

전체 테스트가 1회 직렬 실행으로 통과했다. Provision API 의 상태/존재 분기(Group B) 6건과 AFTER_COMMIT KMS 후처리 분기(Group C) 2건을 모두 검증했다.

## 테스트별 결과

| ID | 분기 | 시나리오 | 소요 | 결과 |
|----|------|----------|-----:|:----:|
| P-7  | B8  | Happy path — 신규 키 provision 성공 | 1,563ms | PASS |
| P-8  | B5  | 동일 입력 재호출 (alias→ENABLED) → 409 | 38ms | PASS |
| P-10 | B7  | 다른 alias 가 같은 address 보유 → 409 | 1,150ms | PASS |
| P-11 | B4  | 기존 legacy row + 다른 wallet_address → 400 | 1,154ms | PASS |
| P-12 | B10 | AFTER_COMMIT — alias 가 다른 ENABLED 키 점유 → 200 + `KMS_CREATE_ALIAS` audit fail | 2,263ms | PASS |
| P-13 | B11 | AFTER_COMMIT — alias 가 ghost(PENDING_DELETION) 가리킴 → `KMS_UPDATE_ALIAS` 복구 | 3,165ms | PASS |
| P-9  | B6  | Service alias-repair — DB row 존재 + alias UNAVAILABLE → `KMS_CREATE_ALIAS` 성공 | 1,635ms | PASS |

## 검증 포인트

- **DB 일관성**: `web3_treasury_wallets` 행, `web3_treasury_provision_audits`, `web3_treasury_kms_audits` audit 행이 시나리오별 기대값과 일치.
- **HTTP 상태 코드**: 정상 200, 충돌 409, 입력 위반 400 모두 의도대로 반환.
- **AFTER_COMMIT 핸들러 동작**: 트랜잭션 커밋 후 KMS 호출이 실행되고, 결과(success/false)가 `web3_treasury_kms_audits` 에 기록됨을 확인.
- **Ghost 복구 분기 (P-13)**: `BindKmsAliasService.handleAliasAlreadyExists` 의 PENDING_DELETION 분기는 `KMS_UPDATE_ALIAS` 만 기록하고 `KMS_CREATE_ALIAS` audit 은 남기지 않는 production 거동과 정확히 일치.
- **ENABLED out-of-band 충돌 (P-12)**: 동일 메서드의 ENABLED 분기는 `KMS_CREATE_ALIAS` success=false audit 기록 후 예외 throw → AFTER_COMMIT 핸들러가 swallow 하여 응답 200 유지.

## 비고

- 실행 직렬(workers=1)로 수행되어 KMS alias / DB unique constraint 충돌 없이 안정적으로 통과.
- Group A(입력 검증), Group D(인증/인가), Group E(skip)는 본 실행 범위 외로, 별도 단위/통합 테스트에서 다루는 것이 적절.
