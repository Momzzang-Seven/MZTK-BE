# 이미지 Presigned URL + S3 PUT 업로드 E2E 테스트 결과 보고서

- **실행일시**: 2026-03-23 17:02:31 KST
- **테스터**: raewookang
- **대상 서버**: http://127.0.0.1:8080 (로컬 Spring Boot)
- **AWS S3 버킷**: `mztk-bucket` (ap-northeast-2)
- **외부 API 연동 여부**: 실제 AWS S3 연동 (Presigned URL PUT + GET)
- **Playwright 버전**: 1.58.2
- **총 소요 시간**: 5.584초 (전체 10개 테스트)

---

## 테스트 시나리오 결과

| # | 시나리오 | reference_type | 업로드 파일 | items 수 | 결과 | 소요 시간 | 비고 |
|---|---|---|---|---|---|---|---|
| TC-1 | COMMUNITY_FREE 단일 업로드 + GET 검증 | COMMUNITY_FREE | test1.png | 1 | ✅ PASS | 434ms | GET 200 (618,515 bytes) |
| TC-2 | COMMUNITY_QUESTION 단일 업로드 + GET 검증 | COMMUNITY_QUESTION | test2.png | 1 | ✅ PASS | 125ms | GET 200 (130,061 bytes) |
| TC-3 | COMMUNITY_ANSWER 단일 업로드 + GET 검증 | COMMUNITY_ANSWER | test3.png | 1 | ✅ PASS | 899ms | GET 200 (1,270,980 bytes) |
| TC-4 | USER_PROFILE 단일 업로드 + GET 검증 | USER_PROFILE | test1.png | 1 | ✅ PASS | 299ms | GET 200 (618,515 bytes) |
| TC-5 | MARKET_CLASS 3장 → 4개 URL (n+1 확장) + GET 검증 | MARKET_CLASS | test1~3.png | 4 | ✅ PASS | 1,082ms | CLASS_THUMB×1 + CLASS_DETAIL×3, GET 200 |
| TC-6 | MARKET_STORE 3장 → 4개 URL (n+1 확장) + GET 검증 | MARKET_STORE | test1~3.png | 4 | ✅ PASS | 984ms | STORE_THUMB×1 + STORE_DETAIL×3, GET 200 |
| TC-7 | WORKOUT 단일 업로드 (private 경로, GET 생략) | WORKOUT | test4.png | 1 | ✅ PASS | 84ms | `/tmp/` 없음 확인, GET 생략 |
| TC-8 | COMMUNITY_FREE 5장 복수 업로드 | COMMUNITY_FREE | test1~5.png | 5 | ✅ PASS | 814ms | 5개 PUT 전부 200 |
| TC-9 | 미인증 요청 → 401 | - | - | - | ✅ PASS | 8ms | HTTP 401 정상 반환 |
| TC-10 | 허용 안 되는 확장자(webp) → 400 IMAGE_005 | - | invalid.webp | - | ✅ PASS | 17ms | HTTP 400 + code=IMAGE_005 |

**총계: 10/10 PASS · 실패 0 · 스킵 0 · Flaky 0**

---

## S3 업로드 경로 확인

| reference_type | imageId | tmpObjectKey prefix | S3 오브젝트 키 | GET 접근 | 파일 크기 |
|---|---|---|---|---|---|
| COMMUNITY_FREE | 417 | `public/community/free/tmp/` | `public/community/free/tmp/88f4fb46-1434-4718-b126-e088492fe870.png` | ✅ 200 | 618,515 bytes |
| COMMUNITY_QUESTION | 418 | `public/community/question/tmp/` | `public/community/question/tmp/bb93f6c8-9e9d-4cf0-afca-d778ec3d39c7.png` | ✅ 200 | 130,061 bytes |
| COMMUNITY_ANSWER | 419 | `public/community/answer/tmp/` | `public/community/answer/tmp/f32988fe-9729-420e-97cd-56457c94b428.png` | ✅ 200 | 1,270,980 bytes |
| USER_PROFILE | 420 | `public/user/profile/tmp/` | `public/user/profile/tmp/c664d84e-a1d6-49f1-8785-4750a598a70f.png` | ✅ 200 | 618,515 bytes |
| MARKET_CLASS_THUMB | 421 | `public/market/class/thumb/tmp/` | `public/market/class/thumb/tmp/a51734e2-ccdd-4c03-8de6-4c197dcca422.png` | ✅ 200 | 618,515 bytes |
| MARKET_CLASS_DETAIL | 422 | `public/market/class/detail/tmp/` | `public/market/class/detail/tmp/cfdff1f4-05a5-4161-b379-388231911ad1.png` | ✅ 200 | 618,515 bytes |
| MARKET_STORE_THUMB | 425 | `public/market/store/thumb/tmp/` | `public/market/store/thumb/tmp/daca324b-b56a-4100-98e6-4ae06d17c50f.png` | ✅ 200 | 618,515 bytes |
| MARKET_STORE_DETAIL | 426 | `public/market/store/detail/tmp/` | `public/market/store/detail/tmp/b3f4f2e9-4de7-4ab5-9433-a3915eac334c.png` | ✅ 200 | 618,515 bytes |
| WORKOUT | 429 | `private/workout/` | `private/workout/44a34700-6835-4450-b8b8-335e2c87be98.png` | 생략 (private) | - |

> S3 Base URL: `https://mztk-bucket.s3.ap-northeast-2.amazonaws.com/`

