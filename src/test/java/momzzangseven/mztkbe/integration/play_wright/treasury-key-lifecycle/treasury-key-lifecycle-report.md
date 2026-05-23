# Treasury Provision API — Playwright 테스트 결과 리포트

> 실행 일자: 2026-05-15
> Spec: `src/test/java/momzzangseven/mztkbe/integration/play_wright/treasury-key-lifecycle/treasury-key-lifecycle.spec.ts`
> 환경: prod profile + 실제 AWS KMS
> Playwright: 1.58.2 / chromium / workers=1 (serial)

## 요약

| 항목 | 값 |
|------|---|
| Suite | `Treasury Provision API — Group B & C` |
| 총 테스트 | 11 |
| 통과 | 10 |
| 실패 | 0 |
| Skip | 1 (P-11) |
| Flaky | 0 |
| 총 소요 시간 | 약 39.71s |

전체 테스트가 1회 직렬 실행으로 통과했다. 기존 Provision API 상태/존재 분기(Group B) 6건 + AFTER_COMMIT KMS 후처리 분기(Group C) 2건에 더해, MOM-444 가 도입한 새 dispatch 4 분기(공유 운영지갑 / C7 rotation / C6 archived re-provision / C5 reactivate)를 실제 AWS KMS 경로에서 회귀 검증했다.

## 테스트별 결과

| ID | 분기 | 시나리오 | 소요 | 결과 |
|----|------|----------|-----:|:----:|
| P-7  | B8  | Happy path — 신규 키 provision 성공 | 1,874ms | PASS |
| P-8  | B5  | 동일 입력 재호출 (alias→ENABLED) → 409 | 297ms | PASS |
| P-10 | C0  | 다른 alias 가 같은 address 보유 → 200 (MOM-444 공유 운영지갑 허용) | 1,762ms | PASS |
| P-11 | C10 | legacy row(kms_key_id=NULL) + 다른 address → 200 (C10 backfill) | — | SKIP |
| P-12 | B10 | AFTER_COMMIT — alias 가 다른 ENABLED 키 점유 → 200 + `KMS_CREATE_ALIAS` audit fail | 2,654ms | PASS |
| P-13 | B11 | AFTER_COMMIT — alias 가 ghost(PENDING_DELETION) 가리킴 → `KMS_UPDATE_ALIAS` 복구 | 3,327ms | PASS |
| P-9  | B6  | Service alias-repair — DB row 존재 + alias UNAVAILABLE → `KMS_CREATE_ALIAS` 성공 | 2,775ms | PASS |
| P-MOM444-1 | C0+C0 | 공유 운영지갑 — REWARD provision 후 동일 raw key 로 SPONSOR provision | 3,605ms | PASS |
| P-MOM444-2 | C7 | Key rotation — 다른 raw key 로 동일 alias 재 provision, 옛 키 disable + schedule_deletion | 3,162ms | PASS |
| P-MOM444-3 | C6 | ARCHIVED → re-provision — 새 key, 옛 key 는 손대지 않음 (disposeOldKey=false) | 3,360ms | PASS |
| P-MOM444-4 | C5 | DISABLED → reactivate — 같은 키 `KMS_ENABLE` + status ACTIVE 복구 | 3,604ms | PASS |

## 검증 포인트

- **DB 일관성**: `web3_treasury_wallets`, `web3_treasury_provision_audits`, `web3_treasury_kms_audits` 의 행이 시나리오별 기대값과 일치.
- **HTTP 상태 코드**: 정상 200, 충돌 409 모두 의도대로 반환. (MOM-444 이후 ADDRESS_MISMATCH 400 / 공유주소 409 는 회귀로 200 으로 변경됨이 확인됨.)
- **AFTER_COMMIT 핸들러 동작**: 트랜잭션 커밋 후 KMS 호출이 실행되고, 결과(success/false)가 `web3_treasury_kms_audits` 에 기록됨을 확인.
- **Ghost 복구 분기 (P-13)**: `BindKmsAliasService.handleAliasAlreadyExists` 의 PENDING_DELETION 분기는 `KMS_UPDATE_ALIAS` 만 기록하고 `KMS_CREATE_ALIAS` audit 은 남기지 않는 production 거동과 정확히 일치.
- **ENABLED out-of-band 충돌 (P-12)**: 동일 메서드의 ENABLED 분기는 `KMS_CREATE_ALIAS` success=false audit 기록 후 예외 throw → AFTER_COMMIT 핸들러가 swallow 하여 응답 200 유지.
- **MOM-444 공유 운영지갑 (P-10, P-MOM444-1)**: cross-row UNIQUE 제거 이후 동일 `treasury_address` 를 여러 alias 가 공유. `wallet_alias : kms_key_id` 는 여전히 1:1 invariant 유지.
- **MOM-444 rotation (P-MOM444-2)**: 다른 raw key 로 동일 alias 재 provision 시 C7 dispatch — alias 가 새 키로 재바인딩되고 옛 키는 `KMS_DISABLE` + `KMS_SCHEDULE_DELETION` audit + 실제 KMS 상태가 `Disabled` 또는 `PendingDeletion` 으로 전이.
- **MOM-444 archived re-provision (P-MOM444-3)**: ARCHIVED row 의 옛 키는 archive 단계에서 이미 `PendingDeletion` 인 상태. 재 provision 은 새 키를 만들어 row 를 부활시키되 옛 키에 `KMS_DISABLE` / `KMS_SCHEDULE_DELETION` audit 을 추가하지 않음(`disposeOldKey=false` 분기).
- **MOM-444 reactivate (P-MOM444-4)**: DISABLED row 재 provision 은 같은 키를 그대로 사용하며 새 `KMS_ENABLE` action audit 이 success=true 로 기록되고 KMS 키 상태가 `Enabled` 로 복귀.

## P-11 Skip 사유

V069 (MOM-391) 가 `web3_treasury_wallets.kms_key_id` 컬럼을 `NOT NULL` 로 잠근 이후, legacy row (`kms_key_id = NULL`) 는 DB 레벨에서 더 이상 생성할 수 없어 E2E fixture 자체가 성립하지 않는다. C10 backfill 분기는 단위 테스트 `ProvisionTreasuryKeyServiceTest` 의 `c10_backfillDiffAddrActive_overwritesAddress` / `c11_*Disabled` / `c12_*Archived` 가 책임진다.

## 비고

- 실행 직렬(workers=1)로 수행되어 KMS alias / DB unique constraint 충돌 없이 안정적으로 통과.
- Group A(입력 검증), Group D(인증/인가)는 본 실행 범위 외로, 별도 단위/통합 테스트에서 다루는 것이 적절.
- MOM-444 회귀 그룹은 Java E2E (Mock KMS) 가 비즈니스 로직 단위 회귀를 잡고, 본 Playwright 그룹은 real `kms:UpdateAlias` / `kms:EnableKey` / `kms:DisableKey` / `kms:ScheduleKeyDeletion` 호출의 단조 동작을 AWS 에 직접 검증하는 보완 레이어.
