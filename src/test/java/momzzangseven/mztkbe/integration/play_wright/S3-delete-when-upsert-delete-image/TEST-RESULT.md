# Playwright E2E 테스트 결과 — S3 삭제 검증

**테스트 파일**: `S3-delete-when-upsert-delete-image/s3-delete-on-upsert.spec.ts`  
**실행 일시**: 2026-03-23 22:10:22 KST  
**환경**: Real S3 (`mztk-bucket`) + Real AWS Lambda + Local Spring Boot Server (ngrok 포트포워딩)  
**결과 요약**: ✅ 4 / 4 통과 (실패 0, 스킵 0)  
**총 소요 시간**: 18.3초

---

## 테스트 케이스별 결과

### ✅ [S3-DEL-001] COMPLETED 이미지를 게시글에서 제거하면 S3 에서 즉시 삭제된다

| 항목 | 내용 |
|------|------|
| 소요 시간 | 5.77초 |
| 이미지 ID | 504 |
| tmpKey | `public/community/free/tmp/ba0eaaa1-691a-4269-8321-0a523f263357.png` |
| finalKey | `public/community/free/ba0eaaa1-691a-4269-8321-0a523f263357.webp` |
| 게시글 ID | 386 |

**실행 흐름**:
1. `COMMUNITY_FREE` presigned URL 1개 발급 (imageId=504)
2. S3에 PNG 업로드 → Lambda 트리거
3. Lambda WebP 변환 완료 (시도 2회, 49,348 bytes)
4. 게시글 생성 → `imageIds=[504]`로 수정 (이미지 연결)
5. `imageIds=[]`로 수정 → `UpsertImagesByReferenceService` Phase 1에서 S3 삭제 실행
6. S3 GET 검증: **HTTP 403** (오브젝트 없음 → 삭제 확인 ✅)

---

### ✅ [S3-DEL-002] 여러 COMPLETED 이미지 중 일부만 제거하면 제거된 이미지만 S3에서 삭제된다

| 항목 | 내용 |
|------|------|
| 소요 시간 | 5.82초 |
| 이미지 ID | img1=505, img2=506 |
| img1 finalKey | `public/community/free/4c0d0f06-c648-4622-86a0-96c019b87628.webp` |
| img2 finalKey | `public/community/free/ccb29ce5-1742-4b95-85ae-708ac66faaf4.webp` |
| 게시글 ID | 387 |

**실행 흐름**:
1. `COMMUNITY_FREE` presigned URL 2개 발급 (img1=505, img2=506)
2. 양쪽 S3 업로드 → Lambda WebP 변환 완료
   - img1: 시도 2회, 49,348 bytes
   - img2: 시도 1회, 24,250 bytes
3. 게시글 생성 → `imageIds=[505,506]`으로 수정
4. `imageIds=[505]`로 수정 → img2만 제거
5. 검증:
   - img2 (제거됨): S3 **HTTP 403** (삭제 확인 ✅)
   - img1 (유지됨): S3 **HTTP 200** (존재 확인 ✅)

---

### ✅ [S3-DEL-003] PENDING 이미지를 게시글에서 제거해도 S3 에 finalObjectKey 가 없으므로 오류 없이 정상 완료된다

| 항목 | 내용 |
|------|------|
| 소요 시간 | 0.36초 |
| 이미지 ID | 507 |
| tmpKey | `public/community/free/tmp/a073a079-d559-4387-aa5d-37bfa85b74bd.png` |
| 게시글 ID | 388 |

**실행 흐름**:
1. presigned URL 발급 후 S3에 PNG 업로드 (Lambda 완료 대기 없음)
2. 이미지가 PENDING 상태인 채로 게시글 생성 → `imageIds=[507]`로 연결
3. `imageIds=[]`로 수정 → PENDING 이미지는 `finalObjectKey`가 없으므로 S3 삭제 스킵
4. API 정상 응답 200 OK 확인
5. tmpKey의 S3 실제 삭제는 버킷 lifecycle rule이 담당

