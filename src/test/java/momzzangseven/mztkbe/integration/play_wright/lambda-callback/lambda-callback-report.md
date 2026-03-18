# Lambda 콜백 전체 흐름 E2E 테스트 결과 보고서

- **실행일시**: 2026-03-17 17:50:16 KST
- **테스터**: raewookang
- **대상 서버**: http://127.0.0.1:8080 (로컬 Spring Boot)
- **AWS S3 버킷**: `mztk-bucket` (ap-northeast-2)
- **외부 API 연동 여부**: 실제 AWS S3 연동 + 실제 AWS Lambda 연동
- **Playwright 버전**: 1.58.2
- **총 소요 시간**: 29.7초 (전체 7개 테스트)

---

## 테스트 시나리오 결과

| # | 시나리오 | 유형 | 결과 | 소요 시간 |
|---|---|---|---|---|
| TC-1 | COMMUNITY_FREE COMPLETED 전체 흐름 (업로드 → Lambda 트리거 → 콜백 대기 → tmp 삭제 → WebP 확인) | 실제 Lambda 연동 | ✅ PASS | 13,903ms |
| TC-2 | COMMUNITY_FREE FAILED 콜백 흐름 (업로드 → FAILED 콜백 수동 전송 → BE 200 OK) | 모의 콜백 | ✅ PASS | 470ms |
| TC-3 | 잘못된 Webhook Secret → 401 Unauthorized + `IMAGE_003` | 보안 | ✅ PASS | 19ms |
| TC-4 | Webhook Secret 헤더 누락 → 400 Bad Request | 보안 | ✅ PASS | 11ms |
| TC-5 | 존재하지 않는 tmpObjectKey → 404 Not Found + `IMAGE_001` | 에러 처리 | ✅ PASS | 20ms |
| TC-6 | COMPLETED 콜백 동일 요청 2회 → 둘 다 200 OK (멱등성) | 멱등성 | ✅ PASS | 181ms |
| TC-7 | MARKET 2장 업로드 전체 흐름 (THUMB+DETAIL 경로 분기 + 3개 Lambda 병렬 처리) | 실제 Lambda 연동 | ✅ PASS | 14,169ms |

**총계: 7/7 PASS · 실패 0 · 스킵 0 · Flaky 0**

---

## TC-1: COMMUNITY_FREE COMPLETED 전체 흐름

**실행 시각**: 2026-03-17 17:50:17 KST | **소요**: 13,903ms

### 단계별 결과

| Step | 내용 | 결과 |
|---|---|---|
| 1 | Presigned URL 발급 (COMMUNITY_FREE, 1개) | ✅ |
| 2 | S3 PUT 업로드 → S3 이벤트로 Lambda 자동 트리거 | ✅ |
| 3 | S3 GET tmp 경로 → 업로드 확인 | ✅ HTTP 200 (618,515 bytes) |
| 4 | S3 final 경로 폴링 → Lambda 콜백 완료 감지 | ✅ **3초 경과** |
| 5 | Lambda tmp 파일 삭제 대기 (10초) | ✅ |
| 6 | S3 GET tmp 경로 → 삭제 확인 | ✅ **HTTP 403** |
| 7 | S3 GET final 경로 → WebP 확인 | ✅ `image/webp` · 49,348 bytes |

### 오브젝트 키

| 구분 | 경로 |
|---|---|
| tmp (원본 PNG) | `public/community/free/tmp/0ed28550-fca9-4e0a-9fe8-788af33c7078.png` |
| final (변환 WebP) | `public/community/free/0ed28550-fca9-4e0a-9fe8-788af33c7078.webp` |

### 검증 포인트

- Lambda 콜백 감지 방식: `final_object_key` S3 경로 폴링 (3초 간격, 최대 120초)
- Lambda → BE 콜백 완료 → WebP 업로드까지 실측 **약 3초**
- tmp 경로 삭제: Lambda가 BE 200 응답 수신 후 삭제 완료, GET 요청 시 **HTTP 403** 반환
- WebP 변환 압축률: PNG 618,515 bytes → WebP **49,348 bytes** (약 92% 감소)

---

## TC-2: COMMUNITY_FREE FAILED 콜백 흐름

**실행 시각**: 2026-03-17 17:50:31 KST | **소요**: 470ms

### 단계별 결과

| Step | 내용 | 결과 |
|---|---|---|
| 1 | Presigned URL 발급 (COMMUNITY_FREE, 1개) | ✅ |
| 2 | S3 PUT 업로드 | ✅ |
| 3 | S3 GET tmp 경로 → 업로드 확인 | ✅ HTTP 200 (618,515 bytes) |
| 4 | FAILED 콜백 수동 전송 (`X-Lambda-Webhook-Secret` 포함) | ✅ HTTP 200 `{"status":"SUCCESS"}` |

### 콜백 페이로드

```json
{
  "status": "FAILED",
  "tmpObjectKey": "public/community/free/tmp/14d38a60-6959-40f2-aca3-8a9476f47abd.png",
  "errorReason": "Lambda OOM: memory limit exceeded during WebP conversion"
}
```

> `errorReason` 필드가 DB `images.error_reason` 컬럼에 정상 저장됩니다.
> FAILED 시에는 `final_object_key` 가 없으므로 WebP 경로 검증 불필요.

---

## TC-3 ~ TC-5: 보안 / 에러 처리

