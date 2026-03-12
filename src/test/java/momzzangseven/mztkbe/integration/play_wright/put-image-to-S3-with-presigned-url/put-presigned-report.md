# 이미지 Presigned URL + S3 PUT 업로드 E2E 테스트 결과 보고서

- **실행일시**: 2026-03-11 23:36:58 KST
- **테스터**: raewookang
- **대상 서버**: http://127.0.0.1:8080 (로컬 Spring Boot)
- **AWS S3 버킷**: `mztk-bucket` (ap-northeast-2)
- **외부 API 연동 여부**: 실제 AWS S3 연동 (Presigned URL PUT + GET)
- **Playwright 버전**: 1.58.2
- **총 소요 시간**: 4.924초 (전체 8개 테스트)

---

## 테스트 시나리오 결과

| # | 시나리오 | reference_type | 업로드 파일 | items 수 | 결과 | 소요 시간 | 비고 |
|---|---|---|---|---|---|---|---|
| TC-1 | COMMUNITY_FREE 단일 업로드 + GET 검증 | COMMUNITY_FREE | test1.png | 1 | ✅ PASS | 757ms | GET 200 (618,515 bytes) |
| TC-2 | COMMUNITY_QUESTION 단일 업로드 + GET 검증 | COMMUNITY_QUESTION | test2.png | 1 | ✅ PASS | 139ms | GET 200 (130,061 bytes) |
| TC-3 | COMMUNITY_ANSWER 단일 업로드 + GET 검증 | COMMUNITY_ANSWER | test3.png | 1 | ✅ PASS | 648ms | GET 200 (1,270,980 bytes) |
| TC-4 | MARKET 3장 → 4개 URL (n+1 확장) + GET 검증 | MARKET | test1~3.png | 4 | ✅ PASS | 1,175ms | THUMB×1 + DETAIL×3, GET 200 |
| TC-5 | WORKOUT 단일 업로드 (private 경로, GET 생략) | WORKOUT | test4.png | 1 | ✅ PASS | 122ms | `/tmp/` 없음 확인, GET 생략 |
| TC-6 | COMMUNITY_FREE 5장 복수 업로드 | COMMUNITY_FREE | test1~5.png | 5 | ✅ PASS | 701ms | 5개 PUT 전부 200 |
| TC-7 | 미인증 요청 → 401 | - | - | - | ✅ PASS | 14ms | HTTP 401 정상 반환 |
| TC-8 | 허용 안 되는 확장자(webp) → 400 IMAGE_005 | - | invalid.webp | - | ✅ PASS | 16ms | HTTP 400 + code=IMAGE_005 |

**총계: 8/8 PASS · 실패 0 · 스킵 0 · Flaky 0**

---

## S3 업로드 경로 확인

| reference_type | tmpObjectKey prefix | S3 오브젝트 URL | GET 접근 | 파일 크기 |
|---|---|---|---|---|
| COMMUNITY_FREE | `public/community/free/tmp/` | `public/community/free/tmp/77673b04-2b97-41b2-a22d-37eb4fca0723.png` | ✅ 200 | 618,515 bytes |
| COMMUNITY_QUESTION | `public/community/question/tmp/` | `public/community/question/tmp/1572544f-6dee-4e05-8f4b-5ffbb6b7f5d9.png` | ✅ 200 | 130,061 bytes |
| COMMUNITY_ANSWER | `public/community/answer/tmp/` | `public/community/answer/tmp/e1db2546-113c-4914-ab25-719b5415e540.png` | ✅ 200 | 1,270,980 bytes |
| MARKET_THUMB | `public/market/thumb/tmp/` | `public/market/thumb/tmp/c842b04b-35e4-40a7-902c-a0effd11bd6b.png` | ✅ 200 | 618,515 bytes |
| MARKET_DETAIL | `public/market/detail/tmp/` | `public/market/detail/tmp/6cf60629-6568-4689-9d17-0b8671013eb8.png` | ✅ 200 | 618,515 bytes |
| WORKOUT | `private/workout/` | `private/workout/78d8f40e-93e4-407b-9638-2aec6bad827d.png` | 생략 (private) | - |

> S3 Base URL: `https://mztk-bucket.s3.ap-northeast-2.amazonaws.com/`

---

## TC-4 MARKET 상세 — 생성된 오브젝트 키 (n+1 확장 검증)

입력 3장 → 4개 Presigned URL 발급 (MARKET_THUMB 1 + MARKET_DETAIL 3)

| imgOrder | reference_type | tmpObjectKey |
|---|---|---|
| 1 | MARKET_THUMB | `public/market/thumb/tmp/c842b04b-35e4-40a7-902c-a0effd11bd6b.png` |
| 2 | MARKET_DETAIL | `public/market/detail/tmp/6cf60629-6568-4689-9d17-0b8671013eb8.png` |
| 3 | MARKET_DETAIL | `public/market/detail/tmp/1676b8d2-dc96-4a1d-85e0-6369bfd46ae6.png` |
| 4 | MARKET_DETAIL | `public/market/detail/tmp/4fa75fbb-cd11-4545-9ba4-696cec9e45a8.png` |

- 4개 tmpObjectKey 모두 서로 다른 UUID → 고유성 확인 ✅
- MARKET(virtual type)은 DB에 저장되지 않고 MARKET_THUMB / MARKET_DETAIL 으로 분리 저장 ✅

---

## TC-6 COMMUNITY_FREE 5장 상세 — 생성된 오브젝트 키

| imgOrder | tmpObjectKey |
|---|---|
| 1 | `public/community/free/tmp/0e5e3bf3-31c0-43cc-9c46-dd8e49d2df2f.png` |
| 2 | `public/community/free/tmp/eda799fe-a81d-4405-bc9c-67a6f8bb383c.png` |
| 3 | `public/community/free/tmp/72750c16-d073-47d8-9d6f-82ad1cf8c237.png` |
| 4 | `public/community/free/tmp/f11ceac6-98ab-40c6-8a86-6cd4932fc1e7.png` |
| 5 | `public/community/free/tmp/154a7088-55ff-42ba-984b-f249ac01179e.png` |

---

## 실패 항목

없음

---

## 비고

- **public 경로 GET 성공**: `public/` 하위 5개 prefix 모두 버킷 퍼블릭 읽기 정책이 활성화되어 있어 서명 없이 GET 200 반환 확인
- **private 경로 (WORKOUT)**: `private/workout/` 경로는 버킷 정책상 퍼블릭 GET 불가 → PUT 성공만 확인하고 GET 검증 생략
- **테스트 계정**: `pw-img-1773239819155@playwright.test` (테스트 실행 시 자동 생성, 실행마다 새 계정 발급)
- **업로드된 tmp/ 오브젝트**: `ImagePendingCleanupScheduler` 에 의해 5시간 후 자동 삭제 예정