---

### ✅ [S3-DEL-004] 게시글 삭제 시 PostDeletedEvent 로 이미지 unlink (AFTER_COMMIT), 서버 응답 200 OK 확인

| 항목 | 내용 |
|------|------|
| 소요 시간 | 5.45초 |
| 이미지 ID | 508 |
| finalKey | `public/community/free/956395da-25ee-4821-8f68-f3e580e85274.webp` |
| 게시글 ID | 389 |

**실행 흐름**:
1. presigned URL 발급 → S3 업로드 → Lambda WebP 변환 완료 (시도 2회, 49,348 bytes)
2. 게시글 생성 → `imageIds=[508]`로 수정 (이미지 연결)
3. 게시글 삭제 → `PostDeletedEvent` 발행 (트랜잭션 커밋 후 `AFTER_COMMIT`)
4. 이미지 unlink 처리 (`referenceId=null`) — DB row 보존
5. finalObjectKey S3 실제 삭제는 `ImageUnlinkedCleanupScheduler`가 매일 03:00 KST에 처리

---

## 전체 결과 요약

| 테스트 케이스 | 설명 | 결과 | 소요 시간 |
|---|---|---|---|
| S3-DEL-001 | COMPLETED 이미지 1개 제거 시 S3 즉시 삭제 | ✅ PASS | 5.77s |
| S3-DEL-002 | 복수 이미지 중 일부만 제거 시 선택적 삭제 | ✅ PASS | 5.82s |
| S3-DEL-003 | PENDING 이미지 제거 시 S3 skip (오류 없음) | ✅ PASS | 0.36s |
| S3-DEL-004 | 게시글 삭제 시 이미지 unlink (Event 방식) | ✅ PASS | 5.45s |

---

## 검증된 동작 원칙

1. **COMPLETED 이미지 즉시 삭제**: `UpsertImagesByReferenceService` Phase 1에서 `imageIds` 기준으로 제거 대상을 선별하고, `COMPLETED` 상태이며 `finalObjectKey`가 있는 이미지에 한해 S3 `DeleteObject`를 호출한다.

2. **PENDING 이미지 안전 처리**: `finalObjectKey`가 없는 PENDING/FAILED 이미지는 S3 삭제를 시도하지 않는다. `tmp/` 경로의 원본 파일은 버킷 lifecycle rule(1일)이 자동 처리한다.

3. **게시글 삭제 시 비동기 unlink**: `PostDeletedEvent`는 `AFTER_COMMIT` 시점에 발행되어 이미지의 `referenceType`, `referenceId`를 `null`로 설정한다. S3 실제 삭제는 `ImageUnlinkedCleanupScheduler`(매일 03:00 KST)가 담당한다.

4. **S3 삭제 결과 검증 방법**: IAM 유저가 public 경로에 대해 `GetObject` 권한이 없으므로, 삭제 후 GET 요청은 **HTTP 403**을 반환한다. 이는 삭제 성공을 의미하며, 오브젝트가 존재하는 경우 **HTTP 200**이 반환된다.

---

## 트러블슈팅 메모

| 이슈 | 원인 | 해결 |
|------|------|------|
| Lambda webhook HTTP 404 | ngrok 포트포워딩 미설정으로 Lambda가 로컬 서버에 접근 불가 | ngrok으로 8080 포트 포워딩 후 Lambda 환경변수에 URL 등록 |
| S3 삭제 후 HTTP 200 반환 | IAM 정책의 `DeleteObject` Resource ARN에 `/*` 와일드카드 누락 | ARN 수정: `arn:aws:s3:::mztk-bucket/public/community/free/*` |
| Playwright regex 오류 | `-g "[S3-DEL-001]"` 실행 시 `[]`가 정규표현식 문자 클래스로 해석 | `npx playwright test -g 'S3-DEL-001'` 로 실행 |
