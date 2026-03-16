# Workout Verification Playwright E2E 테스트 보고서

## 개요

`verification.spec.ts` 는 verification 모듈의 외부 요청 관점 유즈케이스를 Playwright + HTTP API + PostgreSQL fixture 조합으로 검증합니다.

- 공통 선차단/계약 오류
- 기존 verification row 재사용
- `GET /verification/{verificationId}`
- `GET /verification/today-completion`
- 실제 presigned URL 발급 + S3 PUT 업로드 이후 verification submit
- 실제 자산 기반 AI 승인/거절 시나리오
- `verification/photo`, `verification/record` submit 응답 시간 측정

테스트 파일:
- `src/test/java/momzzangseven/mztkbe/integration/play_wright/verification/verification.spec.ts`

## 커버 범위

### Suite A — Precheck and Contract Failures

| TC ID | 시나리오 | 예상 결과 |
|---|---|---|
| `TC-V-A-01` | invalid tmpObjectKey | `400` / `VERIFICATION_001` |
| `TC-V-A-02` | photo endpoint 에 `png` 제출 | `400` / `VERIFICATION_002` |
| `TC-V-A-03` | record endpoint 에 `gif` 제출 | `400` / `VERIFICATION_002` |
| `TC-V-A-04` | image row 없음 | `404` / `VERIFICATION_003` |
| `TC-V-A-05` | image row 소유자 불일치 | `403` / `VERIFICATION_004` |
| `TC-V-A-06` | 기존 verification kind mismatch | `409` / `VERIFICATION_005` |
| `TC-V-A-07` | 오늘 WORKOUT XP 선차단 | `409` / `VERIFICATION_006` |
| `TC-V-A-08` | detail not found | `404` / `VERIFICATION_007` |
| `TC-V-A-P-*` | photo 허용 포맷 `jpg/jpeg/heif/heic` 최종 응답 바디 검증 | 기존 `REJECTED` row 재사용으로 포맷별 최종 응답 계약 확인 |
| `TC-V-A-R-*` | record 허용 포맷 `jpg/jpeg/png/heif/heic` 최종 응답 바디 검증 | 기존 `REJECTED` row 재사용으로 포맷별 최종 응답 계약 확인 |

### Suite B — Existing Row Reuse and Read Models

| TC ID | 시나리오 | 예상 결과 |
|---|---|---|
| `TC-V-B-01` | 기존 `PENDING` row 재사용 | 기존 `PENDING` 반환 |
| `TC-V-B-02` | 기존 `ANALYZING` row 재사용 | 기존 `ANALYZING` 반환 |
| `TC-V-B-03` | 기존 `REJECTED` row 재사용 | 기존 `REJECTED` 반환 |
| `TC-V-B-04` | 기존 `VERIFIED` row 재사용 | 기존 `VERIFIED` 반환 |
| `TC-V-B-05` | 어제 생성된 `FAILED` row 재사용 | 기존 `FAILED` 반환, 재분석 없음 |
| `TC-V-B-06` | today completion = reward + latest verification | `completedMethod` + `latestVerification` 동시 반영 |
| `TC-V-B-07` | today completion empty state | `todayCompleted=false`, `latestVerification=null` |

### Suite C — Real Upload Flow with Deterministic Outcomes

| TC ID | 시나리오 | 예상 결과 |
|---|---|---|
| `TC-V-C-01` | EXIF usable metadata 없는 운동 사진 업로드 후 submit | `REJECTED` / `MISSING_EXIF_METADATA` |
| `TC-V-C-02` | 손상된 JPEG 바이트 업로드 후 record submit | `FAILED` / `ANALYSIS_IMAGE_GENERATION_FAILED` |
| `TC-V-C-03` | 같은 tmpObjectKey 사진 submit 동시 2회 | 같은 `verificationId`, 단일 row 수렴 |
| `TC-V-C-04` | today `FAILED` row 재시도 | 같은 `verificationId` 재사용, `updated_at` 갱신 |

### Suite D — Optional Asset-Driven Real AI Cases

| TC ID | 시나리오 | 필요 자산 | 예상 결과 |
|---|---|---|---|
| `TC-V-D-01` | 승인용 운동 사진 submit | `VERIFICATION_PHOTO_APPROVED_IMAGE` | `VERIFIED`, `completedMethod=WORKOUT_PHOTO` |
| `TC-V-D-02` | 승인용 운동 기록 submit | `VERIFICATION_RECORD_APPROVED_IMAGE` | `VERIFIED`, `exerciseDate=todayKst` |
| `TC-V-D-03` | 거절용 운동 기록 submit | `VERIFICATION_RECORD_REJECTED_IMAGE` | `REJECTED`, `rejectionReasonCode != null` |

## 환경 변수

`.env.example` 에 아래 verification 전용 키를 추가했습니다.

```dotenv
DATABASE_URL=
DB_HOST=localhost
DB_PORT=5432
DB_NAME=mztk_dev
DB_USER=postgres
DB_PASSWORD=postgres

VERIFICATION_PHOTO_APPROVED_IMAGE=
VERIFICATION_RECORD_APPROVED_IMAGE=
VERIFICATION_RECORD_REJECTED_IMAGE=
```

## 실행 방법

```bash
cd src/test/java/momzzangseven/mztkbe/integration/play_wright

# 타입 검증
npx tsc --noEmit

# verification 전체
npx playwright test verification/verification.spec.ts

# deterministic suite만
npx playwright test verification/verification.spec.ts --grep "Suite [A-C]"

# 실제 자산이 필요한 suite만
npx playwright test verification/verification.spec.ts --grep "Suite D"
```

## 응답 시간 측정

운동 사진 인증과 운동 기록 인증 submit API 는 모두 요청 직전 시각과 응답 수신 직후 시각을 비교해 `elapsedMs` 를 계산합니다.

출력 형식:

```text
[verification][photo] submit private/workout/{uuid}.jpg -> 200 in 842ms
[verification][record] submit private/workout/{uuid}.png -> 200 in 1164ms
```

즉, API 요청이 들어간 시점부터 최종 JSON 응답을 받은 시점까지의 end-to-end 응답 시간을 바로 확인할 수 있습니다.

## 작성 시점 검증 결과

### 저장소 내부 검증

| 항목 | 결과 |
|---|---|
| `npx tsc --noEmit` | PASS |

### 실제 Playwright 실행

실제 실행 환경:

- 백엔드 서버: `prod` 프로파일로 로컬 기동
- DB: 임시 PostgreSQL DB (`mztk_verification_prod_pw_20260314_003352`)
- 외부 연동: 실제 presigned URL + S3 업로드 사용
- Playwright 실행 명령:

```bash
set -a
source /Users/nutria/ERICA/Capston/MZTK-BE/.env
set +a
DB_NAME=mztk_verification_prod_pw_20260314_003352 \
  npx playwright test verification/verification.spec.ts
```

실행 결과:

| 항목 | 결과 |
|---|---|
| 전체 테스트 | `42` |
| 통과 | `39` |
| 스킵 | `3` |
| 실패 | `0` |

스킵된 3건은 아래 실자산 환경변수가 비어 있어서 의도적으로 건너뛴 optional AI 자산 케이스입니다.

- `VERIFICATION_PHOTO_APPROVED_IMAGE`
- `VERIFICATION_RECORD_APPROVED_IMAGE`
- `VERIFICATION_RECORD_REJECTED_IMAGE`

실측 submit latency 는 별도 문서에 기록했습니다.

- `src/test/java/momzzangseven/mztkbe/integration/play_wright/verification/verification-latency-report.md`