---

## TC-5 MARKET_CLASS 상세 — 생성된 오브젝트 키 (n+1 확장 검증)

입력 3장 → 4개 Presigned URL 발급 (MARKET_CLASS_THUMB 1 + MARKET_CLASS_DETAIL 3)

| imgOrder | imageId | reference_type | tmpObjectKey |
|---|---|---|---|
| 1 | 421 | MARKET_CLASS_THUMB | `public/market/class/thumb/tmp/a51734e2-ccdd-4c03-8de6-4c197dcca422.png` |
| 2 | 422 | MARKET_CLASS_DETAIL | `public/market/class/detail/tmp/cfdff1f4-05a5-4161-b379-388231911ad1.png` |
| 3 | 423 | MARKET_CLASS_DETAIL | `public/market/class/detail/tmp/a550a281-dc52-45bf-8acf-07b67abae872.png` |
| 4 | 424 | MARKET_CLASS_DETAIL | `public/market/class/detail/tmp/38b846d8-1c46-43ba-9d3c-3c26b59ecacd.png` |

- 4개 tmpObjectKey 모두 서로 다른 UUID → 고유성 확인 ✅
- 4개 imageId 모두 서로 다른 값(421~424) → DB 별도 row 확인 ✅
- MARKET_CLASS(virtual type)은 DB에 저장되지 않고 MARKET_CLASS_THUMB / MARKET_CLASS_DETAIL 로 분리 저장 ✅

---

## TC-6 MARKET_STORE 상세 — 생성된 오브젝트 키 (n+1 확장 검증)

입력 3장 → 4개 Presigned URL 발급 (MARKET_STORE_THUMB 1 + MARKET_STORE_DETAIL 3)

| imgOrder | imageId | reference_type | tmpObjectKey |
|---|---|---|---|
| 1 | 425 | MARKET_STORE_THUMB | `public/market/store/thumb/tmp/daca324b-b56a-4100-98e6-4ae06d17c50f.png` |
| 2 | 426 | MARKET_STORE_DETAIL | `public/market/store/detail/tmp/b3f4f2e9-4de7-4ab5-9433-a3915eac334c.png` |
| 3 | 427 | MARKET_STORE_DETAIL | `public/market/store/detail/tmp/f8683dc9-2c35-49da-a163-11baaf97cc72.png` |
| 4 | 428 | MARKET_STORE_DETAIL | `public/market/store/detail/tmp/28ee066e-8908-4477-8267-9052cc40f7ba.png` |

- 4개 tmpObjectKey 모두 서로 다른 UUID → 고유성 확인 ✅
- 4개 imageId 모두 서로 다른 값(425~428) → DB 별도 row 확인 ✅
- MARKET_STORE(virtual type)은 DB에 저장되지 않고 MARKET_STORE_THUMB / MARKET_STORE_DETAIL 로 분리 저장 ✅

---

## TC-8 COMMUNITY_FREE 5장 상세 — 생성된 오브젝트 키

| imgOrder | imageId | tmpObjectKey |
|---|---|---|
| 1 | 430 | `public/community/free/tmp/4e35ab24-e530-4465-be3a-58a32f7eb182.png` |
| 2 | 431 | `public/community/free/tmp/c54bf7b1-a307-422b-941f-3ba4f842f8ed.png` |
| 3 | 432 | `public/community/free/tmp/348b6d7b-7167-4bea-b3ca-4d6a6a26e6cf.png` |
| 4 | 433 | `public/community/free/tmp/c8c7c86a-b11a-4c70-885b-3fa431b153ac.png` |
| 5 | 434 | `public/community/free/tmp/ea25767d-8b05-417c-bab3-85461c8d5ede.png` |

---

## 실패 항목

없음

---

## 비고

- **응답 구조 변경 반영**: 이번 실행부터 `POST /images/presigned-urls` 응답에 `imageId` 필드가 추가되었으며, 모든 TC에서 `imageId` 양수 검증 통과 확인
- **신규 reference_type 추가**: `USER_PROFILE`(TC-4), `MARKET_STORE`(TC-6) 타입이 새로 추가되어 각각 검증 완료
- **MARKET → MARKET_CLASS 변경**: 기존 `MARKET` 타입이 `MARKET_CLASS`로 rename 되었으며, S3 경로도 `public/market/thumb/`, `public/market/detail/` → `public/market/class/thumb/`, `public/market/class/detail/`로 변경됨
- **public 경로 GET 성공**: `public/` 하위 7개 prefix 모두 버킷 퍼블릭 읽기 정책이 활성화되어 있어 서명 없이 GET 200 반환 확인 (COMMUNITY_FREE, COMMUNITY_QUESTION, COMMUNITY_ANSWER, USER_PROFILE, MARKET_CLASS_THUMB, MARKET_CLASS_DETAIL, MARKET_STORE_THUMB, MARKET_STORE_DETAIL)
- **private 경로 (WORKOUT)**: `private/workout/` 경로는 버킷 정책상 퍼블릭 GET 불가 → PUT 성공만 확인하고 GET 검증 생략
- **테스트 계정**: `pw-img-1774252951799@playwright.test` (테스트 실행 시 자동 생성, 실행마다 새 계정 발급)
- **업로드된 tmp/ 오브젝트**: `ImageUnlinkedCleanupScheduler` 에 의해 5시간 후 자동 삭제 예정