| # | 검증 내용 | 기대 응답 | 실제 응답 | 결과 |
|---|---|---|---|---|
| TC-3 | 잘못된 Webhook Secret (`wrong-secret-should-fail`) | 401 + `IMAGE_003` | 401 + `IMAGE_003` | ✅ |
| TC-4 | `X-Lambda-Webhook-Secret` 헤더 자체 누락 | 400 (`MissingRequestHeaderException`) | 400 | ✅ |
| TC-5 | 존재하지 않는 `tmpObjectKey` 로 COMPLETED 콜백 | 404 + `IMAGE_001` | 404 + `IMAGE_001` | ✅ |

---

## TC-6: COMPLETED 콜백 멱등성

**실행 시각**: 2026-03-17 17:50:32 KST | **소요**: 181ms

1차 콜백 → `status=PENDING` 이미지를 `COMPLETED` 로 전이, `updateImagePort.update()` 호출

2차 동일 콜백 → 이미 `COMPLETED` 상태이므로 서비스 레이어에서 skip 처리, `updateImagePort.update()` 미호출, **HTTP 200** 반환

---

## TC-7: MARKET 2장 업로드 전체 흐름

**실행 시각**: 2026-03-17 17:50:32 KST | **소요**: 14,169ms

### Presigned URL 발급 결과 (MARKET n+1 규칙)

2장 입력 → **3개** URL 발급 (THUMB×1 + DETAIL×2)

| 인덱스 | 역할 | tmp 경로 | final 경로 |
|---|---|---|---|
| [0] | THUMB (image1) | `public/market/thumb/tmp/90b48d3d-c5c4-460f-8b11-c14bb0e09d76.png` | `public/market/thumb/90b48d3d-c5c4-460f-8b11-c14bb0e09d76.webp` |
| [1] | DETAIL (image1) | `public/market/detail/tmp/7b4882e3-6237-485d-9a6b-3522f891be85.png` | `public/market/detail/7b4882e3-6237-485d-9a6b-3522f891be85.webp` |
| [2] | DETAIL (image2) | `public/market/detail/tmp/07f730e5-3ce1-4c31-8abc-11e9a30113fb.png` | `public/market/detail/07f730e5-3ce1-4c31-8abc-11e9a30113fb.webp` |

### 단계별 결과

| Step | 내용 | 결과 |
|---|---|---|
| 1 | Presigned URL 발급 3개 (THUMB×1, DETAIL×2) | ✅ |
| 2 | S3 PUT 업로드 3개 → Lambda 트리거 ×3 | ✅ |
| 3 | S3 GET tmp 경로 3개 → 업로드 확인 | ✅ |
| 4 | final 경로 3개 병렬 폴링 → Lambda 콜백 완료 감지 | ✅ **3개 모두 3초 경과** |
| 5 | Lambda tmp 파일 삭제 대기 (10초) | ✅ |
| 6 | S3 GET tmp 경로 3개 → 삭제 확인 | ✅ **3개 모두 HTTP 403** |
| 7 | S3 GET final 경로 3개 → WebP 확인 | ✅ |

### WebP 변환 결과

| 역할 | 원본 크기 (PNG) | 변환 크기 (WebP) | 압축률 |
|---|---|---|---|
| THUMB (image1) | 618,515 bytes | 3,770 bytes | 약 99% 감소 |
| DETAIL (image1) | 618,515 bytes | 7,770 bytes | 약 99% 감소 |
| DETAIL (image2) | 130,061 bytes | 3,426 bytes | 약 97% 감소 |

### 경로 분포 최종 확인

```
image1 → THUMB  경로 존재 ✅  public/market/thumb/90b48d3d-...webp
image1 → DETAIL 경로 존재 ✅  public/market/detail/7b4882e3-...webp
image2 → DETAIL 경로 존재 ✅  public/market/detail/07f730e5-...webp

image1 은 THUMB + DETAIL 양쪽에 모두 위치 ✅
image2 는 DETAIL 에만 위치 ✅
```

> `S3 Base URL`: `https://mztk-bucket.s3.ap-northeast-2.amazonaws.com/`

---

## 실패 항목

없음

---

## 비고

- **Lambda 콜백 감지 방식**: `final_object_key` S3 경로를 3초 간격으로 폴링하여 HTTP 200 응답을 수신하는 시점을 "Lambda 콜백 완료"로 판단합니다. Lambda가 BE에 콜백 → BE 200 응답 → WebP 업로드의 순서로 동작하므로, WebP 출현이 전체 흐름의 완료 신호입니다.
- **tmp 삭제 확인**: Lambda가 BE로부터 200 응답을 받은 후 tmp 파일을 삭제합니다. 삭제 완료 후 GET 요청 시 S3 버킷 정책에 따라 **HTTP 403**(오브젝트 없음, 퍼블릭 읽기 허용 버킷)이 반환됩니다.
- **WebP 압축률**: Lambda의 WebP 변환 결과 원본 PNG 대비 97~99%의 크기 감소가 확인되었습니다. THUMB 경로는 리사이즈도 적용되어 더 높은 압축률을 보입니다.
- **MARKET n+1 병렬 처리**: 3개의 tmpObjectKey가 각각 독립적인 Lambda로 처리됩니다. `Promise.all` 병렬 폴링으로 가장 느린 Lambda 완료 시점까지 대기하며, 이번 실행에서는 3개 모두 **동일하게 3초** 이내에 완료되었습니다.
- **테스트 계정**: `pw-lambda-1773737417321@playwright.test` (실행 시 자동 생성, 매 실행마다 새 계정 발급)
- **업로드된 tmp 오브젝트**: TC-2의 FAILED tmp 파일(`14d38a60-...`) 은 Lambda가 처리하지 않아 tmp에 잔류합니다. `ImagePendingCleanupScheduler`에 의해 5시간 후 자동 삭제 예정입니다.
